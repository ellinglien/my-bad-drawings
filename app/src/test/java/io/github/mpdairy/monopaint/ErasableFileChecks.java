package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Erasable in the document and its files (spec docs/archive/superpowers/specs/2026-10-16-erasable-design.md, "File format"):
 * pages without paint the eraser can't take write TSM3 or TSM4 byte for byte as before; pages with it write TSM5 and
 * read back every share; an unknown section is skipped, a damaged one refused; books, previews and recovery carry the
 * shares; Undo and Redo bring them back, a tile from before its layer had a share at 255. With
 * {@code write <file>}: writes a TSM5 book for an older reader to refuse (scripts/test_document.sh).
 */
public final class ErasableFileChecks {
    static final int W = 150, H = 110;
    public static void main(String[] args) throws IOException {
        if (args.length == 2 && args[0].equals("write")) {
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(args[1])) { BookCodec.write(out, new DrawingBook(marked()).snapshot()); }
            return;
        }
        rule(); oldFormats(); roundTrip(); sections(); damaged(); books(); undo(); layers();
        System.out.println("PASS: Erasable in files: pages the eraser can wholly erase write TSM3/TSM4 byte for byte as before; pages that keep paint write TSM5 "
                + "(an end-tagged list of sections inside the checksum) and read back every share, masking fluid too; an unknown section is skipped, "
                + "a truncated, corrupt or repeated one refused; books, previews, unshown pages and recovery carry them; the eraser leaves what is kept, "
                + "Undo and Redo bring shares back (a tile from before its layer had one at 255); clear, expand and restack keep them");
    }

    /** A two-layer page: on layer 0 a band laid all erasable, on layer 1 a permanent band (Erasable 0) and one at .4 over it. */
    static ToneDocument marked() {
        ArrayList<ToneDocument.Layer> layers = new ArrayList<>();
        for (int i = 0; i < 2; i++) layers.add(new ToneDocument.Layer(i == 0 ? "Wash" : "Ink", true, ToneDocument.paper(W, H), new byte[W * H]));
        ToneDocument d = new ToneDocument(W, H, layers, 0);
        band(d, 0, 1, 10, 40, 120);
        band(d, 1, 0, 30, 60, 60);
        band(d, 1, .4, 50, 90, 150);
        return d;
    }
    /** Rows {@code y0..y1} of layer {@code layer}, darkened to {@code gray} over what is there (transparent), as erasable as {@code e}; the owns are the band's own darkness. */
    static void band(ToneDocument d, int layer, double e, int y0, int y1, int gray) {
        d.selectLayer(layer);
        d.begin(e);
        int[] grays = new int[W], owns = new int[W];
        for (int y = y0; y < y1; y++) {
            for (int x = 0; x < W; x++) { int u = d.tone(x, y), g = (u * gray + 127) / 255; grays[x] = x < 10 ? -1 : g; owns[x] = 255 - gray; }
            d.setTones(0, y, W, 1, grays, owns);
        }
        d.finish();
    }
    static byte[] bytes(ToneDocument d) throws IOException { ByteArrayOutputStream o = new ByteArrayOutputStream(); DocumentCodec.write(o, d.layerSnapshot()); return o.toByteArray(); }
    static ToneDocument read(byte[] b) throws IOException { return DocumentCodec.read(new ByteArrayInputStream(b)); }
    static String magic(byte[] b) { return new String(b, 0, 4, java.nio.charset.StandardCharsets.US_ASCII); }
    static byte[] share(ToneDocument d, int layer) { byte[] s = d.layerSnapshot().layers.get(layer).share; return s == null ? null : s.clone(); }
    static int[] tones(ToneDocument d) { int[] t = new int[W * H]; for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) t[y * W + x] = d.compositeTone(x, y); return t; }
    /** Erases every pixel of every layer at full strength, as the eraser does (each layer a gesture). */
    static void eraseAll(ToneDocument d) {
        byte[] all = new byte[W * H]; Arrays.fill(all, (byte)1);
        for (int i = 0; i < d.layerCount(); i++) { d.selectLayer(i); d.begin(); d.eraseTones(0, 0, W, H, all); d.finish(); }
    }

    /** The write rule's exact cases on one layer. */
    static void rule() {
        ToneDocument d = marked();
        check(share(d, 0) == null && share(d, 1) != null, "Only the layer with permanent paint has a share");
        int permanentOnly = d.compositeTone(20, 45), both = d.compositeTone(20, 55), part = d.compositeTone(20, 80);
        eraseAll(d);
        check(d.compositeTone(20, 15) == 255, "Erasable paint erases to paper");
        check(d.compositeTone(20, 45) == permanentOnly, "Permanent paint stays as it was");
        check(Math.abs(d.compositeTone(20, 55) - permanentOnly * (1 - .6 * (255 - 150) / 255.0)) <= 1.5, "Over the permanent band, the band at .4 keeps 60% of its own: " + d.compositeTone(20, 55) + " (both " + both + ")");
        int kept = d.compositeTone(20, 80);
        check(Math.abs((255 - kept) - .6 * (255 - part)) <= 1.5, "A band at .4 keeps 60% of its darkness: " + kept + " of " + part);
        int[] once = tones(d);
        eraseAll(d);
        check(Arrays.equals(once, tones(d)), "A second pass takes nothing");
        // Erasable paint over permanent paint: erased, the permanent mark again.
        ToneDocument o = marked(), p = marked();
        band(o, 1, 1, 20, 70, 90);
        eraseAll(o); eraseAll(p);
        check(Arrays.equals(tones(o), tones(p)), "Erasable over permanent, erased: as without it");
        // The eraser's own pixel-by-pixel path (masking fluid on the page) keeps the same.
        ToneDocument m = marked();
        m.paintMaskingFluid(true); m.begin(); m.setTone(0, 0, 250); m.finish(); m.paintMaskingFluid(false);
        for (int i = 0; i < 2; i++) { m.selectLayer(i); m.begin(); for (int y = 0; y < H; y++) for (int x = 1; x < W; x++) m.eraseTone(x, y, 1); m.finish(); }
        check(m.compositeTone(20, 45) == permanentOnly && m.compositeTone(20, 15) == 255, "Erasing pixel by pixel (under masking fluid) keeps the permanent paint too");
    }

    /** Pages that keep nothing write as before: TSM3, or TSM4 with masking fluid, and read and write back byte for byte. */
    static void oldFormats() throws IOException {
        ToneDocument d = marked();
        eraseAll(d);
        ToneDocument plain = new ToneDocument(W, H, new ArrayList<>(Arrays.asList(new ToneDocument.Layer("Wash", true, ToneDocument.paper(W, H), new byte[W * H]),
                new ToneDocument.Layer("Ink", true, ToneDocument.paper(W, H), new byte[W * H]))), 0);
        band(plain, 0, 1, 10, 40, 120); band(plain, 1, 1, 30, 60, 60);
        byte[] b = bytes(plain);
        check(magic(b).equals("TSM3") && Arrays.equals(b, bytes(read(b))), "All-erasable paint writes TSM3, byte for byte after open and save");
        // A share that keeps nothing (a permanent gesture that laid nothing, or kept paint erased away) writes TSM3 too.
        plain.selectLayer(1); plain.begin(0); plain.finish();
        check(share(plain, 1) != null && Arrays.equals(bytes(plain), b), "A share that keeps nothing: TSM3, the same bytes");
        check(share(read(b), 1) == null, "and reads back without one");
        plain.paintMaskingFluid(true); plain.begin(); plain.setTone(5, 5, 200); plain.finish(); plain.paintMaskingFluid(false);
        byte[] m = bytes(plain);
        check(magic(m).equals("TSM4") && Arrays.equals(m, bytes(read(m))), "With masking fluid, TSM4 byte for byte");
    }

    static void roundTrip() throws IOException {
        ToneDocument d = marked();
        byte[] b = bytes(d);
        check(magic(b).equals("TSM5"), "Paint the eraser can't take writes TSM5");
        ToneDocument r = read(b);
        check(Arrays.equals(share(r, 1), share(d, 1)) && share(r, 0) == null && Arrays.equals(tones(r), tones(d)), "TSM5 reads back every share and tone");
        check(Arrays.equals(b, bytes(r)), "and writes back byte for byte");
        eraseAll(d); eraseAll(r);
        check(Arrays.equals(tones(r), tones(d)), "Erased after opening: as erased before saving");
        // With masking fluid: its flag, then the fluid, before the sections.
        ToneDocument f = marked();
        f.paintMaskingFluid(true); f.begin(); f.setTone(3, 3, 120); f.finish(); f.paintMaskingFluid(false);
        ToneDocument fr = read(bytes(f));
        check(magic(bytes(f)).equals("TSM5") && fr.hasMask() && Arrays.equals(share(fr, 1), share(f, 1)) && Arrays.equals(bytes(fr), bytes(f)), "TSM5 with masking fluid round-trips");
        check(magic(b).equals("TSM5") && b.length < 4000, "A share deflates small: " + b.length + " bytes for the page");
    }

    /** {@code page} (TSM5) with {@code extra} sections put before its end tag: the body inflated, the sections added, the checksum made again. */
    static byte[] withSections(byte[] page, byte[]... extra) throws IOException { return withSections(page, 0, extra); }
    /** As {@link #withSections(byte[], byte[]...)}, the body's last {@code drop} bytes before the end tag (its erasable section) left out first. */
    static byte[] withSections(byte[] page, int drop, byte[]... extra) throws IOException {
        java.util.zip.InflaterInputStream in = new java.util.zip.InflaterInputStream(new ByteArrayInputStream(page, 12, page.length - 12));
        ByteArrayOutputStream raw = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
        while ((n = in.read(buf)) > 0) raw.write(buf, 0, n);
        byte[] all = raw.toByteArray(), body = Arrays.copyOf(all, all.length - 8 - 4 - drop);
        ByteArrayOutputStream next = new ByteArrayOutputStream();
        next.write(body);
        for (byte[] e : extra) next.write(e);
        new DataOutputStream(next).writeInt(0);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32(); crc.update(next.toByteArray());
        new DataOutputStream(next).writeLong(crc.getValue());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(page, 0, 12);
        java.util.zip.DeflaterOutputStream z = new java.util.zip.DeflaterOutputStream(out);
        z.write(next.toByteArray()); z.finish();
        return out.toByteArray();
    }
    static byte[] section(int tag, byte[] data) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream(); DataOutputStream d = new DataOutputStream(o);
        d.writeInt(tag); d.writeInt(data.length); d.write(data); return o.toByteArray();
    }
    static void sections() throws IOException {
        ToneDocument d = marked();
        byte[] b = bytes(d), extra = withSections(b, section(0x58545241, new byte[]{1, 2, 3, 4, 5}));
        check(Arrays.equals(bytes(read(extra)), b), "An unknown section is skipped");
        check(Arrays.equals(bytes(read(withSections(b))), b), "The rebuilt page reads as written");
        // ERAS again: refused.
        byte[] eras = new byte[2 + W * H]; eras[0] = 0; eras[1] = 1; Arrays.fill(eras, 2, eras.length, (byte)255);
        refused(withSections(b, section(0x45524153, eras)), "A repeated erasable section");
    }
    static void refused(byte[] b, String what) {
        try { read(b); } catch (IOException expected) { return; }
        throw new AssertionError(what + " is refused");
    }
    static void damaged() throws IOException {
        byte[] b = bytes(marked());
        refused(Arrays.copyOf(b, b.length - 9), "A truncated TSM5 page");
        byte[] corrupt = b.clone(); corrupt[corrupt.length / 2] ^= 0x5a;
        refused(corrupt, "A corrupt TSM5 page");
        // A section longer than the page, and a flag that isn't 0 or 1, built well-formed but for that.
        ByteArrayOutputStream big = new ByteArrayOutputStream(); DataOutputStream dd = new DataOutputStream(big); dd.writeInt(0x51515151); dd.writeInt(Integer.MAX_VALUE);
        refused(withSections(b, big.toByteArray()), "A section longer than any page");
        // The page's own erasable section (two layer flags and one share) taken out and put back: as written; with a bad flag, or short: refused.
        int own = 8 + 2 + W * H;
        byte[] shares = share(marked(), 1), good = new byte[2 + W * H], badFlag = new byte[2 + W * H];
        good[1] = 1; System.arraycopy(shares, 0, good, 2, W * H);
        System.arraycopy(good, 0, badFlag, 0, good.length); badFlag[1] = 2;
        check(Arrays.equals(withSections(b, own, section(0x45524153, good)), b), "The erasable section rebuilt is the page as written");
        refused(withSections(b, own, section(0x45524153, badFlag)), "An erasable section with a flag that isn't 0 or 1");
        refused(withSections(b, own, section(0x45524153, Arrays.copyOf(good, good.length - 5))), "A short erasable section");
        byte[] longer = Arrays.copyOf(good, good.length + 3);
        refused(withSections(b, own, section(0x45524153, longer)), "An erasable section with bytes left over");
        // validate (an unshown page of a book) checks the sections too.
        try { DocumentCodec.validate(new ByteArrayInputStream(Arrays.copyOf(b, b.length - 9)), W, H); throw new AssertionError("validate refuses a truncated page"); }
        catch (IOException expected) { }
        DocumentCodec.validate(new ByteArrayInputStream(b), W, H);
    }

    static void books() throws IOException {
        DrawingBook book = new DrawingBook(marked());
        book.addPage();
        ToneDocument second = book.current();
        second.selectLayer(0); second.begin(0); second.setTones(0, 0, 3, 1, new int[]{0, 0, 0}, new int[]{255, 255, 255}); second.finish();
        book.select(0);
        ByteArrayOutputStream out = new ByteArrayOutputStream(); BookCodec.write(out, book.snapshot());
        byte[] zip = out.toByteArray();
        DrawingBook back = BookCodec.read(new ByteArrayInputStream(zip));
        check(back.count() == 2 && Arrays.equals(share(back.current(), 1), share(book.current(), 1)), "A book carries its pages' shares");
        back.select(1);
        check(share(back.current(), 0) != null && back.current().compositeTone(1, 0) == 0, "an unshown page's too");
        check(Arrays.equals(share(BookCodec.readFirstPage(new ByteArrayInputStream(zip)), 1), share(book.current(), 1)), "The preview reads a TSM5 first page");
        byte[] page = bytes(marked());
        check(Arrays.equals(share(BookCodec.read(new ByteArrayInputStream(page)).current(), 1), share(marked(), 1))
                && Arrays.equals(share(BookCodec.readFirstPage(new ByteArrayInputStream(page)), 1), share(marked(), 1)), "A single TSM5 page opens on its own");
        ByteArrayOutputStream rec = new ByteArrayOutputStream(); RecoveryCodec.write(rec, book.snapshot(), "");
        check(Arrays.equals(share(RecoveryCodec.read(new ByteArrayInputStream(rec.toByteArray())).book.current(), 1), share(book.current(), 1)), "Recovery carries the shares");
    }

    static void undo() {
        ArrayList<ToneDocument.Layer> layers = new ArrayList<>();
        layers.add(new ToneDocument.Layer("Ink", true, ToneDocument.paper(W, H), new byte[W * H]));
        ToneDocument d = new ToneDocument(W, H, layers, 0);
        band(d, 0, 1, 10, 40, 120);
        check(share(d, 0) == null, "Erasable paint makes no share");
        band(d, 0, 0, 20, 30, 40);
        byte[] after = share(d, 0);
        int[] shown = tones(d);
        check(after != null && d.undo() && share(d, 0) != null, "Undo of the first permanent paint keeps its share");
        for (byte s : share(d, 0)) check(s == (byte)255, "every pixel back at 255");
        check(d.undo() && d.redo() && d.redo() && Arrays.equals(share(d, 0), after) && Arrays.equals(tones(d), shown), "Redo brings the shares back byte for byte (a tile from before the share at 255)");
        eraseAll(d);
        int[] erased = tones(d); byte[] erasedShare = share(d, 0);
        check(d.undo() && Arrays.equals(share(d, 0), after) && Arrays.equals(tones(d), shown) && d.redo() && Arrays.equals(tones(d), erased) && Arrays.equals(share(d, 0), erasedShare),
                "Erasing is one step: undone and redone byte for byte, shares too");
        // A cancelled permanent gesture leaves the page and its shares as they were.
        d.begin(0); d.setTones(0, 60, 5, 1, new int[]{0, 0, 0, 0, 0}, new int[]{255, 255, 255, 255, 255}); d.cancel();
        check(Arrays.equals(share(d, 0), erasedShare) && Arrays.equals(tones(d), erased), "Cancel restores shares");
        // clear: one step, the shares 255 and back with Undo.
        check(d.clear() && d.compositeTone(20, 25) == 255, "Clear");
        for (byte s : share(d, 0)) check(s == (byte)255, "cleared to 255");
        check(d.undo() && Arrays.equals(share(d, 0), erasedShare), "Undo of clear brings the shares back");
        // A continuation (wet paint after lift) of a permanent gesture keeps it one step.
        int steps = d.undoSteps();
        d.begin(0); d.setTones(0, 70, 3, 1, new int[]{100, 100, 100}); d.finish();
        d.begin(0); d.setTones(0, 70, 3, 1, new int[]{50, 50, 50}); d.finishContinuation();
        check(d.undoSteps() == steps + 1 && d.undo() && Arrays.equals(share(d, 0), erasedShare), "A continuation is one step with its stroke, shares and all");
    }

    static void layers() {
        ToneDocument d = marked();
        byte[] s = share(d, 1);
        d.expand(2, 3, 0, 0);
        byte[] e = share(d, 1);
        check(e.length == (W + 2) * (H + 3) && e[0] == (byte)255 && e[(3 + 35) * (W + 2) + 2 + 20] == s[35 * W + 20], "Expand keeps the shares, paper's 255 around");
        check(d.undo(), "undo expand");
        check(d.restack(new int[]{1, 0}, null, 0) && share(d, 0) != null && share(d, 1) == null, "Restack carries a layer's share");
        check(d.clearAllLayers() && share(d, 0) == null && d.undo() && share(d, 0) != null, "Clearing every layer drops the shares, Undo brings them back");
        ToneDocument.Snapshot snap = d.layerSnapshot();
        d.selectLayer(0); d.begin(0); d.setTones(30, 100, 1, 1, new int[]{0}, new int[]{255}); d.finish();
        check(!Arrays.equals(snap.layers.get(0).share, share(d, 0)), "A snapshot keeps its own copy of a share once the page changes");
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
