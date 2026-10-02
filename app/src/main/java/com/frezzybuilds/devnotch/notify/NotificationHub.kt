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
    private val live = LinkedHashMap<String, LiveActivity>()

    /** Wann ein Gespräch zuerst als aktiv gesehen wurde – Ersatz, wenn die App keine Startzeit liefert. */
    private val callStarts = mutableMapOf<String, Long>()

    /** Laufende Dauer für Anrufe: frühester plausibler Wert aus App-Angabe und eigener Beobachtung. */
    private fun withCallStart(activity: LiveActivity): LiveActivity {
        if (activity !is LiveActivity.Call || activity.ringing) return activity
        val now = clock()
        val seen = callStarts.getOrPut(activity.key) { now }
        val reported = activity.since.takeIf { it > 0 && now - it in 0..86_400_000L }
        return activity.copy(since = minOf(seen, reported ?: seen))
    }

    /** Uhr (für Tests austauschbar). */
    internal var clock: () -> Long = System::currentTimeMillis

    /** Live-Ansichten, die der Nutzer ausgeblendet hat (Schlüssel). */
    private val hidden = mutableSetOf<String>()

    /** Zuletzt angezeigter Inhalt je Schlüssel (begrenzt), damit Wiederholungen keinen Peek auslösen. */
    private val lastShown = object : LinkedHashMap<String, String>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > 100
    }

    private fun NotchNotification.contentSignature() = "$title\u0000$text"

    private val _primaryLive = MutableStateFlow<LiveActivity?>(null)
    /** Die wichtigste laufende Live-Ansicht (Anruf > Navigation > Timer > Fortschritt). */
    val primaryLive: StateFlow<LiveActivity?> = _primaryLive.asStateFlow()

    /** Apps, die zuletzt Benachrichtigungen geschickt haben (Paket → Name) – für die App-Auswahl. */
    var onAppSeen: (packageName: String, label: String) -> Unit = { _, _ -> }

    @Volatile
    var controller: NotificationController? = null

    /** Vom Listener gesetzt: Live-Ansichten sofort mit den aktiven Benachrichtigungen abgleichen. */
    @Volatile
    var reconciler: (() -> Unit)? = null

    /**
     * Neue/aktualisierte Benachrichtigung. [initialScan]: beim Verbinden vorhandene
     * Benachrichtigungen nur für Live-Ansichten auswerten, keine Peeks für Altes.
     */
    @Synchronized
    fun onPosted(n: NotchNotification, prefs: NotifyPrefs, dndActive: Boolean, ownPackage: String, initialScan: Boolean = false) {
        val activity = LiveParsers.parse(n, prefs.live)?.let(::withCallStart)
        // Entfernen + neu einfügen: Die Reihenfolge der Map ist so die der letzten Aktualisierung.
        live.remove(n.key)
        if (activity != null && n.key !in hidden) live[n.key] = activity
        publish()
        if (initialScan || activity != null) {
            lastShown[n.key] = n.contentSignature()
            return
        }
        // Apps posten dieselbe Benachrichtigung oft erneut (Sortierung, Zeitstempel) – nur echte
        // Änderungen am Inhalt zeigen.
        if (lastShown[n.key] == n.contentSignature()) return
        // Bezahlt mit Google/Samsung Wallet: Bestätigung wie Apple Pay statt normaler Benachrichtigung.
        com.frezzybuilds.devnotch.share.Wallet.parsePayment(n)?.let { payment ->
            lastShown[n.key] = n.contentSignature()
            if (prefs.payments) PeekCenter.show(Peek.Payment(payment.merchant, payment.amount))
            return
        }
        if (NotificationRules.shouldPeek(n, prefs, dndActive, ownPackage)) {
            lastShown[n.key] = n.contentSignature()
            onAppSeen(n.packageName, n.appLabel)
            PeekCenter.show(Peek.Notification(n, peekDurationMs(n, prefs)))
        } else if (!n.isMedia && n.packageName != ownPackage) {
            onAppSeen(n.packageName, n.appLabel)
        }
    }

    @Synchronized
    fun onRemoved(key: String) {
        callStarts.remove(key)
        lastShown.remove(key)
        hidden.remove(key)
        if (live.remove(key) != null) publish()
        // Ein angezeigter Peek zu einer entfernten Benachrichtigung verschwindet mit ihr.
        (PeekCenter.current.value as? Peek.Notification)?.takeIf { it.notification.key == key }?.let(PeekCenter::dismiss)
    }

    /**
     * Abgleich mit den tatsächlich aktiven Benachrichtigungen: Live-Ansichten, deren Benachrichtigung
     * verschwunden ist, ohne dass ein „entfernt“ ankam (Listener kurz getrennt, Sperre …), fliegen raus.
     */
    @Synchronized
    fun retain(activeKeys: Set<String>) {
        lastShown.keys.retainAll(activeKeys)
        hidden.retainAll(activeKeys)
        if (live.keys.retainAll(activeKeys)) publish()
    }

    /** Vom Nutzer ausgeblendet: bleibt weg, bis die App die Benachrichtigung entfernt. */
    @Synchronized
    fun hide(key: String) {
        hidden += key
        if (live.remove(key) != null) publish()
    }

    /** Live-Ansichten neu bewerten (z. B. nach Änderung der Einstellungen). */
    @Synchronized
    fun clearLive() {
        live.clear()
        callStarts.clear()
        lastShown.clear()
        publish()
    }

    fun dismiss(key: String) {
        controller?.cancel(key)
        onRemoved(key)
    }

    /**
     * Mindestens die eingestellte Dauer; lange Nachrichten bleiben so lange, bis der Lauftext
     * einmal durch ist (~120 ms je Zeichen bei 60 dp/s), höchstens 20 s.
     */
    fun peekDurationMs(n: NotchNotification, prefs: NotifyPrefs): Long {
        val base = (prefs.durationSeconds * 1000).toLong()
        val reading = 1_500L + (n.text?.length ?: 0) * 120L
        return maxOf(base, minOf(reading, 20_000L))
    }

    /** Bei gleicher Priorität gewinnt die zuletzt aktualisierte Ansicht. */
    private fun publish() {
        _primaryLive.value = LiveParsers.primary(live.values.toList().asReversed())
    }
}
