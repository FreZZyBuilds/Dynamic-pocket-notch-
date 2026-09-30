package com.frezzybuilds.devnotch.data.clipboard

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ClipDao {

    @Query("SELECT * FROM clips ORDER BY createdAt DESC")
    abstract fun observeAll(): Flow<List<ClipEntry>>

    /** REPLACE + Unique-Index auf `text`: Erneut kopierter Text wird neu (oben) eingefügt. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(entry: ClipEntry)

    @Query(
        "DELETE FROM clips WHERE id NOT IN " +
            "(SELECT id FROM clips ORDER BY createdAt DESC LIMIT :keep)"
    )
    protected abstract suspend fun trimTo(keep: Int)

    @Transaction
    open suspend fun insertAndTrim(entry: ClipEntry, keep: Int) {
        insert(entry)
        trimTo(keep)
    }

    @Query("DELETE FROM clips")
    abstract suspend fun clear()
}
