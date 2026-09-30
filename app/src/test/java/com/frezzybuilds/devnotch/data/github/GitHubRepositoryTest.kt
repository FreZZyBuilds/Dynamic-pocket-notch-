package com.frezzybuilds.devnotch.data.github

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class GitHubRepositoryTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun repository(
        token: String? = "ghp_test",
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = SAMPLE_RESPONSE,
        onRequest: (authorization: String?) -> Unit = {}
    ) = GitHubRepository(
        client = createGitHubHttpClient(MockEngine { request ->
            onRequest(request.headers[HttpHeaders.Authorization])
            respond(body, status, jsonHeaders)
        }),
        tokenStore = { token }
    )

    @Test
    fun `parses contributions and pull requests`() = runTest {
        var auth: String? = null
        val dashboard = repository(onRequest = { auth = it }).fetchDashboard()

        assertEquals("Bearer ghp_test", auth)
        assertEquals("octocat", dashboard.login)
        assertEquals(42, dashboard.totalContributions)
        assertEquals(2, dashboard.weeks.size)
        val day = dashboard.weeks[0][0]
        assertEquals(LocalDate.of(2026, 9, 20), day.date)
        assertEquals(0, day.weekdayIndex) // Sonntag
        assertEquals(ContributionLevel.SECOND_QUARTILE, day.level)
        assertEquals(3, dashboard.openPullRequests)
        assertEquals(1, dashboard.reviewRequests)
        assertEquals("octo/repo", dashboard.recentPullRequests.single().repository)
    }

    @Test
    fun `missing token fails without request`() = runTest {
        assertThrows(MissingTokenException::class.java) {
            kotlinx.coroutines.runBlocking { repository(token = null).fetchDashboard() }
        }
    }

    @Test
    fun `unauthorized maps to readable error`() = runTest {
        val error = assertThrows(GitHubException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository(status = HttpStatusCode.Unauthorized, body = "{}").fetchDashboard()
            }
        }
        assertEquals("Token ungültig oder abgelaufen", error.message)
    }

    @Test
    fun `graphql errors are surfaced`() = runTest {
        val error = assertThrows(GitHubException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository(body = """{"errors":[{"message":"Bad credentials scope"}]}""").fetchDashboard()
            }
        }
        assertEquals("Bad credentials scope", error.message)
    }

    private companion object {
        val SAMPLE_RESPONSE = """
            {"data":{
              "viewer":{
                "login":"octocat",
                "contributionsCollection":{"contributionCalendar":{
                  "totalContributions":42,
                  "weeks":[
                    {"contributionDays":[
                      {"date":"2026-09-20","contributionCount":4,"contributionLevel":"SECOND_QUARTILE"},
                      {"date":"2026-09-21","contributionCount":0,"contributionLevel":"NONE"}]},
                    {"contributionDays":[
                      {"date":"2026-09-27","contributionCount":12,"contributionLevel":"FOURTH_QUARTILE"}]}
                  ]}},
                "pullRequests":{"totalCount":3,"nodes":[
                  {"number":7,"title":"Add notch","url":"https://github.com/octo/repo/pull/7",
                   "repository":{"nameWithOwner":"octo/repo"}}]}
              },
              "reviewRequests":{"issueCount":1}
            }}
        """.trimIndent()
    }
}
