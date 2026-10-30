package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.List;

/**
 * A Float stroke's pen on the water (float.js Stroke's floatDown, holdTick, floatMove and floatLift): what it does to
 * the bath, as ops ({@link FloatBath.Op}) from its samples alone, so a quick stroke's render and the same samples drawn
 * live float the same.
 * <ul>
 * <li><b>Tap or drag, never both.</b> Within {@link #STILL} px of pen-down it is a tap (a drop at lift) or a hold (a drop
 * after {@link #HOLD_MS}, which keeps spreading: a tenth more area each 100 ms of pen time, up to four times). Past it,
 * a drag, and nothing drops: combing with clear water must not start with a clear hole.
 * <li><b>A drag</b> lays a drop every 0.6 of its film's half-width along the path, as much area as the band it lays
 * (times Own paint), so older paint is pushed out of the band before the film lands in the hole; and, with Smudge's
 * Drag or Smear, drags what floats by its tines (pull: Pick up × (.15 + .85 × pressure), MonoPaint's brush-pen pull).
 * <li><b>At lift</b> its line and body, as stamped, float as films ({@link FloatTrace}), each one flat grey.
 * </ul>
 * A drop's edge is a little uneven, and its size varies a little, from the brush's seed and the drop's place: real drops
 * never spread as perfect circles, and rings of one width in turn read as drawn. Pure.
 */
final class FloatPen {
    /** Within this of pen-down, page px, a tap or a hold; past it, a drag. */
    static final double STILL = 3;
    /** Held still this long, ms of pen time, the pen drops. */
    static final long HOLD_MS = 150;
    /** How much a drop's size varies (±, a share of its radius), and how uneven its edge is (each of three waves, a share). */
    static final double SIZE_JITTER = .14, EDGE_WOBBLE = .04;

    private final ResolvedBrush r;
    private final ResolvedBrush.Float f;
    private final FloatBath bath;
    private final long seed;
    private final double sp, area, near;
    private boolean dropped, dragging;
    /** The drop's radius², how far it has grown (1: as dropped), the distance stepped along the stroke, the last step's place. */
    private double r2, grown = 1, cd, prevX, prevY;

    FloatPen(ResolvedBrush r, FloatBath bath, long seed) {
        this.r = r; this.f = r.flt; this.bath = bath; this.seed = seed;
        sp = Math.max(2, f.filmW * .6);
        area = f.clear() ? 0 : 2 * f.filmW * sp * f.load / Math.PI;
        near = 8 * f.filmW + 40;
    }

    /** float.js dropRadius: how big a tap's drop is, from the film's half-width, Spread and pressure; 4 to 240 px before pressure. */
    static double dropRadius(double w, double spread, double p) {
        return StrokeMath.clamp((8 + 3 * w) * (.4 + 1.6 * spread), 4, 240) * Math.sqrt(.4 + .6 * StrokeMath.clamp(p));
    }
    /** float.js floatPull and floatSlip: how far the pen drags floating paint at pressure {@code p}, and the slip that bounds each point's carry. */
    static double pull(double pick, double p) { return pick * (.15 + .85 * StrokeMath.clamp(p)); }
    static double slip(double pull) { return .45 + .5 * pull; }

    /**
     * The pen at sample time {@code time} (every sample, kept or not: a pen held still sends samples too close to keep),
     * the stroke's samples {@code pts}: a hold drops, and keeps spreading.
     */
    void held(StrokePoints pts, long time) {
        if (dragging || pts.count == 0 || time == PenSpeed.UNTIMED || pts.t[0] == PenSpeed.UNTIMED) return;
        if (Math.hypot(pts.x[pts.count - 1] - pts.x[0], pts.y[pts.count - 1] - pts.y[0]) > STILL) return;
        long held = time - pts.t[0];
        if (!dropped) { if (held < HOLD_MS) return; drop(pts); }
        double want = Math.min(4, 1 + .1 * Math.max(0, held - HOLD_MS) / 100);
        if (want - grown < .05) return;
        bath.apply(new FloatBath.Drop(pts.x[0], pts.y[0], (want - grown) * r2, 0, false, null));
        grown = want;
        bath.flush();
    }

    /** The samples from {@code from} on are new: once past {@link #STILL}, a drag pushes aside and combs. */
    void moved(StrokePoints pts, int from) {
        if (!dragging) {
            if (Math.hypot(pts.x[pts.count - 1] - pts.x[0], pts.y[pts.count - 1] - pts.y[0]) <= STILL) return;
            // A drag: stepped along the whole stroke from pen-down.
            dragging = true; prevX = pts.x[0]; prevY = pts.y[0]; cd = 0; from = 1;
        }
        for (int k = Math.max(1, from); k < pts.count; k++) {
            double d0 = pts.d[k - 1], d1 = pts.d[k], seg = d1 - d0;
            while (cd + sp <= d1) {
                cd += sp;
                double t = seg > 0 ? StrokeMath.clamp((cd - d0) / seg) : 1;
                double x = StrokeMath.lerp(pts.x[k - 1], pts.x[k], t), y = StrokeMath.lerp(pts.y[k - 1], pts.y[k], t), pr = StrokeMath.lerp(pts.p[k - 1], pts.p[k], t);
                double pull = pull(f.pick, pr);
                if (pull > 0) {
                    flushPushes();
                    bath.apply(new FloatBath.Rake(prevX, prevY, x, y, f.sig, f.tines, slip(pull)));
                    steps++;
                }
                if (area > 0) { pushes.add(x); pushes.add(y); pushes.add(area); steps++; }
                prevX = x; prevY = y;
            }
        }
        // Every move visits each point floating, and refining each ring it reached: done every STEPS steps, not every
        // sample, so a drag through a rich bath keeps up with the pen (the owner, 2026-10-22: "it gets overloaded").
        if (steps < STEPS) return;
        flushPushes();
        bath.flush();
        steps = 0;
    }
    /** How many of a drag's steps (pushes and combs) are moved and refined together; the pushes waiting, and the steps since. */
    static final int STEPS = 8;
    private final List<Double> pushes = new ArrayList<>();
    private int steps;
    private void flushPushes() {
        if (pushes.isEmpty()) return;
        double[] list = new double[pushes.size()];
        for (int i = 0; i < list.length; i++) list[i] = pushes.get(i);
        pushes.clear();
        bath.apply(new FloatBath.Pushes(list, near));
    }

    /** A tap, or a hold the pen lifted from: the brush's own grey (clear water's is 0), pushing everything floating outward. */
    private void drop(StrokePoints pts) {
        dropped = true;
        double x = pts.x[0], y = pts.y[0], p = pts.p[0];
        // A drop's size and edge vary a little with its place, from the brush's seed.
        int h = (int) seed ^ (int) Math.round(x * 4) * 0x9e3779b1 ^ (int) Math.round(y * 4) * 0x85ebca6b;
        double rho = dropRadius(f.filmW, f.spread, p) * (1 + SIZE_JITTER * (2 * StrokeMath.hash01(h, 1) - 1));
        double[] wobble = new double[9];
        for (int j = 0; j < 3; j++) {
            wobble[3 * j] = EDGE_WOBBLE * (.4 + .6 * StrokeMath.hash01(h, 2 + j)) / (1 + j * .5);
            wobble[3 * j + 1] = new int[]{2, 3, 5}[j];
            wobble[3 * j + 2] = 2 * Math.PI * StrokeMath.hash01(h, 6 + j);
        }
        r2 = rho * rho;
        // Pressure lightens a tap through the hand's darkness, as it lightens a line.
        float dark = f.clear() ? 0 : (float) StrokeMath.clamp(f.dropDark * StrokeMath.handDark(r.pen.pDark, 0, 0, p, 0, 0));
        bath.apply(new FloatBath.Drop(x, y, r2, dark, true, wobble));
        bath.flush();
    }

    /**
     * How far from the pen its moves show, page px: the tines' reach, the band it pushes aside, and a held drop's
     * spread (its pushes reach far, faintly; the rest shows at lift).
     */
    double reach() {
        double far = 0;
        for (double t : f.tines) far = Math.max(far, Math.abs(t));
        double drop = dropped ? 2.5 * Math.sqrt(r2 * grown) : 0;
        return Math.max(Math.max(4 * f.sig + far, 3 * f.filmW + 8), drop);
    }

    /**
     * The pen lifted: a tap drops now; a drag's line and body float as films over the stroke's lattice box
     * {@code box} (page px, inclusive), each one flat grey, the body under the line.
     */
    void lift(StrokePoints pts, FloatTiles line, FloatTiles body, int[] box) {
        if (pts.count == 0) return;
        flushPushes();
        if (!dragging && !dropped) drop(pts);
        if (dragging && !f.clear() && box != null && !StrokeMath.empty(box)) {
            List<FloatBath.Ring> films = new ArrayList<>();
            film(films, body, r.washK, 1.6, true, box);
            film(films, line, r.inkK, 1, false, box);
            if (!films.isEmpty()) bath.apply(new FloatBath.Film(films));
        }
        bath.flush();
    }
    /**
     * {@code buffer}'s film (null: none) traced at half coverage, its grey the mean darkness inside it (each at most
     * {@code cap}) times {@code k}: at most black for a line; a body's as that strength lays ({@link ModularSizes#laid}).
     */
    private static void film(List<FloatBath.Ring> films, FloatTiles buffer, double k, double cap, boolean body, int[] box) {
        if (buffer == null || !(k > 0)) return;
        int s = buffer.shift;
        double[] mean = new double[2];
        List<float[][]> loops = FloatTrace.trace(buffer, box[0] >> s, box[1] >> s, box[2] >> s, box[3] >> s, .5, cap, mean);
        if (mean[1] < 3 || loops.isEmpty()) return;
        float[][] xs = new float[loops.size()][], ys = new float[loops.size()][];
        int[] n = new int[loops.size()];
        for (int i = 0; i < loops.size(); i++) { xs[i] = loops.get(i)[0]; ys[i] = loops.get(i)[1]; n[i] = xs[i].length; }
        films.add(new FloatBath.Ring(xs, ys, n, (float) (body ? ModularSizes.laid(mean[0] * k) : StrokeMath.clamp(mean[0] * k))));
    }
}
