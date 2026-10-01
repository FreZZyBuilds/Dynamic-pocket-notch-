package com.frezzybuilds.devnotch.ui

import com.frezzybuilds.devnotch.service.NotchLayoutMode

/**
 * Größe des aufgeklappten Overlays in dp – eine Quelle für Compose (Inhalt) und den Service
 * (Fenstergröße). Der Service setzt das Fenster beim Aufklappen einmal auf diese Größe; die
 * Feder-Animation läuft danach nur innerhalb des Fensters statt pro Bild das Fenster neu zu
 * layouten (das war die Ursache fürs Ruckeln).
 */
object ExpandedSize {
    const val DASHBOARD_MAX_WIDTH_DP = 360
    const val DASHBOARD_HEIGHT_DP = 280

    /** Mindestabstand des Dashboards zu den Bildschirmrändern. */
    private const val SIDE_MARGIN_DP = 8

    /** Nie breiter als der Bildschirm (Samsung mit großem Bildschirmzoom: ~360 dp gesamt). */
    fun dashboardWidthDp(screenWidthDp: Int): Int =
        minOf(DASHBOARD_MAX_WIDTH_DP, screenWidthDp - 2 * SIDE_MARGIN_DP)

    /** Peek: Pille wächst nach unten – Zeile mit der Linse plus eine Textzeile darunter. */
    const val PEEK_MAX_WIDTH_DP = 320
    const val PEEK_EXTRA_HEIGHT_DP = 46

    fun peekWidthDp(screenWidthDp: Int): Int =
        minOf(PEEK_MAX_WIDTH_DP, screenWidthDp - 2 * SIDE_MARGIN_DP)

    /** @return Breite und Höhe des Peeks in dp; [pillHeightDp] ist die eingeklappte Pillenhöhe. */
    fun peek(screenWidthDp: Int, pillHeightDp: Float): Pair<Float, Float> =
        peekWidthDp(screenWidthDp).toFloat() to pillHeightDp + PEEK_EXTRA_HEIGHT_DP

    /**
     * @param topInsetDp Abstand für Statusleiste/Kamera über dem Dashboard-Inhalt (nur Notch).
     * @return Breite und Höhe in dp.
     */
    fun of(
        mode: NotchLayoutMode,
        screenWidthDp: Int,
        screenHeightDp: Int,
        landscape: Boolean,
        topInsetDp: Float
    ): Pair<Float, Float> = when (mode) {
        NotchLayoutMode.NOTCH_TOP ->
            dashboardWidthDp(screenWidthDp).toFloat() to DASHBOARD_HEIGHT_DP + topInsetDp
        NotchLayoutMode.EDGE_SIDE ->
            EdgeDrawerSpec.forScreen(screenWidthDp, screenHeightDp, landscape).let { it.width.value to it.height.value }
    }
}
