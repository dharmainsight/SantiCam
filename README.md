# SantiCam

Pixel 9a向けカメラアプリの保管・調査用リポジトリです。現行アプリの表示名は「SantiCam」で、Androidパッケージ名は `jp.dharmainsight.shizukacamera` です。

## 1.4.0：QRコードの自動認識

- カメラプレビューにQRコードが映ると、URL（HTTP/HTTPSのみ）を自動で検出し、映像内にリンク先を表示。
- 「開く」でブラウザに移動、「コピー」でURLをコピー。誤操作・不審なリンクに備えて自動では開きません。
- カメラのY（輝度）画像を約450ms間隔で縮小してバックグラウンド解析。写真撮影・録画・ズームの既存動作を維持。
- 録画中およびロック画面のセキュア起動中はQR読み取りを停止。コードが見えなくなったら約3秒後に表示を消去。
- [ZXing Core 3.5.4](https://central.sonatype.com/artifact/com.google.zxing/core/3.5.4)（Apache-2.0）を使用。ビルド時にMaven Centralから取得してSHA-1を照合します。APKに通信権限を追加せず、読み取り時の通信も行いません。
- Androidバージョン：versionCode 5／versionName 1.4.0。アプリIDと元の署名鍵は変更しません。

## 1.3.0：撮影操作

- ランチャー・ヘッダー・診断画面の表示名をSantiCamに統一。
- 倍率ボタンをタップして1×／2×／4×、左右へスライドして連続ズーム。スライダーでも調整可能。
- シャッター短押しで写真。長押しで動画を開始し、押したまま上で拡大・下で縮小、指を離すと終了・保存。
- 初回の動画撮影時にマイク権限を確認。許可後はもう一度長押し。未許可時・ロック画面で未許可の場合は無音動画。
- 動画は対応サイズから最大1080pを選択、MP4/H.264でDCIM/SantiCamへ保存。新しい写真も同フォルダへ保存。
- アプリを離れると録画終了。準備中のキャンセルや保存できない短すぎる録画は破棄して通知。
- TalkBackの長押し操作でも動画開始、クリックで終了。

更新時のアプリIDと署名鍵は従来と同じ。versionCode 4、versionName 1.3.0。1.2.0の診断機能を維持。

### 検証・配布

`bash test.sh`で既存45件とズーム計算1010件を検証。Androidビルドは既存CIで確認。
実機では短押し／長押し、準備直後の解放、上下ズーム、マイク許可・拒否、ホーム移動、動画再生、セキュア起動を確認すること。
更新APKは`bash build.sh`で元の非公開署名鍵を使って生成。未署名CI成果物はインストール不可。

## 署名済みAPKの生成とPixel 9aへの上書き更新

**更新用APKはGitHub Actionsの手動実行で生成します。** 1.4.0のQR機能も同じ手順で配布します。 通常の `Android compile and unit tests` が作る未署名APKはインストールできません。既存1.2.0を更新するには、旧版と同じ署名鍵が必須です。

1. 最新コードと `.github/workflows/signed-update.yml` を `main` にマージしておきます。
2. GitHubのリポジトリ **Settings → Secrets and variables → Actions → New repository secret** で、次の2つを登録します（秘密鍵やパスワードはGit/issue/チャットに貼らないこと）。
   - `SANTICAM_KEYSTORE_B64`：以前の更新で使った **元の** `shizuka-release.p12` のBase64文字列。Linuxなら `base64 -w0 shizuka-release.p12`、PowerShellなら `[Convert]::ToBase64String([IO.File]::ReadAllBytes('shizuka-release.p12'))` で生成。
   - `SANTICAM_KEYSTORE_PASSWORD`：以前の `key.pass` の内容（パスワードそのもの）。
3. GitHub **Actions → Build signed Android update APK → Run workflow → Branch: main → Run workflow** を選びます。
4. 実行が成功したら、その実行画面の **Artifacts → `SantiCam-1.4.0-signed-update`** をダウンロード。ZIPを解凍し、中の `SantiCam-1.4.0.apk` をPixelへ保存します（新しい版ではファイル名が自動更新されます）。
5. Pixelの「Files」からAPKを開いて **更新／インストール** を選びます。**旧版をアンインストールしないでください。** 未知のアプリのインストール許可を求められた場合は、信頼できる取得元であることを確認して許可します。

署名ジョブは `main` の手動実行に限定し、PRには鍵を渡しません。ジョブ内の秘密鍵は一時領域に復元して終了時に削除します。元の署名証明書SHA-256（`2048e9fb...`）およびパッケージID・versionCodeを検証し、不一致ならアップロードを中止します。APKはActions成果物に30日間保管されますが、Gitの履歴には含めません。

ローカルで署名する場合はAndroid SDK 36、Build-Tools 36.0.0、元の署名鍵バックアップを準備し、`SHIZUKA_SIGNING_DIR=/path/to/private-signing bash build.sh` を実行してください。署名鍵バックアップが失われた場合、現行アプリをそのまま上書き更新できません。新しい鍵を無断で作って置き換えないでください。

**1.3.0のPixel実機動作（録画、ズーム、マイク、保存、ロック画面）は未検証です。** APK生成とインストール可否とは別に実機で確認します。
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

暫定の確実な代替操作は、Pixelの「背面クイックタップ → アプリを開く → SantiCam」です。これは電源ボタン2回押しとは異なります。

## 次の検証

1. Pixelカメラを有効に戻し、ジェスチャー設定を「カメラ」に設定。
2. [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) を導入し、Pixel 9aのUSBデバッグを許可。
3. 接続状態を確認後、`scripts/diagnose-pixel-camera.sh`（Windowsなら `.ps1`）を実行。
4. 端末のロックを解除してから `--launch-test`（PowerShellでは `-RunLaunchTest`）を指定し、SantiCamへのIntent個別起動を検査。
5. 起動候補、個別起動、端末側のジェスチャー起動先の結果から修正可否を決める。

**端末の動作は未検証です。** ADBスクリプトの照会コマンドも端末ビルドによって結果が異なることがあります。
