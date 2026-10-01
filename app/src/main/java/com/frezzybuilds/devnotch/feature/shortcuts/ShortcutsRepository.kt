package com.frezzybuilds.devnotch.feature.shortcuts

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow

class ShortcutsRepository(context: Context, private val dao: ProjectShortcutDao) {

    private val prefs = context.getSharedPreferences("shortcuts", Context.MODE_PRIVATE)

    val shortcuts: Flow<List<ProjectShortcut>> = dao.observeAll()

    /**
     * Legt beim allerersten Start die bisherigen festen Shortcuts an (Termux, GitHub, Projekt-Repo).
     * Nur einmal: Löscht der Nutzer alle Kacheln, kommen sie nicht ungefragt zurück.
     */
    suspend fun seedDefaultsOnce() {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        if (dao.getAll().isEmpty()) dao.insertAll(DEFAULTS.mapIndexed { i, s -> s.copy(orderIndex = i) })
        prefs.edit { putBoolean(KEY_SEEDED, true) }
    }

    suspend fun addApp(title: String, packageName: String) =
        add(ProjectShortcut(title = title.trim(), iconType = ShortcutType.SYSTEM_APP, target = packageName, orderIndex = 0))

    suspend fun addUrl(title: String, url: String) {
        val normalized = ShortcutLauncher.normalizeUrl(url) ?: return
        val name = title.trim().ifEmpty { ShortcutLauncher.hostOf(normalized) }
        add(ProjectShortcut(title = name, iconType = ShortcutType.WEB_URL, target = normalized, orderIndex = 0))
    }

    private suspend fun add(shortcut: ProjectShortcut) {
        dao.insert(shortcut.copy(orderIndex = dao.maxOrderIndex() + 1))
    }

    suspend fun delete(shortcut: ProjectShortcut) = dao.delete(shortcut)

    /** Kachel um [delta] Plätze verschieben (−1 = nach vorn, +1 = nach hinten). */
    suspend fun move(shortcut: ProjectShortcut, delta: Int) {
        val list = dao.getAll().toMutableList()
        val from = list.indexOfFirst { it.id == shortcut.id }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return
        list.add(to, list.removeAt(from))
        dao.reorder(list)
    }

    private companion object {
        const val KEY_SEEDED = "seeded"
        val DEFAULTS = listOf(
            ProjectShortcut(title = "Termux", iconType = ShortcutType.SYSTEM_APP, target = "com.termux", orderIndex = 0),
            ProjectShortcut(title = "GitHub", iconType = ShortcutType.SYSTEM_APP, target = "com.github.android", orderIndex = 0),
            ProjectShortcut(
                title = "DevNotch",
                iconType = ShortcutType.WEB_URL,
                target = "https://github.com/FreZZyBuilds/Dynamic-pocket-notch-",
                orderIndex = 0
            )
        )
    }
}
