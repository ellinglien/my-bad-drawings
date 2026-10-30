# Modular Brush Lab: notes

Page: `modular-lab.html` (one file; Google Fonts is the only external resource). Built by `node build.js` from `src/`:
- `textures.js`, `icons.js`, `port.js` (the web port of the app's engine) and `oldmodel.js` (the old model and I/P codes): copied unchanged.
- `engine.js`: the stampers, copied from the prototypes, with the Hand's inputs added.
- `stroke.js`: the blend settle, Water, compose, Carry and the stroke driver.
- `parts.js`: the part model, styles, codes and words.
- `checks.js` and `ui.js`.

Tools:
- `check.cjs`: the click-through, 54 assertions (Float's taps, drag, tilt and Print; Erasable's pencil, ink and Eraser among them).
- `overflow.cjs`: 168 layout states.
- `sweep.cjs`: the part combinations in Node (560 with Float).
- `shots.cjs`: the screenshots in `shots/`.
- `bench.cjs`: the in-browser timings, saved in `bench.txt`.
- `gallery.cjs`: a tip × medium contact sheet.

**RUN CHECKS** on the page (or `#selftest`) runs the self-test. No other folder was edited, and no repo was touched.

- **Stamp** (2026-10-19, `newmods.js`, mischief-lab-2.html's second pad): Tip choice 10, `weight spacing headTurn
  inkFade flick` (Turns is Head Follows' key, On first). A nib cut from the test area by lasso (**Cut Stamp**, beside
  Eraser, shown for a Stamp: marching ants while drawing; an empty lasso says "Nothing to cut") printed every Spacing,
  potato-stamp inked, Ink Fade's share used a print, afresh after a 1 s rest (the surface keeps what the last stamp left).
  Nibs are kept by id (`NM.nibs`, and localStorage); the code carries the id after the six (`parts.js` `nibSeg`), as the
  app's does. Without its nib it prints a default star (the app's prints round). Never rolled, never a variation's swap.
  `stampChecks` in RUN CHECKS.
- **Crayon** (2026-10-20, `newmods.js`, mischief-lab-2.html's fourth pad): Medium choice 7, `wax scratch` (Scratch a
  choice, Off and On). Drawn as a crisp charcoal line (Smudge off, Float as Quick) with its own line stamp (rim wobble,
  ragged edge and flecks from page-fixed noise, `crayonMaps`) and compose rule `crayon` (tooth, build-up over what's
  there). Its wax goes into Resist's planes, all kept: washes slide off, the eraser doesn't lift it, a later crayon
  takes on it (only masking fluid shields it). Scratch On: a blade .3 as wide, laying white. Medium size 30 px in every
  style; never rolled, never a variation's swap. `crayonChecks` in RUN CHECKS.
- **Spiro** (2026-10-20, `newmods.js`, mischief-lab-2.html's third pad): Tip choice 11, `weight mode curve loops teeth
  wheel hole petals ratio phase fades flick`, the app's `SpiroStamper` line for line: Line rides the stroke, Ring pins
  the ring at the first point and turns the wheel by the pen's eased angle round it (only forward past a tremble; at
  the lift it catches up, a tap draws the whole figure). The hand gains the app's Tilt Range and page tilt (hidden, as
  this lab doesn't draw them) so the Spiro's drives after them line up: Pressure Sets, Speed Sets, Tilt Sets, shown
  for a Spiro (`dialShown`, and the Control card's rows). The Stroke's `restamp` (Build-up's ticks, the Spiro's lift)
  is the app's `stampNew`. Never rolled, never a variation's swap. `spiroChecks` in RUN CHECKS. In Ring the test area
  shows the pinned ring in light grey from the touch-down to the lift (`spiroGuide`, `ui.js` `drawGuide`, on the canvas
  over the page, drawn again after each change it shows); Brush Studio's test page doesn't. 2026-10-23: the pen's first
  move out pulls the ring to its size (`spiroPull`, the app's `SpiroStamper.Pull`, nothing inked meanwhile) and the
  guide, dashed now, follows it (`clearGuide`).
- **Grey Shift** (2026-10-20, `newmods.js`, mischief-lab-2.html's fifth pad): the hand's last dials, after a Spiro's
  drives, `shift sorder sgreys sbar sany sany2`, the app's `SwatchPicker` line for line: each touch on the test page
  picks the shade from a set, Random, From a Set (a shuffled deck) or Pattern (In Order, Ping-Pong); Greys Toolbar (the
  test area's shade swatches, the one picked marked; the app's four grey swatches in Brush Studio) or Any (the panel's
  16). Each palette's set is the greys left out, a bit each (hidden dials), shown as a swatch row in the Control card
  (`shiftRows`, `greySetHtml`, ui.js's `greyset`). Written only when on; never rolled. `shiftChecks` in RUN CHECKS.
- **Stay in Lines** (2026-10-20, `newmods.js`, mischief-lab-2.html's first pad): Smudge choice 5, `flood`. The
  surface keeps a walls plane, what strokes of an ink-layer medium (ink, charcoal, graphite, resist, screentone, crayon)
  darkened, the eraser taking it back, a clear page clearing it; the shape is the prototype's walls1 and room1 on it
  (open paper reaching the edge is none: a plain wash). The flood is its bucket queue by pen time, from the brush's rim,
  painted into the stroke's body as the app's `StayIn` does; the stroke's buffers kept to the shape at every compose and
  in a Slow lift's settle. Never rolled, never a variation's swap. `stayChecks` in RUN CHECKS.
- **Filled Shape** (2026-10-23, `newmods.js`; the app's `FilledShape`): Smudge choice 6, `shape`, no dials. Words ("Fills
  the shape it outlines.", "Shape", "filled shape"), codes and checks; never rolled or swapped to. The app closes the
  outline at the lift and fills the area with the same brush through its `FillTool`, which has no twin here, so the
  lab's and Studio's pictures draw the outline alone.
- **Gradient** (2026-10-20, `newmods.js`; the app's `GreyGradient`): the hand's last three dials, after Grey Shift's,
  `grad gto glen`: Fade to paper, To Grey to an End Grey (the panel's 16), Cycle through Grey Shift's set (its Greys:
  the test area's shade swatches through `HOST.swatches`, or Any) from the stroke's grey on, over a Length in cm at
  300 dpi. The `Stroke` subclass lays the stroke at the darkest grey it reaches and writes each point's share into its
  `own` from `oilSpend` (times Paint Load's), which every stamper already reads. Ink, Wash, Charcoal, Graphite and
  Crayon; not Opaque, Resist, Screentone or Float. The Control card's rows (`gradRows`, ui.js), `dialShown` for Brush
  Studio, words after Grey Shift's. Written only when on; never rolled. `gradChecks` in RUN CHECKS.

## Part model
A brush is six parts. Each part holds one choice and that choice's dials. Off and None are choices, so every combination draws (the sweep checks all 420 Tip × Medium × Carry × Life combinations).

- **Tip** (shape)
  - Round: weight, taper in/out, dry brush, wobble, soft edge.
  - Chisel and Flat nib: the same plus nib width and angle.
  - Hake: weight, head follows/fixed, angle, streaks, soft. It is the swept ribbon, which pivots on tight turns.
  - Split nib: tine gap, nib width, angle, weight, tapers, dry.
  - Multi-line: 2–5 lines, reach, weight, tapers, dry, wobble.
  - Bristles: 6–32 hairs, splay, cohesion, dryness (.15), weight.
  - Breathe (tines and lines converging with pressure) is the Hand's pressure→width cell.
- **Medium**
  - Ink: Wet (blends the line into a wash), line kept, pooling, splatter, bleed, bloom, line placement (centre/outline/squiggle), Offset, Chaos.
  - Wash: wetness (flat below .5), hollow edge, bleed, bloom, backruns, pen line.
  - Cover: cover, impasto (lit from the upper left, shaded live in Fast-wet). Gouache and Oils are fully opaque.
  - Charcoal: Spread (Charcoal rolls 70% .15–.45, 15% crisp, 15% messy) and Hold (vine↔compressed). It uses MEDIA-RESEARCH's contact rule with its own stick grain, and is capped at 240 px.
  - Graphite: Grade 6H–8B, smudge, with a per-page Fill plane (the tooth fills, so H blocks B).
- **Carry**: Off; Lift, Drag, Smudge (pick up, lay down, own paint). A dab-sized buffer that travels with the tool, as in MEDIA-RESEARCH §4.
  - Reworked 2026-10-16 for the app's Smudge (spec `docs/archive/superpowers/specs/2026-10-16-smudge-design.md`, F1–F4 as tuned): each reservoir cell trades paint with the page (the brush takes Pick up of the difference where the page is darker and gives Lay down of it where the brush is, each over a radius of travel), so a line it crosses is carried along and fades as it is laid down; more Pick up gives darker, longer streaks, more Lay down a stronger, shorter tail. Dabs are 1 + .08 r apart (r / 4 under 15 px), so streaks don't comb. Lift takes twice as readily and keeps 1 − Lay down of what it takes, so the track lightens; opaque Lift doesn't pass over the page but tints its paint toward darker paint it covers (by twice Pick up). Own paint scales opaque cover too. The reservoir reaches 180 px (at most 63 × 63 cells). Codes are unchanged; codes with Lift, Drag or Smudge draw differently from before.
  - Smear's **Soften** (2026-10-18, spec `docs/archive/superpowers/specs/2026-10-18-stump-design.md`): last of Smear's dials, written only when on (`ADDED`), never rolled. It works the smear as MonoPaint's blending stump (`stroke.js` `stumpDab`: each pixel toward the page around it a quarter radius behind, Pick up the strength), the reservoir trading `1 − soften`; a Rake or Split nib combs along its tines (`parts.js` `tinesOf`, shared with Float). "Softens." / "Combs."; captions "stump" / "comb". The app draws the same (`blend-stump` and `wash-comb` goldens). `modular-lab.html` patched with the same hunks; the lab's Resist doesn't shield the stump pass.
- **Paper**: plain, cold press, laid, felt, crumpled, canvas, plus Tooth. Tooth is the tooth for lines and the granulation for paint.
- **Life**
  - None: final under the nib.
  - Fast-wet (default): window 1–10 mm. Only the last few mm develop while the pen is down; nothing at lift.
  - Drying: drying time 0.75–20 s, Settle, Takes ink (off/drift/vein, tentative), Plume strength. The settle runs at lift, then the wash stays wet: pigment settles, and a pen line drawn into a wash that takes ink plumes.
- **Life · Float** (2026-10-16, `float.js`; design: `docs/archive/superpowers/specs/2026-10-16-float-design.md`): suminagashi.
  - Floating time (Drying's dial), Spread (drop size), Drift (on a tilted page). Code: Life choice `3`.
  - A tap or a held pen drops a disc that pushes floating paint out into rings (Jaffer and Lu); a drag lays the stroke's
    paint as flat-grey films (traced at half coverage) that push older paint aside; Carry Drag or Smudge drags what floats
    (marble-and-bristle's stylus, slip from MonoPaint's brush-pen pull: Pick up × pressure curve); Own paint 0 is
    **Clear water**. Lay down does nothing on water (still shown here).
  - The test area: **Print** (the droplet) prints now; **Level / Tilt** steps the page tilt for Drift; it prints by
    itself after its floating time; any brush that doesn't float prints it first. Two library brushes, **Sumi** and
    **Clear water**, are added once (also to a saved library). Samples: a Float brush's Life tile, and any Clear water
    tile, show taps of it and clear water in turn.
  - Rolled rarely, from the seed after every other part (Anything goes 1/30, Ink wash 1/25, Watercolor 1/40), so the
    seeded starting library is unchanged.
  - `floatChecks` in RUN CHECKS: area kept, crisp rings, clear water lays nothing, log replay, Undo while floating,
    drift only when tilted, print on time, codes. Build with `float.js` after `stroke.js` (`build.js`'s core list).
  - Built in the app (2026-10-16, `inkwash-float`): "This brush" and Float's card words were shortened to the app's
    budgets ("It floats about 8 s: taps drop rings…"; "Float · 16 s" from 10 s), here too.
- **Medium · Erasable** (2026-10-16; design: `docs/archive/superpowers/specs/2026-10-16-erasable-design.md`): the last dial of
  every Medium choice, off (permanent) to 1 (erases clean). A code without it reads 1, as every brush erased before;
  it is written only when not 1 (`ADDED`, with the app's charcoal Dust, a hidden dial here so codes line up), never
  rolled and never nudged by a variation. A brush made new starts at its medium's real value (`REAL_ERASE`: ink off,
  wash .4, opaque .3, Oils off, charcoal .9, graphite 1).
  - Weighted rolls (2026-10-18, the owner's "less likely to be unerasable"): a style's roll weighs it from the seed
    (`rolledErase`, its own salt, no draw from Random, so every other roll is as before): ink and Oils' paint stay
    permanent one roll in six, else erase clean; a medium that erases in part keeps its value. A locked Erasable or
    Medium is kept. Brush Studio's Random tab weighs it the same way. The self-test: 21 of 129 ink and oil rolls
    permanent, the 300 rolls with Erasable at 1 hash as before.
  - The page keeps the tone the eraser leaves (`surf.kept`, made by the first stroke that isn't wholly erasable): the
    compose lays the stroke over it too by `1 − Erasable`; Water's settle and plumes and Float's print by their brush's.
    Smudge moves only the erasable part.
  - The test area's **Eraser** (under Clear; ink-filled while on, put down by tapping a slot): each pixel once a
    stroke goes back to its kept tone; its width follows pressure up to 24, 120 or 220 px by the test size; however
    hard it is pressed it never takes permanent paint (the owner's answer, 2026-10-16: the hard rub is gone, as in the
    app). Three library brushes are added once at the front: Sketch pencil, Steady line, Soft wash. Sumi is permanent.
  - `eraseChecks` in RUN CHECKS (the sketch inked and erased is the ink alone, a wash keeps 60%, a finger leaves ink,
    a hard press leaves ink, codes, rolls as before). With every brush at 1 the 560 combinations draw the same pages as
    before. Built in the app the same day (`inkwash-erasable`): the same codes, the same Sketch pencil.
- **Hand**
  - A 3×3 grid: pressure, tilt and speed against size, darkness and width/angle, each cell off/some/full. Plus a Response gain and whether fast means thinner or thicker.
  - The presets are the swap choices. Editing a cell makes it Custom.
- **Wet smudges** (2026-10-19, spec `docs/archive/superpowers/specs/2026-10-19-wet-smudge-design.md`): Drag's and Smear's **Keeps Paint** (`carry.keep`, last, in `ADDED`, never rolled: the surface's `heldPaint` starts the next stroke's reservoir, `stroke.js` `carryDab`) and Smudge's fifth choice **Clear Water** (`water`: lifts as Lift, then `runOut` at lift lets Lay down of what it lifted spread through its wet, a Slow one's share handed to the host's water as a wash's). `parts.js` (tables, codes, words), `stroke.js`, `newmods.js` (`rollWater`: from the seed, salt `0xc1ea4`, Anything goes 1 in 40, Watercolor and Ink wash 1 in 50, never Float; `wetChecks`). `modular-lab.html` patched with the same hunks; codes byte for byte the app's (`java-codes.sh`).
- **Flick and Fling** (2026-10-19, the mischief modules spec's Stage 2; the app's `FlickFling`): every tip's last dial `tip.flick` and every Blend medium's last `medium.fling`, in `ADDED` (Bristle's hidden `tip.load`, the app's Paint Load, before Flick so the codes line up). `newmods.js`: the `Stroke` subclass watches every sample for a hard stop (drops into `L`, else the body, as the clock reaches them, again after a Slow lift's re-lay) and at `end` feeds a moving lift's continuation as samples; `engine.js`'s `stampLine`, `stampBody` and `stampPlaced` narrow and dry what lies past `pts.flick`. Rolls from the seed (salts `0xf11c4`, `0xf1196`, the app's odds), words, `flickChecks`. Also: Smudge Off no longer says "undefined" in "This brush" (Keeps Paint's sentence). `modular-lab.html` patched with the same hunks.
- **Oil paint** (2026-10-19, spec `docs/archive/superpowers/specs/2026-10-19-oil-paint-design.md`; MonoPaint's oil paint mode, the app's `LoadedPaint`): Drag's and Smear's last dials **Paint Load** (`carry.pload`) and **Starting Load** (`carry.pmin`, .25), in `ADDED` and Smudge's `KEEPS`; `parts.js` `resolve` gives `R.loaded` (a full load 3000 px × Paint Load², by the root of the width), `stroke.js` `oilSpend` spends a dab's width a sample from the Starting Load (no rub here) and every stamper scales its darkness by what is left (`p.own`). The **Filbert**, Tip choice 9 (`newmods.js`): an oval following tilt, a crescent with Paint Load (`engine.js` `stampBody`, `filbertSpacing`). Rolls from the seed (salts `0xf11be`, `0x910ad`; Oils 1 in 3 each, Anything goes a Filbert 1 in 30), words ("Runs out of paint, then only smudges. Rub a grey to load.", "runs out"), `oilChecks`. `modular-lab.html` patched with the same hunks.
- **Fading and Lemon Juice** (2026-10-19, the mischief modules spec's Stage 3; the app's `FadingInk`): Drying choices 5 `fade` (`life.fadeIn`, 10–120 s, 20) and 6 `lemon` (`life.dryIn`, 1–10 s, 4), resolved as Quick with `R.fade`. The test area (`HOST`) keeps each mark (its rectangle as the stroke found it and as laid, and when) and a timer steps it (`fadeTicker`: lemon juice once a second, fading a tenth of its time, 2–5 s; never while the pen is down; it stops when none is left), the app's shares (`fadeShare`); a sample tile and Brush Studio's pages show the stroke as laid. Rolls `rollFade` after the oil's (salts `0xfade5`, `0x1e307`), words, `fadeChecks`. `modular-lab.html` patched with the same hunks.
- **New modules** (2026-10-16, `newmods.js`, built after `checks.js`; design: `docs/archive/superpowers/specs/2026-10-16-new-modules-design.md`).
  All appended, so every old code reads, draws and writes as before (the 560 old combinations hash for hash). The file
  extends parts.js's tables and wraps `resolve`, `composeStroke`, `Stroke` (a subclass: the stampers, the smudge's
  shield, Halo at lift), `Eraser` (a subclass: the resist lift), `Water.mul` / `addWet`, `Bath.print`, `showPage`,
  `blankPage`, the rolls, variations and words; ui.js gains `optLabel` in `dialRow` and the dial's status, and the sweep
  counts Resist by its plane; parts.js's `lifeNote` says Halo.
  - **Blend**, a Medium dial (choice; `ADDED`, written only when not 0): darken (Opaque: cover), white (key lighten),
    behind, invert. White and Invert compose the mark over blank paper and take its darkness as their strength; Behind
    scales the mark by how blank the page was (`blankOf`). A Nomad-narrow choice dial becomes a menu (`fitSegs`).
  - **Stipple** and **Hatch**, Tip choices 7 and 8: their own stampers into L or W (`stampStipple`, `stampHatch`).
  - **Resist**, Medium choice 5 (dial: Erasable; wax ↔ fluid): planes `surf.resist` and `resistKept`; every later
    write takes `1 − resist` of its change; MonoPaint's hatch on screen over what can still lift; wax lays a one-step
    sheen and catches only the tooth's high points; the eraser lifts fluid first.
  - **Halo**, Drying choice 4: Slow's lift, then Living Ink's Mould from the stroke's edge (`Halo`, ten ticks a second
    beside other halos in the test area, to the end at once elsewhere); any other stroke sets it.
  - Presets added once at the front: White gel pen, Wash behind, Stipple sponge, Stipple pen, Hatch pen, Halo ink,
    Masking fluid, Wax resist. Rolls are rare and from the seed (`NM_ODDS`); the seeded library is unchanged.
  - `newModChecks` in RUN CHECKS (14). The sweep is 1,080 combinations. Click-through 65 of 65 (11 new), 392 layout
    states clean (now with clipped-text checks and the new presets).

Simple shows one macro per card: Tip thin↔broad, Medium dry↔wet (crisp↔messy, hard↔soft, thin↔opaque), Paper smooth↔rough, Life still↔lively, Carry clean↔smeary, Hand steady↔responsive.

## Share codes
- **New format:** `name.M<style><tip>_<medium>_<carry>_<paper>_<life>_<hand>.<seed>`.
  - Each part is its choice as one base-36 digit, then that choice's dials in order: a choice dial is one digit, a dial is two base-36 digits over its range.
  - New dials go on the end; a code that stops early reads them as defaults. Never reorder.
- **Old codes still load:**
  - App `name.I…` / `name.P…` codes load through the old decoder. They become Ink at Wet 0 or Wash at Wet 1, with Life Drying, as the app draws them.
  - The self-test proves it: five app codes, from `BrushCodeChecks`, are drawn by the unchanged port and by this engine on the same stroke and paper. 0 pixels differ at 16 greys.
  - Also accepted:
    - the brush-experiments I/P tails (spread/tilt/fast, cover/impasto/head/tilt/fast, Hake style 7, life/window);
    - the bristle tail;
    - living-ink tails, read as still;
    - lab v2's `.x` pen suffix;
    - pen-paint-blend `B` codes with both tail versions;
    - ink-and-water's plume tail.

## Porting to Java (inkwash-v1)
- **`WildBrush`**: becomes six part records, each with a choice enum and a `float[]` of dials. Add `Parts.resolve()` (mirrors `resolve` in parts.js), which derives today's pen and paint fields, so the existing strokes keep working.
- **`BrushSetting`**: one list per choice (the `CHOICES` tables). Appended only.
- **`BrushCode`**:
  - Add the M encode and decode.
  - Keep I/P decoding exactly. `BrushCodeChecks` gains the identity check above, plus M round trips.
- **`BrushStyle`**: the 14 styles as part weights and ranges, including weighted bands for Charcoal's spread. Hake becomes a style rather than paint index 7.
- **`BrushVariations`**: the `makeVars` rule (nudges, and at most one part swap, labelled). Part and dial locks.
- **`BrushCaptions`** / new **`BrushDescription`**: `captionLines` and `plainWords`.
- **`WildInkStroke`**:
  - The hand factors (pressure/speed/tilt darkness, pressure→nib width, speed→angle).
  - Charcoal fuzz from a `StickGrain` tile.
  - Grid-spaced dabs over 16 px.
  - The Fast-wet tail window, limited to the taper length and re-tapered at most every 50 ms.
- **Placements**: a new `Placement` helper used by `WildInkStroke` (split, multi, outline, squiggle, offset, chaos).
- **Bristles**: a new `BristleSwarm` (scaled units above a 10 px radius, 32 hairs maximum).
- **`WashStroke`**: the nib ellipse and tapers, cover, the Fast-wet distance map and window settle. A new `FlatRibbon` for the Hake.
- **`WatercolorSettle`**: settle into a darkness buffer, cover fading the wet effects, relief.
- **New `PenWashStroke`**: line plus body (the blend), with `settleBlend` only in Drying.
- **`PaintLoad`, `ToneDabs.soften`** → a new **`Carry`** (buffer per dab, pick/lay/load).
- **`PaperGrain`**: unchanged. A new `FillPlane` (bytes in 64 px tiles) for graphite.
- **`WatercolorSession`**: owns a `WetLife` (from `Water`) for Drying: wet-end per pixel, settle patches, plume parcels, ticking at 10 Hz on a background thread.
- **`ToneDocument`**: an opaque write that sets alpha (cover).
- **Lab UI** (`BrushLabActivity`, `LabSettings`, `LabLibrary`, `LabUi`, `InkWashBar` size slots and `ShadePicker`):
  - the part column (a new `PartCard`, plus `PartRow` for collapsed rows);
  - the swap strip (from `SettingGuide`'s tile code);
  - the Hand grid (`HandGrid`).
  - The metrics follow `metrics()` in ui.js.

## Cost (headless Chrome on this Mac, `bench.txt`)
- Fast-wet costs about 10–150 µs a sample up to 16 px for every style, and stays under about 1.4 ms mean at 360 px. Rare samples spike to 8 ms (Brush pen at 360 px with a long taper).
- Lift costs 0.2–5 ms.
- Expect 3–5× these on the Manta.

## Open questions
- Takes ink is tentative. It works only with Drying; Fast-wet's window can't host plumes. Is that the right home, or a Medium dial?
- Drying's span is now 0.75–20 s, from ink-and-water. Old paint codes keep their dial value, so they dry up to 4× longer than in the app. Keep it, or use 5 s when Settle and Takes ink are off?
- Should Simple's Randomize keep the Hand (how Elling holds the pen), as lab v2 did? Here it rolls with the style.
- Smudge's own paint defaults to .3. A pure finger (Own paint 0) draws nothing on blank paper; the app has one ready-made, Stump under Charcoal.

## Known gaps
- In Fast-wet, a wet ink line's loosening and feathers (the blend mixing) happen only in Drying. Fast-wet has the loose share and the halo.
- A B code with a flat-mode wash at Wet < 1 reads as a wet body.
- The corner sample and the card samples don't run the Water, so settling shows only in Life tiles and on the test page.
- The eraser clears graphite's Fill plane only where it leaves paper.
- Phone: the Manta frame at 390 px is 34%. Narrow screens default to the Nomad (45%).
- The engine draws to one tone page, not the app's ink and wash layers. Cover over lower layers needs the alpha write above.

## Words (2026-10-16)
The words (`parts.js`: `handWords`, `choiceWords`, `captionItems`/`captionLines`, `plainWords`, `ui.js`'s `lifeNote`)
and the display labels are the app's, in `docs/TEXT-STYLE.md`'s house style (the app's `ModularWords`, `PartChoice`,
`PartDial`): the parts read Tip, Medium, Smudge, Paper, Drying, Control; no arrows; keys and codes unchanged.
`modular-lab.html` was patched with the two files (each old text replaced where it sits verbatim in the page);
`check.cjs` 47 of 47, the self-test ok. The blurbs and the page's own notes are the prototype's still.

The page's header and How it works, and `ui.js`'s in-lab notes and statuses (swap-strip hints, the Control grid's
hint, "No dials" notes, slot, swap, Print and tilt statuses), were then shortened in the same style; no arrows, and
"Undo takes it back" is gone from the statuses. Patched the same way; `check.cjs` 47 of 47, the self-test ok,
`overflow.cjs` 168 states with no problems.

## Names (2026-10-18)
A new brush's first word says what stands out about it, the second stays a fruit or vegetable ("drip-plum"), as the
app's `BrushNames.described`. `parts.js` carries the app's `names/traits.txt` as `NAMES_TRAITS` (one string, kept the
same by `BrushNamesChecks`); `describedName` runs last in `rollStyle` and again after `newmods.js`'s rolls, `kinOf` keeps
a trait's word under a produce from the variation's seed. Both decide from the brush and its seed (their own salts),
drawing nothing from `Math.random`, so every roll is as before; `TRAIT_NAMES.on` false keeps the adjective (the Erasable
hash check turns it off). `nameChecks` in RUN CHECKS: 38 brushes, ties and variations pinned as the app's, 300 rolls,
19 app codes named as the app names them. The adjective and produce still come from `Math.random`, so a name is the
app's only in its first word. `modular-lab.html` patched with the four files.

## Washes reach black (2026-10-19, the app's branch `inkwash-washblack`)
- `parts.js`: `GLAZE` .34, `WASH_BLACK` .97, `KNEE` .55, `laid`, `strength`, `washStrength`. A wash's `washK` is
  `washStrength(shade)` (its full body lays .97 of the shade); ink's and cover's body `GLAZE × shade` as before;
  `previewShade` for Drying's light preview (`stroke.js` `previewWash`). Float's and Halo's greys through `laid`.
- `stroke.js`: compose `laid(dd × washK)` in place of `min(1, …)`; the held-back body `heldDensity`; the water settles a
  cell to its own darkness past .95 (`Dk`). Every body before stays under the knee: old pages draw as before.
- `checks.js`: an old paint's legacy diff drawn at `GLAZE / WASH_BLACK` (the app's `ModularSizes.sinceBlack`).
- `modular-lab.html` patched with the same hunks; codes unchanged. The app's own: the slots' greys, gravity near black.
