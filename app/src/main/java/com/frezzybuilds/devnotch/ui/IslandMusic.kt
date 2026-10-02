package com.frezzybuilds.devnotch.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.peek.Peek
import com.frezzybuilds.devnotch.peek.PeekCenter
import com.frezzybuilds.devnotch.service.MediaNotificationListener
import com.frezzybuilds.devnotch.service.NowPlaying
import com.frezzybuilds.devnotch.ui.theme.Brand
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Farben der Wellenform: aus dem Cover (oben → unten), sonst der Markenverlauf. */
fun waveColors(nowPlaying: NowPlaying?): List<Color> =
    nowPlaying?.accent?.let { accent ->
        val top = Color(accent.top)
        // Etwas aufhellen: dunkle Cover sollen auf Schwarz noch leuchten.
        listOf(lerp(top, Color.White, 0.25f), lerp(Color(accent.bottom), top, 0.5f))
    } ?: listOf(Brand.Magenta, Brand.Cyan)

/**
 * Wellenform wie in Apples kompakter Musikansicht: fünf Balken in Coverfarbe, die nur bei
 * Wiedergabe tanzen. Skalierung in der Layer-Phase – keine Recomposition pro Bild.
 */
@Composable
fun MusicWaveform(nowPlaying: NowPlaying?, modifier: Modifier = Modifier, height: Dp = 14.dp) {
    val colors = waveColors(nowPlaying)
    val playing = nowPlaying?.isPlaying == true && !LocalInspectionMode.current
    val transition = rememberInfiniteTransition(label = "wave")
    val bars: List<State<Float>?> = listOf(420, 300, 520, 360, 460).map { duration ->
        if (playing) transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(duration), RepeatMode.Reverse), label = "w$duration") else null
    }
    val still = listOf(0.45f, 0.8f, 0.55f, 0.95f, 0.4f)
    Row(modifier.height(height), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        bars.forEachIndexed { i, bar ->
            Box(
                Modifier
                    .width(2.5.dp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleY = bar?.value ?: (still[i] * if (nowPlaying?.isPlaying == true) 1f else 0.5f)
                        transformOrigin = TransformOrigin.Center
                    }
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(colors))
            )
        }
    }
}

/**
 * Großer Player beim Gedrückthalten (Apples erweiterte Musikansicht): Cover, Titel, Interpret,
 * Wellenform, Fortschritt und Steuerung. Jede Bedienung verlängert die Anzeige.
 */
@Composable
fun MusicPlayerContent(pillHeight: Dp) {
    val context = LocalContext.current
    val nowPlaying by MediaNotificationListener.nowPlaying.collectAsStateWithLifecycle()
    val np = nowPlaying
    var progress by remember { mutableStateOf(MediaNotificationListener.progress()) }
    if (!LocalInspectionMode.current) {
        LaunchedEffect(Unit) {
            while (true) {
                progress = MediaNotificationListener.progress()
                delay(500)
            }
        }
    }
    fun keepOpen() = PeekCenter.show(Peek.MusicPlayer())

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(pillHeight))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val art = np?.artwork
            if (art != null) {
                val image = remember(art) { art.asImageBitmap() }
                Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(46.dp).clip(RoundedCornerShape(11.dp)))
            } else {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(waveColors(np))), contentAlignment = Alignment.Center) {
                    Text("♪", color = Color.Black, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(np?.title ?: "Nichts läuft", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.fillMaxWidth().smoothMarquee())
                np?.artist?.let { Text(it, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium, maxLines = 1) }
            }
            Spacer(Modifier.width(8.dp))
            MusicWaveform(np, height = 18.dp)
        }
        // Fortschritt (nur wenn der Player Position und Länge meldet).
        progress?.let { (position, duration) ->
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(position), color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
                Box(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(waveColors(np)))
                    )
                }
                Text("-" + formatDuration(duration - position), color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            PlayerButton("⏮", "Zurück") { MediaNotificationListener.skipToPrevious(); keepOpen() }
            PlayPauseButton(playing = np?.isPlaying == true) { MediaNotificationListener.togglePlayPause(); keepOpen() }
            PlayerButton("⏭", "Weiter") { MediaNotificationListener.skipToNext(); keepOpen() }
            PlayerButton("↗", "Player öffnen") {
                MediaNotificationListener.openPlayer(context)
                PeekCenter.current.value?.let(PeekCenter::dismiss)
            }
        }
    }
}

@Composable
private fun PlayerButton(symbol: String, label: String, big: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(if (big) 40.dp else 34.dp)
            .clip(CircleShape)
            .background(if (big) Color.White else Color.White.copy(alpha = 0.10f))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = if (big) Color.Black else Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

/** Play/Pause als gezeichnete Form – Textzeichen wie „⏸“ erscheinen auf Android als buntes Emoji. */
@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(role = Role.Button, onClickLabel = if (playing) "Pause" else "Abspielen", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(14.dp)) {
            if (playing) {
                val bar = size.width * 0.32f
                drawRoundRect(Color.Black, size = androidx.compose.ui.geometry.Size(bar, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f))
                drawRoundRect(
                    Color.Black,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width - bar, 0f),
                    size = androidx.compose.ui.geometry.Size(bar, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f)
                )
            } else {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.12f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(size.width * 0.12f, size.height)
                    close()
                }
                drawPath(path, Color.Black)
            }
        }
    }
}
