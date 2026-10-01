package com.frezzybuilds.devnotch.feature.aiusage

import com.frezzybuilds.devnotch.data.createJsonHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AiUsageApiTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val clock = Clock.fixed(Instant.parse("2026-10-14T09:30:00Z"), ZoneOffset.UTC)
    private val requests = mutableListOf<String>()

    private fun api(handler: (path: String, page: String?) -> Pair<HttpStatusCode, String>) = AiUsageApi(
        createJsonHttpClient(MockEngine { request ->
            requests += request.url.toString()
            val (status, body) = handler(request.url.encodedPath, request.url.parameters["page"])
            respond(body, status, json)
        }),
        clock
    )

    @Test
    fun `openai sums costs and tokens across all pages since the start of the month`() = runTest {
        val usage = api { path, page ->
            HttpStatusCode.OK to when {
                path.endsWith("/costs") && page == null ->
                    """{"data":[{"results":[{"amount":{"value":0.42,"currency":"usd"}}]}],"has_more":true,"next_page":"p2"}"""
                path.endsWith("/costs") ->
                    """{"data":[{"results":[{"amount":{"value":1.0}},{"amount":{"value":0.003}}]}],"has_more":false,"next_page":null}"""
                else ->
                    """{"data":[{"results":[{"input_tokens":400000,"output_tokens":25100}]},{"results":[]}],"has_more":false}"""
            }
        }.fetchOpenAi("sk-admin-test")

        assertEquals(1.423, usage.costUsd, 1e-9)
        assertEquals(425_100L, usage.tokens)
        assertEquals("2026-10", usage.month)
        // 1. Oktober 2026 00:00 UTC
        val first = requests.first()
        assertEquals(true, first.contains("start_time=1790812800"))
        assertEquals(true, first.contains("bucket_width=1d"))
        assertEquals(3, requests.size) // 2 Kosten-Seiten + 1 Token-Seite
    }

    @Test
    fun `openai with a normal api key explains that an admin key is needed`() = runTest {
        val error = assertThrows(AiUsageException::class.java) {
            runBlocking { api { _, _ -> HttpStatusCode.Forbidden to "{}" }.fetchOpenAi("sk-proj-x") }
        }
        assertEquals("OpenAI: Admin-Key (sk-admin-…) nötig", error.message)
    }

    @Test
    fun `openrouter uses usage_monthly and reports no tokens`() = runTest {
        val usage = api { _, _ ->
            HttpStatusCode.OK to """{"data":{"label":"x","usage":12.5,"usage_monthly":3.48,"limit":null}}"""
        }.fetchOpenRouter("sk-or-v1")

        assertEquals(3.48, usage.costUsd, 1e-9)
        assertNull(usage.tokens)
        assertEquals("https://openrouter.ai/api/v1/key", requests.single())
    }

    @Test
    fun `openrouter falls back to total usage when usage_monthly is missing`() = runTest {
        val usage = api { _, _ -> HttpStatusCode.OK to """{"data":{"usage":2.0}}""" }.fetchOpenRouter("k")
        assertEquals(2.0, usage.costUsd, 1e-9)
    }

    @Test
    fun `invalid key is reported per provider`() = runTest {
        val error = assertThrows(AiUsageException::class.java) {
            runBlocking { api { _, _ -> HttpStatusCode.Unauthorized to "{}" }.fetchOpenRouter("bad") }
        }
        assertEquals("OpenRouter: Key ungültig", error.message)
    }
}
