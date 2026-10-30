package io.github.mpdairy.monopaint;

/**
 * A body's rim is smooth, not a row of beads (the owner, 2026-10-29, testing a chisel wash on canvas: "i see a
 * repeated pattern along the edge instead of a smooth line"): straight strokes of chisel washes and a crisp opaque
 * round, samples 3 px apart as a steady hand, and along every row of the top edge the strongest single repeat (2 to
 * 30 px) stays under {@link #MOST} of 255. The beads were the body's dabs ({@link BodyStamper#spacing}) a quarter of
 * the soft edge deep, at the round dab's bend not the oval's, and laid only where samples fell: 14 to 22. So too a big
 * line's edge, the Springy brush at 98 and 150 px, steady and quick (samples 12 px apart): its dabs a twelfth of the
 * nib apart and laid a step of a tenth past their grid line ({@link LineStamper#stamp}), 12 to 21.
 */
public final class EdgeRippleChecks {
    /** The strongest repeat a rim may keep, of 255: under half the panel's grey step (17). */
    static final double MOST = 8;
    /** Anything goes rolls (Chisel, Wash, Canvas, Quick) whose rims beaded. */
    static final String[] CODES = {
            "painty-mold-situation.Md1qpwplk7xqh00n05e_18j0000000000_0_5am_1rm_8s8ni000000kl000000nz0.14tx60o",
            "sooty-lift-junior.Md1yff2lial00sq9z22_1y8f003j54q00_0_503_1h2_8p300000000000000c9nz0.hyzgr1",
    };

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        ModularBrush opaque = PresetPictures.brush("Gouache", "crisp opaque", 7, PartChoice.ROUND);
        PresetPictures.medium(opaque, PartChoice.COVER);
        ModularBrush[] brushes = {ModularCode.decode(CODES[0]), ModularCode.decode(CODES[1]), opaque};
        for (ModularBrush b : brushes) for (double size : new double[]{40, 98}) smooth(b, size, 3);
        ModularBrush springy = ModularCode.decode(SPRINGY);
        for (double size : new double[]{98, 150}) for (float step : new float[]{3, 12}) smooth(springy, size, step);
        System.out.println("PASS: chisel washes, a crisp opaque round and a big Springy brush keep their edges free of a repeat");
    }
    /** The Springy brush (ToolPresets), a round line that swells as it presses. */
    static final String SPRINGY = "springy-brush.M10ssati000003m_000zz0000000000000_0_000_0_4weatg7000000ee0000nz0.2fg";
    static void smooth(ModularBrush b, double size, float step) {
        double[] worst = ripple(stroke(b, size, step));
        System.out.printf("%s at %.0f px, samples %.0f px apart: strongest repeat %.1f of 255, %.1f px apart%n", b.name, size, step, worst[0], worst[1]);
        check(worst[0] < MOST, b.name + " at " + (int) size + " px, samples " + (int) step + " px apart, keeps a smooth edge");
    }

    static final int W = 620, H = 400;

    /** A straight stroke across, normal pressure, a sample every {@code step} px every 4 ms; its tones. */
    static int[] stroke(ModularBrush b, double size, float step) {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession session = new WatercolorSession();
        DrawingStroke s = QuickStroke.start(d, b, size, .75, session);
        float p = ToolSettings.pressure(.6f);
        for (int k = 0; k * step <= 500; k++) { s.sample(60 + step * k, H / 2f, p, 0, 0, k * 4L); s.present(); }
        s.finish(); session.settleNow(d);
        int[] t = new int[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) t[y * W + x] = d.compositeTone(x, y);
        return t;
    }

    /** The strongest single repeat, 2 to 30 px, of any row above the middle (x 150 to 470, less its 31 px running mean): {amplitude, period}. */
    static double[] ripple(int[] tones) {
        int x0 = 150, n = 320, m = 15;
        double[] best = {0, 0};
        for (int y = 5; y < H / 2; y++) {
            double sum = 0;
            for (int x = x0; x < x0 + n; x++) sum += tones[y * W + x];
            if (sum / n > 252 || sum / n < 3) continue;
            double[] r = new double[n - 2 * m];
            for (int i = 0; i < r.length; i++) {
                double mean = 0;
                for (int j = -m; j <= m; j++) mean += tones[y * W + x0 + m + i + j];
                r[i] = tones[y * W + x0 + m + i] - mean / (2 * m + 1);
            }
            for (int k = (int) Math.ceil(r.length / 30.0); k <= r.length / 2; k++) {
                double re = 0, im = 0;
                for (int i = 0; i < r.length; i++) { double a = 2 * Math.PI * k * i / r.length; re += r[i] * Math.cos(a); im += r[i] * Math.sin(a); }
                double amp = 2 * Math.hypot(re, im) / r.length;
                if (amp > best[0]) { best[0] = amp; best[1] = (double) r.length / k; }
            }
        }
        return best;
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: " + what); }
}
