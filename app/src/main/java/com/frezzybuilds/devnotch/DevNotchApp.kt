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
    ).build()

    val notchSettings = NotchSettings(context)

    val clipboardRepository = ClipboardRepository(context, database.clipboardDao())

    val gitHubSettings = GitHubSettings(context)
    val gitHubService = GitHubService(
        client = createGitHubHttpClient(Android.create()),
        token = gitHubSettings::token
    )
}

val Context.appContainer: AppContainer
    get() = (applicationContext as DevNotchApp).container
