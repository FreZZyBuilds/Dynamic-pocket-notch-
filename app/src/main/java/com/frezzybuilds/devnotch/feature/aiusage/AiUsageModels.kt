package com.frezzybuilds.devnotch.feature.aiusage

import kotlinx.serialization.Serializable

enum class AiProvider(val label: String) {
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic"),
    OPENROUTER("OpenRouter"),
    GEMINI("Gemini"),
    OLLAMA("Ollama")
}

/** Verbrauch eines Anbieters im laufenden Abrechnungsmonat (UTC-Kalendermonat). */
@Serializable
data class ProviderUsage(
    val provider: AiProvider,
    /** null = Kosten per API nicht abrufbar (Gemini); zählt nicht zur Summe. */
    val costUsd: Double?,
    /** null = Anbieter liefert keine Token-Zahlen (OpenRouter, Gemini, Ollama). */
    val tokens: Long?,
    /** Monat als "2026-10" – ein Cache aus dem Vormonat zählt nicht mehr. */
    val month: String,
    val fetchedAt: Long,
    /** Zusatzinfo, z. B. „v0.12 · 1 Modell geladen“ (Ollama) oder Hinweise (Gemini). */
    val detail: String? = null
)

data class AiUsageState(
    val usages: List<ProviderUsage> = emptyList(),
    val errors: Map<AiProvider, String> = emptyMap(),
    val loading: Boolean = false,
    val configured: Boolean = false
) {
    val totalCostUsd: Double get() = usages.sumOf { it.costUsd ?: 0.0 }

    /** Summe nur der Anbieter, die Tokens melden; null, wenn keiner es tut. */
    val totalTokens: Long? get() = usages.mapNotNull { it.tokens }.takeIf { it.isNotEmpty() }?.sum()

    val lastUpdated: Long? get() = usages.maxOfOrNull { it.fetchedAt }
}

class AiUsageException(message: String) : Exception(message)

/** Exakt mit Cent für das Widget: "$13.60". */
fun formatUsdExact(value: Double): String = "$%.2f".format(java.util.Locale.US, value)

/** Kompakt für die Pille: "$1.42" – unter 10 $ mit Cent, darüber ganze Dollar. */
fun formatUsd(value: Double): String =
    if (value < 10) "$%.2f".format(java.util.Locale.US, value) else "$%.0f".format(java.util.Locale.US, value)
