import Foundation

/// Byte reader with single-byte access and exact reads over a `ByteSource`, never reading past what it is asked for
/// (so a zip entry's data descriptor is still there after inflating its data).
public final class BufferedReader {
    private let source: ByteSource
    private var buffer: [UInt8] = []
    private var pos = 0

    public init(_ source: ByteSource) { self.source = source }

    private func refill() throws -> Bool {
        if pos < buffer.count { return true }
        buffer = try source.read(max: 1 << 16)
        pos = 0
        return !buffer.isEmpty
    }

    public func readByte() throws -> UInt8? {
        guard try refill() else { return nil }
        defer { pos += 1 }
        return buffer[pos]
    }

    /// Exactly `n` bytes, or fewer only at the end of the stream.
    public func read(_ n: Int) throws -> [UInt8] {
        var out: [UInt8] = []
        out.reserveCapacity(n)
        while out.count < n, try refill() {
            let k = min(n - out.count, buffer.count - pos)
            out += buffer[pos..<(pos + k)]
            pos += k
        }
        return out
    }

    /// Up to `max` bytes that are already buffered (or one refill).
    public func readSome(_ max: Int) throws -> [UInt8] {
        guard try refill() else { return [] }
        let k = min(max, buffer.count - pos)
        defer { pos += k }
        return Array(buffer[pos..<(pos + k)])
    }
}

/// Raw DEFLATE decoder (RFC 1951), streaming its output in pieces. Zip entries written by Android's ZipOutputStream are
/// deflated; Apple has no public zip API, and this keeps the backup code testable on Linux.
public final class Inflater {
    private let input: BufferedReader
    private var bitBuffer: UInt32 = 0
    private var bitCount: UInt32 = 0
    private var window = [UInt8](repeating: 0, count: 32_768)
    private var windowPos = 0
    private var pendingOut: [UInt8] = []

    public init(_ input: BufferedReader) { self.input = input }

    private func bits(_ n: UInt32) throws -> UInt32 {
        while bitCount < n {
            guard let b = try input.readByte() else { throw CryptoError.truncated }
            bitBuffer |= UInt32(b) << bitCount
            bitCount += 8
        }
        let v = bitBuffer & ((1 << n) - 1)
        bitBuffer >>= n
        bitCount -= n
        return v
    }

    private struct Huffman {
        var counts = [Int](repeating: 0, count: 16)
        var symbols: [Int] = []

        init(lengths: [Int]) {
            for l in lengths { counts[l] += 1 }
            counts[0] = 0
            var offs = [Int](repeating: 0, count: 16)
            for i in 1..<16 { offs[i] = offs[i - 1] + counts[i - 1] }
            symbols = [Int](repeating: 0, count: lengths.count)
            for (s, l) in lengths.enumerated() where l != 0 {
                symbols[offs[l]] = s
                offs[l] += 1
            }
        }
    }

    private func decode(_ h: Huffman) throws -> Int {
        var code = 0, first = 0, index = 0
        for len in 1..<16 {
            code |= Int(try bits(1))
            let count = h.counts[len]
            if code - count < first { return h.symbols[index + (code - first)] }
            index += count
            first += count
            first <<= 1
            code <<= 1
        }
        throw CryptoError.invalidFormat("bad deflate code")
    }

    private static let lengthBase = [3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258]
    private static let lengthExtra = [0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0]
    private static let distBase = [1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577]
    private static let distExtra = [0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13]
    private static let fixedLit = Huffman(lengths: (0..<288).map { $0 < 144 ? 8 : $0 < 256 ? 9 : $0 < 280 ? 7 : 8 })
    private static let fixedDist = Huffman(lengths: [Int](repeating: 5, count: 30))

    private func emit(_ b: UInt8) {
        window[windowPos] = b
        windowPos = (windowPos + 1) & 32_767
        pendingOut.append(b)
    }

    /// Inflates the whole stream, handing output to `sink` in pieces.
    public func run(_ sink: ([UInt8]) throws -> Void) throws {
        var final = false
        while !final {
            final = try bits(1) == 1
            let type = try bits(2)
            switch type {
            case 0:
                bitBuffer = 0
                bitCount = 0
                let lenBytes = try input.read(4)
                guard lenBytes.count == 4 else { throw CryptoError.truncated }
                var remaining = Int(lenBytes[0]) | Int(lenBytes[1]) << 8
                while remaining > 0 {
                    let chunk = try input.read(min(remaining, 16_384))
                    guard !chunk.isEmpty else { throw CryptoError.truncated }
                    for b in chunk { emit(b) }
                    remaining -= chunk.count
                    try flush(sink)
                }
            case 1:
                try block(Inflater.fixedLit, Inflater.fixedDist, sink)
            case 2:
                let hlit = Int(try bits(5)) + 257, hdist = Int(try bits(5)) + 1, hclen = Int(try bits(4)) + 4
                let order = [16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15]
                var cl = [Int](repeating: 0, count: 19)
                for i in 0..<hclen { cl[order[i]] = Int(try bits(3)) }
                let clh = Huffman(lengths: cl)
                var lengths: [Int] = []
                while lengths.count < hlit + hdist {
                    let sym = try decode(clh)
                    switch sym {
                    case 0...15: lengths.append(sym)
                    case 16:
                        guard let prev = lengths.last else { throw CryptoError.invalidFormat("bad repeat") }
                        lengths += [Int](repeating: prev, count: 3 + Int(try bits(2)))
                    case 17: lengths += [Int](repeating: 0, count: 3 + Int(try bits(3)))
                    default: lengths += [Int](repeating: 0, count: 11 + Int(try bits(7)))
                    }
                }
                try block(Huffman(lengths: Array(lengths[0..<hlit])), Huffman(lengths: Array(lengths[hlit...])), sink)
            default:
                throw CryptoError.invalidFormat("bad deflate block")
            }
        }
        try flush(sink)
    }

    private func flush(_ sink: ([UInt8]) throws -> Void) throws {
        if !pendingOut.isEmpty {
            try sink(pendingOut)
            pendingOut.removeAll(keepingCapacity: true)
        }
    }

    private func block(_ lit: Huffman, _ dist: Huffman, _ sink: ([UInt8]) throws -> Void) throws {
        while true {
            let sym = try decode(lit)
            if sym < 256 {
                emit(UInt8(sym))
            } else if sym == 256 {
                return
            } else {
                let li = sym - 257
                guard li < 29 else { throw CryptoError.invalidFormat("bad length") }
                let len = Inflater.lengthBase[li] + Int(try bits(UInt32(Inflater.lengthExtra[li])))
                let di = try decode(dist)
                guard di < 30 else { throw CryptoError.invalidFormat("bad distance") }
                let d = Inflater.distBase[di] + Int(try bits(UInt32(Inflater.distExtra[di])))
                for _ in 0..<len { emit(window[(windowPos - d + 32_768) & 32_767]) }
            }
            if pendingOut.count >= 65_536 { try flush(sink) }
        }
    }
}

/// Reads a zip stream entry by entry from the front, like java.util.zip.ZipInputStream (the central directory is not
/// needed). Handles stored and deflated entries, with or without data descriptors (Android writes deflated entries
/// with descriptors).
public final class ZipStreamReader {
    private let reader: BufferedReader

    public init(_ source: ByteSource) { reader = BufferedReader(source) }

    private func le16(_ b: [UInt8], _ o: Int) -> Int { Int(b[o]) | Int(b[o + 1]) << 8 }
    private func le32(_ b: [UInt8], _ o: Int) -> UInt32 { UInt32(b[o]) | UInt32(b[o + 1]) << 8 | UInt32(b[o + 2]) << 16 | UInt32(b[o + 3]) << 24 }

    /// Calls `body(name, read)` for each entry; `read` streams the entry's bytes to a closure. Entries the body does
    /// not read are skipped.
    public func forEachEntry(_ body: (String, (([UInt8]) throws -> Void) throws -> Void) throws -> Void) throws {
        while true {
            let sig = try reader.read(4)
            guard sig.count == 4, le32(sig, 0) == 0x0403_4b50 else { return } // central directory or end
            let h = try reader.read(26)
            guard h.count == 26 else { throw CryptoError.truncated }
            let flags = le16(h, 2), method = le16(h, 4)
            var compressed = Int64(le32(h, 14)), uncompressed = Int64(le32(h, 18))
            let nameLen = le16(h, 22), extraLen = le16(h, 24)
            let name = String(decoding: try reader.read(nameLen), as: UTF8.self)
            let extra = try reader.read(extraLen)
            var zip64 = false
            var i = 0
            while i + 4 <= extra.count {
                let id = le16(extra, i), size = le16(extra, i + 2)
                if id == 0x0001 && size >= 16 {
                    zip64 = true
                    uncompressed = Int64(le32(extra, i + 4)) | Int64(le32(extra, i + 8)) << 32
                    compressed = Int64(le32(extra, i + 12)) | Int64(le32(extra, i + 16)) << 32
                }
                i += 4 + size
            }
            let hasDescriptor = flags & 0x08 != 0
            var consumed = false
            let readEntry: (([UInt8]) throws -> Void) throws -> Void = { sink in
                consumed = true
                try self.readData(method: method, compressed: compressed, descriptor: hasDescriptor, sink: sink)
            }
            try body(name, readEntry)
            if !consumed { try readData(method: method, compressed: compressed, descriptor: hasDescriptor, sink: { _ in }) }
            if hasDescriptor {
                var d = try reader.read(4)
                if d.count == 4 && le32(d, 0) == 0x0807_4b50 { d = try reader.read(4) } // optional signature, then CRC
                _ = try reader.read(zip64 ? 16 : 8)
            }
            _ = uncompressed
        }
    }

    private func readData(method: Int, compressed: Int64, descriptor: Bool, sink: ([UInt8]) throws -> Void) throws {
        switch method {
        case 0:
            guard !descriptor || compressed > 0 else { throw CryptoError.invalidFormat("stored entry without size") }
            var left = compressed
            while left > 0 {
                let b = try reader.readSome(Int(min(left, 65_536)))
                guard !b.isEmpty else { throw CryptoError.truncated }
                try sink(b)
                left -= Int64(b.count)
            }
        case 8:
            try Inflater(reader).run(sink)
        default:
            throw CryptoError.invalidFormat("zip method \(method)")
        }
    }
}

/// Writes a zip of stored (uncompressed) entries: photos and videos are already compressed. Sizes and CRC go in the
/// local header, so java.util.zip.ZipInputStream on Android reads it.
public final class ZipStreamWriter {
    private let out: ByteSink
    private var offset: Int64 = 0
    private var central: [UInt8] = []
    private var count = 0

    public init(_ out: ByteSink) { self.out = out }

    private func le16(_ v: Int) -> [UInt8] { [UInt8(v & 0xff), UInt8((v >> 8) & 0xff)] }
    private func le32(_ v: UInt32) -> [UInt8] { [UInt8(v & 0xff), UInt8((v >> 8) & 0xff), UInt8((v >> 16) & 0xff), UInt8(v >> 24)] }
    private func le64(_ v: Int64) -> [UInt8] { le32(UInt32(truncatingIfNeeded: v)) + le32(UInt32(truncatingIfNeeded: v >> 32)) }

    private func emit(_ b: [UInt8]) throws {
        try out.write(b)
        offset += Int64(b.count)
    }

    public func add(name: String, bytes: [UInt8]) throws {
        try add(name: name, size: Int64(bytes.count), crc: CRC32.checksum(bytes)) { write in try write(bytes) }
    }

    /// Adds an entry whose content is produced twice by `content`: once to compute the CRC, once to write it.
    public func add(name: String, content: (([UInt8]) throws -> Void) throws -> Void) throws {
        var crc = CRC32()
        var size: Int64 = 0
        try content { b in
            crc.update(b)
            size += Int64(b.count)
        }
        try add(name: name, size: size, crc: crc.value, write: content)
    }

    private func add(name: String, size: Int64, crc: UInt32, write: (([UInt8]) throws -> Void) throws -> Void) throws {
        let nameBytes = Array(name.utf8)
        let big = size >= 0xFFFF_FFFF || offset >= 0xFFFF_FFFF
        let extra: [UInt8] = big ? le16(0x0001) + le16(16) + le64(size) + le64(size) : []
        let headerOffset = offset
        let common: [UInt8] = le16(big ? 45 : 20) + le16(0x0800) + le16(0) + le16(0) + le16(0x21) + le32(crc) +
            le32(big ? 0xFFFF_FFFF : UInt32(size)) + le32(big ? 0xFFFF_FFFF : UInt32(size)) + le16(nameBytes.count)
        try emit(le32(0x0403_4b50) + common + le16(extra.count) + nameBytes + extra)
        var written: Int64 = 0
        try write { b in
            try self.emit(b)
            written += Int64(b.count)
        }
        guard written == size else { throw CryptoError.invalidFormat("entry \(name) changed while being written") }
        let cdExtra: [UInt8] = big ? le16(0x0001) + le16(24) + le64(size) + le64(size) + le64(headerOffset) : []
        central += le32(0x0201_4b50) + le16(big ? 45 : 20) + common + le16(cdExtra.count) + le16(0) + le16(0) + le16(0) +
            le32(0) + le32(big ? 0xFFFF_FFFF : UInt32(headerOffset)) + nameBytes + cdExtra
        count += 1
    }

    public func finish() throws {
        let cdOffset = offset
        try emit(central)
        let n = min(count, 0xFFFF)
        try emit(le32(0x0605_4b50) + le16(0) + le16(0) + le16(n) + le16(n) + le32(UInt32(truncatingIfNeeded: central.count)) +
            le32(UInt32(truncatingIfNeeded: min(cdOffset, 0xFFFF_FFFF))) + le16(0))
    }
}
