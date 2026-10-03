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
    /**
     * Wo der Inhalt beginnt: knapp unter der Kameralinse (nicht erst unter der ganzen Pille) –
     * so bleibt die Einblendung flach. Standard: unter der Pille.
     */
    contentTop: Dp = pillHeight,
    /** Benachrichtigung/Anruf: Intent auslösen (Öffnen, Aktion, Annehmen …). */
    onSend: (PendingIntent?) -> Unit = {},
    /** Benachrichtigung schließen. */
    onDismiss: () -> Unit = {},
    /** Groß öffnen (Lesen und Antworten) – wie Herunterziehen. */
    onExpand: () -> Unit = {},
    /** Alle Benachrichtigungen als Stapel zeigen („+N weitere“). */
    onShowAll: () -> Unit = onExpand
) {
    if (peek.isSidePill) {
        Box(modifier.fillMaxSize()) { SidePillContent(peek, lensGap) }
        return
    }
    if (peek is Peek.Notification && peek.style == com.frezzybuilds.devnotch.notify.NotificationStyle.COMPACT) {
        Box(modifier.fillMaxSize()) { CompactNotificationPeek(peek, contentTop) }
        return
    }
    if (peek is Peek.Notification && peek.style == com.frezzybuilds.devnotch.notify.NotificationStyle.GLASS) {
        Box(modifier.fillMaxSize()) { GlassNotificationPeek(peek, contentTop, onSend, onExpand, onShowAll) }
        return
    }
    if (peek is Peek.Notification && peek.style == com.frezzybuilds.devnotch.notify.NotificationStyle.APERTURE) {
        Box(modifier.fillMaxSize()) { ApertureNotificationPeek(peek, pillHeight, lensGap, contentTop) }
        return
    }
    if (peek is Peek.MusicPlayer) {
        Box(modifier.fillMaxSize()) { MusicPlayerContent(contentTop) }
        return
    }
    if (peek is Peek.NameDrop) {
        Box(modifier.fillMaxSize()) { NameDropContent(contentTop) }
        return
    }
    if (peek is Peek.Payment) {
        Box(modifier.fillMaxSize()) { PaymentContent(peek.merchant, peek.amount, contentTop) }
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
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            // Die Zeile mit der Kameralinse liegt in der Statusleiste: Dort zeichnet Android Uhr
            // und Symbole über jedes App-Fenster. Deshalb bleibt sie leer – alles beginnt darunter.
            Spacer(Modifier.height(contentTop))
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
    is Peek.MusicPlayer, is Peek.NameDrop, is Peek.Payment, is Peek.Volume,
    is Peek.Charging, is Peek.System, is Peek.TrackChanged -> PeekSpec("", null, {}, {})
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

/** Einblendungen, die nur die Pille verbreitern: Symbol links, Inhalt rechts der Kamera. */
val Peek.isSidePill: Boolean
    get() = this is Peek.System || this is Peek.Charging || this is Peek.Volume || this is Peek.TrackChanged

/** Linke Seite einer Seitenpille: nur das Symbol, dicht an der Kameralinse (dp). */
const val SIDE_PILL_LEFT_DP = 44

/** Abstand des Inhalts zur Linse bzw. zum rechten Rand (dp). */
private const val SIDE_PILL_INNER_DP = 4
private const val SIDE_PILL_END_DP = 14

/** Schätzung ohne Textmessung (Tests, Vorschau): rechte Seite in dp. */
fun sidePillSideDp(peek: Peek): Int = when (peek) {
    is Peek.System -> {
        val text = peek.title + (peek.value?.let { " $it" } ?: "")
        (text.length * 7.2f + SIDE_PILL_INNER_DP + SIDE_PILL_END_DP).toInt().coerceIn(56, 170)
    }
    is Peek.Charging -> 84
    is Peek.Volume -> 72 + SIDE_PILL_INNER_DP + SIDE_PILL_END_DP
    is Peek.TrackChanged -> 138
    else -> 120
}

/**
 * Rechte Seite genau so breit wie ihr Inhalt (gemessen): Die Pille ist nicht länger als nötig,
 * der Text beginnt direkt hinter der Kamera.
 */
@Composable
fun rememberSidePillSideDp(peek: Peek): Int {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    val small = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    val density = androidx.compose.ui.platform.LocalDensity.current
    return remember(peek, style) {
        fun width(text: String, s: androidx.compose.ui.text.TextStyle = style): Float =
            with(density) { measurer.measure(text, s, maxLines = 1).size.width.toDp().value }
        val content = when (peek) {
            is Peek.System -> width(peek.title) + (peek.value?.let { 5f + width(it, style.copy(fontWeight = FontWeight.Bold)) } ?: 0f)
            is Peek.Charging -> (peek.percent?.let { width("$it %", style.copy(fontWeight = FontWeight.Bold)) + 6f } ?: 0f) + 26f
            is Peek.Volume -> 72f
            is Peek.TrackChanged -> minOf(width(peek.title, small), 120f) + 8f + 15f
            else -> 100f
        }
        (content + SIDE_PILL_INNER_DP + SIDE_PILL_END_DP + 1).toInt().coerceAtMost(190)
    }
}

/**
 * Wie beim iPhone: nur die Pille wird breiter – links neben der Kamera das Symbol, rechts
 * „Lautlos“, der Akkustand, die Lautstärke oder der neue Titel. Keine zusätzliche Höhe.
 */
@Composable
private fun SidePillContent(peek: Peek, lensGap: Dp) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        // Links schmal (nur Symbol), rechts der Rest – passend zur Verschiebung im Container.
        val half = SIDE_PILL_LEFT_DP.dp.coerceAtMost((maxWidth - lensGap) / 2)
        Box(Modifier.width(half).fillMaxHeight().padding(start = 10.dp), contentAlignment = Alignment.CenterStart) {
            when (peek) {
                is Peek.System -> SymbolDot(peek.symbol, Color(peek.tint))
                is Peek.Charging -> SymbolDot("⚡", Brand.Charge)
                is Peek.Volume -> SpeakerIcon(peek.level)
                is Peek.TrackChanged -> {
                    val art = peek.artwork
                    if (art != null) {
                        val image = remember(art) { art.asImageBitmap() }
                        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)))
                    } else {
                        SymbolDot("♪", Brand.Magenta)
                    }
                }
                else -> Unit
            }
        }
        // Inhalt direkt hinter der Linse – kein Leerraum zwischen Kamera und Text.
        Row(
            Modifier.padding(start = half + lensGap + SIDE_PILL_INNER_DP.dp).fillMaxHeight().fillMaxWidth().padding(end = SIDE_PILL_END_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            when (peek) {
                is Peek.System -> {
                    val tint = Color(peek.tint)
                    Text(
                        peek.title,
                        color = if (peek.value == null) tint else Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false).smoothMarquee()
                    )
                    peek.value?.let {
                        Spacer(Modifier.width(5.dp))
                        Text(it, color = tint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
                is Peek.Charging -> {
                    peek.percent?.let {
                        Text("$it %", color = Brand.Charge, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                        Spacer(Modifier.width(6.dp))
                    }
                    BatteryGlyph(peek.percent ?: 100)
                }
                is Peek.Volume -> LevelBar(peek.level, Modifier.width(72.dp))
                is Peek.TrackChanged -> {
                    Text(
                        peek.title,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false).smoothMarquee()
                    )
                    Spacer(Modifier.width(8.dp))
                    MiniEqualizer()
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun SymbolDot(symbol: String, tint: Color) {
    Box(Modifier.size(26.dp).clip(CircleShape).background(tint.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
        Text(symbol, color = tint, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Akku als Umriss mit grüner Füllung – gezeichnet, damit er auf jedem Gerät gleich aussieht. */
@Composable
private fun BatteryGlyph(percent: Int) {
    androidx.compose.foundation.Canvas(Modifier.size(26.dp, 13.dp)) {
        val stroke = 1.5.dp.toPx()
        val cap = 2.dp.toPx()
        val body = androidx.compose.ui.geometry.Size(size.width - cap - 1.dp.toPx(), size.height)
        val radius = androidx.compose.ui.geometry.CornerRadius(3.5.dp.toPx())
        drawRoundRect(Color.White.copy(alpha = 0.55f), size = body, cornerRadius = radius, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        val inset = stroke + 1.dp.toPx()
        drawRoundRect(
            Brand.Charge,
            topLeft = Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size((body.width - 2 * inset) * percent.coerceIn(0, 100) / 100f, body.height - 2 * inset),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
        )
        drawRoundRect(
            Color.White.copy(alpha = 0.55f),
            topLeft = Offset(size.width - cap, size.height * 0.32f),
            size = androidx.compose.ui.geometry.Size(cap, size.height * 0.36f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
        )
    }
}

/** Lautsprecher mit 0–2 Wellen je nach Pegel (gezeichnet statt Emoji). */
@Composable
private fun SpeakerIcon(level: Float) {
    androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.10f, h * 0.38f); lineTo(w * 0.28f, h * 0.38f); lineTo(w * 0.50f, h * 0.18f)
            lineTo(w * 0.50f, h * 0.82f); lineTo(w * 0.28f, h * 0.62f); lineTo(w * 0.10f, h * 0.62f); close()
        }
        drawPath(path, Color.White)
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        if (level <= 0f) {
            drawLine(Color.White, Offset(w * 0.62f, h * 0.36f), Offset(w * 0.88f, h * 0.64f), 1.6.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(Color.White, Offset(w * 0.88f, h * 0.36f), Offset(w * 0.62f, h * 0.64f), 1.6.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
        } else {
            drawArc(Color.White, -45f, 90f, false, Offset(w * 0.38f, h * 0.30f), androidx.compose.ui.geometry.Size(w * 0.30f, h * 0.40f), style = stroke)
            if (level > 0.5f) drawArc(Color.White, -45f, 90f, false, Offset(w * 0.30f, h * 0.16f), androidx.compose.ui.geometry.Size(w * 0.52f, h * 0.68f), style = stroke)
        }
    }
}

/** Pegel als Balken; die Breite gleitet in der Zeichenphase (keine Recomposition je Bild). */
@Composable
private fun LevelBar(level: Float, modifier: Modifier) {
    val animated by androidx.compose.animation.core.animateFloatAsState(level, tween(180), label = "level")
    Box(modifier.height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = animated
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
