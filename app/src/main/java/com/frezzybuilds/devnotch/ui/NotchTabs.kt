package com.frezzybuilds.devnotch.ui

import android.content.Context
import com.frezzybuilds.devnotch.Setup
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.feature.aiusage.AiUsagePanel
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/**
 * Tab „AI“: Nutzungs-Widget, darüber eine kompakte Statuszeile mit Uhrzeit, Datum und Akku
 * (ersetzt den früheren Overview-Tab).
 */
@Composable
fun AiStatsTabContent(onLeave: () -> Unit = {}) {
    val context = LocalContext.current
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        StatusLine()
        AiUsagePanel(onSetup = {
            Setup.open(context, Setup.Section.AI)
            onLeave()
        })
    }
}

/** „14:05 · Mittwoch, 1. Oktober · 🔋 82 %“ – minütlich aktualisiert. */
@Composable
private fun StatusLine() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            // Bis zum nächsten Minutenwechsel schlafen statt sekündlich neu zu zeichnen.
            delay(60_000 - now.time % 60_000)
        }
    }
    val battery = remember(now) { batteryPercent(context) }
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(now)
    val date = DateFormat.getDateInstance(DateFormat.LONG).format(now)
    Text(
        listOfNotNull(time, date, battery?.let { "🔋 $it %" }).joinToString(" · "),
        color = Color.Gray,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1
    )
}

/**
 * Akkustand in Prozent oder null. BATTERY_PROPERTY_CAPACITY liefert auf manchen Geräten
 * Integer.MIN_VALUE („nicht unterstützt“) – dann den Batterie-Broadcast (level/scale) lesen.
 */
fun batteryPercent(context: Context): Int? {
    val capacity = context.getSystemService(BatteryManager::class.java)
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    if (capacity != null && capacity in 0..100) return capacity
    val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (level >= 0 && scale > 0) level * 100 / scale else null
}
