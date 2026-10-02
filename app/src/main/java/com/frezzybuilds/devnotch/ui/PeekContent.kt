package com.frezzybuilds.devnotch.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.frezzybuilds.devnotch.share.localsend.LocalSend
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
import androidx.compose.foundation.layout.widthIn
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
 * Inhalt der nach unten gewachsenen Pille: oben die (leere) Zeile mit der Kameralinse, darunter
 * Symbol, Titel/Untertitel und rechts ein Wert, dann Aktionen.
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
    onDismiss: () -> Unit = {},
    /** Groß öffnen (Lesen und Antworten) – wie Herunterziehen. */
    onExpand: () -> Unit = {},
    /** Alle Benachrichtigungen als Stapel zeigen („+N weitere“). */
    onShowAll: () -> Unit = onExpand
) {
    if (peek is Peek.Notification && peek.style == com.frezzybuilds.devnotch.notify.NotificationStyle.COMPACT) {
        Box(modifier.fillMaxSize()) { CompactNotificationPeek(peek, pillHeight) }
        return
    }
    if (peek is Peek.Notification && peek.style == com.frezzybuilds.devnotch.notify.NotificationStyle.GLASS) {
        Box(modifier.fillMaxSize()) { GlassNotificationPeek(peek, pillHeight, onSend, onExpand, onShowAll) }
        return
    }
    if (peek is Peek.MusicPlayer) {
        Box(modifier.fillMaxSize()) { MusicPlayerContent(pillHeight) }
        return
    }
    if (peek is Peek.NameDrop) {
        Box(modifier.fillMaxSize()) { NameDropContent(pillHeight) }
        return
    }
    if (peek is Peek.Payment) {
        Box(modifier.fillMaxSize()) { PaymentContent(peek.merchant, peek.amount, pillHeight) }
        return
    }
    val spec = peekSpec(peek)
    // Benachrichtigung: Hintergrund in der Farbe des App-Icons (Telegram blau, WhatsApp grün …),
    // nach unten ins Schwarz auslaufend – Text bleibt gut lesbar.
    val tint = (peek as? Peek.Notification)?.notification?.accent?.let { Color(it) }
    Box(
        modifier
            .fillMaxSize()
            .then(if (tint != null) Modifier.background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.22f)))) else Modifier)
            .then(if (peek is Peek.Charging) Modifier.chargeSweep() else Modifier)
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            // Die Zeile mit der Kameralinse liegt in der Statusleiste: Dort zeichnet Android Uhr
            // und Symbole über jedes App-Fenster. Deshalb bleibt sie leer – alles beginnt darunter.
            Spacer(Modifier.height(pillHeight))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                spec.leading()
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        spec.title,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth().smoothMarquee()
                    )
                    spec.subtitle?.let {
                        if (peek is Peek.Notification) {
                            // Lange Nachrichten laufen als Lauftext durch (Zeilenumbrüche zu Leerzeichen);
                            // die Anzeigedauer wächst mit der Länge (siehe NotificationHub).
                            Text(
                                it.replace('\n', ' '),
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth().smoothMarquee(MARQUEE_DELAY_MS, MARQUEE_VELOCITY)
                            )
                        } else {
                            Text(
                                it,
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.widthIn(max = 96.dp)) { spec.trailing() }
            }
            when (peek) {
                is Peek.Notification -> NotificationActions(peek.notification, onSend, onDismiss, onExpand)
                is Peek.LiveCall -> CallButtons(peek.call, onSend)
                is Peek.LiveBanner -> (peek.live as? LiveActivity.Call)?.let { OngoingCallControls(it, peek.keypad, onSend) }
                is Peek.ShareRequest -> Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CallButton("Ablehnen", Color.White.copy(alpha = 0.85f), Modifier.weight(1f)) {
                        LocalSend.decide(peek.request.id, false)
                    }
                    CallButton("Annehmen", Brand.Cyan, Modifier.weight(1f)) {
                        LocalSend.decide(peek.request.id, true)
                    }
                }
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
        // Kein App-Name rechts: Er drängte Titel und Text zusammen; das Symbol zeigt die App.
        trailing = {}
    )
    is Peek.LiveCall -> PeekSpec(
        title = peek.call.caller,
        subtitle = "Eingehender Anruf",
        leading = {
            val avatar = rememberUsableAvatar(peek.call.avatar)
            if (avatar != null) {
                val image = remember(avatar) { avatar.asImageBitmap() }
                Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(26.dp).clip(CircleShape))
            } else {
                Pulsing { Badge("📞", Brush.linearGradient(listOf(Brand.Charge, Color(0xFF00C853)))) }
            }
        },
        trailing = { Pulsing { Text("● ● ●", color = Brand.Charge, style = MaterialTheme.typography.labelSmall) } }
    )
    is Peek.LiveBanner -> liveBannerSpec(peek.live)
    is Peek.ShareRequest -> {
        val r = peek.request
        val model = r.sender.deviceModel?.lowercase().orEmpty()
        val device = if (r.sender.deviceType == "desktop" || "mac" in model || "windows" in model || "linux" in model) "💻" else "📱"
        PeekSpec(
            title = r.sender.alias,
            subtitle = r.text?.let { "„$it“" } ?: if (r.files.size == 1) "möchte „${r.files.first().fileName}“ senden · ${formatBytes(r.totalBytes)}"
                else "möchte ${r.files.size} Dateien senden · ${formatBytes(r.totalBytes)}",
            leading = { Badge(device, Brush.linearGradient(listOf(Brand.Cyan, Brand.Violet))) },
            trailing = { Text("LocalSend", color = Brand.Cyan, style = MaterialTheme.typography.labelSmall) }
        )
    }
    // Wird oben in PeekContent direkt gezeichnet.
    is Peek.MusicPlayer, is Peek.NameDrop, is Peek.Payment -> PeekSpec("", null, {}, {})
    is Peek.System -> {
        val tint = Color(peek.tint)
        PeekSpec(
            title = peek.title,
            subtitle = null,
            leading = {
                Box(Modifier.size(26.dp).clip(CircleShape).background(tint.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                    Text(peek.symbol, color = tint, style = MaterialTheme.typography.labelLarge)
                }
            },
            trailing = {
                peek.value?.let { Text(it, color = tint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1) }
            }
        )
    }
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
private fun NotificationActions(n: NotchNotification, onSend: (PendingIntent?) -> Unit, onDismiss: () -> Unit, onExpand: () -> Unit) {
    // „Antworten“ öffnet die große Ansicht mit Eingabefeld; daneben höchstens eine App-Aktion.
    val direct = n.actions.filter { !it.needsInput && it.intent != null && it.title.isNotBlank() }
        .take(if (n.reply != null) 1 else 2)
    // Seitlich wischbar statt abgeschnitten („Ö…“), lange App-Aktionen gekürzt.
    Row(
        Modifier.padding(top = 6.dp).fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (n.reply != null) ActionChip("Antworten", highlighted = true, onClick = onExpand)
        direct.forEach { action -> ActionChip(action.title.shortLabel(), highlighted = false) { onSend(action.intent) } }
        ActionChip("Öffnen", highlighted = n.reply == null) { onSend(n.contentIntent) }
        ActionChip("✕", highlighted = false, onClick = onDismiss)
    }
}

/** Eingehender Anruf: Ablehnen (rot) und Annehmen (grün). */
@Composable
private fun CallButtons(call: LiveActivity.Call, onSend: (PendingIntent?) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Mit Anrufsteuerung direkt über Android Telecom, sonst über die Knöpfe der Telefon-App.
        // Zustand erst beim Tippen lesen (nicht beim Zeichnen).
        fun telecom() = com.frezzybuilds.devnotch.service.CallControl.state.value?.ringing == true
        CallButton("Ablehnen", Color(0xFFFF4D4D), Modifier.weight(1f)) {
            if (telecom()) com.frezzybuilds.devnotch.service.CallControl.reject() else onSend(call.decline ?: call.contentIntent)
        }
        CallButton("Annehmen", Brand.Charge, Modifier.weight(1f)) {
            if (telecom()) com.frezzybuilds.devnotch.service.CallControl.answer() else onSend(call.answer ?: call.contentIntent)
        }
    }
}

@Composable
private fun CallButton(label: String, color: Color, modifier: Modifier, textColor: Color = Color.Black, onClick: () -> Unit) {
    Box(
        modifier
            .height(30.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = textColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
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

/** Lauftext langer Nachrichten: kurz stehen lassen, dann zügig, aber lesbar durchlaufen. */
private const val MARQUEE_DELAY_MS = 900
private val MARQUEE_VELOCITY = 60.dp

/** Live-Banner: Navigation mit Pfeil und Entfernung, Anruf mit Dauer, Timer mit Restzeit. */
@Composable
private fun liveBannerSpec(live: LiveActivity): PeekSpec {
    val now = rememberNow(ticking = live is LiveActivity.Call || live is LiveActivity.Timer)
    @Composable
    fun icon(bitmap: android.graphics.Bitmap?, fallback: String, brush: Brush) {
        if (bitmap != null) {
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(28.dp))
        } else {
            Badge(fallback, brush)
        }
    }
    @Composable
    fun value(text: String, color: Color) =
        Text(text, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)

    return when (live) {
        is LiveActivity.Navigation -> PeekSpec(
            title = live.instruction,
            subtitle = live.detail ?: live.app,
            leading = { icon(live.turnIcon, "➤", Brush.linearGradient(listOf(Brand.Cyan, Brand.Violet))) },
            trailing = { navDistance(live)?.let { value(it, Brand.Cyan) } }
        )
        is LiveActivity.Call -> PeekSpec(
            title = live.caller,
            subtitle = com.frezzybuilds.devnotch.service.CallControl.state.collectAsStateWithLifecycle().value.let { c ->
                when {
                    c?.dialing == true -> "Wählt …"
                    c?.onHold == true -> "Gehalten"
                    plausibleDuration(live.since, now) -> "Im Gespräch · ${formatDuration(now - live.since)}"
                    else -> "Im Gespräch"
                }
            },
            leading = { icon(rememberUsableAvatar(live.avatar), "📞", Brush.linearGradient(listOf(Brand.Charge, Color(0xFF00C853)))) },
            trailing = { CallWave() }
        )
        is LiveActivity.Timer -> PeekSpec(
            title = live.title,
            subtitle = live.app,
            leading = { icon(live.icon, "⏱", Brand.Horizontal) },
            trailing = { value(formatDuration(if (live.countDown) live.base - now else now - live.base), Color.White) }
        )
        is LiveActivity.Progress -> PeekSpec(
            title = live.title,
            subtitle = live.app,
            leading = { icon(live.icon, "↓", Brand.Horizontal) },
            trailing = { value("${(live.fraction * 100).toInt()} %", Brand.Lilac) }
        )
        is LiveActivity.Recording -> PeekSpec(
            title = "Bildschirmaufnahme",
            subtitle = live.app,
            leading = { Badge("●", Brush.linearGradient(listOf(Color(0xFFFF3B30), Color(0xFFFF6B6B)))) },
            trailing = { if (plausibleDuration(live.since, now)) value(formatDuration(now - live.since), Color(0xFFFF3B30)) }
        )
        is LiveActivity.Transfer -> PeekSpec(
            title = if (live.incoming) "Empfange von ${live.peer}" else "Sende an ${live.peer}",
            subtitle = if (live.fileCount > 1) "${live.fileName} · ${live.fileCount} Dateien" else live.fileName,
            leading = { Badge(if (live.incoming) "⬇" else "⬆", Brush.linearGradient(listOf(Brand.Cyan, Brand.Violet))) },
            trailing = { value("${(live.fraction * 100).toInt()} %", Brand.Cyan) }
        )
        LiveActivity.Torch -> PeekSpec(
            title = "Taschenlampe",
            subtitle = "Leuchtet",
            leading = { Badge("🔦", Brush.linearGradient(listOf(Color(0xFFFFD60A), Color(0xFFFFA000)))) },
            trailing = {}
        )
    }
}

/** 1 536 000 → „1,5 MB“. */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(java.util.Locale.GERMANY, bytes / 1e9)
    bytes >= 1_000_000 -> "%.1f MB".format(java.util.Locale.GERMANY, bytes / 1e6)
    bytes >= 1_000 -> "%.0f KB".format(java.util.Locale.GERMANY, bytes / 1e3)
    else -> "$bytes B"
}

/** Grüne Gesprächs-Wellenform wie in Apples Anrufansicht (Layer-Phase, keine Recomposition). */
@Composable
private fun CallWave() {
    val inspection = LocalInspectionMode.current
    val transition = rememberInfiniteTransition(label = "callWave")
    val bars = listOf(360, 260, 420, 300).map { d ->
        if (inspection) null else transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(d), RepeatMode.Reverse), label = "cw$d")
    }
    Row(Modifier.height(18.dp), horizontalArrangement = Arrangement.spacedBy(2.5.dp), verticalAlignment = Alignment.CenterVertically) {
        bars.forEachIndexed { i, bar ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .graphicsLayer { scaleY = bar?.value ?: listOf(0.5f, 0.9f, 0.6f, 0.8f)[i] }
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brand.Charge)
            )
        }
    }
}

/**
 * Laufendes Gespräch: zugeklappt nur „Steuerung“ und „Auflegen“. Ein Tipp auf „Steuerung“ lässt
 * Stumm, Lautsprecher, Halten und die Wahltasten mit einem federnden „Blob“ aufploppen.
 * Mit Anrufsteuerung (Begleit-App) direkt über Android Telecom; ohne sie Stumm über das Mikrofon,
 * Auflegen über die Aktion der Telefon-App und „Tasten“ öffnet deren Wahltasten.
 */
@Composable
private fun OngoingCallControls(call: LiveActivity.Call, open: Boolean, onSend: (PendingIntent?) -> Unit) {
    val control by com.frezzybuilds.devnotch.service.CallControl.state.collectAsStateWithLifecycle()
    val c = control
    val context = androidx.compose.ui.platform.LocalContext.current
    val audio = remember { context.getSystemService(android.media.AudioManager::class.java) }
    var fallbackMuted by remember { androidx.compose.runtime.mutableStateOf(audio?.isMicrophoneMute == true) }
    val blob = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.55f, stiffness = 500f)

    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.animation.AnimatedVisibility(
            visible = open,
            enter = androidx.compose.animation.scaleIn(blob, initialScale = 0.6f, transformOrigin = TransformOrigin(0.15f, 1f)) +
                androidx.compose.animation.fadeIn(tween(140)),
            exit = androidx.compose.animation.scaleOut(tween(140), targetScale = 0.7f, transformOrigin = TransformOrigin(0.15f, 1f)) +
                androidx.compose.animation.fadeOut(tween(120))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val muted = c?.muted ?: fallbackMuted
                    CallToggle("Stumm", muted, Modifier.weight(1f)) {
                        if (c != null) {
                            com.frezzybuilds.devnotch.service.CallControl.toggleMute()
                        } else {
                            audio?.let { it.isMicrophoneMute = !it.isMicrophoneMute; fallbackMuted = it.isMicrophoneMute }
                        }
                    }
                    if (c != null) {
                        CallToggle("Lautspr.", c.speaker, Modifier.weight(1f)) { com.frezzybuilds.devnotch.service.CallControl.toggleSpeaker() }
                        if (c.canHold) CallToggle(if (c.onHold) "Fortsetzen" else "Halten", c.onHold, Modifier.weight(1f)) { com.frezzybuilds.devnotch.service.CallControl.toggleHold() }
                    } else {
                        // Ohne Anrufsteuerung kann nur die Telefon-App Töne ins Gespräch senden.
                        CallToggle("Tasten", false, Modifier.weight(1f)) { onSend(call.contentIntent) }
                    }
                }
                if (c != null) InCallKeypad()
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Ein Knopf für alles: Steuerung und Wahltasten auf-/zuklappen.
            CallToggle(if (open) "✕  Schließen" else "⋯  Steuerung", open, Modifier.weight(1f)) {
                com.frezzybuilds.devnotch.service.CallControl.keypadOpen.value = !open
            }
            CallButton("Auflegen", Color(0xFFFF453A), Modifier.weight(1f), textColor = Color.White) {
                if (c != null) com.frezzybuilds.devnotch.service.CallControl.hangUp() else onSend(call.hangUp ?: call.contentIntent)
            }
        }
    }
}

@Composable
private fun CallToggle(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) =
    CallButton(label, if (on) Color.White else Color.White.copy(alpha = 0.16f), modifier, textColor = if (on) Color.Black else Color.White, onClick = onClick)

/** Wahltasten im Gespräch: jeder Druck sendet den Ton über Android Telecom (z. B. Hotline-Menüs). */
@Composable
private fun InCallKeypad() {
    val typed by com.frezzybuilds.devnotch.service.CallControl.typed.collectAsStateWithLifecycle()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            typed.ifEmpty { " " },
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        listOf("123", "456", "789", "*0#").forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { digit ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(26.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable(role = Role.Button, onClickLabel = "Taste $digit") {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                com.frezzybuilds.devnotch.service.CallControl.dtmf(digit)
                            },
                        contentAlignment = Alignment.Center
                    ) { Text(digit.toString(), color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

/** „Als gelesen markieren“ → „Gelesen“; sonst höchstens 14 Zeichen. */
private fun String.shortLabel(): String = when {
    Regex("(?i)gelesen|mark as read").containsMatchIn(this) -> "Gelesen"
    Regex("(?i)stumm|mute").containsMatchIn(this) -> "Stumm"
    length > 14 -> take(13).trimEnd() + "…"
    else -> this
}
