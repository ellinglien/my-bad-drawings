package io.github.mpdairy.monopaint;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * The wet smudge spec's pictures (docs/archive/superpowers/specs/2026-10-19-wet-smudge-design.md), drawn by the app's engine:
 * the Water brush through a dried wash (before, at lift, dried; on a level page and a tilted one), and two Dirty brush
 * strokes, the second carrying the first's paint onto clean paper. Composites at full grey, panels side by side.
 * scripts/wetsmudge_pictures.sh.
 */
public class WetSmudgePictures {
    static final int W = 640, H = 420;
    static ModularBrush preset(String name) { return FloatChecks.preset(name); }
    static double[][] line(double x0, double y0, double x1, double y1) { return FloatChecks.line(x0, y0, x1, y1, .8); }
    static double mean(ToneDocument d, int x0, int x1, int y0, int y1) { return CarrySmudgeChecks.mean(d, x0, x1, y0, y1); }
    static int[][] grab(ToneDocument d) { int[][] g = new int[d.height][d.width]; for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) g[y][x] = d.compositeTone(x, y); return g; }
    /** {@code panels} side by side, grey, a gap between. */
    static void save(File f, int[][]... panels) throws Exception {
        int h = panels[0].length, w = panels[0][0].length, gap = 10;
        BufferedImage img = new BufferedImage(panels.length * w + (panels.length - 1) * gap, h, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < h; y++) for (int x = 0; x < img.getWidth(); x++) img.getRaster().setSample(x, y, 0, 100);
        for (int k = 0; k < panels.length; k++) for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.getRaster().setSample(k * (w + gap) + x, y, 0, panels[k][y][x]);
        javax.imageio.ImageIO.write(img, "png", f);
    }
    public static void main(String[] a) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        File out = new File(a.length > 0 ? a[0] : "/tmp/wetsmudge-pictures");
        out.mkdirs();
        water(out, false); water(out, true); dirty(out);
        System.out.println("wrote " + out);
    }
    /** A dried wash (Soft wash rows, settled), then the Water brush across it: before, at lift, and dried. */
    static void water(File out, boolean tilted) throws Exception {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        for (int y = 80; y <= 340; y += 40) WetSmudgeChecks.stroke(d, s, HeldPaint.ANY, preset("Soft wash"), 110, 1, line(80, y, 560, y));
        s.settleNow(d); s.dry("dried");
        int[][] before = grab(d);
        double m0 = mean(d, 300, 340, 120, 300);
        if (tilted) s.tilt = () -> PageTilt.of(0, 1);
        long t0 = System.nanoTime();
        WetSmudgeChecks.stroke(d, s, "SMUDGE", preset("Water brush"), 60, .5, line(200, 60, 440, 360));
        long t1 = System.nanoTime();
        int[][] lifted = grab(d);
        int ticks = 0;
        while (s.tickDrying(d)) ticks++;
        save(new File(out, "water-brush-dried-wash" + (tilted ? "-tilted" : "") + ".png"), before, lifted, grab(d));
        System.out.println((tilted ? "tilted: " : "level: ") + String.format("the stroke %.1f ms, wet %d ticks; mean tone in its path %.1f → %.1f", (t1 - t0) / 1e6, ticks, m0, mean(d, 300, 340, 120, 300)));
    }
    /** A dark band; a Dirty brush stroke through it, then one on clean paper. */
    static void dirty(File out) throws Exception {
        ToneDocument e = InkWashLayers.blank(W, H);
        CarrySmudgeChecks.lay(e, InkWashLayers.WASH, (x, y) -> x >= 100 && x < 180 && y >= 60 && y < 200 ? 60 : 255);
        int[][] e0 = grab(e);
        WatercolorSession t = new WatercolorSession();
        WetSmudgeChecks.stroke(e, t, "SMUDGE", preset("Dirty brush"), 60, .5, line(60, 130, 230, 130));
        int[][] e1 = grab(e);
        WetSmudgeChecks.stroke(e, t, "SMUDGE", preset("Dirty brush"), 60, .5, line(260, 300, 600, 300));
        save(new File(out, "dirty-brush-two-strokes.png"), e0, e1, grab(e));
        System.out.println(String.format("Dirty brush: the second stroke's start %.1f, its end %.1f (paper 255)", mean(e, 265, 300, 285, 315), mean(e, 540, 590, 285, 315)));
    }
}
