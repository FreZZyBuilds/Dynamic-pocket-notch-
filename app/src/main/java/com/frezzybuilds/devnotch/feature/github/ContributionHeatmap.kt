package com.frezzybuilds.devnotch.feature.github

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** GitHub-Standardpalette (Dark Mode): leer + vier Grüntöne. */
val HeatmapColors = mapOf(
    ContributionLevel.NONE to Color(0xFF161B22),
    ContributionLevel.FIRST_QUARTILE to Color(0xFF0E4429),
    ContributionLevel.SECOND_QUARTILE to Color(0xFF006D32),
    ContributionLevel.THIRD_QUARTILE to Color(0xFF26A641),
    ContributionLevel.FOURTH_QUARTILE to Color(0xFF39D353)
)

/**
 * Miniatur-Heatmap wie im GitHub-Profil: eine Spalte pro Woche, eine Zeile pro Wochentag
 * (Sonntag oben), abgerundete Quadrate. Als Canvas gezeichnet – ~112 Zellen ohne 112 Composables.
 */
@Composable
fun ContributionHeatmap(
    weeks: List<List<ContributionDay>>,
    modifier: Modifier = Modifier,
    cellSize: Dp = 8.dp,
    gap: Dp = 3.dp
) {
    Canvas(
        modifier.size(
            width = (cellSize + gap) * weeks.size - gap,
            height = (cellSize + gap) * 7 - gap
        )
    ) {
        val cell = cellSize.toPx()
        val step = cell + gap.toPx()
        val radius = CornerRadius(2.dp.toPx())
        weeks.forEachIndexed { column, week ->
            // Tag über den Wochentag platzieren: Die erste/letzte Woche kann unvollständig sein.
            week.forEach { day ->
                drawRoundRect(
                    color = HeatmapColors.getValue(day.level),
                    topLeft = Offset(column * step, day.weekdayIndex * step),
                    size = Size(cell, cell),
                    cornerRadius = radius
                )
            }
        }
    }
}
