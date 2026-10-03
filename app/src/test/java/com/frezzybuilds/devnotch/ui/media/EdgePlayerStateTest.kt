package com.frezzybuilds.devnotch.ui.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EdgePlayerStateTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * Titel- und Sichtbarkeitswechsel laufen – wie in der App über den StateFlow – innerhalb der
     * Composition: Test-Buttons ändern den lokalen State.
     */
    private fun setUp(autoMinimize: Boolean = true) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            var track by remember { mutableStateOf("song-1") }
            var active by remember { mutableStateOf(true) }
            val state = rememberEdgePlayerState(track, autoMinimize, active)
            Column {
                if (state.minimized) {
                    Text("bubble", Modifier.clickable(onClick = state::restore))
                } else {
                    Text("bar", Modifier.clickable(onClick = state::onInteraction))
                }
                Text("next-track", Modifier.clickable { track = "song-2" })
                Text("hide", Modifier.clickable { active = false })
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `collapses to bubble after 5 seconds and tap restores the bar`() {
        setUp()
        compose.mainClock.advanceTimeBy(4_800)
        compose.onNodeWithText("bar").assertExists()

        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("bubble").assertExists().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("bar").assertExists()
    }

    @Test
    fun `interaction restarts the countdown`() {
        setUp()
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithText("bar").performClick()
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithText("bar").assertExists()

        compose.mainClock.advanceTimeBy(1_200)
        compose.onNodeWithText("bubble").assertExists()
    }

    @Test
    fun `new track shows the full bar again`() {
        setUp()
        compose.mainClock.advanceTimeBy(5_200)
        compose.onNodeWithText("bubble").assertExists()

        compose.onNodeWithText("next-track").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("bar").assertExists()
    }

    @Test
    fun `stays expanded when the option is off`() {
        setUp(autoMinimize = false)
        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithText("bar").assertExists()
    }

    @Test
    fun `does not collapse while the bar is not visible`() {
        setUp()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("hide").performClick()
        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithText("bar").assertExists()
    }
}
