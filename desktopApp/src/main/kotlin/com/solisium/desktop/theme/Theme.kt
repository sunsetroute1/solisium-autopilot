package com.solisium.desktop.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

    val Text = Color(0xFFF0F2F5)
    val TextMuted = Color(0xFFB4BEC9)
    val TextFaint = Color(0xFF8A95A3)

    /** Accent is warm to match the game's UI without competing with provenance colours. */
    val Accent = Color(0xFFE3B15C)
    val AccentSoft = Color(0x33E3B15C)
    val Cool = Color(0xFF6FB3D2)

    val Gold = Color(0xFFE8C36A)
    val Epic = Color(0xFFC9A0FF)
    val Rare = Color(0xFF6EC8FF)
    val Uncommon = Color(0xFF6FDB8A)
    val Common = Color(0xFF9AA3AE)

    /** Provenance: extracted straight from a client table. */
    val Extracted = Color(0xFF5BC8A0)

    /** Provenance: computed by us from extracted values. */
    val Derived = Color(0xFF7FA9E8)

    /** Provenance: shape or scale not yet confirmed against the game. */
    val Unverified = Color(0xFFD9915A)

    /** In-game Talking Wall: blue = true statement. */
    val WallTrue = Color(0xFF4DA3FF)

    /** In-game Talking Wall: red = false statement. */
    val WallFalse = Color(0xFFE04B4B)

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
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 52.sp,
    ),
    headlineSmall = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.5.sp,
    ),
)

/** Monospace is used wherever a raw client value or row id is shown verbatim. */
val MonoStyle = androidx.compose.ui.text.TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 18.sp,
)

/** Light thumb on near-black surfaces — default Compose scrollbars use black alpha and disappear. */
fun appScrollbarStyle(): ScrollbarStyle = defaultScrollbarStyle().copy(
    minimalHeight = 32.dp,
    thickness = 9.dp,
    shape = RoundedCornerShape(5.dp),
    hoverDurationMillis = 160,
    unhoverColor = Color(0xFF8A95A3),
    hoverColor = Color(0xFFD0D7E0),
)

@Composable
fun SolisiumTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    CompositionLocalProvider(LocalScrollbarStyle provides appScrollbarStyle()) {
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
}
