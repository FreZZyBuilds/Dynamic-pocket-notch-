package com.frezzybuilds.devnotch.ui

import com.frezzybuilds.devnotch.service.EdgeSide
import com.frezzybuilds.devnotch.service.NotchLayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotchGesturesTest {

    private val threshold = 110f // 40 dp bei 2,75x

    private fun resolve(mode: NotchLayoutMode, expanded: Boolean, distance: Float, side: EdgeSide = EdgeSide.RIGHT) =
        NotchGestures.resolve(mode, side, expanded, distance, threshold)

    @Test
    fun `notch - drag down beyond 40dp expands, shorter drags do nothing`() {
        assertNull(resolve(NotchLayoutMode.NOTCH_TOP, expanded = false, distance = 100f))
        assertEquals(NotchGestureAction.EXPAND, resolve(NotchLayoutMode.NOTCH_TOP, expanded = false, distance = 120f))
    }

    @Test
    fun `notch - drag up collapses only when expanded`() {
        assertEquals(NotchGestureAction.COLLAPSE, resolve(NotchLayoutMode.NOTCH_TOP, expanded = true, distance = -120f))
        assertNull(resolve(NotchLayoutMode.NOTCH_TOP, expanded = false, distance = -300f))
        assertNull(resolve(NotchLayoutMode.NOTCH_TOP, expanded = true, distance = 300f))
    }

    @Test
    fun `edge right - dragging left toward the center opens, right closes`() {
        assertEquals(NotchGestureAction.EXPAND, resolve(NotchLayoutMode.EDGE_SIDE, expanded = false, distance = -120f))
        assertNull(resolve(NotchLayoutMode.EDGE_SIDE, expanded = false, distance = 120f))
        assertEquals(NotchGestureAction.COLLAPSE, resolve(NotchLayoutMode.EDGE_SIDE, expanded = true, distance = 120f))
    }

    @Test
    fun `edge left - directions are mirrored`() {
        assertEquals(
            NotchGestureAction.EXPAND,
            resolve(NotchLayoutMode.EDGE_SIDE, expanded = false, distance = 120f, side = EdgeSide.LEFT)
        )
        assertEquals(
            NotchGestureAction.COLLAPSE,
            resolve(NotchLayoutMode.EDGE_SIDE, expanded = true, distance = -120f, side = EdgeSide.LEFT)
        )
    }

    @Test
    fun `swiping up on a collapsed peek pushes it away`() {
        assertNull("ohne Einblendung nichts", resolve(NotchLayoutMode.NOTCH_TOP, expanded = false, distance = -200f))
        assertEquals(
            NotchGestureAction.COLLAPSE,
            NotchGestures.resolve(NotchLayoutMode.NOTCH_TOP, EdgeSide.RIGHT, expanded = false, distance = -200f, threshold = threshold, peeking = true)
        )
    }
}
