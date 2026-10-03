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

    /**
     * Zuletzt gezeigter Inhalt je App (Paket + Titel + Text → Zeit), schlüsselübergreifend:
     * Discord & Co. posten dieselbe Nachricht unter zwei Schlüsseln (Unterhaltung + Kanal/Bubble);
     * die zweite Kopie kurz danach löst keinen Peek mehr aus.
     */
    private val recentContent = object : LinkedHashMap<String, Long>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>) = size > 100
    }

    private fun NotchNotification.appSignature() = "$packageName\u0000${title.trim()}\u0000${text?.trim()}"

    /** Gleicher Inhalt derselben App innerhalb dieses Fensters gilt als Doppel. */
    internal const val DUPLICATE_WINDOW_MS = 15_000L

    private val _recent = MutableStateFlow<List<NotchNotification>>(emptyList())
    /**
     * Zuletzt in der Notch gezeigte Benachrichtigungen (neueste zuerst, höchstens [RECENT_MAX]) –
     * für den Stapel „Benachrichtigungen“ und „+N weitere“. Nur im Arbeitsspeicher; verschwindet,
     * sobald die App die Benachrichtigung entfernt.
     */
    val recent: StateFlow<List<NotchNotification>> = _recent.asStateFlow()
    private const val RECENT_MAX = 30

    /** Weitere aktuelle Benachrichtigungen derselben App (für „+N weitere von …“). */
    fun moreFrom(n: NotchNotification): Int = _recent.value.count { it.packageName == n.packageName && it.key != n.key }

    private fun addRecent(n: NotchNotification) {
        _recent.value = (listOf(n) + _recent.value.filter { it.key != n.key }).take(RECENT_MAX)
    }

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
            val now = clock()
            val seenAt = recentContent[n.appSignature()]
            recentContent[n.appSignature()] = now
            if (seenAt != null && now - seenAt in 0..DUPLICATE_WINDOW_MS) return
            addRecent(n)
            PeekCenter.show(Peek.Notification(n, peekDurationMs(n, prefs), prefs.styleOverrides[n.packageName] ?: prefs.style, moreFrom(n)))
        } else if (!n.isMedia && n.packageName != ownPackage) {
            onAppSeen(n.packageName, n.appLabel)
        }
    }

    @Synchronized
    fun onRemoved(key: String) {
        if (_recent.value.any { it.key == key }) _recent.value = _recent.value.filter { it.key != key }
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
        if (_recent.value.any { it.key !in activeKeys }) _recent.value = _recent.value.filter { it.key in activeKeys }
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
        recentContent.clear()
        _recent.value = emptyList()
        live.clear()
        callStarts.clear()
        lastShown.clear()
        publish()
    }

    fun dismiss(key: String) {
        controller?.cancel(key)
        onRemoved(key)
    }

    /** „Alle löschen“ im Stapel: alle gezeigten Benachrichtigungen schließen. */
    fun dismissAllRecent() {
        _recent.value.map { it.key }.forEach(::dismiss)
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
