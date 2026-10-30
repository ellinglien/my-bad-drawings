# Cover Designs: notes

Prototype: `cover-designs.html`, one file with no external scripts and no stylesheets, built the same way as
`mischief-lab-2.html`. iA Writer Duo 400 and 700 are embedded as base64 `@font-face` from `app/src/main/res/font/`. The
page has no `<!doctype>`, `<html>`, `<head>` or `<body>`, because the publisher wraps it. It starts at `<title>`. One
theme, black on white, every colour set. No app code was changed.

The owner, on the marbled covers from `inkwash-covers` (`CoverMarble`, `NotebookCovers`): "the swirled look is cool but
doesnt quite read as a composition book as they are ... i think it's getting there though.. can you do other designs?
also there is a black band on the left side of the cover (and right on the back) for the tape... and the label is
slightly rounded". The guide the owner pointed at (knowledgesync.blog, composition book patterns) couldn't be fetched,
so the designs follow what real composition books look like.

## The page
- **Name:** typing a name makes every cover again (220 ms after the last key). Empty, each design shows its own
  sample name. Each design has its own seed from the name: FNV-1a (32 bits) of `key + ":" + name`, then SplitMix32.
- **Show:** 16 Greys (the panel's greys, `GrayPalette.VALUES`, each tone to its nearest) or Two Tones (cut at 128).
- **1 Shelf:** every design's front cover at 280 × 350, one canvas pixel to a CSS pixel where the grid has room (two
  columns on a phone, scaled down). A tap picks that design for the large view.
- **2 Front and back:** one design at 1920 × 2400, front and back. Fit, or Full Size (a scrolling box, one canvas pixel
  to a CSS pixel). For the three marbles and Reverse, **Method** (Noise, Cells, Both) shows the two halves of the marble
  alone. The time each took is shown under them.
- The work goes a cover a turn (`setTimeout`), so typing stays quick; a new name drops the rest of the queue.
- Patterns are kept in memory up to 80 MB (a full-size one is 18 MB), so switching tones redraws without making the
  pattern again.

## Shared anatomy (every design)
- **Page:** the Manta's, 1920 × 2400. All measures below are shares of the cover's width `w`.
- **The tape:** ink, `0.095 w` wide (182 px), down the left of the front and the right of the back. At full size a
  faint twill: one pixel in eight on a diagonal, in a basket-weave check of 4 px, at grey 80 (the panel's darkest
  grey; anything fainter rounds to black). At a shelf tile's size, plain ink.
- **The label:** white, `0.52 w` × `0.33 w`, its top at 14 % of the height, centred over the printed part (right of
  the tape), corners rounded `0.02 w` (38 px). The double rule `0.009 w` in from its edge: a thin line (`0.0024 w`,
  5 px), a gap (`0.0034 w`), a hairline (`0.0012 w`, 2 px); each at least 1 px, so it holds on a shelf tile.
- **On it:** COMPOSITION BOOK (700, `0.026 w`, letter-spaced 0.14 em); then NAME, SUBJECT and DATE (700, `0.0155 w`)
  each with a hairline to the right. The notebook's name is written on the name line in iA Writer Duo 700, as large as
  fits (`0.052 w` down to `0.026 w`, then cut with an ellipsis); the date on the date line in 400 (`0.022 w`), today's
  date in the prototype ("19 Oct 2026"). Subject is left blank to write on, as covers are drawn on.
- **At a shelf tile's size** (under 600 px wide) the small words and the date are left out: they would only be
  smudges. The title, the lines and the name stay.
- **The back:** the same design, no label, the tape on the right. The pattern is the next part of one printed sheet
  (the field sampled 1.1 cover widths on), as a real book's cover paper wraps round. Swirl's back is a bath of its
  own (seeded from `name + ":back"`). No printed panel on the back: it cluttered the pattern, and real books keep the
  tables inside.

## How a cover is made (the part to port)
Every design is a **field** over the cover in cover widths (u 0–1 across, v 0–1.25 down): above 0 paper, below 0 ink.
- It is sampled on a lattice every 2 px at full size (960 × 1200 samples), every pixel at a shelf tile's size, which
  is made at twice its size (560 × 700) and averaged down 2 × 2.
- Each pixel is blended between its nearest four samples and cut at 0, the edge spread over one pixel by the field's
  slope there: `paper = clamp(0.5 + f / |∇f|)`, the slope from the same four samples. So edges are crisp and smooth at
  any size and a thread down to about 3 px survives.
- Then the tape, the label (canvas text), and every pixel to the panel's tones.
- Swirl is a tone (ink or clear), not a field: sampled every 3 px at full size (640 across, as `CoverMarble`), blended
  between four and steepened (`× 1.25 × step`), as `CoverMarble.tones` does.
- **Determinism:** per sample only + − × ÷, `Math.sqrt`, `Math.floor`, `Math.imul` hashes (Java: `int` multiply and
  `>>>`) and tables. The gradient noise has 16 directions whose components are exact square roots (`(1,0)`, `(1,1)/√2`,
  `(2,1)/√5` and their turns), so the table is the same everywhere. Terrazzo's 256 directions are `Math.cos` /
  `Math.sin` once at start (both V8 and `StrictMath` are fdlibm); Woodgrain's knots use `Math.exp` per sample, and
  Swirl `Math.sin`, `cos` and `exp` per sample: for the port, use `StrictMath` or `CoverMarble`'s tables.
- **Noise:** `perlin` (gradient noise, quintic fade), `fbm` (octaves, each twice as fine, turned by an exact 3-4-5
  rotation so the lattice hides), `worley` (one jittered point a cell, jitter .1–.9; the nearest two from 3 × 3 cells,
  the nearest three from 5 × 5, as the third over 3 × 3 left straight seams).

## The designs
1. **Classic Black Marble** (`classic`). The most important one: reads at once as a composition book at shelf size.
   Paper where either of two things says so, on a page warped twice (a broad drift of 0.03 w, then a wobble at the cells'
   own size so no cell is a clean polygon):
   - **Cells** (reticulation): foam walls where the second-nearest cell point is nearly as near as the nearest
     (`F2 − F1` under a width), swelling into knots at the joins (`F3 − F1` under a larger width). The widths wander
     with a slow noise, so threads break, thin and swell; finer cells (2.2 × finer) split the big ones where a slower
     noise lets them; a fine grain makes the edges ragged. 36 cells a cover width: cells about 2.8 % of the width.
   - **Noise** (flecks): five octaves of gradient noise (gain .6) cut at a level that wanders over the cover, so the
     white clusters crowd in places; porous (black specks inside) where the fine grain is high. Plus a few small specks
     inside the black cells.
   - **The comparison** (Method, on the page): Noise alone (finer and cut lower, so it carries a cover by itself) gives
     loose ragged flecks with no structure: camouflage more than a composition book. Cells alone gives the
     reticulated foam but looks too even, like giraffe skin or dried mud. Both reads as the real one: a dense black
     foam of irregular cells with white threads and knots, broken by porous white clusters. Both is the default.
   - About a quarter paper (the tape aside).
2. **Fine Speckle** (`speckle`): the same at 75 cells a width, walls mostly broken, more specks. About 16 % paper.
3. **Bold Marble** (`bold`): the same at 15 cells a width, six noise octaves for the big clusters' detail. Chunky
   black stones and white threads. About 24 % paper.
4. **Reverse Marble** (`reverse`): the same field turned over (ink where Classic has paper), walls mostly broken
   (width −0.01 ± 0.09), so it is black flecks, threads and knots scattered on white rather than a net. About 25 % ink.
5. **Swirl** (`swirl`): today's suminagashi (`CoverMarble`, Jaffer and Lu's drops, two comb passes of nine tines, two
   swirls), ported, with the new tape and label. Cleaned up: five columns of sites instead of four and drops 0.045–0.075
   instead of 0.06–0.095, so the stones are smaller and there are more of them (up to 140 drops). Still the big
   swirled look the owner liked; thin slivers between ring drops still show at full size.
6. **Crackle** (`crackle`): a crazed glaze, ink cracks on white. Big cells (7 a width) with wobbled walls of wandering
   width; finer cells' walls (19 a width) inside them in patches, as crazing spreads; a fine jitter so the lines crack
   rather than draw.
7. **Terrazzo** (`terrazzo`): angular white chips on black. Three sizes of chip on jittered grids (cells 0.052, 0.021
   and 0.009 w; 85 %, 70 % and 35 % of cells have one), each chip the intersection of 4–7 half-planes round its
   centre, so always convex and angular. The field is the chip's signed distance (the largest half-plane distance).
8. **Woodgrain** (`wood`): a plank's grain, cut like a woodcut: white lines on ink running down the cover, 30–38 a
   width. The lines are streamlines of a slow warped flow, parted round one or two knots as water round a stone (a
   cylinder's stream function, `x · R² / r²`); each knot an eye of rings in an ink ring. Each line's width rises and
   falls along it, as a gouge's does.
9. **Engraved** (`engraved`): a banknote's lines, 52 across the height of a width, waving gently; their weight follows a
   slow noise from hairline to solid, so the cover shades like an engraving. At a shelf tile's size the lines are
   3.4 px apart: no moiré, but it reads as grey tone more than as lines.

## Times
Chrome on the Mac (this session's), each design's sample name, a full-size cover made from nothing (the pattern, the
tape, the label and the tones), warm:

| Design | Front | Back |
| --- | --- | --- |
| Classic Black Marble | 824 ms | 811 ms |
| Fine Speckle | 718 ms | 710 ms |
| Bold Marble | 763 ms | 767 ms |
| Reverse Marble | 687 ms | 687 ms |
| Swirl | 760 ms | 789 ms |
| Crackle | 541 ms | 538 ms |
| Terrazzo | 367 ms | 372 ms |
| Woodgrain | 203 ms | 203 ms |
| Engraved | 193 ms | 194 ms |

- Classic's halves alone: Noise 590 ms, Cells 708 ms. The label and the tones add about 30–40 ms.
- All nine shelf tiles (280 × 350, made at 560 × 700): 1.5 s together, about 170 ms each.
- The first full-size cover after the page loads is slower, 3 s, before the script is compiled to speed.
- On the Manta expect several times longer (the Swirl marble was 0.15 s on the Mac and about 3 s there, at 640 samples
  across): make it ahead in the background and keep it, as `CoverMarble.cached` and `prepare` do. Classic samples
  1.15 M points with about 20 noise lookups each; sampling every 3 px (as Swirl does) would cut it to under half, with
  threads under about 4 px lost.

## Checked
- In Chrome (served from 127.0.0.1, as published, without a doctype): no console errors; typing a long name
  ("RPM 2027 lyrics and chord charts for the whole month") makes every cover again and cuts the name with an ellipsis;
  Two Tones, each design, Method and Full Size all work. No horizontal scroll at 375 px.
- Every full-size cover has only the panel's 16 greys (2 in Two Tones).
- **A bug found and fixed:** without a character set the page's script was read as Windows-1252, so the ellipsis
  ("…", three bytes) became three characters, and cutting a long name added a character each time round: the page
  hung. The script now writes `…` and `×`, and the page text uses `&middot;` and `&times;`; the cut takes
  one character a time and adds the ellipsis once.

## For the app
- Port the field functions and `pattern` (lattice, four-way blend, cut by slope) to Java next to `CoverMarble`;
  `NotebookCovers` gets the tape and the new label (rounded corners, double rule, the three lines, the name on the
  name line). The label's measures above are the constants.
- A notebook's design: a per-notebook option, Classic as the default (the owner: "covers: yes pick its own design with
  black marble by default"). Built on `inkwash-covers2`: `CoverNoise`, `CoverDesign`, `CoverPattern`, the generators
  matching this page's `pattern` to the bit (`CoverDesignChecks`).
- `CoverMarble.VERSION` should change with the new covers, so the cache folder's old marbles aren't read.
