package jp.dharmainsight.shizukacamera;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Unprivileged, on-device camera gesture investigation. No Wi-Fi, adb, or logs. */
public final class DiagnosticActivity extends Activity {
    public static final String EXTRA_SELF_TEST = "jp.dharmainsight.shizukacamera.SELF_TEST";
    private static final String PREFS = "camera_launch_diagnostics";
    private static final int BACKGROUND = 0xFF101411;
    private static final int PANEL = 0xFF202722;
    private static final int ACCENT = 0xFFCCE8A5;
    private static final int WHITE = 0xFFF1F4ED;
    private static final int MUTED = 0xFFA6B0A7;

    private LinearLayout content;
    private String report;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setDecorFitsSystemWindows(false);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.setClipToPadding(false);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets s = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(dp(18) + s.left, dp(14) + s.top, dp(18) + s.right, dp(16) + s.bottom);
            return insets;
        });
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        content.removeAllViews();
        content.addView(label("電源ボタン 起動診断", 25, WHITE, true));
        content.addView(label("Pixel本体だけで確認 · Wi-Fi / PC不要", 12, MUTED, false));
        spacing(18);

        Report data = inspect();
        report = data.full;
        section("判定", data.verdict);
        section("確認結果", data.details);
        section("端末の起動先制限", data.systemUi);

        action("通常カメラIntentで直接起動を試す", () -> {
            Intent test = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            test.setPackage(getPackageName());
            test.putExtra(EXTRA_SELF_TEST, true);
            try {
                startActivity(test);
                toast("カメラが開けば成功です。戻る操作で診断画面に戻れます。");
            } catch (ActivityNotFoundException | SecurityException error) {
                toast("起動できません: " + error.getClass().getSimpleName());
            }
        });
        action("結果をコピー", () -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("SantiCam 起動診断", report));
                toast("診断結果をコピーしました");
            }
        });
        action("結果を共有", () -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, report);
            startActivity(Intent.createChooser(send, "診断結果を共有"));
        });
        action("履歴をリセットして再確認", () -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply();
            render();
        });
        action("アプリの設定を開く", () -> {
            Intent settings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
            try { startActivity(settings); } catch (ActivityNotFoundException error) { toast("設定を開けませんでした"); }
        });

        section("電源ボタン2回押しの確認手順",
            "①『履歴をリセット』してからホーム画面に戻る。\n"
          + "② 電源ボタンを2回押し、起動するアプリを見る。\n"
          + "③ しずかカメラを通常起動し、この画面に戻って『外部Intent受信』の履歴を確認する。\n"
          + "④ 結果をコピーして報告する。ロック画面では初回権限付与を行わない。\n\n"
          + "※『外部Intent受信』は他アプリからの起動も含むため、電源ボタン由来と断定できません。\n"
          + "※ OSが電源ジェスチャーを特定パッケージへ固定している場合、アプリから解除はできません。"
        );
        spacing(16);
        content.addView(label("この画面は端末情報・Intent候補だけを扱います。写真・位置情報・ログの収集や通信は行いません。", 11, MUTED, false));
    }

    private Report inspect() {
        PackageManager pm = getPackageManager();
        SharedPreferences history = getSharedPreferences(PREFS, MODE_PRIVATE);
        String normal = MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA;
        String secure = MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE;
        boolean standardRegistered = registered(pm, normal, MainActivity.class);
        boolean secureRegistered = registered(pm, secure, SecureCameraActivity.class);
        boolean selfTestReceived = history.getLong("self.normal", 0L) > 0L;
        boolean externalReceived = history.getLong("outside.normal", 0L) > 0L || history.getLong("outside.secure", 0L) > 0L;
        String verdict = DiagnosticRules.verdict(standardRegistered, secureRegistered, selfTestReceived, externalReceived);
        StringBuilder details = new StringBuilder();
        details.append("機種: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
            .append("\nAndroid: ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            .append("アプリID: ").append(getPackageName()).append("\n")
            .append("通常カメラ登録: ").append(yesNo(standardRegistered)).append("\n")
            .append("ロック画面カメラ登録: ").append(yesNo(secureRegistered)).append("\n\n")
            .append("通常Intentの既定解決先: ").append(defaultCandidate(pm, normal)).append("\n")
            .append("ロック画面Intentの既定解決先: ").append(defaultCandidate(pm, secure)).append("\n\n")
            .append("通常のカメラ候補:\n").append(candidates(pm, normal)).append("\n")
            .append("ロック画面用の候補:\n").append(candidates(pm, secure)).append("\n")
            .append("アプリ内テスト受信: ").append(when(history, "self.normal")).append("\n")
            .append("外部Intent受信（通常）: ").append(when(history, "outside.normal")).append("\n")
            .append("外部Intent受信（ロック画面）: ").append(when(history, "outside.secure"));
        String systemUi = systemUiResource(pm);
        String full = "SantiCam 1.2.0 / Pixel起動診断\n\n判定: " + verdict
            + "\n\n" + details + "\n\nSystemUI: " + systemUi
            + "\n\n※ 通常アプリでは電源キーイベントの発生やSystemUI内部設定の全体は確定できません。";
        return new Report(verdict, details.toString(), systemUi, full);
    }

    private boolean registered(PackageManager pm, String action, Class<?> activityType) {
        Intent intent = new Intent(action);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        List<ResolveInfo> matches = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo item : matches) {
            if (item.activityInfo != null && getPackageName().equals(item.activityInfo.packageName)
                    && activityType.getName().equals(item.activityInfo.name)
                    && item.activityInfo.enabled && item.activityInfo.exported) return true;
        }
        return false;
    }

    private String candidates(PackageManager pm, String action) {
        Intent intent = new Intent(action);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        List<ResolveInfo> matches = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (matches.isEmpty()) return "  (検出されません。アプリ公開範囲による制限の可能性あり)\n";
        StringBuilder result = new StringBuilder();
        int shown = 0;
        for (ResolveInfo item : matches) {
            if (item.activityInfo == null) continue;
            ActivityInfo a = item.activityInfo;
            CharSequence label = item.loadLabel(pm);
            result.append("  • ").append(label == null ? "カメラ" : label)
                .append(" — ").append(a.packageName).append("/").append(a.name).append("\n");
            shown++;
            if (shown >= 12) {
                if (matches.size() > shown) result.append("  (他 ").append(matches.size()-shown).append(" 件)\n");
                break;
            }
        }
        return result.toString();
    }

    private String defaultCandidate(PackageManager pm, String action) {
        Intent intent = new Intent(action);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        ResolveInfo result = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (result == null || result.activityInfo == null) return "候補なし／取得不可";
        ActivityInfo activity = result.activityInfo;
        // ResolverActivity is a chooser, NOT the user-selected camera.
        if ("android".equals(activity.packageName) && activity.name.contains("ResolverActivity")) {
            return "選択画面（既定先未確定）";
        }
        return activity.packageName + "/" + activity.name + "（照会結果。電源キーの起動先とは限りません）";
    }

    private String systemUiResource(PackageManager pm) {
        // Best effort only; resource names, overlays and access may differ on Pixel builds.
        try {
            Resources res = pm.getResourcesForApplication("com.android.systemui");
            int id = res.getIdentifier("config_cameraGesturePackage", "string", "com.android.systemui");
            if (id == 0) return "SystemUIの参照用リソースが見つかりません。OS内部の固定有無は未判定。";
            String value = res.getString(id);
            if (value == null || value.trim().isEmpty()) {
                return "取得できた参照値は空欄。ただしOSの実際の起動先が自由とは断定できません。";
            }
            return "参照値: " + value + "。これは起動先制限を示唆しますが、実際のジェスチャー経路は未検証です。";
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException | SecurityException e) {
            return "SystemUIリソースを読み取れません（" + e.getClass().getSimpleName() + "）。OS内部の固定有無は未判定。";
        } catch (RuntimeException e) {
            return "SystemUIリソースを取得できません。OS内部の固定有無は未判定。";
        }
    }

    private static String when(SharedPreferences prefs, String key) {
        long timestamp = prefs.getLong(key, 0L);
        return timestamp == 0L ? "なし" : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(new Date(timestamp));
    }
    private static String yesNo(boolean yes) { return yes ? "登録を確認" : "検出されず（追加確認が必要）"; }

    private void section(String heading, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(shape(PANEL, 20));
        card.addView(label(heading, 15, ACCENT, true));
        TextView detail = label(body, 13, WHITE, false);
        detail.setPadding(0, dp(9), 0, 0);
        detail.setTextIsSelectable(true);
        card.addView(detail);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(12);
        content.addView(card, p);
    }

    private TextView action(String title, Runnable run) {
        TextView button = label(title, 14, BACKGROUND, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(15), dp(12), dp(15));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44000000), shape(ACCENT, 18), null));
        button.setOnClickListener(v -> run.run());
        button.setContentDescription(title);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(10);
        content.addView(button, p);
        return button;
    }
    private TextView label(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setLineSpacing(dp(3), 1f);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }
    private int dp(float v) { return Math.round(getResources().getDisplayMetrics().density * v); }
    private void spacing(int pixels) { content.addView(new View(this), new LinearLayout.LayoutParams(1, dp(pixels))); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }

    /** Records only Intent kind and timestamp. Does not monitor the power button itself. */
    public static void recordCameraIntent(Activity activity, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        boolean secure = MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(action);
        if (!secure && !MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA.equals(action)) return;
        String source = intent.getBooleanExtra(EXTRA_SELF_TEST, false) ? "self" : "outside";
        String key = source + (secure ? ".secure" : ".normal");
        activity.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(key, System.currentTimeMillis()).apply();
    }

    private static final class Report {
        final String verdict, details, systemUi, full;
        Report(String verdict, String details, String systemUi, String full) {
            this.verdict = verdict;
            this.details = details;
            this.systemUi = systemUi;
            this.full = full;
        }
    }
}
