package jp.dharmainsight.shizukacamera;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.provider.MediaStore;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

public class MainActivity extends Activity implements CameraEngine.Listener {
    private static final int BACKGROUND = 0xFF101411, PANEL = 0xFF202722, ACCENT = 0xFFCCE8A5, WHITE = 0xFFF1F4ED, MUTED = 0xFFA6B0A7;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private CameraEngine engine;
    private SharedPreferences prefs;
    private TextureView texture;
    private AspectStage stage;
    private FrameLayout viewfinder;
    private Overlay overlay;
    private TextView status, resolution, timerButton, gateTitle, gateBody, gateAction, evLabel;
    private LinearLayout gate, exposureRow, footer;
    private IconButton gridButton, torchButton, switchButton, galleryButton, shutter;
    private TextView[] zoomButtons;
    private SeekBar exposure;
    private OrientationEventListener orientationListener;
    private CameraEngine.Info info;
    private boolean resumed, starting, ready, front, busy, torch, grid;
    private int deviceOrientation, timerSeconds, countdownRemaining, countdownToken;
    private float zoom = 1f;
    private boolean secureSession;
    private Uri lastPhoto;
    private Bitmap lastThumbnail;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        secureSession = state != null && state.getBoolean("secureSession", false);
        refreshLaunchSecurity();
        DiagnosticActivity.recordCameraIntent(this, getIntent());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setDecorFitsSystemWindows(false);
        prefs = getSharedPreferences("camera", MODE_PRIVATE);
        grid = prefs.getBoolean("grid", false);
        String last = secureSession ? null : prefs.getString("lastPhoto", null);
        if (last != null) lastPhoto = Uri.parse(last);
        engine = new CameraEngine(this, this);
        buildUi();
        orientationListener = new OrientationEventListener(this) {
            @Override public void onOrientationChanged(int degrees) { if (degrees != ORIENTATION_UNKNOWN) deviceOrientation = degrees; }
        };
    }

    // A secure session stays restricted even after unlock. Reopen from the launcher
    // to view photos. Never share an Activity/task with the normal camera entry.
    protected boolean isSecureEntry() { return false; }

    private boolean protectPersonalContent() {
        refreshLaunchSecurity();
        return secureSession;
    }

    private void refreshLaunchSecurity() {
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        boolean locked = keyguard == null || keyguard.isKeyguardLocked();
        String action = getIntent() == null ? null : getIntent().getAction();
        secureSession = LaunchPolicy.restricted(secureSession, isSecureEntry(), action, locked);
        if (secureSession) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            lastPhoto = null;
            if (lastThumbnail != null) { lastThumbnail.recycle(); lastThumbnail = null; }
            if (galleryButton != null) { galleryButton.setVisibility(View.INVISIBLE); galleryButton.invalidate(); }
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("secureSession", secureSession);
        super.onSaveInstanceState(out);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        refreshLaunchSecurity();
        DiagnosticActivity.recordCameraIntent(this, intent);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BACKGROUND);
        root.setPadding(dp(20), 0, dp(20), 0);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(dp(20) + i.left, i.top, dp(20) + i.right, i.bottom + dp(6)); return insets;
        });
        setContentView(root);

        LinearLayout header = row(); header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleStack = new LinearLayout(this); titleStack.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("しずか", 27, WHITE); title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        TextView subtitle = text("S I L E N T   C A M E R A", 9, MUTED);
        titleStack.addView(title); titleStack.addView(subtitle);
        header.addView(titleStack, new LinearLayout.LayoutParams(0, -2, 1));
        TextView silent = text("●  無音", 12, ACCENT); silent.setPadding(dp(13), dp(8), dp(13), dp(8)); silent.setBackground(shape(0xFF283124, 24));
        header.addView(silent);
        IconButton about = new IconButton("info", "アプリ情報"); about.setOnClickListener(v -> showAbout());
        header.addView(about, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(74)));

        LinearLayout toolbar = row(); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        torchButton = new IconButton("flash", "ライトを点ける");
        torchButton.setOnClickListener(v -> { if (!ready || busy) return; torch = !torch; torchButton.active = torch; torchButton.invalidate(); torchButton.setContentDescription(torch ? "ライトを消す" : "ライトを点ける"); engine.setTorch(torch); });
        toolbar.addView(torchButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        timerButton = chip("タイマー OFF"); timerButton.setContentDescription("撮影タイマーを切り替え");
        timerButton.setOnClickListener(v -> { if (busy) return; timerSeconds = timerSeconds == 0 ? 3 : timerSeconds == 3 ? 10 : 0; timerButton.setText(timerSeconds == 0 ? "タイマー OFF" : timerSeconds + " 秒"); timerButton.setTextColor(timerSeconds == 0 ? MUTED : ACCENT); });
        toolbar.addView(timerButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        gridButton = new IconButton("grid", "グリッドを切り替え"); gridButton.active = grid;
        gridButton.setOnClickListener(v -> { grid = !grid; gridButton.active = grid; gridButton.invalidate(); overlay.invalidate(); prefs.edit().putBoolean("grid", grid).apply(); });
        toolbar.addView(gridButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(52)));

        stage = new AspectStage();
        viewfinder = new FrameLayout(this); viewfinder.setBackground(shape(0xFF080B09, 24)); viewfinder.setClipToOutline(true);
        texture = new TextureView(this); viewfinder.addView(texture, new FrameLayout.LayoutParams(-1, -1));
        overlay = new Overlay(); viewfinder.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        resolution = text("", 10, WHITE); resolution.setPadding(dp(12), dp(7), dp(12), dp(7)); resolution.setBackground(shape(0x990C100D, 16));
        FrameLayout.LayoutParams resolutionParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START); resolutionParams.setMargins(dp(14), dp(14), 0, 0); viewfinder.addView(resolution, resolutionParams);
        gate = new LinearLayout(this); gate.setOrientation(LinearLayout.VERTICAL); gate.setGravity(Gravity.CENTER); gate.setPadding(dp(24), dp(12), dp(24), dp(12)); gate.setBackgroundColor(0xF5101411);
        gateTitle = text("カメラを準備しています", 18, WHITE); gateTitle.setGravity(Gravity.CENTER);
        gateBody = text("", 13, MUTED); gateBody.setGravity(Gravity.CENTER); gateBody.setPadding(0, dp(12), 0, dp(18));
        gateAction = chip("再接続"); gateAction.setTextColor(BACKGROUND); gateAction.setBackground(ripple(ACCENT, 24)); gateAction.setPadding(dp(28), 0, dp(28), 0);
        gate.addView(gateTitle); gate.addView(gateBody); gate.addView(gateAction, new LinearLayout.LayoutParams(-2, dp(48)));
        gateAction.setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) requestCameraPermission();
            else { starting = false; tryStart(); }
        });
        viewfinder.addView(gate, new FrameLayout.LayoutParams(-1, -1));
        stage.addView(viewfinder); root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { tryStart(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) { transformPreview(); }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { starting = ready = false; engine.stop(); return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture s) {}
        });
        ScaleGestureDetector scale = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) { setZoom(zoom * detector.getScaleFactor()); return true; }
        });
        texture.setOnTouchListener(new View.OnTouchListener() {
            float startX, startY; long downTime; boolean multiple;
            @Override public boolean onTouch(View v, MotionEvent e) {
                scale.onTouchEvent(e);
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { startX = e.getX(); startY = e.getY(); downTime = e.getEventTime(); multiple = false; }
                if (e.getPointerCount() > 1) multiple = true;
                if (e.getActionMasked() == MotionEvent.ACTION_UP && !multiple && ready && !busy && e.getEventTime() - downTime < 600 && Math.hypot(e.getX() - startX, e.getY() - startY) < dp(20)) {
                    v.performClick();
                    engine.focus(e.getX() / v.getWidth(), e.getY() / v.getHeight());
                    overlay.focusX = e.getX(); overlay.focusY = e.getY(); overlay.focusUntil = System.currentTimeMillis() + 1200; overlay.invalidate();
                    ui.postDelayed(() -> overlay.invalidate(), 1250);
                }
                return true;
            }
        });

        LinearLayout zoomRow = row(); zoomRow.setGravity(Gravity.CENTER);
        zoomButtons = new TextView[3]; float[] factors = {1, 2, 4};
        for (int i = 0; i < 3; i++) {
            float value = factors[i]; TextView button = chip((int) value + "×"); zoomButtons[i] = button;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(60), dp(42)); p.setMargins(dp(4), 0, dp(4), 0); zoomRow.addView(button, p);
            button.setOnClickListener(v -> { if (!busy) setZoom(value); }); button.setContentDescription((int) value + "倍ズーム");
        }
        root.addView(zoomRow, new LinearLayout.LayoutParams(-1, dp(58)));

        exposureRow = row(); exposureRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView brightness = text("明るさ", 11, MUTED); exposureRow.addView(brightness);
        exposure = new SeekBar(this); exposure.setMax(100); exposure.setProgress(50); exposure.setThumbTintList(ColorStateList.valueOf(ACCENT)); exposure.setProgressTintList(ColorStateList.valueOf(ACCENT)); exposure.setContentDescription("写真の明るさ");
        exposureRow.addView(exposure, new LinearLayout.LayoutParams(0, dp(40), 1));
        evLabel = text("0.0", 11, MUTED); evLabel.setGravity(Gravity.END); exposureRow.addView(evLabel, new LinearLayout.LayoutParams(dp(36), -2));
        exposure.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int progress, boolean user) {
                if (!user || info == null || !ready) return;
                float ev = info.minEv + (info.maxEv - info.minEv) * progress / 100f;
                evLabel.setText(String.format(Locale.US, "%+.1f", ev)); engine.setExposure(ev);
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        root.addView(exposureRow, new LinearLayout.LayoutParams(-1, dp(40)));

        LinearLayout controls = row(); controls.setGravity(Gravity.CENTER);
        galleryButton = new IconButton("gallery", "最後に撮った写真を開く"); galleryButton.setOnClickListener(v -> openPhoto());
        controls.addView(galleryButton, new LinearLayout.LayoutParams(dp(58), dp(58)));
        View leftSpace = new View(this); controls.addView(leftSpace, new LinearLayout.LayoutParams(0, 1, 1));
        shutter = new IconButton("shutter", "写真を撮る"); shutter.setOnClickListener(v -> capture());
        controls.addView(shutter, new LinearLayout.LayoutParams(dp(90), dp(90)));
        View rightSpace = new View(this); controls.addView(rightSpace, new LinearLayout.LayoutParams(0, 1, 1));
        switchButton = new IconButton("switch", "前後カメラを切り替え"); switchButton.setOnClickListener(v -> {
            if (!ready || busy) return; front = !front; ready = false; starting = false; tryStart();
        }); controls.addView(switchButton, new LinearLayout.LayoutParams(dp(58), dp(58)));
        controls.setPadding(dp(12), 0, dp(12), 0); root.addView(controls, new LinearLayout.LayoutParams(-1, dp(106)));

        footer = new LinearLayout(this); footer.setOrientation(LinearLayout.VERTICAL); footer.setGravity(Gravity.CENTER);
        status = text("音を立てずに、そのまま残す。", 12, MUTED); status.setGravity(Gravity.CENTER); footer.addView(status);
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(30)));
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            boolean compact = b - t - root.getPaddingTop() - root.getPaddingBottom() < dp(620);
            int visibility = compact ? View.GONE : View.VISIBLE;
            if (exposureRow.getVisibility() != visibility) { exposureRow.setVisibility(visibility); footer.setVisibility(visibility); }
        });
        updateControls();
    }

    @Override protected void onResume() {
        super.onResume(); refreshLaunchSecurity(); resumed = true; starting = false; orientationListener.enable(); tryStart();
    }
    @Override protected void onPause() {
        resumed = false; ready = starting = false; cancelCountdown(); orientationListener.disable(); engine.stop(); updateControls(); super.onPause();
        if (secureSession) finish();
    }
    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null); engine.destroy();
        if (lastThumbnail != null) lastThumbnail.recycle(); super.onDestroy();
    }
    @Override public void onConfigurationChanged(Configuration config) { super.onConfigurationChanged(config); transformPreview(); stage.requestLayout(); }

    private void tryStart() {
        if (!resumed || starting) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (secureSession) {
                showGate("最初にロックを解除してください", "ホーム画面からしずかカメラを開き、カメラの使用を許可してください。", null); return;
            }
            showGate("カメラを使えるようにする", "撮影のため、カメラの使用を許可してください。\n写真は端末内に保存されます。", "カメラを許可"); return;
        }
        if (!texture.isAvailable()) return;
        starting = true; ready = false; torch = false; zoom = 1;
        torchButton.active = false; torchButton.invalidate(); updateControls();
        showGate("カメラを準備しています", "", null);
        engine.start(texture.getSurfaceTexture(), front);
    }

    private void requestCameraPermission() {
        if (protectPersonalContent()) return;
        if (prefs.getBoolean("permissionAsked", false) && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); return;
        }
        prefs.edit().putBoolean("permissionAsked", true).apply();
        requestPermissions(new String[] {Manifest.permission.CAMERA}, 10);
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == 10) {
            starting = false;
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) tryStart();
            else showGate("カメラの許可が必要です", "カメラの権限を許可すると撮影できます。", shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ? "カメラを許可" : "設定を開く");
        }
    }

    @Override public void onReady(CameraEngine.Info value) {
        if (!resumed || isDestroyed()) return;
        info = value; front = value.front; ready = true; gate.setVisibility(View.GONE);
        resolution.setText(String.format(Locale.US, "%.1f MP  ·  %d:%d", value.width * value.height / 1000000f,
            value.width / gcd(value.width, value.height), value.height / gcd(value.width, value.height)));
        float ratio = (float) value.width / value.height;
        stage.aspect = value.sensorRotation % 180 == 0 ? ratio : 1 / ratio; stage.requestLayout();
        exposure.setProgress(value.maxEv == value.minEv ? 50 : Math.round(-value.minEv / (value.maxEv - value.minEv) * 100));
        evLabel.setText("0.0"); status.setText("タップでピント · 音量キーでも撮影");
        transformPreview(); updateControls();
    }

    @Override public void onStatus(String message, boolean error) {
        if (!resumed || isDestroyed()) return;
        if (error) { ready = starting = false; cancelCountdown(); busy = false; }
        if (!ready || error) showGate(error ? "カメラを確認してください" : message, error ? message : "", error ? "再接続" : null);
        status.setText(message); updateControls();
    }

    @Override public void onPhoto(Uri uri, Bitmap thumbnail, String error) {
        if (isDestroyed()) { if (thumbnail != null) thumbnail.recycle(); return; }
        busy = false; updateControls();
        if (error != null) { if (resumed) toast(error); status.setText(error); return; }
        if (uri == null) return;
        if (protectPersonalContent()) {
            if (thumbnail != null) thumbnail.recycle();
        } else {
            lastPhoto = uri;
            if (lastThumbnail != null) lastThumbnail.recycle(); lastThumbnail = thumbnail; galleryButton.invalidate();
        }
        status.setText("保存しました · DCIM / ShizukaCamera");
        if (resumed) { overlay.savedUntil = System.currentTimeMillis() + 220; overlay.invalidate(); ui.postDelayed(() -> overlay.invalidate(), 250); }
    }

    private void capture() {
        if (countdownRemaining > 0) { cancelCountdown(); return; }
        if (!ready || busy) return;
        busy = true; countdownRemaining = timerSeconds; updateControls();
        if (timerSeconds == 0) takeNow();
        else { countdownRemaining = timerSeconds; int token = ++countdownToken; tick(token); }
    }
    private void tick(int token) {
        if (token != countdownToken || !resumed) return;
        overlay.countdown = countdownRemaining; overlay.invalidate();
        if (countdownRemaining == 0) { takeNow(); return; }
        status.setText("あと " + countdownRemaining + " 秒 · 撮影ボタンで中止");
        ui.postDelayed(() -> { if (token == countdownToken) { countdownRemaining--; tick(token); } }, 1000);
    }
    private void takeNow() {
        overlay.countdown = 0; overlay.invalidate(); updateControls(); status.setText("保存しています…"); engine.shoot(deviceOrientation);
    }
    private void cancelCountdown() {
        ++countdownToken;
        if (countdownRemaining > 0) { busy = false; status.setText("タイマーを中止しました"); }
        countdownRemaining = 0; if (overlay != null) { overlay.countdown = 0; overlay.invalidate(); } updateControls();
    }
    private void setZoom(float value) {
        if (!ready || info == null || busy) return;
        zoom = CameraMath.clamp(value, 1, info.maxZoom); engine.setZoom(zoom); updateControls();
    }
    private void updateControls() {
        if (shutter == null) return;
        galleryButton.setVisibility(secureSession ? View.INVISIBLE : View.VISIBLE);
        shutter.setEnabled(ready && (!busy || countdownRemaining > 0));
        shutter.busy = busy; shutter.invalidate();
        switchButton.setEnabled(ready && !busy && info != null && info.switchable);
        torchButton.setEnabled(ready && !busy && info != null && info.flash);
        exposure.setEnabled(ready && !busy && info != null && info.maxEv > info.minEv);
        timerButton.setEnabled(!busy);
        if (zoomButtons != null) for (int i = 0; i < zoomButtons.length; i++) {
            float value = 1 << i; boolean active = Math.abs(zoom - value) < .04;
            TextView b = zoomButtons[i]; b.setEnabled(ready && !busy && info != null && info.maxZoom >= value);
            b.setTextColor(active ? BACKGROUND : MUTED); b.setBackground(ripple(active ? ACCENT : PANEL, 24));
            b.setAlpha(b.isEnabled() ? 1 : .38f);
            if (i == 0) b.setText(Math.abs(zoom - 1) > .04 && Math.abs(zoom - 2) > .04 && Math.abs(zoom - 4) > .04 ? String.format(Locale.US, "%.1f×", zoom) : "1×");
        }
    }

    private void openPhoto() {
        if (protectPersonalContent()) { toast("写真を見るにはロックを解除し、ホーム画面から開き直してください"); return; }
        if (busy) return;
        if (lastPhoto == null) { toast("撮影した写真がここに表示されます"); return; }
        Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(lastPhoto, "image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(intent); } catch (ActivityNotFoundException | SecurityException e) { toast("フォトアプリで DCIM / ShizukaCamera を開いてください"); }
    }
    private void showAbout() {
        if (protectPersonalContent()) return;
        new AlertDialog.Builder(this).setTitle("しずかカメラ 1.2.0")
            .setMessage("シャッター音なしで写真を残すカメラです。\n\n・撮影中の映像から写真を保存\n・保存先：DCIM / ShizukaCamera\n・前後カメラ、ズーム、明るさ、タイマー\n・音量キーでも撮影できます\n・自撮り写真は左右反転せず保存\n\n通信・広告・マイク・位置情報は使用しません。画面を離れるとカメラは停止します。\n\n画質と最大解像度は端末により異なります。標準カメラのHDR・夜景処理には対応していません。端末固有の動作音については実機で確認してください。")
            .setNeutralButton("電源ボタンの起動診断", (dialog, which) -> {
                if (!protectPersonalContent()) startActivity(new Intent(this, DiagnosticActivity.class));
            })
            .setPositiveButton("閉じる", null).show();
    }
    private void showGate(String title, String body, String action) {
        gateTitle.setText(title); gateBody.setText(body); gateBody.setVisibility(body.isEmpty() ? View.GONE : View.VISIBLE);
        gateAction.setVisibility(action == null ? View.GONE : View.VISIBLE); if (action != null) gateAction.setText(action); gate.setVisibility(View.VISIBLE);
    }
    private void transformPreview() {
        if (texture == null || texture.getWidth() == 0) return;
        int rotation = getDisplay() == null ? 0 : getDisplay().getRotation();
        float w = texture.getWidth(), h = texture.getHeight();
        Matrix m = new Matrix();
        if (rotation == 1 || rotation == 3) { m.postRotate(-90 * rotation, w / 2, h / 2); m.postScale(w / h, h / w, w / 2, h / 2); }
        else if (rotation == 2) m.postRotate(180, w / 2, h / 2);
        texture.setTransform(m);
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_CAMERA) {
            if (event.getRepeatCount() == 0) capture(); return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_CAMERA) return true;
        return super.onKeyUp(keyCode, event);
    }

    private int dp(float v) { return Math.round(getResources().getDisplayMetrics().density * v); }
    private static int gcd(int a, int b) { while (b != 0) { int r = a % b; a = b; b = r; } return a; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private TextView text(String value, float size, int color) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setFontFeatureSettings("tnum"); return t; }
    private TextView chip(String value) { TextView t = text(value, 13, MUTED); t.setGravity(Gravity.CENTER); t.setBackground(ripple(PANEL, 24)); t.setSoundEffectsEnabled(false); t.setHapticFeedbackEnabled(false); return t; }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private RippleDrawable ripple(int color, int radius) { return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape(color, radius), shape(Color.WHITE, radius)); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    private final class AspectStage extends FrameLayout {
        float aspect = 3f / 4;
        AspectStage() { super(MainActivity.this); }
        @Override protected void onMeasure(int ws, int hs) {
            int width = MeasureSpec.getSize(ws), height = MeasureSpec.getSize(hs);
            float ratio = aspect;
            int rotation = getDisplay() == null ? 0 : getDisplay().getRotation();
            if (rotation == 1 || rotation == 3) ratio = 1 / ratio;
            int childW = Math.min(width, Math.round(height * ratio));
            int childH = Math.min(height, Math.round(childW / ratio));
            if (getChildCount() > 0) getChildAt(0).measure(MeasureSpec.makeMeasureSpec(Math.max(1, childW), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.max(1, childH), MeasureSpec.EXACTLY));
            setMeasuredDimension(width, height);
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            View v = getChildAt(0); int x = (r - l - v.getMeasuredWidth()) / 2, y = (b - t - v.getMeasuredHeight()) / 2;
            v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
        }
    }

    private final class Overlay extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float focusX, focusY; long focusUntil, savedUntil; int countdown;
        Overlay() { super(MainActivity.this); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (grid) { p.setColor(0x66FFFFFF); p.setStrokeWidth(dp(.7f)); for (int i = 1; i < 3; i++) { c.drawLine(w * i / 3, 0, w * i / 3, h, p); c.drawLine(0, h * i / 3, w, h * i / 3, p); } }
            if (System.currentTimeMillis() < focusUntil) { p.setColor(ACCENT); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.5f)); float d = dp(27); c.drawRoundRect(focusX - d, focusY - d, focusX + d, focusY + d, dp(9), dp(9), p); p.setStyle(Paint.Style.FILL); }
            if (System.currentTimeMillis() < savedUntil) c.drawColor(0x32CCE8A5);
            if (countdown > 0) { c.drawColor(0x33000000); p.setColor(WHITE); p.setTextSize(dp(88)); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL)); c.drawText(Integer.toString(countdown), w / 2, h / 2 - (p.ascent() + p.descent()) / 2, p); }
        }
    }

    private final class IconButton extends View {
        final String icon; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); boolean active, busy;
        IconButton(String icon, String description) {
            super(MainActivity.this); this.icon = icon; setContentDescription(description); setClickable(true); setFocusable(true);
            setSoundEffectsEnabled(false); setHapticFeedbackEnabled(false); setBackground(ripple(icon.equals("shutter") ? Color.TRANSPARENT : PANEL, 50));
        }
        @Override public void setEnabled(boolean enabled) { super.setEnabled(enabled); setAlpha(enabled ? 1f : .38f); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); float w = getWidth(), h = getHeight();
            if (icon.equals("gallery") && !secureSession && lastThumbnail != null && !lastThumbnail.isRecycled()) {
                int save = canvas.save(); Path path = new Path(); path.addRoundRect(new RectF(dp(5), dp(5), w - dp(5), h - dp(5)), dp(15), dp(15), Path.Direction.CW); canvas.clipPath(path);
                float scale = Math.max((w - dp(10)) / lastThumbnail.getWidth(), (h - dp(10)) / lastThumbnail.getHeight());
                float tw = lastThumbnail.getWidth() * scale, th = lastThumbnail.getHeight() * scale;
                p.setFilterBitmap(true); canvas.drawBitmap(lastThumbnail, null, new RectF((w - tw) / 2, (h - th) / 2, (w + tw) / 2, (h + th) / 2), p); canvas.restoreToCount(save); return;
            }
            if (icon.equals("shutter")) {
                p.setColor(busy ? ACCENT : WHITE); p.setStrokeWidth(dp(3)); p.setStyle(Paint.Style.STROKE); canvas.drawCircle(w / 2, h / 2, Math.min(w, h) / 2 - dp(5), p); p.setStyle(Paint.Style.FILL);
                if (busy) canvas.drawRoundRect(w / 2 - dp(13), h / 2 - dp(13), w / 2 + dp(13), h / 2 + dp(13), dp(5), dp(5), p);
                else canvas.drawCircle(w / 2, h / 2, Math.min(w, h) / 2 - dp(12), p); return;
            }
            canvas.save(); canvas.translate(w / 2 - dp(12), h / 2 - dp(12)); canvas.scale(dp(24) / 24f, dp(24) / 24f);
            p.setColor(active ? ACCENT : WHITE); p.setStrokeWidth(1.65f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setStyle(Paint.Style.STROKE);
            if (icon.equals("info")) { canvas.drawCircle(12, 12, 9, p); canvas.drawLine(12, 11, 12, 17, p); canvas.drawPoint(12, 7, p); }
            else if (icon.equals("grid")) { canvas.drawRoundRect(3, 3, 21, 21, 3, 3, p); for (int i : new int[] {9, 15}) { canvas.drawLine(i, 3, i, 21, p); canvas.drawLine(3, i, 21, i, p); } }
            else if (icon.equals("flash")) { Path path = new Path(); path.moveTo(14, 2); path.lineTo(5, 13); path.lineTo(11, 13); path.lineTo(10, 22); path.lineTo(19, 10); path.lineTo(13, 10); path.close(); canvas.drawPath(path, p); if (!active) canvas.drawLine(3, 3, 21, 21, p); }
            else if (icon.equals("switch")) {
                canvas.drawArc(3, 3, 21, 21, 205, 145, false, p); canvas.drawArc(3, 3, 21, 21, 25, 145, false, p);
                canvas.drawLine(21, 6, 21, 11, p); canvas.drawLine(21, 11, 16, 11, p); canvas.drawLine(3, 18, 3, 13, p); canvas.drawLine(3, 13, 8, 13, p);
            } else if (icon.equals("gallery")) { canvas.drawRoundRect(3, 3, 21, 21, 4, 4, p); canvas.drawCircle(8, 8, 1.4f, p); Path path = new Path(); path.moveTo(4, 17); path.lineTo(10, 11); path.lineTo(15, 16); path.lineTo(18, 13); path.lineTo(21, 16); canvas.drawPath(path, p); }
            p.setStyle(Paint.Style.FILL); canvas.restore();
        }
    }
}
