package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalIsDarkTheme = staticCompositionLocalOf { true }

fun parseHexColor(hex: String, fallback: Color = BoldRed): Color {
    return try {
        val clean = hex.removePrefix("#")
        val colorLong = when (clean.length) {
            6 -> 0xFF000000L or clean.toLong(16)
            8 -> clean.toLong(16)
            else -> return fallback
        }
        Color(colorLong.toInt())
    } catch (_: Exception) {
        fallback
    }
}

private fun srgbToLinear(c: Float): Float {
    return if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055) / 1.055), 2.4).toFloat()
}

fun calculateOnColor(accent: Color): Color {
    val rLin = srgbToLinear(accent.red)
    val gLin = srgbToLinear(accent.green)
    val bLin = srgbToLinear(accent.blue)
    val lum = 0.2126f * rLin + 0.7152f * gLin + 0.0722f * bLin
    // Luminance threshold: 0.22 ensures Bold Red (#E50914, lum=0.1690) and Crimson (#B80710, lum=0.1038) resolve to White (#FFFFFF),
    // while brighter swatches (Orange lum=0.2819, Green lum=0.3639, Cyan lum=0.3825, Pink lum=0.2477) resolve to Near-Black (#111827).
    return if (lum > 0.22f) Color(0xFF111827) else Color(0xFFFFFFFF)
}

val ColorScheme.textTertiary: Color
    @Composable get() = if (LocalIsDarkTheme.current) TextTertiaryDark else TextTertiaryLight

val ColorScheme.controlBorder: Color
    @Composable get() = if (LocalIsDarkTheme.current) ControlBorderDark else ControlBorderLight

val ColorScheme.badgeTextRed: Color
    @Composable get() = if (LocalIsDarkTheme.current) BoldRedLight else BoldRedDark

@Composable
fun appSwitchColors(): SwitchColors {
    val isDark = LocalIsDarkTheme.current
    return SwitchDefaults.colors(
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        uncheckedTrackColor = if (isDark) SwitchTrackDark else MaterialTheme.colorScheme.surfaceContainerHighest,
        uncheckedThumbColor = if (isDark) SwitchThumbDark else MaterialTheme.colorScheme.outline,
        uncheckedBorderColor = if (isDark) SwitchBorderDark else MaterialTheme.colorScheme.outline
    )
}

@Composable
fun HomeWorkoutTheme(
    darkTheme: Boolean = true,
    accentColorHex: String = "#E50914",
    content: @Composable () -> Unit
) {
    val rawAccent = parseHexColor(accentColorHex, BoldRed)

    val darkScheme = darkColorScheme(
        primary = rawAccent,
        onPrimary = calculateOnColor(rawAccent),
        primaryContainer = rawAccent.copy(alpha = 0.22f),
        onPrimaryContainer = BoldRedLight,
        secondary = BoldRedLight,
        onSecondary = TextWhite,
        secondaryContainer = DarkCharcoal,
        onSecondaryContainer = BoldRedLight,
        tertiary = BoldRedDark,
        onTertiary = TextWhite,
        background = PureBlack,
        onBackground = TextWhite,
        surface = DarkCharcoal,
        onSurface = TextWhite,
        surfaceVariant = CharcoalElevated,
        onSurfaceVariant = TextLightGray,
        surfaceDim = DarkCharcoal,
        surfaceBright = CharcoalElevated,
        surfaceContainerLowest = PureBlack,
        surfaceContainerLow = DarkCharcoal,
        surfaceContainer = CharcoalElevated,
        surfaceContainerHigh = CharcoalElevated,
        surfaceContainerHighest = CharcoalBorder,
        inverseSurface = TextWhite,
        inverseOnSurface = TextPrimaryLight,
        inversePrimary = BoldRed,
        outline = CharcoalBorder,
        outlineVariant = Color(0xFF262626),
        surfaceTint = rawAccent,
        scrim = Color.Black,
        error = BoldRed,
        onError = TextWhite
    )

    val lightScheme = lightColorScheme(
        primary = rawAccent,
        onPrimary = calculateOnColor(rawAccent),
        primaryContainer = rawAccent.copy(alpha = 0.12f),
        onPrimaryContainer = BoldRedDark, // #B80710 (5.53:1 contrast on 12% container)
        secondary = BoldRed,
        onSecondary = TextWhite,
        secondaryContainer = Color(0xFFF3F4F6),
        onSecondaryContainer = Color(0xFF1F2937),
        tertiary = BoldRedDark,
        onTertiary = TextWhite,
        background = LightBg,
        onBackground = TextPrimaryLight,
        surface = LightSurface,
        onSurface = TextPrimaryLight,
        surfaceVariant = LightSurfaceElevated,
        onSurfaceVariant = TextSecondaryLight,
        surfaceDim = Color(0xFFE5E7EB),
        surfaceBright = LightSurface,
        surfaceContainerLowest = LightSurface,
        surfaceContainerLow = LightBg,
        surfaceContainer = Color(0xFFF3F4F6),
        surfaceContainerHigh = Color(0xFFE5E7EB),
        surfaceContainerHighest = Color(0xFFD1D5DB),
        inverseSurface = TextPrimaryLight,
        inverseOnSurface = TextWhite,
        inversePrimary = BoldRedLight,
        outline = LightBorder,
        outlineVariant = Color(0xFFE5E7EB),
        surfaceTint = Color.Transparent,
        scrim = Color.Black,
        error = BoldRed,
        onError = TextWhite
    )

    val colorScheme = if (darkTheme) darkScheme else lightScheme

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
