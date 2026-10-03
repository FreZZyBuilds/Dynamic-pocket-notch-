package com.frezzybuilds.devnotch.feature.billing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Paywall mit Beispiel-Angeboten (ohne RevenueCat): Layout, Auswahl, Kauf-Callback. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h900dp-xhdpi")
class PaywallScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val packages = listOf(
        PaywallPackage("\$rc_annual", "Jährlich", "19,99 €", "Bester Preis", null),
        PaywallPackage("\$rc_monthly", "Monatlich", "2,99 €", null, null),
        PaywallPackage("\$rc_lifetime", "Einmalig", "39,99 €", null, null)
    )

    @Test
    fun renderPaywall() {
        lateinit var view: View
        var purchased: String? = null
        compose.setContent {
            view = LocalView.current
            var state by remember {
                mutableStateOf(PaywallState(loading = false, packages = packages, selectedId = packages[0].id))
            }
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.background(Color(0xFF2B2F77))) {
                    PaywallContent(
                        state = state,
                        highlight = ProFeature.AI_TRACKER,
                        onSelect = { id -> state = state.copy(selectedId = id) },
                        onPurchase = { purchased = state.selectedId },
                        onRestore = {},
                        onDismiss = {},
                        debugUnlock = {}
                    )
                }
            }
        }
        compose.onNodeWithText("KI-Token-Tracker").assertIsDisplayed()
        compose.onNodeWithText("Monatlich").performClick()
        compose.onNodeWithText("Pro freischalten – 2,99 €").performClick()
        assertEquals("\$rc_monthly", purchased)

        compose.onNodeWithText("Jährlich").performClick()
        compose.waitForIdle()
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val out = File("build/screenshots/paywall.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(out.length() > 0)
    }
}
