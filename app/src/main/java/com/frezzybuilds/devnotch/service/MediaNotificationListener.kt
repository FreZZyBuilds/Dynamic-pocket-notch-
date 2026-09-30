package com.frezzybuilds.devnotch.service

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Liest und steuert laufende Mediaplayer (Spotify, YouTube Music, …).
 *
 * Erst der vom Nutzer erteilte Benachrichtigungszugriff erlaubt
 * MediaSessionManager.getActiveSessions() mit dieser Komponente. Das System bindet den Service
 * dann selbst (auch nach einem Neustart); die Notch liest nur [nowPlaying].
 */
class MediaNotificationListener : NotificationListenerService() {

    private var sessionManager: MediaSessionManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Alle aktiven Sessions in System-Priorität, jeweils mit registriertem Callback. */
    private var controllers: List<MediaController> = emptyList()
    private val callbacks = mutableMapOf<MediaSession.Token, MediaController.Callback>()

    /** Cover + Farben werden nur bei Titelwechsel neu berechnet, nicht bei jedem Play/Pause. */
    private var artworkKey: String? = null
    private var artwork: Pair<Bitmap, ArtworkAccent>? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { active ->
        onSessionsChanged(active.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val manager = getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(this, MediaNotificationListener::class.java)
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, mainHandler)
            sessionManager = manager
            onSessionsChanged(manager.getActiveSessions(component))
        } catch (_: SecurityException) {
            // Benachrichtigungszugriff wurde zwischenzeitlich entzogen.
        }
    }

    override fun onListenerDisconnected() {
        release()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        sessionManager = null
        onSessionsChanged(emptyList())
    }

    /** Callbacks für neue Sessions registrieren, für beendete abmelden, dann neu auswählen. */
    private fun onSessionsChanged(active: List<MediaController>) {
        val activeTokens = active.map { it.sessionToken }.toSet()
        controllers.filter { it.sessionToken !in activeTokens }.forEach { old ->
            callbacks.remove(old.sessionToken)?.let(old::unregisterCallback)
        }
        active.filter { it.sessionToken !in callbacks }.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
            }
            controller.registerCallback(callback, mainHandler)
            callbacks[controller.sessionToken] = callback
        }
        controllers = active
        publish()
    }

    /** Wechselt automatisch zur Session, die gerade spielt (z. B. Spotify pausiert, YouTube startet). */
    private fun publish() {
        val snapshots = controllers.map { it.snapshot() }
        val index = NowPlayingSelector.select(snapshots)
        val controller = index?.let(controllers::get)
        activeController = controller
        _nowPlaying.value = index?.let {
            val art = controller?.let(::artworkFor)
            NowPlayingSelector.toNowPlaying(snapshots[it])
                .copy(artwork = art?.first, accent = art?.second)
        }
    }

    /**
     * Cover des Titels, auf max. 160 px verkleinert (große Bitmaps nicht im StateFlow halten),
     * plus zwei Akzentfarben per Palette – wie bei Edge-Music-Playern, die sich ans Album anpassen.
     */
    private fun artworkFor(controller: MediaController): Pair<Bitmap, ArtworkAccent>? {
        val metadata = controller.metadata ?: return null
        val key = listOf(
            controller.packageName,
            metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
            metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        ).joinToString("|")
        if (key == artworkKey) return artwork

        val source = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        artworkKey = key
        artwork = source?.let { bitmap ->
            val factor = ARTWORK_MAX_PX.toFloat() / maxOf(bitmap.width, bitmap.height)
            val small = if (factor < 1f) {
                bitmap.scale((bitmap.width * factor).toInt(), (bitmap.height * factor).toInt())
            } else {
                bitmap
            }
            val palette = Palette.from(small).generate()
            val top = palette.getVibrantColor(palette.getDominantColor(DEFAULT_ACCENT_TOP))
            val bottom = palette.getDarkVibrantColor(palette.getDarkMutedColor(DEFAULT_ACCENT_BOTTOM))
            small to ArtworkAccent(top, bottom)
        }
        return artwork
    }

    private fun MediaController.snapshot() = SessionSnapshot(
        packageName = packageName,
        playbackState = playbackState?.state,
        title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
        artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
    )

    companion object {
        private const val ARTWORK_MAX_PX = 160
        private const val DEFAULT_ACCENT_TOP = 0xFFE040FB.toInt()
        private const val DEFAULT_ACCENT_BOTTOM = 0xFF4A148C.toInt()

        private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)

        /** Titel, Künstler und Play/Pause-Status der aktuellen Session; null = nichts läuft. */
        val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

        /** Nur auf dem Main-Thread gelesen/geschrieben (Callbacks + Compose-Klicks). */
        private var activeController: MediaController? = null

        fun togglePlayPause() {
            val controller = activeController ?: return
            if (NowPlayingSelector.isPlaying(controller.playbackState?.state)) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        }

        fun skipToNext() {
            activeController?.transportControls?.skipToNext()
        }

        fun skipToPrevious() {
            activeController?.transportControls?.skipToPrevious()
        }
    }
}
