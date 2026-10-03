package com.frezzybuilds.devnotch.feature.shortcuts

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ShortcutType { SYSTEM_APP, WEB_URL }

/** Kachel im Dev-Dashboard: startet eine installierte App oder öffnet eine URL. */
@Entity(tableName = "shortcuts")
data class ProjectShortcut(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val iconType: ShortcutType,
    /** Package-Name (SYSTEM_APP) oder URL (WEB_URL). */
    val target: String,
    val orderIndex: Int
)
