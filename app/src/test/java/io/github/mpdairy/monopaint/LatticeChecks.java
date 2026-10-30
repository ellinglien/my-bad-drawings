package io.github.mpdairy.monopaint;

/**
 * A stroke reaching wider than ModularStroke.latticePx is worked out on every other pixel and stretched back: it must
 * draw nearly as every pixel would, change nothing at lift under Fast-wet, undo in one step, and compose only what
 * changed as composing everything would.
 */
public final class LatticeChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        double before = ModularStroke.latticePx;
        try { threshold(); ModularStroke.latticePx = before; alike(); lift(); shortcut(); } finally { ModularStroke.latticePx = before; }
        System.out.println("PASS: a paint's stroke (wash, hake, opaque paint) starts the lattice past 50 px, a pen's past 100 as before (pens at 50 to 100 px and every stroke under 50 draw byte for byte as on every pixel), a paint at 72 px drawing within a few tones of every pixel; a big stroke on the lattice draws within a few tones of every pixel (a wash, a charcoal stick, a Drying ink and wash), changes nothing at lift under Fast-wet, undoes in one step and composes only what changed as composing everything would");
    }
    /** Runs {@code r} with every stroke it starts worked out on every pixel (no lattice), as the web lab draws: for goldens against it. */
    static void everyPixel(Runnable r) {
        double before = ModularStroke.latticePx;
        ModularStroke.latticePx = Double.POSITIVE_INFINITY;
        try { r.run(); } finally { ModularStroke.latticePx = before; }
    }
    static ModularBrush brush(String fixture, PartChoice life) { return ModularStrokeChecks.life(ModularCodeChecks.fixture(fixture), life); }
    static ToneDocument page(ModularBrush b) {
        PartChoice m = b.choice(BrushPart.MEDIUM);
        ToneDocument d = InkWashLayers.blank(1000, 700);
        d.selectLayer(m == PartChoice.WASH || m == PartChoice.COVER ? InkWashLayers.WASH : InkWashLayers.INK);
        return d;
    }
    /** A wave across the page, eight samples an event as the screen sends them. */
    static ModularStroke wave(ToneDocument d, ModularBrush b, double size) { return wave(d, b, size, 1); }
    static ModularStroke wave(ToneDocument d, ModularBrush b, double size, double darkness) {
        ModularStroke s = new ModularStroke(d, b, size, darkness, null);
        for (int i = 0; i <= 400; i++) {
            double t = i / 400.0;
            s.sample((float)(150 + 700 * t), (float)(350 + 120 * Math.sin(2 * Math.PI * t)), ToolSettings.pressure((float)(.2 + .7 * Math.sin(Math.PI * t))), 20, 10, i * 3L);
            if (i % 8 == 7) s.present();
        }
        s.present();
        return s;
    }
    static int[] draw(ModularBrush b, double size, double darkness, double latticePx) {
        ModularStroke.latticePx = latticePx;
        ToneDocument d = page(b);
        wave(d, b, size, darkness).finish();
        int[] tones = new int[d.width * d.height];
        for (int i = 0; i < tones.length; i++) tones[i] = d.compositeTone(i % d.width, i / d.width);
        return tones;
    }
    /**
     * Where the lattice starts (the audit's finding 16, the owner's call): a paint's stroke whose marks reach past 50 px
     * ({@link ModularStroke#latticePx}), a pen's past 100 ({@link ModularStroke#INK_LATTICE_PX}). Under them every pixel
     * is worked out, byte for byte as with no lattice at all; a paint at 72 px on it draws within a few tones of every pixel.
     */
    static void threshold() {
        check(ModularStroke.latticePx == 50 && ModularStroke.INK_LATTICE_PX == 100, "the lattice starts at 50 px for paints, 100 for pens");
        // Preset, size (negative: the least whose marks reach past that many px), whether on the lattice at the default threshold.
        Object[][] cases = {{"Soft wash", 72.0, true}, {"Wet hake", 72.0, true}, {"Grey wash", 90.0, true}, {"Soft wash", 40.0, false},
                {"Steady line", -70.0, false}, {"Messy stick", -70.0, false}, {"Messy stick", -110.0, true}};
        for (Object[] c : cases) {
            ModularBrush b = GravityChecks.preset((String)c[0]);
            double size = (Double)c[1];
            if (size < 0) { double past = -size; size = 1; while (ModularStroke.reach(ResolvedBrush.of(b, size, 1)) <= past) size++; }
            double reach = ModularStroke.reach(ResolvedBrush.of(b, size, 1));
            boolean paint = !ModularSizes.inks(b), on = (Boolean)c[2];
            ModularStroke.latticePx = 50;
            ModularStroke s = new ModularStroke(page(b), b, size, 1, null);
            check(s.onLattice() == on && on == reach > (paint ? 50 : 100), c[0] + " at " + size + " px (reach " + reach + ") is " + (on ? "" : "not ") + "on the lattice");
            s.finish();
            if (on && !paint) continue;
            int[] now = draw(b, size, 1, 50), every = draw(b, size, 1, Double.POSITIVE_INFINITY);
            if (!on) { check(java.util.Arrays.equals(now, every), c[0] + " at " + size + " px draws byte for byte as on every pixel"); continue; }
            long marked = 0, sum = 0, far = 0;
            for (int i = 0; i < every.length; i++) {
                if (every[i] > 250 && now[i] > 250) continue;
                int diff = Math.abs(every[i] - now[i]);
                marked++; sum += diff; if (diff > 24) far++;
            }
            check(marked > 5_000 && sum <= 4.0 * marked && far <= marked / 25, c[0] + " at " + size + " px on the lattice draws within a few tones of every pixel: mean " + (double)sum / marked + ", " + far + " of " + marked + " past 24");
        }
    }
    static void alike() {
        // Fixture, life, size, darkness (0: the old black, a wash's 34 % glaze before washes reached black) and the mean
        // difference allowed, tones: a black wash spans nearly three times the greys, its lattice a little more off.
        Object[][] cases = {{"wet-wash", PartChoice.FAST_WET, 300.0, 0.0, 4.0}, {"wet-wash", PartChoice.FAST_WET, 300.0, 1.0, 4.5},
                {"soft-coal", PartChoice.NONE, 260.0, 1.0, 4.0}, {"brave-onion", PartChoice.DRYING, 300.0, 1.0, 4.0}};
        for (Object[] c : cases) {
            ModularBrush b = brush((String)c[0], (PartChoice)c[1]);
            double size = (Double)c[2], darkness = (Double)c[3] > 0 ? (Double)c[3] : ModularSizes.sinceBlack(b, 1), most = (Double)c[4];
            check(ModularStroke.reach(ResolvedBrush.of(b, size, darkness)) > 200, c[0] + " at " + size + " px reaches past 200 px");
            int[] every = draw(b, size, darkness, Double.POSITIVE_INFINITY), lattice = draw(b, size, darkness, 200);
            long marked = 0, sum = 0, far = 0;
            for (int i = 0; i < every.length; i++) {
                if (every[i] > 250 && lattice[i] > 250) continue;
                int diff = Math.abs(every[i] - lattice[i]);
                marked++; sum += diff; if (diff > 24) far++;
            }
            check(marked > 20_000 && sum <= most * marked && far <= marked / 100,
                    c[0] + " at " + darkness + " on the lattice draws within a few tones of every pixel: mean " + (double)sum / marked + ", " + far + " of " + marked + " past 24");
        }
    }
    static void lift() {
        ModularStroke.latticePx = 200;
        ModularBrush b = brush("wet-wash", PartChoice.FAST_WET);
        ToneDocument d = page(b);
        ModularStroke s = wave(d, b, 300);
        byte[][] before = ModularStrokeChecks.layers(d);
        s.finish();
        check(ModularStrokeChecks.same(before, ModularStrokeChecks.layers(d)), "Fast-wet on the lattice: nothing changes at lift");
        d.undo();
        for (int y = 0; y < d.height; y += 9) for (int x = 0; x < d.width; x += 9) check(d.compositeTone(x, y) == 255, "and one Undo takes it all back");
    }
    static void shortcut() {
        ModularStroke.latticePx = 200;
        for (ModularBrush b : new ModularBrush[]{brush("wet-wash", PartChoice.FAST_WET), brush("thick-oil", PartChoice.FAST_WET)}) {
            String[] sums = new String[2];
            for (int i = 0; i < 2; i++) {
                ModularCompose.skipUnchanged = i == 0;
                try { ToneDocument d = page(b); wave(d, b, 300).finish(); sums[i] = LegacyConversionChecks.crc(d); }
                finally { ModularCompose.skipUnchanged = true; }
            }
            check(sums[0].equals(sums[1]), "On the lattice, composing only what changed draws as composing everything: " + sums[0] + " vs " + sums[1]);
        }
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
