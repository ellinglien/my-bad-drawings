# Living Ink: notes

Prototype: `living-ink.html`, one file, Google Fonts only. Tools: `check.cjs` (Puppeteer checks and shots), `nodebench.js`, `mksheet.js` → `sheet.html` (each mutation at low/mid/high). Engine bits are copied from weblab (`weblab-engine.copy.js`). weblab was not touched, and no repo was changed.

## Life is drying time (Elling's note)
- The life dial is the brush's **Drying time** (`BrushSetting.DRYING`). It uses the same name and the same squared curve as paint, `0.75 s + span × d²`.
- For living ink the span runs to **30 s**, so the range is 0.75–30 s and the default is d = .55, about 9.6 s.
- Why 30 s:
  - At 3 refreshes a second, paint's 5 s is only ~15 visible frames, which is too few to read as growth.
  - 30 s gives ~90 frames, a slow time-lapse.
  - Longer than that reads as a screensaver and holds the page hostage.
  - Paint's whole range is the dial's bottom third (d ≤ .38 gives ≤ 5 s).
- Growth slows as the ink dries: each tick's vigour is `(1 − t/life)^1.4`, so speed, budding and sticking all ease to zero. Then the ink is fixed for good.

## Clock and determinism
- **Ticks:** 10 fixed ticks per wet second; wall time only decides how many have run.
  - The final picture never depends on frame rate or cadence.
  - "Dry now" fast-forwards to that same final picture.
- **RNG:** `rng(brushSeed ^ strokeHash(points) ^ mutationSalt)`. Paper fibres are a 256 px tile fixed to the page, like PaperGrain.
- **Environment:** a snapshot of the page taken at lift.
- **Ink only darkens.** Each stroke accumulates into its own level buffer, which reaches the page by `max`, so living strokes commute with each other.
- **Checked:** bench runs repeat bit-for-bit for all five mutations.

## Mutations
All five share the dials **Growth**, **Reach** (8–64 page px) and a third dial specific to the mutation (**Habit**), plus Drying. They seed only from the stroke's edge and live only within Reach (a chamfer distance band).

1. **Wick (Habit: Fibre pull).** Capillary tendrils start at edge pixels on the outward normal.
   - Each tick they steer toward the local fibre axis, with a seeded wiggle.
   - They deposit a faint line modulated by the paper, and their load decays over ~0.55 × Reach.
   - Tendrils branch occasionally and die in other ink.
2. **Bud (Habit: Cell size).** Cells sit on the edge, swell, then bud up to 3 daughters outward, each 72–98 % of its parent's size.
   - An occupancy mask stops overlaps.
   - Each cell's look (membrane ring, sometimes a nucleus) is fixed at birth. Growing only reveals it outward from the centre, so it is monotone.
3. **Mould (Habit: Wander = sensor angle).** This is physarum built on Fluoddity's paired sensors.
   - Scouts sense left, ahead and right, steer up the trail and toward *other* ink, and lay trail.
   - The trail uses Fluoddity's persistence × 4-neighbour diffusion, over the band only.
   - Veins darken where traffic exceeds 3× the band's mean, and they bridge nearby strokes.
4. **Frost (Habit: Crystal).** Diffusion-limited aggregation.
   - Walkers spawn just beyond the growth front and random-walk with a slight drift toward the stroke.
   - They stick on touch: reluctantly at low Crystal, which gives soft coral, and readily with a six-fold axis bias at high Crystal, which gives fine frost.
   - Sticks come from a budget paced by vigour.
5. **Swim (Habit: Mutation).** Fluoddity's own rule.
   - The brush seed generates 10 Fourier centres, so the seed picks the species. Three cohorts carry copies mutated by Habit (Fluoddity's `mutate_rule`).
   - Swimmers read the velocity field at two sensors. The black box is evaluated mirrored and averaged, so it has no left/right bias, and gives force plus strafe.
   - Each swimmer deposits its velocity and a very faint track. Only shared paths show at 16 greys.
   - In the last ~12 % of the life, swimmers stop and darken in place as small rod "bodies".

Dropped: **breathing**. A crawling edge has to lighten pixels, which ghosts badly on e-ink and breaks the "only darkens" rule. Bud and Wick cover edge life.

## Cost (Chrome on this Mac, typical stroke)
Setup: 844 px two-loop stroke, medium nib, dials ~.5, 10 s life, ~64k px within reach.

| mutation | ms/tick | peak | ms per wet s | agents |
|---|---|---|---|---|
| Wick | 0.22 | 0.4 | 2.2 | 272 |
| Bud | 0.19 | 0.4 | 1.9 | 205 |
| Mould | 1.08 | 1.3 | 10.8 | 2400 |
| Frost | 0.33 | 0.9 | 3.3 | 420 |
| Swim | 1.07 | 1.4 | 10.7 | 420 |

- In node the numbers are ~1.5–3.5× higher (no JIT warmup; Swim 35–40 ms/s).
- Expect the Manta's ARM Java at ~5–10× these: under ~100 ms per wet second worst case, on a background thread.
- Agent counts are capped (Wick 900, Bud 1400 cells, Mould 2400, Frost and Swim 420), and every field loop runs over the band only.
- **Memory:** about 6 floats per region pixel; ~200k px gives ~5 MB in JS. In Java, use bytes/shorts in 64 px tiles.

## Java port sketch
- **New `LivingInk`, one per stroke.** `WildInkStroke.finish()` builds it after the stroke is laid, from:
  - the stroke's own `WashCoverage` (pre-tooth),
  - a page snapshot of the region,
  - `brush`,
  - a hash of the samples.
- **New `LivingSession`, beside `WatercolorSession`.**
  - It keeps the list of wet strokes with their lift time.
  - A `HandlerThread` runs ticks at 10 Hz. Each tick writes `max(level)` into `ToneDocument` tiles and unions a dirty rect.
  - The UI posts a partial GC16 refresh of the dirty rect at ≤3 Hz.
- **Undo, erase, another document or another tool:** fast-forward every wet stroke to dry, so the result never depends on timing, then proceed. Stroke and growth are one undo step.
- **Determinism:** every random draw comes from `WildBrush.Random`. Math is float with fixed op order. Swim needs a `sin` lookup table so JVM and JS agree; check bit-exactness in `BrushCodeChecks`-style desktop tests.

## Dials as BrushSetting / BrushCode
- **Append to INK only, after SPLATTER:**
  - `LIFE("Life", null, 0, 0, {"still","wick","bud","mould","frost","swim"})`
  - `GROWTH`, `REACH`, `HABIT` (0..1; the lab labels HABIT per mutation)
  - the existing `DRYING`
- **Code length:** that adds 1 + 4×2 = **9 characters**. The page shows the tail, e.g. `…3i0lli0js` for mould.
- **`DRYING.shown()`:** branch on kind (`livingMs` vs `WashLook.wetMs`).
- **`BrushCode.decode`:** it currently requires `at == digits.length()`. Old pen codes must still read, so allow the 5 trailing settings to be missing (missing means still, 0).
- **`roll()`:** draw these last, so old seeds keep their character. Make Life rare (~10 %) or lab-only.

## Risks
- **"Nothing moves on its own" is broken by design.** Life is bounded and fast-forwarded on any page change, but a wet page still blocks a full refresh for up to 30 s.
- **Timing-dependent neighbours.** A stroke drawn next to wet ink snapshots partly grown neighbours, so Mould and Frost "food" can differ between live drawing and replay. Fix: snapshot only settled ink (exclude living levels).
- **JS/Java float drift** in Swim (trig) and Mould (diffusion sums). A share code then reproduces the *brush*, but growth may differ by a pixel. Needs a bit-exact test or a LUT plus fixed point.
- **Ghosting.** Monotone darkening helps. Mould's grey veins at high Growth can look heavy at 16 levels.
- **Taste.**
  - Swim at high Growth and Reach gets busy and starts to read as "screensaver". Cap its default Growth at ~.5.
  - Frost at Crystal > .8 shows a comb-like axis bias.
- **Cost scales with stroke length × Reach.** A page-long scribble at max Reach could be ~5× the bench, so cap the band size or agent counts per stroke.
