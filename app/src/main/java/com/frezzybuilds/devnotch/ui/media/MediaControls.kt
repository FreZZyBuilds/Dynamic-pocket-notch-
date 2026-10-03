package com.frezzybuilds.devnotch.ui.media

import com.frezzybuilds.devnotch.ui.smoothMarquee
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.service.MediaNotificationListener
import com.frezzybuilds.devnotch.service.NowPlaying

/** Titel (+ Künstler) als Lauftext – für die eingeklappte Pille. */
@Composable
fun MarqueeTitle(nowPlaying: NowPlaying, modifier: Modifier = Modifier) {
    val text = listOfNotNull(nowPlaying.title, nowPlaying.artist).joinToString(" · ")
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        // Endlos durchlaufen; basicMarquee scrollt nur, wenn der Text zu lang ist.
        modifier = modifier.smoothMarquee()
    )
}

/** Kopfzeile im aufgeklappten Dashboard: Titel/Künstler + Zurück, Play/Pause, Weiter. */
@Composable
fun MediaHeader(nowPlaying: NowPlaying, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                nowPlaying.title,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.smoothMarquee()
            )
            nowPlaying.artist?.let {
                Text(
                    it,
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        MediaButton(MediaIcons.SkipPrevious, "Vorheriger Titel", MediaNotificationListener::skipToPrevious)
        MediaButton(
            icon = if (nowPlaying.isPlaying) MediaIcons.Pause else MediaIcons.Play,
            description = if (nowPlaying.isPlaying) "Pause" else "Abspielen",
            onClick = MediaNotificationListener::togglePlayPause
        )
        MediaButton(MediaIcons.SkipNext, "Nächster Titel", MediaNotificationListener::skipToNext)
    }
}

@Composable
private fun MediaButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** Material-Icons als Pfade – spart die große material-icons-extended-Bibliothek. */
internal object MediaIcons {
    val Play = icon("Play", "M8,5v14l11,-7z")
    val Pause = icon("Pause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z")
    val SkipNext = icon("SkipNext", "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z")
    val SkipPrevious = icon("SkipPrevious", "M6,6h2v12H6zM9.5,12l8.5,6V6z")

    private fun icon(name: String, path: String) = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.White)).build()
}
