package com.frezzybuilds.devnotch.feature.aiusage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.frezzybuilds.devnotch.data.createJsonHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class AiUsageRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var settings: AiUsageSettings
    private var openRouterStatus = HttpStatusCode.OK
    private var openRouterMonthly = 3.48

    private fun api(now: String) = AiUsageApi(
        createJsonHttpClient(MockEngine { request ->
            val body = if (request.url.host == "openrouter.ai") {
                """{"data":{"usage":10.0,"usage_monthly":$openRouterMonthly}}"""
            } else if (request.url.encodedPath.endsWith("/costs")) {
                """{"data":[{"results":[{"amount":{"value":1.0}}]}]}"""
            } else {
                """{"data":[{"results":[{"input_tokens":1000,"output_tokens":500}]}]}"""
            }
            val status = if (request.url.host == "openrouter.ai") openRouterStatus else HttpStatusCode.OK
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }),
        Clock.fixed(Instant.parse(now), ZoneOffset.UTC)
    )

    @Before
    fun setUp() {
        settings = AiUsageSettings(context)
        settings.setKey(AiProvider.OPENAI, "sk-admin-x")
        settings.setKey(AiProvider.OPENROUTER, "sk-or-x")
        settings.cacheJson = null
    }

    @Test
    fun `sums all providers and restores the cache after a restart`() = runTest {
        val repository = AiUsageRepository(api("2026-10-14T10:00:00Z"), settings)
        repository.refresh()
        assertEquals(4.48, repository.state.value.totalCostUsd, 1e-9)
        assertEquals(1_500L, repository.state.value.totalTokens)

        val restarted = AiUsageRepository(api("2026-10-20T10:00:00Z"), settings)
        assertEquals(4.48, restarted.state.value.totalCostUsd, 1e-9)
    }

    @Test
    fun `cache from the previous month is dropped`() = runTest {
        AiUsageRepository(api("2026-10-31T23:00:00Z"), settings).refresh()
        val november = AiUsageRepository(api("2026-11-01T08:00:00Z"), settings)
        assertTrue(november.state.value.usages.isEmpty())
    }

    @Test
    fun `failed provider keeps its last value and reports the error`() = runTest {
        val repository = AiUsageRepository(api("2026-10-14T10:00:00Z"), settings)
        repository.refresh()

        openRouterStatus = HttpStatusCode.Unauthorized
        openRouterMonthly = 99.0
        repository.refresh()

        val state = repository.state.value
        assertEquals(4.48, state.totalCostUsd, 1e-9)
        assertEquals("OpenRouter: Key ungültig", state.errors[AiProvider.OPENROUTER])
    }

    @Test
    fun `without keys nothing is configured`() = runTest {
        settings.setKey(AiProvider.OPENAI, null)
        settings.setKey(AiProvider.OPENROUTER, "")
        val repository = AiUsageRepository(api("2026-10-14T10:00:00Z"), settings)
        repository.refresh()
        assertFalse(repository.state.value.configured)
    }
}
