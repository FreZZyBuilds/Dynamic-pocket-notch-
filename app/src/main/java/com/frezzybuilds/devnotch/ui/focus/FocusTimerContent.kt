package com.frezzybuilds.devnotch.ui.focus

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val StartColor = Color(0xFF03DAC6)
private val PauseColor = Color(0xFFCF6679)
private val TrackColor = Color(0xFF2A2A2A)

/** Tab-Einstieg: liest die StateFlows des ViewModels und rendert [FocusTimerContent]. */
@Composable
fun FocusTimerTab(viewModel: FocusTimerViewModel) {
    val remaining by viewModel.remainingTime.collectAsStateWithLifecycle()
    val total by viewModel.totalTime.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    FocusTimerContent(
        remainingSeconds = remaining,
        totalSeconds = total,
        isRunning = isRunning,
        onToggleTimer = viewModel::toggleTimer,
        onPreset = viewModel::resetTimer
    )
}

@Composable
fun FocusTimerContent(
    remainingSeconds: Long,
    totalSeconds: Long,
    isRunning: Boolean,
    onToggleTimer: () -> Unit,
    onPreset: (minutes: Int) -> Unit
) {
    // Ring leert sich mit der Zeit; die lineare 1-s-Animation lässt ihn gleiten statt springen.
    val progress by animateFloatAsState(
        targetValue = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds else 0f,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "timerProgress"
    )
    val accent by animateColorAsState(
        targetValue = if (isRunning) PauseColor else StartColor,
        label = "timerAccent"
    )

    // Schmale Drawer-Spalte: Ring über den Buttons statt daneben.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < 300.dp) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            ) {
                TimerRing(progress, accent, remainingSeconds)
                TimerControls(isRunning, accent, totalSeconds, onToggleTimer, onPreset)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
            ) {
                TimerRing(progress, accent, remainingSeconds)
                TimerControls(isRunning, accent, totalSeconds, onToggleTimer, onPreset)
            }
        }
    }
}

@Composable
private fun TimerRing(progress: Float, accent: Color, remainingSeconds: Long) {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(132.dp),
            color = accent,
            trackColor = TrackColor,
            strokeWidth = 8.dp,
            strokeCap = StrokeCap.Round
        )
        Text(
            text = formatMmSs(remainingSeconds),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
    }
}

@Composable
private fun TimerControls(
    isRunning: Boolean,
    accent: Color,
    totalSeconds: Long,
    onToggleTimer: () -> Unit,
    onPreset: (minutes: Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onToggleTimer,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.width(128.dp)
        ) {
            Text(if (isRunning) "Pause" else "Start", color = Color.Black)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PresetButton(FocusTimerViewModel.DEFAULT_MINUTES, totalSeconds, onPreset)
            PresetButton(FocusTimerViewModel.BREAK_MINUTES, totalSeconds, onPreset)
        }
    }
}

/** Preset-Button; das aktive Preset ist hervorgehoben. */
@Composable
private fun PresetButton(minutes: Int, totalSeconds: Long, onPreset: (Int) -> Unit) {
    val active = totalSeconds == minutes * 60L
    OutlinedButton(
        onClick = { onPreset(minutes) },
        border = BorderStroke(1.dp, if (active) Color.White else Color.Gray),
        modifier = Modifier.width(60.dp),
        contentPadding = ButtonDefaults.TextButtonContentPadding
    ) {
        Text("${minutes}m", color = if (active) Color.White else Color.Gray)
    }
}
