package com.frezzybuilds.devnotch.feature.github

import android.content.Context
import androidx.core.content.edit

/**
 * Zugangsdaten für die GitHub-GraphQL-API im app-privaten Speicher
 * (von Backups und Geräteübertragung ausgeschlossen, siehe data_extraction_rules.xml).
 */
class GitHubSettings(context: Context) {
    private val prefs = context.getSharedPreferences("github", Context.MODE_PRIVATE)

    /** Personal Access Token – die GraphQL-API verlangt auch für öffentliche Daten einen. */
    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_TOKEN, value?.trim()) }

    /** Angezeigter Benutzer; leer = Inhaber des Tokens. */
    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_USERNAME, value?.trim()?.removePrefix("@")) }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_USERNAME = "username"
    }
}
