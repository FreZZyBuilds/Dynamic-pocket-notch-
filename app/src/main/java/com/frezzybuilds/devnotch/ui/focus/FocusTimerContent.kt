package com.frezzybuilds.devnotch.ui.focus

import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.requiredSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val TrackColor = Color(0xFF1E1E26)
private val PauseSurface = Color(0xFF24242E)

/** Höhe von Start/Pause, Abstand und Presets. */
private val CONTROLS_HEIGHT = 38.dp + 6.dp + 32.dp

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
        onPreset = viewModel::resetTimer,
        onStop = viewModel::stopTimer
    )
}

@Composable
fun FocusTimerContent(
    remainingSeconds: Long,
    totalSeconds: Long,
    isRunning: Boolean,
    onToggleTimer: () -> Unit,
    onPreset: (minutes: Int) -> Unit,
    onStop: () -> Unit = {}
) {
    // Läuft oder angebrochen pausiert: dann gibt es „Stopp“ (zurück auf die volle Zeit).
    val canStop = isRunning || remainingSeconds != totalSeconds
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
        // Der Ring bleibt rund: nie größer als der verfügbare Platz (sonst presst Compose die
        // Zeichenfläche zusammen und der Ring wird zum Oval – z. B. mit Anrufkarte darüber).
        val narrow = maxWidth < 300.dp
        val ringSize = (if (narrow) maxHeight - CONTROLS_HEIGHT - 12.dp else maxHeight - 4.dp)
            .coerceIn(64.dp, 132.dp)
        if (narrow) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            ) {
                TimerRing(progress, glow, remainingSeconds, ringSize)
                TimerControls(isRunning, totalSeconds, canStop, onToggleTimer, onPreset, onStop)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
            ) {
                TimerRing(progress, glow, remainingSeconds, ringSize)
                TimerControls(isRunning, totalSeconds, canStop, onToggleTimer, onPreset, onStop)
            }
        }
    }
}

@Composable
private fun TimerRing(progress: Float, glow: Float, remainingSeconds: Long, ringSize: androidx.compose.ui.unit.Dp = 132.dp) {
    Box(Modifier.requiredSize(ringSize), contentAlignment = Alignment.Center) {
        // Ring im Markenverlauf (Magenta → Violett → Cyan) mit weichem Leuchten darunter.
        Canvas(Modifier.fillMaxSize()) {
            val stroke = (if (ringSize < 100.dp) 6.dp else 8.dp).toPx()
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
            style = when {
                ringSize < 100.dp -> MaterialTheme.typography.titleMedium
                ringSize < 120.dp -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.headlineMedium
            },
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun TimerControls(
    isRunning: Boolean,
    totalSeconds: Long,
    canStop: Boolean,
    onToggleTimer: () -> Unit,
    onPreset: (minutes: Int) -> Unit,
    onStop: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Start im Markenverlauf; läuft der Timer, wird daraus ein ruhiger Pause-Knopf.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(if (canStop) 80.dp else 128.dp)
                .height(38.dp)
                .clip(CircleShape)
                .background(if (isRunning) SolidColor(PauseSurface) else Brand.Horizontal)
                .clickable(role = Role.Button, onClick = onToggleTimer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isRunning) "Pause" else if (canStop) "Weiter" else "Start",
                color = if (isRunning) Color.White else Color.Black,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (canStop) {
            // Beendet den Timer und setzt ihn auf die volle Zeit zurück.
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(PauseSurface)
                    .clickable(role = Role.Button, onClickLabel = "Timer beenden", onClick = onStop),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
            }
        }
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
        // Feste Höhe statt der 40-dp-Mindesthöhe – passt auch in ein niedriges Dashboard.
        modifier = Modifier.width(60.dp).height(32.dp),
        contentPadding = ButtonDefaults.TextButtonContentPadding
    ) {
        Text("${minutes}m", color = if (active) Color.White else Color.Gray)
    }
}
