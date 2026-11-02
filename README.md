# SantiCam

Pixel 9a向けカメラアプリの保管・調査用リポジトリです。現行アプリの表示名は「しずかカメラ（ShizukaCamera）」で、Androidパッケージ名は `jp.dharmainsight.shizukacamera` です。

## 1.2.0：Pixel上だけで電源ジェスチャーを診断

カメラ右上の **ⓘ → 電源ボタンの起動診断** を開くと、Wi-Fi・PC・ADB不要で次の項目を確認できます。

- 通常・ロック画面カメラ起動Intentの登録状況と起動候補
- 既定のIntent解決先（実際の電源キーの起動先とは限りません）
- カメラIntentの自己テスト・外部Intent受信履歴
- 参照可能なSystemUI設定（取得不可なら未判定）
- 結果をコピー・共有、履歴をリセット

詳しくは [Pixel単体での起動診断](docs/on-device-diagnostics.md) を参照。

この機能は原因の切り分け用で、電源ボタン2回押しの起動先を強制的に変える機能ではありません。APKの更新インストールには元の署名鍵が必要です。

## 状況（2026-11-02）

- **通常の起動・撮影**：Pixel 9a実機で動作を確認。
- **電源ボタン2回押し**：未解決。Pixelカメラ有効時は純正カメラが起動し、無効時は画面が消えて何も起動しない。
- **APK 1.1.0**：通常・ロック画面用のカメラ起動IntentがAndroidManifestに宣言されている。
- **原因**：Pixel側が起動先を固定している可能性と、アプリ側の処理に問題がある可能性を切り分ける必要がある。

## 収録内容

- [docs/apk-audit.md](docs/apk-audit.md)：1.0.0 / 1.1.0の比較、ハッシュ、署名と現在の判断
- [docs/AndroidManifest-1.0.0.xml](docs/AndroidManifest-1.0.0.xml)：APKから抽出したXML
- [docs/AndroidManifest-1.1.0.xml](docs/AndroidManifest-1.1.0.xml)：APKから抽出したXML
- [scripts/diagnose-pixel-camera.sh](scripts/diagnose-pixel-camera.sh)：ADBによるPixel起動原因の診断（macOS/Linux/WSL）
- [scripts/diagnose-pixel-camera.ps1](scripts/diagnose-pixel-camera.ps1)：同内容（Windows PowerShell）

### 開発ソース・APKの扱い

**1.1.0のJavaソースを保全し、1.2.0では診断画面・Intent履歴を追加しています。** AndroidManifest.xml、`src/`、`res/`、`tests/`、`build.sh`、`test.sh` は元の1.1.0ソースに由来し、1.2.0に合わせて改修しています。元のZIPとのGit blob SHA一致を全14ファイルで確認済みです。

```bash
bash test.sh  # JDK 17等でAndroid SDK不要の単体テスト40件
```

更新APKの生成にはAndroid SDK Platform 36、Build-Tools 36.0.0、**元の署名鍵（非公開）** が必要です。端末にインストール済みの1.1.0を上書きするには元の署名鍵を使い、`AndroidManifest.xml`の`versionCode`を増やしてください。詳細は`build.sh`を参照。

**署名鍵、パスワード、APK本体は公開リポジトリに含めません。** `.gitignore`だけに頼らず、Gitへの追加前にも秘密情報がないことを確認してください。

### 電源ボタン2回押しで起動しない問題

アプリ側には通常の`STILL_IMAGE_CAMERA`とロック画面用`STILL_IMAGE_CAMERA_SECURE`のIntent登録があります。しかしSystemUI側で起動先パッケージが制限されている場合、アプリのManifest修正だけでは解決できません。

詳しい判定基準は [Pixel 9a電源ジェスチャー診断](docs/pixel-9a-gesture-diagnosis.md)。Androidの公式資料では `config_cameraGesturePackage` により起動対象が指定される場合があると説明されています。**Pixel 9a実機の設定値は未取得、原因未確定です。**

暫定の確実な代替操作は、Pixelの「背面クイックタップ → アプリを開く → しずかカメラ」です。これは電源ボタン2回押しとは異なります。

## 次の検証

1. Pixelカメラを有効に戻し、ジェスチャー設定を「カメラ」に設定。
2. [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) を導入し、Pixel 9aのUSBデバッグを許可。
3. 接続状態を確認後、`scripts/diagnose-pixel-camera.sh`（Windowsなら `.ps1`）を実行。
4. 端末のロックを解除してから `--launch-test`（PowerShellでは `-RunLaunchTest`）を指定し、しずかカメラへのIntent個別起動を検査。
5. 起動候補、個別起動、端末側のジェスチャー起動先の結果から修正可否を決める。

**端末の動作は未検証です。** ADBスクリプトの照会コマンドも端末ビルドによって結果が異なることがあります。
