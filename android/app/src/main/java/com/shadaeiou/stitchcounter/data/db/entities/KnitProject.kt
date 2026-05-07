package com.shadaeiou.stitchcounter.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "knit_projects")
data class KnitProject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val craftType: String = "knit",
    val status: String = "queue",
    val patternSecured: Boolean = false,
    val yarnBought: Boolean = false,
    val yarnWeight: String = "",
    val needleSize: String = "",
    val patternSource: String = "",
    val notes: String = "",
    val photoPath: String? = null,
    val projectPdfPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
