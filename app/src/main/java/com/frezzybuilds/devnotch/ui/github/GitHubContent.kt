package com.frezzybuilds.devnotch.ui.github

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frezzybuilds.devnotch.appContainer
import com.frezzybuilds.devnotch.data.github.GitHubDashboard

@Composable
fun GitHubContent() {
    val context = LocalContext.current
    val viewModel = viewModel { GitHubViewModel(context.appContainer.gitHubRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshIfStale() }

    when (val s = state) {
        GitHubUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        GitHubUiState.NoToken -> Message(
            "Kein GitHub-Token. In der DevNotch-App unter „GitHub“ hinterlegen.",
            onRetry = viewModel::refresh
        )
        is GitHubUiState.Error -> Message(s.message, onRetry = viewModel::refresh)
        is GitHubUiState.Success -> Dashboard(s.dashboard, onRefresh = viewModel::refresh)
    }
}

@Composable
private fun Dashboard(dashboard: GitHubDashboard, onRefresh: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "@${dashboard.login} · ${dashboard.totalContributions} Beiträge",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                "↻",
                color = Color.Gray,
                modifier = Modifier
                    .clickable(onClick = onRefresh)
                    .padding(horizontal = 8.dp)
            )
        }
        ContributionHeatmap(dashboard.weeks, Modifier.fillMaxWidth())
        Text(
            "PRs offen: ${dashboard.openPullRequests} · Review angefragt: ${dashboard.reviewRequests}",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium
        )
        dashboard.recentPullRequests.forEach { pr ->
            Text(
                "${pr.repository}#${pr.number} ${pr.title}",
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openUrl(context, pr.url) }
            )
        }
    }
}

@Composable
private fun Message(text: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onRetry) { Text("Erneut versuchen") }
    }
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
