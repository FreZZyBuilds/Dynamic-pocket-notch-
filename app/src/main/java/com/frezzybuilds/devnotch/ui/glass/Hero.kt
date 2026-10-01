package com.frezzybuilds.devnotch.ui.glass

import androidx.compose.animation.AnimatedContent
import com.frezzybuilds.devnotch.ui.rememberBeamPosition
import com.frezzybuilds.devnotch.ui.borderBeam
import com.frezzybuilds.devnotch.ui.BeamMode
import com.frezzybuilds.devnotch.appContainer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.peek.Peek
import com.frezzybuilds.devnotch.ui.PeekContent
import com.frezzybuilds.devnotch.ui.theme.Brand
import kotlinx.coroutines.delay

private val PillHeight = 34.dp

/** Was die Vorschau nacheinander zeigt – genau das, was die echte Notch kann. */
private enum class ShowcaseState(val width: Dp, val height: Dp) {
    IDLE(118.dp, PillHeight),
    CHARGING(300.dp, PillHeight + 46.dp),
    MUSIC(300.dp, PillHeight + 46.dp),
    TIMER(200.dp, PillHeight)
}

/**
 * Kopfbereich: angedeutete Handy-Oberkante mit einer Live-Vorschau der Notch, die zwischen
 * Ruhezustand, Lade-Peek, Musik-Peek und Timer wechselt. Darunter Name und Claim.
 */
@Composable
fun HeroHeader(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        NotchShowcase()
        Spacer(Modifier.height(18.dp))
        Text(
            "DevNotch",
            style = gradientText(MaterialTheme.typography.displaySmall),
            fontWeight = FontWeight.Black
        )
        Text(
            "Deine Kamera wird zur Kommandozentrale",
            color = Glass.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NotchShowcase() {
    val inspection = LocalInspectionMode.current
    var index by remember { mutableIntStateOf(if (inspection) ShowcaseState.MUSIC.ordinal else 0) }
    if (!inspection) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(2_600)
                index = (index + 1) % ShowcaseState.entries.size
            }
        }
    }
    val state = ShowcaseState.entries[index]

    // Angedeutetes Display: Glas mit runden oberen Ecken, die Notch hängt oben in der Mitte.
    Box(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp, bottomStart = 26.dp, bottomEnd = 26.dp))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.02f))))
            .border(1.dp, Glass.Stroke, RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp, bottomStart = 26.dp, bottomEnd = 26.dp))
            .semantics { contentDescription = "Vorschau der Notch" },
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            Modifier
                .padding(top = 12.dp)
                .animateContentSize(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
                .size(state.width, state.height)
                .clip(RoundedCornerShape(if (state.height > PillHeight) 28.dp else PillHeight / 2))
                .background(Color.Black)
                .then(if (state == ShowcaseState.TIMER) Modifier.timerGlow() else Modifier.showcaseBeam(state))
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn(tween(260, delayMillis = 160)) togetherWith fadeOut(tween(120)) },
                label = "showcase"
            ) { s ->
                Box(Modifier.fillMaxSize()) {
                    when (s) {
                        ShowcaseState.IDLE -> Unit
                        ShowcaseState.CHARGING -> PeekContent(Peek.Charging(82), PillHeight, lensGap = 26.dp)
                        ShowcaseState.MUSIC -> PeekContent(Peek.TrackChanged("Midnight City", "M83", null), PillHeight, lensGap = 26.dp)
                        ShowcaseState.TIMER -> Row(
                            Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⏱ 24:13", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(26.dp))
                            Text("Fokus", color = Brand.Cyan, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            // Kameralinse – sitzt immer in der Mitte der oberen Zeile.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = (PillHeight - 12.dp) / 2)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF15152A))
                    .border(1.dp, Color(0xFF2E2E52), CircleShape)
            )
        }
    }
}

/** Lichtlauf wie in der echten Notch, in der aktuell gewählten Palette. */
@Composable
private fun Modifier.showcaseBeam(state: ShowcaseState): Modifier {
    val settings = LocalContext.current.appContainer.notchSettings
    val prefs by remember { settings.beamPrefsFlow() }.collectAsStateWithLifecycle(initialValue = settings.beamPrefs)
    val on = prefs.mode != BeamMode.OFF
    val strength = remember { mutableFloatStateOf(1f) }.also { it.floatValue = if (on) 1f else 0f }
    val position = rememberBeamPosition(running = on, lapSeconds = prefs.look.lapSeconds)
    val shape = RoundedCornerShape(if (state.height > PillHeight) 28.dp else PillHeight / 2)
    return borderBeam(shape, position, strength, prefs.look)
}

/** Timer-Zustand: Neon-Linie läuft um die Pille (Vorschau des echten Timer-Rings). */
@Composable
private fun Modifier.timerGlow(): Modifier {
    val sweep: State<Float> = if (LocalInspectionMode.current) remember { mutableFloatStateOf(0.35f) } else
        rememberInfiniteTransition(label = "timerGlow").animateFloat(0f, 1f, infiniteRepeatable(tween(2_400, easing = LinearEasing)), label = "sweep")
    return drawBehind {
        drawRoundRect(
            Brush.sweepGradient(Brand.Colors + Brand.Colors.first(), center = Offset(size.width * sweep.value, 0f)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * Großer Ein/Aus-Knopf: Läuft die Notch, kreist ein Neon-Ring und das Licht pulsiert sanft;
 * sonst ein ruhiger Glasring.
 */
@Composable
fun PowerOrb(active: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val inspection = LocalInspectionMode.current
    val transition = rememberInfiniteTransition(label = "orb")
    val spin: State<Float> = if (inspection || !active) remember { mutableFloatStateOf(0f) } else
        transition.animateFloat(0f, 360f, infiniteRepeatable(tween(3_200, easing = LinearEasing)), label = "spin")
    val glow by animateFloatAsState(if (active) 1f else 0f, tween(500, easing = FastOutSlowInEasing), label = "glow")
    Box(
        modifier
            .size(78.dp)
            .drawBehind {
                if (glow > 0f) {
                    drawCircle(
                        Brush.radialGradient(listOf(Brand.Violet.copy(alpha = 0.55f * glow), Color.Transparent)),
                        radius = size.minDimension * 0.85f
                    )
                }
                val stroke = 3.dp.toPx()
                if (active) {
                    rotate(spin.value) {
                        drawCircle(Brush.sweepGradient(Brand.Colors + Brand.Colors.first()), radius = size.minDimension / 2 - stroke, style = Stroke(stroke))
                    }
                } else {
                    drawCircle(Color.White.copy(alpha = 0.22f), radius = size.minDimension / 2 - stroke, style = Stroke(stroke))
                }
            }
            .padding(9.dp)
            .clip(CircleShape)
            .background(if (active) Brand.Horizontal else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f))))
            .clickable(role = Role.Switch, onClickLabel = if (active) "Notch ausschalten" else "Notch einschalten", onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        PowerGlyph(if (active) Color.Black else Color.White)
    }
}

/** Ein/Aus-Symbol, selbst gezeichnet (das Unicode-Zeichen fehlt in vielen Systemschriften). */
@Composable
private fun PowerGlyph(color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
        val stroke = 2.6.dp.toPx()
        val inset = stroke
        drawArc(
            color = color,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(inset, inset + size.height * 0.06f),
            size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
            style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        drawLine(
            color,
            start = Offset(size.width / 2, size.height * 0.02f),
            end = Offset(size.width / 2, size.height * 0.48f),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

/** Ein Schritt der Einrichtung. */
data class SetupStep(
    val title: String,
    val done: Boolean,
    val doneText: String,
    val todoText: String,
    val action: String,
    val onAction: () -> Unit
)

/** Einrichtungs-Checkliste mit Fortschrittsbalken; erledigte Schritte bekommen ein Häkchen. */
@Composable
fun SetupChecklist(steps: List<SetupStep>, modifier: Modifier = Modifier) {
    val done = steps.count { it.done }
    val progress by animateFloatAsState(done.toFloat() / steps.size, tween(600, easing = FastOutSlowInEasing), label = "setupProgress")
    GlassCard(modifier.fillMaxWidth(), neon = done < steps.size) {
        Column(CardPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeader(
                icon = if (done == steps.size) "✓" else "${done}/${steps.size}",
                title = if (done == steps.size) "Alles eingerichtet" else "Einrichtung",
                subtitle = if (done == steps.size) "DevNotch hat alles, was es braucht." else "$done von ${steps.size} erledigt"
            )
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(Brand.Horizontal))
            }
            steps.forEachIndexed { i, step -> StepRow(i + 1, step) }
        }
    }
}

@Composable
private fun StepRow(number: Int, step: SetupStep) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(
            targetState = step.done,
            transitionSpec = { scaleIn(spring(dampingRatio = 0.45f)) + fadeIn() togetherWith fadeOut(tween(100)) },
            label = "stepCheck"
        ) { done ->
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .then(
                        if (done) Modifier.background(Brand.Horizontal)
                        else Modifier.border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (done) "✓" else "$number",
                    color = if (done) Color.Black else Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(step.title, color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(if (step.done) step.doneText else step.todoText, color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        if (!step.done) {
            Spacer(Modifier.width(8.dp))
            GradientButton(step.action, onClick = step.onAction, compact = true)
        }
    }
}
