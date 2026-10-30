package io.github.mpdairy.monopaint;

import java.util.Locale;

/**
 * The smudge tool's brushes at its four sizes (the owner, 2026-10-19: the Water brush's and the Dirty brush's preview
 * "much smaller than the area of effect", the Blend stump's two smallest "no effect"): drawn straight across a striped
 * wash on the brush's own layer, the band the page changes in, across the stroke, is about as wide as the size dot
 * shows, while the pen is down (a quick stroke's smudge written under the pen, or the live stroke) and after lift (a Slow one settled); a
 * Water brush's run-out spreads a little past it, not several times. The Blend stump's smallest sizes blend over pencil
 * and charcoal hatching about as much of their band as the bigger ones do. {@code java SmudgeSizeChecks v} prints the numbers.
 */
public final class SmudgeSizeChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        verbose = args.length > 0;
        footprints(); smallStumps();
        System.out.println("PASS: each smudge brush at the smudge tool's four sizes changes a band about as wide as its size dot, under the pen and after lift, a quick one's too; the Water brush's run-out spreads a little past it; the Blend stump's two smallest sizes blend their band");
    }
    static boolean verbose;
    static void note(String s) { if (verbose) System.out.println(s); }
    static final int W = 480, H = 360, Y = 180;
    static final String[] BRUSHES = {"Blend stump", "Dirty brush", "Water brush"};

    /** Every tone shown. */
    static int[][] tones(ToneDocument d) {
        int[][] t = new int[d.height][d.width];
        for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) t[y][x] = d.compositeTone(x, y);
        return t;
    }
    /** How many rows across the stroke (at columns 230 to 250) changed by 4 tones or more, top to bottom; 0 for none. */
    static int band(int[][] a, int[][] b) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int y = 0; y < H; y++) for (int x = 230; x <= 250; x++) if (Math.abs(a[y][x] - b[y][x]) >= 4) { lo = Math.min(lo, y); hi = Math.max(hi, y); }
        return lo > hi ? 0 : hi - lo + 1;
    }

    /** 1. Each brush at each size: the band under the pen and after lift against its size dot (the size it draws at, px across). */
    static void footprints() {
        for (String name : BRUSHES) {
            ModularBrush b = WetSmudgeChecks.preset(name);
            for (double size : ToolSwatches.SIZES) {
                ToneDocument d = InkWashLayers.blank(W, H);
                CarrySmudgeChecks.lay(d, CarrySmudgeChecks.ownLayer(b), (x, y) -> y >= 40 && y < 320 ? (Math.floorMod(x, 10) < 5 ? 90 : 200) : 255);
                int[][] before = tones(d);
                // As the drawing screen starts the smudge tool's stroke (InkWash.stroke), fed by hand to look before lift.
                WatercolorSession s = new WatercolorSession();
                s.holding("SMUDGE");
                ModularBrush parts = s.onWater(d, b);
                s.settleBefore(d, parts);
                s.dry("stroke");
                DrawingStroke st = QuickStroke.start(d, parts, size, 1, s);
                double[][] path = FloatChecks.line(60, Y, 420, Y, .8);
                for (int i = 0; i < path.length; i++) {
                    double[] p = path[i];
                    st.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), 0, 0, (long) p[3]);
                    if (i % 3 == 2) st.present();
                }
                // A quick one's smudge shows once its render has caught up and composed (QuickStroke's trail, under the pen).
                if (st instanceof QuickStroke) {
                    QuickStroke q = (QuickStroke) st;
                    for (int k = 0; k < 1000 && (q.rendered() < q.samples() || q.trailTiles == 0); k++) { sleep(); st.present(); }
                }
                st.present();
                int down = band(before, tones(d));
                st.finish();
                WetSmudgeChecks.tick(s, d);
                s.settleNow(d);
                int after = band(before, tones(d));
                // The smudge's dab is never under 3 px in radius, so a 4 px one works 6 px across.
                double dot = Math.max(size, 6);
                note(String.format(Locale.ROOT, "%-11s %3.0f px (%s): under the pen %3d rows, after lift %3d", name, size, st instanceof QuickStroke ? "quick" : "live", down, after));
                check(down >= .7 * dot && down <= 1.25 * dot + 4, name + " at " + size + " px shows " + down + " rows under the pen");
                check(after >= .7 * dot && after <= 1.5 * dot + 6, name + " at " + size + " px changes " + after + " rows after lift");
                check(after <= 1.5 * down + 6, name + " at " + size + " px: after lift (" + after + ") not far past what showed under the pen (" + down + ")");
            }
        }
    }

    /**
     * 2. The Blend stump at its two smallest sizes across pencil and charcoal hatching (lines on the ink layer, its own)
     * changes a grey or more over as much of its band (its size across) as the bigger ones do, near enough: before
     * 2026-10-19 a 4 or 16 px stump changed a third of a pencil band, a 44 px one 61%.
     */
    static void smallStumps() {
        ModularBrush stump = WetSmudgeChecks.preset("Blend stump");
        for (Object[] m : new Object[][]{{"HB pencil", 6.0, .4}, {"Soft charcoal", 8.0, .6}}) {
            ModularBrush mark = WetSmudgeChecks.preset((String) m[0]);
            for (double size : new double[]{4, 16, 44}) {
                ToneDocument d = InkWashLayers.blank(W, H);
                WatercolorSession s = new WatercolorSession();
                for (int x = 100; x <= 380; x += 30) CarrySmudgeChecks.feed(QuickStroke.start(d, mark, (Double) m[1], 1, s), FloatChecks.line(x, 60, x, 300, .8));
                int[][] before = tones(d);
                CarrySmudgeChecks.feed(QuickStroke.start(d, stump, size, 1, s), FloatChecks.line(60, Y, 420, Y, .8));
                int[][] after = tones(d);
                int changed = 0, in = 0;
                for (int y = 0; y < H; y++) for (int x = 90; x < 390; x++) {
                    if (!(Math.abs(y + .5 - Y) < size / 2)) continue;
                    in++;
                    if (CarrySmudgeChecks.q(before[y][x]) != CarrySmudgeChecks.q(after[y][x])) changed++;
                }
                double share = changed / (double) in;
                note(String.format(Locale.ROOT, "Blend stump %3.0f px over %s: %.0f%% of its band a grey or more", size, m[0], 100 * share));
                check(share >= (Double) m[2], "a " + size + " px Blend stump blends over " + m[0] + ": " + Math.round(100 * share) + "% of its band changes a grey");
            }
        }
    }

    private static void sleep() { try { Thread.sleep(2); } catch (InterruptedException e) { throw new RuntimeException(e); } }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
