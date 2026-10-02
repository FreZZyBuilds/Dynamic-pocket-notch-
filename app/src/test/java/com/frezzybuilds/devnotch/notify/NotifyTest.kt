package com.frezzybuilds.devnotch.notify

import android.app.Notification
import com.frezzybuilds.devnotch.peek.Peek
import com.frezzybuilds.devnotch.peek.PeekCenter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifyTest {

    private val own = "com.frezzybuilds.devnotch"
    private fun msg(pkg: String = "org.telegram.messenger", title: String = "Lena", text: String? = "Bist du da?") =
        NotchNotification(key = "k-$pkg-$title", packageName = pkg, appLabel = "Telegram", title = title, text = text)

    @After
    fun clear() {
        PeekCenter.current.value?.let(PeekCenter::dismiss)
        NotificationHub.clearLive()
    }

    @Test
    fun `rules filter what may peek`() {
        val prefs = NotifyPrefs()
        assertTrue(NotificationRules.shouldPeek(msg(), prefs, dndActive = false, ownPackage = own))
        assertFalse(NotificationRules.shouldPeek(msg(), prefs.copy(enabled = false), false, own))
        assertFalse(NotificationRules.shouldPeek(msg(pkg = own), prefs, false, own))
        assertFalse(NotificationRules.shouldPeek(msg(), prefs.copy(blockedApps = setOf("org.telegram.messenger")), false, own))
        assertFalse(NotificationRules.shouldPeek(msg().copy(ongoing = true), prefs, false, own))
        assertTrue(NotificationRules.shouldPeek(msg().copy(ongoing = true), prefs.copy(skipOngoing = false), false, own))
        assertFalse(NotificationRules.shouldPeek(msg().copy(silent = true), prefs, false, own))
        assertFalse("Nicht stören", NotificationRules.shouldPeek(msg(), prefs, dndActive = true, ownPackage = own))
        assertTrue(NotificationRules.shouldPeek(msg(), prefs.copy(respectDnd = false), dndActive = true, ownPackage = own))
        assertFalse(NotificationRules.shouldPeek(msg().copy(isMedia = true), prefs, false, own))
        assertFalse(NotificationRules.shouldPeek(msg().copy(isGroupSummary = true), prefs, false, own))
    }

    @Test
    fun `lock screen redaction`() {
        val n = msg()
        assertEquals(n, NotificationRules.redact(n, locked = false, lockContent = LockContent.HIDDEN))
        assertNull(NotificationRules.redact(n, locked = true, lockContent = LockContent.HIDDEN))
        val appOnly = NotificationRules.redact(n, locked = true, lockContent = LockContent.APP_ONLY)!!
        assertEquals("Telegram", appOnly.title)
        assertFalse(appOnly.text!!.contains("Bist du da"))
        assertEquals(n, NotificationRules.redact(n, locked = true, lockContent = LockContent.FULL))
    }

    @Test
    fun `live parsers recognise calls, navigation, timers and progress`() {
        val prefs = LivePrefs()
        val ringing = msg(title = "Mama").copy(category = Notification.CATEGORY_CALL, actions = listOf(NotchAction("Ablehnen", null), NotchAction("Annehmen", null)))
        val call = LiveParsers.parse(ringing, prefs) as LiveActivity.Call
        assertTrue(call.ringing)
        assertEquals("Mama", call.caller)

        val ongoing = msg(title = "Mama").copy(category = Notification.CATEGORY_CALL, usesChronometer = true, whenTime = 1_000L, actions = listOf(NotchAction("Auflegen", null)))
        val active = LiveParsers.parse(ongoing, prefs) as LiveActivity.Call
        assertFalse(active.ringing)
        assertEquals(1_000L, active.since)

        val maps = msg(pkg = "com.google.android.apps.maps", title = "In 200 m rechts abbiegen", text = "Hauptstraße").copy(ongoing = true)
        val nav = LiveParsers.parse(maps, prefs) as LiveActivity.Navigation
        assertEquals("In 200 m rechts abbiegen", nav.instruction)
        assertNull("Navigation abgeschaltet", LiveParsers.parse(maps, prefs.copy(navigation = false)))

        val stopwatch = msg(pkg = "com.sec.android.app.clockpackage", title = "Stoppuhr").copy(ongoing = true, usesChronometer = true, whenTime = 5_000L)
        assertTrue(LiveParsers.parse(stopwatch, prefs) is LiveActivity.Timer)

        val download = msg(pkg = "com.android.chrome", title = "video.mp4").copy(ongoing = true, progress = 45, progressMax = 100)
        assertEquals(0.45f, (LiveParsers.parse(download, prefs) as LiveActivity.Progress).fraction, 0.001f)

        assertNull(LiveParsers.parse(msg(), prefs))
        assertTrue(LiveParsers.primary(listOf(LiveParsers.parse(download, prefs)!!, call)) is LiveActivity.Call)
    }

    @Test
    fun `hub peeks normal notifications and tracks live views`() {
        val prefs = NotifyPrefs()
        NotificationHub.onPosted(msg(), prefs, dndActive = false, ownPackage = own)
        val peek = PeekCenter.current.value as Peek.Notification
        assertEquals("Lena", peek.notification.title)
        assertEquals(5_000L, peek.durationMs)

        val maps = msg(pkg = "com.google.android.apps.maps", title = "In 200 m rechts abbiegen").copy(ongoing = true)
        NotificationHub.onPosted(maps, prefs, false, own)
        assertTrue(NotificationHub.primaryLive.value is LiveActivity.Navigation)
        NotificationHub.onRemoved(maps.key)
        assertNull(NotificationHub.primaryLive.value)

        // Vorhandene Benachrichtigungen beim Verbinden: keine Peeks für Altes.
        PeekCenter.dismiss(peek)
        NotificationHub.onPosted(msg(title = "Alt"), prefs, false, own, initialScan = true)
        assertNull(PeekCenter.current.value)
    }

    @Test
    fun `short instruction for the pill`() {
        assertEquals("200 m", com.frezzybuilds.devnotch.ui.shortInstruction("In 200 m rechts abbiegen"))
        assertEquals("1,5 km", com.frezzybuilds.devnotch.ui.shortInstruction("Nach 1,5 km links"))
        assertEquals("Rechts halten", com.frezzybuilds.devnotch.ui.shortInstruction("Rechts halten"))
        assertEquals("1:15", com.frezzybuilds.devnotch.ui.formatDuration(75_000))
        assertEquals("1:02:05", com.frezzybuilds.devnotch.ui.formatDuration(3_725_000))
    }

    @Test
    fun `system summaries and services never peek`() {
        val prefs = NotifyPrefs(skipOngoing = false)
        assertFalse(NotificationRules.shouldPeek(msg(pkg = "com.android.systemui", title = "6 weitere Benachrichtigungen"), prefs, false, own))
        assertFalse(NotificationRules.shouldPeek(msg(pkg = "android"), prefs, false, own))
        assertFalse(NotificationRules.shouldPeek(msg().copy(category = Notification.CATEGORY_SERVICE), prefs, false, own))
        assertFalse(NotificationRules.shouldPeek(msg().copy(category = Notification.CATEGORY_SYSTEM), prefs, false, own))
        assertTrue(NotificationRules.shouldPeek(msg().copy(category = Notification.CATEGORY_MESSAGE), prefs, false, own))
    }

    @Test
    fun `screen recording is a recording, never navigation or timer`() {
        val prefs = LivePrefs()
        // Samsung-Bildschirmaufnahme: laufende Uhr, teils mit Kategorie „navigation“.
        val recorder = msg(pkg = "com.samsung.android.app.smartcapture", title = "Tippe hier, um die Aufnahme anzuhalten.")
            .copy(ongoing = true, usesChronometer = true, whenTime = 9_000L, category = Notification.CATEGORY_NAVIGATION)
        assertEquals(9_000L, (LiveParsers.parse(recorder, prefs) as LiveActivity.Recording).since)
        assertTrue(LiveParsers.parse(recorder.copy(category = null), prefs) is LiveActivity.Recording)
        // Abgeschaltet: weder Aufnahme noch etwas anderes.
        assertNull(LiveParsers.parse(recorder, prefs.copy(recording = false)))
        // System-UI ohne laufende Uhr ist keine Aufnahme.
        assertNull(LiveParsers.parse(msg(pkg = "com.android.systemui", title = "USB-Debugging").copy(ongoing = true), prefs))

        // Unbekannte Navi-App mit Kategorie und Entfernung zählt weiterhin.
        val nav = msg(pkg = "de.example.navi", title = "In 300 m links").copy(ongoing = true, category = Notification.CATEGORY_NAVIGATION)
        assertTrue(LiveParsers.parse(nav, prefs) is LiveActivity.Navigation)

        // Countdown aus beliebiger App ist ein Timer; Kategorie Wecker ebenso.
        val countdown = msg(pkg = "de.example.kitchen", title = "Nudeln").copy(ongoing = true, usesChronometer = true, whenTime = 60_000L, chronometerCountDown = true)
        assertTrue(LiveParsers.parse(countdown, prefs) is LiveActivity.Timer)
        val alarm = msg(pkg = "de.example.clock", title = "Timer").copy(ongoing = true, usesChronometer = true, whenTime = 60_000L, category = Notification.CATEGORY_ALARM)
        assertTrue(LiveParsers.parse(alarm, prefs) is LiveActivity.Timer)
    }

    @Test
    fun `reposted notification with the same content peeks only once`() {
        val prefs = NotifyPrefs()
        val n = msg(title = "Paket", text = "Zugestellt")
        NotificationHub.onPosted(n, prefs, false, own)
        val first = PeekCenter.current.value as Peek.Notification
        PeekCenter.dismiss(first)
        NotificationHub.onPosted(n, prefs, false, own)
        assertNull("gleicher Inhalt erneut gepostet", PeekCenter.current.value)
        NotificationHub.onPosted(n.copy(text = "Abholbereit"), prefs, false, own)
        assertEquals("Abholbereit", (PeekCenter.current.value as Peek.Notification).notification.text)
    }

    @Test
    fun `stale live views disappear on reconcile and can be hidden`() {
        val prefs = NotifyPrefs()
        val ride = msg(pkg = "ee.mtakso.client", title = "Abholung in 4 Min.").copy(ongoing = true, progress = 20, progressMax = 100)
        NotificationHub.onPosted(ride, prefs, false, own)
        assertTrue(NotificationHub.primaryLive.value is LiveActivity.Progress)

        // Fahrt beendet, aber das „entfernt“ kam nie an: der Abgleich räumt auf.
        NotificationHub.retain(emptySet())
        assertNull(NotificationHub.primaryLive.value)

        // Ausblenden hält, auch wenn die App weiter aktualisiert …
        NotificationHub.onPosted(ride, prefs, false, own)
        NotificationHub.hide(ride.key)
        NotificationHub.onPosted(ride.copy(progress = 30), prefs, false, own)
        assertNull(NotificationHub.primaryLive.value)
        // … bis die Benachrichtigung entfernt wurde und neu kommt.
        NotificationHub.onRemoved(ride.key)
        NotificationHub.onPosted(ride, prefs, false, own)
        assertTrue(NotificationHub.primaryLive.value is LiveActivity.Progress)
    }

    @Test
    fun `newest live view wins on equal priority`() {
        val prefs = NotifyPrefs()
        val old = msg(pkg = "ee.mtakso.client", title = "Fahrt").copy(ongoing = true, progress = 1, progressMax = 100)
        val new = msg(pkg = "com.android.chrome", title = "video.mp4").copy(ongoing = true, progress = 50, progressMax = 100)
        NotificationHub.onPosted(old, prefs, false, own)
        NotificationHub.onPosted(new, prefs, false, own)
        assertEquals("video.mp4", (NotificationHub.primaryLive.value as LiveActivity.Progress).title)
        NotificationHub.onPosted(old.copy(progress = 2), prefs, false, own)
        assertEquals("Fahrt", (NotificationHub.primaryLive.value as LiveActivity.Progress).title)
    }

    @Test
    fun `long messages stay until the marquee has run`() {
        val prefs = NotifyPrefs(durationSeconds = 5f)
        assertEquals(5_000L, NotificationHub.peekDurationMs(msg(text = "Kurz"), prefs))
        assertEquals(1_500L + 100 * 120L, NotificationHub.peekDurationMs(msg(text = "x".repeat(100)), prefs))
        assertEquals("höchstens 20 s", 20_000L, NotificationHub.peekDurationMs(msg(text = "x".repeat(1000)), prefs))
    }

    @Test
    fun `call duration starts even when the dialer sets no clock`() {
        var now = 1_000_000L
        NotificationHub.clock = { now }
        try {
            val prefs = NotifyPrefs()
            // Samsung: laufender Anruf ohne Uhr und ohne „when“.
            val call = msg(title = "Ayomini").copy(category = Notification.CATEGORY_CALL, ongoing = true, actions = listOf(NotchAction("Beenden", null)))
            NotificationHub.onPosted(call, prefs, false, own)
            assertEquals(1_000_000L, (NotificationHub.primaryLive.value as LiveActivity.Call).since)
            // Erneut gepostet: Beginn bleibt.
            now += 65_000
            NotificationHub.onPosted(call, prefs, false, own)
            assertEquals(1_000_000L, (NotificationHub.primaryLive.value as LiveActivity.Call).since)
            // Meldet die App einen früheren, plausiblen Beginn, gilt der.
            NotificationHub.onPosted(call.copy(whenTime = 990_000L), prefs, false, own)
            assertEquals(990_000L, (NotificationHub.primaryLive.value as LiveActivity.Call).since)
        } finally {
            NotificationHub.clock = System::currentTimeMillis
        }
    }

    @Test
    fun `maps without a route is no navigation`() {
        val prefs = LivePrefs()
        val idle = msg(pkg = "com.google.android.apps.maps", title = "Mit Google Maps fahren", text = "Maps").copy(ongoing = true)
        assertNull(LiveParsers.parse(idle, prefs))
        val route = idle.copy(title = "Rechts abbiegen auf Hermannstraße", text = "Ankunft 14:32")
        assertTrue(LiveParsers.parse(route, prefs) is LiveActivity.Navigation)
    }
}
