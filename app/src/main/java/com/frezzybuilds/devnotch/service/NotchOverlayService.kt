package com.frezzybuilds.devnotch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
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
import com.frezzybuilds.devnotch.R
import com.frezzybuilds.devnotch.appContainer
import com.frezzybuilds.devnotch.data.clipboard.ClipboardListener
import com.frezzybuilds.devnotch.ui.NotchContainer

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
    private var composeView: ComposeView? = null
    private lateinit var clipboardListener: ClipboardListener
    private lateinit var displayModeListener: SharedPreferences.OnSharedPreferenceChangeListener

    /** Compose-State: Änderungen lösen automatisch eine Recomposition der Notch aus. */
    private var notchLayout by mutableStateOf(NotchLayout(NotchLayoutMode.NOTCH_TOP))
    private var expanded = false

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val settings = appContainer.notchSettings
        notchLayout = NotchLayout(mode = settings.displayMode, cutout = readCameraCutout())
        // Umschalten in den Einstellungen wirkt sofort, ohne den Service neu zu starten.
        displayModeListener = settings.addDisplayModeListener { mode ->
            notchLayout = notchLayout.copy(mode = mode)
            applyLayout()
        }

        clipboardListener = ClipboardListener(this, appContainer.clipboardRepository, lifecycleScope)
        clipboardListener.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Muss bei startForegroundService() innerhalb weniger Sekunden passieren.
        startInForeground()

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
        // Bei Rotation wandert die Kamera-Aussparung mit.
        notchLayout = notchLayout.copy(cutout = readCameraCutout())
        applyLayout()
    }

    override fun onDestroy() {
        clipboardListener.stop()
        appContainer.notchSettings.removeListener(displayModeListener)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        composeView?.let { windowManager.removeView(it) }
        composeView = null
        viewModelStore.clear()
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        updatePosition()

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@NotchOverlayService)
            setViewTreeViewModelStoreOwner(this@NotchOverlayService)
            setViewTreeSavedStateRegistryOwner(this@NotchOverlayService)
            setContent {
                NotchContainer(
                    layout = notchLayout,
                    onExpandRequest = { isExpanded ->
                        expanded = isExpanded
                        applyLayout()
                    }
                )
            }
        }
        // Ab Android 10 ist die Zwischenablage nur mit Fokus lesbar: Sobald die aufgeklappte
        // Notch Fokus bekommt, den aktuellen Inhalt nachträglich in die Historie übernehmen.
        view.viewTreeObserver.addOnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) clipboardListener.captureCurrentClip()
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

    private fun updatePosition() {
        layoutParams.apply {
            when (notchLayout.mode) {
                NotchLayoutMode.NOTCH_TOP -> {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    // Bei CENTER_HORIZONTAL ist x der Versatz zur Bildschirmmitte.
                    x = notchLayout.cutout?.let { it.centerX - screenWidth() / 2 } ?: 0
                    y = 0
                }
                NotchLayoutMode.EDGE_SIDE -> {
                    // Dockt wie das Samsung Edge-Panel am rechten Rand an (bei RTL-Sprachen links).
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    x = 0
                    y = 0
                }
            }
            // Aufgeklappt: fokussierbar (z. B. für Eingaben), eingeklappt: Fokus bleibt bei der App.
            flags = if (expanded) {
                flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
        }
    }

    /**
     * Liest die obere Kamera-Aussparung in Bildschirmkoordinaten aus.
     * Display.getCutout() gibt es erst ab API 29; darunter wird ohne Cutout zentriert.
     */
    private fun readCameraCutout(): CameraCutout? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val cutout = defaultDisplay()?.cutout ?: return null
        val rect = cutout.boundingRectTop
        if (rect.isEmpty) return null
        return CameraCutout(rect.left, rect.top, rect.right, rect.bottom)
    }

    private fun screenWidth(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.maximumWindowMetrics.bounds.width()
        } else {
            Point().also { size ->
                @Suppress("DEPRECATION")
                defaultDisplay()?.getRealSize(size)
            }.x
        }

    private fun defaultDisplay(): Display? =
        getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_MIN
            )
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        private const val CHANNEL_ID = "notch_overlay"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, NotchOverlayService::class.java)
            )
        }

    }
}
