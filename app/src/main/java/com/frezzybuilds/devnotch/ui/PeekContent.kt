package com.frezzybuilds.devnotch.ui

import androidx.compose.animation.core.Animatable
import com.frezzybuilds.devnotch.notify.NotchNotification
import com.frezzybuilds.devnotch.notify.LiveActivity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import android.app.PendingIntent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.peek.Peek
import com.frezzybuilds.devnotch.ui.theme.Brand

/**
 * Inhalt der nach unten gewachsenen Pille: oben die Zeile mit der Kameralinse (links Symbol,
 * rechts Wert – die Linse bleibt frei), darunter Titel und Untertitel.
 */
@Composable
fun PeekContent(
    peek: Peek,
    pillHeight: Dp,
    lensGap: Dp,
    modifier: Modifier = Modifier,
    /** Benachrichtigung/Anruf: Intent auslösen (Öffnen, Aktion, Annehmen …). */
    onSend: (PendingIntent?) -> Unit = {},
    /** Benachrichtigung schließen. */
    onDismiss: () -> Unit = {}
) {
    val spec = peekSpec(peek)
    Box(modifier.fillMaxSize().then(if (peek is Peek.Charging) Modifier.chargeSweep() else Modifier)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().height(pillHeight), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { spec.leading() }
                Spacer(Modifier.width(lensGap))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { spec.trailing() }
            }
            Text(
                spec.title,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE)
            )
            spec.subtitle?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = if (peek is Peek.Notification) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when (peek) {
                is Peek.Notification -> NotificationActions(peek.notification, onSend, onDismiss)
                is Peek.LiveCall -> CallButtons(peek.call, onSend)
                else -> Unit
            }
            if (peek is Peek.Charging && peek.percent != null) {
                // Akkustand als Leiste im Grün-Verlauf.
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(peek.percent / 100f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF00C853), Brand.Charge)))
                    )
                }
            }
        }
    }
}

private class PeekSpec(
    val title: String,
    val subtitle: String?,
    val leading: @Composable () -> Unit,
    val trailing: @Composable () -> Unit
)

@Composable
private fun peekSpec(peek: Peek): PeekSpec = when (peek) {
    is Peek.Charging -> PeekSpec(
        title = "Wird geladen",
        subtitle = null,
        leading = { Badge("⚡", Brush.linearGradient(listOf(Brand.Charge, Color(0xFF00C853)))) },
        trailing = {
            Text(
                peek.percent?.let { "$it %" } ?: "",
                color = Brand.Charge,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    )
    is Peek.Copied -> PeekSpec(
        title = peek.preview,
        subtitle = "Im Verlauf gespeichert",
        leading = { Badge("✓", Brush.linearGradient(listOf(Brand.Cyan, Brand.Violet))) },
        trailing = { Text("Kopiert", color = Brand.Cyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
    )
    Peek.TimerDone -> PeekSpec(
        title = "Fokuszeit vorbei",
        subtitle = "Zeit für eine kurze Pause",
        leading = { Pulsing { Badge("⏱", Brand.Horizontal) } },
        trailing = { Text("00:00", color = Brand.Magenta, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
    )
    is Peek.Notification -> PeekSpec(
        title = peek.notification.title,
        subtitle = peek.notification.text,
        leading = {
            val icon = peek.notification.icon
            if (icon != null) {
                val image = remember(icon) { icon.asImageBitmap() }
                Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(26.dp).clip(CircleShape))
            } else {
                Badge(peek.notification.appLabel.take(1).uppercase(), Brand.Horizontal)
            }
        },
        trailing = {
            Text(
                peek.notification.appLabel,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
    is Peek.LiveCall -> PeekSpec(
        title = peek.call.caller,
        subtitle = "Eingehender Anruf",
        leading = {
            val avatar = peek.call.avatar
            if (avatar != null) {
                val image = remember(avatar) { avatar.asImageBitmap() }
                Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(26.dp).clip(CircleShape))
            } else {
                Pulsing { Badge("📞", Brush.linearGradient(listOf(Brand.Charge, Color(0xFF00C853)))) }
            }
        },
        trailing = { Pulsing { Text("● ● ●", color = Brand.Charge, style = MaterialTheme.typography.labelSmall) } }
    )
    is Peek.TrackChanged -> PeekSpec(
        title = peek.title,
        subtitle = peek.artist,
        leading = {
            val art = peek.artwork
            if (art != null) {
                val image = remember(art) { art.asImageBitmap() }
                Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)))
            } else {
                Badge("♪", Brand.Horizontal)
            }
        },
        trailing = { MiniEqualizer() }
    )
}

@Composable
private fun Badge(symbol: String, brush: Brush) {
    Box(Modifier.size(26.dp).clip(CircleShape).background(brush), contentAlignment = Alignment.Center) {
        Text(symbol, color = Color.Black, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

/** Sanftes Pulsieren (Skalierung in der Layer-Phase, ohne Recomposition). */
@Composable
private fun Pulsing(content: @Composable () -> Unit) {
    if (LocalInspectionMode.current) {
        content()
        return
    }
    val scale = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale"
    )
    Box(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }) { content() }
}

/** Drei Balken im Markenverlauf, skaliert in der Layer-Phase. */
@Composable
private fun MiniEqualizer() {
    val inspection = LocalInspectionMode.current
    val transition = rememberInfiniteTransition(label = "peekEq")
    val bars: List<State<Float>?> = listOf(380, 520, 300).map { duration ->
        if (inspection) null else transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(duration), RepeatMode.Reverse), label = "eq$duration")
    }
    Row(Modifier.height(16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEachIndexed { i, bar ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleY = bar?.value ?: listOf(0.6f, 1f, 0.75f)[i]
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Brand.Colors[i])
            )
        }
    }
}

/** Ein grüner Lichtstreifen läuft einmal von links nach rechts durch die Pille. */
@Composable
private fun Modifier.chargeSweep(): Modifier {
    if (LocalInspectionMode.current) return this
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) { sweep.animateTo(1f, tween(1_400, delayMillis = 150, easing = FastOutSlowInEasing)) }
    return drawWithContent {
        drawContent()
        val p = sweep.value
        if (p in 0.001f..0.999f) {
            val center = -size.width * 0.3f + p * size.width * 1.6f
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Brand.Charge.copy(alpha = 0.35f), Color.Transparent),
                    startX = center - size.width * 0.25f,
                    endX = center + size.width * 0.25f
                ),
                topLeft = Offset.Zero
            )
        }
    }
}

/** Aktionen einer Benachrichtigung: bis zu zwei App-Aktionen, „Öffnen“ und Schließen. */
@Composable
private fun NotificationActions(n: NotchNotification, onSend: (PendingIntent?) -> Unit, onDismiss: () -> Unit) {
    // Aktionen mit Texteingabe (Antworten) öffnen die App – Inline-Antworten sind vorbereitet.
    val direct = n.actions.filter { !it.needsInput && it.intent != null && it.title.isNotBlank() }.take(2)
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        direct.forEach { action -> ActionChip(action.title, highlighted = false) { onSend(action.intent) } }
        ActionChip("Öffnen", highlighted = true) { onSend(n.contentIntent) }
        ActionChip("✕", highlighted = false, onClick = onDismiss)
    }
}

/** Eingehender Anruf: Ablehnen (rot) und Annehmen (grün). */
@Composable
private fun CallButtons(call: LiveActivity.Call, onSend: (PendingIntent?) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CallButton("Ablehnen", Color(0xFFFF4D4D), Modifier.weight(1f)) { onSend(call.decline ?: call.contentIntent) }
        CallButton("Annehmen", Brand.Charge, Modifier.weight(1f)) { onSend(call.answer ?: call.contentIntent) }
    }
}

@Composable
private fun CallButton(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(30.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Color.Black, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
}

@Composable
fun ActionChip(label: String, highlighted: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (highlighted) Color.White else Color.White.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            color = if (highlighted) Color.Black else Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

