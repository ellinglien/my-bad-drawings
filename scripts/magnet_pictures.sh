#!/usr/bin/env bash
# Pictures of the fridge magnets (MagnetPictures): the drawing screen with Words open on the Manta and the Nomad at the
# panel's 16 greys, tiles close up, and each page's PNG copy, into the folder given (default: /tmp/magnet-pictures).
set -euo pipefail
cd "$(dirname "$0")/.."
out=${1:-/tmp/magnet-pictures}
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
sources=$(sed -n 's/^sources=(\(.*\))/\1/p' scripts/test_inkwash.sh)
javac -nowarn -d "$classes" $(for s in $sources; do echo "$main/$s.java"; done) \
  "$test/MagnetPictures.java" "$test/CarryPictures.java" "$test/DrawLayoutChecks.java"
java -Djava.awt.headless=true -cp "$classes" io.github.mpdairy.monopaint.MagnetPictures "$out"
