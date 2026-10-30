# Mischief Lab 2: notes

Prototype: `mischief-lab-2.html`, one file with no external scripts and no stylesheets, built the same way as
`mischief-lab.html`. The owner's aim for these tools is "useful but delightfully unpredictable". iA Writer Duo 400 and 700
are embedded as base64 `@font-face` from `app/src/main/res/font/` (byte for byte the same as the first lab's). The page
has no `<!doctype>`, `<html>`, `<head>` or `<body>`, because the publisher wraps it. It starts at `<title>`. No app code
was changed.

Shared by all six pads (the first lab's helpers, copied):
- **One canvas pixel per CSS pixel**, drawn with `image-rendering: pixelated`.
- **Every tone shown is one of 16 greys** (`255 − 17k`). One theme only, black on white, every colour set.
- **Input:** pointer events with coalesced samples. A pen's `pressure` is used as it comes. A mouse gets pressure from
  speed (`1.08 − 0.38 × px/ms`, clamped .22–1, eased over ~20 ms).
- **Demo strokes:** the app's swoosh (`WildSample.swoosh`) and wave (`WildSample.wave`), plus shapes where a trick needs
  them (a ring, a box, taps, a row of written loops).
- Each demo plays once, the first time its pad is 60 % in view. Replay plays it again, and drawing stops a replay.
- **New shared bits:** `inkDab` / `inkLine` (an antialiased round darkening a buffer), `vnoise` (one sample of value
  noise), `inPoly` (even-odd), `only` / `showFor` (rows shown for one mode), `syncChips` (a chip chosen from code), and
  `keepFor`: a demo puts back the tool or mode it changed, and one restarted mid-play puts back the one from before the
  first started.
- **Added in the second round:** the Pad's points carry `tilt` (0–1, null for a mouse or a replay); `dialRow` rows have
  `set(v)` for a value a demo sets; `menuRow` makes a small select.

## 1. Stay in the lines
- **Pen** draws crisp ink lines (radius .9–2.4 px). **Soft pen** draws a wet line (radius 3.5–8 px, darkest .5–.85 in
  its middle, fading over the outer 80 %, mottled). **Wash** stays inside the region of ink lines it started in.
- **Only crisp ink makes walls** (owner, 2026-10-19: soft edges "create a weirdness"). The pens draw on separate
  layers, as the app keeps its ink layer apart from its wash layer: walls are read from the Pen's layer alone, so a
  soft line never holds the wash however dark it is (tested at full pressure, .97). Nothing is guessed from edge
  gradients. In the app, walls come from the ink layer's crisp marks.
- **Walls:** the line pixels (ink > .4), thickened by 2 px (two 3×3 max passes) only where the gap is real, so a gap
  of up to about 4 px holds. The demo's ring has a 4 px gap.
  - **A wedge's point (owner, 2026-10-19: "still getting a slight white area with the fill", a leaf of two lines
    meeting at a sharp point):** the wall was thickened everywhere, so wherever the two sides came within about 5 px
    the region was cut off. The margin (below) only reaches in by stepping no farther from a line, and a hand's wobble
    breaks that run toward the point, so for a 10° point up to ~30 px of paper stayed white.
  - **Gap or wedge:** a gap is between two pieces of ink, or two parts of one piece that meet only a long way round
    (the ring's two ends); a wedge's sides meet near by, at its point. So a pixel within 2 px of the line is wall only
    when the ink within 6 px of it is two pieces (8-way connected parts), or one piece in two parts within that box
    whose geodesic distance along the ink (8-way steps) is over 14 × their distance apart there (and over 20 px).
    A 10° wedge's sides are ~11.5 × their distance apart along the ink, so it never closes. Only pixels with ink on
    the far side from their nearest line pixel (cos < −.2) are looked at closely; the rest of a lone line gets no
    thickening at all, the region running right up to it.
  - The gaps that hold are the same as before: open gaps of 4 px in a ring, 5 px between two arcs, 4 px between a
    lid and its box; 5, 6 and 5 px leak, as before.
  - **The cost:** a small loop with a gap leaks, since its two ends are not far apart along the ink: an open 4 px gap
    leaks in a ring of radius 12 px or less (holds from 15 px), a 2 px gap holds down to radius 10.
- **Pockets:** where lines touch or cross (a wedge's point, a wobbly line running into the other side) paper or light
  ink is shut in: a few pixels (up to 24), or a sliver no pixel of which is over 3 px from a line (up to 1000, the far
  side of a wedge's narrow neck). One within 12 px of the region (through the line) is wet when the wash reaches that
  pixel, so no paper shows in the line.
- **The region:** a 4-way flood fill of the open pixels from the touch-down point (the nearest open pixel within 8 px),
  plus a margin and the line's own edge, so the wash meets the line with no paper between.
  - **Margin:** the thickened wall on this side of the line. It grows from the region four ways into wall pixels only,
    each step no farther from a line pixel (Euclidean distance, out to 6 px). That reaches every pixel up to the line at any angle and into
    corners, the line's light antialiased edge pixels too, but never through a gap: past a gap the pixels are farther
    from the line ends again.
  - **Under the line:** the line pixels (ink > .4) next to the region or margin, and the layer behind them, are wet
    too, but the wash doesn't spread on from them. A light pixel wedged between line pixels joins them.
  - **The white rim (owner, 2026-10-19: "slight white where the ink wash meets the line edge"):** it was real white in
    the buffers (wet, base and ink all 0), not the screen. Two causes. The margin took 3 four-way steps, but the walls
    are thickened 8-way, so on a diagonal line (the ring at ±45° and ±135°) the wall is up to 4 or 5 four-way steps
    deep and a 1–2 px strip of paper was left. And the line's edge pixels over ink .4 were never wet, so they showed
    lighter than the wash beside them, a pale seam everywhere. A third case: a wash that ran out 1–4 px before a line
    left a sliver of paper.
- **Meeting the line:** past its reach a wash that has come within 6 px of a line still runs on, straight toward it
  (only to pixels strictly nearer a line, so it creeps at most 4 px along the line that way), so it never stops a few
  pixels short.
- **The flood:** each wash dab wets its region pixels at distance 0. A bucket queue (chamfer steps 3 straight, 4
  diagonal, diagonals only where both sides are open) spreads them about 3 px a frame, so the wash visibly runs out.
- **Spread** sets how far: `220 × spread²` px past the brush, 0 the brush alone, 1 the whole region ("Fills").
- **Look:** paler toward the far end, ×1.3 where it pools against a line, ×1.22 in a rim where it stops short (a
  backrun), mottled by soft noise. The last wash dries into the page when the next starts, so washes glaze.
- **Demo:** the ring with a gap, and a box in three bands, a crisp wave above and a soft wave below. The wash laid in
  the middle band stops at the crisp wave and runs over the soft one into the bottom band.
- **Built in the app** (2026-10-20, branch `inkwash-stayin`): a brush, beside Fill's paint bucket (which came from this
  pad first): Smudge choice 5, **Stay in Lines** (`stayin`; one dial, **Flood**, the prototype's Spread renamed, as
  "spread" is an engine word on screen: Brush, then mm on the page, then Fills; default .35). The shape is Fill's own
  region (`FillRegion`: walls from the selected pair's ink layer only, the gap and wedge test, margin, the line under the
  edge, pockets), found once at the stroke's first sample within 640 page px of it, off the pen's thread on the drawing
  screen; a shape reaching past that, or open paper, leaves a plain wash. Everything the stroke lays is kept to it, the
  settling after lift too. The flood (`StayIn`) is this pad's bucket queue at page resolution, from the brush's rim,
  about 3 px a frame by pen time, at most 2000 pixels a sample (0.08 ms on the desktop), the rest at the lift; its look
  as here (paler far off, ×1.3 at a line, ×1.22 rim), painted into the stroke's body so Quick and Slow work it as their
  own. Never drawn quick, never rolled, never a variation's swap. The web lab and Brush Studio have the same choice,
  dial, codes and words (the lab's walls: what ink-medium strokes laid). See `StayInChecks`.

## 2. Stamp
- The pad starts with a drawing to cut: a solid star (the first stamp), a flower, a patch of hatching and the wave.
- **Two modes, Cut and Brush** (owner: "cut should be lasso not box … the stamp and nib are the same thing"). Box and
  the separate press-only Stamp mode are gone.
- **Cut:** a lasso only, shown in marching ants closed back to its start. On the lift the ink inside becomes the
  stamp, trimmed to what's there, and the mode moves on to Brush. Nothing inside: "Nothing to cut" for 1.2 s.
- **Brush:** a normal brush whose tip is the stamp, laid every Spacing along the line.
  - **Size** 12–120 px (the stamp's longer side), **Spacing** 5–150 % of it, **Turns** On or Off (On: turned to the
    line's heading, smoothed 35 % a print; Off: upright). A touch-down prints once, so a tap gives one print, turned
    ±2.3° at random.
  - Small spacing gives a continuous nib trail, large spacing separate prints along the line.
- **Potato-stamp inking**, kept from the old Stamp mode:
  - Blotches: value noise (6 px cells, in the stamp's own coordinates) plus per-print grit; coverage is
    `clamp((amount × 1.1 − (1 − n)) × 3 + .1)`. Amount = load × (.55 + .6 × pressure), so pressure darkens.
  - Prints that overlap (Spacing under 100 %) share the stroke's blotches, so a trail runs dry evenly instead of
    filling in; prints apart get new blotches each.
  - Load: 1 after a rest of over 1 s since the last print. A press (a touch-down, or a print clear of the last) uses
    Ink fade's share; overlapping prints use (1 − share)^spacing², so a trail fades over several stamp lengths.
    Floor .25.
  - **Ink fade** (owner, 2026-10-19: "be nice to have it not fade sometimes"): 0–30 % of the ink a print uses, None
    to Strong. None: every print as dark as the first, no running dry. 22 % (× .78 a print) is the default, the old
    fixed rate. Strong is 30 % (× .70). Only the running dry changes; the blotches stay at every setting.
  - A stroke's prints are kept at their darkest, then laid on the page by multiply at the lift, so a trail doesn't
    darken itself but strokes and taps overlap darker.
- **In the app** this would be a Tip choice, "Stamp", whose nib is cut from the page by lasso: no Stamp tool, the
  brush's Spacing and Turns do the rest.
- **Built in the app** (2026-10-19, branch `inkwash-stamp`): Tip choice 10 (`stamp`; dials Size, Spacing, Turns (Head
  Follows' key), Ink Fade, Flick), `StampNib` (the lasso's cut, trimmed, at most 256 px; the nib store, keyed by a hash
  of its pixels that the code carries after the six) and `StampStamper` (print2 and place2, every pixel, the medium's
  compose laying it). A Stamp's Medium is 132 px in every style (the pad's 44 CSS px). The rest is per holder (a slot),
  as Paint Load is. Cut Stamp is in Brush Lab's bar (its test page's lasso) and This Brush on the Tools page (the
  drawing's next pen stroke); the lasso is the mirrors' placing stroke in marching ants, through the fast update. The
  web lab and Brush Studio have the same choice, dials, codes and words; the web lab's test area cuts by lasso too, and
  a Stamp without its nib prints a star there (round in the app). See `StampChecks`.

## 3. Spirograph
- **Owner, 2026-10-19:** make it more like the real toy, loops over the old Inner (removed), other geometric oddities,
  and the variables in the Control part so pressure and the rest can drive them.
- **Two phases:** th (the wheel's trip round the ring) and ps (its spin), ps turning q times as fast. ps is summed step
  by step, never computed as q × th, so a setting the pen changes mid-stroke bends the curve instead of making it jump.
- **Curves** (each in both modes, each fits within the size):
  - **Spirograph:** the hypotrochoid, the classic toy. A wheel r rolls inside a ring R, the pen in a hole d from its
    centre: `((R−r) cos th + d cos ps, (R−r) sin th − d sin ps) / R`, q = (R−r)/r. With d < r the loops are smooth,
    no cusps (the first lab put the pen at the rim, d = r, which gives spiky hypocycloids). **Ring teeth** 96 or 105,
    **Wheel** the toy's 24–84-tooth wheels (24, 30, 32, 36, 40, 42, 45, 48, 50, 52, 56, 60, 63, 64, 72, 75, 80, 84),
    **Hole** d/r .2–.9. Default 96 / 40 at 80 %: a 12-lobed rosette in 5 trips round the ring.
  - **Epi:** the epitrochoid, the wheel outside: `((R+r) cos th − d cos ps, …) / (R + 2r)`, q = (R+r)/r. Same dials.
  - **Rose:** the rhodonea `cos(ps) × (cos th, sin th)`, q = k. **Petals** 3–12: odd P is k = P, P a multiple of 4 is
    k = P/2, other even P is k = P/4 (2n petals for k = n/2).
  - **Lissajous:** `(sin(ps + phase), sin th)`, q = a/b. **Ratio** 1:2, 2:3, 3:4, 3:5; **Phase** 0–90°.
  - **Harmonograph:** two damped pendulums, the Lissajous a hair out of tune (+.008) so the figure turns, the amplitude
    `e^(−λ (th − th0))` with **Fades** 3–40 turns to 5 %. In Ring it decays toward the centre as the pen circles; along
    the line th0 is each stroke's start, so the decay resets each stroke.
  - **Coil:** a helix round the line seen 50° from end-on: across the line by cos ps, along it by .77 sin ps, the far
    half (sin ps < 0) in a lighter grey so it reads as a spring (a telephone cord, a Slinky). In Ring the line is a
    circle .8 of the size under a coil .2 of it, wound a whole number of turns (Loops per cm × the circle's length).
- **Ring** (the toy): the touch-down pins the ring there; the pen's angle round the pin is th (unwrapped, nothing
  within 6 px of the pin), so circling the pen turns the wheel and draws the rosette, backwards undoes nothing but
  retraces.
  - **Smooth lines** (owner, 2026-10-19: Lissajous, Harmonograph and Epi "seem pixelated"): th followed the raw
    pointer angle, so a hand's or a mouse's tremble rocked the wheel to and fro, and a mouse's pressure (from speed)
    jumped the size every event. Where th sets the position directly (Lissajous y = sin th, the Harmonograph, Epi's big
    outer term) that showed as zigzags and steps. Now the angle eases over 25 ms and th only goes forward: a reversal
    under 6 px of the pen's arc is a tremble and holds the wheel still, past it the wheel turns back. What the pen
    drives (Size, Wheel, Hole …) eases over 80 ms, and a setting changed since the last point inked is blended in
    along the turn, so it bends the line, never jumps it. Steps are under 1 px, each an antialiased line. The first
    point is inked only when the wheel first turns.
  - Checked with simulated circling, 3 turns, a mouse (whole pixels, pressure from a wobbling speed) and a pen, ±2 px
    jitter: every curve has 0 back-steps and 0 kinks over 35° (before: 85–200 back-steps and up to 440 kinks). **Size** 30–160 px (the ring's radius). While the pen is down the toy is shown in light greys: the ring
  (dashed where the curve has no wheel), the wheel where it is now and a ring round the pen's hole. A tap with no
  movement draws one whole figure (until it closes: 2π × r / gcd(R, r) for the wheels; for the harmonograph until 3 %),
  spun out over about 40 frames with the wheel seen going round. A new touch finishes a spin.
- **Along the line** (the old Loops): the smoothed path walked by length (38 px a cm), th = 2π × Loops per cm × s,
  **Size** 4–40 px. Steps are small enough that the nib never moves more than half a pixel.
- **Control block** (the owner's question): three menus, Pressure, Speed and Tilt, each driving one of Size, Loops per
  cm, Wheel, Hole, Petals, or Nothing. Defaults pressure Size, speed Nothing, tilt Hole.
  - A driven setting is scaled by .4 + .9 × the reading (×1 at about two thirds). Speed reads 0–1.2 px/ms. Wheel drives
    the wheel's teeth (Spirograph, Epi) or the ratio (Lissajous, Harmonograph); a setting the curve lacks does nothing.
  - Tilt comes from PointerEvent tiltX / tiltY, else altitudeAngle (5°–65° of lean to 0–1), added to the Pad's points.
    A mouse gives none, and an unavailable reading leaves the setting as set (the page says so).
  - In the app these would be the Control part's responses. The block is headed "Control" on screen, not "Hand",
    since TEXT-STYLE never shows "Hand".
- **Demo:** the ring pinned on the left and the pen circled five times (the whole rosette), a tap in the middle (a
  whole rose), then a coil along the wave. A pad under 600 px wide has the ring above and the coil along the bottom.
- **Built in the app** (2026-10-20, branch `inkwash-spiro`): Tip choice 11 (`spiro`, shown "Spiro": a Tip choice is 9
  characters at most; dials Size, Mode (Line, Ring), Curve, Loops per cm, Ring Teeth, Wheel, Hole, Petals, Ratio,
  Phase, Fade Turns, Flick; each curve shows only its own) and `SpiroStamper` (gear3, at3, turn3 and trace3: both
  modes, every curve, the ring's tremble guard, its catch-up at the lift and a tap's whole figure, the line through the
  medium's compose). Control's block is the hand's last three dials, **Pressure Sets**, **Speed Sets** and **Tilt
  Sets**, shown in Brush Lab's Control card for a Spiro only (defaults Size, Nothing, Hole; written in a code only when
  changed, so every other code is byte for byte). A Spiro's Medium is 132 px in every style (the line's 22 px each side,
  the ring's 80 px radius, in CSS px; three of the Manta's). Not built: the light grey ring and wheel shown while the
  pen is down, and the spin of a tap (it draws at once). The web lab and Brush Studio have the same choice, dials,
  codes and words. See `SpiroChecks`.
- **Built in the app** (2026-10-20, branch `inkwash-spiroguide`; the owner: a guide, minimal is fine): in Ring, the pinned
  ring as a light grey outline round the touch-down, its radius the stroke's at that pressure (the epitrochoid's ring
  inside its wheel), drawn once over the page and gone at the lift (`MirrorShow.ring`, the mirrors' overlay; never in
  the page, Undo, PNG or PDF), on the drawing screen and Brush Lab's test page, through the fast update as the lasso
  (a session of its own; the stroke's marks keep the gray one, and the lift drives the ring's pixels again). Still not built: the wheel, the pen's
  hole, the dashed reach of the curves with no ring, a tap's spin. The web lab's test area shows the ring too; Brush
  Studio's test page doesn't.

## 4. Crayon
- **Tooth:** value noise 3 px across plus a pixel of grit.
- **Crayon** (radius 3.2–6.6 px): wax goes where `tooth + .35 × wax` is above `1.08 − pressure`, so a light touch
  catches only the peaks and wax already laid lifts the surface (layers build up). Over .75 pressure it also fills the
  valleys, all but the deepest pits, so a hard line goes solid and waxy.
- **The edge** (owner, 2026-10-19: "the edge doesn't have that irregular crayon look"): a wax crayon's, all from noise
  fixed to the page, so a stroke drawn again matches pixel for pixel.
  - Width wobbles ±10 % along the stroke (noise ~22 px across, read at each dab).
  - The outline at each pixel is .8–1.3 of the dab's radius, from a ragged map (notches ~7 px apart, a waver ~2.5
    px, a pixel of grit), so the edge wavers, notches and skips.
  - Toward the edge (past .6 of the radius) the tooth threshold rises by up to .22: only the peaks take wax, so the
    edge breaks up more than the middle. Pressed hard the middle fills solid; the hard fill fades out toward the edge,
    so it stays broken.
  - Flecks: just past the edge (to 1.35 × the radius) a high grain (tooth over .62, one in ten) catches a speck.
- **Wash over:** soft wide dabs toward a ceiling of .36–.61, × `1 − clamp(1.6 × wax)` (wax resists), settling more into
  the paper's pits.
- **Scratch:** a 0.7–2 px blade that takes the wax off, with a ragged edge. Now and then a crumb of wax lands 1.5–3.5 px
  to the side.
- **Page:** White, or Black (the page is solid wax, the tool turns to Scratch, rainbow-scratch-paper style). Changing it
  starts the page fresh, as Clear does.
- **Built in the app** (2026-10-20, branch `inkwash-crayon`): Medium choice 7 (`crayon`; dials Wax, chalky to waxy,
  default .6, and Scratch, Off or On). `Crayon` holds the page-fixed tooth, ragged edge and wobble, the same for every
  crayon. The line holds how hard it presses (the edge's falloff and flecks in it, `LineStamper`); the compose lays the
  wax over what is there, so layers build up (eight dabs' worth at once). At the lift its wax goes on the page's masking
  fluid, all kept (`layWax`, as a wax resist's), so washes slide off it; a crayon stroke takes on wax, so it builds up
  over its own. Scratch is a blade a third as wide, laid with Blend's White and a ragged edge; no crumbs. No Black page:
  cover an area in black crayon, then scratch. Its Medium is 30 px in every style. No smudge, never on water, never
  rolled. The web lab and Brush Studio have the same choice, dials, codes and words. See `CrayonChecks`.

## 5. Shifting swatches
- **Greys** (owner: "the selection of shades can be completely open, or one of the toolbar ones"):
  - **Any:** all 16 panel greys, the swatch row showing all 16 small (a row of up to 44 px cells that shrinks to fit a
    phone). Paper white starts out of the set, since a pen that only darkens can't show it; a tap takes it in.
  - **Toolbar:** the bar's three, black, grey and light grey (panel levels 15, 8 and 5, from `ToolSwatches.GREYS`:
    ink, `INK_GRAY` 120 and `INK_LIGHT_GRAY` 175), as three full swatches. The default.
  - Each keeps its own set. A tap takes a swatch in or out of the set (at least one stays in); out of the set it shows
    small with a grey edge. The one in use has a 5 px ink tab under it, as the bar marks its grey.
- Each touch-down picks a grey from the set, by the **Rule** row, with either palette:
  - **Random:** any, repeats allowed.
  - **From a set:** a shuffled deck, each once before any again, never the same grey twice across a reshuffle.
  - **Pattern:** In order, or Ping-pong (1 2 3 4 3 2 …).
- The pen is a plain ink line (radius .8–2.2 px), darkening only. The demo writes two lines of looped "words", one touch
  each; on a narrow pad a word too long for a line is left out (it used to end the demo after one word).
- **Built in the app** (2026-10-20, branch `inkwash-swatches`): a Control option, so a brush carries it, any brush.
  **Grey Shift** (Off, Random, From a Set, Pattern; "Shifting Greys" was two lines in the Control card) with a
  Pattern's **Order** (In Order, Ping-Pong) and **Greys** (Toolbar, Any): the hand's last dials, written in a code only
  when on, so every other code is byte for byte; kept when a Control preset is swapped; never rolled. Toolbar is the
  bar's grey swatches as they are now, four since 2026-10-19 (not `ToolSwatches.GREYS`' first values); Any is the
  panel's 16, paper white left out at first. Each palette's set is kept in the hand as the greys left out (`sbar`,
  `sany`, `sany2`), shown in Brush Lab's Control card as the swatch row (full with an ink edge, or small with a grey
  edge; the last stays in). `SwatchPicker` picks at the touch-down (`InkWash.stroke`, Fill's tap, Brush Lab's and the
  Tools page's test page), each holder in its own turn (`HeldPaint`). A toolbar grey picked becomes the slot's grey, so
  the bar marks the grey in use once the stroke ends (its faces only, only when it changed: the e-ink stays still while
  drawing). "This brush": "Greys shift each stroke at random." (", shuffled.", ", in order.", ", there and back.").
  Not built: the in-use tab under the lab's swatch row. On the Nomad a Spiro's Control card has no room for Grey
  Shift's rows. The web lab (its three shade swatches for Toolbar) and Brush Studio have the same dials, codes and
  words. See `SwatchChecks`.

## 6. Mirrors
- **Mirror:** the stroke shows light grey while drawn; on the lift its principal axis becomes a mirror line, dashed in
  dark grey across the pad. Under 24 px long: "Too short".
  - The angle snaps to 15° steps within 3°. The line moves to pass through a crossing of mirrors, or the pad's centre,
    when it passes within 12 px of one, so kaleidoscopes are easy to make by hand.
  - A fifth mirror replaces the oldest.
- **Draw:** every stroke is drawn once per map in the group the mirrors make, found breadth first (reflections, then
  reflections of reflections), capped at 24. The badge shows "3 mirrors, 6 copies"; "copies max" when the cap cut it off
  (non-dividing angles, or parallel mirrors, whose group never ends).
- **Kaleidoscope:** three mirrors through the centre at 60° (6 copies), and the mode goes to Draw. **Clear mirrors**
  removes them; Clear removes only the ink.

## Checked (Chrome, this Mac)
- The script passes `node --check`, and there are no console errors on load or through every chip, every dial at both
  ends, every menu option, every button, every swatch and Replay in every mode, in both palettes and every curve.
- Phone width was checked in 375 px and 320 px iframes, with every demo played: no horizontal scroll, the dials, the
  Control menus and the 16 small swatches fit.
- The canvases read back as the 16 greys, plus under 0.1 % of pixels off by one (the browser's readback noise, as in
  the first lab).
- Section 1: a soft line drawn at full pressure across a crisp box lets a full-spread wash through to the other half;
  the crisp box holds it. The demo's middle band stops at the crisp wave and floods past the soft one.
- Section 2: lasso cuts (and "Nothing to cut"), taps that print lighter until a 1.2 s rest, prints at 120 % fading one
  by one, a 10 % trail, Turns on and off, Size and Spacing at both ends.
- Section 3: Ring drawn by circling pointer events (five circles close the default rosette), with the guides shown
  mid-circle; a tap draws each curve's whole figure; every curve along the line, with a real mouse drag too; pen
  events with tilt through every menu choice.
- Section 5: both palettes through every rule; Pattern runs 15 8 5 and ping-pongs 15 8 5 8.
- Third round (2026-10-19), Chrome on this Mac, the page as published (no console errors, every demo played, Replay
  in every section, Crayon on black):
  - Section 1, buffers read back after a full-spread wash: a ring at pen pressure .05–1, with and without a 4 px gap, a
    box, a triangle and a wobbly loop: no pixel of the region left dry and no dry pixel (paper or a light line edge)
    touching the wash. Gaps of 4 and 5 px held (nothing wet outside). At Spread 5–50 % no wash stopped short beside a
    line. The canvas read back matches the buffers.
  - Section 2: six taps, Ink fade None, all the same darkness (sum of tone 636–641); 22 % fades as before, 639 → 36.
  - Section 3: the simulated circling above, every curve, and with Speed driving the wheel and Pressure the hole.
  - Section 4: the same stroke drawn twice on a fresh page matches exactly.
  - No real mouse drag in a circle: the browser tool drags only in straight lines.
- Fourth round (2026-10-19), section 1, Chrome on this Mac, buffers read back after a full-spread wash:
  - Leaves of two arcs meeting at points of 10°, 12°, 15°, 20°, 25° and 30°, meeting exactly or crossing 10–15 px
    past, pen pressure .3, .7 and 1, lines straight or wobbling ±1 or ±2 px (108 leaves): no dry pixel in the open
    leaf, every pocket wet, nothing wet outside. Two exceptions, both a 10° leaf at pressure .3 wobbling ±2 px: one
    light pixel (ink .40) shut deep in the merged lines. Before the change, a 10° leaf wobbling ±1 px left 5 px of
    paper 28 px from the point; ±2 px, 142 px.
  - V shapes of 10°, 20°, 30° and 60°, one stroke or two: none dry in the V.
  - The demo's ring with a 4 and a 5 px gap (centre to centre) at pressure .05, .7 and 1, and with open gaps of 3–4 px:
    holds. Two arcs of a circle (separate pieces) 1–5 px apart, and a box lid 2–4 px short of its sides: hold.
  - A ring, box, triangle and wobbly loop at pressure .05, .4 and 1: nothing dry.
  - Region finding, median of 15 interleaved runs: demo ink 17.5 ms (before 22.6), a 10° leaf 17–19 ms (before
    12–13). The distance to the lines now spreads only from line-edge pixels, which pays for the gap test. The first
    wash after a page load is several times slower while the browser compiles it.
  - No console errors; the demo plays as before.
- Restarting demos mid-play leaves every pad in the tool, mode and spacing it had.
- Not tested with a real pen on a tablet: pressure and tilt were only exercised through synthetic events and replay.

## App port sketch
- **Stay in the lines:** a Wash medium flag "bounded". On touch-down, flood the ink layer's region once (at page
  resolution, in tiles), then clip the wash's dabs and spread to it. The bucket-queue spread maps onto a short wet
  ticker, like Living Ink's.
- **Stay in the lines' walls:** read the ink layer's crisp marks only; the wash layer (and any soft medium) never
  makes a wall.
- **Stamp:** a Tip choice, "Stamp", whose nib is cut from the page by lasso into a small grey bitmap. The tip samples
  it every Spacing, turned by heading when Turns is on, with a per-print load (Ink fade) and noise seed.
- **Spirograph:** a stroke modifier on the resampled points, before the tip: Ring maps the pen's angle round the
  touch-down to th; Along the line maps arc length. Its Size, Loops per cm, Wheel, Hole and Petals become targets for
  the Control part's responses (pressure, speed, tilt), beside Size, Dark and Nib.
- **Crayon:** a Medium beside Charcoal, with its tooth threshold rising with wax laid; Resist already covers "Wash over".
  Scratch is an eraser variant with a blade and crumbs.
- **Swatches:** a per-slot grey rule (Fixed, Random, Set, Pattern) over Any (the 16) or Toolbar (the slot's three),
  picked at touch-down.
- **Mirrors:** page-level guides. Strokes are fanned out per map at input, so each copy is a real stroke and undo takes
  the group.

## Risks
- **The region fill is page-sized work on every wash touch-down** (here ~18–28 ms at 945×360 with the demo's ink,
  the distance to the lines and the gap test included). On the Manta's page it needs tiles and a cache of the wall mask and distances,
  rebuilt only when ink changes.
- **Gap closing is fixed at about 4 px.** At 300 ppi that is a third of a millimetre; it may want to scale with the pen.
  It is only between ink far apart along the ink, so a tiny loop (radius 12 px or less) with a 4 px gap leaks, and a
  very thin wedge whose sides wobble into each other can shut its far end off (then filled as a pocket if thin).
- **The stamp is a screenshot of ink**, so a stamp cut over a wash takes the wash too. A Cut that takes only ink, or only
  the top layer, may be expected.
- **Spirograph loops wider than the pen move quickly cover the page in one stroke.** Loop size may want a lower ceiling
  on the device.
- **A Ring tap draws a whole figure at once** (a harmonograph is ~90 radians of curve): on e-ink the spin may want
  fewer, larger steps.
- **The Spirograph along the line smears the rosette,** since the figure needs several trips to form while it rides;
  with the default 40-tooth wheel it reads as a chain of star points. Bigger wheels loop more.
- **Mirrors with 24 copies multiply ink work by 24**, and copies off the page still cost. The app should cull them.
