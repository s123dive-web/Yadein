import Foundation

public enum CryptoError: Error, Equatable {
    /// Authentication failed: wrong key/password, or the data was changed, reordered or cut short.
    case authenticationFailed
    case invalidFormat(String)
    case truncated
}

/// AES-256-GCM with a 12-byte nonce and 16-byte tag. Output of `seal` is `ciphertext ‖ tag` (what javax.crypto
/// returns on Android). The iPhone app plugs in CryptoKit; `SoftwareAESGCM` exists so the formats can be tested on Linux.
public protocol AEAD: Sendable {
    func seal(_ plaintext: [UInt8], key: [UInt8], nonce: [UInt8], aad: [UInt8]) throws -> [UInt8]
    func open(_ sealed: [UInt8], key: [UInt8], nonce: [UInt8], aad: [UInt8]) throws -> [UInt8]
}

/// Straightforward AES-256-GCM (FIPS 197 + SP 800-38D) in Swift. Not constant-time: production code on iPhone uses
/// CryptoKit; this is the portable reference used by tests and Linux tooling.
public struct SoftwareAESGCM: AEAD {
    public init() {}

    private static let sbox: [UInt8] = {
        var s = [UInt8](repeating: 0, count: 256)
        var p: UInt8 = 1, q: UInt8 = 1
        repeat {
            p = p ^ (p << 1) ^ ((p & 0x80) != 0 ? 0x1B : 0)
            q ^= q << 1; q ^= q << 2; q ^= q << 4
            if q & 0x80 != 0 { q ^= 0x09 }
            let x = q ^ rotl8(q, 1) ^ rotl8(q, 2) ^ rotl8(q, 3) ^ rotl8(q, 4)
            s[Int(p)] = x ^ 0x63
        } while p != 1
        s[0] = 0x63
        return s
    }()

    private static func rotl8(_ x: UInt8, _ s: UInt8) -> UInt8 { (x << s) | (x >> (8 - s)) }
    private static func xtime(_ x: UInt8) -> UInt8 { (x << 1) ^ ((x & 0x80) != 0 ? 0x1B : 0) }

    private static func expandKey(_ key: [UInt8]) -> [UInt8] {
        precondition(key.count == 32, "AES-256 needs a 32-byte key")
        var w = key
        var rcon: UInt8 = 1
        var i = 32
        while w.count < 240 {
            var t = Array(w[(w.count - 4)...])
            if i % 32 == 0 {
                t = [sbox[Int(t[1])] ^ rcon, sbox[Int(t[2])], sbox[Int(t[3])], sbox[Int(t[0])]]
                rcon = xtime(rcon)
            } else if i % 32 == 16 {
                t = t.map { sbox[Int($0)] }
            }
            for j in 0..<4 { w.append(w[w.count - 32] ^ t[j]) }
            i += 4
        }
        return w
    }

    private static func encryptBlock(_ input: [UInt8], _ rk: [UInt8]) -> [UInt8] {
        var s = input
        for i in 0..<16 { s[i] ^= rk[i] }
        for round in 1...14 {
            for i in 0..<16 { s[i] = sbox[Int(s[i])] }
            // ShiftRows (column-major state)
            var t = s
            for c in 0..<4 { for r in 0..<4 { t[c * 4 + r] = s[((c + r) % 4) * 4 + r] } }
            s = t
            if round != 14 {
                for c in 0..<4 {
                    let a0 = s[c * 4], a1 = s[c * 4 + 1], a2 = s[c * 4 + 2], a3 = s[c * 4 + 3]
                    let all = a0 ^ a1 ^ a2 ^ a3
                    s[c * 4] = a0 ^ all ^ xtime(a0 ^ a1)
                    s[c * 4 + 1] = a1 ^ all ^ xtime(a1 ^ a2)
                    s[c * 4 + 2] = a2 ^ all ^ xtime(a2 ^ a3)
                    s[c * 4 + 3] = a3 ^ all ^ xtime(a3 ^ a0)
                }
            }
            for i in 0..<16 { s[i] ^= rk[round * 16 + i] }
        }
        return s
    }

    private static func gmul(_ x: (UInt64, UInt64), _ y: (UInt64, UInt64)) -> (UInt64, UInt64) {
        var z: (UInt64, UInt64) = (0, 0)
        var v = y
        for i in 0..<128 {
            let bit = i < 64 ? (x.0 >> (63 - UInt64(i))) & 1 : (x.1 >> (127 - UInt64(i))) & 1
            if bit == 1 { z = (z.0 ^ v.0, z.1 ^ v.1) }
            let lsb = v.1 & 1
            v = (v.0 >> 1, (v.1 >> 1) | (v.0 << 63))
            if lsb == 1 { v.0 ^= 0xE100_0000_0000_0000 }
        }
        return z
    }

    private static func block(_ b: ArraySlice<UInt8>) -> (UInt64, UInt64) {
        var p = [UInt8](b)
        p += [UInt8](repeating: 0, count: 16 - p.count)
        var hi: UInt64 = 0, lo: UInt64 = 0
        for i in 0..<8 { hi = hi << 8 | UInt64(p[i]); lo = lo << 8 | UInt64(p[8 + i]) }
        return (hi, lo)
    }

    private static func ghash(_ h: (UInt64, UInt64), _ aad: [UInt8], _ c: [UInt8]) -> (UInt64, UInt64) {
        var y: (UInt64, UInt64) = (0, 0)
        for data in [aad, c] {
            var i = 0
            while i < data.count {
                let b = block(data[i..<min(i + 16, data.count)])
                y = gmul((y.0 ^ b.0, y.1 ^ b.1), h)
                i += 16
            }
        }
        y = gmul((y.0 ^ UInt64(aad.count) * 8, y.1 ^ UInt64(c.count) * 8), h)
        return y
    }

    private static func ctr(_ data: [UInt8], _ rk: [UInt8], _ j0: [UInt8]) -> [UInt8] {
        var out = [UInt8](repeating: 0, count: data.count)
        var counter = j0
        var i = 0
        while i < data.count {
            var c = UInt32(counter[12]) << 24 | UInt32(counter[13]) << 16 | UInt32(counter[14]) << 8 | UInt32(counter[15])
            c &+= 1
            counter[12] = UInt8(c >> 24); counter[13] = UInt8((c >> 16) & 0xff); counter[14] = UInt8((c >> 8) & 0xff); counter[15] = UInt8(c & 0xff)
            let ks = encryptBlock(counter, rk)
            for j in 0..<min(16, data.count - i) { out[i + j] = data[i + j] ^ ks[j] }
            i += 16
        }
        return out
    }

    private static func tag(_ rk: [UInt8], _ j0: [UInt8], _ aad: [UInt8], _ c: [UInt8]) -> [UInt8] {
        let hb = encryptBlock([UInt8](repeating: 0, count: 16), rk)
        let s = ghash(block(hb[...]), aad, c)
        let e = encryptBlock(j0, rk)
        var t = [UInt8](repeating: 0, count: 16)
        for i in 0..<8 {
            t[i] = e[i] ^ UInt8((s.0 >> (56 - 8 * UInt64(i))) & 0xff)
            t[8 + i] = e[8 + i] ^ UInt8((s.1 >> (56 - 8 * UInt64(i))) & 0xff)
        }
        return t
    }

    public func seal(_ plaintext: [UInt8], key: [UInt8], nonce: [UInt8], aad: [UInt8]) throws -> [UInt8] {
        precondition(nonce.count == 12)
        let rk = Self.expandKey(key)
        let j0 = nonce + [0, 0, 0, 1]
        let c = Self.ctr(plaintext, rk, j0)
        return c + Self.tag(rk, j0, aad, c)
    }

    public func open(_ sealed: [UInt8], key: [UInt8], nonce: [UInt8], aad: [UInt8]) throws -> [UInt8] {
        guard sealed.count >= 16, nonce.count == 12 else { throw CryptoError.truncated }
        let rk = Self.expandKey(key)
        let j0 = nonce + [0, 0, 0, 1]
        let c = Array(sealed[..<(sealed.count - 16)])
        let expected = Self.tag(rk, j0, aad, c)
        var diff: UInt8 = 0
        for (a, b) in zip(expected, sealed[(sealed.count - 16)...]) { diff |= a ^ b }
        guard diff == 0 else { throw CryptoError.authenticationFailed }
        return Self.ctr(c, rk, j0)
    }
}
