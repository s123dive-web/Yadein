import SwiftUI

// Design tokens: the same numbers as core/ui/theme/Tokens.kt (dp → points). Nothing in the UI hardcodes a size,
// radius or duration; Easy mode swaps `Sizes` and the text scale in one place, exactly as on Android.

enum Spacing {
    static let xxs: CGFloat = 2
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 24
    static let xxl: CGFloat = 32
    static let xxxl: CGFloat = 48
    /// Horizontal page gutter.
    static let gutter: CGFloat = 20
}

enum Radius {
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 20
    static let xl: CGFloat = 28
    static let pill: CGFloat = 999
}

enum Motion {
    static let fast = 0.12
    static let normal = 0.22
    static let slow = 0.36
    static let emphasized = 0.48
    static var standard: Animation { .timingCurve(0.2, 0, 0, 1, duration: normal) }
    static var emphasizedCurve: Animation { .timingCurve(0.05, 0.7, 0.1, 1, duration: emphasized) }
}

struct Sizes: Equatable {
    var touchTarget: CGFloat = 48
    var avatarSm: CGFloat = 28
    var avatarMd: CGFloat = 40
    var avatarLg: CGFloat = 64
    var avatarXl: CGFloat = 96
    var fabSize: CGFloat = 64
    var cardCoverHeight: CGFloat = 200
    var cardCoverHeightCompact: CGFloat = 150
    var timelineRail: CGFloat = 2
    var timelineDot: CGFloat = 10
    var iconSm: CGFloat = 18
    var icon: CGFloat = 24
    var iconLg: CGFloat = 32
    var illustration: CGFloat = 200

    static let standard = Sizes()
    /// Easy mode: bigger targets and simpler layouts for all ages (EasyModeSizes on Android).
    static let easy = Sizes(touchTarget: 56, avatarSm: 32, avatarMd: 48, avatarLg: 72, avatarXl: 112, fabSize: 72,
                            cardCoverHeight: 220, cardCoverHeightCompact: 170)
}

/// Text scale on top of the system Dynamic Type size in Easy mode (EASY_MODE_TEXT_SCALE).
let easyModeTextScale: CGFloat = 1.3
