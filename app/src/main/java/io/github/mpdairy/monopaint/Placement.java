package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.lerp;

/**
 * Lines placed inside the body (engine.js 475–602): split nib, multi-line, outline, squiggle, Offset and Chaos, with
 * a Fast-wet window, the Fast taper and the hand's darkness. Keeps the strongest coverage per pixel. Pure, checked
 * on the desktop.
 */
final class Placement {
    /** Bristles a placed line reads: the first of the line's own ({@link LineStamper#bristles}). */
    private static final int BRISTLES = 48;

    /** Each placed line's load, width and paper phase, and the squiggle's two phases: draws seeded by the brush. */
    static final class Draws {
        final double[] loads = new double[5], widths = new double[5], phaseX = new double[5], phaseY = new double[5];
        final double sq0, sq1;

        Draws(long seed) {
            WildBrush.Random lr = new WildBrush.Random(seed ^ 0x11e5);
            for (int j = 0; j < 5; j++) { loads[j] = .8 + .2 * lr.next(); widths[j] = .85 + .3 * lr.next(); phaseX[j] = lr.next() * 997; phaseY[j] = lr.next() * 991; }
            sq0 = lr.next() * 6.283; sq1 = lr.next() * 6.283;
        }
    }

    /**
     * Where placed lines lie (engine.js 475–602 without the pixels): a stroke's constants, then {@link #at} a step's shared
     * values and {@link #line} each line's ellipse there. {@link #stamp} lays them; a quick stroke's preview sweeps them
     * ({@link QuickStroke}), so its footprint is the lines' own.
     */
    static final class Geometry {
        final StrokePaper paper; final ResolvedBrush.Pen b; final ResolvedBrush.Body body; final StrokePoints pts;
        final double size, bodySize, total; final boolean last; final LineStamper.Window win; final int w, h;
        final double[] loads, widths, phaseX, phaseY;
        final double cosA, sinA, inLen, outLen, bIn, bOut, chaos, sub, reach, bodyRef, lambda, swing, sq0, sq1;
        final ResolvedBrush.Lines kind; final int n, sd;
        /** The step ({@link #at}): where, how far along, the pen there, the line's width, the body's edge, the split's gap. */
        double dirx, diry, x, y, d, pr, v, wd, rB, nx, ny, edge, g, drift, load, dk; boolean dry;
        /** Line {@code j}'s ({@link #line}): its centre, width, ink and ellipse's half-axes. */
        double cx, cy, wj, ink, a, bb;

        Geometry(StrokePaper paper, ResolvedBrush.Pen b, ResolvedBrush.Body body, StrokePoints pts, double size, double bodySize, boolean last, LineStamper.Window win, int w, int h) {
            this.paper = paper; this.b = b; this.body = body; this.pts = pts; this.size = size; this.bodySize = bodySize; this.last = last; this.win = win; this.w = w; this.h = h;
            int count = pts.count;
            total = count > 0 ? pts.d[count - 1] : 0;
            Draws drawn = b.placed();
            loads = drawn.loads; widths = drawn.widths; phaseX = drawn.phaseX; phaseY = drawn.phaseY;
            sq0 = drawn.sq0; sq1 = drawn.sq1;
            cosA = Math.cos(b.angle * Math.PI / 180); sinA = Math.sin(b.angle * Math.PI / 180);
            inLen = b.taperIn * 70; outLen = b.taperOut * 90;
            kind = b.lines;
            double grw = Math.max(1, bodySize / 24);
            bIn = body.taperIn * 70 * grw; bOut = body.taperOut * 90 * grw;
            n = kind == ResolvedBrush.Lines.SPREAD ? b.count : kind == ResolvedBrush.Lines.OUTLINE ? 2 : 1;
            chaos = b.chaos;
            sd = (int)b.seed;
            sub = kind == ResolvedBrush.Lines.SPREAD ? .7 : kind == ResolvedBrush.Lines.OUTLINE ? .6 : kind == ResolvedBrush.Lines.SQUIGGLE ? .85 : 1;
            reach = lerp(.3, .95, b.spread); bodyRef = paintWidth(body, bodySize, .7) / 2;
            lambda = Math.max(30, bodyRef * 3);
            swing = (kind == ResolvedBrush.Lines.SQUIGGLE ? 1 + 6.283 * reach * bodyRef / lambda * (1 + 2 * chaos) : 1) * (1 + chaos);
        }

        /** How many steps the segment from sample {@code k − 1} to {@code k} takes. */
        int steps(int k) {
            int i0 = k - 1;
            double seg = (double)pts.d[k] - pts.d[i0];
            dirx = seg > 0 ? ((double)pts.x[k] - pts.x[i0]) / seg : 1; diry = seg > 0 ? ((double)pts.y[k] - pts.y[i0]) / seg : 0;
            return Math.max(1, (int)Math.ceil(seg * swing / Math.max(0.4, size * sub * 0.1)));
        }

        /** The step at {@code t} of the segment to sample {@code k} (after {@link #steps}): false where nothing is laid. */
        boolean at(int k, double t) {
            int i0 = k - 1;
            x = lerp(pts.x[i0], pts.x[k], t); y = lerp(pts.y[i0], pts.y[k], t); d = lerp(pts.d[i0], pts.d[k], t);
            if (win != null && (d < win.dMin || d >= win.dMax)) return false;
            if (d < b.skipStart || (last && d > total - b.skipEnd)) return false;
            pr = lerp(pts.p[i0], pts.p[k], t); v = lerp(pts.v[i0], pts.v[k], t);
            double open = StrokeMath.breath(b.breathe, pr);
            wd = b.width(size, pr, v);
            if (inLen > 0) wd *= StrokeMath.taper(d / inLen);
            if (b.fast) {
                if (b.taperOut > 0) { double fall = pts.pk[i0] > 0 ? clamp(pr / (pts.pk[i0] * 0.7)) : 1; wd *= lerp(1, Math.pow(fall, 0.8) * 0.92 + 0.08, b.taperOut); }
            } else if (last && outLen > 0) wd *= StrokeMath.taper((total - d) / outLen);
            if (win != null && win.tail) wd *= lerp(1, StrokeMath.taper((total - d) / win.tailLen), win.tailAmt);
            // Flick: past the lift the lines and their body narrow to nothing and run dry (FlickFling).
            double flick = pts.flicks() ? pts.flickWidth(d) : 1, flickDry = pts.flicks() ? pts.flickDry(d) : 0;
            wd *= flick;
            if (b.pool > 0) { double still = 1 - clamp(v / 0.25); wd *= 1 + b.pool * 0.9 * (still * still); }
            double ga = b.gSize != 0 || b.gDark != 0 ? pts.ga(i0, k, t) : 0;
            if (ga != 0) wd *= StrokeMath.gSize(b.gSize, ga);
            double tf = 1;
            if (bIn > 0) tf *= StrokeMath.taper(d / bIn);
            if (last && bOut > 0) tf *= StrokeMath.taper((total - d) / bOut);
            tf *= flick;
            rB = Math.max(0.8, StrokeMath.mix(bodyRef, paintWidth(body, bodySize, pr) / 2, b.breathe) * tf);
            nx = -diry; ny = dirx;
            double an = nx * cosA + ny * sinA, pn = -nx * sinA + ny * cosA;
            double ea = rB * an, eb = rB * body.aspect * pn;
            edge = Math.sqrt(ea * ea + eb * eb);
            g = kind == ResolvedBrush.Lines.SPLIT ? b.spread * open * (chaos > 0 ? 1 + chaos * (2 * cn(sd, 9, 1, d / 60) - 1) * .7 : 1) : 0;
            drift = 0;
            if (b.offset > 0) drift = b.offset * (edge * 1.6 + size * 2.5) * clamp((2 * StrokeMath.noise1(sd ^ 0x0ff5e7, d / (60 + bodyRef * 2)) - 1) * 1.8, -1, 1);
            if (g > 0) wd *= 1 + .9 * g;
            load = b.load(d, v, pr) - flickDry;
            dry = b.dry > 0 || flickDry > 0;
            dk = StrokeMath.handDark(b.pDark, b.sDark, b.tDark, pr, v, 0);
            if (ga != 0) dk *= StrokeMath.gDark(b.gDark, ga);
            // Paint Load: what of its own paint the brush still lays here (LoadedPaint).
            if (pts.own != null) dk *= pts.own(i0, k, t);
            return true;
        }

        /** Line {@code j} at the step ({@link #at}): false where Chaos skips it or an outline runs dry. */
        boolean line(int j) {
            cx = x + nx * drift; cy = y + ny * drift; wj = wd * sub * (n > 1 ? widths[j] : 1); ink = (n > 1 ? loads[j] : 1) * dk;
            if (chaos > 0) {
                if (cn(sd, j, 5, d / (14 + size * 2)) < chaos * .34) return false;
                wj *= Math.max(.25, 1 + chaos * .9 * (2 * cn(sd, j, 3, d / 30) - 1));
                ink *= 1 - chaos * .45 * cn(sd, j, 4, d / 45);
                double jit = chaos * (edge * .55 + size) * (2 * cn(sd, j, 2, d / 38) - 1);
                cx += nx * jit; cy += ny * jit;
            }
            double lw = wj / 2;
            if (kind == ResolvedBrush.Lines.SPREAD) {
                double u = (j - (n - 1) / 2.0) / Math.max(1, (n - 1) / 2.0) * Math.max(0, rB * reach - lw);
                if (chaos > 0) u *= 1 + chaos * .8 * (2 * cn(sd, j, 1, d / 50) - 1);
                cx += cosA * u; cy += sinA * u;
            } else if (kind == ResolvedBrush.Lines.OUTLINE) {
                double off = Math.max(0, edge * .97 - lw * .8) * (j != 0 ? 1 : -1);
                cx += nx * off; cy += ny * off;
                double thin = clamp((edge - wd * .9) / (wd * 1.5));
                int xi = StrokeMath.index((int)cx, w), yi = StrokeMath.index((int)cy, h);
                ink *= 1 - (1 - thin) * b.wetGap * (.2 + .6 * clamp((.5 - paper.low(xi, yi)) * 7));
                wj *= 1 - .3 * (1 - thin) * b.wetGap;
                if (ink <= .02) return false;
            } else if (kind == ResolvedBrush.Lines.SQUIGGLE) {
                double ph = 6.283 * (d / lambda + chaos * 1.6 * StrokeMath.noise1(sd ^ 0x5a1, d / (lambda * 1.4))) + sq0 + .9 * (1 - chaos) * Math.sin(d / lambda * 1.7 + sq1);
                double off = Math.max(0, edge * reach - lw) * Math.sin(ph) * (1 - chaos * .6 * cn(sd, 0, 6, d / (lambda * .9)));
                cx += nx * off; cy += ny * off;
            }
            if (n > 1 || kind == ResolvedBrush.Lines.SQUIGGLE || chaos > 0) {
                int px0 = StrokeMath.index((int)((cx + phaseX[j]) % w), w), py0 = StrokeMath.index((int)((cy + phaseY[j]) % h), h);
                double m = paper.mid(px0, py0), jit = (.2 + b.wobble) * (m - .5) * size * .5;
                cx += nx * jit; cy += ny * jit;
                wj *= 1 + b.wobble * 0.7 * (m - 0.5);
            } else if (b.wobble > 0) wj *= 1 + b.wobble * 0.7 * (paper.mid(StrokeMath.index((int)x, w), StrokeMath.index((int)y, h)) - 0.5);
            a = Math.max(0.6, wj / 2); bb = Math.max(0.6, Math.min(a, Math.max(1, a * b.aspect)));
            return true;
        }
    }

    /** stampPlaced. wetD: the Fast-wet distance map or null; wetGap: the body buffer the split's wet gap goes to, or null. */
    static int[] stamp(FloatTiles into, FloatTiles wetD, FloatTiles wetGap, StrokePaper paper, ResolvedBrush.Pen b, ResolvedBrush.Body body,
                       StrokePoints pts, double size, double bodySize, boolean last, int from, LineStamper.Window win) {
        int w = into.width, h = into.height, count = pts.count, step = into.step();
        double[] bristles = b.bristles();
        Geometry gm = new Geometry(paper, b, body, pts, size, bodySize, last, win, w, h);
        double cosA = gm.cosA, sinA = gm.sinA;
        int n = gm.n;
        int[] rect = StrokeMath.emptyRect(w, h);
        StrokeMath.Span span = new StrokeMath.Span();
        for (int k = Math.max(1, from); k < count; k++) {
            int steps = gm.steps(k);
            double dirx = gm.dirx, diry = gm.diry;
            for (int s = k == 1 ? 0 : 1; s <= steps; s++) {
                double t = (double)s / steps;
                if (!gm.at(k, t)) continue;
                double g = gm.g, load = gm.load, d = gm.d;
                boolean dry = gm.dry;
                for (int j = 0; j < n; j++) {
                    if (!gm.line(j)) continue;
                    double cx = gm.cx, cy = gm.cy, ink = gm.ink, a = gm.a, bb = gm.bb;
                    int nBr = (int)Math.min(BRISTLES, (g > 0 ? 4 : 6) + Math.round(a));
                    int R = (int)Math.ceil(a + 1.5);
                    int x0 = Math.max(0, (int)Math.floor(cx - R)), x1 = Math.min(w - 1, (int)Math.ceil(cx + R));
                    int y0 = Math.max(0, (int)Math.floor(cy - R)), y1 = Math.min(h - 1, (int)Math.ceil(cy + R));
                    // Each row visits only the span of an ellipse that holds every pixel the line can cover: as LineStamper's
                    // without a soft edge, coverage needs dist < 1 + 0.5 / bb.
                    double grown = 1 + 0.5 / bb;
                    span.set(a * grown + 1, bb * grown + 1, cosA, sinA, cx, cy);
                    // The buffer's lattice: every pixel, or a big stroke's even ones.
                    for (int py = into.up(y0); py <= y1; py += step) {
                        if (!span.row(py, x0, x1)) continue;
                        int xa = span.left, xb = span.right;
                        int row = into.rowAt(py), ty = into.tileOf(py);
                        for (int px = into.up(xa); px <= xb; ) {
                            int tx = into.tileOf(px), end = Math.min(xb, into.tileEnd(tx));
                            float[] tile = into.tile(tx, ty, false);
                            for (; px <= end; px += step) {
                                int i = row | into.at(px);
                                float now = tile == null ? into.fill : tile[i];
                                if (now >= ink) continue;
                                double dx = px + 0.5 - cx, dy = py + 0.5 - cy;
                                double u = dx * cosA + dy * sinA, q = -dx * sinA + dy * cosA;
                                double dist = Math.sqrt((u / a) * (u / a) + (q / bb) * (q / bb));
                                double gu = u / (a * a), gq = q / (bb * bb), gr = Math.sqrt(gu * gu + gq * gq) / Math.max(dist, 1e-6);
                                double cov = clamp(0.5 - (dist - 1) / Math.max(gr, 1e-6));
                                if (cov <= 0) continue;
                                double across = (-dx * diry + dy * dirx) / a;
                                if (g > 0) {
                                    double gp = clamp((g * .72 - Math.abs(across)) / .14);
                                    if (gp > 0 && wetGap != null && b.wetGap > 0) wetGap.raise(px, py, (float)(cov * gp * b.wetGap * .8));
                                    cov *= 1 - gp;
                                    if (cov <= 0) continue;
                                }
                                if (dry) {
                                    if (step == 1) { if (bristles[(int)(clamp((across + 1) / 2, 0, .9999) * nBr)] > load + 0.25 * (paper.fine(px, py) - 0.5)) continue; }
                                    else {
                                        // On the lattice: the share of the point's four pixels the bristles leave dry (as LineStamper's).
                                        int wet = 0;
                                        for (int sy = 0; sy < 2; sy++) for (int sx = 0; sx < 2; sx++)
                                            if (bristles[(int)(clamp(((-(dx + sx) * diry + (dy + sy) * dirx) / a + 1) / 2, 0, .9999) * nBr)] <= load + 0.25 * (paper.fine(px + sx, py + sy) - 0.5)) wet++;
                                        if (wet == 0) continue;
                                        cov *= wet / 4.0;
                                    }
                                }
                                cov *= ink;
                                if (cov > now) {
                                    if (tile == null) tile = into.tile(tx, ty, true);
                                    tile[i] = (float)cov;
                                }
                                if (wetD != null && cov > 0) wetD.set(px, py, (float)d);
                            }
                        }
                    }
                    StrokeMath.grow(rect, x0, y0, x1, y1);
                }
            }
        }
        if (b.fast && b.splatter > 0) LineStamper.splatterLive(into, b, pts, size, from, rect);
        else if (last && b.splatter > 0) LineStamper.splatter(into, b, pts, size, rect);
        return rect;
    }

    /** port.js paintWidth: size × (tip + (1 − tip) · p^1.2), tip = lerp(.6, .06, pressure). */
    static double paintWidth(ResolvedBrush.Body body, double size, double p) {
        double tip = lerp(0.6, 0.06, body.pressure);
        return size * (tip + (1 - tip) * Math.pow(clamp(p), 1.2));
    }

    /** The cross-noise of line {@code j}, channel {@code c} (engine.js cn): seeds wrap as the prototype's {@code >>> 0}. */
    private static double cn(int sd, int j, int c, double x) { return StrokeMath.noise1(sd + j * 1013 + c * 7919, x); }

    private Placement() {}
}
