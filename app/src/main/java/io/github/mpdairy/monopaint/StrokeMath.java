package io.github.mpdairy.monopaint;

/**
 * The modular engine's small arithmetic, ported from the prototype (engine.js 8–80 and 479–490, port.js 12–13 and
 * 213–232) with its arithmetic kept, so values match it. A rectangle is {@code int[]{left, top, right, bottom}},
 * inclusive, empty when {@code right < left || bottom < top}. Pure, checked on the desktop.
 */
final class StrokeMath {
    /** How much wetter a line's Fast-wet window reads than its body's (engine.js LINE_WET_K). */
    static final double LINE_WET_K = 1.4;

    static double clamp(double v) { return clamp(v, 0, 1); }
    static double clamp(double v, double lo, double hi) { return v < lo ? lo : v > hi ? hi : v; }
    static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    /** A lerp that lands exactly on its ends, so a dial at 0 or 1 gives its end to the bit. */
    static double mix(double a, double b, double t) { return t <= 0 ? a : t >= 1 ? b : a + (b - a) * t; }
    /** Smoothstep on t clamped to 0–1. */
    static double smooth(double t) { t = clamp(t); return t * t * (3 - 2 * t); }
    static double smoothstep(double e0, double e1, double x) { return smooth((x - e0) / (e1 - e0)); }

    /** A box blur of radius {@code r} over a {@code w × h} buffer, rows then columns, edges clamped; sums in double, stored float. */
    static float[] blur(float[] src, int w, int h, int r) { return blur(src, w, h, r, new float[w * h], new float[w * h]); }
    /**
     * As {@link #blur(float[], int, int, int)}, into {@code out} through {@code tmp}, each at least {@code w × h} and
     * returned as {@code out}. Rows read only {@code src} and columns only {@code tmp}, so {@code out} may be {@code src}
     * (a blur in place); {@code tmp} must be neither. A big one runs its rows, then its columns, in pieces on several
     * cores ({@link Cores}); each row and column is summed as on one, so the floats are the same.
     */
    static float[] blur(float[] src, int w, int h, int r, float[] tmp, float[] out) {
        if ((long)w * h < BLUR_SHARE_PX) { blurRows(src, w, r, tmp, 0, h); blurColumns(tmp, w, h, r, out, 0, w); return out; }
        int rows = Math.min(h, 4 * Cores.WORKERS), cols = Math.min(w, 4 * Cores.WORKERS);
        Cores.each(rows, (k, worker) -> blurRows(src, w, r, tmp, (int)((long)h * k / rows), (int)((long)h * (k + 1) / rows)));
        Cores.each(cols, (k, worker) -> blurColumns(tmp, w, h, r, out, (int)((long)w * k / cols), (int)((long)w * (k + 1) / cols)));
        return out;
    }
    /** A blur bigger than this many values is shared out over the cores. */
    static final int BLUR_SHARE_PX = 32 * 1024;
    /** {@link #blur}'s rows {@code y0..y1 − 1}: {@code src} along each row into {@code tmp}. */
    private static void blurRows(float[] src, int w, int r, float[] tmp, int y0, int y1) {
        int n = 2 * r + 1;
        for (int y = y0; y < y1; y++) {
            double acc = 0; int row = y * w;
            for (int x = -r; x <= r; x++) acc += src[row + Math.max(0, Math.min(w - 1, x))];
            // The ends clamp to the row; between them no index needs it (the same sums in the same order).
            int a = Math.min(w, r), b = Math.max(a, w - 1 - r), x = 0;
            for (; x < a; x++) {
                tmp[row + x] = (float)(acc / n);
                acc += (double)src[row + Math.min(w - 1, x + r + 1)] - src[row + Math.max(0, x - r)];
            }
            for (int add = row + x + r + 1, drop = row + x - r; x < b; x++, add++, drop++) {
                tmp[row + x] = (float)(acc / n);
                acc += (double)src[add] - src[drop];
            }
            for (; x < w; x++) {
                tmp[row + x] = (float)(acc / n);
                acc += (double)src[row + Math.min(w - 1, x + r + 1)] - src[row + Math.max(0, x - r)];
            }
        }
    }
    /**
     * {@link #blur}'s columns {@code x0..x1 − 1}: {@code tmp} down each column into {@code out}, a row at a time, each
     * column's running sum kept apart (the same sums in the same order as one column at a time, so the same floats),
     * reading memory in order rather than a row's width apart.
     */
    private static void blurColumns(float[] tmp, int w, int h, int r, float[] out, int x0, int x1) {
        int n = 2 * r + 1;
        double[] acc = new double[x1 - x0];
        for (int y = -r; y <= r; y++) for (int x = x0, row = Math.max(0, Math.min(h - 1, y)) * w; x < x1; x++) acc[x - x0] += tmp[row + x];
        for (int y = 0; y < h; y++) {
            int row = y * w, add = Math.min(h - 1, y + r + 1) * w, drop = Math.max(0, y - r) * w;
            for (int x = x0; x < x1; x++) {
                out[row + x] = (float)(acc[x - x0] / n);
                acc[x - x0] += (double)tmp[add + x] - tmp[drop + x];
            }
        }
    }

    /**
     * Growable float arrays reused from one settle or blur to the next, so a stroke's repeated settles allocate nothing
     * once they have met their largest box. Each slot keeps what was last written in it.
     */
    static final class Scratch {
        private final float[][] slots = new float[8][];

        /** Slot {@code slot}, at least {@code n} long; its contents are whatever was left there. */
        float[] get(int slot, int n) {
            float[] a = slots[slot];
            if (a == null || a.length < n) slots[slot] = a = new float[n];
            return a;
        }
        /** As {@link #get}, its first {@code n} values 0. */
        float[] zeroed(int slot, int n) {
            float[] a = get(slot, n);
            java.util.Arrays.fill(a, 0, n, 0f);
            return a;
        }
    }

    /** A hash of {@code seed} and {@code i}, 0 to 1. */
    static double hash01(int seed, int i) {
        int t = seed ^ i * 0x27d4eb2d;
        t = (t ^ (t >>> 15)) * 0x2c1b3c6d; t = (t ^ (t >>> 12)) * 0x297a2d39; t ^= t >>> 15;
        return (t & 0xffffffffL) / 4294967296.0;
    }
    /** A smooth 1-D noise along {@code x}, 0 to 1: one octave and a quarter of a finer one. */
    static double noise1(int seed, double x) {
        double i = Math.floor(x), f = x - i, u = f * f * (3 - 2 * f), fine = Math.floor(x * 2.3);
        double a = lerp(hash01(seed, (int)i), hash01(seed, (int)(i + 1)), u);
        double b = lerp(hash01(seed ^ 0x9e3779b9, (int)fine), hash01(seed ^ 0x9e3779b9, (int)(fine + 1)), (x * 2.3) % 1);
        return a * .75 + b * .25;
    }

    /** Turns head angle {@code a} by {@code k} of the way toward {@code target}, radians; a head is symmetric, so never more than a quarter turn. */
    static double turnToward(double a, double target, double k) {
        double d = (target - a) % Math.PI;
        if (d > Math.PI / 2) d -= Math.PI;
        if (d < -Math.PI / 2) d += Math.PI;
        return a + d * k;
    }

    /**
     * How pressure ({@code pr}), speed ({@code v}, px/ms) and tilt ({@code lean}) lighten a mark, through the Hand's
     * darkness cells; each factor applies only when its cell is on, so the result is exactly 1 with every cell off.
     */
    static double handDark(double pDark, double sDark, double tDark, double pr, double v, double lean) {
        double k = 1;
        if (pDark != 0) k *= 1 - 0.8 * pDark * (1 - pr);
        if (sDark != 0) k *= 1 - 0.55 * sDark * clamp(v / 2.5);
        if (tDark != 0 && lean != 0) k *= 1 - 0.45 * tDark * lean;
        return k;
    }

    /**
     * The hand's page tilt on a dab's size (the Gravity Lab's gSize): {@code ga} is how far the stroke runs downhill
     * (+1) or uphill (−1) times the tilt ({@link StrokePoints#ga}); up to 1.4 times downhill, 0.6 uphill; exactly 1
     * with the cell off or a level page.
     */
    static double gSize(double cell, double ga) { return cell != 0 && ga != 0 ? clamp(1 + .4 * cell * ga, .6, 1.4) : 1; }
    /** The hand's page tilt on a dab's darkness (gDark): uphill up to 40% lighter; exactly 1 off, level or downhill. */
    static double gDark(double cell, double ga) { return cell != 0 && ga < 0 ? 1 + .4 * cell * ga : 1; }
    /** The hand's page tilt on a shaped nib's width {@code bb} (at most {@code a}): it opens downhill. */
    static double gWidth(double cell, double ga, double bb, double a) { return cell != 0 && ga != 0 ? Math.max(0.6, Math.min(a, bb * clamp(1 + .6 * cell * ga, .4, 1.6))) : bb; }

    /** Size scaling past the sizes the old engine was tuned for: 1 up to a 16 px line, then in proportion. */
    static double inkBig(double size) { return Math.max(1, size / 16); }
    /** As {@link #inkBig} for paint: 1 up to 220 px, then by the square root. */
    static double paintBig(double size) { return Math.max(1, Math.sqrt(size / 220)); }
    /** The taper in and out lengths in px for the dials {@code taperIn}, {@code taperOut} at line size {@code size}. */
    static double[] taperLens(double taperIn, double taperOut, double size) {
        double k = Math.sqrt(inkBig(size));
        return new double[]{taperIn * 70 * k, taperOut * 90 * k};
    }
    /** How much of its width a mark keeps {@code f} of the way through a taper: 0.08 at the very end, 1 at and past its length. */
    static double taper(double f) { return Math.pow(clamp(f), 0.6) * 0.92 + 0.08; }
    /** How far tines and lines open with pressure {@code pr}: 1 when {@code breathe} is 0. */
    static double breath(double breathe, double pr) { return mix(1, smooth((pr - .2) / .65), breathe); }

    /** The empty rectangle a stroke's dirty area grows from on a {@code width × height} page. */
    static int[] emptyRect(int width, int height) { return new int[]{width, height, -1, -1}; }
    static boolean empty(int[] r) { return r == null || r[2] < r[0] || r[3] < r[1]; }
    static int[] union(int[] a, int[] b) {
        return new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])};
    }
    /** Grows {@code r} to hold the rectangle {@code x0, y0, x1, y1}. */
    static void grow(int[] r, int x0, int y0, int x1, int y1) {
        if (x0 < r[0]) r[0] = x0;
        if (y0 < r[1]) r[1] = y0;
        if (x1 > r[2]) r[2] = x1;
        if (y1 > r[3]) r[3] = y1;
    }
    /** {@code v} clamped to a pixel index under {@code n}, 0 to n − 1. */
    static int index(int v, int n) { return v < 0 ? 0 : v >= n ? n - 1 : v; }
    /** Grows {@code r} to hold {@code s} (nothing when {@code s} is empty); returns {@code r}. */
    static int[] grow(int[] r, int[] s) {
        if (empty(s)) return r;
        if (s[0] < r[0]) r[0] = s[0];
        if (s[1] < r[1]) r[1] = s[1];
        if (s[2] > r[2]) r[2] = s[2];
        if (s[3] > r[3]) r[3] = s[3];
        return r;
    }

    /**
     * The columns of each pixel row whose centres fall inside an ellipse: semi-axes {@code ea} along the turned axis
     * ({@code cos}, {@code sin}) and {@code eb} across it, centred at {@code cx, cy}. Stampers use it to visit only the
     * pixels a dab can cover, so a thin turned nib doesn't walk its whole bounding box.
     */
    static final class Span {
        private double qa, qb, qc, cx, cy;
        /** The row's first and last column after {@link #row} returned true. */
        int left, right;

        void set(double ea, double eb, double cos, double sin, double cx, double cy) {
            double ia = 1 / (ea * ea), ib = 1 / (eb * eb);
            qa = cos * cos * ia + sin * sin * ib; qb = 2 * cos * sin * (ia - ib); qc = sin * sin * ia + cos * cos * ib;
            this.cx = cx; this.cy = cy;
        }
        /** Row {@code py}'s columns inside the ellipse, widened to whole pixels and clipped to {@code x0..x1}; false if none. */
        boolean row(int py, int x0, int x1) {
            double ry = py + 0.5 - cy, hb = qb * ry, disc = hb * hb - 4 * qa * (qc * ry * ry - 1);
            if (disc < 0) return false;
            double root = Math.sqrt(disc);
            left = Math.max(x0, (int)Math.floor(cx - 0.5 + (-hb - root) / (2 * qa)));
            right = Math.min(x1, (int)Math.ceil(cx - 0.5 + (-hb + root) / (2 * qa)));
            return left <= right;
        }
    }

    private StrokeMath() {}
}
