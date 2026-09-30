package com.frezzybuilds.devnotch.data.github

import android.content.Context
import androidx.core.content.edit

/** Personal Access Token im app-privaten Speicher (allowBackup=false: landet nicht in Backups). */
class GitHubTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("github", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_TOKEN, value?.trim()) }

    private companion object {
        const val KEY_TOKEN = "token"
    }
}
