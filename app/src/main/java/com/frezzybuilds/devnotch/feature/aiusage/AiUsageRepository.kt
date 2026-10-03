package com.frezzybuilds.devnotch.feature.aiusage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Holt die Monatskosten aller konfigurierten Anbieter und hält sie lokal gecacht:
 * Nach einem Neustart steht sofort der letzte Stand da, statt erst nach dem Netzwerk.
 * Ein Cache aus dem Vormonat wird verworfen (neuer Abrechnungszyklus).
 */
class AiUsageRepository(
    private val api: AiUsageApi,
    private val settings: AiUsageSettings,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _state = MutableStateFlow(AiUsageState(usages = loadCache(), configured = settings.configuredProviders.isNotEmpty()))
    val state: StateFlow<AiUsageState> = _state.asStateFlow()

    /** Nur abfragen, wenn der Stand älter als [maxAgeMillis] ist (Standard 15 min). */
    suspend fun refreshIfStale(maxAgeMillis: Long = STALE_AFTER_MILLIS) {
        val last = _state.value.lastUpdated ?: 0L
        if (clock() - last >= maxAgeMillis || _state.value.usages.isEmpty()) refresh()
    }

    suspend fun refresh() = mutex.withLock {
        val providers = settings.configuredProviders
        if (providers.isEmpty()) {
            _state.value = AiUsageState(configured = false)
            settings.cacheJson = null
            return@withLock
        }
        _state.update { it.copy(loading = true, configured = true) }

        val usages = mutableListOf<ProviderUsage>()
        val errors = mutableMapOf<AiProvider, String>()
        for (provider in providers) {
            val key = settings.key(provider)
            try {
                usages += when (provider) {
                    AiProvider.OPENAI -> api.fetchOpenAi(key ?: continue)
                    AiProvider.ANTHROPIC -> api.fetchAnthropic(key ?: continue)
                    AiProvider.OPENROUTER -> api.fetchOpenRouter(key ?: continue)
                    AiProvider.GEMINI -> api.fetchGemini(key ?: continue)
                    AiProvider.OLLAMA -> api.fetchOllama(settings.ollamaUrl ?: continue, key)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiUsageException) {
                errors[provider] = e.message.orEmpty()
            } catch (e: IOException) {
                errors[provider] = "${provider.label}: keine Verbindung"
            } catch (e: Exception) {
                errors[provider] = "${provider.label}: ${e.message ?: "Fehler"}"
            }
        }
        // Bei Fehler den letzten bekannten Wert des Anbieters behalten statt 0 $ anzuzeigen.
        val kept = _state.value.usages.filter { it.provider in errors && it.provider in providers }
        val merged = (usages + kept).sortedBy { it.provider.ordinal }
        _state.value = AiUsageState(usages = merged, errors = errors, loading = false, configured = true)
        settings.cacheJson = json.encodeToString(ListSerializer(ProviderUsage.serializer()), merged)
    }

    private fun loadCache(): List<ProviderUsage> {
        val cached = settings.cacheJson ?: return emptyList()
        val month = api.currentMonth()
        return runCatching { json.decodeFromString(ListSerializer(ProviderUsage.serializer()), cached) }
            .getOrDefault(emptyList())
            .filter { it.month == month && it.provider in settings.configuredProviders }
    }

    private companion object {
        const val STALE_AFTER_MILLIS = 15 * 60_000L
    }
}
