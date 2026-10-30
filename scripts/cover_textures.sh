#!/usr/bin/env bash
# The covers' sheets (CoverTexture), made from the designs' own generators (CoverTextures): 1-bit PNGs into the folder
# given (default: the app's assets, app/src/main/assets/covers). Give design keys after it to make only those, e.g.
#   bash scripts/cover_textures.sh app/src/main/assets/covers classic swirl
# The same sheets every time, on any Java. A few minutes on a laptop (Swirl's bath is the slow one).
set -euo pipefail
cd "$(dirname "$0")/.."
out=${1:-app/src/main/assets/covers}
shift || true
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
javac -nowarn -d "$classes" "$main/Cores.java" "$main/CoverNoise.java" "$main/CoverMarble.java" "$main/CoverDesign.java" "$main/CoverTexture.java" \
  "$main/CoverPattern.java" "$main/GrayPalette.java" "$main/Assets.java" "$test/CoverTextures.java"
java -cp "$classes" io.github.mpdairy.monopaint.CoverTextures "$out" "$@"
