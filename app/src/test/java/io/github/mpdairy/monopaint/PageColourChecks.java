package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Page Colour (the owner, 2026-10-23: "a notebook setting: default page color? that'd be cool"; {@link NotebookOptions#paper}):
 * the paper tone a notebook's new pages start on, set by six swatches in the settings list's row (2026-10-23; once a
 * 12-step slider on the Template screen, whose greys an older notebook keeps). White by default,
 * no record then, so an old notebook opens and writes itself as before; a PCOL record otherwise, read back from the file
 * and the recovery. Pages added start on it (the page bar's + too) and count as blank on it; pages with
 * anything on them keep their own paper when it changes, blank ones take the new one (a new notebook's first page
 * among them). The page's own paper (its {@link ToneDocument#paperTone}), so the eraser rubs back to it and the
 * pictures and copies show it; the copies leave out a blank coloured page at the end.
 */
public final class PageColourChecks {
    static final int W = 300, H = 400;
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        words(); oldGrey(); records(); pages(); fresh(); layers(); eraser();
        System.out.println("PASS: Page Colour: six swatches, the six greys, in the settings list (no slider on Template's screen), White by default, an older slider grey kept exactly with no swatch chosen and written byte for byte; a PCOL record only when not white, "
                + "read back from the file and the recovery, an old notebook white and byte for byte as before; added pages and the page bar's + start on it "
                + "and count as blank; pages with marks keep their paper, blank ones (a new notebook's first page too) take the new one; a layer is on its stack's paper; the eraser rubs back to it; "
                + "pictures show it and the copies leave out a blank coloured page at the end");
    }

    static void words() {
        NotebookOptions o = NotebookOptions.DEFAULT;
        check(o.paper == 255 && o.isDefault() && o.paperSwatch() == 0, "White by default, its swatch the first");
        check(o.withPaper(300).paper == 255 && o.withPaper(-4).paper == 0 && !o.withPaper(128).isDefault(), "A tone, 0 to 255");
        // The six swatches are Scribbler Style's six greys, paper to ink, each read back as itself.
        int[] greys = {CardArt.PAPER, CardArt.GREY_200, CardArt.GREY_400, CardArt.GREY_600, CardArt.GREY_800, CardArt.INK};
        check(NotebookOptions.PAPERS.length == 6, "Six swatches");
        for (int i = 0; i < 6; i++) check(NotebookOptions.PAPERS[i] == (greys[i] & 0xff) && o.withPaper(NotebookOptions.PAPERS[i]).paperSwatch() == i, "Swatch " + i + " is grey " + Integer.toHexString(greys[i]));
        // An older notebook's grey from the 12-step slider (30% grey: tone 179) keeps its exact grey, no swatch chosen.
        NotebookOptions old = o.withPaper(179);
        check(old.paper == 179 && old.paperSwatch() == -1 && NotebookOptions.paperWord(179).equals("30% grey"), "An old slider grey kept, no swatch chosen: " + NotebookOptions.paperWord(179));
        check(NotebookOptions.paperWord(255).equals("White") && NotebookOptions.paperWord(0).equals("Black") && NotebookOptions.paperWord(0x88).equals("47% grey"), "Its words");
        check(LibraryCard.rows().contains(LibraryCard.Part.PAGE_COLOUR) && !CardSheet.slides(CardSheet.Screen.TEMPLATE) && CardSheet.sliderHead(CardSheet.Screen.TEMPLATE) == null,
                "A row of the settings list, no slider on the Template screen");
        check(!CardSheet.about(CardSheet.Screen.TEMPLATE, 0).contains("colour"), "The Template screen is the guides alone: " + CardSheet.about(CardSheet.Screen.TEMPLATE, 0));
    }

    /** A notebook from the slider's days, in an off-swatch grey, opens and writes itself byte for byte, its grey as it was. */
    static void oldGrey() throws Exception {
        DrawingBook book = NotebookOptionsChecks.book();
        book.setOptions(book.options().withPaper(179));
        byte[] before = NotebookOptionsChecks.bytes(book);
        DrawingBook read = BookCodec.read(new ByteArrayInputStream(before));
        check(read.options().paper == 179 && read.options().paperSwatch() == -1 && Arrays.equals(NotebookOptionsChecks.bytes(read), before), "An off-swatch grey opens as it was, byte for byte");
    }

    static void records() throws Exception {
        NotebookOptions grey = NotebookOptions.DEFAULT.withPaper(128);
        BookOptions.Covers covers = new BookOptions.Covers(); covers.options = grey;
        byte[] block = BookOptions.bytes(covers);
        BookOptions.Found found = BookOptions.find(BookOptions.of(block), 0, block.length);
        check(found != null && found.records.size() == 1 && found.records.get(0).tag == BookOptions.PAGE_COLOUR && found.records.get(0).length == 1
                && BookOptions.options(found, BookOptions.of(block)).equals(grey), "Page Colour alone is one PCOL record, read back");
        check(NotebookOptions.DEFAULT.readPaper(null).paper == 255 && NotebookOptions.DEFAULT.readPaper(new byte[]{1, 2}).paper == 255 && NotebookOptions.DEFAULT.readPaper(new byte[]{(byte) 200}).paper == 200,
                "None, or one that doesn't read, is white");
        // A notebook from before Page Colour: white, and written back byte for byte.
        DrawingBook old = NotebookOptionsChecks.book();
        byte[] before = NotebookOptionsChecks.bytes(old);
        DrawingBook read = BookCodec.read(new ByteArrayInputStream(before));
        check(read.options().paper == 255 && Arrays.equals(NotebookOptionsChecks.bytes(read), before), "An old notebook is white, byte for byte as before");
        // Set, the file and the recovery keep it; put back, the file is as before.
        old.setOptions(old.options().withPaper(128));
        check(old.unsaved() && BookCodec.read(new ByteArrayInputStream(NotebookOptionsChecks.bytes(old))).options().paper == 128, "The file keeps it");
        ByteArrayOutputStream recovery = new ByteArrayOutputStream();
        RecoveryCodec.write(recovery, old.snapshot(), "");
        check(RecoveryCodec.read(new ByteArrayInputStream(recovery.toByteArray())).book.options().paper == 128, "and the recovery");
        old.setOptions(old.options().withPaper(255));
        check(Arrays.equals(NotebookOptionsChecks.bytes(old), before), "White again: the file byte for byte as before");
    }

    static void pages() throws Exception {
        DrawingBook book = NotebookOptionsChecks.book();   // two pages with marks
        book.setOptions(book.options().withPaper(128));
        for (int i = 0; i < 2; i++) { book.select(i); check(book.current().paperTone() == 255 && !book.current().unmarked(), "A page with marks keeps its paper: " + i); }
        book.addPage();
        ToneDocument added = book.current();
        check(added.paperTone() == 128 && added.unmarked() && BlankPages.blank(book, 2) && BlankPages.blank(added, 128) && !BlankPages.blank(added), "A page added starts on it, and is blank on it");
        BlankPagesChecks.mark(added);
        book.insertPage();
        check(book.count() == 4 && book.index() == 3 && BlankPages.blank(book, 3), "The page bar's + adds a page on it, blank on it");
        book.select(3);
        check(book.current().paperTone() == 128 && book.current().compositeTone(5, 5) == 128, "That page is on it too");
        check(PagePicture.of(book.current().layerSnapshot()).toneOn(0, 0, 255) < 140, "and its picture shows it");
        // Read back from the file: the pages keep their paper.
        DrawingBook read = BookCodec.read(new ByteArrayInputStream(NotebookOptionsChecks.bytes(book)));
        read.select(2);
        check(read.current().paperTone() == 128 && read.options().paper == 128, "The file keeps the pages' paper");
        // The copies leave out the blank coloured page at the end, as a white one.
        java.util.List<PackedPage> packed = new java.util.ArrayList<>();
        for (int i = 0; i < book.count(); i++) { book.select(i); packed.add(PackedPage.of(DrawingBook.encode(book.current()))); }
        check(BlankPages.drawn(packed, 128).size() == 3 && BlankPages.drawn(packed).size() == 4, "The copies leave out the blank coloured page at the end");
    }

    /** A new notebook: its first page and a blank page put away take the colour picked on its card. */
    static void fresh() throws Exception {
        DrawingBook book = new DrawingBook(new ToneDocument(W, H));
        BlankPages.Made made = BlankPages.made(W, H);
        check(book.appendUnchanged(PackedPage.of(made.bytes), made.picture) && book.count() == 2 && BlankPages.madeTone(book.packed(1)) == 255, "A blank page put away");
        long revision = book.current().revision();
        book.setOptions(book.options().withPaper(64));
        check(book.current().paperTone() == 64 && book.current().revision() != revision && book.unsaved(), "The first page takes it, a change to save");
        check(BlankPages.madeTone(book.packed(1)) == 64 && BlankPages.blank(book, 1), "and the one put away, without decoding it");
        book.select(1);
        check(book.current().paperTone() == 64, "which opens on it");
        // A page with marks on it in that notebook keeps its paper when it changes again.
        BlankPagesChecks.mark(book.current());
        book.setOptions(book.options().withPaper(200));
        check(book.current().paperTone() == 64, "A marked page keeps its paper");
        book.select(0);
        check(book.current().paperTone() == 200, "a blank one takes the new one");
    }

    /** A layer is on its stack's paper, and a stack with a mark on any layer keeps its paper when Page Colour changes. */
    static void layers() throws Exception {
        DrawingBook book = NotebookOptionsChecks.book();   // two pages with marks, on white
        book.setOptions(book.options().withPaper(128));
        book.select(0);
        check(book.addLayer(true) && book.current().paperTone() == 255 && book.linked(1), "A layer over a white page is white, not the notebook's colour");
        book.setOptions(book.options().withPaper(60));
        check(book.current().unmarked() && book.current().paperTone() == 255, "A blank layer over a marked page keeps its stack's paper");
    }
    /** The eraser rubs back to the page's colour, not white (it is the page's paper). */
    static void eraser() throws Exception {
        DrawingBook book = new DrawingBook(InkWashLayers.blank(W, H));
        book.setOptions(book.options().withPaper(90));
        ToneDocument d = book.current();
        InkWashLayers.select(d, 0);
        BlendChecks.live(d, BlendChecks.brush(FillChecks.PEN), 6, 1, null, BlendChecks.across(30, 210, 60));
        check(d.compositeTone(120, 60) < 60, "marked");
        EraseBothStroke rub = new EraseBothStroke(d, 60f);
        long t = 0;
        for (int y = 20; y <= 100; y += 10) for (double[] p : BlendChecks.across(-10, 250, y)) rub.sample((float) p[0], (float) p[1], 1f, 0, 0, t += 30);
        rub.finish();
        check(d.unmarked() && d.compositeTone(120, 60) == 90, "The eraser rubs back to the page's colour: " + d.compositeTone(120, 60));
    }
}
