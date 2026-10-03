package com.frezzybuilds.devnotch.system

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.frezzybuilds.devnotch.data.settings.NotchSettings
import com.frezzybuilds.devnotch.peek.Peek
import com.frezzybuilds.devnotch.peek.PeekCenter

/**
 * Beobachtet Systemzustände wie Apples Dynamic Island: Klingelmodus, Nicht stören,
 * Energiesparmodus, Akkustand, Kopfhörer und Taschenlampe. Braucht keine Berechtigung –
 * alles sind öffentliche System-Broadcasts bzw. Callbacks.
 */
class SystemEventMonitor(private val context: Context, private val settings: NotchSettings) {

    private val handler = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val camera = context.getSystemService(CameraManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    /** Bereits gewarnte Akkustufen – zurückgesetzt, sobald geladen wird. */
    private val warnedLevels = mutableSetOf<Int>()
    private var batteryInitialised = false

    private fun on(event: SystemEvent) = settings.isSystemEventOn(event)

    private fun show(event: SystemEvent, symbol: String, title: String, value: String?, tint: Long) {
        if (on(event)) PeekCenter.show(Peek.System(event, symbol, title, value, tint))
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                AudioManager.RINGER_MODE_CHANGED_ACTION -> if (!isInitialStickyBroadcast) onRinger()
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> onDnd()
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> onPowerSave()
                Intent.ACTION_BATTERY_CHANGED -> onBattery(intent)
                Intent.ACTION_AIRPLANE_MODE_CHANGED ->
                    show(SystemEvent.AIRPLANE, "✈", "Flugmodus", if (intent.getBooleanExtra("state", false)) "An" else "Aus", ORANGE)
                VOLUME_CHANGED -> onVolume(intent)
            }
        }
    }

    /** Lautstärke von Medien, Klingelton oder Gespräch – nur echte Änderungen, nicht beim Start. */
    private fun onVolume(intent: Intent) {
        if (!on(SystemEvent.VOLUME)) return
        val stream = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
        if (stream !in VOLUME_STREAMS) return
        val value = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
        val previous = intent.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1)
        if (value < 0 || value == previous) return
        val max = audio?.getStreamMaxVolume(stream)?.takeIf { it > 0 } ?: return
        PeekCenter.show(Peek.Volume((value.toFloat() / max).coerceIn(0f, 1f), stream))
    }

    private fun onRinger() = when (audio?.ringerMode) {
        AudioManager.RINGER_MODE_SILENT -> show(SystemEvent.RINGER, "🔕", "Lautlos", null, RED)
        AudioManager.RINGER_MODE_VIBRATE -> show(SystemEvent.RINGER, "📳", "Vibration", null, ORANGE)
        else -> show(SystemEvent.RINGER, "🔔", "Klingeln", null, WHITE)
    }

    private fun onDnd() {
        val active = notifications?.currentInterruptionFilter?.let { it != NotificationManager.INTERRUPTION_FILTER_ALL } ?: return
        show(SystemEvent.DND, "🌙", "Nicht stören", if (active) "An" else "Aus", PURPLE)
    }

    private fun onPowerSave() {
        val on = power?.isPowerSaveMode ?: return
        show(SystemEvent.POWER_SAVE, "🔋", "Energiesparmodus", if (on) "An" else "Aus", YELLOW)
    }

    private fun onBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val percent = level * 100 / scale
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        if (charging) warnedLevels.clear()
        if (!batteryInitialised) {
            // Beim Start nicht sofort warnen – nur beim Unterschreiten einer Stufe danach.
            batteryInitialised = true
            warnedLevels += LowBattery.LEVELS.filter { percent <= it }
            return
        }
        val crossed = LowBattery.crossed(percent, charging, warnedLevels) ?: return
        warnedLevels += LowBattery.LEVELS.filter { it >= crossed }
        show(SystemEvent.LOW_BATTERY, "⚠", "Akku schwach", "$percent %", RED)
    }

    /** Kopfhörer: der erste Aufruf meldet nur, was schon verbunden ist – ignorieren. */
    private var audioReady = false
    private var lastHeadphone = ""
    private var lastHeadphoneAt = 0L
    private val audioCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            if (!audioReady) {
                audioReady = true
                return
            }
            added.firstOrNull { it.isHeadphone() }?.let { announce(it, connected = true) }
        }

        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            removed.firstOrNull { it.isHeadphone() }?.let { announce(it, connected = false) }
        }
    }

    private fun announce(device: AudioDeviceInfo, connected: Boolean) {
        val name = device.productName?.toString()?.takeIf { it.isNotBlank() && it != android.os.Build.MODEL } ?: "Kopfhörer"
        // Bluetooth meldet sich oft doppelt (A2DP + Headset) – innerhalb von 2 s nur einmal.
        val key = "$name|$connected"
        val now = SystemClock.elapsedRealtime()
        if (key == lastHeadphone && now - lastHeadphoneAt < 2_000) return
        lastHeadphone = key
        lastHeadphoneAt = now
        show(SystemEvent.HEADPHONES, "🎧", name, if (connected) "Verbunden" else "Getrennt", CYAN)
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            SystemStatus.setTorch(enabled && on(SystemEvent.TORCH))
        }
    }

    fun start() {
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
                addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                addAction(VOLUME_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        audio?.registerAudioDeviceCallback(audioCallback, handler)
        runCatching { camera?.registerTorchCallback(torchCallback, handler) }
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        audio?.unregisterAudioDeviceCallback(audioCallback)
        runCatching { camera?.unregisterTorchCallback(torchCallback) }
        SystemStatus.setTorch(false)
    }

    /** Beim Entsperren (vom Overlay-Service gemeldet). */
    fun onUnlocked() = show(SystemEvent.UNLOCK, "🔓", "Entsperrt", null, WHITE)

    private companion object {
        const val RED = 0xFFFF453AL
        const val ORANGE = 0xFFFF9F0AL
        const val WHITE = 0xFFFFFFFFL
        const val PURPLE = 0xFFBF5AF2L
        const val YELLOW = 0xFFFFD60AL
        const val CYAN = 0xFF64D2FFL

        /** Nicht offiziell dokumentiert, aber seit Android 4 vom System gesendet. */
        const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        val VOLUME_STREAMS = setOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING, AudioManager.STREAM_VOICE_CALL)

        val HEADPHONE_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            26, // TYPE_BLE_HEADSET (API 31)
            27 // TYPE_BLE_SPEAKER (API 31)
        )

        fun AudioDeviceInfo.isHeadphone() = isSink && type in HEADPHONE_TYPES
    }
}
