#!/usr/bin/env bash
# Imports the original, owner-supplied ShizukaCamera 1.1.0 source archive.
# Deliberately never reads, imports or commits signing material.
set -euo pipefail

if [[ $# -ne 1 || ! -f "$1" ]]; then
  echo "Usage: bash scripts/import-original-source.sh /path/to/ShizukaCamera-1.1.0-source.zip" >&2
  exit 2
fi
SOURCE_ZIP="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT

unzip -q "$SOURCE_ZIP" -d "$TEMP"
SRC="$TEMP/ShizukaCamera"
if [[ ! -f "$SRC/AndroidManifest.xml" || ! -f "$SRC/src/jp/dharmainsight/shizukacamera/MainActivity.java" || ! -f "$SRC/test.sh" ]]; then
  echo "Unexpected archive layout: expected the original 1.1.0 source bundle." >&2
  exit 1
fi
if grep -Eq 'android:versionCode="[^"]+"' "$SRC/AndroidManifest.xml" && ! grep -q 'android:versionName="1.1.0"' "$SRC/AndroidManifest.xml"; then
  echo "Unexpected source version (expected 1.1.0)." >&2
  exit 1
fi
for p in AndroidManifest.xml build.sh test.sh res src tests; do
  if [[ -e "$ROOT/$p" ]]; then
    echo "Refusing to replace existing $p" >&2
    exit 1
  fi
done

cp "$SRC/AndroidManifest.xml" "$SRC/build.sh" "$SRC/test.sh" "$ROOT/"
cp -R "$SRC/res" "$SRC/src" "$SRC/tests" "$ROOT/"
mkdir -p "$ROOT/docs"
cp "$SRC/README.md" "$ROOT/docs/original-1.1.0-readme.md"
chmod +x "$ROOT/build.sh" "$ROOT/test.sh"

echo "Imported 1.1.0 source. Signing files were not copied."
echo "Next: cd '$ROOT' && bash test.sh"
echo "Verify the diff before git add; never commit private-signing/, *.p12 or key.pass."
