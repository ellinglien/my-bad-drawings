package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.lerp;

/**
 * The body of paint (engine.js 253–333): round dabs, or a nib's or tilt's ellipse, with tapers, streaks, the hand's
 * speed and darkness, Fast's live granulation and the Fast-wet window's distance map; a flat head goes to
 * {@link HakeRibbon}. Keeps the strongest coverage per pixel. Sines and cosines are StrictMath's (fdlibm, as the
 * prototype's JavaScript nearly always gives), so dabs turn as the prototype's do. A stamp's dabs are worked out
 * first, then laid in bands of tile rows, each band in their order, a big stamp's on several cores ({@link Cores}): a
 * pixel sees the same writes in the same order. Pure, checked on the desktop.
 */
final class BodyStamper {
    /** Streak lanes a body's hairs are read from (the prototype's 130 draws). */
    static final int HAIRS = 130;

    /** paintDiameter: the dab's diameter at pressure {@code pr}; {@code lean} is tilt's lean, 0 without tilt. */
    static double diameter(ResolvedBrush.Body b, double size, double pr, double lean) {
        double press = Math.pow(clamp(pr), 1.2), broad = Math.min(1, press * (1 + 2 * lean));
        if (b.flatHead) return size * (0.8 + 0.2 * broad);
        double tip = lerp(0.6, 0.06, b.pressure);
        return size * (tip + (1 - tip) * broad);
    }

    /**
     * How far apart a body of diameter {@code D} lays its dabs: an eighth of it (the prototype's 0.12), closer for a
     * hard edge, so the dabs' ends scallop its rim by under {@link #RIM_DIP} of its soft edge ({@code softK} of the
     * radius, at least a pixel): s²/8·bend ≤ edge · RIM_DIP, at the dab's sharpest bend ({@code r · aspect²}, its
     * oval's ends; {@code r} for a round dab).
     */
    static double spacing(double D, double softK, double aspect) {
        double r = Math.max(0.8, D / 2), edge = Math.max(1, r * softK), bend = Math.max(0.8, r * Math.min(1, aspect) * Math.min(1, aspect));
        return Math.max(0.8, Math.min(D * 0.12, Math.sqrt(8 * RIM_DIP * bend * edge)));
    }
    /**
     * How deep, as a share of the soft edge, the scallops between dabs may dip: a sixteenth, which keeps a rim's repeat
     * about half the panel's grey step. A quarter, measured on the round dab alone, left a chisel's oval rim a row of
     * beads, a stamp's repeat along the edge (the owner, 2026-10-29: "a repeated pattern along the edge instead of a
     * smooth line"; EdgeRippleChecks).
     */
    static final double RIM_DIP = 1 / 16.0;

    /** The body's streak lanes: {@link #HAIRS} draws seeded by the brush (engine.js 270, 343). */
    static double[] hairs(long seed) {
        WildBrush.Random rnd = new WildBrush.Random(seed ^ 0x51ed);
        double[] hairs = new double[HAIRS];
        for (int i = 0; i < HAIRS; i++) hairs[i] = rnd.next();
        return hairs;
    }

    /**
     * stampBody: into {@code into}, from segment {@code from}; last = the stroke is lifting (tapers out, end skips).
     * wetD: the Fast-wet distance map or null (writes the dab's distance where it lays). Returns the touched rectangle.
     */
    static int[] stamp(FloatTiles into, FloatTiles wetD, StrokePaper paper, ResolvedBrush.Body b, StrokePoints pts, double size, int from, LineStamper.Meta meta, boolean last) {
        return stamp(into, wetD, paper, b, pts, size, from, meta, last, null);
    }
    /** As {@link #stamp(FloatTiles, FloatTiles, StrokePaper, ResolvedBrush.Body, StrokePoints, double, int, LineStamper.Meta, boolean)}, laying only inside {@code clip} (null for the page); the dabs and their spacing are the same. */
    static int[] stamp(FloatTiles into, FloatTiles wetD, StrokePaper paper, ResolvedBrush.Body b, StrokePoints pts, double size, int from, LineStamper.Meta meta, boolean last, int[] clip) {
        int w = into.width, h = into.height, n = pts.count, step = into.step();
        int cl = clip != null ? clip[0] : 0, ct = clip != null ? clip[1] : 0, cr = clip != null ? clip[2] : w - 1, cb = clip != null ? clip[3] : h - 1;
        int[] rect = StrokeMath.emptyRect(w, h);
        double tilt = b.tilt, softK = lerp(lerp(0.08, 0.012, b.crisp ? 1 : clamp(b.cover)), 0.6, b.soft);
        if (b.flatHead) return HakeRibbon.stamp(into, wetD, paper, b, pts, size, meta, softK, last);
        double[] hairs = b.streaks > 0 ? b.hairs() : null;
        boolean liveGran = b.fast && b.gran > 0, tiltRead = tilt > 0 || b.tDark != 0 || b.filbert;
        double gran = b.gran, amount = b.amount;
        double total = n > 0 ? pts.d[n - 1] : 0, grw = Math.max(1, size / 24);
        double inLen = b.taperIn * 70 * grw, outLen = b.taperOut * 90 * grw;
        // A dab only a spacing past the last one, wherever the samples fall: the prototype does so past 220 px
        // (engine.js 273); at every size here, since a 480 Hz pen's samples, a pixel or two apart, would otherwise lay
        // a dab each, many to a spacing, and a 120 px body was mostly the same dab laid again. Lifting, the stroke's
        // last point always lays one, so the end reaches the pen.
        LineStamper.Meta gap = meta != null ? meta : new LineStamper.Meta();
        java.util.ArrayList<Dab> dabs = new java.util.ArrayList<>();
        Shape shape = new Shape();
        long area = 0;
        for (int k = Math.max(1, from); k < n; k++) {
            int j = k - 1;
            double seg = (double)pts.d[k] - pts.d[j];
            double dirx = seg > 0 ? ((double)pts.x[k] - pts.x[j]) / seg : 1, diry = seg > 0 ? ((double)pts.y[k] - pts.y[j]) / seg : 0;
            double D1 = diameter(b, size, pts.p[k], tilt > 0 ? BrushDirection.tiltLean(pts.tx[k], pts.ty[k]) : 0);
            // A Filbert's crescent lays its dabs closer (filbertSpacing).
            double spacing = b.filbert ? filbertSpacing(b, D1, softK) : spacing(D1, softK, b.aspect);
            int steps = Math.max(1, (int)Math.ceil(seg / spacing));
            for (int s = k == 1 ? 0 : 1; s <= steps; s++) {
                double t = (double)s / steps, x = lerp(pts.x[j], pts.x[k], t), y = lerp(pts.y[j], pts.y[k], t);
                double pr = lerp(pts.p[j], pts.p[k], t), d = lerp(pts.d[j], pts.d[k], t);
                // Offset moves the body's start or end along the stroke.
                if ((b.skipStart != 0 || b.skipEnd != 0) && (d < b.skipStart || (last && d > total - b.skipEnd))) continue;
                double lean = 0, azimuth = Double.NaN;
                if (tiltRead) {
                    double tx = lerp(pts.tx[j], pts.tx[k], t), ty = lerp(pts.ty[j], pts.ty[k], t);
                    lean = BrushDirection.tiltLean(tx, ty);
                    azimuth = BrushDirection.azimuth(tx, ty);
                }
                double D = diameter(b, size, pr, tilt > 0 ? lean : 0);
                if (d - gap.lastD < (b.filbert ? filbertSpacing(b, D, softK) : spacing(D, softK, b.aspect)) && !(last && k == n - 1 && s == steps)) continue;
                gap.lastD = d;
                double tf = 1;
                if (inLen > 0) tf *= StrokeMath.taper(d / inLen);
                if (last && outLen > 0) tf *= StrokeMath.taper((total - d) / outLen);
                // Flick: past the lift the body narrows to nothing (FlickFling).
                if (pts.flicks()) tf *= pts.flickWidth(d);
                double v = lerp(pts.v[j], pts.v[k], t);
                double dens = b.wet ? lerp(0.7, 1, pr) : 1;
                if (amount != 1) dens *= amount;
                double dk = StrokeMath.handDark(b.pDark, b.sDark, b.tDark, pr, v, lean);
                if (dk != 1) dens *= dk;
                // Paint Load: what its own paint the brush still lays here (LoadedPaint); none left, no dab.
                if (pts.own != null) { dens *= pts.own(j, k, t); if (!(dens > 0)) continue; }
                double ga = b.gSize != 0 || b.gDark != 0 ? pts.ga(j, k, t) : 0;
                if (ga != 0) dens *= StrokeMath.gDark(b.gDark, ga);
                shape(shape, b, D, tf, v, ga, lean, azimuth);
                double r = shape.r, A = shape.A, B = shape.B, th = shape.th;
                double edge = Math.max(1, r * softK);
                double R = Math.ceil(A + 1), c = StrictMath.cos(th), sn = StrictMath.sin(th);
                if (R > gap.widest) gap.widest = (int)R;
                int x0 = Math.max(cl, (int)Math.floor(x - R)), x1 = Math.min(cr, (int)Math.ceil(x + R));
                int y0 = Math.max(ct, (int)Math.floor(y - R)), y1 = Math.min(cb, (int)Math.ceil(y + R));
                if (clip != null && (x1 < x0 || y1 < y0)) continue;
                Dab dab = new Dab();
                dab.x = x; dab.y = y; dab.r = r; dab.edge = edge; dab.A = A; dab.B = B; dab.c = c; dab.sn = sn; dab.dens = dens; dab.d = d;
                dab.dirx = dirx; dab.diry = diry; dab.x0 = x0; dab.x1 = x1; dab.y0 = y0; dab.y1 = y1;
                if (b.crescent && A != B) bite(dab);
                dabs.add(dab);
                if (x1 >= x0 && y1 >= y0) area += (long)(x1 - x0 + 1) * (y1 - y0 + 1);
                StrokeMath.grow(rect, x0, y0, x1, y1);
            }
        }
        lay(into, wetD, paper, b, hairs, liveGran, dabs, area);
        return rect;
    }

    /** A dab's size and ellipse ({@link #shape}): radius r, half-axes A and B (round when equal), angle th. */
    static final class Shape { double r, A, B, th; }
    /**
     * The dab {@link #stamp} lays of diameter {@code D} ({@link #diameter}), into {@code out}: its radius after the
     * tapers' factor {@code tf}, the hand's speed {@code v} and the page's tilt {@code ga} (0 for none), stretched along
     * tilt's lean ({@code lean}, {@code azimuth}: NaN without a reading) or a shaped tip's nib. The quick preview
     * ({@link QuickStroke}) reads the same dab.
     */
    static void shape(Shape out, ResolvedBrush.Body b, double D, double tf, double v, double ga, double lean, double azimuth) {
        double r = Math.max(0.8, D / 2);
        if (tf != 1) r = Math.max(0.8, r * tf);
        if (b.speed != 0) r *= clamp(1 + b.speed * 0.55 * (clamp(v / 2.5) - 0.3), 0.35, 1.6);
        if (ga != 0) r *= StrokeMath.gSize(b.gSize, ga);
        // Tilt stretches the dab along the lean; a shaped tip lays its nib's ellipse.
        double A = r, B = r, th = 0, tilt = b.tilt;
        if (b.filbert) {
            // A Filbert: its oval's broad side across the pen's lean, tilt's lean stretching it too; its Nib Angle without a reading.
            boolean reads = !Double.isNaN(azimuth);
            if (reads && tilt > 0 && lean > 0) A = r * (1 + 0.6 * tilt * lean);
            B = Math.max(0.8, r * b.aspect); th = reads ? azimuth + Math.PI / 2 : b.angle * LineStamper.DEG;
        } else if (tilt > 0 && !Double.isNaN(azimuth) && lean > 0) { A = r * (1 + 0.6 * tilt * lean); B = r * (1 - 0.35 * tilt * lean); th = azimuth + Math.PI / 2; }
        else if (b.aspect < 1) { B = Math.max(0.8, r * b.aspect); th = b.angle * LineStamper.DEG; }
        out.r = r; out.A = A; out.B = B; out.th = th;
    }

    /**
     * How far apart, at most, a Filbert of diameter {@code D} lays its dabs: {@link #spacing} of its oval; a crescent
     * ({@link ResolvedBrush.Body#crescent}) a fifth of its short half-axis too, well inside the half its middle
     * keeps less its soft edges, so they sweep its whole oval without ridges.
     */
    static double filbertSpacing(ResolvedBrush.Body b, double D, double softK) {
        double most = spacing(D, softK, b.aspect);
        return b.crescent ? Math.min(most, Math.max(0.8, .2 * Math.max(0.8, Math.max(0.8, D / 2) * b.aspect))) : most;
    }
    /**
     * A Filbert's crescent (MonoPaint's BrushStamp.crescent, for Paint Load): the dab's oval less a slightly shorter oval
     * (.85 of its length, as thick) set half the oval's reach toward the way the dab moves, so the hollow faces it and a
     * first touch is a thin sliver; the dabs after it sweep the whole oval. In the dab's own axes.
     */
    private static void bite(Dab dab) {
        double tu = dab.dirx * dab.c + dab.diry * dab.sn, tq = -dab.dirx * dab.sn + dab.diry * dab.c;
        double reach = Math.sqrt(dab.A * tu * dab.A * tu + dab.B * tq * dab.B * tq);
        dab.biteU = .5 * reach * tu; dab.biteQ = .5 * reach * tq; dab.biteA = .85 * dab.A; dab.biteB = dab.B;
    }

    /** One dab, as {@link #stamp} worked it out: where, its ellipse (round when A is B), its density and distance. */
    private static final class Dab {
        double x, y, r, edge, A, B, c, sn, dens, d, dirx, diry;
        int x0, x1, y0, y1;
        /** A crescent's bite ({@link #bite}): its centre in the dab's own axes and its half-axes; none when {@code biteA} is 0. */
        double biteU, biteQ, biteA, biteB;
    }

    /** Lays {@code dabs} in order, in bands of the buffer's tile rows ({@link Cores#inBands}; they cover {@code area} pixels). */
    private static void lay(FloatTiles into, FloatTiles wetD, StrokePaper paper, ResolvedBrush.Body b, double[] hairs, boolean liveGran, java.util.List<Dab> dabs, long area) {
        if (dabs.isEmpty()) return;
        int top = Integer.MAX_VALUE, bottom = -1;
        for (Dab d : dabs) { top = Math.min(top, d.y0); bottom = Math.max(bottom, d.y1); }
        if (bottom < top) return;
        Cores.inBands(top, bottom, FloatTiles.SIDE << into.shift, area, (ya, yb, worker) -> {
            for (Dab d : dabs) if (d.y1 >= ya && d.y0 <= yb) dab(into, wetD, paper, b, hairs, liveGran, d, Math.max(ya, d.y0), Math.min(yb, d.y1));
        });
    }

    /** Lays dab {@code dab}'s rows {@code y0..y1}: the strongest coverage kept, and its distance in wetD (if any) wherever it lays. */
    private static void dab(FloatTiles into, FloatTiles wetD, StrokePaper paper, ResolvedBrush.Body b, double[] hairs, boolean liveGran, Dab dab, int y0, int y1) {
        double x = dab.x, y = dab.y, r = dab.r, edge = dab.edge, A = dab.A, B = dab.B, c = dab.c, sn = dab.sn, dens = dab.dens, d = dab.d, dirx = dab.dirx, diry = dab.diry;
        double biteA = dab.biteA, biteB = dab.biteB, biteU = dab.biteU, biteQ = dab.biteQ, biteK = biteA > 0 ? Math.sqrt(biteA * biteB) / edge : 0;
        double gran = b.gran;
        int x0 = dab.x0, x1 = dab.x1, step = into.step();
        double nH = Math.min(120, 6 + A / 2.5);
        boolean round = A == B;
        // The buffer's lattice: every pixel, or a big stroke's even ones.
        for (int py = into.up(y0); py <= y1; py += step) {
            double dy = py + 0.5 - y;
            int xa = x0, xb = x1;
            if (round) {
                double span = r * r - dy * dy;
                if (span <= 0) continue;
                double sx = Math.sqrt(span);
                xa = Math.max(x0, (int)Math.floor(x - 0.5 - sx)); xb = Math.min(x1, (int)Math.ceil(x - 0.5 + sx));
            }
            int row = into.rowAt(py), ty = into.tileOf(py);
            for (int px = into.up(xa); px <= xb; ) {
                int tx = into.tileOf(px), end = Math.min(xb, into.tileEnd(tx));
                float[] tile = into.tile(tx, ty, false), when = wetD != null ? wetD.tile(tx, ty, false) : null;
                for (; px <= end; px += step) {
                    int i = row | into.at(px);
                    float now = tile == null ? into.fill : tile[i];
                    if (now >= dens && !liveGran) continue;
                    double dx = px + 0.5 - x, cov;
                    if (round) cov = clamp((r - Math.sqrt(dx * dx + dy * dy)) / edge) * dens;
                    else {
                        double u = dx * c + dy * sn, q = -dx * sn + dy * c, nd = Math.sqrt((u / A) * (u / A) + (q / B) * (q / B));
                        cov = clamp((1 - nd) * Math.sqrt(A * B) / edge) * dens;
                        // A crescent: less its bite, by how far inside the bite's own soft edge the pixel is.
                        if (biteA > 0 && cov > 0) {
                            double bu = (u - biteU) / biteA, bq = (q - biteQ) / biteB;
                            cov *= 1 - clamp((1 - Math.sqrt(bu * bu + bq * bq)) * biteK);
                        }
                    }
                    if (cov <= 0) continue;
                    if (hairs != null) {
                        double f = clamp(((-dx * diry + dy * dirx) / A + 1) / 2) * nH;
                        int h0 = (int)f;
                        double tt = f - h0;
                        cov *= 1 - b.streaks * 0.75 * lerp(hairs[h0], hairs[h0 + 1], tt * tt * (3 - 2 * tt));
                    }
                    if (liveGran) cov *= clamp(1 + gran * 1.5 * (paper.fine(px, py) - 0.5) + gran * 0.6 * (paper.mid(px, py) - 0.5), 0, 1.6);
                    if (cov > now) {
                        if (tile == null) tile = into.tile(tx, ty, true);
                        tile[i] = (float)cov;
                    }
                    if (wetD != null && cov > 0) {
                        // wetD.set, its tile fetched once a run.
                        if (when == null && (float)d != wetD.fill) when = wetD.tile(tx, ty, true);
                        if (when != null) when[i] = (float)d;
                    }
                }
            }
        }
    }

    private BodyStamper() {}
}
