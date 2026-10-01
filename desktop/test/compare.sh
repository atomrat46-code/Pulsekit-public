#!/bin/sh
# Compares desktop/test/build/snapshots with a saved copy: any difference is a behavior change.
cd "$(dirname "$0")"
diff -ru "$1" build/snapshots && echo "No differences."
