package com.frezzybuilds.devnotch.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.frezzybuilds.devnotch.data.clipboard.ClipboardDao
import com.frezzybuilds.devnotch.data.clipboard.ClipboardItem

@Database(entities = [ClipboardItem::class], version = 1, exportSchema = false)
abstract class DevNotchDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao
}
