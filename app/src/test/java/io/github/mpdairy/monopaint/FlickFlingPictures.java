package io.github.mpdairy.monopaint;

import java.io.File;

/**
 * The mischief modules spec's Stage 2 pictures, drawn by the app's engine: a Brush pen swoosh lifted while moving,
 * without and with Flick; a dash stopped dead and held, without and with Fling; a Watercolour wash and a Halftone ink
 * flicked. Composites at full grey, panels side by side. scripts/flickfling_pictures.sh.
 */
public class FlickFlingPictures {
    static final int W = 640, H = 420;
    public static void main(String[] a) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        File out = new File(a.length > 0 ? a[0] : "/tmp/flickfling-pictures");
        out.mkdirs();
        ModularBrush pen = FloatChecks.preset("Swell brush");
        WetSmudgePictures.save(new File(out, "flick-brush-pen.png"), grab(pen, 0, 0, 14, swoosh()), grab(pen, .7, 0, 14, swoosh()));
        WetSmudgePictures.save(new File(out, "fling-brush-pen.png"), grab(pen, 0, 0, 14, dash()), grab(pen, 0, .8, 14, dash()));
        ModularBrush halftone = pen.copy();
        halftone.part(BrushPart.MEDIUM).set(PartDial.SCREEN, 1);
        WetSmudgePictures.save(new File(out, "flick-wash-and-halftone.png"), grab(FloatChecks.preset("Soft wash"), .6, 0, 70, swoosh()), grab(halftone, .8, .6, 14, swoosh()));
        System.out.println("wrote " + out);
    }
    /** {@code b} with Flick and Fling, along {@code path}, as the drawing screen draws it (quick by the rule), a Slow wash settled. */
    static int[][] grab(ModularBrush b, double flick, double fling, double size, double[][] path) {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        CarrySmudgeChecks.feed(QuickStroke.start(d, FlickFlingChecks.with(b, flick, fling, null), size, 1, s), path);
        s.settleNow(d);
        return WetSmudgePictures.grab(d);
    }
    /** An arc speeding up to about 4 px/ms, lifted while moving (mischief-lab.html's swoosh, at the app's scale). */
    static double[][] swoosh() {
        int n = 120;
        double[][] o = new double[n + 1][];
        double t = 0;
        for (int i = 0; i <= n; i++) {
            double u = i / (double) n, ang = Math.PI * (1.1 - .75 * u);
            o[i] = new double[]{200 + 170 * Math.cos(ang), 330 - 220 * Math.sin(ang) * (.6 + .4 * u), .35 + .5 * Math.sin(Math.PI * Math.min(1, u * 1.2)), 1000 + t};
            t += 4.5 - 3.3 * u;
        }
        return o;
    }
    /** A dash speeding up, stopped dead and held 200 ms before the lift. */
    static double[][] dash() { return FlickFlingChecks.dash(80, 330, 230, 200); }
}
