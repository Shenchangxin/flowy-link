import SwiftUI

enum LinkTokens {
    static let attention = Color(red: 79 / 255, green: 111 / 255, blue: 141 / 255)
    static let danger = Color(red: 140 / 255, green: 58 / 255, blue: 58 / 255)
}

enum LinkCopy {
    static let title = "连接这台电脑上的 Flowy"
    static let body = "在电脑的「手机版」里生成配对码，把链接粘贴到这里。手机会校验证书指纹，然后等你在电脑上点允许。"
    static let action = "连接这台电脑"
    static let field = "配对链接"
}

struct DisconnectedView: View {
    var onConnected: () -> Void = {}
    @State private var url = ""
    @State private var error: String?
    @State private var status: String?
    @State private var busy = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(LinkCopy.title)
                .font(.system(size: 28))
            Text(LinkCopy.body)
                .font(.system(size: 16))
            TextField(LinkCopy.field, text: $url, axis: .vertical)
                .lineLimit(3...6)
                .textFieldStyle(.roundedBorder)
            Button(busy ? "正在连接" : LinkCopy.action) {
                busy = true
                error = nil
                status = nil
                Task {
                    do {
                        let result = try await pairWithHost(rawUrl: url)
                        status = result.status
                        if result.status.hasPrefix("已连接") {
                            onConnected()
                        }
                    } catch {
                        self.error = error.localizedDescription
                    }
                    busy = false
                }
            }
            .buttonStyle(.borderedProminent)
            .tint(LinkTokens.attention)
            .frame(minHeight: 44)
            .disabled(busy || url.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            if let error {
                Text(error)
                    .font(.system(size: 13))
                    .foregroundStyle(LinkTokens.danger)
            }
            if let status {
                Text(status)
                    .font(.system(size: 16))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .padding(24)
        #if os(iOS)
        .safeAreaPadding(.all)
        #endif
    }
}
