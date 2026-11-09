import SwiftUI
import UIKit
import YaadeinData
import YaadeinDomain

// MARK: - Encrypted thumbnails

/// Decrypted thumbnails live in memory only (Android disables Coil's disk cache for the same reason).
final class ThumbnailCache: @unchecked Sendable {
    static let shared = ThumbnailCache()
    private let cache = NSCache<NSString, UIImage>()
    init() { cache.countLimit = 400 }
    func image(_ key: String) -> UIImage? { cache.object(forKey: key as NSString) }
    func set(_ image: UIImage, _ key: String) { cache.setObject(image, forKey: key as NSString) }
    func removeAll() { cache.removeAllObjects() }
}

/// A photo/video thumbnail (or the full photo with `full: true`) decrypted from the vault off the main thread.
struct VaultImage: View {
    let path: String
    var full = false
    var contentMode: ContentMode = .fill
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            if let image {
                Image(uiImage: image).resizable().aspectRatio(contentMode: contentMode)
            } else {
                Rectangle().fill(look.colors.surfaceContainerHigh)
            }
        }
        .task(id: path) { await load() }
    }

    private func load() async {
        let key = (full ? "full:" : "thumb:") + path
        if let cached = ThumbnailCache.shared.image(key) {
            image = cached
            return
        }
        guard let vault = model.vault, !path.isEmpty else { return }
        let loaded: UIImage? = await Task.detached(priority: .userInitiated) {
            guard let bytes = try? vault.read(path) else { return nil }
            return UIImage(data: Data(bytes))
        }.value
        if let loaded {
            ThumbnailCache.shared.set(loaded, key)
            image = loaded
        }
    }
}

// MARK: - Cover art

/// Procedural cover for text-only memories (CoverArt.kt): themed gradient plus a soft illustration per cover style.
struct CoverArt: View {
    let style: String
    var seed: Int64 = 0
    @Environment(\.look) private var look

    var body: some View {
        let (c1, c2) = look.colors.gradient(style)
        ZStack {
            LinearGradient(colors: [c1, c2], startPoint: .topLeading, endPoint: .bottomTrailing)
            Canvas { ctx, size in draw(&ctx, size) }
        }
        .accessibilityHidden(true)
    }

    private func draw(_ ctx: inout GraphicsContext, _ size: CGSize) {
        let w = size.width, h = size.height, m = min(w, h)
        let ink = Color.white.opacity(0.22), inkSoft = Color.white.opacity(0.12)
        func circle(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat, _ c: Color) {
            ctx.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)), with: .color(c))
        }
        func line(_ a: CGPoint, _ b: CGPoint, _ c: Color, _ width: CGFloat) {
            var p = Path(); p.move(to: a); p.addLine(to: b); ctx.stroke(p, with: .color(c), lineWidth: width)
        }
        func heart(_ cx: CGFloat, _ cy: CGFloat, _ s: CGFloat, _ c: Color) {
            var p = Path()
            p.move(to: CGPoint(x: cx, y: cy + s * 0.45))
            p.addCurve(to: CGPoint(x: cx, y: cy - s * 0.3), control1: CGPoint(x: cx - s * 0.9, y: cy - s * 0.2), control2: CGPoint(x: cx - s * 0.4, y: cy - s * 0.8))
            p.addCurve(to: CGPoint(x: cx, y: cy + s * 0.45), control1: CGPoint(x: cx + s * 0.4, y: cy - s * 0.8), control2: CGPoint(x: cx + s * 0.9, y: cy - s * 0.2))
            ctx.fill(p, with: .color(c))
        }
        // Same LCG as Android so confetti and rain land in the same places.
        var s = seed
        func next() -> Int64 { s = (s &* 1_103_515_245 &+ 12345) & 0x7fff_ffff; return s }
        switch style {
        case "beach":
            circle(w * 0.78, h * 0.3, m * 0.16, ink)
            for i in 0..<3 {
                let y = h * (0.62 + CGFloat(i) * 0.12)
                var p = Path()
                p.move(to: CGPoint(x: 0, y: y))
                var x: CGFloat = 0, up = true
                while x < w {
                    p.addQuadCurve(to: CGPoint(x: x + w * 0.16, y: y), control: CGPoint(x: x + w * 0.08, y: up ? y - h * 0.05 : y + h * 0.05))
                    x += w * 0.16
                    up.toggle()
                }
                ctx.stroke(p, with: .color(inkSoft), lineWidth: h * 0.02)
            }
        case "mountain", "winter":
            var p = Path()
            p.move(to: CGPoint(x: 0, y: h)); p.addLine(to: CGPoint(x: w * 0.3, y: h * 0.35)); p.addLine(to: CGPoint(x: w * 0.5, y: h * 0.7))
            p.addLine(to: CGPoint(x: w * 0.68, y: h * 0.25)); p.addLine(to: CGPoint(x: w, y: h)); p.closeSubpath()
            ctx.fill(p, with: .color(inkSoft))
            circle(w * 0.2, h * 0.25, m * 0.1, ink)
        case "travel":
            var p = Path()
            p.move(to: CGPoint(x: w * 0.1, y: h * 0.8))
            p.addCurve(to: CGPoint(x: w * 0.9, y: h * 0.3), control1: CGPoint(x: w * 0.3, y: h * 0.2), control2: CGPoint(x: w * 0.6, y: h * 0.9))
            ctx.stroke(p, with: .color(ink), lineWidth: h * 0.025)
            for (x, y) in [(0.1, 0.8), (0.5, 0.55), (0.9, 0.3)] { circle(w * x, h * y, h * 0.04, Color.white.opacity(0.5)) }
        case "wedding", "baby":
            heart(w * 0.72, h * 0.42, m * 0.3, ink)
            heart(w * 0.28, h * 0.6, m * 0.18, inkSoft)
        case "birthday", "festival", "holi":
            s = seed &* 31 &+ 7
            for _ in 0..<28 {
                let x = CGFloat(next() % 1000) / 1000 * w
                let y = CGFloat(next() % 1000) / 1000 * h
                let v = next()
                circle(x, y, h * (0.015 + CGFloat(v % 100) / 100 * 0.03), Color.white.opacity(0.2 + Double(v % 40) / 100))
            }
        case "diwali":
            for i in 0..<5 {
                let x = w * (0.15 + CGFloat(i) * 0.175), base = h * 0.78
                let bowl = Path(ellipseIn: CGRect(x: x - h * 0.08, y: base - h * 0.08, width: h * 0.16, height: h * 0.16)).trimmedPath(from: 0, to: 0.5)
                ctx.fill(bowl, with: .color(inkSoft))
                var flame = Path()
                flame.move(to: CGPoint(x: x, y: base - h * 0.28))
                flame.addQuadCurve(to: CGPoint(x: x, y: base - h * 0.06), control: CGPoint(x: x + h * 0.07, y: base - h * 0.14))
                flame.addQuadCurve(to: CGPoint(x: x, y: base - h * 0.28), control: CGPoint(x: x - h * 0.07, y: base - h * 0.14))
                ctx.fill(flame, with: .color(Color.white.opacity(0.45)))
            }
        case "school", "college":
            var p = Path()
            p.move(to: CGPoint(x: w * 0.25, y: h * 0.35)); p.addQuadCurve(to: CGPoint(x: w * 0.75, y: h * 0.35), control: CGPoint(x: w * 0.5, y: h * 0.45))
            p.addLine(to: CGPoint(x: w * 0.75, y: h * 0.75)); p.addQuadCurve(to: CGPoint(x: w * 0.25, y: h * 0.75), control: CGPoint(x: w * 0.5, y: h * 0.85)); p.closeSubpath()
            ctx.fill(p, with: .color(inkSoft))
            line(CGPoint(x: w * 0.5, y: h * 0.43), CGPoint(x: w * 0.5, y: h * 0.83), ink, h * 0.015)
        case "home":
            var p = Path()
            p.move(to: CGPoint(x: w * 0.5, y: h * 0.2)); p.addLine(to: CGPoint(x: w * 0.8, y: h * 0.5)); p.addLine(to: CGPoint(x: w * 0.72, y: h * 0.5))
            p.addLine(to: CGPoint(x: w * 0.72, y: h * 0.82)); p.addLine(to: CGPoint(x: w * 0.28, y: h * 0.82)); p.addLine(to: CGPoint(x: w * 0.28, y: h * 0.5))
            p.addLine(to: CGPoint(x: w * 0.2, y: h * 0.5)); p.closeSubpath()
            ctx.fill(p, with: .color(inkSoft))
        case "food":
            circle(w * 0.5, h * 0.55, m * 0.3, inkSoft)
            circle(w * 0.5, h * 0.55, m * 0.2, ink)
        case "music":
            for i in 0..<4 {
                let x = w * (0.2 + CGFloat(i) * 0.2), y = h * (0.6 + CGFloat(i % 2) * 0.1)
                circle(x, y, h * 0.05, ink)
                line(CGPoint(x: x + h * 0.045, y: y), CGPoint(x: x + h * 0.045, y: h * 0.3), ink, h * 0.015)
            }
        case "sports":
            let r = m * 0.22
            ctx.stroke(Path(ellipseIn: CGRect(x: w * 0.6 - r, y: h * 0.5 - r, width: 2 * r, height: 2 * r)), with: .color(ink), lineWidth: h * 0.02)
            line(CGPoint(x: w * 0.15, y: h * 0.8), CGPoint(x: w * 0.9, y: h * 0.8), ink, h * 0.02)
        case "rain":
            s = seed &* 17 &+ 3
            for _ in 0..<24 {
                let x = CGFloat(next() % 1000) / 1000 * w
                let y = CGFloat(next() % 1000) / 1000 * h
                line(CGPoint(x: x, y: y), CGPoint(x: x - h * 0.04, y: y + h * 0.1), inkSoft, h * 0.012)
            }
        default:
            circle(w * 0.85, h * 0.2, m * 0.45, inkSoft)
            circle(w * 0.15, h * 0.9, m * 0.3, inkSoft)
        }
    }
}

// MARK: - People

/// Circular avatar: profile photo when available, otherwise coloured initials (PersonAvatar.kt).
struct PersonAvatar: View {
    let person: Person
    var size: CGFloat? = nil
    var ring: Color? = nil
    @Environment(\.look) private var look
    @Environment(AppModel.self) private var model

    var body: some View {
        let d = size ?? look.sizes.avatarMd
        ZStack {
            Circle().fill(look.colors.avatarColor(person))
            if let mediaId = person.profileMediaId, let media = model.store?.media(id: mediaId) {
                VaultImage(path: media.thumbPath).clipShape(Circle())
            } else {
                Text(person.initials.isEmpty ? "?" : person.initials)
                    .font(.custom("ManropeExtraLight-SemiBold", size: d * 0.4))
                    .foregroundStyle(.white)
            }
        }
        .frame(width: d, height: d)
        .overlay { if let ring { Circle().stroke(ring, lineWidth: 2) } }
        .accessibilityElement()
        .accessibilityLabel(L("cd_avatar_of", person.displayName))
    }
}

/// Overlapping avatars ("who was there") with a +N bubble.
struct FaceStack: View {
    let people: [Person]
    var size: CGFloat? = nil
    var max = 4
    @Environment(\.look) private var look

    var body: some View {
        let d = size ?? look.sizes.avatarSm
        let shown = Array(people.prefix(max))
        let extra = people.count - shown.count
        HStack(spacing: -d * 0.3) {
            ForEach(shown) { PersonAvatar(person: $0, size: d, ring: look.colors.surface) }
            if extra > 0 {
                Text("+\(extra)")
                    .font(look.type.labelSmall)
                    .foregroundStyle(look.colors.onSecondaryContainer)
                    .frame(width: d, height: d)
                    .background(Circle().fill(look.colors.secondaryContainer))
                    .overlay(Circle().stroke(look.colors.surface, lineWidth: 2))
            }
        }
        .accessibilityElement()
        .accessibilityLabel(L("cd_people_in_memory", people.map(\.displayName).joined(separator: ", ")))
    }
}

// MARK: - Memory card

/// The timeline card (MemoryCardView.kt): cover (photo or CoverArt), badges, faces, title, date, place, description.
struct MemoryCardView: View {
    let card: MemoryCard
    var compact = false
    @Environment(\.look) private var look

    var body: some View {
        let height = compact ? look.sizes.cardCoverHeightCompact : look.sizes.cardCoverHeight
        let dateText = memoryDateText(card.memory.date)
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .bottom) {
                SwiftUI.Group {
                    if let cover = card.cover {
                        VaultImage(path: cover.thumbPath)
                    } else {
                        CoverArt(style: card.memory.coverStyle, seed: card.memory.id)
                    }
                }
                .frame(height: height)
                .frame(maxWidth: .infinity)
                .clipped()
                LinearGradient(colors: [.clear, .black.opacity(0.35)], startPoint: .center, endPoint: .bottom)
                HStack(alignment: .center) {
                    if card.mediaCount > 0 {
                        CoverBadge(icon: card.cover?.kind == .VIDEO ? "play.circle.fill" : "photo.on.rectangle", text: LP("media_count", card.mediaCount))
                    } else if card.memory.type == .TEXT_ONLY {
                        CoverBadge(icon: "text.alignleft", text: L("badge_story"))
                    }
                    Spacer()
                    if !card.people.isEmpty { FaceStack(people: card.people) }
                }
                .padding(Spacing.md)
            }
            .frame(height: height)
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(card.memory.title)
                    .font(look.type.titleLarge)
                    .foregroundStyle(look.colors.onSurface)
                    .lineLimit(2)
                HStack(spacing: Spacing.sm) {
                    Text(dateText).font(look.type.labelLarge).foregroundStyle(look.colors.primary)
                    if !card.memory.place.isEmpty {
                        Image(systemName: "mappin.and.ellipse").font(.system(size: look.sizes.iconSm * 0.8)).foregroundStyle(look.colors.onSurfaceVariant)
                        Text(card.memory.place).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant).lineLimit(1)
                    }
                }
                if !compact && !card.memory.description.isEmpty {
                    Text(card.memory.description)
                        .font(look.type.bodyMedium)
                        .foregroundStyle(look.colors.onSurfaceVariant)
                        .lineLimit(2)
                        .padding(.top, Spacing.xs)
                }
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.md)
        }
        .background(look.colors.surfaceContainerLowest)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
        .shadow(color: .black.opacity(look.colors.dark ? 0 : 0.08), radius: 3, y: 1)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(L("cd_memory_card", card.memory.title, dateText))
    }
}

struct CoverBadge: View {
    let icon: String
    let text: String
    @Environment(\.look) private var look

    var body: some View {
        HStack(spacing: Spacing.xs) {
            Image(systemName: icon).font(.system(size: look.sizes.iconSm * 0.8))
            Text(text).font(look.type.labelMedium)
        }
        .foregroundStyle(.white)
        .padding(.horizontal, Spacing.sm)
        .padding(.vertical, Spacing.xs)
        .background(Capsule().fill(.black.opacity(0.45)))
    }
}

// MARK: - Empty state and buttons

/// A designed empty state: illustration, headline, one line of help and an optional action (EmptyState.kt).
struct EmptyState: View {
    let illustration: IllustrationKind
    let title: String
    let message: String
    var actionLabel: String? = nil
    var action: (() -> Void)? = nil
    @Environment(\.look) private var look

    var body: some View {
        VStack(spacing: 0) {
            Illustration(kind: illustration).frame(width: look.sizes.illustration, height: look.sizes.illustration)
            Spacer().frame(height: Spacing.xl)
            Text(title).font(look.type.headlineSmall).multilineTextAlignment(.center).foregroundStyle(look.colors.onSurface)
            Spacer().frame(height: Spacing.sm)
            Text(message).font(look.type.bodyLarge).multilineTextAlignment(.center).foregroundStyle(look.colors.onSurfaceVariant)
            if let actionLabel, let action {
                Spacer().frame(height: Spacing.xl)
                Button(actionLabel, action: action).buttonStyle(FilledButtonStyle())
            }
        }
        .padding(Spacing.xxl)
        .frame(maxWidth: .infinity)
    }
}

/// Material 3 filled button (primary, pill shaped, 48 pt high; 56 in Easy mode).
struct FilledButtonStyle: ButtonStyle {
    var fullWidth = false
    var tonal = false
    @Environment(\.look) private var look
    @Environment(\.isEnabled) private var enabled

    func makeBody(configuration: Configuration) -> some View {
        let bg = tonal ? look.colors.secondaryContainer : look.colors.primary
        let fg = tonal ? look.colors.onSecondaryContainer : look.colors.onPrimary
        configuration.label
            .font(look.type.labelLarge)
            .foregroundStyle(enabled ? fg : look.colors.onSurface.opacity(0.38))
            .padding(.horizontal, Spacing.xl)
            .frame(minHeight: look.sizes.touchTarget)
            .frame(maxWidth: fullWidth ? .infinity : nil)
            .background(Capsule().fill(enabled ? bg : look.colors.onSurface.opacity(0.12)))
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

/// Material 3 text button.
struct TextButtonStyle: ButtonStyle {
    @Environment(\.look) private var look
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(look.type.labelLarge)
            .foregroundStyle(look.colors.primary)
            .padding(.horizontal, Spacing.md)
            .frame(minHeight: look.sizes.touchTarget)
            .opacity(configuration.isPressed ? 0.6 : 1)
    }
}

/// Selectable chip (filter chips, date chips, people chips).
struct Chip: View {
    let text: String
    var selected = false
    var icon: String? = nil
    let action: () -> Void
    @Environment(\.look) private var look

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.xs) {
                if selected { Image(systemName: "checkmark").font(.system(size: 12, weight: .semibold)) } else if let icon { Image(systemName: icon).font(.system(size: 13)) }
                Text(text).font(look.type.labelLarge)
            }
            .padding(.horizontal, Spacing.md)
            .frame(minHeight: 32)
            .foregroundStyle(selected ? look.colors.onSecondaryContainer : look.colors.onSurfaceVariant)
            .background(Capsule().fill(selected ? look.colors.secondaryContainer : Color.clear))
            .overlay(Capsule().stroke(selected ? Color.clear : look.colors.outline, lineWidth: 1))
            .contentShape(Capsule())
            .frame(minHeight: look.sizes.touchTarget)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// Section header used across screens (Fraunces title + optional action).
struct SectionHeader: View {
    let title: String
    var action: (String, () -> Void)? = nil
    @Environment(\.look) private var look

    var body: some View {
        HStack {
            Text(title).font(look.type.titleLarge).foregroundStyle(look.colors.onSurface).accessibilityAddTraits(.isHeader)
            Spacer()
            if let action { Button(action.0, action: action.1).buttonStyle(TextButtonStyle()) }
        }
    }
}

/// Outlined text field in the Material style.
struct OutlinedField: View {
    let label: String
    @Binding var text: String
    var axis: Axis = .horizontal
    var keyboard: UIKeyboardType = .default
    @Environment(\.look) private var look

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(label).font(look.type.labelMedium).foregroundStyle(look.colors.onSurfaceVariant)
            TextField("", text: $text, axis: axis)
                .font(look.type.bodyLarge)
                .keyboardType(keyboard)
                .padding(.horizontal, Spacing.lg)
                .padding(.vertical, Spacing.md)
                .frame(minHeight: look.sizes.touchTarget)
                .background(RoundedRectangle(cornerRadius: Radius.sm).stroke(look.colors.outline, lineWidth: 1))
        }
    }
}
