package com.frezzybuilds.devnotch.ui

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frezzybuilds.devnotch.notify.AppVisuals
import com.frezzybuilds.devnotch.notify.NotchNotification
import com.frezzybuilds.devnotch.service.MediaNotificationListener
import com.frezzybuilds.devnotch.service.NowPlaying
import com.frezzybuilds.devnotch.system.SystemStatus
import com.frezzybuilds.devnotch.ui.focus.FocusTimerViewModel
import com.frezzybuilds.devnotch.ui.theme.Brand
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay

/** Seiten der Seiten-Ansicht (wie im Video: wischbare Karten unter der Kamera). */
enum class NotchPage(val title: String) {
    SEARCH("Suche"),
    INBOX("Mitteilungen"),
    CONTROLS("Steuerung"),
    MUSIC("Musik"),
    APPS("Apps"),
    TIMER("Timer"),
    WEATHER("Wetter");

    companion object {
        fun from(names: List<String>): List<NotchPage> = names.mapNotNull { n -> entries.firstOrNull { it.name == n } }

        /** Gesperrt: nichts, was Apps öffnet oder Privates zeigt (außer gekürzten Mitteilungen). */
        val LOCK_SAFE = setOf(INBOX, CONTROLS, MUSIC, TIMER, WEATHER)
    }
}

/** Kartenhintergrund der Seiten: dunkles Glas. */
private val PageCard = Brush.verticalGradient(listOf(Color(0xFF2A2A30), Color(0xFF16161B)))
private val PageShape = RoundedCornerShape(26.dp)

/**
 * Seiten-Ansicht: wischbare Karten mit Punkten. Beim Wischen schrumpft die abgehende Karte leicht
 * und blendet aus, die neue federt heran – alles in der Layer-Phase (keine Recomposition je Bild).
 */
@Composable
fun PagesDashboard(
    pages: List<NotchPage>,
    initialPage: NotchPage?,
    focusTimer: FocusTimerViewModel,
    nowPlaying: NowPlaying?,
    onOpenNotification: (NotchNotification) -> Unit,
    redact: (NotchNotification) -> NotchNotification?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (pages.isEmpty()) return
    val start = pages.indexOf(initialPage).coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { pages.size }
    LaunchedEffect(initialPage, pages) {
        val index = pages.indexOf(initialPage)
        if (index >= 0 && pager.currentPage != index) pager.animateScrollToPage(index)
    }
    Column(modifier) {
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), pageSpacing = 10.dp, key = { pages[it].name }) { index ->
            val page = pages[index]
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val offset = ((pager.currentPage - index) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                        val scale = 1f - 0.08f * offset
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - 0.5f * offset
                    }
                    .clip(PageShape)
                    .background(PageCard)
                    .border(1.dp, Color.White.copy(alpha = 0.07f), PageShape)
            ) {
                // Nur die sichtbare Seite animiert (Wetter, Wellen …) – spart Akku und Bilder.
                val active = pager.settledPage == index
                when (page) {
                    NotchPage.SEARCH -> SearchPage(onClose)
                    NotchPage.INBOX -> Box(Modifier.padding(12.dp)) { NotificationStackContent(onOpen = onOpenNotification, redact = redact) }
                    NotchPage.CONTROLS -> ControlsPage(active)
                    NotchPage.MUSIC -> MusicPage(nowPlaying, active, onClose)
                    NotchPage.APPS -> AppsPage(onClose)
                    NotchPage.TIMER -> TimerPage(focusTimer)
                    NotchPage.WEATHER -> WeatherPage(active)
                }
            }
        }
        PageDots(pager, pages.size)
    }
}

@Composable
private fun PageDots(pager: PagerState, count: Int) {
    Row(Modifier.fillMaxWidth().height(18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            // Aktiver Punkt wird zum kleinen Strich (wie im Video); Breite in der Layer-Phase.
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = 14.dp, height = 5.dp)
                    .graphicsLayer {
                        val d = ((pager.currentPage + pager.currentPageOffsetFraction) - i).absoluteValue.coerceIn(0f, 1f)
                        scaleX = 0.36f + 0.64f * (1f - d)
                        alpha = 0.35f + 0.65f * (1f - d)
                    }
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

// --- Suche -------------------------------------------------------------------------------------

private fun launch(context: Context, intent: Intent): Boolean =
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)

@Composable
private fun SearchPage(onClose: () -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        val web = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q)
        if (!launch(context, web)) launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
        query = ""
        editing = false
        onClose()
    }
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Brush.horizontalGradient(listOf(Brand.Magenta.copy(alpha = 0.7f), Brand.Cyan.copy(alpha = 0.7f))), CircleShape)
                .clickable(role = Role.Button) { editing = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                if (editing) {
                    // Erst beim Antippen darf das Overlay Fokus nehmen – sonst verlöre die App dahinter
                    // bei jedem Öffnen ihre Tastatur.
                    RequestOverlayFocus()
                    LaunchedEffect(Unit) {
                        delay(60)
                        runCatching { focus.requestFocus() }
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        cursorBrush = SolidColor(Brand.Cyan),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
                if (query.isEmpty()) Text("Suchen oder fragen", color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "🎙",
                modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Sprachsuche") {
                    if (!launch(context, Intent(Intent.ACTION_VOICE_COMMAND))) launch(context, Intent(Intent.ACTION_ASSIST))
                    onClose()
                }.padding(6.dp)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            QuickLaunch("♪", "Musik", Color(0xFF1DB954)) {
                if (!MediaNotificationListener.openPlayer(context)) {
                    val spotify = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                    launch(context, spotify ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC))
                }
                onClose()
            }
            QuickLaunch("🔊", "Lautstärke", Color.White.copy(alpha = 0.18f)) {
                context.getSystemService(AudioManager::class.java)
                    ?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            }
            QuickLaunch("ᛒ", "Bluetooth", Color.White.copy(alpha = 0.18f)) {
                launch(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); onClose()
            }
            QuickLaunch("📷", "Kamera", Color.White.copy(alpha = 0.18f)) {
                launch(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); onClose()
            }
        }
    }
}

@Composable
private fun QuickLaunch(symbol: String, label: String, color: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .pressScale(interaction)
                .clip(CircleShape)
                .background(color)
                .clickable(interaction, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Text(symbol, color = Color.White, style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

// --- Schnelleinstellungen ----------------------------------------------------------------------

@Composable
private fun ControlsPage(active: Boolean) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    var wifi by remember { mutableStateOf<Boolean?>(null) }
    var bluetooth by remember { mutableStateOf<Boolean?>(null) }
    val torch by SystemStatus.torchOn.collectAsStateWithLifecycle()
    var torchLocal by remember { mutableStateOf(torch) }
    var volume by remember { mutableFloatStateOf(0f) }
    var brightness by remember { mutableFloatStateOf(0.5f) }
    fun read() {
        wifi = runCatching { context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)?.isWifiEnabled }.getOrNull()
        @Suppress("MissingPermission")
        bluetooth = runCatching { context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.isEnabled }.getOrNull()
        audio?.let { volume = it.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / it.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
        brightness = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f)
    }
    // Zustand nur auffrischen, solange die Seite sichtbar ist (Schalter ändert man im System-Panel).
    if (active && !LocalInspectionMode.current) {
        LaunchedEffect(Unit) {
            while (true) {
                read()
                delay(1_500)
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlTile("WLAN", wifi, "📶", Modifier.weight(1f)) {
                launch(context, if (android.os.Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            ControlTile("Bluetooth", bluetooth, "ᛒ", Modifier.weight(1f)) { launch(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlTile("Taschenlampe", torch || torchLocal, "🔦", Modifier.weight(1f)) {
                val on = !(torch || torchLocal)
                torchLocal = on
                SystemStatus.setTorchMode(context, on)
            }
            ControlTile("Mobile Daten", null, "⇅", Modifier.weight(1f)) {
                launch(context, if (android.os.Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))
            }
        }
        PillSlider("☀", brightness, Modifier.weight(1f)) { v ->
            brightness = v
            if (Settings.System.canWrite(context)) {
                runCatching {
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (v * 255).toInt().coerceIn(1, 255))
                }
            } else {
                // Einmalige Erlaubnis „Systemeinstellungen ändern“.
                launch(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
            }
        }
        PillSlider("🔈", volume, Modifier.weight(1f)) { v ->
            volume = v
            audio?.let { it.setStreamVolume(AudioManager.STREAM_MUSIC, (v * it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).toInt(), 0) }
        }
    }
}

@Composable
private fun ControlTile(label: String, on: Boolean?, symbol: String, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(56.dp)
            .pressScale(interaction)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = if (on == true) 0.16f else 0.07f))
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (on == true) Color(0xFF0A84FF) else Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Text(symbol, color = Color.White, style = MaterialTheme.typography.labelLarge) }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when (on) { true -> "An"; false -> "Aus"; null -> "Öffnen" },
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** Breiter Regler wie im Kontrollzentrum: Füllung zeigt den Wert, Ziehen oder Tippen setzt ihn. */
@Composable
private fun PillSlider(symbol: String, value: Float, modifier: Modifier, onChange: (Float) -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .pointerInput(Unit) {
                detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(value.coerceIn(0.04f, 1f))
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFE6E8F0))
        )
        Text(symbol, modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp), style = MaterialTheme.typography.labelLarge)
    }
}

// --- Musik ---------------------------------------------------------------------------------------

@Composable
private fun MusicPage(np: NowPlaying?, active: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    var progress by remember { mutableStateOf(MediaNotificationListener.progress()) }
    var volume by remember { mutableFloatStateOf(0f) }
    if (active && !LocalInspectionMode.current) {
        LaunchedEffect(Unit) {
            while (true) {
                progress = MediaNotificationListener.progress()
                audio?.let { volume = it.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / it.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
                delay(500)
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val art = np?.artwork
            if (art != null) {
                val image = remember(art) { art.asImageBitmap() }
                Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
            } else {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(waveColors(np))), contentAlignment = Alignment.Center) {
                    Text("♪", color = Color.Black, style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(np?.title ?: "Nichts läuft", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.fillMaxWidth().smoothMarquee())
                Text(np?.artist ?: "Starte Musik in einer App", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MusicWaveform(np, height = 18.dp)
        }
        // Fortschritt: Ziehen spult.
        val (position, duration) = progress ?: (0L to 0L)
        SeekBar(if (duration > 0) position.toFloat() / duration else 0f, enabled = duration > 0) { f ->
            if (duration > 0) {
                MediaNotificationListener.seekTo((f * duration).toLong())
                progress = (f * duration).toLong() to duration
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(if (duration > 0) formatDuration(position) else "", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(if (duration > 0) formatDuration(duration) else "", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RoundButton("⏮", "Zurück") { MediaNotificationListener.skipToPrevious() }
            PlayPauseButton(playing = np?.isPlaying == true) { MediaNotificationListener.togglePlayPause() }
            RoundButton("⏭", "Weiter") { MediaNotificationListener.skipToNext() }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔈", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f)) {
                SeekBar(volume, enabled = true) { v ->
                    volume = v
                    audio?.let { it.setStreamVolume(AudioManager.STREAM_MUSIC, (v * it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).toInt(), 0) }
                }
            }
            Spacer(Modifier.width(6.dp))
            Text("🔊", style = MaterialTheme.typography.labelMedium)
        }
        if (np != null) {
            val label = remember(np.packageName) { AppVisuals.of(context, np.packageName).label }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(role = Role.Button) { MediaNotificationListener.openPlayer(context); onClose() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Spielt auf $label", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text("↗", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SeekBar(value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            .then(
                if (enabled) Modifier
                    .pointerInput(Unit) { detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) } }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            onChange((change.position.x / size.width).coerceIn(0f, 1f))
                        }
                    }
                else Modifier
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)))
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun RoundButton(symbol: String, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(40.dp)
            .pressScale(interaction)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(symbol, color = Color.White, style = MaterialTheme.typography.titleSmall) }
}

// --- Apps ------------------------------------------------------------------------------------------

/** Standard-Apps über ihre Rollen (Telefon, Kamera, Galerie, Karten, Kalender) – kein App-Scan nötig. */
private class LaunchApp(val label: String, val icon: Drawable?, val intent: Intent)

private fun defaultApps(context: Context): List<LaunchApp> {
    val pm = context.packageManager
    val candidates = listOf(
        "Telefon" to Intent(Intent.ACTION_DIAL),
        "Kamera" to Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        "Galerie" to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY),
        "Karten" to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS),
        "Kalender" to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
        "Nachrichten" to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
        "Browser" to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
        "Einstellungen" to Intent(Settings.ACTION_SETTINGS)
    )
    return candidates.mapNotNull { (fallback, intent) ->
        // Erste passende App nehmen (bei mehreren nicht den Auswahldialog „android“).
        val info = runCatching { pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY) }.getOrDefault(emptyList())
            .firstOrNull { it.activityInfo.packageName != "android" } ?: return@mapNotNull null
        val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(fallback)
        LaunchApp(label, runCatching { info.loadIcon(pm) }.getOrNull(), Intent(intent).setPackage(info.activityInfo.packageName))
    }
}

@Composable
private fun AppsPage(onClose: () -> Unit) {
    val context = LocalContext.current
    val apps = remember { defaultApps(context) }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Text("Apps", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = 4
            val cell: Dp = maxWidth / columns
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                apps.chunked(columns).forEach { row ->
                    Row {
                        row.forEach { app ->
                            val interaction = remember { MutableInteractionSource() }
                            Column(
                                Modifier.width(cell).pressScale(interaction)
                                    .clickable(interaction, indication = null, role = Role.Button, onClickLabel = app.label) {
                                        launch(context, app.intent)
                                        onClose()
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val bitmap = remember(app) { app.icon?.toBitmap(96, 96)?.asImageBitmap() }
                                if (bitmap != null) {
                                    Image(bitmap, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
                                } else {
                                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Brand.Horizontal))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(app.label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
