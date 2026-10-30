# Float (suminagashi): design

Status: approved by Elling (owner: "yes do it"), the three open questions answered (Undo A, the Sumi and Clear water
pair, Clear water leaves a Slow wash alone); built on branch `inkwash-float`, 2026-10-16: see "As built" at the end. Builds on `2026-10-14-modular-engine-design.md` (the Drying part, codes,
Speed on the Manta, Gravity, Quick strokes on `inkwash-quick`) and `2026-10-16-smudge-design.md` (Own paint, the
own-layer rule). Prototype: Float in the web Modular Brush Lab (`docs/brush-lab/prototypes/modular-lab.html`, sources
in `modular-lab-src/float.js`, `stroke.js`, `parts.js`, `ui.js`, `checks.js`).

## In one paragraph

**Float** is a fourth Drying choice, after Instant, Quick and Slow. The stroke's paint floats on water instead of
soaking in. A **tap** (or the pen held still) drops paint that spreads into a disc and pushes everything already
floating outward into rings; a **drag** lays the stroke's own paint as a flat film that shoulders older paint aside,
and, with Smudge Drag or Smear, combs what floats. A Float brush with **Own paint 0 is Clear water**: it lays nothing,
its taps make clear rings and its drags only move paint, which is MonoPaint's dry brush pen on water. Alternate a Sumi
slot and a Clear water slot and you get suminagashi rings. A tilted page drifts the floating paint. When its floating
time has passed since the last touch, or when you tap the wet droplet, it **prints** onto the paper and is ordinary
dry paint from then on. Floating paint is a stack of vector outlines, so ring edges stay crisp at 16 greys however
much it is stirred.

## What you see

| You do | On the water | Why it looks right |
|---|---|---|
| Tap with Sumi | a disc of ink; older rings pushed out around it | a drop on the bath (Jaffer and Lu's drop map, area kept exactly) |
| Tap with Clear water | a clear disc; the ink around it becomes a ring | the clear-water (surfactant) drop of real suminagashi |
| Hold the pen still | the drop keeps spreading: +10% area a 100 ms, up to 4× | ink feeding from a resting brush (marble-and-bristle's held drop) |
| Drag Sumi (Smudge Off) | the brush's mark as one flat grey film; older paint pushed aside, none covered | a loaded brush laying a band on water |
| Drag Clear water | older paint dragged along the track, feathered; nothing of its own | a needle, comb or MonoPaint's dry brush pen |
| Drag a hake of Clear water | broad bands pushed: rings split into hearts and wings | a wide stylus |
| Drag a multi-line or split nib of Clear water | a comb: one thin pull per line | a rake |
| Tilt the tablet | everything floating drifts downhill, the middle of the bath faster than its sides, so rings shear into chevrons | a tray tipped while the ink floats |
| Wait (floating time), or tap the droplet | it prints: the same greys, the paper's tooth taking a touch less ink in its valleys; then it never moves again | the paper lifted off the bath |

Pictures (made by the lab's engine in Node, 16 greys; not committed): `scratchpad/float-out/`
`1-drops-and-clear.png` (three sites of Sumi and Clear water taps in turn, held a different while each),
`2-combed.png` (the same bath, a 5-line Clear water rake down, up, down, then a round Clear water spiral),
`3-hake-band.png` (a hake wash laid above two sites, then a Clear water hake dragged through their middles),
`4-tilted-drift.png` (the same rings, printed level on the left and after 5 s tipped down and right on the right).

### The rules, decided

- **Tap or drag, never both.** Within 3 px of pen-down: a tap (drop at lift) or a hold (drop after 150 ms, then it
  grows). Past 3 px it is a drag and nothing drops: combing with Clear water must not start with a clear hole.
- **One grey per film.** On water every medium is a smooth film: the stroke's body and its line are traced at half
  coverage into outlines, each one flat grey (the mean darkness inside: the body at its glaze, the line at its ink). The
  medium's texture (granulation, charcoal fuzz) is gone while it floats; the paper's tooth comes back at print. Taps are
  the brush's own grey: the line's ink, or the body's glaze for a wash. Pressure and the Hand's darkness set it.
- **Paint never covers floating paint, it displaces it.** A drag lays a drop every 0.6 of its half-width along the path,
  each with the area of the band it lays (× Own paint), so older paint is pushed out of the band before the film lands
  in the hole. Points near the pen take each push in order; points far from it take one push of the summed area (the
  far field differs by under 0.1 px).
- **Smudge decides whether it drags** (the Smudge spec's dials, read on water):

  | Smudge | On water |
  |---|---|
  | Off, Lift | lays its paint, pushes, doesn't drag (Lift has nothing to lift from water: it acts as Off) |
  | Drag, Smear | lays Own paint (0 = Clear water: nothing laid, nothing pushed) and drags by **Pick up** |
  | Lay down | unused on water; the app's Brush Lab hides it for a Float brush (as it hides Takes ink; the web lab still shows it) |

- **The drag** is marble-and-bristle's stylus: each tine a stream function `ψ = (U × r)·exp(−r²/2σ²)` (divergence-free,
  so area is kept), substeps under 0.3σ, with **slip** `.45 + .5 × pull` so the pen passes through the paint and each
  point's shift is bounded (about 1.6σ at pull 0, many σ at pull 1: long feathers). **Pull** is MonoPaint's brush-pen
  pull: `Pick up × (.15 + .85 × pressure)`. Tines: one, σ = 0.8 of the tip's half-width (a hake's 0.7: broad); a split
  nib two, a multi-line one per line, at their placed offsets across the motion, σ = 0.4 of the line's width (thin).
- **Drop size** `(8 + 3w)(.4 + 1.6 × Spread) × √(.4 + .6 × pressure)`, w the film's half-width, 4–240 px. At Spread .5 a
  fineliner's tap makes a disc about 30 px across (60 held a while), a 120 px wash's about 400.

## Dials (Drying · Float)

`[drying, spread, drift]`, in that order; Float is Drying's choice index **3** (`PartChoice.FLOAT`, key `float`).

| Dial | Key | Range, default | Meaning |
|---|---|---|---|
| Floating time | `drying` (Slow's dial, `KEEPS` carries it across Slow ↔ Float) | 0–1, .6 (`wetMs`: 0.75–20 s, 7.7 s at .6) | how long after the last touch it prints |
| Spread | `spread` (new life dial, `F_SPREAD`) | 0–1, .5 | how big a tap's drop is (Simple's macro: small rings ↔ wide rings) |
| Drift | `drift` (new life dial, `DRIFT`) | 0–1, .5 | how fast it drifts on a tilted page: up to 40 px/s upright at 1 |

- **Codes.** The Drying segment becomes `3` + three two-digit dials, e.g.
  `f.M70i000000000at_000zz0000000000000_3wejs00_000_3lli0i0_…`. Every existing code has choice 0–2 there and decodes
  and re-encodes byte for byte (nothing appended to Instant, Quick or Slow). The three dials are ordinary dials of a
  new choice, written always, not `ModularCode.ADDED`. Downgrade: an app without Float reads a Float code as null
  (choice past the part's choices), as for any new choice; noted in the CHANGELOG.
- **Rolls: rare.** Decided after every other part, as `rollGravity` is, and from the brush's seed rather than another
  random draw (`seed × 2654435761 mod n`), so every other roll is unchanged for the same `Random` (the lab's seeded
  starting library is identical, checked): Anything goes 1 in 30, Ink wash 1 in 25, Watercolor 1 in 40, other styles
  never. Spread rolls .3–.8, Drift .5. Variations never swap to Float.
- **Words.** "Float · 7.7 s"; "This brush": "…floats on water for about 8 s: a tap drops it in rings that push older
  paint outward, dragging combs it, then it prints onto the paper"; a Clear water brush: "Clear water from a round
  tip: no paint of its own. On floating paint a tap drops a clear ring that pushes the paint outward, and dragging
  combs it." Captions "float" or "clear water".
- **Tools page:** one pair under Ink wash, **Sumi** (Round · Ink · Smudge Off · Float, Press → darker) and **Clear
  water** (the same with Smudge Smear, Own paint 0, Pick up .9). No new tool, no new button (open question 2).

## Clear water is MonoPaint's dry brush pen, on water

What upstream does (`WetWatercolor.layWater` with `flowing = false`, `ToolSettings.pull`, `PressureStroke.applyMask`):
the brush pen in Dry mode is clear water with no ink of its own. Its **pull** is `strength/100 × (.15 + .85 ×
travel(pressure)^exp)`. Each dab measures the average pigment under it; the pen carries one scalar **load** (first
dab's average), lays `(load − pigment) × .5 × pull` into each newly wet pixel, and re-loads `(under − load) × .3 ×
(1 − carry)²`, so it drags what it passed over, white too, along the stroke. The water it leaves keeps evening its
pigment with its neighbours (12 samples along rays at 1, 4 and 8 px, cut off by dry pixels, at mobility `pull³`) for
40 frames: nearby paint seeps into the clear track. Gravity weights the neighbours `1 ± g`.

How it stays fast on the Manta: only 16 px tiles the pen touched exist; frames run from an idle handler, never under
the pen; each slice has a tile and time budget (96 tiles, the deadline checked between tiles); a watercolor dab is
shown at once as a flat glaze and laid in full later (`queueWater`); up to three helper threads share a frame; a huge
wash steps two frames at once. The pen never waits for the model.

What Float takes, and the mapping (it is the Smudge spec's Own-paint-0 brush, no new dial):

| MonoPaint dry brush pen | Float · Clear water |
|---|---|
| clear water, no ink of its own | Own paint 0 |
| Strength, scaled by the pressure curve | **Pick up**, `pull = Pick up × (.15 + .85 × pressure)` (the same curve) |
| drags what it passes, white too, further with Carry | the stylus drag: points ride the tine with slip `.45 + .5 × pull`; a stroke begun on white pulls white into the ink |
| the brush's size (with lean) | the tip's half-width sets the tine (the Tip part; lean through the Hand) |
| wets only what it touches, edges sharp on dry paper | moves only paint within 4σ of a tine |
| work only where touched, budgeted, never under the pen, preview first, cores | the same habits (Speed, below) |

Not taken: **trading pigment** (a scalar load smears rings into grey streaks; on water the film moves whole, so we
move outlines and edges stay crisp); **the seep** (a floating film doesn't diffuse; crisp edges are the point);
holding ink between strokes (Smudge spec: every stroke starts clean).

**Name.** "Dry brush" is already a style (scratchy broken strokes), so the manipulator is called **Clear water** in
the UI and in "This brush", never "dry brush". The ink it pairs with is **Sumi**.

## The bath: same layer only

- **One bath, one layer.** `WatercolorSession` holds at most one `FloatBath`, on the layer of the stroke that started
  it (ink brushes the pair's ink layer, wash and cover its wash layer, as every stroke; Smudge spec, option A). A Float
  stroke on the same layer joins it; it never reads or moves another layer.
- **What prints the bath first** (`settleNow`, decision 10's list, as Slow's settling): any stroke that doesn't float
  (the eraser too), a Float stroke on another layer, a fill, a layer change, leaving the screen or pausing the app,
  another page or drawing, the droplet's tap. **Not** a slot, shade or size change: alternating Sumi and Clear water
  (and Dark and Light) is how you make rings. A Slow wash still settling is laid first when a Float stroke begins.
- **Under the water** the layer's dry paint is read once, as found (`TonePage`, as `GravityFlow`), and the bath is
  shown as `dry × (1 − film)`: floating paint darkens what is beneath and never moves it. Clear water shows the dry
  paint through.
- **Print** writes `dry × (1 − film × (.9 + .1 × tooth))` over the bath's box, in the bath's gesture, then drops the
  bath. Every medium prints darkening only (a lifted film is a glaze); light gouache over dark is lost on water.

## Undo

Two options:

| | Undo while it floats | Undo after it printed |
|---|---|---|
| **A. Stroke by stroke on the water** (recommended) | takes back the last Float stroke and everything it moved: the bath returns exactly to before it (from a snapshot), still floating; the floating time restarts | one Undo takes back the whole print: the page as before the bath's first stroke |
| B. Pixels only | each Float stroke is an ordinary undo step; Undo restores the pixels before it and the bath stops floating (what you see is dry) | the print is a continuation of the last stroke: one Undo takes back the print and that stroke |

**Decision: A.** While it floats, each stroke moved every older ring; taking one back must put them all back, which
pixel undo can't do without drying the bath (B makes Undo end the floating, a surprise and hidden state). After the
print, the pattern is one sheet lifted off one bath; taking back only its last stroke would need the bath afloat
again (PHILOSOPHY #4: it moved, then set). How: the bath is one gesture from its first stroke to its print (later
Float strokes continue it, as Slow's settle continues its stroke); while it floats, Undo goes to the bath
(`InkWash`/`DrawingPad`'s undo asks `WatercolorSession` first): more than one stroke → `undoLast`, re-render in the
same gesture; one stroke → cancel the gesture. Redo after an Undo of a print prints it again (the gesture's redo).
Snapshots, not replay: replaying the log is exact (checked) but costs 0.1–0.9 s on the Mac for 40–150 strokes, 2–18 s
on the Manta. Keep the rings as they were before the latest stroke (one copy, ~0.5 MB at 30k points) and a checkpoint
every 8 strokes; an Undo further back replays at most 7 strokes from a checkpoint.

## Gravity

Float uses the tilt already wired for Slow (`TiltFeed`, `PageTilt`, its dead zone `FLAT` ≈ 4.6°): `wantsTilt` is also
true while a bath floats with Drift above 0. Each drying tick (10 a second, paced like Slow's) moves every point by
`ĝ × A × (.35 + .65 cos²(πn/2))`, `n` the point's place across the slope over the bath's half-width, `A = 40 px/s ×
Drift × (g − .08)/.92 / 10`: a shear along the slope, so area is kept and rings stretch into chevrons. Level page or
inside the dead zone: nothing moves (checked: the bath's points are identical). Ticks step by age, so a backed-off tick
ends in the same place. Runs, Drips, Pool, Sink and Blend are Slow's and don't apply; the Control's Page tilt row
still shapes the stroke as for any brush.

## The e-ink look

- 16 greys, no dithering in the bath or the print: flat greys are what the panel shows best. A ring's grey is one of
  the app's shades (ink Dark, Light at tone 120, Lighter at 175; a wash's glaze at a third of that), so rings of Sumi, light Sumi and Clear water
  read as distinct bands.
- Edges: 4 samples down each row, exact cover across it, so an edge is at most one in-between grey (the check: 0.6 in-
  between pixels an edge). Outlines are refined to points 0.45–1.6 px apart after every pen event, so a stretched ring
  never becomes a polygon.
- While the pen is down (Quick strokes' preview): the pen's own paint as the light dither the preview uses for a wash
  ("something happens here"), and the bath redrawn only within reach of the pen, one sample a row; at lift the full
  render.

## Speed on the Manta

Measured in Node on this Mac (`scratchpad/float-out/bench.cjs`, one core; Manta ≈ 20× that). A bath of Sumi and Clear
water taps at three sites, then strokes dragged 700 px through it.

| Bath | Points, rings, box | Tap (drop) | Drag ops a 10 px of pen (round 14 / 5-line rake / Sumi band / hake 90) | Full render | Quick render | Lift | Drift tick | Print |
|---|---|---|---|---|---|---|---|---|
| small | 12k, 36, 245k px | 18 µs | 0.16 / 0.29 / 0.38 / 0.26 ms | 3.8 ms | 1.8 ms | 4–15 ms | 2.0 ms + render | 12 ms |
| medium | 28k, 72, 325k px | 41 µs | 0.30 / 0.51 / 0.86 / 0.49 ms | 9.6 ms | 3.5 ms | 10–23 ms | 3.4 ms + render | 12 ms |
| large | 92k, 144, 468k px | 134 µs | 1.2 / 1.3 / 2.6 / 1.6 ms | 27.6 ms | 9.6 ms | 28–48 ms | 8.5 ms + render | 32 ms |

So on the Manta, for a medium bath: a drag's moves cost 6–17 ms per 10 px of pen on one core (a fast stroke covers
10 px in 5–10 ms), a full render ~190 ms, a lift 0.2–0.5 s, a drift tick ~140 ms with a quick render. Unoptimised, a big bath
would lag. The plan, in order:

1. **Points, not pixels.** Floating paint is outlines; every move is a per-point formula, the cost follows the points
   (capped: past 120k points refining coarsens, as the lab's 240k cap), not the page. Done in the prototype.
2. **Only what the pen reaches.** A drag gathers the points within reach of its tines once per segment and refines
   only those rings; pushes refine only rings near the pen, and points far from it take one summed push. Done in the
   prototype (up to 7× cheaper drags than without it).
3. **Cores.** Points move independently, so every op splits across `Cores` by ranges of points (same result on any
   number of cores); the render splits into bands of rows. Expect ~3.5× on the four A55s: a medium drag ~2–5 ms per
   10 px, a full render ~55 ms.
4. **Quick, then render** (`QuickStroke`): Float strokes always go quick. The pen's thread shows the preview only; the
   ops run on the render thread as samples come, in time-budgeted slices (MonoPaint's `queueWater` habit: shown at
   once, laid in full when there is time; never under the pen); the bath shows within reach of the pen at the present
   pace (8–100 ms), one sample a row; at lift, drain the ops, render in full, present. The droplet shows meanwhile.
5. **Ticks** render one sample a row and only every present (≥120 ms; e-ink shows about three frames a second); a tick
   over 40 ms backs off as `SettlePacer` does. Drift steps by age, so the page ends the same.
6. **Memory.** 8 bytes a point (float x, y): 30k points 0.24 MB, the 120k cap ~1 MB; the dry layer under the bath in
   tiles read on first touch; snapshots for Undo ~10 MB at worst.

Bench cases to add (`ModularPerfBench -e float`): Sumi taps ×30 then a Clear water drag at 14 px, a 5-line rake at
12 px, a Clear water hake at 90 px and a Sumi hake wash at 70 px over it; a 5 s tilt; Undo; print. Pass: pen-to-screen
p95 under 60 ms for the preview, lift to final under 0.5 s for a medium bath, ticks inside the pacer's budget.

## Where it lives (for the port)

- Pure: `FloatBath` (rings, the op log per stroke, drop, pushes, rake, drift, refine, the scanline render, print,
  snapshots and `undoLast`), `FloatTrace` (marching squares at half coverage), `ResolvedBrush.Float` (spread, drift,
  floating ms, own paint, pick, film half-width, tines, σ, greys), `PartChoice.FLOAT`, `PartDial.F_SPREAD`, `DRIFT`,
  `ModularStyle.rollFloat`, `ModularWords`. `ModularStroke`: a Float stroke stamps its line and body as Instant does but
  composes nothing; it feeds the bath and, at lift, traces its film. `ModularFeatures.FLOAT` switch.
- `WatercolorSession`: holds the bath beside the Slow patch; `wet()`, `settleNow` (prints), `tickDrying` (drift,
  print on time), `wantsTilt`; Undo goes to it first while a bath floats.
- Android: none new (`DryingTicker`, `WetIndicator`, `TiltFeed` as they are). No upstream file changes: the bath
  writes through `ToneDocument`'s existing block writes in its own gesture.

## Checks

In the web lab now (`floatChecks`, in the page's RUN CHECKS and Node; all pass), and to port:

1. A drop pushes older paint out without changing any ring's area (0.00%).
2. Sumi and Clear water in turn print as rings: ≥ 10 dark/light edges along a ray, ≤ 2.2 in-between pixels an edge.
3. Clear water on blank paper lays nothing (a drag and a tap: 0 pixels).
4. The log is the bath: replaying it gives the same points, bit for bit.
5. Undo while it floats takes back the last stroke exactly.
6. A level page, and a tilt inside the dead zone, don't move it; tilted it drifts downhill (57 px in 2 s at Drift 1)
   with area kept.
7. It prints when its floating time is up.
8. A Float code round-trips; every non-Float code is unchanged (the lab's 300-roll round trip, the legacy codes).

Also kept passing: the lab's 47 click-through assertions (now with Sumi and Clear water taps, a drag, tilt, Print) and
the 560-combination sweep (every Tip × Medium × Smudge × Drying draws). For the app add: `LegacyConversionChecks` and the
bench's page hashes unchanged with Float off; rolls (every other part as before for the same `Random`; Float at its
odds over 2000 rolls); the own-layer rule (a bath on the ink layer leaves the wash layer and other pairs
byte-identical); what prints first (a non-Float stroke, a layer change, a pause; not a slot, shade or size change);
Undo after print restores every layer; quick and live Float strokes end on the same points; `CoresChecks` for the ops
and the render.

Device checks for Elling: Sumi and Clear water taps in turn at Medium and Large; a Clear water drag through a big
bath (does the preview keep up, how long after lift); a Clear water hake; tilting while it floats; the droplet; Undo
while floating and after the print; pausing mid-float.

## Open questions for Elling

1. **Undo while floating: stroke by stroke (A) or pixels only (B)?** Recommended: **A** (above).
2. **Sumi and Clear water as a ready pair on the Tools page** (under Ink wash)? Recommended: **yes**, two presets, no
   new tool.
3. **Should Clear water also move a still-wet Slow wash**, as MonoPaint's dry pen moves wet watercolor? Recommended:
   **no** for now: Float's Clear water acts on floating paint only; on a Slow wash it would need the pigment-trading
   model Float leaves out, and a new stroke already lays the wash first.

## As built (2026-10-16)

Branch `inkwash-float`, from `inkwash-v2-lab` `aea75fc` (Smudge and the wait after lift in). The owner's answers: Undo
stroke by stroke while it floats and one Undo for the whole print (A); Sumi and Clear water as a ready pair under Ink
wash; Clear water acts only on floating paint (a Slow wash still wet is laid first, as before any stroke).

### Where it lives

- **Pure engine** (`scripts/test_inkwash.sh` sources): `FloatBath` (rings, ops logged per stroke, drop, pushes, rake,
  drift, refine, the point cap, snapshots and `undoLast`, what moved since the last show), `FloatScan` (the scanline
  film, bands on `Cores`), `FloatTrace` (marching squares over the stroke's own buffers, its footprint rows only),
  `FloatPen` (a stroke on the water: tap, hold, drag, films at lift, from its samples only), `FloatWater` (the bath on
  its layer: the layer as found in undo tiles, the film written premultiplied through `smearTones`, the one undo step,
  ticks, print, Undo). `ResolvedBrush.Float` and `Life.FLOAT`; `PartChoice.FLOAT`, `PartDial.F_SPREAD`, `DRIFT`;
  `ModularStyle.rollFloat`, `ModularWords`, `ToolPresets` (Sumi, Clear water), `WildSample` (a Float tile's rings).
- **Strokes:** `ModularStroke` stamps a Float stroke's line and body as Instant does (no Fast-wet window, no merge
  into a body, no Smudge reservoir: on water Drag and Smear comb by Pick up instead) and composes nothing; at lift its
  films float and the bath shows. `QuickStroke.quick` is true for every Float brush: the pen's thread sweeps the own
  paint's light dither and writes the bath frames the render thread makes near the pen (`ModularStroke.floatFrame`,
  one sample a row, every `QuickStroke.FRAME_MS` = 60 ms of its time once caught up); at lift the render thread lays
  the films and works out the show (`develop`), the pen's thread writes it.
- **Session:** `WatercolorSession` holds the bath beside the Slow patch: `settleBefore` (a Float stroke on the bath's
  layer joins it; anything else prints it), `settleNow` prints, `tickDrying` drifts and prints on time, `wantsTilt`,
  `onlyRendering` (the droplet shows at once while paint floats), `undo`. `InkWash.stroke` calls `settleBefore`.
- **Upstream files:** `ToneDocument.undoSteps` (+2: whether a continuation emptied the bath's undo step) and
  `PaintActivity.undo` (+2 −1: Undo asks the session first). `DrawingPad`, `DryingTicker`, `WetIndicator`, `TiltFeed`
  are unchanged.

### What changed from the plan, and why

- **The owner's one doubt (vector-clean rings)**, answered in the engine: a drop's radius varies ±14% and its edge
  carries three faint waves (2, 3 and 5 lobes, up to 4% of its radius), from the brush's seed and the drop's place,
  so rings in turn are never of one width; a tap's grey follows pressure through the hand's darkness (Sumi is Press →
  darker), so light and dark rings alternate unevenly; the full show and the print soften each edge by one in-between
  grey (`FloatWater.SOFT`, a [.12 .76 .12] blur each way) and carry a faint grain of the ink (up to 10% from the paper's
  mid noise), the print the paper's tooth on top. Ring edges stay crisp at 16 greys (`FloatChecks.crisp`: 12 edges
  along a ray, 0.3 in-between pixels an edge).
- **Undo:** the bath is one undo step by `finishContinuation`, its first change a step of its own; Undo while it floats
  restores the rings kept before the last stroke (`FloatBath.begin`'s snapshot; a checkpoint every 8 strokes, replayed
  from), shown again in the same step. The bath's only stroke is the page's own Undo. Redo after an Undo of the print
  prints it again; Redo of a floating stroke isn't kept (the page's Redo list is empty while it floats).
- **Batching:** ops are made per sample, not per pen event, and every flush is logged, so quick and live strokes and
  the log's replay float the same points bit for bit, however the input was batched. A drag is stepped from pen-down
  once it leaves the 3 px of a tap (the lab stepped from where it left them).
- **Showing only what moved:** the bath tracks where points moved more than a fifth of a pixel (before and after), and
  rings whose smaller moves add up past it; a stroke's show reaches only there (plus what its preview wrote). The
  print, a drift tick and an Undo show it whole. A partial show equals the whole one (`FloatChecks.showsWhereChanged`:
  0 pixels differ). Tiles the bath never inked are passed over when writing.
- **Words** were shortened to fit Brush Lab's two-sentence budget (150 characters): "It floats about 8 s: taps drop
  rings that push older paint out, then it prints." (with Smudge: "taps drop rings, dragging combs them"); Clear
  water: "Clear water from a round tip: no paint of its own. Taps drop clear rings that push floating paint outward;
  dragging combs it." Its module says "Float · 7.7 s", whole seconds from 10 s ("Float · 16 s"), to fit as Slow's does.
  The web lab says the same. The Tools page's lines: Sumi "floating rings", Clear water "clear rings".
- **Brush Lab** greys Lay down on a Float brush (the lab greys a dial that does nothing rather than hiding it).
- **Rolls:** `ModularStyle.floatRolls` turns Float's roll off for the checks that compare rolls from before it.
- **The dial demo** (`docs/brush-lab/dial-demo.html`) doesn't show Float: its engine copy (the Gravity Lab's stroke.js)
  has no bath, and adding one was not cheap.

### Checks

`scripts/test_inkwash.sh` 52 PASS (new `FloatChecks`), `scripts/test_document.sh` 86 PASS, `./gradlew
:app:assembleRelease` builds. The bench's 210 page hashes (`ModularPerfBench hashes`, every style, 16–360 px, three
Drying choices) are identical before and after. The web lab: self-test, the 560-combination sweep and the 47-step
click-through pass. `FloatChecks`: a drop keeps every ring's area (0.000%) and pushes it out; Clear water combs (area
within 0.11%) and lays nothing on blank paper; rings print crisp; the print is what floated (no pixel darker, none
more than a tenth lighter, of 12,209); Undo takes back one stroke while it floats (the bath's points and every layer
as before) and, after the print, the whole sheet (Redo prints it again); a level page and one inside the dead zone
don't drift, a tilted one drifts downhill (33 px in 2 s at Drift .5, upright .9) with area kept; it prints on time
(77 ticks for 7.7 s); the same strokes, and the log's replay, give the same bath; quick and live strokes float and
print the same; a bath on the ink layer leaves the wash layer alone; a stroke that doesn't float, a Float stroke on
the pair's other layer and a layer change print it, Sumi and Clear water at any size join it; Clear water over a Slow
wash still wet moves nothing; codes round-trip byte for byte; over 5,600 rolls Float changes no other roll and Anything
goes picks it about 1 in 30; several cores float and show what one does; timing budgets.

### On the Manta (bench build, `scripts/bench_device.sh -e float true`)

Baths of Sumi and Clear water taps in turn at 8 px: small 12.7k points (36 rings), medium 32.5k (72), large 105k
(192), spread over three to six sites along the bench's stroke. Ms, one core unless noted:

| Bath | Drop | Drag moves a 10 px of pen (round 14 / rake 12 / Sumi 10 / hake 90) | Frame under the pen | A stroke's show (work, write) | Drift tick | Print | Undo |
|---|---|---|---|---|---|---|---|
| small | 2.4 | 0.6 / 1.2 / 1.2 / 0.7 | 5 | 60 + 71 | 52 | 71 | 77 |
| medium | 5.5 | 1.3 / 1.8 / 2.1 / 1.4 | 9 | 62 + 95 | 86 | 106 | 95 |
| large | 21 | 3.8 / 2.3 / 6.5 / 2.5 | 15 | 86 + 283 | 250 | 288 | 271 |

Drawn as the app draws them (quick), in real time (the samples as the pen makes them, a present every 16 ms with the
page's render): the pen's thread spends 1–4 ms a present (p95 7–17 ms, max 32), so the pen never waits; lift to final
(the stroke finished and its page rendered) small 0.2–0.3 s, medium 0.21–0.35 s, large 0.28–0.42 s, except a Sumi line
through the large bath, 1.0 s (each step of its band pushes all 105k points; the render thread falls behind while the
pen is down). The bench's usual replay, which hands the render thread every sample at once, reads 0.3–1 s for medium.

### Known limits

- Drift ticks and the print show the whole bath: over the 40 ms tick budget from a medium bath, so a tilted page's
  drift backs off (`SettlePacer`) and moves in steps of a few ticks. Since `inkwash-settle` (engine spec, "Settling off
  the UI thread") both are worked out off the page's thread (`FloatWater.work`, `printing`, on the session's
  `SettleWorker`), the print after each drift with it, so the page's thread only writes them and a stroke that doesn't
  float prints the bath without waiting for a drift; a Float stroke joining the bath still waits for a drift running
  there.
- A Sumi or other paint drag through a large bath pushes every point each step: its moves outrun the render thread
  at 6.5 ms a 10 px, and its lift waits for them.
- The preview's own paint is the light dither until the render thread's frame shows its film greys near the pen.
- Redo of a floating stroke taken back isn't kept.

### Crash fix and caps (2026-10-22)

The owner, after crashes with the Sumi kit: "is there a way to make it less processor intensive, i think it gets
overloaded... if it's too complex we can retire the sumi stuff, it's nice but not worth it if it's a problem".

- **The race.** `InkWash.stroke` calls `WatercolorSession.settleBefore` (which lets go of a print worked out ahead) and
  then `dry`, whose `hold(null)` called `start()`, which started a new print ahead on the `SettleWorker`, reading the
  rings while the joining Float stroke pushed, combed and refined them on the pen's (or render) thread. That print
  was still wanted, so its `ArrayIndexOutOfBoundsException` (`FloatScan$Edges.add`, a ring grown between counting and
  reading) came back through `Job.result()` and crashed the app; without an exception it was stale, and the next
  non-Float stroke wrote a print missing the strokes after it. Fix: `dry` does nothing with no Slow wash settling (no
  work started), and `floatLifted` lets go of any print ahead (`quiet`) before starting one for the bath as it is.
  The bath is now only ever changed and read on one thread at a time. `FloatChecks.worker` draws with a worker and
  checks the worker is idle while a joining stroke draws and the print equals the one with no worker (before the
  fix: idle check failed at the first joining stroke; 6 of 6 runs printed a stale bath).
- **Caps.** Every drop moves every point and shows the whole bath again, and a drag pushed and refined every point
  each pen sample, so the work grew with the bath without bound (600 drops in one spot on a Manta-size page, Mac:
  slowest tap 196 ms, Sumi drag 7.1 ms a sample, a 1 s hold 244 ms, print ahead 115 ms; the Manta ~20× that). Now
  the bath prints at lift past `FloatWater.MOST_POINTS` (50,000) or `MOST_PX` (2^20 px of box), a drag moves and
  refines every `FloatPen.STEPS` (8) steps, and a print ahead let go stops before its writes. Same bench: slowest tap
  ≤ 11 ms (≤ 17 ms with drops spread over the page: the stroke that prints the bath), drag ≤ 0.11 ms a sample, 1 s hold ≤ 11 ms, print ahead ≤ 2.7 ms; a drift tick ≤ 3 ms.

### Device checks for Elling

Sumi and Clear water taps in turn at Medium and Large; a Clear water drag, a rake (a multi-line Clear water) and a
hake of Clear water through a big bath (does the preview keep up, how long after lift); tilting while it floats; the
droplet's tap; Undo while floating and after the print; pausing mid-float (it prints); switching slots between Sumi and
Clear water (it keeps floating).

### Pictures

From the app's engine, the Manta's dots beside the 16 greys (`scripts/float_pictures.sh`): Sumi and Clear water drops
in turn at three sites, six sites combed with a 5-line Clear water rake and swirled with a round one, a hake wash band
above two sites with a Clear water hake dragged through them (hearts), and rings printed level beside rings drifted
5 s on a tilted page.
