import SwiftUI
import YaadeinDomain

extension Color {
    /// ARGB like Compose's `Color(0xAARRGGBB)`.
    init(argb: UInt32) {
        self.init(.sRGB,
                  red: Double((argb >> 16) & 0xff) / 255,
                  green: Double((argb >> 8) & 0xff) / 255,
                  blue: Double(argb & 0xff) / 255,
                  opacity: Double((argb >> 24) & 0xff) / 255)
    }
}

private func lighten(_ argb: UInt32, _ f: Double) -> Color {
    let r = Double((argb >> 16) & 0xff) / 255, g = Double((argb >> 8) & 0xff) / 255, b = Double(argb & 0xff) / 255
    return Color(.sRGB, red: r + (1 - r) * f, green: g + (1 - g) * f, blue: b + (1 - b) * f, opacity: 1)
}

private func darken(_ argb: UInt32, _ f: Double) -> Color {
    let r = Double((argb >> 16) & 0xff) / 255, g = Double((argb >> 8) & 0xff) / 255, b = Double(argb & 0xff) / 255
    return Color(.sRGB, red: r * (1 - f), green: g * (1 - f), blue: b * (1 - f), opacity: 1)
}

/// Material 3 colour roles plus Yaadein's extended roles (YaadeinColors on Android), for one palette and brightness.
struct YTheme {
    let dark: Bool
    let primary, onPrimary, primaryContainer, onPrimaryContainer: Color
    let secondary, onSecondary, secondaryContainer, onSecondaryContainer: Color
    let tertiary, onTertiary, tertiaryContainer, onTertiaryContainer: Color
    let error, onError, errorContainer, onErrorContainer: Color
    let background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant: Color
    let outline, outlineVariant, inverseSurface, inverseOnSurface, inversePrimary: Color
    let surfaceContainerLowest, surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest: Color
    // Extended roles
    let avatarPalette: [Color]
    let gradients: [String: (Color, Color)]
    let moodColors: [Mood: Color]
    let faceRing: Color
    let timelineLine: Color
    let timelineYear: Color
    let success: Color
    let warning: Color

    init(palette p: PaletteHex, dark: Bool) {
        let s = dark ? p.dark : p.light
        self.dark = dark
        let c = { (v: UInt32) in Color(argb: v) }
        primary = c(s.primary); onPrimary = c(s.onPrimary); primaryContainer = c(s.primaryContainer); onPrimaryContainer = c(s.onPrimaryContainer)
        secondary = c(s.secondary); onSecondary = c(s.onSecondary); secondaryContainer = c(s.secondaryContainer); onSecondaryContainer = c(s.onSecondaryContainer)
        tertiary = c(s.tertiary); onTertiary = c(s.onTertiary); tertiaryContainer = c(s.tertiaryContainer); onTertiaryContainer = c(s.onTertiaryContainer)
        error = c(s.error); onError = c(s.onError); errorContainer = c(s.errorContainer); onErrorContainer = c(s.onErrorContainer)
        background = c(s.background); onBackground = c(s.onBackground); surface = c(s.surface); onSurface = c(s.onSurface)
        surfaceVariant = c(s.surfaceVariant); onSurfaceVariant = c(s.onSurfaceVariant)
        outline = c(s.outline); outlineVariant = c(s.outlineVariant); inverseSurface = c(s.inverseSurface)
        inverseOnSurface = c(s.inverseOnSurface); inversePrimary = c(s.inversePrimary)
        surfaceContainerLowest = c(s.surfaceContainerLowest); surfaceContainerLow = c(s.surfaceContainerLow)
        surfaceContainer = c(s.surfaceContainer); surfaceContainerHigh = c(s.surfaceContainerHigh)
        surfaceContainerHighest = c(s.surfaceContainerHighest)

        avatarPalette = p.avatars.map(c)
        var g: [String: (Color, Color)] = [:]
        for (k, v) in GeneratedPalettes.gradients { g[k] = (c(v.0), c(v.1)) }
        // gradientsFor(): "theme" and "accent" follow the palette.
        g["theme"] = (dark ? darken(s.primary, 0.1) : lighten(s.primary, 0.1), dark ? darken(s.tertiary, 0.2) : lighten(s.tertiary, 0.2))
        g["accent"] = (c(s.secondary), dark ? darken(s.secondary, 0.3) : lighten(s.secondary, 0.3))
        gradients = g
        moodColors = [.JOYFUL: c(0xFFF59E0B), .CALM: c(0xFF38BDF8), .NOSTALGIC: c(0xFFA78BFA), .PROUD: c(0xFF22C55E),
                      .FUNNY: c(0xFFFB7185), .SAD: c(0xFF64748B), .CELEBRATION: c(0xFFE11D48)]
        faceRing = c(s.secondary)
        timelineLine = c(s.outlineVariant)
        timelineYear = c(s.primary)
        success = dark ? c(0xFF4ADE80) : c(0xFF15803D)
        warning = dark ? c(0xFFFBBF24) : c(0xFFB45309)
    }

    static func palette(for theme: AppTheme) -> PaletteHex {
        switch theme {
        case .WARM_ALBUM, .DYNAMIC: return GeneratedPalettes.warmAlbum
        case .MIDNIGHT: return GeneratedPalettes.midnight
        case .MONSOON: return GeneratedPalettes.monsoon
        case .FESTIVE: return GeneratedPalettes.festive
        case .MINIMAL_MONO: return GeneratedPalettes.minimalMono
        }
    }

    func avatarColor(_ person: Person) -> Color {
        let n = avatarPalette.count
        return avatarPalette[((person.avatarColorIndex % n) + n) % n]
    }

    func gradient(_ style: String) -> (Color, Color) {
        gradients[style] ?? gradients[CoverStyle.defaultStyle] ?? (Color(argb: 0xFF5B2A86), Color(argb: 0xFF8B5CF6))
    }
}

/// Everything a view needs from the theme: colours, sizes and type, switched instantly by settings.
struct Look {
    var colors: YTheme
    var sizes: Sizes
    var easyMode: Bool
    var type: Typography

    static let preview = Look(colors: YTheme(palette: GeneratedPalettes.warmAlbum, dark: false), sizes: .standard, easyMode: false,
                              type: Typography(scale: 1))
}

private struct LookKey: EnvironmentKey {
    static let defaultValue = Look.preview
}

extension EnvironmentValues {
    var look: Look {
        get { self[LookKey.self] }
        set { self[LookKey.self] = newValue }
    }
}

/// Applies the chosen palette, dark mode and Easy mode at the root (YaadeinTheme on Android).
struct ThemeRoot<Content: View>: View {
    let settings: UserSettings
    @ViewBuilder let content: Content
    @Environment(\.colorScheme) private var systemScheme

    var body: some View {
        let dark: Bool = {
            switch settings.darkMode {
            case .SYSTEM: return systemScheme == .dark
            case .LIGHT: return false
            case .DARK: return true
            }
        }()
        let look = Look(colors: YTheme(palette: YTheme.palette(for: settings.theme), dark: dark),
                        sizes: settings.easyMode ? .easy : .standard,
                        easyMode: settings.easyMode,
                        type: Typography(scale: settings.easyMode ? easyModeTextScale : 1))
        content
            .environment(\.look, look)
            .tint(look.colors.primary)
            .preferredColorScheme(settings.darkMode == .SYSTEM ? nil : (dark ? .dark : .light))
            .background(look.colors.surface.ignoresSafeArea())
            .foregroundStyle(look.colors.onSurface)
    }
}
