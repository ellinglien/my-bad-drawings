# Drawing screen C and the Tools page: plan

Spec: `docs/archive/superpowers/specs/2026-10-15-tools-page-design.md`. Branch `inkwash-v2-lab`. One focused commit a task;
checks after each: `scripts/test_inkwash.sh`, `scripts/test_document.sh`, `./gradlew :app:assembleRelease`. No device
(no adb): the owner checks on the Manta and the Nomad.

## Task 1: pure models and their checks

- `DrawLayout`: the bar's fitted widths (preferred/least tables, one fraction), bar and tile heights, the side bar's
  width, faces and contents' height, the page's room; `tap(selected, tool)` (select / Tools page / nothing) and
  `greys(tool)` (not for the eraser). `SwatchFace.tall` and its padding constants move here (pure).
- `ToolPresets`: 13 styles × 5 rows {style, name, M code, description, seed}; `styles()`, `of(style)`.
- `ToolsLayout`: the page's budgets, chip rows (monospace model), columns, card size, library rows, description
  characters, the frame's words fitting.
- `ToolsPage`: the tool as it stands (`roll`, `surprise`, `put`, `result`), the chips' style, each section's cards and
  pages, opening on the pages holding the slot's brush.
- Shared rules: `ToolSlots.rolled` (the slot's dice), `BrushLab.surprise(list, current, random)` (never the current),
  `BrushCaptions.oneLine`. `ToolSlots.recent()`/`RECENT` go (the panel was their only user).
- Checks: `DrawLayoutChecks` (all four screens at 1–4 layers fit, never under the least, the Manta portrait with one
  layer is the mockup's, the side bar fits the page height, taps), `ToolPresetsChecks` (decode, style, round trip,
  draws, unique, descriptions fit), `ToolsPageChecks` (sections, paging, chips, opening pages, roll/surprise/put/result,
  layout budgets, words fitting). Add to `test_inkwash.sh`.

## Task 2: the left edge (`SwatchBar`) and the bar's fit

- `SwatchBar`: a vertical strip, SIZE + 3 faces, rule, GREY + 3 faces, a 3 dp ink rule on the page's side; one
  `SwatchFaces` (greys hidden, not removed, for the eraser); `show(tool)` through `SelectionFeedback` over its square.
  `SwatchFaces.show` skips greys for the eraser.
- `InkWashBar`: the tool panel, Roll/Save/recent/Brush Lab rows and `BrushTile` go; `choose` follows `DrawLayout.tap`;
  selecting a tool or changing a slot shows it on the side bar; widths from `DrawLayout` on a width or layer-count
  change, grey/ink rules between groups; the size modal closes when the pen lands.
- `PaintActivity.showInkWashBar`: bar on top at `DrawLayout.bar`, the side bar in `body` where MonoPaint's column was
  (left, or right for `toolboxRight`), the pad beside it, in portrait and landscape.

## Task 3: the Tools page

- `ToolsActivity` (manifest: portrait, as Brush Lab; turned by a `QuarterTurnLayout` with the drawing screen's turn):
  bar, frame, PRESETS/RECENT/YOUR LIBRARY from `ToolsPage` and `ToolsLayout`; `LoopCard`s; `SampleQueue`; only what
  changed redraws (frame on roll/surprise; a section on its page or chip).
- `PaintActivity.openTools(slot)`; result: the brush (one Undo step via `ToolSlots.use`, named under the bar) and
  whether to open Brush Lab next.

## Task 4: docs

CHANGELOG (Unreleased), the lab spec's As built (the toolbar section: the slot panel is gone), `docs/INTEGRATION.md`
(new classes; `PaintActivity` and `SwatchFace` lines), this spec's As built with measured numbers and deviations.
