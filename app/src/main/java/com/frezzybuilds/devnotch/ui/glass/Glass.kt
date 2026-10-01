package com.frezzybuilds.devnotch.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.ui.theme.Brand
import kotlin.math.cos
import kotlin.math.sin

/**
 * „Neon Glass“: Milchglas-Karten auf einem tiefen Nachthimmel mit langsam treibenden Lichtern
 * in den Markenfarben – dieselbe Bildsprache wie das App-Icon.
 */
object Glass {
    val Night = Color(0xFF07060F)
    val NightTop = Color(0xFF140B2E)
    val Fill = Color.White.copy(alpha = 0.055f)
    val Stroke = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f)))
    val Sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent))
    val TextSecondary = Color.White.copy(alpha = 0.62f)
    val Shape = RoundedCornerShape(26.dp)

    /** Material-Schema der App: dunkel, Markenakzente, transparente Flächen über dem Glas. */
    val AppScheme = darkColorScheme(
        primary = Brand.Lilac,
        onPrimary = Color.Black,
        primaryContainer = Brand.Violet,
        onPrimaryContainer = Color.White,
        secondary = Brand.Cyan,
        onSecondary = Color.Black,
        tertiary = Brand.Magenta,
        background = Night,
        onBackground = Color.White,
        // Listenzeilen & Co. liegen auf Glas – ihre eigene Fläche bleibt durchsichtig.
        surface = Color.Transparent,
        onSurface = Color.White,
        onSurfaceVariant = Color.White.copy(alpha = 0.62f),
        surfaceContainerHigh = Color(0xFF1C1830),
        surfaceContainerHighest = Color(0xFF2A2540),
        outline = Color.White.copy(alpha = 0.22f),
        outlineVariant = Color.White.copy(alpha = 0.10f)
    )
}

/**
 * Hintergrund: Nachthimmel mit drei weichen Lichtern (Magenta, Violett, Cyan), die langsam
 * kreisen. Die Phase wird erst beim Zeichnen gelesen – keine Recomposition pro Bild.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val phase: State<Float> = if (LocalInspectionMode.current) {
        remember { mutableFloatStateOf(0.6f) }
    } else {
        rememberInfiniteTransition(label = "aurora").animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing)),
            label = "auroraPhase"
        )
    }
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Glass.NightTop, Glass.Night, Glass.Night)))
            .drawBehind {
                val p = phase.value
                val w = size.width
                val h = size.height
                fun blob(color: Color, cx: Float, cy: Float, radius: Float, alpha: Float) = drawCircle(
                    Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = Offset(cx, cy), radius = radius),
                    radius = radius,
                    center = Offset(cx, cy)
                )
                blob(Brand.Violet, w * (0.25f + 0.12f * cos(p)), h * (0.12f + 0.05f * sin(p)), w * 0.85f, 0.45f)
                blob(Brand.Magenta, w * (0.85f + 0.10f * sin(p * 1.3f)), h * (0.42f + 0.08f * cos(p)), w * 0.70f, 0.28f)
                blob(Brand.Cyan, w * (0.15f + 0.10f * cos(p * 0.8f)), h * (0.78f + 0.06f * sin(p * 1.1f)), w * 0.75f, 0.20f)
            }
    ) {
        // Auf dem Nachthimmel ist Weiß die Standard-Textfarbe (ohne Surface wäre sie Schwarz).
        CompositionLocalProvider(LocalContentColor provides Color.White) { content() }
    }
}

/** Milchglas-Karte: leicht aufgehellte Fläche, Glanz oben, feiner Lichtrand (oder Neon-Rand). */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    neon: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .clip(Glass.Shape)
            .background(Glass.Fill)
            .background(Glass.Sheen)
            .border(if (neon) 1.5.dp else 1.dp, if (neon) Brand.Horizontal else Glass.Stroke, Glass.Shape),
        content = content
    )
}

/** Primäraktion: Pille im Markenverlauf, die beim Drücken nachgibt. */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "btn")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Brand.Horizontal)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 14.dp else 20.dp, vertical = if (compact) 8.dp else 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.Black,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Sekundäraktion: Glas-Pille. */
@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** Kartenkopf: Symbol im Verlaufskreis, Titel und Untertitel. */
@Composable
fun SectionHeader(icon: String, title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brand.Horizontal),
            contentAlignment = Alignment.Center
        ) { Text(icon, color = Color.Black, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Text im Markenverlauf (Überschriften, Werte). */
fun gradientText(base: TextStyle): TextStyle = base.copy(brush = Brand.Horizontal)

/** Innenabstand für Karteninhalt. */
val CardPadding = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)

/** Kleiner, leuchtender Statuspunkt (grün = aktiv). */
@Composable
fun StatusDot(active: Boolean, modifier: Modifier = Modifier) {
    val color = if (active) Brand.Charge else Color.White.copy(alpha = 0.3f)
    Box(
        modifier
            .size(10.dp)
            .drawBehind {
                if (active) drawCircle(color.copy(alpha = 0.35f), radius = size.minDimension)
            }
            .clip(CircleShape)
            .background(color)
    )
}
