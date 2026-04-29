package com.shadaeiou.stitchcounter.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shadaeiou.stitchcounter.data.db.entities.Project
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observe(id: Long): Flow<Project?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: Long): Project?

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC LIMIT 1")
    suspend fun mostRecent(): Project?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: Project): Long

    @Update
    suspend fun update(project: Project)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: Long)
}
