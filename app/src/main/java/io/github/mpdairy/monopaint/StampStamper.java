package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.lerp;

/**
 * Stamp (mischief-lab-2.html's Stamp, print2 and place2): a brush whose nib is a piece of the page cut with a lasso
 * ({@link StampNib}), laid every Spacing along the line, inside what a tip of its size reaches, in place of the nib and
 * the body: small spacing a continuous nib trail, large separate prints, a tap one print. Turns: the nib turned to the
 * line's heading, eased 35% a print; off, upright (a tap turned a hair either way).
 *
 * <p>Potato-stamp inking: each print is blotchy (value noise in the nib's own coordinates, a twelfth of its longer
 * side across, and a grit per print), coverage {@code clamp((amount × 1.1 − (1 − noise)) × 3 + .1)}, its amount the
 * ink left times {@code .55 + .6 × pressure}, so pressure prints darker. Each print uses Ink Fade's share of the ink: a
 * press (the first print, or a print clear of the last) all of it, overlapping prints {@code (1 − fade)^spacing²}, so a
 * trail runs dry over several stamp lengths, never under {@link #FLOOR}; Ink Fade None, every print as dark as the
 * first. Prints that overlap share the stroke's blotches, so a trail runs dry evenly; prints apart get new ones. The
 * ink carries over to the holder's next stroke ({@link Ink}) until the pen has rested {@link #REST_MS} since the last
 * print, which inks it afresh. Keeps the strongest coverage per pixel, so a stroke's prints don't darken each other,
 * while the medium lays them over the page as it lays any mark (ink, wash, Print Shading and the rest). The prints
 * depend only on the samples (and their times), so however they're batched, quick or live, the same prints land.
 * Pure, checked on the desktop ({@code StampChecks}).
 */
final class StampStamper {
    private StampStamper() { }

    /** A rest this long since the last print (ms) inks the stamp afresh. */
    static final long REST_MS = 1000;
    /** The least ink a print has left. */
    static final double FLOOR = .25;
    /** Turns: how much of the way a print turns toward the line's heading. */
    static final double TURN = .35;
    /** A tap's (and an upright stamp's) turn, at most, radians (±2.3°). */
    static final double JIT = .04;

    /**
     * What a holder's last Stamp stroke left ({@link HeldPaint#stampInk}): its ink and the time of its last print (ms,
     * or {@link PenSpeed#UNTIMED}). A stroke starts with it unless the pen has rested since.
     */
    static final class Ink {
        static final Ink FRESH = new Ink(1, PenSpeed.UNTIMED);
        final double load;
        final long last;
        Ink(double load, long last) { this.load = load; this.last = last; }
    }

    /** One stroke's prints, line or body: the next print's distance, how many so far, the ink left, the heading, the seed. */
    static final class State {
        double next, load, dir = Double.NaN, jit;
        int prints, seed;
        long last = PenSpeed.UNTIMED;
        /** What the stroke leaves its holder. */
        Ink left() { return new Ink(load, last); }
    }

    /** How far apart prints land at size {@code size}, px. */
    static double gap(ResolvedBrush.Pattern P, double size) { return Math.max(1, P.spacing * size); }
    /** How far a stamp of size {@code size} reaches across, page px: its nib's diagonal turned any way, and a pixel each side. */
    static double reach(ResolvedBrush.Pattern P, double size) {
        StampNib nib = P.nib;
        return size * Math.hypot(nib.width, nib.height) / nib.longer() + 3;
    }

    /**
     * The prints from segment {@code from} on into {@code into} (every pixel: a stamp is never on a lattice), the first
     * starting from {@code start}; {@code wetD}, if any, takes each laid pixel's distance along the stroke. Returns the
     * touched rectangle.
     */
    static int[] stamp(FloatTiles into, FloatTiles wetD, ResolvedBrush.Pattern P, StrokePoints pts, double size, int from, LineStamper.Meta meta, Ink start) {
        int[] rect = StrokeMath.emptyRect(into.width, into.height);
        if (pts.count < 1) return rect;
        if (meta.stamp == null) {
            State M = meta.stamp = new State();
            M.seed = StippleStamper.hashInts((int) P.seed ^ 0x57a3b, Math.round(pts.x[0] * 4), Math.round(pts.y[0] * 4));
            M.jit = (StrokeMath.hash01(M.seed, 1) * 2 - 1) * JIT;
            // Rested since the holder's last print (or no times to tell): inked afresh.
            long t0 = pts.t[0];
            boolean rested = start == null || start.last == PenSpeed.UNTIMED || t0 == PenSpeed.UNTIMED || t0 - start.last > REST_MS;
            M.load = rested ? 1 : Math.max(FLOOR, start.load);
        }
        State M = meta.stamp;
        double gap = gap(P, size);
        for (int k = Math.max(1, from); k < pts.count; k++) {
            int j = k - 1;
            double seg = (double) pts.d[k] - pts.d[j];
            double ux = seg > 0 ? ((double) pts.x[k] - pts.x[j]) / seg : 1, uy = seg > 0 ? ((double) pts.y[k] - pts.y[j]) / seg : 0;
            while (M.next <= pts.d[k]) {
                double t = seg > 0 ? clamp((M.next - pts.d[j]) / seg) : 1;
                double x = lerp(pts.x[j], pts.x[k], t), y = lerp(pts.y[j], pts.y[k], t), pr = lerp(pts.p[j], pts.p[k], t), v = lerp(pts.v[j], pts.v[k], t);
                boolean press = M.prints == 0;
                // The first print has no heading yet (a tap's has none at all); later ones turn toward the segment's.
                if (!press && seg > 0) {
                    double head = Math.atan2(uy, ux);
                    if (Double.isNaN(M.dir)) M.dir = head;
                    double d = head - M.dir;
                    while (d > Math.PI) d -= 2 * Math.PI;
                    while (d < -Math.PI) d += 2 * Math.PI;
                    M.dir += d * TURN;
                }
                double angle = P.turns && !Double.isNaN(M.dir) ? M.dir : M.jit;
                double s = P.band(size, pr) * pts.flickWidth(M.next);
                double amount = M.load * (.55 + .6 * pr) * pts.own(j, k, t), dk = P.dark(pr, v);
                // Apart, each print its own blotches; overlapping, the stroke's.
                int seed = P.spacing >= 1 ? StippleStamper.hashInts(M.seed, M.prints, 7) : M.seed;
                int grit = StippleStamper.hashInts(M.seed ^ 0x9e37, M.prints, 3);
                print(into, wetD, P.nib, x, y, s, angle, amount, dk, seed, grit, M.next, rect);
                M.load = Math.max(FLOOR, M.load * Math.pow(1 - P.fade, press ? 1 : Math.min(1, P.spacing * P.spacing)));
                long tk = pts.t[k], tj = pts.t[j];
                M.last = tk == PenSpeed.UNTIMED || tj == PenSpeed.UNTIMED ? tk : Math.round(lerp(tj, tk, t));
                M.prints++;
                M.next += gap;
            }
        }
        return rect;
    }

    /**
     * place2 and print2: {@code nib} centred at {@code cx, cy}, {@code size} px along its longer side, turned by
     * {@code angle}, inked by {@code amount} through its blotches (noise {@code seed}, grit {@code grit}), times the hand's
     * darkness {@code dk}, raised into {@code into}.
     */
    static void print(FloatTiles into, FloatTiles wetD, StampNib nib, double cx, double cy, double size, double angle, double amount, double dk,
            int seed, int grit, double d, int[] rect) {
        if (!(size > 0) || !(dk > 0)) return;
        int w = into.width, h = into.height;
        double k = size / nib.longer(), ca = StrictMath.cos(angle), sa = StrictMath.sin(angle), R = Math.hypot(nib.width, nib.height) * k / 2 + 2;
        int x0 = Math.max(0, (int) Math.floor(cx - R)), x1 = Math.min(w - 1, (int) Math.ceil(cx + R));
        int y0 = Math.max(0, (int) Math.floor(cy - R)), y1 = Math.min(h - 1, (int) Math.ceil(cy + R));
        if (x1 < x0 || y1 < y0) return;
        double cell = Math.max(2, nib.longer() / 12.0), hw = nib.width / 2.0 - .5, hh = nib.height / 2.0 - .5;
        for (int py = y0; py <= y1; py++) for (int px = x0; px <= x1; px++) {
            double dx = px + .5 - cx, dy = py + .5 - cy, u = (dx * ca + dy * sa) / k + hw, q = (-dx * sa + dy * ca) / k + hh;
            if (u < -1 || q < -1 || u > nib.width || q > nib.height) continue;
            double cov = nib.sample(u, q);
            if (!(cov > .01)) continue;
            double n = .65 * valueNoise(u / cell, q / cell, seed) + .35 * StrokeMath.hash01(StippleStamper.hashInts(grit, (int) Math.floor(u), (int) Math.floor(q)), 0);
            float c = (float) (cov * clamp((amount * 1.1 - (1 - n)) * 3 + .1) * dk);
            if (c > 0 && into.raise(px, py, c) && wetD != null) wetD.set(px, py, (float) d);
        }
        StrokeMath.grow(rect, x0, y0, x1, y1);
    }

    /** mischief-lab-2.html vnoise: smooth value noise, 0 to 1, a feature a unit across at {@code x, y}. */
    static double valueNoise(double x, double y, int seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = StrokeMath.smoothstep(0, 1, x - ix), fy = StrokeMath.smoothstep(0, 1, y - iy);
        double a = corner(ix, iy, seed), b = corner(ix + 1, iy, seed), c = corner(ix, iy + 1, seed), e = corner(ix + 1, iy + 1, seed);
        double top = a + (b - a) * fx;
        return top + (c + (e - c) * fx - top) * fy;
    }
    private static double corner(int x, int y, int seed) { return StrokeMath.hash01(StippleStamper.hashInts(seed, x, y), 0); }
}
