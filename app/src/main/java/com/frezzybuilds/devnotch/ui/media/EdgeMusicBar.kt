package com.frezzybuilds.devnotch.ui.media

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.service.NowPlaying

/** Standardverlauf (Magenta → Violett → Indigo), wenn der Player kein Cover liefert. */
private val DefaultGradient = listOf(Color(0xFFE040FB), Color(0xFF8E24AA), Color(0xFF311B92))

/** Farben der Edge-Leiste: Verlauf aus dem Cover plus lesbare Vordergrundfarbe. */
private data class EdgeColors(val gradient: List<Color>, val content: Color, val onButton: Color)

@Composable
private fun rememberEdgeColors(nowPlaying: NowPlaying): EdgeColors {
    val accent = nowPlaying.accent
    val top = accent?.let { Color(it.top) } ?: DefaultGradient.first()
    val bottom = accent?.let { Color(it.bottom) } ?: DefaultGradient.last()
    // Sanfte Überblendung bei Titelwechsel statt hartem Farbsprung.
    val animatedTop by animateColorAsState(top, tween(600), label = "edgeTop")
    val animatedBottom by animateColorAsState(bottom, tween(600), label = "edgeBottom")
    val middle = androidx.compose.ui.graphics.lerp(animatedTop, animatedBottom, 0.45f)
    // Helle Cover (z. B. gelb) bekommen dunkle Schrift – wie im Screenshot mit dem gelben Theme.
    val content = if (middle.luminance() > 0.55f) Color(0xFF111111) else Color.White
    return EdgeColors(
        gradient = listOf(animatedTop, middle, animatedBottom),
        content = content,
        onButton = animatedBottom
    )
}

/**
 * Eingeklappte Edge-Leiste im Stil von „Edge Music Player“: senkrechter Farbverlauf aus dem
 * Albumcover, Cover oben, Equalizer, um 90° gedrehter Titel als Lauftext und darunter die
 * ebenfalls gedrehte Steuerung mit großem runden Play/Pause-Knopf.
 *
 * Die Leiste dockt rechts an (Gravity.END), gelesen wird von unten nach oben.
 */
@Composable
fun EdgeMusicBar(
    nowPlaying: NowPlaying,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = rememberEdgeColors(nowPlaying)
    // Position des Titelbereichs in der Leiste (0..1), um die Ausblendfarben zu treffen.
    var barCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var titleSpan by remember { mutableStateOf(0.3f to 0.65f) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { barCoords = it }
            .background(Brush.verticalGradient(colors.gradient))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Artwork(nowPlaying, colors)
        Spacer(Modifier.height(10.dp))
        Equalizer(playing = nowPlaying.isPlaying, color = colors.content)
        Spacer(Modifier.height(10.dp))

        // Senkrechter Titel: füllt den Platz zwischen Equalizer und Steuerung; lange Titel
        // laufen weich an den Enden aus statt hart abgeschnitten zu werden.
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .onGloballyPositioned { coords ->
                    val bar = barCoords ?: return@onGloballyPositioned
                    val top = bar.localPositionOf(coords, androidx.compose.ui.geometry.Offset.Zero).y
                    titleSpan = top / bar.size.height to (top + coords.size.height) / bar.size.height
                }
                .fadeIntoBackground(
                    top = colors.gradient.colorAt(titleSpan.first),
                    bottom = colors.gradient.colorAt(titleSpan.second)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.verticalLayout(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    nowPlaying.title,
                    color = colors.content,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.edgeMarquee()
                )
                nowPlaying.artist?.let {
                    Text(
                        it.uppercase(),
                        color = colors.content.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.edgeMarquee()
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        // Gedreht wie ein quer liegender Player: „Weiter“ zeigt nach oben, „Zurück“ nach unten.
        EdgeIconButton(MediaIcons.SkipNext, "Nächster Titel", colors.content, onNext)
        Spacer(Modifier.height(6.dp))
        PlayPauseButton(nowPlaying.isPlaying, colors, onPlayPause)
        Spacer(Modifier.height(6.dp))
        EdgeIconButton(MediaIcons.SkipPrevious, "Vorheriger Titel", colors.content, onPrevious)
    }
}

/**
 * Eingeklappter Edge-Player: nur noch das runde Cover. Ein Ring im Albumverlauf zeigt, dass
 * Musik läuft, und dreht sich dabei langsam. Tippen holt die volle Leiste zurück.
 */
@Composable
fun EdgeMiniBubble(
    nowPlaying: NowPlaying,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = rememberEdgeColors(nowPlaying)
    val ringAngle = if (LocalInspectionMode.current || !nowPlaying.isPlaying) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "bubbleRing")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(4_000, easing = LinearEasing)),
            label = "ringAngle"
        ).value
    }
    val ringColors = colors.gradient + colors.gradient.first()

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape)
            .clickable(onClickLabel = "Edge-Player öffnen", onClick = onClick)
            .drawBehind {
                rotate(ringAngle) { drawCircle(Brush.sweepGradient(ringColors)) }
            }
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        val art = nowPlaying.artwork
        val inner = Modifier.fillMaxSize().clip(CircleShape)
        if (art != null) {
            val image = remember(art) { art.asImageBitmap() }
            Image(image, contentDescription = "Albumcover – tippen zum Öffnen", contentScale = ContentScale.Crop, modifier = inner)
        } else {
            Box(inner.background(Brush.verticalGradient(colors.gradient)), contentAlignment = Alignment.Center) {
                Text("♪", color = colors.content, style = MaterialTheme.typography.titleLarge)
            }
        }
        // Pausiert: dezentes Pause-Symbol über dem Cover, damit der Zustand erkennbar bleibt.
        if (!nowPlaying.isPlaying) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(MediaIcons.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Ruhezustand ohne Musik: schlanker Griff mit Glas-Streifen, wie der Samsung-Edge-Griff. */
@Composable
fun EdgeHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1C1C1E), Color(0xFF0A0A0A)))),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.15f))
                    )
                )
        )
    }
}

@Composable
private fun Artwork(nowPlaying: NowPlaying, colors: EdgeColors) {
    val shape = CircleShape
    val base = Modifier
        .size(40.dp)
        .shadow(6.dp, shape)
        .clip(shape)
        .border(1.5.dp, colors.content.copy(alpha = 0.35f), shape)
    val art = nowPlaying.artwork
    if (art != null) {
        val image = remember(art) { art.asImageBitmap() }
        Image(image, contentDescription = "Albumcover", contentScale = ContentScale.Crop, modifier = base)
    } else {
        Box(base.background(colors.content.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text("♪", color = colors.content, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Drei tanzende Balken, solange Musik läuft; pausiert stehen sie flach. */
@Composable
private fun Equalizer(playing: Boolean, color: Color) {
    // Vorschau/Screenshot: Standbild statt Endlosanimation.
    if (LocalInspectionMode.current) {
        EqualizerBars(listOf(0.55f, 1f, 0.7f).map { if (playing) it else 0.25f }, color)
        return
    }
    val transition = rememberInfiniteTransition(label = "equalizer")
    val durations = listOf(420, 560, 360)
    val heights = durations.mapIndexed { i, duration ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(duration), RepeatMode.Reverse),
            label = "bar$i"
        )
    }
    EqualizerBars(heights.map { if (playing) it.value else 0.25f }, color)
}

@Composable
private fun EqualizerBars(fractions: List<Float>, color: Color) {
    Row(
        modifier = Modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        fractions.forEach { fraction ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color.copy(alpha = 0.9f))
            )
        }
    }
}

/**
 * Blendet die Enden des Titelbereichs weich in den Hintergrund aus. Statt einer Alpha-Maske
 * (eigene Offscreen-Ebene) wird darübergemalt – in genau der Verlaufsfarbe, die an dieser
 * Stelle der Leiste liegt. Das ist günstiger und rendert überall identisch.
 */
private fun Modifier.fadeIntoBackground(top: Color, bottom: Color, fraction: Float = 0.14f) =
    drawWithContent {
        drawContent()
        val fade = size.height * fraction
        drawRect(
            brush = Brush.verticalGradient(listOf(top, top.copy(alpha = 0f)), startY = 0f, endY = fade),
            size = size.copy(height = fade)
        )
        drawRect(
            brush = Brush.verticalGradient(
                listOf(bottom.copy(alpha = 0f), bottom),
                startY = size.height - fade,
                endY = size.height
            ),
            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - fade),
            size = size.copy(height = fade)
        )
    }

/** Farbe eines gleichmäßig verteilten Verlaufs an Position [fraction] (0 = oben, 1 = unten). */
private fun List<Color>.colorAt(fraction: Float): Color {
    val scaled = fraction.coerceIn(0f, 1f) * (size - 1)
    val index = scaled.toInt().coerceAtMost(size - 2)
    return androidx.compose.ui.graphics.lerp(this[index], this[index + 1], scaled - index)
}

/** Endloser Lauftext; in der Vorschau statisch, damit Preview/Screenshot zur Ruhe kommen. */
@Composable
private fun Modifier.edgeMarquee(): Modifier =
    if (LocalInspectionMode.current) this
    else basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1_500)

@Composable
private fun PlayPauseButton(isPlaying: Boolean, colors: EdgeColors, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isPlaying) MediaIcons.Pause else MediaIcons.Play,
            contentDescription = if (isPlaying) "Pause" else "Abspielen",
            tint = colors.onButton,
            modifier = Modifier.size(24.dp).rotate(-90f)
        )
    }
}

@Composable
private fun EdgeIconButton(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp).rotate(-90f))
    }
}

/**
 * Dreht den Inhalt um −90° (von unten nach oben lesbar) und tauscht dabei Breite und Höhe im
 * Layout – anders als Modifier.rotate, das nur zeichnet. Der Inhalt bekommt die verfügbare
 * Höhe als Breite, damit der Lauftext die ganze Leistenhöhe nutzt.
 */
private fun Modifier.verticalLayout() = layout { measurable, constraints ->
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = 0,
            maxWidth = constraints.maxHeight,
            minHeight = 0,
            maxHeight = constraints.maxWidth
        )
    )
    layout(placeable.height, placeable.width) {
        placeable.placeWithLayer(
            x = (placeable.height - placeable.width) / 2,
            y = (placeable.width - placeable.height) / 2
        ) { rotationZ = -90f }
    }
}
