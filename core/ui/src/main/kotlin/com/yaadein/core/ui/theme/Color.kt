package com.yaadein.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.yaadein.domain.settings.AppTheme

/**
 * Extended colour roles that Material 3 does not provide. Each theme defines its own set so
 * covers, avatars and moods always harmonise with the active palette.
 */
@Immutable
data class YaadeinColors(
    /** Stable per-person avatar colours (8). */
    val avatarPalette: List<Color>,
    /** Gradient pairs used for text-only memory covers; keys match CoverStyle. */
    val gradients: Map<String, Pair<Color, Color>>,
    val moodJoyful: Color,
    val moodCalm: Color,
    val moodNostalgic: Color,
    val moodProud: Color,
    val moodFunny: Color,
    val moodSad: Color,
    val moodCelebration: Color,
    val faceRing: Color,
    val timelineLine: Color,
    val timelineYear: Color,
    val success: Color,
    val warning: Color,
    val shimmer: Color,
)

val LocalYaadeinColors = staticCompositionLocalOf<YaadeinColors> { WarmAlbum.lightExtended }

@Immutable
data class ThemePalette(
    val light: ColorScheme,
    val dark: ColorScheme,
    val lightExtended: YaadeinColors,
    val darkExtended: YaadeinColors,
)

private fun gradientsFor(primary: Color, secondary: Color, tertiary: Color, dark: Boolean): Map<String, Pair<Color, Color>> {
    val shade: (Color, Float) -> Color = { c, f -> if (dark) c.darken(f) else c.lighten(f) }
    return mapOf(
        "plain_plum" to (Color(0xFF5B2A86) to Color(0xFF8B5CF6)),
        "plain_amber" to (Color(0xFFE8A33D) to Color(0xFFF59E0B)),
        "plain_teal" to (Color(0xFF0F766E) to Color(0xFF2DD4BF)),
        "plain_rose" to (Color(0xFFBE185D) to Color(0xFFFB7185)),
        "plain_indigo" to (Color(0xFF3730A3) to Color(0xFF818CF8)),
        "beach" to (Color(0xFF0EA5E9) to Color(0xFFFDE68A)),
        "mountain" to (Color(0xFF1E3A5F) to Color(0xFF93C5FD)),
        "travel" to (Color(0xFF0D9488) to Color(0xFFFBBF24)),
        "wedding" to (Color(0xFF9F1239) to Color(0xFFFDA4AF)),
        "birthday" to (Color(0xFF7C3AED) to Color(0xFFF472B6)),
        "diwali" to (Color(0xFF9A3412) to Color(0xFFFBBF24)),
        "holi" to (Color(0xFFDB2777) to Color(0xFF34D399)),
        "festival" to (Color(0xFFB91C1C) to Color(0xFFF59E0B)),
        "school" to (Color(0xFF1D4ED8) to Color(0xFF93C5FD)),
        "college" to (Color(0xFF4338CA) to Color(0xFFA5B4FC)),
        "home" to (Color(0xFF92400E) to Color(0xFFFCD34D)),
        "food" to (Color(0xFFC2410C) to Color(0xFFFDBA74)),
        "baby" to (Color(0xFFF9A8D4) to Color(0xFFBFDBFE)),
        "music" to (Color(0xFF6D28D9) to Color(0xFF67E8F9)),
        "sports" to (Color(0xFF15803D) to Color(0xFFBEF264)),
        "rain" to (Color(0xFF334155) to Color(0xFF7DD3FC)),
        "winter" to (Color(0xFF475569) to Color(0xFFE2E8F0)),
        "theme" to (shade(primary, 0.1f) to shade(tertiary, 0.2f)),
        "accent" to (secondary to shade(secondary, 0.3f)),
    )
}

internal fun Color.lighten(fraction: Float): Color = Color(
    red = red + (1f - red) * fraction,
    green = green + (1f - green) * fraction,
    blue = blue + (1f - blue) * fraction,
    alpha = alpha,
)

internal fun Color.darken(fraction: Float): Color = Color(
    red = red * (1f - fraction),
    green = green * (1f - fraction),
    blue = blue * (1f - fraction),
    alpha = alpha,
)

private fun extended(
    scheme: ColorScheme,
    dark: Boolean,
    avatars: List<Color>,
    faceRing: Color = scheme.secondary,
): YaadeinColors = YaadeinColors(
    avatarPalette = avatars,
    gradients = gradientsFor(scheme.primary, scheme.secondary, scheme.tertiary, dark),
    moodJoyful = Color(0xFFF59E0B),
    moodCalm = Color(0xFF38BDF8),
    moodNostalgic = Color(0xFFA78BFA),
    moodProud = Color(0xFF22C55E),
    moodFunny = Color(0xFFFB7185),
    moodSad = Color(0xFF64748B),
    moodCelebration = Color(0xFFE11D48),
    faceRing = faceRing,
    timelineLine = scheme.outlineVariant,
    timelineYear = scheme.primary,
    success = if (dark) Color(0xFF4ADE80) else Color(0xFF15803D),
    warning = if (dark) Color(0xFFFBBF24) else Color(0xFFB45309),
    shimmer = scheme.surfaceVariant,
)

private val warmAvatars = listOf(
    Color(0xFF7C3AED), Color(0xFFE8A33D), Color(0xFF0F766E), Color(0xFFBE185D),
    Color(0xFF2563EB), Color(0xFFC2410C), Color(0xFF15803D), Color(0xFF6D28D9),
)

/** Warm Album: cream surface, deep plum primary, soft amber accent. The default. */
val WarmAlbum: ThemePalette = run {
    val light = lightColorScheme(
        primary = Color(0xFF5B2A86),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFEBDDFF),
        onPrimaryContainer = Color(0xFF23003F),
        secondary = Color(0xFF9A6A1E),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFE1B3),
        onSecondaryContainer = Color(0xFF2B1700),
        tertiary = Color(0xFF8C4A5F),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFD9E2),
        onTertiaryContainer = Color(0xFF3A071C),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFBF5EC),
        onBackground = Color(0xFF1F1A22),
        surface = Color(0xFFFBF5EC),
        onSurface = Color(0xFF1F1A22),
        surfaceVariant = Color(0xFFEEE4DC),
        onSurfaceVariant = Color(0xFF4D4452),
        outline = Color(0xFF7F7483),
        outlineVariant = Color(0xFFD5C7D3),
        inverseSurface = Color(0xFF342F37),
        inverseOnSurface = Color(0xFFF8EEF2),
        inversePrimary = Color(0xFFD6BAFF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF6EFE5),
        surfaceContainer = Color(0xFFF1E9DE),
        surfaceContainerHigh = Color(0xFFEBE3D8),
        surfaceContainerHighest = Color(0xFFE5DDD2),
        surfaceTint = Color(0xFF5B2A86),
    )
    val dark = darkColorScheme(
        primary = Color(0xFFD6BAFF),
        onPrimary = Color(0xFF3B0D68),
        primaryContainer = Color(0xFF53267F),
        onPrimaryContainer = Color(0xFFEBDDFF),
        secondary = Color(0xFFF2BE6A),
        onSecondary = Color(0xFF462A00),
        secondaryContainer = Color(0xFF654000),
        onSecondaryContainer = Color(0xFFFFE1B3),
        tertiary = Color(0xFFFFB0C8),
        onTertiary = Color(0xFF551D31),
        tertiaryContainer = Color(0xFF703347),
        onTertiaryContainer = Color(0xFFFFD9E2),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF1A1418),
        onBackground = Color(0xFFEDE0E6),
        surface = Color(0xFF1A1418),
        onSurface = Color(0xFFEDE0E6),
        surfaceVariant = Color(0xFF3A3139),
        onSurfaceVariant = Color(0xFFD0C3D1),
        outline = Color(0xFF998D9B),
        outlineVariant = Color(0xFF4D4452),
        inverseSurface = Color(0xFFEDE0E6),
        inverseOnSurface = Color(0xFF342F37),
        inversePrimary = Color(0xFF5B2A86),
        surfaceContainerLowest = Color(0xFF120D11),
        surfaceContainerLow = Color(0xFF211B1F),
        surfaceContainer = Color(0xFF261F24),
        surfaceContainerHigh = Color(0xFF30292E),
        surfaceContainerHighest = Color(0xFF3B3439),
        surfaceTint = Color(0xFFD6BAFF),
    )
    ThemePalette(light, dark, extended(light, false, warmAvatars), extended(dark, true, warmAvatars))
}

private val midnightAvatars = listOf(
    Color(0xFF7C8CFF), Color(0xFFFF8FA3), Color(0xFF5EEAD4), Color(0xFFFDE68A),
    Color(0xFFC4B5FD), Color(0xFFF9A8D4), Color(0xFF86EFAC), Color(0xFF93C5FD),
)

/** Midnight: indigo and rose on near-black. */
val Midnight: ThemePalette = run {
    val light = lightColorScheme(
        primary = Color(0xFF3F4DD6),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE0E0FF),
        onPrimaryContainer = Color(0xFF000D5C),
        secondary = Color(0xFFB4284E),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFD9DE),
        onSecondaryContainer = Color(0xFF400012),
        tertiary = Color(0xFF00696B),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFF6FF7F6),
        onTertiaryContainer = Color(0xFF002020),
        background = Color(0xFFF2F3FA),
        onBackground = Color(0xFF191B23),
        surface = Color(0xFFF2F3FA),
        onSurface = Color(0xFF191B23),
        surfaceVariant = Color(0xFFE3E1EC),
        onSurfaceVariant = Color(0xFF46464F),
        outline = Color(0xFF767680),
        outlineVariant = Color(0xFFC7C5D0),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFECEDF5),
        surfaceContainer = Color(0xFFE6E8F0),
        surfaceContainerHigh = Color(0xFFE0E2EA),
        surfaceContainerHighest = Color(0xFFDADCE4),
    )
    val dark = darkColorScheme(
        primary = Color(0xFF7C8CFF),
        onPrimary = Color(0xFF0B1A8F),
        primaryContainer = Color(0xFF2834B6),
        onPrimaryContainer = Color(0xFFE0E0FF),
        secondary = Color(0xFFFF8FA3),
        onSecondary = Color(0xFF5A1128),
        secondaryContainer = Color(0xFF7A2A3F),
        onSecondaryContainer = Color(0xFFFFD9DE),
        tertiary = Color(0xFF4DDBD9),
        onTertiary = Color(0xFF003737),
        tertiaryContainer = Color(0xFF004F50),
        onTertiaryContainer = Color(0xFF6FF7F6),
        background = Color(0xFF0E1020),
        onBackground = Color(0xFFE2E1EC),
        surface = Color(0xFF0E1020),
        onSurface = Color(0xFFE2E1EC),
        surfaceVariant = Color(0xFF2A2C3B),
        onSurfaceVariant = Color(0xFFC7C5D0),
        outline = Color(0xFF90909A),
        outlineVariant = Color(0xFF46464F),
        surfaceContainerLowest = Color(0xFF090B18),
        surfaceContainerLow = Color(0xFF151728),
        surfaceContainer = Color(0xFF1A1C2E),
        surfaceContainerHigh = Color(0xFF242638),
        surfaceContainerHighest = Color(0xFF2F3143),
    )
    ThemePalette(light, dark, extended(light, false, midnightAvatars), extended(dark, true, midnightAvatars))
}

private val monsoonAvatars = listOf(
    Color(0xFF0F766E), Color(0xFF5FA86B), Color(0xFF0369A1), Color(0xFFCA8A04),
    Color(0xFF7C3AED), Color(0xFFBE123C), Color(0xFF4D7C0F), Color(0xFF0891B2),
)

/** Monsoon: teal and leaf green, misty surfaces. */
val Monsoon: ThemePalette = run {
    val light = lightColorScheme(
        primary = Color(0xFF0F766E),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFA6F2EA),
        onPrimaryContainer = Color(0xFF00201E),
        secondary = Color(0xFF3F7A48),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFC3EFC5),
        onSecondaryContainer = Color(0xFF00210A),
        tertiary = Color(0xFF3E6374),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFC1E8FC),
        onTertiaryContainer = Color(0xFF001F2A),
        background = Color(0xFFEEF6F4),
        onBackground = Color(0xFF171D1C),
        surface = Color(0xFFEEF6F4),
        onSurface = Color(0xFF171D1C),
        surfaceVariant = Color(0xFFDAE5E2),
        onSurfaceVariant = Color(0xFF3F4947),
        outline = Color(0xFF6F7977),
        outlineVariant = Color(0xFFBEC9C6),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFE8F0EE),
        surfaceContainer = Color(0xFFE2EAE8),
        surfaceContainerHigh = Color(0xFFDCE4E2),
        surfaceContainerHighest = Color(0xFFD6DFDC),
    )
    val dark = darkColorScheme(
        primary = Color(0xFF5FDCD0),
        onPrimary = Color(0xFF003734),
        primaryContainer = Color(0xFF00504B),
        onPrimaryContainer = Color(0xFFA6F2EA),
        secondary = Color(0xFFA7D4A9),
        onSecondary = Color(0xFF11381D),
        secondaryContainer = Color(0xFF285032),
        onSecondaryContainer = Color(0xFFC3EFC5),
        tertiary = Color(0xFFA5CCDF),
        onTertiary = Color(0xFF073544),
        tertiaryContainer = Color(0xFF254B5B),
        onTertiaryContainer = Color(0xFFC1E8FC),
        background = Color(0xFF0C1A18),
        onBackground = Color(0xFFDEE4E1),
        surface = Color(0xFF0C1A18),
        onSurface = Color(0xFFDEE4E1),
        surfaceVariant = Color(0xFF2B3634),
        onSurfaceVariant = Color(0xFFBEC9C6),
        outline = Color(0xFF899390),
        outlineVariant = Color(0xFF3F4947),
        surfaceContainerLowest = Color(0xFF071411),
        surfaceContainerLow = Color(0xFF132220),
        surfaceContainer = Color(0xFF172624),
        surfaceContainerHigh = Color(0xFF21302E),
        surfaceContainerHighest = Color(0xFF2C3B39),
    )
    ThemePalette(light, dark, extended(light, false, monsoonAvatars), extended(dark, true, monsoonAvatars))
}

private val festiveAvatars = listOf(
    Color(0xFFC8102E), Color(0xFFF2A900), Color(0xFF1D4ED8), Color(0xFF047857),
    Color(0xFF7C3AED), Color(0xFFDB2777), Color(0xFFEA580C), Color(0xFF0891B2),
)

/** Festive: marigold and vermilion, like a Diwali evening. */
val Festive: ThemePalette = run {
    val light = lightColorScheme(
        primary = Color(0xFFA64B00),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDCC5),
        onPrimaryContainer = Color(0xFF351000),
        secondary = Color(0xFFB7142C),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFDAD9),
        onSecondaryContainer = Color(0xFF410006),
        tertiary = Color(0xFF6B5E00),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF2A900),
        onTertiaryContainer = Color(0xFF201C00),
        background = Color(0xFFFFF8E7),
        onBackground = Color(0xFF221A12),
        surface = Color(0xFFFFF8E7),
        onSurface = Color(0xFF221A12),
        surfaceVariant = Color(0xFFF4DFD1),
        onSurfaceVariant = Color(0xFF52443B),
        outline = Color(0xFF85746A),
        outlineVariant = Color(0xFFD7C3B7),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFCF2DE),
        surfaceContainer = Color(0xFFF7ECD6),
        surfaceContainerHigh = Color(0xFFF1E6CF),
        surfaceContainerHighest = Color(0xFFEBE0C8),
    )
    val dark = darkColorScheme(
        primary = Color(0xFFFFB68A),
        onPrimary = Color(0xFF572400),
        primaryContainer = Color(0xFF7C3600),
        onPrimaryContainer = Color(0xFFFFDCC5),
        secondary = Color(0xFFFFB3B0),
        onSecondary = Color(0xFF680010),
        secondaryContainer = Color(0xFF92001B),
        onSecondaryContainer = Color(0xFFFFDAD9),
        tertiary = Color(0xFFF2A900),
        onTertiary = Color(0xFF383000),
        tertiaryContainer = Color(0xFF514700),
        onTertiaryContainer = Color(0xFFFFE170),
        background = Color(0xFF1E1206),
        onBackground = Color(0xFFF0E0D1),
        surface = Color(0xFF1E1206),
        onSurface = Color(0xFFF0E0D1),
        surfaceVariant = Color(0xFF3E3027),
        onSurfaceVariant = Color(0xFFD7C3B7),
        outline = Color(0xFF9F8D82),
        outlineVariant = Color(0xFF52443B),
        surfaceContainerLowest = Color(0xFF160C02),
        surfaceContainerLow = Color(0xFF26190D),
        surfaceContainer = Color(0xFF2B1D10),
        surfaceContainerHigh = Color(0xFF362719),
        surfaceContainerHighest = Color(0xFF423223),
    )
    ThemePalette(light, dark, extended(light, false, festiveAvatars), extended(dark, true, festiveAvatars))
}

private val monoAvatars = listOf(
    Color(0xFF1F1F1F), Color(0xFF525252), Color(0xFF737373), Color(0xFF404040),
    Color(0xFF8A8A8A), Color(0xFF2E2E2E), Color(0xFF666666), Color(0xFF4A4A4A),
)

/** Minimal Mono: ink on paper, nothing to distract from the photos. */
val MinimalMono: ThemePalette = run {
    val light = lightColorScheme(
        primary = Color(0xFF1F1F1F),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE6E6E6),
        onPrimaryContainer = Color(0xFF111111),
        secondary = Color(0xFF6B6B6B),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFEDEDED),
        onSecondaryContainer = Color(0xFF1A1A1A),
        tertiary = Color(0xFF4A4A4A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFE0E0E0),
        onTertiaryContainer = Color(0xFF111111),
        background = Color(0xFFFAFAFA),
        onBackground = Color(0xFF1A1A1A),
        surface = Color(0xFFFAFAFA),
        onSurface = Color(0xFF1A1A1A),
        surfaceVariant = Color(0xFFE8E8E8),
        onSurfaceVariant = Color(0xFF474747),
        outline = Color(0xFF777777),
        outlineVariant = Color(0xFFC8C8C8),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF4F4F4),
        surfaceContainer = Color(0xFFEFEFEF),
        surfaceContainerHigh = Color(0xFFE9E9E9),
        surfaceContainerHighest = Color(0xFFE3E3E3),
    )
    val dark = darkColorScheme(
        primary = Color(0xFFEDEDED),
        onPrimary = Color(0xFF1A1A1A),
        primaryContainer = Color(0xFF3A3A3A),
        onPrimaryContainer = Color(0xFFF5F5F5),
        secondary = Color(0xFFB3B3B3),
        onSecondary = Color(0xFF1F1F1F),
        secondaryContainer = Color(0xFF333333),
        onSecondaryContainer = Color(0xFFEDEDED),
        tertiary = Color(0xFFCFCFCF),
        onTertiary = Color(0xFF1F1F1F),
        tertiaryContainer = Color(0xFF444444),
        onTertiaryContainer = Color(0xFFF0F0F0),
        background = Color(0xFF101010),
        onBackground = Color(0xFFE6E6E6),
        surface = Color(0xFF101010),
        onSurface = Color(0xFFE6E6E6),
        surfaceVariant = Color(0xFF2E2E2E),
        onSurfaceVariant = Color(0xFFC8C8C8),
        outline = Color(0xFF8F8F8F),
        outlineVariant = Color(0xFF474747),
        surfaceContainerLowest = Color(0xFF0A0A0A),
        surfaceContainerLow = Color(0xFF181818),
        surfaceContainer = Color(0xFF1D1D1D),
        surfaceContainerHigh = Color(0xFF272727),
        surfaceContainerHighest = Color(0xFF323232),
    )
    ThemePalette(light, dark, extended(light, false, monoAvatars), extended(dark, true, monoAvatars))
}

fun paletteFor(theme: AppTheme): ThemePalette = when (theme) {
    AppTheme.WARM_ALBUM, AppTheme.DYNAMIC -> WarmAlbum
    AppTheme.MIDNIGHT -> Midnight
    AppTheme.MONSOON -> Monsoon
    AppTheme.FESTIVE -> Festive
    AppTheme.MINIMAL_MONO -> MinimalMono
}
