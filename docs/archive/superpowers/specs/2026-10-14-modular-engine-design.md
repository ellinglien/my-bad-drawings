# Modular brush engine, Stage 1: design

Agreed with Elling on 2026-10-14: "Brush engine: the six-part brush and new
share codes in the app, using the current lab screen. Your existing brushes
must draw exactly as they do now, and the new tips and mediums appear in
Randomize and the styles."

The reference implementation is the Modular Brush Lab prototype, committed as
`docs/brush-lab/prototypes/modular-lab.html` with its notes in
`modular-lab.NOTES.md` (read its "Porting to Java" section). Plan Task 1
extracts its sources into `docs/brush-lab/prototypes/modular-lab-src/`
(`parts.js`, `engine.js`, `stroke.js`, `port.js`, `oldmodel.js`, `checks.js`,
`ui.js`); every reference below is to those files and their line numbers.
Port them; don't redesign them. Where the app differs from the prototype, this
spec says so and why.

- **Stage 1 (this spec):** the six-part brush, M share codes, the new tips and
  mediums drawing live on the e-ink page, Randomize, styles and variations
  making six-part brushes, the current lab screen adapted.
- **Stage 2 (later):** the new lab screen: part cards, Simple and Advanced,
  toolbar slots, Surprise me, Load beside the name, Manta and Nomad layouts.
- **Stage 3 (later):** bristles, Carry (lift, drag, smudge), graphite's Fill
  plane, plumes ("takes ink"). See "Stage 3 notes" under Deferred.

## Why

Today a brush is a pen or a paint with a flat list of dials, and the two kinds
can't meet. The prototype splits a brush into six parts that combine freely,
so a split nib can carry a wash, a hake can lay gouache, charcoal can sit on
canvas. Stage 1 brings that engine into the app without touching the brushes
Elling already has.

## What Elling sees after Stage 1

- **Randomize, the style chips and a slot's dice make six-part brushes.** The
  style chips are the prototype's 14: Fineliner, Brush pen, Dip pen, Marker,
  Pencil, Charcoal, Watercolor, Ink wash, Gouache, Oils, Pastels, Dry brush,
  Hake, Anything goes. "Wild" is called **Anything goes** everywhere it shows.
- **New tips:** round, chisel, flat nib, hake (a wide flat head swept as a
  ribbon, its head following the stroke or fixed, pivoting on tight turns),
  split nib, multi-line (2–5 lines).
- **New mediums:** ink (with Wet, which loosens the line into a wash, and line
  placement centre, outline or squiggle, Offset and Chaos), wash, cover
  (gouache and oils: opaque, light covers dark; impasto lit from the upper
  left), charcoal (Spread and Hold, its own stick grain), graphite (6H to 8B).
- **Papers:** plain, cold press, laid, felt, crumpled, canvas, with Tooth.
- **Life:** None (final under the nib), **Fast-wet** (the default: only the
  last few millimetres behind the nib develop while the pen is down; nothing
  changes at lift) or **Drying** (settles at lift, then the wash keeps settling
  for its drying time, 0.75–20 s).
- **Hand:** pressure, tilt and speed each move size, darkness and width/angle
  (a 3 × 3 grid of amounts), with a Response gain and "fast is thinner or
  thicker". **Tilt works**: leaning the pen widens, lightens or turns the nib
  when the brush's hand says so.
- **Share codes** `name.M…` that the web lab and the app both read and write.
  Old `name.I…` and `name.P…` codes still load, as the old brushes they are.
- The lab shows a plain-language **"This brush"** description above the
  settings ("A thick opaque paint from a flat nib, on canvas. …").
- Every existing brush (the built-ins Ink, Fude, Wash and Watercolor, and every
  Wild pen or paint in a slot or the library) draws exactly as before, pixel
  for pixel, and keeps its current settings rows in the lab.

## Decisions (for Elling)

1. **Two engines, on purpose.** Old brushes (built-ins and `I`/`P` Wild
   brushes) keep drawing through today's code (`InkStroke`, `WildInkStroke`,
   `WashStroke` with `WashLook`), which Stage 1 does not change. Six-part
   brushes draw through a new engine (`ModularStroke`). This is the only way to
   promise pixel-identical old brushes cheaply: the prototype matches the old
   engine on blank paper, but the app's old strokes also have wet-on-wet
   joining, pen grooves and edge bands the prototype never ported. An old brush
   is never silently converted; it stays an `I`/`P` brush until Elling replaces
   it.
2. **Old brushes keep their own lab rows.** Opening an old brush in the lab
   shows today's settings rows (nib, texture, pressure, …). The style chips,
   Randomize and the slot dice make six-part brushes from then on: picking a
   style or randomizing an old brush gives a new six-part brush in the style of
   the same name (old "Wild" → Anything goes). Changing a built-in still makes
   an `I`/`P` copy ("my-ink"), as today.
3. **Codes accepted:** `M` codes (the prototype's format exactly) and the app's
   `I`/`P` codes (exactly as today). The prototype-only codes the web lab also
   reads (pen-paint-blend `B` codes, brush-experiments and marble-and-bristle
   tails, lab v2's `.x` suffix, living-ink tails) are **not** read by the app.
   They exist only in throwaway prototype pages, no app brush uses them, and
   the web lab already converts any of them: paste it there, copy the `M` code,
   paste that in the app. That saves about 150 lines of decoders and their
   checks.
4. **Deferred parts decode and are kept, but draw as fallbacks.** A code with a
   Bristles tip draws as a Round tip with its Weight and Dry brush equal to its
   Dryness. Carry Lift, Drag and Smudge draw as Off. Takes ink and Plume are
   kept but nothing plumes. Graphite draws without its Fill plane (each stroke
   meets fresh tooth). Their values survive a round trip, so Stage 3 draws them
   properly without new codes. Randomize, the styles, the dice and variations
   never pick a deferred choice in Stage 1 (one switch per feature,
   `ModularFeatures`), so a rolled brush never claims to do what it can't.
   Styles that would roll them (Oils' Drag, Gouache's Lift, Pencil's and
   Charcoal's and Pastels' Smudge, Dry brush's Bristles) roll Off or another
   tip instead.
5. **Carry is deferred rather than cheap.** In the app a stroke paints one
   layer of a pair, and the page underneath is several layers; a smudge that
   picks up ink from the ink layer while painting the wash layer needs a
   cross-layer design. That belongs with Stage 3.
6. **Which layer a six-part brush paints:** ink, charcoal and graphite on the
   pair's ink layer; wash and cover on its wash layer. An ink line with Wet
   draws its wash body on the ink layer too, with its line (one stroke, one
   layer, one undo step). Cover paints with coverage (alpha), so light gouache
   covers dark on its own layer and the layers below; it can't hide the ink
   layer above it in the same pair.
7. **Sizes:** a six-part brush's Medium (M) size is its style's size from the
   prototype (Fineliner 4 px, Brush pen 8, Dip pen 6, Marker 12, Pencil 4,
   Charcoal 30, Watercolor 120, Ink wash 8 for ink or 90 for wash, Gouache 60,
   Oils 60, Pastels 24, Dry brush 60, Hake 180). Anything goes uses the app's
   own Medium sizes: 8 px for ink, charcoal and graphite, 120 px for wash and
   cover. Small is 0.4 × M, Large 2 × M, never under 1.5 px or over 360 px.
   Stage 2's toolbar slots replace this.
8. **Shades:** the three shades are the darkness the prototype's swatches use:
   Dark 1, Light 1 − 120/255 (0.53), Lighter 1 − 175/255 (0.31), the app's ink
   greys. Washes glaze at 0.34 of that, as Wild paint does today.
9. **Drying's span** is the prototype's 0.75–20 s for six-part brushes. Old
   paint brushes keep their 0.75–5 s: they never reach the new engine.
10. **Drying settles after lift on the drawing screen and the lab's test page**
    (ten steps a second, on the UI thread, between strokes). Any new stroke
    first deposits whatever is still settling where it lies (one quick pass),
    so the next stroke sees a finished wash; Undo, a fill, a layer change or
    leaving the screen stop it where it is. A Drying stroke and its settling are
    one undo step. Samples and tiles show a Drying brush settled at lift only.
11. **Randomize rolls the Hand with the style**, as the prototype does. Locking
    the Hand part keeps it.
12. **The lab's column for a six-part brush** is generic and long (it scrolls):
    Style chips, Randomize, "This brush", then each part's choice chips and the
    current choice's dials as today's slider rows with a lock and a die each,
    and a lock per part. Stage 2 replaces it with part cards.

## Architecture

```
Brush (built-in | WildBrush I/P | ModularBrush M)
  │  InkWash.stroke(document, brush, preset, shade, session, clock)
  ├─ built-in / WildBrush ───────────► InkStroke, WildInkStroke, WashStroke   (unchanged)
  └─ ModularBrush ─► ModularStroke ─► ResolvedBrush.of(brush, size, shade)   (parts.js resolve)
                        │ samples → StrokePoints (pressure travel, tilt, smoothed direction, peak)
                        │ stampers → FloatTiles L (line), W (body), D (settled body)
                        │   LineStamper | Placement | HakeRibbon | BodyStamper
                        │ Life: None (as laid) | Fast-wet (windows) | Drying (lift settle)
                        │   BodySettle (settleBody, settleBlend)
                        │ ModularCompose: tone = f(base tone, L, D) → ToneDocument.setTones / coverTones
                        └ Drying with Settle → DryingWater patch in WatercolorSession, ticked by DryingTicker
```

Layer rule: everything that decides what a brush is or how it draws is pure
Java (no `android.*`), checked on the desktop. `DryingTicker` is the only new
Android class; `LabSettings`, `BrushLabActivity`, `DrawingPad`,
`BrushTestPad` and `InkWashBar` only show and call.

### File map

New, pure (`app/src/main/java/io/github/mpdairy/monopaint/`):

| Class | Responsibility | Prototype |
|---|---|---|
| `BrushPart` | The six parts in code order, labels, blurbs, the dials a swap keeps (`KEEPS`). | parts.js 6–14, 142 |
| `PartDial` | Every dial: part, key, label, range, default, choices, integer; how it's shown. | parts.js 15–75 |
| `PartChoice` | Every choice of every part, in code order: dials, range and default overrides, label overrides, macro words, blurb, hand preset cells. | parts.js 76–126 |
| `ModularFeatures` | Stage switches: `BRISTLES`, `CARRY`, `PLUMES`, `FILL_PLANE` (all false); which choices rolls may pick. | — |
| `ModularBrush` | The brush: name, seed, style, six parts (a choice and its dial values); fresh parts, swaps, normalize, copy, hand cells, preset matching. | parts.js 134–168, 386–390 |
| `ModularStyle` | The 14 styles: part weights, dial specs (always, sometimes, bands, options), sizes; rolls of a brush, a part, a dial; style bands. | parts.js 276–415 |
| `ModularLocks` | Locked parts and locked dials. | parts.js 391 |
| `ModularCode` | M codes: encode, decode, settings (identity). | parts.js 544–597 |
| `ModularVariations` | Six variations, at most one part swap, labelled. | parts.js 417–461 |
| `ModularWords` | Choice words, hand words, captions, "This brush". | parts.js 463–537 |
| `ModularSizes` | Preset sizes, shade darkness, sample sizes. | ui.js 497–509 |
| `ResolvedBrush` | Parts → what the stampers draw (the line, the body, the settle), with the Stage 1 fallbacks. | parts.js 170–274 |
| `FloatTiles` | Sparse page-sized float buffers in 64-pixel tiles. | (the prototype's page buffers) |
| `StrokePoints` | A stroke's samples: position, pressure travel, time, distance, speed, tilt, peak pressure, smoothed direction. | engine.js 50–66, port.js 277–286 |
| `StrokeMath` | clamp, lerp, mix, smooth, box blur, hashes and 1-D noise, tilt, the hand's darkness. | engine.js 8–80, 479–490, port.js 213–232 |
| `StrokePaper` | The noises a stroke reads: fine and mid from the paper choice's `PaperGrain`, low and blob from `WatercolorSettle.Paper`. | port.js 337–353 |
| `StickGrain` | Charcoal's own seeded 512-px grain. | engine.js 82–106 |
| `LineStamper` | The line: nib ellipse, tapers, pressure, speed, tilt, wobble, pooling, dry brush, charcoal fuzz and dust, grid dabs, windows, splatter. | engine.js 108–251, port.js 135–155 |
| `Placement` | Lines placed in the body: split nib, multi-line, outline, squiggle, Offset, Chaos. | engine.js 475–602 |
| `BodyStamper` | The body of paint: round dabs or a nib ellipse, tapers, tilt, streaks, live granulation, the wet window's distance map. | engine.js 253–333 |
| `HakeRibbon` | The hake's swept ribbon and pivoting head. | engine.js 335–473 |
| `BodySettle` | The settle (bleed, hollow edge, blooms, backruns, granulation, cover, impasto relief) and the line-and-wash blend, in bands. | engine.js 718–775, stroke.js 1–74 |
| `ModularCompose` | A stroke's tones over the tones it found: glaze, ink, charcoal and graphite contact rules, cover, relief. | stroke.js 391–441 |
| `ModularStroke` | The `DrawingStroke`: samples in, stamps, Life, compose to the page; lift. | stroke.js 455–708 |
| `DryingWater` | A Drying wash's suspended pigment settling after lift (the prototype's Water patches only). | stroke.js 76–238 |

New, Android: `DryingTicker` (ten ticks a second while a Drying wash settles,
shared by `DrawingPad` and `BrushTestPad`).

Changed:

| Class | Change |
|---|---|
| `Brush` | A third kind of value: a `ModularBrush` (`parts`). Stores and reads `M` codes; `kind()` by medium; `wet()` false; style label. |
| `InkWash` | Starts six-part brushes' strokes through the pure `ModularStroke.start` and their samples through `WildSample.modular`; `swatch` and a new `diameter` for them; deposits settling washes before any stroke. |
| `BrushDirection` | `azimuth(tiltX, tiltY)`: the lean's direction in radians, NaN without a dependable lean (the prototype's `tiltOf().dir`), so tilt is read one way everywhere. |
| `BrushCode` | `written` becomes package-visible, so `M` codes read case the same way. |
| `ToneDocument` | `coverTones`: a block write that sets coverage (alpha) for opaque paint. |
| `WatercolorSession` | Holds the settling `DryingWater`; `settleNow`, `tickDrying`; `dry` drops it. |
| `WildSample` | Six-part samples: keyed by their settings, sized to the tile. |
| `InkWashBar` | Size dots from `InkWash.diameter`. |
| `BrushCaptions` | Six-part captions; "Anything goes" for Wild. |
| `BrushStyle` | `label()`: "Anything goes" for Wild. |
| `ToolSlots` | The dice rolls a six-part brush in the slot brush's style. |
| `BrushLab` | Holds a six-part brush, its locks and its variations; picks styles, randomizes, swaps choices, slides, rolls and locks dials and parts; loads `M` codes. |
| `LabSettings` | The six-part column; old brushes keep their rows; style chips are the 14 modular styles. |
| `BrushLabActivity` | Help text, corner sample, drying ticks on the test page. |
| `DrawingPad`, `BrushTestPad` | Run a `DryingTicker` after a Drying stroke. |

## Data model

A brush is six parts, each one choice and that choice's dials. Off and None are
real choices, so every combination draws. **Never reorder** a part's choices or
a choice's dials: codes are written in this order. Append only.

### Dials (`PartDial`; parts.js 19–75)

Each dial: range, default, how it shows. A choice dial holds an option index.

- **Tip:** weight .5–1.5 (1, "×1.00"); aspect "Nib width" .14–.55 (.4); angle
  "Nib angle" 20–70 (40, "40°"); taperIn, taperOut, dry "Dry brush", wobble 0–1
  (0); soft "Soft edge" 0–1 (.3); headTurn "Head" {follows, fixed} (0); gap
  "Tine gap" (.55); count "Lines" 2–5 integer (3); reach (.6); hairs "Bristles"
  6–32 integer (26); cohesion (.55); splay (.45); dryness (.15).
- **Medium:** wet 0–1 (0); line "Line kept" (1); pool "Pooling", splatter,
  bleed, bloom, backrun "Backruns" (0); lines "Line placement" {centre,
  outline, squiggle} (0); offset, chaos (0); cover (1); impasto (0); spread
  (.3); hold (.4); grade 0–1 (.4, shown as 6H…8B: `GRADES[round(g < .4 ? g/.4·7
  : 7 + (g−.4)/.6·8)]`).
- **Carry:** pick "Pick up" (.4), lay "Lay down" (.5), load "Own paint" (1).
- **Paper:** tooth (.5).
- **Life:** window "Wet window" 1–10 mm (6, "6.0 mm"); drying "Drying time" 0–1
  (.35, shown as `wetMs(d)/1000` s with `wetMs(d) = round(750 + 19250·d²)`);
  settle (.35); takes "Takes ink" {off, drift, vein} (0); plume (.55).
- **Hand:** ps, pd, pw, ts, td, tw, ss, sd, sw ("Pressure → size", "Pressure →
  dark", "Pressure → width", "Tilt → size", "Tilt → dark", "Tilt → angle",
  "Speed → size", "Speed → dark", "Speed → angle"), all 0–1 (0); dir "Fast is"
  {thinner, thicker} (0); gain "Response" 0–1.5 (1, "×1.00").
- A dial with min 0 shows "off" under .005; otherwise two decimals.

### Choices (`PartChoice`; parts.js 79–120), in code order

- **Tip:** 0 round [weight, taperIn, taperOut, dry, wobble, soft]; 1 chisel
  [aspect, angle, weight, taperIn, taperOut, dry, wobble, soft], aspect range
  .3–.55, default .42; 2 flat "Flat nib" [same as chisel], aspect .14–.28,
  default .2; 3 hake [weight, headTurn, angle, dry, soft], defaults angle 75,
  dry .35, soft .2, dry labelled "Streaks"; 4 split "Split nib" [gap, aspect,
  angle, weight, taperIn, taperOut, dry], aspect .3–.55, default .42; 5 multi
  "Multi-line" [count, reach, weight, taperIn, taperOut, dry, wobble]; 6
  bristles [hairs, splay, cohesion, dryness, weight] (deferred).
- **Medium:** 0 ink [wet, line, pool, splatter, bleed, bloom, lines, offset,
  chaos]; 1 wash [wet, pool, bleed, bloom, backrun, line], defaults wet .85,
  line 0, pool .3, bloom .2, labels pool "Hollow edge", line "Pen line", wet
  "Wetness"; 2 cover [cover, impasto]; 3 charcoal [spread, hold], hold labelled
  "Hold (vine↔compressed)"; 4 graphite [grade, spread], spread default 0,
  labelled "Smudge".
- **Carry:** 0 off []; 1 lift [pick, lay] (.2, .3); 2 drag [pick, lay, load]
  (.6, .7, .85); 3 smudge [pick, lay, load] (.7, .55, .3). 1–3 deferred.
- **Paper:** 0 plain, 1 cold press, 2 laid, 3 felt, 4 crumpled, 5 canvas, each
  [tooth], plain's default 0, the others .5. The index is `BrushTexture`'s
  ordinal (the same order), so a paper is its texture's `PaperGrain`.
- **Life:** 0 none "None" []; 1 wet "Fast-wet" [window]; 2 dry "Drying"
  [drying, settle, takes, plume].
- **Hand:** every choice has [ps, pd, pw, ts, td, tw, ss, sd, sw, gain, dir]; the
  choice is a preset of cells: 0 steady {}, 1 "Press → wider" {ps .8}, 2 "Press
  → darker" {pd .8, ps .15}, 3 "Press → both" {ps .7, pd .6}, 4 "Brush hand"
  {ps .9, pd .3, pw .45, ss .4}, 5 "Tilt → side" {ps .4, ts .8, td .5}, 6 "Tilt
  → nib" {ps .4, tw .9, pw .4}, 7 "Quick → thin" {ps .3, ss .7, sd .5, sw .4},
  8 custom. A fresh preset sets its cells (the rest 0), gain 1, dir 0. A hand
  whose cells are exactly a preset's (within .005) is named for it, otherwise
  Custom. A hand cell in use is `min(1, cell × gain)`.

Rules (parts.js 134–168): a fresh part has every dial of its choice at the
choice's default. Swapping a part's choice keeps the dials that mean the same
thing in both (`KEEPS`: tip weight, tapers, dry, wobble, soft, angle; medium
pool, bleed, bloom, splatter; paper tooth, unless the old paper was plain;
life drying; hand gain and dir); a chisel, flat nib or split keeps the old
aspect only if it is inside its own range; a swap to Custom hand keeps every
cell. Normalizing clamps each dial to its choice's range, rounds choice dials
and integer dials, and fills missing dials with defaults.

## Share codes

`name.M<style><tip>_<medium>_<carry>_<paper>_<life>_<hand>.<seed>`
(parts.js 544–597):

- `name`: the slug (`BrushCode.slug`). `style`: one base-36 digit, the style's
  index in the 14 above (Anything goes is `d`). `seed`: base 36, unsigned 32
  bits.
- Each part: its choice index as one base-36 digit, then its choice's dials in
  order: a choice dial as one base-36 digit; any other dial as two base-36
  digits, `round((clamp(v, min, max) − min) / (max − min) × 1295)` over the
  **dial's** range (not the choice's narrower one).
- Reading: case doesn't matter (as `BrushCode.written`: lowercase, the letter
  after the first dot uppercase). A code is null if: it doesn't match
  `([a-z]+(?:-[a-z]+)*)\.M([0-9a-z])([0-9a-z_]+)\.([0-9a-z]{1,7})`; the style
  index is past 13; the seed is over 0xffffffff; it has fewer than six
  segments; a choice index is past its part's choices; a choice dial's digit is
  past its options. A segment that stops early leaves the remaining dials at
  their defaults; extra characters after a segment's dials, and extra segments
  after the sixth, are ignored. The brush is then normalized.
- New dials go on the end of a choice's list; new parts would go after Hand.
- **Identity** (`ModularCode.settings`): the six segments and the seed, without
  name and style, as `brushKey`. Samples and `Brush.sameAs` key on it.
- **Storage** is the share code, as for old brushes: a `Brush` stores
  `name<TAB>code`; reading tries an `M` code first, then `BrushCode.decode`.
- Golden codes from the prototype are in the plan's checks (for example
  `brave-onion.M24p6okati000ee00_0atzz9000000020000_0_277_1e0_6ee00ee0000we000000nz0.1u5hvr4`).

## Styles, Randomize and variations

- **Styles** (`MSTYLES`, parts.js 281–321): each a size, part weights and dial
  specs: `[lo, hi]` always; `{p, lo, hi}` on that share of rolls, else 0;
  `bands` (weighted ranges: Charcoal's Spread rolls .15–.45 70% of the time,
  0–.1 15%, .6–.9 15%); `opts` (Hake's head: fixed one roll in four). A dial a
  style leaves out rolls at its choice's default. Anything goes uses
  `WILD_PARTS` weights and its own dial rules, and caps tip dry + wobble +
  paper tooth at 1.1 (parts.js 343–374). The hand rolls from the style's cells,
  or Anything goes' odds (parts.js 375–385), then takes a preset's name if it
  matches one.
- In Stage 1, a deferred choice's weight counts as 0; a part whose every
  weighted choice is deferred rolls its first offered choice (Carry: Off). The
  Takes ink dial rolls 0.
- **Rolls** use a `java.util.Random` passed in (as `BrushStyle.roll`), names
  from `BrushNames.fresh`, seeds `random.nextInt() & 0xffffffffL`.
- **Locks:** a locked part is copied whole from the old brush; a locked dial
  keeps its value when the part rolls the same choice again.
- **Variations** (parts.js 421–461): six cards, each nudging two or three
  unlocked, non-choice dials (not gain) within the style's band widened to take
  in the current value; at most one card (85% of the time) also swaps one
  unlocked part other than Hand to another offered choice, captioned "Tip →
  Split nib". Each variation has a new seed and a kin name (`BrushNames.kin`).

## Sizes and shades (`ModularSizes`)

- `size(brush, preset)`: M as decision 7; S = 0.4 M, L = 2 M; clamped to
  1.5–360 px. `InkWash.diameter(brush, preset)` returns it for the bar's size
  dots (old brushes: `inkSize` or `typical`, as today).
- `darkness(shade)`: Dark 1, Light 1 − `INK_GRAY`/255, Lighter 1 −
  `INK_LIGHT_GRAY`/255.
- `InkWash.swatch`: ink, charcoal, graphite: `InkWashShades.ink(shade)`; wash:
  `round(255 × (1 − .34 d))`; cover: `round(255 × (1 − d))`.
- Samples (library tiles, lab cards, the corner, the bar's swoosh) draw at
  `sampleSize(brush, ref)` with `ref = min(height, width × .62)` (ui.js
  497–509, role "loop"): ink `ref / (13 + 14 × wet)`, charcoal `ref/9`,
  graphite `ref/16`, otherwise hake `.24 ref`, others `.17 ref`, at least 2
  px; the loop's pad is `2 × size + 6` when the size is under `ref/7`, else
  `.55 × size + 4`.

## Rendering pipeline

### Samples

`ModularStroke.sample(x, y, pressure, tiltX, tiltY, time)`: non-finite x, y or
pressure draws nothing (as `WildInkStroke`). Pressure becomes travel
(`ToolSettings.travel`). `StrokePoints.add` is the prototype's `addPt`
(engine.js 51–66 over port.js 277–286): a sample under 0.6 px from the last is
skipped; speed `v = .75 v₀ + .25 seg / max(1, t − t₀)` px/ms, 0 without times
(`PenSpeed.UNTIMED`); peak pressure so far; a direction smoothed with
`k = 1 − e^(−seg/12)`; tilt kept as given (degrees, as `BrushDirection`
reads them).

### Resolve

`ResolvedBrush.of(brush, size, darkness)` is `resolve` and `partsA` (parts.js
174–274), doubles throughout, with these Stage 1 changes:

- Bristles: `lineTip` NIB and `bodyTip` BODY; `bristle` = the tip's dryness;
  no swarm.
- Carry: `carry` is always null.
- `takes` is always none.
- `gray = 255 × (1 − darkness)` (the app's paper is 255, the prototype's 251).

The checks compare its numbers with the prototype's for six fixture brushes at
16 px and 360 px.

### Stampers and buffers

- Buffers are `FloatTiles`: page-sized, 64-pixel tiles allocated on first
  write, kept for the stroke's life and released at lift. L is the line's
  coverage, W the body's, D the settled body; Fast-wet adds rawL, rawW, wetD
  (filled −1) and relief; the blend adds lineSave and bodySave. A tile is
  64 × 64 floats (16 KB); a long 360-px stroke touches a few hundred.
- The stampers are ports of the prototype's with its per-pixel formulas and
  constants unchanged, writing a `FloatTiles` where the prototype writes
  `surf.scratch`, and reading noise from `StrokePaper` where it reads `surf.fine`
  etc. Their randomness is `WildBrush.Random` (mulberry32, the same as the
  prototype's `rng`), so seeds pick the same bristles, splatter and offsets.
- `StrokePaper`: fine and mid are the paper choice's `PaperGrain` read as 0–1
  and widened about .5 by its `spread`, as `WildInkStroke.tooth` reads them;
  low is `WatercolorSettle.Paper.low` and blob `Paper.blob`, as Wild paint
  reads them. So the noise is the app's, fixed to the page, not the
  prototype's.
- Big brushes scale as the prototype does (`inkBig = max(1, size/16)`,
  `paintBig = max(1, √(size/220))`), and lines over 16 px lay a dab only where
  the stroke crosses a distance grid (engine.js 124–127, 170).
- `BodySettle.settle` works through the stroke's rectangle in bands of at most
  256 rows, each with enough rows above and below for every blur it runs, so it
  never holds more than about width × 300 floats per array; a band's result is
  identical to settling the whole rectangle at once (a check proves it).

### Life

As `Stroke.add` and `Stroke.end` (stroke.js 628–692):

- **None:** line and body are final as laid; the line's taper out follows
  pressure falling and flicks (`b.fast`); a line with a body merges its loose
  share into the body as it is laid. The body is never settled.
- **Fast-wet:** the line's window (`wetLine`, stroke.js 507–533) re-tapers the
  tail at most every 50 ms of pen time, within `min(windowLen, taper out + 4)`;
  dabs further back are final. The body's window (`wetBody`, stroke.js
  541–594) re-settles the last `windowLen` px once per 330 ms of pen time or
  when the stroke has grown by 0.3 of the window, laying raw coverage in
  between. Without times (`UNTIMED`), only the distance rule applies to the
  body and the line re-tapers every sample. **Nothing changes at lift.**
- **Drying:** while the pen is down the body shows a preview (`under −
  (under − min(200, .82 under)) × darkness`, `WashStroke.preview`'s formula);
  at lift the line is laid again with its taper out, the body laid again and
  settled (`settleBody`, or `settleBlend` where a line meets a body), and the
  whole stroke composed. With Settle above 0 and no cover, a share `.45 ×
  settle` of the body's darkness is held back and handed to `DryingWater`.

### Compose (`ModularCompose`, stroke.js 396–441)

For each pixel in the changed rectangle: `under` is the tone the gesture found
(`ToneDocument.strokeBaseTones`); `c` the line coverage; `d` the body (W while
the pen is down in Drying and in None, D otherwise).

- Body: `glaze = under × (1 − min(1, min(1.6, d) × washK))`; with cover,
  `lerp(glaze, lerp(under, gray, min(1, d / body)), cover)` where `body =
  lerp(1, .4, impasto)`; impasto adds its relief.
- Line: ink ragged by tooth (`clamp((c − (.5 + tooth × .9 × (n − .5))) × 4 +
  .5)`); charcoal by the contact rule with its stick grain (stroke.js 423–429,
  `ex = 1 − under/255`); graphite by its grade with Fill 0 (stroke.js 430–434).
  Then `tone × (1 − a × inkK)`.
- A pixel with no line and no body: if the page differs from `under` (a tail
  the window took back), its span is restored (`ToneDocument.restoreSpan`);
  otherwise it is left alone.
- Writes: pixels with cover go through the new `ToneDocument.coverTones`
  (coverage at least `255 × cover × min(1, d / body)`, so opaque paint hides the
  layers below); the rest through `setTones`. Masking fluid shields both, as it
  does every brush.

### Drying after lift (`DryingWater`, stroke.js 76–238)

The prototype's `Water` patch only: `addWet` (stroke.js 156–200) without
plumes, `stepPatch` (201–238), `curl` with its three `waterTile`s, `wetTicksOf`
(3 to 200 ticks at 10 Hz). Per-page arrays become per-patch arrays (height and
depth only over the patch). A patch remembers each pixel's tone at lift and
writes `round(liftTone × (1 − shown) / (1 − shown₀))`, so ticks never drift.
One patch at a time, on the stroke's layer:

- `WatercolorSession.tickDrying(document)`: one step if the document's revision
  and active layer are the patch's, in its own gesture finished with
  `finishContinuation` (one undo step with the stroke); otherwise the patch is
  dropped unwritten.
- `WatercolorSession.settleNow(document)`: before any new stroke
  (`InkWash.stroke`), the patch's last step (`q = 1`, no flow) deposits
  everything still suspended, then drops it.
- `dry(reason)` drops it.
- `DryingTicker` posts a tick every 100 ms while a patch is held and no stroke
  is down, then renders and presents the dirty area (as `DrawingPad`'s wet
  paint does); when the patch ends it asks for recovery (autosave) as wet paint
  does.
- Paced (2026-10-15, MonoPaint's work budget, `docs/INTEGRATION.md` section 5):
  `WatercolorSession.tick` backs off 100 ms after lagging pen input or a tick
  over 40 ms, and shows at most every 120 ms, the dirty area gathering
  meanwhile; ticks step by the patch's age, so the page ends the same. A
  Fast-wet re-settle's box shows on the same pace (at lift at the latest).
- `WatercolorSession.wet()` (a patch is held) drives the wet indicator at the
  end of the bar; tapping it is `settleNow`.
- Gravity (2026-10-15, section "Gravity" below): a brush with gravity dials holds
  its patch (Settle 0 too) and, once the page tilts, a `GravityFlow` beside it.

## Live drawing on e-ink

- New strokes plug into the existing path unchanged: `DrawingPad.onTouchEvent`
  feeds every sample (historical ones included) to `stroke.sample`, then
  `renderDirty()` renders the document's dirty rectangle into the display
  bitmap and `flush` presents it through `DirectEink` in grey mode 7.
  `ModularStroke` writes tones during `sample`, so the dirty rectangle is the
  stroke's change. The lab's `BrushTestPad` does the same.
- Fast-wet's windows run inside `sample`: re-tapering the tail can lighten
  pixels just behind the nib (a span restored, then laid again), which mode 7
  shows as a grey change; nothing is deferred to lift, so lift costs only
  `document.finish()`.
- Drying's preview and lift behave like Watercolor's today (a plain preview,
  then the settled wash at lift); its after-lift settling ticks like wet paint.
- **Timing check** (`ModularBenchChecks`, desktop JVM, after a warm-up stroke):
  each of the 14 styles' fixed brush, Fast-wet, a 1400 × 900 wave with tilt,
  three samples an event, at 16 px and 360 px. Budgets: at 16 px a mean of at
  most 0.3 ms a sample and a p95 of at most 1.5 ms; at 360 px a mean of at most
  3 ms and a p95 of at most 15 ms; lift at most 30 ms; Watercolor in Drying, lift
  at most 60 ms. These are about twice the prototype's browser numbers
  (`bench.txt`: 360 px means up to 1.3 ms, rare 8 ms spikes).
- **Device-only risk:** the Manta is about 3–5× slower than this Mac; a 360-px
  Brush pen could reach several ms a sample and lag the pen. The knobs, in
  order: the line's grid spacing for big nibs, the body window's 330 ms
  re-settle, the line window's 50 ms re-taper, and capping a body's settle
  region. Elling checks on the device: Brush pen and Ink wash at Large, Hake at
  Large, a Drying Watercolor, tilt on Pencil and Charcoal.

## Compatibility rules

- `LegacyGoldenChecks` (plan Task 1, recorded before any other change, never
  re-recorded): the five `BrushCodeChecks` codes drawn through today's stroke
  for their kind (`WildInkStroke`, or `WashStroke` in their `WashLook`, as
  `InkWash.stroke` dispatches them) at S, M and L in Dark and at M in Light;
  the Wash and Watercolor built-ins at M; each one's loop sample through
  `WildSample.pixels`. CRCs of every layer's tones and alpha. Every later task
  must keep it passing.
- Ink and Fude can't be drawn on the desktop (`InkStroke` runs on
  `PressureStroke`, which needs Android). Stage 1 changes none of their code;
  the one dispatch line `InkWash.stroke` gains for six-part brushes comes
  before theirs and is reviewed in Task 12. `InkWash` itself needs Android, so
  the six-part branch lives in a pure `ModularStroke.start(...)` that the
  checks call.
- `WashGoldenChecks` is never re-recorded and must keep passing.
- Every existing check keeps passing. Where Stage 1 changes behaviour on
  purpose (the dice and Randomize make six-part brushes), the task that makes
  the change rewrites those assertions and says so.
- `I`/`P` codes decode and encode exactly as today; `BrushCodeChecks` is
  unchanged.
- Stored slots and library entries holding `I`/`P` brushes read as before.
- **Downgrade:** an older app can't read `M` brushes; a slot holding one falls
  back to its first brush and the library drops the line when it next writes.
  Don't install an older build over one with six-part brushes saved.

## The lab screen in Stage 1

- `BrushLab` holds either kind. For a six-part brush it keeps `ModularLocks`
  and `ModularVariations`; for an old brush, today's `BrushSetting` locks and
  `BrushVariations`.
- `LabSettings` for a six-part brush: Style chips (14), Randomize, the "This
  brush" text (`ModularWords.plainWords`, up to four sentences), then per part:
  a header row (lock for the whole part, label, the offered choice chips; a
  deferred choice shows only while it is the current one), then the current
  choice's dials as today's rows (lock, label, `BandSlider` over the choice's
  range with the style's band hatched, value as `PartDial.shown`, die). Choice
  dials (Head, Line placement, Fast is) are chips. Life hides Takes ink and
  Plume. Hand shows its preset chips, then its nine cells, Response and Fast
  is.
- Old brushes and built-ins show today's column, but the style chips are the 14
  modular styles.
- Variation cards, the library, share code copy and paste, rename, save, undo
  and Back work for both kinds.

## Deferred

- Stage 2: the new lab screen (part cards, Simple and Advanced macros, the swap
  strip, Hand grid, toolbar size slots, Surprise me, Load beside the name,
  Manta and Nomad layouts).
- Stage 3: Bristles (`BristleSwarm`, engine.js 604–716; built, see "Bristles" below), Carry (stroke.js
  595–626), graphite's Fill plane (stroke.js 693–700 and its compose term),
  Takes ink and plumes (the rest of `Water`), Drying water shared by several
  strokes.
- Not planned: converting old brushes to six parts.

### Stage 3 notes

- **Smudge (Carry: Lift, Drag, Smear) with Medium = Cover:** the reference is
  MonoPaint's opaque wet brush pen (upstream `8743286`, not merged: "Hold ink
  between strokes", opaque wet paint with Coverage 50%, sizes with pressure;
  `BrushInk` lifting paint into a reserve and trading it with the paper). Read
  it before porting stroke.js 595–626; `docs/INTEGRATION.md` section 5 sums it
  up. Built 2026-10-16 (branch `inkwash-smudge`): `2026-10-16-smudge-design.md`, own layer
  only (Decision 5's cross-layer question answered: a smudge moves its own layer, as found at
  pen-down), `ModularFeatures.CARRY` on.
- **Float (suminagashi), a fourth Drying choice:** paint floats as crisp rings, taps push it out, Clear water (Smudge
  Own paint 0) combs it, a tilt drifts it, then it prints. Built 2026-10-16 (branch `inkwash-float`):
  `2026-10-16-float-design.md`, Undo stroke by stroke while it floats, Sumi and Clear water under Ink wash.

## Risks

- **Speed on the Manta** (see Live drawing). Mitigated by the bench budgets and
  the knobs listed; measured by Elling on the device after the build.
- **Memory:** `FloatTiles` and banded settling keep a stroke to a few MB; the
  prototype's page-sized buffers would be about 20 MB each at Manta size.
- **Two engines** to maintain until Stage 3 decides whether old brushes move
  over. The old engine is frozen; new work goes to the new one.
- **Code compatibility drift:** the M tables are copied by hand. The golden
  codes, the prototype's fixture brushes and a 2000-roll round trip guard them.
- **Fallbacks look different from the web lab:** a Bristles or Carry code from
  the web lab draws plainer in the app until Stage 3. The lab's chip shows the
  choice, so it isn't hidden.

## Open questions (none blocking)

- Is 0.4 × / 2 × the style size right for S and L? Elling to judge on the
  device; Stage 2's size slots replace it.
- Should "This brush" also show for old brushes (from their settings)? Not in
  Stage 1.

## As built (2026-10-15)

Stage 1 is on `inkwash-v1`, from `5b87f59` (plan) to the as-built commit.

### Checks

- `scripts/test_inkwash.sh`: 45 PASS. `scripts/test_document.sh`: 80 PASS
  (both one more than the plan said: checks added in review fixes).
- `LegacyGoldenChecks`: unchanged since Task 1 (`f9a3a22`), passing.
  `WashGoldenChecks`: no diff since `3e47f54`, passing.
- `./gradlew :app:assembleRelease`: builds.
- The 16 px Drying lift budget in `ModularBenchChecks` sometimes misses on a
  busy Mac; a second run passes.

### Desktop timings (`ModularBenchChecks table`, Fast-wet, 1400 × 900 wave with tilt)

| Style | Tip · medium | 16 px mean / p95 | 360 px mean / p95 | Lift (16 / 360) |
|---|---|---|---|---|
| Fineliner | Round · Ink | 0.069 / 0.185 ms | 0.192 / 0.601 ms | 0.1 / 0.4 ms |
| Brush pen | Round · Ink | 0.060 / 0.230 | 0.442 / 1.907 | 0.1 / 0.3 |
| Dip pen | Flat nib · Ink | 0.025 / 0.093 | 0.344 / 0.850 | 0.1 / 0.2 |
| Marker | Round · Ink | 0.011 / 0.016 | 0.104 / 0.248 | 0.1 / 0.4 |
| Pencil | Round · Graphite B | 0.050 / 0.080 | 0.648 / 2.738 | 0.1 / 0.5 |
| Charcoal | Round · Charcoal | 0.175 / 0.605 | 2.514 / 9.445 | 0.1 / 0.5 |
| Watercolor | Round · Wash, wet | 0.077 / 0.140 | 0.618 / 3.716 | 0.1 / 0.2 |
| Ink wash | Round · Wash, wet | 0.113 / 0.231 | 0.749 / 3.926 | 0.1 / 0.2 |
| Gouache | Round · Cover | 0.057 / 0.102 | 0.479 / 2.208 | 0.1 / 0.3 |
| Oils | Hake · Cover, impasto | 0.170 / 0.301 | 1.607 / 4.066 | 0.1 / 0.2 |
| Pastels | Round · Charcoal | 0.056 / 0.073 | 0.825 / 1.832 | 0.1 / 0.2 |
| Dry brush | Round · Wash, flat | 0.041 / 0.073 | 0.563 / 2.504 | 0.1 / 0.2 |
| Hake | Hake · Wash, wet | 0.087 / 0.143 | 1.239 / 3.566 | 0.1 / 0.2 |
| Anything goes | Flat nib · Graphite 2H | 0.036 / 0.155 | 2.274 / 11.991 | 0.1 / 0.3 |

All inside budget. The slowest at 360 px are Charcoal and the flat-nib
graphite (p95 near 10–12 ms): the first to watch on the Manta.

### Where the port differs from the prototype, and why

- **Big Drying body at lift:** the body is laid again with its dab spacing
  reset (`lastD`). The prototype kept the live stroke's spacing, so washes
  over 220 px vanished at lift. The web lab got the same fix (`a369244`).
- **Tilt** is read in doubles at each dab's interpolated tilt
  (`BrushDirection.azimuth`/`tiltLean`), not the old float `lean`, so lines
  match the prototype to the bit.
- **StrictMath sin/cos** in the body and hake stampers: the Mac JVM's `Math`
  differs from V8 in the last bit often enough to drift the hake's angle.
- **Fast-wet tail:** `FastWetChecks` reads the taper at x = 400, inside the
  last re-taper; the dabs after it (under 50 ms) keep their width, in the
  prototype as here.
- **Split nib's wet gap** goes to the body as laid. In Fast-wet the body is
  the raw buffer, which the prototype's gap never reached either, so it has no
  effect there.
- **Compose shortcut:** a pixel whose line and body haven't changed since it
  was last composed is skipped. Checked byte-identical against composing every
  pixel (`ComposeShortcutChecks`).
- **Cover paint's coverage** builds on what the gesture found, not on what the
  stroke already wrote, so a settling wet window lets the layers below show
  again (`ToneDocument.coverTones`).
- **Drying hand-off:** the stroke keeps the held-back wetness (`pendingWet`)
  only until lift, hands it to `DryingWater`, and drops it.
- **`DryingWater` is built at its first tick, not at lift,** so lift doesn't
  wait. It keeps only the 2 × 2 cells the wash reached. Two approximations:
  the curl and the depth it reads are worked out once, on the cell grid, not
  per pixel; no plumes, parcels, stains or trails.
- **Size cap:** a wash of more than 2^18 kept cells (about 1 M pixels, a fifth
  of the page) is laid settled at lift, a row at a time, rather than held
  (memory and UI-thread time).
- **Settle finishes instead of stopping:** a new stroke, pause, a panel over
  the canvas, another page, a layer change, a shade change or opening another
  drawing lays what's still suspended where it is, so the saved page is the
  settled one. Undo still takes back stroke and settle as one.
- **Samples** (tiles, cards) have no session, so a Drying stroke is laid
  settled at finish; they no longer look lighter than the stroke.
- **Lab Simple / Advanced** (a pref): Simple shows styles, Randomize and
  "This brush"; Advanced adds the parts and dials. Not in the original Stage 1
  plan; added so the lab fits the Manta by default.
- **`Brush.style()` removed:** the dice, Randomize and the styles use
  `Brush.modularStyle()` (a Wild brush's is Anything goes); the label uses
  `styleLabel()`. Wild shows as "Anything goes" in captions and the lab.
- **Review fix (Task 16):** the taper, the pen's width and a dry line's load
  are one helper each (`StrokeMath.taper`, `Pen.width`, `Pen.load`) instead of
  copies; 504 strokes hash the same before and after.

### Known limits

- Stage 1 stand-ins: Carry draws as Off, Bristles as round, no plumes,
  graphite has no Fill plane. Rolls never pick these; codes that use them load
  and draw plainer.
- Downgrade: an older build drops M brushes from the library, and slots holding
  one fall back (in the CHANGELOG).
- Advanced's column is about 2.5 Manta screens tall (it scrolls).
- Locks set in Advanced still apply in Simple, where they can't be seen.
- Left as is from the review: `BrushDirection.tiltLean` repeats `lean` in
  doubles on purpose (bit-match); `LineStamper` and `Placement` still share the
  shape of their dab loops, as the prototype's `stampLine` and `stampPlaced`
  do. Merging them is a bigger change than Stage 1 should make.

### Device checks for Elling

- Brush pen and Ink wash at Large: no lag behind the pen.
- Hake at Large.
- A Drying Watercolor: it settles after lift, then one Undo takes it all back.
- The first tick of a big wash doesn't stall the screen.
- Drawing while a wash is still settling (it's laid where it is first).
- Pausing the app mid-settle, then coming back.
- Tilt on Pencil and Charcoal.
- An old Wild brush draws unchanged.
- Brush Lab: Simple and Advanced, load an M code, Randomize.
- A slot's dice on a built-in: gives a six-part brush in its style.
- Nomad layout (simulate smaller panel): the lab and bar still fit.

### One engine (2026-10-15)

The owner's decision ("Convert everything"): Decisions 1 and 2 above (two engines, old brushes keeping their rows)
and "Not planned: converting old brushes" are reversed. Plan: `docs/archive/superpowers/plans/2026-10-15-one-engine.md`.

- Old Wild brushes convert as the prototype's `fromOld` (`LegacyBrushes.of`), plus Tilt → size .1 for paint (the
  app's old paint broadened with the lean; the prototype's port of it didn't) and drying seconds kept. The built-ins
  are fixed M codes tuned on the desktop against the old engines: Fineliner, Brush pen, Ink wash, Watercolor.
- Measured against the old engines (Java, the golden wave, S/M/L): the three `I` codes within 0.1 of a tone on
  average; the two `P` codes 5–17 (same shape and tone; no ragged edge band or wet-on-wet joining); Fineliner's area
  1.05×; Ink wash's area and tone equal; Watercolor's area within 2%. Fude couldn't be matched (the six-part tilt range
  is 1–2.2× against Fude's 0.12–4.8×) until the hand's **Tilt range** (below): Brush pen's area is now within 0.94–1.07×
  of Fude's at every hold and size but the smallest (0.75× upright, 0.86× laid low, at 3 px).
- Stored slots and library lines convert once on launch (M codes written back, names kept); a converted slot keeps the
  px and darkness it drew. The old engines (`InkStroke`, `WildInkStroke`, `WashStroke`, `WashLook`,
  `WatercolorSettle`) are gone; the paper noise six-part strokes read moved to `PaperNoise` unchanged.
- `LegacyGoldenChecks` (never re-recorded while its engines lived) and `WashGoldenChecks` went with the engines;
  `LegacyConversionChecks` pins the five codes' and the built-ins' conversions (M codes and six-part pages), and
  `EraseBothChecks` has the eraser's golden. test_inkwash.sh 42 PASS, test_document.sh 77 PASS.
- Device checks: Fineliner and Brush pen feel (Ink and Fude as they were), a converted Wild paint, a slot's size and
  grey after the update.

### Tilt range (2026-10-15)

Not in the prototype. The hand's twelfth dial, `TILT_RANGE` (normal / wide), last in every hand choice's list, so
codes without it read normal. `ModularCode` writes the dials the prototype lacks (`ModularCode.ADDED`) only when one
is off its default: every brush that doesn't use it keeps its code, the prototype's, byte for byte. Wide gives
Tilt → size the old Fude pen's width (`ResolvedBrush.Pen.tiltWidth`: 0.12 of the size at 30° or steeper, the size at
45°, 4.8× at 60° or lower, linear between, scaled by the cell) and Tilt → angle its flattening (`tiltFlat`: round to
45°, to 0.2 at 60°, eased in); without a tilt reading it is the natural 45° hold, so samples (whose points now carry
no reading rather than upright; the same for every other brush) show the size. The line only: the body's tilt is
unchanged. In the lab it is Tilt → size's fourth level, FULL+. The Brush pen built-in uses it at Weight 1.
Engine diff: `LineStamper` two lines (the width and flattening call `Pen.tiltWidth`/`tiltFlat`, which return the
prototype's expressions for a normal range, so every other brush draws to the bit as before), and `ModularStroke`
two lines (it reads `ResolvedBrush.heldBack`/`settlesAfterLift`, which "This brush" now reads too).

## Speed on the Manta (2026-10-15)

The Manta (four Cortex-A55 cores) runs this engine about **20 times** slower than the Mac, not 3–5 times; at 480 Hz
the pen leaves about 2.1 ms a sample. Tools: `scripts/bench_device.sh` (the `-Pbench` build, app id
`ing.ell.mybaddrawings.bench`, no launcher icon, uninstalled after each run) and `scripts/bench_desktop.sh` run
`ModularPerfBench` (androidTest): a 1.5 s, 480 Hz S-curve on a full page, replayed in real time with Android's
batching, reporting lag after lift, share of real time, and `Prof` sections. Its `hashes` mode lists every case's
page CRC (layers and shown tones), so a speed-up can show it changed no pixel; `pgm` writes the pages.

What changed, in order (exact unless said):

1. **Compose once an input event** (`DrawingStroke.present`, called by `DrawingPad` and `BrushTestPad`): same
   tones; a Fast-wet layer's coverage may end lower where the window lightened a pixel.
2. **Body dab spacing at every size**, closer for hard edges (`BodyStamper.spacing`); a Drying lift lays the last
   point. Six-part round bodies under 220 px change slightly.
3. **Fast-wet settles at most every 150 ms** (and always by 0.7 of the window), reading ahead by the extra
   distance; **only paint laid since the last settle rises** (fixes flat half-moons in quick wide washes).
4. **Rows, not pixels**: `FloatTiles.row/setRow`, row-wise column blur, the hake's tile cache, paper noises read
   once a pixel, `au⁸` by squaring.
5. **The lattice**: a stroke whose marks reach past **100 px** (`ModularStroke.reach`, `latticePx`) keeps its
   buffers on the even pixels (`FloatTiles.shift`); stampers evaluate the same formulas at those page places, the
   settles and blend run there with halved radii, and the line, body and relief are stretched bilinearly
   (`FloatTiles.expand`) before composing at every pixel. A dry line's gaps take the share of a point's four
   pixels. Cost: edges a pixel softer, grain and bristle gaps a little softer (`LatticeChecks`: a 300 px wash within
   2.6 tones mean). Threshold from the device sweep: past 200 px wide washes at 120–180 px fell behind; past 110
   every style at 120 px but charcoal keeps up.
6. **Sticks** lay every m-th grid line (m ≤ 4, scallops under a twelfth of the soft edge), small sticks too; a
   Fast-wet line over 64 px or on the lattice re-tapers every 150 ms.
7. **Drying lift**: settles only near tiles with paint, in bands shared over up to three helper threads; lays the
   line and body again only over the end's box; compose and stretch pass over untouched tiles.

Device, the bench's stroke (lag after lift / share of real time; before → after):

| Case | Before | After |
|---|---|---|
| Watercolor None 120 | 2.05 s / 233% | 0.01 s / 35% |
| Pencil None 120 | 2.42 s / 257% | 0.02 s / 74% |
| Charcoal None 120 | 16.1 s / 1168% | 0.34 s / 116% |
| Ink wash Fast-wet 360 | 15.7 s / 1141% | 0.59 s / 133% |
| Oils Fast-wet 360 | 19.4 s / 1391% | 0.65 s / 138% |
| Brush pen Fast-wet 360 | 10.3 s / 776% | 1.38 s / 185% |
| Hake None 360 | 5.5 s / 461% | 0.11 s / 91% |
| Ink wash Drying 360 (lift) | 6.5 s | 0.98 s |
| Charcoal Fast-wet 360 | 65 s | 3.1 s |

At every style's own Small, Medium and Large, everything keeps up except Charcoal Fast-wet at 30 px (121%), Ink
wash Fast-wet at 90 px (108%) and Hake Fast-wet at 360 px (144%, 0.7 s); Drying lifts settle within 0.95 s.
Still slow at 240–360 px: Charcoal (1.8–3.8 s), Pastels (1–2 s), Anything goes Fast-wet (up to 2.9 s), Pencil at
360 (0.9–1.7 s). Next, if needed: Fast-wet's window settle and the compose on helper threads (compose needs
`ToneDocument`'s undo capture made thread-safe); a quarter-resolution lattice past ~300 px.

`ModularBenchChecks` now budgets at this scale: about twice this Mac's slowest style, so a change that doubles a
style's cost fails on the desktop before it lags the pen on the Manta.

Merged after the one-engine work (rebased onto it; branch `inkwash-perf` kept as it was). The converted built-ins and
Wild paints are six-part brushes, so they take these changes too: `LegacyConversionChecks`' paint pages and the
eraser golden's Ink wash page were recorded anew (paints' bodies, a tone or two; pens unchanged; the eraser over the
old page gives its old sums). The exactness claims above hold on the rebased commits (the bench's 210 hashes, each
commit against its parent). On the Manta, the bench build before and after the merge, back to back, times the same
(Watercolor, Ink wash and Charcoal at 120 and 360 px, all three Drying choices: within run-to-run noise).

### Round 2: what the pen waits for (2026-10-15)

Branch `inkwash-perf2`. The owner: "larger brushes going quickly take a while to appear". The real app's stroke timing
showed why the bench missed it: on the Manta **each pen event carries one sample** (480 a second, never batched), and
each was sampled, composed, rendered and presented before the next, so events queued (input age 130 ms average, up to
500 ms) while the bench assumed Android's 60 Hz batching. The bench now measures what the owner sees, each sample's
wait from the pen to its render (`ModularPerfBench`: pen50/pen95/penMax, the session paced as the app paces it, input
`SYNC` as the Manta sends it, `BATCH`, or `VSYNC`), and `LiveStrokeBench` (`LIVE=1 scripts/bench_device.sh`) sends real
pen events, one sample an event at 480 Hz, into the bench build's own drawing screen and prints `DrawingPad`'s stroke
timing line.

What changed, exact unless said (the bench's 210 page hashes are unchanged at 8, 3 and 1 samples an event;
`CoresChecks`: several cores draw what one does):

1. **Batched pen samples** (`PenBatch`, `DrawingPad`): an Inkwash pen stroke gathers its events' samples and draws
   what has gathered from a posted runnable, after the queued events are in, so a slow draw is followed by a bigger
   one. The stroke gets the same samples in the same order; it composes once a batch rather than once an event (the
   same tones; the coverage caveat of "compose once an input event" above). The biggest change by far.
2. **Four cores** (`Cores`: three helpers beside the caller, work in bands of tile rows that share no tile):
   compose (`ToneDocument.openBands`), the stampers (a call's dabs worked out first, then laid band by band in their
   order), Fast-wet's window settle and raise, the blur (rows, then columns), the lattice stretch, the Drying lift's
   settle (moved onto it) and the screen render (`ViewportBitmap`).
3. **Composing from the lattice**: a big stroke is composed straight from its half-resolution buffers, reading each
   pixel as the stretch would (`FloatTiles.between`), and what changed is found on the lattice (a quarter of the
   comparing, no page-sized stretch while drawing, and impasto no longer composes every pixel).
4. **Presents spaced by their cost**: while drawing, about 1.5 times the recent present (8–100 ms). The stroke timing
   line now times the present's parts: the driver call blocks for up to ~56 ms on this firmware (2606141001).
5. A dry line skips the bristles where a dab can't beat what is there; the compose's line rules are a method of their
   own (the device's compiler inlines into small methods).

Real strokes on the device (`LiveStrokeBench`, Fast-wet, the bench's 1.5 s S-curve; before: `d7997d0`). Before:
input age avg/max (the pen's wait before its event is even handled). After: lag avg/max (the oldest sample of a batch,
pen to presented; the median sample waits about half that). Idle: from lift until the screen is finished.

| Case | Before: age, idle after lift | After: lag, idle after lift |
|---|---|---|
| Watercolor 240 / 360 | 370/584 ms, 0.85 s / 810/1407 ms, 1.7 s | 13/140 ms, 0.09 s / 30/156 ms, 0.14 s |
| Ink wash 240 / 360 | 1.2/2.0 s, 2.3 s / 1.7/3.3 s, 3.7 s | 109/375 ms, 0.12 s / 217/337 ms, 0.19 s |
| Oils 240 / 360 | 2.9/5.8 s, 6.2 s / 4.7/9.7 s, 10.3 s | 60/295 ms, 0.12 s / 220/323 ms, 0.23 s |
| Hake 240 / 360 | 1.4/2.7 s, 3.0 s / 2.5/4.9 s, 5.3 s | 31/136 ms, 0.14 s / 145/233 ms, 0.21 s |
| Brush pen 240 / 360 | 0.95/1.8 s, 1.9 s / 1.8/3.6 s, 3.6 s | 23/119 ms, 0.00 s / 92/149 ms, 0.04 s |
| Pastels 240 / 360 | 1.2/2.2 s, 2.2 s / 1.2/2.1 s, 2.1 s | 67/323 ms, 0.06 s / 42/159 ms, 0.06 s |
| Charcoal 240 / 360 | 2.2/4.2 s, 4.2 s / 2.1/4.2 s, 4.2 s | 344/412 ms, 0.20 s / 328/429 ms, 0.16 s |
| Anything goes 240 / 360 | 2.1/4.1 s, 4.1 s / 3.7/7.5 s, 7.6 s | 327/630 ms, 0.34 s / 482/1402 ms, 1.0 s |
| Drying lift (Ink wash 90/180/240) | finish 758/566/638 ms | 769/421/470 ms |
| Drying lift (Watercolor 90/180/240) | finish 403/430/502 ms | 361/290/325 ms |

Still slow: Ink wash and Oils at 360 (~0.2 s behind the pen; the UI thread is about fully busy, half of it composing
the leading edge), Charcoal and Anything goes (the line's Fast-wet re-taper re-lays the whole tail every 150 ms, about
half the line's time), and the Drying lift's settle (0.3–0.8 s, mostly `BodySettle`: the blend's serial line pull and
the settle at full resolution for small brushes). Next, if needed: a cheap preview under the pen refined at lift
(compose the leading edge at half resolution, recompose the stroke at lift: same tones, a layer's coverage can end
lower), a quarter-resolution lattice past ~240 px, a re-taper that re-lays only what its taper changes, and the Drying
lift off the UI thread. Not used: the fast black-and-white plane for the live stroke (`DrawingPad.liveFastPlane`,
off), since on the Manta it exists only from firmware 3.30.45 and this device runs 2606141001; it is wired as shapes'
previews are, for a device test on newer firmware.

### Quick then render (2026-10-15)

Branch `inkwash-quick`. The owner: "speed ... still not good with the big ones ... some threshold ... go with quick
then render"; the pen should feel instant, not the stroke be WYSIWYG. As built:

- **The rule, at pen-down, never mid-stroke** (`QuickStroke.keepsUpTo`, `cost`, `quick`): a stroke goes quick when its
  marks reach (`ModularStroke.reach`) further than what drives its cost keeps up with, live, on the Manta:

  | What it draws | Keeps up to (reach, px) |
  |---|---|
  | a wash, ink or opaque paint, impasto too (no stick, no Fast-wet re-taper); any Drying body | any size |
  | a Fast-wet body (its window re-settles) | 420 |
  | a Fast-wet body with impasto (each re-settle raises its relief, which composes every pixel) | 280 |
  | an ink line whose Fast-wet tail re-tapers (`ModularStroke.retapers`) | 320 |
  | a Fast-wet line at least a quarter as broad as the wet body it bleeds into | 260 |
  | a graphite line | 260 |
  | placed lines (split nib, multi-line) | 220 |
  | a stick's line (charcoal, pastel, graphite with a soft edge) | 200 |
  | a stick's or graphite's line that re-tapers | 130 |

  The least of what the line and body ask. Calibrated on the device bench (`ModularPerfBench`, pen to render at the
  95th percentile): first an awake sweep of the 14 styles × None, Fast-wet, Drying × 60–360 px, then, since the owner
  was drawing during parts of it, clean runs with the screen asleep, which runs the engine about 1.8 times slower (so
  a stroke that keeps up asleep keeps up awake). Asleep, every live case of Watercolor, Ink wash, Oils, Gouache,
  Charcoal and Anything goes at 120/240/360 measured at most 46 ms, and every case past 60 ms is quick. A desktop
  sweep of every Tools page preset and style at 60–360 px (the bench stroke's wall time drawn live) found what those
  styles don't show: placed lines, a broad line bleeding into a wet body and pastel sticks, now rows of their own. Not
  confirmed on the device (the tablet left before the run): the presets at the edge of the new rows. One constant per
  row in `QuickStroke`; `-e sizes edge -e quick rule` times each brush at the biggest size still drawn live.
  Calibrated for the Manta; the Nomad is not measured and is assumed alike. At the Tools page presets' own Large sizes only Dark stick goes
  quick; bigger custom sizes reach the rows above.
- **Preview** (the pen's thread): the brush's footprint at each sample from the stampers' own geometry
  (`LineStamper.nib`, `BodyStamper.shape`, `HakeRibbon.thickness`/`target`; placed lines and paint as wide as they are
  placed), swept as the convex hull of consecutive shapes (24-gon ellipses, every 1/24 of the reach), only what the
  last shape didn't cover written, at most every 8 ms of pen time. Tones (`ModularCompose.flatTone`, no texture): ink
  solid at the line's darkness, opaque paint its own grey, a wash Drying's light preview grey (`previewWash`) as a
  1-in-4 dot dither, a stick's line at 0.6 of its ink as a checkerboard; darken-only over what the gesture found.
- **The true stroke** renders from the same samples on a thread of its own as they come (`ModularStroke.render`: its
  own tilt source replaying the tilt each sample was drawn at, unpaced, its gesture begun at lift), into its own
  buffers only, sharing its work with quiet helpers at the lowest priority (`Cores.quiet`), so the pen's thread never
  waits for it. At lift the preview's gesture is cancelled (the page as the stroke found it), the true stroke's
  gesture begins, and once the thread has drawn the last sample the pen's thread composes it once (`develop`); the
  droplet shows meanwhile (`WatercolorSession.developing`). `QuickLift` (drawing screen and Brush Lab) looks in every
  5 ms; anything that needs the page first (a new stroke, undo, a page, a pause, the droplet's tap) finishes it at once.
  One undo step; a Drying wash then settles and gravity moves it exactly as after a live stroke.
- **Same page:** every layer as the same samples drawn live for Instant and Drying; for Fast-wet the same tones, and a
  layer's coverage as when composed once (live, it depends on how the input was batched: the bench's own hashes at 1, 8
  and all samples a present differ there, never in the shown tones). Checked: the bench's 210 hashes with `-Dquick=on`
  equal the live ones at 8 samples a present (None, Drying) and at one present (Fast-wet); `QuickStrokeChecks` for
  every style and life at 60 and 200 px, the tilt replayed, a tap, the render thread, undo; the live bench's 210 hashes are
  unchanged by the work.
- **The eraser** keeps its path: at its Large (220 px) it costs about 0.14 ms a sample on the Manta (desktop × 20).

Manta, the bench's stroke, screen asleep (about 1.8 times slower than awake), the pen's wait to its render at the 95th
percentile / from lift until the page is final; live as before (`-e quick off`), quick as built (`-e quick rule`, the
true stroke on its own thread). Rows the rule draws live keep their live numbers.

| Case | Live | Quick | Drawn |
|---|---|---|---|
| Watercolor None / Fast-wet / Drying 360 | 23 ms, 0.04 s / 44 ms, 0.16 s / 11 ms, 0.53 s | | live |
| Gouache None / Fast-wet / Drying 360 | 18 ms, 0.04 s / 29 ms, 0.14 s / 11 ms, 0.30 s | | live |
| Ink wash Fast-wet 240 | 43 ms, 0.13 s | | live |
| Ink wash Fast-wet 360 | 90 ms, 0.19 s | 5 ms, 0.54 s | quick |
| Oils None 360 / Fast-wet 240 | 41 ms, 0.05 s / 46 ms, 0.07 s | 4 ms, 0.37 s / 3 ms, 0.45 s | live |
| Oils Fast-wet 360 | 81 ms, 0.20 s | 4 ms, 0.65 s | quick |
| Charcoal None 120 | 54 ms, 0.06 s | (not timed) | quick |
| Charcoal None 240 / 360 | 217 ms, 0.15 s / 255 ms, 0.15 s | 13 ms, 0.50 s / 15 ms, 0.51 s | quick |
| Charcoal Fast-wet 120 / 240 / 360 | 101 ms, 0.08 s / 576 ms, 0.39 s / 711 ms, 0.45 s | 4 ms, 0.38 s / 15 ms, 0.84 s / 11 ms, 0.83 s | quick |
| Charcoal Drying 360 | 249 ms, 0.84 s | 15 ms, 1.20 s | quick |
| Anything goes None 240 / 360 | 30 ms, 0.03 s / 55 ms, 0.05 s | 2 ms, 0.56 s / 3 ms, 0.65 s | quick |
| Anything goes Fast-wet 120 / 240 / 360 | 85 ms, 0.02 s / 506 ms, 0.30 s / 1383 ms, 0.98 s | 1 ms, 1.06 s / 3 ms, 2.26 s / 4 ms, 2.23 s | quick |
| Anything goes Drying 240 / 360 | 59 ms, 0.28 s / 168 ms, 0.52 s | 3 ms, 1.27 s / 4 ms, 1.81 s | quick |

The pen never waits more than ~15 ms for a quick stroke; the price is the time from lift to the final page, now mostly
the true stroke's backlog (what its quiet thread couldn't draw while the pen moved) and the one compose at lift
(0.1–0.45 s of it, asleep). The bench's live column omits the panel's present (up to ~56 ms on this firmware), which
both paths pay. Next, if needed: compose the true stroke in bands on the quiet thread before lift into a page of its
own (the compose at lift is then a copy), a cheaper re-taper for sticks (most of Anything goes' backlog), and
confirming the new rows on the device.

#### The panel's side (2026-10-15, branch `inkwash-panel`)

The owner, on the build with quick strokes: "works with watercolor fine it seems but there are most other large format
brushes where it's still slow ... yeah most", and of upstream MonoPaint's big brushes: "sluggish on longer lines". The
app's own timing of his strokes said the pen waited 1–6 ms on average for its samples to be presented: what he saw
was the panel.

**Measuring the panel.** The e-ink controller (`fdec0000.ebc`, IRQ 18) raises an interrupt for every frame it drives,
about every 15 ms while any update runs, and the count is readable from `adb shell` in `/proc/interrupts` (not from an
app: SELinux). So the panel's own time is countable: an update is shown when the frames stop. The pen's down and up
come from the Wacom device (`/dev/input/event7`). Tools (scratch, outside the repo): `panelmon` (the owner's own
strokes, passively: when the panel stopped after each lift), `ebcbench` (controlled updates on a blank page, the bench
build holding it: `LiveStrokeBench -e hold ms`), `framelog` (the frames beside `LIVE=1 scripts/bench_device.sh`,
matched to each stroke's down and lift). Manta, firmware 2606141001:

| Update (400 × 400 unless said) | Frames | Panel busy |
|---|---|---|
| mode 7 (gray, flags 0), any area 100² to 1000², binary dots or solid | 19 | 270–290 ms |
| mode 9 (pen, flags 1), plane 0 or 1 | 18 | 260–275 ms |
| mode 8 | 18 | 265 ms |
| modes 2, 10, 13 | 16 | 240 ms |
| modes 3, 4, 11, 12, 14 | 29–31 | 390–410 ms |
| mode 1 (redrives even unchanged pixels; the driver then takes the area for white) | 75–83 | 920–1010 ms |
| **mode 5 or 6 (flags 0, plane 0)**, any area | **8** | **140–155 ms** |
| mode 15 | restarted the tablet (never again) | |
| 60 overlapping updates 12 ms apart, a 300 px box moving 8 px each (a stroke's leading edge): done after the last by | mode 7: 217 ms, mode 9: 200 ms, **mode 5: 87 ms** | |
| 30 changes of one area 12 ms apart: done after the last by | mode 7: 214–373 ms, **mode 5: 86 ms** | |

What this says:

- **No queue.** Overlapping updates don't wait for each other: a pixel changed again while its update runs starts
  over toward the new value, and is done one update's time after its last change. The driver call itself blocks at
  times (up to 40–55 ms during a run of updates), whatever the mode.
- **Size doesn't matter**, nor does what the pixels are (the page is black and white dots anyway): only the mode.
- **This firmware's mode 9 is no faster than gray** by the panel's frames; mode 5 is about twice as fast. Which
  waveform each mode is, the driver doesn't say (its waveform partition and sysfs are root-only); mode 5's frame count
  is an A2-like one. The driver keeps track of what the panel shows: an update asking for what is already shown
  returns 17 and drives nothing (so a forced gray refresh of an unchanged page does nothing on this firmware), and after
  a mode 5 or 9 update a gray one over it with the same pixels returns 17 too.
- **Why big brushes felt slow and Watercolor didn't.** A pixel shows only an update's time after its *last* change.
  Watercolor in Slow (the built-in) previews its wash as a flat light grey, written once as the brush's edge reaches it;
  a live Fast-wet or Instant brush keeps changing the pixels under its nib while the nib passes (dab after dab, the
  wet window settling behind), and the panel starts over each time. The bigger the nib, the longer a pixel stays under
  it.

**What changed.**

(Superseded on 2026-10-16: the fast update left strokes ghosted after lift and is now on the bench only; see "Lift after
the fast update" below. The code 17 above isn't "nothing driven": a gray update that changes pixels can return 17 and
still drive them, so it reads as "taken in"; an update asking for what the driver records drives nothing, whatever it
returns.)

1. **Every pen stroke goes to the panel through the fastest update while the pen is down** (`StrokePanel`; the
   eraser keeps the gray one, its ring has grey edges): on the Manta before firmware 3.30.45 driver mode 5
   (`Device.fastStrokeMode`; the JNI accepts it for exact black/white pixels only, as mode 9), elsewhere MonoPaint's own
   mode 9 path where it has one (the Nomad; firmware 3.30.45 on, where gray frames queue). When the stroke ends, everything
   it showed and its last change go to the panel as one forced gray update (`DrawingPad.endStrokePreview`, and the same in
   Brush Lab's pad), so the page ends exactly as before; where the panel already shows the right pixels the driver does
   nothing. A non-binary pixel (none should reach it) drops the stroke back to the gray update.
2. **The quick rule, from the panel** (`QuickStroke.PANEL`, `panelReach`): a stroke goes quick when the marks it keeps
   changing under the pen reach past **36 px** (3 mm on the Manta's 300 ppi panel), whatever their kind: its line, and its
   body but for a transparent Drying body, whose live preview is already written once (so the built-in Watercolor and Ink
   wash in Slow, and the Slow presets, still draw live). The engine's rows above still apply. At the Tools page presets'
   own sizes this sends Medium and Large of most paints, charcoal, pastels and hakes quick, and every Fast-wet Watercolor
   and Ink wash preset; pens, pencils, markers and nibs stay live but at Large for the wet brush pen, the bleeding markers
   and Line and wash.

**On the Manta** (`LIVE=1 scripts/bench_device.sh` with `framelog` beside it, the bench's 1.5 s S-curve at 480 Hz,
Fast-wet unless said): how long after the lift the panel was still driving the stroke's frames (the stroke's own run of
frames; the side bar's thumbnail updates about 1.2 s later are left out). Live: every stroke drawn live; quick: every
stroke quick; gray: the gray update only (as before), fast: the fast update while the pen is down.

| Case | live, gray (before) | live, fast | quick, fast |
|---|---|---|---|
| Ink wash 60 / 240 | 479 / 458 ms | 336 / 359 ms | 622 / 623 ms |
| Gouache 60 / 240 | 466 / 430 ms | 245 / 328 ms | 631 / 642 ms |
| Oils 60 / 240 | 467 / 487 ms | 274 / 380 ms | 633 / 641 ms |
| Watercolor 60 / 240 | 455 / 453 ms | 321 / 342 ms | 618 / 642 ms |
| Charcoal 60 | 348 ms | 264 ms | 641 ms |
| Brush pen 60 / 240 | 463 / 405 ms | 247 / 255 ms | 623 / 631 ms |
| Watercolor Slow 120 / 240 | 220 / 217 ms | 99 / 110 ms | (live) |
| Ink wash Slow 120 / 240 | 221 / 243 ms | 106 / 93 ms | (live) |

A live stroke on the fast update ends 100–220 ms sooner. A quick stroke's true stroke lands about 0.63 s after the
lift (its render and compose, then a gray update), but under the pen its preview is written once and shows one fast
update (about 145 ms) after the nib's edge reaches it. What the pen leaves behind while drawing, from the desktop model
of the dots (`StrokeLagSim`, scratch: a 800 px/s stroke presented every 16 ms; how long after the footprint reached a
dot it last changed, 90th percentile), plus one update: live on the gray update before, quick on the fast one now:

| Case (px) | Before: live dots + 270 ms | Now: quick dots + 145 ms |
|---|---|---|
| Gouache 120 / 240 | 25 + 270 / 39 + 270 | 15 + 145 / 16 + 145 |
| Watercolor Fast-wet 120 / 240 | 34 + 270 / 58 + 270 | 16 + 145 / 16 + 145 |
| Ink wash (Grey wash) 120 / 240 | 67 + 270 / 125 + 270 | 73 + 145 / 135 + 145 |
| Charcoal 60 / 120 / 240 | 60 + 270 / 109 + 270 / 212 + 270 | 42 + 145 / 75 + 145 / 140 + 145 |
| Bleed marker 60 / 240 | 73 + 270 / 219 + 270 | 69 + 145 / 198 + 145 |
| Hake 120 / 240 | 73 + 270 / 134 + 270 | 75 + 145 / 139 + 145 |

So the mark under a big brush now shows roughly 0.15–0.3 s after the pen instead of 0.3–0.5 s; most of the gain is the
fast update, the rest the preview written once (the sticks most). Not measured: how each mode looks (the frames say when
the panel is done, not how a pixel gets there); the owner checks by eye.

Same page: the final stroke is the quick stroke's true render or the live stroke as before (`QuickStrokeChecks`
unchanged but its rule rows; the live bench's 210 hashes don't depend on how the panel is driven).


#### Lift after the fast update (2026-10-16, branch `inkwash-liftbug`)

The owner, on the build where only quick strokes took the fast update (`4a4475f`): "the issue with the app not
refreshing properly on pen lift is still happening", on the drawing screen and in Brush Lab, for "any large size". His
photos: after the lift a big stroke shows teeth and rings, black and white and update-sized, where the page is a smooth
grey or solid black. The system's full refresh (a swipe on the side bar) shows it right.

**Measured** (a bench build that logged every panel update with the driver's own result, the true stroke's write to
the page, and, at the fast session's end, each pixel of the stroke box against the two sessions' records; the panel's
frames from `framelog`; `LiveStrokeBench -e cases`, the bench's 1.5 s S-curve as real pen events):

- **Only the preview reaches the panel through the fast session.** Its last update comes before the lift (Gouache 120:
  2 ms before). The true stroke is written to the page 60–900 ms after the lift, and the fast session has stopped
  showing before that render. So it is not the true render leaking through mode 5.
- **The gray update at lift drives almost nothing.** It is accepted (driver result 0, then 21 frames), but the driver
  drives only the pixels whose value changes from its record, and the record is what the fast update showed. Pixels the
  fast session changed to the value the final keeps, out of all it changed: Gouache 120, 347,640 of 351,190 (opaque
  paint previews as its own flat grey); Gouache 40, pressed lightly or hard (quick either way), 79,179 of 81,467 and
  115,543 of 117,969; Charcoal 240 Fast-wet, 364,608 of 492,804; a Slow wash with Smear, 0 (its light preview differs
  from the final everywhere).
- **Nothing in the app ever drives them again.** The page's auto-refresh 2–3 s later is a forced full-page gray update.
  The driver answers 17 and the panel runs 0 frames.
- What the fast update left in them is what the photos show. A run of mode-5 updates a few ms apart leaves pixels
  part-driven: one area changed 30 times 12 ms apart was done 86 ms after the last change, not 145 (the panel
  findings above). Only a full refresh, which drives every pixel, clears them.

**The fix: strokes take the gray update again** (`StrokePanel.wants`: none; the bench's `quick` and `all` put quick
strokes, or every pen stroke, on the fast path). The preview under a big brush shows about 0.12 s later than on the
fast update: one gray update (270 ms) after the brush's edge reaches it, not 145 ms. The page is unchanged.

**A redrive, for the bench.** The fast session's end, wherever it is used, now goes through `DirectEink.endFast`
(`panel_end.h`). It sends two gray updates back to back. The first is the final, except the pixels the fast session
changed to what the final keeps: those ask for the page before the stroke (the gray session's record), so the driver's
record there differs from the final. The second sends those pixels as the final. On the Manta it works mechanically:
the panel drives the end for about 42 frames, two full gray runs one after the other, where the old end ran 21. But
those pixels go to the page before the stroke and back, so the stroke most likely blinks for about a quarter second
after each lift; the panel finishes about 0.07 s after a gray-only stroke. Not shown to the owner yet; `near` (ask for
a level next to the final rather than the page before) might not blink and is untried. A pair of sessions that don't
share a plane (the Nomad's mode 9) ends as before.

**Checked.** `panel_end_check` (desktop, C, run by `scripts/test_inkwash.sh`) runs `panel_end.h` against a fake
driver: it keeps a record of each pixel, drives only the pixels asked to change, and marks those a fast update drove
as part-driven. The old end leaves 1,279 of its pixels part-driven; the new one leaves none, also with `near`, with a
final that is the preview, and with a final unlike it. A busy display that refuses the first update leaves the record
and the panel as they were, and a refused second update goes again at once.

Manta, the bench build as the app now draws (`-e cases` on the drawing screen and on Brush Lab's test page; Gouache 120
and 40 at light and hard pressure, Charcoal 240 Fast-wet, a Slow wash with Smear, Pencil 20 live). No stroke opened a
fast session; every update was gray. The true stroke was written 6–890 ms after the lift, and the next gray update
covering it followed 10–60 ms later and ran 21–41 frames. On the bench's fast path (Gouache 120), `endFast` came 30–70
ms after the write and ran about 42 frames on both screens.

### The wait after lift (2026-10-16, branch `inkwash-lift`)

The owner: quick strokes "show their final version" about 0.6 s after the lift; heavy ones 1–2 s; Slow washes block the
UI thread 0.3–0.8 s at lift. Measured with each lift's events logged beside the panel's frames (scratch: `LIFTLOG` lines
in a bench build of `DrawingPad`/`QuickLift`/`SelectionFeedback`, `framelog` from the panel work, `LiveStrokeBench`).

**What the 0.6 s was.** For a wash at 240 px the page was final 0.06–0.15 s after the lift, but the panel kept driving
frames until ~0.64 s. The droplet: drawn at the lift while the stroke's fast (mode 5) updates still ran, its grey update
waited for them, and the stroke's own final grey update waited for the droplet's: **the panel runs updates that don't
overlap one after another** (overlapping ones restart, as the panel work found). Without the droplet the same strokes
were done 0.31–0.44 s after the lift. Hiding it again cost as much.

What changed (pages unchanged: the bench's 210 hashes live and quick, against the parent, `QuickStrokeChecks`):

1. **The droplet waits** (`WetIndicator.RENDERING_MS`, 1 s): a quick stroke's render alone shows it only once it has
   lasted a second; a Slow wash settling shows it as before (after the stroke's final update, so it doesn't hold it).
2. **Composed ahead** (`StrokeTones`, `ModularCompose.Page`): the render thread composes what it has drawn every 100 ms
   (`QuickStroke.composeMs`) into tones of its own instead of the page, only while it keeps ahead of the pen (it has
   waited for samples and none wait; on Anything goes, which can't, composing ahead cost more than it saved). It reads
   the page as the stroke found it while the preview draws on it: `ToneDocument.shareBase`/`baseTones` (Undo's captures,
   and a gesture's begin and cancel, under the capture lock while shared; a tile the preview has changed is read from
   its capture). Each pixel keeps the tone last composed for it, so the tones written at lift are what composing the
   whole stroke once wrote: a row with paint through `coverTones` as before (a pixel without paint now passes coverage
   −1, which the page reads as 0, so the kept tones know which pixels laid paint). After lift the thread, at the usual
   priority with the usual helpers, draws what is left and composes the rest (`ModularStroke.develop`), a Drying lift's
   settle and final compose too (off the page, over the kept preview tones, with the restore pass composing over the
   preview did: `StrokeTones.applyTo(page, rect)`); the pen's thread only writes the tones, in bands on four cores
   (0.04–0.18 s on the device, was 0.06–0.9 s). A smudge's render composes on the page at finish as before (its plane
   reads and writes the page).
3. **A Slow wash lifts off the UI thread** (`LiftingStroke`, Inkwash's own beside `DrawingStroke`; `QuickLift` looks in
   on either kind): a live Drying stroke with a session lays its line again, settles and composes off the page on a
   thread of its own; the UI thread then writes it (0.1–0.12 s, was 0.4–0.56 s). Its final is about 0.05 s later than
   before (the written tones take a second pass); a smudge's lift stays on the UI thread.

Manta (firmware 2606141001), awake, `LiveStrokeBench` mode `fast` (the app's rule), the bench's 1.5 s S-curve; before:
`6b5db21`. Final: lift to the page final (and the app's stroke written); panel: when the run of frames holding the
stroke's final update ended (a Drying wash's droplet, drawn after it, can add its own update to that run):

| Case | Before | After |
|---|---|---|
| Watercolor FastWet 240 | 158 ms, panel 643 ms (quick) | 67 ms, panel 344 ms (quick) |
| Watercolor FastWet 360 | 137 ms, panel 648 ms (quick) | 102 ms, panel 384 ms (quick) |
| Watercolor Drying 240 | 391 ms, panel 892 ms (live, UI blocked 396 ms) | 453 ms, panel 952 ms (lifts off the UI thread, UI 115 ms) |
| Watercolor Drying 360 | 402 ms, panel 904 ms (live, UI blocked 415 ms) | 455 ms, panel 956 ms (lifts off the UI thread, UI 120 ms) |
| Charcoal FastWet 240 | 299 ms, panel 646 ms (quick) | 324 ms, panel 649 ms (quick) |
| Charcoal FastWet 360 | 320 ms, panel 666 ms (quick) | 249 ms, panel 578 ms (quick) |
| Charcoal Drying 240 | 657 ms, panel 1011 ms (quick) | 580 ms, panel 917 ms (quick) |
| Charcoal Drying 360 | 684 ms, panel 1037 ms (quick) | 623 ms, panel 966 ms (quick) |
| Anything goes FastWet 240 | 321 ms, panel 637 ms (quick) | 330 ms, panel 652 ms (quick) |
| Anything goes FastWet 360 | 923 ms, panel 1259 ms (quick) | 1015 ms, panel 1346 ms (quick) |
| Anything goes Drying 240 | 337 ms, panel 642 ms (quick) | 304 ms, panel 624 ms (quick) |
| Anything goes Drying 360 | 541 ms, panel 864 ms (quick) | 607 ms, panel 931 ms (quick) |
| Ink wash FastWet 240 | 104 ms, panel 641 ms (quick) | 49 ms, panel 324 ms (quick) |
| Ink wash FastWet 360 | 177 ms, panel 652 ms (quick) | 174 ms, panel 490 ms (quick) |
| Ink wash Drying 240 | 550 ms, panel 1052 ms (live, UI blocked 562 ms) | 584 ms, panel 1084 ms (lifts off the UI thread, UI 99 ms) |
| Ink wash Drying 360 | 683 ms, panel 1011 ms (quick) | 719 ms, panel 1234 ms (quick) |
| Oils FastWet 240 | 142 ms, panel 639 ms (quick) | 65 ms, panel 336 ms (quick) |
| Oils FastWet 360 | 242 ms, panel 654 ms (quick) | 245 ms, panel 570 ms (quick) |
| Oils Drying 240 | 322 ms, panel 641 ms (quick) | 312 ms, panel 632 ms (quick) |
| Oils Drying 360 | 410 ms, panel 695 ms (quick) | 397 ms, panel 724 ms (quick) |

Item by item: washes, oils and ink washes drawn quick end **0.25–0.3 s sooner** on the panel (the droplet), and their
page is final in 0.05–0.25 s. Charcoal and Anything goes are bound by their render: on the device it draws 1.3–2.4 s
of work for a 1.5 s stroke (Anything goes at 360 px is 300 samples behind at lift), so they gain little; **re-tapers**
(item 3 of the brief) were measured and left: on the bench stroke a stick's Fast-wet line re-tapers 10 times, 7 of them
with a taper; its cost is mostly laying each dab a second time into the frozen line (`rawL`), which every exact re-taper
needs, and the window re-laid is short for charcoal (40 px at 240). A cheaper exact re-taper for tapers that are 0 (no
fall, stillness or flick) would save a third of the re-tapers' window work at most. **Ink wash and Oils at 360 px**
(item 4) go quick under the 3 mm rule and are fine: pen to preview a few ms, final 0.17–0.25 s, panel 0.49–0.57 s.

Still on the UI thread after a Slow wash's lift: the settling ticks (`DryingWater.step`, desktop 7–11 ms a tick at
240–360 px, the first, which builds the patch, 40–60 ms; about 20 times that on the Manta, paced by `SettlePacer`). A
pen-down during one waits for it. Next, if needed: step the patch off the UI thread and hand its tones over as
`StrokeTones` hands a stroke's.

### Settling off the UI thread (2026-10-16, branch `inkwash-settle`)

The last stall after a stroke: a Slow wash's settling ticks (`DryingWater.step`, through `DryingTicker`,
`WatercolorSession.tick`, `SettlePacer`) ran on the UI thread, as did gravity's ticks (`GravityFlow`) while a wash is
wet and a Float bath's drift ticks and print. A pen-down during one waited for it, and then for the wash laid where it
was (`finishNow`, on the UI thread too). (`BodySettle` already ran on the lift's own thread since the wait after lift.)

What changed (every page as before, below):

1. **Work, then write.** A tick and laying it now are worked out off the page and written in one pass:
   `DryingWater.work` / `finishing` read the page and keep what they lay in a `StrokeTones` (`GravityFlow.TonePage`
   flushes into it), `apply` writes it in one gesture, a continuation of the stroke's (in bands on four cores);
   `FloatWater.work` / `printing` (the drift and its film, the print's film over the layer as found, kept through
   `StrokeTones.smeared`, a smear's writes), `show` writes them. `step`, `finishNow`, `tick` and `print` do both at
   once, so the desktop checks run the very code the app runs. On the desktop the same work is a little faster
   (Watercolor Slow at 240 / 360 px: a tick 7.5 → 4.7 / 11.3 → 7.0 ms, laying it now 8.1 → 3.3 / 12.2 → 4.9 ms): the
   lay and the lift's read now share their rows out over the cores (a cell's noise is known from its place,
   `WildBrush.Random.ahead`, so the rows are read on their own), and the writes go in bands.
2. **A thread for wet paint** (`SettleWorker`: one per session, the drawing screen's and Brush Lab's; lowest
   priority, sharing work with `Cores`' quiet helpers, so a stroke's own work comes first). `WatercolorSession.tick`
   starts a tick there and answers `SettleWorker.POLL_MS` (5 ms) until it is done, then writes it and shows it as
   before: the next tick `SettlePacer.TICK_MS` after, a tick whose work there and write here pass the 40 ms budget
   backs settling off 100 ms, shows at most every 120 ms. Ticks still step by the patch's age, so the page ends the
   same however they are paced. The page is read there only while nothing else writes it: anything here that writes
   first (a stroke, a layer change, the tap, Undo) takes the work in or lets it go (a wash's work stops at its next
   row; work let go is never read), and a tick whose page changed meanwhile is dropped unwritten.
3. **Laying it now, worked out ahead.** Right after the lift (the patch's read, `lift`, then `finishing`, which
   leaves the patch as it is), and with every tick that changes it, for the page as the tick leaves it
   (`finishing(after)`: a pixel the tick writes shows the tone it asked, and laying lays a pixel only below that), the
   worker works out what laying the wash now would write; for a bath, the print after a Float stroke and with every
   drift (`printing(tick)`, over the box the drift leaves it showing in). A new stroke, a layer change, a pause, another
   page or the droplet's tap (`settleNow`) writes that and lets the tick running there go: the pen waits only for that
   write. It waits for more only just after a Slow wash's lift (until the lift's read and its laying are worked out)
   and for a wash gravity has moved: its flow can't be laid ahead without copying it, so a tick running there is
   written first and the wash laid here, as before. A Float stroke joining the bath waits for a drift running there.

Checks: `SettleWorkerChecks` (desktop, the session with a worker as the ticker drives it): Slow washes, Slow bloom and
gravity washes on a tilted page (Drips, Pooling wash) and a drifting bath end on the page ticking here gives, layers
and one Undo alike; laid while a tick runs there, at 0, 1, 2, 5 and 9 ticks, a wash is laid as it shows, to the pixels
laying it after as many ticks here gives (a wash gravity has moved: after the tick running there), under masking
fluid too; laid while a tick
waits there behind other work, it doesn't wait for it; a page changed meanwhile (Undo) drops the tick unwritten; a
bath printed while a drift runs there prints as it shows, and a Float stroke joining it joins after it; paced (backing
off, showing at most every 120 ms) it ends the same. Unchanged: every other check, the bench's 210 page hashes (live),
and the pages of 131 settles (7 Slow and gravity washes at 60, 150 and 260 px, laid now after 0 to all of their ticks,
then undone, and a medium bath drifting on a tilted page printed after 0 to all of its ticks; scratch `SettleHashes`).

Manta (firmware 2606141001, awake), `LIVE=1 scripts/bench_device.sh -e settle true` on the bench build, before
(`e48a754` with this bench) and after, the same session. Each case drawn as the bench's 1.5 s S-curve (real pen
events), Slow (Drying, Settle .5); Drips is the Tools page preset on a page tilted (0, .8), the gravity bench's tilt;
the bath is the Float bench's medium one (Sumi and Clear water taps at three sites, about 32k points) drifting on that
page. Settled alone: from the lift's own work done until dry; the page's thread held: its longest message while it
settles; ticks: before, the drying ticker's messages on the page's thread (first / mean / max ms), after, their work on
the worker and what the page's thread spends writing and showing each. Pen-down: a short stroke put down 50, 150, 300,
450, 700, 950, 1200, 1600 and 2000 ms after the lift's work, how long from the pen's down until its stroke had begun
(the wash laid or the bath printed first); mean / max of the nine, and the mean of the first four (just after the
lift) / of the rest. A pen-down on a dry page: 21–34 ms.

| Case | Settles | Page's thread held, longest | Ticks (ms) | Pen-down: mean / max; first 0.45 s / later |
|---|---|---|---|---|
| Watercolor 120 | 7.3 → 6.5 s | 883 → 68 ms | 883 / 99 / 245 → work 446 / 58 / 136, here 57 / 26 / 68 | 351 / 787; 570 / 176 → 75 / 189; 116 / 42 |
| Watercolor 240 | 10.1 → 8.1 s | 1179 → 109 ms | 1179 / 186 / 429 → work 503 / 102 / 236, here 73 / 37 / 108 | 774 / 1463; 1093 / 518 → 126 / 323; 206 / 63 |
| Watercolor 360 | 13.0 → 9.8 s | 1761 → 136 ms | 1761 / 254 / 599 → work 723 / 135 / 324, here 136 / 44 / 106 | 1277 / 2176; 1715 / 927 → 193 / 414; 308 / 101 |
| Ink wash 240 | 9.8 → 8.8 s | 1171 → 82 ms | 1171 / 173 / 401 → work 538 / 123 / 276, here 69 / 34 / 81 | 760 / 1389; 1070 / 512 → 136 / 356; 222 / 67 |
| Drips 120, tilted | 69 → 71 s | 1641 → 87 ms | 1641 / 139 / 339 → work 1334 / 138 / 335, here 72 / 28 / 87 | 708 / 1387; 1015 / 462 → 119 / 286; 107 / 128 |
| Float bath, tilted | 17.0 → 17.7 s | 176 → 49 ms | 124 / 91 / 176 → work 82 / 72 / 98, here 46 / 41 / 49 | 168 / 241; 171 / 166 → 54 / 76; 63 / 47 |

What is left: a Slow wash's pen-down within about 0.4 s of the lift waits for the lift's read (the first work ahead:
0.17–0.38 s of lifting at 120–360 px, then its laying, 0.24–0.47 s in all; on the desktop the lift reads 4× faster on
four cores but on the Manta the panel and the lift's own writing share them then), up to 0.19 / 0.32 / 0.41 s at 120 /
240 / 360 px (was 0.79 / 1.46 / 2.18 s); after that a pen-down waits only to write what was laid ahead (26–96 ms) and
for its stroke to begin. A gravity wash's pen-down once its flow is made waits for the tick running there (if any) and
the laying here, 0.13–0.17 s (`laid now here`), about as before but without the tick in front of the pen's event;
with no flow yet (a page still level) it is as a Slow wash's. The page's thread now spends 26–44 ms on average writing
and showing a tick (was the whole tick), the longest under 0.14 s. The patch settles sooner (its ticks no longer wait
for the page's thread's other work); the bath's drift keeps its pace (the print worked out with each drift fills the
wait before the next).

The gravity bench (`scripts/bench_device.sh -e gravity 120,240 -e wide 600 -e names Drips`, the step and its write on
the bench's thread, as before) is unchanged within noise: Drips at 120 / 240 px mean 40.5 → 43.0 / 86.9 → 81.1 ms,
p95 74 → 77 / 160 → 147, the first tick 270 → 238 / 543 → 397 ms (the lift's read on the cores); the page's write
3.3 → 1.7 / 5.8 → 3.1 ms (in bands).

Next, if needed: the lift's read handed over from the lift's own thread (it reads the page the lift is about to
write, so it could start with the lift's tones as `finishing(after)` reads a tick's); a gravity flow copied ahead of
each tick (copy on write by tile), so its laying too can be worked out ahead; a Float stroke joining the bath while a
drift runs there could start from the drift's points kept.

## Bristles (Stage 3, 2026-10-15)

Branch `inkwash-bristles`. The Tip choice Bristles draws (`ModularFeatures.BRISTLES` on): the web lab's swarm (engine.js
604–716, from marble-and-bristle, which the owner liked: "marble and bristle are very cool"), with the owner's note on
it answered: "it seems to run out of ink quickly.. is that necessary?" It now carries its ink further, splays with
pressure, shows its hairs' tracks, and dries out gradually into streaks and dry-brush gaps at the end of a long stroke.

### What draws it

- **`BristleSwarm`** (pure, its own stamper; the swarm is stateful in `LineStamper.Meta.swarm`, as the hake's ribbon
  is): N hairs (6–32) from `seed ^ hash(first sample)`; per step each senses a shared trail field ahead-left, ahead-right
  and ahead (cells of 2 units fading .93 a step), moves toward moderate trail and away from crowding (Cohesion sets the
  gain and the tuft size), inside a soft wall that opens with pressure and speed (Splay), with a seeded wander and a
  spring to its slot. A hair's grey is how many share its line (a lone hair .42, a tuft black). A brush of radius R
  over 10 px moves in units of k = R/10 page px (a big brush is the small swarm enlarged). Laid as `LineStamper` lays
  its dabs: a feed's hairs are worked out step by step, then laid in bands of tile rows on `Cores`, each band in order,
  keeping the strongest coverage; a Fast-wet body's hairs mark `wetD` as they go.
- **`ResolvedBrush`**: `LineTip.BRISTLES`, `BodyTip.BRISTLES` (both stateful: Drying's lift lays nothing again),
  `lineSwarm` / `bodySwarm` (`Swarm`: hairs, cohesion, splay, dryness, Ink load, the hand's Pressure → size, tilt to size
  and dark, darkness cells, page tilt to size and dark; the body's amount), and the tip's `bb.bristle` 0 (parts.js).
- **`ModularStroke`**: `stampL`/`stampW` call it with the line's or the body's radius; `reach` is the swarm's
  (`BristleSwarm.reach`: its wall at full pressure and speed with overshoot and a hair each side, about 2.2 × the size
  for the presets); Fast-wet's settle box takes in the splayed hairs.

### Where it differs from the web lab, and why

- **Ink load** (new, the Tip's last dial, `INK_LOAD`, `ModularCode.ADDED`: written only off its default .5, so every lab
  code reads and writes as before). A hair gives up `.0009 · Dryness³ / carry` of its load a page px (× thirst and
  speed, as the lab), `carry = 2 · 4^(2·load − 1)`: twice the lab's distance at the default, half at 0, eight times at 1.
  At Dryness .75 (Bristle brush) streaks begin about 790 px along (the lab's 395) and the band across the stroke is
  still 64% inked at 800–1100 px, about 46% by 1500–2000 (the lab's load: 45% and 9%). Dryness .15 (the lab's default)
  never runs out within a page. "This brush" says how soon (`BristleSwarm.streaksAt`).
- **Streaks.** The head has lanes fixed across it (1.25 page px, or .45 k, and at least 1.25 lattice points), each with a
  seeded threshold that drifts slowly along the stroke. A dry hair inks a lane only while it holds more than the lane
  asks (every lane at a load of .85 or more, none under .35), so a drying brush breaks into streaks along the stroke
  before the lab's tooth test thins what is left into dry-brush grain. A big head's tufts always leave 8% of its lanes
  bare (eased in from k 1.5 to 4.5): its own hairs' tracks in a loaded stroke. With every lane inked (a small brush,
  or any brush still holding .85) nothing changes from the lab.
- **Pressure → size** (the hand's cell) splays the head, `lerp(1 − .6 ps, 1 + .5 ps, pressure)` on the wall: a light
  touch gathers the hairs to the tip. The lab's swarm ignored it. On the 60 px Bristle brush (Pressure → size .5) a
  light stroke is 41 rows across, a pressed one 99; with a steady hand 56 and 83 (the lab's own splay).
- **Steps on a distance grid for a big brush.** The lab stepped at least once a sample, so a big brush's hairs swirled
  and ran dry faster the more often the pen reported (at 480 Hz, three times as fast at 120 px). A brush over 10 px
  radius steps every k px of travel wherever the samples fall, and lays each hair swept from where it was the step
  before (no beads where steps lie further apart than a hair is wide). At 10 px radius or less it steps as the lab.
- **Each swarm keeps its own trail field**, in tiles of 64 × 64 cells made as hairs reach them (the lab's line and body
  swarms shared one page-sized array, the body's start clearing the line's first trails).
- **Page tilt** (the hand's page-tilt cells) sizes and darkens the hairs as it does every stamper's marks.
- StrictMath for the swarm's powers and trigonometry, so hairs walk the same paths on every device.

Matching the lab (`BristleSwarmChecks`, its engine.js run in Node on the same strokes): at 12, 16 and 20 px the coverage's
sum, area, centre and spread across agree to within float rounding, and each row across a straight stroke to within 1
of about 600; the line case differs from the lab in no pixel by more than 1e-6.

### Rolls, words, presets

- **Rolls** use the lab's weights (Dry brush: Round 2, Bristles 2, Flat nib 1; Anything goes: Bristles 1 of 12) but decide
  Bristles last of all (`ModularFeatures.late`, `ModularStyle.rollBristles`), after Smudge's late choices and Float, and
  from the brush's own seed as Float is (`new Random(seed ^ 0xb415e5)`): a roll picks its tip from the other choices as
  before, every other part rolls exactly as before, then the seed makes it Bristles on the lab's odds (its weight over
  the tip's) with Bristles' dials from the style's specs. No draw is added to the Random, so every later roll from it
  is as before too (Smudge's roll CRC, which turns Float and Bristles off as `floatRolls`/`bristleRolls`, holds). Only
  styles that weigh Bristles change at all. The tip's die decides from its own Random; a variation's swap to Bristles
  is drawn from the card's own seed, so the variations' other draws are unchanged. Anything goes rolls Ink load over
  its whole range.
- **Unchanged:** `LegacyConversionChecks`, and the bench's 210 page hashes are identical to `001cee1`'s (neither the Dry
  brush nor the Anything goes case rolls Bristles from its seed).
- **Words**: the lab's "splits into strands that rejoin"; "soon runs dry into streaks" or "runs dry on long strokes" when
  streaks start within 800 or 2000 px.
- **Tools page** (hand-set, as gravity's): Bristle brush under Dry brush (ink, Dryness .75, Splay .6, cold press,
  Pressure → size .5: "dries streaky") and Hog bristle under Oils (Cover with impasto .6 on canvas, Ink load .7:
  "ridged hairs").

### Quick then render

- Under the panel's rule (`QuickStroke.PANEL`, 36 px) a bristle brush goes quick once its splayed hairs reach past
  36 px (`panelReach` counts a bristle body's swarm, `ModularStroke.bodyReach`): about 16 px and up for the presets.
  The preview is a disc as wide as the wall at each sample plus a hair (`BristleSwarm.wall`, `hairRadius`); the true
  stroke renders from the same samples (the swarm depends only on them), so a quick stroke ends as live.
- The engine's row, `QuickStroke.BRISTLES` = 540 px of reach (line or body, any life), from the Manta (2026-10-16,
  `-e quick off`, Fast-wet, pen to render at the 95th percentile; the owner awake, so the screen may have been on):
  | Preset | 60 px (reach) | 120 px | 240 px |
  |---|---|---|---|
  | Bristle brush (ink) | 4 ms (134) | 16 ms (268) | 23 ms (536) |
  | Hog bristle (impasto) | 13 ms (125) | 55 ms (250) | 134 ms (500): quick by Fast-wet impasto's 280 |
  The quick runs (`-e quick rule`) were not timed on the device (the session was kept short once the owner was up).
  Desktop (`bench_desktop.sh`, Fast-wet): 0.08–0.21 ms a sample mean, about Ink wash's.

### Checks

`BristleSwarmChecks` (desktop): the lab match above; the same samples and seed lay the same hairs fed one, three or all
samples at a time and on one core or several, another seed other hairs; pressing splays; ink lasts and then runs dry
gradually, further than the lab's load, never at Dryness .15; quick ends as live (Bristle brush and Hog bristle, Instant,
Quick, Slow, 40 and 160 px); Dry brush's rolls for seeds 0–11 equal the build before Bristles except the tip where it is
now Bristles, and 300 rolls each of Dry brush and Anything goes from one Random equal the same rolls without Bristles
but for the tip; the lab's odds over 3000 rolls; other styles never; the tip's die and a variation reach it; Ink load's
code and words. test_inkwash.sh 54 PASS (BristleSwarmChecks new), test_document.sh 89 PASS (on `001cee1`); the bench's 210 page hashes identical to `001cee1`'s.

## Gravity (2026-10-15)

The owner approved the Gravity Lab prototype ("bring gravity into the app ... and the lab"): wet paint moves when the
tablet tilts. Reference: the Lab's `src/gravity.js` and `NOTES-gravity.md` (scratchpad `gravitylab/`); plan
`docs/archive/superpowers/plans/2026-10-15-gravity.md`; branch `inkwash-gravity`. Only paint that is still wet moves, only
while the page is tilted, and never once dry (`PHILOSOPHY.md` #4: wet paint, for its few seconds).

### Dials (appended; old codes unchanged)

| Part · choice | Dials (last in the list) | What they do |
|---|---|---|
| Drying · Slow | Runs, Drips, Pool (`G_POOL`, key `pool`), Sink, Blend | after lift, until dry (`GravityFlow`) |
| Drying · Quick | Runs, Pool, Sink | in the window under the pen |
| Medium · Charcoal | Dust | at lift, specks fall downhill |
| Control (every hand) | Page tilt → size, dark, width (`gs gd gw`, after Tilt range) | drawn downhill wider, uphill lighter, a nib opening downhill |

All ten are `ModularCode.ADDED`: written only when one is off its default, so every older brush keeps its code byte
for byte. A Gravity Lab code with page-tilt cells reads differently here (the Lab has no Tilt range before them); its
other gravity dials read the same. `KEEPS`: Life keeps Runs, Pool and Sink across Slow ↔ Quick. A Slow brush with a
gravity dial dries over the Lab's 0.75–60 s (`PartDial.wetMs(d, true)`: `750 + 19250 d² + 40000 d⁶`, within 0.1 s up to
3 s); every other brush keeps 0.75–20 s, so no old brush dries differently. Rolls: Anything goes rolls gravity off (the
Lab's rule); Watercolor rolls a little Runs and Pool one time in five, Charcoal and Pastels some Dust one in four, drawn
after every part (`ModularStyle.rollGravity`) so the styles' other rolls are unchanged; no style rolls Drips. Variations
never turn a gravity dial on.

### What never moves

- Instant (None); opaque Cover (`ResolvedBrush.grav` is null); a line without a wash (no body); dry paint.
- A level page: the flow is built only at the first tick the page is tilted (`DryingWater.step`), so a level page runs
  the old code path exactly. `PageTilt.FLAT` (MonoPaint's `CanvasGravity` dead zone, gravity's share .08 in the page's
  plane, about 4.6°) is level: a tablet on a desk never creeps. Past it the strength rises from 0.
- A wash whose flow would take more than 48 MB (`GravityFlow.MOST_BYTES`) settles as if level.

### Where it lives

- `GravityFlow` (pure): the Lab's `Flow`. Cells of `cs` pixels hold water, mobile pigment, sinking grain, settled
  pigment and grain and the paper's noises (hold, cond, evap, pot, pin, dep); each pixel its wash share (`cur`), what
  is in the paper (`fixed`), its share of its cell (`wgt`) and tooth. A tick: excess blur, Runs (upwind, load = water
  + 1.2 pigment, the pin), Pool, pairs (pressure, mixing, pot), edge flow, Sink, blooms, Blend, drips (spawn along
  the low edge at the bead's wavelength, feed, speed, width, merge, mouth, trail, bead; see "Drips" below), settle, and
  a render every other tick. Drips are drawn at every pixel, on the ticks between the wash's renders. The page: `TonePage`, the layer's tones as floats, read on
  first touch, every change a multiply (`page *= (1 − new) / (1 − old)`, lines over or under kept), written once a
  tick where a tone rounds differently, in the stroke's continuation gesture (one Undo).
- `DryingWater`: holds the flow beside its patch (`moves`, `flow`); the patch's settling lands in the flow's paper share
  (`addFixed`) and shows at the next render; `finishNow` (the wet indicator, a new stroke, a pause) lays what is
  pending and stops the flow and its drips where they are.
- `WatercolorSession`: `tilt` (a `PageTilt.Source`), `wantsTilt` (a tilt-reading stroke is down, or a wash that moves is
  wet), `tiltChanged`, `strokeReadsTilt`; `tickDrying` passes the tilt to the step.
- `ResolvedBrush`: `Grav` (Slow all five; Quick Runs, Pool, Sink), `dust`, `wetMs`, the hand's `gSize gDark gWidth` on
  the pen, body and hake. `settlesAfterLift` is also true for a gravity wash with Settle 0 (it stays wet to move).
- `StrokePoints.ga` (travel direction · tilt, read per sample) and `StrokeMath.gSize/gDark/gWidth` in `LineStamper`,
  `BodyStamper`, `HakeRibbon`, `Placement`; `ModularStroke`: Quick's window (`GravityFlow.Window`, in `settleBand`),
  charcoal's dust at lift (on the stroke's layer, in its gesture).
- Android: `TiltFeed` feeds a session from MonoPaint's `CanvasGravity` (accelerometer, low-pass .2, first reading used as
  is, turned by the display's rotation; Inkwash's page never turns with its app-only rotation), listening only while
  `wantsTilt` and the window has focus (so not while paused), 4 s after for the next stroke. One in `InkWashBar`, one in
  `BrushLabActivity`: the lab's test page tilts with the tablet, and its wet indicator dries it now.

### Cells and the lattice

The prototype's cells are 2 pixels. A stroke past the lattice's 100 px (`ModularStroke.latticePx`), or a wash whose box
would hold more than 48,000 cells (`GravityFlow.MOST_CELLS`), works in cells of 4 pixels (8 for a wash over much of the
page). Every pixel still keeps its own share of its cell, so the edge and the paper's grain stay at full resolution;
what the Lab measures in cells is scaled by `k = 2 / cs` (how far paint moves a tick, the bead's threshold by k², the
scale of lobes, blooms, beads and windows), so a wash moves as far on the page a tick in either. In cells of 4 a render
shows only what moved since the start (`P0`, `A0`), so the stroke's soft edge is not redrawn in 4-pixel steps.

### Checks

`GravityChecks` (desktop; gravity.cjs's checks, on the Lab's 520 × 460 page with the Lab's wave, every pixel as the
Lab): Runs moves the centroid downhill (both ways); the pigment ledger is conserved (0.0000% drift) while its centre moves
19 and 9 px downhill (Pooling wash, Drips at tilt (0, 1)) and the uphill third lightens (63 → 28, 68 → 44) as the low
quarter darkens (56 → 66, 61 → 72); Pool darkens and beads the low edge (CV .07 → .36, ±9 px along it, not a
translation), fading smoothly into the wash on a paper with no texture; Drips (as reworked, below): 2–6 at tilt 0.6,
each at least a fifth of the wash's height wide (21–24 px of 81, on the page too), starting at the low edge (within
5 px); over 12 strokes (tilts .6 and .8) widths a little varied (CV .08, 0.87–1.12× the mean), spaced unevenly about
the edge's wavelength (median gap 4.3 widths, CV .47, never under 1.1 widths), two side by side joining now and then
(2 joins), lengths varied (CV .29, the shortest 93 px of a mean 217) with the heavier running further (correlation
.83 with the load), the most lean .075; darker than the wash (89 vs 70), a bead that dries with a hard edge where one stops on the page (229 vs 126), no bands along a drip on a paper
with no texture (largest step .56 of 255), an even width without jumps (CV ≤ .07), none level, 12× its width at Drips 1
and tilt 1, longer the steeper (the longest by tick 90: 42 px at tilt .5, 272 at 1; the mean lengths are capped by the
page's foot now that they vary), rims darker than the middle, a darker bead, straight (off a straight line from start
to end, so a lean is straight: .01 of the width at tilt 1, .03 at .5), stopped where they are by a level page, merges when the tilt swings
hard, the same seed the same page; Sink (texture ratio .93 →
1.46); Blend softens the edges when rocked (steepest step 10.3 → 7.9); Instant and Cover the same at any tilt; Quick
acts only in its window, and without its dials not at all; dry paint never moves and dries on time; finish now stops
everything; one Undo takes it all back; a level page and a tilt inside the dead zone draw exactly what no gravity
draws; the hand's page tilt; dust only when tilted; presets without gravity draw the same tilted and keep their codes;
the sensor's mapping and dead zone; every tip × medium × Quick/Slow with every dial full draws and dries; cells of 4
past the lattice. Old brushes: `LegacyConversionChecks` unchanged, the bench's 210 page hashes identical before and
after (`ModularPerfBench hashes`).

### Cost on the Manta (`scripts/bench_device.sh -e gravity …`, ms a tick)

Each Slow gravity preset laid along a 600 px wave in the page's middle (as a few seconds' stroke), tilted (0, .8) and
rocked for 4 s, its ticks timed as the drying ticker runs them (the step and the page's write; the screen's render of
the changed area is 1–3 ms more). "Patch" is the Slow wash's own settling, there before gravity: the same washes on a
level page cost 18–23 ms a tick at 120 px (first tick 130–140 ms).

| Wash | 60 px: mean / p95 | 120 px | 240 px | patch (60 / 120 / 240) | first tick (60 / 120 / 240) |
|---|---|---|---|---|---|
| Drips | 25 / 45 | 45 / 86 | 87 / 168 | 8 / 20 / 38 | 178 / 278 / 542 |
| Pooling wash | 33 / 70 | 48 / 90 | 122 / 261 | 8 / 18 / 43 | 195 / 316 / 760 |
| Tilt blend | 37 / 93 | 53 / 122 | 92 / 202 | 8 / 17 / 41 | 180 / 313 / 500 |
| Sinking grain | 23 / 40 | 42 / 75 | 108 / 218 | 8 / 17 / 38 | 163 / 272 / 535 |

Gravity's own share at 120 px is 21–30 ms a tick (runs and pool 5–8, pairs 4–10, render 5–8, the page's write about 4,
drips 2); at 60 px 15–28. Over the pacing's 40 ms (`SettlePacer.TICK_BUDGET_MS`) a tick backs settling off 100 ms, so a
big gravity wash moves at about half speed but ends the same (ticks step by age), and the pen still wins: nothing ticks
while it is down and a new stroke lays the wash first. The bench's S-curve over the whole page is the extreme: 83 ms a
tick at 60 px, 132 at 120 (the patch 36 and 70 of it), its first tick 0.9–1.3 s. Before the work in `24864d8` the
600 px wave at 60 px took 53 ms a tick and at 120 px 65. Next, if needed: per-row spans of the cells that hold paint
(the box holds a lot of dry paper round a curved stroke), Runs' two directions in one pass, and the patch's own
settling on Cores.

### As built: where it differs from the Gravity Lab

- **Built at the first tilted tick, not at lift**, so a level page is the old path exactly; its mobile share is taken from
  the wash as it shows then (a little of the patch's settling may be in it).
- **A render passes over cells where nothing round them moved** (by more than 1e-4), so the parts of a wash that don't
  move keep the stroke's own look; the Lab re-rendered every cell, softening the wash everywhere at its first render.
  The patch's settling shows at the next render (at most a tick later) rather than at once.
- **Cells of 4 or 8 pixels** for big strokes and big washes (above), with the Lab's measures scaled; the Lab has no
  lattice. Drips are drawn at every pixel either way.
- **Drying to a minute only with gravity**; the Lab stretched every brush's drying.
- **Codes:** the gravity dials are written only when on (`ModularCode.ADDED`); the hand's page tilt follows Tilt range,
  so a Lab code with page tilt reads differently here.
- **Presets** are Tools page presets under Watercolor and Charcoal, not five new styles: no new style chips in the
  lab or on the Tools page. Watercolor, Charcoal and Pastels sometimes roll a little gravity; the Lab's base styles
  never did.
- **The dead zone** is MonoPaint's (gravity's share .08, about 4.6°), not the Lab's .04; there is no level to drag
  and no Level button: the tablet's own tilt. A tilt reading arrives about 50 ms after the sensor starts, so a stroke's
  first dabs (the hand's page tilt) can read level.
- **Samples** (library tiles, lab cards, the Tools page) are drawn level: the Lab drew a gravity brush's samples tilted
  (0, .7). Its tile shows the wash; its name and "This brush" say what tilting does.
- **Words:** the Drying module reads "Slow · drips" (its time is on the dial and in "This brush"), Quick's gravity is
  one of "This brush"'s marks, and the phrases are shorter ("drips and runs when tilted") to fit the lab's budgets.
- **Checks:** Blend is judged by how steep the wash's edges stay (rocking softens them), not total edge energy (on the
  app's paper the moved paint's grain adds texture back).
- Not ported: the Lab's `fitDrips` (samples don't run gravity), its spirit level and device-tilt button.
- **Drips** are no longer the Lab's (below).

### Drips (reworked 2026-10-15, branch `inkwash-drips`)

The owner on the Manta, the Drips preset, the tablet at 30–60°: "the drops are still a bit too random and thin...
doesn't look like drops quite yet"; on the Lab: drips "very tiny", "it should be darker", "distinct stripes", "the
pigment should flow down, not just the mark", "wander a bit much from left to right". The Lab's drips started at the
best bead cell anywhere along the edge times a noise (so at random places), were 8–25% of the wash's thickness with a
neck of 0.56 of that, varied a lot in width and length on purpose (stubs that stall), and their trails were about as
dark as the wash. Real runs on tilted paper (and paint drips on a wall) form where the bead along the low edge breaks:
a contact-line instability with a natural wavelength, so drips start at fairly regular places along the edge, each once
enough fluid has gathered there; they are fat rivulets of fairly even width led by a rounded head that holds most of the
paint, run straight down, slow and stop with a bead as the paint runs out, and are darker than the wash because the
pigment goes with them. `GravityFlow` now:

- **Where:** the edge is cut into stretches of `lam` = max(4 drip widths, 90 px) across the downhill (seeded phase,
  each stretch's middle off by up to ±17%, `DRIP_JITTER`; ±6% before the 2026-10-15 variety pass below); only edge
  cells within ±13% of a stretch's middle can start one, the stretch whose bead (water above what the cell had at lift,
  over a window about 0.3 widths across, ÷ (1.2 − 0.4 × its heaviness)) is heaviest first, once past a threshold (5.5 ×
  e0 × (1.45 − 0.45 tilt) × (1.3 − 0.45 Drips) × k², 0.25 at least: lower than the Lab's 7 as the window no longer has
  the noise's up to 1.5× boost); one a stretch (no other stretch's start within 0.5 `lam`; was 0.75); none once every
  stretch along the wash has one. Seeded by stretch (`stretchKind`): about one in eight has none (never two in a row),
  one in seven two side by side (`TWIN_GAP` 1.2 widths apart, ±6% windows, each draining 0.72 widths each way) that
  lean together (0.07–0.1) and join.
- **Size:** width `dripW` = thickness × (0.17 + 0.09 × wetness) × (0.85 + 0.3 Drips), clamped to 3 px and 0.45 ×
  thickness (about a quarter of the wash's thickness: 24 px on a 120 px brush's wash, 45 on 220), each × (0.88 + 0.22
  × its load) × 0.92–1.08 (see Load). It
  takes 85% of the water above 0.9 of the hold over its stretch (across half `lam` each way, back 1.2 widths into the
  wash; most from the middle) and 60% of that water's pigment (the rest stays in the paper: the stretch lightens a
  little, no notch); the bead behind keeps feeding it 6% a tick while its head is within 1.2 thicknesses of the wash.
- **Motion:** v += drive − 0.16 v, drive = tilt × 0.7 × (0.3 + 0.7 √share) × (w0 / 25)^0.3 × (0.3 + 0.7 mobility) ×
  smoothstep(0.04, 0.22, share) (share: its water left / what it took), so speed and length follow its paint and the
  tilt; it stops (dries with a hard bead) when it has slowed to nothing with under 30% left, or runs out; a level page
  sets v to 0 (it waits). Heading: the Lab's inertia, wander 0.004 + 0.06 (1 − tilt)², the paper's pull 0.03 + 0.15
  (1 − tilt) (one sample each side), lean ±0.006: straight down at any working tilt; about three in ten lean
  0.035–0.065 (× (1.2 − 0.5 tilt): 2–4°), still straight.
- **Load** (the 2026-10-15 variety pass): the owner liked the reworked drips but they had swung too regular, "equal
  lengths and near-equal spacing, like table legs or organ pipes". Each drip's load = (its edge's darkness ÷ the
  wash's, 0.8–1.3)² × (0.15 + 1.55 × h^1.2), h its stretch's seeded heaviness (0–1): about 0.1–2.9, 1 typical. Its water
  is lost 1/load as fast per pixel (`lk`), its drive × (0.7 + 0.3 min(load, 1.5)), its width and tone × (0.88 + 0.22 ld)
  and (0.86 + 0.22 ld) (ld the load over 0.15–1.7), its bead × (0.84 + 0.16 min(load, 1)): heavier runs longer, a
  little broader and darker; a light one barely starts. The trail narrows to 0.8 of its width as it runs out (was 0.7).
  A drip that runs into another's trail now bends into its middle 0.8 widths ahead (a Y, not a stub beside it), and the
  joined drip keeps 30% of their mean lean.
- **Look:** width w0 × (share^0.2, 0.7–1.05) × a ±5–7% swell over 3–5 widths; a concave mouth from the edge (1.9× the
  width there, narrowing over 1.3 widths, in the edge's tone, no rims); the trail's tone deepens from the edge's to the
  drip's own (edge or wash × 1.45, ±4%, + 0.05) with darker rims; the bead 1.3–1.55× the width (rounder as it slows), its
  front darker, a teardrop tail into the trail, drawn as the darker of it and what lies there (the trail's rims and the
  wash don't print through it), drying with a dark ring. Inside the wash's own footprint the drip draws nothing and
  over its soft edge the darker of the two shows, so it grows out of the wash without a seam.
- **Cost:** trails are noted as the head moves and laid together with the bead, on the ticks between the wash's
  renders, and only once the head has moved half the bead's radius (so the trail, laid half a radius behind the head,
  never shows past the bead); the moving bead is a stamp worked out again only when its size, tail, tone or heading
  changes by a step; a trail piece skips the start cap the last piece laid; the drip's width is worked out where the
  trail is noted, not every pixel; the drain covers only its stretch's box. `TonePage` now notes each row's changed
  span, so its write covers only that (it was whole 64-pixel rows), which every gravity wash gains from.

Manta (`-e gravity 60,120,240 -e wide 600 -e names Drips`, the same session, alternating builds; ms a tick):

| Drips at | mean before → after | p95 before → after | drips' own | the page's write |
|---|---|---|---|---|
| 60 px | 30.9 → 31.6 | 57 → 54 | 0.7 → 1.8 | 3.2 → 2.9 |
| 120 px | 55.1 → 53.4 | 105 → 93 | 2.2 → 2.4 | 5.4 → 4.6 |
| 240 px | 102.5 → 96.0 | 197 → 174 | 5.0 → 3.4 | 9.0 → 7.5 |

The final build (`6641864`), timed again later in a cooler session against the same before: 23.6 → 25.9 at 60 px,
47.9 → 41.4 at 120, 93.0 → 78.9 at 240 (p95 44 → 45, 92 → 73, 181 → 143; drips' own 0.6 → 1.5, 2.0 → 2.2, 4.7 → 3.2).
Gravity's own share (everything but the patch's settling) is within 0.7 ms of before at 60 px and at or below it at
120 and 240; run to run the same build varies about ±2 ms. The level page and every brush without gravity are
untouched: `LegacyConversionChecks` unchanged and the bench's 210 page hashes identical.

The variety pass (branch `inkwash-drips2`) adds only a few seeded hashes for each low-edge cell the spawn looks at,
and a pair's drains cover less: drips' own cost on the desktop bench (`gravity 60,120,240`, three alternating runs)
0.2 / 0.3 / 0.4 ms a tick before and after, the means within run-to-run noise. On the Manta (one session, before, after,
before, after; ms a tick, mean and drips' own): 60 px 24.6 / 1.5, 24.0 / 1.3, 26.3 / 1.5, 25.9 / 1.3; 120 px 40.7 /
2.2, 42.2 / 1.6, 41.6 / 2.2, 51.1 / 1.9; 240 px 90.1 / 3.2, 86.4 / 3.7, 80.5 / 3.2, 108.4 / 3.9. The last run was
slow throughout (the run, pairs and patch steps the change doesn't touch up about 30%); a second session could not
get the device. The level page and every brush without gravity are untouched (bench page hashes identical).

## Hake start (2026-10-22)

The owner: "the hake brush struggles to find its direction and often leaves a mark that doesn't match at the beginning
before the stroke really gets going.. can you look into that".

Found: a following head took its first angle from the chord to the sample 6 px along (the pen's landing wobble or hook,
without its tilt), laid at once, then could turn only a degree a px; the smoothed travel (`ux, uy`, 12 px) still held the
first segment's direction, and its early wiggles read as tight turns, so the head pivoted with each (a fan of crooked
heads, up to 80 px out at 180 px). The Wet hakes' footprint (`FootprintSweep`, final under the pen) laid the first
sample's head at travel 0 (a vertical stub) and the next at the first segment's direction. Taps (under 6 px) unchanged.

Now (`HakeRibbon.settle/startTravel/travelAt`, twinned in the lab's `engine.js`): a following head lays nothing until the
stroke is `settle(size)` = clamp(0.3 size, 24, 40) px long, then lays from the first sample along `startTravel`, the
chord from a quarter of that span to its end (landing wobble and hook left out), its first angle `target()` there, tilt
included (fixed heads take tilt's turn at the start too). Over the first `settle` px the travel is that direction;
over the next `settle` px it eases into the measured one, so nothing jumps. A stroke lifted sooner lays its start at
the lift (`ModularStroke.hakeLift`, the lab's `Stroke.end`), along the way it went (or across the pen's lean for a
still pen in the sweep). The quick preview and the Wet sweep wait the same (`FootprintSweep.holds`). Only hakes change:
Chisel, Flat Nib and Split Nib keep a fixed angle (their speed turn reads the raw segment) and share none of this.
Cost unchanged (Wet hake 120 px 0.032 ms a sample before and after). Check: `BodyStamperChecks.hakeStart` (a hooked,
wobbly landing: left edge 31 → within 10 px; Wet hake 18 → within 6). `WetPaintChecks` hashes for Wide gouache and
Cover hake updated.

