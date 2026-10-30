# Marble and Bristle: notes

Prototype: `marble-and-bristle.html`, one file, Google Fonts only.

Tools in this folder:
- `check.cjs`: Puppeteer checks, screenshots and the in-browser bench.
- `lab.cjs` with `png.js` (copied from weblab): writes `shot-bristle-dial-sheet.png`. Rows are Cohesion .1/.5/.9 and columns Spread .1/.5/.9; the last row is Dryness 0/.6/.95.
- `runout.cjs`: writes `shot-bristle-runout.png`.
- `shot.sh`.

The rng, addPoint and paper-tooth tile were copied from weblab via living/. weblab, living and blend were not edited, and no repo was touched. URL flags: `?t=bristle`, `?demo`, `?set`, `?manta`, `?bench`.

## Marbling (its own tool)
- **Rings are vector outlines** in page px, stored oldest first. A clear drop is a ring at level 0.
- **Drop (Jaffer and Lu):** every point moves to `C + (P−C)·√(1 + R²/|P−C|²)`. This keeps area exactly, and the new drop lands in the hole it made.
  - So painting the rings oldest to newest is exact; no boolean ops are needed.
  - A held drop is the same map applied again: it adds 10 % of the area per 100 ms, up to 4×.
  - Ink and Clear alternate after each drop.
- **Swirl and comb:** each tine is a moving stylus with stream function `ψ = (U×r)·exp(−r²/2σ²)`.
  - The flow is divergence-free, so area is kept. It returns fluid round the sides, like a real stick.
  - **Slip** (.72 for swirl, .8 for comb) lets the tine pass through the fluid. A point's total shift is then bounded at about 2–4σ, which is the finite shift of J&L's tine lines. Without slip, ink rode the tine for the whole stroke.
  - **Comb:** a rake of tines W apart, set across the first 4 px of the drag. Each point feeds only its two nearest tines, so the cost is O(1) per point.
  - **Substeps:** each step keeps the pen's travel under 0.3σ.
- **Refine:** after each op, split edges over 1.6 px and merge points under 0.45 px. Total points are capped at 240k by coarsening.
- **Display:** while a stylus moves, only the outlines are drawn (1 px, black for ink and grey for clear). At rest, the fill is drawn exactly as it will print.
- **Set** prints the bath, or it prints itself 8 s after the last touch:
  - Canvas fills the rings with antialiasing, oldest first.
  - × paper tooth (.93–1).
  - `max` into the page, then 16 greys.
- **Determinism:** the op log is the session, and a replay is bit-identical.

## Bristle swarm (a Wild pen setting)
- **Hairs:** N of them, from `seed ^ hash(first sample)`. Each hair's state is its lateral offset u and velocity w; its lag behind the pen is fixed.
- **Steps:** the stroke advances ≥1 step per px of pen travel, computed from the samples alone. Resting does nothing, and there is nothing to do at lift.
- **Forces on each hair, per step:**
  - **Fluoddity paired sensors.** Ahead-left and ahead-right sensors read a fading shared trail field: 2 px cells, persistence .93, with lazy decay through a power table.
    - The response is `f(s) = s/(1+(s/sat)²)`: hairs go toward moderate trail and away from crowding.
    - **Cohesion** sets the gain (negative below ~.45, which makes even streaks) and `sat` (tuft size).
  - **A soft wall** at the head's half-width. **Spread** opens it with speed and pressure.
  - **A seeded wander** (Spread).
  - **A weak spring** to the hair's slot.
- **Ink:**
  - **Grey:** a lone hair is grey and a tuft is black; the grey is set by how many hairs share the hair's line across the head.
  - **Load** drains per px by `.0009 · Dryness³ · thirst · (1 + .6·speed)`. The default Dryness is .15.
    - Dryness 0 never runs out.
    - On a ~1600 px stroke, Dryness ≤ .5 stays ≥ 99 % inked; .7 thins in its last third; only near 1 does it run out within a few hundred px.
    - Speed lowers what a hair holds by `.2·Dryness²·speed`, so fast strokes skip a bit more.
    - Each stroke starts full (`shot-bristle-runout.png`, made by `runout.cjs`: Dryness 0/.15/.35/.5/.7/1).
  - **Dry hairs** ink only where the paper tooth is under what they still hold, so they skip.
  - **Before Elling's note**, the drain was linear in Dryness and about 4× steeper with speed, so even .35 ran out within ~400 px.
- **Fast/WYSIWYG:** what shows while the pen is down is the final mark.

## Cost (Chrome on this Mac)
| | |
|---|---|
| Drop | 0.03–0.05 ms |
| Comb/swirl, 31k points | 0.46–0.57 ms per 10 px of pen travel, plus refine ~0.5 ms per move |
| Comb by touch on the 390 px phone check, 10k points | 0.50 ms per 10 px |
| Print, 900×640 | 5–8 ms |
| Area drift over the whole demo stir | +0.07 % |
| Bristle, 1214 px stroke, M | 16 hairs 12.9 ms · 32 hairs 16.9 ms · 64 hairs 36 ms (0.33–0.5 µs per hair-step) |
| Manta toggle, measured | 2.95–3.1 redraws/s |

- **Bristle per 1000 px:** ≈11 / 14 / 30 ms. On ARM Java at 5–10×, expect ≲150 ms per 1000 px at 32 hairs.
- **Reproduction:** both engines are bit-identical twice. The bristle check feeds the stroke whole versus in 3-sample chunks.
- **Earlier version:** before slip and thin hairs, the numbers were 250k points with combs at 1.2 ms per 10 px, and bristles at 65 ms per pen-second.

## Java port sketch
- **Marble is a new tool**, not a brush: `MarbleBath` and a `MarbleSession` beside `WatercolorSession`.
  - **Bath data:** rings are `double[] x, y`, plus a level. `opDrop`, `opRake`, `refine` and an op log are the stored "samples".
  - **While dragging:** run ops on a background thread and coalesce moves. Draw the outlines with `Path` into the dirty box (the union of the rings' bounds) and present through `DirectEink` at ≤3 Hz.
  - **Print:** `Canvas.drawPath` with `ANTI_ALIAS` into an `ALPHA_8` tile per 64 px, then `max` into the `ToneDocument` tiles through `GrayPalette`. One undo step.
  - **Page changes:** undo, another tool or a page turn while the bath is wet sets it first.
- **Bristle lives in `WildInkStroke`.** When Swarm is on, a `BristleSwarm` (float arrays) replaces the nib stamps.
  - It is fed per sample segment and stamps into the stroke's own coverage buffer, using `WildBrush.Random`.
  - Keep the trail field per stroke in 64 px tiles, not the whole page.
  - Lift is the existing finish with no taper-out.

## BrushCode / settings
- **Ink, appended after SPLATTER** (and after Living's settings, if those land first):
  - `SWARM("Bristle swarm", {"off","on"})`
  - `BRISTLES` (6–64), `COHESION`, `SPREAD`
- **Code length:** that adds 1 + 3×2 = **7 characters**, e.g. `…1g7jsg7`.
- **Reused settings:** Dryness is the existing `DRY` (Dry brush), and S/M/L are the existing preset sizes.
- **`decode`:** allow the trailing settings to be missing (missing means off), so old codes still read.
- **`roll()`:** draw these last, and keep Swarm rare (~10 %).
- **Marble's dials** (Ring size 6–80 px, Comb width 10–80 px) are tool settings, not in BrushCode.

## Risks
- **Points can blow up under long stirring.** Combing cost scales with points; the cap coarsens outlines.
  - Filaments under 1 px alias to speckle at 16 greys.
  - Linear splitting is approximate, so very thin filaments could cross and break painter's order. That is rare, and the nonzero fill hides it.
- **JS/Java drift:** only `exp` is transcendental (sqrt is exact). Use `StrictMath` plus a matching polynomial or LUT for a bit-exact share or replay.
- **E-ink:**
  - A full filled redraw of the bath at 3 Hz would flash, so keep fills for rest and Set.
  - Grey clear-ring outlines may vanish in A2/DU modes; use dashed black instead.
- **Bristle cost** scales with hairs × px. Cap at 32 hairs on the device, or step 1.5 px for L.
- **Taste:**
  - High Spread with low Cohesion turns into hairy spaghetti.
  - High Cohesion with high Spread can split into two strands plus one stray hair. This reads as alive, but may look like a glitch.
  - A finger stroke starts and ends blunt (only pen pressure tapers it).
- **The bath sets itself** after 8 s: the one timed event. The result never depends on that timing.
