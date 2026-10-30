# Smudge (the sixth part): design

Status: approved by Elling with the recommendations below (own layer only, a Stump preset, Drag on wet or dry paint);
built on branch `inkwash-smudge`, 2026-10-16: see "As built" at the end. Builds on `2026-10-14-modular-engine-design.md` (decision 5, "Stage 3 notes", Speed on the
Manta, Gravity) and the web lab's Carry (`modular-lab-src/stroke.js` 595–626, `parts.js` 239–247).

Smudge is the brush part internally called `CARRY` (`BrushPart.CARRY`, label "Smudge"). Its choices, in code order:
**Off**, **Lift**, **Drag**, **Smear** (`PartChoice.OFF, LIFT, DRAG, SMUDGE`). Its dials: **Pick up**, **Lay down**,
**Own paint** (Lift has no Own paint). Today `ModularFeatures.CARRY = false`: codes keep the choice, the app draws Off,
and the lab only shows a deferred choice while it is the current one, which is why Brush Lab looked stuck on Off.

## Recommendation in one paragraph

Port the web lab's Carry (a dab-sized reservoir that travels with the brush, picking up the page and laying it back),
with four fixes made in the web lab first so both still draw the same. A stroke smudges **only its own layer**, read
**as it was at pen-down**: the ink half for ink, charcoal and graphite brushes, the paint half for wash and cover, so
pen lines stay crisp over their own paint, as they do today. Pick up and smear in premultiplied pixels (coverage
and darkness), so a smear on the ink layer never paints a light halo over the wash. The smear is final as laid:
nothing settles or runs afterwards, and one Undo takes the stroke back. No new dials; every code decodes as before.

## What each choice does to the page

Grey on white. "Beneath" means marks already on the same layer. Pressure scales both Pick up and Lay down
(`.45 + .55 × pressure`, as the lab).

| Choice | In plain words | Own paint | Where it shows best |
|---|---|---|---|
| **Off** | Lays its own paint only. Today's brushes. | all | everything |
| **Lift** | Takes paint off as it goes, like a damp brush or a tissue on watercolor: where it crosses dark marks the track comes out lighter. Opaque paint (gouache) picks up some of the dark it covers and greys out over it, then cleans up again a brush-width or two later. | all | Gouache over ink-wash darks; a wash brush to pull light back into a dark wash |
| **Drag** | Pushes what's beneath along the stroke and mixes it with its own paint: wet into wet, oils dragged through oils. Dark lines it crosses bend out into the stroke's direction as streaks. | most (.85) | Oils, Hake, a wash over a wash |
| **Smear** | Mostly moves what's there, like a finger or a paper stump: lines it crosses soften and spread into a grey haze along the stroke, with a light touch of its own paint. At Own paint 0 it is a pure finger and draws nothing on blank paper. | little (.3) | Pencil, charcoal, pastels |

The dials, after fix F2: **Pick up** is how much the brush takes and how far it carries it (higher = longer, darker
streaks: the lab's "clean ↔ smeary"); **Lay down** is how much it lets go each time it touches (higher = stronger
right behind the line, a shorter tail); **Own paint** scales the brush's own paint, opaque cover included.

Illustrations (made with the web lab's engine in Node; not committed): `scratchpad/smudge-out/`
`1-choices-today-vs-proposed.png` (Off, Lift, Drag, Smear, finger, for Gouache, Charcoal, Watercolor; the lab today
above, with F1 and F2 below), `2-finger-over-pencil.png`, `3-own-layer-vs-pair.png`.

### Four fixes to the web lab's Carry (made in the lab first, then ported)

The lab's Carry is the reference, but drawing it out shows four flaws. Fix them in `modular-lab-src`, rebuild the
lab, let Elling try them in the web lab, then port. As with the big Drying body fix (`a369244`), the lab and the app
must keep drawing the same.

- **F1. Own paint scales opaque paint too.** The lab multiplies only ink and wash strength by Own paint, so a Gouache
  or Oils Drag or Smear covers its own smear at full strength: the smear shows only at the soft edge. Cover becomes
  `cover × own paint`.
- **F2. More Pick up means a longer smear.** In the lab the reservoir follows the page at rate `pick` per dab, so how
  much it drags is set by Lay down alone and Pick up only shortens the tail: at the default .7 a 26 px finger drags a
  black line about 5 px. The reservoir follows at `k = .03 + .6 × (1 − pick)` instead (never 0, which would stamp the first
  thing it touched along the whole stroke). The lab's macro (clean ↔ smeary on Pick up) then runs the right way.
- **F3. Lift removes.** In the lab Lift is Drag with gentler defaults. Make it the one choice that takes paint
  away: the brush keeps `1 − lay down` of what it picks up instead of mixing it all back, so the track lightens;
  with opaque paint what it holds tints its paint (paint grey moves toward the reservoir by Pick up), which is
  MonoPaint's opaque wet pen (`8743286`: the brush "takes on a little of the paint it covers", `MIX = .25`).
  Constants tuned in the lab against the acceptance numbers in Checks.
- **F4. The smudge is as wide as the mark.** The lab caps the reservoir's radius at 90 px, so a 360 px Hake smears
  only its middle half. Raise the cap to 180 px (half the largest brush); the reservoir stays at most 61 × 61 cells
  (`stride = r / 30`).

Kept from the lab: the reservoir starts each stroke empty (its first touch reads the page, so a stroke never smears
at its very start); a round footprint for every tip (the hake smears in a circle as wide as its head; known limit);
dab spacing `max(1.5, r / 4)`; the order pick up, lay down, then own paint over the result.

Not taken from MonoPaint: **Hold ink between strokes**. Every stroke starts with a clean brush. A brush that remembers
the last stroke would draw differently depending on what you did before (`PHILOSOPHY.md` #1).

## The cross-layer decision

The page is pairs of layers (pair *k* = wash `2k` under ink `2k + 1`, up to four pairs). A stroke paints one layer: ink,
charcoal and graphite brushes the selected pair's ink layer, wash and cover its wash layer. What should a smudge pick
up, and where does it write?

| | Picks up from | Writes | For | Against |
|---|---|---|---|---|
| **A. Own layer** (recommended) | the stroke's own layer, as found at pen-down | that layer | One gesture, one Undo, no new layer rules. Same as MonoPaint's wet pen (`WetWatercolor` reads `document.tone`, the active layer), Procreate, Fresco, MyPaint and Krita's default. Keeps today's rule that ink sits crisp over its own paint (decision 6: cover can't hide the ink above it either). Pencil, charcoal and pastel smears, the styles that roll Smear, act on the ink layer where their marks are. | A paint brush's smear can't drag pen lines (3-own-layer-vs-pair.png, left). A smear on an upper pair finds nothing from the pairs below. |
| **B. The pair** | both halves of the selected pair, each with the same footprint | both: the stroke's layer live, the other half at lift as a linked Undo step (as the eraser does: `EraseBothStroke`, `finishWithPrevious`) | Smudges everything in the pair, as on one sheet of paper (right panel). | The other half changes only at lift, so a live stroke shows half its smear until then, or `ToneDocument` needs a two-layer gesture (an upstream file, the undo core). Ink stops being "always crisp". About twice the work. |
| **C. Everything visible** | the composite of all shown layers | the stroke's layer | Smears what you see. | Copies other pairs' marks into this layer: hide or move a pair and a ghost copy stays behind. Ruled out. |

**Decision: A.** B can follow without code changes if Elling wants paint smears to drag pen lines (open question 1):
the reservoir and the found-pixel plane are per layer by construction, so B is "run A on both halves, deposit the
other half at lift".

### How A works in the app

- **Found pixels, at pen-down.** The stroke keeps a `CarryPlane`: per page pixel, coverage `a` and darkness
  `p = a × (1 − raw / 255)` of its own layer, in 64 px tiles (`FloatTiles`), each tile filled the first time the
  footprint reaches it by a new block read, `ToneDocument.strokeBasePixels` (raw tone and coverage beside
  `strokeBaseTones`, from the gesture's captures: the page as the stroke found it, even after the stroke has
  written there). The reservoir picks up from and lays down into this plane, so a later
  dab drags what an earlier dab of the same stroke smeared (as the lab's `base`), and never what the stroke itself
  painted.
- **Premultiplied.** The reservoir cells hold `(a, p)` too, and pick-up and lay-down mix both. On a one-pair page
  over white paper this equals the lab's tone mixing. In the app it matters: an ink pixel is black at coverage =
  darkness, and lightening it by mixing tones alone would keep full coverage and paint a light grey patch over the
  wash beneath. Mixing coverage thins it instead.
- **Compose** (`ModularCompose`): `under` comes from the plane where the stroke has smudged (the plane's shown tone
  `255 × (1 − p)`), else from `strokeBaseTones` as today. Own paint composes over it by today's rules, at Own paint.
  The carry's box is composed even where there is no line or body (a pure finger), and the compose shortcut counts
  a pixel the carry changed as changed.
- **Write.** A new block write, `ToneDocument.smearTones(x, y, w, h, grays, coverages)`: today's `tones()` with the
  stroke's coverage floor (the smeared `a`) in place of the gesture's found coverage, so a smear can thin a layer
  as well as darken it, and with cover's coverages beside, so a smudging stroke writes everything through it. Same
  Undo capture, dirty area and masking-fluid shield. These two methods are the only upstream-file change
  (`ToneDocument`): log them in `docs/INTEGRATION.md` section 2(b).
- **Drying (Instant, Quick, Slow).** The smudge is laid as the pen moves and is final as laid in all three; it is
  never part of a window's re-settle. A Quick window's restore (`restoreSpan` where the tail was taken back) restores
  to the smeared `under`. A Slow stroke's own wash settles after lift over the smeared page, as it settles over any
  page today: `DryingWater` writes relative to the tone at lift.
- **Settling and gravity before the stroke.** Any new stroke first lays whatever is still settling or running
  (`settleNow`, `finishNow`; decision 10), so a smudge always reads a laid page. **Drag smears what is there, wet or
  dry** (open question 3). Nothing the smudge moved runs on a tilted page afterwards: `GravityFlow` moves only the
  stroke's own wash share; page tilt has no effect on the smudge itself.
- **Undo.** One gesture on one layer: one step (a Slow settle stays a continuation of it, as now).
- **Eraser.** Unchanged: it erases both halves of the pair whatever smudged them.
- **Masking fluid:** shields the write, as for every brush (its UI is hidden).
- **Rolls.** `ModularFeatures.CARRY = true`; styles roll the lab's weights (Pencil Smear 1 in 7, Charcoal 1 in 4,
  Pastels 1 in 3, Gouache Lift 1 in 2, Oils Drag 7 in 10, Anything goes 3 in 9). Carry is rolled **after** every
  other part, as `rollGravity` is, so for the same `Random` every other part rolls exactly as before.
  Variations may swap to or from a Smudge choice (the lab's rule).
- **Words.** "This brush" already has the phrases (`ModularWords.carryWords`); they show once `CARRY` is on. Lift's
  becomes "lifts paint off as it goes" (F3).

## Speed on the Manta

The carry is cheap; its compose and its write are the cost, and those already exist. Measured in the lab (Node, this
Mac, 1400 × 900 wave over a marked page, Smear, samples 2.6 px apart): **the carry pass alone** is 0.6 µs a sample at
6 px, 4 at 30, 7 at 60, 15 at 120, 25 at 360 (r = 90). At about 20× on the Manta that is up to 0.5 ms a sample at
360 px, or about 0.2 ms a pixel of pen travel; at F4's r = 180 about twice that. Under 120 px it is within noise of
today's stroke cost. Plan:

1. **Bounded per dab.** Reservoir at most 61 × 61 cells; a dab touches at most `π r²` pixels, one per `r / 4` px of
   travel, so the cost grows with the brush's radius and the pen's speed, not the page.
2. **Lattice.** A stroke on the half-resolution lattice (`ModularStroke.latticePx`, reach past 100 px) carries on
   the lattice too: the plane keeps the found pixels at full resolution and the smudge as a half-resolution change
   (`under = found + stretch(change)`), so untouched detail stays sharp. 4× fewer pixels; edges a pixel softer, as
   the lattice already makes them.
3. **Cores.** A dab's pixels touch only the reservoir cells under them, so its rows split into bands of whole cell
   rows across `Cores`; each cell is still updated in raster order, so several cores draw what one does
   (`CoresChecks`). Use it only for dabs over ~4,000 pixels.
4. **Quick strokes** (`QuickStroke`, render at lift). `QuickStroke.keepsUpTo` gains a carry term, measured with the
   device bench (`scripts/bench_device.sh -e sizes edge -e quick rule`), so a big smudge goes quick rather than lag.
   While the pen is down the preview sweeps the footprint as today; a brush with Own paint under .5 shows it as the
   light wash dither ("something happens here") rather than its flat paint. (As built, one with no paint of its own
   sweeps its smudge's dab, as wide as its size dot: 2026-10-19, see the Stump spec's "Sizes".) **At lift the smudge must read the
   page as it was at pen-down**: the true stroke runs on the render thread, which may not read the page while the
   preview writes it. So the pen thread, before handing a batch to the render thread, fills the `CarryPlane` tiles
   the batch's footprint can reach (its box plus `r` plus one stride) with `strokeBasePixels`, which reads the
   gesture's captures and so the page as found, preview or not. The render thread reads only the plane. At lift the
   preview's gesture is cancelled, the true stroke's gesture begins, and it composes over the same found page. Live
   and quick strokes end on the same layers (Instant and Slow) and the same tones (Quick), as `QuickStrokeChecks`
   already requires.
5. **Pacing.** Nothing new: the carry is part of the stroke, so `PenBatch`, the present pacing and `SettlePacer` apply
   unchanged.
6. **Memory.** The plane is two floats a pixel only in tiles the smudge reached: a 180 px smear across the Manta's
   page touches about 200 tiles, ~6 MB, freed at lift.

Bench cases to add (`ModularPerfBench`, over a page pre-filled with marks; carry over blank paper flatters):
Pencil Smear 6 px, Charcoal Smear 30 and 120, the Stump preset 28 and 90, Gouache Lift 60 and 240, Oils Drag 60 and
240, Hake Drag 360. Pass: pen-to-screen p95 under 60 ms live, or quick, with the lift-to-final time reported.

## Codes and the lab's dials

- **Nothing in the code changes.** Carry is segment 3: its choice digit (0 off, 1 lift, 2 drag, 3 smudge), then
  Lift `pick, lay` and Drag and Smear `pick, lay, load`, two base-36 digits each over 0–1. Every M code, from the app
  or the web lab, decodes to the same values and re-encodes byte for byte. **No new dials.** If a later one is
  wanted (a smudge width, say), it is appended last in the choice's list and listed in `ModularCode.ADDED`, written
  only when off its default, as Tilt range and the gravity dials were.
- **What changes is drawing.** Codes with Lift, Drag or Smear drew as Off in the app; after this they smudge, as
  in the web lab (after F1 to F4). No app brush has one: rolls never picked them and old I/P conversions are Off, so
  only codes pasted from the web lab change, and they now match it. Off draws exactly as today.
- **Defaults** stay the lab's: Lift .2 / .3; Drag .6 / .7 / .85; Smear .7 / .55 / .3. Dial meanings shift with F2 and
  F3 (Pick up now lengthens the smear). Codes store dial values, so this changes how web-lab Carry codes draw, not
  how they read; F1 to F4 land in the lab first and the lab's NOTES say so.
- **Brush Lab:** the Smudge part offers Off, Lift, Drag, Smear; Advanced shows Pick up, Lay down, Own paint with lock
  and die; Simple shows its macro (Pick up, clean ↔ smeary). The lab's test page and samples draw the smudge over
  their grey band (the lab's `ui.js` 518–522 already paints one for Carry samples).
- **Tools page:** one preset, **Stump** under Charcoal: Charcoal medium, Smear, Own paint 0, Pick up .7, Lay down .6
  (open question 2). No new tool or button.

## Desktop checks

New `CarrySmudgeChecks` unless noted; all run in `scripts/test_inkwash.sh`.

1. **Codes:** carry choice × dial round trips; golden codes with Lift, Drag, Smear segments decode to the same values
   and re-encode byte for byte (`ModularCodeChecks`); a 2000-roll round trip with `CARRY` on.
2. **Golden images against the lab:** the lab's Node engine (after F1 to F4) renders six fixed brushes (Pencil Smear
   6 px, Charcoal Smear 30, Stump 28, Gouache Lift 60, Oils Drag 60, Watercolor Drag 120) on the golden wave over a
   fixed marks page (lines, a grey band, hatching), stored as CRC plus PGM. Java on a one-pair page, marks on the
   stroke's own layer, matches at 16 greys with at most 0.1% of pixels one grey off. A desktop script writes the pairs
   side by side as PNG for Elling.
3. **Acceptance numbers** (also the lab's tuning targets): Smear at its defaults drags a 6 px black line at least one
   brush width along the stroke; Stump over pencil hatching softens the lines (edge contrast down at least 40%)
   without darkening the average; Lift with a wash brush over a 150-grey band lightens the track by at least one of 16
   greys; light Gouache Lift over a dark band comes out at least two greys darker than over white and recovers within
   two brush widths; Drag bends a crossing line at least half a brush width.
4. **Own layer only:** a smudge on the wash layer leaves the ink layer and every other pair byte-identical, and the
   other way round.
5. **No halos:** smearing an ink line on the ink layer over a dark wash never makes the composite lighter than the
   wash alone (premultiplied write).
6. **Pen-down page:** quick and live strokes with carry end the same (`QuickStrokeChecks`); over a page with marks,
   the quick stroke's smudge equals the live one, so it never picked up its own preview.
7. **Undo:** one Undo restores every layer byte for byte, with a Slow settle after the stroke too.
8. **Settling first:** a smudge begun while a Slow wash settles equals settling the wash now, then the same smudge.
9. **Gravity:** after a carry stroke with a Slow gravity wash, the tilted page moves only the wash's share (pigment
   ledger as `GravityChecks`); a smudge-only stroke on a tilted page is identical to one on a level page.
10. **Eraser:** `EraseBothChecks` golden unchanged; erasing over a smudge erases both halves.
11. **Nothing else moves:** with every brush's Carry Off, `LegacyConversionChecks` and the bench's page hashes
    (`ModularPerfBench hashes`) are unchanged; `CoresChecks` with carry: several cores = one.
12. **Rolls:** with carry rolled last, every other part of every style rolls as before for the same `Random`; carry
    choices appear at about the lab's weights over 2000 rolls.
13. **Sweep:** every tip × medium × carry × drying draws marks, no NaN, buffers released (the lab's `sweep.cjs` in
    Java).
14. **Timing:** `ModularBenchChecks` gains the bench cases above, budgeted at about twice this Mac's time.

Device checks for Elling after the build: Stump over pencil; Charcoal Smear at Large; Gouache Lift over ink wash;
Oils Drag at Large; a big Hake Drag (quick, renders at lift); one Undo; a smudge right after a Slow wash.

## Order of work

1. **Web lab:** F1 to F4 in `modular-lab-src`, NOTES, rebuild `modular-lab.html`; Elling tries it.
2. **Java:** `CarryPlane`, `Carry` (reservoir and dabs), `ModularCompose` under-from-plane, `ToneDocument.smearTones`,
   `ModularStroke` (live and render paths), `QuickStroke` (plane filling, preview dither, cost term),
   `ModularFeatures.CARRY = true`, rolls, words, the Stump preset; checks 1 to 14.
3. **Device bench**, then the `QuickStroke` threshold; this spec's as-built section; `INTEGRATION.md` (2(b) for
   `ToneDocument`, section 5's Smudge row).

## Other apps (how they smudge)

- **Procreate:** Smudge uses any brush's shape and blends pixels already on the **current layer**; painting with
  Wet mix (dilution, charge, pull) picks up the layer's colour under the brush. GPU.
- **Fresco live brushes:** watercolour and oil simulated on the GPU, mixing only paint within the same live layer.
- **Rebelle:** physically based wet paint on the GPU; blend, smudge and re-wetting act on the active layer's paint.
- **Krita colour smudge:** *smearing* copies a dab from the previous position (the lab's reservoir is this, with a
  memory); *dulling* samples one colour per dab; **Smudge length** is how much it picks up, **Colour rate** its own
  paint. Samples the current layer, or with **Overlay mode** the merged layers below, written into the current layer
  (option C's ghost-copy problem is the known cost).
- **MyPaint:** one smudge colour per dab (an average under the dab), updated at a rate set by **smudge length**,
  mixed into the brush colour; current layer. MonoPaint's `BrushInk` is the same idea, a single reservoir with a
  capacity.

They stay fast by working per dab, not per pixel of the page (one averaged sample, or one dab-sized copy), by
reading one layer, and on the GPU. Inkwash has no GPU path; the bounded reservoir, the lattice and `Cores` are its
equivalents.

## Tarashikomi and suminagashi

- **Tarashikomi** (Rinpa: Sōtatsu, Kōrin) drops ink or pigment into a still-wet layer and lets it pool and bloom
  with soft, irregular edges. It is wet-into-wet, but passive: nothing drags it. In Inkwash that is the **Drying**
  part (Slow, with Bloom, Backruns, Pool, gravity's Pool), and "Takes ink" plumes when they come. Of the Smudge
  choices, **Drag** ("wet into wet") is the nearest cousin, but it is the brush pushing paint, not paint spreading
  on its own.
- **Suminagashi** ("floating ink") drops ink on water, rings it, fans or blows it, and lifts it onto paper. Nothing in
  Smudge makes the rings. **Smear** is like drawing a comb or a breath through them: it bends and softens marks
  already there without adding ink. **Lift** is neither: it is blotting.

So Smudge is closer to a finger or a dry brush moving paint than to ink spreading in water. The water-drop look
lives in Drying.

## Open questions for Elling

1. **Should a paint brush's smudge also drag pen lines** (option B, both halves of the pair)? Recommended: **no**
   for now. Pen lines stay crisp over their own paint, as today; smudge lines with a pencil, charcoal or Stump brush,
   which work on the ink layer. Revisit after trying it.
2. **A ready-made Stump** (Charcoal · Smear · Own paint 0) on the Tools page? Recommended: **yes**, one preset under
   Charcoal, no new tool.
3. **Should Drag act only on paint that is still wet?** Recommended: **no**. It smears whatever is there; the page
   is always laid before a new stroke, so "still wet" would be hidden state that makes the same stroke behave two ways.

## As built (2026-10-16)

Branch `inkwash-smudge`, from `inkwash-v2-lab` `f17cecd` (the panel's quick rule in). The owner approved the three
open questions as recommended: a paint brush's smudge never drags pen lines (own layer only), a Stump preset under
Charcoal, Drag works on whatever paint is there.

### What changed from the plan, and why

- **F2, tuned: the reservoir trades paint with the page.** Drawn out (`scratchpad/smudge-build-out/`), F2's "follow at
  k" left a smear looking like lightening: at Lay down .55 a dab, a pixel meets about eight dabs, so a line crossed by
  a 30 px Smear was all but erased (black to 241) and spread over 100 px at about one grey. The lab and the app now
  trade: where the page is darker than a cell the brush takes Pick up of the difference, where the cell is darker it
  gives Lay down of it, each over a radius of travel (`1 − (1 − v)^(spacing / r)` a dab); what is taken leaves the
  page, what is given leaves the brush (conserved, as MonoPaint's `BrushInk.trade`). Dabs are `1 + .08 r` apart
  (`r / 4` under 15 px): at `r / 4` the cells combed the streaks into stripes. Dial meanings are as the plan says:
  more Pick up, darker and longer streaks; more Lay down, stronger right behind the line and a shorter tail.
- **F3, tuned:** Lift takes twice as readily (`2 × Pick up`), starts clean (so it lifts at once, also on a wash it
  begins over) and soaks up `1 − Lay down` of what it holds over a radius of travel (it stays damp, so it keeps
  lifting rather than filling up). Opaque Lift doesn't pass over the page (its cover hides it): its paint's grey moves
  toward darker paint it covers by twice Pick up (`ResolvedBrush.tint`, never lighter). "By Pick up" alone gave 1.6
  greys over a dark band; the acceptance asks two.
- **F4:** the reservoir is at most 63 × 63 cells (`n = r / stride + 1` = 31), not 61.
- **Own paint 0 lays nothing:** a pure finger (the Stump) stamps no line and no body (`ResolvedBrush.line`/`body`);
  they were invisible and the Fast-wet stick's re-taper was most of its cost on the Manta. The lab still stamps them,
  unseen; pages match.
- **No lattice smudge.** The plane is full resolution at every size; instead a dab over 4,000 pixels shares its rows
  over `Cores` in bands of whole cell rows (several cores draw what one does: `CarrySmudgeChecks.cores`), and pixels
  inside the soft edge skip the square root. Quick strokes carry the big ones.
- **Compose:** the plane marks the pixels a dab changed (`CarryPlane.fresh`), so the compose shortcut still skips the
  rest (`ComposeShortcutChecks` gained four smudges); on the lattice a smudging stroke composes every pixel of its box.
- **Rolls** use the Bristles branch's late roll (`ModularFeatures.late`, `ModularStyle.rollLate`,
  `ModularVariations.late`, copied unchanged so the two merge), with Smudge's three choices late: the lab's weights,
  drawn after every part (`CarrySmudgeChecks.rolls`: 2,100 rolls with Smudge set Off hash as before, `5e4d29f1`).
- **Words:** the smudge is a sentence of its own ("It smears what's there.") after the marks, and "This brush" keeps
  to 260 characters (`ModularWords.MOST`, leaving out sentences past the second): the combined sentence broke Brush
  Lab's two-sentence budget. Lift "lifts paint off as it goes", Drag "drags the paint beneath along" (wet or dry), in
  the lab too.
- **Samples:** a Smudge card's band and strip are on the brush's own layer (on the wash layer a charcoal Smear found
  nothing); a brush that smudges with under half its own paint (the Stump) shows its tile over a grey band.
- **Quick rule** (`QuickStroke`, eligibility only): the panel's reach counts a smudge's dab and a Slow wash that
  smudges (it changes the page under the pen again and again), and the engine's cost adds the dab over `CARRY` =
  90 px. A finger previews its nib's footprint as the light wash (`ModularCompose.flatTone`). The pen-down snapshot as
  planned: `QuickStroke.sample` fills the plane on the pen's thread (`ModularStroke.reachCarry`, the samples' box plus
  `r + stride + 2`) before the render thread can take the sample; the render reads only the plane.
- **Goldens** are 8-bit PNGs of the lab's tones (`app/src/test/resources/carry`, 4–21 KB each,
  `scripts/carry_goldens.cjs`), drawn at Own paint 0 so the page is the smudge alone; Gouache Lift is replaced there by
  a wash Lift at no darkness (Lift has no Own paint dial). The lab's paper is set to 255 for them.
- The dial demo (`docs/brush-lab/dial-demo.html`) draws the new Smudge and drops its "Coming" mark (its engine copy
  patched in place; its build lives outside the repo).

### Checks

`scripts/test_inkwash.sh` 51 PASS (new `CarrySmudgeChecks`), `scripts/test_document.sh` 86 PASS,
`./gradlew :app:assembleRelease` builds. The web lab: self-test, the 420-combination sweep and the 40-step
click-through pass. The bench's 126 page hashes (`ModularPerfBench` brushes with Smudge Off, 16/120/360 px, three
Lives) are identical before and after. Goldens: all six within one grey of the lab at 16 greys; 6 of 172,800 pixels
one grey off (Oils Drag), the rest exact.

Acceptance (`CarrySmudgeChecks.acceptance`, numbers on this Mac): a 12 px Smear drags a 6 px black line 20 tones (a
grey) a brush width on; under a 30 px Smear the line spreads thinner, 13 tones half a width on and 9 a width on (the
check asks most of a grey and half a grey: a broad finger carries only what a thin line had). Stump over hatching:
edge contrast 36.5 → 11.4 (−69%), mean tone 199.2 → 200.0. Lift over a 150 band: 183 with a light wash brush (.2);
164 against 126 for the same brush without Lift at a middle grey (.5). Light Gouache Lift: 176 over white, 122 over a
dark band, 176 two widths after it. Drag (Watercolor, 60 px) moves a crossing line's darkness 37 px along, 9 tones
half a width on.

### On the Manta (bench build, over marks, `-e marks true`; pen to screen p95, ms)

| Drawn live (`-e quick off`) | 20 px | 30 | 45 | 60 | 90 | 120 |
|---|---|---|---|---|---|---|
| Finger (Own paint 0: Stump, Smear, Drag alike) | 1 | 1 | 2 | 6 | 23 | — |
| Charcoal Smear, Own paint .3, Quick | 2 | 6 | 29 | 89 | 89 | 191 |
| Oils or Watercolor Drag, Own paint .85, Quick | 2 | 3 | 5 | 12–13 | 72–79 | 101–104 |
| Watercolor Drag, Slow | 1 | 1 | 3 | 7 | 41 | 88 |

Before a finger stopped stamping its line, the Stump lagged from 30 px (105 ms). With the rule (`-e quick rule
-e sizes edge`) every smudge at its biggest live size keeps up: p95 1–6 ms. Drawn quick, the pen never waits (p95
≤ 15 ms) and the stroke is final this long after lift (1.5 s stroke): Stump 0.09 s at 90 px, 0.18 at 240; Charcoal
Smear 0.55 / 0.76; Slow Drag 0.54 / 0.81 / 2.28 at 360; Hake Drag 0.98 / 1.17 / 1.55 at 360 (before the cores: 1.1,
2.1, 4.4 and 3.8 s at the big sizes).

### Pictures

From the app's engine, the Manta's dots beside the 16 greys (`scripts/smudge_pictures.sh`): the Stump over pencil
hatching, Drag through a dry and a still-wet wash (and Oils Drag at Own paint .4), Lift over dark (gouache greys,
a light wash lightens), Smear across ink lines (default, finger 30 and 14 px), a paint Drag over pen lines (the lines
stay crisp), and each golden beside the lab's.

### Known limits

- A thin line under a broad finger smears faint: what is carried is what the line had (a 6 px line under 30 px, half
  a grey a width on).
- Oils Drag at its default Own paint (.85) covers most of its own smear; lower Own paint shows more.
- A light wash Lift lightens; at a middle grey its own wash takes back part of what it lifts.
- Every tip smudges with a round footprint (the hake as a circle as wide as its head), as planned.
- A Slow Drag at 360 px is final about 2.3 s after a 1.5 s stroke (quick: the pen never waits).

Later (2026-10-18): Smear's **Soften** dial, MonoPaint's blending stump and combs on a Rake or Split nib, with Blend
stump, Comb and Wash comb on the Tools page: `2026-10-18-stump-design.md`.

### Device checks for Elling

As planned: Stump over pencil; Charcoal Smear at Large; Gouache Lift over ink wash; Oils Drag at Large; a big Hake
Drag (quick, renders at lift); one Undo; a smudge right after a Slow wash. Also: Brush Lab's Smudge card (choices,
dials, samples), and the Stump's tile in a slot.

## Damp tissue lays nothing (2026-10-22, branch `print-tissue`)

The owner: "damp tissue lifts paint but if it touches any light/paper area it leaves a dark mark". The Damp tissue
preset (Watercolor) was a Lift wash with Blend Behind: Lift always lays its own wash as it lifts (it has no Own Paint),
and Behind laid that wash only where the page was blank, so on paper it left a near-black mark (248 of 255 darker).
It is now Clear Water (no paint of its own) with Lay Down 0, so it soaks up what it lifts and lays none of it back;
Pick Up .5 lifts about as much as the old Lift at .8 did (a 120 wash comes up to about 215). `ToolPresetsChecks.tissue`:
across a page half washed, live and quick, at 30 and 80 px, no pixel of paper or wash darker, the wash lifted.

