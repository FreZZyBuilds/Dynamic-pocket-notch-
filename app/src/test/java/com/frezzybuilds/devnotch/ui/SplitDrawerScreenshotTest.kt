package com.frezzybuilds.devnotch.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.service.NotchLayout
import com.frezzybuilds.devnotch.service.NotchLayoutMode
import com.frezzybuilds.devnotch.service.NowPlaying
import com.frezzybuilds.devnotch.service.PillGeometry
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import com.frezzybuilds.devnotch.ui.focus.FocusTimerViewModel
import com.frezzybuilds.devnotch.ui.media.EdgeHandle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Tablet-Hochformat: eingeklappte Griffleiste und aufgeklappter Split-Drawer (600×520 dp). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w720dp-h600dp-mdpi")
class SplitDrawerScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun renderTabletDrawer() {
        lateinit var view: View
        val timer = FocusTimerViewModel()
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Row(
                        modifier = Modifier
                            .background(Brush.verticalGradient(listOf(Color(0xFF2B2F77), Color(0xFF6A3DE8))))
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(12.dp, 100.dp)
                                .clip(AbsoluteRoundedCornerShape(topLeft = 6.dp, bottomLeft = 6.dp))
                        ) { EdgeHandle() }
                        Box(
                            Modifier
                                .size(600.dp, 520.dp)
                                .clip(AbsoluteRoundedCornerShape(topLeft = 24.dp, bottomLeft = 24.dp))
                                .background(Color.Black)
                        ) {
                            SplitDrawer(
                                focusTimer = timer,
                                nowPlaying = NowPlaying("Midnight City", "M83", isPlaying = true, packageName = "demo"),
                                leftPane = DrawerPane.TIMER,
                                rightPane = DrawerPane.NOTES,
                                onSelectLeft = {},
                                onSelectRight = {},
                                onClose = {}
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val out = File("build/screenshots/split_drawer.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Smartphone: aufgeklappte Notch mit fünf Tabs inkl. „Notizen“ – passen alle Beschriftungen? */
    @Test
    @Config(sdk = [34], qualifiers = "w400dp-h340dp-xhdpi")
    fun renderPhoneDashboardTabs() {
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalInspectionMode provides true) {
                Box(Modifier.background(Color(0xFF3A3F8F)).padding(20.dp)) {
                    NotchContainer(
                        layout = NotchLayout(NotchLayoutMode.NOTCH_TOP, pill = PillGeometry(240, 70, 0, 16)),
                        onExpandRequest = {}
                    )
                }
            }
        }
        compose.onRoot().performTouchInput { click(center) }
        compose.onNodeWithText("Notizen").performClick()
        // Die erste Notiz legt der ViewModel asynchron in Room an.
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2 }
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("Sprint 42")
        fields[1].performTextInput("- [x] Room-Migration\n- [ ] `./gradlew test`\n- Notch-Gesten prüfen")
        compose.waitForIdle()
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val out = File("build/screenshots/phone_tabs.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
