# Brush experiments: notes

Prototype: `brush-experiments.html` (one file; Google Fonts is its only external resource). Sources are in `src/`: `engine.js` is an unchanged copy of the web port, `exp.js` holds the proposed engine changes and `ui-x.js` the page. Build with `node build.js`. `node eqtest.js` checks that the new stamps match the port exactly with every new field off. `node bench-node.js [tabs]` prints the timings (saved in `bench.txt`, and `bench-big.txt` for tabs 1 and 6).

## Share codes (applies to all six experiments)

`BrushCode` writes `name.K S DIALS.SEED`: K is I or P, S is a single-digit style index, and the dials follow `BrushSetting.of(kind)` order. `decode` rejects a code unless it reads exactly every dial (`at == digits.length()`).

- **Append** new dials at the end of each kind's list. Never reorder.
  - Ink: `SPREAD, TILT, FAST`
  - Paint: `COVER, IMPASTO, HEAD, TILT, FAST`
- **decode:** accept a code that stops exactly after the old (v1) dials, and default the new ones to off. Old codes keep reading.
- **encode:** leave the new dials out when they are all off. Today's brushes then keep byte-identical codes, keys and `WashLook` caches, and older app builds can still read them. A code that uses a new dial will be rejected by an older build (it returns null): that's acceptable, but say so in the release notes.
- **New style:** paint style `Hake` goes at index 7, after Wild. The index stays a single digit, so the regex holds up to 9. `BrushStyle.wild(kind)` currently returns "last in list", so it must look up Wild by name or index instead. The style chips can still show Hake before Wild.
- **WildBrush.roll:** don't draw the new dials from the seed. If a roll ever does, the draws go last, so old seeds keep their character.

## 1. Charcoal: bigger, messier, softer

Elling's follow-up: charcoal should be "quite messy and more fuzzy", but crisp lines must stay possible. The references are willow and vine charcoal (soft, dusty), plus compressed charcoal (darker, still dusty).

- **Settings**
  - Per-style sizes: `BrushStyle.sizes = {S, M, L}`. Charcoal is 6 / 30 / 120 px (today it uses the pen sizes, 3 / 8 / 16). Other styles keep `InkWashSizes`.
  - New pen dial `SPREAD` (0–1), from crisp (0, today's look) to messy (1). Every effect below scales with it:
    - **Fuzzy fringe:** spread × (1.4 × short half-width + 0.12 × long half-width).
    - **Broken edge:** the edge wanders by the paper's 6 px and 44 px noise, ±1.1 and ±1.8 × spread × fringe.
    - **Uneven deposit:** coverage × (1 + 1.2·spread·(low − ½) + 0.5·spread·(mid − ½)). These are the patches where the stick skips.
    - **Dust:** up to 24 specks per dab, 0.5–2 px across, scattered up to 2.3× the radius. They're seeded by distance along the stroke, so the lift re-lay draws the same ones.
    - **Smudge:** level = max(after tooth, coverage × 0.5 × spread), a faint grey that fades out instead of stopping.
    - **Pressure:** darkens more than it widens: coverage × lerp(1, 0.3 + 0.7p², spread).
    - **Tooth contrast:** 4 − 2.4 × spread, so the grain reads as greys.
  - Ranges: Elling's v3 note asks for "a slight fuzz most of the time… but sometimes crisp". Charcoal's roll is a three-way pick: 70% in 0.15–0.45 (slight fuzz), 15% in 0–0.1 (crisp) and 15% in 0.6–0.9 (heavy and messy). `BrushStyle.Range` needs a weighted band for this (or the roll can pick a band first). Pencil 0–0.2, others off.
  - At about 0.3 the fringe is narrow, specks are few (n ≈ 0.3 × (1.5 + 0.08a)) and the deposit varies gently (±0.18). The 0.95 look was too blotchy for a default; it stays reachable on the slider.
  - Presets in the page:
    - Willow stick: spread .3.
    - Compressed: spread .2, darker, little dry.
    - Vine point: spread .35.
    - Crisp charcoal: spread 0.
    - The Big-sizes charcoal is .3 too.
    - A Spread slider dials any of them.
- **Paper-agnostic** (Elling: "it seems to be relying on the crumpled texture… it should be paper agnostic"):
  - Charcoal's fuzz, edge wander, skips, dry gaps and dust come from **its own grain**: `stickGrain(seed)` is a seeded, tileable 512 px value noise at 1.6, 7 and 36 px, cached per seed.
  - The paper adds only its **Paper tooth** share: grain = lerp(stick, paper, 0.3 × tooth), with a grain amplitude of at least 0.6.
  - No charcoal preset uses crumpled any more (Willow and Vine are on cold press, Crisp on plain).
  - `charcoal-papers.png` shows the same stroke on plain, cold press, laid and crumpled; it reads the same on all four. The page has a **Paper check** button.
  - Java: a `StickGrain` tile per seed (3 × 512² bytes, ~0.8 MB, built once per brush), read in `WildInkStroke` instead of `PaperGrain` when spread > 0.
- **Java:** `BrushStyle` (sizes, range), `BrushSetting`, `WildBrush`, `InkWashSizes` (`inkSize(preset, style)`; callers in `WildInkStroke`, `InkWashBar` size dots and `WildSample.pad`), `WildInkStroke` (fringe, broken edge, deposit, dust, reach, smudge and contrast in `level()`), `BrushCode`.
- **Style vs dial:** sizes come from the style, not a dial. A Wild brush that copies Charcoal's dials stays pen-sized. If that matters, use a `SCALE` dial instead.
- **Risk: cost per dab grows with r².** Today every sample lays at least one dab, so a 120 px nib sampled every 3 px is stamped about 40 times over. The prototype carries dab spacing across samples for styles with their own sizes, which cuts the test stroke from one dab per sample (368) to 90. With that, the messy Willow stick at L (120 px) costs 351 µs per sample (506 µs p95), against 12 µs for today's 16 px charcoal. Crisp charcoal at L costs 147 µs. The fringe and dust are the extra cost: soft coverage never reaches 1, so today's early skip of full pixels never fires. Port that spacing too.
- **Risk: lift cost.** The tail is re-laid within the widest reach, which is now up to about 75 px, so lift costs more: 0.2 ms before, up to 9 ms for Vine point at L.

## 2. Cover: opaque paint

- **How the app composites today**
  - Each layer stores tone plus alpha. Layers combine source-over (`compositeTone`).
  - Wild paint shows a plain preview while the pen is down: `WashStroke.lay` uses `preview()` or `min(gray, base)`.
  - At lift, `WatercolorSettle` multiplies over the base tones of the active layer only (`strokeBaseTones`), and `setTones` writes them.
  - `setTones` treats grey as pigment on transparent paper: alpha becomes max(a, 255 − gray). A light grey written on an empty upper layer is stored as black at low alpha. So paint can only darken, even across layers.
- **Proposed:** a paint dial `COVER` (0–1).
  - tone = lerp(glaze, lerp(base, shade, coverage), cover), where glaze = base × (1 − d × deep).
  - Defaults, per Elling ("more opaque by default"): Gouache and Oils 1 (they randomize within 0.9–1), Pastels 0.9 (range 0.75–1), Dry brush 0–0.5, Watercolor and Ink wash 0.
  - The Cover tab opens on After, with Light painted over the black stripe.
  - **Opaque paint looks like body colour.**
    - Wet effects fade out as cover rises: feather, hollow rim, bloom and backrun are each × (1 − cover). There are no blooms and no soft wet halos.
    - The hardest edge drops from 8% to 1.2% of the radius at cover 1, so gouache can be crisp.
    - Gouache (soft 0.03, grain 0.04, no streaks) is flat and matte.
  - **New paint dial `IMPASTO`** (0–1; Oils 0.6–1, others 0):
    - At lift, the settled coverage is lit from the upper left. Bristle-streak ridges get ±100 × impasto tones (light on one side, shade on the other), and paint piled along the edges (coverage minus its 3 px blur) reads up to 90 × impasto darker.
    - Impasto paint covers fully where the bristles dragged thin: alpha = d / lerp(1, 0.4, impasto). The streaks show as relief, not as gaps.
    - Cost: one more blur over the stroke at lift. Oils at L lift went from 11 ms to 16 ms.
    - **Light direction fixed** (from the media research): the relief was `D[j−rw−1] − D[j+rw+1]`, which lit it from the lower right, so ridges could read as grooves. It is now `D[j+rw+1] − D[j−rw−1]`, light at the upper left.
    - The relief is kept as an offset apart from the tone and cleared only when the stroke ends, so composing a pixel again never changes it.
    - **WYSIWYG:** in Fast-wet the impasto is shaded live, in the trailing window, from the paint as it lies now (same light and same edge term). In Drying it is still shaded at lift; in plain Fast there is none.
    - In Java this goes in `WatercolorSettle` (a relief pass) and `WashLook` (impasto in the key).
  - Pastels keep thinner cover in their grain, so the paper's dark shows through, as dry pigment does.
- **Java:** `BrushSetting`, `WildBrush`, `BrushStyle`, `WashLook` (cover; include it in the key), `WashStroke.lay` (preview mixes instead of `min`), `WatercolorSettle.settle` (mix instead of multiply), `ToneDocument` (a block writer for opaque paint that sets tone and alpha, as `paintTone` / `setPixel(gray, coverage)` does), `BrushCode`.
- **Layers:** opaque paint must write alpha = max(a, coverage × 255) with tone = shade. Otherwise it can't hide lower layers. That is the physical behaviour: paint on top hides what's under it. Masking fluid already goes through `setPixel`'s shield blend.
- **Undo:** no change. Tiles are captured before any write, whatever the operation. `restoreSpan` and `WildInkStroke`'s rule that ink never gets lighter only apply to ink.
- **Risks**
  - Wet join re-settles joined washes over their recorded bases (`recordBases`). Opaque mixing must use those same bases, never the current page, or it compounds.
  - Groove pooling should scale by (1 − cover).
  - "Joined paint never darkens itself" still holds, because coverage keeps the stronger level.

## 3. Big brush: Hake (wide flat)

- **Settings**
  - New paint choice `HEAD`: round or flat.
  - New paint style `Hake`, 60 / 180 / 360 px across (today's largest is a 220 px round).
  - Hake ranges: wet or flat, pressure 0.1–0.4, soft 0.1–0.4, streaks 0.2–0.6, cover 0–0.9.
- **How the flat head draws**
  - The contact is wide and thin, with a half-depth of 3–8% of the size. Pressure deepens the contact, and width only grows 80→100%.
  - The head is held square to the smoothed travel direction (about 12 px to turn), or across the lean when Tilt is on.
  - Paint runs out along the stroke: streaks rise to +70% by 2400 px.
- **Fixed on turns (v4):** Elling's U-turn showed spokes and wedges at both ends and at the turn, gaps between stamps, and streaks aliasing into concentric bands. The fix:
  - **A swept ribbon:** heads are placed every max(1.5, min(Q, 2 + 0.03W)) px. Between two heads the quad joining their ends is filled through its inverse bilinear map, so each pixel gets its bristle lane s (across the head) and t (between heads). The head itself (W × Q) is also laid, for the square-cut ends and for cover when the stroke runs along the head. No gaps, no spokes.
  - **Streaks in lanes:** the hair amount is read by lane s, so the streaks curve round turns, bunching inside and spreading outside, with no banding.
  - **Head angle, a hand's:**
    - **Follow (lagged)**, the default, eases toward square-to-the-stroke over about 1.2 widths of travel (minimum 40 px) and at most 1° per px. A U-turn goes thin, then broad, like a broad nib.
    - **Fixed** holds `angle` (Hake presets 75°) like a chisel.
    - Tilt turns either across the lean.
    - Follow is the better default: it keeps a broad mark in every direction and still turns like a real brush.
  - **Tight turns pivot (v5):** the v4 U-turn still bulged a teardrop into the inner side, because the lagged head was mid-turn coming out of it. Now:
    - Where the turn radius is under 1.5 widths, the head turns with the stroke (fully under 0.75 widths). Only gentle bends lag.
    - If a turn would still run one end of the head backwards (radius under the half-width, the offset-curve cusp), that end is **pinned** where it was and the head swings round it, as a flat brush pivots on its inner corner. The outer end sweeps the arc, and the lanes by the pinned end compress toward the pivot.
    - A fast-swinging head gets in-between heads, so its outer end never moves more than 6 px per step (no stepped edge, no spokes).
    - The contact thins over the head's last few percent (depth × (1 − |u/W|⁸)), so the corners don't poke teeth past the outer arc.
    - Results: the U-turn at M is a clean U with a pivot point inside. Zigzag corners mitre, and loops are smooth with concentric lanes. At 360 these tight test paths just overlap themselves (radius about 40 px against a 180 px half-width), with no loops, spokes or teeth. Cost at 360 is about 240–420 µs per sample with the in-between heads.
  - **Ends:** the first head waits until the stroke has gone max(6 px, 5% of the size) and takes that chord's direction, so there is never a head at an unknown angle.
  - **Tests:** `hake-uturn-before.png` (v3's stamps, `#oldhake`), `hake-uturn-after.png` (v5) and `hake-uturn-L-compare.png` (v3 vs v4 at L). `hake-matrix.png` has U-turn, zigzag and loop at M 180 (top) and 360 (bottom); Before is today's round brush. The test paths are snapped to a 1.5 px grid, like mouse input; a smooth path hides the jitter bug.
  - **Cost:** at M about 80–115 µs per sample, vs 85–100 for the stamps. At L 140–210 µs, vs ~250.
  - **Size-scaling exemptions:** the Hake takes none of the pen scaling (taper, wobble, pool, splatter, bristle count). It keeps the paint ones (spacing carry, bloom, bleed, rim scaling), which only matter for wet hakes.
  - **Java:** a `FlatRibbon` footprint in `WashStroke` (head state: angle and last head), replacing `RoundFootprint` for flat heads.
- **Java:** `BrushSetting`, `WildBrush`, `BrushStyle` (append Hake; fix `wild()`), `InkWashSizes` (`maxSize(preset, style)`), `WashStroke` (a new `FlatFootprint` beside `RoundFootprint`, plus spacing), `WashLook` (streak strength by distance), `BrushCode`, Lab style chips.
- **Risks**
  - The non-Fast settle and feather scale with area. A 360 px hake wash takes 24–38 ms at lift in JS, and much more on the device's full `WatercolorSettle`.
  - A fast turn can gap the swept rectangle unless spacing stays at or below the contact depth.

## 4. Tilt

- **Today:** Wild pens ignore tilt (`WildInkStroke.sample`). Wild paint already widens with lean, but only when pressing (`WashLook.diameter`).
- **Proposed:** a dial `TILT` (0–1) on both kinds.
  - Ranges: Brush pen, Dip pen, Marker and Charcoal 0.6–1; Fineliner and Pencil 0–0.3; paints 0.5–1.
- **Pens**
  - Width × (1 + 1.2 × tilt × lean), using `BrushDirection.lean`, which is full at 65°.
  - The nib angle turns toward (lean direction + 90°) by the tilt share, as in `BrushDirection.resolve`.
  - A round nib flattens to 1 − 0.45 × tilt × lean.
- **Paints**
  - The round head becomes an oval across the lean: up to 1.6× long and 0.65× short.
  - The flat head turns across the lean.
- **Java:** `BrushSetting`, `WildBrush`, `BrushStyle`, `WildInkStroke` (store tiltX/tiltY per sample next to `ps`; per-dab angle, width and aspect), `WashStroke` / `WashLook.diameter` (an elliptical footprint), `BrushCode`. Reuse `BrushDirection`.
- **Nomad:** `PenSample.read` already turns the landscape-frame tilt into the page frame (tiltX = −tiltY, tiltY = panelX), and `BrushTestPad` passes `landscapeTilt`. No extra mapping is needed.
- **Risks**
  - The azimuth is unreliable near upright. Below 5° keep the last heading, as `BrushDirection` does.
  - The rotation needs smoothing, or the dab angle jitters.
  - The oval paint footprint costs about 2× today's round, because it loops over a bounding box rather than spans (673 vs 356 µs at L). Rasterise it per row.

## 5. Fast (WYSIWYG)

- **Flag:** `FAST`, a choice (off / on) appended to both kinds. When on, nothing changes after lift:
  - Ink: no tail re-lay in `WildInkStroke.finish`. The taper comes live from pressure falling below 70% of the stroke's running peak and from a flick (speed above 1.2 px/ms). Splatter is thrown live, each segment seeded by its own index.
  - Paint: flat only. Streaks and granulation go in per dab, and the pen writes final tones, not the preview.
  - Off entirely: `streak()`, feather, hollow, bloom, backrun, the settle, wet join and drying (`WatercolorSession.remember`), and groove pooling.
- **Fast set:**
  - Fast fineliner: round, pressure 0.15, taper out 0.3.
  - Fast brush pen: pressure 0.85, taper in 0.4, taper out 0.8, speed 0.3.
  - Fast pencil: tooth 0.75, dry 0.15, cold press.
  - Fast gouache: flat, cover 1, soft 0.05, grain 0.05.
  - Fast marker wash: flat, cover 0, soft 0.3, grain 0.1.
  - Ship them as built-ins (`BuiltinBrush` / `ToolLibrary`).
- **Java:** `BrushSetting`, `WildBrush`, `WildInkStroke` (live taper and splatter; `finish` lays nothing), `WashStroke` (a Fast path that skips the preview and the settle), `BuiltinBrush`, `BrushCode`.
- **Cost** (median, M / L; `bench.txt`, Node V8, page buffer only, not the screen; compare the columns, not the absolute numbers):

| preset | µs/sample before → after | lift ms before → after | px changed at lift |
|---|---|---|---|
| Fast fineliner | 6 → 6 / 9 → 9 | 0.2 → 0 | 1% → 0% |
| Fast brush pen | 8 → 7 / 11 → 10 | 0.3 → 0 | 2% → 0% |
| Fast pencil | 9 → 8 / 13 → 13 | 0.2 → 0 | 1% → 0% |
| Fast gouache | 116 → 129 / 339 → 421 | 6.8–11.6 → 0 | 97% → 0% |
| Fast marker wash | 58 → 112 / 180 → 381 | 6.5–10.9 → 0 | 95% → 0% |

  Fast paint costs up to about 2× more per sample, because granulation and final tones are computed live. In exchange, lift is free and nothing redraws.
- **Risks**
  - Pressure-led taper needs a pen. With a mouse or finger only the flick taper works.
  - A spacing carry can leave the very end up to one step (10% of the nib) short.
  - Fast strokes still bump the page revision, which dries other wet paint. That is fine.

## 5b. Fast-wet: a trailing wet window

Elling asked to make everything "as WYSIWYG as possible". Proposal: **Fast-wet is every style's default life**, and today's post-lift Drying becomes an opt-in. `FAST` becomes a choice `LIFE`: fast | wet | drying, with a dial `WET WINDOW` of 0–10 mm (default 6).

- **The window**
  - Length: L = mm × 12 px (Manta, 300 dpi) + half the brush. At 6 mm that is 76 px for an 8 px pen and 132 px for a 120 px wash.
  - The window is measured along the stroke. Every pixel behind it is final, and at lift nothing is laid.
- **Pens**
  - Dabs that leave the window are laid once, untapered, into a frozen buffer.
  - Each sample, the window region starts from that buffer, and the window's dabs are laid again with the tail taper as the pen is now:
    - amount = taperOut × max(easing off, slowing, flicking);
    - easing off: pressure below 75% of the peak, full at 30%;
    - slowing: below 0.3 px/ms;
    - flicking: above 1.2 px/ms.
  - So the taper has already resolved by the time the pen lifts, with no re-lay at lift. Pooling swells live.
- **Paints**
  - Raw coverage is laid once per dab, and each pixel remembers its dab's distance along the stroke.
  - Each sample, the window's pixels are settled again with age = (distance behind the nib) / L. The darker wet edge (hollow), blooms, backruns, granulation and bleed into bare paper all grow with age. Impasto is shaded here too.
  - A bled pixel joins the window, so it freezes L later.
- **Settled once per frame (v5):** the window is settled every 330 ms of pen time (the Manta's ~3 e-ink frames a second), or every 30% of the window's length of travel, whichever comes first.
  - Between settles, fresh dabs show as laid (age 0).
  - Effects are fully developed by 70% of the window, so no pixel leaves the window unsettled. Time alone wasn't enough: a quick stroke covers more than the 132 px window in 330 ms, which left flat patches.
  - Wash cost dropped from about 1.2 ms per sample to 0.22–0.27 ms (p95 1–1.6 ms, on the samples that settle).
- **The page:** tab 5 runs every preset three ways (Drying, Fast, Fast-wet). Sample draws all three for the same stroke.
  - Only the life differs between the rows. Cover, impasto and the shade are the brush's own; before v5, the Drying row used today's engine, so opaque oils showed there as a transparent grey.
  - Opaque presets switch the shade from Black to Mid, so cover and impasto read.
  - `tonecheck.txt` gives each preset's mean grey for the three lives. Fast-wet is within 2 greys of Drying for every preset. Fast is within 16, about one e-ink step: it is lighter on tapered pens and darker on watercolor, which has no hollow centre.
  - Fast-wet presets: Wet brush pen, Wet dip pen, Wet watercolor (hollow, bloom, backrun, grain), Wet ink wash (soft, bleed) and Wet oils (impasto).
  - All five Fast presets also run as Fast-wet.
- **Measured** (`bench-fastwet.txt`, Node, M, median of 5; box = the bounding box of pixels changed per sample, i.e. the e-ink update region; reach = farthest changed pixel from the nib):

| preset | µs/sample D / F / FW | changed at lift D / F / FW | FW box max (avg) | FW reach |
|---|---|---|---|---|
| Wet brush pen | 10 / 9 / 100 | 2% / 0 / 0 | 76×56 (43²) | 72 px |
| Wet dip pen | 8 / 8 / 91 | 2% / 0 / 0 | 56×40 (26²) | 54 px |
| Wet watercolor | 72 / 100 / 268 (p95 1598) | 65% / 0 / 0 | 228×190 (99²) | 183 px |
| Wet ink wash | 72 / 69 / 255 (p95 1477) | 85% / 0 / 0 | 231×189 (96²) | 182 px |
| Wet oils | 110 / 131 / 224 (p95 935) | 82% / 0 / 0 | 219×178 (96²) | 173 px |
| Fast fineliner | 8 / 8 / 84 | 1% / 0 / 0 | 37×30 (22²) | 35 px |
| Fast gouache | 89 / 179 / 144 | 2% / 0 / 0 | 203×181 (103²) | 158 px |

  - **0 pixels change at lift** for Fast and Fast-wet, every preset.
  - Changes happen at most the window plus the brush radius behind the nib: 54–72 px for pens and 165–186 px for 120 px washes.
  - The update box stays small: at most about 230×190 px (avg ~160 px square) for washes and under 80×60 px for pens. Today's Drying redraws the whole stroke's box at lift instead.
  - **Cost:** Fast-wet pens are about 10× Fast (the window is laid again each sample) but still about 0.1 ms. Fast-wet washes, now settled once per frame, average 0.22–0.27 ms; the settling samples cost 1–1.6 ms.
- **Limits:**
  - Bleed and mixing happen only with paint wet in the *same* stroke (the stroke crossing its own window), because nothing stays wet after lift. See the table below for the cross-stroke version.
- **Java:**
  - `WildInkStroke`: a frozen `WashCoverage` plus window re-lay; `finish` lays nothing.
  - `WashStroke`: a per-pixel distance-along-stroke map in the coverage tiles, and a `WatercolorSettle.settleWindow(region, age)`.
  - `WatercolorSession`: no wet joins after lift.
  - `BrushSetting` `LIFE` and `WET_WINDOW` are appended to both kinds.

## Every effect: under the nib, in the wet window, or only after lift

(a) = under the nib (Fast). (b) = in the trailing wet window (Fast-wet), and how. (c) = only after lift today, with the closest WYSIWYG version.

| effect | class | how, or the WYSIWYG approximation |
|---|---|---|
| Dry brush | a | Bristles skip per dab, from load and paper. |
| Grain (paper tooth) | a | Per pixel, at compose. |
| Splatter | a | Thrown live per segment (seeded by segment), already in Fast. |
| Wobble | a | Per dab, from noise. |
| Pooling | a / b | Swells as the pen slows (a). Its growth while the pen rests can continue in the window (b). |
| Taper in | a | Known from the start. |
| Taper out | b (c today) | Today the tail is laid again at lift. Fast-wet lays the window's tail again each sample, from the pen's slowing, easing off or flicking, so it is right at lift. Fast does it per dab, from the pressure fall or flick. |
| Soft edge | a | Per dab. |
| Granulation | a / b | Per dab in Fast. "Settling in" with age in the window in Fast-wet. |
| Feathered bleed | b (c today) | A blur of the window's wet paint creeping into bare paper, grown with age. Bled pixels join the window and freeze after it. |
| Hollow edge | b (c today) | Rim = paint − blur(paint), darkened with age in the window. |
| Bloom | b (c today) | Low-noise lift and darkening, with age in the window. Today's 44 px blooms fit a 132 px window. |
| Brush streaks | a | Per dab, or per lane on the Hake ribbon. |
| Backrun | b (c today) | Ring and blossom from blob and low noise, with age in the window. A real backrun forms as the wash dries (seconds); the window can only start it. |
| Cover | a | A per-pixel mix at compose. |
| Impasto | b (c today) | Relief shaded live in the window (light at the upper left). It needs neighbours, so not strictly under the nib. |
| Charcoal spread | a | Stick grain, fringe and dust per dab. Dust is seeded by distance, so any re-lay matches. |
| Hake streaks | a | Lanes on the swept ribbon. |
| Tilt | a | Per dab. |
| Blend line/wash mixing | b / c | Within a stroke: the window. Across strokes today it needs paint wet after lift. **Approximation:** keep an invisible wetness map after lift (no pixel changes) for the drying time, and let the *next* stroke's own window do the mixing. All change happens under or just behind the new nib. |
| Plumes (ink into wet paint) | c → b | Plumes travel after lift today. **Approximation:** with that wetness map, the line's window grows short plumes (reach ≤ window) while the line is drawn, and they freeze with the line. |
| Settling pigment | b | In the blend prototype, `(1 − f)·D` lands at once and the rest later. Spread that remainder over the window's age instead. |
| Living ink | c | Growth over seconds after lift *is* the effect. **Approximation:** grow only inside the window while drawing (reach ≤ window), so tendrils and buds trail the nib and freeze at lift. Its slow time-lapse is lost; offer the drying life as the opt-in. |
| Bristle swarm | a | Hairs follow the pen while drawing. |

## 6. Big sizes: any brush, 1–360 px

- **Proposal: three customisable size slots on one px scale (1–360, log).** The page's px slider does this. What px means for each engine:
  - Pens: nib width at the preset. It is not the widest mark: Pressure can swell a stroke up to 1.6× the slot. Better: make the slot the firmest width, i.e. divide by 1 + 0.6 × pressure dial.
  - Paints: largest diameter.
  - Hake: width.
  - Charcoal: stick length.
- **Java:**
  - `InkWashSizes` presets become stored px per slot (`PaintPreferences`).
  - `inkSize` and `maxSize` read the slot.
  - The `InkWashBar` size dots and `WildSample.pad` use the slot.
  - A `scale` factor goes into `WildInkStroke`, `WashStroke`, `WashLook` and `WatercolorSettle`.
  - No share-code change: size is not in the code.
- **Cost per sample at 360 px** (Node, mean / p95 µs). Before is today's engine scaled up. After uses spacing carry: a dab every 10% of the width, or every 2·√(tip radius) on thin nibs.

| engine | before | after | lift ms before → after |
|---|---|---|---|
| Fineliner | 967 / 1069 | 218 / 946 | 16 → 4 |
| Brush pen | 1254 / 1886 | 248 / 1564 | 33 → 16 |
| Dip pen (flat, thin) | 1031 / 1373 | 738 / 1074 | 1 → 1 |
| Pencil | 1523 / 1864 | 335 / 1643 | 0 → 0 |
| Willow charcoal | 1181 / 1524 | 1870 / 4165 | 32 → 81 |
| Watercolor | 624 / 894 | 89 / 590 | 65 → 64 |
| Gouache | 946 / 1244 | 128 / 825 | 18 → 18 |
| Oils | 826 / 1229 | 118 / 754 | 16 → 16 |
| Hake | 876 / 1226 | 114 / 336 | 36 → 37 |

  - Spacing carry cuts the mean 4–8×. The samples that do lay a dab still cost about what one costs today (p95), so worst-case latency at 360 px stays around 1–2 ms in JS. On the device that is probably 3–5× more.
  - **No cap is needed for cost**, except messy charcoal at 360 (p95 4 ms in JS). For that one: cap at about 240 px, or drop the dust to every other dab when the stick is big.
  - Thin flat nibs gain least, because their pointed ends need close dabs.
- **Problems found when today's engine is simply scaled up** (After fixes each one; all factors are 1 at today's sizes, and `eqtest.js` confirms nothing changes there):
  - **Tapers** are fixed at 70/90 px, so a 360 px nib ends in a blunt blob. Scale them by √(size/16).
  - **Wobble** reads 6 px noise per dab, so the width jumps dab to dab, which gives lumpy edges. Read the noise at size/16 scale.
  - **Pooling** is a share (up to +90%), so a slow 360 px pen swells to 680 px. Make it an amount: at most its 16 px amount × √scale.
  - **Splatter** drops are size/5 (up to 100 px blobs) yet land only 6–40 px ahead, inside the nib. Cap drops at 24/5 × √scale and throw them clear (+ half the size, × √scale).
  - **Dry-brush bristles:** 48 across 360 px make even 7 px stripes. Allow up to 160 (the first 48 unchanged, so small brushes keep their look).
  - **Paint hairs:** 120 maximum gives 4–5 px streaks at 360. Acceptable.
  - **Watercolor blooms and backruns** use fixed 44 px and 16 px noise, which mottles a 360 px wash. Scale the noise by size/220. Scale the feather bleed and hollow rim by √(size/220).
  - **Paper tooth and granulation** stay fixed to the page. That is correct, because paper doesn't scale with the brush.
  - **Thin nibs:** with spacing carried, a flat nib's pointed ends scallop. The spacing bound 2·bb/√a fixes it.
- **Style advice:**
  - Fineliner and Pencil at 360 px are just flat black discs. The engines allow it, but those styles could suggest a range (Fineliner ≤ 48, Pencil ≤ 64) without enforcing it.
  - A big pencil wants charcoal-like side shading (Spread) rather than a 360 px lead.

## Prototype caveats

- The prototype composites one tone buffer, not the app's tone and alpha layers.
- "Changed at lift" compares 16-level greys.
- The JS lift times leave out the app's diffusion, edge band and groove passes, so the device's non-Fast lifts are heavier than shown.
- Fast-wet's numbers come from Node (`bench-fastwet.txt`); the page's Benchmark button shows the same columns in a real browser.
- Headless Chrome freezes `performance.now`, so the page's Benchmark button only shows real numbers in a real browser.
