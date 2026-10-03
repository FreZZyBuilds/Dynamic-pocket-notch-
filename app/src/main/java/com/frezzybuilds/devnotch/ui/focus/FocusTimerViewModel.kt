package com.frezzybuilds.devnotch.ui.focus

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Fokus-Timer mit Presets (25 min / 5 min).
 *
 * Die Restzeit wird aus einer Ziel-Uhrzeit (elapsedRealtime) berechnet statt Sekunden zu
 * zählen – so driftet der Timer nicht, auch wenn Ticks verspätet ankommen.
 */
class FocusTimerViewModel(
    private val clock: () -> Long = SystemClock::elapsedRealtime
) : ViewModel() {

    private val _remainingTime = MutableStateFlow(DEFAULT_MINUTES * 60L)

    /** Restzeit in Sekunden (Standard: 25 Minuten). */
    val remainingTime: StateFlow<Long> = _remainingTime.asStateFlow()

    private val _totalTime = MutableStateFlow(DEFAULT_MINUTES * 60L)

    /** Länge des aktuellen Presets in Sekunden – Basis für den Fortschrittsring. */
    val totalTime: StateFlow<Long> = _totalTime.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /** Meldet jedes Ablaufen des Timers (für den „Fokuszeit vorbei“-Peek). */
    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    /** Exakte Restzeit in ms, damit Pausieren keine angebrochene Sekunde verliert. */
    private var remainingMillis = DEFAULT_MINUTES * 60_000L
    private var endAt = 0L
    private var tickJob: Job? = null

    fun startTimer() {
        if (_isRunning.value) return
        // Nach Ablauf startet „Start“ das aktuelle Preset neu.
        if (remainingMillis == 0L) remainingMillis = _totalTime.value * 1000
        endAt = clock() + remainingMillis
        _isRunning.value = true
        tickJob = viewModelScope.launch {
            while (true) {
                remainingMillis = (endAt - clock()).coerceAtLeast(0)
                _remainingTime.value = remainingMillis.toDisplaySeconds()
                if (remainingMillis == 0L) {
                    _isRunning.value = false
                    _finished.tryEmit(Unit)
                    break
                }
                // Genau zum nächsten Sekundenwechsel der Restzeit aufwachen.
                delay((remainingMillis % 1000L).takeIf { it > 0 } ?: 1000L)
            }
        }
    }

    fun pauseTimer() {
        if (!_isRunning.value) return
        tickJob?.cancel()
        remainingMillis = (endAt - clock()).coerceAtLeast(0)
        _remainingTime.value = remainingMillis.toDisplaySeconds()
        _isRunning.value = false
    }

    fun toggleTimer() = if (_isRunning.value) pauseTimer() else startTimer()

    /** Stoppt den Timer und setzt ihn auf ein Preset (z. B. 25 oder 5 Minuten). */
    fun resetTimer(minutes: Int = DEFAULT_MINUTES) {
        require(minutes > 0) { "minutes must be positive" }
        tickJob?.cancel()
        _isRunning.value = false
        _totalTime.value = minutes * 60L
        remainingMillis = minutes * 60_000L
        _remainingTime.value = minutes * 60L
    }

    /** Beendet den Timer: zurück auf die volle Zeit des aktuellen Presets, Pille wieder frei. */
    fun stopTimer() = resetTimer((_totalTime.value / 60).toInt().coerceAtLeast(1))

    companion object {
        const val DEFAULT_MINUTES = 25
        const val BREAK_MINUTES = 5

        /** Aufrunden: Direkt nach dem Start steht 25:00 da, nicht 24:59. */
        private fun Long.toDisplaySeconds(): Long = (this + 999) / 1000
    }
}

/** Sekunden als MM:SS. */
fun formatMmSs(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)
