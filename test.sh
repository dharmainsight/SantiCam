#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/tests
java com.sun.tools.javac.Main --release 8 -encoding UTF-8 -d build/tests src/jp/dharmainsight/shizukacamera/CameraMath.java src/jp/dharmainsight/shizukacamera/YuvPacking.java src/jp/dharmainsight/shizukacamera/LaunchPolicy.java src/jp/dharmainsight/shizukacamera/DiagnosticRules.java src/jp/dharmainsight/shizukacamera/GestureMath.java tests/jp/dharmainsight/shizukacamera/GestureTest.java tests/jp/dharmainsight/shizukacamera/CoreTest.java
java -cp build/tests jp.dharmainsight.shizukacamera.CoreTest

java -cp build/tests jp.dharmainsight.shizukacamera.GestureTest
