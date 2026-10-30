# Pen Paint Blend: notes

Page: `pen-paint-blend.html`. It is built by `node build-blend.js` from `src/` (engine.js is copied from weblab without changes, plus blend.js and ui-blend.js). Screenshots: `shot-desktop.png`, `shot-phone-390.png`, `shot-stroke.png` (real mouse strokes), `shot-strip-outline.png`, `shot-strip-mixing.png`, `shot-offset-chaos.png`, `shot-bench.png`, and `placements.png` (all five placements). Headless Chrome at 1280 px and at 390 px (3x, touch): no console errors, no horizontal scroll.

## Recommendation: A, Line + wash
- **How it works:** one brush with a line part (WildInkStroke) and a wash part (the Wild paint settle), fed the same samples.
- **Wet** sets how the line splits:
  - the share that stays crisp on the ink layer is `mix(1, line, smooth(wet))`;
  - the share that loosens into the wash is `4w(1-w)*1.2 + w*line`, then diffuses and feathers.
- **The wash body** appears from Wet .08. It grows from a halo about 2.2× the nib to the paint size, and its shape relaxes from the nib's ellipse and angle to round. It takes the nib's tapers too, fading out by Wet 1.
- **Wet 0 is today's pen and Wet 1 (Line 0) is today's paint, bit for bit.** The page checks this on load: 16 of 16 random old codes give identical pixels.
- **Why A:**
  - Old brushes don't change.
  - It reuses both tested engines.
  - It maps onto the layer pair the app already has: line on the ink layer, wash under it on the wash layer. That is classic pen-and-wash.
  - The line stays crisp, so it reads on e-ink.
- **B (Wet nib):** one nib whose water footprint swells with Wet. Pigment runs inside the water and gathers at its edge.
  - It looks good as a "wet line", but it is one fat mark rather than line over wash.
  - Wet 1 is not today's paint, so the old WashStroke would have to stay beside it, which makes a seam near Wet 1.
  - It costs about the same as A.
  - It could come back later as a style inside A: a small, tight wash body plus a strong edge.

## Cost (Chrome on this Mac; long loop, size L, every effect high, 1400×900; ms)
| | per sample | settle after lift | settle × today's paint |
|---|---|---|---|
| Pen today | 0.019 | 1.0 | 0.03 |
| Paint today | 0.152 | 34.0 | 1.00 |
| A Wet .25 / .5 / .75 / 1 (Line .5) | 0.030 / 0.054 / 0.109 / 0.165 | 36.5 / 40.7 / 47.5 / 34.7 | 1.07 / 1.20 / **1.40** / 1.02 |
| B Wet .25 / .5 / .75 / 1 | 0.016 / 0.037 / 0.076 / 0.154 | 30.0 / 33.9 / 38.2 / 46.5 | 0.88 / 1.00 / 1.12 / 1.37 |

- **Per sample:** always at or under paint plus 10%.
- **After lift:** the worst case is A at about Wet .75, 1.4× today's paint settle. Scaled to today's 61 ms paint settle on the Mac, that is about 85 ms.
- **What causes it:** the extra pass is the line-diffusion blur. It could run at half resolution, or join the feather blur, to get back to about 1.1×.
- **Not counted:** the page stamps the whole stroke again at lift. The app re-lays only the tail, so that column is not used.

## Lines: placements inside the wash (round 2, after "two tools overlaid")
- **What changed:** the line's geometry now comes from the wash body. At every stamp step the body's ellipse is worked out there: its size from pressure, Wet and tapers; its nib angle and shape. The ink is placed inside it, so the lines widen, taper and turn with the wash.
- **Placements:**
  - **Centre:** today's line, kept for comparison. It runs through `stampInk`, so it is bit-identical.
  - **Spread:** 2–5 lines across the body's long (nib) axis. A chisel at an angle staggers them. They converge as the body narrows.
  - **Outline:** two lines on the body's edges either side of the path. They are softened and broken by low noise where the wash is thin, and by more as Wet rises. At Wet 0 they are a clean double contour.
  - **Squiggle:** one line, `offset = halfWidth × Reach × sin(2π d/λ + seed phase + 0.9 sin(…))`, with λ = max(30, 3 × body radius). It is deterministic from the seed.
  - **Split:** the dip-pen tines. The gap is `spread × breath(p)`, with wet ink in the gap as Wet rises, and dry brush frays the rails.
- **Breathe (0–1):** `mix(radius at p=.7, radius(p), breathe)`. At 1 the lines follow pressure: a light touch converges them, pressing spreads them. At 0 the spacing is fixed. For Split it scales the tine gap. The count never changes.
- **Ink and wash meet:**
  - The line's loose share (`lineWet`) bleeds into the wash, as before.
  - The wash also gathers darker along the lines: `W += .3 × bodyAmt × L` (additive, after the max). Both terms are 0 at Wet 0 and at Wet 1 / Line 0, so the identity check still passes, 16/16.
- **Randomize:** about 15% of rolls get a placement other than Centre, spread evenly over the other four. These are drawn last, so old seeds keep their other dials.
- **B** (wet nib) draws Centre only.
- **Cost** (same bench, ms per sample / settle ms):
  - spread ×5: 0.052 / 0.9 at Wet 0, and 0.149 / 47.6 at Wet .75
  - outline: 0.029 / 1.0 and 0.121 / 46.6
  - squiggle: 0.035 / 0.9 and 0.125 / 47.9
  - split: 0.051 / 0.9 and 0.145 / 46.8
  - Compared with Centre (0.018 at Wet 0, 0.108 at Wet .75), that is at most +0.04 ms a sample. Settle cost doesn't change, since the lines are just more ink in the same buffers.
- **BrushCode:** four settings appended after `drying` in B codes: `lines` (a choice: centre, spread, outline, squiggle, split), `count` (dial 1–5, rounded), `spread` (shown as "Reach", 0–1) and `breathe` (0–1). That is 7 more characters. They are written only when the placement isn't Centre, and a B code that ends at `drying` reads as Centre. This is also the pattern for adding settings to a code version later. Old I and P codes are unaffected.
- **Java:**
  - `WildBrush` gets `lines` (an enum `Placement`), `count`, `spread` and `breathe`.
  - `BrushSetting` gets LINES, COUNT, SPREAD and BREATHE, appended to the BLEND list. `BrushCode` reads them as an optional tail.
  - **WildInkStroke.dab:** today's per-step nib stamp. It becomes a loop over placement offsets. A new `Placement.offsets(step, bodyRadius, bodyAspect, angle, dir, d, pressure)` returns up to 5 `(dx, dy, widthScale, ink)` tuples, and each goes through the existing ellipse/dry-brush/tooth row fill. Centre returns one `(0, 0, 1, 1)`, so the code path is today's.
  - The body radius comes from `WashLook`'s paint width and taper, the same formula WashStroke uses (`InkWashSizes.maxSize` × tip/pressure curve). Expose it as `WashLook.radius(pressure, distance, total)`.
  - **Split:** a per-pixel gap mask in the row fill (`|across| < .72 g`). The gap's wet share is written to the wash coverage PenWashStroke holds.
  - **Squiggle:** the stamp spacing shrinks by the sideways swing factor so the line stays unbroken.
  - **Tail at lift:** WildInkStroke's tail re-lay must also re-lay the placements, since Outline, Spread and Squiggle depend on the body's taper out.
  - **Captions:** "spread lines", "outlined", "squiggle", "split nib".

## Offset, Chaos and mixing (round 3, after "lock step… still distinct ideas")
- **Offset (0–1)**
  - A slow, seeded 1-D value-noise drift of the line(s) across the path. Its amplitude, `offset × (1.6 × wash half-width + 2.5 × nib)`, reaches past the wash edge.
  - **Lag:** the seed picks, at each end, whether the line or the wash skips its first or last `offset × (14 + .55 × body size) × (.4…1)` px. So the line starts or ends before or after its wash.
  - The end lag needs the stroke's length, so it is laid at lift, like the taper out.
- **Chaos (0–1)**
  - Per line, on seeded noise rather than sines: width ×(1 ± .9c), ink −.45c, a lateral jitter of `c × (.55 half-width + nib)`, and Spread spacing ×(1 ± .8c).
  - Breaks: a dab is skipped where the noise is under `.34c`, so lines break and restart.
  - Squiggle: its phase is pushed by noise and its swing comes and goes, so the meander is irregular.
- **Mixing:** it runs at settle, only where a line meets a wash body, so it never touches Wet 0 or Wet 1 / Line 0. The identity check is still 16/16.
  1. **The line loosens in wet wash.** Each line pixel inside the body loses `.72 × soft` of its ink, where `soft = body coverage × smooth(1.5w) × low-noise mask`. So the line goes greyer where the wash is heavy, dissolving in and out along the stroke. The lost ink goes into the wash, and the line also spills a small halo of fuzz. Line pixels outside the wash stay crisp.
  2. **The wash pulls ink out of the line.** The line is blurred at half resolution, `radius 3 + 9w`, then thresholded against the low and mid noise. That makes feathers that branch into the wash, stronger with Wet.
  3. **The edge pools at crossings.** Where the line crosses the wash body's rim (body minus its ±3 px neighbours), the rim gains `3.2 × rim × line nearby`.
  - Visiting line pixels only, plus a sparse half-resolution field, keeps mixing at about +10–15 ms over the wash settle.
- **Randomize:** Offset `maybe(.3) × .9` and Chaos `maybe(.25) × .8`, drawn last.
- **Strip:** a second strip shows this brush at Wet clamp(.3–.7), Offset 0 → 1 and Chaos 0 → 1.
- **Cost (ms per sample / settle ms; paint today 0.149 / 35.3)**
  - Centre, Wet .5 / .75: 0.053 / 47.5 and 0.111 / 51.4, which is 1.35–1.45× paint
  - Spread ×5, Wet .75: 0.155 / 67.6, 1.9×
  - Outline / Squiggle / Split, Wet .75: 0.12–0.15 / 61–65
  - Centre, Offset 1, Chaos 1, Wet .75: 0.119 / 65.7
  - Spread ×5, Offset 1, Chaos 1: 0.179 / 72.5, 2.05×
  - Scaled to the 61 ms paint settle, that is about 85–125 ms on the Mac. Many lines in heavy wash is the expensive case.
- **BrushCode:** `offset` and `chaos` are appended to the B tail after `breathe`, 4 more characters. The tail is written when the placement isn't Centre, or Offset or Chaos is above 0. Decoding reads as many whole tail settings as the code holds, so codes from the last round still load.
- **Java:**
  - `WildBrush` gets `offset` and `chaos`. Both are deterministic from the seed through a new `StrokeNoise.value(seed, channel, x)`: a hash plus a smoothstep lerp, so the desktop and the device match.
  - **WildInkStroke.dab:** the offsets from `Placement` gain drift, chaos jitter, width and ink scales, and a skip flag for breaks. The lag is a distance window checked at the top of `dab`.
  - **WashStroke:** gets the same window, `skipStart` and `skipEnd`, for the wash lag.
  - **PenWashStroke.finish:** keeps the line and body coverage, which `WashCoverage` already holds, and runs `mix()` before `WatercolorSettle`. That means:
    - a sparse pass over the line's tiles: loosen, spill, and move ink to the wash;
    - a half-resolution blur of the line, for the feathers;
    - the rim test, done as four neighbour reads.
  - Each step works on `WashCoverage` tiles, so it fits the settle's tiling and threads.

## Java changes (branch inkwash-v1)
- **WildBrush:**
  - Add `wet` and `line`.
  - Merge the pairs into one field each: `grain` (tooth + gran), `bristle` (dry + streaks), `pool` (pool + hollow), `bleed` (= feather).
  - `Kind` stops being stored. Derive it: `INK` when wet == 0, `PAINT` when wet == 1 and line == 0, otherwise `BLEND`.
  - `roll()` is unchanged for old seeds. Add `rollBlend` for the continuous Wild.
  - `normalize()`: a flat body drops bloom and backrun, not pool.
- **BrushSetting:** add WET, LINE and GRAIN/BRISTLE as named merged settings, plus a third list `BLEND` in the B-code order (below). Keep `INK` and `PAINT` for decoding.
- **BrushCode:** accept `[IPB]`.
  - B: `slug.B<style base-36 char><dials>.<seed36>`, with 21 settings: nib, mode, texture, wet, line, aspect, angle, taperIn, taperOut, pressure, speed, grain, bristle, wobble, pool, splatter, soft, bleed, bloom, backrun, drying.
  - I and P decode as before, then map to Wet 0 / Line 1 and Wet 1 / Line 0. Each merged pair has exactly one member per old kind, so nothing is lost.
  - Encode always writes B. Stored slots convert the next time they are written.
- **New PenWashStroke (DrawingStroke):**
  - It owns a WildInkStroke-style line coverage and a WashStroke-style wash coverage.
  - The ink layer is live, as today's pen is. The wash coverage is previewed on screen the way EraseBothStroke previews, and is settled onto the wash layer at lift.
  - The two layers are one undo step through `finishWithPrevious`, which EraseBothStroke already uses.
- **WashLook / WatercolorSettle:**
  - `of(brush)` reads the effective dials (bleed × s, pool × s and so on).
  - Add a nib-ellipse stamp (aspect, angle, tapers) and an optional pre-feather diffusion pass. Both are skipped when round, untapered and Wet 1, so today's pixels are unchanged.
- **WildInkStroke:** takes a darkness scale (inkShare) and exposes its coverage, so PenWashStroke can copy the loose share into the wash.
- **InkWash.stroke:** routes Wet 0 to WildInkStroke and Wet 1 with Line 0 to WashStroke (fast paths that are exactly today's code). Everything in between goes to PenWashStroke.
- **Brush:**
  - `inks()` / `wet()` come from wet.
  - `wet()` is true when wet > 0, so the brush joins WatercolorSession.
  - Sizes: the line uses `inkSize`, the wash uses `maxSize`.
  - Shades: `InkWashShades.ink` and `.watercolor`.
- **BrushStyle:** one list (below). Each style gets a wet and line range.
- **BrushCaptions / BrushVariations:** use the merged dials, add "wet" and "more line" captions, and let variations push Wet.
- **BuiltinBrush:** the approximations get wet = 0 or 1.
- **ToolSlots:** default slots unchanged. Migration happens on read.

## Kind, styles, the lab's chips
- **Kind** is no longer a switch. It is a region of Wet, used only for layer, size and shade routing.
- **Styles become one list, ordered pen to paint.** This order is fixed only once B codes ship:
  Fineliner, Brush pen, Dip pen, Marker, Pencil, Charcoal, **Pen and wash** (wet .3–.65, line .7–1), **Wet line** (wet .15–.4, line 0–.4, bleed .3–.7), Ink wash, Watercolor, Gouache, Oils, Pastels, Dry brush, Wild.
  - Old pen styles are Wet 0. Old paint styles are Wet 1 / Line 0.
  - Old I and P style indexes map into this list.
- **Lab chips:**
  - There is one chip row in that order, so the two new chips sit between the pens and the paints.
  - Wild rolls across everything: about 20% pens, 20% paints, the rest anywhere between.
  - The lab gets the big Wet dial at the top and Line under it. Dials that do nothing at the current Wet are greyed out, as round-nib angle is today.

## Risks
- **Settle budget:** with mixing, the settle is 1.35–2× today's paint (Spread ×5 in heavy wash is the worst case). Measure on the Manta, and consider capping lines at 3 when Wet > .6.
- **Offset and Chaos at lift:** both change where ink lands near the stroke's end once the length is known, so the end visibly shifts at lift. This is the same as taper out today.
- **Placements:** offsets read the body's radius, so a body taper out changes where lines sit at the end of the stroke, and the lines visibly jump at lift. Re-laying the tail covers this, as for the pen's taper today. Outline at very low Reach and Wet reads like Split.
- **Merged pairs:** Pool means two looks (slow-point blobs vs a hollow rim). If Elling wants them independent, they split into two dials. That is still lossless.
- **Settle cost:** up to about 1.4× today's on the Manta before any optimisation. Measure on the device.
- **Two layers:**
  - The wash is under the ink. Erasing and the wet session work per layer, so a blend stroke dries other wet paint only if it changes the page, as today.
  - The wash preview while drawing is an overlay, not the layer itself.
- **Flat mode:** Paint flat at high Wet is gouache-like, not wet. The name "Wet" is slightly off there.
- **Textures** are not simulated in this page. In the app the one texture feeds both grain uses.
- **Web vs app:** the web settle is the roll-tester port, not WatercolorSettle. Tune the look again on the Manta.
- **B codes and old builds:** an older app build cannot read B codes. Sharing with an old build fails, but it fails safely: decode returns null.
