import CommonCrypto
import Foundation
import Security

struct PairResult {
    var status: String
    var deviceId: String?
}

enum LinkSessionError: Error, LocalizedError {
    case message(String)

    var errorDescription: String? {
        switch self {
        case .message(let text):
            return text
        }
    }
}

func pairWithHost(rawUrl: String) async throws -> PairResult {
    let parsed = try parsePairingUrl(rawUrl.trimmingCharacters(in: .whitespacesAndNewlines))
    let body: [String: String] = [
        "nonce": parsed.nonce,
        "displayName": ProcessInfo.processInfo.hostName,
        "platform": "ios",
        "publicKeyFingerprint": deviceFingerprint(),
        "appVersion": "0.1.0",
    ]
    let payload = try JSONSerialization.data(withJSONObject: body)
    let session = PinnedSession(expected: parsed.fingerprint)
    var lastError: Error = LinkSessionError.message("没有可连接的地址")
    for address in parsed.addresses {
        do {
            return try await session.finish(parsed: parsed, address: address, payload: payload)
        } catch {
            lastError = error
        }
    }
    throw lastError
}

private final class PinnedSession: NSObject, URLSessionDelegate {
    let expected: String
    private lazy var session: URLSession = {
        URLSession(configuration: .ephemeral, delegate: self, delegateQueue: nil)
    }()

    init(expected: String) {
        self.expected = expected.lowercased()
    }

    func finish(parsed: PairingUrl, address: String, payload: Data) async throws -> PairResult {
        let root = "https://\(address):\(parsed.port)"
        var present = URLRequest(url: URL(string: "\(root)/api/link/pair")!)
        present.httpMethod = "POST"
        present.setValue("application/json", forHTTPHeaderField: "Content-Type")
        present.httpBody = payload
        present.timeoutInterval = 8
        let (_, response) = try await session.data(for: present)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw LinkSessionError.message("电脑拒绝了配对请求")
        }
        for _ in 0..<40 {
            try await Task.sleep(nanoseconds: 1_500_000_000)
            var status = URLRequest(url: URL(string: "\(root)/api/link/pair/status")!)
            status.setValue(parsed.nonce, forHTTPHeaderField: "x-flowy-pair-nonce")
            status.timeoutInterval = 8
            let (data, statusResponse) = try await session.data(for: status)
            guard let http = statusResponse as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                throw LinkSessionError.message("读取配对状态失败")
            }
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            switch json?["status"] as? String {
            case "approved":
                guard let access = json?["accessToken"] as? String else {
                    throw LinkSessionError.message("登录没有生效")
                }
                var whoami = URLRequest(url: URL(string: "\(root)/api/link/whoami")!)
                whoami.setValue("Bearer \(access)", forHTTPHeaderField: "Authorization")
                whoami.timeoutInterval = 8
                let (_, whoResponse) = try await session.data(for: whoami)
                guard let whoHttp = whoResponse as? HTTPURLResponse, (200..<300).contains(whoHttp.statusCode) else {
                    throw LinkSessionError.message("登录没有生效")
                }
                return PairResult(status: "已连接 \(parsed.name)", deviceId: json?["deviceId"] as? String)
            case "denied":
                throw LinkSessionError.message("这台电脑拒绝了这台手机")
            case "expired", "consumed":
                throw LinkSessionError.message("配对码已失效，请在电脑上重新生成")
            default:
                continue
            }
        }
        throw LinkSessionError.message("还在等待电脑上的允许")
    }

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        guard let trust = challenge.protectionSpace.serverTrust,
              let chain = SecTrustCopyCertificateChain(trust) as? [SecCertificate],
              let leaf = chain.first
        else {
            completionHandler(.cancelAuthenticationChallenge, nil)
            return
        }
        let data = SecCertificateCopyData(leaf) as Data
        if sha256(data) == expected {
            completionHandler(.useCredential, URLCredential(trust: trust))
        } else {
            completionHandler(.cancelAuthenticationChallenge, nil)
        }
    }
}

private func sha256(_ data: Data) -> String {
    var hash = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
    data.withUnsafeBytes { raw in
        _ = CC_SHA256(raw.baseAddress, CC_LONG(data.count), &hash)
    }
    return hash.map { String(format: "%02x", $0) }.joined()
}

private func deviceFingerprint() -> String {
    let key = "flowy-link.fingerprint"
    if let existing = UserDefaults.standard.string(forKey: key) {
        return existing
    }
    var bytes = [UInt8](repeating: 0, count: 32)
    _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
    let fingerprint = bytes.map { String(format: "%02x", $0) }.joined()
    UserDefaults.standard.set(fingerprint, forKey: key)
    return fingerprint
}
