package com.frezzybuilds.devnotch.ui.focus

import androidx.compose.animation.core.LinearEasing
import com.frezzybuilds.devnotch.ui.theme.Brand
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.ButtonDefaults
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

private val TrackColor = Color(0xFF1E1E26)
private val PauseSurface = Color(0xFF24242E)

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
    // Läuft der Timer, leuchtet der Ring heller (Glow-Stärke 0…1).
    val glow by animateFloatAsState(if (isRunning) 1f else 0.35f, tween(400), label = "timerGlow")

    // Schmale Drawer-Spalte: Ring über den Buttons statt daneben.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < 300.dp) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            ) {
                TimerRing(progress, glow, remainingSeconds)
                TimerControls(isRunning, totalSeconds, onToggleTimer, onPreset)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
            ) {
                TimerRing(progress, glow, remainingSeconds)
                TimerControls(isRunning, totalSeconds, onToggleTimer, onPreset)
            }
        }
    }
}

@Composable
private fun TimerRing(progress: Float, glow: Float, remainingSeconds: Long) {
    Box(contentAlignment = Alignment.Center) {
        // Ring im Markenverlauf (Magenta → Violett → Cyan) mit weichem Leuchten darunter.
        Canvas(Modifier.size(132.dp)) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2 + 4.dp.toPx()
            val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
            val topLeft = Offset(inset, inset)
            drawArc(TrackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (progress > 0f) {
                // Verlauf startet oben (−90°) und läuft im Uhrzeigersinn mit dem Fortschritt.
                rotate(-90f) {
                    val brush = Brush.sweepGradient(Brand.Colors + Brand.Colors.first())
                    drawArc(brush, 0f, 360f * progress, false, topLeft, arcSize, alpha = 0.25f * glow, style = Stroke(stroke * 2.6f, cap = StrokeCap.Round))
                    drawArc(brush, 0f, 360f * progress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
        }
        Text(
            text = formatMmSs(remainingSeconds),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun TimerControls(
    isRunning: Boolean,
    totalSeconds: Long,
    onToggleTimer: () -> Unit,
    onPreset: (minutes: Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Start im Markenverlauf; läuft der Timer, wird daraus ein ruhiger Pause-Knopf.
        Box(
            Modifier
                .width(128.dp)
                .height(40.dp)
                .clip(CircleShape)
                .background(if (isRunning) SolidColor(PauseSurface) else Brand.Horizontal)
                .clickable(role = Role.Button, onClick = onToggleTimer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isRunning) "Pause" else "Start",
                color = if (isRunning) Color.White else Color.Black,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
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
