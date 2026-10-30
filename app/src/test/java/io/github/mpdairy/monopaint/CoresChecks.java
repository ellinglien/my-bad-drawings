package io.github.mpdairy.monopaint;

/**
 * Work shared out over the cores ({@link Cores}: compose, stretch, stampers, settles, blurs) draws exactly what one
 * core does, big strokes on the lattice and small ones, each life; and the pen's batched samples ({@link PenBatch})
 * reach the stroke as they came.
 */
public final class CoresChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        Object[][] cases = {{"wet-wash", PartChoice.FAST_WET, 300.0}, {"thick-oil", PartChoice.FAST_WET, 300.0}, {"wide-hake", PartChoice.FAST_WET, 260.0},
                {"soft-coal", PartChoice.NONE, 260.0}, {"brave-onion", PartChoice.DRYING, 300.0}, {"rake", PartChoice.FAST_WET, 60.0}, {"wet-wash", PartChoice.DRYING, 40.0}};
        for (Object[] c : cases) {
            ModularBrush b = LatticeChecks.brush((String)c[0], (PartChoice)c[1]);
            double size = (Double)c[2];
            String shared = draw(b, size, true), alone = draw(b, size, false);
            check(shared.equals(alone), c[0] + " " + c[1] + " at " + size + " draws the same on several cores as on one: " + shared + " vs " + alone);
        }
        batch();
        System.out.println("PASS: work shared out over the cores draws what one core does (washes, oil, hake, charcoal, a Drying lift, small and big), and batched pen samples reach the stroke in order");
    }
    static String draw(ModularBrush b, double size, boolean shared) {
        boolean was = Cores.shared;
        Cores.shared = shared;
        try { ToneDocument d = LatticeChecks.page(b); LatticeChecks.wave(d, b, size).finish(); return LegacyConversionChecks.crc(d); }
        finally { Cores.shared = was; }
    }
    static void batch() {
        StringBuilder got = new StringBuilder();
        DrawingStroke recorder = new DrawingStroke() {
            @Override public void sample(float x, float y, float p, float tx, float ty) { }
            @Override public void sample(float x, float y, float p, float tx, float ty, long t) { got.append(x).append(',').append(y).append(',').append(p).append(',').append(tx).append(',').append(ty).append(',').append(t).append(';'); }
            @Override public boolean finish() { return false; }
        };
        PenBatch batch = new PenBatch();
        StringBuilder want = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            batch.add(i, 2 * i, i / 1000f, -i, i % 7, 5L * i);
            want.append((float)i).append(',').append((float)(2 * i)).append(',').append(i / 1000f).append(',').append((float)-i).append(',').append((float)(i % 7)).append(',').append(5L * i).append(';');
            if (i == 0) check(batch.oldest() == 0 && batch.size() == 1, "The oldest sample's time");
            if (i % 333 == 332) check(batch.feed(recorder) == 333 && batch.isEmpty(), "A batch feeds all it gathered and empties");
        }
        batch.feed(recorder);
        check(got.toString().equals(want.toString()), "Batched samples reach the stroke as they came, in order");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
