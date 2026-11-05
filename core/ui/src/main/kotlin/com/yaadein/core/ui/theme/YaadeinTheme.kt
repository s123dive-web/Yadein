package com.yaadein.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.shape.RoundedCornerShape
import com.yaadein.domain.settings.AppTheme
import com.yaadein.domain.settings.DarkMode

/**
 * Root theme. Applies the chosen palette (or Material You dynamic colour), dark mode, and Easy mode
 * (larger text and touch targets). Theme switches are instant because the palette is a plain value.
 */
@Composable
fun YaadeinTheme(
    theme: AppTheme = AppTheme.WARM_ALBUM,
    darkMode: DarkMode = DarkMode.SYSTEM,
    easyMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (darkMode) {
        DarkMode.SYSTEM -> systemDark
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    val context = LocalContext.current
    val palette = paletteFor(theme)
    val scheme: ColorScheme = when {
        theme == AppTheme.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> palette.dark
        else -> palette.light
    }
    val extended = if (dark) palette.darkExtended else palette.lightExtended
    val radius = Radius()
    val shapes = remember(radius) {
        Shapes(
            extraSmall = RoundedCornerShape(radius.sm),
            small = RoundedCornerShape(radius.sm),
            medium = RoundedCornerShape(radius.md),
            large = RoundedCornerShape(radius.lg),
            extraLarge = RoundedCornerShape(radius.xl),
        )
    }
    val baseDensity = LocalDensity.current
    val density = remember(baseDensity, easyMode) {
        if (easyMode) Density(baseDensity.density, baseDensity.fontScale * EASY_MODE_TEXT_SCALE) else baseDensity
    }
    CompositionLocalProvider(
        LocalYaadeinColors provides extended,
        LocalSpacing provides Spacing(),
        LocalRadius provides radius,
        LocalElevation provides Elevation(),
        LocalMotion provides Motion(),
        LocalSizes provides if (easyMode) EasyModeSizes else Sizes(),
        LocalEasyMode provides easyMode,
        LocalDensity provides density,
    ) {
        MaterialTheme(colorScheme = scheme, typography = YaadeinTypography, shapes = shapes, content = content)
    }
}

/** Convenience accessors so screens read `Yaadein.spacing.lg` instead of raw numbers. */
object Yaadein {
    val colors: YaadeinColors @Composable get() = LocalYaadeinColors.current
    val spacing: Spacing @Composable get() = LocalSpacing.current
    val radius: Radius @Composable get() = LocalRadius.current
    val elevation: Elevation @Composable get() = LocalElevation.current
    val motion: Motion @Composable get() = LocalMotion.current
    val sizes: Sizes @Composable get() = LocalSizes.current
    val easyMode: Boolean @Composable get() = LocalEasyMode.current
}
