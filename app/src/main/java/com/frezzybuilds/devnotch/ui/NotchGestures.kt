package com.frezzybuilds.devnotch.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import com.frezzybuilds.devnotch.service.EdgeSide
import com.frezzybuilds.devnotch.service.NotchLayoutMode

/** Was eine Wischgeste mit der Notch macht. */
enum class NotchGestureAction { EXPAND, COLLAPSE }

object NotchGestures {

    /**
     * Entscheidet anhand der bisher zurückgelegten Strecke (Summe aller Deltas, nicht des letzten
     * Events), ob die Geste auslöst.
     *
     * - Notch oben: nach unten ziehen öffnet, nach oben ziehen schließt (vertikale Achse).
     * - Edge-Dock: vom Rand zur Bildschirmmitte ziehen öffnet, zurück zum Rand schließt
     *   (horizontale Achse; „zur Mitte“ hängt davon ab, ob links oder rechts angedockt ist).
     */
    fun resolve(
        mode: NotchLayoutMode,
        side: EdgeSide,
        expanded: Boolean,
        distance: Float,
        threshold: Float
    ): NotchGestureAction? {
        // Positive Richtung = „öffnen“: unten bzw. zur Mitte.
        val towardOpen = when (mode) {
            NotchLayoutMode.NOTCH_TOP -> distance
            NotchLayoutMode.EDGE_SIDE -> if (side == EdgeSide.RIGHT) -distance else distance
        }
        return when {
            !expanded && towardOpen > threshold -> NotchGestureAction.EXPAND
            expanded && towardOpen < -threshold -> NotchGestureAction.COLLAPSE
            else -> null
        }
    }
}

/**
 * Wischgesten für die Notch-Box. Löst pro Geste höchstens einmal aus, sobald die Strecke die
 * Schwelle überschreitet – mit Haptik im Moment des Einrastens. Scrollbare Inhalte im Dashboard
 * (Listen) bekommen ihre Drags zuerst; gewischt wird über Kopfzeile, Tabs und freie Flächen.
 */
fun Modifier.notchDragGestures(
    mode: NotchLayoutMode,
    side: EdgeSide,
    expanded: Boolean,
    thresholdPx: Float,
    haptic: HapticFeedback,
    onAction: (NotchGestureAction) -> Unit
): Modifier = pointerInput(mode, side, expanded, thresholdPx) {
    var distance = 0f
    var fired = false

    fun onDelta(delta: Float) {
        if (fired) return
        distance += delta
        NotchGestures.resolve(mode, side, expanded, distance, thresholdPx)?.let { action ->
            fired = true
            haptic.performHapticFeedback(
                // Öffnen deutlich spürbar, Schließen als leichter Tick.
                if (action == NotchGestureAction.EXPAND) HapticFeedbackType.LongPress
                else HapticFeedbackType.TextHandleMove
            )
            onAction(action)
        }
    }

    fun reset() {
        distance = 0f
        fired = false
    }

    when (mode) {
        NotchLayoutMode.NOTCH_TOP -> detectVerticalDragGestures(
            onDragStart = { reset() },
            onDragEnd = ::reset,
            onDragCancel = ::reset
        ) { change, dragAmount ->
            change.consume()
            onDelta(dragAmount)
        }
        NotchLayoutMode.EDGE_SIDE -> detectHorizontalDragGestures(
            onDragStart = { reset() },
            onDragEnd = ::reset,
            onDragCancel = ::reset
        ) { change, dragAmount ->
            change.consume()
            onDelta(dragAmount)
        }
    }
}
