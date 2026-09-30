package com.frezzybuilds.devnotch.service

/** Wo die Notch auf dem Bildschirm sitzt (Einstellung „Display Mode“). */
enum class NotchLayoutMode(val label: String) {
    /** Oben zentriert bzw. um das Punch-Hole der Kamera herum. */
    NOTCH_TOP("Punchhole Center"),

    /** Vertikal zentriert am Bildschirmrand (Gravity.END), wie das Samsung Edge-Panel. */
    EDGE_SIDE("Floating Edge Bar")
}

/** Obere Kamera-Aussparung in Bildschirm-Pixeln, bezogen auf die aktuelle Rotation. */
data class CameraCutout(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val centerX: Int get() = (left + right) / 2
}

data class NotchLayout(
    val mode: NotchLayoutMode,
    val cutout: CameraCutout? = null
)
