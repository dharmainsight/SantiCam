# Pixel 9a: 電源ボタン2回押しの切り分け

## 再現条件（2026-11-02時点）
- Pixel 9a / Android 17。しずかカメラ 1.1.0 は通常起動・撮影可能。
- 「設定 → システム → ジェスチャー → 電源ボタンを2回押す」は「カメラ」。
- Pixelカメラを有効にすると純正カメラが起動。
- Pixelカメラを無効にすると画面が消え、自作カメラは起動しない。
- 自作カメラ選択画面は現れない。

これらは実機ユーザーからの報告であり、**原因確定ではない**。

## Android側の構造（一次情報）

AOSPの[カメラジェスチャー設計資料](https://android.googlesource.com/platform/frameworks/base/+/ea75bd1d3ca9/packages/SystemUI/docs/camera.md)によると、電源ボタン2回押しは一般アプリが直接受け取るキーイベントではなく、システムの `GestureLauncherService` → `SystemUI` がカメラ起動Intentを発行する。

- ロック解除中：`android.media.action.STILL_IMAGE_CAMERA`
- セキュアロック中：主に `android.media.action.STILL_IMAGE_CAMERA_SECURE`
- Android 12以降、`config_cameraGesturePackage` が空でなければ、非セキュアIntentにパッケージ名を指定できる。その場合、サードパーティーアプリにIntentフィルタを追加しても選択されない可能性がある。
- ただし、**Pixel 9a実機の当該リソース値は未確認**。端末固有の固定起動を確定事実とはしない。

[Google公式Pixelヘルプ](https://support.google.com/pixelphone/answer/7443425?hl=ja)で一般ユーザーが選べる電源ボタン2回押しの操作は「カメラ」と「ウォレット」。任意のアプリを直接割り当てる公式UIは案内されていない。

## ADBで切り分ける

先にPixelカメラを有効へ戻し、USBデバッグの許可済み端末でリポジトリ内の `scripts/diagnose-pixel-camera.sh`（Windowsは `.ps1`）を実行する。

1. インストール済みパッケージを確認（`adb shell pm path jp.dharmainsight.shizukacamera`）。
2. 2種類のIntentに対する候補を照会する。
3. `config_cameraGesturePackage` の値をSystemUIリソースから照会する（取得不可のビルドもある）。
4. **ロック解除後だけ** `--launch-test` / `-RunLaunchTest` を追加し、自作アプリの通常Intent起動可否を確認する。
5. 実際の電源ボタン2回押しを、ロック解除中・セキュアロック中で分けて再現する。

| 観測 | 示唆する原因・次の確認 |
| --- | --- |
| 自作アプリがIntent候補に出ない | インストール状態、Manifest、exported、フィルタを確認 |
| 候補に出るがIntent直接起動に失敗 | Activity実装・権限・クラッシュログを確認 |
| Intent直接起動は成功、ボタン2回押しだけ失敗 | SystemUIの起動先制限／既定解決を優先的に調べる |
| リソース値が純正パッケージ名 | 固定起動の有力な証拠。一般アプリだけでは変更できない可能性が高い |
| リソース値が空／取得不可 | 制限がない証明にはならない。他の経路・ログも確認 |

**安全上の注意：** `adb shell pm disable-user` によるシステムカメラ無効化や、非公式のSystemUI変更は診断に不要。診断用スクリプトは設定変更をしない。ロック中の起動は写真の閲覧制限など別の安全設計が必要。

## 実用的な代替手段

[Pixelの「クイックタップ」](https://support.google.com/pixelphone/answer/7443425?hl=ja)は端末背面を2回タップした際に指定アプリを開ける。設定 → システム → ジェスチャー → クイックタップ → アプリを開く → しずかカメラ、で代替できる。**電源ボタン2回押しではなく背面2回タップ**の操作となる点に注意。

## 次の開発タスク

- 元の1.1.0 Javaソースは `src/`、`res/`、`AndroidManifest.xml`、`build.sh`、`test.sh`、`tests/` に復元済み。署名用 `*.p12`・パスワードは絶対にコミットしない。
- 上記ADB結果に応じてアプリ側改修の要否を判断する。
- 修正する場合はテスト、署名互換性、更新インストール、実機起動をそれぞれ検証する。
