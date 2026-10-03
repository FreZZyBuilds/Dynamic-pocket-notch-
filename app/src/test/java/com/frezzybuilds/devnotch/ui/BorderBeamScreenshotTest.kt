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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Lichtlauf um Pille und Peek, beide Paletten, an mehreren Positionen – plus Bildfolge. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BorderBeamScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val position = mutableFloatStateOf(0f)
    private val full = mutableFloatStateOf(1f)

    private fun save(view: View, file: File) {
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        file.apply { parentFile?.mkdirs() }.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w380dp-h560dp-xhdpi")
    fun renderBeams() {
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            Column(
                Modifier.fillMaxSize().background(Color(0xFF1A1630)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Stile mit Gemini-Farben
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BeamStyle.entries.forEach { style ->
                        val shape = RoundedCornerShape(18.dp)
                        Box(
                            Modifier.size(108.dp, 36.dp).clip(shape).background(Color.Black)
                                .borderBeam(shape, remember { mutableFloatStateOf(0.3f) }, full, BeamLook(style = style, palette = BeamPalette.GEMINI))
                        )
                    }
                }
                // Alle Paletten als Strahl
                BeamPalette.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { palette ->
                            val shape = RoundedCornerShape(18.dp)
                            Box(
                                Modifier.size(170.dp, 36.dp).clip(shape).background(Color.Black)
                                    .borderBeam(shape, remember { mutableFloatStateOf(0.42f) }, full, BeamLook(palette = palette))
                            )
                        }
                    }
                }
                // Peek-Größe, Gemini-Ring
                val peekShape = RoundedCornerShape(30.dp)
                Box(
                    Modifier.size(340.dp, 81.dp).clip(peekShape).background(Color.Black)
                        .borderBeam(peekShape, remember { mutableFloatStateOf(0.45f) }, full, BeamLook(style = BeamStyle.RING))
                )
            }
        }
        compose.waitForIdle()
        save(view, File("build/screenshots/beams.png"))
    }

    @Test
    @Config(sdk = [34], qualifiers = "w220dp-h70dp-xhdpi")
    fun renderBeamFrames() {
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            Box(Modifier.fillMaxSize().background(Color(0xFF14102A)), contentAlignment = Alignment.Center) {
                val shape = RoundedCornerShape(20.dp)
                Box(
                    Modifier.size(180.dp, 40.dp).clip(shape).background(Color.Black)
                        .borderBeam(shape, position, full, BeamLook())
                )
            }
        }
        repeat(36) { i ->
            compose.runOnIdle { position.floatValue = i / 36f }
            compose.waitForIdle()
            save(view, File("build/screenshots/beam_frames/f%02d.png".format(i)))
        }
    }
}
