#!/usr/bin/env bash
# Read-only Pixel camera intent diagnostics. The --launch-test flag launches ShizukaCamera while unlocked.
set -u

PACKAGE="jp.dharmainsight.shizukacamera"
CAMERA_ACTION="android.media.action.STILL_IMAGE_CAMERA"
SECURE_ACTION="android.media.action.STILL_IMAGE_CAMERA_SECURE"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found. Install Android SDK Platform Tools." >&2
  exit 1
fi

echo "=== Connected device ==="
adb devices
echo "=== OS version ==="
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
echo "=== App installed? ==="
adb shell pm path "$PACKAGE"
echo "=== Camera intent candidates ==="
adb shell cmd package query-activities --brief -a "$CAMERA_ACTION"
echo "=== Secure camera intent candidates ==="
adb shell cmd package query-activities --brief -a "$SECURE_ACTION"
echo "=== SystemUI camera gesture package (best-effort resource lookup) ==="
adb shell cmd overlay lookup --user 0 com.android.systemui com.android.systemui:string/config_cameraGesturePackage || true

if [ "${1:-}" = "--launch-test" ]; then
  echo "=== Launch standard camera intent (unlock phone first) ==="
  adb shell am start -W -a "$CAMERA_ACTION" -p "$PACKAGE"
else
  echo "To test launching the app: bash scripts/diagnose-pixel-camera.sh --launch-test"
fi
