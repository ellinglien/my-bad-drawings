package io.github.mpdairy.monopaint;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Blank pages in a notebook ({@link BlankPages}): what is blank; no blank page kept at the end since 2026-10-24, the old
 * one going when a notebook is opened, not as a change, and only when it is truly blank and nothing of its own; the page
 * bar's + tile adding a page at the end ({@link DrawingBook#insertPage}), up to the most; a blank page deleted
 * without asking; the PDF and PNG copies leaving out blank pages at the end.
 */
public final class BlankPagesChecks {
    static final int W = 300, H = 400;

    public static void main(String[] args) throws Exception {
        blank();
        File dir = Files.createTempDirectory("blank").toFile();
        try {
            opened(dir);
            plus();
            deleting(dir);
            copies(dir);
            cost(dir);
        } finally { NotebookMigrationChecks.delete(dir); }
        System.out.println("PASS: blank pages: a page with no marks, magnets or masking fluid on white paper is blank, a shaded one isn't; opening an old notebook takes the blank page at its end away, not as a change, its other pages byte for byte once saved; "
                + "a last page with a mark, a shade or anything of its own (template, rules, time, step, ghost, cover picture) stays, and so do the page drawn on, a page between, the only page and a page with layers; "
                + "the page bar's + puts a page after the page drawn on (the first from the front cover, the last from the back), on the notebook's template and Page Colour, drawn on, up to 250 pages, \"Notebook full, 250 pages\" past them; a blank page is deleted without asking (told by its page, its picture or its bytes; a faint mark, a shade or a magnet asks); "
                + "the PDF and PNG copies leave out blank pages at the end, never the first page or one between; a blank page is about 9 KB of a 1920 × 2400 notebook's file");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static ToneDocument marked(int seed) {
        ToneDocument page = new ToneDocument(W, H);
        page.begin();
        Random r = new Random(seed);
        for (int i = 0; i < 200; i++) page.setTone(r.nextInt(W), r.nextInt(H), r.nextInt(128));
        page.finish();
        return page;
    }
    static void mark(ToneDocument page) { page.begin(); for (int x = 0; x < 30; x++) page.setTone(x, 10, 0); page.finish(); }
    static PackedPage packed(ToneDocument page) throws IOException { return PackedPage.of(DrawingBook.encode(page)); }
    /** A notebook of these pages written to {@code file} with their pictures, as the app saves one, and opened. */
    static DrawingBook written(File file, List<ToneDocument> pages) throws IOException {
        List<PackedPage> packed = new ArrayList<>(); List<byte[]> pictures = new ArrayList<>();
        for (ToneDocument p : pages) { packed.add(packed(p)); pictures.add(PagePicture.of(p.layerSnapshot()).encode()); }
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            BookCodec.write(out, W, H, packed, 0, new int[packed.size()], 0, pictures);
        }
        return BookCodec.open(file);
    }

    /** What is blank. */
    static void blank() {
        ToneDocument page = new ToneDocument(W, H);
        check(BlankPages.blank(page) && BlankPages.blank(InkWashLayers.blank(W, H)), "A new page is blank");
        check(!BlankPages.blank(marked(1)), "A marked page isn't");
        ToneDocument shaded = new ToneDocument(W, H); shaded.shadePaper(136);
        check(!BlankPages.blank(shaded), "A shaded page isn't");
        ToneDocument magnet = new ToneDocument(W, H);
        magnet.placeMagnets(Magnets.of(Collections.singletonList(new Magnets.Magnet("ink", 100, 100, 0, 0))));
        check(!BlankPages.blank(magnet), "A page with a magnet isn't");
        ToneDocument fluid = new ToneDocument(W, H);
        fluid.paintMaskingFluid(true); fluid.begin(); fluid.setTone(5, 5, 0); fluid.finish(); fluid.paintMaskingFluid(false);
        check(fluid.hasMask() && !BlankPages.blank(fluid), "A page with masking fluid isn't");
        ToneDocument erased = marked(2); erased.begin(); for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) erased.eraseTone(x, y, 1); erased.finish();
        check(BlankPages.blank(erased), "A page drawn on and wholly erased is blank again");
    }

    /**
     * Opening a notebook: the blank page notebooks kept at their end until 2026-10-24 goes, not as a change; a last page
     * with anything on it or of its own stays, and so do the page drawn on, a page between and a notebook's only page.
     */
    static void opened(File dir) throws IOException {
        File file = new File(dir, "three.mpaint");
        // As the app saved a notebook then: three pages drawn on and the blank page after them, the bytes BlankPages.made makes.
        DrawingBook old = written(file, Arrays.asList(marked(1), marked(2), marked(3), new ToneDocument(W, H)));
        List<Long> crcs = new ArrayList<>();
        for (PackedPage p : BookCodec.openPages(file).pages) crcs.add(p.crc);
        check(crcs.get(3) == BlankPages.made(W, H).crc, "The old blank page at the end is the app's own bytes");
        check(BlankPages.opened(old) && old.count() == 3 && old.index() == 0, "Opened: the blank page at the end goes, the page shown still current");
        check(!old.unsaved(), "Not a change: opening a notebook doesn't save it");
        check(!BlankPages.opened(old) && old.count() == 3, "Its last page drawn on: nothing more goes");
        // Saved with a change elsewhere: three pages, the unchanged ones byte for byte.
        old.select(1); mark(old.current()); old.select(0);
        NotebookPagesChecks.save(old, file, dir);
        List<PackedPage> saved = BookCodec.openPages(file).pages;
        check(saved.size() == 3 && saved.get(0).crc == crcs.get(0) && saved.get(2).crc == crcs.get(2) && saved.get(1).crc != crcs.get(1),
                "Saved: three pages, the unchanged ones byte for byte, the changed one changed");
        // A last page with anything on it stays: a faint mark, a shade.
        ToneDocument faint = new ToneDocument(W, H); faint.begin(); faint.setTone(7, 7, 250); faint.finish();
        ToneDocument shaded = new ToneDocument(W, H); shaded.shadePaper(136);
        for (ToneDocument last : new ToneDocument[]{marked(5), faint, shaded}) {
            DrawingBook kept = written(new File(dir, "kept.mpaint"), Arrays.asList(marked(4), last));
            check(!BlankPages.opened(kept) && kept.count() == 2, "A last page with anything on it stays");
        }
        // A blank last page of its own stays: its own template, rules, time, see-through step, ghost pin or cover picture.
        String[] owns = {"template", "rules", "time", "step", "ghost", "cover"};
        for (String own : owns) {
            DrawingBook book = written(new File(dir, "own.mpaint"), Arrays.asList(marked(4), new ToneDocument(W, H)));
            switch (own) {
                case "template": book.setPageTemplate(1, NotebookTemplate.GRID); break;
                case "rules": book.setPageRules(1, NotebookRules.validOwn(1) ? 1 : 2); break;
                case "time": book.setPageSeconds(1, 120); break;
                case "step": book.setTracing(book.tracing().withStep(1, TracingPaper.FAINT)); break;
                case "ghost": book.setTracing(book.tracing().withGhost(1)); break;
                default: book.setCover(1);
            }
            check(!BlankPages.opened(book) && book.count() == 2, "A blank last page with its own " + own + " stays");
        }
        // The page drawn on, a page between, a notebook's only page, a blank page with a layer on it.
        DrawingBook on = written(new File(dir, "on.mpaint"), Arrays.asList(marked(4), new ToneDocument(W, H)));
        on.select(1);
        check(!BlankPages.opened(on) && on.count() == 2, "The page drawn on stays");
        DrawingBook between = written(new File(dir, "between.mpaint"), Arrays.asList(marked(4), new ToneDocument(W, H), marked(5)));
        check(!BlankPages.opened(between) && between.count() == 3, "A blank page between stays");
        DrawingBook only = written(new File(dir, "only.mpaint"), Collections.singletonList(new ToneDocument(W, H)));
        check(!BlankPages.opened(only) && only.count() == 1, "A notebook's only page stays");
        DrawingBook layered = written(new File(dir, "layered.mpaint"), Arrays.asList(marked(4), new ToneDocument(W, H), new ToneDocument(W, H)));
        layered.setTracing(new TracingPaper(TracingPaper.PLAIN, -1, new int[]{0, 0, TracingPaper.LINKED}));
        check(!BlankPages.opened(layered) && layered.count() == 3, "A blank page with a layer on it stays: only a page alone goes");
        // A notebook as the app saves one now: nothing added on opening.
        DrawingBook now = written(new File(dir, "now.mpaint"), Arrays.asList(marked(4), marked(5)));
        check(!BlankPages.opened(now) && now.count() == 2 && BlankPages.blank(now, 1) == false, "A notebook with no blank page at its end: nothing added, nothing taken");
    }

    /** The page bar's + tile: a new page at the notebook's end, on the notebook's template and Page Colour, drawn on; none past the most. */
    static void plus() throws IOException {
        DrawingBook book = new DrawingBook(marked(1));
        book.addPage(); mark(book.current());
        book.addPage(); mark(book.current());
        book.setOptions(book.options().withTemplate(NotebookTemplate.LINED).withPaper(200));
        book.select(0);
        book.insertPage();
        check(book.count() == 4 && book.index() == 3, "+ on the first page: a new last page, drawn on");
        check(BlankPages.blank(book, 3) && book.current().paperTone() == 200 && book.template(3).equals(NotebookTemplate.LINED), "on the notebook's Page Colour and template");
        check(!BlankPages.blank(book, 0) && !BlankPages.blank(book, 1) && !BlankPages.blank(book, 2), "the pages before it stay");
        book.insertPage();
        check(book.count() == 5 && book.index() == 4 && BlankPages.blank(book, 4), "+ on the last page: a new last page");
        book.select(DrawingBook.FRONT); book.insertPage();
        check(book.count() == 6 && book.index() == 5 && BlankPages.blank(book, 5), "+ on the front cover: a new last page");
        book.select(DrawingBook.BACK); book.insertPage();
        check(book.count() == 7 && book.index() == 6, "+ on the back cover: a new last page");
        check(book.unsaved(), "A change to save");
        // Full: the 250th page is the last, and the note says so.
        DrawingBook full = new DrawingBook(marked(1));
        while (full.count() < DrawingBook.MAX_PAGES) full.insertPage();
        boolean threw = false;
        try { full.insertPage(); } catch (IOException e) { threw = true; }
        check(threw && full.count() == DrawingBook.MAX_PAGES, "Full at " + DrawingBook.MAX_PAGES + " pages: no more");
        check(DrawingBook.PAGES_FULL.equals("Notebook full, 250 pages"), "The note when full: " + DrawingBook.PAGES_FULL);
    }

    /** A blank page goes without asking; a page with anything on it asks. */
    static void deleting(File dir) throws IOException {
        ToneDocument faint = new ToneDocument(W, H); faint.begin(); faint.setTone(7, 7, 250); faint.finish();
        ToneDocument shaded = new ToneDocument(W, H); shaded.shadePaper(136);
        DrawingBook book = written(new File(dir, "delete.mpaint"), Arrays.asList(marked(1), new ToneDocument(W, H), faint, shaded, InkWashLayers.blank(W, H)));
        check(PagePicture.decode(book.picture(2)).white(), "A faint mark leaves its picture white");
        check(BlankPages.deleteAsks(book, 0), "A marked page (drawn on now) asks");
        check(!BlankPages.deleteAsks(book, 1), "A blank page, by its picture and bytes, goes at once");
        check(BlankPages.deleteAsks(book, 2), "A faint mark asks: its picture is white, its page isn't");
        check(BlankPages.deleteAsks(book, 3), "A shaded page asks, by its picture");
        check(!BlankPages.deleteAsks(book, 4), "A blank page of another make goes at once, decoded to tell");
        DrawingBook bare = new DrawingBook(W, H, new ArrayList<>(Arrays.asList(DrawingBook.encode(marked(1)), DrawingBook.encode(new ToneDocument(W, H)), DrawingBook.encode(marked(2)))), 0);
        check(!BlankPages.deleteAsks(bare, 1) && BlankPages.deleteAsks(bare, 2), "Pages with no pictures: told by their bytes");
        check(!BlankPages.empty(bare) && BlankPages.empty(new DrawingBook(new ToneDocument(W, H))), "A notebook with nothing drawn is empty");
    }

    /** The PDF and PNG copies: blank pages at the end left out, the first and those between kept. */
    static void copies(File dir) throws Exception {
        File folder = new File(dir, "export");
        PackedPage a = packed(marked(1)), b = packed(marked(2)), blank = PackedPage.of(BlankPages.made(W, H).bytes), other = packed(InkWashLayers.blank(W, H));
        NotebookExport.Done done = NotebookExport.update(folder, "N", Arrays.asList(a, b, blank, other));
        check(done.drawn == 2 && pdfPages(done.pdf) == 2 && !new File(folder, "N/003.png").exists(), "Blank pages at the end: no PNG, no PDF page");
        done = NotebookExport.update(folder, "N", Arrays.asList(a, blank, b, blank));
        check(pdfPages(done.pdf) == 3 && new File(folder, "N/002.png").isFile() && !new File(folder, "N/004.png").exists(), "A blank page between is kept");
        done = NotebookExport.update(folder, "Blank", Arrays.asList(blank, other));
        check(pdfPages(done.pdf) == 1 && new File(folder, "Blank/001.png").isFile(), "A notebook of blank pages keeps its first");
        check(BlankPages.drawn(Arrays.asList(a, b)).size() == 2, "A notebook with no blank page at its end: all of it");
    }
    static int pdfPages(File pdf) throws IOException {
        String text = new String(Files.readAllBytes(pdf.toPath()), StandardCharsets.ISO_8859_1);
        int at = text.indexOf("/Type /Pages /Count ") + 20;
        return Integer.parseInt(text.substring(at, text.indexOf(' ', at)));
    }

    /** What a blank page costs a 1920 × 2400 notebook's file. */
    static void cost(File dir) throws IOException {
        BlankPages.Made made = BlankPages.made(1920, 2400);
        check(made.bytes.length < 16 * 1024 && made.picture.length < 1024, "A blank 1920 × 2400 page packs to " + made.bytes.length + " bytes, its picture " + made.picture.length);
        check(BlankPages.made(1920, 2400) == made, "Made once a size");
    }
}
