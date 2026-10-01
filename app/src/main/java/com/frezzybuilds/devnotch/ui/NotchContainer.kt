package com.frezzybuilds.devnotch.ui

import androidx.compose.animation.animateContentSize
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import com.frezzybuilds.devnotch.peek.PeekCenter
import com.frezzybuilds.devnotch.peek.Peek
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.animation.core.LinearEasing
import com.frezzybuilds.devnotch.ui.theme.Brand
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.State
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.ui.graphics.drawOutline
import com.frezzybuilds.devnotch.service.EdgeSide
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    onEdgeDragEnd: () -> Unit = {},
    /** Fenster fokussierbar machen (true) oder Fokus an die App dahinter zurückgeben (false). */
    onFocusableChange: (Boolean) -> Unit = {},
    /** Zählt Zurück-Tasten des fokussierten Overlays hoch; jede Änderung klappt die Notch ein. */
    backPresses: Int = 0,
    /** Einklapp-Animation ist fertig – erst jetzt darf der Service das Fenster verkleinern. */
    onCollapseSettled: () -> Unit = {},
    /** Peek beginnt/endet – der Service passt die Fenstergröße einmalig an. */
    onPeekChange: (Boolean) -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }
    // Erster Eindruck mit sofortigem Nutzen: ohne GitHub-Token startet die Notch im Timer.
    val startContext = LocalContext.current
    var selectedTab by remember {
        mutableStateOf(if (startContext.appContainer.gitHubSettings.token.isNullOrBlank()) NotchTab.TIMER else NotchTab.DEV)
    }

    // ViewModel hängt am ViewModelStore des Service: Der Timer läuft auch eingeklappt weiter.
    val focusTimer: FocusTimerViewModel = viewModel { FocusTimerViewModel() }
    val timerRemaining by focusTimer.remainingTime.collectAsStateWithLifecycle()
    val timerTotal by focusTimer.totalTime.collectAsStateWithLifecycle()
    val timerRunning by focusTimer.isRunning.collectAsStateWithLifecycle()
    // Eingeklappt nur anzeigen, wenn der Timer läuft oder angebrochen pausiert ist.
    val showTimerInPill = timerRunning || timerRemaining != timerTotal
    // Abgelaufener Anteil, gleitend wie der Ring im Timer-Tab; gelesen erst beim Zeichnen.
    val timerElapsed = animateFloatAsState(
        targetValue = if (timerTotal > 0) 1f - timerRemaining.toFloat() / timerTotal else 0f,
        animationSpec = tween(1_000, easing = LinearEasing),
        label = "pillTimer"
    )
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

    // Aufklappen in zwei Schritten: erst vergrößert der Service das Fenster, zwei Bilder später
    // federt der Inhalt auf. Sonst zeichnet Android ein Bild mit alter Fenstergröße (schwarzer,
    // eckiger Kasten). Einklappen sofort – das Fenster schrumpft erst nach der Animation.
    val expandScope = rememberCoroutineScope()
    var expandJob by remember { mutableStateOf<Job?>(null) }
    fun setExpanded(expanded: Boolean) {
        // Wer aufklappt, sieht ohnehin alles – ein laufender Peek ist damit erledigt.
        if (expanded) PeekCenter.current.value?.let(PeekCenter::dismiss)
        expandJob?.cancel()
        onExpandRequest(expanded)
        if (expanded) {
            expandJob = expandScope.launch {
                withFrameNanos { }
                withFrameNanos { }
                isExpanded = true
            }
        } else {
            isExpanded = false
        }
    }

    // --- Peeks: kurze Live-Einblendungen der eingeklappten Pille (nur Notch oben) -----------
    val peek by PeekCenter.current.collectAsStateWithLifecycle()
    val activePeek = peek.takeIf { layout.mode == NotchLayoutMode.NOTCH_TOP && !isExpanded }
    val peekHaptic = LocalHapticFeedback.current
    // Lebensdauer unabhängig von der Anzeige: Ein Peek aus dem Edge-Modus oder bei offener
    // Notch läuft trotzdem ab und taucht später nicht unpassend auf.
    LaunchedEffect(peek) {
        val current = peek ?: return@LaunchedEffect
        delay(current.durationMs)
        PeekCenter.dismiss(current)
    }
    LaunchedEffect(activePeek) {
        onPeekChange(activePeek != null)
        val shown = activePeek ?: return@LaunchedEffect
        peekHaptic.performHapticFeedback(
            if (shown is Peek.TimerDone) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
        )
    }
    val timerRingVisible = layout.mode == NotchLayoutMode.NOTCH_TOP && !isExpanded &&
        activePeek == null && showTimerInPill

    // Timer abgelaufen → Peek.
    LaunchedEffect(focusTimer) { focusTimer.finished.collect { PeekCenter.show(Peek.TimerDone) } }
    // Neuer Titel (nicht beim ersten Anzeigen, nur während Wiedergabe) → Peek mit Cover.
    val trackKey = nowPlaying?.let { it.packageName + "|" + it.title }
    var lastTrackKey by remember { mutableStateOf(trackKey) }
    LaunchedEffect(trackKey) {
        val current = nowPlaying
        val showsPill = layout.mode == NotchLayoutMode.NOTCH_TOP && !isExpanded
        if (showsPill && trackKey != null && trackKey != lastTrackKey && current?.isPlaying == true) {
            PeekCenter.show(Peek.TrackChanged(current.title, current.artist, current.artwork))
        }
        lastTrackKey = trackKey
    }

    // Fokus nur, solange aufgeklappt UND ein sichtbarer Bereich ihn braucht (Notizen, Eingabe-
    // felder, Clip-Tab). Eingeklappt sind die Inhalte nicht komponiert → sofort wieder false.
    val overlayFocus = remember { OverlayFocus() }
    val focusable = OverlayFocus.isFocusable(isExpanded, overlayFocus.requests)
    LaunchedEffect(focusable) { onFocusableChange(focusable) }

    // Zurück-Taste (kommt nur an, solange das Fenster fokussierbar ist): Notch einklappen.
    var handledBackPresses by remember { mutableStateOf(backPresses) }
    LaunchedEffect(backPresses) {
        if (backPresses != handledBackPresses) {
            handledBackPresses = backPresses
            if (isExpanded) setExpanded(false)
        }
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

    val (pillWidth, pillHeight) =
        collapsedSize(layout, hasMedia = nowPlaying != null, minimized = edgeMini)
    val peekConfig = LocalConfiguration.current
    val (collapsedWidth, collapsedHeight) = if (activePeek != null) {
        ExpandedSize.peek(peekConfig.screenWidthDp, pillHeight.value).let { (w, h) -> w.dp to h.dp }
    } else {
        pillWidth to pillHeight
    }
    // Als State, gelesen erst in Layer/Zeichnen: Ein animierter Radius darf nicht bei jedem
    // Bild den ganzen Container neu komponieren (das ruckelte beim Einklappen).
    val cornerRadius = animateDpAsState(
        targetValue = when {
            isExpanded -> 24.dp
            activePeek != null -> 30.dp
            else -> minOf(collapsedWidth, collapsedHeight) / 2
        },
        animationSpec = notchSpring(),
        label = "notchCorner"
    )
    val animatedShape = remember(layout.mode, layout.edgeSide) { AnimatedNotchShape(layout, cornerRadius) }

    // Edge-Modus: Drawer-Größe aus Bildschirm und Ausrichtung (Hoch-/Querformat).
    val configuration = LocalConfiguration.current
    val drawer = EdgeDrawerSpec.forScreen(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        landscape = layout.landscape
    )
    val density = LocalDensity.current
    val topInset = with(density) {
        if (layout.mode == NotchLayoutMode.NOTCH_TOP) layout.expandedTopInset.toDp() else 0.dp
    }
    val (expandedWidth, expandedHeight) = ExpandedSize.of(
        layout.mode,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        layout.landscape,
        topInset.value
    ).let { (w, h) -> w.dp to h.dp }
    var leftPane by remember { mutableStateOf(DrawerPane.DEV) }
    var rightPane by remember { mutableStateOf(DrawerPane.NOTES) }

    val haptic = LocalHapticFeedback.current
    val dragThreshold = with(LocalDensity.current) { GestureThreshold.toPx() }

    // Squish beim Drücken (nur eingeklappt) und Neon-Rand, der beim Aufklappen einblendet.
    val pillInteraction = remember { MutableInteractionSource() }
    val pressed by pillInteraction.collectIsPressedAsState()
    val squish = animateFloatAsState(
        targetValue = if (pressed && !isExpanded) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "pillSquish"
    )
    val rim = animateFloatAsState(
        targetValue = when {
            isExpanded -> 1f
            activePeek != null -> 0.7f
            else -> 0f
        },
        animationSpec = tween(if (isExpanded) 420 else 160),
        label = "neonRim"
    )

    // Lichtlauf („Border Beam“) um die Notch – Modus und Farben live aus den Einstellungen.
    val beamPrefs by remember { settings.beamPrefsFlow() }
        .collectAsStateWithLifecycle(initialValue = settings.beamPrefs)
    // Nicht um die Cover-Bubble und den schmalen Griff am Rand; der Timer-Ring hat Vorrang.
    val beamShapeFits = !edgeMini && (layout.mode == NotchLayoutMode.NOTCH_TOP || isExpanded)
    val beamOn = beamShapeFits && when (beamPrefs.mode) {
        BeamMode.ALWAYS -> !timerRingVisible
        BeamMode.EVENTS -> isExpanded || activePeek != null
        BeamMode.OFF -> false
    }
    val beamStrength = animateFloatAsState(if (beamOn) 1f else 0f, tween(450), label = "beamStrength")
    val beamPosition = rememberBeamPosition(running = beamOn)

    CompositionLocalProvider(LocalOverlayFocus provides overlayFocus) {
        MaterialTheme(colorScheme = Brand.NotchScheme) {
            Box(
                modifier = Modifier
                    // Antippen: Die Pille „gibt nach“ und federt zurück.
                    .graphicsLayer {
                        scaleX = squish.value
                        scaleY = squish.value
                    }
                    .animateContentSize(animationSpec = notchSpring()) { _, _ ->
                        if (!isExpanded) onCollapseSettled()
                    }
                    .then(
                        if (isExpanded) Modifier.size(expandedWidth, expandedHeight)
                        else Modifier.size(collapsedWidth, collapsedHeight)
                    )
                    .graphicsLayer {
                        shape = if (edgeMini) RectangleShape else notchShape(layout, cornerRadius.value)
                        clip = true
                    }
                    // Bubble: transparentes Fenster, die Kreisform zeichnet EdgeMiniBubble selbst.
                    .background(if (edgeMini) Color.Transparent else NotchBlack)
                    // Aufgeklappt: violetter Schimmer von oben und Neon-Rand wie im App-Icon.
                    .then(if (edgeMini) Modifier else Modifier.neonFrame(animatedShape, rim))
                    .then(
                        if (beamShapeFits) {
                            Modifier.borderBeam(animatedShape, beamPosition, beamStrength, beamPrefs.palette.colors)
                        } else {
                            Modifier
                        }
                    )
                    // Fokus-Timer läuft: Fortschritt als Lichtlinie einmal rund um die Pille.
                    .then(
                        if (timerRingVisible) Modifier.pillProgress(timerElapsed) else Modifier
                    )
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
                    .clickable(
                        enabled = !isExpanded,
                        interactionSource = pillInteraction,
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        setExpanded(true)
                    },
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
                            // Inhalt unter Statusleiste und Kamera; darüber bleibt die Fläche schwarz.
                            .padding(top = topInset)
                    )
                } else if (activePeek != null) {
                    PeekContent(
                        peek = activePeek,
                        pillHeight = pillHeight,
                        lensGap = lensGap(layout),
                        // Feste Endgröße + unbounded: Der Inhalt wird beim Wachsen aufgedeckt.
                        modifier = Modifier
                            .wrapContentSize(Alignment.TopCenter, unbounded = true)
                            .size(collapsedWidth, collapsedHeight)
                            .staggerIn(0)
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
                        PillItem.MusicGlyph -> MiniArtwork(playing)
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
        Box(Modifier.staggerIn(0)) { DashboardHeader(nowPlaying, onClose) }

        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            // Indikator im Markenverlauf statt Standard-Lila, Trennlinie kaum sichtbar.
            indicator = { positions ->
                if (selectedTab.ordinal < positions.size) {
                    Box(
                        with(TabRowDefaults) { Modifier.tabIndicatorOffset(positions[selectedTab.ordinal]) }
                            .padding(horizontal = 14.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Brand.Horizontal)
                    )
                }
            },
            divider = { HorizontalDivider(color = Color.White.copy(alpha = 0.08f)) },
            modifier = Modifier.staggerIn(1)
        ) {
            NotchTab.entries.forEach { tab ->
                // Content-Variante ohne die 16-dp-Textränder: fünf Tabs passen so in 336 dp.
                Tab(
                    selected = tab == selectedTab,
                    onClick = { onSelectTab(tab) },
                    unselectedContentColor = Color.White.copy(alpha = 0.45f)
                ) {
                    Text(
                        tab.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (tab == selectedTab) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 14.dp)
                    )
                }
            }
        }

        // Tab-Wechsel gleitet in Richtung des neuen Tabs statt hart umzuschalten.
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(tween(260)) { width -> direction * width / 6 } + fadeIn(tween(220, delayMillis = 40))) togetherWith
                    (slideOutHorizontally(tween(200)) { width -> -direction * width / 6 } + fadeOut(tween(140))) using
                    SizeTransform(clip = false)
            },
            label = "tabContent",
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
                .staggerIn(2)
        ) { tab ->
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    NotchTab.AI -> ProGate(ProFeature.AI_TRACKER, onLeave = onClose) { AiStatsTabContent(onLeave = onClose) }
                    NotchTab.TIMER -> FocusTimerTab(focusTimer)
                    NotchTab.CLIP -> ClipboardContent(onLeave = onClose)
                    NotchTab.DEV -> DevTabContent(onLaunched = onClose)
                    NotchTab.NOTES -> NotesContent(onLeaveForExternalApp = onClose)
                }
            }
        }
    }
}

/**
 * Aufgeklappt: zarter violetter Schimmer von oben und ein Neon-Rand im Markenverlauf. [rim]
 * (0…1) wird erst beim Zeichnen gelesen – das Ein-/Ausblenden kostet keine Recomposition.
 */
private fun Modifier.neonFrame(shape: Shape, rim: State<Float>): Modifier = drawWithContent {
    val strength = rim.value
    if (strength > 0f) {
        drawRect(
            Brush.radialGradient(
                listOf(Brand.Violet.copy(alpha = 0.22f * strength), Color.Transparent),
                center = Offset(size.width / 2f, 0f),
                radius = size.width * 0.75f
            )
        )
    }
    drawContent()
    if (strength > 0f) {
        drawOutline(
            shape.createOutline(size, layoutDirection, this),
            brush = Brand.Horizontal,
            alpha = 0.9f * strength,
            // Die Hälfte liegt außerhalb des Clips – sichtbar bleibt ein feiner 1-dp-Rand.
            style = Stroke(width = 2.dp.toPx())
        )
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
    // Ganze Aussparung aussparen – bei breiten Notches mehr als nur die Linse.
    return with(LocalDensity.current) { lens.width.toDp() } + 12.dp
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

/** Kleines rundes Cover links neben der Kamera; ohne Cover eine Note im Markenverlauf. */
@Composable
private fun MiniArtwork(nowPlaying: NowPlaying?) {
    val art = nowPlaying?.artwork
    val shape = CircleShape
    if (art != null) {
        val image = remember(art) { art.asImageBitmap() }
        Image(
            image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(20.dp).clip(shape)
        )
    } else {
        Box(Modifier.size(20.dp).clip(shape).background(Brand.Horizontal), contentAlignment = Alignment.Center) {
            Text("♪", color = Color.Black, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Fortschritt des Fokus-Timers als Linie im Markenverlauf rund um die Pille, beginnend oben in
 * der Mitte im Uhrzeigersinn. [elapsed] (0…1) wird erst beim Zeichnen gelesen.
 */
private fun Modifier.pillProgress(elapsed: State<Float>): Modifier = drawWithContent {
    drawContent()
    val fraction = elapsed.value.coerceIn(0f, 1f)
    if (fraction <= 0f) return@drawWithContent
    val stroke = 2.dp.toPx()
    val inset = stroke / 2
    val w = size.width
    val h = size.height
    val r = (h / 2 - inset).coerceAtLeast(0f)
    val path = Path().apply {
        moveTo(w / 2, inset)
        lineTo(w - inset - r, inset)
        arcTo(Rect(w - inset - 2 * r, inset, w - inset, h - inset), -90f, 180f, false)
        lineTo(inset + r, h - inset)
        arcTo(Rect(inset, inset, inset + 2 * r, h - inset), 90f, 180f, false)
        lineTo(w / 2, inset)
    }
    val measure = PathMeasure().apply { setPath(path, false) }
    val segment = Path()
    measure.getSegment(0f, measure.length * fraction, segment, true)
    drawPath(segment, Brand.Horizontal, style = Stroke(width = stroke, cap = StrokeCap.Round))
}

/**
 * Form mit animiertem Eckenradius: Der Radius wird erst beim Erzeugen der Kontur gelesen
 * (Zeichen-/Cache-Phase) – Rand und Lichtlauf folgen ihm ohne Recomposition.
 */
private class AnimatedNotchShape(private val layout: NotchLayout, private val radius: State<Dp>) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        notchShape(layout, radius.value).createOutline(size, layoutDirection, density)
}

