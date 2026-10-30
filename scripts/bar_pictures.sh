#!/usr/bin/env bash
# Pictures of the drawing screen's side column and page bar, and the inside of a notebook (DrawScreenPictures: laid out
# by DrawLayout and NotebookLayout, the tray rows the engine's own strokes), on the Manta and the Nomad in both turns,
# and of the Tools page (ToolsPagePictures), into the folder given (default: /tmp/bar-pictures).
set -euo pipefail
cd "$(dirname "$0")/.."
out=${1:-/tmp/bar-pictures}
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
sources=$(sed -n 's/^sources=(\(.*\))/\1/p' scripts/test_inkwash.sh)
javac -nowarn -d "$classes" $(for s in $sources; do echo "$main/$s.java"; done) \
  "$test/DrawScreenPictures.java" "$test/ToolsPagePictures.java" "$test/DrawLayoutChecks.java" 2>&1 | grep -v '^Note' || true
java -Djava.awt.headless=true -cp "$classes" io.github.mpdairy.monopaint.DrawScreenPictures "$out"
java -Djava.awt.headless=true -cp "$classes" io.github.mpdairy.monopaint.ToolsPagePictures "$out"
