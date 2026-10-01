package com.frezzybuilds.devnotch

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.frezzybuilds.devnotch.data.DevNotchDatabase
import com.frezzybuilds.devnotch.data.createJsonHttpClient
import com.frezzybuilds.devnotch.feature.aiusage.AiUsageApi
import com.frezzybuilds.devnotch.feature.aiusage.AiUsageRepository
import com.frezzybuilds.devnotch.feature.aiusage.AiUsageSettings
import com.frezzybuilds.devnotch.data.clipboard.ClipboardRepository
import com.frezzybuilds.devnotch.data.settings.NotchSettings
import com.frezzybuilds.devnotch.feature.github.GitHubService
import com.frezzybuilds.devnotch.feature.github.GitHubSettings
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

    /** Ein Ktor-Client für alle JSON-APIs. */
    private val httpClient = createJsonHttpClient(Android.create())

    val gitHubSettings = GitHubSettings(context)
    val gitHubService = GitHubService(
        client = httpClient,
        token = gitHubSettings::token
    )

    val aiUsageSettings = AiUsageSettings(context)
    val aiUsageRepository = AiUsageRepository(AiUsageApi(httpClient), aiUsageSettings)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as DevNotchApp).container
