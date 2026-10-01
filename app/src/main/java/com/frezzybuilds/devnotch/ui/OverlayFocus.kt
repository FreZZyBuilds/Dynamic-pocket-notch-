package com.frezzybuilds.devnotch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Fokus-Verwaltung des Overlay-Fensters.
 *
 * Das Fenster ist standardmäßig `FLAG_NOT_FOCUSABLE`: Tastatur und Tasten-Events bleiben bei der
 * App dahinter (deren Bildschirmtastatur wird beim Aufklappen nicht geschlossen). Nur Bereiche,
 * die Fokus wirklich brauchen, melden ihn mit [RequestOverlayFocus] an – Textfelder (Notizen,
 * Shortcut-Suche/URL) für die Bildschirmtastatur, der Clip-Tab, weil Android ab 10 die
 * Zwischenablage nur mit Fensterfokus herausgibt.
 */
@Stable
class OverlayFocus {
    var requests by mutableIntStateOf(0)
        private set

    internal fun acquire() { requests++ }
    internal fun release() { requests-- }

    companion object {
        /** Fokussierbar nur aufgeklappt UND wenn ein sichtbarer Bereich Fokus angefordert hat. */
        fun isFocusable(expanded: Boolean, requests: Int): Boolean = expanded && requests > 0
    }
}

val LocalOverlayFocus = staticCompositionLocalOf<OverlayFocus?> { null }

/** Solange der Aufrufer in der Komposition ist, darf das Overlay-Fenster Fokus erhalten. */
@Composable
fun RequestOverlayFocus() {
    val focus = LocalOverlayFocus.current ?: return
    DisposableEffect(focus) {
        focus.acquire()
        onDispose { focus.release() }
    }
}
