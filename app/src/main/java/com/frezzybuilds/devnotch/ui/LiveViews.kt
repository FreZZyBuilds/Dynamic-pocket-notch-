package com.frezzybuilds.devnotch.ui

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.platform.LocalContext
import com.frezzybuilds.devnotch.system.SystemStatus
import com.frezzybuilds.devnotch.notify.NotificationHub
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import android.app.PendingIntent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.notify.LiveActivity
import com.frezzybuilds.devnotch.ui.theme.Brand
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Aktuelle Uhrzeit, sekündlich – nur solange [ticking] (laufende Dauer/Countdown). */
@Composable
fun rememberNow(ticking: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (ticking && !LocalInspectionMode.current) {
        LaunchedEffect(Unit) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1_000 - now % 1_000)
            }
        }
    }
    return now
}

/** 75 s → „1:15“, 3725 s → „1:02:05“. */
fun formatDuration(millis: Long): String {
    val total = abs(millis) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Laufende Dauer nur anzeigen, wenn sie plausibel ist (0 … 24 h) – sonst „Anruf“. */
fun plausibleDuration(since: Long, now: Long): Boolean = since > 0 && now - since in 0..86_400_000L

private val DISTANCE = Regex("""(\d+[.,]?\d*\s?(m|km|ft|mi))\b""")

/** Kurztext für die Pille, z. B. „In 200 m rechts abbiegen“ → „200 m“. */
fun shortInstruction(instruction: String): String = DISTANCE.find(instruction)?.value ?: instruction

/** Entfernung zur nächsten Abbiegung – aus Anweisung oder Details (Maps setzt sie mal hier, mal dort). */
fun navDistance(nav: LiveActivity.Navigation): String? =
    DISTANCE.find(nav.instruction)?.value ?: nav.detail?.let { DISTANCE.find(it)?.value }

/** Eingeklappte Pille mit Live-Ansicht: links Symbol, rechts der wichtigste Wert. */
@Composable
fun LivePill(live: LiveActivity, lensGap: Dp) {
    val now = rememberNow(ticking = live is LiveActivity.Call || live is LiveActivity.Timer || live is LiveActivity.Recording)
    Row(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            when (live) {
                is LiveActivity.Call -> LiveIcon(live.avatar, "📞", Brand.Charge)
                is LiveActivity.Navigation -> LiveIcon(live.turnIcon, "➤", Brand.Cyan)
                is LiveActivity.Timer -> LiveIcon(null, "⏱", Brand.Violet)
                is LiveActivity.Progress -> LiveIcon(live.icon, "↓", Brand.Lilac)
                is LiveActivity.Recording -> RecordingDot()
                is LiveActivity.Transfer -> LiveIcon(null, if (live.incoming) "⬇" else "⬆", Brand.Cyan)
                LiveActivity.Torch -> LiveIcon(null, "🔦", TorchYellow)
            }
        }
        Spacer(Modifier.width(lensGap))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            val (text, color) = when (live) {
                is LiveActivity.Call -> (if (plausibleDuration(live.since, now)) formatDuration(now - live.since) else "Anruf") to Brand.Charge
                // Ohne Entfernung kein abgeschnittenes Wort („Mit…“), sondern nur der Pfeil links.
                is LiveActivity.Navigation -> (navDistance(live) ?: "") to Brand.Cyan
                is LiveActivity.Timer -> formatDuration(if (live.countDown) live.base - now else now - live.base) to Color.White
                is LiveActivity.Progress -> "${(live.fraction * 100).toInt()} %" to Brand.Lilac
                is LiveActivity.Recording -> (if (plausibleDuration(live.since, now)) formatDuration(now - live.since) else "REC") to RecordingRed
                is LiveActivity.Transfer -> "${(live.fraction * 100).toInt()} %" to Brand.Cyan
                LiveActivity.Torch -> "An" to TorchYellow
            }
            Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun LiveIcon(bitmap: Bitmap?, fallback: String, tint: Color, size: Dp = 20.dp) {
    if (bitmap != null) {
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Text(fallback, color = tint, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Aufgeklappt: Live-Karte über den Tabs, mit den passenden Aktionen. */
@Composable
fun LiveCard(live: LiveActivity, onSend: (PendingIntent?) -> Unit, modifier: Modifier = Modifier) {
    val now = rememberNow(ticking = live is LiveActivity.Call || live is LiveActivity.Timer || live is LiveActivity.Recording)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brand.Card)
            .border(1.dp, Brand.Horizontal, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (live) {
                is LiveActivity.Call -> LiveIcon(live.avatar, "📞", Brand.Charge, 32.dp)
                is LiveActivity.Navigation -> LiveIcon(live.turnIcon, "➤", Brand.Cyan, 32.dp)
                is LiveActivity.Timer -> LiveIcon(live.icon, "⏱", Brand.Violet, 32.dp)
                is LiveActivity.Progress -> LiveIcon(live.icon, "↓", Brand.Lilac, 32.dp)
                is LiveActivity.Recording -> LiveIcon(null, "●", RecordingRed, 32.dp)
                is LiveActivity.Transfer -> LiveIcon(null, if (live.incoming) "⬇" else "⬆", Brand.Cyan, 32.dp)
                LiveActivity.Torch -> LiveIcon(null, "🔦", TorchYellow, 32.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                val (title, detail) = when (live) {
                    is LiveActivity.Call -> live.caller to (if (live.ringing) "Eingehender Anruf" else if (plausibleDuration(live.since, now)) "Im Gespräch · ${formatDuration(now - live.since)}" else "Anruf")
                    is LiveActivity.Navigation -> live.instruction to (live.detail ?: live.app)
                    is LiveActivity.Timer -> live.title to "${live.app} · ${formatDuration(if (live.countDown) live.base - now else now - live.base)}"
                    is LiveActivity.Progress -> live.title to "${live.app} · ${(live.fraction * 100).toInt()} %"
                    is LiveActivity.Recording -> "Bildschirmaufnahme" to (if (plausibleDuration(live.since, now)) "${live.app} · ${formatDuration(now - live.since)}" else live.app)
                    LiveActivity.Torch -> "Taschenlampe" to "Leuchtet"
                    is LiveActivity.Transfer -> (if (live.incoming) "Empfange von ${live.peer}" else "Sende an ${live.peer}") to
                        "${live.fileName} · ${(live.fraction * 100).toInt()} %"
                }
                Text(title, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.smoothMarquee())
                Text(detail, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (live is LiveActivity.Progress) {
            Box(Modifier.padding(vertical = 2.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
                Box(Modifier.fillMaxWidth(live.fraction).fillMaxHeight().clip(CircleShape).background(Brand.Horizontal))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when (live) {
                is LiveActivity.Call -> {
                    if (live.ringing) {
                        ActionChip("Ablehnen", highlighted = false) { onSend(live.decline ?: live.contentIntent) }
                        ActionChip("Annehmen", highlighted = true) { onSend(live.answer ?: live.contentIntent) }
                    } else {
                        live.hangUp?.let { ActionChip("Auflegen", highlighted = false) { onSend(it) } }
                        ActionChip("Zum Anruf", highlighted = true) { onSend(live.contentIntent) }
                    }
                }
                is LiveActivity.Navigation -> ActionChip("${live.app} öffnen", highlighted = true) { onSend(live.contentIntent) }
                LiveActivity.Torch -> {
                    val context = LocalContext.current
                    ActionChip("Ausschalten", highlighted = true) { SystemStatus.turnOffTorch(context) }
                }
                else -> ActionChip("Öffnen", highlighted = true) { onSend(live.contentIntent) }
            }
            // Bleibt eine Ansicht hängen (z. B. App lässt die Benachrichtigung stehen): wegnehmen.
            if (live !is LiveActivity.Call && live !is LiveActivity.Torch && live !is LiveActivity.Transfer) {
                ActionChip("Ausblenden", highlighted = false) { NotificationHub.hide(live.key) }
            }
        }
    }
}

private val RecordingRed = Color(0xFFFF3B30)
private val TorchYellow = Color(0xFFFFD60A)

/** Roter, sanft pulsierender Aufnahme-Punkt (Alpha in der Zeichenphase). */
@Composable
private fun RecordingDot() {
    val alpha = if (LocalInspectionMode.current) {
        remember { mutableFloatStateOf(1f) }
    } else {
        rememberInfiniteTransition(label = "rec").animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "recAlpha"
        )
    }
    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(10.dp).graphicsLayer { this.alpha = alpha.value }.clip(CircleShape).background(RecordingRed))
    }
}
