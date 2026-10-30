package io.github.mpdairy.monopaint;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.CRC32;

/**
 * The covers' designs (docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md, "Covers"; {@link CoverDesign},
 * {@link CoverPattern}, {@link NotebookCovers}, {@link BookOptions}): each of the nine made from the name alone, the
 * same twice and the approved prototype's to the bit (docs/archive/brush-lab/prototypes/cover-designs.html, its {@code pattern}
 * at 240 × 300 for "Field notes", front and back); another name another cover, the back another part of the sheet; the
 * tape on the left of the front and the right of the back, its twill at full size only; the panel's greys only; a shelf
 * tile the page-sized cover smaller. The design kept as a tagged record in the options block only when it isn't
 * Classic Black Marble, read back from the file, the shelf's reading and the recovery; none, or one this version doesn't
 * know, Classic; the covers' paper and copies following it. The chooser fitting both tablets in both turns. Timings at
 * full size and a shelf tile's. {@code write <file>} writes a notebook with a design for the covers version
 * ({@code OlderDesignCheck}, 6e3bae8); {@code older <file>} opens what it wrote.
 */
public final class CoverDesignChecks {
    static final int W = CoverChecks.W, H = CoverChecks.H;
    /** The prototype's sheet at 240 × 300 for "Field notes" (its {@code pattern}, float32 little-endian), CRC-32, front then back. */
    static final Map<CoverDesign, long[]> PROTOTYPE = new EnumMap<>(CoverDesign.class);
    static {
        PROTOTYPE.put(CoverDesign.CLASSIC, new long[]{0xb9c09d2bL, 0x3147ab67L});
        PROTOTYPE.put(CoverDesign.SPECKLE, new long[]{0xdc35ebc6L, 0x6b7d534eL});
        PROTOTYPE.put(CoverDesign.BOLD, new long[]{0x9afc86e7L, 0x2c2bfe0bL});
        PROTOTYPE.put(CoverDesign.REVERSE, new long[]{0x329ad5fcL, 0x2c6b9973L});
        PROTOTYPE.put(CoverDesign.SWIRL, new long[]{0x2f5efc67L, 0xb0139220L});
        PROTOTYPE.put(CoverDesign.CRACKLE, new long[]{0x624e10d9L, 0x239642edL});
        PROTOTYPE.put(CoverDesign.TERRAZZO, new long[]{0x287b4f5dL, 0xe6d1f86dL});
        PROTOTYPE.put(CoverDesign.WOOD, new long[]{0x131e45d1L, 0xba979520L});
        PROTOTYPE.put(CoverDesign.ENGRAVED, new long[]{0xca15ba08L, 0xaddbe30cL});
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("write")) { Files.write(new File(args[1]).toPath(), designed()); return; }
        if (args.length == 2 && args[0].equals("older")) { older(new File(args[1])); return; }
        Assets.install(Assets.folder(Assets.FOLDER));
        generators(); textures(); windows(); sizes(); record(); paper();
        String times = times();
        System.out.println("PASS: cover designs: each of the nine generators the prototype's to the bit; each design's sheet in the app's assets, read and not made, the size it should be, ink and paper both, the five that tile wrapping round with no edge, the script making them again bit for bit (Woodgrain's and Engraved's, made again here); "
                + "a cover a window of its sheet from the name alone, the same twice, another name another window, every window inside its sheet (or wrapping round one that tiles), turned 8 ways where the design has no direction and only flipped where it has, the back further along the sheet; "
                + "the tape on the left of the front and the right of the back, its twill at full size only; the panel's greys only; a shelf tile the page's cover smaller; "
                + "the design a tagged record in the options block only when it isn't Classic Black Marble, read back whole, from the file, by the shelf and from the recovery; none or one unknown is Classic; "
                + "the covers' paper and their copies follow it");
        System.out.println("  " + times);
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static long crc(float[] shares) {
        ByteBuffer b = ByteBuffer.allocate(shares.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : shares) b.putFloat(f);
        CRC32 c = new CRC32(); c.update(b.array()); return c.getValue();
    }
    static int differ(byte[] a, byte[] b) { int n = 0; for (int i = 0; i < a.length; i++) if (a[i] != b[i]) n++; return n; }

    // ---- each generator ----

    static void generators() {
        check(CoverDesign.values().length == 9 && CoverDesign.DEFAULT == CoverDesign.CLASSIC && CoverDesign.of(null) == CoverDesign.CLASSIC && CoverDesign.of("plaid") == CoverDesign.CLASSIC,
                "Nine designs, Classic Black Marble the default, for none and for one unknown");
        Set<String> keys = new HashSet<>(), labels = new HashSet<>();
        byte[] classic = CoverPattern.tones(CoverDesign.CLASSIC, "Field notes", W, H, false);
        for (CoverDesign d : CoverDesign.values()) {
            check(CoverDesign.of(d.key) == d && keys.add(d.key) && labels.add(d.label), "Each design has its own key and name: " + d.key);
            check(Character.isUpperCase(d.label.charAt(0)) && !d.label.contains("  "), "Title Case names: " + d.label);
            // The prototype's sheet, to the bit, front and back.
            long[] expected = PROTOTYPE.get(d);
            check(crc(CoverPattern.shares(d, "Field notes", W, H, false)) == expected[0], d.label + "'s front is the prototype's");
            check(crc(CoverPattern.shares(d, "Field notes", W, H, true)) == expected[1], d.label + "'s back is the prototype's");
            // The name alone: the same twice, another for another name.
            byte[] a = CoverPattern.tones(d, "2026-10-19 brisk-bean notebook", W, H, false), again = CoverPattern.tones(d, "2026-10-19 brisk-bean notebook", W, H, false);
            byte[] other = CoverPattern.tones(d, "2026-10-19 brisk-bean notebooks", W, H, false), back = CoverPattern.tones(d, "2026-10-19 brisk-bean notebook", W, H, true);
            check(Arrays.equals(a, again), d.label + ": the same name makes the same cover");
            check(differ(a, other) > a.length / 20, d.label + ": another name makes another (" + differ(a, other) + " pixels differ)");
            check(differ(a, back) > a.length / 20, d.label + ": the back is another part of the sheet");
            if (d != CoverDesign.CLASSIC) check(differ(classic, CoverPattern.tones(d, "Field notes", W, H, false)) > classic.length / 10, d.label + " isn't Classic Black Marble");
        }
        // Shared over the cores or not, the same.
        boolean shared = Cores.shared;
        try { Cores.shared = false; check(crc(CoverPattern.shares(CoverDesign.BOLD, "Field notes", W, H, false)) == PROTOTYPE.get(CoverDesign.BOLD)[0], "The same made on one core"); }
        finally { Cores.shared = shared; }
    }

    // ---- the sheets ----

    /** Each design's sheet: in the assets, read (not made), its kind's size, ink and paper, wrapping round where it tiles; Woodgrain's and Engraved's made again by the script's code, bit for bit. */
    static void textures() {
        CoverTexture.forget();
        for (CoverDesign d : CoverDesign.values()) {
            CoverTexture t = CoverTexture.of(d);
            CoverTexture.Kind k = CoverTexture.kind(d);
            check(t != null && t.width == k.width && t.height == k.height && t.width % 16 == 0 && t.height % 16 == 0, d.label + ": its sheet is in the app's assets, " + k.width + " x " + k.height);
            long paper = 0;
            for (byte b : t.bits) paper += Integer.bitCount(b & 255);
            long all = (long) t.width * t.height;
            check(paper > all / 30 && paper < all - all / 30, d.label + ": its sheet has ink and paper (" + 100 * paper / all + " % paper)");
            check(k.tiles == (d == CoverDesign.CLASSIC || d == CoverDesign.SPECKLE || d == CoverDesign.BOLD || d == CoverDesign.REVERSE || d == CoverDesign.TERRAZZO)
                    && k.turns == (d != CoverDesign.WOOD && d != CoverDesign.ENGRAVED), d.label + ": tiles and turns as it should");
            if (k.tiles) {
                // Round the edge, texels differ no more than they do a texel apart inside it.
                int edge = 0, inside = 0;
                for (int y = 0; y < t.height; y++) { if (t.paper(t.width - 1, y) != t.paper(0, y)) edge++; if (t.paper(t.width / 2 - 1, y) != t.paper(t.width / 2, y)) inside++; }
                for (int x = 0; x < t.width; x++) { if (t.paper(x, t.height - 1) != t.paper(x, 0)) edge++; if (t.paper(x, t.height / 2 - 1) != t.paper(x, t.height / 2)) inside++; }
                check(edge <= 1.5 * inside + 20, d.label + ": its sheet wraps round with no edge (" + edge + " texels differ round it, " + inside + " inside)");
            }
            // The same sheet read twice; kept, not read again.
            check(CoverTexture.of(d) == t, d.label + ": its sheet is read once and kept");
        }
        for (CoverDesign d : new CoverDesign[]{CoverDesign.WOOD, CoverDesign.ENGRAVED}) {
            boolean[] made = CoverTextures.sheet(d);
            CoverTexture t = CoverTexture.of(d);
            boolean same = true;
            for (int y = 0; y < t.height && same; y++) for (int x = 0; x < t.width; x++) if (made[y * t.width + x] != t.paper(x, y)) { same = false; break; }
            check(same, d.label + ": scripts/cover_textures.sh makes the app's sheet again, bit for bit");
        }
        // A PNG not of the kind the script writes isn't read as one.
        try { CoverTexture.decode(CoverDesign.CLASSIC, new byte[]{1, 2, 3}); check(false, "Not a PNG"); } catch (IOException expected) { }
    }

    /** A cover's window: from the name alone, inside its sheet (or round one that tiles), turned only as its design allows, spread over the sheet. */
    static void windows() {
        int[][] sizes = {{1785, 2560}, {1920, 2400}, {1404, 1872}, {397, 569}, {240, 300}, {1785, 3400}};
        for (CoverDesign d : CoverDesign.values()) {
            CoverTexture t = CoverTexture.of(d);
            CoverTexture.Kind k = t.kind;
            Set<Integer> turns = new HashSet<>();
            Set<Integer> places = new HashSet<>();
            for (int n = 0; n < 200; n++) {
                String name = "2026-10-" + (10 + n % 20) + " notebook " + n;
                for (int[] size : sizes)
                    for (boolean back : new boolean[]{false, true}) {
                        CoverTexture.Window w = t.window(name, size[0], size[1], back), again = t.window(name, size[0], size[1], back);
                        check(w.turn == again.turn && w.x == again.x && w.y == again.y && w.per == again.per, d.label + ": the same name, the same window");
                        check(k.turns || (w.turn & 4) == 0, d.label + ": only flipped, never turned a quarter");
                        boolean across = (w.turn & 4) != 0;
                        double rw = across ? w.per * size[1] / size[0] : w.per, rh = across ? w.per : w.per * size[1] / size[0];
                        check(w.x >= 0 && w.y >= 0 && w.x < t.width && w.y < t.height, d.label + ": the window starts on its sheet");
                        if (!k.tiles) check(w.x + rw <= t.width + 1e-6 && w.y + rh <= t.height + 1e-6, d.label + ": the window inside its sheet, " + size[0] + " x " + size[1]);
                        else check(w.per == CoverTexture.PER_WIDTH, d.label + ": a sheet that tiles is never shrunk");
                        if (size[0] == 1785 && size[1] == 2560 && !k.tiles) check(w.per == CoverTexture.PER_WIDTH, d.label + ": the Manta's cover is the sheet one for one");
                        if (size[0] == 1785 && !back) { turns.add(w.turn); places.add((w.x / 256) * 64 + w.y / 256); }
                    }
            }
            check(turns.size() == (k.turns ? 8 : 4), d.label + ": turned every way it may be (" + turns.size() + ")");
            check(places.size() >= 12, d.label + ": windows all over the sheet (" + places.size() + " places)");
            // Front and back differ; another name, another cover.
            String name = "2026-10-19 brisk-bean notebook";
            byte[] a = CoverPattern.tones(d, name, 397, 569, false), b = CoverPattern.tones(d, name, 397, 569, true), c = CoverPattern.tones(d, name + "s", 397, 569, false);
            check(differ(a, b) > a.length / 20 && differ(a, c) > a.length / 20, d.label + ": the back, and another name, are other parts of the sheet");
        }
        // The Manta's cover is the sheet's texels one for one: black and white only, but for the tape's twill.
        byte[] manta = CoverPattern.tones(CoverDesign.CLASSIC, "2026-10-19 brisk-bean notebook", 1785, 2560, false);
        for (byte v : manta) check(v == 0 || v == (byte) 255 || v == (byte) 80, "The Manta's cover black and white, texel for pixel");
    }

    // ---- full size and a shelf tile's ----

    static void sizes() {
        Set<Integer> greys = new HashSet<>(); for (int g : GrayPalette.VALUES) greys.add(g);
        for (CoverDesign d : CoverDesign.values()) {
            String name = "2026-10-19 brisk-bean notebook";
            for (int[] size : new int[][]{{1920, 2400}, {375, 469}}) {
                int w = size[0], h = size[1], spine = CoverPattern.spine(w);
                boolean full = w >= CoverPattern.SMALL;
                for (boolean back : new boolean[]{false, true}) {
                    byte[] t = CoverPattern.cached(d, name, w, h, back);
                    check(t.length == w * h, "The size asked for");
                    int ink = 0, paper = 0, twill = 0, x0 = back ? w - spine : 0;
                    for (int y = 0; y < h; y++)
                        for (int x = 0; x < w; x++) {
                            int tone = t[y * w + x] & 255;
                            check(greys.contains(tone), d.label + ": the panel's greys only, " + tone);
                            boolean tape = x >= x0 && x < x0 + spine;
                            if (tape) { check(tone == 0 || full && tone == 80, d.label + ": the tape is ink, its twill the darkest grey"); if (tone == 80) twill++; }
                            else if (tone < 192) ink++; else paper++;   // a tile's fine cracks and threads are greys
                        }
                    int printed = (w - spine) * h;
                    check(full == (twill > spine * h / 30), d.label + ": the twill at full size only (" + twill + ")");
                    check(ink > printed / 30 && paper > printed / 30, d.label + ": ink and paper both (" + 100 * ink / printed + " % ink, " + 100 * paper / printed + " % paper)");
                    // The tape's side: the band as wide on the other side is printed, not all ink.
                    int other = back ? 0 : w - spine, light = 0;
                    for (int y = 0; y < h; y++) for (int x = other; x < other + spine; x++) if ((t[y * w + x] & 255) >= 128) light++;
                    check(light > 0, d.label + ": the tape on the " + (back ? "right of the back" : "left of the front"));
                }
            }
            // A shelf tile is the page's cover smaller.
            byte[] big = CoverPattern.tones(d, name, 1920, 2400, false), tile = CoverPattern.tones(d, name, 240, 300, false);
            byte[] down = NotebookCovers.resampled(1920, 2400, (x, y) -> big[y * 1920 + x] & 255, 240, 300);
            long off = 0; for (int i = 0; i < tile.length; i++) off += Math.abs((tile[i] & 255) - (down[i] & 255));
            check(off / tile.length < 40, d.label + ": a tile's cover is the page's, smaller (mean difference " + off / tile.length + ")");
        }
    }

    // ---- the design kept ----

    static byte[] bytes(DrawingBook book) throws IOException { return CoverChecks.bytes(book); }
    /** A notebook whose covers are drawn on, the front's picture page 2, in Terrazzo. */
    static byte[] designed() throws IOException {
        DrawingBook book = CoverChecks.read(CoverChecks.covered(), "2026-10-19 brisk-bean notebook");
        book.setDesign(CoverDesign.TERRAZZO);
        return bytes(book);
    }
    static BookOptions.Found options(byte[] file) {
        return BookOptions.find(BookOptions.of(file), 0, BookPictures.start(file, BookPictures.readLong32(file, file.length - 22 + 16)));
    }

    static void record() throws Exception {
        // The default isn't written: an undrawn Classic notebook has no options block, byte for byte as before.
        DrawingBook plain = CoverChecks.book(2, "Loose pages");
        plain.markSaved(plain.snapshot());
        byte[] before = bytes(plain);
        check(options(before) == null && plain.design() == CoverDesign.CLASSIC, "Classic Black Marble isn't written: no options block");
        plain.setDesign(CoverDesign.CLASSIC);
        check(!plain.unsaved(), "Choosing the design in use changes nothing");
        // Each design round trips: written as its record, read back whole, from the file and by the shelf.
        File dir = Files.createTempDirectory("designs").toFile();
        try {
            for (CoverDesign d : CoverDesign.values()) {
                DrawingBook book = CoverChecks.book(2, "Loose pages");
                book.markSaved(book.snapshot());
                book.setDesign(d);
                check(book.unsaved() == (d != CoverDesign.CLASSIC) && book.design() == d, "Another design is a change to save: " + d.key);
                byte[] file = bytes(book);
                BookOptions.Found found = options(file);
                check((found != null) == (d != CoverDesign.CLASSIC), "An options block only for a design that isn't the default: " + d.key);
                if (found != null) {
                    BookOptions.Record r = found.get(BookOptions.DESIGN);
                    check(r != null && found.records.size() == 1 && new String(BookOptions.of(file).read(r.offset, r.length), "UTF-8").equals(d.key), "Its record is the design's key alone");
                }
                check(CoverChecks.read(file, "Loose pages").design() == d, "Read back whole: " + d.key);
                File f = new File(dir, d.key + ".mpaint"); Files.write(f.toPath(), file);
                DrawingBook opened = BookCodec.open(f);
                check(opened.design() == d && !opened.unsaved(), "Opened from its file: " + d.key);
                if (opened.packed(0) != null && opened.packed(0).file() != null) opened.packed(0).file().close();
                check(BookPictures.readCover(f).design == d && BookCodec.readPages(new ByteArrayInputStream(file)).covers.design == d, "The shelf reads it: " + d.key);
                // Back to Classic: the block goes again.
                book.setDesign(CoverDesign.CLASSIC);
                check(Arrays.equals(bytes(book), before), "Back to Classic, the file is as it was");
            }
            // With the covers drawn on and a cover picture, beside their records; saved again from its file.
            byte[] file = designed();
            BookOptions.Found found = options(file);
            check(found.get(BookOptions.DESIGN) != null && found.get(BookOptions.FRONT) != null && found.get(BookOptions.COVER_PICTURE) != null, "Beside the covers' records");
            DrawingBook book = CoverChecks.read(file, "2026-10-19 brisk-bean notebook");
            check(book.design() == CoverDesign.TERRAZZO && book.coverDrawn(DrawingBook.FRONT) && book.cover() == 2 && book.count() == 3, "Read back with the covers");
            File f = new File(dir, "designed.mpaint"); Files.write(f.toPath(), file);
            DrawingBook opened = BookCodec.open(f);
            ByteArrayOutputStream out = new ByteArrayOutputStream(); BookCodec.write(out, opened.snapshot());
            check(Arrays.equals(out.toByteArray(), file), "Saved again from its file, byte for byte");
            if (opened.packed(0) != null && opened.packed(0).file() != null) opened.packed(0).file().close();
            // The recovery keeps it.
            out.reset();
            RecoveryCodec.write(out, book.snapshot(), "");
            check(RecoveryCodec.read(new ByteArrayInputStream(out.toByteArray())).book.design() == CoverDesign.TERRAZZO, "The recovery keeps the design");
            out.reset();
            RecoveryCodec.write(out, CoverChecks.book(1, "x").snapshot(), "");
            check(RecoveryCodec.read(new ByteArrayInputStream(out.toByteArray())).book.design() == CoverDesign.CLASSIC, "and Classic without one");
        } finally { NotebookMigrationChecks.delete(dir); }
        // A design this version doesn't know (a later one's) is Classic; the rest of the block reads as before.
        BookOptions.Covers later = new BookOptions.Covers();
        later.picture = 1; later.design = CoverDesign.TERRAZZO;
        byte[] block = BookOptions.bytes(later);
        String all = new String(block, "ISO-8859-1");
        int at = all.indexOf("terrazzo");
        check(at > 0, "The record holds the key");
        for (int i = 0; i < 8; i++) block[at + i] = (byte) "plaid-01".charAt(i);
        BookOptions.Found unknown = BookOptions.find(BookOptions.of(block), 0, block.length);
        BookOptions.Covers read = BookOptions.covers(unknown, BookOptions.of(block), null, 3);
        check(read.design == CoverDesign.CLASSIC && read.picture == 1, "An unknown design is Classic, the other records read");
    }

    // ---- the covers' paper and copies ----

    static void paper() throws Exception {
        DrawingBook book = CoverChecks.book(2, "2026-10-19 brisk-bean notebook");
        book.select(DrawingBook.FRONT);
        byte[] classic = book.current().paperPixels();
        check(Arrays.equals(classic, NotebookCovers.paper(true, CoverDesign.CLASSIC, "2026-10-19 brisk-bean notebook", W, H, null)), "An old notebook's covers are Classic Black Marble");
        int[] at = CoverChecks.onLabel();
        CoverChecks.mark(book.current(), at[0], at[1], 6, 20);
        book.setDesign(CoverDesign.WOOD);
        check(book.current().paperPixels() == classic, "The cover drawn on keeps its paper until it is refreshed (its pattern may take a while)");
        check(book.refreshCovers() && !Arrays.equals(book.current().paperPixels(), classic), "Refreshed, it is Woodgrain");
        check(Arrays.equals(book.current().paperPixels(), NotebookCovers.paper(true, CoverDesign.WOOD, "2026-10-19 brisk-bean notebook", W, H, null)), "Woodgrain's front, label and all");
        check(book.current().compositeTone(at[0] + 2, at[1] + 2) == 20, "The drawing on it unchanged, over its new pattern");
        book.select(DrawingBook.BACK);
        check(Arrays.equals(book.current().paperPixels(), CoverPattern.cached(CoverDesign.WOOD, "2026-10-19 brisk-bean notebook", W, H, true)), "The back turned to is Woodgrain's, the tape on its right");
        // The label on every design, at the page's size and a tile's.
        for (CoverDesign d : CoverDesign.values()) {
            byte[] front = NotebookCovers.paper(true, d, "2026-10-19 brisk-bean notebook", W, H, null);
            NotebookCovers.Label l = NotebookCovers.label("2026-10-19 brisk-bean notebook", W, H);
            int in = l.inset + l.rule + l.gap + l.hairline + 1;
            check((front[(l.y + l.height / 2) * W + l.x + in] & 255) == 255 && (front[(l.y + l.height / 2) * W + l.x + l.inset] & 255) < 255, d.label + ": the white label and its rule");
        }
        // The copies: another design draws both covers again.
        DrawingBook copied = CoverChecks.read(CoverChecks.covered(), "Loose pages");
        DrawingBook.Snapshot s = copied.snapshot();
        List<PackedPage> pages = s.packed();
        PackedPage[] covers = s.packedCovers();
        File dir = Files.createTempDirectory("design-copies").toFile();
        try {
            File export = new File(dir, "EXPORT");
            NotebookExport.update(export, "Loose pages", pages, null, new NotebookExport.Covers("Loose pages", CoverDesign.CLASSIC, W, H, covers[0], covers[1], s.cover));
            check(NotebookExport.update(export, "Loose pages", pages, null, new NotebookExport.Covers("Loose pages", CoverDesign.CRACKLE, W, H, covers[0], covers[1], s.cover)).drawn == 2,
                    "Another design draws 00-cover.png and zz-back.png again");
            File front = new File(new File(export, "Loose pages"), NotebookExport.FRONT_PNG);
            check(NotebookExport.read(Files.readAllBytes(front.toPath())).crc == NotebookCovers.key(true, CoverDesign.CRACKLE, "Loose pages", W, H, covers[0].crc, pages.get(2).crc),
                    "Its PNG carries its design");
            java.awt.image.BufferedImage back = javax.imageio.ImageIO.read(new File(new File(export, "Loose pages"), NotebookExport.BACK_PNG));
            boolean tape = true;
            for (int y = 0; y < H; y += 7) tape &= (back.getRGB(W - 2, y) & 255) < 40;
            check(tape, "The back's PNG has its tape on the right");
        } finally { NotebookMigrationChecks.delete(dir); }
    }

    // ---- timings ----

    /** The Manta's cover and shelf tile, and the desktop checks' full size and tile. */
    static final int[][] TIMED = {{1785, 2560}, {397, 569}, {1920, 2400}, {375, 469}};
    /**
     * Each design's front (label and all, as the app's covers) made from nothing, the best of three, from its sheet over the
     * cores as the app makes them (and on one), the sheet already read; its sheet read (the PNG decoded) and averaged down
     * for a tile, the first time; and by its generator, as before, at full size and a tile's. Bounds generous, for slower
     * machines: a sheet's tile under 25 ms, its full size under 100 ms (the Manta about 20 times slower).
     */
    static String times() {
        StringBuilder s = new StringBuilder("Desktop Java, ms, the best of three, from the sheet over the cores (on one): Manta full 1785 x 2560 / Manta tile 397 x 569 / full 1920 x 2400 / tile 375 x 469; "
                + "reading the sheet and averaging it down for a tile, first time; the generator (before) full 1920 x 2400 / tile 375 x 469 over the cores:");
        boolean shared = Cores.shared;
        try {
            for (CoverDesign d : CoverDesign.values()) {
                double[] t = new double[TIMED.length], one = new double[TIMED.length];
                for (int k = 0; k < TIMED.length; k++) {
                    t[k] = best(d, TIMED[k]);
                    Cores.shared = false;
                    one[k] = best(d, TIMED[k]);
                    Cores.shared = shared;
                }
                Timing.limit(t[1] < 25 && t[3] < 25 && t[0] < 100 && t[2] < 100, d.label + ": a sheet's tile under 25 ms and its full size under 100 ms (" + t[1] + ", " + t[0] + ")");
                // The sheet read and averaged down, first time.
                CoverTexture.forget();
                long t0 = System.nanoTime();
                CoverTexture sheet = CoverTexture.of(d);
                double read = (System.nanoTime() - t0) / 1e6;
                t0 = System.nanoTime();
                sheet.level(2);
                double down = (System.nanoTime() - t0) / 1e6;
                // The generator, as before.
                double[] before = new double[2];
                for (int k = 0; k < 2; k++) {
                    int w = k == 0 ? 1920 : 375, h = k == 0 ? 2400 : 469;
                    double fastest = Double.MAX_VALUE;
                    for (int run = 0; run < 2; run++) {
                        long g0 = System.nanoTime();
                        CoverPattern.generated(d, "before " + run, w, h, false);
                        fastest = Math.min(fastest, (System.nanoTime() - g0) / 1e6);
                    }
                    before[k] = fastest;
                }
                s.append(String.format(Locale.ROOT, " %s %.1f / %.1f / %.1f / %.1f (%.1f / %.1f / %.1f / %.1f), read %.0f + %.1f, before %.0f / %.0f;",
                        d.label, t[0], t[1], t[2], t[3], one[0], one[1], one[2], one[3], read, down, before[0], before[1]));
            }
        } finally { Cores.shared = shared; }
        return s.toString();
    }
    /** {@code design}'s front at {@code size}, made three times for new names, the fastest, ms. */
    private static double best(CoverDesign design, int[] size) {
        CoverPattern.forget();
        CoverTexture.of(design).level(2);
        double best = Double.MAX_VALUE;
        for (int run = 0; run < 3; run++) {
            long t0 = System.nanoTime();
            NotebookCovers.paper(true, design, "time " + size[0] + " " + run + " " + System.nanoTime(), size[0], size[1], null);
            best = Math.min(best, (System.nanoTime() - t0) / 1e6);
        }
        return best;
    }

    // ---- an older version's notebook ----

    /** What the covers version (6e3bae8, OlderDesignCheck) wrote of a notebook in Terrazzo: its covers kept, its design (unknown there) gone, so Classic. */
    static void older(File file) throws Exception {
        DrawingBook book = CoverChecks.read(Files.readAllBytes(file.toPath()), "2026-10-19 brisk-bean notebook");
        check(book.count() == 3 && book.coverDrawn(DrawingBook.FRONT) && book.coverDrawn(DrawingBook.BACK) && book.cover() == 2, "Saved by the covers version, its covers are kept");
        check(book.design() == CoverDesign.CLASSIC, "and its design, unknown there, is Classic again");
        System.out.println("PASS: a notebook with a design opens in the covers version (6e3bae8), its covers as drawn, the design's record passed by; saved there, it opens here with its covers, in Classic Black Marble");
    }
}
