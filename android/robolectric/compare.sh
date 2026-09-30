#!/usr/bin/env bash
# Usage: compare.sh BASELINE_DIR   (after running: gradle :app:testDebugUnitTest)
# Diffs the new snapshots against a saved baseline.
set -euo pipefail
cd "$(dirname "$0")"
diff -ru "$1" app/build/snapshots && echo "Snapshots identical."
