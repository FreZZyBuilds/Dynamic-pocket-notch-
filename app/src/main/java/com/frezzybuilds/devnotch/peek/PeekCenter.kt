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
        // Wie beim iPhone: Blitz links, Akku mit Prozent rechts der Kamera – keine Zusatzhöhe.
        override val extraHeightDp: Int get() = 0
    }

    /** Lautstärke geändert (wie die Lautstärke-Anzeige in der Insel ab iOS 17). [level] 0…1. */
    data class Volume(val level: Float, val stream: Int) : Peek {
        override val durationMs = 1_600L
        override val extraHeightDp: Int get() = 0
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
        override val durationMs: Long,
        val style: com.frezzybuilds.devnotch.notify.NotificationStyle = com.frezzybuilds.devnotch.notify.NotificationStyle.CLASSIC,
        /** Weitere aktuelle Benachrichtigungen derselben App („+N weitere“). */
        val more: Int = 0
    ) : Peek {
        override val extraHeightDp: Int get() = when (style) {
            // Kopfzeile (Titel + Lauftext) und immer die Aktionszeile („Öffnen“, ✕).
            com.frezzybuilds.devnotch.notify.NotificationStyle.CLASSIC -> 86
            // Eine schmale Zeile wie auf dem iPhone.
            com.frezzybuilds.devnotch.notify.NotificationStyle.COMPACT -> 40
            // Karte mit Kopf, Text und Fußzeile („+N weitere“ oder Aktionen).
            com.frezzybuilds.devnotch.notify.NotificationStyle.GLASS -> 122
            // Name in der Kamerazeile, nur die Textzeile darunter.
            com.frezzybuilds.devnotch.notify.NotificationStyle.APERTURE -> 22
        }
    }

    /** Eingehender Anruf – bleibt groß, solange es klingelt (nicht in [PeekCenter] abgelegt). */
    data class LiveCall(val call: com.frezzybuilds.devnotch.notify.LiveActivity.Call) : Peek {
        override val durationMs = Long.MAX_VALUE
        override val extraHeightDp: Int get() = 96
    }

    /** Wecker klingelt – groß mit „Schlummern“ und „Stopp“, solange er klingelt. */
    data class LiveAlarm(val alarm: com.frezzybuilds.devnotch.notify.LiveActivity.Alarm) : Peek {
        override val durationMs = Long.MAX_VALUE
        override val extraHeightDp: Int get() = 92
    }

    /** Neuer Titel läuft. */
    /**
     * Systemereignis wie auf dem iPhone: Lautlos, Nicht stören, Energiesparen, Akku schwach,
     * Kopfhörer, Entsperren. [tint] ist eine ARGB-Farbe für Symbol und Wert.
     */
    data class System(
        val event: com.frezzybuilds.devnotch.system.SystemEvent,
        val symbol: String,
        val title: String,
        val value: String?,
        val tint: Long
    ) : Peek {
        override val durationMs = if (event == com.frezzybuilds.devnotch.system.SystemEvent.UNLOCK) 1_300L else 2_200L
        // Wie beim iPhone: Symbol links, Text rechts der Kamera – die Pille wird nur breiter.
        override val extraHeightDp: Int get() = 0
    }

    /** LocalSend: ein Gerät möchte Dateien senden – Annehmen oder Ablehnen (wie AirDrop). */
    data class ShareRequest(val request: com.frezzybuilds.devnotch.share.localsend.IncomingRequest) : Peek {
        override val durationMs = 60_000L
        override val extraHeightDp: Int get() = 92
    }

    /** NameDrop läuft: Handys aneinanderhalten oder QR-Code zeigen. */
    data class NameDrop(val shownAt: Long = java.lang.System.nanoTime()) : Peek {
        override val durationMs = com.frezzybuilds.devnotch.share.NameDropSession.SESSION_MS
        override val extraHeightDp: Int get() = 150
    }

    /** Bezahlt (Google/Samsung Wallet) – wie die Apple-Pay-Bestätigung in der Insel. */
    data class Payment(val merchant: String, val amount: String) : Peek {
        override val durationMs = 3_500L
        override val extraHeightDp: Int get() = 64
    }

    /** Großer Player (Gedrückthalten bei Musik); [shownAt] macht jede Bedienung zu einem neuen Peek. */
    data class MusicPlayer(val shownAt: Long = java.lang.System.nanoTime()) : Peek {
        override val durationMs = 8_000L
        override val extraHeightDp: Int get() = 112
    }

    /**
     * Live-Ansicht als Banner unter der Kamera (Navigation, Anruf, Timer) – bleibt, solange sie läuft.
     * [keypad]: Anrufsteuerung aufgeklappt; [companion]: Android hat DevNotch an den Anruf gebunden
     * (dann gibt es auch Wahltasten im Gespräch).
     */
    data class LiveBanner(
        val live: com.frezzybuilds.devnotch.notify.LiveActivity,
        val keypad: Boolean = false,
        val companion: Boolean = true
    ) : Peek {
        override val durationMs = Long.MAX_VALUE
        // Anrufe: zu nur „Steuerung“ und „Auflegen“; aufgeklappt Stumm/Lautsprecher/Halten und Tasten.
        override val extraHeightDp: Int get() = when {
            live !is com.frezzybuilds.devnotch.notify.LiveActivity.Call -> 50
            keypad && companion -> 262
            keypad -> 124
            else -> 88
        }
    }

    /** Neuer Titel: Cover links, Titel mit Wellenform rechts der Kamera – keine Zusatzhöhe. */
    data class TrackChanged(val title: String, val artist: String?, val artwork: Bitmap?) : Peek {
        override val durationMs = 3_000L
        override val extraHeightDp: Int get() = 0
    }
}

/** Fester Name für Diagnose-Berichte (Klassennamen sind im Release-Build verschleiert). */
fun debugName(peek: Peek): String = when (peek) {
    is Peek.Charging -> "Laden"
    is Peek.Copied -> "Kopiert"
    Peek.TimerDone -> "Timer fertig"
    is Peek.Notification -> "Nachricht (${peek.style.label})"
    is Peek.LiveCall -> "Anruf klingelt"
    is Peek.LiveAlarm -> "Wecker klingelt"
    is Peek.System -> "System"
    is Peek.ShareRequest -> "LocalSend"
    is Peek.NameDrop -> "NameDrop"
    is Peek.Payment -> "Bezahlt"
    is Peek.MusicPlayer -> "Musik-Player"
    is Peek.LiveBanner -> "Live-Banner"
    is Peek.Volume -> "Lautstärke"
    is Peek.TrackChanged -> "Neuer Titel"
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
