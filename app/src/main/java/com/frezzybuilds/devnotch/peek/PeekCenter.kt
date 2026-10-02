package com.frezzybuilds.devnotch.peek

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Kurze Live-Einblendungen der eingeklappten Notch („Peeks“), wie die Dynamic Island bei
 * Ereignissen: Die Pille wächst für ein paar Sekunden nach unten und zeigt, was passiert ist.
 */
sealed interface Peek {
    /** Anzeigedauer, danach schrumpft die Pille zurück. */
    val durationMs: Long

    /** Zusätzliche Höhe unter der Linsen-Zeile (dp). */
    val extraHeightDp: Int get() = 46

    /** Ladekabel angesteckt. [percent] null, wenn der Akkustand unbekannt ist. */
    data class Charging(val percent: Int?) : Peek {
        override val durationMs = 3_500L
    }

    /** Text wurde kopiert (nur wo Android das Mitlesen erlaubt, siehe ClipboardListener). */
    data class Copied(val preview: String) : Peek {
        override val durationMs = 2_500L
    }

    /** Fokus-Timer ist abgelaufen. */
    data object TimerDone : Peek {
        override val durationMs = 4_500L
    }

    /** Benachrichtigung einer App (Dauer aus den Einstellungen). */
    data class Notification(
        val notification: com.frezzybuilds.devnotch.notify.NotchNotification,
        override val durationMs: Long
    ) : Peek {
        // Kopfzeile (Titel + Lauftext) und immer die Aktionszeile („Öffnen“, ✕).
        override val extraHeightDp: Int get() = 86
    }

    /** Eingehender Anruf – bleibt groß, solange es klingelt (nicht in [PeekCenter] abgelegt). */
    data class LiveCall(val call: com.frezzybuilds.devnotch.notify.LiveActivity.Call) : Peek {
        override val durationMs = Long.MAX_VALUE
        override val extraHeightDp: Int get() = 96
    }

    /** Neuer Titel läuft. */
    /** Live-Ansicht als Banner unter der Kamera (Navigation, Anruf, Timer) – bleibt, solange sie läuft. */
    data class LiveBanner(val live: com.frezzybuilds.devnotch.notify.LiveActivity) : Peek {
        override val durationMs = Long.MAX_VALUE
        override val extraHeightDp: Int get() = 50
    }

    data class TrackChanged(val title: String, val artist: String?, val artwork: Bitmap?) : Peek {
        override val durationMs = 3_000L
    }
}

/** Prozessweite Ablage des aktuellen Peeks; der NotchContainer zeigt und beendet ihn. */
object PeekCenter {
    private val _current = MutableStateFlow<Peek?>(null)
    val current: StateFlow<Peek?> = _current.asStateFlow()

    /** Ein neuer Peek ersetzt den laufenden – das jüngste Ereignis ist das relevante. */
    fun show(peek: Peek) {
        _current.value = peek
    }

    /** Beendet [peek], aber nur, wenn inzwischen kein neuerer angezeigt wird. */
    fun dismiss(peek: Peek) {
        _current.compareAndSet(peek, null)
    }

    /** Kurzer Textauszug für Copied-Peeks: eine Zeile, höchstens [max] Zeichen. */
    fun previewOf(text: String, max: Int = 48): String {
        val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: text.trim()
        return if (line.length <= max) line else line.take(max - 1).trimEnd() + "…"
    }
}
