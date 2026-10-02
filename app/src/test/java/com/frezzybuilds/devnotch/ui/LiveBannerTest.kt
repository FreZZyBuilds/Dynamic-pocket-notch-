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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.notify.LiveActivity
import com.frezzybuilds.devnotch.notify.NotchNotification
import com.frezzybuilds.devnotch.notify.NotificationHub
import com.frezzybuilds.devnotch.notify.NotifyPrefs
import com.frezzybuilds.devnotch.peek.Peek
import com.frezzybuilds.devnotch.service.NotchLayout
import com.frezzybuilds.devnotch.service.NotchLayoutMode
import com.frezzybuilds.devnotch.service.PillGeometry
import com.frezzybuilds.devnotch.ui.theme.Brand
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Live-Banner unter der Kamera: Navigation groß, nach oben wischen macht es klein. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w380dp-h640dp-mdpi")
class LiveBannerTest {

    @get:Rule
    val compose = createComposeRule()

    @After
    fun clear() = NotificationHub.clearLive()

    private val maps = NotchNotification(
        key = "0|com.google.android.apps.maps|1",
        packageName = "com.google.android.apps.maps",
        appLabel = "Maps",
        title = "Rechts abbiegen auf Hermannstraße",
        text = "200 m · Ankunft 14:32",
        ongoing = true
    )

    @Test
    fun `navigation shows as banner and swiping up shrinks it to the pill`() {
        NotificationHub.onPosted(maps, NotifyPrefs(), dndActive = false, ownPackage = "com.frezzybuilds.devnotch")
        compose.setContent {
            NotchContainer(
                layout = NotchLayout(NotchLayoutMode.NOTCH_TOP, pill = PillGeometry(120, 36, 0, 8)),
                onExpandRequest = {}
            )
        }
        compose.waitForIdle()
        compose.onNodeWithText("Rechts abbiegen auf Hermannstraße").assertExists()
        compose.onNodeWithText("200 m").assertExists()

        compose.onRoot().performTouchInput { swipe(Offset(centerX, 60f), Offset(centerX, -100f), 300) }
        compose.waitForIdle()
        compose.onNodeWithText("Rechts abbiegen auf Hermannstraße").assertDoesNotExist()
        // Klein: nur noch die Entfernung neben der Kamera.
        compose.onNodeWithText("200 m").assertExists()
    }

    @Test
    fun `distance comes from title or details`() {
        val nav = LiveActivity.Navigation("k", "p", null, "Maps", "Rechts abbiegen auf Hermannstraße", "200 m · Ankunft 14:32", null)
        assertEquals("200 m", navDistance(nav))
        assertEquals("1,5 km", navDistance(nav.copy(instruction = "In 1,5 km links", detail = null)))
        assertEquals(null, navDistance(nav.copy(detail = "Ankunft 14:32")))
    }

    @Test
    @Config(sdk = [34], qualifiers = "w380dp-h400dp-xxhdpi")
    fun renderBanners() {
        val banners = listOf(
            LiveActivity.Navigation("n", "p", null, "Maps", "Rechts abbiegen auf Hermannstraße", "200 m · Ankunft 14:32", null),
            LiveActivity.Call("c", "p", null, "Mama", null, ringing = false, since = 0L, answer = null, decline = null, hangUp = null),
            LiveActivity.Timer("t", "p", null, "Uhr", "Nudeln", base = System.currentTimeMillis() + 245_000, countDown = true, icon = null)
        )
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MaterialTheme(colorScheme = Brand.NotchScheme) {
                    Column(
                        Modifier.fillMaxSize().background(Color(0xFF2B2E6E)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        banners.forEach { live ->
                            Box(Modifier.size(320.dp, 86.dp).clip(RoundedCornerShape(30.dp)).background(Color.Black)) {
                                PeekContent(Peek.LiveBanner(live), pillHeight = 36.dp, lensGap = 42.dp)
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        File("build/screenshots/live_banners.png").apply { parentFile?.mkdirs() }.outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun `dark dialer icons are not used as avatar`() {
        val dark = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF111111.toInt()) }
        val photo = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFC8A27A.toInt()) }
        val transparent = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
        org.junit.Assert.assertTrue(dark.isMostlyDark())
        org.junit.Assert.assertFalse(photo.isMostlyDark())
        org.junit.Assert.assertTrue(transparent.isMostlyDark())
    }

    @Test
    @Config(sdk = [34], qualifiers = "w380dp-h260dp-xxhdpi")
    fun renderCallBanner() {
        val darkIcon = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF111111.toInt()) }
        val call = LiveActivity.Call("c", "com.samsung.android.dialer", null, "Ayomini", darkIcon, ringing = false,
            since = System.currentTimeMillis() - 83_000, answer = null, decline = null, hangUp = null)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MaterialTheme(colorScheme = Brand.NotchScheme) {
                    Column(Modifier.fillMaxSize().background(Color(0xFF2B2E6E)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(320.dp, 128.dp).clip(RoundedCornerShape(30.dp)).background(Color.Black)) {
                            PeekContent(Peek.LiveBanner(call), pillHeight = 36.dp, lensGap = 42.dp)
                        }
                        Box(Modifier.size(166.dp, 36.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black)) {
                            LivePill(call, 42.dp)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        File("build/screenshots/call_banner.png").apply { parentFile?.mkdirs() }.outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
