package com.frezzybuilds.devnotch.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun OverviewTabContent(pomodoro: PomodoroState) {
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
            "🍅 " + if (pomodoro.isRunning) "Fokus läuft – ${pomodoro.formatted}" else "Kein Timer aktiv",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun PomodoroTabContent(pomodoro: PomodoroState) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { pomodoro.progress },
                modifier = Modifier.size(110.dp),
                color = Color(0xFFFF6B5A),
                trackColor = Color(0xFF2A2A2A)
            )
            Text(pomodoro.formatted, color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = pomodoro::toggle) {
                Text(if (pomodoro.isRunning) "Pause" else "Start")
            }
            TextButton(onClick = pomodoro::reset) {
                Text("Zurücksetzen", color = Color.Gray)
            }
        }
    }
}

/**
 * Ab Android 10 darf nur die fokussierte App die Zwischenablage lesen. Das Overlay ist im
 * aufgeklappten Zustand fokussierbar, daher klappt das Lesen meist erst nach einem Tap.
 */
@Composable
fun ClipboardTabContent() {
    val context = LocalContext.current
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }
    var content by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        content = runCatching {
            clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        }.getOrNull()
    }
    LaunchedEffect(Unit) { refresh() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            content?.takeIf { it.isNotBlank() } ?: "Zwischenablage leer oder nicht lesbar",
            color = if (content.isNullOrBlank()) Color.Gray else Color.White,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 5,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        )
        TextButton(onClick = ::refresh) { Text("Aktualisieren") }
    }
}

@Composable
fun DevTabContent() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        InfoLine("Gerät", "${Build.MANUFACTURER} ${Build.MODEL}")
        InfoLine("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        InfoLine("ADB", if (isAdbEnabled(context)) "aktiv" else "aus")
        TextButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }) {
            Text("Entwickleroptionen öffnen")
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = Color.Gray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}

private fun isAdbEnabled(context: Context): Boolean =
    Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
