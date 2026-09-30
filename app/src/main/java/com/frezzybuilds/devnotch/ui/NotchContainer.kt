package com.frezzybuilds.devnotch.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frezzybuilds.devnotch.feature.github.DevTabContent
import com.frezzybuilds.devnotch.service.NotchLayout
import com.frezzybuilds.devnotch.service.NotchLayoutMode
import com.frezzybuilds.devnotch.ui.clipboard.ClipboardContent
import com.frezzybuilds.devnotch.ui.focus.FocusTimerTab
import com.frezzybuilds.devnotch.ui.focus.formatMmSs
import com.frezzybuilds.devnotch.ui.focus.FocusTimerViewModel

private val NotchBlack = Color(0xFF000000)
private val PillWidth = 120.dp
private val DashboardWidth = 360.dp
private val DashboardHeight = 280.dp
private val EdgeHandleWidth = 20.dp

private enum class NotchTab(val title: String) {
    DEV("Dev"),
    OVERVIEW("Overview"),
    TIMER("Timer"),
    CLIP("Clip")
}

/** Eine Feder für Größe und Eckenradius, damit beides synchron „nachfedert“. */
private fun <T> notchSpring() = spring<T>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)

@Composable
fun NotchContainer(
    layout: NotchLayout,
    onExpandRequest: (Boolean) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(NotchTab.DEV) }

    // ViewModel hängt am ViewModelStore des Service: Der Timer läuft auch eingeklappt weiter.
    val focusTimer: FocusTimerViewModel = viewModel { FocusTimerViewModel() }
    val timerRemaining by focusTimer.remainingTime.collectAsStateWithLifecycle()
    val timerTotal by focusTimer.totalTime.collectAsStateWithLifecycle()
    val timerRunning by focusTimer.isRunning.collectAsStateWithLifecycle()
    // Eingeklappt nur anzeigen, wenn der Timer läuft oder angebrochen pausiert ist.
    val showTimerInPill = timerRunning || timerRemaining != timerTotal

    fun setExpanded(expanded: Boolean) {
        isExpanded = expanded
        onExpandRequest(expanded)
    }

    val (collapsedWidth, collapsedHeight) = collapsedSize(layout)
    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 24.dp else minOf(collapsedWidth, collapsedHeight) / 2,
        animationSpec = notchSpring(),
        label = "notchCorner"
    )

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            modifier = Modifier
                .animateContentSize(animationSpec = notchSpring())
                .then(
                    if (isExpanded) Modifier.size(DashboardWidth, DashboardHeight)
                    else Modifier.size(collapsedWidth, collapsedHeight)
                )
                .clip(notchShape(layout, cornerRadius))
                .background(NotchBlack)
                // Nur eingeklappt klickbar, sonst schluckt die Box Taps im Dashboard.
                .clickable(enabled = !isExpanded) { setExpanded(true) },
            contentAlignment = Alignment.Center
        ) {
            if (isExpanded) {
                // Feste Größe + unbounded: Das Dashboard wird beim Aufklappen „aufgedeckt“
                // statt während der Animation zusammengequetscht zu werden.
                Dashboard(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    focusTimer = focusTimer,
                    onClose = { setExpanded(false) },
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopCenter, unbounded = true)
                        .size(DashboardWidth, DashboardHeight)
                )
            } else if (showTimerInPill && layout.mode == NotchLayoutMode.NOTCH_TOP) {
                // Rechts neben dem Punch-Hole, damit die Kamera frei bleibt.
                Text(
                    text = "⏱ ${formatMmSs(timerRemaining)}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun Dashboard(
    selectedTab: NotchTab,
    onSelectTab: (NotchTab) -> Unit,
    focusTimer: FocusTimerViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "DevNotch",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClose) {
                Text("✕", color = Color.Gray)
            }
        }

        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color.Transparent,
            contentColor = Color.White
        ) {
            NotchTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onClick = { onSelectTab(tab) },
                    unselectedContentColor = Color.Gray,
                    text = {
                        Text(
                            tab.title,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
        ) {
            when (selectedTab) {
                NotchTab.OVERVIEW -> OverviewTabContent(focusTimer)
                NotchTab.TIMER -> FocusTimerTab(focusTimer)
                NotchTab.CLIP -> ClipboardContent()
                NotchTab.DEV -> DevTabContent()
            }
        }
    }
}

/**
 * Oben: exakt die vom Service berechnete Pillengröße (120×35 dp oder größer, symmetrisch um
 * die Kameralinse), damit Fensterposition und Inhalt übereinstimmen. Am Rand ein schmaler Griff.
 */
@Composable
private fun collapsedSize(layout: NotchLayout): Pair<Dp, Dp> {
    val density = LocalDensity.current
    return when (layout.mode) {
        NotchLayoutMode.NOTCH_TOP -> with(density) {
            layout.pill.width.toDp() to layout.pill.height.toDp()
        }
        NotchLayoutMode.EDGE_SIDE -> EdgeHandleWidth to PillWidth
    }
}

/**
 * Oben rundum abgerundet. Die Edge Bar klebt per Gravity.END am Rand, daher wird nur die
 * Start-Seite (zur Bildschirmmitte) abgerundet – das passt auch bei RTL automatisch.
 */
private fun notchShape(layout: NotchLayout, radius: Dp): RoundedCornerShape =
    when (layout.mode) {
        NotchLayoutMode.NOTCH_TOP -> RoundedCornerShape(radius)
        NotchLayoutMode.EDGE_SIDE -> RoundedCornerShape(topStart = radius, bottomStart = radius)
    }
