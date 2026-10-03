package com.frezzybuilds.devnotch.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frezzybuilds.devnotch.share.ShareActions
import com.frezzybuilds.devnotch.share.Wallet
import com.frezzybuilds.devnotch.ui.theme.Brand

/** Aussehen einer Kategorie-Kachel in der Übersicht. */
/** [title] kurz für die Kachel, [longTitle] im Kopf der geöffneten Ansicht. */
internal class SectionLook(val symbol: String, val title: String, val colors: List<Color>, val longTitle: String = title)

internal fun NotchTab.look(): SectionLook = when (this) {
    NotchTab.INBOX -> SectionLook("🔔", "Neu", listOf(Color(0xFFFF375F), Color(0xFFFF9F0A)), "Mitteilungen")
    NotchTab.TIMER -> SectionLook("⏱", "Timer", listOf(Brand.Magenta, Brand.Violet))
    NotchTab.PHONE -> SectionLook("📞", "Telefon", listOf(Color(0xFF30D158), Color(0xFF00C7BE)))
    NotchTab.NOTES -> SectionLook("📝", "Notizen", listOf(Color(0xFFFFD60A), Color(0xFFFF9F0A)))
    NotchTab.CLIP -> SectionLook("📋", "Clip", listOf(Brand.Cyan, Color(0xFF0A84FF)), "Zwischenablage")
    NotchTab.DEV -> SectionLook("💻", "Dev", listOf(Color(0xFF5E5CE6), Brand.Cyan))
    NotchTab.AI -> SectionLook("✨", "AI", listOf(Brand.Violet, Brand.Magenta), "AI-Kosten")
}

/** Reihenfolge in der Übersicht: Alltägliches zuerst. */
internal val HomeOrder = listOf(NotchTab.INBOX, NotchTab.TIMER, NotchTab.PHONE, NotchTab.NOTES, NotchTab.CLIP, NotchTab.DEV, NotchTab.AI)

/** Leichtes Eindrücken beim Antippen (Federung in der Layer-Phase, ohne Recomposition je Bild). */
@Composable
internal fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.55f, stiffness = 700f), label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Übersicht beim Herunterziehen: Kacheln je Kategorie (mit kurzer Live-Info) und Schnellaktionen
 * zum Teilen. Erst ein Tipp auf eine Kachel öffnet die jeweilige Ansicht.
 */
@Composable
internal fun DashboardHome(
    tabs: List<NotchTab>,
    subtitle: (NotchTab) -> String,
    badge: (NotchTab) -> Int,
    onOpen: (NotchTab) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Kein Scrollen: Die Kacheln passen sich der Höhe an – so bleibt Hochwischen zum Schließen frei.
    BoxWithConstraints(modifier) {
        val columns = if (maxWidth >= 320.dp) 3 else 2
        val rows = ((tabs.size + columns - 1) / columns).coerceAtLeast(1)
        val gap = 8.dp
        val shareRow = 38.dp
        // Hoch genug für Symbol + Titel + Info auch bei großer Systemschrift (Samsung).
        val density = androidx.compose.ui.platform.LocalDensity.current
        val tallNeeded = 20.dp + 26.dp + 4.dp + with(density) { 20.sp.toDp() + 16.sp.toDp() } + 2.dp
        val cardHeight = ((maxHeight - shareRow - gap * rows) / rows).coerceIn(44.dp, maxOf(104.dp, tallNeeded))
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            tabs.chunked(columns).forEachIndexed { row, rowTabs ->
                Row(Modifier.fillMaxWidth().staggerIn(1 + row), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rowTabs.forEach { tab -> SectionCard(tab, subtitle(tab), badge(tab), cardHeight, Modifier.weight(1f)) { onOpen(tab) } }
                    repeat(columns - rowTabs.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            ShareActionRow(onClose, Modifier.staggerIn(4))
        }
    }
}

@Composable
private fun SectionCard(tab: NotchTab, subtitle: String, badge: Int, height: androidx.compose.ui.unit.Dp, modifier: Modifier, onClick: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Schrifthöhen inkl. Schriftgröße des Systems – sonst wird die Info-Zeile unten abgeschnitten.
    val titleLine = with(density) { 20.sp.toDp() }
    val infoLine = with(density) { 16.sp.toDp() }
    val look = tab.look()
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .height(height)
            .pressScale(interaction)
            .clip(shape)
            .background(Brush.linearGradient(listOf(look.colors.first().copy(alpha = 0.24f), Color.White.copy(alpha = 0.04f))))
            .border(1.dp, Brush.linearGradient(listOf(look.colors.first().copy(alpha = 0.55f), Color.White.copy(alpha = 0.06f))), shape)
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = look.title, onClick = onClick)
            .padding(10.dp)
    ) {
        // Hohe Kachel: Symbol über Titel und Info; niedrige: Symbol neben dem Titel.
        val tall = height >= 20.dp + 26.dp + 4.dp + titleLine + infoLine
        @Composable
        fun icon() = Box(Modifier.size(if (tall) 26.dp else 24.dp).clip(CircleShape).background(Brush.linearGradient(look.colors)), contentAlignment = Alignment.Center) {
            Text(look.symbol, style = MaterialTheme.typography.labelMedium)
        }
        @Composable
        fun texts() = Column {
            Text(look.title, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (height >= 20.dp + titleLine + infoLine) {
                Text(subtitle, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (tall) {
            Column { icon(); Spacer(Modifier.height(4.dp)); texts() }
        } else {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) { icon(); Spacer(Modifier.width(8.dp)); texts() }
        }
        if (badge > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).clip(CircleShape).background(Color(0xFFFF375F)).padding(horizontal = 6.dp, vertical = 1.dp)
            ) { Text(if (badge > 99) "99+" else badge.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
        }
    }
}

/** NameDrop, Dateien senden (LocalSend) und Wallet als Knöpfe mit Beschriftung. */
@Composable
private fun ShareActionRow(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val wallet = remember { Wallet.installedApp(context) != null }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ShareButton("👤", "NameDrop", Modifier.weight(1f)) { onClose(); ShareActions.startNameDrop(context) }
        ShareButton("📤", "Senden", Modifier.weight(1f)) { onClose(); ShareActions.pickAndSend(context) }
        if (wallet) ShareButton("💳", "Pay", Modifier.weight(1f)) { onClose(); Wallet.open(context) }
    }
}

@Composable
private fun ShareButton(symbol: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(38.dp)
            .pressScale(interaction)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(symbol, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Kopf einer geöffneten Kategorie: zurück zur Übersicht, Symbol und Titel. */
@Composable
internal fun SectionTopBar(tab: NotchTab, onBack: (() -> Unit)?) {
    val look = tab.look()
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            val interaction = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .pressScale(interaction)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable(interaction, indication = null, role = Role.Button, onClickLabel = "Zur Übersicht", onClick = onBack)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("‹", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Text("Übersicht", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.size(22.dp).clip(CircleShape).background(Brush.linearGradient(look.colors)), contentAlignment = Alignment.Center) {
            Text(look.symbol, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(6.dp))
        Text(look.longTitle, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
