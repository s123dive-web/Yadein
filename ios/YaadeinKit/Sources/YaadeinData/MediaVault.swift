import Foundation
import YaadeinCrypto

/// App-private folder of encrypted files (photos, videos, thumbnails, voice notes, reels), each in the `YDN2` format
/// with the vault's media key. Paths stored in the database are relative to the vault root, as on Android.
public final class MediaVault: @unchecked Sendable {
    public let root: URL
    private let aead: AEAD
    private let key: () throws -> [UInt8]

    public enum Folder: String, CaseIterable { case media, thumbs, voice, reels, staging }

    public init(root: URL, aead: AEAD, key: @escaping () throws -> [UInt8]) {
        self.root = root
        self.aead = aead
        self.key = key
    }

    public func url(_ relativePath: String) -> URL { root.appendingPathComponent(relativePath) }

    public func newPath(_ folder: Folder, ext: String) -> String { "\(folder.rawValue)/\(UUID().uuidString.lowercased()).\(ext)" }

    private func ensureFolder(of url: URL) throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    }

    /// temp file → encrypt → verify (decrypt and compare SHA-256) → rename into place. Returns the relative path and
    /// the plaintext SHA-256. Nothing is left behind if any step fails.
    public func store(_ source: ByteSource, folder: Folder, ext: String) throws -> (path: String, sha256: String) {
        let path = newPath(folder, ext: ext)
        let target = url(path)
        try ensureFolder(of: target)
        let temp = target.appendingPathExtension("part")
        defer { try? FileManager.default.removeItem(at: temp) }
        let sink = try FileSink(url: temp)
        let sha: String
        do {
            sha = try MediaFileFormat.encrypt(source, to: sink, aead: aead, key: try key())
            try sink.close()
        } catch {
            try? sink.close()
            throw error
        }
        guard try sha256OfPlaintext(at: temp) == sha else { throw CryptoError.authenticationFailed }
        try FileManager.default.moveItem(at: temp, to: target)
        return (path, sha)
    }

    public func store(bytes: [UInt8], folder: Folder, ext: String) throws -> (path: String, sha256: String) {
        try store(MemorySource(bytes), folder: folder, ext: ext)
    }

    public func store(file: URL, folder: Folder, ext: String) throws -> (path: String, sha256: String) {
        let source = try FileSource(url: file)
        defer { try? source.close() }
        return try store(source, folder: folder, ext: ext)
    }

    /// Sequential plaintext of a stored file.
    public func open(_ relativePath: String) throws -> ByteSource {
        try MediaFileFormat.decryptor(try FileSource(url: url(relativePath)), aead: aead, key: try key())
    }

    public func read(_ relativePath: String) throws -> [UInt8] { try readAll(try open(relativePath)) }

    /// Streams the plaintext of a stored file to `sink` in pieces.
    public func stream(_ relativePath: String, to sink: ([UInt8]) throws -> Void) throws {
        let source = try open(relativePath)
        while true {
            let b = try source.read(max: 1 << 16)
            if b.isEmpty { return }
            try sink(b)
        }
    }

    /// Decrypts to a temporary plaintext file (for players and share sheets); the caller deletes it.
    public func decryptToTemporaryFile(_ relativePath: String, ext: String) throws -> URL {
        let out = FileManager.default.temporaryDirectory.appendingPathComponent("yaadein-\(UUID().uuidString).\(ext)")
        let sink = try FileSink(url: out)
        defer { try? sink.close() }
        try stream(relativePath) { try sink.write($0) }
        return out
    }

    public func sha256OfPlaintext(at url: URL) throws -> String {
        let source = try MediaFileFormat.decryptor(try FileSource(url: url), aead: aead, key: try key())
        var sha = SHA256()
        while true {
            let b = try source.read(max: 1 << 16)
            if b.isEmpty { break }
            sha.update(b)
        }
        return sha.hexDigest()
    }

    public func delete(_ relativePath: String) { try? FileManager.default.removeItem(at: url(relativePath)) }

    public func exists(_ relativePath: String) -> Bool { FileManager.default.fileExists(atPath: url(relativePath).path) }

    public func size(_ relativePath: String) -> Int64 {
        ((try? FileManager.default.attributesOfItem(atPath: url(relativePath).path)[.size]) as? NSNumber)?.int64Value ?? 0
    }

    /// Files on disk that no row refers to (an import cut short); Android prunes these at start-up too.
    public func pruneOrphans(keeping referenced: Set<String>) {
        for folder in [Folder.media, .thumbs, .voice, .staging] {
            let dir = root.appendingPathComponent(folder.rawValue)
            guard let names = try? FileManager.default.contentsOfDirectory(atPath: dir.path) else { continue }
            for name in names where !referenced.contains("\(folder.rawValue)/\(name)") {
                try? FileManager.default.removeItem(at: dir.appendingPathComponent(name))
            }
        }
    }

    public func wipe() { try? FileManager.default.removeItem(at: root) }
}
