package io.github.mpdairy.monopaint;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Before and after, for looking (not a check): {@code cells DIR} draws, with the app's engine as the drawing screen
 * runs it ({@link QuickStroke}), each Print preset's pressure S-curve and a 300 px square filled by back-and-forth
 * scribbles at medium pressure, and Hand Hatch's held dabs, slow drag and filled square, one PNG a cell, printing each
 * square's {@link #evenness}; {@code sheet BEFORE AFTER OUT} lays two such folders side by side. Built against an older
 * engine too (it uses nothing newer), so the before is the older engine's own drawing.
 */
public final class SprayHatchPictures {
    static final int W = 560, H = 360, SQ = 300, SX = 30, SY = 30;

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (args[0].equals("cells")) cells(new File(args[1]));
        else sheet(new File(args[1]), new File(args[2]), new File(args[3]));
    }

    static final String[] PRINT = {"Dot tone", "Line tone", "Sand tone", "Gradient tone", "Hatched tone", "Ben-Day brush", "Rosette ink",
            "Halftone brush", "Mezzotint", "Dither pencil", "Engraving pen", "Wavy-line pen"};

    static void cells(File dir) throws Exception {
        dir.mkdirs();
        for (String name : PRINT) {
            ModularBrush b = preset(name);
            double size = ModularSizes.size(b, InkWashSizes.Preset.MEDIUM);
            ToneDocument s = InkWashLayers.blank(W, H);
            stroke(s, b, size, 1, sCurve());
            save(s, new File(dir, name + ".s.png"));
            ToneDocument q = InkWashLayers.blank(W, H);
            stroke(q, b, size, 1, square(.5, size));
            save(q, new File(dir, name + ".q.png"));
            System.out.printf("%-15s size %3.0f  square: %s%n", name, size, evenness(q));
        }
        ModularBrush hh = handHatch();
        double size = 60;
        ToneDocument dabs = InkWashLayers.blank(W, H);
        int[] ms = {150, 400, 800, 1500, 3000};
        for (int i = 0; i < ms.length; i++) { double[][] p = dab(60 + i * 110, 110, .45, ms[i]); stroke(dabs, hh, size, 1, p); }
        for (int i = 0; i < 3; i++) stroke(dabs, hh, size, 1, dab(110 + i * 170, 270, .3 + .3 * i, 1000));
        save(dabs, new File(dir, "hh.dabs.png"));
        ToneDocument drag = InkWashLayers.blank(W, H);
        stroke(drag, hh, size, 1, drag(90, .35, 1.5));
        stroke(drag, hh, size, 1, drag(180, .6, 4));
        stroke(drag, hh, size, 1, drag(270, .9, 10));
        save(drag, new File(dir, "hh.drag.png"));
        ToneDocument sq = InkWashLayers.blank(W, H);
        stroke(sq, hh, size, 1, square(.5, size));
        save(sq, new File(dir, "hh.q.png"));
        System.out.printf("%-15s square: %s%n", "Hand hatch", evenness(sq));
    }

    /** StippleChecks.mk's Hand Hatch: a Fineliner ink, seed 17, Steady. */
    static ModularBrush handHatch() {
        ModularBrush b = new ModularBrush("g", 17, ModularStyle.named("Fineliner"));
        b.set(BrushPart.TIP, new ModularBrush.Part(PartChoice.HAND_HATCH)); b.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.INK)); b.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.OFF));
        b.set(BrushPart.PAPER, new ModularBrush.Part(PartChoice.PLAIN)); b.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.FAST_WET)); b.set(BrushPart.HAND, new ModularBrush.Part(PartChoice.STEADY));
        return b.normalize();
    }
    static ModularBrush preset(String name) {
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(name)) return p.brush().parts;
        throw new AssertionError(name);
    }

    /** A stroke as the drawing screen runs one: quick or live, a present every sample. */
    static void stroke(ToneDocument d, ModularBrush b, double size, double darkness, double[][] path) {
        DrawingStroke s = QuickStroke.start(d, b, size, darkness, null);
        for (double[] p : path) { s.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), 0, 0, (long) p[3]); s.present(); }
        s.finish();
    }

    /** An S across, pressure light at the ends, full in the middle, about 8 ms a sample. */
    static double[][] sCurve() {
        int n = 240;
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            double u = (double) i / n;
            o[i] = new double[]{40 + 480 * u, H / 2.0 - 110 * Math.sin(u * Math.PI * 2), .05 + .95 * Math.sin(Math.PI * u), 1000 + 8 * i};
        }
        return o;
    }
    /** Back-and-forth scribbles filling the square, rows a third of the tip apart (at least 8 px), wobbling a little. */
    static double[][] square(double p, double size) {
        List<double[]> o = new ArrayList<>();
        double gap = Math.max(8, size / 3), t = 1000;
        boolean right = true;
        java.util.Random rnd = new java.util.Random(3);
        for (double y = SY + size * .3; y <= SY + SQ - size * .3 + .1; y += gap) {
            double wob = rnd.nextDouble() * 4 - 2;
            for (double x = 0; x <= SQ - size * .6; x += 3) {
                double xx = SX + size * .3 + (right ? x : SQ - size * .6 - x);
                o.add(new double[]{xx, y + wob + 1.5 * Math.sin(xx * .05), p, t}); t += 6;
            }
            right = !right;
        }
        return o.toArray(new double[0][]);
    }
    /** A pen set down at {@code x, y} and held {@code ms} still. */
    static double[][] dab(double x, double y, double p, int ms) {
        List<double[]> o = new ArrayList<>();
        for (int t = 0; t <= ms; t += 8) o.add(new double[]{x + (t == 0 ? 0 : .3 * Math.sin(t * .01)), y, p, 1000 + t});
        return o.toArray(new double[0][]);
    }
    /** A slow drag across at {@code pxPerMs}... given as px per 8 ms sample. */
    static double[][] drag(double y, double p, double step) {
        List<double[]> o = new ArrayList<>();
        int i = 0;
        for (double x = 40; x <= 520; x += step / 4, i++) o.add(new double[]{x, y + 6 * Math.sin(x * .02), p, 1000 + 8 * i});
        return o.toArray(new double[0][]);
    }

    /**
     * How evenly the square fills: the share of inked pixels (tone under 128) in each 30 px block of its inner 240 px,
     * as "mean, min–max, spread (stdev / mean), edge" where edge is the share of the outer 10 px band inked relative to
     * the middle's.
     */
    static String evenness(ToneDocument d) {
        double[] f = blocks(d);
        double mean = 0, lo = 1, hi = 0;
        for (double v : f) { mean += v; lo = Math.min(lo, v); hi = Math.max(hi, v); }
        mean /= f.length;
        double var = 0;
        for (double v : f) var += (v - mean) * (v - mean);
        double sd = Math.sqrt(var / f.length);
        return String.format("mean %.2f  min %.2f  max %.2f  spread %.2f", mean, lo, hi, mean > 0 ? sd / mean : 9);
    }
    /** The inked share of each 30 px block of the square's inner 240 px (8 × 8). */
    static double[] blocks(ToneDocument d) {
        double[] f = new double[64];
        for (int by = 0; by < 8; by++) for (int bx = 0; bx < 8; bx++) {
            int n = 0;
            for (int y = 0; y < 30; y++) for (int x = 0; x < 30; x++) if (d.compositeTone(SX + 30 + bx * 30 + x, SY + 30 + by * 30 + y) < 128) n++;
            f[by * 8 + bx] = n / 900.0;
        }
        return f;
    }

    static void save(ToneDocument d, File f) throws Exception {
        BufferedImage img = new BufferedImage(d.width, d.height, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) { int t = d.compositeTone(x, y); img.setRGB(x, y, 0xff000000 | t << 16 | t << 8 | t); }
        javax.imageio.ImageIO.write(img, "png", f);
    }

    static void sheet(File before, File after, File out) throws Exception {
        List<String[]> rows = new ArrayList<>();
        for (String n : PRINT) rows.add(new String[]{n, n + ".s.png", n + ".q.png"});
        rows.add(new String[]{"Hand hatch BEFORE", "hh.dabs.png", "hh.drag.png", "hh.q.png", "b"});
        rows.add(new String[]{"Hand hatch AFTER", "hh.dabs.png", "hh.drag.png", "hh.q.png", "a"});
        int label = 150, gap = 10, cw = W / 2, ch = H / 2;
        int w = label + 4 * (cw + gap) + gap, h = 40 + rows.size() * (ch + gap) + 30;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.BLACK); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        String[] heads = {"S-curve BEFORE", "S-curve AFTER", "Square BEFORE", "Square AFTER"};
        for (int c = 0; c < 4; c++) g.drawString(heads[c], label + c * (cw + gap) + 8, 26);
        for (int r = 0; r < rows.size(); r++) {
            String[] row = rows.get(r);
            int y = 40 + r * (ch + gap) + (row.length == 5 ? 30 : 0);
            if (row.length == 5 && row[4].equals("b")) {
                g.setColor(Color.BLACK); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
                String[] hh = {"Held dabs .15-3 s", "Slow drags", "Scribbled square"};
                for (int c = 0; c < 3; c++) g.drawString(hh[c], label + c * (cw + gap) + 8, y - 8);
            }
            g.setColor(Color.BLACK); g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            g.drawString(row[0], 8, y + 20);
            File dir = row.length == 5 && row[4].equals("b") ? before : after;
            File[] cells = row.length == 3
                    ? new File[]{new File(before, row[1]), new File(after, row[1]), new File(before, row[2]), new File(after, row[2])}
                    : new File[]{new File(dir, row[1]), new File(dir, row[2]), new File(dir, row[3])};
            for (int c = 0; c < cells.length; c++) {
                int x = label + c * (cw + gap);
                if (cells[c].exists()) g.drawImage(javax.imageio.ImageIO.read(cells[c]), x, y, cw, ch, null);
                g.setColor(new Color(190, 190, 190)); g.drawRect(x, y, cw - 1, ch - 1);
            }
        }
        g.dispose();
        out.getParentFile().mkdirs();
        javax.imageio.ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }
}
