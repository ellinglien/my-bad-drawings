package io.github.mpdairy.monopaint;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A Stamp tip's nib (mischief-lab-2.html's Stamp; {@link StampStamper}): a piece of the page cut with a lasso, the ink
 * inside it trimmed to what's there, kept as a small grey bitmap of coverage (0 none to 255 full), its longer side at
 * most {@link #MOST} px. The stamp and the nib are the same thing: a brush whose nib is a potato stamp.
 *
 * <p>Brush codes are text, so the nib is kept beside them: in a store (the library's, {@link BrushLibrary.Store}) under
 * its {@link #id}, a hash of its pixels, which the brush's code carries ({@link ModularBrush#nib}). A code whose nib the
 * store doesn't hold (shared from another tablet, or never cut) prints a round stamp ({@link #ROUND}). Pure, checked on
 * the desktop ({@code StampChecks}).
 */
final class StampNib {
    /** The longest side a nib is kept at, px: a bigger cut is scaled down (a print is at most {@link ModularSizes#MAX}). */
    static final int MOST = 256;
    /** A pixel with less coverage than this is no ink: the cut is trimmed to the pixels past it. */
    static final double INK = .05;
    /** The store's key for a nib: this, then its id in base 36. */
    static final String KEY = "stamp_nib_";
    /**
     * Cut Stamp's words (docs/TEXT-STYLE.md): its button, its hint on the drawing screen, and what an empty lasso says
     * (mischief-lab-2.html's, for 1.2 s).
     */
    static final String CUT = "Cut Stamp", LASSO = "Draw round the shape to cut", NOTHING = "Nothing to cut", DONE = "Stamp cut";
    static final long NOTHING_MS = 1200, LASSO_MS = 2500;
    /** What a code without its nib prints: a round stamp, 48 px across, its edge antialiased. */
    static final StampNib ROUND = round(48);

    final int width, height;
    /** Coverage, row by row, 0 to 255 (unsigned). */
    private final byte[] a;
    /** A hash of the nib's size and pixels, never 0 (0 is a code with no nib). */
    final int id;

    StampNib(int width, int height, byte[] a) {
        if (width < 1 || height < 1 || a.length != width * height) throw new IllegalArgumentException("a nib of " + width + " × " + height);
        this.width = width; this.height = height; this.a = a;
        int h = 0x811c9dc5;
        h = (h ^ width) * 16777619; h = (h ^ height) * 16777619;
        for (byte b : a) h = (h ^ (b & 0xff)) * 16777619;
        id = h == 0 ? 1 : h;
    }

    /** The coverage at pixel {@code x, y}, 0 to 1; 0 outside. */
    double at(int x, int y) { return x < 0 || y < 0 || x >= width || y >= height ? 0 : (a[y * width + x] & 0xff) / 255.0; }
    /** mischief-lab-2.html sample2: the coverage at the nib's own {@code u, v} (pixel centres at whole numbers), bilinear, nothing outside it. */
    double sample(double u, double v) {
        int x = (int) Math.floor(u), y = (int) Math.floor(v);
        double fx = u - x, fy = v - y;
        return (at(x, y) * (1 - fx) + at(x + 1, y) * fx) * (1 - fy) + (at(x, y + 1) * (1 - fx) + at(x + 1, y + 1) * fx) * fy;
    }
    /** Its longer side, px. */
    int longer() { return Math.max(width, height); }

    /** A round nib {@code d} px across. */
    static StampNib round(int d) {
        byte[] a = new byte[d * d];
        double r = d / 2.0 - .5;
        for (int y = 0; y < d; y++) for (int x = 0; x < d; x++)
            a[y * d + x] = (byte) Math.round(255 * StrokeMath.clamp(r + .5 - Math.hypot(x + .5 - d / 2.0, y + .5 - d / 2.0)));
        return new StampNib(d, d, a);
    }

    /**
     * mischief-lab-2.html cut2: the ink inside the lasso {@code xs, ys} (its first {@code n} points, page px, closed back
     * to its start; even-odd) on the {@code width × height} page as {@code page} shows it over paper {@code paper}, trimmed
     * to the pixels with ink ({@link #INK}), scaled down to {@link #MOST} if bigger. Null when there's nothing to cut: a
     * lasso of fewer than three points, or no ink inside it.
     */
    static StampNib cut(Mirrors.Tones page, int paper, float[] xs, float[] ys, int n, int width, int height) {
        if (n < 3) return null;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) { minX = Math.min(minX, xs[i]); maxX = Math.max(maxX, xs[i]); minY = Math.min(minY, ys[i]); maxY = Math.max(maxY, ys[i]); }
        int x0 = Math.max(0, (int) Math.floor(minX)), x1 = Math.min(width - 1, (int) Math.ceil(maxX));
        int y0 = Math.max(0, (int) Math.floor(minY)), y1 = Math.min(height - 1, (int) Math.ceil(maxY));
        if (x1 < x0 || y1 < y0) return null;
        int bw = x1 - x0 + 1, bh = y1 - y0 + 1;
        float[] ink = new float[bw * bh];
        double[] cross = new double[n];
        double white = Math.max(1, paper);
        int tx0 = Integer.MAX_VALUE, ty0 = Integer.MAX_VALUE, tx1 = -1, ty1 = -1;
        for (int y = y0; y <= y1; y++) {
            // The lasso's crossings of this row's pixel centres, left to right: inside between each pair (even-odd).
            double cy = y + .5;
            int c = 0;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                double ya = ys[j], yb = ys[i];
                if ((ya > cy) != (yb > cy)) cross[c++] = xs[j] + (cy - ya) / (yb - ya) * (xs[i] - xs[j]);
            }
            Arrays.sort(cross, 0, c);
            for (int k = 0; k + 1 < c; k += 2) {
                int from = Math.max(x0, (int) Math.ceil(cross[k] - .5)), to = Math.min(x1, (int) Math.floor(cross[k + 1] - .5));
                for (int x = from; x <= to; x++) {
                    float v = (float) StrokeMath.clamp((white - page.tone(x, y)) / white);
                    if (!(v > INK)) continue;
                    ink[(y - y0) * bw + x - x0] = v;
                    if (x < tx0) tx0 = x;
                    if (x > tx1) tx1 = x;
                    if (y < ty0) ty0 = y;
                    if (y > ty1) ty1 = y;
                }
            }
        }
        if (tx1 < 0) return null;
        int w = tx1 - tx0 + 1, h = ty1 - ty0 + 1;
        // Scaled down to MOST on its longer side, each kept pixel the mean of the cut's pixels it covers.
        double k = Math.max(1, Math.max(w, h) / (double) MOST);
        int sw = Math.max(1, (int) Math.round(w / k)), sh = Math.max(1, (int) Math.round(h / k));
        byte[] a = new byte[sw * sh];
        for (int y = 0; y < sh; y++) for (int x = 0; x < sw; x++) {
            int fx0 = (int) Math.floor(x * k), fx1 = Math.max(fx0 + 1, Math.min(w, (int) Math.floor((x + 1) * k)));
            int fy0 = (int) Math.floor(y * k), fy1 = Math.max(fy0 + 1, Math.min(h, (int) Math.floor((y + 1) * k)));
            double sum = 0;
            for (int yy = fy0; yy < fy1; yy++) for (int xx = fx0; xx < fx1; xx++) sum += ink[(ty0 - y0 + yy) * bw + tx0 - x0 + xx];
            a[y * sw + x] = (byte) Math.round(255 * sum / ((fx1 - fx0) * (fy1 - fy0)));
        }
        return new StampNib(sw, sh, a);
    }

    /** As the store keeps it: "1," its width, height and pixels deflated, in base 64. */
    String write() {
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        d.setInput(a); d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        while (!d.finished()) out.write(chunk, 0, d.deflate(chunk));
        d.end();
        return "1," + width + "," + height + "," + Base64.getEncoder().encodeToString(out.toByteArray());
    }
    /** A nib {@link #write} wrote; null for anything else. */
    static StampNib read(String text) {
        if (text == null) return null;
        String[] f = text.split(",", 4);
        if (f.length != 4 || !"1".equals(f[0])) return null;
        try {
            int w = Integer.parseInt(f[1]), h = Integer.parseInt(f[2]);
            if (w < 1 || h < 1 || w > MOST || h > MOST) return null;
            Inflater in = new Inflater();
            in.setInput(Base64.getDecoder().decode(f[3]));
            byte[] a = new byte[w * h];
            int got = 0;
            while (got < a.length && !in.finished()) { int k = in.inflate(a, got, a.length - got); if (k == 0 && (in.needsInput() || in.needsDictionary())) break; got += k; }
            in.end();
            return got == a.length ? new StampNib(w, h, a) : null;
        } catch (IllegalArgumentException | DataFormatException e) { return null; }
    }

    // ---- The store ----

    /** Where nibs are kept (the library's store on the device); null: only those cut since the app started. */
    private static BrushLibrary.Store store;
    /** Nibs read or cut, by id. */
    private static final Map<Integer, StampNib> CACHE = new HashMap<>();

    /** Nibs are kept in {@code where} from now on. */
    static synchronized void storeIn(BrushLibrary.Store where) { store = where; }
    /** The store's key for nib {@code id}. */
    static String key(int id) { return KEY + Long.toString(id & 0xffffffffL, 36); }
    /** Keeps {@code nib} (in the store too, if it isn't there yet); returns its id. */
    static synchronized int keep(StampNib nib) {
        CACHE.put(nib.id, nib);
        if (store != null && store.get(key(nib.id)) == null) store.put(key(nib.id), nib.write());
        return nib.id;
    }
    /** Nib {@code id}, or null: 0 (no nib), or one the store doesn't hold. */
    static synchronized StampNib of(int id) {
        if (id == 0) return null;
        StampNib nib = CACHE.get(id);
        if (nib == null && store != null) {
            nib = read(store.get(key(id)));
            // A nib whose pixels don't hash to its key isn't this one.
            if (nib != null && nib.id != id) nib = null;
            if (nib != null) CACHE.put(id, nib);
        }
        return nib;
    }
    /** What a brush with nib {@code id} prints: that nib, or {@link #ROUND} without it. */
    static StampNib orRound(int id) { StampNib nib = of(id); return nib != null ? nib : ROUND; }
    /** Forgets every nib read or cut (the checks: a fresh start). */
    static synchronized void forget() { CACHE.clear(); }
}
