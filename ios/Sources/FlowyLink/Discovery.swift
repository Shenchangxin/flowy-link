import Foundation
import Network

private final class ResumeOnce {
    private let lock = NSLock()
    private var used = false

    func take() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        if used { return false }
        used = true
        return true
    }
}

func mergeDiscoveredAddress(_ session: HostSession) async -> HostSession {
    guard let found = await discoverAddress(matching: session.fingerprint), !session.addresses.contains(found) else {
        return session
    }
    var updated = session
    updated.addresses.insert(found, at: 0)
    saveSession(updated)
    return updated
}

private func discoverAddress(matching expected: String) async -> String? {
    let browser = NWBrowser(for: .bonjour(type: "_flowy-link._tcp", domain: nil), using: .tcp)
    return await withCheckedContinuation { continuation in
        let gate = ResumeOnce()
        browser.browseResultsChangedHandler = { results, _ in
            for result in results {
                guard case let .bonjour(record) = result.metadata,
                      record["fp"]?.lowercased() == expected.lowercased() else { continue }
                let connection = NWConnection(to: result.endpoint, using: .tcp)
                connection.stateUpdateHandler = { state in
                    guard case .ready = state,
                          case let .hostPort(host, _) = connection.currentPath?.remoteEndpoint,
                          let address = hostAddress(host),
                          gate.take() else { return }
                    connection.cancel()
                    browser.cancel()
                    continuation.resume(returning: address)
                }
                connection.start(queue: .global())
            }
        }
        browser.stateUpdateHandler = { state in
            if case .failed = state, gate.take() {
                browser.cancel()
                continuation.resume(returning: nil)
            }
        }
        browser.start(queue: .global())
        DispatchQueue.global().asyncAfter(deadline: .now() + 3) {
            if gate.take() {
                browser.cancel()
                continuation.resume(returning: nil)
            }
        }
    }
}

private func hostAddress(_ host: NWEndpoint.Host) -> String? {
    switch host {
    case let .ipv4(address):
        return "\(address)"
    case let .name(name, _):
        return name
    default:
        return nil
    }
}
