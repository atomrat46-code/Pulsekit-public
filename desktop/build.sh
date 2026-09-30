#!/usr/bin/env bash
# Builds the desktop edition: build/Pulsekit.jar (run with: java -jar build/Pulsekit.jar).
# Requires JDK 17+.
set -euo pipefail
cd "$(dirname "$0")"
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT/classes"

javac --release 17 -nowarn -encoding UTF-8 -cp ../shared/libs/jlayer-1_0_1.jar \
  -d "$OUT/classes" $(find src ../shared/src -name '*.java')

# Python helpers PythonRun reads as resources, and the JLayer decoder classes.
cp src/pulsekit/drum_midi.py ../shared/src/pulsekit/midiutil.py "$OUT/classes/pulsekit/"
(cd "$OUT/classes" && jar xf ../../../shared/libs/jlayer-1_0_1.jar javazoom)

jar cfm "$OUT/Pulsekit.jar" MANIFEST.MF -C "$OUT/classes" .
echo "Built $(pwd)/$OUT/Pulsekit.jar"
