package com.frezzybuilds.devnotch.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Markenfarben aus dem App-Icon: Neon-Verlauf Magenta → Violett → Cyan. Die Notch nutzt sie
 * sparsam als Akzent (Rand, Tab-Indikator, Timer, Buttons) – der Rest bleibt tiefschwarz.
 */
object Brand {
    val Magenta = Color(0xFFE040FB)
    val Violet = Color(0xFF7C4DFF)
    val Cyan = Color(0xFF18FFFF)
    val Lilac = Color(0xFFB388FF)

    /** Ladeanzeige/Erfolg in Peeks. */
    val Charge = Color(0xFF34E37A)

    val Colors = listOf(Magenta, Violet, Cyan)
    val Horizontal: Brush = Brush.horizontalGradient(Colors)

    /** Fläche von Karten und Leere-Zuständen auf Schwarz. */
    val Card = Color(0xFF111118)

    /** Material-Farbschema der Notch: Akzente aus der Marke statt Standard-Lila. */
    val NotchScheme = darkColorScheme(
        primary = Lilac,
        onPrimary = Color.Black,
        secondary = Cyan,
        onSecondary = Color.Black,
        tertiary = Magenta,
        background = Color.Black,
        surface = Color(0xFF121212),
        surfaceVariant = Color(0xFF1E1E24),
        onSurfaceVariant = Color(0xFFB4B4C0)
    )
}
