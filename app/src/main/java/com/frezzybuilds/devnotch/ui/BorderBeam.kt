package com.frezzybuilds.devnotch.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.ui.theme.Brand
import kotlin.math.pow

/** Wann der Lichtlauf um die Notch kreist. */
enum class BeamMode(val label: String) {
    ALWAYS("Immer"),
    EVENTS("Ereignisse"),
    OFF("Aus")
}

/** Farbverlauf des Lichts: vom Schweif zur Spitze. */
enum class BeamPalette(val label: String, val colors: List<Color>) {
    NEON("Neon", listOf(Brand.Magenta, Brand.Violet, Brand.Cyan)),
    SUNSET("Sunset", listOf(Color(0xFFFF4FA3), Color(0xFF9B6BFF), Color(0xFFFFB547)))
}

/** Eine Runde in ms – ruhig genug, dass es edel statt hektisch wirkt. */
const val BEAM_LAP_MS = 2_600

/** Länge des Lichts als Anteil am Umfang. */
private const val BEAM_LENGTH = 0.26f

/** Der Schweif wird in Stücke geteilt, die zur Spitze hin heller und farbiger werden. */
private const val BEAM_SLICES = 20

/** Position (0…1) des Lichts; ohne [running] steht es still (kein Neuzeichnen). */
@Composable
fun rememberBeamPosition(running: Boolean): State<Float> {
    if (!running || LocalInspectionMode.current) return remember { mutableFloatStateOf(0.18f) }
    return rememberInfiniteTransition(label = "beam").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BEAM_LAP_MS, easing = LinearEasing)),
        label = "beamPosition"
    )
}

/**
 * „Border Beam“: Ein Lichtsegment mit weichem Schweif läuft am Rand von [shape] entlang,
 * dahinter eine kaum sichtbare Spur. Pfad und Messung werden nur bei Größenänderung neu
 * berechnet; [position] und [strength] liest erst die Zeichenphase – keine Recomposition.
 */
fun Modifier.borderBeam(
    shape: Shape,
    position: State<Float>,
    strength: State<Float>,
    colors: List<Color>
): Modifier = drawWithCache {
    val core = 2.2.dp.toPx()
    val glow = 9.dp.toPx()
    // Pfad leicht nach innen versetzt: Die Form ist meist geclippt, sonst fiele die äußere
    // Hälfte der Linie weg und sie wirkte dünn.
    val inset = core / 2 + 0.5.dp.toPx()
    val inner = Size((size.width - 2 * inset).coerceAtLeast(0f), (size.height - 2 * inset).coerceAtLeast(0f))
    val path = Path().apply {
        addOutline(shape.createOutline(inner, layoutDirection, this@drawWithCache))
        translate(Offset(inset, inset))
    }
    val measure = PathMeasure().apply { setPath(path, true) }
    val length = measure.length
    val slice = Path()
    val track = Stroke(width = 1.dp.toPx())
    val headRadius = 9.dp.toPx()

    fun colorAt(t: Float): Color {
        val scaled = t * (colors.size - 1)
        val i = scaled.toInt().coerceAtMost(colors.size - 2)
        return lerp(colors[i], colors[i + 1], scaled - i)
    }

    onDrawWithContent {
        drawContent()
        val s = strength.value
        if (s <= 0f || length <= 0f) return@onDrawWithContent
        drawPath(path, Color.White.copy(alpha = 0.07f * s), style = track)

        val beam = length * BEAM_LENGTH
        val head = position.value * length
        val step = beam / BEAM_SLICES
        for (k in 0 until BEAM_SLICES) {
            val t = (k + 1f) / BEAM_SLICES // 0 = Schweifende, 1 = Spitze
            var start = head - beam + k * step
            if (start < 0f) start += length
            val end = start + step + 0.5f // minimal überlappen, keine Lücken
            slice.reset()
            if (end <= length) {
                measure.getSegment(start, end, slice, true)
            } else {
                measure.getSegment(start, length, slice, true)
                measure.getSegment(0f, end - length, slice, true)
            }
            val color = colorAt(t)
            val alpha = t.pow(1.6f) * s
            // Butt-Enden: runde Kappen überlappen sich sonst als sichtbare „Perlen“.
            drawPath(slice, color.copy(alpha = 0.28f * alpha), style = Stroke(glow, cap = StrokeCap.Butt))
            drawPath(slice, color.copy(alpha = alpha), style = Stroke(core, cap = StrokeCap.Butt))
        }
        // Heller Lichtpunkt an der Spitze.
        val tip = measure.getPosition(head.coerceIn(0f, length))
        val tipColor = colors.last()
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f * s), tipColor.copy(alpha = 0.45f * s), Color.Transparent), center = tip, radius = headRadius),
            radius = headRadius,
            center = tip
        )
    }
}
