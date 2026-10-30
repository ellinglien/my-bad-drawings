# Mischief Lab: notes

Prototype: `mischief-lab.html`, one file with no external scripts and no stylesheets. iA Writer Duo 400 and 700 are embedded as base64 `@font-face` from `app/src/main/res/font/` (the same way `studio-src/build.cjs` does it). The page has no `<!doctype>`, `<html>`, `<head>` or `<body>`, because the publisher wraps it. It starts at `<title>`. No app code was changed.

Shared by all four pads:
- **One canvas pixel per CSS pixel**, drawn with `image-rendering: pixelated`, so a 1-bit pattern stays crisp.
- **Every tone shown is one of 16 greys** (`255 − 17k`), as on the panel. Screen's 1-bit modes write only 0 and 255.
- **Input:** pointer events with coalesced samples.
  - A pen's `pressure` is used as it comes.
  - A mouse gets pressure from speed: `1.08 − 0.38 × px/ms`, clamped .22–1 and eased over ~20 ms.
- **Demo strokes:**
  - The app's swoosh, `WildSample.swoosh`, ported with the same points, pressure curve and timing.
  - The app's wave (`WildSample.wave`, SWELL) for writing on the next page.
  - A back-and-forth scribble for laying screentone.
- Each demo plays once, the first time its pad is 60 % in view, and Replay plays it again. Drawing stops a replay.

## 1. Screen (the one that matters)
- **The brush lays greys as usual, and the screen applies at composite time.**
  - Strokes keep their tone buffer.
  - The screen is a per-pixel threshold map, rebuilt when a screen dial changes. A pixel is ink when tone > threshold.
  - So switching screens re-renders the same strokes and never touches them. In the app this is a `ToneDocument` → panel step, not a brush part.
- **Base brushes:**
  - Soft wash: soft dabs ease toward a pressure-set ceiling of .3–.75, modulated by tooth.
  - Ink line: an antialiased round, .55–1 dark by pressure.
  - Charcoal: catches the tooth's high points, more of them under pressure.
- **Screens:**
  - Halftone: Euclidean spot `.5 − (cos 2πu + cos 2πv)/4`, with Angle and Cell. Tone sets dot size, and pressure darkens through the brush.
  - Line screen: `|frac(v) − .5| × 2`, so line width follows tone. A slight sine swell in the lines reads as engraving.
  - Dither: Bayer 4×4 or 8×8.
  - Grain: white-noise threshold in 1–4 px blocks.
  - Screentone: a separate **fill** layer scribbled over an area. Pattern is Dots, Lines, Sand (per-pixel noise) or Gradient (dots ramping down the page, as cut from a gradient sheet). Tone is the sheet's density. Pen lines under it stay solid (tone > .5).
- **Scrape** (all screens): a blade, not an eraser.
  - Each dab multiplies fill and tone by `1 − f(.08 + .5n²)`. `n` is noise in streaks along the stroke direction.
  - So one pass thins the dots unevenly and repeated passes clear the area. It reads like a scraped tone.
- **Newsprint and riso:**
  - Misregister: a second pass at 45 % tone, offset by (0.8, 0.6) × the dial, OR'd in.
  - Ink gain: a ragged 1 px dilation, likelier with more inked neighbours.
  - Uneven: low-frequency blotches thin the tone, plus speck dropouts where coverage was thin.
- **Why it matters:** the output is pure 1-bit, so the panel's fast black-and-white waveform (A2/DU) could draw screened marks at once without a GC16 grey refresh. On-screen wording: "1-bit: every pixel is pure black or pure white."

## 2. Flick and fling
- **Flick:** happens when the pen lifts while it's still moving (last move < 40 ms before the lift, speed > .35 px/ms).
  - Velocity is taken over the last 30 ms, and the turn rate (rad/px) from the 30 ms before that.
  - The stroke carries on for `carry × speed × 85` px (max 260), keeping its turn and easing out (`1 − (1−e)^2.2`) over 150–300 ms.
  - The width tapers as `(1 − u)^.75`, and bristle streaks along the heading drop out after 30 % of the way, so it ends dry.
- **Fling:** a hard stop, meaning a peak speed > 1 px/ms in the last 140 ms followed by 45 ms with no movement.
  - The silence is the only sign: a mouse sends no events once it stops, so a per-frame watcher spots it while the pen is still down.
  - Ink pools a little at the stop.
  - Then `amount × (1.5 + 2.2v)` drops fly ahead within about ±13°, with distance and size scaled by speed. Nearer drops land first.
  - Each drop is an oval along its flight, and 40 % of them get a speck beyond.
- **Physical, not gimmicky:** nothing happens on slow strokes or slow lifts (checked: a 0.25 px/ms line adds no pixels). Both effects are seeded from the stroke, so they don't vary at random.

## 3. Fading ink and lemon ink
- **What each pixel keeps:** its ink, the time it was laid and which ink it is, so both inks share a pad. The pad re-renders whole at 4 Hz until nothing is still changing, then stops.
- **Fading** (Fades in 10 s–2 min, default 20 s):
  - Life = `fade × (.35 + .65 d) × (.75 + .5 mottle)`. Thin ink and damp patches go first, so it breaks up the way a Buddha Board does.
  - Ink shows full until 55 % of life, then thins to nothing, and the pixel is freed.
  - Drawing over fading ink refreshes it.
- **Lemon** (Dries in 1–10 s, default 4 s):
  - Laid at 10 % (a faint trace), then darkens in to 88 % with a smoothstep. The drying time varies ±30 % by mottle, so it browns unevenly.
  - Fresh lemon over dry lemon keeps the dry time.
- **Stepped:** 4 Hz is close to how often the panel takes a partial refresh, so smooth animation would be wasted. The page says so in a note.

## 4. Bleed-through and carbon
- **This page:** a felt marker, radius 5–8 px. It keeps three buffers:
  - ink;
  - soak, which builds more on slow strokes, and a marker held still keeps soaking and spreads its dot up to 1.5×;
  - pressure.
- **The next page's ghost** is recomputed from those three buffers whenever this page changes.
  - Bleed-through: soak blurred twice (box r 3) plus a little blurred ink, mirrored left to right, × strength × .62, with mild paper mottle.
  - Carbon: the right way round. Coverage is `(pressure × .75 + .2 − tooth) × 3` against the next page's tooth, × strength × .5, so harder marks take more and it stays grainy.
- **The next page takes a fine pen.** Its ink composites over the ghost by multiply (`1 − (1−a)(1−b)`). The mode toggle and Strength re-render existing marks.

## Checked (Chrome, this Mac)
- The script parses (node `new Function`), and there are no console errors on load or through every chip, every dial at both ends, every screen with Replay, and all four demos.
- Phone width was checked in 375 px and 320 px iframes: no horizontal scroll, the pages stack, and the dials go to one column. The browser window itself could not be resized narrower.
- In 1-bit modes the canvas reads back as only black and white, plus ~240 pixels off by one in a single channel. A blank test canvas shows the same, so it is the browser's readback noise, not the page.
- With synthetic mouse events: a slow lift adds nothing, a moving release flicks, a dead stop then lift flings.
- Lemon's mean darkness was 33 / 146 / 190 at 0.3 / 2 / 6 s. Fading at 10 s was empty after 10 s.
- Not tested with a real pen on a tablet: pressure was only exercised through replay.

## App port sketch
- **Screen:** a per-brush `SCREEN` setting (none, halftone, line, dither, grain) plus Angle and Cell, applied where tone tiles reach the panel buffer.
  - Screentone is better as its own tool (Fill + Scrape) than as a brush part.
  - Marks that are fully 1-bit can take the fast waveform.
- **Flick and fling:** in `WildInkStroke.finish()`, from the last samples. The fling's stop test needs the "no samples for 45 ms while down" timer, because the pen keeps reporting, but with near-zero motion.
- **Fading and lemon:** they need a per-pixel birth time (shorts in 64 px tiles), a 4 Hz ticker like `LivingSession`, and a policy for undo and page change (fast-forward, as Living Ink does).
- **Bleed and carbon:** compute the ghost when the next page is opened, from the previous page's ink, soak and pressure, as a read-only underlay layer.

## Risks
- **Fading ink is real data loss.** It needs a clear sign and an exclusion from autosave decisions. Maybe it only lives in a scratch page.
- **Screens at 1 px cells alias with the panel's own pixel grid.** Cells under 4 px should be tested on the device.
- **Misregister and gain are per-pixel hashes,** so they are stable, but they move if the canvas origin moves. They should be fixed to page coordinates in the app.
- **Fling detection on a pen:** the stylus keeps sending samples when still, so "stopped" there means speed under ~.05 px/ms for 45 ms, not silence.
