package io.github.mpdairy.monopaint;

import java.io.File;

/**
 * The oil paint spec's pictures (docs/archive/superpowers/specs/2026-10-19-oil-paint-design.md), drawn by the app's engine: the
 * Oil paint Basic tool across a dark wash (its Starting Load, then a full load as a rub gives it), and the Filbert's
 * first touches and short strokes, as a crescent with Paint Load and as its oval without. Composites at full grey,
 * panels side by side. scripts/oil_pictures.sh.
 */
public class OilPictures {
    static final int W = 640, H = 420;

    public static void main(String[] a) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        File out = new File(a.length > 0 ? a[0] : "/tmp/oil-pictures");
        out.mkdirs();
        overWash(out); touches(out);
        System.out.println("wrote " + out);
    }

    /** A dark wash band down the middle; Oil paint across it at its Starting Load, then again after a full rub. */
    static void overWash(File out) throws Exception {
        ToneDocument d = OilChecks.darkBand(W, H);
        int[][] before = WetSmudgePictures.grab(d);
        WatercolorSession s = new WatercolorSession();
        ModularBrush oil = OilChecks.oil();
        // Its Starting Load: a light grey laid solid, running out on the dark band, then smudging it on and leaving nothing.
        OilChecks.stroke(d, s, "SLOT1", oil, 60, .3, OilChecks.line(30, 110.4, 610, 106.3, .8));
        // Loaded full, as a long rub on a grey leaves it: it lays all the way across.
        s.held.keepLoad("SLOT1", 1);
        OilChecks.stroke(d, s, "SLOT1", oil, 60, .3, OilChecks.line(30, 300.4, 610, 296.3, .8));
        WetSmudgePictures.save(new File(out, "oil-paint-over-dark-wash.png"), before, WetSmudgePictures.grab(d));
        System.out.println(String.format("Oil paint: start %.0f, on the band %.0f, past it %.0f (paper 255, band %d)",
                CarrySmudgeChecks.mean(d, 40, 70, 100, 120), CarrySmudgeChecks.mean(d, 290, 340, 100, 120), CarrySmudgeChecks.mean(d, 520, 600, 100, 120), OilChecks.BAND));
    }

    /** Filbert taps and short strokes four ways, the pen leaning 40° to the right: with Paint Load (crescents), and without. */
    static void touches(File out) throws Exception {
        int[][][] panels = new int[2][][];
        for (int k = 0; k < 2; k++) {
            ToneDocument d = InkWashLayers.blank(W, H);
            WatercolorSession s = new WatercolorSession();
            ModularBrush b = OilChecks.oil();
            if (k == 1) b.part(BrushPart.CARRY).set(PartDial.PLOAD, 0);
            // Taps: a pen leaning right, up and up-right.
            double[][] leans = {{40, 0}, {0, -40}, {30, -30}};
            for (int i = 0; i < leans.length; i++) OilChecks.stroke(d, s, "SLOT1", b, 80, 1, OilChecks.tap(110 + 200 * i, 90, leans[i][0], leans[i][1]));
            // Short strokes right, left, down and up, leaning right.
            double[][] ways = {{60, 250, 160, 250}, {360, 250, 260, 250}, {500, 190, 500, 270}, {580, 330, 580, 250}};
            for (double[] w : ways) OilChecks.stroke(d, s, "SLOT1", b, 80, 1, OilChecks.line(w[0], w[1], w[2], w[3], .8, 40, 0));
            panels[k] = WetSmudgePictures.grab(d);
        }
        WetSmudgePictures.save(new File(out, "filbert-first-touches.png"), panels);
    }
}
