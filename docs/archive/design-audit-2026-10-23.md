# Design audit, 2026-10-23 (phase 1: capture and audit, nothing restyled)

Every screen and popup of Inkwash, captured on the Manta and audited against **Scribbler Style** (`docs/DESIGN.md`,
`docs/TEXT-STYLE.md`; tokens in `Theme` and `TypeStyle`), with the owner's top rule **shade before line**: things are
told apart by a grey step (grey-200 on paper, paper on grey-200), ink is for text, icons and the one thing on or chosen,
a rule that has to stay is grey-400.

The owner's brief for this pass (2026-10-23): "the thing i want to fix on the brush selection [Tools page] and brush
designer [Brush Lab] is to give it a proper hierarchy, and to link things together, to make it simple and clear what's
going on, mainly." Their earlier device notes: "reorganize brush selection screen", "add colour picker to brush
selection screen", "more swatches below brushes". Those two screens come first below (section 2), with mocks.

## 1. How the pictures were made

- `ScreenShots` (androidTest), in the isolated checks build only (`ing.ell.mybaddrawings.checks`, its own library
  `Document/Inkwash checks`), never the owner's app:
  `./gradlew -PisolatedChecks -PfastChecks :app:assembleDebug :app:assembleDebugAndroidTest`, install both, then
  `am instrument -w -e screens true [-e only lab,tools,draw,menus,app] ing.ell.mybaddrawings.checks.test/io.github.mpdairy.monopaint.WidthInstrumentation`;
  PNGs in the build's `files/screens/`.
- New section `app`: opens a notebook, then each screen, menu and dialog as a tap or hold would (`performClick`,
  `performLongClick`, or the method the button calls), draws the activity's window and every popup and dialog window
  over it at its screen place (`composite`), then closes them (Back to each window, `dismissPanels`, any popup left
  removed). Manta size and the Nomad's (the drawing screen's own simulation, cropped to its frame).
- Fixed on the way: the old `lab` and `menus` sections called methods since renamed (`pick` is now `tapped`,
  `editSize`/`editGrey` are `editAll`, `deleteDrawing` and `addLayer` are gone); the drawing screen starts on the shelf,
  so `draw` and `menus` now open a notebook first; `PrintScreen`'s tables (built on first use, over 15 s in a
  debuggable build) are warmed off the main thread, and the build has `-PfastChecks` (not debuggable), or Brush Lab
  and the Tools page hit Android's 15 s focus limit; every screen draws through Android (`DirectEink.useAndroidDrawing`).
- 155 pictures (Manta and Nomad); kept outside the repo, in the session scratchpad
  `design/shots/` (named below); the mocks in `design/mocks/`.

### Screen inventory

| Screen | Shots (`manta`/`nomad` each unless said) | Drawn by |
|---|---|---|
| Drawing screen (side column, tray, faces, grey strip, tools, layers, brush line, page bar) | `draw-{s}`, `draw-{s}-portrait-1layer`, `-portrait-4layers`, `-landscape-1layer`, `-landscape-4layers` | `InkWashBar`, `DrawLayout`, `SlotTile`, `SwatchBar`/`SwatchFace`, `GreyStrip`, `LayerColumn`, `PageTurner`, `ZoomButton` |
| Zen | `draw-{s}-zen` | `PaintActivity.setZen` |
| A note on the brush line | `draw-{s}-note` | `InkWashBar.note` |
| Words bin (Magnetic Poetry) | `draw-{s}-words-bin` | `WordBin`, `WordsMode` |
| Menu (file menu) | `menu-{s}-file` | `PaintActivity.fileMenu` / `menu` |
| Finger | `menu-{s}-finger` | `PaintActivity.fingerMenu` |
| Fun, Mirrors | `menu-{s}-fun`, `menu-{s}-mirrors` | `FunMenu` via `PaintActivity.menu` |
| Eraser hold (kinds, Clear page) | `menu-{s}-eraser` | `InkWashBar.eraserMenu`, `lightRow` |
| Smudge hold (smudge brushes) | `menu-{s}-smudge` | `InkWashBar.tapSmudge`, `openPanel` |
| Stamp hold | `menu-{s}-stamp` | `InkWashBar.stampMenu` |
| Sizes and Greys (size or grey hold) | `sizes-and-greys-{s}`, `lab-{s}-{basic,advanced}-sizes-and-greys` | `SizeModal` |
| Layer hold | `menu-{s}-layer` | `LayerColumn.menu` |
| Page options (strip hold) | `menu-{s}-page-options` | `PageTurner.Strip.options` |
| Dialogs: Time is up, Delete page, Daily Prompt, New Notebook, Rename, Import PDF box, Pages, Settings | `dialog-{s}-time-up`, `-delete-page`, `-daily-prompt`, `-new-notebook`, `-rename`, `-import-pdf`, `-pages`, `-settings` | `PaintActivity.showDialog` + `Theme.dialog`, `NamePrompt`, `PageOverview`, `AppSettingsDialog` |
| Shelf, its notebook hold | `shelf-{s}`, `shelf-{s}-notebook-menu` | `NotebookScreens.shelf`, `PaintActivity.notebookMenu` |
| Inside a notebook (pages) | `notebook-{s}-pages` | `NotebookScreens.pages` |
| Library card | `notebook-{s}-card` | `LibraryCard`, `CardArt` |
| Card screens: Template (with Page Colour), page's Template, Guides, Page Transparency, Tools, Timed Pages, Lock, Cover Design, Random From | `card-{s}-template`, `-page-template`, `-guides`, `-transparency`, `-tools`, `-timed`, `-lock`, `-cover`, `-random-from` | `CardSelector`, `CardSheet` |
| Tools page: Basic tools, Recent, Saved, All (a style), Saved empty, Kits, a kit's sheet, a brush tried, Undo; portrait and landscape | `tools-{s}-{portrait,landscape}[-recent,-saved,-all-watercolor,-saved-empty,-kits,-kit-sheet,-tried,-undone]` | `ToolsActivity`, `ToolsPage`, `ToolsLayout`, `SlotRow`/`SlotTile`, `TestArea`, `LabUi` |
| Brush Lab, Basic and Advanced: main, each part open (tip, medium, paper, drying, control, smudge), Load sheet, styles flyout, Sizes and Greys, renaming | `lab-{s}-{basic,advanced}[-tip,-medium,-paper,-drying,-control,-smudge,-load,-styles,-sizes-and-greys,-rename]` | `BrushLabActivity`, `LabLayout`, `LabSettings`, `PartCard`, `SwapStrip`, `LabLibrary`, `LabButton`, `TestArea` |

**Not captured, and why**
- Rotate prompt (`draw-*-rotate-prompt` shows no button): `RotationPrompt.suggest` shows it only with the sensor's
  suggestion live; needs a real turn. Drawn by `PaintActivity` line 329: a 2 dp `Color.BLACK` outline, 8 dp **rounded**
  corners (`Ui.outline(dp(2), Color.BLACK, dp(8))`), both off-style.
- The Paper chips: retired (no Paper button on the bar, `PageTurner`'s doc, 2026-10-21); Page Transparency is on the card.
- Timed Pages' clock on the bar, Timed Sections: would mean setting the checks notebook timed; the clock is
  `InkWashBar.clock` (LABEL style, auto-sized down to 8 sp, under the 12 sp floor in `DESIGN.md`).
- Import Image's placing (`PicturePlacer`) and Export Now's work: need a picked file or write copies; the Import PDF box is shown.
- A Toast over other screens (`messageView`): not run this time (`menus` section); it is a 2 dp ink box.
- Brush Lab's dial menus (`LabSettings.Beside`): need a dial's button found and tapped; a part's swap panel and its
  Advanced dials are captured.
- Tools page landscape (`tools-*-landscape*`): drawn off the screen at the turned size, the library comes out cut
  short or the page overflows; treat those pictures as unconfirmed until seen on the device turned.

## 2. The Tools page and Brush Lab: hierarchy and links (the owner's priority)

Mocks (scratchpad `design/mocks/`): `tools-before-after.png` (and `tools-after.png`), `lab-before-after.png`
(and `lab-after.png`).

### 2.1 Tools page now (`tools-manta-portrait.png`)

Reading order, top to bottom, and what weighs most:

1. Top bar: Back, a grey "Tools", Undo (outlined), **Brush Lab, ink-filled**: the heaviest thing on the page is a
   way off it, and it is far from the brush it would change.
2. **Test Area**: the largest block (a third of the page), a 2 dp ink box (`TestArea`, `Theme.box`).
3. This Brush: tray stroke, name, words.
4. Toolbar Slots: eight tiles in an ink-framed grid (`LabUi.grid`), the chosen one a 4 dp ink outline that blinks
   (`ToolsActivity.blink`); New Brush, outlined, at the right of the header.
5. Library: tabs in an ink-framed segmented row, pager, a second row of style chips (plain text, the chosen ink-filled),
   then a grid of cards in an ink frame.

Every section has the same header (`LabUi.header`: the name, a 3 dp ink rule the whole width), so the four sections
read as equals; nothing says "start here". Counting ink: 5 framed boxes, 3 heavy rules, 4 ink fills (Brush Lab, the tab,
the chip, the white-gel-pen card), the blinking outline. The chosen slot, the one thing that should stand out, competes
with all of them.

What is separated that belongs together:
- The **slot** (what the bar will draw with), **This Brush** (what is in it) and **Brush Lab** (how to change it) are
  three places: the slot in the middle, its brush above it, the way to change it in the opposite corner.
- The **sizes and greys** of the slot are not on the page at all (they are on the drawing screen's bar and in Brush
  Lab): the owner's "more swatches below brushes" and "add colour picker". The test area draws at the slot's size and
  grey, but nothing shows which.
- The library card a slot came from is not marked; picking a card (`-tried`) outlines it in ink and puts it in the
  slot, but the link is only by name.

What looks alike that isn't:
- Tabs (Basic tools, Recent...) and style chips (All, Fineliner...) are two levels of the same filter, drawn two
  different ways; the tab row also looks like Brush Lab's Basic | Advanced switch, a mode, not a filter.
- Test Area's Clear, New Brush and Undo are outlined buttons of the same weight as the tab frame and the card grid.
- Kits (`-kits`) are cards in the same grid as brushes, with empty grey cells after them.

### 2.2 Brush Lab now (`lab-manta-basic.png`, `lab-manta-advanced.png`)

Reading order:

1. Top bar: Back, the loop and the name (heading) with a pencil, Load (outlined), **Save (ink)**, Undo, and at the far
   right **Basic | Advanced** (ink-framed, the chosen ink-filled).
2. Left column: STYLE (a 2 × 8 table in an ink frame, the chosen ink), **Randomize** (a full-width ink bar, the
   heaviest element on the screen), **THIS BRUSH** (an ink header bar, then a box with the loop and the words),
   **ADJUST** (another ink bar), six part cards (`PartCard`: 2 dp ink frames, each with its own patterned left strip:
   hatch, grey, dots, double line, solid ink, stripes).
3. Right column: Clear, the sizes and greys, the test page (ink box), **"Toolbar: tap to replace"** (ink bar) and its
   eight loops, **Library** (ink bar), library cards.

There are seven ink-filled bars or blocks before anything is chosen; the chosen style, size and slot are small
ink marks among them. Randomize, the least-used action, is the loudest. "This brush" is an ink bar whose block does
not hold the brush's name (the name is in the top bar), against `DESIGN.md` ("The name and loop appear here, once").

What is separated that belongs together:
- **Which slot is being changed** is shown halfway down the right column ("Toolbar: tap to replace"), under the test
  page, in loops; on the Tools page the same slots are tray strokes in tiles at the top. Coming from the Tools page,
  nothing on screen says "you are changing slot 2, the one you just had selected".
- Sizes and greys sit above the test page, away from This brush.
- Finding a brush (Style table, Randomize, Library, the slot strip's "tap to replace") and changing this one (Adjust)
  are mixed in both columns; `DESIGN.md` asks for them kept apart.

What looks alike that isn't:
- Five header styles on one screen: STYLE (plain label), THIS BRUSH and ADJUST (ink bars, caps), "Toolbar: tap to
  replace" and "Library" (ink bars, sentence case), and the Tools page's plain headers with ink rules. `LabUi.barText`
  vs `LabUi.header` vs `PartCard.head`.
- The six part strips are six patterns that mean "a part"; they read as six different kinds of thing.
- The Library here and the Tools page's library are the same brushes drawn two ways (loops in ink-barred cards vs tray
  strokes in a framed grid), with cards saying the name twice ("Fineliner / Fineliner / permanent").

### 2.3 Getting from picking a brush to changing it, and back

- Drawing screen → hold a tray row → **Tools page on that slot** (`InkWashBar`, `openTools`).
- Drawing screen → tap the brush line's preview or name → **Brush Lab on the slot in use** (skips the Tools page).
- Tools page → Brush Lab (top right) → Brush Lab on the selected slot; Back returns to the Tools page.
- Tools page → New Brush → Brush Lab on a fresh brush; Save puts it in the slot and returns.
- Brush Lab's own slot strip can switch slot, and its own library can replace the brush, so the Lab is also a
  second, different-looking brush picker.

Two doors into Brush Lab, two pickers, two looks for the same slots.

### 2.4 Proposed hierarchy (mocks)

One top group, the same on both screens, on a **grey-200 band**, so moving between them keeps the same thing in the
same place:

1. **On the bar** (a working name for the Toolbar Slots header, the owner's call): the eight slots as paper tiles on the band, the chosen one marked by a **5 dp ink tab** under it (as
   Brush Lab marks size and grey now), its tile running on, without a gap, into
2. **This brush**: paper on the band: the brush's stroke (Tools page) or loop (Lab), its name (heading, tap to
   rename), its words (caption, grey-800), and its **Sizes and Greys** faces (with the ink tab on the one in use) and,
   on the Tools page, the **grey bar** (`ShadeStrip`): the owner's "colour picker" and "more swatches below brushes".
   Its one ink-filled action is **Change in Brush Lab** on the Tools page, and **Save** in Brush Lab.
3. **Try it**: the test page, paper on the band, Clear as words and the drop icon (no frames).

Below the band, on plain paper, quieter:

- Tools page: **Library**: tabs as words with the chosen one ink-filled (no frame), style chips grey-200 with the
  chosen ink-filled (one chip style for both levels), cards grey-200 with their sample on paper, paper gutters, no
  ink frame; the card whose brush is in the chosen slot gets the 5 dp ink tab (the link card ↔ slot).
- Brush Lab: **Adjust** (part cards: paper on grey-200, no frames, no strip patterns, the part's icon at the right, the
  chosen choice bold) and, under it, **Start from** (style chips, Randomize as a word with the dice): finding a starting
  point, quiet. Brush Lab's own library goes; "Other brushes" sends back to the Tools page (Back names it: "‹ Tools").

Links, said once each: chosen slot = ink tab, its tile joins This brush; This brush → Brush Lab (button in the block);
Brush Lab → Tools (Back names it); library card ↔ slot (the same ink tab). Basic | Advanced stays a mode switch beside
the title, grey-200 behind it, so it no longer looks like the library tabs.

Open questions for the owner: does Brush Lab keep a library at all (proposed: no, the Tools page finds, the Lab
changes); is the grey bar wanted on the Tools page or only Sizes and Greys; Test area height (the mock gives it about
a quarter of the page, from a third).

## 3. Findings, ranked by how much they break unity

1. **Ink frames everywhere, from two shared helpers.** `Theme.box(context, stroke)` defaults to an ink line, and
   `LabUi.grid` / `LabUi.header` / `LabButton` (outlined) / `PartCard` draw 2–3 dp ink frames and rules, so every box,
   card grid, tab row, test area and section header on the Tools page, Brush Lab, card screens and Sizes and Greys is
   ink-framed. Fixing these four helpers moves most screens to shade before line at once.
   (Shots: `tools-*`, `lab-*`, `card-*`, `sizes-and-greys-*`.)
2. **Menus are MonoPaint's, not Inkwash's.** `PaintActivity.menu` (file menu, Finger, Fun, Mirrors, notebook hold)
   frames every row in a 1 dp `Color.BLACK` outline, 16 sp text (`setTextSize(16)`, no `TypeStyle`), `Color.BLACK`
   text, 52 dp rows. The page panel (`showPagePanel`) the same. Other popups opened by holds are Inkwash panels
   (`InkWashBar.openPanel`: smudge chooser, stamp, layer) with different frames, button styles and type sizes
   (`menu-*-smudge`, `-stamp`, `-layer`): three looks for "a list beside a button". The eraser's light row
   (`lightRow`, `menu-*-eraser`) is a fourth, with grey hairline dividers and tiny caption text.
3. **Five header styles.** `LabUi.header` (name + 3 dp ink rule: Tools page), `LabUi.barText` (white on an ink bar:
   Brush Lab's THIS BRUSH, ADJUST, Library, Toolbar, Tweak it), plain LABEL (Brush Lab's STYLE, the card's TEMPLATE),
   card screens' title with a **double** ink rule (`CardSelector`: `card-*`), and the shelf/notebook bar's heading
   (`NotebookScreens`). Case varies too ("Toolbar: tap to replace", "Library", "THIS BRUSH", "Test Area").
4. **Chosen state has four forms.** Ink fill (tabs, chips, card-screen rows with a ✓ box, Template tiles with ✓ corner),
   4 dp ink outline that blinks (Tools page slot), 5 dp ink tab (Brush Lab sizes, greys, slots; page strip), the bar's
   right-edge bar (drawing screen, menus). The rules in `DESIGN.md` name all four; per component it's fine, but a
   template tile is ink-filled **and** ticked, a card-screen row filled **and** ticked: two marks for one choice.
5. **Brush Lab's ink mass.** Seven ink bars/blocks (Save, Basic, chosen style, Randomize, THIS BRUSH, ADJUST, Toolbar,
   Library) before anything is chosen; Randomize is the heaviest thing on screen (`LabSettings`, `LabLibrary`,
   `LabUi.barText`). See 2.2.
6. **Legacy MonoPaint Settings dialog** (`dialog-*-settings`, `AppSettingsDialog`, `SettingsForm`): unreachable from
   Inkwash's UI (no caller of `appSettings()`), but it still lists retired tools (Airbrush, Fill, Shapes, Blending
   stump, Layers) in non-Phosphor icons, all-caps checkboxes and round radio buttons, an off-palette `0xffeeeeee` fill.
   Proposal: delete with its callers, or leave unreachable and say so.
7. **Off-palette greys and raw colours in UI code.** `ToolSettingsPanel:59` divider `0xffcccccc`; `SettingsForm:172`
   `0xffeeeeee`; `Toolbar:251` `0xffaaaaaa` and 4 dp rounded; `PaintActivity:329` rotate button 8 dp rounded,
   `Color.BLACK`. 113 `Color.BLACK/WHITE/GRAY` or literal colours across UI files (most in MonoPaint-era classes:
   `DrawingPad` 11, `PaintActivity` 10, `SettingsForm` 9, `ToolButton` 9, `PaletteEditor` 8, `PageOverview` 7,
   `Toolbar` 6); 19 bare `setTextSize(n)` outside `TypeStyle` (`LayersPanel` 4, `PaintActivity` 4,
   `FullscreenController` 3, `PaletteEditor` 3). Values are mostly the right ink and paper, but they bypass the tokens,
   so a token change misses them.
8. **Clipped or crowded text.** Brush Lab on the Nomad: the slot strip's size captions overlap and lose their first
   digit (".1mm", ".5mm", ".0mm": `LabLibrary` toolbar row, `lab-nomad-basic`); the Nomad's "Style: Brush pe…";
   imported template names cut ("2020103119122…", `card-*-template`, `-random-from`); a long menu row wraps ("Use This
   Page on the Cover", `menu-*-file`); the Timed Sections clock auto-sizes to 8 sp (under the 12 sp floor).
9. **Words against `TEXT-STYLE.md`.** Brush Lab's style table shows the key, "Watercolor" (`LabSettings:250` uses
   `ModularStyle.name`, not `.label`; the Nomad's flyout uses `.label`, "Watercolour"); the library card numbers its
   eight tools 1–8 (`LibraryCard:302`) where "nothing is numbered" for slots; "Toolbar: tap to replace" has a colon
   and an instruction; "This Brush" (Title Case) on the Tools page vs "THIS BRUSH" (the owner's caps) in the Lab vs
   This brush in `DESIGN.md`; the Lab's slot strip is "Toolbar", the Tools page's "Toolbar Slots".
10. **Dialogs**: `Theme.dialog` gives a 2 dp ink outline; the action buttons are MonoPaint's text buttons (CANCEL,
    DELETE, both the same weight; the destructive one not distinguished); New Notebook/Rename rows are 2 dp
    ink-framed (`NamePrompt`, `Theme.box`). The Pages dialog's ink frame is broken at the sides where its grid scrolls
    over it (`PageOverview`, `dialog-*-pages`).
11. **Two sample shapes on one task.** The Tools page shows slots and library as tray strokes, Brush Lab shows the same
    slots and library as loops. `DESIGN.md` sanctions both, but on the two screens the owner wants linked the same
    slot looks different; proposal: the slot strip uses the tray stroke on both, Brush Lab keeps the loop for This
    brush only.
12. **Small and one-off.** Page options (`menu-*-page-options`) is a box with only "PAGE 1" in it when tracing is off
    (an options popup with no options); Random From shows no mark for which templates are in (`card-*-random-from`)
    and offers "Random" inside Random; the Kits tab fills its grid with empty grey cells (`tools-*-kits`); Undo/Redo,
    Menu, Finger and Fun have no button grey while tools do (by the owner's choice, 2026-10-21: keep).

What a MonoPaint user meets well: the side column, the grey strip ("a gradient displayed like in monopaint"), the
eraser and smudge icons, long press to discover. What they would not recognise: the library card's skeuomorphic
stamps and pocket (the notebook's settings), the Tools page / Brush Lab split (MonoPaint had one tool settings popover),
the loop vs tray stroke.

## 4. Proposed component rules (Scribbler Style)

Tokens: `Theme.PAPER` #fff, `GREY_200` #ddd, `GREY_400` #bbb, `GREY_600` #888, `GREY_800` #555, `INK` #000,
`ON_INK`; strokes `FINE` 1, `BASE` 2, `RULE` 3, `HEAVY` 4 dp; type `TypeStyle.CAPTION` 13/400, `BODY` 15/400,
`LABEL` 14/700 caps .06 em, `HEADING` 20/700, `TITLE` 32/700. Square corners everywhere.

| Component | Rule | Tokens |
|---|---|---|
| **Surface / panel** | A group is a grey step, not a frame: grey-200 band on paper, paper blocks on the band. Never more than two steps deep. | `GREY_200`, `PAPER`; no stroke |
| **Section header** | One style: LABEL in grey-800, 12 dp above its content, no rule. A rule only where two sections touch with no grey step between: `BASE` grey-400. Replaces `LabUi.header` rule, `LabUi.barText` ink bars, the card screens' double rule. | `LABEL`, `GREY_800`, rule `BASE` `GREY_400` only if needed |
| **Screen title** | HEADING beside Back on bars (Tools, Brush Lab, a notebook); TITLE + a CAPTION line on card screens, no rule under. | `HEADING` / `TITLE`, `CAPTION` `GREY_800` |
| **Back** | "‹ Where" in LABEL, the place it goes named ("‹ Tools", "‹ Notebooks"), no frame. | `LABEL`, `INK` |
| **Primary button** | One per screen or block: ink-filled, on-ink LABEL text, 44 dp tall, icon optional at 18 dp. (Save, Change in Brush Lab, a dialog's act.) | fill `INK`, text `ON_INK`, `LABEL` |
| **Secondary button** | LABEL words in ink, no frame, no fill (Load, Clear, Cancel, New Brush, Randomize); on grey-200 the same. Disabled grey-400 words. | `LABEL`, `INK` / `GREY_400` |
| **Icon button** | Phosphor Bold 24 dp, ink, no background (as Menu, Undo, Finger, Fun now). Disabled grey-400. | `INK`, `GREY_400` |
| **Tabs (a filter's top level)** | Words in LABEL, the chosen ink-filled; no frame round the row. | `LABEL`, fill `INK` |
| **Mode switch (Basic \| Advanced)** | Segments on grey-200, the chosen ink-filled; sits beside the title, never with tabs. | `GREY_200`, `INK` |
| **Chips (a filter's second level, choices)** | CAPTION words on grey-200, the chosen ink-filled with on-ink text; 6 dp apart; wrap, never scroll. | `CAPTION`, `GREY_200`, `INK` |
| **Option row (card screens' lists)** | A grey-200 row with icon, title (BODY 700) and caption; the chosen one ink-filled, no ✓ (one mark). | `GREY_200`, `INK`, `BODY`, `CAPTION` |
| **Tile / card (brush, template, cover, kit)** | Sample on paper, name and words below on grey-200, 6 dp paper gutters, no frame; the one in use: 5 dp ink tab under it (no fill, no ✓). | `GREY_200`, `PAPER`, tab `INK` 5 dp |
| **Selected state** | Exactly one form per component: tiles and swatches the 5 dp ink tab; tabs, chips, option rows ink fill; drawing-screen bar and menu rows the right-edge bar. No ink outline (retire the Tools page's blinking 4 dp outline for the tab). | `INK` |
| **Menu (a list beside a button)** | One component for `PaintActivity.menu`, `InkWashBar.openPanel` and the eraser's row: a grey-200 panel, rows 48 dp, Phosphor icon + BODY words, no row frames, groups split by a `BASE` grey-400 rule, a marked row's right-edge bar. | `GREY_200`, `BODY`, `INK`, `GREY_400` |
| **Popup panel (Sizes and Greys, a part's swap, page options)** | A grey-200 panel with a LABEL header and Done as a secondary button; inner previews paper on it; no ink frame. | `GREY_200`, `PAPER`, `LABEL` |
| **Dialog** | A grey-200 box, no outline (`Theme.dialog`); title BODY 700, message BODY 400; Cancel secondary, the act primary (ink-filled); square. | `GREY_200`, `BODY`, `INK` |
| **Sheet (Load code, a kit, the Lab's Load)** | No scrim (e-ink): a grey-200 sheet the screen's width, under the bar, its fields paper. | `GREY_200`, `PAPER` |
| **Text field** | Paper on grey-200, no outline, the cursor ink; selected text grey-400. | `PAPER`, `GREY_400` |
| **Divider** | Only where no grey step can do it: `BASE` grey-400. Never ink. | `GREY_400`, `BASE` |
| **Note / toast** | On the drawing screen the brush line's note (as now); elsewhere a grey-200 box, BODY, no outline. | `GREY_200`, `BODY` |
| **Test page** | Paper on a grey-200 band, no frame; its Clear and drop as a secondary button and icon. | `PAPER`, `GREY_200` |
| **Exceptions kept** | The grey bar's 1 dp ink frame and marker (`ShadeStrip`, MonoPaint's); page strip previews' frames (`PageTurner`: thumbnails need an edge on paper); the library card's stamps (the notebook's own art); magnets. | as `DESIGN.md` |

Where to change it, in order: `Theme.box` (default line grey-400 or none), `LabUi.header`/`barText`/`grid`,
`LabButton` (outlined → words), `PaintActivity.menu` + `InkWashBar.openPanel` (one menu component), `Theme.dialog`,
`CardSelector` (title rule, option rows, tiles), `PartCard` (frames, strips), `SizeModal`, `NamePrompt`. Then the
two screens' layouts (`ToolsActivity`/`ToolsLayout`, `BrushLabActivity`/`LabLayout`/`LabLibrary`) per section 2.4.

E-ink notes for the pass: grey-200 fills are dithered dots on the panel's 16-step grid (no ghosting from flat
fills, but a full-band fill costs a full refresh when it first appears: show bands with the screen, never toggle them);
no animation (the Tools page's blinking outline goes with the outline); a chosen-state change is one tab's rectangle,
cheap for `SelectionFeedback`.

## 5. Mocks

In the session scratchpad, `design/mocks/` (rendered with Python/PIL from the screenshots, iA Writer Duo from
`res/font`, samples cut from the real shots):

- `tools-before-after.png`, `tools-after.png`: the Tools page per 2.4 (the one band: On the bar → This brush with
  sizes, greys and the grey bar → Try it; Library below on paper).
- `lab-before-after.png`, `lab-after.png`: Brush Lab Basic per 2.4 (the same band; Adjust and Try it side by side;
  Start from quiet under Adjust; no library).
- `menu-before-after.png`: the file menu as the one menu component (grey-200 panel, no row frames, groups split by a
  grey-400 rule, the marked row's bar). "Use Page on Cover" there only shows the row fitting one line; the wording is
  the owner's call.
- `dialog-before-after.png`: Delete page as the dialog component (grey-200 box, act ink-filled).
