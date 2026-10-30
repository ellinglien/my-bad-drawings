# Mischief Lab 3: notes

Prototype: `mischief-lab-3.html`, built like `mischief-lab-2.html`: one file, no external scripts, iA Writer Duo 400 and 700
embedded, one black-on-white theme with every colour set, every tone one of 16 greys, the `Pad` class (pressure, tilt,
replay), no `<html>`, `<head>` or `<body>`, no `alert()`, `confirm()` or `prompt()`. Each section has Clear and Replay and
a demo that plays once when its pad is 60 % in view. The goal is "useful but delightfully unpredictable", a slightly
haunted, magic journal. No app code was changed. Labels follow `docs/TEXT-STYLE.md`.
Tested in Chrome: no console errors, every control tried, demos run. Not tested at a real phone width (the window would not resize).

## 1. Record and Replay
- The pad logs what the pen sends. Replay clears the page and feeds the log back through the same drawing code at its
  real timing (waits between strokes, holds before a lift, pauses inside a stroke). Chips 1×, 2× and 4× scale the timing.
- **Log format** (what the readout measures): version byte, stroke count, then per stroke brush id, size, grey, wait ms,
  hold ms, sample count, and per sample a zigzag varint step in half pixels for x and y, a varint step in ms, and one
  pressure byte. The demo (3 strokes, 284 samples) is 1.1 KB raw and 0.7 KB deflated (`CompressionStream`, deflate-raw).
  Replay decodes the bytes first, so what plays is exactly what would be stored.
- **In the app:** one sample log per page, in the drawing's file beside the page's pixels. Each stroke keeps brush (the
  brush code), size and grey. Replay works only because the engine is deterministic: the same samples, brush and grey
  make the same marks. Nothing random may run in a stroke without a seed kept in the log (the lab's noise is fixed to
  the page). A page's log is a few KB, so keeping every page's is cheap. Erasing and undo would be log entries too.

## 2. Flipbook
- 3 to 6 pages, each its own pad. Onion skin shows the page before at a quarter of ink strength (grey level 4),
  under the page's own ink. Editing a page redraws the next one. Play steps through the pages at 2, 4, 8 or 12 fps on a
  stage below, loops until Stop (the demo plays three loops). Remove Page takes the last one.
- In the app, a flipbook would be a notebook mode: the page bar's Play, with the notebook's pages as frames.

## 3. Daily Prompt
- 32 cards of our own, written in the style recorded in `docs/archive/notes/2026-10-19-owner-ideas.md` (short, timed, image-first,
  about the day, simple shapes, no skill). Timed ones are 30 s, 2 Min or 3 Min. 4 are untimed. Two are Diary Page cards.
- Today's card is picked by the day number, so it is the same all day. Deal Another picks a different one. Start counts
  down on the card (the clock changes once a second, with a bar). At 0 the card inverts and says Time.
- **Diary Page:** the pad shows boxes for Did, Saw and Heard on the left and a large Drawing box, in grey so ink reads on top.
  In the app this would be a page template.

## 4. Sigils
Detection is geometry on the stroke's points, run on each lift. No model.
- **Loop:** over 60 px long, over 24 px across, its ends within a fifth of its length. Overshoot past the start is cut.
  Circularity 4πA/P² on 64 resampled points: over .88 is a circle. Otherwise corners (Douglas-Peucker with 9 % of the
  box diagonal, turns under 40° dropped): 3 is a triangle, 4 a square. Anything else is ignored. In 40 noisy trials of
  each, circle 38 of 40, triangle and square 40 of 40.
- **Mark:** a later stroke whose box centre is inside a waiting loop and under .9 of its size. A dot if tiny. A line if
  over a quarter of the loop's diagonal and straight (chord over .8 of length). Anything else is ignored.
- **Casts:** circle with a dot, triangle with a line, square with two crossing lines (segment test), and a spiral on its
  own (an open stroke that turns over 1.5 times about its centre while its radius changes steadily, correlation over
  .85). A loop with no mark waits, and nothing happens.
- **Blooms** are lists of short segments drawn in order over 0.85 s, so growth is the bloom: a hypotrochoid rosette from
  the centre (5 gear ratios picked by the stroke), jagged branching cracks from the line's ends and middle that stop
  at the triangle's edge, a basket weave of striped squares swept in from a corner, and veins that wander and branch
  outward by smooth noise (Mold's look, no timing). Seeded from the stroke, so replays match.
- Limits: loop first, then the mark. A square's cross is two strokes.

## 5. Echo Ink
- On a lift a timer starts (Delay, 0.5 to 5 s). Then a second hand walks the stroke's samples at their own timing,
  each moved by one fixed offset (Offset, 0 to 40 px, direction random per stroke) plus smooth wobble that grows with
  it. It draws thinner on its own layer at a grey set by Faintness (0 to 90 %, higher is fainter). Strokes may echo
  while the next is drawn. Clear cancels waiting echoes.

## 6. Tilt-Reveal Ink
- Ink goes to a hidden layer. Each pixel has a threshold (patchy noise, .1 to .9 of the range) and shows once the tilt
  passes it, so marks surface in patches. Pixels within .07 of their threshold flicker every 180 ms: the shimmer. It
  redraws only while the tilt is between 3 % and 97 %.
- Tilt is 0 to 45°. The Tilt dial works with a mouse. `deviceorientation` moves the dial by the change in beta and gamma
  since the first event (Set Level re-takes that). iOS needs a tap first (Use Device Tilt). While drawing, the stroke
  shows as a faint ghost that vanishes on the lift.
- In the app, the device's tilt comes from the same sensor as page tilt (`docs/TILT_INPUT.md`), and the hidden layer is
  an ink layer with a reveal curve.
