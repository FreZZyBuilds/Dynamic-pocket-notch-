package com.frezzybuilds.devnotch.feature.github

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.frezzybuilds.devnotch.appContainer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.frezzybuilds.devnotch.feature.shortcuts.ProjectShortcutsContent

/** Bereiche des Dev-Tabs; die Wahl bleibt erhalten, solange die Notch läuft. */
enum class DevSection(val title: String) { GITHUB("GitHub"), PROJECTS("Projekte") }

private var lastDevSection by mutableStateOf(DevSection.GITHUB)

/**
 * Dev-Tab: umschaltbar zwischen GitHub (Profil + Heatmap) und Projekt-Kacheln.
 * @param onLaunched Notch einklappen, nachdem eine App/URL gestartet wurde.
 */
@Composable
fun DevTabContent(onLaunched: () -> Unit = {}) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DevSection.entries.forEach { section ->
                val active = section == lastDevSection
                Text(
                    section.title,
                    color = if (active) Color.Black else Color.Gray,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (active) Color.White else Color(0xFF1E1E1E))
                        .clickable { lastDevSection = section }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
        when (lastDevSection) {
            DevSection.GITHUB -> GitHubSection()
            DevSection.PROJECTS -> ProjectShortcutsContent(onLaunched = onLaunched)
        }
    }
}

@Composable
private fun GitHubSection() {
    val context = LocalContext.current
    val container = context.appContainer
    val viewModel = viewModel { GitHubViewModel(container.gitHubService, container.gitHubSettings) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshIfStale() }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val s = state) {
                GitHubUiState.Loading -> CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(24.dp).align(Alignment.Center)
                )
                GitHubUiState.NoToken -> Message(
                    "Kein GitHub-Token. In der DevNotch-App unter „GitHub“ hinterlegen.",
                    onRetry = viewModel::refresh
                )
                is GitHubUiState.Error -> Message(s.message, onRetry = viewModel::refresh)
                is GitHubUiState.Success -> Profile(s.profile, onRefresh = viewModel::refresh)
            }
        }
    }
}

@Composable
private fun Profile(profile: GitHubProfile, onRefresh: () -> Unit) {
    // Platzbudget im 360×280-Dashboard: ~160 dp für Profilzeile + Heatmap + Shortcut-Chips.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AsyncImage(
                model = profile.avatarUrl,
                contentDescription = "Avatar von ${profile.login}",
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A2A))
            )
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name ?: "@${profile.login}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "@${profile.login} · ${profile.openPullRequests} PRs offen",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
            Text(
                "↻",
                color = Color.Gray,
                modifier = Modifier
                    .clickable(onClick = onRefresh)
                    .padding(horizontal = 8.dp)
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            ContributionHeatmap(profile.weeks)
            Text(
                "${profile.weeks.sumOf { w -> w.sumOf { it.count } }}\nBeiträge",
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@Composable
private fun Message(text: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onRetry) { Text("Erneut versuchen") }
    }
}
