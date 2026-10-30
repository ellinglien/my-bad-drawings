# Wet paint: MonoPaint's wet canvas for Gouache and Oils (2026-10-22, design as built)

Branch `wet-paint` (from `inkwash-v2-lab` `46a7a05`). Builds Option A of
`2026-10-22-monopaint-paint-proposal.md` with the owner's decisions recorded there (section 9).

**The owner's ask:** wet flow, opaque wet paint (mixing; oils re-dip and get dirtier as they go; clear water lifts wet or
dry paint), responsiveness, simplicity (one Stays Wet dial, plus Runs Downhill). Watercolor, Ink wash and the ink and
line tools unchanged.

## 1. What the owner sees

- **Gouache** and **Oils** on the Tools page are new, all Wet (the old twelve are retired, below).
  - Gouache: Wet gouache (*mixes wet*), Runny gouache (*runs downhill*, Runs Downhill .6), Wet flat (*flat, wet*),
    Gouache hake (*broad, wet*), Tacky gouache (*barely mixes*, Stays Wet .15), Gouache water (*lifts paint*: Clear
    Water).
  - Oils: Wet oils (*drags, mixes*), Loaded oils (*runs out*: Paint Load, rub a grey to load), Flat oils, Broad oils
    (*flat/broad, drags*), Tilt oils (*tilt to widen*), Oil glaze (*thin, mixes*, Opacity .55).
  - Each follows the old preset it replaces (tip and Control), on smooth paper, no Impasto; Oils with Drag at Own Paint 1.
- **Under the pen** the stroke shows as it lands, each pixel once, final: no flat preview, no render after.
- **After the lift** it softens into the wet paint around it for 1 to 6 s (Stays Wet), and runs downhill on a tilted
  tablet (Runs Downhill). A light stroke over wet dark paint mixes with it; on blank paper it stays the grey it was laid
  (it mixes only with paint: the owner, 2026-10-22).
- **Dried paint** (after the droplet's tap) wets again under Gouache, which mixes into it; under Oils it stays dry: an
  oil stroke covers it unmixed, and it doesn't move (the owner, 2026-10-22).
- **A new install's slot 6** starts with Wet gouache (was a dry Gouache, `ToolSlots.OLD_GOUACHE`, still known to the
  Erasable migration).
- **It stays wet** through ink, pencil and eraser strokes. The droplet at the foot of the bar shows while it is wet; a
  tap dries it. Leaving the page or the notebook, changing the layer pair or the layers, a pause, or a timed page's lock
  dries it too. Drying lays nothing (the page already shows the paint); it only stops it mixing as wet paint.
- **Oils** pick up what they cross (blank paper doesn't count) and lay it, so they get dirtier as they go and clean up
  again over 40 px; each stroke starts dipped in its grey. Loaded oils runs out and then only smears (MonoPaint's oil
  paint mode).
- **Gouache water** lifts wet or dry paint and lets it run through its water; it lays nothing on blank paper.
- **Pastels** over wet paint sink into it (their marks become wet paint and move with it); a **Dry brush** drags it along
  its path. Over a page with no wet paint both draw exactly as before.
- **Undo** takes back a Wet stroke with its movement (one step); the paint then rests where it is, so Redo gives the
  stroke back as it ended.
- **Brush Lab:** Drying's eighth choice **Wet**, dials **Stays Wet** and Runs Downhill. "This brush": "Stays wet until
  dried, runs when tilted."

## 2. Engine

| Piece | What |
|---|---|
| `PartChoice.WET_PAINT` | Life index 7, key `wetpaint`, label Wet, appended: no older code holds it. Seed-only (no roll, no variation swap). |
| `PartDial.STAYS_WET` | 0–1, .5; in `ModularCode.ADDED` (written only off its default). With `RUNS`. |
| `ResolvedBrush.Wet` | For Opaque, or Clear Water; `rewets` false for an Oils brush: Stays Wet → MonoPaint's wetness 15–85 (`setWetness`: how much of the paint under new paint mixes into it, 9–51%, and how freely it evens out) and 8–40 frames a touch moves (`setFrames`; 1.2–6 s at 150 ms). The engine lays Wet as Instant (`asInstant`) wherever there is no wet canvas: samples and tiles, Copy Strokes' copies, Wet on another medium. |
| `WetPaintStroke` | `QuickStroke.start` makes it for a Wet brush with a session. Its footprint (`FootprintSweep`, the quick preview's sweep moved out of `QuickStroke` byte for byte: the stampers' own geometry) is laid into the canvas at each present (`WetWatercolor.paintMask`; touched pixels never again). Drag/Smear without Paint Load: a paint-only pick-up (`PaintLoad`'s 40 px trade), 1 − .3 × Pick Up Paint of its own grey kept. Paint Load: `PaintLoad` (pick-up, deposit) driven by `LoadedPaint`'s share (`PaintLoad.left`). Clear Water: `waterMask(flowing)` from a `BrushInk` rinsed each stroke, pull .5 + .5 × Pick Up Paint. One gesture (Erasable and kind of its medium) on the pair's wash half. |
| `WetCanvas` | The session's fourth wet thing (beside a Slow wash, a Float bath and Halos): one `WetWatercolor(document, wetness)` (whole canvas) on one layer. `step`: a slice on the drying ticker, sized by MonoPaint's `WetWorkBudget` (idle 5 ms), frames every `FRAME_MS`, gravity = the session's `PageTilt` × Runs Downhill. `catchUp`: when the page's revision isn't the one it left, `rewet` takes what something else changed as its paint and the paint rests. `join`: Pastels and Dry brush. |
| `WatercolorSession` | `wetCanvas`, `settleBefore` leaves it, `settleNow` dries it (`settleOthers` is the old `settleNow`), `undo` rests it, `tick` steps it once nothing else is wet, `wet()` includes it, `moving()` (fading marks wait for it) doesn't when it rests, `wantsTilt` while it runs. |
| `WetWatercolor` (upstream) | Hooks marked "Inkwash": `gesture(erasable, kinds)`, `setFrames`, `rest`, `rewet`, `finishStroke(withPrevious)`, `paintOnly(paintOnly, dryStays)` (new paint mixes only with pixels that hold paint, a `Tile.paint` bit: laid by a stroke, or taken up holding coverage on the layer; with `dryStays`, paint taken up from the page is held stuck beneath, dry, and opaque paint covers it). Upstream behaviour unchanged without them. MIT, © mpdairy (this fork's LICENSE). |

**Undo.** The stroke is a gesture of its own; every slice after it finishes as a continuation of it
(`finishContinuation`), so the stroke and its movement are one step. Anything else that changes the page rests the
paint before it would move again, so no slice ever joins another stroke's step; Undo rests it outright, keeping Redo.
A dry brush's drag is laid as a continuation of its stroke.

**Layers and rules.** It paints the pair's wash half (Opaque's half) and reads and writes only that layer (it selects it
for each slice and puts the active layer back). Masking fluid shields it as every write (`ToneDocument`); its pixels
carry the medium's Erasable and kind (paint), its slices too (`gesture`). Locked pages and sections, No Erasing and One
Brush refuse strokes as ever; a timed page's lock dries it.

**Recovery.** The page shows the paint as it goes, so autosave saves it as it is; killed while wet, it reopens dry.

**Pastels and Dry brush** (`WatercolorSession.joinsWet`, `lifted`): a stroke of a Pastels or Dry brush style brush on
the pair whose wash half has wet paint is drawn on that layer (its own half otherwise, as ever); at the lift
(`ModularStroke.finish`, `QuickStroke.develop`) the session is told; at the next step its marks over wet tiles become
wet paint and move (`rewet(wake)`), and a dry brush's samples are swept as MonoPaint's dry brush pen's water
(`waterMask`, not flowing: it picks up the wet paint it crosses and carries it on), a 4 ms slice at a time.

## 3. Draw once (the experiment)

The owner's "one write per pixel", switchable, **off by default**: Pastels and Dry brush strokes are drawn quick with
no flat preview, Quick drying as Instant, so the true stroke is written behind the pen as the render composes it
(Trail), each pixel once, final. Their look at the end is their Instant stroke's (checked); what changes is how they
show while drawn: nothing near the pen until the render's next compose (100 ms), no second update.

Switch (kept until switched again):

```
adb shell am start -S -n ing.ell.mybaddrawings/io.github.mpdairy.monopaint.PaintActivity --ez drawOnce true
adb shell am start -S -n ing.ell.mybaddrawings/io.github.mpdairy.monopaint.PaintActivity --ez drawOnce false
```

## 4. Speed

Inkwash's budget is ≤ 0.1 ms a sample on this Mac; the Manta is about 20 times slower.

**Pen, the proposal's harness** (1400 × 900 wave over marks, 3 samples an event, mean / p95 ms a sample):

| Brush | 60 px | 120 px | 240 px |
|---|---|---|---|
| Wet gouache | .019 / .024 | .026 / .034 | .046 / .065 |
| Gouache hake | .020 / .024 | .031 / .041 | .052 / .073 |
| Gouache water | .022 / .027 | .038 / .047 | .083 / .095 |
| Wet oils | .023 / .029 | .039 / .051 | .065 / .087 |
| Loaded oils | .016 / .026 | .022 / .037 | .038 / .070 |
| *old* Opaque round (live) | .027 / .061 | .039 / .078 | .048 / .102 |
| *old* Impasto (live) | .056 / .123 | .104 / .199 | .119 / .395 |
| *old* Oil paint (live) | .135 / .310 | .242 / .335 | .345 / .672 |

`scripts/bench_desktop.sh wet 60,120,240` (ModularPerfBench, a 1.5 s real-time S-curve on a full Manta page over marks):
mean ms a sample .02–.04 at 60–120 px, .07–.08 at 240 (Wet gouache, Runny gouache, Gouache water, Gouache hake), .10
Loaded oils, .12 Wet oils (its pick-up; was .18 before reading it every other pixel). Under the budget everywhere but
the dragging oils at 240 px.

**The movement after the lift** (same bench, unpaced compute, whole stroke): 160–450 ms of desktop work at 60–120 px,
450–840 ms at 240 px, over 21–42 frames; slices of about .3–.9 ms here (64 tiles, MonoPaint's cap). On the Manta that
is several seconds of the spare cores' time while the pen is up; it never runs while the pen is down (the drying
ticker waits), and backs off after slow input as Slow washes do (`SettlePacer`).

**Draw once** (Pastels and Dry brush presets, 60–240 px): the pen thread's cost drops from .01–.03 ms a sample (the
preview) to ~0; the render thread's work is the same.

**Device bench** (not run: the owner has the Manta):

```
cd inkwash-wet  # a worktree
KEEP=1 scripts/bench_device.sh -e wetPaint true -e sizes 60,120,240
# just two presets: -e names "Wet gouache,Wet oils"; Draw once on Pastels and Dry brush:
NOBUILD=1 scripts/bench_device.sh -e drawOnce true -e quick rule -e styles "@Soft pastel,@Dry round" -e sizes 60,120,240
scripts/bench_device.sh --uninstall
```

The script builds `-Pbench :app:assembleBench :app:assembleBenchAndroidTest`, installs app id
`ing.ell.mybaddrawings.bench` beside the owner's app, and runs
`adb shell am instrument -w -e modularPerf true -e wetPaint true -e sizes 60,120,240 ing.ell.mybaddrawings.bench.test/io.github.mpdairy.monopaint.WidthInstrumentation`,
printing the InkBench lines (each preset's pen row, then "flow: slices, frames | slice ms mean p95 max | work ms").

## 5. Checks

`WetPaintChecks` (in `scripts/test_inkwash.sh`): 30 presets without Wet, every style (the old Gouache and Oils too), hash
byte for byte as before Wet at 60 and 200 px, mid-stroke and settled; each pixel written once under the pen; mixing;
movement then rest; stays wet across ink, pencil and the eraser; the droplet's tap dries, laying nothing; one Undo step,
Redo kept; downhill; clear water lifts wet and dry paint, lays nothing on blank paper; oils get dirtier; masking fluid;
Erasable and kind; the wash half only; dark stays dark on paper; Gouache mixes into dried paint, Oils cover it and it
stays put; pastel sinks, dry brush drags (one Undo step); Draw once. Also
`ModularCodeChecks.wet` (index 7, codes unchanged), `PartModelChecks`, `ToolPresetsChecks` (Gouache and Oils only Wet,
seeds, the retired twelve).

## 6. Not mirrored in the web lab and Brush Studio

`parts.js` and Brush Studio don't know Wet: a Wet code pasted there doesn't read (its Life digit 7 is past their
list). Not ported: the wet model is MonoPaint's Java, and the lab would draw it as plain Opaque at best. The names'
`traits.txt` has no `life.wetpaint` (it must equal the web lab's). Cheap next step if wanted: append `wetpaint` to
parts.js's Life list, drawn as Instant.

## 7. Not done, and risks

- **Not done:** paper tooth and Dry Brush gaps in wet paint (laid solid, as MonoPaint's), Impasto (none on the Wet
  presets), Gradient along a Wet stroke, Flick, Copy Strokes' copies as wet paint (they are laid as Instant opaque, and
  the copies rest the paint).
- **Flow on the Manta** is the main unknown: several seconds of spare-core time after a big stroke; untested on device.
- **Mixing with paper:** MonoPaint mixes new paint with white paper too (its default lightens black by 39%); here it
  mixes only with paint (the owner's answer). Wet paint still spreads a soft edge into the paper around it.
- **"Paint" is coverage on the layer:** white paint already laid counts as paint, blank paper doesn't; a pixel the flow
  spread into from paper isn't paint to the next stroke.
- **Any other change to the wet layer rests the paint** (it stays wet and mixes, but stops moving until the next Wet
  stroke), so an ink stroke drawn within the few seconds a Wet stroke moves cuts that movement short. So does selecting
  a layer, which the page counts as a change.
- **Pastels over wet paint** draw on the wash half while any of the pair is wet (not only where they cross it): over
  dry paper there they look the same, but sit under the pair's ink.
- **Old slot brushes** named like the retired presets now show as brushes of their own (by design).

## 8. Wet brush: MonoPaint's wet brush pen on the wet canvas (2026-10-22, branch `wet-brush`)

**The owner's decision (2026-10-22, "yep go for it"):** rebuild Brush pen's **Wet brush** on the wet canvas, as a wet
*ink* brush like MonoPaint 0.98.3's wet brush pen, instead of the speed plan's options for the old one (its quick render
was 228 ms desktop at 240 px, about 4.5 s on the Manta).

**What the owner sees and does.**
- **Wet brush** (Brush pen, "ink in water") lays ink in water into the wet paint. Under the pen: final, one write a pixel.
- Its brush **holds its ink between strokes**: an inky brush lets ink out where it goes (strokes get paler along the
  way and from stroke to stroke), a clean one soaks up the wet ink it crosses (MonoPaint's `BrushInk`, untinted).
- **Dipping: a tap on a grey** (the grey strip, or a grey in the flyout; a rub too) dips the selected tool's brush: its
  next stroke starts full of the grey it draws with. A brush never dipped starts dipped. This is MonoPaint's "tap a
  colour to dip" on Inkwash's greys, per slot (each slot's brush holds its own ink, as Paint Load's does). Kept between
  launches (§8.1), as MonoPaint keeps it. "This brush": "Holds ink between strokes. Tap a grey to dip."
- It **lifts dry paint at once** (any dried paint on the wash half, gouache or ink), its ink runs through its water and
  **pools downhill** on a tilted tablet (Runs Downhill .4), and it stays wet until the droplet's tap or leaving the page,
  with the rest of the wet paint (Stays Wet .5).
- The rules of section 1 hold: it mixes only with paint (dark stays dark on fresh paper), masking fluid shields it, One
  Undo takes back a stroke and its movement, No Erasing / Lock rules and layers as every Wet stroke.
- **The old Wet brush** (Fast-wet line and wash) is retired (`ToolPresets.RETIRED`): saved slots and library brushes
  keep it, shown as a brush of its own; its share code decodes as before. Its engine path (Ink with Quick drying) stays
  in Brush Lab.

**Engine.**
- `ResolvedBrush.Wet.inks`: Wet on **Ink or Wash** without Clear Water (before: laid as Instant, wet null). Opaque,
  Clear Water and every other medium as before.
- `WetPaintStroke` ink mode: `WetWatercolor.waterMask(mask, ..., INK_PULL .6, held, INK_TRADE)` (flowing water, MonoPaint's
  strength 60). The footprint reaches each pixel once, so each new pixel trades the ink of the ~4 dabs MonoPaint's pen
  (spaced .4 of a radius) would rub it with, in one trade (`INK_TRADE` ≈ 3.3, by pressure since §8.1; four separate rubs cost .04 ms a sample
  more at 240 px for the same result).
- Capacity: `BrushInk.CAPACITY` (3000 px of black) up to MonoPaint's pen size (48 px), by area beyond
  (`WetPaintStroke.inkCapacity`): a broad brush runs dry over as many of its widths as the pen does.
- `HeldPaint.wetInk/keepWetInk/dip` per holder; `InkWash.dip` from the bar's grey tap and rub, Brush Lab's greys
  (holder `ANY`).
- Medium **Wash** (the preset): the wet paint is on the pair's wash half (`ModularStroke.layerOf`), so the ink mixes with
  wet gouache and pen lines on the ink half stay crisp above it. Wet on Ink works the same on the ink half (one wet
  canvas at a time: a Wet stroke on the other half dries the first).
- Upstream hooks (marked "Inkwash"): `BrushInk(dryPaintSticks, capacity)`, `WetWatercolor.waterMask(..., trade)`.

**Speed** (desktop, `scripts/bench_desktop.sh wet 60,120,240`, mean / p95 ms a sample; budget .1):

| | 60 px | 120 px | 240 px |
|---|---|---|---|
| Wet brush (new, final under the pen) | .02 / .03 | .03 / .05 | .08 / .15 |
| old Wet brush, engine live (`bench_desktop.sh <its code> 60,120,240`) | .22 / .51 | .29 / .75 | .30 / 1.13 |

The old one draws quick in the app (render at lift 228 ms desktop at 240 px, ~4.5 s Manta); the new one has no render.
Its movement after lift: 600–970 slices of ~.4 ms (p95 .45–.9), 240–400 ms of desktop work over 20–40 frames, on spare
cores while the pen is up.

**Device bench** (not run; the owner's call):

```
cd inkwash-wetbrush  # a worktree
KEEP=1 scripts/bench_device.sh -e wetPaint true -e names "Wet brush" -e sizes 60,120,240
# the old one, engine only, for comparison (as speed plan §5.3):
NOBUILD=1 scripts/bench_device.sh -e styles "wet-brush.M10i0d2o54w00at_09ls2009s000000000_0_100_1jz_8yrfo00000000ny0000nz0.16ebhgk" -e sizes 60,120,240 -e quick off -e marks true
scripts/bench_device.sh --uninstall
```

**Checks:** `WetPaintChecks.wetBrush` (once a pixel under the pen; on the wash half; holds and lets out its ink, the next
stroke paler; a dip refills it; a clean brush soaks up wet ink; lifts dried paint; dark stays dark on paper; runs
downhill; one Undo step, Redo kept; masking fluid), `ToolPresetsChecks` (Wet brush Wet ink on a Wash, its seed its
code's, the old one retired under the same name). Non-Wet pages byte for byte as before (the BEFORE table).

**Open** (answered in §8.1): strength (a full black dip laid about a 30% grey on fresh paper, MonoPaint's rate);
whether the held ink should survive an app restart (MonoPaint saves it).

### 8.1 Owner feedback, 2026-10-22 (branch `wet-fixes`)

**The owner's words:** "gouache - can't preview in the test area? no marks anywhere"; on strength, "we can have a wider
range, darker"; on keeping the brush's ink across a restart, "yes".

**Test area** (Tools page and Brush Lab, `TestArea` / `BrushTestPad`): Wet strokes already ran the test page's own wet
canvas (`InkWash.stroke` with the test area's `WatercolorSession`); on a desktop run and on an emulator (Android drawing,
not the Manta's direct e-ink path) Wet gouache, Wet oils and the Wet brush all paint, mix and settle there. What did go
blank: the test page's Wet brush (holder `ANY`, shared by every brush tried there) ran dry and the Tools page has no
greys to dip it, so later strokes laid nothing, and a light slot grey (Gouache's slot is Lighter) runs dry sooner. Now:
- another brush on the test page starts dipped; a Wet brush run under a quarter of its grey's ink dips itself again;
- **Clear** dries the wet paint (the droplet goes) and dips the brush;
- leaving the test area (its screen put away: back to the drawing, Brush Lab opened) dries what is wet there.
Not reproduced: a Wet gouache stroke laying nothing at all on the Manta (its direct e-ink path); if it still does after
this, it is there to look at, on the device.
Card and slot samples (`WildSample`, no session) draw Wet brushes as Instant, as before: they show, without moving.

**Strength by pressure** (`WetPaintStroke.inkTrade`, `INK_LIGHT` .2, `INK_FULL` .92): a fresh-paper pixel takes from .2
of the brush's shade at the lightest touch to .92 at full pressure (squared curve), the trade otherwise MonoPaint's
(`BrushInk.trade`, pull .6). A full brush holds `INK_LOAD` 4 × MonoPaint's pen (`inkCapacity`), so a dark stroke still
runs dry over about the length the pen's paler one did. Black dip, 800 px strokes on fresh paper, tone at 10 / 160 /
400 / 760 px (desktop, `range` in `WetPaintChecks`):

| | stroke 1 | stroke 2 | stroke 3 |
|---|---|---|---|
| 20 px, light (travel .1) | 236 / 236 / 236 / 237 | 236 … 237 | 237 … 238 |
| 20 px, half (.5) | 159 / 163 / 170 / 179 | 181 … 196 | 197 … 209 |
| 20 px, full (1) | 35 / 74 / 130 / 182 | 192 … 234 | 237 … 249 |
| 40 px, full (1) | 52 / 120 / 192 / 235 | 241 … 254 | 254 … 255 |
| before: 20 px, full | 166 / – / 221 / 242 | 245 … 253 | 254 … 255 |
| before: 40 px, light | 167 / – / 189 / 205 | 209 … 229 | 231 … 241 |

(The before rows: 60 / – / 440 / 800 px, start to end of strokes 2 and 3. Before, every pressure laid about the same .4
of the shade: ~165 at the start.) A light touch now lays a pale grey and
barely empties the brush; full pressure starts near black and runs dry along the stroke.

**Ink kept between launches** (`HeldPaint.saveWetInkIn`, from `InkWash`): each tool's Wet brush ink is kept in the
preferences as `wet_ink_<holder>` = "fullness tint" (MonoPaint's `BrushInk` state; the tint is 0 here), read back at the
tool's next Wet stroke after a launch; a grey's tap (a dip) clears it, so the brush starts in its grey. The test page's
brush (`ANY`) isn't kept.

**Checks:** `WetPaintChecks.wetBrushRange` (full dip near black at full pressure, pale at a light touch, runs dry along
the stroke), `wetInkKept` (kept, read back, a dip clears it, `ANY` not kept); non-Wet pages byte for byte (the BEFORE
table), desktop checks all pass.

## 9. Wet watercolour: Watercolor on the wet canvas (2026-10-22, branch `watercolour-wet`)

**The owner's words (2026-10-22):** "watercolor marks... even though they are still wet painting a few strokes together
they don't behave like watercolor does, per se... the initial marks are still quite visible.." Choosing the wet canvas:
"i think maybe 1 ... since i think we can port over the dark edge, bleed and bloom .. ?" And earlier, of Inkwash's
watercolour: "i love the look of ours".

**What the owner sees.**
- The Watercolor presets **Soft wash, Wet blooms, Backrun, Granulating, Slow bloom, Pooling wash, Tilt blend, Sinking
  grain** are Wet now, under the same names and with the same tip, medium, paper and Control as before; only Drying
  changes (Wet: Stays Wet from their old drying time, Runs Downhill from their old runs or pooling). The old ones are
  retired (`ToolPresets.RETIRED`): a slot or a library brush holding one keeps it, and its code reads as before.
  **Drips** stayed a Slow wash (the wet canvas has no drip trails; retired later the same day, below), and the Watercolor tools that aren't washes (sponge,
  wash behind, comb, dirty and water brushes, masking fluid, wax, tissue) are unchanged. Ink wash is unchanged.
- Strokes painted while the paint is wet **merge** into one wash (wet into wet): their water joins and the pigment runs
  through it, so the first marks don't show as separate shapes. It stays wet until the droplet's tap or leaving the page,
  as every Wet paint.
- As a wash's water dries back (the seconds after the lift), its pigment **creeps to the drying edge** (a darker rim),
  **settles into the paper's tooth** (granulation, by the paper's Tooth) and, with Bleed, **feathers a little past the
  brush** into the paper around.
- A fresh, wetter stroke laid into or beside a wash that has gone damp **blooms**: its water pushes out into the damp
  wash, carrying pigment to a frilly dark front with a paler centre (a cauliflower or backrun).
- **Transparent:** a wash only darkens what is under it (glaze); dried paint under it stays put (a stroke after the
  droplet's tap glazes over the dried one). It lightens only by lifting (Gouache water, Water brush, Damp tissue).
- On a tilted tablet the wash runs and pools at its low edge (Runs Downhill), as wet gouache does.
- Under the pen: final, each pixel written once, as every Wet stroke. Everything above happens after the lift, in the
  wet canvas's timed slices on the spare cores, never while the pen is down.

**Which brushes.** A Wet brush on a **Wash** medium of the **Watercolor** style without Clear Water
(`ResolvedBrush.Wet.wash`). The Wet brush (Brush pen: Wet on a Wash) stays MonoPaint's wet brush pen, ink in water. As
with Oils' dried paint, the style decides (it is in every share code). No new dials and no new choices: **Medium Wash's
own dials drive it** (Dark Edge → the rim, Bloom and Backruns → blooms and their frills, Bleed → feathering, Wet → how
wet the water is laid), Paper's Tooth → granulation, Drying Wet's Stays Wet → how slowly the water dries back, Runs
Downhill → gravity. So share codes don't change at all; nothing new is rolled (Wet is never rolled; Erasable untouched).

**On the tile model** (as built; `WetWatercolor`, MonoPaint's whole canvas, Inkwash hooks `wash(Wash)` and
`washMask`, MIT, © mpdairy). Each wet pixel already has loose pigment, stuck pigment (dried paint beneath) and, if water,
its freshness (`pull`, fresh to .4 still) and mobility (pull³), through which pigment evens out with its water
neighbours along 1, 4 and 8 px rays, weighted downhill. Watercolour adds:
- **Laying** (`washMask`): each pixel the footprint newly reaches becomes flowing water (pull .45 + .4 × Wash's Wet, less
  where the paper's low noise says, by Backruns) and takes `(255 − ink) × grey × strength` more pigment, a glaze; the
  strength is 1 from a usual pressure up and about half at the lightest (`WetPaintStroke.washStrength`), so a wash at
  black lays near black. Paint dried on the page is held stuck beneath (`paintOnly(true, true)`, as Oils'), so the wash
  glazes over it and never lightens it.
- **Washes and wet into wet:** every water pixel belongs to a wash (`Tile.wave`). A stroke laid into water still fresher
  than `MERGES` (.45 since 2026-10-23; .55 before) joins that wash (`join`, a union of wash numbers), so the two are one
  wash with no edge between them; a stroke laid beside or into damp water is a wash of its own. Joined, their water
  combines (`COMBINE`, 2026-10-23): damper water beside a merged stroke's fresher water takes .7 of the difference each
  frame and joins it, a few px deep, and no pigment is carried through combined water, so the pigments even out across
  where the strokes meet instead of keeping the newer stroke's edge.
- **Dark edge:** each wash's edge is where its water meets paper or another wash's water (`edgeness`: the share of the
  pixels within 4 px that aren't its water, worked out again only where water changed). As the water dries back (from
  LOOSE .7 to STILL .4) its pigment shows gathered there (`look`): up to 1 + 3.25 × Dark Edge at the edge, a little paler
  (−.35 × Dark Edge) inside it. Water at the edge also dries back faster. (Built first as pigment moved pixel to pixel
  toward the edge; that left a one-pixel black line with a pale ring inside it and was unstable, so the rim is how the
  dried-back pigment shows, the rim's darkness the band's.)
- **Blooms:** fresh water beside damp water fresher by more than .15 floods it, a pixel a frame where the frill noise
  (paper fine grain and blob) lets it (more of it with more Bloom), and the flooded water joins the fresh wash; pigment
  moves from the fresher pixel of each pair to the drier (`capillary`, Bloom's, conserved). So a stroke into a damp wash
  pushes out a frilly front, and that front is a wash's edge: dark, paler behind it (a cauliflower). Within a stroke,
  Bloom and Backruns show as its water's unevenness drying back: a little mottling, and tide lines where the low noise
  crosses one level (Backruns).
- **Bleed:** fresh water wets blank paper beside it where the frill noise lets it (by Bleed), within the margin the
  canvas wets around a stroke, so a wash feathers a few px. (No preset has Bleed today.)
- **Granulation:** a wash's pigment shows × (1 + 1.2 × Tooth × (2 × valley − 1)) as it dries back, the paper's own grain.
- **Evening out:** the canvas's tile-scale evening out is off where washes are (`WASH_SPREAD` 0: a stroke keeps its
  own shading; its water still evens pixel by pixel), and its tile-scale fall downhill is .4 of MonoPaint's
  (`WASH_FALL`), so a tilted wash pools at its low edge without draining its top white.
All of it runs in the frames the wet canvas already runs after the lift (`WetCanvas.step`), its pixel work shared with
the spare cores (`edgeness`, then the frame's flow, then its settle, each reading only what the pass doesn't write; a
pair is worked out only when both tiles move in the frame, so pigment is conserved). Gouache, Oils and the Wet brush
take none of these paths (`wash` null): their pages hash as before (`WetPaintChecks.washOthers`).

**Cards and samples** (no session) lay a Wet brush as Instant, as before: the Wash's look (dark edge, blooms, grain)
shows there through its Instant wash.

**Speed** (desktop, `scripts/bench_desktop.sh wet 60,120,240 "Soft wash,Wet blooms,Backrun,Granulating,Slow bloom,Pooling
wash,Tilt blend,Sinking grain,Wet gouache"`; mean / p95 ms a sample under the pen; budget .1):

| | 60 px | 120 px | 240 px | flow after the lift (frames, desktop work) |
|---|---|---|---|---|
| wet watercolours (8) | .02 / .03 | .04–.05 / .06 | .10–.12 / .13–.17 | 22–75 frames; 500–760 ms at 60 px, 1.0–1.7 s at 120–240 px; slices ~.7–.9 ms |
| Wet gouache (same run) | .03 / .08 | .03 / .04 | .09 / .11 | 24 frames; 200–410 ms |

The pen is about Wet gouache's (at 240 px at or a little over the budget, as Wet gouache is). The flow after the lift is
2–4 times Wet gouache's (its frames run until the water dries back, 2 to 3 times as many, each doing more): on the Manta
that is perhaps 10–35 s of a spare core's time after a big stroke, in small slices that never run while the pen is down;
the look (rim, grain, blooms) comes up over it. Device bench (not run, the owner's call):

```
cd inkwash-wcolour  # a worktree
KEEP=1 scripts/bench_device.sh -e wetPaint true -e names "Soft wash,Wet blooms,Backrun,Slow bloom,Pooling wash,Wet gouache" -e sizes 60,120,240
scripts/bench_device.sh --uninstall
```

**Pictures:** `scripts/watercolour_pictures.sh <folder>`: each old preset (top) beside its wet one (bottom): one stroke,
two strokes wet into wet, a stroke into a nearly dry one, tilted half upright.

**Checks:** `WetPaintChecks` `washOnce` (each pixel once under the pen), `washMerges` (wet into wet: no edge where the
first ended; over a dried one, an edge), `washEdge` (rim darker than the middle once dried back, as laid even),
`washBlooms` (a stroke into a damp wash changes it more than into a dried one), `washGlazes` (over dried paint: darker,
never lighter), `washUndo` (one step, Redo kept), `washOthers` (Wet gouache, Wet oils, Wet brush hash as before);
`ToolPresetsChecks` (the eight Wet, their parts but Drying the old ones', the old ones retired under the same names);
`GreyBarChecks` (a wet wash at black, dried back first: near black, fewer greys than a Slow one, at least 80). The
checks of the Slow and Quick washes draw the retired ones (`FloatChecks.find`).

**Open:** the flow's cost on the Manta; the tilted wash's pooling strength (`WASH_FALL`) by eye.

**Wet into wet mixes more; Drips retired (2026-10-22, branch `watercolour-mix`).** The owner: "watercolor looks
great... for overlapping wet strokes, the color should mix a little bit more, especially early on in the stroke... as
if they are merged strokes. but they look awesome as they dry.. it's just a slight tweak i think". And: "i don't love
the drips... i think they are too heavy handed and just look weird. hard to get liquid flow just right with it."
- **Mixing** (`washMask`): laid into wash water still fresh, a stroke's pigment mixes into the loose pigment there
  instead of glazing over it, by `MIXES` (.8) × smoothstep of the water's freshness from `MIXES_FROM` (.45) to
  `LOOSE` (.7): at its freshest the overlap of the same grey is only a little darker than either (the new pixel takes
  .2 of the glaze plus .8 of `max(0, laid alone − loose there)`), so it reads as one wash, not a darker band. Its water
  mixes in too: it is made fresher only by the share that doesn't mix.
- **No line where merged strokes meet** (`capillary`): each stroke has its own wash number now (joined to the washes it
  merges into, as before), so where two merged strokes' water meets is known, and there no pigment is carried from the
  fresher water to the drier. Before, a second stroke into a wash half a second old pushed a thin dark line along its
  edge inside the first.
- Unchanged: a single stroke, a stroke into a nearly dry wash (blooms) and a tilted wash are byte for byte as before
  (`WatercolourPictures`' panels), as are the drying back's edge, grain, blooms and tide lines; Gouache, Oils and the
  Wet brush take none of it (`washOthers`). Pictures: `scripts/watercolour_pictures.sh <folder> mix`, two crossing
  strokes laid at once, after 10 and after 40 of the canvas's 50 ms flow frames (about .5 s and 2 s), and a stroke
  back over its own start (a stroke never lays a pixel twice, so this one is as before).
- **Drips** is off Basic tools (`ToolPresets.RETIRED`: a slot or library brush holding it keeps it, its code reads as
  before); Life's Drips dial stays in the Brush Lab. 116 presets, 22 retired; Watercolor's gravity presets are Pooling
  wash, Tilt blend and Sinking grain.

**Wet ink wash (2026-10-22, branch `inkwash-wet`).** The owner: "wet ink wash brush blending .. similar issue as the
watercolor where there should be a bit more mixing, less dark where they overlap, especially when wet". Ink wash's
washes kept only the latest stroke wet (Quick drying's window, or a Slow wash), so where two crossed they glazed into a
darker band.
- **Grey wash, Flat wash, Slow ink wash, Line and wash and Wet ink are Wet** under the same names, tips, media, papers
  and Control (Stays Wet .5; Slow ink wash's .8; Runs Downhill 0); the old ones retired (`ToolPresets.RETIRED`, codes as
  before), seeds their codes'. Sumi, Clear water and Comb (Float: the floating rings are the brush) and Mold ink (Halo)
  are unchanged: not washes.
- **Engine:** a wet wash (`ResolvedBrush.washes`) is a Watercolor Wash, as before, or an Ink wash style Wash or Ink with
  a body (Wet past .08): the watercolour wash of this section, so its strokes mix wet into wet (`MIXES`) and dry back
  as a watercolour's. All on the pair's **wash half** (`ModularStroke.layerOf`; an Ink wash Ink went on the ink half), so
  the five merge with each other and the watercolour washes. Tuned for ink (`Wet.wash`, `Wet.pull`): its water laid
  fresher (.6 + .25 × Wet, against .45 + .4 × Wet) so strokes stay loose longer to mix; Dark Edge and Bloom at .4 of a
  watercolour's (a soft rim, small blooms); its water feathering further into the paper by Bleed (`Wash.feather` .95
  against .85: a ragged few px, ink on rice paper); no grain (their papers have no Tooth). An Ink's body lays a third of
  its grey (`ModularSizes.GLAZE`, as an ink's body glazes) times its body share (`bodyAmt`), the pale wash the old ones
  laid about their line; a Wash's all of it, as before.
- **Pen line:** a wet wash with Pen Line past 0 (Line and wash, Wet ink) sweeps the line's own footprint too and lays it
  as dry ink held stuck beneath the water (`WetWatercolor.lineMask`): crisp, never moved by the flow, glazed by washes
  over it. It is on the wash half with its wash, not the ink half: a gesture binds one layer and a Wet stroke is one
  Undo step with its movement, so a line on the other half would need a second gesture under the pen. What shows is the
  same; the eraser and the halves' rules take it with the wash.
- **Slow ink wash** is paler than before (about 195 against 155 at a .7 grey): its old Slow drying pooled its body
  darker; the wet one lays an ink's third.
- Watercolour, Gouache, Oils and the Wet brush draw byte for byte as before (`WatercolourPictures` both modes compared;
  `washOthers`); non-Wet pages unchanged (the BEFORE table).
- **Speed, after the coordinator's Manta bench** (2026-10-22, `-e wetPaint true`, sizes 8, 60, 120: Grey wash kept up;
  Line and wash, Wet ink and Slow ink wash lagged 1.1–8.4 s from pen to screen at 60–240 px, 5–25 of 720 events
  handled). Their size is the line's, their wash up to 8 times wider (about 270–430 px at 60–240), and every pixel the
  wet canvas lays under the pen costs about the same. Now:
  - an ink wash's wash is laid at most `WET_HALO` 40 px past its line each side and at most `WET_BODY` 180 px across,
    unless the line is wider (`ResolvedBrush.wetBody`; at Ink wash's own 8 px nothing changes: 46 px);
  - a pen line as wide as its wash is laid in the wash's own pass (`washMask(..., line)`); a narrower one is laid just
    before the wash at each step, so a pixel the wash then reaches is written once (`lineMask`);
  - a footprint's new pixels are read row by row between their first and last column (`Footprint.rows`): a broad round's
    new pixels are a thin crescent in a box about half the round, every pixel of which was read at each step; and the
    mask is cleared span by span. Shared by every Wet stroke; watercolour, gouache and oils draw byte for byte as before
    (`WatercolourPictures` both modes, `washOthers`).
  Desktop (`scripts/bench_desktop.sh wet 8,60,120,240 "Line and wash,Wet ink,Grey wash,Flat wash,Slow ink wash,Soft
  wash"`, mean / p95 ms a sample, budget .1; the computer busy with other work, so a few runs repeated):

  | | 8 px | 60 px | 120 px | 240 px |
  |---|---|---|---|---|
  | Line and wash | .02 / .02 | .05 / .07 | .07 / .10 | .08 / .12 |
  | Wet ink | .02 / .02 | .06 / .08 | .08 / .12 | .08 / .11 |
  | Slow ink wash | .02 / .03 | .04 / .06 | .05 / .08 | .07 / .10 |
  | Grey wash | .01 / .01 | .02 / .03 | .04 / .06 | .08 / .12 |
  | Flat wash | .01 / .01 | .02 / .03 | .04 / .05 | .08 / .12 |
  | Soft wash (watercolour) | .01 / .01 | .02 / .03 | .04 / .06 | .08 / .11 |

  Before this: Line and wash .16 / .29 / .29 at 60 / 120 / 240, Wet ink .10 / .16 / .34, Slow ink wash .14 / .17 / .19.
  The flow after the lift: 20–67 frames, 240–760 ms of desktop work. Device bench (not run here):
  `cd inkwash-inkwash  # a worktree && KEEP=1 scripts/bench_device.sh -e wetPaint true -e names "Line and wash,Wet ink,Grey wash,Flat wash,Slow ink wash" -e sizes 8,60,120,240 && scripts/bench_device.sh --uninstall`
- Looks at a broad size: Line and wash and Wet ink at 60 px show their line with a 40 px pale halo (the old one's faded
  out over about 100 px); at 240 px the wash is under the line only. Slow ink wash at 60 px is 180 px across, not ~300.
  Pictures at 60 and 120 px: `scripts/watercolour_pictures.sh <folder> "Ink wash" 60`.
- Pictures: `scripts/watercolour_pictures.sh <folder> "Ink wash"` (old top, wet bottom: one stroke, crossing at once,
  .5 s and 2 s apart, tilted half upright). Checks: `WetPaintChecks.inkWash`, `ToolPresetsChecks` (116 presets, 27
  retired).

## Light over dark (2026-10-22)

The owner: "light colors on opaque materials.. crayon, oils.. they should go on light. like white or grey paint", then
"pencil and charcoal in lighter colors should do the same" and "also the print ones".

- One rule (`ResolvedBrush.laysLight`): a dry stick's or pencil's line (Graphite, Charcoal and Pastels, Crayon) and any
  Print Shading or Screentone mark is pigment that sits on top. Where the page under it (`ModularCompose.Under`, the
  composite as the gesture found it) is darker than the stroke's grey, it lays that grey over it by its reach (tooth and
  grain kept) and covers by the same amount, as opaque Cover paint already did. Elsewhere it darkens as before, so
  black, a grey over lighter marks, and every page without light-over-dark pixels draw byte for byte as before (the
  golden hashes are unchanged). Washes stay transparent: darken only.
- White ink (2026-10-24, the owner: "if they select a light shade, it's treated like a light color ink"):
  `ResolvedBrush.lightOver` is how opaque the light grey is. Sticks and prints 1. A pen's line (Ink, Wet under .5,
  Instant or Fast-wet, no fade, halo or float) or a Marker style's flat ink (Wash, Instant) 0 at mid grey (128) or
  darker, easing to 1 by 150 (smoothstep). A pen's line lies between its darkening and its cover by it, its body
  covers as Cover paint by it. Over black: white 255, the bar's light grey 175. Mid grey and darker never read the page,
  so they compose byte for byte as before. The pen line is on its pair's ink layer, so it covers that pair's ink and
  wash and everything below; a marker's wash stays under its pair's ink.
- Print: its black dots ink first (the max-union rule and the print tone are as before); where they don't, white goes
  on the pixels whose thresholds are highest (`PrintScreen.white`, the dots' complement), the share that leaves the page
  at the print's grey on average: pure black and white, crisp on e-ink.
- Wet canvas (`WetWatercolor.paintMask`, an Inkwash hook): opaque paint lighter than the wet paint under it keeps
  `LIGHT_MIXES` (.35) of its mixing, so it covers more than it mixes. Dark on blank paper still stays dark; a darker
  stroke into lighter wet paint mixes as before. Wet oils still get dirtier dragged through dark paint (their Drag),
  so white oil over wet black ends a light grey (about 177 of 255), white gouache near white (219).
- Dry Cover presets (Opaque round, Impasto, Dry oils) already laid light; Dry round, Scumble and Cover hake lay it
  broken, by design. A light stroke covers only its own layer and those below: a stroke on a pair's wash half can't
  cover ink on the ink half above it.
- Check: `LightOverDarkChecks` (24 opaque presets over a black block, prints only black and white, Wet gouache over wet
  black, transparent washes and inks stay black). Pictures: `java LightOverDarkChecks pictures <file.png>`.

