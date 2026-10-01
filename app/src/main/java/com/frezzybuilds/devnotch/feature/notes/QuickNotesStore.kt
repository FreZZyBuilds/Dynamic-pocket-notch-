package com.frezzybuilds.devnotch.feature.notes

import android.content.Context
import androidx.core.content.edit

/**
 * Früherer Einzel-Notizzettel (SharedPreferences). Wird nur noch gelesen, um den Text einmalig
 * in die Room-Notizen zu übernehmen (NotesRepository.migrateLegacyScratchpad).
 */
class QuickNotesStore(context: Context) {
    private val prefs = context.getSharedPreferences("notes", Context.MODE_PRIVATE)

    var text: String
        get() = prefs.getString(KEY_TEXT, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_TEXT, value) }

    private companion object {
        const val KEY_TEXT = "scratch"
    }
}
