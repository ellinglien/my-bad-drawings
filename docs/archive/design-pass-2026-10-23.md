# Design pass, 2026-10-23: the owner's decisions

The owner approved the proposal (https://claude.ai/artifact/1J5ggh6Xs6SSw8qyahdyCv) with the changes below. The
findings, screen inventory and component rules are in `docs/archive/design-audit-2026-10-23.md` (section "Component rules");
the style is `docs/DESIGN.md` (Scribbler Style, "Shade before line"). (The mocks were removed before the public release.)

## Everywhere

- Component rules as in the audit's table (the Tools page and Brush Lab later went to 1 px ink lines instead of grey
  fills, owner, 2026-10-23: "eink grey doesn't quite work the same way"; see DESIGN.md): groups are grey-200 bands with paper blocks, no frames; one section
  header style (caps LABEL in grey-800, no rule); one ink-filled primary button per screen; other buttons plain ink
  words; tabs and chips ink-filled when chosen; tiles grey-200 with the drawing on paper and a 5 dp ink tab under the
  one in use; one "chosen" mark per component (no fill + ✓, no blinking outline); dividers grey-400 only where a
  grey step can't do it, never ink. Kept as is: the grey bar (ShadeStrip), page-strip thumbnails, magnets.
- **Menus and popups that open over the page (owner: "i am worried they will leave e-ink ghosts when they open and
  close. this happens with the eraser menu"):** option A. A white (paper) panel with a **1 px ink edge**, no grey
  fills, rows of text only, **12 px text** (owner changed 11 → 12, 2026-10-23; one size for every menu), the chosen
  row bold with the right-edge ink bar. No grey anywhere in a menu. When a menu or popup closes, refresh just its
  rectangle with a full (clean) e-ink update so no ghost stays; this also fixes the eraser menu's ghost.
- Dialogs: as the audit (grey-200 box, no outline, Cancel plain words, the action ink-filled). Whole screens may use
  grey-200 bands: they get a full refresh when they open.

## Tools page (brush selection)

Order top to bottom: **Active in toolbar** (the 8 slots; the chosen one has the ink tab and joins into the next
row) → **This brush** (mark, name, words, the four sizes, the four greys and the grey bar, **no Sizes / Greys / Grey
bar headings**; the one primary button **Tweak in Brush Lab**) → **Scratch pad** (about a quarter of the page, was
"Test Area") → **Library** (tabs Basic tools · Recent · Saved · All · Kits, the type chips, the grid). **New Brush**
moves down to the Library row. The page count (‹ 1/7 ›) moves **under** the grid so it's clear there's more.
- **The slots and the Library must not blend** (owner, 2026-10-23: "need a bit more space between the toolbar slots
  and the library... it blends together"): the top group (Active in toolbar + This brush) is one grey-200 band, the
  Library a separate band, with a `space-6` (48 px, scaled for the Manta) paper gap between them, and the Library's
  tiles drawn smaller than the slots so the two never read as one grid. Same in Brush Lab between Active in toolbar
  and the rest.

## Brush Lab (brush designer)

Same top as the Tools page so both read as one place: **Active in toolbar** (slots show the **same brush mark as
the Tools page**, not the swirl) → **About this brush** (mark, name with rename, words, sizes and greys, **no
Sizes / Greys headings**). Below: **Adjust** (the six part cards, dial end words kept: fine/broad, dry/wet…) beside
the **Scratch pad**. **Randomize** at the bottom, a big full-width button. Brush Lab has **no library of its own**.
Back reads "‹ Tools". Save is the one primary button and reads **Save to library**. Basic | Advanced is a mode
switch beside the title (grey-200 segments, chosen ink-filled).
- A part's choices (Tip, Medium…): grey tiles, drawing on paper, ink tab under the one in use, no black title bar,
  no frame, Done as plain words. It opens over the page, so the menu rule applies to its edge (1 px ink, paper).
- **Save behaviour:** changes already go to the slot on Back. Fix the trap: a brush made with New Brush and never
  saved is no longer thrown away on Back; it stays in its slot like any other change.

## Getting back to where you started (Tools page and Brush Lab)

Owner (2026-10-23): "if the toolbar slot changes, there should be a way to go back to the original tool that was open
when it was opened, an easy way. this would help people from getting lost in it".
- Each screen remembers what every slot held when the screen opened (the Tools page from the drawing screen, Brush
  Lab from wherever it was opened; a Lab opened from the Tools page keeps the Tools page's starting brushes, so going
  Tools → Lab → back still offers the brush from before either).
- When the chosen slot holds something else than it did then, a plain-words button shows in This brush / About this
  brush, next to the primary button: **"↺ Back to Fineliner"** (the original's name). One tap puts that brush back in
  the slot as it was, sizes and greys included. It hides again once the slot matches its start.
- The slot itself shows that it changed: a small grey-800 dot under its name, so you can see at a glance which slots
  you've changed on this visit. No dot once it's back.
- It's one more step on Undo, so Undo can take it back too. Leaving the screen keeps the changes, as now.

## Nothing unsaved is lost when a slot is replaced

Owner (2026-10-23): "when a brush that is not saved that was in the toolbar is replaced in the toolbar, prompt user
to save the previous one? so it isn't lost forever maybe". Built without a prompt (a dialog on every swap is slow on
e-ink and interrupts; the owner can ask for the prompt instead):
- When a slot's brush that isn't in the library is replaced (Tools page pick, a kit, Brush Lab Randomize or Start
  from, Load), it is kept automatically in **Recent**, the library tab that already lists recently used brushes.
- A brief note says so where notes show on that screen: "Kept Fineliner copy in Recent".
- Recent keeps the latest 30 such brushes and drops the oldest past that. A brush you Save moves to Saved as now.
  A brush identical to one already in the library isn't kept twice.

## Drawing screen

Owner (2026-10-23): "redesign the canvas page itself to match ... moving the pages bar to the top. keep the zen in the
bottom left (align a bit better pls) and you can put the selected brush indicator to the right along the bottom".
- **Page bar at the top** (`PageTurner`, `DrawLayout.pageBar`): the page's width, from just right of the side column,
  same contents and order as now (notebook, page previews, ‹ page / count ›, lock, zoom). The page starts under it.
- **Zen** stays in the bottom-left corner, centred under the side column on the column's icon axis, with the same
  bottom margin as the brush line.
- **The brush line** (mark, name · caption, system notes) runs along the bottom edge from just right of Zen,
  left-aligned, one line; its tap still opens Brush Lab. Not at the bottom right (owner, 2026-10-23: "i am worried it
  will be triggered by a palm accidentally.. best to shift it to the left"): the right-handed palm rests there.
- **The active toolbar slot is underlined** (owner, 2026-10-23: "the selected toolbar slot is not clear... too
  subtle"; first built as a black fill, its mark drawn white). Later the same day (owner: "some brushes are inverted,
  so in addition to the inverted look of the brush we need to determine another way to indicate that it is active...
  underline?"; picked a thick underline): a 5 dp ink bar under the slot's full width, its mark drawn as it is, with a
  few dp clear of the next row so it never reads as that row's. Pure black and white, so it never ghosts. The same
  mark is used wherever the 8 slots show (Tools page and Brush Lab "Active in toolbar"), and for the side column's
  tool buttons in use and the layer drawn on (a layer's picture can be dark too).
- Scribbler Style throughout (no ink frames, one chosen mark). Check Nomad, landscape, Zen on, and that nothing
  covers the page's drawing area or the side column.

## Notebook settings (replaces the library card)

Replace the library card with a plain list (owner: "replace it with the list"):
- Top: "‹ Notebooks", Lock on the right; the notebook's name in TITLE with Rename under it.
- One grey-200 row per setting, key in caps LABEL grey-800 on the left, current value on the right, "›" when it opens
  a screen: **Template** (thumbnail + name ›), **Page colour** (six swatches in the row, the six greys; was a 12-step
  slider, owner: "six swatches is enough"), **See-through pages** (Off/On switch in the row), **Tools** (toolbar
  name + the 8 small marks ›), **No erasing** (Off/On in the row), **Timed pages** (value ›), **Cover** (name ›),
  plus rows for any other card screen (Random from…); Template visibility (Full, Half, Faint, Hidden) is a row of chips on the Template screen, not a row of its own (owner, 2026-10-23) in the same form.
- Foot: one grey-800 caption line "Made Oct 23, 2026 · 2 pages" (the stamps go).
- The card screens it opens (Template etc.) follow the same rules: grey tiles, ink tab, no ✓, names wrap to two
  lines instead of being cut.
- Old notebooks with a page colour from the 12-step slider keep their exact grey; the six swatches only set new
  values (a stored grey not among the six shows no swatch chosen).

## After the design pass: template visibility on the toolbar

Built 2026-10-23 (branch `column-extras`) as planned here, the button in the fourth row's right cell, the Lasso's cell
beside it kept empty: see **Template Visibility** in `docs/DESIGN.md`. The Nomad in landscape holds the row with its
icon rows at 28 dp.

Owner (2026-10-23): "if there is a template, a way to adjust its visibility quickly on the toolbar". Today it's the
file menu's Template row (Full, Half, Faint, Hidden). Plan: a Template visibility button in the side column, shown only when the page has guides. The cell beside the smudge
tool (where Fill was) now holds the eraser, and Magnetic Poetry and Mirrors took the Fun button's place (owner,
2026-10-23), so every cell of the tool grid is used: the button needs a fourth row of the grid (one icon row, about
56 dp on the Manta, which the column has. On the Nomad in landscape the layers already fold, so check it fits). A tap steps Full → Half → Faint → Hidden, the
icon showing the level (Phosphor, e.g. `eye` / `eye-slash` with the level as a small caption); a long press opens
the four as a menu row (the menu rule applies). Same state as the file menu's row, which stays.
