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

# 1. Resources + manifest (assets plus the shared midiutil.py and Programs/)
cp -r assets "$OUT/assets"
cp ../shared/src/pulsekit/midiutil.py "$OUT/assets/"
# DrumMidi_CRT.jar (DrumMidi_CRT with an MP3 decoder) follows DrumMidi_CRT.java.
python3 ../tools/make_drummidi_jar.py
cp -r ../Programs "$OUT/assets/Programs"   # PyJav's Java / Python / Code menus
cp -r ../Prompts "$OUT/assets/Programs/Scripts"   # PyJav's Scripts menu (the repo's Prompts folder)
# SogniMusic.java and SogniChat.java carry a copy of shared SogniApi (programs see only rt.jar); keep them the same.
sogni_body() { sed -n '/--- SogniApi begin ---/,/--- SogniApi end ---/p' "$1" | sed 's/^ *//'; }
for prog in SogniMusic SogniChat; do
  if ! diff <(sogni_body ../shared/src/pulsekit/SogniApi.java) <(sogni_body ../Programs/Java/$prog.java) >/dev/null; then
    echo "Programs/Java/$prog.java's SogniApi copy differs from shared/src/pulsekit/SogniApi.java" >&2; exit 1
  fi
done
# Each bundled Java program must compile on the phone: the in-app compiler sees only
# assets/rt.jar (Java 8, no lambdas), so check them against it here.
for prog in ../Programs/Java/*.java; do
  mkdir -p "$OUT/program-check"
  javac -source 8 -target 8 -nowarn -encoding UTF-8 -Xlint:none -proc:none \
    -bootclasspath assets/rt.jar -classpath assets/rt.jar:assets/javax-midi.jar -d "$OUT/program-check" "$prog" \
    || { echo "$prog does not compile with PyJav's on-phone compiler (assets/rt.jar)" >&2; exit 1; }
done
rm -rf "$OUT/program-check"
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml \
  --min-sdk-version 24 --target-sdk-version 33 \
  -A "$OUT/assets" -o "$OUT/base.apk" "$OUT/res.zip"

# 2. Java sources
javac -source 8 -target 8 -nowarn -encoding UTF-8 -Xlint:none \
  -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" -d "$OUT/classes" \
  $(find src ../shared/src -name '*.java' ! -name Mp3Decode.java)

# 3. Dex (Mp3Decode/JLayer are desktop-only; Android decodes MP3 with MediaExtractor)
"$BT/d8" --release --min-api 24 --lib "$JAR" --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class')
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
