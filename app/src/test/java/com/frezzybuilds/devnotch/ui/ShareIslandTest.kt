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
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.notify.LiveActivity
import com.frezzybuilds.devnotch.peek.Peek
import com.frezzybuilds.devnotch.share.NameDropSession
import com.frezzybuilds.devnotch.share.localsend.DeviceInfo
import com.frezzybuilds.devnotch.share.localsend.FileDto
import com.frezzybuilds.devnotch.share.localsend.IncomingRequest
import com.frezzybuilds.devnotch.ui.theme.Brand
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** NameDrop, LocalSend-Anfrage und -Fortschritt, Bezahlt-Bestätigung in der Insel. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w380dp-h760dp-xxhdpi")
class ShareIslandTest {

    @get:Rule
    val compose = createComposeRule()

    @After
    fun stop() = NameDropSession.stop()

    @Test
    fun renderShareIsland() {
        NameDropSession.start(previewCard())
        val request = IncomingRequest(
            "r",
            DeviceInfo(alias = "Lenas iPhone", deviceModel = "iPhone", fingerprint = "x"),
            listOf(FileDto("1", "Urlaub.jpg", 2_400_000, "image/jpeg"), FileDto("2", "Strand.mov", 18_000_000, "video/quicktime"))
        )
        val items = listOf(
            Peek.NameDrop() to 186.dp,
            Peek.ShareRequest(request) to 128.dp,
            Peek.LiveBanner(LiveActivity.Transfer(true, "Lenas iPhone", 2, 0.42f, "Strand.mov")) to 86.dp,
            Peek.Payment("REWE Markt", "12,50 €") to 100.dp
        )
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MaterialTheme(colorScheme = Brand.NotchScheme) {
                    Column(Modifier.fillMaxSize().background(Color(0xFF2B2E6E)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items.forEach { (peek, height) ->
                            Box(Modifier.size(320.dp, height).clip(RoundedCornerShape(30.dp)).background(Color.Black)) {
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
        File("build/screenshots/share_island.png").apply { parentFile?.mkdirs() }.outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
