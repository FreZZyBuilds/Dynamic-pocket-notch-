package com.frezzybuilds.devnotch.ui.focus

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FocusPhase(val durationMillis: Long, val label: String, val emoji: String) {
    FOCUS(25 * 60_000L, "Fokus", "🍅"),
    BREAK(5 * 60_000L, "Pause", "☕")
}

data class FocusTimerState(
    val phase: FocusPhase = FocusPhase.FOCUS,
    val remainingMillis: Long = FocusPhase.FOCUS.durationMillis,
    val isRunning: Boolean = false,
    val completedFocusSessions: Int = 0
) {
    /** Unberührt: volle Fokuszeit, nicht gestartet. Dann bleibt die eingeklappte Pille leer. */
    val isIdle: Boolean
        get() = !isRunning && phase == FocusPhase.FOCUS && remainingMillis == phase.durationMillis

    val progress: Float
        get() = 1f - remainingMillis.toFloat() / phase.durationMillis

    /** mm:ss, auf volle Sekunden aufgerundet (zeigt 25:00 direkt nach dem Start). */
    val formatted: String
        get() {
            val seconds = (remainingMillis + 999) / 1000
            return "%02d:%02d".format(seconds / 60, seconds % 60)
        }
}

/**
 * Pomodoro-Timer mit 25 min Fokus / 5 min Pause im Wechsel.
 *
 * Die Restzeit wird aus einer Ziel-Uhrzeit (elapsedRealtime) berechnet statt Sekunden zu
 * zählen – so driftet der Timer nicht, auch wenn Ticks verspätet ankommen.
 */
class FocusTimerViewModel(
    private val clock: () -> Long = SystemClock::elapsedRealtime
) : ViewModel() {

    private val _state = MutableStateFlow(FocusTimerState())
    val state: StateFlow<FocusTimerState> = _state.asStateFlow()

    private var tickJob: Job? = null
    private var endAt = 0L

    fun start() {
        if (_state.value.isRunning) return
        endAt = clock() + _state.value.remainingMillis
        _state.update { it.copy(isRunning = true) }
        tickJob = viewModelScope.launch {
            while (true) {
                val remaining = (endAt - clock()).coerceAtLeast(0)
                _state.update { it.copy(remainingMillis = remaining) }
                if (remaining == 0L) {
                    advancePhase()
                    endAt = clock() + _state.value.remainingMillis
                    continue
                }
                // Genau zum nächsten Sekundenwechsel der Restzeit aufwachen.
                delay((remaining % 1000L).takeIf { it > 0 } ?: 1000L)
            }
        }
    }

    fun pause() {
        if (!_state.value.isRunning) return
        tickJob?.cancel()
        _state.update {
            it.copy(isRunning = false, remainingMillis = (endAt - clock()).coerceAtLeast(0))
        }
    }

    fun toggle() = if (_state.value.isRunning) pause() else start()

    fun reset() {
        tickJob?.cancel()
        _state.update { FocusTimerState(completedFocusSessions = it.completedFocusSessions) }
    }

    /** Springt sofort ins nächste Intervall; läuft der Timer, läuft er dort weiter. */
    fun skip() {
        val wasRunning = _state.value.isRunning
        tickJob?.cancel()
        _state.update { it.copy(isRunning = false) }
        advancePhase()
        if (wasRunning) start()
    }

    private fun advancePhase() {
        _state.update { current ->
            val next = if (current.phase == FocusPhase.FOCUS) FocusPhase.BREAK else FocusPhase.FOCUS
            current.copy(
                phase = next,
                remainingMillis = next.durationMillis,
                completedFocusSessions = current.completedFocusSessions +
                    if (current.phase == FocusPhase.FOCUS) 1 else 0
            )
        }
    }
}
