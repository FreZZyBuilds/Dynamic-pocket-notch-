package com.frezzybuilds.devnotch.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.service.MediaNotificationListener
import com.frezzybuilds.devnotch.service.NowPlaying
import com.frezzybuilds.devnotch.system.Sky
import com.frezzybuilds.devnotch.system.WeatherNow
import com.frezzybuilds.devnotch.system.WeatherRepo
import com.frezzybuilds.devnotch.ui.focus.FocusTimerViewModel
import com.frezzybuilds.devnotch.ui.theme.Brand
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Seiten-Ansicht wie im Video: alle sieben Seiten, Wetter-Daten und Timer-Rad. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w760dp-h1000dp-mdpi")
class PagesDashboardTest {

    @get:Rule
    val compose = createComposeRule()

    @After
    fun clear() {
        WeatherRepo.setForTest(WeatherRepo.State.Loading)
        MediaNotificationListener.setNowPlayingForTest(null)
    }

    @Test
    fun `open-meteo response becomes weather`() {
        val json = """{"latitude":49.45,"current":{"time":"2026-10-03T14:00","temperature_2m":25.6,"weather_code":2,"is_day":1}}"""
        val w = WeatherRepo.parse(json)
        assertEquals(26, w.temperature)
        assertEquals(Sky.PARTLY, w.sky)
        assertEquals("Teilweise bewölkt", w.description)
        assertEquals(true, w.isDay)
        assertEquals(Sky.STORM, WeatherRepo.describe(95).first)
        assertEquals(Sky.SNOW, WeatherRepo.describe(73).first)
    }

    @Test
    fun `timer wheel helpers`() {
        assertEquals("17:05", clockLabel(17 * 60 + 5))
        assertEquals("00:30", clockLabel(24 * 60 + 30))
        assertEquals(75, timerDurationTo(16 * 60 + 15, 15 * 60))
        assertEquals(1, timerDurationTo(10, 20))
    }

    @Test
    fun renderAllPages() {
        WeatherRepo.setForTest(WeatherRepo.State.Ready(WeatherNow(26, Sky.PARTLY, "Teilweise bewölkt", true, "Nürnberg", 1L)))
        val playing = NowPlaying("On My Way", "Alan Walker, Sabrina Carpenter", true, "com.spotify.music")
        MediaNotificationListener.setNowPlayingForTest(playing)
        val timer = FocusTimerViewModel()
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MaterialTheme(colorScheme = Brand.NotchScheme) {
                    Column(Modifier.fillMaxSize().background(Color(0xFF3A1A2E)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        NotchPage.entries.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { page ->
                                    Box(Modifier.size(360.dp, 230.dp)) {
                                        PagesDashboard(
                                            pages = listOf(page),
                                            initialPage = null,
                                            focusTimer = timer,
                                            nowPlaying = playing,
                                            onOpenNotification = {},
                                            redact = { it },
                                            onClose = {},
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                        Box(Modifier.size(360.dp, 200.dp)) { WeatherScene(Sky.RAIN, day = false, animate = false, modifier = Modifier.fillMaxSize()) }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("26°").assertExists()
        compose.onNodeWithText("Timer starten").assertExists()
        compose.onNodeWithText("On My Way").assertExists()
        compose.onNodeWithText("Taschenlampe").assertExists()
        compose.onNodeWithText("Suchen oder fragen").assertExists()
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        File("build/screenshots/pages_dashboard.png").apply { parentFile?.mkdirs() }.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
