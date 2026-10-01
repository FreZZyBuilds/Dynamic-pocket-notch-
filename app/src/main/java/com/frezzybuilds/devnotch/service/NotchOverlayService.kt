package com.frezzybuilds.devnotch.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.frezzybuilds.devnotch.MainActivity
import com.frezzybuilds.devnotch.R
import com.frezzybuilds.devnotch.appContainer
import com.frezzybuilds.devnotch.data.clipboard.ClipboardListener
import com.frezzybuilds.devnotch.ui.NotchContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground-Service, der die Dynamic Notch als System-Overlay zeichnet.
 *
 * Da ein Service keine Activity ist, stellt er Lifecycle-, ViewModelStore- und
 * SavedState-Owner selbst bereit, damit ComposeView im Overlay-Fenster funktioniert.
 */
class NotchOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    /** Wurzel des Overlay-Fensters (enthält die ComposeView, fängt die Zurück-Taste ab). */
    private var composeView: OverlayRootView? = null
    private lateinit var clipboardListener: ClipboardListener
    private lateinit var displayModeListener: SharedPreferences.OnSharedPreferenceChangeListener

    /** Compose-State: Änderungen lösen automatisch eine Recomposition der Notch aus. */
    private var notchLayout by mutableStateOf(
        NotchLayout(NotchLayoutMode.NOTCH_TOP, pill = PillGeometry(0, 0, 0, 0))
    )
    private var expanded = false

    /**
     * Fenster fokussierbar? Nur aufgeklappt UND wenn der sichtbare Inhalt Fokus braucht
     * (Notizen/Eingabefelder für die Bildschirmtastatur, Clip-Tab für die Zwischenablage) –
     * siehe [com.frezzybuilds.devnotch.ui.OverlayFocus]. Sonst gehen Tasten und Tastatur an
     * die App dahinter.
     */
    private var windowFocusable = false

    /** Zurück-Tasten im fokussierten Overlay; NotchContainer klappt bei jeder Änderung ein. */
    private var backPresses by mutableIntStateOf(0)

    /** Edge-Modus: Andock-Rand, Versatz nach innen (nur beim Ziehen/Einrasten) und vertikal. */
    private var edgeSide = EdgeSide.RIGHT
    // Float: Drag-Deltas sind Bruchteile von Pixeln und würden sich beim Abrunden verlieren.
    private var edgeX = 0f
    private var edgeY = 0f
    private var snapAnimator: ValueAnimator? = null

    override fun onCreate() {
        super.onCreate()
        running.value = true
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val settings = appContainer.notchSettings
        edgeSide = settings.edgeSide
        edgeY = settings.edgeOffsetFraction * screenHeight()
        // Vor dem ersten Layout gibt es noch keine Window-Insets: detectCutoutBounds() nutzt
        // dann die Display-Aussparung (API 29+) und korrigiert sich, sobald Insets ankommen.
        notchLayout = layoutFor(effectiveMode(), detectCutoutBounds()?.toLens())
        // Umschalten in den Einstellungen wirkt sofort, ohne den Service neu zu starten.
        displayModeListener = settings.addDisplayModeListener {
            notchLayout = notchLayout.copy(mode = effectiveMode())
            applyLayout()
        }

        clipboardListener = ClipboardListener(this, appContainer.clipboardRepository, lifecycleScope)
        clipboardListener.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Muss bei startForegroundService() innerhalb weniger Sekunden passieren.
        startInForeground()

        if (intent?.action == ACTION_STOP) {
            appContainer.notchSettings.notchEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (composeView == null) setupOverlay() else applyLayout()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Bei Rotation wandern Aussparung und Bildschirmmitte mit; die Edge-Position ist relativ
        // zur Bildschirmhöhe gespeichert und passt daher auch im Querformat.
        edgeY = appContainer.notchSettings.edgeOffsetFraction * screenHeight()
        // Ausrichtung und Tablet-Status neu lesen: Foldables wechseln beim Aufklappen zum Tablet.
        notchLayout = layoutFor(effectiveMode(), detectCutoutBounds()?.toLens())
        applyLayout()
    }

    override fun onDestroy() {
        snapAnimator?.cancel()
        clipboardListener.stop()
        appContainer.notchSettings.removeListener(displayModeListener)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        composeView?.let { windowManager.removeView(it) }
        composeView = null
        viewModelStore.clear()
        running.value = false
        super.onDestroy()
    }

    private fun setupOverlay() {
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                // Berührungen außerhalb der Notch gehen an die App dahinter, auch wenn fokussierbar.
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Tastatur (Notizen) verschiebt das Fenster, statt das Eingabefeld zu verdecken –
            // wichtig im Edge-Modus, wo der Drawer mittig sitzt.
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        updatePosition()

        val content = ComposeView(this).apply {
            setContent {
                NotchContainer(
                    layout = notchLayout,
                    onExpandRequest = { isExpanded ->
                        expanded = isExpanded
                        // Einklappen gibt den Fokus sofort zurück – nicht erst nach der
                        // Recomposition, damit Eingaben direkt wieder an die App dahinter gehen.
                        if (!isExpanded) windowFocusable = false
                        applyLayout()
                    },
                    onEdgeDrag = ::onEdgeDrag,
                    onEdgeDragEnd = ::onEdgeDragEnd,
                    onFocusableChange = { wantsFocus ->
                        val next = wantsFocus && expanded
                        if (next != windowFocusable) {
                            windowFocusable = next
                            applyLayout()
                        }
                    },
                    backPresses = backPresses
                )
            }
        }
        val view = OverlayRootView(this, onBack = { backPresses++ }).apply {
            setViewTreeLifecycleOwner(this@NotchOverlayService)
            setViewTreeViewModelStoreOwner(this@NotchOverlayService)
            setViewTreeSavedStateRegistryOwner(this@NotchOverlayService)
            addView(content)
        }
        // Ab Android 10 ist die Zwischenablage nur mit Fokus lesbar: Sobald die aufgeklappte
        // Notch Fokus bekommt, den aktuellen Inhalt nachträglich in die Historie übernehmen.
        view.viewTreeObserver.addOnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) clipboardListener.captureCurrentClip()
        }
        // Insets (inkl. displayCutout) kommen erst nach dem Anhängen des Fensters und bei jeder
        // Änderung (Rotation, Verschieben). Danach die Position am Punch-Hole nachjustieren.
        view.setOnApplyWindowInsetsListener { v, insets ->
            v.post(::refreshCutout)
            insets
        }
        windowManager.addView(view, layoutParams)
        composeView = view

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /** Überträgt Modus, Position und Fokus-Zustand auf das bestehende Overlay-Fenster. */
    private fun applyLayout() {
        val view = composeView ?: return
        updatePosition()
        windowManager.updateViewLayout(view, layoutParams)
    }

    // LEFT/RIGHT statt START/END: gemeint ist der physische Rand, an den die Bubble gezogen wurde.
    @SuppressLint("RtlHardcoded")
    private fun updatePosition() {
        layoutParams.apply {
            when (notchLayout.mode) {
                NotchLayoutMode.NOTCH_TOP -> {
                    val pill = notchLayout.pill
                    // CENTER_HORIZONTAL + x: Das Fenster bleibt bei jeder Breite (Pille wie
                    // aufgeklapptes Dashboard) horizontal auf die Linse zentriert.
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    x = pill.x
                    if (expanded) {
                        // Dashboard nie über die Oberkante schieben, System hält es im Bildschirm.
                        y = maxOf(pill.y, 0)
                        flags = flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS.inv()
                    } else {
                        // Eingeklappt exakt symmetrisch um die Linse, auch wenn y leicht negativ ist.
                        y = pill.y
                        flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    }
                }
                NotchLayoutMode.EDGE_SIDE -> {
                    // Physischer Rand (links/rechts), den der Nutzer per Ziehen gewählt hat.
                    val edge = if (edgeSide == EdgeSide.LEFT) Gravity.LEFT else Gravity.RIGHT
                    gravity = edge or Gravity.CENTER_VERTICAL
                    x = edgeX.roundToInt()
                    y = edgeY.roundToInt()
                    flags = flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS.inv()
                }
            }
            // Fokussierbar nur bei Bedarf (siehe [windowFocusable]); eingeklappt nie.
            flags = if (OverlayWindowFlags.isFocusable(expanded, windowFocusable)) {
                flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
        }
    }

    /** Bubble folgt dem Finger (Deltas in Bildschirm-Pixeln). */
    private fun onEdgeDrag(dx: Float, dy: Float) {
        snapAnimator?.cancel()
        // x zählt vom Andock-Rand nach innen: rechts angedockt bedeutet Finger nach rechts = kleiner.
        edgeX += if (edgeSide == EdgeSide.LEFT) dx else -dx
        edgeY += dy
        applyLayout()
    }

    /** Loslassen: am näheren Rand einrasten (animiert) und Position speichern. */
    private fun onEdgeDragEnd() {
        val view = composeView ?: return
        val snap = EdgeDock.snap(edgeSide, edgeX.roundToInt(), view.width, screenWidth())
        if (snap.side != edgeSide) {
            edgeSide = snap.side
            notchLayout = notchLayout.copy(edgeSide = snap.side)
        }
        edgeX = snap.startX.toFloat()
        edgeY = EdgeDock.clampY(edgeY.roundToInt(), view.height, screenHeight()).toFloat()
        applyLayout()

        appContainer.notchSettings.edgeSide = edgeSide
        appContainer.notchSettings.edgeOffsetFraction = edgeY / screenHeight()

        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofFloat(edgeX, 0f).apply {
            duration = 260
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                edgeX = it.animatedValue as Float
                applyLayout()
            }
            start()
        }
    }

    /** Liest die Aussparung erneut und verschiebt die Pille nur, wenn sich die Linse bewegt hat. */
    private fun refreshCutout() {
        if (composeView == null || notchLayout.mode != NotchLayoutMode.NOTCH_TOP) return
        val lens = detectCutoutBounds()?.toLens()
        // Verschieben löst neue Insets aus; die Linse in Bildschirmkoordinaten bleibt aber gleich,
        // daher endet die Schleife hier.
        if (lens == notchLayout.lens) return
        notchLayout = layoutFor(notchLayout.mode, lens)
        applyLayout()
    }

    /**
     * Ermittelt die obere Kamera-Aussparung in Bildschirmkoordinaten.
     *
     * 1. `composeView.rootWindowInsets.displayCutout` (API 28+): Die Rects sind relativ zum
     *    Overlay-Fenster und nur vorhanden, wenn das Fenster die Aussparung überlappt. Sie werden
     *    mit der Fensterposition in Bildschirmkoordinaten umgerechnet.
     * 2. Fallback `Display.cutout` (API 29+): gilt für den ganzen Bildschirm, z. B. vor dem ersten
     *    Layout oder wenn die Kamera außerhalb des Fensters liegt.
     *
     * Berücksichtigt nur Aussparungen im oberen Viertel (im Querformat liegt die Kamera seitlich).
     */
    private fun detectCutoutBounds(): Rect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val maxTop = screenHeight() / 4

        composeView?.rootWindowInsets?.displayCutout?.let { cutout ->
            val window = IntArray(2).also { composeView?.getLocationOnScreen(it) }
            cutout.boundingRects
                .map { Rect(it).apply { offset(window[0], window[1]) } }
                .topmost(maxTop)
                ?.let { return it }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            defaultDisplay()?.cutout?.boundingRects?.topmost(maxTop)?.let { return it }
        }
        return null
    }

    private fun List<Rect>.topmost(maxTop: Int): Rect? =
        filter { !it.isEmpty && it.top <= maxTop }.minByOrNull { it.top }

    /** Linse aus der Aussparung – null, wenn die Pille nicht zentriert darum passt (Ecke/Rand). */
    private fun Rect.toLens(): CameraLens? = NotchGeometry.usableLens(
        CameraLens.fromBounds(left, top, right, bottom),
        screenWidth(),
        resources.displayMetrics.density
    )

    /** Tablets immer im Edge-Layout, sonst wie in den Einstellungen gewählt. */
    private fun effectiveMode(): NotchLayoutMode =
        AdaptiveLayout.effectiveMode(appContainer.notchSettings.displayMode, isTablet())

    private fun layoutFor(mode: NotchLayoutMode, lens: CameraLens?) = NotchLayout(
        mode = mode,
        lens = lens,
        pill = NotchGeometry.collapsedPill(lens, screenWidth(), resources.displayMetrics.density),
        edgeSide = edgeSide,
        landscape = isLandscape()
    )

    private fun screenWidth(): Int = screenSize().x

    private fun screenHeight(): Int = screenSize().y

    private fun screenSize(): Point =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.maximumWindowMetrics.bounds.let { Point(it.width(), it.height()) }
        } else {
            Point().also { size ->
                @Suppress("DEPRECATION")
                defaultDisplay()?.getRealSize(size)
            }
        }

    private fun defaultDisplay(): Display? =
        getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)

    /**
     * Diskrete, dauerhafte Benachrichtigung: Ohne Foreground-Service würde Android den Prozess im
     * Leerlauf beenden und die Notch verschwände. Kanal mit minimaler Wichtigkeit – kein Ton,
     * kein Statusleisten-Icon, kein App-Badge; nur in der eingeklappten Benachrichtigungsliste.
     */
    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        // Kanal der Vorversion entfernen, sonst bleibt er verwaist in den App-Einstellungen.
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.overlay_channel_description)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
        )

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, NotchOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.overlay_notification_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        const val CHANNEL_ID = "devnotch_service"
        private const val LEGACY_CHANNEL_ID = "notch_overlay"
        const val NOTIFICATION_ID = 1

        /** „Beenden“ in der Benachrichtigung: stoppt und schaltet den Autostart ab. */
        const val ACTION_STOP = "com.frezzybuilds.devnotch.action.STOP"

        private val running = MutableStateFlow(false)

        /** Ob der Service gerade läuft – auch nach Neustart der Activity korrekt. */
        val isRunning: StateFlow<Boolean> = running.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, NotchOverlayService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NotchOverlayService::class.java))
        }
    }
}
