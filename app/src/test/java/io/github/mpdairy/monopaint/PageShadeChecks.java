package io.github.mpdairy.monopaint;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.DeflaterOutputStream;

/**
 * The page's shade ({@link PageShade}, {@link ToneDocument#shadePaper}): the Fill page button only on a page with no
 * marks, filling as one Undo step, again while still unmarked, hidden once the pen goes down or its time is up; the
 * eraser back to the shade; transparent paint only darkening it, opaque paint and White lighter; brushes laying the same
 * paint on any shade; TSM5's PAPR section, an old file on white, a white page byte for byte as before; notebooks'
 * pictures, PNGs and pages keeping it.
 */
public final class PageShadeChecks {
    static final int W = 240, H = 180;
    static final String INK = "steady-line.M00il00000000at_000zz000000000000000_0_00r_1jz_84w0000000000000000nz0.51j8ud",
            WASH = "wash-behind.M60i000000000at_1ulat00770000ee2_0_000_1jz_0000000000000000000nz0.11kx",
            GOUACHE = "opaque-round.M80b70000000054_2zz00at_0_000_1jz_83z0000000000000000nz0.t4mf73",
            STUMP = "blend-stump.M50i00000000yat_3eqlj00we_3ssll00zz_1nl_1jz_8pthh00x9is00000000nz0.51j8ud",
            WHITE_GEL = BlendChecks.WHITE_GEL, INVERT_INK = BlendChecks.INVERT_INK;

    public static void main(String[] args) throws IOException {
        Assets.install(Assets.folder(Assets.FOLDER));
        offers(); fills(); timing(); eraser(); darkens(); lightens(); paper(); files(); older(); white(); notebooks();
        System.out.println("PASS: page shade: Fill page only on a page with no marks (no paint, fluid or magnets) and not already that shade, White page for the lightest grey on a shaded page; "
                + "it fills as one Undo step, again while unmarked, and Undo gives each shade back; gone once the pen goes down or after 4 s, and on a page marked since; "
                + "the eraser rubs back to the shade; ink and wash only darken it, a mark lighter than it barely; gouache and White ink go lighter (scratchboard); every brush lays the same paint on any shade, the paper's tooth too; "
                + "PAPR round-trips beside magnets, a later one read, a bad or repeated one refused; old files open white; a white page is byte for byte as before; notebooks' pictures, PNGs and pages keep each page's shade");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    // ---- pages and strokes (the drawn page is the one whose bytes were pinned before page shades) ----

    static ModularBrush brush(String code) { ModularBrush b = ModularCode.decode(code); check(b != null, "decodes: " + code); return b; }
    static ModularBrush instant(ModularBrush b) { ModularBrush c = b.copy(); c.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.NONE)); return c; }
    static ModularBrush own(ModularBrush b) { ModularBrush c = b.copy(); c.part(BrushPart.MEDIUM).set(PartDial.M_BLEND, 0); return c; }
    static ModularBrush erasable(ModularBrush b) { ModularBrush c = b.copy(); c.part(BrushPart.MEDIUM).set(PartDial.ERASABLE, 1); return c; }
    static double[][] across(double x0, double x1, double y) {
        int n = (int)Math.ceil(Math.abs(x1 - x0) / 2.5);
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) o[i] = new double[]{x0 + (x1 - x0) * i / n, y, .8, i * 4};
        return o;
    }
    static void live(ToneDocument d, ModularBrush b, double size, double darkness, double[][] path) { live(d, b, size, darkness, null, path); }
    static void live(ToneDocument d, ModularBrush b, double size, double darkness, WatercolorSession session, double[][] path) {
        DrawingStroke s = ModularStroke.start(d, b, size, darkness, session);
        for (int i = 0; i < path.length; i++) {
            double[] p = path[i];
            s.sample((float)p[0], (float)p[1], ToolSettings.pressure((float)p[2]), 0, 0, (long)p[3]);
            if (i % 3 == 2) s.present();
        }
        s.present();
        s.finish();
    }
    /** A page with an ink line, a wash band and a light opaque stroke, on white. */
    static ToneDocument drawn() {
        ToneDocument d = InkWashLayers.blank(W, H);
        // The wash at .6 as kept before washes reached black (ModularSizes.sinceBlack): the page pinned then.
        live(d, instant(own(brush(WASH))), 40, ModularSizes.sinceBlack(brush(WASH), .6), across(20, 220, 60));
        live(d, instant(brush(INK)), 6, 1, across(30, 210, 50));
        live(d, instant(brush(GOUACHE)), 24, .2, across(40, 200, 120));
        return d;
    }
    static ToneDocument shaded(int tone) { ToneDocument d = InkWashLayers.blank(W, H); check(d.shadePaper(tone), "shades"); return d; }
    static int[] shown(ToneDocument d) { int[] t = new int[W * H]; for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) t[y * W + x] = d.compositeTone(x, y); return t; }
    static boolean all(ToneDocument d, int tone) { for (int v : shown(d)) if (v != tone) return false; return true; }
    static byte[] bytes(ToneDocument d) throws IOException { return DrawingBook.encode(d); }
    static ToneDocument read(byte[] b) throws IOException { return DocumentCodec.read(new ByteArrayInputStream(b)); }
    static String magic(byte[] b) { return new String(b, 0, 4, java.nio.charset.StandardCharsets.US_ASCII); }
    static long crc(byte[] b) { CRC32 c = new CRC32(); c.update(b); return c.getValue(); }
    static long crc(int[] v) { java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(v.length * 4); for (int x : v) b.putInt(x); return crc(b.array()); }
    /** Whether every layer of {@code a} shows the same tone over white as {@code b}'s, pixel by pixel: the same paint, however it is held. */
    static boolean samePaint(ToneDocument a, ToneDocument b) {
        if (a.layerCount() != b.layerCount()) return false;
        ToneDocument.Snapshot x = a.layerSnapshot(), y = b.layerSnapshot();
        for (int n = 0; n < x.layers.size(); n++)
            for (int i = 0; i < W * H; i++) if (onWhite(x.layers.get(n), i) != onWhite(y.layers.get(n), i)) return false;
        return true;
    }
    static int onWhite(ToneDocument.Layer l, int i) { int t = l.tones[i] & 255, a = l.alpha[i] & 255; return (t * a + 255 * (255 - a) + 127) / 255; }
    static ModularBrush preset(String name) { return brush(FloatChecks.find(name).code); }
    static boolean sameLayers(ToneDocument a, ToneDocument b) {
        if (a.layerCount() != b.layerCount()) return false;
        ToneDocument.Snapshot x = a.layerSnapshot(), y = b.layerSnapshot();
        for (int i = 0; i < x.layers.size(); i++)
            if (!Arrays.equals(x.layers.get(i).tones, y.layers.get(i).tones) || !Arrays.equals(x.layers.get(i).alpha, y.layers.get(i).alpha)) return false;
        return true;
    }
    /** The bar's three greys, dark to lighter. */
    static final int[] GREYS = {40, 120, 200};

    // ---- the button ----

    static void offers() {
        ToneDocument blank = InkWashLayers.blank(W, H);
        PageShade.Offer dark = PageShade.offer(blank, GREYS, 0, 0);
        check(dark != null && dark.tone == 40 && dark.label().equals("Fill page"), "A grey picked on a page with no marks offers Fill page in that grey");
        check(PageShade.offer(blank, GREYS, 2, 0) == null, "The lightest grey on white paper offers nothing: the page is white already");
        check(PageShade.tone(new int[]{0, 255, 120}, 1) == 255 && PageShade.tone(new int[]{90, 90, 60}, 0) == 255 && PageShade.tone(GREYS, 1) == 120,
                "The lightest grey (or one at 255, or tied for lightest) stands for white paper");
        ToneDocument inked = InkWashLayers.blank(W, H);
        live(inked, instant(brush(INK)), 6, 1, across(30, 60, 50));
        check(!inked.unmarked() && PageShade.offer(inked, GREYS, 0, 0) == null, "A page with a mark offers nothing");
        ToneDocument worded = InkWashLayers.blank(W, H);
        worded.setMagnets(Magnets.NONE.with(new Magnets.Magnet("moon", 70, 50, 0, 0)));
        check(PageShade.offer(worded, GREYS, 0, 0) == null, "Nor one with a magnet");
        ToneDocument fluid = InkWashLayers.blank(W, H);
        fluid.beginFluid(1); fluid.setTone(5, 5, 0); fluid.finish();
        check(PageShade.offer(fluid, GREYS, 0, 0) == null, "Nor one with masking fluid");
        ToneDocument erased = InkWashLayers.blank(W, H);
        live(erased, instant(brush(INK)), 6, 1, across(30, 60, 50));
        check(erased.undo() && erased.unmarked() && PageShade.offer(erased, GREYS, 0, 0) != null, "A mark taken back by Undo leaves the page unmarked again");
        ToneDocument shade = shaded(40);
        check(PageShade.offer(shade, GREYS, 0, 0) == null, "Nor the shade the page has already");
        PageShade.Offer white = PageShade.offer(shade, GREYS, 2, 0);
        check(white != null && white.tone == 255 && white.label().equals("White page") && white.note().equals("Page white") && dark.note().equals("Page shaded"),
                "On a shaded page the lightest grey offers White page");
        // The button's words: two words at most, no hints (docs/TEXT-STYLE.md).
        for (String s : new String[]{PageShade.FILL, PageShade.WHITE, PageShade.SHADED, PageShade.WHITENED}) check(s.split(" ").length <= 2 && !s.contains("ndo"), "short words: " + s);
    }
    static void fills() {
        ToneDocument d = InkWashLayers.blank(W, H);
        int steps = d.undoSteps();
        PageShade.Offer offer = PageShade.offer(d, GREYS, 1, 0);
        check(offer.take(d, 100) && d.paperTone() == 120 && all(d, 120), "Fill page shades the whole page that grey");
        check(d.undoSteps() == steps + 1 && d.unmarked(), "as one Undo step, and the page still has no marks");
        PageShade.Offer again = PageShade.offer(d, GREYS, 0, 200);
        check(again != null && again.take(d, 300) && d.paperTone() == 40, "Still unmarked, another grey shades it again");
        PageShade.Offer back = PageShade.offer(d, GREYS, 2, 400);
        check(back != null && back.take(d, 500) && d.paperTone() == 255 && all(d, 255), "White page gives plain white paper back");
        check(d.undo() && d.paperTone() == 40 && all(d, 40), "Undo takes the page back to its shade before");
        check(d.undo() && d.paperTone() == 120 && d.undo() && d.paperTone() == 255 && !d.canUndo(), "and Undo again, step by step, to white");
        check(d.redo() && d.paperTone() == 120 && d.redo() && d.paperTone() == 40, "Redo shades it again");
        check(!offer.take(d, 600), "An offer taken is gone");
        // Drawn on after filling: Undo takes the stroke back, then the shade.
        live(d, instant(brush(INK)), 6, 1, across(30, 60, 50));
        check(d.undo() && d.paperTone() == 40 && d.unmarked() && d.undo() && d.paperTone() == 120, "Undo takes back a stroke on the shade, then the shade");
    }
    static void timing() {
        ToneDocument d = InkWashLayers.blank(W, H);
        PageShade.Offer offer = PageShade.offer(d, GREYS, 0, 1000);
        check(offer.shown(1000) && offer.shown(1000 + PageShade.OFFER_MS - 1) && !offer.shown(1000 + PageShade.OFFER_MS), "The button stays 4 s, then goes");
        check(!offer.take(d, 1000 + PageShade.OFFER_MS) && d.paperTone() == 255 && !d.canUndo(), "Too late, its tap changes nothing");
        PageShade.Offer pen = PageShade.offer(d, GREYS, 0, 0);
        pen.penDown();
        check(!pen.shown(1) && !pen.take(d, 1) && d.paperTone() == 255, "The pen going down takes it away: drawing ignores it");
        PageShade.Offer marked = PageShade.offer(d, GREYS, 0, 0);
        live(d, instant(brush(INK)), 6, 1, across(30, 60, 50));
        check(!marked.take(d, 10) && d.paperTone() == 255, "A page marked since (a finger, say) isn't filled");
    }

    // ---- the shade is the paper's ----

    static void eraser() {
        ToneDocument d = shaded(90);
        live(d, erasable(instant(own(brush(WASH)))), 40, .8, across(20, 220, 60));
        live(d, erasable(instant(brush(INK))), 6, 1, across(30, 210, 60));
        check(d.compositeTone(120, 60) < 60, "marked");
        EraseBothStroke rub = new EraseBothStroke(d, 60f);
        int[] preview = new int[1];
        long t = 0;
        for (int y = 20; y <= 100; y += 10) for (double[] p : across(-10, 250, y)) rub.sample((float)p[0], (float)p[1], 1f, 0, 0, t += 30);
        rub.erasedTones(120, 60, 1, 1, preview);
        rub.finish();
        check(preview[0] == 90, "While erasing, the screen shows the shade where it went: " + preview[0]);
        check(all(d, 90) && d.unmarked() && d.paperTone() == 90, "The eraser rubs back to the shade, not to white");
    }
    static void darkens() {
        for (int tone : new int[]{150, 60}) {
            ToneDocument white = InkWashLayers.blank(W, H), shade = shaded(tone);
            for (ToneDocument d : new ToneDocument[]{white, shade}) {
                // The wash at .6 as kept before washes reached black (ModularSizes.sinceBlack): the page pinned then.
        live(d, instant(own(brush(WASH))), 40, ModularSizes.sinceBlack(brush(WASH), .6), across(20, 220, 60));
                live(d, instant(brush(INK)), 6, .5, across(30, 210, 120));
            }
            int[] on = shown(shade), off = shown(white);
            int darker = 0;
            for (int i = 0; i < on.length; i++) {
                check(on[i] <= tone, "Ink and wash never lighten the shade: " + on[i] + " over " + tone);
                // Like tinted paper: the paint's own darkness times the paper's.
                check(Math.abs(on[i] - Math.round(off[i] * tone / 255f)) <= 2, "the paint over the shade is its tone on white times the shade's: " + on[i] + " for " + off[i] + " on white");
                if (on[i] < tone - 4) darker++;
            }
            check(darker > 1000, "and darken it where they lie");
        }
        // Washes that settle lighter where they lay darker a moment before (Slow drying) stay transparent: never lighter than the paper.
        for (String name : new String[]{"Slow ink wash", "Slow bloom", "Pooling wash", "Wet blooms", "Flat wash"}) {
            ToneDocument wet = shaded(120);
            WatercolorSession session = new WatercolorSession();
            live(wet, preset(name), 30, .7, session, across(20, 220, 90));
            session.settleNow(wet);
            int most = 0, darker = 0;
            for (int v : shown(wet)) { most = Math.max(most, v - 120); if (v < 110) darker++; }
            check(most <= 0 && darker > 500, name + " only darkens the shade: " + most + " lighter at most");
        }
        // A wash lighter than the shade barely shows.
        ToneDocument d = shaded(70);
        live(d, instant(own(brush(WASH))), 40, .1, across(20, 220, 60));
        int most = 0;
        for (int v : shown(d)) most = Math.max(most, 70 - v);
        check(most > 0 && most <= 12, "A pale wash over a dark shade barely shows: " + most + " steps at most");
    }
    static void lightens() {
        ToneDocument d = shaded(40);
        live(d, instant(brush(GOUACHE)), 24, .2, across(40, 200, 90));
        int lighter = 0;
        for (int v : shown(d)) if (v > 40 + 60) lighter++;
        check(lighter > 1000, "Gouache covers the shade, lighter than it: " + lighter + " px");
        ToneDocument black = shaded(0);
        live(black, instant(brush(WHITE_GEL)), 8, 1, across(40, 200, 90));
        int white = 0;
        for (int v : shown(black)) if (v > 200) white++;
        check(white > 300 && black.compositeTone(120, 90) > 200 && black.compositeTone(120, 20) == 0, "White ink on a black page: scratchboard (" + white + " px)");
        ToneDocument inverted = shaded(0);
        live(inverted, instant(brush(INVERT_INK)), 12, 1, across(40, 200, 90));
        check(inverted.compositeTone(120, 90) > 200, "Invert turns the black page white where it goes: " + inverted.compositeTone(120, 90));
    }
    /** Every brush lays the same paint on a shaded page as on white: what is under them is the paper, whatever its shade. */
    static void paper() {
        ModularBrush slow = brush(WASH); slow = own(slow); slow.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.DRYING));
        Object[][] brushes = {
                {"a wash", instant(own(brush(WASH))), 40.0, .6},
                {"a slow wash, settled", slow, 40.0, .6},
                {"a wash behind the ink", instant(brush(WASH)), 40.0, .6},
                {"the stump", brush(STUMP), 30.0, .5},
                {"gouache", instant(brush(GOUACHE)), 24.0, .2},
                {"ink on cold press (the paper's tooth)", preset("Toothy line"), 8.0, 1.0},
                {"charcoal on laid paper", preset("Crisp charcoal"), 14.0, .8},
                {"pooling wash", preset("Pooling wash"), 30.0, .7},
        };
        for (Object[] b : brushes) {
            ToneDocument white = InkWashLayers.blank(W, H), shade = shaded(70);
            for (ToneDocument d : new ToneDocument[]{white, shade}) {
                live(d, instant(brush(INK)), 6, 1, across(30, 210, 60));
                WatercolorSession session = new WatercolorSession();
                live(d, (ModularBrush)b[1], (double)b[2], (double)b[3], session, across(20, 220, 64));
                session.settleNow(d);
            }
            check(samePaint(white, shade), b[0] + " lays the same paint on the shade as on white");
        }
    }

    // ---- files ----

    static void files() throws IOException {
        ToneDocument d = drawn();
        check(d.shadePaper(97), "shades a drawn page (a file can hold one)");
        byte[] b = bytes(d);
        check(magic(b).equals("TSM5"), "A shaded page writes TSM5");
        ToneDocument back = read(b);
        check(back.paperTone() == 97 && sameLayers(back, d) && Arrays.equals(shown(back), shown(d)) && !back.canUndo(), "and reads back its shade, its paint, no Undo");
        d.setMagnets(Magnets.NONE.with(new Magnets.Magnet("moon", 70, 50, -12, 0)));
        ToneDocument both = read(bytes(d));
        check(both.paperTone() == 97 && both.magnets().equals(d.magnets()), "The shade beside magnets");
        ToneDocument.Snapshot s = d.layerSnapshot();
        check(new ToneDocument(s).paperTone() == 97, "A page made from a snapshot keeps its shade");
        check(InkWashLayers.resized(s, W + 20, H - 10, 0, 0).paperTone() == 97, "and a page resized");
        // A later version's section, with more after the tone, opens on its tone; a bad or repeated one is refused.
        check(read(page(section(0x50415052, new byte[]{33, 1, 2}))).paperTone() == 33, "A later PAPR section is read for its tone");
        for (byte[] bad : new byte[][]{page(section(0x50415052, new byte[0])), page(cat(section(0x50415052, new byte[]{1}), section(0x50415052, new byte[]{2})))}) {
            boolean refused = false;
            try { read(bad); } catch (IOException e) { refused = true; }
            check(refused, "An empty or repeated PAPR section is refused");
        }
    }
    static void older() throws IOException {
        // Files written before page shades hold no PAPR section: TSM3, TSM4, a TSM5 with others'.
        ToneDocument plain = read(bytes(drawn()));
        check(plain.paperTone() == 255 && all(read(bytes(InkWashLayers.blank(W, H))), 255), "An old TSM3 page opens on white");
        ToneDocument fluid = InkWashLayers.blank(W, H); fluid.beginFluid(1); fluid.setTone(5, 5, 0); fluid.finish();
        byte[] four = bytes(fluid);
        check(magic(four).equals("TSM4") && read(four).paperTone() == 255, "An old TSM4 page opens on white");
        ToneDocument worded = drawn(); worded.setMagnets(Magnets.NONE.with(new Magnets.Magnet("moon", 70, 50, 0, 0)));
        byte[] five = bytes(worded);
        check(magic(five).equals("TSM5") && read(five).paperTone() == 255, "An old TSM5 page (Erasable's shares, magnets) opens on white");
        check(read(page(section(0x58595a31, new byte[]{9, 9}))).paperTone() == 255, "and one with only a section this reader doesn't know");
    }
    static void white() throws IOException {
        // Pinned before page shades (inkwash-v2-lab 75a97e6): the same page, pixels, picture and PNG; its file gained the KIND section
        // (kinds of mark, 2026-10-20: 0x1b1de9bc before), then its strokes moved to kind planes, the PLNS section (kind layers,
        // 2026-10-27: 0x3b100f6e before), nothing else.
        ToneDocument d = drawn();
        ToneDocument.Snapshot s = d.layerSnapshot();
        check(crc(bytes(d)) == 0xa0563703L && crc(d.exportPixels(true)) == 0x3c91705eL && crc(PagePicture.of(s).encode()) == 0xcfaaa93cL
                && crc(NotebookExport.png(s, 0)) == 0x38f20748L, "A white page writes, shows and exports byte for byte as before page shades");
        byte[] before = bytes(d);
        check(d.shadePaper(80) && d.undo() && Arrays.equals(bytes(d), before), "A shade taken back writes the page byte for byte as before");
        check(d.shadePaper(80) && d.shadePaper(255) && Arrays.equals(bytes(d), before), "and so does a page made white again");
    }
    static void notebooks() throws IOException {
        int tone = GrayPalette.VALUES[5];
        ToneDocument first = shaded(tone);
        DrawingBook book = new DrawingBook(first);
        book.addPage();
        check(book.current().paperTone() == 255, "A new page is white");
        live(book.current(), instant(brush(INK)), 6, 1, across(30, 60, 50));
        book.select(0); book.duplicatePage();
        check(book.current().paperTone() == tone, "A page duplicated keeps its shade");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BookCodec.write(out, book.snapshot());
        DrawingBook back = BookCodec.read(new ByteArrayInputStream(out.toByteArray()));
        DrawingBook.Snapshot s = back.snapshot();
        check(s.page(0).paperTone() == tone && s.page(1).paperTone() == tone && s.page(2).paperTone() == 255, "A notebook keeps each page's shade, the shown one and the others");
        back.select(2);
        check(back.current().paperTone() == 255 && !back.current().unmarked(), "its white page as it was");
        // The page's picture and its PNG copy show the shade.
        PagePicture picture = PagePicture.of(s.page(0).layerSnapshot());
        boolean even = true;
        for (int y = 0; y < picture.height; y++) for (int x = 0; x < picture.width; x++) even &= picture.tone(x, y) == tone;
        check(even, "The notebook's picture of a shaded page is its shade");
        BufferedImage png = javax.imageio.ImageIO.read(new ByteArrayInputStream(NotebookExport.png(s.page(0).layerSnapshot(), 0)));
        check((png.getRGB(W / 2, H / 2) & 255) == DotPattern.exportGray(tone) && (png.getRGB(0, 0) & 255) == DotPattern.exportGray(tone), "and its PNG copy");
        int[] pixels = s.page(0).exportPixels(true);
        check((pixels[0] & 255) == DotPattern.exportGray(tone), "and the drawing's PNG copy");
        java.io.File folder = java.nio.file.Files.createTempDirectory("shade").toFile();
        try {
            NotebookExport.update(folder, "Shade", back.snapshot().packed());
            BufferedImage copy = javax.imageio.ImageIO.read(new java.io.File(folder, "Shade/001.png"));
            BufferedImage plain = javax.imageio.ImageIO.read(new java.io.File(folder, "Shade/003.png"));
            check(copy != null && (copy.getRGB(5, 5) & 255) == DotPattern.exportGray(tone) && (plain.getRGB(5, 5) & 255) == 255, "EXPORT's PNG of each page shows its own shade");
        } finally { delete(folder); }
        // The display's renderer: the same composite, page dots on the shade (the native one is compose_check's).
        ToneDocument page = s.page(0);
        int[] dots = new int[16];
        page.render(dots, 3, 3, 4, 4);
        boolean shade = true;
        for (int k = 0; k < 16; k++) shade &= dots[k] == DotPattern.pixel(tone, 3 + k % 4, 3 + k / 4);
        check(shade, "The page shows its shade in the panel's dots");
    }

    static void delete(java.io.File f) { java.io.File[] in = f.listFiles(); if (in != null) for (java.io.File c : in) delete(c); f.delete(); }
    static byte[] cat(byte[] a, byte[] b) { byte[] c = Arrays.copyOf(a, a.length + b.length); System.arraycopy(b, 0, c, a.length, b.length); return c; }
    static byte[] section(int tag, byte[] body) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream(); DataOutputStream d = new DataOutputStream(o);
        d.writeInt(tag); d.writeInt(body.length); d.write(body); return o.toByteArray();
    }
    /** A blank one-layer TSM5 page holding {@code sections}, written by hand. */
    static byte[] page(byte[] sections) throws IOException {
        int w = 8, h = 4;
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        DataOutputStream header = new DataOutputStream(o);
        header.writeInt(0x54534d35); header.writeInt(w); header.writeInt(h);
        DeflaterOutputStream z = new DeflaterOutputStream(o);
        CRC32 crc = new CRC32();
        DataOutputStream data = new DataOutputStream(new CheckedOutputStream(z, crc));
        data.writeInt(1); data.writeInt(0);
        data.writeUTF("Layer 1"); data.writeBoolean(true); data.writeByte(100);
        byte[] tones = new byte[w * h]; Arrays.fill(tones, (byte)255);
        data.write(tones); data.write(new byte[w * h]);
        data.writeBoolean(false); data.write(sections); data.writeInt(0);
        data.flush(); new DataOutputStream(z).writeLong(crc.getValue());
        z.finish(); z.flush();
        return o.toByteArray();
    }
}
