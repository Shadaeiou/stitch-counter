package com.shadaeiou.stitchcounter.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.shadaeiou.stitchcounter.data.db.entities.HistoryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE projectId = :projectId ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(projectId: Long, limit: Int = 50): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history WHERE projectId = :projectId ORDER BY timestamp DESC LIMIT 1")
    suspend fun mostRecent(projectId: Long): HistoryEntry?

    @Insert
    suspend fun insert(entry: HistoryEntry): Long

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history WHERE projectId = :projectId")
    suspend fun clear(projectId: Long)
}
