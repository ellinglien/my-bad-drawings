#!/usr/bin/env bash
# Share codes made by Brush Studio (the lab's encodeM), read and written again by the app's ModularCode.
# usage: bash docs/brush-lab/studio-src/java-codes.sh   (after build.cjs; needs the JDK from Android Studio)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; REPO="$(cd "$HERE/../../.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"; export PATH="$JAVA_HOME/bin:$PATH"
main="$REPO/app/src/main/java/io/github/mpdairy/monopaint"
sources=$(sed -n 's/^sources=(\(.*\))$/\1/p' "$REPO/scripts/test_inkwash.sh")
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
javac -nowarn -d "$tmp" $(for s in $sources; do echo "$main/$s.java"; done) "$HERE/java/StudioCodeEcho.java" 2>&1 | grep -v "^Note:" || true
node "$HERE/codes.cjs" write > "$tmp/codes.txt"
java -cp "$tmp" io.github.mpdairy.monopaint.StudioCodeEcho < "$tmp/codes.txt" > "$tmp/java.txt"
node "$HERE/codes.cjs" compare "$tmp/codes.txt" "$tmp/java.txt"
