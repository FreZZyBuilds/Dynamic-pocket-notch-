package com.frezzybuilds.devnotch.ui

/** Was in einer Hälfte der eingeklappten Pille steht. */
sealed interface PillItem {
    data class Timer(val text: String) : PillItem
    data class Cost(val text: String) : PillItem
    data object MusicGlyph : PillItem
    /** Wellenform in Coverfarbe – wie Apples kompakte Musikansicht. */
    data object MusicWave : PillItem
}

data class PillSlots(val start: PillItem?, val end: PillItem?)

object PillLayout {
    /**
     * Verteilt Musik, Timer und AI-Kosten auf die zwei Hälften links/rechts der Kamera:
     * - Musik läuft: wie beim iPhone links das Cover, rechts die Wellenform. Ein laufender Timer
     *   wandert dann in den kleinen Kreis neben der Insel ([minimal]).
     * - Keine Musik: links der Timer, rechts die Kosten („$1.42 neben dem Timer“);
     *   ist nur eins von beiden aktiv, steht es rechts.
     */
    fun slots(musicPlaying: Boolean, timerText: String?, costText: String?): PillSlots {
        val timer = timerText?.let(PillItem::Timer)
        val cost = costText?.let(PillItem::Cost)
        return if (musicPlaying) {
            PillSlots(start = PillItem.MusicGlyph, end = PillItem.MusicWave)
        } else if (timer != null && cost != null) {
            PillSlots(start = timer, end = cost)
        } else {
            PillSlots(start = null, end = timer ?: cost)
        }
    }

    /**
     * Zweite Aktivität als kleiner Kreis rechts neben der Insel (Apples „minimal“-Ansicht).
     * Höchstens eine: Musik neben einer Live-Ansicht, sonst der Fokus-Timer neben Live oder Musik.
     */
    fun minimal(hasLive: Boolean, musicPlaying: Boolean, timerShown: Boolean): MinimalItem? = when {
        hasLive && musicPlaying -> MinimalItem.MUSIC
        (hasLive || musicPlaying) && timerShown -> MinimalItem.TIMER
        else -> null
    }
}

/** Inhalt des kleinen Kreises neben der Insel. */
enum class MinimalItem { MUSIC, TIMER }
