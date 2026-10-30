#!/usr/bin/env bash
# Notebook timings on this Mac, before (the document classes from 580a326) and after: a 24-page notebook opened,
# 20 notebooks' covers read for the shelf, a page's picture made, the PDF and PNG copies. The Manta is about 20 times slower.
set -euo pipefail
cd "$(dirname "$0")/.."
classes=$(mktemp -d) old=$(mktemp -d) files=$(mktemp -d)
trap 'rm -rf "$classes" "$old" "$files"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
eval "$(grep '^sources=' scripts/test_inkwash.sh)"
javac -nowarn -d "$classes" $(for s in "${sources[@]}"; do echo "$main/$s.java"; done) \
  "$test/NotebookFixture.java" "$test/NotebookBench.java" "$test/NotebookFileChecks.java" 2>&1 | grep -v '^Note' || true
java -cp "$classes" io.github.mpdairy.monopaint.NotebookBench make "$files"
java -cp "$classes" io.github.mpdairy.monopaint.NotebookBench time "$files"
[ -n "${KEEP:-}" ] && cp "$files/EXPORT/24 pages.pdf" "$KEEP"
src=$(mktemp -d)
for f in GrayPalette DotPattern ToneDocument DocumentCodec DrawingBook BookCodec; do
  git show "580a326:$main/$f.java" > "$src/$f.java"
done
javac -nowarn -d "$old" "$src"/{GrayPalette,DotPattern,ToneDocument,DocumentCodec,DrawingBook,BookCodec}.java "$test/NotebookBenchBefore.java" 2>&1 | grep -v '^Note' || true
java -cp "$old" io.github.mpdairy.monopaint.NotebookBenchBefore "$files"
rm -rf "$src"
