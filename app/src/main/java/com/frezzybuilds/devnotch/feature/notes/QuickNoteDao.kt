package com.frezzybuilds.devnotch.feature.notes

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickNoteDao {

    @Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<QuickNote>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): QuickNote?

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Insert
    suspend fun insert(note: QuickNote): Long

    @Update
    suspend fun update(note: QuickNote)

    @Delete
    suspend fun delete(note: QuickNote)
}
