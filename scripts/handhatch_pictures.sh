#!/usr/bin/env bash
# A contact sheet of Hand Hatch (pressure, going over, holding, its stroke's dials, Lines Follow, the old Hatch beside it)
# and the tonal Print Shading choices (Stipple, Woodcut, Scribble, Hatching: a ramp, an S-curve pressing harder, a
# scribble), drawn by the app's engine (HandHatchPictures), into the PNG given (default: /tmp/handhatch-sheet.png).
set -euo pipefail
cd "$(dirname "$0")/.."
out=${1:-/tmp/handhatch-sheet.png}
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
sources=$(sed -n 's/^sources=(\(.*\))/\1/p' scripts/test_inkwash.sh)
checks=$(sed -n 's/^checks=(\(.*\))/\1/p' scripts/test_inkwash.sh)
javac -nowarn -d "$classes" $(for s in $sources; do echo "$main/$s.java"; done) \
  $(for c in $checks NotebookFixture NotebookBench CoverTextures HandHatchPictures; do echo "$test/$c.java"; done) 2>&1 | grep -v '^Note:' || true
java -cp "$classes" io.github.mpdairy.monopaint.HandHatchPictures "$out"
