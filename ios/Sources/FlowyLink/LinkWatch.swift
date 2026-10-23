import Foundation
#if os(iOS)
import UIKit
import UserNotifications
#endif

final class LinkWatch {
    private let session: PinnedSession
    private let host: HostSession
    private let conversationId: String
    private var task: URLSessionWebSocketTask?
    private var pieces: [String: String] = [:]

    init(host: HostSession, conversationId: String) {
        self.host = host
        self.conversationId = conversationId
        self.session = PinnedSession(expected: host.fingerprint)
    }

    func start(onText: @escaping (String) -> Void, onPhase: @escaping (String) -> Void) {
        guard let address = host.addresses.first,
              let url = URL(string: "wss://\(address):\(host.port)/ws") else { return }
        var request = URLRequest(url: url)
        request.setValue("Bearer \(host.accessToken)", forHTTPHeaderField: "Authorization")
        let socket = session.socket(for: request)
        task = socket
        receive(socket, onText: onText, onPhase: onPhase)
        socket.resume()
    }

#if os(iOS)
    private func showStatusIfBackground(_ phase: String) {
        guard UIApplication.shared.applicationState != .active else { return }
        let text: String
        switch phase {
        case "confirmation.pending":
            text = "有一项操作需要你确认"
        case "turn.failed":
            text = "有一条回复没有完成"
        default:
            text = "有一条回复已经完成"
        }
        let content = UNMutableNotificationContent()
        content.title = "电脑上的 Flowy"
        content.body = text
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil))
    }
#endif

    func stop() {
        task?.cancel(with: .normalClosure, reason: nil)
    }

    private func receive(_ socket: URLSessionWebSocketTask, onText: @escaping (String) -> Void, onPhase: @escaping (String) -> Void) {
        socket.receive { [weak self] result in
            guard let self else { return }
            guard case let .success(message) = result else { return }
            defer { self.receive(socket, onText: onText, onPhase: onPhase) }
            guard case let .string(text) = message,
                  let data = text.data(using: .utf8),
                  let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let payload = root["data"] as? [String: Any],
                  payload["conversation_id"] as? String == self.conversationId else { return }
            let name = root["name"] as? String
            if name == "message.stream", payload["type"] as? String == "content" {
                let id = payload["msg_id"] as? String ?? "live"
                var buffer = self.pieces[id] ?? ""
                if payload["replace"] as? Bool == true { buffer = "" }
                buffer += displayText(payload["data"])
                self.pieces[id] = buffer
                let latest = buffer
                DispatchQueue.main.async { onText(latest) }
            } else if name == "turn.completed" || name == "turn.failed" {
                let waiting = (payload["runtime"] as? [String: Any])?["state"] as? String == "waiting_confirmation"
                let phase = waiting ? "confirmation.pending" : (name == "turn.failed" ? "turn.failed" : "turn.completed")
                DispatchQueue.main.async { [weak self] in
                    onPhase(phase)
                    #if os(iOS)
                    self?.showStatusIfBackground(phase)
                    #endif
                }
            }
        }
    }
}
