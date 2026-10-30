package io.github.mpdairy.monopaint;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/**
 * Filled Shape ({@link PartChoice#SHAPE}, {@link FilledShape}): Smudge choice 6, its code round trip and every older
 * code as it was; the area an outline encloses (non-zero winding): a square, a concave C, a figure eight's two lobes, a
 * star's middle and a loop gone round twice; drawn, the outline closes and its area fills, nothing outside, one Undo
 * step with Redo; a dot and a short line stay the stroke they were; Copy Strokes fills each image, Real Mirror the kept
 * side; outline and inside one tone at every grey ({@link #tone}); a page-sized shape fits the Manta's heap. {@code pictures <dir>} draws ink, wash and crayon shapes as PNGs.
 */
public final class FilledShapeChecks {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    static final int W = 900, H = 640;

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (args.length > 0 && args[0].equals("page")) { page(); return; }
        if (args.length > 1 && args[0].equals("pictures")) { pictures(new File(args[1])); return; }
        codes(); areas(); drawn(); String tone = tone(); tiny(); mirrors();
        String heap = heap();
        System.out.println("PASS: Filled Shape is Smudge choice 6 and its code reads back, older codes as they were; a square, a concave C, a figure eight's two lobes, a star's middle and a loop gone round twice fill; drawn, the outline closes and fills inside only, one Undo step and Redo; a dot and a short line stay strokes; Copy Strokes fills each image, Real Mirror the kept side; " + tone + "; " + heap);
    }

    static ModularBrush preset(String name) {
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(name)) return p.brush().parts;
        throw new AssertionError("no preset " + name);
    }
    static ModularBrush shaped(ModularBrush b) { ModularBrush c = b.copy(); c.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.SHAPE)); return c; }
    static ModularBrush ink() { return shaped(preset("Steady line")); }

    // ---- codes ----

    static void codes() {
        check(PartChoice.SHAPE.index() == 6 && PartChoice.of(BrushPart.CARRY, 6) == PartChoice.SHAPE && PartChoice.of(BrushPart.CARRY, 7) == null
                && PartChoice.SHAPE.key.equals("shape") && PartChoice.SHAPE.label.equals("Filled Shape") && PartChoice.SHAPE.dials.isEmpty() && PartChoice.SHAPE.macro == null,
                "Filled Shape is Smudge choice 6, no dials");
        ModularBrush b = ink();
        String code = ModularCode.encode(b);
        ModularBrush back = ModularCode.decode(code);
        check(code.split("_")[2].equals("6") && back != null && back.choice(BrushPart.CARRY) == PartChoice.SHAPE && ModularCode.encode(back).equals(code), "its code _6_ reads back: " + code);
        for (ToolPresets.Preset p : ToolPresets.all()) check((ModularCode.encode(ModularCode.decode(p.code)).equals(p.code) || HandHatchChecks.handHatch(p.code)), p.name + "'s code reads and writes as it was");
        for (ToolPresets.Preset p : ToolPresets.retired()) check((ModularCode.encode(ModularCode.decode(p.code)).equals(p.code) || HandHatchChecks.handHatch(p.code)), p.name + " (retired) reads and writes as it was");
        ModularBrush f = preset("Filled shape");
        check(FilledShape.fills(f) && !FilledShape.fills(preset("Fill in")) && ResolvedBrush.of(f, 30, 1).carry == null, "the Filled shape preset fills shapes and smudges nothing");
        check(ModularFeatures.seedOnly(PartChoice.SHAPE) && !ModularFeatures.varies(PartChoice.SHAPE), "never a variation's swap");
        check(ModularWords.choiceWords(f, BrushPart.CARRY).equals("Shape") && ModularWords.plainWords(f).contains("Fills the shape it outlines.") && ModularWords.caption(f).contains("filled shape"),
                "its words: " + ModularWords.plainWords(f) + " / " + ModularWords.caption(f));
    }

    // ---- the area ----

    static final double[][] ONE = {Mirrors.Group.IDENTITY};
    static FillRegion.Room room(double[][] pts) {
        float[] xs = new float[pts.length], ys = new float[pts.length];
        for (int i = 0; i < pts.length; i++) { xs[i] = (float) pts[i][0]; ys[i] = (float) pts[i][1]; }
        return FilledShape.room(xs, ys, pts.length, ONE, W, H);
    }
    /** Points round a curve {@code f}(t), t from 0 to {@code turns}, about 2 px apart, pressure travel .8, 4 ms apart. */
    static double[][] curve(java.util.function.DoubleFunction<double[]> f, double turns, int n) {
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) { double[] p = f.apply(turns * i / n); o[i] = new double[]{p[0], p[1], .8, i * 4}; }
        return o;
    }
    static double[][] eight(double cx, double cy, double r) { return curve(t -> new double[]{cx + 2 * r * Math.sin(2 * Math.PI * t), cy + r * Math.sin(4 * Math.PI * t)}, .97, 600); }
    static double[][] star(double cx, double cy, double r) {
        double[][] c = new double[5][];
        for (int k = 0; k < 5; k++) { double a = -Math.PI / 2 + k * 4 * Math.PI / 5; c[k] = new double[]{cx + r * Math.cos(a), cy + r * Math.sin(a)}; }
        return FillMirrorChecks.path(c);
    }
    static double[][] ring(double cx, double cy, double r, double turns) { return curve(t -> new double[]{cx + r * Math.cos(2 * Math.PI * t), cy + r * Math.sin(2 * Math.PI * t)}, turns, (int) (turns * 2 * Math.PI * r / 2)); }
    static double[][] blob(double cx, double cy, double r) { return curve(t -> { double a = 2 * Math.PI * t, w = 1 + .15 * Math.sin(3 * a + .7); return new double[]{cx + 1.3 * r * w * Math.cos(a), cy + r * w * Math.sin(a)}; }, .94, 500); }

    static void areas() {
        FillRegion.Room sq = room(FillMirrorChecks.path(new double[]{100, 100}, new double[]{300, 100}, new double[]{300, 300}, new double[]{100, 300}));
        check(sq != null && sq.area == 200 * 200 && sq.inside(150, 150) && !sq.inside(99, 150) && !sq.inside(301, 150), "a square closes and fills, its side the closing line: " + (sq == null ? 0 : sq.area));
        // A C open to the right: its notch stays out.
        FillRegion.Room c = room(FillMirrorChecks.path(new double[]{400, 100}, new double[]{600, 100}, new double[]{600, 160}, new double[]{460, 160},
                new double[]{460, 240}, new double[]{600, 240}, new double[]{600, 300}, new double[]{400, 300}));
        check(c != null && c.area == 200 * 200 - 140 * 80 && c.inside(430, 200) && !c.inside(530, 200), "a concave C fills, its notch left out: " + (c == null ? 0 : c.area));
        FillRegion.Room e = room(eight(250, 480, 70));
        check(e != null && e.inside(180, 480) && e.inside(320, 480) && !e.inside(250, 420) && !e.inside(250, 540), "a figure eight fills both lobes");
        FillRegion.Room s = room(star(650, 470, 140));
        check(s != null && s.inside(650, 470) && s.inside(650, 360), "a star fills its middle and its points (non-zero)");
        FillRegion.Room twice = room(ring(450, 320, 100, 2));
        check(twice != null && twice.inside(450, 320) && Math.abs(twice.area - Math.PI * 100 * 100) < 800, "a loop gone round twice fills whole: " + (twice == null ? 0 : twice.area));
        check(FilledShape.tiny(100, 4) && FilledShape.tiny(800, 30) && !FilledShape.tiny(1000, 30) && !FilledShape.tiny(200, 4), "tiny: under 144 px, or under the brush's square");
    }

    // ---- drawn ----

    /** {@code b}'s stroke along {@code path} as the drawing screen draws it ({@link InkWash#stroke}): live or quick, lifted, worked after lift, finished. */
    static boolean draw(ToneDocument d, ModularBrush b, double size, double darkness, double[][] path) {
        DrawingStroke s = FilledShape.of(d, b, QuickStroke.start(d, b, size, darkness, null), size, darkness, null);
        for (int i = 0; i < path.length; i++) {
            double[] p = path[i];
            s.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), Float.NaN, Float.NaN, (long) p[3]);
            if (i % 3 == 2) s.present();
        }
        s.present();
        if (s instanceof LiftingStroke && ((LiftingStroke) s).developsAfterLift() && ((LiftingStroke) s).lift()) while (((LiftingStroke) s).develop(1_000_000)) { }
        return s.finish();
    }
    static int[] shown(ToneDocument d) { int[] t = new int[d.width * d.height]; for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) t[y * d.width + x] = d.compositeTone(x, y); return t; }
    static ToneDocument blank() { ToneDocument d = InkWashLayers.blank(W, H); InkWashLayers.select(d, 0); return d; }

    static void drawn() {
        for (String name : new String[]{"ink", "Filled shape", "Wax crayon", "Soft wash"}) {
            ModularBrush b = name.equals("ink") ? ink() : shaped(preset(name));
            ToneDocument d = blank();
            byte[][] before = BlendChecks.all(d);
            int[] shownBefore = shown(d);
            double size = Math.min(16, ModularSizes.size(b, InkWashSizes.Preset.MEDIUM));
            check(draw(d, b, size, .8, FillMirrorChecks.path(new double[]{100, 100}, new double[]{400, 100}, new double[]{400, 400}, new double[]{100, 400})), name + ": draws");
            int[] after = shown(d);
            int inside = 0, dry = 0, outside = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int i = y * W + x;
                boolean changed = after[i] != shownBefore[i];
                if (x > 120 && x < 380 && y > 120 && y < 380) { inside++; if (!changed) dry++; }
                if ((x < 100 - size || x > 400 + size || y < 100 - size || y > 400 + size) && changed) outside++;
            }
            check(dry < inside / 50, name + ": the inside fills: " + dry + " of " + inside + " left");
            check(outside == 0, name + ": nothing outside the outline: " + outside);
            check(after[250 * W + 100] < shownBefore[250 * W + 100], name + ": the outline closed, its last side drawn");
            byte[][] laid = BlendChecks.all(d);
            check(d.undo() && BlendChecks.same(BlendChecks.all(d), before), name + ": one Undo takes outline and fill back");
            check(d.redo() && BlendChecks.same(BlendChecks.all(d), laid), name + ": Redo lays both again");
        }
    }

    /**
     * The owner (2026-10-23): "it can only be black? otherwise the color is a bit diff for the stroke vs the fill". At
     * each grey swatch but black, and 50%, the outline's middle (where it lies over the fill) and the inside are one
     * tone, in ink, the Filled shape preset, an ink wash, crayon and watercolour: their means within {@link #TONE_GAP}.
     */
    static String tone() {
        double worst = 0;
        String at = "";
        for (String name : new String[]{"ink", "Filled shape", "Grey wash", "Wax crayon", "Soft wash"}) {
            ModularBrush b = name.equals("ink") ? ink() : name.equals("Filled shape") ? preset(name) : shaped(preset(name));
            double size = Math.max(12, ModularSizes.size(b, InkWashSizes.Preset.MEDIUM));
            for (double grey : new double[]{ToolSwatches.GREYS[1], ToolSwatches.GREYS[2], ToolSwatches.GREYS[3], .5}) {
                ToneDocument d = blank();
                int x0 = 150, x1 = 550, y0 = 100, y1 = 540, c = Math.max(2, (int) (size / 8));
                check(draw(d, b, size, grey, FillMirrorChecks.path(new double[]{x0, y0}, new double[]{x1, y0}, new double[]{x1, y1}, new double[]{x0, y1})), name + ": draws");
                double band = mean(d, x0 + (int) size, x1 - (int) size, y0 - c, y0 + c), inside = mean(d, x0 + (int) size, x1 - (int) size, 250, 400), gap = Math.abs(band - inside);
                check(gap <= TONE_GAP, String.format(Locale.ROOT, "%s at grey %.2f: the outline (%.1f) and the inside (%.1f) are one tone", name, grey, band, inside));
                if (gap > worst) { worst = gap; at = name; }
            }
        }
        return String.format(Locale.ROOT, "outline and inside one tone at every grey (%.1f apart at most, %s)", worst, at);
    }
    /** How far apart the outline's mean tone and the inside's may be, of 255: before the fix they were 25 to 45 apart. */
    static final double TONE_GAP = 10;
    static double mean(ToneDocument d, int x0, int x1, int y0, int y1) { double s = 0; for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) s += d.compositeTone(x, y); return s / ((x1 - x0 + 1) * (y1 - y0 + 1)); }

    static void tiny() {
        ModularBrush shape = ink(), plain = ink();
        plain.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.OFF));
        double[][] dot = {{200, 200, .8, 0}, {201, 200, .8, 8}, {201, 201, .8, 16}, {200, 201, .8, 24}};
        double[][] line = FillMirrorChecks.path(new double[]{100, 300}, new double[]{180, 302});
        for (double[][] path : new double[][][]{dot, line}) {
            ToneDocument a = blank(), b = blank();
            byte[][] before = BlendChecks.all(a);
            draw(a, shape, 6, 1, path); draw(b, plain, 6, 1, path);
            check(BlendChecks.same(BlendChecks.all(a), BlendChecks.all(b)), (path == dot ? "a dot" : "a short line") + " stays the stroke it was");
            check(a.undo() && BlendChecks.same(BlendChecks.all(a), before), "and one Undo takes it back");
        }
    }

    // ---- mirrors ----

    static void mirrors() {
        Mirrors.Line middle = new Mirrors.Line(W / 2.0, H / 2.0, Math.PI / 2);
        double[][] box = FillMirrorChecks.path(new double[]{100, 200}, new double[]{300, 200}, new double[]{300, 400}, new double[]{100, 400});
        {
            ToneDocument d = blank();
            d.setMirrors(Mirrors.NONE.add(middle));
            int[] before = shown(d);
            check(draw(d, ink(), 6, .8, box), "Copy Strokes draws");
            int[] after = shown(d);
            check(after[300 * W + 200] < before[300 * W + 200] && after[300 * W + (W - 200)] < before[300 * W + (W - 200)], "Copy Strokes: the shape and its image fill");
            check(d.undo() && Arrays.equals(shown(d), before), "one Undo takes the shape, its copy and their fills");
        }
        {
            // Real Mirror keeping the left: a box across the mirror fills as the page shows it, laid on the kept side only;
            // one drawn wholly past the mirror fills its reflection there.
            ToneDocument d = blank();
            d.setMirrors(Mirrors.NONE.add(middle).styled(Mirrors.Style.REAL).keeping(100, 320));
            check(d.mirrors().reflects(), "the setup: Real Mirror");
            byte[][] before = BlendChecks.all(d);
            int[] tones = shown(d);
            check(draw(d, ink(), 6, .8, FillMirrorChecks.path(new double[]{350, 200}, new double[]{500, 200}, new double[]{500, 400}, new double[]{350, 400})), "Real Mirror draws");
            int[] after = shown(d);
            check(after[300 * W + 380] < tones[300 * W + 380] && after[300 * W + 430] < tones[300 * W + 430], "Real Mirror: the kept side fills, the part past the mirror folded onto it");
            check(after[300 * W + 600] == tones[300 * W + 600], "nothing laid past the kept side");
            check(d.undo() && BlendChecks.same(BlendChecks.all(d), before), "one Undo takes it back");
            check(draw(d, ink(), 6, .8, FillMirrorChecks.path(new double[]{560, 200}, new double[]{700, 200}, new double[]{700, 400}, new double[]{560, 400})), "Real Mirror draws past the mirror");
            after = shown(d);
            check(after[300 * W + (W - 630)] < tones[300 * W + (W - 630)] && after[300 * W + 630] == tones[300 * W + 630], "a shape drawn past the mirror fills on the kept side, folded");
        }
    }

    // ---- a page ----

    /** A Manta page's shape round the whole page with the heaviest fill (Bleed marker's settle), in a heap the Manta's size. */
    static String heap() throws Exception {
        String bin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        Process p = new ProcessBuilder(bin, "-Xmx192m", "-cp", System.getProperty("java.class.path"), FilledShapeChecks.class.getName(), "page").redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        check(p.waitFor() == 0 && out.startsWith("page"), "a page-sized shape fits a 192 MB heap: " + out);
        return out;
    }
    static void page() {
        int w = 1920, h = 2560;
        ToneDocument d = InkWashLayers.blank(w, h);
        InkWashLayers.select(d, 0);
        ModularBrush b = shaped(preset("Bleed marker"));
        long t0 = System.nanoTime();
        draw(d, b, ModularSizes.size(b, InkWashSizes.Preset.MEDIUM), .7, FillMirrorChecks.path(new double[]{40, 40}, new double[]{w - 40, 40}, new double[]{w - 40, h - 40}, new double[]{40, h - 40}));
        check(d.compositeTone(w / 2, h / 2) < 250, "the page-sized shape fills");
        Runtime r = Runtime.getRuntime();
        System.out.println(String.format(Locale.ROOT, "page-sized Bleed marker shape (%d × %d) laid in %.1f s in a %d MB heap", w, h, (System.nanoTime() - t0) / 1e9, r.maxMemory() >> 20));
    }

    // ---- pictures ----

    static void pictures(File dir) throws Exception {
        dir.mkdirs();
        Object[][] cases = {{"ink", ink(), 8.0, .9}, {"wash", preset("Filled shape"), 0.0, .7}, {"crayon", shaped(preset("Wax crayon")), 0.0, .8}, {"watercolour", shaped(preset("Soft wash")), 0.0, .7}};
        for (Object[] c : cases) {
            ModularBrush b = (ModularBrush) c[1];
            double size = (Double) c[2] > 0 ? (Double) c[2] : ModularSizes.size(b, InkWashSizes.Preset.MEDIUM);
            ToneDocument d = blank();
            draw(d, b, size, (Double) c[3], blob(170, 170, 100));
            draw(d, b, size, (Double) c[3], star(470, 180, 140));
            draw(d, b, size, (Double) c[3], eight(230, 480, 90));
            draw(d, b, size, (Double) c[3], ring(700, 470, 110, 2));
            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { int g = d.compositeTone(x, y); img.setRGB(x, y, g << 16 | g << 8 | g); }
            File f = new File(dir, "filled-shape-" + c[0] + ".png");
            javax.imageio.ImageIO.write(img, "png", f);
            System.out.println(f.getAbsolutePath());
        }
    }

    private FilledShapeChecks() {}
}
