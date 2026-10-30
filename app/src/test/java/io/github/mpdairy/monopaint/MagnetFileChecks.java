package io.github.mpdairy.monopaint;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Fridge magnets in files: TSM5's WORD section ({@link DocumentCodec}, {@link Magnets#encode}). A page without magnets
 * writes as it did; a page with them writes TSM5 and reads them back, beside Erasable's and wax's sections; a section of
 * a later version is skipped, the drawing opening without them; damage is refused; books and their unshown pages keep
 * them. With {@code write <file>}: a book with magnets for an older reader ({@code MagnetOlderReaderCheck}), and its
 * pages' composite printed for it to match.
 */
public final class MagnetFileChecks {
    static final int W = ErasableFileChecks.W, H = ErasableFileChecks.H;

    public static void main(String[] args) throws IOException {
        if (args.length == 2 && args[0].equals("write")) {
            DrawingBook book = new DrawingBook(worded(ErasableFileChecks.marked()));
            book.addPage(); book.current().setMagnets(Magnets.NONE.with(new Magnets.Magnet("moon", 70, 50, -12, 0)));
            try (FileOutputStream out = new FileOutputStream(args[1])) { BookCodec.write(out, book.snapshot()); }
            System.out.println(hash(book.snapshot().page(0)) + " " + hash(book.snapshot().page(1)));
            return;
        }
        plain(); roundTrip(); versions(); damaged(); books();
        System.out.println("PASS: magnets in files: a page without them writes TSM3/TSM4 as before; with them TSM5 and a WORD section, read back word, place, tilt and turn, "
                + "beside Erasable's and wax's sections; a later version's section is skipped (the drawing opens without them); a short, oversized, repeated or off-page one refused; "
                + "books, unshown pages and snapshots keep them");
    }

    static ToneDocument worded(ToneDocument page) {
        page.setMagnets(Magnets.NONE.with(new Magnets.Magnet("the", 30, 20, 0, 0)).with(new Magnets.Magnet("whisper", 100, 80, 21, 1))
                .with(new Magnets.Magnet("&", 140, 100, -25, 3)));
        return page;
    }
    static byte[] bytes(ToneDocument d) throws IOException { return ErasableFileChecks.bytes(d); }

    static void plain() throws IOException {
        ToneDocument page = InkWashLayers.blank(W, H);
        byte[] before = bytes(page);
        check(ErasableFileChecks.magic(before).equals("TSM3"), "A page without magnets: TSM3");
        page.setMagnets(page.magnets().with(new Magnets.Magnet("moon", 40, 40, 0, 0)));
        check(ErasableFileChecks.magic(bytes(page)).equals("TSM5"), "With a magnet: TSM5");
        page.undo();
        check(Arrays.equals(bytes(page), before), "Its magnet taken off, the page writes byte for byte as before");
    }
    static void roundTrip() throws IOException {
        ToneDocument page = worded(InkWashLayers.blank(W, H));
        ToneDocument back = ErasableFileChecks.read(bytes(page));
        check(back.magnets().equals(page.magnets()) && back.magnets().get(1).tilt == 21 && back.magnets().get(2).turn == 3 && back.magnets().get(2).tilt == -25,
                "Magnets read back: word, place, tilt, turn, order");
        check(Arrays.equals(ErasableFileChecks.tones(back), ErasableFileChecks.tones(page)), "and the drawing as it was");
        check(!back.canUndo(), "A page read from a file has no Undo");
        // Beside Erasable's shares and wax.
        ToneDocument marked = worded(ErasableFileChecks.marked());
        ToneDocument both = ErasableFileChecks.read(bytes(marked));
        check(both.magnets().equals(marked.magnets()) && Arrays.equals(ErasableFileChecks.share(both, 1), ErasableFileChecks.share(marked, 1)),
                "Magnets and Erasable's shares in one page");
    }
    static byte[] word(Magnets m) throws IOException { return ErasableFileChecks.section(0x574f5244, m.encode()); }
    static void versions() throws IOException {
        ToneDocument page = InkWashLayers.blank(W, H);
        page.setMagnets(page.magnets().with(new Magnets.Magnet("moon", 40, 40, 0, 0)));
        byte[] b = bytes(page);
        // The same page with a version 2 section in place of its own: skipped.
        byte[] later = Magnets.NONE.with(new Magnets.Magnet("x", 1, 1, 0, 0)).encode(); later[0] = 2;
        int own = 8 + page.magnets().encode().length;
        ToneDocument read = ErasableFileChecks.read(ErasableFileChecks.withSections(b, own, ErasableFileChecks.section(0x574f5244, later)));
        check(read.magnets().isEmpty() && Arrays.equals(ErasableFileChecks.tones(read), ErasableFileChecks.tones(page)), "A later version's magnets are skipped; the drawing opens");
        check(ErasableFileChecks.read(ErasableFileChecks.withSections(b, own, word(page.magnets()))).magnets().equals(page.magnets()), "The rebuilt section reads as written");
    }
    static void damaged() throws IOException {
        ToneDocument page = InkWashLayers.blank(W, H);
        page.setMagnets(page.magnets().with(new Magnets.Magnet("moon", 40, 40, 0, 0)));
        byte[] b = bytes(page);
        int own = 8 + page.magnets().encode().length;
        ErasableFileChecks.refused(ErasableFileChecks.withSections(b, word(page.magnets())), "A repeated magnet section");
        byte[] section = page.magnets().encode();
        ErasableFileChecks.refused(ErasableFileChecks.withSections(b, own, ErasableFileChecks.section(0x574f5244, Arrays.copyOf(section, section.length - 3))), "A short magnet section");
        byte[] extra = Arrays.copyOf(section, section.length + 1);
        ErasableFileChecks.refused(ErasableFileChecks.withSections(b, own, ErasableFileChecks.section(0x574f5244, extra)), "A magnet section with bytes left over");
        ErasableFileChecks.refused(ErasableFileChecks.withSections(b, own, word(Magnets.NONE.with(new Magnets.Magnet("off", W + 50, 10, 0, 0)))), "A magnet off the page");
        ErasableFileChecks.refused(ErasableFileChecks.withSections(b, own, word(Magnets.NONE.with(new Magnets.Magnet("tilted", 10, 10, 90, 0)))), "A magnet tilted past the most");
        ByteArrayOutputStream many = new ByteArrayOutputStream(); DataOutputStream d = new DataOutputStream(many);
        d.writeByte(Magnets.VERSION); d.writeInt(Magnets.MOST + 1);
        ErasableFileChecks.refused(ErasableFileChecks.withSections(b, own, ErasableFileChecks.section(0x574f5244, many.toByteArray())), "Too many magnets");
        ErasableFileChecks.refused(ErasableFileChecks.withSections(b, own, ErasableFileChecks.section(0x574f5244, new byte[Magnets.MOST_BYTES + 1])), "An oversized magnet section");
    }
    static void books() throws IOException {
        DrawingBook book = new DrawingBook(worded(InkWashLayers.blank(W, H)));
        Magnets first = book.current().magnets();
        book.addPage();
        Magnets second = Magnets.NONE.with(new Magnets.Magnet("sea", 50, 50, 5, 0));
        book.current().setMagnets(second);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BookCodec.write(out, book.snapshot());
        DrawingBook back = BookCodec.read(new java.io.ByteArrayInputStream(out.toByteArray()));
        check(back.current().magnets().equals(second), "The shown page keeps its magnets in a book");
        check(back.snapshot().page(0).magnets().equals(first) && book.snapshot().page(1).magnets().equals(second), "Unshown pages and snapshots keep theirs");
        back.select(0);
        check(back.current().magnets().equals(first), "Turning to a page brings its magnets");
        check(BookCodec.readFirstPage(new java.io.ByteArrayInputStream(out.toByteArray())).magnets().equals(first), "A preview's page has them too");
    }
    /** A page's look for the older reader to match: its layers' count and composite. */
    static String hash(ToneDocument d) {
        long h = d.layerCount();
        for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) h = h * 31 + d.compositeTone(x, y);
        return Long.toHexString(h);
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
