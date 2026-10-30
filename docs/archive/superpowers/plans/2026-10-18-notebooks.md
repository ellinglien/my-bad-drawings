# Notebooks: plan

Spec: `docs/archive/superpowers/specs/2026-10-18-notebooks-design.md`. Branch `inkwash-books`. Each step a commit with its
desktop checks (`scripts/test_document.sh`, which runs `scripts/test_inkwash.sh`) and, from step 6, `./gradlew
:app:assembleRelease`.

1. **Page pictures** (`PagePicture`, pure): make, encode, decode, show. Timed.
2. **The notebook block** (`BookPictures`; `BookCodec` writes and reads it; `DrawingBook` keeps a picture and the cover
   with each page) and **opening without inflating** every page. `NotebookFileChecks`: written and read, stale pictures
   ignored, the size limit, random access to the cover, damage still refused; the `580a326` reader opens a notebook
   (`OlderReaderCheck`). Timed before and after: a 24-page notebook's open, 20 notebooks' covers.
3. **Names** (`DrawingNames.notebook`, `shown`, `stored`). `DrawingNamesChecks`.
4. **Migration** (`NotebookMigration`, pure Java). `NotebookMigrationChecks` on a copy of a realistic library: mixed
   sizes, the old bar's strip, multi-page files, `.bak`/`.new`, folders, brush and import files; originals byte for byte
   in `Originals/`; interrupted at every step and run again; idempotent; the split at 100 pages and at 16 MB.
5. **PDF and PNG copies** (`NotebookExport`, pure Java). `NotebookExportChecks`: the PNGs read by `javax.imageio`, the
   PDF's structure, only changed pages drawn, renumbering. Timed for 24 pages.
6. **Layouts** (`NotebookLayout`, pure; `DrawLayout`'s page turner in place of the layers). `NotebookLayoutChecks`,
   `DrawLayoutChecks`: both tablets, both turns, nothing clipped or scrolled.
7. **The screens** (`NotebookShelf`, `NotebookPages`, `Notebooks`; `PageTurner` in `InkWashBar`) and `PaintActivity`'s
   hooks; the migration on `DocumentStore`'s queue.
8. CHANGELOG, INTEGRATION, the numbers.
