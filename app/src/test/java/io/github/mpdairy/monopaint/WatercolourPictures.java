package io.github.mpdairy.monopaint;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Wet watercolour's comparison pictures (the wet paint spec, section 9): each Watercolor preset retired by the wet ones
 * (top row) beside the wet one of the same name (bottom row), drawn by the app's engine as the drawing screen draws them
 * (a session, its slices run to the end): one stroke; two overlapping strokes, the second at once (wet into wet); a
 * second stroke into the first once it has nearly dried (blooms); one stroke on a tilted page. Composites, panels side
 * by side, one PNG a preset and all.png. scripts/watercolour_pictures.sh. With a style named after the folder (the ink
 * wash note, 2026-10-22: "Ink wash"), that style's retired presets beside their wet ones instead, five panels each: one
 * stroke, two crossing strokes at once, about .5 s and about 2 s apart, and one stroke tilted ({@link #crossings}).
 */
public class WatercolourPictures {
    static final int W = 360, H = 240, GAP = 8;
    static final double SIZE = 60, SHADE = .7;
    /** The size drawn at: {@link #SIZE}, or with a style ({@link #crossings}) each brush's own Medium size (Ink wash's ink 8 px, its wash 90). */
    static double size = SIZE;

    public static void main(String[] a) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        File out = new File(a.length > 0 ? a[0] : "/tmp/watercolour-pictures");
        out.mkdirs();
        if (a.length > 1 && "mix".equals(a[1])) { mix(out); return; }
        if (a.length > 2) at = Double.parseDouble(a[2]);
        if (a.length > 1 && !a[1].isEmpty() && !"Watercolor".equals(a[1])) { crossings(out, a[1]); return; }
        List<int[][][]> sheet = new ArrayList<>();
        for (ToolPresets.Preset old : ToolPresets.retired()) {
            if (!"Watercolor".equals(old.style.name)) continue;
            ToolPresets.Preset now = null;
            for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(old.name) && "Watercolor".equals(p.style.name)) now = p;
            if (now == null) continue; // Drips, retired without a wet one
            int[][][] top = row(old.brush().parts), bottom = row(now.brush().parts);
            save(new File(out, old.name.toLowerCase().replace(' ', '-') + ".png"), top, bottom);
            sheet.add(top); sheet.add(bottom);
            System.out.println(old.name);
        }
        save(new File(out, "all.png"), sheet.toArray(new int[0][][][]));
        System.out.println("wrote " + out);
    }

    /**
     * Wet into wet (section 9, the owner's 2026-10-22 note): for each wet watercolour, two crossing strokes, the second
     * at once, about .5 s and about 2 s after the first (10 and 40 of the canvas's 50 ms flow frames), and a stroke
     * painted back over its own start. One PNG a preset and mix.png.
     */
    static void mix(File out) throws Exception {
        List<int[][][]> sheet = new ArrayList<>();
        for (ToolPresets.Preset p : ToolPresets.all()) {
            if (!"Watercolor".equals(p.style.name) || p.brush().parts.choice(BrushPart.LIFE) != PartChoice.WET_PAINT) continue;
            ModularBrush b = p.brush().parts;
            int[][][] r = {cross(b, 0), cross(b, 10), cross(b, 40), backOver(b)};
            save(new File(out, "mix-" + p.name.toLowerCase().replace(' ', '-') + ".png"), r);
            sheet.add(r);
            System.out.println(p.name);
        }
        save(new File(out, "mix.png"), sheet.toArray(new int[0][][][]));
        System.out.println("wrote " + out);
    }
    /** {@code style}'s retired presets (top) beside the wet ones of their names (bottom): one stroke, crossing 0, .5 and 2 s apart, tilted. */
    static void crossings(File out, String style) throws Exception {
        List<int[][][]> sheet = new ArrayList<>();
        for (ToolPresets.Preset old : ToolPresets.retired()) {
            if (!style.equals(old.style.name)) continue;
            ToolPresets.Preset now = null;
            for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(old.name) && style.equals(p.style.name)) now = p;
            if (now == null) continue;
            int[][][] top = crossRow(old.brush().parts), bottom = crossRow(now.brush().parts);
            save(new File(out, old.name.toLowerCase().replace(' ', '-') + ".png"), top, bottom);
            sheet.add(top); sheet.add(bottom);
            System.out.println(old.name);
        }
        save(new File(out, "all.png"), sheet.toArray(new int[0][][][]));
        System.out.println("wrote " + out);
    }
    /** The size {@link #crossings} draws at, px (a third argument); 0 for each brush's own Medium size. */
    static double at;
    static int[][][] crossRow(ModularBrush b) { size = at > 0 ? at : ModularSizes.size(b, InkWashSizes.Preset.MEDIUM); return new int[][][]{single(b, false), cross(b, 0), cross(b, 10), cross(b, 40), single(b, true)}; }
    static int[][] cross(ModularBrush b, int frames) {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, b, size, SHADE, wave(40, 120, 320, 30));
        settle(s, d, frames);
        stroke(d, s, b, size, SHADE, line(new double[]{120, 20, 240, 220}));
        settle(s, d, Integer.MAX_VALUE);
        return grab(d);
    }
    /** One stroke right, then back left over where it began. */
    static int[][] backOver(ModularBrush b) {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, b, size, SHADE, line(new double[]{50, 110, 300, 110, 300, 135, 90, 135}));
        settle(s, d, Integer.MAX_VALUE);
        return grab(d);
    }
    /** Straight runs through the points {@code xy}, light-firm-light, a sample every 2.5 px, 10 ms apart. */
    static double[][] line(double[] xy) {
        List<double[]> o = new ArrayList<>();
        double total = 0;
        for (int k = 2; k < xy.length; k += 2) total += Math.hypot(xy[k] - xy[k - 2], xy[k + 1] - xy[k - 1]);
        double done = 0;
        for (int k = 2; k < xy.length; k += 2) {
            double len = Math.hypot(xy[k] - xy[k - 2], xy[k + 1] - xy[k - 1]);
            int n = (int) Math.ceil(len / 2.5);
            for (int i = k == 2 ? 0 : 1; i <= n; i++) {
                double u = (double) i / n, at = (done + len * u) / total;
                o.add(new double[]{xy[k - 2] + (xy[k] - xy[k - 2]) * u, xy[k - 1] + (xy[k + 1] - xy[k - 1]) * u, .35 + .5 * Math.sin(Math.PI * at), o.size() * 10});
            }
            done += len;
        }
        return o.toArray(new double[0][]);
    }
    /** The four panels for {@code b}. */
    static int[][][] row(ModularBrush b) {
        return new int[][][]{single(b, false), twoWet(b), bloom(b), single(b, true)};
    }
    static int[][] single(ModularBrush b, boolean tilted) {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        if (tilted) s.tilt = () -> PageTilt.of(0, .5);
        stroke(d, s, b, size, SHADE, wave(40, 100, 320, 40));
        settle(s, d, Integer.MAX_VALUE);
        return grab(d);
    }
    static int[][] twoWet(ModularBrush b) {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, b, size, SHADE, wave(40, 95, 320, 30));
        stroke(d, s, b, size, SHADE, wave(40, 145, 320, -30));
        settle(s, d, Integer.MAX_VALUE);
        return grab(d);
    }
    /** The first stroke left to nearly dry (most of the slices a stroke alone takes), then a second into its edge. */
    static int[][] bloom(ModularBrush b) {
        ToneDocument probe = InkWashLayers.blank(W, H);
        WatercolorSession p = new WatercolorSession();
        stroke(probe, p, b, size, SHADE, wave(40, 95, 320, 30));
        int n = settle(p, probe, Integer.MAX_VALUE);
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, b, size, SHADE, wave(40, 95, 320, 30));
        settle(s, d, (int) Math.round(n * .85));
        stroke(d, s, b, size * .8, SHADE * .8, wave(60, 140, 300, -20));
        settle(s, d, Integer.MAX_VALUE);
        return grab(d);
    }
    /** A stroke as the drawing screen draws it ({@link WetPaintChecks#start}), presented at every sample. */
    static void stroke(ToneDocument d, WatercolorSession s, ModularBrush b, double size, double darkness, double[][] path) {
        DrawingStroke st = WetPaintChecks.start(d, s, b, size, darkness);
        for (double[] p : path) { st.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), 0, 0, (long) p[3]); st.present(); }
        st.finish();
    }
    static int settle(WatercolorSession s, ToneDocument d, int most) { int n = 0; while (n < most && n < 5000 && s.tickDrying(d)) n++; return n; }
    /** A wave from {@code x0} to {@code x1} about {@code y}, {@code amp} high, light-firm-light, a sample every 2.5 px, 10 ms apart (about a second a stroke, as a hand paints). */
    static double[][] wave(double x0, double y, double x1, double amp) {
        int n = (int) Math.ceil((x1 - x0) / 2.5);
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            double u = (double) i / n;
            o[i] = new double[]{x0 + (x1 - x0) * u, y + amp * Math.sin(2 * Math.PI * u) * .5, .35 + .5 * Math.sin(Math.PI * u), i * 10};
        }
        return o;
    }
    static int[][] grab(ToneDocument d) { int[][] g = new int[d.height][d.width]; for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) g[y][x] = d.compositeTone(x, y); return g; }
    /** Rows of panels, grey between. */
    static void save(File f, int[][][]... rows) throws Exception {
        int cols = rows[0].length;
        BufferedImage img = new BufferedImage(cols * W + (cols - 1) * GAP, rows.length * H + (rows.length - 1) * GAP, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) img.getRaster().setSample(x, y, 0, 100);
        for (int r = 0; r < rows.length; r++) for (int k = 0; k < cols; k++) for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
            img.getRaster().setSample(k * (W + GAP) + x, r * (H + GAP) + y, 0, rows[r][k][y][x]);
        javax.imageio.ImageIO.write(img, "png", f);
    }
}
