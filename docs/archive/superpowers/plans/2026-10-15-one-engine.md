# One engine: every old brush becomes a six-part brush (plan)

Owner's decision (2026-10-15): "no need to keep the old brush ideas, just adapt to current mode" and "Convert
everything": the built-ins Ink, Fude, Wash and Watercolor and every old Wild `I`/`P` brush become six-part brushes,
accepting that Ink and Fude may feel slightly different. Afterwards the app draws through one engine
(`ModularStroke`), plus the eraser. This reverses Stage 1's "Two engines, on purpose" (engine spec, Decisions 1–2).

Not touched: the engine files the `inkwash-perf` branch is optimising (`ModularStroke`, `ModularCompose`,
`BodySettle`, `BodyStamper`, `LineStamper`, `Placement`, `HakeRibbon`, `FloatTiles`, `StrokeMath`), and MonoPaint's
own tools (`PressureStroke`, `ToolStrokes`, `WetWatercolor`, …), which its hidden mode still draws with.

## 1. Old Wild brushes (`I`/`P` codes) → six parts

`LegacyBrushes.of(WildBrush)`, a port of the prototype's `fromOld` (parts.js 651–667):

| Old | Six-part |
|---|---|
| pen: nib round/chisel/flat, width, angle, tapers, dry, wobble | Tip Round / Chisel / Flat nib, same dials, Weight 1, Soft .3 |
| pen: pool, splatter | Medium Ink, Wet 0, Line kept 1, same Pooling and Splatter |
| paint: soft, streaks | Tip Round, Soft = soft, Dry brush = streaks |
| paint: wet/flat, hollow, feather, bloom, backrun | Medium Wash, Wet 1 or 0, Hollow edge, Bleed, Bloom, Backruns |
| texture, tooth (pen) or granulation (paint) | Paper of the same texture, Tooth = it |
| drying time | Drying **Slow**, Settle 0 (re-tapered and settled at lift as before, nothing after); the old 0.75–5 s kept as seconds: d = v·√(4250/19250) |
| pressure, speed (±, its way at 0) | Control (Custom): Pressure → size, Speed → size, Fast is thinner/thicker |
| style (Wild → Anything goes) | the six-part style of the same name, seed and name kept |

One deviation from the prototype, measured: the app's old paint laid its body down sooner as the pen leaned (the
prototype's port of it didn't). A paint gets **Tilt → size .1**, which turns that on in the body's width and barely
stretches the dab: mean tone difference on the scripted wave drops from 13–21 to 6–13.

Fidelity (desktop, Java old engine vs converted, the `LegacyGoldenChecks` wave, S/M/L, composite tones):
- the three `I` codes: 0.0–0.1 mean difference, dark area identical (to the pixel at S);
- the two `P` codes: mean 5–17, the shape and tone the same, the old engine's ragged edge band and wet-on-wet joining
  gone (the six-part engine has neither).
PNGs (old | new | difference ×4) in the session scratchpad `onebrush/png/`.

## 2. The built-ins → six-part brushes

Picked by drawing each old built-in and its candidate on the same wave and tilts, then tuning dials until area and
tone match (`onebrush/png/`, `INK-*`, `FUDE-*`, `WASH-*`, `WATERCOLOR-*`). Each becomes a fixed M code, its name the
friendly one:

| Old | New name · style | Parts (what matters) | How close |
|---|---|---|---|
| Ink | **Fineliner** · Fineliner | Round, Ink, Instant, Press → size .35, fast → thinner .4, Weight .97 | area 1.05×, same tone, mean 11–13 (edge anti-aliasing). Lean past 45° no longer thickens 20% or flattens. |
| Fude | **Brush pen** · Brush pen | Round, Ink, Instant, Tilt → size 1, Tilt → angle 1 (the nib turns and flattens with the lean), Press → size .3, Weight .8 | The old Fude went from .12× upright to 4.8× laid low; six parts can only do 1×–2.2×. Matched near a 45–50° hold; thinner upright, broader but less dramatic laid low. **The one to try on the device.** |
| Wash | **Ink wash** · Ink wash | Round, Wash flat (Wet 0), Slow, Press → size 1 (tip to full), Tilt → size .1, Weight .82 | area and tone equal (205 vs 205 at Dark); a smooth edge instead of the ragged band. |
| Watercolor | **Watercolor** · Watercolor | Round, Wash wet, Hollow edge .35, no bloom or grain, Soft .15, Slow, Press → size 1, Press → dark .35, Weight .82 | area within 2%, tone within 4; softer edge, no wet-on-wet joining. |

Names: the bar and library said Ink, Fude, Wash, Watercolor; they now say the style names (the six-part model names a
brush by its look).

## 3. Sizes and greys of converted slots

A slot holding an old brush drew at its preset (S/M/L) and shade. On conversion the slot's own px and darkness
(`ToolSwatches`, kept per slot) are set to what it drew, and its swatch marks move to the nearest shared swatch:

- px: Ink/Fude/Wild pen `inkSize` 3 / 8 / 16; Wash/Watercolor/Wild paint `maxSize` 24 / 120 / 220.
- darkness: pens 1 − ink grey/255 (1, .53, .31: the swatches); Watercolor and Wild paint the old pale shares (1, .55,
  .3); Wash its flat greys 205 / 230 / 245 through the glaze (×1.1, tuned): .63, .31, .13.
- the size swatch nearest on a log scale, the grey swatch nearest. The eraser keeps S/M/L.

## 4. Storage migration

- `Brush` holds only a six-part brush. `Brush.read` reads `M` lines; an `@INK`/`@FUDE`/`@WASH`/`@WATERCOLOR` line is
  that built-in's six-part brush; an `I`/`P` line is converted, keeping its stored name. Anything else reads as null
  (a slot falls back to its default, a library line is dropped), as before: nothing crashes.
- `ToolSlots`, once per launch where needed: any slot line that isn't an `M` line is rewritten as one, its px and
  darkness set as in 3 (before the line, so a cut-short run repeats). `BrushLibrary` rewrites its lines as `M` lines
  the first time it reads an old one. Built-ins become ordinary library brushes: deletable, no longer re-added.
- The pre-slot migration (old Wild tool seeds) stays, now landing as converted brushes.
- Checks: a snapshot of Stage-1/2 prefs (built-ins, I/P slots and library, sizes, shades, a corrupt line, an empty
  slot) migrates to M lines with the expected names, px, darkness and swatch marks; a second read changes nothing.

## 5. Fresh install

Slots and library: **Fineliner, Brush pen, Pencil, Ink wash, Watercolor, Gouache** — three line tools then three
paints, as the old bar's order: the four converted built-ins (so a fresh install and an upgraded one share them), plus
Pencil (graphite HB–B on cold press, Quick: the sketching tool the set lacked) and Gouache (opaque, Quick: light over
dark, which no old tool could do). Charcoal was the other candidate; Pencil is finer, cheaper on the Manta and closer
to the fineliner-and-wash way Elling draws. Sizes and greys: each slot its own style size at the nearest swatch;
greys Dark, but Ink wash Light (≈ the old Wash) and Gouache Lighter (so light-over-dark shows).

## 6. What is removed and what stays

Removed (unreachable once every brush is six-part):
- engines: `InkStroke`, `WildInkStroke`, `WashStroke`, `WashLook`, `WatercolorSettle` (its paper noise moves to
  `PaperNoise`, read by `StrokePaper`/`PaperGrain`, values unchanged); `WatercolorSession`'s wet-wash list (keeps
  the Drying wash); `InkWashSizes.Nib` and the pens' width rules; `BuiltinBrush`, `BrushVariations`, `BrushCaptions`'s
  old-brush half; `BrushStyle`'s ranges and rolls (keeps names, for `I`/`P` style indexes).
- lab and bar: old settings rows, `BrushLab.settings()`/`BrushSetting` locks/slides, built-in copies ("my-ink"),
  old samples and fixed sizes (`WildSample.pixels` for old brushes, `fitting`, `ToolSwatches.custom`), "old brush"
  branches in `LabSettings`, `LabLayout`, `LabLibrary`, `SwatchFaces`, `ToolSlots`, `InkWash`, `InkWashBar`,
  `BrushLabActivity`; Wild-only checks.
- Kept: `WildBrush`, `BrushCode`, `BrushSetting` (to read `I`/`P` codes: pasting an old share code still works, it
  arrives as six parts), `WildBrush.Random` (the engine's generator), `EraseBothStroke`, `RoundFootprint`,
  `WashCoverage`, `PenSpeed`, `InkWashSizes` presets (eraser), `InkWashShades` (conversion). `PressureStroke`'s
  `headThickness` (upstream file) is now unused by Inkwash; left as is.

## 7. Checks

- `LegacyGoldenChecks` and `WashGoldenChecks` pinned the old engines; they go with them. In their place,
  `LegacyConversionChecks`: the five `BrushCodeChecks` codes and the four built-ins convert to fixed `M` codes
  (string goldens, independent of rendering), and their converted strokes (the same wave, S/M/L + Light, plus loop
  samples) hash to recorded CRCs from the six-part engine. Sizes stay under 200 px reach where the perf branch's
  lattice would change pixels; if a perf merge changes them anyway, re-record and say so. The eraser's golden moves to
  `EraseBothChecks` (recorded before removing `WashStroke`, over a six-part wash).
- Rewritten assertions where behaviour changes on purpose (built-ins in the library, old rows in the lab, old
  samples), named in each commit.

## 8. Docs

CHANGELOG "Unreleased"; `docs/INTEGRATION.md` (class map, the one-engine note); the engine spec's and lab spec's
As-built notes; the lab's How it works text.

## Commits

1. This plan. 2. `LegacyBrushes` + defaults + conversion checks (old engines still present, fidelity check against
them). 3. `Brush` six-part only, storage migration, fresh defaults. 4. Lab and bar old branches removed. 5. Old
engines and checks removed, goldens replaced. 6. Docs.
