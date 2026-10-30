# Oil paint: Paint Load, Rub to Load and the Filbert (2026-10-19, as built)

MonoPaint's oil paint mode (upstream `fe6786d`, "Add oil paint brushes that run out of paint and smudge"; TOOLS.md
"Oil paint mode when opaque") ported into Inkwash's six-part engine as Smudge dials, a Tip choice and a Basic tool, not
as MonoPaint's tool settings. Branch `inkwash-oil`. The owner said yes to the whole plan; on loading: "start with a
small load".

## What the owner sees

- **Oil paint** (Basic tool, Oils, "runs out"): a Filbert of opaque oil paint on canvas whose Drag has Paint Load on.
  Every stroke starts with a small load (its Starting Load): it lays its grey solidly, runs out along the stroke
  (sooner pressed wider), laying less of its own paint and more of what it drags from the page, and once empty it only
  smudges, so over clean paper it leaves nothing. "This brush": "Oil paint, filbert, on canvas. Runs out of paint,
  then only smudges. Rub a grey to load. Pressure widens." Caption "Oils, filbert, runs out"; a new brush that runs out
  is named for it, "oil-plum"; a Filbert, "filbert-fig".
- **Rub to load:** rubbing the pen back and forth on one of the side bar's grey faces picks that grey and loads the
  brush in use with it, more the farther the pen rubs (twice as far as the pen travelled over it, in page px of
  full-width stroke), up to a full load. A plain tap still just picks the grey; only the pen moving over the face past
  6 dp is a rub, and a rub calls off the long press. Only a brush with Paint Load reacts; with any other a rub is a
  tap. Brush Lab's grey faces load its test page's brush the same way.
- **The gauge:** a slot whose brush has Paint Load shows a small upright gauge at the right end of its tray row, a
  1 dp ink outline 6 dp wide inked from the bottom by the share of a full load its next stroke starts with: it fills
  when a grey is rubbed and drains as strokes spend it, down to the Starting Load, which every stroke has. Drawn again
  after each stroke and each rub, over that row only.
- What a rub loaded lasts until the strokes spend it: what a stroke leaves on the brush starts its next one (per tool,
  the finger's apart), and once less than the Starting Load is left the next stroke starts at the Starting Load again.
  Kept while the app runs, not saved, as Keeps Paint's paint is. Undo doesn't give paint back.
- **Brush Lab:** Drag and Smear have two new last dials, **Paint Load** (off to 1: how far a full brush lays) and
  **Starting Load** (the share every stroke starts with, default .25); the Smudge module's line reads "lift · drag ·
  smear · load". The Tip has a tenth choice, **Filbert** (Nib Width, Nib Angle and a chisel's other dials): a rounded
  oval whose broad side lies across the pen's lean, so it follows tilt (its Nib Angle without a reading). With Paint
  Load on, each dab is a crescent hollowed on the side it moves toward: a first touch is a thin sliver, a stroke's
  start is rounded and its end hollowed.

## Engine

- `PartDial.PLOAD` (key `pload`) and `PMIN` (key `pmin`, default .25), last of Drag's and Smear's dials after Keeps
  Paint, in `ModularCode.ADDED` (written only when on or moved), kept when Drag and Smear swap (`BrushPart.keeps`), set
  to their defaults by a part's roll with no random draw, never nudged by a variation. Every older code reads Paint Load
  off and writes byte for byte.
- `PartChoice.FILBERT` (key `filbert`), Tip index 9, a chisel's dials (Nib Width .3 to .55, default .5; Soft Edge .15);
  `ModularFeatures.seedOnly` (no style weighs it, a variation never swaps to it; Brush Lab offers it).
- **Rolls:** `ModularStyle.rollFilbert` and `rollPaintLoad`, after every other part, from the seed with salts
  `0xf11be` and `0x910ad` (`oilRolls` turns both off, and the roll-freezing lists in `ErasableChecks` and
  `CarrySmudgeChecks` freeze them): an Oils round or flat nib becomes a Filbert one roll in three (Anything goes, which
  reaches every choice, one in 30), .35 to .55 wide; an Oils Drag or Smear gets Paint Load one roll in three, .35 to .75.
  Every other roll comes out exactly as before.
- **`LoadedPaint`** (new, pure): a full load lays `3000 × Paint Load²` page px of full-width stroke for a 60 px brush,
  scaled by the root of the brush's width (half to twice); a stroke starts with `max(Starting Load, held)` of it;
  `Run` spends each sample's length times its dab's width share (`BodyStamper.diameter` at that pressure, or the line's
  width, 1 at a full press: a wider stroke spends faster) and writes the paint left, `1 − smoothstep(spent / length)`
  (MonoPaint's `PaintLoad.travel`), into the samples' `StrokePoints.own`. `rubbed`: what a rub loads (from at least the
  Starting Load, `RUB` = 2 page px a screen px rubbed, at most full); `rub` (6 dp): a rub, not a tap; `rub(held, …)` and
  `shown(held, …)`: the bar's and the lab's.
- Every stamper multiplies its per-dab darkness by the paint left there (`StrokePoints.own(j, k, t)`): `BodyStamper`
  (and a dab with none left isn't laid), `LineStamper.nib`, `Placement`, `HakeRibbon` (each head's), `BristleSwarm`,
  `StippleStamper`, `HatchStamper`. Without Paint Load `own` is null: one null test a dab, no per-sample work, and
  every golden, bench hash and CRC is unchanged. The smudge (`Carry`) runs as it always did: its plane is the page as
  found, so as the brush's own paint thins, what it drags shows through, and once empty only the drag is left.
- `ResolvedBrush.loaded`: a Drag's or Smear's `LoadedPaint`, not on water and only for a brush that lays a line or a
  body; `Pen.filbert` (the nib's broad side across the lean, `LineStamper.nib`); `Body.filbert` (`BodyStamper.shape`:
  an oval at Nib Width, its broad side across the lean, tilt's lean stretching it) and `Body.crescent` (Paint Load):
  each dab less a bite, an oval .85 as long, as thick, set half the oval's reach toward the way it moves
  (`BodyStamper.bite`, MonoPaint's `BrushStamp.crescent` generalised to any heading). A Filbert lays its dabs closer
  (`filbertSpacing`: its ends' sharper bend; a crescent's a fifth of its short half-axis, so the sweep has no ridges).
- `ModularStroke`: the stroke's `LoadedPaint.Run`, started from the holder's held load (`HeldPaint.load`), spent in
  `add` before any stamper reads the sample; at `finish` what is left, if more than the Starting Load, is kept for the
  holder (`HeldPaint.keepLoad`). `QuickStroke`'s preview spends a twin of it on its own samples and, where less than
  half is left, shows the brush as a smudge is (the light wash's dither), so the preview runs out where the stroke does.
- `HeldPaint` gains each holder's load beside its paint. `ModularWords`: `runsOut`, the Filbert's noun, the smudge
  sentence and tag, the Smudge module's "load"; `BrushNames.traitKeys` `carry.pload` (traits.txt: `oil`, `filbert`).
- Desktop speed (`OilChecks`): Oil paint per sample about the same as the Filbert without Paint Load (60 px .116 ms,
  120 px .23 ms).

## UI

`SwatchFace.rubs` (a grey's rub: its travel, the tap and long press called off past 6 dp), `SwatchFaces.Host.rubbed`
(picked first, then loaded; one that didn't load is a tap, `greyPicked`), `InkWash.rub` and `paintLoad`,
`SlotTile.gauge` and `Face`'s load, `InkWashBar.loadChanged` (after each stroke, `heldChanged`, and each rub, through
`SelectionFeedback`), `BrushLabActivity`'s faces. `DrawingPad` already calls `heldChanged` after each stroke.

## Web twins

`parts.js` (the dials, `R.loaded`, the Filbert's resolve, words, captions, traits), `engine.js` (the Filbert's nib and
oval, its crescent and spacing; the paint left in the line, body, hake, placed lines and bristles), `stroke.js`
(`oilSpend`), `newmods.js` (the Filbert choice, the paint left in Stipple and Hatch, `rollOil` with the app's salts and
odds, `oilChecks`; the new-module roll check freezes it), the lab page patched with the same hunks, Brush Studio rebuilt
(check.cjs 115 of 115; java-codes.sh 606 of 606 the same, `codes.cjs` writing Oil paint's code and a Smear with Paint
Load). Each web stroke starts at its Starting Load: rubbing and the gauge have no web version. The web has no "what's
inside" line, so the Smudge module's "load" is the app's only; Brush Scan (`match-lab.html`) carries only the traits
list. The web stampers aren't the app's bit for bit, so the twins match in behaviour, not pixels.

## Doubts

- "Rub a grey to load." is an instruction in "This brush", which TEXT-STYLE otherwise keeps to what a brush is; the
  owner asked for it plainly.
- Paint Load's label is Bristle's Paint Load's too (one concept: how far a brush's paint lasts), on another part.
- PHILOSOPHY #1 (no hidden state): the load carries between strokes; the gauge shows it.
- Samples (tiles, tray rows, Brush Lab's loop) are drawn at the Starting Load: the loop shows its solid start fading
  out by about half way, the rest empty paper. Honest, and it reads as "runs out", but shows less of the brush.
- Mirror copies of a stroke each start from the holder's load and each leave theirs; the last to finish wins.
- A perfectly straight synthetic drag shows a thin line at its centre row (any Drag, before this branch too); hand
  strokes don't.
