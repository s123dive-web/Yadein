import AVFoundation
import CoreImage
import CoreImage.CIFilterBuiltins
import CoreText
import Foundation
import UIKit
import YaadeinData
import YaadeinDomain

/// A bundled soundtrack (BundledTrack on Android): same files, same beats per minute, so cuts land on the beat alike.
struct ReelTrack: Hashable, Identifiable {
    let key: String
    let theme: RecapTheme?
    let bpm: Int
    /// The user's own audio file (copied into the app); nil for bundled tracks.
    var customURL: URL? = nil
    var id: String { key }
    /// No beat for the user's own music: cuts follow the length preset instead.
    var beatMs: Int64? { bpm > 0 ? 60_000 / Int64(bpm) : nil }
    var label: String { customURL?.deletingPathExtension().lastPathComponent ?? L("track_\(key)") }
    var url: URL? { customURL ?? Bundle.main.url(forResource: "track_\(key)", withExtension: "m4a") }

    static func own(_ url: URL) -> ReelTrack { ReelTrack(key: "own", theme: nil, bpm: 0, customURL: url) }

    static let all: [ReelTrack] = [
        ReelTrack(key: "warm", theme: .WARM, bpm: 90), ReelTrack(key: "minimal", theme: .MINIMAL, bpm: 80),
        ReelTrack(key: "festive", theme: .FESTIVE, bpm: 110), ReelTrack(key: "classic", theme: .CLASSIC_FILM, bpm: 84),
        ReelTrack(key: "lullaby", theme: nil, bpm: 72), ReelTrack(key: "celebration", theme: nil, bpm: 112),
        ReelTrack(key: "acoustic", theme: nil, bpm: 96), ReelTrack(key: "upbeat", theme: nil, bpm: 120),
    ]
}

enum ReelError: LocalizedError {
    case writer(String)
    var errorDescription: String? { if case let .writer(m) = self { return m }; return nil }
}

/// Renders a `RecapPlan` (the storyboard from the shared RecapPlanner) into a 1080×1920 H.264 reel, following
/// RecapRenderer on Android: photos pre-framed at 1.25× (whole photo over a blurred copy when the shape differs by more
/// than 12 %), smoothstep Ken Burns, captions above the bottom 18 %, per-theme grading, music looped under the reel
/// (clips muted when there is music), and the "Made with Yaadein" outro.
final class ReelRenderer {
    let plan: RecapPlan
    let track: ReelTrack?
    let vault: MediaVault
    let store: VaultStore
    private let w = reelWidth, h = reelHeight
    private let fps: Int32 = 30
    private let ci = CIContext(options: [.useSoftwareRenderer: false])
    private var cancelled = false

    init(plan: RecapPlan, track: ReelTrack?, vault: MediaVault, store: VaultStore) {
        self.plan = plan
        self.track = track
        self.vault = vault
        self.store = store
    }

    func cancel() { cancelled = true }

    /// Renders to a temporary MP4 and returns its URL. `progress` is 0...1.
    func render(progress: @escaping (Double) -> Void) async throws -> URL {
        let silent = FileManager.default.temporaryDirectory.appendingPathComponent("reel-v-\(UUID().uuidString).mp4")
        try await writeVideo(to: silent) { progress($0 * 0.9) }
        defer { try? FileManager.default.removeItem(at: silent) }
        let out = FileManager.default.temporaryDirectory.appendingPathComponent("reel-\(UUID().uuidString).mp4")
        try await addAudio(video: silent, to: out)
        progress(1)
        return out
    }

    // MARK: Video

    private func writeVideo(to url: URL, progress: @escaping (Double) -> Void) async throws {
        let writer = try AVAssetWriter(outputURL: url, fileType: .mp4)
        let settings: [String: Any] = [
            AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: w, AVVideoHeightKey: h,
            AVVideoCompressionPropertiesKey: [AVVideoAverageBitRateKey: 10_000_000, AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel],
        ]
        let input = AVAssetWriterInput(mediaType: .video, outputSettings: settings)
        input.expectsMediaDataInRealTime = false
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA, kCVPixelBufferWidthKey as String: w,
            kCVPixelBufferHeightKey as String: h, kCVPixelBufferCGImageCompatibilityKey as String: true,
            kCVPixelBufferCGBitmapContextCompatibilityKey as String: true,
        ])
        writer.add(input)
        guard writer.startWriting() else { throw ReelError.writer(writer.error?.localizedDescription ?? "writer") }
        writer.startSession(atSourceTime: .zero)

        let totalFrames = max(1, Int(plan.totalDurationMs * Int64(fps) / 1000))
        var frame = 0
        func append(_ draw: (CGContext) -> Void) async throws {
            while !input.isReadyForMoreMediaData { try await Task.sleep(nanoseconds: 2_000_000) }
            guard let pool = adaptor.pixelBufferPool else { throw ReelError.writer("no pixel buffer pool") }
            var buffer: CVPixelBuffer?
            CVPixelBufferPoolCreatePixelBuffer(nil, pool, &buffer)
            guard let pb = buffer else { throw ReelError.writer("no pixel buffer") }
            CVPixelBufferLockBaseAddress(pb, [])
            if let ctx = CGContext(data: CVPixelBufferGetBaseAddress(pb), width: w, height: h, bitsPerComponent: 8,
                                   bytesPerRow: CVPixelBufferGetBytesPerRow(pb), space: CGColorSpaceCreateDeviceRGB(),
                                   bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) {
                // UIKit-style coordinates (origin top-left) for text drawing.
                ctx.translateBy(x: 0, y: CGFloat(h))
                ctx.scaleBy(x: 1, y: -1)
                draw(ctx)
            }
            CVPixelBufferUnlockBaseAddress(pb, [])
            adaptor.append(pb, withPresentationTime: CMTime(value: CMTimeValue(frame), timescale: fps))
            frame += 1
            if frame % 15 == 0 { progress(Double(frame) / Double(totalFrames)) }
        }

        for segment in plan.segments {
            if cancelled { throw CancellationError() }
            let frames = max(1, Int(segment.durationMs * Int64(fps) / 1000))
            switch segment {
            case let .titleCard(title, subtitle, _, isOutro):
                let card = titleCard(title: title, subtitle: subtitle, outro: isOutro)
                for _ in 0..<frames { try await append { self.flip($0, card) } }
            case let .photo(mediaId, _, start, end, caption, date):
                guard let framed = framedPhoto(mediaId) else { continue }
                for i in 0..<frames {
                    let t = smoothstep(Double(i) / Double(max(1, frames - 1)))
                    let f = Framing(cx: lerp(start.cx, end.cx, t), cy: lerp(start.cy, end.cy, t), zoom: lerp(start.zoom, end.zoom, t))
                    try await append { ctx in
                        self.drawKenBurns(framed, f, in: ctx)
                        if let caption { self.drawCaption(caption, date, in: ctx) }
                    }
                }
            case let .clip(mediaId, startMs, endMs, caption, date):
                try await appendClip(mediaId: mediaId, startMs: startMs, endMs: endMs, frames: frames) { image in
                    try await append { ctx in
                        self.drawFill(image, in: ctx)
                        if let caption { self.drawCaption(caption, date, in: ctx) }
                    }
                }
            }
        }
        input.markAsFinished()
        await writer.finishWriting()
        if writer.status != .completed { throw ReelError.writer(writer.error?.localizedDescription ?? "finish") }
    }

    /// CGContext.draw puts images upside down in a flipped context; redraw upright.
    private func flip(_ ctx: CGContext, _ image: CGImage) {
        ctx.saveGState()
        ctx.translateBy(x: 0, y: CGFloat(h))
        ctx.scaleBy(x: 1, y: -1)
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
        ctx.restoreGState()
    }

    private func lerp(_ a: Float, _ b: Float, _ t: Double) -> Float { a + (b - a) * Float(t) }
    private func smoothstep(_ x: Double) -> Double { x * x * (3 - 2 * x) }

    // MARK: Photos

    /// 9:16 frame at 1.25× reel size: filled when the photo is within 12 % of 9:16, otherwise whole over a blurred,
    /// dimmed copy of itself (PhotoFramer on Android), then graded for the theme.
    private func framedPhoto(_ mediaId: Int64) -> CGImage? {
        guard let media = store.media(id: mediaId), let bytes = try? vault.read(media.encryptedPath),
              let src = CGImageSourceCreateWithData(Data(bytes) as CFData, nil),
              let photo = MediaImporter.downscaled(src, edge: 2560) else { return nil }
        let fw = Int(Double(w) * 1.25), fh = Int(Double(h) * 1.25)
        let target = CGRect(x: 0, y: 0, width: fw, height: fh)
        let ratio = CGFloat(photo.width) / CGFloat(photo.height), want = CGFloat(fw) / CGFloat(fh)
        let full = CIImage(cgImage: photo)
        var out: CIImage
        if abs(ratio - want) / want <= 0.12 {
            out = aspect(full, into: target, fill: true)
        } else {
            let blurred = aspect(full, into: target, fill: true).clampedToExtent()
                .applyingGaussianBlur(sigma: 40).cropped(to: target)
                .applyingFilter("CIColorControls", parameters: [kCIInputBrightnessKey: -0.25])
            out = aspect(full, into: target, fill: false).composited(over: blurred)
        }
        out = grade(out)
        return ci.createCGImage(out, from: target)
    }

    private func aspect(_ image: CIImage, into rect: CGRect, fill: Bool) -> CIImage {
        let sx = rect.width / image.extent.width, sy = rect.height / image.extent.height
        let s = fill ? max(sx, sy) : min(sx, sy)
        let scaled = image.transformed(by: CGAffineTransform(scaleX: s, y: s))
        let dx = rect.midX - scaled.extent.midX, dy = rect.midY - scaled.extent.midY
        return scaled.transformed(by: CGAffineTransform(translationX: dx, y: dy)).cropped(to: rect)
    }

    private func grade(_ image: CIImage) -> CIImage {
        switch plan.theme {
        case .WARM:
            return image.applyingFilter("CITemperatureAndTint", parameters: ["inputNeutral": CIVector(x: 6500, y: 0), "inputTargetNeutral": CIVector(x: 5600, y: 0)])
                .applyingFilter("CIColorControls", parameters: [kCIInputSaturationKey: 1.1])
        case .MINIMAL:
            return image.applyingFilter("CIColorControls", parameters: [kCIInputSaturationKey: 0.6, kCIInputContrastKey: 1.08])
        case .FESTIVE:
            return image.applyingFilter("CIColorControls", parameters: [kCIInputSaturationKey: 1.25, kCIInputContrastKey: 1.06])
        case .CLASSIC_FILM:
            return image.applyingFilter("CIPhotoEffectMono").applyingFilter("CISepiaTone", parameters: [kCIInputIntensityKey: 0.35])
                .applyingFilter("CIColorControls", parameters: [kCIInputContrastKey: 1.1])
        }
    }

    /// Ken Burns: the visible window is (1/zoom) of the frame centred on (cx, cy), clamped so no edge shows.
    private func drawKenBurns(_ image: CGImage, _ f: Framing, in ctx: CGContext) {
        let iw = CGFloat(image.width), ih = CGFloat(image.height)
        let zoom = CGFloat(max(1, f.zoom))
        let vw = iw / zoom, vh = ih / zoom
        let cx = min(max(CGFloat(f.cx) * iw, vw / 2), iw - vw / 2)
        let cy = min(max(CGFloat(f.cy) * ih, vh / 2), ih - vh / 2)
        let scale = CGFloat(w) / vw
        let drawRect = CGRect(x: -(cx - vw / 2) * scale, y: -(cy - vh / 2) * scale, width: iw * scale, height: ih * scale)
        ctx.saveGState()
        ctx.interpolationQuality = .high
        ctx.translateBy(x: 0, y: CGFloat(h))
        ctx.scaleBy(x: 1, y: -1)
        ctx.draw(image, in: CGRect(x: drawRect.minX, y: CGFloat(h) - drawRect.maxY, width: drawRect.width, height: drawRect.height))
        ctx.restoreGState()
    }

    private func drawFill(_ image: CGImage, in ctx: CGContext) {
        let s = max(CGFloat(w) / CGFloat(image.width), CGFloat(h) / CGFloat(image.height))
        let dw = CGFloat(image.width) * s, dh = CGFloat(image.height) * s
        ctx.saveGState()
        ctx.setFillColor(UIColor.black.cgColor)
        ctx.fill(CGRect(x: 0, y: 0, width: w, height: h))
        ctx.translateBy(x: 0, y: CGFloat(h))
        ctx.scaleBy(x: 1, y: -1)
        ctx.draw(image, in: CGRect(x: (CGFloat(w) - dw) / 2, y: (CGFloat(h) - dh) / 2, width: dw, height: dh))
        ctx.restoreGState()
    }

    // MARK: Text

    private func font(_ name: String, _ size: CGFloat) -> UIFont { UIFont(name: name, size: size) ?? .systemFont(ofSize: size, weight: .semibold) }

    private func drawText(_ text: String, font: UIFont, color: UIColor, in rect: CGRect, ctx: CGContext, align: NSTextAlignment = .center) {
        let style = NSMutableParagraphStyle()
        style.alignment = align
        style.lineBreakMode = .byWordWrapping
        let attr = NSAttributedString(string: text, attributes: [.font: font, .foregroundColor: color, .paragraphStyle: style])
        UIGraphicsPushContext(ctx)
        attr.draw(with: rect, options: [.usesLineFragmentOrigin, .truncatesLastVisibleLine], context: nil)
        UIGraphicsPopContext()
    }

    /// Title and date strip above the bottom 18 % that reel apps cover with their own buttons (CardPainter).
    private func drawCaption(_ title: String, _ date: String?, in ctx: CGContext) {
        let short = CGFloat(min(w, h))
        let bottom = CGFloat(h) * 0.82
        let stripHeight = short * 0.28
        let rect = CGRect(x: 0, y: bottom - stripHeight, width: CGFloat(w), height: stripHeight)
        let colors = [UIColor.black.withAlphaComponent(0).cgColor, UIColor.black.withAlphaComponent(0.55).cgColor] as CFArray
        if let g = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 1]) {
            ctx.drawLinearGradient(g, start: CGPoint(x: 0, y: rect.minY), end: CGPoint(x: 0, y: rect.maxY), options: [])
        }
        let pad = short * 0.07
        drawText(title, font: font("Fraunces-SemiBold", short * 0.065), color: .white,
                 in: CGRect(x: pad, y: rect.minY + stripHeight * 0.25, width: CGFloat(w) - 2 * pad, height: short * 0.17), ctx: ctx, align: .left)
        if let date {
            drawText(date, font: font("ManropeExtraLight-SemiBold", short * 0.038), color: UIColor.white.withAlphaComponent(0.85),
                     in: CGRect(x: pad, y: rect.maxY - short * 0.07, width: CGFloat(w) - 2 * pad, height: short * 0.06), ctx: ctx, align: .left)
        }
    }

    /// Intro / text / outro card on the theme's gradient.
    private func titleCard(title: String, subtitle: String, outro: Bool) -> CGImage {
        let size = CGSize(width: w, height: h)
        let (top, bottom): (UIColor, UIColor) = {
            switch plan.theme {
            case .WARM: return (UIColor(red: 0.36, green: 0.16, blue: 0.53, alpha: 1), UIColor(red: 0.91, green: 0.64, blue: 0.24, alpha: 1))
            case .MINIMAL: return (UIColor(white: 0.1, alpha: 1), UIColor(white: 0.32, alpha: 1))
            case .FESTIVE: return (UIColor(red: 0.78, green: 0.06, blue: 0.18, alpha: 1), UIColor(red: 0.95, green: 0.66, blue: 0, alpha: 1))
            case .CLASSIC_FILM: return (UIColor(red: 0.18, green: 0.15, blue: 0.12, alpha: 1), UIColor(red: 0.45, green: 0.38, blue: 0.3, alpha: 1))
            }
        }()
        let renderer = UIGraphicsImageRenderer(size: size, format: { let f = UIGraphicsImageRendererFormat(); f.scale = 1; return f }())
        let image = renderer.image { r in
            let ctx = r.cgContext
            if let g = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [top.cgColor, bottom.cgColor] as CFArray, locations: [0, 1]) {
                ctx.drawLinearGradient(g, start: .zero, end: CGPoint(x: 0, y: size.height), options: [])
            }
            let short = min(size.width, size.height)
            let pad = short * 0.1
            drawText(title, font: font("Fraunces-SemiBold", short * (outro ? 0.075 : 0.09)), color: .white,
                     in: CGRect(x: pad, y: size.height * 0.38, width: size.width - 2 * pad, height: short * 0.45), ctx: ctx)
            if !subtitle.isEmpty {
                drawText(subtitle, font: font("ManropeExtraLight-Medium", short * 0.042), color: UIColor.white.withAlphaComponent(0.85),
                         in: CGRect(x: pad, y: size.height * 0.38 + short * 0.42, width: size.width - 2 * pad, height: short * 0.2), ctx: ctx)
            }
        }
        return image.cgImage!
    }

    // MARK: Clips

    /// Decodes the clip's frames between start and end. The clip is decrypted to a protected temporary file only for
    /// the time it is read, then deleted.
    private func appendClip(mediaId: Int64, startMs: Int64, endMs: Int64, frames: Int, append: (CGImage) async throws -> Void) async throws {
        guard let media = store.media(id: mediaId), let file = try? vault.decryptToTemporaryFile(media.encryptedPath, ext: "mp4") else { return }
        try? (file as NSURL).setResourceValue(URLFileProtection.complete, forKey: .fileProtectionKey)
        defer { try? FileManager.default.removeItem(at: file) }
        let asset = AVURLAsset(url: file)
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: w, height: h)
        generator.requestedTimeToleranceBefore = CMTime(value: 1, timescale: 30)
        generator.requestedTimeToleranceAfter = CMTime(value: 1, timescale: 30)
        var last: CGImage?
        for i in 0..<frames {
            if cancelled { throw CancellationError() }
            let ms = Double(startMs) + Double(endMs - startMs) * Double(i) / Double(max(1, frames))
            if let img = try? await generator.image(at: CMTime(seconds: ms / 1000, preferredTimescale: 600)).image { last = img }
            if let last { try await append(last) }
        }
    }

    // MARK: Audio

    /// Loops the soundtrack under the whole reel with a 1.5 s fade-out (or keeps no audio without a track).
    private func addAudio(video: URL, to out: URL) async throws {
        let videoAsset = AVURLAsset(url: video)
        let duration = try await videoAsset.load(.duration)
        let composition = AVMutableComposition()
        guard let vTrack = try await videoAsset.loadTracks(withMediaType: .video).first,
              let cv = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid) else {
            try FileManager.default.copyItem(at: video, to: out)
            return
        }
        try cv.insertTimeRange(CMTimeRange(start: .zero, duration: duration), of: vTrack, at: .zero)
        var mix: AVMutableAudioMix?
        if let url = track?.url {
            let music = AVURLAsset(url: url)
            if let aTrack = try await music.loadTracks(withMediaType: .audio).first,
               let ca = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid) {
                let musicLength = try await music.load(.duration)
                var cursor = CMTime.zero
                while cursor < duration, musicLength.seconds > 0.5 {
                    let piece = CMTimeMinimum(musicLength, duration - cursor)
                    try ca.insertTimeRange(CMTimeRange(start: .zero, duration: piece), of: aTrack, at: cursor)
                    cursor = cursor + piece
                }
                let params = AVMutableAudioMixInputParameters(track: ca)
                let fade = CMTime(seconds: min(1.5, duration.seconds / 4), preferredTimescale: 600)
                params.setVolumeRamp(fromStartVolume: 1, toEndVolume: 0, timeRange: CMTimeRange(start: duration - fade, duration: fade))
                let m = AVMutableAudioMix()
                m.inputParameters = [params]
                mix = m
            }
        }
        guard let export = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetHighestQuality) else {
            throw ReelError.writer("export")
        }
        export.outputURL = out
        export.outputFileType = .mp4
        export.audioMix = mix
        await export.export()
        if export.status != .completed { throw ReelError.writer(export.error?.localizedDescription ?? "export") }
    }
}
