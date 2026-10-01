package com.frezzybuilds.devnotch.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.frezzybuilds.devnotch.ui.BeamLook
import com.frezzybuilds.devnotch.ui.BeamMode
import com.frezzybuilds.devnotch.ui.BeamStyle
import com.frezzybuilds.devnotch.ui.BeamPalette
import androidx.core.content.edit
import com.frezzybuilds.devnotch.notify.LivePrefs
import com.frezzybuilds.devnotch.notify.LockContent
import com.frezzybuilds.devnotch.notify.NotifyPrefs
import com.frezzybuilds.devnotch.service.EdgeSide
import com.frezzybuilds.devnotch.service.LockscreenMode
import com.frezzybuilds.devnotch.service.NotchLayoutMode
import com.frezzybuilds.devnotch.service.isTablet
import com.frezzybuilds.devnotch.ui.media.EdgeTheme
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Darstellungsoptionen des Edge-Players. */
data class EdgePrefs(
    val autoMinimize: Boolean,
    val minimizeDelaySeconds: Int,
    val showOnTrackChange: Boolean,
    val theme: EdgeTheme
)

data class BeamPrefs(val mode: BeamMode, val look: BeamLook)

/** Persistente Einstellungen der Notch. Der Overlay-Service beobachtet Änderungen live. */
class NotchSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val isTablet = context.isTablet()

    /** Ohne gespeicherte Wahl: Tablets als Edge Bar, Smartphones am Punch-Hole. */
    var displayMode: NotchLayoutMode
        get() = prefs.getString(KEY_DISPLAY_MODE, null)
            ?.let { runCatching { NotchLayoutMode.valueOf(it) }.getOrNull() }
            ?: if (isTablet) NotchLayoutMode.EDGE_SIDE else NotchLayoutMode.NOTCH_TOP
        set(value) = prefs.edit { putString(KEY_DISPLAY_MODE, value.name) }

    /**
     * Hat der Nutzer die Notch eingeschaltet? Steuert den Autostart nach dem Booten
     * (BootReceiver). Wird nur durch den Schalter in der App bzw. „Beenden“ in der
     * Benachrichtigung geändert – nicht, wenn Android den Prozess beendet.
     */
    var notchEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTCH_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTCH_ENABLED, value) }

    /** Hersteller-Hinweis (Xiaomi/Samsung) wurde als erledigt markiert. */
    var oemHintDone: Boolean
        get() = prefs.getBoolean(KEY_OEM_HINT_DONE, false)
        set(value) = prefs.edit { putBoolean(KEY_OEM_HINT_DONE, value) }

    /** Edge-Player klappt nach einigen Sekunden ohne Interaktion zur runden Cover-Bubble ein. */
    var edgeAutoMinimize: Boolean
        get() = prefs.getBoolean(KEY_EDGE_AUTO_MINIMIZE, true)
        set(value) = prefs.edit { putBoolean(KEY_EDGE_AUTO_MINIMIZE, value) }

    /** Wartezeit bis zum Einklappen in Sekunden (3, 5 oder 10). */
    var edgeMinimizeDelaySeconds: Int
        get() = prefs.getInt(KEY_EDGE_MINIMIZE_DELAY, 5)
        set(value) = prefs.edit { putInt(KEY_EDGE_MINIMIZE_DELAY, value) }

    /** Bei neuem Titel kurz die volle Leiste zeigen. */
    var edgeShowOnTrackChange: Boolean
        get() = prefs.getBoolean(KEY_EDGE_SHOW_ON_TRACK, true)
        set(value) = prefs.edit { putBoolean(KEY_EDGE_SHOW_ON_TRACK, value) }

    var edgeTheme: EdgeTheme
        get() = prefs.getString(KEY_EDGE_THEME, null)
            ?.let { runCatching { EdgeTheme.valueOf(it) }.getOrNull() }
            ?: EdgeTheme.ALBUM
        set(value) = prefs.edit { putString(KEY_EDGE_THEME, value.name) }

    /** Andock-Rand der verschiebbaren Bubble/Leiste. */
    var edgeSide: EdgeSide
        get() = prefs.getString(KEY_EDGE_SIDE, null)
            ?.let { runCatching { EdgeSide.valueOf(it) }.getOrNull() }
            ?: EdgeSide.RIGHT
        set(value) = prefs.edit { putString(KEY_EDGE_SIDE, value.name) }

    /** Vertikale Position als Anteil der Bildschirmhöhe (0 = Mitte), übersteht Rotation. */
    var edgeOffsetFraction: Float
        get() = prefs.getFloat(KEY_EDGE_OFFSET, 0f)
        set(value) = prefs.edit { putFloat(KEY_EDGE_OFFSET, value) }

    /** Lichtlauf um die Notch: wann er kreist und in welchen Farben. */
    var beamMode: BeamMode
        get() = prefs.getString(KEY_BEAM_MODE, null)
            ?.let { name -> BeamMode.entries.firstOrNull { it.name == name } }
            ?: BeamMode.ALWAYS
        set(value) = prefs.edit { putString(KEY_BEAM_MODE, value.name) }

    var beamPalette: BeamPalette
        get() = prefs.getString(KEY_BEAM_PALETTE, null)
            ?.let { name -> BeamPalette.entries.firstOrNull { it.name == name } }
            ?: BeamPalette.GEMINI
        set(value) = prefs.edit { putString(KEY_BEAM_PALETTE, value.name) }

    var beamStyle: BeamStyle
        get() = prefs.getString(KEY_BEAM_STYLE, null)
            ?.let { name -> BeamStyle.entries.firstOrNull { it.name == name } }
            ?: BeamStyle.BEAM
        set(value) = prefs.edit { putString(KEY_BEAM_STYLE, value.name) }

    /** Sekunden pro Runde. */
    var beamLapSeconds: Float
        get() = prefs.getFloat(KEY_BEAM_LAP, BeamLook.DEFAULT_LAP_SECONDS)
        set(value) = prefs.edit { putFloat(KEY_BEAM_LAP, value.coerceIn(BeamLook.LAP_RANGE)) }

    var beamBrightness: Float
        get() = prefs.getFloat(KEY_BEAM_BRIGHTNESS, BeamLook.DEFAULT_BRIGHTNESS)
        set(value) = prefs.edit { putFloat(KEY_BEAM_BRIGHTNESS, value.coerceIn(BeamLook.BRIGHTNESS_RANGE)) }

    var beamLength: Float
        get() = prefs.getFloat(KEY_BEAM_LENGTH, BeamLook.DEFAULT_LENGTH)
        set(value) = prefs.edit { putFloat(KEY_BEAM_LENGTH, value.coerceIn(BeamLook.LENGTH_RANGE)) }

    val beamPrefs: BeamPrefs
        get() = BeamPrefs(beamMode, BeamLook(beamStyle, beamPalette, beamLapSeconds, beamBrightness, beamLength))

    /** Änderungen wirken sofort in der laufenden Notch. */
    fun beamPrefsFlow(): Flow<BeamPrefs> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in BEAM_PREF_KEYS) trySend(beamPrefs)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(beamPrefs)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val edgePrefs: EdgePrefs
        get() = EdgePrefs(edgeAutoMinimize, edgeMinimizeDelaySeconds, edgeShowOnTrackChange, edgeTheme)

    /** Alle Edge-Darstellungsoptionen als Flow: Änderungen wirken sofort in der laufenden Notch. */
    fun edgePrefsFlow(): Flow<EdgePrefs> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in EDGE_PREF_KEYS) trySend(edgePrefs)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(edgePrefs)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /**
     * Ruft [onChange] bei jeder Änderung des Display-Modus auf. Der zurückgegebene Listener muss
     * gehalten und an [removeListener] übergeben werden (SharedPreferences hält ihn nur schwach).
     */
    fun addDisplayModeListener(onChange: (NotchLayoutMode) -> Unit): SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_DISPLAY_MODE) onChange(displayMode)
        }.also(prefs::registerOnSharedPreferenceChangeListener)

    /** Allgemeiner Listener für mehrere Schlüssel (Referenz halten, mit [removeListener] lösen). */
    fun addListener(keys: Set<String>, onChange: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in keys) onChange()
        }.also(prefs::registerOnSharedPreferenceChangeListener)

    // --- Benachrichtigungen & Live-Ansichten ---------------------------------------------------
    var notifyEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFY_ENABLED, value) }
    var notifyDuration: Float
        get() = prefs.getFloat(KEY_NOTIFY_DURATION, NotifyPrefs.DEFAULT_DURATION_SECONDS)
        set(value) = prefs.edit { putFloat(KEY_NOTIFY_DURATION, value.coerceIn(NotifyPrefs.DURATION_RANGE)) }
    var notifyLockContent: LockContent
        get() = prefs.getString(KEY_NOTIFY_LOCK_CONTENT, null)
            ?.let { name -> LockContent.entries.firstOrNull { it.name == name } }
            ?: LockContent.APP_ONLY
        set(value) = prefs.edit { putString(KEY_NOTIFY_LOCK_CONTENT, value.name) }
    var notifySkipOngoing: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_SKIP_ONGOING, true)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFY_SKIP_ONGOING, value) }
    var notifySkipSilent: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_SKIP_SILENT, true)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFY_SKIP_SILENT, value) }
    var notifyRespectDnd: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_DND, true)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFY_DND, value) }
    var notifyBlockedApps: Set<String>
        get() = prefs.getStringSet(KEY_NOTIFY_BLOCKED, emptySet()).orEmpty().toSet()
        set(value) = prefs.edit { putStringSet(KEY_NOTIFY_BLOCKED, value) }
    var liveCalls: Boolean
        get() = prefs.getBoolean(KEY_LIVE_CALLS, true)
        set(value) = prefs.edit { putBoolean(KEY_LIVE_CALLS, value) }
    var liveNavigation: Boolean
        get() = prefs.getBoolean(KEY_LIVE_NAV, true)
        set(value) = prefs.edit { putBoolean(KEY_LIVE_NAV, value) }
    var liveTimers: Boolean
        get() = prefs.getBoolean(KEY_LIVE_TIMERS, true)
        set(value) = prefs.edit { putBoolean(KEY_LIVE_TIMERS, value) }
    var liveProgress: Boolean
        get() = prefs.getBoolean(KEY_LIVE_PROGRESS, true)
        set(value) = prefs.edit { putBoolean(KEY_LIVE_PROGRESS, value) }

    val notifyPrefs: NotifyPrefs
        get() = NotifyPrefs(
            enabled = notifyEnabled,
            durationSeconds = notifyDuration,
            lockContent = notifyLockContent,
            skipOngoing = notifySkipOngoing,
            skipSilent = notifySkipSilent,
            respectDnd = notifyRespectDnd,
            blockedApps = notifyBlockedApps,
            live = LivePrefs(liveCalls, liveNavigation, liveTimers, liveProgress)
        )

    fun notifyPrefsFlow(): Flow<NotifyPrefs> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in NOTIFY_PREF_KEYS) trySend(notifyPrefs)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(notifyPrefs)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** Apps, die Benachrichtigungen geschickt haben (Paket → Name), höchstens 60, neueste zuerst. */
    val seenApps: Map<String, String>
        get() = prefs.getString(KEY_NOTIFY_SEEN, null).orEmpty().lineSequence()
            .mapNotNull { line -> line.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap(LinkedHashMap())

    fun rememberSeenApp(packageName: String, label: String) {
        val current = seenApps
        if (current.keys.firstOrNull() == packageName) return
        val updated = LinkedHashMap<String, String>().apply {
            put(packageName, label)
            current.forEach { (pkg, name) -> if (pkg != packageName && size < 60) put(pkg, name) }
        }
        prefs.edit { putString(KEY_NOTIFY_SEEN, updated.entries.joinToString("\n") { "${it.key}\t${it.value}" }) }
    }

    var lockscreenMode: LockscreenMode
        get() = prefs.getString(KEY_LOCKSCREEN_MODE, null)
            ?.let { name -> LockscreenMode.entries.firstOrNull { it.name == name } }
            ?: LockscreenMode.SHOW
        set(value) = prefs.edit { putString(KEY_LOCKSCREEN_MODE, value.name) }

    fun removeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val KEY_LOCKSCREEN_MODE = "lockscreen_mode"
        const val KEY_NOTIFY_ENABLED = "notify_enabled"
        const val KEY_NOTIFY_DURATION = "notify_duration"
        const val KEY_NOTIFY_LOCK_CONTENT = "notify_lock_content"
        const val KEY_NOTIFY_SKIP_ONGOING = "notify_skip_ongoing"
        const val KEY_NOTIFY_SKIP_SILENT = "notify_skip_silent"
        const val KEY_NOTIFY_DND = "notify_dnd"
        const val KEY_NOTIFY_BLOCKED = "notify_blocked"
        const val KEY_NOTIFY_SEEN = "notify_seen_apps"
        const val KEY_LIVE_CALLS = "live_calls"
        const val KEY_LIVE_NAV = "live_navigation"
        const val KEY_LIVE_TIMERS = "live_timers"
        const val KEY_LIVE_PROGRESS = "live_progress"
        val NOTIFY_PREF_KEYS = setOf(
            KEY_NOTIFY_ENABLED, KEY_NOTIFY_DURATION, KEY_NOTIFY_LOCK_CONTENT, KEY_NOTIFY_SKIP_ONGOING,
            KEY_NOTIFY_SKIP_SILENT, KEY_NOTIFY_DND, KEY_NOTIFY_BLOCKED,
            KEY_LIVE_CALLS, KEY_LIVE_NAV, KEY_LIVE_TIMERS, KEY_LIVE_PROGRESS
        )
        private const val KEY_DISPLAY_MODE = "display_mode"
        const val KEY_NOTCH_ENABLED = "notch_enabled"
        const val KEY_OEM_HINT_DONE = "oem_hint_done"
        const val KEY_EDGE_AUTO_MINIMIZE = "edge_auto_minimize"
        const val KEY_EDGE_MINIMIZE_DELAY = "edge_minimize_delay"
        const val KEY_EDGE_SHOW_ON_TRACK = "edge_show_on_track"
        const val KEY_EDGE_THEME = "edge_theme"
        const val KEY_EDGE_SIDE = "edge_side"
        const val KEY_EDGE_OFFSET = "edge_offset"
        const val KEY_BEAM_MODE = "beam_mode"
        const val KEY_BEAM_PALETTE = "beam_palette"
        const val KEY_BEAM_STYLE = "beam_style"
        const val KEY_BEAM_LAP = "beam_lap_seconds"
        const val KEY_BEAM_BRIGHTNESS = "beam_brightness"
        const val KEY_BEAM_LENGTH = "beam_length"
        val BEAM_PREF_KEYS = setOf(
            KEY_BEAM_MODE, KEY_BEAM_PALETTE, KEY_BEAM_STYLE, KEY_BEAM_LAP, KEY_BEAM_BRIGHTNESS, KEY_BEAM_LENGTH
        )
        val EDGE_PREF_KEYS = setOf(
            KEY_EDGE_AUTO_MINIMIZE, KEY_EDGE_MINIMIZE_DELAY, KEY_EDGE_SHOW_ON_TRACK, KEY_EDGE_THEME
        )
    }
}
