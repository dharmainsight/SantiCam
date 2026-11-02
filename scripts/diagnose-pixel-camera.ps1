# Read-only Pixel camera intent diagnostics. -RunLaunchTest launches ShizukaCamera while unlocked.
param([switch]$RunLaunchTest)

$Package = 'jp.dharmainsight.shizukacamera'
$CameraAction = 'android.media.action.STILL_IMAGE_CAMERA'
$SecureAction = 'android.media.action.STILL_IMAGE_CAMERA_SECURE'

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Error 'adb not found. Install Android SDK Platform Tools.'
    exit 1
}

Write-Host '=== Connected device ==='
& adb devices
Write-Host '=== OS version ==='
& adb shell getprop ro.build.version.release
& adb shell getprop ro.build.version.sdk
Write-Host '=== App installed? ==='
& adb shell pm path $Package
Write-Host '=== Camera intent candidates ==='
& adb shell cmd package query-activities --brief -a $CameraAction
Write-Host '=== Secure camera intent candidates ==='
& adb shell cmd package query-activities --brief -a $SecureAction
Write-Host '=== SystemUI camera gesture package (best-effort resource lookup) ==='
& adb shell cmd overlay lookup --user 0 com.android.systemui 'com.android.systemui:string/config_cameraGesturePackage'

if ($RunLaunchTest) {
    Write-Host '=== Launch standard camera intent (unlock phone first) ==='
    & adb shell am start -W -a $CameraAction -p $Package
} else {
    Write-Host 'To test launching the app: .\scripts\diagnose-pixel-camera.ps1 -RunLaunchTest'
}
