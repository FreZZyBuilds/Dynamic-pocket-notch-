package com.frezzybuilds.devnotch.ui

import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frezzybuilds.devnotch.system.Sky
import com.frezzybuilds.devnotch.system.WeatherRepo
import com.frezzybuilds.devnotch.ui.focus.FocusTimerViewModel
import com.frezzybuilds.devnotch.ui.focus.formatMmSs
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin

private val TimerOrange = Color(0xFFFF9F0A)

/** Rad-Maßstab: etwa acht Stunden auf einer Karte, Striche alle 5 Minuten, Rasten auf 15 Minuten. */
private const val PER_MINUTE_DP = 0.75f
private const val TICK_MIN = 5
private const val STEP_MIN = 15

// --- Timer mit Drehrad -------------------------------------------------------------------------

/** Minuten seit Mitternacht für eine Uhrzeit (ms). */
private fun minuteOfDay(ms: Long): Int = Calendar.getInstance().apply { timeInMillis = ms }.let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

/** „17:05“ für Minuten seit Mitternacht (auch über Mitternacht hinaus). */
fun clockLabel(minuteOfDay: Int): String = "%02d:%02d".format((minuteOfDay / 60) % 24, minuteOfDay % 60)

/** Endzeit auf 5 Minuten gerastert → Dauer in Minuten ab [nowMinute] (mindestens 1). */
fun timerDurationTo(endMinute: Int, nowMinute: Int): Int = (endMinute - nowMinute).coerceAtLeast(1)

@Composable
internal fun TimerPage(focusTimer: FocusTimerViewModel) {
    val remaining by focusTimer.remainingTime.collectAsStateWithLifecycle()
    val total by focusTimer.totalTime.collectAsStateWithLifecycle()
    val running by focusTimer.isRunning.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val nowMinute = remember { minuteOfDay(System.currentTimeMillis()) }
    // Wählbare Endzeit (Minuten seit Mitternacht, auch > 24 h für „morgen früh“), Start: in einer Stunde.
    var end by remember { mutableFloatStateOf(((ceil((nowMinute + 60) / 15.0) * 15)).toFloat()) }
    // Rastet wie im Video auf Viertelstunden.
    val snapped = ((end / STEP_MIN).roundToInt() * STEP_MIN).coerceIn(nowMinute + 5, nowMinute + 12 * 60)
    var lastTick by remember { mutableIntStateOf(snapped) }
    if (snapped != lastTick) {
        lastTick = snapped
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val active = running || remaining != total

    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        if (active) {
            Text("Timer läuft", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelLarge)
            Text(formatMmSs(remaining), color = TimerOrange, fontSize = 52.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimerButton(if (running) "Pause" else "Weiter", filled = true, Modifier.weight(1f)) { focusTimer.toggleTimer() }
                TimerButton("Stopp", filled = false, Modifier.weight(1f)) { focusTimer.stopTimer() }
            }
        } else {
            TimerRuler(
                endMinute = end,
                nowMinute = nowMinute,
                onDrag = { delta -> end = (end - delta).coerceIn((nowMinute + 5).toFloat(), (nowMinute + 12 * 60).toFloat()) },
                onDragEnd = { end = snapped.toFloat() },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimerButton("Timer starten", filled = false, Modifier) {
                    focusTimer.resetTimer(timerDurationTo(snapped, nowMinute))
                    focusTimer.startTimer()
                }
                Spacer(Modifier.weight(1f))
                Text(clockLabel(snapped), color = TimerOrange, fontSize = 40.sp, fontWeight = FontWeight.Light)
            }
        }
    }
}

@Composable
private fun TimerButton(label: String, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(40.dp)
            .pressScale(interaction)
            .clip(CircleShape)
            .background(if (filled) TimerOrange else TimerOrange.copy(alpha = 0.18f))
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (filled) Color.Black else TimerOrange, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Lineal wie ein Drehrad: Striche alle 5 Minuten, Stundenzahlen darüber, die Mitte (Dreieck)
 * ist die Endzeit. Wischen dreht das Rad, beim Loslassen rastet es auf Viertelstunden.
 */
@Composable
private fun TimerRuler(endMinute: Float, nowMinute: Int, onDrag: (Float) -> Unit, onDragEnd: () -> Unit, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
    Canvas(
        modifier.pointerInput(Unit) {
            val perMinute = PER_MINUTE_DP.dp.toPx()
            detectHorizontalDragGestures(onDragEnd = onDragEnd) { change, dx ->
                change.consume()
                onDrag(dx / perMinute)
            }
        }
    ) {
        val perMinute = PER_MINUTE_DP.dp.toPx()
        val center = size.width / 2
        val baseline = size.height * 0.78f
        val visible = (size.width / perMinute / 2).toInt() + 10
        val first = ((endMinute.toInt() - visible) / TICK_MIN) * TICK_MIN
        var m = first
        while (m <= endMinute + visible) {
            val x = center + (m - endMinute) * perMinute
            if (x in -10f..size.width + 10f) {
                val hour = m % 60 == 0
                val fade = 1f - ((x - center) / center).let { it * it }.coerceIn(0f, 1f) * 0.85f
                val past = m < nowMinute
                val tickH = if (hour) size.height * 0.42f else if (m % 30 == 0) size.height * 0.32f else size.height * 0.24f
                drawLine(
                    color = (if (past) Color.White.copy(alpha = 0.2f) else TimerOrange).copy(alpha = fade * if (hour) 1f else 0.75f),
                    start = Offset(x, baseline - tickH),
                    end = Offset(x, baseline),
                    strokeWidth = if (hour) 2.5.dp.toPx() else 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                if (hour) {
                    val text = "${(m / 60) % 24}"
                    val layout = measurer.measure(text, labelStyle)
                    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, baseline - tickH - layout.size.height - 2.dp.toPx()), alpha = fade)
                }
            }
            m += TICK_MIN
        }
        // Markierung in der Mitte.
        val tri = Path().apply {
            moveTo(center, baseline + 3.dp.toPx())
            lineTo(center - 6.dp.toPx(), baseline + 12.dp.toPx())
            lineTo(center + 6.dp.toPx(), baseline + 12.dp.toPx())
            close()
        }
        drawPath(tri, TimerOrange)
        drawLine(Color.White, Offset(center, baseline - size.height * 0.46f), Offset(center, baseline), 2.dp.toPx(), StrokeCap.Round)
    }
}

// --- Wetter mit animierter Szene ---------------------------------------------------------------

@Composable
internal fun WeatherPage(active: Boolean) {
    val context = LocalContext.current
    val state by WeatherRepo.state.collectAsStateWithLifecycle()
    if (!LocalInspectionMode.current) {
        LaunchedEffect(active) { if (active) WeatherRepo.refresh(context) }
    }
    val ready = (state as? WeatherRepo.State.Ready)?.weather
    val sky = ready?.sky ?: Sky.PARTLY
    val day = ready?.isDay ?: true
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp))) {
        WeatherScene(sky, day, animate = active && !LocalInspectionMode.current, modifier = Modifier.fillMaxSize())
        Column(Modifier.padding(16.dp)) {
            when (val s = state) {
                is WeatherRepo.State.Ready -> {
                    Text("${s.weather.temperature}°", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
                    Text(s.weather.description, color = Color.White, style = MaterialTheme.typography.labelLarge)
                    s.weather.place?.let { Text(it, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall) }
                }
                WeatherRepo.State.Loading -> Text("Wetter lädt …", color = Color.White, style = MaterialTheme.typography.labelLarge)
                is WeatherRepo.State.Failed -> Text(s.message, color = Color.White, style = MaterialTheme.typography.labelLarge)
                WeatherRepo.State.NeedsPermission -> {
                    Text("Wetter", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Standort in DevNotch erlauben ›",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.25f)).clickable(role = Role.Button) {
                            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                                runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        }.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Animierte Wetterszene (wie im Video): Himmel, Sonne mit drehenden Strahlen oder Mond mit Sternen,
 * ziehende Wolken, Regen/Schnee/Gewitter, Wiese und ein kleiner Spaziergänger (bei Regen mit
 * Schirm). Eine einzige Zeitachse, gelesen nur beim Zeichnen.
 */
@Composable
fun WeatherScene(sky: Sky, day: Boolean, animate: Boolean, modifier: Modifier = Modifier) {
    val time: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "weather").animateFloat(0f, 1f, infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    } else {
        remember { mutableFloatStateOf(0.3f) }
    }
    Canvas(modifier) {
        val t = time.value
        val seconds = t * 60f
        val h = size.height
        val w = size.width
        val ground = h * 0.82f
        // Himmel.
        val skyColors = when {
            !day -> listOf(Color(0xFF0B1026), Color(0xFF243266))
            sky == Sky.STORM -> listOf(Color(0xFF3A4150), Color(0xFF5B6475))
            sky == Sky.RAIN || sky == Sky.FOG -> listOf(Color(0xFF6E7F93), Color(0xFF9AA8B8))
            sky == Sky.CLOUDY || sky == Sky.SNOW -> listOf(Color(0xFF7FA3C8), Color(0xFFB7CBE0))
            else -> listOf(Color(0xFF3F8FE0), Color(0xFF9CCBF2))
        }
        drawRect(Brush.verticalGradient(skyColors, endY = ground))
        if (!day) drawStars(seconds, w, ground)
        // Sonne oder Mond.
        val sunCenter = Offset(w * 0.78f, h * 0.30f)
        val r = h * 0.12f
        if (day && sky != Sky.STORM && sky != Sky.RAIN && sky != Sky.FOG) {
            drawCircle(Color(0xFFFFE27A).copy(alpha = 0.25f + 0.1f * sin(seconds * 1.5f)), r * 1.7f, sunCenter)
            rotate(seconds * 12f, sunCenter) {
                repeat(12) { i ->
                    val a = i * (2 * PI / 12).toFloat()
                    val dir = Offset(kotlin.math.cos(a), sin(a))
                    drawLine(Color(0xFFFFD54F), sunCenter + dir * (r * 1.25f), sunCenter + dir * (r * 1.6f), r * 0.16f, StrokeCap.Round)
                }
            }
            drawCircle(Color(0xFFFFC928), r, sunCenter)
        } else if (!day) {
            drawCircle(Color(0xFFF2F0E6), r, sunCenter)
            drawCircle(Color(0xFF1A2550), r * 0.85f, sunCenter + Offset(r * 0.45f, -r * 0.2f))
        }
        // Wolken ziehen langsam von links nach rechts.
        val clouds = when (sky) {
            Sky.CLEAR -> 1
            Sky.PARTLY -> 2
            else -> 4
        }
        val cloudColor = when {
            sky == Sky.STORM -> Color(0xFF8A919E)
            sky == Sky.RAIN || sky == Sky.FOG -> Color(0xFFD6DCE3)
            !day -> Color(0xFF8E97B5)
            else -> Color.White
        }
        repeat(clouds) { i ->
            val span = w + r * 6
            val x = ((i * 0.37f + seconds / (40f + i * 9f)) % 1f) * span - r * 3
            val y = h * (0.22f + 0.12f * (i % 3))
            drawCloud(Offset(x, y), r * (1.0f + 0.25f * (i % 2)), cloudColor)
        }
        if (sky == Sky.FOG) repeat(3) { i -> drawRect(Color.White.copy(alpha = 0.18f), Offset(0f, h * (0.45f + i * 0.1f)), Size(w, h * 0.05f)) }
        // Wiese.
        drawRect(if (day) Color(0xFF5DBB4A) else Color(0xFF1F3D2A), Offset(0f, ground), Size(w, h - ground))
        drawRect(if (day) Color(0xFF7BD06A) else Color(0xFF2E5A3E), Offset(0f, ground), Size(w, h * 0.02f))
        // Niederschlag.
        if (sky == Sky.RAIN || sky == Sky.STORM) drawRain(seconds, w, ground)
        if (sky == Sky.SNOW) drawSnow(seconds, w, ground)
        if (sky == Sky.STORM && (seconds % 7f) in 0f..0.15f) drawRect(Color.White.copy(alpha = 0.5f))
        // Spaziergänger: läuft in 14 s über die Wiese.
        val walk = (seconds % 14f) / 14f
        drawWalker(Offset(-20f + walk * (w + 40f), ground), h * 0.2f, seconds, umbrella = sky == Sky.RAIN || sky == Sky.STORM)
    }
}

private fun DrawScope.drawCloud(at: Offset, r: Float, color: Color) {
    drawCircle(color, r * 0.75f, at)
    drawCircle(color, r, at + Offset(r * 0.8f, -r * 0.35f))
    drawCircle(color, r * 0.8f, at + Offset(r * 1.7f, 0f))
    drawRect(color, Offset(at.x, at.y), Size(r * 1.7f, r * 0.75f))
}

private fun DrawScope.drawStars(seconds: Float, w: Float, ground: Float) {
    repeat(18) { i ->
        val x = (i * 97 % 100) / 100f * w
        val y = (i * 53 % 100) / 100f * ground * 0.7f
        val twinkle = 0.4f + 0.6f * ((sin(seconds * 2 + i) + 1) / 2)
        drawCircle(Color.White.copy(alpha = twinkle), 1.5f + (i % 3), Offset(x, y))
    }
}

private fun DrawScope.drawRain(seconds: Float, w: Float, ground: Float) {
    repeat(36) { i ->
        val x = (i * 61 % 100) / 100f * w
        val y = ((i * 0.13f + seconds * 1.4f) % 1f) * ground
        drawLine(Color.White.copy(alpha = 0.6f), Offset(x, y), Offset(x - 3f, y + 14f), 2f, StrokeCap.Round)
    }
}

private fun DrawScope.drawSnow(seconds: Float, w: Float, ground: Float) {
    repeat(28) { i ->
        val y = ((i * 0.17f + seconds * 0.25f) % 1f) * ground
        val x = (i * 71 % 100) / 100f * w + sin(seconds * 1.5f + i) * 8f
        drawCircle(Color.White.copy(alpha = 0.9f), 2.5f + (i % 2), Offset(x, y))
    }
}

/** Kleine Figur mit schwingenden Beinen und Armen (roter Pulli), optional mit Schirm. */
private fun DrawScope.drawWalker(feet: Offset, height: Float, seconds: Float, umbrella: Boolean) {
    val swing = sin(seconds * 8f) * 0.45f
    val hip = feet + Offset(0f, -height * 0.45f)
    val neck = hip + Offset(0f, -height * 0.35f)
    val leg = height * 0.45f
    val stroke = height * 0.09f
    val skin = Color(0xFFF1C7A0)
    // Beine.
    drawLine(Color(0xFF2B3A67), hip, hip + Offset(sin(swing) * leg, kotlin.math.cos(swing) * leg), stroke, StrokeCap.Round)
    drawLine(Color(0xFF2B3A67), hip, hip + Offset(-sin(swing) * leg, kotlin.math.cos(swing) * leg), stroke, StrokeCap.Round)
    // Körper.
    drawLine(Color(0xFFE53935), hip, neck, stroke * 1.6f, StrokeCap.Round)
    // Arme (gegengleich).
    val arm = height * 0.3f
    drawLine(skin, neck + Offset(0f, height * 0.04f), neck + Offset(-sin(swing) * arm, kotlin.math.cos(swing) * arm), stroke * 0.8f, StrokeCap.Round)
    // Kopf mit Kappe.
    val head = neck + Offset(0f, -height * 0.12f)
    drawCircle(skin, height * 0.11f, head)
    drawRect(Color(0xFFE53935), head + Offset(-height * 0.11f, -height * 0.12f), Size(height * 0.24f, height * 0.06f))
    if (umbrella) {
        val top = head + Offset(0f, -height * 0.32f)
        drawLine(Color(0xFF333333), head + Offset(height * 0.08f, 0f), top + Offset(height * 0.08f, 0f), stroke * 0.5f)
        drawArc(Color(0xFF7E57C2), 180f, 180f, true, top + Offset(-height * 0.22f, -height * 0.15f), Size(height * 0.6f, height * 0.3f))
    }
}
