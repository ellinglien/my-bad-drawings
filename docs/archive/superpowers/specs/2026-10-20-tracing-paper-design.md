# Tracing paper: pages that stack, a strip of previews, one Stack panel

The owner approved the design, 2026-10-20, from the prototype `docs/brush-lab/prototypes/paper-lab.html` and
`paper-lab.NOTES.md` (branch `inkwash-paperlab`, 273e45f). This spec says how to build it in the app. It covers
design only, with no code. Read with `PHILOSOPHY.md`, `docs/TEXT-STYLE.md`, `docs/DESIGN.md` and `docs/INTEGRATION.md`.

## The idea, in one paragraph

A notebook's pages stack like tracing paper. Each page keeps its own layers, as now (`InkWashLayers`: up to four
wash+ink pairs). A notebook setting, **Paper**, controls what shows through the page being drawn on:
- **Plain**: nothing shows through.
- **Tracing**: the page before shows through, plus a fainter page after.
- **Glass**: every page shows, the drawing page Solid on top.

Every page and every layer has one of four **steps**: Solid, Half, Faint, Hidden. Ghosts and steps only change what
the screen shows. They never change a page's pixels, its file bytes, its PNG/PDF copy, Fill, the eyedropper or a
brush. A strip of tiny live page previews replaces ‹ 3 / 24 ›. One **Stack** panel holds the pages and their layers,
and it replaces the side bar's layer controls. The UI uses only two nouns for these things: **page** and **layer**.

## 1. What the owner sees

### The bottom strip (replaces `PageTurner`'s ‹ ›)

The strip keeps the page bar's place, height and look: 40 dp (36 on the Nomad), paper, no rule, the page's width.
The page loses no height. From the end away from the column, it holds:

- **Notebook** (as now).
- **Play** ▶.
- **Paper**: its icon shows the current Paper.
- **Stack**: the layers icon.
- **"3 / 24"**: today's `PageTurner.Number`, with its padlock and its Timed-page blink. The previews are not
  numbered, so this label says where the drawing page is in a notebook of 100.
- **The previews**: one per page, in a window around the drawing page.
- **Refresh** (dc9985f) holds the column end, as wide as Notebook, so the middle stays in the middle (`PageTurner.place`).

The front and back covers are the strip's first and last tiles, drawn as in `NotebookScreens`' strip. Tapping one
goes to that cover. With them, the strip replaces ‹ and ›: a tap on the next preview is the same as ›.

**Manta portrait** (1024 × 1365 dp, column 72 dp on the right, page 952 dp wide). Buttons are 56 dp. Previews are
22 × 30 dp with a 4 dp gap, so about 21 fit:
```
┌────────────────────────────────────────────────────────────────────┬────┐
│                                                                    │    │
│                         the page (drawing)                         │col │
│                     . . ghost of page 2 (Half) . .                 │    │
├────────────────────────────────────────────────────────────────────┤    │
│ [nb] [▶] [◫] [≋]  3 / 24  ▫ ▫ ▣ ▫ ▫ ▫ ▫ ▫ ▫ ▫ ▫ ▫ ▫ ▫ ▫ ▫ ▫ ▫ ▫ ▫ ▫  [⟳] │    │
│                            ▔ ━                                     │    │
└────────────────────────────────────────────────────────────────────┴────┘
  ▣ the drawing page: ink frame and 5 dp ink tab (as NotebookScreens' strip)
  ▔ the ghost: a 3 dp grey-600 tab      ░ Hidden: the preview at 35 % (LayerThumbnail.DIMMED)
```

**Nomad portrait** (749 × 998 dp, column 62 dp, page 687 dp). Buttons are 48 dp. Previews are 19 × 26 dp with a
3 dp gap, so about 15 fit:
```
┌───────────────────────────────────────────────────┬───┐
│                       page                        │col│
├───────────────────────────────────────────────────┤   │
│[nb][▶][◫][≋] 3 / 24 ▫ ▫ ▣ ▫ ▫ ▫ ▫ ▫ ▫ ▫ ▫ ▫ ▫ ▫ ▫ [⟳]│   │
└───────────────────────────────────────────────────┴───┘
```
With the column on the left, the strip is mirrored: Notebook and the icons sit on the right, Refresh on the left.
In landscape the same strip holds more previews.

What each gesture on a preview does:

| Gesture | What happens |
|---|---|
| Tap | Goes to the page. |
| Quick drag (moves 8 dp before 400 ms) | **Riffle**: the page area shows the picture of the page under the finger (`PagePicture`, nothing decoded). Release goes to that page. Near a strip end, the window slides one page every 150 ms. |
| Hold 400 ms, then drag | **Reorder**: the preview lifts (3 dp ink frame), and a 2 dp ink gap shows where it will land. Release moves the page. |
| Hold 400 ms, release without moving | Opens the **quick-adjust popover** above the preview. |

The quick-adjust popover is `InkWashBar`'s panel style: 2 dp ink box, square corners. The Ghost row shows in Tracing only.
```
┌ PAGE 5 ─────────────────────────────┐
│ [Solid] [Half] [Faint] [Hidden]     │   page step (the one in force is ink-filled)
│ Layer 2  [S] [H] [F] [–]            │   its layers, top first
│ Layer 1  [S] [H] [F] [–]            │
│ [Ghost]                             │   Tracing only
└─────────────────────────────────────┘
```
In the layer rows, S/H/F/– are drawn as four grey swatches (ink, grey-600, grey-400, a struck square) with screen
reader names Solid, Half, Faint, Hidden. The page row has the room to spell its four words out. A tap outside, or a
page turn, closes the popover.

### Icon buttons: a tap does the obvious thing, a hold shows more (owner rule)

The icons use the bar's existing long press (`ToolButton`, the system timeout). A button with no options shows its
name on a hold (`setTooltipText`), as Refresh does now.

- **Play** ▶. A tap replays the drawing page (Replay at the last speed, 1× at first). A hold opens a small chooser:
  **Replay** | **Flipbook**, then the speeds **1× 2× 4×** (Replay) or **2 fps 4 fps 8 fps** (Flipbook). Picking one
  plays it and makes it what a tap does. A touch anywhere stops playback, as `PageReplay` does today.
- **Paper**. A tap cycles Plain, Tracing, Glass. A hold shows the three as a column of chips, the current one
  ink-filled. The icons are Phosphor Bold: Plain `file`, Tracing `copy`, Glass `stack-simple`.
- **Stack** (`ic_layers`). A tap opens or closes the Stack panel. A hold shows its name.
- **Notebook** and **Refresh** work as they do now.

### The Stack panel (replaces the layer controls)

The panel sits above the strip at the column's side, 360 dp wide (320 on the Nomad) and up to the page's height.
Pages are listed top first: the top of the stack is the last page.
```
┌ PAGES AND LAYERS ───────────────────── ✕ ┐
│ ▸ ≡ [▫] Page 6   [S][H][F][–]            │
│ ▾ ≡ [▣] Page 5 ✎ [S][H][F][–]            │  the drawing page (✎), opened
│     ≡ [▫] Layer 2 ✎ [S][H][F][–]         │  the drawing layer: ✎, row ink-filled
│     ≡ [▫] Layer 1   [S][H][F][–]         │
│     + New layer                          │
│ ▸ ≡ [▫] Page 4   [S][H][F][–]            │
│   ▾ 18 more                              │  window: the rest of a long notebook
├──────────────────────────────────────────┤
│ PAPER  [Plain] [Tracing] [Glass]         │
└──────────────────────────────────────────┘
```

- **Page row**:
  - ▸ opens the page's layers (the drawing page is open by default).
  - ≡ drags the page to a new place.
  - The preview, "Page 5", the four steps, and ✎ on the drawing page.
  - Tapping the row goes to that page.
- **Layer row**:
  - ≡ drags the layer within its page.
  - `LayerThumbnail`'s picture, "Layer 2", the four steps, and ✎ on the drawing layer.
  - A tap makes it the drawing layer, on its page.
  - A hold opens **Move Layer Up**, **Move Layer Down** and **Delete Layer**.
  - **+ New layer** adds a layer above the drawing layer. It is greyed at four.
- **Nothing scrolls** (`DESIGN.md`): the panel shows the rows that fit around the drawing page. "▴ 3 more" and
  "▾ 18 more" move the window by a screenful, and a ≡ drag held at either end moves it too.
- The Paper chips at the foot make the panel complete on its own.

### What each Paper shows

| Paper | Under the drawing page |
|---|---|
| Plain | Nothing. Page steps are ignored. Layer steps apply. |
| Tracing | The page before, at its step (Half if never set), and the page after, at its step (Faint if never set). A pinned **Ghost** takes the page before's place. A Hidden page gives no ghost. The page before that one does not take its place. |
| Glass | Every other page, in stack order, each at its step (Half if never set). Hidden pages are left out. The drawing page is Solid on top. |

**Fixed rules**:
- The drawing page always shows Solid.
- The drawing layer always shows Solid. Its own step returns when another layer becomes the drawing layer.
- Choosing a Hidden layer shows it, as drawing on a hidden pair does today (`InkWashLayers.showSelected`).
- Covers are not in the stack. A cover never ghosts, has no steps, and shows Plain.

## 2. Steps and grey levels

Each step is a fixed strength. The effective strength of a layer on a ghost page is page step × layer step.

| Step | Strength | Black ink shows as | Opacity passed to `nativeCompose` |
|---|---|---|---|
| Solid | 1 | 0 | 100 |
| Half | 0.44 | tone 143 (palette 144, step 7 of 16) | 44 |
| Faint | 0.18 | tone 209 (palette 208, step 13 of 16) | 18 |
| Hidden | 0 | not drawn | not composed |

- A grey mark of tone g at strength s shows as 255 − (255 − g)·s, and the page's dots render it like any other grey.
- Half × Half = 0.19, which looks Faint. Faint × Faint = 0.03, which is nearly paper. That is correct; it needs no special case.
- Opacity is a per-layer `int` that `nativeCompose` already takes, so steps need no new arithmetic in native code.

### Dots, not panel grey

**Decision: ghosts render through the page's own path.** They are one more layer under the page's layers, put
through the 8 × 8 ordered dots (`DotPattern`) when not zoomed and plain tones when zoomed (`ViewportBitmap.compose`).
Reasons:
- The page raster is binary dots already, so the fast binary update while drawing keeps ghosts crisp. The grey
  waveform would smear them at every stroke.
- It needs no new display mode.
- Ghosts and real greys look the same on screen. The step and the Paper icon say which is which, and Plain shows the truth.

**Risk:**
- A 1 px pencil line at Faint dithers to about one dot in three, so it reads as a dotted line. That should be enough
  to trace by.
- Check it on the Manta in Stage 2. If it doesn't read, Faint goes to 0.25 (tone 191) and nothing else changes.
- Half must stay visibly lighter than the drawing page's darkest greys. Check that on the device too.

## 3. Data model and file format

### In memory

`DrawingBook` gains three fields:
- `paper`: the Paper (PLAIN 0, TRACING 1, GLASS 2).
- `ghost`: the pinned page, or −1.
- `steps`: a list kept parallel to `pages` and `canvasChoices`.

Each step entry has two parts:
- **The page step**: 0 = never set (the Paper's default), 1 Solid, 2 Half, 3 Faint, 4 Hidden.
- **The layer steps**: 2 bits per pair, for up to 4 pairs: 0 Solid, 1 Half, 2 Faint.

Steps move, copy and go with their page wherever `pictures` does (`shiftPages`, `remove`, `duplicate`, the new
`move`). `ghost` follows its page as `cover` does, and becomes −1 when its page is deleted.

**Hidden layer = the layer's existing visible flag** (`InkWashLayers.show`, saved in the page, undoable). So:
- today's hide becomes the Hidden step, unchanged;
- export keeps leaving hidden layers out, as now;
- an older version shows the same layers.

Half and Faint on a layer are view-only bits in `steps`. They are never `Layer.opacity` (MonoPaint's), which is
written into the page and would print.

**Reorder**: `DrawingBook.move(from, to)` is new. It does the following:
- moves the packed page, its picture, its canvas choice and its steps;
- re-keys the decoded cache and the revision maps the way `shiftPages` does;
- moves `cover`, `ghost` and `index` with their pages;
- counts as one book edit (`edits++`), as Delete page does.

Reorder is not undoable; dragging the page back reverses it. Page bytes are untouched, so the PNG copies keep
their CRCs: `NotebookExport` already keeps a moved page's PNG under its new number.

### In the file

Everything goes in the notebook's options block, `BookOptions` "INO1": tagged records that older versions skip,
added only when they aren't the default.

| Record | Bytes | Written when |
|---|---|---|
| `PAPM` | 1 byte: 1 Tracing, 2 Glass | Paper isn't Plain |
| `GHST` | int: the pinned page | A ghost is pinned |
| `STEP` | int: page count, then per page 1 byte page step and 1 byte layer steps | Any page or layer step isn't the default |

- **Validation:**
  - A `STEP` record whose count isn't the page count is ignored.
  - So is a byte outside its range.
  - A `GHST` outside the pages is ignored.
- **Byte-identical when unused**: Plain, no ghost and default steps write no records. A notebook that never used
  any of this writes exactly as before, and `Covers.any()` stays false for it. A layer hidden today stays in the page,
  as today.
- **Why not in the page's own sections** (`DocumentCodec` TSM5)? Steps are a view. In the page they would change its
  bytes and CRC, so every step change would redraw its PNG and PDF copies and dirty a page that wasn't drawn on.
- **An older version**:
  - It opens a Tracing notebook as Plain, with every page as drawn.
  - If it saves the notebook, it drops the three records (Paper goes back to Plain), as it does for any record it
    doesn't know.
  - Hidden layers stay hidden, because that is the page's own flag.
- **Recovery** (`RecoveryCodec`): a new trailing section, `PAPER`, goes after RULES with the same three things.
  Older readers stop before it, as they do for RULES. A Paper, step, ghost or reorder change counts as a book edit,
  so autosave and recovery pick it up on their usual schedule ("Saving less while drawing"). No page is re-encoded.

## 4. Rendering ghosts and their cost

### One ghost plane, not N layers

**`GhostPlane`** is new, pure Java and checked on the desktop. It holds one byte per page pixel: the ghost pages
flattened at their strengths over the drawing page's paper tone. `ViewportBitmap.compose` passes it as an opaque
layer under the page's layers, in the slot `ToneDocument.visibleLayers` already uses for a cover's paper (`under`).
Covers never have ghosts, so the two never meet.

- **Per stroke**: one more layer read in `renderDirty`, inside the dirty box only. The eraser's preview
  (`showErased`) and Real Mirror's reflection read the plane too, so erasing reveals the ghost, not white.
- **Never seen by the page**: `compositeTone`, Fill, Stay in Lines, the eyedropper, `PagePicture`, export and Replay
  use the document, which holds no ghost. A check proves this (Stage 2).
- **Layer steps of the drawing page**: `visibleLayers` multiplies each pair's opacity by its step. The drawing pair
  stays at 100.

### When the plane is built

The plane is keyed by everything it depends on:
- the Paper,
- the drawing page,
- the ghost pages' revisions or CRCs,
- their steps,
- the order.

Other pages can't change while one is being drawn on, so the plane is built only when one of these changes:
- **A page turn**: the plane is built on a worker. The new page shows at once if its plane is ready within 300 ms.
  If not, the page shows first and the plane follows with one more full-page update (`refreshAfterLargeChange`).
- **A step, ghost, Paper or reorder change**: the rebuild waits 300 ms after the last tap (debounced), so several
  taps in Stack refresh once.
- **Tracing** decodes the page before and the page after. The page before is usually still in `DrawingBook`'s
  two-page cache. Each is flattened a row at a time (`PagePicture.Composite`), then dropped, without entering the cache.
- **Glass** decodes pages one at a time, bottom to top, into the same plane.

### Memory on the Manta

The page is at most 1920 × 2560, about 4.9 Mpx.

| What | Size |
|---|---|
| Ghost plane | 4.9 MB, plus one shared opaque alpha of 4.9 MB, allocated once (or `nativeCompose` treats a null alpha as opaque: a one-line native change, preferred) |
| A page decoded to build it | about 20 MB for each pair, one page at a time, freed before the next |
| Previews | 100 × (41 × 56 px) ARGB, about 0.9 MB |
| Flipbook ring | 3 frames of 1-bit dots, 0.6 MB each |

Tracing and Glass cost the same memory: one plane. Building is the only thing that grows with the page count.

### Speed

Targets, measured with `NotebookBench` on desktop pages at Manta size, then on the device:
- Tracing plane: 250 ms or less.
- Glass plane: 1 s or less for 20 pages.
- `renderDirty` while drawing: no more than 10 % slower than in Plain.

If Glass misses its target, add a per-page **flat** cache: each page's composite, deflated (mostly white, so small),
kept in the app's cache folder by page CRC. A Glass rebuild then inflates instead of decoding. Add it only if the
bench says so.

### Previews

- **The drawing page**: sampled from the document after each stroke settles, in `LayerThumbnail`'s way (3 × 3 samples
  per preview pixel, all visible layers). This reuses the bar's `THUMBNAILS_MS` idle redraw and draws through
  `selectionFeedback.update` (the fast update of that one square), never under the pen.
- **Other pages**: their stored `PagePicture`, scaled down. A page with no picture yet gets one from
  `NotebookScreens`' "page pictures" worker.

## 5. The layer controls map into Stack

There are two layer UIs today:
- **`InkWashBar`'s layer column**: add above and below, thumbnails, a folded list. Hidden since 2026-10-18 (`DrawLayout.LAYERS = false`).
- **MonoPaint's `LayersPanel`**: on MonoPaint's own Toolbar rail.

Stack replaces the first. Its code (`layerPanel`, `layerGroup`, `folded`, `sizeLayers`, the LAYERS budget in
`DrawLayout`) is deleted in Stage 3; its actions are reused inside Stack. Stage 3 confirms that no Inkwash screen
reaches MonoPaint's panel (upstream, left untouched).

| Today | In Stack |
|---|---|
| Tap a thumbnail: select its pair | Tap a layer row: it becomes the drawing layer (✎) |
| New layer above / below (`InkWashLayers.add`) | **+ New layer** (above the drawing layer). Below: hold the row, then Move Layer Down |
| Show / Hide layer | The Hidden step, or any other step (the same undoable flag) |
| Move layer up / down | ≡ drag, as one undo step (new `InkWashLayers.moveTo`, through the same `Stack.apply`). Also on the row's hold |
| Delete layer (undo restores it) | Hold the row, then **Delete Layer**. Under No Erasing, refused for a layer with marks ("Erasing is off"), as Clear Layer is |
| Thumbnail, dimmed when hidden | The same `LayerThumbnail` in the row |
| The folded list (a short column) | Gone. The panel is the one place |
| Clear current layer (Notebook menu) | Stays in the menu |
| MonoPaint's opacity and rename | Not offered in Inkwash, as now. Steps cover "see it fainter" without changing the page |

## 6. Interaction details

- **Thresholds**: all in one pure class, `StripGesture`, checked on the desktop. It classifies (time, distance,
  pointer kind) into tap, riffle, lift or popover.
  - Hold: 400 ms. Move: 8 dp (not px; 15 px on the Manta). These are the same for pen and finger to start with.
  - A second pointer cancels the gesture.
  - A hover never counts.
  - The pen's side button held during a tap opens the popover at once: the pen's shortcut for the hold.
  - Tune the numbers on the device in Stage 1; the starting values are the prototype's.
- **Pen and finger**: both work the strip and Stack, as for the rest of the chrome. On the page, a finger stays the
  finger tool, and there is no riffle on the page: it would clash with drawing.
- **The bottom edge**: the previews keep 4 dp clear of the screen's edge, so a riffle doesn't wake a system
  edge-swipe (`edgeSwipeSlopDp`).
- **Busy**: riffle, reorder, Play and an open Stack wait for the stroke to finish (`finishStroke`, `dryWet`), as
  page turns do. `app.busy()` includes Flipbook, as it already includes Replay.

## 7. Play: Replay and Flipbook

- **Replay**: `PageReplay` as it is. The tap calls `start(speed)` directly, and the chips move into Play's hold.
  Replay shows the page Plain:
  - no ghost plane, layer steps ignored, ink as drawn (the prototype's rule);
  - the ghost returns when it stops.
  - The Notebook menu's "Replay page" row goes in Stage 4.
- **Flipbook**: a new `Flipbook` beside `PageReplay`, with the same handler, step, stop-on-touch and HOLD_MS pattern.
  - It shows each page Solid and Plain, in stack order, from the drawing page to the last, then loops once.
  - A worker prepares the frames: decode, compose to 1-bit dots, then a ring of 3 frames.
  - Each frame goes to the panel through `PageTurnDisplay`, the page turn's one-shot update.
  - When a frame isn't ready in time it is skipped, never queued, so 8 fps means at most 8 fps. Stage 4 measures
    what the Manta reaches.
  - The fallback, if decoding can't keep up: frames from `PagePicture` scaled up four times.
  - It needs two shown pages. Otherwise it shows the note "Needs two pages".
- **Do Hidden pages play?** Recommendation:
  - Flipbook **skips** Hidden pages, so a sketch page can stay in the notebook and out of the animation.
  - Riffle and the strip still reach every page.
  - Replay of a Hidden drawing page plays: it was asked for.

## 8. PDF export of Glass

**Recommendation: the PDF stays pages as drawn, one per page.** The rules:
- Ghosts, page steps and layer Half/Faint never print.
- Hidden pages print.
- A Hidden layer stays out, as hidden layers do today.

`NotebookExport` and `AutoExport` don't change, and no export button is added. A Glass composite is a picture of a
view, and nobody has asked for it to be printed (open question 1).

## 9. Rules, the lock and timed pages

- **Lock (Locked, Sealed)**: this locks the tools and the rules. Paper, steps, ghosts and reordering are a view of
  the pages, not a rule, so they stay free.
- **No Erasing**:
  - Steps and Hidden are not erasing: they hide, they don't take marks.
  - Delete Layer on a layer with marks is refused (see §5).
  - Clear page and Clear current layer are refused, as today.
- **Timed Pages**:
  - Riffle, Flipbook and showing a page as a ghost don't run a page's clock (`PageClock` counts drawing time).
  - A page locked by its time can still be a ghost, change step and move. It can't be drawn on, and that includes
    its layers' ✎. A tap on its layer row says "Page locked".
  - The padlock and blink stay on "3 / 24".
- **One Brush, Template, Guides in PDF**: not affected. Guides are the drawing page's paper, under the ghost plane's
  paper tone. Ghost pages' guides are not ghosted, so lines never double up.

## 10. Accessibility

- **Screen-reader text**:
  - Each preview says "Page 3 of 24", plus ", ghost" or ", hidden".
  - The drawing page says "Page 3 of 24, drawing".
  - Covers say "Front cover" and "Back cover", as now.
  - Paper says "Paper: Tracing". Steps say their word.
- **Without dragging**: every gesture-only action has a plain path.
  - A preview's accessibility actions: **Move Page Up**, **Move Page Down**, **Page Options** (the popover).
  - Stack's layer hold menu has the layer moves.
- **Targets**:
  - The strip is one wide target: a tap goes to the nearest preview, so 22 dp previews don't need 48 dp hit boxes.
  - Stack rows are 56 dp tall, and step chips 40 dp.
- **Discovering what a hold does**: no feature lives only behind a hold. Stack shows everything the popover and
  Paper's hold show.
- **E-ink**: no animation. A lifted preview is shown by a thicker frame, not by motion. The text is 12 sp or more,
  with no clipping (`DrawLayoutChecks`).

## 11. Words (add to `docs/TEXT-STYLE.md`'s list)

- **Paper** (the notebook's): **Plain**, **Tracing**, **Glass**.
- **Steps**: **Solid**, **Half**, **Faint**, **Hidden**. **Ghost**.
- **Play**: **Replay**, **Flipbook**, "1×", "2 fps".
- **Stack's title**: **Pages and Layers**. **New Layer**, **Move Layer Up/Down**, **Delete Layer**,
  **Move Page Up/Down**, **Page Options**.
- **Notes**: "Needs two pages", "Nothing to replay" (exists).
- "Stack" is this spec's name for the panel, never on screen.
- **Paper collides with a brush's Paper part** (Smooth, Cold Press …). See open question 3.

## 12. Staged build plan

Each stage installs and works on its own (release + compile install routine), and leaves the app shippable. Each
stage also does three things:
- updates `INTEGRATION.md` (upstream files touched: `DrawingBook`, `BookCodec`/`BookOptions`, `RecoveryCodec`,
  `ViewportBitmap`, `ToneDocument`),
- adds its checks to `scripts/test_inkwash.sh`,
- adds a CHANGELOG line.

### Stage 1: the preview strip and its gestures

- **Build**:
  - `PageStrip`, which replaces ‹ › in `PageTurner`. It keeps Number (padlock, blink), Notebook and Refresh, and adds
    the previews and the cover tiles.
  - `StripGesture`: tap, riffle (from pictures), hold+drag reorder, hold+release.
  - `DrawingBook.move`.
  - In this stage the popover only says the page's number. No Paper yet.
- **Desktop checks**:
  - `StripGestureChecks`: thresholds, pen and finger, second pointer.
  - `DrawLayoutChecks`: the strip fits the Manta and Nomad, both column sides and landscape. Preview count, 12 sp,
    Refresh and Notebook at their ends.
  - `NotebookFileChecks`: a move keeps pictures, cover, choices and CRCs; it survives save, recovery and reopen;
    PNG copies are reused.
  - Preview render timing: 100 pages under 50 ms from pictures.
- **On the device**: hold and move thresholds with pen and finger, riffle speed, the bottom edge.

### Stage 2: Tracing ghosts

- **Build**:
  - Paper (Plain and Tracing; the icon cycles two for now).
  - `GhostPlane` and its key, the plane in `ViewportBitmap.compose`, `showErased`, and Real Mirror.
  - Page steps and Ghost in the popover.
  - `PAPM`, `GHST` and the page-step half of `STEP`; recovery `PAPER`.
- **Desktop checks**:
  - `TracingChecks`:
    - Half and Faint black land on 143 and 209.
    - The plane equals a reference composite.
    - `compositeTone`, Fill, export PNG and `PagePicture` are identical with and without ghosts.
    - The plane is rebuilt only when its key changes.
  - `NotebookOptionsChecks`: a Plain notebook writes byte for byte as before; the records round-trip; a bad count is
    ignored.
  - An older-reader check (the `OlderNotebookCheck` pattern, against af06002): a Tracing notebook opens as drawn.
  - `NotebookBench`: Tracing plane build time at Manta size.
- **On the device**: ghost legibility at Half and Faint (§2's fallback), page-turn time, `renderDirty` within 10 %.

### Stage 3: Glass, the Stack panel replaces the layer controls, layer steps

- **Build**:
  - Glass.
  - The Stack panel: pages, layers, ≡ drag, steps, ✎, Paper chips, window.
  - Layer steps in `visibleLayers`, and the layer half of `STEP`.
  - `InkWashLayers.moveTo`.
  - Delete Layer under No Erasing.
  - Delete `InkWashBar`'s layer column and `DrawLayout.LAYERS`.
- **Desktop checks**:
  - `StackLayoutChecks`: fits both devices, 100 pages windowed, no scrolling, 12 sp.
  - `InkWashLayersChecks`: `moveTo` is one undo step and restores exactly.
  - `TracingChecks`:
    - Glass order and Hidden pages left out.
    - Page × layer strength.
    - The drawing page and layer stay Solid.
  - Memory: one plane, whatever the page count.
  - `NotebookBench`: Glass for 20 and 100 pages; this decides the flat cache.
- **On the device**: Glass with 20 pages, the Stack panel by pen, and that nothing from the old layer column is
  missing (§5 table).

### Stage 3b: layers are pages (the owner, 2026-10-21)

On the Manta the owner found the Stack panel hard to read (the unlabelled black, grey, light grey and struck step
swatches) and the screen sluggish while it was open. He chose a simpler model, which replaces §1's Stack panel and §5:

- **A layer is a page.** The panel is a plain layers panel titled **Layers**: a row a page, the last page on top, a new
  notebook one layer. A row: ≡ (drag to reorder, not undoable, as a page move), its preview, "Layer 3", and one eye (a tap
  sets the page's step Hidden or back to never set; the only see-through control in the panel). Tap = go to that page.
  Hold = **Duplicate Layer**, **Delete Layer** (asked first unless blank; refused under No Erasing for a page with marks,
  as Clear page is) and, on the page drawn on when it holds more than one in-page pair, **Merge Old Layers**. **New Layer**
  at the foot adds a page right after the page drawn on and goes to it (none past `MAX_PAGES`). Gone from the panel: steps,
  ▸, layer rows, ✎, the Paper chips. Windowed for 100 pages as before.
- **Steps per page** stay on the strip's hold popover, as words (Solid, Half, Faint, Hidden), not swatches.
- **Notebook-wide defaults** in Notebook Options (open or new notebooks): **Layers Show Through**: **Off** (Plain),
  **Page Before** (Tracing), **Nearby Pages** (Glass, the nearest four), and **Faint**, the strength a page never set shows
  at (else Half). Per-page steps override it; the ghosts (onion skin) read it, and so will Play. Kept in `PAPM` and a new
  `PSTR` record (one byte, only when Faint; older builds skip it) and the recovery's `TSRS` section. The Paper button on
  the strip says the same three words.
- **In-page layers** (`InkWashLayers` pairs) are no longer offered. No notebook is rewritten: a page with several pairs
  renders as before (its layer steps too). **Merge Old Layers** flattens every pair, hidden ones included (so they show,
  never lost), into one ink layer as one Undo step.
- **The sluggishness**: the strip and the panel drew the same page pictures at two sizes into one cache keyed by the
  picture alone, so each evicted the other and asked for a new decode; every decode that arrived updated both, which
  drew again at the other size, for as long as the panel was open. The cache is now keyed by size too
  (`NotebookScreens.drawPreview`), and the panel never makes a picture of the page drawn on.

### Stage 3c: layers are stacked pages (the owner, 2026-10-21)

The owner found 3b's Layers panel "too hard to navigate" and "buggy", and asked for layers "as easy as normal", like
Inkwash's original side-column layers: "they will just be clear pages automatically". This replaces 3b's panel:

- **A layer is a clear page stacked on the page below.** A page may carry a small mark, "stacked on the page below"
  (`TracingPaper.LINKED`, kept in a new `STAK` record: the page count, then a byte a page; only when a page is stacked).
  A **stack** is a run of linked pages, its first page the bottom layer (`PageStacks`), four layers at most (as the old
  in-page layers). A build without `STAK` skips it and shows each layer as a page of its own (`OlderOptionsCheck` against
  2ddb2a4). The recovery keeps it in a trailing `TSRK` section.
- **The side column, the original way** (`LayerColumn`, under the tool buttons, `DrawLayout.Part.LAYERS`): + above, a
  thumbnail a layer of the stack drawn on (the top first, the one drawn on framed), + below. One tap adds a clear page
  stacked onto the page drawn on, above or below, and draws on it; a tap on a thumbnail draws on that layer. Only the
  stack drawn on shows, never the rest of the notebook. A hold on a thumbnail (or a tap on the one drawn on) opens its
  menu beside it: **Duplicate**, **Merge Down**, **Delete** (each one Undo step, `LayerEdits`: the page and its link put
  back exactly while nothing changed since; Delete refused under No Erasing for a layer with marks; Merge Down refused on a
  timed page locked), its step (**Solid**, **Half**, **Faint**, **Hidden**, each with its eye) and, on a page drawn with
  the old in-page layers, **Merge Old Layers**. Where the column has no room for a full stack at the least sizes (the
  Nomad in landscape), the group folds into one button showing the layer drawn on, which opens the group beside it; the
  same on every stack of a screen, so the column never jumps as layers come and go.
- **Rendering**: the stack's other layers show through the one drawn on, each at its step (Solid if never set; Hidden
  left out), through the ghost plane (`GhostPlane`, `Ghosts`): no new compositing. A page's step is now its step as a
  layer. Decision: the plane lies under the page drawn on, so a layer above it shows under its marks (white paint on the
  layer drawn on covers the layers above too); the eraser's preview reveals the layers below, not white.
- **Everywhere else a stack is one page**: the bottom strip (one preview, the layers' pictures over each other,
  `PagePicture.stacked`; the live page multiplied over them), "3 / 24" and its count, the notebook screen (its strip, its
  big page flattened, Delete Page deleting every layer, Use on Cover the bottom page), the library card's and the shelf's
  page counts (`BookPictures.Cover.stacks`), page turns (on the stack's top layer), moves (`DrawingBook.moveStack`), Each
  Page's brushes, Page Transparency (Page Before and Nearby Pages show whole stacks, each layer at the notebook's strength
  times its own step), and the copies (`NotebookExport`: a PNG and a PDF page a stack, its layers flattened under the
  bottom page's guides, a Hidden layer left out; a page alone keeps its PNG's key). Replay plays the layer drawn on.
- **Guides** are the bottom page's: every layer shows them, a page's own template is its stack's, and when the bottom
  layer goes the layer on it takes them.
- **The blank page at the end, always** (the owner: "be sure to always have or create a new page after the last one"):
  after every page change (`PaintActivity.updatePages`: opening, adding, deleting, moving, turning, Time is up's Next
  Page) and at the first mark on a layer of a blank last page (`BlankPages.end`, `Watch`), the last stack is blank or a
  blank page goes after it; not a change to save, and the copies still leave blank pages at the end out.
- **Gone**: the Layers panel (`StackPanel`, `StackLayout`, `StackLayoutChecks`), the page bar's Layers button (the strip
  is Notebook, Zoom, the number, the previews), the strip popover's steps (a layer's step is the column's; the popover
  keeps "Page 5" and, with Page Before, Ghost, which pins the stack's bottom page), `TracingPaper.shownStep`.

**Follow-up (2026-10-21, branch `inkwash-stacks2`):**
- Layers above cover the layer drawn on, as normal layers do: the see-through is two planes (`GhostPlane.Planes`): one
  under the page drawn on (the layers below it and Page Transparency's stacks, as before) and one over the page's own
  layers (the layers above it, flattened at their steps, their tones and cover, white paint too), composed by the
  screen alone (`ToneDocument.visibleLayers`' last layer; `nativeCompose` takes 11), never in the page. A lower layer's
  white paint no longer covers an upper one. The eraser's preview and Real Mirror show both (`GhostPlane.over`).
- A turn to a stack lands on the layer of it drawn on last this session (`DrawingBook.pageOf`; memory only), else its top.
- A neighbouring stack shows flattened, its layers at their own steps, then once at Page Transparency's strength: an
  untouched one shows at exactly Page Before's (Half), never compounded per layer.
- At most six pages are decoded for a turn (`TracingPaper.MOST_DECODES`): the stack drawn on's layers first, then the
  nearest stacks that fit whole. Desktop, 1920 × 2560, Nearby Pages with stacks of two: 5 pages, both planes in about 50 ms.

Bugs found in the 3b code paths (the owner's "feels buggy"):
1. The panel's preview of the page drawn on was blank or stale: it never made a picture of that page, and nothing else drew it.
2. The eye did nothing visible with Page Transparency Off (and on the page drawn on, which always shows): it only set
   the step ghosts read. Now a step changes how the layer shows in its stack at once.
3. After a drag, the hold menu stayed under the old page number (`menu` wasn't moved with its page).
4. Moving the blank last page into the middle, or deleting it, left the notebook without one until it was opened again.
5. Delete Layer asked "Delete page 5?" and couldn't be undone.
Not reproduced: page-index desyncs after a move (`DrawingBook.move` re-keys the cache, revisions, cover, ghost and the
page drawn on; `PageStacksChecks` moves stacks both ways and back); Undo after a move (moves aren't undoable, as
specified; Undo takes back the page's own strokes). The "▴/▾ more" window went with the panel.

### Stage 4: Play

- **Build**:
  - Play: tap = Replay; hold = Replay | Flipbook with speeds.
  - `Flipbook`: frame ring, `PageTurnDisplay`, skips Hidden.
  - Replay shows Plain.
  - Remove "Replay page" from the Notebook menu.
- **Desktop checks**:
  - `FlipbookChecks`: frame order, Hidden skipped, loop, stop on touch, frames dropped and never queued.
  - The 1-bit frame packing round-trips.
  - `StrokeLogChecks` unchanged.
- **On the device**: the fps reached at 2, 4 and 8, and whether the `PagePicture` fallback is needed.

## Open questions for the owner

**Answered 2026-10-20: all six as recommended.**
1. The PDF stays pages as drawn, one per page; ghosts never print, and no export button is added.
2. Flipbook skips Hidden pages.
3. The word stays **Paper**; screen readers say "Notebook paper".
4. No undo for steps or page reorder; hiding a layer stays undoable.
5. Ghosts at Half 44 % and Faint 18 %, in the page's own dots.
6. The strip stays 40 dp tall (36 on the Nomad).

Also 2026-10-20: the page bar's Refresh button was removed (712a0b7), so the strip has none; where this spec puts Refresh
at the column end, there is nothing (Stage 1 lets the previews run to it, 4 dp short).

The questions as asked:

1. **Glass in the PDF?** Recommend: no. The PDF stays pages as drawn, and no export button is added. A "print what
   Glass shows" can come later if missed.
2. **Hidden pages in Flipbook?** Recommend: skipped. Hidden means "not in the show". Riffle and the strip still reach
   every page.
3. **"Paper" is also a brush part (Smooth, Cold Press …).** Recommend: keep the owner's word, since it is only ever
   seen on the strip and in Stack. Screen readers say "Notebook paper".
4. **Undo for steps and reorder?** Recommend: no. They are a view and a book change, like Delete page. Hiding a layer
   stays undoable, as now.
5. **Ghost strength.** Recommend Half 44 % and Faint 18 % as dots. If a fine pencil line doesn't read on the Manta,
   raise Faint to 25 %.
6. **Strip height.** Recommend keeping 40 dp (36 on the Nomad), so the page loses nothing and previews are
   22 × 30 dp. Making them bigger costs the page 8 dp.
