#!/usr/bin/env bash
# Build-time only dependency: no network permission is needed in the camera APK.
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION=3.5.4
SHA1=955fcd6bcd0723ddfb8ee6ed502d5fdf0e9676a9
JAR="$PWD/build/deps/zxing-core-$VERSION.jar"
mkdir -p "$(dirname "$JAR")"
if [[ -f "$JAR" ]] && printf '%s  %s\n' "$SHA1" "$JAR" | sha1sum -c --status; then
  printf '%s\n' "$JAR"
  exit 0
fi
TMP="$JAR.download"
trap 'rm -f "$TMP"' EXIT
curl --fail --silent --show-error --location --retry 3 \
  "https://repo.maven.apache.org/maven2/com/google/zxing/core/$VERSION/core-$VERSION.jar" -o "$TMP"
printf '%s  %s\n' "$SHA1" "$TMP" | sha1sum -c --status || {
  echo "ZXing checksum mismatch; refusing to build." >&2
  exit 1
}
mv "$TMP" "$JAR"
printf '%s\n' "$JAR"
