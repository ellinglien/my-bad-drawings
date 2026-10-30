package io.github.mpdairy.monopaint;

import java.io.File;

/**
 * Pictures of Float (suminagashi) drawn by the app's engine, for looking (not a check): {@code java FloatPictures <out
 * folder>} (scripts/float_pictures.sh). Each scene is drawn as the drawing screen draws it ({@link
 * WatercolorSession#settleBefore}, then {@link QuickStroke#start}), printed, and saved as {@link CarryPictures#save}
 * does: the Manta's dots beside the 16 greys.
 */
public final class FloatPictures {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        File out = new File(args.length > 0 ? args[0] : ".");
        out.mkdirs();
        CarryPictures.save(drops(), new File(out, "1-drops-sumi-and-clear-water"));
        CarryPictures.save(combed(), new File(out, "2-combed-feathers"));
        CarryPictures.save(hake(), new File(out, "3-hake-band-through-rings"));
        CarryPictures.save(drift(), new File(out, "4-tilted-drift"));
        System.out.println("wrote " + out);
    }

    static final int W = 900, H = 600;

    static ModularBrush sumi() { return FloatChecks.sumi(); }
    static ModularBrush clear() { return FloatChecks.clear(); }
    static ModularBrush tip(ModularBrush b, PartChoice tip) { return FloatChecks.tip(b, tip); }

    private static long clock;
    /** A stroke as the drawing screen starts one: what settles laid first (a Float stroke joins the bath), then quick. */
    static void stroke(ToneDocument d, WatercolorSession session, ModularBrush b, double size, double darkness, double[][] path) {
        session.settleBefore(d, b);
        DrawingStroke s = QuickStroke.start(d, b, size, darkness, session);
        for (double[] p : path) { s.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), 0, 0, clock + (long) p[3]); s.present(); }
        s.finish();
        clock += 10_000;
    }
    static double[][] tap(double x, double y, double p, int ms) { return FloatChecks.tap(x, y, p, ms); }
    /** Sumi and Clear water in turn at {@code x, y}, {@code n} times each, held a little differently each time. */
    static void rings(ToneDocument d, WatercolorSession s, double x, double y, int n, double size, double seed) {
        for (int i = 0; i < n; i++) {
            double wob = Math.sin(seed + i * 2.1);
            // A hand never taps the same spot twice: a few px off each time.
            double jx = 3 * Math.sin(seed * 7 + i * 2.3), jy = 3 * Math.cos(seed * 5 + i * 1.9);
            stroke(d, s, sumi(), size, i % 3 == 2 ? .55 : 1, tap(x + jx, y + jy, .55 + .3 * Math.sin(seed * 3 + i), (int) (260 + 120 * wob)));
            stroke(d, s, clear(), size, 1, tap(x - jy, y + jx, .5, (int) (180 + 90 * Math.cos(seed + i * 1.7))));
        }
    }
    static double[][] line(double x0, double y0, double x1, double y1, double p) { return FloatChecks.line(x0, y0, x1, y1, p); }

    /** Three sites of Sumi and Clear water taps in turn. */
    static ToneDocument drops() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        rings(d, s, 250, 260, 8, 10, 1);
        rings(d, s, 640, 230, 7, 10, 2);
        rings(d, s, 450, 470, 6, 6, 3);
        s.settleNow(d);
        return d;
    }
    /** A bath of six sites combed: a 5-line Clear water rake across and back, then a round Clear water swirl. */
    static ToneDocument combed() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        for (int k = 0; k < 6; k++) rings(d, s, 170 + (k % 3) * 280, 170 + (k / 3) * 260, 7, 10, k + 1);
        ModularBrush rake = tip(clear(), PartChoice.MULTI_LINE);
        rake.part(BrushPart.TIP).set(PartDial.COUNT, 5);
        for (int k = 0; k < 4; k++) {
            double y = 110 + k * 125;
            stroke(d, s, rake, 14, 1, k % 2 == 0 ? line(20, y, 880, y + 20, .7) : line(880, y + 20, 20, y, .7));
        }
        int n = 300;
        double[][] swirl = new double[n][];
        for (int i = 0; i < n; i++) { double a = i * .05, r = 30 + i * .5; swirl[i] = new double[]{450 + r * Math.cos(a), 300 + r * Math.sin(a), .7, i * 4}; }
        stroke(d, s, clear(), 30, 1, swirl);
        s.settleNow(d);
        return d;
    }
    /** A hake wash laid floating above two sites of rings, then a Clear water hake dragged through their middles. */
    static ToneDocument hake() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        ModularBrush wash = tip(sumi(), PartChoice.HAKE);
        wash.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.WASH));
        wash.normalize();
        stroke(d, s, wash, 90, 1, line(40, 200, 860, 200, .7));
        rings(d, s, 280, 300, 7, 10, 4);
        rings(d, s, 620, 300, 7, 10, 5);
        ModularBrush comb = tip(clear(), PartChoice.HAKE);
        stroke(d, s, comb, 90, 1, line(40, 300, 860, 300, .7));
        stroke(d, s, comb, 60, 1, line(860, 430, 40, 430, .7));
        s.settleNow(d);
        return d;
    }
    /** The same rings printed level (left) and after 5 s tilted down and to the right (right). */
    static ToneDocument drift() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        rings(d, s, 220, 250, 7, 6, 6);
        s.settleNow(d);
        s.tilt = () -> PageTilt.of(.45, .6);
        rings(d, s, 620, 250, 7, 6, 6);
        for (int t = 0; t < 50; t++) s.tickDrying(d);
        s.settleNow(d);
        return d;
    }
}
