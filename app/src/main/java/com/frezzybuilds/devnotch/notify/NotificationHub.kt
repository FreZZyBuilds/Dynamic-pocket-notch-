package com.frezzybuilds.devnotch.notify

import com.frezzybuilds.devnotch.peek.Peek
import com.frezzybuilds.devnotch.peek.PeekCenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Vom Listener bereitgestellt: Benachrichtigungen schließen. */
fun interface NotificationController {
    fun cancel(key: String)
}

/**
 * Schaltzentrale zwischen Benachrichtigungs-Listener und Notch: Live-Ansichten verwalten,
 * Peeks auslösen, gesehene Apps für die Einstellungen merken.
 */
object NotificationHub {
    private val live = mutableMapOf<String, LiveActivity>()

    private val _primaryLive = MutableStateFlow<LiveActivity?>(null)
    /** Die wichtigste laufende Live-Ansicht (Anruf > Navigation > Timer > Fortschritt). */
    val primaryLive: StateFlow<LiveActivity?> = _primaryLive.asStateFlow()

    /** Apps, die zuletzt Benachrichtigungen geschickt haben (Paket → Name) – für die App-Auswahl. */
    var onAppSeen: (packageName: String, label: String) -> Unit = { _, _ -> }

    @Volatile
    var controller: NotificationController? = null

    /**
     * Neue/aktualisierte Benachrichtigung. [initialScan]: beim Verbinden vorhandene
     * Benachrichtigungen nur für Live-Ansichten auswerten, keine Peeks für Altes.
     */
    @Synchronized
    fun onPosted(n: NotchNotification, prefs: NotifyPrefs, dndActive: Boolean, ownPackage: String, initialScan: Boolean = false) {
        val activity = LiveParsers.parse(n, prefs.live)
        if (activity != null) live[n.key] = activity else live.remove(n.key)
        publish()
        if (initialScan || activity != null) return
        if (NotificationRules.shouldPeek(n, prefs, dndActive, ownPackage)) {
            onAppSeen(n.packageName, n.appLabel)
            PeekCenter.show(Peek.Notification(n, (prefs.durationSeconds * 1000).toLong()))
        } else if (!n.isMedia && n.packageName != ownPackage) {
            onAppSeen(n.packageName, n.appLabel)
        }
    }

    @Synchronized
    fun onRemoved(key: String) {
        if (live.remove(key) != null) publish()
        // Ein angezeigter Peek zu einer entfernten Benachrichtigung verschwindet mit ihr.
        (PeekCenter.current.value as? Peek.Notification)?.takeIf { it.notification.key == key }?.let(PeekCenter::dismiss)
    }

    /** Live-Ansichten neu bewerten (z. B. nach Änderung der Einstellungen). */
    @Synchronized
    fun clearLive() {
        live.clear()
        publish()
    }

    fun dismiss(key: String) {
        controller?.cancel(key)
        onRemoved(key)
    }

    private fun publish() {
        _primaryLive.value = LiveParsers.primary(live.values)
    }
}
