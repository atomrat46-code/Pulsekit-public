#!/usr/bin/env bash
# Builds the desktop edition: build/Pulsekit.jar (run with: java -jar build/Pulsekit.jar).
# Builds with JDK 11+; the jar runs on Java 8 or newer (class files for Java 8, so an older Java runtime opens it too).
set -euo pipefail
cd "$(dirname "$0")"
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT/classes"

javac --release 8 -nowarn -encoding UTF-8 -cp ../shared/libs/jlayer-1_0_1.jar:libs/jna-5.14.0.jar:libs/flatlaf-3.5.4.jar \
  -d "$OUT/classes" $(find src ../shared/src -name '*.java')

# Python helpers PythonRun reads as resources, and the JLayer decoder classes.
cp src/pulsekit/drum_midi.py ../shared/src/pulsekit/midiutil.py "$OUT/classes/pulsekit/"
(cd "$OUT/classes" && jar xf ../../../shared/libs/jlayer-1_0_1.jar javazoom)
# JNA (VlcPlayer calls the computer's VLC with it): its classes, its native parts for each system, and its licences.
(cd "$OUT/classes" && jar xf ../../libs/jna-5.14.0.jar com/sun/jna META-INF/AL2.0 META-INF/LGPL2.1 META-INF/LICENSE \
  && mkdir -p META-INF/jna && mv META-INF/AL2.0 META-INF/LGPL2.1 META-INF/LICENSE META-INF/jna/)
# FlatLaf (the modern look): its classes, themes and native window parts, and its licence (Apache 2.0).
(cd "$OUT/classes" && jar xf ../../libs/flatlaf-3.5.4.jar com/formdev META-INF/LICENSE \
  && mkdir -p META-INF/flatlaf && mv META-INF/LICENSE META-INF/flatlaf/)

# PyJav's Java / Python / Code menus, and Scripts from the repo's Prompts folder.
# A JAR cannot list a folder, so add an index.
# DrumMidi_CRT.jar (DrumMidi_CRT with an MP3 decoder) follows DrumMidi_CRT.java.
python3 ../tools/make_drummidi_jar.py
cp -r ../Programs "$OUT/classes/Programs"
cp -r ../Prompts "$OUT/classes/Programs/Scripts"
(cd "$OUT/classes/Programs" && find . -type f ! -name index.txt | sed 's#^\./##' | sort) > "$OUT/classes/Programs/index.txt"

jar cfm "$OUT/Pulsekit.jar" MANIFEST.MF -C "$OUT/classes" .
echo "Built $(pwd)/$OUT/Pulsekit.jar"
