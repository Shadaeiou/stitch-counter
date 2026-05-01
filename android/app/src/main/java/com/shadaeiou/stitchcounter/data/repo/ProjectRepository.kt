package com.shadaeiou.stitchcounter.data.repo

import com.shadaeiou.stitchcounter.data.db.AnnotationDao
import com.shadaeiou.stitchcounter.data.db.HistoryDao
import com.shadaeiou.stitchcounter.data.db.ProjectDao
import com.shadaeiou.stitchcounter.data.db.entities.HistoryEntry
import com.shadaeiou.stitchcounter.data.db.entities.PageAnnotation
import com.shadaeiou.stitchcounter.data.db.entities.Project
import com.shadaeiou.stitchcounter.ui.pdf.Stroke
import com.shadaeiou.stitchcounter.ui.pdf.strokesFromJson
import com.shadaeiou.stitchcounter.ui.pdf.toJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val historyDao: HistoryDao,
    private val annotationDao: AnnotationDao,
) {
    fun observeProject(id: Long): Flow<Project?> = projectDao.observe(id)
    fun observeHistory(projectId: Long): Flow<List<HistoryEntry>> = historyDao.observeRecent(projectId)
    fun observeStrokes(projectId: Long, page: Int): Flow<List<Stroke>> =
        annotationDao.observe(projectId, page).map { it?.let { a -> strokesFromJson(a.strokesJson) } ?: emptyList() }

    suspend fun ensureProject(): Project {
        return projectDao.mostRecent() ?: run {
            val id = projectDao.insert(Project(name = "Project 1"))
            projectDao.get(id)!!
        }
    }

    suspend fun increment(project: Project): Project {
        val updated = project.copy(count = project.count + 1, updatedAt = System.currentTimeMillis())
        projectDao.update(updated)
        historyDao.insert(HistoryEntry(
            projectId = project.id, type = "up",
            fromCount = project.count, toCount = updated.count,
        ))
        return updated
    }

    suspend fun decrement(project: Project): Project {
        if (project.count <= 0) return project
        val updated = project.copy(count = project.count - 1, updatedAt = System.currentTimeMillis())
        projectDao.update(updated)
        historyDao.insert(HistoryEntry(
            projectId = project.id, type = "down",
            fromCount = project.count, toCount = updated.count,
        ))
        return updated
    }

    suspend fun reset(project: Project): Project {
        val updated = project.copy(count = 0, updatedAt = System.currentTimeMillis())
        projectDao.update(updated)
        historyDao.insert(HistoryEntry(
            projectId = project.id, type = "reset",
            fromCount = project.count, toCount = 0,
        ))
        return updated
    }

    suspend fun setLabel(project: Project, label: String): Project {
        val updated = project.copy(label = label, updatedAt = System.currentTimeMillis())
        projectDao.update(updated)
        return updated
    }

    suspend fun setPdf(project: Project, path: String?): Project {
        val updated = project.copy(pdfPath = path, currentPage = 0, updatedAt = System.currentTimeMillis())
        projectDao.update(updated)
        return updated
    }

    suspend fun setPage(project: Project, page: Int): Project {
        val updated = project.copy(currentPage = page, updatedAt = System.currentTimeMillis())
        projectDao.update(updated)
        return updated
    }

    suspend fun setNotes(project: Project, notes: String): Project {
        val updated = project.copy(notes = notes, updatedAt = System.currentTimeMillis())
        projectDao.update(updated)
        return updated
    }

    suspend fun setKnitPattern(project: Project, pattern: String): Project {
        val updated = project.copy(knitPattern = pattern, updatedAt = System.currentTimeMillis())
        projectDao.update(updated)
        return updated
    }

    suspend fun setPatternHtml(project: Project, html: String): Project {
        val updated = project.copy(patternHtml = html, updatedAt = System.currentTimeMillis())
        projectDao.update(updated)
        return updated
    }

    suspend fun saveStrokes(projectId: Long, page: Int, strokes: List<Stroke>) {
        annotationDao.upsert(PageAnnotation(projectId = projectId, page = page, strokesJson = strokes.toJson()))
    }

    suspend fun undoLast(project: Project): Project {
        val last = historyDao.mostRecent(project.id) ?: return project
        val updated = project.copy(count = last.fromCount, updatedAt = System.currentTimeMillis())
        projectDao.update(updated)
        historyDao.delete(last.id)
        return updated
    }
}
