import CommonCrypto
import Foundation
import Security

struct PairResult {
    var status: String
    var deviceId: String?
}

struct HostSession: Codable {
    var name: String
    var fingerprint: String
    var port: Int
    var addresses: [String]
    var accessToken: String
    var refreshToken: String
    var deviceId: String
    var wakeEnabled: Bool
}

private let sessionKey = "flowy-link.session"
private let cacheKey = "flowy-link.conversations"

func loadSession() -> HostSession? {
    guard let data = UserDefaults.standard.data(forKey: sessionKey) else { return nil }
    return try? JSONDecoder().decode(HostSession.self, from: data)
}

func saveSession(_ session: HostSession) {
    if let data = try? JSONEncoder().encode(session) {
        UserDefaults.standard.set(data, forKey: sessionKey)
    }
}

func clearSession() {
    UserDefaults.standard.removeObject(forKey: sessionKey)
}

func cachedConversationNames() -> [String] {
    UserDefaults.standard.stringArray(forKey: cacheKey) ?? []
}

func cacheConversationNames(_ names: [String]) {
    UserDefaults.standard.set(names, forKey: cacheKey)
}

func callHost(_ session: HostSession, method: String, path: String, body: Data? = nil) async throws -> (HostSession, Data) {
    var current = session
    var lastError: Error = LinkSessionError.message("没有可连接的地址")
    for address in current.addresses {
        do {
            let client = PinnedSession(expected: current.fingerprint)
            var response = try await client.send(session: current, address: address, method: method, path: path, body: body, stepUp: nil)
            if response.status == 401 {
                current = try await client.refresh(session: current, address: address)
                response = try await client.send(session: current, address: address, method: method, path: path, body: body, stepUp: nil)
            }
            if response.status == 403, let text = String(data: response.data, encoding: .utf8), text.contains("link step-up") {
                let grant = try await client.confirm(session: current, address: address, method: method, path: path)
                response = try await client.send(session: current, address: address, method: method, path: path, body: body, stepUp: grant)
            }
            guard (200..<300).contains(response.status) else {
                let text = String(data: response.data, encoding: .utf8) ?? ""
                throw LinkSessionError.message("电脑返回 \(response.status) \(text)")
            }
            return (current, response.data)
        } catch {
            lastError = error
        }
    }
    throw lastError
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
                guard let access = json?["accessToken"] as? String,
                      let refresh = json?["refreshToken"] as? String
                else {
                    throw LinkSessionError.message("登录没有生效")
                }
                let record = HostSession(
                    name: parsed.name,
                    fingerprint: parsed.fingerprint,
                    port: parsed.port,
                    addresses: parsed.addresses,
                    accessToken: access,
                    refreshToken: refresh,
                    deviceId: json?["deviceId"] as? String ?? "",
                    wakeEnabled: true
                )
                saveSession(record)
                var whoami = URLRequest(url: URL(string: "\(root)/api/link/whoami")!)
                whoami.setValue("Bearer \(access)", forHTTPHeaderField: "Authorization")
                whoami.timeoutInterval = 8
                let (_, whoResponse) = try await session.data(for: whoami)
                guard let whoHttp = whoResponse as? HTTPURLResponse, (200..<300).contains(whoHttp.statusCode) else {
                    throw LinkSessionError.message("登录没有生效")
                }
                return PairResult(status: "已连接 \(parsed.name)", deviceId: record.deviceId)
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

    func send(session: HostSession, address: String, method: String, path: String, body: Data?, stepUp: String?) async throws -> (status: Int, data: Data) {
        var request = URLRequest(url: URL(string: "https://\(address):\(session.port)\(path)")!)
        request.httpMethod = method
        request.setValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")
        if let stepUp {
            request.setValue(stepUp, forHTTPHeaderField: "x-flowy-step-up")
        }
        if method != "GET" {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = body ?? Data("{}".utf8)
        }
        request.timeoutInterval = 8
        let (data, response) = try await self.session.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        return (status, data)
    }

    func refresh(session: HostSession, address: String) async throws -> HostSession {
        let payload = try JSONSerialization.data(withJSONObject: ["refreshToken": session.refreshToken])
        var request = URLRequest(url: URL(string: "https://\(address):\(session.port)/api/link/session/refresh")!)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = payload
        request.timeoutInterval = 8
        let (data, response) = try await self.session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode),
              let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let access = json["accessToken"] as? String
        else {
            throw LinkSessionError.message("登录已失效，请重新配对")
        }
        var updated = session
        updated.accessToken = access
        saveSession(updated)
        return updated
    }

    func confirm(session: HostSession, address: String, method: String, path: String) async throws -> String {
        let payload = try JSONSerialization.data(withJSONObject: ["method": method, "path": path])
        let opened = try await send(session: session, address: address, method: "POST", path: "/api/link/step-up", body: payload, stepUp: nil)
        guard (200..<300).contains(opened.status),
              let json = try JSONSerialization.jsonObject(with: opened.data) as? [String: Any],
              let id = json["id"] as? String
        else {
            throw LinkSessionError.message("电脑没有接受这次确认")
        }
        for _ in 0..<40 {
            try await Task.sleep(nanoseconds: 1_500_000_000)
            let polled = try await send(session: session, address: address, method: "GET", path: "/api/link/step-up/status", body: nil, stepUp: id)
            let status = ((try? JSONSerialization.jsonObject(with: polled.data)) as? [String: Any])?["status"] as? String
            switch status {
            case "approved":
                return id
            case "denied", "expired", "missing":
                throw LinkSessionError.message("电脑没有允许这次操作")
            default:
                continue
            }
        }
        throw LinkSessionError.message("等待电脑确认超时")
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
