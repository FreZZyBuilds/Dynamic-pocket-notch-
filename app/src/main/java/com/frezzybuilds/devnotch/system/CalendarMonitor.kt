package com.frezzybuilds.devnotch.system

import android.Manifest
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.frezzybuilds.devnotch.data.settings.NotchSettings
import com.frezzybuilds.devnotch.notify.LiveActivity
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Nächster Termin für die Notch (null = nichts in Kürze). */
object UpcomingEvent {
    private val _next = MutableStateFlow<LiveActivity.Event?>(null)
    val next: StateFlow<LiveActivity.Event?> = _next.asStateFlow()

    internal fun set(event: LiveActivity.Event?) {
        _next.value = event
    }

    /** Aus allen Terminen den passenden: beginnt in [leadMs] oder hat vor höchstens [graceMs] begonnen. */
    fun pick(events: List<Candidate>, now: Long, leadMs: Long = LEAD_MS, graceMs: Long = GRACE_MS): Candidate? =
        events.filter { !it.allDay && !it.declined && it.begin in (now - graceMs)..(now + leadMs) }.minByOrNull { it.begin }

    data class Candidate(val id: Long, val title: String, val begin: Long, val location: String?, val allDay: Boolean, val declined: Boolean)

    const val LEAD_MS = 15 * 60_000L
    const val GRACE_MS = 5 * 60_000L
}

/**
 * Liest den Kalender (nur mit Erlaubnis und eingeschalteter Option) jede Minute und bei Änderungen:
 * Beginnt ein Termin in den nächsten 15 Minuten, zeigt die Notch „Meeting · in 10 Min.“ –
 * wie Apples Live-Aktivität für Termine. Alles bleibt auf dem Gerät.
 */
class CalendarMonitor(private val context: Context, private val settings: NotchSettings) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "notch-calendar").apply { isDaemon = true } }
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            if (running) main.postDelayed(this, 60_000L)
        }
    }

    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    private fun allowed(): Boolean = settings.liveCalendar &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (running) return
        running = true
        runCatching { context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer) }
        main.post(tick)
    }

    fun stop() {
        running = false
        main.removeCallbacks(tick)
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
        UpcomingEvent.set(null)
    }

    fun shutdown() {
        stop()
        worker.shutdownNow()
    }

    /** Einstellung geändert oder Erlaubnis erteilt. */
    fun refresh() {
        if (!allowed()) {
            UpcomingEvent.set(null)
            return
        }
        runCatching {
            worker.execute {
                val event = runCatching { query() }.getOrNull()
                main.post { UpcomingEvent.set(event) }
            }
        }
    }

    private fun query(): LiveActivity.Event? {
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - UpcomingEvent.GRACE_MS)
            ContentUris.appendId(it, now + UpcomingEvent.LEAD_MS)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS
        )
        val candidates = mutableListOf<UpcomingEvent.Candidate>()
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                candidates += UpcomingEvent.Candidate(
                    id = c.getLong(0),
                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "Termin",
                    begin = c.getLong(2),
                    location = c.getString(3)?.takeIf { it.isNotBlank() },
                    allDay = c.getInt(4) != 0,
                    declined = c.getInt(5) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
                )
            }
        }
        val pick = UpcomingEvent.pick(candidates, now) ?: return null
        val open = PendingIntent.getActivity(
            context,
            pick.id.toInt(),
            Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, pick.id))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return LiveActivity.Event("calendar:${pick.id}:${pick.begin}", open, pick.title, pick.location, pick.begin)
    }
}
