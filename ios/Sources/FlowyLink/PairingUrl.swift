import Foundation

struct PairingUrl: Equatable {
    var version: Int
    var port: Int
    var fingerprint: String
    var nonce: String
    var addresses: [String]
    var name: String
}

enum PairingUrlError: Error, Equatable {
    case invalid
}

func parsePairingUrl(_ raw: String) throws -> PairingUrl {
    guard raw.hasPrefix("flowy://pair?") else { throw PairingUrlError.invalid }
    let rest = String(raw.dropFirst("flowy://pair?".count))
    var fields: [String: String] = [:]
    for pair in rest.split(separator: "&") {
        guard let index = pair.firstIndex(of: "=") else { throw PairingUrlError.invalid }
        let key = String(pair[..<index])
        let value = percentDecode(String(pair[pair.index(after: index)...]))
        fields[key] = value
    }
    guard let version = Int(fields["v"] ?? ""),
          let port = Int(fields["p"] ?? ""),
          (1...65535).contains(port),
          let fingerprint = fields["fp"]?.lowercased(),
          fingerprint.count == 64,
          fingerprint.allSatisfy(\.isHexDigit),
          let nonce = fields["n"]?.lowercased(),
          (16...128).contains(nonce.count),
          nonce.allSatisfy(\.isHexDigit),
          let hosts = fields["h"],
          let name = fields["name"]
    else { throw PairingUrlError.invalid }
    let addresses = hosts.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
    if addresses.isEmpty { throw PairingUrlError.invalid }
    return PairingUrl(version: version, port: port, fingerprint: fingerprint, nonce: nonce, addresses: addresses, name: name)
}

private func percentDecode(_ value: String) -> String {
    var bytes: [UInt8] = []
    let raw = Array(value.utf8)
    var index = 0
    while index < raw.count {
        if raw[index] == UInt8(ascii: "%"), index + 2 < raw.count {
            let hex = String(bytes: raw[(index + 1)...(index + 2)], encoding: .utf8) ?? ""
            if let decoded = UInt8(hex, radix: 16) {
                bytes.append(decoded)
                index += 3
                continue
            }
        }
        bytes.append(raw[index])
        index += 1
    }
    return String(bytes: bytes, encoding: .utf8) ?? value
}
