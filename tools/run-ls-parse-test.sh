#!/bin/bash
# 跑 ShizukuRoot.parseLsOutput 的单测（不打包进 APK）。
# 用法（项目根目录）：bash tools/run-ls-parse-test.sh
set -e
cd "$(dirname "$0")/.."
KOTLINC=/home/hatch/kotlin/kotlinc/bin/kotlinc
STDLIB=/home/hatch/kotlin/kotlinc/lib/kotlin-stdlib.jar
ANDROID_JAR=$HOME/Android/Sdk/platforms/android-34/android.jar
AARS=$PWD/build-direct/aars
CP="$ANDROID_JAR:$AARS/shizuku-api-13.1.0.jar:$AARS/shizuku-aidl-13.1.0.jar:$AARS/shizuku-shared-13.1.0.jar:$AARS/shizuku-provider-13.1.0.jar"
rm -rf /tmp/lstest && mkdir -p /tmp/lstest
"$KOTLINC" -jvm-target 17 -nowarn -cp "$CP" -d /tmp/lstest \
  app/src/main/java/com/fairuman/filemanager/ShizukuRoot.kt \
  tools/LsParseTest.kt
export JAVA_HOME="${JAVA_HOME:-$HOME/jdk17}"
"$JAVA_HOME/bin/java" -cp "/tmp/lstest:$STDLIB" LsParseTestKt
