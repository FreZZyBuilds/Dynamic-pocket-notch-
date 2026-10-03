package com.frezzybuilds.devnotch.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Größe des seitlichen Drawers und ob er als Split-Ansicht (zwei Spalten) erscheint. */
data class EdgeDrawerSpec(val width: Dp, val height: Dp, val split: Boolean) {
    companion object {
        private const val MIN_WIDTH = 360
        private const val MAX_WIDTH = 640
        private const val MIN_HEIGHT = 280
        private const val MAX_HEIGHT = 520

        /** Ab dieser Breite haben beide Spalten genug Platz (je ≥ ~240 dp). */
        private const val SPLIT_MIN_WIDTH = 520

        /**
         * Hochformat: schmal und halbhoch; Querformat: breit und fast volle Höhe (wenig Höhe da).
         * Nie breiter/höher als der Bildschirm minus Rand.
         */
        fun forScreen(screenWidthDp: Int, screenHeightDp: Int, landscape: Boolean): EdgeDrawerSpec {
            val width = (screenWidthDp * if (landscape) 0.7f else 0.75f).toInt()
                .coerceIn(MIN_WIDTH, MAX_WIDTH)
                .coerceAtMost(screenWidthDp - 16)
            val height = (screenHeightDp * if (landscape) 0.85f else 0.5f).toInt()
                .coerceIn(MIN_HEIGHT, MAX_HEIGHT)
                .coerceAtMost(screenHeightDp - 32)
            return EdgeDrawerSpec(width.dp, height.dp, split = width >= SPLIT_MIN_WIDTH)
        }
    }
}
