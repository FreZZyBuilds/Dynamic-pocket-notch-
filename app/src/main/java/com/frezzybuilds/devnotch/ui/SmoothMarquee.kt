package com.frezzybuilds.devnotch.ui

import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lauftext ohne Stocken: `basicMarquee` hält nach jeder Runde standardmäßig 1,2 s an – das wirkte
 * wie ein Ruckler. Hier läuft der Text nach einer kurzen Startpause gleichmäßig im Kreis, mit
 * Abstand zwischen Ende und Anfang. Gescrollt wird nur, wenn der Text nicht passt.
 */
fun Modifier.smoothMarquee(initialDelayMillis: Int = 900, velocity: Dp = 40.dp): Modifier =
    basicMarquee(
        iterations = Int.MAX_VALUE,
        repeatDelayMillis = 0,
        initialDelayMillis = initialDelayMillis,
        spacing = MarqueeSpacing(40.dp),
        velocity = velocity
    )
