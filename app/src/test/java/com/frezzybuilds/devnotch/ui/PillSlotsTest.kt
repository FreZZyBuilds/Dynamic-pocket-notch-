package com.frezzybuilds.devnotch.ui

import com.frezzybuilds.devnotch.feature.aiusage.formatUsd
import org.junit.Assert.assertEquals
import org.junit.Test

class PillSlotsTest {

    private val timer = PillItem.Timer("⏱ 24:13")
    private val cost = PillItem.Cost("$1.42")

    @Test
    fun `cost sits next to the timer`() {
        assertEquals(PillSlots(timer, cost), PillLayout.slots(false, "⏱ 24:13", "$1.42"))
    }

    @Test
    fun `a single item stays on the right as before`() {
        assertEquals(PillSlots(null, timer), PillLayout.slots(false, "⏱ 24:13", null))
        assertEquals(PillSlots(null, cost), PillLayout.slots(false, null, "$1.42"))
        assertEquals(PillSlots(null, null), PillLayout.slots(false, null, null))
    }

    @Test
    fun `music shows artwork and waveform like the iPhone`() {
        val music = PillSlots(PillItem.MusicGlyph, PillItem.MusicWave)
        assertEquals(music, PillLayout.slots(true, "⏱ 24:13", "$1.42"))
        assertEquals(music, PillLayout.slots(true, null, null))
    }

    @Test
    fun `second activity goes into the small circle`() {
        assertEquals(MinimalItem.MUSIC, PillLayout.minimal(hasLive = true, musicPlaying = true, timerShown = true))
        assertEquals(MinimalItem.TIMER, PillLayout.minimal(hasLive = true, musicPlaying = false, timerShown = true))
        assertEquals(MinimalItem.TIMER, PillLayout.minimal(hasLive = false, musicPlaying = true, timerShown = true))
        assertEquals(null, PillLayout.minimal(hasLive = false, musicPlaying = false, timerShown = true))
        assertEquals(null, PillLayout.minimal(hasLive = true, musicPlaying = false, timerShown = false))
    }

    @Test
    fun `usd formatting stays short enough for the pill`() {
        assertEquals("$1.42", formatUsd(1.423))
        assertEquals("$0.00", formatUsd(0.0))
        assertEquals("$27", formatUsd(27.4))
    }
}
