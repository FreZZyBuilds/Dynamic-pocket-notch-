package com.frezzybuilds.devnotch.share

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.frezzybuilds.devnotch.peek.Peek
import com.frezzybuilds.devnotch.peek.PeekCenter
import com.frezzybuilds.devnotch.system.SystemEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * NameDrop für Android: Solange eine Sitzung läuft, gibt sich das Handy per NFC als Tag mit der
 * eigenen Kontaktkarte aus. Ein anderes Android-Handy, das man daran hält, liest sie wie einen
 * NFC-Aufkleber und bietet „Kontakt speichern“ an – mit DevNotch direkt aus der Notch, sonst über
 * die Kontakte-App. Außerhalb einer Sitzung antwortet der Dienst nicht (für Leser unsichtbar).
 */
object NameDropSession {
    sealed interface State {
        data object Idle : State
        data class Active(val card: ContactCard) : State
        data class Sent(val card: ContactCard) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    private var emulator: Type4TagEmulator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stop() }

    /** Sitzung für [card] öffnen (endet nach [SESSION_MS] oder mit [stop]). */
    fun start(card: ContactCard) {
        val ndef = Ndef.mimeRecord(Ndef.VCARD_MIME, card.toVCard().toByteArray(Charsets.UTF_8))
        emulator = Type4TagEmulator(ndef) {
            handler.post {
                _state.value = State.Sent(card)
                PeekCenter.show(Peek.System(SystemEvent.SHARE, "✓", "Kontakt gesendet", card.name, 0xFF30D158L))
                handler.removeCallbacks(timeout)
                handler.postDelayed(timeout, 1_500)
            }
        }
        _state.value = State.Active(card)
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, SESSION_MS)
    }

    fun stop() {
        emulator = null
        handler.removeCallbacks(timeout)
        _state.value = State.Idle
    }

    /** APDU vom NFC-Leser; ohne Sitzung „nicht gefunden“. */
    fun process(apdu: ByteArray): ByteArray = emulator?.process(apdu) ?: Type4TagEmulator.SW_NOT_FOUND

    /** NFC vorhanden und eingeschaltet? null = Gerät ohne NFC. */
    fun nfcEnabled(context: Context): Boolean? = NfcAdapter.getDefaultAdapter(context)?.isEnabled

    const val SESSION_MS = 60_000L
}

/** Leitet NFC-Befehle an die laufende NameDrop-Sitzung weiter. */
class NameDropApduService : HostApduService() {
    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray =
        NameDropSession.process(commandApdu)

    override fun onDeactivated(reason: Int) = Unit
}
