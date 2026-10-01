package com.frezzybuilds.devnotch.feature.aiusage

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
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
 * - OpenRouter: `/api/v1/key` liefert `usage_monthly` (Credits = USD, UTC-Monat) direkt;
 *   Token-Zahlen gibt es dort nicht.
 */
class AiUsageApi(
    private val client: HttpClient,
    private val clock: Clock = Clock.systemUTC()
) {

    fun currentMonth(): String = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC)).let {
        "%04d-%02d".format(it.year, it.monthValue)
    }

    private fun monthStartEpochSeconds(): Long =
        ZonedDateTime.now(clock.withZone(ZoneOffset.UTC))
            .with(TemporalAdjusters.firstDayOfMonth())
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toEpochSecond()

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

    private fun check(response: HttpResponse, provider: AiProvider) {
        when {
            response.status == HttpStatusCode.Unauthorized ->
                throw AiUsageException("${provider.label}: Key ungültig")
            response.status == HttpStatusCode.Forbidden && provider == AiProvider.OPENAI ->
                throw AiUsageException("OpenAI: Admin-Key (sk-admin-…) nötig")
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
