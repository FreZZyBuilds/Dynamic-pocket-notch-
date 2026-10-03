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

    /** Bildschirmaufnahme läuft (roter Punkt mit Dauer, wie auf dem iPhone). */
    data class Recording(
        override val key: String,
        override val packageName: String,
        override val contentIntent: PendingIntent?,
        val app: String,
        /** Start der Aufnahme (ms) oder 0, wenn die App keine Uhr mitliefert. */
        val since: Long
    ) : LiveActivity {
        override val priority = 70
    }

    /** LocalSend-Übertragung (AirDrop-Ersatz) mit Fortschritt. */
    data class Transfer(
        val incoming: Boolean,
        val peer: String,
        val fileCount: Int,
        val fraction: Float,
        val fileName: String
    ) : LiveActivity {
        override val key = "localsend:transfer"
        override val packageName = "com.frezzybuilds.devnotch"
        override val contentIntent: PendingIntent? = null
        override val priority = 75
    }

    /** Taschenlampe an – kein Benachrichtigungs-Ursprung, kommt aus [com.frezzybuilds.devnotch.system.SystemStatus]. */
    data object Torch : LiveActivity {
        override val key = "system:torch"
        override val packageName = "android"
        override val contentIntent: PendingIntent? = null
        override val priority = 20
    }

    /** Wecker oder abgelaufener Timer klingelt – groß mit „Schlummern“ und „Stopp“. */
    data class Alarm(
        override val key: String,
        override val packageName: String,
        override val contentIntent: PendingIntent?,
        val title: String,
        val text: String?,
        val dismiss: PendingIntent?,
        val snooze: PendingIntent?
    ) : LiveActivity {
        override val priority = 95
    }

    /** Lieferung oder Fahrt (Lieferando, Uber, Bolt, DHL …) mit Ankunft und ggf. Fortschritt. */
    data class Delivery(
        override val key: String,
        override val packageName: String,
        override val contentIntent: PendingIntent?,
        val app: String,
        val title: String,
        val detail: String?,
        /** „14:32“ oder „8 Min.“, wenn im Text erkennbar. */
        val eta: String?,
        val fraction: Float?,
        val icon: Bitmap?
    ) : LiveActivity {
        override val priority = 65
    }

    /** Nächster Kalendertermin, der bald beginnt (aus dem Kalender, nicht aus Benachrichtigungen). */
    data class Event(
        override val key: String,
        override val contentIntent: PendingIntent?,
        val title: String,
        val location: String?,
        /** Beginn (ms). */
        val start: Long
    ) : LiveActivity {
        override val packageName = "com.android.calendar"
        override val priority = 50
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
        "com.sygic.aura", "com.mapswithme.maps.pro", "app.organicmaps", "com.tomtom.gplay.navapp"
    )
    private val RECORDER_APPS = setOf(
        "com.samsung.android.app.smartcapture", "com.android.systemui", "com.miui.screenrecorder",
        "com.oneplus.screenrecord", "com.coloros.screenrecorder", "com.huawei.screenrecorder"
    )
    private val CLOCK_APPS = setOf(
        "com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage",
        "com.oneplus.deskclock", "com.coloros.alarmclock", "com.huawei.deskclock", "com.motorola.timeweatherwidget"
    )
    private val TIMER_CATEGORIES = setOf(Notification.CATEGORY_ALARM, Notification.CATEGORY_REMINDER, "stopwatch")
    private val ROUTE_WORDS = Regex("(?i)ankunft|arrival|\\beta\\b|abbiegen|turn (left|right)|\\b\\d+\\s?min\\b")
    private val DISTANCE = Regex("(?i)\\b\\d+([.,]\\d+)?\\s?(m|km|ft|mi|yd)\\b")
    private val ANSWER = Regex("(?i)annehmen|antworten|answer|accept|abheben")
    private val DECLINE = Regex("(?i)ablehnen|decline|reject|abweisen")
    private val HANG_UP = Regex("(?i)auflegen|beenden|hang ?up|end call")
    private val SNOOZE = Regex("(?i)schlummer|snooze|später|spaeter")
    private val DISMISS = Regex("(?i)^\\s*(stopp|stop|beenden|ausschalten|schließen|schliessen|dismiss|turn off|aus)\\b")
    private val RUNNING = Regex("(?i)pause|anhalten|runde|lap|fortsetzen|resume")
    private val UPCOMING = Regex("(?i)bevorstehend|upcoming|jetzt ausschalten|dismiss now|überspringen|skip")
    private val DELIVERY_APPS = setOf(
        "com.takeaway.android", "com.yopeso.lieferando", "com.ubercab", "com.ubercab.eats", "ee.mtakso.client",
        "com.wolt.android", "com.getflink.android", "com.gorillas.android", "com.amazon.mShop.android.shopping",
        "de.dhl.paket", "de.hermesworld.app", "com.dpd.de", "com.gls.app", "com.freenow.android", "taxi.android.client",
        "com.deliveryhero.foodora", "com.lieferheld.android", "com.doordash.driverapp", "com.dd.doordash"
    )
    private val DELIVERY_WORDS = Regex("(?i)unterwegs|lieferung|zugestellt|kurier|fahrer|abholung|ankunft|on the way|driver|arriving|courier")
    private val ETA_CLOCK = Regex("\\b([01]?\\d|2[0-3]):[0-5]\\d\\b")
    private val ETA_MIN = Regex("(?i)\\b(\\d{1,3})\\s?(min|minuten|minutes|mins)\\b")

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
            // Samsung & Co. setzen keine Uhr, aber „when“ = Gesprächsbeginn; der Hub prüft Plausibilität.
            since = if (!ringing && n.whenTime > 0) n.whenTime else 0L,
            answer = answer,
            decline = n.declineIntent ?: n.action(DECLINE),
            hangUp = hangUp
        )
    }

    val navigation = LiveParser { n, prefs ->
        // Bekannte Navi-Apps; andere nur mit Kategorie „navigation“ UND einer Entfernung im Text –
        // sonst landen z. B. Bildschirmaufnahmen mit falscher Kategorie in der Pille.
        // Nur mit echter Wegbeschreibung (Entfernung, Ankunft, Abbiegen): „Mit Google Maps fahren“
        // ohne Route ist keine Navigation.
        val text = "${n.title} ${n.text.orEmpty()}"
        val routing = DISTANCE.containsMatchIn(text) || ROUTE_WORDS.containsMatchIn(text)
        val isNav = routing && n.ongoing && (n.packageName in NAVIGATION_APPS || n.category == Notification.CATEGORY_NAVIGATION)
        if (!prefs.navigation || !isNav || n.isMedia) return@LiveParser null
        LiveActivity.Navigation(n.key, n.packageName, n.contentIntent, n.appLabel, n.title, n.text, n.icon)
    }

    val alarm = LiveParser { n, prefs ->
        if (!prefs.timers) return@LiveParser null
        val clockLike = n.category == Notification.CATEGORY_ALARM || n.packageName in CLOCK_APPS
        if (!clockLike) return@LiveParser null
        // Klingelt: „Schlummern“ oder „Stopp“ – aber kein laufender Timer/Stoppuhr (die haben „Pause“)
        // und keine Vorschau „Bevorstehender Wecker · Jetzt ausschalten“.
        val text = "${n.title} ${n.text.orEmpty()} ${n.actions.joinToString(" ") { it.title }}"
        val snooze = n.action(SNOOZE)
        val dismiss = n.action(DISMISS)
        // Am Vorhandensein der Knöpfe erkennen – nicht daran, ob sie einen Intent tragen.
        val ringing = (n.hasAction(SNOOZE) || n.hasAction(DISMISS)) && !n.hasAction(RUNNING) && !UPCOMING.containsMatchIn(text)
        if (!ringing) return@LiveParser null
        LiveActivity.Alarm(n.key, n.packageName, n.contentIntent, n.title, n.text, dismiss, snooze)
    }

    val delivery = LiveParser { n, prefs ->
        if (!prefs.progress || !n.ongoing || n.isMedia) return@LiveParser null
        val text = "${n.title} ${n.text.orEmpty()}"
        val known = n.packageName in DELIVERY_APPS
        if (!known && !(n.category == Notification.CATEGORY_TRANSPORT && DELIVERY_WORDS.containsMatchIn(text))) return@LiveParser null
        if (!known && n.progressFraction == null) return@LiveParser null
        val eta = ETA_MIN.find(text)?.let { "${it.groupValues[1]} Min." } ?: ETA_CLOCK.find(text)?.value
        LiveActivity.Delivery(n.key, n.packageName, n.contentIntent, n.appLabel, n.title, n.text, eta, n.progressFraction, n.icon)
    }

    val timer = LiveParser { n, prefs ->
        if (!prefs.timers || !n.ongoing || !n.usesChronometer || n.whenTime <= 0L) return@LiveParser null
        // Nur echte Timer/Stoppuhren: Countdown, Uhr-App oder passende Kategorie. Eine laufende
        // Uhr allein haben auch Bildschirmaufnahmen, Hotspots und Sprachmemos.
        val isTimer = n.chronometerCountDown || n.packageName in CLOCK_APPS || n.category in TIMER_CATEGORIES
        if (!isTimer) return@LiveParser null
        LiveActivity.Timer(n.key, n.packageName, n.contentIntent, n.appLabel, n.title, n.whenTime, n.chronometerCountDown, n.icon)
    }

    val recording = LiveParser { n, prefs ->
        if (!prefs.recording || !n.ongoing || n.packageName !in RECORDER_APPS) return@LiveParser null
        // In der System-UI nur Benachrichtigungen mit laufender Uhr – dort hängen auch andere Dauermeldungen.
        if (n.packageName == "com.android.systemui" && !n.usesChronometer) return@LiveParser null
        LiveActivity.Recording(n.key, n.packageName, n.contentIntent, n.appLabel, if (n.usesChronometer) n.whenTime else 0L)
    }

    val progress = LiveParser { n, prefs ->
        val fraction = n.progressFraction
        if (!prefs.progress || !n.ongoing || fraction == null) return@LiveParser null
        LiveActivity.Progress(n.key, n.packageName, n.contentIntent, n.appLabel, n.title, fraction, n.icon)
    }

    /** Reihenfolge = Vorrang bei mehrdeutigen Benachrichtigungen. */
    val all: List<LiveParser> = listOf(call, alarm, recording, navigation, delivery, timer, progress)

    fun parse(n: NotchNotification, prefs: LivePrefs): LiveActivity? =
        all.firstNotNullOfOrNull { it.parse(n, prefs) }

    /** Die wichtigste aktive Ansicht; bei Gleichstand die zuerst übergebene. */
    fun primary(active: Collection<LiveActivity>): LiveActivity? = active.maxByOrNull { it.priority }
}
