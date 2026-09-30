# Pulsekit Android

Full Pulsekit Android app (`pulsekit.app`, versionCode 198), built from plain
Java source. Code shared with the desktop edition lives in `../shared/src`.

## Prebuilt APK

`dist/Pulsekit.apk` is debug-signed, minSdk 24 (Android 7.0+). It is signed
with a different key than your original `pulsekit.jks` build, so uninstall that
one first.

## Building

Requires JDK 17+ and an Android SDK with `platforms;android-34` and
`build-tools;35.0.0` (build-tools 34's d8 crashes on this code).

    export ANDROID_HOME=/path/to/android-sdk
    ./build.sh            # -> build/Pulsekit.apk

`build.sh` runs aapt2, javac, d8, zipalign and apksigner directly. Gradle and
Javassist are not needed. On the first run it creates `debug.keystore`
(git-ignored). Set `KEYSTORE=/path/to/key.jks` to sign with your own key; the
script assumes the `android` / `androiddebugkey` credentials.

## How MainActivity was rebuilt

The original build compiled a prebuilt `MainActivity.class` and injected most
features into it at build time with Javassist (`patch-original/`). Those
features are now ordinary source in `src/pulsekit/MainActivity.java`:

- The decompiler artifacts in the CFR output were fixed by hand. Some of them
  compiled but misbehaved, e.g. the accent row, track mutes and pad
  live-record wrote to the wrong index, and song-picker entries opened the
  wrong fill.
- The 94 fields and 113 methods from `PatchAnalyze` sit at the end of
  MainActivity: Analyze, Compose, Prompts, file-set Info, stem and MIDI
  playback, and PyJav.
- Each Javassist hook became a small wrapper. A hooked method `foo` was renamed
  `fooBase` (or `fooCore` for injected methods), and `foo` runs the injected
  before/after code around it in the same order Javassist did.
- `PatchPyJavText` and `PatchNode` (open .js/.ts files, Sogni client, Termux
  hint) are applied. `PatchRecent` and `PatchRefresh` were already built into
  `PatchAnalyze`'s code.
- The decompiled MainActivity carried an older Analyze screen with the same
  method names as the patched one. The patched version replaces it.
- `lambda$name$N` methods from the decompiler are renamed `nameActionN`, because
  javac reserves those names.

`patch-original/` is kept for reference only and is not compiled.
