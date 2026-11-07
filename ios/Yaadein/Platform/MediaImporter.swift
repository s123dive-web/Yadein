import AVFoundation
import CoreTransferable
import Foundation
import ImageIO
import PhotosUI
import SwiftUI
import UIKit
import UniformTypeIdentifiers
import YaadeinData
import YaadeinDomain

/// A video handed over by the Photos picker as a file we own (copied out of the picker's temporary location).
struct PickedMovie: Transferable {
    let url: URL
    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(contentType: .movie) { SentTransferredFile($0.url) } importing: { received in
            let ext = received.file.pathExtension.isEmpty ? "mov" : received.file.pathExtension
            let copy = FileManager.default.temporaryDirectory.appendingPathComponent("pick-\(UUID().uuidString).\(ext)")
            try FileManager.default.copyItem(at: received.file, to: copy)
            return PickedMovie(url: copy)
        }
    }
}

/// What the importer learned about a picked item before copying it (MediaImporter.inspect on Android).
struct PickedMedia: Identifiable {
    let id = UUID()
    let kind: MediaKind
    /// Image bytes (photos) or a temporary file (videos); deleted after import.
    let data: Data?
    let fileURL: URL?
    let takenAtMillis: Int64?
    let sizeBytes: Int64
    let width: Int
    let height: Int
    let durationMs: Int64?
    let preview: UIImage?

    var pickedItem: PickedItem { PickedItem(key: id.uuidString, takenAtMillis: takenAtMillis, isVideo: kind == .VIDEO) }

    func discard() { if let fileURL { try? FileManager.default.removeItem(at: fileURL) } }
}

enum ImportRejection: Error, Equatable {
    case photoTooLarge, videoTooLarge, unsupported, unreadable, noSpace

    var message: String {
        switch self {
        case .photoTooLarge: return L("reject_photo_too_large")
        case .videoTooLarge: return L("reject_video_too_large")
        case .unsupported: return L("reject_unsupported")
        case .unreadable: return L("reject_unreadable")
        case .noSpace: return L("reject_no_space", "50 MB")
        }
    }
}

/// inspect → limits → optimise → thumbnail → encrypt + verify → one database transaction (MediaImporter.kt).
enum MediaImporter {
    static let maxPhotoBytes: Int64 = 20 * 1024 * 1024
    static let maxVideoBytes: Int64 = 200 * 1024 * 1024
    static let maxVideoBytesOptimized: Int64 = 2 * 1024 * 1024 * 1024
    static let maxPhotoEdge: CGFloat = 2560
    static let thumbEdge: CGFloat = 640

    /// Takes the files waiting from Share → Save to Yaadein: each is moved out of the App Group into a private
    /// temporary file (videos) or read into memory (photos), checked against the same limits, and the inbox is cleared.
    static func takeShared(optimize: Bool) async -> ([PickedMedia], [ImportRejection]) {
        let files = AppGroup.pendingShares()
        guard !files.isEmpty else { return ([], []) }
        var ok: [PickedMedia] = []
        var rejected: [ImportRejection] = []
        for file in files {
            do {
                let isVideo = UTType(filenameExtension: file.pathExtension)?.conforms(to: .movie) ?? false
                if isVideo {
                    let temp = FileManager.default.temporaryDirectory.appendingPathComponent("shared-\(UUID().uuidString).\(file.pathExtension)")
                    try FileManager.default.moveItem(at: file, to: temp)
                    let media = try await inspectVideo(temp)
                    if media.sizeBytes > (optimize ? maxVideoBytesOptimized : maxVideoBytes) {
                        media.discard()
                        throw ImportRejection.videoTooLarge
                    }
                    ok.append(media)
                } else {
                    let data = try Data(contentsOf: file)
                    if Int64(data.count) > maxPhotoBytes { throw ImportRejection.photoTooLarge }
                    ok.append(try inspectPhoto(data))
                }
            } catch let r as ImportRejection {
                rejected.append(r)
            } catch {
                rejected.append(.unreadable)
            }
        }
        AppGroup.clearShares()
        return (ok, rejected)
    }

    /// Loads and inspects picked items; rejected ones are returned separately with the reason.
    static func inspect(_ items: [PhotosPickerItem], optimize: Bool) async -> ([PickedMedia], [ImportRejection]) {
        var ok: [PickedMedia] = []
        var rejected: [ImportRejection] = []
        for item in items {
            let isVideo = item.supportedContentTypes.contains { $0.conforms(to: .movie) }
            do {
                if isVideo {
                    guard let movie = try await item.loadTransferable(type: PickedMovie.self) else { throw ImportRejection.unreadable }
                    let media = try await inspectVideo(movie.url)
                    if media.sizeBytes > (optimize ? maxVideoBytesOptimized : maxVideoBytes) {
                        media.discard()
                        throw ImportRejection.videoTooLarge
                    }
                    ok.append(media)
                } else {
                    guard let data = try await item.loadTransferable(type: Data.self) else { throw ImportRejection.unreadable }
                    if Int64(data.count) > maxPhotoBytes { throw ImportRejection.photoTooLarge }
                    ok.append(try inspectPhoto(data))
                }
            } catch let r as ImportRejection {
                rejected.append(r)
            } catch {
                rejected.append(.unreadable)
            }
        }
        return (ok, rejected)
    }

    static func inspectPhoto(_ data: Data) throws -> PickedMedia {
        guard let src = CGImageSourceCreateWithData(data as CFData, nil),
              let props = CGImageSourceCopyPropertiesAtIndex(src, 0, nil) as? [CFString: Any] else { throw ImportRejection.unsupported }
        var w = props[kCGImagePropertyPixelWidth] as? Int ?? 0
        var h = props[kCGImagePropertyPixelHeight] as? Int ?? 0
        if let o = props[kCGImagePropertyOrientation] as? Int, o >= 5 { swap(&w, &h) }
        let exif = props[kCGImagePropertyExifDictionary] as? [CFString: Any]
        let tiff = props[kCGImagePropertyTIFFDictionary] as? [CFString: Any]
        let dateText = (exif?[kCGImagePropertyExifDateTimeOriginal] as? String) ?? (tiff?[kCGImagePropertyTIFFDateTime] as? String)
        return PickedMedia(kind: .PHOTO, data: data, fileURL: nil, takenAtMillis: dateText.flatMap(exifMillis), sizeBytes: Int64(data.count),
                           width: w, height: h, durationMs: nil, preview: downscaled(src, edge: thumbEdge).map(UIImage.init(cgImage:)))
    }

    static func inspectVideo(_ url: URL) async throws -> PickedMedia {
        let asset = AVURLAsset(url: url)
        let duration = try await asset.load(.duration)
        var size = CGSize.zero
        if let track = try await asset.loadTracks(withMediaType: .video).first {
            let natural = try await track.load(.naturalSize)
            let t = try await track.load(.preferredTransform)
            let r = natural.applying(t)
            size = CGSize(width: abs(r.width), height: abs(r.height))
        }
        var taken: Int64?
        if let item = try? await asset.load(.creationDate), let date = try? await item.load(.dateValue) {
            taken = Int64(date.timeIntervalSince1970 * 1000)
        }
        let bytes = ((try? FileManager.default.attributesOfItem(atPath: url.path)[.size]) as? NSNumber)?.int64Value ?? 0
        return PickedMedia(kind: .VIDEO, data: nil, fileURL: url, takenAtMillis: taken, sizeBytes: bytes, width: Int(size.width),
                           height: Int(size.height), durationMs: Int64(duration.seconds * 1000), preview: await videoFrame(url))
    }

    /// "2024:12:20 18:31:05" in the phone's time zone, as Android's ExifInterface reading does.
    static func exifMillis(_ text: String) -> Int64? {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy:MM:dd HH:mm:ss"
        return f.date(from: text).map { Int64($0.timeIntervalSince1970 * 1000) }
    }

    static func downscaled(_ src: CGImageSource, edge: CGFloat) -> CGImage? {
        let opts: [CFString: Any] = [kCGImageSourceCreateThumbnailFromImageAlways: true, kCGImageSourceCreateThumbnailWithTransform: true,
                                     kCGImageSourceThumbnailMaxPixelSize: edge]
        return CGImageSourceCreateThumbnailAtIndex(src, 0, opts as CFDictionary)
    }

    static func videoFrame(_ url: URL) async -> UIImage? {
        let gen = AVAssetImageGenerator(asset: AVURLAsset(url: url))
        gen.appliesPreferredTrackTransform = true
        gen.maximumSize = CGSize(width: thumbEdge, height: thumbEdge)
        guard let frame = try? await gen.image(at: CMTime(seconds: 0.5, preferredTimescale: 600)) else { return nil }
        return UIImage(cgImage: frame.image)
    }

    static func jpeg(_ image: CGImage, quality: CGFloat) -> Data? { UIImage(cgImage: image).jpegData(compressionQuality: quality) }

    /// Encrypts one picked item into the vault and adds its row (first photo becomes the cover). Returns the media id.
    @discardableResult
    static func save(_ media: PickedMedia, memoryId: Int64?, keepOriginal: Bool, store: VaultStore, vault: MediaVault) async throws -> Int64 {
        try await Task.detached(priority: .userInitiated) {
            defer { media.discard() }
            let freeBytes = (try? URL(fileURLWithPath: NSHomeDirectory()).resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey]))?
                .volumeAvailableCapacityForImportantUsage ?? Int64.max
            if freeBytes < media.sizeBytes * 2 + 50 * 1024 * 1024 { throw ImportRejection.noSpace }
            var stored: (path: String, sha256: String)
            var thumb: (path: String, sha256: String)
            var width = media.width, height = media.height
            var size = media.sizeBytes
            switch media.kind {
            case .PHOTO:
                guard let data = media.data, let src = CGImageSourceCreateWithData(data as CFData, nil) else { throw ImportRejection.unreadable }
                var bytes = [UInt8](data)
                if !keepOriginal && CGFloat(max(width, height)) > MediaImporter.maxPhotoEdge, let scaled = MediaImporter.downscaled(src, edge: MediaImporter.maxPhotoEdge), let jpg = MediaImporter.jpeg(scaled, quality: 0.9) {
                    bytes = [UInt8](jpg)
                    width = scaled.width
                    height = scaled.height
                } else if let upright = MediaImporter.downscaled(src, edge: CGFloat(max(width, height))), let jpg = MediaImporter.jpeg(upright, quality: 0.92), !data.isJPEGWithoutRotation {
                    // HEIC/PNG or a rotated JPEG: store upright JPEG so every viewer (and Android after a backup) shows it right.
                    bytes = [UInt8](jpg)
                }
                size = Int64(bytes.count)
                stored = try vault.store(bytes: bytes, folder: .media, ext: "jpg")
                let t = MediaImporter.downscaled(src, edge: MediaImporter.thumbEdge).flatMap { MediaImporter.jpeg($0, quality: 0.8) } ?? Data()
                thumb = try vault.store(bytes: [UInt8](t), folder: .thumbs, ext: "jpg")
            case .VIDEO:
                guard let url = media.fileURL else { throw ImportRejection.unreadable }
                var source = url
                var exported: URL?
                if !keepOriginal && (min(width, height) > 1080 || media.sizeBytes > MediaImporter.maxVideoBytes) {
                    exported = await MediaImporter.exportVideo1080(url)
                    if let exported { source = exported }
                }
                defer { if let exported { try? FileManager.default.removeItem(at: exported) } }
                size = ((try? FileManager.default.attributesOfItem(atPath: source.path)[.size]) as? NSNumber)?.int64Value ?? media.sizeBytes
                if size > MediaImporter.maxVideoBytes { throw ImportRejection.videoTooLarge }
                if exported != nil, min(width, height) > 1080 {
                    let scale = 1080 / CGFloat(min(width, height))
                    width = Int(CGFloat(width) * scale)
                    height = Int(CGFloat(height) * scale)
                }
                stored = try vault.store(file: source, folder: .media, ext: "mp4")
                let t = media.preview?.jpegData(compressionQuality: 0.8) ?? Data()
                thumb = try vault.store(bytes: [UInt8](t), folder: .thumbs, ext: "jpg")
            }
            do {
                return try store.addMedia(Media(memoryId: memoryId, kind: media.kind, encryptedPath: stored.path, thumbPath: thumb.path,
                                                takenAt: media.takenAtMillis, durationMs: media.durationMs, sizeBytes: size, width: width,
                                                height: height, sha256: stored.sha256))
            } catch {
                vault.delete(stored.path)
                vault.delete(thumb.path)
                throw error
            }
        }.value
    }

    /// Short side 1080 (portrait 4K → 1080×1920), as TransformerVideoOptimizer does on Android.
    static func exportVideo1080(_ url: URL) async -> URL? {
        let out = FileManager.default.temporaryDirectory.appendingPathComponent("opt-\(UUID().uuidString).mp4")
        guard let session = AVAssetExportSession(asset: AVURLAsset(url: url), presetName: AVAssetExportPreset1920x1080) else { return nil }
        session.outputURL = out
        session.outputFileType = .mp4
        session.shouldOptimizeForNetworkUse = false
        await session.export()
        return session.status == .completed ? out : nil
    }

    /// Stores a profile photo (memoryId nil) for a person; returns the media id.
    static func saveProfilePhoto(_ item: PhotosPickerItem, store: VaultStore, vault: MediaVault) async -> Int64? {
        let (ok, _) = await inspect([item], optimize: true)
        guard let media = ok.first, media.kind == .PHOTO else { return nil }
        return try? await save(media, memoryId: nil, keepOriginal: false, store: store, vault: vault)
    }
}

private extension Data {
    /// A JPEG whose EXIF orientation is "up" (or absent) can be stored byte for byte.
    var isJPEGWithoutRotation: Bool {
        guard count > 3, self[startIndex] == 0xFF, self[startIndex + 1] == 0xD8 else { return false }
        guard let src = CGImageSourceCreateWithData(self as CFData, nil),
              let props = CGImageSourceCopyPropertiesAtIndex(src, 0, nil) as? [CFString: Any] else { return false }
        return (props[kCGImagePropertyOrientation] as? Int ?? 1) == 1
    }
}
