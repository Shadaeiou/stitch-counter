package com.shadaeiou.stitchcounter.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "Untitled",
    val label: String = "",
    val count: Int = 0,
    val currentPage: Int = 0,
    val pdfPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
