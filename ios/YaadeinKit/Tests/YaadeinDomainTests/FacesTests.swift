import XCTest
@testable import YaadeinDomain

/// Mirrors FaceClusteringTest.kt and FaceFeaturesTest.kt.
final class FacesTests: XCTestCase {
    func sample(_ id: Int64, _ v: Float...) -> FaceSample {
        FaceSample(mediaId: id, embedding: FaceClustering.normalise(v), left: 0, top: 0, right: 1, bottom: 1)
    }

    func testSimilarFacesJoinOneClusterDifferentFacesStartAnother() {
        var clusters: [FaceCluster] = []
        var next: Int64 = 1
        let (c1, id1) = FaceClustering.assign(sample(1, 1, 0.1, 0), clusters: clusters, nextId: next); clusters = c1; next = max(next, id1 + 1)
        let (c2, id2) = FaceClustering.assign(sample(2, 0.95, 0.15, 0.02), clusters: clusters, nextId: next); clusters = c2; next = max(next, id2 + 1)
        let (c3, id3) = FaceClustering.assign(sample(3, 0, 0, 1), clusters: clusters, nextId: next); clusters = c3
        XCTAssertEqual(id1, id2)
        XCTAssertNotEqual(id3, id1)
        XCTAssertEqual(2, clusters.count)
        XCTAssertEqual(2, clusters.first { $0.id == id1 }?.count)
    }

    func testCosineDistance() {
        XCTAssertEqual(0, FaceClustering.cosineDistance([1, 2], [2, 4]), accuracy: 1e-5)
        XCTAssertEqual(1, FaceClustering.cosineDistance([1, 0], [0, 1]), accuracy: 1e-5)
        XCTAssertEqual(1, FaceClustering.cosineDistance([Float](repeating: 1, count: 10), [Float](repeating: 1, count: 12)))
    }

    let n = FaceFeatures.size
    func face(_ seed: Int, gain: Double = 1, offset: Int = 0) -> [Int] {
        (0..<(n * n)).map { i in
            let x = Double(i % n), y = Double(i / n)
            let v = 120 + 60 * sin((x + Double(seed * 3)) / (4.0 + Double(seed % 3))) * sin((y + Double(seed * 5)) / (5.0 + Double(seed % 4)))
            return min(max(Int(v * gain) + offset, 0), 255)
        }
    }

    func testDescriptorSizeAndUnitLength() {
        let e = FaceFeatures.embed(face(1))
        XCTAssertEqual(FaceFeatures.dimensions, e.count)
        XCTAssertEqual(1, e.reduce(0) { $0 + $1 * $1 }, accuracy: 1e-3)
    }

    func testLightingStaysWithinThresholdAndDifferentFacesAreFurther() {
        let base = FaceFeatures.embed(face(2))
        XCTAssertLessThan(FaceClustering.cosineDistance(base, FaceFeatures.embed(face(2, gain: 0.6, offset: -10))), FaceFeatures.threshold)
        XCTAssertLessThan(FaceClustering.cosineDistance(base, FaceFeatures.embed(face(2, gain: 1.2, offset: 25))), FaceFeatures.threshold)
        XCTAssertGreaterThan(FaceClustering.cosineDistance(base, FaceFeatures.embed(face(7))),
                             FaceClustering.cosineDistance(base, FaceFeatures.embed(face(2, gain: 0.6))))
    }

    func testEqualisation() {
        let eq = FaceFeatures.equalise((0..<100).map { 100 + $0 % 10 })
        XCTAssertEqual(0, eq.min())
        XCTAssertGreaterThanOrEqual(eq.max()!, 229)
    }
}
