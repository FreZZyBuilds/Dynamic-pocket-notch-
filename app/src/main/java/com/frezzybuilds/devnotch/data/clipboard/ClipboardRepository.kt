package com.frezzybuilds.devnotch.data.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.frezzybuilds.devnotch.feature.billing.ProLimits
import com.frezzybuilds.devnotch.feature.billing.ProPlan
import kotlinx.coroutines.flow.Flow

class ClipboardRepository(
    context: Context,
    private val dao: ClipboardDao,
    /** Pro = unbegrenzter Verlauf, Free = die letzten [ProPlan.FREE_CLIPBOARD_ENTRIES]. */
    private val isPro: () -> Boolean = { false }
) {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)

    val history: Flow<List<ClipboardItem>> = dao.observeAll()

    suspend fun save(text: String) {
        val entry = ClipboardItem(text = text.take(MAX_TEXT_LENGTH), timestamp = System.currentTimeMillis())
        val keep = ProLimits.clipboardEntries(isPro())
        if (keep == null) dao.insert(entry) else dao.insertAndTrim(entry, keep)
    }

    /** Legt einen Eintrag wieder in die Zwischenablage (der Listener schiebt ihn dann nach oben). */
    fun copyToClipboard(entry: ClipboardItem) {
        clipboard.setPrimaryClip(ClipData.newPlainText("DevNotch", entry.text))
    }

    suspend fun clear() = dao.clear()

    companion object {
        private const val MAX_TEXT_LENGTH = 10_000
    }
}
