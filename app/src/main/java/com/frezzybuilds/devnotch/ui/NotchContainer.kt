package com.frezzybuilds.devnotch.ui

import androidx.compose.animation.animateContentSize
import com.frezzybuilds.devnotch.feature.billing.ProGate
import com.frezzybuilds.devnotch.feature.billing.ProFeature
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import com.frezzybuilds.devnotch.service.EdgeSide
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frezzybuilds.devnotch.feature.github.DevTabContent
import com.frezzybuilds.devnotch.feature.notes.NotesContent
import com.frezzybuilds.devnotch.service.NotchLayout
import com.frezzybuilds.devnotch.service.MediaNotificationListener
import com.frezzybuilds.devnotch.service.NotchLayoutMode
import com.frezzybuilds.devnotch.service.NowPlaying
import com.frezzybuilds.devnotch.ui.media.EdgeHandle
import com.frezzybuilds.devnotch.ui.media.EdgeMiniBubble
import com.frezzybuilds.devnotch.ui.media.rememberEdgePlayerState
import com.frezzybuilds.devnotch.appContainer
import com.frezzybuilds.devnotch.feature.aiusage.formatUsd
import com.frezzybuilds.devnotch.feature.aiusage.usageColor
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import com.frezzybuilds.devnotch.ui.media.EdgeMusicBar
import com.frezzybuilds.devnotch.ui.media.MarqueeTitle
import com.frezzybuilds.devnotch.ui.media.MediaHeader
import com.frezzybuilds.devnotch.ui.clipboard.ClipboardContent
import com.frezzybuilds.devnotch.ui.focus.FocusTimerTab
import com.frezzybuilds.devnotch.ui.focus.formatMmSs
import com.frezzybuilds.devnotch.ui.focus.FocusTimerViewModel

private val NotchBlack = Color(0xFF000000)
private val PillWidth = 120.dp
private val DashboardWidth = 360.dp
private val DashboardHeight = 280.dp
/** Schlanke, vertikale Griffleiste im eingeklappten Edge-Modus. */
private val EdgeHandleWidth = 12.dp
private val EdgeHandleHeight = 100.dp
private val EdgeBarWidth = 60.dp
private val EdgeBarHeight = 340.dp
private val EdgeBubbleSize = 56.dp
private val EdgeBubbleGap = 8.dp

private const val AI_REFRESH_INTERVAL_MS = 15 * 60_000L

/** Mindest-Wischstrecke, ab der eine Geste die Notch öffnet oder schließt. */
private val GestureThreshold = 40.dp

private enum class NotchTab(val title: String) {
    DEV("Dev"),
    TIMER("Timer"),
    NOTES("Notizen"),
    CLIP("Clip"),
    AI("AI")
}

/** Eine Feder für Größe und Eckenradius, damit beides synchron „nachfedert“. */
private fun <T> notchSpring() = spring<T>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)

@Composable
fun NotchContainer(
    layout: NotchLayout,
    onExpandRequest: (Boolean) -> Unit,
    onEdgeDrag: (dx: Float, dy: Float) -> Unit = { _, _ -> },
    onEdgeDragEnd: () -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(NotchTab.DEV) }

    // ViewModel hängt am ViewModelStore des Service: Der Timer läuft auch eingeklappt weiter.
    val focusTimer: FocusTimerViewModel = viewModel { FocusTimerViewModel() }
    val timerRemaining by focusTimer.remainingTime.collectAsStateWithLifecycle()
    val timerTotal by focusTimer.totalTime.collectAsStateWithLifecycle()
    val timerRunning by focusTimer.isRunning.collectAsStateWithLifecycle()
    // Eingeklappt nur anzeigen, wenn der Timer läuft oder angebrochen pausiert ist.
    val showTimerInPill = timerRunning || timerRemaining != timerTotal
    val nowPlaying by MediaNotificationListener.nowPlaying.collectAsStateWithLifecycle()

    // AI-Kosten: eingeklappt optional neben dem Timer; dann regelmäßig (alle 15 min) auffrischen.
    val container = LocalContext.current.appContainer
    val aiUsage by container.aiUsageRepository.state.collectAsStateWithLifecycle()
    val aiDisplay by remember { container.aiUsageSettings.displayFlow() }
        .collectAsStateWithLifecycle(initialValue = container.aiUsageSettings.display)
    // KI-Token-Tracker ist Pro: ohne Abo weder Abfragen noch Betrag in der Pille.
    val isPro by container.proAccess.isPro.collectAsStateWithLifecycle()
    val showCostInPill = isPro && aiDisplay.showInPill
    LaunchedEffect(showCostInPill) {
        while (showCostInPill) {
            container.aiUsageRepository.refreshIfStale()
            delay(AI_REFRESH_INTERVAL_MS)
        }
    }

    fun setExpanded(expanded: Boolean) {
        isExpanded = expanded
        onExpandRequest(expanded)
    }

    // Edge-Player: nach 5 s ohne Interaktion zur Cover-Bubble einklappen (abschaltbar).
    val settings = LocalContext.current.appContainer.notchSettings
    val edgePrefs by remember { settings.edgePrefsFlow() }
        .collectAsStateWithLifecycle(initialValue = settings.edgePrefs)
    val edgeMedia = nowPlaying?.takeIf { layout.mode == NotchLayoutMode.EDGE_SIDE }
    val edgePlayer = rememberEdgePlayerState(
        // Ohne „Bei neuem Song zeigen“ bleibt der Schlüssel konstant → kein Wiederaufklappen.
        trackKey = if (edgePrefs.showOnTrackChange) nowPlaying?.let { it.packageName + "|" + it.title } else Unit,
        autoMinimize = edgePrefs.autoMinimize,
        active = edgeMedia != null && !isExpanded,
        delayMillis = edgePrefs.minimizeDelaySeconds * 1_000L
    )
    val edgeMini = edgeMedia != null && edgePlayer.minimized && !isExpanded

    val (collapsedWidth, collapsedHeight) =
        collapsedSize(layout, hasMedia = nowPlaying != null, minimized = edgeMini)
    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 24.dp else minOf(collapsedWidth, collapsedHeight) / 2,
        animationSpec = notchSpring(),
        label = "notchCorner"
    )

    // Edge-Modus: Drawer-Größe aus Bildschirm und Ausrichtung (Hoch-/Querformat).
    val configuration = LocalConfiguration.current
    val drawer = EdgeDrawerSpec.forScreen(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        landscape = layout.landscape
    )
    val (expandedWidth, expandedHeight) = when (layout.mode) {
        NotchLayoutMode.NOTCH_TOP -> DashboardWidth to DashboardHeight
        NotchLayoutMode.EDGE_SIDE -> drawer.width to drawer.height
    }
    var leftPane by remember { mutableStateOf(DrawerPane.DEV) }
    var rightPane by remember { mutableStateOf(DrawerPane.NOTES) }

    val haptic = LocalHapticFeedback.current
    val dragThreshold = with(LocalDensity.current) { GestureThreshold.toPx() }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            modifier = Modifier
                .animateContentSize(animationSpec = notchSpring())
                .then(
                    if (isExpanded) Modifier.size(expandedWidth, expandedHeight)
                    else Modifier.size(collapsedWidth, collapsedHeight)
                )
                .clip(if (edgeMini) RectangleShape else notchShape(layout, cornerRadius))
                // Bubble: transparentes Fenster, die Kreisform zeichnet EdgeMiniBubble selbst.
                .background(if (edgeMini) Color.Transparent else NotchBlack)
                // Wischen: Notch nach unten auf / nach oben zu; Edge zur Mitte auf / zum Rand zu.
                // Die eingeklappte Bubble hat eigene Gesten (Verschieben), daher dort nicht.
                .then(
                    if (edgeMini) Modifier
                    else Modifier.notchDragGestures(
                        mode = layout.mode,
                        side = layout.edgeSide,
                        expanded = isExpanded,
                        thresholdPx = dragThreshold,
                        haptic = haptic
                    ) { action -> setExpanded(action == NotchGestureAction.EXPAND) }
                )
                // Nur eingeklappt klickbar, sonst schluckt die Box Taps im Dashboard.
                .clickable(enabled = !isExpanded) { setExpanded(true) },
            contentAlignment = Alignment.Center
        ) {
            if (isExpanded && layout.mode == NotchLayoutMode.EDGE_SIDE && drawer.split) {
                // Seitlicher Drawer als Split-Ansicht: links Dev/Pomodoro, rechts Notizen/Clipboard.
                SplitDrawer(
                    focusTimer = focusTimer,
                    nowPlaying = nowPlaying,
                    leftPane = leftPane,
                    rightPane = rightPane,
                    onSelectLeft = { leftPane = it },
                    onSelectRight = { rightPane = it },
                    onClose = { setExpanded(false) },
                    modifier = Modifier
                        .wrapContentSize(Alignment.Center, unbounded = true)
                        .size(drawer.width, drawer.height)
                )
            } else if (isExpanded) {
                // Feste Größe + unbounded: Das Dashboard wird beim Aufklappen „aufgedeckt“
                // statt während der Animation zusammengequetscht zu werden. Schmale Bildschirme
                // im Edge-Modus nutzen dieselben Tabs in Drawer-Größe.
                Dashboard(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    focusTimer = focusTimer,
                    nowPlaying = nowPlaying,
                    onClose = { setExpanded(false) },
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopCenter, unbounded = true)
                        .size(expandedWidth, expandedHeight)
                )
            } else if (layout.mode == NotchLayoutMode.NOTCH_TOP) {
                val playing = nowPlaying?.takeIf { it.isPlaying }
                val timerText = if (showTimerInPill) "⏱ ${formatMmSs(timerRemaining)}" else null
                val costText = aiUsage.takeIf { showCostInPill && it.usages.isNotEmpty() }
                    ?.let { formatUsd(it.totalCostUsd) }
                val slots = PillLayout.slots(musicPlaying = playing != null, timerText = timerText, costText = costText)
                val costColor = usageColor(aiUsage.totalCostUsd, aiDisplay.limitUsd)

                @Composable
                fun render(item: PillItem) = when (item) {
                    is PillItem.Timer -> PillLabel(item.text)
                    is PillItem.Cost -> PillLabel(item.text, costColor)
                    PillItem.MusicGlyph -> PillLabel("♪")
                    PillItem.MusicTitle -> playing?.let { MarqueeTitle(it) }
                }
                CollapsedPillContent(
                    lensGap = lensGap(layout),
                    start = slots.start?.let { item -> { render(item) } },
                    end = slots.end?.let { item -> { render(item) } }
                )
            } else {
                // Edge-Dock: mit Musik die farbige Player-Leiste (oder eingeklappt die Cover-Bubble),
                // sonst der schlanke Griff. Die Größe federt über animateContentSize, der Inhalt
                // blendet über – so „zieht sich“ die Leiste zur Bubble zusammen.
                val media = edgeMedia
                if (media != null) {
                    AnimatedContent(
                        targetState = edgeMini,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            fadeIn(tween(220, delayMillis = 120)) togetherWith fadeOut(tween(120)) using
                                SizeTransform(clip = false)
                        },
                        label = "edgePlayer"
                    ) { mini ->
                        if (mini) {
                            EdgeMiniBubble(
                                nowPlaying = media,
                                theme = edgePrefs.theme,
                                onClick = edgePlayer::restore,
                                onDrag = onEdgeDrag,
                                onDragEnd = onEdgeDragEnd,
                                // Kleiner Abstand zum Rand: Die Bubble schwebt frei.
                                modifier = Modifier.padding(
                                    start = if (layout.edgeSide == EdgeSide.LEFT) EdgeBubbleGap else 0.dp,
                                    end = if (layout.edgeSide == EdgeSide.RIGHT) EdgeBubbleGap else 0.dp
                                )
                            )
                        } else {
                            EdgeMusicBar(
                                nowPlaying = media,
                                theme = edgePrefs.theme,
                                onPrevious = {
                                    edgePlayer.onInteraction()
                                    MediaNotificationListener.skipToPrevious()
                                },
                                onPlayPause = {
                                    edgePlayer.onInteraction()
                                    MediaNotificationListener.togglePlayPause()
                                },
                                onNext = {
                                    edgePlayer.onInteraction()
                                    MediaNotificationListener.skipToNext()
                                }
                            )
                        }
                    }
                } else {
                    EdgeHandle()
                }
            }
        }
    }
}

@Composable
private fun Dashboard(
    selectedTab: NotchTab,
    onSelectTab: (NotchTab) -> Unit,
    focusTimer: FocusTimerViewModel,
    nowPlaying: NowPlaying?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(12.dp)) {
        DashboardHeader(nowPlaying, onClose)

        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color.Transparent,
            contentColor = Color.White
        ) {
            NotchTab.entries.forEach { tab ->
                // Content-Variante ohne die 16-dp-Textränder: fünf Tabs passen so in 336 dp.
                Tab(
                    selected = tab == selectedTab,
                    onClick = { onSelectTab(tab) },
                    unselectedContentColor = Color.Gray
                ) {
                    Text(
                        tab.title,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 14.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
        ) {
            when (selectedTab) {
                NotchTab.AI -> ProGate(ProFeature.AI_TRACKER, onLeave = onClose) { AiStatsTabContent() }
                NotchTab.TIMER -> FocusTimerTab(focusTimer)
                NotchTab.CLIP -> ClipboardContent(onLeave = onClose)
                NotchTab.DEV -> DevTabContent(onLaunched = onClose)
                NotchTab.NOTES -> NotesContent(onLeaveForExternalApp = onClose)
            }
        }
    }
}

/** Kopfzeile: Mediensteuerung, wenn Musik läuft (keine Extra-Höhe), sonst Titel; dazu ✕. */
@Composable
internal fun DashboardHeader(nowPlaying: NowPlaying?, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (nowPlaying != null) {
            MediaHeader(nowPlaying, Modifier.weight(1f))
        } else {
            Text(
                "DevNotch",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
        }
        TextButton(onClick = onClose) {
            Text("✕", color = Color.Gray)
        }
    }
}

/**
 * Eingeklappter Inhalt in zwei Hälften links/rechts der Kameralinse, damit Text nie über die
 * Linse läuft. Ohne Linse bleibt nur ein schmaler Mittelsteg.
 */
@Composable
private fun CollapsedPillContent(
    lensGap: Dp,
    start: (@Composable () -> Unit)?,
    end: (@Composable () -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { start?.invoke() }
        Spacer(Modifier.width(lensGap))
        Box(Modifier.weight(1f).clipToBounds(), contentAlignment = Alignment.CenterStart) { end?.invoke() }
    }
}

@Composable
private fun PillLabel(text: String, color: Color = Color.White) {
    Text(text, color = color, style = MaterialTheme.typography.labelSmall, maxLines = 1)
}

/** Breite der Linse plus etwas Luft; ohne Linse 8 dp. */
@Composable
private fun lensGap(layout: NotchLayout): Dp {
    val lens = layout.lens ?: return 8.dp
    return with(LocalDensity.current) { lens.diameter.toDp() } + 12.dp
}

/**
 * Oben: exakt die vom Service berechnete Pillengröße (120×35 dp oder größer, symmetrisch um
 * die Kameralinse), damit Fensterposition und Inhalt übereinstimmen. Am Rand ein schmaler Griff,
 * der sich bei Musik per Feder zur 60×340-dp-Player-Leiste aufzieht.
 */
@Composable
private fun collapsedSize(layout: NotchLayout, hasMedia: Boolean, minimized: Boolean): Pair<Dp, Dp> {
    val density = LocalDensity.current
    return when (layout.mode) {
        NotchLayoutMode.NOTCH_TOP -> with(density) {
            layout.pill.width.toDp() to layout.pill.height.toDp()
        }
        NotchLayoutMode.EDGE_SIDE -> when {
            minimized -> EdgeBubbleSize + EdgeBubbleGap to EdgeBubbleSize
            hasMedia -> EdgeBarWidth to EdgeBarHeight
            else -> EdgeHandleWidth to EdgeHandleHeight
        }
    }
}

/**
 * Oben rundum abgerundet. Am Rand nur die zur Bildschirmmitte zeigende Seite – physisch links
 * oder rechts, je nachdem, wo die Leiste angedockt ist.
 */
private fun notchShape(layout: NotchLayout, radius: Dp): Shape =
    when {
        layout.mode == NotchLayoutMode.NOTCH_TOP -> RoundedCornerShape(radius)
        layout.edgeSide == EdgeSide.RIGHT -> AbsoluteRoundedCornerShape(topLeft = radius, bottomLeft = radius)
        else -> AbsoluteRoundedCornerShape(topRight = radius, bottomRight = radius)
    }
