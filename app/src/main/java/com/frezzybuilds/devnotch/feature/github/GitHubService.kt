package com.frezzybuilds.devnotch.feature.github

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

data class GitHubProfile(
    val login: String,
    val name: String?,
    val avatarUrl: String,
    val openPullRequests: Int,
    val totalContributions: Int,
    /** Wochen (Sonntag–Samstag), älteste zuerst; höchstens so viele wie angefragt. */
    val weeks: List<List<ContributionDay>>
)

data class ContributionDay(
    val date: LocalDate,
    val count: Int,
    val level: ContributionLevel
) {
    /** 0 = Sonntag … 6 = Samstag (Zeile in der Heatmap). */
    val weekdayIndex: Int get() = date.dayOfWeek.value % 7
}

/** GitHubs eigene Einstufung: keine Beiträge + vier Quartile (= vier Grüntöne). */
enum class ContributionLevel { NONE, FIRST_QUARTILE, SECOND_QUARTILE, THIRD_QUARTILE, FOURTH_QUARTILE }

class MissingTokenException : Exception("Kein GitHub-Token hinterlegt")

class GitHubException(message: String) : Exception(message)

/**
 * Lädt Profil und Contribution-Kalender der letzten Wochen über die GitHub GraphQL API.
 *
 * Die GraphQL-API braucht immer einen Token, auch für öffentliche Profile. Ohne
 * Benutzernamen wird der Inhaber des Tokens (`viewer`) angezeigt.
 */
class GitHubService(
    private val client: HttpClient,
    private val token: () -> String?,
    private val clock: Clock = Clock.systemUTC()
) {

    suspend fun fetchProfile(username: String?, weeks: Int = DEFAULT_WEEKS): GitHubProfile {
        require(weeks in 1..52) { "weeks must be between 1 and 52" }
        val token = token() ?: throw MissingTokenException()

        // Zeitraum an Wochengrenzen (Sonntag) ausrichten, damit die erste Spalte vollständig ist.
        val today = LocalDate.now(clock)
        val from = today.minusWeeks(weeks - 1L)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            .atStartOfDay(ZoneOffset.UTC)
        val variables = buildMap {
            put("from", from.toString())
            put("to", clock.instant().toString())
            if (username != null) put("login", username)
        }

        val response = client.post(ENDPOINT) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(GraphQLRequest(if (username != null) USER_QUERY else VIEWER_QUERY, variables))
        }
        when {
            response.status == HttpStatusCode.Unauthorized ->
                throw GitHubException("Token ungültig oder abgelaufen")
            !response.status.isSuccess() ->
                throw GitHubException("GitHub-API: HTTP ${response.status.value}")
        }

        val body = response.body<GraphQLResponse>()
        body.errors?.firstOrNull()?.let { throw GitHubException(it.message) }
        val user = body.data?.user ?: body.data?.viewer
            ?: throw GitHubException("Benutzer „$username“ nicht gefunden")
        return user.toProfile(weeks)
    }

    private fun UserDto.toProfile(weeks: Int): GitHubProfile {
        val calendar = contributionsCollection.contributionCalendar
        return GitHubProfile(
            login = login,
            name = name,
            avatarUrl = avatarUrl,
            openPullRequests = pullRequests.totalCount,
            totalContributions = calendar.totalContributions,
            weeks = calendar.weeks.takeLast(weeks).map { week ->
                week.contributionDays.map { day ->
                    ContributionDay(
                        date = LocalDate.parse(day.date),
                        count = day.contributionCount,
                        level = ContributionLevel.entries
                            .firstOrNull { it.name == day.contributionLevel }
                            ?: ContributionLevel.NONE
                    )
                }
            }
        )
    }

    companion object {
        const val DEFAULT_WEEKS = 16
        private const val ENDPOINT = "https://api.github.com/graphql"

        private val PROFILE_FRAGMENT = """
            fragment Profile on User {
              login
              name
              avatarUrl(size: 96)
              pullRequests(states: OPEN) { totalCount }
              contributionsCollection(from: ${'$'}from, to: ${'$'}to) {
                contributionCalendar {
                  totalContributions
                  weeks { contributionDays { date contributionCount contributionLevel } }
                }
              }
            }
        """.trimIndent()

        private val USER_QUERY = """
            query(${'$'}login: String!, ${'$'}from: DateTime!, ${'$'}to: DateTime!) {
              user(login: ${'$'}login) { ...Profile }
            }
        """.trimIndent() + "\n" + PROFILE_FRAGMENT

        private val VIEWER_QUERY = """
            query(${'$'}from: DateTime!, ${'$'}to: DateTime!) {
              viewer { ...Profile }
            }
        """.trimIndent() + "\n" + PROFILE_FRAGMENT
    }
}

// --- GraphQL DTOs ---

@Serializable
private data class GraphQLRequest(val query: String, val variables: Map<String, String>)

@Serializable
private data class GraphQLResponse(val data: DataDto? = null, val errors: List<GraphQLError>? = null)

@Serializable
private data class GraphQLError(val message: String)

@Serializable
private data class DataDto(val user: UserDto? = null, val viewer: UserDto? = null)

@Serializable
private data class UserDto(
    val login: String,
    val name: String? = null,
    val avatarUrl: String,
    val pullRequests: CountDto,
    val contributionsCollection: ContributionsCollectionDto
)

@Serializable
private data class CountDto(val totalCount: Int)

@Serializable
private data class ContributionsCollectionDto(val contributionCalendar: CalendarDto)

@Serializable
private data class CalendarDto(val totalContributions: Int, val weeks: List<WeekDto>)

@Serializable
private data class WeekDto(val contributionDays: List<DayDto>)

@Serializable
private data class DayDto(val date: String, val contributionCount: Int, val contributionLevel: String)
