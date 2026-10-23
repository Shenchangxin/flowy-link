import { LinkCopy, LinkTokens } from '../common/Tokens';
import { parsePairingUrl } from '../common/Pairing';

@Entry
@Component
struct Index {
  @State url: string = '';
  @State error: string = '';
  @State name: string = '';
  @State port: string = '';
  @State fingerprint: string = '';
  @State addresses: string = '';

  @Builder
  fact(label: string, value: string) {
    Column() {
      Text(label)
        .fontSize(13)
        .fontColor('#5C5C5C')
      Text(value)
        .fontSize(16)
        .fontColor('#1C1C1C')
        .margin({ top: 4 })
        .wordBreak(WordBreak.BREAK_ALL)
    }
    .width('100%')
    .margin({ top: 12 })
    .alignItems(HorizontalAlign.Start)
  }

  build() {
    Column() {
      Text(LinkCopy.title)
        .fontSize(28)
        .fontColor('#1C1C1C')
        .fontWeight(FontWeight.Normal)
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
        .fontSize(16)
        .fontColor('#FFFFFF')
        .height(48)
        .width('100%')
        .margin({ top: 16 })
        .backgroundColor(LinkTokens.attention)
        .borderRadius(12)
        .onClick(() => {
          this.error = '';
          this.name = '';
          this.port = '';
          this.fingerprint = '';
          this.addresses = '';
          try {
            const parsed = parsePairingUrl(this.url.trim());
            this.name = parsed.name;
            this.port = `${parsed.port}`;
            this.fingerprint = parsed.fingerprint;
            this.addresses = parsed.addresses.join('、');
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
      if (this.name.length > 0) {
        Column() {
          Text(this.name)
            .fontSize(16)
            .fontColor('#1C1C1C')
          this.fact('端口', this.port)
          this.fact('指纹', this.fingerprint)
          this.fact('地址', this.addresses)
          Text(LinkCopy.note)
            .fontSize(13)
            .fontColor('#5C5C5C')
            .margin({ top: 16 })
        }
        .width('100%')
        .padding(16)
        .margin({ top: 24 })
        .backgroundColor('#F7F7F7')
        .borderRadius(12)
        .alignItems(HorizontalAlign.Start)
      }
    }
    .width('100%')
    .height('100%')
    .padding({ top: 24, right: 24, bottom: 24, left: 24 })
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Start)
  }
}
