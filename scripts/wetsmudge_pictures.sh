#!/usr/bin/env bash
# The wet smudges (Water brush, Dirty brush) drawn by the app's engine (WetSmudgePictures) into the folder given
# (default: /tmp/wetsmudge-pictures). See docs/archive/superpowers/specs/2026-10-19-wet-smudge-design.md.
set -euo pipefail
cd "$(dirname "$0")/.."
out=${1:-/tmp/wetsmudge-pictures}
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
sources=$(sed -n 's/^sources=(\(.*\))/\1/p' scripts/test_inkwash.sh)
checks=$(sed -n 's/^checks=(\(.*\))/\1/p' scripts/test_inkwash.sh)
helpers=$(sed -n 's/^helpers=(\(.*\))/\1/p' scripts/test_inkwash.sh)
javac -nowarn -d "$classes" $(for s in $sources; do echo "$main/$s.java"; done) \
  $(for c in $checks $helpers WetSmudgePictures; do echo "$test/$c.java"; done) 2>&1 | grep -v '^Note:' || true
java -cp "$classes" io.github.mpdairy.monopaint.WetSmudgePictures "$out"
