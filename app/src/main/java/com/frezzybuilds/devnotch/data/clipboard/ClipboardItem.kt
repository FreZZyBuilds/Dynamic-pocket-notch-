package com.frezzybuilds.devnotch.data.clipboard

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Ein Eintrag der Zwischenablage-Historie. `text` ist eindeutig, Duplikate rutschen nach oben. */
@Entity(tableName = "clips", indices = [Index(value = ["text"], unique = true)])
data class ClipboardItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    // Spaltenname bleibt "createdAt": identisches Schema, keine Migration bestehender DBs nötig.
    @ColumnInfo(name = "createdAt") val timestamp: Long
)
