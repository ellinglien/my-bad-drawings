#!/usr/bin/env bash
# Desktop checks for the Inkwash tools. scripts/test_document.sh runs these too.
set -euo pipefail
cd "$(dirname "$0")/.."
# The app ships the notices as they are in the repo.
cmp -s THIRD_PARTY_NOTICES.md app/src/main/assets/THIRD_PARTY_NOTICES.txt || { echo "FAIL: THIRD_PARTY_NOTICES.md and app/src/main/assets/THIRD_PARTY_NOTICES.txt differ"; exit 1; }
# The built brush-lab pages are not tracked (2026-10-27); VoiceChecks and BrushNamesChecks read them, so build them first.
node docs/brush-lab/studio-src/build.cjs >/dev/null && node docs/brush-lab/match-lab-src/build.js >/dev/null
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
sources=(AppName AppRename HandOver Assets Cores TypeStyle BrushNames PaperNoise PaperGrain BrushTexture BrushSetting BrushStyle BrushCode Brush BrushCaptions BrushLibrary LibraryCleanup BrushBackup ToolBackup BrushImport ToolSwatches ToolSlots FingerTool BrushLab LabPages LabText LabLayout DrawLayout ToolPresets ToolsLayout ToolsPage ToolKits GrayPalette DotPattern ToneDocument DocumentCodec NotebookLayout NotebookExport NotebookPdf NotebookMigration DrawingBook PackedPage BookCodec BookPictures PagePicture RecoveryCodec RecoverySchedule LayerThumbnail BrushDirection ToolSettings DrawingStroke LiftingStroke DrawingFiles InkWashLayers InkWashSizes InkWashShades PenSpeed RoundFootprint WashCoverage PageTilt GravityFlow DryingWater SettlePacer SettleWorker WetWorkBudget BrushInk PaintLoad WetWatercolor WetCanvas WatercolorSession WetPaintStroke EraseBothStroke EraseKind DrawingNames WildBrush WildSample BrushPart PartDial PartChoice ModularFeatures ModularStyle ModularLocks ModularCode ModularBrush ModularVariations ModularWords BrushVoice ResolvedBrush FloatTiles StrokePoints StrokeMath StrokePaper StickGrain LineStamper Placement HakeRibbon BodyStamper BodySettle ModularCompose StrokeTones ModularStroke ModularSizes LegacyBrushes Prof PenBatch FootprintSweep QuickStroke CarryPlane Carry FloatBath FloatScan FloatTrace FloatPen FloatWater BristleSwarm StippleStamper HatchStamper HandHatchStamper HeldPen DisplayRecovery ToneDabs ToolStroke HaloGrowth Magnets MagnetDrag WordPacks WordBinLayout PageShade BlankPages NotebookNames PageClear PrintScreen CoverNoise CoverMarble CoverDesign CoverPattern CoverTexture NotebookCovers BookOptions NotebookTemplate NotebookOptions SurpriseTools PinchPan PageView CanvasViewport MultiFingerTap FillRegion FillTool HeldPaint ClearWater Mirrors MirrorStroke MirrorShow ShadeStrip SwatchSetLayout FlickFling LoadedPaint PageClock NotebookRules TimedPages SandTimer FadingInk StampNib StampStamper Crayon SpiroStamper SwatchPicker StayIn FilledShape GreyGradient StrokeLog DailyPrompt StripGesture TracingPaper GhostPlane CoverWear PageStacks LayerEdits LibraryCard CardArt CardSheet TimedSections PictureImport HoverNib FloatingSelection LassoPlanes EdgeHold PageExport PsdWriter)
checks=(AppRenameChecks HandOverChecks KindVisibilityChecks ToolBackupChecks RoundFootprintChecks EdgeRippleChecks LinkedEditChecks InkWashLayersChecks LayerPairChecks LayerThumbnailChecks InkWashSizesChecks InkWashShadesChecks WashCoverageChecks EraseBothChecks PenSpeedChecks DrawingNamesChecks DrawingRenameChecks WildBrushChecks WildSampleChecks BrushNamesChecks BrushTextureChecks BrushCodeChecks BrushChecks BrushLibraryChecks LibraryCleanupChecks ToolSlotsChecks ToolSwatchesChecks BrushLabChecks LabSlotsChecks LabLayoutChecks PartModelChecks ModularStyleChecks ModularCodeChecks ModularVariationsChecks ResolveChecks StrokeBasicsChecks LineStamperChecks BodyStamperChecks ModularStrokeChecks FastWetChecks ComposeShortcutChecks ModularBenchChecks LatticeChecks ModularBrushChecks ModularLabChecks DryingWaterChecks SettlePacingChecks SettleWorkerChecks GravityChecks PartSampleChecks LegacyConversionChecks DrawLayoutChecks FingerToolChecks ToolPresetsChecks ToolsPageChecks ToolKitsChecks VoiceChecks CoresChecks QuickStrokeChecks CarrySmudgeChecks FloatChecks BristleSwarmChecks ErasableChecks EraseKindChecks KindLayersChecks EraseAllChecks StippleChecks HatchChecks HandHatchChecks ScreenFillChecks BlendChecks DisplayRecoveryChecks StumpChecks ResistChecks HaloChecks MagnetChecks WordPacksChecks WordBinChecks NotebookFileChecks NotebookMigrationChecks NotebookExportChecks NotebookPdfChecks NotebookLayoutChecks NotebookPagesChecks SaveLessChecks PageShadeChecks PageColourChecks BlankPagesChecks NotebookNamesChecks PageClearChecks ScreenChecks CoverChecks CoverDesignChecks NotebookOptionsChecks ZoomStrokeChecks PinchPanChecks FillRegionChecks FillChecks WetSmudgeChecks MirrorChecks FillMirrorChecks GreyBarChecks FlickFlingChecks OilChecks SmudgeSizeChecks TrailChecks NotebookRulesChecks SandTimerChecks FadingChecks StampChecks CrayonChecks SpiroChecks SwatchChecks StayInChecks FilledShapeChecks GradientChecks StrokeLogChecks DailyPromptChecks StripGestureChecks TracingChecks CoverWearChecks PageStacksChecks CrispPageChecks LibraryCardChecks TemplateChecks TimedSectionsChecks NoErasingChecks PictureImportChecks WetPaintChecks LightOverDarkChecks StabilityChecks HoverNibChecks SelectionChecks LassoChecks ZoomFitChecks EdgeHoldChecks PageExportChecks)
# Shared by checks, not run on their own.
helpers=(Timing NotebookFixture NotebookBench CoverTextures PresetPictures)
javac -d "$classes" $(for s in "${sources[@]}"; do echo "$main/$s.java"; done) \
  $(for c in "${checks[@]}" "${helpers[@]}"; do echo "$test/$c.java"; done)
for c in "${checks[@]}"; do java -cp "$classes" "io.github.mpdairy.monopaint.$c"; done
# A notebook from this version opens in the one before notebooks (580a326): its block of pictures is ignored.
old_ref=580a326
if git rev-parse -q --verify "$old_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  for f in GrayPalette DotPattern ToneDocument DocumentCodec DrawingBook BookCodec; do
    git show "$old_ref:app/src/main/java/io/github/mpdairy/monopaint/$f.java" > "$old_src/$f.java"
  done
  javac -d "$old_classes" "$old_src"/*.java "$test/OlderNotebookCheck.java"
  java -cp "$classes" io.github.mpdairy.monopaint.NotebookFileChecks write "$old_classes/notebook.mpaint"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderNotebookCheck "$old_classes/notebook.mpaint"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the older notebook reader's check needs git and commit $old_ref"
fi
# A notebook whose covers are drawn on opens in the version before covers (2a352ed, the Manta's), without them; one that
# version wrote opens here with plain covers and is written again byte for byte.
cover_ref=2a352ed
if git rev-parse -q --verify "$cover_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  for f in GrayPalette DotPattern ToneDocument DocumentCodec DrawingBook PackedPage BookCodec BookPictures InkWashLayers Magnets; do
    git show "$cover_ref:$main/$f.java" > "$old_src/$f.java"
  done
  javac -nowarn -d "$old_classes" "$old_src"/*.java "$test/OlderCoversCheck.java"
  java -cp "$classes" io.github.mpdairy.monopaint.CoverChecks write "$old_classes/covers.mpaint"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderCoversCheck "$old_classes/covers.mpaint" "$old_classes/older.mpaint"
  java -cp "$classes" io.github.mpdairy.monopaint.CoverChecks older "$old_classes/older.mpaint"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the older version's check for covers needs git and commit $cover_ref"
fi
# A notebook in a cover design opens in the covers version (6e3bae8), its DSGN record passed by; one that version saved
# again opens here with its covers, in Classic Black Marble.
design_ref=6e3bae8
if git rev-parse -q --verify "$design_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  for f in GrayPalette DotPattern ToneDocument DocumentCodec DrawingBook PackedPage BookCodec BookPictures InkWashLayers Magnets CoverMarble NotebookCovers BookOptions PagePicture LabText TypeStyle; do
    git show "$design_ref:$main/$f.java" > "$old_src/$f.java"
  done
  javac -nowarn -d "$old_classes" "$old_src"/*.java "$test/OlderDesignCheck.java"
  java -cp "$classes" io.github.mpdairy.monopaint.CoverDesignChecks write "$old_classes/designed.mpaint"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderDesignCheck "$old_classes/designed.mpaint" "$old_classes/saved.mpaint"
  java -cp "$classes" io.github.mpdairy.monopaint.CoverDesignChecks older "$old_classes/saved.mpaint"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the covers version's check for cover designs needs git and commit $design_ref"
fi
# A notebook with options (template, Guides in PDF, tools) opens in the version before them (233ba6b), their TMPL, GPDF and
# TOOL records passed by; one that version saved again opens here with the default options.
options_ref=233ba6b
if git rev-parse -q --verify "$options_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  for f in GrayPalette DotPattern ToneDocument DocumentCodec DrawingBook PackedPage BookCodec BookPictures InkWashLayers Magnets Mirrors CoverMarble CoverNoise CoverDesign CoverPattern CoverTexture Assets Cores NotebookCovers BookOptions PagePicture LabText TypeStyle DrawingFiles; do
    git show "$options_ref:$main/$f.java" > "$old_src/$f.java"
  done
  javac -nowarn -d "$old_classes" "$old_src"/*.java "$test/OlderOptionsCheck.java"
  java -cp "$classes" io.github.mpdairy.monopaint.NotebookOptionsChecks write "$old_classes/options.mpaint"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderOptionsCheck "$old_classes/options.mpaint" "$old_classes/saved.mpaint"
  java -cp "$classes" io.github.mpdairy.monopaint.NotebookOptionsChecks older "$old_classes/saved.mpaint"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the version before options' check needs git and commit $options_ref"
fi
# A Tracing notebook (Paper, a ghost, steps) opens as drawn in the version before tracing paper (af06002), its PAPM, GHST and
# STEP records passed by; one that version saved again opens here Plain.
paper_ref=af06002
if git rev-parse -q --verify "$paper_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$paper_ref" app/src/main/java | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/OlderOptionsCheck.java"
  java -cp "$classes" io.github.mpdairy.monopaint.TracingChecks write "$old_classes/tracing.mpaint"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderOptionsCheck "$old_classes/tracing.mpaint" "$old_classes/saved.mpaint"
  java -cp "$classes" io.github.mpdairy.monopaint.TracingChecks older "$old_classes/saved.mpaint"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the version before tracing paper's check needs git and commit $paper_ref"
fi
# A notebook of stacked layers (one page of two layers) opens in the version before stacks (2ddb2a4), its STAK record passed
# by: each layer a page of its own, as drawn; one that version saved again opens here as two pages.
stack_ref=2ddb2a4
if git rev-parse -q --verify "$stack_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$stack_ref" app/src/main/java | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/OlderOptionsCheck.java"
  java -cp "$classes" io.github.mpdairy.monopaint.PageStacksChecks write "$old_classes/stacked.mpaint"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderOptionsCheck "$old_classes/stacked.mpaint" "$old_classes/saved.mpaint"
  java -cp "$classes" io.github.mpdairy.monopaint.PageStacksChecks older "$old_classes/saved.mpaint"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the version before stacks' check needs git and commit $stack_ref"
fi
# A page with mirrors opens in the version before them (2327d14), its MIRR section skipped.
mirror_ref=2327d14
if git rev-parse -q --verify "$mirror_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  for f in GrayPalette DotPattern ToneDocument DocumentCodec Magnets; do
    git show "$mirror_ref:$main/$f.java" > "$old_src/$f.java"
  done
  javac -nowarn -d "$old_classes" "$old_src"/*.java "$test/MirrorOlderReaderCheck.java"
  where=$(java -cp "$classes" io.github.mpdairy.monopaint.MirrorChecks write "$old_classes/mirrors.mpaint")
  java -cp "$old_classes" io.github.mpdairy.monopaint.MirrorOlderReaderCheck "$old_classes/mirrors.mpaint" $where
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the older reader's check for mirrors needs git and commit $mirror_ref"
fi
# A page with kinds of mark opens in the version before them (e288fe9), its KIND section skipped.
kind_ref=e288fe9
if git rev-parse -q --verify "$kind_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$kind_ref" app/src/main/java | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/OlderKindCheck.java"
  where=$(java -cp "$classes" io.github.mpdairy.monopaint.EraseKindChecks write "$old_classes/kinds.mpaint")
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderKindCheck "$old_classes/kinds.mpaint" $where
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the older reader's check for kinds needs git and commit $kind_ref"
fi
# A notebook whose cover's design is rubbed off opens in the version before (ca133f7), its WORN section skipped, the design whole.
wear_ref=ca133f7
if git rev-parse -q --verify "$wear_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$wear_ref" app/src/main/java | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/OlderWearCheck.java"
  java -cp "$classes" io.github.mpdairy.monopaint.CoverWearChecks write "$old_classes/worn.mpaint"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderWearCheck "$old_classes/worn.mpaint"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the older reader's check for cover wear needs git and commit $wear_ref"
fi
# A page with started Timed Sections opens as drawn in the version before them (a1328a8), its SECT section skipped.
pace_ref=a1328a8
if git rev-parse -q --verify "$pace_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$pace_ref" app/src/main/java | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/OlderSectionsCheck.java"
  java -cp "$classes" io.github.mpdairy.monopaint.TimedSectionsChecks write "$old_classes/sections.mpaint"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderSectionsCheck "$old_classes/sections.mpaint"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the version before timed sections' check needs git and commit $pace_ref"
fi
# The native renderer (app/src/main/cpp/compose.h) against the Java one, on white paper and on a shaded page.
javac -d "$classes" -cp "$classes" app/src/test/java/io/github/mpdairy/monopaint/ComposeFixture.java
cc -std=c99 -O2 -Wall -Wextra -Werror -o "$classes/compose_check" app/src/test/cpp/compose_check.c
java -cp "$classes" io.github.mpdairy.monopaint.ComposeFixture "$classes/compose.bin"
"$classes/compose_check" "$classes/compose.bin"
java -cp "$classes" io.github.mpdairy.monopaint.ComposeFixture "$classes/compose.bin" 97
"$classes/compose_check" "$classes/compose.bin"
# And on a notebook cover, its marble under the layers.
java -cp "$classes" io.github.mpdairy.monopaint.ComposeFixture "$classes/compose.bin" cover
"$classes/compose_check" "$classes/compose.bin"
# The end of a stroke shown through the panel's fast update (app/src/main/cpp/panel_end.h) against a fake driver.
cc -std=c99 -O2 -Wall -Wextra -Werror -o "$classes/panel_end_check" app/src/test/cpp/panel_end_check.c
"$classes/panel_end_check"
