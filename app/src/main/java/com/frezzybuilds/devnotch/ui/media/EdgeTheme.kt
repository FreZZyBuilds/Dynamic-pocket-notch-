package com.frezzybuilds.devnotch.ui.media

import androidx.compose.ui.graphics.Color

/**
 * Stil des Edge-Players. [colors] ist der Verlauf von oben nach unten;
 * `null` = aus dem Albumcover berechnet (fällt ohne Cover auf [NEON] zurück).
 */
enum class EdgeTheme(val label: String, val description: String, private val argb: List<Long>?) {
    ALBUM("Album-Farben", "Passt sich automatisch dem Cover an", null),
    NEON("Neon", "Magenta → Violett", listOf(0xFFE040FB, 0xFF8E24AA, 0xFF311B92)),
    OCEAN("Ocean", "Türkis → Tiefblau", listOf(0xFF18FFFF, 0xFF2979FF, 0xFF1A237E)),
    SUNSET("Sunset", "Orange → Pink → Beere", listOf(0xFFFFB74D, 0xFFFF5252, 0xFF880E4F)),
    MINT("Mint", "Frisches Grün", listOf(0xFFA7FFEB, 0xFF1DE9B6, 0xFF00695C)),
    GOLD("Gold", "Sonniges Gelb", listOf(0xFFFFF176, 0xFFFFD54F, 0xFFFFA000)),
    MIDNIGHT("Midnight Glass", "Dezent dunkel", listOf(0xFF3A3A3C, 0xFF1C1C1E, 0xFF000000));

    val colors: List<Color>? get() = argb?.map { Color(it) }

    companion object {
        val Fallback = NEON
    }
}
