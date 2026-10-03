package com.frezzybuilds.devnotch.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.notify.LiveActivity
import com.frezzybuilds.devnotch.notify.NotchNotification
import com.frezzybuilds.devnotch.notify.NotificationHub
import com.frezzybuilds.devnotch.notify.NotificationStyle
import com.frezzybuilds.devnotch.notify.NotifyPrefs
import com.frezzybuilds.devnotch.peek.Peek
import com.frezzybuilds.devnotch.peek.PeekCenter
import com.frezzybuilds.devnotch.service.CallControl
import com.frezzybuilds.devnotch.ui.theme.Brand
import com.frezzybuilds.devnotch.appContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Benachrichtigungs-Stile (Klassisch, iOS kompakt, Glas), Stapel und eingeklappte Anrufsteuerung. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w380dp-h1100dp-mdpi")
class NotificationStylesTest {

    @get:Rule
    val compose = createComposeRule()

    @After
    fun clear() {
        PeekCenter.current.value?.let(PeekCenter::dismiss)
        NotificationHub.clearLive()
        CallControl.setStateForTest(null)
        CallControl.keypadOpen.value = false
    }

    private fun msg(key: String, title: String, text: String, pkg: String = "com.discord", label: String = "Discord", accent: Int = 0xFF5865F2.toInt()) =
        NotchNotification(key = key, packageName = pkg, appLabel = label, title = title, text = text, postTime = System.currentTimeMillis(), accent = accent)

    private fun save(view: View, name: String) {
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        File("build/screenshots/$name.png").apply { parentFile?.mkdirs() }.outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun styleHeightsMatchTheirLayout() {
        val n = msg("a", "Omar", "Verpasster Anruf")
        assertEquals(86, Peek.Notification(n, 0, NotificationStyle.CLASSIC).extraHeightDp)
        assertTrue(Peek.Notification(n, 0, NotificationStyle.COMPACT).extraHeightDp < 60)
        assertTrue(Peek.Notification(n, 0, NotificationStyle.GLASS).extraHeightDp > 100)
        // Hub übernimmt Stil und „+N weitere“.
        val prefs = NotifyPrefs(style = NotificationStyle.GLASS)
        NotificationHub.onPosted(msg("a", "Omar", "eins"), prefs, false, "own")
        NotificationHub.onPosted(msg("b", "Omar", "zwei"), prefs, false, "own")
        val peek = PeekCenter.current.value as Peek.Notification
        assertEquals(NotificationStyle.GLASS, peek.style)
        assertEquals(1, peek.more)
    }

    @Test
    fun renderStylesAndStack() {
        val prefs = NotifyPrefs()
        listOf(
            msg("1", "frezzy", "gg, noch eine Runde?"),
            msg("2", "Lena", "Kommst du mit ins Kino?", "org.telegram.messenger", "Telegram", 0xFF2AABEE.toInt()),
            msg("3", "Mama", "Ruf mal zurück ❤️", "com.whatsapp", "WhatsApp", 0xFF25D366.toInt()),
            msg("4", "Omar", "Verpasster Anruf", "com.samsung.android.dialer", "Telefon", 0xFF30D158.toInt())
        ).forEach { NotificationHub.onPosted(it, prefs, false, "own") }
        val n = msg("x", "Omar ❤️❤️", "Verpasster Anruf", "com.samsung.android.dialer", "Telefon", 0xFF30D158.toInt())
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MaterialTheme(colorScheme = Brand.NotchScheme) {
                    Column(
                        Modifier.fillMaxSize().background(Color(0xFF2B2E6E)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        NotificationStyle.entries.forEach { style ->
                            val peek = Peek.Notification(n, 0, style, more = 1)
                            // Wie im Container: Inhalt knapp unter der 12-dp-Linse (36/2 + 6 + 3 = 27 dp).
                            Box(Modifier.size(340.dp, (27 + peek.extraHeightDp).dp).clip(RoundedCornerShape(30.dp)).background(Color.Black)) {
                                PeekContent(peek, pillHeight = 36.dp, lensGap = 42.dp, contentTop = 27.dp)
                                // Kameralinse zur Orientierung.
                                Box(Modifier.align(androidx.compose.ui.Alignment.TopCenter).padding(top = 12.dp).size(12.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFF1C1C2A)))
                            }
                        }
                        listOf(
                            Peek.System(com.frezzybuilds.devnotch.system.SystemEvent.RINGER, "🔕", "Lautlos", null, 0xFFFF453AL),
                            Peek.System(com.frezzybuilds.devnotch.system.SystemEvent.LOW_BATTERY, "⚠", "Akku schwach", "20 %", 0xFFFF453AL),
                            Peek.Charging(80),
                            Peek.Volume(0.6f, android.media.AudioManager.STREAM_MUSIC),
                            Peek.TrackChanged("Blinding Lights", "The Weeknd", null)
                        ).forEach { sys ->
                            // Wie auf dem Gerät: Lücke = Linse (12 dp) + 6 dp je Seite.
                            Box(Modifier.size((SIDE_PILL_LEFT_DP + 24 + rememberSidePillSideDp(sys)).dp, 36.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black)) {
                                PeekContent(sys, pillHeight = 36.dp, lensGap = 24.dp)
                                // Linse: links schmal, rechts der Text.
                                Box(Modifier.padding(start = (SIDE_PILL_LEFT_DP + 6).dp, top = 12.dp).size(12.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFF1C1C2A)))
                            }
                        }
                        Box(Modifier.size(340.dp, 250.dp).clip(RoundedCornerShape(30.dp)).background(Color.Black).padding(14.dp)) {
                            NotificationStackContent(onOpen = {})
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("+1 weitere von Telefon · alle zeigen").assertExists()
        compose.onNodeWithText(" · 4").assertExists()
        compose.onNodeWithText("Alle löschen").assertExists()
        save(view, "notification_styles")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w380dp-h420dp-xxhdpi")
    fun callControlsHideBehindOneButton() {
        CallControl.setStateForTest(
            CallControl.State(caller = "Ayomini", ringing = false, dialing = false, onHold = false,
                connectedAt = System.currentTimeMillis() - 65_000, muted = false, speaker = false, canHold = true)
        )
        val call = LiveActivity.Call("c", "com.samsung.android.dialer", null, "Ayomini", null, ringing = false,
            since = System.currentTimeMillis() - 65_000, answer = null, decline = null, hangUp = null)
        val collapsed = Peek.LiveBanner(call, keypad = false)
        assertTrue(collapsed.extraHeightDp < Peek.LiveBanner(call, keypad = true).extraHeightDp)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MaterialTheme(colorScheme = Brand.NotchScheme) {
                    Column(Modifier.fillMaxSize().background(Color(0xFF2B2E6E)).padding(16.dp)) {
                        Box(Modifier.size(330.dp, (36 + collapsed.extraHeightDp).dp).clip(RoundedCornerShape(30.dp)).background(Color.Black)) {
                            PeekContent(collapsed, pillHeight = 36.dp, lensGap = 42.dp)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Lautspr.").assertDoesNotExist()
        compose.onNodeWithText("Auflegen").assertExists()
        save(view, "call_controls_collapsed")
        compose.onNodeWithText("⋯  Steuerung").performClick()
        assertTrue(CallControl.keypadOpen.value)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w380dp-h300dp-xxhdpi")
    fun timerStaysRoundInALowDashboard() {
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MaterialTheme(colorScheme = Brand.NotchScheme) {
                    Column(Modifier.fillMaxSize().background(Color(0xFF2B2E6E)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // So viel Platz bleibt mit Anrufkarte über den Tabs.
                        Box(Modifier.size(320.dp, 88.dp).background(Color.Black)) {
                            com.frezzybuilds.devnotch.ui.focus.FocusTimerContent(1500, 1500, false, {}, {})
                        }
                        Box(Modifier.size(320.dp, 150.dp).background(Color.Black)) {
                            com.frezzybuilds.devnotch.ui.focus.FocusTimerContent(900, 1500, true, {}, {})
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onAllNodesWithText("25m").assertCountEquals(2)
        save(view, "timer_low")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w380dp-h200dp-xxhdpi")
    fun sidePillIsNotClippedByTheWrappingView() {
        lateinit var view: View
        PeekCenter.show(Peek.System(com.frezzybuilds.devnotch.system.SystemEvent.RINGER, "📳", "Vibration", null, 0xFFFF9F0AL))
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalInspectionMode provides true) {
                Box(Modifier.fillMaxSize().background(Color(0xFF2B2E6E)), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                    // Wie im Service: Ansicht nur so breit wie ihr Inhalt, mittig, schneidet am Rand ab.
                    Box(Modifier.padding(top = 8.dp).clipToBounds()) {
                        NotchContainer(
                            layout = com.frezzybuilds.devnotch.service.NotchLayout(
                                com.frezzybuilds.devnotch.service.NotchLayoutMode.NOTCH_TOP,
                                lens = com.frezzybuilds.devnotch.service.CameraLens(centerX = 570, centerY = 60, diameter = 36),
                                pill = com.frezzybuilds.devnotch.service.PillGeometry(390, 105, 0, 8)
                            ),
                            onExpandRequest = {}
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        val text = compose.onNodeWithText("Vibration").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        save(view, "side_pill_in_window")
        // Ganz sichtbar: rechtes Textende liegt innerhalb der (mittigen) Ansicht.
        assertTrue("Text ragt aus der Ansicht", text.right <= root.right)
    }

    @Test
    fun lockedOverviewShowsSafeCardsOrEverythingWhenAllowed() {
        val settings = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().appContainer.notchSettings
        fun open(full: Boolean) {
            settings.lockscreenFull = full
            compose.setContent {
                NotchContainer(
                    layout = com.frezzybuilds.devnotch.service.NotchLayout(
                        com.frezzybuilds.devnotch.service.NotchLayoutMode.NOTCH_TOP,
                        pill = com.frezzybuilds.devnotch.service.PillGeometry(120, 36, 0, 8)
                    ),
                    onExpandRequest = {},
                    locked = true
                )
            }
            compose.onRoot().performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 450f), 300) }
            compose.waitForIdle()
        }
        try {
            open(full = false)
            compose.onNodeWithText("Timer").assertExists()
            compose.onNodeWithText("Neu").assertExists()
            compose.onNodeWithText("Notizen").assertDoesNotExist()
        } finally {
            settings.lockscreenFull = false
        }
    }

    @Test
    fun lockedOverviewWithEverythingAllowed() {
        val settings = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().appContainer.notchSettings
        settings.lockscreenFull = true
        try {
            compose.setContent {
                NotchContainer(
                    layout = com.frezzybuilds.devnotch.service.NotchLayout(
                        com.frezzybuilds.devnotch.service.NotchLayoutMode.NOTCH_TOP,
                        pill = com.frezzybuilds.devnotch.service.PillGeometry(120, 36, 0, 8)
                    ),
                    onExpandRequest = {},
                    locked = true
                )
            }
            compose.onRoot().performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 450f), 300) }
            compose.waitForIdle()
            compose.onNodeWithText("Notizen").assertExists()
        } finally {
            settings.lockscreenFull = false
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w380dp-h560dp-mdpi")
    fun renderNewLiveViews() {
        val now = System.currentTimeMillis()
        val alarm = LiveActivity.Alarm("a", "com.sec.android.app.clockpackage", null, "Wecker", "07:00 · Aufstehen", null, null)
        val peeks = listOf(
            Peek.LiveAlarm(alarm),
            Peek.LiveBanner(LiveActivity.Delivery("d", "ee.mtakso.client", null, "Bolt", "Dein Fahrer ist unterwegs", "VW Golf · B-XY 123", "6 Min.", null, null)),
            Peek.LiveBanner(LiveActivity.Event("e", null, "Sprint-Planung", "Raum 3.14", now + 10 * 60_000L))
        )
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MaterialTheme(colorScheme = Brand.NotchScheme) {
                    Column(Modifier.fillMaxSize().background(Color(0xFF2B2E6E)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        peeks.forEach { p ->
                            Box(Modifier.size(340.dp, (27 + p.extraHeightDp).dp).clip(RoundedCornerShape(30.dp)).background(Color.Black)) {
                                PeekContent(p, pillHeight = 36.dp, lensGap = 42.dp, contentTop = 27.dp)
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Stopp").assertExists()
        compose.onNodeWithText("in 10 Min.").assertExists()
        save(view, "new_live_views")
    }
}
