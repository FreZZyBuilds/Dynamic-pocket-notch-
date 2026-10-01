package com.frezzybuilds.devnotch.ui

import com.frezzybuilds.devnotch.service.NotchGeometry
import com.frezzybuilds.devnotch.service.NotchLayoutMode
import com.frezzybuilds.devnotch.service.PillGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedSizeTest {

    @Test
    fun `dashboard never wider than the screen minus margin`() {
        // Samsung mit großem Bildschirmzoom: 1080 px / 3,0 = 360 dp – vorher 360 dp Dashboard → abgeschnitten.
        assertEquals(344, ExpandedSize.dashboardWidthDp(360))
        assertEquals(360, ExpandedSize.dashboardWidthDp(412))
        assertEquals(360, ExpandedSize.dashboardWidthDp(384))
    }

    @Test
    fun `notch height includes the status bar inset`() {
        val (w, h) = ExpandedSize.of(NotchLayoutMode.NOTCH_TOP, 360, 780, landscape = false, topInsetDp = 36f)
        assertEquals(344f, w)
        assertEquals(280f + 36f, h)
    }

    @Test
    fun `edge size equals the drawer and fits the screen`() {
        val (w, h) = ExpandedSize.of(NotchLayoutMode.EDGE_SIDE, 360, 780, landscape = false, topInsetDp = 99f)
        val drawer = EdgeDrawerSpec.forScreen(360, 780, landscape = false)
        assertEquals(drawer.width.value, w)
        assertEquals(drawer.height.value, h)
        assertTrue(w <= 360 - 16)
    }

    @Test
    fun `top inset reaches below status bar and pill`() {
        // Pille 20 px unter der Oberkante, 96 px hoch → Unterkante 116; Statusleiste 110 px.
        val pill = PillGeometry(width = 330, height = 96, x = 0, y = 20)
        assertEquals(96, NotchGeometry.expandedTopInset(pill, statusBarHeight = 110))
        // Hohe Statusleiste (z. B. große Kamera): die Statusleiste bestimmt.
        assertEquals(130, NotchGeometry.expandedTopInset(pill, statusBarHeight = 150))
        // Pille ragt minimal über die Kante (y < 0): Fenster beginnt bei 0.
        val raised = PillGeometry(width = 330, height = 96, x = 0, y = -4)
        assertEquals(92, NotchGeometry.expandedTopInset(raised, statusBarHeight = 60))
    }

    @Test
    fun `user size is applied but never exceeds the screen`() {
        // Gewünscht 420 × 520 dp auf 412 × 915: Breite durch den Rand begrenzt, Höhe passt.
        val (w, h) = ExpandedSize.of(NotchLayoutMode.NOTCH_TOP, 412, 915, landscape = false, topInsetDp = 36f, wantedWidthDp = 420, wantedHeightDp = 520)
        assertEquals(412f - 16f, w)
        assertEquals(520f + 36f, h)
        // Querformat (Höhe 412): höchstens 80 % der Höhe.
        assertEquals(329, ExpandedSize.dashboardHeightDp(412, 600))
        // Kleiner Wunsch bleibt klein.
        assertEquals(240, ExpandedSize.dashboardHeightDp(915, 240))
    }
}
