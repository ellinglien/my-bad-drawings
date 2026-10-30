# Speed plan for every drawing tool (2026-10-22)

The owner, 2026-10-22: "i noticed the paints we have are very slow. try to think of ways to improve the speed of all
tools".

This is research and a plan only. No app code was changed. Branch: `inkwash-v2-lab`. Read beside the
[MonoPaint paint proposal](2026-10-22-monopaint-paint-proposal.md) and the engine spec's panel findings
([modular engine design](2026-10-14-modular-engine-design.md), "Measuring the panel" and "Lift after the fast update").

## 1. Summary

- **The engine is not the main cost for most tools.** Drawn live, batched 8 samples a present, every Gouache preset
  costs 0.3–1.6 ms a sample on the Manta (estimated: desktop × 20). The 480 Hz budget is 2.08 ms. Oils, Dry brush,
  Hakes, washes and pencils mostly fit too, up to about 200 px.
- **What the owner sees as slow is the quick rule.** `QuickStroke.PANEL` (36 px of reach) sends almost every paint,
  stick, pencil and hake quick from about 30–60 px. A quick stroke shows a flat preview, then writes the true texture
  behind the pen (Trail) or at lift. On the panel's black and white dots:
  - For **opaque paint** (Gouache, most Oils) the preview's dots and the final's are 93–99% the same. So the "every
    pixel twice" cost the paint proposal feared is small for them (§3.1).
  - For **textured tools** (pastels, charcoal, pencils, Hog bristle, Bristle brush, Dry brush, Split nib, Hatch pen,
    Ballpoint, Oil paint) 10–80% of the dots change again behind the pen or at lift. The last 10% of a stroke's dots
    land 0.35–4.3 s after the brush's edge reached them. Drawn live, the same strokes change 99–100% of their dots
    once, and 90% are final within 0–150 ms of the edge (§3.2).
  - The rule's reason was the panel's fast update (mode 5), which was **turned off on 2026-10-16** (it left ghosts).
    On the gray update a quick preview is only about 10–70 ms ahead of live under the pen. Its cost (the texture
    0.4–2 s later) is now the larger one.
- **Every lift that renders re-presents the whole stroke's box.** That is 1.8–4.0 million px for the bench's S-curve,
  where only a few thousand dots change. During the stroke too, the presented rectangle is a median **7×** the pixels
  that changed (up to 45× for Ben-Day at 200 px).
- **A few tools are slow in the engine itself:**
  - Print's Ben-Day, Rosette, Halftone, Engraving and Wavy-line work out a trig screen threshold per pixel, every
    compose.
  - Split nib and Copperplate (placed lines).
  - Slow-drying gravity washes tick 215–343 times after lift. They present about 110–170 of those ticks, while only
    2–6% of the dots ever change.

**Quick wins first** (§6): stop the panel reason for going quick (S), present by changed tiles (S–M), cache Print
screens (S), cut empty settle presents (S–M).

## 2. The bench

**Where:** a scratch folder, `speedplan/` (not in the repo).

- `src4/io/github/mpdairy/monopaint/SpeedBench.java` and `LagCompare.java`.
- `run.sh` compiles the `sources=` list of `scripts/test_inkwash.sh` plus these files.
- Results: `all.tsv` (every preset as the app decides), `liveall.tsv` (every preset live), `nosleep.tsv` (the render
  falling behind), `lag_rule.tsv`, `lag_live.tsv`, `lagcompare.tsv`, `alloc.tsv`.

**What it does:** every Basic tools preset (117), the eraser (S, M and XL) and Fill (5 brushes), drawn through
`QuickStroke.start` exactly as the drawing screen starts them.

- **The stroke:** the device bench's 1.5 s S-curve (`ModularPerfBench.path`: 720 samples at 480 Hz, pressure rising
  and falling) on a 1920 × 2560 page of marks.
- **Input and present:** samples go in 8 at a time (a 16.7 ms batch, as `PenBatch`), then `present()`, then the page's
  dirty rectangle is taken, as `DrawingPad.renderDirty` takes it.
- **Sizes:** 16, 60 and 200 px (60, 120 and 240 for the lag runs).

**What it measures:**

- **Timing run:**
  - Pen-thread ms a sample (sample + present).
  - The lift (`finish`) and the quick render's own ms.
  - The settle ticks after lift and their ms (`WatercolorSession.tickDrying`).
  - Prof sections.
  - Bytes allocated on the pen thread a sample.
- **Panel run** (paced 1 ms of sleep a batch, about real time at the Manta's 20× slowness):
  - After each present, the page inside the dirty rectangle is turned into the drawing screen's black and white dots
    (`DotPattern.pixel`, what `ViewportBitmap.compose` shows at 1:1). These are compared with what was last shown.
  - Per dot: how many times it changed in the stroke, at lift and while settling.
- **LagCompare:**
  - Footprint arrival is taken where the flat quick preview first changes a dot.
  - Per dot: the time from footprint arrival to the dot's last change, drawn quick and drawn live. A change at lift
    counts at the lift plus its estimated Manta wait.
  - Reported: p50 and p90.

**Manta estimates:**

- Engine and render cost: desktop × 20 (the memory note; the earlier bench's ratio).
- Panel update: 270 ms (mode 7).
- A quick render's after-lift wait: max(real-time desktop wait × 20, render ms × 20 − 750 ms). This assumes the
  render thread gets about half a core over the 1.5 s stroke on the Manta. **That assumption needs the device.**

**Limits:**

- The desktop has more and faster cores than the Manta. The render thread's real share and the driver's blocking are
  not modelled.
- Smudge-only presets (Wash comb, Dirty brush, Water brush, Clear water) drew over marks on another layer in the live
  run, so their live update counts read 0. Ignore those rows.
- Timings vary ±30% run to run (JIT, scheduling). The quick rows' after-lift waits are the noisiest.

Run it:

```
SP=path/to/speedplan
$SP/run.sh SpeedBench > all.tsv                      # every preset, the app's rule (~15 min)
$SP/run.sh SpeedBench live sizes=60,200 > live.tsv    # every preset drawn live
$SP/run.sh LagCompare sizes=60,120,240 only="Opaque round,Soft pastel,Hog bristle"
```

## 3. Findings

### 3.1 Dots changed more than once (quick, as shipped)

Mean over presets, panel run, 60 and 200 px. "Late" means the render thread falls far behind the pen (no pacing), as
it may on the Manta.

| Family | Updates per touched dot | Dots changed again at lift, paced | Same, render late | Dots first shown at lift |
|---|---|---|---|---|
| Gouache | 1.03 | 1% | 3% | 0% |
| Oils (not Oil paint) | 1.02–1.18 | 1–8% | 2–18% | 0–2% |
| Oil paint | 1.67–1.78 | 67–78% | same | 9–11% |
| Hake | 1.05 | 2% | 5% | 9–28% (wet hakes) |
| Dry brush (cover) | 1.14–1.26 | 2–10% | 14–26% | 0% |
| Dry brush (wash, glaze) | 1.04–1.06 | 2% | 4–6% | 21–28% → 67–68% late |
| Pastels | 1.12–1.32 | 3–12% | 12–32% | 9–18% → 35–40% late |
| Charcoal | 1.16–1.32 | 6–18% | +8% | 11–30% |
| Pencil (B to 5B) | 1.17–1.29 | 6–12% | n/a | 0–17% |
| Print | 1.00–1.01 | 0% | 0% | 4–14% |
| Watercolor and Ink wash (Fast-wet) | 1.04–1.11 | 1–3% | 2–5% | 7–31% |
| Split nib, Copperplate | 1.64–1.72 | 27–32% | | 0% |
| Bristle brush, Ballpoint, Hatch pen | 1.24–1.52 | 6–34% | | 0–24% |
| Water brush, Lemon juice, Wax resist | 1.56–2.00 | 56–100% (by design: the preview can't show the result) | | |

**Drawn live, every family is at 1.00–1.09 updates a dot** (`liveall.tsv`). So the "dab by dab" darkening that
`PANEL` guards against flips almost no dots at the panel's 16-grey dot patterns. The worst live case was Dry round at
1.06–1.09.

### 3.2 How long after the brush's edge a dot is final (p90, pen ms, Manta lift wait included)

From `lagcompare.tsv`:

| Preset | 60 px quick / live | 120 px quick / live | 240 px quick / live | Live Manta ms a sample (est.) |
|---|---|---|---|---|
| Opaque round | 0 / 0 | 0 / 16 | 0 / 16 | 0.5–0.8 |
| Impasto | 0 / 0 | 0 / 16 | 0 / 16 | 0.8–1.9 |
| Hog bristle | 432 / 32 | 782 / 66 | 1102 / 116 | 1.6–2.6 |
| Oil paint | 1534 / 0 | 1644 / 16 | 1692 / 32 | 1.5–3.0 |
| Soft pastel | 732 / 16 | 700 / 32 | 850 / 66 | 1.5–2.9 |
| Chisel pastel | 602 / 0 | 652 / 16 | 766 / 16 | 1.7–2.5 |
| Soft charcoal | 538 / 16 | 532 / 16 | 858 / 50 | 1.4–3.4 |
| Dark stick | 822 / 16 | 832 / 16 | 1560 / 50 | 1.7–4.5 |
| HB pencil | 816 / 0 | 882 / 16 | 750 / 16 | 1.5–2.5 |
| Dry round | 350 / 16 | 358 / 16 | 400 / 32 | 0.7–1.7 |
| Flat dry | 516 / 0 | 500 / 16 | 582 / 16 | 0.5–1.0 |
| Bristle brush | 654 / 32 | 754 / 66 | 1014 / 132 | 1.2–1.6 |
| Cover hake | 310 / 0 | 266 / 0 | 182 / 0 | 0.5–2.3 |
| Split nib | 1876 / 32 | 1082 / 66 | 4274 / 150 | 0.9–6.9 |
| Ballpoint | 936 / 32 | 732 / 50 | 968 / 100 | 0.9–3.1 |
| Hatch pen | 950 / 0 | 682 / 0 | 850 / 0 | 1.2–2.1 |
| Wet brush | 882 / 32 | 732 / 32 | 932 / 66 | 2.8–5.1 |
| Bleed marker | 618 / 16 | 640 / 32 | 882 / 66 | 1.5–4.2 |
| Soft wash, Grey wash, Wide gouache, Oil hake, Wet hake, Dry glaze, Ben-Day, Mezzotint | 0 / 0–16 at every size | | | 0.4–5.5 |

**Quick costs textured tools 0.35–4.3 s at p90. Live costs them 0–150 ms**, provided the engine keeps up. Where the
live engine is over 2.08 ms a sample at K = 8, `PenBatch` makes bigger batches. That is the existing `keepsUpTo` rule's
job, built from device data, and it should stay.

### 3.3 Slowest tools as shipped (estimated Manta lag)

Ranked by the estimated time until the stroke looks final (under the pen plus after lift), from `all.tsv` and
`lagcompare.tsv`:

| # | Tool, size | Why | Numbers (desktop → Manta est.) |
|---|---|---|---|
| 1 | **Split nib 200–240** (Copperplate similar) | Placed lines: preview footprint ≠ final; line + retaper heavy | Render 169 ms → ~3.4 s; 1.7 updates a dot; p90 final 4.3 s; live 6.9 ms a sample |
| 2 | **Slow ink wash 200** | Drying quick stroke: no Trail; the lift re-lays line, body and settle | Lift 130 ms → ~2.6 s, then 104 settle ticks; 39% of dots first shown at lift (at 60 px) |
| 3 | **Mold ink 60–200** | Halo growth at lift | Lift 72–97 ms → 1.4–1.9 s; 66 ticks after |
| 4 | **Ben-Day / Rosette ink 200** (Halftone, Engraving, Wavy-line) | Per-pixel trig screen threshold in the quick preview | Pen thread 0.22–0.25 ms a sample → 4.3–4.9 ms (2.3× the 480 Hz budget): ~2 s behind by lift |
| 5 | **Oil paint 60–240** | Smudge (Drag + Paint Load) can't be previewed | 67–78% of dots change again at lift; p90 1.5–1.7 s |
| 6 | Dark stick 240, Messy stick 200, Hog bristle 240, B/2B pencil 60 | Sticks and bristles quick by PANEL; texture behind the pen | p90 1.1–1.6 s; render 70–90 ms |
| 7 | Slow gravity washes (Drips, Tilt blend, Sinking grain, Pooling) 200 | 215–343 settle ticks, ~half presented | Lift 31–33 ms → 0.6 s; ticks 0.96–1.29 s desktop CPU (~20–26 s of Manta CPU) for 2–6% of dots |
| 8 | Fill (any brush), 700 × 700 area | One big compose on the pen thread | 4–7.5 ms → 85–150 ms, 1 MB allocated; presented once (fine) |
| — | Eraser | — | 0.003–0.008 ms a sample, 1.00 updates a dot: nothing to do |

**For the paints the owner named:**

- Gouache is not engine-bound and its quick preview matches its final.
- What he likely feels:
  - The lift's render and re-present (0.3–0.6 s, the engine spec's device table: quick strokes landed 0.63 s after
    lift).
  - Oils' Hog bristle and Oil paint (§3.2).
  - Pastels' and Dry brush's textured dots arriving late.
- The device must confirm Gouache (§7).

### 3.4 Other measurements

- **Presented area:**
  - During a stroke, the dirty rectangle is a median 7× the dots that changed. At 200 px: Soft pastel 12.3k against
    0.9k px a sample; Ben-Day 43.9k against 0.9k.
  - At a quick stroke's lift, `document.dirty()` is the whole stroke's box: 1.77–4.0 Mpx. Opaque round 200 changes
    under 1% of that.
  - The panel itself doesn't care about size (engine spec: "Size doesn't matter"). The cost is CPU on the UI thread:
    `ViewportBitmap.compose` of the area, and the JNI four-bit compare and copy.
- **Pen-thread allocation:**
  - Median 3.8 KB a sample quick, 7.3 KB live. Hog bristle 16 px: 48 KB; Bristle brush 16: 41 KB (`BristleSwarm.Dabs`
    growing by `Arrays.copyOf`); Blend stump 200: 38 KB.
  - Most of the rest is inherent: copy-on-write tiles in `ToneDocument.copyTile`/`capture` and new `FloatTiles` tiles.
  - `BodySettle.Bands.band` allocates per settle. At 480 Hz, 41–48 KB a sample is about 20 MB/s on ART.
- **Retaper:** `line.retaper` is 40–50% of `line` for pencils (B, 2B), charcoal and brush pens (Prof).
- **Present cadence:** not modelled. `presentGap` is 1.5 × the moving `presentMs`, clamped to 8–100 ms. Measure on the
  device.

## 4. Causes by family

- **Paints (Gouache, Oils, Hake cover, Dry brush cover):**
  - The PANEL rule sends them quick at 60 px and up. The quick render and its lift (0.3–1 s on the Manta) delay the
    texture (impasto ridges, bristle streaks, dry-brush gaps).
  - The lift re-presents the stroke's box.
  - Hog bristle's bristle body is the heaviest live (2.6 ms a sample at 240).
  - Oil paint's smudge forces a full rewrite at lift.
- **Charcoal, graphite and pastels:**
  - The quick preview is a fixed half dither (`HALF`), not the stick's grain. So 10–40% of the dots are new or changed
    at lift.
  - The live cost is grain at every pixel plus the retaper of the Fast-wet tail (RETAPER_STICK).
- **Screentone and patterns (Print):**
  - The tone presets are cheap.
  - Ben-Day, Rosette, Halftone, Engraving and Wavy-line compute a cos/sin/sqrt threshold per pixel, every time a pixel
    is composed or previewed (`PrintScreen.threshold`; only the evened kinds are cached in `kept`).
- **Hatch, stipple and spray:**
  - Stipple and Hand hatch are live and cheap.
  - Hatch pen goes quick from 60 px. HATCH is 125, so this is the PANEL rule, and its lines all land at lift (p90 0.7–0.95 s).
- **Smudge and carry (Stump, Blend stump, Water brush, Dirty brush, Oil paint):**
  - A preview can't show a smudge, so quick means everything at lift.
  - Carry costs 18–76 ms of render.
  - `CARRY` (90 px dab) sends them quick.
- **Washes (Watercolor, Ink wash Fast-wet):**
  - Preview dots match (opaque-ish light dither), but 7–31% of the dots appear only at lift (the preview's light dither
    is lighter than the wash).
  - Slow: no Trail, a full re-lay at lift, then many settle ticks.
- **Eraser:** fine.
- **Fill:** one compose of the area on the pen thread (85–150 ms on the Manta est.). Acceptable for a tap.

## 5. Fixes

**Gain:** measured on the desktop unless "est.". **Risk to look:** "none" means the final page is byte for byte the
same. **Generic:** helps every tool, not one family.

| # | Fix | Gain | Risk to look | Effort | Generic |
|---|---|---|---|---|---|
| F1 | **Retire the panel reason for quick.** `QuickStroke.quick` = `cost(r) > 1` (and Float); drop `panelReach > PANEL`, or set `PANEL = ANY`. Strokes the engine keeps up with draw live, each dot written once and final under the pen. | Textured tools: p90 final 0.35–1.7 s → 0–0.15 s (§3.2). Opaque paint: no lift render (0.3–0.6 s on the Manta). No lift re-present. The quick render thread goes away for them. | None to the final page (quick and live end the same: `QuickStrokeChecks`). Fast-wet coverage differs as live batching does today. Under the pen the mark shows 10–70 ms later than a flat preview would. | S (rule + checks' rule rows) | Yes |
| F2 | **Present changed tiles, not one box.** `ToneDocument` keeps a dirty tile set beside `dirty()`. `renderDirty` passes it as `apart` areas (that path exists for mirrors), merged into a few rects. | Compose and JNI area −7× median in the stroke; at a quick lift −100× or more (1.8–4 Mpx → the changed tiles). Est. 20–60 ms of UI thread a lift on the Manta. | None | S–M | Yes |
| F3 | **Cache the Print screen threshold for every kind** (extend `kept` from the evened kinds to Ben-Day, Rosette, Halftone, Lines, Wavy, Cross, Diamond…). Keep the value as computed (a double table or exact float) so ties don't move. | Ben-Day and Rosette 200: pen 0.22–0.25 → est. ~0.05–0.08 ms a sample (quick.preview is nearly all of their 155–176 ms of pen-thread time a stroke). Under the 480 Hz budget. | None (if stored exactly) | S | Print only (as built: §5.1) |
| F4 | **Settle shows only what flips a dot.** A tick's write compares the dots, not the tones, before presenting. Ticks that flip none are not presented. Optional: stop ticking after N ticks without a flipped dot. | Gravity washes: about 110–170 presents → est. 10–30. The CPU stays unless the early stop is taken (then est. −50%). | None without the early stop. With it, the saved tones differ slightly (sub-dot; visible only zoomed). | S–M | Yes (washes, Float, Halo) |
| F5 | **Previews that match the final dots** (for what stays quick at large sizes): a stick's or pencil's preview thresholds its flat darkness against the paper's grain (`StrokePaper`) instead of `HALF`. A wash's preview takes the body's expected tone, not `SPARSE`. | Pastels and charcoal at lift: est. 10–40% → under 10% of dots changed. Washes: 7–31% first shown at lift → est. under 5%. | Preview only. The final is unchanged. | M | Sticks and washes |
| F6 | **Placed-line preview** (Split nib, Copperplate): sweep each placed line's own nib (`Placement` geometry), not the hull. | Lift rewrites 27–32% → est. under 5%; p90 4.3 s → about the render time. | Preview only | M | Dip pen |
| F7 | **Fewer retapers where they're invisible:** in a quick render, retaper only at a compose (`composeMs`) and at lift. Live: retaper at most once per present (`BIG_RETAPER_MS` for sticks). | `line.retaper` is 40–50% of line time: B/2B pencil live est. 2.5 → 1.8 ms a sample. Render −30% for sticks. | Must be byte-checked: if the final taper depends only on the last retaper, none; otherwise a visible tail change. | M | Lines |
| F8 | **No growing arrays on the pen thread:** `BristleSwarm.Dabs` sized once a stroke (from size and spacing); `BodySettle` bands reused per worker. | Bristles 41–48 KB → est. under 5 KB a sample. Fewer ART GC pauses (device). | None | S | Partly (as built: §5.1) |
| F9 | **Trail for Drying quick strokes and Halo/Float lifts** (only Instant/Fast-wet trail today): write the body as composed, settle after lift as now. | Slow ink wash 200: est. half of the 2.6 s lift moves under the pen. | The preview shown differs. The final page is unchanged if the lift re-lays as now. | M–L | Washes |
| F10 | **Fill on the cores:** `ModularStroke.fill` stamps the area in bands (`Cores.inBands`) and allocates its buffers once. | 85–150 ms → est. 30–50 ms a tap. | None | S–M | Fill |
| F11 | **Present cadence:** cap `presentGap` at about 33 ms while the pen is down (the panel restarts a pixel on each change and doesn't queue, so presenting sooner never delays the final; the driver blocks 40–55 ms in runs). | Device only. | None | S | Yes |
| F12 | **Lattice for sticks at 60–100 px** (`INK_LATTICE_PX` 100 → 50 for charcoal and pastels only, if F1 leaves them over budget): half resolution live, as paints already are. | Est. ~2× live engine for sticks at 50–100 px. | Visible: edges a pixel softer (the owner's call, as paints in the audit's finding 16). | S | Sticks |
| F13 | **Oil paint and smudges live to a bigger dab:** with F1, `CARRY` (90 px) still sends Oil paint quick. Measure Drag live at 120–240 on the device and raise `CARRY` if it keeps up. | Oil paint p90 1.5–1.7 s → est. 0–30 ms where live. | None | S (after the device run) | Smudge |
| F14 | Half-res live preview refined at lift (the past idea) | Superseded: the lattice already does half resolution for paints at 50 px and up, and F1 makes the refine-at-lift step unnecessary for anything that keeps up. | | | |
| F15 | SIMD-ish int math in compose | Not worth it now. Compose is 30–60% of live time for sticks and impasto, but the JIT already keeps it tight. Revisit only if F1 + F12 leave sticks over budget. | | L | |

### 5.1 As built: F3 and F8 (2026-10-22, branch `speed-f3-f8`)

Bench: `run.sh SpeedBench nopanel only="Print,Hog bristle,Bristle brush"`, pen ms a sample (best of 3–5 runs) and
pen-thread bytes a sample, desktop. Untouched kinds moved 0–6% either way (noise).

**F3, Print thresholds kept (`PrintScreen`):**

- Kept now: Ben-Day and Rosette (as the doubles worked out), besides the four evened kinds (floats, as before).
  `threshold` is `keptAt` or `worked`; `worked` is the old formula, unchanged.
- Not kept: Halftone, Dots, Gradient, Lines, Wavy, Crosshatch, Diamond, Mezzotint, Dither, Grain, Sand. They work out in
  ~4–11 ns a pixel (desktop), and the preview asks most pixels about once, so the tile lookup and first fill cost more
  than they save. Kept, Halftone, Engraving, Wavy, Mezzotint and Hatched tone ran 6–23% slower; the first build kept
  everything and was reverted to this.

| Preset | px | ms before | ms after | | B/sample before | after |
|---|---|---|---|---|---|---|
| Ben-Day brush | 16 | 0.036 | 0.028 | −23% | 4.1k | 8.3k |
| Ben-Day brush | 60 | 0.091 | 0.062 | −31% | 7.3k | 16.2k |
| Ben-Day brush | 200 | 0.232 | 0.142 | −39% | 14.2k | 31.0k |
| Rosette ink | 16 | 0.041 | 0.040 | −4% | 5.0k | 10.6k |
| Rosette ink | 60 | 0.095 | 0.087 | −8% | 8.7k | 19.2k |
| Rosette ink | 200 | 0.197 | 0.152 | −23% | 13.6k | 29.5k |

- **Short of the 0.05–0.08 estimate.** The threshold isn't most of the preview's cost. Ben-Day 200 asks ~9.3k
  thresholds a sample on the pen thread, and ~2k of them are new pixels that must be worked out anyway. The rest of
  `QuickStroke.Body.tones` (document tone, print tone, `printed`, Ink gain's hash) runs for every pixel of every stamp,
  inked already or not.
- **Next step (not done; it's QuickStroke's preview, outside this change):** in `tones`, skip the screen's `ink` where
  the pixel can't change (`opaque == null && now <= want`; want is 0, or min(base, DOT_TONE) for Tone). Tried on the
  desktop: Ben-Day 200 0.232 → 0.124, Rosette 200 0.197 → 0.102, Rosette 16 −34%, Halftone 200 −10%. Golden over every
  preset: identical.
- **Memory:** a kept Ben-Day or Rosette tile is 8 KB (32 × 32 doubles), allocated on first ask. That's the B/sample
  rise above (~17 KB a sample at 200 px). A whole 1920 × 2560 page would be 39 MB (a page-wide Fill); a 200 px stroke
  across it a few MB. The preview's screen and the render's are two instances, each with its own tiles.

**F8, no growing arrays on the pen thread (`BristleSwarm`):**

- `Dabs` and the workers' `Lanes` live in the stroke's `State` and are cleared per feed, not made per feed. Their
  arrays grow to the largest feed once a stroke.
- `BodySettle`'s bands not changed: the allocation profile (JFR, Hog bristle and Bristle brush 16) shows no `BodySettle`
  allocation for them.

| Preset | px | B/sample before | after | ms before | ms after |
|---|---|---|---|---|---|
| Hog bristle | 16 | 48.2k | 14.3k | 0.086 | 0.085 |
| Bristle brush | 16 | 41.2k | 7.3k | 0.059 | 0.053 |
| Hog bristle / Bristle brush | 60, 200 | 2.7–8.3k | unchanged | | (noise) |

- **Short of "under 5 KB" for Hog bristle 16:** what's left is the stroke's own first-touch tiles (`FloatTiles.tile`
  for the hair and body buffers, `ToneDocument.copyTile`/`copyPlane`) and the swarm's trail field. That's inherent,
  as §3.4 says.

**Identical output:**

- Golden harness: every Basic tools preset at 16, 60 and 200 px, the app's rule and all live. CRC of each live
  present's dirty rectangle and of the final page after settling.
- Old and new code: 702 rows, all identical.
- `ScreenChecks.kept`: every screen and pattern (cell 9, angle 20); the kept threshold read twice equals `worked` bit
  for bit, including off the page and past the kept tiles.
- `BristleSwarmChecks`, already in place: batching and cores give the same hairs.


### 5.2 As built: F6, F9, F7, F5 and a Record and Replay fix (2026-10-22, branch `speed-f6-f9-f7`)

Desktop only; the Manta runs are in §5.3. Bench files in the scratchpad's `f6f9/` (`run.sh`, `src4/`: `Golden`,
`SpeedBench`, `PreviewMatch`, `LiftBreak`, `RetaperProbe`, `ProfRender`). **PreviewMatch** draws a quick stroke with
Trail off and compares the preview's black and white dots at lift with the final page's, over the dots either changed.

**Identical final, every commit:** `Golden` (every Basic preset at 16/60/200 px, the app's rule and all live; CRC of
each live present and of the settled page) 702/702 rows identical to `inkwash-v2-lab`; desktop checks RAN 115, none
failed; release build.

**F6, placed-line preview (`QuickStroke`, `Placement.Geometry`, `FootprintSweep`):**

- The preview swept a circle as wide as the placed body. It now sweeps each placed line's own nib ellipse where
  `Placement` lays it, a split's two sides of its gap each a sweep of their own. `Placement`'s geometry moved,
  unchanged, into `Placement.Geometry`, shared by `stamp` and the preview.
- Split nib, Copperplate, Ballpoint at 60/120/240: updates per dot 1.31–1.72 → 1.01–1.07; dots rewritten at lift
  13–34% → 0.4–2.9%; p90 first-to-last change 0.75–4.3 s → 0; preview dots equal to final 28–68% → 89–98%.
- The render's cost is unchanged (Split nib 240 still ~220 ms desktop, ~4 s Manta est.); what changes is that the
  dots shown under the pen are already the final ones.

**Record and Replay drops the wet state (bug, fixed):** `DrawingPad` logs each pen stroke right after its finish, and
`logStroke` made a new page revision. A Slow wash, a halo and a floating bath hold the revision their lift left, so the
next tick read "page changed" and dropped them (the §9 log "drying wash stops: page changed" after Slow ink wash's
lift). A Slow wash never settled, Halo was set at once, Float stopped. `logStroke` no longer bumps the revision (the
stroke's own step did). `StrokeLogChecks.keepsWet`.

**F9, Trail for Drying and Halo, and a faster big-wash lift:**

- Trail (`ModularStroke.trail`) now covers Drying and Halo: behind the pen the render's line and body as laid (the
  body's light grey while wet), settled at lift over them. Float doesn't trail (its bath composes on the page; its
  lift is 20–30 ms desktop). Slow ink wash 60/120/200/240: p90 first-to-last change 5.1/2.4/1.9/0 s → 2.0/1.1/0.55/0 s;
  dots first shown at lift 39/25/12/9% → 18/11/7/6%; page at lift equal to final 37% → 65% (60 px), 79% → 88% (200).
  Mold ink: dots first shown at lift ~1% → 0.2%.
- Where Slow ink wash's lift goes (desktop, 200 px, before): render thread after lift 33 ms (settle 20, compose 13);
  the pen thread's write 64 ms, of which **55 ms laying the big wash settled at once** (`DryingWater.finishNow`: more than
  `MOST_CELLS`), one cell row at a time on one core; the stroke's tones 3.5 ms. So Trail itself moves little time (the
  plan's "half the lift" estimate was wrong).
- `DryingWater.finishing` now lifts and lays a block of `Cores.WORKERS × 16` rows at a time, over the cores (rows were
  already independent). Lift to final 120/200/240 px: 77/98/115 → 58/65/71 ms; pen thread 51/64/75 → 29/31/34 ms.
- Not done: settling the body behind the pen. `BodySettle` is local, but its half-size grid is aligned to the settle
  region's origin (the stroke's box at lift) and a stick's or line's tail is laid again at lift, so an exact
  incremental lift would need a cache checked at lift (origin parity unchanged, no dab within reach since). L-sized.

**F7, retapers in a quick render (`ModularStroke.retaperNow`):**

- A Fast-wet line after a re-taper is the last re-taper plus the dabs since (the one before lays nothing the next
  doesn't lay again). A render now notes the sample the pen's time calls a re-taper at and does it at its next compose
  or at lift, the samples since hidden from it, then their dabs laid again. Every compose, and the end, are as before.
- Only where that holds exactly: a line alone (a body takes the line in as laid, and the merge adds per merge),
  no splatter, **no stick dust** (Dark stick, charcoal, pastels: specks reach past the re-taper's box, so the end
  depends on every re-taper; tried, `sameAsLive` caught it), no flick, smudge, Paint Load or hand page tilt. Presets: B
  and 2B pencil, Split nib, Broad nib, Chisel and Tilt brush, Toothy line, Lemon juice. Live strokes unchanged (a
  present comes every batch, under the cadence).
- Gain is small: render behind the pen, retaper ms −35–70% at 60 px (B pencil 19.5 → 12.8, 2B 15.7 → 5.8), render
  −10–30%; Split nib 240 ~0 (its cost is freezing the window, which still happens once). Paced: within noise.
- `QuickStrokeChecks.retapersWait`: five presets, 60/150 px, composing every batch or never, trail on and off, every
  layer byte for byte.

**F5, flat previews for washes and sticks:** the preview writes its flat tone at every pixel (was a dither on every
fourth pixel for a wash, every other for a stick), so the panel's own dots show it as they show the final. Preview dots
equal to final: washes 27–38% → 87–90%, pencils and sticks 30–43% → 56–74%. Dots first shown at lift: Soft wash 21/31%
→ 2/3%, sticks 15–31% → 3–11%. Pen thread 0.004–0.023 → 0.006–0.037 ms a sample.

**Wet brush (profiled, nothing changed; replaced 2026-10-22 by the wet canvas Wet brush, the owner's choice: wet paint spec §8):** quick at every size (reach 50 at 16 px). Its render at 240 px, desktop
paced: 228 ms (~4.5 s Manta est. for a 1.5 s stroke): line 57 (re-taper 28), line-into-body merge 51, compose 60, window
settle 31, raise 16. Live at 240: 0.55 ms a sample, compose 226 ms (26 Mpx composed). No safe fix:

- The merge adds `along · l / 4` every time it runs over a pixel, so merging less often changes the look.
- The compose already skips empty tiles and unchanged pixels.
- Re-tapers can't be put off (it has a body).
- Options (the owner's call, each changes the look slightly): merge only over a re-taper's new dabs, not its whole box
  (merge 51 → ~15 ms est.); settle the Fast-wet window every `WET_FRAME_MS` only (drop the 0.3-window rule) at 120 px
  and up; or put Wet brush on the lattice from 60 px.

### 5.3 Manta check for 5.2 (to run)

```
# Quick strokes as the app decides: lift_to_final_ms and panel after lift against §9 Run 1
LIVE=1 scripts/bench_device.sh -e styles "@Split nib,@Copperplate,@Ballpoint,@B pencil,@2B pencil,@HB pencil,@Soft charcoal,@Dark stick,@Soft pastel,@Soft wash" -e lives none -e sizes 60,120,240 -e modes gray
# Slow washes, halo, float after lift: ticks run, no "page changed"; Slow ink wash's lift_to_final against 2.6-2.9 s
LIVE=1 scripts/bench_device.sh -e settle true -e cases "@Slow ink wash:200,@Mold ink:200,@Sumi:200,@Drips:200" -e trace true
# Engine only (no pen events): Wet brush and the re-tapering lines live, against §9 Run 2
scripts/bench_device.sh -e styles "@Split nib,@B pencil,@2B pencil,@Wet brush" -e sizes 60,120,240 -e quick off -e marks true
```

### 5.4 As built: Fill's memory and the whole-page hold (2026-10-22, branch `fill-opaque`)

The owner, 2026-10-22: "the fill bucket.. it makes things quite slow sometimes, and undo and redo also gets really slow.
it might have also had something to do with a crash that happened." and "(in this session i also accidentally filled a
blank page with the fill also, so maybe that isn't a good idea, to fill with first swatch selection)".

Measured on the desktop (Mac, ≈20× the Manta), a 1872 × 2480 page with a ring drawn, the tap on open paper (4.55 Mpx):

| | before | after |
|---|---|---|
| Slow watercolour page fill | 344 ms, 129 MB allocated, Undo step 26.3 MB | ~350 ms, 110 MB, step 13.2 MB |
| Halftone ink page fill | 116 ms, 86 MB, step 34.5 MB | ~120 ms, 86 MB, step 13.3 MB |
| Flat ink page fill (blank page) | step ≈ 27 MB | step ≈ 0.1 MB (tiles only) |
| Heap a page fill needs, page itself 25 MB (watercolour / halftone) | 130 / 115 MB | 101 / 92 MB |
| Six page fills with Undo and Redo | 163 / 149 MB | 130 / 130 MB |
| A ring's fill (89k px) | step 0.8 MB | 0.4 MB |

Undo and Redo themselves restore tiles in 1-2 ms (≈40 ms on the Manta); what was slow was everything around them on a
heap near its 192 MB limit (collections at every allocation), and a whole-page step pushing every other Undo step out of
the 32 MB history. Changes:

- **Uniform tiles are shared in Undo** (`ToneDocument.shared`): a capture or step tile that is one value (blank paper,
  a flat fill, an all-255 share or print plane) is one shared array, never written (every writer of a step copies
  first), counted as 0 bytes. A fill over blank paper keeps only its laid side.
- **A fill's settling reads its room** (`DryingWater`'s `room`) instead of a page-sized float copy of it.
- **The lift's page-sized buffers go before the Undo step is copied** (`ModularStroke.releaseLift`; wax keeps them, as
  it lays again after).
- **A heap guard** (`FillTool.fits`, `PEAK_BYTES_PX` = 18 bytes a pixel of the area): a fill the heap can't take
  collects garbage, then lets the page's older Undo steps go down to 8 MB (as a page put away keeps), else notes "Not
  enough memory to fill" and lays nothing rather than crash.
- **Nearly the whole page needs a held pen**: a tap whose area is 90% of the page or more notes "Hold to fill the whole
  page"; the pen held 500 ms before it lifts fills it. Areas shut in by lines fill at a tap as before. Chosen over a
  confirmation (one more dialog) and over refusing whole-page fills (the owner asked for them that morning).
- **The grey**: Fill already lays the selected slot's own grey (`InkWash.fill` takes the same `shifted` look a stroke of
  that slot does; with the eraser or smudge tool selected, the slot last selected). A brush with Grey Shift takes
  its next grey in turn, as its strokes do (`SwatchPicker.look`). No other path to the first swatch was found, so a
  black fill means that slot's grey (or its shift's turn) was black; unchanged.

Not changed: a fill over an already filled page still holds both sides (≈26 MB at page size, non-uniform), so the
history keeps it alone; the engine's whole-page Slow wash at lift (~75 MB over the page) is the remaining peak.

## 6. Quick wins first

1. **F1, retire the PANEL quick reason.**
   - It's one rule, and the final page doesn't change.
   - It turns most paint, stick, pencil and dry-brush strokes from "flat, then texture 0.4–1.7 s later" into "final
     under the pen".
   - Confirm on the Manta first (§7, run 1). Then ship.
2. **F3, cache Print screens.** S, byte for byte. It takes Ben-Day, Rosette and Halftone under the budget at 200 px.
3. **F2, present changed tiles.** S–M, byte for byte. Every tool presents less. It matters most for what stays quick
   and for Slow lifts.
4. **F4, present a settle tick only when dots flip.** S–M, byte for byte without the early stop.
5. **F8, no growing arrays on the pen thread.** S.
6. Then, by what the device runs show:
   - F13 (smudge live size).
   - F12 (lattice for sticks), only if the sticks fall behind live.
   - F5 and F6 (better previews for what stays quick).
   - F7 (retaper).
   - F9 (Trail for Drying).
   - F10 (Fill).
   - F11 (cadence).

## 7. What needs a Manta measurement

Each run uses the bench build (`ing.ell.mybaddrawings.bench`, never the user's app) and uninstalls it after.

1. **Live against quick under the pen and after lift: confirms F1** (the deciding run). Each preset is drawn live on
   the gray update, then with the app's rule. Read "panel after lift" and DrawingPad's `stroke timing` lines:
   - `lift_to_final_ms`, `render_ms` and `trail_tiles` for the quick ones;
   - the batch lag for the live ones.

   ```
   LIVE=1 scripts/bench_device.sh -e styles "@Opaque round,@Wide gouache,@Impasto,@Hog bristle,@Oil paint,@Soft pastel,@Chisel pastel,@Soft charcoal,@Dark stick,@HB pencil,@Dry round,@Flat dry,@Cover hake,@Hatch pen,@Bristle brush" -e lives none -e sizes 60,120,240 -e modes livegray,gray
   ```

   Run `framelog` (scratch, the engine spec's panel tools) beside it for the panel's own frames. Inside the app,
   `PanelFrames` reads −1 (SELinux).

   **Pass:** live's batch lag p95 stays under about 60 ms, and "panel after lift" for live is at most quick's. Where a
   preset's live lag is over that, `keepsUpTo` should send it quick (adjust that row, not PANEL).
2. **Engine cost live at the presets' sizes, over marks** (the per-sample cost behind F1, F7, F12 and F13):

   ```
   scripts/bench_device.sh -e styles "@Hog bristle,@Oil paint,@Soft pastel,@Side pastel,@Dark stick,@B pencil,@2B pencil,@Ben-Day brush,@Rosette ink,@Split nib,@Wet brush" -e sizes 60,120,240 -e quick off -e marks true
   ```

3. **The lift's present area (F2) and the present cadence (F11):** on `LIVE=1` runs, DrawingPad's `stroke timing` lines
   already log the render and present ms and the presented area. Compare `-e modes gray` before and after F2 on:

   ```
   LIVE=1 scripts/bench_device.sh -e cases "@Opaque round:none:240,@Soft pastel:none:240,@Slow ink wash:none:200" -e where pad
   ```

4. **Settle presents (F4):**

   ```
   LIVE=1 scripts/bench_device.sh -e settle true -e cases "@Drips:200,@Tilt blend:200,@Slow ink wash:200" -e trace true
   ```

   Read the ticks' work and write ms, and how many were shown.
5. **The render thread's share** (the 50% assumption behind every "after lift" estimate here): run 1's `stroke
   timing quick … render_ms … after_lift_ms` for the quick rows gives it directly.
6. **GC (F8):** `adb logcat -s art` during run 2's Hog bristle and Bristle brush at 16 and 60 px. Look for
   concurrent-copying pauses over 5 ms.

## 8. Notes for the work

- **F1 is a change to `QuickStroke`'s rule only.**
  - `PANEL` and `panelReach` stay as documentation or go.
  - `keepsUpTo` keeps its device-measured rows.
  - Update the engine spec's "quick rule" paragraph and `QuickStrokeChecks`' rule rows.
  - The web lab has no quick strokes, so nothing changes there.
- **F2 and F4 are generic DrawingPad/ToneDocument changes** (MonoPaint-upstream files). Split them into small commits
  and note them in `docs/INTEGRATION.md`.
- **Wet paint** (`../inkwash-wet`, branch `wet-paint`) writes each pixel once by design. F1 and F2 help it too.
  Nothing here touches that branch.

## 9. Manta measurements 2026-10-22

The §7 runs on the Manta (firmware as shipped, bench build of `inkwash-v2-lab` at this commit's parent plus the bench
fix). Panel time from `framelog` beside each run (`/proc/interrupts`, the EBC's frames): "panel after lift" is when
the panel's last frame ran after the pen lifted. Inside the app `PanelFrames` still reads −1 (SELinux). One stroke per
row (the bench's 1.5 s S-curve, 480 Hz), so ±50 ms is noise; a `-e cases` rerun of Opaque round 240 quick read 1.5 s
where run 1 read 0.58 s, so single rows can be off further.

**Bench fix.** `LiveStrokeBench` drew nothing since the app opens on the notebook shelf: the shelf covered the page
and took every pen event (no `stroke timing` lines, "idle after lift 0 ms"). It now starts a notebook if there is no
page and always closes the shelf, in both the live and `-e cases` paths. **Guard:** pen events are injected
system-wide, so before every stroke the bench checks its own screen is resumed and has the window focus; if not, it
brings its task back once, and otherwise stops the run with "bench stopped: … no pen events sent". Each event also
checks the focus, so a stroke stops if another app comes up mid-stroke. `bench_device.sh` presses Home before and
after a run, and prints any instrumentation failure. Tested: with Settings opened over the bench before a stroke, the
guard brought the bench back and the stroke landed on it.

### Run 1: live against quick (F1)

`panel after lift` ms, live (`livegray`) / quick (`gray`, the app's rule; every row here was quick), and live's mean
batch lag ms. Quick's `lift_to_final_ms` (Trail draws the texture behind the pen on the device).

| Preset | 60 px | 120 px | 240 px | quick lift → final 60/120/240 |
|---|---|---|---|---|
| Opaque round | 1384 / 529 (4) | 1374 / 533 (3) | 532 / 581 (7) | 23/31/50 |
| Wide gouache | 1370 / 521 (3) | 1374 / 537 (6) | 589 / 601 (38) | 19/37/86 |
| Impasto | 1380 / 534 (4) | 1463 / 522 (11) | 660 / 592 (38) | 21/33/91 |
| Hog bristle | 1397 / 537 (20) | 1515 / 665 (113) | 937 / 250 (435) | 26/148/695 |
| Oil paint | 1373 / 712 (9) | 0 / 749 (42) * | 0 / 215 (510) * | 141/169/310 |
| Soft pastel | 1473 / 540 (9) | 1496 / 586 (19) | 621 / 756 (182) | 24/45/154 |
| Chisel pastel | 1464 / 530 (5) | 1466 / 551 (13) | 556 / 617 (45) | 16/30/74 |
| Soft charcoal | 1463 / 548 (8) | 1494 / 598 (17) | 611 / 736 (179) | 30/47/173 |
| Dark stick | 1454 / 558 (17) | 1507 / 634 (96) | 895 / 1041 (406) | 25/87/485 |
| HB pencil | 1470 / 556 (5) | 1478 / 562 (7) | 539 / 609 (19) | 26/35/78 |
| Dry round | 1481 / 534 (4) | 1578 / 516 (10) | 702 / 587 (41) | 25/33/72 |
| Flat dry | 1388 / 521 (3) | 1452 / 519 (6) | 639 / 547 (10) | 15/30/54 |
| Cover hake | 1370 / 531 (3) | 1369 / 540 (6) | 585 / 612 (42) | 26/30/65 |
| Hatch pen | 1453 / 531 (4) | 1470 / 567 (13) | 710 / 735 (253) | 18/29/151 |
| Bristle brush | 1456 / 552 (6) | 1468 / 583 (17) | 552 / 749 (83) | 31/38/149 |

\* Live Oil paint presented 2–3 times in the whole stroke (`presents=2`), so the panel had nothing left to drive after
lift: it did not keep up, not a win.

- **The panel reason for quick still holds on the gray update.** At 60 and 120 px every live stroke kept the panel
  driving frames without a break for 1.37–1.58 s after lift, with no presents after the lift. Quick: 0.52–0.75 s. A
  pixel changed again while it is updating waits for that update to end, so a live stroke's many changes per pixel
  queue up behind the pen. At 240 px live batches lag more, change each pixel fewer times, and the tail drops to
  about quick's.
- **Quick is already near final at lift on the device.** Trail writes the texture behind the pen: `lift_to_final_ms`
  is 15–90 ms for most presets at every size, 140–310 ms for Oil paint (no Trail), and 485–695 ms only for Dark stick
  and Hog bristle at 240. §3.2's 0.35–4.3 s (desktop, "lift wait included") does not happen on the Manta.

### Run 2: engine cost live over marks (F7, F12, F13; F8 GC)

`quick off`, over marks, pen-to-screen ms p50/p95 (bench's batched pacing):

| Preset | 60 | 120 | 240 | notes |
|---|---|---|---|---|
| Hog bristle | 12/26 | 40/79 | 114/215 | |
| Oil paint | 8/29 | 31/72 | 94/254 | |
| Soft pastel | 12/26 | 22/46 | 97/179 | |
| Side pastel | 25/68 | 21/48 | 83/163 | |
| Dark stick | 17/49 | 45/96 | 478/833 | `line.retaper` 220/360/686 ms of line 661/804/1344 a stroke |
| B pencil | 80/179 | 14/47 | 56/125 | retaper 476 of line 1011 ms at 60 |
| 2B pencil | 58/154 | 13/45 | 58/125 | retaper 443 of line 965 ms at 60 |
| Ben-Day brush | 11/54 | 24/111 | 111/285 | |
| Rosette ink | 13/51 | 23/84 | 46/174 | |
| Split nib | 2/25 | 184/289 | 3788/4463 | 240: busy 344 %, line 4.9 s a stroke |
| Wet brush | 33/57 | 120/201 | 623/1031 | |

GC: no ART GC line for the bench process (ART logs pauses over 5 ms or GCs over 100 ms) during runs 1 and 2, nor in a
rerun of Hog bristle, Bristle brush, Oil paint and Soft pastel at 16/60/120 px.

### Run 3: the lift's present (F2)

Quick strokes, before F2: the lift's final render + flush on the UI thread is 4–10 ms at 60–120 px and 20–60 ms at
240 px (Opaque round 240: 22 + 17 ms; Soft pastel 240: 21 + 34 ms; Slow ink wash 200: 18 + 23 ms). In the stroke, a
quick 240 px stroke spends 450–750 ms of render + flush on the UI thread (9–20 Mpx composed) for 1.5 s of pen, while
quick's batch lag stays at 2–21 ms mean.

### Run 4: settle (F4)

| Case | After lift |
|---|---|
| @Drips 200 | settles in 434 ms, 40 ticks, page's thread busy 129 ms (longest 50 ms) |
| @Tilt blend 200 | settles in 277 ms, 21 ticks, busy 46 ms |
| @Slow ink wash 200 | no ticks: the settle is laid ahead at lift (`settle ahead lay` 270–430 ms, then "drying wash stops: page changed"); quick `lift_to_final_ms` 2.6–2.9 s, `after_lift_ms` 1.0–1.3 s |

A pen down 150–2000 ms after any of them began its stroke within 12–48 ms.

### Verdicts

| Fix | Verdict | Why |
|---|---|---|
| F1 retire PANEL | **Not worth it (refuted).** Keep `PANEL`. | Live leaves the panel busy 1.4–1.6 s after lift at 60–120 px against quick's 0.52–0.75 s, for every preset. Quick is already final 15–90 ms after lift. |
| F2 present changed tiles | **Confirmed gain, small.** Do it when convenient. | 20–60 ms of UI thread saved a lift at 240 px, and much of 450–750 ms of compose/flush a stroke. Quick's lag is already low, so little the owner would see. |
| F4 settle presents | **Not worth it.** | Drips and Tilt blend settle in 0.3–0.4 s with 21–40 ticks; the Slow wash no longer ticks after lift. Its cost is the 2.6–2.9 s lift (F9's area). |
| F7 fewer retapers | **Confirmed cost; gain only at large stick sizes.** Medium, after the byte check. | `line.retaper` is 33–51 % of line time (Dark stick, B/2B pencil). It shows where quick is slow at lift: Dark stick 240 (485 ms to final). |
| F12 lattice for sticks | **Not worth it.** | It only helped if F1 put sticks live. Quick sticks are final in 16–173 ms up to 240 px (Dark stick 240: 485 ms). |
| F13 raise CARRY | **Not worth it.** Keep `CARRY` 90. | Live Oil paint did not keep up even at 60 px on the pad (2–3 presents a stroke, lag 42–510 ms at 120–240). Quick Oil paint is final 141–310 ms after lift. |
| F8 (GC) | Not worth it for GC. | No logged GC pauses. |

**Presets that can drop the quick preview: none.** At 60 and 120 px every preset is clearly worse live. At 240 px Opaque
round (532 / 581 ms, lag 7) and HB pencil (539 / 609, lag 19) tie with quick within noise; Wide gouache, Chisel
pastel and Cover hake also tie but lag 38–45 ms mean. A tie isn't worth a size-dependent rule.

**Where the time really is now on the Manta:** Split nib past 60 px (pen p95 0.3–4.5 s live), Wet brush 120–240,
Slow washes' lift (2.6–2.9 s), Dark stick and Hog bristle at 240 (0.5–0.7 s to final). So the order is F6, F9 and
F7, then F2.
