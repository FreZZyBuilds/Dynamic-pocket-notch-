package com.frezzybuilds.devnotch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.feature.aiusage.AiUsagePanel
import com.frezzybuilds.devnotch.feature.github.DevTabContent
import com.frezzybuilds.devnotch.feature.notes.NotesContent
import com.frezzybuilds.devnotch.service.NowPlaying
import com.frezzybuilds.devnotch.ui.clipboard.ClipboardContent
import com.frezzybuilds.devnotch.ui.focus.FocusTimerTab
import com.frezzybuilds.devnotch.ui.focus.FocusTimerViewModel

/** Inhalte der beiden Drawer-Spalten. */
enum class DrawerPane(val title: String) {
    DEV("Dev-Tools"),
    TIMER("Pomodoro"),
    AI("AI-Kosten"),
    NOTES("Notizen"),
    CLIP("Clipboard");

    companion object {
        val Left = listOf(DEV, TIMER, AI)
        val Right = listOf(NOTES, CLIP)
    }
}

/**
 * Seitlicher Drawer für Tablets und Querformat: zwei Spalten nebeneinander, jede mit eigenem
 * Umschalter. Links Werkzeuge zum Arbeiten (Dev-Tools, Pomodoro), rechts Ablage
 * (Notizen, Clipboard) – so lässt sich z. B. der Timer laufen sehen, während man notiert.
 */
@Composable
fun SplitDrawer(
    focusTimer: FocusTimerViewModel,
    nowPlaying: NowPlaying?,
    leftPane: DrawerPane,
    rightPane: DrawerPane,
    onSelectLeft: (DrawerPane) -> Unit,
    onSelectRight: (DrawerPane) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(12.dp)) {
        DashboardHeader(nowPlaying, onClose)
        Row(Modifier.fillMaxSize().padding(top = 8.dp)) {
            DrawerColumn(DrawerPane.Left, leftPane, onSelectLeft, Modifier.weight(1f)) { pane ->
                when (pane) {
                    DrawerPane.TIMER -> FocusTimerTab(focusTimer)
                    DrawerPane.AI -> AiUsagePanel()
                    else -> DevTabContent(onLaunched = onClose)
                }
            }
            Box(
                Modifier
                    .padding(horizontal = 12.dp)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF2A2A2A))
            )
            DrawerColumn(DrawerPane.Right, rightPane, onSelectRight, Modifier.weight(1f)) { pane ->
                when (pane) {
                    DrawerPane.CLIP -> ClipboardContent()
                    else -> NotesContent(onLeaveForExternalApp = onClose)
                }
            }
        }
    }
}

@Composable
private fun DrawerColumn(
    panes: List<DrawerPane>,
    selected: DrawerPane,
    onSelect: (DrawerPane) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (DrawerPane) -> Unit
) {
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Kompakte Pillen-Umschalter statt TabRow: spart Höhe im Drawer.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            panes.forEach { pane ->
                val active = pane == selected
                Text(
                    pane.title,
                    color = if (active) Color.Black else Color.Gray,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (active) Color.White else Color(0xFF1E1E1E))
                        .clickable { onSelect(pane) }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
        Box(Modifier.weight(1f)) { content(selected) }
    }
}
