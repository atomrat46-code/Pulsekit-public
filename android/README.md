# Pulsekit Android

Pulsekit Android app (`pulsekit.app`, versionCode 198), built from plain Java
source. Code shared with the desktop edition lives in `../shared/src`.

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

## Code layout

MainActivity used to be one 7,000-line class. It is now split by feature; each
feature class holds `app` (the activity) and the state only it uses. Shared
state (pattern cells, learned patterns, the song, the current view) stays on
MainActivity.

| Class | What it does |
| --- | --- |
| `UiKit` | Base class of MainActivity: colors and view factories (`col`, `row`, `text`, `pill`, `dp`, ...) |
| `MainActivity` | Lifecycle, shared state, header/tabs/knobs/transport, `show()` page routing |
| `GridEditor` | Pattern grid, accent row, note lengths, steps and time signature |
| `Playback` | Transport, audio mixer thread, drum voices, drum sets and pads |
| `SongEditor` | Song lanes, parts, song-pick menu, imported songs |
| `StyleLibrary` | Patterns, fills and Fillerns: chips, variations and menus |
| `ImportLibrary` | Learning from imported MIDI, imported-file lists, .fset import |
| `FileSets` | File-set Info page, style changes, songs from sets, source-MIDI playback |
| `ProjectIo` | Import/Export pages, projects, plugins, file decoding |
| `Persistence` | Saving and restoring learned patterns, Fillerns, kit and session |
| `PyJav` | PyJav page: programs, prompts, run modes, input/output files |
| `ProgramMenus` | PyJav's Java / Python / Code menus over the bundled `Programs/` folder |
| `HelpPage` | Help page opened from File > Help-Android |
| `FileSetClicks`, `PyJavUi` | Click adapters used by FileSets and PyJav |

`robolectric/` has behavior tests that snapshot the app's views and state;
run them before and after a change to check nothing else moved.

## How MainActivity was rebuilt

The original build compiled a prebuilt `MainActivity.class` and injected most
features into it at build time with Javassist (`patch-original/`). Those
features are now ordinary source in `src/pulsekit/MainActivity.java`:

- The decompiler artifacts in the CFR output were fixed by hand. Some of them
  compiled but misbehaved, e.g. the accent row, track mutes and pad
  live-record wrote to the wrong index, and song-picker entries opened the
  wrong fill.
- Each Javassist hook became a small wrapper. A hooked method `foo` was renamed
  `fooBase` (or `fooCore` for injected methods), and `foo` runs the injected
  before/after code around it in the same order Javassist did.
- `lambda$name$N` methods from the decompiler are renamed `nameActionN`, because
  javac reserves those names.
- Where it was safe, each wrapper pair was folded back into one method. The
  pairs that remain (`fooBase`/`fooCore`) wrap a method with early returns.
- Isolate, Analyze and Compose (with Combine tracks and the stem mixer) are
  removed. An imported WAV or MP3 becomes the PyJav input file instead.

`patch-original/` is kept for reference only and is not compiled.

## Build notes

- `midiutil.py` lives in `../shared/src/pulsekit` and is copied into the APK
  assets at build time, as is `../Programs` (as `assets/Programs`).
- `Mp3Decode.java` and JLayer are left out: Android decodes MP3 with
  MediaExtractor.
