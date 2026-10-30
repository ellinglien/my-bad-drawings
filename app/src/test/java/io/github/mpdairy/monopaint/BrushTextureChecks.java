package io.github.mpdairy.monopaint;

/** Textures: scans replace the paper's fine and mid noise for one brush; plain is the paper's own. */
public final class BrushTextureChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        scans(); plain(); mid(); ink(); paint();
        System.out.println("PASS: each texture reads its 256-pixel scan once (CC0 or Unsplash) (mean 128, spread about 52), mid noise is the scan at three times the scale and tiles; plain is the paper's own grain; a textured brush's tooth and granulation follow its scan, the same pixels every time");
    }
    static void scans() {
        for (BrushTexture t : BrushTexture.values()) {
            if (t == BrushTexture.PLAIN) { check(t.grain() == PaperGrain.PLAIN, "Plain is the paper's own grain"); continue; }
            PaperGrain g = t.grain();
            check(g != PaperGrain.PLAIN && g.fine.length == 256 * 256 && g.midSide == 768, t.label + " reads its 256-pixel scan");
            check(t.grain() == g, "A scan is read once");
            double sum = 0, squares = 0;
            for (byte b : g.fine) { int v = b & 255; sum += v; squares += v * v; }
            double mean = sum / g.fine.length, sd = Math.sqrt(squares / g.fine.length - mean * mean);
            check(Math.abs(mean - 128) < 3 && sd > 40 && sd < 60, t.label + " is normalised to mean 128, spread 52: " + mean + ", " + sd);
        }
    }
    static void plain() {
        check(PaperGrain.PLAIN.fine == PaperNoise.FINE && PaperGrain.PLAIN.mid == PaperNoise.MID && PaperGrain.PLAIN.midSide == PaperGrain.SIDE,
                "Plain grain is the paper's own FINE and MID");
        for (int y = 0; y < 600; y += 7) for (int x = 0; x < 600; x += 5)
            check(PaperGrain.PLAIN.fine(x, y) == (PaperNoise.FINE[(y & 255) * 256 + (x & 255)] & 255) && PaperGrain.PLAIN.mid(x, y) == (PaperNoise.MID[(y & 255) * 256 + (x & 255)] & 255),
                    "and reads them where the paper did, at " + x + ", " + y);
        check(PaperGrain.PLAIN.spread == 1.22f, "Plain keeps tooth's spread");
    }
    static void mid() {
        PaperGrain g = BrushTexture.CANVAS.grain();
        for (int y = 0; y < 256; y += 3) for (int x = 0; x < 256; x += 5) {
            check(g.mid(3 * x, 3 * y) == g.fine(x, y), "Mid at three times the scale lands on the scan's pixels");
            int a = g.fine(x, y), b = g.fine(x + 1, y), between = g.mid(3 * x + 1, 3 * y);
            check(between >= Math.min(a, b) && between <= Math.max(a, b), "and is smooth between them");
            check(g.mid(x + 768, y + 1536) == g.mid(x, y) && g.fine(x + 256, y + 512) == g.fine(x, y), "Both tile");
        }
        check(g.spread == 1, "A scan is read as it is");
    }
    static void ink() {
        ToneDocument plain = line(ink(BrushTexture.PLAIN)), felt = line(ink(BrushTexture.FELT)), again = line(ink(BrushTexture.FELT));
        check(differs(plain, felt), "A felt brush's tooth rags its edges unlike plain paper");
        check(!differs(felt, again), "The same texture always draws the same pixels");
    }
    static void paint() {
        ToneDocument plain = wash(paint(3_200_000_001L, BrushTexture.PLAIN)), canvas = wash(paint(3_200_000_002L, BrushTexture.CANVAS)),
                again = wash(paint(3_200_000_003L, BrushTexture.CANVAS));
        check(differs(plain, canvas), "A canvas brush granulates unlike plain paper");
        check(!differs(canvas, again), "The same texture always settles the same pixels");
    }
    static WildBrush ink(BrushTexture texture) {
        WildBrush b = WildBrush.roll(WildBrush.Kind.INK, 0); b.nib = WildBrush.Nib.ROUND; b.aspect = 1;
        b.taperIn = b.taperOut = b.dry = b.wobble = b.pool = b.splatter = 0; b.speed = 0; b.pressure = .5; b.tooth = .8;
        b.texture = texture;
        return b;
    }
    static WildBrush paint(long seed, BrushTexture texture) {
        WildBrush b = WildBrush.roll(WildBrush.Kind.PAINT, seed);
        b.wet = true; b.pressure = .5; b.soft = 0; b.gran = .9; b.feather = b.hollow = b.bloom = b.streaks = b.backrun = 0; b.drying = .5;
        b.texture = texture;
        return b;
    }
    /** An old pen's line, converted to six parts (its texture the Paper part's), 16 px. */
    static ToneDocument line(WildBrush b) {
        ToneDocument d = InkWashLayers.blank(400, 200);
        DrawingStroke s = ModularStroke.start(d, LegacyBrushes.of(b), 16, 1, null);
        for (int x = 40, t = 0; x <= 360; x += 2, t += 7) s.sample(x, 100, .25f, Float.NaN, Float.NaN, t);
        s.finish();
        return d;
    }
    /** An old paint's wash, converted to six parts, 120 px. */
    static ToneDocument wash(WildBrush b) {
        ToneDocument d = InkWashLayers.blank(320, 240);
        DrawingStroke s = ModularStroke.start(d, LegacyBrushes.of(b), 120, 1, null);
        for (int x = 40; x <= 280; x += 4) s.sample(x, 120, .78f, 0, 0);
        s.finish();
        return d;
    }
    static boolean differs(ToneDocument a, ToneDocument b) {
        for (int y = 0; y < a.height; y++) for (int x = 0; x < a.width; x++) if (a.compositeTone(x, y) != b.compositeTone(x, y)) return true;
        return false;
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
