package com.frezzybuilds.devnotch.service

import android.app.Notification
import android.content.ComponentName
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.frezzybuilds.devnotch.appContainer
import com.frezzybuilds.devnotch.notify.NotchNotification
import com.frezzybuilds.devnotch.notify.NotificationController
import com.frezzybuilds.devnotch.notify.NotificationHub
import com.frezzybuilds.devnotch.data.settings.NotchSettings
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Liest und steuert laufende Mediaplayer – jede App mit Media-Session: Spotify, YouTube,
 * YouTube Music, SoundCloud, Deezer, Samsung Music, VLC, Browser-Videos (Chrome, Firefox) …
 *
 * Erst der vom Nutzer erteilte Benachrichtigungszugriff erlaubt
 * MediaSessionManager.getActiveSessions() mit dieser Komponente. Das System bindet den Service
 * dann selbst (auch nach einem Neustart); die Notch liest nur [nowPlaying].
 *
 * Apps liefern Titel und Cover sehr unterschiedlich. Deshalb gibt es Fallback-Ketten:
 * - Titel: METADATA_KEY_TITLE → DISPLAY_TITLE → MediaDescription → Titel der Medien-Benachrichtigung
 * - Cover: Bitmap in den Metadaten → Cover-URI (z. B. YouTube, per Coil geladen)
 *          → großes Icon der Medien-Benachrichtigung
 */
class MediaNotificationListener : NotificationListenerService() {

    private var sessionManager: MediaSessionManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = MainScope()

    /** Alle aktiven Sessions in System-Priorität, jeweils mit registriertem Callback. */
    private var controllers: List<MediaController> = emptyList()
    private val callbacks = mutableMapOf<MediaSession.Token, MediaController.Callback>()

    /** Cover + Farben je Titel; nur bei Titelwechsel neu berechnet, nicht bei jedem Play/Pause. */
    private val artworkCache = object : LinkedHashMap<String, Artwork?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Artwork?>) = size > 12
    }
    private val loadingUris = mutableSetOf<String>()

    /** Letzte Medien-Benachrichtigung je App: Titel/Cover-Fallback für sparsame Metadaten. */
    private val mediaNotifications = mutableMapOf<String, NotificationInfo>()

    /** Hält die Referenz – SharedPreferences merkt sich Listener nur schwach. */
    private var notifyPrefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private data class Artwork(val bitmap: Bitmap, val accent: ArtworkAccent)
    private data class NotificationInfo(val title: String?, val text: String?, val largeIcon: Bitmap?)

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { active ->
        onSessionsChanged(active.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationHub.controller = NotificationController { key -> runCatching { cancelNotification(key) } }
        NotificationHub.onAppSeen = { pkg, label -> applicationContext.appContainer.notchSettings.rememberSeenApp(pkg, label) }
        runCatching { activeNotifications }.getOrNull()?.forEach { sbn ->
            rememberMediaNotification(sbn)
            routeNotification(sbn, initialScan = true)
        }
        // Schalter geändert (z. B. Navigation aus): laufende Live-Ansichten neu bewerten.
        val settings = applicationContext.appContainer.notchSettings
        notifyPrefsListener?.let(settings::removeListener)
        notifyPrefsListener = settings.addListener(NotchSettings.NOTIFY_PREF_KEYS) {
            NotificationHub.clearLive()
            runCatching { activeNotifications }.getOrNull()?.forEach { routeNotification(it, initialScan = true) }
        }
        val manager = getSystemService(MediaSessionManager::class.java)
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component(), mainHandler)
            sessionManager = manager
            onSessionsChanged(manager.getActiveSessions(component()))
        } catch (_: SecurityException) {
            // Benachrichtigungszugriff wurde zwischenzeitlich entzogen.
        }
    }

    override fun onListenerDisconnected() {
        release()
        NotificationHub.controller = null
        NotificationHub.clearLive()
        // Das System trennt Listener gelegentlich (z. B. nach App-Updates) – wieder verbinden.
        runCatching { requestRebind(component()) }
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        release()
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (rememberMediaNotification(sbn)) publish() else routeNotification(sbn, initialScan = false)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (mediaNotifications.remove(sbn.packageName) != null) publish()
        NotificationHub.onRemoved(sbn.key)
    }

    /**
     * Alle übrigen Benachrichtigungen: Live-Ansichten (Anruf, Navigation …) und Peeks nach den
     * Einstellungen. Nur im Arbeitsspeicher, nichts wird gespeichert oder übertragen.
     */
    private fun routeNotification(sbn: StatusBarNotification, initialScan: Boolean) {
        val prefs = applicationContext.appContainer.notchSettings.notifyPrefs
        // Alles ausgeschaltet: Benachrichtigungen gar nicht erst auswerten.
        if (!prefs.enabled && !prefs.live.any) return
        val notification = NotchNotification.from(this, sbn, runCatching { currentRanking }.getOrNull()) ?: return
        if (notification.isMedia) return
        val dnd = runCatching { currentInterruptionFilter != INTERRUPTION_FILTER_ALL }.getOrDefault(false)
        NotificationHub.onPosted(notification, prefs, dnd, packageName, initialScan)
    }

    private fun component() = ComponentName(this, MediaNotificationListener::class.java)

    private fun release() {
        notifyPrefsListener?.let(applicationContext.appContainer.notchSettings::removeListener)
        notifyPrefsListener = null
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        sessionManager = null
        onSessionsChanged(emptyList())
    }

    /** Merkt sich Titel und großes Icon von Benachrichtigungen mit Media-Session. */
    private fun rememberMediaNotification(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        val extras = notification.extras ?: return false
        if (!extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return false
        val icon = runCatching {
            notification.getLargeIcon()?.loadDrawable(this)?.toBitmap()?.downscaled()
        }.getOrNull()
        mediaNotifications[sbn.packageName] = NotificationInfo(
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            largeIcon = icon
        )
        return true
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
                override fun onSessionDestroyed() = publish()
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
            val snapshot = snapshots[it]
            val art = controller?.let { c -> artworkFor(c, snapshot) }
            NowPlayingSelector.toNowPlaying(snapshot).copy(artwork = art?.bitmap, accent = art?.accent)
        }
    }

    private fun artworkFor(controller: MediaController, snapshot: SessionSnapshot): Artwork? {
        val key = listOf(snapshot.packageName, snapshot.title, snapshot.artist).joinToString("|")
        artworkCache[key]?.let { return it }

        val metadata = controller.metadata
        val bitmap = metadata?.let {
            it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        }
        if (bitmap != null) return bitmap.downscaled().toArtwork().also { artworkCache[key] = it }

        // Nur eine Adresse (YouTube & Co.): asynchron laden, danach erneut veröffentlichen.
        val uri = metadata?.let {
            it.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: it.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: it.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
                ?: it.description?.iconUri?.toString()
        }
        if (uri != null && loadingUris.add(key)) loadArtwork(key, uri)

        // Bis dahin (oder ohne URI): das Cover aus der Medien-Benachrichtigung.
        return mediaNotifications[snapshot.packageName]?.largeIcon?.toArtwork()
    }

    private fun loadArtwork(key: String, uri: String) {
        scope.launch {
            val request = ImageRequest.Builder(this@MediaNotificationListener)
                .data(uri)
                .size(ARTWORK_MAX_PX)
                .allowHardware(false) // Palette braucht Software-Bitmaps
                .build()
            val result = imageLoader.execute(request)
            loadingUris.remove(key)
            val bitmap = (result as? SuccessResult)?.drawable?.toBitmap() ?: return@launch
            artworkCache[key] = bitmap.downscaled().toArtwork()
            publish()
        }
    }

    /** Große Bitmaps nicht im StateFlow halten: auf max. 160 px verkleinern. */
    private fun Bitmap.downscaled(): Bitmap {
        val factor = ARTWORK_MAX_PX.toFloat() / maxOf(width, height)
        return if (factor < 1f) scale((width * factor).toInt(), (height * factor).toInt()) else this
    }

    /** Zwei Akzentfarben per Palette – wie Edge-Music-Player, die sich ans Album anpassen. */
    private fun Bitmap.toArtwork(): Artwork {
        val palette = Palette.from(this).generate()
        val top = palette.getVibrantColor(palette.getDominantColor(DEFAULT_ACCENT_TOP))
        val bottom = palette.getDarkVibrantColor(palette.getDarkMutedColor(DEFAULT_ACCENT_BOTTOM))
        return Artwork(this, ArtworkAccent(top, bottom))
    }

    private fun MediaController.snapshot(): SessionSnapshot {
        val description = metadata?.description
        val notification = mediaNotifications[packageName]
        return SessionSnapshot(
            packageName = packageName,
            playbackState = playbackState?.state,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: description?.title?.toString()
                ?: notification?.title,
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: description?.subtitle?.toString()
                ?: notification?.text
        )
    }

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
