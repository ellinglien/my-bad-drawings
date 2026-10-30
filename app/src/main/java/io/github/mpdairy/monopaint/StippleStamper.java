package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.lerp;

/**
 * Stipple (the new modules spec; newmods.js stampStipple): dots scattered around the path instead of a solid mark.
 * Every cell of path, a dot and a bit long, holds a fixed set of candidate dots from the stroke's seed and the cell's
 * number; pressure decides how many land (12% light, all pressed), so tone rises with pressure and with each pass.
 * Jitter 0 sets them on a staggered lattice across the band (an even stipple), 1 scatters them, varies their size and
 * clumps them in twos to fours (a sponge). Build-up, when on, keeps laying dots at random in the band while the pen
 * rests or lingers ({@link Holds}), as a spray does. Dots depend only on the samples (and their times, for Build-up),
 * so however the samples are batched, quick or live, the same dots land. Keeps the strongest coverage per pixel.
 * Arithmetic as the lab's, so the same samples lay the same coverage to the bit. Pure, checked on the desktop.
 */
final class StippleStamper {
    private StippleStamper() { }

    /** One stroke's stipple, line or body: its seed, the next cell's distance and number, and the hold dots laid. */
    static final class State {
        int seed;
        double next;
        int cell, holdsLaid;
        double holdAcc;
    }

    /** Build-up's pen time between two of its ticks, ms. */
    static final long TICK = 20;
    /** Build-up slow and fast: about how long a held pen takes to cover its band once, ms. */
    static final double[] FILL_MS = {0, 4000, 1200};
    /** The most ticks one sample adds (a pen resting 5 s without a sample); the most dots a tick lays. */
    static final int MOST_TICKS = 250, MOST_DOTS = 400;

    /**
     * Build-up's ticks: every {@link #TICK} of pen time since pen-down, the pen's place, pressure and distance along the
     * stroke at the sample that reached it. Fed every sample, kept or not (a resting pen's are too close to keep), so a
     * pen held still keeps its tick; a stroke with Build-up off has none.
     */
    static final class Holds {
        private long t0 = PenSpeed.UNTIMED, ticks;
        int count;
        float[] x = new float[16], y = new float[16], p = new float[16], d = new float[16];
        int[] tick = new int[16];

        /** A sample at {@code time} (ms), position {@code x, y}, pressure travel {@code p}, {@code d} px along; whether it added ticks. */
        boolean feed(float x, float y, float p, long time, float d) {
            if (time == PenSpeed.UNTIMED) return false;
            if (t0 == PenSpeed.UNTIMED) { t0 = time; return false; }
            long now = Math.max(ticks, Math.min(ticks + MOST_TICKS, (time - t0) / TICK));
            if (now == ticks) return false;
            for (long n = ticks + 1; n <= now; n++) {
                if (count == tick.length) grow();
                this.x[count] = x; this.y[count] = y; this.p[count] = p; this.d[count] = d; tick[count] = (int)n;
                count++;
            }
            ticks = now;
            return true;
        }
        private void grow() {
            int more = 2 * tick.length;
            x = java.util.Arrays.copyOf(x, more); y = java.util.Arrays.copyOf(y, more); p = java.util.Arrays.copyOf(p, more);
            d = java.util.Arrays.copyOf(d, more); tick = java.util.Arrays.copyOf(tick, more);
        }
    }

    /** A dot's diameter for tip size {@code size}: up to two fifths of the tip, at least 1.6 px so the panel shows each, at most 60. */
    static double dotSize(ResolvedBrush.Pattern P, double size) { return clamp(size * lerp(.05, .4, P.dot * P.dot), 1.6, 60); }
    /** The band's half-width the dots scatter over at full pressure: the tip's half times 1 + 1.6 × Scatter. */
    static double band(ResolvedBrush.Pattern P, double size) { return Math.max(dotSize(P, size) * .5, size / 2 * (1 + 1.6 * P.scatter)); }
    /** How far a stipple reaches across, page px: its band, a clump's spread past it and a dot's radius each side. */
    static double reach(ResolvedBrush.Pattern P, double size) { return reach(P, size, 1); }
    /** How far it reaches at pressure travel {@code pr}: its band as pressure narrows it (the hover ring's, {@link HoverNib}). */
    static double reach(ResolvedBrush.Pattern P, double size, double pr) {
        double dot = dotSize(P, size), clump = P.jitter > .5 ? .9 * dot : 0;
        return 2 * (P.band(band(P, size), pr) + clump + dot / 2 * (1 + .45 * P.jitter) + 1);
    }

    /** newmods.js hashInts: FNV-style, each value XORed in then multiplied. */
    static int hashInts(int h, int a, int b) { h = (h ^ a) * 16777619; return (h ^ b) * 16777619; }

    /**
     * stampStipple: the cells from segment {@code from} on, and Build-up's ticks not yet laid ({@code holds}, or null),
     * into {@code into} (every pixel: a stipple is never on a lattice); {@code wetD}, if any, takes each laid pixel's
     * distance along the stroke. Returns the touched rectangle.
     */
    static int[] stamp(FloatTiles into, FloatTiles wetD, ResolvedBrush.Pattern P, StrokePoints pts, double size, int from, LineStamper.Meta meta, Holds holds) {
        int[] rect = StrokeMath.emptyRect(into.width, into.height);
        if (pts.count < 1) return rect;
        if (meta.stipple == null) {
            meta.stipple = new State();
            meta.stipple.seed = hashInts((int)P.seed ^ 0x57e1, Math.round(pts.x[0] * 4), Math.round(pts.y[0] * 4));
        }
        State M = meta.stipple;
        double dotD = dotSize(P, size), band = band(P, size);
        double clump = P.jitter > .5 ? (P.jitter - .5) * 2 : 0, step = Math.max(1.2, dotD * .9);
        int per = (int)Math.max(1, Math.round(2 * band * step / (dotD * dotD) * 1.1 / (1 + 1.5 * clump)));
        for (int k = Math.max(1, from); k < pts.count; k++) {
            int j = k - 1;
            double seg = (double)pts.d[k] - pts.d[j];
            double ux = seg > 0 ? ((double)pts.x[k] - pts.x[j]) / seg : 1, uy = seg > 0 ? ((double)pts.y[k] - pts.y[j]) / seg : 0;
            while (M.next <= pts.d[k]) {
                double t = seg > 0 ? clamp((M.next - pts.d[j]) / seg) : 1;
                double x = lerp(pts.x[j], pts.x[k], t), y = lerp(pts.y[j], pts.y[k], t), pr = lerp(pts.p[j], pts.p[k], t), v = lerp(pts.v[j], pts.v[k], t);
                double dens = lerp(.12, 1, StrokeMath.smooth(pr)), dk = P.dark(pr, v) * pts.own(j, k, t), bw = P.band(band, pr);
                int c = M.cell;
                for (int q = 0; q < per; q++) {
                    int hs = hashInts(M.seed, c, q);
                    if (StrokeMath.hash01(hs, 0) >= dens) continue;
                    double lat = ((q + .5 + (c & 1) * .5) / per) % 1 * 2 - 1, rnd = StrokeMath.hash01(hs, 1) + StrokeMath.hash01(hs, 2) - 1;
                    double across = lerp(lat, rnd, P.jitter) * bw, along = (StrokeMath.hash01(hs, 3) - .5) * step * (.3 + .7 * P.jitter);
                    double cx = x - uy * across + ux * along, cy = y + ux * across + uy * along;
                    dotAndClump(into, wetD, P, hs, cx, cy, dotD, clump, dk, M.next, rect);
                }
                M.next += step; M.cell++;
            }
        }
        if (holds != null && P.hold > 0) {
            // Build-up: each tick's dots at random in the band round the pen, as many as cover it once in FILL_MS, by pressure.
            double fill = FILL_MS[Math.min(FILL_MS.length - 1, P.hold)];
            for (; M.holdsLaid < holds.count; M.holdsLaid++) {
                int e = M.holdsLaid;
                double pr = holds.p[e], bw = P.band(band, pr), dens = lerp(.12, 1, StrokeMath.smooth(pr));
                M.holdAcc += Math.min(MOST_DOTS, 4 * bw * bw / (dotD * dotD) * dens * TICK / fill);
                int n = (int)Math.floor(M.holdAcc);
                M.holdAcc -= n;
                double dk = P.dark(pr, 0) * (pts.own == null ? 1 : pts.own[pts.count - 1]);
                for (int q = 0; q < n; q++) {
                    int hs = hashInts(M.seed ^ 0x401d, holds.tick[e], q);
                    double a = 2 * Math.PI * StrokeMath.hash01(hs, 1), rad = bw * Math.sqrt(StrokeMath.hash01(hs, 2));
                    dotAndClump(into, wetD, P, hs, holds.x[e] + rad * StrictMath.cos(a), holds.y[e] + rad * StrictMath.sin(a), dotD, clump, dk, holds.d[e], rect);
                }
            }
        }
        return rect;
    }

    /** A dot of the candidate {@code hs} at {@code cx, cy}, its size jittered, and its clump of up to three more as Jitter nears 1. */
    private static void dotAndClump(FloatTiles into, FloatTiles wetD, ResolvedBrush.Pattern P, int hs, double cx, double cy, double dotD, double clump, double dk, double d, int[] rect) {
        double r = Math.max(.8, dotD / 2 * (1 + P.jitter * .9 * (StrokeMath.hash01(hs, 4) - .5)));
        dot(into, wetD, cx, cy, r, dk, d, rect);
        for (int n = (int)Math.floor(clump * 3.99 * StrokeMath.hash01(hs, 5)), m = 0; m < n; m++)
            dot(into, wetD, cx + (StrokeMath.hash01(hs, 6 + m) - .5) * dotD * 1.8, cy + (StrokeMath.hash01(hs, 9 + m) - .5) * dotD * 1.8,
                    r * lerp(.45, .9, StrokeMath.hash01(hs, 12 + m)), dk, d, rect);
    }

    /** newmods.js nmDot: a round dot of radius {@code r}, its edge antialiased over a pixel, at darkness {@code val}. */
    static void dot(FloatTiles into, FloatTiles wetD, double cx, double cy, double r, double val, double d, int[] rect) {
        int w = into.width, h = into.height;
        int x0 = Math.max(0, (int)Math.floor(cx - r - 1)), x1 = Math.min(w - 1, (int)Math.ceil(cx + r + 1));
        int y0 = Math.max(0, (int)Math.floor(cy - r - 1)), y1 = Math.min(h - 1, (int)Math.ceil(cy + r + 1));
        if (x1 < x0 || y1 < y0) return;
        for (int py = y0; py <= y1; py++) for (int px = x0; px <= x1; px++) {
            double dx = px + .5 - cx, dy = py + .5 - cy, cov = clamp(r + .5 - Math.sqrt(dx * dx + dy * dy)) * val;
            if (cov <= 0) continue;
            into.raise(px, py, (float)cov);
            if (wetD != null) wetD.set(px, py, (float)d);
        }
        StrokeMath.grow(rect, x0, y0, x1, y1);
    }
}
