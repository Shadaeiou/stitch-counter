package com.shadaeiou.stitchcounter.ui.theme

import androidx.compose.ui.graphics.Color

val BgBlack = Color(0xFF0A0A0A)
val SurfaceDark = Color(0xFF141414)
val OnSurface = Color(0xFFE5E5E5)
val Accent = Color(0xFF22C55E)
val AccentRed = Color(0xFFEF4444)
val Muted = Color(0xFF6B7280)

val FlashGreen = Color(0x6622C55E)
val FlashRed = Color(0x66EF4444)

data class CounterBgChoice(val label: String, val argb: Long)

val CounterBackgrounds: List<CounterBgChoice> = listOf(
    CounterBgChoice("Black", 0xFF0A0A0AL),
    CounterBgChoice("Charcoal", 0xFF1F1F1FL),
    CounterBgChoice("Navy", 0xFF0F1729L),
    CounterBgChoice("Forest", 0xFF0A1F0FL),
    CounterBgChoice("Plum", 0xFF1A0F1FL),
    CounterBgChoice("Wine", 0xFF1F0A0AL),
)
