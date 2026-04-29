package com.shadaeiou.stitchcounter.ui.pdf

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class StrokePoint(val x: Float, val y: Float)

@Serializable
data class Stroke(
    val points: List<StrokePoint>,
    val colorArgb: Long = 0xFFEF4444L,
    val widthPx: Float = 6f,
)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val strokeListSerializer = ListSerializer(Stroke.serializer())

fun List<Stroke>.toJson(): String = json.encodeToString(strokeListSerializer, this)
fun strokesFromJson(raw: String): List<Stroke> =
    runCatching { json.decodeFromString(strokeListSerializer, raw) }.getOrDefault(emptyList())
