import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

struct LinkRootView: View {
    @State private var session = loadSession()

    var body: some View {
        if let current = session {
            HomeView(session: current) {
                clearSession()
                session = nil
            }
        } else {
            DisconnectedView {
                session = loadSession()
            }
        }
    }
}

struct HomeView: View {
    @State var session: HostSession
    var onDisconnect: () -> Void
    @State private var offline = false
    @State private var tab = "任务"

    var body: some View {
        VStack(spacing: 0) {
            if wide {
                HStack(alignment: .top, spacing: 0) {
                    TaskPane(session: $session, offline: offline)
                    LibraryPane(session: session, offline: offline)
                    SettingsPane(session: $session, offline: offline, onDisconnect: onDisconnect)
                        .frame(maxWidth: 320)
                }
            } else {
                Group {
                    switch tab {
                    case "库":
                        LibraryPane(session: session, offline: offline)
                    case "设置":
                        SettingsPane(session: $session, offline: offline, onDisconnect: onDisconnect)
                    default:
                        TaskPane(session: $session, offline: offline)
                    }
                }
                HStack {
                    ForEach(["任务", "库", "设置"], id: \.self) { item in
                        Button(item) { tab = item }
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .foregroundStyle(tab == item ? LinkTokens.attention : .primary)
                    }
                }
            }
        }
        .task {
            do {
                let (updated, _) = try await callHost(session, method: "GET", path: "/api/link/whoami")
                session = updated
                saveSession(updated)
            } catch {
                offline = true
            }
        }
    }

    private var wide: Bool {
        #if os(iOS)
        UIScreen.main.bounds.width > 600
        #else
        false
        #endif
    }
}

private struct TaskPane: View {
    @Binding var session: HostSession
    var offline: Bool
    @State private var names = cachedConversationNames()
    @State private var draft = ""
    @State private var selected: String?
    @State private var note: String?
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(offline ? "\(session.name) · 离线，只读" : session.name)
                Button("刷新对话") {
                    Task { await refresh() }
                }
                .frame(maxWidth: .infinity, minHeight: 44)
                .disabled(offline || busy)
                ForEach(names, id: \.self) { name in
                    Text(name)
                }
                TextField("新消息", text: $draft, axis: .vertical)
                    .textFieldStyle(.roundedBorder)
                    .disabled(offline)
                Button("发给这台电脑") {
                    Task { await send() }
                }
                .buttonStyle(.borderedProminent)
                .tint(LinkTokens.attention)
                .frame(maxWidth: .infinity, minHeight: 44)
                .disabled(offline || busy || draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                Button("停止当前对话") {
                    Task { await cancel() }
                }
                .frame(maxWidth: .infinity, minHeight: 44)
                .disabled(offline || selected == nil)
                Button("查看待确认") {
                    Task { await approvals() }
                }
                .frame(maxWidth: .infinity, minHeight: 44)
                .disabled(offline || selected == nil)
                if let note {
                    Text(note)
                }
                if let error {
                    Text(error).foregroundStyle(LinkTokens.danger)
                }
            }
            .padding(24)
        }
    }

    private func refresh() async {
        busy = true
        defer { busy = false }
        do {
            let (updated, data) = try await callHost(session, method: "GET", path: "/api/conversations?limit=30")
            session = updated
            names = conversationNames(data)
            cacheConversationNames(names)
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func send() async {
        busy = true
        defer { busy = false }
        do {
            let created = try JSONSerialization.data(withJSONObject: [
                "type": "nomi",
                "name": "手机",
                "extra": [String: String](),
            ])
            let (updated, createdData) = try await callHost(session, method: "POST", path: "/api/conversations", body: created)
            session = updated
            let object = try JSONSerialization.jsonObject(with: createdData) as? [String: Any]
            let payload = object?["data"] as? [String: Any]
            let id = payload?["conversation_id"] as? String ?? ""
            let message = try JSONSerialization.data(withJSONObject: ["content": draft])
            let (sent, sentData) = try await callHost(session, method: "POST", path: "/api/conversations/\(id)/messages", body: message)
            session = sent
            selected = id
            draft = ""
            note = String(data: sentData, encoding: .utf8).map { String($0.prefix(500)) } ?? "已发送"
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func cancel() async {
        guard let selected else { return }
        do {
            let (updated, _) = try await callHost(session, method: "POST", path: "/api/conversations/\(selected)/cancel")
            session = updated
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func approvals() async {
        guard let selected else { return }
        do {
            let (updated, data) = try await callHost(session, method: "GET", path: "/api/conversations/\(selected)/confirmations")
            session = updated
            note = String(data: data, encoding: .utf8).map { String($0.prefix(500)) }
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }
}

private struct LibraryPane: View {
    var session: HostSession
    var offline: Bool
    @State private var bodyText = ""
    @State private var error: String?

    private let reads = [
        ("模型", "/api/providers"),
        ("技能", "/api/skills"),
        ("知识库", "/api/knowledge"),
        ("定时", "/api/cron"),
        ("会议", "/api/meetings"),
        ("文件", "/api/fs"),
        ("设置", "/api/settings"),
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text("这些读取发生在电脑上")
                ForEach(reads, id: \.0) { item in
                    Button(item.0) {
                        Task {
                            do {
                                let (_, data) = try await callHost(session, method: "GET", path: item.1)
                                let text = String(data: data, encoding: .utf8) ?? ""
                                bodyText = "\(item.0)\n\(String(text.prefix(500)))"
                                error = nil
                            } catch {
                                self.error = error.localizedDescription
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .disabled(offline)
                }
                if !bodyText.isEmpty {
                    Text(bodyText)
                }
                if let error {
                    Text(error).foregroundStyle(LinkTokens.danger)
                }
            }
            .padding(24)
        }
    }
}

private struct SettingsPane: View {
    @Binding var session: HostSession
    var offline: Bool
    var onDisconnect: () -> Void
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(session.name)
            Text(session.fingerprint)
                .font(.system(.body, design: .monospaced))
            Toggle("锁屏唤醒", isOn: Binding(
                get: { session.wakeEnabled },
                set: { enabled in
                    Task {
                        do {
                            let payload = try JSONSerialization.data(withJSONObject: ["enabled": enabled])
                            let (updated, _) = try await callHost(session, method: "POST", path: "/api/link/wake", body: payload)
                            var next = updated
                            next.wakeEnabled = enabled
                            session = next
                            saveSession(next)
                        } catch {
                            self.error = error.localizedDescription
                        }
                    }
                }
            ))
            .disabled(offline)
            Button("断开这台电脑", action: onDisconnect)
                .frame(maxWidth: .infinity, minHeight: 44)
            if let error {
                Text(error).foregroundStyle(LinkTokens.danger)
            }
            Spacer()
        }
        .padding(24)
    }
}

private func conversationNames(_ data: Data) -> [String] {
    guard let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return [] }
    let payload = object["data"] as? [String: Any] ?? object
    let items = payload["items"] as? [[String: Any]] ?? []
    return items.map { item in
        let name = item["name"] as? String ?? ""
        return name.isEmpty ? (item["conversation_id"] as? String ?? "") : name
    }
}
