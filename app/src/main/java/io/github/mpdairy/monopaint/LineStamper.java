package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.lerp;

/**
 * The line (engine.js 108–251, port.js 135–155): the port's nib ellipse stamped along the samples, with tilt,
 * charcoal's stick, size scaling, the Fast taper, a Fast-wet window and the hand's darkness, nib-width and
 * speed-angle inputs. Keeps the strongest coverage per pixel, so the order dabs are laid in doesn't matter: a call's
 * dabs are worked out first, then laid in bands of tile rows, a big call's on several cores ({@link Cores}). Pure,
 * checked on the desktop.
 */
final class LineStamper {
    /** What a stroke's stamps remember: the widest reach, the body's last dab distance, the hake's state, the bristles' swarm, a stipple's, hatching's, stamp's or spiro's state. */
    static final class Meta {
        int widest; double lastD = Double.NEGATIVE_INFINITY; HakeRibbon.State hake; BristleSwarm.State swarm;
        StippleStamper.State stipple; HatchStamper.State hatch; StampStamper.State stamp; SpiroStamper.State spiro; HandHatchStamper.State handHatch;
    }
    /** Lay only dabs with dMin <= d < dMax; with a tail, taper them toward the end over tailLen by tailAmt. */
    static final class Window { double dMin, dMax = Double.POSITIVE_INFINITY, tailLen, tailAmt; boolean tail; }

    static final double DEG = Math.PI / 180;
    /** The prototype's 2654435761, as Math.imul reads it. */
    static final int GOLDEN = (int)2654435761L;
    /** Bristles drawn for a dry line (the prototype's nBrMax with scaleAware on). */
    private static final int BRISTLES = 160;

    /** stampLine: into {@code into}, from segment {@code from}; last = the stroke is lifting; clip null for the page. Returns the touched rectangle. */
    static int[] stamp(FloatTiles into, StrokePaper paper, ResolvedBrush.Pen b, StrokePoints pts, double size, boolean last, int from, int[] clip, Meta meta, Window win) {
        int w = into.width, h = into.height, n = pts.count, step = into.step();
        double total = n > 0 ? pts.d[n - 1] : 0;
        double big = StrokeMath.inkBig(size), bigS = Math.sqrt(big);
        double[] bristles = b.bristles();
        double[] lens = StrokeMath.taperLens(b.taperIn, b.taperOut, size);
        double inLen = lens[0], outLen = lens[1], spread = b.fuzz;
        boolean dryOn = b.dry > 0, tiltOn = b.tilts();
        StickGrain G = spread > 0 ? StickGrain.of(b.seed) : null;
        int[] rect = StrokeMath.emptyRect(w, h);
        java.util.ArrayList<Dab> dabs = new java.util.ArrayList<>();
        java.util.ArrayList<double[]> specksAt = new java.util.ArrayList<>();
        Nib nib = new Nib();
        long area = 0;
        // Big nibs lay a dab only where the stroke crosses a fixed distance grid: a twelfth of a nib, or closer as a
        // body keeps its rim's scallops (BodyStamper.spacing: at the nib's sharpest bend, under a sixteenth of its edge,
        // a crisp one's pixel or a stick's fuzz, about 1.5 spreads of the nib's half width), so a big line's edge is
        // smooth, not a repeat (the owner's Springy brush, 2026-10-29; EdgeRippleChecks). The grid depends on distance
        // alone, so the Fast-wet window's re-lay lands the same dabs. At 16 px and under every step lays a dab, as the
        // port does.
        double gridSp = big > 1 ? Math.min(size * 0.08, BodyStamper.spacing(size, 1.5 * spread, b.aspect)) : 0;
        int cl = clip != null ? clip[0] : 0, ct = clip != null ? clip[1] : 0, cr = clip != null ? clip[2] : w - 1, cb = clip != null ? clip[3] : h - 1;
        for (int k = Math.max(1, from); k < n; k++) {
            int j = k - 1;
            double seg = (double)pts.d[k] - pts.d[j];
            double dirx = seg > 0 ? ((double)pts.x[k] - pts.x[j]) / seg : 1, diry = seg > 0 ? ((double)pts.y[k] - pts.y[j]) / seg : 0;
            // Tilt Shading: a nib laid on its side is stepped as its width (its soft edge hides the steps).
            double shade = b.side > 0 ? b.shade(BrushDirection.tiltLean(pts.tx[k], pts.ty[k])) : 0;
            // A crisp nib steps a grid at most, so every grid line gets its dab (a tenth of the nib, past the grid,
            // laid a dab each step: a quick stroke's notches); a stick's fuzz hides where its dabs land.
            double stepLen = Math.max(0.4, (shade > 0 ? size * (1 + ResolvedBrush.SIDE_WIDTH * shade) : size) * 0.1);
            if (gridSp > 0 && shade == 0 && spread == 0) stepLen = Math.min(stepLen, gridSp);
            int steps = Math.max(1, (int)Math.ceil(seg / stepLen));
            for (int s = k == 1 ? 0 : 1; s <= steps; s++) {
                double t = (double)s / steps, x = lerp(pts.x[j], pts.x[k], t), y = lerp(pts.y[j], pts.y[k], t), d = lerp(pts.d[j], pts.d[k], t);
                if (win != null && (d < win.dMin || d >= win.dMax)) continue;
                nib(nib, b, paper, pts, size, big, bigS, inLen, outLen, total, last, win, tiltOn, j, k, t, x, y, d, dirx, diry, w, h);
                double pr = nib.pr, v = nib.v, cosA = nib.cosA, sinA = nib.sinA, a = nib.a, bb = nib.bb, dk = nib.dk, soft = nib.soft, side = nib.shade;
                // Crayon: its width wobbles along the stroke, by noise fixed to the page.
                if (b.crayon) { double wob = Crayon.wobble(x, y); a *= wob; bb *= wob; }
                double sw = spread > 0 ? Math.max(1, spread * (bb * 1.4 + a * 0.12)) : 0;
                // Tilt Shading: a stick laid on its side feathers its fuzzy edge further.
                if (sw > 0 && side > 0) sw *= 1 + ResolvedBrush.SIDE_FEATHER * side;
                // A stick's soft edge hides the scallops of dabs further apart: every m-th grid line, as many as keep
                // them, on the nib's narrower side, under a twelfth of the edge's tone (s²/8bb ≤ sw/18), at most four.
                // A small stick, which lays a dab every step, takes a grid of steps' length when it can skip some.
                double stickSp = big > 1 ? gridSp : Math.max(0.4, size * 0.1);
                int every = sw > 0 ? Math.max(1, Math.min(4, (int)(Math.sqrt(8 * bb * sw / 18) / stickSp))) : 1;
                double grid = big > 1 ? gridSp * every : every > 1 ? stickSp * every : 0;
                // Tilt Shading: a nib on its side lays a dab every so much of its width, its soft edge and grain hiding
                // the scallops (a dab every sample would lay each pixel about ten times over).
                if (side > 0) grid = Math.max(grid, 2 * a * ResolvedBrush.SIDE_STEP);
                if (grid > 0 && !(k == 1 && s == 0) && !(last && k == n - 1 && s == steps)
                        && Math.floor(d / grid) == Math.floor((d - seg / steps) / grid)) continue;
                // Flick: past the lift its bristles run dry too (FlickFling), whatever the brush's own dry brush.
                double flickDry = pts.flicks() ? pts.flickDry(d) : 0;
                double load = b.load(d, v, pr) - flickDry;
                // A stick's pressure eases its grain; a crayon's line holds how hard it presses (Crayon.press).
                double press = press(b, pr);
                int nBr = (int)Math.min(BRISTLES, 6 + Math.round(a));
                int R = (int)Math.ceil((b.crayon ? a * Crayon.REACH : a) + 0.7 * sw + 1.5);
                if (meta != null && R > meta.widest) meta.widest = R;
                int x0 = Math.max(cl, (int)Math.floor(x - R)), x1 = Math.min(cr, (int)Math.ceil(x + R));
                int y0 = Math.max(ct, (int)Math.floor(y - R)), y1 = Math.min(cb, (int)Math.ceil(y + R));
                if (x1 >= x0 && y1 >= y0) {
                    // Each row visits only the span of an ellipse that holds every pixel the dab can cover: coverage needs
                    // dist < 1 + m / bb (the gradient g is at most 1 / bb; m is the soft edge's reach past the rim in px),
                    // so the nib's ellipse scaled by that, grown by a pixel all round. Pixels outside it would lay nothing.
                    double m = sw != 0 ? (0.5 + 1.45 * spread) * sw : soft > 0 ? 0.5 * soft : 0.5, grown = (b.crayon ? Crayon.REACH : 1) + m / bb;
                    Dab dab = new Dab();
                    dab.x = x; dab.y = y; dab.a = a; dab.bb = bb; dab.cosA = cosA; dab.sinA = sinA; dab.grown = grown;
                    dab.sw = sw; dab.dk = dk; dab.press = press; dab.load = load; dab.nBr = nBr; dab.dirx = dirx; dab.diry = diry;
                    dab.dry = dryOn || flickDry > 0; dab.soft = soft; dab.side = side;
                    dab.x0 = x0; dab.x1 = x1; dab.y0 = y0; dab.y1 = y1;
                    dabs.add(dab);
                    area += (long)(x1 - x0 + 1) * (y1 - y0 + 1);
                    StrokeMath.grow(rect, x0, y0, x1, y1);
                }
                // Dust: specks knocked off around the stick, seeded by where the dab is, so a re-lay lays the same ones.
                if (spread > 0) {
                    WildBrush.Random dr = new WildBrush.Random((int)b.seed ^ (int)Math.round(d * 4) * GOLDEN);
                    // As many to a length of stroke however far apart the dabs.
                    long specks = Math.min(24 * every, Math.round(spread * (1.5 + a * 0.08) * every));
                    for (int m = 0; m < specks; m++) {
                        double th = dr.next() * 2 * Math.PI, rr = 1.02 + dr.next() * dr.next() * 1.3;
                        double su = Math.cos(th) * (a + 0.5 * sw) * rr, sq = Math.sin(th) * (bb + 0.5 * sw) * rr;
                        double sx = x + su * cosA - sq * sinA, sy = y + su * sinA + sq * cosA, sr = 0.5 + dr.next() * dr.next() * 1.6, sc = (0.6 + 0.4 * dr.next()) * dk;
                        int X0 = Math.max(cl, (int)Math.floor(sx - sr - 1)), X1 = Math.min(cr, (int)Math.ceil(sx + sr + 1));
                        int Y0 = Math.max(ct, (int)Math.floor(sy - sr - 1)), Y1 = Math.min(cb, (int)Math.ceil(sy + sr + 1));
                        if (X1 < X0 || Y1 < Y0) continue;
                        specksAt.add(new double[]{sx, sy, sr, sc, X0, Y0, X1, Y1});
                        StrokeMath.grow(rect, X0, Y0, X1, Y1);
                    }
                }
            }
        }
        lay(into, paper, b, G, bristles, dabs, specksAt, area);
        if (b.fast && b.splatter > 0) splatterLive(into, b, pts, size, from, rect);
        else if (last && b.splatter > 0) {
            if (big > 1) {
                WildBrush.Random rr = finalDrops(b, pts);
                for (int k = 1; k < n; k++) splatterSeg(into, b, pts, size, k, rr, rect);
            } else splatter(into, b, pts, size, rect);
        }
        return rect;
    }

    /** A nib's short half-axis for long half-axis {@code a} and its {@code aspect}: at least a pixel, never past {@code a}. */
    static double shortHalf(double a, double aspect) { return Math.max(0.6, Math.min(a, Math.max(1, a * aspect))); }
    /** The nib at one place along the stroke ({@link #nib}): the pressure and speed there, its ellipse (half-axes a, bb; angle's cosine and sine) and the hand's darkness. */
    static final class Nib { double pr, v, a, bb, cosA, sinA, dk, soft, shade; }
    /** What of its darkness pen {@code b}'s line holds at pressure {@code pr} where it lays most: a stick's eased grain, a crayon's press, else 1. */
    static double press(ResolvedBrush.Pen b, double pr) { return b.fuzz > 0 ? lerp(1, 0.3 + 0.7 * pr * pr, b.fuzz) : b.crayon ? pr : 1; }
    /**
     * The nib {@link #stamp} lays at {@code t} of the way from sample {@code j} to {@code k} ({@code x, y}, {@code d}
     * along the stroke, heading {@code dirx, diry}), into {@code out}: its width from pressure, speed, the tapers, tilt
     * and the page's tilt, its angle and flattening, and the hand's darkness. {@code big}, {@code bigS}: {@link
     * StrokeMath#inkBig} of {@code size} and its root; {@code inLen}, {@code outLen}: the tapers' lengths; {@code total}
     * the stroke's length; {@code win}: a Fast-wet window's tail, or null; {@code w, h}: the page's size. The quick
     * preview ({@link QuickStroke}) reads the same nib.
     */
    static void nib(Nib out, ResolvedBrush.Pen b, StrokePaper paper, StrokePoints pts, double size, double big, double bigS, double inLen, double outLen, double total,
                    boolean last, Window win, boolean tiltOn, int j, int k, double t, double x, double y, double d, double dirx, double diry, int w, int h) {
        double pr = lerp(pts.p[j], pts.p[k], t), v = lerp(pts.v[j], pts.v[k], t);
        double wd = b.width(size, pr, v);
        if (inLen > 0) wd *= StrokeMath.taper(d / inLen);
        // Flick: past the lift the stroke narrows to nothing (FlickFling).
        if (pts.flicks()) wd *= pts.flickWidth(d);
        if (b.fast) {
            if (b.taperOut > 0) {
                double fall = pts.pk[j] > 0 ? clamp(pr / (pts.pk[j] * 0.7)) : 1, flick = clamp((v - 1.2) / 2);
                wd *= lerp(1, Math.pow(fall, 0.8) * 0.92 + 0.08, b.taperOut) * (1 - 0.8 * b.taperOut * flick);
            }
        } else if (last && outLen > 0) wd *= StrokeMath.taper((total - d) / outLen);
        if (win != null && win.tail) wd *= lerp(1, StrokeMath.taper((total - d) / win.tailLen), win.tailAmt);
        int xi = StrokeMath.index((int)(x / big), w), yi = StrokeMath.index((int)(y / big), h);
        if (b.wobble > 0) wd *= 1 + b.wobble * 0.7 * (paper.mid(xi, yi) - 0.5);
        if (b.pool > 0) {
            double still = 1 - clamp(v / 0.25), grw = b.pool * 0.9 * (still * still);
            wd = big > 1 ? wd + Math.min(wd * grw, 16 * grw * bigS) : wd * (1 + grw);
        }
        // The hand's page tilt: wider downhill, lighter uphill, a shaped nib opening downhill (1 when level or off).
        double ga = b.gSize != 0 || b.gDark != 0 || b.gWidth != 0 ? pts.ga(j, k, t) : 0;
        if (ga != 0) wd *= StrokeMath.gSize(b.gSize, ga);
        double ang = b.angle * DEG, flat = 1, lean = 0, shade = 0;
        if (tiltOn) {
            double tx = lerp(pts.tx[j], pts.tx[k], t), ty = lerp(pts.ty[j], pts.ty[k], t);
            lean = BrushDirection.tiltLean(tx, ty);
            wd *= b.tiltWidth(tx, ty, lean);
            // Tilt Shading: laid low, the side of the nib (a pencil's lead).
            shade = b.shade(lean);
            if (shade > 0) wd *= 1 + ResolvedBrush.SIDE_WIDTH * shade;
            double azimuth = BrushDirection.azimuth(tx, ty);
            if (b.tiltAngle > 0 && !Double.isNaN(azimuth)) ang = StrokeMath.turnToward(ang, azimuth + Math.PI / 2, b.tiltAngle);
            // A Filbert's broad side lies across the pen's lean.
            if (b.filbert && !Double.isNaN(azimuth)) ang = azimuth + Math.PI / 2;
            flat = b.tiltFlat(tx, ty, lean);
        }
        // Speed turns the nib to follow a quick stroke, broad side across it.
        if (b.sAngle > 0) ang = StrokeMath.turnToward(ang, Math.atan2(diry, dirx) + Math.PI / 2, b.sAngle * clamp(v / 1.5));
        double a = Math.max(0.6, wd / 2);
        double bb = shortHalf(a, b.aspect * flat);
        // Pressure to nib width: a round tip squashes into an oval, a shaped nib opens wider.
        if (b.pWidth > 0) bb = b.aspect >= 1 ? Math.max(0.6, bb * (1 - 0.45 * b.pWidth * pr)) : Math.max(0.6, Math.min(a, bb * (1 + 1.2 * b.pWidth * (pr - 0.4))));
        if (ga != 0) bb = StrokeMath.gWidth(b.gWidth, ga, bb, a);
        double dk = StrokeMath.handDark(b.pDark, b.sDark, b.tDark, pr, v, lean);
        if (ga != 0) dk *= StrokeMath.gDark(b.gDark, ga);
        if (shade > 0) dk *= 1 - ResolvedBrush.SIDE_LIGHT * shade;
        // Paint Load: what of its own paint the brush still lays here (LoadedPaint).
        if (pts.own != null) dk *= pts.own(j, k, t);
        out.pr = pr; out.v = v; out.a = a; out.bb = bb; out.cosA = Math.cos(ang); out.sinA = Math.sin(ang); out.dk = dk;
        // Its edge's softness, px (0: the crisp edge's half pixel each side).
        out.soft = shade > 0 ? 1 + ResolvedBrush.SIDE_SOFT * shade * a : 0;
        out.shade = shade;
    }

    /** One dab of the nib, as {@link #stamp} worked it out: where, its ellipse, and what it lays. */
    private static final class Dab {
        double x, y, a, bb, cosA, sinA, grown, sw, dk, press, load, dirx, diry;
        /** Tilt Shading: the edge soft over this many px (0: crisp), and how far the nib lies on its side ({@link ResolvedBrush.Pen#shade}, 0 upright). */
        double soft, side;
        int nBr, x0, x1, y0, y1;
        /** Whether its bristles leave streaks dry: a dry line's, or a flick's past the lift. */
        boolean dry;
    }
    /**
     * Lays {@code dabs} and the dust's specks ({sx, sy, r, darkness, x0, y0, x1, y1}) into {@code into}, keeping the
     * strongest coverage per pixel: in bands of the buffer's tile rows ({@link Cores#inBands}; they cover {@code area}
     * pixels). Each band writes only its own tiles.
     */
    private static void lay(FloatTiles into, StrokePaper paper, ResolvedBrush.Pen b, StickGrain G, double[] bristles, java.util.List<Dab> dabs, java.util.List<double[]> specks, long area) {
        if (dabs.isEmpty() && specks.isEmpty()) return;
        int top = Integer.MAX_VALUE, bottom = -1;
        for (Dab d : dabs) { top = Math.min(top, d.y0); bottom = Math.max(bottom, d.y1); }
        for (double[] k : specks) { top = Math.min(top, (int)k[5]); bottom = Math.max(bottom, (int)k[7]); }
        StrokeMath.Span[] spans = new StrokeMath.Span[Cores.WORKERS];
        Cores.inBands(top, bottom, FloatTiles.SIDE << into.shift, area, (ya, yb, worker) -> {
            StrokeMath.Span span = spans[worker];
            if (span == null) spans[worker] = span = new StrokeMath.Span();
            for (Dab d : dabs) if (d.y1 >= ya && d.y0 <= yb) dab(into, paper, b, G, bristles, d, span, Math.max(ya, d.y0), Math.min(yb, d.y1));
            for (double[] sp : specks) if (sp[7] >= ya && sp[5] <= yb) speck(into, sp, Math.max(ya, (int)sp[5]), Math.min(yb, (int)sp[7]));
        });
    }

    /** Lays dab {@code d}'s rows {@code y0..y1} into {@code into}, the strongest coverage kept. */
    private static void dab(FloatTiles into, StrokePaper paper, ResolvedBrush.Pen b, StickGrain G, double[] bristles, Dab d, StrokeMath.Span span, int y0, int y1) {
        int step = into.step();
        double x = d.x, y = d.y, a = d.a, bb = d.bb, cosA = d.cosA, sinA = d.sinA, sw = d.sw, dk = d.dk, press = d.press, load = d.load, dirx = d.dirx, diry = d.diry;
        double spread = b.fuzz;
        boolean crayon = b.crayon;
        int nBr = d.nBr, x0 = d.x0, x1 = d.x1;
        boolean dryOn = d.dry;
        double side = d.side;
        // Without a stick's soft edge a dab lays at most dk (1 when the hand doesn't darken).
        double dkCap = sw != 0 || dk == 1 ? Double.POSITIVE_INFINITY : dk;
        span.set(a * d.grown + 1, bb * d.grown + 1, cosA, sinA, x, y);
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
                    // A pixel already at the most this dab could lay is left before any work (cov never exceeds its cap).
                    if (now >= 1 || now >= dkCap) continue;
                    double dx = px + 0.5 - x, dy = py + 0.5 - y;
                    // Tilt Shading: the side of the lead skips the paper's valleys (its fine grain) and lays in streaks, so
                    // its most here is less (a pixel at that most is left, as above).
                    double tooth = 1;
                    if (side > 0) {
                        tooth = sideTooth(paper.fine(px, py), side) * sideStreak(bristles, side, (-dx * diry + dy * dirx) / a);
                        if (sw == 0 && (float)(dk * tooth) <= now) continue;
                    }
                    double gm = 0, gl = 0, grain = 0;
                    if (sw != 0) {
                        gm = G.mid(px, py); gl = G.low(px, py);
                        grain = clamp(1 + 1.2 * spread * (gl - 0.5) + 0.5 * spread * (gm - 0.5), 0.2, 1.2);
                        double cap = press * grain * tooth;
                        if (dk != 1) cap *= dk;
                        if (cap <= now) continue;
                    }
                    double u = dx * cosA + dy * sinA, q = -dx * sinA + dy * cosA;
                    double dist = Math.sqrt((u / a) * (u / a) + (q / bb) * (q / bb));
                    double gu = u / (a * a), gq = q / (bb * bb), g = Math.sqrt(gu * gu + gq * gq) / Math.max(dist, 1e-6);
                    double cov;
                    if (sw != 0) {
                        double e = (1 - dist) / Math.max(g, 1e-6) + sw * spread * (1.1 * (gm - 0.5) + 1.8 * (gl - 0.5));
                        double f = clamp((e + 0.5 * sw) / sw);
                        cov = f * f * (3 - 2 * f) * press * grain;
                    } else if (crayon) cov = Crayon.press(dist, g, press, px, py);
                    else if (d.soft > 0) cov = clamp(0.5 - (dist - 1) / Math.max(g, 1e-6) / d.soft);
                    else cov = clamp(0.5 - (dist - 1) / Math.max(g, 1e-6));
                    if (side > 0) cov *= tooth;
                    if (cov <= 0) continue;
                    // The bristles only take away (a share of at most 1): a dab that can't beat what is there skips them.
                    if (cov * dk <= now) continue;
                    if (dryOn) {
                        double across = clamp(((-dx * diry + dy * dirx) / a + 1) / 2, 0, 0.9999);
                        if (step == 1) { if (bristles[(int)(across * nBr)] > load + 0.25 * ((G != null ? G.fine(px, py) : paper.fine(px, py)) - 0.5)) cov = 0; }
                        // On the lattice a bristle's gap, a pixel wide, would fall on some points and miss others (a dotted
                        // streak): the share of the point's four pixels the bristles leave dry.
                        else cov *= dry(bristles, nBr, load, G, paper, px, py, dx, dy, dirx, diry, a);
                    }
                    if (dk != 1) cov *= dk;
                    if (cov > now) {
                        if (tile == null) tile = into.tile(tx, ty, true);
                        tile[i] = (float)cov;
                    }
                }
            }
        }
    }
    /** Lays speck {@code sp}'s rows {@code y0..y1}. */
    private static void speck(FloatTiles into, double[] sp, int y0, int y1) {
        double sx = sp[0], sy = sp[1], sr = sp[2], sc = sp[3];
        int X0 = (int)sp[4], X1 = (int)sp[6];
        for (int py = into.up(y0), step = into.step(); py <= y1; py += step) for (int px = into.up(X0); px <= X1; px += step) {
            double ex = px + 0.5 - sx, ey = py + 0.5 - sy;
            into.raise(px, py, (float)(clamp(sr - Math.sqrt(ex * ex + ey * ey) + 0.5) * sc));
        }
    }

    /** The share of the 2 × 2 pixels from {@code px, py} (the first {@code dx, dy} from the dab's centre) a dry line's bristles lay ink on. */
    private static double dry(double[] bristles, int nBr, double load, StickGrain G, StrokePaper paper, int px, int py, double dx, double dy, double dirx, double diry, double a) {
        int wet = 0;
        for (int j = 0; j < 2; j++) for (int i = 0; i < 2; i++) {
            double across = clamp(((-(dx + i) * diry + (dy + j) * dirx) / a + 1) / 2, 0, 0.9999);
            if (bristles[(int)(across * nBr)] <= load + 0.25 * ((G != null ? G.fine(px + i, py + j) : paper.fine(px + i, py + j)) - 0.5)) wet++;
        }
        return wet / 4.0;
    }

    /**
     * Tilt Shading's tooth: what of its tone the side of a nib laid {@code side} of the way down ({@link ResolvedBrush.Pen#shade})
     * leaves where the paper's fine grain is {@code fine} (0 valley to 1 peak): the side rides the peaks and skips the
     * valleys below {@link ResolvedBrush#SIDE_SKIP} of the way up, the lower it lies the more, eased over {@link
     * ResolvedBrush#SIDE_BAND}. Nearly all or nothing, so the line's rule (graphite's tooth, charcoal's contact) keeps its
     * own grain on what is left.
     */
    static double sideTooth(double fine, double side) {
        double e = ResolvedBrush.SIDE_SKIP * side;
        return StrokeMath.smoothstep(e - ResolvedBrush.SIDE_BAND, e + ResolvedBrush.SIDE_BAND, fine);
    }
    /**
     * Tilt Shading's unevenness across the band ({@code across}: −1 to 1, one edge to the other): streaks along the
     * stroke, as the lead's side never lies quite flat, from the brush's own draws ({@code bristles}), eased between
     * {@link #STREAKS} across; at most {@link ResolvedBrush#SIDE_STREAK} lighter.
     */
    static double sideStreak(double[] bristles, double side, double across) {
        double f = clamp((across + 1) / 2, 0, 0.9999) * STREAKS;
        int i = (int)f;
        return 1 - ResolvedBrush.SIDE_STREAK * side * lerp(bristles[i], bristles[i + 1], f - i);
    }
    /** How many streaks lie across a band laid with the side ({@link #sideStreak}). */
    private static final int STREAKS = 9;

    /** A dry line's bristles: {@link #BRISTLES} draws seeded by the brush ({@link Placement} reads the first of the same draws). */
    static double[] bristles(long seed) {
        WildBrush.Random rnd = new WildBrush.Random(seed ^ 0x9e37);
        double[] bristles = new double[BRISTLES];
        for (int i = 0; i < BRISTLES; i++) bristles[i] = rnd.next();
        return bristles;
    }

    /** splatterLive (engine.js 229–231). */
    static void splatterLive(FloatTiles into, ResolvedBrush.Pen b, StrokePoints pts, double size, int from, int[] rect) {
        for (int k = Math.max(1, from); k < pts.count; k++) splatterSeg(into, b, pts, size, k, new WildBrush.Random((int)b.seed ^ k * GOLDEN), rect);
    }

    /** splatterSeg (engine.js 232–251): segment {@code k}'s drops from {@code rnd}, thrown farther and bigger by a big nib. */
    private static void splatterSeg(FloatTiles into, ResolvedBrush.Pen b, StrokePoints pts, double size, int k, WildBrush.Random rnd, int[] rect) {
        double big = StrokeMath.inkBig(size), bigS = Math.sqrt(big);
        int j = k - 1;
        double seg = (double)pts.d[k] - pts.d[j], v = pts.v[k];
        if (v < 0.8 || seg <= 0 || rnd.next() > b.splatter * 0.025 * (v - 0.8) * seg) return;
        double dirx = ((double)pts.x[k] - pts.x[j]) / seg, diry = ((double)pts.y[k] - pts.y[j]) / seg;
        double reach = big > 1 ? bigS : 1, dropSize = big > 1 ? Math.min(size, 24) * bigS : size;
        for (int drops = 1 + (int)(rnd.next() * 3); drops > 0; drops--) {
            double ahead = (6 + rnd.next() * 34 * v) * reach + (big > 1 ? size * 0.5 : 0), side = (rnd.next() - 0.5) * 18 * reach;
            double x = pts.x[k] + dirx * ahead - diry * side, y = pts.y[k] + diry * ahead + dirx * side;
            double r = Math.max(0.7, (0.3 + rnd.next() * rnd.next() * 1.4) * dropSize / 5);
            drop(into, x, y, r, false, rect);
        }
    }

    /** The port's splatter (port.js 135–155): fast flicks throw drops ahead of the pen and a little to the side. */
    static void splatter(FloatTiles into, ResolvedBrush.Pen b, StrokePoints pts, double size, int[] rect) {
        WildBrush.Random rnd = finalDrops(b, pts);
        for (int k = 1; k < pts.count; k++) {
            int j = k - 1;
            double seg = (double)pts.d[k] - pts.d[j], v = pts.v[k];
            if (v < 0.8 || seg <= 0 || rnd.next() > b.splatter * 0.025 * (v - 0.8) * seg) continue;
            double dirx = ((double)pts.x[k] - pts.x[j]) / seg, diry = ((double)pts.y[k] - pts.y[j]) / seg;
            for (int drops = 1 + (int)(rnd.next() * 3); drops > 0; drops--) {
                double ahead = 6 + rnd.next() * 34 * v, side = (rnd.next() - 0.5) * 18;
                double x = pts.x[k] + dirx * ahead - diry * side, y = pts.y[k] + diry * ahead + dirx * side;
                double r = Math.max(0.7, (0.3 + rnd.next() * rnd.next() * 1.4) * size / 5);
                drop(into, x, y, r, true, rect);
            }
        }
    }

    /** The lift's drops come from one generator seeded by the stroke's sample count and length, as {@link WildInkStroke}'s splatter. */
    private static WildBrush.Random finalDrops(ResolvedBrush.Pen b, StrokePoints pts) {
        double total = pts.count > 0 ? pts.d[pts.count - 1] : 0;
        return new WildBrush.Random((int)b.seed ^ pts.count * GOLDEN ^ (int)Math.round(total * 16.0));
    }

    /** One round drop of radius {@code r} at {@code x, y}; {@code hypot}: the port's distance (Math.hypot), else engine.js's square root. */
    private static void drop(FloatTiles into, double x, double y, double r, boolean hypot, int[] rect) {
        int R = (int)Math.ceil(r + 1);
        int x0 = Math.max(0, (int)Math.floor(x - R)), x1 = Math.min(into.width - 1, (int)Math.ceil(x + R));
        int y0 = Math.max(0, (int)Math.floor(y - R)), y1 = Math.min(into.height - 1, (int)Math.ceil(y + R));
        if (x1 < x0 || y1 < y0) return;
        for (int py = into.up(y0), step = into.step(); py <= y1; py += step) for (int px = into.up(x0); px <= x1; px += step) {
            double ex = px + 0.5 - x, ey = py + 0.5 - y;
            into.raise(px, py, (float)clamp(r - (hypot ? Math.hypot(ex, ey) : Math.sqrt(ex * ex + ey * ey)) + 0.5));
        }
        StrokeMath.grow(rect, x0, y0, x1, y1);
    }

    private LineStamper() {}
}
