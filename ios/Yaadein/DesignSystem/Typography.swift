import SwiftUI

/// The Material 3 type scale from core/ui/theme/Type.kt with the same bundled fonts: Fraunces (serif headlines) and
/// Manrope (sans body). Sizes follow Dynamic Type (`relativeTo`) and Easy mode multiplies them by 1.3.
struct Typography {
    let scale: CGFloat

    private enum Face: String {
        case serifRegular = "Fraunces-Regular"
        case serifMedium = "Fraunces-Medium"
        case serifSemiBold = "Fraunces-SemiBold"
        case serifBold = "Fraunces-Bold"
        case sansRegular = "ManropeExtraLight-Regular"
        case sansMedium = "ManropeExtraLight-Medium"
        case sansSemiBold = "ManropeExtraLight-SemiBold"
        case sansBold = "ManropeExtraLight-Bold"
    }

    private func font(_ face: Face, _ size: CGFloat, _ style: Font.TextStyle) -> Font {
        .custom(face.rawValue, size: size * scale, relativeTo: style)
    }

    var displayLarge: Font { font(.serifSemiBold, 44, .largeTitle) }
    var displayMedium: Font { font(.serifSemiBold, 36, .largeTitle) }
    var displaySmall: Font { font(.serifSemiBold, 32, .largeTitle) }
    var headlineLarge: Font { font(.serifSemiBold, 30, .title) }
    var headlineMedium: Font { font(.serifSemiBold, 26, .title) }
    var headlineSmall: Font { font(.serifSemiBold, 22, .title2) }
    var titleLarge: Font { font(.serifSemiBold, 20, .title3) }
    var titleMedium: Font { font(.sansSemiBold, 16, .headline) }
    var titleSmall: Font { font(.sansSemiBold, 14, .subheadline) }
    var bodyLarge: Font { font(.sansRegular, 16, .body) }
    var bodyMedium: Font { font(.sansRegular, 14, .callout) }
    var bodySmall: Font { font(.sansRegular, 12, .footnote) }
    var labelLarge: Font { font(.sansSemiBold, 14, .subheadline) }
    var labelMedium: Font { font(.sansSemiBold, 12, .caption) }
    var labelSmall: Font { font(.sansSemiBold, 11, .caption2) }
}
