#!/bin/sh
# Runs the desktop behavior scenarios against desktop/build/Pulsekit.jar.
# Each scenario starts the app in a fresh home folder; snapshots go to desktop/test/build/snapshots.
# Needs a display: uses $DISPLAY, or starts Xvfb on :93.
set -e
cd "$(dirname "$0")"
OUT=build/snapshots
JAR=../build/Pulsekit.jar
rm -rf build/classes "$OUT"
mkdir -p build/classes "$OUT"
# Java 8 class files: the suite runs on Java 8 too (JAVA=/path/to/java8/bin/java).
javac --release 8 -nowarn -cp "$JAR" -d build/classes pulsekit/DesktopBehavior.java
JAVA="${JAVA:-java}"
if [ -z "$DISPLAY" ]; then
  # Start Xvfb unless one is running on :93 (a lock file alone may be left from a stopped one).
  # (Its process must be an Xvfb: after a restart another process can have the lock's number.)
  if ! { [ -e /tmp/.X93-lock ] && ps -p "$(tr -d ' ' < /tmp/.X93-lock)" -o comm= 2>/dev/null | grep -q Xvfb; }; then
    rm -f /tmp/.X93-lock /tmp/.X11-unix/X93
    Xvfb :93 -screen 0 1400x1000x24 >/dev/null 2>&1 &
    sleep 2
  fi
  export DISPLAY=:93
fi
SCENARIOS=$(grep -o 'void s[0-9][0-9a-z_]*()' pulsekit/DesktopBehavior.java | sed 's/void //; s/()//')
fail=0
for s in $SCENARIOS; do
  case "$s" in
    s14_persistence_read) home="build/home-s14";;
    s14_persistence_write) home="build/home-s14"; rm -rf "$home";;
    *) home="build/home-$s"; rm -rf "$home";;
  esac
  mkdir -p "$home"
  if timeout 120 "$JAVA" -Djava.awt.headless=false -Duser.home="$PWD/$home" -Dpulsekit.test.dir="$PWD" -cp "$JAR:build/classes" pulsekit.DesktopBehavior "$s" "$OUT" >/dev/null 2>&1; then
    echo "ok   $s"
  else
    echo "FAIL $s"; fail=1
  fi
done
exit $fail
