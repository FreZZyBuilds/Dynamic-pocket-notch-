package com.frezzybuilds.devnotch.service

import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeDockTest {

    private val screen = 1080
    private val bubble = 176 // 64 dp bei 2,75x

    @Test
    fun `released near the right edge stays right`() {
        val snap = EdgeDock.snap(EdgeSide.RIGHT, x = 120, windowWidth = bubble, screenWidth = screen)
        assertEquals(EdgeDock.Snap(EdgeSide.RIGHT, 120), snap)
    }

    @Test
    fun `dragged past the middle switches to the left edge in left coordinates`() {
        // Rechts angedockt, 700 px nach innen gezogen → Mitte bei 1080 - 700 - 88 = 292 (linke Hälfte)
        val snap = EdgeDock.snap(EdgeSide.RIGHT, x = 700, windowWidth = bubble, screenWidth = screen)
        assertEquals(EdgeSide.LEFT, snap.side)
        // Gleiche Bildschirmposition, jetzt vom linken Rand aus: 1080 - 700 - 176 = 204
        assertEquals(204, snap.startX)
    }

    @Test
    fun `left docked bubble dragged to the right half switches right`() {
        val snap = EdgeDock.snap(EdgeSide.LEFT, x = 800, windowWidth = bubble, screenWidth = screen)
        assertEquals(EdgeDock.Snap(EdgeSide.RIGHT, 104), snap)
    }

    @Test
    fun `vertical offset is kept on screen`() {
        assertEquals(1112, EdgeDock.clampY(5_000, windowHeight = 176, screenHeight = 2400))
        assertEquals(-1112, EdgeDock.clampY(-5_000, windowHeight = 176, screenHeight = 2400))
        assertEquals(300, EdgeDock.clampY(300, windowHeight = 176, screenHeight = 2400))
    }
}
