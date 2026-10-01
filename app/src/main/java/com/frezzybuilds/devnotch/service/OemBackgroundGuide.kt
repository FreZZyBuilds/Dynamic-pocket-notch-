package com.frezzybuilds.devnotch.service

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Hersteller mit eigenem, zusätzlichem Energiesparen (über Androids Akku-Optimierung hinaus).
 * Dort muss DevNotch zusätzlich freigegeben werden, sonst beendet das System die Notch trotzdem.
 */
enum class OemGuide(
    val vendor: String,
    val steps: List<String>,
    /** Herstellereigene Einstellungsseiten, in dieser Reihenfolge versucht (Paket, Klasse). */
    val screens: List<Pair<String, String>>
) {
    XIAOMI(
        vendor = "Xiaomi / Redmi / POCO",
        steps = listOf(
            "„Autostart“ für DevNotch einschalten",
            "Akku-Sparmodus der App auf „Keine Einschränkungen“ stellen",
            "In der Übersicht der letzten Apps DevNotch nach unten ziehen und das Schloss antippen"
        ),
        screens = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.miui.powerkeeper" to "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
        )
    ),
    SAMSUNG(
        vendor = "Samsung",
        steps = listOf(
            "Einstellungen → Akku → Hintergrundnutzungslimits",
            "DevNotch zu „Nie in den Standby-Modus versetzte Apps“ hinzufügen",
            "Prüfen, dass DevNotch nicht unter „Apps im Standby-Modus“ steht"
        ),
        screens = listOf(
            "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
            "com.samsung.android.sm" to "com.samsung.android.sm.battery.ui.BatteryActivity"
        )
    );

    companion object {
        /** Erkennung über Hersteller und Marke (Redmi/POCO melden teils eigene Marken). */
        fun forDevice(manufacturer: String, brand: String): OemGuide? {
            val ids = listOf(manufacturer, brand).map { it.lowercase() }
            return when {
                ids.any { it in setOf("xiaomi", "redmi", "poco") } -> XIAOMI
                ids.any { it == "samsung" } -> SAMSUNG
                else -> null
            }
        }
    }
}

/**
 * Öffnet die passende Herstellerseite. Die Klassen ändern sich je nach Firmware-Version –
 * deshalb nacheinander probieren und zuletzt auf die App-Info von DevNotch ausweichen.
 */
fun OemGuide.openSettings(context: Context) {
    val candidates = screens.map { (pkg, cls) ->
        Intent().setComponent(ComponentName(pkg, cls))
            .putExtra("package_name", context.packageName)
            .putExtra("package_label", "DevNotch")
    } + Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    for (intent in candidates) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
            // Manche Herstellerseiten sind nicht exportiert – nächste versuchen.
        }
    }
}
