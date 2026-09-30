package com.frezzybuilds.devnotch.feature.github

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class GitHubServiceTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    // Mittwoch, 30.09.2026
    private val clock = Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC)

    private class Captured {
        var authorization: String? = null
        var query: String = ""
        var variables: Map<String, String> = emptyMap()
    }

    private fun service(
        token: String? = "ghp_test",
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = response("user", weeks = 20),
        captured: Captured = Captured()
    ) = GitHubService(
        client = createGitHubHttpClient(MockEngine { request ->
            captured.authorization = request.headers[HttpHeaders.Authorization]
            val json = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            captured.query = json.getValue("query").jsonPrimitive.content
            captured.variables = json.getValue("variables").jsonObject
                .mapValues { it.value.jsonPrimitive.content }
            respond(body, status, jsonHeaders)
        }),
        token = { token },
        clock = clock
    )

    @Test
    fun `queries given user for the last 16 weeks aligned to sunday`() = runTest {
        val captured = Captured()
        service(captured = captured).fetchProfile("octocat")

        assertEquals("Bearer ghp_test", captured.authorization)
        assertTrue(captured.query.contains("user(login: \$login)"))
        assertEquals("octocat", captured.variables["login"])
        // 15 Wochen vor dem 30.09. ist der 17.06. (Mi) → vorheriger Sonntag 14.06.
        assertEquals("2026-06-14T00:00Z", captured.variables["from"])
        assertEquals("2026-09-30T12:00:00Z", captured.variables["to"])
    }

    @Test
    fun `without username the token owner is queried`() = runTest {
        val captured = Captured()
        service(body = response("viewer", weeks = 3), captured = captured).fetchProfile(null)

        assertTrue(captured.query.contains("viewer {"))
        assertFalse(captured.variables.containsKey("login"))
    }

    @Test
    fun `maps profile and keeps at most the requested weeks`() = runTest {
        val profile = service().fetchProfile("octocat")

        assertEquals("octocat", profile.login)
        assertEquals("The Octocat", profile.name)
        assertEquals("https://avatars.example/octocat", profile.avatarUrl)
        assertEquals(2, profile.openPullRequests)
        assertEquals(16, profile.weeks.size)
        val day = profile.weeks.last().first()
        assertEquals(0, day.weekdayIndex) // Sonntag
        assertEquals(ContributionLevel.THIRD_QUARTILE, day.level)
    }

    @Test
    fun `unknown user surfaces graphql error`() = runTest {
        val body = """{"data":{"user":null},"errors":[{"message":"Could not resolve to a User with the login of 'nobody'."}]}"""
        val error = assertThrows(GitHubException::class.java) {
            runBlocking { service(body = body).fetchProfile("nobody") }
        }
        assertEquals("Could not resolve to a User with the login of 'nobody'.", error.message)
    }

    @Test
    fun `missing token fails before any request`() = runTest {
        assertThrows(MissingTokenException::class.java) {
            runBlocking { service(token = null).fetchProfile("octocat") }
        }
    }

    @Test
    fun `unauthorized maps to readable error`() = runTest {
        val error = assertThrows(GitHubException::class.java) {
            runBlocking { service(status = HttpStatusCode.Unauthorized, body = "{}").fetchProfile("octocat") }
        }
        assertEquals("Token ungültig oder abgelaufen", error.message)
    }

    /** Antwort mit [weeks] vollständigen Wochen, die letzte beginnt am Sonntag 27.09.2026. */
    private fun response(root: String, weeks: Int): String {
        val lastSunday = LocalDate.of(2026, 9, 27)
        val weekJson = (weeks - 1 downTo 0).joinToString(",") { back ->
            val start = lastSunday.minusWeeks(back.toLong())
            val days = (0..6).joinToString(",") { d ->
                """{"date":"${start.plusDays(d.toLong())}","contributionCount":$d,"contributionLevel":"THIRD_QUARTILE"}"""
            }
            """{"contributionDays":[$days]}"""
        }
        return """
            {"data":{"$root":{
              "login":"octocat","name":"The Octocat","avatarUrl":"https://avatars.example/octocat",
              "pullRequests":{"totalCount":2},
              "contributionsCollection":{"contributionCalendar":{"totalContributions":99,"weeks":[$weekJson]}}
            }}}
        """.trimIndent()
    }
}
