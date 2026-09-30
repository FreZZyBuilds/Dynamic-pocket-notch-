package com.frezzybuilds.devnotch.data.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.flow.Flow

class ClipboardRepository(
    context: Context,
    private val dao: ClipboardDao
) {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)

    val history: Flow<List<ClipboardItem>> = dao.observeAll()

    suspend fun save(text: String) {
        dao.insertAndTrim(
            ClipboardItem(text = text.take(MAX_TEXT_LENGTH), timestamp = System.currentTimeMillis()),
            keep = MAX_ENTRIES
        )
    }

    /** Legt einen Eintrag wieder in die Zwischenablage (der Listener schiebt ihn dann nach oben). */
    fun copyToClipboard(entry: ClipboardItem) {
        clipboard.setPrimaryClip(ClipData.newPlainText("DevNotch", entry.text))
    }

    suspend fun clear() = dao.clear()

    companion object {
        const val MAX_ENTRIES = 10
        private const val MAX_TEXT_LENGTH = 10_000
    }
}
