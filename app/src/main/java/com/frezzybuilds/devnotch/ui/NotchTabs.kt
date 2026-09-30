package com.frezzybuilds.devnotch.ui

import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.ui.focus.FocusTimerState
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun OverviewTabContent(timer: FocusTimerState) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1_000)
        }
    }
    val battery = remember(now.time / 60_000) {
        context.getSystemService(BatteryManager::class.java)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            DateFormat.getTimeInstance(DateFormat.SHORT).format(now),
            color = Color.White,
            style = MaterialTheme.typography.displaySmall
        )
        Text(
            DateFormat.getDateInstance(DateFormat.FULL).format(now),
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )
        Text("🔋 $battery %", color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (timer.isIdle) "🍅 Kein Timer aktiv"
            else "${timer.phase.emoji} ${timer.phase.label} – ${timer.formatted}" +
                if (timer.isRunning) "" else " (pausiert)",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
