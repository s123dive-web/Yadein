import Foundation

/// A detected face: a compact embedding plus where it was found (FaceClustering.kt).
public struct FaceSample: Sendable {
    public let mediaId: Int64
    public let embedding: [Float]
    public let left: Float, top: Float, right: Float, bottom: Float

    public init(mediaId: Int64, embedding: [Float], left: Float, top: Float, right: Float, bottom: Float) {
        self.mediaId = mediaId
        self.embedding = embedding
        self.left = left
        self.top = top
        self.right = right
        self.bottom = bottom
    }
}

public struct FaceCluster: Hashable, Codable, Identifiable, Sendable {
    public var id: Int64
    public var centroid: [Float]
    public var count: Int
    public var sampleMediaId: Int64
    public var sampleBox: [Float]
    public var suggestedPersonId: Int64?
    public var confirmed = false

    public init(id: Int64, centroid: [Float], count: Int, sampleMediaId: Int64, sampleBox: [Float]) {
        self.id = id
        self.centroid = centroid
        self.count = count
        self.sampleMediaId = sampleMediaId
        self.sampleBox = sampleBox
    }
}

/// Incremental, deterministic nearest-centroid clustering (cosine distance). The user confirms every suggestion.
public enum FaceClustering {
    public static let defaultThreshold: Float = 0.32

    public static func cosineDistance(_ a: [Float], _ b: [Float]) -> Float {
        // Embeddings from an older descriptor have another length: never similar.
        if a.count != b.count { return 1 }
        var dot: Float = 0, na: Float = 0, nb: Float = 0
        for i in a.indices {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if na == 0 || nb == 0 { return 1 }
        return 1 - dot / (na.squareRoot() * nb.squareRoot())
    }

    /// Returns the updated clusters and the cluster the sample joined; a new cluster gets id = nextId.
    public static func assign(_ sample: FaceSample, clusters: [FaceCluster], nextId: Int64,
                              threshold: Float = defaultThreshold) -> ([FaceCluster], Int64) {
        var best: FaceCluster?
        var bestDistance = Float.greatestFiniteMagnitude
        for c in clusters {
            let d = cosineDistance(c.centroid, sample.embedding)
            if d < bestDistance {
                bestDistance = d
                best = c
            }
        }
        if let best, bestDistance <= threshold {
            let n = best.count + 1
            var updated = best
            updated.centroid = best.centroid.indices.map { (best.centroid[$0] * Float(best.count) + sample.embedding[$0]) / Float(n) }
            updated.count = n
            return (clusters.map { $0.id == best.id ? updated : $0 }, best.id)
        }
        let fresh = FaceCluster(id: nextId, centroid: sample.embedding, count: 1, sampleMediaId: sample.mediaId,
                                sampleBox: [sample.left, sample.top, sample.right, sample.bottom])
        return (clusters + [fresh], nextId)
    }

    /// L2-normalise so cosine distance is stable across lighting.
    public static func normalise(_ v: [Float]) -> [Float] {
        let n = v.reduce(0) { $0 + $1 * $1 }
        let s = n == 0 ? 1 : n.squareRoot()
        return v.map { $0 / s }
    }
}

/// Face descriptor (FaceFeatures.kt): uniform LBP (8,1) on a histogram-equalised 64×64 grey crop, pooled over 4×4.
public enum FaceFeatures {
    public static let size = 64
    public static let grid = 4
    public static let bins = 59
    public static let dimensions = grid * grid * bins
    /// Cosine distance below which two faces join the same suggestion.
    public static let threshold: Float = 0.24

    private static let uniformIndex: [Int] = {
        var table = [Int](repeating: 58, count: 256)
        var next = 0
        for p in 0..<256 {
            var transitions = 0
            for i in 0..<8 where ((p >> i) & 1) != ((p >> ((i + 1) % 8)) & 1) { transitions += 1 }
            if transitions <= 2 {
                table[p] = next
                next += 1
            }
        }
        return table
    }()

    /// `grey`: size×size values 0...255, row-major.
    public static func embed(_ grey: [Int]) -> [Float] {
        precondition(grey.count == size * size)
        let eq = equalise(grey)
        var hist = [Float](repeating: 0, count: dimensions)
        let cell = size / grid
        let dx = [-1, 0, 1, 1, 1, 0, -1, -1], dy = [-1, -1, -1, 0, 1, 1, 1, 0]
        for y in 1..<(size - 1) {
            for x in 1..<(size - 1) {
                let c = eq[y * size + x]
                var code = 0
                for k in 0..<8 where eq[(y + dy[k]) * size + x + dx[k]] >= c { code |= 1 << k }
                let cellIndex = min(y / cell, grid - 1) * grid + min(x / cell, grid - 1)
                hist[cellIndex * bins + uniformIndex[code]] += 1
            }
        }
        return FaceClustering.normalise(hist.map { $0.squareRoot() })
    }

    /// Histogram equalisation: spreads the grey levels so dark and bright photos look alike.
    public static func equalise(_ grey: [Int]) -> [Int] {
        var counts = [Int](repeating: 0, count: 256)
        for g in grey { counts[min(max(g, 0), 255)] += 1 }
        var cdf = [Int](repeating: 0, count: 256)
        var run = 0
        for i in 0..<256 {
            run += counts[i]
            cdf[i] = run
        }
        let lo = cdf.first { $0 > 0 } ?? 0
        let range = max(grey.count - lo, 1)
        return grey.map { Int(Int64(cdf[min(max($0, 0), 255)] - lo) * 255 / Int64(range)) }
    }
}
