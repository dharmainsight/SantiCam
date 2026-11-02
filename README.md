# SantiCam

Pixel 9a向けカメラアプリの保管・調査用リポジトリです。現行アプリの表示名は「しずかカメラ（ShizukaCamera）」で、Androidパッケージ名は `jp.dharmainsight.shizukacamera` です。

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

**現時点でこのGitHubリポジトリにビルド可能なオリジナルJava/Kotlinソース、署名秘密鍵、インストール用APKは未登録です。** 今回の引き継ぎ資料は、ユーザー提供のAPKを静的解析して作成したものです。ManifestのXMLは原本のソースコードではありません。APKの所有・配布権限や署名鍵を確認した上で、ソースとバイナリを追加してください。

元のソースZIPを所有者から入手できる場合は、次のスクリプトで**ソースだけ**を取り込めます（既存ファイルを上書きしません）。

```bash
bash scripts/import-original-source.sh /path/to/ShizukaCamera-1.1.0-source.zip
bash test.sh
```

元の署名バックアップはZIPとは別に安全に保管してください。リポジトリの `.gitignore` は秘密鍵・パスワードの誤登録を防ぐための補助措置であり、Gitへの追加前にも内容を確認してください。

今後の更新版APKは同じアプリIDと同じ署名秘密鍵を使用し、バージョンコードを増やして更新インストールできる形で配布することを目標とします。

詳しい原因仮説と判断基準、設定を変更しない診断方法、クイックタップの代替手順は [docs/pixel-9a-gesture-diagnosis.md](docs/pixel-9a-gesture-diagnosis.md) を参照してください。

## 次の検証

1. Pixelカメラを有効に戻し、ジェスチャー設定を「カメラ」に設定。
2. [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) を導入し、Pixel 9aのUSBデバッグを許可。
3. 接続状態を確認後、`scripts/diagnose-pixel-camera.sh`（Windowsなら `.ps1`）を実行。
4. 端末のロックを解除してから `--launch-test`（PowerShellでは `-RunLaunchTest`）を指定し、しずかカメラへのIntent個別起動を検査。
5. 起動候補、個別起動、端末側のジェスチャー起動先の結果から修正可否を決める。

**端末の動作は未検証です。** ADBスクリプトの照会コマンドも端末ビルドによって結果が異なることがあります。
