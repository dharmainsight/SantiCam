package jp.dharmainsight.shizukacamera;

final class LaunchPolicy {
    static boolean restricted(boolean alreadyRestricted, boolean secureEntry, String action, boolean locked) {
        return alreadyRestricted || secureEntry || locked
            || "android.media.action.STILL_IMAGE_CAMERA_SECURE".equals(action);
    }
}
