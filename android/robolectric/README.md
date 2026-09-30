# Behavior tests (Robolectric)

Runs MainActivity on the JVM and records what the app does, so a refactor can
be checked for unchanged behavior without a phone.

    cd android/robolectric
    echo "sdk.dir=$ANDROID_HOME" > local.properties   # once
    gradle :app:testDebugUnitTest

Each scenario in `BehaviorTest` (boot, every view, grid taps, play/stop, MIDI,
song-MIDI, .sng, .fset and program imports, WAV as PyJav input, project
round-trip, song editing, pads, restart persistence) writes the view tree and
key state to `app/build/snapshots/`. Save a copy before a change and run
`./compare.sh <saved-copy>` after it; any difference is a behavior change.

Methods and fields are looked up by name on MainActivity and the feature
objects it holds, so the tests survive code moving between classes.
