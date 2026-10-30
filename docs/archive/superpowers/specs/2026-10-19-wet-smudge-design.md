# Wet smudges: Dirty brush and Water brush (2026-10-19, as built)

The owner said yes to both of MonoPaint's wet brush pen behaviours (upstream `8743286`, "Hold ink between strokes"),
ported into Inkwash's six-part engine as two smudge brushes in the smudge tool's chooser, each with its own icon.
Branch `inkwash-wetsmudge`.

## What the owner sees

- **Dirty brush** (Basic tool, Watercolor, "keeps paint"): a Smear with no paint of its own that keeps what it picks up
  for its next stroke, so the next smear starts with that paint. While it holds paint the smudge tile's icon (a brush
  with a dab of paint) has its head filled grey, and the smudge chooser shows **Clean Brush** under its cards: a tap
  empties it and closes the chooser. Working it out on clean paper cleans it too.
- **Water brush** (Basic tool, Watercolor, "re-wets paint"): Smudge's new choice **Clear Water**. It lays only water: it
  lifts what it touches (paler where it works), the lifted paint runs out softly through the wet it leaves, gathering a
  little at the wet's edge, and with Slow drying the wet area is a wash again: it keeps settling for its drying time and
  runs and pools downhill on a tilted page. Over a wash still wet it lifts more and runs further. While paint floats
  (Float) it works on the water instead, nudging and combing what floats. Icon: a brush beside a water drop (Blur's is a
  plain drop).
- Brush Lab: Drag and Smear have a new last dial **Keeps Paint** (off to 1); Smudge has a fifth choice **Clear Water**
  (Pick Up Paint, Lay Down Paint; its module reads "Water").
- Words: "Smears. Keeps paint for the next stroke." / caption tag "keeps paint"; "Clear water. Lifts paint, lets it
  run. Wet for 5.0 s, runs and pools when tilted." / caption "Watercolour, clear water, runs".

The name: the Basic tool is **Water brush** (a painter's tool: a water brush re-wets watercolour) because Float's
**Clear water** preset already has that name and preset names are unique; the Smudge choice is **Clear Water**.

## Engine

- `PartDial.KEEPS` (key `keep`), last of Drag's and Smear's dials, in `ModularCode.ADDED` (written only when on), never
  rolled (`ModularStyle.rollChoice`), never nudged by a variation. Every older code reads it as off and writes as before.
- `PartChoice.CLEAR_WATER` (key `water`), Smudge's index 4, `ModularFeatures.seedOnly`: no style weighs it, a variation
  never swaps to it; `ModularStyle.rollClearWater` makes a brush Clear Water from its seed (salt `0xc1ea4`): Anything
  goes 1 in 40, Watercolor and Ink wash 1 in 50, never on Float; Pick Up Paint .35–.75, Lay Down Paint .4–.8.
  `ModularBrush.ownPaint`: Clear Water lays no paint of its own (Off 1, else Own Paint).
- **Keeps Paint** (`HeldPaint`, pure): per holder (the drawing screen's tool key, `/finger` for the finger; Brush Lab's
  page one of its own) the reservoir `Carry` ended with (cells' coverage and darkness, NaN where untouched). The next
  stroke's cells start, at first touch, Keeps Paint of the way from the page to the held paint, so it lays it where the
  brush is darker. A size change maps the cells across the dab. Kept while the app runs (`WatercolorSession.held`), not
  saved. Undo doesn't give paint back to the brush.
- **Clear Water** (`ClearWater`, pure): under the pen it is Lift (`Carry`: starts clean, takes twice as readily, soaks
  up 1 − Lay down), and marks how wet each pixel is on the plane (`CarryPlane.wetTile`). At lift, Lay Down Paint of
  what it lifted (the loose darkness found less what is there) spreads by two box blurs (about .8 of the dab's radius)
  through the wet softened half as far, a little more at the wet's soft edge, laid back as a glaze; with Slow drying the
  loose paint under the wet becomes wet paint again (`CarryPlane.rewet`): its darkness over the rest is the stroke's
  `pendingWet`, its held-back share (.45 × Flow While Wet) lifted off for now, handed to `DryingWater.lay` as a wash's
  body is, so the session settles it, ticks it on the worker and runs it with `GravityFlow`. Nothing new in the wet
  model: the stroke's Drying part is the water's.
- Rules kept: only the loose part moves (`CarryPlane`: the kept part and what masking fluid shields stay put; masking
  fluid also keeps the run-out off). A wash still wet when the stroke began (`WatercolorSession.stillWet`: the box of
  the Slow wash `settleNow` laid just before, nothing else since) isn't set yet: there the eraser's rule doesn't hold
  it, and the water lifts 1.6 times as readily, runs 1.4 times as far and lays back 1.25 times as much.
- **On the water**: `WatercolorSession.onWater` gives a Clear Water stroke, while a bath floats on the selected pair,
  its Float twin (`ClearWater.afloat`: Float drying, on the bath's layer), which joins the bath (`settleBefore`) as
  Float's Clear water does; `ResolvedBrush.floatParts` counts Clear Water as dragging with no paint.
- Speed (desktop, `WetSmudgeChecks`): per sample the same as Smear (60 px about .065 ms, 120 px about .16 ms); the
  Water brush's lift 3 to 4 ms (the Manta about twenty times that).

## UI

`ToolSlots.SmudgeKind` gains WATER and DIRTY (`smudgeKind`, `keepsPaint`); `InkWashBar.smudgeIcon(brush, dirty)`
(`ic_water_brush`, `ic_dirty_brush`, `ic_dirty_brush_held`), `heldChanged` after each stroke (`DrawingPad`), Clean
Brush in `fillSmudges`; `InkWash.stroke(document, tool, finger)`, `holder`, `smudgeDirty`, `cleanSmudge`.

## Web twins

`parts.js` (the dial and choice, codes, words), `stroke.js` (`carryDab`: Clear Water lifts, Keeps Paint from the
surface's `heldPaint`; `runOut` at lift, a Slow one's share to the host's water as a wash's) and `newmods.js` (the
roll, its checks `wetChecks`), the lab page patched with the same hunks, Brush Studio rebuilt. The web lab's Clear
Water re-wets only what it lifted (not the paint under it); codes are byte for byte the app's (`java-codes.sh`).

## Sizes (2026-10-19 fix)

A quick Water or Dirty brush (past 36 px) previewed its wash's nib, a tenth of its size, over a smudge as wide as its
size dot; it now previews its smudge's dab, in the light wash's dither. The Water brush's run-out spreads about a
quarter past the dab, as built. See the Stump spec, "Sizes: what shows is what it smudges"; `SmudgeSizeChecks`.

## Doubts

- PHILOSOPHY #1 (no hidden state): a dirty brush holds state between strokes; the grey head and Clean Brush show it.
- The held paint isn't saved and Undo doesn't put paint back on the brush.
- Inside a still-wet wash's box the water may also lift older paint there that the eraser couldn't (the box is coarse).
