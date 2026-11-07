import SwiftUI

/// The eight procedural illustrations from core/ui/illustrations/Illustration.kt, drawn in theme colours with the same
/// geometry, so empty states and onboarding look the same on both phones.
enum IllustrationKind { case photoStack, peopleCircle, kite, searchGlass, filmReel, lockHeart, calendarStar, boxClosed }

struct Illustration: View {
    let kind: IllustrationKind
    @Environment(\.look) private var look

    var body: some View {
        Canvas { ctx, size in
            let c = look.colors
            let w = size.width, h = size.height, s = min(w, h)
            let surface = c.surfaceContainerLowest, onSurface = c.onSurface, avatars = c.avatarPalette
            ctx.fill(Path(ellipseIn: CGRect(x: w / 2 - s * 0.42, y: h / 2 - s * 0.42, width: s * 0.84, height: s * 0.84)),
                     with: .color(c.primaryContainer.opacity(0.6)))
            switch kind {
            case .photoStack:
                polaroid(&ctx, CGPoint(x: w * 0.32, y: h * 0.36), s * 0.42, -12, surface, avatars[2], onSurface)
                polaroid(&ctx, CGPoint(x: w * 0.5, y: h * 0.5), s * 0.42, 6, surface, avatars[1], onSurface)
                heart(&ctx, CGPoint(x: w * 0.72, y: h * 0.3), s * 0.11, c.secondary)
            case .peopleCircle:
                face(&ctx, CGPoint(x: w * 0.5, y: h * 0.36), s * 0.14, avatars[0], surface)
                face(&ctx, CGPoint(x: w * 0.34, y: h * 0.6), s * 0.14, avatars[1], surface)
                face(&ctx, CGPoint(x: w * 0.66, y: h * 0.6), s * 0.14, avatars[2], surface)
                heart(&ctx, CGPoint(x: w * 0.5, y: h * 0.6), s * 0.07, c.secondary)
            case .kite:
                var kite = Path()
                kite.move(to: CGPoint(x: w * 0.55, y: h * 0.2)); kite.addLine(to: CGPoint(x: w * 0.72, y: h * 0.42))
                kite.addLine(to: CGPoint(x: w * 0.55, y: h * 0.7)); kite.addLine(to: CGPoint(x: w * 0.38, y: h * 0.42)); kite.closeSubpath()
                ctx.fill(kite, with: .color(c.primary))
                line(&ctx, CGPoint(x: w * 0.55, y: h * 0.2), CGPoint(x: w * 0.55, y: h * 0.7), surface, s * 0.012)
                line(&ctx, CGPoint(x: w * 0.38, y: h * 0.42), CGPoint(x: w * 0.72, y: h * 0.42), surface, s * 0.012)
                var tail = Path()
                tail.move(to: CGPoint(x: w * 0.55, y: h * 0.7))
                tail.addCurve(to: CGPoint(x: w * 0.38, y: h * 0.95), control1: CGPoint(x: w * 0.45, y: h * 0.8), control2: CGPoint(x: w * 0.5, y: h * 0.9))
                ctx.stroke(tail, with: .color(c.secondary), lineWidth: s * 0.015)
                bow(&ctx, CGPoint(x: w * 0.5, y: h * 0.78), s * 0.035, c.tertiary)
                bow(&ctx, CGPoint(x: w * 0.47, y: h * 0.87), s * 0.035, c.tertiary)
            case .searchGlass:
                polaroid(&ctx, CGPoint(x: w * 0.42, y: h * 0.5), s * 0.4, -6, surface, avatars[3], onSurface)
                ctx.stroke(Path(ellipseIn: CGRect(x: w * 0.62 - s * 0.14, y: h * 0.42 - s * 0.14, width: s * 0.28, height: s * 0.28)),
                           with: .color(c.primary), lineWidth: s * 0.035)
                line(&ctx, CGPoint(x: w * 0.72, y: h * 0.53), CGPoint(x: w * 0.84, y: h * 0.68), c.primary, s * 0.05)
            case .filmReel:
                circle(&ctx, CGPoint(x: w * 0.5, y: h * 0.48), s * 0.26, c.primary)
                circle(&ctx, CGPoint(x: w * 0.5, y: h * 0.48), s * 0.06, surface)
                for i in 0..<5 {
                    let a = CGFloat(i * 72 - 90) * .pi / 180
                    circle(&ctx, CGPoint(x: w * 0.5 + s * 0.16 * cos(a), y: h * 0.48 + s * 0.16 * sin(a)), s * 0.045, surface)
                }
                var strip = Path()
                strip.move(to: CGPoint(x: w * 0.6, y: h * 0.7))
                strip.addQuadCurve(to: CGPoint(x: w * 0.85, y: h * 0.95), control: CGPoint(x: w * 0.8, y: h * 0.8))
                ctx.stroke(strip, with: .color(c.secondary), lineWidth: s * 0.05)
            case .lockHeart:
                ctx.fill(Path(roundedRect: CGRect(x: w * 0.32, y: h * 0.45, width: w * 0.36, height: h * 0.32), cornerRadius: s * 0.06), with: .color(c.primary))
                // drawArc(180°, 180°) over an ellipse (0.38w, 0.24h, 0.24w × 0.36h): its top half.
                let shackle = Path(ellipseIn: CGRect(x: w * 0.38, y: h * 0.24, width: w * 0.24, height: h * 0.36)).trimmedPath(from: 0.5, to: 1)
                ctx.stroke(shackle, with: .color(c.primary), lineWidth: s * 0.05)
                heart(&ctx, CGPoint(x: w * 0.5, y: h * 0.61), s * 0.07, c.secondary)
            case .calendarStar:
                ctx.fill(Path(roundedRect: CGRect(x: w * 0.28, y: h * 0.3, width: w * 0.44, height: h * 0.44), cornerRadius: s * 0.04), with: .color(surface))
                ctx.fill(Path(roundedRect: CGRect(x: w * 0.28, y: h * 0.3, width: w * 0.44, height: h * 0.12), cornerRadius: s * 0.04), with: .color(c.primary))
                for r in 0..<3 { for col in 0..<4 {
                    circle(&ctx, CGPoint(x: w * (0.34 + Double(col) * 0.107), y: h * (0.5 + Double(r) * 0.08)), s * 0.015, onSurface.opacity(0.25))
                } }
                star(&ctx, CGPoint(x: w * 0.66, y: h * 0.66), s * 0.09, c.secondary)
            case .boxClosed:
                ctx.fill(Path(roundedRect: CGRect(x: w * 0.3, y: h * 0.45, width: w * 0.4, height: h * 0.3), cornerRadius: s * 0.03), with: .color(c.primary.opacity(0.85)))
                ctx.fill(Path(roundedRect: CGRect(x: w * 0.26, y: h * 0.38, width: w * 0.48, height: h * 0.12), cornerRadius: s * 0.03), with: .color(c.primary))
                line(&ctx, CGPoint(x: w * 0.5, y: h * 0.5), CGPoint(x: w * 0.5, y: h * 0.75), surface, s * 0.02)
                heart(&ctx, CGPoint(x: w * 0.5, y: h * 0.3), s * 0.06, c.secondary)
            }
        }
        .accessibilityHidden(true)
    }

    private func circle(_ ctx: inout GraphicsContext, _ center: CGPoint, _ r: CGFloat, _ color: Color) {
        ctx.fill(Path(ellipseIn: CGRect(x: center.x - r, y: center.y - r, width: r * 2, height: r * 2)), with: .color(color))
    }

    private func line(_ ctx: inout GraphicsContext, _ a: CGPoint, _ b: CGPoint, _ color: Color, _ width: CGFloat) {
        var p = Path()
        p.move(to: a)
        p.addLine(to: b)
        ctx.stroke(p, with: .color(color), lineWidth: width)
    }

    private func polaroid(_ ctx: inout GraphicsContext, _ center: CGPoint, _ size: CGFloat, _ degrees: Double, _ paper: Color, _ photo: Color, _ ink: Color) {
        var c = ctx
        c.translateBy(x: center.x, y: center.y)
        c.rotate(by: .degrees(degrees))
        let top = CGPoint(x: -size / 2, y: -size / 2)
        c.fill(Path(roundedRect: CGRect(x: top.x + size * 0.02, y: top.y + size * 0.04, width: size, height: size * 1.15), cornerRadius: size * 0.04), with: .color(ink.opacity(0.12)))
        c.fill(Path(roundedRect: CGRect(x: top.x, y: top.y, width: size, height: size * 1.15), cornerRadius: size * 0.04), with: .color(paper))
        c.fill(Path(CGRect(x: top.x + size * 0.08, y: top.y + size * 0.08, width: size * 0.84, height: size * 0.78)), with: .color(photo.opacity(0.85)))
        c.fill(Path(ellipseIn: CGRect(x: top.x + size * 0.62, y: top.y + size * 0.22, width: size * 0.16, height: size * 0.16)), with: .color(paper.opacity(0.8)))
        var hills = Path()
        hills.move(to: CGPoint(x: top.x + size * 0.08, y: top.y + size * 0.86))
        hills.addQuadCurve(to: CGPoint(x: top.x + size * 0.5, y: top.y + size * 0.78), control: CGPoint(x: top.x + size * 0.3, y: top.y + size * 0.5))
        hills.addQuadCurve(to: CGPoint(x: top.x + size * 0.92, y: top.y + size * 0.86), control: CGPoint(x: top.x + size * 0.7, y: top.y + size * 0.55))
        hills.closeSubpath()
        c.fill(hills, with: .color(paper.opacity(0.55)))
    }

    private func face(_ ctx: inout GraphicsContext, _ center: CGPoint, _ r: CGFloat, _ skin: Color, _ ink: Color) {
        circle(&ctx, center, r, skin)
        circle(&ctx, CGPoint(x: center.x - r * 0.3, y: center.y - r * 0.15), r * 0.09, ink)
        circle(&ctx, CGPoint(x: center.x + r * 0.3, y: center.y - r * 0.15), r * 0.09, ink)
        var smile = Path()
        smile.addArc(center: CGPoint(x: center.x, y: center.y + r * 0.25), radius: r * 0.4, startAngle: .degrees(20), endAngle: .degrees(160), clockwise: false)
        ctx.stroke(smile, with: .color(ink), lineWidth: r * 0.1)
    }

    private func heart(_ ctx: inout GraphicsContext, _ center: CGPoint, _ size: CGFloat, _ color: Color) {
        var p = Path()
        p.move(to: CGPoint(x: center.x, y: center.y + size * 0.9))
        p.addCurve(to: CGPoint(x: center.x, y: center.y - size * 0.6), control1: CGPoint(x: center.x - size * 1.8, y: center.y - size * 0.4),
                   control2: CGPoint(x: center.x - size * 0.8, y: center.y - size * 1.6))
        p.addCurve(to: CGPoint(x: center.x, y: center.y + size * 0.9), control1: CGPoint(x: center.x + size * 0.8, y: center.y - size * 1.6),
                   control2: CGPoint(x: center.x + size * 1.8, y: center.y - size * 0.4))
        p.closeSubpath()
        ctx.fill(p, with: .color(color))
    }

    private func bow(_ ctx: inout GraphicsContext, _ center: CGPoint, _ size: CGFloat, _ color: Color) {
        var p = Path()
        p.move(to: center); p.addLine(to: CGPoint(x: center.x - size, y: center.y - size * 0.6)); p.addLine(to: CGPoint(x: center.x - size, y: center.y + size * 0.6)); p.closeSubpath()
        p.move(to: center); p.addLine(to: CGPoint(x: center.x + size, y: center.y - size * 0.6)); p.addLine(to: CGPoint(x: center.x + size, y: center.y + size * 0.6)); p.closeSubpath()
        ctx.fill(p, with: .color(color))
    }

    private func star(_ ctx: inout GraphicsContext, _ center: CGPoint, _ r: CGFloat, _ color: Color) {
        var p = Path()
        for i in 0..<10 {
            let radius = i % 2 == 0 ? r : r * 0.45
            let a = CGFloat(i * 36 - 90) * .pi / 180
            let pt = CGPoint(x: center.x + radius * cos(a), y: center.y + radius * sin(a))
            if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        p.closeSubpath()
        ctx.fill(p, with: .color(color))
    }
}
