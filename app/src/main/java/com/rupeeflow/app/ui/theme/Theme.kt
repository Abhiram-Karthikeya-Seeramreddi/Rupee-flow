package com.rupeeflow.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rupeeflow.app.data.ThemeMode

val Brand = Color(0xFF5B4CF0)
val BrandDeep = Color(0xFF2F2399)
val Mint = Color(0xFF1FBF8F)
val Coral = Color(0xFFF2545B)
val Amber = Color(0xFFF5A524)

@Immutable
data class ExtraColors(
    val positive: Color,
    val negative: Color,
    val warning: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val cardBorder: Color,
    val subtle: Color,
)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(Mint, Coral, Amber, Brand, BrandDeep, Color(0x14000000), Color(0xFF8A8799))
}

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E2FF),
    onPrimaryContainer = Color(0xFF1C1269),
    secondary = Color(0xFF0F9D77),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F5E9),
    onSecondaryContainer = Color(0xFF00382A),
    tertiary = Color(0xFFE2683C),
    tertiaryContainer = Color(0xFFFFE2D6),
    background = Color(0xFFF7F7FB),
    onBackground = Color(0xFF16141F),
    surface = Color(0xFFF7F7FB),
    onSurface = Color(0xFF16141F),
    surfaceVariant = Color(0xFFECEAF4),
    onSurfaceVariant = Color(0xFF5E5B6E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF1F0F7),
    surfaceContainerHigh = Color(0xFFEBE9F3),
    surfaceContainerHighest = Color(0xFFE4E2EE),
    outline = Color(0xFFC9C6D6),
    outlineVariant = Color(0xFFE3E1EC),
    error = Coral,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA79DFF),
    onPrimary = Color(0xFF1C1269),
    primaryContainer = Color(0xFF3B30A8),
    onPrimaryContainer = Color(0xFFE6E2FF),
    secondary = Color(0xFF5FE0B6),
    onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF0B4F3D),
    onSecondaryContainer = Color(0xFFD3F5E9),
    tertiary = Color(0xFFFFA27F),
    tertiaryContainer = Color(0xFF5A2A18),
    background = Color(0xFF0F0E17),
    onBackground = Color(0xFFEAE8F2),
    surface = Color(0xFF0F0E17),
    onSurface = Color(0xFFEAE8F2),
    surfaceVariant = Color(0xFF26243A),
    onSurfaceVariant = Color(0xFFB4B1C6),
    surfaceContainerLowest = Color(0xFF0B0A12),
    surfaceContainerLow = Color(0xFF17151F),
    surfaceContainer = Color(0xFF1B1926),
    surfaceContainerHigh = Color(0xFF23212F),
    surfaceContainerHighest = Color(0xFF2C2A3A),
    outline = Color(0xFF4A4760),
    outlineVariant = Color(0xFF2E2C3E),
    error = Color(0xFFFF7A80),
)

private val AppTypography = Typography().let { t ->
    val num = "tnum" // tabular figures so amounts line up
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = num),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = num, letterSpacing = (-1).sp),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = num, letterSpacing = (-0.5).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = num),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = num),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = num),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = num),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(fontFeatureSettings = num),
        bodyMedium = t.bodyMedium.copy(fontFeatureSettings = num),
    )
}

val NumberStyle = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun RupeeFlowTheme(mode: ThemeMode, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    val extra = if (dark) ExtraColors(
        positive = Color(0xFF4FD8A8), negative = Color(0xFFFF7A80), warning = Color(0xFFFFC15E),
        heroStart = Color(0xFF4B3DE0), heroEnd = Color(0xFF241A7A), cardBorder = Color(0x1FFFFFFF), subtle = Color(0xFF8E8BA3),
    ) else ExtraColors(
        positive = Color(0xFF0F9D77), negative = Color(0xFFE5484D), warning = Color(0xFFD98A00),
        heroStart = Color(0xFF6A5BFF), heroEnd = Color(0xFF3324B5), cardBorder = Color(0x12000000), subtle = Color(0xFF8A8799),
    )
    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}

object Rf {
    val colors: ExtraColors
        @Composable get() = LocalExtraColors.current
}
