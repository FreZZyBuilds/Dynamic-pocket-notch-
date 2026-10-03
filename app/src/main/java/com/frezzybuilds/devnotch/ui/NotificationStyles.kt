package com.frezzybuilds.devnotch.ui

import android.app.PendingIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frezzybuilds.devnotch.notify.NotchNotification
import com.frezzybuilds.devnotch.notify.NotificationHub
import com.frezzybuilds.devnotch.peek.Peek
import com.frezzybuilds.devnotch.ui.theme.Brand

/** „jetzt“, „vor 5 Min.“, „vor 2 Std.“ – wie in der iPhone-Mitteilung. */
fun relativeTime(postTime: Long, now: Long): String {
    if (postTime <= 0) return "jetzt"
    val minutes = (now - postTime).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> "jetzt"
        minutes < 60 -> "vor $minutes Min."
        minutes < 24 * 60 -> "vor ${minutes / 60} Std."
        else -> "vor ${minutes / (24 * 60)} T."
    }
}

/** Glasiger Leuchtrand (Cyan → Violett → Magenta) mit weichem Schein nach außen. */
private val GlowColors = listOf(Brand.Cyan, Brand.Violet, Brand.Magenta, Brand.Cyan)

private fun Modifier.glassCard(radius: Dp, accent: Color?, glow: Boolean = true): Modifier = this
    .drawBehind {
        if (glow) {
            val r = radius.toPx()
            // Weicher Schein: mehrere breite, sehr transparente Ränder.
            listOf(10f to 0.05f, 6f to 0.09f, 3f to 0.14f).forEach { (w, a) ->
                drawRoundRect(
                    Brush.sweepGradient(GlowColors.map { it.copy(alpha = a) }),
                    cornerRadius = CornerRadius(r + w.dp.toPx() / 2),
                    style = Stroke(w.dp.toPx())
                )
            }
        }
    }
    .clip(RoundedCornerShape(radius))
    .background(
        Brush.verticalGradient(
            listOf(
                (accent ?: Color.White).copy(alpha = if (accent != null) 0.22f else 0.14f),
                Color.White.copy(alpha = 0.05f)
            )
        )
    )
    .border(1.dp, Brush.sweepGradient(GlowColors.map { it.copy(alpha = 0.75f) }), RoundedCornerShape(radius))

/** Avatar bzw. Symbol mit kleinem App-Abzeichen unten rechts (wie auf dem iPhone). */
@Composable
fun NotificationAvatar(n: NotchNotification, size: Dp) {
    Box(Modifier.size(size)) {
        val icon = n.icon ?: n.appIcon
        if (icon != null) {
            val image = remember(icon) { icon.asImageBitmap() }
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
        } else {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(Brand.Horizontal), contentAlignment = Alignment.Center) {
                Text(n.appLabel.take(1).uppercase(), color = Color.Black, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
        // Abzeichen nur, wenn das große Bild nicht schon das App-Icon ist.
        val badge = n.appIcon?.takeIf { n.icon != null && n.icon !== it }
        if (badge != null) {
            val image = remember(badge) { badge.asImageBitmap() }
            Image(
                image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .size(size * 0.42f)
                    .clip(RoundedCornerShape(size * 0.12f))
                    .border(1.5.dp, Color.Black, RoundedCornerShape(size * 0.12f))
            )
        }
    }
}

/**
 * iOS kompakt: flach wie auf dem iPhone. Das Symbol sitzt links über die ganze Höhe (neben der Kamera
 * ist Platz), Name, Text und „jetzt“ beginnen knapp unter der Linse. Tippen öffnet, Herunterziehen
 * zum Antworten.
 */
@Composable
fun CompactNotificationPeek(peek: Peek.Notification, contentTop: Dp) {
    val n = peek.notification
    val now = rememberNow(ticking = false)
    val avatar = 34.dp
    Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 18.dp)) {
        Box(Modifier.align(Alignment.CenterStart)) { NotificationAvatar(n, avatar) }
        Column(Modifier.fillMaxWidth().padding(start = avatar + 12.dp, top = contentTop)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    n.title, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (peek.more > 0) "+${peek.more}" else relativeTime(n.postTime, now),
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            n.text?.let {
                Text(
                    it.replace('\n', ' '),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().smoothMarquee(900, 60.dp)
                )
            }
        }
    }
}

/**
 * Aperture (wie der iOS-Tweak): Die Pille wird nur breit und etwas tiefer. Symbol links über beide
 * Zeilen, Name in App-Farbe links der Kamera, „jetzt“ rechts davon, die Nachricht darunter.
 * In der Kamerazeile zeichnet Android sonst Uhr und Symbole – daher am besten mit überdeckter Statusleiste.
 */
@Composable
fun ApertureNotificationPeek(peek: Peek.Notification, pillHeight: Dp, lensGap: Dp, contentTop: Dp = pillHeight) {
    val n = peek.notification
    val now = rememberNow(ticking = false)
    // Name in App-Farbe, aufgehellt, damit er auf Schwarz leuchtet.
    val nameColor = n.accent?.let { androidx.compose.ui.graphics.lerp(Color(it), Color.White, 0.2f) } ?: Brand.Cyan
    val avatar = 30.dp
    val side = 12.dp
    // Erste Zeile auf Höhe der Linse, die zweite knapp darunter.
    val titleTop = (pillHeight / 2 - 10.dp).coerceAtLeast(0.dp)
    val titleHeight = 20.dp
    val textTop = maxOf(contentTop - 2.dp, titleTop + titleHeight - 2.dp)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val half = (maxWidth - lensGap) / 2
        Box(Modifier.padding(start = side, top = titleTop).height(maxHeight - titleTop), contentAlignment = Alignment.CenterStart) {
            NotificationAvatar(n, avatar)
        }
        val textStart = side + avatar + 8.dp
        Box(Modifier.padding(start = textStart, top = titleTop).width((half - textStart).coerceAtLeast(24.dp)).height(titleHeight), contentAlignment = Alignment.CenterStart) {
            Text(n.title, color = nameColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.padding(start = half + lensGap, top = titleTop, end = side + 4.dp).fillMaxWidth().height(titleHeight), contentAlignment = Alignment.CenterEnd) {
            Text(
                if (peek.more > 0) "+${peek.more}" else relativeTime(n.postTime, now),
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
        Box(Modifier.padding(start = textStart, top = textTop, end = side + 4.dp).fillMaxWidth()) {
            Text(
                (n.text ?: n.appLabel).replace('\n', ' '),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().smoothMarquee(900, 60.dp)
            )
        }
    }
}

/**
 * Glas: Milchglas-Karte mit Leuchtrand („✦ App · jetzt“), Avatar, Titel und Text. Unten
 * „+N weitere von App · alle zeigen“ – öffnet den Stapel – oder die Aktionen.
 */
@Composable
fun GlassNotificationPeek(
    peek: Peek.Notification,
    pillHeight: Dp,
    onSend: (PendingIntent?) -> Unit,
    onExpand: () -> Unit,
    onShowAll: () -> Unit
) {
    val n = peek.notification
    val now = rememberNow(ticking = false)
    val accent = n.accent?.let { Color(it) }
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
        Spacer(Modifier.height(pillHeight))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 10.dp)
                .glassCard(20.dp, accent)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✦ ", color = Brand.Violet, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(n.appLabel, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                Text(relativeTime(n.postTime, now), color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NotificationAvatar(n, 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(n.title, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    n.text?.let {
                        Text(
                            it.replace('\n', ' '),
                            color = Color.White.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth().smoothMarquee(900, 60.dp)
                        )
                    }
                }
            }
            if (peek.more > 0) {
                Text(
                    "+${peek.more} weitere von ${n.appLabel} · alle zeigen",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = onShowAll)
                        .padding(vertical = 3.dp)
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (n.reply != null) ActionChip("Antworten", highlighted = true, onClick = onExpand)
                    ActionChip("Öffnen", highlighted = n.reply == null) { onSend(n.contentIntent) }
                    ActionChip("Alle", highlighted = false, onClick = onShowAll)
                }
            }
        }
    }
}

/**
 * Stapel „Benachrichtigungen · N“ im aufgeklappten Dashboard: Glas-Zeilen, seitenweise mit
 * Punkten, „Alle löschen“. Tippen auf eine Zeile öffnet sie groß (lesen und antworten).
 */
@Composable
fun NotificationStackContent(onOpen: (NotchNotification) -> Unit) {
    val items by NotificationHub.recent.collectAsStateWithLifecycle()
    val now = rememberNow(ticking = true, stepMs = 30_000L)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Benachrichtigungen", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(" · ${items.size}", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            if (items.isNotEmpty()) ActionChip("Alle löschen", highlighted = false) { NotificationHub.dismissAllRecent() }
        }
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Keine neuen Benachrichtigungen", color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.labelLarge)
            }
            return@Column
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            val rowHeight = 52.dp
            val gap = 6.dp
            val perPage = ((maxHeight - 14.dp + gap) / (rowHeight + gap)).toInt().coerceAtLeast(1)
            val pages = items.chunked(perPage)
            val pager = rememberPagerState { pages.size }
            Column {
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), pageSpacing = 12.dp, key = { pages.getOrNull(it)?.firstOrNull()?.key ?: it }) { page ->
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        pages.getOrNull(page).orEmpty().forEachIndexed { i, n ->
                            StackRow(n, now, highlighted = page == 0 && i == 0, height = rowHeight) { onOpen(n) }
                        }
                    }
                }
                if (pages.size > 1) {
                    Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        repeat(pages.size) { i ->
                            Box(
                                Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(if (i == pager.currentPage) 7.dp else 5.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = if (i == pager.currentPage) 0.9f else 0.3f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StackRow(n: NotchNotification, now: Long, highlighted: Boolean, height: Dp, onClick: () -> Unit) {
    val accent = n.accent?.let { Color(it) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .glassCard(16.dp, if (highlighted) accent else null, glow = false)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NotificationAvatar(n, 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n.title, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(relativeTime(n.postTime, now), color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.labelSmall)
            }
            n.text?.let {
                Text(it.replace('\n', ' '), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
