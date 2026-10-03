package com.frezzybuilds.devnotch.feature.aiusage

import androidx.compose.foundation.clickable
import com.frezzybuilds.devnotch.ui.EmptyState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frezzybuilds.devnotch.appContainer
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val Ok = Color(0xFF03DAC6)
private val Warn = Color(0xFFFFD54F)
private val Over = Color(0xFFFF5252)

/** Farbe nach Auslastung des Monatslimits: grün, ab 85 % gelb, ab 100 % rot. */
fun usageColor(costUsd: Double, limitUsd: Double): Color {
    val ratio = if (limitUsd > 0) costUsd / limitUsd else 0.0
    return when {
        ratio >= 1.0 -> Over
        ratio >= 0.85 -> Warn
        else -> Ok
    }
}

/** Verbindet das Widget mit Repository und Einstellungen; aktualisiert beim Öffnen, wenn veraltet. */
@Composable
fun AiUsagePanel(modifier: Modifier = Modifier, onSetup: (() -> Unit)? = null) {
    val container = LocalContext.current.appContainer
    val state by container.aiUsageRepository.state.collectAsStateWithLifecycle()
    val display by remember { container.aiUsageSettings.displayFlow() }
        .collectAsStateWithLifecycle(initialValue = container.aiUsageSettings.display)
    LaunchedEffect(Unit) { container.aiUsageRepository.refreshIfStale() }
    val scope = rememberCoroutineScope()

    AiUsageWidget(
        state = state,
        monthlyLimitUsd = display.limitUsd,
        onRefresh = { scope.launch { container.aiUsageRepository.refresh() } },
        onSetup = onSetup,
        modifier = modifier
    )
}

/**
 * Übersicht: Dollar im laufenden Abrechnungsmonat gegen das Monatslimit, Fortschrittsbalken,
 * Token-Zähler und Aufschlüsselung je Anbieter.
 */
@Composable
fun AiUsageWidget(
    state: AiUsageState,
    monthlyLimitUsd: Double,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    /** Nicht eingerichtet: einladender Leere-Zustand mit Sprung in die Einstellungen. */
    onSetup: (() -> Unit)? = null
) {
    if (!state.configured && onSetup != null) {
        EmptyState(
            icon = "✦",
            title = "KI-Kosten im Blick",
            text = "Verbinde OpenAI, Anthropic, OpenRouter, Gemini oder Ollama – deine Monatskosten erscheinen hier.",
            action = "Anbieter verbinden",
            onAction = onSetup,
            modifier = modifier
        )
        return
    }
    val cost = state.totalCostUsd
    val progress = if (monthlyLimitUsd > 0) (cost / monthlyLimitUsd).coerceIn(0.0, 1.0).toFloat() else 0f
    val accent = usageColor(cost, monthlyLimitUsd)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("AI Usage", style = MaterialTheme.typography.titleSmall, color = Color.White, modifier = Modifier.weight(1f))
                if (state.configured) {
                    Text(
                        "${formatUsdExact(cost)} / ${formatUsdExact(monthlyLimitUsd)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent
                    )
                    Text(
                        if (state.loading) " …" else " ↻",
                        color = Color.Gray,
                        modifier = Modifier.clickable(enabled = !state.loading, onClick = onRefresh).padding(start = 6.dp)
                    )
                }
            }
            if (!state.configured) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Kein Key hinterlegt – in der DevNotch-App unter „AI-Nutzung“ einrichten.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                return@Column
            }

            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = accent,
                trackColor = Color(0xFF2C2C2C),
                drawStopIndicator = {}
            )
            Spacer(Modifier.height(6.dp))

            Text(
                state.totalTokens?.let { "${formatTokens(it)} Tokens diesen Monat" } ?: "Tokens: –",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            Spacer(Modifier.height(4.dp))
            // Je Anbieter: Kosten (oder „—“, wenn per API nicht abrufbar) und Zusatzinfo.
            state.usages.forEach { usage ->
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        usage.provider.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        usage.costUsd?.let(::formatUsdExact) ?: "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                usage.detail?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8A8A8A), maxLines = 1)
                }
            }
            state.errors.values.forEach { error ->
                Text(error, style = MaterialTheme.typography.labelSmall, color = Over)
            }
        }
    }
}

/** 425100 → „425.100“ (deutsches Tausendertrennzeichen). */
fun formatTokens(tokens: Long): String = NumberFormat.getIntegerInstance(Locale.GERMANY).format(tokens)
