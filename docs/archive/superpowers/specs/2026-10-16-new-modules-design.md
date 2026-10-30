# New modules: Blend, Stipple, Hatch, Resist, Halo (design)

Status: proposal. Elling approved prototyping the five ("blend, stipple, crosshatch, halo, and resist seem awesome!");
built in the web Modular Brush Lab (branch `inkwash-newmods-spec`, "Web prototype" at the end); in the app all five
("as built" sections at the end). Halo is shown as **Mold** (the owner, 2026-10-18); its key and codes stay `halo`.
Builds on `2026-10-14-modular-engine-design.md` (parts, codes, Speed on the Manta, Quick then render, Gravity),
`2026-10-16-smudge-design.md` (own layer, `CarryPlane`, `smearTones`), `2026-10-16-float-design.md` (rare rolls from the
seed, the bath's rules) and `2026-10-16-erasable-design.md` (the share plane, the eraser). Earlier prototype: Living Ink
(`docs/archive/brush-lab/prototypes/living-ink.html`, its Mould). Upstream: MonoPaint's masking fluid (`ToneDocument.mask`,
`maskHatch`, `peelMask`, `MaskPeelTouch`).

## In one paragraph

No seventh part. **Blend** is a Medium dial that says how a mark meets what is already there: the medium's own way
(**Darken** for ink, wash, charcoal and graphite; **Cover** for Opaque, which keeps its Opacity as the amount),
**White** (paints light over dark: a white gel pen, chalk), **Behind** (paints only where the page is still blank, under
what is there) and **Invert**. **Stipple** and **Hatch** are Tip choices: dots scattered along the path, packed closer
by pressure; parallel lines that pressure crosses a second and a third way, building greys from crisp black lines.
**Resist** is a Medium choice: masking fluid or wax, shown hatched on screen, that nothing takes on until the eraser
rubs it off (wax stays). It is MonoPaint's masking fluid, reused. **Halo** is a Drying choice, Living Ink's Mould: while
the stroke is wet, veins reach out to nearby marks and bridge them, then set. Everything is appended: old codes read,
draw and write byte for byte, and every new choice is rare in Randomize.

## Where each one lives

| Module | Part | Code | Dials (appended to that choice) | Simple's slider |
|---|---|---|---|---|
| Blend | Medium, a dial on every choice | `medium.blend`, a choice dial after Erasable, `ADDED` (written only when not 0) | options 0 own (Darken; Opaque: Cover), 1 White (key `lighten`), 2 Behind, 3 Invert | none (the card's name says it: "White ink", "Wash behind") |
| Stipple | Tip, choice 7 (`stipple`) | `7` + dials | Dot size, Scatter, Jitter, Weight | Dot size: fine ↔ coarse |
| Hatch | Tip, choice 8 (`hatch`) | `8` + dials | Spacing, Line weight, Angle 0–180°, Lines (follows / fixed, default fixed), Layers 1–3, Weight | Spacing: tight ↔ open |
| Resist | Medium, choice 5 (`resist`) | `5` (+ Erasable when off) | Erasable (fluid 1, wax 0) | Erasable: wax ↔ fluid |
| Halo | Drying, choice 4 (`halo`) | `4` + dials | Drying time (Slow's and Float's, kept across a swap), Reach, Growth | Reach: close ↔ far |

### Why Blend is a Medium dial, and how it meets Opaque

A seventh part would add a card to every brush for something most brushes never change. Blend belongs to the Medium:
it is a property of the paint (white gel ink, chalk, opaque white gouache are media). Today's Opaque already is a blend:
it covers. So Blend's first option is **the medium's own way**, shown as **Darken** on every medium but Opaque and as
**Cover** on Opaque, and the code's 0 means it. One concept, one term each:

- **Cover** is what Opaque does; **Opacity** stays Opaque's dial and is how much it covers. No other medium covers:
  "opaque ink" is Opaque on a small round tip.
- **Darken** is what every other medium does (a glaze), as today.
- **White** lays white, the shade setting how strongly (Dark full white, Light about half, Lighter a third), so it only
  ever lightens. On Opaque it is opaque white. (Its key is `lighten`; "White" is the display word because it says what
  it lays, fits the Manta's row and matches the captions: "White ink", "Chalk".)
- **Behind** is the medium's own way, only where the page is blank: blank paper takes all of it, mid grey and darker
  none (a smooth ramp, `smoothstep(.55, .96, tone/paper)`), so it slips under every mark already there.
- **Invert** flips what is under it toward its opposite, by the shade: paper turns black, black white, mid grey stays.

### Keeping the lab uncluttered as parts grow

- **No new part, no new screen.** Each module is a choice of an existing part or one dial shared by every choice.
- **Shared dials go last, in the same order everywhere:** a Medium card ends with Erasable, then Blend, whatever the
  medium. The eye learns where they are; the medium's own dials stay on top.
- **Simple shows nothing new.** Its cards keep one slider each; the new choices only change the card's name and sample
  ("White ink", "Ink stipple", "Masking fluid", "Halo 6.6 s").
- **Rare in rolls, never in variations.** Randomize reaches them now and then (below); the six variations never swap
  to one, so a brush never turns odd by surprise.
- **Nothing clipped.** Blend's four options fit the Manta's row as a segmented control; where a choice dial's options
  don't fit (the Nomad, also Line style's three there), it becomes a menu button (▾). Dials that do nothing for this
  brush are greyed, as today (Blend on a Float brush).
- **A rule for later:** a behaviour that changes how paint meets the page is a Blend option; a shape is a Tip; what
  happens after lift is a Drying choice. If a part's choices outgrow two rows of chips, they get a second page in the
  swap strip rather than a new part.

## 1. Blend

| Blend | Over paper | Over a dark mark | Like | Rule (tones, lower is darker; `u` the page under, `a` the mark's darkness over blank paper) |
|---|---|---|---|---|
| Darken (own) | the mark | darker still | ink, a glaze | today's compose |
| Cover (Opaque's own) | its grey | its grey | gouache | today's compose |
| White | nothing | white, by the shade | white gel pen, chalk, white gouache | `u + (paper − u) · a` |
| Behind | the mark | nothing | a wash laid after inking, paint behind line art | own way × `blank(u)` |
| Invert | black, by the shade | white | a negative | `u + (paper − 2u) · a` |

Pictures (the web lab's engine in Node, 16 greys; not committed), `scratchpad/newmods-out/`: `1-wash-behind.png` (a line
drawing in dark and light ink, then the same wash glazed over it and behind it; below, light grey opaque paint covering
the drawing and behind it: the lines stay), `2-white-gel.png` (white gel pen stars, a line, hatching and a Light grey
ring over dark gouache; white chalk on cold press below), `7-invert.png` (black lines that turn white crossing a black
band).

**Interactions**

- **Erasable:** White and Invert keep their medium's value (a white gel pen is ink: permanent); the eraser takes the
  erasable part as for any mark. Behind's mark is erasable as its medium.
- **Smudge:** Lift, Drag and Smear move the page as found; the brush's own paint then meets it with its Blend.
- **Slow (settling) and gravity:** White and Invert carry no pigment, so they don't settle, run or drip (their Slow is
  only time wet, for Takes ink). Behind settles, runs and drips only where it landed: its suspended pigment is scaled by
  how blank the page was.
- **Float:** on water every medium is a darkening film (Float spec), so Blend has no effect there; Brush Lab greys it.
- **Halo:** Behind's veins grow only on blank paper; White and Invert grow no halo (veins only darken).
- **Resist:** a resist shields every Blend.
- **Quick then render:** the preview under the pen draws the mark as it will be: White as white dots on the panel's
  black-and-white fast path, Invert as the inverted dot, Behind only where the page is blank (it reads the page as
  found, which the quick stroke already has). Lift renders the same compose, so the end is unchanged.
- **The eraser:** unchanged.

**In the app.** `ModularCompose` already has the stroke's own coverage at each pixel (`own`, the Erasable spec), so no
second pass: White and Invert are one extra mix a pixel, Behind one multiply. Layers: White and Invert write coverage
(`coverTones`, as Opaque does), so a white gel pen on the ink layer covers the wash beneath; a White wash on the wash
layer can't lighten the ink above it (decision 6's limit, as for Opaque). Behind and Invert read the pair as found: the
composited tone of the stroke's pair at gesture start (`TonePage`, as `GravityFlow` reads it), tile by tile on first
touch.

## 2. Stipple

Dots scattered around the path. Every cell of path, a dot and a bit long, holds a fixed set of candidate dots (from the
stroke's seed and the cell's number); pressure decides how many land (12% light, all pressed), so tone rises with
pressure and with each pass. The band is the tip's width times `1 + 1.6 × Scatter`; **Dot size** is up to two fifths of
the tip across (at least 1.6 px, so the panel shows each dot); **Jitter** 0 sets the dots on a staggered lattice (an
even stipple), 1 scatters them, varies their size and clumps them in twos to fours (a sponge). Control's Pressure size
narrows the band when light; Pressure dark lightens the dots.

- With **ink** they are pen dots; **wash** makes a sponge print (with the wash's edges and blooms when wet); **Opaque**
  covers in dots; **charcoal** and **graphite** dots catch the tooth.
- Tone at default spacing (a 26 px pen stipple, mean darkness in its band): 25% light, 59% at .6, 73% pressed.

Pictures: `3-stipple.png` (a pen stipple from light to pressed; a ball shaded by pressure away from the light; a sponge
in two shades).

**Interactions:** Erasable and Blend as the medium; Smudge moves the dots after they land; Quick: each dot is laid once
(nothing to re-taper); Slow: a stippled wash settles as one; Float: the dots float as films and push older paint;
gravity: a stippled wash runs like any wash; Halo grows from every dot's edge; the eraser as any mark. **Quick then
render:** dots are a function of the samples only, so quick and live strokes lay the same dots however the samples are
batched (checked: 1 or 7 a time, the same page); on the panel's fast path the dots are already black or white.

**Manta:** cheaper than a round tip (Node on this Mac: 11 µs a sample at 16 px, 40 at 120, 101 at 360, against a round
tip's 19, 172, 600), so about 0.2–2 ms a sample on the Manta; no memory beyond the stroke's buffers (candidates are
hashed, never stored).

## 3. Hatch

Parallel lines instead of a solid mark, inside the band a round tip of its weight would cover. Each pixel takes the
lines of its nearest dab: one set; where pressure passes .45 a second set at 90° to it; past .72 a third at 45° (up to
**Layers**). **Lines: fixed** puts the angle on the page, so strokes of one brush line up and an overlap adds nothing
(the tone comes from pressure, not from scrubbing: checked); **follows** turns the angle with the stroke (Angle 90
hatches across a curve, like contour hatching). **Spacing** is 3–18 px and **Line weight** 0.9–4 px at a 30 px tip, both
growing with the square root of the size past that.

- The greys are built from black lines: at default spacing a band is 21% dark with one set, 37% with two, 50% with
  three. Only line edges are in-between greys (45% of the dark pixels' count, a pixel each side), so it reads crisp on
  16 greys and dithers nowhere.
- With wash it is a soft striped wash; with charcoal or graphite the lines catch the tooth.

Pictures: `4-hatch.png` (light to pressed: one, two, three directions; a cube shaded in three passes; lines that follow
an arc and a wave).

**Interactions:** as Stipple. Quick then render: the lines depend only on the samples (checked: batched 1 or 7, the same
page); the fast path shows them as they are (black). **Manta:** about a round tip's cost (157 µs a sample at 120 px
against 172; 475 at 360 against 600), plus a nearest-dab distance per pixel over the stroke's box (`FloatTiles`, a short
a pixel in the app, cleared at lift).

## 4. Resist

Masking fluid or wax: the mark itself is nearly invisible, and whatever comes later (washes, ink, every medium and
Blend, settling, runs, drips, Float's print, Halo's veins, a smudge's lay-down) takes `1 − resist` of its change there.
Its one dial is **Erasable**: **masking fluid** (1, the default) rubs off; **wax** (off) stays.

| | On screen | In the drawing | Removed |
|---|---|---|---|
| Masking fluid | MonoPaint's hatch: diagonal lines 4 px wide every 12 px, black over light, white over dark, where it shields at least 32 of 255 | nothing | the eraser lifts it first: one pass takes the fluid and leaves the paint under it; the next pass erases as usual. Clear page peels it all. |
| Wax | nothing (no hatch) | a faint sheen, one grey step, and it catches only the paper's high points, so on cold press the wash seeps into the grain | never (it shields the paint under it from the eraser too) |

Why the hatch: it is MonoPaint's own answer, made for this panel (black and white only, so it shows on the fast
update); it marks a working aid that is never part of the drawing (exports and the drawing's PNG never show it), and it
goes the moment the fluid is lifted. Wax isn't a working aid; real wax shows by a slight sheen and by what refuses to
take, so it lays its sheen and is otherwise unseen.

Pictures: `6-resist.png` (masking fluid lines and rings with a wax band on cold press, hatched; the same under a dark
wash; the fluid then rubbed off along its lines with the small eraser: white lines in the wash, the wax speckled).

**Interactions**

- **Erasable:** its own dial, as above; under wax the eraser takes only `1 − resist` (the shield applies to erasing, as
  MonoPaint's does).
- **Smudge:** lay-down is shielded (upstream's shield on `smearTones` already does it).
- **Slow, gravity:** settling, runs and drips write through `ToneDocument` and are shielded; running water doesn't enter
  masked pixels (upstream's rule).
- **Float:** the print is shielded; a Resist brush never floats (on Float it lays as Quick).
- **Halo, Blend:** shielded; Resist has no Blend of its own.
- **Quick then render:** the preview shows the hatch under the pen (black and white: the fast path's own kind); at lift
  the mask is written.
- **Tools page and samples:** a Resist tile shows its loop hatched.

**In the app: reuse MonoPaint's masking fluid.** `ToneDocument` already has the page-wide mask layer (TSM4), the shield
in every write (`setPixel`, `paintSpan`, the block writes), `maskHatch` and `DrawingPad.drawMask`, `peelMask` and Undo;
Inkwash hides only the UI to paint it. A Resist stroke is a `ModularStroke` whose gesture binds the mask
(`paintMaskingFluid(true)` for that gesture) and writes its own coverage there. Wax is the Erasable spec's share plane
on the mask layer (its permanent part), and its sheen a linked continuation on the pair's ink layer (one Undo). Upstream
changes: `maskHatch` hatches only the liftable part; `EraseBothStroke` first erases the mask where fluid can lift (a
linked mask gesture), then the layers. **Manta:** the mask layer is 9.8 MB when the page has any resist (upstream's
cost), 4.9 MB more with wax; every write already reads the shield when a mask exists, so drawing costs what it costs
over MonoPaint's fluid today (the lab: a 120 px wash over a page with resist 390 µs a sample against 313).

## 5. Halo

Living Ink's **Mould**, which Elling liked best, as a Drying choice: it happens while the stroke is wet, and **Drying
time** (0.75–20 s, default 6.6 s) sets when it sets, as Float's does. At lift the stroke is laid as Slow lays it (no
settling of its own); then scouts leave its edge into a band of **Reach** around it (12–80 px, growing with the square
root of the tip past 6 px), sense left, ahead and right, steer up their trail and toward other marks, and lay trail; the
trail diffuses and fades over the band only. Changed from Living Ink so it bridges and stays quiet:

- Sensors 8 px ahead at ±43°, a little wander (.18), other marks worth five trail.
- **Shuttle:** a scout that reaches another mark turns back and carries to its own stroke, and lays a second, bridge
  trail. Veins form where traffic passes three times the band's mean; they darken to .7 where the bridge trail runs
  and only to .2 elsewhere, so bridges read and the rest is a faint halo.
- Vigour falls as it dries (`(1 − t/N)^1.4`), so growth slows to nothing; it only darkens (veins are multiplied in, so
  they commute with any glaze); then it never moves again (`PHILOSOPHY.md` #4).
- **Growth** sets how many scouts (1–6% of the band, at most 2,400) and how fast veins darken.

Halo strokes grow together: one laid while others are still growing feeds them (its mark is food for theirs), so a
cluster of strokes bridges both ways. **Any stroke that isn't Halo, the eraser, Undo, a fill, a layer change or leaving
the screen sets every growing halo where it is**, as a new stroke lays a Slow wash (decision 10); a slot, shade or size
change doesn't.

Pictures: `5-halo.png` (Halo ink lines, rings and ticks laid one after another while the others grew: veins bridge the
two waves, the rings to the line below, and a faint halo stays within reach).

**Interactions:** Erasable (veins erase as their stroke); Smudge (a smudge stroke sets halos first); gravity none (veins
aren't water; a Halo brush doesn't drip); Float (another Drying choice); Blend (Behind's veins only on blank paper;
White and Invert grow none); Resist (veins shielded); the eraser (sets halos, then erases veins with their stroke).
Quick then render: the stroke draws as Slow does; growth is after lift.

**Manta speed and memory.** Node on this Mac, a 1,000 × 440 px wave: setup at lift 50–90 ms (the chamfer distance, the
band, the edge), a tick 2.6–6.5 ms (band 160–610k px, 2,400 scouts), 66 ticks for 6.6 s. On the Manta (≈20×) that is a
1–1.8 s setup and 50–130 ms ticks, too slow as is. The plan, in order:

1. **Trail on 2 × 2 cells** (as `DryingWater`'s pigment): a quarter of the diffusion, the band and the memory. Scouts
   keep page coordinates.
2. **Band only, in 64 px tiles** (`FloatTiles`): trail and bridge trail as shorts, distance, accumulation, level and
   food as bytes: about 3.5 B a page pixel of band, 0.5–2 MB for these strokes (the lab's floats over the whole box:
   22–40 MB); cap the band at 1M page pixels (trim Reach past it).
3. **Off the pen thread:** setup and ticks on the session's `SettleWorker`; the stroke shows at lift, the halo follows.
4. **Cores** for diffusion (rows of tiles) and scouts (ranges), same result on any number of cores.
5. **Ticks paced** by `SettlePacer`; shown by grey updates at most every 120 ms, only the rectangle that changed.

Expect a setup under 0.15 s after lift and 4–10 ms a tick on four cores; one core's worth stays under the 40 ms tick
budget for a page-long stroke. Bench cases to add (`ModularPerfBench -e halo`): a Halo ink wave at 6 and 16 px beside an
ink line, reach .55 and 1; three Halo strokes growing together.

## Randomize

As Float: each decided after every other part, from the brush's seed with its own salt (`hash01(seed ^ salt)`), never
another draw from `Random`, so every other roll is unchanged (checked: of 1,400 seeded rolls, 1,351 identical, 49 with
one new choice and nothing else changed; the lab's seeded library unchanged). A locked part is left alone. Variations
never swap to a new choice.

| Rolled | Styles, odds | Its dials |
|---|---|---|
| Stipple | Anything goes 1/30, Fineliner 1/40, Watercolor 1/45 | Dot .35–.65 (a wash .35–.55), Scatter .2–.8, Jitter any (a wash .7–1) |
| Hatch | Anything goes 1/30, Dip pen 1/30, Fineliner 1/35, Pencil 1/35 | Spacing .2–.6, weight .15–.45, angle 30–60°, fixed 3 in 4 |
| White | Pastels 1/30, Anything goes 1/40, Charcoal 1/40, Gouache 1/40 | |
| Behind | Watercolor 1/30, Ink wash 1/30, Gouache 1/35 | |
| Invert | Anything goes 1/80 | |
| Resist | Watercolor 1/60 | fluid 7 in 10, else wax |
| Halo | Anything goes 1/30, Ink wash 1/30, Fineliner 1/50 (not with Float) | Reach .3–.8, Growth .3–.8 |

## Words (`docs/TEXT-STYLE.md` additions)

- Word list: **Tip** + Stipple, Hatch; **Medium** + Resist; **Drying** + Halo; **Blend** options Darken, Cover, White,
  Behind, Invert. **Dials:** Dot size, Scatter, Jitter, Spacing, Line weight, Angle, Lines, Layers, Reach, Growth,
  Blend.
- Card names: "White ink", "White wash", "White paint", "Chalk", "White pencil"; "Ink behind", "Wash behind" (as built:
  the medium's plain noun, so the app's module keeps one line); "Invert ink"; "Masking fluid", "Wax resist"; "Halo 6.6 s".
- "This brush": the first sentence's noun ("Ink stipple.", "Sponged wash.", "Ink hatching.", "White ink line.", "White
  chalk.", "Masking fluid on cold-press paper.", "Wax resist."); then, after the marks, "Goes behind marks." / "Inverts
  what's under it." / "Nothing takes on it until rubbed off." (wax: "Nothing takes on it."); Halo in the drying's place:
  "Veins bridge nearby marks for 6.6 s."
- Captions add one tag after the style: "white", "behind", "invert", "stipple", "hatch", "resist" / "wax", "halo"
  ("Fineliner, white"; "Watercolour, stipple").
- Swap tile note: "halo 6.6 s".

## Codes

Examples (the lab): a Fineliner with White writes `…_000zz0000000000000001_…` (its Erasable `00` and Blend `1` added to
the medium); Opaque behind `_2zz00at2_`; a Stipple tip `_7cleei0i0_`; Hatch `_8eeat901zzi0_`; wax `_500_`, masking fluid
`_5_`; Halo `_4jsi0i0_`. An app without these reads a code with a new choice as null (a choice past the part's choices)
and a code whose only change is Blend as the plain medium (characters after a segment's dials are ignored): noted in the
CHANGELOG when built.

## Checks

In the web lab now (`newModChecks` in RUN CHECKS and Node; all pass), and to port:

1. Old codes (the legacy app codes, Erasable's, Float's) re-encode byte for byte; Blend is written only when not 0; each
   new choice round-trips.
2. Rolls: with the new rolls off, every seeded roll as before; on, a brush is either identical or differs by one new
   choice (1,400 rolls); the seeded library unchanged.
3. White: a white gel line over a dark wash is near white; on blank paper it lays nothing (0 px).
4. Behind: a wash behind a light ink line leaves the line's grey (119 against 85 glazed); on paper the same as Darken.
5. Invert: paper to black, black to white.
6. Resist: a wash doesn't take on fluid or wax; one eraser pass lifts the fluid and keeps the wax; a wash after the
   lift takes where the fluid was.
7. Stipple: dark pixels rise with pressure (×2.9); batched 1 or 7 samples a time, the same dots.
8. Hatch: pressure crosses the lines (×2.4 dark pixels); in-between greys under 90% of the dark count; overlapping
   fixed strokes line up; batched 1 or 7, the same lines.
9. Halo: veins bridge a line 28 px away (1,207 px between) and none grow past Reach; a stroke that isn't Halo sets it.

Also kept passing: the lab's self-test, the sweep (now 1,080 combinations, every one draws; the 560 of old choices draw
the same page, hash for hash, as before), the click-through (65, eleven new: the presets, White over dark, the fluid
hatched, a wash not taking, the lift, Halo growing and set, Hatch and Stipple drawing, Blend's options on Opaque), 392
layout states with no overflow and no clipped text, no horizontal scroll at 390 px.

For the app add: `LegacyConversionChecks` and the bench's page hashes unchanged with every new choice off; quick and
live strokes equal for Stipple, Hatch and every Blend (`QuickStrokeChecks`); Blend on each layer (White on the ink layer
covers the wash; Behind and Invert read the pair as found); Resist through the mask layer (a TSM4 round trip; Undo of a
resist stroke and of a lift; the hatch only where liftable; wax's share plane and sheen linked; Clear peels); Halo
(deterministic for the same strokes and ticks; set by a non-Halo stroke, the eraser, Undo, a layer change; `CoresChecks`
for its diffusion; timing budgets).

Device checks for Elling: White gel pen over a dark wash; a wash behind an inked drawing; Hatch pen shading at three
pressures; Stipple pen and sponge; Masking fluid, a dark wash over it, the small eraser along the fluid; Wax resist on
cold press under a wash; Halo ink lines near each other, then another brush (it sets).

## Recommended build order

1. **Blend** (smallest, most used): `PartDial.BLEND` (`ADDED`), option labels per medium, `ModularCompose` (own, White,
   Behind, Invert with `own`), `TonePage` read for Behind and Invert, the quick preview's white and inverted dots
   (`StrokePanel`), words and rolls; checks 1–5. Opaque's Cover needs nothing new.
2. **Hatch, then Stipple** (pure stampers beside `LineStamper` and `BodyStamper`, no new state outside the stroke):
   `PatternStamper` (`stipple`, `hatch`), `ResolvedBrush.Pattern`, `PartChoice` 7 and 8, words, rolls, presets Hatch
   pen, Stipple pen, Stipple sponge; checks 7–8 and the bench's hashes.
3. **Resist** (reuses upstream, small upstream changes): the mask gesture from `ModularStroke`, `maskHatch` on the
   liftable part, `EraseBothStroke`'s lift, wax through the share plane (after Erasable lands); presets Masking fluid
   and Wax resist; check 6, the codec round trip.
4. **Halo** (largest: a model, a session, threads): `HaloGrowth` (pure: cells, band tiles, scouts, ticks), held by
   `WatercolorSession` beside the Slow patch and the bath (set rules, ticks, Undo as one step with its stroke),
   `SettleWorker`, `Cores`; preset Halo ink; check 9 and the bench.

## Open questions for Elling

1. **Blend as a Medium dial with the medium's own way first** (Darken; Cover on Opaque, which keeps Opacity), and its
   second option shown as **White** rather than "Lighten"? Recommended: **yes** to both: no seventh part, one term for
   each way, and "White" says what it lays and fits the row.
2. **How resist comes off:** the eraser lifts masking fluid first (one pass lifts, the next erases), wax never; no Peel
   button (Clear page peels all)? Recommended: **yes**: it is how fluid really comes off, and Inkwash keeps MonoPaint's
   Peel hidden.
3. **Halo strokes grow together, and any other stroke sets them** (as a new stroke lays a Slow wash)? Recommended:
   **yes**: a cluster of Halo strokes bridges both ways, and the page never changes under a different brush.

## Web prototype (as built, 2026-10-16)

`docs/brush-lab/prototypes/modular-lab.html`, sources in `modular-lab-src/`; everything new in **`newmods.js`** (built
after `checks.js`), which extends parts.js's tables and wraps `resolve`, `composeStroke`, `Stroke` (a subclass: the
stampers, the smudge's shield, Halo at lift), `Eraser` (a subclass: the lift), `Water.mul` and `addWet`, `Bath.print`,
`showPage` (the hatch), `blankPage`, the rolls, `makeVars`, the words and the samples (a White brush's tiles on a dark
ground). Outside it: ui.js shows a choice dial's options through `optLabel` (two lines) and its sweep counts a Resist by
its plane (one); parts.js's `lifeNote` says Halo (one). Eight presets lead the library behind Erasable's three: White
gel pen, Wash behind, Stipple sponge, Stipple pen, Hatch pen, Halo ink, Masking fluid, Wax resist.

- The lab draws on one tone page, so Behind and Invert read that page, and White, Invert and Resist compose the mark a
  second time over blank paper to get its darkness (the app has it already: `own`). Per sample (Node, this Mac): White
  and Invert +40% over Darken at 120 px, Behind up to +17%; Resist as a line.
- Resist is two planes on the page (`resist`, `resistKept`); the hatch is drawn over the page on screen only.
- Halo runs on whole-box float arrays (22–40 MB for a long wave); the app's plan above is the real one.
- Build: `scratchpad/newmods-lab/build.cjs html` patches the base ref's page with the worktree's sources;
  `sweep2.cjs` compares the old combinations with the base ref's bundle; `pictures.cjs` writes `newmods-out/`.

## Stipple and Hatch in the app (as built, 2026-10-16)

Branch `inkwash-hatch`. Resist and Halo are still the web lab's only (Blend: below).

- **Parts and codes.** `PartChoice` STIPPLE (Tip 7: Dot size, Scatter, Jitter, Weight, Build-up) and HATCH (Tip 8:
  Spacing, Line weight, Angle 0–180°, Lines follows/fixed (Hatch's head turn, default fixed), Layers 1–3, Weight), the
  lab's keys, ranges and codes: the lab's presets and the owner's Zealous shallot (`zealous-shallot.M67gzlduui0_…`, a
  sponged Watercolor stipple) decode and write back byte for byte. Old codes are untouched (LegacyConversionChecks and
  the bench's 210 page hashes the same).
- **Build-up** (new, the owner's ask: "can it randomize position on hold?"): Stipple's last dial, off / slow / fast,
  `ADDED` (written only when on: `_7cleei0i0_` stays the lab's). Every 20 ms of pen time since pen-down is a tick at
  the pen's place and pressure, fed by every sample kept or not; each tick lays, at random in the band round the pen,
  as many dots as cover it once in 4 s (slow) or 1.2 s (fast), by pressure, from the stroke's seed and the tick's
  number. So it depends only on the samples and their times: quick and live strokes, and any batching, lay the same
  dots. A resting pen may send nothing, so the pads (`DrawingPad`, `BrushTestPad`) send its last sample again after
  30 ms of quiet (`HeldPen`, only for a stroke that builds while held); the web lab's test area and Brush Studio tick
  it the same way. Measured (Stipple pen at 26 px, dark pixels after a rest at one spot): off 35 whatever the time;
  slow 82, 134, 232, 403, 666 and fast 202, 344, 586, 940, 1,245 at 0.25, 0.5, 1, 2 and 4 s. In words: "Builds up
  where it lingers." (after the noun).
- **Engine.** `StippleStamper` and `HatchStamper` port stampStipple and stampHatch with the lab's arithmetic (StrictMath
  sines): on the lab's own strokes the coverage is the lab's to the bit (pen stipple, even stipple, Build-up, fixed
  hatching), or within float rounding of the app's float distances (sponge, following hatching: sums within 0.002%).
  `ResolvedBrush.Pattern` holds the dials; the tip resolves as a round tip of its weight and its line and body
  (`LineTip`/`BodyTip` STIPPLE, HATCH) are laid by the pattern, stateful in `LineStamper.Meta` (a hatch's nearest-dab
  distances in a `FloatTiles`, dropped at finish). A pattern stroke is never on the lattice (dots and lines a pixel or
  two across). Marks go through `ModularCompose` and `ToneDocument`'s write path, so Erasable, Undo and masking fluid
  apply as to any stroke.
- **Hatch and the layer's coverage.** A pixel takes its nearest dab's lines, so its coverage can fall as the pen
  passes; a layer keeps the most coverage a gesture gave a pixel. As with Fast-wet's window, a live hatch shows the
  same tones as its quick render, and its layer's coverage is as when the stroke is composed once.
- **Quick then render** (the Manta, awake, `-e quick off`; asleep runs about 1.8 times slower): the Stipple pen waits 3,
  8 and 10 ms at the 95th percentile at 120, 240 and 360 px, so a stipple line draws live at every size
  (`QuickStroke.STIPPLE` 640 px of reach) and, its dots each laid once, doesn't count to the panel's reach either; the
  Hatch pen 4, 12, 17, 29 and 54 ms at 60, 80, 100, 120 and 160 px (after walking each row's disc a tile's run at a
  time; before, 25 and 43 at 100 and 120), so `HATCH` 125, and the panel's 36 px sends a hatch past it quick; a
  Stipple sponge's wet body follows Fast-wet's own 420. The quick preview shows a pattern's band as the wash's light
  dither. A 240 px hatch drawn quick waits about 1.4 s after lift for its render (the bench's 1.5 s stroke).
- **Rolls** from the seed after every other part (`ModularStyle.rollPattern`, the lab's odds and salts; Stipple, then
  Hatch); every other roll is as before; `ModularFeatures.seedOnly` keeps variations from swapping to them (and Float).
- **Words:** "Ink stipple.", "Sponged wash." (wash, opaque), "Ink hatching.", "Pencil hatching."; captions put
  "stipple" or "hatch" after the style.
- **Presets** (the lab's codes): Stipple pen and Hatch pen under Fineliner, Stipple sponge under Watercolor.
- **Brush Lab:** both are Tip chips and swap tiles, their cards drawing waves as every part's; nine Tip tiles on the
  Nomad in Basic are 69 dp, so a swap tile's caption insets 4 dp there (`LabLayout.swapInset`) to keep "Bristle" and
  "Stipple" whole at 12 sp.
- **Checks:** `StippleChecks`, `HatchChecks` (the lab's coverage, determinism, pressure ×2.9, passes, Build-up,
  hatching's bands 21 / 36 / 49% with one, two, three sets, crisp edges, fixed strokes lining up, quick == live,
  rolls at the lab's odds, codes, words); the web lab's self-check 7b (Build-up), sweep (1,080 combinations, every old
  one hash for hash as before), click-through 65/65; Brush Studio's 84.
- **Pictures** (the app's engine, not committed): `scratchpad/hatch-out/` 1-hatch.png, 2-stipple.png, 3-buildup.png,
  4-part-waves.png.

## Blend in the app (as built, 2026-10-16)

Branch `inkwash-blend`. Resist and Halo are still the web lab's only.

- **Dial and codes.** `PartDial.M_BLEND` (key `blend`; the enum name because gravity's Drying Blend is `BLEND`), last
  of every Medium choice after Erasable, `ADDED` (written only when not 0): the lab's presets and codes decode and write
  back byte for byte (`_000zz0000000000000001_` White gel pen, `_1ulat00770000ee2_` Wash behind, `_2zz00at2_`); old
  codes are untouched (LegacyConversionChecks; the bench's 210 page hashes the same). Options per choice
  (`PartChoice.optionsOf`, newmods.js `optLabel`): darken, white, behind, invert; Opaque's cover first. A medium chosen
  starts at its own way (as the lab's `switchChoice`: Blend isn't among what a swap keeps), so a variation that swaps the
  medium drops it; variations never nudge it (a choice dial).
- **Compose** (`ModularCompose`, the one write path through `ToneDocument`): each pixel's mark over white (`alone`, the
  Erasable spec's own darkness, now worked out for any Blend too) gives its darkness `a`.
  - **White**: `u + (255 − u) · a`, with coverage `c + (255 − c) · a` from the coverage found (`coverTones`): exactly
    white paint of strength `a` laid over the layer's pixel, so a white gel pen on the ink layer covers the wash under it
    and the layers below show through by `1 − a`. A White wash on the wash layer lightens the wash, never the ink above
    (decision 6's limit). A charcoal stick's line is worked out over white for it (its contact depends on what is under
    it), as the lab composes the mark over blank paper.
  - **Invert**: `u + (255 − V − u) · a`, the same coverage: paint of the opposite tone of what shows under it, `V`.
  - **Behind**: the medium's own tone and coverage scaled by `blank(V) = smoothstep(.55, .96, V / 255)`.
  - `V`, the page under the stroke as its gesture found it (`ModularCompose.Under`, read a 64 px tile at a time on first
    touch through the new `ToneDocument.baseComposite`): every visible layer from the bottom to the top of the stroke's
    pair for Behind (so a wash on the wash layer goes behind the pair's own ink and everything below, not only the pair
    as first proposed), to its own layer for Invert (what shows there; the ink above can't be inverted from below). It
    never changes during a gesture, so a quick stroke's render thread, a Slow lift's thread and every band read the same.
- **Erasable.** One rule, `ToneDocument`'s for any write: Behind keeps the medium's own darkness scaled by `blank`
  (`owns`); White and Invert pass no `owns`, so each pixel takes the rule for its change: darkened (Invert over paper)
  keeps its permanent share of the darkening; lightened (White) loses its permanent share of what went. So White ink
  (permanent) over permanent ink stays white when erased, over pencil leaves paper; Behind's wash under permanent ink
  keeps 60% (the wash's .4) and the ink every byte; Invert ink over paper stays black. Where the lab keeps an erasable
  white's kept tone darker than shown (chalk over permanent ink, erased, would show the ink again), the app can't: a
  pixel keeps a share of its own coverage at its own tone, so the eraser only takes paint away and never brings back
  what White covered (checked: nothing it leaves is darker than before).
- **Smudge**: the plane's tone and coverage are what the Blend meets (`smearTones`), as the lab. **Drying**: White and
  Invert carry no pigment: no Settle, held-back body, gravity or dust (`ResolvedBrush`); Behind's held-back pigment is
  scaled by `blank` at lift and its settling lands only by it (`DryingWater`'s `lands`, a share of each pixel's laying),
  so a Slow wash behind leaves every mark byte for byte after settling; gravity runs move it from where it landed.
  **Float**: Blend is the medium's own on water (`ResolvedBrush.blendMode` OWN) and greyed in Brush Lab.
- **Quick then render**: the render composes as live (`StrokeTones` now reads the coverages found too), so quick strokes
  end as drawn live, every layer (Fast-wet: tones, and layers as composed once). The preview (grey updates only; the
  bench's black-and-white panel path untouched) shows White and Invert as their flat tone over the page under them,
  covering (opaque, cancelled at lift as every preview), and Behind's dots only where the page is blank.
- **Rolls** from the seed after every part (`ModularStyle.rollBlend`, the lab's odds and salts: White, then Behind, then
  Invert; where the lab rolls Watercolor to Resist, not drawn yet, the medium stays without a Blend); every other roll
  as before (`blendRolls` off: 8,400 rolls identical but for Blend). A locked medium, or a locked Blend, keeps it.
- **Words** (newmods.js, two fixes there too): the Medium card's "White ink", "Chalk", "White pencil"; "Wash behind",
  "Paint behind"; "Invert ink" (the plain noun: "Wet wash behind" didn't fit the module's one line); "This brush" "White
  ink line.", "White chalk.", "White pencil" (the lab said "White hB graphite"), then "Goes behind marks." or "Inverts
  what's under it."; the lab's sentence split no longer cuts "6.7 s.". Captions tag white, behind or invert after the
  style.
- **Presets** (the lab's codes): White gel pen under Fineliner ("white on dark"), Wash behind under Watercolor ("behind
  marks"): 81 presets.
- **Brush Lab**: Blend's four options as segments under their label on the Manta; on the Nomad, where Ink's eleven dials
  wouldn't fit with them stacked, a menu button ("darken ▾") that opens the options as chips in the flyout's frame
  (`LabLayout.menu`). Greyed on Float. A White brush's samples (cards, tiles, loops) draw on a dark ground (68), as the
  lab's; the Medium card's wave crosses the grey band, so Behind stops at it and Invert flips it.
- **Brush Studio** rebuilt: of its 604 codes 505 now read and write back in the app (346 before; 159 differed only by
  Blend), 99 hold a choice the app doesn't have yet; its click-through 92 of 92.
- **Checks**: `BlendChecks`: codes; golden pages from the lab's engine (`scripts/blend_goldens.cjs`,
  `app/src/test/resources/blend`) for ink and opaque paint (white gel at Dark and Light, Invert ink, paint behind, white
  paint: at 16 greys ink 0.003–0.01% of pixels a grey off; paint 0.1–0.4% along its soft edge, which the app's paint body
  draws a pixel off the lab's); for a wash and charcoal, whose own marks the app draws its own way, the lab's rule on the
  app's own mark (to two grey steps); Behind leaving every mark byte for byte (Quick, Instant, Slow; wash and paint; and
  on blank paper the medium's own way, every byte); White over dark and nothing on paper; Invert twice; the eraser
  cases above; Smudge; Slow, Float; quick == live for six brushes × three dryings × two sizes, and the preview; several
  cores; rolls; words against the lab's; Brush Lab.
- **Speed** (the Manta, Fast-wet, over marks, compose ms a 1.5 s stroke; pen to screen p95): White gel pen 567 / 633 at
  120 / 360 px against plain ink's 521 / 593 (+9%; 7 and 31 ms against 6 and 29); Invert ink 635 / 733 (+22%; 8 and
  52 ms); Wash behind 805 / 874 against the wash's 668 / 757 (+20%; 28 and 78 ms against 20 and 58; a Watercolor wash
  past 36 px goes quick anyway). Desktop: the same order (+28% at 120 px for White, +26% for Behind).
- **Pictures** (the app's engine, 16 greys, not committed): `scratchpad/blend-out/` 1-wash-behind.png, 2-white-gel.png,
  3-invert.png, 4-medium-cards.png, 5-erase.png; `goldens/` each golden beside the lab's.

## Resist in the app (as built, 2026-10-18)

Branch `inkwash-resist`. Halo is still the web lab's only. Owner-approved: Resist is a Medium choice; masking fluid
shows as MonoPaint's hatch on screen only, nothing takes on it, the eraser lifts it first (no Peel button; Clear page
peels everything); wax stays for good as a faint one-grey sheen and catches only the paper's tooth.

- **Choice and codes.** `PartChoice.RESIST`, Medium 5 (key `resist`), its one dial Erasable (the macro, wax ↔ fluid;
  no Blend): masking fluid `_5_` (Erasable 1, the choice's real value, not written), wax `_500_`. The lab's presets and
  codes decode and write back byte for byte; old codes are untouched (LegacyConversionChecks; the bench's 210 page
  hashes the same). An app without Resist reads a code with it as nothing (a choice past the part's): CHANGELOG.
- **Resolve** (`ResolvedBrush.of`, newmods.js resolve): a Resist brush is laid as a crisp ink line (`asInk`: wet 0, pen
  line 1, no pooling, splatter or broken line) at its Erasable, without a smudge (it smears nothing; the lab now drops
  its carry too), on paper never on water (Float lays as Quick). Any tip: a stippled or hatched resist lays its dots or
  lines. Size and shade as ink (`ModularSizes.inks`): Watercolor's 120 px at Medium; the shade sets how strongly it
  shields (Light fluid keeps out about half of what comes over it, as the lab's).
- **Onto MonoPaint's masking fluid, no parallel plane.** `ToneDocument`'s mask layer (TSM4) already shields every
  write (`setPixel`, the block writes, `paintSpan`, the eraser), is hatched (`maskHatch`, `DrawingPad.drawMask`), undoes
  and is peeled by Clear page (`clearAllLayers`). A masking fluid stroke's gesture binds it (`beginFluid(1)`) and its
  compose (`ModularCompose`, the resist branch) writes each pixel's mark darkness over white, `a`, as the fluid's
  darkness, the more of what is there and `a` (newmods.js `max(rb, a)`), tone 0 at coverage `a`. **Wax** is the
  Erasable spec's share on that layer: `beginFluid(erasable)` gives the mask a permanent share (MonoPaint's own fluid
  tools still begin at 1), so the part wax lays is kept by the write rule (`own · (1 − e)` over what was kept; the lab's
  `rk` now adds the same way, `rkb + a·(1 − e)·(1 − rkb)`, at most the shield, so overlapping wax agrees too). Wax's
  share of `a` lands only on the paper's high points (`a ·= 1 − k + k · smoothstep(t0 ± .1, height)`, `t0 = .25 + .5 ·
  tooth`, `k = 1 − e`), and its **sheen**, `u · (1 − .07 · a · k)`, goes on the pair's ink layer: in the stroke's own
  gesture first, then the resist onto the mask in a gesture linked to it (`ModularStroke.layWax`, one Undo step), since
  the mask would shield the sheen. A masking fluid stroke is one gesture on the mask; a part-erasable resist is both.
- **What takes on it: nothing**, through the shield in every write, so no new code: washes (Quick's window, Slow's lift
  and settling ticks, gravity's runs and drips), ink, every Blend (White, Behind, Invert), Float's print, a smudge's
  lay-down. A smudge, a blending stump and a comb (Smear's Soften) pick up and blend nothing from under it either:
  `CarryPlane` keeps the shielded share of each pixel in place, as it keeps a permanent share, and both the trade and
  the stump work on the plane's loose part. The lab does the same (`shieldDab` around `carryDab` and `stumpDab`: within
  a dab's reach each shielded pixel shows only its loose part, the page over paper by `1 − rp`, and what changes there
  lands by `1 − rp`; its check 6b). Checked byte for byte on every layer at
  every fully masked pixel. A Float bath prints before a resist on Float is laid (`WatercolorSession.settleBefore`).
- **The eraser lifts it first** (`EraseBothStroke`): where a pass meets fluid that can lift (more than a grey step, the
  lab's .004), the pass lifts it and leaves the paint under it (the ink isn't erased there, nor the wash at lift); the
  fluid is lifted at lift, a mask gesture between the ink's and the wash's, all one Undo step. The next pass erases as
  usual. Wax is the share that can't lift: the eraser's block write on the mask leaves it, and it shields the paint
  under it from the eraser as from paint. The eraser previews what lies beneath again once a page has no fluid left
  (`hasMask`), not only once it has never had any.
- **The hatch shows only what lifts** (`ToneDocument.maskHatch`: the fluid's darkness less its kept part, at least 32,
  the lab's `hatchAt`): wax is never hatched; lifted, the hatch goes. Screen only: exports, the drawing's PNG and its
  tones never show it. Brush Lab's test page now draws it too (`DrawingPad.drawMask`, shared). Fluid over wax that
  already shields in full adds nothing (it stays wax, unhatched), as in the lab; over wax on cold press it fills the
  grain and is hatched there.
- **Files.** The mask persisted in .mpaint already (TSM4: its tones and coverage). Wax's share is a new TSM5 section,
  `MSHR` (the mask's shares, a byte a pixel), written only when the fluid keeps some; a page with masking fluid only
  still writes TSM4 byte for byte. Read back, the eraser lifts the fluid and leaves the wax. A TSM5 reader from before
  skips `MSHR` and reads wax as masking fluid; older ones refuse TSM5, as with Erasable.
- **Quick then render** (grey panel updates only; the bench's black-and-white path untouched): a resist's render
  composes on the page at finish, as a smudge's (wax writes two layers there); its preview binds the mask for masking
  fluid, so the stroke shows hatched under the pen (flat, the footprint at the shade's strength), and wax's preview is
  its flat sheen. Quick strokes end byte for byte as drawn live (fluid, wax on cold press, wax on smooth paper, at 60
  and 160 px), and a Slow resist lifted on its own thread as one lifted on the pen's.
- **Undo, Clear page:** one step takes back a resist (wax with its sheen) and an eraser stroke (the fluid it lifted
  with the paint); Clear page peels every resist, Undo puts it back. Clear current layer leaves the fluid. Brush Lab's
  test page keeps it when laid out again (`InkWashLayers.resized`), and its Clear peels it.
- **Rolls** from the seed after every part (`ModularStyle.rollBlend`, the lab's salt and odds): Watercolor one in 60,
  masking fluid seven times in ten, else wax; Blend gives way to it; `resistRolls` off (and `blendRolls` off) every roll
  as before; variations never swap to it (`ModularFeatures.seedOnly`).
- **Words** (the lab's): the Medium card "Masking fluid" / "Wax resist"; "This brush" "Masking fluid. Nothing takes on
  it until rubbed off.", "Wax resist on cold-press paper. Nothing takes on it. Dry-brush texture."; captions tag
  "resist" or "wax" first after the style ("Watercolour, resist"); no medium tag and no Erasable word (it is in the
  name). The Medium module's "inside" words are unchanged.
- **Presets** (the lab's codes): Masking fluid ("saves whites") and Wax resist ("grainy resist", on cold press) under
  Watercolor: 83 presets.
- **Samples** (Brush Lab's Medium card wave and swap tiles, the loop, the Tools page and slot tiles): a resist shows by
  what refuses to take: no band under it, and a wash of the band's grey (150) over the whole sample once laid
  (`WildSample.washedOver`; newmods.js `washOver`, the same rule), so masking fluid reads as a white hatched wave and
  wax as a white speckled one.
- **Brush Studio** rebuilt from the lab's sources (after the stump's merge): of its 604 codes 533 read and write back
  in the app; 71 hold Halo. Its check 115 of 115; the web lab's click-through 65 of 65, the sweep's 1,080 combinations
  all drawing, its self-checks all ok.
- **Checks**: `ResistChecks`: codes and resolve; golden pages from the lab's engine (`scripts/resist_goldens.cjs`,
  `app/src/test/resources/resist`: masking fluid over marks shown hatched, under ink, Light fluid under ink, wax on
  smooth paper shown and under ink; at 16 greys, of 172,800 pixels, masking fluid's 0 one grey off (2 hatch pixels
  further), wax's 2 and 14 one grey off along its soft edge); for wax on cold press, whose grain the app draws from its own paper, the lab's rule on the
  app's own paper (no wax below the paper's middle height; it catches 54% of the band where fluid fills it); a wash
  (Quick, Slow settling, dripping on a tilted page) over fluid and wax, White, Behind, Invert and Sumi over fluid, each
  leaving every fully masked pixel byte for byte; a smear, Blend stump and Wash comb picking up nothing from under
  fluid; the eraser's two passes,
  wax kept, a wash taking after the lift, a half-erasable resist; the hatch; files; Undo; Clear page; quick == live and
  the hatched preview; several cores; rolls; words against the lab's; Brush Lab. Existing checks taught Resist where
  they sweep every medium (a resist marks the mask: `GravityChecks`, `CarrySmudgeChecks`, the lab's sweep's
  `resistMarked`), skip it for Blend (`BlendChecks`), and count the presets (`ToolPresetsChecks`).
- **Speed** (the Manta was not connected: desktop, this Mac, ×20 for the Manta, a 1,600 px wave on a Manta-sized
  page): a masking fluid line at 120 px costs a plain ink line's live (13.8 ms against 14.7 for the whole stroke); quick,
  its compose at lift is 2.5 ms at 120 px and 8.3 at 360 (about 50 and 170 ms on the Manta); the Wax resist preset (cold
  press, dry brush) 62 ms live against the same line as plain ink's 36 (the resist's compose onto the mask at lift, with
  the grain, about 20), and 23.5 ms at lift drawn quick (about 0.5 s on the Manta). A wash over a page with fluid costs
  what it does on a blank one (32.5 against 36.6 ms; ink 6.1 against 6.3): the shield was already in every write. The
  eraser's lift check: 1.9 ms a stroke against 1.2. Memory: the mask layer is 9.8 MB once a page has a resist
  (upstream's), 4.9 MB more with wax's share; the eraser's lifted pixels are sparse tiles.
- **Pictures** (the app's engine, 16 greys, the fluid hatched as on screen; not committed): `scratchpad/resist-out/`
  1-resist.png (fluid lines and rings and a wax band on cold press; under a dark wash; the fluid rubbed off along a line
  and a ring: white in the wash, wax speckled and kept), 2-eraser-two-passes.png, 3-dark-light-wax.png,
  4-quick-preview-then-render.png, 5-medium-cards-and-loops.png; `pairs/` each golden beside the lab's.
- **Device checks for Elling:** Masking fluid, a dark wash over it, the small eraser along the fluid (lifts it, then
  erases); Wax resist on cold press under a wash, then the eraser over it (stays); the hatch under the pen while drawing
  a big masking fluid stroke (quick); Clear page; a saved drawing with wax reopened.

## Halo in the app (as built, 2026-10-18), shown as Mold

Branch `inkwash-halo`. The owner's decision: "Halo grows until another brush sets it, with limits", and its display
name is **Mold** (US spelling, his; the key `halo`, its codes and every style name unchanged; the web lab and Brush
Studio say Mold too). Drying time at most 20 s and Reach at most about three tip widths.

- **Choice and codes.** `PartChoice.HALO`, Drying 4 (key `halo`, label Mold), dials Drying time (default .55, 6.6 s;
  kept across a swap), `H_REACH` (Reach, the macro, close ↔ far) and `GROWTH`: the lab's `_4jsjsll_`. Brush Studio's
  604 codes all read and write back now (533 before; the 71 left were Halo). Old codes untouched (LegacyConversionChecks,
  the bench's page hashes).
- **Resolve** (`ResolvedBrush.of`, newmods.js resolve): Slow's lift with no settling of its own (`asSlow`: Settle 0, so
  no `DryingWater`, nothing runs), then `ResolvedBrush.Halo`: the lab's Reach `round((12 + 68·reach) · clamp(√(w/6), 1,
  3))`, `w` the line's size or the body's, **capped at `round(3w)`**; ticks `max(3, round(wetMs · 10 / 1000))`, **at most
  200** (the lab's 20 s already); darkness the line's `inkK` or the wash's `1.5 · washK`; Behind's flag. White and
  Invert grow none (veins only darken), nor a resist (the lab's grows none: its mark isn't on the page).
- **Mismatches with the owner's numbers.** The lab's default drying is 6.6 s (`.55`), not about 8 s: kept, as asked.
  20 s is the lab's top. The three-width cap binds below about 39 px at the default Reach (a 6 px line reaches 18 px,
  the lab's 46), below 80 px at Reach 1, and never for the lab's Reach 0. The golden script applies the app's caps to
  the lab.
- **`HaloGrowth`**, newmods.js Halo to the bit: its float32 arrays as floats worked in doubles (the diffusion's sum of
  unrounded doubles), StrictMath's sines, cosines, atan2 and pow (fdlibm, as V8's), V8's `hypot`, mulberry32 from
  `pen.seed ^ hashPts ^ 0x51a3e01d`. A lab quirk is kept: a pixel black before and after the stroke counts as its own
  (`1 − 0 / max(1, 0)` is 1), so existing black marks in its box are home to its scouts rather than food.
  Storage is the app's: only page-aligned 32 px tiles within Reach of a seed's tile (21 bytes a pixel), the chamfer a
  box row at a time; a band over 2^19 px trims Reach (a big wash: 138 → 96 px at 120 px, 240 → 62 at 240 px and Reach
  1, since the band holds the stroke itself). A tick's diffusion shares tile rows over the cores (their sums added in
  order: the same on any number of cores, within a float32 rounding of the lab's single sum).
- **Reading the page apart** (`Lift`, `Lift.read`): at the stroke's finish only its layer's Undo captures are kept
  (`ToneDocument.foundTiles`, references). The page under the box (composites before and after, `foundComposite`) is
  read once, on the cores: by the worker right after the lift, or by a Halo pen-down that comes first. The rest of the
  build and every tick read nothing on the page, so a joining stroke never waits for a build or a tick, and lifts queue.
- **Session** (`WatercolorSession`, beside the Slow wash and the bath; one of the three wet at a time): halos are built
  and stepped on the `SettleWorker`, written on the page's thread (`HaloGrowth.apply`: each pixel the lab's float page
  times the factor, the float kept so ticks never drift; one gesture as erasable as the stroke, a continuation, so Undo
  takes the last stroke back with the veins since). A Halo stroke on their layer joins them (its mark feeds theirs);
  anything else sets them where they are: another stroke (a Halo one on the pair's other layer too), the eraser, Undo,
  a layer change, a pause, the wet indicator's tap. The droplet shows while they grow; ticks are paced by `SettlePacer`.
  Without a session (samples, pictures) a halo grows to the end at once.
- **Masking fluid** shields veins as any write; Erasable keeps or lets go their share as their stroke's; files keep
  them (they are the layer's paint); quick then render ends as live and grows the same veins.
- **Rolls** (`rollHalo`, the lab's salt 0x4a10): Anything goes and Ink wash 1 in 30, Fineliner 1 in 50, never from
  Float; Reach and Growth .3–.8; `haloRolls` off every roll as before; seed-only, never a variation.
- **Words** (the lab's): "Mold 6.6 s", "mold 6.6 s", "Veins bridge nearby marks for 6.6 s.", caption tag "mold".
  Preset **Mold ink** under Ink wash (the lab's Halo ink, Press dark; its code `mold-ink.M…` by the display name): 87.
- **Cost, desktop (this Mac), a page-long wave on 1920 × 2560:** Mold ink 20 px: build 13 ms (its read 4 ms), band
  252k px, 10 MB, a tick 1.8 ms on four cores (3.4 one); a 120 px Mold wash: build 31–37 ms (read 5), band 522k px,
  13.7 MB, a tick 2.1–2.5 ms (5 one). The page's write of a tick 0.1–0.2 ms. **The Manta at ×20:** builds 0.25–0.75 s
  on the worker, never waited for; the read 70–100 ms, waited for only by a Halo pen-down within that time of a lift;
  a tick 35–50 ms on four cores, a third to half of its 100 ms; 2–4 ms of the page's thread a tick.
- **Checks:** `HaloChecks`. The lab's goldens (`scripts/halo_goldens.cjs`, `app/src/test/resources/halo`: Halo ink
  12 px and 6 px beside bars, the preset along the wave over lines, a grey band and hatching, a 40 px Halo wash, two
  lines, the second laid after the first grew 20 ticks): grown here from the lab's pages, 0 pixels a tone off for
  three, the wash 18 (the lab's page holds fractions) and the pair 7; drawn by the app, the bars and the pair the lab's
  exactly at 16 greys, the wave and wash about as many veins (the app's strokes there are a tone or so off the lab's).
  Then codes, the set and join rules, Undo, the eraser and Erasable, masking fluid, the worker, cores, quick == live,
  files, the limits, timing, rolls, the lab's words for 119 brushes (116 word for word; bristle tips and Anything goes
  round tips are worded the app's own way already), the preset and its sample.


## Mold darker and faster (the owner, 2026-10-22, branch `rules-mold`)

The owner: "mold is very faint / light and slow". Found: the lab reads a pixel black before and after the stroke as the
stroke's own (`1 − a / max(1, b)` is 1 at `b = 0`), so a black line beside a Mold stroke was a seed, never food: no
scout was fed, no bridge trail laid, and the veins stopped at FAINT, .2 of the stroke's darkness. Mold only bridged to
grey marks.

- **Black marks feed it** (`HaloGrowth.OWN_BLACK`): black before and after counts as the stroke's own only within 2 px
  of a pixel the stroke darkened (where it crossed a black mark); further off it is food.
- **Four scout moves a tick** (`HaloGrowth.MOVES`, the lab's two): veins reach a mark and darken in about half the time.
  Ten ticks a second and the presents' pacing as before; a tick 3.8 ms on the desktop (HaloChecks timing, 20 px; budget 20 ms).
- **Mold ink**: Mold Growth 1 (was .6), Drying .494, 5.5 s (was .55, 6.6 s): `_4hsjszz_`. Reach stays (three tip widths
  caps it at every usual size).
- `HaloGrowth.lab` keeps the lab's arithmetic for the checks' port and golden pages (still tone for tone).

Measured (Mold ink at Dark beside a black Ink line, the gap 2 × size + 8 px; visible: 30 px three greys darker):

| size | | darkest vein | px 3+ greys darker | visible after | done after |
|---|---|---|---|---|---|
| 6 px | before | 205 (20%) | 0 | never | 6.6 s |
| 6 px | after | 76 (70%) | 619 | 1.8 s | 5.5 s |
| 12 px | before | 204 (20%) | 350 | 2.6 s | 6.6 s |
| 12 px | after | 76 (70%) | 2028 | 0.8 s | 5.5 s |

Beside a grey mark (Ink at .7), which fed it before too: visible 3.6 → 1.8 s (6 px), 1.6 → 0.8 s (12 px), darkest the same
(81%). No preset but Mold ink uses Mold, so every other preset draws byte for byte; rolled Mold brushes grow the new way.
