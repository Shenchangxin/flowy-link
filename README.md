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

`swift test` 覆盖配对链接解析。把 `ios/Sources/FlowyLink` 加进一个 iOS 17 的 App 目标后，入口视图是 `LinkRootView`。

鸿蒙端用 DevEco Studio 打开 `harmony/`。当前页面会识别配对链接并展示电脑地址。证书固定后的网络连接在 Android 和 iOS 包里。鸿蒙页不会跳过证书校验去明文访问电脑。

推送到 GitHub 后，Actions 会跑 Android 打包与单元测试、Apple `swift test`，以及鸿蒙模块文件检查。

## 联合验证

两边要在同一局域网。电脑先跑包含 Link 改动的 allo 桌面端。手机安装本仓库打出的 Android 或 iOS 包。

1. 电脑首次启动会在数据目录的 `link/cert.pem` 写入证书，并监听 `0.0.0.0:25818`。明文 WebUI 仍在 `25808`，不要把那个端口改成 TLS。
2. Windows 若拦截入站，放行 TCP `25818`。用浏览器打开时还要放行网页端口 `25808`。
3. 点左下角个人信息，再点「连接手机」。不安装 App 时，点「生成网页二维码」，用微信、系统相机或其他扫码工具扫描；扫到的是 `http://` 局域网地址，手机会打开网页并登录。安装手机版时，再点「生成配对码」。两张码都应带局域网地址，而不是只有 `127.0.0.1`。
4. 手机可以扫描二维码，也可以粘贴整段 `flowy://pair?...`。扫到配对码后会直接开始连接。手机只接受证书 DER 的 SHA-256 与链接中 `fp` 一致的服务器。iOS 宿主 App 的 Info.plist 要有 `NSCameraUsageDescription`，否则系统会在打开相机时退出；没有相机时粘贴链接即可。
5. 电脑上出现这台手机的名称后点「允许」。手机进入任务页，并列出这台电脑上已有的对话；没有对话时只有「开始新对话」。点进一条对话后可以发送、停止、切换这台电脑上已经配置好的模型，并处理待确认。回复会跟着电脑上的流式事件刷新，结束后再拉一次完整消息。桌面会话列表里能看到同一条对话。库页按名称阅读技能、知识库等条目，不展示密钥。
6. 设置里可以连接另一台电脑，并在已配对的电脑之间切换。Android 会按证书指纹在局域网里重新发现地址；iOS 也会浏览同一个服务类型。发现不到就保持只读，发送按钮不可用。
7. 锁屏提醒只写「回复完成」或「需要确认」，没有对话正文。应用还在后台、进程仍在时，Android 和 iOS 会发这条系统通知。这一版没有向云端登记推送令牌，所以即使配置了 `FLOWY_LINK_FCM_SERVER_KEY`，进程被系统结束后也不会送到锁屏。
8. Android 桌面小组件只显示电脑名称和「已配对」，不放对话正文。
9. 再点一次允许不会重复发令牌。手机刷新状态会得到 `consumed`。
10. 运营平台要先执行 FlowyClaw 的 `code/backend/sql/0081/schema.sql`。吊销后，电脑下一次心跳会让这台手机的访问令牌失效。电脑允许配对后，若桌面已登录云账号，会把设备指纹登记到云端；未登录时局域网控制仍然可用。对话结束或出现待确认时，电脑会把不含正文的唤醒记到云端。

同一台电脑上的自测可以看证书指纹是否稳定：重启后 `link/cert.pem` 不变，桌面展示的指纹也不变。allo 里对应的自动化检查是：

```bash
cargo test -p flowy-link-kernel -p nomifun-link --offline
```

其中包含配对链接往返、桌面确认只发一次令牌，以及按指纹固定证书后访问 Link 监听。
