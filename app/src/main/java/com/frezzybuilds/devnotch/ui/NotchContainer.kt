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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.max
import com.frezzybuilds.devnotch.service.EdgeSide
import com.frezzybuilds.devnotch.service.NotchLayout
import com.frezzybuilds.devnotch.service.NotchLayoutMode
import kotlinx.coroutines.delay

private val NotchBlack = Color(0xFF000000)
private val PillWidth = 120.dp
private val PillHeight = 35.dp
private val DashboardWidth = 360.dp
private val DashboardHeight = 280.dp

private enum class NotchTab(val title: String) {
    OVERVIEW("Overview"),
    POMODORO("Pomodoro"),
    CLIPBOARD("Clipboard"),
    DEV("Dev")
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
    var selectedTab by remember { mutableStateOf(NotchTab.OVERVIEW) }

    // Timer-State liegt hier (nicht im Tab), damit er auch eingeklappt weiterläuft.
    val pomodoro = remember { PomodoroState() }
    LaunchedEffect(pomodoro.isRunning) {
        while (pomodoro.isRunning) {
            delay(1_000)
            pomodoro.tick()
        }
    }

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
                    pomodoro = pomodoro,
                    onClose = { setExpanded(false) },
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopCenter, unbounded = true)
                        .size(DashboardWidth, DashboardHeight)
                )
            } else if (pomodoro.isRunning && layout.mode == NotchLayoutMode.NOTCH_TOP) {
                // Rechts neben dem Punch-Hole, damit die Kamera frei bleibt.
                Text(
                    text = pomodoro.formatted,
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
    pomodoro: PomodoroState,
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
                NotchTab.OVERVIEW -> OverviewTabContent(pomodoro)
                NotchTab.POMODORO -> PomodoroTabContent(pomodoro)
                NotchTab.CLIPBOARD -> ClipboardTabContent()
                NotchTab.DEV -> DevTabContent()
            }
        }
    }
}

/** 120×35 dp; oben so breit/hoch, dass das Punch-Hole umschlossen wird. Am Rand hochkant. */
@Composable
private fun collapsedSize(layout: NotchLayout): Pair<Dp, Dp> {
    val density = LocalDensity.current
    return when (layout.mode) {
        NotchLayoutMode.NOTCH_TOP -> {
            val cutout = layout.cutout
            val holeWidth = with(density) { (cutout?.width ?: 0).toDp() }
            // Oberkante liegt bei y = 0, daher top + bottom für symmetrischen Abstand ums Loch.
            val holeSpan = with(density) { ((cutout?.top ?: 0) + (cutout?.bottom ?: 0)).toDp() }
            max(PillWidth, holeWidth + 48.dp) to max(PillHeight, holeSpan)
        }
        NotchLayoutMode.EDGE_SIDE -> PillHeight to PillWidth
    }
}

/** Oben rundum abgerundet; am Rand nur die zur Bildschirmmitte zeigende Seite. */
private fun notchShape(layout: NotchLayout, radius: Dp): RoundedCornerShape =
    when {
        layout.mode == NotchLayoutMode.NOTCH_TOP -> RoundedCornerShape(radius)
        layout.side == EdgeSide.RIGHT -> RoundedCornerShape(topStart = radius, bottomStart = radius)
        else -> RoundedCornerShape(topEnd = radius, bottomEnd = radius)
    }

/** Einfacher Pomodoro-Timer (25 min Fokus). */
class PomodoroState(private val durationSeconds: Int = 25 * 60) {
    var remainingSeconds by mutableIntStateOf(durationSeconds)
        private set
    var isRunning by mutableStateOf(false)
        private set

    val formatted: String
        get() = "%02d:%02d".format(remainingSeconds / 60, remainingSeconds % 60)

    val progress: Float
        get() = 1f - remainingSeconds.toFloat() / durationSeconds

    fun toggle() {
        if (remainingSeconds == 0) remainingSeconds = durationSeconds
        isRunning = !isRunning
    }

    fun reset() {
        isRunning = false
        remainingSeconds = durationSeconds
    }

    fun tick() {
        if (remainingSeconds > 0) remainingSeconds--
        if (remainingSeconds == 0) isRunning = false
    }
}
