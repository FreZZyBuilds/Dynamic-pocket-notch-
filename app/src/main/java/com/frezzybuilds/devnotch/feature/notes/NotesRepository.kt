package com.frezzybuilds.devnotch.feature.notes

import kotlinx.coroutines.flow.Flow

class NotesRepository(
    private val dao: QuickNoteDao,
    private val legacyScratchpad: QuickNotesStore,
    private val clock: () -> Long = System::currentTimeMillis
) {
    /** Angepinnte zuerst, sonst zuletzt bearbeitete zuerst. */
    val notes: Flow<List<QuickNote>> = dao.observeAll()

    suspend fun create(): Long = dao.insert(QuickNote(title = "", content = "", updatedAt = clock()))

    suspend fun get(id: Long): QuickNote? = dao.get(id)

    suspend fun notesIsEmpty(): Boolean = dao.count() == 0

    suspend fun save(id: Long, title: String, content: String) {
        val note = dao.get(id) ?: return
        if (note.title == title && note.content == content) return
        dao.update(note.copy(title = title, content = content, updatedAt = clock()))
    }

    suspend fun togglePin(id: Long) {
        val note = dao.get(id) ?: return
        dao.update(note.copy(isPinned = !note.isPinned))
    }

    suspend fun delete(id: Long) {
        dao.get(id)?.let { dao.delete(it) }
    }

    /**
     * Übernimmt den Text des früheren Einzel-Notizzettels (SharedPreferences) einmalig als
     * erste Notiz, damit beim Umstieg auf Room nichts verloren geht.
     */
    suspend fun migrateLegacyScratchpad() {
        val legacy = legacyScratchpad.text
        if (legacy.isBlank()) return
        dao.insert(QuickNote(title = "Notizzettel", content = legacy, updatedAt = clock()))
        legacyScratchpad.text = ""
    }
}
