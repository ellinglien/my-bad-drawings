package io.github.mpdairy.monopaint;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Inkwash's Smear beside upstream MonoPaint's Blending stump ({@link ToolStroke} with {@code SOFTEN}, {@link
 * ToneDabs#soften}) and its oil brush running out into a smudge ({@link PaintLoad}), each on the same test page, drawn by
 * the app's engine, for looking (not a check): {@code java StumpPictures <out folder>}. Each picture stacks the page
 * smudged by each tool (one straight stroke and one wave, left to right), at 256 greys, with the tool's name and its
 * time a sample on this machine. MonoPaint's tools work on the stroke's layer as the drawing screen would select it.
 */
public final class StumpPictures {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        File out = new File(args.length > 0 ? args[0] : ".");
        out.mkdirs();
        for (Page page : Page.values()) {
            BufferedImage img = new BufferedImage(W, (H + LABEL) * TOOLS.length, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = img.createGraphics();
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            for (int t = 0; t < TOOLS.length; t++) {
                ToneDocument d = page.make();
                WatercolorSession s = SESSION;
                long ns = smudge(d, s, TOOLS[t], page);
                int top = t * (H + LABEL);
                g.setColor(new Color(60, 60, 60)); g.fillRect(0, top, W, LABEL);
                g.setColor(Color.WHITE);
                String label = TOOLS[t] + "   " + String.format("%.1f µs a sample", ns / 1000.0) + "   " + measure(page, d);
                g.drawString(label, 8, top + 16);
                System.out.println(page.file + ": " + label);
                for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) img.getRaster().setSample(x, top + LABEL + y, 0, d.compositeTone(x, y));
            }
            javax.imageio.ImageIO.write(img, "png", new File(out, page.file + ".png"));
        }
        System.out.println("wrote " + out);
    }

    static final int W = 900, H = 300, LABEL = 22;
    /** The tools compared: Inkwash's Smear finger (the Stump on the ink layer, a wash finger on the wash layer), MonoPaint's stump, its oil brush. */
    static final String[] TOOLS = {"Inkwash Stump (Smear, Own paint 0)", "Inkwash Smear at Soften 1, Pick up .4", "MonoPaint Blending stump", "MonoPaint oil brush, paint runs out"};
    /** The smudge's diameter, px: MonoPaint's stump at its default size and a firm press (19 + 38 × .8² ≈ 43). */
    static final double SIZE = 44;
    static WatercolorSession SESSION;

    enum Page {
        PENCIL("1-pencil-hatching", true), INK("2-ink-lines", true), DRY("3-dry-wash", false), WET("4-wet-slow-wash", false), OPAQUE("5-opaque-paint", false);
        final String file; final boolean ink;
        Page(String file, boolean ink) { this.file = file; this.ink = ink; }
        ToneDocument make() {
            ToneDocument d = InkWashLayers.blank(W, H);
            SESSION = new WatercolorSession();
            WatercolorSession s = SESSION;
            switch (this) {
                case PENCIL: {
                    ModularBrush pencil = CarryPictures.brush("Pencil", PartChoice.GRAPHITE, PartChoice.OFF, PartChoice.NONE);
                    pencil.part(BrushPart.MEDIUM).set(PartDial.GRADE, .7);
                    for (int x = 60; x < 860; x += 9) CarryPictures.stroke(d, s, pencil, 4, 1, CarryPictures.line(x, 20, x + 40, 280, .8));
                    break;
                }
                case INK: {
                    ModularBrush pen = CarryPictures.brush("Fineliner", PartChoice.INK, PartChoice.OFF, PartChoice.NONE);
                    for (int x = 90; x < 860; x += 60) CarryPictures.stroke(d, s, pen, 6, 1, CarryPictures.line(x, 20, x, 280, .8));
                    break;
                }
                case DRY: case WET: {
                    ModularBrush wash = CarryPictures.brush("Watercolor", PartChoice.WASH, PartChoice.OFF, PartChoice.DRYING);
                    for (int x = 150; x < 860; x += 200) {
                        CarryPictures.stroke(d, s, wash, 110, .8, CarryPictures.line(x, 20, x, 280, .8));
                        if (this == DRY) s.settleNow(d);
                    }
                    break;
                }
                case OPAQUE: {
                    ModularBrush paint = CarryPictures.brush("Gouache", PartChoice.COVER, PartChoice.OFF, PartChoice.NONE);
                    double[] greys = {.9, .3, .65, .15};
                    for (int i = 0; i < 4; i++) CarryPictures.stroke(d, s, paint, 90, greys[i], CarryPictures.line(140 + 190 * i, 20, 140 + 190 * i, 280, .8));
                    break;
                }
            }
            return d;
        }
    }

    /** Smudges page {@code d} with tool {@code t}: a straight stroke at y 90, a wave about y 210; returns the mean time a sample, ns. */
    static long smudge(ToneDocument d, WatercolorSession s, String tool, Page page) {
        double[][] a = CarryPictures.line(20, 90, 880, 90, .8), b = CarryPictures.wave(20, 880, 210, 35, .8);
        long ns = 0; int n = 0;
        for (double[][] path : new double[][][]{a, b}) {
            s.settleNow(d);
            DrawingStroke st;
            if (tool.startsWith("Inkwash")) {
                ModularBrush f = page.ink ? CarryPictures.stump() : washFinger();
                if (tool.contains("Soften")) { f = f.copy(); f.part(BrushPart.CARRY).set(PartDial.SOFTEN, 1); f.part(BrushPart.CARRY).set(PartDial.PICK, .4); f = f.normalize(); }
                st = QuickStroke.start(d, f, SIZE, 1, s);
            }
            else {
                d.selectLayer(page.ink ? InkWashLayers.INK : InkWashLayers.WASH);
                st = tool.contains("stump") ? new ToolStroke(d, ToolSettings.defaults(ToolSettings.Tool.SOFTEN).size((int) SIZE).minimum((int) SIZE), 0)
                        : new OilBrush(d, SIZE, 128);
            }
            long t0 = System.nanoTime();
            for (double[] p : path) { st.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), 0, 0, (long) p[3]); st.present(); }
            st.finish();
            ns += System.nanoTime() - t0; n += path.length;
        }
        return ns / n;
    }
    /** Inkwash's finger on the wash layer: Watercolor, Smear, Own paint 0, the Stump's Pick up and Lay down. */
    static ModularBrush washFinger() {
        ModularBrush f = CarryPictures.brush("Watercolor", PartChoice.WASH, PartChoice.SMUDGE, PartChoice.NONE);
        f.part(BrushPart.CARRY).set(PartDial.LOAD, 0); f.part(BrushPart.CARRY).set(PartDial.PICK, .7); f.part(BrushPart.CARRY).set(PartDial.LAY, .6);
        return f.normalize();
    }

    /** How far the straight stroke (y 90) carried the page's marks: the darkness it added past each mark, at half a width and a width on. */
    static String measure(Page page, ToneDocument d) {
        ToneDocument clean = page.make();
        double half = 0, whole = 0, two = 0, contrast0 = 0, contrast1 = 0; int k = 0;
        for (int x = 40; x < 860; x++) {
            double before = mean(clean, x, 90), after = mean(d, x, 90);
            if (clean.compositeTone(x, 90) < 200 && clean.compositeTone(x + 1, 90) >= 200) {
                // a mark's right edge: what lies a half width, a width and two widths on, along the stroke
                half += before(clean, d, x + (int) (SIZE / 2)); whole += before(clean, d, x + (int) SIZE); two += before(clean, d, x + 2 * (int) SIZE); k++;
            }
            contrast0 += Math.abs(mean(clean, x + 1, 90) - before); contrast1 += Math.abs(mean(d, x + 1, 90) - after);
        }
        double m0 = 0, m1 = 0;
        for (int x = 120; x < 780; x++) for (int y = 84; y <= 96; y++) { m0 += clean.compositeTone(x, y); m1 += d.compositeTone(x, y); }
        m0 /= 660 * 13; m1 /= 660 * 13;
        if (k == 0) return "";
        return String.format("past a mark %.0f / %.0f / %.0f tones at ½, 1, 2 widths; edges %.0f%%; track %.0f → %.0f", half / k, whole / k, two / k, 100 * contrast1 / Math.max(1e-9, contrast0), m0, m1);
    }
    private static double before(ToneDocument clean, ToneDocument d, int x) { return x >= W - 2 ? 0 : mean(clean, x, 90) - mean(d, x, 90); }
    /** The composite's mean tone over five rows about {@code y}. */
    private static double mean(ToneDocument d, int x, int y) { double s = 0; for (int j = -2; j <= 2; j++) s += d.compositeTone(x, y + j); return s / 5; }

    /**
     * MonoPaint's opaque brush with paint that runs out ({@link PressureStroke}'s {@link PaintLoad} path: a round
     * stamp every .4 of its radius, its paint spent over the default load's length), drawn without Android's
     * Bitmap: the stamp here is an anti-aliased disc, which a round head draws.
     */
    static final class OilBrush implements DrawingStroke {
        final ToneDocument d; final float r; final PaintLoad load; final int side; final int[] mask;
        float px, py; boolean started;
        OilBrush(ToneDocument d, double size, int gray) {
            this.d = d; r = (float) size / 2;
            load = new PaintLoad(d, gray, ToolSettings.defaults(ToolSettings.Tool.BRUSH).oilPaint(true).paintLength());
            side = (int) Math.ceil(2 * r) + 4; mask = new int[side * side];
            d.begin();
        }
        @Override public void sample(float x, float y, float pressure, float tx, float ty) {
            if (!started) { px = x; py = y; started = true; dab(x, y); return; }
            float dist = (float) Math.hypot(x - px, y - py), spacing = Math.max(.5f, r * .4f);
            int steps = Math.max(1, (int) Math.ceil(dist / spacing));
            for (int i = 1; i <= steps; i++) { float t = (float) i / steps; load.travel(dist / steps); dab(px + (x - px) * t, py + (y - py) * t); }
            px = x; py = y;
        }
        void dab(float cx, float cy) {
            int left = (int) Math.floor(cx - r) - 2, top = (int) Math.floor(cy - r) - 2;
            for (int j = 0; j < side; j++) for (int i = 0; i < side; i++) {
                double dx = left + i + .5 - cx, dy = top + j + .5 - cy, e = StrokeMath.clamp(r + .5 - Math.hypot(dx, dy));
                mask[j * side + i] = (int) Math.round(255 * e) << 24;
            }
            int l = Math.max(0, left), t = Math.max(0, top), rr = Math.min(d.width, left + side), b = Math.min(d.height, top + side);
            if (rr <= l || b <= t) return;
            int w = rr - l, h = b - t;
            int[] sub = new int[w * h];
            for (int j = 0; j < h; j++) System.arraycopy(mask, (t - top + j) * side + (l - left), sub, j * w, w);
            load.dab(sub, w, l, t, w, h, false);
        }
        @Override public boolean finish() { return d.finish(); }
    }
}
