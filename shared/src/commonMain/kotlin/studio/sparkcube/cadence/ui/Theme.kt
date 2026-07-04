package studio.sparkcube.cadence.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Semantic colors; swapped wholesale between light and dark. */
data class Palette(
    val paper: Color,
    val card: Color,
    val hairline: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val highlight: Color,
    val highlightInk: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val error: Color,
    val isDark: Boolean,
)

val LightPalette = Palette(
    paper = Color(0xFFF7F4EE),
    card = Color(0xFFFFFFFF),
    hairline = Color(0xFFE9E3D8),
    ink = Color(0xFF1F1B15),
    muted = Color(0xFF6C6558),
    faint = Color(0xFFA9A091),
    highlight = Color(0xFFFFE7A6),
    highlightInk = Color(0xFF2A2418),
    accent = Color(0xFF2563EB),
    accentSoft = Color(0xFFE6EDFC),
    onAccent = Color(0xFFFFFFFF),
    error = Color(0xFFB3261E),
    isDark = false,
)

val DarkPalette = Palette(
    paper = Color(0xFF16161A),
    card = Color(0xFF212127),
    hairline = Color(0xFF2E2E36),
    ink = Color(0xFFECE7DD),
    muted = Color(0xFFA39C8F),
    faint = Color(0xFF6E6A61),
    highlight = Color(0xFF4A4324),
    highlightInk = Color(0xFFF3ECDD),
    accent = Color(0xFF5B8DEF),
    accentSoft = Color(0xFF29303F),
    onAccent = Color(0xFF0E1116),
    error = Color(0xFFF2B8B5),
    isDark = true,
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** On-screen reading font (independent of the narration voice). */
enum class ReadingFont(val label: String, val family: FontFamily) {
    Serif("Serif", FontFamily.Serif),
    Sans("Sans", FontFamily.SansSerif),
}
