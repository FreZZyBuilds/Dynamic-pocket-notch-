package com.frezzybuilds.devnotch.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onRoot
import com.frezzybuilds.devnotch.service.NotchLayout
import com.frezzybuilds.devnotch.service.NotchLayoutMode
import com.frezzybuilds.devnotch.service.OverlayWindowFlags
import com.frezzybuilds.devnotch.service.PillGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fenster-Fokus: nur aufgeklappt und nur für Bereiche mit Eingaben (Notizen) bzw. Zwischenablage
 * (Clip) – sonst behält die App dahinter Tastatur und Tasten.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w480dp-h800dp-mdpi")
class OverlayFocusTest {

    @get:Rule
    val compose = createComposeRule()

    private val focusChanges = mutableListOf<Boolean>()
    private val expandRequests = mutableListOf<Boolean>()
    private val lastFocus get() = focusChanges.last()

    private fun show() {
        compose.setContent {
            var backPresses by remember { mutableIntStateOf(0) }
            Column {
                // Simuliert OverlayRootView: Zurück-Taste zählt hoch.
                Text("⟵ Zurück-Taste", Modifier.clickable { backPresses++ })
                NotchContainer(
                    layout = NotchLayout(NotchLayoutMode.NOTCH_TOP, pill = PillGeometry(120, 36, 0, 8)),
                    onExpandRequest = { expandRequests += it },
                    onFocusableChange = { focusChanges += it },
                    backPresses = backPresses
                )
            }
        }
    }

    private fun expand() {
        compose.onRoot().performTouchInput {
            swipe(start = center, end = center + Offset(0f, 120f), durationMillis = 300)
        }
        compose.waitForIdle()
    }

    /** Kategorie über die Übersicht öffnen (erst zurück, falls schon eine offen ist). */
    private fun tab(title: String) {
        if (compose.onAllNodesWithText("Übersicht").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Übersicht").performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithText(title).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `focus only while expanded on notes or clip tab`() {
        show()
        compose.waitForIdle()
        assertFalse("eingeklappt nie fokussierbar", lastFocus)

        expand()
        assertFalse("Übersicht: Tastatur bleibt bei der App dahinter", lastFocus)

        tab("Notizen")
        assertTrue("Notizen: Bildschirmtastatur muss erscheinen können", lastFocus)

        tab("Timer")
        assertFalse(lastFocus)

        tab("Clip")
        assertTrue("Clip: Zwischenablage nur mit Fensterfokus lesbar", lastFocus)

        tab("Notizen")
        assertTrue(lastFocus)
    }

    @Test
    fun `back key collapses and releases focus immediately`() {
        show()
        expand()
        tab("Notizen")
        assertTrue(lastFocus)

        compose.onNodeWithText("⟵ Zurück-Taste").performClick()
        compose.waitForIdle()

        assertEquals(listOf(true, false), expandRequests)
        assertFalse(lastFocus)
        assertEquals(0, compose.onAllNodesWithText("Notizen").fetchSemanticsNodes().size)
    }

    @Test
    fun `flag logic`() {
        assertFalse(OverlayFocus.isFocusable(expanded = false, requests = 2))
        assertFalse(OverlayFocus.isFocusable(expanded = true, requests = 0))
        assertTrue(OverlayFocus.isFocusable(expanded = true, requests = 1))

        assertFalse(OverlayWindowFlags.isFocusable(expanded = false, contentWantsFocus = true))
        assertFalse(OverlayWindowFlags.isFocusable(expanded = true, contentWantsFocus = false))
        assertTrue(OverlayWindowFlags.isFocusable(expanded = true, contentWantsFocus = true))
    }
}
