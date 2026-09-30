package com.frezzybuilds.devnotch.service

/** Wo die Notch auf dem Bildschirm sitzt. */
enum class NotchLayoutMode {
    /** Oben zentriert bzw. um das Punch-Hole der Kamera herum (Smartphones). */
    NOTCH_TOP,

    /** Vertikal zentriert am linken oder rechten Bildschirmrand (Tablets). */
    EDGE_SIDE
}

enum class EdgeSide { LEFT, RIGHT }

/** Obere Kamera-Aussparung in Bildschirm-Pixeln, bezogen auf die aktuelle Rotation. */
data class CameraCutout(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val centerX: Int get() = (left + right) / 2
}

data class NotchLayout(
    val mode: NotchLayoutMode,
    val side: EdgeSide = EdgeSide.RIGHT,
    val cutout: CameraCutout? = null
)
