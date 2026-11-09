import ImageIO
import SwiftUI
import UIKit
import Vision
import YaadeinData
import YaadeinDomain

/// Finds faces with Apple's on-device Vision framework, makes the same 64×64 LBP descriptor as Android
/// (FaceFeatures) and clusters them (FaceClustering). Nothing is tagged until the user confirms.
enum FaceScanner {
    static func samples(in image: CGImage, mediaId: Int64) -> [FaceSample] {
        let request = VNDetectFaceRectanglesRequest()
        try? VNImageRequestHandler(cgImage: image, options: [:]).perform([request])
        let faces = (request.results ?? []).filter { $0.boundingBox.width > 0.04 }
        let w = CGFloat(image.width), h = CGFloat(image.height)
        return faces.compactMap { face in
            // Vision boxes are normalised with the origin at the bottom left.
            let box = face.boundingBox
            var rect = CGRect(x: box.minX * w, y: (1 - box.maxY) * h, width: box.width * w, height: box.height * h)
            rect = rect.insetBy(dx: -rect.width * 0.1, dy: -rect.height * 0.1).intersection(CGRect(x: 0, y: 0, width: w, height: h))
            guard let crop = image.cropping(to: rect), let grey = grey64(crop, roll: CGFloat(face.roll?.doubleValue ?? 0)) else { return nil }
            return FaceSample(mediaId: mediaId, embedding: FaceFeatures.embed(grey), left: Float(rect.minX / w), top: Float(rect.minY / h),
                              right: Float(rect.maxX / w), bottom: Float(rect.maxY / h))
        }
    }

    /// Upright (roll removed, like Android's eye alignment), 64×64 grey pixels.
    private static func grey64(_ image: CGImage, roll: CGFloat) -> [Int]? {
        let n = FaceFeatures.size
        var pixels = [UInt8](repeating: 0, count: n * n)
        guard let ctx = CGContext(data: &pixels, width: n, height: n, bitsPerComponent: 8, bytesPerRow: n,
                                  space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGImageAlphaInfo.none.rawValue) else { return nil }
        ctx.interpolationQuality = .high
        ctx.translateBy(x: CGFloat(n) / 2, y: CGFloat(n) / 2)
        ctx.rotate(by: roll)
        ctx.translateBy(x: -CGFloat(n) / 2, y: -CGFloat(n) / 2)
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: n, height: n))
        return pixels.map(Int.init)
    }
}

/// Settings → Face suggestions (FacesScreen.kt): switch, scan, "Is this Rahul?" cards.
struct FacesView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @State private var progress: (Int, Int)?
    @State private var choosing: Int64?

    var body: some View {
        let _ = model.revision
        let store = model.store
        let pending = (store?.faceClusters() ?? []).filter { !$0.confirmed }.sorted { $0.count > $1.count }
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                SettingsSwitchRow(icon: "face.smiling", title: L("faces_enable"), subtitle: L("faces_enable_help"),
                                  isOn: Binding(get: { model.settings.faceSuggestions }, set: { v in model.updateSettings { $0.faceSuggestions = v } }))
                Text(L("faces_privacy")).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant)
                if model.settings.faceSuggestions {
                    if let progress {
                        Text(L("faces_scanning", progress.0, progress.1)).font(look.type.bodyMedium)
                        ProgressView(value: Double(progress.0), total: Double(max(1, progress.1)))
                    } else {
                        Button(L("faces_scan")) { Task { await scan() } }.buttonStyle(FilledButtonStyle(tonal: true))
                    }
                    if pending.isEmpty {
                        Text(L("faces_none")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
                    } else {
                        Text(LP("faces_pending", pending.count)).font(look.type.titleMedium)
                        ForEach(pending) { cluster in card(cluster) }
                    }
                }
            }
            .padding(Spacing.gutter)
        }
        .screenTitle(L("settings_faces"))
    }

    private func card(_ c: FaceCluster) -> some View {
        let person = c.suggestedPersonId.flatMap { model.store?.person(id: $0) }
        return VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack(spacing: Spacing.md) {
                FaceCrop(mediaId: c.sampleMediaId, box: c.sampleBox).frame(width: 72, height: 72).clipShape(Circle())
                VStack(alignment: .leading) {
                    Text(person.map { L("faces_is_this", $0.displayName) } ?? L("faces_who")).font(look.type.titleMedium)
                    Text(LP("faces_seen", c.count)).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant)
                }
            }
            if let person, choosing != c.id {
                HStack {
                    Button(L("faces_yes")) { try? model.store?.confirmFace(clusterId: c.id, personId: person.id) }.buttonStyle(FilledButtonStyle())
                    Button(L("faces_not_sure")) { choosing = c.id }.buttonStyle(TextButtonStyle())
                }
            } else {
                FlowLayout(spacing: Spacing.sm) {
                    ForEach(model.store?.persons() ?? []) { p in
                        Chip(text: p.displayName) {
                            try? model.store?.confirmFace(clusterId: c.id, personId: p.id)
                            choosing = nil
                        }
                    }
                    Chip(text: L("faces_not_sure")) { try? model.store?.dismissFace(clusterId: c.id) }
                }
            }
        }
        .padding(Spacing.lg)
        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(look.colors.surfaceContainerLow))
    }

    private func scan() async {
        guard let store = model.store, let vault = model.vault else { return }
        let ids = store.unscannedPhotoIds()
        progress = (0, ids.count)
        for (i, id) in ids.enumerated() {
            let found: [FaceSample] = await Task.detached(priority: .utility) {
                guard let m = store.media(id: id), let bytes = try? vault.read(m.encryptedPath),
                      let src = CGImageSourceCreateWithData(Data(bytes) as CFData, nil),
                      let image = MediaImporter.downscaled(src, edge: 1600) else { return [] }
                return FaceScanner.samples(in: image, mediaId: id)
            }.value
            try? store.addFaces(mediaId: id, samples: found)
            progress = (i + 1, ids.count)
        }
        suggestPeople()
        progress = nil
    }

    /// A cluster whose sample photo already has exactly one tagged person suggests that person.
    private func suggestPeople() {
        guard let store = model.store else { return }
        for c in store.faceClusters() where !c.confirmed && c.suggestedPersonId == nil {
            let tagged = store.mediaPeople(mediaId: c.sampleMediaId)
            let inMemory = store.media(id: c.sampleMediaId)?.memoryId.flatMap { store.card(id: $0)?.people.map(\.id) } ?? []
            if let only = (tagged.count == 1 ? tagged : inMemory.count == 1 ? inMemory : []).first {
                var updated = store.snapshot
                if let i = updated.faceClusters.firstIndex(where: { $0.id == c.id }) {
                    updated.faceClusters[i].suggestedPersonId = only
                    try? store.replaceAll(updated)
                }
            }
        }
    }
}

/// The face part of a photo.
struct FaceCrop: View {
    let mediaId: Int64
    let box: [Float]
    @Environment(AppModel.self) private var model
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            if let image { Image(uiImage: image).resizable().scaledToFill() } else { Circle().fill(Color.gray.opacity(0.3)) }
        }
        .task {
            guard let m = model.store?.media(id: mediaId), let vault = model.vault, box.count == 4 else { return }
            image = await Task.detached {
                guard let bytes = try? vault.read(m.encryptedPath), let src = CGImageSourceCreateWithData(Data(bytes) as CFData, nil),
                      let full = MediaImporter.downscaled(src, edge: 1600) else { return nil }
                let w = CGFloat(full.width), h = CGFloat(full.height)
                let r = CGRect(x: CGFloat(box[0]) * w, y: CGFloat(box[1]) * h, width: CGFloat(box[2] - box[0]) * w, height: CGFloat(box[3] - box[1]) * h)
                return full.cropping(to: r).map { UIImage(cgImage: $0) }
            }.value
        }
    }
}
