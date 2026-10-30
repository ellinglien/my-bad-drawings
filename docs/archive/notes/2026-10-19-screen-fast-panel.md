# Screen and the panel's fast update (2026-10-19)

The mischief modules spec's speed experiment (Stage 1): a screened mark is pure black and white, so could it draw
through the panel's 1-bit fast update while the pen is down and still be exactly what you get? Found from the code and
the earlier bench findings (`StrokePanel`, `DirectEink.endFast`, `app/src/main/cpp/panel_end.h`, `docs/DIRECT_GRAY.md`);
nothing here was run on a tablet.

## What the app does today

- Every pen stroke reaches the panel through the canvas's gray session (mode 7, flags 0): about 270 ms of panel time an
  update, whatever its size. On firmware 3.30.45 on, gray frames queue on the Manta too.
- The fast session exists on the bench only (`StrokePanel.wants`, since 2026-10-16): driver mode 5 on the main plane on
  the Manta before firmware stamp 2610091001 (about 145 ms an update), else the firmware's own pen request, mode 9 on
  plane 0, where `DirectEink.fastBinaryControls()` (the color bar's marker, which follows the pen).
- It stayed on the bench because of two panel findings: a live stroke changes the pixels under its nib again and again
  and a run of fast updates leaves them part-driven (a Slow wash ghosted in rings); and at lift the driver passes by a
  pixel whose final is the value the fast update recorded, so those keep their ghosts. `endFast` drives them again with
  two gray updates back to back, and the stroke blinks for about a quarter second after the lift.

## What a screened mark changes

- **Each pixel changes once, to black.** The compose writes a screened pixel black where the mark's own darkness over
  white passes the page's threshold there, else leaves it as the gesture found it. With Instant or Slow drying the
  darkness under the pen only grows (Screen turns off settling, gravity, dust and Mold), so a pixel goes from what was
  there to black once and never back. Quick drying's wet window re-tapers its tail under the pen and can take a dot
  back; a smudge moves what is under it.
- **The final is what was shown**, for a live stroke: nothing lays a grey after the lift. A quick stroke (a big brush,
  `QuickStroke`) previews the screen's dots from its flat mark and lands the textured mark's at lift, so some dots
  change then: about 23% of a big wash's final dots (its preview at `QuickStroke.SCREENED_BODY` of its glaze, since
  `inkwash-screen2`; 29% before), 2% of an ink line's. Since then a second stroke builds on the layer's print tone,
  so it inks more pixels over the first, still each once.
- **The pixels are exactly black or untouched**, so mode 9's converter, which writes only exact black and white and
  leaves every other pixel as it was, carries the whole stroke: a shaded page's grey and other marks under it are never
  touched.

So the engine's side is WYSIWYG for a live screened stroke. What isn't known is the panel's side: whether its fast
update drives those blacks fully. Every inked pixel is exactly the case `panel_end_ask_again` asks again (the fast
session changed it, and the final keeps it), so with mode 5 the end still needs `endFast`'s two updates and its blink,
as quick strokes did. Mode 9 strokes have not been measured: the pen request drives the color bar's marker crisply, and
if it drives a stroke's dots the same way, the lift could end with one plain update.

## The bench switch (default off)

`StrokePanel.bench = "screen"` puts every screened stroke (Screen or Tone, live or quick; `LiftingStroke.screened`) on
the fast path and ends it through `endFast`; every other stroke draws as the app does. Nothing changes for the app:
`bench` is null outside the bench. `LiveStrokeBench` takes it as the panel mode `screen`, and a style name with
`+halftone` gives that roll a halftone Screen:

```
LIVE=1 scripts/bench_device.sh -e cases "Marker+halftone:none:24:1:screen,Marker+halftone:none:24:1:screenfast,Marker+halftone:none:24:1:gray,Ink wash+halftone:none:120:1:screen" -e where pad
```

Each case prints the panel's time after lift and its frames. What to look at, on the Manta, firmware before and after
2610091001 (mode 5, then mode 9):

1. While drawing: do the dots appear under the pen at once, crisp black, with no trail?
2. At lift: does the stroke blink (the end's two updates), and does it end exactly as drawn, with no ghost left after a
   few seconds (compare a full refresh)?
3. The `screen` case's panel time after lift against the `gray` case's.

If mode 9 shows the dots crisp and an end with one plain update leaves no ghost (the panel mode `screenfast`: the
same stroke ended with one plain gray update), a screened brush could take the fast path in the app, live strokes with Instant
drying first.
