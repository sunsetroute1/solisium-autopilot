package com.solisium.desktop.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Palette notes: the app is a companion window that sits beside a dark full-screen
 * game, so the surfaces are near-black with narrow steps between elevation levels.
 * Colour is reserved almost entirely for data provenance, which is the one thing the
 * user must never misread.
 */
object Palette {
    val Base = Color(0xFF0B0D10)
    val Surface = Color(0xFF13161B)
    val SurfaceHigh = Color(0xFF1A1E25)
    val SurfaceHover = Color(0xFF20252E)
    val Border = Color(0xFF262C36)
    val BorderStrong = Color(0xFF333B47)

    val Text = Color(0xFFE7EAEE)
    val TextMuted = Color(0xFF97A0AD)
    val TextFaint = Color(0xFF6B7480)

    /** Accent is warm to match the game's UI without competing with provenance colours. */
    val Accent = Color(0xFFE3B15C)
    val AccentSoft = Color(0x33E3B15C)
    val Cool = Color(0xFF6FB3D2)

    /** Provenance: extracted straight from a client table. */
    val Extracted = Color(0xFF5BC8A0)

    /** Provenance: computed by us from extracted values. */
    val Derived = Color(0xFF7FA9E8)

    /** Provenance: shape or scale not yet confirmed against the game. */
    val Unverified = Color(0xFFD9915A)

    val Danger = Color(0xFFE0685F)
}

/** Spacing scale. Kept small and named so padding stays consistent across screens. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

@Immutable
data class SolisiumShapes(val cornerSmall: androidx.compose.ui.unit.Dp = 8.dp, val corner: androidx.compose.ui.unit.Dp = 12.dp)

val LocalShapes: ProvidableCompositionLocal<SolisiumShapes> = staticCompositionLocalOf { SolisiumShapes() }

private val AppTypography = Typography(
    displaySmall = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
    ),
    bodyMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelSmall = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        letterSpacing = 0.6.sp,
    ),
)

/** Monospace is used wherever a raw client value or row id is shown verbatim. */
val MonoStyle = androidx.compose.ui.text.TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
)

@Composable
fun SolisiumTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Palette.Base,
            background = Palette.Base,
            onBackground = Palette.Text,
            surface = Palette.Surface,
            onSurface = Palette.Text,
            surfaceVariant = Palette.SurfaceHigh,
            onSurfaceVariant = Palette.TextMuted,
            outline = Palette.Border,
            error = Palette.Danger,
        ),
        typography = AppTypography,
        content = content,
    )
}
