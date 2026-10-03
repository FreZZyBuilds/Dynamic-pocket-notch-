package com.frezzybuilds.devnotch.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optionale Bedienungshilfe – einziger Weg, die Notch über dem Sperrbildschirm zu zeigen.
 *
 * Seit Android 8 blendet das System normale Overlays (`TYPE_APPLICATION_OVERLAY`) bei aktiver
 * Sperre immer aus; `FLAG_SHOW_WHEN_LOCKED` gilt nur für Activities. Fenster vom Typ
 * `TYPE_ACCESSIBILITY_OVERLAY` liegen dagegen über der Sperre. Der Dienst liest **keine**
 * Bildschirminhalte und empfängt keine Ereignisse (siehe `res/xml/notch_accessibility.xml`):
 * Er stellt nur seinen WindowManager bereit, solange das Gerät gesperrt ist.
 */
class NotchAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        current.value = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        current.value = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        current.value = null
        super.onDestroy()
    }

    /** WindowManager mit dem Token dieses Dienstes – nur damit sind Accessibility-Overlays erlaubt. */
    fun overlayWindowManager(): WindowManager = getSystemService(WindowManager::class.java)

    companion object {
        private val current = MutableStateFlow<NotchAccessibilityService?>(null)

        /** Der verbundene Dienst oder null (nicht aktiviert bzw. vom System getrennt). */
        val instance: StateFlow<NotchAccessibilityService?> = current.asStateFlow()

        /** In den Android-Einstellungen eingeschaltet? (Auch wenn der Dienst gerade neu bindet.) */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val self = ComponentName(context, NotchAccessibilityService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == self }
        }

        fun openSettings(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
