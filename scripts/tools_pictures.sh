#!/usr/bin/env bash
# Pictures of the Tools page (ToolsPagePictures: laid out by ToolsLayout, its previews the engine's tray strokes) on
# the Manta and the Nomad in both turns, and the Manta's at the selected slot's blink, into the folder given (default:
# /tmp/tools-pictures).
set -euo pipefail
cd "$(dirname "$0")/.."
out=${1:-/tmp/tools-pictures}
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
sources=$(sed -n 's/^sources=(\(.*\))/\1/p' scripts/test_inkwash.sh)
javac -nowarn -d "$classes" $(for s in $sources; do echo "$main/$s.java"; done) \
  "$test/ToolsPagePictures.java" "$test/DrawLayoutChecks.java" "$test/BrushLabChecks.java" "$test/Timing.java" "$test/LegacyConversionChecks.java" 2>&1 | grep -v '^Note' || true
java -Djava.awt.headless=true -cp "$classes" io.github.mpdairy.monopaint.ToolsPagePictures "$out"
