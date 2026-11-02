# ShizukaCamera APK監査（2026-11-02）

ユーザー提供APK `ShizukaCamera-1.0.0.apk` と `ShizukaCamera-1.1.0.apk` の静的検査結果。アプリの実機挙動はユーザー報告に基づく。ADBによるシステム設定の直接確認は未実施。

| 項目 | 1.0.0 | 1.1.0 |
|---|---|---|
| `versionCode` | 1 | 2 |
| `versionName` | 1.0.0 | 1.1.0 |
| パッケージ | `jp.dharmainsight.shizukacamera` | 同左 |
| min/target SDK | 31 / 36 | 31 / 36 |
| メインActivity | `.MainActivity` | 同左 |
| `STILL_IMAGE_CAMERA` | なし | `.MainActivity` に宣言 |
| `STILL_IMAGE_CAMERA_SECURE` | なし | `.SecureCameraActivity` に宣言 |
| `CAMERA` 権限 | あり | あり |
| APKサイズ | 41,319 bytes | 41,319 bytes |

## SHA-256（オリジナルAPK）

- 1.0.0: `a5e76031409103172241edda81b992cda84e733f53a650dc55437642f65dce89`
- 1.1.0: `1eef6f1f8d9368984c10a3c7726c58ad472d9bce69fa1346238ca7b7aef8d336`

## 署名

APK Signature Scheme v2の署名ブロックから取り出したX.509署名証明書のSHA-256フィンガープリントは双方で一致。

`2048e9fb749d0b68b9153f8cf290d94efff0a9b5e6bb0ea5fa9db1bab622bbfe`

この一致は**既存版同士の署名主体が同じこと**を示すが、オリジナルの署名秘密鍵が入手できたことは意味しない。新しいAPKの上書きインストールには同一秘密鍵等、Androidの署名互換性を満たす必要がある。

## 実機で確認できた現象

- Pixel 9a / Android 17（ユーザー申告）。
- 1.1.0アプリは通常起動と撮影が可能。
- 電源ボタン2回押しは設定画面で有効、起動先「カメラ」。
- Pixelカメラを無効化した場合、電源ボタン2回押しはアプリを起動せず画面消灯。
- Pixelカメラを有効にすると、電源ボタン2回押しから純正カメラが起動。
- カメラアプリの選択画面は表示されない。

## 原因仮説と判定基準

1. **SystemUIの起動パッケージ指定**：AndroidはカメラジェスチャーのIntent送付先を特定のパッケージに限定可能。実機の設定値が純正カメラに固定されているなら、アプリのManifest変更だけでは解決しない。
2. **Activityの起動条件・処理**：1.1.0にはIntent宣言があるものの、宣言だけではActivityが必ず起動・動作できると証明できない。ADBでIntent個別起動を確認する。
3. **Intentの解決・既定設定**：競合ハンドラーやパッケージの有効状態などを確認する。

**現時点で「Pixelが純正カメラをハードコードしている」とは断定しない。**

## 再現／診断

Pixelカメラを有効に戻す。USBデバッグ有効化とPCのADBセットアップ後、以下を実行する。

```bash
bash scripts/diagnose-pixel-camera.sh
bash scripts/diagnose-pixel-camera.sh --launch-test  # ロック解除状態で
```

結果にしずかカメラが含まれるかと、明示的Intentで起動できるかを確認。コマンドの利用可否はAndroidビルドに依存するため、失敗をもって起動固定を断定しない。

## 参考資料

- [Android MediaStore Intent](https://developer.android.com/reference/android/provider/MediaStore)
- [Android SDK adb](https://developer.android.com/tools/adb)
- [AOSP SystemUI カメラジェスチャー](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/packages/SystemUI/docs/camera.md)
