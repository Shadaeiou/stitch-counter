package com.shadaeiou.stitchcounter.data.repo

import com.shadaeiou.stitchcounter.data.db.KnitProjectDao
import com.shadaeiou.stitchcounter.data.db.entities.KnitProject
import kotlinx.coroutines.flow.Flow

class KnitProjectRepository(private val dao: KnitProjectDao) {

    fun observeAll(): Flow<List<KnitProject>> = dao.observeAll()

    suspend fun create(): Long {
        val now = System.currentTimeMillis()
        return dao.insert(KnitProject(createdAt = now, updatedAt = now))
    }

    suspend fun update(project: KnitProject) {
        dao.update(project.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
