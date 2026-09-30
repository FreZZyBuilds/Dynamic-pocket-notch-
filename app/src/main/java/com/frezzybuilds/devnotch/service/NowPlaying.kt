package com.frezzybuilds.devnotch.service

import android.media.session.PlaybackState

/** Was gerade läuft – das, was die Notch anzeigt. */
data class NowPlaying(
    val title: String,
    val artist: String?,
    val isPlaying: Boolean,
    val packageName: String
)

/** Momentaufnahme einer Media-Session, unabhängig von MediaController (testbar ohne Android). */
data class SessionSnapshot(
    val packageName: String,
    val playbackState: Int?,
    val title: String?,
    val artist: String?
)

object NowPlayingSelector {

    private val PLAYING_STATES = setOf(
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_SKIPPING_TO_NEXT,
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM
    )

    /** Gestoppte/fehlerhafte Sessions blendet die Notch aus; pausierte bleiben (zum Fortsetzen). */
    private val HIDDEN_STATES = setOf(
        PlaybackState.STATE_NONE,
        PlaybackState.STATE_STOPPED,
        PlaybackState.STATE_ERROR
    )

    fun isPlaying(state: Int?): Boolean = state in PLAYING_STATES

    /**
     * Wählt die anzuzeigende Session: bevorzugt eine, die gerade spielt, sonst die vom System am
     * höchsten priorisierte (die Liste ist nach Priorität sortiert). Ohne Titel → nichts anzeigen.
     * Gibt den Index in [sessions] zurück, damit der Aufrufer den passenden Controller kennt.
     */
    fun select(sessions: List<SessionSnapshot>): Int? {
        val candidates = sessions.withIndex().filter { (_, s) ->
            !s.title.isNullOrBlank() && s.playbackState !in HIDDEN_STATES
        }
        return (candidates.firstOrNull { isPlaying(it.value.playbackState) } ?: candidates.firstOrNull())
            ?.index
    }

    fun toNowPlaying(session: SessionSnapshot) = NowPlaying(
        title = session.title.orEmpty(),
        artist = session.artist?.takeIf { it.isNotBlank() },
        isPlaying = isPlaying(session.playbackState),
        packageName = session.packageName
    )
}
