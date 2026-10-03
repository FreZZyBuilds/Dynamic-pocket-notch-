package com.frezzybuilds.devnotch.system

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Systemereignisse, die die Notch wie Apples Dynamic Island kurz anzeigt (oder, bei Taschenlampe
 * und Bildschirmaufnahme, solange sie aktiv sind). Jedes lässt sich einzeln abschalten.
 */
enum class SystemEvent(val label: String, val description: String) {
    CHARGING("Laden", "Akkustand beim Anstecken des Kabels"),
    RINGER("Lautlos / Vibration / Ton", "Wenn du den Klingelmodus änderst"),
    DND("Nicht stören", "Beim Ein- und Ausschalten"),
    POWER_SAVE("Energiesparmodus", "Beim Ein- und Ausschalten"),
    LOW_BATTERY("Akku schwach", "Bei 20 % und 10 %"),
    HEADPHONES("Kopfhörer", "Verbunden oder getrennt (Bluetooth, Kabel, USB)"),
    TORCH("Taschenlampe", "Solange sie leuchtet – Antippen schaltet sie aus"),
    RECORDING("Bildschirmaufnahme", "Roter Punkt mit laufender Dauer"),
    UNLOCK("Entsperren", "Kurzes Schloss-Symbol beim Entsperren"),
    VOLUME("Lautstärke", "Lautsprecher links, Pegel rechts der Kamera – wie ab iOS 17"),
    AIRPLANE("Flugmodus", "Beim Ein- und Ausschalten"),
    PAYMENT("Google Pay", "„Bezahlt“ mit Betrag nach Zahlungen mit Google Pay oder Samsung Wallet"),
    SHARE("NameDrop & LocalSend", "Kontakt gesendet/empfangen, Dateien empfangen");
}

/** Laufende Systemzustände, die die Notch dauerhaft zeigt. */
object SystemStatus {
    private val torch = MutableStateFlow(false)

    /** Taschenlampe an (nur gemeldet, wenn das Ereignis eingeschaltet ist). */
    val torchOn: StateFlow<Boolean> = torch.asStateFlow()

    fun setTorch(on: Boolean) {
        torch.value = on
    }

    /** Taschenlampe ein- oder ausschalten (Schnelleinstellungen in der Notch). */
    fun setTorchMode(context: Context, on: Boolean) {
        val manager = context.getSystemService(CameraManager::class.java) ?: return
        runCatching {
            manager.cameraIdList
                .firstOrNull { id -> manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                ?.let { manager.setTorchMode(it, on) }
        }
    }

    /** Schaltet die Taschenlampe aus – wie das Antippen der Insel auf dem iPhone. */
    fun turnOffTorch(context: Context) {
        val manager = context.getSystemService(CameraManager::class.java) ?: return
        runCatching {
            manager.cameraIdList
                .filter { id -> manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                .forEach { id -> manager.setTorchMode(id, false) }
        }
    }
}

/** Akku-Warnstufen: einmal je Stufe, bis wieder geladen wird. */
object LowBattery {
    val LEVELS = listOf(20, 10)

    /** Neue Warnstufe, die bei [percent] erreicht wurde (noch nicht gemeldet), sonst null. */
    fun crossed(percent: Int, charging: Boolean, alreadyWarned: Set<Int>): Int? =
        if (charging) null else LEVELS.filter { percent <= it && it !in alreadyWarned }.minOrNull()
}
