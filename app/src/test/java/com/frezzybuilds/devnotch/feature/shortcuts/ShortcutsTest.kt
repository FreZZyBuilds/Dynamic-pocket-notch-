package com.frezzybuilds.devnotch.feature.shortcuts

import android.app.Application
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.frezzybuilds.devnotch.data.DevNotchDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ShortcutsTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun inMemory() = Room.inMemoryDatabaseBuilder(app, DevNotchDatabase::class.java)
        .allowMainThreadQueries().build()

    @Test
    fun `migration 2 to 3 keeps clipboard and notes and adds shortcuts`() = runTest {
        val file = app.getDatabasePath("migrate3.db").apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `clips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_clips_text` ON `clips` (`text`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `isPinned` INTEGER NOT NULL)")
            db.execSQL("INSERT INTO clips (text, createdAt) VALUES ('npm run dev', 1)")
            db.execSQL("INSERT INTO notes (title, content, updatedAt, isPinned) VALUES ('Todo', '- [ ] x', 1, 0)")
            db.version = 2
        }
        val database = Room.databaseBuilder(app, DevNotchDatabase::class.java, "migrate3.db")
            .addMigrations(DevNotchDatabase.MIGRATION_1_2, DevNotchDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        assertEquals(listOf("npm run dev"), database.clipboardDao().observeAll().first().map { it.text })
        assertEquals(1, database.quickNoteDao().count())
        database.projectShortcutDao().insert(ProjectShortcut(title = "x", iconType = ShortcutType.WEB_URL, target = "https://x.dev", orderIndex = 0))
        assertEquals(ShortcutType.WEB_URL, database.projectShortcutDao().getAll().single().iconType)
        database.close()
    }

    @Test
    fun `defaults are seeded once, new tiles go to the end, move and delete work`() = runTest {
        val db = inMemory()
        val repository = ShortcutsRepository(app, db.projectShortcutDao())

        repository.seedDefaultsOnce()
        assertEquals(listOf("Termux", "GitHub", "DevNotch"), repository.shortcuts.first().map { it.title })

        repository.addUrl("", "linear.app/team")
        repository.addApp("Slack", "com.Slack")
        var titles = repository.shortcuts.first().map { it.title }
        assertEquals(listOf("Termux", "GitHub", "DevNotch", "linear.app", "Slack"), titles)

        val slack = repository.shortcuts.first().last()
        repository.move(slack, -10) // ganz nach vorn
        titles = repository.shortcuts.first().map { it.title }
        assertEquals("Slack", titles.first())

        // Alle löschen: Standard-Kacheln kommen nicht ungefragt zurück.
        repository.shortcuts.first().forEach { repository.delete(it) }
        repository.seedDefaultsOnce()
        assertEquals(emptyList<ProjectShortcut>(), repository.shortcuts.first())
        db.close()
    }

    @Test
    fun `urls are normalized for typical dev inputs`() {
        assertEquals("https://github.com/user/repo", ShortcutLauncher.normalizeUrl("github.com/user/repo"))
        assertEquals("http://localhost:3000", ShortcutLauncher.normalizeUrl("localhost:3000"))
        assertEquals("http://10.0.2.2:8080/api", ShortcutLauncher.normalizeUrl("10.0.2.2:8080/api"))
        assertEquals("http://192.168.1.5:5173", ShortcutLauncher.normalizeUrl("http://192.168.1.5:5173"))
        assertNull(ShortcutLauncher.normalizeUrl("nur text"))
        assertNull(ShortcutLauncher.normalizeUrl("dashboard"))
        assertNull(ShortcutLauncher.normalizeUrl(""))
    }

    @Test
    fun `launch opens urls and falls back to the install page for missing apps`() {
        ShortcutLauncher.launch(app, ProjectShortcut(title = "Repo", iconType = ShortcutType.WEB_URL, target = "https://github.com/x/y", orderIndex = 0))
        var started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://github.com/x/y", started.dataString)

        // Termux nicht installiert → F-Droid, andere Apps → Play Store
        ShortcutLauncher.launch(app, ProjectShortcut(title = "Termux", iconType = ShortcutType.SYSTEM_APP, target = "com.termux", orderIndex = 0))
        assertEquals("https://f-droid.org/packages/com.termux/", shadowOf(app).nextStartedActivity.dataString)

        ShortcutLauncher.launch(app, ProjectShortcut(title = "Slack", iconType = ShortcutType.SYSTEM_APP, target = "com.Slack", orderIndex = 0))
        started = shadowOf(app).nextStartedActivity
        assertEquals("market://details?id=com.Slack", started.dataString)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, started.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
