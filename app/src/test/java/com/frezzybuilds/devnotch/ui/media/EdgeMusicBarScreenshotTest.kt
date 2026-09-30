package com.frezzybuilds.devnotch.ui.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.service.ArtworkAccent
import com.frezzybuilds.devnotch.service.NowPlaying
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Rendert die Edge-Leiste in mehreren Zuständen als PNG nach app/build/screenshots/
 * (echtes Android-Rendering via Robolectric Native Graphics). Dient als Design-Vorschau und
 * Smoke-Test, dass Layout und Animationen ohne Absturz zeichnen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h460dp-xxhdpi")
class EdgeMusicBarScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun gradientArtwork(from: Int, to: Int): Bitmap =
        Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).also { bmp ->
            Canvas(bmp).drawRect(0f, 0f, 160f, 160f, Paint().apply {
                shader = LinearGradient(0f, 0f, 160f, 160f, from, to, Shader.TileMode.CLAMP)
            })
        }

    private val defaultTheme = NowPlaying(
        title = "Advance Soul", artist = "Adam's", isPlaying = true, packageName = "demo"
    )
    private val blueArtwork = NowPlaying(
        title = "Midnight City – Extended Mix", artist = "M83", isPlaying = false, packageName = "demo",
        artwork = gradientArtwork(0xFF4FC3F7.toInt(), 0xFF1A237E.toInt()),
        accent = ArtworkAccent(0xFF42A5F5.toInt(), 0xFF1A237E.toInt())
    )
    private val yellowArtwork = NowPlaying(
        title = "Golden Hour", artist = "JVKE", isPlaying = true, packageName = "demo",
        artwork = gradientArtwork(0xFFFFF176.toInt(), 0xFFFFA000.toInt()),
        accent = ArtworkAccent(0xFFFFEB3B.toInt(), 0xFFFFB300.toInt())
    )

    /** Leisten so, wie sie am rechten Rand kleben: nur die Innenseite abgerundet. */
    @Composable
    private fun Docked(content: @Composable () -> Unit) {
        Box(
            Modifier
                .size(60.dp, 340.dp)
                .clip(RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp))
        ) { content() }
    }

    @Test
    fun renderEdgeStates() {
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            // Vorschau-Modus: Equalizer und Lauftext als Standbild, damit Compose idle wird.
            CompositionLocalProvider(LocalInspectionMode provides true) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Row(
                    modifier = Modifier
                        .background(Brush.verticalGradient(listOf(Color(0xFF2B2F77), Color(0xFF6A3DE8))))
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Docked { EdgeMusicBar(defaultTheme, {}, {}, {}) }
                    Docked { EdgeMusicBar(blueArtwork, {}, {}, {}) }
                    Docked { EdgeMusicBar(yellowArtwork, {}, {}, {}) }
                    Box(
                        Modifier
                            .size(20.dp, 120.dp)
                            .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                    ) { EdgeHandle() }
                }
            }
            }
        }

        compose.waitForIdle()
        // Direkt über Skia zeichnen (Robolectric Native Graphics) – captureToImage wartet auf
        // Frame-Commit-Callbacks, die Robolectric nicht liefert.
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val out = File("build/screenshots/edge_music_bar.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
