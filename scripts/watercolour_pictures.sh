#!/usr/bin/env bash
# Wet watercolour's comparison pictures (the wet paint spec, section 9): each retired Watercolor preset beside the wet one
# of its name (one stroke, two wet into wet, a stroke into a nearly dry one, tilted), drawn by the app's engine
# (WatercolourPictures) into the folder given (default: /tmp/watercolour-pictures). With "mix" after the folder: wet into
# wet, crossing strokes 0, .5 and 2 s apart and a stroke back over its start (mix-*.png). With a style instead ("Ink wash"):
# its retired presets beside their wet ones, one stroke, crossing 0, .5 and 2 s apart, tilted; at its Medium size, or at
# the px given after the style.
set -euo pipefail
cd "$(dirname "$0")/.."
out=${1:-/tmp/watercolour-pictures}
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
sources=$(sed -n 's/^sources=(\(.*\))/\1/p' scripts/test_inkwash.sh)
checks=$(sed -n 's/^checks=(\(.*\))/\1/p' scripts/test_inkwash.sh)
helpers=$(sed -n 's/^helpers=(\(.*\))/\1/p' scripts/test_inkwash.sh)
javac -nowarn -d "$classes" $(for s in $sources; do echo "$main/$s.java"; done) \
  $(for c in $checks $helpers WatercolourPictures; do echo "$test/$c.java"; done) 2>&1 | grep -v '^Note:' || true
java -cp "$classes" io.github.mpdairy.monopaint.WatercolourPictures "$out" "${@:2}"
