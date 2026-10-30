# Match Lab: notes

Page: `match-lab.html` (one file; Google Fonts' Roboto Mono is the only external resource). Daily Puzzle Style: six
greys, 2 px strokes and 3 px borders, square corners. Works at phone width (checked at 390 px).

Built by `node match-lab-src/build.js [node-bundle path]`:
- The engine is the Modular Brush Lab's, read unchanged from `prototypes/modular-lab-src/` with Brush Studio's own
  file list (`engine-files.cjs`, shared by both builds since 2026-10-27; before that this page left out `newmods.js`,
  so Stipple, Hatch, Stamp, Crayon, Blend and the rest were missing). The lab's port is the newest the repo has but
  trails the app (as of 2026-10-27): no Wet paint drying (Life 7), no Print style, so 41 of the app's 118 Basic tools'
  codes don't read here (7 of the 33 drawn ones: Soft wash, Wet blooms, Pooling wash, Loaded oils, Dot tone, Ben-Day
  brush, Engraving pen); White ink's light-over-dark and Spiro's Oval and Bar tracks aren't drawn. None of that
  touches the codes this page writes (checked: every sample's code reads back the same through the app's
  `ModularCode`, with `studio-src/java/StudioCodeEcho.java`).
- The app's drawn tools: `DrawLayout.PICKER`'s names with their codes from `ToolPresets.ROWS`, read from the app's
  source at build time (`DRAWN_TOOLS`). The paper scans come from `prototypes/modular-lab.html`'s
  `TEXTURE_SRC` (the same pixels as `app/src/main/assets/textures/*.bin`, checked: 0 difference).
- Its own files: `imaging.js` (from the first web lab: lighting fit, Otsu, components, distance transform,
  Zhang–Suen thinning), `analyze.js`, `fit.js`, `paper.js`, `samples.js`, `ui.js`, `page.html`.
- The core (engine + the above but the UI) is one `<script id="core">`. The fit's Web Worker is made from that
  same text (a Blob URL); if a page policy forbids that, the fit runs on the page in 40 ms slices.

## Match a stroke
1. **Photo**: grey, at most 1200 px; the lighting divided out (a cubic surface fitted to the paper, stroke left
   out); Otsu's threshold, closed a little (pencil crumbs), the largest piece and its nearby drops; a padded box.
   Drag on the photo to box it again; Flip the start if the dot is at the wrong end.
1b. **Paper** (since 2026-10-16, after the owner's dim photo read paper texture as a wash): the bare paper well away
   from the mark gives its level and spread; paper level plus 2.5 spreads becomes white, and a mark that is plainly
   ink (dark core, even tone) but photographed grey is stretched so its core is black. Faint flecks not touching the
   mark are dropped. A halo counts only if the band just outside the edge has more pixels over the paper's floor
   than bare paper does (3 times as many, plus 15 %) and is darker by 1.5 spreads; otherwise wet ink isn't offered.
   Candidates get the same floor (speckle at the paper's spread, then 2.5 spreads off), so both sides match.
2. **Scale**: the stroke about 440 px long (a hand's stroke on the Manta), at most 110 px wide, but its finest line
   (from the distance transform's ridges, at a stricter threshold) kept at 3 px or more.
3. **Measure**, along the centre line: width profile and taper lengths, edge roughness and softness, body tone and a
   tone histogram, dry-brush gaps, halo outside the edge, hollow edge, streaks, splatter, how many lines cross each
   row (peaks with a quarter-tone dip, so grey gaps count), a four-octave grain spectrum (differences of
   Gaussians), and a nib's angle and width from how the width turns with direction.
3a. **Steady** (2026-10-27, the owner on real pens: "a bit too sensitive to edge roughness, wobble, etc"; `STEADY` in
   `analyze.js`). A ragged pen edge, the camera's noise and a little hand wobble pushed fits toward rough tips, dry
   media and the Wobble dial: edge roughness flagged "rough" from 0.1 px (ink's prior down, crayon and charcoal up) and
   was scored from zero; Wobble was started at 6 × the width's wiggle; a shaped nib was missed when ragged widths
   blurred the width-by-direction fit. Now: each traced edge is smoothed over 1.5 px before its roughness is read (on
   the photo and every candidate alike); roughness under 0.3 px (or 2 % of the width) counts as none, in the score
   and the priors; Wobble starts only past a wiggle of 5 % of the width (and costs 1.2, was 0.8); the stroke's
   direction is taken over a width each way (was half); the nib is fitted to widths smoothed over half a width. The
   score's roughness unit is 0.6 (was 0.35), the grain spectrum's 0.5 (was 0.35, its floor 0.02, was 0.01). On the
   samples a ragged pen edge (1.2 px, wobble 1.5 px, double noise) reads 0.08–0.13 px; the pencil's grainy edge 0.5.
   Tried and dropped: smoothing the photo's edge itself (the camera's blur then copied the softer edge: halos, rounded
   square ends), a smoother centre line (the italic nib's hairlines went to a round tip), and reading ends on a
   blurred image (square cuts lost).
3b. **Ends**: past the centre line's end, the width across every half pixel; the tip is where it falls under 15 %
   of the body's. Square: how full the last half width is against the width before it (square ≈ 1, a round cap ≈ 0.8,
   a taper far less), measured again turned to the cut if it is slanted. Under 6 px wide it can't be told. A square
   end means no taper at that end, no easing off of pressure into it, and the Hake (whose head follows the stroke, so
   both ends are cut across) or, when the other end tapers, a flat nib held along the cut (nearest of 20–70°). Ends
   are scored on candidates too.
4. **Fit**: candidates (Tip × Medium × Drying, each with a prior from the measurements) are drawn by the engine
   along the photo's centre line, pressure solved from the photo's widths through the brush's own width law,
   about 1 px/ms, easing off at the end. Each render gets the camera's blur (matched on edge slopes) and the
   photo's sensor noise, then is scored: silhouette IoU, blurred difference, width profile, tone histogram,
   roughness, spectrum, gaps, halo, hollow, streaks, splatter, line count, pooling; plus a small cost per effect
   that's on and a unit per step of prior below the likeliest. Search: each candidate's size, shade and its one or
   two defining dials (a few Nelder–Mead steps), the best four over every free dial, the best two to the end of the
   budget, then the five papers if grain shows. Near ties (8 %) go to the likelier structure. Budget 5 s.
   Crayon (since 2026-10-27) is tried for a rough mark that isn't crisp ink, round or chisel, on cold press.
4a. **Tip and Category** (2026-10-27, the owner: "select a nib first to start, and a starting point for what category
   it's in"): two chip rows before the scan, both Any by default (today's search). Tip: Round, Chisel, Flat Nib,
   Split Nib, Rake, Bristle, Hake (the engine's Tip choices the fit can draw, their labels); a hard limit. Category:
   the tool picker's groups in `DrawLayout.PICKER`'s order (the app shows no headers), `CATEGORIES` in `fit.js`, each
   with its drawn tools and the tips and media they draw with (`noisecheck.cjs` checks every drawn tool is in one):
   Pens (round, flat, split; ink), Markers (chisel, round; ink, wash), Brushes (round, bristle, rake, hake; ink,
   wash), Washes (round, hake, bristle; wash), Dry Media (round, chisel; graphite, charcoal, crayon), Oils (bristle,
   round; Opaque, since Loaded oils' wet paint doesn't read here), Odd Ones (round; ink, wash: a spirograph, a
   screentone or a fading ink can't be told from one stroke). Its tips and media are seeded and get 3 units of prior,
   so others pay 3 of loss; the drawn tools compared are the group's (a tip's, if a tip is chosen). With either
   chosen: at most 6 structures (10) and 60 % of the time (3 s). The pencil case keeps each tool's choices.
4b. **Drawn tools**: each drawn tool the engine reads, as it is, at the fit's grey, three sizes, then the closest five
   at four finer sizes; the closest three are named under the code with their scores. About 0.5–3 s more.
5. **Out**: the brush (part words from the lab's `choiceWords`, its sentence from `brushWords`, as Brush Studio), its
   M share code with Copy, the size and grey to draw it with (codes don't carry them), the numbers, and a test pad
   (Pen pressure and tilt pass through; a mouse presses harder when slow). A name typed under the code renames it
   (a code holds its name as a slug: "Blackwing 602" reads back as "blackwing").
6. **Pencil case** (2026-10-27): Add Tool puts the scan in the list under its name; names stay editable. Kept in
   localStorage (`brush-scan-set`: name, code, size, grey, nearest drawn tool, the tip and category chosen); a stored code that no longer reads is
   dropped. Out as one text block, Copy All or Download (`brush-scan.scribulor.txt`): the format `BrushImport` reads
   (a code a line, `#` lines skipped), each code under a `#` line with the full name, size, grey and nearest drawn
   tool. The app takes it as "Scribulor import.txt" or any "*.scribulor.txt" beside the drawings. (`ToolKits` only
   names Basic tools and `ToolBackup` is never read back, so neither fits.)

## Import a paper
Square crop (drag to choose) → the photo over a heavy blur of itself → the grain's size matched to the app's papers
by the lag where its autocorrelation falls to a half (Grain size slider multiplies it) → high-pass (σ 14) →
seamless: each axis cross-faded near its edges with itself shifted half a tile, spread kept → 8 bits at mean 128,
spread 52. Shown as a tile, a 2 × 2 repeat, the numbers (seam = step across the wrap ÷ steps inside; 1 is seamless),
and under four brushes. Download PNG (8-bit grey, 256 × 256) or .bin (the app's raw format); Use in Try it draws on it
(it stands in for cold press while drawing; the share code keeps the brush's paper).

## Checks (Node, from `match-lab-src/`)
- `make-tests.cjs <bundle> <dir>`: the 14 sample photos (`samples.js`: 8 engine strokes with known brushes, rescaled,
  blurred, warm uneven light, grain, noise; 6 drawn without the engine) and `truth.json`.
- `make-paper-photo.py <Paper003 colour JPEG> <out.png>`: a CC0 paper (not one the app ships) under a lamp.
- `noisecheck.cjs <bundle>`: pass or fail. Four samples fitted clean and with a ragged edge, wobble and noise keep
  their tip and medium, read as smooth and keep Wobble low; the pencil still reads rough; a chosen tip is the only one
  tried; the categories hold every drawn tool once. About a minute.
- `fitcheck.cjs <bundle> <dir> [filter] [budget]`: fits every sample, prints the numbers, writes photo | fit PNGs.
- `papercheck.cjs <bundle> <photo> <dir>`: imports a paper, prints its numbers beside the app's.
- `shots.cjs <dir> [stroke.png] [paper.png]`: headless Chrome at 1280 and 390 px; the page's `#selftest` (two
  samples into an empty pencil case under names, the pencil with Dry Media chosen: its medium and drawn tools the
  category's and the choice kept; then every code in its import file read back); the phone run scans with Brushes chosen.

## Results (5 s budget, this Mac; 2026-10-16, fourth pass)
| Sample | Found | IoU | Width err | Ends photo / fit |
|---|---|---|---|---|
| Fineliner | round · ink | .70 | 1.9 % | fine |
| Brush pen | round · ink | .87 | 2.0 % | tapered / tapered, round |
| Italic nib 40° | flat nib · ink | .86 | 4.7 % | |
| Split nib | split · ink | .61 | 10.1 % | |
| Three lines | 3-line rake · ink | .63 | 3.7 % | |
| Watercolour | round · wash | .92 | 2.6 % | |
| Charcoal | round · ink (was charcoal) | .87 | 6.6 % | |
| Flat end (Hake, dim) | hake · ink | .90 | 1.3 % | square, square / same |
| Dry brush | round · wash | .90 | 1.9 % | |
| Sumi brush (drawn) | round · ink | .75 | 10.4 % | |
| Feathered pen (drawn) | round · wet ink (halo found) | .73 | 2.0 % | |
| Pencil (drawn) | round · wash | .64 | 22.6 % | |
| Italic at 140° (drawn) | round · ink | .75 | 20.5 % | |
| Two lines (drawn) | split · ink | .50 | 16.4 % | |
| Dim photo (drawn) | flat nib · ink | .87 | 5.0 % | square, tapered / square, round |
| Slant cut (drawn) | flat nib · ink | .86 | 5.0 % | square, tapered / same |
| Thin top (drawn: a third as wide for a third, then 3× wider, square end) | flat nib · ink | .82 | 2.5 % | square, tapered / square, round |
| Wobbly top (drawn: ±28 % wobble in the top third) | flat nib · ink | .78 | 2.9 % | square, round / square, tapered |
| Flat brush (drawn) | hake · ink | .89 | 3.1 % | square, square / same |
| Wash (drawn) | round · wash | .86 | 6.1 % | |

Fourth pass (the owner's lines still came back with a pale band, a uniform width and a round end):
- The display was already the clean engine render (the camera's blur and speckle are only in the score). The band
  was the brush's own: the score couldn't see it because the photo had been pressed to black and white and the
  candidate with it. The halo-band term now reads the candidate as drawn, before that curve.
- Crisp ink, as a best guess: a mark with a dark, even core and no measured halo is pressed near black and white
  (paper floor to white, core to black, a 30 % step), on both sides of the comparison, and drawn at full shade. Wash,
  wet ink and pencil aren't offered for it; charcoal only as a fallback. Only a pale mark, an uneven inside or a
  real halo keeps the greys. The halo test is stricter (a band from 2.5 px past the half-tone edge, darker by three
  paper spreads).
- Width: the pressure envelope now follows the photo's row-by-row widths, smoothed over a third of a width, and only
  the taper zones are held (from each tip until the width reaches 80 % of what follows, so a thin stretch isn't a
  taper). Pressure to size starts at the least that covers the photo's width range. Width error fell on most samples
  (brush pen 4.4 → 2.0 %, watercolour 7.4 → 2.6 %, dry brush 11.7 → 1.9 %, pencil 33 → 23 %).
- Ends: a taper's last few strips no longer read as a short straight cut (the end must keep 70 % of its width to
  half a width from the tip).
- Trade-off: the charcoal sample is now matched as crisp ink (its black core reads as ink; its rough edge stays).

## Known gaps
- Pencil from a photo still comes out as a pale wash: graphite is tried but needs a longer search than 5 s gives it. Thin parallel lines score low on IoU however close they look (a pixel's shift is a third of a line).
- Nib angles outside the engine's 20–70° (a left hand's 140°) can't be matched; it falls back to round.
- Hair streaks (sumi) aren't reproduced: Bristles is tried but loses to a darker round ink.
- A dark wash (d6) comes out lighter than the photo: the engine's transparent wash tops out about there.
- Hake has no taper, so a stroke square at one end and pointed at the other uses a flat nib, whose cut is slanted
  by the nib's angle (20–70°) rather than square across a vertical stroke.
- The start is guessed as the blunter end; a square lift-off reads as the start. The brush draws the same either way.
- Size is in the work frame's pixels, which assumes a 440 px stroke; the code holds the look, not the scale.
- The found brush is named for what stands out in it (the lab's `describedName`): "ink-apple" for a Hake in ink.
  Rebuilt 2026-10-18 from the lab's sources as they stand, so the page also has the lab's changes since its last build.
