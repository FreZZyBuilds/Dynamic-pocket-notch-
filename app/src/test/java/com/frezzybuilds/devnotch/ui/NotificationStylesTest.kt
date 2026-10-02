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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
                            Box(Modifier.size(340.dp, (36 + peek.extraHeightDp).dp).clip(RoundedCornerShape(30.dp)).background(Color.Black)) {
                                PeekContent(peek, pillHeight = 36.dp, lensGap = 42.dp)
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
}
