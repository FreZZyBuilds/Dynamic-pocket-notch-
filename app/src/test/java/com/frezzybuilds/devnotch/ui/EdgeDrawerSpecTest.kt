package com.frezzybuilds.devnotch.ui

import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.service.AdaptiveLayout
import com.frezzybuilds.devnotch.service.NotchLayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeDrawerSpecTest {

    @Test
    fun `phone portrait - compact tabbed drawer`() {
        val spec = EdgeDrawerSpec.forScreen(392, 851, landscape = false)
        assertEquals(360.dp, spec.width)
        assertEquals(425.dp, spec.height)
        assertFalse(spec.split)
    }

    @Test
    fun `phone landscape - wide split drawer limited by the low height`() {
        val spec = EdgeDrawerSpec.forScreen(851, 392, landscape = true)
        assertEquals(595.dp, spec.width)
        assertEquals(333.dp, spec.height)
        assertTrue(spec.split)
    }

    @Test
    fun `tablet portrait and landscape - split drawer capped at 640x520`() {
        val portrait = EdgeDrawerSpec.forScreen(800, 1280, landscape = false)
        assertEquals(600.dp, portrait.width)
        assertEquals(520.dp, portrait.height)
        assertTrue(portrait.split)

        val landscape = EdgeDrawerSpec.forScreen(1280, 800, landscape = true)
        assertEquals(640.dp, landscape.width)
        assertEquals(520.dp, landscape.height)
        assertTrue(landscape.split)
    }

    @Test
    fun `very small screens never get a drawer larger than the screen`() {
        val spec = EdgeDrawerSpec.forScreen(320, 300, landscape = true)
        assertEquals(304.dp, spec.width)
        assertEquals(268.dp, spec.height)
        assertFalse(spec.split)
    }

    @Test
    fun `tablets always use the edge layout, phones follow the setting`() {
        assertEquals(NotchLayoutMode.EDGE_SIDE, AdaptiveLayout.effectiveMode(NotchLayoutMode.NOTCH_TOP, isTablet = true))
        assertEquals(NotchLayoutMode.NOTCH_TOP, AdaptiveLayout.effectiveMode(NotchLayoutMode.NOTCH_TOP, isTablet = false))
        assertEquals(NotchLayoutMode.EDGE_SIDE, AdaptiveLayout.effectiveMode(NotchLayoutMode.EDGE_SIDE, isTablet = false))
    }
}
