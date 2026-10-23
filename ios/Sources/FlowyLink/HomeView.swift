import SwiftUI
#if os(iOS)
import UserNotifications
#endif

struct LinkRootView: View {
    @State private var session = loadSession()
    @State private var adding = false

    var body: some View {
        if let current = session, !adding {
            HomeView(session: current, onDisconnect: {
                clearSession()
                session = loadSession()
            }, onAdd: { adding = true }, onSwitch: { session = $0 })
        } else {
            DisconnectedView(onConnected: {
                adding = false
                session = loadSession()
            }, onCancel: session == nil ? nil : { adding = false })
        }
    }
}

private enum HomeCopy {
    static let empty = "这台电脑上还没有对话"
    static let start = "开始新对话"
    static let newer = "新对话"
    static let refresh = "刷新"
    static let retry = "重试"
    static let offline = "离线，只读"
    static let offlineBody = "离线，只读。连接恢复后可以继续。"
    static let offlineThread = "离线，只读。连上电脑后会显示这条对话。"
    static let message = "消息"
    static let sending = "正在发送"
    static let send = "发送"
    static let back = "返回"
    static let models = "模型"
    static let pending = "待确认"
    static let stop = "停止"
    static let modelTitle = "选择这台电脑上的模型"
    static let modelEmpty = "这台电脑还没有可用模型"
    static let backThread = "返回对话"
    static let confirmEmpty = "没有待确认的操作"
    static let confirmBody = "这些操作会在电脑上执行"
    static let allow = "允许"
    static let libraryTitle = "电脑上的内容"
    static let libraryEmpty = "这里还没有条目"
    static let computers = "电脑"
    static let add = "连接另一台电脑"
    static let wake = "锁屏提醒"
    static let wakeBody = "提醒只写状态，不包含对话正文。应用还在后台时会发系统通知。这一版没有登记推送令牌，进程结束后不会送到锁屏。"
    static let disconnect = "断开这台电脑"
    static let disconnectTitle = "断开这台电脑？"
    static let disconnectBody = "这台手机不再控制这台电脑。其他已配对的电脑还在。"
    static let cancel = "取消"
}

struct HomeView: View {
    @State var session: HostSession
    var onDisconnect: () -> Void
    var onAdd: () -> Void
    var onSwitch: (HostSession) -> Void
    @State private var offline = false
    @State private var tab = "任务"
    @State private var notice: String?
    #if os(iOS)
    @Environment(\.horizontalSizeClass) private var widthClass
    #endif

    var body: some View {
        VStack(spacing: 0) {
            if let notice, !notice.isEmpty {
                Text(notice)
                    .font(.system(size: 13))
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 24)
                    .padding(.top, 8)
            }
            pages
        }
        .tint(LinkTokens.attention)
        #if os(iOS)
        .safeAreaPadding(.all)
        #endif
        .task {
            do {
                let discovered = await mergeDiscoveredAddress(session)
                let (updated, _) = try await callHost(discovered, method: "GET", path: "/api/link/whoami")
                session = updated
                saveSession(updated)
                notice = nil
            } catch {
                offline = true
            }
        }
        .onAppear { LinkWait.show = { notice = $0 } }
        .onDisappear { LinkWait.show = nil }
    }

    @ViewBuilder
    private var pages: some View {
        #if os(iOS)
        if widthClass == .regular {
            HStack(spacing: 0) {
                TaskPane(session: $session, offline: offline)
                Rectangle().fill(LinkTokens.line).frame(width: 1)
                LibraryPane(session: session, offline: offline)
                Rectangle().fill(LinkTokens.line).frame(width: 1)
                SettingsPane(session: $session, offline: offline, onDisconnect: onDisconnect, onAdd: onAdd, onSwitch: onSwitch)
            }
        } else {
            phone
        }
        #else
        phone
        #endif
    }

    private var phone: some View {
        VStack(spacing: 0) {
            ZStack {
                TaskPane(session: $session, offline: offline)
                    .opacity(tab == "任务" ? 1 : 0)
                    .allowsHitTesting(tab == "任务")
                    .accessibilityHidden(tab != "任务")
                LibraryPane(session: session, offline: offline)
                    .opacity(tab == "库" ? 1 : 0)
                    .allowsHitTesting(tab == "库")
                    .accessibilityHidden(tab != "库")
                SettingsPane(session: $session, offline: offline, onDisconnect: onDisconnect, onAdd: onAdd, onSwitch: onSwitch)
                    .opacity(tab == "设置" ? 1 : 0)
                    .allowsHitTesting(tab == "设置")
                    .accessibilityHidden(tab != "设置")
            }
            Rectangle().fill(LinkTokens.line).frame(height: 1)
            HStack(spacing: 0) {
                ForEach(["任务", "库", "设置"], id: \.self) { item in
                    Button(item) { tab = item }
                        .buttonStyle(.plain)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .foregroundStyle(tab == item ? LinkTokens.attention : .secondary)
                }
            }
        }
    }
}

private struct TaskPane: View {
    @Binding var session: HostSession
    var offline: Bool
    @State private var rows: [(String, String)]
    @State private var opened: String?
    @State private var messages: [(Bool, String)]
    @State private var live: String
    @State private var draft: String
    @State private var error: String?
    @State private var busy: Bool
    @State private var loading: Bool
    @State private var loadingMessages: Bool
    @State private var showModels: Bool
    @State private var modelLoading: Bool
    @State private var models: [ModelChoice]
    @State private var showConfirms: Bool
    @State private var confirmLoading: Bool
    @State private var confirms: [PendingConfirmation]
    @State private var watch: LinkWatch?

    init(session: Binding<HostSession>, offline: Bool) {
        _session = session
        self.offline = offline
        let fingerprint = session.wrappedValue.fingerprint
        let cached = cachedConversations(fingerprint)
        let last = loadLastConversation(fingerprint)
        let open = cached.contains(where: { $0.0 == last }) ? last : nil
        _rows = State(initialValue: cached)
        _opened = State(initialValue: open)
        _messages = State(initialValue: [])
        _live = State(initialValue: "")
        _draft = State(initialValue: open.map { loadDraft(fingerprint, $0) } ?? "")
        _error = State(initialValue: nil)
        _busy = State(initialValue: false)
        _loading = State(initialValue: cached.isEmpty && !offline)
        _loadingMessages = State(initialValue: false)
        _showModels = State(initialValue: false)
        _modelLoading = State(initialValue: false)
        _models = State(initialValue: [])
        _showConfirms = State(initialValue: false)
        _confirmLoading = State(initialValue: false)
        _confirms = State(initialValue: [])
        _watch = State(initialValue: nil)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(session.name).font(.system(size: 16)).lineLimit(1).padding(.horizontal, 24).padding(.top, 16)
            if offline {
                Text(HomeCopy.offline).font(.system(size: 13)).foregroundStyle(.secondary).padding(.horizontal, 24)
            }
            Group {
                if showModels {
                    modelList
                } else if showConfirms {
                    confirmList
                } else if let opened {
                    thread(opened)
                } else {
                    list
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .padding(.horizontal, 24)
            .padding(.top, 12)
        }
        .task(id: session.fingerprint) { await refresh() }
        .task(id: opened) { await watchOpened() }
        .onChange(of: session.fingerprint) { _, fingerprint in
            rows = cachedConversations(fingerprint)
            let last = loadLastConversation(fingerprint)
            opened = rows.contains(where: { $0.0 == last }) ? last : nil
            messages = []
            live = ""
            showModels = false
            showConfirms = false
            error = nil
        }
        .onChange(of: opened) { _, id in
            saveLastConversation(session.fingerprint, id)
            draft = id.map { loadDraft(session.fingerprint, $0) } ?? ""
            if id == nil { live = "" }
        }
        .onDisappear { watch?.stop() }
    }

    private var list: some View {
        VStack(alignment: .leading, spacing: 8) {
            if loading && rows.isEmpty {
                LoadingLines()
            } else if rows.isEmpty && offline {
                Text(HomeCopy.offlineBody).font(.system(size: 16))
            } else if rows.isEmpty, let error {
                Text(error).font(.system(size: 13)).foregroundStyle(LinkTokens.danger)
                Button(HomeCopy.retry) { Task { await refresh() } }
                    .buttonStyle(.borderedProminent)
                    .frame(maxWidth: .infinity, minHeight: 44)
            } else if rows.isEmpty {
                Text(HomeCopy.empty).font(.system(size: 16))
                Button(busy ? HomeCopy.sending : HomeCopy.start) { Task { await create() } }
                    .buttonStyle(.borderedProminent)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .disabled(offline || busy)
            } else {
                Button(HomeCopy.refresh) { Task { await refresh() } }
                    .buttonStyle(.plain)
                    .frame(minHeight: 44, alignment: .leading)
                    .disabled(offline)
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(rows, id: \.0) { row in
                            Button { opened = row.0 } label: { NameLine(row.1) }
                                .buttonStyle(.plain)
                        }
                    }
                }
                Button(busy ? HomeCopy.sending : HomeCopy.newer) { Task { await create() } }
                    .buttonStyle(.plain)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .disabled(offline || busy)
                if let error { Text(error).font(.system(size: 13)).foregroundStyle(LinkTokens.danger) }
            }
        }
    }

    private func thread(_ id: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 16) {
                    Button(HomeCopy.back) { opened = nil }.buttonStyle(.plain).frame(minHeight: 44)
                    Button(HomeCopy.models) { Task { await loadModels() } }.buttonStyle(.plain).frame(minHeight: 44).disabled(offline)
                    Button(HomeCopy.pending) { Task { await loadConfirms(id) } }.buttonStyle(.plain).frame(minHeight: 44).disabled(offline)
                    Button(HomeCopy.stop) { Task { await stop(id) } }.buttonStyle(.plain).frame(minHeight: 44).disabled(offline)
                }
            }
            .frame(height: 44)
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 8) {
                        if loadingMessages && messages.isEmpty {
                            LoadingLines()
                        } else if offline && messages.isEmpty {
                            Text(HomeCopy.offlineThread).font(.system(size: 13)).foregroundStyle(.secondary)
                        }
                        ForEach(Array(messages.enumerated()), id: \.offset) { _, line in
                            bubble(line.1, mine: line.0)
                        }
                        if !live.isEmpty { bubble(live, mine: false) }
                        Color.clear.frame(height: 1).id("bottom")
                    }
                }
                .onChange(of: messages.count) { _, _ in proxy.scrollTo("bottom", anchor: .bottom) }
                .onChange(of: live) { _, _ in proxy.scrollTo("bottom", anchor: .bottom) }
            }
            TextField(offline ? HomeCopy.offline : HomeCopy.message, text: $draft, axis: .vertical)
                .textFieldStyle(.roundedBorder)
                .disabled(offline)
                .onChange(of: draft) { _, value in saveDraft(session.fingerprint, id, value) }
            Button(busy ? HomeCopy.sending : HomeCopy.send) { Task { await send(id) } }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity, minHeight: 44)
                .disabled(offline || busy || draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            if let error { Text(error).font(.system(size: 13)).foregroundStyle(LinkTokens.danger) }
        }
    }

    private var modelList: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(HomeCopy.modelTitle).font(.system(size: 16))
            if modelLoading {
                LoadingLines()
            } else if models.isEmpty {
                Text(HomeCopy.modelEmpty).font(.system(size: 13)).foregroundStyle(.secondary)
            } else {
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(models) { item in
                            Button { Task { await pick(item) } } label: { NameLine("\(item.providerName) · \(item.model)") }
                                .buttonStyle(.plain)
                                .disabled(offline)
                        }
                    }
                }
            }
            Button(HomeCopy.backThread) { showModels = false }.buttonStyle(.plain).frame(minHeight: 44)
            if let error { Text(error).font(.system(size: 13)).foregroundStyle(LinkTokens.danger) }
        }
    }

    private var confirmList: some View {
        VStack(alignment: .leading, spacing: 8) {
            if confirmLoading {
                Text(HomeCopy.confirmBody).font(.system(size: 16))
                LoadingLines()
            } else if confirms.isEmpty {
                Text(HomeCopy.confirmEmpty).font(.system(size: 16))
            } else {
                Text(HomeCopy.confirmBody).font(.system(size: 16))
                ForEach(confirms) { item in
                    Text(item.title).font(.system(size: 16))
                    Button(HomeCopy.allow) { Task { await allow(item) } }
                        .buttonStyle(.borderedProminent)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .disabled(offline)
                }
            }
            Button(HomeCopy.backThread) { showConfirms = false }.buttonStyle(.plain).frame(minHeight: 44)
            if let error { Text(error).font(.system(size: 13)).foregroundStyle(LinkTokens.danger) }
        }
    }

    private func bubble(_ text: String, mine: Bool) -> some View {
        HStack {
            if mine { Spacer(minLength: 48) }
            Text(text)
                .font(.system(size: 16))
                .foregroundStyle(mine ? .white : .primary)
                .padding(12)
                .background(mine ? LinkTokens.attention : LinkTokens.paper, in: RoundedRectangle(cornerRadius: 12))
                .frame(maxWidth: 280, alignment: mine ? .trailing : .leading)
            if !mine { Spacer(minLength: 48) }
        }
    }

    private func refresh() async {
        guard !offline else {
            loading = false
            return
        }
        if rows.isEmpty { loading = true }
        defer { loading = false }
        do {
            let (updated, data) = try await callHost(session, method: "GET", path: "/api/conversations?limit=30")
            session = updated
            rows = conversationRows(data)
            cacheConversations(session.fingerprint, rows)
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func watchOpened() async {
        guard let opened, !offline else { return }
        watch?.stop()
        let next = LinkWatch(host: session, conversationId: opened)
        watch = next
        next.start(onText: { live = $0 }, onPhase: { _ in
            Task { await reloadMessages(opened, finishLive: true) }
        })
        await reloadMessages(opened, finishLive: false)
    }

    private func reloadMessages(_ id: String, finishLive: Bool) async {
        if messages.isEmpty { loadingMessages = true }
        defer { loadingMessages = false }
        guard let (_, data) = try? await callHost(session, method: "GET", path: "/api/conversations/\(id)/messages?limit=40") else { return }
        messages = messageLines(data)
        if finishLive { live = "" }
    }

    private func create() async {
        guard !busy else { return }
        busy = true
        defer { busy = false }
        do {
            let body = try JSONSerialization.data(withJSONObject: ["type": "nomi", "name": "手机", "extra": [:] as [String: Any]])
            let (updated, data) = try await callHost(session, method: "POST", path: "/api/conversations", body: body)
            session = updated
            let object = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            if let id = (object?["data"] as? [String: Any])?["conversation_id"] as? String, !id.isEmpty {
                rows.insert((id, "手机"), at: 0)
                cacheConversations(session.fingerprint, rows)
                opened = id
            }
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func send(_ id: String) async {
        busy = true
        defer { busy = false }
        do {
            let body = try JSONSerialization.data(withJSONObject: ["content": draft])
            _ = try await callHost(session, method: "POST", path: "/api/conversations/\(id)/messages", body: body)
            let (updated, data) = try await callHost(session, method: "GET", path: "/api/conversations/\(id)/messages?limit=40")
            session = updated
            messages = messageLines(data)
            draft = ""
            saveDraft(session.fingerprint, id, "")
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func stop(_ id: String) async {
        do {
            let (updated, _) = try await callHost(session, method: "POST", path: "/api/conversations/\(id)/cancel")
            session = updated
            error = nil
        } catch { self.error = error.localizedDescription }
    }

    private func loadModels() async {
        showModels = true
        modelLoading = true
        defer { modelLoading = false }
        do {
            let (updated, data) = try await callHost(session, method: "GET", path: "/api/providers")
            session = updated
            models = modelChoices(data)
            error = nil
        } catch { self.error = error.localizedDescription }
    }

    private func pick(_ item: ModelChoice) async {
        guard let opened else { return }
        do {
            let body = try JSONSerialization.data(withJSONObject: ["model": ["provider_id": item.providerId, "model": item.model]])
            let (updated, _) = try await callHost(session, method: "PATCH", path: "/api/conversations/\(opened)", body: body)
            session = updated
            showModels = false
            error = nil
        } catch { self.error = error.localizedDescription }
    }

    private func loadConfirms(_ id: String) async {
        showConfirms = true
        confirmLoading = true
        defer { confirmLoading = false }
        do {
            let (updated, data) = try await callHost(session, method: "GET", path: "/api/conversations/\(id)/confirmations")
            session = updated
            confirms = confirmationLines(data)
            error = nil
        } catch { self.error = error.localizedDescription }
    }

    private func allow(_ item: PendingConfirmation) async {
        guard let opened else { return }
        do {
            let (updated, history) = try await callHost(session, method: "GET", path: "/api/conversations/\(opened)/messages?limit=20")
            session = updated
            guard let msgId = latestMessageId(history) else { throw LinkSessionError.message("没有可确认的消息") }
            let body = try JSONSerialization.data(withJSONObject: ["msg_id": msgId, "data": item.option])
            _ = try await callHost(session, method: "POST", path: "/api/conversations/\(opened)/confirmations/\(item.callId)/confirm", body: body)
            confirms.removeAll { $0.callId == item.callId }
            error = nil
        } catch { self.error = error.localizedDescription }
    }
}

private struct LibraryPane: View {
    var session: HostSession
    var offline: Bool
    @State private var title: String?
    @State private var path: String?
    @State private var rows: [String] = []
    @State private var loading = false
    @State private var error: String?
    private let reads = [("模型", "/api/providers"), ("技能", "/api/skills"), ("知识库", "/api/knowledge"), ("定时", "/api/cron"), ("会议", "/api/meetings"), ("文件", "/api/fs"), ("设置", "/api/settings")]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let title, path != nil {
                Text(title).font(.system(size: 16))
                if loading {
                    LoadingLines()
                } else if let error, rows.isEmpty {
                    Text(error).font(.system(size: 13)).foregroundStyle(LinkTokens.danger)
                } else if rows.isEmpty {
                    Text(HomeCopy.libraryEmpty).font(.system(size: 16)).foregroundStyle(.secondary)
                } else {
                    ScrollView {
                        VStack(spacing: 0) {
                            ForEach(Array(rows.enumerated()), id: \.offset) { _, name in
                                NameLine(name)
                            }
                        }
                    }
                }
                Button(HomeCopy.back) { self.title = nil; self.path = nil; error = nil }
                    .buttonStyle(.plain)
                    .frame(minHeight: 44)
                if let error, !rows.isEmpty {
                    Text(error).font(.system(size: 13)).foregroundStyle(LinkTokens.danger)
                }
            } else {
                Text(HomeCopy.libraryTitle).font(.system(size: 16))
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(reads, id: \.0) { item in
                            Button {
                                title = item.0
                                path = item.1
                                rows = []
                                error = nil
                                loading = true
                                Task { await load(item.1) }
                            } label: { NameLine(item.0) }
                            .buttonStyle(.plain)
                            .disabled(offline)
                        }
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 16)
    }

    private func load(_ path: String) async {
        do {
            let (_, data) = try await callHost(session, method: "GET", path: path)
            rows = libraryRows(data)
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }
}

private struct SettingsPane: View {
    @Binding var session: HostSession
    var offline: Bool
    var onDisconnect: () -> Void
    var onAdd: () -> Void
    var onSwitch: (HostSession) -> Void
    @State private var error: String?
    @State private var confirmDisconnect = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(HomeCopy.computers).font(.system(size: 16))
            ForEach(loadHosts(), id: \.fingerprint) { host in
                Button {
                    activateHost(host.fingerprint)
                    onSwitch(host)
                } label: {
                    NameLine(host.name, color: host.fingerprint == session.fingerprint ? LinkTokens.attention : .primary)
                }
                .buttonStyle(.plain)
            }
            Button(HomeCopy.add, action: onAdd).buttonStyle(.plain).frame(maxWidth: .infinity, minHeight: 44)
            Toggle(HomeCopy.wake, isOn: Binding(get: { session.wakeEnabled }, set: { enabled in
                #if os(iOS)
                if enabled {
                    UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
                }
                #endif
                Task {
                    do {
                        let payload = try JSONSerialization.data(withJSONObject: ["enabled": enabled])
                        let (updated, _) = try await callHost(session, method: "POST", path: "/api/link/wake", body: payload)
                        var next = updated
                        next.wakeEnabled = enabled
                        session = next
                        saveSession(next)
                        error = nil
                    } catch { self.error = error.localizedDescription }
                }
            }))
            .frame(minHeight: 44)
            .disabled(offline)
            Text(HomeCopy.wakeBody).font(.system(size: 13)).foregroundStyle(.secondary)
            Text(session.fingerprint).font(.system(size: 13, design: .monospaced)).foregroundStyle(.secondary).textSelection(.enabled)
            Button(HomeCopy.disconnect) { confirmDisconnect = true }
                .buttonStyle(.plain)
                .frame(maxWidth: .infinity, minHeight: 44)
                .foregroundStyle(LinkTokens.danger)
            if let error { Text(error).font(.system(size: 13)).foregroundStyle(LinkTokens.danger) }
            Spacer(minLength: 0)
        }
        .padding(24)
        .confirmationDialog(HomeCopy.disconnectTitle, isPresented: $confirmDisconnect, titleVisibility: .visible) {
            Button(HomeCopy.disconnect, role: .destructive, action: onDisconnect)
            Button(HomeCopy.cancel, role: .cancel) {}
        } message: {
            Text(HomeCopy.disconnectBody)
        }
    }
}

private struct NameLine: View {
    var text: String
    var color: Color

    init(_ text: String, color: Color = .primary) {
        self.text = text
        self.color = color
    }

    var body: some View {
        VStack(spacing: 0) {
            Text(text)
                .font(.system(size: 16))
                .foregroundStyle(color)
                .lineLimit(1)
                .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
            Rectangle().fill(LinkTokens.line).frame(height: 1)
        }
    }
}

private struct LoadingLines: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            RoundedRectangle(cornerRadius: 4).fill(LinkTokens.paper).frame(height: 16)
            RoundedRectangle(cornerRadius: 4).fill(LinkTokens.paper).frame(height: 16)
            RoundedRectangle(cornerRadius: 4).fill(LinkTokens.paper).frame(width: 180, height: 16)
        }
        .padding(.top, 8)
    }
}
