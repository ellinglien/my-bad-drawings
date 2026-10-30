# Erasable (a Medium dial): design

Status: built (2026-10-16): the web Modular Brush Lab (branch `inkwash-erasable-spec`, "Web prototype" below) and the
app (branch `inkwash-erasable`, "As built in the app" at the end). Elling approved the direction (a Medium dial,
real-media defaults, the eraser and Smudge respect it) and answered the three questions ("Answers" below): the eraser
never touches permanent paint, however hard it is pressed. Builds on `2026-10-14-modular-engine-design.md`
(decision 6, which layer a brush paints; codes; Speed on the Manta), `2026-10-16-smudge-design.md` (own layer,
`CarryPlane`, `smearTones`) and `2026-10-16-float-design.md` (the print).

## In one paragraph

Every brush gets a Medium dial, **Erasable**, from off (permanent) to 1 (erases clean), starting where the real medium
is: ink permanent, pencil and charcoal erasable, a wash and gouache in part. The **eraser takes the erasable part of
each mark it passes and leaves the rest**, so the oldest workflow on paper works without layers: sketch in pencil, ink
over it, erase the pencil, and the ink is exactly as it was. Smudge moves only erasable paint too. The page stores, per
layer, one extra byte a pixel (the share of its paint the eraser can take), only on layers that hold paint that isn't
wholly erasable. Old drawings, old brush codes and every brush that erases fully draw and erase exactly as today.

## What Elling sees

**The dial.** Erasable is the last dial of every Medium choice (Ink, Wash, Opaque, Charcoal, Graphite), with its lock
and die in Advanced; Simple shows nothing new. It reads like the other dials: `off`, `0.40`, `1.00`.

**Where it starts** (a style's roll, a medium chosen in Brush Lab, every Basic tool; **superseded 2026-10-22**: every
medium starts at 1 and no roll changes it, "Every brush erases unless the owner says" at the end):

| Medium | Erasable | Like |
|---|---|---|
| Ink (also with Wet: ink wash, Sumi) | off | dried ink |
| Wash | 0.40 | watercolour: an eraser lifts a little off the top |
| Opaque | 0.30; **Oils off** (the one style that differs) | gouache lifts a little; dried oil doesn't |
| Charcoal (Charcoal, Pastels) | 1.00 (**changed 2026-10-20**, was 0.90: a faint ghost stayed) | charcoal: erases clean, like graphite |
| Graphite | 1.00 | pencil: erases clean |

It is never rolled (Randomize, the dice and variations leave it at the medium's value); the Advanced die on the dial
itself rolls it like any dial. **Changed 2026-10-18** (the owner: "weighted variables, less likely to be unerasable"):
a whole roll (Randomize, a style picked, Anything goes, Brush Studio's Random tab) weighs it from the seed: a medium
permanent for real (ink, Oils' paint) stays so one roll in six, else erases clean; a medium that erases in part keeps
its value; a locked Erasable or Medium is kept. No draw from Random, so every other dial rolls as before
(`ModularStyle.rolledErasable`, parts.js `rolledErase`; 2,000 app rolls with Erasable at 1 hash as before). A part's
die and a medium chosen by hand still start at the real value.

**Words** (`docs/TEXT-STYLE.md`): only when the dial isn't the medium's own value. "This brush" gains one short sentence
after the smudge: `Erasable.` / `Permanent.` / `Part erasable.`; the caption one tag: `erasable`, `permanent`,
`part erasable` ("Fineliner, erasable"; "Pencil, 4B, smear" / "permanent, laid"). A variation never changes it, so no
"more erasable".

**The eraser** (the tool, the pen's eraser end, a finger) keeps its three sizes, its pressure and speed, and erases both
halves of the pair as today. What changes is what it leaves:

- Each pixel it passes keeps its permanent part: pencil under or over ink goes, the ink stays as it was; a wash at 0.40
  keeps 60% of its darkness; gouache at 0.30 thins to 70% coverage (the layers below show through a little).
- What is left is permanent: a second pass takes nothing more, however hard the pen presses (no hard rub: Elling's
  answer).
- On a page with no permanent paint (every drawing made before), it erases everything, as today.

**Smudge.** Lift, Drag and Smear pick up and move only the erasable part: a Stump softens the pencil shading of an inked
drawing and leaves the ink lines crisp; a wet Lift takes back 40% of a wash and never touches permanent ink.

**Tools page.** Every Basic tool carries its medium's value (its code gains two digits where that isn't 1). One new
preset under Pencil, beside the five: **Sketch pencil** (Graphite H, smooth paper, Press dark, card line "erases
clean"), the pencil to draw under ink. No new tool or button.

Pictures (the web lab's engine in Node, 16 greys; not committed), `scratchpad/erase-out/`: `1-pencil-sketch.png` (a
Sketch pencil drawing), `2-inked.png` (Steady line over it), `3-pencil-erased.png` (the whole page erased: only the ink,
pixel for pixel the ink drawn alone), `4-wash-erased.png` (right half erased: a Soft wash keeps 63%, pencil hatching
goes, an ink line stays; bottom, a 10 px line hard-rubbed once and twice: 89%, 81%, the rub since dropped).

## The data model

The page is pairs of layers (wash `2k` under ink `2k + 1`); each layer is a tone and a coverage byte a pixel
(`ToneDocument.Layer.tones`, `alpha`); ink, charcoal and graphite paint the ink layer, wash and opaque the wash layer
(engine decision 6). So **pencil and ink share a layer**: the killer workflow happens inside one layer.

| | A. Erasable share (recommended) | B. Two planes a layer | C. The layer pair, or a sketch layer |
|---|---|---|---|
| What | per layer, `share` byte a pixel: the part of its coverage the eraser takes (255 all, 0 none); no plane = all 255 | each layer split into a permanent and an erasable tone + coverage, composited | erasable media paint another layer (the wash half, or a third layer per pair) |
| Memory | +1 B/px, only on layers with paint that isn't wholly erasable (4.9 MB a layer on the Manta) | +2 B/px on every layer (9.8 MB each) | none, or a third of the layers |
| Exact | for all transparent paint: the whole ink layer, washes, pencil, charcoal; approximate under opaque paint (below) | yes | yes, for 0 or 1 only |
| Partly erasable | yes | yes | no |
| Render | unchanged: the screen composites the same layers | twice the layers through the native compositor | unchanged |
| Order | kept | opaque paint drawn after pencil would show the pencil on top (the erasable plane is always above) | pencil would sit under the wash, or a third layer per pair halves the pairs (8 layers: 2 pairs) |
| Against | one byte more in every write, capture and file | heavy on the Manta's memory and render | hidden state; "few, clear tools" |

**Decision: A.** If the opaque limit matters in use, the exact variant is a kept pixel instead of a share (tone and
coverage, 2 B/px): the same write rule, twice the memory. The web lab draws that exact rule (one page, no coverage);
the two agree on every transparent mark.

### How A works

- **The plane.** `ToneDocument.Layer.share`, a full-page `byte[]` like `tones` and `alpha`, null until the first paint
  on that layer that isn't wholly erasable (then filled with 255), copied on write with them (`Layer.copy`,
  `writable`), carried by `restack`, `expand`, `InkWashLayers.resized`. A pixel with no coverage has share 255.
- **What a pixel keeps.** Its kept coverage is `aK = a × (1 − share/255)` at the same raw tone.
- **The write rule** (in `ToneDocument.tones`, the one block write behind `setTones`, `coverTones` and `smearTones`):
  the gesture carries the stroke's Erasable `e` (`begin(e)`, default 1); the compose passes, beside its tones, the
  stroke's own coverage `own` at each pixel (it has it: line coverage × `inkK`, the glaze, cover's coverage). The new
  kept coverage is the stroke's permanent part over what the pixel kept when the gesture began (from the capture, so
  repeated dabs never drift): `aK' = (1 − e)·own + aK₀·(1 − (1 − e)·own)`, and `share' = 255 × (1 − aK'/a')`.
  - `e = 1` and no plane: nothing new (today's path, bit for bit). `e = 1` with a plane: `aK' = aK₀`.
  - Erasable over permanent: the permanent part is untouched; erased, the pixel is the permanent mark again.
  - Permanent over erasable: the stroke's own mark becomes the kept part; erased, the pixel is the ink alone, the pencil
    under it gone (checked in the lab: 0 pixels differ from the ink drawn alone).
  - Partly erasable: both, in proportion.
- **The opaque limit.** A share is a part of the coverage: where opaque paint already covers a pixel fully, paint laid
  over it on the same layer erases only as far as the opaque paint under it does (erasable gouache over permanent oils
  stays). Only the wash layer can hold opaque paint, and erasing over gouache is rare; ink over gouache is on the ink
  layer and exact.
- **Wet paint after lift.** A Slow wash's ticks, gravity's runs and drips, a Float print and a quick stroke's lift write
  as continuations of the stroke's gesture with the stroke's `e`. They pass no `own`: it is the change from the
  gesture's found pixel, `own = (a' − a₀)/(1 − a₀)` (exact for transparent paint; where `a₀` is full, the share is kept).
  Fast-wet re-settles are composes and pass `own`. A `QuickStroke`'s `StrokeTones` keeps `own` beside its tones.
- **Smudge** (`CarryPlane`, `Carry`): the plane holds only the loose part, `(a × share, p × share)`; the permanent part
  stays where it is and the compose puts the loose paint, smudged, back over it; the write's share is the loose part's
  over the total (the smudge is as erasable as what it moved), the brush's own paint with its `e`.
- **The eraser** (`EraseBothStroke`, `ToneDocument.eraseTones`, `eraseTone`): coverage × (1 − share), share → 0 (no
  coverage left: paper, 255); the ink half erases as the pen moves, the wash half at lift. Its preview, `erasedTones`,
  shows the pair at its kept coverage (`compositeToneKept`) instead of without it. Masking fluid shields as today.
- **Undo and redo.** A tile capture holds the share too (3 bytes a pixel on a layer with a plane, 2 elsewhere); restore
  writes it back; a tile captured before its layer had a plane restores to 255. Every stroke and every erase stays one
  step. `clear` and `clearAllLayers` drop the planes.
- **MonoPaint's own tools** (hidden in Inkwash) write as `e = 1`.

## Memory and speed on the Manta

1920 × 2560 = 4.9 M pixels; the page is 9.8 MB a layer (2 B/px), 79 MB at eight layers; Undo keeps 32 MB.

- **Memory:** +4.9 MB for each layer that holds paint that isn't wholly erasable: an ink-and-wash drawing on one pair
  +9.8 MB, four pairs at most +39 MB. A snapshot's copy on write clones it with the layer (+4.9 MB beside today's 9.8).
  Undo's captures grow by half on those layers, so its 32 MB holds about two thirds as many pixels there.
- **Speed:** no second compose. The write gains one byte read, about ten multiplies and a division a written pixel,
  only where the layer has a plane; the compose fills one more value it already has. Expect under a tenth of a stroke's
  time; the lab, which composes twice instead, measured +4 to 26% a sample (this Mac, 1400 × 900 wave: Fineliner 8 px
  24 → 25 µs, Watercolor 120 px 183 → 230 µs). Rendering is unchanged (the planes are never drawn). The eraser reads
  one byte more a pixel: at Large (220 px), 0.14 ms a sample today, about 0.2 ms.
- **The panel:** the eraser already shows by gray updates (`StrokePanel` leaves it out of the fast black-and-white
  path), and a partial erase leaves greys, so it stays there; nothing new on the panel.
- **Files:** a share plane deflates to a few dozen KB (a dense pencil-and-ink test page: 4.9 MB → 47 KB, about a
  fifth of its layer's tones); `DrawingBook`'s 16 MB limit is unaffected in practice.

## File format (`.mpaint`)

- **TSM5**, written only when some layer has a plane with a covered pixel below 255; otherwise TSM3 or TSM4 exactly as
  today, so old drawings and drawings with only erasable paint are byte-identical and open everywhere. TSM5 is TSM4's
  body (layers, a mask flag and the mask) followed by tagged sections, each `tag` (int), `length` (int), bytes; unknown
  tags are skipped, so the next addition needs no new magic. The first, `ERAS`: per layer a flag byte, then the plane
  (`w × h` bytes) if set. Inside the checksum, as everything else.
- **Old drawings open as fully erasable**: no plane, the eraser takes everything, exactly as today.
- **Older versions opening a TSM5 drawing refuse it** ("Unknown painting format") and leave it untouched: `DocumentCodec`
  rejects an unknown magic and extra data, `BookCodec` an extra ZIP entry, so no older reader can be made to skip the
  planes. Decided, not asked: Inkwash is installed only by Elling, the drawing is never damaged, and the PNG copy each
  drawing writes on leaving still opens anywhere. MonoPaint, reading the same format, refuses it the same way.
- `BookCodec.read`'s single-page fast path, `readFirstPage` (previews) and `validate` (pages not shown) accept TSM5;
  recovery files go through `BookCodec` and carry the planes.

## Share codes

- `PartDial.ERASABLE` ("erase", "Erasable", 0–1), appended last to every Medium choice's list (Charcoal's after Dust),
  and in `ModularCode.ADDED`: written only when not 1. **Its default is 1 for every medium**, so a code that doesn't
  say reads 1, which is today's behaviour; every existing code, from the app or the web lab, decodes and re-encodes
  byte for byte, and a brush at 1 encodes as before.
- The real-media values are applied where a brush is made, not when one is read: `ModularStyle`'s roll sets it after
  rolling, without drawing from `Random`, so every other part of every style rolls exactly as before (the lab checks
  300 seeded rolls hash as before with it set back to 1); a medium chosen in Brush Lab; `ToolPresets` (re-encoded);
  old I/P conversions (`LegacyBrushes`) stay 1.
- Examples (the lab): Steady line permanent
  `steady-line.M00il00000000at_000zz000000000000000_0_00r_1jz_84w0000000000000000nz0.51j8ud` (`00` added to the medium);
  a Charcoal at 0.90 writes Dust then Erasable, `…_3nbcw00we_…`; Soft wash at 0.40 `…_1wva200000000ee_…`.
- The web lab had no Dust; it now has it as a hidden dial, so its codes line up with the app's.

## Checks

Desktop, a new `ErasableChecks` in `scripts/test_inkwash.sh` (and `scripts/test_document.sh` for the codec):

1. **The workflow:** a Sketch pencil drawing, Steady line over it, every pixel erased: the page equals the ink drawn
   alone on a blank page, byte for byte on the ink layer.
2. **Old pages:** a page with no planes erases to paper as today (`EraseBothChecks` golden unchanged).
3. **Part erasable:** a wash at 0.40 keeps 60% of its darkness (±2%); gouache at 0.30 keeps 70% of its coverage.
4. **Order:** erasable over permanent and permanent over erasable, on the ink layer, both orders.
5. **Pressed hard:** the eraser at full pressure, twice, leaves permanent ink as it was (no hard rub).
6. **Smudge:** Smear, Drag and Lift over permanent ink change nothing; over pencil they move it (`CarrySmudgeChecks`
   acceptance unchanged on pages without planes).
7. **Wet paint:** a Slow wash at 0.40 erased after its settle keeps 60%; gravity's drips and a Float print take their
   brush's value; a quick and a live stroke end with the same planes (`QuickStrokeChecks`).
8. **Undo/redo:** a stroke and an erase each come back byte for byte, planes included; a tile captured
   before its layer had a plane.
9. **Codes:** every golden and preset code re-encodes byte for byte; the dial written only when not 1, after Dust;
   2,000 rolls with Erasable set to 1 hash as before; rolls start at the real values.
10. **File:** TSM3/TSM4 written when no layer holds a plane (old files byte-identical after open and save); a TSM5
    round trip; an unknown section skipped; a truncated or corrupt one refused; an older reader refuses TSM5 cleanly.
11. **Nothing else moves:** with every brush at 1, the bench's 126 page hashes (`ModularPerfBench hashes`) and
    `LegacyConversionChecks` are unchanged; `CoresChecks` (several cores = one) with planes.
12. **Timing:** `ModularBenchChecks` gains Fineliner 8 px, Watercolor 120 px and Stump 90 px over a page with
    planes, budgeted at twice this Mac's time.

On the Manta (bench build): the same strokes over a marked page with and without planes (`-e marks true`), the eraser
at its three sizes. Pass: pen-to-screen p95 within 10% of today. For Elling: Sketch pencil, Steady line over it, erase;
a wash erased; a Stump over an inked drawing; Undo after an erase; an old drawing still erases everything.

## Order of work, if approved

1. `ToneDocument` (the plane, `begin(e)`, the write rule, `eraseTones`, captures, `compositeToneKept`) and
   `DocumentCodec` (TSM5), with checks 2, 8, 10. Upstream file: log it in `docs/INTEGRATION.md` 2(b).
2. The dial: `PartDial`, `PartChoice`, `ModularCode.ADDED`, real values in `ModularStyle`, `BrushLab`, `ToolPresets`
   (and Sketch pencil), `ModularWords`; checks 9.
3. The engine: `ModularCompose` (`own`), `ModularStroke`, `QuickStroke`/`StrokeTones`, `DryingWater`, `GravityFlow`,
   `FloatWater`, `Carry`/`CarryPlane`, `EraseBothStroke`; checks 1, 3 to 7, 11, 12; the device bench.

## Answers (Elling, 2026-10-16)

1. **Should a hard rub lighten permanent ink?** **No**: the eraser never touches permanent paint. The rub is gone from
   the app's plan and from the web lab (its Eraser and `eraseChecks`).
2. **Brushes already in slots and the library** decode as Erasable 1. **Once, at the update, a slot holding a Basic tool
   exactly as it came takes the tool's new code** (the Fineliner in slot 1 becomes permanent); brushes Elling made keep
   erasing fully until changed in Brush Lab.
3. **Drag and Smear leave permanent paint too, not only Lift?** **Yes**: a finger moves pencil and charcoal, never
   dried ink; a Stump over an inked drawing is the point.

## Web prototype (as built, 2026-10-16)

`docs/brush-lab/prototypes/modular-lab.html`, sources in `modular-lab-src/` (built and patched as its NOTES say):

- `parts.js`: the Erasable dial (and the app's Dust, hidden) last in each Medium list; `ADDED` (written only when off
  its default; never rolled, never nudged by a variation); `REAL_ERASE` and `realErase` (Oils 0); the words
  (`eraseLabel`). `engine.js`: `blankPage` drops the kept tones.
- `stroke.js`: `surf.kept`, the tone the eraser leaves, made by the first stroke that isn't wholly erasable; the compose
  lays the stroke over the kept tones too, by `1 − e` (`toneOf` twice); Water's settle and plumes, and Float's print,
  by their brush's `e`; Carry moves only the loose part (the page's tone over the kept tone); `Eraser` (the app's
  sizes 24, 120, 220 px by the test size, its pressure curve, each pixel once a stroke; since Elling's answer, no hard rub).
- `ui.js`: an **Eraser** button in the test area under Clear (ink-filled while on; tapping a slot or changing the brush
  puts it down; "Erased."), and three library brushes added once at the front: **Sketch pencil**, **Steady line**,
  **Soft wash**. Sumi is permanent.
- `checks.js`: `eraseChecks` in RUN CHECKS: the workflow (3,664 px of pencil gone, 0 px differ from the ink alone), an
  old code erases to paper, a wash keeps 60.0%, erasable gouache over ink gives the ink back, a finger smears pencil
  and leaves ink, pressed hard twice the eraser leaves ink (was: the rub), codes byte for byte, 300 seeded rolls hash as
  before (`3cfc3ac9`). After the rub went: the self-test passes, click-through 54 of 54, 168 layout states clean.
- With every brush at 1, all 560 Tip × Medium × Smudge × Drying combinations draw the same page, hash for hash, as the
  lab before this change. Click-through 54 of 54 (seven new: pencil, ink, Eraser, erase, slot puts it down, a wash in
  part), the self-test passes, 168 layout states without a problem, no horizontal scroll at 390 px.

## As built in the app (2026-10-16, branch `inkwash-erasable`)

Built as above, decision A, with these differences and findings:

- **The share is a share of the pixel's paint, worked out in darkness over white**, not in coverage: the kept darkness is
  `own·(1 − e)` over what the pixel kept as found, and the share what the pixel's darkness holds beyond it; the eraser
  leaves the raw tone at coverage × (1 − share). On the ink layer (raw tone 0) darkness and coverage are the same and
  the rule is exactly the coverage rule above; a Fast-wet window that lightens a pixel within its gesture leaves a raw
  tone above 0 and the coverage rule would have kept too little (a Soft wash kept 55% instead of 60%).
- **`own` is the stroke's own darkness over white**, worked out by `ModularCompose` beside each tone with the same
  arithmetic as the tone, the same paint over white (255), step for step: so ink alone and ink over pencil agree to the
  grey, and the workflow is exact (every byte of the ink layer, tones, coverages and shares, as the ink drawn alone). Opaque paint passes its darkness too;
  charcoal's own uses the line's ink as found under it (its contact rule reads the tone under it).
- **Writes without `own`** (wet paint after lift, gravity, dust): a darker pixel takes the darkening since found as its
  own; a lighter one (paint run or lifted away) loses its permanent share of what went. A Slow wash settles in steps,
  each keeping 60% of its own darkening, so a settled Slow wash keeps 66% and gravity's drips 62% (the web lab's Water
  compounds the same way); a Fast-wet Soft wash keeps 60.1%, gouache 69.8% of its coverage.
- **Float:** a show lays no paint of its own (the layer keeps what it kept as found); only the print lays its film as
  its own, at the least Erasable of the strokes floating (`FloatWater.floats`).
- **Smudge:** `CarryPlane` splits each pixel at fill into its kept part (fixed) and the loose part it trades; the stroke
  composes over the two together, so the write's kept part is as found and Smear, Drag and Lift over permanent ink
  change no byte; over pencil and ink the ink's kept coverage is the same at every pixel after.
- **No hard rub** (Elling's answer). `WashCoverage` is unchanged.
- **Undo:** shares are captured beside the tile captures (`beforeShare`, and by tile key for a lock-free lookup);
  `clear` sets them to 255 (so Undo brings them back) rather than dropping the share; `clearAllLayers` drops them.
- **TSM5** ends its sections with a zero tag. `BookCodec`'s single-page paths open TSM4 too (they missed it).
- **Slots:** the first-launch brushes (`ToolSlots.FIRST`) start at real values; the once-at-update rule (flag
  `tool_slots_erasable`) also covers them, so the Fineliner in slot 1 becomes permanent; it runs before the old-engine
  conversion, which keeps converted built-ins at 1. The library and the Recent list are left as they are (a Basic tool
  saved before shows under Saved beside its new self).
- **Words:** brushes from before (at 1) say "Erasable." where their medium isn't (an old ink, wash, charcoal); old I/P
  conversions too ("Ink line, chisel tip. … Erasable. Pressure widens.").
- **Sketch pencil** is the web lab's, byte for byte: `sketch-pencil.M40i000000000at_4at00_0_000_1jz_25ess00000000000000nz0.inw`.

Checks: `ErasableChecks` (test_inkwash.sh) and `ErasableFileChecks` plus `OlderReaderCheck` (test_document.sh, the older
reader compiled from git at `8d52808`); 93 PASS in all. With Erasable the bench's 210 page hashes are unchanged (rolls
now start at real values; tones and coverages are the same, only shares are new). 2,000 rolls with Erasable set to 1
hash as before (`b96c79b2`).

**Speed.** Desktop (this Mac, best of five, a sample's mean, today = the brush at 1 over erasable marks, Erasable = its
real value over permanent marks): Steady line 8 px −1%, 120 px +12%; Soft wash 120 px +21%; Opaque round 60 px +3%;
HB pencil 8 px −3%; Stump 90 px −43% (over permanent marks nothing moves). The block write costs 8.8 ns a pixel without
a share, 11.9 with one at Erasable 1 and 13.5 at .4. The Manta (bench build, paced BATCH input, over marks, pen to
screen p50/p95 ms, two runs each, identical): Steady line 8 px 0/1 → 0/1, 90 px 2/4 → 3/4, 120 px 1/3 → 1/3; Soft wash
8 px 0/2 → 0/2, 90 px 2/10 → 3/12, 120 px 1/9–10 → 2/13; Stump 90 px 8/12 → 1/4 and 120 px 15/23 → 2/7 (over
permanent marks). So the spec's "within 10%" holds for lines and pencils and not for a big Fast-wet wash (+20–40% at
p95, still 13 ms, far inside the 60 ms a live stroke may take); that run predates the last two speed-ups (a lazy found
darkness, a multiply for the division: −10% of the write's cost on the desktop). A second, later Manta run was noisy
(the tablet in use) and isn't reported. The eraser on the Manta, ms a sample (mean/p95) and lift: Small 0.03/0.10 →
0.02/0.05, lift 22 → 28; Medium 0.08/0.56 → 0.08/0.56, lift 38 → 42; Large 0.14/1.28 → 0.14/1.38, lift 56 → 63.
**Memory:** a pair's two shares take 9.4 MB of heap on the Manta (4.7 MB a layer).

## Everything erases everything (the owner, 2026-10-22, branch `erase-template`)

The owner's note: "Pen isn't erased by eraser (everything)"; and on why: "i was toying with a restriction that everything behave like physical media but i realize that isnt what i want". So the Eraser on **Everything** (the eraser, the pen's eraser end,
a finger set to Eraser) now takes every mark whole, permanent ink and paint at any Erasable included
(`EraseBothStroke.takes`: every kind, `ToneDocument.eraseKinds(KIND_ALL)`); its preview agrees, and it still lifts
masking fluid first, scrapes Tone's dots and rubs a cover's design as before. What still respects Erasable: a chosen kind
(Pen, Paint, Dry Media) on a pixel where another kind laid permanent paint (pencil under permanent ink: Dry Media takes
the pencil and leaves the ink, the sketch-then-ink workflow), Smudge and Carry (they move only the erasable part), a
Lift, and MonoPaint's own erasers (not on Inkwash's drawing screen). Unchanged: masking fluid still shields what is under
it, its wax is never lifted, and No Erasing still turns the eraser off. The Erasable dial stays, with its words. Checks:
`ErasableChecks` (the rule itself through `eraseAll`, the workflow through Dry Media, Everything to paper),
`EraseKindChecks.everything`, `HaloChecks` and `BlendChecks` (the rule).

## Every brush erases unless the owner says (the owner, 2026-10-22, branch `erasable-default`)

The owner: "i regret some of the choices that made some things less erasable... although i do like the constraint for
notebooks as a setting, per-tool things should be erasable unless the user specifies. maybe the randomizer leaves that
var alone unless the user moves it"; and "yes.. want the tools to be realistic and feel real" (the look and feel, not
restrictions: "i was toying with a restriction that everything behave like physical media but i realize that isnt what
i want"). So the table under "Where it starts" no longer holds:

- **Every medium starts at 1** (`PartChoice.erasable`, `ModularStyle.erasable`, Oils' paint too; parts.js
  `REAL_ERASE`): a style's roll, a medium chosen, a new brush.
- **No roll changes it** (`ModularStyle.keepErasable`, parts.js `keepErase`; replaces the 2026-10-18 weighted roll,
  `rolledErasable`): Randomize and a style picked keep the brush's value, the slot's dice (`ToolSlots.rolled`) too, the
  Medium's die, and Brush Studio's Random tab; variations never touched it; a medium chosen by hand keeps it
  (`ModularBrush.switchTo`), 1 to or from Resist. Surprise Me and a first roll have no brush before: 1. The dial has no
  die and no lock in Brush Lab (or the web lab); only the hand moves it. Rolls with it at 1 still hash as before (2,000
  app rolls, 300 lab rolls).
- **Every Basic tool at 1**: 86 share codes lost their Erasable digits (the codes of every ink, wash and opaque tool,
  seeds unchanged), and `ToolSlots.FIRST`'s six. The web lab's presets follow (`make` starts at 1).
- **Once, the stored brushes** (`ToolSlots.erasable`, flag `tool_slots_erasable_1`, after the six-part conversion): every
  share code in the slots, the app's own toolbar while a notebook's is open, the smudge and stamp tools, Recent and the
  library whose medium is below 1 goes to 1, names, places and sizes kept. The low values came from the defaults and
  rolls, not a choice. Only the stored copies: a code pasted later reads as written. A notebook's own toolbar (Surprise
  Me, Library) inside its file isn't rewritten.
- **Left as it was:** Resist's dial is wax (off) or masking fluid (1), not erasability: Wax resist, Masking fluid, Resist
  rolls (seven in ten fluid) and its die are unchanged, and the migration leaves wax alone. Screentone and Crayon have no
  Erasable. Vanishing ink and Lemon juice were permanent only by default; their point is fading and darkening, so they go
  to 1. No Erasing (the notebook rule) is unchanged, as are the eraser's kinds (Dry Media still leaves permanent ink) and
  the words (`Permanent.`, `Part erasable.` when off 1).
- Checks: `ErasableChecks` (rolls start at 1 and no roll, die or variation changes it; Resist's die still flips; every
  Basic tool at 1; the migration over slots, app toolbar, stamp, Recent and library, once, wax kept; its rule checks
  now set the real-media values by hand, `ErasableChecks.real`), `ToolPresetsChecks`, `ToolSlotsChecks`,
  `ModularStyleChecks`, `WetSmudgeChecks` (a wash at .4 by hand), the words' tables (`BlendChecks`, `ScreenChecks`,
  `halo/words.tsv`); the web lab's `eraseChecks` 8.
