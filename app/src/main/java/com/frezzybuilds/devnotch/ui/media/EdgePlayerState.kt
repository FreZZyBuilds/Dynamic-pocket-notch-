package com.frezzybuilds.devnotch.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** Wie lange die volle Edge-Leiste ohne Interaktion stehen bleibt, bevor sie einklappt. */
const val EDGE_AUTO_MINIMIZE_DELAY_MS = 5_000L

/** Ob der Edge-Player gerade zur runden Cover-Bubble eingeklappt ist. */
@Stable
class EdgePlayerState internal constructor() {
    var minimized by mutableStateOf(false)
        private set

    /** Jede Interaktion startet den Einklapp-Countdown neu. */
    internal var interactions by mutableIntStateOf(0)
        private set

    /** Volle Leiste zeigen (Tipp auf die Bubble, neuer Titel). */
    fun restore() {
        minimized = false
        interactions++
    }

    /** Tipp auf einen Button der Leiste: Countdown neu starten. */
    fun onInteraction() {
        interactions++
    }

    internal fun minimize() {
        minimized = true
    }
}

/**
 * @param trackKey wechselt bei neuem Titel → die volle Leiste erscheint kurz wieder
 * @param autoMinimize Einstellung „Edge-Player automatisch einklappen“
 * @param active nur zählen, wenn die Leiste tatsächlich sichtbar ist (Edge-Modus, Musik, nicht aufgeklappt)
 */
@Composable
fun rememberEdgePlayerState(
    trackKey: Any?,
    autoMinimize: Boolean,
    active: Boolean,
    delayMillis: Long = EDGE_AUTO_MINIMIZE_DELAY_MS
): EdgePlayerState {
    val state = remember { EdgePlayerState() }
    LaunchedEffect(trackKey) { state.restore() }
    LaunchedEffect(autoMinimize) { if (!autoMinimize) state.restore() }
    LaunchedEffect(autoMinimize, active, state.minimized, state.interactions) {
        if (autoMinimize && active && !state.minimized) {
            delay(delayMillis)
            state.minimize()
        }
    }
    return state
}
