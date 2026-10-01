package com.frezzybuilds.devnotch

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.frezzybuilds.devnotch.data.DevNotchDatabase
import com.frezzybuilds.devnotch.data.clipboard.ClipboardRepository
import com.frezzybuilds.devnotch.data.settings.NotchSettings
import com.frezzybuilds.devnotch.feature.github.GitHubService
import com.frezzybuilds.devnotch.feature.github.GitHubSettings
import com.frezzybuilds.devnotch.feature.github.createGitHubHttpClient
import com.frezzybuilds.devnotch.feature.notes.NotesRepository
import com.frezzybuilds.devnotch.feature.notes.QuickNotesStore
import io.ktor.client.engine.android.Android

class DevNotchApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/** Minimale manuelle Dependency-Injection: eine Instanz pro Prozess. */
class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(
        context,
        DevNotchDatabase::class.java,
        "devnotch.db"
    )
        // v1 → v2 ergänzt die Notizen; die Clipboard-Historie bleibt erhalten.
        .addMigrations(DevNotchDatabase.MIGRATION_1_2)
        .build()

    val notchSettings = NotchSettings(context)

    val clipboardRepository = ClipboardRepository(context, database.clipboardDao())

    val notesRepository = NotesRepository(database.quickNoteDao(), QuickNotesStore(context))

    val gitHubSettings = GitHubSettings(context)
    val gitHubService = GitHubService(
        client = createGitHubHttpClient(Android.create()),
        token = gitHubSettings::token
    )
}

val Context.appContainer: AppContainer
    get() = (applicationContext as DevNotchApp).container
