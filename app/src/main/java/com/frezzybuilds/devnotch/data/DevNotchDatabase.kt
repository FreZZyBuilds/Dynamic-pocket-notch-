package com.frezzybuilds.devnotch.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.frezzybuilds.devnotch.data.clipboard.ClipboardDao
import com.frezzybuilds.devnotch.data.clipboard.ClipboardItem
import com.frezzybuilds.devnotch.feature.notes.QuickNote
import com.frezzybuilds.devnotch.feature.notes.QuickNoteDao

@Database(entities = [ClipboardItem::class, QuickNote::class], version = 2, exportSchema = false)
abstract class DevNotchDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao
    abstract fun quickNoteDao(): QuickNoteDao

    companion object {
        /** v1 → v2: Notizen-Tabelle ergänzen, Clipboard-Historie bleibt unangetastet. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notes` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`isPinned` INTEGER NOT NULL)"
                )
            }
        }
    }
}
