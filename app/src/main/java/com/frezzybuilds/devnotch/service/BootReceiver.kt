package com.frezzybuilds.devnotch.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.frezzybuilds.devnotch.appContainer

/**
 * Startet die Notch nach einem Neustart des Geräts – und nach einem App-Update, das den
 * laufenden Prozess beendet – automatisch wieder, sofern der Nutzer sie eingeschaltet hatte.
 *
 * Ein Foreground-Service vom Typ specialUse darf aus BOOT_COMPLETED heraus gestartet werden
 * (anders als z. B. dataSync oder mediaPlayback ab Android 15).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in TRIGGERS) return
        if (!shouldStart(context)) return
        NotchOverlayService.start(context)
    }

    companion object {
        private val TRIGGERS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)

        /** Nur wenn eingeschaltet und die Overlay-Berechtigung (noch) besteht. */
        fun shouldStart(context: Context): Boolean =
            context.appContainer.notchSettings.notchEnabled && Settings.canDrawOverlays(context)
    }
}
