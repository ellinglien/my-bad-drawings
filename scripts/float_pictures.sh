#!/usr/bin/env bash
# Pictures of Float (suminagashi) drawn by the app's engine (FloatPictures: as the Manta shows them, its dots beside the
# 16 greys), into the folder given (default: /tmp/float-pictures): Sumi and Clear water drops in turn, a combed bath, a
# hake band through rings, and rings printed level beside rings that drifted on a tilted page.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}" PATH="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}/bin:$PATH"
out=${1:-/tmp/float-pictures}
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
sources=$(sed -n 's/^sources=(\(.*\))/\1/p' scripts/test_inkwash.sh)
javac -nowarn -d "$classes" $(for s in $sources; do echo "$main/$s.java"; done) \
  "$test/FloatPictures.java" "$test/FloatChecks.java" "$test/CarryPictures.java" "$test/CarrySmudgeChecks.java" "$test/QuickStrokeChecks.java" \
  "$test/ModularStrokeChecks.java" "$test/LegacyConversionChecks.java" "$test/ModularCodeChecks.java" 2>&1 | grep -v '^Note:' || true
java -cp "$classes" io.github.mpdairy.monopaint.FloatPictures "$out"
