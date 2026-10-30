# Notebook options: template, rules, tools, lock, cover

Approved by the owner, 2026-10-19 ("looks great ! proceed!"). From the owner's notes (`docs/archive/notes/2026-10-19-owner-ideas.md`):
- page templates;
- notebook-wide rules ("no erase, only one brush, timed pages");
- marbled composition covers as the default;
- "a random selection of tools possible at the start ... custom for each notebook .. lockable and unlockable, but down the road maybe locked forever ? like the other constraints or timer".

The goal for the tools is "useful but delightfully unpredictable". A plain notebook stays exactly as simple as today: every option defaults to off, and the name prompt alone still makes a notebook in one tap.

## Where the options live

- **New notebook:** the name prompt (today's: the name selected, three suggestions) gains one line under the
  suggestions, **More options**. Tapping it opens the options on the same sheet. Without it, the notebook is today's.
  The owner: "new notebook creation will have a set of extended options".
- **Later:** the notebook's menu (the shelf tile's long press and the file menu) gains **Notebook options**: the same
  sheet, with locked items shown as locked.

## The options

| Option | Choices | Default |
|---|---|---|
| Template | Blank · Diary (a big panel above, lines below) · Comic panels | Blank |
| Guides in PDF | Print · Leave out | Print (the owner: "included in the pdf by default") |
| Tools | My toolbar · Surprise me · Pick from library | My toolbar |
| No erasing | off · on | off |
| One brush | off · a brush chosen here | off |
| Timed pages | off · 1 · 3 · 5 · 10 min | off |
| Lock | Open · Locked · Sealed | Open |

### Template
- The guides are printed faintly on each new page, under the drawing. The eraser never takes them, and they aren't marks: a page with only guides counts as blank.
- They're kept as the notebook's template name, not drawn into each page's file, so they cost nothing per page.
- Thumbnails show them. The PDF prints them unless set to Leave out.

### Tools
- **Each notebook keeps its own toolbar:** the six slots, their sizes and greys. Opening a notebook loads its toolbar; leaving puts yours back.
  - **My toolbar** copies today's six.
  - **Pick from library** opens the toolbar picker.
- **Surprise me** rolls six, a balanced mix. The owner: "a variety of tools... fast.. fancy.. simple .. complex.. and a range of types". A mix of the library's own brushes and fresh rolls:
  - one plain pen to write with: a simple, fast ink line;
  - a range of types across the rest: a line tool, a wash or soft brush, a dry or textured tool, and two wildcards (Anything goes);
  - a spread of speed: at least two fast (WYSIWYG) and at most two heavy;
  - a spread of complexity: at least two simple, at least one fancy (a mischief module: Mold, Print shading, gravity…);
  - about half from the owner's library and half fresh, never two the same.
- **Re-rolling:** while setting up, the six previews show with a dice button to roll again (the owner: "yes"). Each preview can be tapped to re-roll just that one.

### Rules
- **No erasing:** the eraser, Clear page and the finger's eraser are off in this notebook. Undo still works, since Undo isn't erasing.
- **One brush:** the toolbar holds only that brush, in its four sizes and greys. Chosen when the notebook is made. The owner: "when creating the notebook".
- **Timed pages:** a page gets the chosen minutes from its first stroke.
  - In the last 30 s (the last 10 s for a 1-minute page), the side bar's page number blinks.
  - At zero the page locks: no more marks, and no Undo. The owner: "no, locked means locked"; "just lock".
  - The next page (the blank one at the end) is ready, and the timer starts again on its first stroke.
  - A locked page shows a small closed-lock mark by its number.

### Lock (tools and rules alike)
- **Open:** change anything in Notebook options any time.
- **Locked:** the tools and rules can't be changed while drawing. Notebook options shows them with an Unlock button.
- **Sealed:** locked for good, with no Unlock. It asks once, when chosen: "Sealed for good. The tools and rules can't be changed later." A sealed notebook's cover shows a small seal.

Template and Guides in PDF are never locked.

### Covers (front and back)
- Every notebook gets a black-and-white marbled cover, generated from its name (the Suminagashi work), with a plain white label patch holding the name, like a composition book. Same name, same marble; rename regenerates it. The shelf shows the front cover.
- **A back cover** with the same marble (no label). The owner: "include a back cover with the same design".
- **Drawable:** both covers are pages you can open and draw on "like any other page" (the owner). The marble is their paper: it shows under the drawing, the eraser rubs back to it, Clear page leaves it. ‹ from the first page opens the front cover; the back cover comes after the last page (after the blank page at the end).
- **A page as the cover picture:** "Use this page on the cover" pastes a smaller copy of a page onto the front cover, like a picture glued onto a composition book, kept up to date as that page changes. Drawing on the cover goes over it. "No cover picture" takes it off.
- A cover with nothing drawn and no picture costs nothing in the file: it's made from the name when shown.
- The PDF starts with the front cover and ends with the back cover.

## Storage
- The options are a small notebook-level block in the notebook file, next to the existing notebook block; an old notebook has none, which means all defaults.
- The notebook's toolbar is stored as brush codes. A timed page's lock is stored with the page.
- Exports are unchanged apart from the guides.

### Covers as built (stage 1, branch `inkwash-covers`)
- **The marble** (`CoverMarble`): Jaffer and Lu's suminagashi, inverted per point, seeded from the file's name (FNV-1a, then SplitMix64): ink and clear drops in turn at about 20 sites over the cover (rings in rings, crowding into stones), two comb passes of nine tines, two swirls. Pure black and white, edges smoothed through the panel's greys. Sampled at most 640 across (where an edge may be only) and blended up, so a page's marble is 0.15 s on the Mac (about 3 s on the Manta, made ahead in the background from the first and last page), a shelf tile's about 30 ms; both kept in memory and in the app's cache folder (`marbles/`), so a later launch reads them (the shelf's 20 tiles in 25 ms).
- **The front's paper** (`NotebookCovers`): the marble; a white label 62% of the width, a fine ink rule, the name as shown in iA Writer Duo 700 at the largest size that fits three lines; the cover picture 58% of the width under it, centred, a thin ink rule round it, no tilt (it would alias on e-ink). The back's: the marble alone.
- **No page shade on a cover**: Fill page isn't offered there; the marble is its paper.
- **Navigation**: ‹ from page 1 opens the front cover, › from the last page (the blank one) the back cover; the bar's › never shows + now (the blank page at the end stands for it; the notebook's strip keeps +). The strip: Cover, the pages, +, Back.
- **The cover picture**: the menu's **Use This Page on the Cover** (on a page) and **No Cover Picture** (on the front cover, or on the page that is the picture); inside the notebook the foot's **Use on cover** / **No picture** (in place of Set as cover; hidden on the back, and Delete page hidden on both covers). A page set as the cover before covers (any but page 1, which was everyone's) is the picture.
- **Storage**: the options block "INO1" (`BookOptions`), a list of tagged records, between the last page and the notebook block: `FRNT`/`BACK` (a drawn cover's page bytes and CRC), `FPIC`/`BPIC` (its picture of its marks alone, to lay on its paper), `CPIC` (the picture's page). Written only when there is something in it; an undrawn cover with no picture costs nothing, and the file is then byte for byte as before. The 2a352ed reader (the Manta's) opens such a file with its pages and saves it again without the covers.
- **Copies**: `00-cover.png` and `zz-back.png` beside `001.png`…, the PDF's first and last pages.

### Cover designs as built (branch `inkwash-covers2`)
The owner, on the marble above: "the swirled look is cool but doesnt quite read as a composition book … also there is a
black band on the left side of the cover (and right on the back) for the tape... and the label is slightly rounded";
then, of the prototype (`docs/archive/brush-lab/prototypes/cover-designs.html`): "covers: yes pick its own design with black
marble by default".
- **Nine designs** (`CoverDesign`, ported from the prototype to the bit: `CoverNoise`, `CoverPattern`): Classic Black
  Marble (every notebook's, old ones and Loose pages too, unless another is picked), Fine Speckle, Bold Marble, Reverse
  Marble, Swirl (the marble above, cleaned up: five columns of sites, smaller drops), Crackle, Terrazzo, Woodgrain,
  Engraved. Each made from the notebook's file name with the design's own seed. A notebook's covers moved from the
  first marble to Classic unless it has a design kept: as agreed.
- **The tape**: ink, 9.5% of the width, the left of the front and the right of the back, a faint twill at full size.
- **The label**: 52% × 33% of the width, its top at 14% of the height, over the printed part; rounded corners, a double
  rule; COMPOSITION BOOK; NAME (the name after its date, in iA Writer Duo 700, as large as fits, then cut with an
  ellipsis), SUBJECT (blank, to write on) and DATE (the stored name's date, "19 Oct 2026"). Under 600 px wide (a shelf
  tile) the small words and the date are left out. The cover picture goes under it, centred over the printed part.
- **The back**: the pattern further along the sheet (Swirl: a bath of its own), the tape on the right, no label.
- **Storage**: the options block's `DSGN` record (the design's key), only when it isn't Classic. Versions before it skip
  it (6e3bae8 opens such a notebook with its covers, and saves it again without the design).
- **Choosing**: **Cover Design** in the shelf tile's long press (Rename, Cover Design, Delete) and in the menu inside a
  notebook opens a dialog of the nine as small front covers with that notebook's name, each named under it, the one in
  use marked by the bar's black bar at its right; one tap picks it. Its grid (`NotebookLayout.chooser`) fits both tablets
  in both turns: the Manta 3 or 5 columns of 200 dp covers, the Nomad 3 of 134 dp (portrait) or 5 of 138 dp. The new
  notebook's name prompt has no cover line yet: with the keyboard up, the Nomad in landscape has no room for another
  row; it waits for the options sheet ("More options").
- **Speed**: each design's pattern made once, ahead, as a large sheet bundled with the app (`CoverTexture`,
  scripts/cover_textures.sh), a notebook's cover a window of it chosen by its name, turned or flipped where the design
  allows: a shelf tile under a millisecond on the desktop, a Manta cover a few (the generators took 60 ms and 120 ms).
  Made in the background and kept in memory, the shelf's tiles at their size, the page's size only for a cover turned
  to or the copies; a shelf tile shows a plain placeholder (paper and its tape) until it is made; back on the shelf,
  the tiles shown before show at once.

### The options sheet, templates and tools as built (stages 2 and 3, branch `inkwash-nbopts`)
- **The sheet** (`NotebookOptionsSheet`, sizes in `NotebookLayout.sheet*`, checked for both tablets in both turns): a dialog,
  **Notebook Options**, of rows each a label over chips, the one chosen ink-filled: **Template** (Blank, Diary, Comic
  Panels), **Guides in PDF** (Print, Leave Out), **Tools** (My Toolbar, Surprise Me, Pick from Library). 580 dp wide at
  most (520 before the rules), 664 dp tall with Surprise Me's previews, nothing scrolls. Rules and Lock add four rows, label beside chips. Nothing
  changes until **Done**. Reached from the shelf tile's long press (Rename, Cover Design, Notebook Options, Delete), the file
  menu, and **Options**, the new notebook's name prompt's neutral button (its choices are said in the generated name: `NotebookNames.named`). That button is left out on the Nomad turned to
  landscape (`NotebookLayout.roomForMore`): its prompt, with the keyboard up, has no room for a row more (as the cover design
  found); the menus have the options there. Not measured on the device.
- **Templates** (`NotebookTemplate`): Blank (none), Diary (a hairline panel across the top 46%, then rules every 4.5% of the
  width), Comic Panels (two columns of three hairline panels). Tones are the panel's own greys (rules 192, panels 160); a line is 2 px at
  1920 wide, 1 px on narrower pages and thumbnails. The guides are the page's **paper**, not marks: `ToneDocument.placeGuides`,
  laid under every layer like a cover's paper (`guided`: the darker of the guide and the page's shade), so the eraser rubs
  back to them, Clear Page leaves them, `unmarked` and `BlankPages` ignore them, and a page's file and picture hold none.
  Placed on every page decoded (never a cover) and again when the template changes. Thumbnails draw the guides' mask under the
  page's picture; the copies (`NotebookExport.update(…, options)`, `DrawingBook.Snapshot.printed`) print them unless Leave Out,
  a page's PNG carrying its CRC mixed with the template's (`pageKey`) so only a change of template or of Guides in PDF draws
  the pages again; Blank keeps the page's own CRC.
- **Surprise Me** (`SurpriseTools`): each slot has a role, so the mix is balanced by construction and a re-roll of one slot
  keeps it so. PEN (first, plain: a simple ink line that dries at once, no wet, splatter or bleed), LINE (fast and simple),
  WASH, DRY, two WILD (Anything goes), FANCY (a mischief module: Screen, Tone, Flick, Fling, Mold, Float or a page-tilt
  cell; a roll that misses it in 400 tries gets a Flick) and one of any kind. So at least two fast and two simple, one
  fancy, every type, and none the same (`sameAs`). Four slots are drawn for the library; a role the library has no brush
  for is rolled fresh. Each comes with the size of its style's medium and Dark, a wash Light, opaque paint Lighter. Over 900 rolls
  and their re-rolls (`NotebookOptionsChecks`) none breaks a rule; from a rich library 4.00 of 8 come from it. "Fast" is Drying
  Instant or Quick, "simple" a plain tip and medium, no smudge, no slow drying and no mischief.
- **A notebook's own toolbar** (`ToolSlots.Toolbar`): the eight brushes as stored brush codes, each with its px and darkness,
  four tab-separated fields a line. Kept in the options block's `TOOL` record (its choice's key, Surprise Me or Pick from
  Library, then the eight lines), so Tools reopens as it was. Opening a notebook (`PaintActivity.syncToolbar`: when the
  notebook is open and not on the shelf) loads it over the slots, the app's own toolbar kept under `tool_appwide` until the
  notebook is left or replaced, a launch cut short putting it right at the next sync; the Tools page and Brush Lab, which
  read the same store, see the notebook's slots. The notebook's changes are read back from the slots when it is saved,
  left or replaced (`captureToolbar`). The swatches (the four sizes and greys), the library and the recent brushes stay
  the app's. Pick from Library chosen for a notebook not open saves its file with the app's toolbar as the start, opens it,
  then the Tools page.
- **Storage**: the options block's `TMPL` (the template's key), `GPDF` (one byte, 0 for Leave Out) and `TOOL`, each only
  when not the default; the recovery's TSRO section; the shelf reads them with the cover. A notebook with defaults is
  byte for byte as before; 233ba6b (before options) opens one that has them without them and saves it again without
  (`OlderOptionsCheck`).

### Rules and Lock as built (stages 4 and 5, branch `inkwash-nbrules`)
- **The sheet** grows four rows, **No Erasing** (Off, On), **One Brush** (Off, or the brush's name), **Timed Pages** (Off, 1 Min, 3 Min,
  5 Min, 10 Min) and **Lock** (Open, Locked, Sealed), each with its label beside its chips (the first three rows keep theirs over
  them), so seven rows fit the Nomad turned to landscape (664 dp tall of 749, nothing scrolls). The sheet is 580 dp wide at most,
  its rows 12 dp apart. **One Brush** opens a list of the toolbar's brushes and the library's; the one picked takes the place of
  the tools (choosing a toolbar again turns it off), at the size and grey a slot starts with. **Locked** greys Tools, No
  Erasing, One Brush and Timed Pages and shows **Unlock** beside Locked; **Sealed** asks once ("Sealed for good. The tools and rules
  can't be changed later.", **Seal** or Cancel), greys the same rows and has no Unlock. Template and Guides in PDF are never greyed.
  Nothing changes until Done, as before.
- **No Erasing** (`NotebookRules.refuses`, asked in one place for every stroke: `PaintActivity.allowStroke`) refuses the eraser
  (the bar's, the pen's eraser end and side button, the finger's), a smudge that takes paint away (Lift, Clear Water: its lift is the
  lifting) in the smudge tool or a slot, Clear Page, Clear Current Layer, Peel Off Masking Fluid and the old erase button. A smudge
  that only moves paint (Drag, Smear) is not erasing. The eraser's tile shows disabled (30%, "Eraser. Off in this notebook"), the
  hover ring is not drawn, a refused touch is followed to its end and marks nothing, with the note "Erasing is off" (at most once in 3 s).
  Undo and Redo still work.
- **One Brush**: the toolbar holds only that brush in **its first slot** and the other seven are hidden (a bar of one reads clearer
  than eight of the same); the slots all hold it in the file's terms (`ToolSlots.of`), its four sizes and greys are the app's swatches.
  The slots are frozen (rolling, picking, the Tools page and Brush Lab change nothing). The smudge tool, a second brush, is disabled.
- **Timed Pages** (`TimedPages`, `PageClock`): a page's timer starts at its first stroke; it counts real drawing time on the
  page, from the monotonic clock, and **pauses** whenever the page or the app isn't showing (the app paused, the shelf or the
  notebook's pages over it, another page turned to, the file saved) and counts on from what the page spent; a notebook closed for a week
  has lost nothing. In the last 30 s (10 s for 1 Min) the bar's page number blinks slowly (shown a second, hidden a second), drawn
  over the number's square only through the panel's fast update (`SelectionFeedback.update`), never while the pen is down (it waits,
  as the wet droplet does). At zero the page locks: a stroke in progress finishes first; then no marks (`allowStroke`, "Page
  locked"), no Undo, no Redo (`ToneDocument.undo/redo` refuse), no magnets moved, no layer changes; a small padlock by the number.
  It is not blank (`ToneDocument.unmarked`), so the blank page at the end that `BlankPages` keeps stays ready after it and the
  timer starts again on that page's first stroke. A locked page stays locked whatever Timed Pages is set to, and a duplicate of a
  timed page starts its own clock. Lock state is not shown in the notebook view's page bar yet.
- **Lock**: Locked and Sealed freeze a toolbar of its own (Surprise Me's, the library's) while drawing; My Toolbar is the app's
  and has nothing to freeze. A Sealed notebook's shelf cover (and the front cover wherever shown) gets a small seal in its bottom right
  corner (`NotebookCovers.seal`: an ink disc, a paper ring, a smaller ink disc and a dot, in ink and paper only, as the label's
  double rule), drawn over the cover and kept under its own key.
- **Storage**: records in the options block, each only when on: `NERS` (one byte), `ONEB` (the toolbar line), `TIMD` (minutes, one
  byte) and `LOCK` (1 Locked, 2 Sealed); the recovery's `TSRU` section; a page's `TIME` section in its own record (the drawing time
  spent, a flags byte for started and locked), only once its timer has started. Versions before them skip the records and the
  section: 233ba6b opens such a notebook, saves it again without the rules, and a locked page passes through its save still locked.
  A notebook with none of them is byte for byte as before.

### Rubbing the design off (branch `inkwash-covererase`)
The owner: "the eraser can erase the cover's design (the marble/speckle/etc. pattern) on a notebook's covers, but NOT the
label/nameplate/tape/sticker".
- **What rubs**: the Everything eraser (the bar's, the pen's eraser end, a finger) takes the cover's printed pattern where it
  passes, down to white, with the marks, and shows it so while it moves. The tape, the label and the cover picture are laid
  over the paper after the pattern, so they stay; the seal is drawn over the cover. A kind eraser (Pen, Paint, Dry Media)
  leaves it: the design is no kind of mark. No Erasing refuses it with the eraser.
- **Undo**: one step with the marks the stroke took. **Clear Page** brings the whole design back (one step; Undo rubs it
  again): the only way back besides Undo. **Cover Design** changed later: the rubbed places stay, and rub the new design.
  **Locked and Sealed** freeze tools and rules, not pages: the covers can still be drawn on and rubbed.
- **Storage**: where it is rubbed (`CoverWear`, a bit a pixel, deflated) is the cover page's own `WORN` section, so the
  file, the recovery and autosave keep it with the cover; a cover rubbed and not drawn on is kept as drawn. The front
  cover's picture (`FPIC`) carries it after its greys at the picture's size, for the shelf. Versions before it skip the
  section and stop reading the picture at its greys (ca133f7 opens such a notebook with the design whole); a notebook
  never rubbed is byte for byte as before.

## Order to build
1. **Covers:** independent, and visible at once.
2. **The options sheet:** Template and Guides in PDF.
3. **Tools:** per-notebook toolbar, Surprise me and re-roll.
4. **Rules:** No erasing, One brush, Timed pages. (Built.)
5. **Lock:** Open, Locked, Sealed. (Built.)

## Template guides on the screen (the owner, 2026-10-22, branch `erase-template`)

The owner: "A way to hide/show/adjust transparency of template". Built as a view of the notebook, like Page
Transparency: `NotebookOptions.guidesShown`, **Full** (default), **Half**, **Faint**, **Hidden**, with Page
Transparency's eyes (`PageTurner.STEP_ICONS`). Two places: the drawing screen's file menu row **Template** (shown with
Guides in PDF, when there are guides; a tap goes to the next step and says "Guides half" and so on; its icon is the
step's eye), and the library card, an eye with the step's word beside the Template swatch (none for Blank) that opens
the **Template Guides** screen of four choices. Half and Faint lighten each guide as a ghost of that step is
(`NotebookTemplate.shown`: 255 − (255 − g)·s, s 44% and 18%), made once a template, size and step and shared, so the
page's own dots draw them (no transparency on e-ink) at no cost a frame. Display only: never in a page, its file or its
copies. **Hidden does not drop the guides from the PDF and PNG copies**: Guides in PDF alone says what they print (the
simplest consistent rule: the step is about drawing, the copies have their own row). Not fixed by Lock (a view, not a
tool or rule). File: a `GSHN` record (one byte, 1 Half, 2 Faint, 3 Hidden), only when not Full, skipped by versions
before it (they show Full); the recovery keeps it in a `TSRG` section. Checks: `NotebookOptionsChecks.guidesShown`,
`LibraryCardChecks` (the eye, its part, its screen fitting both tablets).

## Opaque paint over the guides (the owner, 2026-10-22: "opaque media should cover template")

Looked into on branch `erase-template`, nothing changed in the drawing: the guides are the paper under every layer
(`ToneDocument.pixels`), and the page, the screen's raster (`ViewportBitmap`, `visibleLayers`), the crisp pages and
the PNG/PDF copies (`PagePicture.Composite.printed`) all lay the layers over them source-over. Measured (a Grid page, a
stroke across it, the share of a grid line's contrast left under the mark): Opaque round, Flat gouache and Impasto at
full Cover leave 0% at every grey (black to white); a Soft wash leaves 6% dark, 53% at half, 85% light, as transparent
paint should. Where guides do show through opaque paint is a Cover dial below 1 ("thin": Cover .4 leaves 40 to 60%,
as it leaves any mark under it), and the soft edges of a mark. `NotebookOptionsChecks.guidesUnderPaint` pins it: opaque
covers the guides on the page and in the copies, a wash lets them through. Open: which tool the owner saw it with (a
thin Cover, or the wet paint branch).

## Fixed tools on the Tools page (the owner, 2026-10-22, branch `rules-mold`)

The owner: "selecting an oil brush in the picker screen doesn't stick when i choose (notebook set to each page, this is
probably why... but it should prevent you from picking a new brush and explain) also it's not clear how to disable the
constraint .. do i select my toolbar? library? ..("all" brushes slider is on)".

Found: the Tools page (`ToolsActivity`) knew nothing of the notebook; back on the drawing screen `ToolSlots.use` refused
every pick of a frozen toolbar (`NotebookOptions.toolbarFrozen`: Each Page, or a Locked or Sealed notebook's own
toolbar) and said nothing.

- **The Tools page says so** (`NotebookRules.toolsFixed`, `toolsSettings`): its bar's "Tools" becomes the note
  "Notebook picks tools each page" (Each Page), "Notebook locked" or "Notebook sealed"; the library is greyed and takes no
  tap; Undo and New Brush go; **Notebook Settings** (not when sealed) goes back to the drawing and opens the library card
  with its **Tools** screen, or **Lock Settings** while locked (unlock first). A slot can still be tapped to try its brush.
- **A Brush Lab change** a fixed toolbar can't keep gives the same note instead of nothing.
- **The card's Tools screen**: **My Toolbar** "The app's toolbar, free choice" (the way out), **Each Page** "New every
  page, can't change"; the slider is **Brushes on Bar** (how many slots show, 1 to 8 or All), not a source of tools.
  Surprise Me and Library toolbars stay free to change unless locked.
