#!/usr/bin/env bash
# Checks the CLAP host on a Linux computer with a Linux build of a plugin (the same C code Android runs):
#   bash android/native/test.sh <plugin.clap> [song.mid]
# It lists the plugin's ids and, with a MIDI file, plays it through the plugin into clap-test.wav.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
out="${TMPDIR:-/tmp}/pulsekit-clap-test"
cc -O2 -Wall -I"$here" -I"$here/clap" -o "$out" "$here/test_main.c" "$here/clap_render.c" "$here/midi_wav.c" -ldl -lm
if [ $# -ge 2 ]; then "$out" "$1" "$2" clap-test.wav; else "$out" "$1"; fi
