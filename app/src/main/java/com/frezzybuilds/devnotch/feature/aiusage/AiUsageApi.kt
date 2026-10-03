package com.frezzybuilds.devnotch.feature.aiusage

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * REST-Abfragen der Nutzungsdaten.
 *
 * - OpenAI: Costs API (`/v1/organization/costs`) für Dollar und Usage API
 *   (`/v1/organization/usage/completions`) für Tokens. Beide verlangen einen **Admin-Key**
 *   (`sk-admin-…`); normale API-Keys werden abgelehnt. Das alte `/v1/usage` funktioniert nur
 *   mit Dashboard-Sitzungen und ist daher nicht nutzbar.
 * - Anthropic: Usage & Cost Admin API (`/v1/organizations/cost_report` und
 *   `/v1/organizations/usage_report/messages`) mit Admin-Key (`sk-ant-admin01-…`). Beträge kommen
 *   als Dezimal-String in **Cent**.
 * - OpenRouter: `/api/v1/key` liefert `usage_monthly` (Credits = USD, UTC-Monat) direkt;
 *   Token-Zahlen gibt es dort nicht.
 * - Gemini: Es gibt keine API, die mit einem API-Key Kosten liefert (nur AI-Studio-Dashboards).
 *   Geprüft wird nur, ob der Key gültig ist.
 * - Ollama: lokal und kostenlos; ohne Nutzungsstatistik. Angezeigt werden Version, geladene und
 *   installierte Modelle.
 */
class AiUsageApi(
    private val client: HttpClient,
    private val clock: Clock = Clock.systemUTC()
) {

    fun currentMonth(): String = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC)).let {
        "%04d-%02d".format(it.year, it.monthValue)
    }

    private fun monthStart(): ZonedDateTime =
        ZonedDateTime.now(clock.withZone(ZoneOffset.UTC))
            .with(TemporalAdjusters.firstDayOfMonth())
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)

    private fun monthStartEpochSeconds(): Long = monthStart().toEpochSecond()

    suspend fun fetchOpenAi(adminKey: String): ProviderUsage {
        val start = monthStartEpochSeconds()
        val cost = pages(OPENAI_COSTS, adminKey, start).sumOf { bucket ->
            bucket.results.sumOf { it.amount?.value ?: 0.0 }
        }
        val tokens = pages(OPENAI_COMPLETIONS, adminKey, start).sumOf { bucket ->
            bucket.results.sumOf { it.inputTokens + it.outputTokens }
        }
        return ProviderUsage(AiProvider.OPENAI, cost, tokens, currentMonth(), clock.millis())
    }

    /** Tages-Buckets seit Monatsanfang, über alle Seiten (next_page → page). */
    private suspend fun pages(url: String, key: String, start: Long): List<BucketDto> {
        val buckets = mutableListOf<BucketDto>()
        var page: String? = null
        repeat(MAX_PAGES) {
            val response = client.get(url) {
                bearerAuth(key)
                parameter("start_time", start)
                parameter("bucket_width", "1d")
                parameter("limit", 31)
                page?.let { parameter("page", it) }
            }
            check(response, AiProvider.OPENAI)
            val body = response.body<OpenAiPageDto>()
            buckets += body.data
            page = body.nextPage?.takeIf { body.hasMore } ?: return buckets
        }
        return buckets
    }

    suspend fun fetchOpenRouter(apiKey: String): ProviderUsage {
        val response = client.get(OPENROUTER_KEY) { bearerAuth(apiKey) }
        check(response, AiProvider.OPENROUTER)
        val data = response.body<OpenRouterKeyDto>().data
        return ProviderUsage(
            provider = AiProvider.OPENROUTER,
            costUsd = data.usageMonthly ?: data.usage,
            tokens = null,
            month = currentMonth(),
            fetchedAt = clock.millis()
        )
    }

    suspend fun fetchAnthropic(adminKey: String): ProviderUsage {
        // RFC 3339, z. B. 2026-10-01T00:00:00Z
        val start = monthStart().toInstant().toString()
        val costCents = anthropicPages(ANTHROPIC_COSTS, adminKey, start).sumOf { bucket ->
            bucket.results.sumOf { it.amount?.toDoubleOrNull() ?: 0.0 }
        }
        val tokens = anthropicPages(ANTHROPIC_MESSAGES, adminKey, start).sumOf { bucket ->
            bucket.results.sumOf {
                it.uncachedInputTokens + it.cacheReadInputTokens + it.outputTokens +
                    (it.cacheCreation?.let { c -> c.ephemeral1h + c.ephemeral5m } ?: 0)
            }
        }
        return ProviderUsage(AiProvider.ANTHROPIC, costCents / 100.0, tokens, currentMonth(), clock.millis())
    }

    private suspend fun anthropicPages(url: String, key: String, start: String): List<AnthropicBucketDto> {
        val buckets = mutableListOf<AnthropicBucketDto>()
        var page: String? = null
        repeat(MAX_PAGES) {
            val response = client.get(url) {
                header("x-api-key", key)
                header("anthropic-version", "2023-06-01")
                parameter("starting_at", start)
                parameter("bucket_width", "1d")
                parameter("limit", 31)
                page?.let { parameter("page", it) }
            }
            check(response, AiProvider.ANTHROPIC)
            val body = response.body<AnthropicPageDto>()
            buckets += body.data
            page = body.nextPage?.takeIf { body.hasMore } ?: return buckets
        }
        return buckets
    }

    /** Nur Key-Prüfung: Kosten sind bei Gemini nicht per API abrufbar. */
    suspend fun fetchGemini(apiKey: String): ProviderUsage {
        val response = client.get(GEMINI_MODELS) {
            header("x-goog-api-key", apiKey)
            parameter("pageSize", 1)
        }
        // Gemini antwortet auf ungültige Keys mit 400 (API_KEY_INVALID) statt 401.
        if (response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.Forbidden) {
            throw AiUsageException("Gemini: Key ungültig")
        }
        check(response, AiProvider.GEMINI)
        return ProviderUsage(
            provider = AiProvider.GEMINI,
            costUsd = null,
            tokens = null,
            month = currentMonth(),
            fetchedAt = clock.millis(),
            detail = "Key gültig · Kosten nur im AI-Studio-Dashboard"
        )
    }

    /** Ollama-Server (lokal, im LAN oder ollama.com): Version, geladene und installierte Modelle. */
    suspend fun fetchOllama(baseUrl: String, apiKey: String?): ProviderUsage {
        val base = baseUrl.trimEnd('/')
        suspend fun get(path: String) = client.get("$base$path") {
            apiKey?.let { bearerAuth(it) }
        }.also { check(it, AiProvider.OLLAMA) }

        val version = get("/api/version").body<OllamaVersionDto>().version
        val running = get("/api/ps").body<OllamaModelsDto>().models
        val installed = get("/api/tags").body<OllamaModelsDto>().models
        val loaded = when (running.size) {
            0 -> "kein Modell geladen"
            1 -> "${running.first().name} geladen"
            else -> "${running.size} Modelle geladen"
        }
        return ProviderUsage(
            provider = AiProvider.OLLAMA,
            // Lokal kostenlos; die Cloud (ollama.com) rechnet per Abo ab, ohne Kosten-API.
            costUsd = if (Url(base).host.endsWith("ollama.com")) null else 0.0,
            tokens = null,
            month = currentMonth(),
            fetchedAt = clock.millis(),
            detail = "v$version · $loaded · ${installed.size} installiert"
        )
    }

    private fun check(response: HttpResponse, provider: AiProvider) {
        when {
            response.status == HttpStatusCode.Unauthorized ->
                throw AiUsageException("${provider.label}: Key ungültig")
            response.status == HttpStatusCode.Forbidden && provider == AiProvider.OPENAI ->
                throw AiUsageException("OpenAI: Admin-Key (sk-admin-…) nötig")
            (response.status == HttpStatusCode.Forbidden || response.status == HttpStatusCode.NotFound) &&
                provider == AiProvider.ANTHROPIC ->
                throw AiUsageException("Anthropic: Admin-Key (sk-ant-admin01-…) nötig")
            response.status == HttpStatusCode.TooManyRequests ->
                throw AiUsageException("${provider.label}: Rate-Limit, später erneut")
            !response.status.isSuccess() ->
                throw AiUsageException("${provider.label}: HTTP ${response.status.value}")
        }
    }

    private companion object {
        const val OPENAI_COSTS = "https://api.openai.com/v1/organization/costs"
        const val OPENAI_COMPLETIONS = "https://api.openai.com/v1/organization/usage/completions"
        const val OPENROUTER_KEY = "https://openrouter.ai/api/v1/key"
        const val ANTHROPIC_COSTS = "https://api.anthropic.com/v1/organizations/cost_report"
        const val ANTHROPIC_MESSAGES = "https://api.anthropic.com/v1/organizations/usage_report/messages"
        const val GEMINI_MODELS = "https://generativelanguage.googleapis.com/v1beta/models"
        const val MAX_PAGES = 6
    }
}

// --- DTOs ---

@Serializable
private data class OpenAiPageDto(
    val data: List<BucketDto> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("next_page") val nextPage: String? = null
)

@Serializable
private data class BucketDto(val results: List<ResultDto> = emptyList())

@Serializable
private data class ResultDto(
    val amount: AmountDto? = null,
    @SerialName("input_tokens") val inputTokens: Long = 0,
    @SerialName("output_tokens") val outputTokens: Long = 0
)

@Serializable
private data class AmountDto(val value: Double = 0.0, val currency: String = "usd")

@Serializable
private data class OpenRouterKeyDto(val data: OpenRouterKeyData)

@Serializable
private data class OpenRouterKeyData(
    val usage: Double = 0.0,
    @SerialName("usage_monthly") val usageMonthly: Double? = null,
    val limit: Double? = null
)

@Serializable
private data class AnthropicPageDto(
    val data: List<AnthropicBucketDto> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("next_page") val nextPage: String? = null
)

@Serializable
private data class AnthropicBucketDto(val results: List<AnthropicResultDto> = emptyList())

@Serializable
private data class AnthropicResultDto(
    /** Cent als Dezimal-String: "123.45" = $1.23 */
    val amount: String? = null,
    @SerialName("uncached_input_tokens") val uncachedInputTokens: Long = 0,
    @SerialName("cache_read_input_tokens") val cacheReadInputTokens: Long = 0,
    @SerialName("output_tokens") val outputTokens: Long = 0,
    @SerialName("cache_creation") val cacheCreation: AnthropicCacheCreationDto? = null
)

@Serializable
private data class AnthropicCacheCreationDto(
    @SerialName("ephemeral_1h_input_tokens") val ephemeral1h: Long = 0,
    @SerialName("ephemeral_5m_input_tokens") val ephemeral5m: Long = 0
)

@Serializable
private data class OllamaVersionDto(val version: String = "?")

@Serializable
private data class OllamaModelsDto(val models: List<OllamaModelDto> = emptyList())

@Serializable
private data class OllamaModelDto(val name: String = "?")
