package com.frezzybuilds.devnotch.ui

/** Was in einer Hälfte der eingeklappten Pille steht. */
sealed interface PillItem {
    data class Timer(val text: String) : PillItem
    data class Cost(val text: String) : PillItem
    data object MusicGlyph : PillItem
    data object MusicTitle : PillItem
}

data class PillSlots(val start: PillItem?, val end: PillItem?)

object PillLayout {
    /**
     * Verteilt Musik, Timer und AI-Kosten auf die zwei Hälften links/rechts der Kamera:
     * - Musik läuft: rechts der Titel als Lauftext, links Timer, sonst Kosten, sonst ♪.
     * - Keine Musik: links der Timer, rechts die Kosten („$1.42 neben dem Timer“);
     *   ist nur eins von beiden aktiv, steht es wie bisher rechts.
     */
    fun slots(musicPlaying: Boolean, timerText: String?, costText: String?): PillSlots {
        val timer = timerText?.let(PillItem::Timer)
        val cost = costText?.let(PillItem::Cost)
        return if (musicPlaying) {
            PillSlots(start = timer ?: cost ?: PillItem.MusicGlyph, end = PillItem.MusicTitle)
        } else if (timer != null && cost != null) {
            PillSlots(start = timer, end = cost)
        } else {
            PillSlots(start = null, end = timer ?: cost)
        }
    }
}
