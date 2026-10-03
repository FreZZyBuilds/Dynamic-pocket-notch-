package com.frezzybuilds.devnotch

import com.frezzybuilds.devnotch.service.NotchAccessibilityService
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.data.settings.NotchSettings
import com.frezzybuilds.devnotch.notify.LockContent
import com.frezzybuilds.devnotch.notify.NotchNotification
import com.frezzybuilds.devnotch.notify.NotifyPrefs
import com.frezzybuilds.devnotch.notify.NotificationStyle
import androidx.compose.foundation.layout.height
import com.frezzybuilds.devnotch.peek.Peek
import com.frezzybuilds.devnotch.peek.PeekCenter
import com.frezzybuilds.devnotch.service.LockscreenMode
import com.frezzybuilds.devnotch.ui.ExpandedSize
import com.frezzybuilds.devnotch.ui.glass.CollapsibleCard
import com.frezzybuilds.devnotch.ui.glass.Glass
import com.frezzybuilds.devnotch.ui.theme.Brand

/**
 * Einklappbare Einstellungs-Karte; ob sie offen ist, bleibt über App-Starts gespeichert.
 * [forceOpen] klappt sie auf (z. B. beim Sprung aus der Notch zu dieser Karte).
 */
@Composable
internal fun SettingsSection(
    id: String,
    icon: String,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    defaultOpen: Boolean = false,
    forceOpen: Boolean = false,
    neon: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val settings = LocalContext.current.appContainer.notchSettings
    var open by remember(id) { mutableStateOf(settings.isSectionOpen(id, defaultOpen)) }
    LaunchedEffect(forceOpen) {
        if (forceOpen && !open) {
            open = true
            settings.setSectionOpen(id, true)
        }
    }
    CollapsibleCard(
        icon = icon,
        title = title,
        subtitle = subtitle,
        expanded = open,
        onToggle = {
            open = !open
            settings.setSectionOpen(id, open)
        },
        modifier = modifier,
        neon = neon,
        content = content
    )
}

/** Zeile mit Titel, Erklärung und Schalter; die ganze Zeile ist antippbar. */
@Composable
internal fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .alpha(if (enabled) 1f else 0.45f)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Brand.Lilac, checkedThumbColor = Color.White)
        )
    }
}

/** Größe des aufgeklappten Dashboards; wirkt sofort in der laufenden Notch. */
@Composable
internal fun NotchSizeSettings(settings: NotchSettings) {
    var width by remember { mutableFloatStateOf(settings.dashboardWidthDp.toFloat()) }
    var height by remember { mutableFloatStateOf(settings.dashboardHeightDp.toFloat()) }
    SettingLabel("Größe aufgeklappt")
    ValueSlider(
        label = "Breite",
        valueText = "${width.toInt()} dp",
        value = width,
        range = ExpandedSize.DASHBOARD_WIDTH_RANGE.first.toFloat()..ExpandedSize.DASHBOARD_WIDTH_RANGE.last.toFloat(),
        onChange = { width = it },
        onDone = { settings.dashboardWidthDp = width.toInt() }
    )
    ValueSlider(
        label = "Höhe",
        valueText = "${height.toInt()} dp",
        value = height,
        range = ExpandedSize.DASHBOARD_HEIGHT_RANGE.first.toFloat()..ExpandedSize.DASHBOARD_HEIGHT_RANGE.last.toFloat(),
        onChange = { height = it },
        onDone = { settings.dashboardHeightDp = height.toInt() }
    )
    Text(
        "Tipp: Am Griff unten im aufgeklappten Dashboard ziehen ändert die Höhe direkt. " +
            "Die Notch bleibt immer kleiner als der Bildschirm.",
        color = Glass.TextSecondary,
        style = MaterialTheme.typography.bodySmall
    )
}

/** Benachrichtigungen in der Notch: an/aus, Dauer, Filter und App-Liste. */
@Composable
internal fun NotificationSettings(settings: NotchSettings, hasListenerAccess: Boolean, onGrantAccess: () -> Unit) {
    var enabled by remember { mutableStateOf(settings.notifyEnabled) }
    var duration by remember { mutableFloatStateOf(settings.notifyDuration) }
    var skipOngoing by remember { mutableStateOf(settings.notifySkipOngoing) }
    var skipSilent by remember { mutableStateOf(settings.notifySkipSilent) }
    var respectDnd by remember { mutableStateOf(settings.notifyRespectDnd) }
    var blocked by remember { mutableStateOf(settings.notifyBlockedApps) }
    var style by remember { mutableStateOf(settings.notifyStyle) }

    if (!hasListenerAccess) {
        HintBox(
            "Benachrichtigungszugriff fehlt. Ohne ihn sieht die Notch keine Benachrichtigungen, " +
                "Anrufe oder Navigation.",
            action = "Zugriff erteilen",
            onAction = onGrantAccess
        )
    }
    SwitchRow("In der Notch anzeigen", "Neue Benachrichtigungen gleiten kurz aus der Notch", enabled) {
        enabled = it
        settings.notifyEnabled = it
    }
    AnimatedVisibility(enabled) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingLabel("Stil")
            // Zwei Reihen à zwei: vier Kacheln in einer Reihe wären auf schmalen Handys zu eng.
            NotificationStyle.entries.chunked(2).forEach { row ->
                ChoiceRow(row, style, { it.label }) { style = it; settings.notifyStyle = it }
            }
            Text(style.description, color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall)
            NotificationStylePreview(style)
            ValueSlider(
                label = "Anzeigedauer",
                valueText = "%.1f s".format(java.util.Locale.GERMANY, duration),
                value = duration,
                range = NotifyPrefs.DURATION_RANGE,
                onChange = { duration = it },
                onDone = { settings.notifyDuration = duration }
            )
            SwitchRow("Laufende ausblenden", "Downloads, Dienste und andere Dauer-Benachrichtigungen", skipOngoing) {
                skipOngoing = it
                settings.notifySkipOngoing = it
            }
            SwitchRow("Lautlose ausblenden", "Nur Benachrichtigungen mit normaler oder hoher Wichtigkeit", skipSilent) {
                skipSilent = it
                settings.notifySkipSilent = it
            }
            SwitchRow("„Nicht stören“ beachten", "Bei aktivem Nicht-stören-Modus keine Peeks", respectDnd) {
                respectDnd = it
                settings.notifyRespectDnd = it
            }
            AppFilter(settings, blocked) {
                blocked = it
                settings.notifyBlockedApps = it
            }
            val context = LocalContext.current
            HintBox(
                "Nachricht doppelt (Notch und Android-Pop-up)? Schalte bei der App die Pop-ups " +
                    "(„Pop-up-Benachrichtigungen“ bzw. „Auf Bildschirm einblenden“) aus – die Notch zeigt sie dann allein.",
                action = "Android öffnen"
            ) { openSystemNotificationSettings(context) }
        }
    }
}

/** So sieht eine Nachricht im gewählten Stil aus (echte Peek-Darstellung, verkleinert ohne Kamerazeile). */
@Composable
private fun NotificationStylePreview(style: NotificationStyle) {
    val sample = remember {
        NotchNotification(
            key = "preview", packageName = "org.telegram.messenger", appLabel = "Telegram",
            title = "Lena", text = "Kommst du heute Abend mit ins Kino? 🍿",
            postTime = System.currentTimeMillis(), accent = 0xFF2AABEE.toInt()
        )
    }
    val peek = Peek.Notification(sample, 0L, style, more = 2)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height((peek.extraHeightDp + 8).dp)
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp, topStart = 12.dp, topEnd = 12.dp))
            .background(Color.Black)
    ) {
        com.frezzybuilds.devnotch.ui.PeekContent(peek, pillHeight = 6.dp, lensGap = 0.dp)
    }
}

/** Android-Benachrichtigungseinstellungen (dort lassen sich Pop-ups je App ausschalten). */
private fun openSystemNotificationSettings(context: Context) {
    val intents = listOf(
        android.content.Intent("android.settings.NOTIFICATION_SETTINGS"),
        android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
    )
    intents.firstOrNull { intent ->
        runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
}

/** Apps, die schon Benachrichtigungen geschickt haben – einzeln ein- und ausschaltbar. */
@Composable
private fun AppFilter(settings: NotchSettings, blocked: Set<String>, onChange: (Set<String>) -> Unit) {
    val apps = remember { settings.seenApps.entries.toList() }
    var showAll by remember { mutableStateOf(false) }
    SettingLabel("Apps")
    if (apps.isEmpty()) {
        Text(
            "Apps erscheinen hier, sobald sie eine Benachrichtigung geschickt haben.",
            color = Glass.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        return
    }
    val visible = if (showAll) apps else apps.take(APPS_PREVIEW)
    visible.forEach { (pkg, label) ->
        val allowed = pkg !in blocked
        SwitchRow(label, if (allowed) null else "Ausgeblendet", allowed) { on ->
            onChange(if (on) blocked - pkg else blocked + pkg)
        }
    }
    if (apps.size > APPS_PREVIEW) {
        Text(
            if (showAll) "Weniger anzeigen" else "Alle ${apps.size} Apps anzeigen",
            color = Brand.Cyan,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button) { showAll = !showAll }
                .padding(vertical = 6.dp)
        )
    }
}

private const val APPS_PREVIEW = 6

/** Live-Ansichten: welche laufenden Vorgänge die Notch dauerhaft zeigt. */
@Composable
internal fun LiveViewSettings(settings: NotchSettings) {
    var calls by remember { mutableStateOf(settings.liveCalls) }
    var navigation by remember { mutableStateOf(settings.liveNavigation) }
    var timers by remember { mutableStateOf(settings.liveTimers) }
    var progress by remember { mutableStateOf(settings.liveProgress) }
    Text(
        "Laufende Vorgänge bleiben in der Pille sichtbar und öffnen sich als Karte im Dashboard.",
        color = Glass.TextSecondary,
        style = MaterialTheme.typography.bodySmall
    )
    SwitchRow("📞 Anrufe", "Annehmen und Ablehnen direkt in der Notch, Gesprächsdauer", calls) {
        calls = it; settings.liveCalls = it
    }
    DirectCallPermission()
    Text(
        "Im Gespräch: Stumm, Lautsprecher, Halten, Wahltasten (Töne ins Gespräch) und Auflegen direkt in der Notch. " +
            "Android bindet DevNotch dafür als Begleit-App an laufende Anrufe (wie eine Smartwatch) – ab Android 10.",
        color = Glass.TextSecondary,
        style = MaterialTheme.typography.bodySmall
    )
    SwitchRow("🧭 Navigation", "Nächste Abbiegung aus Google Maps, Waze, HERE und anderen", navigation) {
        navigation = it; settings.liveNavigation = it
    }
    SwitchRow("⏱ Timer & Stoppuhren", "Countdowns aus Uhr-Apps", timers) {
        timers = it; settings.liveTimers = it
    }
    SwitchRow("⬇ Fortschritt", "Downloads, Uploads und Exporte mit Fortschrittsring", progress) {
        progress = it; settings.liveProgress = it
    }
}

/** Sperrbildschirm: Notch anzeigen oder nicht, und wie viel Benachrichtigungsinhalt. */
@Composable
internal fun LockscreenSettings(settings: NotchSettings) {
    var mode by remember { mutableStateOf(settings.lockscreenMode) }
    var content by remember { mutableStateOf(settings.notifyLockContent) }
    val context = LocalContext.current
    var a11yOn by remember { mutableStateOf(NotchAccessibilityService.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        a11yOn = NotchAccessibilityService.isEnabled(context)
        onPauseOrDispose { }
    }
    var showDisclosure by remember { mutableStateOf(false) }

    SettingLabel("Notch auf dem Sperrbildschirm")
    ChoiceRow(LockscreenMode.entries, mode, { it.label }) { mode = it; settings.lockscreenMode = it }
    Text(
        when (mode) {
            LockscreenMode.SHOW -> "Musik, Timer, Mitteilungen und Peeks bleiben sichtbar. Notizen, Zwischenablage & Co. nur, wenn du es unten erlaubst."
            LockscreenMode.HIDE -> "Die Notch verschwindet beim Sperren und kommt beim Entsperren zurück."
        },
        color = Glass.TextSecondary,
        style = MaterialTheme.typography.bodySmall
    )
    if (mode == LockscreenMode.SHOW) {
        var full by remember { mutableStateOf(settings.lockscreenFull) }
        SwitchRow(
            "Alles auf dem Sperrbildschirm",
            "Gleiche Übersicht wie entsperrt: auch Notizen, Clip, Telefon, Dev und AI. Jeder, der dein Handy hat, sieht sie.",
            full
        ) {
            full = it
            settings.lockscreenFull = it
        }
        if (a11yOn) {
            Text("✓ Bedienungshilfe aktiv – die Notch erscheint über der Sperre.", color = Brand.Cyan, style = MaterialTheme.typography.bodySmall)
        } else {
            HintBox(
                "Über dem Sperrbildschirm erlaubt Android nur Fenster von Bedienungshilfen. " +
                    "Schalte dafür „DevNotch Overlay“ ein.",
                action = "Einrichten",
                onAction = { showDisclosure = true }
            )
        }
    }
    if (showDisclosure) AccessibilityDisclosure(onDismiss = { showDisclosure = false })
    AnimatedVisibility(mode == LockscreenMode.SHOW) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingLabel("Inhalt von Benachrichtigungen")
            ChoiceRow(LockContent.entries, content, { it.label }) { content = it; settings.notifyLockContent = it }
            Text(
                when (content) {
                    LockContent.FULL -> "Absender und Text sind auch gesperrt lesbar."
                    LockContent.APP_ONLY -> "Gesperrt nur App und „Neue Benachrichtigung“."
                    LockContent.HIDDEN -> "Gesperrt keine Benachrichtigungs-Peeks."
                },
                color = Glass.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** Schnellaktionen: Peeks testen und Größe zurücksetzen – als Kachel-Raster. */
@Composable
internal fun QuickActions(settings: NotchSettings, notchRunning: Boolean) {
    val context = LocalContext.current
    var sizeReset by remember { mutableIntStateOf(0) }
    if (!notchRunning) {
        Text(
            "Schalte die Notch ein, um die Vorschauen zu sehen.",
            color = Glass.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionTile("🔔", "Benachrichtigung", "Test-Peek zeigen", notchRunning, Modifier.weight(1f)) {
            PeekCenter.show(Peek.Notification(testNotification(context), (settings.notifyDuration * 1000).toLong(), settings.notifyStyle, more = 2))
        }
        ActionTile("⚡", "Laden", "Lade-Animation", notchRunning, Modifier.weight(1f)) {
            PeekCenter.show(Peek.Charging(percent = 80))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionTile("📋", "Kopiert", "Clip-Peek zeigen", notchRunning, Modifier.weight(1f)) {
            PeekCenter.show(Peek.Copied("Hallo von DevNotch ✦"))
        }
        ActionTile("↺", "Größe", if (sizeReset > 0) "Zurückgesetzt ✓" else "Standard wiederherstellen", true, Modifier.weight(1f)) {
            settings.dashboardWidthDp = ExpandedSize.DASHBOARD_MAX_WIDTH_DP
            settings.dashboardHeightDp = ExpandedSize.DASHBOARD_HEIGHT_DP
            sizeReset++
        }
    }
}

private fun testNotification(context: Context) = NotchNotification(
    key = "devnotch:test",
    packageName = context.packageName,
    appLabel = "DevNotch",
    title = "So sehen Benachrichtigungen aus",
    text = "Tippe zum Öffnen oder auf ✕ zum Schließen. Dauer und Filter stellst du in den Einstellungen ein.",
    postTime = System.currentTimeMillis()
)

/** Kachel für eine Schnellaktion: Symbol, Titel, Kurzinfo. */
@Composable
private fun ActionTile(icon: String, title: String, subtitle: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(14.dp)
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Brand.Horizontal),
            contentAlignment = Alignment.Center
        ) { Text(icon, style = MaterialTheme.typography.titleSmall) }
        Spacer(Modifier.size(10.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Hinweis mit Aktion, z. B. fehlende Berechtigung. */
@Composable
private fun HintBox(text: String, action: String, onAction: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x33FFB74D))
            .border(1.dp, Color(0x66FFB74D), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(
            action,
            color = Color(0xFFFFB74D),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onAction).padding(6.dp)
        )
    }
}

/**
 * Deutliche Offenlegung vor dem Einschalten der Bedienungshilfe (Play-Vorgabe): wofür sie dient,
 * was sie nicht tut, wie man sie aktiviert.
 */
@Composable
internal fun AccessibilityDisclosure(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bedienungshilfe für die Notch") },
        text = {
            Text(
                "DevNotch nutzt die Bedienungshilfe-Schnittstelle (AccessibilityService) ausschließlich " +
                    "für ihr eigenes Fenster: damit die Notch über dem Sperrbildschirm erscheinen und " +
                    "auf Wunsch die Statusleisten-Symbole hinter der Pille verdecken darf.\n\n" +
                    "DevNotch liest dabei keine Bildschirminhalte, beobachtet keine Eingaben, " +
                    "steuert keine anderen Apps und sendet keine Daten.\n\n" +
                    "Tippe in den Einstellungen auf „Installierte Apps“ bzw. „Heruntergeladene Apps“ → " +
                    "„DevNotch Overlay“ und schalte es ein.\n\n" +
                    "Ist der Schalter ausgegraut (bei Installation außerhalb des Play Store): " +
                    "App-Info von DevNotch öffnen → ⋮ → „Eingeschränkte Einstellungen zulassen“."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                NotchAccessibilityService.openSettings(context)
            }) { Text("Zustimmen und öffnen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

/** Notch über der Statusleiste: verdeckt Uhr/Symbole hinter der Pille (braucht die Bedienungshilfe). */
@Composable
internal fun StatusBarCoverSetting(settings: NotchSettings) {
    val context = LocalContext.current
    var cover by remember { mutableStateOf(settings.coverStatusBar) }
    var a11yOn by remember { mutableStateOf(NotchAccessibilityService.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        a11yOn = NotchAccessibilityService.isEnabled(context)
        onPauseOrDispose { }
    }
    var showDisclosure by remember { mutableStateOf(false) }
    SwitchRow(
        "Statusleiste überdecken",
        "Die Notch liegt über Uhr und Symbolen: Was hinter der Pille steht, wird verdeckt – wie bei Apple.",
        cover
    ) {
        cover = it
        settings.coverStatusBar = it
        if (it && !a11yOn) showDisclosure = true
    }
    if (cover && !a11yOn) {
        HintBox(
            "Android erlaubt Fenster über der Statusleiste nur Bedienungshilfen. Schalte „DevNotch Overlay“ ein.",
            action = "Einrichten",
            onAction = { showDisclosure = true }
        )
    }
    if (showDisclosure) AccessibilityDisclosure(onDismiss = { showDisclosure = false })
}

/** Live-Ansichten als Banner unter der Kamera (wie Apples Live-Aktivitäten) oder nur klein. */
@Composable
internal fun LiveBannerSetting(settings: NotchSettings) {
    var banner by remember { mutableStateOf(settings.liveBanner) }
    SwitchRow(
        "Live-Banner unter der Notch",
        "Navigation, Anrufe und Timer groß direkt unter der Kamera. Nach oben wischen macht sie klein.",
        banner
    ) {
        banner = it
        settings.liveBanner = it
    }
    AnimatedVisibility(banner) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var hide by remember { mutableIntStateOf(settings.callAutoHideSeconds) }
            SettingLabel("Anruf-Banner einklappen")
            ChoiceRow(listOf(5, 10, 0), hide, { if (it == 0) "Nie" else "nach $it s" }) {
                hide = it
                settings.callAutoHideSeconds = it
            }
            Text(
                "Während des Gesprächs rückt das Banner zurück in die Pille (Name und Dauer bleiben sichtbar). " +
                    "Antippen holt es mit Steuerung und Auflegen wieder hervor.",
                color = Glass.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** Systemereignisse wie auf dem iPhone – jedes einzeln an/aus. */
@Composable
internal fun SystemEventSettings(settings: NotchSettings) {
    var off by remember { mutableStateOf(settings.systemEventsOff) }
    Text(
        "Kurze Hinweise aus der Notch wie bei Apples Dynamic Island. Keine Berechtigung nötig.",
        color = Glass.TextSecondary,
        style = MaterialTheme.typography.bodySmall
    )
    com.frezzybuilds.devnotch.system.SystemEvent.entries.forEach { event ->
        SwitchRow(event.label, event.description, event.name !in off) { on ->
            settings.setSystemEvent(event, on)
            off = settings.systemEventsOff
        }
    }
}

/** Teilen wie beim iPhone: NameDrop (NFC/QR), AirDrop-Ersatz LocalSend und Wallet. */
@Composable
internal fun ShareSettings(settings: NotchSettings) {
    val context = LocalContext.current
    var card by remember { mutableStateOf(settings.contactCard) }
    var receive by remember { mutableStateOf(settings.localSendReceive) }
    var alias by remember { mutableStateOf(settings.localSendAlias) }
    val nfc = remember { com.frezzybuilds.devnotch.share.NameDropSession.nfcEnabled(context) }
    val wallet = remember { com.frezzybuilds.devnotch.share.Wallet.installedApp(context) }

    SettingLabel("NameDrop – deine Kontaktkarte")
    androidx.compose.material3.OutlinedTextField(
        value = card.name, onValueChange = { card = card.copy(name = it) },
        label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
    )
    androidx.compose.material3.OutlinedTextField(
        value = card.phone, onValueChange = { card = card.copy(phone = it) },
        label = { Text("Telefon") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
    )
    androidx.compose.material3.OutlinedTextField(
        value = card.email, onValueChange = { card = card.copy(email = it) },
        label = { Text("E-Mail") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
    )
    Text(
        when (nfc) {
            null -> "Dieses Gerät hat kein NFC – NameDrop zeigt dann einen QR-Code (iPhone-Kamera erkennt ihn als Kontakt)."
            false -> "NFC ist aus. Einschalten, um Handys aneinanderzuhalten – sonst geht nur der QR-Code."
            true -> "Notch → 👤: Handy oben an ein anderes Android-Handy halten. Für iPhones gibt es den QR-Code."
        },
        color = Glass.TextSecondary,
        style = MaterialTheme.typography.bodySmall
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.frezzybuilds.devnotch.ui.glass.GradientButton(
            if (card == settings.contactCard) "NameDrop starten" else "Speichern & starten",
            onClick = {
                settings.contactCard = card
                com.frezzybuilds.devnotch.share.ShareActions.startNameDrop(context)
            }
        )
        if (nfc == false) {
            com.frezzybuilds.devnotch.ui.glass.GlassButton("NFC einschalten", onClick = {
                runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NFC_SETTINGS)) }
            })
        }
    }

    SettingLabel("AirDrop – über LocalSend")
    SwitchRow(
        "Empfangen",
        "Für LocalSend im selben WLAN sichtbar (iPhone, Mac, Windows, Linux, Android). Jede Übertragung musst du in der Notch annehmen.",
        receive
    ) {
        receive = it
        settings.localSendReceive = it
    }
    androidx.compose.material3.OutlinedTextField(
        value = alias,
        onValueChange = { alias = it; settings.localSendAlias = it },
        label = { Text("Gerätename") },
        placeholder = { Text("DevNotch (${android.os.Build.MODEL})") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    com.frezzybuilds.devnotch.ui.glass.GlassButton("Dateien senden", onClick = { com.frezzybuilds.devnotch.share.ShareActions.pickAndSend(context) })
    Text(
        "Senden geht auch aus jeder App: Teilen → „LocalSend (DevNotch)“. Empfangenes landet in Downloads/DevNotch.",
        color = Glass.TextSecondary,
        style = MaterialTheme.typography.bodySmall
    )

    SettingLabel("Google Pay – wie Apple Pay")
    Text(
        if (wallet != null) "Nach einer Zahlung mit Google Pay (Google Wallet/GPay) oder Samsung Wallet zeigt die Notch „Bezahlt“ mit Betrag. Notch → 💳 öffnet Google Pay."
        else "Google Pay (Google Wallet/GPay) nicht gefunden. Damit zeigt die Notch nach jeder Zahlung „Bezahlt“ mit Betrag.",
        color = Glass.TextSecondary,
        style = MaterialTheme.typography.bodySmall
    )
    if (wallet != null) {
        com.frezzybuilds.devnotch.ui.glass.GlassButton("Google Pay öffnen", onClick = { com.frezzybuilds.devnotch.share.Wallet.open(context) })
    }
}

/** Telefon-Tab: direkt wählen erlauben (sonst öffnet die Telefon-App mit der Nummer). */
@Composable
private fun DirectCallPermission() {
    val context = LocalContext.current
    fun granted() = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    var allowed by remember { mutableStateOf(granted()) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { allowed = it }
    if (allowed) {
        Text("✓ Telefon-Tab wählt direkt", color = Brand.Cyan, style = MaterialTheme.typography.bodySmall)
    } else {
        HintBox(
            "Telefon-Tab in der Notch: Ohne Erlaubnis öffnet „Anrufen“ die Telefon-App mit der Nummer.",
            action = "Direkt wählen",
            onAction = { launcher.launch(android.Manifest.permission.CALL_PHONE) }
        )
    }
}
