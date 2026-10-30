# Lab Screen (Stage 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Brush Lab becomes the approved web prototype's screen: a part column on the left (Simple: style grid, Randomize, one slider per part, "This brush"; Advanced: one part open at a time with every dial, lock and die), a big test area, and under it the six toolbar slots and the paged library, with Surprise me. Nothing scrolls on the Manta or the Nomad. The toolbar gains shared size and shade swatches you can set, and slot previews that show the mark at its size.

**Architecture:** Pure, desktop-checked logic first, Android views after. `BrushLab` grows from one brush to the six slots and a selection; `LabPages` pages the library; `LabLayout` computes every dp budget (and its check proves both tablets fit); `WildSample` draws each part's sample; `ToolSwatches` owns the shared size and grey swatches and each tool's own px and darkness. The views (`BrushLabActivity`, `LabSettings`, `LabLibrary`, `PartCard`, `SwapStrip`, `HandGrid`, `SwatchFace`, `SizeModal`, `InkWashBar`) only show and call them. The engine, part model and share codes don't change.

**Tech Stack:** Java 8 Android app (no Kotlin, no XML layouts; vector drawables are XML, as today), desktop checks run by `bash scripts/test_inkwash.sh` (plain `main` methods with a `check(boolean, String)` helper, no JUnit).

**Source of truth:** `docs/archive/superpowers/specs/2026-10-15-lab-screen-design.md`. **Reference implementation:** `docs/brush-lab/prototypes/modular-lab-src/ui.js` (the screen; "ui.js a–b" below means its lines) and the page's CSS in `docs/brush-lab/prototypes/modular-lab.html`. Port the prototype's screen; change only what the spec says.

**Conventions (from CLAUDE.md, AGENTS.md and the Stage 1 plan):**
- Read `PHILOSOPHY.md` and the spec first. Pure classes never import `android.*`; Android classes only show and call them. Before adding a helper, search for the one that exists (`LabUi`, `LabButton`, `BandSlider`, `SquareSwitch`, `LoopCard`, `ModularWords`, `BrushCaptions`, `Ui.selectedFrame`).
- Java toolchain for every command:
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" PATH="$JAVA_HOME/bin:$PATH"`
- Desktop checks: `bash scripts/test_inkwash.sh | grep -c '^PASS'` (45 before Task 1) and `bash scripts/test_document.sh | grep -c '^PASS'` (80 before Task 1; it runs `test_inkwash.sh` at its end, so both grow together). Every new pure source goes into `sources=(...)` and every new check class into `checks=(...)` of `scripts/test_inkwash.sh` in the task that adds it. A check class prints exactly one `PASS:` line.
- Build: `ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest` → `BUILD SUCCESSFUL`. If an `androidTest` class references something a task renames or removes, update it in the same task. Do **not** run `adb` or install.
- **Never re-record `WashGoldenChecks` or `LegacyGoldenChecks`.** Nothing here changes how a brush draws.
- Every user-facing change gets a plain-language line under **Unreleased** in `CHANGELOG.md` in the same commit (each task gives its lines).
- Never `git add -A` or `git commit -a`; add files by name. Leave the untracked `docs/monopaint-ideas-draft.md` alone. Commit messages end with:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01E7BSCi595U3kgRpEiXR6YV
  ```
- Java 8 only (no `var`, `List.of`, `String.repeat`, switch expressions). Format numbers with `Locale.ROOT`.
- Paths are under `app/src/main/java/io/github/mpdairy/monopaint/` (main) or `app/src/test/java/io/github/mpdairy/monopaint/` (checks) unless given in full.
- e-ink: no animations; rebuild only the regions whose content changed.

**Decisions this plan makes where the spec left room:**
1. The lab's Back returns all six slots as `BrushLabActivity.BRUSHES` (a `String[]` of `Brush.write()`) plus `SLOT` (the selected one). The drawing screen applies each changed slot with `ToolSlots.use(slot, brush, true)` (keeping the size the lab set).
2. `LabLibrary` is extended into the shelf (slots row, Surprise, badges) rather than replaced, so its paging and delete code stays.
3. `ToolSlots` owns the one `ToolSwatches` (built on the same store), so the family size rule runs wherever a slot's brush changes.
4. Samples render on one background `HandlerThread` (`SampleQueue`), newest first; `WildSample`'s cache is already synchronized.

---

## File structure

| File | Task | Responsibility |
|---|---|---|
| `BrushLab.java` (modify), `LabPages.java` (new), `LabSlotsChecks.java` (new check); `BrushLabActivity`, `PaintActivity`, `LabLibrary` (modify, wiring only); `BrushLabChecks`, `ModularLabChecks` (modify) | 1 | The lab works on the six slots; library order and paging. |
| `LabLayout.java` (new), `LabLayoutChecks.java` (new check) | 2 | Every dp budget for Manta and Nomad, Simple and Advanced. |
| `WildSample.java`, `ModularSizes.java` (modify), `PartSampleChecks.java` (new check) | 3 | Each part's sample. |
| `BrushLabActivity`, `LabLibrary`, `LoopCard`, `BrushTestPad`, `LabSettings` (modify), `SampleQueue.java` (new, Android) | 4 | The new frame: bar, test area, shelf, variations, Load sheet, status. |
| `LabSettings` (modify), `PartCard.java`, `SwapStrip.java` (new), `res/drawable/ic_part_*.xml` (new ×6); `BrushLab`, `ModularWords`, `ModularLabChecks` (modify) | 5 | Simple: style grid, Randomize, part cards, "This brush", the swap strip. |
| `LabSettings`, `PartCard` (modify), `HandGrid.java` (new); `BrushLab`, `ModularLabChecks` (modify) | 6 | Advanced: open card, dial rows, Hand grid, Fast switch, style flyout. |
| `ToolSwatches.java` (new), `ToolSwatchesChecks.java` (new check); `ToolSlots`, `InkWash`, `ModularSizes`, `DrawingPad`, `InkWashBar`, `BrushLabActivity`, `BrushLab`, `PaintActivity` (modify) | 7 | Shared size and grey swatches; strokes at a tool's px and darkness. |
| `SwatchFace.java`, `SizeModal.java` (new); `InkWashBar`, `WildSample`, `BrushLabActivity` (modify), `WildSampleChecks` (modify); `LabSizeButton.java` (delete) | 8 | Swatch faces, the size modal and grey flyout, the slot swoosh. |
| spec (modify) | 9 | Verification and as-built notes. |

## Tasks

1. The lab works on the six slots
2. `LabLayout`: the budgets
3. Part samples
4. The new frame
5. Simple: part cards and the swap strip
6. Advanced: open parts, dials and the Hand grid
7. Shared size and shade swatches
8. Swatches on screen and the slot swoosh
9. Full verification and as-built notes

---

### Task 1: The lab works on the six slots

**Files:**
- Modify: `BrushLab.java`, `BrushLabActivity.java`, `PaintActivity.java`, `LabLibrary.java`, `BrushLabChecks.java`, `ModularLabChecks.java`, `scripts/test_inkwash.sh`
- Create: `LabPages.java`, `LabSlotsChecks.java`

Pure logic: what the lab holds and does. The screen stays as it is (it now edits the selected slot, which is the slot it was opened on).

- [ ] **Step 1: `LabPages`**

```java
package io.github.mpdairy.monopaint;

/** Paging for the lab's library (e-ink: pages, not scrolling). Pure, checked on the desktop. */
final class LabPages {
    private LabPages() { }
    /** How many pages {@code count} cards take at {@code perPage} a page; at least one. */
    static int pages(int count, int perPage) { int per = Math.max(1, perPage); return Math.max(1, (count + per - 1) / per); }
    /** The page holding card {@code index}. */
    static int of(int index, int perPage) { return Math.max(0, index) / Math.max(1, perPage); }
    /** {@code page} kept inside the pages there are. */
    static int clamp(int page, int count, int perPage) { return Math.max(0, Math.min(page, pages(count, perPage) - 1)); }
}
```

- [ ] **Step 2: `BrushLab` on six slots**

Read `BrushLab` whole first. Change it so:
- It holds `private final Brush[] brushes = new Brush[ToolSlots.COUNT]` (each `copyOf(slots.brush(i))`) and `private int selected`. The constructor keeps its signature `BrushLab(ToolSlots slots, int selected, Random random)`; the `final int slot` field goes.
- `brush()` is `brushes[selected]`; `parts`/`settings` follow the selected brush (`show(Brush)` writes `brushes[selected]`).
- `int selected()`, `Brush slot(int i)`, `Brush[] results()` (a copy of the six).
- `void select(int i)`: the lab now works on slot `i`; not an Undo step; `fresh()` variations; status `"Slot " + (i + 1) + ": " + opened()`.
- History holds steps, not bare brushes:
  ```java
  private static final class Step { final int slot; final Brush before; Step(int slot, Brush before) { this.slot = slot; this.before = before; } }
  ```
  `remember()` pushes `new Step(selected, copyOf(brush()))`; `undo()` pops, sets `selected = step.slot`, `show(step.before)`, `fresh()`, status `"Back to " + name + " in slot " + (slot + 1) + "."`.
- `open(Brush)` becomes `put(Brush picked)`: a library card or a variation goes into the selected slot (an Undo step). The same brush: status `name + " is in slot N already."`, no step. Otherwise status `name + " is in slot N now. Undo takes it back."`. `pickVariation(i)` calls `put` and keeps its caption status.
- `int surprise()`: the library's cards whose brush isn't `sameAs(brush())`; none → status "Nothing else in the library." and `-1`; else one picked with `random.nextInt(pool.size())`, `put` it, status `"Surprise: " + name + " is in slot N. Undo takes it back."`, return its index in `library()`.
- `library()`: the built-ins in `BuiltinBrush.values()` order, then `slots.library.saved()` (newest first). The order never depends on the slots. `Card` becomes `Card(Brush brush, List<Integer> slots, BrushLibrary.Entry entry)`: `slots` = the indices `i` with `brushes[i].sameAs(brush)` (0-based). `int indexOf(Brush b)`: the card index `sameAs(b)`, or -1.
- `void setAdvanced(boolean advanced)` and a private `locks()`:
  ```java
  private static final ModularLocks NO_LOCKS = new ModularLocks();
  /** Locks belong to Advanced: Simple rolls everything and keeps them for later (ui.js 65). */
  private ModularLocks locks() { return advanced ? partLocks : NO_LOCKS; }
  ```
  Every roll, style, variation and `keeping()` uses `locks()`; the old brushes' `BrushSetting` locks likewise (`advanced ? locks : EnumSet.noneOf(...)`). `toggleLock` still edits `partLocks`/`locks`.
- `result()` goes; `save()` and `load()` act on the selected slot.

- [ ] **Step 3: Wiring**

- `BrushLabActivity`: `static final String SLOT = "slot", BRUSHES = "brushes";`. `back()` puts `SLOT = lab.selected()` and `BRUSHES` = each `results()[i].write()`. On create, `lab.setAdvanced(preferences.brushLabAdvanced())`; `LabSettings`'s Simple/Advanced chips also call `lab.setAdvanced`.
- `LabLibrary`: a card tap calls `lab.put(card.brush)`; `now` stays `card.brush.sameAs(lab.brush())`.
- `PaintActivity.brushLabReturned`: read `SLOT` and `BRUSHES`; for each slot whose brush reads and differs, `inkWash.slots.use(i, brush)`; select `SLOT`; if the selected slot changed, `inkWashBar.brushChanged(SLOT, name)` as today. (Task 7 changes the call to `use(i, brush, true)`.)
- `ModularLabChecks`, `BrushLabChecks`: replace `open(...)` with `put(...)`, `result()` with `results()[lab.selected()]`, `Card.slot` with `Card.slots`, and rewrite exactly the library-order assertion to "built-ins, then saved newest first". Their PASS lines change only in that clause.

- [ ] **Step 4: `LabSlotsChecks`**

Use `BrushLabChecks.slots()` for a fresh store. Assert:
- A lab opened on slot 2 holds all six slots' brushes (`sameAs`), `selected() == 2`, nothing to undo.
- `select(4)`: `brush()` is slot 4's; `canUndo()` still false; status starts "Slot 5:".
- `put(library().get(1).brush)` (Fude): slot 4 holds it, the other five unchanged, `canUndo()`; `put` of the same brush again: no new step, status ends "already.".
- `select(1)`, then `undo()`: slot 4 is back to its brush and `selected() == 4`.
- Changing a built-in in slot 0 (`roll(BrushSetting.PRESSURE)` after `select(0)`) makes `my-ink` in slot 0 only.
- `library()`: the first four are the built-ins in order; save brush A then B (two `randomize()` + `save()` in different slots): B's card comes before A's; after `put(A)` the order is unchanged. The `INK` card's `slots` is `[0]` on a first launch.
- `surprise()` 200 times on one lab (undoing each): never returns -1, never picks the brush the slot held, and every built-in gets picked at least once.
- `results()`: six brushes; unchanged slots `sameAs` `ToolSlots`'.
- Locks: in Advanced (`setAdvanced(true)`), after one `randomize()` (a six-part brush), lock `BrushPart.TIP`; 20 `randomize()` keep the tip choice and dials. `setAdvanced(false)`: within 20 `randomize()` the tip choice changes at least once; `partLocks.parts` still holds TIP.
- `LabPages`: `pages(0,10) == 1`, `pages(10,10) == 1`, `pages(11,10) == 2`, `of(10,10) == 1`, `clamp(5,11,10) == 1`, `clamp(-1,11,10) == 0`.

PASS line: `PASS: the lab holds the six slots and works on the picked one; cards, variations and Surprise go into it and Undo takes any slot's change back, reselecting it; the library keeps one order with slot badges; Simple ignores locks and keeps them; pages`

Add `LabPages` to `sources` (after `BrushLab`) and `LabSlotsChecks` to `checks` (after `BrushLabChecks`).

- [ ] **Step 5: Check and build**

```bash
bash scripts/test_inkwash.sh | grep -c '^PASS'     # 46
bash scripts/test_document.sh | grep -c '^PASS'    # 81
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest   # BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit**

No CHANGELOG line (the screen doesn't change yet).
```bash
git add scripts/test_inkwash.sh app/src/main/java/io/github/mpdairy/monopaint/{BrushLab,LabPages,BrushLabActivity,PaintActivity,LabLibrary}.java app/src/test/java/io/github/mpdairy/monopaint/{LabSlotsChecks,BrushLabChecks,ModularLabChecks}.java
git commit -m "Brush Lab works on the six slots: put, select, Surprise, one library order"
```

---

### Task 2: `LabLayout`: the budgets

**Files:**
- Create: `LabLayout.java`, `LabLayoutChecks.java`
- Modify: `scripts/test_inkwash.sh`

Port `metrics()` (ui.js 129–159) with the spec's changes (Layout → Budgets). All values are dp ints.

- [ ] **Step 1: `LabLayout`**

```java
/** Every dp budget of Brush Lab for a root of width × height dp (spec "Layout"). Pure, checked on the desktop. */
final class LabLayout {
    static final int BAR = 56, STATUS = 28, RULE = 3, TAB = 14, LOCK = 28, STYLE_BUTTON = 34, WIDE = 900;
    final int width, height, main, column, right;
    final boolean advanced, compact, carryShown, styleGrid;
    final int styleRow, styleHeight, randomize, wordsMin, card, carry, row, open, words;
    final int slotWidth, slotHeight, cardWidth, cardHeight, libraryColumns, libraryRows, shelf;
    final int variationColumns, variationRows, variations, under, testWidth, testHeight;
    final int label, value, slider, nameWidth;

    LabLayout(int width, int height, boolean advanced, boolean carryShown) {
        this.width = width; this.height = height; this.advanced = advanced; this.carryShown = carryShown;
        compact = width < WIDE;
        main = height - BAR - STATUS;
        column = advanced ? (width >= 1000 ? 380 : 336) : (width >= 1000 ? 300 : 256);
        right = width - column - RULE;
        randomize = compact ? 50 : 58;
        styleRow = compact ? 27 : 31;
        wordsMin = compact ? 140 : 124;
        row = compact ? 38 : 42;
        carry = carryShown ? 40 : 0;
        int parts = carryShown ? 6 : 5;
        if (!advanced) {
            int grid = 26 + 7 * styleRow + 8;
            styleGrid = (main - grid - randomize - wordsMin - carry) / 5 >= 84;
            styleHeight = styleGrid ? grid : STYLE_BUTTON;
            card = clamp((main - styleHeight - randomize - wordsMin - carry) / 5, 64, 140);
            open = 0;
        } else {
            styleGrid = false; styleHeight = STYLE_BUTTON;
            card = clamp((main - styleHeight - randomize - wordsMin - carry) / 5, 64, 140);
            open = main - styleHeight - randomize - (parts - 1) * row;
        }
        words = main - styleHeight - randomize - carry - 5 * card;
        slotWidth = compact ? 60 : 74; slotHeight = compact ? 50 : 58;
        cardWidth = compact ? 104 : 118; cardHeight = compact ? 98 : 116;
        libraryColumns = Math.max(2, (right - 24) / cardWidth);
        libraryRows = main >= 1100 && !advanced ? 2 : 1;
        boolean oneLine = right - 24 >= 52 + 6 * (slotWidth - 2) + 330;
        int head = oneLine ? Math.max(slotHeight, 34) : slotHeight + 8 + 34;
        shelf = 8 + head + 8 + libraryRows * cardHeight + 10;
        variationColumns = right < 560 ? 3 : 6; variationRows = 6 / variationColumns;
        variations = advanced ? variationRows * (compact ? 84 : 112) : 0;
        under = shelf + (advanced ? variations + 10 : 0);
        testWidth = right - 20; testHeight = main - under - 10;
        label = compact ? 104 : 112; value = compact ? 40 : 44;
        slider = column - RULE - TAB - 2 - 16 - label - value - 8 - 2 * LOCK - 12;
        nameWidth = width - 48 - 40 - 72 - 104 - (compact ? 120 : 176) - (compact ? 48 : 96) - 12;
    }
    /** Choice chips a row holds in the open card. */
    int chipsPerRow() { return Math.max(2, (column - 30) / 92); }
    int chipRows(int chips) { return (chips + chipsPerRow() - 1) / chipsPerRow(); }
    /** The open card's dial row height for {@code dials} rows under {@code chipRows} rows of chips (ui.js 300). */
    int dialRow(int dials, int chipRows) { return clamp((open - 30 - chipRows * 28 - 10) / Math.max(1, dials), 32, 44); }
    /** A Hand grid cell's height (ui.js 297): the grid's head, then Response and Fast is as rows of 44. */
    int handCell(int chipRows) { return clamp((open - 30 - chipRows * 28 - 10 - 22 - 2 * 44) / 3, 30, 46); }
    /** The swap strip's columns for {@code n} tiles, so each is about 1.35 : 1 (ui.js 444–446). */
    int swapColumns(int n) { ... port the loop with pw = right - 24, ph = under - 56 ... }
    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
```

- [ ] **Step 2: `LabLayoutChecks`**

For `MANTA = (1024, 1365)` and `NOMAD = (749, 998)`, both modes, Carry shown and hidden:
- Simple: `styleHeight + randomize + carry + 5 * card + words == main`, `words >= wordsMin`, `card >= 84`, `styleGrid`.
- Advanced: the same sum with `words >= 0`; open: `styleHeight + randomize + (parts - 1) * row + open == main`.
- For every part and every offered choice (`ModularFeatures.offered`), with its shown dials (`ModularFeatures.shown`) and its part's chip count: `dialRow(...) >= 32`; for the Hand, `30 + chipRows * 28 + 10 + 22 + 3 * handCell + 2 * 44 <= open`.
- `slider >= 80`, `libraryColumns >= 3`, `testHeight >= 500`, `testWidth >= 380`, `under + 10 + testHeight == main`, `nameWidth >= 200`.
- The spec's table, exactly: Manta Simple test 913 × 701, Advanced 907 × 621; Nomad Simple 688 × 470, Advanced 510 × 390; Manta Simple cards 140, Nomad Simple 100; Manta open card 1021, Nomad 678; library 5/5/4/3 columns; Manta Simple 2 library rows.
- `swapColumns(n)` for n = 2…8 on all four: each tile's aspect between 0.9 and 2.2.

PASS line: `PASS: Brush Lab fits the Manta and the Nomad in Simple and Advanced with nothing scrolling: every region adds up to the screen, every part's dials fit its open card, sliders are at least 80 dp and the test area keeps the most room`

Add `LabLayout` to `sources`, `LabLayoutChecks` to `checks`.

- [ ] **Step 3: Check, build, commit**

```bash
bash scripts/test_inkwash.sh | grep -c '^PASS'     # 47
bash scripts/test_document.sh | grep -c '^PASS'    # 82
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
git add scripts/test_inkwash.sh app/src/main/java/io/github/mpdairy/monopaint/LabLayout.java app/src/test/java/io/github/mpdairy/monopaint/LabLayoutChecks.java
git commit -m "LabLayout: Brush Lab's budgets for the Manta and the Nomad"
```

---

### Task 3: Part samples

**Files:**
- Modify: `WildSample.java`, `ModularSizes.java`, `scripts/test_inkwash.sh`
- Create: `PartSampleChecks.java`

Each part card draws what its part adds (ui.js 482–546).

- [ ] **Step 1: Paths**

In `WildSample`:
- Points gain tilt: `{x, y, pressure travel, time ms, tiltX, tiltY}`; `draw` passes `point[4], point[5]` when present, else `0, 0` (upright, as today). Check `NativePen`/`BrushTestPad` for the tilt units `DrawingStroke.sample` expects and convert the prototype's degrees to them.
- `static float[][] wave(int w, int h, float pad, float amp, double waves, boolean ramp, boolean tilt)`: port `wavePath` (ui.js 483–494): `n = max(30, ceil((w − 2 pad) / 2.5))`; envelope `sin(πu)`; pressure `.15 + .78 env^.7`, or for `ramp` `.08 + .92 u^1.2`; time steps `2 + 4(1 − env)` or `2 + 5(1 − u)`; tilt (ui.js 490) only when `tilt`.
- `enum Shape` appends `WAVE`, `RAMP` (never reorder: the cache key names them).

- [ ] **Step 2: Roles**

- `ModularSizes.sampleSize(ModularBrush b, float ref, BrushPart role)`: today's rule, then Paper `× 1.7`, Hand `× 1.8` when under `ref / 7` (ui.js 505–506). The two-argument form calls it with `null` (the loop).
- `static int[] WildSample.part(ModularBrush b, BrushPart role, int width, int height)`: `ref = min(height, width × .62)`; Medium and Life: the loop (`loopPad` as today) over a grey band; Carry: a wave (`amp × .4`, `.75` waves) over a dark band (tone 60) and a darker strip (40 at 20–24 %); Paper: a wave (`amp × .35`, `.75` waves); Hand: `wave(..., amp × .6, 1, ramp, tilt)`; Tip: `wave(..., amp, 1.25, false, false)`. Bands: x from 42 % to 58 % of the width, tone 150 (60 for Carry), laid on the blank page before the stroke as `ui.js` 518–523 does (find the `ToneDocument` call that sets a wash-layer tone). Pad and amp as ui.js 525. At Dark. Kept by `key(...) + " " + role + " " + width + "x" + height`.

- [ ] **Step 3: `PartSampleChecks`**

- `wave(400, 120, 20, 30, 1.25, false, false)`: first x 20, last x 380, ≥ 30 points, middle pressure > both ends, tilt 0.
- `ramp`: pressure never falls; the last point's lean is about 55° (within 1°); the first 0.
- For each of the 14 styles rolled with `new WildBrush.Random(77)`, each role, a 200 × 120 tile: more than 100 pixels darker than 238 outside the band.
- Medium's tile: a pixel at (100, 2), in the band and off the stroke, is grey.
- The same call twice returns the same array; another role, another array.

PASS line: `PASS: part cards draw what their part adds (a wave for the tip and paper, the loop over a grey band for medium and life, bands for carry, a pressure ramp with a turning tilt for the hand), for every style, kept per role`

- [ ] **Step 4: Check, build, commit**

```bash
bash scripts/test_inkwash.sh | grep -c '^PASS'     # 48
bash scripts/test_document.sh | grep -c '^PASS'    # 83
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
git add scripts/test_inkwash.sh app/src/main/java/io/github/mpdairy/monopaint/{WildSample,ModularSizes}.java app/src/test/java/io/github/mpdairy/monopaint/PartSampleChecks.java
git commit -m "Part samples: each part card draws what its part adds"
```
(`WildSampleChecks` must still pass unchanged: the loop and swoosh don't change here.)

---

### Task 4: The new frame

**Files:**
- Modify: `BrushLabActivity.java`, `LabLibrary.java`, `LoopCard.java`, `BrushTestPad.java`, `LabSettings.java`, `CHANGELOG.md`
- Create: `SampleQueue.java` (Android)

Android views only; every size from `LabLayout` (rebuilt when the root's size or the mode changes). Look: `LabUi` (square, hard rules, mono, inverted only for text buttons and chips).

- [ ] **Step 1: The skeleton**

`BrushLabActivity.onCreate` builds, from the root's measured size (`LabLayout` in dp):
- bar (56) | rule 3 | main row: column container (a `FrameLayout`, `layout.column` wide, so flyouts can overlay it) | 3 dp rule | right column: the test area (`testHeight` + 10 margin) then the under area (`under`, a `FrameLayout`) | rule 3 | status (28, one line, ellipsized).
- A `FrameLayout` over the main row holds the Load sheet (Step 4).
- Remove the "BRUSH LAB" title, "How it works" (`HOW`, `showHelp`) and the bottom share box.

- [ ] **Step 2: The bar**

Back · name + pencil (today's `renderName`, `name.setMaxWidth(dp(layout.nameWidth))`) · **LOAD** (outlined `LabButton`, `setOn` while the sheet is open) · **SAVE** (solid, `ic_ph_save`, 36 tall) · a SIMPLE | ADVANCED segment (two `LabButton.chip`s butted, as `LabSettings.styleHead` does now; "ADV" when compact) → `preferences.setBrushLabAdvanced`, `lab.setAdvanced`, rebuild with a new `LabLayout` · Undo (icon only when compact). Remove `styleHead`'s mode chips from `LabSettings`.

- [ ] **Step 3: The test area**

A 2 dp frame, 10 dp in. `BrushTestPad` fills it. Overlaid: **CLEAR** top left (6 dp in); the S/M/L `LabSizeButton`s top right (Task 8 replaces them); the corner loop's area moves to `top = 58 dp`, `width = min(300 dp, 34 % of the pad)`, `height = .66 width`, `10 dp` from the right (ui.js 578): give `BrushTestPad` a `cornerArea(Rect)` setter instead of its constants. `BrushTestPad.onSizeChanged` keeps the old page: copy the overlapping region of the old `ToneDocument` into the new blank one (top-left anchored) rather than starting blank.

- [ ] **Step 4: The Load sheet**

Under the bar, over the main row, full width, 3 dp frame (ui.js 456–463): "LOAD A CODE" field + LOAD; "SHARE CODE" read-only (`lab.code()`) + COPY; a grey note line for a bad code. Today's `buildShare`, `copyCode`, `loadCode` move here. Loading closes it.

- [ ] **Step 5: The shelf (`LabLibrary`)**

`LabLibrary` takes the `LabLayout`. It draws, in the under area:
- a slots row: `TOOLBAR SLOTS »` (two lines, 11 sp heading) and six `LoopCard`s (`slotWidth × slotHeight`, sample only, the number top left); the selected one framed with a tab. Tap → `lab.select(i)`, then turn to `LabPages.of(lab.indexOf(lab.brush()), perPage)` when it is in the library.
- a head: `LIBRARY → SLOT N`, **⚄ SURPRISE ME** (outlined) → `int i = lab.surprise(); if (i >= 0) page = LabPages.of(i, perPage);`, ◀ `page/pages` ▶; while a delete waits, today's confirm.
- `libraryRows × libraryColumns` cards: `LoopCard` with the two caption lines and the name (grey, dropped when the card is too short), slot badges top left (black squares, white digits, 1-based); empty cells keep the size. Tap → `lab.put(card.brush)`; long-press a saved card → today's delete confirm. Save turns to the saved brush's page.
- In Advanced, above the shelf: the six variations (`variationColumns × variationRows`, `LoopCard` captions) and the icon-only dice (today's `renderVariations`, moved).

- [ ] **Step 6: `LoopCard` and `SampleQueue`**

- `LoopCard`: the lab's brush is framed (4 dp) with a 6 dp tab under its caption, never inverted (CSS `.tcard.now`). An optional badge list and a sample-only form (for slots).
- `SampleQueue` (Android): one `HandlerThread`; `request(View owner, Supplier<int[]> pixels, Consumer<Bitmap> done)`: newest request first, a newer request for the same owner replaces the older, `done` runs on the main thread. `LoopCard.onDraw` shows white until its bitmap arrives, then invalidates once.

- [ ] **Step 7: The column**

`LabSettings` renders into the column container as it does now, inside a `ScrollView` until Tasks 5–6 (old rows, six-part parts). Its Randomize and style chips work as today.

- [ ] **Step 8: Render less**

`render()` redraws a region only when its inputs changed: the bar (name, undo, mode), the column (brush identity, locks, mode), the corner (brush, size), the shelf (selection, page, library size), the variations (variations list). Status always.

- [ ] **Step 9: Build and check**

```bash
bash scripts/test_inkwash.sh | grep -c '^PASS'     # 48
bash scripts/test_document.sh | grep -c '^PASS'    # 83
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
```
Leave for Elling (no adb): open the lab from slot 3; tap slot 5, then a library card; Surprise me twice, Undo; Load a code; Back: both slots changed on the bar.

- [ ] **Step 10: Commit**

CHANGELOG (Unreleased):
```
- Brush Lab is laid out anew: the test page takes most of the screen, with the settings on the left and, under the
  test page, your six toolbar slots and the library. Tap a slot, then a library brush, to put it there; "Surprise me"
  puts in a random one, and Undo takes it back. Load and Save sit beside the brush's name; Load also shows the share
  code to copy. Variations show only in Advanced.
```
```bash
git add CHANGELOG.md app/src/main/java/io/github/mpdairy/monopaint/{BrushLabActivity,LabLibrary,LoopCard,BrushTestPad,LabSettings,SampleQueue}.java
git commit -m "Brush Lab: the new frame, with the toolbar slots and library under the test page"
```

---

### Task 5: Simple: part cards and the swap strip

**Files:**
- Modify: `LabSettings.java`, `BrushLab.java`, `ModularWords.java`, `BrushLabActivity.java`, `ModularLabChecks.java`, `CHANGELOG.md`
- Create: `PartCard.java`, `SwapStrip.java`, `app/src/main/res/drawable/ic_part_{tip,medium,carry,paper,life,hand}.xml`

- [ ] **Step 1: Pure additions, with checks**

- `BrushLab.swaps(BrushPart part)` → `List<Swap>` (`Swap{PartChoice choice; Brush brush; boolean now}`): port `swapList` (ui.js 425–440) for the offered choices (`ModularFeatures.offered`) plus the current one: each a copy with only that part switched (`ModularBrush.switchTo`, normalized); for the Hand, a fresh preset keeping the brush's Response (`GAIN`) and Fast is (`DIR`); Custom never offered. No "takes ink" tile (Stage 3).
- `ModularWords.sentences(String text)` → the text's sentences, each with its full stop; `ModularWords.firstSentences(String text, int count)` joins the first `count` (at least 2).
- `ModularLabChecks` gains: for every part of three rolled brushes, `swaps(part)` has one `now`, every other tile differs from the brush only in that part (`part(p).equals` for the others), and the Hand tiles keep `GAIN` and `DIR`; `sentences` splits "A. B. C." into three and `firstSentences(…, 1)` keeps two. (Same PASS line, one clause added: "swaps change one part".)

- [ ] **Step 2: Glyphs**

Six 24 dp vector drawables from ui.js `GLYPH` (15–22), black strokes 2 dp (hand 1.8), as the `ic_ph_*` drawables are written.

- [ ] **Step 3: `PartCard`**

A `ViewGroup` for one part, `layout.card` tall (Carry slim: 40):
- the 14 dp tab with the part's pattern (CSS `.t-tip` … `.t-hand`: diagonal stripes, `HATCH` grey, horizontal stripes, a 5 dp dot grid, a ring, solid black), a 2 dp rule;
- head: glyph 16, label (11 sp bold caps), then in Advanced a lock and a die (Task 6);
- the choice in words (`ModularWords.choiceWords`, 15 sp bold, one line, ellipsized);
- the part sample (`WildSample.part`, through `SampleQueue`), `col × .38` (`.36` compact) wide, `card − 42` tall, top right, 1 dp `RULE` border;
- the macro row (26): the low word, a `BandSlider` over `choice.range(choice.macro)` (no band), the high word (10 sp grey caps). Off/None: "Nothing moves after the nib passes." for Life None; Carry Off is hidden.
- Picked (its swap strip open): a 4 dp inset frame and a 6 dp tab on its right edge (CSS `.card.sel`), never inverted.
- Tap outside the slider → `onPick`. Slider: `lab.startSlide()`, `lab.slide(macro, v)`, `lab.endSlide(macro)`, then `changed`.

- [ ] **Step 4: Simple column (`LabSettings`)**

Six-part brush, top to bottom, sizes from `LabLayout`, no `ScrollView`:
1. "STYLE" and the 14 styles as a 2 × 7 grid of `styleRow`-tall cells (CSS `.stylegrid`), the brush's inverted; or a "STYLE: X ▾" button when `!styleGrid` (opens the flyout from Task 6).
2. RANDOMIZE, `randomize` tall, full width (today's button).
3. The cards: Tip, Medium, Carry (only when not Off), Paper, Life, Hand.
4. "THIS BRUSH" and `firstSentences(plainWords, n)`, the largest `n` whose `StaticLayout` fits `words − 26` dp at 12.5 sp (never below 2).
An old brush: Style, Randomize, "THIS BRUSH" from `BrushCaptions.describe(brush)` joined with ". ", no cards.

- [ ] **Step 5: `SwapStrip`**

In the under area, over the shelf and variations, the same height (the test area doesn't move): head (tab, glyph, "TIP: PICK ONE", ✕), then `layout.swapColumns(n)` columns of `LoopCard`-style tiles: the sample (`WildSample.part` with the part's role), the choice's label, a note (Medium: `choiceWords`; Life: "final as the nib passes" / "the last N mm settle; nothing after lift" / "keeps changing for N s after you lift" (ui.js 423); Hand: `handWords`). The current tile framed. Tap → `lab.choose(part, choice)` (an Undo step), the strip stays. ✕ or tapping the card again closes it.

- [ ] **Step 6: Check, build, commit**

```bash
bash scripts/test_inkwash.sh | grep -c '^PASS'     # 48
bash scripts/test_document.sh | grep -c '^PASS'    # 83
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
```
CHANGELOG:
```
- Brush Lab's Simple mode shows a brush as cards, one per part (tip, medium, paper, life, hand), each with a small
  sample of what it adds and one slider named by what it does (thin to broad, dry to wet, smooth to rough, still to
  lively, steady to responsive). Tap a card to see every choice for that part drawn with your brush, and tap one to
  use it. Everything fits on the screen of the Manta and the Nomad, without scrolling.
```
```bash
git add CHANGELOG.md app/src/main/java/io/github/mpdairy/monopaint/{LabSettings,BrushLab,ModularWords,BrushLabActivity,PartCard,SwapStrip}.java app/src/main/res/drawable/ic_part_{tip,medium,carry,paper,life,hand}.xml app/src/test/java/io/github/mpdairy/monopaint/ModularLabChecks.java
git commit -m "Brush Lab Simple: part cards with one slider each, and the swap strip"
```

---

### Task 6: Advanced: open parts, dials and the Hand grid

**Files:**
- Modify: `LabSettings.java`, `PartCard.java`, `BrushLab.java`, `ModularLabChecks.java`, `CHANGELOG.md`
- Create: `HandGrid.java`

- [ ] **Step 1: Pure additions, with checks**

In `BrushLab` (six-part brush):
- `boolean active(PartDial dial)`: port `dialActive` (ui.js 246–258), minus the Takes ink lines (hidden). A dial that changes nothing now is shown greyed.
- `void cycleCell(PartDial cell)`: off (< .17) → .5, some (< .75) → 1, full → 0 (ui.js 697); then the hand is named (`ModularBrush.namedHand`), an Undo step.
- `void toggleRowLock(int row)` and `void rollRow(int row)`: rows are `PartDial.HAND_CELLS` in threes (pressure, tilt, speed; verify the order against `ModularWords.handWords`); a row is locked when its three dials are; rolling skips locked dials (ui.js 703, 706–709).
- `void flipFast()`: `DIR` 0 ↔ 1, an Undo step, status "Fast: thinner." / "Fast: thicker.".
`ModularLabChecks` gains: `active` greys Ink's Line/Bleed/Bloom at Wet 0 and Wash's Bloom at Wet .3; `cycleCell` cycles three times back to off and makes the hand Custom or a named preset; a locked row survives `rollRow` and `randomize()` in Advanced; `flipFast` twice restores.

- [ ] **Step 2: One row layout for every dial**

Replace `LabSettings.row(...)` with one row used by six-part dials and old settings alike: `label (layout.label wide, 11 sp bold caps, up to 2 lines, no ellipsis) | middle (slider or chips) | value (layout.value, grey, right-aligned) | 8 dp | lock (28) | die (28)`. The lock moves from the left edge to the right, 8 dp clear of the value (Elling: more padding left of the locks). Inactive dials: label and value in `RULE` grey. Rows `dialRow(...)` tall.

- [ ] **Step 3: Fast switch**

Generalize `speedSwitch` into `fastSwitch(boolean thicker, boolean locked, Runnable flip)`: `SquareSwitch` and "fast = thinner" / "fast = thicker", no arrows. Old brushes' Speed and the six-part Hand's `DIR` both use it.

- [ ] **Step 4: `HandGrid`**

The 3 × 3 grid (ui.js 277–287, CSS `.hgrid`, `.cell`): heads Size / Dark / Width·angle; rows Pressure / Tilt / Speed; a cell's fill is hatched for some, solid for full, its level written in caps; a speed-size cell shows "+" or "−" for Fast is; each row ends with its lock and die. Cells `handCell(chipRows)` tall. Tap → `cycleCell`.

- [ ] **Step 5: Advanced column**

1. "STYLE: <name> ▾" (34): a flyout over the column (3 dp frame, the 14 chips, ui.js 314) → `lab.pickStyle`, closes.
2. RANDOMIZE, full width.
3. Closed: `PartCard`s with lock and die in the head (`lab.toggleLock(part)`, `lab.rollPart(part)`), then "THIS BRUSH" when `words >= wordsMin`.
   A card tapped opens: the others collapse to `row`-tall rows (tab, glyph, label 64 wide, choice, lock); the open card (`open` tall): head (choice in words, lock, die, ✕), the choice chips (offered + current; locked part disables them), then its dials via Step 2's row (choice dials as segmented chips, ui.js 268–269), Hand: the grid, Response, Fast switch. The swap strip opens with it (Task 5).
4. Old brushes: today's rows in Step 2's layout, in a `ScrollView` (the one scrolling case).

- [ ] **Step 6: Check, build, commit**

```bash
bash scripts/test_inkwash.sh | grep -c '^PASS'     # 48
bash scripts/test_document.sh | grep -c '^PASS'    # 83
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
```
CHANGELOG:
```
- Brush Lab's Advanced mode opens one part at a time, with all of that part's settings, each with its own lock and
  die, on one screen without scrolling. The hand is a grid: tap a cell to say how much pressure, tilt or speed changes
  size, darkness or the nib. "Fast" is a switch that says what it does. Locks only work in Advanced; Simple rolls
  everything and keeps your locks for when you come back.
```
```bash
git add CHANGELOG.md app/src/main/java/io/github/mpdairy/monopaint/{LabSettings,PartCard,BrushLab,HandGrid}.java app/src/test/java/io/github/mpdairy/monopaint/ModularLabChecks.java
git commit -m "Brush Lab Advanced: open one part with every dial, the hand grid and the Fast switch"
```

---

### Task 7: Shared size and shade swatches

**Files:**
- Create: `ToolSwatches.java`, `ToolSwatchesChecks.java`
- Modify: `ToolSlots.java`, `InkWash.java`, `ModularSizes.java`, `DrawingPad.java`, `InkWashBar.java`, `BrushLabActivity.java`, `BrushLab.java`, `PaintActivity.java`, `ToolSlotsChecks.java`, `scripts/test_inkwash.sh`, `CHANGELOG.md`

- [ ] **Step 1: `ToolSwatches`**

```java
/**
 * The three size swatches and three grey swatches every tool picks from, shared by all tools, and each tool's own
 * size in px and darkness, so changing a swatch never changes another tool (spec "The toolbar"). Six-part brushes draw
 * at the px and darkness; old brushes and the eraser at the swatch's index as S/M/L and Dark/Light/Lighter. Kept in
 * the slots' store, read each time. Pure, checked on the desktop.
 */
final class ToolSwatches {
    static final double[] SIZES = {4, 16, 120}, GREYS = {1, 1 - InkWashShades.INK_GRAY / 255.0, 1 - InkWashShades.INK_LIGHT_GRAY / 255.0};
    static final double[] STOPS = {2, 4, 8, 16, 32, 64, 120, 240, 360}, FLYOUT = {1, .87, .73, .6, .47, .33, .2, .1};
    static final String PX = "inkwash_px_", GREY = "inkwash_grey_", SIZE_SWATCHES = "inkwash_size_swatches", GREY_SWATCHES = "inkwash_grey_swatches";
    /** What a tool draws at. */
    static final class Look { final InkWashSizes.Preset preset; final InkWashShades.Shade shade; final double px, darkness; ... }
    ToolSwatches(BrushLibrary.Store store)
    double size(int i); double grey(int i);
    int sizeIndex(String key)   // the preset stored under ToolSlots.SIZE + key, as an index
    int greyIndex(String key)   // the shade stored under ToolSlots.SHADE + key
    Look look(String key, Brush brush)   // px: stored, else diameter(brush, preset); darkness: stored, else ModularSizes.darkness(shade)
    void chooseSize(String key, int i)   // stores the preset name of i and px = size(i)
    void setSize(String key, int i, double px)   // swatch i = clamp(px, ModularSizes.MIN, MAX), then chooseSize
    void chooseGrey(String key, int i); void setGrey(String key, int i, double darkness)   // 0–1
    /** A brush of the other family (line media ↔ paint media) takes the size swatch nearest its own Medium size. */
    void brushChanged(String key, Brush before, Brush after)
    static boolean custom(Brush brush)   // a six-part brush: its sizes and greys can be set
    static float diameter(Brush brush, InkWashSizes.Preset preset)   // moved from InkWash.diameter, which now calls it
    static double position(double px); static double sizeAt(double t)   // the modal's log slider, MIN–MAX (ui.js 340–341)
    static String mm(double px)   // "1.4 mm", "12 mm" (ui.js 342)
}
```
Swatch triples are stored as "4,16,120" with `Locale.ROOT`; unreadable values fall back to the defaults. The family is `Brush.inks()`; the new brush's own size is `diameter(after, MEDIUM)`; nearest by `|log(a / b)|`.

- [ ] **Step 2: Wiring**

- `ToolSlots` builds `final ToolSwatches swatches = new ToolSwatches(store)`. `use(slot, brush)` and `roll(slot)` call `swatches.brushChanged(key(slot), before, after)` after a change; `use(slot, brush, boolean keepSize)` skips it. `PaintActivity.brushLabReturned` uses `use(i, brush, true)`; `BrushLab.put`, `surprise`, `load`, `randomize`, `pickStyle` call `slots.swatches.brushChanged(ToolSlots.key(selected), before, after)` so the lab's test page matches.
- `InkWash`: `ToolSwatches.Look look(int tool)` (`slots.swatches.look(key(tool), brush(tool))`); `stroke(ToneDocument, Brush, ToolSwatches.Look, WatercolorSession, LongSupplier)`: a six-part brush → `ModularStroke.start(document, parts, look.px, look.darkness, session)`; others → the existing preset/shade `stroke`. `preset(tool)`/`shade(tool)` read through the swatches' indices; `setPreset`/`setShade` become `chooseSize(i)`/`chooseGrey(i)` (the wet-paint drying rule now compares looks). `diameter` delegates.
- `ModularSizes.swatch(ModularBrush b, double darkness)`; the `Shade` form calls it.
- `DrawingPad`: the pen stroke uses `InkWash.stroke(document, brush, look(chosen), ...)`; the eraser outline unchanged.
- `InkWashBar` panel: size dot *i* shows `custom ? swatches.size(i) : diameter(brush, preset i)`; shade *i* shows `custom ? ModularSizes.swatch(parts, grey(i)) : InkWash.swatch(brush, shade i)`; taps call `chooseSize`/`chooseGrey`; the picked one is `sizeIndex`/`greyIndex`.
- `BrushLabActivity`: test strokes and the corner at `swatches.look(ToolSlots.key(lab.selected()), lab.brush())`; the S/M/L buttons call `chooseSize`.

- [ ] **Step 3: `ToolSwatchesChecks`** (and `ToolSlotsChecks`)

- Defaults: sizes 4, 16, 120; greys 1, .529, .314 (3 decimals).
- A six-part slot with nothing stored draws at `diameter(brush, preset)` (what it drew before) and `ModularSizes.darkness(shade)`.
- `chooseSize(k, 2)`: preset `LARGE` stored, px 120. `setSize(k, 0, 7)`: swatch 0 is 7; `k`'s px 7; another key's px unchanged. Clamps: 1000 → 360, .5 → 1.5; greys −1 → 0, 2 → 1.
- An old brush's look uses the index's preset and shade and ignores px and darkness.
- Family rule: a Fineliner (4 px) slot given a Watercolor takes 120 (index 2); Watercolor → another Watercolor keeps its px; `use(slot, brush, true)` keeps it.
- `sizeAt(position(p)) ≈ p` for every stop; `position(1.5) == 0`, `position(360) == 1`; `mm(16)` is "1.4 mm", `mm(240)` "20 mm".
- Unreadable stored triples fall back to the defaults; written values use '.'.
`ToolSlotsChecks`: its assertions for `use` still pass (rewrite only any that assumed sizes are untouched by a family change).

PASS line: `PASS: three size and three grey swatches shared by every tool, each tool keeping its own px and darkness; six-part brushes draw at them, old brushes and the eraser at S/M/L and Dark/Light/Lighter; nothing changes size until a swatch is picked; a brush of the other family takes the nearest size`

Add `ToolSwatches` to `sources` (before `ToolSlots`), `ToolSwatchesChecks` to `checks`.

- [ ] **Step 4: Check, build, commit**

```bash
bash scripts/test_inkwash.sh | grep -c '^PASS'     # 49
bash scripts/test_document.sh | grep -c '^PASS'    # 84
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
```
CHANGELOG:
```
- Six-part brushes now share three sizes, 4, 16 and 120 pixels at first, instead of sizes tied to each style. A slot
  keeps the size it had until you pick another. When a slot gets a paint after a pen, or a pen after a paint, it takes
  the size closest to the new brush's own. The built-in tools, older brushes and the eraser keep their own three sizes.
```
```bash
git add scripts/test_inkwash.sh CHANGELOG.md app/src/main/java/io/github/mpdairy/monopaint/{ToolSwatches,ToolSlots,InkWash,ModularSizes,DrawingPad,InkWashBar,BrushLabActivity,PaintActivity,BrushLab}.java app/src/test/java/io/github/mpdairy/monopaint/{ToolSwatchesChecks,ToolSlotsChecks}.java
git commit -m "Shared size and grey swatches; each tool keeps its own px and darkness"
```

---

### Task 8: Swatches on screen and the slot swoosh

**Files:**
- Create: `SwatchFace.java`, `SizeModal.java`
- Modify: `InkWashBar.java`, `WildSample.java`, `BrushLabActivity.java`, `WildSampleChecks.java`, `CHANGELOG.md`
- Delete: `LabSizeButton.java` (if nothing else uses it; grep first)

- [ ] **Step 1: `SwatchFace`**

One view for a size or a grey (CSS `.tslot`): no border. Size: a black dot as wide as it draws, up to 80 % of the face, beyond that the largest dot and its mm (today's `SizeDot` rule, moved here; `SizeDot` goes). Grey: the flat grey; lighter than 235 gets a 1 px inset `RULE` line. Picked: a 5 dp black tab under it, 6 dp in from each side; nothing else. A 13 dp black ▾ corner tab, top right, only when `ToolSwatches.custom(brush)`. Tap → choose; the ▾ tab or a long-press → edit.

- [ ] **Step 2: `SizeModal` and the grey flyout**

- `SizeModal` (a `Dialog`, square, 3 dp frame, no animation): head "SIZE N", "16 px · 1.4 mm", DONE; a 120 dp view showing the true-size dot 1 : 1, clipped; a `BandSlider` 0–1 over `ToolSwatches.position(px)`, labelled "THIN" and "BIG BRUSH" under its ends; the nine stops as chips. Moving or a stop → `setSize(key, i, sizeAt(t))`, the faces redraw; DONE closes.
- The grey flyout (a `PopupWindow` beside the swatch): 8 `SwatchFace` greys, 44 dp, 4 × 2 (`ToolSwatches.FLYOUT`), the current one tabbed. A tap → `setGrey(key, i, d)` and closes.

- [ ] **Step 3: Faces in the slot panel and the lab**

- `InkWashBar.fillToolPanel`: the three `SizeDot`s and three `PaletteSwatch`es become `SwatchFace`s (the eraser: sizes only, no ▾). Picking applies and closes the panel, as today; ▾ opens the modal or flyout and leaves the panel open.
- `BrushLabActivity`: the test area's top right gets the selected slot's three sizes and three greys as 44 dp `SwatchFace`s in two groups (ui.js 345–351, CSS `.tools`), replacing the S/M/L buttons. Picking re-draws the corner loop; the test page stays.

- [ ] **Step 4: The slot swoosh**

- `WildSample.swoosh`: pressure `p = .1 + .85 · smoothstep(min(1, t / .7))` (`smoothstep(x) = x²(3 − 2x)`): light, rising, firm for the last ~30 %; time steps `6 − 3 (p − .1) / .85` ms (slower where firm). The bar's `swooshPreset` goes.
- `InkWashBar.showBrush(slot)`: the swoosh in the slot's grey at the slot's size: six-part brushes at `min(look.px, height / 4)` px and `look.darkness`; old brushes at the slot's preset and shade. Past a quarter of the button's height, the size in mm (`ToolSwatches.mm`, 9 sp bold, bottom right; white on the selected, inverted button). The cache key includes size and grey. Redraw a slot's face when its size or grey changes.
- `WildSampleChecks.swoosh()`: rewrite exactly its pressure assertions: `p[0]` .1, `p[42]` (t = .7) .95, `p[30]` ≈ .78, never falling; positions unchanged; drop the `swooshPreset` line.

- [ ] **Step 5: Check, build, commit**

```bash
bash scripts/test_inkwash.sh | grep -c '^PASS'     # 49
bash scripts/test_document.sh | grep -c '^PASS'    # 84
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
```
(Fix any `androidTest` that looked for `SizeDot`/`PaletteSwatch` in the slot panel.)

CHANGELOG:
```
- The three sizes and three greys in a slot's panel can be set: tap the small corner tab (or hold) on a size to pick it
  from thin to big brush, or on a grey to pick one of eight. Every slot uses the same three sizes and greys, and each
  slot remembers the one it uses. They no longer have borders: a short bar under the one in use marks it. Brush Lab's
  test page has the same sizes and greys.
- Each slot's button shows its brush's real mark at its own size and grey, getting firmer to the end like a real
  stroke; a size too big to show is written in millimetres.
```
```bash
git add CHANGELOG.md app/src/main/java/io/github/mpdairy/monopaint/{SwatchFace,SizeModal,InkWashBar,WildSample,BrushLabActivity}.java app/src/test/java/io/github/mpdairy/monopaint/WildSampleChecks.java
git rm app/src/main/java/io/github/mpdairy/monopaint/LabSizeButton.java   # only if unused
git commit -m "Swatch faces, the size modal and grey flyout, and slot previews at their size"
```

---

### Task 9: Full verification and as-built notes

**Files:**
- Modify: `docs/archive/superpowers/specs/2026-10-15-lab-screen-design.md` (an "As built" section at the end)

- [ ] **Step 1: Everything passes**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" PATH="$JAVA_HOME/bin:$PATH"
bash scripts/test_inkwash.sh | grep -c '^PASS'     # 49
bash scripts/test_document.sh | grep -c '^PASS'    # 84
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease   # BUILD SUCCESSFUL
git diff 155dec4 -- app/src/test/java/io/github/mpdairy/monopaint/WashGoldenChecks.java app/src/test/java/io/github/mpdairy/monopaint/LegacyGoldenChecks.java   # empty
```

- [ ] **Step 2: Review the diff against CLAUDE.md's rules**

`git diff 155dec4 --stat`, then read every changed main file for: one row layout for every dial and setting (not two); one swatch face for sizes and greys, bar and lab; the lock set used through `locks()` only; `InkWash.diameter` moved, not copied; no `android.*` in `BrushLab`, `LabPages`, `LabLayout`, `ToolSwatches`, `WildSample`, `ModularSizes`; old-engine strokes untouched.

- [ ] **Step 3: As-built notes**

Append "As built (2026-10-…)": check counts, anything that differs from the spec and why, the measured `LabLayout` numbers if they changed, and the device checks for Elling:
- Manta and Nomad (or the smaller-panel preview), Simple and Advanced: nothing scrolls, nothing is cut off; two-line labels fit at the lab's larger text.
- Open each part in Advanced on the Nomad; the Hand grid; the Fast switch.
- Tap slot 5, a library card, Surprise me, Undo; Back: the bar shows the changed slots.
- How quickly the lab settles after a change (samples arriving) on the Manta.
- Size modal and grey flyout from the bar and the lab; a Watercolor dropped into a Fineliner slot takes a broad size.
- An old Wild brush and a built-in: fixed sizes, no ▾, draw as before.

- [ ] **Step 4: Commit**

```bash
git add docs/archive/superpowers/specs/2026-10-15-lab-screen-design.md
git commit -m "docs: lab screen Stage 2 as built"
```
