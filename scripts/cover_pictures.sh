#!/usr/bin/env bash
# Pictures of notebook covers (CoverPictures): the shelf on the Manta in portrait with covers in several designs, a
# Classic front and back, a front cover drawn on over its cover picture and its back cover at the page's size, and the
# nine designs, into the folder given (default: /tmp/cover-pictures).
set -euo pipefail
cd "$(dirname "$0")/.."
out=${1:-/tmp/cover-pictures}
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
sources=$(sed -n 's/^sources=(\(.*\))/\1/p' scripts/test_inkwash.sh)
javac -nowarn -d "$classes" $(for s in $sources; do echo "$main/$s.java"; done) \
  "$test/CoverPictures.java" "$test/CoverChecks.java" "$test/NotebookFixture.java" "$test/NotebookBench.java" "$test/NotebookFileChecks.java" "$test/NotebookMigrationChecks.java" 2>&1 | grep -v '^Note' || true
java -Djava.awt.headless=true -cp "$classes" io.github.mpdairy.monopaint.CoverPictures "$out"
