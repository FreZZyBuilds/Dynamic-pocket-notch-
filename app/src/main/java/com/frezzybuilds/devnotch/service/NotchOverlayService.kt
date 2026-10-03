package com.frezzybuilds.devnotch.service

import com.frezzybuilds.devnotch.share.NameDropSession
import com.frezzybuilds.devnotch.share.localsend.LocalSend
import com.frezzybuilds.devnotch.system.SystemEventMonitor
import com.frezzybuilds.devnotch.system.SystemEvent
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.runtime.Recomposer
import com.frezzybuilds.devnotch.notify.NotificationHub
import android.view.View
import kotlinx.coroutines.launch
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
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
import com.frezzybuilds.devnotch.data.settings.NotchSettings
import com.frezzybuilds.devnotch.data.clipboard.ClipboardListener
import com.frezzybuilds.devnotch.peek.Peek
import com.frezzybuilds.devnotch.peek.PeekCenter
import com.frezzybuilds.devnotch.ui.ExpandedSize
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

    private val overlayRecomposer = Recomposer(AndroidUiDispatcher.Main)
    private val recomposeScope = CoroutineScope(AndroidUiDispatcher.Main + SupervisorJob())

    /** Fenstertyp, in dem die Notch gerade hängt, und der zugehörige WindowManager. */
    // Als Compose-State: Liegt die Notch über der Statusleiste, nutzt das Dashboard die Kamerazeile.
    private var host by mutableStateOf(OverlayHost.APP)
    private var hostWindowManager: WindowManager? = null
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

    /**
     * Fenster hat die feste Aufgeklappt-Größe. Wird beim Aufklappen sofort gesetzt und erst nach
     * der Einklapp-Animation zurückgenommen: So ändert sich die Fenstergröße pro Vorgang nur
     * zweimal statt in jedem Animationsbild (WRAP_CONTENT + animateContentSize ruckelte).
     */
    private var windowExpanded = false
    private val mainHandler = Handler(Looper.getMainLooper())
    /** Ein Peek wird angezeigt (Pille kurz nach unten gewachsen) bzw. das Fenster hat Peek-Größe. */
    private var peeking = false
    private var windowPeek = false

    /** Gewünschte Dashboard-Größe (dp) – vom NotchContainer gemeldet, auch live beim Ziehen. */
    private var dashboardWidthDp = ExpandedSize.DASHBOARD_MAX_WIDTH_DP
    private var dashboardHeightDp = ExpandedSize.DASHBOARD_HEIGHT_DP

    /** Eingeklappte Pille mit Text: gewünschte bzw. aktuell im Fenster gesetzte Breite (dp). */
    private var pillWideDp: Int? = null
    private var windowWideDp: Int? = null

    /** Höhe des aktuellen Peeks unter der Linsen-Zeile (Benachrichtigungen sind höher). */
    private var peekExtraHeightDp = ExpandedSize.PEEK_EXTRA_HEIGHT_DP

    private val shrinkWindow = Runnable {
        var changed = false
        if (!expanded && windowExpanded) {
            windowExpanded = false
            changed = true
        }
        if (!peeking && windowPeek) {
            windowPeek = false
            changed = true
        }
        if (windowWideDp != pillWideDp) {
            windowWideDp = pillWideDp
            changed = true
        }
        if (changed) applyLayout()
    }

    private lateinit var localSendListener: SharedPreferences.OnSharedPreferenceChangeListener

    private fun syncLocalSend() {
        if (appContainer.notchSettings.localSendReceive) LocalSend.acquire(this, LOCALSEND_USER) else LocalSend.release(LOCALSEND_USER)
    }

    /** Lautlos, Nicht stören, Akku, Kopfhörer, Taschenlampe … (Apple-Insel-Ereignisse). */
    private lateinit var systemEvents: SystemEventMonitor

    /** Bildschirm an/aus und Sperre – bestimmt Sichtbarkeit und was die Notch zeigen darf. */
    private var deviceLock by mutableStateOf(DeviceLock.UNLOCKED)
    private lateinit var lockSettingsListener: SharedPreferences.OnSharedPreferenceChangeListener

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val keyguard = getSystemService(KeyguardManager::class.java)
            val next = when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> DeviceLock.SCREEN_OFF
                Intent.ACTION_USER_PRESENT -> DeviceLock.UNLOCKED
                else -> DeviceLock.from(screenOn = true, keyguardLocked = keyguard?.isKeyguardLocked == true)
            }
            if (next == DeviceLock.SCREEN_OFF) {
                // Beim Sperren nichts offen lassen: einklappen, Peek beenden, Fokus abgeben.
                backPresses++
                PeekCenter.current.value?.let(PeekCenter::dismiss)
                windowFocusable = false
            }
            deviceLock = next
            applyLayout()
            mainHandler.removeCallbacks(lockRecheck)
            if (next == DeviceLock.LOCKED) mainHandler.postDelayed(lockRecheck, LOCK_RECHECK_MS)
            // Beim Einschalten/Entsperren veraltete Live-Ansichten (beendete Fahrt …) sofort entfernen.
            if (next != DeviceLock.SCREEN_OFF) NotificationHub.reconciler?.invoke()
            if (intent.action == Intent.ACTION_USER_PRESENT) systemEvents.onUnlocked()
        }
    }

    /**
     * Gesperrt bleibt die Notch privat (nur Timer). Kommt „entsperrt“ nicht an – z. B. wenn ein
     * Anruf über dem Sperrbildschirm lief –, korrigiert diese Prüfung den Zustand selbst.
     */
    private val lockRecheck = object : Runnable {
        override fun run() {
            if (deviceLock != DeviceLock.LOCKED) return
            if (getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == false) {
                deviceLock = DeviceLock.UNLOCKED
                applyLayout()
            } else {
                mainHandler.postDelayed(this, LOCK_RECHECK_MS)
            }
        }
    }

    /** Ladekabel angesteckt → Lade-Peek mit Akkustand. */
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_POWER_CONNECTED) return
            if (!appContainer.notchSettings.isSystemEventOn(SystemEvent.CHARGING)) return
            val percent = getSystemService(BatteryManager::class.java)
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 }
            PeekCenter.show(Peek.Charging(percent))
        }
    }

    /** Die ComposeView im Overlay-Fenster; ihre Gravity hält den Inhalt an Kamera bzw. Rand. */
    private var contentView: ComposeView? = null

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

        systemEvents = SystemEventMonitor(this, settings).also { it.start() }

        clipboardListener = ClipboardListener(this, appContainer.clipboardRepository, lifecycleScope)
        clipboardListener.start()

        // System-Broadcast, für dynamisch registrierte Empfänger weiterhin zugestellt.
        ContextCompat.registerReceiver(
            this,
            powerReceiver,
            IntentFilter(Intent.ACTION_POWER_CONNECTED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        deviceLock = DeviceLock.from(
            screenOn = getSystemService(PowerManager::class.java)?.isInteractive != false,
            keyguardLocked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        )
        lockSettingsListener = settings.addListener(setOf(NotchSettings.KEY_LOCKSCREEN_MODE, NotchSettings.KEY_COVER_STATUS_BAR)) { applyLayout() }
        // LocalSend-Empfang (AirDrop-Ersatz): läuft mit der Notch, solange er eingeschaltet ist.
        syncLocalSend()
        localSendListener = settings.addListener(setOf(NotchSettings.KEY_LOCALSEND_RECEIVE)) { syncLocalSend() }
        // Bedienungshilfe ein-/ausgeschaltet: Fenster ggf. in den anderen Typ umhängen.
        lifecycleScope.launch { NotchAccessibilityService.instance.collect { applyLayout() } }
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
        mainHandler.removeCallbacks(lockRecheck)
        appContainer.notchSettings.removeListener(localSendListener)
        LocalSend.release(LOCALSEND_USER)
        NameDropSession.stop()
        systemEvents.stop()
        overlayRecomposer.cancel()
        recomposeScope.cancel()
        snapAnimator?.cancel()
        clipboardListener.stop()
        appContainer.notchSettings.removeListener(displayModeListener)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        mainHandler.removeCallbacks(shrinkWindow)
        unregisterReceiver(powerReceiver)
        unregisterReceiver(screenReceiver)
        appContainer.notchSettings.removeListener(lockSettingsListener)
        composeView?.let { view -> runCatching { (hostWindowManager ?: windowManager).removeViewImmediate(view) } }
        hostWindowManager = null
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
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                // Ausdrücklich angefordert. Android übernimmt es für Fenster ohne Eltern-Fenster
                // ohnehin aus der App-Einstellung (Standard: an) – so hängt es nicht davon ab.
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
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

        // Eigener Recomposer statt des fensterbezogenen: Beim Umhängen zwischen normalem Overlay und
        // Accessibility-Fenster bleibt der Inhalt samt Zustand erhalten (offene Nachricht, Entwurf …).
        recomposeScope.launch { overlayRecomposer.runRecomposeAndApplyChanges() }
        val content = ComposeView(this).apply {
            setParentCompositionContext(overlayRecomposer)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this@NotchOverlayService))
            setContent {
                NotchContainer(
                    layout = notchLayout,
                    aboveStatusBar = host == OverlayHost.ACCESSIBILITY,
                    onExpandRequest = { isExpanded ->
                        expanded = isExpanded
                        if (isExpanded) {
                            mainHandler.removeCallbacks(shrinkWindow)
                            windowExpanded = true
                        } else {
                            // Einklappen gibt den Fokus sofort zurück – nicht erst nach der
                            // Recomposition, damit Eingaben direkt wieder an die App dahinter gehen.
                            windowFocusable = false
                            // Normalerweise meldet onCollapseSettled das Ende der Animation;
                            // Sicherheitsnetz, falls keine Größenänderung mehr animiert wird.
                            mainHandler.postDelayed(shrinkWindow, SHRINK_FALLBACK_MS)
                        }
                        applyLayout()
                    },
                    onCollapseSettled = {
                        mainHandler.removeCallbacks(shrinkWindow)
                        shrinkWindow.run()
                    },
                    onPeekChange = { extraHeight ->
                        val active = extraHeight != null
                        val heightChanged = extraHeight != null && extraHeight != peekExtraHeightDp
                        if (extraHeight != null) peekExtraHeightDp = extraHeight
                        if (active != peeking || heightChanged) {
                            peeking = active
                            if (active) {
                                // Erst das Fenster vergrößern, dann wächst die Pille darin.
                                mainHandler.removeCallbacks(shrinkWindow)
                                windowPeek = true
                            } else {
                                mainHandler.postDelayed(shrinkWindow, SHRINK_FALLBACK_MS)
                            }
                            applyLayout()
                        }
                    },
                    onDashboardSizeChange = { w, h ->
                        if (w != dashboardWidthDp || h != dashboardHeightDp) {
                            dashboardWidthDp = w
                            dashboardHeightDp = h
                            if (windowExpanded) applyLayout()
                        }
                    },
                    onPillWidthChange = { widthDp ->
                        if (widthDp != pillWideDp) {
                            pillWideDp = widthDp
                            if (widthDp != null && widthDp > (windowWideDp ?: 0)) {
                                // Breiter: Fenster sofort auf Endbreite, die Pille wächst darin.
                                mainHandler.removeCallbacks(shrinkWindow)
                                windowWideDp = widthDp
                                applyLayout()
                            } else {
                                // Schmaler: erst nach der Animation (onCollapseSettled bzw. Fallback).
                                mainHandler.postDelayed(shrinkWindow, SHRINK_FALLBACK_MS)
                            }
                        }
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
                    backPresses = backPresses,
                    locked = deviceLock.isLocked
                )
            }
        }
        val view = OverlayRootView(this, onBack = { backPresses++ }).apply {
            setViewTreeLifecycleOwner(this@NotchOverlayService)
            setViewTreeViewModelStoreOwner(this@NotchOverlayService)
            setViewTreeSavedStateRegistryOwner(this@NotchOverlayService)
            addView(content, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        contentView = content
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
        composeView = view
        attach(view, targetHost())

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /** Überträgt Modus, Position und Fokus-Zustand auf das bestehende Overlay-Fenster. */
    private fun applyLayout() {
        val view = composeView ?: return
        updatePosition()
        val target = targetHost()
        if (target != host || hostWindowManager == null) {
            // Umhängen: Compose baut den Inhalt im neuen Fenster neu auf (Timer & Co. liegen im
            // ViewModelStore des Service und laufen weiter).
            hostWindowManager?.let { wm -> runCatching { wm.removeViewImmediate(view) } }
            hostWindowManager = null
            attach(view, target)
        } else {
            runCatching { hostWindowManager!!.updateViewLayout(view, layoutParams) }
        }
    }

    private fun targetHost(): OverlayHost = OverlayHost.choose(
        deviceLock,
        appContainer.notchSettings.lockscreenMode,
        appContainer.notchSettings.coverStatusBar,
        NotchAccessibilityService.instance.value != null,
        // Tastatur nur im normalen Overlay – dorthin wechselt die Notch, solange ein Textfeld offen ist.
        needsKeyboard = expanded && windowFocusable
    )

    /** Hängt das Fenster in [target] ein; scheitert die Bedienungshilfe, im normalen Overlay. */
    private fun attach(view: View, target: OverlayHost) {
        val a11yManager = NotchAccessibilityService.instance.value?.overlayWindowManager()
        if (target == OverlayHost.ACCESSIBILITY && a11yManager != null) {
            layoutParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            val added = runCatching { a11yManager.addView(view, layoutParams) }.isSuccess
            if (added) {
                host = OverlayHost.ACCESSIBILITY
                hostWindowManager = a11yManager
                return
            }
        }
        layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        windowManager.addView(view, layoutParams)
        host = OverlayHost.APP
        hostWindowManager = windowManager
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
                    if (windowExpanded) {
                        // Dashboard nie über Ober- oder Seitenkante schieben.
                        val maxShift = ((screenWidth() - expandedSizePx().first) / 2).coerceAtLeast(0)
                        x = pill.x.coerceIn(-maxShift, maxShift)
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
                    y = if (windowExpanded) {
                        // Drawer vollständig sichtbar halten, egal wo die Bubble angedockt ist.
                        EdgeDock.clampY(edgeY.roundToInt(), expandedSizePx().second, screenHeight())
                    } else {
                        edgeY.roundToInt()
                    }
                    flags = flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS.inv()
                }
            }
            // Aufgeklappt/Peek feste Größe (Animation läuft im Fenster), sonst passend zum Inhalt.
            if (windowExpanded) {
                val (w, h) = expandedSizePx()
                width = w
                height = h
            } else if (windowPeek && notchLayout.mode == NotchLayoutMode.NOTCH_TOP) {
                val (w, h) = peekSizePx()
                width = w
                height = h
            } else if (windowWideDp != null && notchLayout.mode == NotchLayoutMode.NOTCH_TOP) {
                width = (windowWideDp!! * resources.displayMetrics.density).roundToInt()
                height = WindowManager.LayoutParams.WRAP_CONTENT
            } else {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
            }
            // Inhalt im (größeren) Fenster dort verankern, wo die Pille/Bubble sitzt.
            (contentView?.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                val anchor = contentGravity()
                if (lp.gravity != anchor) {
                    lp.gravity = anchor
                    contentView?.layoutParams = lp
                }
            }
            // Sperrbildschirm: je nach Einstellung darüber anzeigen oder ganz ausblenden.
            val lockMode = appContainer.notchSettings.lockscreenMode
            val hidden = DeviceLock.hideOverlay(deviceLock, lockMode)
            // Über der Sperre erscheint die Notch nur im Accessibility-Fenster (siehe [targetHost]);
            // FLAG_SHOW_WHEN_LOCKED wirkt bei Overlay-Fenstern nicht.
            flags = if (hidden) {
                flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            composeView?.visibility = if (hidden) android.view.View.INVISIBLE else android.view.View.VISIBLE
            // Fokussierbar nur bei Bedarf (siehe [windowFocusable]); eingeklappt und gesperrt nie
            // (die PIN-Eingabe des Sperrbildschirms darf nie zu uns wandern).
            flags = if (!deviceLock.isLocked && OverlayWindowFlags.isFocusable(expanded, windowFocusable)) {
                flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
        }
    }

    @SuppressLint("RtlHardcoded")
    private fun contentGravity(): Int = when (notchLayout.mode) {
        NotchLayoutMode.NOTCH_TOP -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        NotchLayoutMode.EDGE_SIDE ->
            (if (edgeSide == EdgeSide.LEFT) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL
    }

    /** Peek-Fenstergröße in Pixeln – dieselbe Rechnung wie im NotchContainer. */
    private fun peekSizePx(): Pair<Int, Int> {
        val density = resources.displayMetrics.density
        val (w, h) = ExpandedSize.peek(resources.configuration.screenWidthDp, notchLayout.pill.height / density, peekExtraHeightDp)
        return (w * density).roundToInt() to (h * density).roundToInt()
    }

    /** Aufgeklappte Fenstergröße in Pixeln – dieselbe Rechnung wie im NotchContainer. */
    private fun expandedSizePx(): Pair<Int, Int> {
        val density = resources.displayMetrics.density
        val config = resources.configuration
        val (w, h) = ExpandedSize.of(
            notchLayout.mode,
            config.screenWidthDp,
            config.screenHeightDp,
            notchLayout.landscape,
            if (notchLayout.mode == NotchLayoutMode.NOTCH_TOP) notchLayout.expandedTopInset / density else 0f,
            wantedWidthDp = dashboardWidthDp,
            wantedHeightDp = dashboardHeightDp
        )
        return (w * density).roundToInt() to (h * density).roundToInt()
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

    private fun layoutFor(mode: NotchLayoutMode, lens: CameraLens?): NotchLayout {
        val pill = NotchGeometry.collapsedPill(lens, screenWidth(), resources.displayMetrics.density)
        return NotchLayout(
            mode = mode,
            lens = lens,
            pill = pill,
            edgeSide = edgeSide,
            landscape = isLandscape(),
            expandedTopInset = NotchGeometry.expandedTopInset(pill, statusBarHeight())
        )
    }

    /** Höhe der Statusleiste: aus den Fenster-Insets und der System-Ressource. */
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun statusBarHeight(): Int {
        val fromInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            composeView?.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.statusBars())?.top ?: 0
        } else {
            0
        }
        // Insets sind relativ zum Fenster (das bei y > 0 liegen kann) – daher das Maximum.
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        val fromResource = if (id > 0) resources.getDimensionPixelSize(id) else 0
        return maxOf(fromInsets, fromResource)
    }

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
            .setSmallIcon(R.drawable.ic_stat_devnotch)
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
        private const val LOCK_RECHECK_MS = 2_000L

        /** Spätestens dann wird das Fenster nach dem Einklappen verkleinert (Feder ≈ 500 ms). */
        private const val SHRINK_FALLBACK_MS = 900L

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

private const val LOCALSEND_USER = "service"
