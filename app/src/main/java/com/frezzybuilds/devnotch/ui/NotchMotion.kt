package com.frezzybuilds.devnotch.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Erst wächst die Form, dann erscheint der Inhalt – zeilenweise, leicht von unten. */
private const val STAGGER_START_MS = 70L
private const val STAGGER_STEP_MS = 45L
private const val STAGGER_DURATION_MS = 260

/**
 * Gestaffeltes Einblenden beim Aufklappen: Element [index] startet [index] × 45 ms später.
 * Die Werte werden erst in der Layer-Phase gelesen – keine Recomposition pro Bild.
 */
@Composable
fun Modifier.staggerIn(index: Int): Modifier {
    if (LocalInspectionMode.current) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(STAGGER_START_MS + index * STAGGER_STEP_MS)
        progress.animateTo(1f, tween(STAGGER_DURATION_MS, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 10.dp.toPx()
    }
}
