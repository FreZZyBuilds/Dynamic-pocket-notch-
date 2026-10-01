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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.peek.Peek
import com.frezzybuilds.devnotch.peek.PeekCenter
import com.frezzybuilds.devnotch.service.NotchGeometry
import com.frezzybuilds.devnotch.service.NotchLayout
import com.frezzybuilds.devnotch.service.NotchLayoutMode
import com.frezzybuilds.devnotch.service.PillGeometry
import com.frezzybuilds.devnotch.ui.theme.Brand
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Neuer Look: alle Peek-Arten, Dashboard mit Neon-Rand und Timer im Markenverlauf. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PeekScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @After
    fun clearPeek() {
        PeekCenter.current.value?.let(PeekCenter::dismiss)
    }

    private fun save(view: View, name: String) {
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        File("build/screenshots/$name.png").apply { parentFile?.mkdirs() }.outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w380dp-h560dp-xxhdpi")
    fun renderAllPeeks() {
        lateinit var view: View
        val peeks = listOf(
            Peek.Charging(36),
            Peek.Copied(PeekCenter.previewOf("val notch = DynamicIsland(glow = true) // neu")),
            Peek.TimerDone,
            Peek.TrackChanged("Midnight City", "M83", null)
        )
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MaterialTheme(colorScheme = Brand.NotchScheme) {
                    Column(
                        Modifier.fillMaxSize().background(Color(0xFF2B2F77)).padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        peeks.forEach { peek ->
                            Box(
                                Modifier
                                    .size(320.dp, 35.dp + 46.dp)
                                    .clip(RoundedCornerShape(30.dp))
                                    .background(Color.Black)
                            ) { PeekContent(peek, pillHeight = 35.dp, lensGap = 28.dp) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        save(view, "peeks")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w380dp-h420dp-xxhdpi")
    fun renderBrandedDashboardAndTimer() {
        lateinit var view: View
        val pill = PillGeometry(width = 360, height = 105, x = 0, y = 12)
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalInspectionMode provides true) {
                Box(Modifier.fillMaxSize().background(Color(0xFF3A3F8F)), contentAlignment = Alignment.TopCenter) {
                    NotchContainer(
                        layout = NotchLayout(
                            NotchLayoutMode.NOTCH_TOP,
                            pill = pill,
                            expandedTopInset = NotchGeometry.expandedTopInset(pill, 108)
                        ),
                        onExpandRequest = {}
                    )
                }
            }
        }
        compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, 50f)) }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithText("Timer").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        save(view, "dashboard_timer_branded")
        compose.onNodeWithText("Clip").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        save(view, "dashboard_clip_empty")
    }
}
