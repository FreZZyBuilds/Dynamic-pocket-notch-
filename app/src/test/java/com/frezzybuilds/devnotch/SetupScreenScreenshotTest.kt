package com.frezzybuilds.devnotch

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import android.graphics.Canvas
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.frezzybuilds.devnotch.ui.glass.AuroraBackground
import com.frezzybuilds.devnotch.ui.glass.Glass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Einstellungen im „Neon Glass“-Look, in voller Länge (hoher Bildschirm statt Scrollen). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SetupScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(sdk = [34], qualifiers = "w392dp-h2100dp-xhdpi")
    fun renderSetupScreen() {
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MaterialTheme(colorScheme = Glass.AppScheme) {
                    AuroraBackground { SetupScreen() }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Einrichtung").assertExists()
        save(view, "setup_glass.png")
    }

    /** Alle Karten aufgeklappt: Benachrichtigungen, Live-Ansichten, Sperrbildschirm, Größe. */
    @Test
    @Config(sdk = [34], qualifiers = "w392dp-h5200dp-xhdpi")
    fun renderSetupScreenExpanded() {
        val settings = ApplicationProvider.getApplicationContext<Context>().appContainer.notchSettings
        listOf("actions", "look", "beam", "notify", "live", "lock").forEach { settings.setSectionOpen(it, true) }
        settings.rememberSeenApp("com.whatsapp", "WhatsApp")
        settings.rememberSeenApp("com.google.android.gm", "Gmail")
        settings.notifyBlockedApps = setOf("com.google.android.gm")
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MaterialTheme(colorScheme = Glass.AppScheme) {
                    AuroraBackground { SetupScreen() }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("In der Notch anzeigen").assertExists()
        compose.onNodeWithText("Gesperrt nur App", substring = true).assertExists()
        save(view, "setup_sections.png")
    }

    private fun save(view: View, name: String) {
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        File("build/screenshots/$name").apply { parentFile?.mkdirs() }.outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
