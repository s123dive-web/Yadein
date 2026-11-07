import Foundation

/// Streaming authenticated encryption shared by media files (`YDN2`) and backups (`YDNB` format 2), exactly as Android's
/// `MediaCrypto` and `ChunkedAead`: the plaintext is cut into 64 KiB chunks, each sealed with AES-256-GCM on its own.
/// Nonce for chunk i = 7-byte random prefix ‖ i as 4-byte big-endian ‖ last-chunk flag. The file header is the
/// associated data. A changed byte, reordered chunk or cut-off end fails to decrypt.
public enum ChunkedAEAD {
    public static let chunk = 64 * 1024
    public static let tagBytes = 16
    public static let prefixBytes = 7

    public static func nonce(prefix: [UInt8], index: UInt32, last: Bool) -> [UInt8] {
        prefix + [UInt8(index >> 24), UInt8((index >> 16) & 0xff), UInt8((index >> 8) & 0xff), UInt8(index & 0xff), last ? 1 : 0]
    }

    public static func randomBytes(_ n: Int) -> [UInt8] {
        var g = SystemRandomNumberGenerator()
        return (0..<n).map { _ in UInt8.random(in: 0...255, using: &g) }
    }
}

/// Where encrypted or decrypted bytes go.
public protocol ByteSink: AnyObject {
    func write(_ bytes: [UInt8]) throws
}

/// Where bytes come from. `read` returns up to `max` bytes; an empty result means the end.
public protocol ByteSource: AnyObject {
    func read(max: Int) throws -> [UInt8]
}

public final class FileSink: ByteSink {
    private let handle: FileHandle
    public init(url: URL) throws {
        FileManager.default.createFile(atPath: url.path, contents: nil)
        handle = try FileHandle(forWritingTo: url)
        try handle.truncate(atOffset: 0)
    }
    public func write(_ bytes: [UInt8]) throws { try handle.write(contentsOf: Data(bytes)) }
    public func close() throws { try handle.close() }
}

public final class FileSource: ByteSource {
    private let handle: FileHandle
    public init(url: URL) throws { handle = try FileHandle(forReadingFrom: url) }
    public func read(max: Int) throws -> [UInt8] { [UInt8](try handle.read(upToCount: max) ?? Data()) }
    public func close() throws { try handle.close() }
}

public final class MemorySink: ByteSink {
    public private(set) var bytes: [UInt8] = []
    public init() {}
    public func write(_ b: [UInt8]) throws { bytes += b }
}

public final class MemorySource: ByteSource {
    private let data: [UInt8]
    private var pos = 0
    public init(_ data: [UInt8]) { self.data = data }
    public func read(max: Int) throws -> [UInt8] {
        let n = min(max, data.count - pos)
        defer { pos += n }
        return Array(data[pos..<(pos + n)])
    }
}

/// Encrypts everything written to it; `finish()` seals the final chunk (always flagged last, possibly empty).
public final class ChunkedEncryptor: ByteSink {
    private let out: ByteSink
    private let aead: AEAD
    private let key: [UInt8]
    private let prefix: [UInt8]
    private let aad: [UInt8]
    private var buffer: [UInt8] = []
    private var index: UInt32 = 0
    private var finished = false

    public init(out: ByteSink, aead: AEAD, key: [UInt8], prefix: [UInt8], aad: [UInt8]) {
        self.out = out
        self.aead = aead
        self.key = key
        self.prefix = prefix
        self.aad = aad
        buffer.reserveCapacity(ChunkedAEAD.chunk)
    }

    public func write(_ bytes: [UInt8]) throws {
        var offset = 0
        while offset < bytes.count {
            // Only seal a full chunk once more data arrives, so the last chunk is always flagged as last.
            if buffer.count == ChunkedAEAD.chunk { try seal(last: false) }
            let n = min(bytes.count - offset, ChunkedAEAD.chunk - buffer.count)
            buffer += bytes[offset..<(offset + n)]
            offset += n
        }
    }

    private func seal(last: Bool) throws {
        let nonce = ChunkedAEAD.nonce(prefix: prefix, index: index, last: last)
        try out.write(try aead.seal(buffer, key: key, nonce: nonce, aad: aad))
        index += 1
        buffer.removeAll(keepingCapacity: true)
    }

    public func finish() throws {
        guard !finished else { return }
        finished = true
        try seal(last: true)
    }
}

/// Decrypts a `ChunkedEncryptor` stream. The first chunk is opened on the first read, so a wrong key fails at once.
public final class ChunkedDecryptor: ByteSource {
    private let input: ByteSource
    private let aead: AEAD
    private let key: [UInt8]
    private let prefix: [UInt8]
    private let aad: [UInt8]
    private var plain: [UInt8] = []
    private var pos = 0
    private var index: UInt32 = 0
    private var finished = false
    private var pending: [UInt8] = []

    public init(input: ByteSource, aead: AEAD, key: [UInt8], prefix: [UInt8], aad: [UInt8]) {
        self.input = input
        self.aead = aead
        self.key = key
        self.prefix = prefix
        self.aad = aad
    }

    private func fill(_ want: Int) throws -> [UInt8] {
        var out = pending
        pending = []
        while out.count < want {
            let r = try input.read(max: want - out.count)
            if r.isEmpty { break }
            out += r
        }
        return out
    }

    private func nextChunk() throws {
        let size = ChunkedAEAD.chunk + ChunkedAEAD.tagBytes
        let sealed = try fill(size)
        var last = sealed.count < size
        if !last {
            let peek = try fill(1)
            last = peek.isEmpty
            pending = peek
        }
        guard sealed.count >= ChunkedAEAD.tagBytes else { throw CryptoError.truncated }
        let nonce = ChunkedAEAD.nonce(prefix: prefix, index: index, last: last)
        plain = try aead.open(sealed, key: key, nonce: nonce, aad: aad)
        pos = 0
        index += 1
        if last { finished = true }
    }

    public func read(max: Int) throws -> [UInt8] {
        while pos >= plain.count {
            if finished { return [] }
            try nextChunk()
        }
        let n = Swift.min(max, plain.count - pos)
        defer { pos += n }
        return Array(plain[pos..<(pos + n)])
    }
}

/// `YDN2` media files (Android MediaCrypto format 2): `"YDN2" ‖ prefix(7)` then chunks; header is the AAD.
public enum MediaFileFormat {
    public static let magic: [UInt8] = Array("YDN2".utf8)
    public static let headerBytes = 4 + ChunkedAEAD.prefixBytes

    /// Encrypts `source` into `sink`, returning the plaintext SHA-256 (hex) that the database records.
    @discardableResult
    public static func encrypt(_ source: ByteSource, to sink: ByteSink, aead: AEAD, key: [UInt8],
                               prefix: [UInt8] = ChunkedAEAD.randomBytes(ChunkedAEAD.prefixBytes)) throws -> String {
        let header = magic + prefix
        try sink.write(header)
        let enc = ChunkedEncryptor(out: sink, aead: aead, key: key, prefix: prefix, aad: header)
        var sha = SHA256()
        while true {
            let b = try source.read(max: ChunkedAEAD.chunk)
            if b.isEmpty { break }
            sha.update(b)
            try enc.write(b)
        }
        try enc.finish()
        return sha.hexDigest()
    }

    public static func decryptor(_ source: ByteSource, aead: AEAD, key: [UInt8]) throws -> ChunkedDecryptor {
        var header: [UInt8] = []
        while header.count < headerBytes {
            let r = try source.read(max: headerBytes - header.count)
            if r.isEmpty { throw CryptoError.truncated }
            header += r
        }
        guard Array(header.prefix(4)) == magic else { throw CryptoError.invalidFormat("Not a Yaadein media file") }
        return ChunkedDecryptor(input: source, aead: aead, key: key, prefix: Array(header[4...]), aad: header)
    }

    /// Plaintext length of an encrypted file of `fileLength` bytes.
    public static func plainLength(fileLength: Int64) -> Int64 {
        let body = fileLength - Int64(headerBytes)
        guard body >= Int64(ChunkedAEAD.tagBytes) else { return 0 }
        let sealedChunk = Int64(ChunkedAEAD.chunk + ChunkedAEAD.tagBytes)
        let chunks = (body + sealedChunk - 1) / sealedChunk
        return body - chunks * Int64(ChunkedAEAD.tagBytes)
    }
}

public func readAll(_ source: ByteSource) throws -> [UInt8] {
    var out: [UInt8] = []
    while true {
        let b = try source.read(max: 1 << 16)
        if b.isEmpty { return out }
        out += b
    }
}

/// Random access to the plaintext of a `YDN2` file (MediaCrypto.openReader on Android): only the chunks covering the
/// requested range are read and decrypted, so a video can be scrubbed without decrypting it all or writing plaintext.
public final class MediaRandomReader {
    private let handle: FileHandle
    private let aead: AEAD
    private let key: [UInt8]
    private let header: [UInt8]
    private let prefix: [UInt8]
    private let fileLength: Int64
    private let chunkCount: Int64
    public let plainLength: Int64
    private var cachedIndex: Int64 = -1
    private var cached: [UInt8] = []
    private let lock = NSLock()

    public init(url: URL, aead: AEAD, key: [UInt8]) throws {
        handle = try FileHandle(forReadingFrom: url)
        self.aead = aead
        self.key = key
        let h = [UInt8](try handle.read(upToCount: MediaFileFormat.headerBytes) ?? Data())
        guard h.count == MediaFileFormat.headerBytes, Array(h.prefix(4)) == MediaFileFormat.magic else {
            try? handle.close()
            throw CryptoError.invalidFormat("Not a Yaadein media file")
        }
        header = h
        prefix = Array(h[4...])
        fileLength = Int64(try handle.seekToEnd())
        let body = fileLength - Int64(MediaFileFormat.headerBytes)
        let sealedChunk = Int64(ChunkedAEAD.chunk + ChunkedAEAD.tagBytes)
        chunkCount = max(1, (body + sealedChunk - 1) / sealedChunk)
        plainLength = MediaFileFormat.plainLength(fileLength: fileLength)
    }

    deinit { try? handle.close() }

    private func chunk(_ index: Int64) throws -> [UInt8] {
        if index == cachedIndex { return cached }
        let sealedChunk = Int64(ChunkedAEAD.chunk + ChunkedAEAD.tagBytes)
        let offset = Int64(MediaFileFormat.headerBytes) + index * sealedChunk
        try handle.seek(toOffset: UInt64(offset))
        let sealed = [UInt8](try handle.read(upToCount: Int(min(sealedChunk, fileLength - offset))) ?? Data())
        let nonce = ChunkedAEAD.nonce(prefix: prefix, index: UInt32(index), last: index == chunkCount - 1)
        cached = try aead.open(sealed, key: key, nonce: nonce, aad: header)
        cachedIndex = index
        return cached
    }

    /// Up to `length` plaintext bytes starting at `offset` (fewer at the end).
    public func read(offset: Int64, length: Int) throws -> [UInt8] {
        try lock.withLock {
            var out: [UInt8] = []
            var pos = offset
            let end = min(plainLength, offset + Int64(length))
            while pos < end {
                let index = pos / Int64(ChunkedAEAD.chunk)
                let c = try chunk(index)
                let start = Int(pos - index * Int64(ChunkedAEAD.chunk))
                let n = min(c.count - start, Int(end - pos))
                guard n > 0 else { break }
                out += c[start..<(start + n)]
                pos += Int64(n)
            }
            return out
        }
    }
}
