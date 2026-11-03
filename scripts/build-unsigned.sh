#!/usr/bin/env bash
# Build an unsigned APK for compilation verification. It CANNOT be installed as an update.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_JAR:=${ANDROID_HOME:-}/platforms/android-36/android.jar}"
: "${ANDROID_BUILD_TOOLS:=${ANDROID_HOME:-}/build-tools/36.0.0}"
test -f "$ANDROID_JAR" || { echo "Set ANDROID_JAR or install SDK platform 36." >&2; exit 1; }
test -f "$ANDROID_BUILD_TOOLS/lib/d8.jar" || { echo "Set ANDROID_BUILD_TOOLS to build-tools 36.0.0." >&2; exit 1; }
mkdir -p build/generated build/classes build/dex dist
"$ANDROID_BUILD_TOOLS/aapt2" compile --dir res -o build/resources.zip
"$ANDROID_BUILD_TOOLS/aapt2" link -I "$ANDROID_JAR" --manifest AndroidManifest.xml --java build/generated --min-sdk-version 31 --target-sdk-version 36 -o build/resources.apk build/resources.zip
find src build/generated -name '*.java' -print > build/sources.txt
java com.sun.tools.javac.Main --release 8 -encoding UTF-8 -cp "$ANDROID_JAR" -d build/classes @build/sources.txt
java sun.tools.jar.Main cf build/classes.jar -C build/classes .
java -cp "$ANDROID_BUILD_TOOLS/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 31 --lib "$ANDROID_JAR" --output build/dex build/classes.jar
python3 - <<'PY'
from pathlib import Path
import shutil, zipfile
shutil.copyfile('build/resources.apk', 'build/unsigned.apk')
with zipfile.ZipFile('build/unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as apk:
    for path in sorted(Path('build/dex').glob('*.dex')):
        apk.write(path, path.name)
PY
"$ANDROID_BUILD_TOOLS/zipalign" -f -p 4 build/unsigned.apk dist/SantiCam-1.3.0-unsigned.apk
"$ANDROID_BUILD_TOOLS/zipalign" -c -p 4 dist/SantiCam-1.3.0-unsigned.apk
sha256sum dist/SantiCam-1.3.0-unsigned.apk
