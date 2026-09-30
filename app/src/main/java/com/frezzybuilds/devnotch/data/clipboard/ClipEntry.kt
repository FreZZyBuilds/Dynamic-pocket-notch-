package com.frezzybuilds.devnotch.data.clipboard

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Ein Eintrag der Zwischenablage-Historie. `text` ist eindeutig, Duplikate rutschen nach oben. */
@Entity(tableName = "clips", indices = [Index(value = ["text"], unique = true)])
data class ClipEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long
)
