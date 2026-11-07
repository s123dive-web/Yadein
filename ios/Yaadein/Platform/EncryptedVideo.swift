import AVFoundation
import AVKit
import SwiftUI
import UniformTypeIdentifiers
import YaadeinCrypto
import YaadeinData

/// Plays a vault video without writing plaintext anywhere: AVFoundation asks for byte ranges through a custom URL
/// scheme and each range is decrypted from the `YDN2` chunks on demand (EncryptedDataSource on Android).
final class EncryptedVideoLoader: NSObject, AVAssetResourceLoaderDelegate {
    static let scheme = "yaadein-vault"
    private let reader: MediaRandomReader
    private let contentType: String
    private let queue = DispatchQueue(label: "yaadein.video")

    init(reader: MediaRandomReader, contentType: String) {
        self.reader = reader
        self.contentType = contentType
    }

    func resourceLoader(_ resourceLoader: AVAssetResourceLoader, shouldWaitForLoadingOfRequestedResource loadingRequest: AVAssetResourceLoadingRequest) -> Bool {
        queue.async { [reader, contentType] in
            if let info = loadingRequest.contentInformationRequest {
                info.contentType = contentType
                info.contentLength = reader.plainLength
                info.isByteRangeAccessSupported = true
            }
            if let data = loadingRequest.dataRequest {
                var offset = data.currentOffset != 0 ? data.currentOffset : data.requestedOffset
                let end = data.requestsAllDataToEndOfResource ? reader.plainLength : data.requestedOffset + Int64(data.requestedLength)
                do {
                    while offset < end, !loadingRequest.isCancelled {
                        let chunk = try reader.read(offset: offset, length: Int(min(256 * 1024, end - offset)))
                        if chunk.isEmpty { break }
                        data.respond(with: Data(chunk))
                        offset += Int64(chunk.count)
                    }
                    loadingRequest.finishLoading()
                } catch {
                    loadingRequest.finishLoading(with: error)
                }
            } else {
                loadingRequest.finishLoading()
            }
        }
        return true
    }

    /// An AVPlayer for a vault path; keep the returned loader alive as long as the player.
    static func player(path: String, vault: MediaVault, aead: AEAD, key: [UInt8]) -> (AVPlayer, EncryptedVideoLoader)? {
        guard let reader = try? MediaRandomReader(url: vault.url(path), aead: aead, key: key),
              let url = URL(string: "\(scheme)://media/\(UUID().uuidString).\(path.hasSuffix(".m4a") ? "m4a" : "mp4")") else { return nil }
        let type = path.hasSuffix(".m4a") ? UTType.mpeg4Audio.identifier : UTType.mpeg4Movie.identifier
        let loader = EncryptedVideoLoader(reader: reader, contentType: type)
        let asset = AVURLAsset(url: url)
        asset.resourceLoader.setDelegate(loader, queue: loader.queue)
        return (AVPlayer(playerItem: AVPlayerItem(asset: asset)), loader)
    }
}

struct EncryptedVideoPlayer: View {
    let path: String
    @Environment(AppModel.self) private var model
    @State private var player: AVPlayer?
    @State private var loader: EncryptedVideoLoader?

    var body: some View {
        ZStack {
            if let player {
                VideoPlayer(player: player)
            } else {
                ProgressView().tint(.white)
            }
        }
        .task(id: path) {
            guard let vault = model.vault, let keys = try? model.session.currentKeys(),
                  let made = EncryptedVideoLoader.player(path: path, vault: vault, aead: model.aead, key: keys.media) else { return }
            loader = made.1
            player = made.0
        }
        .onDisappear { player?.pause() }
    }
}
