import { LinkCopy, LinkTokens } from '../common/Tokens';
import { parsePairingUrl } from '../common/Pairing';

@Entry
@Component
struct Index {
  @State url: string = '';
  @State error: string = '';
  @State status: string = '';

  build() {
    Column() {
      Text(LinkCopy.title)
        .fontSize(28)
        .fontColor('#1C1C1C')
      Text(LinkCopy.body)
        .fontSize(16)
        .fontColor('#5C5C5C')
        .margin({ top: 12 })
      TextInput({ placeholder: '配对链接', text: this.url })
        .height(48)
        .margin({ top: 24 })
        .onChange((value: string) => {
          this.url = value;
        })
      Button(LinkCopy.action)
        .height(48)
        .width('100%')
        .margin({ top: 16 })
        .backgroundColor(LinkTokens.attention)
        .onClick(() => {
          this.error = '';
          this.status = '';
          try {
            const parsed = parsePairingUrl(this.url.trim());
            this.status = `已识别 ${parsed.name}，地址 ${parsed.addresses.join('、')}。鸿蒙端的证书固定连接仍在下一版，请用同一仓库的 Android 或 iOS 包向这台电脑发起允许。`;
          } catch (caught) {
            this.error = caught instanceof Error ? caught.message : '配对链接格式不正确';
          }
        })
      if (this.error.length > 0) {
        Text(this.error)
          .fontSize(13)
          .fontColor(LinkTokens.danger)
          .margin({ top: 8 })
      }
      if (this.status.length > 0) {
        Text(this.status)
          .fontSize(16)
          .margin({ top: 8 })
      }
    }
    .width('100%')
    .height('100%')
    .padding({ top: 48, right: 24, bottom: 24, left: 24 })
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Start)
  }
}
