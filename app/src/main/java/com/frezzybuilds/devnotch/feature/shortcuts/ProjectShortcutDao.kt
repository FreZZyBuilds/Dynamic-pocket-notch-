package com.frezzybuilds.devnotch.feature.shortcuts

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectShortcutDao {

    @Query("SELECT * FROM shortcuts ORDER BY orderIndex ASC, id ASC")
    fun observeAll(): Flow<List<ProjectShortcut>>

    @Query("SELECT * FROM shortcuts ORDER BY orderIndex ASC, id ASC")
    suspend fun getAll(): List<ProjectShortcut>

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM shortcuts")
    suspend fun maxOrderIndex(): Int

    @Insert
    suspend fun insert(shortcut: ProjectShortcut): Long

    @Insert
    suspend fun insertAll(shortcuts: List<ProjectShortcut>)

    @Update
    suspend fun updateAll(shortcuts: List<ProjectShortcut>)

    @Delete
    suspend fun delete(shortcut: ProjectShortcut)

    /** Neue Reihenfolge in einem Rutsch schreiben. */
    @Transaction
    suspend fun reorder(ordered: List<ProjectShortcut>) {
        updateAll(ordered.mapIndexed { index, s -> s.copy(orderIndex = index) })
    }
}
