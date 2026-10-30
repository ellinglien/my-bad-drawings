#!/usr/bin/env bash
# Pictures of Brush Lab, the Tools page, the drawing screen's toolbar, its file menu, dialogs and a message at the Manta's size and the Nomad's, on the
# Manta (androidTest ScreenShots): builds the bench build (ing.ell.mybaddrawings.bench, beside the user's app, never
# touching it or the tablet's settings), installs it with its test package, takes the pictures, pulls them into the
# folder given (default build/screens) and uninstalls both. -e only lab|tools|draw|menus|app narrows it, e.g.
#   scripts/screens_device.sh /tmp/screens lab
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}" PATH="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}/bin:$PATH"
# The tablet: $SERIAL, else the first device `adb devices` lists.
serial=${SERIAL:-$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')}
[ -n "$serial" ] || { echo "No tablet: connect one by USB, or set SERIAL (see adb devices)" >&2; exit 1; }
adb="adb -s $serial"
pkg=ing.ell.mybaddrawings.bench
out=${1:-build/screens}; only=${2:-}
./gradlew -q -Pbench :app:assembleBench :app:assembleBenchAndroidTest
trap '$adb uninstall $pkg.test > /dev/null || true; $adb uninstall $pkg > /dev/null || true' EXIT
$adb install -r app/build/outputs/apk/bench/app-bench.apk > /dev/null
$adb install -r app/build/outputs/apk/androidTest/bench/app-bench-androidTest.apk > /dev/null
extra=""; [ -n "$only" ] && extra="-e only $only"
$adb shell "am instrument -w -e screens true $extra $pkg.test/io.github.mpdairy.monopaint.WidthInstrumentation" | grep -E "FAILED|INSTRUMENTATION_CODE" || true
mkdir -p "$out"
$adb pull "/sdcard/Android/data/$pkg/files/screens/." "$out/" > /dev/null
ls "$out" | wc -l
