package com.frezzybuilds.devnotch.feature.shortcuts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Installierte, startbare App für die Auswahl beim Hinzufügen. */
data class LaunchableApp(val label: String, val packageName: String)

object ShortcutLauncher {

    /**
     * Fallbacks, wenn eine App nicht installiert ist. Termux wird über F-Droid verteilt
     * (Play-Store-Version veraltet); alle anderen landen auf ihrer Play-Store-Seite.
     */
    private val INSTALL_PAGES = mapOf("com.termux" to "https://f-droid.org/packages/com.termux/")

    /** Startet die Kachel aus dem Overlay heraus (neuer Task nötig). */
    fun launch(context: Context, shortcut: ProjectShortcut) {
        val intent = when (shortcut.iconType) {
            ShortcutType.SYSTEM_APP ->
                context.packageManager.getLaunchIntentForPackage(shortcut.target)
                    ?: view(INSTALL_PAGES[shortcut.target] ?: "market://details?id=${shortcut.target}")
            ShortcutType.WEB_URL -> view(shortcut.target)
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // Kein Play Store (z. B. Custom ROM): Webseite des Stores öffnen.
            if (shortcut.iconType == ShortcutType.SYSTEM_APP) {
                runCatching {
                    context.startActivity(
                        view("https://play.google.com/store/apps/details?id=${shortcut.target}")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }

    private fun view(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url))

    /**
     * „github.com/foo/bar“ → „https://github.com/foo/bar“. Lokale Dev-Server
     * (localhost, 127.0.0.1, 10.0.2.2) bekommen http://. Ungültiges → null.
     */
    fun normalizeUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.contains(' ')) return null
        val withScheme = when {
            Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed) -> trimmed
            Regex("^(localhost|127\\.0\\.0\\.1|10\\.0\\.2\\.2)(:|/|$)").containsMatchIn(trimmed) -> "http://$trimmed"
            else -> "https://$trimmed"
        }
        val host = Uri.parse(withScheme).host
        return if (host.isNullOrBlank() || (!host.contains('.') && host != "localhost")) null else withScheme
    }

    fun hostOf(url: String): String = Uri.parse(url).host?.removePrefix("www.") ?: url

    /** Alle Apps mit Launcher-Icon (sichtbar dank <queries> MAIN/LAUNCHER im Manifest). */
    fun launchableApps(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { LaunchableApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
