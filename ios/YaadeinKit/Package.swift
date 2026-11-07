// swift-tools-version:5.9
// Pure Swift ports of Android's core:domain (and the platform-independent crypto primitives). No UIKit, no network,
// no third-party dependencies: it builds and tests on Linux as well as macOS, so the logic is checked without a Mac.
import PackageDescription

let package = Package(
    name: "YaadeinKit",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "YaadeinDomain", targets: ["YaadeinDomain"]),
        .library(name: "YaadeinCrypto", targets: ["YaadeinCrypto"]),
    ],
    targets: [
        .target(name: "YaadeinDomain"),
        .target(name: "YaadeinCrypto"),
        .testTarget(name: "YaadeinDomainTests", dependencies: ["YaadeinDomain"]),
        .testTarget(name: "YaadeinCryptoTests", dependencies: ["YaadeinCrypto"]),
    ],
    swiftLanguageVersions: [.v5]
)
