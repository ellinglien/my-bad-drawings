package io.github.mpdairy.monopaint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A cover's printed sheet at a size ({@link CoverDesign}; the approved prototype's {@code pattern}, {@code tape} and
 * {@code half}, docs/archive/brush-lab/prototypes/cover-designs.html): the design's field sampled on a lattice, each pixel blended
 * between its nearest four samples and cut at 0, the edge spread over one pixel by the field's slope there
 * ({@code paper = clamp(.5 + f / |∇f|)}), so edges are crisp and smooth at any size and a thread down to about 3 px
 * survives; Swirl's tones blended and steepened instead. Then the cloth tape down the spine ({@link #SPINE} of the width:
 * the left of the front, the right of the back, a faint twill at full size), and every pixel to the panel's greys.
 *
 * <p>A full-size cover samples every 2 px (Swirl every 3); a small one (a shelf tile, under {@link #SMALL} px wide) is
 * made at twice its size, every pixel, and averaged down, as the prototype makes its shelf. The lattice's rows are shared
 * over the cores ({@link Cores}). That takes seconds on the Manta, so the app's covers are windows of the sheets made
 * this way once, ahead ({@link CoverTexture}, scripts/cover_textures.sh), the generators only their makers (and the
 * fallback for a design with none). Made once a design, name, size and side, then kept in memory, and made ahead in the
 * background ({@link #prepare}). Not kept in the app's cache folder as they were: a window of a sheet is made two or
 * three times faster than such a file is read back (desktop: a Manta cover 2 ms, against 6 to read; a shelf tile 0.4 ms
 * against 1), and never written. Pure, checked and timed on the desktop ({@code CoverDesignChecks}).
 */
final class CoverPattern {
    /** Changes whenever the pattern a design and name give changes (the covers' PNG copies are drawn again, {@link NotebookCovers#key}). */
    static final int VERSION = 3;
    /** The tape's width, a share of the cover's; its twill's grey (the panel's darkest grey but black). */
    static final double SPINE = .095, TWILL = .32;
    /** A field's back: the same printed sheet this many cover widths on, as a real book's cover paper wraps round. */
    static final double BACK_SHIFT = 1.1;
    /** Under this width a cover is small (a shelf tile): made at twice its size and averaged down, no twill. */
    static final int SMALL = 1000;
    /** Each tone's nearest of the panel's greys. */
    static final int[] LEVELS = new int[256];
    static {
        for (int t = 0; t < 256; t++) {
            int best = 0;
            for (int g : GrayPalette.VALUES) if (Math.abs(g - t) < Math.abs(best - t)) best = g;
            LEVELS[t] = best;
        }
    }

    /** The tape's width on a cover {@code width} px wide. */
    static int spine(int width) { return (int) Math.round(width * SPINE); }

    /** How many sheets {@link #tones} has made (the checks': a shelf shown again makes none). */
    static final java.util.concurrent.atomic.AtomicInteger MAKES = new java.util.concurrent.atomic.AtomicInteger();
    /**
     * {@code design}'s sheet for {@code name}, {@code width × height} px, the front's or the {@code back}'s, a tone a pixel
     * row by row, the tape on it, at the panel's greys: a window of the design's texture ({@link CoverTexture}), or, if
     * the app has none for it, made by its generator.
     */
    static byte[] tones(CoverDesign design, String name, int width, int height, boolean back) {
        MAKES.incrementAndGet();
        CoverTexture sheet = CoverTexture.of(design);
        byte[] out = sheet != null ? sheet.tones(sheet.window(name, width, height, back), width, height, LEVELS) : generated(design, name, width, height, back);
        tape(out, width, height, back, width >= SMALL);
        return out;
    }
    /**
     * {@link #tones} from the design's generator itself, without the tape: its sheet when the app's isn't there
     * ({@link CoverTexture#of}), and what the sheets are made from.
     */
    static byte[] generated(CoverDesign design, String name, int width, int height, boolean back) {
        float[] shares = width < SMALL ? half(shares(design, name, 2 * width, 2 * height, back), 2 * width, 2 * height) : shares(design, name, width, height, back);
        byte[] out = new byte[shares.length];
        for (int i = 0; i < out.length; i++) out[i] = (byte) LEVELS[(int) Math.round(shares[i] * 255)];
        return out;
    }

    /** The paper's share in each pixel (0 ink, 1 paper) of {@code design}'s sheet {@code w × h} px: the prototype's {@code pattern}. */
    static float[] shares(CoverDesign design, String name, int w, int h, boolean back) {
        return shares(design, name, w, h, back, (w >= SMALL ? 2 : 1) + (design.tone() ? 1 : 0));
    }
    /** As {@link #shares(CoverDesign, String, int, int, boolean)}, sampled every {@code step} px. */
    static float[] shares(CoverDesign design, String name, int w, int h, boolean back, int step) {
        CoverDesign.Field field = design.field(name, h / (double) w, back);
        boolean tone = design.tone();
        int lw = (w + step - 1) / step + 2, lh = (h + step - 1) / step + 2;
        float[] lattice = new float[lw * lh];
        double shift = back && !tone ? BACK_SHIFT : 0, unit = 1.0 / w;
        double[][] scratch = new double[Cores.WORKERS][3];
        int band = 8, bands = (lh + band - 1) / band;
        Cores.each(bands, (piece, worker) -> {
            double[] s = scratch[worker];
            for (int j = piece * band; j < Math.min(lh, piece * band + band); j++) {
                double v = j * step * unit;
                for (int i = 0; i < lw; i++) lattice[j * lw + i] = (float) field.at(i * step * unit + shift, v, s);
            }
        });
        float[] out = new float[w * h];
        double sharpen = 1.25 * step;
        Cores.each((h + 63) / 64, (piece, worker) -> {
            for (int y = piece * 64; y < Math.min(h, piece * 64 + 64); y++) {
                double fy = (y + .5) / step;
                int iy = Math.min(lh - 2, (int) Math.floor(fy));
                double ty = fy - iy;
                for (int x = 0; x < w; x++) {
                    double fx = (x + .5) / step;
                    int ix = Math.min(lw - 2, (int) Math.floor(fx));
                    double tx = fx - ix;
                    int o = iy * lw + ix;
                    double a = lattice[o], b = lattice[o + 1], c = lattice[o + lw], d = lattice[o + lw + 1], p;
                    if (tone) {
                        if (a == b && a == c && a == d) p = a;
                        else { double t = (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty; p = .5 + (t - .5) * sharpen; }
                    } else {
                        double top = a + (b - a) * tx, bottom = c + (d - c) * tx, f = top + (bottom - top) * ty;
                        double gx = ((b - a) * (1 - ty) + (d - c) * ty) / step, gy = (bottom - top) / step;
                        double g = Math.sqrt(gx * gx + gy * gy) + 1e-9;
                        p = .5 + f / g;
                    }
                    out[y * w + x] = (float) (p < 0 ? 0 : p > 1 ? 1 : p);
                }
            }
        });
        return out;
    }

    /** {@code shares} at half size: each pixel its four's average. */
    static float[] half(float[] shares, int w, int h) {
        int hw = w >> 1, hh = h >> 1;
        float[] out = new float[hw * hh];
        for (int y = 0; y < hh; y++)
            for (int x = 0; x < hw; x++) {
                int o = 2 * y * w + 2 * x;
                out[y * hw + x] = (float) (((double) shares[o] + shares[o + 1] + shares[o + w] + shares[o + w + 1]) / 4);
            }
        return out;
    }

    /**
     * The cloth spine: ink, {@link #SPINE} of the width, on the left of the front and the right of the {@code back}. With
     * {@code weave} (full size) a faint twill: one pixel in eight a dark grey, on a diagonal, in a basket-weave check of 4 px.
     */
    static void tape(byte[] cover, int w, int h, boolean back, boolean weave) {
        int sw = spine(w), x0 = back ? w - sw : 0;
        byte twill = (byte) LEVELS[(int) Math.round(TWILL * 255)];
        for (int y = 0; y < h; y++)
            for (int x = x0; x < x0 + sw; x++)
                cover[y * w + x] = weave && ((x + 2 * y) & 7) == 0 && ((y >> 2) & 1) == (((x >> 2) + 1) & 1) ? twill : 0;
    }

    // Made once a design, name, size and side

    /** Recently made sheets, by what they are: a cover's paper and a few shelf tiles. */
    private static final Map<String, byte[]> MADE = new LinkedHashMap<>(16, .75f, true);
    private static long bytes;
    private static final long LIMIT = 24L << 20;
    /** Sheets being made, by the same keys: a second asker waits for the first instead of making it again. */
    private static final Map<String, java.util.concurrent.FutureTask<byte[]>> MAKING = new java.util.HashMap<>();
    /** {@link #tones}, made once and kept while room allows; don't change what it gives. */
    static byte[] cached(CoverDesign design, String name, int width, int height, boolean back) {
        String key = design.key + (back ? " back " : " front ") + width + "x" + height + ":" + name;
        java.util.concurrent.FutureTask<byte[]> task;
        boolean mine = false;
        synchronized (MADE) {
            byte[] made = MADE.get(key);
            if (made != null) return made;
            task = MAKING.get(key);
            if (task == null) { task = new java.util.concurrent.FutureTask<>(() -> tones(design, name, width, height, back)); MAKING.put(key, task); mine = true; }
        }
        if (mine) task.run();
        byte[] made;
        try { made = task.get(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); made = tones(design, name, width, height, back); }
        catch (java.util.concurrent.ExecutionException e) { throw Cores.rethrow(e.getCause()); }
        finally { if (mine) synchronized (MADE) { MAKING.remove(key); } }
        synchronized (MADE) {
            if (!MADE.containsKey(key)) { MADE.put(key, made); bytes += made.length; }
            java.util.Iterator<Map.Entry<String, byte[]>> oldest = MADE.entrySet().iterator();
            while (bytes > LIMIT && MADE.size() > 1 && oldest.hasNext()) {
                Map.Entry<String, byte[]> e = oldest.next();
                if (e.getKey().equals(key)) continue;
                bytes -= e.getValue().length; oldest.remove();
            }
        }
        return made;
    }
    /** Lets go of the sheets held in memory: on the way back to drawing, and the checks', to make them again. */
    static void forget() { synchronized (MADE) { MADE.clear(); bytes = 0; } }
    private static final java.util.concurrent.ExecutorService AHEAD = java.util.concurrent.Executors.newSingleThreadExecutor(work -> {
        Thread thread = new Thread(() -> { Cores.quiet(true); work.run(); }, "cover pattern"); thread.setDaemon(true); thread.setPriority(Thread.MIN_PRIORITY); return thread;
    });
    /**
     * {@link #cached} in the background, the front's and the back's at a page's size, so turning to a cover needn't wait;
     * then {@code then} (null: nothing), on that thread.
     */
    static void prepare(CoverDesign design, String name, int width, int height, Runnable then) {
        AHEAD.execute(() -> {
            try { cached(design, name, width, height, false); cached(design, name, width, height, true); } catch (RuntimeException | OutOfMemoryError ignored) { }
            if (then != null) then.run();
        });
    }

    private CoverPattern() {}
}
