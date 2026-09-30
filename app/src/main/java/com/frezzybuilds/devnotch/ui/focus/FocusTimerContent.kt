package com.frezzybuilds.devnotch.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FocusColor = Color(0xFFFF6B5A)
private val BreakColor = Color(0xFF4FC3F7)

@Composable
fun FocusTimerContent(
    state: FocusTimerState,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onSkip: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.size(120.dp),
                color = if (state.phase == FocusPhase.FOCUS) FocusColor else BreakColor,
                trackColor = Color(0xFF2A2A2A)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.formatted, color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${state.phase.emoji} ${state.phase.label}",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = onToggle) {
                Text(if (state.isRunning) "Pause" else "Start")
            }
            Row {
                TextButton(onClick = onReset) { Text("Reset", color = Color.Gray) }
                TextButton(onClick = onSkip) { Text("Weiter", color = Color.Gray) }
            }
            Text(
                "Sessions: ${state.completedFocusSessions}",
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
