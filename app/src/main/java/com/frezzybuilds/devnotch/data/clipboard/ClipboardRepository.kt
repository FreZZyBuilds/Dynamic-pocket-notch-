package com.frezzybuilds.devnotch.data.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.flow.Flow

class ClipboardRepository(
    context: Context,
    private val dao: ClipDao
) {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)

    val history: Flow<List<ClipEntry>> = dao.observeAll()

    suspend fun save(text: String) {
        dao.insertAndTrim(
            ClipEntry(text = text.take(MAX_TEXT_LENGTH), createdAt = System.currentTimeMillis()),
            keep = MAX_ENTRIES
        )
    }

    /** Legt einen Eintrag wieder in die Zwischenablage (der Listener schiebt ihn dann nach oben). */
    fun copyToClipboard(entry: ClipEntry) {
        clipboard.setPrimaryClip(ClipData.newPlainText("DevNotch", entry.text))
    }

    suspend fun clear() = dao.clear()

    companion object {
        const val MAX_ENTRIES = 10
        private const val MAX_TEXT_LENGTH = 10_000
    }
}
