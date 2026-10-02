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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.notify.AppVisuals
import com.frezzybuilds.devnotch.notify.NotchAction
import com.frezzybuilds.devnotch.notify.NotchNotification
import com.frezzybuilds.devnotch.peek.Peek
import com.frezzybuilds.devnotch.ui.theme.Brand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w380dp-h560dp-xxhdpi")
class PhoneAndTintTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `phone numbers are cleaned`() {
        assertEquals("+491512345678", cleanPhoneNumber("Tel: +49 (151) 234-56-78"))
        assertEquals("+4930*1#", cleanPhoneNumber("+49 30 +*1#"))
        assertEquals("", cleanPhoneNumber("keine Nummer"))
    }

    @Test
    fun `app icon colour becomes the accent`() {
        val telegramBlue = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF2AABEE.toInt()) }
        val accent = AppVisuals.accentOf(telegramBlue)!!
        val blue = accent and 0xFF
        val red = (accent shr 16) and 0xFF
        assertTrue("blau statt rot: $red/$blue", blue > red)
    }

    @Test
    fun `dial pad builds the number`() {
        compose.setContent { MaterialTheme(colorScheme = Brand.NotchScheme) { Box(Modifier.size(320.dp, 170.dp)) { PhoneTabContent(onLeave = {}) } } }
        listOf("0", "1", "5", "1").forEach { compose.onNodeWithContentDescription(it).performClick() }
        compose.onNodeWithText("0151").assertExists()
    }

    @Test
    fun renderTintedPeekAndPhone() {
        val telegram = NotchNotification(
            key = "t", packageName = "org.telegram.messenger", appLabel = "Telegram",
            title = "PS5 Homebrew – GoldHEN, PKG-builder and more", text = "pkg works fine on ps5 but not on ps4",
            actions = listOf(NotchAction("Als gelesen markieren", null), NotchAction("Stummschalten", null)),
            accent = 0xFF2AABEE.toInt()
        )
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MaterialTheme(colorScheme = Brand.NotchScheme) {
                    Column(Modifier.fillMaxSize().background(Color(0xFF2B2E6E)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(320.dp, 122.dp).clip(RoundedCornerShape(30.dp)).background(Color.Black)) {
                            PeekContent(Peek.Notification(telegram, 5_000), pillHeight = 36.dp, lensGap = 42.dp)
                        }
                        Box(Modifier.size(336.dp, 190.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black).padding(12.dp)) {
                            PhoneTabContent(onLeave = {})
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        File("build/screenshots/tint_and_phone.png").apply { parentFile?.mkdirs() }.outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
