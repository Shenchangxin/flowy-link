import SwiftUI
#if os(iOS)
import AVFoundation
#endif

enum LinkTokens {
    static let attention = Color(red: 79 / 255, green: 111 / 255, blue: 141 / 255)
    static let danger = Color(red: 140 / 255, green: 58 / 255, blue: 58 / 255)
    static let paper = Color(red: 242 / 255, green: 242 / 255, blue: 242 / 255)
    static let line = Color(red: 230 / 255, green: 230 / 255, blue: 230 / 255)
}

enum LinkCopy {
    static let title = "连接这台电脑上的 Flowy"
    static let body = "在电脑的「手机版」里生成配对码。扫码或粘贴后，手机会核对证书指纹，并等你在电脑上点允许。"
    static let action = "连接这台电脑"
    static let field = "配对链接"
}

struct DisconnectedView: View {
    var onConnected: () -> Void = {}
    var onCancel: (() -> Void)? = nil
    @State private var url = ""
    @State private var error: String?
    @State private var busy = false
    @State private var scanning = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(LinkCopy.title)
                .font(.system(size: 28))
            Text(LinkCopy.body)
                .font(.system(size: 16))
                .foregroundStyle(.secondary)
            TextField(LinkCopy.field, text: $url, axis: .vertical)
                .lineLimit(3...6)
                .textFieldStyle(.roundedBorder)
            Button(busy ? "正在连接" : (url.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "扫描配对码" : LinkCopy.action)) {
                let trimmed = url.trimmingCharacters(in: .whitespacesAndNewlines)
                if trimmed.isEmpty {
                    scanning = true
                    return
                }
                busy = true
                error = nil
                Task {
                    do {
                        let result = try await pairWithHost(rawUrl: trimmed)
                        if result.status.hasPrefix("已连接") { onConnected() }
                    } catch {
                        self.error = error.localizedDescription
                    }
                    busy = false
                }
            }
            .buttonStyle(.borderedProminent)
            .tint(LinkTokens.attention)
            .frame(maxWidth: .infinity, minHeight: 44)
            .disabled(busy)
            if url.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                Text("也可以把链接直接粘贴到上面的输入框。")
                    .font(.system(size: 13))
                    .foregroundStyle(.secondary)
            }
            if let error {
                Text(error)
                    .font(.system(size: 13))
                    .foregroundStyle(LinkTokens.danger)
            }
            if let onCancel {
                Button("返回已连接的电脑", action: onCancel)
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .padding(24)
        #if os(iOS)
        .safeAreaPadding(.all)
        .sheet(isPresented: $scanning) {
            ScanView { code in
                url = code
                scanning = false
                busy = true
                error = nil
                Task {
                    do {
                        let result = try await pairWithHost(rawUrl: code)
                        if result.status.hasPrefix("已连接") { onConnected() }
                    } catch {
                        self.error = error.localizedDescription
                    }
                    busy = false
                }
            } onClose: { message in
                scanning = false
                error = message
            }
        }
        #endif
    }
}

#if os(iOS)
struct ScanView: UIViewControllerRepresentable {
    var onCode: (String) -> Void
    var onClose: (String?) -> Void

    func makeUIViewController(context: Context) -> ScannerController {
        let controller = ScannerController()
        controller.onCode = onCode
        controller.onClose = onClose
        return controller
    }

    func updateUIViewController(_ controller: ScannerController, context: Context) {}
}

final class ScannerController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onCode: ((String) -> Void)?
    var onClose: ((String?) -> Void)?
    private let session = AVCaptureSession()
    private var preview: AVCaptureVideoPreviewLayer?
    private var delivered = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            start()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { allowed in
                DispatchQueue.main.async {
                    if allowed { self.start() } else { self.onClose?("没有相机权限。可以把配对链接粘贴进去。") }
                }
            }
        default:
            onClose?("没有相机权限。可以把配对链接粘贴进去。")
        }
        let hint = UILabel()
        hint.text = "把电脑上的配对码放进画面"
        hint.textColor = .white
        hint.font = .systemFont(ofSize: 16)
        hint.translatesAutoresizingMaskIntoConstraints = false
        let close = UIButton(type: .system)
        close.setTitle("改用粘贴", for: .normal)
        close.tintColor = .white
        close.titleLabel?.font = .systemFont(ofSize: 16)
        close.translatesAutoresizingMaskIntoConstraints = false
        close.addTarget(self, action: #selector(closeTapped), for: .touchUpInside)
        view.addSubview(hint)
        view.addSubview(close)
        NSLayoutConstraint.activate([
            hint.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            hint.bottomAnchor.constraint(equalTo: close.topAnchor, constant: -8),
            close.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            close.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -24),
            close.heightAnchor.constraint(equalToConstant: 44),
            close.widthAnchor.constraint(greaterThanOrEqualToConstant: 44),
        ])
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        preview?.frame = view.bounds
    }

    private func start() {
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else {
            onClose?("这台手机打不开相机。")
            return
        }
        session.addInput(input)
        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else { return }
        session.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = [.qr]
        let preview = AVCaptureVideoPreviewLayer(session: session)
        preview.frame = view.bounds
        preview.videoGravity = .resizeAspectFill
        view.layer.insertSublayer(preview, at: 0)
        self.preview = preview
        DispatchQueue.global(qos: .userInitiated).async { self.session.startRunning() }
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput objects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard !delivered, let code = objects.compactMap({ $0 as? AVMetadataMachineReadableCodeObject }).first?.stringValue,
              code.hasPrefix("flowy://pair?") else { return }
        delivered = true
        session.stopRunning()
        onCode?(code)
    }

    @objc private func closeTapped() {
        session.stopRunning()
        onClose?(nil)
    }
}
#endif
