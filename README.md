# Flowy Link

Flowy 手机版。手机是电脑上正在运行的 Flowy 的另一块屏幕：扫码或粘贴配对链接后，登录在电脑进程里生效。对话正文不经过云端。

这一仓库包含 Android、iOS、鸿蒙三端界面，以及打包用的 GitHub Actions。电脑端的 TLS 监听、配对接口和桌面「手机版」页在 allo 工程里。

## 构建

Android 需要 JDK 17。在 `android/` 目录执行：

```bash
gradle :app:assembleDebug :app:testDebugUnitTest
```

没有本机 Gradle 时，用 Android Studio 打开 `android/`。调试包在 `android/app/build/outputs/apk/debug/`。

Apple 端在 macOS 上，于 `ios/` 目录执行：

```bash
swift test
```

`swift test` 覆盖配对链接解析。把 `ios/Sources/FlowyLink` 加进一个 iOS 17 的 App 目标后，入口视图是 `DisconnectedView`。

鸿蒙端用 DevEco Studio 打开 `harmony/`。当前页面会识别配对链接并展示电脑地址。证书固定后的网络连接在 Android 和 iOS 包里。

推送到 GitHub 后，Actions 会跑 Android 打包与单元测试、Apple `swift test`，以及鸿蒙模块文件检查。

## 联合验证

两边要在同一局域网。电脑先跑包含 Link 改动的 allo 桌面端。手机安装本仓库打出的 Android 或 iOS 包。

1. 电脑首次启动会在数据目录的 `link/cert.pem` 写入证书，并监听 `0.0.0.0:25818`。明文 WebUI 仍在 `25808`，不要把那个端口改成 TLS。
2. Windows 若拦截入站，放行 TCP `25818`。
3. 打开桌面的开放能力，进入「手机版」，点「生成配对码」。二维码和链接里应有局域网地址，而不是只有 `127.0.0.1`。
4. 手机把整段 `flowy://pair?...` 链接粘贴进去，点「连接这台电脑」。手机只接受证书 DER 的 SHA-256 与链接中 `fp` 一致的服务器。
5. 电脑上出现这台手机的名称后点「允许」。手机应显示「已连接」，并已用返回的访问令牌调用过 `whoami`。
6. 再点一次允许不会重复发令牌。手机刷新状态会得到 `consumed`。
7. 运营平台要先执行 FlowyClaw 的 `code/backend/sql/0081/schema.sql`。吊销后，电脑下一次心跳会让这台手机的访问令牌失效。

同一台电脑上的自测可以看证书指纹是否稳定：重启后 `link/cert.pem` 不变，桌面展示的指纹也不变。allo 里对应的自动化检查是：

```bash
cargo test -p flowy-link-kernel -p nomifun-link --offline
```

其中包含配对链接往返、桌面确认只发一次令牌，以及按指纹固定证书后访问 Link 监听。
