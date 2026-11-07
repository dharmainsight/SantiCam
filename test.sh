#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/tests
ZXING_JAR="$(bash scripts/fetch-zxing.sh)"
java com.sun.tools.javac.Main --release 8 -encoding UTF-8 -cp "$ZXING_JAR" -d build/tests src/jp/dharmainsight/shizukacamera/CameraMath.java src/jp/dharmainsight/shizukacamera/YuvPacking.java src/jp/dharmainsight/shizukacamera/LaunchPolicy.java src/jp/dharmainsight/shizukacamera/DiagnosticRules.java src/jp/dharmainsight/shizukacamera/GestureMath.java src/jp/dharmainsight/shizukacamera/QrFrameSampler.java src/jp/dharmainsight/shizukacamera/QrScanner.java src/jp/dharmainsight/shizukacamera/QrLinkPolicy.java tests/jp/dharmainsight/shizukacamera/GestureTest.java tests/jp/dharmainsight/shizukacamera/CoreTest.java tests/jp/dharmainsight/shizukacamera/QrScannerTest.java
java -cp "build/tests:$ZXING_JAR" jp.dharmainsight.shizukacamera.CoreTest

java -cp "build/tests:$ZXING_JAR" jp.dharmainsight.shizukacamera.GestureTest
java -cp "build/tests:$ZXING_JAR" jp.dharmainsight.shizukacamera.QrScannerTest
