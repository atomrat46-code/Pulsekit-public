# Pulsekit

Drum pattern and MIDI tool, in a desktop (Swing) edition and an Android
edition that share their core code.

## Layout

| Path | What |
| --- | --- |
| `shared/src/pulsekit` | Code in both editions: `Engine` (patterns, songs, MIDI, file sets), `AudioIo` (WAV/SF2/MP3 encode, mixing, MIDI file sets), `StyleDb`, `JavaRun`, `PromptRun`, `PyJavHints`, `PyJavRecent`, `midiutil.py`. `Mp3Decode` is desktop-only. |
| `shared/libs` | JLayer 1.0.1 (MP3 decoding on desktop) |
| `desktop/` | Swing app (`Pulsekit.java`), `PythonRun`, `DesktopAi`, `drum_midi.py` |
| `android/` | Android app: `src/pulsekit` (MainActivity, PyJav, Prompts, ECJ/dx Java runner, Node via Termux), `res`, `assets`, `dist/Pulsekit.apk` |

See `LIBRARIES.txt` for the third-party pieces and where they are used.

## Building

Desktop (JDK 17+):

    desktop/build.sh              # -> desktop/build/Pulsekit.jar
    java -jar desktop/build/Pulsekit.jar

Android (JDK 17+, Android SDK with `platforms;android-34` and `build-tools;35.0.0`):

    ANDROID_HOME=/path/to/sdk android/build.sh   # -> android/build/Pulsekit.apk

Neither build uses Gradle. See `android/README.md` for signing.

## WAV and MP3

Importing a WAV or MP3 makes it the input file of the PyJav program, so a
program such as `MidiDrumGen.java` can turn it into MIDI. The earlier
Isolate, Analyze and Compose features were removed from both editions.
