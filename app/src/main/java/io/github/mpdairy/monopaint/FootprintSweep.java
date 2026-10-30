package io.github.mpdairy.monopaint;

/**
 * A mark's footprint swept from sample to sample, as the stampers' own geometry lays it ({@link LineStamper#nib},
 * {@link BodyStamper#shape}, the hake's head, a pattern's band, the hairs' wall, a smudge's dab): its shape at each
 * sample as a convex polygon (an ellipse; the hake's thin head), the hull of the last two swept. Only what the last
 * shape didn't cover is new (a sliver ahead, and behind where the mark grows), so each pixel is reached about once;
 * each page row's new pixels gather into at most two spans ({@link #lo}..{@link #hi}, {@link #lo2}..{@link #hi2}),
 * handed to {@link #write} once a row has a third, and at {@link #flush}. A quick stroke's preview fills them flat
 * ({@link QuickStroke}); a Wet stroke lays them into the wet canvas ({@link WetPaintStroke}). Moved out of
 * {@code QuickStroke.Sweep} unchanged. Pure, checked on the desktop.
 */
abstract class FootprintSweep {
    static final int SIDES = 24;
    /** The most corners a shape may have: an ellipse's, or one cut by a line ({@code QuickStroke}'s split nib). */
    static final int MOST = SIDES + 2;
    final ResolvedBrush r;
    /** The stroke's samples, whose shapes it sweeps. */
    final StrokePoints pts;
    final int width, height;
    final boolean isLine;
    /**
     * A smudge with no paint of its own: the dab it smudges with ({@link ResolvedBrush.Carry#stumpR}), as wide as its
     * size dot, in the light wash's dither. Its nib (a wash's is a tenth of its size) would show a fraction of what it moves.
     */
    final boolean smudge;
    /** Only even rows take spans (a sparse dither has no dots on odd rows). */
    final boolean evenRows;
    final StrokePaper paper;
    final LineStamper.Nib nib = new LineStamper.Nib();
    final BodyStamper.Shape shape = new BodyStamper.Shape();
    /** The last shape's and this one's corners (how many: pn, qn), and the two's hull. */
    double[] px = new double[MOST], py = new double[MOST], qx = new double[MOST], qy = new double[MOST];
    int pn, qn;
    final double[] hx = new double[2 * MOST + 1], hy = new double[2 * MOST + 1], sx = new double[2 * MOST], sy = new double[2 * MOST];
    final int[] order = new int[2 * MOST];
    boolean first = true;
    /** Each page row's two spans still to write (lo > hi: none), and the rows that have one. */
    final int[] lo, hi, lo2, hi2;
    int top = Integer.MAX_VALUE, bottom = -1;
    /** A polygon's left and right edge at each of its rows, and the hull's. */
    final double[] left, right, hullLeft, hullRight;

    FootprintSweep(ResolvedBrush r, StrokePoints pts, int width, int height, boolean isLine, StrokePaper paper, boolean evenRows) {
        this.r = r; this.pts = pts; this.width = width; this.height = height; this.isLine = isLine; this.paper = paper; this.evenRows = evenRows;
        smudge = isLine && !r.line;
        int h = height;
        lo = new int[h]; hi = new int[h]; lo2 = new int[h]; hi2 = new int[h];
        java.util.Arrays.fill(lo, Integer.MAX_VALUE); java.util.Arrays.fill(hi, -1);
        java.util.Arrays.fill(lo2, Integer.MAX_VALUE); java.util.Arrays.fill(hi2, -1);
        left = new double[h]; right = new double[h]; hullLeft = new double[h]; hullRight = new double[h];
    }

    /** Writes row {@code y}'s spans (and, the second when {@code lo2[y] <= hi2[y]}) and empties them. */
    abstract void write(int y);

    /**
     * A hake whose head follows the stroke: the direction its start lies along ({@link HakeRibbon#startTravel}), NaN until
     * it has settled ({@link #holds}); and how far it settles over.
     */
    double hakeStart = Double.NaN, hakeSettle;
    /** The stroke has lifted: a hake's start waits no more. */
    boolean lifted;
    /** The following hake head this sweep lays, or null. */
    private ResolvedBrush.Body followHead() {
        ResolvedBrush.Body head = isLine ? (r.lineTip == ResolvedBrush.LineTip.HAKE ? r.lineHake : null) : r.body != null && r.body.flatHead ? r.body : null;
        return smudge || r.pattern != null || head == null || head.headFixed ? null : head;
    }
    /**
     * Whether nothing is swept yet: a following hake waits, as {@link HakeRibbon#stamp} does, until the stroke's direction
     * settles ({@link HakeRibbon#settle}) or it lifts, then lays its start along it from the first sample ({@link #to}).
     */
    boolean holds() {
        return Double.isNaN(hakeStart) && !lifted && followHead() != null && pts.total() < HakeRibbon.settle(isLine ? r.inkSize : r.bodySize);
    }

    /** Sample {@code k}'s shape, swept from the last one's; a shape with no corners ends the sweep, the next starts afresh. */
    void to(int k) {
        // A following hake's start, once its direction has settled: the first sample's shape first, then on from there.
        if (Double.isNaN(hakeStart) && followHead() != null) {
            double settle = HakeRibbon.settle(isLine ? r.inkSize : r.bodySize);
            hakeSettle = settle;
            hakeStart = HakeRibbon.startTravel(pts, Math.min(settle, pts.total()));
            // A pen that hasn't moved: the head lies across its lean, as the brush rests on the paper; else as before.
            if (Double.isNaN(hakeStart)) { double az = BrushDirection.azimuth(pts.tx[0], pts.ty[0]); hakeStart = Double.isNaN(az) ? 0 : az; }
            if (k > 0 && first) sweepTo(0);
        }
        sweepTo(k);
    }
    private void sweepTo(int k) {
        double[] tx = px, ty = py; px = qx; py = qy; qx = tx; qy = ty;
        pn = qn;
        qn = shapeAt(k, qx, qy);
        if (qn == 0) { first = true; return; }
        if (first) { first = false; fill(qx, qy, qn, null, null, 0); return; }
        int n = hull();
        fill(hx, hy, n, px, py, pn);
    }

    /** Sample {@code k}'s shape as up to {@link #MOST} corners into {@code xs, ys}, in order round it; returns how many ({@link #SIDES} here). */
    int shapeAt(int k, double[] xs, double[] ys) {
        double[] e = ellipseAt(k);
        ellipse(pts.x[k], pts.y[k], e[0], e[1], e[2], e[3], xs, ys);
        return SIDES;
    }
    /** {@link #ellipseAt}'s answer, reused. */
    private final double[] at = new double[4];
    /** Sample {@code k}'s shape, an ellipse round the sample: its half-axes a and b, turned to cos, sin. */
    double[] ellipseAt(int k) {
        int j = Math.max(0, k - 1);
        double x = pts.x[k], y = pts.y[k], d = pts.d[k], seg = (double)pts.d[k] - pts.d[j];
        double dirx = seg > 0 ? ((double)pts.x[k] - pts.x[j]) / seg : 1, diry = seg > 0 ? ((double)pts.y[k] - pts.y[j]) / seg : 0;
        double a, b, c, s;
        ResolvedBrush.Body head = isLine ? (r.lineTip == ResolvedBrush.LineTip.HAKE ? r.lineHake : null) : r.body.flatHead ? r.body : null;
        ResolvedBrush.Swarm swarm = isLine ? r.lineSwarm : r.bodyTip == ResolvedBrush.BodyTip.BRISTLES ? r.bodySwarm : null;
        if (smudge) {
            a = b = Math.max(1, r.carry.stumpR()); c = 1; s = 0;
        } else if (r.pattern != null) {
            // Stipple, Hatch and Stamp: the band their dots, lines or prints fill, pressure narrowing it as it does them.
            double hs = r.pattern.stamp() ? r.pattern.size : isLine ? r.inkSize : r.bodySize, pr = pts.p[k];
            a = b = Math.max(1, r.pattern.stipple() ? r.pattern.band(StippleStamper.band(r.pattern, hs), pr) + StippleStamper.dotSize(r.pattern, hs) / 2 : r.pattern.band(hs / 2, pr) + .5);
            c = 1; s = 0;
        } else if (swarm != null) {
            // Bristles: as wide as the hairs keep to, a hair's width past it each side.
            double radius = (isLine ? r.inkSize : r.bodySize) / 2, pr = pts.p[k];
            a = b = Math.max(1, BristleSwarm.wall(swarm, radius, pr, pts.v[k], d) + BristleSwarm.hairRadius(swarm, radius, pr)); c = 1; s = 0;
        } else if (head != null) {
            // The hake's head: as wide as it lays, as thick, across the stroke (without its turn's easing).
            double hs = isLine ? r.inkSize : r.bodySize, pr = pts.p[k], tX = pts.tx[k], tY = pts.ty[k];
            double D = BodyStamper.diameter(head, hs, pr, BrushDirection.tiltLean(tX, tY));
            if (head.gSize != 0 && k > 0) D *= StrokeMath.gSize(head.gSize, pts.ga(j, k, 1));
            double travel = HakeRibbon.travelAt(head.headFixed ? Double.NaN : hakeStart, hakeSettle, d, Math.atan2(Float.isNaN(pts.uy[k]) ? 0 : pts.uy[k], Float.isNaN(pts.ux[k]) ? 1 : pts.ux[k]));
            double th = HakeRibbon.target(head, travel, BrushDirection.azimuth(tX, tY));
            a = Math.max(1, D / 2); b = Math.max(1, HakeRibbon.thickness(head, hs, pr) / 2); c = Math.cos(th); s = Math.sin(th);
        } else if (isLine ? r.linePlaced : r.bodyTip == ResolvedBrush.BodyTip.PLACED) {
            // Placed lines or paint: as wide as they are placed.
            ResolvedBrush.Pen pen = isLine ? r.pen : r.bodyPen;
            a = b = Math.max(.6, pen.width(isLine ? r.placeSize : r.bodyPlaceSize, pts.p[k], pts.v[k]) / 2); c = 1; s = 0;
        } else if (isLine) {
            ResolvedBrush.Pen pen = r.pen;
            double big = StrokeMath.inkBig(r.inkSize), lens = StrokeMath.taperLens(pen.taperIn, pen.taperOut, r.inkSize)[0];
            LineStamper.nib(nib, pen, paper, pts, r.inkSize, big, Math.sqrt(big), lens, 0, pts.total(), false, null, pen.tilts(), j, k, 1, x, y, d, dirx, diry, width, height);
            a = nib.a; b = nib.bb; c = nib.cosA; s = nib.sinA;
        } else {
            ResolvedBrush.Body bd = r.body;
            double tX = pts.tx[k], tY = pts.ty[k], lean = 0, azimuth = Double.NaN;
            if (bd.tilt > 0 || bd.tDark != 0) { lean = BrushDirection.tiltLean(tX, tY); azimuth = BrushDirection.azimuth(tX, tY); }
            double D = BodyStamper.diameter(bd, r.bodySize, pts.p[k], bd.tilt > 0 ? lean : 0);
            double inLen = bd.taperIn * 70 * Math.max(1, r.bodySize / 24), tf = inLen > 0 ? StrokeMath.taper(d / inLen) : 1;
            double ga = (bd.gSize != 0 || bd.gDark != 0) && k > 0 ? pts.ga(j, k, 1) : 0;
            BodyStamper.shape(shape, bd, D, tf, pts.v[k], ga, lean, azimuth);
            a = shape.A; b = shape.B; c = StrictMath.cos(shape.th); s = StrictMath.sin(shape.th);
        }
        at[0] = a; at[1] = b; at[2] = c; at[3] = s;
        return at;
    }

    /** How far before the rest footprint's sample the pen came from, px: past any taper in (the biggest size's is about 1,050). */
    private static final float PAST = 100_000;
    /** Paper with no grain: a rest footprint without the paper's wobble. */
    private static StrokePaper flat;
    /**
     * {@code r}'s marks with the pen at rest, as the side column's readout shows them ({@link SwatchReadout}): upright, at
     * the pressure a hand usually draws at ({@link HoverNib#TYPICAL}), still, past its taper in, on paper with no grain;
     * the stampers' own shapes, as a quick stroke's preview sweeps them. Each mark's ellipse round the pen (the line's, or
     * a smudge's dab as the preview's; the body's): {a, b, cos, sin}. Pure.
     */
    static double[][] atRest(ResolvedBrush r) {
        if (flat == null) { byte[] grey = new byte[PaperGrain.SIDE * PaperGrain.SIDE]; java.util.Arrays.fill(grey, (byte) 128); flat = new StrokePaper(PaperGrain.scan(grey)); }
        StrokePoints pts = new StrokePoints();
        float p = (float) HoverNib.TYPICAL;
        pts.add(-PAST, 0, p, PenSpeed.UNTIMED, 0, 0);
        pts.add(0, 0, p, PenSpeed.UNTIMED, 0, 0);
        boolean line = r.line || r.body == null && r.carry != null && r.carry.moves;
        double[][] marks = new double[(line ? 1 : 0) + (r.body != null ? 1 : 0)][];
        int n = 0;
        for (boolean isLine : new boolean[]{true, false}) {
            if (isLine ? !line : r.body == null) continue;
            marks[n++] = new FootprintSweep(r, pts, 1, 1, isLine, flat, false) { @Override void write(int y) {} }.ellipseAt(1).clone();
        }
        return marks;
    }
    /** An ellipse round {@code x, y}, half-axes {@code a, b} turned to {@code c, s} (cos, sin), as {@link #SIDES} corners into {@code xs, ys}. */
    static void ellipse(double x, double y, double a, double b, double c, double s, double[] xs, double[] ys) {
        for (int i = 0; i < SIDES; i++) {
            double u = a * COS[i], q = b * SIN[i];
            xs[i] = x + u * c - q * s; ys[i] = y + u * s + q * c;
        }
    }

    /** The convex hull of the last two shapes into {@link #hx}, {@link #hy} (Andrew's monotone chain); returns its corners. */
    int hull() {
        int m = pn + qn;
        for (int i = 0; i < pn; i++) { sx[i] = px[i]; sy[i] = py[i]; }
        for (int i = 0; i < qn; i++) { sx[pn + i] = qx[i]; sy[pn + i] = qy[i]; }
        // The corners by x, then y: an insertion sort of 48, no boxing.
        for (int i = 0; i < m; i++) {
            int p = i, at = i;
            while (at > 0 && (sx[order[at - 1]] > sx[p] || sx[order[at - 1]] == sx[p] && sy[order[at - 1]] > sy[p])) { order[at] = order[at - 1]; at--; }
            order[at] = p;
        }
        int n = 0;
        for (int i = 0; i < m; i++) {
            int p = order[i];
            while (n >= 2 && cross(hx[n - 2], hy[n - 2], hx[n - 1], hy[n - 1], sx[p], sy[p]) <= 0) n--;
            hx[n] = sx[p]; hy[n] = sy[p]; n++;
        }
        for (int i = m - 2, low = n + 1; i >= 0; i--) {
            int p = order[i];
            while (n >= low && cross(hx[n - 2], hy[n - 2], hx[n - 1], hy[n - 1], sx[p], sy[p]) <= 0) n--;
            hx[n] = sx[p]; hy[n] = sy[p]; n++;
        }
        return n - 1;
    }

    /**
     * Adds the page pixels inside the convex polygon {@code xs, ys} ({@code n} corners) but not inside the convex
     * polygon {@code ox, oy} ({@code on} corners; null for none) to the rows' spans.
     */
    void fill(double[] xs, double[] ys, int n, double[] ox, double[] oy, int on) {
        int[] rows = edges(xs, ys, n, hullLeft, hullRight);
        if (rows == null) return;
        int[] old = ox != null ? edges(ox, oy, on, left, right) : null;
        int w = width;
        for (int y = rows[0]; y <= rows[1]; y++) {
            int x0 = Math.max(0, (int)Math.ceil(hullLeft[y] - .5)), x1 = Math.min(w - 1, (int)Math.floor(hullRight[y] - .5));
            if (x1 < x0) continue;
            if (old != null && y >= old[0] && y <= old[1]) {
                int e0 = (int)Math.ceil(left[y] - .5), e1 = (int)Math.floor(right[y] - .5);
                if (e0 <= e1 && e1 >= x0 && e0 <= x1) {
                    // What lies outside the last shape: on either side of it, or nothing.
                    if (x0 < e0) add(y, x0, e0 - 1);
                    if (x1 > e1) add(y, e1 + 1, x1);
                    continue;
                }
            }
            add(y, x0, x1);
        }
    }

    /** Row {@code y}'s pixels {@code a..b} are footprint: joined to a span they touch, or a span of their own (the row written first when it has two already). */
    void add(int y, int a, int b) {
        // A sparse dither has no dots on odd rows.
        if (evenRows && (y & 1) != 0) return;
        if (y < top) top = y;
        if (y > bottom) bottom = y;
        if (lo[y] > hi[y]) { lo[y] = a; hi[y] = b; return; }
        if (b >= lo[y] - 1 && a <= hi[y] + 1) {
            lo[y] = Math.min(lo[y], a); hi[y] = Math.max(hi[y], b);
            // Grown to meet the second span: one span.
            if (lo2[y] <= hi2[y] && hi2[y] >= lo[y] - 1 && lo2[y] <= hi[y] + 1) { lo[y] = Math.min(lo[y], lo2[y]); hi[y] = Math.max(hi[y], hi2[y]); lo2[y] = Integer.MAX_VALUE; hi2[y] = -1; }
            return;
        }
        if (lo2[y] > hi2[y]) { lo2[y] = a; hi2[y] = b; return; }
        if (b >= lo2[y] - 1 && a <= hi2[y] + 1) {
            lo2[y] = Math.min(lo2[y], a); hi2[y] = Math.max(hi2[y], b);
            if (hi2[y] >= lo[y] - 1 && lo2[y] <= hi[y] + 1) { lo[y] = Math.min(lo[y], lo2[y]); hi[y] = Math.max(hi[y], hi2[y]); lo2[y] = Integer.MAX_VALUE; hi2[y] = -1; }
            return;
        }
        write(y);
        lo[y] = a; hi[y] = b;
    }

    /** Writes every row's spans. */
    void flush() {
        for (int y = top; y <= bottom; y++) if (lo[y] <= hi[y]) write(y);
        top = Integer.MAX_VALUE; bottom = -1;
    }

    /** The polygon's left and right edge at each pixel row it covers (by the row's middle), into {@code left, right}; returns its first and last row, or null for none. */
    int[] edges(double[] xs, double[] ys, int n, double[] left, double[] right) {
        int t = 0, b = 0;
        for (int i = 1; i < n; i++) { if (ys[i] < ys[t]) t = i; if (ys[i] > ys[b]) b = i; }
        int y0 = Math.max(0, (int)Math.ceil(ys[t] - .5)), y1 = Math.min(height - 1, (int)Math.floor(ys[b] - .5));
        if (y1 < y0) return null;
        // The two chains from the top corner to the bottom one, each edge setting its rows.
        chain(xs, ys, n, t, b, 1, left, y0, y1);
        chain(xs, ys, n, t, b, n - 1, right, y0, y1);
        for (int y = y0; y <= y1; y++) if (left[y] > right[y]) { double s = left[y]; left[y] = right[y]; right[y] = s; }
        return new int[]{y0, y1};
    }
    /** Walks the polygon from corner {@code from} to {@code to}, stepping {@code step} (1 or n − 1), setting {@code at}[row] to the edge's x at each row's middle. */
    private static void chain(double[] xs, double[] ys, int n, int from, int to, int step, double[] at, int y0, int y1) {
        for (int i = from; i != to; ) {
            int k = (i + step) % n;
            double ya = ys[i], yb = ys[k];
            if (yb > ya) {
                int r0 = Math.max(y0, (int)Math.ceil(ya - .5)), r1 = Math.min(y1, (int)Math.ceil(yb - .5) - 1);
                double slope = (xs[k] - xs[i]) / (yb - ya);
                for (int y = r0; y <= r1; y++) at[y] = xs[i] + (y + .5 - ya) * slope;
            }
            i = k;
        }
        // The bottom corner's row, if its middle is the corner itself.
        int last = (int)Math.ceil(ys[to] - .5);
        if (last >= y0 && last <= y1 && last + .5 == ys[to]) at[last] = xs[to];
    }
    static double cross(double ax, double ay, double bx, double by, double cx, double cy) { return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax); }

    private static final double[] COS = new double[SIDES], SIN = new double[SIDES];
    static {
        for (int i = 0; i < SIDES; i++) { COS[i] = Math.cos(2 * Math.PI * i / SIDES); SIN[i] = Math.sin(2 * Math.PI * i / SIDES); }
    }
}
