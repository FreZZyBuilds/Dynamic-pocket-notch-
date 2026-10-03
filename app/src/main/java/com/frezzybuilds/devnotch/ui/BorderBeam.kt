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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
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

/** Wie das Licht läuft. */
enum class BeamStyle(val label: String) {
    /** Ein Lichtsegment mit Schweif. */
    BEAM("Strahl"),

    /** Der ganze Rand leuchtet, die Farben fließen rundherum (wie Gemini). */
    RING("Gemini-Ring"),

    /** Zwei Strahlen, die sich gegenüber jagen. */
    DUAL("Doppelt")
}

/** Farbverläufe – mehrere kräftige Farben, damit das Licht bunt statt einfarbig wirkt. */
enum class BeamPalette(val label: String, val colors: List<Color>) {
    GEMINI("Gemini", listOf(Color(0xFF4285F4), Color(0xFF9B72F2), Color(0xFFEA4335), Color(0xFFFBBC04), Color(0xFF34A853))),
    NEON("Neon", listOf(Brand.Magenta, Brand.Violet, Brand.Cyan)),
    SUNSET("Sunset", listOf(Color(0xFFFF4FA3), Color(0xFF9B6BFF), Color(0xFFFFB547))),
    AURORA("Aurora", listOf(Color(0xFF00E5A0), Color(0xFF18FFFF), Color(0xFF7C4DFF), Color(0xFF00E5A0))),
    OCEAN("Ocean", listOf(Color(0xFF0061FF), Color(0xFF00C6FF), Color(0xFF6DFFE5))),
    FIRE("Fire", listOf(Color(0xFFFF1744), Color(0xFFFF6D00), Color(0xFFFFD600))),
    CANDY("Candy", listOf(Color(0xFFFF80AB), Color(0xFFB388FF), Color(0xFF84FFFF), Color(0xFFCCFF90))),
    RAINBOW("Rainbow", listOf(Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFEA00), Color(0xFF00E676), Color(0xFF2979FF), Color(0xFFD500F9))),
    ICE("Ice", listOf(Color(0xFFFFFFFF), Color(0xFF80D8FF), Color(0xFF40C4FF))),
    GOLD("Gold", listOf(Color(0xFFFFF8E1), Color(0xFFFFC400), Color(0xFFFF8F00)))
}

/** Aussehen des Lichtlaufs (Werte aus den Einstellungen). */
data class BeamLook(
    val style: BeamStyle = BeamStyle.BEAM,
    val palette: BeamPalette = BeamPalette.GEMINI,
    /** Sekunden pro Runde. */
    val lapSeconds: Float = DEFAULT_LAP_SECONDS,
    /** 0,4 … 1,6 – Deckkraft und Glow. */
    val brightness: Float = DEFAULT_BRIGHTNESS,
    /** Anteil des Umfangs, den ein Strahl einnimmt. */
    val length: Float = DEFAULT_LENGTH
) {
    companion object {
        const val DEFAULT_LAP_SECONDS = 4.5f
        const val DEFAULT_BRIGHTNESS = 1.25f
        const val DEFAULT_LENGTH = 0.4f
        val LAP_RANGE = 1.5f..9f
        val BRIGHTNESS_RANGE = 0.4f..1.6f
        val LENGTH_RANGE = 0.15f..0.7f
    }
}

/** Position (0…1) des Lichts; ohne [running] steht es still (kein Neuzeichnen). */
@Composable
fun rememberBeamPosition(running: Boolean, lapSeconds: Float = BeamLook.DEFAULT_LAP_SECONDS): State<Float> {
    if (!running || LocalInspectionMode.current) return remember { mutableFloatStateOf(0.18f) }
    return rememberInfiniteTransition(label = "beam").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween((lapSeconds * 1000).toInt(), easing = LinearEasing)),
        label = "beamPosition"
    )
}

/**
 * Lichtlauf am Rand von [shape] („Border Beam“ bzw. Gemini-Ring). Pfad und Messung entstehen
 * nur bei Größen-/Formänderung; [position] und [strength] liest erst die Zeichenphase –
 * keine Recomposition pro Bild.
 */
fun Modifier.borderBeam(
    shape: Shape,
    position: State<Float>,
    strength: State<Float>,
    look: BeamLook
): Modifier = drawWithCache {
    val core = 2.4.dp.toPx()
    val glow = 10.dp.toPx()
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
    // Einmal je Größe statt zweimal pro Teilstück und Bild (früher ~56 Objekte pro Bild).
    val glowStroke = Stroke(glow, cap = StrokeCap.Butt)
    val coreStroke = Stroke(core, cap = StrokeCap.Butt)
    val headRadius = 9.dp.toPx()
    val colors = look.palette.colors
    val brightness = look.brightness

    /** Farbe an Stelle [t] (0…1) des Verlaufs, zyklisch. */
    fun colorAt(t: Float): Color {
        val wrapped = ((t % 1f) + 1f) % 1f
        val scaled = wrapped * colors.size
        val i = scaled.toInt() % colors.size
        return lerp(colors[i], colors[(i + 1) % colors.size], scaled - scaled.toInt())
    }

    fun DrawScope.segment(start: Float, span: Float, color: Color, alpha: Float) {
        var from = start % length
        if (from < 0f) from += length
        // Leicht überlappend (keine Fugen). Deckend statt transparent – zu Schwarz abgedunkelt –
        // und „Lighten“: Überlappungen werden nicht doppelt hell (keine Striche) und der
        // Neon-Rand darunter wird nie verdunkelt.
        val to = from + span + 1f
        slice.reset()
        if (to <= length) {
            measure.getSegment(from, to, slice, true)
        } else {
            measure.getSegment(from, length, slice, true)
            measure.getSegment(0f, to - length, slice, true)
        }
        // Butt-Enden: runde Kappen überlappen sich sonst als sichtbare „Perlen“.
        drawPath(slice, lerp(Color.Black, color, (0.32f * alpha).coerceIn(0f, 1f)), style = glowStroke, blendMode = BlendMode.Lighten)
        drawPath(slice, lerp(Color.Black, color, alpha.coerceIn(0f, 1f)), style = coreStroke, blendMode = BlendMode.Lighten)
    }

    fun DrawScope.beam(head: Float, s: Float) {
        val beam = length * look.length
        val slices = 28
        val step = beam / slices
        for (k in 0 until slices) {
            val t = (k + 1f) / slices // 0 = Schweifende, 1 = Spitze
            // Über die ganze Länge kräftig – nur das Schweifende läuft weich aus. Jede Stelle
            // bekommt ihre eigene Farbe aus der Palette: bunt statt einfarbig.
            val alpha = (0.15f + 0.85f * t.pow(0.55f)) * brightness * s
            segment(head - beam + k * step, step, colorAt(t * 0.999f), alpha)
        }
        val tip = measure.getPosition(((head % length) + length) % length)
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = (0.85f * s * brightness).coerceAtMost(1f)), colors.last().copy(alpha = 0.45f * s), Color.Transparent),
                center = tip,
                radius = headRadius
            ),
            radius = headRadius,
            center = tip
        )
    }

    onDrawWithContent {
        drawContent()
        val s = strength.value
        if (s <= 0f || length <= 0f) return@onDrawWithContent
        drawPath(path, Color.White.copy(alpha = 0.07f * s), style = track)
        val p = position.value
        when (look.style) {
            BeamStyle.BEAM -> beam(p * length, s)
            BeamStyle.DUAL -> {
                beam(p * length, s)
                beam((p + 0.5f) * length, s)
            }
            BeamStyle.RING -> {
                // Ganzer Rand, Farben wandern mit der Position um die Form.
                val slices = 56
                val step = length / slices
                for (k in 0 until slices) {
                    val t = k / slices.toFloat()
                    segment(k * step, step, colorAt(t - p), 0.9f * brightness * s)
                }
            }
        }
    }
}
