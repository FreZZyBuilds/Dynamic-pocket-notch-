package com.frezzybuilds.devnotch.peek

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.frezzybuilds.devnotch.service.NotchLayout
import com.frezzybuilds.devnotch.service.NotchLayoutMode
import com.frezzybuilds.devnotch.service.PillGeometry
import com.frezzybuilds.devnotch.ui.NotchContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h800dp-mdpi")
class PeekTest {

    @get:Rule
    val compose = createComposeRule()

    @After
    fun clear() {
        PeekCenter.current.value?.let(PeekCenter::dismiss)
    }

    @Test
    fun `newer peek survives dismissal of the older one`() {
        val old = Peek.Charging(40)
        val new = Peek.Copied("x")
        PeekCenter.show(old)
        PeekCenter.show(new)
        PeekCenter.dismiss(old)
        assertEquals(new, PeekCenter.current.value)
        PeekCenter.dismiss(new)
        assertNull(PeekCenter.current.value)
    }

    @Test
    fun `preview is the first non-empty line, shortened`() {
        assertEquals("hello", PeekCenter.previewOf("\n  hello  \nworld"))
        val preview = PeekCenter.previewOf("a".repeat(80), max = 20)
        assertEquals(20, preview.length)
        assertTrue(preview.endsWith("…"))
    }

    private val peekChanges = mutableListOf<Boolean>()

    private fun show() {
        compose.setContent {
            NotchContainer(
                layout = NotchLayout(NotchLayoutMode.NOTCH_TOP, pill = PillGeometry(120, 36, 0, 8)),
                onExpandRequest = {},
                onPeekChange = { peekChanges += (it != null) }
            )
        }
        compose.waitForIdle()
    }

    @Test
    fun `peek grows the pill, ends after its duration and reports window changes`() {
        show()
        compose.mainClock.autoAdvance = false
        PeekCenter.show(Peek.TimerDone)
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Fokuszeit vorbei").assertExists()
        assertEquals(true, peekChanges.last())

        compose.mainClock.advanceTimeBy(Peek.TimerDone.durationMs)
        assertNull(PeekCenter.current.value)
        assertEquals(false, peekChanges.last())
    }

    @Test
    fun `expanding the notch dismisses a running peek`() {
        show()
        PeekCenter.show(Peek.Charging(80))
        compose.waitForIdle()
        compose.onNodeWithText("80 %").assertExists()

        compose.onRoot().performTouchInput { swipe(center, center + Offset(0f, 150f), 300) }
        compose.waitForIdle()
        assertNull(PeekCenter.current.value)
        assertEquals(false, peekChanges.last())
    }
}
