package com.frezzybuilds.devnotch.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import com.frezzybuilds.devnotch.service.EdgeSide
import com.frezzybuilds.devnotch.service.NotchLayout
import com.frezzybuilds.devnotch.service.NotchLayoutMode
import com.frezzybuilds.devnotch.service.PillGeometry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Wischt über den echten NotchContainer (mdpi: 1 dp = 1 px, Schwelle 40 px). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w480dp-h800dp-mdpi")
class NotchContainerGestureTest {

    @get:Rule
    val compose = createComposeRule()

    private val expandRequests = mutableListOf<Boolean>()

    private fun show(mode: NotchLayoutMode, side: EdgeSide = EdgeSide.RIGHT) {
        compose.setContent {
            NotchContainer(
                layout = NotchLayout(mode, pill = PillGeometry(120, 36, 0, 8), edgeSide = side),
                onExpandRequest = { expandRequests += it }
            )
        }
    }

    private fun swipeBy(dx: Float, dy: Float) = compose.onRoot().performTouchInput {
        swipe(start = center, end = center + Offset(dx, dy), durationMillis = 300)
    }

    @Test
    fun `notch - short drag does nothing, drag down opens, drag up closes`() {
        show(NotchLayoutMode.NOTCH_TOP)

        swipeBy(0f, 25f)
        compose.waitForIdle()
        assertEquals(emptyList<Boolean>(), expandRequests)

        swipeBy(0f, 120f)
        compose.waitForIdle()
        assertEquals(listOf(true), expandRequests)
        compose.onNodeWithText("Timer").assertExists() // Dashboard mit Tabs ist offen

        swipeBy(0f, -120f)
        compose.waitForIdle()
        assertEquals(listOf(true, false), expandRequests)
    }

    @Test
    fun `edge right - drag toward the center opens, toward the edge does not`() {
        show(NotchLayoutMode.EDGE_SIDE, EdgeSide.RIGHT)

        swipeBy(120f, 0f)
        compose.waitForIdle()
        assertEquals(emptyList<Boolean>(), expandRequests)

        swipeBy(-120f, 0f)
        compose.waitForIdle()
        assertEquals(listOf(true), expandRequests)
    }
}
