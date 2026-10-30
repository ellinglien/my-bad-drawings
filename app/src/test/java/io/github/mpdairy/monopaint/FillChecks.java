package io.github.mpdairy.monopaint;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/**
 * Fill ({@link FillTool}): a slot's brush laid over the area its tap is in, shut in by crisp ink lines drawn with the
 * app's own pen. A leaf and a ring filled with a watercolour (Slow, blooming) and a halftone ink: only the wash layer
 * changes, only inside the area, the ink lines byte for byte as they were; every paper pixel in it is filled (no rim
 * at the lines, and to the leaf's tip), the halftone in pure black dots; one Undo step, and Redo; open paper fills
 * nothing; a wash-layer line across a box holds nothing; masking fluid keeps it off; a shaded page fills the same area;
 * the settling after lift stays in; Erasable follows the brush; the fill's brush has no smudge and floats as Slow (the
 * bar's paint bucket is retired, 2026-10-23: its engine stays). {@code java FillChecks pictures <folder>} writes the four fills as PNGs.
 */
public final class FillChecks {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    static final int W = 900, H = 640;
    /** The pen the lines are drawn with: a plain ink line (the web lab's, as ResistChecks'). */
    static final String PEN = ResistChecks.INK;
    /** A watercolour slot (the Tools page's Slow bloom: Slow drying, blooming) and a halftone ink (Round marker, Print Shading Halftone). */
    static ModularBrush watercolour() { return BlendChecks.brush("slow-bloom.M60i000000000i6_1yh0000fc2a00ee_0_1g7_2i7cl0js_8j90000000000000000nz0.vkxdda"); }
    static ModularBrush halftone() { return ScreenChecks.screened(BlendChecks.brush("round-marker.M30i000000000at_000zzde0000000000000_0_071_1jz_83j00000000008b0000nz0.1ifv2mx"), 1); }

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (args.length > 1 && args[0].equals("pictures")) { pictures(new File(args[1])); return; }
        String fills = fills();
        nothing(); fluid(); shaded(); settling(); erasable(); brushes();
        String big = bigFills();
        String strips = strips();
        System.out.println("PASS: Fill " + fills + "; only the wash layer changes, only inside, the ink lines byte for byte, every paper pixel filled to the lines and the leaf's tip; one Undo step, and Redo; open paper and a wash-layer line hold nothing; masking fluid keeps it off; a shaded page fills the same area; Slow's settling stays in; Erasable follows the brush; no smudge, Float as Slow; " + big + "; " + strips);
    }

    // ---- the drawing ----

    /** A page with a leaf (two arcs meeting at 20° points) on the left and a ring on the right, in crisp ink on the ink layer. */
    static ToneDocument page() {
        ToneDocument d = InkWashLayers.blank(W, H);
        InkWashLayers.select(d, 0);
        ModularBrush pen = BlendChecks.brush(PEN);
        double a = Math.toRadians(10), L = 380, R = L / 2 / Math.sin(a), cx = 240, cy = 320, rc = R * Math.cos(a);
        BlendChecks.live(d, pen, 7, 1, null, arc(cx, cy + rc, R, -Math.PI / 2 - a - .01, -Math.PI / 2 + a + .01));
        BlendChecks.live(d, pen, 7, 1, null, arc(cx, cy - rc, R, Math.PI / 2 - a - .01, Math.PI / 2 + a + .01));
        BlendChecks.live(d, pen, 7, 1, null, arc(650, 320, 170, 0, 2 * Math.PI + .05));
        return d;
    }
    static final int[] LEAF = {240, 320}, RING = {650, 320};
    /** An arc as pen samples about 2 px apart, pressure travel .8. */
    static double[][] arc(double cx, double cy, double r, double a0, double a1) {
        int n = Math.max(12, (int)Math.ceil(Math.abs(a1 - a0) * r / 2));
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) { double t = a0 + (a1 - a0) * i / n; o[i] = new double[]{cx + r * Math.cos(t), cy + r * Math.sin(t), .8, i * 4}; }
        return o;
    }
    /** Fills at {@code tap} with {@code brush} (size, darkness), finished (Drying laid settled, no session); the region, or null. */
    static FillRegion.Room fill(ToneDocument d, ModularBrush brush, double size, double darkness, int[] tap, WatercolorSession session) {
        FillRegion.Room room = FillTool.region(d, tap[0], tap[1]);
        if (room == null) return null;
        ModularStroke s = FillTool.start(d, brush, size, darkness, session, room);
        check(s.fills(), "a fill's stroke says so");
        s.present();
        s.finish();
        return room;
    }
    static int[] shown(ToneDocument d) { int[] t = new int[W * H]; for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) t[y * W + x] = d.compositeTone(x, y); return t; }

    // ---- 1. a leaf and a ring, watercolour and halftone ----

    static String fills() {
        StringBuilder out = new StringBuilder();
        Object[][] cases = {{"watercolour", watercolour(), 60.0, .55}, {"halftone ink", halftone(), 30.0, .5}};
        for (Object[] c : cases) for (int[] tap : new int[][]{LEAF, RING}) {
            String name = c[0] + (tap == LEAF ? " leaf" : " ring");
            ToneDocument d = page();
            int ink = FillTool.walls(d), wash = InkWashLayers.washOf(d, 0);
            byte[][] inkBefore = ErasableChecks.layer(d, ink), washBefore = ErasableChecks.layer(d, wash);
            int[] before = shown(d);
            FillRegion.Room room = fill(d, (ModularBrush)c[1], (Double)c[2], (Double)c[3], tap, null);
            check(room != null, name + ": the area is found");
            check(ErasableChecks.same(ErasableChecks.layer(d, ink), inkBefore), name + ": the ink lines byte for byte as they were");
            int[] after = shown(d);
            int outside = 0, paper = 0, dry = 0, dots = 0, greys = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int i = y * W + x;
                boolean in = room.inside(x, y), line = d.compositeTone(x, y) < 255 && lineAt(inkBefore, x, y);
                if (after[i] != before[i] && !in) outside++;
                byte k = room.kind(x, y);
                if (k == FillRegion.OPEN || k == FillRegion.MARGIN || k == FillRegion.POCKET) {
                    paper++;
                    if (after[i] >= before[i]) dry++;
                    if (after[i] == 0) dots++;
                    if (after[i] != 0 && after[i] != before[i]) greys++;
                }
            }
            check(outside == 0, name + ": nothing changes outside the area: " + outside);
            check(!ErasableChecks.same(ErasableChecks.layer(d, wash), washBefore), name + ": the wash layer takes the fill");
            if (c[0].equals("watercolour")) check(dry == 0, name + ": every paper pixel in the area darkens, to the lines and the tip: " + dry + " of " + paper + " left");
            else check(greys == 0 && dots > paper / 10 && dots < paper, name + ": pure black dots over the area (" + dots + " of " + paper + "), no grey: " + greys);
            // One Undo step, and Redo.
            check(d.undo(), name + ": Undo");
            check(Arrays.equals(shown(d), before) && ErasableChecks.same(ErasableChecks.layer(d, wash), washBefore), name + ": one Undo step takes it all back");
            check(d.redo() && Arrays.equals(shown(d), after), name + ": Redo lays it again");
            out.append(out.length() > 0 ? ", " : "").append(String.format(Locale.ROOT, "%s %d px", name, room.area));
        }
        return "fills a 20° leaf and a ring with a watercolour and a halftone ink (" + out + ")";
    }
    /** Whether the ink layer (as {@code layer} holds it) has a line pixel at {@code x, y}. */
    static boolean lineAt(byte[][] layer, int x, int y) {
        int i = y * W + x, a = layer[1][i] & 255, t = layer[0][i] & 255;
        return 255 - (t * a + 255 * (255 - a) + 127) / 255 > FillRegion.LINE;
    }

    // ---- 1b. a whole page: small in Undo ----

    /**
     * The owner (2026-10-22): "the fill bucket.. it makes things quite slow sometimes, and undo and redo also gets really
     * slow". Laid, a whole-page fill's Undo step shares its blank paper's tiles: a flat ink's step holds next to nothing, a watercolour's or halftone's only its laid side (at most 3.1 bytes a
     * pixel, about half what both sides took); Undo puts the blank page back byte for byte, Redo the fill. Six whole-page fills
     * with Undo and Redo keep the history within its budget and one step.
     */
    static String bigFills() {
        int w = W, h = H;
        StringBuilder out = new StringBuilder();
        Object[][] cases = {{"flat ink", BlendChecks.brush(PEN), 7.0, 1.0, 0.1}, {"watercolour", watercolour(), 60.0, .55, 3.1}, {"halftone", halftone(), 30.0, .5, 3.1}};
        for (Object[] c : cases) {
            ToneDocument d = InkWashLayers.blank(w, h);
            int wash = InkWashLayers.washOf(d, 0);
            byte[][] before = ErasableChecks.layer(d, wash);
            int h0 = d.historyBytes();
            fill(d, (ModularBrush)c[1], (Double)c[2], (Double)c[3], new int[]{w / 2, h / 2}, null);
            double perPx = (d.historyBytes() - h0) / (double)(w * h);
            check(perPx <= (Double)c[4], c[0] + ": a whole-page fill's Undo step holds at most " + c[4] + " bytes a pixel: " + perPx);
            byte[][] after = ErasableChecks.layer(d, wash);
            check(d.undo() && ErasableChecks.same(ErasableChecks.layer(d, wash), before), c[0] + ": Undo puts the blank page back");
            check(d.redo() && ErasableChecks.same(ErasableChecks.layer(d, wash), after), c[0] + ": Redo lays the fill again");
            out.append(out.length() > 0 ? ", " : "").append(String.format(Locale.ROOT, "%s %.2f", c[0], perPx));
        }
        ToneDocument d = InkWashLayers.blank(w, h);
        int most = 0;
        for (int i = 0; i < 6; i++) {
            fill(d, watercolour(), 60, .3 + .1 * i, new int[]{w / 2, h / 2}, null);
            check(d.undo() && d.redo(), "page fill " + i + ": Undo and Redo");
            most = Math.max(most, d.historyBytes());
        }
        check(most <= 32 * 1024 * 1024 + 6 * w * h, "six page fills keep Undo within its budget and a step: " + most);
        return "a whole-page fill's Undo step, bytes a pixel: " + out;
    }

    // ---- 1c. in strips ----

    /** A tall page with a box in ink: strips of rows to lay a fill in. */
    static final int SW = 400, SH = 1100;
    /**
     * A fill is laid, settled and composed a strip of rows at a time ({@link ModularStroke#fill}): every preset's fill of
     * a tall box at Medium, a few at Large (on the stroke's lattice) and over a whole blank page, and light greys over a
     * dark fill, write the page byte for byte as the whole fill at once did ({@link #STRIPS}: the hashes of the pages it
     * wrote, 2026-10-23; the textures' again 2026-10-29, on the Paint plane); Undo puts the page back, Redo the fill.
     */
    static String strips() throws java.io.IOException {
        java.util.List<String> names = new java.util.ArrayList<>();
        java.util.List<Integer> got = new java.util.ArrayList<>();
        ToneDocument boxed = InkWashLayers.blank(SW, SH);
        InkWashLayers.select(boxed, 0);
        BlendChecks.live(boxed, BlendChecks.brush(PEN), 7, 1, null, box(30, 40, SW - 30, SH - 40));
        byte[] page = written(boxed);
        java.util.List<Object[]> cases = new java.util.ArrayList<>();
        for (ToolPresets.Preset p : ToolPresets.all()) cases.add(new Object[]{p.name, InkWashSizes.Preset.MEDIUM, false, .7});
        for (String name : new String[]{"Bleed marker", "Wet chisel", "Fountain pen", "Halftone brush", "Soft pastel", "Flat wash"}) cases.add(new Object[]{name, InkWashSizes.Preset.LARGE, false, .7});
        for (String name : new String[]{"Bleed marker", "Fountain pen", "Halftone brush"}) cases.add(new Object[]{name, InkWashSizes.Preset.MEDIUM, true, .7});
        // Light greys over a dark fill: opaque media cover it.
        for (String name : new String[]{"Soft pastel", "Wax crayon", "Halftone brush", "Crisp charcoal"}) cases.add(new Object[]{name, InkWashSizes.Preset.MEDIUM, false, .15});
        int band = ModularStroke.fillBand;
        // As the app lays them, and in strips cut elsewhere: three tile rows a core.
        for (int strip : new int[]{band, 192}) for (Object[] c : cases) {
            ModularStroke.fillBand = strip;
            String name = (String)c[0];
            boolean whole = (Boolean)c[2], light = (Double)c[3] < .5;
            ModularBrush b = null;
            for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(name)) b = p.brush().parts;
            ToneDocument d = whole ? InkWashLayers.blank(SW, SH) : DocumentCodec.read(new java.io.ByteArrayInputStream(page));
            if (light) fill(d, BlendChecks.brush(PEN), 7, 1, new int[]{SW / 2, SH / 2}, null);
            byte[][] before = BlendChecks.all(d);
            check(fill(d, b, ModularSizes.size(b, (InkWashSizes.Preset)c[1]), (Double)c[3], new int[]{SW / 2, SH / 2}, null) != null, name + ": fills");
            byte[] after = written(d);
            byte[][] laid = BlendChecks.all(d);
            check(d.undo() && BlendChecks.same(BlendChecks.all(d), before), name + ": Undo puts the page back");
            check(d.redo() && BlendChecks.same(BlendChecks.all(d), laid), name + ": Redo lays the fill again");
            names.add(name + " " + c[1] + (whole ? " page" : "") + (light ? " light" : "") + " in strips of " + strip);
            got.add(Arrays.hashCode(after));
        }
        ModularStroke.fillBand = band;
        StringBuilder wrong = new StringBuilder();
        for (int i = 0; i < got.size(); i++) if (STRIPS[i % STRIPS.length] != got.get(i)) wrong.append(wrong.length() > 0 ? ", " : "").append(names.get(i));
        if (System.getProperty("strips") != null) { StringBuilder s = new StringBuilder(); for (int h : got) s.append(String.format(Locale.ROOT, "0x%08x, ", h)); System.out.println(s); }
        check(wrong.length() == 0 && got.size() == 2 * STRIPS.length, "fills in strips write what one piece did: " + wrong);
        return STRIPS.length + " fills in strips (of " + band + " and 192 rows a core) write the page byte for byte as one piece did";
    }
    static byte[] written(ToneDocument d) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        DocumentCodec.write(out, d.layerSnapshot(), java.util.zip.Deflater.NO_COMPRESSION);
        return out.toByteArray();
    }
    /** {@link #strips}' pages as the whole fill at once wrote them. */
    static final int[] STRIPS = {
            0x14cf3f28, 0x14cf3f28, 0x14cf3f28, 0x14cf3f28, 0x14cf3f28, 0x8d0bb76d, 0x8d0bb76d, 0x8d0bb76d, 0x8d0bb76d, 0xed45a9c6,
            0x14cf3f28, 0x14cf3f28, 0x6661b6aa, 0x43d49130, 0x14cf3f28, 0x14cf3f28, 0x14cf3f28, 0x14cf3f28, 0xd3777a10, 0x14cf3f28,
            0xd123005e, 0xbe4a730f, 0x14cf3f28, 0xaa57e6c7, 0x4dd3446b, 0x14cf3f28, 0x14cf3f28, 0x14cf3f28, 0x14cf3f28, 0x14cf3f28,
            0x14cf3f28, 0x14cf3f28, 0x14cf3f28, 0xb2f9f3f0, 0x14cf3f28, 0x936d56ce, 0xa123645d, 0xa796f9b1, 0x6d45853a, 0x60f1492c,
            0x7e432ed1, 0x7e432ed1, 0x7e432ed1, 0x177648f9, 0x15bd2ea0, 0x089db095, 0xc96135c2, 0xbe87f0c4, 0x874e62a6, 0x5ad89a32,
            0xfc977f63, 0x6cca5b1e, 0xf194c8ea, 0x45895bf3, 0xe1763498, 0xb1c2fe0e, 0xe1763498, 0xe1763498, 0xd8ef82ad, 0x106a9538,
            0x106a9538, 0x106a9538, 0x106a9538, 0x106a9538, 0x106a9538, 0x106a9538, 0x106a9538, 0x413466a5, 0xd3892a11, 0x9c2d079c,
            0x9c2d079c, 0xdf0d4cbe, 0xe972ebbe, 0x3f73461f, 0x7e432ed1, 0x4a474667, 0x6661b6aa, 0x106a9538, 0x7e432ed1, 0x5fa0b096,
            0x1134925a, 0x1134925a, 0x1134925a, 0x9fbe96d5, 0x6d45853a, 0x6d45853a, 0xb977e4ab, 0xb977e4ab, 0xb977e4ab, 0x6d45853a,
            0xb977e4ab, 0xb977e4ab, 0xb977e4ab, 0xb977e4ab, 0xb977e4ab, 0x328ebc9d, 0x3dbcbbb5, 0x4007c0ae, 0xd9dcc324, 0xd6675cc0,
            0xa3ce4f14, 0x56a8eb3e, 0xaf77e29d, 0x7f92afbb, 0xd9c418c2, 0xda26c372, 0x0358e9dc, 0x0ad37687, 0x2e0e8634, 0xec5621de,
            0xe0b1b015, 0x5f929c7a, 0xe8e77816, 0xe4424e0a, 0x76a4fd42, 0x632d0a40, 0x42330764, 0xa5961932, 0xcdb851c4, 0x3b9ff36e,
            0xc98bd61d, 0x3fd15847, 0x08d20d11, 0xbaf54ac8, 0x2e9b2cb0, 0x0474fe0f, 0x491b0d3d, 0xc2dc72a9, 0x6661b6aa, 0x3fd15847,
            0x4007c0ae, 0x7e432ed1, 0xf0c4e8c6, 0x638bdf12, 0xc863833c, 0xa958ccc7, 0xdbc6265b, 0xf33ed11a, 0x94864ddc
    };

    // ---- 2. what holds nothing ----

    static void nothing() {
        ToneDocument d = page();
        // The owner (2026-10-22): "i just used the paint bucket on a blank page and it said no closed area.. it should fill the page ideally".
        FillRegion.Room open = FillTool.region(d, 20, 20);
        check(open != null && open.x0 == 0 && open.y0 == 0 && open.inside(20, 20) && !open.inside(650, 320), "open paper fills up to the page's edges, not inside the shapes");
        FillRegion.Room whole = FillTool.region(InkWashLayers.blank(W, H), W / 2, H / 2);
        check(whole != null && whole.x0 == 0 && whole.y0 == 0 && whole.x1 == W - 1 && whole.y1 == H - 1 && whole.inside(0, 0) && whole.inside(W - 1, H - 1), "a blank page fills whole");
        // A box in ink with a line across it on the wash layer: the wash line is no wall, the box fills whole.
        ToneDocument b = InkWashLayers.blank(W, H);
        InkWashLayers.select(b, 0);
        BlendChecks.live(b, BlendChecks.brush(PEN), 7, 1, null, box(200, 150, 700, 500));
        ModularBrush washPen = BlendChecks.life(watercolour(), PartChoice.NONE);
        BlendChecks.live(b, washPen, 20, 1, null, BlendChecks.across(190, 710, 325));
        FillRegion.Room room = FillTool.region(b, 450, 240);
        check(room != null && room.inside(450, 420), "a line on the wash layer, however dark, holds no fill");
    }
    static double[][] box(double x0, double y0, double x1, double y1) {
        double[][] c = {{x0, y0}, {x1, y0}, {x1, y1}, {x0, y1}, {x0, y0 - 4}};
        java.util.ArrayList<double[]> o = new java.util.ArrayList<>();
        for (int k = 1; k < c.length; k++) {
            int n = (int)Math.ceil(Math.hypot(c[k][0] - c[k - 1][0], c[k][1] - c[k - 1][1]) / 2);
            for (int i = k == 1 ? 0 : 1; i <= n; i++) o.add(new double[]{c[k - 1][0] + (c[k][0] - c[k - 1][0]) * i / n, c[k - 1][1] + (c[k][1] - c[k - 1][1]) * i / n, .8, o.size() * 4});
        }
        return o.toArray(new double[0][]);
    }

    // ---- 3. masking fluid, a shaded page, the settling after lift ----

    static void fluid() {
        ToneDocument d = page();
        BlendChecks.live(d, BlendChecks.brush(ResistChecks.FLUID), 30, 1, null, BlendChecks.across(560, 740, 320));
        int[] before = shown(d);
        boolean[] masked = new boolean[W * H];
        int n = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (d.maskShield(x, y) == 255) { masked[y * W + x] = true; n++; }
        check(n > 1000, "the fluid is laid: " + n);
        FillRegion.Room room = fill(d, watercolour(), 60, .55, RING, null);
        int[] after = shown(d);
        int took = 0, around = 0;
        for (int i = 0; i < W * H; i++) { if (masked[i] && after[i] != before[i]) took++; if (!masked[i] && room.inside(i % W, i / W) && after[i] < before[i]) around++; }
        check(took == 0 && around > 50000, "masking fluid keeps the fill off (" + took + " masked pixels changed) and it fills round it (" + around + ")");
    }
    static void shaded() {
        ToneDocument plain = page(), d = page();
        check(d.shadePaper(200), "the page is shaded");
        FillRegion.Room a = FillTool.region(plain, RING[0], RING[1]), b = FillTool.region(d, RING[0], RING[1]);
        check(FillRegionChecks.same(a, b), "a shaded page's area is the same");
        int[] before = shown(d);
        fill(d, watercolour(), 60, .55, RING, null);
        int[] after = shown(d);
        int outside = 0, darker = 0;
        for (int i = 0; i < W * H; i++) { if (after[i] != before[i] && !b.inside(i % W, i / W)) outside++; if (b.kind(i % W, i / W) == FillRegion.OPEN && after[i] < before[i]) darker++; }
        check(outside == 0 && darker > 50000, "on a shaded page the fill darkens the shade inside (" + darker + ") and nothing outside (" + outside + ")");
    }
    /** A Slow fill kept settling in a session, then laid: still nothing outside. */
    static void settling() {
        ToneDocument d = page();
        WatercolorSession session = new WatercolorSession();
        int[] before = shown(d);
        FillRegion.Room room = fill(d, watercolour(), 60, .55, LEAF, session);
        check(session.wet(), "a Slow fill keeps settling after lift");
        session.settleNow(d);
        int[] after = shown(d);
        int outside = 0;
        for (int i = 0; i < W * H; i++) if (after[i] != before[i] && !room.inside(i % W, i / W)) outside++;
        check(outside == 0, "its settling stays inside the area: " + outside);
    }

    // ---- 4. Erasable, the fill's brush, the bar ----

    static void erasable() {
        for (double e : new double[]{1, 0}) {
            ToneDocument d = page();
            int wash = InkWashLayers.washOf(d, 0);
            fill(d, BlendChecks.erasable(halftone(), e), 30, 1, RING, null);
            // Its shares: the base's and each kind plane's (ErasableChecks.layer).
            byte[][] layer = ErasableChecks.layer(d, wash);
            int kept = 0;
            for (int k = 2; k < layer.length; k++) for (byte s : layer[k]) if ((s & 255) < 255) kept++;
            check(e == 1 ? kept == 0 : kept > 1000, "Erasable follows the brush: at " + e + ", " + kept + " pixels keep a share");
        }
    }
    static void brushes() {
        ModularBrush slot = watercolour();
        slot.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.SMUDGE));
        ModularBrush b = FillTool.brush(slot);
        check(b.choice(BrushPart.CARRY) == PartChoice.OFF && slot.choice(BrushPart.CARRY) == PartChoice.SMUDGE, "the fill's brush has no smudge, the slot's untouched");
        check(FillTool.brush(BlendChecks.life(slot, PartChoice.FLOAT)).choice(BrushPart.LIFE) == PartChoice.DRYING, "Float fills as Slow");
        check(FillTool.brush(slot).choice(BrushPart.LIFE) == slot.choice(BrushPart.LIFE), "other drying as the slot's");
        // Float and Quick fill too.
        for (PartChoice life : new PartChoice[]{PartChoice.FLOAT, PartChoice.FAST_WET, PartChoice.NONE}) {
            ToneDocument d = page();
            int[] before = shown(d);
            FillRegion.Room room = fill(d, BlendChecks.life(watercolour(), life), 60, .55, RING, null);
            int[] after = shown(d);
            int outside = 0, darker = 0;
            for (int i = 0; i < W * H; i++) { if (after[i] != before[i] && !room.inside(i % W, i / W)) outside++; if (after[i] < before[i]) darker++; }
            check(outside == 0 && darker > 50000, life + " fills inside only (" + darker + " darker, " + outside + " outside)");
        }
    }
    // ---- pictures ----

    /** The four fills, each its own PNG in {@code dir}: the leaf and the ring, watercolour and halftone. */
    static void pictures(File dir) throws Exception {
        dir.mkdirs();
        Object[][] cases = {{"watercolour", watercolour(), 60.0, .55}, {"halftone", halftone(), 30.0, .5}};
        for (Object[] c : cases) for (int[] tap : new int[][]{LEAF, RING}) {
            ToneDocument d = page();
            fill(d, (ModularBrush)c[1], (Double)c[2], (Double)c[3], tap, null);
            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { int g = d.compositeTone(x, y); img.setRGB(x, y, g << 16 | g << 8 | g); }
            File f = new File(dir, "fill-" + c[0] + "-" + (tap == LEAF ? "leaf" : "ring") + ".png");
            javax.imageio.ImageIO.write(img, "png", f);
            System.out.println(f.getAbsolutePath());
        }
    }

    private FillChecks() {}
}
