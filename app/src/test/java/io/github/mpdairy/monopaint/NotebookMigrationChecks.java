package io.github.mpdairy.monopaint;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * The migration to notebooks (docs/archive/superpowers/specs/2026-10-18-notebooks-design.md, "Old drawings"), on a copy of a
 * realistic library: one-page drawings of mixed sizes (the old bar's blank strip, a smaller page, one with marks where
 * it would be cut), an interrupted save's .bak and an unfinished .new, an older TSM file, a folder, multi-page files
 * (one with a name the app refuses), a damaged file, a notebook, a stale recovery, brush and import files. Originals
 * end up byte for byte in Originals, everything else where it was; it runs once; interrupted before any step and run
 * again, it ends as an uninterrupted run does; the unsaved working drawing goes in with its newest pages; Loose pages
 * split at the most pages and at 16 MB.
 */
public final class NotebookMigrationChecks {
    static final int W = 60, H = 80;
    static final long DAY = 86_400_000L, T0 = 1_760_000_000_000L;

    public static void main(String[] args) throws Exception {
        File base = Files.createTempDirectory("migration").toFile();
        try {
            library(base);
            interrupted(base);
            working(base);
            limits(base);
            nothing(base);
        order(base);
        } finally { delete(base); }
        System.out.println("PASS: the migration to notebooks gathers one-page drawings into Loose pages, oldest first, one size each"
                + " (smaller padded right and bottom, larger losing only blank paper: the old bar's strip off the left, then the right;"
                + " one that would lose a mark at its own size), reading an interrupted save's .bak and older TSM files, from folders too;"
                + " multi-page files stay notebooks at their place, pages byte for byte (a name the app refuses made one it takes); every"
                + " original, .bak and .new moves byte for byte into Originals only after the notebooks are written and read back, a"
                + " damaged one too; notebooks, brush and import files and the old recovery stay as they were; the plan says what went where;"
                + " it runs once; interrupted before each of its steps (and its half-written files cut short), run again it ends as an"
                + " uninterrupted run does; the unsaved working drawing goes in with its newest pages, its file moved as it was; Loose"
                + " pages ordered by the date in the name, then when changed, then by name (undated by the day it changed); one notebook past the old 16 MB, split at the most pages and near the size limit; a library with nothing to gather is left alone");
    }

    // The library

    /** A page {@code w × h} with an ink mark (8 × 8, {@code tone}) at ({@code x}, {@code y}). */
    static ToneDocument page(int w, int h, int x, int y, int tone) {
        ToneDocument page = InkWashLayers.blank(w, h);
        page.selectLayer(InkWashLayers.INK);
        page.begin();
        for (int dx = 0; dx < 8; dx++) for (int dy = 0; dy < 8; dy++) page.setTone(x + dx, y + dy, tone);
        page.finish();
        return page;
    }
    /** An old drawing's file: a book without a notebook block, as Inkwash wrote before notebooks. */
    static byte[] old(ToneDocument... pages) throws IOException {
        DrawingBook book = new DrawingBook(pages[0]);
        for (int i = 1; i < pages.length; i++) { book.addPage(); NotebookBench.copy(pages[i], book.current()); }
        book.select(0);
        ByteArrayOutputStream out = new ByteArrayOutputStream(); BookCodec.write(out, book.snapshot());
        return NotebookFileChecks.withoutBlock(out.toByteArray());
    }
    static void put(File root, String path, byte[] bytes, long modified) throws IOException {
        File file = new File(root, path); file.getParentFile().mkdirs();
        Files.write(file.toPath(), bytes); file.setLastModified(modified);
    }

    /** The realistic library; each one-page drawing's mark, by name, for checking its page later. */
    static void build(File root) throws IOException {
        put(root, "2026-10-13 dapper-fig.mpaint", old(page(W, H, 10, 10, 20)), T0);
        // An interrupted save: the .bak is the last good copy, the base half written.
        put(root, "2026-10-14 brisk-bean.mpaint.bak", old(page(W, H, 12, 12, 40)), T0 + DAY);
        put(root, "2026-10-14 brisk-bean.mpaint", "half written".getBytes(), T0 + DAY);
        // An unfinished write alone.
        put(root, "2026-10-14 lost.mpaint.new", "unfinished".getBytes(), T0 + DAY);
        ByteArrayOutputStream tsm = new ByteArrayOutputStream(); DocumentCodec.write(tsm, page(W, H, 14, 14, 60).layerSnapshot());
        put(root, "2026-10-15 older format.mpaint", tsm.toByteArray(), T0 + 2 * DAY);
        // The old bar's strip: 6 blank columns on the left.
        put(root, "2026-10-16 wide.mpaint", old(page(W + 6, H, 6 + 16, 16, 80)), T0 + 3 * DAY);
        // Wider with marks at both edges: it can't lose 6 columns.
        ToneDocument marked = page(W + 6, H, 0, 20, 100);
        marked.begin(); marked.setTone(W + 5, 30, 0); marked.finish();
        put(root, "2026-10-17 wide marked.mpaint", old(marked), T0 + 4 * DAY);
        put(root, "2026-10-17 small.mpaint", old(page(W - 10, H - 10, 18, 18, 120)), T0 + 5 * DAY);
        put(root, "Sketches/2026-10-18 tidy-kiwi.mpaint", old(page(W, H, 20, 20, 140)), T0 + 6 * DAY);
        put(root, "Three pages.mpaint", old(page(W, H, 1, 1, 10), page(W, H, 2, 2, 30), page(W, H, 3, 3, 50)), T0 + 7 * DAY);
        put(root, "Weird (copy).mpaint", old(page(W, H, 4, 4, 70), page(W, H, 5, 5, 90)), T0 + 8 * DAY);
        put(root, "broken.mpaint", "PK not a drawing".getBytes(), T0 + 9 * DAY);
        // Already a notebook (it has a block): left alone.
        DrawingBook notebook = new DrawingBook(page(W, H, 2, 2, 0));
        ByteArrayOutputStream nb = new ByteArrayOutputStream(); BookCodec.write(nb, notebook.snapshot());
        put(root, "My notebook.mpaint", nb.toByteArray(), T0 + 10 * DAY);
        put(root, "_recovery.mpaint", old(page(W, H, 3, 3, 3)), T0);
        put(root, "Inkwash import.txt", "M1abc\n".getBytes(), T0);
        put(root, "friend.inkwash.txt", "M1def\n".getBytes(), T0);
        put(root, "Inkwash import (added).txt", "M1xyz\n".getBytes(), T0);
        put(root, "Inkwash brushes.txt", "saved brushes".getBytes(), T0);
    }
    /** Every file under {@code root}, by its path there, and its bytes. */
    static TreeMap<String, byte[]> files(File root) throws IOException {
        TreeMap<String, byte[]> all = new TreeMap<>();
        walk(root, "", all);
        return all;
    }
    private static void walk(File folder, String at, TreeMap<String, byte[]> all) throws IOException {
        File[] list = folder.listFiles(); if (list == null) return;
        for (File f : list) {
            String path = at.isEmpty() ? f.getName() : at + "/" + f.getName();
            if (f.isDirectory()) walk(f, path, all); else all.put(path, Files.readAllBytes(f.toPath()));
        }
    }

    static void library(File base) throws Exception {
        File root = new File(base, "library"); build(root);
        TreeMap<String, byte[]> before = files(root);
        NotebookMigration.Result result = new NotebookMigration(root).run(null, null);
        TreeMap<String, byte[]> after = files(root);
        check(result.ran && !result.working, "It ran");
        expected(before, after);
        check(new NotebookMigration(root).finished(), "Finished");
        // Once: running again changes nothing.
        NotebookMigration.Result again = new NotebookMigration(root).run(null, null);
        check(!again.ran && same(after, files(root)), "It runs once: a second run changes nothing");
        // A new one-page drawing afterwards is a notebook like any other, never gathered.
        put(root, "2026-10-19 later.mpaint", old(page(W, H, 1, 1, 1)), T0 + 20 * DAY);
        check(!new NotebookMigration(root).run(null, null).ran && new File(root, "2026-10-19 later.mpaint").isFile(), "Never again, even with a new old drawing");
    }

    /** {@code after} is what the migration should make of {@code before}. */
    static void expected(TreeMap<String, byte[]> before, TreeMap<String, byte[]> after) throws Exception {
        String[] moved = {"2026-10-13 dapper-fig.mpaint", "2026-10-14 brisk-bean.mpaint.bak", "2026-10-14 brisk-bean.mpaint",
                "2026-10-14 lost.mpaint.new", "2026-10-15 older format.mpaint", "2026-10-16 wide.mpaint", "2026-10-17 wide marked.mpaint",
                "2026-10-17 small.mpaint", "Sketches/2026-10-18 tidy-kiwi.mpaint", "Three pages.mpaint", "Weird (copy).mpaint", "broken.mpaint"};
        for (String path : moved) {
            check(Arrays.equals(before.get(path), after.get("Originals/" + path)), "Moved byte for byte into Originals: " + path);
            if (!path.equals("Three pages.mpaint")) check(!after.containsKey(path), "No longer in the library: " + path);
        }
        for (String path : new String[]{"My notebook.mpaint", "_recovery.mpaint", "Inkwash import.txt", "friend.inkwash.txt",
                "Inkwash import (added).txt", "Inkwash brushes.txt"})
            check(Arrays.equals(before.get(path), after.get(path)), "Left exactly as it was: " + path);
        // Loose pages: oldest first, at the common size.
        DrawingBook loose = BookCodec.read(new ByteArrayInputStream(after.get("Loose pages.mpaint")));
        int[][] marks = {{10, 10, 20}, {12, 12, 40}, {14, 14, 60}, {16, 16, 80}, {0, 0, 0}, {18, 18, 120}, {20, 20, 140}};
        check(loose.count() == marks.length && loose.width == W && loose.height == H, "Loose pages holds all seven, at " + W + " × " + H + ": " + loose.count());
        for (int i = 0; i < marks.length; i++) {
            if (i == 4) continue;
            loose.select(i);
            ToneDocument p = loose.current();
            check(p.width == W && p.height == H && p.compositeTone(marks[i][0] + 3, marks[i][1] + 3) == marks[i][2] && p.compositeTone(marks[i][0] - 1, marks[i][1] + 3) == 255,
                    "Loose pages, page " + (i + 1) + ": its drawing, oldest first, in place (the wide one without its blank strip)");
            check(loose.picture(i) != null, "Each page has its picture");
        }
        loose.select(5);
        check(loose.current().compositeTone(W - 1, H - 1) == 255, "The smaller page padded with blank paper");
        byte[] dapper = BookCodec.readPages(new ByteArrayInputStream(before.get("2026-10-13 dapper-fig.mpaint"))).pages.get(0).bytes();
        check(Arrays.equals(BookCodec.readPages(new ByteArrayInputStream(after.get("Loose pages.mpaint"))).pages.get(0).bytes(), dapper), "A page that fits goes in byte for byte");
        byte[] tsm = before.get("2026-10-15 older format.mpaint");
        check(Arrays.equals(BookCodec.readPages(new ByteArrayInputStream(after.get("Loose pages.mpaint"))).pages.get(2).bytes(), tsm), "An older TSM file's page byte for byte");
        // The one that would lose a mark: in its place in Loose pages, at its own size, byte for byte.
        loose.select(4);
        check(loose.current().width == W + 6 && loose.current().compositeTone(3, 23) == 100 && loose.current().compositeTone(W + 5, 30) == 0 && loose.picture(4) != null,
                "A drawing that would lose a mark keeps its own size in Loose pages, every mark kept");
        check(Arrays.equals(BookCodec.readPages(new ByteArrayInputStream(after.get("Loose pages.mpaint"))).pages.get(4).bytes(),
                BookCodec.readPages(new ByteArrayInputStream(before.get("2026-10-17 wide marked.mpaint"))).pages.get(0).bytes()), "byte for byte");
        // Multi-page files: notebooks at their place, pages byte for byte.
        BookCodec.Pages three = BookCodec.readPages(new ByteArrayInputStream(after.get("Three pages.mpaint")));
        BookCodec.Pages was = BookCodec.readPages(new ByteArrayInputStream(before.get("Three pages.mpaint")));
        check(three.pages.size() == 3 && three.found != null, "A multi-page file is a notebook where it was, with its pictures");
        for (int i = 0; i < 3; i++) check(Arrays.equals(three.pages.get(i).bytes(), was.pages.get(i).bytes()), "Its page " + (i + 1) + " byte for byte");
        check(after.containsKey("Weird copy.mpaint") && BookCodec.readPages(new ByteArrayInputStream(after.get("Weird copy.mpaint"))).pages.size() == 2,
                "A multi-page file with a name the app refuses: a notebook under one it takes");
        String plan = new String(after.get("Originals/Notebooks migration.txt"), "UTF-8");
        check(plan.contains("page\t2026-10-13 dapper-fig\tfile\t0\t0\t0") && plan.contains("page\t2026-10-16 wide\tfile\t0\t6\t0")
                && plan.contains("unreadable\tbroken") && plan.trim().endsWith("done"), "The plan says what went where:\n" + plan);
        Set<String> made = new TreeSet<>(after.keySet()); made.removeAll(before.keySet());
        made.removeIf(p -> p.startsWith("Originals/"));
        check(made.equals(new TreeSet<>(Arrays.asList("Loose pages.mpaint", "Weird copy.mpaint"))), "Nothing else is made: " + made);
    }

    /** Interrupted before each step (half-written files cut short), then run again: as an uninterrupted run. */
    static void interrupted(File base) throws Exception {
        File clean = new File(base, "clean"); build(clean);
        TreeMap<String, byte[]> before = files(clean);
        new NotebookMigration(clean).run(null, null);
        TreeMap<String, byte[]> whole = files(clean);
        int steps;
        { File count = new File(base, "count"); build(count); int[] n = {0}; NotebookMigration m = new NotebookMigration(count); m.steps = w -> n[0]++; m.run(null, null); steps = n[0]; delete(count); }
        check(steps > 20, "Many steps to interrupt: " + steps);
        for (int stop = 0; stop < steps; stop++) {
            File root = new File(base, "stop" + stop); build(root);
            NotebookMigration first = new NotebookMigration(root);
            int[] n = {0}; final int at = stop;
            first.steps = what -> { if (n[0]++ == at) throw new IOException("Interrupted before " + what); };
            try { first.run(null, null); throw new AssertionError("Not interrupted at " + stop); }
            catch (IOException expected) { }
            // Whatever was half written is cut short, as a power cut would leave it.
            for (Map.Entry<String, byte[]> f : files(root).entrySet())
                if (f.getKey().endsWith(NotebookMigration.WRITING)) Files.write(new File(root, f.getKey()).toPath(), Arrays.copyOf(f.getValue(), f.getValue().length / 2));
            check(!new File(root, "Originals/Notebooks migration.txt").isFile() || !new NotebookMigration(root).finished(), "Not finished when interrupted");
            // No original is ever lost: each is in the library or in Originals, byte for byte, at every stop.
            TreeMap<String, byte[]> mid = files(root);
            for (String path : before.keySet())
                check(Arrays.equals(before.get(path), mid.get(path)) || Arrays.equals(before.get(path), mid.get("Originals/" + path)),
                        "Interrupted at step " + stop + ", " + path + " is still whole somewhere");
            NotebookMigration.Result result = new NotebookMigration(root).run(null, null);
            TreeMap<String, byte[]> after = files(root);
            after.keySet().removeIf(p -> p.endsWith(NotebookMigration.WRITING));
            expected(before, after);
            for (String path : whole.keySet())
                if (!path.endsWith(".txt")) check(Arrays.equals(whole.get(path), after.get(path)), "Interrupted at step " + stop + " and run again: " + path + " as an uninterrupted run makes it");
            check(result.ran || stop == steps - 1, "The run after an interruption finishes it");
            delete(root);
        }
    }

    /** The working drawing with unsaved changes. */
    static void working(File base) throws Exception {
        File root = new File(base, "working");
        put(root, "2026-10-13 a.mpaint", old(page(W, H, 1, 1, 10)), T0);
        put(root, "2026-10-14 b.mpaint", old(page(W, H, 2, 2, 20)), T0 + DAY);
        byte[] b = Files.readAllBytes(new File(root, "2026-10-14 b.mpaint").toPath());
        // Its recovery: b with a new mark its file doesn't have.
        DrawingBook recovered = BookCodec.read(new ByteArrayInputStream(b));
        recovered.current().begin(); recovered.current().setTone(40, 40, 0); recovered.current().finish();
        NotebookMigration.Result result = new NotebookMigration(root).run(new NotebookMigration.Working("2026-10-14 b", recovered), null);
        DrawingBook loose = BookCodec.read(new ByteArrayInputStream(Files.readAllBytes(new File(root, "Loose pages.mpaint").toPath())));
        loose.select(1);
        check(result.working && loose.count() == 2 && loose.current().compositeTone(40, 40) == 0, "The working drawing goes in with its newest pages, last");
        check(Arrays.equals(b, Files.readAllBytes(new File(root, "Originals/2026-10-14 b.mpaint").toPath())), "Its file moves as it was");
        // A working drawing never saved goes in as a page of its own.
        File fresh = new File(base, "fresh");
        put(fresh, "2026-10-13 a.mpaint", old(page(W, H, 1, 1, 10)), T0);
        DrawingBook unsaved = new DrawingBook(page(W, H, 30, 30, 5));
        new NotebookMigration(fresh).run(new NotebookMigration.Working("2026-10-18 new-one", unsaved), null);
        DrawingBook two = BookCodec.read(new ByteArrayInputStream(Files.readAllBytes(new File(fresh, "Loose pages.mpaint").toPath())));
        two.select(1);
        check(two.count() == 2 && two.current().compositeTone(33, 33) == 5, "An unsaved new drawing goes in last");
        // The working drawing of a notebook: not gathered.
        File book = new File(base, "notebook");
        DrawingBook nb = new DrawingBook(page(W, H, 2, 2, 0));
        ByteArrayOutputStream out = new ByteArrayOutputStream(); BookCodec.write(out, nb.snapshot());
        put(book, "Mine.mpaint", out.toByteArray(), T0);
        NotebookMigration.Result none = new NotebookMigration(book).run(new NotebookMigration.Working("Mine", nb), null);
        check(!none.ran && !none.working && !new File(book, "Originals").exists(), "A notebook's unsaved changes are not gathered");
    }

    /** Past {@link DrawingBook#MAX_PAGES} pages, and past 16 MB of pages: Loose pages 2, 3 … in order. */
    static void limits(File base) throws Exception {
        File root = new File(base, "many");
        int most = DrawingBook.MAX_PAGES;
        for (int i = 0; i < 2 * most + 5; i++) put(root, String.format("d%03d.mpaint", i), old(page(16, 16, i % 8, 0, i % 200)), T0 + i * 1000L);
        new NotebookMigration(root).run(null, null);
        int[] counts = new int[3];
        for (int k = 0; k < 3; k++) {
            DrawingBook book = BookCodec.read(new ByteArrayInputStream(Files.readAllBytes(new File(root, k == 0 ? "Loose pages.mpaint" : "Loose pages " + (k + 1) + ".mpaint").toPath())));
            counts[k] = book.count();
        }
        check(counts[0] == most && counts[1] == most && counts[2] == 5, "Split at " + most + " pages: " + Arrays.toString(counts));
        DrawingBook third = BookCodec.read(new ByteArrayInputStream(Files.readAllBytes(new File(root, "Loose pages 3.mpaint").toPath())));
        third.select(4);
        int last = 2 * most + 4;
        check(third.current().compositeTone(last % 8 + 2, 2) == last % 200, "In order: the newest last");
        // Near 16 MB: pages of noise, over 2 MB each.
        File big = new File(base, "big");
        Random random = new Random(5);
        for (int i = 0; i < 9; i++) {
            ToneDocument noisy = InkWashLayers.blank(1500, 1500);
            noisy.selectLayer(InkWashLayers.INK); noisy.begin();
            int[] row = new int[1500];
            for (int y = 0; y < 1500; y++) { for (int x = 0; x < 1500; x++) row[x] = random.nextInt(256); noisy.setTones(0, y, 1500, 1, row); }
            noisy.finish();
            put(big, "n" + i + ".mpaint", old(noisy), T0 + i);
        }
        File together = new File(base, "together");
        for (File f : big.listFiles()) { File to = new File(together, f.getName()); to.getParentFile().mkdirs(); Files.copy(f.toPath(), to.toPath()); to.setLastModified(f.lastModified()); }
        new NotebookMigration(together).run(null, null);
        check(BookCodec.openPages(new File(together, "Loose pages.mpaint")).pages.size() == 9 && !new File(together, "Loose pages 2.mpaint").exists()
                && new File(together, "Loose pages.mpaint").length() > 16L << 20, "Past the old 16 MB, all in one notebook");
        NotebookMigration small = new NotebookMigration(big);
        small.budget = 15L << 20;
        small.run(null, null);
        File first = new File(big, "Loose pages.mpaint"), second = new File(big, "Loose pages 2.mpaint");
        int a = BookCodec.readPages(new FileInputStream(first)).pages.size(), b = BookCodec.readPages(new FileInputStream(second)).pages.size();
        check(a + b == 9 && b > 0 && first.length() <= BookCodec.MAX_ARCHIVE_BYTES && second.length() <= BookCodec.MAX_ARCHIVE_BYTES,
                "Split near a notebook's limit (here 15 MB): " + a + " + " + b + " pages, " + first.length() / 1024 + " KB");
    }

    /** A library with nothing to gather is left alone: no plan, no Originals. */
    static void nothing(File base) throws Exception {
        File root = new File(base, "empty"); root.mkdirs();
        put(root, "Inkwash import.txt", "x".getBytes(), T0);
        check(!new NotebookMigration(root).run(null, null).ran && !new File(root, "Originals").exists(), "Nothing to gather: nothing written");
        File none = new File(base, "missing");
        check(!new NotebookMigration(none).run(null, null).ran, "No library yet: nothing");
    }

    /** The order: by the date in the name, then when it last changed, then by name; a drawing without a date by when it last changed. */
    static void order(File base) throws Exception {
        File root = new File(base, "order");
        // Their files' times scrambled (a copy resets them; a re-saved old drawing jumps ahead).
        put(root, "2026-10-18 suave-fig.mpaint", old(page(W, H, 0, 0, 18)), T0);
        put(root, "2026-10-13 fancy-lobster.mpaint", old(page(W, H, 0, 0, 13)), T0 + 9 * DAY);
        put(root, "2026-10-16 bold-onion.mpaint", old(page(W, H, 0, 0, 16)), T0 + 2 * DAY);
        put(root, "2026-10-13 fizzy-biscuit.mpaint", old(page(W, H, 0, 0, 14)), T0 + 10 * DAY);
        put(root, "Harbour.mpaint", old(page(W, H, 0, 0, 15)), java.time.LocalDate.of(2026, 10, 14).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + 3600_000);
        new NotebookMigration(root).run(null, null);
        DrawingBook loose = BookCodec.open(new File(root, "Loose pages.mpaint"));
        int[] tones = new int[loose.count()];
        for (int i = 0; i < tones.length; i++) { loose.select(i); tones[i] = loose.current().compositeTone(2, 2); }
        check(java.util.Arrays.equals(tones, new int[]{13, 14, 15, 16, 18}), "By the name's date, then when changed; undated by its day: " + java.util.Arrays.toString(tones));
    }
    static boolean same(TreeMap<String, byte[]> a, TreeMap<String, byte[]> b) {
        if (!a.keySet().equals(b.keySet())) return false;
        for (String k : a.keySet()) if (!Arrays.equals(a.get(k), b.get(k))) return false;
        return true;
    }
    static void delete(File f) {
        File[] list = f.listFiles();
        if (list != null) for (File c : list) delete(c);
        f.delete();
    }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
