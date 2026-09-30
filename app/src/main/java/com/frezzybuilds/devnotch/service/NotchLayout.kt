package com.frezzybuilds.devnotch.service

import kotlin.math.roundToInt

/** Wo die Notch auf dem Bildschirm sitzt (Einstellung „Display Mode“). */
enum class NotchLayoutMode(val label: String, val description: String) {
    /** Oben zentriert bzw. um das Punch-Hole der Kamera herum. */
    NOTCH_TOP("Center Notch (Punch-Hole)", "Oben mittig, umschließt die Frontkamera"),

    /** Vertikal zentriert am Bildschirmrand (Gravity.END), wie das Samsung Edge-Panel. */
    EDGE_SIDE("Edge Dock (Tablet/Phone Side)", "Schmale Leiste am rechten Bildschirmrand")
}

/** Kameralinse in Bildschirm-Pixeln, bezogen auf die aktuelle Rotation. */
data class CameraLens(val centerX: Int, val centerY: Int, val diameter: Int) {
    companion object {
        /**
         * Leitet die Linse aus dem Bounding-Rect der Aussparung ab. Manche Geräte melden ein Rect,
         * das bis an die Bildschirmkante reicht (höher als breit): Die Linse sitzt dann am unteren
         * Ende und ihr Durchmesser ist die kürzere Seite.
         */
        fun fromBounds(left: Int, top: Int, right: Int, bottom: Int): CameraLens {
            val diameter = minOf(right - left, bottom - top)
            return CameraLens(
                centerX = (left + right) / 2,
                centerY = bottom - diameter / 2,
                diameter = diameter
            )
        }
    }
}

/**
 * Größe und Fensterposition der eingeklappten Pille in Pixeln.
 * [x] ist der Versatz zur Bildschirmmitte (Gravity.CENTER_HORIZONTAL), [y] der Abstand von oben.
 */
data class PillGeometry(val width: Int, val height: Int, val x: Int, val y: Int)

/** An welchem Bildschirmrand der Edge-Player andockt (physisch, unabhängig von RTL). */
enum class EdgeSide { LEFT, RIGHT }

data class NotchLayout(
    val mode: NotchLayoutMode,
    val lens: CameraLens? = null,
    val pill: PillGeometry,
    val edgeSide: EdgeSide = EdgeSide.RIGHT
)

/**
 * Loslassen nach dem Verschieben der Bubble: Sie rastet am näheren Rand ein.
 *
 * [x] ist der Fensterversatz vom aktuellen Andock-Rand nach innen (Gravity.LEFT bzw. RIGHT).
 * Wechselt der Rand, wird [x] in die Koordinaten des neuen Randes umgerechnet, damit die
 * Einrast-Animation dort startet, wo der Finger losgelassen hat.
 */
object EdgeDock {
    data class Snap(val side: EdgeSide, val startX: Int)

    fun snap(side: EdgeSide, x: Int, windowWidth: Int, screenWidth: Int): Snap {
        val centerFromLeft = when (side) {
            EdgeSide.LEFT -> x + windowWidth / 2
            EdgeSide.RIGHT -> screenWidth - x - windowWidth / 2
        }
        val target = if (centerFromLeft < screenWidth / 2) EdgeSide.LEFT else EdgeSide.RIGHT
        val startX = if (target == side) x else screenWidth - x - windowWidth
        return Snap(target, startX)
    }

    /** Vertikaler Versatz (CENTER_VERTICAL) so begrenzen, dass das Fenster sichtbar bleibt. */
    fun clampY(y: Int, windowHeight: Int, screenHeight: Int): Int {
        val limit = (screenHeight - windowHeight) / 2
        return y.coerceIn(-limit.coerceAtLeast(0), limit.coerceAtLeast(0))
    }
}

object NotchGeometry {
    const val PILL_WIDTH_DP = 120f
    const val PILL_HEIGHT_DP = 35f

    /** Schwarzer Rand oberhalb/unterhalb der Linse. */
    private const val LENS_MARGIN_VERTICAL_DP = 8f

    /** Schwarzer Rand links/rechts der Linse. */
    private const val LENS_MARGIN_HORIZONTAL_DP = 24f

    /** Abstand zur Oberkante auf Geräten ohne Kamera-Aussparung. */
    const val NO_CUTOUT_TOP_OFFSET_DP = 8f

    /**
     * Ohne Linse: 120×35 dp, horizontal zentriert, 8 dp von oben.
     * Mit Linse: mindestens 120×35 dp, Mittelpunkt der Pille = Mittelpunkt der Linse.
     * Breite und Höhe sind gerade, damit links/rechts und oben/unten exakt gleich viel Rand bleibt.
     */
    fun collapsedPill(lens: CameraLens?, screenWidth: Int, density: Float): PillGeometry {
        fun dp(value: Float) = (value * density).roundToInt()

        if (lens == null) {
            return PillGeometry(
                width = dp(PILL_WIDTH_DP).roundUpToEven(),
                height = dp(PILL_HEIGHT_DP).roundUpToEven(),
                x = 0,
                y = dp(NO_CUTOUT_TOP_OFFSET_DP)
            )
        }
        val width = maxOf(dp(PILL_WIDTH_DP), lens.diameter + 2 * dp(LENS_MARGIN_HORIZONTAL_DP)).roundUpToEven()
        val height = maxOf(dp(PILL_HEIGHT_DP), lens.diameter + 2 * dp(LENS_MARGIN_VERTICAL_DP)).roundUpToEven()
        return PillGeometry(
            width = width,
            height = height,
            // CENTER_HORIZONTAL: Fenstermitte = Bildschirmmitte + x → x = Linse − Bildschirmmitte.
            x = lens.centerX - screenWidth / 2,
            // Kann negativ sein, wenn die Linse sehr nah an der Kante sitzt (Pille ragt minimal raus).
            y = lens.centerY - height / 2
        )
    }

    private fun Int.roundUpToEven() = if (this % 2 == 0) this else this + 1
}
