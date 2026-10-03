package com.frezzybuilds.devnotch

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.frezzybuilds.devnotch.data.DevNotchDatabase
import com.frezzybuilds.devnotch.data.createJsonHttpClient
import com.frezzybuilds.devnotch.feature.billing.ProAccess
import com.frezzybuilds.devnotch.feature.billing.RevenueCatBilling
import com.frezzybuilds.devnotch.feature.aiusage.AiUsageApi
import com.frezzybuilds.devnotch.feature.aiusage.AiUsageRepository
import com.frezzybuilds.devnotch.feature.aiusage.AiUsageSettings
import com.frezzybuilds.devnotch.data.clipboard.ClipboardRepository
import com.frezzybuilds.devnotch.data.settings.NotchSettings
import com.frezzybuilds.devnotch.feature.github.GitHubService
import com.frezzybuilds.devnotch.feature.github.GitHubSettings
import com.frezzybuilds.devnotch.feature.notes.NotesRepository
import com.frezzybuilds.devnotch.feature.shortcuts.ShortcutsRepository
import com.frezzybuilds.devnotch.feature.notes.QuickNotesStore
import io.ktor.client.engine.android.Android

class DevNotchApp : Application() {
    // Bewusst außerhalb des (lazy) Containers: RevenueCat startet in onCreate, ohne dass
    // Datenbank, HTTP-Client & Co. schon beim Prozessstart gebaut werden.
    val proAccess by lazy { ProAccess(this, debugBuild = BuildConfig.DEBUG) }
    val billing by lazy { RevenueCatBilling(proAccess) }

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Abstürze lokal mitschreiben (nur für den Diagnose-Bericht, den der Nutzer selbst teilt).
        com.frezzybuilds.devnotch.diag.CrashLog.install(this)
        // RevenueCat früh starten: Kaufstatus (Entitlement „pro“) steht dann bereit, wenn
        // Notch oder Einstellungen ihn brauchen. Mit dem Platzhalter-Key läuft die App im Free-Modus.
        billing.configure(this, BuildConfig.REVENUECAT_API_KEY, BuildConfig.DEBUG)
    }
}

/** Minimale manuelle Dependency-Injection: eine Instanz pro Prozess. */
class AppContainer(app: DevNotchApp) {
    private val context: Context = app

    private val database = Room.databaseBuilder(
        context,
        DevNotchDatabase::class.java,
        "devnotch.db"
    )
        // v1 → v2 Notizen, v2 → v3 Shortcuts; bestehende Daten bleiben erhalten.
        .addMigrations(DevNotchDatabase.MIGRATION_1_2, DevNotchDatabase.MIGRATION_2_3)
        .build()

    val notchSettings = NotchSettings(context)

    val clipboardRepository = ClipboardRepository(context, database.clipboardDao()) { proAccess.isPro.value }

    val shortcutsRepository = ShortcutsRepository(context, database.projectShortcutDao())

    val notesRepository = NotesRepository(database.quickNoteDao(), QuickNotesStore(context))

    /** Ein Ktor-Client für alle JSON-APIs. */
    private val httpClient = createJsonHttpClient(Android.create())

    val gitHubSettings = GitHubSettings(context)
    val gitHubService = GitHubService(
        client = httpClient,
        token = gitHubSettings::token
    )

    /** Pro-Status (RevenueCat-Entitlement; in Debug-Builds zusätzlich Test-Freischaltung). */
    val proAccess: ProAccess = app.proAccess
    val billing: RevenueCatBilling = app.billing

    val aiUsageSettings = AiUsageSettings(context)
    val aiUsageRepository = AiUsageRepository(AiUsageApi(httpClient), aiUsageSettings)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as DevNotchApp).container
