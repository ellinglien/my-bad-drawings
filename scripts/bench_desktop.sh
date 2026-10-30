#!/usr/bin/env bash
# The six-part engine's timing bench on this computer (app/src/androidTest/.../ModularPerfBench): a 1.5 s pen stroke
# replayed in real time through each style's brush on a full Manta page. The Manta runs it about 20 times slower.
# Usage: scripts/bench_desktop.sh [styles,comma|all] [sizes,comma|all] [lives: none,wet,dry|all] [stroke ms]
set -euo pipefail
export STRICT_TIMING=1 # the checks' timing limits fail here (app/src/test/.../Timing.java); off, they only print TIME
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}" PATH="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}/bin:$PATH"
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
sources=$(sed -n 's/^sources=(\(.*\))$/\1/p' scripts/test_inkwash.sh)
javac -nowarn -d "$classes" $(for s in $sources; do echo "$main/$s.java"; done) app/src/androidTest/java/io/github/mpdairy/monopaint/ModularPerfBench.java 2>&1 | grep -v "^Note:" || true
java -Xmx4g ${JOPTS:-} -cp "$classes" io.github.mpdairy.monopaint.ModularPerfBench "$@"
