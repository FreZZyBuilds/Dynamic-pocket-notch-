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

    /** Vom Nutzer einstellbar (Regler bzw. Ziehen am Griff). */
    val DASHBOARD_WIDTH_RANGE = 300..480
    val DASHBOARD_HEIGHT_RANGE = 240..600

    /** Mindestabstand des Dashboards zu den Bildschirmrändern. */
    private const val SIDE_MARGIN_DP = 8

    /** Nie breiter als der Bildschirm (Samsung mit großem Bildschirmzoom: ~360 dp gesamt). */
    fun dashboardWidthDp(screenWidthDp: Int, wantedDp: Int = DASHBOARD_MAX_WIDTH_DP): Int =
        minOf(wantedDp, screenWidthDp - 2 * SIDE_MARGIN_DP)

    /** Höchstens 80 % der Bildschirmhöhe, damit das Dashboard nie über den Rand ragt. */
    fun dashboardHeightDp(screenHeightDp: Int, wantedDp: Int = DASHBOARD_HEIGHT_DP): Int =
        minOf(wantedDp, (screenHeightDp * 0.8f).toInt())

    /** Peek: Pille wächst nach unten – Zeile mit der Linse plus eine Textzeile darunter. */
    const val PEEK_MAX_WIDTH_DP = 320
    const val PEEK_EXTRA_HEIGHT_DP = 46

    fun peekWidthDp(screenWidthDp: Int): Int =
        minOf(PEEK_MAX_WIDTH_DP, screenWidthDp - 2 * SIDE_MARGIN_DP)

    /** @return Breite und Höhe des Peeks in dp; [pillHeightDp] ist die eingeklappte Pillenhöhe. */
    fun peek(screenWidthDp: Int, pillHeightDp: Float, extraHeightDp: Int = PEEK_EXTRA_HEIGHT_DP): Pair<Float, Float> =
        peekWidthDp(screenWidthDp).toFloat() to pillHeightDp + extraHeightDp

    /** Platz je Seite neben der Linse für Timer, Kosten oder Live-Werte („⏱ 24:59“, „200 m“). */
    const val WIDE_SIDE_DP = 52
    private const val WIDE_PADDING_DP = 20

    /**
     * Eingeklappte Pille mit Text neben der Kamera: breit genug für je einen Wert links und
     * rechts der Linse, nie schmaler als die normale Pille und nie breiter als der Bildschirm.
     */
    fun widePillWidthDp(screenWidthDp: Int, pillWidthDp: Int, lensGapDp: Int): Int =
        minOf(maxOf(pillWidthDp, lensGapDp + 2 * WIDE_SIDE_DP + WIDE_PADDING_DP), screenWidthDp - 2 * SIDE_MARGIN_DP)

    /**
     * @param topInsetDp Abstand für Statusleiste/Kamera über dem Dashboard-Inhalt (nur Notch).
     * @return Breite und Höhe in dp.
     */
    fun of(
        mode: NotchLayoutMode,
        screenWidthDp: Int,
        screenHeightDp: Int,
        landscape: Boolean,
        topInsetDp: Float,
        wantedWidthDp: Int = DASHBOARD_MAX_WIDTH_DP,
        wantedHeightDp: Int = DASHBOARD_HEIGHT_DP
    ): Pair<Float, Float> = when (mode) {
        NotchLayoutMode.NOTCH_TOP ->
            dashboardWidthDp(screenWidthDp, wantedWidthDp).toFloat() to
                dashboardHeightDp(screenHeightDp, wantedHeightDp) + topInsetDp
        NotchLayoutMode.EDGE_SIDE ->
            EdgeDrawerSpec.forScreen(screenWidthDp, screenHeightDp, landscape).let { it.width.value to it.height.value }
    }
}
