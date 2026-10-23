// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "FlowyLink",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "FlowyLink", targets: ["FlowyLink"]),
    ],
    targets: [
        .target(name: "FlowyLink"),
        .testTarget(name: "FlowyLinkTests", dependencies: ["FlowyLink"]),
    ]
)
