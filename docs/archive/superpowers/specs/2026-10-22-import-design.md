# Import Image, Import PDF, Export Now: design

Status: approved by the owner (2026-10-22, "yes" to the proposal); built on branch `import-images-pdf`. Three rows in
the drawing screen's file menu (⋮), after Guides in PDF: **Import Image**, **Import PDF**, **Export Now**.

**2026-10-26, reversed in part at the owner's request (branch `erase-image`):** Place lays the picture on the layer
drawn on, as any mark, not on a new layer above. On a new layer the eraser reached only the picture, never the marks
drawn before it ("eraser then could not erase the original line, just the image"). Its Undo is the ordinary one (Redo
has it); `LayerEdits.added` and the "full" notes are gone. The rest stands.

## Open questions

1. **49 PDF pages, not 100.** A layer is a page (`PageStacks`), and a PDF page is a stack of two (the PDF's page, a
   clear layer), so a notebook's 100 holds 49 of them and the blank page after the last. A 49-page import fills the
   notebook: no more layers or pages there. Fewer (40, leaving room for layers) or one layer per PDF page (drawn
   over by adding a layer) are the alternatives.
2. **The PDF's page is locked by its clock**, as a timed page at zero (`PageClock.lock`): no marks, no erasing, no Undo
   there, Merge Down onto it refused, "Page locked" said and a padlock by the page number while it is drawn on. It can
   still be deleted (a layer's Delete), so the PDF can be taken off a page. No new lock concept was added; one of its
   own would let the padlock say something other than a timed page's.
3. **Undo of Import Image has no Redo** (as Delete and Merge Down's layer Undo). On a full stack or in a full
   notebook the picture goes on the layer drawn on as one ordinary Undo step, which Redo has.
4. **Pinch to resize** is built (two fingers, the point between them staying put) but was not tried on the tablet:
   `adb` can't make two fingers.

## What the owner sees

- **Import Image**: Android's file picker for a picture. The picture shows over the page, fitted inside what shows of
  it and centred, a square handle at its lower right, and a bar along the page's foot: **Cancel** and **Place**. Until
  one is tapped every touch is the picture's, the pen's or a finger's: a drag moves it, a drag from the handle resizes
  it, two fingers pinch it, its shape always kept, never smaller than about 4 mm or lost off the page. While it moves
  only its outline and handle follow (e-ink, as a magnet moves); at lift it shows in its new place. Place lays it on a
  **new layer above** the one drawn on, drawn on, as one Undo step that takes the layer away with it. With the stack's
  four layers, "Layers full, on this layer"; with the notebook's 100 pages, "Notebook full, on this layer"; then it is
  laid on the layer drawn on (a cover: on the cover). A timed page locked takes none ("Page locked"). No Erasing,
  Lock Settings and Brushes don't block it (a picture is drawing); under No Erasing Undo is off as ever.
- **Import PDF**: the picker for a PDF. A box says "Importing 3 / 12" with **Cancel**; the notebook drawn in is saved,
  and a new notebook named after the PDF opens, in a cover design by chance, on its first page's clear layer. More than
  49 pages, or more than fit the 200 MB: the first that fit, and the note "First 49 pages". Cancelled, failed or the
  app stopped part way: no notebook at all. A PDF that won't open (damaged, password): "Can't read it".
- **Export Now**: the notebook's PDF and PNG copies written now, as leaving it writes them, whatever changed; done,
  "PDF in EXPORT/Inkwash" (the copies' folder: `PngExport.SUPERNOTE_FOLDER`); failed, "Export failed".

## Mechanism

- **Greys** (`PictureImport.greys`): a pixel's brightness over white (Rec. 601 luma, its see-through share white),
  smooth 0 to 255 as every mark's tone is held; the panel's dots and the copies' greys are made from it later, as from
  any mark. Brightness 248 and over is paper: left clear, so the layers under it and the guides show. The own Supernote
  templates read pictures the same way (`NotebookTemplate.tones` now calls `PictureImport.grey`).
- **Laying** (`PictureImport.lay`): one gesture, `setTones`, as ink is laid (transparent: a grey's darkness over
  whatever is under it), only where darker than the layer already is there, so a picture on a layer with marks never
  lightens them. One Undo step. A new layer above (`DrawingBook.addLayer`) and the picture on it are taken back together
  by `LayerEdits.added`, as Delete and Merge Down are: while the layer is as it was left.
- **Placing** (`PictureImport.Frame`, `PicturePlacer`): page pixels; the frame kept at least 64 px on the page, its
  shorter side at least 48 px, its longer at most three pages. The picture is decoded no larger than fits the page
  (`ImageDecoder`, upright as the camera took it), drawn over the raster a band of 128 rows at a time and laid the
  same way, so neither needs a whole page of pixels twice.
- **PDF** (`PdfImport`): `PdfRenderer` draws each page at the notebook's page size (the notebook drawn in's), fitted and
  centred on white; its greys laid on a blank page whose clock is locked (`PictureImport.pdfPage`); packed and written
  into one file in the app's cache as it goes, so the pages never all sit in memory. Once all are done the store
  (`DocumentStore.create`, on its own thread after any queued saves) writes the notebook's file whole from that file
  (`PictureImport.writeBook`: each PDF page with the blank page's bytes stacked on it, `TracingPaper.LINKED`), named
  after the PDF ("name 2" when taken), atomically, and the cache file goes.
- **Export Now** (`exportCopy(true)`): the copies' worker (`AutoExport`), told to say when it is done.

Checks: `PictureImportChecks` (greys, laying and its Undo, the new layer's Undo, fitting, the frame, the cap, a PDF's
notebook written and read back).
