# Ink and Water: notes

Page: `ink-and-water.html`, built by `node build.js` from `src/`:
- `engine.js` and `blend.js` are copied unchanged from blend/src.
- `tex.js` holds the cold press scan only.
- `water.js` is new and holds all the physics.
- `ui.js` is the page.

Other files:
- `check.cjs` runs the headless Chrome checks.
- `scene.js` (with `nodeh.js` and `png.js`) renders a node test scene to PNG.

No repo was changed, and blend/, living/ and weblab/ were only read.

## Model
- **Life is drying time.**
  - There are 10 fixed ticks per wet second.
  - Each pixel stores `wetEnd` and `wetLife`, so `wet = (wetEnd − tick)/life`.
  - Speeds scale by `wet^0.8–1.4`, so everything slows to a stop.
  - Drying uses the paint curve `0.75 s + span·d²`, with the span raised to 20 s. The default is 8.2 s.
- **Only darkens.**
  - Every change multiplies onto the wash layer by a darkness that only grows.
  - The single exception is that a crisp line drawn over wet paint is *laid* lighter from its first stamp (the share that bleeds out). It never lightens afterwards. That share is `keep = 1 − ρ(wet)·seep`.
- **Deterministic.**
  - Randomness comes from `rng(seed)`, and the currents and paper are page-fixed tiles.
  - A pen sample joins the sim at the current tick, so the result follows from seed + strokes (with their ticks).
  - "Dry now" just runs the remaining ticks.
  - The bench runs the demo twice in virtual ticks, and the checksums are equal for both styles.

### Plumes (ink meets wet paint)
1. Line coverage that is new on a *wet* pixel gives up `ρ = plume·wet^1.2·0.85` of its ink. It does so more where the paper's blob noise says "seep", so plumes come out at separate points. Dry paper is untouched.
2. Released ink fills **seeps**: one source per ~10 parcels' worth. Each seep lets out one parcel a tick to alternate sides, like smoke from a chimney. These streaklines are what curl.
3. **Parcels** move in three ways:
   - They ride a curl-noise current (a slowly rotating stream function) multiplied by a water-depth ramp, so currents run *along* the wash edge and never into it.
   - **Drift** style also moves them down the gradient of a trail field (ink spreads from where it crowds).
   - **Vein** style instead uses Living Ink's mould senses: three sensors climb the trail, with distant ink as food. Parcels follow each other into channels and bridge to nearby lines.
4. Parcels stain as they travel: by distance (`reach = 20 + 50·plume` px), plus a little where they linger. Their radius grows with distance, time and load.
   - Stains are restricted to wet pixels.
   - Parcels turn back at the edge.
   - Whatever is left drops where they stop as the water dries.
5. Load is capped per parcel (≤ 2Q), so a parcel never lays a dark thread. Parcels are capped at 1200, with a soft cap of 2400 for seeps.

### Settle (pigment in a drying wash)
1. At lift, the blend's own settle computes the stroke's darkness `D`.
   - `(1 − f)·D` lands at once, where `f = 0.45·settle`.
   - The rest is suspended on a half-resolution grid.
2. Every other tick, a conservative upwind flux moves the suspended pigment in four ways:
   - It **drifts** with the same currents.
   - It **flocculates**, drawn up its own blurred gradient. A per-cell crowding cap keeps flocs soft rather than specks.
   - It **rolls into valleys** (paper height).
   - Late in drying, it moves **out to the edge**.
3. Deposition runs at `1 − e^(−λ(0.4+1.2·age/N))`, so it speeds up as the wash dries. Each cell's deposit is shared among its 2×2 pixels by `exp(G·(0.5 − h))`, which favours valleys. The last tick drops the rest.
4. Paper height is `.65·fine + .35·mid` (cold press scan or plain), half-blurred by 1 px.

## Cost (Chrome, this Mac)
| case | plumes ms/tick | settle ms/tick | ms per wet s | peak tick |
|---|---|---|---|---|
| Bench, 1200×800, L wash + M pen + flick, drift | 1.07 | 1.83 | 29 | 7.0 |
| same, vein | 0.96 | 1.88 | 28 | 5.8 |
| Live demo, ~1700×1050 page, L wash | 2.0 | 2.5 | 45–50 | 15 |
| Phone 390 px, finger wash + pen | 0.65 | 0.71 | 14 | 6 |

- **Manta cadence:** measured 2.7–3.0 redraws/s while wet.
- **Cost bounds:**
  - Settle scales with the wash's area (~4 ns per pixel per tick).
  - Plumes scale with the parcel count, which is capped.
  - Trail diffusion runs on wet 32 px tiles only, every other tick.
- **On the Manta:** expect ~5–10× these numbers, about 150–400 ms per wet second for a big wash, on a background thread.
- **Cheap wins:**
  - 5 TPS instead of 10 (×2).
  - Integer math.
  - Plume stains at half resolution.

## Java hook points (inkwash-v1)
- **`InkWash.stroke` line 83–84:** a pen or blend stroke currently calls `session.dry(...)`.
  - Instead, take `session.wet(document, now, look)` and hand the wet `WashCoverage`s to the ink stroke.
  - Dry only after the plumes have finished, or on undo, erase or a layer change, as today.
- **`WatercolorSession`:**
  - Add per-wash lift time and look (`wetMs`).
  - Own a new `WetLife` (this file's `Water`): the wet-end per tile, the seeps, the parcels, the settling patches.
  - Run it on a `HandlerThread` at 10 Hz. It writes into wash-layer tiles and unions a dirty rect. The UI does a partial GC16 refresh at ≤3 Hz.
- **`WildInkStroke.stamp/dab`:**
  - After each dab, report the new coverage on wet pixels (seen/keep buffers per tile) to `WetLife.touchLine`.
  - Scale the dab's laid level by `keep`.
  - At `finish()`, flush the leftover seep.
  - The `PenWashStroke` from the blend notes does the same for its line.
- **`WatercolorSettle`:**
  - Split the per-pixel `tone(...)` into a darkness `D`. Today it multiplies onto the base immediately.
  - Hand `D` to `WetLife.addWash`, which lays `(1 − f)D` now and settles the rest.
  - At `settle = 0` it is today's pixels.
- **`PaperGrain`:** `fine` and `mid` give the height (bytes, 256/768 tiles).
- **`WetWatercolor`:** this is not needed. Its tile/BitSet layout (16 px) is the model to copy for memory.
- **Undo:**
  - Fast-forward `WetLife` to dry, then undo.
  - The wash, its settling and plumes are one step with the stroke that caused them, using `finishWithPrevious`.

## Dials as BrushSetting / BrushCode
- Append after the B code's DRYING:
  - `PLUME` (0..1, 2 chars),
  - `PLUME_STYLE` (`drift|vein`, 1 digit),
  - `SETTLE` (0..1, 2 chars).
  - That is **5 characters**.
- The page shows the tail, e.g. `…ne0ll.`, after the B code.
- `decode` should accept the tail missing, which means 0, 0, 0 and is today's look.
- `roll()` draws these last.
- `DRYING.shown()` needs the new 20 s span. Old brushes would then dry ~4× longer, so either keep 5 s for brushes with plume = settle = 0, or accept the change.
- Pen and blend read PLUME; wash and blend read SETTLE.

## Risks
- **Taste.**
  - Vein at high Plumes can read as tufts or "legs" near the line. That is the hairy look Elling disliked.
  - Drift is the safe default.
  - Lines drawn late into half-dry paint bleed less on purpose, to avoid dark tufts.
- **The wash darkens as it dries.** Real watercolor lightens, but e-ink can't. At Settle 1, 45% of the pigment arrives during drying.
- **Line lighter at wet crossings** is laid that way from the first stamp, which is fine for e-ink. The live preview of the wash is shown at `(1 − f)` so the lift never lightens.
- **Timing is part of the stroke.** The same strokes at other ticks give other plumes. Replays and share codes reproduce the brush, not the exact bleed, unless sample ticks are stored.
- **JS/Java float drift** in the currents (trig, bilinear) and the flux sums. A bit-exact port needs LUTs and fixed point, checked like `BrushCodeChecks`.
- **Cost on big washes.** The settle is area-bound, so cap the patch size or tick it at 5 Hz on device.
- **A wet page holds refresh** for up to the drying time. Same trade as Living Ink.
