#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
test_classes=$(mktemp -d)
trap 'rm -rf "$test_classes"' EXIT
javac -d "$test_classes" \
  app/src/main/java/io/github/mpdairy/monopaint/{GrayPalette,DotPattern,ToneDocument,DocumentCodec,DrawingBook,PackedPage,BookCodec,BookPictures,DrawingFiles,RecoveryCodec,ToolSettings,ToolLibrary,BrushDirection,BristleTexture,WetWatercolor,WetWorkBudget,FloodFill,ToneDabs,DrawingStroke,ToolStroke,ShapeStroke,ShapeGeometry,DirtyRegions,PaintLoad,BrushInk,WatercolorWash,Magnets,Mirrors,LabText,TypeStyle,InkWashLayers,Cores,CoverNoise,CoverMarble,CoverDesign,CoverPattern,CoverTexture,Assets,NotebookCovers,BookOptions,NotebookTemplate,NotebookOptions,PageClock,PagePicture,FadingInk,PaperNoise,FloatingSelection}.java \
  app/src/test/java/io/github/mpdairy/monopaint/{LayerChecks,MaskChecks,DocumentChecks,ToolChecks,BookChecks,DrawingFilesChecks,BristleChecks,WetWatercolorChecks,FlatWashChecks,CanvasPaintChecks,GradientFillChecks,AirbrushChecks,EraseChecks,ShapeChecks,PaintLoadChecks,CanvasSizeChecks,ErasableFileChecks,MagnetFileChecks,SelectionChecks}.java
javac -d "$test_classes" app/src/main/java/io/github/mpdairy/monopaint/RotationSuggestion.java app/src/test/java/io/github/mpdairy/monopaint/RotationSuggestionChecks.java
java -cp "$test_classes" io.github.mpdairy.monopaint.RotationSuggestionChecks
java -cp "$test_classes" io.github.mpdairy.monopaint.DocumentChecks
java -cp "$test_classes" io.github.mpdairy.monopaint.ToolChecks
java -cp "$test_classes" io.github.mpdairy.monopaint.BristleChecks
java -cp "$test_classes" io.github.mpdairy.monopaint.BookChecks
java -cp "$test_classes" io.github.mpdairy.monopaint.DrawingFilesChecks
java -cp "$test_classes" io.github.mpdairy.monopaint.WetWatercolorChecks
java -cp "$test_classes" io.github.mpdairy.monopaint.FlatWashChecks
java -cp "$test_classes" io.github.mpdairy.monopaint.CanvasPaintChecks

java -cp "$test_classes" io.github.mpdairy.monopaint.LayerChecks
java -cp "$test_classes" io.github.mpdairy.monopaint.MaskChecks
java -cp "$test_classes" io.github.mpdairy.monopaint.GradientFillChecks

java -cp "$test_classes" io.github.mpdairy.monopaint.AirbrushChecks

java -cp "$test_classes" io.github.mpdairy.monopaint.EraseChecks

java -cp "$test_classes" io.github.mpdairy.monopaint.ErasableFileChecks
# An older version (the document classes before Erasable, from git) refuses a TSM5 drawing and leaves it as it was.
old_ref=8d52808
if git rev-parse -q --verify "$old_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  for f in GrayPalette DotPattern ToneDocument DocumentCodec DrawingBook BookCodec; do
    git show "$old_ref:app/src/main/java/io/github/mpdairy/monopaint/$f.java" > "$old_src/$f.java"
  done
  javac -d "$old_classes" "$old_src"/*.java app/src/test/java/io/github/mpdairy/monopaint/OlderReaderCheck.java
  java -cp "$test_classes" io.github.mpdairy.monopaint.ErasableFileChecks write "$old_classes/tsm5.mpaint"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderReaderCheck "$old_classes/tsm5.mpaint"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the older reader's check needs git and commit $old_ref"
fi

java -cp "$test_classes" io.github.mpdairy.monopaint.MagnetFileChecks
# An older version (the document classes before magnets, from git) opens a drawing with magnets, skipping their section.
magnet_ref=f34fed5
if git rev-parse -q --verify "$magnet_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  for f in GrayPalette DotPattern ToneDocument DocumentCodec DrawingBook BookCodec; do
    git show "$magnet_ref:app/src/main/java/io/github/mpdairy/monopaint/$f.java" > "$old_src/$f.java"
  done
  javac -d "$old_classes" "$old_src"/*.java app/src/test/java/io/github/mpdairy/monopaint/MagnetOlderReaderCheck.java
  hashes=$(java -cp "$test_classes" io.github.mpdairy.monopaint.MagnetFileChecks write "$old_classes/words.mpaint")
  java -cp "$old_classes" io.github.mpdairy.monopaint.MagnetOlderReaderCheck "$old_classes/words.mpaint" $hashes
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the older reader's check for magnets needs git and commit $magnet_ref"
fi

javac -d "$test_classes" app/src/main/java/io/github/mpdairy/monopaint/CanvasViewport.java app/src/test/java/io/github/mpdairy/monopaint/ViewportChecks.java
java -cp "$test_classes" io.github.mpdairy.monopaint.ViewportChecks

java -cp "$test_classes" io.github.mpdairy.monopaint.ShapeChecks
java -cp "$test_classes" io.github.mpdairy.monopaint.SelectionChecks

java -cp "$test_classes" io.github.mpdairy.monopaint.PaintLoadChecks

java -cp "$test_classes" io.github.mpdairy.monopaint.CanvasSizeChecks
javac -d "$test_classes" app/src/main/java/io/github/mpdairy/monopaint/ExportSize.java app/src/test/java/io/github/mpdairy/monopaint/ExportSizeChecks.java
java -cp "$test_classes" io.github.mpdairy.monopaint.ExportSizeChecks
javac -d "$test_classes" app/src/main/java/io/github/mpdairy/monopaint/MultiFingerTap.java app/src/test/java/io/github/mpdairy/monopaint/MultiFingerTapChecks.java
java -cp "$test_classes" io.github.mpdairy.monopaint.MultiFingerTapChecks

javac -d "$test_classes" app/src/main/java/io/github/mpdairy/monopaint/EdgeSwipe.java app/src/test/java/io/github/mpdairy/monopaint/EdgeSwipeChecks.java
java -cp "$test_classes" io.github.mpdairy.monopaint.EdgeSwipeChecks

javac -d "$test_classes" app/src/test/java/io/github/mpdairy/monopaint/DeviceChecks.java
java -cp "$test_classes" io.github.mpdairy.monopaint.DeviceChecks

bash scripts/test_inkwash.sh
