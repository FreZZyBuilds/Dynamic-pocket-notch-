package com.frezzybuilds.devnotch.feature.github

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Schnellzugriff im Dev-Tab: eine App (mit Fallback-Link) oder eine feste URL. */
sealed interface DevShortcut {
    val label: String

    data class App(
        override val label: String,
        val packageName: String,
        /** Wird geöffnet, wenn die App nicht installiert ist (z. B. Store-Seite). */
        val fallbackUrl: String
    ) : DevShortcut

    data class Link(override val label: String, val url: String) : DevShortcut
}

/** Vordefinierte Shortcuts – hier eigene Projekt-URLs ergänzen. */
val DefaultDevShortcuts: List<DevShortcut> = listOf(
    DevShortcut.App(
        label = "Termux",
        packageName = "com.termux",
        // Termux wird über F-Droid verteilt, die Play-Store-Version ist veraltet.
        fallbackUrl = "https://f-droid.org/packages/com.termux/"
    ),
    DevShortcut.App(
        label = "GitHub",
        packageName = "com.github.android",
        fallbackUrl = "https://play.google.com/store/apps/details?id=com.github.android"
    ),
    DevShortcut.Link(
        label = "DevNotch",
        url = "https://github.com/FreZZyBuilds/Dynamic-pocket-notch-"
    )
)

/**
 * Startet den Shortcut aus dem Overlay heraus. Die Pakete müssen im Manifest unter
 * `<queries>` stehen, sonst sieht Android 11+ sie nicht und es greift immer der Fallback.
 */
fun DevShortcut.launch(context: Context) {
    val intent = when (this) {
        is DevShortcut.App ->
            context.packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl))
        is DevShortcut.Link ->
            // github.com-Links öffnen automatisch die GitHub-App, falls installiert.
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }
    try {
        // Start aus einem Service-Kontext braucht einen neuen Task.
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Kein Browser o. Ä. vorhanden – nichts zu tun.
    }
}
