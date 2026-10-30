# Notebooks: design

Status: designed and built on branch `inkwash-books`, 2026-10-18, from `inkwash-v2-lab` `580a326`. Owner's ask: a
Paper-by-WeTransfer-style app where everything lives in notebooks, not loose drawings (mockup:
claude.ai/artifact/5Z7ND16BszQ5cozLhMjHrQ). The user-facing word is **notebook**. Plan:
`docs/archive/superpowers/plans/2026-10-18-notebooks.md`.

## What was there

MonoPaint already keeps up to 100 pages in one `.mpaint` file (`BookCodec`: a ZIP of a `manifest` and `pages/N.tsm`,
at most 16 MB of pages), keeps at most two pages decoded (`DrawingBook`), and turns pages fast on e-ink
(`DrawingPad.showPage`, `PageTurnDisplay`). Inkwash hid all of it with MonoPaint's header. Opening a file inflated every
page to check it (50 ms a 1920 × 2400 page on this Mac, about 1 s on the Manta: 24 s for 24 pages); the Open dialog
decoded page 1 of every drawing for its tile, every time.

## The screens

1. **The shelf** (the app opens to it). Notebooks newest first, by when they last changed; each a cover (a page picture,
   page 1 unless another was set), its name and "24 pages · Oct 17" ("today", "yesterday", "Oct 17", "Oct 17 2025"; "1
   page"). Last, a dashed **New notebook** tile. More notebooks than fit: ‹ › page buttons under the grid; nothing
   scrolls. Tapping a cover opens the notebook; a long press on it opens a small menu, **Rename** and **Delete** (the
   name prompt; Delete asks first unless nothing is drawn in the open notebook, and always for another).
2. **Inside a notebook.** A header: **‹ Notebooks**, the name (tap to rename), "3 / 24". The page, as big as fits, with
   ‹ › beside it; a finger swipe also turns it. Under it a strip of page pictures, the current one framed in ink with a
   tab under it, and a dashed **+** tile that adds a page at the end. The strip shows a window of pages round the
   current one. At the foot: **Draw** (ink-filled; tapping the page does the same), **Set as cover** (greyed when it
   already is), **Delete page** (asks first unless the page is blank; greyed on the only page).
3. **Drawing.** The side bar loses its layer controls (the Wash and Ink layers stay underneath, as they are). At its
   foot, a page turner: ‹, the page as a fraction (3 over 24, with a rule between: "3/24" doesn't fit the Nomad's 58 dp
   column at 100 pages), › and a **Notebook** button (back to the notebook). On the last page › becomes **+** and adds a
   page. Pages turn through `PageTurnDisplay`. A finger on the page stays the finger tool: no swipe to turn there. On
   the Nomad in landscape even the least sizes can't hold it: it folds into one button, the page over its count in
   captions, which opens ‹ › and Notebook beside it (as the layers folded there); Words is in the menu there, as it was.
   With Words (the fridge magnets) and the turner both in the column, it takes more than the layer buttons it replaced:
   the column's items are about a quarter under their preferred heights on the Manta, its grey and size faces 47 dp
   square (were 53), the Nomad's 27 (were 32), each still above its least.

The menu keeps **Rename notebook**, **Delete notebook** and Finger; New drawing and Open drawing go (the shelf does
both). Android's Back: from drawing to the notebook, from the notebook to the shelf.

A shelf page holds as many tiles as fit (Manta 4 × 2 in portrait, 6 × 2 in landscape; Nomad 4 × 2 and 5 × 1). A
tile's name takes up to three lines (four on the Nomad) and "24 pages · Oct 17" up to two; a rename that wouldn't fit
every tablet's tile and title is refused ("Name too long"), so no name is ever cut. A file whose old name is longer
shows with "…". A new notebook nobody has drawn in isn't saved, so it isn't on the shelf.

## Names

A new notebook's name is `2026-10-18 brisk-bean notebook` on disk (sorts by date), shown as **Oct 18 brisk-bean
notebook** (the year added when it isn't this year): the date, an adjective and a fruit or vegetable from the brushes'
word lists, "notebook". A clash adds a number. Rename takes any valid name; a name typed starting with the shown date
("Oct 18 harbour") keeps the ISO date on disk. A notebook is never renamed by what is drawn in it
(`DrawingNames.described` leaves names ending "notebook" alone). Pages have no names.

**The name prompt** (the owner, 2026-10-19: "prompt to edit title when creating one, include base (selected so if user
starts typing it overwrites) and single-tap suggestions below"; `NotebookNames`, `NamePrompt`). New notebook asks first:
a dialog titled "New notebook", its field holding the name it would get as shown ("Oct 19 brisk-bean notebook"), all of
it selected and the keyboard up, so typing replaces it; under it three other names of the same kind, one tap taking one
at once; **OK** and **Cancel** (nothing is made). Rename (the menu's, the name over the page, a tile's long press) is
the same prompt titled "Rename notebook", its own name selected, the suggestions dated as it is (today when it has no
date) so it sorts where it did. A name refused says so by the field ("Name taken", "Name too long") and the prompt
stays. A notebook other than the open one is renamed or deleted on its file alone, with its copies.

## A blank page at the end

The owner, 2026-10-19: "always add one blank to end of booklet ready for drawing (but can be deleted)" (`BlankPages`).
A page is **blank** with no marks, no magnets, no masking fluid and white paper.

- **When.** A notebook opened from the shelf whose last page isn't blank gets a blank page after it; so does a notebook
  whose last page gets its first mark while drawing (a shade or a magnet counts). › from the last drawn page lands on it.
- **Not a change.** It is put there without counting as an edit (`DrawingBook.appendUnchanged`): opening a notebook
  never rewrites its file or moves it up the shelf. The next save for any other change writes it: about 9 KB for a 1920
  × 2400 page (the bytes the + makes, packed once a size, at the files' level), so the pages before it are streamed
  byte for byte as ever (Loose pages too). While the notebook is unchanged, the shelf counts its file's pages.
- **Deleted**, it isn't put back at once: only at the next open, or the next first mark on a blank last page.
- **Deleting a blank page doesn't ask** (Delete page inside a notebook, the page menu): told by the page if it is
  decoded, else by its picture (any grey: not blank), else by its bytes (a blank page's, or decoded once to see).
- **The copies** leave out blank pages at the end (never the first page, nor a blank page between): no PNG, no PDF page.

## The file: page pictures inside the notebook

Every save writes, besides the pages, a **notebook block** (`BookPictures`) between the last page's data and the ZIP's
central directory:

```
"INB1"  version 1  cover (page index)  count
count × (CRC-32 of the page's entry bytes, picture length)
the pictures, in page order
block length  "INB1"
```

- **Older readers ignore it.** `BookCodec.read` before this (and upstream MonoPaint) reads with `ZipInputStream`, which
  stops at the first bytes that aren't a local header ("INB1" isn't `PK\3\4`), and checks the central directory's count,
  which lists only the manifest and the pages, as before. ZIP tools read through the central directory. Checked with the
  `BookCodec` from `580a326` (`scripts/test_document.sh`).
- **A picture** (`PagePicture`): the page's visible layers on white (never the masking fluid) and its fridge magnets
  over them (each tile averaged into the blocks it covers), each picture pixel the average of its 4 × 4 page pixels, 480 × 600 for a 1920 × 2400 page (600 on the long side), at the panel's 16 greys,
  two to a byte, deflated: about 11 KB for a sketch, about 30 ms to make on this Mac.
- **Valid only for its page:** a picture whose CRC doesn't match its page's entry is ignored, so a file another app
  rewrote shows freshly made pictures, never stale ones.
- **Within the old readers' size limit:** the whole file stays within 16 MB + 64 KB (what `BookCodec.read` accepts);
  pictures that don't fit are left out (made again when shown).
- **Random access:** the shelf reads the ZIP's end record, the block's trailer and the cover's picture only
  (`BookPictures.readCover`), never a page.
- In memory each page's picture travels with it (`DrawingBook.picture`, `offerPicture`), dropped once the page changes.
  A changed page's picture is made in the background: when the page is left (turned, the notebook view opened) from a
  snapshot (the layers are copy-on-write), and at a save for the page being drawn on. A page with no picture (an old
  file) is decoded in the background once, shown, and its picture saved with the next save.

## Pages read from the file (2026-10-18, second pass)

The dry run on the owner's library (43 drawings, 33 MB) made four notebooks: pages were held in memory, so a book was
capped at 16 MB of them, and heavy wash pages are about 1 MB packed. Now a page's packed bytes are a `PackedPage`:
in memory only once made or changed since its notebook was saved; otherwise the stored ZIP entry in the notebook's
file, read when needed and checked against its CRC-32 every time.

- **Limits:** 100 pages and 200 MB of them (`DrawingBook.MAX_PACKED_BYTES`). A notebook past 16 MB opens only in this
  version on (older readers held a book whole, up to 16 MB + 64 KB). "This notebook is full" or "This notebook has 100
  pages" is all a full one says.
- **Opening** reads the end record, the central directory, each entry's local header (in order, stored, named as the
  directory says, within the file), the manifest and each page's 12-byte header: no page but the shown one. A damaged
  byte in a page is refused when that page is read ("Page damaged"), and it fails alone; a damaged structure is refused
  at once.
- **Memory:** the decoded pages (at most two), and the pages changed since the last save; nothing else.
- **Saving** writes the new file beside the old as before (AtomicFile), streaming each unchanged page from the old file
  with its CRC (a damaged page stays as damaged, never re-checksummed), then, holding one lock from the rename until it
  is done, points every page it wrote into the new file and frees the changed pages' memory (`BookCodec.replaced`), so
  no read meets the old file once it is gone. An interrupted save leaves the old file whole and the book reading it.
- **The recovery file** (TSR3) holds only the pages not in the notebook's file, the rest named by length and CRC and
  found in it when recovered; TSR1 and TSR2 still read.
- Checked on a 60-page, 58 MB notebook (`NotebookPagesChecks`) and timed on the owner's library (below).

## Opening a notebook

`BookCodec.read` no longer inflates every page: the ZIP's own CRC-32 over each stored page (which `ZipInputStream`
checks while reading) still refuses a file with any damaged byte, and each page's header is checked (format, a valid
size; on TSB1 books the book's). A page is decoded when it is turned to; one that can't be is reported ("Could not open
page") and the notebook stays on the page it was on, every page kept as it was.

## PDF and PNG copies

The owner: "are they all saved as pdfs, the booklets, so they're easy to print or send once they're synced".

- **EXPORT/Inkwash/<notebook name>.pdf**, one PDF page per notebook page, at the page's real size at 300 dpi (1920 ×
  2400 px is 6.4 × 8 in), smooth greys as the PNG copies.
- **EXPORT/Inkwash/<notebook name>/001.png, 002.png …**, one grey PNG per page, are kept: they are the PDF's cache.
  Each carries its page's CRC-32 (a `tEXt` chunk), so only pages that changed are drawn again; a page that moved
  (after a delete) keeps its PNG under its new number. A page's fridge magnets are drawn over its PNG, so its PDF page,
  by `Magnets.stamp` with `WordsMode.exportTiles()`, as the PNG copy had them. The PDF is stitched from them without decoding anything: a PNG's
  image data is a PDF image as it is (`FlateDecode` with the PNG predictor), 8-bit grey. Written by Inkwash's own
  writers (`NotebookExport`, pure Java, timed on the desktop) rather than `android.graphics.pdf.PdfDocument`: that one
  can't run in the desktop checks and stores every image as RGB.
- Written in the background when a notebook or the app is left (the moments the PNG copy was written before), and for
  each notebook the migration writes. Renaming a notebook renames its PDF and folder; deleting it deletes both;
  deleting a page renumbers the PNGs and rewrites the PDF.

## Old drawings: the migration to notebooks

Runs once, at the first launch of this version, before the shelf shows (with "Making notebooks…" and a count), on the
store's IO queue, so no save can run beside it. `NotebookMigration` (pure Java, checked on the desktop on a copy of a
realistic library).

1. **What it gathers.** Every `.mpaint` in the library and its folders (an atomic save's `.bak` counts as the drawing:
   it is then the last good copy), except `Originals/` and the recovery file. A file that already holds a notebook block
   is a notebook and is left alone. Anything else (brush files, "Inkwash import.txt", `*.inkwash.txt`, the brush backup)
   stays exactly where it is.
2. **One-page drawings** become the pages of **Loose pages**, oldest first: by the date their name starts with
   ("2026-10-14 dapper-fig"; a drawing without one, the day it last changed), then by when each last changed, then by
   name. Page 1 the oldest, the last page the newest. Only past 100 pages or 200 MB of pages: **Loose pages 2**, 3 … in
   the same order.
3. **One size per notebook, no pixel lost.** A notebook takes the size most of its drawings have (the newest's on a
   tie). A smaller drawing is padded with blank paper on its right and bottom. A larger one loses only blank paper:
   extra columns come off its left edge (the old bar's strip) as far as it is blank, then its right; extra rows off its
   top, then its bottom. A drawing that would lose a mark keeps its own size, in its place among the others (a book's
   pages may differ in size). A drawing that fits as it is, or keeps its own size, goes in byte for byte.
4. **Multi-page files** stay notebooks where they are: each is written anew beside its place (`name.mpaint.writing`),
   its pages byte for byte with their pictures, and read back; its original then moves into `Originals/` like any other,
   and the new file takes its place. One with a name the app refuses (a sign other than letters, numbers, space, - and _)
   gets one it takes ("Weird (copy)" becomes "Weird copy").
5. **The unsaved working drawing** (a recovery newer than its file) goes in with its newest pages; its file is moved
   to `Originals/` untouched like any other. A recovery with no name goes in as a page of its own.
6. **Writing and checking.** Each notebook is written to `name.mpaint.writing`, synced, read back as the app opens it
   (every page's CRC-32 and header, `BookCodec.readPages`), each page compared byte for byte with what was meant to go
   in, and its pictures found; then renamed into place. Only when every notebook is written and read back are the
   originals moved, untouched (a rename, with their `.bak` and `.new`), into `Originals/<their folder>/`; a name taken
   there gets a number, and a file is never moved over another. A file that doesn't read as a drawing (damaged, an
   unfinished `.new` alone) is in no notebook and moves to Originals as it is, listed as "unreadable" in the plan.
7. **Once, and safe to interrupt.** A plan (`Originals/Notebooks migration.txt`, plain text: each notebook and its
   pages, by original, page and how far each was moved) is written before anything else, whole, synced and renamed into
   place; each notebook written, each move (its destination recorded before the files move), "moved all" and "done" are
   recorded as they happen. A later launch with the plan unfinished goes on from it: notebooks not recorded as written
   are written again from the originals (still in place: nothing moves before every notebook is written), moves not yet
   done are done under their recorded names, a multi-page notebook whose file is cut short is written again from its
   original in Originals. A file holding a notebook block is never moved. With "done" in the plan, or a library with
   nothing to gather, it never runs again; the app's preferences note it too. `NotebookMigrationChecks` interrupts a
   realistic library before each of the run's 38 steps, cuts every half-written file short, runs again and
   compares every file with an uninterrupted run's; at every stop each original is whole in the library or in Originals.
8. Afterwards the recovery file is cleared: its drawing now lives in a notebook. The old flat PNG copies in EXPORT stay.

## Numbers (this Mac; the Manta is about 20 times slower)

`scripts/bench_notebooks.sh`, 1920 × 2400 sketch pages drawn with the app's own brushes (`NotebookFixture`):

| | This Mac | Manta, estimated |
|---|---|---|
| A page's picture made (11 KB) | 30 ms | 0.6 s, in the background |
| A picture shown | 3 ms | 60 ms |
| Open a 24-page notebook, before (every page inflated) | 812 ms | 16 s |
| Open a 24-page notebook, now | 56 ms | 1.1 s |
| A page turned to, then decoded (Draw) | 31 ms | 0.6 s |
| The shelf's 20 covers, before (the Open dialog decoded page 1 of each) | 1143 ms | 23 s |
| The shelf's 20 covers, now (from the files' ends) | 33 ms | 0.7 s |
| PDF and PNG copies of 24 pages, the first time | 2.6 s | 50 s, in the background |
| The same, one page changed | 114 ms | 2.3 s |
| The 24-page PDF; its PNGs | 3.0 MB; 3.0 MB | |
| The migration of 24 one-page drawings | 1.6 s | 32 s, once |

### The owner's library, dry run (a copy of the 2026-10-18 backup, 43 drawings, 33 MB)

All 43 into one **Loose pages** (1785 × 2560): 10-13 soggy-teapot, wobbly-kettle, fancy-lobster, fizzy-biscuit,
peckish-lobster; 10-14 noodly-pangolin, zesty-ferret, soggy-octopus, pompous-melon, zealous-kale; the eight of 10-15,
the six of 10-16, the six of 10-17, the thirteen of 10-18 (vivid-yam last). Seven of 10-13 and 10-14 lose a blank strip
of 60 to 135 columns on their left (the old bar's); peckish-lobster, noodly-pangolin and soggy-octopus (1920 × 2455,
marks in that strip) keep their own size. Every original is in Originals, byte for byte. Within a day the order is by
when each file last changed, which the backup's copy reset.

| On this Mac (Manta about 20 ×) | |
|---|---|
| The migration | 6.1 s (about 2 min, once) |
| Loose pages on disk | 33.8 MB |
| Open | 31 ms, 21 MB held (0.6 s) |
| A page turned to | 42 ms on average, 87 at most (0.8 s, 1.7 s); 31 MB held at most |
| Save with one page changed | 13 ms here, from the disk cache (the Manta writes 34 MB: a second or two), 26 MB held after |
| Every page held in memory instead, as before | 43 MB more |

## Later

- **Make this page transparent, to combine two pages** (the owner's idea): a page shown over the one under it.
- **Move a page** (reorder): left out for now, fewer buttons.
- The layer controls in the bar, if they come back.
