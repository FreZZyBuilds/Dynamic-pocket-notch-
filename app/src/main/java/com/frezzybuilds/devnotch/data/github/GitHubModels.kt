package com.frezzybuilds.devnotch.data.github

import java.time.LocalDate

data class GitHubDashboard(
    val login: String,
    val totalContributions: Int,
    /** Wochen (Sonntag–Samstag) wie im GitHub-Profil, älteste zuerst. */
    val weeks: List<List<ContributionDay>>,
    val openPullRequests: Int,
    val reviewRequests: Int,
    val recentPullRequests: List<PullRequestSummary>
)

data class ContributionDay(
    val date: LocalDate,
    val count: Int,
    val level: ContributionLevel
) {
    /** 0 = Sonntag … 6 = Samstag (Zeile in der Heatmap). */
    val weekdayIndex: Int get() = date.dayOfWeek.value % 7
}

enum class ContributionLevel { NONE, FIRST_QUARTILE, SECOND_QUARTILE, THIRD_QUARTILE, FOURTH_QUARTILE }

data class PullRequestSummary(
    val repository: String,
    val number: Int,
    val title: String,
    val url: String
)

class MissingTokenException : Exception("Kein GitHub-Token hinterlegt")

class GitHubException(message: String) : Exception(message)
