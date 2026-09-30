package com.frezzybuilds.devnotch.service

import org.junit.Assert.assertEquals
import org.junit.Test

class NotchGeometryTest {

    private val density = 2.75f // z. B. 1080×2400 bei 440 dpi
    private val screenWidth = 1080

    /** Wo das Fenster bei Gravity.TOP | CENTER_HORIZONTAL tatsächlich landet (wie Gravity.apply). */
    private fun PillGeometry.left() = (screenWidth - width) / 2 + x
    private fun PillGeometry.right() = left() + width
    private fun PillGeometry.bottom() = y + height

    @Test
    fun `without cutout the pill is 120x35dp, centered, 8dp from the top`() {
        val pill = NotchGeometry.collapsedPill(lens = null, screenWidth, density)

        assertEquals(330, pill.width)  // 120 dp
        assertEquals(96, pill.height)  // 35 dp = 96,25 px → 96
        assertEquals(0, pill.x)
        assertEquals(22, pill.y)       // 8 dp
    }

    @Test
    fun `centered punch hole - equal margin on all opposite sides`() {
        // Linse Ø 44 px, Mittelpunkt (540, 60)
        val lens = CameraLens.fromBounds(518, 38, 562, 82)
        val pill = NotchGeometry.collapsedPill(lens, screenWidth, density)

        assertEquals(lens.centerX - pill.left(), pill.right() - lens.centerX)
        assertEquals(lens.centerY - pill.y, pill.bottom() - lens.centerY)
        assertEquals(0, pill.x)
    }

    @Test
    fun `off-center punch hole shifts the window by the lens offset`() {
        // Linse links oben, Mittelpunkt (120, 70)
        val lens = CameraLens.fromBounds(98, 48, 142, 92)
        val pill = NotchGeometry.collapsedPill(lens, screenWidth, density)

        assertEquals(120 - 540, pill.x)
        assertEquals(lens.centerX - pill.left(), pill.right() - lens.centerX)
        assertEquals(lens.centerY - pill.y, pill.bottom() - lens.centerY)
    }

    @Test
    fun `cutout rect reaching the screen edge is treated as a lens at its lower end`() {
        // Rect von der Oberkante bis unter die Linse: 44 breit, 90 hoch
        val lens = CameraLens.fromBounds(518, 0, 562, 90)

        assertEquals(44, lens.diameter)
        assertEquals(540, lens.centerX)
        assertEquals(68, lens.centerY)
    }

    @Test
    fun `large lens grows the pill beyond the minimum size`() {
        val lens = CameraLens.fromBounds(440, 20, 640, 220) // Ø 200 px
        val pill = NotchGeometry.collapsedPill(lens, screenWidth, density)

        assertEquals(200 + 2 * 66, pill.width)  // 24 dp Rand je Seite, > 120 dp Minimum
        assertEquals(200 + 2 * 22, pill.height) // 8 dp Rand oben/unten, > 35 dp Minimum
    }
}
