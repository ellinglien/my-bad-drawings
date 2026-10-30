package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Floating paint (the Float spec; the web lab's float.js Bath): a stack of rings, oldest first, each one or more closed
 * outlines in page pixels and one flat grey (clear water's is 0). Everything that moves paint on the water moves the
 * rings' points, never pixels, so edges stay crisp however often the bath is stirred; painting the rings oldest to
 * newest is exact, because a new drop sits in the hole it made ({@link FloatScan}).
 * <ul>
 * <li>a drop (Jaffer and Lu): {@code P' = C + (P − C) √(1 + r² / |P − C|²)}, every ring's area kept exactly;
 * <li>a drag (marble-and-bristle's stylus): per tine the stream function {@code ψ = (U × r) exp(−|r|² / 2σ²)}, divergence
 * free, with slip;
 * <li>drift (the page's tilt): a shear along the downhill direction, faster mid-bath, divergence free.
 * </ul>
 * Each change is an {@link Op}, logged on the stroke that made it (drift on the last stroke), so replaying the log gives
 * the same bath bit for bit. Undo while it floats ({@link #undoLast}) takes the last stroke back from a copy of the rings
 * kept before it, or replays at most {@link #CHECKPOINT} − 1 strokes from the copy kept every {@link #CHECKPOINT}.
 * Outlines are refined after each op batch ({@link #flush}): long edges split, points closer than {@link #MIN_SEG} merged,
 * coarser past {@link #CAP} points. Moves share their points over {@link Cores} (each point moves on its own, so the bath
 * is the same on any number of cores). Pure, checked on the desktop ({@code FloatChecks}).
 */
final class FloatBath {
    /** Outlines are refined to points this far apart at most and at least, page px (float.js F_MAXSEG, F_MINSEG). */
    static final double MAX_SEG = 1.6, MIN_SEG = .45;
    /** Past this many points refining coarsens (the spec's cap: ~1 MB of points). */
    static int CAP = 120_000;
    /** A copy of the rings is kept before every this many strokes, for Undo further back than the last. */
    static final int CHECKPOINT = 8;
    /** Moves over this many points share them over the cores. */
    static final int SHARE_POINTS = 6000;

    /** One ring: its outlines (a film may have holes), one flat grey, its box {left, top, right, bottom}. */
    static final class Ring {
        float[][] x, y;
        int[] n;
        final float dark;
        final float[] box = new float[4];
        int pts;
        /** Moves too small to show, added up since it last showed; whether they passed {@link #MOVED}; its box when it last showed. */
        float lag;
        boolean lagged;
        final float[] shownBox = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};

        Ring(float[][] x, float[][] y, int[] n, float dark) { this.x = x; this.y = y; this.n = n; this.dark = dark; box(); }
        /** A disc of radius {@code r} at {@code cx, cy}, points about 1.2 px apart (at least 24); {@code wobble} its edge's unevenness. */
        static Ring circle(double cx, double cy, double r, float dark, double[] wobble) {
            int n = Math.max(24, (int) Math.ceil(2 * Math.PI * r / 1.2));
            float[] xs = new float[n], ys = new float[n];
            for (int i = 0; i < n; i++) {
                double a = i * 2 * Math.PI / n, k = 1;
                if (wobble != null) for (int j = 0; j < wobble.length; j += 3) k += wobble[j] * Math.cos(wobble[j + 1] * a + wobble[j + 2]);
                xs[i] = (float) (cx + r * k * Math.cos(a)); ys[i] = (float) (cy + r * k * Math.sin(a));
            }
            return new Ring(new float[][]{xs}, new float[][]{ys}, new int[]{n}, dark);
        }
        void box() {
            float l = Float.POSITIVE_INFINITY, t = Float.POSITIVE_INFINITY, r = Float.NEGATIVE_INFINITY, b = Float.NEGATIVE_INFINITY;
            int p = 0;
            for (int c = 0; c < n.length; c++) {
                float[] xs = x[c], ys = y[c];
                for (int i = 0; i < n[c]; i++) {
                    float px = xs[i], py = ys[i];
                    if (px < l) l = px; if (px > r) r = px; if (py < t) t = py; if (py > b) b = py;
                }
                p += n[c];
            }
            box[0] = l; box[1] = t; box[2] = r; box[3] = b; pts = p;
        }
        Ring copy() {
            float[][] cx = new float[n.length][], cy = new float[n.length][];
            for (int c = 0; c < n.length; c++) { cx[c] = Arrays.copyOf(x[c], n[c]); cy[c] = Arrays.copyOf(y[c], n[c]); }
            return new Ring(cx, cy, n.clone(), dark);
        }
        /** Its area, page px² (holes subtracted; for the checks). */
        double area() {
            double a = 0;
            for (int c = 0; c < n.length; c++) {
                double s = 0;
                for (int i = 0, j = n[c] - 1; i < n[c]; j = i++) s += (double) x[c][j] * y[c][i] - (double) x[c][i] * y[c][j];
                a += s / 2;
            }
            return Math.abs(a);
        }
    }

    /** A change to the bath, as logged. */
    abstract static class Op { abstract void apply(FloatBath b); }
    /** A drop of radius² {@code r2} at {@code x, y}: pushes everything out, then (unless only a push) floats a disc of grey {@code dark}. */
    static final class Drop extends Op {
        final double x, y, r2; final float dark; final boolean disc; final double[] wobble;
        Drop(double x, double y, double r2, float dark, boolean disc, double[] wobble) { this.x = x; this.y = y; this.r2 = r2; this.dark = dark; this.disc = disc; this.wobble = wobble; }
        @Override void apply(FloatBath b) { b.drop(x, y, r2); if (disc) b.add(Ring.circle(x, y, Math.sqrt(r2), dark, wobble)); b.dirtyAll = true; }
    }
    /** A pen's band pushes in order ({@code x, y, r2} triples); points farther than {@code near} from them take one summed push. */
    static final class Pushes extends Op {
        final double[] list; final double near;
        Pushes(double[] list, double near) { this.list = list; this.near = near; }
        @Override void apply(FloatBath b) { b.pushes(list, near); }
    }
    /** The pen dragged from {@code a} to {@code b}: tines at {@code tines} across the motion, each {@code sig} wide, with {@code slip}. */
    static final class Rake extends Op {
        final double ax, ay, bx, by, sig, slip; final double[] tines;
        Rake(double ax, double ay, double bx, double by, double sig, double[] tines, double slip) { this.ax = ax; this.ay = ay; this.bx = bx; this.by = by; this.sig = sig; this.tines = tines; this.slip = slip; }
        @Override void apply(FloatBath b) { b.rake(this); }
    }
    /** A stroke's own paint floats: its films' outlines and greys. */
    static final class Film extends Op {
        final List<Ring> films;
        Film(List<Ring> films) { this.films = films; }
        @Override void apply(FloatBath b) { for (Ring g : films) b.add(g.copy()); b.dirtyAll = true; }
    }
    /** One tick of drift: downhill {@code gx, gy} (unit), {@code a} px mid-bath, across a bath {@code hw} wide either side of {@code cx, cy}. */
    static final class Drift extends Op {
        final double gx, gy, a, cx, cy, hw;
        Drift(double gx, double gy, double a, double cx, double cy, double hw) { this.gx = gx; this.gy = gy; this.a = a; this.cx = cx; this.cy = cy; this.hw = hw; }
        @Override void apply(FloatBath b) { b.drift(this); b.dirtyAll = true; b.movedAll = true; }
    }
    /** Outlines refined: where a batch of ops ends. */
    static final Op FLUSH = new Op() { @Override void apply(FloatBath b) { b.refine(); } };

    /** The rings, oldest first. */
    final List<Ring> rings = new ArrayList<>();
    /** The log: one list of ops per stroke. */
    private final List<List<Op>> strokes = new ArrayList<>();
    /** The rings as they were before the last stroke (its index), and every {@link #CHECKPOINT}th stroke. */
    private State before;
    private int beforeAt = -1;
    private final Map<Integer, State> checkpoints = new HashMap<>();
    /** Points in all, and the longest edge refining keeps (grows past {@link #CAP}). */
    int points;
    double maxSeg = MAX_SEG;
    /** Rings an op moved (refined at the next flush), or all of them. */
    private final IdentityHashMap<Ring, Boolean> dirty = new IdentityHashMap<>();
    private boolean dirtyAll;

    /** The rings and what refining depends on, kept for Undo. */
    private static final class State {
        final List<Ring> rings; final int points; final double maxSeg;
        State(FloatBath b) { rings = new ArrayList<>(b.rings.size()); for (Ring g : b.rings) rings.add(g.copy()); points = b.points; maxSeg = b.maxSeg; }
        void restore(FloatBath b) { b.rings.clear(); for (Ring g : rings) b.rings.add(g.copy()); b.points = points; b.maxSeg = maxSeg; b.dirty.clear(); b.dirtyAll = false; b.movedAll = true; }
    }

    // ---- The log ----

    /** A stroke begins: its ops are logged on it. */
    void begin() {
        int k = strokes.size();
        before = new State(this); beforeAt = k;
        if (k % CHECKPOINT == 0) checkpoints.put(k, before);
        strokes.add(new ArrayList<>());
    }
    /** How many strokes it holds. */
    int strokes() { return strokes.size(); }
    /** Applies {@code op} and logs it on the last stroke. */
    void apply(Op op) {
        op.apply(this);
        if (!strokes.isEmpty()) strokes.get(strokes.size() - 1).add(op);
    }
    /** Refines what the ops since the last flush moved, and logs that it did. */
    void flush() { if (dirtyAll || !dirty.isEmpty()) apply(FLUSH); }

    /**
     * Undo while it floats: the bath as it was before its last stroke, from the copy kept then, or replayed from the
     * last checkpoint before it. False with no stroke to take back.
     */
    boolean undoLast() {
        int n = strokes.size();
        if (n == 0) return false;
        if (beforeAt == n - 1 && before != null) before.restore(this);
        else {
            int k = n - 1;
            while (k > 0 && !checkpoints.containsKey(k)) k--;
            State s = checkpoints.get(k);
            if (s != null) s.restore(this); else { rings.clear(); points = 0; maxSeg = MAX_SEG; dirty.clear(); dirtyAll = false; movedAll = true; }
            for (int i = k; i < n - 1; i++) for (Op op : strokes.get(i)) op.apply(this);
        }
        strokes.remove(n - 1);
        for (Integer at : new ArrayList<>(checkpoints.keySet())) if (at > n - 1) checkpoints.remove(at);
        before = null; beforeAt = -1;
        return true;
    }
    /** The log replayed on a fresh bath (for the checks: the log is the bath). */
    FloatBath replay() {
        FloatBath b = new FloatBath();
        for (List<Op> ops : strokes) { b.begin(); for (Op op : ops) b.apply(op); }
        return b;
    }

    /** Floats {@code g} on top. */
    private void add(Ring g) { rings.add(g); points += g.pts; moved(g.box[0], g.box[1], g.box[2], g.box[3]); }

    // ---- The moves ----

    /** Every point pushed out by a drop of radius² {@code r2} at {@code cx, cy}. */
    private void drop(double cx, double cy, double r2) {
        movePoints(null, (xs, ys, from, to) -> {
            for (int i = from; i < to; i++) {
                double dx = xs[i] - cx, dy = ys[i] - cy, d2 = dx * dx + dy * dy;
                if (d2 < 1e-12) { dx = 1e-6; dy = 0; d2 = 1e-12; }
                double s = Math.sqrt(1 + r2 / d2);
                xs[i] = (float) (cx + dx * s); ys[i] = (float) (cy + dy * s);
            }
        });
    }

    /**
     * A pen's band pushes, in order. Points within {@code near} of their box take each in turn; points farther take one
     * drop of the summed area at their centre (the far field differs by under ~0.1 px).
     */
    private void pushes(double[] list, double near) {
        double x0 = Double.POSITIVE_INFINITY, y0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, y1 = Double.NEGATIVE_INFINITY, A = 0, cx = 0, cy = 0;
        for (int k = 0; k < list.length; k += 3) {
            double x = list[k], y = list[k + 1], a = list[k + 2];
            x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y);
            A += a; cx += x * a; cy += y * a;
        }
        if (!(A > 0)) return;
        double fA = A, fcx = cx / A, fcy = cy / A, lx = x0 - near, ly = y0 - near, hx = x1 + near, hy = y1 + near;
        // Rings whose box reaches the pen's are refined after.
        IdentityHashMap<Ring, Boolean> inBox = new IdentityHashMap<>();
        for (Ring g : rings) if (!(g.box[2] < lx || g.box[0] > hx || g.box[3] < ly || g.box[1] > hy)) { inBox.put(g, Boolean.TRUE); dirty.put(g, Boolean.TRUE); }
        movePoints(inBox, (xs, ys, from, to) -> {
            for (int i = from; i < to; i++) {
                double px = xs[i], py = ys[i];
                if (px >= lx && px <= hx && py >= ly && py <= hy) {
                    for (int k = 0; k < list.length; k += 3) {
                        double dx = px - list[k], dy = py - list[k + 1], d2 = dx * dx + dy * dy;
                        if (d2 < 1e-12) { dx = 1e-6; dy = 0; d2 = 1e-12; }
                        double f = Math.sqrt(1 + list[k + 2] / d2);
                        px = list[k] + dx * f; py = list[k + 1] + dy * f;
                    }
                } else {
                    double dx = px - fcx, dy = py - fcy, d2 = dx * dx + dy * dy;
                    if (d2 < 1e-12) { dx = 1e-6; dy = 0; d2 = 1e-12; }
                    double f = Math.sqrt(1 + fA / d2);
                    px = fcx + dx * f; py = fcy + dy * f;
                }
                xs[i] = (float) px; ys[i] = (float) py;
            }
        });
    }

    /**
     * The pen moved a → b: the points within reach of its tines ride the stylus's flow, in substeps under 0.3 σ, each
     * point on its own (so the order of the loops doesn't matter). Only rings whose box is within reach are visited.
     */
    private void rake(Rake op) {
        double len = Math.hypot(op.bx - op.ax, op.by - op.ay);
        if (len < 1e-9) return;
        double far = 0;
        for (double t : op.tines) far = Math.max(far, Math.abs(t));
        double reach = 4 * op.sig + far;
        double lx = Math.min(op.ax, op.bx) - reach, ly = Math.min(op.ay, op.by) - reach, hx = Math.max(op.ax, op.bx) + reach, hy = Math.max(op.ay, op.by) + reach;
        IdentityHashMap<Ring, Boolean> near = new IdentityHashMap<>();
        for (Ring g : rings) if (!(g.box[2] < lx || g.box[0] > hx || g.box[3] < ly || g.box[1] > hy)) { near.put(g, Boolean.TRUE); dirty.put(g, Boolean.TRUE); }
        if (near.isEmpty()) return;
        int n = (int) Math.min(600, Math.max(1, Math.ceil(len / (op.sig * .3))));
        double Ux = (op.bx - op.ax) / n, Uy = (op.by - op.ay) / n, is2 = 1 / (op.sig * op.sig), cut = 16 * op.sig * op.sig;
        double ml = Math.hypot(Ux, Uy), nx = -Uy / ml, ny = Ux / ml, slip = op.slip, ax = op.ax, ay = op.ay;
        double[] tines = op.tines;
        int T = tines.length;
        movePoints(near, (xs, ys, from, to) -> {
            for (int i = from; i < to; i++) {
                double px = xs[i], py = ys[i];
                if (px < lx || px > hx || py < ly || py > hy) continue;
                for (int s = 0; s < n; s++) {
                    double cx = ax + Ux * (s + .5), cy = ay + Uy * (s + .5), vx = 0, vy = 0;
                    for (int j = 0; j < T; j++) {
                        double qx = px - cx - nx * tines[j], qy = py - cy - ny * tines[j], r2 = qx * qx + qy * qy;
                        if (r2 > cut) continue;
                        double G = Math.exp(-.5 * r2 * is2), cr = Ux * qy - Uy * qx;
                        vx += G * (Ux - cr * qy * is2); vy += G * (Uy + cr * qx * is2);
                    }
                    px += vx * slip; py += vy * slip;
                }
                xs[i] = (float) px; ys[i] = (float) py;
            }
        });
    }

    /**
     * Drift: everything floats downhill, the middle of the bath (across the slope) faster than its sides, so rings shear
     * into the chevrons of a tray tipped while the ink floats. Divergence free: the speed depends only on the place
     * across the slope.
     */
    private void drift(Drift op) {
        double px = -op.gy, py = op.gx;
        movePoints(null, (xs, ys, from, to) -> {
            for (int i = from; i < to; i++) {
                double u = StrokeMath.clamp(((xs[i] - op.cx) * px + (ys[i] - op.cy) * py) / op.hw, -1, 1), c = Math.cos(u * Math.PI / 2);
                double f = .35 + .65 * c * c;
                xs[i] = (float) (xs[i] + op.gx * op.a * f); ys[i] = (float) (ys[i] + op.gy * op.a * f);
            }
        });
    }

    /** A move of points {@code from..to − 1} of one outline. */
    private interface Move { void run(float[] xs, float[] ys, int from, int to); }
    /**
     * Runs {@code move} over every point of the rings in {@code only} (null: all), in pieces over the cores when there are
     * many: each point moves on its own, so the result is the same on any number of cores.
     */
    private void movePoints(IdentityHashMap<Ring, Boolean> only, Move move) {
        List<float[]> xs = new ArrayList<>(), ys = new ArrayList<>();
        List<int[]> spans = new ArrayList<>();
        List<Ring> of = new ArrayList<>();
        int total = 0;
        for (Ring g : rings) {
            if (only != null && !only.containsKey(g)) continue;
            for (int c = 0; c < g.n.length; c++) {
                int n = g.n[c];
                for (int a = 0; a < n; a += PIECE) { xs.add(g.x[c]); ys.add(g.y[c]); spans.add(new int[]{a, Math.min(n, a + PIECE)}); of.add(g); }
                total += n;
            }
        }
        int pieces = spans.size();
        // Each piece's largest move and the box of its points that moved past MOVED (where they were and are).
        float[] most = new float[pieces], box = new float[4 * pieces];
        Cores.Piece work = (k, worker) -> {
            float[] X = xs.get(k), Y = ys.get(k), ox = oldX[worker], oy = oldY[worker];
            int a = spans.get(k)[0], b = spans.get(k)[1];
            System.arraycopy(X, a, ox, 0, b - a); System.arraycopy(Y, a, oy, 0, b - a);
            move.run(X, Y, a, b);
            float m = 0, l = Float.POSITIVE_INFINITY, t = Float.POSITIVE_INFINITY, r = Float.NEGATIVE_INFINITY, bt = Float.NEGATIVE_INFINITY;
            for (int i = a; i < b; i++) {
                float dx = X[i] - ox[i - a], dy = Y[i] - oy[i - a], d = Math.abs(dx) + Math.abs(dy);
                if (d > m) m = d;
                if (d > MOVED) {
                    l = Math.min(l, Math.min(X[i], ox[i - a])); r = Math.max(r, Math.max(X[i], ox[i - a]));
                    t = Math.min(t, Math.min(Y[i], oy[i - a])); bt = Math.max(bt, Math.max(Y[i], oy[i - a]));
                }
            }
            most[k] = m; box[4 * k] = l; box[4 * k + 1] = t; box[4 * k + 2] = r; box[4 * k + 3] = bt;
        };
        if (total < SHARE_POINTS || pieces < 2) for (int k = 0; k < pieces; k++) work.run(k, 0);
        else Cores.each(pieces, work);
        // A ring whose small moves have added up past MOVED shows again whole; else only where its points moved past it.
        IdentityHashMap<Ring, Float> rings = new IdentityHashMap<>();
        for (int k = 0; k < pieces; k++) {
            Ring g = of.get(k);
            Float was = rings.get(g);
            if (was == null || most[k] > was) rings.put(g, most[k]);
            if (box[4 * k] <= box[4 * k + 2]) moved(box[4 * k], box[4 * k + 1], box[4 * k + 2], box[4 * k + 3]);
        }
        for (java.util.Map.Entry<Ring, Float> e : rings.entrySet()) {
            Ring g = e.getKey();
            g.lag += e.getValue();
            if (g.lag > MOVED) { g.lag = 0; g.lagged = true; }
        }
    }
    /** Points to a piece of moving work, and each worker's copy of a piece's points before it moves. */
    private static final int PIECE = 2048;
    private final float[][] oldX = new float[Cores.WORKERS][PIECE], oldY = new float[Cores.WORKERS][PIECE];
    /** A move under this (page px, |dx| + |dy|) isn't shown until a ring's moves add up past it: a fifth of a pixel. */
    static final float MOVED = .2f;
    /** Where the bath has changed since it last showed ({left, top, right, bottom}, page px; null: nowhere), or everywhere. */
    private float[] moved;
    private boolean movedAll;
    private void moved(float l, float t, float r, float b) {
        if (moved == null) moved = new float[]{l, t, r, b};
        else { moved[0] = Math.min(moved[0], l); moved[1] = Math.min(moved[1], t); moved[2] = Math.max(moved[2], r); moved[3] = Math.max(moved[3], b); }
    }
    /**
     * Where the bath must show again ({left, top, right, bottom}, page px, padded by an edge's length; null: nowhere), and
     * from now on nowhere: what ops moved past {@link #MOVED} (before and after), rings whose small moves added up past it
     * (where they were and are), rings added; everywhere after a drift or an Undo ({@code null} with {@code all[0]} set).
     */
    float[] takeMoved(boolean[] all) {
        for (Ring g : rings) if (g.lagged) {
            g.lagged = false;
            if (g.shownBox[0] <= g.shownBox[2]) moved(g.shownBox[0], g.shownBox[1], g.shownBox[2], g.shownBox[3]);
            moved(g.box[0], g.box[1], g.box[2], g.box[3]);
        }
        for (Ring g : rings) System.arraycopy(g.box, 0, g.shownBox, 0, 4);
        all[0] = movedAll;
        float[] out = moved == null ? null : new float[]{moved[0] - 2 - (float) maxSeg, moved[1] - 2 - (float) maxSeg, moved[2] + 2 + (float) maxSeg, moved[3] + 2 + (float) maxSeg};
        moved = null; movedAll = false;
        return out;
    }

    // ---- Refining ----

    private float[] rx = new float[256], ry = new float[256];
    /** Splits long edges and merges points closer than {@link #MIN_SEG} on the rings ops moved; coarser past {@link #CAP} points. */
    private void refine() {
        if (dirtyAll) for (Ring g : rings) refine(g);
        else for (Ring g : rings) if (dirty.containsKey(g)) refine(g);
        dirtyAll = false; dirty.clear();
        if (points > CAP && maxSeg < 8) maxSeg *= 1.5;
    }
    private void refine(Ring g) {
        int before = g.pts, pts = 0;
        double maxSeg = this.maxSeg;
        for (int c = 0; c < g.n.length; c++) {
            float[] X = g.x[c], Y = g.y[c];
            int n = g.n[c], m = 0;
            if (n == 0) continue;
            float lx = X[0], ly = Y[0];
            m = put(m, lx, ly);
            for (int i = 1; i <= n; i++) {
                int j = i % n;
                float x = X[j], y = Y[j];
                double d = Math.hypot(x - lx, y - ly);
                if (d < MIN_SEG && i < n) continue;
                if (d > maxSeg) {
                    int k = (int) Math.ceil(d / maxSeg);
                    for (int q = 1; q < k; q++) m = put(m, (float) (lx + (x - lx) * (double) q / k), (float) (ly + (y - ly) * (double) q / k));
                }
                if (i < n) m = put(m, x, y);
                lx = x; ly = y;
            }
            if (X.length < m || X.length > 4 * m + 64) { X = new float[m + (m >> 2) + 8]; Y = new float[X.length]; g.x[c] = X; g.y[c] = Y; }
            System.arraycopy(rx, 0, X, 0, m); System.arraycopy(ry, 0, Y, 0, m);
            g.n[c] = m;
            pts += m;
        }
        g.box();
        points += pts - before;
    }
    private int put(int m, float x, float y) {
        if (m == rx.length) { rx = Arrays.copyOf(rx, m * 2); ry = Arrays.copyOf(ry, m * 2); }
        rx[m] = x; ry[m] = y;
        return m + 1;
    }

    /** The box every ring lies in, {left, top, right, bottom} in page px (unclipped), or null with none. */
    float[] box() {
        if (rings.isEmpty()) return null;
        float[] r = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (Ring g : rings) { r[0] = Math.min(r[0], g.box[0]); r[1] = Math.min(r[1], g.box[1]); r[2] = Math.max(r[2], g.box[2]); r[3] = Math.max(r[3], g.box[3]); }
        return r;
    }
    /** A hash of every point at 1/64 px (for the checks: two baths alike). */
    int hash() {
        int h = 0x811c9dc5;
        for (Ring g : rings) for (int c = 0; c < g.n.length; c++) for (int i = 0; i < g.n[c]; i++) {
            h = (h ^ Math.round(g.x[c][i] * 64)) * 16777619; h = (h ^ Math.round(g.y[c][i] * 64)) * 16777619;
        }
        return h;
    }
}
