package com.frezzybuilds.devnotch

import android.Manifest
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.frezzybuilds.devnotch.ui.theme.DevNotchTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Ab Android 13 nötig, damit die Foreground-Service-Benachrichtigung sichtbar ist.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            DevNotchTheme {
                Scaffold { padding ->
                    SetupScreen(Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
fun SetupScreen(modifier: Modifier = Modifier) {
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
    var showModeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("DevNotch", style = MaterialTheme.typography.headlineMedium)

        Card(Modifier.fillMaxWidth()) {
            if (!hasOverlayPermission) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Overlay-Berechtigung fehlt", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Damit die Notch über anderen Apps schweben kann, braucht DevNotch die " +
                            "Berechtigung „Über anderen Apps einblenden“."
                    )
                    Button(onClick = { openOverlaySettings(context) }) {
                        Text("Overlay-Berechtigung erteilen")
                    }
                }
            } else {
                ListItem(
                    headlineContent = { Text("Notch aktiv") },
                    supportingContent = {
                        Text(if (isServiceRunning) "Läuft im Hintergrund" else "Gestoppt")
                    },
                    trailingContent = {
                        Switch(
                            checked = isServiceRunning,
                            onCheckedChange = { enable ->
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
                        )
                    }
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Platzierung") },
                supportingContent = {
                    Text(
                        if (isTablet) "${NotchLayoutMode.EDGE_SIDE.label} – auf Tablets automatisch"
                        else displayMode.label
                    )
                },
                modifier = Modifier.clickable { showModeDialog = true }
            )
            if (displayMode == NotchLayoutMode.EDGE_SIDE || isTablet) {
                EdgePlayerSettings(settings)
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Benachrichtigungszugriff") },
                supportingContent = {
                    Text(if (hasListenerAccess) "Erteilt – Mediensteuerung verfügbar" else "Für die Mediensteuerung nötig")
                },
                trailingContent = {
                    if (!hasListenerAccess) {
                        TextButton(onClick = { openNotificationListenerSettings(context) }) {
                            Text("Erteilen")
                        }
                    }
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Akku-Optimierung") },
                supportingContent = {
                    Text(
                        if (batteryExempt) "Ausgenommen – Notch läuft zuverlässig im Hintergrund"
                        else "Aktiv – Android kann die Notch im Leerlauf beenden"
                    )
                },
                trailingContent = {
                    if (!batteryExempt) {
                        TextButton(onClick = { showBatteryDialog = true }) { Text("Ausnehmen") }
                    }
                }
            )
        }

        OemHintCard(settings)

        GitHubCard()

        AiUsageCard()
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

    if (showModeDialog) {
        DisplayModeDialog(
            current = displayMode,
            onConfirm = { mode ->
                displayMode = mode
                // Persistiert; ein laufender Service übernimmt die Änderung sofort.
                settings.displayMode = mode
                showModeDialog = false
            },
            onDismiss = { showModeDialog = false }
        )
    }
}

/** RadioButton-Auswahldialog: Center Notch (Punch-Hole) vs. Edge Dock (Tablet/Phone Side). */
@Composable
private fun DisplayModeDialog(
    current: NotchLayoutMode,
    onConfirm: (NotchLayoutMode) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Platzierung") },
        text = {
            Column(Modifier.selectableGroup()) {
                NotchLayoutMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = mode == selected,
                                onClick = { selected = mode },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // onClick = null: Die ganze Zeile ist klickbar (größere Touch-Fläche, TalkBack).
                        RadioButton(selected = mode == selected, onClick = null)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                mode.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("Übernehmen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun GitHubCard() {
    val settings = LocalContext.current.appContainer.gitHubSettings
    var token by remember { mutableStateOf(settings.token.orEmpty()) }
    var username by remember { mutableStateOf(settings.username.orEmpty()) }
    var saved by remember { mutableStateOf(token to username) }
    val isSaved = saved == (token to username) && token.isNotBlank()

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("GitHub", style = MaterialTheme.typography.titleMedium)
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
            OutlinedButton(onClick = {
                settings.token = token
                settings.username = username
                saved = token to username
            }) {
                Text(if (isSaved) "Gespeichert ✓" else "Speichern")
            }
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

    Card(Modifier.fillMaxWidth()) {
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
            return@Card
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Wichtig für ${guide.vendor}", style = MaterialTheme.typography.titleMedium)
            Text(
                "Dein Gerät beendet Apps im Hintergrund zusätzlich zur Akku-Optimierung. " +
                    "Damit die Notch dauerhaft läuft:",
                style = MaterialTheme.typography.bodyMedium
            )
            guide.steps.forEachIndexed { index, step ->
                Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { guide.openSettings(context) }) { Text("Einstellungen öffnen") }
                TextButton(onClick = {
                    done = true
                    settings.oemHintDone = true
                }) { Text("Erledigt") }
            }
        }
    }
}

/**
 * AI-Nutzung: Keys für OpenAI (Admin-Key) und OpenRouter, Monatslimit und ob der Betrag
 * eingeklappt in der Notch erscheinen soll. Speichern löst sofort eine Abfrage aus.
 */
@Composable
private fun AiUsageCard() {
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

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("AI-Nutzung", style = MaterialTheme.typography.titleMedium)
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
            OutlinedButton(
                enabled = (limit.toDoubleOrNull() ?: 0.0) > 0,
                onClick = {
                    settings.setKey(AiProvider.OPENAI, openAiKey)
                    settings.setKey(AiProvider.OPENROUTER, openRouterKey)
                    settings.setKey(AiProvider.ANTHROPIC, anthropicKey)
                    settings.setKey(AiProvider.GEMINI, geminiKey)
                    settings.ollamaUrl = ollamaUrl
                    settings.setKey(AiProvider.OLLAMA, ollamaKey)
                    settings.monthlyLimitUsd = limit.toDouble()
                    settings.showInPill = showInPill
                    saved = true
                    scope.launch { container.aiUsageRepository.refresh() }
                }
            ) { Text(if (saved) "Gespeichert ✓" else "Speichern") }
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
