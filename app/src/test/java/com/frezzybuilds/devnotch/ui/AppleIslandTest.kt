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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.notify.LiveActivity
import com.frezzybuilds.devnotch.peek.Peek
import com.frezzybuilds.devnotch.peek.PeekCenter
import com.frezzybuilds.devnotch.service.NotchLayout
import com.frezzybuilds.devnotch.service.NotchLayoutMode
import com.frezzybuilds.devnotch.service.PillGeometry
import com.frezzybuilds.devnotch.system.SystemEvent
import com.frezzybuilds.devnotch.system.SystemStatus
import com.frezzybuilds.devnotch.ui.theme.Brand
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Apple-Insel: Systemereignisse, Taschenlampe, Aufnahme. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w380dp-h640dp-mdpi")
class AppleIslandTest {

    @get:Rule
    val compose = createComposeRule()

    @After
    fun clear() {
        SystemStatus.setTorch(false)
        PeekCenter.current.value?.let(PeekCenter::dismiss)
    }

    private fun island() = compose.setContent {
        NotchContainer(
            layout = NotchLayout(NotchLayoutMode.NOTCH_TOP, pill = PillGeometry(120, 36, 0, 8)),
            onExpandRequest = {}
        )
    }

    @Test
    fun `torch shows in the pill and a tap turns it off`() {
        SystemStatus.setTorch(true)
        island()
        compose.onNodeWithText("An").assertExists()
        compose.onNodeWithText("🔦").assertExists()
        // Robolectric hat keine Kamera mit Blitz: Ausschalten darf nicht abstürzen.
        compose.onRoot().performClick()
        compose.waitForIdle()
    }

    @Test
    fun `system event peek appears in the island`() {
        island()
        PeekCenter.show(Peek.System(SystemEvent.RINGER, "🔕", "Lautlos", null, 0xFFFF453AL))
        compose.waitForIdle()
        compose.onNodeWithText("Lautlos").assertExists()
        assertFalse(PeekCenter.current.value == null)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w380dp-h560dp-xxhdpi")
    fun renderSystemEvents() {
        val peeks = listOf(
            Peek.System(SystemEvent.RINGER, "🔕", "Lautlos", null, 0xFFFF453AL),
            Peek.System(SystemEvent.HEADPHONES, "🎧", "AirPods Pro", "Verbunden", 0xFF64D2FFL),
            Peek.System(SystemEvent.LOW_BATTERY, "⚠", "Akku schwach", "20 %", 0xFFFF453AL),
            Peek.System(SystemEvent.POWER_SAVE, "🔋", "Energiesparmodus", "An", 0xFFFFD60AL),
            Peek.LiveBanner(LiveActivity.Recording("r", "p", null, "Bildschirmrekorder", since = 0L))
        )
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MaterialTheme(colorScheme = Brand.NotchScheme) {
                    Column(
                        Modifier.fillMaxSize().background(Color(0xFF2B2E6E)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        peeks.forEach { peek ->
                            Box(Modifier.size(320.dp, 82.dp).clip(RoundedCornerShape(30.dp)).background(Color.Black)) {
                                PeekContent(peek, pillHeight = 36.dp, lensGap = 42.dp)
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        File("build/screenshots/system_events.png").apply { parentFile?.mkdirs() }.outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w380dp-h300dp-xxhdpi")
    fun renderMusicIsland() {
        val art = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(230, 80, 40)) }
        com.frezzybuilds.devnotch.service.MediaNotificationListener.setNowPlayingForTest(
            com.frezzybuilds.devnotch.service.NowPlaying(
                title = "Midnight City", artist = "M83", isPlaying = true, packageName = "com.spotify.music", artwork = art,
                accent = com.frezzybuilds.devnotch.service.ArtworkAccent(0xFFFF7043.toInt(), 0xFF6A1B9A.toInt())
            )
        )
        // Zweite Aktivität: Taschenlampe (Live) – Musik wandert in den kleinen Kreis.
        SystemStatus.setTorch(true)
        try {
            lateinit var view: View
            compose.setContent {
                view = LocalView.current
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    MaterialTheme(colorScheme = Brand.NotchScheme) {
                        Column(Modifier.fillMaxSize().background(Color(0xFF2B2E6E)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Großer Player (Gedrückthalten).
                            Box(Modifier.size(320.dp, 148.dp).clip(RoundedCornerShape(30.dp)).background(Color.Black)) {
                                PeekContent(Peek.MusicPlayer(), pillHeight = 36.dp, lensGap = 42.dp)
                            }
                            // Kompakt: Cover + Wellenform, daneben der kleine Kreis (Taschenlampe als zweite Aktivität).
                            Box(Modifier.size(320.dp, 60.dp)) {
                                // Pillengröße in Pixeln: 120 × 36 dp bei xxhdpi.
                                NotchContainer(
                                    layout = NotchLayout(NotchLayoutMode.NOTCH_TOP, pill = PillGeometry(360, 108, 0, 8)),
                                    onExpandRequest = {}
                                )
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            File("build/screenshots/music_island.png").apply { parentFile?.mkdirs() }.outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            com.frezzybuilds.devnotch.service.MediaNotificationListener.setNowPlayingForTest(null)
        }
    }
}
