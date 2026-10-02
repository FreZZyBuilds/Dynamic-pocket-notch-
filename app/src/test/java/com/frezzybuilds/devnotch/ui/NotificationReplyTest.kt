package com.frezzybuilds.devnotch.ui

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.frezzybuilds.devnotch.notify.LockContent
import com.frezzybuilds.devnotch.notify.NotchAction
import com.frezzybuilds.devnotch.notify.NotchNotification
import com.frezzybuilds.devnotch.notify.NotificationRules
import com.frezzybuilds.devnotch.notify.ReplyAction
import com.frezzybuilds.devnotch.ui.theme.Brand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Direktantwort: Text kommt als RemoteInput bei der App an; Ansicht zum Lesen und Antworten. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w380dp-h420dp-xxhdpi")
class NotificationReplyTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun replyAction(): ReplyAction {
        val intent = PendingIntent.getBroadcast(
            context, 1, Intent("de.example.REPLY").setPackage(context.packageName), PendingIntent.FLAG_MUTABLE
        )
        val input = RemoteInput.Builder("key_text_reply").setLabel("Antworten").build()
        return ReplyAction("Antworten", intent, arrayOf(input))
    }

    private fun message(reply: ReplyAction?) = NotchNotification(
        key = "0|org.telegram.messenger|1",
        packageName = "org.telegram.messenger",
        appLabel = "Telegram",
        title = "Lena",
        text = "Bist du heute Abend beim Meetup? Ich bring den Laptop mit, dann können wir die " +
            "Notch-Animation zusammen auf dem Pixel testen. Sag Bescheid, ob 19 Uhr passt!",
        actions = listOf(NotchAction("Als gelesen markieren", null), NotchAction("Antworten", null, needsInput = true)),
        reply = reply
    )

    @Test
    fun `reply text reaches the app as remote input`() {
        assertTrue(replyAction().send(context, "Bin dabei 👍"))
        val sent = shadowOf(context as android.app.Application).broadcastIntents.last()
        val results = RemoteInput.getResultsFromIntent(sent)
        assertEquals("Bin dabei 👍", results.getCharSequence("key_text_reply").toString())
    }

    @Test
    fun `lock screen redaction removes the reply`() {
        assertNull(NotificationRules.redact(message(replyAction()), locked = true, lockContent = LockContent.APP_ONLY)!!.reply)
    }

    @Test
    fun `detail view shows full text and sends the reply`() {
        var replied = false
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            MaterialTheme(colorScheme = Brand.NotchScheme) {
                Column(
                    Modifier
                        .padding(12.dp)
                        .size(356.dp, 330.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black)
                        .padding(12.dp)
                ) {
                    NotificationDetail(message(replyAction()), locked = false, onSend = {}, onReplied = { replied = true }, onBack = {})
                }
            }
        }
        compose.onNodeWithText("Antworten an Lena …").assertExists()
        compose.onNodeWithContentDescription("Antwort").performTextInput("19 Uhr passt!")
        compose.waitForIdle()
        save(view, "notification_reply")
        compose.onNodeWithText("➤").performClick()
        compose.onNodeWithText("Gesendet ✓").assertExists()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertTrue(replied)
        val sent = shadowOf(context as android.app.Application).broadcastIntents.last()
        assertEquals("19 Uhr passt!", RemoteInput.getResultsFromIntent(sent).getCharSequence("key_text_reply").toString())
    }

    @Test
    fun `locked detail offers no reply`() {
        compose.setContent {
            MaterialTheme(colorScheme = Brand.NotchScheme) {
                Column(Modifier.size(356.dp, 330.dp)) {
                    NotificationDetail(message(replyAction()), locked = true, onSend = {}, onReplied = {}, onBack = {})
                }
            }
        }
        compose.onNodeWithText("Zum Antworten entsperren").assertExists()
        compose.onNodeWithContentDescription("Antwort").assertDoesNotExist()
    }

    @Test
    fun `pulling a message peek down opens it for reading and replying`() {
        com.frezzybuilds.devnotch.peek.PeekCenter.show(
            com.frezzybuilds.devnotch.peek.Peek.Notification(message(replyAction()), durationMs = 60_000)
        )
        compose.setContent {
            NotchContainer(
                layout = com.frezzybuilds.devnotch.service.NotchLayout(
                    com.frezzybuilds.devnotch.service.NotchLayoutMode.NOTCH_TOP,
                    pill = com.frezzybuilds.devnotch.service.PillGeometry(120, 36, 0, 8)
                ),
                onExpandRequest = {}
            )
        }
        compose.onNodeWithText("Antworten").assertExists()
        compose.onRoot().performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 450f), 300) }
        compose.waitForIdle()
        compose.onNodeWithText("Antworten an Lena …").assertExists()
        compose.onNodeWithText("Zurück").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Timer").assertExists()
    }

    private fun save(view: View, name: String) {
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        File("build/screenshots/$name.png").apply { parentFile?.mkdirs() }.outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
