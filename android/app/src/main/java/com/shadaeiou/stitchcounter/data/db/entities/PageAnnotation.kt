package com.shadaeiou.stitchcounter.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "annotations",
    primaryKeys = ["projectId", "page"],
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("projectId")],
)
data class PageAnnotation(
    val projectId: Long,
    val page: Int,
    val strokesJson: String = "[]",
    val updatedAt: Long = System.currentTimeMillis(),
)
