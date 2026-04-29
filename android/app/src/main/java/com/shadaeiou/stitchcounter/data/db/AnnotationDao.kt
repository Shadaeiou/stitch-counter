package com.shadaeiou.stitchcounter.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shadaeiou.stitchcounter.data.db.entities.PageAnnotation
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE projectId = :projectId AND page = :page LIMIT 1")
    fun observe(projectId: Long, page: Int): Flow<PageAnnotation?>

    @Query("SELECT * FROM annotations WHERE projectId = :projectId AND page = :page LIMIT 1")
    suspend fun get(projectId: Long, page: Int): PageAnnotation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(annotation: PageAnnotation)

    @Query("DELETE FROM annotations WHERE projectId = :projectId")
    suspend fun clearForProject(projectId: Long)
}
