package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import java.util.concurrent.ConcurrentHashMap

// Warm Architectural Editorial Studio Palette (Light)
val AlabasterCanvas = Color(0xFFFAF8F5)
val PurePaper = Color(0xFFFFFFFF)
val WarmSand = Color(0xFFF2EDE6)
val SoftStone = Color(0xFFE9E2D9)
val HairlineBorder = Color(0xFFE2D9CE)
val ObsidianInk = Color(0xFF14171A)
val SlateInk = Color(0xFF525A63)
val MutedInk = Color(0xFF7B848E)

val StudioCobalt = Color(0xFF2555E6)
val StudioCobaltContainer = Color(0xFFE7EEFF)
val DeepCobaltInk = Color(0xFF12308C)

val StudioTerracotta = Color(0xFFD95D39)
val StudioTerracottaContainer = Color(0xFFFFEAE3)
val DeepTerracottaInk = Color(0xFF782811)

val StudioForest = Color(0xFF1E7F5C)
val StudioForestContainer = Color(0xFFE1F5EC)

val StudioCrimson = Color(0xFFC83E4D)
val StudioCrimsonContainer = Color(0xFFFFE5E8)

// Deep Editorial Charcoal Palette (Dark)
val CharcoalCanvas = Color(0xFF111315)
val CharcoalSurface = Color(0xFF1A1E22)
val CharcoalElevated = Color(0xFF23282E)
val CharcoalBorder = Color(0xFF2F363E)
val BoneInk = Color(0xFFF4F1EC)
val SilverInk = Color(0xFFB1B8C0)

val LuminousCobalt = Color(0xFF7FA3FF)
val DarkCobaltContainer = Color(0xFF1A2D66)
val LuminousTerracotta = Color(0xFFFF9373)
val DarkTerracottaContainer = Color(0xFF592010)
val LuminousForest = Color(0xFF5CD6A6)
val DarkForestContainer = Color(0xFF0E3D2C)
val LuminousCrimson = Color(0xFFFF8590)

data class ClientColorSwatch(
    val hex: String,
    val label: String,
    val color: Color
)

val ClientColorPalette: List<ClientColorSwatch> = listOf(
    ClientColorSwatch("#2555E6", "Cobalt", Color(0xFF2555E6)),
    ClientColorSwatch("#D95D39", "Terracotta", Color(0xFFD95D39)),
    ClientColorSwatch("#1E7F5C", "Forest", Color(0xFF1E7F5C)),
    ClientColorSwatch("#C98A1C", "Ochre", Color(0xFFC98A1C)),
    ClientColorSwatch("#7B3FA0", "Plum", Color(0xFF7B3FA0)),
    ClientColorSwatch("#187A8A", "Teal", Color(0xFF187A8A)),
    ClientColorSwatch("#C83E4D", "Crimson", Color(0xFFC83E4D)),
    ClientColorSwatch("#6D4C3D", "Espresso", Color(0xFF6D4C3D)),
)

private val HEX_COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")

private val parsedColorCache: ConcurrentHashMap<String, Color> = ConcurrentHashMap<String, Color>().apply {
    ClientColorPalette.forEach { swatch ->
        put(swatch.hex.uppercase(), swatch.color)
    }
}

fun parseClientColor(hex: String, fallback: Color = StudioCobalt): Color {
    val cleaned = hex.trim()
    if (cleaned.length != 7) return fallback
    val key = cleaned.uppercase()
    parsedColorCache[key]?.let { return it }
    return try {
        if (HEX_COLOR_REGEX.matches(cleaned)) {
            Color(android.graphics.Color.parseColor(cleaned)).also { parsedColorCache[key] = it }
        } else {
            fallback
        }
    } catch (_: Exception) {
        fallback
    }
}
