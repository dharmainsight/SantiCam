package jp.dharmainsight.shizukacamera;

/** Conservative interpretation of observations available to an unprivileged app. */
public final class DiagnosticRules {
    private DiagnosticRules() {}

    public static String verdict(boolean standardRegistered, boolean secureRegistered,
                                 boolean selfTestReceived, boolean outsideIntentReceived) {
        if (!standardRegistered) {
            return "通常カメラの候補として検出されません。Manifest・アプリの有効状態を確認してください。";
        }
        if (outsideIntentReceived) {
            return "外部からのカメラ起動Intentを受信した履歴があります。ただし電源ボタン由来とは断定できません。";
        }
        if (selfTestReceived) {
            return "アプリ内の起動テストは成功しています。電源ボタンから届かない場合、OSの起動先制限・既定アプリ設定が次の調査対象です。";
        }
        if (!secureRegistered) {
            return "通常カメラ候補には登録されていますが、ロック画面用候補が見つかりません。先に起動テストを実施してください。";
        }
        return "通常・ロック画面のカメラ候補に登録されています。起動テストと電源ボタン2回押しの再現を行ってください。";
    }
}
