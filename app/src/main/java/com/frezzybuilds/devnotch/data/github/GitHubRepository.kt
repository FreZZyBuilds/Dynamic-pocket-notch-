package com.frezzybuilds.devnotch.data.github

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

fun createGitHubHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

/**
 * Lädt Contribution-Kalender und offene Pull Requests des Token-Inhabers über die
 * GitHub GraphQL API (ein einziger Request).
 */
class GitHubRepository(
    private val client: HttpClient,
    private val tokenStore: () -> String?
) {

    suspend fun fetchDashboard(): GitHubDashboard {
        val token = tokenStore() ?: throw MissingTokenException()

        val response = client.post(ENDPOINT) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(GraphQLRequest(DASHBOARD_QUERY))
        }
        when {
            response.status == HttpStatusCode.Unauthorized ->
                throw GitHubException("Token ungültig oder abgelaufen")
            !response.status.isSuccess() ->
                throw GitHubException("GitHub-API: HTTP ${response.status.value}")
        }

        val body = response.body<GraphQLResponse<DashboardData>>()
        body.errors?.firstOrNull()?.let { throw GitHubException(it.message) }
        val data = body.data ?: throw GitHubException("Leere Antwort von GitHub")
        return data.toDashboard()
    }

    private fun DashboardData.toDashboard(): GitHubDashboard {
        val calendar = viewer.contributionsCollection.contributionCalendar
        return GitHubDashboard(
            login = viewer.login,
            totalContributions = calendar.totalContributions,
            weeks = calendar.weeks.map { week ->
                week.contributionDays.map { day ->
                    ContributionDay(
                        date = LocalDate.parse(day.date),
                        count = day.contributionCount,
                        level = ContributionLevel.entries
                            .firstOrNull { it.name == day.contributionLevel }
                            ?: ContributionLevel.NONE
                    )
                }
            },
            openPullRequests = viewer.pullRequests.totalCount,
            reviewRequests = reviewRequests.issueCount,
            recentPullRequests = viewer.pullRequests.nodes.map {
                PullRequestSummary(it.repository.nameWithOwner, it.number, it.title, it.url)
            }
        )
    }

    private companion object {
        const val ENDPOINT = "https://api.github.com/graphql"

        val DASHBOARD_QUERY = """
            query {
              viewer {
                login
                contributionsCollection {
                  contributionCalendar {
                    totalContributions
                    weeks { contributionDays { date contributionCount contributionLevel } }
                  }
                }
                pullRequests(states: OPEN, first: 5, orderBy: {field: UPDATED_AT, direction: DESC}) {
                  totalCount
                  nodes { number title url repository { nameWithOwner } }
                }
              }
              reviewRequests: search(query: "is:open is:pr archived:false review-requested:@me", type: ISSUE) {
                issueCount
              }
            }
        """.trimIndent()
    }
}

// --- GraphQL DTOs ---

@Serializable
private data class GraphQLRequest(val query: String)

@Serializable
private data class GraphQLResponse<T>(val data: T? = null, val errors: List<GraphQLError>? = null)

@Serializable
private data class GraphQLError(val message: String)

@Serializable
private data class DashboardData(val viewer: ViewerDto, val reviewRequests: SearchCountDto)

@Serializable
private data class ViewerDto(
    val login: String,
    val contributionsCollection: ContributionsCollectionDto,
    val pullRequests: PullRequestConnectionDto
)

@Serializable
private data class ContributionsCollectionDto(val contributionCalendar: CalendarDto)

@Serializable
private data class CalendarDto(val totalContributions: Int, val weeks: List<WeekDto>)

@Serializable
private data class WeekDto(val contributionDays: List<DayDto>)

@Serializable
private data class DayDto(val date: String, val contributionCount: Int, val contributionLevel: String)

@Serializable
private data class PullRequestConnectionDto(val totalCount: Int, val nodes: List<PullRequestDto>)

@Serializable
private data class PullRequestDto(
    val number: Int,
    val title: String,
    val url: String,
    val repository: RepositoryDto
)

@Serializable
private data class RepositoryDto(val nameWithOwner: String)

@Serializable
private data class SearchCountDto(val issueCount: Int)
