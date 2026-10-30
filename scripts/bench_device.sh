#!/usr/bin/env bash
# The six-part engine's timing bench on the Manta: builds the bench build (ing.ell.mybaddrawings.bench, beside the
# user's app, never touching it), installs it with its test package, runs ModularPerfBench through the
# instrumentation and prints its table. Arguments are instrumentation extras, e.g.
#   scripts/bench_device.sh -e styles "Ink wash,Oils" -e sizes 120,360 -e lives none,wet,dry  (-e sizes presets: each
# style's Small, Medium and Large; -e lattice 200: the lattice past 200 px; -e ms 6000: a longer stroke; Erasable:
# -e erasable 1 draws every brush all erasable, as before it; -e marks true -e marksErasable 0 lays the marks permanent;
# -e stability wash,session,fill: the heap under load (StabilityBench), -e actions 40 [-e presets "Soft wash,Sumi"]
# -e eraser true times the eraser over pages with and without shares, and the heap a pair's shares take)
# LIVE=1 runs LiveStrokeBench instead: real pen events into the bench build's own drawing screen (it shows on the panel
# while it runs), printing DrawingPad's "stroke timing" lines too, e.g. LIVE=1 scripts/bench_device.sh -e styles "Ink wash" -e sizes 360.
# LIVE=1 ... -e book "name" -e rounds 30 [-e away 10] [-e holdMs 45000]: the heap through a long session on one page of a
# notebook copied into Document/Scribulor bench (zoom, pen, filled shape, crayon, eraser), held for a heap dump at both ends.
# The bench packages are uninstalled when the run ends (they don't stay on the owner's device); KEEP=1 keeps them
# for further runs (NOBUILD=1 reuses them), and scripts/bench_device.sh --uninstall removes them afterwards.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}" PATH="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}/bin:$PATH"
# The tablet: $SERIAL, else the first device `adb devices` lists.
serial=${SERIAL:-$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')}
[ -n "$serial" ] || { echo "No tablet: connect one by USB, or set SERIAL (see adb devices)" >&2; exit 1; }
adb="adb -s $serial"
pkg=ing.ell.mybaddrawings.bench
if [ "${1:-}" = "--uninstall" ]; then $adb uninstall $pkg.test || true; $adb uninstall $pkg || true; exit 0; fi
if [ -z "${NOBUILD:-}" ]; then
  ./gradlew -q -Pbench :app:assembleBench :app:assembleBenchAndroidTest
  $adb install -r app/build/outputs/apk/bench/app-bench.apk > /dev/null
  $adb install -r app/build/outputs/apk/androidTest/bench/app-bench-androidTest.apk > /dev/null
fi
# The pen events are injected system-wide: start from the home screen, never over the owner's apps (the bench
# also checks its own screen has the focus before each stroke).
$adb shell input keyevent KEYCODE_HOME; sleep 1
$adb logcat -c
# The table streams to logcat as it runs (a long run outgrows the log's buffer): kept as it comes.
log=$(mktemp)
$adb logcat -s InkBench:I MonoPaint:I > "$log" &
reader=$!
# The device's shell splits words again: quote each argument for it.
extras=""; for a in "$@"; do extras="$extras '$a'"; done
kind="-e modularPerf true"; [ -n "${LIVE:-}" ] && kind="-e liveStrokes true"
$adb shell "am instrument -w $kind $extras $pkg.test/io.github.mpdairy.monopaint.WidthInstrumentation" | grep -E "Exception|Error|stopped" >&2 || true
sleep 1; kill $reader 2> /dev/null || true
sed -n 's/.*InkBench: //p; s/.*MonoPaint: \(stroke timing\)/\1/p' "$log" | grep -v '^--$' || true
rm -f "$log"
$adb shell input keyevent KEYCODE_HOME
if [ -z "${KEEP:-}" ]; then $adb uninstall $pkg.test > /dev/null || true; $adb uninstall $pkg > /dev/null || true; fi
