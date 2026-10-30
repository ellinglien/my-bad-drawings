package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.lerp;

/**
 * Hatch (the new modules spec; newmods.js stampHatch): parallel lines instead of a solid mark, inside the band a round
 * tip of its weight would cover. Each pixel takes the lines of its nearest dab: one set; where pressure passes .45 a
 * second set at 90° to it; past .72 a third at 45° (up to Layers). Lines fixed: the angle is on the page, so strokes of
 * one brush line up and an overlap adds nothing (the tone comes from pressure, not from scrubbing); follows: the angle
 * turns with the stroke. Spacing 3–18 px and Line weight 0.9–4 px at a 30 px tip, both growing with the square root of
 * the size past that. Greys are built from crisp black lines; only their edges are in-between greys. The lines depend
 * only on the samples, so however they're batched, quick or live, the same lines land. Arithmetic as the lab's (sines
 * StrictMath's), so the same samples lay the same coverage. Pure, checked on the desktop.
 */
final class HatchStamper {
    private HatchStamper() { }

    /** Where pressure adds the second and third direction, and how far each turns from the first, degrees. */
    static final double[] AT = {0, .45, .72}, TURN = {0, 90, 45};

    /** One stroke's hatching, line or body: the next dab's distance, and each pixel's squared distance to its nearest dab so far. */
    static final class State {
        double next;
        /** On every pixel (a hatch is never on a lattice), as its buffers. */
        final FloatTiles best;
        State(int width, int height) { best = new FloatTiles(width, height, Float.POSITIVE_INFINITY); }
    }

    /** The lines' spacing and width, page px, at tip size {@code size}. */
    static double pitch(ResolvedBrush.Pattern P, double size) { return lerp(3, 18, P.pitch) * Math.max(1, Math.sqrt(size / 30)); }
    static double lineWidth(ResolvedBrush.Pattern P, double size) { return Math.min(pitch(P, size) * .75, lerp(.9, 4, P.lineW) * Math.max(1, Math.sqrt(size / 30))); }
    /** How far hatching reaches across, page px: its round band and the pixel of edge round it. */
    static double reach(double size) { return size + 3; }

    /**
     * stampHatch: the dabs from segment {@code from} on into {@code into} (every pixel: hatching is never on a lattice);
     * {@code wetD}, if any, takes each laid pixel's distance along the stroke. Returns the touched rectangle.
     */
    static int[] stamp(FloatTiles into, FloatTiles wetD, ResolvedBrush.Pattern P, StrokePoints pts, double size, int from, LineStamper.Meta meta) {
        int w = into.width, h = into.height;
        int[] rect = StrokeMath.emptyRect(w, h);
        if (meta.hatch == null) meta.hatch = new State(w, h);
        State M = meta.hatch;
        FloatTiles best = M.best;
        double pitch = pitch(P, size), lw = lineWidth(P, size);
        double[] sn = new double[3], cs = new double[3];
        for (int L = 0; L < 3; L++) { double a = (P.angle + TURN[L]) * LineStamper.DEG; sn[L] = StrictMath.sin(a); cs[L] = StrictMath.cos(a); }
        double r0 = size / 2, spacing = Math.max(1, r0 * .15);
        for (int q = Math.max(1, from); q < pts.count; q++) {
            int j = q - 1;
            double seg = (double)pts.d[q] - pts.d[j];
            double ux = !Float.isNaN(pts.ux[q]) ? pts.ux[q] : seg > 0 ? ((double)pts.x[q] - pts.x[j]) / seg : 1;
            double uy = !Float.isNaN(pts.uy[q]) ? pts.uy[q] : seg > 0 ? ((double)pts.y[q] - pts.y[j]) / seg : 0;
            while (M.next <= pts.d[q]) {
                double t = seg > 0 ? clamp((M.next - pts.d[j]) / seg) : 1;
                double x = lerp(pts.x[j], pts.x[q], t), y = lerp(pts.y[j], pts.y[q], t), pr = lerp(pts.p[j], pts.p[q], t), v = lerp(pts.v[j], pts.v[q], t), d = M.next;
                double r = P.band(r0, pr), dk = P.dark(pr, v) * pts.own(j, q, t);
                int layers = 1;
                while (layers < P.layers && pr >= AT[layers]) layers++;
                int x0 = Math.max(0, (int)Math.floor(x - r - 1)), x1 = Math.min(w - 1, (int)Math.ceil(x + r + 1));
                int y0 = Math.max(0, (int)Math.floor(y - r - 1)), y1 = Math.min(h - 1, (int)Math.ceil(y + r + 1));
                double R2 = (r + .5) * (r + .5);
                for (int py = y0; py <= y1; py++) {
                    double dy = py + .5 - y, room = R2 - dy * dy;
                    if (room < 0) continue;
                    // The row's pixels inside the disc (each still tested, as the lab's), a tile's run at a time.
                    double half = Math.sqrt(room);
                    int a = Math.max(x0, (int)Math.floor(x - half - .5)), b = Math.min(x1, (int)Math.ceil(x + half - .5));
                    int ty = best.tileOf(py), row = best.rowAt(py);
                    for (int px = a; px <= b; ) {
                        int tx = best.tileOf(px), end = Math.min(b, best.tileEnd(tx));
                        float[] near = best.tile(tx, ty, true), cover = null;
                        for (; px <= end; px++) {
                            double dx = px + .5 - x, d2 = dx * dx + dy * dy;
                            int i = row | best.at(px);
                            if (d2 > R2 || d2 >= near[i]) continue;
                            near[i] = (float)d2;
                            double X = P.fixed ? px + .5 : d + dx * ux + dy * uy, Y = P.fixed ? py + .5 : -dx * uy + dy * ux;
                            double cov = 0;
                            for (int L = 0; L < layers; L++) {
                                double m = (P.fixed ? X * sn[L] + Y * cs[L] : -X * sn[L] + Y * cs[L]) / pitch + L * .37, f = Math.abs(m - Math.round(m)) * pitch;
                                double c = clamp(lw / 2 + .5 - f);
                                if (c > cov) cov = c;
                            }
                            float out = (float)(cov * clamp(r + .5 - Math.sqrt(d2)) * dk);
                            if (cover == null) cover = into.tile(tx, ty, out != into.fill);
                            if (cover != null) cover[i] = out;
                            if (wetD != null && out > 0) wetD.set(px, py, (float)d);
                        }
                    }
                }
                if (x1 >= x0 && y1 >= y0) StrokeMath.grow(rect, x0, y0, x1, y1);
                M.next += spacing;
            }
        }
        return rect;
    }
}
