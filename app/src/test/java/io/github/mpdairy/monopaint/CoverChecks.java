package io.github.mpdairy.monopaint;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * A notebook's front and back covers (docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md, "Covers";
 * {@link CoverPattern}, {@link NotebookCovers}, {@link BookOptions}, {@link DrawingBook#FRONT}): the pattern made from the
 * name alone, the same every time and at every size (each design's own checks: {@code CoverDesignChecks}); the label and
 * the cover picture's places; a cover with nothing
 * drawn costing nothing in the file; drawing on both covers, saved and opened again; the eraser and Clear page back to
 * the marble; the cover picture following its page and taken off; the notebook's order; the PDF's; the shelf's reading;
 * the recovery file; no page shade on a cover; old notebooks opening as they were. {@code write <file>} writes a notebook
 * with drawn covers for an older version to open ({@code OlderCoversCheck}); {@code older <file>} opens one it wrote.
 */
public final class CoverChecks {
    static final int W = 240, H = 300;

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("write")) { Files.write(new File(args[1]).toPath(), covered()); return; }
        if (args.length == 2 && args[0].equals("older")) { older(new File(args[1])); return; }
        Assets.install(Assets.folder(Assets.FOLDER));
        marble(); label(); undrawn(); drawn(); erase(); picture(); order(); shade();
        File dir = Files.createTempDirectory("covers").toFile();
        String visits;
        try { copies(dir); shelf(dir); visits = shelfAgain(dir); recovery(dir); } finally { NotebookMigrationChecks.delete(dir); }
        System.out.println("PASS: covers: the pattern is made from the name alone, the same twice, another for another name, the same at a tile's size and the page's, black and white with smoothed edges, kept in memory and made again the same;"
                + "the label is a composition book's, right of the tape near the top, the name on its line in a size that fits or cut with an ellipsis, the date from the stored name, the small words left out at a tile's size; the cover picture 58% of the cover wide under it, its proportions kept; a cover with nothing drawn costs nothing (the file byte for byte as without covers) and shows its paper; "
                + "both covers drawn on are kept as pages in the options block, read back (from the file when needed) and saved again; the eraser and Clear page go back to the pattern, which stays; "
                + "the cover picture is a copy of its page on the front cover, follows that page as it changes, goes with No picture or when its page is deleted, one page at a time, drawing going over it; "
                + "the order is the front cover, the pages, the back cover, the covers counted as no pages and never copied or deleted; the PDF starts with the front cover and ends with the last page (00-cover.png first, zz-back.png last of the PNGs), drawn again only when they change; "
                + "the shelf reads the cover picture, the page size and the front cover's marks from the file's ends; the recovery file keeps the covers; no page shade on a cover; "
                + "a shelf of 8 shown again makes no cover and reads no file unchanged, its tiles found by what they show (not by their pictures' arrays, as before), a notebook changed its tile alone made again");
        System.out.println("  " + visits);
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    // ---- helpers ----

    static void mark(ToneDocument page, int x, int y, int size, int tone) {
        page.begin(); for (int dx = 0; dx < size; dx++) for (int dy = 0; dy < size; dy++) page.setTone(x + dx, y + dy, tone); page.finish();
    }
    static DrawingBook book(int pages, String name) throws IOException {
        DrawingBook book = new DrawingBook(InkWashLayers.blank(W, H));
        // No day known (no LAST record, LibraryCardChecks checks it): the file's other records alone, as before it.
        book.today = () -> -1;
        for (int i = 0; i < pages; i++) {
            if (i > 0) book.addPage();
            mark(book.current(), 10 + i * 5, 20 + i * 7, 12, 30 + 40 * i);
        }
        book.select(0);
        book.name(name);
        return book;
    }
    static byte[] bytes(DrawingBook book) throws IOException { ByteArrayOutputStream out = new ByteArrayOutputStream(); BookCodec.write(out, book.snapshot()); return out.toByteArray(); }
    static DrawingBook read(byte[] file, String name) throws IOException { DrawingBook book = BookCodec.read(new ByteArrayInputStream(file)); book.name(name); return book; }
    /** Where the label is on a {@code W × H} cover: a point inside it, inside its double rule, clear of its words. */
    static int[] onLabel() {
        NotebookCovers.Label l = NotebookCovers.label("x", W, H);
        int in = l.inset + l.rule + l.gap + l.hairline + 2;
        return new int[]{l.x + in, l.y + in};
    }
    static int composite(ToneDocument page, int x, int y) { return page.compositeTone(x, y); }
    static boolean showsPaper(ToneDocument page) {
        byte[] paper = page.paperPixels();
        for (int y = 0; y < page.height; y++) for (int x = 0; x < page.width; x++) if (page.compositeTone(x, y) != (paper[y * page.width + x] & 255)) return false;
        return true;
    }
    /** A notebook whose covers are drawn on, the front's picture page 2, in Terrazzo, written as the app saves one. */
    static byte[] covered() throws IOException {
        DrawingBook book = book(3, "2026-10-19 brisk-bean notebook");
        book.select(DrawingBook.FRONT); int[] at = onLabel(); mark(book.current(), at[0], at[1], 6, 20);
        book.select(DrawingBook.BACK); mark(book.current(), 50, 60, 10, 0);
        book.select(0); book.setCover(2); book.setDesign(CoverDesign.TERRAZZO);
        return bytes(book);
    }

    // ---- the marble ----

    static void marble() {
        CoverDesign classic = CoverDesign.DEFAULT;
        byte[] a = CoverPattern.tones(classic, "2026-10-19 brisk-bean notebook", 480, 600, false), again = CoverPattern.tones(classic, "2026-10-19 brisk-bean notebook", 480, 600, false);
        byte[] b = CoverPattern.tones(classic, "2026-10-19 brisk-bean notebooks", 480, 600, false);
        check(Arrays.equals(a, again), "The same name makes the same pattern");
        int differ = 0; for (int i = 0; i < a.length; i++) if (a[i] != b[i]) differ++;
        check(differ > a.length / 10, "Another name makes another pattern (" + differ + " pixels differ)");
        check(CoverPattern.cached(classic, "Loose pages", 96, 120, false) == CoverPattern.cached(classic, "Loose pages", 96, 120, false), "A pattern is made once and kept");
        // Made again (a new launch), the same.
        byte[] made = CoverPattern.cached(classic, "Kept", 120, 150, true).clone();
        CoverPattern.forget(); CoverTexture.forget();
        check(Arrays.equals(made, CoverPattern.cached(classic, "Kept", 120, 150, true)), "A pattern made again (a new launch) is the same");
        // At a tile's size it is the same pattern: the page-sized one, averaged down, agrees with it.
        byte[] big = CoverPattern.tones(classic, "Harbour sketches", 1920, 2400, false), tile = CoverPattern.tones(classic, "Harbour sketches", 240, 300, false);
        byte[] down = NotebookCovers.resampled(1920, 2400, (x, y) -> big[y * 1920 + x] & 255, 240, 300);
        long off = 0; for (int i = 0; i < tile.length; i++) off += Math.abs((tile[i] & 255) - (down[i] & 255));
        check(off / tile.length < 24, "A tile's pattern is the page's, smaller (mean difference " + off / tile.length + ")");
        // Black and white, ink and paper both well shown, edges smoothed through the panel's greys only.
        int ink = 0, clear = 0, grey = 0;
        Set<Integer> greys = new HashSet<>(); for (int g : GrayPalette.VALUES) greys.add(g);
        for (byte v : big) { int t = v & 255; if (t == 0) ink++; else if (t == 255) clear++; else grey++; check(greys.contains(t), "A pattern's tones are the panel's greys: " + t); }
        check(ink > big.length / 3 && clear > big.length / 10 && grey < big.length / 5 && grey > 0, "Black and white, edges smoothed (" + ink + " ink, " + clear + " clear, " + grey + " grey)");
    }

    // ---- where things go ----

    static void label() {
        for (int[] size : new int[][]{{1920, 2400}, {1404, 1872}, {375, 469}, {240, 300}}) {
            int w = size[0], h = size[1];
            for (String name : new String[]{"2026-10-19 brisk-bean notebook", "Loose pages", "2025-10-19 an extraordinarily long notebook name that runs past its line"}) {
                NotebookCovers.Label l = NotebookCovers.label(name, w, h);
                int spine = CoverPattern.spine(w);
                check(l.x > spine && l.x + l.width < w && l.y > 0 && l.bottom() < h / 2, "The label is near the top, right of the tape, inside the cover");
                check(Math.abs(l.x + l.width / 2.0 - (spine + (w - spine) / 2.0)) <= 1, "centred over the printed part");
                check(l.lines.length == 3 && l.lines[0] < l.lines[1] && l.lines[1] < l.lines[2] && l.lines[2] + l.line < l.bottom() - l.inset && l.lineEnd < l.x + l.width - l.inset, "Its three writing lines inside its rule");
                check(NotebookCovers.width(l.name, l.nameSize, 0) <= l.lineEnd - l.lineStart, "The name fits its line: " + l.name);
                check(l.nameSize >= w * NotebookCovers.NAME_LEAST * .94 - .01 && l.nameSize <= w * NotebookCovers.NAME_MOST + .01, "at a size between the least (a step under, as the prototype) and the most");
                boolean tiny = w < NotebookCovers.TINY;
                List<String> words = new ArrayList<>(); for (NotebookCovers.Words word : l.words) words.add(word.text);
                check(words.contains(l.name) && words.contains("C") && words.contains("K"), "COMPOSITION BOOK and the name on the label: " + words);
                check(words.contains("A") == !tiny && words.contains("J") == !tiny && words.contains("D") == !tiny, "NAME, SUBJECT and DATE but at a tile's size: " + words);
                String date = NotebookCovers.dated(name);
                check(date.isEmpty() || words.contains(date) == !tiny, "The date on its line but at a tile's size");
                for (NotebookCovers.Words word : l.words)
                    check(word.x >= l.x + l.inset && word.x + NotebookCovers.width(word.text, word.size, 0) <= l.x + l.width - l.inset + .5 && word.baseline < l.bottom() - l.inset && word.baseline - word.size > l.y + l.inset - 1,
                            "Every word inside the rule: " + word.text);
            }
            int[] box = NotebookCovers.pictureBox(w, h, w, h);
            NotebookCovers.Label l = NotebookCovers.label("Loose pages", w, h);
            int spine = CoverPattern.spine(w);
            check(box[2] <= w * NotebookCovers.PICTURE_WIDTH + 1 && Math.abs(box[3] - box[2] * (double) h / w) <= 1.5, "The cover picture is at most 58% of the cover wide, the page's proportions kept");
            check(Math.abs(box[0] + box[2] / 2.0 - (spine + (w - spine) / 2.0)) <= 1 && box[1] > l.bottom() && box[1] + box[3] < h, "The cover picture is centred under the label, inside the cover");
        }
        check(NotebookCovers.label("2026-10-19 brisk-bean notebook", 1920, 2400).name.equals("brisk-bean notebook") && NotebookCovers.dated("2026-10-19 brisk-bean notebook").equals("19 Oct 2026"),
                "The name line says the name after its date, the date line the date: " + NotebookCovers.dated("2026-10-19 brisk-bean notebook"));
        check(NotebookCovers.label("Loose pages", 1920, 2400).name.equals("Loose pages") && NotebookCovers.dated("Loose pages").isEmpty(), "A name without a date: all of it, no date");
        String cut = NotebookCovers.label("2025-10-19 an extraordinarily long notebook name that runs past its line", 1920, 2400).name;
        check(cut.endsWith("…") && cut.length() < 50, "A long name is cut with an ellipsis: " + cut);
        int[] wide = NotebookCovers.pictureBox(1920, 2400, 2400, 1920);
        check(wide[2] <= 1920 * NotebookCovers.PICTURE_WIDTH + 1 && wide[1] + wide[3] < 2400, "A wide page's picture still fits");
    }

    // ---- a cover with nothing drawn ----

    static void undrawn() throws IOException {
        DrawingBook book = book(3, "Loose pages");
        book.markSaved(book.snapshot());
        byte[] before = bytes(book);
        book.select(DrawingBook.FRONT);
        ToneDocument front = book.current();
        check(front.paperPixels() != null && front.unmarked() && showsPaper(front), "The front cover is its paper, nothing on it");
        check(Arrays.equals(Arrays.copyOf(front.paperPixels(), 0), new byte[0]) && front.paperPixels().length == W * H, "Its paper is the page's size");
        book.select(DrawingBook.BACK);
        check(Arrays.equals(book.current().paperPixels(), CoverPattern.cached(CoverDesign.DEFAULT, "Loose pages", W, H, true)), "The back cover is its pattern alone, Classic Black Marble");
        book.select(0);
        check(!book.unsaved(), "Looking at the covers changes nothing");
        byte[] after = bytes(book);
        check(Arrays.equals(before, after), "Covers with nothing drawn cost nothing: the file is byte for byte as without them");
        check(BookOptions.find(BookOptions.of(after), 0, BookPictures.start(after, BookPictures.readLong32(after, after.length - 22 + 16))) == null, "No options block for them");
        // Drawn on, then cleared: nothing kept again.
        book.select(DrawingBook.FRONT); mark(book.current(), 30, 30, 5, 0); check(book.current().clearPage(), "Clear page on the front cover");
        book.select(0);
        check(Arrays.equals(bytes(book), before), "A cover cleared is no cover in the file");
    }

    // ---- drawing on the covers ----

    static void drawn() throws IOException {
        byte[] file = covered();
        DrawingBook book = read(file, "2026-10-19 brisk-bean notebook");
        check(book.count() == 3 && book.coverDrawn(DrawingBook.FRONT) && book.coverDrawn(DrawingBook.BACK) && book.cover() == 2 && book.design() == CoverDesign.TERRAZZO, "Both covers, the cover picture and the design are read back, the pages still three");
        BookCodec.Pages pages = BookCodec.readPages(new ByteArrayInputStream(file));
        ToneDocument stored = DocumentCodec.read(new ByteArrayInputStream(pages.covers.pages[0].bytes()));
        check(stored.paperPixels() == null && !stored.unmarked(), "A cover's page is stored as a page, its marks without its paper");
        book.select(DrawingBook.FRONT);
        int[] at = onLabel();
        check(composite(book.current(), at[0] + 2, at[1] + 2) == 20 && book.current().paperPixels() != null, "The front cover's drawing is back, over its label");
        book.select(DrawingBook.BACK);
        check(composite(book.current(), 55, 65) == 0, "The back cover's drawing is back");
        // From a file: the covers read from it when needed, and saved again from it as they are.
        File dir = Files.createTempDirectory("cover").toFile();
        try {
            File f = new File(dir, "n.mpaint"); Files.write(f.toPath(), file);
            DrawingBook opened = BookCodec.open(f); opened.name("2026-10-19 brisk-bean notebook");
            check(opened.packed(DrawingBook.FRONT) != null && !opened.packed(DrawingBook.FRONT).inMemory(), "An opened notebook's cover is read from its file, not held");
            opened.select(DrawingBook.FRONT);
            check(composite(opened.current(), at[0] + 2, at[1] + 2) == 20, "and decodes as drawn");
            opened.select(0);
            ByteArrayOutputStream out = new ByteArrayOutputStream(); BookCodec.write(out, opened.snapshot());
            DrawingBook again = read(out.toByteArray(), "2026-10-19 brisk-bean notebook");
            check(again.coverDrawn(DrawingBook.FRONT) && again.coverDrawn(DrawingBook.BACK) && again.cover() == 2, "Saved again, it keeps its covers");
            if (opened.packed(0).file() != null) opened.packed(0).file().close();
        } finally { NotebookMigrationChecks.delete(dir); }
        // The covers count toward the notebook's size, and its file stays within what readers before covers accept.
        check(file.length < BookCodec.MAX_ARCHIVE_BYTES, "A notebook with covers is within the readers' limit");
    }

    // ---- the eraser and Clear page ----

    static void erase() throws IOException {
        DrawingBook book = book(2, "Loose pages");
        book.select(DrawingBook.FRONT);
        ToneDocument front = book.current();
        mark(front, 40, 100, 30, 0);
        mark(front, 100, 150, 30, 136);
        check(!showsPaper(front), "Marks show on the cover");
        front.begin(); for (int x = 40; x < 70; x++) for (int y = 100; y < 130; y++) front.eraseTone(x, y, 1); front.finish();
        byte[] paper = front.paperPixels();
        boolean back = true; for (int x = 40; x < 70; x++) for (int y = 100; y < 130; y++) back &= front.compositeTone(x, y) == (paper[y * W + x] & 255);
        check(back, "The eraser rubs back to the pattern");
        check(PageClear.offered(front) && front.clearPage() && showsPaper(front) && front.paperPixels() == paper, "Clear page leaves the pattern");
        check(front.undo() && !showsPaper(front), "Undo gives the marks back");
    }

    // ---- the cover picture ----

    static void picture() throws IOException {
        DrawingBook book = book(3, "Loose pages");
        book.select(DrawingBook.FRONT);
        byte[] plain = book.current().paperPixels().clone();
        int[] box = NotebookCovers.pictureBox(W, H, W, H);
        book.select(1); book.setCover(1);
        check(book.unsaved(), "Using a page on the cover is a change to save");
        book.select(DrawingBook.FRONT);
        byte[] with = book.current().paperPixels();
        check(!Arrays.equals(plain, with), "The page is glued on the front cover");
        // A thin ink rule round it, the page inside it: the page's mark (tone 70 at 15..27, 27..39) in its place, smaller.
        int rule = Math.max(1, Math.round(W / (float) NotebookCovers.PICTURE_RULE));
        check((with[(box[1] - rule) * W + box[0] + box[2] / 2] & 255) == 0, "A thin ink rule round the picture");
        double s = box[2] / (double) W;
        int mx = box[0] + (int) Math.round(20 * s), my = box[1] + (int) Math.round(32 * s);
        check(Math.abs((with[my * W + mx] & 255) - 70) <= 20 && (with[(box[1] + 2) * W + box[0] + 2] & 255) == 255, "The page's copy, smaller, inside it (" + (with[my * W + mx] & 255) + ")");
        // It follows its page: page 2 drawn on, the front cover shows it.
        book.select(1); mark(book.current(), 0, 200, 240, 0);
        book.select(DrawingBook.FRONT);
        byte[] followed = book.current().paperPixels();
        int fy = box[1] + (int) Math.round(240 * s);
        check((followed[fy * W + box[0] + box[2] / 2] & 255) == 0 && (with[fy * W + box[0] + box[2] / 2] & 255) == 255, "The cover picture follows its page as it changes");
        // Drawing on the cover goes over it.
        mark(book.current(), mx - 2, my - 2, 4, 0);
        check(book.current().compositeTone(mx, my) == 0, "Drawing on the cover goes over its picture");
        // One page at a time; No picture takes it off; deleting its page does too.
        book.select(0); book.setCover(2); book.select(DrawingBook.FRONT);
        check(book.cover() == 2 && !Arrays.equals(book.current().paperPixels(), followed), "Another page replaces it: one at a time");
        book.setCover(-1);
        check(book.refreshCovers(), "No picture: the front cover drawn on shows its paper again");
        check(Arrays.equals(book.current().paperPixels(), plain), "No picture takes it off, the pattern and label as they were");
        book.select(0); book.setCover(1); book.select(1); book.deletePage();
        check(book.cover() == -1, "Deleting the page takes it off the cover");
        // Saved and read: the shelf finds the picture's page and its picture.
        book.setCover(1);
        DrawingBook again = read(bytes(book), "Loose pages");
        check(again.cover() == 1, "The cover picture is saved as its page");
    }

    // ---- the order ----

    static void order() throws IOException {
        DrawingBook book = book(3, "Loose pages");
        List<Integer> seen = new ArrayList<>();
        for (int at = DrawingBook.FRONT; at != DrawingBook.NONE; at = book.after(at)) { seen.add(at); book.select(at); }
        check(seen.equals(Arrays.asList(DrawingBook.FRONT, 0, 1, 2, DrawingBook.BACK)), "The order: the front cover, the pages, the back cover: " + seen);
        List<Integer> back = new ArrayList<>();
        for (int at = DrawingBook.BACK; at != DrawingBook.NONE; at = book.before(at)) back.add(at);
        Collections.reverse(back);
        check(back.equals(seen), "and back again");
        check(book.count() == 3 && book.index() == DrawingBook.BACK, "Covers aren't counted as pages");
        try { book.deletePage(); throw new AssertionError("A cover is deleted"); } catch (IOException expected) { check(expected.getMessage().equals(DrawingBook.COVER), "A cover isn't deleted"); }
        try { book.duplicatePage(); throw new AssertionError("A cover is copied"); } catch (IOException expected) { }
        try { book.deletePage(DrawingBook.FRONT); throw new AssertionError("A cover is deleted by place"); } catch (IllegalArgumentException expected) { }
        book.addPage();
        check(book.count() == 4 && book.after(3) == DrawingBook.BACK, "A page added goes before the back cover");
    }

    static void shade() throws IOException {
        DrawingBook book = book(1, "Loose pages");
        book.select(DrawingBook.FRONT);
        check(PageShade.offer(book.current(), GrayPalette.VALUES, 3, 0) == null, "No page shade on a cover: its marble is its paper");
        book.select(0);
        ToneDocument blank = new ToneDocument(W, H);
        check(PageShade.offer(blank, GrayPalette.VALUES, 3, 0) != null, "A page still offers one");
    }

    // ---- the copies ----

    static void copies(File dir) throws Exception {
        DrawingBook book = read(covered(), "Loose pages");
        DrawingBook.Snapshot s = book.snapshot();
        List<PackedPage> pages = s.packed();
        PackedPage[] covers = s.packedCovers();
        File export = new File(dir, "EXPORT");
        NotebookExport.Done done = NotebookExport.update(export, "Loose pages", pages, null, new NotebookExport.Covers("Loose pages", CoverDesign.DEFAULT, W, H, covers[0], covers[1], s.cover));
        check(done.drawn == 5, "Both covers and three pages are drawn: " + done.drawn);
        File folder = new File(export, "Loose pages");
        File front = new File(folder, NotebookExport.FRONT_PNG), back = new File(folder, NotebookExport.BACK_PNG);
        check(front.isFile() && back.isFile() && new File(folder, "001.png").isFile(), "The covers' PNGs beside the pages'");
        String[] names = folder.list(); Arrays.sort(names);
        check(names[1].equals(NotebookExport.FRONT_PNG) && names[0].equals(NotebookPdf.STATE) && names[names.length - 1].equals(NotebookExport.BACK_PNG), "00-cover.png sorts first and zz-back.png last: " + Arrays.toString(names));
        String pdf = new String(Files.readAllBytes(NotebookExport.pdf(export, "Loose pages").toPath()), java.nio.charset.StandardCharsets.ISO_8859_1);
        check(pdf.contains("/Type /Pages /Count 4 "), "The PDF has the front cover and the three pages (the back cover's PNG only: pages keep their numbers)");
        // The front cover first: the PDF's first image is the cover's PNG's image data.
        NotebookExport.Png first = NotebookExport.read(Files.readAllBytes(front.toPath()));
        int firstImage = pdf.indexOf("stream\n", pdf.indexOf("/Subtype /Image")) + 7;
        check(Arrays.equals(Arrays.copyOfRange(pdf.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), firstImage, firstImage + first.data.length), first.data), "The PDF starts with the front cover");
        NotebookExport.Png last = NotebookExport.read(Files.readAllBytes(new File(folder, "003.png").toPath()));
        int lastImage = pdf.indexOf("stream\n", pdf.lastIndexOf("/Subtype /Image")) + 7;
        check(Arrays.equals(Arrays.copyOfRange(pdf.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), lastImage, lastImage + last.data.length), last.data), "and ends with the last page");
        BufferedImage image = javax.imageio.ImageIO.read(front);
        int[] at = onLabel();
        check(image.getWidth() == W && (image.getRGB(at[0] + 2, at[1] + 2) & 255) < 80, "The front cover's PNG shows its drawing over its label");
        check(first.crc == NotebookCovers.key(true, CoverDesign.DEFAULT, "Loose pages", W, H, covers[0].crc, pages.get(2).crc), "Its PNG carries what it is drawn from");
        // Nothing changed: nothing drawn again. Renamed: the covers are drawn again, the pages kept.
        check(NotebookExport.update(export, "Loose pages", pages, null, new NotebookExport.Covers("Loose pages", CoverDesign.DEFAULT, W, H, covers[0], covers[1], s.cover)).drawn == 0, "Unchanged covers aren't drawn again");
        check(NotebookExport.update(export, "Loose pages", pages, null, new NotebookExport.Covers("Harbour", CoverDesign.DEFAULT, W, H, covers[0], covers[1], s.cover)).drawn == 2, "A new name draws the covers again");
        check(NotebookExport.update(export, "Loose pages", pages, null, new NotebookExport.Covers("Harbour", CoverDesign.DEFAULT, W, H, covers[0], covers[1], -1)).drawn == 1, "Another cover picture draws the front again");
    }

    // ---- the shelf ----

    static void shelf(File dir) throws Exception {
        File f = new File(dir, "shelf.mpaint");
        DrawingBook book = read(covered(), "2026-10-19 brisk-bean notebook");
        book.select(2); book.offerPicture(book.pictureKey(2), PagePicture.of(book.current().layerSnapshot()).encode());
        book.select(DrawingBook.FRONT);
        book.offerPicture(book.pictureKey(DrawingBook.FRONT), PagePicture.of(book.current().layerSnapshot()).encode());
        check(PagePicture.decode(book.picture(DrawingBook.FRONT)).marksAlone(), "A cover's picture is of its marks alone");
        Files.write(f.toPath(), bytes(book));
        BookPictures.Cover c = BookPictures.readCover(f);
        check(c.pages == 3 && c.cover == 2 && Arrays.equals(c.picture, book.picture(2)) && c.width == W && c.height == H, "The shelf reads the cover picture's page and picture and the page size");
        check(c.frontDrawn && Arrays.equals(c.front, book.picture(DrawingBook.FRONT)), "and the front cover's marks");
        byte[] tile = NotebookCovers.shown(true, c.design, "2026-10-19 brisk-bean notebook", W / 2, H / 2, PagePicture.decode(c.picture), PagePicture.decode(c.front));
        int[] at = onLabel();
        check((tile[(at[1] / 2 + 1) * (W / 2) + at[0] / 2 + 1] & 255) < 120, "The tile shows the cover's drawing over its label");
        byte[] bare = NotebookCovers.shown(false, CoverDesign.DEFAULT, "2026-10-19 brisk-bean notebook", W / 2, H / 2, null, null);
        check(Arrays.equals(bare, CoverPattern.cached(CoverDesign.DEFAULT, "2026-10-19 brisk-bean notebook", W / 2, H / 2, true)), "A bare back cover's tile is its pattern");
        // A notebook from before covers: no cover picture unless one was chosen then (not the first page).
        DrawingBook old = book(3, "Old");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DrawingBook.Snapshot s = old.snapshot();
        BookCodec.write(out, W, H, s.packed(), 0, new int[3], 2, s.pictures);
        Files.write(f.toPath(), out.toByteArray());
        check(BookPictures.readCover(f).cover == 2 && read(out.toByteArray(), "Old").cover() == 2, "A page chosen as the cover before covers is the cover picture");
        out.reset(); BookCodec.write(out, W, H, s.packed(), 0, new int[3], 0, s.pictures);
        Files.write(f.toPath(), out.toByteArray());
        check(BookPictures.readCover(f).cover == -1 && read(out.toByteArray(), "Old").cover() == -1, "The first page (everyone's before) isn't");
    }

    // ---- the shelf shown again ----

    /** The shelf's tiles on the Manta: 397 × 569 px. */
    static final int TILE_W = 397, TILE_H = 569;
    /** One shelf: each file's cover read as the shelf reads it, then each tile found among those shown by its key or made ({@code NotebookScreens.composed}); the tiles made. */
    static int visit(File[] files, Map<String, byte[]> shown, boolean byIdentity) throws IOException {
        int made = 0;
        for (File f : files) {
            BookPictures.Cover c = byIdentity ? BookPictures.readCover(f) : BookPictures.cover(f);
            String name = f.getName().replace(".mpaint", "");
            String key = byIdentity ? System.identityHashCode(c.picture) + " " + System.identityHashCode(c.front) + " " + c.design.key + name
                    : NotebookCovers.shownKey(true, c.design, name, TILE_W, TILE_H, c.picture, c.front);
            if (shown.containsKey(key)) continue;
            shown.put(key, NotebookCovers.shown(true, c.design, name, TILE_W, TILE_H, c.picture == null ? null : PagePicture.decode(c.picture), c.front == null ? null : PagePicture.decode(c.front)));
            made++;
        }
        return made;
    }
    /**
     * A shelf of 8 notebooks (three with a cover picture and their front covers drawn on), in several designs, shown at a
     * launch, then shown again: its covers' patterns made once; back to it (the shelf's tiles kept), the files unchanged
     * aren't read again and every tile is found, none made, as it wasn't when tiles were found by their pictures' arrays
     * (as before, the files read again). Timed.
     */
    static String shelfAgain(File dir) throws Exception {
        File shelf = new File(dir, "shelf");
        shelf.mkdirs();
        CoverDesign[] designs = {CoverDesign.CLASSIC, CoverDesign.CLASSIC, CoverDesign.TERRAZZO, CoverDesign.CLASSIC, CoverDesign.SWIRL, CoverDesign.WOOD, CoverDesign.CLASSIC, CoverDesign.CRACKLE};
        File[] files = new File[designs.length];
        for (int i = 0; i < files.length; i++) {
            String name = "2026-10-" + (11 + i) + " notebook " + i;
            DrawingBook book = i % 3 == 0 ? read(covered(), name) : book(2, name);
            book.setDesign(designs[i]);
            if (i % 3 == 0) {
                book.select(2); book.offerPicture(book.pictureKey(2), PagePicture.of(book.current().layerSnapshot()).encode());
                book.select(DrawingBook.FRONT); book.offerPicture(book.pictureKey(DrawingBook.FRONT), PagePicture.of(book.current().layerSnapshot()).encode());
            }
            files[i] = new File(shelf, name + ".mpaint");
            Files.write(files[i].toPath(), bytes(book));
        }
        try {
            // A launch: nothing in memory, not even the designs' sheets.
            CoverPattern.forget(); CoverTexture.forget();
            int makes = CoverPattern.MAKES.get(), reads = BookPictures.READS.get();
            Map<String, byte[]> shown = new HashMap<>();
            long t0 = System.nanoTime();
            int made = visit(files, shown, false);
            double first = (System.nanoTime() - t0) / 1e6;
            check(made == 8 && CoverPattern.MAKES.get() - makes == 8 && BookPictures.READS.get() - reads == 8, "The first shelf makes each cover once (" + made + ")");
            // The tiles' covers kept, but not the shown tiles (the shelf's view made again): none made.
            makes = CoverPattern.MAKES.get();
            visit(files, new HashMap<>(), false);
            check(CoverPattern.MAKES.get() == makes, "Its covers' patterns made once, kept (" + (CoverPattern.MAKES.get() - makes) + " made again)");
            // Back to the shelf: the files unchanged aren't read again, every tile found by what it shows.
            makes = CoverPattern.MAKES.get(); reads = BookPictures.READS.get();
            t0 = System.nanoTime();
            made = visit(files, shown, false);
            double again = (System.nanoTime() - t0) / 1e6;
            check(made == 0 && CoverPattern.MAKES.get() == makes && BookPictures.READS.get() == reads, "Back to the shelf, nothing read or made (" + made + " made, " + (BookPictures.READS.get() - reads) + " read)");
            // A file changed: read again, and only its tile made again.
            DrawingBook changed = read(Files.readAllBytes(files[1].toPath()), "x"); changed.setDesign(CoverDesign.BOLD);
            Files.write(files[1].toPath(), bytes(changed));
            files[1].setLastModified(files[1].lastModified() + 2000);
            check(visit(files, shown, false) == 1, "A notebook changed, its tile alone made again");
            // As before: the shelf read the files again and found tiles by their pictures' arrays, so made those with pictures again.
            Map<String, byte[]> before = new HashMap<>();
            visit(files, before, true);
            int remade = visit(files, before, true);
            check(remade == 3, "Found by their pictures' arrays, tiles with pictures were made again on every return (" + remade + ")");
            return String.format(Locale.ROOT, "The shelf of 8 on the Manta (tiles %d x %d), desktop Java, ms: at a launch %.0f (every file's cover read, 5 designs' sheets read, each tile made), back to it %.2f (nothing read or made; before, %d of 8 made again on each return)",
                    TILE_W, TILE_H, first, again, remade);
        } finally { CoverPattern.forget(); }
    }

    // ---- the recovery file ----

    static void recovery(File dir) throws Exception {
        File f = new File(dir, "Notebook.mpaint");
        Files.write(f.toPath(), covered());
        DrawingBook book = BookCodec.open(f); book.name("Notebook");
        book.select(DrawingBook.BACK); mark(book.current(), 100, 100, 8, 60);
        ToneDocument drawn = book.current();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RecoveryCodec.write(out, book.snapshot(), "Notebook");
        check(out.size() < 2 * covered().length, "The recovery names the front cover in the notebook's file");
        RecoveryCodec.Recovered back = RecoveryCodec.read(new ByteArrayInputStream(out.toByteArray()), path -> f);
        DrawingBook r = back.book; r.name("Notebook");
        check(r.cover() == 2 && r.coverDrawn(DrawingBook.FRONT) && r.coverDrawn(DrawingBook.BACK) && r.design() == CoverDesign.TERRAZZO && r.unsaved() == book.unsaved(), "The recovery keeps the covers, the cover picture and the design");
        r.select(DrawingBook.BACK);
        boolean same = true;
        for (int y = 96; y < 112; y++) for (int x = 48; x < 112; x++) same &= r.current().compositeTone(x, y) == drawn.compositeTone(x, y);
        check(same && !showsPaper(r.current()), "The back cover drawn since the save is recovered with what it had");
        if (book.packed(0) != null && book.packed(0).file() != null) book.packed(0).file().close();
    }

    // ---- an older version's notebook ----

    /** A notebook an older version wrote (OlderCoversCheck): it opens as it was, gets covers, and with nothing drawn on them is written again byte for byte. */
    static void older(File file) throws Exception {
        byte[] bytes = Files.readAllBytes(file.toPath());
        DrawingBook book = read(bytes, "Loose pages");
        check(book.count() == 3 && !book.coverDrawn(DrawingBook.FRONT) && book.cover() == -1 && book.design() == CoverDesign.CLASSIC, "An older version's notebook opens with its pages and plain covers, Classic Black Marble");
        book.select(DrawingBook.FRONT);
        check(book.current().paperPixels() != null && showsPaper(book.current()), "and gets its covers");
        book.select(0);
        for (int i = 0; i < 3; i++) { book.select(i); check(book.current().compositeTone(12 + 5 * i, 22 + 7 * i) == 30 + 40 * i, "Its page " + (i + 1) + " reads as it was written"); }
        book.select(0);
        check(!book.unsaved() && Arrays.equals(bytes(book), bytes), "Unchanged, it is written byte for byte as the older version wrote it");
        System.out.println("PASS: a notebook an older version (2a352ed) wrote opens with its pages and plain covers, written again byte for byte while nothing is drawn on them");
    }

    // ---- the desktop's letters ----

    /** The label's letters on the desktop: iA Writer Duo through Java2D, as {@code CoverLettering} draws them on the device. */
    static final class Letters implements NotebookCovers.Lettering {
        static final Font BOLD, REGULAR;
        static {
            try {
                BOLD = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/ia_writer_duo_bold.ttf"));
                REGULAR = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/ia_writer_duo_regular.ttf"));
            } catch (Exception e) { throw new IllegalStateException(e); }
        }
        @Override public void letter(byte[] tones, int width, int height, NotebookCovers.Label label) {
            BufferedImage letters = new BufferedImage(label.width, label.height, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = letters.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setColor(java.awt.Color.WHITE);
            for (NotebookCovers.Words words : label.words) {
                g.setFont((words.weight >= 600 ? BOLD : REGULAR).deriveFont(words.size));
                g.drawString(words.text, words.x - label.x, words.baseline - label.y);
            }
            g.dispose();
            byte[] a = (byte[]) letters.getRaster().getDataElements(0, 0, label.width, label.height, null);
            for (int y = 0; y < label.height; y++)
                for (int x = 0; x < label.width; x++) {
                    int alpha = a[y * label.width + x] & 255, o = (label.y + y) * width + label.x + x;
                    if (alpha > 0 && label.x + x < width && o >= 0 && o < tones.length) tones[o] = (byte) (((tones[o] & 255) * (255 - alpha) + 127) / 255);
                }
        }
    }
}
