package com.frezzybuilds.devnotch.service

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingSelectorTest {

    private fun session(pkg: String, state: Int?, title: String? = "Song $pkg", artist: String? = "Artist") =
        SessionSnapshot(pkg, state, title, artist)

    @Test
    fun `playing session wins over higher-priority paused one`() {
        val sessions = listOf(
            session("spotify", PlaybackState.STATE_PAUSED),
            session("youtube", PlaybackState.STATE_PLAYING)
        )
        assertEquals(1, NowPlayingSelector.select(sessions))
    }

    @Test
    fun `paused session is still shown so it can be resumed`() {
        val index = NowPlayingSelector.select(listOf(session("spotify", PlaybackState.STATE_PAUSED)))
        assertEquals(0, index)
        assertFalse(NowPlayingSelector.toNowPlaying(session("spotify", PlaybackState.STATE_PAUSED)).isPlaying)
    }

    @Test
    fun `stopped sessions and sessions without title are ignored`() {
        val sessions = listOf(
            session("a", PlaybackState.STATE_STOPPED),
            session("b", PlaybackState.STATE_PLAYING, title = null),
            session("c", PlaybackState.STATE_NONE)
        )
        assertNull(NowPlayingSelector.select(sessions))
    }

    @Test
    fun `buffering counts as playing, blank artist becomes null`() {
        val nowPlaying = NowPlayingSelector.toNowPlaying(
            session("x", PlaybackState.STATE_BUFFERING, title = "Track", artist = " ")
        )
        assertTrue(nowPlaying.isPlaying)
        assertEquals("Track", nowPlaying.title)
        assertNull(nowPlaying.artist)
    }
}
