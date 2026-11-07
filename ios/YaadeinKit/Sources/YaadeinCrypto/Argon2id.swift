import Foundation

/// BLAKE2b (RFC 7693), unkeyed, any output length 1...64. Used by Argon2id.
public struct Blake2b {
    private static let iv: [UInt64] = [
        0x6a09e667f3bcc908, 0xbb67ae8584caa73b, 0x3c6ef372fe94f82b, 0xa54ff53a5f1d36f1,
        0x510e527fade682d1, 0x9b05688c2b3e6c1f, 0x1f83d9abfb41bd6b, 0x5be0cd19137e2179,
    ]
    private static let sigma: [[Int]] = [
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15], [14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3],
        [11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4], [7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8],
        [9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13], [2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9],
        [12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11], [13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10],
        [6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5], [10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0],
    ]

    private var h: [UInt64]
    private var buffer = [UInt8]()
    private var counter: UInt64 = 0
    private let outLength: Int

    public init(outputLength: Int) {
        precondition((1...64).contains(outputLength))
        outLength = outputLength
        h = Blake2b.iv
        h[0] ^= 0x0101_0000 ^ UInt64(outputLength)
    }

    public mutating func update(_ bytes: [UInt8]) {
        for b in bytes {
            if buffer.count == 128 {
                counter &+= 128
                compress(block: buffer, last: false)
                buffer.removeAll(keepingCapacity: true)
            }
            buffer.append(b)
        }
    }

    public mutating func update(le32 value: UInt32) {
        update([UInt8(value & 0xff), UInt8((value >> 8) & 0xff), UInt8((value >> 16) & 0xff), UInt8(value >> 24)])
    }

    public mutating func finalize() -> [UInt8] {
        counter &+= UInt64(buffer.count)
        let block = buffer + [UInt8](repeating: 0, count: 128 - buffer.count)
        compress(block: block, last: true)
        var out = [UInt8]()
        out.reserveCapacity(64)
        for word in h { for i in 0..<8 { out.append(UInt8((word >> (8 * UInt64(i))) & 0xff)) } }
        return Array(out.prefix(outLength))
    }

    public static func hash(_ bytes: [UInt8], outputLength: Int) -> [UInt8] {
        var b = Blake2b(outputLength: outputLength)
        b.update(bytes)
        return b.finalize()
    }

    @inline(__always) private static func rotr(_ x: UInt64, _ n: UInt64) -> UInt64 { (x >> n) | (x << (64 - n)) }

    private mutating func compress(block: [UInt8], last: Bool) {
        var m = [UInt64](repeating: 0, count: 16)
        for i in 0..<16 {
            var w: UInt64 = 0
            for j in 0..<8 { w |= UInt64(block[i * 8 + j]) << (8 * UInt64(j)) }
            m[i] = w
        }
        var v = h + Blake2b.iv
        v[12] ^= counter
        if last { v[14] = ~v[14] }
        func g(_ a: Int, _ b: Int, _ c: Int, _ d: Int, _ x: UInt64, _ y: UInt64) {
            v[a] = v[a] &+ v[b] &+ x
            v[d] = Blake2b.rotr(v[d] ^ v[a], 32)
            v[c] = v[c] &+ v[d]
            v[b] = Blake2b.rotr(v[b] ^ v[c], 24)
            v[a] = v[a] &+ v[b] &+ y
            v[d] = Blake2b.rotr(v[d] ^ v[a], 16)
            v[c] = v[c] &+ v[d]
            v[b] = Blake2b.rotr(v[b] ^ v[c], 63)
        }
        for r in 0..<12 {
            let s = Blake2b.sigma[r % 10]
            g(0, 4, 8, 12, m[s[0]], m[s[1]])
            g(1, 5, 9, 13, m[s[2]], m[s[3]])
            g(2, 6, 10, 14, m[s[4]], m[s[5]])
            g(3, 7, 11, 15, m[s[6]], m[s[7]])
            g(0, 5, 10, 15, m[s[8]], m[s[9]])
            g(1, 6, 11, 12, m[s[10]], m[s[11]])
            g(2, 7, 8, 13, m[s[12]], m[s[13]])
            g(3, 4, 9, 14, m[s[14]], m[s[15]])
        }
        for i in 0..<8 { h[i] ^= v[i] ^ v[i + 8] }
    }
}

/// Argon2id v1.3 (RFC 9106), pure Swift and dependency free. Android derives the PIN and backup keys with argon2kt
/// (the reference C implementation) using `Argon2id.yaadein` parameters; this produces identical bytes, so a backup
/// password typed on an iPhone opens a backup made on Android and the other way round.
public enum Argon2id {
    public struct Parameters: Sendable {
        public let iterations: Int
        public let memoryKiB: Int
        public let parallelism: Int
        public let length: Int

        public init(iterations: Int, memoryKiB: Int, parallelism: Int, length: Int) {
            self.iterations = iterations
            self.memoryKiB = memoryKiB
            self.parallelism = parallelism
            self.length = length
        }
    }

    /// What Android uses for PINs and backups: t = 3, m = 64 MiB, p = 2, 32-byte key.
    public static let yaadein = Parameters(iterations: 3, memoryKiB: 65_536, parallelism: 2, length: 32)

    private static let blockWords = 128
    private static let syncPoints = 4

    public static func hash(password: [UInt8], salt: [UInt8], parameters p: Parameters = yaadein) -> [UInt8] {
        let lanes = p.parallelism
        let memoryBlocks = max(p.memoryKiB, 8 * lanes) / (syncPoints * lanes) * (syncPoints * lanes)
        let laneLength = memoryBlocks / lanes
        let segmentLength = laneLength / syncPoints

        // H0
        var h0 = Blake2b(outputLength: 64)
        for v in [lanes, p.length, p.memoryKiB, p.iterations, 0x13, 2] { h0.update(le32: UInt32(v)) }
        h0.update(le32: UInt32(password.count)); h0.update(password)
        h0.update(le32: UInt32(salt.count)); h0.update(salt)
        h0.update(le32: 0) // secret
        h0.update(le32: 0) // associated data
        let seed = h0.finalize()

        let memory = UnsafeMutablePointer<UInt64>.allocate(capacity: memoryBlocks * blockWords)
        memory.initialize(repeating: 0, count: memoryBlocks * blockWords)
        defer {
            memory.initialize(repeating: 0, count: memoryBlocks * blockWords) // wipe
            memory.deallocate()
        }

        for lane in 0..<lanes {
            for j in 0..<2 {
                let bytes = hPrime(seed + le32(UInt32(j)) + le32(UInt32(lane)), length: 1024)
                load(bytes, into: memory + (lane * laneLength + j) * blockWords)
            }
        }

        let zero = [UInt64](repeating: 0, count: blockWords)
        var inputBlock = [UInt64](repeating: 0, count: blockWords)
        var addressBlock = [UInt64](repeating: 0, count: blockWords)
        var scratch = [UInt64](repeating: 0, count: blockWords * 2)

        for pass in 0..<p.iterations {
            for slice in 0..<syncPoints {
                for lane in 0..<lanes {
                    let dataIndependent = pass == 0 && slice < syncPoints / 2
                    var startIndex = 0
                    if dataIndependent {
                        for i in 0..<blockWords { inputBlock[i] = 0 }
                        inputBlock[0] = UInt64(pass)
                        inputBlock[1] = UInt64(lane)
                        inputBlock[2] = UInt64(slice)
                        inputBlock[3] = UInt64(memoryBlocks)
                        inputBlock[4] = UInt64(p.iterations)
                        inputBlock[5] = 2 // Argon2id
                    }
                    if pass == 0 && slice == 0 {
                        startIndex = 2
                        if dataIndependent { nextAddresses(&inputBlock, &addressBlock, zero, &scratch) }
                    }
                    var current = lane * laneLength + slice * segmentLength + startIndex
                    var previous = current % laneLength == 0 ? current + laneLength - 1 : current - 1
                    for index in startIndex..<segmentLength {
                        if current % laneLength == 1 { previous = current - 1 }
                        let pseudoRandom: UInt64
                        if dataIndependent {
                            if index % blockWords == 0 { nextAddresses(&inputBlock, &addressBlock, zero, &scratch) }
                            pseudoRandom = addressBlock[index % blockWords]
                        } else {
                            pseudoRandom = memory[previous * blockWords]
                        }
                        var refLane = Int((pseudoRandom >> 32) % UInt64(lanes))
                        if pass == 0 && slice == 0 { refLane = lane }
                        let sameLane = refLane == lane
                        var area: Int
                        if pass == 0 {
                            area = sameLane ? slice * segmentLength + index - 1 : slice * segmentLength + (index == 0 ? -1 : 0)
                        } else {
                            area = sameLane ? laneLength - segmentLength + index - 1 : laneLength - segmentLength + (index == 0 ? -1 : 0)
                        }
                        let j1 = pseudoRandom & 0xffff_ffff
                        var rel = (j1 &* j1) >> 32
                        rel = UInt64(area - 1) &- ((UInt64(area) &* rel) >> 32)
                        let start = (pass != 0 && slice != syncPoints - 1) ? (slice + 1) * segmentLength : 0
                        let refIndex = (start + Int(rel)) % laneLength
                        let refBlock = refLane * laneLength + refIndex
                        fill(memory + previous * blockWords, memory + refBlock * blockWords, memory + current * blockWords,
                             withXor: pass != 0, &scratch)
                        previous = current
                        current += 1
                    }
                }
            }
        }

        var final = [UInt64](repeating: 0, count: blockWords)
        for lane in 0..<lanes {
            let last = memory + (lane * laneLength + laneLength - 1) * blockWords
            for i in 0..<blockWords { final[i] ^= last[i] }
        }
        var bytes = [UInt8]()
        bytes.reserveCapacity(1024)
        for w in final { for i in 0..<8 { bytes.append(UInt8((w >> (8 * UInt64(i))) & 0xff)) } }
        return hPrime(bytes, length: p.length)
    }

    public static func hash(password: String, salt: [UInt8], parameters: Parameters = yaadein) -> [UInt8] {
        hash(password: Array(password.utf8), salt: salt, parameters: parameters)
    }

    private static func le32(_ v: UInt32) -> [UInt8] { [UInt8(v & 0xff), UInt8((v >> 8) & 0xff), UInt8((v >> 16) & 0xff), UInt8(v >> 24)] }

    private static func load(_ bytes: [UInt8], into block: UnsafeMutablePointer<UInt64>) {
        for i in 0..<blockWords {
            var w: UInt64 = 0
            for j in 0..<8 { w |= UInt64(bytes[i * 8 + j]) << (8 * UInt64(j)) }
            block[i] = w
        }
    }

    /// Variable-length hash H' from RFC 9106 §3.3.
    static func hPrime(_ input: [UInt8], length: Int) -> [UInt8] {
        if length <= 64 { return Blake2b.hash(le32(UInt32(length)) + input, outputLength: length) }
        var out = [UInt8]()
        var v = Blake2b.hash(le32(UInt32(length)) + input, outputLength: 64)
        out += v.prefix(32)
        let r = (length + 31) / 32 - 2
        if r >= 2 {
            for _ in 2...r {
                v = Blake2b.hash(v, outputLength: 64)
                out += v.prefix(32)
            }
        }
        out += Blake2b.hash(v, outputLength: length - 32 * r)
        return out
    }

    private static func nextAddresses(_ input: inout [UInt64], _ address: inout [UInt64], _ zero: [UInt64], _ scratch: inout [UInt64]) {
        input[6] &+= 1
        zero.withUnsafeBufferPointer { z in
            input.withUnsafeMutableBufferPointer { inp in
                address.withUnsafeMutableBufferPointer { addr in
                    let zp = UnsafeMutablePointer(mutating: z.baseAddress!)
                    fill(zp, inp.baseAddress!, addr.baseAddress!, withXor: false, &scratch)
                    fill(zp, addr.baseAddress!, addr.baseAddress!, withXor: false, &scratch)
                }
            }
        }
    }

    @inline(__always) private static func blaMka(_ x: UInt64, _ y: UInt64) -> UInt64 {
        let lo: UInt64 = (x & 0xffff_ffff) &* (y & 0xffff_ffff)
        let sum: UInt64 = x &+ y
        return sum &+ (lo &<< 1)
    }

    @inline(__always) private static func rotr(_ x: UInt64, _ n: UInt64) -> UInt64 { (x >> n) | (x << (64 - n)) }

    @inline(__always) private static func gb(_ v: UnsafeMutablePointer<UInt64>, _ a: Int, _ b: Int, _ c: Int, _ d: Int) {
        v[a] = blaMka(v[a], v[b]); v[d] = rotr(v[d] ^ v[a], 32)
        v[c] = blaMka(v[c], v[d]); v[b] = rotr(v[b] ^ v[c], 24)
        v[a] = blaMka(v[a], v[b]); v[d] = rotr(v[d] ^ v[a], 16)
        v[c] = blaMka(v[c], v[d]); v[b] = rotr(v[b] ^ v[c], 63)
    }

    @inline(__always) private static func round(_ v: UnsafeMutablePointer<UInt64>, _ i: [Int]) {
        gb(v, i[0], i[4], i[8], i[12]); gb(v, i[1], i[5], i[9], i[13])
        gb(v, i[2], i[6], i[10], i[14]); gb(v, i[3], i[7], i[11], i[15])
        gb(v, i[0], i[5], i[10], i[15]); gb(v, i[1], i[6], i[11], i[12])
        gb(v, i[2], i[7], i[8], i[13]); gb(v, i[3], i[4], i[9], i[14])
    }

    private static let rowIndices: [[Int]] = (0..<8).map { r in (0..<16).map { 16 * r + $0 } }
    private static let columnIndices: [[Int]] = (0..<8).map { (c: Int) -> [Int] in
        var list: [Int] = []
        for k in 0..<8 {
            let base: Int = 2 * c + 16 * k
            list.append(base)
            list.append(base + 1)
        }
        return list
    }

    /// The compression function G of RFC 9106 §3.5 (`fill_block` in the reference code).
    private static func fill(_ prev: UnsafeMutablePointer<UInt64>, _ ref: UnsafeMutablePointer<UInt64>,
                             _ next: UnsafeMutablePointer<UInt64>, withXor: Bool, _ scratch: inout [UInt64]) {
        scratch.withUnsafeMutableBufferPointer { s in
            let r = s.baseAddress!
            let tmp = r + blockWords
            for i in 0..<blockWords {
                r[i] = ref[i] ^ prev[i]
                tmp[i] = withXor ? r[i] ^ next[i] : r[i]
            }
            for row in rowIndices { round(r, row) }
            for column in columnIndices { round(r, column) }
            for i in 0..<blockWords { next[i] = tmp[i] ^ r[i] }
        }
    }
}
