# Pulsekit Android

Buildable Android project for Pulsekit (`pulsekit.app`, versionCode 156).

## Prebuilt APK

`dist/Pulsekit.apk`: debug-signed, minSdk 24 (Android 7.0+). Sideload it with
`adb install dist/Pulsekit.apk`, or copy it to the phone and open it (allow
"Install unknown apps" for your file manager).

## Building

Requires JDK 17+ and an Android SDK with `platforms;android-34` and
`build-tools;35.0.0` (build-tools 34's d8 crashes on `MainActivity$1`).

    export ANDROID_HOME=/path/to/android-sdk
    ./build.sh            # -> build/Pulsekit.apk

The script runs aapt2, javac, d8, zipalign and apksigner directly, so Gradle is
not needed. On the first run it creates `debug.keystore` (git-ignored). To sign
with your own key, set `KEYSTORE=/path/to/key.jks`. The script assumes the
`android` / `androiddebugkey` credentials.

## Source notes

- `src/pulsekit/MainActivity.java` was recovered with the CFR decompiler. Its
  decompiler artifacts (reused variable slots, untyped `Object` locals, lambdas
  capturing the wrong variable, a broken `synchronized` block) were repaired by
  hand so it compiles. All the other sources are original.
- `unused/PyJavUi.java` and `unused/MixLevels.java` call `MainActivity` methods
  (`pkRunPyJav`, `setMixLevel`, ...) that are missing from this MainActivity.
  Nothing references them, so they are left out of the build.
