# Drawing screen C and the Tools page (design)

The owner's approved mockups (Part Card Names canvas): **MainC** ("Drawing screen · C: sizes and greys on the left
edge") and **ToolsPage** ("Tools page (tap the selected slot again)"). Style: `Theme` (Inkwash Design Style, `docs/DESIGN.md`: six greys,
iA Writer Duo (Roboto Mono until 2026-10-16), square, ≥ 2 dp strokes, ≥ 13 sp, caps tracked labels, ink-filled selection, no shadows). Branch
`inkwash-v2-lab`. Plan: `docs/archive/superpowers/plans/2026-10-15-tools-page.md`.

## What changes

- **No slot dropdown.** The selected tool's three sizes and three greys are always on the drawing screen's left edge,
  in a narrow bar: SIZE and its three faces, a rule, GREY and its three faces (the eraser: sizes only). The face in
  use has the tab under it; a tap picks one; the ▾ corner (or a long press) edits it in the existing size modal or
  grey flyout. Same `SwatchFaces`/`SwatchFace`/`ToolSwatches` as Brush Lab's head row: no second swatch code.
- **Tapping the selected slot again opens the Tools page.** Another slot or the eraser: selects it. The selected eraser
  again: nothing (its sizes are already on the left). Layer thumbnails keep their dropdown panel, unchanged.
- **Gone from the drawing screen:** the slot panel's Roll, Save, the four recent brushes and Brush Lab. Save is the
  lab's; Roll, recent brushes, the library and Brush Lab are the Tools page's.
- **Wet indicator** stays at the end of the bar. The bar now fits its items to its width (below), which also ends the
  Nomad-portrait overflow with four layers.

## Drawing screen

```
┌ bar ─────────────────────────────────────────────────────────────────┐
│ ☰│↶│↷│  [1][2][3][4][5][6][⌫]        ‖[+▲]│[th][th]…│[+▼] 💧          │
├──────┬───────────────────────────────────────────────────────────────┤
│ SIZE │                                                               │
│  •   │                                                               │
│  ●   │                    the page                                   │
│  ⬤▬ │                                                               │
│ ──── │                                                               │
│ GREY │                                                               │
│  ■   │                                                               │
│  ■▬  │                                                               │
│  □   │                                                               │
└──────┴───────────────────────────────────────────────────────────────┘
```

- **Placement reuses MonoPaint's machinery.** MonoPaint keeps its tool column in `PaintActivity.body`, a row beside
  the pad, on the drawing hand's side (`toolboxRight`), and `CanvasViewport` already opens a page at 100% with
  "paper under the bars" on that side. Inkwash hides MonoPaint's column; the side bar goes in its place (left unless
  `toolboxRight`). The pad is just narrower: page ↔ view mapping, pen input, undo and the e-ink connection follow
  the pad's place as they did with MonoPaint's column. In landscape Inkwash keeps the same arrangement (bar on top,
  side bar beside the pad), not MonoPaint's turned header.
- **New pages** are the pad's size (as today), so they lose the side bar's width. **Older pages** (made full width)
  open at 100% with their left strip under the side bar (MonoPaint's rule: the page fits the screen, so it opens
  actual size). Inkwash has no pan, so that strip can't be drawn on until the page is exported; see Risks.
- **Bar fitting** (`DrawLayout`, pure): menu, undo, redo, a gap, six slot tiles, the eraser's tile, a stretch, add
  above, a thumbnail a layer, add below, the wet indicator; 2 dp rules (grey after each of menu/undo/redo, ink before
  the layers, grey either side of the thumbnails), 4 dp at each end. Each item has a preferred width (Manta: the
  mockup's icon 56, slot 84, gap 10, eraser 64, thumbnail 54, wet 56; Nomad: today's 48, 72, 12, 48, 48, 48) and a
  least (40, 44, 0, 40, 36, 40). When they don't fit, all shrink together by one fraction, for the layers there are;
  a layer added or removed refits the bar (rare, and it redraws the thumbnails anyway).
- **Side bar:** Manta 76 dp wide (with its 3 dp ink rule), faces 56 dp square (views 62 × 68); Nomad 62, faces 44
  (50 × 56). Padding 12, labels 14 sp caps (a 23 dp line), the rule 3 dp with 10 dp margins. A change redraws only
  the faces that changed, through `SelectionFeedback` over the side bar's square (e-ink).

### Budgets (dp; screen W × H; bar height; slot tile; side bar; page)

| | Manta portrait | Manta landscape | Nomad portrait | Nomad landscape |
|---|---|---|---|---|
| Screen | 1024 × 1365 | 1365 × 1024 | 749 × 998 | 998 × 749 |
| Bar / tiles | 76 / 72 | 76 / 72 | 56 / 52 | 56 / 52 |
| 1 layer: icon, slot, gap, eraser, thumb, wet; stretch | 56, 84, 10, 64, 54, 56; 36 | same; 377 | 44, 60, 7, 44, 43, 44; 11 | 48, 72, 12, 48, 48, 48; 150 |
| 4 layers (most) | 51, 72, 7, 57, 48, 51; 10 | 56, 84, 10, 64, 54, 56; 215 | 41, 48, 1, 41, 37, 41; 5 | 48, 72, 12, 48, 48, 48; 6 |
| Side bar; its contents' height | 76; 517 | 76; 517 | 62; 445 | 62; 445 |
| Page room | 948 × 1289 | 1289 × 948 | 687 × 942 | 936 × 693 |

Today's bar overflowed the Nomad in portrait from two layers on (it needed 848 dp with square slots).

## Tools page

A full-screen Activity like Brush Lab (`ToolsActivity`), turned with the drawing screen (a `QuarterTurnLayout`, so it
works in landscape too). Back (system or the arrow) returns to the drawing.

```
┌ ← Tools for slot 2                         [⚄ SURPRISE ME] [■ BRUSH LAB] ┐
├───────────────────────────────────────────────────────────────────────────┤
│ ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓ │
│ ┃ [sample]  name · Style                               [⚄ ROLL ANOTHER] ┃ │
│ ┃           plain words, whole sentences                                ┃ │
│ ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛ │
│ ▓ PRESETS                                                    ‹ 1/1 › ▓  │
│ │All│Fineliner│Brush pen│…│Hake│   (chips, the chosen one ink-filled)      │
│ [card][card][card][card][card][card]                                      │
│ ▓ RECENT ▓   [card]…                                                      │
│ ▓ YOUR LIBRARY                                               ‹ 1/3 › ▓  │
│ [card]… rows                                                              │
└───────────────────────────────────────────────────────────────────────────┘
```

- **Bar** (56 with its 3 dp rule): back, "Tools for slot N" (heading 20 sp), **Surprise me** (outlined, dice: a random
  library brush, never the current one; `BrushLab.surprise`'s rule, now one static helper both use) and **Brush Lab**
  (ink-filled, flask: opens the lab on this slot with the tool as it stands).
- **Current tool** in a 4 dp ink frame: the loop sample in the slot's grey, sized to its 2 dp grey-400 box, "name ·
  Style", the plain words (`ModularWords.plainWords`, whole sentences that fit, at least the first), **Roll another**
  (outlined, dice: a new brush in its style, the slot's dice `ToolSlots.rolled`). Roll and Surprise change the tool
  shown here and stay on the page; Back takes it to the slot.
- **Sections** under ink bars (14 sp caps, on-ink), each a grid in a 2 dp ink border with grey-400 lines:
  **PRESETS** (chips All + the 13 styles, wrapping; the brush's style chosen at first, All for Anything goes; one row
  of that style's presets, paged with ‹ › in the bar when more), **RECENT** (the library's most recently used, one
  row), **YOUR LIBRARY** (every library brush, newest first, the rows left, paged; opens on the page holding the slot's
  brush).
- **Cards** (`LoopCard`, Brush Lab's): the loop sample at full darkness (what the brush lays on its own, as the lab's
  library), a grey-400 rule, the name (13 sp 600), one line of description in grey-800 (a preset's own; else the
  brush's caption items, comma-joined while they fit one line). The tool as it stands is outlined 4 dp. Samples draw
  on `SampleQueue`'s background thread into `WildSample`'s cache; a card is white until its sample arrives.
- **A tap on a card** puts it in the slot and closes the page: the drawing screen puts it in with `ToolSlots.use`, one
  Undo step there, as the old panel's recent brushes did (its size follows the brush's family as before). Back after
  Roll or Surprise does the same with that brush. Brush Lab does the same, then opens the lab.

### Presets

`ToolPresets` (pure): five per style, 13 styles, 65 in all, each a share code rolled from the style with a fixed seed
(kept beside it), renamed with a friendly name and a description of at most 14 characters (one line on the Nomad's
narrowest card). The codes are the presets: changing how styles roll changes none. Checks: every code decodes, is its
row's style, round-trips, draws a non-blank sample, names are unique, descriptions fit.

### Budgets (dp)

| | Manta portrait | Manta landscape | Nomad portrait | Nomad landscape |
|---|---|---|---|---|
| Padding side / gap | 12 / 16 | 12 / 16 | 10 / 12 | 10 / 12 |
| Frame (contents 110, sample box) | 142 (180 × 110) | 142 | 138 (140 × 86) | 138 |
| Chip rows (36 each) | 2 | 1 | 2 | 2 |
| Columns × card | 6 × 164 × 148 | 8 × 165 × 148 | 5 × 143 × 111 | 6 × 160 × 104 |
| Library rows (cards a page) | 4 (24) | 2 (16) | 3 (15) | 1 (6) |
| Description chars / line | 16 | 16 | 14 | 16 |
| Title room / words room | 588 / 572 | 929 / 913 | 317 / 349 | 566 / 598 |

Rules (`ToolsLayout`, pure): as many columns as cards at least 142 dp wide fit (3 to 8); the three grids' rows share
what the rest leaves, as many as fit at 132 (Nomad 108) dp, cards then as tall as that allows up to 150 (128); the
library gets all but two rows, at least one. The name takes one line on the Manta, up to two on the Nomad; the words take the lines left
(three under one name line).

## Reused vs new

| Reused | New |
|---|---|
| `SwatchFaces`, `SwatchFace`, `SizeModal`, `ToolSwatches`, `SlotTile`, `LoopCard`, `SampleQueue`, `WildSample`, `LabButton`, `LabUi`, `LabPages`, `LabText`, `ModularWords`, `BrushCaptions`, `BrushLibrary`, `ToolSlots` (dice now `rolled`, shared), `BrushLab` (`surprise` rule shared), `WetIndicator`, `InkWashBar` (panels for layers only), `PaintActivity`'s `body` row | `DrawLayout` (pure: bar fit, side bar, taps), `SwatchBar` (the left edge), `ToolPresets` (pure), `ToolsPage` (pure: sections, paging, picks), `ToolsLayout` (pure), `ToolsActivity` |

## Out of scope

Deleting library brushes from the Tools page (Brush Lab's long-press stays the place); a search; animated
previews; moving the bar to the left; panning to reach an old page's covered strip.

## Risks

- **Old full-width drawings**: their left 76 dp (Manta) / 62 dp (Nomad) sits under the side bar and can't be drawn on.
  New drawings fit. If it matters, a later change could open such pages fitted instead (`CanvasViewport`, upstream).
- **Nomad portrait with four layers**: slots 48, icons 41, thumbnails 37 dp: check they are easy to hit.
- **Chip wrapping** is budgeted with the monospace model; a device wrapping one row more squeezes the library a
  little (its grid takes what is left), never off the screen.

## As built (2026-10-15)

Commits `3951dc8` (models, presets, checks), `4fe9dde` (Brush Lab's grid and sampler shared), `2d75ce6` (the left
edge, the bar's fit), `bbd01d5` (the Tools page), `09312b7` (Nomad row height), `192ec36` (the left edge follows
Brush Lab's changes), then these docs. Checks:
`test_inkwash.sh` 47 PASS (new: `DrawLayoutChecks`, `ToolPresetsChecks`, `ToolsPageChecks`), `test_document.sh` 82
PASS; `assembleRelease` builds. No device run (no adb). The budgets above are `DrawLayout`'s and `ToolsLayout`'s, as
checked.

Where it differs from the mockups and this spec:

- **Bar order:** the add-layer buttons stay either side of the thumbnails (above, thumbnails, below), as before; the
  mockup puts both before them. The slot and eraser tiles keep `SlotTile`'s ink top and bottom rules (shared with the
  lab's TOOLBAR row); the mockup's slots have only their ends.
- **Chips** are 13 sp (the caption style, 600), not the mockup's 14 px, as Brush Lab's chips.
- **Roll another and Surprise me stay on the page** (the frame shows the new tool; Back puts it in the slot). Only a
  card closes the page at once.
- **Card samples** are at full darkness (what the brush lays on its own, as Brush Lab's library); the frame's sample is
  in the slot's grey.
- **Tap the selected eraser** does nothing (its sizes are already on the left).
- **The size modal or grey flyout** closes when the pen lands anywhere on the drawing screen (as the slot panel did).
- **Code:** `SwatchFace.tall` and its padding moved to `DrawLayout` (pure); `SwatchFaces` takes the app's turn on each
  layout (the left edge lives across rotations) and shows no greys for the eraser; Brush Lab's grid (`LabUi.grid`,
  `cell`, `gridRow`) and card sampler (`LabLibrary.sampler`) are shared; `ToolSlots.recent()`/`RECENT` went with the
  panel; the dice (`ToolSlots.rolled`) and Surprise's rule (`BrushLab.surprise(list, current, random)`) are shared.
- **Upstream files:** `PaintActivity` (the arrangement in `showInkWashBar`, `openTools`/`toolsReturned`),
  `DrawingPad` (one line: a full refresh redraws both bars), the manifest (`ToolsActivity`). See
  `docs/INTEGRATION.md`.

### Device checks for Elling

1. Manta and Nomad, portrait and landscape: the bar fits with one to four layers (Nomad portrait with four: slots
   48 dp, icons 41, thumbnails 37 wide; easy enough to hit?); the wet droplet at its end.
2. The left edge: SIZE and GREY faces; a tap picks (the tab moves, the slot's tile redraws); the ▾ corner or a long
   press opens the size modal or grey flyout; the eraser shows only its sizes; no full-screen flash on a change.
3. Drawing beside the left edge: strokes land under the pen, undo and the eraser work, the page isn't shifted.
   **An older drawing** (made before this build, full width) opens with its left 76 dp (Nomad 62) under the side bar:
   is that acceptable, or should such pages open fitted?
4. Tap the selected slot: the Tools page. Chips change the presets; pages turn; a card goes in the slot and the
   drawing returns with its name under the bar; Undo takes it back. Roll another, Surprise me, then Back. Brush Lab
   from the page opens on that slot with the tool shown.
5. Samples on the Tools page arrive in one or two steps without blinking; the frame's words fit (Nomad: long names
   wrap to two lines).
6. Landscape: the Tools page opens turned with the drawing.

## One side toolbar (as built, 2026-10-15)

The owner, after design C: "let's merge the top toolbar to the side bar... just one side toolbar (since it has more
room)", "one narrower column", "put a link to the lab in the toolbar as well"; older drawings whose left strip sits under
the column are accepted. Reference mockup: Part Card Names canvas, **SideBar** (two columns; built as one).

```
┌────┬──────────────────────────────────┐
│ ☰  │                                  │
│ ■  │ Brush Lab (ink, flask)           │
│ ↶  │                                  │
│ ↷  │                                  │
│[1] │ the six slots (SlotTile,         │
│[2] │   stacked), then the eraser      │
│ …  │                                  │
│[⌫] │                                  │
│ •  │ sizes ×3                         │
│ ●  │                                  │
│ ⬤  │               the page           │
│ ── │                                  │
│ ■  │ greys ×3 (the eraser: hidden,    │
│ ■  │   keeping their place)           │
│ □  │                                  │
│ ━━ │                                  │
│ +▲ │ layers (folded: one button)      │
│ th │                                  │
│ +▼ │                                  │
│    │                                  │
│ 💧 │ wet indicator                    │
└────┴──────────────────────────────────┘
```

- **No top bar.** One column (`InkWashBar`, a vertical `LinearLayout`) in `PaintActivity.body` where design C's side
  bar was (MonoPaint's tool column place, the drawing hand's side), the pad beside it, the whole height. Page ↔ view
  mapping, pen input, undo, zoom and the e-ink connection follow the pad as before (no change in `DrawingPad` or
  `CanvasViewport`).
- **Order:** menu, Brush Lab (`LabButton`, ink-filled flask, inset 6/4 dp; opens the lab on the selected slot, or with
  the eraser selected the slot last selected: `DrawLayout.labSlot`), undo, redo (2 dp grey rules between the four), a
  gap, the six slots and the eraser (`SlotTile` in a column: ink sides, an ink top on the first, grey-400 dividers
  between, the badge on the left rule), a gap, the faces (`SwatchBar`: sizes, a 2 dp grey rule, greys), a gap, a 2 dp
  ink rule, the layers (add above, a grey rule, a thumbnail a layer topmost first, a grey rule, add below), a stretch,
  the wet indicator.
- **Fit** (`DrawLayout`, pure, the bar's fit turned on its side): each item has a preferred height (Manta: icon 56,
  gap 10, slot 64, face 68, thumbnail 56, droplet 48; Nomad 48, 8, 52, 56, 48, 40) and a least (40, 0, 40, 36, 40, 32);
  when they don't fit, all shrink together by one fraction. When even the least don't fit, the layers **fold**: one
  button (the current layer's thumbnail, framed, a second outline behind it when there are more) whose list opens
  beside it, holding add above, every thumbnail and add below at their preferred heights; a thumbnail there selects,
  the selected one's panel opens beside it. On a screen shorter than any of the four everything shrinks alike below
  its least rather than overflow.
- **Labels** (TOOLS, SIZE, GREY, LAYERS): only when every item fits at its preferred height with them for the most
  layers (so they never come and go with a layer), and LAYERS fits across the column. Neither holds on the four
  screens, so none show today; thin rules part the groups instead.
- **Width:** Manta 72 dp, Nomad 58, no rule beside the page (it was 80 and 64 with a 3 dp ink rule until the owner asked for narrower and no line): the faces' squares and the slots' loops
  read at that, and the widest thing that must fit is an icon or a 40 dp target.
- **Panels open beside** their owner, toward the page, joined like a tab along the near side (`TabPanel` now opens
  sideways); the file menu opens beside the menu button (`PaintActivity.menu`); a brush's name shows beside its slot.

### Budgets (dp; `DrawLayout`, as checked)

| | Manta portrait | Manta landscape | Nomad portrait | Nomad landscape |
|---|---|---|---|---|
| Screen | 1024 × 1365 | 1365 × 1024 | 749 × 998 | 998 × 749 |
| Column | 80 | 80 | 64 | 64 |
| 1 layer: icon, slot, face (square), thumb, droplet | 56, 64, 68 (56), 56, 48 | 45, 48, 47 (35), 45, 37 | 44, 46, 47 (35), 44, 36 | folded: 40, 40, 36 (24), 40, 32 |
| 4 layers | 51, 56, 59 (47), 51, 43 | 42, 42, 40 (28), 42, 34 | 41, 40, 39 (27), 41, 33 | folded: same |
| Layers fold | never | never | never | always (unfolded least: 830 > 749) |
| Page | 944 × 1365 | 1285 × 1024 | 685 × 998 | 934 × 749 |
| Design C's page | 948 × 1289 | 1289 × 948 | 687 × 942 | 936 × 693 |

The page gains the old bar's height (76 Manta, 56 Nomad) and loses 4 dp (Manta) or 2 (Nomad) of width.

### Differences from the request and the mockup

- One column, not the mockup's two; no section labels (they don't fit, above).
- The eraser's tile keeps `SlotTile`'s icon tile, not the mockup's "ERASER" row.
- Nomad landscape always folds its layers (even one layer doesn't fit unfolded at the least sizes). Faces go to 36 dp
  (a 24 dp square) and the droplet to 32 there, under the 40 dp aim, to fit without scrolling.
- Older drawings: design C's pages (948 dp wide on the Manta) open at 100% with 4 dp under the column; pages from
  before C with 80 (Nomad 64) under it. Accepted by the owner.

### Upstream files

`PaintActivity`: `showInkWashBar` puts the one column in `body` (no top bar); `menu` opens the file menu beside its
anchor instead of below it. See `docs/INTEGRATION.md`.

### Device checks for Elling

1. Manta and Nomad, portrait and landscape: the column fits top to bottom with one to four layers, nothing cut off;
   the droplet at the foot.
2. Nomad landscape: the Layers button shows the current layer; tap it: the list beside it (add above, the layers,
   add below); tap a layer to select it, again for its panel; drawing on the page closes them.
3. Brush Lab button opens the lab on the selected slot (with the eraser selected: the slot last used).
4. Drawing right beside the column: strokes land under the pen, undo, zoom and the eraser work.
5. Slots, faces and thumbnails at the smallest sizes (Manta landscape with four layers, Nomad): easy enough to hit?
6. Menu, layer panel and a brush's name open beside the column, not over it; no full-screen flash on a tap.

## Nomad pass (as built, 2026-10-15)

Seen at the Nomad's size on the Manta (`ScreenShots`, the bench build). The current tool's frame could cut its words:
"whole sentences that fit, at least the first" let the first sentence run past the frame when the title took two lines
(a name of 15+ characters beside a long style on the Nomad's 349 dp: about one brush in five there).

- `ToolsLayout` sizes the frame's text for the longest title (a 32-character name and the longest style, two lines on
  the Manta's and the Nomad's portrait) and the longest first sentence (`FIRST_SENTENCE`, 80 characters; checked over
  every style's descriptions and the presets), never less than the mockup's 110: Manta portrait 118, Nomad portrait
  142, Nomad landscape 118, Manta landscape 110.
- To keep the Nomad's three library rows, its cards' good height is 104 (was 108): cards 143 × 105 there (were 111);
  Manta portrait 164 × 147 (were 148). `ToolsPageChecks` proves the longest title and first sentence fit on all four
  screens.
- The sections' bars read "Presets", "Recent" and "Your library", worded as Brush Lab's bars ("Tool library").
- The drawing screen's toolbar at the Nomad's size (portrait and landscape, one and four layers) fits as `DrawLayout`
  budgets. One fault found: the folded Layers button (the Nomad in landscape) came back blank after turning to portrait
  and back, as did a layer thumbnail put back at its old size: a view re-attached at its old size gets no size change,
  so its picture, dropped on detach, was never drawn again. `InkWashBar.LayerThumb` draws it again on attach.

## A test area to try tools (as built, 2026-10-16)

The owner: "main library page should have a test area"; then "focus on simplicity.. too much will confuse. use white
space to differentiate sections".

```
portrait                                              landscape
┌ ← Tools for slot 2        [↶ UNDO] [⚄ SURPRISE ME] [■ BRUSH LAB] ┐   ┌ ← Tools for slot 2       [↶] [⚄ …] [■ …] ┐
├────────────────────────────────────────────────────────────────┤   ├─────────────────────────────────────────┤
│ name · Style      [CLEAR] 💧                 [⚄ ROLL ANOTHER]   │   │ name · Style       │ ▓ Presets  ‹ › ▓   │
│ plain words,      ┌──────────────────────────────── [loop] ┐    │   │ plain words        │ chips              │
│ whole sentences   │  test area: the pen draws with the     │    │   │ [CLEAR] 💧 [⚄ ROLL]│ [card]…            │
│                   │  tool at the slot's size and grey      │    │   │ ┌──────── [loop] ┐ │ ▓ Recent ▓         │
│                   └────────────────────────────────────────┘    │   │ │  test area     │ │ [card]…            │
│                         (white space)                           │   │ │                │ │ ▓ Your library ‹ ›▓│
│ ▓ Presets ‹ 1/2 › ▓  chips, a row of cards                      │   │ └────────────────┘ │ [card]… rows       │
│ ▓ Recent ▓  a row        ▓ Your library ‹ › ▓  rows             │   └─────────────────────────────────────────┘
```

- **A card stays on the page.** Tapping a preset, recent or library card makes it the tool here (outlined 4 dp, as the
  tool as it stands always was) and the test area draws with it; Roll another and Surprise me do the same. **Undo**
  (in the bar, outlined, greyed with nothing to take back; icon only on the Nomad, as Brush Lab's) takes each pick,
  roll or surprise back in turn, to the slot's own tool. **Back** takes the tool to the slot, one Undo step on the
  drawing screen as before; Brush Lab does the same, then opens the lab.
- **The test area is Brush Lab's** (`TestArea`: `BrushTestPad` and its quick and fast-panel paths, the drawing screen's
  strokes, its own Watercolor session, the tilt feed for Gravity). The pen draws at the slot's size and grey, the size
  following the tool as the slot's will when it takes it (`ToolSwatches.lookAfter`, the rule `brushChanged` applies:
  a Watercolor tried in the Fineliner's slot draws at its own medium size, not a 4 px line); nothing is written until
  Back. The pen's eraser end erases. What is drawn stays when the tool changes; **Clear** (top left, over the test area
  as Brush Lab's) clears it; the wet drop beside it shows while a slow wash settles (a tap lays it).
- **The current tool's heavy frame and its sample box went:** the test area's corner box shows the tool's loop in the
  slot's grey (as Brush Lab's), so the frame's job is the test area's. The name and the plain words sit beside it in
  portrait, over it in landscape (whole sentences, at least the first, at most four lines; the longest title and first
  sentence always fit). **Roll another** moved to the head row over the test area, at its right.
- **White space, not rules:** a blank twice the gap (32 dp Manta, 24 Nomad) parts the tool from the cards (under the
  band in portrait, beside the column in landscape); no new bar, label or box.
- **Layout** (`ToolsLayout`): portrait, the tool is a band across the top, the words `textWidth` wide (300 Manta, 250
  Nomad) beside the head row and test area; the cards keep a good height (132 / 104) and the library takes the rows
  left after a test area of at least 400 (Nomad 280) dp. Landscape, the sections take three fifths of the width in
  whole cards at their least (142 dp), the tool the rest; the words as tall as the longest title and first sentence
  need; the cards as tall as fits (up to 150 / 128).

### Budgets (dp; `ToolsLayout`, as checked)

| | Manta portrait | Manta landscape | Nomad portrait | Nomad landscape |
|---|---|---|---|---|
| Test area (its frame) | **684 × 435** | **587 × 742** | **467 × 320** | **376 × 459** |
| Words | 300 wide beside | 587 × 118 over | 250 wide beside | 376 × 142 over |
| Columns × card | 6 × 164 × 132 | 5 × 142 × 142 | 5 × 143 × 104 | 4 × 142 × 106 |
| Chip rows | 2 | 2 | 2 | 3 |
| Library rows (cards a page) | 2 (12) | 3 (15) | 1 (5) | 2 (8) |
| Was | 4 (24) | 2 (16) | 3 (15) | 1 (6) |

`ToolsPageChecks` proves on all four: the heights and widths add up to the screen (nothing scrolls), the test area at
least 360 × 300, Clear, the drop and Roll another across its head row, the title beside the bar's buttons (Undo too),
every preset card's name and line whole at 13 sp, the longest title and first sentence whole; and the behaviour:
picks stay, Undo takes back picks and rolls to the slot's own tool, nothing is written while trying, and the look a
tool is tried at is the one the slot takes with it.

## Tabs, one grid, and a focus pass (as built, 2026-10-16)

The owner: "instead of 'presets' would 'basic tools' be better. is there a way to have the type filter work for all on
that library page? and another tab for basic tools, recent, or saved, or full library? instead of the sections"; then
"simplify the tools page to make it more focused, give hierarchy to focus attention on the most important thing" (the
cards to pick from, and trying the chosen one).

```
portrait                                              landscape
┌ ← TOOLS FOR SLOT 2 (grey)           [↶ UNDO] [■ BRUSH LAB] ┐   ┌ ← TOOLS FOR SLOT 2       [↶] [■ BRUSH LAB] ┐
│                                                            │   │ name · Style      │ [Basic|Recent|Saved|All] ‹ 1/3 ›
│ name · Style (the heading)        [⚄ ROLL ANOTHER] [CLEAR] │   │ grey words        │ All Fineliner Brush pen …   │
│ grey words, a line or two                                  │   │ [CLEAR] 💧    [⚄] │                             │
│ ┌─────────────────────────────────────────────── [loop] ┐ │   │ ┌──────── [loop] ┐ │ [card][card][card][card]…   │
│ │ test area, the full width                             │ │   │ │ test area      │ │ rows                        │
│ └────────────────────────────────────────────────────────┘ │   │ └────────────────┘ │                             │
│                        (white space)                       │   └──────────────────────────────────────────────┘
│ [Basic tools|Recent|Saved|All]                    ‹ 1/3 ›  │
│ All Fineliner Brush pen Dip pen … (plain words, one filled)│
│ [card][card][card][card][card][card]  rows, one grid       │
```

- **One grid, four tabs** (`ToolsPage.Tab`): **Basic tools** (the presets, renamed wherever the app says it; the code
  keeps `ToolPresets`), **Recent** (`ToolSlots.recent`: every brush put in a slot, latest first, each once, 48 at most,
  Basic tools and rolls too; before any was kept, the library's most recently used), **Saved** (the library's brushes
  that aren't Basic tools, newest first; a saved copy of a Basic tool stays a Basic tool, `ToolPresets.find`), **All**
  (both, each once, style by style: a style's Basic tools, then its saved brushes). The grid opens on the page holding
  the slot's tool; a tab or a type starts from its first page otherwise.
- **The type chips** (All, the 13 styles, Anything goes) filter whichever tab is open by the brush's style; a tab shows
  only the chips it has brushes of (hidden, not greyed), and the chosen one always, so a type a tab has nothing of
  shows a short line in the grid's place ("No Hake in Saved", "Nothing in Saved yet").
- **Kept:** the tab and the type, under `tools_tab` and `tools_style` (the app's preferences), across slots and
  visits. The first time: Basic tools and the slot brush's style (All for Anything goes).
- **Focus pass:** the brush's name is the page's one heading (20 sp); its words are small and grey (13 sp, grey-800, a
  line or two: whole sentences, at least the first); the bar's title is a grey caps label. The bar keeps Back, Undo
  (its icon only on the Nomad) and Brush Lab. **Surprise me went:** the cards are now the way to pick a brush you
  have, while Roll another makes one no card holds, so the dice that stays is Roll another, beside the name in portrait
  (icon only on the Nomad). The tabs are Brush Lab's Basic | Advanced switch (a 2 dp outline, the open one
  ink-filled, 40 dp); the chips are plain 13 sp words 32 dp tall, the chosen one ink-filled, no box or lines. No ink
  section bars and no rule under the bar: white space parts the tool (32 / 24 dp), the tabs and chips (8 / 6 under
  the tabs), and the grid (16 / 12 over it), which is closed on all four sides now.
- **Portrait** puts the name, words and buttons in a head row over a full-width test area; the head row is as tall
  as the longest title and first sentence need, its contents at its foot (so the name sits on the test area it is
  tried on, any spare above it).
- **Long-press delete** isn't on the Tools page: the old library section never had it (Brush Lab's library keeps it).

### Budgets (dp; `ToolsLayout`, as checked)

| | Manta portrait | Manta landscape | Nomad portrait | Nomad landscape |
|---|---|---|---|---|
| Test area (its frame) | **1000 × 320** | 587 × 751 | **729 × 225** | 376 × 472 |
| Was | 684 × 435 | 587 × 742 | 467 × 320 | 376 × 459 |
| Name and words | 648 × 110 beside the buttons | 587 × 110 over | 553 × 110 beside | 376 × 130 over |
| Columns × rows, card | 6 × 5, 164 × 132 | 5 × 6, 142 × 132 | 5 × 4, 143 × 104 | 4 × 4, 142 × 126 |
| Chip rows (every chip) | 2 | 2 | 2 | 3 |
| **Cards a page** | **30** | **30** | **20** | **16** |
| Was (shown at once; the library's a page) | 24 (12) | 25 (15) | 15 (5) | 16 (8) |

`ToolsPageChecks` proves the tabs' contents (Basic tools in their order; Saved without Basic tools, newest first;
All each once, style by style; Recent latest first, each once, at most 48), the type across every tab and only the
chips a tab has, the empty line, the remembered tab and type, paging and the page holding the tool, and on all four
screens: the heights and widths add up (nothing scrolls), the tabs and pages fit across, chips smaller than tabs,
text at least 12 sp, every Basic tool's card whole at 13 sp, the longest title and first sentence whole, the title
beside the bar's buttons.

Pictures (bench build, `ScreenShots`, laid out off the screen at each size so the panel may sleep): every tab, All
through Watercolor, an empty Saved, a card tried and undone, at the Manta's and the Nomad's sizes in both turns.

### iA Writer Duo (2026-10-16)

The face is now iA Writer Duo, measured as duospace (`LabText`, `docs/DESIGN.md`). A card's one-line description is
cut by width, not by characters (`ToolsLayout.lineRoom`, `BrushCaptions.oneLine` with a measure). Chips have 9 dp
padding either side (was 10), so Manta landscape keeps two chip rows ("Watercolour" is wider now) and 6 rows of cards.
Test areas grew a dp (a line is 1.3 em, was 1.32): 1000 × 320, 587 × 751, 729 × 225, 376 × 472; cards and cards a
page unchanged. Six Basic tools' lines held an m or a w that pushed them past the Nomad's 132 dp card line and were
reworded within the text style: Quick sketch and Round marker "thins at speed" (was "thin when fast"), Lively liner
"swell, grainy" ("swells, grainy"), Pooling wash "runs downhill" ("pools downhill"), Crumpled and Scumble
"on crumpled" ("crumpled paper").

### The loop beside the name (2026-10-16)

As Brush Lab's bar (the owner: "link the brush name at the top with the preview loop"): the tool's loop, 45 × 48 dp in
the slot's grey (`TestArea.loop`, `LabLayout.NAME_LOOP_*`), sits directly left of its name, level with the name's top,
the words under the name; the test area's corner box is gone, so the whole test page takes the pen. The words' room
is what the loop and its 8 dp gap leave (`ToolsLayout.wordsRoom`); the text block is at least the loop's height. Test
areas: 1000 × 320, 587 × 751, 729 × 225 unchanged; the Nomad's landscape column 376 × 440 (was 472: its words wrap a
line more beside the loop), the corner box it lost no longer covering any of it. `ToolsPageChecks` pins the sizes.

## The smudge tool and the finger tool (as built, 2026-10-18)

The owner: "smudge as a separate tool on the drawing screen: a fixed tool tile like the eraser … its own three sizes …
tapping the selected smudge tile again opens a small chooser"; and "a setting for what one finger does on the page:
Nothing · Eraser · Smudge · Slot 1–6 … two fingers keep pinch/pan/zoom … in the drawing screen's menu".

```
│[5] │
│[6] │
│[⌫] │ the eraser
│[~] │ the smudge tool: its brush's loop over a grey band, no number   ┌──────────┬──────────┐
│ •  │ its sizes (no greys, as the eraser)       tap it again:         │ [loop]   │ [loop]   │
│ ●  │                                                                 │ Stump    │ Clear    │
│ ⬤  │                                                                 │ blends   │ water    │
                                                                       └──────────┴──────────┘
```

- **The smudge tool** is the column's eighth tool tile, under the eraser's (`ToolSlots.SMUDGE`, `InkWash.SMUDGE`). It
  holds one smudge brush of its own (`tool_smudge`; at first the Blend stump Basic tool, `ToolSlots.SMUDGE_FIRST`, the one
  name to change when a better blending stump lands) and draws it like a slot's brush, at its own size from the three
  shared size swatches (the ▾ sets one, as a slot's); no greys, as the eraser. Its tile is a `SlotTile` with its brush's
  loop (a smudge brush's samples show over a grey band) and no number.
- **Tap the selected smudge tile again:** a chooser opens beside it, joined like a layer's panel: the Tools page's cards
  (`LoopCard`, `ToolsPage.card`) for the **smudge brushes**, the Basic tools whose Smudge part isn't Off and whose Own
  paint is under .5 (`ToolSlots.smudges`; today Stump and Clear water), then the saved ones (the library's that aren't
  Basic tools), newest first; the tool's own brush outlined (shown first if it is neither). A card puts its brush in the
  tool (its size following its family, as a slot's) and closes the chooser, the name shown beside the tile; the pen on
  the page, or the tile again, closes it. Two across, at most three rows (`DrawLayout.chooserGrid`: never more cells than
  cards), paged with ‹ › under them past six. No Undo step (a pick is one tap to change back), no Roll, no Brush Lab.
- **Finger** (the menu's last row, "Finger: Eraser"; the choices in a second menu, the chosen one ink-filled):
  **Nothing**, **Eraser**, **Smudge**, **Slot 1** to **Slot 6**, kept under `inkwash_finger`. Eraser is the first
  choice: it is what one finger always did (the request took Nothing for today's behaviour; it isn't). Eraser erases at
  the fingertip's size, as before; Smudge and a slot draw that tool's brush at its size and grey, a medium press, no tilt.
- **Two and three fingers** keep their taps (Undo, Redo). There is no pinch or pan: Inkwash locks MonoPaint's navigation
  (`navigationLocked`, no page zoom), so two fingers never zoomed here; unchanged.
- **Palm rejection** is the finger eraser's, now for every choice (`FingerTool`, pure): a finger is refused while the
  pen is down, within 250 ms of the pen hovering, while a fill or the eyedropper holds the page, and for a contact over
  90 dp (resting hands land at 94 to 128); it draws only once it moves past 8 dp, so a tap's first finger lays nothing;
  a second finger, the pen landing or a contact swelling past 115 dp takes back what it drew: the eraser's is dropped,
  a brush's finished and undone (as Undo would; its quick render and wet paint can't be dropped mid-way).

### Budgets (dp; `DrawLayout`, as checked)

To fit eight tiles, a tile's least is 36 (was 40) and a face's 35 (was 36, a 23 dp square): only the Nomad in landscape
sits at the least.

| | Manta portrait | Manta landscape | Nomad portrait | Nomad landscape |
|---|---|---|---|---|
| 1 layer: icon, slot, face (square), thumb, droplet | 54, 60, 65 (53), 54, 46 | 45, 44, 45 (33), 45, 37 | 43, 42, 45 (33), 43, 35 | folded: 40, 36, 35 (23), 40, 32 |
| Was | 56, 64, 68 (56), 56, 48 | 45, 48, 47 (35), 45, 37 | 44, 46, 47 (35), 44, 36 | folded: 40, 40, 36 (24), 40, 32 |
| 4 layers | 50, 54, 56 (44), 50, 42 | 41, 38, 38 (26), 41, 33 | 41, 38, 37 (25), 41, 33 | folded: same |
| Was | 51, 56, 59 (47), 51, 43 | 42, 42, 40 (28), 42, 34 | 41, 40, 39 (27), 41, 33 | folded: same |
| Chooser card; most rows; width | 142 × 132; 3; 290 | same | 142 × 104; 3; 290 | same |

`DrawLayoutChecks` proves the column on all four with one to four layers (eight tiles, nothing scrolls, tiles at least
36, faces 35), the Manta portrait within a tenth of its preferred heights, the chooser beside the column for 1 to 40
cards; the taps (the selected smudge tool again: the chooser). `ToolSlotsChecks`: the Blend stump at first, only smudge
brushes in the chooser, Basic tools then saved, the pick kept. `FingerToolChecks`: the choices, keys and words, and that
a palm (94 to 128 dp), the pen down or near, or a busy app never draws, a fingertip swelling to 85 dp keeps drawing.

### Device checks for Elling

1. Manta and Nomad, both turns, one to four layers: the smudge tile under the eraser's, nothing cut off (Nomad
   landscape: tiles 36 dp tall, faces' squares 23: easy enough to hit?).
2. The smudge tile: its loop over a grey band; select it, smudge with the pen; its three sizes; tap again: the chooser
   (Stump, Clear water, and any saved smudge brush), a card puts it in, its name beside the tile.
3. Menu, "Finger: Eraser": choose Smudge, rub a pencil drawing with a finger; choose Slot 2, draw with a finger; choose
   Nothing, a finger does nothing; two- and three-finger taps still undo and redo with every choice.
4. With a finger choice that draws, rest the drawing hand on the page while drawing with the pen: nothing drawn by the
   palm; a palm that lands first is taken back when the pen lands.

## Edit buttons, the six slots, no numbers (as built, 2026-10-19, branch `inkwash-baredit`)

The owner: "instead of a little tiny caret in the swatch and sizes, let's add a way to customize the set below? also
similarly have a way to load the brushes with brushes from the library.. maybe a big modal with the slots"; "no need
to have numbers by the brushes? or is that necessary?"; "lab... 'create new brush' should be an option in there"; then
"selecting a brush opens the options panel each time, that seems excessive.. the slot customizer will solve that",
"for selecting finger action... include brush previews for brushes", and "you can remove the grey stroke around the
selected swatch".

- **The side bar** (`DrawLayout.COLUMN`): `TRAY, EDIT_TOOLS, GAP, FACES, EDIT_SIZES, GAP, TOOLS`. Each **Edit** button
  (`DrawLayout.EDIT`, the LABEL style in a 2 dp outline, 57 dp wide, 32 / 28 / 26 dp tall) sits straight under what it
  edits; both say "Edit" (a second word doesn't fit the Nomad's 62 dp column at 14 sp), their screen-reader words
  "Edit tools" and "Edit sizes and greys". The faces' ▾ corners are gone (Brush Lab's too); a long press on a face
  still edits that one.
- **The whole set** (`SizeModal.set`, from `SwatchFaces.editAll`): "Sizes and Greys" and Done; the four sizes as faces,
  the one being set tabbed, and under them the size modal's own editor (`SizeEditor`: the true-size dot, 80 dp here, the
  slider, Fine and Broad, the nine stops); a rule; the four greys as faces and under them the grey flyout's own eight
  (`GreyGrid`). The size modal and the flyout are built from the same two. Each change applies at once. The smudge tool:
  sizes only; the eraser: its Edit greyed (its sizes can't be set).
- **A tray row only selects** (`DrawLayout.tap`: the selected slot again is `NOTHING`); a long press on a row opens
  the Tools page on its slot. The smudge tool keeps its chooser on a second tap.
- **The Tools page holds the six slots** (`SlotRow`, Brush Lab's TOOLBAR row, now shared): under the bar, across the
  band in portrait, atop the tool's column in landscape, 74 dp (Nomad 52) and a gap. Tap a slot, then a card ("click a
  slot and then click an option from library"); the tabs, chips, grid, test area, Roll another and Undo are as they
  were, on the selected slot; Undo takes back any slot's change and selects that slot again; Back returns all six
  (`ToolsActivity.BRUSHES`, null where unchanged), the drawing screen applying each (an Undo step each) and selecting
  the page's slot. The bar: back, "Tools", Undo, **New Brush** and Brush Lab. New Brush opens Brush Lab on a new brush
  in the slot brush's style (Randomize's rule, `BrushLabActivity.FRESH`); Save there returns at once and the Tools page
  opens again on the slot, which holds it; leaving the lab without saving gives the slot its own brush back.
- **No numbers**: `SlotTile` and `LoopCard` lost their number badges (the bar's tray, Brush Lab's TOOLBAR, the
  library cards' slot badges, the library bar's "SLOT N"); the tray stroke takes the number's column
  (62 dp long on the Manta, 52 on the Nomad: about 18 dp more). The Finger menu names a slot by its brush and shows its tray stroke (`InkWashBar.fingerIcon`;
  Eraser and Smudge their icons, Nothing the struck-through hand), the one chosen marked by the right-edge bar; the
  Finger button's mark for a slot is its tray stroke, small, in a fine ink frame.
- **A near-white grey's inset rule** shows only while it isn't the one in use (`SwatchFace`).

### Budgets (dp; `DrawLayout`, `ToolsLayout`, as checked)

| | Manta portrait | Manta landscape | Nomad portrait | Nomad landscape |
|---|---|---|---|---|
| Bar items / spare | 1070 / 277 | 995 / 11 | 944 / 36 | 722 / 11 |
| Was | 1006 / 341 | 1006 / 0 | 888 / 92 | 722 / 11 |
| Tray row, Edit, face row | 30, 32, 34 | 28, 30, 33 | 28, 28, 32 | 26, 26, 32 |
| Page turner, Words | whole, bar | whole, bar | whole, bar | **folded**, bar (was whole, menu) |
| Tools page test area | 1000 × 364 | 587 × 661 | 729 × 267 | 376 × 376 |
| Was | 1000 × 320 | 587 × 751 | 729 × 225 | 376 × 440 |
| Cards a page (was) | 24 (30) | 30 (30) | 15 (20) | 16 (16) |

On the Nomad in landscape the two Edit buttons (52 dp at the least) leave the whole page turner 41 dp short even
without Words, so it folds into one button (‹, › and Notebook beside it), and Words fits in the column again. In
portrait the six slots take a row of cards; the test area keeps its least and gains the rest.

## Four sections, the slots in front (as built, 2026-10-19, branch `inkwash-toolspage`)

The owner: "tool selection page.... the slots should be the main focus... you can move the test area to the top and the
current brush below that, then the slots, then the library display. each section should be labeled clearly.. Remove
"roll another". lets use the toolbar stroke look for all of the previews. to show that a slot is active, have a line
around it. it can be blinking slowly? or animated outline or something?"; then "also back button should be clearly
visible.. sometimes it gets lost".

```
portrait                                         landscape
┌ [← BACK] TOOLS            [↶ UNDO] [■ BRUSH LAB] ┐   ┌ [← BACK] TOOLS                     [↶] [■ BRUSH LAB] ┐
│ TEST AREA                              [CLEAR] 💧 │   │ TEST AREA      [CLEAR] │ TOOLBAR SLOTS    [+ NEW BRUSH] │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │   │ ━━━━━━━━━━━━━━━━━━━━━━ │ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │
│ ┌ the test page ─────────────────────────────┐  │   │ ┌ the test page ─────┐ │ [slot][SLOT][slot]…  (six)    │
│ └────────────────────────────────────────────┘  │   │ │                    │ │ LIBRARY              ‹ 1/3 ›  │
│ THIS BRUSH                                       │   │ └────────────────────┘ │ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │   │ THIS BRUSH             │ [Basic tools|Recent|Saved|All]│
│ ~~~~ (tray)  name · Style / grey words           │   │ ━━━━━━━━━━━━━━━━━━━━━━ │ chips                         │
│ TOOLBAR SLOTS                     [+ NEW BRUSH]  │   │ ~~ name · Style        │ [card][card][card][card]…     │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │   │    grey words          │                               │
│ [slot][SLOT][slot][slot][slot][slot]  (biggest)  │   └──────────────────────────────────────────────────────┘
│ LIBRARY [Basic tools|Recent|Saved|All]   ‹ 1/3 › │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │
│ chips; [card][card]… rows                        │
```

- **Order and headers.** Test Area, This Brush, Toolbar Slots, Library, top to bottom in portrait; in landscape the
  first two are a column at the left and the slots and the library at the right (the slots beside the cards they take).
  Each section is under a light header (`LabUi.header`, as a Brush Lab module's head: its label in the LABEL style,
  caps, 14 sp, 700; buttons at its right; a 3 dp ink rule along its foot), 40 dp (36 on the Nomad), a blank of half the
  gap under it, a gap between sections. Clear and the wet drop are in Test Area's header, **New Brush** in Toolbar
  Slots' (it makes a slot's brush, so it sits with the slots), the pages in Library's, and the tabs too where they fit
  across (portrait; in landscape they keep their own row under it).
- **The bar:** Back, "Tools", Undo, Brush Lab. **Back** is `LabButton.back`: the arrow and "Back" in a 3 dp ink rule,
  44 dp tall, first at the left with the side padding before it, so nothing measured after it can move it. Brush Lab's
  bar has the same Back; to keep the Nomad's longest Basic tool name whole beside it, its Save there shows its disk (or
  the check) alone, as its Undo does (`LabLayout.bar`).
- **Roll another went** (`ToolsPage.roll`, `ToolsLayout.ROLL`). New Brush makes a new brush in the slot's style.
- **Every preview is the bar's tray stroke** (`LabLibrary.traySampler`): the slots and This Brush at the slot's size and
  grey (`InkWash.tray`: no broader than .6 of the height, a faint one darker, `WildSample.legibleTray`), the library's
  cards at full darkness at the size that fits the card (`ModularSizes.sampleSize` of its height), legible the same way.
  Drawn in the background into `WildSample`'s cache as before. Brush Lab keeps its loops.
- **The slots** are `SlotRow.cards`: six `LoopCard`s in a grid's 2 dp ink border with grey-400 lines, each its tray
  stroke over its brush's name (700, ink, wrapped onto a second line rather than cut: `LoopCard.setNameFirst`) and its
  line where it fits (left out when it only repeats the name: `ToolsPage.slotCaption`), in one box on all six (three
  lines on the Manta, two on the Nomad: `ToolsLayout.slotLines`, as Brush Lab's variations' `setFixed`), so the six
  strokes line up. 140 dp tall on the Manta (142 in landscape), 100 on the Nomad (111 in landscape), taller than any
  card. On the Nomad in landscape the slots are at least 101 dp wide (`SLOT_LEAST_WIDTH`, which widens the right column
  there) and their words are 12 sp, so a ten-letter name ("Watercolor") stays whole; a single word over 91 dp at 12 sp
  (Copperplate, Compressed, Granulating) ends in "…" there, nowhere else.
- **The selected slot** has a 4 dp ink outline round the whole card (`LoopCard`'s). **It blinks** for 6 s after a slot
  is chosen (a tap, or Undo selecting another) or the page opens, then stays ink: ink, grey-800, grey-600, grey-800, a
  step every 375 ms (round once in 1.5 s), 16 steps, ending on ink. Each step sends only the outline's four 4 dp sides
  to the panel through `SelectionFeedback.update(View, Rect[], Runnable)` (the grey partial mode, never the Nomad's fast
  pen plane, whose ink a later redraw would leave behind); Android's own redraw of the card follows only with the next
  frame something else needs. It stops, ink, while the pen draws on the test page or the window loses focus, and on
  leaving. Not tried on a panel yet (no adb): if it ghosts, a static double outline is the fallback.

### Budgets (dp; `ToolsLayout`, as checked)

| | Manta portrait | Manta landscape | Nomad portrait | Nomad landscape |
|---|---|---|---|---|
| Test area | **1000 × 384** | 587 × 695 | **729 × 231** | 334 × 391 |
| Was | 1000 × 364 | 587 × 661 | 729 × 267 | 376 × 376 |
| This Brush (tray width) | 1000 × 77 (200) | 587 × 129 (200) | 729 × 109 (120) | 334 × 182 (84) |
| Slots, each | **164 × 140** | 118 × 142 | **119 × 100** | 101 × 111 |
| Library: columns × rows, card | 6 × 3, 164 × 116 | 5 × 5, 142 × 118 | 5 × 2, 143 × 96 | 4 × 3, 152 × 100 |
| Cards a page (was) | 18 (24) | 25 (30) | 10 (15) | 12 (16) |
| Tabs | in the header | in the header | in the header | own row |

`ToolsPageChecks` proves on all four: the four sections add up to the height (two columns in landscape), every section
the width, the slots taller than the cards and at least 101 dp wide, every header's label and buttons across, every
Basic tool's slot name whole (but those single long words on the Nomad in landscape), cards' and the title's words as
before, the test area at least 330 × 220. Pictures: `scripts/tools_pictures.sh` (`ToolsPagePictures`, a desktop
stand-in from the same sizes and the engine's own tray strokes; the Manta's at the blink's grey step too).

### Device checks for Elling

1. Both tablets, both turns: the four headers, nothing cut off, nothing scrolls; Back top left, labelled, in Brush Lab
   too (Nomad: Save is its disk alone; the name still whole?).
2. Open the page, tap a slot: its outline fades grey and back for about 6 s, then stays black. Does it ghost, flash the
   card, or leave a black ring when another slot is chosen (the Nomad)? If so, say "double outline".
3. The previews: slots, This Brush and the cards are strokes; faint pencils readable; samples arrive without blinking.
4. New Brush from the slots' header; Undo and Back as before.
