package com.frezzybuilds.devnotch.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.frezzybuilds.devnotch.service.EdgeSide
import com.frezzybuilds.devnotch.service.NotchLayout
import com.frezzybuilds.devnotch.service.NotchLayoutMode

@Composable
fun NotchContainer(
    layout: NotchLayout,
    onExpandRequest: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    val (width, height) = when (layout.mode) {
        NotchLayoutMode.NOTCH_TOP -> if (expanded) {
            320.dp to 140.dp
        } else {
            // Pille vertikal symmetrisch um das Punch-Hole legen (Oberkante liegt bei y = 0).
            val cutout = layout.cutout
            val holeWidth = with(density) { (cutout?.width ?: 0).toDp() }
            val holeSpan = with(density) { ((cutout?.top ?: 0) + (cutout?.bottom ?: 0)).toDp() }
            max(120.dp, holeWidth + 48.dp) to max(32.dp, holeSpan)
        }
        NotchLayoutMode.EDGE_SIDE -> if (expanded) 280.dp to 200.dp else 20.dp to 120.dp
    }

    val shape = when (layout.mode) {
        NotchLayoutMode.NOTCH_TOP -> RoundedCornerShape(if (expanded) 28.dp else 50.dp)
        NotchLayoutMode.EDGE_SIDE -> {
            // Nur die zur Bildschirmmitte zeigende Seite abrunden.
            val r = if (expanded) 28.dp else 10.dp
            if (layout.side == EdgeSide.RIGHT) {
                RoundedCornerShape(topStart = r, bottomStart = r)
            } else {
                RoundedCornerShape(topEnd = r, bottomEnd = r)
            }
        }
    }

    Box(
        modifier = Modifier
            .animateContentSize()
            .size(width, height)
            .clip(shape)
            .background(Color.Black)
            .clickable {
                expanded = !expanded
                onExpandRequest(expanded)
            },
        contentAlignment = Alignment.Center
    ) {
        if (expanded) Text("DevNotch", color = Color.White)
    }
}
