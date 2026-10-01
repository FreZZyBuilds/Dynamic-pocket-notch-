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
    private val headers = mutableListOf<io.ktor.http.Headers>()

    private fun api(handler: (path: String, page: String?) -> Pair<HttpStatusCode, String>) = AiUsageApi(
        createJsonHttpClient(MockEngine { request ->
            requests += request.url.toString()
            headers += request.headers
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

        assertEquals(1.423, usage.costUsd!!, 1e-9)
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

        assertEquals(3.48, usage.costUsd!!, 1e-9)
        assertNull(usage.tokens)
        assertEquals("https://openrouter.ai/api/v1/key", requests.single())
    }

    @Test
    fun `openrouter falls back to total usage when usage_monthly is missing`() = runTest {
        val usage = api { _, _ -> HttpStatusCode.OK to """{"data":{"usage":2.0}}""" }.fetchOpenRouter("k")
        assertEquals(2.0, usage.costUsd!!, 1e-9)
    }

    @Test
    fun `invalid key is reported per provider`() = runTest {
        val error = assertThrows(AiUsageException::class.java) {
            runBlocking { api { _, _ -> HttpStatusCode.Unauthorized to "{}" }.fetchOpenRouter("bad") }
        }
        assertEquals("OpenRouter: Key ungültig", error.message)
    }

    @Test
    fun `anthropic converts cent strings to dollars and sums all token kinds`() = runTest {
        val usage = api { path, page ->
            HttpStatusCode.OK to when {
                path.endsWith("/cost_report") && page == null ->
                    """{"data":[{"starting_at":"2026-10-01T00:00:00Z","results":[{"amount":"123.45","currency":"USD"}]}],"has_more":true,"next_page":"page_2"}"""
                path.endsWith("/cost_report") ->
                    """{"data":[{"results":[{"amount":"76.55","currency":"USD"}]}],"has_more":false,"next_page":null}"""
                else ->
                    """{"data":[{"results":[{"uncached_input_tokens":1500,"cache_read_input_tokens":200,"output_tokens":500,
                        "cache_creation":{"ephemeral_1h_input_tokens":10,"ephemeral_5m_input_tokens":5}}]}],"has_more":false}"""
            }
        }.fetchAnthropic("sk-ant-admin01-test")

        assertEquals(2.0, usage.costUsd!!, 1e-9) // 123.45 ct + 76.55 ct = $2.00
        assertEquals(2_215L, usage.tokens)
        assertEquals(true, requests.first().contains("starting_at=2026-10-01T00%3A00%3A00Z"))
        assertEquals("sk-ant-admin01-test", headers.first()["x-api-key"])
        assertEquals("2023-06-01", headers.first()["anthropic-version"])
    }

    @Test
    fun `anthropic with a workspace key explains that an admin key is needed`() = runTest {
        val error = assertThrows(AiUsageException::class.java) {
            runBlocking { api { _, _ -> HttpStatusCode.Forbidden to "{}" }.fetchAnthropic("sk-ant-api03-x") }
        }
        assertEquals("Anthropic: Admin-Key (sk-ant-admin01-…) nötig", error.message)
    }

    @Test
    fun `gemini only validates the key and reports no costs`() = runTest {
        val usage = api { _, _ -> HttpStatusCode.OK to """{"models":[{"name":"models/gemini-2.5-flash"}]}""" }
            .fetchGemini("AIza-test")
        assertNull(usage.costUsd)
        assertEquals("Key gültig · Kosten nur im AI-Studio-Dashboard", usage.detail)
        assertEquals("AIza-test", headers.single()["x-goog-api-key"])

        val error = assertThrows(AiUsageException::class.java) {
            runBlocking { api { _, _ -> HttpStatusCode.BadRequest to "{}" }.fetchGemini("bad") }
        }
        assertEquals("Gemini: Key ungültig", error.message)
    }

    @Test
    fun `ollama reports server status, local usage is free`() = runTest {
        val usage = api { path, _ ->
            HttpStatusCode.OK to when {
                path.endsWith("/api/version") -> """{"version":"0.12.3"}"""
                path.endsWith("/api/ps") -> """{"models":[{"name":"llama3.2:3b","size_vram":2000000000}]}"""
                else -> """{"models":[{"name":"llama3.2:3b"},{"name":"qwen2.5-coder:7b"},{"name":"nomic-embed-text"}]}"""
            }
        }.fetchOllama("http://192.168.1.20:11434/", apiKey = null)

        assertEquals(0.0, usage.costUsd!!, 0.0)
        assertEquals("v0.12.3 · llama3.2:3b geladen · 3 installiert", usage.detail)
        assertEquals("http://192.168.1.20:11434/api/version", requests.first())
    }

    @Test
    fun `ollama cloud has no cost api`() = runTest {
        val usage = api { path, _ ->
            HttpStatusCode.OK to if (path.endsWith("/api/version")) """{"version":"0.12.3"}""" else """{"models":[]}"""
        }.fetchOllama("https://ollama.com", apiKey = "ollama-key")
        assertNull(usage.costUsd)
        assertEquals("Bearer ollama-key", headers.first()[HttpHeaders.Authorization])
    }
}
