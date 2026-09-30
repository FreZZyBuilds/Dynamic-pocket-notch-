package com.frezzybuilds.devnotch.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.frezzybuilds.devnotch.service.NotchLayoutMode

/** Persistente Einstellungen der Notch. Der Overlay-Service beobachtet Änderungen live. */
class NotchSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val isTablet = context.resources.configuration.smallestScreenWidthDp >= 600

    /** Ohne gespeicherte Wahl: Tablets als Edge Bar, Smartphones am Punch-Hole. */
    var displayMode: NotchLayoutMode
        get() = prefs.getString(KEY_DISPLAY_MODE, null)
            ?.let { runCatching { NotchLayoutMode.valueOf(it) }.getOrNull() }
            ?: if (isTablet) NotchLayoutMode.EDGE_SIDE else NotchLayoutMode.NOTCH_TOP
        set(value) = prefs.edit { putString(KEY_DISPLAY_MODE, value.name) }

    /**
     * Ruft [onChange] bei jeder Änderung des Display-Modus auf. Der zurückgegebene Listener muss
     * gehalten und an [removeListener] übergeben werden (SharedPreferences hält ihn nur schwach).
     */
    fun addDisplayModeListener(onChange: (NotchLayoutMode) -> Unit): SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_DISPLAY_MODE) onChange(displayMode)
        }.also(prefs::registerOnSharedPreferenceChangeListener)

    fun removeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private companion object {
        const val KEY_DISPLAY_MODE = "display_mode"
    }
}
