package io.github.mpdairy.monopaint;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * Layers as stacks of clear pages (docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md, "Stage 3c: layers are
 * stacked pages"; {@link PageStacks}, {@link TracingPaper#LINKED}, {@code BookOptions}' STAK, the recovery's TSRK,
 * {@link LayerEdits}): the stacks the links make; a new layer above or below, drawn on, four at most; Duplicate stacked
 * on its original; Delete and Merge Down each one Undo step, back exactly; a move takes a stack whole; the guides the
 * bottom page's; what shows through the layer drawn on (its stack's other layers Solid, Hidden left out, other stacks at
 * the Paper's strength); STAK only when a page is stacked, round-tripped through the file and the recovery, a bad one
 * ignored, a reader without it (an older build) seeing every layer a page; the shelf, the copies (a PNG a stack, its
 * layers flattened) and the count a stack one page; the page bar's + after a stack, never inside it, and a blank page with
 * layers kept on opening.
 * {@code write <file>} writes a stacked notebook for the version before stacks ({@code OlderOptionsCheck}, 2ddb2a4);
 * {@code older <file>} opens what that version saved again.
 */
public final class PageStacksChecks {
    static final int W = 60, H = 40;

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("write")) { write(new File(args[1])); return; }
        if (args.length == 2 && args[0].equals("older")) { older(new File(args[1])); return; }
        stacks();
        add();
        undo();
        moves();
        guides();
        ghosts();
        records();
        copies();
        blankEnd();
        over();
        lands();
        neighbours();
        decodes();
        edges();
        System.out.println("PASS: layers above the one drawn on cover it (a second plane over the page's layers, white paint too), layers below and other stacks under it, a lower layer's white never covering an upper one, the eraser's preview and Real Mirror shown the same way (GhostPlane.over); a turn lands on the layer of a stack drawn on last, else its top; a neighbouring stack shows flattened at Page Transparency's strength (an untouched one exactly Page Before's, its own layers' steps inside it); at most six pages decoded a turn, the stack drawn on's first");
        System.out.println("PASS: layers are stacked pages: a run of pages linked to the one below is a stack, four at most, the first never linked; a new layer above or below the one drawn on, drawn on, refused at four and on a cover; Duplicate stacked on its original; Delete and Merge Down each one Undo step that puts the page, its link, its picture and the layer under it back exactly, ready only while nothing changed since; a move takes a stack whole, either way; every layer shows the bottom page's guides, which go to the next layer when the bottom goes; the layer drawn on sees its stack's other layers Solid (their own step, Hidden left out) and other stacks at the Paper's strength times theirs; STAK only when a page is stacked (an unstacked notebook byte for byte as before), round-tripped through the file and the recovery, a bad record ignored, a reader without it seeing each layer a page; the shelf, the copies (a PNG and a PDF page a stack, its layers flattened under the bottom's guides, a Hidden layer left out) and the count say a stack is one page; the page bar's + puts a page after the whole stack drawn on, never inside it, and opening keeps a blank last page with layers; a layer below the bottom keeps the stack's own guides, the bottom deleted passes its guides, ghost pin and cover picture to the layer on it and Undo puts them back; Undo of a layer delete in a full notebook puts it back; one page has no layer to delete or merge");
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /** A page with a black mark at ({@code x}, {@code y}). */
    static void mark(ToneDocument page, int x, int y) { page.begin(); page.setTone(x, y, 0); page.finish(); }
    /** {@code links} as tracing paper for their pages. */
    static TracingPaper linked(boolean... links) {
        int[] s = new int[links.length];
        for (int i = 0; i < s.length; i++) if (links[i]) s[i] = TracingPaper.LINKED;
        return new TracingPaper(TracingPaper.PLAIN, -1, s);
    }
    /** A book of {@code n} pages, page i marked at (i, i), drawn on the first. */
    static DrawingBook book(int n) throws IOException {
        DrawingBook book = new DrawingBook(InkWashLayers.blank(W, H));
        mark(book.current(), 0, 0);
        for (int i = 1; i < n; i++) { book.addPage(); mark(book.current(), i, i); }
        book.select(0);
        return book;
    }
    /** What page {@code i} holds: its marks' tones, read decoded. */
    static String holds(DrawingBook book, int i) throws IOException {
        int was = book.index();
        book.select(i);
        StringBuilder s = new StringBuilder();
        ToneDocument p = book.current();
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { int t = p.compositeTone(x, y); if (t != 255) s.append(x).append(',').append(y).append('=').append(t).append(' '); }
        book.select(was);
        return s.toString();
    }
    static List<String> all(DrawingBook book) throws IOException {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < book.count(); i++) out.add(holds(book, i) + (book.linked(i) ? "^" : ""));
        return out;
    }

    // ---- the stacks the links make ----

    static void stacks() {
        PageStacks s = linked(false, true, true, false, false, true).stacks();
        check(s.count() == 3 && s.pages() == 6, "Six pages, three stacks");
        check(s.first(0) == 0 && s.last(0) == 2 && s.size(0) == 3 && s.first(1) == 3 && s.size(1) == 1 && s.first(2) == 4 && s.last(2) == 5, "Each stack a run, bottom first");
        check(s.of(2) == 0 && s.of(3) == 1 && s.of(5) == 2 && s.bottom(2) == 0 && s.bottom(5) == 4, "A page's stack and its bottom");
        check(s.of(DrawingBook.FRONT) == DrawingBook.FRONT && s.page(DrawingBook.BACK) == DrawingBook.BACK && s.page(0) == 2, "A cover stays itself; a stack is turned to on its top layer");
        check(s.stacked(1) && !s.stacked(3), "Stacked: a layer of a stack of two or more");
        check(s.after(DrawingBook.FRONT) == 0 && s.after(0) == 1 && s.after(2) == DrawingBook.BACK && s.after(DrawingBook.BACK) == DrawingBook.NONE
                && s.before(DrawingBook.BACK) == 2 && s.before(0) == DrawingBook.FRONT && s.before(DrawingBook.FRONT) == DrawingBook.NONE, "A turn goes to the next stack, the covers at the ends");
        check(PageStacks.valid(new boolean[]{false, true, true, true}) && !PageStacks.valid(new boolean[]{false, true, true, true, true}) && !PageStacks.valid(new boolean[]{true, false}),
                "Four layers at most, the first page never stacked");
        check(TracingPaper.plain(4).stacks().count() == 4, "No links: every page its own");
    }

    // ---- a new layer, Duplicate ----

    static void add() throws Exception {
        DrawingBook book = book(2);
        check(book.addLayer(true) && book.index() == 1 && book.linked(1) && book.count() == 3 && book.current().unmarked(), "A layer above: a clear page stacked on the page drawn on, drawn on");
        check(holds(book, 0).startsWith("0,0") && holds(book, 2).startsWith("1,1") && !book.linked(2), "The pages either side as they were, the next page its own");
        book.select(0);
        check(book.addLayer(false) && book.index() == 0 && !book.linked(0) && book.linked(1) && book.linked(2) && book.stacks().size(0) == 3, "A layer below: the bottom now, the page drawn on stacked on it");
        check(holds(book, 1).startsWith("0,0"), "and the page drawn on keeps its marks");
        book.select(2);
        check(book.addLayer(true) && book.stacks().size(0) == 4, "Four layers");
        check(!book.addLayer(true) && !book.addLayer(false) && book.count() == 5, "No fifth: refused, nothing changed");
        book.select(DrawingBook.FRONT);
        check(!book.addLayer(true), "None on a cover");
        DrawingBook copy = book(2);
        copy.duplicatePage();
        check(copy.index() == 1 && copy.linked(1) && copy.stacks().count() == 2 && holds(copy, 1).equals(holds(copy, 0)), "Duplicate: a copy stacked on its original, drawn on");
    }

    // ---- Delete and Merge Down, each one Undo step ----

    static void undo() throws Exception {
        // Delete the layer drawn on: the layer below is drawn on; Undo puts it back, drawn on.
        DrawingBook book = book(3);
        book.setTracing(linked(false, true, false));
        book.select(1);
        List<String> before = all(book);
        LayerEdits.Undo gone = LayerEdits.delete(book, 1);
        check(gone != null && book.count() == 2 && book.index() == 0 && !book.linked(1) && book.stacks().count() == 2, "Delete: the layer goes, the one below drawn on");
        check(gone.ready(book), "Undo can take it back");
        gone.apply(book);
        check(all(book).equals(before) && book.index() == 1, "Undo: back as it was, in its place, linked, drawn on: " + all(book));
        // The bottom layer goes: the layer on it becomes the bottom; Undo stacks it again.
        book.select(0);
        gone = LayerEdits.delete(book, 0);
        check(gone != null && book.count() == 2 && !book.linked(0) && book.index() == 0 && holds(book, 0).startsWith("1,1"), "Delete the bottom: the layer on it the bottom, drawn on");
        gone.apply(book);
        check(all(book).equals(before) && book.index() == 0, "Undo: the bottom back, the layer stacked on it again");
        check(LayerEdits.delete(book, 2) == null && book.count() == 3, "A page alone isn't a layer to delete");
        // Merge Down: the upper's marks in the lower, the upper gone; Undo: both as they were.
        book.select(1);
        LayerEdits.Undo merged = LayerEdits.mergeDown(book, 1);
        check(merged != null && book.count() == 2 && book.index() == 0 && holds(book, 0).contains("0,0=0") && holds(book, 0).contains("1,1=0"), "Merge Down: the layer's marks in the one under it, drawn on");
        check(InkWashLayers.pairs(book.current()) == 1, "as one layer");
        merged.apply(book);
        check(all(book).equals(before) && book.index() == 1, "Undo: both layers as they were, one step: " + all(book));
        check(LayerEdits.mergeDown(book, 0) == null, "The bottom has nothing to merge into");
        // Undo is ready only while nothing changed since.
        book.select(1);
        merged = LayerEdits.mergeDown(book, 1);
        mark(book.current(), 30, 30);
        check(!merged.ready(book), "A stroke since: Undo takes the stroke back first, not the merge");
        book.current().undo();
        check(merged.ready(book), "and once it is undone, the merge");
        gone = LayerEdits.delete(book(3), 0);
        DrawingBook other = book(3);
        other.setTracing(linked(false, true, true));
        gone = LayerEdits.delete(other, 2);
        other.setTracing(other.tracing().withPaper(TracingPaper.TRACING));
        check(!gone.ready(other), "Another book edit since: not ready");
        // A timed page locked (under Lock Pages) is left as it is.
        DrawingBook timed = book(2);
        timed.setOptions(timed.options().withLockPages(true));
        timed.setTracing(linked(false, true));
        timed.select(1); timed.current().setClock(PageClock.NONE.lock(1000));
        check(LayerEdits.mergeDown(timed, 1) == null && timed.count() == 2, "A locked layer isn't merged");
    }

    // ---- moves ----

    static void moves() throws Exception {
        DrawingBook book = book(5);
        book.setTracing(linked(false, true, false, false, true));
        // Stacks: [0 1] [2] [3 4]; move the first after the last.
        List<String> before = all(book);
        book.moveStack(0, 2);
        List<String> after = all(book);
        check(after.get(0).equals(before.get(2)) && after.get(1).equals(before.get(3)) && after.get(2).equals(before.get(4))
                && after.get(3).equals(before.get(0)) && after.get(4).equals(before.get(1)) && book.stacks().count() == 3 && book.stacks().size(2) == 2 && book.stacks().size(1) == 2,
                "A stack moved whole to the end: " + after);
        check(book.index() == 3, "The page drawn on goes with it");
        book.moveStack(2, 0);
        check(all(book).equals(before) && book.index() == 0, "and back: as it was");
        book.moveStack(1, 0);
        check(book.stacks().size(0) == 1 && book.stacks().size(1) == 2 && holds(book, 0).equals(before.get(2).replace("^", "")), "A page alone moved before a stack, the stack whole after it");
    }

    // ---- the guides ----

    static void guides() throws Exception {
        DrawingBook book = book(2);
        book.setOptions(NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.LINED));
        book.select(0);
        book.setPageTemplate(0, NotebookTemplate.GRID);
        book.addLayer(true);
        check(book.template(1).equals(NotebookTemplate.GRID) && book.template(2).equals(NotebookTemplate.LINED), "A layer shows its stack's bottom page's guides");
        book.setPageTemplate(1, NotebookTemplate.DOTS);
        check(book.template(0).equals(NotebookTemplate.DOTS) && book.template(1).equals(NotebookTemplate.DOTS), "A layer's own template is its stack's (the bottom page's)");
    }

    // ---- what shows through ----

    static void ghosts() {
        TracingPaper t = linked(false, false, true, true, false);
        check(Arrays.deepEquals(t.ghosts(2), new int[][]{{1, 100}, {3, 100}}) && Arrays.deepEquals(t.ghosts(1), new int[][]{{2, 100}, {3, 100}}), "Off: the stack's other layers, Solid, above and below the one drawn on");
        check(t.ghosts(0).length == 0 && t.ghosts(4).length == 0, "A page alone, Off: nothing");
        check(Arrays.deepEquals(t.withStep(3, TracingPaper.HIDDEN).withStep(1, TracingPaper.HALF).ghosts(2), new int[][]{{1, 44}}), "A layer at its step; Hidden left out");
        check(Arrays.deepEquals(t.withStep(2, TracingPaper.HIDDEN).ghosts(2), new int[][]{{1, 100}, {3, 100}}), "The layer drawn on always shows (its own step doesn't hide it)");
        TracingPaper tr = t.withPaper(TracingPaper.TRACING);
        check(Arrays.deepEquals(tr.ghosts(4), new int[][]{{1, 44}, {2, 44}, {3, 44}}), "Page Before: the stack before, every layer at Half");
        check(Arrays.deepEquals(tr.ghosts(0), new int[][]{{1, 18}, {2, 18}, {3, 18}}), "and the stack after at Faint");
        check(Arrays.deepEquals(tr.ghosts(2), new int[][]{{0, 44}, {1, 100}, {3, 100}, {4, 18}}), "With its own stack's layers");
        TracingPaper glass = t.withPaper(TracingPaper.GLASS);
        check(Arrays.deepEquals(glass.ghosts(0), new int[][]{{1, 44}, {2, 44}, {3, 44}, {4, 44}}), "Nearby Pages: the nearest stacks, whole");
    }

    // ---- the file, the recovery, an older reader ----

    static void records() throws Exception {
        DrawingBook book = book(3);
        byte[] before = NotebookOptionsChecks.bytes(book);
        BookOptions.Covers none = new BookOptions.Covers(); none.tracing = TracingPaper.plain(3);
        check(!none.any(), "No page stacked: no record");
        TracingPaper stacked = linked(false, true, false).withStep(1, TracingPaper.HIDDEN);
        book.setTracing(stacked);
        check(book.unsaved(), "Stacking is a change to save");
        BookOptions.Covers alone = new BookOptions.Covers(); alone.tracing = linked(false, true, false);
        byte[] block = BookOptions.bytes(alone);
        BookOptions.Found found = BookOptions.find(BookOptions.of(block), 0, block.length);
        check(found != null && found.records.size() == 1 && found.records.get(0).tag == BookOptions.STACKS, "STAK alone is one record");
        check(BookOptions.covers(found, BookOptions.of(block), null, 3).tracing.equals(alone.tracing) && BookOptions.stacks(found, BookOptions.of(block), 3) == 2, "and reads back: two pages");
        byte[] after = NotebookOptionsChecks.bytes(book);
        File dir = Files.createTempDirectory("stacks").toFile();
        try {
            File file = new File(dir, "nb.mpaint"); Files.write(file.toPath(), after);
            for (DrawingBook b : new DrawingBook[]{BookCodec.read(new ByteArrayInputStream(after)), BookCodec.open(file)})
                check(b.tracing().equals(stacked) && b.linked(1) && b.stacks().count() == 2 && !b.unsaved(), "The stacks read back from the file");
            BookPictures.Cover cover = BookPictures.readCover(file);
            check(cover.pages == 3 && cover.stacks == 2, "The shelf says two pages (three pages in the file)");
            List<PackedPage> a = BookCodec.openPages(file).pages;
            File base = new File(dir, "base.mpaint"); Files.write(base.toPath(), before);
            List<PackedPage> b = BookCodec.openPages(base).pages;
            for (int i = 0; i < 3; i++) check(a.get(i).crc == b.get(i).crc, "The pages byte for byte as without it");
            // A reader without STAK (the records it knows, the stack's left out): every layer a page of its own.
            TracingPaper older = TracingPaper.read(null, null, BookCodec.read(new ByteArrayInputStream(after)).tracing().stepBytes(), 3);
            check(older.stacks().count() == 3, "A reader without the record sees three pages");
        } finally { NotebookOptionsChecks.delete(dir); }
        ByteArrayOutputStream recovery = new ByteArrayOutputStream();
        RecoveryCodec.write(recovery, book.snapshot(), "");
        check(RecoveryCodec.read(new ByteArrayInputStream(recovery.toByteArray())).book.tracing().equals(stacked), "The recovery keeps the stacks");
        book.setTracing(TracingPaper.plain(3));
        check(Arrays.equals(NotebookOptionsChecks.bytes(book), before), "Unstacked: the file byte for byte as before");
        byte[] bytes = linked(false, true, false).stackBytes();
        check(TracingPaper.read(null, null, null, null, bytes, 4).stacks().count() == 4, "A STAK for another page count is ignored");
        byte[] bad = bytes.clone(); bad[4] = 1;
        check(TracingPaper.read(null, null, null, null, bad, 3).stacks().count() == 3, "and one stacking the first page");
        bad = bytes.clone(); bad[5] = 2;
        check(TracingPaper.read(null, null, null, null, bad, 3).stacks().count() == 3, "and one with a byte out of range");
        byte[] five = linked(false, true, true, true, true).stackBytes();
        check(TracingPaper.read(null, null, null, null, five, 5).stacks().count() == 5, "and one with a stack of five");
        check(linked(false, true).plainView().linked(1) && linked(false, true).withStep(0, TracingPaper.HALF).withLayers(1, new int[]{-1}).linked(1), "Back to plain, or old layers merged: the stacks stay");
    }

    // ---- a stack is one page elsewhere ----

    static void copies() throws Exception {
        DrawingBook book = book(3);
        book.setTracing(linked(false, true, false));
        check(book.stacks().count() == 2, "Three pages, two stacks: \"1 / 2\"");
        File dir = Files.createTempDirectory("stackcopies").toFile();
        try {
            DrawingBook.Snapshot s = book.snapshot();
            NotebookExport.Done done = NotebookExport.update(dir, "nb", s.packed(), null, null, NotebookOptions.DEFAULT, null, s.tracing);
            File pngs = new File(dir, "nb");
            check(done.drawn == 2 && new File(pngs, "001.png").isFile() && new File(pngs, "002.png").isFile() && !new File(pngs, "003.png").exists(), "A PNG a stack: two");
            NotebookExport.Png first = NotebookExport.read(Files.readAllBytes(new File(pngs, "001.png").toPath()));
            check(first.width == W && first.height == H, "The stack's PNG the page's size");
            byte[] pdf = Files.readAllBytes(NotebookExport.pdf(dir, "nb").toPath());
            check(new String(pdf, "ISO-8859-1").contains("/Count 2 "), "Two PDF pages");
            check(NotebookExport.update(dir, "nb", s.packed(), null, null, NotebookOptions.DEFAULT, null, s.tracing).drawn == 0, "Unchanged: nothing drawn again");
            NotebookExport.Done hid = NotebookExport.update(dir, "nb", s.packed(), null, null, NotebookOptions.DEFAULT, null, s.tracing.withStep(1, TracingPaper.HIDDEN));
            check(hid.drawn == 1, "A layer Hidden: its stack's PNG drawn again (left out)");
            // The flattened stack: both layers' marks.
            ToneDocument.Snapshot a = DocumentCodec.read(new ByteArrayInputStream(s.packed().get(0).bytes())).layerSnapshot();
            ToneDocument.Snapshot b = DocumentCodec.read(new ByteArrayInputStream(s.packed().get(1).bytes())).layerSnapshot();
            ToneDocument.Snapshot flat = PageStacks.flatten(new ArrayList<>(Arrays.asList(a, b)), new boolean[2]);
            int[] row = new int[W];
            new PagePicture.Composite(flat).row(0, row); int r0 = row[0];
            new PagePicture.Composite(flat).row(1, row);
            check(r0 == 0 && row[1] == 0, "Flattened, both layers' marks");
        } finally { NotebookOptionsChecks.delete(dir); }
        PagePicture one = PagePicture.of(book.snapshot().page(0).layerSnapshot()), two = PagePicture.of(book.snapshot().page(1).layerSnapshot());
        PagePicture both = PagePicture.stacked(Arrays.asList(one, two));
        check(both.tone(0, 0) < 255 && both.width == one.width, "The strip's picture of a stack: its layers over each other");
    }

    // ---- the page bar's + and the end of a notebook (2026-10-24: no blank page kept there) ----

    static void blankEnd() throws Exception {
        DrawingBook book = book(2);
        check(book.addLayer(true) && book.count() == 3 && book.linked(1), "A layer on the first page");
        book.select(0);
        book.insertPage();
        check(book.count() == 4 && book.index() == 3 && !book.linked(3) && book.stacks().count() == 3 && book.stacks().of(1) == 0,
                "+ on a stack's bottom: a page at the notebook's end, never inside the stack, drawn on");
        check(!book.linked(2) && holds(book, 2).equals(holds(book(2), 1)), "the page after the stack stays, unstacked");
        // A blank last page with a layer on it is a page with layers: opening keeps it.
        book.select(3);
        check(book.addLayer(true) && book.count() == 5 && book.linked(4), "A layer on the new page");
        book.select(0);
        check(book.linked(4) && BlankPages.blank(book, 3) && BlankPages.blank(book, 4) && !BlankPages.opened(book) && book.count() == 5, "Opening keeps a blank last page with layers");
    }

    // ---- layers above cover the one drawn on (follow-up) ----

    /** What the screen shows at pixel {@code i} of {@code page} with {@code planes}: as the native compose does. */
    static int shown(ToneDocument page, GhostPlane.Planes planes, int i) {
        byte[][] tones = new byte[ToneDocument.MAX_LAYERS + 3][], alpha = new byte[ToneDocument.MAX_LAYERS + 3][];
        int[] opacity = new int[ToneDocument.MAX_LAYERS + 3];
        int n = page.visibleLayers(tones, alpha, opacity, planes == null ? null : planes.under, 0, planes == null ? null : planes.overTones, planes == null ? null : planes.overAlpha), result = page.paperTone();
        for (int k = 0; k < n; k++) {
            int a = alpha[k][i] & 255;
            if (a == 0) continue;
            if (opacity[k] != 100) a = (a * opacity[k] + 50) / 100;
            result = ((tones[k][i] & 255) * a + result * (255 - a) + 127) / 255;
        }
        return result;
    }
    static void paint(ToneDocument page, int x, int y, int tone) { page.begin(); page.setTone(x, y, tone); page.finish(); }
    static GhostPlane.Planes planes(DrawingBook book) throws IOException { return GhostPlane.build(book.width, book.height, book.ghostSources()); }

    /** A page whose ink layer holds {@code marks}: {x, y, tone} each, at full cover (white paint too). */
    static ToneDocument painted(int[]... marks) {
        ArrayList<ToneDocument.Layer> layers = new ArrayList<>();
        layers.add(InkWashLayers.empty("Wash", W, H));
        ToneDocument.Layer ink = InkWashLayers.empty("Ink", W, H);
        for (int[] m : marks) { ink.tones[m[1] * W + m[0]] = (byte) m[2]; ink.alpha[m[1] * W + m[0]] = (byte) 255; }
        layers.add(ink);
        return new ToneDocument(W, H, layers, InkWashLayers.INK);
    }
    static void over() throws Exception {
        // Bottom: black at 1,1 and 3,3. Middle: white paint at 3,3, black at 5,5 and 9,9. Top: black at 7,7, white at 1,1 and 9,9.
        ArrayList<byte[]> pages = new ArrayList<>();
        pages.add(DrawingBook.encode(painted(new int[]{1, 1, 0}, new int[]{3, 3, 0})));
        pages.add(DrawingBook.encode(painted(new int[]{3, 3, 255}, new int[]{5, 5, 0}, new int[]{9, 9, 0})));
        pages.add(DrawingBook.encode(painted(new int[]{7, 7, 0}, new int[]{1, 1, 255}, new int[]{9, 9, 255})));
        DrawingBook book = new DrawingBook(W, H, pages, 0);
        book.setTracing(linked(false, true, true));
        book.select(1);
        GhostPlane.Planes p = planes(book);
        ToneDocument mid = book.current();
        check(p != null && p.under != null && p.overAlpha != null, "Drawing on the middle layer: one plane under it, one over it");
        check(shown(mid, p, 5 * W + 5) == 0, "Its own mark shows");
        check(shown(mid, p, 7 * W + 7) == 0, "The layer above covers it: its mark shows over the page");
        check(shown(mid, p, 9 * W + 9) == 255, "and the layer above's white paint covers the mark drawn on");
        check(shown(mid, p, 3 * W + 3) == 255, "Its own white paint covers the layer below");
        check(shown(mid, p, 1 * W + 1) == 255, "The layer above's white covers the layer below's black");
        book.select(0);
        p = planes(book);
        check(shown(book.current(), p, 7 * W + 7) == 0 && shown(book.current(), p, 5 * W + 5) == 0, "Drawing on the bottom: both layers above show over it");
        check(p.under == null, "and nothing lies under it");
        // The eraser's preview and Real Mirror: a page tone through GhostPlane.over is what the compose shows.
        for (int i : new int[]{1 * W + 1, 7 * W + 7, 9 * W + 9, 20 * W + 20})
            check(GhostPlane.over(book.current().compositeTone(i % W, i / W), p, i) == shown(book.current(), p, i), "The eraser's preview shows the planes as the page does at " + i);
        // Half on the layer above: its marks at Half over the page.
        book.setTracing(book.tracing().withStep(2, TracingPaper.HALF));
        check(Math.abs(shown(book.current(), planes(book), 7 * W + 7) - 143) <= 1, "A layer above at Half: 143");
        // Nothing of it in the page.
        check(book.current().compositeTone(7, 7) == 255, "Never in the page itself");
    }
    static void lands() throws Exception {
        DrawingBook book = book(4);
        book.setTracing(linked(false, true, true, false));
        check(book.pageOf(0) == 2 && book.pageOf(1) == 3 && book.pageOf(DrawingBook.FRONT) == DrawingBook.FRONT, "Never drawn on this session: a stack's top layer");
        book.select(1); book.select(3);
        check(book.pageOf(0) == 1, "A turn lands on the layer drawn on last");
        book.select(0); book.select(3);
        check(book.pageOf(0) == 0 && book.tracing().equals(linked(false, true, true, false)), "and follows it; never in tracing paper (nor the file)");
        book.moveStack(0, 1);
        check(book.pageOf(1) == 1, "It moves with its stack");
    }
    static void neighbours() throws Exception {
        TracingPaper t = linked(false, true, false).withPaper(TracingPaper.TRACING);
        int[][] g = t.ghostLayers(2);
        check(g.length == 2 && g[0][1] == 100 && g[0][2] == 44 && g[0][3] == 0 && g[1][3] == 0, "Page Before: the stack before, its layers Solid in it, the stack at Half");
        // Two black layers at one pixel: the stack flattened first, then Half once: 143, not 255 × .56 × .56.
        DrawingBook book = new DrawingBook(InkWashLayers.blank(W, H));
        book.addPage(); book.addPage();
        book.setTracing(linked(false, true, false).withPaper(TracingPaper.TRACING));
        book.select(0); paint(book.current(), 4, 4, 0);
        book.select(1); paint(book.current(), 4, 4, 0);
        book.select(2);
        GhostPlane.Planes p = planes(book);
        check(Math.abs(shown(book.current(), p, 4 * W + 4) - 143) <= 1, "An untouched neighbour at Page Before's strength alone: " + shown(book.current(), p, 4 * W + 4));
        book.setTracing(book.tracing().withStep(1, TracingPaper.HIDDEN));
        check(Math.abs(shown(book.current(), planes(book), 4 * W + 4) - 143) <= 1, "A layer Hidden inside it: the other still at Half");
        book.select(0); paint(book.current(), 6, 6, 0);
        book.select(2);
        book.setTracing(book.tracing().withStep(0, TracingPaper.HALF));
        // Its own Half inside the stack (0.44), then the stack at Half (0.44).
        int at = shown(book.current(), planes(book), 6 * W + 6);
        check(Math.abs(at - (255 - 255 * .44 * .44)) <= 2, "A layer's own step applies inside its stack: " + at);
    }
    static void decodes() throws Exception {
        boolean[] links = new boolean[24];
        for (int i = 0; i < links.length; i++) links[i] = i % 4 != 0;
        TracingPaper t = new TracingPaper(TracingPaper.GLASS, -1, linkedBits(links));
        for (int i = 0; i < links.length; i++) check(t.ghostLayers(i).length <= TracingPaper.MOST_DECODES, "At most six pages at page " + i);
        check(t.ghostLayers(9).length == 3 + 0 || t.ghostLayers(9).length <= 6, "Its own stack's three first");
        TracingPaper alone = new TracingPaper(TracingPaper.GLASS, -1, new int[10]);
        check(alone.ghostLayers(5).length == TracingPaper.GLASS_PAGES, "Pages alone: the nearest four");
        // Desktop ms: Nearby Pages at Manta size, stacks of two layers, a turn's planes from packed pages.
        int w = 1920, h = 2560;
        DrawingBook book = new DrawingBook(InkWashLayers.blank(w, h));
        for (int i = 1; i < 10; i++) book.addPage();
        for (int i = 0; i < 10; i++) { book.select(i); paint(book.current(), 100 + i, 100 + i, 0); }
        boolean[] two = new boolean[10];
        for (int i = 0; i < 10; i++) two[i] = i % 2 == 1;
        book.setTracing(new TracingPaper(TracingPaper.GLASS, -1, linkedBits(two)));
        book.select(5); book.select(4);
        long best = Long.MAX_VALUE;
        for (int k = 0; k < 3; k++) { long t0 = System.nanoTime(); planes(book); best = Math.min(best, System.nanoTime() - t0); }
        int pages = book.tracing().ghostLayers(4).length;
        System.out.println("Nearby Pages at 1920 x 2560, stacks of two: " + pages + " pages, planes built in " + best / 1_000_000 + " ms (desktop)");
        check(pages <= TracingPaper.MOST_DECODES, "Six pages at most");
    }
    // ---- edges: the stack's guides, ghost and cover kept as layers come and go; Undo in a full notebook ----

    static void edges() throws Exception {
        // A layer below the bottom: the stack keeps its guides (its own template).
        DrawingBook book = book(2);
        book.setOptions(NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.LINED));
        book.setPageTemplate(0, NotebookTemplate.GRID);
        book.select(0);
        check(book.addLayer(false) && book.template(0).equals(NotebookTemplate.GRID) && book.template(1).equals(NotebookTemplate.GRID), "A layer below: the stack keeps its own guides: " + book.template(0));
        // The bottom deleted: the layer on it takes its guides, the ghost pinned to it and the cover picture; Undo puts them back.
        book = book(3);
        book.setOptions(NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.LINED));
        book.setTracing(linked(false, true, false).withPaper(TracingPaper.TRACING).withGhost(0));
        book.setPageTemplate(0, NotebookTemplate.GRID);
        book.setCover(0);
        book.select(0);
        LayerEdits.Undo gone = LayerEdits.delete(book, 0);
        check(gone != null && book.template(0).equals(NotebookTemplate.GRID), "The bottom deleted: the layer on it takes its guides: " + book.template(0));
        check(book.tracing().ghost == 0 && book.cover() == 0, "and its ghost pin and cover picture: " + book.tracing().ghost + " " + book.cover());
        gone.apply(book);
        check(book.template(0).equals(NotebookTemplate.GRID) && book.template(1).equals(NotebookTemplate.GRID) && book.tracing().ghost == 0 && book.cover() == 0 && book.linked(1), "Undo: all back on the bottom");
        // A full notebook: a layer deleted, nothing added at the end; Undo puts the layer back.
        DrawingBook full = new DrawingBook(InkWashLayers.blank(W, H));
        mark(full.current(), 0, 0);
        while (full.count() < DrawingBook.MAX_PAGES) { full.addPage(); mark(full.current(), 1, 1); }
        full.setTracing(full.tracing().withStep(0, 0));
        boolean[] links = new boolean[DrawingBook.MAX_PAGES]; links[1] = true;
        full.setTracing(new TracingPaper(TracingPaper.PLAIN, -1, linkedBits(links)));
        full.select(1);
        gone = LayerEdits.delete(full, 1);
        check(gone != null && full.count() == DrawingBook.MAX_PAGES - 1, "Full: a layer deleted, nothing added at the end");
        check(gone.ready(full), "Undo is ready");
        gone.apply(full);
        check(full.count() == DrawingBook.MAX_PAGES && full.linked(1) && full.index() == 1 && !full.current().unmarked(), "and puts the layer back, the blank page at the end making room");
        // One page: no layer to delete or merge; a layer on it, then its delete and Undo.
        DrawingBook one = book(1);
        check(LayerEdits.delete(one, 0) == null && LayerEdits.mergeDown(one, 0) == null, "One page: nothing to delete or merge");
        check(one.addLayer(true) && (gone = LayerEdits.delete(one, 1)) != null && one.count() == 1, "A layer on it, deleted");
        gone.apply(one);
        check(one.count() == 2 && one.linked(1) && one.index() == 1, "and back");
    }
    static int[] linkedBits(boolean[] links) { int[] s = new int[links.length]; for (int i = 0; i < s.length; i++) if (links[i]) s[i] = TracingPaper.LINKED; return s; }

    // ---- the version before stacks ----

    static void write(File file) throws Exception {
        DrawingBook book = NotebookOptionsChecks.book();
        book.setTracing(linked(false, true));
        check(book.stacks().count() == 1, "Two layers, one page");
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) { BookCodec.write(out, book.snapshot()); }
        System.out.println("PASS: wrote a notebook of one page of two layers");
    }
    static void older(File file) throws Exception {
        DrawingBook read = BookCodec.open(file);
        check(read.count() == 2 && read.stacks().count() == 2 && !read.linked(1), "A stacked notebook the version before saved again opens here as two pages");
        System.out.println("PASS: the version before stacks (2ddb2a4) opened a notebook of one page of two layers as two pages, its marks as drawn, and saved it again; it opens here as two pages");
    }
}
