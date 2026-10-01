package com.frezzybuilds.devnotch.notify

import android.app.Notification
import android.app.PendingIntent
import android.graphics.Bitmap

/**
 * Live-Ansicht in der Notch – wie die „Live Activities“ der Dynamic Island. Entsteht aus
 * laufenden Benachrichtigungen; jede Art hat einen eigenen [LiveParser].
 */
sealed interface LiveActivity {
    val key: String
    val packageName: String
    val contentIntent: PendingIntent?

    /** Höher = wichtiger; die wichtigste aktive Ansicht gewinnt. */
    val priority: Int

    data class Call(
        override val key: String,
        override val packageName: String,
        override val contentIntent: PendingIntent?,
        val caller: String,
        val avatar: Bitmap?,
        /** Klingelt noch (eingehend) – dann groß mit Annehmen/Ablehnen. */
        val ringing: Boolean,
        /** Gesprächsbeginn (ms) für die laufende Dauer, sonst 0. */
        val since: Long,
        val answer: PendingIntent?,
        val decline: PendingIntent?,
        val hangUp: PendingIntent?
    ) : LiveActivity {
        override val priority = 100
    }

    data class Navigation(
        override val key: String,
        override val packageName: String,
        override val contentIntent: PendingIntent?,
        val app: String,
        /** z. B. „In 200 m rechts abbiegen“. */
        val instruction: String,
        /** z. B. „Hauptstraße · Ankunft 14:32“. */
        val detail: String?,
        val turnIcon: Bitmap?
    ) : LiveActivity {
        override val priority = 80
    }

    data class Timer(
        override val key: String,
        override val packageName: String,
        override val contentIntent: PendingIntent?,
        val app: String,
        val title: String,
        /** Startzeit (Stoppuhr) bzw. Endzeit (Countdown). */
        val base: Long,
        val countDown: Boolean,
        val icon: Bitmap?
    ) : LiveActivity {
        override val priority = 60
    }

    data class Progress(
        override val key: String,
        override val packageName: String,
        override val contentIntent: PendingIntent?,
        val app: String,
        val title: String,
        val fraction: Float,
        val icon: Bitmap?
    ) : LiveActivity {
        override val priority = 40
    }
}

/** Erkennt eine Live-Ansicht in einer Benachrichtigung. Neue Arten = neuer Parser. */
fun interface LiveParser {
    fun parse(n: NotchNotification, prefs: LivePrefs): LiveActivity?
}

object LiveParsers {
    private val NAVIGATION_APPS = setOf(
        "com.google.android.apps.maps", "com.waze", "com.here.app.maps", "net.osmand", "net.osmand.plus",
        "com.sygic.aura", "com.mapswithme.maps.pro", "app.organicmaps", "com.tomtom.gplay.navapp",
        "com.samsung.android.app.galaxyfinder"
    )
    private val ANSWER = Regex("(?i)annehmen|antworten|answer|accept|abheben")
    private val DECLINE = Regex("(?i)ablehnen|decline|reject|abweisen")
    private val HANG_UP = Regex("(?i)auflegen|beenden|hang ?up|end call")

    private fun NotchNotification.hasAction(pattern: Regex) = actions.any { pattern.containsMatchIn(it.title) && !it.needsInput }

    private fun NotchNotification.action(pattern: Regex) = actions.firstOrNull { pattern.containsMatchIn(it.title) && !it.needsInput }?.intent

    val call = LiveParser { n, prefs ->
        if (!prefs.calls || !(n.category == Notification.CATEGORY_CALL || n.isCallStyle)) return@LiveParser null
        // Klingeln erkennt man am Vorhandensein von „Annehmen“ (ohne „Auflegen“) – nicht daran,
        // ob die Aktion einen Intent trägt.
        val canAnswer = n.answerIntent != null || n.hasAction(ANSWER)
        val canHangUp = n.hangUpIntent != null || n.hasAction(HANG_UP)
        val ringing = canAnswer && !canHangUp
        val answer = n.answerIntent ?: n.action(ANSWER)
        val hangUp = n.hangUpIntent ?: n.action(HANG_UP)
        LiveActivity.Call(
            key = n.key,
            packageName = n.packageName,
            contentIntent = n.contentIntent,
            caller = n.title,
            avatar = n.icon,
            ringing = ringing,
            since = if (!ringing && n.usesChronometer) n.whenTime else 0L,
            answer = answer,
            decline = n.declineIntent ?: n.action(DECLINE),
            hangUp = hangUp
        )
    }

    val navigation = LiveParser { n, prefs ->
        val isNav = n.category == Notification.CATEGORY_NAVIGATION || (n.packageName in NAVIGATION_APPS && n.ongoing)
        if (!prefs.navigation || !isNav || n.isMedia) return@LiveParser null
        LiveActivity.Navigation(n.key, n.packageName, n.contentIntent, n.appLabel, n.title, n.text, n.icon)
    }

    val timer = LiveParser { n, prefs ->
        if (!prefs.timers || !n.ongoing || !n.usesChronometer || n.whenTime <= 0L) return@LiveParser null
        LiveActivity.Timer(n.key, n.packageName, n.contentIntent, n.appLabel, n.title, n.whenTime, n.chronometerCountDown, n.icon)
    }

    val progress = LiveParser { n, prefs ->
        val fraction = n.progressFraction
        if (!prefs.progress || !n.ongoing || fraction == null) return@LiveParser null
        LiveActivity.Progress(n.key, n.packageName, n.contentIntent, n.appLabel, n.title, fraction, n.icon)
    }

    /** Reihenfolge = Vorrang bei mehrdeutigen Benachrichtigungen. */
    val all: List<LiveParser> = listOf(call, navigation, timer, progress)

    fun parse(n: NotchNotification, prefs: LivePrefs): LiveActivity? =
        all.firstNotNullOfOrNull { it.parse(n, prefs) }

    /** Die wichtigste aktive Ansicht (bei Gleichstand die zuletzt aktualisierte). */
    fun primary(active: Collection<LiveActivity>): LiveActivity? = active.maxByOrNull { it.priority }
}
