import XCTest
@testable import FlowyLink

final class PairingUrlTests: XCTestCase {
    func testParsesTheSharedVector() throws {
        let fingerprint = String(repeating: "ab", count: 32)
        let nonce = String(repeating: "cd", count: 16)
        let url = "flowy://pair?v=1&p=25818&fp=\(fingerprint)&n=\(nonce)&h=192.168.1.20,10.0.0.8&name=Study"
        let parsed = try parsePairingUrl(url)
        XCTAssertEqual(parsed.port, 25818)
        XCTAssertEqual(parsed.addresses, ["192.168.1.20", "10.0.0.8"])
        XCTAssertEqual(parsed.name, "Study")
        XCTAssertEqual(parsed.fingerprint, fingerprint)
    }

    func testRejectsAShortFingerprint() {
        let url = "flowy://pair?v=1&p=1&fp=abcd&n=00112233445566778899aabbccddeeff&h=127.0.0.1&name=a"
        XCTAssertThrowsError(try parsePairingUrl(url))
    }
}
