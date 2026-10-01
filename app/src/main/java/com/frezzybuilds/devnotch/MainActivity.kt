package com.frezzybuilds.devnotch

import android.Manifest
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import com.frezzybuilds.devnotch.ui.BeamStyle
import com.frezzybuilds.devnotch.ui.BeamLook
import androidx.compose.runtime.mutableFloatStateOf
import com.frezzybuilds.devnotch.ui.rememberBeamPosition
import com.frezzybuilds.devnotch.ui.borderBeam
import com.frezzybuilds.devnotch.ui.BeamPalette
import com.frezzybuilds.devnotch.ui.BeamMode
import com.frezzybuilds.devnotch.ui.theme.Brand
import com.frezzybuilds.devnotch.ui.staggerIn
import com.frezzybuilds.devnotch.ui.glass.gradientText
import com.frezzybuilds.devnotch.ui.glass.StatusDot
import com.frezzybuilds.devnotch.ui.glass.SetupStep
import com.frezzybuilds.devnotch.ui.glass.SetupChecklist
import com.frezzybuilds.devnotch.ui.glass.SectionHeader
import com.frezzybuilds.devnotch.ui.glass.PowerOrb
import com.frezzybuilds.devnotch.ui.glass.HeroHeader
import com.frezzybuilds.devnotch.ui.glass.GradientButton
import com.frezzybuilds.devnotch.ui.glass.GlassCard
import com.frezzybuilds.devnotch.ui.glass.GlassButton
import com.frezzybuilds.devnotch.ui.glass.Glass
import com.frezzybuilds.devnotch.ui.glass.CardPadding
import com.frezzybuilds.devnotch.ui.glass.AuroraBackground
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.frezzybuilds.devnotch.feature.aiusage.AiProvider
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.os.PowerManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import com.frezzybuilds.devnotch.data.settings.NotchSettings
import com.frezzybuilds.devnotch.ui.media.EdgeTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frezzybuilds.devnotch.service.NotchLayoutMode
import com.frezzybuilds.devnotch.service.NotchOverlayService
import com.frezzybuilds.devnotch.service.OemGuide
import com.frezzybuilds.devnotch.service.openSettings
import com.frezzybuilds.devnotch.service.isTablet
import com.frezzybuilds.devnotch.feature.billing.Paywall
import com.frezzybuilds.devnotch.feature.billing.PaywallHost
import com.frezzybuilds.devnotch.feature.billing.ProFeature

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Immer dunkle Glas-Optik: helle Statusleisten-Symbole auf transparentem Grund.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        // Ab Android 13 nötig, damit die Foreground-Service-Benachrichtigung sichtbar ist.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        paywallFeature = Paywall.featureFrom(intent)
        setupSection = Setup.sectionFrom(intent)
        setContent {
            MaterialTheme(colorScheme = Glass.AppScheme) {
                AuroraBackground {
                Scaffold(containerColor = Color.Transparent, contentColor = Color.White) { padding ->
                    SetupScreen(
                        Modifier.padding(padding),
                        focusSection = setupSection,
                        onSectionShown = { setupSection = null }
                    ) { feature ->
                        paywallFeature = feature
                        showPaywall = true
                    }
                }
                if (showPaywall) {
                    PaywallHost(highlight = paywallFeature) {
                        showPaywall = false
                        paywallFeature = null
                    }
                }
                }
            }
        }
        if (paywallFeature != null) showPaywall = true
    }

    // Paywall-Wunsch aus dem Overlay (Paywall.open) – singleTop liefert ihn hier ab.
    private var paywallFeature by mutableStateOf<ProFeature?>(null)
    private var showPaywall by mutableStateOf(false)

    // Einrichten-Wunsch aus der Notch (Setup.open): zur passenden Karte scrollen.
    private var setupSection by mutableStateOf<Setup.Section?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Paywall.featureFrom(intent)?.let {
            paywallFeature = it
            showPaywall = true
        }
        Setup.sectionFrom(intent)?.let { setupSection = it }
    }

    override fun onResume() {
        super.onResume()
        // Kauf oder Lizenzwechsel außerhalb der App (z. B. im Play Store) übernehmen.
        lifecycleScope.launch { appContainer.billing.refresh() }
    }
}

// BringIntoViewRequester: Sprung aus der Notch zur passenden Karte.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SetupScreen(
    modifier: Modifier = Modifier,
    focusSection: Setup.Section? = null,
    onSectionShown: () -> Unit = {},
    onOpenPaywall: (ProFeature?) -> Unit = {}
) {
    val context = LocalContext.current
    val settings = context.appContainer.notchSettings

    // Bei jeder Rückkehr aus den Systemeinstellungen neu prüfen – ein einmaliges
    // remember { canDrawOverlays() } würde die frisch erteilte Berechtigung nicht bemerken.
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasListenerAccess by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var batteryExempt by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    LifecycleResumeEffect(Unit) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        hasListenerAccess = isNotificationListenerEnabled(context)
        batteryExempt = isIgnoringBatteryOptimizations(context)
        onPauseOrDispose { }
    }
    var showBatteryDialog by remember { mutableStateOf(false) }

    // Echter Service-Zustand statt lokaler Variable: stimmt auch nach Neustart der App.
    val isServiceRunning by NotchOverlayService.isRunning.collectAsStateWithLifecycle()
    var displayMode by remember { mutableStateOf(settings.displayMode) }
    // Tablets nutzen immer das Edge-Layout (siehe AdaptiveLayout).
    val isTablet = remember { context.isTablet() }

    fun toggleNotch() {
        val enable = !isServiceRunning
        // Merken für den Autostart nach dem Neustart (BootReceiver).
        settings.notchEnabled = enable
        if (enable) {
            NotchOverlayService.start(context)
            // Ohne Ausnahme kann Android die Notch im Leerlauf beenden.
            if (!batteryExempt) showBatteryDialog = true
        } else {
            NotchOverlayService.stop(context)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeroHeader(Modifier.staggerIn(0))

        // Ein/Aus: großer Neon-Knopf, sobald die Overlay-Berechtigung da ist.
        GlassCard(Modifier.fillMaxWidth().staggerIn(1), neon = isServiceRunning) {
            Row(CardPadding, verticalAlignment = Alignment.CenterVertically) {
                PowerOrb(
                    active = isServiceRunning,
                    onToggle = { if (hasOverlayPermission) toggleNotch() else openOverlaySettings(context) }
                )
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(isServiceRunning)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isServiceRunning) "Notch läuft" else "Notch ist aus",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        when {
                            !hasOverlayPermission -> "Tippe, um „Über anderen Apps einblenden“ zu erlauben."
                            isServiceRunning -> "Läuft im Hintergrund – tippe zum Ausschalten."
                            else -> "Tippe auf den Knopf, um sie zu starten."
                        },
                        color = Glass.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        SetupChecklist(
            steps = listOf(
                SetupStep(
                    title = "Über Apps einblenden",
                    done = hasOverlayPermission,
                    doneText = "Die Notch darf über allen Apps schweben.",
                    todoText = "Nötig, damit die Notch angezeigt wird.",
                    action = "Erlauben",
                    onAction = { openOverlaySettings(context) }
                ),
                SetupStep(
                    title = "Benachrichtigungen",
                    done = hasListenerAccess,
                    doneText = "Musik, Benachrichtigungen und Live-Ansichten aktiv.",
                    todoText = "Für Musik, Benachrichtigungen, Anrufe und Navigation.",
                    action = "Erteilen",
                    onAction = { openNotificationListenerSettings(context) }
                ),
                SetupStep(
                    title = "Akku-Optimierung",
                    done = batteryExempt,
                    doneText = "Läuft zuverlässig im Hintergrund.",
                    todoText = "Sonst kann Android die Notch beenden.",
                    action = "Ausnehmen",
                    onAction = { showBatteryDialog = true }
                )
            ),
            modifier = Modifier.staggerIn(2)
        )

        SettingsSection(
            id = "actions",
            icon = "⚡",
            title = "Schnellaktionen",
            subtitle = "Peeks testen, Größe zurücksetzen",
            defaultOpen = true,
            modifier = Modifier.staggerIn(3)
        ) { QuickActions(settings, isServiceRunning) }

        SettingsSection(
            id = "look",
            icon = "◐",
            title = "Darstellung",
            subtitle = if (isTablet) "${NotchLayoutMode.EDGE_SIDE.label} – auf Tablets automatisch" else displayMode.label,
            modifier = Modifier.staggerIn(3)
        ) {
            if (!isTablet) {
                SettingLabel("Platzierung")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NotchLayoutMode.entries.forEach { mode ->
                        ModeChip(
                            label = if (mode == NotchLayoutMode.NOTCH_TOP) "Notch oben" else "Am Rand",
                            selected = mode == displayMode,
                            modifier = Modifier.weight(1f)
                        ) {
                            displayMode = mode
                            // Persistiert; ein laufender Service übernimmt die Änderung sofort.
                            settings.displayMode = mode
                        }
                    }
                }
            }
            AnimatedVisibility(visible = displayMode == NotchLayoutMode.NOTCH_TOP && !isTablet) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { NotchSizeSettings(settings) }
            }
            AnimatedVisibility(visible = displayMode == NotchLayoutMode.EDGE_SIDE || isTablet) {
                Column { EdgePlayerSettings(settings) }
            }
        }

        SettingsSection(
            id = "beam",
            icon = "✧",
            title = "Lichtlauf",
            subtitle = "Licht, das um die Notch kreist",
            modifier = Modifier.staggerIn(4)
        ) { BeamSettings(settings) }

        SettingsSection(
            id = "notify",
            icon = "🔔",
            title = "Benachrichtigungen",
            subtitle = if (hasListenerAccess) "Peeks aus der Notch, Filter pro App" else "Zugriff fehlt",
            modifier = Modifier.staggerIn(4)
        ) { NotificationSettings(settings, hasListenerAccess) { openNotificationListenerSettings(context) } }

        SettingsSection(
            id = "live",
            icon = "◉",
            title = "Live-Ansichten",
            subtitle = "Anrufe, Navigation, Timer, Fortschritt",
            modifier = Modifier.staggerIn(5)
        ) { LiveViewSettings(settings) }

        SettingsSection(
            id = "lock",
            icon = "🔒",
            title = "Sperrbildschirm",
            subtitle = "Was gesperrt sichtbar ist",
            modifier = Modifier.staggerIn(5)
        ) { LockscreenSettings(settings) }

        Box(Modifier.staggerIn(5)) { OemHintCard(settings) }

        Box(Modifier.staggerIn(6)) { ProStatusCard(onOpenPaywall) }

        val gitHubRequester = remember { BringIntoViewRequester() }
        val aiRequester = remember { BringIntoViewRequester() }
        LaunchedEffect(focusSection) {
            when (focusSection) {
                Setup.Section.GITHUB -> gitHubRequester.bringIntoView()
                Setup.Section.AI -> aiRequester.bringIntoView()
                null -> return@LaunchedEffect
            }
            onSectionShown()
        }

        GitHubCard(
            Modifier.bringIntoViewRequester(gitHubRequester).staggerIn(6),
            forceOpen = focusSection == Setup.Section.GITHUB
        )

        AiUsageCard(
            Modifier.bringIntoViewRequester(aiRequester).staggerIn(7),
            forceOpen = focusSection == Setup.Section.AI
        )

        Spacer(Modifier.height(24.dp))
    }

    if (showBatteryDialog) {
        BatteryOptimizationDialog(
            onConfirm = {
                showBatteryDialog = false
                requestIgnoreBatteryOptimizations(context)
            },
            onDismiss = { showBatteryDialog = false }
        )
    }
}

/**
 * Lichtlauf um die Notch: Modus, Stil, Farben, Tempo, Helligkeit und Länge – mit
 * Live-Vorschau. Änderungen wirken sofort in der laufenden Notch.
 */
@Composable
private fun BeamSettings(settings: NotchSettings) {
    var mode by remember { mutableStateOf(settings.beamMode) }
    var style by remember { mutableStateOf(settings.beamStyle) }
    var palette by remember { mutableStateOf(settings.beamPalette) }
    var lap by remember { mutableFloatStateOf(settings.beamLapSeconds) }
    var brightness by remember { mutableFloatStateOf(settings.beamBrightness) }
    var length by remember { mutableFloatStateOf(settings.beamLength) }
    val look = BeamLook(style, palette, lap, brightness, length)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Live-Vorschau: schwarze Pille mit dem gewählten Lichtlauf.
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            val position = rememberBeamPosition(running = mode != BeamMode.OFF, lapSeconds = lap)
            val strength = remember { mutableFloatStateOf(1f) }.also { it.floatValue = if (mode == BeamMode.OFF) 0f else 1f }
            val shape = RoundedCornerShape(26.dp)
            Box(
                Modifier
                    .size(240.dp, 52.dp)
                    .clip(shape)
                    .background(Color.Black)
                    .borderBeam(shape, position, strength, look)
            )
        }
        Text(
            when (mode) {
                BeamMode.ALWAYS -> "Kreist ständig um die Notch – kostet etwas mehr Akku."
                BeamMode.EVENTS -> "Nur beim Aufklappen und bei Peeks."
                BeamMode.OFF -> "Kein Lichtlauf."
            },
            color = Glass.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        ChoiceRow(BeamMode.entries, mode, { it.label }) { mode = it; settings.beamMode = it }
        SettingLabel("Stil")
        ChoiceRow(BeamStyle.entries, style, { it.label }) { style = it; settings.beamStyle = it }

        SettingLabel("Farben")
        BeamPalette.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { option ->
                    PaletteChip(option, selected = option == palette, modifier = Modifier.weight(1f)) {
                        palette = option
                        settings.beamPalette = option
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // Tempo: rechts = schneller (kürzere Runde).
        ValueSlider(
            label = "Tempo",
            valueText = "%.1f s pro Runde".format(java.util.Locale.GERMANY, lap),
            value = BeamLook.LAP_RANGE.endInclusive + BeamLook.LAP_RANGE.start - lap,
            range = BeamLook.LAP_RANGE,
            onChange = { lap = BeamLook.LAP_RANGE.endInclusive + BeamLook.LAP_RANGE.start - it },
            onDone = { settings.beamLapSeconds = lap }
        )
        ValueSlider(
            label = "Helligkeit",
            valueText = "${(brightness * 100).toInt()} %",
            value = brightness,
            range = BeamLook.BRIGHTNESS_RANGE,
            onChange = { brightness = it },
            onDone = { settings.beamBrightness = brightness }
        )
        ValueSlider(
            label = "Länge",
            valueText = "${(length * 100).toInt()} % des Rands",
            value = length,
            range = BeamLook.LENGTH_RANGE,
            onChange = { length = it },
            onDone = { settings.beamLength = length }
        )
    }
}

/** Zwischenüberschrift innerhalb einer Karte. */
@Composable
internal fun SettingLabel(text: String) {
    Text(text.uppercase(), color = Glass.TextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
}

/** Reihe aus Auswahl-Kacheln (eine aktiv). */
@Composable
internal fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            ModeChip(label(option), selected = option == selected, modifier = Modifier.weight(1f)) { onSelect(option) }
        }
    }
}

/** Paletten-Kachel: Farbverlauf als Balken plus Name. */
@Composable
private fun PaletteChip(palette: BeamPalette, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (selected) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.03f))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) SolidColor(Color.White.copy(alpha = 0.7f)) else SolidColor(Color.White.copy(alpha = 0.12f)), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp, 10.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(palette.colors))
        )
        Spacer(Modifier.width(10.dp))
        Text(palette.label, color = if (selected) Color.White else Glass.TextSecondary, style = MaterialTheme.typography.labelLarge)
    }
}

/** Regler mit Beschriftung und aktuellem Wert; gespeichert wird beim Loslassen. */
@Composable
internal fun ValueSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onDone: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueText, color = Glass.TextSecondary, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Brand.Lilac,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
            )
        )
    }
}

/** Auswahl-Kachel für die Platzierung: aktiv mit Neon-Rand und Verlaufstext. */
@Composable
internal fun ModeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.03f))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Brand.Horizontal else SolidColor(Color.White.copy(alpha = 0.12f)), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = if (selected) gradientText(MaterialTheme.typography.labelLarge) else MaterialTheme.typography.labelLarge,
            color = if (selected) Color.Unspecified else Glass.TextSecondary,
            fontWeight = FontWeight.SemiBold
        )
    }
}


@Composable
private fun GitHubCard(modifier: Modifier = Modifier, forceOpen: Boolean = false) {
    val settings = LocalContext.current.appContainer.gitHubSettings
    var token by remember { mutableStateOf(settings.token.orEmpty()) }
    var username by remember { mutableStateOf(settings.username.orEmpty()) }
    var saved by remember { mutableStateOf(token to username) }
    val isSaved = saved == (token to username) && token.isNotBlank()

    // Verbunden: kompakt mit „Bearbeiten“; sonst gleich die Felder.
    var editing by remember { mutableStateOf(settings.token.isNullOrBlank()) }
    SettingsSection(
        id = "github",
        icon = "</>",
        title = "GitHub-Heatmap",
        subtitle = if (saved.first.isNotBlank()) "Verbunden${saved.second.takeIf { it.isNotBlank() }?.let { " als @$it" } ?: ""}" else "Pro · Contributions und offene PRs in der Notch",
        modifier = modifier,
        forceOpen = forceOpen
    ) {
        Column(
            modifier = Modifier.animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!editing) {
                GlassButton("Bearbeiten", onClick = { editing = true })
                return@Column
            }
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Benutzername") },
                placeholder = { Text("leer = Inhaber des Tokens") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Personal Access Token") },
                supportingText = { Text("Die GraphQL-API verlangt einen Token, auch für öffentliche Profile.") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            GradientButton(if (isSaved) "Gespeichert ✓" else "Speichern", onClick = {
                settings.token = token
                settings.username = username
                saved = token to username
                if (token.isNotBlank()) editing = false
            })
        }
    }
}

/**
 * Nur auf Xiaomi/Samsung: Diese Hersteller beenden Hintergrund-Apps zusätzlich zur
 * Android-Akku-Optimierung. Schritt-für-Schritt-Anleitung plus Direktsprung in deren Einstellungen.
 */
@Composable
private fun OemHintCard(settings: NotchSettings) {
    val context = LocalContext.current
    val guide = remember { OemGuide.forDevice(Build.MANUFACTURER, Build.BRAND) } ?: return
    var done by remember { mutableStateOf(settings.oemHintDone) }

    GlassCard(Modifier.fillMaxWidth(), neon = !done) {
        if (done) {
            ListItem(
                headlineContent = { Text("${guide.vendor}: Hintergrund freigegeben") },
                supportingContent = { Text("Verschwindet die Notch trotzdem, die Schritte erneut prüfen.") },
                trailingContent = {
                    TextButton(onClick = {
                        done = false
                        settings.oemHintDone = false
                    }) { Text("Anzeigen") }
                }
            )
            return@GlassCard
        }
        Column(CardPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(icon = "!", title = "Wichtig für ${guide.vendor}", subtitle = "Damit die Notch dauerhaft läuft")
            Text(
                "Dein Gerät beendet Apps im Hintergrund zusätzlich zur Akku-Optimierung. " +
                    "Damit die Notch dauerhaft läuft:",
                style = MaterialTheme.typography.bodyMedium
            )
            guide.steps.forEachIndexed { index, step ->
                Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton("Einstellungen öffnen", onClick = { guide.openSettings(context) })
                GlassButton("Erledigt", onClick = {
                    done = true
                    settings.oemHintDone = true
                })
            }
        }
    }
}

/** Free/Pro-Status: Free = Focus-Timer + Basis-Notch, Pro schaltet den Rest frei. */
@Composable
private fun ProStatusCard(onOpenPaywall: (ProFeature?) -> Unit) {
    val proAccess = LocalContext.current.appContainer.proAccess
    val isPro by proAccess.isPro.collectAsStateWithLifecycle()
    GlassCard(Modifier.fillMaxWidth(), neon = true) {
        Column(CardPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (isPro) "DevNotch Pro ✦ aktiv" else "DevNotch Pro ✦",
                style = gradientText(MaterialTheme.typography.titleLarge),
                fontWeight = FontWeight.Black
            )
            Text(
                if (isPro) "GitHub-Heatmap, KI-Token-Tracker, unbegrenzte Zwischenablage und Projekt-Shortcuts sind freigeschaltet."
                else "Enthalten: Focus-Timer und Basis-Notch. Pro: GitHub-Heatmap, KI-Token-Tracker, " +
                    "unbegrenzte Zwischenablage & Projekt-Shortcuts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isPro) {
                GradientButton("Pro freischalten", onClick = { onOpenPaywall(null) })
            }
        }
    }
}

/**
 * AI-Nutzung: Keys für OpenAI (Admin-Key) und OpenRouter, Monatslimit und ob der Betrag
 * eingeklappt in der Notch erscheinen soll. Speichern löst sofort eine Abfrage aus.
 */
@Composable
private fun AiUsageCard(modifier: Modifier = Modifier, forceOpen: Boolean = false) {
    val container = LocalContext.current.appContainer
    val settings = container.aiUsageSettings
    val scope = rememberCoroutineScope()
    var openAiKey by remember { mutableStateOf(settings.key(AiProvider.OPENAI).orEmpty()) }
    var openRouterKey by remember { mutableStateOf(settings.key(AiProvider.OPENROUTER).orEmpty()) }
    var anthropicKey by remember { mutableStateOf(settings.key(AiProvider.ANTHROPIC).orEmpty()) }
    var geminiKey by remember { mutableStateOf(settings.key(AiProvider.GEMINI).orEmpty()) }
    var ollamaUrl by remember { mutableStateOf(settings.ollamaUrl.orEmpty()) }
    var ollamaKey by remember { mutableStateOf(settings.key(AiProvider.OLLAMA).orEmpty()) }
    var limit by remember { mutableStateOf("%.2f".format(java.util.Locale.US, settings.monthlyLimitUsd)) }
    var showInPill by remember { mutableStateOf(settings.showInPill) }
    var saved by remember { mutableStateOf(false) }

    val connected = buildList {
        if (openAiKey.isNotBlank()) add("OpenAI")
        if (anthropicKey.isNotBlank()) add("Anthropic")
        if (openRouterKey.isNotBlank()) add("OpenRouter")
        if (geminiKey.isNotBlank()) add("Gemini")
        if (ollamaUrl.isNotBlank()) add("Ollama")
    }
    var editing by remember { mutableStateOf(connected.isEmpty()) }
    SettingsSection(
        id = "ai",
        icon = "✦",
        title = "KI-Token-Tracker",
        subtitle = if (connected.isEmpty()) "Pro · Monatskosten deiner KI-Anbieter" else "${connected.size} verbunden · Limit \$$limit",
        modifier = modifier,
        forceOpen = forceOpen
    ) {
        Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (connected.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    connected.forEach { name ->
                        Text(
                            "$name ✓",
                            color = Brand.Cyan,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Brand.Cyan.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
            if (!editing) {
                GlassButton("Schlüssel bearbeiten", onClick = { editing = true })
                return@Column
            }
            OutlinedTextField(
                value = openAiKey,
                onValueChange = { openAiKey = it; saved = false },
                label = { Text("OpenAI Admin-Key (sk-admin-…)") },
                supportingText = {
                    Text("Nur Admin-Keys dürfen Kosten lesen: platform.openai.com → Organization → Admin keys. Der Key bleibt auf diesem Gerät.")
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = anthropicKey,
                onValueChange = { anthropicKey = it; saved = false },
                label = { Text("Anthropic Admin-Key (sk-ant-admin01-…)") },
                supportingText = {
                    Text("Claude Console → Settings → Admin keys. Nur für Organisationen verfügbar, nicht für Einzelkonten.")
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = openRouterKey,
                onValueChange = { openRouterKey = it; saved = false },
                label = { Text("OpenRouter API-Key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = geminiKey,
                onValueChange = { geminiKey = it; saved = false },
                label = { Text("Gemini API-Key") },
                supportingText = {
                    Text("Google bietet keine Kosten-API: geprüft wird nur der Key, Kosten zeigt das AI-Studio-Dashboard.")
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = ollamaUrl,
                onValueChange = { ollamaUrl = it; saved = false },
                label = { Text("Ollama-Server") },
                placeholder = { Text("http://localhost:11434") },
                supportingText = { Text("Lokal (Termux), im WLAN (z. B. http://192.168.1.20:11434) oder https://ollama.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (ollamaUrl.isNotBlank()) {
                OutlinedTextField(
                    value = ollamaKey,
                    onValueChange = { ollamaKey = it; saved = false },
                    label = { Text("Ollama API-Key (optional, für ollama.com)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = limit,
                onValueChange = { limit = it.replace(',', '.'); saved = false },
                label = { Text("Monatslimit in \$") },
                isError = limit.toDoubleOrNull()?.let { it <= 0 } ?: true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Kosten in der Notch anzeigen", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = showInPill, onCheckedChange = { showInPill = it; saved = false })
            }
            GradientButton(
                if (saved) "Gespeichert ✓" else "Speichern",
                onClick = {
                    if ((limit.toDoubleOrNull() ?: 0.0) <= 0) return@GradientButton
                    settings.setKey(AiProvider.OPENAI, openAiKey)
                    settings.setKey(AiProvider.OPENROUTER, openRouterKey)
                    settings.setKey(AiProvider.ANTHROPIC, anthropicKey)
                    settings.setKey(AiProvider.GEMINI, geminiKey)
                    settings.ollamaUrl = ollamaUrl
                    settings.setKey(AiProvider.OLLAMA, ollamaKey)
                    settings.monthlyLimitUsd = limit.toDouble()
                    settings.showInPill = showInPill
                    saved = true
                    if (connected.isNotEmpty()) editing = false
                    scope.launch { container.aiUsageRepository.refresh() }
                }
            )
        }
    }
}

/** Hinweis-Dialog vor dem System-Dialog: erklärt, warum die Ausnahme nötig ist. */
@Composable
private fun BatteryOptimizationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Im Hintergrund aktiv bleiben") },
        text = {
            Text(
                "Android beendet Apps mit Akku-Optimierung im Leerlauf – die Notch würde dann " +
                    "verschwinden, bis du DevNotch wieder öffnest. Nimm DevNotch von der " +
                    "Akku-Optimierung aus, damit sie dauerhaft läuft. Der Mehrverbrauch ist gering, " +
                    "da die Notch nur zeichnet, wenn sich etwas ändert."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Ausnehmen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Später") } }
    )
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

/**
 * Öffnet den System-Dialog „Akku-Optimierung ignorieren?“ direkt für DevNotch. Manche Hersteller
 * blockieren diesen Intent – dann die allgemeine Liste der Akku-Optimierungen öffnen.
 */
@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimizations(context: Context) {
    val direct = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
    )
    try {
        context.startActivity(direct)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

private fun openOverlaySettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    )
}

private fun openNotificationListenerSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}

/** Optionen des Edge-Players: Design, Einklappen, Wartezeit, Verhalten bei neuem Song. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EdgePlayerSettings(settings: NotchSettings) {
    var theme by remember { mutableStateOf(settings.edgeTheme) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var autoMinimize by remember { mutableStateOf(settings.edgeAutoMinimize) }
    var delay by remember { mutableIntStateOf(settings.edgeMinimizeDelaySeconds) }
    var showOnTrack by remember { mutableStateOf(settings.edgeShowOnTrackChange) }

    HorizontalDivider()
    ListItem(
        headlineContent = { Text("Design") },
        supportingContent = { Text(theme.label) },
        leadingContent = { ThemeSwatch(theme) },
        modifier = Modifier.clickable { showThemeDialog = true }
    )
    HorizontalDivider()
    ListItem(
        headlineContent = { Text("Edge-Player einklappen") },
        supportingContent = { Text("Zeigt nach kurzer Zeit nur noch das runde Cover. Tippen öffnet den Player, Ziehen verschiebt die Bubble.") },
        trailingContent = {
            Switch(
                checked = autoMinimize,
                onCheckedChange = {
                    autoMinimize = it
                    settings.edgeAutoMinimize = it
                }
            )
        }
    )
    if (autoMinimize) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("Einklappen nach", style = MaterialTheme.typography.bodyMedium)
            val options = listOf(3, 5, 10)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                options.forEachIndexed { index, seconds ->
                    SegmentedButton(
                        selected = delay == seconds,
                        onClick = {
                            delay = seconds
                            settings.edgeMinimizeDelaySeconds = seconds
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size)
                    ) { Text("$seconds s") }
                }
            }
        }
        ListItem(
            headlineContent = { Text("Bei neuem Song kurz zeigen") },
            supportingContent = { Text("Klappt den Player bei Titelwechsel auf, damit du den neuen Song siehst") },
            trailingContent = {
                Switch(
                    checked = showOnTrack,
                    onCheckedChange = {
                        showOnTrack = it
                        settings.edgeShowOnTrackChange = it
                    }
                )
            }
        )
    }

    if (showThemeDialog) {
        ThemeDialog(
            current = theme,
            onSelect = {
                theme = it
                settings.edgeTheme = it
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
private fun ThemeDialog(current: EdgeTheme, onSelect: (EdgeTheme) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Design des Edge-Players") },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                EdgeTheme.entries.forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = theme == current,
                                onClick = { onSelect(theme) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = theme == current, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        ThemeSwatch(theme)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(theme.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                theme.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } }
    )
}

/** Farbvorschau: Verlauf des Themes; „Album-Farben“ als bunter Farbkreis. */
@Composable
private fun ThemeSwatch(theme: EdgeTheme) {
    val brush = theme.colors?.let { Brush.verticalGradient(it) }
        ?: Brush.sweepGradient(listOf(Color(0xFFE040FB), Color(0xFF18FFFF), Color(0xFFFFD54F), Color(0xFFFF5252), Color(0xFFE040FB)))
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(brush)
    )
}
