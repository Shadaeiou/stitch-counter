package com.shadaeiou.stitchcounter.data.notes

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class NoteItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

private val json = Json { ignoreUnknownKeys = true; isLenient = true }
private val listSerializer = ListSerializer(NoteItem.serializer())

fun parseNotes(raw: String): List<NoteItem> {
    if (raw.isBlank()) return emptyList()
    return runCatching { json.decodeFromString(listSerializer, raw) }
        .getOrElse {
            // Legacy plain-text notes: surface as a single unpinned note.
            listOf(NoteItem(text = raw))
        }
}

fun List<NoteItem>.toNotesJson(): String = json.encodeToString(listSerializer, this)
