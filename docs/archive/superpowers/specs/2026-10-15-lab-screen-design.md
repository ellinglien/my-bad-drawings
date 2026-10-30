# Modular brush lab, Stage 2: the lab screen (design)

Stage 2 of the modular brush work (Stage 1, the six-part engine, is
`2026-10-14-modular-engine-design.md`, done and installed). It replaces Brush
Lab's screen with the approved web prototype's:
`docs/brush-lab/prototypes/modular-lab.html`, its UI source
`modular-lab-src/ui.js` (line numbers below are that file's) and
`modular-lab.NOTES.md`. Port the prototype's screen; where this spec differs,
it says why. The engine, the part model and share codes do not change.
Stage 3 (bristles, Carry, plumes, graphite's Fill plane) stays deferred.

Branch `inkwash-v2-lab`, from `inkwash-v1` @ `155dec4`.

## Goals

- **The test area is the priority.** Use the space for it. No explainer text,
  no "TEST AREA" label, no title.
- **It fits the Manta and the Nomad with nothing scrolling** in either mode
  (portrait; the lab stays portrait-only).
- **Simple** is clutter-light: styles, a big Randomize, one slider per part,
  the brush in plain words. Every parameter it has is visible.
- **Advanced** exposes parts, choices, dials, locks, dice and variations, one
  part open at a time, still without scrolling.
- **The lab works on the toolbar's six slots**: pick a slot, then tap a
  library card (or a variation) to put it there.
- Close the toolbar gaps: size slots and shade swatches you can set, a slot
  preview that shows the real mark at its size.

## Elling's feedback, and where each stands

`app` = already in the app, `proto` = in the prototype, to port, `new` = new
in this spec.

| Feedback | State | Where in this spec |
|---|---|---|
| Minimal, brutalist: square, hard rules, one mono face | app + proto | Look |
| Few clear tools; hide explainer text | proto; **new** for the app: drop "BRUSH LAB", "How it works", the swap hint, the open card's blurb | Bar, Column |
| No "TEST AREA" label; the test area gets the space | app (no label) + proto (space) | Layout |
| Clear button upper left of the test area | app + proto | Test area |
| Fits Manta and Nomad; the right column felt cramped | **new** (proto designed it; the app scrolls) | Layout, `LabLayout` checks |
| Simple vs Advanced; Simple has quick randomizing and a plain description in the sidebar; Advanced has parts, dials, sliders | app (a basic switch, Stage 1) + proto (the screen) | Column |
| No scrolling to see parameters (except Advanced may hide them behind a tap) | **new** (Simple ≈ 470 dp scrolls ~50 dp on the Nomad; Advanced is 1,900–2,400 dp) | Layout |
| Locks set in Advanced apply invisibly in Simple | proto (Simple ignores locks, keeps them for Advanced); **new** in the app | Locks |
| Variations hidden by default, only in Advanced; frees room for slots | proto | Advanced |
| Variations' dice: icon only | app + proto | Advanced |
| Randomize: big dice, solid black like Save, full width, above the variables | app (Stage 1 column) | Column (kept full width in both modes; the prototype halves it in Advanced) |
| Style chips include all types, no pen-or-brush first; Wild is "Anything goes" | app | Column |
| Name with a rename pencil, then Load (was Code), then Save, solid black | app (pencil); proto (Load, Save beside the name) | Bar |
| Bottom area: toolbar slots and library together, "TOOLBAR SLOTS »", "LIBRARY → SLOT N" | proto | Shelf |
| Tap a slot, then a library card or a variation, to assign | proto | Shelf, Interactions |
| "Surprise me" (dice) puts a random library brush, never the current one, in the slot; Undo reverts | proto | Shelf |
| Library cards: two description lines first, name after, cut to fit | app + proto | Shelf |
| The library could get big | app + proto (paged); **new**: slot numbers on cards, fixed order, Surprise and slot picks turn to the right page | Shelf |
| Any slot holds any tool | app | — |
| Toolbar preview shows the real mark (the icon's swoosh, pressure rising to firm at the end) and its size | app (a swoosh, light-firm-light, fixed size); **new**: the ramp, the slot's size and grey | Toolbar |
| Size slots like swatch slots, set in a modal from thin to big brush | **new** (proto has them in the test area) | Toolbar, Test area |
| Three swatches you can set with a flyout | **new** (proto test area) | Toolbar, Test area |
| Each slot remembers its last shade | app | Toolbar (kept, now a grey) |
| Swatches with no border; the tab under the picked one is the only marker; paper white gets a 1 px inset rule | **new** (proto CSS `.tslot`) | Toolbar, Test area |
| Speed is a horizontal switch whose label changes, no arrows | app (old brushes' Speed). Still relevant: a six-part Hand's "Fast is" (`DIR`) becomes the same switch (today it is two chips) | Advanced |
| A bit more padding left of the lock icons | **new** | Advanced rows |
| Long labels ("Hold (vine↔compressed)") wrap in the label space | **new** | Advanced rows |
| An animated demo of each slider | later | Out of scope |

## Layout

All in dp. Both tablets are 300 ppi (1.875 px/dp). Portrait only.
`LabLayout` (pure, new) computes every number below from the lab root's
measured size, the mode and what is shown, and its check proves the sums.

### Regions (both tablets, both modes)

```
┌ bar 56 ──────────────────────────────────────────────────────────┐
│ ←  name ✎  [LOAD] [■ SAVE]   │ SIMPLE │ ADVANCED │  ↶ UNDO       │
├──────────────┬───────────────────────────────────────────────────┤
│ column       │ test area                                          │
│  style       │ [CLEAR]                 sizes ▾▾▾  shades ▾▾▾     │
│  RANDOMIZE   │                          ┌ corner loop ┐          │
│  part cards  │                          └─────────────┘          │
│  this brush  │                                                    │
│              ├───────────────────────────────────────────────────┤
│              │ (Advanced: six variations, icon-only dice)        │
│              │ TOOLBAR SLOTS » [1][2][3][4][5][6]                 │
│              │ LIBRARY → SLOT 3   [⚄ SURPRISE ME]  ◀ 2/7 ▶       │
│              │ [card][card][card][card][card]                     │
├──────────────┴───────────────────────────────────────────────────┤
│ status 28: one line, short and factual                            │
└───────────────────────────────────────────────────────────────────┘
```

The column moves to the **left** (it is on the right today), as in the
prototype: it runs the full height, and the shelf sits under the test area.
The bar (56) and the status line (28) include their 3 dp rules, so the main
area is H − 84.

### Budgets

| | Manta Simple | Manta Advanced | Nomad Simple | Nomad Advanced |
|---|---|---|---|---|
| Screen | 1024 × 1365 | | 749 × 998 | |
| Main height | 1281 | 1281 | 914 | 914 |
| Column width | 300 | 380 | 256 | 336 |
| Right side width (W − column − 3) | 721 | 641 | 490 | 410 |
| Style block | 2 × 7 grid, 31 rows, 243 + 8 | "STYLE: X ▾" row 34 | 2 × 7 grid, 27 rows, 215 + 8 | row 34 |
| Randomize | 58 | 58 | 50 | 50 |
| Part cards (5 shown) | 140 each | closed 140; open: 4 rows × 42, card 1021 | 100 each | closed 138; open: 4 rows × 38, card 678 |
| "This brush" | 272 (≥ 124) | when room, hidden while a part is open | 141 (≥ 140) | same |
| Variations | — | 6 × 1, 112 tall | — | 3 × 2, 168 tall |
| Shelf (slots + library) | 358: slots row, library head, 2 rows of 5 cards (118 × 116) | 242: 1 row of 5 | 216: 1 row of 4 (104 × 98) | 216: 1 row of 3 |
| **Test area** | **913 × 701** | **907 × 621** | **688 × 470** | **510 × 390** |

Rules (port of `metrics()`, ui.js 129–159, changed where noted):
- `compact` = width < 900 (the Nomad): smaller rows, slot tiles 60 × 50
  (74 × 58 on the Manta), library cards ≥ 104 wide × 98 (118 × 116).
- **Column width:** Simple 300 (Manta) / 256 (Nomad), as the prototype.
  Advanced 380 / 336, wider than the prototype's 360 / 306 so a dial row fits
  a 112 / 104 dp wrapping label, the value (44 / 40), an 8 dp gap, lock and
  die (28 each), and a slider of at least 80 dp (Manta 113, Nomad 81). This answers "the right
  column felt cramped" (today's 360 dp column with single-line labels).
- **Simple cards:** `clamp((main − style − randomize − words_min − carry) / 5, 64, 140)`
  (the prototype caps at 116; the Manta has room, and a bigger sample reads
  better on e-ink). Below 84 the style grid folds into a "STYLE ▾" button, as
  the prototype; neither tablet needs it.
- **Carry card** is hidden while Carry is Off (always, for rolled brushes:
  Stage 1 never offers Lift, Drag or Smudge). A decoded code with another
  Carry shows a 40 dp slim card (choice name only) and the other cards share
  the rest. The prototype always shows it; a part with one choice is noise.
- **Advanced:** tapping a part opens it; the others collapse to rows
  (42 / 38). The open card's dial rows share its height:
  `clamp((open − 30 − chipRows × 28 − 10) / dials, 32, 44)`. The longest part
  (Ink, 9 dials, 2 chip rows) gets 44 on both tablets.
- **Shelf:** library rows 2 when main ≥ 1100 and Simple, else 1; columns
  `max(2, floor((right − 24) / cardWidth))`. The slots row and the library head
  are one line when `right − 24 ≥ 52 + 6 × (slotWidth − 2) + 330`, else two
  (both tablets: two).
- **The test area never changes size within a mode.** The swap strip and the
  Load sheet take the space they need without moving it (see below). Switching
  mode changes its size; its page keeps what is drawn (copied, top-left
  anchored), not cleared as today.
- **Bar:** back 48, name (flex, ≥ 200), pencil 40, Load, Save, the
  SIMPLE | ADVANCED segment ("ADV" on the Nomad), Undo (icon only on the
  Nomad).

## The screen

### Bar
Back · the brush's name and a pencil (rename in place, as today) · **LOAD**
(outlined; opens the Load sheet; pressed while open) · **SAVE** (solid black,
bookmark icon) · SIMPLE | ADVANCED segmented (moved out of the column) · Undo.
Gone: the "BRUSH LAB" title and "How it works". (proto; app has the pencil)

### Load sheet
Over the top of the main area, under the bar, full width, 3 dp frame (proto
`renderSheet`, 456–463): "LOAD A CODE" field + Load; "SHARE CODE" read-only +
Copy. Loading puts the brush in the selected slot (an Undo step) and closes
the sheet. A bad code says so in the sheet. The bottom share rows go.

### Test area
A frame 10 dp in from the right side's edges. On it, overlaid:
- **CLEAR**, top left (app + proto).
- **Sizes and shades**, top right (proto `renderTools`, 345–351): the
  selected slot's three size swatches and three shade swatches, 44 dp each,
  the same views and rules as the toolbar (below). Tap = use for the test
  strokes and the slot; the ▾ corner tab (or a long-press) opens the size
  modal or the grey flyout. Replaces today's S/M/L row above the page.
- **Corner loop**, top right under the tools (proto `loopBox`, 578): the app
  icon's loop in the brush at the slot's size (capped at the box height /
  4.5) and grey, redrawn on every change.
Strokes use the selected slot's size and grey, through the same strokes as
the drawing screen; the pen's eraser end erases (app).

### Column, Simple
Top to bottom (Stage 1 order, the prototype's parts):
1. **Style**: the 14 styles as a 2 × 7 grid, the brush's style inverted. A tap
   rolls a new brush in that style (app behaviour).
2. **RANDOMIZE**: full width, solid black, big dice (app).
3. **Part cards**: Tip, Medium, (Carry), Paper, Life, Hand. Each: a 14 dp
   pattern tab (ui.js CSS `.t-*`), its glyph, the part's label, the choice in
   words (`ModularWords.choiceWords`), a sample drawn for that part (below),
   and one **macro slider** between its two words (`PartChoice.macro`,
   `words`: thin↔broad, dry↔wet, smooth↔rough, still↔lively,
   steady↔responsive). No lock, no dice. Tapping the card (not the slider)
   opens the **swap strip**.
4. **This brush**: `ModularWords.plainWords`, whole sentences only: the last
   sentences go when the box is short, never fewer than two (proto `fitPlain`).
An old brush (built-in, `I`/`P`): Style, Randomize and "This brush" from
`BrushCaptions.describe`; no cards. Changing it is Advanced's job.

### Swap strip (both modes)
Tapping a part card replaces the area under the test area (shelf, and
variations in Advanced) with that part's choices, each a tile drawn with your
brush with only that part changed (proto `swapList`/`renderSwap`, 425–453):
header = tab, glyph, "TIP: PICK ONE", ✕. No hint text. Columns are picked so
tiles are about 1.35 : 1 (the prototype's cost loop). Only offered choices
(`ModularFeatures.offered`) plus the current one; the current tile is framed.
A tap puts that choice in (an Undo step); the strip stays open to try others;
✕ or tapping the card again closes it.

### Column, Advanced
1. **STYLE: <name> ▾** (34): opens a flyout of the 14 chips over the column.
2. **RANDOMIZE**, full width (kept full width, unlike the prototype's half).
3. **Part cards**, as Simple plus a lock and a die in each header. Tap a card:
   it opens and the others collapse to rows (tab, glyph, label, choice,
   lock). The open card: header (choice, lock, die, ✕), the choice chips, then
   every dial of the choice as a row:
   `label (112 Manta / 104 Nomad, wraps to two lines) | slider over the
   choice's range with the style's band hatched | value | 8 dp | lock | die`.
   Dials that change nothing now are greyed, not hidden (proto `dialActive`,
   246–258). Choice dials (Head, Line placement) are segmented chips.
   Takes ink and Plume stay hidden (Stage 3).
   **Hand** (open): a 3 × 3 grid, rows Pressure / Tilt / Speed, columns Size /
   Dark / Width·angle; a tap cycles off → some → full (proto `handGrid`,
   275–287), each row with a lock and a die; then Response as a dial row; then
   **Fast is** as a switch with a changing label, "fast = thinner" /
   "fast = thicker" (today's Speed switch, generalized; not chips).
4. **This brush** when there is room and no part is open.
Old brushes: today's settings rows; this is the one place that may scroll.

### Shelf (under the test area)
- **TOOLBAR SLOTS »** and the six slots: each the slot's brush as a loop and
  its number; the selected one framed with a tab (never inverted). Tap = the
  lab now works on that slot (not an Undo step).
- **LIBRARY → SLOT N**, **⚄ SURPRISE ME**, ◀ page/pages ▶.
- **Cards**: built-ins in their order, then saved brushes newest first; the
  order never changes on a pick, so pages don't reshuffle. Each card: its
  loop, two caption lines (description first: `BrushCaptions.describe`), the
  name in grey under them when there is room (dropped first), single-line
  each, cut to fit. Numbered badges show which slots hold it. The selected
  slot's brush is framed, never inverted (today it inverts). Empty cells keep
  the grid's size.
- Tap a card: it goes in the selected slot (an Undo step). Long-press a saved
  brush: delete, confirmed in the head (app).
- **Surprise me**: a random library brush that is not the slot's current one
  goes in (an Undo step), and the library turns to its page.
- Selecting a slot turns the library to the page holding its brush, if it is
  in the library. Save turns to the page holding the saved brush.
- **Variations (Advanced only)**: six cards above the shelf, captions as
  today, the icon-only dice for six more. A tap puts it in the selected slot.

### Status line
One line, 28 dp, ellipsized: what just happened ("Surprise: brave-onion is in
slot 3. Undo takes it back."). No timings.

### Locks
Locks belong to Advanced. In Simple, Randomize, styles and variations roll
everything; the macro sliders are never disabled. Locks are kept (not cleared)
and apply again in Advanced, where they show. (proto `locks()`, 65)

## Interactions and state

- The lab holds copies of all six slots and the selected slot. It opens on the
  slot whose panel opened it.
- **Undo** takes back, latest first, any change to any slot: a library card,
  a variation, Surprise, Load, Randomize, a style, a swap, a slider, a die,
  a rename. It restores that slot and selects it again. Selecting a slot,
  locks, the mode and paging are not undo steps.
- Changing a built-in still makes the lab's own copy "my-ink" (app).
- **Back** (and the system back) returns all six slots and the selected one.
  The drawing screen puts each changed slot's brush in (each its own Undo
  step there, as today) and selects the lab's selected slot; the label under
  the bar names it if it changed.
- Sizes and shades changed in the lab are the slots' own and are stored at
  once (the drawing screen reads them on return).

## Samples

- Part cards draw their part's sample (proto `renderSample` roles, 482–546):
  Tip a wave; Medium the Tip's wave over a grey band; Drying a wave that
  lifts firm over the band; Carry a wave over dark and grey bands; Paper a
  wave at 1.7 × size; Hand a pressure ramp with a turning tilt. Swap tiles
  draw their part's wave too (as built 2026-10-16, "Every part sample a
  wave"); slots, library cards and variations draw the loop.
  All at Dark, sized to the tile (`ModularSizes.sampleSize`, plus the role
  factors).
- Samples render on one background thread, newest request first, into the
  existing `WildSample` cache; a view shows white until its sample arrives.
  Today they render synchronously in `onDraw`, which would stall a screen
  that now shows 20–35 samples.

## The toolbar (drawing screen): only the gaps

Already there: six open slots, each showing a swoosh in its brush; a slot's
panel with three sizes, three shades, dice, save, four recent brushes and
Brush Lab; each slot remembering its size and shade.

New:
1. **Three size swatches, shared by all tools** (default 4, 16, 120 px).
   Each tool keeps its own size in px, so editing a swatch never changes
   other slots. In the slot's panel the three size dots become size swatches:
   tap = use; the ▾ corner tab or a long-press opens the **size modal**: a
   true-size dot (1 : 1, clipped, 120 tall), a log slider "thin ↔ big brush"
   from 1.5 to 360 px, stops 2 4 8 16 32 64 120 240 360, "16 px · 1.4 mm",
   Done. Picking a size in the modal sets that swatch and the current tool.
   A face shows the true-size dot up to 80 % of the face, beyond that the
   largest dot with its size in mm (today's `SizeDot` rule).
2. **Three shade swatches, shared** (default darkness 1, .53, .31: today's
   Dark, Light, Lighter). Each slot keeps its own grey. The ▾ tab or a
   long-press opens a **grey flyout** of 8 greys (1, .87, .73, .6, .47, .33,
   .2, .1; proto `GREYS`), 44 dp, 4 × 2.
3. **Swatch faces have no border.** The picked one has a 5 dp black tab under
   it, the only marker. A face lighter than 235 gets a 1 px inset rule in
   `RULE` grey. Size swatches use the same marker (today: a frame).
4. **Old brushes and the eraser** (built-ins, `I`/`P` Wild) keep their three
   fixed sizes and shades: swatch *i* is S/M/L and Dark/Light/Lighter for
   them, their faces show what they draw, and the ▾ tab is hidden. Their
   strokes don't change (Stage 1's promise).
5. **A slot that gets a brush of the other family** (line media ink, charcoal,
   graphite ↔ paint media wash, cover) takes the size swatch nearest the new
   brush's own Medium size (`ModularSizes.size(b, MEDIUM)`), so a Watercolor
   dropped into a Fineliner slot isn't a 4 px line. Otherwise a slot keeps its
   size across rolls and picks.
6. **Slot preview**: the swoosh's pressure becomes a ramp rising to firm for
   the last ~30 % (`p = .1 + .85 · smoothstep(t / .7)`), drawn in the slot's
   grey at the slot's size: true width up to ¼ of the button's height, beyond
   that at ¼ with the size in mm, 9 sp bold, bottom right.
Storage keeps today's keys (`inkwash_size_slotN` = preset name, read as the
swatch index; `inkwash_shade_slotN` = shade name, the index), so older builds
still read them, plus new keys for each tool's px and darkness and the two
swatch triples. A tool with no px yet draws at what its preset drew
(`InkWash.diameter`), so nothing changes size until it is picked.

## Reused vs new

| Reused as is or extended | New |
|---|---|
| `BrushLab` (now six slots + selection), `ToolSlots`, `BrushLibrary`, `ModularWords`, `ModularStyle`, `ModularVariations`, `ModularLocks`, `PartChoice.macro/words`, `ModularFeatures` | `LabLayout` (pure): every dp budget; `LabPages` (pure): paging |
| `BrushLabActivity` (rebuilt layout), `LabSettings` (the column, both modes), `LabLibrary` (becomes the shelf: slots row, Surprise, badges), `LoopCard` (frame not invert, background sample), `BrushTestPad` (keeps its page on resize), `LabButton`, `LabUi`, `BandSlider`, `SquareSwitch` | `PartCard`, `HandGrid`, `SwapStrip` views; `SampleQueue` (background samples); six part glyph drawables |
| `WildSample` (part roles, the ramp swoosh, size and grey on the bar), `ModularSizes` (role sizes) | `ToolSwatches` (pure): shared swatches, each tool's px and grey |
| `InkWash`, `InkWashBar` (panel faces, modal, flyout), `PaintActivity` (six slots back) | `SwatchFace` view, `SizeModal` dialog |

## Out of scope

- Stage 3: Bristles, Carry Lift/Drag/Smudge, plumes and Takes ink, graphite's
  Fill plane.
- An animated demo of what each slider does (later, if cheap), a setting
  guide, "How it works" text.
- Moving the toolbar to the left edge (lab v2's proposal), sizes and shades on
  the bar itself, inverted mode.
- A library style filter or search (paging and badges first; revisit if the
  library passes ~10 pages).
- Landscape lab; converting old brushes to six parts; custom sizes or greys
  for old brushes and the eraser.

## Open questions (defaults chosen, none blocking)

1. **Old brushes keep fixed sizes and shades** (no ▾). Default as above;
   scaling their engines to arbitrary px is Stage 3-sized.
2. **Family switch resets the size** to the nearest swatch. Default on; the
   alternative is "always keep the slot's size".
3. **"How it works" is removed.** Default; nothing replaces it.
4. **Simple ignores locks silently.** Default (prototype). No marker in
   Simple that locks exist.
5. **Randomize keeps the Hand?** (prototype question). Default: no, it rolls
   with the style unless the Hand is locked in Advanced (Stage 1).

## Risks

- **Sample cost on the Manta**: 20–35 samples per screen, re-rendered on
  change. Background rendering, the cache and only re-rendering what changed
  (the selected slot, its cards, the variations) keep it responsive; measure
  on the device.
- **e-ink refresh**: rebuilding every view on each change flashes the whole
  screen. Re-render only the regions whose content changed.
- **Fonts**: the lab uses `Ui.largerText`; fixed row heights from `LabLayout`
  must hold two-line 11 sp labels at that scale. Check on both tablets.
- **Six slots back**: a lab session can change several slots at once; each is
  its own Undo on the drawing screen.
- **Size migration**: tools keep drawing at their old size until a swatch is
  picked; picking one changes six-part brushes to the shared px.

## As built (2026-10-15)

Branch `inkwash-v2-lab`, Tasks 1–8 from `8b03769`, then the final review's
fixes. Checks: `test_inkwash.sh` 49 PASS, `test_document.sh` 84 PASS;
`assembleDebug`, `assembleDebugAndroidTest` and `assembleRelease` build;
`WashGoldenChecks` and `LegacyGoldenChecks` unchanged since `155dec4`. No
device run (no adb): everything below "Device checks" is still to be seen.
`LabLayout`'s numbers are the spec's table, unchanged (Manta Simple test page
913 × 701, Advanced 907 × 621; Nomad 688 × 470 and 510 × 390; open card 1021
and 678).

### Where it differs from the spec and the plan

- **Task 1–2:** as planned.
- **Task 3:** as planned (part samples kept per role).
- **Task 4:** samples draw on one background thread (`SampleQueue`); results
  no longer wanted go to a drop hook, so `LoopCard` recycles them, and cards
  take their sample lazily. The corner loop is drawn once and not cached. The
  test page counts as covered while renaming too (the keyboard), not only
  under the Load sheet; pen-downs under Clear or the swatches draw nothing.
  `BrushStamp` keeps per-thread scratch paths (a `ThreadLocal`) instead of a
  class lock, so the sample thread never stalls the pen. `LabUi.after`
  replaced two near-identical layout helpers.
- **Task 5–6:** as planned; a hand keeps its locked cells whatever it rolls
  (`ModularStyle`), and a chosen chip stays filled (grey) when locked. Old
  brushes' Advanced settings scroll (the one scrolling case, as planned).
  Choice segments (Head, Line placement, Takes ink) are as wide as their
  words and never smaller than 8 sp, so "squiggle" fits the Nomad's
  Advanced column; they already had the value column's room.
- **Task 7:** the wet-paint rule is the session's, not the selection's: wet
  paint is all one darkness (`WatercolorSession.darkness`). A wet brush's
  stroke at another darkness, or a grey change (`InkWash.greyChange`, the
  one helper both screens use), dries it first. This also covers switching
  between two old Watercolor slots of different greys mid-wet and the lab's
  test page. Same darkness: nothing changes, so old brushes draw as before.
  Undo of a roll or pick restores the slot's stored size (swatch and px)
  rather than fitting one again, so Undo after a lab return keeps the size
  the lab set. Stored swatches are clamped on read (1.5–360 px, greys 0–1).
  `InkWash`'s per-selection `chooseSize`/`setSize`/`chooseGrey`/`setGrey`
  went: both screens set swatches through `SwatchFaces`.
- **Task 8:** the size modal is a flat `PopupWindow` turned with the app
  (`Popups`), not a `Dialog`, so it works alike on both screens. Its slider
  shows each move in the modal only (the true-size dot and the px · mm); the
  size is applied where it stops, or on a stop's tap, or on closing
  mid-slide, so the bar's swoosh and the lab's faces redraw once (e-ink).
  `SwatchFaces` binds the faces, the modal and the flyout to `ToolSwatches`
  for the slot panel and the lab alike. A slot button that changes width
  draws its swoosh at once. `PaletteSwatch` stays (the palette editor uses it).

### Owner's device feedback, round 1 (2026-10-15)

- **Column:** "This brush" sits right under Randomize in both modes, always shown (a part open too), at least its
  first two sentences, at most the whole description; the cards follow, the blank under them. An old brush's words in
  Simple take the rest; in Advanced they sit over its scrolling rows.
- **Shelf:** two sections under solid black header bars: "TOOLBAR: TAP TO REPLACE", then the six slots across the
  whole width (box 70 dp tall on the Manta, 50 on the Nomad); "TOOL LIBRARY" with "→ SLOT N" (right-aligned, or under
  the title on the Nomad in Advanced), Surprise me and the pages in its bar, the grid butted under it. The old
  "Toolbar slots »" and "Library → slot N" labels are gone.
- **Surprise me moved out of the lab** (owner: confusing in the library): its button is gone from the library's bar,
  which now keeps "→ SLOT N" on one line everywhere; `BrushLab.surprise()` and its checks stay, for a future toolbar
  selector page.
- **No text cut:** a card's choice words wrap to two lines (14 sp Manta, 12 sp Nomad), the sample left of them
  narrowing to make room; a collapsed row's choice two lines; the open card's choice on its own line under its head;
  Life None's macro text two lines, smaller rather than cut; segments and values smaller (7 sp at least) rather than
  cut. Library, variation and swap-tile captions wrap whole words, shrinking from 10 to 8 sp, then leaving out the
  name, then giving the sample as little as 30% and the text 7 sp (`LabText.caption`). `LabLayoutChecks` measures
  every choice word, swap note, caption, dial label, value and "This brush" (two sentences) against the budgets with a
  monospace model of the lab's 1.2× text.
- **Clear vanished on the Manta** (it still worked: a repaint left it out of what the pad presents). Clear and the
  sizes and greys no longer lie over the pad: they sit in a 60 dp head above the test page, so nothing the pad draws
  straight to the panel can paint over them; the pad's cover compositing is gone. The loop's box moved to 10 dp from
  the page's top.
- **Budgets now** (test area width × height, page height; "This brush"; cards; card sample):
  Manta Simple 701 × 871, page 807; words 241; cards 140; sample 80 × 100.
  Manta Advanced 621 × 865, page 801; words 199; cards 140 (open card 822); sample 144 × 74.
  Nomad Simple 470 × 658, page 594; words 181; cards 92; sample 62 × 52.
  Nomad Advanced 390 × 480, page 416; words 140; cards 138 (open card 538); sample 121 × 72.
  The Nomad's test area in Advanced is below the old 500 dp floor (now 470): the header bars and the test head cost it.

### Owner's B5 column and part names (2026-10-15)

- **Names:** the parts read Tip, Medium, Paper, Drying (was Life), Control (was Hand), Smudge (was Carry); Drying's
  choices Instant, Quick, Slow (None, Fast-wet, Drying). Labels only (`BrushPart.label`, `PartChoice.label`); enum
  names, keys and M codes are unchanged. Library captions say "instant" for Instant (was "fast"). The web prototype
  keeps the old labels (it is built and checked by Node and Puppeteer; not touched).
- **Column:** style block, Randomize (inset like the frame), then one heavy frame (`LabSettings.frame`: 5 dp border,
  10 dp padding on the Manta, 4 and 8 on the Nomad, a gap from the column's edge and rule) with a full-width black
  THIS BRUSH bar, "This brush" (no heading or rule now), and the six parts as `PartCard` modules, two to a row, Smudge
  always shown (Off included; `carryShown` and the slim Carry card are gone). A module: 2 dp frame, the pattern strip,
  a black head (name in white bold caps and the white glyph at its right, as tall as the name's line; under them the
  part's fixed description in pale grey, `ModularWords.inside`, one table), then the choice in bold, centred, 2 lines
  at most (autosized 15→9 sp Manta, 13→8 Nomad), the sample, the slider with its end words under it. Advanced: the
  lock and die follow the slider; a part open, the six turn slim (glyph, name, lock, choice) and the open card lies
  under them across the frame's padding.
- **Budgets** (`LabLayout`; test area; column; module w × h; sample height; "This brush"):
  Manta Simple 601 × 871, column 400, modules 173 × 228, sample 49, words 169 (all of it), style grid kept.
  Manta Advanced 601 × 865, column 400, modules 173 × 273, sample 90, words 169, open card 688, slider 110.
  Nomad Simple 410 × 658, column 316, modules 137 × 198, sample 28 (the least), words 138; the style grid folds into
  "Style ▾" (no room for it with two-up modules).
  Nomad Advanced 382 × 480, column 344, modules 151 × 208, sample 45, words 106 (two sentences), open card 423, slider
  80 (dial labels 98 dp, values 38).
- **Other changes the budgets needed:** a swap strip's tiles are never much shorter than a library card, and may be
  as tall as one when narrower (Control's nine on the Nomad's narrower Simple test area); Clear's padding is 6 dp on
  the Nomad; `LabText` counts Latin-1 ("·", the no-break space) as one monospace advance.
- **Bar:** Undo sits after Save in the name's box, outlined (Save stays the black one), greyed with nothing to undo.
  Save's icon is Phosphor FloppyDisk (regular), its saved state FloppyDisk fill (slot panel).
- **Glyphs:** the part glyphs' strokes are 20% heavier, to stay crisp at the name's height on black.

### Owner's device feedback, round 2: blinking and the frame (2026-10-15)

- **What a slide redraws.** During a drag: the slider, and in Advanced its value (fixed height, so no layout). The
  size modal: the slider and its px/mm (one fixed-height line); its dot and stops follow on lift. On lift, one
  `render()`: the module's choice and sample (`BrushLab.redraw`, a pure diff of the brush shown and the brush now:
  another seed or style redraws every part, else only the parts whose own settings changed, so a Tip slide redraws
  only Tip although each part's sample draws the whole brush), "This brush" if its words changed, the test page's
  corner, the selected slot's tile, the status line, and Undo the first time. Not the other modules, the library, the
  swap strip's samples (their captions update in place), the size and grey faces (`SwatchFace.editable` only redraws
  when its ▾ comes or goes) or the variations: `endSlide` no longer makes new ones (`fitSize` only). Advanced's open
  card and an old brush's rows are rebuilt only when what they show but slider values changes (`openKey`,
  `oldKey`); otherwise sliders and values move in place. `LabButton.setOn`/`setEnabled` redraw only on a change.
- **One frame.** `SampleQueue` hands results back together, in one main-thread turn, when its queue runs dry (or after
  400 ms, so a full library still fills in steps). `LoopCard.setSample` keeps the old sample until the new arrives
  (it was blanked first: two redraws).
- **Stable sizes.** Every text a change can touch has a fixed height: the module's choice, "This brush", the open
  card's choice and values, the status line, the size modal's value.
- **Frame.** The frame draws its border and the THIS BRUSH bar's black as one, in whole pixels (`LabSettings.frame`);
  the bar is only its words, so it meets the border on the top, left and right with no seam (the GradientDrawable
  stroke's anti-aliased, half-pixel edge and the dp rounding of border + padding left slivers).
- **Modules.** Order `BrushPart.SHOWN`: Tip, Medium / Paper, Drying / Control, Smudge (codes keep the enum order). A
  head is as tall as its row's longer description needs (`LabLayout.rowHead`), with the module padding all round (the
  14 dp under the description is gone); the sample takes the rest. The head's colours are `PartCard.HEAD_FILL`,
  `HEAD_NAME`, `HEAD_WORDS`, in one place for a lighter, outlined head.
- **"This brush"** is `wordsMin` (its first two sentences, more when they fit), not the whole description's budget,
  which left a ~100 dp blank under short words on the Manta. A fixed height, not measured: measured words would move
  the modules on a slide.
- **Budgets now** (module h; least sample; heads by row; words): Manta Simple 249, 76, 81/67/67, 106; Manta Advanced
  297, 120 (sample max 120 now, 80 on the Nomad), words 106, open card 751, 74 dp blank under the frame; Nomad Simple
  201, 39, 83/58/58, 127; Nomad Advanced 208, 53, 70/58/58, 106.
- **Names:** Smudge's choice "Smudge" reads "Smear" (key `smudge`, codes unchanged). Graphite's Spread dial still
  reads "Smudge".

### Design pass: Inkwash Design Style (then "Daily Puzzle Style", 2026-10-15)

Plan: `docs/archive/superpowers/plans/2026-10-15-design-pass.md`. The owner's design system and the approved mockup
(LabDaily, Manta Basic; module heads as B5e "light header").

- **Tokens:** `Theme` (Inkwash-owned, so MonoPaint's `Ui` stays as it was): the six greys, strokes (fine 1, base 2,
  rule 3, heavy 4 dp), `TypeStyle` (caption 13, label 14 caps .06 em 600, body 15, heading 20 600, title 32 700),
  Roboto Mono bundled (`assets/fonts`, SIL OFL 1.1; the googlefonts repo's license, not Apache) at weights 400–700.
  The app keeps its 1.2× text scale, so 13 sp draws 15.6 dp. `LabText` measures Roboto Mono: 0.6 em a character, a
  line 1.32 em (was 1.17). (Since 2026-10-16 the face is iA Writer Duo: see "Typeface" below and `docs/DESIGN.md`.)
- **Simple → Basic** wherever it shows. **No status line:** `BrushLab.status` still says what each change did (the
  checks read it); the screen shows only a code that won't load (the Load sheet's last line, in the lab's words) and
  "Copied the share code."; Save reads "Saved" (filled floppy) while the brush is in the library. Every other status
  shows where it happened (the slot outline, the module, the slider, the library card).
- **Regions:** no rule between the column and the right side, a 16 dp gap (12 on the Nomad). Both sides start with a
  44 dp head row (STYLE, or Style ▾; Clear, the three sizes, a 2 dp rule, the three greys, faces 38 dp wide), so the
  style table's top and the test area's line up. The test area is just the page in a 2 dp ink frame.
- **Column:** the style table (2 × 7 rows of 28, a 2 dp ink border, an ink rule between the columns, grey-400 lines
  between rows, the style ink-filled), Randomize (ink, 48), the frame (4 dp border, the THIS BRUSH bar at 16 sp 700),
  "This brush" (15 sp; 13 on the Nomad), the modules. A module: 2 dp border, the strip, a white head (name 14 sp 700
  tracked, the glyph in ink, the description in grey-800 at 13 sp: Manta only), a 3 dp ink rule, the choice (16 sp,
  600, down to 13, one line, or two for Medium on the Nomad, three for Control on the Manta), the sample, the slider
  (2 dp track, 12 × 16 thumb) and the end words (13 sp grey-800; on two lines where they don't fit side by side: the
  Nomad's Control). A choice without a slider (Instant, Off) says what it means in the sample's place. Picked: a 4 dp
  outline (no tab).
- **Shelf:** ink header bars 36 dp (TOOLBAR: TAP TO REPLACE; TOOL LIBRARY with → SLOT N and outlined on-ink page
  buttons; on the Nomad the two labels stack and the bar is 49). The slot row is `SlotTile`s, the same tile the
  drawing screen's bar draws: the loop at the slot's own size and grey (`InkWash.tile`, no broader than a fifth of the
  loop's reach, then the size in mm), the number badge, grey-400 dividers inside an ink border, the selected slot a 4
  dp inset outline. Library cards: the sample, a 2 dp grey-400 rule, the name (600), the caption in grey-800, all 13
  sp; 4 columns on the Manta (was 5: 13 sp captions need the width), 3 on the Nomad; the lab's brush outlined 4 dp.
  Swap strips: an ink bar head, grey-400 lines; a tile too narrow for its note shows the choice alone (the Nomad's
  Control).
- **Budgets now** (test area w × h; column; module w × h; least sample; heads by row; "This brush"):
  Manta Basic 580 × 769, 404, 187 × 254, 41, 121/80/80, 119; Manta Advanced 580 × 785, open card 753, sample 103;
  Nomad Basic 363 × 591, 354, 164 × 215, 57, 31/31/31 (no descriptions), 103; Nomad Advanced 363 × 421, open card 435,
  sample 49. Library: Manta 4 × 2 (Basic) or 4 × 1, cards 144 × 148; Nomad 3 × 1, 119 × 128.
- **Advanced keeps its denser rules** (as before this pass): dial labels 11 sp (10 in a 32 dp row), values and
  segments down to 7, slim modules 12/11 sp down to 9/8, the open card's choice 11, variation captions down to 9, and a
  noMacro line beside the lock and die down to 9. Basic has nothing under 13 sp (`LabLayoutChecks`).

### Known limits

- The eraser keeps fixed sizes (no ▾). (Old brushes did too, until every brush became six-part: see "One engine".)
- Swatch edits are not Undo steps; Undo of a slot change also takes back a
  size picked for that slot since.
- Wet paint from another slot dries when the next stroke starts, not on
  selecting the slot (the page is the same either way).
- Resizing the bar draws six swooshes on the main thread (a brief stall on
  rotation only).
- "squiggle" at 8 sp is about 46 dp wide in a segment about 47 dp wide on the
  Nomad (monospace metrics, the lab's 1.2× text): tight; check it there.
- Stage 3 items (Bristles, Carry Lift/Drag/Smudge, plumes, Takes ink, graphite
  Fill plane) stay out.

### Device checks for Elling (Manta and Nomad)

1. Basic and Advanced: everything fits, nothing scrolls (except an old
   brush's Advanced settings), nothing is cut off; two-line labels and
   "squiggle" fit at the lab's larger text. Open each part in Advanced on the
   Nomad; the Hand grid; the Fast switch.
2. e-ink: no full-screen flashing on a change; only the changed region
   redraws. How quickly samples arrive on the Manta.
3. Swatches, from the bar's slot panel and the lab: tap a size and a grey;
   the ▾ tab (or hold) opens the size modal (slider, stops, Done) and the
   grey flyout; the bar's swoosh and the lab's faces redraw on lift. A
   Watercolor dropped into a Fineliner slot takes a broad size.
4. Each slot's swoosh shows its size and grey; a big size shows mm.
5. Load (paste, copy the code), Save, rename: the keyboard is never painted
   over by the test page.
6. Surprise me; putting a library card or a variation into a slot; tap slot
   5, a card, Undo; Back: the bar shows each changed slot.
7. Undo on the drawing screen across two changed slots: each slot's brush and
   size come back.
8. Watercolor: paint, then change its grey (or switch to a Watercolor slot in
   another grey) while wet and paint again: the first strokes keep their grey.
9. Ink and Fude feel unchanged while the lab or the bar draws samples
   (`BrushStamp`'s `ThreadLocal`).
10. First launch over a Stage 1 install keeps its slots, library and sizes.
11. An old Wild brush and a built-in: fixed sizes, no ▾, draw as before.

### One engine (2026-10-15)

Every brush is six-part now (plan `docs/archive/superpowers/plans/2026-10-15-one-engine.md`; engine spec, "One engine").
For this screen and the bar:

- The toolbar's point 4 (old brushes keep S/M/L and Dark/Light/Lighter) is gone: every slot's sizes and greys can be
  set; only the eraser keeps its own three sizes. A slot converted from an old brush draws at the px and darkness it
  drew, its marks on the nearest shared swatches.
- The lab has no old-brush branch left: no old settings rows (Advanced never scrolls now), no "built in: changes make
  your own copy", no Wild variations; Load reads an old I/P code as its six parts.
- The first launch's slots and library: Fineliner, Brush pen, Pencil, Ink wash, Watercolor, Gouache. The library's
  built-ins became ordinary brushes (deletable, not brought back).
- Grey changes no longer dry wet watercolor (there is none); device check 8 above no longer applies, and checks 9 and
  11 become: Fineliner and Brush pen feel right; a converted brush draws as before at its size and grey.

### Drawing screen C and the Tools page (2026-10-15)

Spec `2026-10-15-tools-page-design.md`. For this screen and the toolbar:

- **The slot panel is gone.** "The toolbar: only the gaps", points 1–2: the three size and grey swatches now sit on
  the drawing screen's left edge (`SwatchBar`), always shown for the selected tool, through the same `SwatchFaces`
  as the lab's head row; the eraser shows its sizes only. The panel's dice, Save, four recent brushes and Brush Lab
  button went: Save is the lab's; dice ("Roll another"), recent brushes, the library and Brush Lab are on the new
  Tools page, which tapping the selected slot again opens.
- **Surprise me is back**, on the Tools page's bar (not the lab's library, per round 1): `BrushLab.surprise`'s rule is
  now a static helper both use.
- The lab itself is unchanged; its grid and card sampler are shared with the Tools page (`LabUi.grid`,
  `LabLibrary.sampler`).

### Variations under their own bar (2026-10-15)

Owner's device feedback (Manta, Advanced): the variations want a header like the shelf's, with more white above the
toolbar, so they read as the test area's; their captions showed at very different sizes (each card shrank its own, 13
down to 9 sp, and the Nomad dropped a second line) and their samples at different heights.

- **Bar:** a "Variations" ink bar (`LabUi.bar`, as "Toolbar: Tap to replace" and "Tool library"), the dice on it at the
  right as an on-ink icon button like the library's page buttons; the cards take the dice's old 44 dp column. The six
  sit in a `LabUi.grid` butted under it.
- **Apart:** under the variations, `LabLayout.apart` = twice the gap (20 dp Manta, 16 Nomad) before the toolbar's bar;
  the test area to the Variations bar keeps the usual gap.
- **One size, words fit to it** (coordinator's revision: 8–10 sp was too small): captions are a fixed 13 sp
  (`TypeStyle.CAPTION`) in three lines on the Manta, 12 sp in two on the Nomad, in a caption box of one height, so
  every sample is one height. `ModularVariations.Made.changes` lists what changed, most telling first (the swap, then
  the nudges by how far they moved as a share of the dial's range), each in its wordings, longest first ("Tip → Split
  nib" / "Split nib"; "more nib width" / "+nib width"). `LabText.changes` keeps up to two that fit in their shortest
  wordings, in order (one that doesn't fit is left out; the first always shows), then gives each its longest wording
  that still fits. Lines wrap at spaces, and after a hyphen in a word too wide for the line ("Multi-" / "line"); nothing
  is clipped or ellipsized. The status line and the card's content description keep the whole caption. Over the
  checks' 840 rolled variations: two changes on 441 cards on the Manta, 352 on the Nomad, the rest one; all whole
  (`LabLayoutChecks`, both tablets). `LoopCard.setFixed` draws them (smaller by halves only should the device's paint
  wrap wider than the model: never seen, never cut).
- **Budgets now** (card w × h, sample; variations block with its bar; test area): Manta Advanced 94 × 122, 48; 160 + 20
  apart; 580 × 737 (was 785). Nomad Advanced 118 × 87, 36; 214 + 16 apart; 363 × 361 (was 421). Basic unchanged.
  `LabLayoutChecks` proves the sums, the blank wider than the gap, and every rolled caption whole at its fixed size.
- **Landscape:** the lab stays portrait-only (manifest); `LabLayout` does not fit either tablet turned (the column
  overflows in Basic too), unchanged by this.
- **Slot 4's blank tile (the owner's photo, "19mm"):** no failure found. Every style's rolled brushes and the six first
  brushes draw their tile at every grey from 100% to 10% on the desktop, none throws; a wash at 10–20% grey inks only
  1–2% of the tile in scattered dots, which reads as blank on the panel. A tile whose sample did fail would also stay
  white (`SampleQueue` logs "Sample failed"); check slot 4's grey first, then the Manta's log.

### Nomad pass: Advanced at 12 sp (2026-10-15)

The owner: "i need to consider the sizing on the nomad as well as the manta", with the rules: nothing in the lab
scrolls (Basic and Advanced), no text is cut (it wraps), no text under 12–13 sp, no explainer text. Seen at the
Nomad's size on the Manta (`ScreenShots`, an androidTest run in the bench build: the lab's and the Tools page's
content in a Nomad-sized `PanelPreviewLayout`, the drawing screen through its own simulation; the owner's tablet
settings untouched). Basic already held the rules on both tablets; Advanced did not (dial labels 11 sp shrinking to
8, values and segments to 7, the Control grid's heads to 7, slim modules to 8).

- **Advanced's text is 12 sp, never smaller** (`LabLayout.DENSE_SP`): slim modules (name and choice), the open card's
  head, dial labels (two lines, a break after an arrow; label column 112 Manta / 106 Nomad), values (as wide as six
  characters: Wet window says "10 mm" at its top, not "10.0 mm"), segments, the Control grid (row names as wide as
  "PAGE TILT", heads "WIDTH·" / "ANGLE" on two lines, levels) and a noMacro line beside the lock and die.
- **The open card no longer repeats its choice.** Its chips and its choice words went: the swap strip beside it (which
  opens with it in Advanced) shows every choice with the current one framed and puts one in, and the slim module above
  shows the choice in words. The card is its head, then the dials; a choice with none (Drying's Instant, Smudge's Off)
  says what it means there, as its module does. That room is what lets 12 sp fit the Nomad.
- **Control's row names** sit level with their cells (they were at the top of the row).
- **Segments under their label** when they don't fit beside it at 12 sp (Line placement's "centre / outline /
  squiggle"): the label on a line of its own, the segments across the row with the lock and die (`LabLayout.stackedRow`,
  52 dp). Head ("follows / fixed") still fits beside its label.
- **Slim modules** hold their choice in two lines on the Nomad, three on the Manta (Control names two responses there).
- **Budgets now:** slim module Manta 100 / Nomad 81; open card 666 / 396; dial rows on the Nomad 38 (Ink: 8 rows and
  Line placement's 52), 44 (Chisel), 48 otherwise; Manta 48 throughout; Control cells 46; dial sliders 126 / 82.
  Basic and the right side are unchanged (Nomad Advanced test area 363 × 361).
- **The test area in Nomad Advanced** stays 363 × 361: everything under it (Variations 214 + 16, the toolbar and the
  library 275) is the owner's design, and no rearrangement found room without dropping something. The candidates,
  for the owner: three variations a time on the Nomad (+89 dp), or the variation captions on one line there (+40); the
  library's row left to Basic and the Tools page in Nomad Advanced (+179).
- `LabLayoutChecks` now proves, on both tablets, every dial label in two lines, every value, every segment (beside or
  under its label), the Control grid's heads and levels in a cell, slim choices in their lines, all at 12 sp, and each
  choice's dial rows as tall as their labels need.

### Advanced without the library (2026-10-16)

The owner: "lets leave the library out of advanced on both the nomad and manta". Then, for both screens: "focus on
simplicity.. too much will confuse. use white space to differentiate sections".

- **Advanced has no TOOL LIBRARY** (its bar and grid) on either tablet. Under the test area: Variations (their bar, the
  six), the wider blank, then TOOLBAR: TAP TO REPLACE and the six slots. Basic keeps the library as before. Picking
  from the library, Surprise me and the presets are the Tools page's, which now has a test area of its own (its spec).
  A saved brush is deleted from Basic's library (long press), as before.
- **The test area takes the room** (`LabLayout.library` false in Advanced; the shelf is the toolbar alone, 110 dp
  Manta / 88 Nomad): Manta Advanced **580 × 933** (was 737), Nomad Advanced **363 × 548** (was 361). Basic unchanged
  (580 × 769, 363 × 591).
- **The swap strip** covers the same shorter area in Advanced (Manta 290 dp under the test area, Nomad 318): its tiles
  may now be 7/10 of a library card tall (was 8/10), so the Nomad's nine (Control) go 3 × 3 at 119 × 92 rather than 5
  × 2 at 81 × 139; the Manta's nine stay 5 across. `LabLayoutChecks` proves every swap caption whole at 13 sp there,
  the regions adding up, nothing scrolling and Advanced's 12 sp text, on both tablets, as before.
- **Code:** the test area's page in its frame, its corner loop, Clear and the wet drop (with the tilt feed) moved out
  of `BrushLabActivity` into `TestArea`, which the Tools page uses too: one test area, no second copy.

### Words (2026-10-16)

The text pass (`docs/TEXT-STYLE.md`) renamed the library's bar to LIBRARY and its slot to SLOT N (was TOOL LIBRARY,
→ SLOT N). On the Nomad the two now fit one line, so the bar is no longer stacked and Basic's test area is
**363 × 604** (was 591), its shelf 262 dp (was 275). Card captions break between their items when a line is too narrow ("Watercolour" / "slow",
`LabText.wrapItem`), as two-line captions already did.

### Typeface: iA Writer Duo; slot tiles without outlines (2026-10-16)

The owner: "let's use this font across all elements in the app: iA Writer Duo (including the file menu)"; "on the
drawing page, also remove strokes around tool slots". The style is now called the Inkwash Design Style
(`docs/DESIGN.md`, its Typography).

- **The face:** iA Writer Duo, 400 and 700 only (`res/font`, the theme's font too), replacing Roboto Mono. Weights:
  500 → 400 (captions), 600 → 700 (labels, headings, choices, dial labels, card names). `LabText` measures it as
  duospace: 0.6 em a character, m, w, M, W, %, @, —, … and a few more 0.9 em (`LabText.WIDE_SET`), a sign the face
  lacks 1 em; a line 1.3 em (was 1.32).
- **Budgets that changed** (`LabLayoutChecks.table`): Advanced test areas Manta **580 × 934**, Nomad **363 × 550**
  (Basic unchanged, 580 × 769 and 363 × 604); "This brush" 117 / 102 dp; modules Manta 255 (Basic) / 325 (Advanced),
  Nomad 215; least samples 45 / 107 / 60 / 52; heads 119 / 78 / 78, Nomad 30; open card 672 / 400; slim modules 99 /
  80; variation cards 94 × 121 and 118 × 86; sliders Manta 117, Nomad 82.
- **Fits fixed:** the value column is the widest value's width ("9.8 mm", two wide m's) rather than six m's; the
  Nomad's dial labels are 97 dp (were 106; every label still two lines, checked), so its sliders keep 82 dp; Carry's
  choice budget is 3.3 em ("Smear"); the Control grid's head check uses the heads it shows (Size, Dark, Nib); the
  Nomad's swap tiles in Basic may set a caption at 12 sp where 13 won't fit ("Custom" is 61 dp of a 59 dp line
  there; `LabLayout.swapLeast`).
- **Slot tiles** (`SlotTile`, the same component on the drawing screen's bar and Brush Lab's TOOLBAR row): no outline,
  no rules between tiles; the loop inside a 2 dp margin (3 dp over it); the selected slot marked as the sizes and
  greys are, a 5 dp ink tab under it (3 dp in from the loop's sides), in place of the 4 dp outline.
- **Pictures** (`scripts/screens_device.sh … menus`): the file menu, the Delete confirmation, the Open browser and a
  status message, drawn off the screen at the Manta's and the Nomad's sizes, besides the lab, the Tools page and the bar.

### The loop stands upright (2026-10-16)

The owner: "for the preview stroke (the app icon)... can we rotate all of them by 90 degrees clockwise, then flipped
horizontally? across the app... (the stroke looks too much like a letter a currently)"; then "yes, turn the app
launcher icon to match as well".

- **Every sample loop is the icon's turned** a quarter clockwise, then flipped left to right: x and y swapped
  (`WildSample.loop` draws `LOOP[i][1], LOOP[i][0]`, 159 wide × 218 high from (48, 28)). The start, the direction and
  the pressure and speed along it are as before. Cards, tiles, the bar's slots, part cards, swap tiles, variations and
  the test area's corner all draw through it.
- **Marks keep their weight to the loop**: a loop's reach (`WildSample.reach`, what `ModularSizes.sampleSize` and
  `InkWash.tileCap` measure against) is the wide loop's turned: the tile's width, or .62 of a wide tile's height (was
  its height, or .62 of a narrow tile's width). In the wide cards the loop is as high as before and narrower, its line
  thinner to match, so its eye stays open. The part cards' waves keep the old measure (`WildSample.waveReach`).
- **The corner box stands upright**: as high as before (a third of the page wide, 300 dp at most, × .66), and .8 as
  wide as it is high (`TestArea.frame`), so it fits the loop and gives the test page back the rest.
- **The launcher icon** turned the same way (`ic_launcher_foreground.xml`'s path transposed, its translate swapped;
  `ic_launcher.png` rendered again). The web labs (Modular Brush Lab, the dial demo, the Gravity Lab; the Match Lab's
  engine copy) draw the same loop and box.
- Checks: `WildSampleChecks` pins the mockup's 202 points turned (a 140 × 200 card's loop is the 200 × 140 card's with
  x and y swapped) and the upright shape; `LegacyConversionChecks`' nine loop-sample checksums were recorded again,
  its pages unchanged.

### The loop's tail halved (2026-10-16)

The owner, with a sketch of the turned loop and a mark across its tail: "i do want the crossing though, just the final
tail bit.. clip after the short line intersecting".

- **The loop keeps its crossing and half its tail after it** (`WildSample.cut`, `LOOP_TAIL` .5): the densified line is
  measured to where it first crosses itself, and only half of what follows is drawn, the end interpolated. Its extent is
  159 × 186 from (48, 28) (was 159 × 218).
- **It finishes firm**: pressure .15 + .75 sin(π/2 · min(1, u / .6))^.7 along the shorter line (`LOOP_FIRM`), light at
  the start and full from .6 of the way to the end (was light at both ends); 2 + 3p ms a step, as before.
- **Reach** follows the new aspect: the tile's width, or .73 of a wide tile's height (`WildSample.reach`); the corner box
  is .9 as wide as high (`TestArea.frame`).
- **The launcher icon** is cut the same way: the tail's two edges cut across at the loop's new end, a round cap as wide
  as the line, re-centred (44 × 38 dp). The web labs (Modular Brush Lab, the dial demo, the Gravity Lab) draw the same
  points (checked equal to the app's: 190 points in a 140 × 200 card, first, fiftieth and last the same).
- Checks: `WildSampleChecks` pins those points, one crossing with a tail after it, pressure only rising and firm to the
  end; `LegacyConversionChecks`' nine loop-sample checksums recorded again, its pages unchanged.

### The loop beside the name (2026-10-16)

The owner: "we should link the brush name at the top with the preview loop.. so you see the name and the demo
together at all times. this will help keep it clear what the name means", and the same elsewhere in the app.

- **The brush's loop sits directly left of its name on the bar**, one unit: a tile 45 × 48 dp, 84 × 90 px (a little taller than
  the bar's 44 dp buttons and narrower, as the loop stands; `LabLayout.NAME_LOOP_*`), 8 dp before the name, no frame
  (as a slot's tile). It is the corner box's loop (`TestArea.loop`, a `LoopCard`): in the slot's grey, at the size that
  fits the tile, drawn in the background whenever the brush or its grey changes (`TestArea.showLoop`, from `render`
  and a swatch pick). It stays while renaming, left of the field, and above the Load sheet.
- **The test area's corner box is gone**: nothing lies over the test page now (`BrushTestPad` presents only the page;
  a pen-down anywhere on it draws).
- **The Nomad's bar** keeps the name's room for the longest Basic tool ("Crisp charcoal", "Slow ink wash", 202 dp at
  20 sp): Load, Save and Basic | Adv have 10 dp padding either side there (was 14; the Manta keeps 14;
  `LabLayout.barPad`). Name room: Manta 332 dp, Nomad 203 dp (was 224 with no loop). A longer name of one's own still
  ends in "…", as before.
- Checks: `LabLayoutChecks` now proves every Basic tool's and every style's name fits whole beside the loop on both
  tablets (was: at least 200 dp).
- Elsewhere: the Tools page's tool has the same loop beside its name (see its spec). Not added where it would only
  repeat a loop already beside the name, or crowd: the drawing screen's label beside the side toolbar (it sits level
  with the slot's own tile, which is that loop at the slot's size and grey), the library's and the Tools page's cards
  (each already its loop over its name), the Load sheet (no name; the bar's loop and name stay over it) and the
  library's "Delete … ?" bar (the card it asks about is right under it). The web lab still draws the corner box.

### Every part sample a wave (2026-10-16)

DESIGN.md "Loop and wave": the loop is a whole brush, the wave is one part or setting. The Medium and Drying modules
and their swap tiles still drew the loop (over the grey band); a swap tile is choosing a part's option, so adjusting a
part: a wave too.

- **Every part-level sample in Brush Lab is a wave** (`WildSample.part`): the Basic modules, Advanced's modules (the
  open card has dials, no sample; the slim modules none) and every swap strip tile. Sized to the wave's reach
  (`WildSample.waveReach`) with the card's pad and swing, as the other parts; same greys (Dark) and sizes logic.
- **Medium**: the Tip's wave (1.25 periods, full swing) over the grey band, so cover and transparency show where it
  crosses the band.
- **Drying**: one period at .7 of the swing over the band, pressed as the loop is (`WildSample.Press.LIFT`, the loop's
  `firmEnd`): light at the start, firm from .6 of the way to the end and slower as it firms, so it lifts firm and wet
  and what Drying does at and after lift (the settled body, the re-tapered tail, wet edges) has paint to act on. A
  Float brush keeps its rings, drawn over the wave at the middle.
- `WildSample.wave`'s ramp flag became `Press` (`SWELL`, `RAMP`, `LIFT`); the loop's pressure is `firmEnd`, shared.
- The loop stays wherever the thing is a whole brush: slots, library cards, variations, the name's loop, the Tools
  page's cards. `LegacyConversionChecks`' checksums (loop samples and pages) are unchanged.
- Checks: `PartSampleChecks` proves every part's sample, for three Basic tools, at both tablets' module samples and
  swap tiles in both modes, is a mark well wider than tall across most of the tile (a loop stands, a little taller than
  wide, in the middle), and pins Drying's pressure (light, then firm from .6 on, slower where firm).

### The swap strip becomes a panel beside the module (2026-10-18)

The owner's page (2026-10-17 snug-apple: "Move bottom right selection/module panel closer: modal", a sketch of a part
card with a grid of choices next to it): a module's choices no longer open in the strip under the test area.

- **A small modal beside the module** (`SwapStrip` in a focusable popup, `BrushLabActivity.showSwap`): just right of the
  column, level with the tapped module (moved up at the screen's foot), as wide as the test area and as tall as its head
  bar and tiles (`LabLayout.swapPanel`). The same tiles (waves, the current one framed), the same sizes as the strip's
  (`swapColumns`, `swapCell`), so every caption check still holds. The shelf under the test area stays as it is.
- **It closes on a pick** (the choice put in, one Undo step), on ✕, on a pen tap outside it and on Back; a resting
  finger outside doesn't close it (`Ui.restingOutside`, as the size modal). While it is open the test page is covered
  (the pad doesn't present over it).
- **Advanced:** tapping a module opens the part in the column and the panel; a pick or a tap outside closes the panel
  and leaves the part open; tapping its slim module opens the panel again; the open card's ✕ closes the part.
- Checks: `LabLayoutChecks` places the panel for 2 to 9 tiles on the Manta and the Nomad in both modes, right of the
  column and inside the screen. Not yet seen on a tablet.
