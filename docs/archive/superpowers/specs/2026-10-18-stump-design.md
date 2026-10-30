# Blending stump and combs (Smear's Soften): design and as built

Status: built on branch `inkwash-stump`, 2026-10-18, from `inkwash-v2-lab` `1b34862`. Owner's ask: "the smudge tool
in monopaint is interesting... useful for the droplet art and smearing wet or other paint.. also similar tool as a
rake/comb". Builds on the Smudge spec (`2026-10-16-smudge-design.md`) and Float (`2026-10-16-float-design.md`).

## MonoPaint's Blending stump against Inkwash's Smear

Upstream's stump is `ToolStroke` with `Tool.SOFTEN` (`ToneDabs.soften`): a round dab every .12 of its width; each pixel
in it moves toward the mean of a small box (a fifth of the radius, 2 to 8 px) around the pixel a quarter radius
*behind* along the stroke (80%) and around itself (20%), by `.7 × strength × (1 − q)²`; strength 35, 57 px, reading
the layer as it goes. Its brushes' "oil paint mode" (`PaintLoad`) runs out of paint over the stroke and then dulls the
page with one averaged tone (MyPaint's dulling smudge).

Drawn by the app's engine on the same pages (`StumpPictures`; one straight stroke and one wave, 44 px, the Stump
against MonoPaint's stump and oil brush; numbers from this Mac):

| | Inkwash Stump (Smear, Own paint 0) | MonoPaint Blending stump | MonoPaint oil brush |
|---|---|---|---|
| Look | an even grey haze along the track; marks it crosses lose their dark into it (an ink line is cut, a narrow wash lightens across the track) | marks stay where they were, edges soften, each mark gets a short comet tail the way it rubs | lays its own grey, fades, then a dull averaged smear |
| How far paint travels | a reservoir's worth, 1–2 widths and faint beyond (ink lines: 22 / 15 / 19 tones at ½, 1, 2 widths) | about a quarter width (7 / 0 / 2) | 40 px pick-up length (33 / 29 / 24, its own paint) |
| Direction | along the stroke, carried | along the stroke, pulled from behind | along, averaged |
| Hatching (edge contrast, mean tone) | 23%, mean kept | 31%, mean kept | 42%, darker (its paint) |
| One pass on a wash or gouache | lightens the crossed band, lays it beyond | barely visible (edges 75–100% kept) | its paint over it |
| Desktop µs a sample (28 px, marks) | 17.5 | (port: 37) | n/a |

What MonoPaint's does better: it smudges *where it rubs* and keeps the drawing's darks in place, so rubbing back and
forth blends hatching and lines into tone like a real stump, where the Smear erases lines into a uniform haze. What the
Smear does better: one pass is strong and it drags far, like a dirty finger. Both are worth having; the stump's was
missing.

## What was built

- **Smear's Soften** (`PartDial.SOFTEN`, key `soften`, label "Soften", 0 to 1, default 0): appended last to Smear's
  dials, in `ModularCode.ADDED` (written only when not 0), never rolled (`ModularStyle.rollChoice`), not in Tweak it's
  pool. Soften `s` works the smear as MonoPaint's stump: the reservoir trades `1 − s` of what it would, then a stump
  pass (`Carry.soften`) on the plane, premultiplied: `ToneDabs.soften`'s rule with Pick up as the strength
  (`.7 × s × Pick up × (.45 + .55 × pressure)`), spread over the stroke's dabs as MonoPaint's are over its own
  (`1 − (1 − w)^(spacing / .24 r)`), every spot's summed-area table built before any pixel is written, big round dabs
  over `Cores` in row bands. At Soften 1, Pick up .35, firm pressure, it draws MonoPaint's stump at strength 35 within
  about a tone on average (`StumpChecks.monoPaint`).
- **Combs:** a stump whose tip is a Rake or a Split nib works only along its tines (`ResolvedBrush.tinesOf`, the
  same tines Float rakes with): a little stump at each tine (`1.6 ×` its half-width, the blur its half-width), a pixel
  taken by its nearest tine, dragging only what it meets behind it by `.7 × strength × (1 − q)`, so its streaks run
  on. A round tip keeps the round footprint.
- **Basic tools:** **Blend stump** (Charcoal, first, the smudge tool's first brush, `ToolSlots.SMUDGE_FIRST`: the Stump at Soften 1, Pick up .8; "Blending stump" is 6 px too wide
  for Brush Lab's bar at 749 × 998), **Comb** (Ink wash: Clear water with a five-line Rake, combs what floats), **Wash
  comb** (Watercolour: a five-line Rake, Wash, Smear, Own paint 0, Soften 1, Pick up 1: combs a wash into streaks).
  A clear-water comb's tile drops round rings, then combs across them (`WildSample.FloatSample`).
- **Words:** a Smear at Soften .5 or more says "Softens." (tag "stump"), with tines "Combs." (tag "comb").
- **Quick rule:** a round stump's dab costs `1 + Soften` times the trade's (`QuickStroke.cost`; desktop: Blend
  stump 37 / 172 / 362 µs a sample at 28 / 90 / 240 px, the Stump 17.5 / 96 / 170, Wash comb 32 / 51 / 228). Not yet
  measured on the Manta.
- **Web lab:** `parts.js` (the dial, `tinesOf`, the words) and `stroke.js` (`stumpDab`) the same; `modular-lab.html`
  patched with the same hunks; Brush Studio rebuilt. Two new goldens (`blend-stump`, `wash-comb`,
  `scripts/carry_goldens.cjs`) match the app exactly at 16 greys; the six old goldens are byte-identical.

## One comb or two

A single Comb can't both comb floating ink and streak a wash: a Float stroke works on the bath (no smudge reservoir on
water), any other stroke prints the bath first, and Sumi's print is permanent (Erasable 0), which a smudge never moves.
So **Comb** is Float's (suminagashi combing) and **Wash comb** streaks paint on the page. A tool that offers "the comb"
can pick Comb while ink floats and Wash comb otherwise (the floating rings show, so it isn't hidden state).

## Own layer only, but a stump on the ink blends the wash too

As every smudge: Blend stump (Charcoal) moves the ink layer (pencil, charcoal, ink lines), Wash comb the wash layer
(washes, gouache). A finger tool that should smudge both needs the pair (the Smudge spec's option B) or one brush of
each medium.

**2026-10-19, "blend washes" (the owner; branch `inkwash-speed2`):** a stump, a Smear with Soften, on the selected
pair's ink layer now blends the pair's wash too, the Smudge spec's option B for stumps only
(`ModularStroke.alsoBlends`). The wash gets a plane of its own (`CarryPlane` of that layer as it is,
`ToneDocument.layerPixels`/`layerKept`, so its Erasable shares and masking fluid hold as on the stroke's own layer) and
a clean reservoir, worked by the stroke's own dabs; what it moved there is composed at finish in a gesture of its own,
linked to the stroke's (`finishWithPrevious`, as the eraser lays its wash), so one Undo takes both. A live stroke
(under 36 px) shows the wash's part at lift; a quick one shows both at lift. Wash comb and every other paint smudge
still move only their own layer (pen lines stay crisp). A stump's spot that reads only blank tiles is passed over, so
the second plane costs little over a blank wash; ink-only pages draw byte for byte. `StumpChecks.washes` and
`erasable`: a wash's hatching steps 36.5 → 6.1 at its darkness (55.4 → 55.1), both layers where both have marks,
Undo and Redo, quick as live, a hidden wash and Wash comb's ink untouched, permanent ink byte for byte, a dried wash
softening 83% at Erasable 1, 33% at .4, 0% at 0, masking fluid blocking it. The web lab draws on one page, so its stump
already blended everything: no web change.

## Smearing wet paint (a Slow wash still settling)

Today any new stroke lays what still settles first (`WatercolorSession.settleNow`), so a smudge reads the laid page:
the pictures `4-wet-slow-wash.png` and `3-dry-wash.png` are the same. A wet-into-wet smear (moving the still-wet pigment
and its water) is feasible but not small: the smudge would have to advect `DryingWater`'s suspended pigment per 2 × 2
cell (`S`) along with the page, re-base the patch (it writes `tone at lift × (1 − shown) / (1 − shown at lift)`, which
would overwrite the smear), keep `SettleWorker`'s off-page work in step, keep Undo's order (the settle steps are
continuations of the wash's gesture, the smudge a gesture between them), and move `GravityFlow`'s ledger. Roughly
`DryingWater` + `SettleWorker` + `WatercolorSession` + Undo checks, several hundred lines and a device bench; not built.

## Sizes: what shows is what it smudges (2026-10-19 fix, branch `inkwash-smudgefix`)

The owner on the Manta: the Water brush's and the Dirty brush's preview "much smaller than the area of effect", and the
Blend stump's two smallest sizes "no effect, draw no mark". Measured on the desktop (`SmudgeSizeChecks`, each brush
straight across a striped wash on its own layer at the swatches' 4, 16, 44 and 120 px; rows changed across the stroke):

| | 4 | 16 | 44 | 120 |
|---|---|---|---|---|
| Dirty brush, under the pen, before | 6 | 16 | **4** | **12** |
| Dirty brush, after lift | 6 | 16 | 42 | 114 |
| Water brush, under the pen, before | 6 | 16 | **4** | **12** |
| Water brush, after lift (settled) | 10 | 24 | 56 | 150 |
| Blend stump, under the pen, before | 6 | 14 | 54 | 148 |
| Blend stump, after lift | 6 | 14 | 38 | 106 |
| every brush, under the pen, now | 6 | 14–16 | 43 | 119 |

- **The preview.** Past 36 px a smudge's stroke goes quick (`QuickStroke.panelReach`: its dab's 2r), and a smudge
  with no paint of its own previewed its *nib*: a wash's nib is a tenth of its size (`inkSize = washSize / 10`), the
  smudge's dab half of it (`carry.r = washSize / 2`), so a 120 px Water or Dirty brush showed a 12 px line, a solid
  grey one, over a smear 114 to 150 px wide. Now the preview sweeps the smudge's own dab (`ResolvedBrush.Carry.stumpR`: its radius,
  or out to a comb's outer tines), in the light wash's dither (the Smudge spec's "something happens here"), so it is as
  wide as the size dot. The working radius was right (the size dot is its diameter) and is unchanged: the Dirty brush
  smears over the size shown, and the Water brush's run-out spreads a quarter past it (two box blurs of .8 r through its
  wet, as built), never several times. Live strokes (16 px and under) never had a preview: the stroke itself shows.
- **Small stumps.** MonoPaint's stump was never under 19 px across (its lightest press). Under 20 px Inkwash kept
  MonoPaint's reach, a 5 × 5 blur and a pull of a quarter radius (1 to 2 px), which mixes less than the pencil and
  charcoal lines and grain under it (a few px, whatever the stump's size), and its `(1 − q)²` falloff left only the
  middle of the dab working: a 4 or 16 px stump changed a third of its band over HB pencil, a 44 px one 61%, and the
  Manta's 8 × 8 dots showed a dotted hairline or nothing. Now a round stump under 10 px in radius
  (`ResolvedBrush.Carry.small`) blurs and pulls at least 3 px (`FINE_REACH`), and its falloff eases toward `(1 − q)`
  (fully at 6 px in radius and under, none from 10 px up: `flatter`). Over HB pencil a 4 / 16 px stump now changes 50% /
  46% of its band by a grey (charcoal 77% / 70%; 44 px: 61% / 74%), still within its size. From 20 px up every stump
  draws exactly as before (the goldens, MonoPaint's stump at 20 / 44 / 90 px, `ModularPerfBench hashes` all unchanged).

Also seen: a Blend stump (Charcoal) smudges only the ink layer, so over a wash it changes nothing at any size, though
from 44 px up it shows a footprint under the pen ("Own layer only" above). If the owner tried it over a wash, that is
why the small sizes looked dead and the big ones seemed to draw. Not changed here.

## Checks

`StumpChecks` (new; `scripts/test_inkwash.sh` 61 PASS on `f411ca8`, `test_document.sh` 98, Brush Studio 115 of 115): codes (Soften two digits more, Soften 0 as before, rolls
never set it), the two goldens, MonoPaint's stump (20 / 44 / 90 px: 1.1 tones apart on average, at most 1.8% of
changed pixels a grey and 8 tones apart), hatching (edges 36.5 → 6.1, mean 199.2 → 199.9), a line pulled the way it
rubs (68 tones darker just after it, 3 before, 0 two widths on; the Stump 26, 0, 7), Wash comb (5 streaks past a wash's
edge, nothing on paper), Comb (rings combed 28 px along, area within 0.04%, nothing on paper), quick as live, cores.
`ModularPerfBench hashes` (210 cases) identical before and after; `LegacyConversionChecks` and `CarrySmudgeChecks`
unchanged.

## Pictures

`scratchpad/stump-out/` (outside the repo): `1-pencil-hatching.png` … `5-opaque-paint.png` (each page smudged by the
Stump, Smear at Soften 1, MonoPaint's stump and its oil brush), `tune-0.png` … `tune-3.png` (Soften and Pick up, one
pass and rubbed), `probe-comb-0.png` / `-1.png` (Wash comb over washes and gouache), `probe-rings-float-comb.png`
(Comb through floating rings), `tiles.png` (the Tools page tiles). `StumpPictures` draws the comparison pages.
