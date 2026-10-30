package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gathers the library's old drawings into notebooks, once (docs/archive/superpowers/specs/2026-10-18-notebooks-design.md, "Old
 * drawings"): every one-page drawing becomes a page of "Loose pages", by the date in its name (another Loose pages past
 * 100 pages or 200 MB; a drawing that can't take the notebook's size without losing a mark keeps its own); a multi-page file becomes a notebook of its own at its path, its pages byte
 * for byte. Every original is moved, untouched (renamed, with its atomic save's {@code .bak} and {@code .new}), into
 * {@link #ORIGINALS} only once every notebook is written and read back. A plan in plain text ({@link #PLAN}, in
 * Originals) is written before anything else and records each notebook written and each original moved, so a run that
 * is interrupted goes on from where it stopped, and one that finished never runs again. Files that aren't drawings
 * (brushes, import files) are never touched, nor anything that is already a notebook (its file has a notebook block).
 * Pure Java: checked on the desktop on a copy of a realistic library ({@code NotebookMigrationChecks}). Runs on the
 * store's IO queue, before anything else reads the library.
 */
final class NotebookMigration {
    static final String ORIGINALS = DrawingFiles.ORIGINALS, PLAN = "Notebooks migration.txt", LOOSE = "Loose pages";
    private static final String HEADER = "Inkwash notebooks migration 1";
    /** A file being written, before it is read back and renamed into place. */
    static final String WRITING = ".writing";
    /** The pages a Loose pages notebook takes at most, by their estimated size: 1 MB under a notebook's limit (200 MB). */
    static final long PAGE_BUDGET = DrawingBook.MAX_PACKED_BYTES - (1 << 20);
    /** This run's budget ({@link #PAGE_BUDGET}; the checks try a smaller one). */
    long budget = PAGE_BUDGET;
    /** A drawing's files: itself, an atomic save's backup and its unfinished write. */
    private static final String[] SIBLINGS = {"", ".bak", ".new"};

    /** How far a run is: pages written, of all. */
    interface Progress { void at(int done, int total); }

    /** The working drawing, when its recovery holds changes its file doesn't: its path ("" if never named) and book. */
    static final class Working {
        final String path; final DrawingBook book;
        Working(String path, DrawingBook book) { this.path = path; this.book = book; }
    }

    /** What a run did. */
    static final class Result {
        /** Whether anything was gathered, by this run or the interrupted one it finished. */
        final boolean ran;
        /** The notebooks written, as library paths; whether the working drawing went into one. */
        final List<String> notebooks;
        final boolean working;
        final int pages, moved;
        Result(boolean ran, List<String> notebooks, boolean working, int pages, int moved) {
            this.ran = ran; this.notebooks = notebooks; this.working = working; this.pages = pages; this.moved = moved;
        }
        static Result nothing() { return new Result(false, new ArrayList<>(), false, 0, 0); }
    }

    private final File root, originals, planFile;
    /** Each step that changes a file, named, just before it: the checks interrupt a run at each in turn. */
    interface Steps { void step(String what) throws IOException; }
    Steps steps = what -> { };

    NotebookMigration(File root) {
        this.root = root; originals = new File(root, ORIGINALS); planFile = new File(originals, PLAN);
    }

    /** Whether this library's migration has finished (its plan says so). */
    boolean finished() throws IOException { return planFile.isFile() && Plan.read(planFile).done; }

    /**
     * Gathers the old drawings, or finishes an interrupted run; with nothing to gather, writes nothing, not even a plan.
     * {@code working}: the working drawing with changes its file lacks, or null.
     */
    Result run(Working working, Progress progress) throws IOException {
        Plan plan;
        if (planFile.isFile()) {
            plan = Plan.read(planFile);
            if (plan.done) return Result.nothing();
        } else {
            plan = plan(working);
            if (plan == null) return Result.nothing();
            if (!originals.isDirectory() && !originals.mkdirs()) throw new IOException("Could not make " + ORIGINALS);
            save(plan);
        }
        int total = 0, done = 0;
        for (Output output : plan.outputs) total += output.pages.size();
        // 1. Every notebook written and read back.
        for (Output output : plan.outputs) {
            if (!plan.written.contains(output.path)) {
                write(plan, output, working, progress, done, total);
                plan.written.add(output.path); save(plan);
            }
            done += output.pages.size();
            if (progress != null) progress.at(done, total);
        }
        // 2. Only then the originals, untouched, into Originals.
        int moved = 0;
        for (String source : plan.movedAll ? new ArrayList<String>() : plan.sources()) {
            // Where it goes is recorded first, so an interrupted move finishes under the same name.
            String to = plan.moved.get(source);
            if (to == null) {
                to = destination(source);
                if (to == null) continue;
                plan.moved.put(source, to); save(plan);
            }
            if (moveOriginal(source, to)) moved++;
        }
        // From here on a file at an original's path is a notebook taking its place, never an original.
        if (!plan.movedAll) { plan.movedAll = true; save(plan); }
        // 3. Each multi-page drawing's notebook into its place, now free.
        for (Output output : plan.outputs) {
            File temporary = temporary(output.path);
            if (!output.copy || !temporary.isFile()) continue;
            // Read once more before it takes its place: one cut short is written again, from the original in Originals.
            try { closeSources(verify(temporary)); }
            catch (IOException damaged) { write(plan, output, working, null, 0, total); }
            if (DrawingFiles.exists(drawing(output.path))) {
                String free = unused(DrawingFiles.parent(output.path), DrawingFiles.name(output.path), new Plan());
                plan.written.add(free); output.path = free; save(plan);
            }
            move(temporary, drawing(output.path));
        }
        plan.done = true; save(plan);
        List<String> notebooks = new ArrayList<>();
        for (Output output : plan.outputs) if (drawing(output.path).isFile()) notebooks.add(output.path);
        return new Result(true, notebooks, plan.usesWorking(), total, moved);
    }

    // A drawing's pages

    /** A drawing's pages, packed, as its file or the working drawing holds them: a file's read from it when needed. */
    static final class Book {
        final int width, height, active;
        final List<PackedPage> pages;
        final int[] choices;
        Book(int width, int height, int active, List<PackedPage> pages, int[] choices) {
            this.width = width; this.height = height; this.active = active; this.pages = pages; this.choices = choices;
        }
        static Book of(BookCodec.Pages read) throws IOException {
            int[] size = read.single ? pageSize(read.pages.get(0).bytes()) : new int[]{read.width, read.height};
            return new Book(size[0], size[1], read.active, read.pages, read.choices);
        }
        static Book of(DrawingBook book) throws IOException {
            DrawingBook.Snapshot s = book.snapshot();
            List<PackedPage> pages = s.packed();
            int[] choices = new int[s.canvasChoices.size()];
            for (int i = 0; i < choices.length; i++) choices[i] = s.canvasChoices.get(i);
            return new Book(s.width, s.height, s.index, pages, choices);
        }
    }
    /** A file's pages, its structure and every page's header checked, none read yet; null when it doesn't open. */
    private static Book read(File file) {
        if (file == null) return null;
        try { return Book.of(BookCodec.openPages(file)); }
        catch (IOException | RuntimeException e) { return null; }
    }
    /** Every page of {@code file} read and checked against its CRC-32, one at a time. */
    private static List<PackedPage> verify(File file) throws IOException {
        BookCodec.Pages back = BookCodec.openPages(file);
        for (PackedPage page : back.pages) page.bytes();
        if (back.found == null) throw new IOException(file.getName() + " read back without its pictures");
        return back.pages;
    }
    private static int[] pageSize(byte[] page) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(page);
        b.getInt(); return new int[]{b.getInt(), b.getInt()};
    }
    private static ToneDocument decode(byte[] page) throws IOException { return DocumentCodec.read(new ByteArrayInputStream(page)); }
    /** The pages a notebook's {@code page} comes from: the working drawing's, or its original's, where it is now. */
    private Book book(Plan plan, Page page, Working working) throws IOException {
        if (page.working) return working == null ? null : Book.of(working.book);
        return read(content(locate(plan, page.source)));
    }
    /** The day a drawing's page goes by: the date its name starts with ("2026-10-14 dapper-fig"), else the day it last changed. */
    private static java.time.LocalDate day(Source source) {
        String name = DrawingFiles.name(source.path);
        if (name.matches("\\d{4}-\\d{2}-\\d{2}( .*)?")) {
            try { return java.time.LocalDate.parse(name.substring(0, 10)); } catch (java.time.DateTimeException ignored) { }
        }
        if (source.modified == Long.MAX_VALUE) return java.time.LocalDate.now();
        return java.time.Instant.ofEpochMilli(source.modified).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }

    // Planning

    /** An old drawing found in the library. */
    static final class Source {
        /** Its library path (folder and name, no extension); for a working drawing never named, "". */
        final String path;
        /** Read from the working drawing rather than its file; whether a file of its own is there. */
        boolean working, file;
        long modified, bytes;
        int pages, width, height;
        Source(String path) { this.path = path; }
    }
    /** A notebook to write: its path and its pages; {@code copy}: a multi-page drawing's, its pages as they are. */
    static final class Output {
        String path; final boolean copy; int width, height;
        final List<Page> pages = new ArrayList<>();
        Output(String path, boolean copy, int width, int height) { this.path = path; this.copy = copy; this.width = width; this.height = height; }
    }
    /** A page's {@code left} and {@code top} when it keeps its own size: it would lose a mark at the notebook's. */
    static final int OWN_SIZE = -1;
    /** A notebook's page: page {@code page} of a drawing, its pixel ({@code left}, {@code top}) at the notebook page's top left, or ({@link #OWN_SIZE}) as it is. */
    static final class Page {
        final String source; final boolean working; final int page, left, top;
        Page(String source, boolean working, int page, int left, int top) { this.source = source; this.working = working; this.page = page; this.left = left; this.top = top; }
    }

    /** The plan for this library, or null when there is nothing to gather. */
    private Plan plan(Working working) throws IOException {
        Plan plan = new Plan();
        List<Source> singles = new ArrayList<>(), multis = new ArrayList<>();
        boolean matched = false;
        for (String path : drawings("")) {
            File bytes = content(path);
            // Already a notebook: left alone.
            if (bytes != null && BookPictures.hasBlock(bytes)) continue;
            Source source = new Source(path); source.file = true; source.modified = modified(path);
            if (working != null && !path.isEmpty() && working.path.equals(path)) {
                // Its recovery is newer than its file: the newest pages go in; the file is moved as it is.
                source.working = true; source.modified = Long.MAX_VALUE; matched = true;
                describe(source, Book.of(working.book));
            } else {
                Book book = read(bytes);
                if (book == null) { plan.skipped.add(path); continue; }
                describe(source, book);
            }
            (source.pages == 1 ? singles : multis).add(source);
        }
        if (working != null && !matched && !(working.path.length() > 0 && DrawingFiles.exists(drawing(working.path)))) {
            // A working drawing with no file yet.
            Source source = new Source(working.path); source.working = true; source.modified = Long.MAX_VALUE;
            describe(source, Book.of(working.book));
            (source.pages == 1 ? singles : multis).add(source);
        }
        if (singles.isEmpty() && multis.isEmpty() && plan.skipped.isEmpty()) return null;
        // Each multi-page drawing a notebook where it was (its file moves first), or under a name the app takes.
        for (Source multi : multis) {
            String path = multi.path;
            boolean own = !path.isEmpty() && DrawingFiles.validPath(path) && !plan.reserved(path) && (multi.file || !DrawingFiles.exists(drawing(path)));
            if (!own) {
                String folder = DrawingFiles.parent(path);
                path = unused(folder.isEmpty() || DrawingFiles.validPath(folder) ? folder : "", safeName(DrawingFiles.name(path), "Notebook"), plan);
            }
            Output output = new Output(path, true, multi.width, multi.height);
            for (int i = 0; i < multi.pages; i++) output.pages.add(new Page(multi.path, multi.working, i, 0, 0));
            plan.outputs.add(output); plan.reserve(path);
        }
        // The one-page drawings, oldest first, in Loose pages: at the size most of them have, each fitted without losing a
        // mark; one that can't be keeps its own size (a book's pages may differ). Another Loose pages only past the limits.
        // By the date in the name ("2026-10-14 dapper-fig"; a drawing without one, the day it last changed), then when it last changed, then by name.
        java.util.Comparator<Source> order = java.util.Comparator.comparing(NotebookMigration::day);
        singles.sort(order.thenComparingLong(s -> s.modified).thenComparing(s -> s.path));
        int number = 1;
        if (!singles.isEmpty()) {
            int[] size = commonSize(singles);
            Output output = null;
            long bytes = 0;
            for (Source source : singles) {
                int[] fit = fit(source, size[0], size[1], working);
                if (fit == null) fit = new int[]{OWN_SIZE, OWN_SIZE};
                boolean same = fit[0] == OWN_SIZE || source.width == size[0] && source.height == size[1];
                long estimate = same ? source.bytes : (long) (1.25 * source.bytes * size[0] * size[1] / ((double) source.width * source.height));
                if (output == null || output.pages.size() >= DrawingBook.MAX_PAGES || bytes + estimate > budget) {
                    String name = loose(number++, plan);
                    output = new Output(name, false, size[0], size[1]);
                    plan.outputs.add(output); plan.reserve(name); bytes = 0;
                }
                output.pages.add(new Page(source.path, source.working, 0, fit[0], fit[1])); bytes += estimate;
            }
        }
        return plan;
    }
    private static void describe(Source source, Book book) throws IOException {
        source.pages = book.pages.size();
        int[] size = source.pages == 1 ? pageSize(book.pages.get(0).bytes()) : new int[]{book.width, book.height};
        source.width = size[0]; source.height = size[1];
        for (PackedPage page : book.pages) source.bytes += page.length;
    }
    /** The size most of {@code sources} have; on a tie, the one the newest of them has. */
    private static int[] commonSize(List<Source> sources) {
        LinkedHashMap<Long, Integer> counts = new LinkedHashMap<>();
        HashMap<Long, Integer> newest = new HashMap<>();
        for (int i = 0; i < sources.size(); i++) {
            long key = (long) sources.get(i).width << 32 | sources.get(i).height;
            counts.merge(key, 1, Integer::sum); newest.put(key, i);
        }
        long best = -1;
        for (Map.Entry<Long, Integer> e : counts.entrySet()) {
            int count = e.getValue();
            if (best < 0 || count > counts.get(best) || count == counts.get(best) && newest.get(e.getKey()) > newest.get(best)) best = e.getKey();
        }
        return new int[]{(int) (best >>> 32), (int) best};
    }
    /** Where {@code source}'s page goes on a {@code width × height} page without losing a mark ({@link #fit(ToneDocument, int, int)}), or null. */
    private int[] fit(Source source, int width, int height, Working working) throws IOException {
        if (source.width <= width && source.height <= height) return new int[]{0, 0};
        Book book = source.working ? Book.of(working.book) : read(content(source.path));
        if (book == null) return null;
        try { return fit(decode(book.pages.get(0).bytes()), width, height); }
        catch (IOException e) { return null; }
    }
    /**
     * Where {@code page} goes on a {@code width × height} page without losing a mark: its pixel at the new page's top
     * left, {@code {left, top}}, or null. Smaller: padded on the right and bottom. Larger: the extra comes off its left
     * edge (the old bar's strip) as far as it is blank, then its right; rows off its top, then its bottom. A mark is any
     * layer's paint or the masking fluid.
     */
    static int[] fit(ToneDocument page, int width, int height) {
        ToneDocument.Snapshot s = page.layerSnapshot();
        int left = 0, top = 0;
        if (page.width > width) {
            int extra = page.width - width;
            left = blank(s, true, true, extra);
            if (blank(s, true, false, extra - left) < extra - left) return null;
        }
        if (page.height > height) {
            int extra = page.height - height;
            top = blank(s, false, true, extra);
            if (blank(s, false, false, extra - top) < extra - top) return null;
        }
        return new int[]{left, top};
    }
    /** How many columns ({@code columns}) or rows from the start (or end) edge, up to {@code most}, hold no mark. */
    private static int blank(ToneDocument.Snapshot s, boolean columns, boolean start, int most) {
        int lines = columns ? s.width : s.height, along = columns ? s.height : s.width;
        for (int n = 0; n < most; n++) {
            int line = start ? n : lines - 1 - n;
            for (int k = 0; k < along; k++) {
                int i = columns ? k * s.width + line : line * s.width + k;
                for (ToneDocument.Layer layer : s.layers) if (layer.marks(i)) return n;
                if (s.mask != null && s.mask.alpha[i] != 0) return n;
            }
        }
        return most;
    }

    // Writing

    /**
     * Writes {@code output} beside its place ({@link #WRITING}), synced, reads it back and compares every page with what
     * was meant; a Loose pages notebook then goes into its place, a multi-page drawing's once the original has moved.
     */
    private void write(Plan plan, Output output, Working working, Progress progress, int before, int total) throws IOException {
        List<PackedPage> pages = new ArrayList<>();
        List<byte[]> pictures = new ArrayList<>();
        int[] choices = null; int index = 0;
        if (output.copy) {
            Book book = book(plan, output.pages.get(0), working);
            if (book == null || book.pages.size() != output.pages.size()) throw new IOException("Could not read " + output.pages.get(0).source);
            for (int i = 0; i < book.pages.size(); i++) {
                PackedPage bytes = book.pages.get(i);
                pages.add(bytes); pictures.add(picture(bytes, null));
                if (progress != null) progress.at(before + i + 1, total);
            }
            choices = book.choices; index = book.active;
        } else {
            for (int n = 0; n < output.pages.size(); n++) {
                Page page = output.pages.get(n);
                Book book = book(plan, page, working);
                // An original gone since the plan was made (deleted by hand) or that doesn't decode: left out (it moves as it is).
                if (book == null && !page.source.isEmpty() && !plan.skipped.contains(page.source)) plan.skipped.add(page.source);
                if (book != null) {
                    PackedPage bytes = book.pages.get(page.page);
                    ToneDocument decoded = null;
                    try { decoded = decode(bytes.bytes()); }
                    catch (IOException | RuntimeException e) { bytes = null; if (!page.source.isEmpty() && !plan.skipped.contains(page.source)) plan.skipped.add(page.source); }
                    if (bytes != null) {
                        boolean own = page.left == OWN_SIZE;
                        if (!own && (decoded.width != output.width || decoded.height != output.height || page.left != 0 || page.top != 0)) {
                            decoded = InkWashLayers.resized(decoded.layerSnapshot(), output.width, output.height, page.left, page.top);
                            bytes = PackedPage.of(DrawingBook.encode(decoded));
                        }
                        pages.add(bytes); pictures.add(picture(bytes, decoded));
                    }
                }
                if (progress != null) progress.at(before + n + 1, total);
            }
            choices = new int[pages.size()];
        }
        if (pages.isEmpty()) return;
        File temporary = temporary(output.path), folder = temporary.getParentFile();
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Could not make " + folder);
        steps.step("write " + output.path);
        try (FileOutputStream out = new FileOutputStream(temporary)) {
            BookCodec.write(out, output.width, output.height, pages, index, choices, 0, pictures);
            out.getFD().sync();
        }
        // Read back as the app opens it: its structure, every page's header and CRC-32, then each byte for byte as meant (a page at a time).
        List<PackedPage> back = verify(temporary);
        if (back.size() != pages.size()) throw new IOException(output.path + " read back with " + back.size() + " pages, not " + pages.size());
        for (int i = 0; i < pages.size(); i++)
            if (!Arrays.equals(back.get(i).bytes(), pages.get(i).bytes())) throw new IOException("Page " + (i + 1) + " of " + output.path + " read back changed");
        closeSources(back); closeSources(pages);
        if (!output.copy) move(temporary, drawing(output.path));
    }
    /** {@code bytes}' page picture ({@code decoded}, if decoded already); null when the page doesn't decode. */
    private static byte[] picture(PackedPage bytes, ToneDocument decoded) {
        try { return PagePicture.of((decoded != null ? decoded : decode(bytes.bytes())).layerSnapshot()).encode(); }
        catch (IOException | RuntimeException e) { return null; }
    }
    /** Closes the files these pages were read from (each opened for this notebook alone). */
    private static void closeSources(List<PackedPage> pages) {
        for (PackedPage page : pages) { PackedPage.Source file = page.file(); if (file != null) file.close(); }
    }

    // The library's files

    /** Every drawing's path under {@code folder} and its folders, but Originals; none named _recovery. */
    private List<String> drawings(String folder) {
        List<String> found = new ArrayList<>();
        File[] files = (folder.isEmpty() ? root : new File(root, folder)).listFiles();
        if (files == null) return found;
        Arrays.sort(files);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (File file : files) {
            String name = file.getName();
            if (name.contains("\t") || name.contains("\n") || name.contains("/")) continue;
            if (file.isDirectory()) {
                if (!(folder.isEmpty() && name.equalsIgnoreCase(ORIGINALS))) found.addAll(drawings(DrawingFiles.child(folder, name)));
                continue;
            }
            for (String end : SIBLINGS) {
                String suffix = DrawingFiles.EXTENSION + end;
                if (!name.endsWith(suffix) || name.length() == suffix.length()) continue;
                String base = name.substring(0, name.length() - suffix.length());
                if (!base.startsWith("_recovery")) names.add(base);
                break;
            }
        }
        for (String name : names) found.add(DrawingFiles.child(folder, name));
        return found;
    }
    private File drawing(String path) { return new File(root, path + DrawingFiles.EXTENSION); }
    private File temporary(String path) { return new File(root, path + DrawingFiles.EXTENSION + WRITING); }
    /** A drawing's file: its backup if it has one (an interrupted save's last good copy, as AtomicFile reads it), else itself; null if neither is there. */
    private File content(String path) {
        File base = drawing(path), backup = new File(base.getPath() + ".bak");
        return backup.isFile() ? backup : base.isFile() ? base : null;
    }
    /** Where an original is now: where it was, or in Originals once moved. */
    private String locate(Plan plan, String path) {
        String moved = plan.moved.get(path);
        return moved != null && !DrawingFiles.exists(drawing(path)) ? ORIGINALS + "/" + moved : path;
    }
    private long modified(String path) {
        File base = drawing(path);
        return Math.max(base.lastModified(), new File(base.getPath() + ".bak").lastModified());
    }
    /** Where in Originals {@code path}'s files go: its folder, its name (a number added if taken); null when none is there. */
    private String destination(String path) {
        if (path.isEmpty()) return null;
        boolean any = false;
        for (String end : SIBLINGS) any |= new File(root, path + DrawingFiles.EXTENSION + end).exists();
        if (!any) return null;
        String folder = DrawingFiles.parent(path), name = DrawingFiles.name(path);
        File into = folder.isEmpty() ? originals : new File(originals, folder);
        String free = name;
        for (int n = 2; taken(into, free); n++) free = name + " " + n;
        return DrawingFiles.child(folder, free);
    }
    /** Moves {@code path}'s files still there, untouched, to Originals/{@code to}; never over a file. Whether any moved. */
    private boolean moveOriginal(String path, String to) throws IOException {
        File into = new File(originals, to).getParentFile();
        if (!into.isDirectory() && !into.mkdirs()) throw new IOException("Could not make " + into);
        boolean any = false;
        for (String end : SIBLINGS) {
            File from = new File(root, path + DrawingFiles.EXTENSION + end), target = new File(originals, to + DrawingFiles.EXTENSION + end);
            if (!from.exists()) continue;
            // A notebook is never an original (originals are the files without a block): it stays.
            if (from.isFile() && BookPictures.hasBlock(from)) continue;
            if (target.exists()) throw new IOException("Originals already has " + target.getName());
            steps.step("move " + path + DrawingFiles.EXTENSION + end);
            Files.move(from.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
            any = true;
        }
        return any;
    }
    private static boolean taken(File folder, String name) {
        for (String end : SIBLINGS) if (new File(folder, name + DrawingFiles.EXTENSION + end).exists()) return true;
        return false;
    }
    /** A rename, never a copy: the same bytes, in one step. */
    private void move(File from, File to) throws IOException {
        steps.step("move " + from.getName() + " to " + to.getPath().substring(root.getPath().length()));
        rename(from, to);
    }
    private static void rename(File from, File to) throws IOException {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    private void save(Plan plan) throws IOException { steps.step("plan"); plan.write(planFile); }

    // Names

    /** The {@code n}th Loose pages notebook's name, or the next one nothing in the library has: "Loose pages", "Loose pages 2" … */
    private String loose(int n, Plan plan) {
        for (int k = n; ; k++) {
            String name = k == 1 ? LOOSE : LOOSE + " " + k;
            if (!plan.reserved(name) && !DrawingFiles.exists(drawing(name)) && !temporary(name).exists()) return name;
        }
    }
    private String unused(String folder, String base, Plan plan) {
        for (int n = 1; ; n++) {
            String suffix = n == 1 ? "" : " " + n;
            String path = DrawingFiles.child(folder, base.substring(0, Math.min(base.length(), 64 - suffix.length())).trim() + suffix);
            if (!plan.reserved(path) && !DrawingFiles.exists(drawing(path)) && !temporary(path).exists()) return path;
        }
    }
    /** {@code name} as one the app takes: other signs as spaces, at most 64 letters; {@code otherwise} if nothing is left. */
    static String safeName(String name, String otherwise) {
        String safe = name.replaceAll("[^\\p{L}\\p{N} _-]+", " ").replaceAll(" +", " ").trim();
        if (safe.length() > 64) safe = safe.substring(0, 64).trim();
        return safe.isEmpty() || safe.equals("_recovery") ? otherwise : safe;
    }

    // The plan

    /** What a run does, kept in Originals/{@link #PLAN} as plain text a person can read. */
    static final class Plan {
        final List<Output> outputs = new ArrayList<>();
        final List<String> skipped = new ArrayList<>();
        final LinkedHashSet<String> written = new LinkedHashSet<>();
        final LinkedHashMap<String, String> moved = new LinkedHashMap<>();
        boolean done, movedAll;
        private final HashSet<String> reserved = new HashSet<>();
        boolean reserved(String path) { return reserved.contains(path.toLowerCase(Locale.ROOT)); }
        void reserve(String path) { reserved.add(path.toLowerCase(Locale.ROOT)); }
        boolean usesWorking() {
            for (Output output : outputs) for (Page page : output.pages) if (page.working) return true;
            return false;
        }

        /** Every original the plan moves, in order: each notebook's, then those that didn't read. */
        List<String> sources() {
            LinkedHashSet<String> all = new LinkedHashSet<>();
            for (Output output : outputs) for (Page page : output.pages) if (!page.source.isEmpty()) all.add(page.source);
            all.addAll(skipped);
            return new ArrayList<>(all);
        }

        /** Written whole, synced, then renamed over the last: a plan is never half written. */
        void write(File file) throws IOException {
            StringBuilder text = new StringBuilder(HEADER).append('\n');
            text.append("# ").append(AppName.NAME).append(" gathered these drawings into notebooks, then moved each original here as it was.\n");
            for (Output output : outputs) {
                text.append(output.copy ? "copy" : "notebook").append('\t').append(output.path).append('\t')
                        .append(output.width).append('\t').append(output.height).append('\n');
                for (Page page : output.pages)
                    text.append("page\t").append(page.source).append('\t').append(page.working ? "working" : "file").append('\t')
                            .append(page.page).append('\t').append(page.left).append('\t').append(page.top).append('\n');
            }
            for (String path : skipped) text.append("unreadable\t").append(path).append('\n');
            for (String path : written) text.append("written\t").append(path).append('\n');
            for (Map.Entry<String, String> move : moved.entrySet()) text.append("moved\t").append(move.getKey()).append('\t').append(move.getValue()).append('\n');
            if (movedAll) text.append("moved all\n");
            if (done) text.append("done\n");
            File temporary = new File(file.getPath() + WRITING);
            try (FileOutputStream out = new FileOutputStream(temporary)) {
                out.write(text.toString().getBytes(StandardCharsets.UTF_8)); out.getFD().sync();
            }
            rename(temporary, file);
        }

        static Plan read(File file) throws IOException {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            if (lines.isEmpty() || !lines.get(0).equals(HEADER)) throw new IOException("Unknown notebooks migration plan");
            Plan plan = new Plan();
            Output output = null;
            for (String line : lines.subList(1, lines.size())) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] f = line.split("\t", -1);
                try {
                    switch (f[0]) {
                        case "copy": case "notebook":
                            output = new Output(f[1], f[0].equals("copy"), Integer.parseInt(f[2]), Integer.parseInt(f[3]));
                            plan.outputs.add(output); plan.reserve(output.path); break;
                        case "page":
                            if (output == null) throw new IOException("A page before its notebook");
                            output.pages.add(new Page(f[1], f[2].equals("working"), Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]))); break;
                        case "unreadable": plan.skipped.add(f[1]); break;
                        case "written": plan.written.add(f[1]); break;
                        case "moved": plan.moved.put(f[1], f[2]); break;
                        case "moved all": plan.movedAll = true; break;
                        case "done": plan.done = true; break;
                        default: throw new IOException("Unknown line in the notebooks migration plan: " + line);
                    }
                } catch (RuntimeException e) { throw new IOException("Damaged notebooks migration plan: " + line, e); }
            }
            return plan;
        }
    }
}
