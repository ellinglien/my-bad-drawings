package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.lerp;

/**
 * Hand Hatch (the owner's "handmade crosshatch", 2026-10-21; "like stipple/spray with lines", 2026-10-21): a spray of
 * short hatch strokes. Round the pen, within the tip's band, it scatters strokes as an airbrush scatters dots: each a
 * short, slightly crooked stroke about the size of the tip long (a little longer or shorter each), turned a few degrees
 * off the angle, slightly bowed, heavier where it starts and flicked thin at its end, its place random in the spray.
 *
 * <p>The spray builds a dose on the page, in cells of a fixed grid ({@link State#dose}): the more at the middle of the
 * band, less toward its edge ({@code 1 − q²}, q the squared distance a share of the band's). Passing over a place doses it by pressure ({@link #passDose}); the pen's time there doses
 * it too ({@link #holdRate}, Build-up's ticks, {@link StippleStamper.Holds}), so a held pen grows a patch, a slow drag
 * lays more than a quick one, and going over again adds more. Each cell holds a fixed list of candidate strokes, each
 * laid once the cell's dose reaches its own threshold: the first third in one direction, then the crossing direction,
 * then the third ({@link HatchStamper#TURN}; as many as Layers), until the cell is full at the darkest, Spacing's
 * pitch. Its stroke's dials: Line Length, Wobble (turn, bow, length and weight together) and Taper Out.
 *
 * <p>Lines fixed (the default) keeps the angle on the page; following turns it with the stroke where each is laid. The
 * candidates are seeded by the brush's seed and the stroke's first sample, so a second stroke over the first lays its
 * own. The moving pen's doses (fixed steps along the stroke) and the held pen's (its ticks, by distance) are taken in
 * one order, by distance along the stroke, however the samples are batched, quick or live; each stroke keeps the
 * strongest coverage per pixel. Pure, checked on the desktop ({@code HandHatchChecks}).
 */
final class HandHatchStamper {
    private HandHatchStamper() { }

    /** A stroke's length, a share of the tip's size, at Line Length 0 and 1 (the middle, 1.05). */
    static double length(ResolvedBrush.Pattern P) { return lerp(.45, 1.65, P.hLen); }
    /** How far a stroke's length varies either way at Wobble {@code w}, a share of it. */
    static double lengthVary(double w) { return .08 + .44 * w; }
    /** At Wobble 1: how far a stroke turns off its angle either way, degrees; how deep its bow, a share of its length. */
    static final double TURN_VARY = 10, BOW = .06;
    /**
     * The dose one pass lays at the middle of the band, light to firm pressure (a full cell is 1): a light pass lays
     * a few strokes one way, a firm one about half the cell, crossed.
     */
    static double passDose(double pr) { return lerp(.06, 1, Math.pow(pr, 1.5)); }
    /** The dose a held pen lays a millisecond at the middle of the band, light to firm: about 2.5 s to the darkest light, .7 s firm. */
    static double holdRate(double pr) { return lerp(.25, 1, pr) / 700; }
    /** Strokes scattered at random need about this many times an even hatching's to read as dark at its spacing. */
    static final double FILL = 1.6;
    /** The most strokes a cell holds, however small the pitch. */
    static final int MOST = 60;

    /** One stroke's hatching, line or body: its seed, the next step's distance, the ticks taken, and the page's cells. */
    static final class State {
        int seed;
        boolean seeded;
        double next;
        int ticksLaid, grid, gw, gh;
        /** Each cell's dose so far; its strokes laid; their darkness (the most of the doses' darkness) and distance along. */
        float[] dose, dark, at;
        byte[] laid;
    }

    /** A cell's side, page px: about a third of a stroke, at least 4. */
    static int grid(ResolvedBrush.Pattern P, double size) { return Math.max(4, (int) Math.round(strokeLength(P, size) / 3)); }
    static double strokeLength(ResolvedBrush.Pattern P, double size) { return Math.max(6 * lineWidth(P, size), size * length(P)); }
    /**
     * How many strokes fill a cell at the darkest, on average: each direction's strokes Spacing apart, as many
     * directions as Layers. A cell holds this many give or take one ({@link Spray#most}), so the average holds between
     * whole numbers.
     */
    static double full(ResolvedBrush.Pattern P, double size) {
        double g = grid(P, size), perDirection = g * g / (pitch(P, size) * strokeLength(P, size));
        return Math.max(1, Math.min(MOST - 1, FILL * perDirection * layers(P)));
    }
    static int layers(ResolvedBrush.Pattern P) { return Math.max(1, Math.min(3, P.layers)); }
    /** How far hatching reaches across, page px: the band, a cell, a stroke's half length at its longest, its bow and weight. */
    static double reach(ResolvedBrush.Pattern P, double size) {
        double len = strokeLength(P, size);
        return size * .5 + grid(P, size) + len * .5 * (1 + lengthVary(P.hWob)) * (1 + BOW * P.hWob) + lineWidth(P, size) + 3;
    }
    /** The strokes' spacing at the darkest (Spacing), page px: Hatch's. */
    static double pitch(ResolvedBrush.Pattern P, double size) { return HatchStamper.pitch(P, size); }
    /** A stroke's weight, page px: Hatch's line weight. */
    static double lineWidth(ResolvedBrush.Pattern P, double size) { return HatchStamper.lineWidth(P, size); }

    /**
     * The spray from segment {@code from} on, and the held pen's ticks ({@code holds}) not yet taken, into {@code into};
     * {@code wetD}, if any, takes each laid pixel's distance along the stroke. Returns the touched rectangle.
     */
    static int[] stamp(FloatTiles into, FloatTiles wetD, ResolvedBrush.Pattern P, StrokePoints pts, double size, int from, LineStamper.Meta meta, StippleStamper.Holds holds) {
        int w = into.width, h = into.height;
        int[] rect = StrokeMath.emptyRect(w, h);
        if (pts.count < 1) return rect;
        if (meta.handHatch == null) meta.handHatch = new State();
        State M = meta.handHatch;
        if (!M.seeded) {
            // The stroke's own seed: the brush's and its first sample's place, so the same samples lay the same strokes.
            M.seed = mix((int) P.seed ^ (int) (P.seed >>> 32), (int) Math.floor(pts.x[0]), (int) Math.floor(pts.y[0]));
            M.grid = grid(P, size);
            M.gw = (w + M.grid - 1) / M.grid; M.gh = (h + M.grid - 1) / M.grid;
            int n = M.gw * M.gh;
            M.dose = new float[n]; M.dark = new float[n]; M.at = new float[n]; M.laid = new byte[n];
            M.seeded = true;
        }
        Spray S = new Spray(P, size, M, into, wetD, rect);
        // The moving pen's steps and the held pen's ticks in one order, by distance (a tick after the steps at its own).
        int e = M.ticksLaid, ticks = holds == null ? 0 : holds.count;
        for (int q = Math.max(1, from); q < pts.count; q++) {
            int j = q - 1;
            double seg = (double) pts.d[q] - pts.d[j];
            double ux = !Float.isNaN(pts.ux[q]) ? pts.ux[q] : seg > 0 ? ((double) pts.x[q] - pts.x[j]) / seg : 1;
            double uy = !Float.isNaN(pts.uy[q]) ? pts.uy[q] : seg > 0 ? ((double) pts.y[q] - pts.y[j]) / seg : 0;
            while (M.next <= pts.d[q]) {
                for (; e < ticks && holds.d[e] < M.next; e++) S.tick(pts, holds, e);
                double t = seg > 0 ? clamp((M.next - pts.d[j]) / seg) : 1;
                double x = lerp(pts.x[j], pts.x[q], t), y = lerp(pts.y[j], pts.y[q], t), pr = lerp(pts.p[j], pts.p[q], t), v = lerp(pts.v[j], pts.v[q], t);
                double r = P.band(S.r0, pr);
                // A pass's dose spread over the steps that cross a place: a chord of the band holds 2r / step of them, at
                // .8 of the middle's weight on average.
                S.dose(x, y, r, passDose(pr) * S.step * .625 / r, P.dark(pr, v) * pts.own(j, q, t), M.next, ux, uy);
                M.next += S.step;
            }
        }
        for (; e < ticks; e++) S.tick(pts, holds, e);
        M.ticksLaid = e;
        return rect;
    }

    /** One stamp's spraying: the frame's directions, the stroke's sizes, and where it lays. */
    private static final class Spray {
        final ResolvedBrush.Pattern P;
        final State M;
        final FloatTiles into, wetD;
        final int[] rect;
        final double lw, len, r0, step, g;
        final int layers;
        final double full;
        /** Wobble's: length's variation, turn (radians), bow, weight's variation, the share drawn the other way. */
        final double vary, turnVary, bow, weightVary, backs;
        final double[] cs = new double[3], sn = new double[3];
        final Line line = new Line();
        Spray(ResolvedBrush.Pattern P, double size, State M, FloatTiles into, FloatTiles wetD, int[] rect) {
            this.P = P; this.M = M; this.into = into; this.wetD = wetD; this.rect = rect;
            lw = lineWidth(P, size); len = strokeLength(P, size); r0 = size / 2; g = M.grid;
            step = Math.max(1, Math.min(r0, g) * .5);
            layers = layers(P); full = full(P, size);
            double wob = P.hWob;
            vary = lengthVary(wob); turnVary = TURN_VARY * wob * LineStamper.DEG; bow = BOW * wob;
            weightVary = .1 + .6 * wob; backs = .4 * wob; line.ends = P.hEnds;
            for (int L = 0; L < 3; L++) {
                double a = (P.angle + HatchStamper.TURN[L]) * LineStamper.DEG;
                cs[L] = StrictMath.cos(a); sn[L] = StrictMath.sin(a);
            }
        }

        /** Held tick {@code e}: the pen's time at its place, its heading the stroke's where it reached. */
        void tick(StrokePoints pts, StippleStamper.Holds holds, int e) {
            double x = holds.x[e], y = holds.y[e], pr = holds.p[e], d = holds.d[e];
            int at = Math.min(pts.count - 1, pts.firstAt((float) d));
            double ux = Float.isNaN(pts.ux[at]) ? 1 : pts.ux[at], uy = Float.isNaN(pts.uy[at]) ? 0 : pts.uy[at];
            if (pts.count > 1 && Float.isNaN(pts.ux[at]) && at > 0) {
                double dx = pts.x[at] - pts.x[at - 1], dy = pts.y[at] - pts.y[at - 1], l = Math.hypot(dx, dy);
                if (l > 0) { ux = dx / l; uy = dy / l; }
            }
            double dk = P.dark(pr, 0) * (pts.own == null ? 1 : pts.own[at]);
            dose(x, y, P.band(r0, pr), holdRate(pr) * StippleStamper.TICK, dk, d, ux, uy);
        }

        /**
         * Doses the cells round {@code x, y} within {@code r}: {@code amount} at the middle, less toward the edge (a
         * spray's falloff), at darkness {@code dk}, {@code d} along, heading {@code ux, uy}; lays each stroke whose
         * threshold its cell's dose has reached.
         */
        void dose(double x, double y, double r, double amount, double dk, double d, double ux, double uy) {
            if (!(r > 0) || !(amount > 0) || !(dk > 0)) return;
            int gx0 = Math.max(0, (int) Math.floor((x - r) / g)), gx1 = Math.min(M.gw - 1, (int) Math.floor((x + r) / g));
            int gy0 = Math.max(0, (int) Math.floor((y - r) / g)), gy1 = Math.min(M.gh - 1, (int) Math.floor((y + r) / g));
            double rr = r * r;
            for (int gy = gy0; gy <= gy1; gy++) for (int gx = gx0; gx <= gx1; gx++) {
                double cx = (gx + .5) * g - x, cy = (gy + .5) * g - y, q = (cx * cx + cy * cy) / rr;
                if (q >= 1) continue;
                int i = gy * M.gw + gx;
                int most = most(i);
                if (M.laid[i] >= most) continue;
                M.dose[i] += (float) (amount * (1 - q * q));
                int was = M.laid[i];
                if (M.dark[i] == 0) M.at[i] = (float) d;
                if (dk > M.dark[i]) {
                    // Darker now: the strokes laid here again, as dark (each keeps the strongest coverage).
                    M.dark[i] = (float) dk;
                    for (int k = 0; k < was; k++) lay(gx, gy, k, ux, uy);
                }
                int k = was;
                while (k < most && threshold(i, k) <= M.dose[i]) lay(gx, gy, k++, ux, uy);
                M.laid[i] = (byte) k;
            }
        }

        /** The most strokes cell {@code i} holds: {@link #full}, rounded up or down by chance as its fraction says. */
        int most(int i) { return (int) Math.floor(full + unit(mix(M.seed, i, -1))); }
        /** Candidate {@code k}'s threshold in cell {@code i}: the k-th share of the full cell, give or take half a share. */
        double threshold(int i, int k) { return (k + .25 + .5 * unit(mix(M.seed, i, k * 2 + 1))) / full; }

        /** Lays cell {@code gx, gy}'s candidate {@code k}: its direction by its rank, its place in the cell, its own turn, bow and weight. */
        void lay(int gx, int gy, int k, double ux, double uy) {
            int i = gy * M.gw + gx, hs = mix(M.seed, i, k * 2);
            // As the cell fills: one direction, then the crossing one, then the third.
            int L = Math.min(layers - 1, (int) (k * layers / Math.max(full, layers)));
            double turn = (unit(hs * 0xc2b2ae35) - .5) * 2 * turnVary;
            double ca = cs[L] * Math.cos(turn) - sn[L] * Math.sin(turn), sa = sn[L] * Math.cos(turn) + cs[L] * Math.sin(turn);
            if (P.fixed) { line.ex = ca; line.ey = sa; }
            else { line.ex = ca * ux - sa * uy; line.ey = ca * uy + sa * ux; }
            line.cx = (gx + unit(hs)) * g; line.cy = (gy + unit(hs * 0x9e3779b1)) * g;
            double half = .5 * len * (1 + (unit(hs * 0x85ebca6b) - .5) * 2 * vary);
            line.half = half;
            line.bow = (unit(hs * 0x27d4eb2d) - .5) * 2 * bow * 2 * half;
            line.width = lw * (1 + (unit(hs * 0x165667b1) - .5) * weightVary);
            // Most strokes go the same way (a hand's habit), a few the other.
            line.back = unit(hs * 0x7feb352d) < backs;
            line.dark = M.dark[i];
            line.lay(into, wetD, M.at[i], rect);
        }
    }

    /** One hatch stroke: its centre, heading, half its length, bow (px off its chord at the middle), weight, which way it went and how dark. */
    static final class Line {
        double cx, cy, ex, ey, half, bow, width, dark;
        /** Taper Out: 0 blunt, 1 a thin flick, and pressed on at the start the more so. */
        double ends = .7;
        boolean back;

        /** Its weight along it, {@code u} from -1 (where the pen set down) to 1: pressed on at the start, flicked thin at the end. */
        double weight(double u) {
            if (back) u = -u;
            double start = u < -.8 ? 1 - .43 * ends * (1 - (u + 1) / .2) : 1;
            double end = u > .45 ? 1 - ends * StrokeMath.smoothstep(.45, 1, u) : 1;
            return start * end * (1.1 - .2 * (u + 1) / 2);
        }

        /** Lays it into {@code into}, keeping the stronger coverage at each pixel, growing {@code rect}. */
        void lay(FloatTiles into, FloatTiles wetD, float d, int[] rect) {
            int w = into.width, h = into.height;
            double nx = -ey, ny = ex, hw = width / 2 * 1.1 + 1;
            boolean wide = Math.abs(ex) >= Math.abs(ey);
            // Along its major axis, a pixel column (or row) at a time, the few pixels round its centre line there.
            double a0 = wide ? cx - half * Math.abs(ex) : cy - half * Math.abs(ey), a1 = wide ? cx + half * Math.abs(ex) : cy + half * Math.abs(ey);
            double major = wide ? Math.abs(ex) : Math.abs(ey), spread = hw / major + 1.5;
            int lo = Math.max(0, (int) Math.floor(a0 - hw)), hi = Math.min((wide ? w : h) - 1, (int) Math.ceil(a1 + hw));
            int b0 = Integer.MAX_VALUE, b1 = Integer.MIN_VALUE, c0 = Integer.MAX_VALUE, c1 = Integer.MIN_VALUE;
            for (int a = lo; a <= hi; a++) {
                double ac = a + .5;
                // The chord's point at this column (row), then the bow's offset there.
                double tl = wide ? (ac - cx) / (ex * half) : (ac - cy) / (ey * half);
                double tc = clamp(tl, -1, 1);
                double mid = (wide ? cy + ey * half * tl : cx + ex * half * tl) + (wide ? ny : nx) * bow * (1 - tc * tc);
                int m0 = (int) Math.floor(mid - spread), m1 = (int) Math.ceil(mid + spread);
                for (int b = m0; b <= m1; b++) {
                    int px = wide ? a : b, py = wide ? b : a;
                    if (px < 0 || py < 0 || px >= w || py >= h) continue;
                    double dx = px + .5 - cx, dy = py + .5 - cy;
                    double along = dx * ex + dy * ey, u = along / half;
                    double over = Math.abs(along) - half;
                    if (over > hw) continue;
                    double uc = clamp(u, -1, 1);
                    double off = Math.abs(dx * nx + dy * ny - bow * (1 - uc * uc));
                    double half = width / 2 * weight(uc);
                    double c = clamp(half + .5 - off) * clamp(.5 - over);
                    if (!(c > 0)) continue;
                    float out = (float) (c * dark);
                    if (into.raise(px, py, out) && wetD != null) wetD.set(px, py, d);
                    if (px < b0) b0 = px; if (px > b1) b1 = px; if (py < c0) c0 = py; if (py > c1) c1 = py;
                }
            }
            if (b1 >= b0) StrokeMath.grow(rect, b0, c0, b1, c1);
        }
    }

    /** A hash of three ints. */
    static int mix(int a, int b, int c) {
        int h = a * 0x27d4eb2d ^ b * 0x165667b1 ^ c * 0x9e3779b1;
        h = (h ^ (h >>> 15)) * 0x85ebca6b; h = (h ^ (h >>> 13)) * 0xc2b2ae35;
        return h ^ (h >>> 16);
    }
    /** {@code h} as 0 to 1. */
    static double unit(int h) { int g = (h ^ (h >>> 16)) * 0x45d9f3b; g ^= g >>> 16; return (g & 0xffffffffL) / 4294967296.0; }
}
