# Pixel 9aの端末内起動診断

## 使い方（しずかカメラ 1.2.0 / PC・Wi-Fi・ADB不要）
1. カメラを通常起動し、右上ⓘ →「電源ボタンの起動診断」を開く。
2. 通常・ロック画面用カメラIntentの登録状況と候補を確認する。
3. 「通常カメラIntentで直接起動を試す」を押し、カメラが開いたら戻る操作で診断画面に戻る。
4. 「履歴をリセットして再確認」を押して、ホーム画面で電源ボタンを2回押す。
5. 起動したアプリを確認後、しずかカメラを通常起動し直し「外部Intent受信履歴」を確認する。
6. 「結果をコピー」から結果を共有する。

## 判定上の注意
- カメラIntentの自己テスト成功はOSの電源ボタン起動先を証明しない。
- 外部Intentの受信は他アプリによる起動も含み、物理ボタン由来とは限らない。
- SystemUI起動先設定の参照が不可能なときは未判定とする。
- 端末OS側で純正カメラ固定の場合、一般アプリだけでは起動先を変更できない可能性が高い。

## プライバシーと安全
- 通信・位置情報・マイク・写真の読み込み権限は追加しない。
- 端末内にカメラIntentの種類と受信日時だけを記録する。ユーザーがコピー・共有する場合だけ結果を外に出す。
- 既存のロック画面セッションでは診断画面を開かない。

参考資料:
- https://developer.android.com/training/package-visibility/declaring
- https://developer.android.com/reference/android/provider/MediaStore
- https://android.googlesource.com/platform/frameworks/base/+/ea75bd1d3ca9/packages/SystemUI/docs/camera.md
