package com.shadaeiou.stitchcounter.ui.theme

import androidx.compose.ui.graphics.Color

val BgBlack = Color(0xFF0A0A0A)
val SurfaceDark = Color(0xFF141414)
val OnSurface = Color(0xFFE5E5E5)
val Accent = Color(0xFF22C55E)
val AccentRed = Color(0xFFEF4444)
val Muted = Color(0xFF6B7280)

val FlashWhite = Color(0xCCFFFFFF)
val FlashRed = Color(0xCCEF4444)

data class CounterBgChoice(val label: String, val argb: Long)

val CounterBackgrounds: List<CounterBgChoice> = listOf(
    CounterBgChoice("Black",   0xFF0A0A0AL),
    CounterBgChoice("Slate",   0xFF1B2A3AL),
    CounterBgChoice("Indigo",  0xFF1E2A78L),
    CounterBgChoice("Teal",    0xFF0E4F4FL),
    CounterBgChoice("Forest",  0xFF134E2BL),
    CounterBgChoice("Plum",    0xFF3F1A5CL),
    CounterBgChoice("Wine",    0xFF5C1530L),
)

data class PenColorChoice(val label: String, val argb: Long)

val PenColors: List<PenColorChoice> = listOf(
    PenColorChoice("Red",        0xFFEF4444L),
    PenColorChoice("Light Pink", 0xFFFFB6C1L),
    PenColorChoice("Gold",       0xFFFFD700L),
    PenColorChoice("Kelly Green",0xFF4CBB17L),
    PenColorChoice("Cyan",       0xFF00CED1L),
    PenColorChoice("Light Blue", 0xFFADD8E6L),
    PenColorChoice("Silver",     0xFFC0C0C0L),
    PenColorChoice("Black",      0xFF000000L),
    PenColorChoice("White",      0xFFFFFFFFL),
)
