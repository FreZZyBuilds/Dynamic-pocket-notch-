package com.frezzybuilds.devnotch.feature.notes

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.frezzybuilds.devnotch.data.DevNotchDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotesStorageTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `migration chain 1 to 3 keeps the clipboard history and adds notes and shortcuts`() = runTest {
        // Datenbank im Zustand von Version 1 (nur Clipboard), wie sie auf Geräten liegt.
        val file = context.getDatabasePath("migrate.db").apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `clips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_clips_text` ON `clips` (`text`)")
            db.execSQL("INSERT INTO clips (text, createdAt) VALUES ('git status', 1)")
            db.version = 1
        }

        val database = Room.databaseBuilder(context, DevNotchDatabase::class.java, "migrate.db")
            .addMigrations(DevNotchDatabase.MIGRATION_1_2, DevNotchDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        assertEquals(listOf("git status"), database.clipboardDao().observeAll().first().map { it.text })
        assertEquals(0, database.projectShortcutDao().getAll().size)
        database.quickNoteDao().insert(QuickNote(title = "t", content = "c", updatedAt = 1))
        assertEquals(1, database.quickNoteDao().count())
        database.close()
    }

    @Test
    fun `legacy scratchpad becomes the first note once, pinned notes sort first`() = runTest {
        val database = Room.inMemoryDatabaseBuilder(context, DevNotchDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val legacy = QuickNotesStore(context).apply { text = "alter Zettel" }
        var now = 100L
        val repository = NotesRepository(database.quickNoteDao(), legacy) { now++ }

        repository.migrateLegacyScratchpad()
        repository.migrateLegacyScratchpad() // zweiter Aufruf darf nichts doppeln
        val newer = repository.create()
        repository.save(newer, "Neu", "inhalt")

        var notes = repository.notes.first()
        assertEquals(listOf("Neu", "Notizzettel"), notes.map { it.title })
        assertEquals("alter Zettel", notes.last().content)
        assertEquals("", legacy.text)

        repository.togglePin(notes.last().id)
        notes = repository.notes.first()
        assertEquals("Notizzettel", notes.first().title)
        database.close()
    }
}
