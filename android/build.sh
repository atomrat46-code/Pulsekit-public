#!/usr/bin/env bash
# Builds Pulsekit.apk with the plain Android SDK tools (no Gradle needed).
# Requires: JDK 17+, ANDROID_HOME with platforms;android-34 and build-tools;35.0.0.
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"
BT="$SDK/build-tools/${BUILD_TOOLS:-35.0.0}"
JAR="$SDK/platforms/android-${PLATFORM:-34}/android.jar"
OUT=build

rm -rf "$OUT" && mkdir -p "$OUT/classes" "$OUT/dex"

# 1. Resources + manifest
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml \
  --min-sdk-version 24 --target-sdk-version 33 \
  -A assets -o "$OUT/base.apk" "$OUT/res.zip"

# 2. Java sources
javac -source 8 -target 8 -nowarn -encoding UTF-8 -Xlint:none \
  -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" -cp "libs/*" -d "$OUT/classes" \
  $(find src ../shared/src -name '*.java')

# 3. Dex (app classes + bundled libs)
"$BT/d8" --release --min-api 24 --lib "$JAR" --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class') libs/*.jar
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -j ../unsigned.apk classes*.dex)

# 4. Align + sign (debug key is generated on first run)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
KS="${KEYSTORE:-debug.keystore}"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/Pulsekit.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$OUT/Pulsekit.apk"
echo "Built $(pwd)/$OUT/Pulsekit.apk"
