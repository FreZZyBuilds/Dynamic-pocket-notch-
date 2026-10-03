package com.frezzybuilds.devnotch.service

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Begleit-App für Anrufe (wie eine Smartwatch-App): Android bindet DevNotch an laufende Gespräche
 * (`CALL_COMPANION_APP`). Damit gehen Wahltasten (Töne ins Gespräch), Stumm, Lautsprecher, Halten,
 * Annehmen, Ablehnen und Auflegen direkt aus der Notch. Die Telefon-App bleibt die Anruf-Oberfläche.
 */
class CallCompanionService : InCallService() {

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = CallControl.update()
        override fun onDetailsChanged(call: Call, details: Call.Details) = CallControl.update()
    }

    override fun onCreate() {
        super.onCreate()
        CallControl.service = this
    }

    override fun onCallAdded(call: Call) {
        call.registerCallback(callback)
        CallControl.calls += call
        CallControl.update()
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        CallControl.calls -= call
        if (CallControl.calls.isEmpty()) CallControl.keypadOpen.value = false
        CallControl.update()
    }

    @Deprecated("Ab API 34 onCallEndpointChanged; für ältere Versionen weiterhin nötig")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        CallControl.audio = audioState
        CallControl.update()
    }

    override fun onDestroy() {
        CallControl.service = null
        CallControl.calls.clear()
        CallControl.update()
        super.onDestroy()
    }
}

/** Steuerung des laufenden Anrufs – nur verfügbar, solange Android DevNotch angebunden hat. */
object CallControl {
    /** Was die Notch vom Gespräch zeigt. */
    data class State(
        val caller: String,
        val ringing: Boolean,
        val dialing: Boolean,
        val onHold: Boolean,
        /** Gesprächsbeginn (System.currentTimeMillis-Basis) oder 0. */
        val connectedAt: Long,
        val muted: Boolean,
        val speaker: Boolean,
        val canHold: Boolean
    )

    // Nur solange Android den Dienst gebunden hat; onDestroy setzt beides zurück (kein Leck).
    @android.annotation.SuppressLint("StaticFieldLeak")
    internal var service: InCallService? = null
    internal val calls = mutableListOf<Call>()
    internal var audio: CallAudioState? = null

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    /** Wahltasten im Anruf-Banner aufgeklappt. */
    val keypadOpen = MutableStateFlow(false)

    /** Zuletzt im Gespräch getippte Ziffern (Anzeige über den Tasten). */
    val typed = MutableStateFlow("")

    private val main = Handler(Looper.getMainLooper())

    /** Der wichtigste Anruf: klingelnd vor aktiv vor gehalten. */
    private val current: Call?
        get() = calls.firstOrNull { it.stateCompat == Call.STATE_RINGING }
            ?: calls.firstOrNull { it.stateCompat == Call.STATE_ACTIVE || it.stateCompat == Call.STATE_DIALING || it.stateCompat == Call.STATE_CONNECTING }
            ?: calls.firstOrNull()

    internal fun update() {
        val call = current
        if (call == null) {
            _state.value = null
            typed.value = ""
            return
        }
        val details = call.details
        val name = details.callerDisplayName?.takeIf { it.isNotBlank() }
            ?: (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) details.contactDisplayName else null)?.takeIf { it.isNotBlank() }
            ?: details.handle?.schemeSpecificPart
            ?: "Anruf"
        val state = call.stateCompat
        _state.value = State(
            caller = name,
            ringing = state == Call.STATE_RINGING,
            dialing = state == Call.STATE_DIALING || state == Call.STATE_CONNECTING,
            onHold = state == Call.STATE_HOLDING,
            connectedAt = details.connectTimeMillis.takeIf { it > 0 } ?: 0L,
            muted = audio?.isMuted == true,
            speaker = audio?.route == CallAudioState.ROUTE_SPEAKER,
            canHold = details.can(Call.Details.CAPABILITY_HOLD) || details.can(Call.Details.CAPABILITY_SUPPORT_HOLD)
        )
    }

    /** Nur für Tests und Vorschauen. */
    @androidx.annotation.VisibleForTesting
    internal fun setStateForTest(value: State?) {
        _state.value = value
    }

    fun answer() = current?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
    fun reject() = current?.reject(false, null)
    fun hangUp() = current?.disconnect()

    fun toggleMute() {
        service?.setMuted(audio?.isMuted != true)
    }

    fun toggleSpeaker() {
        val on = audio?.route == CallAudioState.ROUTE_SPEAKER
        service?.setAudioRoute(if (on) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
    }

    fun toggleHold() {
        val call = current ?: return
        if (call.stateCompat == Call.STATE_HOLDING) call.unhold() else call.hold()
    }

    /** Ton ins Gespräch senden (wie die Tasten der Telefon-App). */
    fun dtmf(digit: Char) {
        val call = current ?: return
        call.playDtmfTone(digit)
        main.postDelayed({ call.stopDtmfTone() }, 160)
        typed.value = (typed.value + digit).takeLast(24)
    }

    @Suppress("DEPRECATION")
    private val Call.stateCompat: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) details.state else state
}
