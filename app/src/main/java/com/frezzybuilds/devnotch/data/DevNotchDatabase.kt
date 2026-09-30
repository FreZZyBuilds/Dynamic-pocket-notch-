package com.frezzybuilds.devnotch.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.frezzybuilds.devnotch.data.clipboard.ClipDao
import com.frezzybuilds.devnotch.data.clipboard.ClipEntry

@Database(entities = [ClipEntry::class], version = 1, exportSchema = false)
abstract class DevNotchDatabase : RoomDatabase() {
    abstract fun clipDao(): ClipDao
}
