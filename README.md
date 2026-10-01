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
| `Programs/` | Programs listed in PyJav's menus: `Java/`, `Python/` and `Code/`. Both builds bundle this folder. |

See `LIBRARIES.txt` for the third-party pieces and where they are used.

## Building

Desktop (JDK 17+):

    desktop/build.sh              # -> desktop/build/Pulsekit.jar
    java -jar desktop/build/Pulsekit.jar

Android (JDK 17+, Android SDK with `platforms;android-34` and `build-tools;35.0.0`):

    ANDROID_HOME=/path/to/sdk android/build.sh   # -> android/build/Pulsekit.apk

Neither build uses Gradle. See `android/README.md` for signing.

## PyJav menus

PyJav has Java, Python and Code menus listing `Programs/Java`, `Programs/Python`
and `Programs/Code`. Choosing a Java or Python entry makes it the program Run
executes, without touching the editor. Choosing a Code entry opens the file in
the editor for editing; it does not change what Run executes. Opening a program
any other way (Browse file, Recent, Import) makes that the program again. To add
a program, put the file in the right folder and rebuild.

## Help

File > Help-Android (phone) and File > Help-Desktop open a Help page. Its text
is in `android/src/pulsekit/HelpPage.java` and `HELP_SECTIONS` in
`desktop/src/pulsekit/Pulsekit.java`; the two differ where the editions run
programs differently (Termux on Android, Termux for Windows and a JDK on the PC).

## WAV and MP3

Importing a WAV or MP3 makes it the input file of the PyJav program, so a
program such as `Programs/Java/DrumMidi_CRT.java` can turn it into MIDI. The earlier
Isolate, Analyze and Compose features were removed from both editions.
