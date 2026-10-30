#!/usr/bin/env bash
# Pictures of Smudge drawn by the app's engine (CarryPictures: as the Manta shows them, its dots beside the 16 greys) and
# each of CarrySmudgeChecks' golden pages beside the web lab's (lab left, app right, where they differ below), into
# the folder given (default: /tmp/smudge-pictures). The goldens come from scripts/carry_goldens.cjs.
set -euo pipefail
cd "$(dirname "$0")/.."
out=${1:-/tmp/smudge-pictures}
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
sources=$(sed -n 's/^sources=(\(.*\))/\1/p' scripts/test_inkwash.sh)
javac -nowarn -d "$classes" $(for s in $sources; do echo "$main/$s.java"; done) \
  "$test/CarryPictures.java" "$test/CarrySmudgeChecks.java" "$test/QuickStrokeChecks.java" "$test/ModularStrokeChecks.java" \
  "$test/LegacyConversionChecks.java" "$test/ModularCodeChecks.java"
java -cp "$classes" io.github.mpdairy.monopaint.CarryPictures "$out"
java -cp "$classes" io.github.mpdairy.monopaint.CarrySmudgeChecks pairs "$out"
