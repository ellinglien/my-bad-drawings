# Scribulor and MonoPaint: integration notes

For working with MonoPaint's developer (mpdairy) later: what Scribulor changed,
where the line between "engine" and "Scribulor app" runs, and what could go back
upstream. Docs only; nothing here is a commitment. Elling contacts the
developer himself.

Last checked: 2026-10-27, against `upstream/main` at `5f764ec` (2026-10-22; nothing new upstream since; merged on
branch `upstream-lasso`, 2026-10-23).

## 1. Purpose and status

- **Scribulor** is a fork of [MonoPaint](https://github.com/mpdairy/monopaint),
  a drawing app for the Supernote Manta and Nomad. MonoPaint is MIT-licensed
  (`LICENSE`, © 2026 mpdairy); Scribulor keeps that license and notice.
- **What it is:** a deliberately minimal pen-and-wash sketchbook (see
  `PHILOSOPHY.md`): ten toolbar slots plus an eraser, black/white/grey, nothing
  that keeps moving after the pen lifts but wet paint, for its drying time (and,
  with gravity, when the tablet tilts). On top of MonoPaint's fast e-ink pen
  path, documents and layers, it adds its own toolbar, a brush engine (the
  "six-part" modular brush) and Brush Lab, a screen for making brushes.
- **Identity:** installs beside MonoPaint (`applicationId ing.ell.mybaddrawings`, `app.helpyy.inkwash` until 2026-10-30,
  library folder `My Bad Drawings`, own signing key). Java package is still
  `io.github.mpdairy.monopaint` (see section 6).
- **Owner's intent:** collaborate with MonoPaint, ideally by offering the
  brush engine as a shared module and sending generic fixes upstream.

### How far it has diverged (`upstream/main...inkwash-v2-lab`)

| Measure | Value |
|---|---|
| Scribulor commits not in upstream | 1119 (2026-10-13 to 2026-10-27) |
| Upstream commits not in Scribulor | 0 (`8743286` merged 2026-10-15, `cc40de2` to `5f764ec` 2026-10-23) |
| Files changed, whole repo | 924, +195,598 / −3,188 |
| of which app code (`app/src/main`) | 495 files, +64,835 / −1,992 |
| of which desktop checks (`app/src/test`) | 208 files, +35,567 / −12 |
| of which docs (specs, plans, prototypes; finished ones in `docs/archive/`) | 155 files, +89,251 / −67 |
| Java classes upstream has | 89 |
| upstream classes Scribulor modified | 49 (+ `viewport_bitmap.c`, `layers.h`, `CMakeLists.txt`) |
| upstream classes Scribulor deleted | 4 (`AppSettingsDialog`, `ImageImport`, `PaletteEditor`, `PictureFiles`) |
| upstream classes untouched | 36 |
| new Scribulor classes | 224 |

Section 2's lists were written class by class as each change landed; where they and these counts differ, the counts
are current (2026-10-27).

The divergence is mostly *additive*: new classes beside upstream's. The edits
to upstream files are concentrated in `DrawingPad`, `PaintActivity` and
`ToneDocument`.

## 2. Map of the code

All classes are in `app/src/main/java/io/github/mpdairy/monopaint/`.
Categories: **(a)** upstream, untouched; **(b)** upstream, modified by Scribulor;
**(c)** Scribulor engine, pure Java (no `android.*`), candidate shared module;
**(d)** Scribulor UI/product layer, Scribulor-only; **(e)** tooling.

### (a) Upstream, untouched (64)

Documents and storage: `DrawingBook DrawingStorage
PngExport RecoveryCodec ExportSize`. Display and e-ink: `DirectEink
DirtyRegions DotGray DotPattern GrayPalette CanvasViewport
PageTurnDisplay PanelCoordinates PanelPreviewLayout QuarterTurnLayout Device
ProbeActivity`. MonoPaint's tools and wet paint: `ToolStrokes
ToolLibrary ToneDabs Footprint BristleTexture BrushInk PaintRub ShapeStroke
ShapeGeometry ShapePreview FloodFill WetDroplet WetWorkBudget
WatercolorWash BlowDryer MaskPeelTouch` (`CanvasGravity` is now reused by Scribulor's gravity: see (b); `BrushInk` and
`WetWorkBudget` are used, unchanged, by Wet paint: see "Wet paint" at the end). MonoPaint's UI (still
built, hidden by Scribulor): `Toolbar ToolRail ToolControls ToolIcons
ToolSettingsPanel SettingsForm SettingsScroller WetnessBar
LayersPanel SidePanel EdgeBars EdgeSwipe
PageActionButton PageLabel PageOverview Popups RotationPrompt
RotationSuggestion ControlHost ControlRaster DropletIcon
FullscreenController MultiFingerTap ZoomButtonTouch PaintState`. Since Zoom came back (2026-10-19, below) Scribulor uses
`CanvasViewport`, `ZoomButtonTouch`, `MultiFingerTap` and `ViewportBitmap`'s zoomed raster again, unchanged; the first
three are in `scripts/test_inkwash.sh` as well as `test_document.sh`.

### (b) Upstream, modified by Scribulor

| File | What changed, and why |
|---|---|
| `ToneDocument` | Block writes (`setTones`, `coverTones`, `smearTones`, `eraseTones`), reads (`strokeBaseTones`, `strokeBasePixels`, `strokeBaseKept`, `layerDarkness`, `compositeToneWithout`, `compositeToneKept`, `baseComposite`, `foundTiles`, `foundComposite`, `visibleLayers`, `touched`, `maskShield`, `maskLiftable`), `restack`/`setLayersVisible`, linked undo (`finishWithPrevious`), tile-at-a-time settle; one place for undo capture; Erasable's per-layer share (`Layer.share`, `begin(erasable)`, the one write rule), the masking fluid's too (`beginFluid`; `maskHatch` hatches only what lifts). The engine writes the page through these. |
| `DocumentCodec`, `BookCodec` | TSM5: tagged sections after TSM4's body (Erasable's shares, `ERAS`; wax's, `MSHR`; the kinds of mark, `KIND`; kind planes, `PLNS`, a layer's look in the layers' part; Show Kinds' steps, `KSHN`, a byte), written only when a page needs one; the single-page fast paths open TSM4/TSM5 (see "Erasable", "Resist" and "Kind layers" below). |
| `DrawingPad` | Draws with Scribulor strokes on ink/wash layer pairs; the finger tool (erase, smudge or a slot's brush); the hovering nib's outline (`HoverNib`); erasing shows paper; pen speed; auto full-refresh at pauses; drying ticker; native area render; the hatch drawn by a static `drawMask` Brush Lab's test page shares; two fingers a tap until they move, then MonoPaint's pinch and pan (`PinchPan`), the page's map from `PageView`. Heaviest edit. |
| `PaintActivity` | Scribulor bar replaces MonoPaint's controls (upstream ones built but hidden, `53b444a`); automatic names, rename/delete, PNG copy on leaving; Brush Lab and Tools page launch (Tools → Brush Lab → Tools, the slots' start carried through); slots, swatches; the side bar in MonoPaint's tool column place. |
| `ViewportBitmap`, `viewport_bitmap.c` (+ new `compose.h`) | Native rendering of a changed area, not only whole pages (`84a568e`). |
| `WetWatercolor` | Wet paint (2026-10-22): hooks marked "Inkwash", upstream's behaviour unchanged without them: `gesture(erasable, kinds)` (its own slices begin as erasable and of the kind as the stroke that woke it), `setFrames`, `rest()`, `rewet(wake)` (take up what something else changed), `finishStroke(withPrevious)`, and in `paintMask`'s whole-canvas mixing `LIGHT_MIXES` (opaque paint lighter than the wet paint under it covers more than it mixes; light over dark, 2026-10-22). Now live engine code under the six-part engine's Wet choice. Generic: any host can drive it beside other tools. |
| `PaintLoad` | Wet paint: `left(share)` sets its own paint from Smudge's Paint Load (`LoadedPaint`) instead of its own run out. |
| `DrawingStroke` | Default `sample(..., long time)` so strokes can follow pen speed (`bcee59b`); default `present()`, called once an input event, so a stroke can compose once an event instead of once a sample (`bc7b014`); default `zoom(scale)`, so a stroke reads the pen's speed on the glass when zoomed. All backward compatible. |
| `BrushDirection` | `azimuth`/`tiltLean`: tilt read one way, in doubles, for the engine. |
| `BrushStamp` | Per-thread scratch paths (`ThreadLocal`) so Brush Lab samples on a background thread. |
| `PressureStroke` | None since the audit (2026-10-19): `headThickness`, for Scribulor's old pens, went back to upstream's. |
| `ToolSettings` | `travel` made visible, an inverse helper `pressure` (the checks use it; `pressureFor`, for the old pens, went at the audit). `travel` has a knee since 2026-10-29 (the old line to .30, then on to 1 at .78; firmware 3.31.45, `docs/TILT_INPUT.md`). |
| `ToolStroke`, `NativePen` | Read pen pressure through `ToolSettings.travel` instead of their own copies of the `.05–.45` line (2026-10-29). |
| `DocumentStore`, `DrawingFiles`, `DrawingBrowser` | Rename and delete drawings, following `AtomicFile` backups. |
| `Ui`, `ToolButton`, `PressOutline`, `PaletteSwatch` | Inverted selection, selected frame, pointer-indexed `outside` (palm fix), Brush Lab helpers. `ToolButton.faces`: `iconOnly` without asking Android for a frame, for a change the caller puts on the panel (2026-10-24). `ToolButton.slow`, `PressOutline.inverted` and `clicked`, `ControlRaster.draw(…, binary)`: a slow button's press inverted in one binary update, let go in the click's own frame (2026-10-25, see "Press feedback"). `markerArea` of a bar row (`SlotTile.Face`) is its side bar alone (2026-10-25, see "Side bars alone"). |
| `ShadePicker` | Its strip's numbers and pixels (`ShadeStrip`, pure) and its pen and marker feedback (`StripMarker`) moved out, unchanged, so Scribulor's grey bar draws and follows the pen with the same code (see "The grey bar" below). |
| `PaintPreferences` | Brush Lab Basic/Advanced flag. |
| `SelectionFeedback` | `cleanAfterClose` (a closed popup's patch cleaned, 2026-10-23) and captures on white. The several-patch `update` for the Tools page's slot blink (2026-10-19) went with the blink. Updates of views inside a view whose update is running gather into its one present (`gathering`, 2026-10-24, see "Fast pickers"). A nested `updateBinary` restores the flag it found (2026-10-25, see "Snappy UI"). A tool pick updates only the patches gathered inside it, ink and paper boxes in mode 5, and a menu is put on the panel before its window shows (`updateParts`, `presentAhead`, 2026-10-25, see "Snappy UI 2"). Otherwise the one-patch path is as upstream's. |
| `CanvasGravity` | Reused for Scribulor's gravity: also made from a `Context` with its own switch, `read()` gives a `PageTilt`, the display's rotation honoured (see "Gravity" below). |
| Build and identity | `app/build.gradle` (applicationId, version, signing, library folder), `AndroidManifest.xml` (Brush Lab activity; dropped `dev.tilesmile` alias), `strings.xml`, launcher icon, `README.md` (icon line only), `CLAUDE.md`, `CHANGELOG.md`, notices (Phosphor icons). |

#### Design pass (2026-10-15): upstream files touched

The look lives in Scribulor's `Theme`; MonoPaint's `Ui`, `ToolButton`,
`SettingsForm` and the screens only MonoPaint shows (`Toolbar`, `LayersPanel`,
`SidePanel`, `FullscreenController`; `PaletteEditor` and `AppSettingsDialog` deleted at the refocus, 2026-10-24) are
untouched. One seam: MonoPaint dialogs and the file menu that Scribulor shows are
restyled after MonoPaint builds them.

| File | Lines | Why |
|---|---|---|
| `PaintActivity` | 4 | `Theme.fonts` at start; `Theme.restyle` after the file menu's `SettingsForm.style`; `Theme.dialog` at the end of `compactDialog` and `orientDialog`. |
| `DrawingBrowser` | 1 | `Theme.restyle` on each Open/Save row (built after the dialog). |
| `PaintPreferences` | comment | "Simple" reads "Basic". |
| `WildSample` (Scribulor engine, not upstream) | 1 | `reach` package-visible for `InkWash.tileCap`. |

#### One engine (2026-10-15): upstream files touched

None. Every brush became a six-part brush (plan `docs/archive/superpowers/plans/2026-10-15-one-engine.md`) and Scribulor's old
engines were removed; MonoPaint's own tools (`PressureStroke`, `ToolStrokes`, `WetWatercolor`, …) are untouched and
draw MonoPaint's mode as before. Two Scribulor edits to upstream files are now unused by Scribulor and could be undone
to shrink the divergence: `PressureStroke.headThickness` and `ToolSettings.pressureFor` (both undone at the audit, 2026-10-19).

#### Speed on the Manta (2026-10-15): upstream files touched

Branch `inkwash-perf`, rebased onto the one-engine work and fast-forwarded into `inkwash-v2-lab` (`2874178`…). The
engine work is in Scribulor's own classes (`ModularStroke`, `BodyStamper`, `BodySettle`, `FloatTiles`, `HakeRibbon`,
`LineStamper`, `Placement`, `StrokeMath`, `ModularCompose`, new `Prof`); upstream files:

| File | Lines | Why |
|---|---|---|
| `DrawingStroke` | 6 | Default `present()`: an input event's samples in, then one compose (`bc7b014`). |
| `DrawingPad` | 1 | `presentInput` calls `stroke.present()` before rendering (`bc7b014`). |
| `ToneDocument` | 2 | `touched(x, y)`: whether the gesture changed a tile, so a compose can pass over tiles it never touched (`4f64db4`). |
| `app/build.gradle` | 11 | A `bench` build type (`-Pbench`): release speed, own id `.bench`, own library folder (`2874178`). |
| `WidthInstrumentation` (androidTest) | 8 | `-e modularPerf true` runs `ModularPerfChecks`/`ModularPerfBench` (`2874178`). |
| `app/src/bench/AndroidManifest.xml` (new) | 10 | Bench build: profileable, no launcher entry (`cccaafb`). |

#### Brush pen tilt range (2026-10-15): upstream files touched

None. Engine files (Scribulor's own): `LineStamper` two lines (tilt width and flattening through
`ResolvedBrush.Pen.tiltWidth`/`tiltFlat`), `ModularStroke` two lines (what settles after lift comes from
`ResolvedBrush`); a new hand dial, Tilt range, that M codes write only when wide (engine spec, "Tilt range").

#### Settle pacing and wet indicator (2026-10-15): upstream files touched

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | 1 + 1 (−7) | `onTouchEvent` reports each pen event's handling time and age to the session's pace (`WatercolorSession.input`); `settleDrying` is now `DryingTicker.settleNow` (the same steps, moved so the wet indicator's tap and Brush Lab share them). |

Read, not edited: `DropletIcon.outline()` (the droplet's path) for Scribulor's `WetIndicator`. Engine (Scribulor's own):
new `SettlePacer`; `WatercolorSession` (`pace`, `input`, the paced `tick`, `wet`, `wetChanged`); `ModularStroke` (a
Fast-wet re-settle's box shows when the pace allows, at lift at the latest). UI (Scribulor's own): `DryingTicker`, new
`WetIndicator`, `InkWashBar`, `InkWash`, `BrushLabActivity`, `BrushTestPad`, `LabLayout`. See section 5.

#### Speed round 2: batched pen input, four cores (2026-10-15): upstream files touched

Branch `inkwash-perf2` (engine spec, "Round 2: what the pen waits for"). Engine (Scribulor's own): new `Cores` (the
helper pool, `BodySettle`'s moved onto it), `ModularCompose` (bands; `composeLattice`), `ModularStroke`, `LineStamper`,
`BodyStamper`, `HakeRibbon`, `StrokeMath.blur`, `FloatTiles.between`, `Prof`; glue: new `PenBatch`, `StrokeTiming`.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +40 −6 | `openBands`/`closeBands`: while open, writers on several threads may each write and read their own whole tile rows (Undo captures and the dirty area under a lock, the base-tile cache passed over); `TILE` package-visible; `coverTones` looks the gesture's base tile up once a tile. Generic: any tool could compose in bands (`4b80663`). |
| `ViewportBitmap` | +10 −2 | `compose(area)`: an area of 64k pixels or more rendered in row bands on several cores (`fdbc420`). |
| `DrawingPad` | +60 −5 | Scribulor pen strokes gather samples (`PenBatch`) and draw them from a posted runnable (`drawBatch`, `flushBatch` before finish); stroke presents spaced by 1.5 × their recent cost (8–100 ms, `presentGap`, now `PresentPace`); the present's parts into the stroke timing; `liveFastPlane` (off) begins the fast preview at pen-down for Scribulor strokes (`6193c27`). |
| `DirectEink`, `cpp/direct_eink.c` | +10 / +20 | The present's parts timed (turn, compare, copy, driver call; `presentParts`) (`6193c27`). |
| `WidthInstrumentation` (androidTest) | +10 | `-e liveStrokes true` runs `LiveStrokeBench` (`b38307e`). |

#### Gravity (2026-10-15): upstream files touched

Branch `inkwash-gravity` (plan `docs/archive/superpowers/plans/2026-10-15-gravity.md`; engine spec, "Gravity"). The page's
tilt comes from **MonoPaint's own `CanvasGravity`** (its accelerometer handling, low-pass filter, first reading used as
is, and its flat dead zone), reused rather than duplicated:

| File | Lines | Why |
|---|---|---|
| `CanvasGravity` | +20 −10 | Also made from a plain `Context` with its own `enabled` switch (MonoPaint's constructor passes its "Watercolor uses gravity" setting, as before); `read()` gives a `PageTilt` (its dead zone and strength, moved there so the engine shares them); the reading turned by the display's rotation (`PageTilt.fromDevice`; 0 while Android stays in portrait). `apply(WetWatercolor)` gives MonoPaint's wet paint the same pull as before. Generic: any tool could read the page's downhill this way. |

Engine (Scribulor's own): new `PageTilt`, `GravityFlow`; `DryingWater` (a flow beside its patch, made the first tilted
tick), `WatercolorSession` (`tilt`, `wantsTilt`, `tiltChanged`, `strokeReadsTilt`), `ResolvedBrush` (`Grav`, `dust`,
`wetMs`, the hand's page tilt), `StrokePoints.ga`, `StrokeMath.gSize/gDark/gWidth`, the stampers (`LineStamper`,
`BodyStamper`, `HakeRibbon`, `Placement`, a line or two each), `ModularStroke` (Quick's window, dust, the hand's
reading), `StrokePaper` (a constructor from a grain, for the checks), `Prof` (gravity's sections); the dials
(`PartDial`, `PartChoice`, `BrushPart`, `ModularCode.ADDED`, `ModularBrush`, `ModularStyle`, `ModularVariations`).
UI (Scribulor's own): new `TiltFeed`; `InkWashBar` and `BrushLabActivity` (one each), `BrushLab`, `HandGrid`, `LabLayout`,
`LabSettings`, `ModularWords`, `ToolPresets`. Not touched: `WetWatercolor` (MonoPaint's flowing watercolor) and its
gravity, which stay MonoPaint's own; `DrawingPad` and `PaintActivity`.

From MonoPaint (`monopaint-speed-techniques.md`'s ranking): measuring the present's parts (1); cost-aware present
spacing (3), and drawing from a posted runnable so queued input runs first; spare-core sharing (6, the `share()`
pattern of `f9d40db`/`281373d`, generalised as `Cores`); the fast binary plane for the live stroke (2) is wired from
upstream's `beginFastPreview` but off (the Manta has the plane only from firmware 3.30.45). Not taken: presenting from
another thread (4); batching made it unneeded so far.

#### Drawing screen C and the Tools page (2026-10-15): upstream files touched

Spec `docs/archive/superpowers/specs/2026-10-15-tools-page-design.md`. The left edge reuses MonoPaint's own place for a tool
column: `PaintActivity.body`, the row beside the pad where MonoPaint's `Toolbar.toolScroll` goes on the drawing hand's
side (hidden by Scribulor), so `CanvasViewport`'s "paper under the bars" and the pad's page ↔ view mapping, input and
e-ink connection work unchanged.

| File | Lines | Why |
|---|---|---|
| `PaintActivity` | +11 −1 in `showInkWashBar` | Scribulor's arrangement in either turn: root vertical, its bar on top at `DrawLayout`'s height, the side bar (`SwatchBar`) in `body` where MonoPaint's tool column goes, the pad beside it (landscape no longer uses MonoPaint's turned header arrangement, which was hidden anyway). |
| `PaintActivity` | +25 −1 | `openTools`/`toolsReturned`: start `ToolsActivity` with the slot and the app's turn; put the chosen brush in the slot (`ToolSlots.use`, one Undo step), then Brush Lab if asked. |
| `DrawingPad` | 1 | The auto full refresh redraws both of Scribulor's bars (`inkWashBar.invalidate()`), not only the top one. |
| `AndroidManifest.xml` | 2 | `ToolsActivity` (portrait; turned by its own `QuarterTurnLayout`). |

Read, not edited: `QuarterTurnLayout` (turns the Tools page as the drawing is), `SelectionFeedback` (the side bar's
partial updates), `Toolbar`'s column placement in `applyToolboxSide`.

#### One side toolbar (2026-10-15): upstream files touched

Spec `docs/archive/superpowers/specs/2026-10-15-tools-page-design.md`, "One side toolbar". The top bar and design C's side bar
merge into one column (`InkWashBar`), still in MonoPaint's tool column place (`PaintActivity.body`), so the pad's page
↔ view mapping, input, zoom and e-ink connection are unchanged; `DrawingPad` and `CanvasViewport` untouched.

| File | Lines | Why |
|---|---|---|
| `PaintActivity` | +4 −6 in `showInkWashBar` | No bar on top of `root`: the one column goes in `body` on the drawing hand's side, at `DrawLayout`'s width, then `arrange`. |
| `PaintActivity` | +4 −4 in `menu` | The file menu (its only caller is Scribulor's menu button) opens beside its anchor, toward the page, level with it, instead of below it. |

Scribulor's own: `InkWashBar` (the column; panels open sideways), `DrawLayout` (the fit along the height, folding the
layers), `SwatchBar` (now the faces' group inside the column), `SlotTile` (tiles in a column as well as a row),
`DrawLayoutChecks`.

#### Quick strokes (2026-10-15): upstream files touched

Branch `inkwash-quick` (engine spec, "Quick then render"). A stroke too heavy to keep up live on the Manta, decided at
pen-down from the brush and its size (`QuickStroke.keepsUpTo`), draws a flat preview of the brush's footprint under the
pen while a thread of its own draws the true stroke from the samples as they come (its own buffers only; with helpers
of its own at the lowest priority, so it takes only the cores the pen's thread leaves); at lift the preview is taken back and the true
stroke composed, while the wet indicator shows. Engine (Scribulor's own): new `QuickStroke`; the stampers' geometry as
methods (`LineStamper.nib`, `BodyStamper.shape`, `HakeRibbon.thickness`/`target`, `ResolvedBrush.Pen.tilts`: no pixel
changes), `ModularStroke` (`selectLayer`, `retapers`, `handTilt`/`readsTilt`, `render`: its own tilt source, unpaced,
its gesture `begin`s later), `ModularCompose` (`previewWash`, `flatTone`), `Cores.quiet`, `PenBatch.feed(stroke, i)`/
`moveTo`, `WatercolorSession.developing`, `Prof` (two sections). UI (Scribulor's own): new `QuickLift`, `BrushTestPad`,
`InkWash`.

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | +20 −2 | At pen-up a `QuickStroke` stays the pad's stroke while `QuickLift` looks in on its render (`liftQuick`); a pen-down, a finger erasing and the droplet's tap (`settleDrying`) finish it first (every other edit already calls `finishStroke`); `stroking()` is false meanwhile so the droplet shows. |

Not touched: `DrawingStroke` (a quick stroke is a `DrawingStroke`; the pad knows `QuickStroke` by `instanceof`, as it
knows `EraseBothStroke`), `ToneDocument` (the preview is a gesture cancelled at lift, the true stroke a gesture of its
own: one undo step), MonoPaint's tools. The eraser keeps its path: at its Large size (220 px) it costs about 0.14 ms a
sample on the Manta (desktop × 20), well within the pen.

#### Nomad pass (2026-10-15): upstream files touched

Branch `inkwash-nomad` (lab spec, "Nomad pass"). UI (Scribulor's own): `LabLayout`, `LabSettings`, `PartCard`, `HandGrid`
(Advanced at 12 sp), `ToolsLayout`, `ToolsActivity` (the frame's words never cut), `InkWashBar` (a layer thumbnail
put back at its old size draws its picture again). Engine (Scribulor's own): `PartDial.shown` says "10 mm" for the Wet
window's top (was "10.0 mm"). Tooling: new `ScreenShots` (androidTest) and `scripts/screens_device.sh`.

| File | Lines | Why |
|---|---|---|
| `WidthInstrumentation` (androidTest) | +9 | `-e screens true` runs `ScreenShots`: the lab, the Tools page and the drawing screen at the Manta's and the Nomad's sizes, in the bench build. |

#### The panel's side (2026-10-15): upstream files touched

Branch `inkwash-panel` (engine spec, "Quick then render", "The panel's side"). Counting the e-ink controller's frame
interrupts showed that on the Manta's firmware 2606141001 the gray update (mode 7) and MonoPaint's pen request (mode 9)
both keep the panel busy about 270 ms, and driver mode 5 about 145 ms; a pixel shows only an update's time after its last
change, so a live brush that keeps changing the pixels under a big nib shows late. Scribulor's own: new `StrokePanel`
(every pen stroke but the eraser goes to the panel through the fastest update while the pen is down), `QuickStroke`
(`PANEL`, `panelReach`: past 3 mm every mark that changes pixels under the pen goes quick), `ModularStroke.lineReach`,
`BrushTestPad` (the same for Brush Lab's pad); bench: `PanelFrames`, `LiveStrokeBench` (modes, `hold`).

| File | Lines | Why |
|---|---|---|
| `cpp/direct_eink.c` | +5 −4 | Mode 5 (flags 0, main plane) accepted for exact black/white pixels, checked as mode 9's are. |
| `Device` | +9 | `fastStrokeMode()`: the driver mode for a stroke's black/white pixels on this tablet and firmware (the Manta before 3.30.45: 5; else none known). Only `Device` names tablets. |
| `DrawingPad` | +31 −14 | An Scribulor pen stroke opens `StrokePanel`'s session at pen-down (the unused `liveFastPlane` switch goes); `endStrokePreview` ends it before the stroke's last render and sends what it showed plus that render as one forced gray update. |

Generic: any MonoPaint pen or preview could use mode 5 on that firmware; the Nomad and 3.30.45 are unmeasured, so
`fastStrokeMode` says none there and MonoPaint's mode 9 path is used where it exists. A note for mpdairy: on this
firmware a forced gray refresh of unchanged pixels returns 17 and drives nothing (the driver compares with what the
panel shows), so `refresh` does not clear ghosts there. Mode 1 runs 75–83 frames (about 1 s, a full flashing update) and does
redrive unchanged pixels, but afterwards the driver takes the area for white whatever its pixels (a gray update of white
over it then returns 17), so it is no drop-in refresh either. Mode 15 restarted the Manta: never send modes the firmware's own library doesn't.

#### Lift after the fast update (2026-10-16): upstream files touched

Branch `inkwash-liftbug` (engine spec, "Lift after the fast update"). The fast update left quick strokes ghosted after
lift. The driver drives only pixels asked to change from its record, and the fast update records what it showed while
leaving pixels part-driven, so the gray update at lift passed by every pixel whose final the preview had shown (99% of
a Gouache stroke). Strokes take the gray update again (`StrokePanel.wants`: the bench only). Wherever a fast session is
still used, its end goes through `DirectEink.endFast`, which has the driver drive all of its pixels again (at the price
of a blink). Scribulor's own: `StrokePanel` (`wants`, the bench's `redrive`/`endNear`), `BrushTestPad` (its fast session
ends through `endFast`, no `forceNext`); new `cpp/panel_end.h` (pure C) and its desktop check
`app/src/test/cpp/panel_end_check.c`; bench: `LiveStrokeBench -e cases` (either screen, per-case panel, bands, pressure).

| File | Lines | Why |
|---|---|---|
| `cpp/direct_eink.c` | +37 | `nativeEndFast`: `panel_end` over the two sessions' records and the gray session's plane, submitting gray updates (a busy display waits a ms before a submit again). |
| `DirectEink` | +26 | `endFast(fast, bitmap, dirty)`: through the buffer on a turned page as `present` is; a pair not sharing a plane or the bench's old end refreshes as before. |
| `DrawingPad` | +32 −12 | `endStrokePreview` keeps the stopped fast session (`endingFast`, `endingArea`) until `endFast` has shown it with everything pending, retrying while busy; `flush` does that first. |

Generic: any MonoPaint preview on a fast plane that ends with a gray update over the same plane has the same trap on
this firmware. Where the final keeps the preview's pixels, a forced refresh drives nothing.

#### Smudge (2026-10-16): upstream files touched

Branch `inkwash-smudge` (spec `docs/archive/superpowers/specs/2026-10-16-smudge-design.md`). Engine (Scribulor's own): new
`Carry` (the reservoir and its dabs, big ones on `Cores`) and `CarryPlane` (the stroke's own layer as found at pen-down,
premultiplied, tile by tile); `ResolvedBrush` (`Carry`, Own paint scaling cover, opaque Lift's tint), `ModularCompose`
(the stroke's paint over the plane, written through `smearTones`), `ModularStroke` (the pass after the stamps;
`reachCarry` for a quick render), `QuickStroke` (the pen thread fills the plane before the render takes a sample; a
smudge's dab in the quick rule, `CARRY`), `ModularFeatures` (`CARRY` on; `late`, the Bristles branch's late roll),
`ModularStyle`/`ModularVariations` (that late roll, copied unchanged from `inkwash-bristles`), `ModularWords`, `Prof`.
UI (Scribulor's own): `ToolPresets` (Stump), `WildSample` (a smudge's samples over a band on its own layer), `LabLayout`.
Not touched: `DrawingPad` (settling first is `InkWash.stroke`'s `settleNow`, as before), MonoPaint's tools.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +38 −6 | `strokeBasePixels`: the gesture's found tones with their coverages (premultiplied: what a smudge reads, from the gesture's captures, so a quick render reads the page as the pen found it). `smearTones`: `coverTones` with the whole coverage floor given (the smeared coverage, not the found one), so a smear can thin a layer as well as darken it without a light halo over the layer below. Same Undo capture, dirty area and masking-fluid shield. Generic: any smudge or blend tool could write this way. |

#### The wait after lift (2026-10-16): upstream files touched

Branch `inkwash-lift` (engine spec, "The wait after lift"). Engine (Scribulor's own): new `StrokeTones` (a stroke's tones
composed off the page, written in one pass) and `LiftingStroke` (a stroke whose lift's work runs off the pen's thread,
beside `DrawingStroke`, which stays untouched); `ModularCompose` (composes onto a `Page`: the document or `StrokeTones`;
a pixel without paint passes coverage −1, read as 0), `ModularStroke` (`develop`, the Drying lift split into its
buffers' part and its compose, `lift`/`develop(nanos)` for a live Drying stroke), `QuickStroke` (composes ahead, the
lift's work on its thread), `WatercolorSession.onlyRendering`. UI (Scribulor's own): `QuickLift` (either kind of
`LiftingStroke`), `BrushTestPad`, `WetIndicator` (a render's droplet only after a second), `InkWashBar`,
`BrushLabActivity`.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +30 −6 | `shareBase`/`baseTones`: while shared, another thread may read what the gesture's layer held when it began (a changed tile from its Undo capture, any other from the page), so a quick render composes ahead while its preview draws; captures, a gesture's begin and its cancel take the capture lock then. Generic: any tool rendering a stroke on another thread could read its base this way. |
| `DrawingPad` | +6 −5 | `liftQuick` hands any `LiftingStroke` that has work after lift to `QuickLift` (was: `QuickStroke` only). |

#### Float (2026-10-16): upstream files touched

Branch `inkwash-float` (spec `docs/archive/superpowers/specs/2026-10-16-float-design.md`): suminagashi, a fourth Drying choice.
Engine (Scribulor's own): new `FloatBath` (floating paint as outlines, its ops, log and snapshots), `FloatScan` (the
scanline film), `FloatTrace` (a stroke's films), `FloatPen` (a stroke on the water), `FloatWater` (the bath on its
layer, one undo step to its print); `ResolvedBrush` (`Float`, `Life.FLOAT`; no Smudge reservoir on water),
`PartChoice`/`PartDial` (`FLOAT`, `F_SPREAD`, `DRIFT`, appended), `ModularFeatures` (`FLOAT`, `varies`),
`ModularStroke` (stamps but composes nothing; frames under the pen; films at lift), `QuickStroke` (Float always quick,
the render thread's frames shown by the pen's thread), `WatercolorSession` (the bath beside the Slow patch:
`settleBefore`, ticks, `undo`), `ModularStyle` (`rollFloat`, from the seed after every part), `ModularVariations`,
`ModularWords`. UI (Scribulor's own): `InkWash` (`settleBefore`), `ToolPresets` (Sumi, Clear water), `WildSample`
(a Float tile's rings), `BrushLab` (Lay down greyed on water). Not touched: `DrawingPad`, `DryingTicker`, `WetIndicator`,
`TiltFeed`, MonoPaint's tools.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +2 | `undoSteps`: how many steps Undo holds, so a bath whose continuation emptied its step (it went back to the page as found) starts a new one next time. Generic. |
| `PaintActivity` | +2 −1 | `undo`: while paint floats, the session takes back its last stroke (`WatercolorSession.undo`), else the page's Undo as before. |

#### Settling off the UI thread (2026-10-16): upstream files touched

Branch `inkwash-settle` (engine spec, "Settling off the UI thread"). None: no upstream file changes. Engine (Scribulor's
own): new `SettleWorker` (the thread a session's wet paint works on); `WatercolorSession` (`worker`; `tick` starts a
tick there and writes it when done; what laying the wash or printing the bath now writes worked out there ahead, written
by `settleNow` without waiting for a tick); `DryingWater` (`work`/`apply`, `finishing`, `lift`, `build`, `stop`: a
step or the finish worked out off the page into a `StrokeTones`, the lift's read and the lay on `Cores`);
`GravityFlow.TonePage.flush` (into a `StrokeTones`); `FloatWater` (`work`, `printing`, `show`, `tones`: a drift tick
and the print worked out off the page); `StrokeTones` (`smeared`: a smear's writes, a bath's; workers may share a
tile); `WildBrush.Random` (`ahead`, `skip`: a draw known from its place). UI (Scribulor's own): `InkWash` and
`BrushLabActivity` give their sessions a worker. `DrawingPad`, `DryingTicker`, `BrushTestPad` and `WetIndicator` are
unchanged: the ticker calls `tick` as before, now answered every 5 ms while the work runs.

#### Erasable (2026-10-16): upstream files touched

Branch `inkwash-erasable` (spec `docs/archive/superpowers/specs/2026-10-16-erasable-design.md`): a Medium dial, Erasable, the
eraser and Smudge respect. Engine (Scribulor's own): `PartDial.ERASABLE`, last of every Medium choice (`PartChoice`, with
each medium's real value, `erasable()`), `ModularCode.ADDED`, `ModularStyle` (rolls set the real value without drawing
from the Random; Oils off), `ModularBrush.switchTo` (a medium chosen starts at it), `ModularVariations` (never nudged),
`ModularWords`, `ResolvedBrush.erase`; `ModularCompose` (beside each tone, the stroke's own darkness over white, `owns`,
through the `Page` writes), `StrokeTones` (keeps them), `ModularStroke` (`begin(e)`), `DryingWater` (its ticks as
erasable as the stroke), `FloatWater` (the print's film as its own paint, the least Erasable of the strokes floating),
`CarryPlane` (a pixel's kept part stays put; the plane holds the loose part), `EraseBothStroke` (its preview at the kept
coverage), `InkWashLayers.resized`. UI (Scribulor's own): `ToolPresets` (every Basic tool at its medium's value, Sketch
pencil), `ToolSlots` (`FIRST` at real values; once, a slot holding a Basic tool as it came takes its new code),
`LegacyBrushes` (comment only: old I/P conversions and stored built-ins stay at 1). Not touched: `DrawingPad` (its
eraser preview is `EraseBothStroke.erasedTones`), `PaintActivity`, MonoPaint's tools (they write as Erasable 1).

**Changed 2026-10-22** (branch `erasable-default`; the owner: "per-tool things should be erasable unless the user
specifies"): every medium starts at 1 (`PartChoice.erasable`, `ModularStyle.erasable`; Oils too), no roll changes it
(`ModularStyle.keepErasable`: Randomize, a style, the slot's dice, the Medium's die; the dial has no die or lock in
Brush Lab, `LabSettings`; `ModularBrush.switchTo` keeps it across media), every Basic tool and `ToolSlots.FIRST` at 1,
and `ToolSlots.erasable` (flag `tool_slots_erasable_1`) sets every stored brush below 1 to 1 once, Resist's wax apart.
Scribulor's own files only.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +232 −54 | `Layer.share`: per layer, a byte a pixel, the share of its paint the eraser takes (255 all; null until the layer first holds paint that isn't wholly erasable, then made at `begin(erasable)`), carried by `copy`, copy-on-write, `expand`, `restack`, Undo (tile captures hold it, `beforeShare`; a tile captured before the layer had one restores to 255), `cancel`, `restoreSpan`, `clear` (to 255), `clearAllLayers` (dropped). The one write rule in the block write and `store`: the kept darkness is the gesture's permanent share of `owns` (each pixel's own darkness over white, or the darkening since found) over what the pixel kept as found, exact for transparent paint; a pixel left lighter by wet paint loses its permanent share of what went. `eraseTone`/`eraseTones`/`eraseFromBase` leave the kept part; `compositeToneKept`, `strokeBaseKept`. Captures are also kept by tile key (`foundTiles`), so `captureOf` needs no map or lock (a band reads only its own tiles). Pages without shares, and gestures at Erasable 1 on them, write exactly as before (the bench's 210 page hashes unchanged). Generic: any eraser-proof layer of paint (a permanent ink, a fixative). |
| `DocumentCodec` | +56 −5 | TSM5: TSM4's layers, a masking-fluid flag and the fluid, then tagged sections (tag, length, bytes; an unknown tag skipped; a zero tag ends them), all inside the checksum; the first, `ERAS`, each layer's flag and share. Written only when some layer keeps a covered pixel's paint; otherwise TSM3/TSM4 byte for byte. Older readers refuse TSM5 ("Unknown painting format") and leave the file. Generic: the sections are a place for any later per-layer data without a new magic. |
| `BookCodec` | +2 −2 | The single-page fast path and `readFirstPage` take TSM1–TSM5 (TSM4 was missing). |
| `ModularPerfBench`, `ModularPerfChecks` (androidTest) | +86 −2 | `-e erasable`, `-e marksErasable`, `-e eraser true` (the eraser over pages with and without shares, and the heap a pair's shares take). |

#### Text pass (2026-10-16): upstream files touched

Branch `inkwash-text`, `docs/TEXT-STYLE.md`. Only words: the dialogs and notes MonoPaint shows inside Scribulor say
"drawing" (not "painting") and say less. No behaviour changes.

| File | Lines | Why |
|---|---|---|
| `PaintActivity` | +15 −20 | Storage, save, recovery and rename dialogs reworded; "Saved" without its path; the untested-firmware note no longer points to Settings (Scribulor shows no Settings); the slot label after Undo is the brush's name alone. |
| `DrawingBrowser` | +7 −7 | Open and Save drawing: "Drawings", "Name", "Empty folder", "A drawing has this name." |

#### Stipple and Hatch (2026-10-16): upstream files touched

Branch `inkwash-hatch` (spec `docs/archive/superpowers/specs/2026-10-16-new-modules-design.md`, "As built"). Engine (Scribulor's
own): new `StippleStamper` and `HatchStamper` (the web lab's newmods.js stampers, stateful in `LineStamper.Meta`),
`ResolvedBrush.Pattern`, `PartChoice` STIPPLE and HATCH, `PartDial` (their dials, Build-up `ADDED`), `ModularStroke`
(their stamps, reach, every pixel never the lattice, Build-up's ticks), `QuickStroke` (their rows and preview),
`ModularStyle` (`rollPattern`, from the seed), `ModularFeatures.seedOnly`, `ModularWords`, `ToolPresets`; new
`HeldPen` (a quiet pen's last sample again). UI (Scribulor's own): `BrushTestPad` (HeldPen's tick), `LabLayout`,
`LabText`, `LoopCard`, `SwapStrip` (a Nomad swap tile's 4 dp inset). Their marks go through `ModularCompose` and
`ToneDocument`'s write path like any stroke's: Erasable, Undo and masking fluid apply.

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | +12 | `heldTick`: while the pen is down on a stroke that builds while held (`DrawingStroke.buildsWhileHeld`, Stipple's Build-up), every 30 ms of quiet the last sample again (`HeldPen`), flushed and shown as an input. Posted only for such a stroke. |
| `DrawingStroke` | +2 | `buildsWhileHeld()`, default false. |

#### Blend stump and combs (2026-10-18): no upstream files touched

Branch `inkwash-stump` (spec `docs/archive/superpowers/specs/2026-10-18-stump-design.md`). Engine (Scribulor's own): `PartDial`
(Smear's `SOFTEN`, `ADDED`), `PartChoice`, `ModularCode`, `ModularStyle` (never rolled), `ModularVariations`,
`ResolvedBrush` (`Carry.soften`, its tines from `tinesOf`, shared with Float), `Carry` (the stump pass, MonoPaint's
`ToneDabs.soften` rule ported to the plane), `ModularStroke.reachCarry` (its reach), `QuickStroke` (its cost),
`ModularWords`. UI: `ToolPresets` (Blend stump, Comb, Wash comb), `WildSample` (a clear-water comb's tile). Upstream's
`ToolStroke` and `ToneDabs` are only compiled into `scripts/test_inkwash.sh`, unchanged, so `StumpChecks` can draw
MonoPaint's own stump beside Scribulor's.

#### Blend (2026-10-16): upstream files touched

Branch `inkwash-blend` (spec `docs/archive/superpowers/specs/2026-10-16-new-modules-design.md`, "Blend in the app"): a Medium
dial, how a mark meets what is there (the medium's own way, White, Behind, Invert). Engine (Scribulor's own):
`PartDial.M_BLEND` (`ADDED`), `PartChoice` (last of every medium, `optionsOf`: Opaque's cover first), `ModularCode`,
`ResolvedBrush.BlendMode` (White and Invert: no settle, gravity or dust; on water its own way), `ModularCompose` (the
three rules from each pixel's mark over white; `Under`, the page under the stroke as its gesture found it), `StrokeTones`
(reads the coverages found too, for White's and Invert's coverage), `ModularStroke` (its `Under`, Behind's held-back
pigment and dust by how blank the page was), `DryingWater` (`lands`: where a settling may land), `QuickStroke` (the
preview over the page under it), `ModularStyle` (`rollBlend`, from the seed), `ModularWords`, `ToolPresets`,
`WildSample` (a White brush's samples on a dark ground). UI (Scribulor's own): `BrushLab` (greyed on Float), `LabLayout`
and `LabSettings` (a choice dial's options as its choice names them; the Nomad's menu button). Its marks go through
`ToneDocument`'s one write path: White and Invert as `coverTones` with no `owns` (the write rule's share for a pixel's
change), Behind with its scaled `owns`; Undo, masking fluid and the eraser apply as to any stroke.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +32 −4 | `baseTones(key, out, coverages)`: `baseTones` with the coverages found beside the tones (a quick stroke's render covers from them). `baseComposite(key, top, out)`: a tile's composite over white of every visible layer up to `top`, the gesture's layer as the gesture found it (its Undo capture), safe from another thread or a band as `baseTones` is. Generic: any tool that reads what is already on the page (a blend, a fill by colour) can read it this way. No change to the write rule. |

#### The smudge tool and the finger tool (2026-10-18): upstream files touched

Branch `inkwash-fingertool` (spec `docs/archive/superpowers/specs/2026-10-15-tools-page-design.md`, "The smudge tool and the
finger tool"): a smudge tool tile under the eraser's (its own brush, sizes only, a chooser of smudge brushes), and a
menu choice for what one finger does on the page. Scribulor's own: `ToolSlots` (`ERASER`, `SMUDGE`, `TOOLS`; the smudge
tool's brush, `smudges`, `smudgeChoices`, `useSmudge`), `InkWash` (`SMUDGE`, `stroke(document, tool)`, `finger()`),
`FingerTool` (new, pure: the choices and when a finger may draw), `DrawLayout` (eight tool tiles, their least 36 and the
faces' 35 dp; `Tap.SMUDGES`; the chooser's sizes), `InkWashBar` (the tile, the chooser), `ToolsPage` (`card(brush,
lineRoom)` shared with the chooser). Engine untouched.

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | +103 −86 | `fingerErase` becomes `fingerStroke`: one finger draws with `InkWash.finger()`'s tool, the eraser as before (sized by the fingertip), or a slot's or the smudge tool's brush at its size and grey (pressure a medium press, no tilt), lifted through `liftQuick` like the pen's. Its decisions (refused at landing, taken back, starts after 8 dp) are `FingerTool`'s; the palm thresholds moved there unchanged (90, 115 dp). A brush's stroke taken back (a second finger, the pen landing, a palm) is finished then undone (`finishedChanged`; the eraser's is still dropped). The pen's Scribulor pen-down moved into `beginInkStroke(tool, pointer, kind)`, shared by the finger. |
| `PaintActivity` | +25 −5 | The file menu's last row "Finger: …" opens the choices (`fingerMenu`), the chosen one ink-filled (`menu`'s new `chosen`). |

#### Flick and Fling (2026-10-19): no upstream files touched

Branch `inkwash-flick` (spec `docs/archive/superpowers/specs/2026-10-19-mischief-modules-design.md`, Stage 2;
`mischief-lab.html`'s section 2). Engine only (Scribulor's own, pure): `PartDial.FLICK` (last of every Tip choice) and
`FLING` (last of every medium with Blend), both `ModularCode.ADDED`; `FlickFling` (new: the pen's motion watched for a
moving lift or a hard stop; a flick's samples past the lift; a fling's seeded drops, laid as the samples' clock reaches
them); `StrokePoints` (`flickFrom`, `flickLen`, `flickWidth`, `flickDry`: what the stampers narrow and dry);
`LineStamper`, `Placement`, `BodyStamper`, `HakeRibbon` (a flick's taper, and the line's dry streaks); `ModularStroke`
(`flickFling`, `land`, `layDrop`; the flick's samples go through `add` at `lastSamples`, so every drying, quick render and
lift lays them; the drops go in the line, else the body, and again after a Slow lift's re-lay; `buildsWhileHeld`, so the
pad's `HeldPen` tells a resting pen's time); `ResolvedBrush` (`flick`, `fling`); `ModularStyle` (`rollFlick`,
`rollFling`, `flickRolls`, `flingRolls`); `ModularVariations` (an off dial stays off); `LabLayout.shows` (the Nomad's
Medium card shows Fling's row where it fits after Print Shading's); `ModularWords`. No UI or upstream file: `DrawingPad`
already ticks `HeldPen` for any stroke that `buildsWhileHeld`.

#### Oil paint: Paint Load and the Filbert (2026-10-19): no upstream files touched

Branch `inkwash-oil` (spec `docs/archive/superpowers/specs/2026-10-19-oil-paint-design.md`): upstream `fe6786d`'s oil paint mode
(in the tree, MonoPaint's own tools; its `PaintLoad`, `PaintRub`, `BrushStamp.crescent` and `ToolSettings.loadedBy` left
as they are) ported into the six-part engine. Engine (Scribulor's own, pure): `PartDial.PLOAD`, `PMIN` (Drag's and
Smear's last, `ModularCode.ADDED`), `PartChoice.FILBERT` (Tip 9, `ModularFeatures.seedOnly`), `BrushPart.keeps`,
`LoadedPaint` (new: a load's length, a stroke's `Run` spending it into `StrokePoints.own`, the rub's arithmetic),
`StrokePoints` (`own`), `ResolvedBrush` (`loaded`, `Pen.filbert`, `Body.filbert`, `Body.crescent`), the stampers
(`BodyStamper`'s filbert oval, crescent bite and spacing; each stamper's darkness times the paint left), `ModularStroke`
(the stroke's `Run`, the holder's load kept at finish), `QuickStroke` (its preview runs out too), `HeldPaint` (each
holder's load), `ModularStyle` (`rollFilbert`, `rollPaintLoad`, `oilRolls`), `ModularVariations`, `ModularWords`,
`BrushNames`, `ToolPresets` (Oil paint). UI (Scribulor's own): `SwatchFace.rubs`, `SwatchFaces.Host.rubbed`, `InkWash`
(`rub`, `paintLoad`), `SlotTile` (the gauge), `InkWashBar` (`loadChanged`, the faces' rub), `BrushLabActivity` (its
faces' rub). `DrawingPad` already calls `InkWashBar.heldChanged` after each stroke.

#### Wet smudges: Keeps Paint and Clear Water (2026-10-19): upstream files touched

Branch `inkwash-wetsmudge` (spec `docs/archive/superpowers/specs/2026-10-19-wet-smudge-design.md`): upstream `8743286`'s wet
brush pen, "Hold ink between strokes" on and off, ported into the six-part engine. Engine (Scribulor's own, pure):
`PartDial.KEEPS`, `PartChoice.CLEAR_WATER`, `ModularBrush.ownPaint`, `ResolvedBrush.Carry` (`keeps`, `water()`,
`lifts()`, `keepsPaint()`), `Carry` (held paint at first touch, `kept()`, Clear Water's wet marks), `CarryPlane`
(`water`, `wetTile`, `foundTile`, `rewet`), `HeldPaint` (new: the paint each holder's brush keeps), `ClearWater` (new:
the run-out at lift and the Float twin), `ModularStroke` (`heldBy`, `stillWet`, `runOut`), `WatercolorSession`
(`held`, `holding`, `stillWet`, `onWater`), `DryingWater` (`box`, `layer`), `ModularStyle.rollClearWater`,
`ModularWords`, `ToolPresets` (Dirty brush, Water brush). UI (Scribulor's own): `ToolSlots` (`SmudgeKind` WATER and
DIRTY, `keepsPaint`), `InkWash` (`stroke(document, tool, finger)`, `holder`, `smudgeDirty`, `cleanSmudge`),
`InkWashBar` (the smudge icon's choice and its dirty head, Clean Brush in the chooser), three drawables. The wet model
is `DryingWater`'s and `GravityFlow`'s, unchanged.

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | +4 −1 | A finger's stroke is the finger's (`InkWash.stroke(document, tool, finger)`, so its brush keeps paint of its own); after each stroke the bar's smudge icon follows whether its brush holds paint (`InkWashBar.heldChanged`). |

#### Resist (2026-10-18): upstream files touched

Branch `inkwash-resist` (spec `docs/archive/superpowers/specs/2026-10-16-new-modules-design.md`, "Resist in the app"): a Medium
choice, masking fluid or wax, laid on MonoPaint's own masking fluid (`ToneDocument`'s mask layer, its shield in every
write, `maskHatch`, Undo, `clearAllLayers` peeling it), not a parallel plane. Engine (Scribulor's own): `PartChoice.RESIST`
(Medium 5, Erasable only), `ResolvedBrush.resist` and `asInk` (laid as a crisp ink line, no smudge, never on water),
`ModularCompose` (the mark's darkness onto the mask, the more of what is there and it; wax's share only on the paper's
high points; wax's sheen, `WAX_SHEEN`, on the ink layer), `ModularStroke` (`fluid`, `beginFluid` for its gesture; wax's
sheen first, then the resist in a linked gesture, `layWax`; a render of one composes on the page at finish),
`QuickStroke` (masking fluid previews on the mask, hatched), `EraseBothStroke` (lifts fluid first: where fluid can lift,
that pass lifts it at lift, a linked mask gesture, and leaves the paint; previews again once no fluid is left),
`CarryPlane` (what the fluid shields stays put, as a kept share, for the trade and the stump alike), `WatercolorSession` (a bath prints before a resist on
Float), `ModularStyle` (`rollBlend`: Resist from the seed, Watercolor 1/60, fluid 7 in 10), `ModularFeatures.seedOnly`,
`ModularSizes.inks`, `ModularWords`, `ToolPresets` (Masking fluid, Wax resist), `WildSample` (a resist's samples washed
over), `InkWashLayers.resized` (Brush Lab's test page keeps the fluid). UI (Scribulor's own): `BrushTestPad` (draws the hatch through `DrawingPad.drawMask`). Shielding needed no new code:
settling, gravity, Float's print, Blend and a smudge's lay-down already write through the shield.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +23 −4 | `beginFluid(erasable)`: a gesture on the masking fluid whatever `paintMaskingFluid` says (a resist laid as any stroke is); the fluid may now keep a permanent share (`begin`'s `permanent` no longer forced to 0 for it; MonoPaint's own fluid tools begin at 1, so nothing changes for them), which is wax: the eraser's block write on the mask already leaves a layer's kept part. `maskHatch` hatches only the part that lifts (`liftable`: the fluid's darkness less its kept part), so wax shows only by what it keeps out. `maskShield(x, y)`, `maskLiftable(x, y)`: reads for the eraser and a smudge. Generic: a permanent resist is any tool's that wants one. |
| `DocumentCodec` | +25 −8 | TSM5's `MSHR` section: the masking fluid's shares, written only when it keeps some (wax); TSM5 is written for that too. Pages with fluid only write TSM4 as before. A TSM5 reader from before skips `MSHR` (unknown tag) and reads wax as masking fluid; older ones refuse TSM5 as before. |
| `DrawingPad` | +3 −1 | `drawMask(document, display, area)` made static, so Brush Lab's test page draws the hatch the same way; the instance method calls it. |

#### Print shading builds up; the owner's names (2026-10-19): upstream files touched

Branch `inkwash-screen2`, from the owner's first test ("screen tones always seem consistent, not lighter and darker or
cumulative like in the demo"). The flat tones: the threshold read the mark's own darkness over white, so a second stroke
laid the same dots again, never more, and a line at the default black swatch is solid (its coverage is 1). Now each
layer keeps an optional print tone (`Layer.print`): what its screened marks would show unscreened. A screened stroke
laid its medium's own way (`ResolvedBrush.prints`; not White, Invert or Tone) builds on it as transparent paint darkens
(`PrintScreen.printed`), its screen inks that, and the compose writes it back through the `Page` (`strokeBasePrints`,
`setPrints`; `StrokeTones` keeps it for a quick render). Two prints on one layer: each stroke inks only its own new dots
from the tone both built; the first's stay. Engine (Scribulor's own): `PrintScreen` (`printed`, `LABEL`, `ROW_LABEL`),
`ModularCompose`, `StrokeTones`, `ModularStroke.begin` (`printing`), `QuickStroke` (the preview builds on it, and a
transparent body previews at `SCREENED_BODY` of its glaze), `InkWashLayers.cropped`. Words and UI (Scribulor's own): the
owner's names, Print shading, engraving, Screentone, Darkness (`PartDial`, `PartChoice`, `ModularWords`: display only);
Control's darkness words for a dotted brush (`ModularWords.handWords`, `darkHead`, `HandGrid`); a choice dial's menu in
a panel beside its button (`LabSettings.Beside`, `BrushLabActivity.showMenu`, `besideColumn` shared with the swap
panel); `LabText` breaks a word at a soft hyphen (`SwapStrip`'s Screen-/tone on the Nomad). The drawing screen's side
bar files are untouched.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +157 −40 | `Layer.print`: per layer, a byte a pixel, the print tone (255 none; null until a screened gesture calls `printing()`), carried as `Layer.share` is: `copy`, copy-on-write, `expand`, Undo (`beforePrint`, `TileEdit.printBefore/After`; a tile captured before the layer had one restores to 255), `cancel`, `restoreSpan`, `clear`. `setPrints` (Undo capture, the masking fluid's shield), `strokeBasePrints`, `basePrints` (shared, as `baseTones`), `printTone`. `eraseTones`/`eraseTone` lower it with the coverage they take (a pixel with print tone and no paint is erased too). `copyShare` is `copyPlane`, `sameShare` `samePlane`. A gesture without a screen never touches it: pages and strokes without one write exactly as before. Generic: any hidden per-layer tone a tool builds on. |
| `DocumentCodec` | +42 −16 | TSM5's `PRNT` section: each layer's flag and print tone, written only when some pixel holds some (an all-255 plane, a screened stroke undone, writes nothing), so every other page is byte for byte; `ERAS` and `PRNT` read by one `perLayer`. A reader from before skips it and opens the drawing as it shows. |

#### Screen and Tone (2026-10-19): no upstream files touched

Branch `inkwash-screen` (spec `docs/archive/superpowers/specs/2026-10-19-mischief-modules-design.md`, "Stage 1"): a Medium dial,
Screen (none, halftone, line screen, dither, grain, with Cell, Angle, Misregister, Ink gain and Uneven), and a Medium
choice, Tone (a screentone: dots, lines, sand or a gradient at a Tone over the brush's fill). Engine (Scribulor's own): new
`PrintScreen` (the threshold at each page pixel, mischief-lab.html's `buildScreen` and `bit1`, the second pass's Ink
gain and Uneven worked out from the neighbours' thresholds; a Tone dot is tone 1 at coverage 254, black on any paper,
which no other brush lays); `PartDial` (`SCREEN`, `CELL`, `S_ANGLE`, `MIS`, `S_GAIN`, `UNEVEN`, `PATTERN`, `TTONE`, all
`ADDED`), `PartChoice` (Screen's six last of every medium with Blend; `TONE`, Medium 6), `ModularCode`, `ResolvedBrush`
(`screen`; Tone laid as Resist is, `asInk`; a screened mark doesn't settle, run, drip, shed dust or grow mold),
`ModularCompose` (the mark's darkness over white through the screen as each pixel is written: ink, or as the gesture
found it; Misregister composes each line and body buffer with its faint second pass at the offset, `ghost`; nothing new
runs without a screen but one null test a pixel), `QuickStroke` (the preview is the screen's own dots), `EraseBothStroke`
(Tone's dots scrape in streaks along the stroke; anything else erases as ever), `ModularStyle` (`rollTone`, `rollScreen`,
from the seed after every part), `ModularFeatures.seedOnly`, `ModularVariations` (never nudges Screen's dials or a Tone),
`ModularSizes` (Tone on the ink layer, 40 px in every style), `ModularWords`, `LiftingStroke.screened` (the bench's
fast panel path, `StrokePanel`'s "screen"). UI (Scribulor's own): `BrushLab` (Screen's own dials only with a screen, Cell
and Angle greyed where its print has none), `LabLayout` (`shows`: the Nomad's open card has no room for Screen's own
dials; `stacks`; five options are a menu button anywhere), `LabSettings`. Its marks go through `ToneDocument`'s one write
path: Undo, masking fluid, Erasable and a shaded page apply as to any stroke.

#### Halo, shown as Mold (2026-10-18): upstream files touched

Branch `inkwash-halo` (spec `docs/archive/superpowers/specs/2026-10-16-new-modules-design.md`, "Halo in the app"): a Drying
choice whose veins grow from a wet stroke to nearby marks, until another brush sets them. Engine (Scribulor's own): new
`HaloGrowth` (the lab's Halo: `Lift`, `Read`, build, step, apply); `PartChoice.HALO`, `PartDial.H_REACH` and `GROWTH`;
`ResolvedBrush` (`asSlow`, `Halo`, the owner's limits); `ModularStroke.finish` (hands the lift to the session, a sample's
grows at once); `WatercolorSession` (the halos beside the Slow wash and the bath: lifts queued, built and stepped on the
`SettleWorker`, set by anything else, joined by a Halo stroke on their layer); `ModularStyle.rollHalo`,
`ModularFeatures.seedOnly`, `ModularWords`, `ToolPresets` (Mold ink). UI untouched: the drying ticker, the wet indicator
and Brush Lab's test page already drive a session.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +16 −4 | `foundTiles()`: the gesture's Undo captures by tile key (references; never changed afterwards), so a stroke's page before it can be read after it's finished. `foundComposite(key, layer, tile, out)`: a tile's composite of every visible layer, one layer read from such a capture (or as it is); `baseComposite` now shares its loop. Generic: any tool that grows from a stroke after it (Halo) or compares a page before and after one. |

#### Fridge magnets (2026-10-18): upstream files touched

Branch `inkwash-magnets` (the owner's books-and-magnets mockup, screen 3; reference: the owner's Paintmageddon,
`magnetic-poetry.html`, `WordBin.tsx`, `WordLayer.tsx`, `renderWordsToCanvas.ts`): word tiles lying over the page,
never in a layer. Pure (Scribulor's own, `test_inkwash.sh`): new `Magnets` (the page's magnets, immutable; the tile's
geometry in page pixels: 5 mm iA Writer Duo 700 words, 7.3 mm tiles, a 2 dp border and a solid 2 dp offset shadow, a
tilt of ±2.5°, a quarter turn from the app's turn; `stamp`, the one way a picture of the page gets them; the WORD
section's bytes), `MagnetDrag` (a magnet under the pen: follow, lift, take off over the bin or off the page; rectangle
parts), `WordPacks` (the bundled kit, `assets/words/default.txt`, and `*.words.txt` packs beside the drawings, each
shuffled once a session), `WordBinLayout` (the bin's rows and pages). Android (Scribulor's own): `MagnetTiles` (a tile's
picture, antialiased and tilted once, kept), `WordBin` (the bin's view), `WordsMode` (the bin, the pen on magnets, the
drag's overlay, the canvas's hooks). `DrawLayout` counts one more icon (and rule) for the bar's Words button where the
column holds it at its least sizes with four layers (not the Nomad in landscape: there it is the menu's);
`InkWashBar` adds the button before the stretch (`words`, `wordsShown`; a tool chosen closes Words); `AutoExport` stamps
the PNG copy. Undo: a third kind of `ToneDocument` step (magnets before and after, by reference), so placing, moving
and taking off are each one step in the page's one history; its redraw is kept apart from the paint's dirty box
(`takeMagnetAreas`), so a magnet moved across the page redraws two tile-sized patches, not the page between. A drag
redraws only the tile's old and new patches: the raster holds the page without it and the tile is a `RasterOverlay`
over it, presented through the canvas's gray session like the eraser's ring; one full clean refresh (`autoRefresh`) at
lift. The canvas never presents under the bin (a non-focusable `PopupWindow` Android draws over it): `flush` and
`autoRefresh` skip what it covers. For the notebooks' PDF: give `Magnets.stamp` the page's ARGB pixels and a
`WordsMode.exportTiles()`.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +39 −1 | `magnets()`, `setMagnets` (one Undo step; no-op when equal), `placeMagnets` (from a file, no step), `takeMagnetAreas`; `Edit(Magnets, Magnets)` and its branch in `travel`; `Snapshot.magnets`; `ToneDocument(Snapshot)`. No gesture, layer or composite reads them. Generic: any per-page overlay kept by reference in the history. |
| `DocumentCodec` | +24 −6 | TSM5's `WORD` section (`Magnets.encode`: a version byte, a count, each word, centre, tilt and turn), written only when a page has magnets; TSM5 is written for that alone too. A TSM5 reader from before skips it (unknown tag) and opens the drawing without them (`MagnetOlderReaderCheck`, against `f34fed5`); a later version's section is skipped by this one; damage refused. Pages without magnets write TSM3/TSM4/TSM5 byte for byte as before. |
| `DrawingBook` | +1 −1 | `Snapshot.page` of the shown page carries its magnets (`new ToneDocument(activePage)`); other pages carry them in their bytes. |
| `DrawingPad` | +73 −26 | `words` (a `WordsMode` and its `Host`, lending the raster, `pagePoint`, `queue`, `render`, `flush`, `autoRefresh`); `render` draws the magnets last, `renderAll` all of them; `renderDirty` also renders `takeMagnetAreas` and takes the held magnet's overlay off and back like the ring's; with Words open the pen goes to `words.touch` (after `historyTap`, so two- and three-finger taps still undo and redo) and the eraser ring hides; `suspend` drops a drag; `flush` and `autoRefresh` present only outside `covered()` (the bin's panel area mapped like `refreshFastInk`). |
| `PaintActivity` | +8 −1 | `dismissPanels` and `requestQuarter` close Words; the file menu gets a Words row where the bar has no Words button. |
| `PngExport` | +6 −1 | The full-size export stamps the magnets (the dot-size export doesn't). |

Not done: `expand` (MonoPaint's canvas growth, not reachable in Scribulor) doesn't move magnets; MonoPaint's `clear` and
`clearAllLayers` leave them; page thumbnails (`LayerThumbnail`) show the paint only.

**2026-10-19, magnets that move (branch `inkwash-magnetfix`).** Only the open bin gave the pen to the magnets, and the
bin's own window took a stroke landing on a word and read only its lift: so on the tablet no magnet ever moved.
`MagnetDrag.landing` now decides who a landing belongs to (the pen on a magnet always picks it up; with the bin closed
everything else draws); `WordBin` hands a pen stroke off a tile to `WordsMode.carry`. A moving magnet stays put and
only its outline follows (`MagnetDrag.outline`, four 3 px bands), through the fast black-and-white preview where the
panel has one; at lift the area it crossed gets one clean refresh. Tiles are Paintmageddon's: Roboto Mono Bold
(`res/font/roboto_mono_bold.ttf`, `Theme.magnetFace`, the magnets' only), 2.7 mm words on 4.7 mm tiles, a fine grey
edge, a 2 px ink shadow, 2 px corners; the bin's four rows hold up to 48. Files store no size, so older pages just get
the new tiles. `DrawingPad` (+25 −8): `words.touch(event, mayPick)` on every touch (`mayPick`: no other pen, finger,
eyedropper, gradient, fill or shape gesture), the host's `fastPreview` (`beginFastPreview`/`endFastPreview`) and
`refresh(reason, area)` (`refreshArea`, a forced refresh of that area alone outside `covered()`), and `autoRefresh`
waits while a magnet moves; the host's `redraw` is gone.

**2026-10-19, speed and the stump (branch `inkwash-speed2`).** A Blend stump on the ink also blends the pair's wash
(the stump spec, "Own layer only"); paints start the lattice at 50 px (`ModularStroke.latticeFrom`); quick strokes
write their true tones behind the pen while drawing (`QuickStroke.trail`, `StrokeTones.writeTiles`: engine-only).

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +20 | `layerPixels(index, …)`, `layerKept(index, …)`: `strokeBasePixels` and `strokeBaseKept` of a layer no gesture is changing, as it is (a stump's other half). Reads only; no change to the write rule. Generic: any tool that reads a second layer while drawing on one. |

### (c) Scribulor engine: pure Java, UI-free

Checked on the desktop with plain `javac` (`scripts/test_inkwash.sh`), which
also proves they need no Android.

| Group | Classes |
|---|---|
| Six-part brush model | `BrushPart PartDial PartChoice ModularBrush ModularStyle ModularLocks ModularFeatures ModularVariations ModularWords ModularSizes` |
| Share codes | `ModularCode` (M codes); `BrushCode`, `WildBrush`, `BrushSetting`, `BrushStyle` read older I/P codes, only to convert them |
| Resolve and stroke | `ResolvedBrush ModularStroke StrokePoints StrokeMath StrokePaper`; `QuickStroke` (a stroke too heavy to draw live: a flat preview, then the true stroke rendered from its samples); `FlickFling` (Flick's samples past a moving lift, Fling's drops on a hard stop, both in the stroke) |
| Smudge | `Carry` (a dab-sized reservoir trading paint with the stroke's own layer: Lift, Drag, Smear, Clear Water; Smear's Soften, MonoPaint's blending stump and its combs), `CarryPlane` (that layer as the stroke found it, premultiplied, as smudged so far), `HeldPaint` (the paint a brush with Keeps Paint holds between strokes, and a Paint Load brush's load, per holder), `ClearWater` (Clear Water's run-out at lift, a wash's wet again with Slow drying; its Float twin), `LoadedPaint` (Paint Load: MonoPaint's oil paint mode, a brush's own paint running out along the stroke; a rub's load) |
| Stampers and buffers | `FloatTiles LineStamper Placement BodyStamper HakeRibbon BristleSwarm StippleStamper HatchStamper StickGrain PaperGrain PaperNoise` (`FloatTiles` also holds a big stroke's half-resolution lattice and its bilinear stretch; `BristleSwarm` is the bristle tip's hairs, the web lab's swarm; `StippleStamper` and `HatchStamper` the Stipple and Hatch tips' dots and lines, newmods.js's) |
| Compose and drying | `ModularCompose PrintScreen BodySettle DryingWater WatercolorSession SettlePacer SettleWorker HaloGrowth` (`PrintScreen` is Screen's and Tone's threshold at each page pixel, which the compose writes through; `HaloGrowth` is Halo's veins, shown as Mold, the web lab's newmods.js Halo on 32 px tiles, read from the page apart from its build; `ModularCompose` also holds Blend's rules and `Under`, the page under a stroke as found; `SettlePacer` paces settling after lift and Fast-wet's re-settles, section 5; `SettleWorker` is the thread wet paint works on after lift) |
| Float | `FloatBath` (suminagashi: floating paint as outlines moved by drops, the pen's pushes and combs, and drift; its log, snapshots for Undo), `FloatScan` (its film at 16 greys), `FloatTrace` (a stroke's own paint traced into rings), `FloatPen` (a stroke on the water), `FloatWater` (the bath on its layer until it prints) |
| Gravity | `GravityFlow` (a wet wash moving with the page's tilt: runs, pools, drips, sinks, blends; the Gravity Lab's gravity.js), `PageTilt` (the page's downhill vector and dead zone, from MonoPaint's `CanvasGravity`) |
| Cores | `Cores`: three helper threads beside the caller's (and three quiet ones at the lowest priority for a background render, `quiet`); compose, stampers, settles, blurs, the lattice stretch and the render share bands of tile rows that share no tile, so the page is the same on one core |
| Timing | `Prof`: named timing sections, off unless the bench turns them on |
| Pen input | `PenBatch`: a stroke's samples gathered from input events, drawn together (Android glue in `DrawingPad`); `HeldPen`: a resting pen's last sample again, for a stroke that builds while held |
| Old brushes, converted | `LegacyBrushes`: I/P Wild brushes and the four old built-ins as six-part brushes, and the size and grey a converted slot keeps. The old engines (`InkStroke`, `WildInkStroke`, `WashStroke`, `WashLook`, `WatercolorSettle`) were removed on 2026-10-15: one engine draws every brush. |
| The eraser | `EraseBothStroke WashCoverage RoundFootprint PenSpeed` (ink and wash of the pair together, one undo step; it leaves what each layer keeps, Erasable; it lifts masking fluid first, Resist) |
| Brush values and storage | `Brush BrushTexture BrushNames BrushCaptions BrushLibrary` |
| Samples (preview images) | `WildSample` (`modular(...)` returns pixels for any size/shape) |
| Scribulor-specific but pure | `InkWashLayers` (ink over wash layer pairs), `InkWashSizes`, `InkWashShades`, `Assets`, `DrawingNames`, `LayerThumbnail` |
| Fridge magnets | `Magnets` (a page's word tiles, their geometry, `stamp`, the WORD section), `MagnetDrag`, `WordPacks`, `WordBinLayout` |
| Fill | `FillRegion` (the area a tap is in, shut in by the ink layer's crisp lines: the Mischief Lab 2 prototype's "Stay in the lines", in 2 px cells, then drawn at page resolution), `FillTool` (the fill's brush, its walls and its stroke, `ModularStroke.fill`; the bar's taps while it is on), `FilledShape` (Filled Shape: an outline closed at the lift and the area it encloses, non-zero winding, filled through `FillTool.start`) |
| Pictures in | `PictureImport` (Import Image and Import PDF, 2026-10-22: a picture's greys, laid as ink in one Undo step; the placing frame; a PDF's notebook of locked pages under clear layers, its cap and its file) |
| Mirrors | `Mirrors` (a page's mirrors: the group they make, the snapping, the cap, Show Guides, Real Mirror's `View`, the MIRR section), `MirrorStroke` (a stroke and its copies, laid as its change reflected or drawn again, one Undo step; Real Mirror's stroke folded into the kept side), `MirrorShow` (what the canvas shows: guides, the reflection, live copies, a placing stroke) |

Reference implementation: the web prototype
`docs/brush-lab/prototypes/modular-lab.html` (sources in `modular-lab-src/`);
the Java port matches it bit for bit except where the engine spec's "As built"
lists a reason.

### (d) Scribulor UI/product layer (Scribulor-only)

- Toolbar: `InkWashBar` (the one side column), `InkWash` (selection, starts
  every stroke), `ToolSlots`, `ToolSwatches`, `SwatchFace(s)`, `SizeModal`,
  `SwapStrip`; `SwatchBar` (the selected tool's sizes and greys, in the
  column) and `DrawLayout` (pure: the column's order, `COLUMN`, which
  `InkWashBar` builds from, its fitted heights, folding the layers, the tool
  buttons two by two with Fill's cell measured before Fill comes
  (`InkWashBar.fillButton` puts its button there), what a tap on a tool does,
  the smudge chooser's sizes); `FingerTool` (pure: what one finger does, when
  it may draw). Four sizes and four greys since 2026-10-19: `ToolSwatches`
  keeps the older three where an older build reads them and the fourth under
  its own key; `InkWashSizes.Preset` and `InkWashShades.Shade` gained `LARGER`
  and `MID` in their places (saved by name). `WildSample.legibleTray` draws a
  thin, sparse tray stroke darker on the bar only.
- Tools page (tapping the selected slot again): `ToolsActivity`, `ToolsPage`
  (pure: sections, paging, picks, Undo, the look a tool is tried at),
  `ToolsLayout` (pure: every size), `ToolPresets` (pure: 65 presets as M
  codes, five per style).
- Brush Lab: `BrushLabActivity`, `BrushLab` (pure model), `LabSettings`,
  `LabLibrary`, `LabLayout`, `LabText`, `LabPages`, `LabUi`, `LabButton`,
  `PartCard`, `HandGrid`, `BandSlider`, `SquareSwitch`, `LoopCard`,
  `BrushTestPad`, `SampleQueue`; `TestArea` (the test page in its frame, Clear,
  the wet drop and the brush's loop beside its name: Brush Lab's and the Tools page's).
- Android glue: `StrokePanel` (a pen stroke's session on the panel's fastest black/white update, `Device.fastStrokeMode`; the bench only since 2026-10-16),
  `QuickLift` (after a quick stroke's lift, looks in on its render thread every 5 ms, then finishes
  it), `DryingTicker`, `PenSample`, `RasterOverlay`, `DisplayRecovery` (pure: the canvas's fast display through a busy
  or failing panel, reconnecting by itself), `RecoverySchedule` (pure: when the recovery save runs while drawing), `AutoExport`,
  `StrokeTiming` (debug log), `TiltFeed` (a session's page tilt from MonoPaint's
  `CanvasGravity`, listening only while something wet could move).
- The Mirrors button (the Fun button until 2026-10-23): `MirrorsMenu` (once `FunMenu`, the Mirrors list, `DrawLayout.MirrorRow`;
  what the pen's next stroke places, `DrawingPad.placing`).
- Fridge magnets: `WordsMode` (the Words bin and the pen on magnets, the drag's overlay; hooks in `DrawingPad`),
  `WordBin` (the bin's view: WORDS, the pack and page, More words, Done, a page of tiles), `MagnetTiles` (tile pictures).
- Pictures in (2026-10-22): `PicturePlacer` (Import Image's placing over the page: the outline and handle while it
  moves, the picture in dots at lift, Cancel and Place along the page's foot; hooks in `DrawingPad` beside the
  magnets', the same `WordsMode.Host`), `PdfImport` (Import PDF: `PdfRenderer` a page at a time into a cache file, then
  `DocumentStore.create` writes the notebook whole; `PdfImport.page`, one page as a picture, for This Page and the
  previews), `ImportPicker` (2026-10-26: the imports' own file list, with Cancel, in place of Android's picker, which
  has none; 2026-10-29: Import's one picker, pictures and PDFs a folder at a time with crumbs, as wide as the screen,
  then a PDF's New Notebook or This Page).
- `WetIndicator`: the droplet at the foot of the toolbar (and beside Brush Lab's
  Clear) while a Slow wash is still settling or a quick stroke still renders; a tap lays it now.
- Styling: `Theme` (the owner's Scribbler Style, `docs/DESIGN.md`: six greys,
  strokes, the typeface `face`, `restyle`/`dialog` for MonoPaint dialogs Scribulor
  shows), `TypeStyle` (the type scale, pure), `SlotTile` (the slot tile Brush Lab
  draws, and the bar's tray rows); Phosphor icons (`res/drawable/ic_ph_*`, `ic_part_*`; from 2026-10-26
  the owner's own drawings traced with potrace; since 2026-10-29 Iconoir's, MIT, or drawn for Scribulor in its style,
  same names, each file naming its source in a comment, `THIRD_PARTY_NOTICES.md`),
  launcher icon, iA Writer Duo in `res/font/` (SIL OFL 1.1, the license in
  `assets/fonts/OFL.txt`). The font is also the app theme's (`res/values/styles.xml`,
  `android:fontFamily`), which is how MonoPaint's and the system's menus, dialogs
  and popups get it. Upstream files touched for it (2026-10-16): `PaintActivity`
  (the page number, dialog buttons and `message`, a toast with its own view since
  the system draws plain text toasts in its own font), `LayersPanel`,
  `SettingsForm`, `FullscreenController`, `Footprint`, `PageOverview` and
  `ToolButton`: each `setTypeface(null, BOLD)` or default-face `Paint` now takes
  `Theme.face(400 or 700)`.

### (e) Tooling

- `scripts/test_inkwash.sh`: desktop checks for the engine and Scribulor
  classes, plus `compose_check.c` comparing native and Java rendering.
  `scripts/test_document.sh` (upstream's) runs them too. `scripts/carry_goldens.cjs` draws Smudge's golden pages
  with the web lab's engine in Node (`app/src/test/resources/carry`, read by `CarrySmudgeChecks`);
  `scripts/smudge_pictures.sh` draws Smudge with the app's engine as the Manta shows it, and each golden beside the lab's.
- New `*Checks.java` in `app/src/test`, including golden checks
  (`LegacyConversionChecks`: old codes and built-ins convert to fixed M codes and pages; an eraser golden in
  `EraseBothChecks`), `LatticeChecks` (a big stroke's half-resolution lattice against every pixel) and
  `ModularBenchChecks` (desktop timing budgets at about twice this Mac's slowest style, so a change that doubles a
  style's cost fails here before it lags the pen on the Manta). `ErasableChecks` (the pencil-ink-erase workflow pixel for
  pixel, part erasable, Smudge, wet paint, quick, Undo, codes and rolls, slots) and, in `scripts/test_document.sh`,
  `ErasableFileChecks` (TSM3/TSM4 byte for byte, TSM5 round trips, sections, damage, books, recovery, Undo) and
  `OlderReaderCheck` (the document classes from before Erasable, from git, refuse a TSM5 drawing). `LegacyGoldenChecks` and `WashGoldenChecks` pinned
  the old engines and went with them. The two goldens were re-recorded once for the speed work (paints' bodies
  changed on purpose); the eraser over the old page still gives its old sums.
- The timing bench: `ModularPerfBench` (androidTest; a 1.5 s, 480 Hz stroke on a full Manta page replayed in real
  time with Android's batching; `hashes` mode lists each case's page CRC, so a speed-up can show it changed no
  pixel), run on the device by `scripts/bench_device.sh` (the `bench` build, `ing.ell.mybaddrawings.bench`, no
  launcher icon, uninstalled after each run) and on the desktop by `scripts/bench_desktop.sh`. It reports each
  sample's wait from the pen to its render, paces settling as the app does, and feeds input as the Manta sends it
  (`SYNC`), batched (`BATCH`) or at 60 Hz (`VSYNC`). `LiveStrokeBench` (`LIVE=1 scripts/bench_device.sh`) sends
  real pen events into the bench build's own drawing screen and prints `DrawingPad`'s stroke timing lines.
- Pictures at both tablets' sizes: `ScreenShots` (androidTest), run by `scripts/screens_device.sh` in the bench build.
  On the Manta it shows the Nomad's layout at its physical size through `PanelPreviewLayout` (MonoPaint's own
  smaller-panel simulation, put round Brush Lab's and the Tools page's content; the drawing screen uses its own),
  never the tablet's display settings; the drawing screen is drawn from its views (its page goes straight to the
  panel, which screenshots miss).

## 3. Proposed engine-module boundary

### What a host (MonoPaint) would call

```java
// Brushes travel as strings.
ModularBrush brush = ModularCode.decode("brave-onion.M24p6...0nz0.1u5hvr4"); // null if invalid
String code = ModularCode.encode(brush);

// A stroke is MonoPaint's own DrawingStroke interface: live, or quick when live would lag the pen.
DrawingStroke s = Engine.start(surface, layer, brush, sizePx, darkness /*0-1*/, wetSession);
s.sample(x, y, pressure, tiltX, tiltY, timeMs);   // per pen event
s.finish();                                         // one undo step
// A quick stroke (instanceof QuickStroke) renders its true stroke on a thread of its own: lift() at pen-up, then
// develop(0) now and then until false (it composes the page then); finish() waits and does it at once.

// While a slow-drying wash settles after lift (~10 Hz, paced), and before anything else edits the page:
long nextMs = wetSession.tick(surface, show);  wetSession.settleNow(surface);
wetSession.input(handleNanos, eventAgeMs);   // per pen event: lagging input backs settling off
boolean wet = wetSession.wet();              // for a "still wet" indicator; wetSession.wetChanged tells when
wetSession.tilt = () -> PageTilt.pull(gx, gy);  // gravity: the page's downhill (CanvasGravity.read() on Android)
boolean listen = wetSession.wantsTilt();     // run the sensor only then; wetSession.tiltChanged tells when

// Preview images for menus and pickers (any thread, own scratch page).
int[] pixels = Engine.sample(brush, darkness, Shape.LOOP, sizePx, width, height);
```

Today these are `QuickStroke.start(ToneDocument, ModularBrush, double size,
double darkness, WatercolorSession)` (live strokes are `ModularStroke`s), `WildSample.modular(...)` and
`WatercolorSession`; `Engine` is a facade still to write.

### What the engine needs from the host

- **A tile surface like `ToneDocument`:** read tones and the tones under the
  stroke (`tone`, `strokeBaseTones`, `layerDarkness`), write blocks
  (`setTones`, `coverTones`), pick a layer, and undo hooks (`begin`,
  `finish`, `finishWithPrevious`, `cancel`, `restoreSpan`, `revision`).
  About 20 methods; could become a `ToneSurface` interface that
  `ToneDocument` implements.
- **Paper textures:** thirteen 64 KB grain files (`assets/textures/*.bin`: five ambientCG scans, eight Unsplash photos) via
  `Assets`/`PaperGrain`; noise otherwise procedural.
- **A settle ticker:** something calling `tick` ten times a second while a
  Drying wash settles, waiting as long as it answers (`DryingTicker` is ~60
  lines of Android `Handler`), and the pen events' timing for the pace
  (`SettlePacer`, optional: without one nothing is paced). Given a
  `SettleWorker`, the session works its ticks out on that thread and `tick`
  answers 5 ms while one runs; without one everything runs on the caller's.
- **A quick stroke's lift:** something calling `QuickStroke.develop(0)` every few ms after lift, keeping the stroke as
  "still finishing" until it answers false (`QuickLift`, ~60 lines of Android `Handler`); without one, `finish` waits
  for the render at lift. The engine starts one thread per quick stroke.
- **A clock** (`LongSupplier`) for pen speed and wet windows.
- **Threading:** strokes run on the page's owner thread, sharing big pieces
  of work with `Cores`' helpers while it waits (the surface must allow
  writers in separate tile rows at once: `ToneDocument.openBands`); samples
  draw on their own scratch page, so they can run on a background thread.
  Wet paint's work after lift reads the page on the session's `SettleWorker`
  while the owner thread doesn't write it (the session sees to that: anything
  that writes first takes the work in or lets it go), and only the owner
  thread writes what it laid.
- **Batched input:** a host whose pen events carry one sample each should
  gather them and feed the stroke what has gathered (`PenBatch`), or a big
  brush falls behind.

### What would need adapting to MonoPaint

| Engine today | MonoPaint's equivalent | Adaptation |
|---|---|---|
| `ModularStroke.start` picks the layer via `InkWashLayers` (ink over wash pairs) | Free layers (`LayersPanel`, `MAX_LAYERS`) | Host passes the target layer; move the pair rule into Scribulor. |
| `darkness` 0–1, `size` px | `int gray` 0–255 (`GrayPalette`), `ToolSettings` min/max diameter + pressure response | Map gray → darkness; size from the tool's diameter. The brush's Control part handles pressure itself. |
| `WatercolorSession` / `DryingWater`: paint settles in place; with gravity dials a `GravityFlow` moves it with the tilt | `WetWatercolor`: water that runs with tilt (`CanvasGravity`), `BlowDryer` | Keep both; engine strokes ignore MonoPaint's wet layer at first. Both now read the tilt through `CanvasGravity`. Deciding how they meet is a conversation, not a port. |
| Brushes stored as `name<TAB>code` (`BrushLibrary.Store`) | `ToolLibrary` versioned binary, `ToolSettings.Tool` enum (append-only) | One new tool kind that holds an M code string. |
| Writes via new `ToneDocument` methods | `ToneDocument` without them | Upstream those methods first (or the `ToneSurface` interface). |
| Everything package-private in one package | Same package name | Easiest path is same-package files; a Gradle `java-library` module would need a public facade. |
| Masking fluid respected; Resist lays it (masking fluid, and wax as its permanent share) | Mask layer (`hasMask`), its own tool and Peel | Resist reuses the layer; MonoPaint's tool could lay wax through `beginFluid(erasable)`. |

### Share codes as a portable format

M codes (`name.M<style><six parts>.<seed>`, spec in
`docs/archive/superpowers/specs/2026-10-14-modular-engine-design.md`, "Share codes")
are plain ASCII, append-only (new dials go on the end), case-insensitive, and
decode identically in the web prototype and the Java engine. They are the
natural exchange format: a brush made in Scribulor's Brush Lab or the web lab
could be pasted into MonoPaint and draw the same.

## 4. Upstreamable now

Generic wins that help MonoPaint without the rest of Scribulor. Each would need
re-cutting as a small commit on top of `upstream/main` (some are tangled with
Scribulor code today).

| Priority | Commits | Pitch |
|---|---|---|
| High | `84a568e` | Render changed areas with the existing native compositor: a big settle went from 640 ms to a few ms on the Manta; desktop check proves pixel-identical output (`compose.h`, `compose_check.c`). |
| High | `1a0ef71` | Bug fix: with a palm already resting, the pen landing (`ACTION_POINTER_DOWN`) now closes an open panel (`Ui.outside` per pointer). |
| Medium | `29abb08`, `389897c`, `a9c7247` | Automatic full e-ink refresh at natural pauses (pen out of range 2 s, menu closed, big undo), never under the pen. Clears ghosting without a button. Hooks into `InkWashBar` need replacing. |
| Medium | `cefc8af`, `e4753c9`, `5daf4ba` | `ToneDocument`: linked undo steps (one Undo for a gesture spanning layers) and one place for tile undo capture. |
| Medium | `bcee59b` (interface part) | `DrawingStroke.sample(..., time)` default method: strokes can follow pen speed; existing strokes unchanged. |
| Medium | `0379ce9`, `d0b66aa`, `2066ef3` | Rename and delete drawings safely with `AtomicFile` backups, case-only renames, taken names. Only if MonoPaint wants in-app rename. |
| Low | `7307362` (`BrushStamp` part) | Per-thread scratch paths, if MonoPaint ever draws previews off the main thread. |
| Low | `03dbb85` | Thin outline of the eraser's size while erasing (`RasterOverlay`). |
| Low | `inkwash-erasable` (`DocumentCodec` part) | Tagged sections after a page's body (TSM5): later per-layer data without a new magic; an unknown section skipped. And `ToneDocument`'s captures by tile key, so a block write looks a capture up without a map or lock. |
| Medium | `bc7b014` (interface and `DrawingPad` part) | `DrawingStroke.present()`: a stroke stamps each sample but shows the page once an input event (a 480 Hz pen batches up to eight samples per 60 Hz event, more when the screen falls behind). Default does nothing, so existing strokes are unchanged; any MonoPaint stroke that composes per sample could use it. |
| Low | `2874178`, `cccaafb` | Device benchmark harness: a `bench` build type (release speed, own id, profileable, no launcher icon) and `bench_device.sh` timing strokes on the tablet, uninstalling itself after; MonoPaint has `BrushPerformanceChecks` to pair with. `ModularPerfBench` itself drives Scribulor's engine; the build type, manifest and script are generic. |

| Medium | `inkwash-zoom` (`PinchPan`, `DrawingPad.navigationGesture`) | With zoom unlocked, two fingers stay a two-finger tap until one moves past the tap's own slop, and only then pinch and pan, from where they landed: an Undo tap no longer nudges the view (a 103% zoom), a pinch never also undoes. Pure, with desktop checks. |
| Low | `inkwash-zoom` (`DrawingStroke.zoom`) | A stroke that reads the pen's speed reads it on the glass when zoomed, so a speed-sensitive tool behaves at 800% as at 100%. |

Not candidates: anything that assumes ink/wash layer pairs, the Scribulor bar,
slots or Brush Lab.

## 5. MonoPaint v0.98.3 watercolor (our fork point) and later upstream

Scribulor forked at `5212e95` "Release MonoPaint 0.98.3" (the merge base with
`upstream/main`), so all of MonoPaint's watercolor is already in Scribulor's tree,
untouched and unreachable from Scribulor's UI (its color bar and toolbar are
`GONE`). It arrived in 0.98; 0.98.3 only made the wet brush pen erase with the
Eraser color. Notes from reading it, and how each part relates to Scribulor's own
wash (`DryingWater`, which settles in place and never flows: `PHILOSOPHY.md` #4).

### Their flowing watercolor (`WetWatercolor`)

| MonoPaint | What it does | Scribulor |
|---|---|---|
| Tiles | A transient wet layer over the dry drawing in 16 × 16 tiles made only where paint lands; per pixel pigment, water freshness (`pull`, mobility = freshness³), reserve, stuck and soaked paint. | Own model: 2 × 2 cells kept only where the wash reached (`DryingWater`), one patch at a time. |
| Flow and diffusion | Trades along 4 rays a pixel (1, 4, 8 px), stopping at dry pixels, at the less fresh pixel's mobility, so ink is conserved; tiles joined by water trade their means 8 quick steps a frame (`planSpread`); freshness decays .985 a frame. | Pigment drifts toward the paper's valleys, the wash's middle and a slow curl, and is laid in place; nothing runs. |
| Gravity | `CanvasGravity` reads the accelerometer; pixels draw more from uphill, tile pairs pool downhill, wet reach grows downhill to 32 px. App setting **"Watercolor uses gravity"**. | **In the app** (2026-10-15, owner's decision after the Gravity Lab prototype): the tilt from MonoPaint's own `CanvasGravity` (credited, reused; its dead zone now `PageTilt.FLAT`), only while something wet could move; its own model, `GravityFlow` (a Slow wash's runs, pooled edge, drips, sinking grain, rocking; Quick's window; charcoal's dust), as dials per brush rather than a setting. Only wet paint moves, never once dry (`PHILOSOPHY.md` #4). |
| Wet brush pen | Lifts dry paint into a hidden reserve (2× the shade) that dissolves while the water is fresh; trades ink with the paper (`BrushInk.trade`) and carries it between strokes. | **Smudge** (2026-10-16): Lift, Drag, Smear trade paint with the page per dab-sized reservoir cell (`Carry`, from the web lab's Carry), wet or dry, on the stroke's own layer; every stroke starts with a clean brush unless Drag's or Smear's **Keeps Paint** is on (2026-10-19: Hold ink between strokes, the Dirty brush, `HeldPaint`), which the side bar shows and Clean Brush empties. Its Dry mode (clear water, no ink of its own, dragging what it passes by `pull`) is **Float's Clear water** (2026-10-16): Smudge's Own paint 0 on floating paint, Pick up as the pull with the same pressure curve; it moves outlines rather than trading pigment, so rings stay crisp. |
| Wet canvas | `WetnessBar`: above 0, existing paint near a stroke is rehydrated and bleeds. | Not planned: no modes. |
| Preview, then flow | Under the pen a flat tint glaze (`queueWater`), the dabs queued; the stroke holds its wet paint still (`holdsWetPaint`) and it starts to move once you lift. | Same shape: Slow (Drying) shows a preview under the pen and settles after lift; Fast-wet develops a window behind the nib. |
| Threads | Up to 3 "Wet paint" helper threads; each tile writes only itself, so the result doesn't depend on the split. | `Cores`: 3 helpers the same way, for compose, stampers, settles, blurs and the render (round 2). |
| Work budget | `WetWorkBudget`: 0.5–2 ms a slice while drawing, 5 ms idle; after a slow pen event or slice it backs off **100 ms**; the pen always wins (no wet work while pen pixels wait). | **Borrowed** (2026-10-15): `SettlePacer`, below. |
| Screen updates | Moving water shown at most every **120 ms** (`WET_SHOW_MS`); a slice keeps to a 96–192 px span so a far tile doesn't stretch the dirty rectangle. | **Borrowed**: settling shows at most every 120 ms, its areas gathered into one. |
| Undo | Each animation slice is a continuation of the stroke's gesture: motion is part of the stroke's undo step. | Same: each tick `finishContinuation`, one undo with the stroke. |
| Storage | Wet state is never saved; only the resulting tones. | Same. |
| Masking fluid | A page-wide mask layer (TSM4), hatched; shields every layer from paint, erasing and running water; Peel removes it. | Engine strokes respect it (compose); MonoPaint's UI to paint it is hidden; **Resist** (2026-10-18), a Medium choice, lays it as a brush: masking fluid the eraser lifts first, or wax, its permanent share, never hatched. No Peel button: Clear page peels it all. |

### The blow dryer

Tapping MonoPaint's rippling droplet runs `blowDry()`: `finishStroke`, then
`dryWet()`, which lays any queued dabs in full (the real result, not the glaze),
flushes the last frame and **freezes**: the wet state is dropped and the page
keeps its tones as they are. `BlowDryer` is only the animation: a hair dryer
and air lines in pure black lines (6 frames × 140 ms) so the button stays on
the panel's fast black-and-white refresh; a tap up to 1 s after the ripple
stops still counts.

Scribulor's answer is the **wet indicator** (`WetIndicator`). Until
2026-10-29 its tap was `settleNow`, a freeze of its own: everything still
suspended laid where it was. Since then (the owner: "looks like the hair dryer
just stops the image as it is instead of accellerate the drying processing")
it fast-forwards (`DryingTicker.hurry`, `WatercolorSession.hurry`): each wet
thing runs its remaining ticks back to back, unpaced, on the page's tilt as it
is, and the page shows once at the end, as left alone to dry, one undo step
with its stroke; MonoPaint's blow dryer blows round until it is done. Laying
where it is stays for a stroke, a pause, another page, and for what is still
wet after `WatercolorSession.HURRY_MS` (4 s). It is a droplet, dashed while
dry, in the side column and beside Brush Lab's Clear; it changes by a partial
update of its own square, never under the pen.

### Pacing borrowed (`SettlePacer`)

- **Back-off:** a pen event handled ≥ 24 ms late or taking ≥ 8 ms, a Drying
  tick with its show over 40 ms, or a Fast-wet re-settle over 8 ms backs
  settling off for 100 ms. Ticks wait it out (`WatercolorSession.tick` answers
  how long); a Fast-wet re-settle is still worked out when its rule says
  (its timing is the look) but its box is shown once the pace allows, and at
  lift at the latest; the pen's own dabs never wait. While the pen is down
  nothing ticks (as before).
- **Showing:** settling is shown at most every 120 ms; meanwhile the page's
  dirty area (ticks) or the stroke's pending box (Fast-wet) gathers. The last
  tick always shows.
- **Same result:** a tick steps by its patch's age, not the clock, so a paced
  Drying wash ends on the same layers, bit for bit; a Fast-wet stroke ends on
  the same page tones, and a layer's coverage can end lower at a few pixels (a
  layer keeps the most coverage any compose gave a pixel; the same caveat as
  composing once an input event). `SettlePacingChecks` checks both (10 Fast-wet
  strokes: under 600 pixels of 1.7 million with lower coverage, none higher).
- No pace (the desktop checks, samples, `ModularBenchChecks`): unchanged.

### Upstream since the fork: `8743286` (2026-10-14, unreleased)

"Bring back the clear-water wet brush pen and add opaque wet paint": a brush
pen setting **"Hold ink between strokes"** (off = clear water that only moves
what is there); **opaque wet paint** with a **Coverage** setting (50%); both
brush pens size with pressure; Strength 60%; a reworked tile-scale spread; the
preset format adds Coverage.

- MonoPaint's opaque wet pen is the reference for Scribulor's **Smudge** part
  (Lift / Drag / Smear: `PartChoice.LIFT`, `DRAG`, `SMUDGE`; built 2026-10-16) with
  Medium = Cover: opaque Lift's paint takes on the dark it covers (its `MIX`), as
  `BrushInk` does; the clear-water pen (Hold ink off) is Smear with Own paint 0, the
  Stump. Taken from them: a reservoir that trades with the page per dab, conserving
  what it moves (`BrushInk.trade`), and the work shared over cores in bands that
  never share a cell. Spec `docs/archive/superpowers/specs/2026-10-16-smudge-design.md`. Holding ink between strokes came
  later (2026-10-19) as Drag's and Smear's Keeps Paint (the Dirty brush), and Hold ink off as Smudge's Clear Water
  (the Water brush: lifts, and lets the lifted paint run out through its wet, a wash's again with Slow drying); spec
  `docs/archive/superpowers/specs/2026-10-19-wet-smudge-design.md`.
- Upstream `fe6786d` (oil paint brushes that run out of paint and smudge; in the tree, as MonoPaint's own tools) is the
  reference for **Paint Load** (2026-10-19): Drag's and Smear's Paint Load and Starting Load (MonoPaint's Minimum load),
  the paint left falling as `PaintLoad.travel`'s smoothstep, a wider dab spending faster; a rub on a grey face loading as
  `ToolSettings.loadedBy` (the travel, twice it here); the Filbert's crescent as `BrushStamp.crescent`, the bite set
  along the way the dab moves rather than its short axis. Spec `docs/archive/superpowers/specs/2026-10-19-oil-paint-design.md`.
- **Not merged.** A trial merge conflicts only in `CHANGELOG.md`
  (`DrawingPad`, `PaintActivity`, `PressureStroke`, `ToolSettings` merge
  cleanly); its code only matters if Scribulor shows MonoPaint's tools again.
  The owner decides whether to merge (conflict log, section 6).

## 6. Habits that keep integration possible

- **Keep the engine pure and separable.** No `android.*` in category (c);
  reach the page only through `ToneDocument` methods; keep new engine classes
  in `scripts/test_inkwash.sh` so `javac` proves it.
- **Keep Scribulor UI in its own layer.** Styling, the bar, slots and Brush Lab
  live in Scribulor classes. Edit an upstream file only when there is no other
  seam, and keep the edit small (a hook, not a rewrite).
- **Split generic wins into small, focused commits** with clear messages that
  say what and why, separate from Scribulor features, so they can be
  cherry-picked onto `upstream/main`.
- **Keep `upstream` fetched.** `git fetch upstream` now and then; check
  `git merge-tree --write-tree --name-only HEAD upstream/main` for conflicts
  and note them below. Never push to upstream (push is disabled).
- **Package name stays `io.github.mpdairy.monopaint` for now.** Pro: diffs and
  merges stay small; files move between the projects unchanged; everything
  package-private keeps working. Con: the package names mpdairy, which could
  confuse readers; renaming later touches every file. Revisit if the fork
  becomes permanently separate or is published.
- **Record decisions here** (and specs in `docs/superpowers/specs/`).

### Upstream conflict log

| Date | Upstream | Result |
|---|---|---|
| 2026-10-15 | `8743286` (wet brush pen, opaque wet paint) | Merged into inkwash-v2-lab. Conflict only in `CHANGELOG.md` (kept Scribulor's Unreleased; MonoPaint's own notes stay in its repo); `DrawingPad`, `PaintActivity`, `PressureStroke`, `ToolSettings` auto-merged. Checks 50/85 PASS. |
| 2026-10-23 | `cc40de2` (reconnect the fast display) | Merged commit by commit on `upstream-lasso` (one merge each, so the history says which was taken how). Taken: `SelectionFeedback`'s kept control session, closed at focus loss. Kept Scribulor's `DrawingPad` reconnect (`DisplayRecovery`), which already does upstream's retries. |
| 2026-10-23 | `ce7cde8` (fast display into the `fasteink` submodule) | Recorded only (`merge -s ours`): Scribulor's `DirectEink` does more than `fasteink` 7daef8f (the mode 5 stroke plane `StrokePanel`, `endFast` through `panel_end.h`, ENOMEM as busy, present timing), so no submodule and no `FastEink`/`Session`. Later upstream code written against `FastEink` needs porting back to `DirectEink`. Moving Scribulor's additions into fasteink is the way to converge. |
| 2026-10-23 | `ff743a2` (a finger closes the menus a finger opened) | Taken, `Ui.closesPanel` with Scribulor's palm rule: a contact wider than `FingerTool.PALM_DP` never closes a menu (`Ui.widestFingerDp`, which `DrawingPad` now shares). Brush Lab's swap panel stays pen-only. |
| 2026-10-23 | `5f764ec` (Lasso, image import) | Taken: `FloatingSelection`, `CanvasGuide` (now also the gradient guide, in place of Scribulor's `RasterOverlay` copy of it), `SelectionPreview` and `selection_preview.c`/`layers.h` (built, unused by Scribulor), `ToneDocument.addLayer(pixels)`/`copyLayer`/`overTone`/`beginLayer`, the Lasso in MonoPaint's hidden toolbar, Undo dropping a floating piece. Kept Scribulor's: `viewport_bitmap.c`, `DrawingBrowser`, the file menu and Import Image. Dropped: `ImageImport`, `PictureFiles` (request code 3 is Brush Lab's). Then the Lasso wired into Scribulor (below, "The Lasso"). Checks 125 RAN, none failed. |

## 7. Open questions for a first conversation (draft for Elling)

1. Is a shared brush engine interesting to you? As copied files in the same
   package, a Gradle module, or not at all?
2. Would you take a `ToneSurface`-style interface (or the new `ToneDocument`
   block methods) upstream, so an engine can sit outside `ToneDocument`?
3. Wet paint: MonoPaint's runs and flows; Scribulor's settles in place. Engine
   brushes now join `WetWatercolor` (the Wet Drying choice, 2026-10-22, below);
   would you take its small hooks (erasable and kind, rest, rewet) upstream?
4. Would M share codes work for MonoPaint's users (pasting brushes between
   apps, the web lab)?
5. Which of the section 4 fixes do you want, and how: PRs, patches, or issues
   first? Any code style or commit-message conventions?
6. Are you OK with Scribulor keeping the `io.github.mpdairy.monopaint` package,
   or would you prefer a rename?
7. How should Scribulor credit MonoPaint in the app and README?
8. Testing: are desktop checks plus a device bench a format you'd use?
9. Your roadmap: anything planned that would collide with Scribulor's edits to
   `DrawingPad`, `ToneDocument` or rendering?

## 8. Keeping this doc current

Update this file when:

- the engine's boundary changes (a class moves between (c) and (d), a new
  `ToneDocument` method, the facade or `ToneSurface` lands);
- a commit touches an upstream file (add it to the (b) table);
- a generic fix lands (add it to section 4 with its hash);
- `upstream` is fetched (update the date, divergence numbers and conflict log).

Numbers above come from `git log --oneline upstream/main..HEAD | wc -l` and
`git diff --stat upstream/main...HEAD`.

#### Brush import on resume (2026-10-18): upstream files touched

| File | Lines | Why |
|---|---|---|
| `PaintActivity` | 1 | `onResume` calls `inkWash.importBrushes()` (Scribulor-owned: reads Document/Scribulor import files). |

#### Fast display recovery (2026-10-18): upstream files touched

The owner's note: "sometimes gets sluggish … could jog it back by clicking open project … FAST DISPLAY stopped … after
hovering with the eraser ring … autosave?". Upstream's `flush` gave up the gray session after 120 refused presents
counted across every caller (the retry every 8 ms and the eraser's ring at every frame together, so a busy spell of
about 0.65 s instead of 1 s), and on any present failure dropped the canvas to Android drawing (slow on e-ink) until
something else called `connectDisplay` (a dialog closing, the window's focus back). Its notice waited in `saveError`
and showed as "Autosave failed: Fast display stopped …" at the next `showSaveError`, which `openDrawing` calls first:
so the notice came when he tapped Open, and closing the file browser reconnected. New `DisplayRecovery` (pure,
Scribulor's own; `DisplayRecoveryChecks`): a busy panel is given up after 120 refusals and 2 s, however many callers;
after a failure the canvas connects again by itself after 1 s, three times in a row at most until a present goes
through; the notice is said at once.

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | +35 −7 | `flush`: refused presents counted by `DisplayRecovery` (timed), a failure marks the page owed a refresh and calls `reconnectLater` instead of setting `app.saveError`; `reconnect` connects again when no pen or pinch is on the page; `connectDisplay` restarts the busy count and, failing while recovering, tries again; `disconnectDisplay` drops a pending reconnect. Generic: any MonoPaint build on a firmware whose driver refuses (EAGAIN) or errors now and then. |

#### Notebooks (2026-10-18): upstream files touched

Branch `inkwash-books` (spec `docs/archive/superpowers/specs/2026-10-18-notebooks-design.md`): the app opens to a shelf of
notebooks; everything lives in MonoPaint's own multi-page books, which Scribulor had hidden. Scribulor's own, new: `PagePicture`
(a page's 480 × 600 picture at 16 greys, pure), `BookPictures` (the notebook block's format and its random-access read,
pure, beside `BookCodec`), `NotebookMigration` (the old drawings into notebooks, once, pure Java), `NotebookExport`
(the PDF and grey PNG copies, pure Java), `NotebookLayout` (the shelf's and the notebook's sizes, pure), `NotebookScreens`
(the two screens, over the drawing screen), `PageTurner` (the bar's ‹ page ›, Notebook). Changed, Scribulor's:
`DrawLayout` (`LAYERS` off: the layer controls hidden, the layer routing untouched; the turner's items, folded on the
Nomad in landscape), `InkWashBar` (the turner at the foot; the layers not added), `AutoExport` (the notebook's copies
instead of one page's PNG), `DrawingNames` (`notebook`, `shown`, `stored`), `InkWashLayers.resized` (with an offset).
Engine untouched.

| File | Lines | Why |
|---|---|---|
| `BookCodec` | +98 −24 | Writes its stored ZIP by hand (`ZipOutputStream` can't put bytes before its central directory): the manifest, the pages, then the notebook block (`BookPictures`), then the central directory, which lists the manifest and pages only, as before, and a fixed entry time (the same book, the same bytes). The whole file stays within what `read` accepted before (16 MB + 64 KB): pictures that don't fit are left out. `read` no longer inflates every page: each page's ZIP CRC-32 (checked by `ZipInputStream`) and header (`DocumentCodec.checkHeader`) still refuse a damaged file, and only the shown page decodes (24 pages: 812 to 56 ms on this Mac). `readPages`: the checks without decoding, for the migration. Generic: a faster open for any MonoPaint book; older readers (MonoPaint's, this repo's `580a326`, checked) read a notebook as a plain book. |
| `DocumentCodec` | +12 | `checkHeader`: a page's format and size without inflating it. |
| `DrawingBook` | +132 −11 | Each page keeps an opaque picture that moves with it through add, duplicate and delete and is dropped once the page changes (`picture`, `pictureKey`, `offerPicture`, `restorePictures`), and the book a cover page (`cover`, `setCover`) that follows its page. `deletePage(int)`: delete a page other than the current one without decoding anything. `cachedPage`, `packed`, `Snapshot.packed()`. The snapshot carries the pictures, the cover and `written`. |
| `DocumentStore` | +57 | `migrate` (on the IO queue, after the library moves and before anything reads it), `notebooks` (every notebook, read from its file's end), `page` (one page decoded, for a missing cover), `forgetRecovery`, `library`; `Snapshot.prepare` runs on the IO thread before a named save (Scribulor makes the drawn page's picture there). |
| `DrawingFiles` | +23 | `notebooks()`: every drawing in the library and its folders but `Originals`; `root()`. |
| `DrawingPad` | +1 −1 | `showPage` never presents straight to the panel while the notebook screens cover the canvas. |
| `PaintActivity` | +145 −23 | The notebook screens in a frame over `root`, inside the turning frame; at launch the migration, then the shelf; the turner's and the screens' actions (`turnPage`, `showNotebook`, `drawOn`, `addNotebookPage`, `deleteNotebookPage`, `leaveNotebook`, `openNotebook`, `newNotebook`); new notebooks named `DrawingNames.notebook`; the file menu Rename notebook, Delete notebook, Finger (New and Open drawing go: the shelf does both); copies when a notebook or the app is left (`exportDue`); Back from drawing opens the notebook, inside it goes to the shelf. |

#### The audit (2026-10-19, branch `inkwash-audit`): upstream files touched

Speed and clean code, nothing the user sees but speed (findings and numbers: `docs/archive/notes/2026-10-19-audit.md`). Every
page is as before: the bench's 210 hashes at 8, 1 and 1000 samples a present and with every stroke quick.

| File | Lines | Why |
|---|---|---|
| `DocumentStore` | +5 −1 | The file thread (`IO`) at the lowest priority: the recovery save after every stroke compresses the whole page (seconds on the Manta) and took a core from the next stroke. Generic. |
| `DrawingPad` | +5 −2 | `renderAll` renders the full page through `ViewportBitmap.compose(…, area)` (the page's own pixels, bands on four cores) instead of the snapshot path (one core, and the snapshot made the next stroke clone whole layers). Generic. |
| `PressureStroke` | −8 +1 | Back to upstream's: `headThickness` was for Scribulor's old pens. |
| `ToolSettings` | −7 | `pressureFor` (old pens only) removed. |

Scribulor's own: `ModularCompose` (a transparent Drying body's preview recomposes only where the body newly lies),
`StickGrain` (the grain's noise built 4× faster, the same floats), `Cores.rethrow` (one copy of "rethrow another
thread's failure", used by `ModularStroke`, `QuickStroke`, `SettleWorker`), dead methods removed (`FloatBath`,
`HaloGrowth`, `LabUi`), `ToolsLayout` uses `LabLayout.clamp`.


#### Notebook pages read from the file (2026-10-18): upstream files touched

Branch `inkwash-books2` (spec, "Pages read from the file"): a book no longer holds every page's packed bytes in memory,
so a notebook may hold 100 pages and 200 MB of them (it was 16 MB). New, generic: `PackedPage` (a page's packed bytes, in
memory or a stored entry of the book's file read on demand and CRC-checked every time; one lock for reads and for a save
moving pages to the new file).

| File | Lines | Why |
|---|---|---|
| `DrawingBook` | small | Pages are `PackedPage`s (a `byte[]` list still makes one); `MAX_PACKED_BYTES` 200 MB; `offerPacked` (the page a save packed is kept, so leaving it needn't pack it again); the limits' words say "notebook" (`FULL`, `FULL_PAGES`, `LAST_PAGE`). |
| `BookCodec` | rewritten | `open`/`openPages(File)`: random access, the structure and every page's header checked, no page read; `write` streams stored pages from their file and returns where each page's bytes start; `replaced`: the rename and the pages pointed into the new file under the lock; one manifest parser for both readers. |
| `RecoveryCodec` | +70 | TSR3: only the pages not in the notebook's file, the rest by length and CRC; `read(input, drawing)` finds them; TSR1 and TSR2 still read. |
| `DocumentStore` | +25 | `openBook` opens from the file (an interrupted save's backup put back first, as AtomicFile reads it); a named save streams and then `replaced`; `recover` and `page` read from files. |
| `BookChecks` | 2 | `packedActivePage` is a `PackedPage`. |

Still whole in memory: `DocumentStore.move` (moving the library from private to shared storage, once) copies each file
whole. Generic: any MonoPaint book of any size opens and saves without holding its pages.

#### Saving less while drawing (2026-10-19, branch `inkwash-saveless`): upstream files touched

Audit findings 6 and 7 (`docs/archive/notes/2026-10-19-audit.md`). The recovery save ran after every stroke and packed the whole
page each time (240 ms of a core here on the owner's heaviest pages, about 4.8 s on the Manta at most), and its
snapshot made the next stroke copy a whole layer on the drawing thread. Now it waits for a pause, packs the page quickly,
and the page is packed once per change: what the recovery packed is what a page turn and the next save start from. Files
are byte for byte as before. New, Scribulor's own: `RecoverySchedule` (pure; `SaveLessChecks`).

| File | Lines | Why |
|---|---|---|
| `DocumentCodec` | +11 −1 | `write(stream, snapshot, level)`, any deflate level; `QUICK` (`BEST_SPEED`), 30% of the default's time for about 18% more bytes. The two-argument `write` keeps the default level. Generic. |
| `ToneDocument` | +34 −5 | `Snapshot.release`: each layer counts the snapshots holding its arrays (`holds`, under the layer's lock; `shared` volatile); a released snapshot takes its count back and at 0 the next write needn't copy the layer (9–14 MB). One never released keeps the copy, as before. Generic. |
| `PackedPage` | +12 −5 | `quick`: bytes packed at `QUICK`, for memory and the recovery file only. |
| `DrawingBook` | +62 −8 | A page left is packed at `QUICK`; `encode(document, level)` releases its snapshot; `offerPacked` lets quick bytes give way to the file's (any decoded page, `savedPages`); `savedPages`: after a save, the pages it was made from read the file's bytes; `Snapshot.packed` packs the active page quickly (the recovery), `Snapshot.filed` packs every quick page at the default level (a book's file), `activePacked`, `release`. |
| `BookCodec` | +1 −1 | `write(output, snapshot)` writes `snapshot.filed()`. |
| `RecoveryCodec` | +2 −1 | A recovery's inline pages read back as quick, so a file made from them gets the default level. |
| `DocumentStore` | +16 −7 | `recoverLater` keeps each snapshot's own result (it used the first caller's for all); the recovery and named-save snapshots are released once written or dropped. |
| `PaintActivity` | +45 −17 | `recovery()` is "the page changed": its save waits on `RecoverySchedule` (2.5 s still, 5 s at most, never while drawing, wet paint until 5 s); `recoveryNow()` saves at once at a pause, page turn, add or delete, a notebook opened or new, a named save, a rename; the notebook view and the shelf save a change still waiting (`recoverySaved`). A recovery's packed page goes back to the book (`offerPacked`). `saved` takes the file's pages back (`savedPages`) before its recovery save, which then packs nothing. |
| `DrawingPad` | +2 | `drawingNow()`: a stroke, shape, fill or gradient under way or still finishing. |
| `BookChecks`, `NotebookPagesChecks` | +3 −1, +2 | A page left is packed quickly; the checks' save takes the save back as the app does. |

Not changed: the notebooks' streaming save (`BookCodec.write`/`replaced`), TSR3's format, the migration (it writes a
working book's pages as it holds them; only a recovery written by this version, read by a migration that failed and runs
again, could put quick bytes in a notebook, which read the same). The PNG copies key each page by its bytes' CRC, so a
page exported while packed quickly is drawn again once more at the next export after a save.


#### Page shade (2026-10-19, branch `inkwash-pageshade`): upstream files touched

The owner: "a way to choose the color of a page on start". On a page with no marks (no paint, masking fluid or
magnets), a grey picked on the side bar shows a Fill page button beside it for 4 s; it shades the page's paper that grey
(the lightest grey: White page, plain paper again), one Undo step. The shade is a page property, the **paper tone**, not
a layer: every composite starts from it instead of white, and nothing else changes. Transparent paint (raw tone black at
its coverage) then darkens the paper as tinted paper takes a glaze, opaque paint and White cover it, the eraser rubs back
to it, and the brushes, which read their own layers over white, find blank paper whatever its shade. No per-pixel work
is added: a composite's first value is a field instead of a constant. A white page (255) writes, draws, exports and
pictures byte for byte as before (`PageShadeChecks` pins a drawn page's bytes, pixels, picture and PNG from before; the
bench's hashes, goldens and `LegacyConversionChecks` unchanged). New, Scribulor's own: `PageShade` (the offer: which grey,
when, for how long, pure; `PageShadeChecks`).

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +49 −20 | `paperTone`, `placePaper` (from a file), `shadePaper` (one Undo step: `StackState` holds the paper), `unmarked`; `Snapshot.paper`; `compositeToneWithout`, `compositeToneKept` and the tile composites start from the paper (`baseComposite` takes the paper to read over: Behind reads the marks over white, White and Invert the page as shown; `foundComposite`, Halo's, over white). The one write rule on a shaded paper only: a pixel holding darkness alone (a black raw tone) takes its new tone's coverage, lighter too, so a Slow wash settling lighter stays a glaze rather than turning into light paint over the shade (over white the same tone; white pages untouched). Generic: a tinted paper for any MonoPaint page. |
| `DocumentCodec` | +23 −8 | TSM5's `PAPR` section: the paper tone, a byte (a later version may add more; the reader takes the first), written only when the paper isn't white, TSM5 written for that alone too. A TSM5 reader from before skips it and opens the page on white; MonoPaint and Scribulor before TSM5 refuse a TSM5 page ("Unknown painting format"), so a shaded page that would otherwise be TSM3 or TSM4 doesn't open there at all. |
| `ViewportBitmap`, `viewport_bitmap.c`, `compose.h` | +15 −14 | The native compose's paper tone (`nativeCompose(…, paper, …)`), hoisted: each row starts from it as it started from white. `compose_check.c` compares it with the Java renderer on white and on a shaded page. |

Scribulor's own: `PagePicture.Composite` (a snapshot's paper; the notebook pictures and `NotebookExport`'s PNGs and PDF),
`InkWashLayers.resized` (keeps the paper), `ModularStroke.reading` (the page a Blend reads, on the paper it needs; used by
`ModularStroke.under` and `QuickStroke`), `ModularCompose.Under` (its paper), `SwatchFaces.Host.greyPicked`,
`SwatchBar.grey`, `InkWashBar` (`offer`: a button in the label's place beside the column, taken away by the pen as the
label is; the grey flyout opening hides it). Brush Lab's test page is never shaded.

#### Naming notebooks and the blank page at the end (2026-10-19, branch `inkwash-booknames`): upstream files touched

The owner's feedback on notebooks: a name prompt for a new notebook (the name it would get, selected, and three
suggestions, one tap each), Rename and Delete by a long press on the shelf, a blank page deleted without asking, and
always one blank page at a notebook's end. New, Scribulor's own: `BlankPages` (what is blank, the page at the end, the
copies leaving out blank pages at the end, pure; `BlankPagesChecks`), `NotebookNames` (the prompt's names, pure;
`NotebookNamesChecks`), `NamePrompt` (the dialog). Changed, Scribulor's: `NotebookScreens` (a long press on a tile; the
open notebook's shelf count is its file's while unchanged), `NotebookExport.update` (blank pages at the end left out),
`DrawingNames` (`notebooks`: several names at once; `isoDate` package-wide), `PagePicture.white`.

| File | Lines | Why |
|---|---|---|
| `DrawingBook` | +11 | `appendUnchanged(page, picture)`: a packed page after the last, the current page staying current, not counted as a change (opening a notebook puts its blank page there without rewriting the file). Generic. |
| `PaintActivity` | +90 −70 | `newNotebook` asks the name first (`NamePrompt`); `renameNotebook(path)` and `deleteNotebook(path)` for any notebook (the menu's, the title's and the tile's long press, `notebookMenu`), replacing `renameDrawing` and `deleteDrawing`; a blank notebook is deleted without asking; `openNotebook` puts the blank page at the end (on the store's thread for another notebook); `recovery()` tells `BlankPages.Watch` of each change, `updatePages` of each page shown; Delete page asks only for a page that isn't blank (`deleteNotebookPage`, the page menu's). |

#### Clear page (2026-10-19, branch `inkwash-clearpage`): upstream files touched

The owner: "long press on eraser: option to clear page?". A long press on the side bar's eraser tile shows a Clear page
button beside it for 4 s (the label's place, as Fill page), only on a page with something on it (`unmarked`). It takes
off every layer's marks, hidden ones too, the masking fluid and the magnets as one Undo step, with no question; the
layers, the shade and the page's place stay. New, Scribulor's own: `PageClear` (the words and when it is offered, pure;
`PageClearChecks`: clear then Undo gives the page back byte for byte).

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +33 −9 | `clearPage`: `clearAllLayers` with the magnets too (both share `blankLayers`); `StackState` holds the magnets (immutable, by reference), so one structural step clears and restores them with the layers. A structural step's history bytes are the larger side's layers, not both sides': while undoable it holds the layers it took off (those it put on are the page's own), while redoable the reverse. Clearing a Manta page swaps every layer: the sum (about 49 MB with Erasable's shares) was over the whole 32 MB budget, so the next stroke dropped the clear from Undo; it is about 30 MB now, its old layers held by reference, never copied. Generic: any MonoPaint step that replaces layers. |

Scribulor's own: `InkWashBar` (the eraser tile's long press, `offerClear`: `layerChange`, so wet paint dries first and the
page has one clean refresh after; the note "Page cleared").


#### Finger button (2026-10-19, branch `inkwash-clearpage`): upstream files touched

The owner: "not sure where to set the finger control.. can we use an icon?". Under the smudge tool's tile, a Finger button
(Phosphor's `hand-pointing`, Bold, `@phosphor-icons/core@2.1.1`: `ic_ph_hand_pointing`) marked with what one finger does:
struck through for Nothing, a small eraser, the Smudge part's glyph (`ic_part_carry`) or the slot's number in its tile's
ink badge. A tap opens the same menu as the file menu's Finger row, beside it; both write `FingerTool.KEY`. It is in the
column exactly where Words is (`DrawLayout.finger`, counted with Words): not on the Nomad in landscape, where the menu has
it. One icon more takes the Manta's faces from 58 to 56 dp tall (squares 46 to 44) and the Nomad's squares from 27 to 25
(least 23); `DrawLayoutChecks` checks the fit on all four screens.

| File | Lines | Why |
|---|---|---|
| `PaintActivity` | +10 −5 | `fingerMenu` package-visible, tells the bar when the choice changes; `menu` moved up where it would run off the foot (the Finger button sits low in the column), so it never scrolls. |

#### Notebook covers (2026-10-19, branch `inkwash-covers`): upstream files touched

Stage 1 of the notebook options (docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md, "Covers"). Every notebook
has a front and a back cover, drawn on like pages, before the first page and after the last; their paper is a marble made
from the notebook's name (and on the front, a label with the name and, if chosen, a smaller copy of a page). The paper
is a page property again, now a picture: `ToneDocument.paperPixels`, a tone a pixel in place of the paper tone, never
saved with the page nor in Undo (the notebook makes it when the cover is shown). Every composite starts from it as from
the shade; the native compose takes it as one more, fully covering layer under the others, so its C code is unchanged
but for the count. New, Scribulor's own: `CoverMarble` (the marble, pure, seeded from the name, kept in memory and in the
app's cache folder), `NotebookCovers` (the label, the picture's place, the paper, shelf tiles, the PNG key; pure),
`BookOptions` (the options block "INO1"), `CoverLettering` (the label's letters on the device); checks `CoverChecks`,
`OlderCoversCheck` (against 2a352ed), compose_check on a cover.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +40 −8 | `paperPixels`, `placePaper(byte[])` (no Undo step, the page dirty), `PAGE_PAPER` for `baseComposite` (the page's own paper: its shade or its pixels), `visibleLayers` puts the paper pixels first as an opaque layer (`opaque(n)`, one all-255 array kept), the composites start from `paperAt(i)`; the glaze rule of a shaded paper applies on paper pixels too; `Snapshot.paperPixels`, `Snapshot.unmarked`, `Layer.covers`. Pages without paper pixels write, draw and compose as before. Generic: a patterned paper for any page. |
| `DrawingBook` | +170 −60 | `FRONT`/`BACK` places beside the pages (negative, so the page shifts never move them), `after`/`before`, `valid`; the covers' packed bytes (null with nothing drawn: nothing kept), their pictures, the book's `name` for their paper, made when a cover is decoded or turned to (`paperKey` says when to make it again); `cover` is now the cover picture's page (-1 none) and goes when its page is deleted; covers can't be copied or deleted; `Snapshot`: `fileIndex`, `packedCovers`, `filedCovers`, `writtenCovers`, `picture(i)`/`setPicture(i)`. |
| `BookCodec`, `BookPictures` | +60 −10, +100 −20 | The options block between the last page and the notebook block (`BookOptions`), written only when a cover is drawn on or a page is the cover picture; the notebook block's cover is the picture's page or the first page, as older readers expect. `openPages`/`readPages` find it (covers read from the file when needed), `replaced` points the covers into the new file; `readCover` reads the page size, the picture's page and the front cover's marks. Older readers (2a352ed, 580a326 and MonoPaint) never reach it: they stream to the last page or read the notebook block just before the directory, as before. |
| `RecoveryCodec` | +60 −40 | TSR3's covers section after the pictures (older readers stop before it); the header's page is the file's. |
| `ViewportBitmap`, `viewport_bitmap.c` | +6 −4, +5 −4 | Nine layers at most (eight and a cover's paper). |
| `ModularStroke` | +1 −1 | White and Invert read the page over its own paper (`PAGE_PAPER`). |
| `PaintActivity` | +70 −20 | The name to the book (`nameBook`), ‹ › through the covers (`turnPage`), the menu's cover picture rows, the marble made ahead from the first and last page, the lettering and cache folder set at start. |
| `DocumentStore` | +10 −4 | The shelf's `Notebook` keeps the page size and the front cover's marks; `page(path, FRONT)`. |

The PNG copies (`NotebookExport`, Scribulor's own) start with `00-cover.png` and end with `zz-back.png` (the PDF, since
2026-10-24, starts with the front cover and leaves the back out), each carrying `NotebookCovers.key` (marble version, name, label, size, the cover's and the picture page's CRCs) in place of
a page's CRC, so a cover is drawn again only when one of them changes.

#### Cover designs (2026-10-19, branch `inkwash-covers2`): upstream files touched

The covers become composition books (the approved prototype `docs/archive/brush-lab/prototypes/cover-designs.html`), each
notebook in one of nine designs, Classic Black Marble by default. New, Scribulor's own and pure: `CoverNoise` (the
prototype's seeds, hashes, gradient noise and cells, ported exactly), `CoverDesign` (the nine, their keys and fields),
`CoverPattern` (a design's sheet at a size: the lattice shared over `Cores`, the cut by slope, the tape; the cache, in
memory and the cache folder's `covers/`, and `prepare`, moved here from `CoverMarble`), `CoverChooser` (the Cover Design
dialog, UI); `CoverMarble` is now Swirl's suminagashi alone; `NotebookCovers` draws the new label (`Label`, `Words`;
`Lettering` draws words, `CoverLettering` on the device). Checks `CoverDesignChecks` (each design against the prototype's
output, to the bit), `OlderDesignCheck` (against 6e3bae8).

| File | Lines | Why |
|---|---|---|
| `DrawingBook` | +25 −12 | `design()`, `setDesign` (a change to save; the cover drawn on refreshed by its caller, as its pattern may take a while), `restoreDesign`; `paperKey` names the design; `name(String)` (the label is made from the stored name, so the name as shown went); `Snapshot.design`. |
| `BookCodec`, `BookPictures` | +3 −1, +6 −2 | The design into and out of the options block; `readCover` reads it for the shelf. |
| `RecoveryCodec` | +10 −1 | A DSGN section after the covers', only when the design isn't the default (older readers stop before it). |
| `DocumentStore` | +25 −3 | `Notebook.design`; `design(path, design)`: a saved notebook's file opened, its design set, saved again (the shelf's long press on a notebook not open). |
| `PaintActivity` | +60 −10 | Cover Design in the file menu and the shelf's long press (`chooseCoverDesign`, `setCoverDesign`: the pattern made ahead, then the cover drawn on refreshed); `CoverPattern`'s folder and the old `marbles/` folder emptied at start. |
| `DrawingFiles`, `DrawingNames` | +5, +1 −4 | `isoDate` moved to `DrawingFiles` (the label's date; `DrawingNames.isoDate` calls it). |

`BookOptions` (Scribulor's own) gains the `DSGN` record (the design's key in UTF-8), written only when the design isn't
Classic: an undrawn Classic notebook is byte for byte as before. The covers version (6e3bae8) skips the record and opens
such a notebook with its covers; 2a352ed never reads the block. `NotebookCovers.key` (the copies' PNG key) names the
design and `CoverPattern.VERSION` (2).

#### Covers made fast (2026-10-19, branch `inkwash-coverfast`): upstream files touched

The owner: "notebook covers seem to take a while to load..". Each design's pattern is now made once, ahead, by its own
generator (`CoverDesign.sheet`, `scripts/cover_textures.sh` and `CoverTextures`, a desktop maker) into a 1-bit PNG in
`app/src/main/assets/covers/` (1.85 MB for the nine), and a cover is a window of it chosen by the name (`CoverTexture`,
Scribulor's own and pure: the PNG read, averaged-down levels for small covers, the window turned or flipped where the
design allows). The generators stay, as the sheets' makers, the prototype's checks and the fallback for a design without
a sheet. The cache folder's `covers/` is gone (a window is made faster than such a file is read back) and emptied at
start like `marbles/`; `CoverPattern.VERSION` is 3. The shelf finds a tile by what its pictures hold
(`NotebookCovers.shownKey`), not by their arrays, and doesn't build itself again when the notebooks read are as shown.

| File | Lines | Why |
|---|---|---|
| `BookPictures` | +30 | `cover(File)`: `readCover` kept by path, length and time, so a return to the shelf reads no file unchanged and its pictures are the same arrays. |
| `DocumentStore` | +1 −1 | The shelf's listing reads covers through `BookPictures.cover`. |
| `PaintActivity` | +12 −10 | `forgetOldCovers`: the cache folder's `marbles/` and `covers/` emptied at start; no cover folder set. |

#### Zoom and its lock (2026-10-19, branch `inkwash-zoom`): upstream files touched

The owner: "can we add zoom and lock zoom like in monopaint?". MonoPaint's zoom (its TODO.md "Zoom and pan lock", "Faster
pinch and pan preview") was still in `DrawingPad`, built but switched off (`navigationLocked = true` at every start). It
is on again, in Scribulor's bar: a Zoom button under redo (`ZoomButton`: Phosphor's padlock over the percentage, through
the fast feedback path; MonoPaint's `ZoomButtonTouch` on it, so a second quick tap is 100%), locked at first, the choice
kept (`PaintPreferences.navigationLocked`, upstream's). Where the column has no room (`DrawLayout.zoom`, the Nomad in
landscape, which would need 31 dp more) the menu has a "Zoom 100%: Locked" row instead, as it has Words. Pinch and pan,
the 800% limit, pinch inward to fit, new pages and turns starting fitted, the zoomed raster dithered at screen pixels
and the gray presenter kept through a pinch are all MonoPaint's, unchanged; at 100% nothing takes the zoomed path, so
drawing is as fast as before.

What Scribulor adds is how two fingers share the page with its taps and the finger tool. MonoPaint starts navigating at
the second finger's landing, so a two-finger tap (Undo) could also nudge the view (a 103% zoom). Here two fingers are
a tap until one moves past the tap's own slop (18 dp, `MultiFingerTap`'s), and only then navigate, from where they
landed, the tap reset so no Undo fires; a third finger before they move makes a three-finger tap; the finger tool still
sees every touch first and takes its stroke back when the second finger lands (`FingerTool.cancels`), which no longer
keeps the two from navigating. Pure, `PinchPan` (`PinchPanChecks`, with `PageView`: the page's map at 100%, 200% and
800%, panned, all four turns, against MonoPaint's rotate-scale-move).

Zoomed in, the engine draws what it draws at 100%: sizes are page pixels, and every page path (the native area
render, the zoomed raster, the eraser's ring, magnets' outlines and hit tests, the finger tool, the drying ticker, Float,
Mold and gravity redraws, the page shade's paper) already maps through `pageToView`. Print Shading's page-fixed dots are
page pixels in the layers, so the zoomed raster's nearest sampling magnifies them like a printed page. One thing broke:
the pen's speed was read in page pixels, calibrated as screen pixels (`PenSpeed`), so at 800% a hand's usual speed
read as a crawl and every speed-thinned brush went fat (Brush pen +25% ink, Anything goes +78%), the eraser small. A
stroke now takes the zoom (`DrawingStroke.zoom`) and reads speed on the glass: `StrokePoints.zoom`, `PenSpeed.zoom`
(`ZoomStrokeChecks`: within 6% of 100% at 200% and 800%; at 100% byte for byte as before). The bar's size dots still
show the size at 100%, as MonoPaint's do.

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | +44 −36 | `navigationGesture`: two fingers through `PinchPan` (land, start past the slop, step), a finger's own stroke not blocking it, called after `fingerStroke` so the finger tool sees the second finger first; `historyTap`'s slop is `PinchPan.SLOP_DP`; `place` sets `PageView.map` (the same rotate, scale and move); `updateViewport` tells the bar's Zoom button; a stroke beginning gets `zoom(viewport.scale())`. Generic: the tap-or-pinch rule, for any MonoPaint whose two-finger tap undoes. |
| `PaintActivity` | +15 −17 | `navigationLocked` read from the preferences again (upstream's line); `toggleNavigationLock` through the bar's `ZoomButton` (MonoPaint's toolbar is hidden); the menu's Zoom row where the bar has no room (`fileMenu` builds its rows as lists now, Words' and Zoom's alike). |
| `DrawingStroke` | +6 | `zoom(scale)`, default nothing. Generic: any MonoPaint stroke that follows the pen's speed. |

Scribulor's own: `PinchPan`, `PageView`, `ZoomButton` (new); `DrawLayout` (`Part.ZOOM`, `zoom`; Zoom and Words are
"extras", an icon and a rule each, where they fit: `count`, `total`, `rules` take how many); `InkWashBar` (the button and
its rule in the column, shown where it fits); the engine's `StrokePoints`, `PenSpeed`, `ModularStroke`, `QuickStroke`,
`EraseBothStroke` (`zoom`); `SwatchFace` (its note).

#### Edit buttons, the six slots on the Tools page, no numbers (2026-10-19, branch `inkwash-baredit`): upstream files touched

The owner: "instead of a little tiny caret in the swatch and sizes, let's add a way to customize the set below? also
similarly have a way to load the brushes with brushes from the library.. maybe a big modal with the slots"; "no need to
have numbers by the brushes?"; "lab... 'create new brush' should be an option in there". Scribulor's own: `DrawLayout`
(the two Edit buttons, `Part.EDIT_TOOLS` and `EDIT_SIZES`, `Item.EDIT`; a tray row's second tap does nothing),
`InkWashBar` (the Edit buttons, a tray row's long press, the tray strokes the row's width, the Finger mark and choices
drawn from the tray strokes), `SwatchFace` (no ▾), `SwatchFaces.editAll` and `SizeModal.set` (the whole set, built from
the size modal's `SizeEditor` and the grey flyout's `GreyGrid`, which the two old pickers use too), `SlotTile` and
`LoopCard` (no number badges), `SlotRow` (the six slots' tiles, shared by Brush Lab's `LabLibrary` and the Tools page),
`ToolsPage` (six slots, one selected; Undo across them), `ToolsLayout` (the slots' row), `ToolsActivity` (the slots,
New Brush), `BrushLabActivity` (`FRESH`: opens on a new brush, returns once it is saved), `FingerTool` (a slot's choice
named by its brush). Engine untouched.

| File | Lines | Why |
|---|---|---|
| `PaintActivity` | +72 −33 | `toolsReturned` takes the six slots' brushes the Tools page returns (`slotsReturned`, shared with `brushLabReturned`) and selects its slot, then opens Brush Lab, or Brush Lab on a new brush (`FRESH`), whose return opens the Tools page again; `fingerMenu` names a slot by its brush and gives each row its picture (`InkWashBar.fingerIcon`); `menu` takes drawables (the int overload maps to them) and marks the chosen row with the bar's right-edge bar in place of the ink fill; the file menu's Finger row is `InkWashBar.fingerRow`. |

#### The side bar's two branches together (2026-10-19, branch `inkwash-barmerge`)

`inkwash-zoom` and `inkwash-baredit` merged: one column list (`DrawLayout.COLUMN`) holds everything, top to bottom:
menu, Brush Lab, undo, redo, Zoom, the six pens and their Edit, the four pairs and their Edit, the tools two by two
(Smudge | Fill over Eraser | Finger, Fill's cell measured and hidden until `InkWashBar.fillButton`), Words, the wet
indicator, the page turner. Zoom's "extras" counting (`count`, `total`, `rules` take how many) and baredit's
`Item.EDIT` sit side by side. Where room is short what stays is the pens, the pairs, the tools, undo, redo and the
two Edit buttons; then the page turner (whole before the extras, else folded); then Words, then Zoom (Words is now
fitted first). The two Edit buttons fold the page turner on the Nomad in landscape even without the extras, so
Words and Zoom both fit beside it there (2 dp to spare at the least sizes): on all four screens both are in the bar,
and their menu rows show only on shorter screens. The Zoom button's padlock is `DrawLayout.zoomLock` (16 dp, 13 on
40 dp icon buttons) so it and the percentage keep 2 dp over and under. `DrawLayoutChecks` checks the combined fit
on all four screens and the order of what goes (`priority`). `PaintActivity.fileMenu` keeps zoom's list rows, the
cover rows and baredit's Finger row (`InkWashBar.fingerRow`).

| Screen (dp) | Items / room | Heights (icon, tray row, face, Edit) | Page turner | Words, Zoom |
|---|---|---|---|---|
| Manta 1024×1365 | 1126 / 1345, 219 spare | 56, 30, 34 (28 square), 32 | whole | bar, bar |
| Manta 1365×1024 | 990 / 1004, 14 spare | 48, 28, 33 (27 square), 29 | whole | bar, bar |
| Nomad 749×998 | 962 / 978, 16 spare | 47, 26, 32 (26 square), 27 | whole | bar, bar |
| Nomad 998×749 | 729 / 731, 2 spare | 40, 26, 32 (26 square), 26 | folded | bar, bar |

#### The grey bar (2026-10-19, branch `inkwash-greybar`): upstream files touched

The owner: "for selecting the swatches... can we have a gradient displayed like in monopaint in the sizes and greys
selector? right now it's just grays (and doesn't include black for some)". The Sizes and Greys window's and the grey
flyout's eight fixed greys (`GreyGrid`, `ToolSwatches.FLYOUT`) became MonoPaint's color bar: `GreyBar` (Scribulor's own),
black to white in the panel's dots, a marker under the grey in use, the grey big beside it (`SizeModal.GreyEditor`). It
snaps to the panel's 65 dot densities, MonoPaint's levels (`DotPattern.pickerTone`); a grey swatch keeps any darkness
0–1 as before (`ToolSwatches.darkness`/`tone` map a level to darkness and back). The missing black: a wash's swatch shows
its glaze (`ModularSizes.swatch`, `GLAZE` .34; the engine glazes at `0.34 × shade`), so every wash's eight greys topped
out at 34 % grey; the bar keeps black and white at its ends for every brush, its big grey and words ("Black glaze, 34%
grey") showing what one stroke lays. Engine untouched. The window's geometry is pure now (`SwatchSetLayout`, checked by
`GreyBarChecks` on the four screens: 589 dp tall, 300 wide); its px-and-mm line was 24 px tall, not 24 dp, and clipped
its text on the tablets.

| File | Lines | Why |
|---|---|---|
| `ShadePicker` | +26 −67 | The strip's end zones, tone at a position, marker place and pixels are `ShadeStrip`'s (new, pure); the pen's pointer, the deferred marker frame and the clean pass 250 ms after a lift are `StripMarker`'s (new). Same numbers and order; Scribulor's `GreyBar` uses both. |

#### Washes reach black (2026-10-19, branch `inkwash-washblack`): no upstream files touched

The owner chose to let washes reach black. The six-part engine (Scribulor's own, and its web twin in
`docs/brush-lab/prototypes/modular-lab-src`) glazed every body at `0.34 × shade`; a wash's now lays
`ModularSizes.WASH_BLACK` (.97) of its swatch's darkness where its body is full (`washStrength`), ink's wet body and
opaque paint's as before. Past `ModularSizes.KNEE` (.55) a body's strength eases toward black (`laid`) instead of
clipping at 1, so a black wash keeps its pooled edges, blooms and grain; every body laid before stays under the knee
(.34 × 1.6), so it lays byte for byte as it did. The same `laid` sets compose (`ModularCompose.tones`), Drying's held
back body and what settles (`ModularStroke.held`, `DryingWater`, which settles a cell to its own darkness past .95),
Halo's and Float's greys, the screened preview (`QuickStroke`), and gravity near black (`GravityFlow.deep`: its caps and
a drip's shading rise with the wash, 0 for every wash before). Drying's preview grey follows the glaze
(`ResolvedBrush.previewShade`). Greys kept before stay where they were (`inkwash_grey_`) and draw as they did
(`ModularSizes.sinceBlack`: a wash at .34 / .97 of them, an ink at them); a grey picked since is kept under
`inkwash_dark_` (`ToolSwatches.look`), so nothing is rewritten, an older build reads its keys, and a pen put in a slot
that held a wash still draws at the slot's grey as it would have. Samples and cards show a wash at the old black (`ModularSizes.sampleDarkness`). Codes and files
unchanged.

#### Fill (2026-10-19, branch `inkwash-fill`): upstream files touched

The owner: "fill button beside the smudger ... then it's four tools that would stack nicely". A paint bucket (Phosphor's
`paint-bucket`, Bold, `@phosphor-icons/core@2.1.1`: `ic_ph_paint_bucket`) in the bar's Fill place. While it is on, a pen
tap fills the area there, shut in by the selected pair's crisp ink lines, with the selected slot's brush, on that pair's
wash layer, as one Undo step. New, Scribulor's own and pure: `FillRegion` (the region: the web prototype's walls1 and room1,
section 1 of `docs/archive/brush-lab/prototypes/mischief-lab-2.html`, worked out in 2 × 2 page px cells, about the prototype's
pixel, then drawn at page resolution; a gap closes only where a line ends, so a wobbling wedge's pinch stays open;
pockets chain twice; `FillRegionChecks`, with a timing at the Manta's page size) and `FillTool` (`FillChecks`).

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | +38 | `fillToolTap` (with Fill on, the pen's landing marks the spot, its lift fills there; the eraser end erases as ever) and `layFill` (the fill as a stroke: `liftQuick` runs a Slow fill's lift off the pen's thread, else `finishStroke`; the note "No closed area"). Scribulor's own pen path only. |

Scribulor's own: `ModularStroke.fill` (the brush laid over a region rather than along samples: its line's coverage and
its body's paint at a firm, still press; a Fast-wet body settled once as a Drying lift settles it; a Drying fill's lift
skips laying the line and body again; the settled body, relief and line kept to the region, and the settling after lift
lands only there (`DryingWater`'s lands) and doesn't run downhill; no dust), `InkWash` (`filling`, `fill`, and `before`,
what any stroke or fill does first), `InkWashBar` (the button, `tapFill`, Fill off at any other tool's tap).
#### Mirrors and the Fun button (2026-10-19, branch `inkwash-mirrors`): upstream files touched

The owner: "how about a button with the phosphor confetti for the fun options (mirror, magnetic poetry, and any future
weird stuff)"; Mischief Lab 2's section 6, "mirrors is really cool, i love it. perfect, no notes"; and "maybe there should
be a mode where the mirrors remove part of the screen if there was stuff there before? to be like a real mirror placed
on the page". The bar's Words button became **Fun** in the same place (`DrawLayout.Part.FUN`, `fun`; the fit is
Words', unchanged on all four screens), Phosphor's `confetti`, Bold (`@phosphor-icons/core@2.1.1`: `ic_ph_confetti`);
the Mirrors row's icon is Phosphor's `flip-horizontal`, Bold (same version: `ic_ph_flip_horizontal`). Its list
(`FunMenu`, through `PaintActivity.menu`) has a row a mode, Magnetic Poetry (the magnets' bin) and Mirrors (their own
list: Mirrors on or off, Copy Strokes, Real Mirror, Place Mirror, Kaleidoscope, Flip Side, Keep Reflection, Clear Mirrors (now Remove Mirrors, beside Show Guides: `inkwash-mirrorfast`, below), and the
caption "3 mirrors, 6 copies"). Scribulor's own, pure (`test_inkwash.sh`, `MirrorChecks`, `MirrorOlderReaderCheck`
against 2327d14): `Mirrors`, `MirrorStroke`, `MirrorShow`. The engine is untouched: copies are the engine's own
strokes (`ModularStroke.start`, `EraseBothStroke`), made in `InkWash`.

- **The maths** (`Mirrors.Group`): the prototype's, breadth first from each mirror's reflection, maps the same to 1e-4
  in their turn and half a pixel in their move, at most 24 counting the page. Snapping (`Mirrors.fit`): the stroke's
  principal axis, 15° steps within .055 rad, through a crossing of mirrors or the centre within 12 prototype pixels;
  under 24 is "Too short". Prototype pixels are CSS pixels (1/96 inch) scaled to the 300 ppi page (`PX`, 3.125).
- **Copy Strokes** (`MirrorStroke`): while the pen is down only the stroke itself is drawn, unchanged, as fast as
  before. The canvas shows the copies from it (`MirrorShow`): each time the page changes under the stroke, every map's
  image of the change is kept as tones (`ToneDocument.gestureTones`, or the eraser's `erasedTones`) and drawn over the
  render, at most `LIVE_PX` pixels a frame (the farther maps of a big wash wait for the lift; a pen's few pixels show
  all 23). At lift the stroke finishes (its own lift work first) and the copies are drawn one by one, each a live
  stroke of the same tool fed the mapped samples at once (tilt mirrored too), each on the page as the last left it, so
  overlaps lay over each other as two strokes do; `QuickLift` looks in between, one copy a look; a copy wholly off the
  page is culled (since `inkwash-mirrorfast`, below, most brushes' copies are the stroke's change reflected instead). `ToneDocument.linkSince` links every step after the stroke began into one Undo step. A copy's Slow
  wash lays the one before it settled (the session settles one wash at a time); the last keeps settling.
- **Real Mirror** (`Mirrors.View`): the kept point's images, one a map; a pixel nearest image k lies in cell k (each
  image's Voronoi cell, convex, so a row crosses each once and is a few runs found by halving) and shows the page at
  map k's inverse of it. The canvas draws covered cells over its native render (`MirrorShow.draw`, in bands on the
  cores: a whole Manta page's kaleidoscope 20 ms on this Mac, its guides 4 ms); a change under the pen re-renders its
  images too, so drawing shows in the reflection at once. A stroke draws on any side (2026-10-19, branch
  `inkwash-mirror2`; the owner: "allow drawing on either side after placement (and allow drawing 'through' the
  mirror"): `MirrorStroke` folds each sample into the kept cell (`View.fold`, the inverse of the map whose cell holds
  it, tilt turned too), and where the pen crosses into another cell it adds a sample at the crossing (`View.leaves`,
  the segment against each cell's half-planes), pressure, tilt and time between, so the mark meets the mirror and goes
  on as its reflection, one stroke. Across a group's mirror the two folds meet; past the cap (non-dividing angles) they
  may not, and the rest is a stretch of its own, drawn at lift as a copy is, linked into the same Undo step. Keep Reflection (`ToneDocument.keepReflection`) lays the view
  into every layer and the masking fluid, one structural step holding the old layers by reference. Default side: the cell
  with the most marks (`Mirrors.mostInk`); Flip Side (`Mirrors.flipped`) keeps the next cell round, of those whose
  image of the kept point is on the page, sorted round their middle (one mirror: the other side). The pen's stroke
  never picks it, so no stroke is lost to it.
- **Exports**: guides never; Real Mirror's reflection as the page shows it, in `ToneDocument.exportPixels` (the full-size
  PNG) and `PagePicture.Composite` (the notebook's PNG and PDF copies, the shelf's pictures). Not in the dot-size export
  (`ViewportBitmap.dithered`, native) nor the layer thumbnails.
- **Fill** (since `inkwash-fillmirror`, below): with Copy Strokes each image's area (`Mirrors.Group.points`), with Real Mirror the tap folded into the kept side.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +105 −5 | `mirrors()`, `placeMirrors` (from a file), `showMirrors` (on or off: no step), `setMirrors` (a structural step: `StackState` keeps the mirrors, `restore` keeps on or off as it is), `keepReflection`, `gestureTones`, `lastStep`/`linkSince`; `Snapshot.mirrors`, `unmarked` counts them; `exportPixels` shows Real Mirror's view. Generic but `keepReflection`: any per-page guide kept in the history, a gesture's changed pixels, steps linked after the fact. |
| `DocumentCodec` | +20 −5 | TSM5's `MIRR` section (`Mirrors.encode`: version, on, style, the kept point, the lines), written only when a page has mirrors; older readers skip it (checked against 2327d14); pages without mirrors write as before, byte for byte. |
| `DrawingPad` | +65 −6 | `mirrorShow`, its areas in `renderDirty` beside the magnets', `drawMirrors` in `render` and `renderAll` before the magnets; `placing` (the pen's next stroke a `MirrorShow.Place`); the eraser checks through `own(stroke)` (a `MirrorStroke`'s primary); the live copies begun with the stroke and ended at its finish. |
| `PaintActivity` | +30 −10 | `fun` (`FunMenu`); the menu's Fun row where the bar has no Fun button; `menu` marks any rows (`boolean[]`) and takes a caption under them. |
| `PagePicture` | +20 −5 | `Composite` reads Real Mirror's view (its source pixel a row). |

| Screen (dp) | Items / room | Page turner | Fun, Zoom |
|---|---|---|---|
| Manta 1024×1365 | 1126 / 1345, 219 spare | whole | bar, bar |
| Manta 1365×1024 | 990 / 1004, 14 spare | whole | bar, bar |
| Nomad 749×998 | 962 / 978, 16 spare | whole | bar, bar |
| Nomad 998×749 | 729 / 731, 2 spare | folded | bar, bar |

#### Mirrors fast, Show Guides, Remove Mirrors (2026-10-19, branch `inkwash-mirrorfast`): upstream files touched

The owner, on the Manta: "after drawing a long line in kaleidoscope mode it got really sluggish and the marks didn't
show for a few seconds... seems to happen also with just two mirrors. just one mirror is fine. we should add a way to
hide the mirror dotted lines." Each Copy Strokes copy was a real stroke drawn again at lift, one a look on the UI
thread: as long as the stroke itself, so a kaleidoscope's five cost five strokes.

- **Copies laid as the stroke's change reflected** (`ToneDocument.darkeningSince`, `layAgain`; `MirrorStroke`): for a
  brush whose compose only darkens what it finds by a factor of the mark's own (`ModularCompose.darkensBy`: its own
  Blend, no cover, relief, Screen, resist or smudge, not a charcoal stick) and that is final at lift with nothing that
  reads which way is down (`ModularStroke.laysAgain`: no settling after lift, Halo, Float, dust or gravity), the
  stroke's one Undo step is read as each pixel's factor (its tone over white after over before; NaN where it found
  black), and each copy is a gesture of its own that darkens what it finds by its source pixel's factor through
  `setTones`, the stroke's permanent share kept (`owns` from the factor), in bands of tile rows on the cores. Where a
  map carries pixel centres onto pixel centres (a mirror on the grid) a copy reads one pixel: the stroke reflected,
  exactly; where it turns (the kaleidoscope's 60° and 120°) it reads between pixels, Catmull-Rom at an edge (kept
  within the four nearest, so no ringing) and bilinear elsewhere. Only blocks (16 px) whose source holds a change are
  looked at. Over other marks it darkens them as a copy drawn again does (exact for a mark that multiplies). Not with
  masking fluid on the page (the stroke's write was shielded where it went), nor for opaque paint (Gouache), a smudge,
  the eraser, Halo, Float or a wash that settles after lift: those copies are drawn again as before. The default
  Fineliner, Brush pen, Pencil, Ink wash and Watercolor qualify. What is approximate: the copy's paper grain and the
  brush's noise are the stroke's, reflected, not the page's at the copy (Pencil's grain shows it: on the grid 9 % of
  its pixels differ by more than 8 greys from a copy drawn again); turned copies differ from copies drawn again by
  3.8 greys on average for the 6 px Fineliner (edge pixels up to about 50), 0.1 for a 120 px wash; what the eraser
  leaves of a reflected copy is the stroke's within a grey. Checked: `MirrorChecks.reflected`.
- **Lift, desktop, a 2033 px stroke, drawn again / reflected, ms** (the Manta about 20 times these), mirrors through
  the centre (three: the kaleidoscope): Fineliner 6 px 5/1.7, 13/3.1, 22/5.0, 27/4.8; Brush pen 30 px 6/1.9, 17/3.9,
  24/6.0, 33/6.7; Watercolor 120 px 14/7.9, 35/11.5, 52/16.2, 67/17.4. So a kaleidoscope's pen lifts in about 100 ms on
  the Manta (was 450), its 120 px wash in about 330 (was 1000). `MirrorChecks.timings` bounds the kaleidoscope's
  reflected lift at 60 ms for pens and 150 for the wash.
- **Rendered apart** (`MirrorStroke.areas`, `DrawingPad.renderDirty(also, apart)`): the reflected copies' boxes and
  the stroke's last part are rendered and refreshed each apart, not as one box round them all (a kaleidoscope's round
  the page's middle); a short stroke far out renders a tenth of the area. Only while they hold everything changed:
  anything past their box renders the one box as before.
- **Live copies** (`MirrorShow.take`): a copy looks only where the change's 16 px blocks that hold a mark land, and
  asks to render only the box it lit; the budget (`LIVE_PX`) counts what it looks at. A pen's frame now shows every
  copy (the cap's 23 too, where four of a kaleidoscope's five showed for a 200 px box before), at about the same cost.
- **Real Mirror** is unchanged: under the pen a frame costs 0.4 to 1.3 ms here with one to four mirrors, a whole page
  about 20 ms.
- **Show Guides** (`Mirrors.guides`, `ToneDocument.showGuides`, `DrawLayout.MirrorRow.GUIDES`): the dashes shown or
  not, the mirrors acting all the same; per page, in its MIRR section beside on or off (no Undo step, kept by Undo and
  Remove Mirrors). A page with its guides showing writes the section as before (version 1, byte for byte); hidden, it
  writes version 2 with the flag, which the version before skips (the page opens there without mirrors).
- **Remove Mirrors**: Clear Mirrors' new label (the row's key stays `CLEAR`); with Copy Strokes it keeps the drawing,
  with Real Mirror the page shows as it really is again, beside Keep Reflection, which lays what is seen.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +180 −2 | `Darkening`, `darkeningSince`, `layAgain` (a gesture's change as each pixel's factor, laid again under an affine map); `showGuides`; `StackState.restore` keeps the guides' setting as on or off. Generic: any stroke's change laid again elsewhere. |
| `DrawingPad` | +20 −3 | `renderDirty(also, apart)`: a finished `MirrorStroke`'s areas rendered apart. |

#### Fill in mirrors (2026-10-20, branch `inkwash-fillmirror`): no upstream files touched

The plan's note under Mirrors made real: a Fill tap with mirrors on fills as the mirrors treat a stroke, one Undo step.
Scribulor's own, pure: `FillTool.Areas` (where a tap fills, area by area), `FillTool.lay`, `FillTool.seen`,
`FillTool.keptSide`; `MirrorStroke`'s fill mode; `InkWash.fill` through them. `FillRegion` is untouched. Checked:
`FillMirrorChecks`.

- **Copy Strokes**: the area at the tap and at each of its images (`Mirrors.Group.points`), each a fill of its own on
  the page as the last left it, byte for byte the fills tapped one after another without mirrors (a stroke's copies are
  likewise each laid on the page as the last left it). The tap's area is found and laid at once, on the UI thread as
  before; the images' at lift, one a look (`MirrorStroke(document, primary, Copies)`: each `Copies.start` finds the next
  area and starts its fill, null when none is left), so `QuickLift` shows them in turn and `ToneDocument.linkSince`
  makes them one Undo step. Passed by: an image off the page, on a line (a pixel darker than `FillRegion.LINE`), or in
  an area already found, so an area that holds several images (a box across the mirror, a ring round a kaleidoscope's
  centre) is found once and filled once. A tap on open paper still fills its images' areas; "No closed area" only when
  none has one. Not reflected as a stroke's change is (`layAgain`): an image's area needn't be the tap's reflected.
- **Real Mirror**: no copies, as for a stroke; the tap folds into the kept side (`View.fold`, as a stroke's samples do)
  and its area is found in the page as shown: the walls are the kept side's ink through the view (`FillTool.seen`, a
  block's source pixels read as one box round them), so a shape the mirror closes (lines running up to it) holds the
  fill, and marks hidden under the mirror don't. The area is kept to the kept side (`FillTool.keptSide`; a box whose
  corners are all kept is left as it is), which the view shows on every side. Before, a tap on a covered side filled the
  hidden page there.
- **Timing** (`FillMirrorChecks.timing`, a 1404 × 1872 page, desktop): a kaleidoscope's six rings of radius 120 px in
  28 ms, one ring 5 ms, so each image costs what a tap there would (the Manta about 20×, spread over the lift's looks);
  bounded at six and a half rings' time.

#### The Tools page in four sections (2026-10-19, branch `inkwash-toolspage`): upstream files touched

The owner: "the slots should be the main focus... move the test area to the top and the current brush below that, then
the slots, then the library display. each section should be labeled clearly.. Remove "roll another". lets use the
toolbar stroke look for all of the previews. to show that a slot is active, have a line around it. it can be blinking
slowly?"; then "back button should be clearly visible.. sometimes it gets lost". Scribulor's own: `ToolsLayout` (the four
sections and their headers), `ToolsActivity` (the order, the headers, the tray previews, the blink), `ToolsPage` (no
Roll another; `slotCaption`), `SlotRow.cards` (the Tools page's six slots as cards), `LoopCard` (a name first in the
caption, wrapped; the outline's colour), `LabLibrary.traySampler`, `LabUi.header`, `LabButton.back` and
`LabLayout.backWidth` (Back with its word, on Brush Lab's bar too; Brush Lab's Save is its icon alone on the Nomad to
make room). Engine untouched.

| File | Lines | Why |
|---|---|---|
| `SelectionFeedback` | +23 −8 | `update(View, Rect[], Runnable)` and a `fast` flag on `present`: the selected slot's outline fades through the panel's partial update on its four sides only, in the grey mode, so no pen-plane ink is left behind when the slot is no longer selected. |

#### Fading and Lemon Juice (2026-10-19, branch `inkwash-fade`): upstream files touched

Spec `docs/archive/superpowers/specs/2026-10-19-mischief-modules-design.md`, Stage 3 (`mischief-lab.html`'s section 3). Engine
(Scribulor's own, pure): `PartChoice.FADE` and `LEMON` (Drying 5 and 6) with `PartDial.FADE_IN` and `DRY_IN`; `FadingInk`
(new: a mark's shares by the clock, the mottle, the e-ink steps, the file's section); `ResolvedBrush` (`fade`,
`asQuick`); `ModularStroke` (its Undo steps become the mark at `finish`; not `laysAgain`, so mirrored copies are strokes
that fade as their own); `ModularStyle` (`rollFade`, `fadeRolls`, `lemonRolls`); `ModularFeatures.seedOnly`;
`ModularWords`; `SurpriseTools.fancy`; `names/traits.txt` (fade, juice). UI (Scribulor's own): `DryingTicker` steps the
page's marks when due, each on its own partial refresh, beside a settling wash and never while the page is wet; nothing
is posted without them.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +204 −7 | `fadeSince` (the Undo steps since a mark become a `FadingInk.Mark`: its tiles before and as laid), `fade(now, shown)` and `stepFade` (each pixel still showing what the mark last showed shows its share now, and so does every Undo and Redo step's copy of it on that layer, so both give it as the clock has it; a step left changing nothing leaves the history, its link passed on), `rewet`, `fading`, `nextFade`, `fades`, `placeFades`, `Snapshot.fades`; `own(layer)` out of `writable` (any layer's arrays copied from a snapshot before a write). A page without marks writes, draws and undoes exactly as before. Generic: any mark that changes by the clock. |
| `DocumentCodec` | +24 −6 | TSM5's `FADE` section (the live marks, `FadingInk.encode`), written only while some; a reader without it opens the page as it showed. |
| `DrawingPad` | +13 −1 | `fadeToNow`: a page shown (turned to, a drawing opened) steps its marks to the clock before it is drawn, and starts the ticker; `pauseDrying`. |
| `PaintActivity` | +1 −1 | `onPause` stops the ticker (`pauseDrying`); `onResume`'s `resumeDrying` starts it again. |

## Notebook options (2026-10-19, branch `inkwash-nbopts`)

A template's guides under each page, a per-notebook toolbar and the options sheet
(`docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md`). The engine is untouched; the upstream files below take small,
generic additions, and a page or notebook with no options writes, draws and composes exactly as before.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +40 −4 | `guides`/`placeGuides` (a template's guides, a tone a pixel; no Undo step, the page dirty), `guided(guides, paper)` (the darker of guide and shade, one made and kept), `pixels()` (a cover's paper pixels, else the guides on the shade) in place of `paperPixels` in `paperAt` and `visibleLayers`, `Snapshot.guides`/`shownPaper()`. `paperPixels()` stays cover-only (Fill Page is still offered on a guided page); the glaze rule is unchanged. Pages without guides write, draw and compose as before. Generic: a printed ground under any page. |
| `PagePicture`, `ViewportBitmap` | +2, +2 −1 | `Composite.printed` (the copies' composite on the guided paper); the screen's compose lays `shownPaper()` under the layers. A page's picture stays marks and paper, with no guides. |
| `ToolSlots`, `ToolSwatches` | +85, +15 | `Toolbar` (the eight slots with their sizes and greys, as lines), `toolbar`/`load`/`enter`/`leave`/`appWide` (a notebook's own toolbar loaded over the app's, which waits under `tool_appwide`), `keepLook`. The slots, library and recent brushes are unchanged for a notebook with none. |
| `DrawingBook`, `BookCodec`, `BookPictures`, `DocumentStore`, `RecoveryCodec` | +60 | The notebook's `NotebookOptions` (kept as `TMPL`, `GPDF`, `TOOL` records of the options block, only what isn't the default; a recovery section; the shelf's reading), guides placed on each page decoded, `Snapshot.printed` for the copies. |


## Stamp (2026-10-19, branch `inkwash-stamp`)

A Tip whose nib is a piece of the page cut with a lasso (`docs/archive/brush-lab/prototypes/mischief-lab-2.html`, section 2). Engine
(Scribulor's own, pure): new `StampNib` (the cut, trimmed; the nib store over `BrushLibrary.Store`, keyed by a hash of the
nib's pixels) and `StampStamper` (the prints, through the stroke's own buffers and compose, so every medium, Print
Shading, Erasable and drying lay them); `PartChoice.STAMP` (Tip choice 10), `PartDial.STAMP_SPACING` and `INK_FADE`;
`ModularBrush.nib`, which `ModularCode` writes as a seventh segment only for a Stamp with a nib (every other code byte
for byte); `ResolvedBrush.Pattern` holds a Stamp as it holds Stipple and Hatch; `HeldPaint` keeps each holder's ink;
`MirrorShow.Place` gains the lasso (marching ants), which `StrokePanel` sends through the fast update; `BrushBackup`
keeps nibs beside the library's file. `LabLayout.swapColumns` gives three rows when two would make a tile too narrow
for a tip's name (the Nomad's eleven Tip tiles in Basic).

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | +25 −12 | `Placing.CUT` and `cutting`: the pen's next stroke is the lasso (the placing stroke, dashed), shown through the fast update; its end goes to `PaintActivity.stampCut`. `drawShow`, the mirrors' overlay drawing made static so Brush Lab's test page draws its lasso the same way. |
| `PaintActivity` | +26 | `cutStamp` (from the Tools page's Cut Stamp) and `stampCut`: the cut nib kept and the slot's brush given it, or "Nothing to cut" beside the slot. |

## Crayon (2026-10-20, branch `inkwash-crayon`)

A Medium of wax on toothy paper (`docs/archive/brush-lab/prototypes/mischief-lab-2.html`, section 4). Engine (Scribulor's own,
pure): new `Crayon` (the page-fixed tooth and ragged edge, the line's press, the wax over what is there, its share of
the masking fluid, Scratch's blade); `PartChoice.CRAYON` (Medium choice 7), `PartDial.WAX` and `SCRATCH`;
`ResolvedBrush.Rule.CRAYON`, `wax`, `scratch`, `waxes()`, `stick()` (the stick test `QuickStroke` and `ModularCompose`
shared), `Pen.crayon`; `LineStamper` lays a crayon's ragged edge and press; `ModularCompose.ink` its wax, and
`waxOnMask` composes it onto the masking fluid as `layWax` already does for a wax resist (`ModularStroke`, one Undo
step). Scratch is the crayon as a thin blade laid with Blend's White. `StickGrain.tileNoise` is shared. Every older code
is byte for byte; no roll picks it.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +9 −1 | `takesOnWax`: for the gesture, only the masking fluid the eraser can lift shields its paint, so a crayon builds up over its own wax (kept fluid) and Scratch cuts through it. Off at every `begin`. Generic: any paint that takes on wax. |
## Spiro (2026-10-20, branch `inkwash-spiro`)

A Tip drawing the spirograph toy's curves (`docs/archive/brush-lab/prototypes/mischief-lab-2.html`, section 3). Engine
(Scribulor's own, pure): new `SpiroStamper` (both modes, every curve, a fine antialiased line through the stroke's own
buffers and compose, so every medium, Print Shading, Erasable and drying lay it); `PartChoice.SPIRO` (Tip choice 11) and
its dials (`SPIRO_MODE`, `CURVE`, `LOOPS`, `TEETH`, `WHEEL`, `HOLE`, `PETALS`, `RATIO`, `PHASE`, `FADES`); the hand's last
three dials, `P_DRIVE`, `S_DRIVE`, `T_DRIVE` (`ModularCode.ADDED`: written only when changed, so every other code is
byte for byte; kept when a Control preset is swapped); `ResolvedBrush.Pattern` holds a Spiro as it holds a Stamp;
`ModularStroke.stampNew` (split from `add`) stamps again at the lift for a Spiro's ring; `QuickStroke` never sends a
Spiro quick (its preview would follow the pen, not the ring). Scribulor UI only: `BrushLab.dials` hides the dials a curve
doesn't use, `LabSettings` adds the drives to the Control card for a Spiro (`LabLayout.handCell(rows)`, `handRow`). No
upstream file touched.

## Spiro's ring guide (2026-10-20, branch `inkwash-spiroguide`)

In Ring, the pinned ring as a light grey outline over the page from the touch-down to the lift, never in the page
(mischief-lab-2.html's guide, minimal: no wheel, no tap animation). Engine (pure): `SpiroStamper.guide` (the ring's
radius at the touch-down's pressure and tilt) behind `ModularStroke.ringGuide`. The outline is `MirrorShow.ring`, an
overlay drawn once into the same tiles as Copy Strokes' live copies and the placing stroke, rendered with the page's
dirty areas and taken away by the `MirrorShow.end()` every lift already calls; PNG, PDF and Undo never see it. Brush
Lab's test page draws it through its own `MirrorShow` (`BrushTestPad`, Scribulor's).

| File | Lines | Why |
|---|---|---|
| `DrawingStroke` | +6 | Default `ringGuide(pressure, tiltX, tiltY)`, 0: only a Spiro in Ring answers (`MirrorStroke` and `QuickStroke` pass it on). |
| `DrawingPad` | +37 | `ringGuide`: after a pen's or finger's first sample, a Spiro's ring into `mirrorShow` round `samplePoint`, shown at once through a fast session of its own (`ringDirect`, `StrokePanel.open`, as the lasso's), which the stroke's gray updates leave be; `endStrokePreview` ends it at the lift as a fast preview (`endFast`: the driver drives its pixels again); `disconnectDisplay` closes it. |

Brush Lab's test page (`BrushTestPad`, Scribulor's) does the same with `ringFast`.

## Spiro's ring pull (2026-10-23, branch `spiro-ring`)

In Ring the pen's first move out from the pin sizes the ring (the owner: "could it be possible to set the size of the
circle at the beginning somehow?"). Engine (pure): `SpiroStamper.Pull` follows the samples (the farthest distance once
past `PIN`, until the pen has gone `PULL_TURN`, 25°, round or come back in by `REV`); the stamper inks nothing while it
lasts, then sets the ring's radius from it (`ringShare`: the toy's ring, the epitrochoid's inside its wheel) and starts
the wheel the way the pull went. A tap, and a move that never leaves the pin, still draw the whole figure at the
brush's size; Line is unchanged. The guide is no longer drawn into `MirrorShow`'s overlay tiles: `MirrorShow` keeps the
ring (centre, radius, its own `Pull` fed by `ringTo`) and draws it dashed per pixel, asking to render again only boxes
round its arcs as it grows. Web twins: `newmods.js` (`spiroPull`, `spiroShare`), `ui.js` (the guide follows, dashed),
`voice.js`, `modular-lab.html` patched, studio rebuilt.

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | +12 −2 | `sampleEvent` and `fingerSample` feed each sample to `mirrorShow.ringTo`; `presentInput` shows a pulled ring through `ringDirect` (`presentRing`, split from `ringGuide`). |

`BrushTestPad` (Scribulor's) does the same in `sample` and `presentRing`.

## Spiro's ring set up as a shape (2026-10-26, branch `spiro-taps`)

Replaces the pull above (the owner: the shape tool's way). On the drawing screen the pen's first stroke with a Spiro in
Ring sets the ring up: its touch-down pins the centre, the drag sizes the ring (the guide's radius the pen's distance,
out and back in; within `PIN` the brush's own), nothing is inked, and the lift sets it, the guide staying. The pen's
next stroke with that tool draws, and goes in with the setup first: the centre, then the rim with no pressure, two and
one `StrokeLog.MOST_PAUSE` before its first sample, so the stroke log replays it unchanged. Engine (pure):
`SpiroStamper.Pull` and `PULL_TURN` are gone; `rim` (the radius a rim sets, 0 within `PIN`) serves the stamper and
`MirrorShow.ringTo`; the stamper takes a second sample of no pressure, `PIN` or more out, as the rim, else the ring is
the brush's size (a finger's stroke, Brush Lab's test page, a stroke logged before). A setup that never leaves the pin
is a tap: the whole figure at the brush's size. Any other stroke, a tool or page change, a panel, or Undo (which takes
back only the setup) drops it. Web twins not changed.

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | +80 −8 | `RingSetup` (a `DrawingStroke` that inks nothing, as `MirrorShow.Place`), `ringSet`, `ringPrefix`, `feed` (split from `sampleEvent`), `tapRing`, `dropRing`; `beginInkStroke` starts or consumes a setup; `finishStroke` keeps its guide. |
| `MirrorShow` | +8 −6 | `ringTo` follows the distance; `beginCopies` keeps a ring set up. |
| `InkWashBar`, `PaintActivity` | +4 | A tool change and Undo drop the setup. |
| `WildSample` | +4 | A Ring's loop tile is a tap: the whole rosette mid-tile. |

`BrushTestPad` no longer follows the pen: its ring is the brush's size, pinned where the pen lands.

Speed (2026-10-26, branch `spiro-speed`): `MirrorShow.ringTo` only moves the radius; `take` queues the ring's arcs, as
last shown and as now, once a render (`ringShown`, `ringArcs`). Before, each sample queued both rings' arcs (some 100
boxes, each rendered on its own), so a batch's samples multiplied the work and a setup's drag left input 20 s behind on
the Manta. No upstream file touched.

Ghosts (2026-10-26, branch `spiro-ghost`): `MirrorShow.end` says whether a ring went. `DrawingPad` (+3 −2): the guide
gone at a figure's lift (`finishStroke`) or dropped (`dropRing`, with `refreshSoon`) marks the page `ghosted`, so the
next auto-refresh is the clean flash; the guide's redraws through the fast session left its old radii on the panel.
## Spiro's oval and bar tracks (2026-10-26, branch `spiro-tracks`)

The owner: "on oval and rectangle etc would be cool". `PartDial.SPIRO_MODE` gains Oval and Bar (values 2 and 3; Line
and Ring, and every code, as they were). Engine (pure): `SpiroStamper.Track`, the toy's oval and bar rings: an ellipse,
or a rectangle with its corners rounded to a quarter of its shorter side, sampled along its line (2048 points: where,
how far along, which way it faces, its angle round the centre). The setup goes corner to corner as a shape's does
(`box`, the stamper's rim sample and the guide both); within the pin, the brush's own size, 0.6 as tall. The pen's
angle round the centre finds the point on the track; the wheel's centre keeps its radius in from it, and its spin is
how far it has rolled less the line's turning (`spin`; the epitrochoid rolls outside). Wheels are sized to the track's
tightest bend as the ring's are to its radius (radius ≤ that bend, so a Bar's corners never stop it), and the track
has a whole 24 of teeth, so a figure closes after the wheel's teeth over their common factor with the track's laps.
The other curves are stretched to the track's box. Ring is byte for byte as before (`ringShare` stays the Ring's).

| File | Lines | Why |
|---|---|---|
| `DrawingStroke` | +2 | Default `ringMode()`, 1: the shape a `ringGuide` takes (`MirrorStroke`, `QuickStroke`, `FilledShape` pass it on; `ModularStroke` answers). |
| `MirrorShow` | +55 −30 | `ring(…, mode)`; the guide is a track (`ringNow`, `ringShown`: centre and halves), `ringTo` follows `SpiroStamper.box`, `arcs` and `ringAt` draw an oval or a Bar's rounded rectangle (`SpiroStamper.edge`); the ring's arithmetic unchanged. |
| `DrawingPad` | +3 −2 | `RingSetup.ringMode`; `ringGuide` passes the mode. |

`BrushTestPad` (Scribulor's) passes the mode too. Web twins not changed.
## Grey Shift (2026-10-20, branch `inkwash-swatches`)

A Control option picking a brush's grey at each touch-down (`docs/archive/brush-lab/prototypes/mischief-lab-2.html`, section 5,
Shifting swatches). Engine (Scribulor's own, pure): new `SwatchPicker` (the rules Random, From a Set and Pattern, In Order
or Ping-Pong; Toolbar's greys, the bar's `ToolSwatches`, or Any, the panel's 16; each palette's set as the greys left
out); the hand's last six dials, `SHIFT`, `SHIFT_ORDER`, `SHIFT_GREYS`, `SHIFT_BAR`, `SHIFT_ANY`, `SHIFT_ANY2`
(`PartDial.SHIFTS`, after a Spiro's drives; `ModularCode.ADDED`: written only when on, so every other code is byte for
byte; kept when a Control preset is swapped); `HeldPaint.shifting` keeps each holder's turn while the app runs;
`ModularWords.plainWords` says it. Scribulor UI only: `InkWash.stroke` and `fill` pick at the touch-down (a toolbar grey
becomes the slot's, `ToolSwatches.chooseGrey`), `InkWashBar.heldChanged` marks it after the stroke (`SwatchBar.show`,
only when it changed); `TestArea` picks for Brush Lab's and the Tools page's test page (`Host.swatches`);
`BrushLab.handRows`, `LabLayout.handRows` and `menu(PartDial, String[])` (a Control row's options beside it or in a
menu), `LabSettings` and the new `GreySet` view (the set's swatches), `BrushLab.toggleGrey`. No upstream file touched.
## Stay in Lines (2026-10-20, branch `inkwash-stayin`)

A brush whose wet paint keeps inside the crisp ink lines (`docs/archive/brush-lab/prototypes/mischief-lab-2.html`, section 1).
Engine (Scribulor's own, pure): `PartChoice.STAY_IN` (Smudge choice 5) and `PartDial.FLOOD`; `ResolvedBrush.stayIn` (no
smudge then); new `StayIn`, the stroke's shape and flood: the shape is Fill's `FillRegion` (a new bounded `find`, its
open cells within 640 page px of pen-down, so Fill's own find is as it was), the flood the prototype's bucket queue at
page resolution, at most 2000 pixels a sample. `ModularStroke` finds the shape at its first sample (off the pen's thread
when it has a session and doesn't draw on the ink layer itself; waited for at the lift), keeps its line, body and relief
to it at every compose as a fill's room (`filled` now tells a fill from it), paints the flood into its body as composed
(Fast-wet's shown body, else the body as laid; laid again with a Slow lift's body), and runs the flood to its end at
the lift. `QuickStroke` never sends it quick. No upstream file touched.

## Gradient (2026-10-20, branch `inkwash-gradient`, on `inkwash-swatches`)

A Control option changing a brush's grey along each stroke, by distance drawn (the owner's "fading color (or gradient
shift)"). Engine (Scribulor's own, pure): new `GreyGradient` (Fade to paper, To Grey to an End Grey of the panel's 16,
Cycle through Grey Shift's set from the stroke's grey, round and round, smoothstep between stops; a Length of whole cm
at `SpiroStamper.PX_CM`); the hand's three dials after Grey Shift's, `GRADIENT`, `GRAD_TO`, `GRAD_LEN`
(`PartDial.GRADIENTS`; `ModularCode.ADDED`: written only when on, every other code byte for byte; kept when a Control
preset is swapped; never rolled). How it reaches the dabs: `ModularStroke` resolves the brush at the darkest grey the
stroke reaches (`GreyGradient.ref`) and each kept sample's share of it goes into `StrokePoints.own` (`apply`, after
`LoadedPaint.Run.spend`, multiplying its share), which every stamper already multiplies its dabs' darkness by (line,
body, placed, hake, bristles, stipple, hatch, stamp, spiro); a wash's share is the ratio of `ModularSizes.washStrength`,
any other mark's the ratio of greys, so the compose (and `compose.h`) is untouched. `QuickStroke`'s preview re-shades
its sweeps a panel grey at a time (`Sweep.shade`, a `ResolvedBrush` per grey it passes, made once). Ink, Wash, Charcoal,
Graphite and Crayon (`GreyGradient.takes`); not Opaque (it covers at its own grey), Resist, Screentone or Float.
`FillTool.brush` turns it off. `WatercolorSession.swatches` carries the toolbar's greys to a Cycle on Toolbar (set by
`InkWash` and `TestArea`). Scribulor UI only: `BrushLab.handRows(b, gradient)` (Gradient, a To Grey's End Grey, Length,
a Cycle's Greys and set when Grey Shift doesn't show them), `LabLayout.handRows` (where there's no room: Gradient's own
rows first, then its row, then Grey Shift's), `ModularWords.plainWords`. `GradientChecks`. No upstream file touched.

## Record and Replay (2026-10-20, branch `inkwash-replay2`): upstream files touched

`mischief-lab-3.html` section 1 (owner: "GREAT, build"). The pen's brush strokes are logged per page and the page can
draw itself again. New, Scribulor's own: `StrokeLog` (pure: the log, its bytes, the player that feeds strokes back a chunk
of pen time at a time), `PageReplay` (the Replay page action: plays on a blank copy of the page, a step per refresh,
then shows the page as it was; any touch ends it), `StrokeLogChecks`. The log is not an Undo step: each entry is tied to
its step by `ToneDocument.Edit.serial`, so Undo and Redo hide and show strokes, a new stroke drops the undone ones, and
Clear page leaves a marker. Not logged: eraser, smudge, fill, shapes, finger, a pen held still (Stipple's Build-up),
mirror copies; a page past 60,000 samples stops logging.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +25 | `log`/`replay()`/`logStroke`/`placeLog`; `Edit.serial`; `Snapshot.log` (the visible strokes); the log is cut where `redo` is cleared; `clearPage` and `clearAllLayers` leave a marker. |
| `DocumentCodec` | +16 −3 | TSM5's `RPLY` section (`StrokeLog.encode`), written only when the page has strokes; an older reader skips it (unknown tag), a log that doesn't read is left out. |
| `DrawingPad` | +10 | Records the pen's samples for an ink stroke (`recording`), logs it at the lift if it changed the page; any touch during a replay stops it. |
| `PaintActivity` | +6 −2 | `replay`, busy during a replay, stopped in `onPause`, a Replay page item in the page menu. |
| `InkWash` | +14 | `recorder` (brush code, size, grey of the stroke just made) and `replay(...)`, the stroke a replay draws. |
## Daily Prompt (2026-10-20, branch `inkwash-prompt`, on `inkwash-v2-lab`)
Owner: "good, build" (Mischief Lab 3, section 3). Engine (Scribulor's own, pure): new `DailyPrompt` (32 cards of our own, the
day number picks today's, `another` picks a different one, `heading`) and a fifth `NotebookTemplate`, `DIARY_PAGE` ("diarypage",
Diary Page: three boxes down the left and a big drawing box, as guides; no labels drawn). Scribulor UI only: one file-menu row,
Daily Prompt (`PaintActivity.fileMenu`, `dailyPrompt`), a dialog with Deal Another; Diary Page shows in the existing Template
list of Notebook Options. No countdown yet. `DailyPromptChecks`. Upstream: `PaintActivity` (a menu row and one method); no file
format change beyond a new template key (an older version reads it as Blank).

### Record and Replay, follow-up (2026-10-20, branch `inkwash-replay3`)

`StrokeLog.added` drops the oldest strokes at `MOST_SAMPLES` instead of refusing the new one (a stroke past the cap alone
is still not kept); undone strokes are already gone by then, so Undo, Redo and Clear page behave as before. Speed chips:
`PageReplay.choose` shows 1×, 2×, 4× through `InkWashBar.chips` (the label's place, level with the eraser) and a tap
calls `start(speed)`, which scales the pen time per step. `PaintActivity`'s page menu item calls `choose`. No other
upstream file touched. Exporting a replay is not built.
### Replay fix (2026-10-25, branch `replay-fix`)

`DrawingPad.onTouchEvent` ended a running replay on any event, a resting palm's moves too, so it stopped as it began.
It now asks `PageReplay.ends` (a press, `StrokeLog.endsReplay`: the pen, or a finger no broader than
`FingerTool.PALM_DP`). `PageReplay.choose(anchor)` shows the speeds through `PaintActivity.menu` beside the menu's
anchor (as Play's Mirrors); `InkWashBar.chips` is gone. `StrokeLogChecks.ends` checks the rule. On the bench app the
replay itself ran and reached the panel (read back through `DirectEink.readGray`); `adb screencap` doesn't show a
direct e-ink present, so screenshots there look unchanged.
### Replay's veil (2026-10-26, branch `replay-blob`)

The owner saw "a random blob (a previous drawing)" during a replay. The log keeps strokes whose marks the eraser or the
Lasso (not logged) has since taken away or moved; the replay laid them again where drawn (the owner's notebook, page 48:
21 filled shapes lassoed elsewhere, 411k px of blobs). `GhostPlane.offMarks` builds an over plane of the paper wherever
the page shows no mark; `PageReplay` keeps it for its copy and `Ghosts.plane` hands it to the pad for that copy (the
slot Tracing's plane takes for the book's page), so the replay shows only what the page holds. `StrokeLogChecks.veil`.
No upstream file touched.
### Daily Prompt follow-up (2026-10-20, branch `inkwash-prompt2`)
`DailyPrompt.clock` and the card's Start/Stop/Time in `PaintActivity.dailyPrompt` (a postDelayed tick of 1 s on the pad; not
`TimedPages`, which locks pages). A started Diary Page card calls `setNotebookOptions` with the Diary Page template (notebook-wide,
guides are paper). `NotebookTemplate.DIARY_PAGE` draws DID, SAW, HEARD with a 3 x 5 dot font. `DailyPromptChecks` extended.
### Six more Print Shading choices (2026-10-20, branch `inkwash-prints`)
`PartDial.SCREEN` gains Rosette, Ben-Day, Crosshatch, Wavy Lines, Mezzotint and Diamond Dots as options 5 to 10 (still one base-36
digit, so old codes read and write as before), and `PartDial.PATTERN` (Screentone) gains Crosshatch, Wavy Lines and Mezzotint as 4 to 6.
`PrintScreen.Kind` takes the new kinds in the same order (the screen kinds before `DOTS`, Tone's after `GRADIENT`, so
`values()[screen - 1]` and `DOTS.ordinal() + pattern` stay right) and `threshold` the six formulas; Screentone's twins share them.
Still a threshold at each page pixel, so the compose, the print tone, the eraser and the quick path are untouched; the native
renderer (`compose.h`) has no print path (the screen is decided in Java before it), so there is nothing to keep equal.
Ben-Day evens its threshold through a table measured at class load. `ModularWords` (names, tags), `BrushLab.active` (Angle greyed only
for Dither and Grain), `ModularStyle.rollScreen` (one Screen roll in `NEW_SCREEN_ODDS` = 6 takes an appended print, own salt, so a
Screen's rate and every other roll are unchanged; Tone's roll still picks the first four patterns). `ScreenChecks` runs every loop over
the ten, plus `newPrints` (coverage, nesting, Angle, difference, Screentone twins, page-fixed). Web lab (`modular-lab-src/newmods.js`,
`modular-lab.html` patched with the same hunks) and Brush Studio (rebuilt) carry the same names, codes, thresholds and roll.

### An eraser that takes one kind of mark (2026-10-20, branch `inkwash-erasekind`)
Spec `docs/archive/superpowers/specs/2026-10-20-erase-kind-design.md`. Upstream files touched: `ToneDocument` (+153 −53: `Layer.kind`/`kinds`/`kindBase`, a third
plane beside `share` and `print` with the same plumbing: `copy`, copy-on-write, `bind`, `expand`, `reflected`, tile captures `beforeKind`/`foundKinds`,
`TileEdit`, `restoreTile`, `restoreSpan`, Undo bytes; `begin(erasable, kinds)`; the two write sinks `tones` and `store` add the gesture's kind bit
(`laid`); `eraseKinds`, `takes`, `overrides` in `eraseTone`/`eraseTones`; `compositeToneKept(…, kinds)`), `DocumentCodec` (`KIND` section, +47), `InkWashLayers.cropped`.
Scribulor-side: `ResolvedBrush.kind` (by Medium), `ModularStroke.begin`, `DryingWater.lay`, `HaloGrowth`, `FloatWater.floats` (the wet continuations
begin with the stroke's kind), `EraseBothStroke` (limited by `kinds`, leaves masking fluid), `EraseKind` (the four choices), `InkWash.eraseKinds`,
`InkWashBar.eraserMenu` (the long press). MonoPaint's tools begin with kind 0 and tag nothing. The native renderer only shows the page: nothing to
keep equal. Checks: `EraseKindChecks` (56) and `OlderKindCheck` (the document classes from `e288fe9` skip the section); `PageShadeChecks` pins the
white page's file crc again (the section is new in it; pixels, picture and PNG unchanged).

### Kind layers: each kind of mark on a plane of its own (2026-10-27, branch `kind-layers`)
Spec `docs/superpowers/specs/2026-10-27-kind-layers-design.md` (step 1, the engine; step 2's show/hide row is UI only). Upstream files
touched: `ToneDocument` (`Layer.planes`, each plane a `Layer`, made lazily by `begin(erasable, kinds)` through `planeOf`; `KIND_OPAQUE`;
`stacked`/`stackSize`/`stackOwner`/`stackOpacity`, the one composite order every composite walks: per pair the bases, then the planes kind
by kind; `beginPlane`, `hasPlane`, `selectKind`/`selectedKinds` and the `selected` read target; `setKindOpacity`; Undo finds a plane's layer
(`ownerOf`, `address`/`at`); `copy`/`release`/`held`/`each` carry planes; `layerDarkness`, `layerTone`, `layerPixels` read a layer's look,
`layerPixels/layerKept(index, kinds, …)` one plane; `sheets`/`flattened`), `DocumentCodec` (`PLNS` section, the look in the layers' part),
`ViewportBitmap` and `viewport_bitmap.c` (43 arrays, was 11), `ShapePreview` and `layers.h` (40), `GhostPlane`, `PagePicture`,
`InkWashLayers` (`merge`, `resized`), `NotebookMigration`, `FadingInk` (a plane's address in `FADE`). Scribulor-side: `ResolvedBrush.medium`
(Cover lays `PAINT|OPAQUE`), `ModularStroke.kinds`/`prepare` (a smudge with no paint of its own works on its Medium's plane), `QuickStroke`
(the preview on the stroke's plane), `EraseBothStroke` (plane by plane, the Pen plane live), `LassoPlanes` (base and four planes a half),
`WetCanvas` (one plane), `WetPaintStroke`, `WatercolorSession.joiningKinds`, `FloatWater` (the bath's first kind), `HaloGrowth`
(`foundComposite(…, kinds, …)`), `CarryPlane` (a plane of the other half). The native renderer composites more arrays, by the same rule.
Checks: `KindLayersChecks` (60: order, exact erase where pen crosses pencil, Undo, an old file byte for byte, a new one's planes, the Lasso,
a stump, kind opacity); `EraseKindChecks` rewritten for planes; checks that read a layer's pixels read its look (`Layer.flattened`,
`layerPixels`) and lay their setups on the plane the brush works on; `PageShadeChecks` pins the white page's file crc again (pixels,
picture and PNG unchanged); `FillChecks`' strip goldens taken again from the fill laid in one piece.

### Export Page: a page's PDF, PNG or Layers (2026-10-28, branch `page-export`)
Upstream files touched: `PaintActivity` (+11, `exportPage`, as `exportNow`). New, Scribulor's own and pure: `PageExport` (one page's copy, named "<notebook> p5.psd") and
`PsdWriter` (a Grayscale PSD of grey layers, PackBits, with its merged image); `PageExportChecks` reads the PSD back.
Scribulor-side: `NotebookExport` (`greys` split out of `png`, unchanged output; `flatten`, and `layers`, `hidden`, `said`,
`stackKey` opened to the package), `NotebookPdf.write(…, from)` (the first page's number; the notebook's PDF unchanged),
`PagePicture.Composite.printed(page, from, to)` (a run of the stack's places alone), `Magnets.stamp` (over a clear
pixel too, for the Magnets layer), `AutoExport.page`, `NotebookScreens` (Page Settings'
foot), `LibraryCard.EXPORT_PAGE`.

### Tracing paper, Stage 1: the page bar's previews (2026-10-20, branch `inkwash-paper1`)
Spec `docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md`, §12 Stage 1. On the drawing screen the page bar's ‹ › go:
`PageTurner` now holds the Notebook button, the page and its count (padlock and blink as before) and a strip of tiny live
previews of the front cover, the pages and the back cover, a window round the page drawn on (`PageTurner.Strip`). Tap goes,
a quick drag riffles (the page's picture over the page area, `PaintActivity.showPanelAt`, nothing decoded; the window slides
one page every 150 ms at an end), a hold then a drag moves the page, a hold released opens its options (stage 1: its
number). Inside a notebook (`NotebookScreens`) the bar keeps ‹ ›. New, Scribulor's own and pure: `StripGesture` (the
thresholds, tiles, landing); `StripGestureChecks`. Scribulor-side: `PageTurner`, `DrawLayout` (preview sizes,
`stripPlaces`, `previewAt`, `previews`; `pageBarUsed`/`pageBarPlaces` went), `NotebookLayout.window` (shared with the
notebook's strip), `NotebookScreens.drawPlace`/`drawPreview` (previews from pictures in the page's dots, made on its
"page pictures" worker; the page drawn on is never copied for one), `PagePicture.dots`, `LayerThumbnail.renderPage` (the
drawing page's preview, sampled after a stroke settles, `InkWashBar.THUMBNAILS_MS`), `InkWashBar.pageChanged`.
`NotebookFileChecks.moved`, `DrawLayoutChecks.pageBar` rewritten.

| File | Lines | Why |
|---|---|---|
| `DrawingBook` | +30 | `move(from, to)`: the packed page, picture and canvas choice move; the decoded pages and the three revision maps are re-keyed (`moved(i, from, to)`, as `shiftPages` does); the cover picture's page and `index` follow; one book edit. Page bytes untouched, so the PNG copies are kept by CRC. Generic. |
| `PaintActivity` | +22 −6 | `movePage` (busy-guarded, `updatePages`, `recoveryNow`); `showPanelAt` split out of `showPagePanel` (a panel at given bounds); `updatePanel`; `closePagePanel(false)` hides it without its own update when a page turn follows. |

### Tracing paper, Stage 2: Tracing ghosts (2026-10-20, branch `inkwash-paper2`)
Spec `docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md`, §12 Stage 2. A notebook's **Paper** (Plain, Tracing; Glass
is read and kept but shows as Plain until stage 3), each page's step and a pinned ghost. In Tracing the page before (or the
ghost) shows Half (44 %) and the page after Faint (18 %) under the page drawn on, in the page's own dots. New, Scribulor's own
and pure: `TracingPaper` (the Paper, ghost, steps; which pages ghost and how strong; the records' bytes) and `GhostPlane`
(the ghosts flattened, a byte a pixel: how much a black layer laid over the paper covers; built on the cores); `TracingChecks`.
Android-side: `Ghosts` (the plane kept by `DrawingBook.ghostKey`, built on its own worker on the quiet cores; a page turn
waits up to 300 ms for it, else it follows in one more full update; a Paper/step/ghost/order change rebuilds 300 ms after the
last tap; never under the pen), `PageTurner` (the Paper button: tap cycles, hold shows the Papers; the options popover's
steps and Ghost; a ghost's grey tab, a Hidden page dimmed), `DrawLayout.STRIP_BUTTONS`/`GHOST_TAB`, `PaintActivity.setTracing`
(a book edit, free under the lock and the rules), `ic_ph_file`. The ghosts are the screen's alone: the page, its file, PNG/PDF,
`PagePicture`, Fill, the eyedropper and Replay (another document shown, so no plane) never see them. Measured on this Mac at
1920 × 2560: the Tracing plane 53 ms (decoding the page after is most of it); the Manta's time is for the device.

| File | Lines | Why |
|---|---|---|
| `DrawingBook` | +60 | `paper`, `ghost`, `steps` (kept beside `pictures`; moved, copied and removed with their pages; `ghost` follows its page as `cover` does), `tracing()`/`setTracing` (one book edit)/`restoreTracing`, `ghostKey`, `ghostSources`, `contentKey` (shared with the cover's `paperKey`); `Snapshot.tracing`. |
| `BookOptions`, `BookCodec` | +20, +2 | `PAPM`, `GHST`, `STEP` records, each only when not the default (a Plain notebook writes byte for byte as before); `Covers.tracing`. Older versions skip them and open the notebook Plain (`OlderOptionsCheck` against af06002). |
| `RecoveryCodec` | +17 | A trailing `PAPER` section (TSRP) after RULES, only when not the default; older readers stop before it. |
| `ToneDocument`, `ViewportBitmap` | +7, +6 | `visibleLayers(…, ghost)`: the plane as a black layer right over the paper (guides and a cover's paper under it); the area compose takes it. Arrays one longer. |
| `viewport_bitmap.c` | +4 −4 | `nativeCompose` takes 10 layers (8, the paper pixels, the ghost). |
| `DrawingPad`, `MirrorShow` | +20, +4 | `ghost` (taken at `showPage`, cleared by `replace`), `showGhost`; the eraser's preview and Real Mirror's reflection on the ghost (`GhostPlane.over`). |
### Tracing paper, Stage 3b: layers are pages (2026-10-21, branch `inkwash-layerpages`)
Spec `docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md`, "Stage 3b". `StackPanel`/`StackLayout` become the
Layers panel (a row a page: ≡, preview, eye, hold menu; New Layer at the foot); `StepIcon`, `InkWashLayers.movedFrom`/
`marked` and `ToneDocument.layerCovers` are deleted. New: `InkWashLayers.merge` (every pair, hidden ones too, flattened
into one ink layer), `TracingPaper.strength`/`withStrength` and the `PSTR` record (`BookOptions`) and `TSRS` recovery
section (`RecoveryCodec`), Notebook Options' Layers Show Through row (`NotebookLayout`'s chips 32 dp and gap 3 dp so eight
rows fit the Nomad turned), `PaintActivity.newLayer`/`duplicateLayer`/`deleteLayer`/`mergeLayers`. Fix:
`NotebookScreens.drawPreview` keys its cache by size, so the strip and the panel stop remaking each other's pictures.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +8 −2 | `replaceLayers` (Merge Old Layers as one structural undo step, as `restack`); `layerCovers` gone. |
| `DrawingBook` | +3 −3 | Tracing paper's default strength kept beside the Paper. |
| `RecoveryCodec` | +7 −3 | The `TSRS` section: the default strength when Faint; older readers stop before it. |

### Tracing paper, Stage 3: Glass, the Stack panel, layer steps (2026-10-20, branch `inkwash-stack`)
Spec `docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md`, §12 Stage 3. **Glass**: every other page in stack order under
the page drawn on, each at its step (Half unset), Hidden left out, flattened into the same one plane (`GhostPlane`, a page at
a time from its bytes). **Layer steps**: Half and Faint per pair, view-only bits beside the page step (`STEP`'s second byte,
already written and validated by stage 2); Hidden stays the layer's own undoable visible flag. On the drawing page each pair
but the one drawn on shows at its step; on a ghost page, page × layer. New, Scribulor's own: `StackLayout` (pure: sizes, rows,
the window, words; `StackLayoutChecks`), `StackPanel` (the "Pages and Layers" panel above the page bar: page and layer
rows, ≡ drag, steps, ✎, New Layer, a layer's hold menu, Paper chips), `StepIcon` (a step as a grey swatch), `ic_ghost`,
`ic_ph_stack_simple`; `LabButton.iconChip` (an icon chip whose word is its content description and its hold's tooltip).
`InkWashLayers.moveTo` (any place, one `restack`, one undo step), `movedFrom`, `marked`; `TracingPaper` Glass, `shownStep`,
`layerBits`/`layerStep`/`withLayerStep`/`withLayers` (steps follow a pair moved, added or deleted in the panel);
`PaintActivity.layerEdit` (shared by the panel and Clear page), `layerSteps`. `InkWashBar`'s layer column (`layerGroup`,
`folded`, `sizeLayers`, `LayerThumb`, the layer panel) and `DrawLayout.LAYERS`/`Item.THUMB`/`collapsed`/the layer count are
deleted; `DrawLayout(width, height)`. MonoPaint's `LayersPanel` stays untouched: only its `Toolbar` rail reaches it, never
shown in Scribulor (`showInkWashBar`). Measured on this Mac (`NotebookBench`, 1920 × 2400 pages from their bytes): the Glass plane 717 ms for 20
pages, 3.7 s for 100 (Tracing 74 ms). At the Manta's usual ×20 that misses the spec's 1 s for 20 pages, so the per-page
flat cache (§4) is the next step, for the owner to approve; until then a Glass page shows first and its plane follows.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +12 −2 | `visibleLayers(…, ghost, layerSteps)`: each pair's opacity times its step but the pair drawn on's; `layerCovers` (Delete Layer under No Erasing). |
| `ViewportBitmap` | +2 −2 | The area compose passes the layer steps. |
| `DrawingBook` | +4 −2 | `layerSteps(page)`; a ghost's layer bits in `ghostKey` and its `GhostPlane.Source`. |
| `DrawingPad` | +14 −4 | `layerSteps` (taken at `showPage`, cleared by `replace`), `showSteps`; `showGhost` and it share `showAgain`. |
### Rubbing a cover's design off (2026-10-20, branch `inkwash-covererase`)
Spec `docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md`, "Rubbing the design off". The Everything eraser takes a
notebook cover's printed pattern where it goes; the tape, label and cover picture stay. New, Scribulor's own and pure:
`CoverWear` (where it is rubbed, a bit a pixel; `with`, `scaled`, `apply`, `encode`/`decode`); `CoverWearChecks`, and
`OlderWearCheck` (the classes from `ca133f7` skip the section and the picture's tail). Scribulor-side: `NotebookCovers.paper(…,
bare)` (white under the tape, picture and label), `NotebookCovers.once` (the cover picture read once), `shown` rubs the
tile, `NotebookExport.cover` and `DrawingBook.placePaper` hand the bare paper's maker over. A page that isn't a cover, and
a cover never rubbed, writes, draws, composes and undoes exactly as before.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +57 −11 | `wear`/`placeWear`, `placePaper(pixels, bare)` (the bare paper made when first needed), `rubs`, `rubDesign` (an Undo step joined to the stroke's, only its area redrawn), `repaper` (`paperPixels` is the made paper with the bare one where rubbed, a new array each time), `StackState.wear` (Undo, Redo; `Edit` counts its bytes), `Snapshot.wear`, `unmarked` (a rubbed cover is not), `clearPage` (brings the design back), `compositeToneKept` (the Everything eraser's preview on the bare paper). Generic: a ground under the layers that an eraser can wear. |
| `DocumentCodec` | +19 −5 | TSM5's `WORN` section, written only when rubbed; a reader without it skips it. |
| `EraseBothStroke` | +6 −1 | `finish`: with no kinds, on a page that `rubs`, the stroke's coverage rubs the design (after the layer is restored, so Undo restores it too). |
| `PagePicture` | +22 −10 | A cover's marks picture keeps the wear at its size, after its deflated greys (`decode` finds where they end); `white` is false when rubbed. |

### One grey strip in the side bar (2026-10-21, branch `inkwash-greystrip`)
The owner's request: the side bar's four grey faces become one thin strip beside the four sizes. All Scribulor's own, no
upstream file touched. New: `GreyStrip` (the view: `ShadeStrip`'s pixels stood up, four marks, the one in use bolder; a
tap snaps to a mark within `ShadeStrip.SNAP_MM`, else sets the nearest mark's grey; a long press opens Sizes and Greys;
a rub loads), `GreyRub` (the rub-to-load gesture, moved out of `SwatchFace` so the face and the strip share it).
`ShadeStrip.nearest`, `SNAP_MM`, `SIDE_WIDTH`/`SIDE_REACH` (pure), checked in `GreyBarChecks.sideStrip` on the four
screens. `SwatchBar` lays out the sizes' column and the strip (same widths and heights as the pairs, so `DrawLayout` is
unchanged) and redraws only when what they show changes; `SwatchFaces(…, sizesOnly, …)` gives it no grey faces. Same
storage (`ToolSwatches.chooseGrey`/`setGrey`). `InkWashBar` unchanged: `SwatchBar.grey(i)` is the strip.

### Bar pass: Undo and Redo in a row, Zoom on the page bar, eyes for steps, Phosphor icons (2026-10-21, branch `inkwash-barpass`)
The owner's requests; all Scribulor's own, no upstream engine file touched. `DrawLayout`: the column loses `LAB`, `REDO`
and `ZOOM` (`UNDO` is undo and redo side by side, each `half()` wide), one grey rule, Fun the only extra (`zoom`,
`zoomLock`, `ZOOM_ROOM` gone); the page bar's parts are Notebook, Layers, Zoom (`zoomButton()`, its padlock beside
800% in captions), the number and the previews (`stripWidths`, `stripPlaces`). `PageTurner` owns the `ZoomButton`
(same `ZoomButtonTouch`; `InkWashBar.zoom` is it) and loses the Paper button and its hold (`PAPER_SAID`,
`PAPER_ICONS`, `papers`); `STEP_ICONS` (Phosphor eye Fill, Duotone, Light, eye-closed) are used by the strip's page
options, `StackPanel`'s eye and Notebook Options' Faint chip. The file menu's Zoom row (`PinchPan.row`) is gone: the
button is always on the bar. Brush Lab opens from the Tools page only. `SwatchFace.pair`'s bar is at the face's
left; `GreyStrip` marks the grey in use with a dot right of the strip (`ShadeStrip.SIDE_DOT`, `sideStrip`, checked in
`GreyBarChecks`). Drawables: MonoPaint's own icons replaced by Phosphor Bold ones (`ic_ph_*`) where Phosphor has the
same thing, and unused ones deleted; brush, tip, smudge, part, gradient-fill, oval and cover-picture drawings kept.
### A crisp page and the library card inside a notebook (2026-10-21, branch `inkwash-libcard`)
The owner's notebook-screen design pass (option C, "Library card"; section 1, "From the page, at screen size"). All
Scribulor's own, no upstream file touched. `PagePicture.shown(page, tiles, w, h)`: the page box-averaged straight to its box's
size through `Composite.printed` (the guides under the marks), the private `of` given the size; `NotebookScreens` makes it
off the UI thread for the page shown big and its neighbours (`crisps`, an LRU of about three, emptied on leaving the
screen), draws it 1:1 and shows the small picture until it is ready; `mask(PagePicture)` is shared with the small
pictures' masks. New pure `LibraryCard` (rows, values, defaults, which taps step, layout sizes); `NotebookScreens` draws it
(the page's box when `viewing == CARD`, a tile after the front cover in the strip, never a `DrawingBook` place) and
routes taps to the existing setters (`setNotebookOptions`, `setTracing`, `setCoverDesign`) and long presses to
`notebookOptions` / `chooseCoverDesign`. No file records. `NotebookLayout.DRAW` is `OPEN` ("Open"); a tap on the strip's
selected preview opens it. Checked in `CrispPageChecks` and `LibraryCardChecks`. `PageTurner` unchanged: on the card it is
shown the front cover.

### Templates, Random per page, Brushes, editable prompts, the sheet's design pass (2026-10-21, branch `inkwash-notebookopts`)
All Scribulor's own; no upstream file touched beyond `DrawingBook` and `BookCodec`, which already carry Scribulor's notebook
fields. `NotebookTemplate` is a class now (keys unchanged; a key it doesn't know is Blank, as before): nine more drawn
templates, own templates keyed `my:<file name>` read from Supernote's MyStyle through `NotebookTemplate.images` (set by
`PaintActivity`: `BitmapFactory`, scaled to the page; `tones` turns ARGB into the panel's greys), and Random keyed
`random[/key...]`. `shared` keeps the last four guides made (a Random notebook's pages take turns), an own template's only
while its file is there. `DrawingBook` keeps a template key a page beside the tracing steps (`templates`: moved, copied and
deleted with its page, filled under Random by `dice`; `template(i)`, `restorePageTemplates`, `Snapshot.pageTemplates`),
written as the options block's `PTPL` record and the recovery's `TSRT` section, each only when a page has one;
`NotebookExport.update` takes the keys so a page's PNG is keyed and drawn by its own template. `NotebookOptions.oneBrush`
is gone: `brushes` (record `BRSH`; `ONEB` is still read, as a Library toolbar of the one brush with Brushes 1) and
`Tools.EACH_PAGE` (`ToolSlots.forPage` rolls a page's own Surprise Me, seeded by the first page's lines and the page
number; `PaintActivity.syncToolbar` on every page change). `DailyPrompt` reads `Scribulor prompts.txt` beside the notebooks
(`DrawingStorage.shared()`), writing `DEFAULTS` when it is missing or holds no card. `NotebookOptionsSheet` and its sizes in
`NotebookLayout` redone: sections, a label column, one line of chips a row. Checked in `TemplateChecks` (new),
`NotebookRulesChecks`, `NotebookOptionsChecks`, `LibraryCardChecks`, `NotebookNamesChecks` and `DailyPromptChecks`.
Older versions skip `PTPL` and `BRSH` and read Random and own templates as Blank, Each Page as My Toolbar.

### The stamp tool (2026-10-21, branch `inkwash-stamptool`)
All Scribulor's own; no upstream file touched beyond `DrawingPad` and `PaintActivity` (comments only: `cutting` and
`cutStamp` take the stamp tool's index as they take a slot's). No new engine: the tool holds a Stamp-tipped brush drawn by
`StampStamper`, and Cut Stamp is the drawing screen's existing lasso (`DrawingPad.Placing.CUT`, `PaintActivity.stampCut`,
`StampNib.cut`, the one cut Brush Lab's test page also calls). `ToolSlots.STAMP` (after the smudge tool; `TOOLS` is 11),
`STAMP_BRUSH` (`tool_stamp`), `STAMP_KEY`, `stamp()`, `firstStamp()` (the first slot's Fineliner with a Stamp tip): `brush`,
`key`, `set` and `change` take it as a slot, so `use` and Undo work unchanged; it is outside the notebook `Toolbar` and a
frozen toolbar, like the smudge tool. `NotebookRules.refuses` and `smudgeOff` treat it as the smudge tool (off under
Brushes). `DrawLayout.GRID_ROWS` is 3 (the stamp tool alone in the third row) and `greys` includes it. `InkWashBar`: the
row, `ic_ph_stamp` (Phosphor Bold), and the long press's `stampMenu` (a `LoopCard`, Cut Stamp, chips for `HEAD_TURN`,
`STAMP_SPACING`, `INK_FADE` in `PartChoice.STAMP`'s labels and `PartDial.shown`). Checked in `ToolSlotsChecks`,
`NotebookRulesChecks` and `DrawLayoutChecks`.

### The library card as the one settings place; Fun beside the stamp tool; Random per page (2026-10-21, branch `inkwash-cardonly`)
`NotebookOptionsSheet` and `CoverChooser` are gone, with `NotebookLayout`'s sheet and chooser sizes, `NamePrompt`'s Options
button (`More`, `roomForMore`), `NotebookNames.field(options)` / `suggestion(i, options)` / `follow`, `DocumentStore.options`
and `PaintActivity.notebookOptions` / `chooseCoverDesign` / `toolsAfterOpen`. Settings change only on the open notebook, so
nothing rewrites a closed notebook's file for them. New Android `CardSelector` (a row's own screen over the notebook
screen, its pictures on `NotebookScreens`' worker; grid from the pure `NotebookLayout.pick`); pure `LibraryCard` now says
what a tap does (`tap`: toggle, choose, show, fixed), `toggled`, each screen's `choices` / `chosen`, `showThrough`, and its
notes. `NotebookScreens.showCard`; `tapCard` routes to toggles, `CardSelector` or a note. Upstream files touched:
`PaintActivity` (`chooseTools`, moved from the sheet; `showSettings` and `leaveDrawing`; `openNotebook(path, card)`; the
file menu's and the shelf menu's Settings row in place of Cover Design and Notebook Options; `newNotebook` opens the card;
`describeName` says a new notebook's options in its generated name at the first save, `NotebookNames.named`; the menu's Fun
row gone), `DrawingBook` (`newTemplate(at)` avoids its neighbours' templates through `NotebookTemplate.pick(dice, beside...)`)
and `DocumentStore` (one method less). `DrawLayout`: Fun in the stamp tool's row, so `Part.FUN`, `fun`, the extras
(`EXTRA_ICONS`, `EXTRA_RULES`, `extras()`, `count(extras)`, `rules(extras)`) are gone. Checked in `LibraryCardChecks`,
`TemplateChecks`, `DrawLayoutChecks`, `NotebookRulesChecks`, `NotebookNamesChecks`.

### Timed Pages in seconds, its countdown; Brushes and Timed Pages as sliders; a page's prompt; Open Notebook (2026-10-21, branch `inkwash-timerpass`)
`NotebookOptions.minutes` / `MINUTES` / `withMinutes` are now `seconds` / `SECONDS` (Off, 30 s steps to 5 min, minutes to
an hour) / `withSeconds` (snapped to the nearest stop). `BookOptions` adds `TSEC` (`TIMED_SECONDS`: two bytes), read over
`TIMD`, which is still written as the nearest old minute choice (`minutesBytes`) for builds before; `readMinutes` reads any
1 to 60. `RecoveryCodec`'s rules keep the two-byte seconds in TIMD's slot (`readMinutes` then `readSeconds`, each by its
length). `TimedPages` and `NotebookRules` (blink window, `timeLabel` in place of `minutesLabel`) take seconds;
`NotebookRules.countdown` / `nextCountdown` give the side bar's m:ss and its next change, which `TimedPages.nextLook` now
includes. `DrawLayout.Part.TIMER` (24 dp, `DrawLayout.TIMER`, kept on every screen) heads the column; `InkWashBar.countdown`
updates it through `SelectionFeedback` and waits for the pen, as the page bar's blink does. A page's Daily Prompt card is a
page section, `PRMT` in `DocumentCodec` (UTF-8 `DailyPrompt.line`, skipped by readers before it), held by
`ToneDocument.prompt` / `setPrompt` (a change to save, not Undo; a page with one isn't `unmarked`). `LibraryCard.slides`
picks `CardSelector`'s slider screen (the existing `BandSlider`, set on Back) for Brushes and Timed Pages. Upstream files
touched: `PaintActivity` (`timedLook` shows the countdown and `promptLook`; `dailyPrompt` gives the page its card,
`promptCard` is the window; the file menu's Open Notebook, `NotebookLayout.OPEN_NOTEBOOK`), `ToneDocument`, `DocumentCodec`;
`InkWashBar`'s menu, Undo and Redo have no button background, the menu's icon `ic_ph_dots_three_circle` (Phosphor Bold's
ring, three dots). Checked in `NotebookRulesChecks` (seconds round-trip, a TIMD-only file, the recovery, the countdown),
`DailyPromptChecks` (the PRMT section), `DrawLayoutChecks`, `LibraryCardChecks`, `NotebookNamesChecks`.

### The library card's look; its screens; a page's own template; the day last added to (2026-10-21, branch `inkwash-carddesign`)
The owner's "could look nicer" pass. New pure `CardArt`: a `Pen` interface in dp (fills, dot greys from `DotPattern`,
frames, rules, the pocket's notch, text in four faces measured as `LabText` measures them, icons, pictures, turns), and the
shared pieces (the stamped double rule, a rubber stamp inked or faint, the check, a choice card, a slider). New Android
`CanvasPen` draws a `Pen` on a `Canvas` (shapes without smoothing, `DotGray` tiles, `Theme.face`/monospace, the
`ic_ph_*` icons; its `ArtView` draws an art in dp). `LibraryCard` is rewritten: `Layout` (the largest text that fits,
the name in two lines or one, the borrowers' lines in one column or two, the stamps in a row or two by two, the slack to
the swatch first), `draw`, `at` (the part tapped), `tap`/`screen`; the rows, `choices`, `toggled` and `rowAt` are gone.
New pure `CardSheet` holds each screen's title, line, choices, notes, icons, sliders and layout (`NotebookLayout.pick`,
`pickWidth`, `pickHeight`, `PICK_*`, `NOTE_LINES`, `PRINT`, `LEAVE_OUT` are gone). `CardSelector` places a `Card` view a
choice (said to a screen reader) and a `Slider` view on the sheet's boxes; it no longer uses `BandSlider` (Brush Lab
still does). `NotebookScreens`: the card through `CanvasPen`, its template swatch made on the worker; the strip's
`GestureDetector` (a hold on the front cover: Cover Design; on a page: `PAGE_TEMPLATE`).
A page's own template is a `PTPL` line `"="` + key (`NotebookOptions.OWN`, `ownKey`; `pageTemplate` reads it first);
older builds read it as Blank under Random and pass it by otherwise. `DrawingBook.setPageTemplate`; the notebook's own
template (or Random) puts a page back. The day last added to: `BookOptions` `LAST` (an int of epoch days, skipped by
older builds), `RecoveryCodec` `TSRL`; `DrawingBook` counts page edits (`pageEdits`; covers don't count),
`pagesChanged`, `lastAdded` (today while a page is unsaved), `restoreLastDay`, `today` (a check's clock; -1 keeps none).
Not the file's mtime: the one-time passes (`randomCoversOnce`, `plainPagesOnce`) rewrote every notebook, and a setting
saves the file too. A file from before it says the made day alone until a page changes. `NotebookTemplate.fill` clips
x to the row (a small Hex picture ran off the array). Upstream files touched: `PaintActivity` (`setPageTemplate`; the
file menu's Guides in PDF row, marked while printing), `DrawingBook`, `BookOptions`, `BookCodec`, `RecoveryCodec`,
`NotebookTemplate`. Display renames: `TracingPaper.TRANSPARENCY` "Page Transparency", `NotebookRules.LOCK` "Lock
Settings". New icon `ic_ph_check` (Phosphor Bold). Checked in `LibraryCardChecks` (fit on both tablets in both turns,
every word 12 sp or more inside its box, the in-use fill and check, parts hit where drawn, `LAST` and the recovery, a
page's own template), `NotebookRulesChecks`; `CoverChecks.book` keeps no day, `SaveLessChecks` expects the `LAST` record.

### Timed Sections on the Diary Page (2026-10-21, branch `inkwash-timedsections`)
`TimedSections` (pure, immutable) holds a page's sections: each one's seconds and wall-clock end, `start`, `skip` (the
running one ends now, the rest follow on in full), `active`, `remaining`, `locked` (a day, `LOCK_MS`, after its end),
`nextChange`, and `draw`, the screen's overlay (the running box outlined `band` px in ink, the rest dimmed to `DIM`, a
flashing box's outline inverted). `NotebookTemplate` gains `sections` / `sectionSeconds` / `timed()` / `sectionRects`
(the Diary Page's four boxes; its guides now drawn from them). `DailyPrompt.sectionTimes` reads "Diary Page times | Did
2:30 | ..." from the prompts file (a times line is never a card; the defaults gain one). Page record: `SECT` in
`DocumentCodec` (`TimedSections.encode`, only once started; skipped by readers before it), held by
`ToneDocument.sections` / `setSections` (a change to save, not Undo; a page with them started isn't `unmarked`;
`DrawingBook.duplicatePage` clears them, as the clock). Upstream files touched: `ToneDocument` gains `fence` / `fenced` /
`inkless`, and `finish` puts a fenced box back as the gesture found it (`restoreSpan` over each captured tile), so a
locked section takes no marks from any tool, its Undo step holding nothing there; `DrawingPad` refuses a pen landing in a
fenced box (`TimedSections.LOCKED`), draws the overlay in `render` / `renderAll` (`drawSections`, never in the page) and
gains `showSections` / `flashSection`; `PaintActivity.timedLook` hands a Diary Page to `sectionLook` (Timed Pages
configured 0 there), `allowStroke` starts a fresh page's sections, `countdownTapped` starts or skips. `DrawLayout.TIMER`
is 40 dp (two lines); `InkWashBar`'s countdown is tappable and autosizes. Not fenced: Undo of a stroke from before the
lock, and the whole-layer edits (Clear Page, Clear Layer, Delete Layer). Checked in `TimedSectionsChecks` (and
`OlderSectionsCheck` against a1328a8), `DrawLayoutChecks`.

## Print style and everyday pens (2026-10-21, branch `inkwash-presets`)

A fifteenth style, **Print** (`ModularStyle` index 14, code digit `e`, after Anything goes so every older code reads as
before; `ModularStyle.shown()` lists it before Anything goes, for the Tools page's chips, `ToolsLayout.chipLabels`,
`ToolPresets.styles` and Brush Lab's style table and flyout, which is now 2 × 8 at 25 dp rows, `LabLayout.styleRows`).
Its roll: round or chisel, ink, wash, charcoal or graphite, pressure darkening; `rollScreen` gives every Print roll a
Print Shading (any of the ten alike) and `rollTone` one in five Screentone, both from the seed as before, so no other
style's rolls change. Marker gains a size for Wash (40 px), for the Highlighter. Twelve Print Basic tools and four
everyday pens (Ballpoint, Gel pen, Fountain pen under Fineliner; Highlighter under Marker, Wash with Blend Mode Behind)
are built dial by dial in `PresetPictures` (test sources), which prints their `ToolPresets` rows and draws a contact
sheet (S-curve, scribble and card loop each); `ToolPresetsChecks` checks the rows are its output. The web lab
(`modular-lab-src/parts.js`) and Brush Studio don't have Print: a Print code doesn't read there. No upstream file touched.
### Hand Hatch and four tonal prints (2026-10-21, branch `inkwash-handhatch`)
No upstream files touched. Engine (Scribulor's own, pure): new `HandHatchStamper` (hatch strokes laid whole, once each, keyed
by set, row and place along it in a `LongSet`, seeded by the brush's seed and the stroke's first sample; sets added by
pressure, by a return to a place (a coarse grid of last distances and passes) and by pen time on a place through Stipple's
Build-up ticks (`StippleStamper.Holds`, so `ModularStroke.holds` is made for it too and the pad's held ticks reach it);
strongest coverage kept, so batching never changes it); `PartChoice.HAND_HATCH` (Tip choice 12, key `handhatch`, Hatch's
dials but Layers, and `PartDial.HLEN`, `HWOB`, `HENDS`); `ResolvedBrush` (`LineTip`/`BodyTip.HAND_HATCH`, `Pattern.hLen`,
`hWob`, `hEnds`, `handHatch()`), `LineStamper.Meta.handHatch`, `ModularStroke` (reach, dispatch), `QuickStroke` (kept up
with as a stipple: its strokes are laid once), `ModularSizes.HATCHING` (60 px in every style), `ModularFeatures.seedOnly`
(never rolled), `ModularWords`, `DrawingNames`. `PrintScreen.Kind` gains `STIPPLE`, `WOODCUT`, `SCRIBBLE`, `HATCHING` before
`DOTS` and their `TONE_` twins after `TONE_MEZZO` (`PartDial.SCREEN` options 11 to 14, `PATTERN` 7 to 10, still one
base-36 digit): a raw field per family (`raw`), evened at class load (`EVEN`, from a page at the default Pattern Size and
45 degrees) so the share inked is the darkness, still a threshold at each page pixel; the older kinds' thresholds are the
bit-for-bit same (`HandHatchChecks.old`). Scribulor UI only: `BrushLab.chips` shows Hand Hatch in Advanced only (Basic's
thirteenth Tip tile would not fit the Nomad's swap strip). Rolls unchanged (new prints never rolled). Checks:
`HandHatchChecks` (determinism live and batched, building by pressure, passes and holding, reach, bounds, dials, old codes
and prints, words, a bench line); `scripts/handhatch_pictures.sh` draws the contact sheet. Ben-Day redrawn (the owner):
`PrintScreen.BENDAY` is now a round dot at every site of a hexagonal lattice (1.5 cells apart, in the angle's frame), a
pixel's threshold the tint (`TINTS`: 10, 20, 30, 50, 70%, solid) whose dots first reach it (`BENDAY_TINT`, measured at
class load); same Kind and option, so codes read as before and only its look changes. `ScreenChecks` takes its steps. Web lab and Brush Studio have
no twins yet. Since 2026-10-22 (branch `print-tissue`, the owner: "it seems to jump between shades") a pixel's threshold is the
share itself (`BENDAY_SHARE`, solid at .997), not snapped to a tint, so the dots grow smoothly with pressure;
`ScreenChecks` takes a step every twentieth of the grey. `WildSample.TYPICAL_PRESS`: a print whose shade
follows pressure draws its loop and tray stroke at travel .6 at most (since branch `previews-typical`, every brush:
`pressShades` is gone; see below). Damp tissue is Clear Water, Lay Down 0
(`ToolPresetsChecks.tissue`).

### Tilt Shading: pencils shade laid low (2026-10-22, branch `pencil-tilt`)
The owner: "all of the pencil presets... let's use the tilt so when the pencil is tilted it shades like an angled
pencil". Engine change, mirrored in the web lab and Brush Studio. `PartDial.SIDE_SHADE` (key `tshade`, "Tilt
Shading", 0–1, 0), the hand's last dial after Gradient's, in `ModularCode.ADDED` and Hand's `KEEPS`, so every older
code reads it off and every brush without it writes as before. `ResolvedBrush.Pen.side` (any line; 0 under a Screen);
`Pen.shade(lean)` = side × smoothstep(.5, 1, lean) on `BrushDirection.tiltLean` (5°→0, 65°→1): nothing through 35°
(the Manta's degrees, `docs/TILT_INPUT.md`; the natural hold, 30–45°, stays the crisp line, byte for byte), all of it at
65° and lower. `LineStamper.nib` widens the line by 1 + `SIDE_WIDTH` (5) × shade, lifts `SIDE_LIGHT` (.25) × shade of its
darkness (so graphite's contact rule reaches only the paper's high points: grainy) and gives the dab an edge soft over
1 + `SIDE_SOFT` (.5) × shade × its radius (`Dab.soft`); a shaded segment is stepped as its widened size (cost: 4 px
pencils at 70° 0.02–0.04 ms a sample on the desktop, upright 0.006–0.014). `ModularStroke.lineReach` grows by the same
width. Control card: `BrushLab.handRows` shows it after Fast for a Round, Chisel, Flat or Filbert line of an ink-layer
medium (`tiltShades`). Presets: 2H, HB, B, 2B, 5B and Sketch pencil at 1 (their Tilt Size and Tilt Dark cells, which
widened and lightened from 5°, set to 0; seeds, names and captions kept; Erasable unwritten); Loose hatch (a Hand
hatch) unchanged. Lab: `engine.js` `sideShade`/`SIDE` in `stampLine`, `parts.js` `pen.side`, `newmods.js` the dial,
`dialShown`, `tiltShadeChecks`; `modular-lab.html` patched with the same hunks; Studio rebuilt (check.cjs 117 of 117;
java-codes 621 of 621, the 2H preset and it half shaded among them). `LineStamperChecks.tiltShades`.

### Tilt Shading lighter and more diffused, charcoal and pastels too (2026-10-23, branch `tilt-shading`)
The owner: "on an angled pencil the shade is usually a little lighter than a straight mark... and it's a bit more
diffused... for charcoal etc as well too". Engine change in `LineStamper` and `ResolvedBrush`, mirrored in the web lab
and Brush Studio. A shaded dab (`Nib.shade`, `Dab.side`) now lifts `SIDE_LIGHT` (.05, was .25) of its darkness, since
graphite's and charcoal's rules amplify a lower coverage into a washed-out band, and instead keeps its coverage where the
side touches: `LineStamper.sideTooth` drops it below `SIDE_SKIP` (.35) × shade of the paper's fine grain (eased over
`SIDE_BAND` .05), nearly all or nothing so the medium's own rule keeps its grain on what is left, and
`LineStamper.sideStreak` lays faint streaks along the stroke (the brush's own draws, 9 across, at most `SIDE_STREAK` .1).
Its edge is soft over `SIDE_SOFT` (.8, was .5) of the radius, and a stick's fuzzy edge is `SIDE_FEATHER` (.5) wider
again. At 70 degrees and travel .3 to .9 the band's middle is 52 to 83% of the upright mark's darkness for 2B pencil and
61 to 73% for Soft charcoal (was 0 to 52% and 49 to 60%). Speed: a shaded dab is laid every `SIDE_STEP` (.2) of its
width (a pixel was laid about ten times over), and a pixel already at its most is left before the dab's work, so a 4 px
pencil at 70 degrees costs what it did (0.04 ms a sample on the desktop). `ModularStroke.lineReach` no longer grows
with Tilt Shading: the lattice and quick-or-live choices go by the upright line again, so an upright mark is drawn as
before the brush had Tilt Shading (pencils of 16 px and up are live and full resolution again, as before 2026-10-22),
and `sideReach` (the widened line) only pads `trailReach`. Presets: the Charcoal and Pastels sticks (not Blend stump,
Stump, Wax crayon, Crayon scratch) at Tilt Shading 1 with their Tilt Size and Tilt Dark cells 0, as the pencils, and
`ToolSlots.FIRST`'s Soft charcoal with them. A crayon's line takes the tooth and streaks but not the soft edge (no
preset has it). `LineStamperChecks.sideTone`: the 2B pencil and Soft charcoal presets at 70 degrees are 55 to 80% of
upright's darkness at the same pressure, with more of the band soft edge.

### Charcoal's quick preview reads its grain (2026-10-22, branch `pencil-tilt`)
The owner: "charcoal preview is also darker than the resulting mark..". Scribulor-only classes; finals byte for byte (Golden
over Charcoal and Pastels: 90 rows, every final hash the same). `ModularCompose.Contact`: charcoal's contact rule with
its brush's terms worked out once (the same arithmetic, so the compose is unchanged); `QuickStroke` fill `GRAIN`: a
charcoal or pastel line's quick preview (a nib, not placed lines; not White, Invert or a Screen) writes, at each pixel,
the tone the rule lays at the stick's core: `LineStamper`'s coverage (its press `lerp(1, .3 + .7 pr², fuzz)` × the
hand's darkness × the stick's low and mid grain) through `Contact.preview` (the rule without its divisions) over the
tone found, instead of the flat `STICK_SHARE` grey. A stick past `ModularStroke.INK_LATTICE_PX` reads it once each
2 × 2 pixels. Measured, quick preview against final, 16/60/120 px at travel .4/.6/.9 (the session's scratchpad
`pencil/`): dark pixels 0.88–1.30 times the final's (were 0.84–3.51: Messy stick 3.51 → 0.99 at 60 px, .4), 76–100% of
the final's own. Desktop pen-thread cost about twice the flat tone's (0.02–0.035 ms a sample at 60–120 px, 0.07 at
240); `QuickStrokeChecks.previewIsCheap` asks a Charcoal preview for under half of drawing live (a third elsewhere).
Cards are unchanged (already at `TYPICAL_PRESS`: 0.80–1.06 of a real stroke at .6).

### Previews at a usual pressure; crayon's previews (2026-10-22, branch `previews-typical`)
The owner: "the preview marks are much darker than the typical line left at normal pressure", "ie. crayon", "the
preview for crayon is pretty poor". Scribulor-only classes; no engine change, finals byte for byte (the speed plan's
Golden: 702 rows, every final and live hash the same). `WildSample.typical` caps every loop and tray stroke, and a part
card's Swell wave (not Hand's ramp or Drying's Lift), at `TYPICAL_PRESS` (.6). `ModularSizes.sampleSize`: Crayon a fifth
of the reach (was a ninth, Charcoal's). `WildSample.waxGround`: a crayon's Scratch samples scrape a ground of the same
crayon, Scratch off, not the White brushes' flat dark band. `QuickStroke` fill `TOOTH`: a crayon line's quick preview
(Wax crayon past ~40 px) writes the line's whole ink where `Crayon.tooth` ≥ `CRAYON_PEAKS` (1.1) − the newest sample's
travel, nothing between, instead of the flat STICK_SHARE grey. `WetPaintChecks` (Wax crayon's mid-stroke hashes) and
`LegacyConversionChecks` (the loop samples) re-recorded; their pages unchanged.

### Tracing paper, Stage 3c: layers are stacked pages (2026-10-21, branch `inkwash-stacks`)
Spec `docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md`, "Stage 3c". A layer is a clear page stacked on the page
below: `TracingPaper.LINKED` (bit 16 of a page's step bits, so it moves, copies and goes with its page wherever `steps`
does), the `STAK` record (`BookOptions`, only when a page is stacked; a build without it sees each layer a page,
`OlderOptionsCheck` against 2ddb2a4) and the `TSRK` recovery section. New, Scribulor's own and pure: `PageStacks` (the
stacks the links make; `flatten`, a stack's layers as one page for the copies and the crisp page), `LayerEdits` (Delete
and Merge Down as one Undo step: `DrawingBook.take`/`restore`, ready while the page's last Undo step and the book's edits
are unchanged), `PageStacksChecks`. Android-side: `LayerColumn` (the column's + above, thumbnails, + below, folded
button and hold menu), `InkWashBar` (its LAYERS part; `beside`/`openPanel` shared, `drawTab` back), `DrawLayout` (the
layers' budget, folded when a full stack doesn't fit; the strip without a Layers button; the menu's words and sizes),
`PageTurner` and `NotebookScreens` (a stack one preview, number, tile, turn, move; `drawStackPreview`, a stack's crisp
page), `PaintActivity` (`newLayer(above)`, `duplicateLayer`, `deleteLayer`, `mergeDown`, `deleteStack`, `moveStack`,
`drawOnStack`, stack turns, the layer Undo queue, the blank page at the end on every `updatePages`). Deleted:
`StackPanel`, `StackLayout`, `StackLayoutChecks`, `TracingPaper.shownStep`.

| File | Lines | Why |
|---|---|---|
| `DrawingBook` | +95 −8 | `stacks`, `linked`, `addLayer`, `moveStack`, `take`/`restore`/`Taken`, `changes`; a copy is stacked on its original; deleting a stack's bottom makes the layer on it the bottom (and moves the page drawn on to the layer below, for a layer); `template(i)` is the stack's bottom page's; step bits masked to their byte. |
| `BookOptions`, `BookPictures` | +12, +6 | `STAK`, `stacks(found, …)`; the shelf's `Cover.stacks` (the count it shows). |
| `RecoveryCodec` | +9 | The trailing `TSRK` section; older readers stop before it. |
| `NotebookExport`, `AutoExport` | +40, +3 | A PNG and a PDF page a stack (`update(…, tracing)`), its layers flattened under the bottom page's guides, a Hidden layer left out; a page alone keeps its key. |
| `PagePicture` | +14 | `stacked`: layers' pictures multiplied, the strip's picture of a stack. |
| `InkWashLayers` | +8 −4 | `merge(document, above)`: Merge Down through Merge Old Layers' flattening. |
| `BlankPages` | +25 −10 | `end`/`lastBlank`: the last stack, every layer; `Watch` on the last stack. |

### Stage 3c follow-up: layers above cover, landing layer, flattened neighbours (2026-10-21, branch `inkwash-stacks2`)
`GhostPlane.build` returns `Planes` (under: as before; over: the layers above the page drawn on, tones and cover),
groups a neighbouring stack (`Source.group`) to flatten it before its strength; `TracingPaper.ghostLayers` (page, layer
%, stack %, stack), `MOST_DECODES`; `DrawingBook.pageOf` (a memory-only bit beside the step bits, masked out of
`tracing()`). Checks in `PageStacksChecks` (over, lands, neighbours, decodes).

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +7 | `visibleLayers(…, overTones, overAlpha)`: the layers above, flattened, as a last layer; arrays `MAX_LAYERS` + 3. |
| `ViewportBitmap`, `viewport_bitmap.c` | +6, +4 −4 | The planes passed through; `nativeCompose` takes 11 layers. |
| `DrawingPad`, `MirrorShow` | +2 −2 | The ghost is `GhostPlane.Planes`. |

### Layers and pages bug hunt (2026-10-21, branch `inkwash-bh-layers`)
| File | Lines | Why |
|---|---|---|
| `DrawingBook` | +8 −2 | `addLayer` below the bottom gives the new bottom the stack's own template; `unstack` (a stack's bottom deleted) passes its template, ghost pin and cover picture to the layer on it. |
| `PaintActivity` | +6 −3 | Use This Page on the Cover (file menu) uses the stack's bottom page, as the notebook screen does; `deleteStack` on the stack drawn on lands on the next stack's layer drawn on last (`pageOf`). |

### Spray hatch and screened fills (2026-10-21, branch `inkwash-sprayhatch`)
No upstream files touched. Engine (Scribulor's own, pure). `HandHatchStamper` is now a spray (the owner: "like stipple/spray
with lines"): the moving pen's steps (`passDose`, by pressure) and the held pen's Build-up ticks (`holdRate`) dose cells of
a fixed page grid round the pen (`State.dose`, falling off `1 − q²` toward the band's edge), taken in one order by distance
so batching never changes them; each cell's candidate strokes (`Spray.lay`: place, turn, bow, weight from the seed) are
laid once its dose passes their thresholds, the first third in one direction, then the crossing one, then the third,
up to `full` (Spacing's pitch, `FILL`); a darker dose lays a cell's strokes again as dark. Its `LongSet`, keys and row
sets are gone; Tip choice, dials and codes unchanged. Screened marks (Print Shading, Screentone): `ResolvedBrush` resolves
a screened brush's tip crisp and even (`Body.crisp`, `BodyStamper`'s cover-sharp edge; no soft edge, dry brush, streaks,
tooth, stick fuzz, wet edges, line bled into the wash or diffusion) and `ResolvedBrush.screenTone` is the darkness the
screen inks: the line's ink, the body's at `SCREENED_BODY` of the swatch, the more of the two, so a stroke prints one tone
over its whole footprint (the cause of "a squiggly line in the middle": the screen thresholded the body's soft falloff
and its light glaze, only the line's core passing). `ModularCompose` inks a screened line without its stick grain and
from `screenTone`; `PrintScreen.printed` keeps the darker of the print tone and the mark (was: built up as transparent
paint), and the compose inks from the print tone as kept, so strokes over strokes at one tone lay the same dots and a
filled area is even; `QuickStroke`'s preview follows. Checks: `ScreenFillChecks` (each Print preset: a scribbled square's
evenness against the screen's own, a stroke's edge, live, batched and again the same), `ScreenChecks` (union of two
strokes, the print tone the stroke's, the darker of two in either order), `HandHatchChecks` (spray: slow drag, held dab,
cap; bench at its busiest). `SprayHatchPictures` (test side, not a check) draws the before and after sheet.

### Import Image, Import PDF, Export Now (2026-10-22, branch `import-images-pdf`)
The file menu's three rows (docs/archive/superpowers/specs/2026-10-22-import-design.md). Pure and checked: `PictureImport`
(`PictureImportChecks`); `NotebookTemplate.tones` reads a picture's brightness through `PictureImport.grey` (unchanged);
`LayerEdits.added` (Undo takes a layer just added away, with what was laid on it). 2026-10-26, at the owner's request
(branch `erase-image`): Place lays the picture on the layer drawn on (`PictureImport.place`), so the eraser takes it and
the marks under it alike; the new layer and `LayerEdits.added` are gone, and its Undo is the ordinary one.
2026-10-29 (branch `import-merge`, the owner: "can we merge the pdf and image import flow? ... so it'd be just one import
thing"; "more like supernote file explorer"): one **Import** row (`importFile`): one picker for both, browsing by folder
(`PictureImport.entries`, `start`, `path`; Android's fallback picker takes `image/*` and PDFs); a picture goes to
`importPicture` as before; a PDF to `ImportPicker.pdf`, New Notebook (`importPdf(uri)`, unchanged) or This Page (the
page drawn by `PdfImport.page`, placed through `importPicture`). Upstream `PaintPreferences.importFolder` (unused
since `ImageImport` went) remembers the picker's folder, its default now Document (was Download): one line.

| File | Lines | Why |
|---|---|---|
| `PaintActivity` | +110 −5 | The rows; `pickDocument` (ACTION_OPEN_DOCUMENT) and its result (since 2026-10-26 only where the app may not list files: Scribulor's `ImportPicker`, with Cancel; one picker by folder since 2026-10-29, `importFile`); Import Image's placing and `lay` (on the layer drawn on since 2026-10-26, one Undo step); Import PDF's box with Cancel, then the new notebook opened; `exportCopy(now)` for Export Now and its note. |
| `DrawingPad` | +14 −4 | The placer's hooks beside the magnets' (touch first, overlay off and on, the raster composed again, suspend, no auto-refresh mid-drag); the magnets' host shared, with `dots()`. |
| `DocumentStore` | +19 | `create`: a new notebook's file written whole on the store's thread, or not at all. |
| `AutoExport` | +8 −3 | `notebook(…, done)`: run once the copies are written; whether they were queued. |

### Wet paint (2026-10-22, branch `wet-paint`)
MonoPaint's wet canvas (`WetWatercolor`, upstream `8743286`, MIT, © mpdairy) under the six-part engine as Drying's Wet
(docs/archive/superpowers/specs/2026-10-22-wet-paint-design.md; the owner's Option A of
docs/archive/superpowers/specs/2026-10-22-monopaint-paint-proposal.md). `WetWatercolor` goes from dormant to live engine code, driven by Scribulor's own
`WetCanvas` (the session's fourth wet thing) and `WetPaintStroke` (the footprint along the pen laid into it). Its
upstream hooks are split into their own commits (generic: erasable/kind for its own gestures, wake frames, rest, rewet,
finishing with the previous edit). `BrushInk`, `PaintLoad` (one setter) and `WetWorkBudget` are reused as they are;
`CanvasGravity` is not: the tilt comes from the session's `PageTilt` source, one sensor path. MonoPaint's own wet canvas
path in `DrawingPad` (`wet`, `advanceWet`, `dryWet`, the droplet and wetness bar) is untouched and still unreachable
from Scribulor's bar.

| File | Lines | Why |
|---|---|---|
| `WetWatercolor` | +60 −5 | The hooks above. |
| `PaintLoad` | +5 | `left(share)`. |
| `QuickStroke` | +37 −208 | Its preview's footprint sweep moves to `FootprintSweep` (byte for byte); `start` routes Wet strokes to `WetPaintStroke` and Pastels/Dry brush over wet paint to its layer; Draw once (the experiment). |
| `ModularStroke` | +2 | Tells the session a stroke ended (Pastels and Dry brush joining wet paint). |
| `WatercolorSession`, `DryingTicker` | +119 −10 | The wet canvas: kept across strokes (`settleBefore` leaves it, `settleNow` dries it), ticked after the others, rests at Undo; the ticker's droplet tap dries it; fading marks wait only while something moves. |
| `PaintActivity`, `PaintPreferences` | +11 | Draw once from the launch extra `drawOnce`. |
| `InkWash` | +2 −1 | The finger eraser settles as the pen's does (wet paint stays wet). |

New, pure: `FootprintSweep`, `WetCanvas`, `WetPaintStroke`; checks `WetPaintChecks`. Engine: `PartChoice.WET_PAINT`,
`PartDial.STAYS_WET`, `ResolvedBrush.Wet`/`asInstant`, `ModularFeatures`, `ModularWords`, `ToolPresets` (Gouache and Oils
replaced, `RETIRED`), `ToolSlots` (the Erasable migration knows the retired ones). Not mirrored in the web lab (see the
spec).

### Everything erases everything (2026-10-22, branch `erase-template`)
The owner: "Pen isn't erased by eraser (everything)". The Eraser on Everything takes every mark whole, permanent paint
too: it erases as an eraser limited to every kind (`KIND_ALL`). Engine only; no upstream file but `ToneDocument`'s comments.

| File | Lines | Why |
|---|---|---|
| `EraseBothStroke` | +14 −8 | `takes`: every kind for Everything; the fluid's lift still by the Erasable rule (its wax stays). |
| `ToneDocument` | +9 −4 | `compositeToneKept`: `KIND_ALL` rubs a cover's design as Everything did; `eraseKinds`' words. |

### Template guides on the screen: Full, Half, Faint, Hidden (2026-10-22, branch `erase-template`)
The owner: "A way to hide/show/adjust transparency of template". Pure and checked: `NotebookOptions.guidesShown` (a
`GSHN` record in `BookOptions`, a `TSRG` section in `RecoveryCodec`), `NotebookTemplate.shown` (the guides lightened
as ghosts are, cached beside `shared`), the card's eye (`LibraryCard`, `CardSheet.Screen.GUIDES`, `CardArt.Icon.EYE`).

| File | Lines | Why |
|---|---|---|
| `DrawingBook` | +1 −1 | `guided`: the page drawn on gets the guides at the notebook's step (the copies' `printed` unchanged). |
| `PaintActivity` | +7 −1 | The file menu's Template row; the page shown again when the step changes. |
| `NotebookScreens` | +3 −2 | A page shown big takes the step's guides, keyed by it. |
| `CardSelector`, `CanvasPen` | +2 | The Template Guides screen's choice; the full eye's icon. |

### Side column like MonoPaint's (2026-10-22, branch `sidebar-monopaint`)

The owner (2026-10-22): "i want users familiar with the monopaint interface to feel like they know what everything does
in ours." Where MonoPaint 0.98.3 has an equivalent we follow its icon, order and behaviour: its sidebar palette
(`Toolbar.addSidebarPalette`: four 22 dp squares two by two, default 0, 162, 225, 255; a tap selects the shade; set
from the palette editor) becomes `SwatchBar.grid`, the shared `SwatchFaces` grey faces paired two by two under the sizes
and `GreyStrip` (tap or drag picks where the pen lifts, its square following the pen through `StripMarker` as the color bar's marker does; hold opens the Sizes and Greys window, which is our palette editor; rub picks and loads, as the color bar's `PaintRub`). The four
greys are still `ToolSwatches`' shared ones with their defaults (black, dark, mid, light grey: not changed to
MonoPaint's white, which a wash can't lay). `GreyStrip`'s tap now sets the tool's own darkness alone
(`ToolSwatches.useGrey`, `greyInUse`: no swatch marked unless it is that grey; "the color picker is too hard to use");
its four snap marks and `ShadeStrip.nearest`/`SNAP_MM` are gone. The wet indicator sits under the swatches
(`DrawLayout.COLUMN`: FACES, SWATCHES, WET), 44 dp on the Manta (48 before) so the Manta in landscape stays at its
preferred heights; the Nomad in landscape now fits under its preferred heights (fit 0.36, icons 42 dp). Zen Mode's
button is beside Menu, half its row each ("zen mode button, can you place it on the toolbar always"); the floating
filled hands (`ic_ph_hands_praying_fill`, Phosphor Fill 2.1.1) only in Zen Mode; the outline stays Bold, as every bar
icon (DESIGN.md). The brush line (`InkWashBar.brushLine`, `DrawLayout.BRUSH_LINE` 18 dp, `brushWords`) sits in the
page area over the page bar; `DrawLayout.pageHeight` (the notebook screens' page proportion) is unchanged, the canvas
`canvasHeight` 18 dp shorter, so older pages open at 100% panned (`CanvasViewport`), never shrunk. A slot's tap says
its name (`say`, `DrawLayout.NAME_MS`). Checked: `DrawLayoutChecks` (order, budgets, brushLine), `GreyBarChecks.sideStrip`.
Not checked on a device (the owner was using it).

| File | Lines | Why |
|---|---|---|
| `BlowDryer` | +15 −9 | Its frame drawing is a static `draw`, `FRAMES`/`FRAME_MS` package-visible, so `WetIndicator` runs MonoPaint's blow dryer after its tap ("monopaint when drying has a brief hair dryer animation"); the button overlay draws as before. |
| `DrawingPad` | +8 −2 | `uncovered`: a closed popup's area is blanked white then drawn again (`eraseFastInk`, as the Layers panel's close), so the eraser's kinds and the other flyouts leave no ghost ("popout menu for erase often leaves a ghost"). |
| `PaintActivity` | +3 −3 | The brush line placed over the page bar, hidden in Zen; the floating Zen button shows the filled hands; the menu's Zen row gone. |
| `ShadeStrip` | −13 | The strip's snap helpers, no longer used. |

### Zen Mode (2026-10-22, branch `erase-template`)
The owner: "a zen mode without toolbars would be nice. floating icon hands-praying in bottom left". The file menu's
**Zen Mode** row hides the column and the page bar (`showInkWashBar`), so the pad, and the page fitted to it, take the
whole screen; a plain hands-praying icon (`ic_ph_hands_praying`, Phosphor Bold) over the stage's bottom-left corner as
turned, 44 dp, 12 dp in, leaves it (Back too). A session's only. Trade-offs: the page is fitted again to the larger pad
(it grows and moves; keeping it still would waste the space Zen gives), a Timed page's countdown (on the column) isn't
shown while in Zen (Time is up's window still is), and a fast e-ink stroke crossing the icon may draw over it until the
next refresh redraws it (`invalidateZen`). Checked: `DrawLayoutChecks.zen` (its size, place, words).

| File | Lines | Why |
|---|---|---|
| `PaintActivity` | +40 | The row, `setZen`, the button over the stage, Back leaves Zen. |
| `DrawingPad` | +1 −1 | A full refresh draws the Zen button again. |
| `DrawLayout` | +9 | Its words and sizes. |

### Wet brush: MonoPaint's wet brush pen on the wet canvas (2026-10-22, branch `wet-brush`)
Brush pen's **Wet brush** rebuilt on Wet (the owner, 2026-10-22: "yep go for it"; the wet paint spec, section 8): Wet on
Ink or Wash is MonoPaint 0.98.3's wet brush pen, untinted `BrushInk` in flowing water (`WetWatercolor.waterMask`),
held per holder between strokes (`HeldPaint.wetInk`), dipped by a tap on a grey. The old Wet brush (Fast-wet line and
wash) is retired (`ToolPresets.RETIRED`), its code and engine path unchanged.

| Upstream file | Lines | Why |
|---|---|---|
| `BrushInk` | +6 −6 | Hook: a brush's own capacity (`BrushInk(dryPaintSticks, capacity)`); `CAPACITY` upstream, so its behaviour is unchanged. Generic: a bigger brush holds more. |
| `WetWatercolor` | +12 −3 | Hook: `waterMask(..., pull, ink, trade)`, flowing water whose new pixels trade `trade` dabs' worth of ink in one go (a footprint laid once a pixel, as the pen's overlapping dabs would rub it); `layWater` takes the factor, 1 everywhere upstream. |
| `InkWashBar`, `BrushLabActivity`, `InkWash` | +10 | A grey's tap or rub dips the selected tool's brush (`InkWash.dip`, `HeldPaint.dip`). |

Pure: `ResolvedBrush.Wet.inks`, `WetPaintStroke` (ink mode, `INK_PULL`, `inkTrade` by pressure, `INK_LOAD`, `inkCapacity`), `HeldPaint` (wet ink kept in the preferences, `saveWetInkIn`),
`ModularWords` ("Holds ink between strokes. Tap a grey to dip."), `ToolPresets`; checks `WetPaintChecks.wetBrush`,
`ToolPresetsChecks`. The bench's `WET_NAMES` gains it.

### Speed plan F6, F9, F7, F5 (2026-10-22, branch `speed-f6-f9-f7`)

Engine-internal speed work (the speed plan's §5.2): quick previews (`QuickStroke`: placed lines sweep each line's own
nib through the new `Placement.Geometry`; washes and sticks flat at every pixel), Trail for Drying and Halo
(`ModularStroke.trail`), a render's deferred re-tapers (`ModularStroke.retaperNow`), a big Drying wash laid in blocks
over the cores (`DryingWater.finishing`). `FootprintSweep` shapes may have up to `SIDES + 2` corners or none. Final
pages byte for byte as before.

| Upstream file | Change | Why |
|---|---|---|
| `ToneDocument` | −1 | `logStroke` no longer makes a new revision: the stroke's own step made one, and the wet state its lift left (a Slow wash, a halo, a floating bath) holds that one, so the extra revision dropped it as "page changed". |


### Wet watercolour: Watercolor's washes on the wet canvas (2026-10-22, branch `watercolour-wet`)

The owner's choice ("i think maybe 1 ... we can port over the dark edge, bleed and bloom"; the wet paint spec, section
9): Wet on a Watercolor style Wash is a transparent wash laid into MonoPaint's wet canvas (`WetWatercolor`, upstream
`8743286`, MIT, © mpdairy), merging wet into wet and drying back with Scribulor's look (dark edge, blooms, granulation,
tide lines). Eight Watercolor presets made Wet; the old ones retired (`ToolPresets.RETIRED`), their codes unchanged.

| Upstream file | Lines | Why |
|---|---|---|
| `WetWatercolor` | +326 −12 | Hooks marked "Inkwash": `wash(Wash)` (a wash's dials and noises; null upstream: nothing below runs), `washMask` (a glaze of fresh water, each pixel once), washes as numbered water (`Tile.wave`, `join`: wet into wet is one wash), `edgeness` (a pass before the frame's flow), `capillary` (pigment from fresher to drier water, damp water flooded: blooms), `bleedInto`, `look` (how a wash's pigment shows as it dries back: rim, grain, mottle, tide lines; 1 for every other pixel), `wakeBeside`, tile-scale evening off and fall at .4 where washes are. Large for an upstream file: the wash's physics needs the tile's private state; it is one switch (`wash`), with upstream's frames, slices and sharing unchanged. Not generic yet; could be offered upstream as "watercolour edges" if Matt wants it. |
| `ModularPerfBench` (androidTest) | +7 −4 | The wet bench takes names on the desktop and gains the washes; a brush that runs is benched tilted. |

Pure: `ResolvedBrush.Wet.wash` (`Wet.wash(b, stays)`: Dark Edge, Bloom, Backruns, Bleed, Tooth, Stays Wet; style
Watercolor), `WetCanvas.brush` (keeps the wash), `WetPaintStroke.washStrength`, `ModularWords` ("Merges while wet, blooms
when damp."), `ToolPresets`; checks `WetPaintChecks.wash*`, `ToolPresetsChecks`, `GreyBarChecks`; pictures
`scripts/watercolour_pictures.sh` (`WatercolourPictures`).

### Wet ink wash: Ink wash's washes on the wet canvas (2026-10-22, branch `inkwash-wet`)

The owner: "wet ink wash brush blending .. similar issue as the watercolor where there should be a bit more mixing, less
dark where they overlap, especially when wet". Wet on an Ink wash style Wash, or Ink with a body, is a wet wash too
(`ResolvedBrush.washes`), on the pair's wash half (`ModularStroke.layerOf`), with a pen line laid beneath its water.

| Upstream file | Lines | Why |
|---|---|---|
| `WetWatercolor` | +45 −6 | Hooks marked "Inkwash": `lineMask` (a wash's pen line, dry ink held stuck beneath the water: never moved, glazed over), `washMask(..., rows, line)` (reads each mask row between its first and last new column; lays a line as wide as the wash in its pass), `Wash.feather` (how far its water bleeds into paper; .85, as before, for watercolour). |
| `ModularPerfBench` (androidTest) | +1 | The wet bench gains the five Ink wash washes. |

Pure: `ResolvedBrush.washes`, `Wet.pull`, `Wet.line`, `wetBody` (an ink wash's wash under the pen at most 40 px past its
line, 180 px across), `WetCanvas.Footprint.take` (each row's first and last new column, `rows`; the mask cleared span by
span: the same masks, read faster), `WetPaintStroke` (`lineSweep`, `lineInWash`, `body`), `ToolPresets`; checks
`WetPaintChecks.inkWash`, `ToolPresetsChecks`; pictures `scripts/watercolour_pictures.sh <folder> "Ink wash"`.

### No bucket, Fill in and Page Colour (2026-10-23, branch `no-bucket`)

The owner: "maybe this app doesn't use a bucket, just a brush with fill in the lines" and "a notebook setting: default
page color? that'd be cool". The bar's paint bucket (Fill, 2026-10-19, above) is taken out: `InkWashBar`'s button and
`tapFill`, `InkWash.filling` and `fill`, `FillTool`'s tap rules and notes (`LABEL`, `HOLD`, `needsHold`, the heap
guard, `slot`, `tapWhileFilling`) and `DrawLayout.labSlot`; its cell stays, empty. `FillTool`'s engine (`region`,
`Areas`, `lay`, `start`, `brush`, `walls`) and `FillRegion` stay: Stay in Lines reads them, `FillChecks` keeps them
working. A Basic tool, Fill in (`ToolPresets`, Stay in Lines at Flood 1), and a kit, Colouring In, take its place.
Page Colour is `NotebookOptions.paper`, a `PCOL` record (`BookOptions`) and a recovery section (`TSRB`), only when not
white; a page added starts on it as its own paper tone (`ToneDocument.paperTone`, the page shade's), so the eraser,
pictures and copies already show it; `BlankPages` counts a page blank on the notebook's colour.

| Upstream file | Lines | Why |
|---|---|---|
| `DrawingPad` | +1 −70 | The 2026-10-19 Fill's `fillToolTap`, `layFill` and `previewFill` taken back out. |
| `DrawingBook` | +30 −4 | `blankPage` (a new page on the notebook's Page Colour, for `addPage` and a page not packed yet), `repaperBlank` (Page Colour changed: a stack all blank takes it; a page put away only when it is the blank page's own bytes), `addLayer` (a layer on its stack's paper). |
| `ToneDocument` | +2 | `setPaper`: the paper as a change to save, not an Undo step. |
| `PaintActivity` | +2 −2 | The page shown again when Page Colour changes; the blank page made ahead on the notebook's colour. |

### Filled Shape (2026-10-23, branch `filled-shape`)

The owner: "draw a filled shape, could we do that possibly?", as a brush, not a tool button. Smudge's seventh choice,
`PartChoice.SHAPE` (key `shape`, no dials; older codes never hold it, never rolled or swapped to), beside Stay in
Lines: both decide where a stroke's paint goes and neither moves paint along (`ResolvedBrush.carry` null). New, pure:
`FilledShape`, a `LiftingStroke` round the outline's stroke (the brush's own, or its `MirrorStroke`): it keeps the pen's
samples, and at the lift finds the area they enclose (`room`: each row's crossings, non-zero winding, a byte a pixel of
its box; Copy Strokes every image of it, Real Mirror each cell's share folded into the kept cell, `FillTool.keptSide`),
closes the outline with a sample at its start, lets the outline finish its own work after lift, then lays the area
through `FillTool.start(..., shape)` (the retired bucket's fill in strips, but on the outline's own layer,
`ModularStroke.fillShape`: a stick's or crayon's line as its press lays it, `LineStamper.press`, and the compose keeping
the darker of the outline and the fill, `ModularCompose.keepDarker`, so the two are one tone at any grey) and links the steps into one Undo
step. A dot or a short line (`tiny`: under 144 px or the brush's square) stays a stroke. The bucket's heap guard comes
back here (`PEAK_BYTES_PX`, older Undo steps let go, else the outline alone). `InkWash.stroke` (drawing screen and Brush
Lab's test page) and `replay` wrap strokes with `FilledShape.of`; `WildSample` fills loops, tray strokes and the Smudge
card's blob (`blob`). `PartCard` shows a sample for it though it has no dial. Web twins: `newmods.js` (choice, words,
code, checks; the pictures draw the outline alone, as `FillTool` has no twin there), `voice.js`, `codes.cjs`.

| Upstream file | Lines | Why |
|---|---|---|
| `DrawingPad` | +30 −4 | The retired bucket's `previewFill` back for a big shape (`FilledShape.PREVIEW_PX`); a shape's mirrored outline found for the live copies (`outline`). |

### Scribbler Style, phase A: the shared pieces (2026-10-23, branch `design-base`)

The owner's design pass (`docs/archive/design-pass-2026-10-23.md`, the component rules in `docs/DESIGN.md`, "Components").
Scribulor's own pieces carry it: `Theme` (`box` is grey-200 with no line, `menu` and `menuRow` for every menu and popup
over the page, `dialog` grey-200 with the act ink-filled), `TypeStyle.MENU` (12 sp), `LabButton` (primary ink-filled,
every other plain words, no `outlined` flag), `LabUi` (one header style, the grid a grey-200 band), `LoopCard` (words on
grey-200, the ink tab for the one in use, a toolbar slot ink-filled with `slot`), `SlotTile` (the slot in use
ink-filled, its mark on-ink), `PartCard` (no frame or strip), `SwapStrip`, `SizeModal`, `InkWashBar`'s
menus, `CardArt` (no ✓). The Tools page's blinking outline is gone with `LoopCard.outline` and `setNowColor`.

| Upstream file | Lines | Why |
|---|---|---|
| `PaintActivity` | +27 −32 | `menu` built from `Theme.menu`/`menuRow` (no row frames, 12 sp), closing through `DrawingPad.uncovered`; the page panel's close cleans its area (`cleanArea`); the rotate button square, in the menu's look; the last `Color.*` and bare text sizes as tokens. |
| `DrawingPad` | +24 | `cleanArea` (`uncovered` without the page's full refresh) and `cleanOffPage`: what a closed popup covered beside the page gets `SelectionFeedback.clean` from the window, so no menu leaves a ghost on the bar or the page bar either. |
| `SelectionFeedback` | +13 −15 | `cleanAfterClose` (the clean pass once Android has drawn under a closed popup); captures start on white, since the panel takes a pixel's red alone and a clear control showed black; the several-patch `update` (the Tools page's blink) removed with the blink. |
| `SettingsForm`, `Toolbar`, `ToolSettingsPanel` | +4 −4 | Their off-palette greys (`0xffeeeeee`, `0xffaaaaaa`, `0xffcccccc`) and rounded corners as `Theme`'s greys, square. MonoPaint's screens, still built and hidden. |

### Scribbler Style, phase D: the drawing screen's layout (2026-10-23, branch `design-canvas`)

The owner's design pass (`docs/archive/design-pass-2026-10-23.md`, "Drawing screen"): "moving the pages bar to the top. keep the
zen in the bottom left (align a bit better pls) and you can put the selected brush indicator to the right along the
bottom". `DrawLayout` holds the geometry. The page bar (`PageTurner`) sits in `pageArea` over the pad, the page's width.
The column runs from the top to Zen Mode's cell (`columnHeight` is the screen less `ZEN_CELL`, its view padded clear of
it). The foot row holds Zen Mode's corner (`lineStart` wide) and the brush line (`BRUSH_LINE`). `zenCell` is mirrored
exactly for a column on the right. The canvas is the same size as before (`canvasHeight`), so no page changes shape.
The strip's previews start `STRIP_EDGE` from the screen's top with their tab along the bar's foot, and a page's options
drop down from the strip. Inside a notebook the bar stays at the foot (`PageTurner`'s padding goes on the page's side).
The brush line's name takes a tap only over its words (`InkWashBar.onWords`), and the line is mirrored with the column
(`setLayoutDirection`). A stroke that starts on the brush line doesn't draw, as before: the line is outside the pad.
The rule over the layers and other layers' thumbnail edges are grey-400. Checked: `DrawLayoutChecks.screen` (every
screen, either hand: bar, canvas, column, Zen, line, no overlaps), `zen`, `fits`, `pageBar`, and on the device
`NomadUiChecks.layout` (each turn and hand).

| Upstream file | Lines | Why |
|---|---|---|
| `PaintActivity` | +15 −15 in `showInkWashBar`, `placeZen` | The page bar in `pageArea` over the pad. The foot row holds Zen Mode's corner and the brush line. Zen Mode's cell from `DrawLayout.ZEN_CELL`. |

### Scribbler Style: the side column pass (2026-10-23, branch `design-canvas`)

The owner: "some things are a bit off with it.. layers section is a bit janky.. inconsistencies". Scribulor classes only,
no upstream file. `DrawLayout` gains one inner margin (`edge`, the column view's padding), so `inner` and `half` are the
content's and every row sits on one axis. Faces are a grid of two cells (`PAIR_PAD`, `FACE_GAP` the same across and
down, `faceSide`), the grey strip spans the sizes' squares (`GreyStrip.rows`), and the size and swatch in use have a tab
under them (`SwatchFace.pair(leftCell)`). The tool buttons in use are ink-filled as a slot is, and rows are symmetric
(`SlotTile.row`, `draw`). The shared `SlotTile` changes touch only rows, which the drawing screen alone uses. `SwatchFace`
changes touch only paired faces. Groups are parted by `GAPS` gaps (no `RULES`), which share the spare a full stack
leaves, up to `GAP_MOST`. `LayerColumn` has a label (`LAYERS_LABEL`, `LAYER_LABEL`), one + (New Layer Above), and New
Layer Below in the layer drawn on's menu, the layer drawn on ink-filled. A blurring smudge brush shows the stump
(`smudgeIcon`). Checked: `DrawLayoutChecks.column`, `tray`, `order`, `layers`, `GreyBarChecks`. Desktop pictures:
`scripts/bar_pictures.sh` (4 layers).

### Page Settings (2026-10-23, branch `page-settings`)

The owner: "long press on a page will bring up a panel to adjust the settings for that page (similar to notebook
settings, with constraints, etc.)". The screen is Scribulor's (`NotebookScreens.fillPageSettings`, the rows
`LibraryCard.pageRows`, the rules `NotebookRules.forPage`). A page's own No Erasing and Timed Pages are kept in its bits
beside its steps (`DrawingBook.RULE_SHIFT`), so they move with the page wherever its steps do. Files gain `PRUL`
(`BookOptions`), the recovery `TSRR`, each only when a page has one, so other notebooks write byte for byte as before.
Checked: `NotebookRulesChecks.pageRules`, `LibraryCardChecks.pageSettings`.

A page's own time (2026-10-23, branch `page-layers`: "ideally it should be a slider"): the stop of
`NotebookOptions.SECONDS` in its bits beside its rules (`DrawingBook.TIME_SHIFT`, the rules now 4 bits, as
`validOwn` always allowed), kept with the page as the rules are (`OWN`). Files gain `PTIM` (the page count, two bytes a
page, its seconds), the recovery `TSRM` after `TSRR`, each only when a page has one, so every other notebook, one with
`PRUL` too, writes byte for byte as before. Older builds skip `PTIM`, and their recovery reader stops before `TSRM`. Such
a page then takes the notebook's time, or 5 min. Checked: `NotebookRulesChecks.pageSeconds`.

| Upstream file | Lines | Why |
|---|---|---|
| `DrawingBook` | +48 −4 | `pageRules`, `setPageRules`, `rules(page)`, `restorePageRules`, `ownTemplate`, `paperTone`. The rule bits are masked out of `tracing()` and kept by `restoreTracing`, and go to the layer that becomes a stack's bottom (as its template). `Snapshot.pageRules`. |
| `BookCodec` | +2 −1 | The snapshot's page rules to `BookOptions`, read back with `restorePageRules`. |
| `RecoveryCodec` | +12 −1 | The `TSRR` section, and `TSRM` (a page's own time) after it. |
| `PaintActivity` | +41 −8 | `rules()` is the page drawn on's (`rules(page)` another's, for Delete Page and Delete layer). `setPageRules`, `shadeStack` (Page Colour, through `history` as Fill page). `clearPage` package-private. The Pages grid's long press. Brushes on Bar's write-back uses the notebook's options, not the page's. |
### The side column: side bars, the Fun button's two, Menu level with the page bar (2026-10-23, branch `column-tweaks`)

The owner, on the Manta: side lines instead of underlines, more room between the brushes, the swatches as wide as the
grey strip and lined up with it, the eraser beside the smudge tool, Magnetic Poetry and Mirrors as buttons with no Fun
button, Menu level with the page previews, the brush line clear of Zen, Zen dotted while off, the wet drop not always on,
no LAYERS label. Scribulor classes only, but for two upstream-file edits. `SlotTile.row` keeps `DrawLayout.barRoom` on
the side its bar is on (`Face.barLeft`), the bar drawn there (`SIDE_BAR`, `BAR_GAP`). Tray rows `TRAY_TOP` over and
under, 4 dp taller. `SwatchFace.pair` and `GreyStrip` keep `faceMarkRoom` beside the square, the strip a square wide.
`FunMenu` became `MirrorsMenu` (`app.mirrors`), `InkWashBar.poetry` and `mirrors` are tool cells (`modesChanged`).
`DrawLayout.menuRow` centres Menu on the previews (Menu out of the `ICON` count), `LINE_GAP` parts the brush line from
Zen. `WetIndicator` draws its dry drop dashed (Phosphor's drop path, Bold width, `DRY_DASHES`), so `ic_ph_drop` is gone.
`DrawLayout.MARK` stays for the Tools page and Brush Lab. Checked: `DrawLayoutChecks` (`column`, `tray`, `order`, `fun`,
`screen`), `GreyBarChecks`, on the device `NomadUiChecks.layout` (Menu level with the previews, the line's gap).

| Upstream file | Lines | Why |
|---|---|---|
| `PaintActivity` | +4 −4 | `mirrors` (`MirrorsMenu`) in place of `fun`. Zen Mode's icons swapped. |
| `DrawingPad` | +2 −2 | `app.mirrors.placed`. |

### The side column's extras: Left-Handed, Template Visibility, a dirty brush shown (2026-10-23, branch `column-extras`)

Scribulor UI only. `DrawLayout.GRID_ROWS` is 4: the fourth row is `InkWashBar.lassoCell` (an empty `View`, kept for the
Lasso, branch `upstream-lasso`, which puts its button there in its place) and `InkWashBar.template` (Template
Visibility, `DrawLayout.TEMPLATE`, `showTemplate`, `templateMenu`), placed and sized with the other cells in `place`
and `showModes`. `TIGHT`'s icon rows went from 33 to 28 dp so the folded column (the Nomad in landscape) still holds
it. `PaintActivity.showGuides` is the one way the guides' step is set (the menu's Template row, the button, its menu).
The drawing hand: `DrawLayout.LEFT_HANDED`, a file menu row toggling `PaintPreferences.toolboxRight` then
`applyToolboxSide`. A dirty brush: `InkWash.dirty(tool)`, `heldGrey(tool)`, `clean(tool)` for any slot and the smudge
tool (were `smudgeDirty` and `cleanSmudge`), `HeldPaint.Reservoir.darkness` (engine, pure: the paint's own darkness),
`DrawLayout.heldGrey` (snapped to the six greys, never paper), `SlotTile.dab` and `Face.held`, the smudge icon's head
tinted (`InkWashBar.heldBrush`, `ic_dirty_brush_paint` under `ic_dirty_brush`, `ic_dirty_brush_held` gone), a dirty
slot's long press `cleanMenu`. Checked: `DrawLayoutChecks.extras` and `order`, `WetSmudgeChecks.dirty`, on the device
`ScreenShots` (Template Visibility, its menu, Left-Handed).

| Upstream file | Lines | Why |
|---|---|---|
| `PaintActivity` | +15 −1 | The menu's Left-Handed row, `showGuides`. |

### The Lasso: cut and paste on Scribulor's pages (2026-10-23, branch `upstream-lasso`)

MonoPaint's Lasso (`5f764ec`) on Scribulor's model. `LassoPlanes` (engine, pure Java, in `scripts/test_inkwash.sh`) lifts
what the selected pair's wash and ink (`InkWashLayers`) and the masking fluid hold inside the loop, by role, every
plane: tones, coverage, `Layer.share`, `Layer.print` and kinds (`kindsAt` with `permanentAt`), so a moved piece looks
and erases as it did. Placing is one Undo step of linked gestures (`finishWithPrevious`): the lift cleared where any
plane had coverage, then each role laid on the destination pair's layer as a sheet over what is there (nearest pixels,
as the screen shows it). `FloatingSelection` keeps the pose; its `tones`/`alpha` are the piece's look (ink over wash),
and `planes` holds the rest. The screen shows it through `DrawingPad.render` (`LassoPlanes.shown`: the pair bare where
lifted, the look over that), not the native `SelectionPreview`, which knows none of Scribulor's compositing.

- **Scribulor UI.** `InkWashBar.lasso` in the fourth row's left cell (column-extras' `lassoCell`), Phosphor `lasso`
  (`ic_ph_lasso`). `DrawingPad.lassoOn`: the pen's strokes are loops in Cut Stamp's ants (`MirrorShow.Place`,
  `Placing`), lifted at the end (`liftLasso`). Copy (`copySelection`), Delete (`deleteSelection`) in `PaintActivity.menu`;
  Cut, Paste and the way that deletes since 2026-10-25 (below, "Lasso options").
- **Not at every `finishStroke`.** Upstream puts the piece down in `finishStroke`, which Scribulor calls from menus,
  focus loss and most buttons; in Scribulor only the pen off the piece, another tool (`InkWashBar.choose`,
  `selectTool`), a notebook change (`replace`), No Erasing and Undo end it. The page is untouched while it floats, so
  saves and a killed app lose nothing.
- **Another page.** `carrySelection`: a page turn or layer switch (layers are pages) takes the piece along as a copy
  (`FloatingSelection.copy`, lifted from nowhere). The page left is never cut: its Undo may be gone once it is packed
  (`DrawingBook` keeps two pages decoded), so cutting there would risk the paint. A page of another size drops it.
- **Import Image stays Scribulor's** (Android's picker, laid on the layer drawn on, `PicturePlacer` with
  Cancel and Place, the panel's fast outline while it moves). Upstream's import (its own picture browser, fit or own
  size and quarter turns in a dialog, floating as a Lasso piece onto an in-page layer) would lose the picker and the
  placing frame. PDF import unchanged.
- **Upstream files touched:** `ToneDocument` (+75: `copyPlanes`, `layKinds`, `layPixel`, `liftPixel`, `lay`, the
  `sheet` formula `overTone` now shares), `FloatingSelection` (+60 −25: `lift` split into `inside`, `found`, `crop` and
  `piece`, which `LassoPlanes` shares; `copy`, `samePose`, `at`, `planes`; `place` hands Scribulor pieces to their
  planes), `DrawingPad` (the lines above, `showErased` taking any `MirrorShow.Marks`), `Ui` (`closesPanel`'s palm),
  `PaintActivity` (`undo` drops a floating piece first). Generic: `LassoPlanes` could move every plane of a MonoPaint
  layer the same way.
- **Checked:** `SelectionChecks` (upstream's, unchanged) and `LassoChecks` (every plane of both layers and the fluid
  carried, a loop's shape, quarter turns, Undo and Redo, Delete, Copy, another page and a one-layer page, the preview
  matching the placed page, a file round trip). Not tried on a device: dragging a large piece re-renders its area each
  frame.

#### The PDF in Documents (2026-10-24, branch `better-pdf`)

The owner: "a cool pdf that lives in the documents section and might be searchable". No upstream file's logic touched:
`PaintActivity.EXPORTED` (Inkwash's note) says the new folder.

- **Where.** `NotebookPdf` (new, pure Java) writes `Document/<LIBRARY_FOLDER>/<title>.pdf`, beside the notebooks
  (`DrawingStorage.shared()`, so `AppRename` carries it over with the library on a rename). The title is the stored name
  without a title's hidden date. EXPORT keeps the PNGs (Drive's pictures, and the PDF's page cache), no PDF: the old
  `EXPORT/<app>/<name>.pdf` is removed once the Documents one is written (left if a `.mark` is beside it). Export Now
  writes the same file. `AutoExport.hasCopies` now means "has `.pdf.txt`", so a notebook from before is copied once on
  opening.
- **What it holds.** Each page at its pixels at 300 ppi (1:1 on the tablet), its PNG's image data as is (Flate with the
  PNG predictor). `NotebookExport.png` now writes a palette of the page's own greys at 1, 2 or 4 bits when a page has 16
  or fewer, else 8-bit grey as before: lossless either way (`[/Indexed /DeviceGray]` in the PDF). Brush strokes are
  antialiased, so most drawn pages stay 8-bit. Covers and plain pages shrink. An invisible text layer (`3 Tr`, Courier,
  WinAnsi, not embedded: Courier's 0.6 em advance is Roboto Mono's, so a magnet's word spans its tile): title and page,
  days, template, Daily Prompt card, each magnet's word on its tile's baseline, turned with it. Outline: Cover, then a
  day at a time (closed) with its pages. Page labels, Info (Title, Author and Producer from `AppName`, Subject the
  template, Keywords, days at noon), and `/Notebook` (the stored name) in the Info, written first, so the app can tell
  its own PDF from its first 4 KB.
- **Days.** Pages carry no dates, so the PDF's record (`EXPORT/<app>/<name>/.pdf.txt`, `NotebookPdf.State`) keeps each
  page's key, made and changed days and words. A kept PNG keeps its days. A redrawn page is changed today and keeps the
  made day of the page that was at its place if that one is gone. A PNG from before the record takes its file's day.
- **Handwriting.** The Supernote keeps a PDF's ink in `<name>.pdf.mark` beside it, by file name and page number, and
  makes one on opening. A PDF is rewritten in place only while pages keep their numbers (`keepsNumbers`: changed in
  place or added at the end). Otherwise, with a mark there, a new `<title> 2.pdf`, the marked one left. A file is the app's
  only when the record names it at the length it wrote (or, with no record, its info says so). Other files and lone
  marks are passed over (`free`). Rename moves the PDF and its mark. Delete removes the PDF only without a mark. An
  unchanged book isn't rewritten (sync sees nothing).
- **Speed and memory.** Only changed pages are drawn (as before, and now pages alike, such as blank pages between drawn
  ones, keep a PNG each instead of being drawn every time). The PDF reads one PNG at a time.
- **Checked:** `NotebookPdfChecks` (its own small PDF reader: the table, pages, labels, outline, info, words and their
  positions, greys decoded at 1, 2, 4 and 8 bits, incremental drawing, days, handwriting, other files, rename, delete,
  250 pages in a 40 MB JVM). By hand on the desktop: `qpdf --check`, `pdfinfo`, `pdftotext -bbox`, Ghostscript (greys
  within 1 of the PNGs, its colour rounding), PDFKit's search and outline. `PdfSample` (test helper) writes a sample
  Manta notebook. Not tried on the tablet yet: whether Documents lists it at once, search, and the mark policy there.

#### Three owner bugs (2026-10-24, branch `bugs-1024`): upstream files touched

- **The tray's pull turned the page.** The Supernote's tray is a finger pulled down from the screen's top (Android's
  start band on the Manta: 69 px, 37 dp, logcat's `SystemGestures`). The page bar sits there, and a pull past 8 dp on a
  preview was a riffle that turned to the page under the finger on lift. `PaintActivity.dispatchTouchEvent` now follows
  a finger landing within `StripGesture.SYSTEM_EDGE_DP` (40, the bar's height): on the move that decides it
  (`StripGesture.edgePull`, the riffle's own 8 dp), a pull mostly down is cancelled for every view and swallowed to its
  lift. A tap, or a drag along the bar, goes on as before. The notebook screen's swipe already needs a horizontal fling.
- **A Brush Lab ghost through the system's refresh.** `DirectEink` writes into the panel's shared plane, which the
  firmware's full refresh shows again, and a session sends only pixels changed against its own seed. So pixels another
  screen left there stayed over an unchanged page. `DrawingPad.returned` (every `PaintActivity.onResume`): the next
  `connectDisplay` blanks the fast plane where there is one (`eraseFastInk`, now skipping mode 9 where the firmware has
  none, which logged a stack trace on every popup close on the Manta) and shows the whole window again, forced (the page
  through `direct`, the rest through `cleanOffPage`). `BrushTestPad.disconnect` likewise marks the pad for a forced full
  refresh on its next connect (the Tools page after the Lab). `ToolsActivity.onPause` closes its grey bar's kept
  `SelectionFeedback` session.
- **The Lasso with the eraser selected.** `DrawingPad.beginInkStroke` took any eraser stroke as the pen's eraser end, so
  with the Eraser the tool in use the Lasso's loop never started. It now takes the eraser end itself
  (`DrawLayout.lassoLoop`), and `InkWashBar.tapLasso` reads `DrawLayout.lassoTap`.
- **Upstream files touched:** `PaintActivity` (the edge guard, `returned` on resume), `DrawingPad` (`returned`,
  `eraseFastInk`'s mode 9 test, `beginInkStroke`'s eraser end). **Checked:** `StripGestureChecks.edge`,
  `DrawLayoutChecks.lasso` (from every tool, twice, after Undo and a page turn, another tool).

#### The size slider and the Dropper (2026-10-24, branch `size-slider`): upstream files touched

- **The size slider.** The side column's four size faces and four grey swatches go (the owner: "change the four size
  options to a quick size slider ... grey swatches can go"). `SizeSlider` (new, Scribulor UI) stands in the left cell,
  six rows of faces long, on `ToolSwatches.position`'s log scale. Its thumb moves through `StripMarker`, as the grey
  strip's square does. It sets the slot's own px (`ToolSwatches.useSize`), the eraser one of its engine's four
  (`ToolSwatches.eraserAt`). `SwatchBar` holds the slider, the strip and the Dropper, and `SwatchFaces` stays for the Tools
  page and Brush Lab, marking no size when a slot draws at a size of its own (`ToolSwatches.sizeInUse`). Nothing in the
  stored keys changed: a slot kept as faces reads as it drew.
- **The preview.** `DrawingPad.sizePreview` draws `HoverNib`'s ring beside the column at the page's scale through the
  hover ring's overlay (`RasterOverlay`, the fast path), with the size in words in a paper box upright on the screen,
  and on lift takes it off and cleans its patch (`cleanArea`).
- **The Dropper.** `DrawingPad.dropperOn` reuses MonoPaint's eyedropper gesture (`sampleShadeGesture`, `pickPointer`):
  the pen picks `ToneDocument.shownTone` (new, read only: `visibleLayers`' layers with the stack's planes on the paper,
  the guides left out), a disc of it drawn over the pen by the same overlay (`drawDrop`), hovering too. The lift hands
  it to `InkWashBar.dropped`, which sets the tool's grey (`ToolSwatches.drop`, `ModularSizes.laying`: `swatch`'s
  inverse) and puts the Dropper down. The tool in use is never changed.
- **Upstream files touched:** `DrawingPad` (+120: the preview, the Dropper's ring and gesture branch, the outline's tone
  and words), `ToneDocument` (+15: `shownTone`). **Checked:** `ToolSwatchesChecks.columnSlider`, `sliderSlots`,
  `dropper`, `DrawLayoutChecks.tray` and `order`. `ScreenShots` draws the slider's preview and the Dropper and picks a grey.
- **The column's polish (2026-10-24, branch `column-polish`).** The owner: "the redraw is blinky and slow", "the
  selector is offset", "put the size selected and the current shade below ... it can be one thing", "move eyedropper to
  beside wet indicator". `SizeSlider` is now a static right triangle with the strip's tab right of it, and its
  `StripMarker` area is the tab's strip alone (`DrawLayout.sliderMarkRoom`), ink and paper, so `SelectionFeedback`
  sends it in the fast binary mode. The thumb across the grey wedge had made each move a grey update of the whole cell,
  and the preview added a patch of the page. `DrawingPad.sizePreview`, the outline's words and `DROPPER_LIFT` are gone.
  `drawDrop` draws the Dropper's ring round the tip, a hole in its middle (`DROPPER_HOLE`). `SwatchReadout` (new,
  Scribulor UI) is one dot under the slider and the strip, the size in the grey, drawn only when they change. The
  Dropper moved to the wet indicator's row in `InkWashBar`. **Upstream files touched:** `DrawingPad` (about −60: the
  preview and its words out, the ring centred). **Checked:** `DrawLayoutChecks.tray` (the tab's room, a drag's panel
  area against the old one, the readout, the Dropper's cell), `ToolSwatchesChecks.columnSlider`,
  `NomadUiChecks.layout` (the readout under the slider and the strip, the Dropper beside the wet indicator).

#### The refocus: keep, tuck, cut (2026-10-24, branch `removal-pass`): upstream files touched

The owner's marks (`docs/archive/refocus-marks-2026-10-24.md`): a paper notebook first, a little mischief second.

- **Deleted, upstream files:** `AppSettingsDialog`, `PaletteEditor` (MonoPaint's settings window and palette editor, never
  reachable in Scribulor). Their hooks went from `PaintActivity` (`appSettings`, `paletteSettings`, `savePalette`, the
  editor's touch and colour hooks), `DrawingPad` (one guard), `SidePanel` (one close) and the hidden `Toolbar` (its
  palette button, `paletteAnchor`, `showPaletteShades`). An upstream merge touching those files will conflict there:
  keep them deleted. The device suites that drove them (`SettingsUiChecks`, `PaletteUiChecks`) are deleted, and
  `NomadUiChecks` turns the simulation on directly.
- **Deleted, Scribulor:** `LayerColumn` (the side column's layers; Page Settings' Layers row now), the Lock (`NotebookOptions.Lock`,
  its screen, the seal mark), Guides in PDF (`printGuides`), the slots' dab and gauge on the column, the Finger button.
- **Engine (c), small:** `ModularCode.decode` reads a Hand Hatch tip as Hatch (`asHatch`) and `ModularFeatures.offered`
  never offers it. The engine's own Hand Hatch path (`HandHatchStamper`, its branches in `ResolvedBrush`,
  `ModularStroke`, `QuickStroke`) is untouched, so as not to cross the white-ink work, and is now dead: remove it in a
  pass of its own.
- **File formats:** unchanged writers but for what is no longer written. `BookOptions` passes `GPDF` and `LOCK` by;
  `RecoveryCodec` still writes its rules' fourth array (empty) and its options' second (`{1}`) so the layout stays,
  and reads past both. Checked: `NotebookRulesChecks.lockCut` (files and a recovery as older versions wrote them).
- **Upstream files touched:** `PaintActivity` (the menu in groups with `divider`, `playMenu`, `playBrush`, Export Now
  the one export path, `leaving` in place of `exportCopy`, the lock's guards gone), `DrawingPad` (one line).

## One grey (2026-10-24, branch `one-colour`)

The owner's "procreate method": one current grey for every brush. Scribulor's own classes only, no engine change.
- **Model:** `ToolSwatches.CURRENT` (`inkwash_current_grey`, a darkness as `inkwash_dark_` keeps it) is what `look`
  gives every key but the smudge tool's (its own, as before) and the eraser's (none). `useGrey(darkness)` and
  `drop(brush, tone)` set it. `ToolSlots`' constructor runs `startCurrent` once: the selected slot's grey (the stamp's,
  else the first slot's) by the old per-key rule (`own`). The per-key greys (`inkwash_shade_`, `inkwash_grey_`,
  `inkwash_dark_`) and a notebook toolbar's darkness column are still written and read as before, painted at by
  nothing. `Toolbar.sameAs` compares brushes and sizes only. Gone: `chooseGrey`, `setGrey`, `greyInUse`,
  `InkWash.swatch`, `InkWash.tile`, the grey faces of `SwatchFaces`/`SwatchFace` and `SizeModal`'s grey editor.
  Grey Shift no longer writes the slot's grey (`InkWash.shifted`).
- **Previews:** `WildSample.slotTray` (was `InkWash.tray`) draws at `WildSample.PREVIEW` whatever the look's grey.
  `WildSample.darkGround` (Blend's White) is the one dark ground. `LabLibrary.sampler` takes no darkness.
- **Upstream files touched:** none. The stroke engine is untouched, so a stroke at a given grey draws as before.
- **Checks:** `ToolSlotsChecks.oneGrey` and `greyMigration`, `WildSampleChecks.neutral`. The per-slot grey checks in
  `ToolSwatchesChecks`, `GreyBarChecks`, `ToolsPageChecks`, `BrushLabChecks`, `NotebookOptionsChecks` moved over.

## One size (2026-10-25, branch `one-setting`)

The owner: "stop remembering per tool size and shade and use only the current setting". The grey was one already
(above); now the size is too, for every slot, the stamp tool, the smudge tool and the eraser. Scribulor's own classes,
no engine change.
- **Model:** `ToolSwatches.CURRENT_PX` (`inkwash_current_px`) is the px `look` gives every key; the eraser's is kept no
  finer than `ERASER_LEAST` when read, so a fine brush size never makes a 1.5 px eraser. `useSize(px)` (the slider),
  `chooseSize(i)` and `setSize(i, px)` (the Tools page's and Brush Lab's four faces) set it. `startCurrent` takes the
  selected slot's old size (by `own`, the old per-key rule) once, as it does the grey. A first launch writes 44 px
  (`ToolSlots.FIRST_PX`, a number now; the owner's toolbar was 44/44/44/44/4.5/360/44/182 and the eraser 24).
- **Eraser:** shares the size (the owner's words cover every tool). This undoes 2026-10-24's "eraser size stays same
  size for eraser" (`ERASER_PX`); the earlier problem it fixed, the slider snapping the eraser to its four, stays fixed.
- **Gone:** each key's size writes (`inkwash_size_`, `inkwash_px_`, `inkwash_eraser_px`) and the last grey writes
  (`inkwash_shade_`, `inkwash_dark_`, `inkwash_grey_`, from `keep`, `keepValues`, `keepLook`), so `brushChanged`
  (a brush of the other family took its own nearest size), `lookAfter`, `keptSize`/`restoreSize` (Undo's size),
  `keptLook`/`restoreLook` (Back to the start's size and grey, `ToolSlots.Start.looks`, `ToolsPage.Step.looks`,
  `BrushLab.Step.look`), `BrushLab.fitSize`, `useEraserSize`, `eraserAt`, `px(brush, i)`, `sizeIndex`, `greyIndex`,
  `ToolSlots.use`'s and `useAll`'s `keepSize` and `PaintActivity.slotsReturned`'s `sized`. The old keys are still read
  by `own`, never written, so a launch reads them once and an older build still reads what it wrote.
- **Notebook toolbars:** the file format is unchanged: `Toolbar.lines` still writes each slot's px and darkness (now the
  current ones) and `parse` reads them, but `load` takes the brushes only and `sameAs` compares brushes. A notebook's
  toolbar no longer brings its sizes. `SurpriseTools` still fills those columns.
- **Old installs:** the six-part conversion (`ToolSlots.convert`) converts brushes only; a converted slot no longer gets
  its old px and darkness written, so `startCurrent` reads the selected slot's old swatches with its new brush.
- **Toolbar backup** (`ToolBackup`, never read back): the slot lines lose their size column; one `size` line takes the
  place of the eraser's.
- **Shown:** `SwatchFaces.show()` and `SwatchFace.show` take no tool; the eraser's faces are the shared four, and can be
  set. `SwatchBar`'s slider sets `useSize` for any tool. Share codes, brush keys and names are untouched.
- **Brush Lab:** it had no size or grey of its own, only the slots' (`ToolSwatches`); it follows the same rule.
- **Upstream files touched:** `PaintActivity` (`slotsReturned` without `sized`, two `use` calls without `true`).
- **Checks:** `ToolSwatchesChecks` (`choose`, `eraser`, `picks`, `sliderSlots`, `everyTool` rewritten for one size;
  `keep` and `eraserAt`'s gone), `ToolSlotsChecks` (`start`, `grown`, `converted`, `interrupted`, `oneGrey`),
  `ToolBackupChecks`, `ToolsPageChecks`, `BrushLabChecks`, `NotebookOptionsChecks.toolbar`, `ToolKitsChecks`.
  `ScreenShots` and `LiveStrokeBench` (androidTest) set the one size or grey.
- **Then, the eraser's and the smudge tool's own size again** (2026-10-25, branch `eraser-size`; the owner: "eraser keeps
  its own size i think.. whatever the procreate convention is"). Procreate keeps the eraser's and the smudge tool's
  sizes apart from painting's, so `look` gives them `own`'s px: the eraser's `ERASER_PX` (no finer than
  `ERASER_LEAST`), the smudge tool's `inkwash_px_SMUDGE`. `useSize(key, px)` (the slider, `SwatchBar`) writes theirs while
  they are picked, the one size for any other key. A first launch sets the eraser to 24 px (`ToolSlots.FIRST_ERASER_PX`).
  Neither key was ever deleted, so a tablet that ran the one-size build gets back the sizes they had. The toolbar
  backup has its `eraser` line again. The Tools page's and Brush Lab's faces are the brushes' only. Checks:
  `ToolSwatchesChecks.eraser`, `choose`, `everyTool`, `ToolSlotsChecks.start`, `ToolBackupChecks`.

## Fast pickers (2026-10-24, branch `fast-pickers`)

The owner, on the Manta: "the size thing is still blinking when i change it", and "monopaint's shade selector is
TOTALLY fast ... both the shade selector and size selector are slow". Then "maybe also speed up the stroke selector".

**How MonoPaint's colour bar marker is fast** (`upstream/main`: `ShadePicker`, `SelectionFeedback`, `PaintActivity.updateColorBar`,
`78e7673`). Every move of the pen calls `selectionFeedback.update(shadePicker, markerArea(), () -> paint.gray = value)`.
That draws the picker into a bitmap before and after the change (only its 8 dp marker gutter), finds the changed
pixels and hands that patch straight to the panel driver through a kept session (`DirectEink` here, `fasteink` upstream).
The change itself only sets a field: no `invalidate`, so Android draws no frame. The patch is kept in `pending` and
invalidated at Android's next frame for some other reason (`syncBeforeDraw`), so Android's surface catches up later.
Which panel mode: the fast binary pen plane where `Device.fastBinaryPlane` names one (the Nomad, and firmware from
2610091001). On the owner's Manta firmware (Chauvet 2606141001) there is none, so every patch is a mode 7 grey update.
With the fast plane a clean grey pass follows 250 ms after the lift. Without it, `saveToolState` invalidates the gutter
on the lift.

**What Scribulor did on top, found with `PickerBench` on the Manta** (2026-10-24, before is `77c10b83`):
- The grey strip never picked. With no long press its own `View.onTouchEvent` refused the pen's down, so the moves and
  the lift never came: the square jumped to the pen and stayed, the grey in use and the dot never changed (the owner's
  "the dot color doesn't reflect the shade selected"). It keeps the whole gesture now.
- Every control in the column was clipped by one pixel: the cells (`tile`, 2 × 60 px) were wider than the column's
  inside (135 − 8 − 8 = 119 px at 1.875 px a dp). A clipped control fails `PanelCoordinates.fullyVisible`, so every
  `update` fell back to an Android invalidate. The strip's moves, the slots' marks, everything in the column went
  through Android. The far margin now takes the rounding (`InkWashBar.arrange`).
- An Android redraw of a column view, even of unchanged pixels, drove the panel 34 frames, about 500 ms: the firmware's
  default update for an app's frame is two passes, the blink. The column and the brush line now ask the firmware for
  partial grey (`View.setEinkUpdateMode(3, 7)`, as `NativePen` does for the page, `InkWashBar.partialGrey`, left at
  the firmware's default under Settings' Standard Android drawing). An unchanged redraw then drives nothing, a change
  one 21 frame grey pass. So the redraw `syncBeforeDraw` does later costs nothing. The bar's auto refresh, which leaned
  on that two-pass redraw, now gets `SelectionFeedback.clean` (forced mode 7) for the column and the line.
- On a lift `SwatchBar.show` presented the whole bar and `GreyStrip.show`, `SizeSlider.show` and `SwatchReadout.show`
  each called `invalidate()`, an Android frame at once. They only set fields now, and the bar's change is one present
  of what changed (the tab, the dot).
- A slot's stroke, drawn again after a size change, called `iconOnly` (an invalidate). It goes through `update` with
  `ToolButton.faces` now. The brush line's name (`Caption`, quiet while its update runs) and preview (drawn into the
  bitmap it shows, cached by what it shows) do the same.
- A slot tap: the Button's Material ripple (Android frames while it ran), the press outline (a grey update on the down
  and another 200 ms after), then the marks, the size and the grey as separate presents. The panel runs grey updates one
  at a time, so each was about 270 ms more. Now no background and no press outline on the tray's rows and tool cells
  (the mark is the answer), and the marks, the tab and the dot gather into one present over the column
  (`SelectionFeedback.gathering`), the brush line one more.
- The slot's name label (and the strip's page shade offer) closing called `DrawingPad.uncovered`: a forced mode 7
  refresh of the whole page and an Android redraw of the whole column, 1.5 s after every slot tap. It cleans what it
  covered alone now (`cleanArea`).

**Measured** (`PickerBench`, the checks build on the Manta, Chauvet 2606141001, 24 moves a drag, panel frames
from `fdec0000.ebc` in `/proc/interrupts`, about 15 ms each, "quiet" is the lift to the panel's last frame):

| Gesture | Before (`77c10b83`) | After |
|---|---|---|
| Strip drag | no grey picked, each move an Android redraw of the whole 60 × 224 px strip | 1 mode 7 present a move, 8 × 29 px, 0 Android frames, lift: tab and dot, 36 frames, quiet 390 ms |
| Size drag | 1 mode 7 present a move, 10 × 28 px, lift: Android redraws (slot row, whole column after the label), 70 frames, quiet 790 ms | the same a move, 0 Android frames, lift 37 frames, quiet 415 ms |
| Size tap | 70 frames, quiet 800 ms | 37 frames, quiet 420 ms |
| Slot tap | 3 presents + 6 Android redraws (5 rows), 65 frames, quiet 740 ms, then a whole page refresh 1.5 s later | 2 presents (column, line), 69 frames with the name label opening, quiet 790 ms, no page refresh |
| Unchanged Android redraw of a column view | 34 frames, 510 ms | 0 frames |

The slot tap's remaining frames are mostly its name label, an Android window the firmware draws (the owner asked for
it, 2026-10-22). MonoPaint on this device was not measured (it was not used during the run). Its code path on this
firmware is the same as the strip's now: one mode 7 patch a move, nothing through Android until the lift.

- **Upstream files touched:** `SelectionFeedback` (`gathering`), `ToolButton` (`faces`), `PaintActivity`
  (`setAndroidDrawing` sets the column's firmware mode again). `DrawingPad` untouched.
- **Layout (the owner, same day):** the strip left and the slider right, both flipped (white and broad at the top), the
  wet indicator on the brush line after the name (`DrawLayout.LINE_NAME`), the Dropper and the Lasso in its old row,
  Play and Template Visibility one row (`GRID_ROWS` 2).
- **Checks:** `DrawLayoutChecks` (the rows, the drop after the name, the column's heights), `ToolSwatchesChecks` (the
  flipped slider), `NomadUiChecks.layout` (the cells, the drop on the line, nothing clipped). Bench: `PickerBench`
  (`-e pickerBench true`, the checks build only).


## Binary size slider and the owner's notes (2026-10-24, branch `notes-1024`)

The owner's handwritten notes on the Manta, the same day: "size picker is still blinking. make it an outline shape
instead? why is it redrawing?" with a sketch ("DO IT THIS WAY": the dot on top, the triangle pointing down on the left
with its tab on its left, the strip on the right with its tab on its right), then "shade picker is fine, it's just the
size one". Also: the eraser on its own row, no lines between the tray's strokes, Zen as an unfilled and a filled circle,
one frame weight on the page bar, the eraser's size its own, a subtler hover ring, the slot's name as words alone.

**Why it still blinked** (`PickerBench` on the Manta, Chauvet 2606141001, before is `8342f2ca`). "Fast pickers"
took every Android frame off the pick, but what was left still went to the panel as grey updates (mode 7): each of the
24 moves of a drag one 10 × 28 px mode 7 update, and the lift two or three more, the column's (the tab and the dot,
103 × 41 px), then the slot's row (its stroke drawn again at the new size, in the background) and the brush line's
preview (the same). On this firmware a grey update runs 18 to 21 frames and drives a changed pixel through its grey
waveform, which shows as a flash, and the panel runs them one after another: the lift was 31 to 32 frames, quiet
about 350 ms after it. The strip's drag is the same mode 7 a move (the owner: "fine").

**What changed.**
- `SelectionFeedback.updateBinary` (upstream file, +12): an update whose changed patch is all ink and paper goes through
  the panel's binary update, MonoPaint's fast plane (mode 9) where `Device.fastBinaryPlane` names one, else
  `Device.fastStrokeMode` (driver mode 5 on this Manta firmware, 8 frames, the pen stroke's), else mode 7 as before. A
  patch with any grey in it stays mode 7. `StripMarker.binary` (upstream file, +2) sends its marker's moves that way.
- `SizeSlider`: an ink outline of a triangle pointing down, no fill, no anti-aliasing, its tab on its left. Every move is
  a mode 5 update of the tab's strip.
- `SwatchReadout`: no anti-aliasing, so the dot (its grey as the panel's dots, its ink edge) is ink and paper. The bar's
  change on a lift (`SwatchBar.show`) is `updateBinary`: the tab and the dot in one mode 5 update.
- `InkWashBar.swatchChanged`: a size or grey picked changes the slider, the strip and the dot alone. The slot's row and
  the brush line's preview show the brush at its Medium size whatever the slot's size (`ToolSwatches.picture`), so
  neither is drawn again.

**Measured** (`PickerBench`, the checks build, 24 moves a drag, two rounds, panel frames from `fdec0000.ebc`):

| Gesture | Before (`8342f2ca`) | After |
|---|---|---|
| Size drag, a move | 1 mode 7 present, 10 × 28 px | 1 mode 5 present, 8 × 28 px |
| Size drag, the lift | 2 to 3 mode 7 presents (column 103 × 41, row, line), 32 frames, quiet 340 to 350 ms | 1 mode 5 present (the dot, 44 × 44), 13 frames, quiet 127 to 147 ms |
| Size tap | 31 to 32 frames, quiet 358 to 384 ms | 11 frames, quiet 138 to 149 ms, all mode 5 |
| Strip drag, a move | 1 mode 7 present, 8 × 29 px | the same (unchanged by the owner's word) |
| Strip drag, the lift | 1 mode 7 present, 29 to 31 frames, quiet 338 to 340 ms | 1 mode 5 present (the dot), 16 frames, quiet 179 to 196 ms |
| Android frames during and after either drag | 0 | 0 |

No grey update is left on the size slider's path, the dot included. Not measured: residue from mode 5 over many moves
(the panel is the judge, a forced mode 7 clean of unchanged pixels drives nothing on this firmware). Device time about 15
minutes, the checks app and its folder removed after.

**The other notes.**
- The eraser's own size (`ToolSwatches.useEraserSize`, `ERASER_PX`): the slider snapped the eraser to its four sizes,
  so the lower two thirds of it all meant S and the eraser "stayed the same size". It now keeps the px the slider sets,
  4 to 360, under its own key (its old `PX` key held a brush swatch's size and was never read), whatever the brushes'
  sizes and the tool picked. `EraseBothStroke.pen` and `InkWashSizes.diameter(float most, …)` take that size,
  `HoverNib.eraser(most)` its ring. Choosing one of its four on the Tools page sets that size.
- The eraser on its own row (`DrawLayout.GRID_ROWS` 3): the eraser, the smudge tool, Play and Template Visibility.
- No hairline between tray rows (`DrawLayout.SLOT_LINE` gone, 24acb8e's lines undone).
- Zen Mode's button: `ic_ph_circle` while off, new `ic_ph_circle_fill` (Phosphor circle fill) while on (`PaintActivity`,
  upstream file, 2 lines).
- The page bar's previews all in a 2 dp frame (`PageTurner`), the page drawn on told by its ink frame and its tab.
- The hover ring (`DrawingPad`, upstream file, about 30 lines): eight 2 px ink dots on the nib's outline, each its own
  saved patch, no white dashes and no firmest ring (`HoverNib.firm` and `FIRM_SHOWN` gone).
- The slot's name label: bold words on paper, no `Theme.menu` frame (`InkWashBar.say`).
- **Checks:** `DrawLayoutChecks` (three tool rows, the eraser's own row, no hairline, Zen's icons, the column's
  heights), `ToolSwatchesChecks` (the eraser keeps its own size), `HoverNibChecks` (no firmest ring, the eraser's own
  size), `NomadUiChecks.layout` (the dot over the slider and the strip, the eraser alone on its row), `ScreenShots` (a
  slot's name, the eraser picked), `DrawScreenPictures`. `PickerBench` reports the presents after the lift.


## The brush that would not switch, and the owner's notes (2026-10-24, branch `slot-bugs`)

The owner's handwritten notes on the Manta, after `90bc4236`: "won't switch to another brush, kept jumping to liftith
with wrong size as well", "artifacting of side size indicator", "triangle shape should be right triangle", "switching
to eraser, brush selector blinked", "strokes around pages on top toolbar should be hairline". Answers: tray rows at
Medium are fine, the name label fully transparent.

**Why it jumped back.** Not the model. `ToolSwatchesChecks.everyTool` picks every slot in turn with sizes set between,
after the eraser's, the smudge tool's and the grey's, and a notebook toolbar coming and going: each tool keeps its own
size and nothing is written under another's key. The selection (`InkWash.select`) is set at once on the tap. The jump
was the screen. Since "Fast pickers" (`8342f2ca`) a tool pick is one `SelectionFeedback.update` of the whole column,
and the updates of the views inside it (`ToolButton.mark` of the old and new slot, `SwatchBar.show`'s tab and dot) run
inside it, gathered, with no frame asked of Android. Afterwards only the column's own drawing was invalidated
(`retainForNextDraw`, then `owner.invalidate`). Android keeps each view's recorded drawing until that view is
invalidated, so its next frame of the column still had the marks, the slider's tab and the dot as Android last drew
them, at launch: the slot in use then, at its size. The brush line's name is the same (`Caption.quiet`). On the Manta
the column is under partial grey (`setEinkUpdateMode(3, 7)`), and the driver drives any pixel the frame asks for that
differs from what it holds, which after the fast update is the new mark. So any later frame of the column (`PickerBench`
saw two after every slot tap) put the old slot's mark, size and name back on the panel.

**What changed.**
- `SelectionFeedback.invalidateWithin` (upstream file, about 20 lines): the owner and every view inside it over the
  patch are invalidated, at the next frame as before (`retainForNextDraw`, `finishUpdate`) and in the fallbacks that
  draw through Android. A turned child (a popup's `QuarterTurnLayout`) is invalidated whole.
- `SwatchBar.show`: the grey strip and the Dropper shown or hidden (the eraser, the smudge tool) inside the same fast
  update, gathered into the tool pick's one update of the column. They were set visible or not and invalidated through
  Android, the whole column drawn again with the stale rows: the eraser's blink.
- `SelectionFeedback.redrive` (upstream file, about 35 lines) and `StripMarker` (upstream file, 4 lines): a binary
  marker on the Manta (the size slider's tab, driver mode 5) gets one clean 250 ms after the lift. The driver passes by a
  pixel asked for the value it holds, forced or not, so `clean` of unchanged paper drives nothing there. `redrive` asks
  for the tab's strip with its paper at level 14, then as it is, two mode 7 updates back to back (`panel_end.h`'s
  "near", in Java over a session of the strip alone). Ink stays as it is. On the fast plane (the Nomad) the clean pass
  stays as it was.
- `SizeSlider`: a right triangle, its straight side on the left by the tab, the right angle at the top.
- `PageTurner`: every page preview's frame 1 px, the page drawn on in ink with its tab.
- `InkWashBar.say`: the label's words with no background, its popup transparent (the light rows keep paper).

**Checks.** `ToolSwatchesChecks.everyTool` (desktop). `PickerBench.stale` (the checks build, `-e pickerBench true`):
after each slot tap and an eraser tap, the column alone asked for a frame, the window read back (`PixelCopy`) against the
column drawn now, the ink of each tray row, the eraser's cell and the dot compared. A row whose ink differs by more than
60 px prints FAILED. Not run on the tablet yet (it was not connected), so the redrive's look and the bench's numbers are
still to measure.

#### The page bar's + instead of a blank page at the end (2026-10-24, branch `add-page`): upstream files touched

The owner, 2026-10-24: "instead of always a one white page, add a + icon on the top toolbar". A notebook no longer keeps
a blank page at its end. The page bar's + tile (`PageTurner.Strip`, tile `count + 1` in `StripGesture`, pinned in the last slot when out of the window) adds a page at the end
(`DrawingBook.insertPage` is `addPage`). `BlankPages.opened` now takes an old notebook's blank page at its end away when it
is opened (`DrawingBook.dropUnchanged`, not as a change), only a page alone, blank on the Page Colour, with nothing of
its own and not the page drawn on. `BlankPages.Watch`, `end`, `lastBlank` and `append` are gone.

| File | Lines | What |
| --- | --- | --- |
| `PaintActivity` | +15 −20 | `replaceBook` calls `BlankPages.opened` (every way a notebook opens: the shelf, the recovery, Open). `updatePages`, `recovery()` and `openNotebook` no longer put a blank page at the end. `newPage` is the +, with a note past 250 pages. Timed Pages' Next Page on the last page is `newPage`. |
| `DrawingPad` | +9 | `redrawAsks`: while `PickerBench` asks, each `invalidate` of the page keeps its caller, so the bench names who asks Android to draw the page again after a slot tap. |
| `LayerEdits` | −3 | Undo of a layer deleted in a full notebook no longer makes room by the blank page at the end (none comes). |

**Checks.** `BlankPagesChecks.opened` and `plus`, `PageStacksChecks.blankEnd`, `PageColourChecks.pages`, `CoverChecks.order`,
`PictureImportChecks`, `DrawLayoutChecks` (desktop). `PageNavigationChecks` (the checks build): + after the page drawn on, an
old notebook's blank page at its end gone on opening. `PickerBench` prints "page redraw asked by" each tap.
## Big brushes under the pen (2026-10-24, branch `big-brushes`)

The owner: "larger brushes, they seem a little sluggish" and "i kind of enjoy how the monopaint paints go on quick but
evolve over time, sinking into paper". Engine and glue only.

**Measured on the Manta** (`LIVE=1 scripts/bench_device.sh -e styles "@Steady line,@HB pencil,@Soft charcoal,@Round
marker,@Soft wash,@Grey wash,@Opaque round,@Wet gouache,@Hog bristle,@Wet oils,@Soft pastel,@Ben-Day brush" -e lives
none -e sizes 60,120,240 -e modes gray`, `framelog` beside it). Every brush but the Wet ones goes quick at these sizes.

- Under the pen (a batch's wait from its oldest sample to presented, p50/p95 ms): 4–12 / 8–55 for every quick brush,
  Soft charcoal and Soft pastel 240 at 25–35 / 52–71. Wet paint (Soft wash, Grey wash, Wet gouache, Wet oils) 5–9 /
  7–16 up to 120 px, 17–38 / 33–162 at 240. **Ben-Day brush 25/114, 90/261 and 940/1350 at 60/120/240**: the outlier.
- Why Ben-Day: its body is 4.3 times its size (628 px at 240) and the quick preview worked out its screen at every new
  pixel on the pen's thread (`PrintScreen.ink`, its thresholds and Ink gain's four neighbours).
- Pen-down to the first present: 75–160 ms for every brush (Ben-Day 236–406). In it: the injected down's age when
  handled 30–44 ms, the stroke's creation 30–50 ms (about 20 of it `ModularStroke.begin` on the bench's fresh page:
  `ToneDocument.begin` scans a blank layer's alpha with `Layer.covers` once per layer), the first present 2–6 ms. Then
  the panel's first frame came about 100 ms after the first present.
- After lift (quick): 20–70 ms to final for most, 100–220 for big sticks and pastel, 624 Hog bristle 240, 362 Ben-Day
  240. Panel busy after lift 0.55–0.85 s for quick strokes, 0.75–4.4 s for Wet paint (it moves after the lift by design).
- Big sticks, pastel, pencil 240 and Hog bristle 240 never trail (`trail_tiles=0`): the render never catches up, so
  their texture lands at lift. Tried: composing every 250 ms while behind. Finals the same, but nothing was written
  behind the pen (the trail keeps clear of a big brush's reach from the frontier) and Hog bristle 240's lift to final
  went 0.62 → 1.72 s. Reverted.

**What changed.**
- `QuickStroke.Sweep` (`flatScreen`): an ink screen's preview (Print's screens but the tones) is black dots where the
  panel's own pattern for the screen's mean grey is black (`DotPattern.pixel`), so it stays black or nothing as the
  screen's dots were. Desktop, pen-thread ms a batch of 8 at 240 px: Ben-Day 1.29 → 0.31, Rosette 0.88 → 0.34, Halftone
  0.99 → 0.33.
- `QuickStroke.Sweep.tones`: a tone screen's pixel its dot can't darken skips the screen (speed plan §5.1's next step).
- `StrokeTiming` and `DrawingPad` (upstream file, 1 line: `timing.down` at the pen's down): the stroke timing line
  gains `lag_p50_p95` and `first_ms` (the down's event time to the first present) with `first_parts` (the down's age
  when handled, the creation, the first present).

**Manta after:** Ben-Day under the pen 11/31, 20/97, 47/128 ms at 60/120/240 (two runs: 10–11/31–54, 20–26/97–112,
42–47/128–131), first present 95–260, 133–137, 189–209 ms. Its render still lags: lift to final 84–430, 185–1019,
667–690 ms (by lift it has 300–720 of the 719 samples, varying run to run). Every other row within noise of before.

**Final pages unchanged:** every layer's tones, alpha, share, print and kind planes after settling, hashed for 19
presets (all 12 Print presets, the measured ones) at 16/60/240 px, the app's rule, every stroke quick with the render
paced, and every stroke live: identical to `2425373f`. `WetPaintChecks.BEFORE`'s two mid-stroke Halftone hashes (the
preview) updated, their ends unchanged. `ScreenChecks.quick` still holds (black dots or nothing).

**Not done:** the covers scan at a blank layer's first stroke (about 20 ms of pen-down on the Manta, once a layer a
page). Faster renders for big sticks, pastel and bristles (their texture at lift). Wet paint's 240 px lay.

#### A clean window on return (2026-10-24, branch `resume-clean`): upstream files touched

The owner, 2026-10-24: "noticed lots of eink ghosts on testing", switching back from Brush Lab or the Tools page.

**Why.** The return's clean (`262b6bc`) was a forced mode 7 refresh of the page and of the window (`refreshFastInk`,
`SelectionFeedback.clean`). On the Manta the driver passes by any pixel asked for the value it holds, forced or not, so
that refresh drives only what changed and cleans nothing. Then `8342f2ca` put the column and the brush line under
partial grey (`setEinkUpdateMode(3, 7)`, as the page already was), so their redraw on return is one grey pass from the
other screen's black text and frames, which ghosts. The only update the app knows that drives unchanged pixels again is
the firmware's default for Android frames (PickerBench, 2026-10-24: the column's unchanged redraw drove the panel twice,
about 500 ms with the blink). Mode 1 also redrives, but the driver then takes the area for white, and the JNI does not
accept it.

**What changed.**
- `Ui.cleanWindow(root, reason, restore)`: every view in the window back to the firmware's default
  (`resetEinkUpdateMode`), the whole tree invalidated, and `restore` 600 ms after that frame is drawn. Logs "Clean window
  (reason)".
- `DrawingPad`: the connect after a return (`returned`) blanks the fast plane as before, then runs `cleanWindow` over the
  window. The restore puts the page (`NativePen.partialGrey`, if still connected) and the column and brush line
  (`InkWashBar.partialGrey`) back under partial grey. The forced page refresh owed at that connect is dropped, since the
  clean redrew the page and its redraw of the bar would blink again. `cleanOffPage` is no longer run there.
- `DrawingPad.suspend`: leaving the drawing screen blanks the fast pen plane (mode 9, where there is one) over the whole
  window first, so no fast ink stays over the next screen.
- `NotebookScreens.hide` marks the return too.
- `BrushTestPad.opened`, called from `BrushLabActivity.onResume` and `ToolsActivity.onResume`: the next connect runs
  `cleanWindow` and restores the pad's partial grey.

| File | Lines | Why |
|---|---|---|
| `Ui` | +24 | `cleanWindow`, `CLEAN_MS`. |
| `NativePen` | +6 | `partialGrey`: the host back to (3, 7). |
| `DrawingPad` | +23 −9 | The return's clean, the fast plane blanked on suspend, `windowArea`. |
| `BrushTestPad` | +10 | `opened`. |
| `BrushLabActivity`, `ToolsActivity`, `NotebookScreens` | 1 each | Mark the open or the return. |

Generic: any MonoPaint screen coming back over another on this firmware has the same trap, wherever a view is under
partial grey or a forced refresh is relied on to clean.

**Not checked on the Manta** (it was not connected): the flash itself, and whether 600 ms is long enough before partial
grey comes back. The firmware may read a view's mode when the frame is posted, then any delay works. Logcat shows the
"Clean window" line at each open and return.


## Snappy UI (2026-10-25, branch `snappy-ui`)

The owner: "MonoPaint's UI feels particularly fast", make Scribulor's as snappy. A tap on the drawing screen should be
one small panel update of what changed, sent as MonoPaint's selection feedback is, with no Android redraw of its
neighbours and no grey pass more.

**What changed.**
- The slot's name label is gone (`InkWashBar.choose`, `stamp`, `DrawLayout.NAME_MS`). Every slot tap opened a popup
  window beside the column and closed it 1.5 s later (the firmware drawing it, then `cleanArea` refreshing what it
  covered), about 30 of the slot tap's 69 frames in "Fast pickers". The brush line still names the brush. The label stays
  for a slot's brush changed, a grey the Dropper picked, the mirrors and Cut Stamp.
- A tool tap's column update is `SelectionFeedback.updateBinary`: when what changed is ink and paper alone (the old and
  new marks, no new size) it is the panel's binary update (mode 5 on this Manta firmware, 8 frames), else mode 7 as
  before. `updateBinary` now restores the flag it found (upstream file, +2), so the dot's update gathered inside the
  column's no longer clears it.
- The brush line and the eraser's note are one update of the line (`updateLine` over both). A preview shown or gone (a
  slot to or from the eraser) moved the name through a layout after the update, so Android drew the line again with the
  name elsewhere, one more grey pass: the line is laid out inside the update now.
- Nothing about the brush is worked out on the tap. The line's words (`ModularWords.caption`, `BrushVoice.about`, which
  resolve the brush and read its textures the first time, 34 ms for the first on the desktop) and its preview are made
  in the background for each slot as its brush is shown (`InkWashBar.warmLine`, kept by `Brush.identity`).
- The page bar (`PageTurner`'s view) is under partial grey as the column and the brush line are (`InkWashBar.partialGrey`).
  Its arrows, number and previews are drawn through Android, and each redraw was the firmware's two passes with the
  blink, the unchanged arrows too. The bar's auto refresh cleans it (`SelectionFeedback.clean`) as it does the column.
- Menus (`PaintActivity.menu`, upstream file, 3 lines) and the side panels (`InkWashBar.beside`: the smudge chooser, the
  stamp menu) open under partial grey, one grey pass, and on closing clean only what they covered
  (`DrawingPad.cleanArea`). `DrawingPad.uncovered` also refreshed the whole page and drew the column, the page bar and
  the brush line again through Android after every close. Upstream MonoPaint's menu closes with nothing at all.

**The DrawingPad redraw on a slot tap** is not named yet: the tablet was not connected (`adb devices` empty), so
`PickerBench` did not run. From the code, the candidates are `SelectionFeedback`'s pending patches (`retainForNextDraw`
keeps the whole page after a page turn or a pan until Android's next frame for any reason, then `invalidateWithin`
draws the page again) and the label's close (gone). The bench's "page redraw asked by" names the caller on the next run.

**Not changed:** Zen Mode lays the whole screen out again (the page fitted to it), so its tap draws the page. Undo and
Redo draw what they change on the page and refresh it after a large change, their buttons draw nothing. The size slider,
the grey strip and the wet drop were fast already.

**Not measured** (no tablet): MonoPaint beside Scribulor, and every number above but the desktop's. `PickerBench` now
also opens and closes the menu and times Undo and Redo of a stroke. Risk to watch: mode 5 for a slot's old mark (ink
to paper) may leave a faint trail, as the size slider's tab did over many moves.

| File | Lines | What |
| --- | --- | --- |
| `SelectionFeedback` | +2 | `updateBinary` restores the flag it found. |
| `PaintActivity` | +3 −1 | `menu`: partial grey content, `cleanArea` on close. |

**Checks.** `DrawLayoutChecks` (the name's timing check gone), desktop RAN 127. `PickerBench` (the checks build):
menu open, menu close, undo tap, redo tap.

## Device timing (2026-10-25, branch `device-timing`)

`PickerBench` on the Manta (Chauvet 2606141001) at `2a7ada35`, and MonoPaint (`dev.tilesmile.supernote.paint`)
timed beside it the same way: a pen tap injected from the shell (`InputManager.injectInputEvent` in a small dex, its
down stamped on the boot clock), the panel's frames read from `fdec0000.ebc` in `/proc/interrupts` by a loop on the
device, about 10 ms a read. "First" is ms from the down to the first panel frame, "settle" from the down to the last.

| Action | MonoPaint: first, frames, settle | Scribulor: first, frames, settle, mode |
|---|---|---|
| Tool pick (a tool button, a slot) | 104 to 114 ms, 36 to 37, 504 to 519 ms | 206 to 251 ms, 37, 606 to 661 ms, two mode 7 presents |
| Eraser pick (bench) | not timed | about 230 ms, 73 to 83, 830 to 960 ms, two mode 7 presents and two Android frames of the whole column |
| Grey (colour bar tap, the strip) | 79 to 86 ms, 37 to 42, 491 to 549 ms | 81 to 83 ms, 21, 310 to 312 ms, mode 7 then mode 5 |
| Size (slider tap, bench) | not timed | about 135 ms, 44 to 45, about 630 ms, mode 5 |
| Menu open | 79 to 89 ms, 127 to 161, 2.4 to 2.8 s | 413 to 763 ms, 36 to 57, 0.8 to 2.3 s |
| Menu close (a tap off it) | 127 to 148 ms, 68, 917 to 937 ms | 133 to 149 ms, 69, 932 to 939 ms |
| Undo, Redo (bench) | not timed | 37 to 40 frames, quiet about 364 ms (Undo), 21 frames, about 300 ms (Redo) |

Since then (`PickerBench`, not this injector, which reads about 15 to 25 ms later): a slot pick's first frame 185 to 207
ms after "Snappy UI 2", 110 to 146 ms after "The pick's bar on the down" (below), where MonoPaint's is its press outline.

**The whole page drawn again.** No slot tap draws `DrawingPad` again now (24 taps over three runs, android frames 0,
page redraw asked by none). The name label was the one that did (gone in "Snappy UI"). The menu's opening still does,
asked by none. Traced on the main looper: the invalidate of the whole page (`ViewRootImpl` message 2, an invalidate rect
of `DrawingPad` 1785 × 2401) is posted while the window's focus change (message 6) is handled. The menu's popup is
focusable, so the drawing window loses its focus and the firmware has the page drawn again. It is not
`disconnectDisplay` or any of `NativePen.disable`'s three firmware calls, each tried alone with nothing dirtied. An
Android redraw of the unchanged page drives no panel frame, so it costs time on the main thread, not on the panel.
Upstream MonoPaint's menu loses the focus the same way. Not changed: a menu that keeps the window's focus is a
non-focusable popup, which changes how Back and a tap off it work.

**The slot's old mark and mode 5.** A slot tap's column update is mode 7 every time on the Manta: the changed patch
runs from the old mark's row to the new one and takes in the grey strokes of the rows between, and `opaqueBinary` checks
the whole patch. So the mode 5 path in `selectTool` does not run for a slot tap and leaves no trail to fix. A
screencap cannot judge it anyway: the fast update goes to the panel, not to Android's surface, so a screencap shows the
old mark until Android's next frame of the column.

**What MonoPaint does that Scribulor does not, as next steps.**
- A tool pick shows in about 105 ms, Scribulor's slot in about 230 ms, the grey strip in 81 ms. The slot's update
  captures and compares the whole column twice (135 × 2401) and opens a new panel session for it. Next: update the old
  and new rows and the swatch bar alone, and check only the changed pixels for ink and paper, so a slot pick can be mode 5.
- Its menu's first frame comes at 80 to 90 ms, Scribulor's at 400 to 760 ms (the rows built, the popup measured, the
  page drawn again on the focus change). Scribulor's menu drives far fewer frames after. Next: time the menu's build.

**Bench.** `PickerBench.pen` checks the window's focus on the down alone. A tap off the menu closes it on the down,
and the window has its focus back only later, so the lift threw and the bench stopped at "menu close". Device time about
40 minutes (the tablet was off USB for ten), the checks app, its test APK and its folder removed after. MonoPaint was
opened on its unsaved painting, so no new painting was started (its new painting asks to save or drop that one). Its
tool, colour bar and menu were timed, its pencil put back and its grey set back at the black end. Size and Undo were
not timed there, an Undo would change that painting.
## The eraser takes crayon, the scratch pads keep up (2026-10-25, branch `crayon-lab`): upstream files touched

The owner, on the Manta, with the Wax crayon large: "Crayon didn't erase with eraser. Base erase should erase
everything." And "it was sluggish in brush studio but not in main".

**Why the crayon stayed.** A crayon lays its wax on the page's masking fluid as a kept share (`ModularStroke.layWax`,
`beginFluid(0)`), so washes slide off it. Every write to a paint layer goes through the fluid's shield (`setPixel`), and
the eraser's writes did too, so wax shielded the crayon's own marks from the eraser, Everything included. Nothing about
the eraser's kind: it starts on Everything (`InkWash.eraseKinds` is 0, never saved, set only by a tool kit's step), so
there was no default to change or migrate. Large crayons lay wax over most of what they cover, so they showed it most.
Wax resist had the same rule by design (2026-10-18, "the eraser never lifts wax").

**What changed.**
- `ToneDocument.shield`: for Scribulor's eraser (`eraseKinds` not 0) only the fluid the eraser can lift shields, as
  `takesOnWax` already did for a crayon's own build-up. Wax keeps paint out, not the eraser off the paint under it. A
  kind-limited eraser (Pen, Paint, Dry Media) reaches paint under wax too and leaves the wax (no kind of mark) and
  masking fluid as before. MonoPaint's erasers (0) are unchanged.
- `EraseBothStroke.finish`: on Everything, the fluid gesture at lift now also takes the wax wherever the eraser went
  but where it lifted fluid, with `KIND_ALL` (the mask has no kinds, so every pixel is taken whole). Where it lifted
  fluid, the paint and any wax under it stay for the next pass, as before. One Undo step with the ink and wash.
- Tone's scrape is unchanged: Everything still scrapes a share of Tone's dots each pass (a black pixel at coverage 254
  reads as one, so a pen's edge pixel can scrape too).

**Why the scratch pads were slow.** `BrushTestPad` (Brush Lab's test area and the Tools page's Scratch Pad, both
`TestArea`) drew each pen event alone: sample, compose, the hatch, and a present, 480 times a second on the Manta. The
drawing screen gathers samples (`PenBatch`, drawn once the input queue is empty) and spaces a stroke's presents by 1.5
times their recent cost (8 to 100 ms) since "Speed round 2". The strokes themselves were the same (`InkWash.stroke`,
`QuickStroke`'s rule, the fast plane through `StrokePanel`, mode 7 from `prepareDocumentCanvas`), and nothing else in the
Lab redraws under the pen.

**What changed.**
- New `PresentPace` (pure): the present spacing taken out of `DrawingPad` (`presentMs`, `lastPresent`, `presentGap`),
  the same formula. `DrawingPad` and `BrushTestPad` both use it.
- `BrushTestPad`: samples go to a `PenBatch`. The first event since a draw posts `drawBatch`, which feeds them all,
  presents the stroke, renders and presents once. The down, the lift, a held pen's tick and `finishStroke` draw what is
  gathered first. Its presents while the pen is down wait out `PresentPace`.

**Measured on the desktop** (a 1300 × 900 pad, a 480-sample second of pen, the page's render and hatch for the dirty
area in Java standing in for the native compose). Per draw, desktop alone: Wax crayon 120 px 0.05 ms, 240 px 0.15 ms,
360 px 0.30 ms, Fineliner 360 px 0.21 ms, so on the desktop every draw keeps up either way. With a model of the Manta
(desktop ms × 15, 4 ms a present), the pen's wait from a sample to its draw, p50/p95 ms, per event (before) and gathered
(after): Wax crayon 120 px 643/1212 → 2/7, 240 px 948/1859 → 19/30, 360 px 1395/2792 → 38/63, Fineliner 240 px
836/1557 → 8/15. Presents a second 480 → 41 to 80. The model's factor is a guess, not measured. Timing on the Manta is
still to do (`LIVE=1 scripts/bench_device.sh` covers the drawing screen only).

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +6 −3 | `shield`: liftable fluid only for Scribulor's eraser. Generic: any eraser that wants wax kept off paint, not off itself. |
| `DrawingPad` | +8 −16 | The present spacing through `PresentPace`. |
| `EraseBothStroke`, `BrushTestPad`, `PresentPace` | Scribulor's | The wax taken on Everything, the pad's batches and spacing, the spacing shared. |

**Checks.** New `EraseAllChecks`: the owner's Wax crayon code at 44, 120 and 360 px, dark then light over a black
ground, and every Basic tool (121 in all), drawn as the drawing screen draws them and dried, then two passes of the
eraser as it starts: white paper but Tone's dots, and nothing on the masking fluid. Dry Media takes a crayon and leaves
its wax. `ResistChecks` now expects the eraser to take wax and the paint under it, and the sheen with it. Golden hashes
unchanged (no drawing changed). Desktop RAN 128.

## Snappy UI 2 (2026-10-25, branch `snappy-2`)

The next steps from "Device timing": a slot pick, the eraser pick and the menu's opening. Found by timing each step on
the main thread on the Manta (logcat stamps, taken out again), then `PickerBench` before and after.

**Where the time went.** From the click to the panel's first update, a slot pick spent about 95 ms on the main thread:
`InkWash.select` 36 ms (the toolbar backup's text, every slot's share code, built on every pick, twice when the
preference listener fired too), the whole column captured twice (10 to 15 ms each), compared (20 ms), a session opened
(2 to 3 ms). The panel's first frame then comes about 95 ms after the update is handed over, whatever the app does. The
eraser pick's two Android frames came from the brush line, not the strip: its preview went `GONE` for the eraser and
`VISIBLE` again for a slot, a layout, so Android drew the line and the column again (the strip's `setVisibility` was a
second cause). The menu was not slow in the app: built, measured and shown in 60 to 120 ms, drawn by Android 150 to 170
ms after the down. The firmware put the new window on the panel about 350 ms later. Neither the menu's partial grey
mode nor its focus changes that (tried: without `partialGrey` first frame 531 ms, a non-focusable popup 532 ms).

**What MonoPaint's first frame is.** MonoPaint's menu button and tool buttons draw a press outline through
`SelectionFeedback` on the pen's down (`PageActionButton`, `PressOutline` with a feedback session), so its 80 to 110 ms
"first frame" in "Device timing" is that box, not its menu or its marks. Scribulor's column has no press outline since
"Fast pickers" (a box on and off is two more grey updates the panel runs one after another), so its first frame is the
change itself.

**What changed.**
- `SelectionFeedback.updateParts` (upstream file): a tool pick captures only the patches of the updates gathered inside
  it (the old row, the new row, the swatch bar, the Dropper, Play), each before its change and after them all, not the
  135 × 2401 column twice. `differences` splits each patch's changed pixels into boxes wherever more than 5 unchanged
  rows or columns part them, and each box is checked for ink and paper on its own. Every ink and paper box goes out in
  mode 5 (mode 9 where the firmware has the fast plane), the rest in one grey update over their union, drawn again from
  the column. A slot's mark is ink on the paper of the bar's room, so a pick stays mode 5 whatever grey strokes the rows
  between hold. One session a mode a pick, over the union of the patches (open measured at 2 to 3 ms, so not kept).
- `InkWash.backUpToolbar`: the backup is written 2 s after the last change, not on the tap (`select` 36 ms to 2 to 6).
- `SwatchBar.show`: the strip and the Dropper are drawn as paper for the eraser and the smudge tool, no
  `setVisibility`. `GreyStrip.hidden` (paper, no touch), the Dropper a paper `ToolButton.overlay` in its own update
  gathered into the pick's, `InkWashBar.tapDropper` does nothing while it is hidden.
- `InkWashBar.showBrushLine`: for the eraser and the smudge tool the preview's bitmap is paper, kept in its place, so the
  line is not laid out again. The tool's name now stays where a brush's name is, after the preview's room.
- `PaintActivity.menu` (upstream file): the menu is built, placed and drawn into a bitmap in `built`, and the file menu's
  is kept (keyed by its rows, marks, anchor, size, turn and finger or pen) and made ahead at the first idle moment
  after the drawing screen gets its focus (`prepareFileMenu`). On a tap it is put on the panel at once through
  `SelectionFeedback.presentAhead` (one grey update where the window will be), then shown as before.
- `PickerBench`: a self check of `differences` (two marks apart, two boxes), and a pause before each menu opening so
  Redo's frames don't run into it.

**Measured** (`PickerBench`, the checks build with `-PfastChecks`, the Manta, Chauvet 2606141001, two rounds,
before is `c445dfc4`). "First" is ms from the bench's pen down to the panel's first frame. The bench waits for the app to
be idle between the down and the lift, so its click comes 45 to 65 ms after the down, later than a real tap's.

| Gesture | Before: first, frames, quiet after lift | After |
|---|---|---|
| Slot pick | 223 to 278 ms, 37, 423 to 431 ms | 185 to 207 ms, 38, 427 to 440 ms, 0 Android frames |
| Eraser pick | 230 to 258 ms, 73, 828 to 830 ms, 2 Android frames of the column and line | 176 to 188 ms, 38, 428 to 442 ms, 0 Android frames |
| Slot pick after the eraser | 262 to 270 ms, 73, 843 to 844 ms, 2 Android frames | 161 to 185 ms, 38, 423 to 438 ms, 0 Android frames |
| Menu open | 504 to 588 ms, 36, 597 to 621 ms | 158 to 189 ms, 72 to 75, 621 to 643 ms |
| Menu close (a tap off it) | 249 to 279 ms, 51 to 105, 0.6 to 1.3 s | 192 to 235 ms, 51 to 112, 0.6 to 1.3 s (unchanged) |

Main thread from the click to the first update: slot pick about 95 ms before, 35 to 45 after. Menu about 120 ms plus
the firmware's 350 before, 30 to 40 ms after (`presentAhead`, mostly the driver taking a 450 × 1136 grey update).

**Not changed, and what is left.**
- The menu's frames went up: our grey update (16 to 18 frames), then the firmware still drives its own update of the
  new window when it shows it (about 36 frames, as before). The settle is about the same. A firmware that passes by the
  pixels already shown would make it one pass.
- The brush line is still its own mode 7 update after the column's (its preview is grey), 21 of a pick's 38 frames.
- The first frame is still the change itself, about 95 ms after it reaches the driver. A press outline like MonoPaint's
  would show sooner but adds two grey updates the panel queues before the mark.
- In "Device timing" the slot pick's column was noted as mode 7 every time. On the bench's toolbar it was already mode
  5 (the rows between were ink and paper). Not measured on a toolbar whose rows hold grey, where the old path went mode
  7 and the new one stays mode 5.
- Not checked by eye: mode 5 for the marks over many picks (a faint trail, as the size slider's tab once left).

| File | Lines | What |
|---|---|---|
| `SelectionFeedback` | +165 −1 | `updateParts`, `presentAll`, `part`, `differences`, `presentAhead`, gathered updates register their patch. |
| `PaintActivity` | +58 −9 | `menu` split into `built` (kept for the file menu) and its showing, `prepareFileMenu`, made ahead on focus. |

**Checks.** Desktop RAN 127 (ModularBenchChecks failed its timing under load, as it does). `PickerBench` (the checks build, `-e pickerBench true`): the `differences` self check, every
pick's stale check (the Android frame after it matches what the panel was given). Device time about 22 minutes, the
checks app, its test APK and its folder removed after.

#### A clean page after a pinch, a pan or the eraser (2026-10-25, branch `display-ghosts`): upstream files touched

The owner, 2026-10-25 (10:15 to 12:50, fill brush, Fineliner, eraser, much zooming): ghosts while zooming and panning
that took several of the Supernote's swipe refreshes, and erased marks showing again until a refresh. A pinch or pan
puts a frame on the panel every 16 ms through the page's grey session, each a mode 7 update over pixels the one before
is still driving; the eraser's moves and its ring do the same over a small area (469 presents in one 2.5 s stroke).
Pixels changed mid-update are left part-driven, and the driver records them as shown, so the auto-refresh's forced
mode 7 passes them by (the panel's side, above: it drives nothing on this firmware). Nothing else cleaned the page after
a pinch: `endNavigation` ended it without any refresh, and a firmware ACTION_CANCEL (about 145 two-finger ones in the
session) ends it the same way as a lift.

Now `endNavigation` (lift or cancel) and the eraser's end or take-back mark the page `ghosted`, and the next auto-refresh
(1 s after a pinch or pan ends, else the usual pen-away pause) draws the page alone again in the firmware's default
update (`Ui.cleanWindow` with the page as root, partial grey back after it) instead of the forced mode 7 pass. One blink
of the page, never under the pen or mid-pinch; other pauses are as before. The plain pen-away pause no longer
refreshes at all (the owner, 2026-10-25: "do away with the penaway"): only a ghosted page is cleaned then, and Brush
Lab's pad drops its pen-away pass too. That forced pass, with the bar's captures,
held the main thread 0.5 to 1.2 s in the log ("Skipped 32 to 74 frames" right after 19 auto-refreshes).

| File | Lines | What |
|---|---|---|
| `DrawingPad` | +26 −6 | `ghosted`, `NAVIGATED_MS`, `refreshSoon(reason, delay)`; `endNavigation`, `finishStroke` (an eraser), `cancelFingerStroke` set it; `autoRefresh` cleans the page when it is set. |
| `Ui` | +2 −1 | `cleanWindow`'s log names the view cleaned. |

Not checked on the Manta (no device in this change). Generic: any MonoPaint build on this firmware leaves the same
ghosts after a pinch.

## Press feedback (2026-10-25, branch `press-feedback`): upstream files touched

The owner liked that MonoPaint feels snappy, partly because its first frame on a press is an outline ("Snappy UI 2").
Scribulor's slow buttons showed nothing until their answer: a menu (the firmware adds about 350 ms for a new window), a
page turn, the notebook. Now a slow button's press is its box inverted (ink, the icon paper) on the pen's or finger's
down, in one binary update, and the click lets it go with no update of its own (KOReader's `Button`: the unhighlight is
not fenced, it blends into what the callback draws). The tool slots, the strip, the slider, Undo and Redo keep no press:
their answer is already quick, and a press would queue in front of it.

**What changed.**
- `ToolButton.slow()`: the press for Menu and Play (`InkWashBar`), and the page bar's arrows and Notebook (`PageTurner`).
  One helper, the house `PressOutline` given the feedback session and `inverted`.
- `PressOutline.set`: an inverted press goes through `SelectionFeedback.updateBinary` (mode 5 on the Manta, 8 frames,
  about 145 ms). `ToolButton` draws it with `selectionInverted`'s look (`fillBox`, the icon white) through
  `ControlRaster` as ink and paper (its dots for the soft edges), so the patch passes `opaqueBinary`.
- `PressOutline.clicked` (from `ToolButton.performClick`, before the action): unpressed and the button invalidated, so
  Android's next frame, the one the action asks for, draws it again. The firmware drives the box back to paper with that
  frame (it drives what differs from what it holds, "The brush that would not switch"). A press let go off the button
  (cancel, a move off it) is upstream's one update. On the Nomad's fast plane, which Android's frames don't clear, the
  click lets go in an update as upstream's.

**Measured** (`PickerBench`, `-e pickerBench press`: Menu and Play opened and closed, three rounds; the checks build
with `-PfastChecks`, the Manta, Chauvet 2606141001; before is `e2b3d4d6`, the bench alone). "First" is ms from the
bench's pen down to the panel's first frame.

| Button | Before: first, quiet after lift | After: first, quiet after lift, the press |
|---|---|---|
| Menu | 192 to 220 ms, 612 to 620 ms | 139 to 169 ms, 503 to 569 ms, one mode 5 present 52 × 48 |
| Play | 197 to 237 ms, 600 to 645 ms | 113 to 146 ms, 532 to 559 ms, one mode 5 present 43 × 43 |

The button's own Android frame (the let-go) comes with the click, 140 to 190 ms after the down, before the page's redraw
on the focus change. Closing a menu is unchanged. Page arrows and Notebook not timed (the bench's drawing has one page).

**Not done.** Idea 12 (build a menu while the pen hovers over its button): the file menu is already made ahead at the
first idle moment (`prepareFileMenu`), and keeping Play's or any other menu would mean generalising that one kept menu
and its key, not a small change. Brush Lab's way in (a tap on the brush line) and the menu's own rows (Tools, Library)
have no press: they are not `ToolButton`s.

| File | Lines | What |
|---|---|---|
| `PressOutline` | +21 −1 | `inverted`, its press binary, `clicked`. |
| `ToolButton` | +6 −4 | `slow()`, the inverted look while pressed, `clicked` before the action. |
| `ControlRaster` | +4 −2 | `draw(…, binary)`: ink and paper on any panel when asked. |

**Checks.** Desktop RAN 128. `PickerBench` press mode (above). Not checked by eye on the Manta: the box's look, and
whether the let-go's grey pass shows as a flash of the box. Generic: any MonoPaint button could use `slow()`.

## Side bars alone (2026-10-25, branch `slot-redraw`): upstream files touched

The owner: "the brush slots.. they dont need to update so often, just the line indicator beside", then "most of the
things now use the bar to indicate it's active so buttons and selections should be pretty quick". Every control whose
choice is a side bar (the eight slots, the eraser, the smudge and stamp tools, Play, the Lasso, the Dropper) is a
`ToolButton` with two `SlotTile.Face`s that differ only by that bar.

**What a pick did before** (`PickerBench -e pickerBench slots`, below). The panel already got only the bars:
`SelectionFeedback.differences` cut each patch down to its changed pixels. But `ToolButton.markerArea` was the whole
button for a two-face button, so every pick captured each tile whole before and after (its stroke or icon painted
again), the union kept for Android's next frame spanned the rows between, and the Lasso and the Dropper marked
themselves and the tool they give way to in separate updates, each a mode 7 grey pass. A size or grey picked updated
the Dropper (two captures) though nothing about it changed, and any `showBrush` (a Tools page or Brush Lab return,
a notebook's toolbar entered or left, a tool kit's Undo) drew every slot's stroke again in the background and updated
each row on the panel.

**What changed.**
- `SlotTile.sideBar` (the one geometry `draw` uses too) and `Face.sideBar`: where a row's bar goes. `ToolButton.markerArea`
  (upstream file, +10 −4 with `iconBounds`, which `onDraw` shares) returns it for a button whose faces are bar rows, so
  `mark` captures, compares and keeps for Android's next frame that strip alone. One place, every bar control.
- `InkWashBar.lassoChanged`: the Lasso's, the Dropper's and the tool's marks (and Play's) in one `updateParts` of the
  column, as a tool pick's, so they go out together in the binary update.
- `InkWashBar.showBrush`: a row is drawn again only when its brush (`Brush.identity`) or its size changed (`trayShown`,
  in place of `looped`). The picture follows the brush, not its size or grey (`ToolSwatches.picture`).
- `SwatchBar.show`: the Dropper is updated only when it is shown or hidden.
- `SlotTile.Face.painted` counts row paints for the bench, which now has a `slots` mode: picks of slots, the eraser, the
  Lasso and the Dropper, a stroke, a size and a grey, and a self check that the marker area is the bar's width.

**Measured** (the checks build with `-PfastChecks`, the Manta, Chauvet 2606141001, two rounds; before is `115332ab`
with the bench and a temporary log of each present). Bars are 9 px wide (5 dp), "quiet" is from the lift.

| Gesture | Before: presents of the column, row paints, panel frames, quiet | After |
|---|---|---|
| Slot pick | mode 5, the two bars 11 × 64 (cut from two whole 120 × 64 tiles, the swatch bar and the Dropper), 14 row paints, 38 to 39 frames, 419 to 437 ms | mode 5, the two bars 9 × 64 (strips captured), 10 paints, 32 to 34 frames, 372 to 383 ms |
| Eraser pick and back | 10 to 11 paints, 38 frames, 419 to 447 ms | 10 to 11 paints, 33 to 34 frames, 360 to 383 ms |
| Lasso or Dropper on or off | two separate mode 7 updates (11 × 47, 11 × 64), 4 paints, 22 frames, 252 to 275 ms | one update, two mode 5 bars (9 × 43, 9 × 64), 6 paints, 9 frames, 105 to 123 ms |
| Size or grey tap | 2 paints (the Dropper) | 0 |
| A stroke on the page | 0 | 0 |

First frame after the down is about the same (slot 185 to 207 ms, Lasso 148 to 182 ms): the column's update takes 80 to
125 ms on the main thread with the pick's own work, not the captures. The stale check (Android's next frame of the
column against what the panel was given) matched every row after each round. Not measured: a return from the Tools
page or Brush Lab (the bench does not open them), where unchanged slots no longer draw at all.

| File | Lines | What |
|---|---|---|
| `ToolButton` | +10 −4 | `markerArea` of a bar row is its side bar; `iconBounds`, shared with `onDraw`. |

**Checks.** Desktop RAN 128. `PickerBench -e pickerBench slots`. Not checked by eye on the Manta. Generic: none (Scribulor's
faces only).

## The pick's bar on the down (2026-10-25, branch `pick-speed`): upstream files touched

Goal: a pick's first frame as soon as MonoPaint's (104 to 114 ms, "Device timing"). Timed on the Manta with SystemClock
stamps from the pen's down through the pick (taken out again), `PickerBench -e pickerBench slots`.

**Where the time went** (a slot pick, ms after the bench's down; 2 rounds):
- The down reaches the button 15 to 30 ms after the bench sends it; the lift, and with it the click, 45 to 60 ms after
  (the bench waits for the app to be idle in between). Nothing was drawn on the down: the pick ran on the click.
- The click's main thread until the first box went to the driver: 30 to 60 ms. `swatches.show` 8 to 11 (its whole
  120 × 336 bar captured as a part), the parts captured again and compared 13 to 25 (the swatch bar's `differences`
  alone 5 to 16), the before and after bitmaps over the union of the parts (120 × 923 to 1141) 3 to 5, the two marks 4
  to 9, `iconButton`'s click to `selectTool` 1 to 7, a session 2 to 3.
- The driver: the panel's first frame 85 to 100 ms after the first box goes, whatever the app does.
- After the first box, the second box's `ioctl` blocked the main thread 65 to 70 ms every pick (the driver holds a
  second update sent right after the first), then the brush line's update 15 to 37 ms.

So the click alone, however lean, left the first frame at the lift plus about 95 ms. MonoPaint's first frame is its
press outline on the down ("Snappy UI 2").

**What changed.** A bar row's press is its side bar (`ToolButton.bar`, `PressOutline.bar`): on the pen's or finger's
down the new bar goes out in the binary update over the bar alone (two captures of 9 × 64 and one session, about 5 to
14 ms after the down reaches the button). The click's `mark(true)` ends the press in the mark (`press.pressed` cleared
inside the mark's change), so its part shows no change and the pick's update sends only the old bar, the swatch bar
and the line. A press slid off (or cancelled) takes its bar back in a binary update; a long press does too, unless it
took the tool up (the smudge tool's). The slots, the eraser, the smudge tool, the Lasso and the Dropper; not Play (its
press stays the inverted box). The old bar no longer queues behind the new one, so the 65 to 70 ms block went with it.

**Measured** (the checks build with `-PfastChecks`, the Manta, Chauvet 2606141001; before is `cdad8878` with the
timing stamps, after without; first frame from the bench's down).

| Gesture | Before: first frame | After: first frame | Presents of the column |
|---|---|---|---|
| Slot pick | 181 to 218 ms | 110 to 146 ms (180 once, the first pick of the run) | the new bar on the down, mode 5 9 × 64; the old bar and the swatch bar at the click |
| Eraser pick | 184 to 188 ms | 132 to 146 ms | as a slot's, 9 × 43 on the down |
| Lasso, the Dropper taken up | 146 to 181 ms | 111 to 130 ms | 9 × 43 on the down, the tool's 9 × 64 at the click |
| The Dropper put down | 146 to 181 ms | 155 to 171 ms (unchanged) | its bar was shown already: nothing new to show on the down |

Quiet after the lift, panel frames and Android frames as before (slot 415 to 440 ms, 38 frames, 0 Android frames). The
stale check matched every row in both rounds. New in the bench: a press slid off slot 2 leaves it unpressed and
unmarked and slot 1 picked.

**Not changed.** The click's own work (the swatch bar's capture and compare, the line's update) is no longer before the
first frame, so it stays as it was. The driver's 85 to 100 ms from the hand-over is the panel's. MonoPaint's 104 to 114
ms came from the shell injector, which was not run here (the bench reads 15 to 25 ms later).

**Risks.** A pick now shows two bars for the time between the down and the lift (the old one goes at the click). A
press that slides off shows a bar on and off again, two binary updates. Not checked by eye on the Manta.

| File | Lines | What |
|---|---|---|
| `PressOutline` | +8 −1 | `bar`: the press as the bar, in a binary update over `markerArea`. |
| `ToolButton` | +12 −2 | `bar()`, the bar drawn while pressed, `mark` ends the press, `performLongClick` lets it go. |

**Checks.** Desktop RAN 129. `PickerBench -e pickerBench slots` (the slid-off check). Release build EXIT 0. Device time
about 15 minutes, the checks app and its test APK removed after. Generic: any MonoPaint button whose selection is a
small mark could show it on the down.
#### Pinch and pan in one binary frame at a time (2026-10-25, branch `eink-frames`): upstream files touched

Research idea 2 (binary while the image moves, grey once still). A pinch or pan sent the whole view in mode 7 at every
drawn frame, over pixels the frame before was still driving, so the page needed its clean blink after every gesture
("A clean page after a pinch", above). Measured first (`LiveStrokeBench -e display true -e probe true`, the Manta,
2606141001): a full-screen mode 7 update runs its 21 frames slowly, done about 1 s after the hand-over (310 to 365 ms
up to a quarter of the screen); mode 5 is done 180 to 240 ms after it at any size; the first frame comes 80 to 140 ms
after the hand-over either way. So each grey pinch frame (one every 100 to 120 ms, the render's pace) went over
several still running.

**What changed.**
- `PresentPace.sent`/`busyFor` (pure): the areas the panel still drives, in the driver's pixels, and until when
  (`panelMs`: mode 5 250 ms; grey 370 ms, a whole screen about 1 s). `DrawingPad.flush` records every present that
  went (`result > 0`) and, asked to `hold`, keeps a pending patch over a busy area for the retry at its end (newest
  pixels then, merged by `DirtyRegions` meanwhile) instead of sending it over pixels mid-update. The retry holds as the
  flush that posted it did (`holding`). `DirtyRegions` gives patches by index so held ones stay.
- `DrawingPad.startNavigation` (both starts, two fingers and No Erasing's pan): opens a mode 5 session
  (`Device.fastStrokeMode`) on the view's raster, the page's dots, so every frame goes in mode 5. `drawNavigationFrame`
  draws the next frame only once the panel is done with the view (rescheduled at the end otherwise), so frames never
  overlap. `endNavigation` (lift or cancel) shows the last view in grey through the page's session, held until the last
  mode 5 frame is done. Where there is no mode 5 (the Nomad, 3.30.45) or the fast session fails (a grey pixel),
  frames go grey as before but are paced the same way, and only then is the page marked `ghosted` for its clean.
- `DirectEink.mode`: the session's driver mode, for the pace.

| File | Lines | What |
|---|---|---|
| `DrawingPad` | +66 −20 | `flush(force, hold)`, `busyFor`, `startNavigation`, `drawNavigationFrame(last)`, `endNavigation`'s grey last frame; the clean only after grey frames. |
| `DirectEink` | +3 | `mode`. |
| `DirtyRegions` | +3 −2 | `size`, `get(i)`, `remove(i)` for `first`/`removeFirst`. |
| `PresentPace` | Scribulor's | `panelMs`, `sent`, `busyFor`. |

**Measured** (`LIVE=1 scripts/bench_device.sh -e display true -e rounds 3`, the bench's 0.5 s pinch, a page of Brush
pen strokes). Before → after, per gesture:

| Gesture | Frames sent | Panel frames | Last frame after the lift | Clean blink |
|---|---|---|---|---|
| Pinch in with a pan | 4 to 5 grey → 2 binary + 1 grey | 140 to 165 → 72 | 2.09 to 2.15 s → 0.57 to 0.60 s | one (about 105 frames) → none |
| Pinch out to the fitted page | 4 to 6 grey → 2 to 3 binary | 145 to 190 → 108 to 176 | 2.47 to 2.72 s → 0.95 to 1.78 s | one → none |

The fitted page's end is still drawn through Android (the unscaled raster, `invalidate`), the tail after 0.9 s.

**Not checked by eye:** whether mode 5 frames over many pinches leave a faint ghost the grey last frame doesn't clear
(it drives only pixels that changed since the last binary frame). If they do, put `ghosted` back for binary frames too.
The bench: `PanelFrames` reads the frames through the shell where the app may not read /proc/interrupts (the Manta):
`scripts/bench_device.sh` puts the loop on the device for the run.

Generic: any MonoPaint build on this firmware pans the same way, and its grey frames stack the same.

#### The eraser, its ring and the sliders wait for the panel (2026-10-25, branch `eink-frames`): upstream files touched

Research idea 3. The eraser sent 469 presents in one 2.5 s stroke (the bench's 1.5 s stroke: 517 to 578), its moves and
its ring every few ms over pixels the last grey update still drove; a slider's tab one mode 5 present per pen move. The
pinch's rule (above) now covers them: a present over an area the panel still drives is held, the newest pixels sent
once it is done, merged with what changed meanwhile. The pen's own ink is never held.

**What changed.**
- `DrawingPad`: `erasing()`; the eraser's presents (`presentInput`), its last ones at the lift (`endStrokePreview(then,
  hold)`), a finger eraser taken back (`cancelFingerStroke`) and the ring's frames (hover, or the eraser's) flush with
  `hold`. The eraser no longer marks the page `ghosted`: its updates no longer overlap, so no blink after erasing.
- `SelectionFeedback` (upstream): its own `PresentPace`. A kept session's present (the same patch again: a slider's tab,
  the grey strip's marker, the droplet) over an area still driven is held (`heldOwner`, `heldArea`, `heldDirty`,
  `heldMode`); `sendHeld` at its end captures the patch as it is then, through the kept session or one of its own if
  another view's present replaced it. `clean` and `redrive` (a marker's end) wait for the area too (`waits`), dropping a
  held present there, and are recorded (`sent`). The present's bookkeeping is `shown`.

| File | Lines | What |
|---|---|---|
| `DrawingPad` | +14 −10 | `erasing()`, `hold` at the eraser's and the ring's flushes; no `ghosted` for the eraser. |
| `SelectionFeedback` | +97 −12 | `pace`, the held present and `sendHeld`, `busyFor`, `waits`, `sent`, `shown`. |

**Measured** (the Manta, 2606141001). `LiveStrokeBench -e display true -e rounds 3`, the eraser's 1.5 s stroke over Brush
pen strokes, before → after: presents 517 to 578 → 14 to 17; panel frames while erasing about 190 → about 115, in
separate runs of 19 (one update each, never two at once); last frame 240 to 270 ms → 220 to 290 ms after the lift; the
clean blink about 2.1 s after (64 to 74 frames) → none. `PickerBench` (checks build), a 24-move drag: slider 26 presents
→ 6, panel frames during 48 to 51 → 28, quiet 607 to 637 ms → 659 to 707 ms after the lift; grey strip 26 → 5 to 6,
panel frames 52 to 54 → 38 to 40, quiet 174 → 518 to 603 ms (its clean now waits for the marker's last update).

**Risks.** The eraser's trail and the ring follow in steps of about 0.37 s (one grey update), the tab about 0.25 s. The
panel times are fixed (`PresentPace.panelMs`): on a cold panel an update may still run when the next goes, and the ghosts
would come back with no blink after erasing to clear them. Not checked by eye.

Ghost budget per area (research idea 7): not done. With the pinch's and the eraser's updates no longer overlapping,
`ghosted` is left only for pinch frames sent in grey (no mode 5, or it failed), where the whole view changed anyway, so
a per-area budget has nothing to bound. Worth doing if the owner still sees ghosts after these two.

### Reverted 2026-10-25 evening

The owner found big ghosts the Supernote swipe refresh no longer cleared and an unusably slow eraser after the "eink-frames" (mode 5 pinch frames, held patches) and "eink-adapt" (learned panel times) merges; both are reverted (the sections above describing them no longer apply). Next: small A/B experiments per section before any wide change.

## Lasso options: Cut, Copy, Paste, the loop that deletes (2026-10-25, branch `lasso-options`): upstream files touched

The owner: "i was thinking the lasso, there should be options for lasso. in supernote there is a fast delete that
might be cool. or cut. and then the usual copy." Extended the owner of the selection, `DrawingPad`'s floating piece and
`LassoPlanes`; no new display path.

**What changed.**
- **The menu** (`InkWashBar.tapLasso`, rows from `DrawLayout.lassoRows`): Cut, Copy, Delete with a piece floating, Paste
  once one is kept; in use with nothing floating and one kept, Paste alone (`DrawLayout.lassoTap(on, floating, kept)`,
  which was `NOTHING` there). `app.menu`, so it opens and closes as every menu (`presentAhead`, the closed patch cleaned).
- **Kept** (`DrawingPad.kept`): a `FloatingSelection.copy()` (lifted from nowhere, its planes shared, nothing writes
  them). `cutSelection` is `deleteSelection` (now boolean) and keeps the piece; `copySelection` keeps it and leaves the
  page alone (it laid a copy and floated another until now: Copy then Paste does that). `pasteSelection` puts any piece
  down (`placeSelection`), then `floatSelection(kept.copy())` where it was kept, so it drags and goes down as a moved
  piece does, one Undo step, cutting nothing. A page of another size takes none (`FloatingSelection.fits`, which
  `carrySelection` now shares). In memory only: lost when the activity is recreated.
- **The loop that deletes** (`DrawingPad.lassoDeletes`, set by the Lasso's hold, `InkWashBar.lassoWays`, a `Theme.menu`
  of Select and Delete, the one in use marked): `liftLasso` lifts as before, then `erase` (Delete's body, shared) takes
  it off at once, wet paint dried first: the same `LassoPlanes.place(…, false, true)`, one Undo step, the selected pair
  and the fluid, permanent paint too, as Delete and the eraser's Everything; refused on a locked page; the Lasso is off
  under No Erasing as before. Shown by `renderArea` over the cut, `renderDirty`, `flush`, Delete's path. The Lasso's
  icon is Phosphor's trash in that way (`lassoFaces`, through `selectionFeedback.update` as the eraser's mark), its
  screen-reader name "Lasso: Delete" (`DrawLayout.lassoSaid`). Not saved: every start is Select.
  Since the same day (branch `trash-tool`) the hold, `lassoWays`, `lassoFaces` and `lassoSaid` are gone: the **Trash**
  (`InkWashBar.trash`, `tapTrash`, the eraser row's right cell) takes the loop up with `lassoDeletes` set, the Lasso
  with it cleared (`takeUpLasso(deletes)`); `lassoChanged` marks the Lasso or the Trash. Upstream: `DrawingPad.lassoDeletes`'s comment alone.
- **Icons:** Phosphor Bold `scissors` (Cut) and `clipboard` (Paste), `ic_ph_scissors`, `ic_ph_clipboard`.

| File | Lines | What |
|---|---|---|
| `DrawingPad` | +41 −27 | `lassoDeletes`, `kept`, `hasKept`, `cutSelection`, `copySelection` (keeps), `pasteSelection`, `deleteSelection` boolean, `erase` (shared), `liftLasso`'s Delete way, `carrySelection` through `fits`. |
| `FloatingSelection` | +2 | `fits(document)`: the page it came from is that size. |

Scribulor's: `InkWashBar` (+49 −8: `tapLasso`'s rows, `takeUpLasso`, `lassoFaces`, `lassoWays`, the hold), `DrawLayout`
(+15 −2: `lassoTap`'s `kept`, `lassoRows`, `lassoSaid`, the words).

**Checks.** `LassoChecks.pasted` (Copy leaves the page, Cut and its Undo, each Paste a fresh copy where it was kept,
moved or not, its own Undo, another page of the size, not another size), `LassoChecks.fastDelete` (the ringed piece
gone unmoved, permanent Pen ink and wash too, the rest kept, one Undo), `DrawLayoutChecks.lasso` (rows, the tap with
something kept, the said name). Not checked on the Manta by eye.

## Page turns move the marks alone (2026-10-25, branch `pagebar-underline`): upstream files touched

The owner: "i think we can avoid a refresh of the top bar as well unless there is a new page added, just move the
underline". The side bars' way ("Side bars alone") for the page bar: a turn puts on the panel what it changes, and the
bar is drawn again whole only when the pages change.

**What a turn did before** (from the code; not measured on the Manta, which was not connected). Every `updatePages`,
each turn too, went to `PageTurner.show` and `Strip.shown`: every preview invalidated, Android's frame of the whole strip.
In that frame:
- the page turned to drew blank: the page drawn on is never drawn from its picture (`except`), and the sample of the page
  left did not count for it. `redrawLiveWhenIdle` 800 ms later sampled it and put it back (a second update);
- the page left drew blank too when it had been drawn on (its picture is made as it is left, `leavingPage`), then
  `picturesArrived` twice (the picture, then its preview's dots): blank, blank, its picture;
- in a notebook with more pages than slots the window was round the page drawn on (`NotebookLayout.window`), so every
  turn slid it a tile and every preview changed.

**What changed.**
- `PageTurner.show(index, count, turned)`: `PaintActivity.changePage` (every turn: ‹ ›, a tap or riffle on the strip,
  Timed Pages' Next Page) says `turned`. With the same count and the page turned to in the window, the arrows, the number
  and the strip are captured, the turn applied, and the changed pixels go out in one `SelectionFeedback.updateParts` of
  the bar (ink and paper boxes binary, the rest one grey update), with no Android frame asked of the strip. Anything else
  (a page added, deleted or moved, a layer, another notebook, a page turned to off the window) is `Strip.shown` as before.
- `Strip.drawnOn`: the previews' marks (ink frame, underline, ghost tabs) follow the page the bar last showed, not the
  book's, so the capture before the turn shows what the panel shows.
- The window is kept while the page drawn on shows in it (`StripGesture.shows`, pure), and goes round that page only when
  it leaves it or the pages change.
- A preview is drawn from the sample (`LayerThumbnail.renderPage`) only for the place and layer it was taken of
  (`livePlace`, `liveLayer`), the page left too, until its picture is ready (`NotebookScreens.drawStackPreview` now says
  whether it drew everything). A page turned to with no sample shows its picture, as it showed before the turn;
  `redrawLiveWhenIdle` no longer samples it until it changes. When the sample moves to another page, that page goes back
  to its picture in the same update (the strip's).

| File | Lines | What |
|---|---|---|
| `PaintActivity` | +5 −3 | `updatePages(turned)`; `changePage` says a turn. |
| `WidthInstrumentation` | +2 −2 | `-e pickerBench pages`. |

Scribulor's: `PageTurner` (above), `StripGesture.shows`, `NotebookScreens.drawStackPreview`/`drawPicturePreview` return
whether they drew, `InkWashBar.pagesChanged(…, turned)`, `PickerBench` (`pages`: a notebook of four pages in the checks
app's folder, ‹ and › three times each, a stroke then a turn off and back, two rounds; the page bar's previews listed
among the views Android draws again).

**Triggers of the page bar, after.** A turn: one `updateParts` of the bar (above). A stroke: the page's preview
800 ms after it settles (`redrawLiveWhenIdle`, one preview; the strip when the sample moves off another page). A picture
ready (`picturesArrived`, the strip's fast update of what changed). Tracing paper changed, a riffle's window sliding at an
end and a riffle or lift let go off its place (`Strip.update`). A timed page's blink (the number alone). The +, a page
deleted, moved or added as a layer, another notebook: `Strip.shown`, all of it. The auto-refresh after a pinch
(`InkWashBar.invalidate`): the bar redrawn and cleaned, unchanged. A save offers the page's picture but draws nothing.

**Checks.** Desktop RAN 128 (`StripGestureChecks.tiles`: the kept window). Not measured on the Manta and not checked by
eye: the Manta was not connected. To measure: the checks build (`-PisolatedChecks -PfastChecks`), `-e pickerBench pages`,
before is `7f92804b` with the bench's `pages` mode alone.

**Risks.** The window no longer centres on each page turned to: it slides only when the page leaves it, then goes round
it. A capture that differs from the panel (a turn within 800 ms of a stroke on a page turned to) leaves pixels for
Android's next frame of the bar, which the firmware drives where they differ. Generic: none (Scribulor's bar).

## The side bar rearranged (2026-10-26, branch `bar-layout`): upstream files touched

- **Upstream files touched:** `PaintActivity` alone: the menu's Finger row removed, and `fingerMenu`'s pick tells the
  bar (`InkWashBar.showFinger`); the menu is now opened from the column's Finger button. Everything else is Scribulor's
  (`DrawLayout.COLUMN` and its parts, `InkWashBar`'s rows, `DrawLayout.brushWords` with a Basic tool's line).
- **Layout:** the smudge tool and Finger, then the eraser and the Trash, straight under the tray (`Part.TOOLS`, no gap);
  the Dropper and the Lasso (`Part.WET`), then Play and the wet indicator (`Part.PLAY`); Template Visibility in Menu's
  row. The same rows as before, two gaps not three (`GAPS`), so the Nomad in landscape still fits at its least
  (`DrawLayoutChecks.NOW`: 613 dp and 0 to spare). The brush line's name room is 310 dp (280 and the drop's 30).
- **Checks:** `DrawLayoutChecks` (order, rows, heights, the menu without Finger, the Finger button, the brush line's
  description), `NomadUiChecks.layout` (the rows on the device; not run here). Generic: none.

## The page fitted whole and centred (2026-10-26, branch `zoom-fit`): upstream files touched

- **Why:** the owner, "when switching to zoom mode or back.. will some part of the image be obscured? let's make it
  fit neatly in both". The pad is laid out beside the bars, not under them, so its size is the free area; a page of
  that size filled it at 100% already. Any other page (made the other way up, made in Zen Mode, from an older layout)
  opened at 100% in MonoPaint's home placement, an edge hidden past the pad, and Zen Mode left a smaller page in the
  top corner.
- **Upstream files touched:** `CanvasViewport` (+10 −14): while untouched since a reset, `configure` fits the page
  (`fitScale`, at most 100%) and centres it on whole pixels, so a change of room (Zen Mode, a turn) fits it again;
  `opening`/`openActualSize` removed. The pinch's floor, `minZoom`, is unchanged (100%). `DrawingPad` (+2 −9): the
  100%-at-home opening and `homeOnScreen` removed; `actualSize` kept for MonoPaint's Expand canvas. `ZoomButtonTouch` (+2 −2): the double tap fits instead of going to 100%. Generic
  in part: a centred fit suits MonoPaint too, but its own "open expanded pages at 100%" choice is reversed here.
- **Cost:** a page not the pad's size is now drawn through the zoomed raster (`ViewportBitmap`) while fitted, as when
  zoomed; a pad-sized page is not (identity, as before).
- **Checks:** `ZoomFitChecks` (Manta and Nomad, either way up, pages from either turn and from Zen Mode: whole,
  centred, the fitted scale; the double tap and Zen Mode back to exactly that view; pinch floor 100%), `ViewportChecks`
  updated; `ZoomButtonChecks` (device, not run here) updated.

## Layout v3 (2026-10-26, branch `layout-v3`): upstream files touched

- **Upstream files touched:** none. `PaintActivity` already sizes the foot row from `DrawLayout.footHeight` and
  `BRUSH_LINE`, so the taller brush line needed no edit there; the pad is laid out above it, so the zoom fit
  (`CanvasViewport.configure`) sees the smaller free area.
- **Layout** (the owner's mockup): `DrawLayout.COLUMN` is Menu, the tray, `Part.TOOLS` (the smudge tool and the eraser,
  a tray row each, `TOOL_ROWS` counted with the slots), `Part.BLOCK` (undo and redo, the Dropper and the Lasso, the
  Trash: `BLOCK_ROWS` icon rows), the faces, `Part.PLAY` (the wet indicator and Finger, Template Visibility and Play:
  `PLAY_ROWS` small rows), the countdown, five gaps (`GAPS`). Icon rows 44/40/30 dp (were 56/48/40). `GRID_ROWS`,
  `Part.UNDO` and `Part.WET` are gone. The smudge tool's and the eraser's rows show the owner's drawings
  (`InkWashBar.drawing`, `res/drawable/tool_smudge.xml`, `tool_eraser.xml`: traced vectors in px since 2026-10-26, drawn as Drawables); `heldBrush` and
  `ic_dirty_brush_paint` went (a dirty smudge brush shows `SlotTile`'s dab). The brush line is 32 dp, its preview 80 x
  32 dp (were 18, 40 x 14).
- **Checks:** `DrawLayoutChecks` (order, counts, heights, `NOW`: 924 and 291, 874 and 0, 830 and 32, 613 and 0; the
  drawings' greys; the preview's size), `ZoomFitChecks` (the Manta's pad 1785 x 2387 px, 2401 before), `NomadUiChecks.layout`
  (device; not run here), `DrawScreenPictures`. A page made at the old pad size now shows fitted, about 99%, through the
  zoomed raster (see the zoom fit above). Generic: none.

## Layout v4 (2026-10-26, branch `layout-v4`): upstream files touched

- **Upstream files touched:** none. `DrawLayout.COLUMN` puts `Part.FACES` before `Part.BLOCK` (the owner: "move undo
  redo eye drop lasso and recycle to below picker"); the same parts, rows and five gaps, so the heights and `NOW` are
  unchanged. **Checks:** `DrawLayoutChecks.order`, `NomadUiChecks.layout` (device; not run here).

## Slot drawings (2026-10-26, branch `slot-drawings`): upstream files touched

- **Upstream files touched:** none. A tray row shows its brush's picker drawing (`DrawLayout.drawing`: the brush's
  Basic tool, `ToolPresets.find`, looked up in `PICKER`), cut `TIP_TRIM` at its handle end and flush left, through
  `InkWashBar.drawing`, the smudge tool's and the eraser's path, which now draws any `Drawable` (PNG or vector).
  Undrawn brushes keep `WildSample.slotTray`'s stroke; Finger's menu keeps the stroke for every slot (`trays`, drawn in
  the background). **Checks:** `DrawLayoutChecks.slotDrawings`, `DrawScreenPictures` (the mockup's tray). Generic: none.

## Slot drawings matched by origin (2026-10-26, branch `slot-drawings-match`): upstream files touched

- **Upstream files touched:** none. `ToolPresets.origin`: the Basic tool a brush came from (a brush keeps no "from"):
  `find`, else the one preset of its style and seed (a rename and Brush Lab's dials keep both), its name less a copy's
  number choosing among presets sharing one, else the preset so named. `DrawLayout.drawing` uses it, and a tool not in
  `PICKER` takes its style's one drawing (Pencil: HB pencil), none where a style has several or none (Print since Ben-Day brush and Engraving pen joined
  `PICKER`, 2026-10-26, branch `drawn-round2`).
  **Checks:** `DrawLayoutChecks.slotDrawings` (the owner's three slot codes, a rename, a brush of its own). Generic: none.

## Rotate turns the page (2026-10-27, branch `rotate-canvas`): upstream files touched

- **Upstream files touched:** `DrawingPad` (`place` gives `PageView.map` no turn of its own, so the page lies upright in
  the turned canvas and `CanvasViewport` fits it unswapped, afresh on each turn (`fittedTurn`); the pen's signed tilt
  turned from the panel's axes by `PageView.turn`), `CanvasGravity` (the app's turn added to the display's, from the
  drawing screen's constructor only; Brush Lab's stays 0), `PngExport`, `PageOverview`, `DrawingBrowser` (no more
  turning a copy to match the app: the page is shown upright), `FullscreenController` (Expand canvas's margins with no
  turn), `PaintActivity` (the Rotate button 72 dp, its icon 36 dp). `WordsMode`: a new magnet's turn is 0. The page's
  tones are never turned; MonoPaint's counter-turn (`PageView.map`'s quarter) is kept for its checks. Fast e-ink: the
  turned page goes through the screen-sized viewport raster and `DirectEink`'s quarter-turned copy, the path landscape
  already took whenever the page was scaled. **Checks:** `PinchPanChecks.rotateTurnsThePage` (upright, whole, centred,
  largest fit; page → canvas → page at the fit and zoomed; tilt turned as gravity), `RotationPromptChecks` and
  `OrientationChecks` (device; not run here). Generic: none.

## Opacity on the shade strip (2026-10-27, branch `shade-opacity`): upstream files touched

- **Upstream files touched:** `PaintActivity` (`toolbarFixed` no longer private, so the strip's Opacity refuses in a
  fixed toolbar with its note). Scribulor's own: `GreyStrip` (`mirrored`), `SwatchBar` (the strip's long press),
  `InkWashBar.opacityMenu` (the panel, through `openPanel`), `DrawLayout.opacity` (Opaque only; the slot's brush with its
  Medium's Opacity dial set, an Undo step as Fling's). No engine change: every stroke resolves the slot's brush, so
  `ResolvedBrush` reads the dial as it was. **Checks:** `DrawLayoutChecks` (which brushes, kept per slot, read by the
  stroke, Undo). Generic: none.

## The owner's notes page (2026-10-27, branch `notes-1027`): upstream files touched

- **Upstream files touched:** `viewport_bitmap.c` (`nativeRender` takes `lines`: the view pixels beyond the page are the
  owner's fine lines, `backdrop()`, instead of white; one test per outside pixel, no new pass or layer),
  `ViewportBitmap` (`backdrop`, false unless the drawing screen sets it, so `dithered` and every copy stay white),
  `CanvasViewport` (`backdrop()`: under 100% as the readout rounds it), `DrawingPad` (`updateViewport` sets the raster's
  `backdrop` on every view change, before it is drawn), `PaintActivity` (Play's menu without Diary Page and the ready
  brushes; `playBrush(Brush)` removed). Scribulor's own: `WetIndicator` (the dry drop is the owner's
  `ic_drop_dashed`, drawn as the wet drops are; its dashed Phosphor path gone), `DrawLayout` (`DIARY`, `PLAY_BRUSHES`
  removed), `res/drawable/tool_eraser.xml` (the owner's new block eraser). MonoPaint's `WetDroplet` and `DropletIcon`
  (its hidden toolbar's rippling droplet) are untouched. **Checks:** `ZoomFitChecks.backdrop` (lines only under 100%,
  only the drawing screen asks), `DrawLayoutChecks` (Play's rows, the dry drop). Generic: none.

## Size tab as the shade tab (2026-10-27, branch `size-strip-ghost`): no upstream file touched

- The owner: "weird ghosting glitch with the size selector indicator. i move it and it blinks and sometimes isn't all
  displayed ... the shade picker doesn't have this same problem." Both tabs move through `StripMarker` over a narrow
  column of their own, and a lift of either is the same `SwatchBar.show` (one `updateBinary` of the tab and the dot); no
  relayout, brush line or slot row on either. The one difference: `SizeSlider` set `StripMarker.binary`, so its moves
  were the Manta's stroke mode 5 (which skips pixels it takes as unchanged and leaves a trail) and, 250 ms after the
  lift, `SelectionFeedback.redrive` drove the tab's whole column near-white then white, two grey updates: the blink.
- `SizeSlider` (Scribulor's own): no longer sets `binary`, so its moves are the strip's mode 7 and nothing runs after
  the lift on the Manta; its lift then puts the tab back where the size is (`follow(inUse)`, as `GreyStrip`), for a lift
  that changed nothing. `StripMarker.binary` and `SelectionFeedback.redrive` are left as they are, unused, so the
  binary path is one line to try again. **Checks:** `DrawLayoutChecks.extras` (the slider's marker as the strip's, no
  `pen.binary`, no relayout or Android redraw in either). Not tried on the Manta here.

## Sand timer (2026-10-27, branch `sand-timer`): upstream files touched

- The owner: "the per page timer, i'd like to put it in the toolbar ... just have a sand timer icon, press it to have it
  open a menu and you can set a timer", then a plain drawing timer, not Timed Pages.
- **Upstream files touched:** `PaintActivity` (`sand`, a `SandTimer`; `timedLook` counts it while resumed, shows its
  countdown or icon where no Timed Pages countdown or timed sections take the row, and its popup at zero once the pen
  is up; `countdownTapped` opens its menu there; `setSandTimer`; `onPause` pauses it; `dispatchTouchEvent` closes its
  popup on any touch). Scribulor's own: `SandTimer` (pure: choices, count, pause, zero once), `InkWashBar` (the row's
  icon, `timerMenu`, `timeUp`, `countdown` takes `sand`), `NotebookOptions.MINUTES` (was `OLD_MINUTES`, now shared),
  `DrawLayout` (comments: the TIMER row's room was already kept on every screen), `res/drawable/ic_sand_timer.xml`
  (a placeholder, listed in `docs/icons/TO-DRAW.md`). Timed Pages, `TimedPages`, the page record and the lock are
  untouched. **Checks:** `SandTimerChecks` (choices, countdown, pause, zero once, no page held), `DrawLayoutChecks`
  (the icon's cell fits the row on all four screens). Generic: none.

## Timed Pages a guide, Lock Pages a setting (2026-10-27, branch `timed-guide`): upstream files touched

- The owner: "i would like to be able to edit after the timer runs out, it's just a guide", then "could the lock be a
  setting in the notebook? like, timers lock pages?". At zero `TimedPages.tick` still runs the page's clock out
  (`PageClock.lock`, kept in the page's `TIME` record, unchanged), now `State.TIME_UP`; whether that locks the page is the
  notebook's **Lock Pages** (`NotebookOptions.lockPages`, off by default; `TLCK` in `BookOptions`, `TSRX` in the recovery).
  `PageClock.locks(timerLocks)`: a clock run out with time spent is a timer's and locks only under Lock Pages; one with
  none spent is a PDF's page (`PictureImport.pdfPage`, `lock(0)`) and always locks. Timers always spend 30 s or more, so
  old files tell them apart with no format change.
- **Upstream files touched:** `ToneDocument` (`timerLocks`, set by `DrawingBook.current`; `locked()` is
  `clock.locks(timerLocks)`, and `canUndo` / `canRedo` / the undo step use it; `unmarked` still counts any run-out clock
  as a mark), `PaintActivity` (`timedLook`: at `TIME_UP` the sand timer's popup with Next Page, `InkWashBar.timeUp(next)`,
  in place of the AlertDialog; the countdown row shows as an untimed page's once run out, unless locked; `sectionLook`
  fences a finished section only under Lock Pages), `DrawingBook` (`current` tells the page; `setOptions` /
  `restoreOptions` refresh it). Scribulor's own: `TimedPages` (reads the raw run-out flag, so a page run out never counts
  again), `LayerEdits.mergeDown`, `LibraryCard` / `NotebookScreens` (the Lock Pages row, Off and On, greyed unless
  `locksApply`), `NotebookRules` (`LOCK_PAGES`; `TIME_UP`, `TIME_UP_SAID`, `STAY` gone), `CardSheet` (Timed Pages' line).
  **Checks:** `NotebookRulesChecks` (off: editable at zero; on: locked; the PDF page locked either way; an older notebook
  reads Lock Pages off; file and recovery keep it on), `LibraryCardChecks` (ten rows, the row set in place),
  `TracingChecks`, `PageStacksChecks`, `NotebookOptionsChecks`. Generic: none.

## Housekeeping (2026-10-27, branch `cleanup-1027`): upstream files touched

No behaviour changes. Ten unused drawables deleted (`ic_blur_drop` and nine Phosphor icons); dead members gone
(`InkWashBar.drawTab`, `InkWash.selectPair`, `StayIn.pocketAt`, `ModularFeatures.FILL_PLANE`, `NotebookCovers.USE_PAGE`);
`NotebookLibraryBench` deleted (no script ran it); finished specs, plans, notes, mocks, dated reports and the web
prototypes but `modular-lab.html` moved to `docs/archive/`, sub-paths kept. Stale comments and docs brought up to date.
- **Upstream files touched:** `PaintActivity` (`playBrush` renamed `pickBrush`, the tool picker its only caller; comments).
  Generic: none.

## More room in the side column (2026-10-28, branch `toolbar-room`): upstream files touched

Undo and redo, Shapes, Play and Refresh left the column (`InkWashBar`, `DrawLayout`: two icon-block rows, two small
rows); the sand timer beside the wet indicator; Show Kinds a column button; Refresh on the page bar (`PageTurner.ghost`,
`DrawLayout.barParts`); Export Now and Delete Notebook at Notebook Settings' foot (`NotebookScreens`,
`LibraryCard.settingsLayout`). Undo and redo stay on MonoPaint's `MultiFingerTap`, unchanged.
- **Upstream files touched:** `PaintActivity` (the menu's rows: Play in, Export Now, Rename, Delete Notebook, Show
  Kinds, Daily Prompt and Replay Page out; Play's menu: Shapes, Daily Prompt, Replay Page in, Stamp, Surprise Me and
  Fling out, `toggleFling` gone; `exportNow` package-private for Notebook Settings). Generic: none.
- **Checks:** `DrawLayoutChecks` (the menu's rows, the column's rows, the page bar's parts), `LibraryCardChecks`,
  `NotebookExportChecks`.

## One Lasso, Refresh back in the column (2026-10-28, branch `lasso-merge`): upstream files touched

The Trash's button left the column: the Lasso's bar over a floating piece (`InkWashBar.lassoBar`, the label's popup,
`showLabel` split so it can take a place; `DrawLayout.LASSO_BAR`) holds Cut, Copy and Delete, and the Lasso's tap
opens Paste alone (`DrawLayout.lassoTap`; `lassoRows` gone). The edge strip's hold keeps `lassoDeletes`. Refresh left
the page bar (`PageTurner.ghost` gone, `DrawLayout.GHOST`, `GHOST_GAP` gone, `barParts` Zoom, the number, the gap) for
the Trash's row, the whole row its target (`InkWashBar.ghost`). The sand timer drawn as the eyes (`showModes`).
`Popups.bounds(root, view, area)` maps a part of a view. `ic_ph_recycle` and `ic_ph_scissors` deleted, unused.
- **Upstream files touched:** `DrawingPad` (+22 −7: `lassoBar()`, called where a piece floats, is grabbed, released,
  ended or carried to a page; `lassoDeletes`'s comment). Generic: none.
- **Checks:** `DrawLayoutChecks` (the Lasso's tap and bar, the column's rows, the page bar's parts), `NomadUiChecks`
  (Refresh's row).

## The blow dryer fast-forwards (2026-10-29, branch `dryer-fastforward`): upstream files touched

The wet indicator's tap runs what is wet on to its end instead of laying it where it is (the owner: "looks like the hair
dryer just stops the image as it is instead of accellerate the drying processing"): `WatercolorSession.hurry` (a Slow
wash's ticks, halos', a drifting bath's, wet paint's slices back to back with no pace and no work ahead; a bath that
can't drift prints at once; shown once; capped at `HURRY_MS`, then laid where it is), `DryingTicker.hurry`,
`WetIndicator.Host.dry`/`hurrying` (the dryer blows round while it runs), `BrushTestPad.dry`, `InkWashBar`, `TestArea`.
- **Upstream files touched:** `DrawingPad` (+2: `dryNow`, the bar's tap; `settleDrying` no longer the tap). Generic: none.
- **Checks:** `SettleWorkerChecks.hurried` (tapped equals left to dry: Slow washes, gravity, baths, halos exactly; wet
  paint within a tone). Bench: `ModularPerfBench dryer`.

## A prompt not acted on leaves the page (2026-10-29, branch `prompt-drop`): upstream files touched

The owner: "for prompts, if it's closed without doing it, remove it from the page". `ToneDocument.dropUnusedPrompt`
(nothing on the page but the card, `unmarkedButPrompt`, which `unmarked` now builds on; or no Undo step done since it was
given, `promptTop`, in memory only) and `PaintActivity.leavePrompt`, run before the page is kept: `changePage`,
`pageEdit`, `leaveNotebook`, `onPause`; `promptLook` drops a card found on opening with nothing else on the page.
- **Upstream files touched:** `ToneDocument` (+16 −3), `PaintActivity` (+17 −3). Generic: none.
- **Checks:** `DailyPromptChecks` (dealt then left blank, drawn on, drawn then undone, drawn on before, read from a file).
## Nib Preview, a notebook setting (2026-10-29, branch `nib-preview-setting`): upstream files touched

The owner: "for the dot preview.. have it enabled by default but able to be disabled in the notebook settings?". The
hover ring's dots are the notebook's **Nib Preview** (`NotebookOptions.nibPreview`, on by default; `NIBP` in
`BookOptions` and `TSRN` in the recovery, each written only when off, so every older notebook reads on). Built as Lock
Pages is: `LibraryCard.Part.NIB_PREVIEW` (the page group, after See-Through Pages), `NotebookScreens` (Off and On chips).
The Dropper's ring and the eraser's live ring are untouched.
- **Upstream files touched:** `DrawingPad` (+1: `hoverOutline` hides the ring while `app.rules().nibPreview` is off; no
  notebook, `NotebookOptions.DEFAULT`, shows it). Generic: none.
- **Checks:** `NotebookOptionsChecks` (its record alone, the file and recovery keep it off, absent reads on),
  `LibraryCardChecks` (eleven rows, the row set in place, the hover check in `DrawingPad`).
## Pressure range with a knee (2026-10-29, branch `pressure-range`): upstream files touched

Firmware 3.31.45 widened the Manta pen's pressure (hard strokes .63–.83, `docs/TILT_INPUT.md`), so MonoPaint's
`.05–.45` line read the whole firm half of the hand as one full press. `ToolSettings.travel` keeps that line up to .30
(travel .625) and runs on to 1 at .78; `pressure` is its inverse. The pen eraser (`InkWashSizes.diameter`, Scribulor's)
no longer broadens with lean: the owner's natural ~33° hold had it at full size from a usual press. `BodyStamper.diameter`
keeps the same lean boost for bodies with a Tilt cell (full from about .28 at that hold): unchanged, a brush's look.
- **Upstream files touched:** `ToolSettings` (`travel`, `pressure`, `KNEE`/`KNEE_TRAVEL`/`FULL`), `ToolStroke` (+1 −1)
  and `NativePen` (+1 −2): their own copies of the old line now call `travel`. Generic: any MonoPaint pen on that
  firmware wants the knee.
- **Checks:** `InkWashSizesChecks` (identical below .30, continuous there, 1 at .78, round trips); `ToolChecks`,
  `AirbrushChecks`, `EraseChecks`, `BrushTextureChecks` press .78 where they meant a full press; `EraseBothChecks` and
  `MirrorChecks` give their hands as travel (their pages unchanged); `WetPaintChecks` re-pins eight settled pages whose
  travel comes back through the knee 1 ulp apart (every mid-stroke page unchanged).
## Brush Lab cleanup and New, Open, Save (2026-10-29, branch `brushlab-cleanup`): no upstream files touched

The owner's Brush Lab edits: no toolbar band (the lab works on the slot it opened on), the drawing screen's picker box in
place of its sizes and grey bar, no Basic mode, no Tweak It, Open's sheet closes without a code; then option A, New,
Open, Save and Save as New with an Edited mark and a suggested name. All Scribulor's own classes.
- **Shared, not forked:** `PickerBox` is `SwatchBar`'s box (the grey strip and size slider over each other, the touch
  split on the diagonal) pulled out whole; `SwatchBar` builds it, Brush Lab builds another inside a `QuarterTurnLayout`
  turned 90°. `PickerBox.show` marks both and sets their screen-reader words for either screen.
- **Gone:** `SlotRow`, `LabLibrary`'s slots and variations (it keeps `sampler` and `traySampler`), `LoopCard.slot` and
  `setFixed`, `LabText.changes` and `at`, `BrushLab`'s variations, `setAdvanced`, `BACK_ROOM` and the Basic lock bypass,
  `PaintPreferences.brushLabAdvanced` (its stored flag is left unread). `ModularVariations` stays: the checks use it to
  roll brushes, and it mirrors the web lab's Tweak It.
- **Library:** `BrushLibrary.update(id, brush)` writes one entry over in its place (its id kept, stamped as used);
  `ToolSwatches.moveLook` moves a brush's own size and grey (`brush_look_<code>`) to its new code.
- **Upstream files touched:** none. Generic: none.
- **Checks:** `LabLayoutChecks` (one mode, no band, the picker box's squares, the Open sheet, the new budget table),
  `BrushLabChecks` (`openSave`, `saveAsNew`, `suggests`, `update`, `newBrush`), `LabSlotsChecks`, `ModularLabChecks`
  (every back-room choice offered), `HandHatchChecks`, `PartSampleChecks`, `BlendChecks`, `ScreenChecks`; the device's
  `ScreenShots` shoots the one mode and the Open sheet.
## Smooth body rims (2026-10-29, branch `springy-edge`): no upstream files touched

The owner, testing a chisel wash on canvas: "i see a repeated pattern along the edge instead of a smooth line". The
body's dabs scalloped its rim into a row of beads: `BodyStamper.spacing` kept the scallops under a quarter of the soft
edge, and for a round dab only, where a chisel's or flat nib's oval bends much tighter at its ends. Scribulor's own
engine class.
- **`BodyStamper`:** `spacing(D, softK, aspect)` holds the scallops under `RIM_DIP` (a sixteenth) of the soft edge at
  the oval's sharpest bend (`r · aspect²`, the rule the Filbert alone had); `filbertSpacing` is that plus the crescent's
  bound. Ovals, and big crisp rounds, lay closer dabs (desktop bench: Gouache's body 13 to 24 ms a 240 px stroke, the
  pen's wait unchanged; untimed on the Manta). 12 of 126 presets' pictures change, at their rims: the dry brushes'
  sawtooth edges smooth out; Print's screens keep their dots.
- **Not done:** a dab still lands only where a sample step falls, up to a sample gap past its spacing; stepping a
  quarter spacing removed the last faint repeats but cost more (Gouache's body 13 to 28 ms) and left an Instant stroke's
  end up to a spacing short of the pen (an Instant stroke's lift never lays the last point; `BlendChecks`' paint-behind
  golden caught it).
- **Upstream files touched:** none. Generic: none.
- **Checks:** `EdgeRippleChecks` (two chisel washes and a crisp opaque round, 40 and 98 px: no single repeat along the
  rim stronger than 8 of 255; the chisels 14 and 22 before).

## Smooth line edges (2026-10-29, branch `line-edge`): no upstream files touched

The owner, of the Springy brush's faint scallops found while fixing the body's beads: "yes fix that too". The same cause
as the body's, in the line's own grid. Scribulor's own engine class.
- **`LineStamper`:** the big nib's distance grid is `BodyStamper.spacing` of the nib (capped at the old 0.08 of its
  size): its sharpest bend, under `RIM_DIP` of its edge, a crisp nib's pixel or a stick's fuzz (about 1.5 spreads of its
  half width), so sticks keep their old grid and dust. Its old bound let the scallops dip half a pixel. A crisp nib now
  steps a grid at most, so every grid line gets its dab; it stepped a tenth of the nib, past the grid, so a quick stroke
  (samples 12 px apart) laid a dab a step. A quarter grid (each dab nearer its line) was smoother still but left five
  fringe pixels a tone off in `BlendChecks`' Invert twice and four masked pixels unerased in `ResistChecks`. The Springy
  brush at 98 and 150 px: the strongest repeat along the edge 7 to 17 of 255 before, 1 to 2.5 after.
- **Web lab:** `prototypes/modular-lab-src/engine.js`'s `stampLine` has the same grid and step (Brush Studio and the
  match lab read it), so the app still draws the lab's pages; `node scripts/resist_goldens.cjs` drew three resist
  goldens again (`fluid-ink`, `fluid-light`, `wax-ink`); the blend goldens came out the same.
- **Cost** (desktop bench, a 1.5 s stroke, line ms): Fineliner 5 to 10 at 240 px, Brush pen 5 to 9, Marker 5 to 9, Dip
  pen (a flat nib, tight ends) 12 to 22, Pencil 10 to 23; Charcoal and Pastels as before; the pen's p95 wait unchanged.
  Untimed on the Manta: ink lines draw live at any size (`QuickStroke.ANY`), graphite past 260 px goes quick.
- **Pictures:** 113 of 126 presets' pictures are byte for byte (most draw at 16 px or under, a dab every step); a few
  sticks, crayons and tones move a speck or two.
- **Upstream files touched:** none. Generic: none.
- **Checks:** `EdgeRippleChecks` also draws the Springy brush at 98 and 150 px, samples 3 and 12 px apart (before: up to
  17 of 255). `WetPaintChecks` and `LegacyConversionChecks` re-pin the settled pages of lines past 16 px (every page
  mid-stroke unchanged).

## Crayon's lift (2026-10-29, branch `crayon-lift`)

The owner: "this crayon i've been using has a long delay between drawing and the line showing up on screen". The
Manta's stroke timing showed a Wax crayon keeping up under the pen like Ballpoint but its lift costing about 170 ms
(finish 105, final render 50, flush 15; Ballpoint's 5 / 0 / 0.2). The page shown never changed at the lift: it composed
the whole stroke again onto the masking fluid (`layWax`, its wax that washes slide off) and the fluid's write marked the
stroke's whole box to render again, though the fluid shows only as its hatching.
- **`ModularStroke`:** a live crayon (not a render, Drying or a fill) composes its wax beside each compose into a
  `StrokeTones` over the masking fluid as it is (`waxTones`), so `layWax` only writes it. Byte for byte as before
  (`CrayonChecks.lift`: against a quick stroke, whose render still composes at the lift, on paper and over fluid).
- **Cost** (desktop, a 1.5 s stroke, 12 and 24 px): the lift 2.5–3.2 ms to 1.0–1.4 (what is left is the write and
  Undo's copy); the render after it 1.2 M px to none; a sample 0.023 / 0.039 ms to 0.028 / 0.052. Untimed on the Manta.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +18 −2 | `maskTones`: the masking fluid's tile as it is (paper for none), read while a gesture on a layer is in progress. The block write on the masking fluid grows the dirty area only where its hatching (`liftable` ≥ 32) comes or goes, all the fluid shows. Generic: any tool writing the fluid. |
| `StrokeTones` | +13 −6 | `onMask`: tones composed over the masking fluid rather than the gesture's layer. Scribulor's own class. |

## A new application id and the handover (2026-10-30, branch `public-prep`): upstream files touched

For the public release the id became `ing.ell.mybaddrawings` (`app.helpyy.inkwash` named the owner's private project;
`ing.ell` is the owner's domain). Another id is another app, which can't read the old one's private files, so the
old id gets one last build (`-PlegacyId`, signed with the old key from `keystore.properties`' `legacy.*` entries)
that leaves its preferences and private drawings folder in `Document/My Bad Drawings/.handover` (a dot name, so the
shelf never lists it), and the new app takes them once (`HandOver`, pure; `DrawingStorage.leaveHandOver`,
`takeHandOver`, `handOverWaiting`). Release signing now reads a local, gitignored `keystore.properties`, else
Android's debug key, so anyone can build.
- **Upstream files touched:** `app/build.gradle` (the id, `-PlegacyId`, signing). `PaintActivity` (+12: takes the
  handover before the preferences are read; `loadDrawing` starts the app again if file access was just allowed and a
  handover waits, so nothing this launch holds is saved over it; one note once the page is up; `onResume` and
  `onPause` leave it, in the old id's build only). `DrawingStorage` (+45: `allowed` split from `sharedAllowed`, the
  handover's three calls). Generic: none.
- **Checks:** `HandOverChecks` (typed round trip, content:// left out, drawings copied without overwriting the new
  app's, taken once, the old app stops writing once taken).

## Shapes and Play back in the column; the Self-Portrait Prompt, a notebook setting (2026-10-30, branch `inkwash-play-row`): upstream files touched

The owner: "move shapes button to below visibility and timer, and move fun button beside it"; "('daily prompt', maybe
rename it as 'self portrait prompt'? ...) actually in that case put it in notebook settings as an optional thing, new for
every new page". `InkWashBar` gets `shapes` and `play` back (as before 2026-10-28: `tapShapes`, `modesChanged`, Shapes'
side bar in `lassoChanged`) in a small row under Show Kinds and the sand timer (`DrawLayout.PLAY_ROWS` 3); `MirrorsMenu`
and `WordsMode` call `modesChanged` again. The Self-Portrait Prompt is `NotebookOptions.selfPortrait` (off by default;
`SELF` in `BookOptions` and `TSRQ` in the recovery, each written only when on), built as Nib Preview is
(`LibraryCard.Part.SELF_PORTRAIT`, `NotebookScreens` chips). `DailyPrompt` keeps its name and file; its `TITLE` and
heading say Self-Portrait Prompt, `forDay` went.
- **Upstream files touched:** `PaintActivity` (+31 −32: the main menu's Play row and Play's Shapes and Daily Prompt rows
  out; `addPage(BookEdit)` deals a new page a card while the notebook's option is on, `addPage` and `newPage` through
  it; `dailyPrompt` without `today`). `RecoveryCodec` (+6 −3: `TSRQ`). Generic: none.
- **Checks:** `DrawLayoutChecks` (the row's order and cells, Play's and Shapes' marks, the menus' rows, the column's
  sums on the four screens), `ToolSwatchesChecks` and `GreyBarChecks` (turned screens under 5 % a dp and 2.5 px a grey:
  the box grows only into spare room), `NotebookOptionsChecks` (its record alone, file and recovery, absent reads off),
  `LibraryCardChecks` (twelve rows, set in its row), `DailyPromptChecks` (headings, where cards are dealt);
  `NomadUiChecks` (device) for the row's place.
