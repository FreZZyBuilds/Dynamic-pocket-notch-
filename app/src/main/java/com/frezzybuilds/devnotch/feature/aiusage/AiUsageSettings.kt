package com.frezzybuilds.devnotch.feature.aiusage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Keys, Monatslimit und Anzeige-Option. App-privat gespeichert und – wie alle App-Daten –
 * von Cloud-Backup und Geräteübertragung ausgeschlossen (data_extraction_rules.xml).
 */
class AiUsageSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ai_usage", Context.MODE_PRIVATE)

    fun key(provider: AiProvider): String? =
        prefs.getString(keyName(provider), null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setKey(provider: AiProvider, value: String?) =
        prefs.edit { putString(keyName(provider), value?.trim()) }

    val configuredProviders: List<AiProvider>
        get() = AiProvider.entries.filter { key(it) != null }

    /** Monatslimit in USD für den Fortschrittsbalken. */
    var monthlyLimitUsd: Double
        get() = prefs.getFloat(KEY_LIMIT, 20f).toDouble()
        set(value) = prefs.edit { putFloat(KEY_LIMIT, value.toFloat()) }

    /** Kosten auch eingeklappt in der Notch (neben dem Timer) zeigen. */
    var showInPill: Boolean
        get() = prefs.getBoolean(KEY_SHOW_IN_PILL, false)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_IN_PILL, value) }

    /** Lokaler Cache der letzten Abfrage (JSON). */
    var cacheJson: String?
        get() = prefs.getString(KEY_CACHE, null)
        set(value) = prefs.edit { putString(KEY_CACHE, value) }

    data class Display(val limitUsd: Double, val showInPill: Boolean)

    val display: Display get() = Display(monthlyLimitUsd, showInPill)

    fun displayFlow(): Flow<Display> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_LIMIT || key == KEY_SHOW_IN_PILL) trySend(display)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(display)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun keyName(provider: AiProvider) = "key_${provider.name.lowercase()}"

    private companion object {
        const val KEY_LIMIT = "monthly_limit"
        const val KEY_SHOW_IN_PILL = "show_in_pill"
        const val KEY_CACHE = "cache"
    }
}
