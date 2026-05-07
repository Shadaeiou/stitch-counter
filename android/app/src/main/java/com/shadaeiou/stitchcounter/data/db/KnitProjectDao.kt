package com.shadaeiou.stitchcounter.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.shadaeiou.stitchcounter.data.db.entities.KnitProject
import kotlinx.coroutines.flow.Flow

@Dao
interface KnitProjectDao {
    @Query("SELECT * FROM knit_projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<KnitProject>>

    @Insert
    suspend fun insert(project: KnitProject): Long

    @Update
    suspend fun update(project: KnitProject)

    @Query("DELETE FROM knit_projects WHERE id = :id")
    suspend fun delete(id: Long)
}
