package jp.dharmainsight.shizukacamera;

/** Dedicated lock-screen task; no personal-media navigation. */
public final class SecureCameraActivity extends MainActivity {
    @Override protected boolean isSecureEntry() { return true; }
}
