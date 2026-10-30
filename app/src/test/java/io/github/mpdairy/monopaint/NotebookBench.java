package io.github.mpdairy.monopaint;

import java.io.*;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Notebook timings on this Mac (scripts/bench_notebooks.sh; the Manta is about 20 times slower): a page's picture made,
 * a 24-page notebook opened, 20 notebooks' covers read for the shelf. {@code make <dir>} writes the files
 * (1920 × 2400 sketch pages, {@link NotebookFixture}); {@code time <dir>} times them with this version's classes.
 * {@link NotebookBenchBefore} times the same files with the classes from before notebooks.
 */
public final class NotebookBench {
    public static void main(String[] args) throws Exception {
        File dir = new File(args[1]);
        if (args[0].equals("make")) { make(dir); return; }
        Assets.install(Assets.folder(Assets.FOLDER));
        File notebook = new File(dir, "24 pages.mpaint");
        double open = best(() -> {
            try (InputStream in = new FileInputStream(notebook)) { BookCodec.read(in); }
        });
        double turn = best(() -> {
            DrawingBook book; try (InputStream in = new FileInputStream(notebook)) { book = BookCodec.read(in); }
            book.select(1);
        }) - open;
        // Tracing paper's plane on a page turn: the page before still decoded, the page after from its bytes (spec target on the Manta: 250 ms).
        DrawingBook traced; try (InputStream in = new FileInputStream(notebook)) { traced = BookCodec.read(in); }
        traced.select(1); traced.select(2);
        traced.setTracing(traced.tracing().withPaper(TracingPaper.TRACING));
        double tracing = best(() -> GhostPlane.build(traced.width, traced.height, traced.ghostSources()));
        System.out.printf(Locale.ROOT, "tracing paper: the Tracing plane %.0f ms%n", tracing);
        // Glass: every other page from its bytes, one at a time into the one plane (spec target on the Manta: 1 s for 20
        // pages); 20 and 100 pages (the notebook's pages over again) decide whether a per-page flat cache is needed.
        traced.setTracing(traced.tracing().withPaper(TracingPaper.GLASS));
        java.util.List<GhostPlane.Source> sources = traced.ghostSources(), packed = new java.util.ArrayList<>();
        for (int i = 0; packed.size() < 99; i++) { GhostPlane.Source s = sources.get(i % sources.size()); if (s.packed != null) packed.add(s); }
        double glass4 = best(() -> GhostPlane.build(traced.width, traced.height, packed.subList(0, TracingPaper.GLASS_PAGES)));
        double decode4 = best(() -> { for (GhostPlane.Source s : packed.subList(0, TracingPaper.GLASS_PAGES)) DocumentCodec.read(new java.io.ByteArrayInputStream(s.packed.bytes())).layerSnapshot().release(); });
        System.out.printf(Locale.ROOT, "tracing paper: the Glass plane, %d pages %.0f ms (decoding them %.0f ms)%n", TracingPaper.GLASS_PAGES, glass4, decode4);
        double glass20 = best(() -> GhostPlane.build(traced.width, traced.height, packed.subList(0, 19)));
        double glass100 = best(() -> GhostPlane.build(traced.width, traced.height, packed));
        System.out.printf(Locale.ROOT, "tracing paper: the Glass plane, 20 pages %.0f ms, 100 pages %.0f ms%n", glass20, glass100);
        // The shelf: each notebook's front cover, its pattern and label, its first page glued on (Inkwash's covers), at the
        // size the shelf showed page pictures (half the picture's): with the patterns made (kept, as the app keeps them), and
        // each made for the first time (a name not seen).
        int tw = PagePicture.size(1920, 2400)[0] / 2, th = PagePicture.size(1920, 2400)[1] / 2;
        int[] run = {0};
        Work tiles = () -> {
            for (int i = 0; i < 20; i++) {
                BookPictures.Cover cover = BookPictures.readCover(new File(dir, "shelf " + i + ".mpaint"));
                String name = "shelf " + i + (run[0] > 0 ? " " + run[0] : "");
                NotebookCovers.shown(true, cover.design, name, tw, th, cover.picture == null ? null : PagePicture.decode(cover.picture), cover.front == null ? null : PagePicture.decode(cover.front));
            }
        };
        double shelf = best(tiles);
        double coldShelf = Double.MAX_VALUE;
        for (int k = 1; k <= 3; k++) { run[0] = k; long t0 = System.nanoTime(); tiles.run(); coldShelf = Math.min(coldShelf, (System.nanoTime() - t0) / 1e6); }
        // The front cover turned to from the first page: its paper (pattern, label, page 2 glued on) made, the pattern made ahead or not.
        double[] front = new double[2];
        for (int k = 0; k < 2; k++) {
            String name = "24 pages " + k;
            if (k == 1) CoverPattern.cached(CoverDesign.DEFAULT, name, 1920, 2400, false);
            DrawingBook book; try (InputStream in = new FileInputStream(notebook)) { book = BookCodec.read(in); }
            book.name(name); book.setCover(1);
            long t0 = System.nanoTime(); book.select(DrawingBook.FRONT); front[k] = (System.nanoTime() - t0) / 1e6;
        }
        double marble = best(() -> CoverPattern.tones(CoverDesign.DEFAULT, "marble", 1920, 2400, false)), tileMarble = best(() -> CoverPattern.tones(CoverDesign.DEFAULT, "marble", 375, 469, false));
        // The shelf at a later launch: nothing in memory, the patterns made again from their sheets.
        run[0] = 10; tiles.run();
        double keptShelf = best(() -> { CoverPattern.forget(); tiles.run(); });
        ToneDocument page;
        try (InputStream in = new FileInputStream(notebook)) { page = BookCodec.read(in).current(); }
        ToneDocument.Snapshot snapshot = page.layerSnapshot();
        byte[][] made = new byte[1][];
        double picture = best(() -> made[0] = PagePicture.of(snapshot).encode());
        double flip = best(() -> PagePicture.decode(made[0]).pixels(480, 600));
        // The PDF and PNG copies of the 24 pages: all drawn, then one page changed.
        java.util.List<PackedPage> pages = NotebookMigration.Book.of(BookCodec.readPages(new FileInputStream(notebook))).pages;
        File export = new File(dir, "EXPORT");
        long t = System.nanoTime();
        NotebookExport.Done all = NotebookExport.update(export, "24 pages", pages);
        double exportAll = (System.nanoTime() - t) / 1e6;
        java.util.List<PackedPage> changed = new java.util.ArrayList<>(pages);
        ToneDocument edited = DocumentCodec.read(new ByteArrayInputStream(pages.get(3).bytes()));
        mark(edited);
        changed.set(3, PackedPage.of(DrawingBook.encode(edited)));
        t = System.nanoTime();
        NotebookExport.Done one = NotebookExport.update(export, "24 pages", changed);
        double exportOne = (System.nanoTime() - t) / 1e6;
        long pngBytes = 0; for (File f : new File(export, "24 pages").listFiles()) pngBytes += f.length();
        System.out.printf(Locale.ROOT, "copies: 24 pages drawn %.0f ms (%d drawn), one changed %.0f ms (%d drawn); PDF %d KB, PNGs %d KB%n",
                exportAll, all.drawn, exportOne, one.drawn, all.pdf.length() / 1024, pngBytes / 1024);
        // The migration of 24 one-page drawings of this size.
        File library = new File(dir, "library");
        library.mkdirs();
        for (int i = 0; i < 24; i++) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            java.util.List<PackedPage> one1 = java.util.Collections.singletonList(pages.get(i));
            BookCodec.write(out, 1920, 2400, one1, 0, new int[1], 0, java.util.Collections.singletonList(null));
            byte[] file = out.toByteArray();
            Files.write(new File(library, "d" + i + ".mpaint").toPath(), NotebookFileChecks.withoutBlock(file));
        }
        t = System.nanoTime();
        new NotebookMigration(library).run(null, null);
        System.out.printf(Locale.ROOT, "migration: 24 one-page drawings into Loose pages %.0f ms%n", (System.nanoTime() - t) / 1e6);
        System.out.printf(Locale.ROOT, "after: open 24 pages %.0f ms (a page turned to then decodes in %.0f ms); shelf, 20 covers %.0f ms; picture made %.1f ms (%d bytes); a picture shown %.1f ms; file %d KB%n",
                open, turn, shelf, picture, made[0].length, flip, notebook.length() / 1024);
        System.out.printf(Locale.ROOT, "covers: shelf, 20 covers, patterns made first %.0f ms, after a new launch %.0f ms; a page-sized Classic Black Marble %.0f ms, a 375 px tile's %.1f ms; the front cover turned to %.0f ms (pattern made ahead %.0f ms)%n",
                coldShelf, keptShelf, marble, tileMarble, front[0], front[1]);
    }

    static void mark(ToneDocument page) {
        page.begin(); for (int x = 100; x < 400; x++) page.setTone(x, 300, 0); page.finish();
    }
    static void make(File dir) throws IOException {
        dir.mkdirs();
        DrawingBook book = null;
        for (int i = 0; i < 24; i++) {
            ToneDocument page = NotebookFixture.sketch(1920, 2400, 100 + i);
            if (book == null) book = new DrawingBook(page);
            else { book.addPage(); copy(page, book.current()); }
            book.offerPicture(book.pictureKey(book.index()), PagePicture.of(book.current().layerSnapshot()).encode());
            // Each shelf notebook's first page on its cover (Inkwash's covers): the shelf's most work.
            if (i < 20) { DrawingBook shelf = new DrawingBook(page); shelf.setCover(0); write(new File(dir, "shelf " + i + ".mpaint"), shelf); }
        }
        book.select(0);
        write(new File(dir, "24 pages.mpaint"), book);
    }
    /** {@code from}'s layers onto {@code to}, both the same size. */
    static void copy(ToneDocument from, ToneDocument to) {
        ToneDocument.Snapshot s = from.layerSnapshot();
        while (to.layerCount() < s.layers.size()) to.addLayer();
        for (int n = 0; n < s.layers.size(); n++) {
            to.selectLayer(n); to.begin();
            int[] row = new int[from.width];
            for (int y = 0; y < from.height; y++) {
                for (int x = 0; x < from.width; x++) { int i = y * from.width + x; row[x] = (s.layers.get(n).alpha[i] & 255) == 0 ? -1 : s.layers.get(n).tones[i] & 255; }
                to.setTones(0, y, from.width, 1, row);
            }
            to.finish();
        }
    }
    static void write(File file, DrawingBook book) throws IOException {
        DrawingBook.Snapshot snapshot = book.snapshot();
        if (snapshot.picture(snapshot.index) == null) snapshot.setPicture(snapshot.index, PagePicture.of(snapshot.activePage).encode());
        try (OutputStream out = new FileOutputStream(file)) { BookCodec.write(out, snapshot); }
    }

    interface Work { void run() throws Exception; }
    /** The best of five runs, after two to warm up, ms. */
    static double best(Work work) throws Exception {
        for (int i = 0; i < 2; i++) work.run();
        double best = Double.MAX_VALUE;
        for (int i = 0; i < 5; i++) { long t = System.nanoTime(); work.run(); best = Math.min(best, (System.nanoTime() - t) / 1e6); }
        return best;
    }
}
