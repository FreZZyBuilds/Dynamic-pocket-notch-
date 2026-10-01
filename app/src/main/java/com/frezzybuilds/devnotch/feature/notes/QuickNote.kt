package com.frezzybuilds.devnotch.feature.notes

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Eine Markdown-Notiz. Angepinnte Notizen stehen in der Auswahl immer vorn. */
@Entity(tableName = "notes")
data class QuickNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val updatedAt: Long,
    val isPinned: Boolean = false
) {
    val displayTitle: String
        get() = title.ifBlank { content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(24) ?: "Ohne Titel" }
}
