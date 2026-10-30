package io.github.mpdairy.monopaint;

import java.io.*;
import java.util.*;

/**
 * Pictures brought onto the page ({@link PictureImport}; docs/archive/superpowers/specs/2026-10-22-import-design.md): a
 * picture's greys (its brightness over white, paper-white clear), shared with the own templates; laid on the layer drawn
 * on as one Undo step, never lightening a mark; fitted inside the page and centred, then moved, resized and pinched with
 * its shape kept and never lost off the page; a PDF's pages capped at 124, two layers each, the PDF's at the bottom locked
 * and a clear one on it, drawn on, inside the 200 MB; and a PDF's notebook written and read back as such.
 */
public final class PictureImportChecks {
    static final int W = 300, H = 400;

    public static void main(String[] args) throws IOException {
        greys(); laying(); placed(); fitting(); frame(); cap(); pdfNotebook(); pickers();
        System.out.println("PASS: picture import: greys are brightness over white (black 0, white and see-through clear, half-seen black mid grey), the own templates' too; "
                + "laid on the layer drawn on as ink is, one Undo step, never lighter than a mark there; placed on the layer drawn on, the eraser takes it and the line drawn before alike, Undo takes it away; fitted inside the page, centred, its shape kept; "
                + "moved, resized by its corner and pinched with its shape kept, never smaller than 48 px or lost off the page; "
                + "a PDF's pages capped at 124 (two layers each, one page left for the blank after the last), pairs that pass 200 MB refused; "
                + "a PDF's notebook opens on its first clear layer, each page a stack of two with the PDF's page locked at the bottom, no blank page added after the last, nor taken; "
                + "Import's picker shows a folder at a time: at the top the Supernote's folders, Pictures and Download; in one its folders A to Z, then its pictures and PDFs (any case) newest first, hidden ones skipped; it opens in the last folder used");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static void greys() {
        check(PictureImport.grey(0xff000000) == 0, "black is 0");
        check(PictureImport.grey(0xffffffff) == 255, "white is 255");
        check(PictureImport.grey(0x00000000) == 255, "see-through is white");
        check(Math.abs(PictureImport.grey(0x80000000) - 128) <= 1, "half-seen black is mid grey");
        check(PictureImport.grey(0xffff0000) == 76, "red is its luma");
        int[] g = PictureImport.greys(new int[]{0xffffffff, 0xfff8f8f8, 0xfff7f7f7, 0xff404040, 0x00000000});
        check(Arrays.equals(g, new int[]{-1, -1, 247, 64, -1}), "paper is clear, the rest their grey: " + Arrays.toString(g));
        // The own templates read pictures the same way: paper clear, the rest the nearest of the panel's greys.
        byte[] t = NotebookTemplate.tones(new int[]{0xfff8f8f8, 0xff000000, 0xff808080});
        check((t[0] & 255) == NotebookTemplate.NONE && t[1] == 0 && (t[2] & 255) == 128, "templates keep their reading");
    }

    static void laying() {
        ToneDocument page = new ToneDocument(W, H);
        long top = page.top();
        int[] greys = new int[20 * 10];
        Arrays.fill(greys, 100); greys[0] = -1;
        PictureImport.lay(page, 50, 60, 20, 10, greys);
        check(page.top() != top, "one Undo step");
        check(page.tone(51, 60) == 100 && page.tone(69, 69) == 100, "laid at its grey");
        check(page.tone(50, 60) == 255, "clear stays clear");
        check(page.tone(49, 60) == 255 && page.tone(70, 60) == 255, "nowhere else");
        check(page.undo() && page.inkless(), "Undo takes it all back");
        // Over a darker mark it never lightens it; it darkens a lighter one.
        page.begin(); page.setTone(10, 10, 20); page.setTone(11, 10, 200); page.finish();
        int[] over = {150, 150, 150};
        PictureImport.lay(page, 10, 10, 3, 1, over);
        check(page.tone(10, 10) == 20 && page.tone(11, 10) == 150 && page.tone(12, 10) == 150, "darkens, never lightens");
        // Off the page it is cut.
        PictureImport.lay(page, W - 2, H - 1, 4, 1, new int[]{0, 0, 0, 0});
        check(page.tone(W - 1, H - 1) == 0, "cut at the edge");
    }

    /**
     * Place lays the picture on the layer drawn on, as any mark (the owner, 2026-10-26: "drew a line, then placed an
     * image... eraser then could not erase the original line, just the image"): the eraser takes the line drawn before
     * it and the picture alike, and Undo takes the picture away, the line kept.
     */
    static void placed() throws IOException {
        DrawingBook book = new DrawingBook(new ToneDocument(W, H));
        ToneDocument page = book.current();
        line(page);
        int[] picture = new int[50 * 50];
        Arrays.fill(picture, 60);
        PictureImport.place(book, 100, 80, 50, 50, picture.clone());
        check(page.compositeTone(30, 100) == 0 && book.current().compositeTone(120, 90) == 60, "the line and the picture both there");
        EraseBothStroke eraser = new EraseBothStroke(book.current(), 60f, 0);
        for (int x = 0; x <= W; x += 10) eraser.sample(x, 100, 1f, Float.NaN, Float.NaN);
        eraser.finish();
        check(page.compositeTone(30, 100) == 255, "the eraser takes the line drawn before the picture");
        check(book.current().compositeTone(120, 90) == 255, "and the picture");
        check(book.count() == 1 && book.current() == page, "on the layer drawn on, no layer added");
        // Undo takes the picture away as any mark, the line kept.
        page.undo(); page.undo();
        check(page.compositeTone(30, 100) == 0 && page.compositeTone(120, 90) == 255, "Undo takes the picture away, the line kept");
    }
    /** A black line across the page at y 100, as one mark. */
    static void line(ToneDocument page) {
        page.begin();
        for (int x = 20; x < W - 20; x++) page.setTone(x, 100, 0);
        page.finish();
    }

    static void fitting() {
        check(Arrays.equals(PictureImport.fit(1000, 500, 1920, 2560), new int[]{0, 800, 1920, 960}), "wide fits the width, centred");
        check(Arrays.equals(PictureImport.fit(100, 400, 1920, 2560), new int[]{640, 0, 640, 2560}), "tall fits the height, centred");
        check(Arrays.equals(PictureImport.fit(3, 4, 1920, 2560), new int[]{0, 0, 1920, 2560}), "the page's shape fills it");
        boolean threw = false;
        try { PictureImport.fit(0, 5, 10, 10); } catch (IllegalArgumentException e) { threw = true; }
        check(threw, "an empty picture is refused");
    }

    static void frame() {
        PictureImport.Frame f = PictureImport.Frame.fitted(1000, 500, 1920, 2560);
        float aspect = f.w / f.h;
        PictureImport.Frame shown = PictureImport.Frame.fitted(1000, 500, new int[]{135, -40, 1920, 2400}, 1920, 2560);
        check(shown.x == 135 + 56 && Math.abs(shown.w - (1920 - 56 - 191)) < 1 && Math.abs(shown.y + shown.h / 2 - (56 + 2344) / 2f) < 1, "fitted inside what shows of the page, the handle's room in: " + shown.x + "," + shown.y + " " + shown.w);
        PictureImport.Frame none = PictureImport.Frame.fitted(1000, 500, new int[]{3000, 0, 3100, 10}, 1920, 2560);
        check(none.x == 0 && none.w == 1920, "the whole page when none of it shows");
        PictureImport.Frame far = f.moved(-5000, 9000);
        check(far.x + far.w == PictureImport.Frame.KEPT && far.y == 2560 - PictureImport.Frame.KEPT, "kept on the page: " + far.x + "," + far.y);
        check(far.w == f.w && far.h == f.h, "a move keeps its size");
        PictureImport.Frame small = f.cornerTo(f.x + 500, f.y + 10);
        check(small.x == f.x && small.y == f.y && Math.abs(small.w - 500) < .01 && Math.abs(small.w / small.h - aspect) < 1e-4, "the corner sets its width, its shape kept");
        PictureImport.Frame low = f.cornerTo(f.x + 10, f.y + 400);
        check(Math.abs(low.h - 400) < .01, "or its height, whichever reaches further");
        PictureImport.Frame tiny = f.cornerTo(f.x - 900, f.y - 900);
        check(Math.abs(tiny.h - PictureImport.Frame.SMALLEST) < .01, "never smaller than 48 px across: " + tiny.h);
        PictureImport.Frame huge = f.cornerTo(1e7f, 1e7f);
        check(huge.w <= 2560 * PictureImport.Frame.LARGEST + .01, "never past three pages");
        float cx = f.x + f.w / 2, cy = f.y + f.h / 2;
        PictureImport.Frame half = f.scaled(.5f, cx, cy, 0, 0);
        check(Math.abs(half.w - f.w / 2) < .01 && Math.abs(half.x + half.w / 2 - cx) < .01 && Math.abs(half.y + half.h / 2 - cy) < .01, "a pinch keeps the point between the fingers");
        PictureImport.Frame panned = f.scaled(1, cx, cy, 30, -20);
        check(Math.abs(panned.x - f.x - 30) < .01 && Math.abs(panned.y - f.y + 20) < .01, "and follows the fingers");
        check(f.onHandle(f.x + f.w, f.y + f.h) && f.onHandle(f.x + f.w - 60, f.y + f.h + 40) && !f.onHandle(f.x + f.w / 2, f.y + f.h / 2), "the handle at its lower right");
    }

    static void cap() {
        check(PictureImport.MOST_PDF_PAGES == 124, "124 PDF pages at most");
        check(PictureImport.pdfPages(3) == 3 && PictureImport.pdfPages(124) == 124 && PictureImport.pdfPages(300) == 124 && PictureImport.pdfPages(0) == 0, "the first 124");
        check(PictureImport.fits(0, 1000) && !PictureImport.fits(DrawingBook.MAX_PACKED_BYTES - PictureImport.SPARE - 10, 11), "inside the 200 MB");
        PageStacks s = PictureImport.stacks(3).count() == 6 ? stacksOf(PictureImport.stacks(3)) : null;
        check(s != null && s.count() == 3 && s.size(0) == 2 && s.size(2) == 2 && s.first(1) == 2, "a stack of two a PDF page");
        boolean[] links = new boolean[2 * PictureImport.MOST_PDF_PAGES];
        for (int i = 1; i < links.length; i += 2) links[i] = true;
        check(PageStacks.valid(links), "valid stacks");
    }
    static PageStacks stacksOf(TracingPaper t) {
        boolean[] links = new boolean[t.count()];
        for (int i = 0; i < links.length; i++) links[i] = (t.stepBits(i) & TracingPaper.LINKED) != 0;
        return new PageStacks(links);
    }

    static void pdfNotebook() throws IOException {
        int[] argb = new int[W * H];
        Arrays.fill(argb, 0xffffffff);
        for (int x = 20; x < 120; x++) argb[50 * W + x] = 0xff000000;
        ToneDocument pdf = PictureImport.pdfPage(W, H, PictureImport.greys(argb.clone()));
        check(pdf.locked() && pdf.tone(20, 50) == 0 && pdf.tone(20, 51) == 255, "the PDF's page, locked");
        check(!pdf.undo(), "no Undo history");
        ToneDocument read = DocumentCodec.read(new ByteArrayInputStream(DrawingBook.encode(pdf)));
        check(read.locked() && read.tone(119, 50) == 0, "locked in its file");
        // Three PDF pages, written as the import writes them, through a file as it does.
        File dir = java.nio.file.Files.createTempDirectory("pdfimport").toFile();
        try {
            File temp = new File(dir, "pages"), book = new File(dir, "book.mpaint");
            List<long[]> at = new ArrayList<>();
            try (FileOutputStream out = new FileOutputStream(temp)) {
                long offset = 0;
                for (int i = 0; i < 3; i++) {
                    byte[] b = DrawingBook.encode(pdf);
                    java.util.zip.CRC32 crc = new java.util.zip.CRC32(); crc.update(b);
                    out.write(b); at.add(new long[]{offset, b.length, crc.getValue()}); offset += b.length;
                }
            }
            PackedPage.Source source = PackedPage.Source.open(temp);
            List<PackedPage> pages = new ArrayList<>();
            for (long[] a : at) pages.add(PackedPage.in(source, a[0], (int) a[1], a[2]));
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(book))) {
                PictureImport.writeBook(out, W, H, pages, PackedPage.of(BlankPages.made(W, H).bytes), CoverDesign.DEFAULT, 20000);
            }
            source.close();
            DrawingBook opened = BookCodec.open(book);
            check(opened.count() == 6 && opened.stacks().count() == 3, "three pages of two layers: " + opened.count());
            check(opened.index() == 1 && opened.linked(1) && !opened.linked(2) && opened.current().inkless() && !opened.current().locked(), "opens on the first clear layer");
            opened.select(4);
            opened.select(0);
            check(opened.current().locked() && opened.current().tone(20, 50) == 0, "its PDF page under it, locked");
            check(!BlankPages.opened(opened) && opened.count() == 6 && opened.stacks().count() == 3, "no blank page added after the last, nor taken");
            check(opened.lastAdded() != null && opened.lastAdded().toEpochDay() == 20000, "made on its day");
            boolean threw = false;
            try { PictureImport.writeBook(new ByteArrayOutputStream(), W, H, new ArrayList<>(), pages.get(0), CoverDesign.DEFAULT, 1); } catch (IOException e) { threw = true; }
            check(threw, "no pages, no notebook");
        } finally { NotebookMigrationChecks.delete(dir); }
    }

    static void pickers() throws IOException {
        File root = java.nio.file.Files.createTempDirectory("pickers").toFile();
        try {
            long now = 1_700_000_000_000L;
            // Import's picker by folder (2026-10-29): the top the Supernote's folders that are there; in a folder its
            // folders A to Z, then its pictures and PDFs together, newest first; hidden ones and other files skipped.
            File old = file(root, "Document/old.PNG", now - 3000), pdf = file(root, "Document/Scribulor/book.pdf", now - 1000),
                    scan = file(root, "Document/scan.Pdf", now - 500), art = file(root, "Document/art/a.png", now);
            file(root, "Document/.hidden.png", now); file(root, "Document/n.note", now); file(root, "Document/.thumbs/t.png", now);
            file(root, "Android/data/p.png", now); file(root, "Download/x.png", now);
            List<File> top = PictureImport.entries(root, root);
            check(top.equals(Arrays.asList(new File(root, "Document"), new File(root, "Download"))), "the top: the Supernote's folders that are there, in their order: " + top);
            List<File> document = PictureImport.entries(root, new File(root, "Document"));
            check(document.equals(Arrays.asList(art.getParentFile(), pdf.getParentFile(), scan, old)), "folders A to Z, then pictures and PDFs newest first: " + document);
            check(PictureImport.entries(root, new File(root, "missing")).isEmpty(), "a folder gone, nothing listed");
            check(PictureImport.pdf(null, "/Download/scan.Pdf") && PictureImport.pdf("application/pdf", "/document/42")
                            && !PictureImport.pdf("image/png", "/x.png") && !PictureImport.pdf(null, null),
                    "a PDF by its type or its ending, a picture otherwise");
            check(PictureImport.path(root, pdf.getParentFile()).equals("Document/Scribulor") && PictureImport.path(root, root).isEmpty(), "a folder's path from the root");
            check(PictureImport.start(root, "Document/Scribulor").equals(pdf.getParentFile()) && PictureImport.start(root, "Document/gone/deeper").equals(new File(root, "Document"))
                            && PictureImport.start(root, "").equals(root) && PictureImport.start(root, "../etc").equals(root) && PictureImport.start(root, "/etc").equals(root),
                    "opens in the last folder, else the nearest above it still there, never outside the root");
        } finally { NotebookMigrationChecks.delete(root); }
    }
    static File file(File root, String path, long changed) throws IOException {
        File file = new File(root, path); file.getParentFile().mkdirs(); file.createNewFile(); file.setLastModified(changed);
        return file;
    }
}
