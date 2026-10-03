package com.frezzybuilds.devnotch.service

import android.content.Context
import android.content.res.Configuration

/** Tablet = kürzeste Bildschirmseite ≥ 600 dp (auch aufgeklappte Foldables). */
fun Context.isTablet(): Boolean = resources.configuration.smallestScreenWidthDp >= 600

fun Context.isLandscape(): Boolean =
    resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

object AdaptiveLayout {
    /**
     * Auf Tablets gibt es selten ein Punch-Hole und oben mittig verdeckt eine Pille Inhalte –
     * dort gilt immer das Edge-Layout (seitliche Griffleiste + Drawer). Auf Smartphones zählt die
     * Einstellung.
     */
    fun effectiveMode(selected: NotchLayoutMode, isTablet: Boolean): NotchLayoutMode =
        if (isTablet) NotchLayoutMode.EDGE_SIDE else selected
}
