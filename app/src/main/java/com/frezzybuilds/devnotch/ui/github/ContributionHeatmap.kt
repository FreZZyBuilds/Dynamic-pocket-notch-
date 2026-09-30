package com.frezzybuilds.devnotch.ui.github

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.data.github.ContributionDay
import com.frezzybuilds.devnotch.data.github.ContributionLevel

private val CellSize = 9.dp
private val CellGap = 2.dp

/** GitHub-Grüntöne für dunklen Hintergrund. */
private fun ContributionLevel.color(): Color = when (this) {
    ContributionLevel.NONE -> Color(0xFF161B22)
    ContributionLevel.FIRST_QUARTILE -> Color(0xFF0E4429)
    ContributionLevel.SECOND_QUARTILE -> Color(0xFF006D32)
    ContributionLevel.THIRD_QUARTILE -> Color(0xFF26A641)
    ContributionLevel.FOURTH_QUARTILE -> Color(0xFF39D353)
}

/**
 * Minimalistische Contribution-Heatmap: eine Spalte pro Woche, eine Zeile pro Wochentag.
 * Zeigt so viele der jüngsten Wochen, wie in die verfügbare Breite passen.
 */
@Composable
fun ContributionHeatmap(
    weeks: List<List<ContributionDay>>,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val visibleWeeks = ((maxWidth + CellGap) / (CellSize + CellGap)).toInt().coerceAtLeast(1)
        val shown = weeks.takeLast(visibleWeeks)

        Canvas(
            Modifier.size(
                width = (CellSize + CellGap) * shown.size - CellGap,
                height = (CellSize + CellGap) * 7 - CellGap
            )
        ) {
            val cell = CellSize.toPx()
            val step = cell + CellGap.toPx()
            val radius = CornerRadius(2.dp.toPx())
            shown.forEachIndexed { column, week ->
                week.forEach { day ->
                    drawRoundRect(
                        color = day.level.color(),
                        topLeft = Offset(column * step, day.weekdayIndex * step),
                        size = Size(cell, cell),
                        cornerRadius = radius
                    )
                }
            }
        }
    }
}
