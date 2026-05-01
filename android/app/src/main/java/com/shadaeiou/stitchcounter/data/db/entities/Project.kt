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
    // JSON-encoded list of NoteItem (see data.notes.NoteItem). Empty string =
    // no notes. Older plain-text notes are migrated automatically as a single
    // unpinned note when first parsed.
    val notes: String = "",
    // Repeating K/P pattern (e.g. "KKP"). Empty disables the indicator.
    val knitPattern: String = "",
    // HTML-formatted pattern imported from a URL. Empty = none saved.
    val patternHtml: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
