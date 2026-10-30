# MonoPaint-style paint for Gouache, Oils, Pastels and Dry brush (2026-10-22, proposal)

Research and design only. No app code changed. Branch looked at: `inkwash-v2-lab` (`90c4873`). MonoPaint looked at:
upstream `main` `8743286` ("Bring back the clear-water wet brush pen and add opaque wet paint", on top of 0.98.3
`5212e95`), cloned fresh.

**The owner's ask (2026-10-22):** "I like the way his brushes work... can we bring our system more in line with his for
the paint media? I like our other ones and ink." He likes all four of: **wet flow** (paint stays wet, spreads and runs
into itself, follows gravity, until it is dried), **opaque wet paint** (thick paint that pushes into and mixes with
what's there, re-dips each stroke, and clear water lifts it), **responsiveness** (marks appear quickly and smoothly
under the pen), and **simplicity** (fewer knobs; brushes behave like the medium).

**Scope:** the **Gouache, Oils, Pastels and Dry brush** styles only. Watercolor, Ink wash and the ink and line tools
are not touched.

---

## 1. MonoPaint's paint pipeline (upstream `8743286`)

All files are under `app/src/main/java/io/github/mpdairy/monopaint/`.

### The wet model: `WetWatercolor` (1095 lines)

- **One grey plane of "pigment"** (0–255 ink over white), kept beside the dry drawing. It covers only the 16 × 16
  tiles that have been painted. Each tile holds `pigment`, `target`, `next`, the bits `wet`, `water` and `flowing`, plus
  `pull`/`mobility` (how fresh the water is), `reserve` (lifted paint that hasn't dissolved yet) and `stuck` paint.
  Drying throws this state away. The tones it wrote stay behind as ordinary saved pixels.
- **Two modes.**
  - **Wet canvas** (`new WetWatercolor(document, wetness)`, so `wholeCanvas`): each stamp first *hydrates* the
    existing drawing within 8–32 px of the stamp. `hydrate` → `lift` turns the drawn tone into pigment, so old paint
    joins in.
  - **Local water**: brush pens and watercolour mode. Only the pixels it touches are wet.
- **Opaque paint into a wet canvas** (`paintMask`, the Brush on a wet canvas):
  - Each pixel the stroke reaches for the first time (the `touched` bitset, so overlapping dabs never compound) takes
    the new pigment.
  - Its `target` is set to `old × .6·wetness + new × (1 − .6·wetness)`.
  - Then `document.paintTone` writes the pixel at once. **The pixel under the pen is final when written.**
- **Flow** (`advance` → `computeTile` → `evened`/`flowed` → `settle`):
  - Each frame, each wet pixel moves toward its 12 neighbours along four 3-sample rays (about 40%) and toward its
    target (15%): `value·(1−.55w) + Σneighbours·w/30 + target·.15w`.
  - Gravity weights the rays: `fromAbove = 1 + gy`, `fromBelow = 1 − gy`, and the same for x. Paint drifts downhill
    while the total stays the same.
  - Flowing water also trades whole tiles at a time (`planSpread`/`shapeSpread`, `POOL` .7 straight downhill).
  - A tile is awake for `FRAMES` = 20 frames after it is touched (`WATER_FRAMES` = 40 for the pen's water).
    `FRAME_MS` is 150 ms, and flowing water can go as fast as 50 ms a frame. Fresh water dries by `DRYING` .985 a frame.
  - Pixel work for a batch of tiles is shared with up to 3 spare cores (`share`).
- **Opaque wet brush pen** (`waterMask(…, cover, coverage)`, new in `8743286`):
  - The water carries the pen's shade.
  - It covers `Coverage` (default 50%) of the paint under it, and of lifted paint's hidden reserve, as each pixel is
    first reached. White pushes dark paint back.
  - An ink-holding pen trades `MIX` = .25 of the paint it covers into its own load (`BrushInk.trade`), so its colour
    gets dirty along the stroke.
  - **Re-dip:** `DrawingPad.penDown` calls `paint.brushInk.load(gray)`, so every opaque stroke starts dipped in the
    selected grey again.
- **Clear-water pen lift** ("Hold ink between strokes" off, so `clearWater`):
  - The flowing water lifts dry paint into the water (`lift`; `reserve = pigment × (DEPTH−1)`, `DISSOLVE` .8).
  - The lifted paint thins out through the wet area.
  - The tile-scale spread now blends as ink, so lifted paint no longer darkens what it runs into.
- **Oil paint mode** (`PaintLoad`, Brush option "Oil paint mode when opaque"):
  - The brush lays its grey solidly, runs out along the stroke (`travel`, a smoothstep over `paintLength`), and mixes
    toward what it picked up (`PICKUP_LENGTH` 40 px).
  - Once empty it only smears (`SMUDGE` .45).
  - Rubbing the pen on the colour bar reloads it (`PaintRub`).
  - Into a wet canvas it uses `pickUp` + `wet.paintMask(load.tone(), deposit)`.
- **Gravity:** `CanvasGravity` reads the accelerometer, smoothed .2, with a flat dead zone of .08. It listens only while
  wet paint moves and only when the "Watercolor uses gravity" setting is on.
- **Drying:**
  - The **Wetness slider** (`WetnessBar`; 0 dries the canvas) wets the canvas.
  - The **droplet** (`WetDroplet`) ripples while paint moves. A tap shows the **blow dryer** (`BlowDryer`): it calls
    `dryWet()`, which lays queued dabs and drops the model.
  - Choosing a tool that doesn't wet also dries.
  - Otherwise wet paint **stays wet indefinitely**. Its tiles stop animating after about 3 s, but they keep their wet
    bits, so a later stroke still mixes into them.

### Under the pen: why it feels quick on the Manta

`DrawingPad.onTouchEvent` → `sampleEvent` for every historical sample → `PressureStroke.sample` → dabs:

- **Dabs are cheap.**
  - The stamp is drawn by Android's Canvas into a reused mask (`BrushStamp.draw`; Round/Flat/Filbert, crescent for
    oil).
  - Then one cheap pass over its pixels: `paintMask`/`waterMask`/`PaintLoad.dab`. The spacing is `.4 × minor radius`.
- **Each pixel is written once, and its first write is final.**
  - The `touched` bitsets in `WetWatercolor` and `PaintLoad` stop overlapping dabs from rewriting a pixel.
  - The e-ink panel shows a pixel after its last change. So in MonoPaint the edge of the mark is final the moment it
    lands: no preview, and no second update when a render or a texture arrives later.
- **One render a pen event:** `renderDirty()` (the union rectangle) → `flush(false)` → `DirectEink.present` of the
  dirty patches, coalesced in an **8 ms** window.
- **Flow never competes with the pen.**
  - It runs from an idle handler (`advanceWet`) under `WetWorkBudget`: 0.5–2 ms a slice and at most 16 tiles while
    drawing, 5 ms idle.
  - The budget is **0** for 100 ms after any pen event that took ≥ 8 ms or arrived ≥ 24 ms late.
  - A slice is skipped while the pen's pixels still wait to be presented. Flowing water is presented at most every
    120 ms.

### Knobs the user sees

- **Brush:**
  - Head (Round / Flat / Filbert).
  - Min/Max size (and Brush height for Flat).
  - Pressure response.
  - Oil paint mode (and Loading speed and Minimum load when it's on).
- **Wet brush pen:**
  - Size, Strength (60) and Pressure.
  - Hold ink between strokes.
  - Coverage (50).
- **Global:**
  - Opaque / Transparent.
  - Watercolour droplet.
  - Wetness slider.
  - Gravity setting.

That's about 4–6 controls per tool, and the wetness is a single page-wide state.

## 2. Inkwash today, for these four styles

### The presets (decoded from `ToolPresets.ROWS` with `ModularCode.decode`)

| Style | Presets | Tip | Medium | Smudge (Carry) | Paper | Drying (Life) |
|---|---|---|---|---|---|---|
| Gouache | Opaque round, Wide gouache, Flat gouache, Swell gouache, Felt gouache | Round / Hake / Flat nib | **COVER** 1.0, Impasto 0, Erasable .3 | **Off** | Plain, Cold press, Felt | **Quick**, Wet Edge 6, no gravity |
| Oils | Impasto, Oil hake, Flat bristle, Dry oils, Side oils, Hog bristle | Round / Hake / Flat nib / Bristles; Dry Brush .5–.9 | **COVER** 1.0, Impasto .43–.85 | **Off** | Canvas | **Quick** |
| Oils | Oil paint (the oil paint spec) | Filbert | COVER, Impasto .28 | **Drag** pick .6, lay .7, Paint Load .55, Starting Load .25 | Canvas | Quick |
| Pastels | Chisel, Soft, Crumpled, Crisp, Side pastel | Chisel / Round | **CHARCOAL** (stick grain) | Off | Laid, Felt, Crumpled, Cold press | Quick |
| Pastels | Wax crayon, Crayon scratch | Round | CRAYON | Off | Cold press | Instant |
| Dry brush | Dry round, Scumble, Flat dry | Round / Flat nib; Dry Brush .5–.9 | COVER .56–.68 | Off | Laid, Crumpled | Quick |
| Dry brush | Dry glaze, Grainy wash | Flat nib / Round | WASH | Off | Cold press | Quick |
| Dry brush | Bristle brush | Bristles | INK | Off | Cold press | Quick |

What these presets share:

- **Twelve of the thirteen Gouache and Oils presets don't mix at all.** Smudge is Off, so opaque paint just covers.
- **None of them stays wet.**
  - Quick only re-settles the last few mm behind the nib while the pen is down (`ModularStroke`, `WET_FRAME_MS` 330).
  - `ResolvedBrush` excludes opaque Cover from gravity (`grav` needs `!(cover > 0)`) and from the held-back settle
    (`heldBack = cover > 0 ? 0 : …`).
- **Drag and Smear (`Carry`/`CarryPlane`) read the page as the stroke found it.** So a smudge moves dry paint exactly as
  readily as fresh paint. Wet and dry are not told apart.

### Inkwash's own wet machinery

- **Slow drying (`DryingWater`, `GravityFlow`) is for transparent washes, one stroke at a time.**
  - `WatercolorSession` keeps one settling wash. **Any new stroke** first lays it where it is (`settleBefore` →
    `settleNow`).
  - So there's no wet-into-wet across strokes, except Float baths and the Water brush's "still wet" box.
- **The Water brush (`ClearWater`, Carry choice 4) is MonoPaint's clear-water pen**, rebuilt on `CarryPlane` plus a
  Slow wash, from the same upstream commit (the wet-smudge spec). It re-wets washes, not opaque paint.
- **Re-dip and loading already exist:**
  - `LoadedPaint`: Paint Load / Starting Load, rub a grey to load, and the gauge.
  - `HeldPaint`: Keeps Paint and Dirty brush.

### What's left of MonoPaint's wet canvas in Inkwash

- `WetWatercolor.java` is **byte-identical to upstream `8743286`**. Also still present: `BrushInk`, `PaintLoad`,
  `CanvasGravity`, `WetWorkBudget`, `WetnessBar`, `WetDroplet`, `BlowDryer`, `WatercolorWash`, and `DrawingPad`'s
  `advanceWet`/`scheduleWet`/`dryWet`.
- **The modular brushes never use any of it. In practice it's dead code.**
  - `PaintActivity` always makes `inkWash` (line 148).
  - `DrawingPad.penDown` takes the `app.inkWash != null` branch → `beginInkStroke`, which calls `dryWet()` and never
    `wetPaint()`.
  - Only the old `ToolSettings` path (`ToolStrokes` → `PressureStroke`) would feed `WetWatercolor`, and Inkwash never
    reaches it.
  - The droplet and wetness bar are still built in the old colour bar (`PaintActivity` 336–347). Inkwash's own bar
    shows its own wet indicator (`InkWashBar` `WetIndicator`, "tap to dry" a Slow wash).
- **Masking fluid:** Inkwash has its own Resist medium (Masking fluid, Wax resist presets) and the document's mask.
  `WetWatercolor` writes through `ToneDocument.setTone`/`paintTone`/`setTones`, which still respect the mask.
- **Not handled:** Inkwash's erasable share or kind planes. `WetWatercolor`'s slices call `document.begin()` with no
  erasable or kind (see the risks).

## 3. Side by side

### How the paint behaves

| | MonoPaint | Inkwash today (these styles) |
|---|---|---|
| Wetness | Page-wide state (Wetness slider). Paint stays wet until dried (dryer, slider at 0, a non-wet tool). Tiles animate for about 3 s after each touch. | Per brush (Drying). Opaque paint is final once the Quick window passes. A Slow wash stays wet until the next stroke. |
| Mixing | New opaque paint takes `.6·wetness` of what's under it as its target, then diffuses with its wet neighbours. Old dry paint near the stroke is re-wetted (hydrate). | None, unless Smudge is Drag/Smear, which drags the page as found, wet or dry alike. |
| Re-dip | Every opaque wet-pen stroke starts dipped in the selected grey. The pen gets dirty along the stroke (MIX .25). Oil mode runs out and reloads by rubbing. | Drag/Smear start from their own paint. Paint Load runs out and rub-to-load reloads it (ported). Keeps Paint holds dirt between strokes. |
| Lift | Clear-water pen lifts **anything** wet or dry, and the lift runs through the water. | Water brush lifts and re-wets washes. It doesn't treat opaque paint as wet. |
| Gravity | Any wet paint, opaque included. | Washes only. Opaque Cover is excluded. |
| Drying | One tap (blow dryer). Otherwise it waits. | Automatic (window or drying time). Any next stroke sets the paint. Tapping the indicator lays a Slow wash. |

### Responsiveness

The engine's cost per sample is **not** the gap. A desktop bench (this Mac, live `ModularStroke` versus
`WetWatercolor` with MonoPaint's dab spacing) on the bench's 1400 × 900 wave over marks, mean ms per sample:

| Size | Opaque round | Impasto | Oil paint | Hog bristle | Soft pastel | Dry round | MonoPaint wet canvas | MonoPaint opaque wet pen |
|---|---|---|---|---|---|---|---|---|
| 60 px | .098 | .085 | .127 | .143 | .112 | .042 | .059 | .052 |
| 120 px | .033 | .106 | .222 | .126 | .092 | .074 | .047 | .069 |
| 240 px | .048 | .106 | .298 | .138 | .120 | .106 | .155 | .227 |

- MonoPaint's flow after the stroke costs about .005–.012 ms per tile-frame. That's 140–370 ms of desktop CPU per
  stroke over 20–40 frames, all in idle slices, shared across cores.
- The harness is at `a scratch folder, `paintprop/src/io/github/mpdairy/monopaint/PaintBench.java`.

What the owner feels comes from three other things:

1. **Preview, then render.** Every Gouache, Oils, Pastels and Dry brush preset is drawn quick (`QuickStroke.quick`)
   from about 30–60 px up. The rule is `PANEL` = 36 px of body reach; pastels and bristles from 30 px, the rest from
   60 px.
   - While the pen is down the owner sees a **flat grey preview**.
   - Then the true texture is written behind the pen (Trail) or at lift. Each pixel therefore changes twice, which
     means two grey panel updates of about 270 ms each (the 2026-10-19 audit).
   - MonoPaint writes each pixel once, final, as the edge reaches it.
2. **Quick re-settles** change pixels behind the nib again and again (`WET_FRAME_MS` 330, re-tapers every 50 ms).
3. **Present spacing.** Inkwash spaces presents by `1.5 × presentMs`, 8–100 ms (`DrawingPad.presentGap`). MonoPaint
   uses a fixed 8 ms. This needs a device measurement before anyone changes it.

### UI complexity

- **MonoPaint:** about 5 knobs a tool plus 3 page-wide controls (opaque/transparent, droplet, wetness).
- **Inkwash:** six parts with 2–25 dials each in Brush Lab. The everyday choice is a Basic tool: one of 5–7 presets
  per style.
- **Wetness in Inkwash** is spread over Drying (Quick/Slow and their dials), Smudge (Drag/Smear/Clear Water and their
  dials) and Medium Opacity. No single control means "this paint is wet".

## 4. Options

### Option A (recommended): a "Wet" Drying choice that uses MonoPaint's wet canvas

Bring the wet canvas that already sits in the tree into the six-part engine as Drying's eighth choice. Gouache and Oils
presets use it.

**Engine**

- **`PartChoice.WET_PAINT`** ("Wet", key `wetpaint`): Life index 7, appended. **No existing code holds it**, so every
  share code reads and writes byte for byte. It is seed-only, like Clear Water and Filbert: no style rolls it unless
  added with a salted roll after every other part, so older rolls don't change.
  - **Stays Wet** (0–1, written as `ADDED`): maps to `WetWatercolor` wetness (mixing share and diffusion strength) and
    the frames a touch wakes.
  - **Runs Downhill** (reuse `PartDial.RUNS`): scales the gravity handed to `setGravity`. 0 means it doesn't run.
  - That's all. No other dials.
- **`WetPaintStroke`** (new `LiftingStroke`), chosen in `QuickStroke.start` when Life is Wet and Medium is COVER. It is
  always live, never quick.
  - **Footprint:** each sample's footprint comes from the existing stampers' geometry (`BodyStamper.shape` for
    Round/Flat/Filbert/Hake and the crescent). Paper tooth and Dry Brush gaps are folded into the mask's coverage once
    per pixel.
  - **Laying paint:** the mask goes to `WetWatercolor.paintMask(mask, …, grey, opaque, strength)`. Strength is the
    Medium's Opacity times `LoadedPaint`'s paint left. With Drag or Smear, `PaintLoad`-style pick-up mixes the load
    toward what it crosses: Inkwash's `LoadedPaint` already ports `PaintLoad.travel`, so add only its `pickUp`.
  - **Re-dip:** each stroke starts at the selected grey (MonoPaint's `brushInk.load`), or at the held load with Keeps
    Paint or Paint Load.
  - **Clear Water** (Smudge choice 4) on a Wet brush → `waterMask(flowing, ink = null)`: MonoPaint's clear-water pen.
    It lifts wet *or* dry opaque paint and lets it run.
- **`WatercolorSession` gets a fourth kind beside the Drying wash, Float bath and Halos: a wet canvas.** It holds one
  `WetWatercolor` per page and layer pair.
  - A Wet stroke on that layer joins it, as Float joins its bath.
  - The wet indicator shows while it runs. Its tap is the blow dryer (`dryWet`, which already exists in `DrawingPad`).
  - Undo sets it first.
- **Frames:**
  - Run them on `DrawingPad`'s existing `advanceWet` idle loop with `WetWorkBudget` (pen first), unchanged from
    MonoPaint.
  - Gravity comes from Inkwash's `PageTilt`/`TiltFeed`, not `CanvasGravity`, so there is one sensor path.
- **Impasto and texture:**
  - The wet canvas diffuses, which would blur paper grain and ridges.
  - v1: tooth is applied as the paint is laid (it survives as pigment variation). Impasto relief is drawn once, when
    the canvas dries (`dryWet`), over the dried tones.
  - Alternatively, Wet presets are made without Impasto (the owner's call).
- **Attribution:** `WetWatercolor`, `BrushInk`, `PaintLoad` and `WetWorkBudget` are MonoPaint's code (MIT, © mpdairy)
  and already in this MIT fork. Keep their headers, add "from MonoPaint `8743286`, MIT" to the new class's doc and to
  CHANGELOG, and leave LICENSE as it is.

**What the owner sees and does**

- **Gouache** (Wet, Stays Wet medium, Opacity .9, no Smudge or a light Drag):
  - Strokes cover crisply as they land, then soften into wet neighbours for a few seconds.
  - The next stroke pushes into and mixes with them.
  - Tilt the Manta and wet paint creeps downhill.
  - The droplet ripples while paint is wet. Tap it, and the blow dryer sets everything.
  - The Water brush lifts wet *or* dry gouache (gouache re-wets).
- **Oils** (Wet, Stays Wet high, Drag with Paint Load, Filbert or Flat):
  - Each stroke starts dipped in the selected grey and gets dirtier as it drags through wet paint.
  - It runs out, and rubbing a grey loads it (already built).
  - No run by default: Runs Downhill 0, since oils don't drip.
- **Under the pen:** the mark is the final mark from the first touch. No flat preview is replaced later.
- **Simplicity:** the Wet presets have one meaningful dial each (Stays Wet). Brush Lab's Drying shows "Wet" with two
  dials.

**Responsiveness:**

- The desktop bench above puts the wet canvas at .05–.07 ms a sample to 120 px. That fits the ≤ .1 ms budget.
- At 240 px it is .155 to .227 ms a sample, over budget. MonoPaint runs at that size on the owner's Manta today, using
  Android's native stamp raster, but it needs `scripts/bench_device.sh` before shipping.
- Fallback: past the budget, draw the mask at the lattice (every other pixel), as `ModularStroke.latticePx` already does
  for paints.

**Effort: L** (about 5–8 days with checks and the web twins).

| Piece | Size |
|---|---|
| Choice, dials and code | S |
| `WetPaintStroke` | M |
| Session kind and indicator | M |
| Erasable/kind writes in `WetWatercolor` (`begin(erasable, KIND_PAINT)` per slice) | S |
| Impasto at dry | S–M |
| Presets | S |
| `parts.js` and Brush Studio: the choice, codes and words. The web lab shows it as plain Cover (no wet model on the web). | M |
| Checks: codes, a bench case, a `WetPaintChecks` (mixing, re-dip, lift, gravity, dryer), device bench | M |

**Risks**

- **Speed:**
  - Big sizes (240 px+) and long wet sessions. Many awake tiles cost about 20× the desktop on the Manta.
  - `WetWorkBudget` keeps the pen first, but the flow may look slower than on the desktop.
  - Re-wetting old paint near a stroke (`hydrate`) can wake many tiles on a busy page.
- **Correctness:**
  - `WetWatercolor` predates Inkwash's erasable shares, kinds and layer pairs. Every write path must set them, or the
    eraser and Erase-kind rules break. This needs a check.
  - Undo inside a wet session: MonoPaint makes each slice a continuation, so one Undo takes back a stroke plus its
    flow so far.
- **Codes:** none break. Existing presets keep their codes. New Wet presets are new rows (or replace rows, see the
  questions).
  - If a row's code is *replaced*, slots that hold the old code still load and draw as before. They just stop matching
    the preset by identity (`ToolPresets.find`) and show as a custom brush.
- **Two wet models side by side** (`DryingWater` for washes, `WetWatercolor` for opaque paint): a Slow wash and a wet
  canvas can't both be wet. One lays the other first, the way Float already works.

### Option B: retune the existing parts to approximate it

No new model. Change resolve rules and presets:

- Let opaque Cover take Slow drying, gravity and the held-back settle (remove the `cover > 0` exclusions in
  `ResolvedBrush`).
- Give every Gouache and Oils preset Drag (pick about .5, lay about .6) with Paint Load, so each stroke re-dips and mixes.
- Let the Water brush's "still wet" join apply to Cover strokes.
- Retune Quick's Wet Edge for paints.

**What the owner gets:**

- Strokes smear what's under them (re-dip plus drag).
- The last stroke settles and runs a little downhill.
- The Water brush lifts the last stroke.

**What he doesn't get:**

- Paint that stays wet across strokes. Each new stroke still sets the previous one.
- MonoPaint's "pushes into wet paint" look. `DryingWater` settles pigment into paper valleys, which is a watercolour
  look, not opaque mixing.
- Responsiveness. Slow drying is the preview-then-relay-at-lift path, so it adds lag rather than removing it.

**Effort: M.**

**Risks:**

- Changes the look of opaque Slow brushes that already exist in other styles (Hake's "Cover hake" and rolled brushes).
- Drag at large sizes goes quick sooner (`QuickStroke.CARRY` is 90 px), so it gets slower, not faster.

### Recommendation

**Option A.** It is the only option that gives all four things the owner listed.

- The model is MonoPaint's own and is already in the tree, unchanged.
- It fits the six-part engine as one Drying choice, with no break in share codes.
- Its pen path writes each pixel once and final, which is the main reason MonoPaint feels quick on e-ink.

Suggested order of work:

1. **A1:** `WetPaintStroke` and the session kind, with Gouache Wet presets. Device bench.
2. **A2:** Oils (Drag and Paint Load), Clear Water on wet paint, gravity.
3. **A3:** Pastels and Dry brush interaction (below).
4. **A4:** web twins.

## 5. Pastels and Dry brush

MonoPaint has **no pastel or dry-brush model** to port. Its wet canvas is a diffusion. Run on a stick's grain or a dry
brush's skips, it would blur exactly what makes them pastel and dry brush. **They should stay dry media.** "Wet flow" and
"opaque wet paint" make sense for them only as an interaction with wet paint that is already on the page.

- **Pastels: "into wet paint."**
  - Over dry paper they draw exactly as now: same codes, same pixels.
  - Where the stick crosses tiles of an active wet canvas, its deposit also goes into the wet canvas as pigment
    (`paintMask` at its coverage). The pastel line softens a little, darkens like wet pastel, gets pushed around by the
    next wet stroke, and dries with the canvas.
  - The rest of the grain stays.
  - Effort **S–M**.
- **Dry brush: "thirsty brush."**
  - Over dry paper they draw as now.
  - Over wet paint the brush drags it, as MonoPaint's dry Brush pen does (`waterMask(flowing = false, carry)`). A
    COVER dry brush also lays its own grey into the wet at its Opacity, so scumbling into wet gouache or oils mixes.
  - "Dry glaze" and "Grainy wash" (WASH) and the Bristle brush (INK) only drag.
  - Effort **S–M**.
- **Compatibility:** both only kick in where a wet canvas exists, and only new Wet strokes make one. So every existing
  page, code, golden and bench hash for these styles stays the same. No code or dial changes.
- **Responsiveness, optional, separate (flag):**
  - Pastels and the Dry brush presets go quick from about 30–60 px (stick grain: `STICK` 200 reach; `PANEL` 36).
  - A pixel-once live rule for their body (the most coverage a stroke reaches, not dab-by-dab darkening), as MonoPaint
    does, would let them draw live and final under the pen.
  - That **changes their look** (a stroke no longer builds darker over itself), so it's the owner's call. Not part of
    A.

## 6. Against PHILOSOPHY.md and the architecture rules

- **#1, one obvious behaviour:**
  - "Wet" is a Drying choice. A Wet brush is a different tool, not a mode, and MonoPaint's page-wide Wetness slider
    is not brought over.
  - The paint the canvas holds while wet is hidden state. The droplet shows it, as it does for Slow washes today.
- **#2, fixed values over settings:** Wet presets fix Stays Wet. Brush Lab adds one choice with two dials, and the
  Basic tools add no controls.
- **#4, marks stay put unless the tool says so:**
  - Wet paint keeps moving until it is dried, which goes further than "its few seconds".
  - It's opt-in, and the preset words say it ("stays wet", "mixes", "runs when tilted").
  - Open question 2 sets how long it lasts.
- **#5, physical media:** this is the point of the proposal. Gouache re-wets, oils stay open and drag, pastel sinks
  into wet paint and a dry brush drinks it.
- **#7, would the app feel worse without it:** the owner asked for it.
- **Architecture:**
  - Extend the owners that already exist rather than writing parallel code:
    - `WetWatercolor` for the wet canvas.
    - `LoadedPaint` and `HeldPaint` for the load.
    - `WatercolorSession` for the "one wet thing at a time" rule and the indicator.
    - `DrawingPad.advanceWet` and `WetWorkBudget` for pacing.
    - The stampers' geometry for footprints.
  - `WetPaintStroke` is the only new class: the glue from the six-part resolve to `WetWatercolor`. If it grows a
    second copy of `PressureStroke`'s dab loop, factor that loop out first.
  - Update `docs/INTEGRATION.md`: `WetWatercolor` goes from dormant to live engine code, and its erasable and kind
    writes are a generic fix to split out.

## 7. What stays the same

- Watercolor, Ink wash and the ink and line tools.
- Float, Mold, Fading and Lemon.
- Slow washes and `GravityFlow`.
- Every share code.
- Rolls, unless the owner wants Gouache/Oils rolls to reach Wet (a salted roll, appended).

## 8. Open questions for the owner

1. **Replace or add?** Should the new Wet Gouache and Oils brushes *replace* today's Gouache and Oils presets, or sit
   first in those styles with the current ones kept below? Old codes keep loading either way.
2. **When does wet paint dry?** Only on the blow dryer tap, leaving the page or a pause, as in MonoPaint? Or also as
   soon as an ink pen or pencil stroke is drawn, as Inkwash sets a wash today? (Gouache re-wets old dry paint the way
   MonoPaint does. Should Oils?)
3. **Pastels and Dry brush:** is it right that they stay dry and only interact with wet paint (pastel sinks into it;
   dry brush drags or lifts it), and should the pixel-once speed-up be tried even though it changes their look?

## 9. Owner decisions (Elling, 2026-10-22)

**Option A**, with these answers to the open questions:

1. **Replace.** The new Wet Gouache and Oils brushes *replace* today's Gouache and Oils presets on the Tools page.
   - Old share codes still decode and draw wherever they are kept: slots, the library, notebooks. Only the preset list
     changes.
   - A toolbar slot holding an old Gouache or Oils preset keeps its brush (it now shows as a brush of its own).
2. **Wet paint dries only when dried.** A tap on the droplet (the blow dryer), or leaving the page or the notebook. An
   ink, pencil or any other stroke does *not* dry it.
3. **Pastels and Dry brush stay dry media**, but react to wet paint as section 5 says: pastel sinks into wet paint, a
   dry brush drags it and soaks it up.
   - The "one write per pixel" speed-up (no preview, then texture) is run as an **experiment**: switchable, off by
     default, so the owner can compare looks on the Manta.

**What the owner liked** (keep these): wet flow; opaque wet paint (mixing; oils re-dip and get dirtier as they go;
clear water lifts wet or dry paint); responsiveness; simplicity (one "Stays Wet" dial, plus Runs Downhill).

**Unchanged:** Watercolor, Ink wash, and the ink and line tools. Pages and brushes that don't use Wet must render byte
for byte as before (a check says so).

**Follow-up answers (the owner, 2026-10-22, after the first build):**

4. **Wet paint mixes only with other paint**, not with white paper: dark stays dark on fresh paper (no lightening).
5. **Gouache re-wets** dried paint under a new Wet stroke; **dried Oils do not** (an oil stroke covers them, unmixed, and
   they don't move).
6. **A new install's slot 6 is Wet gouache.**

The build is specified in `2026-10-22-wet-paint-design.md`.
