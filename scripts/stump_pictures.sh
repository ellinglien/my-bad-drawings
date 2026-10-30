#!/usr/bin/env bash
# Inkwash's Smear (the Stump and Smear at Soften 1) beside upstream MonoPaint's Blending stump and its oil brush running
# out, each on the same test pages, drawn by the app's engine (StumpPictures), into the folder given (default:
# /tmp/stump-pictures). See docs/archive/superpowers/specs/2026-10-18-stump-design.md.
set -euo pipefail
cd "$(dirname "$0")/.."
out=${1:-/tmp/stump-pictures}
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
sources=$(sed -n 's/^sources=(\(.*\))/\1/p' scripts/test_inkwash.sh)
checks=$(sed -n 's/^checks=(\(.*\))/\1/p' scripts/test_inkwash.sh)
javac -nowarn -d "$classes" $(for s in $sources PaintLoad; do echo "$main/$s.java"; done) \
  $(for c in $checks CarryPictures StumpPictures; do echo "$test/$c.java"; done) 2>&1 | grep -v '^Note:' || true
java -cp "$classes" io.github.mpdairy.monopaint.StumpPictures "$out"
