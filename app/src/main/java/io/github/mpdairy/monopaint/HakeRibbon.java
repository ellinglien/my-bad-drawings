package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.lerp;

/**
 * The hake: a wide flat head swept as a ribbon (engine.js 336–473). The head follows the stroke or stays at its
 * angle, turns at most a degree per pixel, pivots round tight turns, and lays the ribbon between heads, so a
 * quick stroke leaves no gaps. Keeps the strongest coverage per pixel. Sines and cosines are StrictMath's, as in
 * {@link BodyStamper}: the head's angle carries from head to head, so a last-bit difference would grow. A stamp's
 * heads and ribbons are worked out first, then laid in bands of tile rows, each band in their order, a big stamp's on
 * several cores ({@link Cores}): a pixel sees the same writes in the same order. Pure.
 */
final class HakeRibbon {
    /** What a stroke's hake remembers between stamps: the next head's distance, the last head, its angle and travel. */
    static final class State { double next; Head prev; double th = Double.NaN, travel = Double.NaN, start = Double.NaN, settle; int k = 1; }
    /** One head: centre, angle, half-width W, thickness Q, distance, pressure, and the angle's cosine and sine. */
    /** A head: where, its turn, half-width, thickness, distance, pressure and turn's cosine and sine; {@code own}, the paint left there (Paint Load; 1 otherwise). */
    static final class Head { double x, y, th, w, q, d, pr, c, s, own = 1; }

    /** A head's thickness at pressure {@code pr}, {@code size} the hake's: the quick preview's ({@link QuickStroke}) too. */
    static double thickness(ResolvedBrush.Body b, double size, double pr) {
        return Math.max(1.5, size * (0.03 + 0.05 * Math.pow(clamp(pr), 1.2)) * (1 + 0.8 * b.pWidth * (pr - 0.4)));
    }
    /**
     * The angle a head turns toward, travelling at angle {@code travel}: across the stroke, or the head's own angle when
     * fixed, turned toward the pen's lean by tilt ({@code azimuth}: NaN without a reading). The quick preview's head
     * ({@link QuickStroke}) takes it as is, without the turn's easing.
     */
    static double target(ResolvedBrush.Body b, double travel, double azimuth) {
        double target = !b.headFixed ? travel + Math.PI / 2 : b.angle * LineStamper.DEG;
        if (b.tilt > 0 && !Double.isNaN(azimuth)) target = StrokeMath.turnToward(target, azimuth + Math.PI / 2, b.tilt);
        return target;
    }

    /**
     * How far a head that follows the stroke waits, page px, before it lays its start: until the stroke's direction has
     * settled (the owner, 2026-10-22: "the hake brush struggles to find its direction and often leaves a mark that doesn't
     * match at the beginning"). A pen lands with a wobble or a little hook, and the first few px point anywhere; a real
     * flat brush lands already lying along the way it goes. A third of the hake's size, kept between 24 and 40 px (about
     * 2 and 3.5 mm on the Manta).
     */
    static double settle(double size) { return Math.max(24, Math.min(40, size * 0.3)); }
    /**
     * The direction (radians) the stroke's first {@code upTo} px settle on: from a quarter of the way along to the end,
     * so the landing's wobble and hook are left out; NaN for a stroke that hasn't moved.
     */
    static double startTravel(StrokePoints pts, double upTo) {
        double[] a = pointAt(pts, upTo * .25), z = pointAt(pts, upTo);
        double dx = z[0] - a[0], dy = z[1] - a[1];
        if (Math.hypot(dx, dy) < 1e-6) { dx = z[0] - pts.x[0]; dy = z[1] - pts.y[0]; }
        return Math.hypot(dx, dy) < 1e-6 ? Double.NaN : Math.atan2(dy, dx);
    }
    /** The stroke's point {@code at} px along it. */
    private static double[] pointAt(StrokePoints pts, double at) {
        int n = pts.count, k = 1;
        if (n < 2) return new double[]{pts.x[0], pts.y[0]};
        while (k < n - 1 && pts.d[k] < at) k++;
        double seg = (double)pts.d[k] - pts.d[k - 1], t = seg > 0 ? clamp((at - pts.d[k - 1]) / seg) : 1;
        return new double[]{lerp(pts.x[k - 1], pts.x[k], t), lerp(pts.y[k - 1], pts.y[k], t)};
    }
    /**
     * The travel a following head turns by at {@code d}: the settled {@code start} over the first {@code settle} px, then
     * eased into the {@code measured} one over as far again, so the start lies straight and the head never jumps; the
     * measured one alone without a start (NaN).
     */
    static double travelAt(double start, double settle, double d, double measured) {
        if (Double.isNaN(start) || d >= 2 * settle) return measured;
        if (d <= settle) return start;
        double dt = measured - start;
        dt = Math.atan2(Math.sin(dt), Math.cos(dt));
        return start + dt * (d - settle) / settle;
    }

    /**
     * stampHake: lays heads from the state's next distance to the stroke's end. wetD: the Fast-wet distance map or null.
     * {@code last}: the stroke is lifting, so a start still waiting for its direction ({@link #settle}) is laid now.
     */
    static int[] stamp(FloatTiles into, FloatTiles wetD, StrokePaper paper, ResolvedBrush.Body b, StrokePoints pts, double size, LineStamper.Meta meta, double softK, boolean last) {
        int n = pts.count;
        State H;
        if (meta == null) H = new State();
        else { if (meta.hake == null) meta.hake = new State(); H = meta.hake; }
        int[] rect = StrokeMath.emptyRect(into.width, into.height);
        if (n < 2) return rect;
        double total = pts.d[n - 1];
        boolean follow = !b.headFixed;
        Lay lay = new Lay(into, wetD, paper, b, b.hairs(), softK, rect);
        // What to lay, in order: a head alone (B null) or a ribbon from A to B.
        java.util.ArrayList<Head[]> laid = new java.util.ArrayList<>();
        if (Double.isNaN(H.th)) {
            double need = Math.max(6, size * 0.05);
            if (total < need) return rect;
            // A following head waits until the way the stroke goes has settled, then lays its start along it, from the
            // first sample: nothing is laid before, so nothing is laid at a guess. Its first angle is the one it would
            // turn to there, the pen's lean (tilt) included.
            if (follow) {
                double settle = settle(size);
                if (total < settle && !last) return rect;
                H.settle = settle;
                H.start = startTravel(pts, Math.min(settle, total));
                if (Double.isNaN(H.start)) H.start = 0;
            }
            H.th = target(b, follow ? H.start : 0, BrushDirection.azimuth(pts.tx[0], pts.ty[0]));
        }
        while (H.next <= total) {
            double at = H.next;
            while (H.k < n - 1 && pts.d[H.k] < at) H.k++;
            int i0 = H.k - 1, i1 = H.k;
            double seg = (double)pts.d[i1] - pts.d[i0], t = seg > 0 ? clamp((at - pts.d[i0]) / seg) : 1;
            double x = lerp(pts.x[i0], pts.x[i1], t), y = lerp(pts.y[i0], pts.y[i1], t), pr = lerp(pts.p[i0], pts.p[i1], t);
            double tx = lerp(pts.tx[i0], pts.tx[i1], t), ty = lerp(pts.ty[i0], pts.ty[i1], t);
            double azimuth = BrushDirection.azimuth(tx, ty);
            double D = BodyStamper.diameter(b, size, pr, BrushDirection.tiltLean(tx, ty)), W;
            // The hand's page tilt: a hake drawn downhill is wider (size only, as the Gravity Lab's).
            if (b.gSize != 0) D *= StrokeMath.gSize(b.gSize, pts.ga(i0, i1, t));
            // Flick: past the lift the head narrows to nothing (FlickFling).
            if (pts.flicks()) D *= pts.flickWidth(at);
            W = Math.max(1, D / 2);
            double Q = thickness(b, size, pr);
            double step = H.prev != null ? at - H.prev.d : 0;
            // ux, uy are unset before a second sample: the prototype's ?? 1 and ?? 0.
            double travel = travelAt(H.start, H.settle, at, Math.atan2(lerp(or(pts.uy[i0], 0), or(pts.uy[i1], 0), t), lerp(or(pts.ux[i0], 1), or(pts.ux[i1], 1), t)));
            double target = target(b, travel, azimuth);
            if (step > 0) {
                // A turn tighter than the head pivots it round its inner end; otherwise it eases toward the target, a degree per px at most.
                if (follow && !Double.isNaN(H.travel)) {
                    double dt = travel - H.travel;
                    dt = Math.atan2(StrictMath.sin(dt), StrictMath.cos(dt));
                    double radius = Math.abs(dt) > 1e-6 ? step / Math.abs(dt) : Double.POSITIVE_INFINITY;
                    H.th += dt * clamp((1.5 * W - radius) / (0.75 * W));
                }
                double lag = Math.max(40, W * 1.2), want = StrokeMath.turnToward(H.th, target, 1 - Math.exp(-step / lag)) - H.th;
                H.th += clamp(want, -LineStamper.DEG * step, LineStamper.DEG * step);
            }
            H.travel = travel;
            Head head = new Head();
            head.x = x; head.y = y; head.th = H.th; head.w = W; head.q = Q; head.d = at; head.pr = pr; head.c = StrictMath.cos(H.th); head.s = StrictMath.sin(H.th);
            if (pts.own != null) head.own = pts.own(i0, i1, t);
            if (H.prev != null) {
                // An end that would step backwards stays where it was: the head swings round it.
                Head A = H.prev;
                double mx = x - A.x, my = y - A.y, m = Math.hypot(mx, my);
                if (m > 1e-6) {
                    double dL = ((x + head.c * W) - (A.x + A.c * A.w)) * mx + ((y + head.s * W) - (A.y + A.s * A.w)) * my;
                    double dR = ((x - head.c * W) - (A.x - A.c * A.w)) * mx + ((y - head.s * W) - (A.y - A.s * A.w)) * my;
                    if (dL < 0 && dR >= 0) { head.x = A.x + A.c * A.w - head.c * W; head.y = A.y + A.s * A.w - head.s * W; }
                    else if (dR < 0 && dL >= 0) { head.x = A.x - A.c * A.w + head.c * W; head.y = A.y - A.s * A.w + head.s * W; }
                }
            }
            Head from = H.prev;
            if (from != null) {
                // In-between heads, at most 24, so a quick turn's ribbon follows the turn.
                double end = Math.max(Math.hypot((head.x + head.c * W) - (from.x + from.c * from.w), (head.y + head.s * W) - (from.y + from.s * from.w)),
                        Math.hypot((head.x - head.c * W) - (from.x - from.c * from.w), (head.y - head.s * W) - (from.y - from.s * from.w)));
                int parts = (int)Math.min(24, Math.ceil(end / 6));
                for (int j = 1; j < parts; j++) {
                    double f = (double)j / parts, th = StrokeMath.turnToward(from.th, head.th, f);
                    Head md = new Head();
                    md.x = lerp(from.x, head.x, f); md.y = lerp(from.y, head.y, f); md.th = th; md.w = lerp(from.w, W, f); md.q = lerp(from.q, Q, f);
                    md.d = lerp(from.d, at, f); md.pr = lerp(from.pr, pr, f); md.c = StrictMath.cos(th); md.s = StrictMath.sin(th); md.own = lerp(from.own, head.own, f);
                    laid.add(new Head[]{from, md});
                    from = md;
                }
            }
            laid.add(new Head[]{head, null});
            if (from != null) laid.add(new Head[]{from, head});
            H.prev = head;
            H.next += Math.max(1.5, Math.min(Q, 2 + W * 0.03));
        }
        lay.all(laid);
        return rect;
    }

    private static double or(float v, double unset) { return Float.isNaN(v) ? unset : v; }

    /** laneShare: how much of the head's paint lane {@code sLane} (0–1 across) carries at run-out {@code st}. */
    static double laneShare(double[] hairs, double sLane, double W, double st) {
        if (st <= 0) return 1;
        double f = clamp(sLane) * Math.min(120, 6 + W / 2.5);
        int h0 = (int)f;
        double tt = f - h0;
        return 1 - st * 0.75 * lerp(hairs[h0], hairs[h0 + 1], tt * tt * (3 - 2 * tt));
    }
    /** runOut: the streaks, growing as the head runs dry over 2400 px. */
    static double runOut(ResolvedBrush.Body b, double d) { return b.streaks + (1 - b.streaks) * 0.7 * clamp(d / 2400); }
    /** hakeDens: wet paint lighter at a light touch, the body's amount, pressure's darkness. */
    static double dens(ResolvedBrush.Body b, double pr) {
        return (b.wet ? lerp(0.7, 1, pr) : 1) * b.amount * (b.pDark != 0 ? 1 - 0.8 * b.pDark * (1 - pr) : 1);
    }

    /** One stamp's buffers and inputs: lays heads and ribbons (hakeHead, hakeRibbon, hakePut). */
    private static final class Lay {
        final FloatTiles into, wetD; final StrokePaper paper; final ResolvedBrush.Body b; final double[] hairs; final double softK, gran;
        final boolean liveGran; final int[] rect;
        /** The prototype's WET_D: the distance written to wetD by what is being laid. */
        double wetDist;

        /** Page pixels from one lattice point of {@link #into} to the next. */
        final int step;
        /** The page rows this lay keeps to (a band of them). */
        int top, bottom = Integer.MAX_VALUE;
        /** Whether heads grow {@link #rect} (their box doesn't depend on the band): the whole stamp's lay and the first band's. */
        boolean growsHeads = true;

        Lay(FloatTiles into, FloatTiles wetD, StrokePaper paper, ResolvedBrush.Body b, double[] hairs, double softK, int[] rect) {
            this.into = into; this.wetD = wetD; this.paper = paper; this.b = b; this.hairs = hairs; this.softK = softK; this.rect = rect;
            step = into.step();
            liveGran = b.fast && b.gran > 0; gran = b.gran;
        }
        /** Another lay of the same stamp, for another band, growing its own rectangle. */
        Lay with(int top, int bottom) {
            Lay l = new Lay(into, wetD, paper, b, hairs, softK, StrokeMath.emptyRect(into.width, into.height));
            l.top = top; l.bottom = bottom;
            return l;
        }

        /**
         * Lays {@code laid} (a head, B null, or a ribbon from A to B) in order: in bands of tile rows ({@link Cores#inBands}),
         * each with its own lay; then grows {@link #rect} by what they covered.
         */
        void all(java.util.List<Head[]> laid) {
            if (laid.isEmpty()) return;
            double y0 = Double.POSITIVE_INFINITY, y1 = Double.NEGATIVE_INFINITY, area = 0;
            for (Head[] op : laid) for (Head hd : op) if (hd != null) {
                double r = hd.w + hd.q + 2;
                y0 = Math.min(y0, hd.y - r); y1 = Math.max(y1, hd.y + r);
                area += 4 * hd.w * (hd.q + 2);
            }
            int first = Math.max(0, (int)Math.floor(y0) - 2);
            java.util.List<Lay> lays = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
            Cores.inBands(first, Math.min(into.height - 1, (int)Math.ceil(y1) + 2), FloatTiles.SIDE << into.shift, (long)area, (top, bottom, worker) -> {
                Lay l = with(top, bottom);
                l.growsHeads = top == first;
                for (Head[] op : laid) l.one(op);
                lays.add(l);
            });
            for (Lay l : lays) StrokeMath.grow(rect, l.rect);
        }
        private void one(Head[] op) { if (op[1] == null) head(op[0]); else ribbon(op[0], op[1]); }

        /** The tiles {@link #put} last wrote, at tile column {@link #tileX} of page row {@link #rowY} (−1 for none yet): heads and ribbons put a row's pixels in turn. */
        private float[] intoTile, wetTile;
        private int rowY = -1, tileX = -1, rowAt;

        /** Raises {@code into} to {@code cov} at {@code px, py} and marks it laid at {@link #wetDist}, as into.raise and wetD.set would. */
        void put(int px, int py, double cov) {
            if (liveGran) cov *= clamp(1 + gran * 1.5 * (paper.fine(px, py) - 0.5) + gran * 0.6 * (paper.mid(px, py) - 0.5), 0, 1.6);
            int tx = into.tileOf(px), ty = into.tileOf(py);
            if (py != rowY || tx != tileX) {
                rowY = py; tileX = tx; rowAt = into.rowAt(py);
                intoTile = into.tile(tx, ty, false); wetTile = wetD != null ? wetD.tile(tx, ty, false) : null;
            }
            int i = rowAt | into.at(px);
            float v = (float)cov;
            if (v > (intoTile == null ? into.fill : intoTile[i])) {
                if (intoTile == null) intoTile = into.tile(tx, ty, true);
                intoTile[i] = v;
            }
            if (wetD != null) {
                float when = (float)wetDist;
                if (wetTile == null && when != wetD.fill) wetTile = wetD.tile(tx, ty, true);
                if (wetTile != null) wetTile[i] = when;
            }
        }

        /** hakeHead: the head's rounded rectangle, W across and Q thick. */
        void head(Head hd) {
            int w = into.width, h = into.height;
            double x = hd.x, y = hd.y, W = hd.w, Q = hd.q, c = hd.c, sn = hd.s, edgeU = Math.max(1, W * softK * 0.5);
            double dens = dens(b, hd.pr) * hd.own, st = runOut(b, hd.d);
            wetDist = hd.d;
            double ex = Math.abs(c) * W + Math.abs(sn) * Q + 1.5, ey = Math.abs(sn) * W + Math.abs(c) * Q + 1.5, U = W + 0.5, QQ = Q + 0.5;
            int x0 = Math.max(0, (int)Math.floor(x - ex)), x1 = Math.min(w - 1, (int)Math.ceil(x + ex));
            int y0 = Math.max(0, (int)Math.floor(y - ey)), y1 = Math.min(h - 1, (int)Math.ceil(y + ey));
            for (int py = into.up(Math.max(y0, top)), yb = Math.min(y1, bottom); py <= yb; py += step) {
                double dy = py + 0.5 - y, lo = -ex, hi = ex;
                if (Math.abs(c) > 1e-6) { double a1 = (-U - dy * sn) / c, a2 = (U - dy * sn) / c; lo = Math.max(lo, Math.min(a1, a2)); hi = Math.min(hi, Math.max(a1, a2)); }
                else if (Math.abs(dy * sn) > U) continue;
                if (Math.abs(sn) > 1e-6) { double b1 = (dy * c - QQ) / sn, b2 = (dy * c + QQ) / sn; lo = Math.max(lo, Math.min(b1, b2)); hi = Math.min(hi, Math.max(b1, b2)); }
                else if (Math.abs(dy * c) > QQ) continue;
                if (hi < lo) continue;
                for (int px = into.up(Math.max(x0, (int)Math.floor(x - 0.5 + lo))), xb = Math.min(x1, (int)Math.ceil(x - 0.5 + hi)); px <= xb; px += step) {
                    double dx = px + 0.5 - x, u = dx * c + dy * sn, q = -dx * sn + dy * c;
                    // au⁸ by squaring: Math.pow a pixel was the head's dearest step on the device.
                    double au = Math.abs(u) / W, au2 = au * au, au4 = au2 * au2;
                    double cov = clamp((W - Math.abs(u)) / edgeU) * clamp(Q * (1 - au4 * au4) - Math.abs(q) + 0.5) * dens;
                    if (cov > 0) put(px, py, cov * laneShare(hairs, (1 - u / W) / 2, W, st));
                }
            }
            if (growsHeads) StrokeMath.grow(rect, x0, y0, x1, y1);
        }

        /** hakeRibbon: the quad between heads A and B, solved for each pixel's place across (s) and along (t). */
        void ribbon(Head A, Head B) {
            int w = into.width, h = into.height;
            wetDist = B.d;
            double ax = A.x + A.c * A.w, ay = A.y + A.s * A.w, bx = A.x - A.c * A.w, by = A.y - A.s * A.w;
            double cx = B.x - B.c * B.w, cy = B.y - B.s * B.w, dx = B.x + B.c * B.w, dy = B.y + B.s * B.w;
            double ex = bx - ax, ey = by - ay, fx = dx - ax, fy = dy - ay, gx = ax - bx + cx - dx, gy = ay - by + cy - dy;
            double k2 = gx * fy - gy * fx;
            double[] X = {ax, bx, cx, dx}, Y = {ay, by, cy, dy};
            int y0 = Math.max(0, (int)Math.floor(Math.min(Math.min(ay, by), Math.min(cy, dy))) - 1);
            int y1 = Math.min(h - 1, (int)Math.ceil(Math.max(Math.max(ay, by), Math.max(cy, dy))) + 1);
            int rx0 = w, rx1 = -1;
            boolean owns = A.own != 1 || B.own != 1;
            for (int py = into.up(Math.max(y0, top)), yb = Math.min(y1, bottom); py <= yb; py += step) {
                double yc = py + 0.5, lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
                for (int e = 0; e < 4; e++) {
                    double x1e = X[e], y1e = Y[e], x2e = X[(e + 1) & 3], y2e = Y[(e + 1) & 3];
                    if (yc < Math.min(y1e, y2e) - 1 || yc > Math.max(y1e, y2e) + 1) continue;
                    if (Math.abs(y2e - y1e) < 1e-6) { lo = Math.min(lo, Math.min(x1e, x2e)); hi = Math.max(hi, Math.max(x1e, x2e)); }
                    else { double xe = x1e + (x2e - x1e) * clamp((yc - y1e) / (y2e - y1e)); lo = Math.min(lo, xe); hi = Math.max(hi, xe); }
                }
                if (hi < lo) continue;
                int xa = Math.max(0, (int)Math.floor(lo - 1.5)), xb = Math.min(w - 1, (int)Math.ceil(hi + 1.5));
                for (int px = into.up(xa); px <= xb; px += step) {
                    double hx = px + 0.5 - ax, hy = yc - ay;
                    double k1 = ex * fy - ey * fx + hx * gy - hy * gx, k0 = hx * ey - hy * ex;
                    double s, t;
                    if (Math.abs(k2) < 1e-9) {
                        if (Math.abs(k1) < 1e-12) continue;
                        t = -k0 / k1; s = solve(t, hx, hy, ex, ey, fx, fy, gx, gy);
                    } else {
                        double disc = k1 * k1 - 4 * k0 * k2;
                        if (disc < 0) continue;
                        disc = Math.sqrt(disc);
                        t = (-k1 - disc) / (2 * k2); s = solve(t, hx, hy, ex, ey, fx, fy, gx, gy);
                        if (!(t >= -1e-3 && t <= 1.001 && s > -0.05 && s < 1.05)) { t = (-k1 + disc) / (2 * k2); s = solve(t, hx, hy, ex, ey, fx, fy, gx, gy); }
                    }
                    if (!(t >= -1e-3 && t <= 1.001) || !(s > -0.05 && s < 1.05)) continue;
                    double tt = clamp(t), W = lerp(A.w, B.w, tt), edgeU = Math.max(1, W * softK * 0.5);
                    double cov = clamp((W - Math.abs(1 - 2 * s) * W) / edgeU) * dens(b, lerp(A.pr, B.pr, tt));
                    if (owns) cov *= lerp(A.own, B.own, tt);
                    if (cov <= 0) continue;
                    put(px, py, cov * laneShare(hairs, s, W, runOut(b, lerp(A.d, B.d, tt))));
                    if (px < rx0) rx0 = px;
                    if (px > rx1) rx1 = px;
                }
            }
            if (rx1 >= rx0) StrokeMath.grow(rect, rx0, y0, rx1, y1);
        }

        private static double solve(double v, double hx, double hy, double ex, double ey, double fx, double fy, double gx, double gy) {
            return Math.abs(ex + gx * v) > Math.abs(ey + gy * v) ? (hx - fx * v) / (ex + gx * v) : (hy - fy * v) / (ey + gy * v);
        }
    }

    private HakeRibbon() {}
}
