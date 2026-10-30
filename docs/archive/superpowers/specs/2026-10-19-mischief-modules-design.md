# Mischief modules: Screen, Tone, Flick, Fling, Fading, Lemon juice, Bleed-through, Carbon

Approved by the owner on 2026-10-19 after trying the web prototype
(`docs/archive/brush-lab/prototypes/mischief-lab.html`, artifact JLXGU4UZN364S6nZBtQJXv): "i love all of those, i think
they're perfect". The owner renamed Lemon to **Lemon juice**.

The app is "a hand writing and drawing/doodling journaling app that is fun and useful but occasionally slightly
mischievous". These modules are where the mischief lives. They are opt-in: Randomize gives each one rarely, and no
Basic tool uses them unless the owner picks one.

## Where each goes (the six parts: Tip · Medium · Paper · Drying · Control · Smudge)

| Module | Part | Shape | Keys (never renamed) |
|---|---|---|---|
| Screen | Medium | a dial like Blend: none, halftone, line screen, dither, grain; with Cell, Angle, and the newsprint dials Misregister, Ink gain, Uneven | `screen`, `cell`, `sangle`, `mis`, `gain`, `uneven` |
| Tone (screentone) | Medium | its own choice: scribble an area to lay a pattern fill (dots, lines, sand, gradient) at a Tone; the eraser scrapes it | `tone`; dials `pattern`, `ttone`, plus Cell and Angle |
| Flick | Tip | a dial: how far the stroke carries on past a moving lift, tapering dry, 150–300 ms | `flick` |
| Fling | Medium | a dial beside Splatter: drops thrown forward on a hard stop, size and reach from the speed | `fling` |
| Fading | Drying | a choice: the mark fades away, thin parts first, over 10 s – 2 min (Fades in) | `fade`; dial `fadeIn` |
| Lemon juice | Drying | a choice: goes on as a faint trace, darkens in unevenly over 1–10 s (Dries in) | `lemon`; dial `dryIn` |
| Bleed-through / Carbon | Medium | a dial: off, bleed-through (mirrored, feathered, soaks more where slow or held), carbon (right way round, grainy, harder pressure transfers more); with Strength | `through`, `tstrength` |

Share codes: every new dial is appended after the existing ones, written only when not its default, so every
existing code is byte for byte as before (as Blend and Erasable did). New choices take new choice indexes at the end
of their part's list.

## Rules shared by all

- **Rolls:** rare, decided from the brush's seed with each module's own salt after every part (the Halo/Mold
  pattern in `ModularStyle`), so every other roll comes out exactly as before. A check per module counts its odds and
  shows the rolls without it are unchanged (`ROLLS_BEFORE` and friends).
- **Words:** each says what it is in the brush's caption and in the lab's module text (docs/TEXT-STYLE.md: plain).
- **Web twins:** the Studio and the web lab get the same dials, codes and rolls (newmods.js pattern), and the
  `java-codes` check stays green.
- **Erasable:** a screened or toned mark erases as its medium does; Tone always erases (it's a scrape).
- **Page shade:** each works on a shaded page (`PageShade`, the `PAPR` section): screens threshold against the paper,
  Tone lays black dots on any shade, fading returns to the shade.
- **Speed:** nothing new runs on a stroke that doesn't use the module. A brush with all of them off draws exactly as
  now (goldens, bench hashes, `LegacyConversionChecks` unchanged).

## Stage 1: Screen and Tone (first)

- Screen applies when the stroke's tones reach the page: the brush lays its greys as usual into its own coverage, and
  the write turns them into black or white by a per-pixel threshold fixed to the page (so overlapping strokes line up),
  as `mischief-lab.html`'s `buildScreen` and `bit1` do. Ink gain and Uneven are the prototype's second pass.
- Misregister lays the faint second pass at an offset (0–8 px at the prototype's scale; scale to the Manta's dpi).
- **Speed experiment:** a screened mark is pure black and white. Try drawing it through the panel's fast
  black-and-white update while the pen is down, and report whether it is WYSIWYG at full speed on the Manta (bench
  build first, then the owner).
- Tone: a medium that stores a fill coverage; what shows is the pattern at Tone over that coverage. Pen marks on the
  same page stay solid under it. The eraser scrapes in streaks along its stroke (prototype `dab1`, scrape branch).

### Stage 1 as built (2026-10-19, branch `inkwash-screen`)

- **Codes.** Screen is the six dials `screen cell sangle mis gain uneven` after Blend on Ink, Wash, Opaque, Charcoal and
  Graphite (Resist has none); Tone is Medium choice 6 with `pattern ttone cell sangle`. All are `ADDED`: a fresh Tone
  writes `_6_`, a halftone ink adds Blend's `0` and `1` to its medium segment, and every old code is byte for byte.
  Cell 3–16 px (default 6, about .5 mm on the Manta's 300 ppi: the prototype's pixels are taken one to one), Angle
  0–90° (45), Misregister 0–8 px (the faint pass at .8 and .6 of it), Ink gain and Uneven 0–1, Tone .05–1 (.45).
- **The write.** `PrintScreen` holds the threshold; `ModularCompose` runs the mark's own darkness over white through it
  as each pixel is written: black (White's white, Invert's opposite), or the page as the gesture found it (opaque paint
  lays white by its coverage). A shaded page shows black dots on its shade. Misregister composes each line and body
  buffer with its faint second pass (the buffer again at the offset, .45 of its strength). Ink gain and Uneven are
  worked out at each pixel from its neighbours' thresholds and the paper's soft noise (`PaperNoise.low`). A screened
  mark doesn't settle, run, drip, shed dust or grow Mold: nothing lays a grey after it. On water a Screen does nothing.
- **Tone** is laid as Resist is (a crisp ink line, no smudge, never on water) on the ink layer, 40 px in every style; its
  dots are tone 1 at coverage 254 (black on any shade, and no other brush lays it), so the eraser knows them: each pass
  takes a dot by its streak across the stroke, 1.4 px wide, about one in five on a light streak to nine in ten on a
  heavy one, afresh each pass. Its gradient runs down the page.
- **Rolls** (`rollTone`, then `rollScreen`, after the rake): Screen 1 in 25 for Anything goes, Marker, Ink wash and
  Charcoal, 1 in 40 for Fineliner and Pencil, never the rest (Watercolor included); any print alike, a cell of 4–10 px,
  45° one in two; Misregister one in four, Ink gain and Uneven one in three. Tone 1 in 40, Anything goes only. Salts
  `0x5c4ee`, `0x70e5`. Variations never nudge Screen's dials or swap to Tone.
- **Brush Lab.** Screen is a menu button (five options); its own dials show only with a screen, Cell and Angle greyed
  where the print has none (dither; grain has no angle; Tone's sand neither). The Nomad's open card has no room for
  Screen's own dials beside a medium's, and none for Screen beside Ink's eleven: there it is set by rolls, codes or the
  Manta.
- **Speed experiment:** `docs/archive/notes/2026-10-19-screen-fast-panel.md`; the bench's `StrokePanel.bench = "screen"`.

### Stage 1, after the owner's first test (2026-10-19, branch `inkwash-screen2`)

The owner: the selector "opens at the top of the section but should be a flyout panel"; "not clear how to adjust the
screen stuff" in Control; "screen tones always seem consistent, not lighter and darker or cumulative like in the demo";
"very fast"; "pen lift there are some changes"; "is screen the right term for this? maybe print shading". Then picked
the names: **Print shading** (none, halftone, engraving, dither, grain), **Screentone** with its **Darkness**.

- **Why the tones were flat.** The threshold read the mark's own darkness over white. Grey and pressure did reach it
  (a 53% grey ink line gave 56% dots; Pressure dark at .2 against .9, 29% against 94%), but a second stroke over the
  first laid the same dots again, never more, while unscreened it darkens (53% to 78%); and at the default black swatch
  an ink line's darkness is 1, solid black, with pressure changing it only through Control's Pressure dark (off on most
  styles). A wash's body gives about a fifth dots at black, whatever the pressure, as its glaze does unscreened.
- **The print tone.** Each layer keeps an optional print tone (`ToneDocument.Layer.print`, made by the first screened
  stroke on the layer, as Erasable's share is): the tone its screened marks would show unscreened. A screened stroke
  builds on it as transparent paint darkens (`PrintScreen.printed`: `v × (1 − a)`; opaque paint moves it toward its own
  by its coverage) and its screen inks that, so the dots are the grey swatch × coverage × Control's darkness cells, and a
  second stroke prints darker (53% grey twice: 84% dots for 78% grey). The eraser lowers it with the coverage it takes,
  Undo and Redo restore it with the tile, masking fluid keeps it out, a file keeps it (TSM5's `PRNT` section, written
  only when some pixel holds some: a page without a screened mark, or with one undone, writes byte for byte as before).
  Per layer, not per page, so it goes with its layer's erasing, Undo and restacking. White, Invert and Screentone
  still work from the mark over white. A brush without a Print shading never touches it.
- **Two prints on one layer.** Each stroke inks only its own new dots, by its screen, from the print tone both built;
  the earlier dots stay (a dither over a halftone keeps the halftone's dots and adds its own), as overprinting does.
- **Control.** A brush that prints dots says what its darkness cells do: "pressure grows dots", "tilt shrinks dots",
  and its grid's Dark column reads Dots. Every cell still does something, so none is greyed. Pressure → Cell was left
  out: the cell is the page's, so two strokes land on the same dots and a second builds on the first; a cell that changed
  with pressure would break both, and pressure already sizes the dots through Pressure dark.
- **At lift.** A live Instant or Quick screened stroke ends as drawn. A quick stroke (a big wash) previews flat and lands
  the textured dots at lift: its transparent body now previews at three quarters of its glaze
  (`QuickStroke.SCREENED_BODY`), so about 23% of the final dots change at lift, from 29% (halftone, dither and gain
  washes, 120 and 240 px, black and grey); the rest is the body's soft edge and streaks. An ink line changes 2%, 4% with
  Misregister, whose ghost the preview doesn't draw. A live Slow wash previews Drying's flat grey and settles at lift
  (about 29% of its dots change); previewing the true glaze instead gave 25-26%, not worth a second path in the compose.
- **The selector.** Every choice dial shown as a menu button (Print shading's, Blend's on the Nomad) opens its chips in a
  panel beside the button, just right of the column, as a part's swap panel opens beside its module.

### Print, after the owner's test of 2026-10-22 (branch `print-tissue`)

The owner: "pressure changes with the print brushes: the ben day one, it doesn't seem to blend between, it seems to
jump between shades. most noticeable on ben day", and "preview for the multi-shade print patterns can be less dark to
match the actual typical output".

- **Why Ben-Day jumped.** Not the pressure (the pen's .05 to .45 is read as travel 0 to 1 continuously, a screened
  stroke's darkness follows it, and Halftone's and Rosette's thresholds were already smooth): Ben-Day's threshold was
  each pixel's printer's tint (`TINTS`: 10, 20, 30, 50, 70%, solid), so its dots only grew when the tone crossed a tint
  and held still between. A pressure ramp (pen .05 to .45, 60 px, black) gave four plateaus (22, 34, 54, 74% ink),
  steps of 17%. Now each pixel's threshold is the share of the page its site's dot covers when it reaches it
  (`BENDAY_SHARE`, measured at class load as before, solid at .997): the dots grow smoothly, still all one size at one
  tone, crisp black, the darker of two strokes kept. The same ramp: 28% to 85%, no step over 7% between 37 px bins
  (as Halftone's 8%). Same Kind, codes and thresholds kept a stroke (bit for bit as worked out).
- **Cards at a usual pressure.** A print whose shade follows pressure (Print Shading, not Screentone, with Press → darker
  or Both: Ben-Day brush, Rosette ink, Halftone brush, Engraving pen, Wavy-line pen, Dither pencil) shows its loop and
  tray stroke pressed no firmer than travel .6 (`WildSample.TYPICAL_PRESS`, match-lab's "usual pressure"), not the
  loop's firm .9: the densest patch of Ben-Day's card 75% → 54% (a stroke at .6 prints 59%), Halftone's 83% → 62% (70%),
  Rosette's 67% → 42% (48%). Every other brush's samples are as before.

### Previews at a usual pressure, and crayon's (branch `previews-typical`, 2026-10-22)

The owner: "preview marks.... like the print versions, the preview marks are much darker than the typical line left at
normal pressure... can you investigate and adjust the preview a bit", "ie. crayon", "yes the preview for crayon is
pretty poor if we could look at that".

- **Measured.** For every Basic tool: the ink within .35 of the mark's width of its centre line, on the Tools page card
  (its firm last third), the bar row and the lab loop, against a real stroke at Medium at travel .6 (and .5). Before,
  17 brushes' cards were over 1.3 times as dark as the real stroke: Messy stick 2.55x, Dark stick 2.31x, Loose hatch
  2.21x, 5B pencil 2.03x, Wax crayon 2.01x, HB pencil 1.71x, Hand and Neat hatch ~1.65x, Stipple sponge 1.67x, B pencil,
  Charcoal dust, Sketch pencil, Soft charcoal, Flat dry ~1.4x; washes ~1.2x. The cause is nearly all pressure: the
  tray stroke ends at travel .95 and the loop at .9; a real stroke drawn at the card's smaller size was within ~10% of
  one at Medium, so size isn't it, and the tray stroke doesn't cross itself.
- **Fixed for all.** Every sample presses no firmer than `TYPICAL_PRESS` (.6), as the pressure-shaded prints did: over
  1.3x went from 17 brushes to 4 (5B pencil 1.43x, Stipple sponge 1.48x, Rosette ink 1.36x, a thin line's ratio noisy
  at this measure; Loose hatch now 0.46x, light but its lines read). The median card/real ratio 1.04 → 1.00. Hand's
  ramp and Drying's lift stay as they were (they show what pressure and lift do). Brushes that need a ground (Stump,
  Comb, Clear water, Damp tissue, Lemon juice's darkening) are not compared.
- **Crayon.** Its card was solid black (firm wax fills the valleys) and its line a ninth of the tile, so the 3 px tooth
  read as salt: now at .6 its card is the broken, toothy wax a stroke lays (0.99 → 0.46 against a real 0.49), a fifth of
  the tile (about its Medium on a card). Crayon scratch drew a thin smooth line on the White brushes' flat dark band; it
  now scrapes a ground of its own crayon (Scratch off, four firm rows at .8), its line .3 of the broader size, so the
  crumbled wax shows round it.
- **Crayon while drawing.** Wax crayon goes quick past ~40 px (Larger, Large). Its quick preview was the sticks' flat
  grey (speed plan F5), the same dark band at any pressure. Now its line's whole ink goes on the tooth's peaks the
  pen's pressure reaches (`QuickStroke.TOOTH`, `Crayon.tooth` ≥ 1.1 − travel, as `Crayon.wax` takes the tooth past
  1.08 − press): at 60 px its inked pixels are within 13% of the final's from travel .6 to .9 and 85–94% of them the
  final's own (at .4 it shows ~1.8 times the final's few). The final is the stroke's own, byte for byte.
- Pictures: the session's scratchpad `previews/` (cards-before/after, crayon-closeup, crayon-live-preview).

### Charcoal's preview (branch `pencil-tilt`, 2026-10-22)

The owner: "charcoal preview is also darker than the resulting mark..".

- **Measured.** Every Charcoal and Pastels stick at 16, 60 and 120 px, travel .4, .6 and .9: the quick preview under the
  pen (no trail) against the finished stroke, dark panel pixels over the stroke's middle. Before, the preview was the
  sticks' flat grey (speed plan F5) at any pressure: at .4 Messy stick 2.6–3.5x the final's, Dark stick 2.8–3.2x,
  Charcoal dust 2.4–2.9x, Soft charcoal 2.0x; at .6 1.4–2.3x; at .9 0.84–1.12x (hard pastels a little light). Cards
  were already right (0.80–1.06 of a real stroke at .6).
- **Fixed.** The preview lays the final's own contact rule at the stick's core, by the pen's pressure now, the stick's
  grain and the paper's: 0.88–1.30x the final's dark pixels at every size and pressure (most within 10%), 76–100% of them
  the final's own. Its edge is the nib's, not the stick's fuzz. The final is the stroke's own, byte for byte.
- Pictures: `pencil/charcoal-preview-before.png`, `pencil/charcoal-preview-after.png` (the session's scratchpad).

### Pencils shade when tilted (branch `pencil-tilt`, 2026-10-22)

The owner: "all of the pencil presets... let's use the tilt so when the pencil is tilted it shades like an angled
pencil".

- **What pencils had.** Their rolled Control (Custom) held Tilt Size about .49 and Tilt Dark about .46: from 5° of lean
  a little wider and lighter, linearly, so the natural hold was already a touch wider, and laid low the 4 px line only
  reached about 7 px: no shading. No existing control gave a soft edge or a wide grainy side.
- **Tilt Shading**, Control's new last dial (any nib line; on the pencils at 1): through 35° from upright nothing
  changes (the Manta reports degrees, X and Y lean, `docs/TILT_INPUT.md`; a writing hold of 30–45° keeps the crisp
  line); from 35° to 65° it eases in (smoothstep): up to six times as wide, a quarter lighter, its edge soft over half
  its radius, so graphite's contact catches only the paper's tooth. Measured at Medium (4 px), travel .6: 0° and 30°
  the same as upright before; 50°: 13–15 rows wide; 70°: 15–20 rows of grainy grey, lighter (HB pencil: 4 → 15 → 19
  rows). 5B pencil, on laid paper, stays streaky.
- Pictures: `pencil/pencil-tilt-before.png`, `pencil/pencil-tilt-after.png` (the session's scratchpad).

## Stage 2: Flick and Fling

- Flick: on a lift while still moving (last ~30 ms faster than a threshold), the stroke carries on along its last
  heading and turn, eased out, tapering and drying, length `carry × speed`, at most ~260 px at the prototype's scale.
  Drawn as a short continuation of the same stroke, so it is one Undo step with it.
- Fling: a hard stop (speed above ~1 px/ms in the last 140 ms, then still for 45 ms, or a lift right after a dead
  stop) pools a little ink and throws drops ahead, nearest landing first. Seeded from the stroke, so a redraw matches.
- Both draw within ~300 ms of the lift: WYSIWYG.

### Stage 2 as built (2026-10-19, branch `inkwash-flick`)

- **Codes.** Flick is the last dial of every Tip choice (key `flick`), Fling the last of every medium with Blend (key
  `fling`, after Print Shading's dials; not Resist or Screentone, which lay plain ink). Both `ADDED`, 0 to 1, off by
  default: every old code and drawing is byte for byte. A tip or medium swapped keeps them (`BrushPart.keeps`).
- **Scale.** `mischief-lab.html`'s pad is CSS px (1/96 in); the Manta's page px is 1/300 in, so the prototype's lengths
  and speeds are taken 3 times (`FlickFling.PROTO`), as the hand's on the glass (`StrokePoints.zoom`): zoomed in, the
  same hand throws as far on the glass and a shorter way on the page. Times are the prototype's.
- **Flick.** At the lift, if the pen moved within the last 40 ms and went faster than 1.05 px/ms over its last 30 ms,
  the stroke carries on for Flick × that speed × 85 px on the glass (at most 780, none under 9), along its heading at
  the lift (its last 9 px: the 30 ms chord lags a curve) and turning as the 30 ms before turned (at most .004 rad a
  px), eased out over 150 to 300 ms of the samples' clock. These are the stroke's own samples (`FlickFling.carry`,
  1.5 px apart, pressure falling to nothing), fed through `ModularStroke.add` at `lastSamples`, so every tip, medium,
  drying, Print Shading, page shade, Erasable, a quick stroke's render and a Slow lift's taper lay them as the stroke's
  own, in its Undo step and in each mirrored replay. The stampers narrow what lies past the lift as `(1 − u)^.75`
  (`StrokePoints.flickWidth`: the line, placed lines and their body, a round body, a hake's head; bristles, stipple
  and hatch by pressure alone) and the line runs dry from 30% of the way, .8 of its bristles at the end
  (`flickDry`). Drawn at once at the lift (no animation): WYSIWYG within the lift's own time.
- **Fling.** The pen's rest is where it stays within 4 glass px (a stylus keeps reporting, a hair apart); a stop is
  hard when the pen went faster than 3 px/ms (over about 20 ms) in the 140 ms before it. Held 45 ms with the pen down,
  it throws then; a lift within 45 ms throws at the lift; one fling a stroke, and none after a moving lift. The pool, an
  ink disc 1.3 half-widths across, half a half-width past the stop (a body's 1.05 and .3), lands at once; then Fling ×
  (1.5 + 2.2 × the speed) drops (the speed in prototype px/ms, at most 5) within about ±13° of the last 40 ms' heading,
  ellipses 1.5:1 along their flight, 40% with a speck three radii beyond, sized from the prototype's by the brush (its
  half-width over the prototype's 3.2 px, past 6.4 px by the root), each landing 30 ms + its flight after the throw,
  nearest first, as the samples' clock reaches it (the pad's `HeldPen` sends a resting pen's time, as for Stipple's
  Build-up: `buildsWhileHeld`), the rest at the lift. Seeded by the brush's seed and the samples up to the stop, so a
  redraw lands the same; a reflection lands its own draws. The drops are the line's coverage (else the body's, at its
  amount; Fast-wet's as laid and as shown), composed as the medium, and laid again after a Slow lift's re-lay.
- **Rolls** (`rollFlick`, `rollFling`, after Clear Water's; salts `0xf11c4`, `0xf1196`): Flick 1 in 12 for Brush pen,
  Ink wash and Anything goes, 1 in 20 for Watercolor, .35 to .85; Fling 1 in 15 for Dip pen, Brush pen and Anything
  goes, .3 to .8. A variation never turns either on.
- **Words.** "This brush", after what it is: "Flicks on a quick lift.", "Flings drops on a hard stop." (both: one
  sentence). The Medium card "Ink, fling" where its line has room; the Tip card has none, so the Tip's and Medium's
  modules list them ("shape · taper · angle · flick", "ink · paint · charcoal · pencil · fling"). The Nomad's open Medium
  card shows Fling's row where it fits after Print Shading's (not beside Ink's). No Basic tool uses them (this spec:
  the owner picks one).
- **Web twins.** The web lab's engine draws both (newmods.js: the stroke's samples past the lift, the drops into its
  buffers, the same rules), with the same dials, codes, rolls and words; Bristle's Paint Load (the app's) is a hidden
  dial there now so the codes line up. Brush Studio rebuilt.
- **Speed.** Nothing runs for a brush with both off: no watcher (`FlickFling.of` is null), and the stampers' checks
  are one comparison of a dab against a flag. Goldens, bench hashes and `LegacyConversionChecks` unchanged.
- **Checks** `FlickFlingChecks`; pictures `scripts/flickfling_pictures.sh`.

## Stage 3: Fading and Lemon juice

- Both keep, per mark, when it was laid. What shows is worked out from its age, stepped (the prototype's 4 a second;
  on e-ink, as coarse as looks fine, about once a second or slower for Fading), with a partial refresh of the area.
- Fading: when a mark has faded completely it is gone from the page (and the file). Undo during the fade brings
  nothing back once gone; that's the point. A page closed mid-fade keeps fading by the clock when reopened.
- Lemon juice: after its time it's an ordinary dried mark.

### Stage 3 as built (2026-10-19, branch `inkwash-fade`)

- **Codes.** Fading is Drying choice 5 (`fade`, its one dial **Fades In**, `fadeIn`, 10 to 120 s, 20), Lemon Juice choice 6
  (`lemon`, **Dries In**, `dryIn`, 1 to 10 s, 4): a fresh one writes `5` or `6` and its dial; every older code is byte for
  byte. Both are laid as Quick lays it (`ResolvedBrush.asQuick`), with a `FadingInk.Spec`. Seed-only: a variation never
  swaps to one.
- **The mark.** At lift (`ModularStroke.finish`) the stroke's Undo steps become one mark a layer (`ToneDocument.fadeSince`):
  its tiles as they were before it and as laid, and when (wall time). The cheapest record that can put the page back: one
  birth time a mark, and each pixel's fade weight worked out from its two states (how much the stroke darkened it, as
  the prototype's `dark`). Each pixel shows a share of its change by its age (`FadingInk.share`, the prototype's
  `shade3`): Fading's life is Fades In × (.35 to 1 by its weight, so thin parts go first) × (.75 to 1.25 by a mottle fixed
  to the page: the paper's 16 px blob, .7, and a pixel of grit, .3), held full for 55% of it then thinning to nothing;
  Lemon Juice from 26/255 (about 10%) to all of it over Dries In × (.7 to 1.3 by the mottle), eased, then exactly as
  Quick lays it. Only pixels still showing what the mark last showed are written, so ink drawn over a fading mark stays
  where it lies. Fresh fading ink over a fading mark changes from what lay under that one (`rewet`), so both go together.
- **The clock and the file.** TSM5's `FADE` section keeps the live marks (layer, kind, time, born, the time its pixels
  show, the tiles), written only while some: a gone mark leaves the page and its file byte for byte as never drawn on
  (`FadingChecks`). A page shown (turned to, opened) is first stepped to now (`DrawingPad.fadeToNow`), so one closed
  mid-fade is as far along as the clock says, or gone; a lemon mark closed half way is dark. A page never opened again
  keeps its mark in the notebook's file, and its thumbnail shows it as saved, until it is.
- **Undo.** Each step rewrites every Undo and Redo step's copy of the mark on its layer too, so Undo takes the stroke
  and Redo gives it back as the clock has it, never fresh; once gone, the stroke's step changes nothing and leaves the
  history (a step linked to it takes its link). Layers restacked keep fading; a page grown or Keep Reflection makes new
  layers, which keep the marks as they are then (the old layers, which an Undo brings back, go on fading by the clock).
- **E-ink.** `DryingTicker` steps a page's marks when due: Lemon Juice once a second, Fading a tenth of Fades In, 2 to 5 s
  (a 20 s fade is 13 steps); each mark is rendered and shown on its own partial refresh of what it changed, and the
  pen-away refresh follows when one ends. Never while a stroke is down or the page is wet (a wash, a halo or a float
  reads the page as it left it); nothing is posted with no mark, and nothing ticks while paused (`pauseDrying`). A
  page-wide 160 px fading wash steps in about 25 ms on the desktop.
- **Live.** The stroke is drawn live as Quick draws it; Lemon Juice turns to its faint trace at the lift (the faint trace
  is what the page holds from then). A sample (no session: Brush Lab's tiles) shows the stroke as laid, once.
- **Rolls** (`rollFade`, last; salts `0xfade5`, `0x1e307`): Fading 1 in 40 for Anything goes (Fades In 15 to 60 s from the
  seed), else Lemon Juice 1 in 40 for Anything goes and 1 in 60 for Ink wash (Dries In 2 to 6 s), never from Float or Mold;
  the roll-freezing lists have `fadeRolls` and `lemonRolls`.
- **Words.** "This brush" in the drying's place: "Fades away in 20 s.", "Darkens in as it dries."; the Drying card
  "Fading 20 s" and "Lemon juice" (its time would pass the module's line); the swap tile's note "fades 20 s", "darkens
  4.0 s"; captions tag "fading" and "lemon juice" after the style, as Mold's. A rolled one is named fade-plum or
  juice-fig (`names/traits.txt`: lemon is a produce word).
- **Web twins.** `newmods.js`: the same choices, codes, rolls and words; the web lab's test area keeps each mark and a
  timer steps it (`fadeTicker`, never while the pen is down), so the fade and the darkening show there; a sample tile and
  Brush Studio's pages show the stroke as laid. `modular-lab.html` patched with the same hunks, Brush Studio rebuilt
  (`java-codes`: 606 of 606).
- **Checks** `FadingChecks`; the web lab's `fadeChecks`.

## Stage 4: Bleed-through and Carbon (notebook)

- The ghost is never saved into the next page. The next page's composite draws it from the page before: bleed-through
  mirrored left to right, carbon the right way round, at Strength, from the previous page's marks laid by brushes
  with `through` on (a per-page "soak" layer stored with the page, optional section).
- Thumbnails and the PDF show it. A loose single page has no next page: nothing to do.

## Not in scope

- A seventh part. Screen is a Medium dial, as Blend is.
- Fading on the Brush Lab's sample loop beyond showing it once.
