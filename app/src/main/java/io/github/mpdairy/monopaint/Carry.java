package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;

/**
 * Smudge (the web lab's Carry, stroke.js carryPass and carryDab, as the Smudge spec tuned them): a dab-sized reservoir
 * that travels with the brush, trading paint with the stroke's own layer ({@link CarryPlane}). Each cell, {@code
 * stride} px of the dab, meets the page under it every dab: where the page is darker the brush takes Pick up of the
 * difference, where the brush is darker it gives Lay down of it, each over a radius of travel; what it takes is gone
 * from the page and what it gives is gone from the brush, so a line it crosses is carried on along the stroke and fades
 * as it is laid down. Lift takes twice as readily and soaks up 1 − Lay down of what it holds over a radius of
 * travel (a damp brush), so the track lightens.
 * Pressure scales all of it. Coverage and darkness trade together (premultiplied), so a smear thins a layer rather
 * than painting a light patch over what lies below. The reservoir starts each stroke empty: a cell's first touch
 * reads the page, so a stroke never smears at its very start (Lift's starts clean and lifts at once). Pure, checked on
 * the desktop.
 *
 * <p>Keeps Paint (Drag's and Smear's, the wet smudge spec): a dirty brush. The reservoir starts with what the holder's
 * last stroke ended with ({@link HeldPaint}): a cell's first touch reads the page and the held paint, Keeps Paint of
 * the way from one to the other, so the stroke lays what the last one picked up; {@link #kept} is what it ends with.
 *
 * <p>Clear Water (the wet smudge spec) lifts as Lift does, the brush starting clean, and marks how wet it leaves each
 * pixel on the plane ({@link CarryPlane#wetTile}); at lift {@link ClearWater} lets what it lifted run out through that
 * wet. Over a wash still wet when the stroke began ({@code stillWet}) it lifts more readily, and all of that wash: it
 * isn't set yet ({@link CarryPlane#water}).
 *
 * <p>Smear's Soften (the Stump spec) works part or all of it as MonoPaint's blending stump ({@code ToneDabs.soften}):
 * each dab moves every pixel under it toward the page around it (a box a fifth of the radius across, 2 to 8 px) as
 * found a quarter of the radius behind, along the stroke, so marks soften in place and pull on a little the way the
 * stroke rubs, keeping their darkness where they were. Pick up sets how strongly (MonoPaint's strength), pressure scales
 * it, and it is spread over a stroke's dabs as MonoPaint's are over its own (a dab every .24 r). The reservoir trades
 * {@code 1 − soften} of what it would; at Soften 1 there is none. A stump smaller than MonoPaint's (under 20 px across)
 * reaches at least 3 px for its box and its pull, and works more of its dab ({@link ResolvedBrush.Carry#small}).
 */
final class Carry {
    private final ResolvedBrush.Carry c;
    private final CarryPlane plane;
    /** Each cell's coverage and darkness, NaN until first touched. */
    private final float[] cellA, cellP;
    private final int side;
    /** The stroke's distance of the next dab. */
    private double next;
    /** Keeps Paint: the held paint each cell starts from, NaN where none; null for a clean brush or none kept. */
    private final float[] heldA, heldP;
    /** Clear Water: it lifts as Lift does and marks the wet; the page box a wash was still wet in when it began, or null. */
    private final boolean water;
    private final int[] stillWet;

    Carry(ResolvedBrush.Carry c, CarryPlane plane) { this(c, plane, null, null); }
    /** As {@link #Carry(ResolvedBrush.Carry, CarryPlane)}, starting with {@code held} (a dirty brush's paint, or null) and lifting more readily over {@code stillWet} (or null). */
    Carry(ResolvedBrush.Carry c, CarryPlane plane, HeldPaint.Reservoir held, int[] stillWet) {
        this.c = c; this.plane = plane;
        side = 2 * c.n + 1;
        cellA = new float[side * side]; cellP = new float[side * side];
        java.util.Arrays.fill(cellA, Float.NaN); java.util.Arrays.fill(cellP, Float.NaN);
        HeldPaint.Reservoir h = held != null && c.keepsPaint() ? held.sized(c.n) : null;
        heldA = h == null ? null : h.a; heldP = h == null ? null : h.p;
        water = c.water();
        this.stillWet = water ? stillWet : null;
        if (water) plane.water(this.stillWet);
    }
    /**
     * Keeps Paint: what the brush ends the stroke holding, for its next ({@link HeldPaint}): each cell as the stroke left
     * it, one it never reached as it was held; null when it keeps none (Keeps Paint off) or never touched the page.
     */
    HeldPaint.Reservoir kept() {
        if (!c.keepsPaint()) return null;
        float[] a = cellA.clone(), p = cellP.clone();
        boolean any = false;
        for (int j = 0; j < a.length; j++) {
            if (a[j] != a[j] && heldA != null) { a[j] = heldA[j]; p[j] = heldP[j]; }
            any |= a[j] == a[j];
        }
        return any ? new HeldPaint.Reservoir(c.n, a, p) : null;
    }

    /** The dabs between samples {@code from − 1} and the newest, every {@link ResolvedBrush.Carry#spacing} px; returns the page rectangle they changed (empty for none). */
    int[] pass(StrokePoints pts, int from, int width, int height) {
        int[] rect = StrokeMath.emptyRect(width, height);
        for (int k = Math.max(1, from); k < pts.count; k++) {
            double d0 = pts.d[k - 1], d1 = pts.d[k], seg = d1 - d0;
            while (next <= d1) {
                double t = seg > 0 ? clamp((next - d0) / seg) : 1;
                double x = pts.x[k - 1] + (pts.x[k] - (double) pts.x[k - 1]) * t, y = pts.y[k - 1] + (pts.y[k] - (double) pts.y[k - 1]) * t;
                double pr = pts.p[k - 1] + (pts.p[k] - (double) pts.p[k - 1]) * t;
                if (c.soften < 1) dab(x, y, pr, width, height, rect);
                if (c.soften > 0) {
                    double len = Math.hypot(pts.x[k] - (double) pts.x[k - 1], pts.y[k] - (double) pts.y[k - 1]);
                    soften(x, y, pr, len > 0 ? (pts.x[k] - pts.x[k - 1]) / len : 0, len > 0 ? (pts.y[k] - pts.y[k - 1]) / len : 0, width, height, rect);
                }
                next += c.spacing;
            }
        }
        return rect;
    }

    /** A dab's own: where it is, what it trades, and how it lifts; set by {@link #dab}, read by {@link #rows}. */
    private double x, y, take, give, soak, edge, takeWet;
    private boolean lift;
    private int x0, x1;
    /** The dab's columns from x0: dx² and the cell column's offset (with n), from {@link #dab}. */
    private double[] colD2 = new double[0];
    private int[] colJ = new int[0];
    /** A dab covering this many pixels or more shares its rows out over {@link Cores}. */
    static final double SHARE_PX = 4000;

    /** One dab at {@code x, y} with pressure travel {@code pr}, grown into {@code rect}. */
    private void dab(double x, double y, double pr, int width, int height, int[] rect) {
        double r = c.r, press = .45 + .55 * pr, pass = 1 / c.hits;
        this.x = x; this.y = y; edge = Math.max(1, r * .3);
        lift = c.lifts();
        take = 1 - Math.pow(1 - Math.min(.95, (lift ? 2 : 1) * c.pick * press), pass);
        // Clear Water over a wash still wet: the paint comes up more readily.
        takeWet = water ? 1 - Math.pow(1 - Math.min(.95, 2 * ClearWater.WET_LIFT * c.pick * press), pass) : take;
        // Lift soaks up 1 − Lay down of what it holds over a radius of travel: it stays damp, so it keeps lifting.
        give = 1 - Math.pow(1 - c.lay * press, pass); soak = lift ? 1 - Math.pow(c.lay * press, pass) : 0;
        // Part stump: the reservoir trades the rest.
        if (c.soften > 0) { take *= 1 - c.soften; give *= 1 - c.soften; }
        x0 = Math.max(0, (int) Math.floor(x - r)); x1 = Math.min(width - 1, (int) Math.ceil(x + r));
        int y0 = Math.max(0, (int) Math.floor(y - r)), y1 = Math.min(height - 1, (int) Math.ceil(y + r));
        if (x1 < x0 || y1 < y0) return;
        // It may lay what it carries anywhere it reaches.
        plane.unblank(x0, y0, x1, y1);
        // Each column's reach across and cell, the same for every row: worked out once a dab.
        if (colD2.length < x1 - x0 + 1) { colD2 = new double[x1 - x0 + 1]; colJ = new int[x1 - x0 + 1]; }
        for (int px = x0; px <= x1; px++) { double dx = px + .5 - x; colD2[px - x0] = dx * dx; colJ[px - x0] = (int) Math.round(dx / c.stride) + c.n; }
        if (Math.PI * r * r < SHARE_PX || !Cores.shared) rows(y0, y1);
        else {
            // A big dab in bands of whole cell rows (each cell is in one band, met in raster order as on one core):
            // the page is the same however the bands are run. Its tiles are filled first, on this thread.
            plane.reach(x0, y0, x1, y1);
            int[] tops = new int[y1 - y0 + 2];
            int n = 0;
            for (int py = y0, last = Integer.MIN_VALUE; py <= y1; py++) { int k = cellRow(py); if (k != last) { tops[n++] = py; last = k; } }
            int pieces = Math.min(n, 4 * Cores.WORKERS), per = (n + pieces - 1) / pieces;
            tops[n] = y1 + 1;
            int[] cut = tops; int cells = n;
            Cores.each((n + per - 1) / per, (piece, worker) -> rows(cut[piece * per], cut[Math.min(cells, (piece + 1) * per)] - 1));
        }
        StrokeMath.grow(rect, new int[]{x0, y0, x1, y1});
    }
    /** The reservoir's row of cells page row {@code py} meets. */
    private int cellRow(int py) { return (int) Math.round((py + .5 - y) / c.stride); }

    /** The dab's page rows {@code ya..yb}: every pixel within its radius trades with the cell over it. */
    private void rows(int ya, int yb) {
        double r = c.r, x = this.x, y = this.y, take = this.take, give = this.give, soak = this.soak, edge = this.edge, inner = (r - edge) * (r - edge);
        boolean lift = this.lift, water = this.water;
        float[] heldA = this.heldA, heldP = this.heldP;
        double keeps = c.keeps, takeWet = this.takeWet;
        int[] wetBox = stillWet;
        int n = c.n, T = CarryPlane.SIDE, missed = 0, x0 = this.x0;
        double[] colD2 = this.colD2;
        int[] colJ = this.colJ;
        for (int py = ya; py <= yb; py++) {
            double dy = py + .5 - y;
            int jy = (cellRow(py) + n) * side, ty = py / T, row = (py % T) * T;
            boolean wetRow = wetBox != null && py >= wetBox[1] && py <= wetBox[3];
            for (int xs = x0; xs <= x1; ) {
                int tx = xs / T, xe = Math.min(x1, tx * T + T - 1);
                float[] A = plane.coverageTile(tx, ty), P = A == null ? null : plane.darknessTile(tx, ty);
                byte[] F = A == null ? null : plane.freshTile(tx, ty);
                float[] Wt = A == null || !water ? null : plane.wetTile(tx, ty);
                for (int px = xs; px <= xe; px++) {
                    double d2 = colD2[px - x0] + dy * dy;
                    if (d2 >= r * r) continue;
                    if (A == null) { missed++; continue; }
                    // Inside the soft edge the whole trade; on it, by how far in (no root needed inside).
                    double e = d2 < inner ? 1 : clamp((r - Math.sqrt(d2)) / edge);
                    int j = jy + colJ[px - x0], i = row + px % T;
                    double ua = A[i], up = P[i], ba = cellA[j], bp = cellP[j];
                    // A cell's first touch reads the page, so a stroke never smears at its very start; Lift's starts clean, so it lifts at once.
                    if (ba != ba) {
                        ba = lift ? 0 : ua; bp = lift ? 0 : up;
                        // A dirty brush: the paint it held, Keeps Paint of the way from the page to it.
                        if (heldA != null && heldA[j] == heldA[j]) { ba += (heldA[j] - ba) * keeps; bp += (heldP[j] - bp) * keeps; }
                    }
                    // The page darker than the brush: it takes; the brush darker: it gives.
                    boolean takes = up > bp;
                    double tk = wetRow && px >= wetBox[0] && px <= wetBox[2] ? takeWet : take;
                    double f = (takes ? tk : give) * e, keep = 1 - soak * e;
                    if (Wt != null && e > Wt[i]) Wt[i] = (float) e;
                    float na = (float) (ua + (ba - ua) * f), np = (float) (up + (bp - up) * f);
                    if (na != A[i] || np != P[i]) { A[i] = na; P[i] = np; F[i] = 1; }
                    cellA[j] = (float) ((ba - (ba - ua) * f) * keep); cellP[j] = (float) ((bp - (bp - up) * f) * keep);
                }
                xs = xe + 1;
            }
        }
        if (missed > 0) plane.missed(missed);
    }

    // ---------- Soften: MonoPaint's blending stump (ToneDabs.soften) on the plane ----------
    /** Each spot's page coverage and darkness over its box, summed (a summed-area table, a row and a column of 0 first), one after another. */
    private double[] sumA = new double[0], sumP = new double[0];
    /** The dab's spots (one for a round stump, one a tine for a comb): centre, radius, box (left, top, right, bottom, exclusive) and table (start, row length). */
    private double[] spotX = new double[0], spotY = new double[0];
    private int[] box = new int[0], table = new int[0];
    /** Each spot whose table reads only blank tiles ({@link CarryPlane#blank}): it moves nothing, so it isn't worked. */
    private boolean[] idle = new boolean[0];
    private int spots;
    /** The stump dab's heading, strength, pull (px, along the stroke), blur reach and page size; read by {@link #softRows}. */
    private double sux, suy, sWeight, spotR;
    private int pullX, pullY, blur, pageW, pageH;
    /** A small stump's flatter falloff ({@link ResolvedBrush.Carry#flatter}), 0 for MonoPaint's. */
    private double flatter;

    /**
     * One stump dab at {@code x, y}, pressure travel {@code pr}, the stroke heading {@code ux, uy} (0, 0 at its start):
     * every pixel within the radius moves toward the mean of the page around the pixel a quarter radius behind (80%) and
     * around itself (20%), by {@code .7 × strength × (1 − q)²} at {@code q = d² / r²}, all read before any is written. A
     * comb does this at each tine, a stump of the tine's width, the tine taking only what it meets behind it, by {@code
     * .7 × strength × (1 − q)}, so its streaks run on.
     */
    private void soften(double x, double y, double pr, double ux, double uy, int width, int height, int[] rect) {
        double press = .45 + .55 * pr;
        double[] tines = c.tines;
        // A comb's tines lie across the way it moves: none before it moves.
        if (tines != null && ux == 0 && uy == 0) return;
        spotR = tines != null ? c.tineReach() : c.r;
        // MonoPaint's .7 × strength at its dab spacing (.12 of the width), spread over this stroke's dabs.
        double w = clamp(.7 * c.soften * c.pick * press);
        sWeight = 1 - Math.pow(1 - w, c.spacing / Math.max(.5, .24 * spotR));
        if (!(sWeight > 0)) return;
        sux = ux; suy = uy; blur = c.blur(); flatter = c.flatter(); pageW = width; pageH = height;
        double pull = c.pullReach();
        pullX = (int) Math.round(ux * pull); pullY = (int) Math.round(uy * pull);
        spots = tines != null ? tines.length : 1;
        if (spotX.length < spots) { spotX = new double[spots]; spotY = new double[spots]; box = new int[4 * spots]; table = new int[2 * spots]; idle = new boolean[spots]; }
        int need = 0;
        for (int k = 0; k < spots; k++) {
            double at = tines != null ? tines[k] : 0;
            spotX[k] = x - at * uy; spotY[k] = y + at * ux;
            int x0 = Math.max(0, (int) Math.floor(spotX[k] - spotR)), x1 = Math.min(width, (int) Math.ceil(spotX[k] + spotR));
            int y0 = Math.max(0, (int) Math.floor(spotY[k] - spotR)), y1 = Math.min(height, (int) Math.ceil(spotY[k] + spotR));
            box[4 * k] = x0; box[4 * k + 1] = y0; box[4 * k + 2] = x1; box[4 * k + 3] = y1;
            if (x1 <= x0 || y1 <= y0) { table[2 * k + 1] = 0; continue; }
            int sl = Math.max(0, x0 - blur - Math.abs(pullX)), st = Math.max(0, y0 - blur - Math.abs(pullY));
            int sr = Math.min(width, x1 + blur + Math.abs(pullX)), sb = Math.min(height, y1 + blur + Math.abs(pullY));
            table[2 * k] = need; table[2 * k + 1] = sr - sl + 1;
            need += (sr - sl + 1) * (sb - st + 1);
        }
        if (sumA.length < need) { sumA = new double[need]; sumP = new double[need]; }
        // Every spot's table before any pixel is written: which read only blank tiles first, then their sums (a comb's
        // tines on several cores, each its own part of the table).
        int T = CarryPlane.SIDE;
        for (int k = 0; k < spots; k++) {
            int sw = table[2 * k + 1];
            if (sw == 0) continue;
            int sl = left(k), st = top(k), sr = sl + sw - 1, sb = Math.min(height, box[4 * k + 3] + blur + Math.abs(pullY));
            plane.reach(sl, st, sr - 1, sb - 1);
            // Over paper, or paint none of which moves (permanent, shielded), every mean is nothing: the spot moves nothing.
            idle[k] = plane.blank(sl, st, sr - 1, sb - 1);
        }
        Cores.Piece sums = (k, worker) -> {
            int sw = table[2 * k + 1];
            if (sw == 0 || idle[k]) return;
            int sl = left(k), st = top(k), sr = sl + sw - 1, sb = Math.min(height, box[4 * k + 3] + blur + Math.abs(pullY)), base = table[2 * k], missed = 0;
            java.util.Arrays.fill(sumA, base, base + sw, 0); java.util.Arrays.fill(sumP, base, base + sw, 0);
            for (int py = st; py < sb; py++) {
                double rowA = 0, rowP = 0;
                int o = base + (py - st + 1) * sw, above = o - sw;
                sumA[o] = 0; sumP[o] = 0;
                // A tile's run of the row at a time: its tile looked up once.
                for (int px = sl, ty = py / T, row = (py % T) * T; px < sr; ) {
                    int tx = px / T, end = Math.min(sr, tx * T + T);
                    float[] A = plane.coverageTile(tx, ty), P = A == null ? null : plane.darknessTile(tx, ty);
                    if (A == null) missed += end - px;
                    for (int i = row + px % T, j = px - sl + 1; px < end; px++, i++, j++) {
                        if (A != null) { rowA += A[i]; rowP += P[i]; }
                        sumA[o + j] = sumA[above + j] + rowA; sumP[o + j] = sumP[above + j] + rowP;
                    }
                }
            }
            if (missed > 0) plane.missed(missed);
        };
        if (tines != null && Cores.shared) Cores.each(spots, sums);
        else for (int k = 0; k < spots; k++) sums.run(k, 0);
        for (int k = 0; k < spots; k++) {
            if (table[2 * k + 1] == 0) continue;
            int y0 = box[4 * k + 1], y1 = box[4 * k + 3];
            if (!idle[k]) plane.unblank(box[4 * k], y0, box[4 * k + 2] - 1, y1 - 1);
            StrokeMath.grow(rect, new int[]{box[4 * k], y0, box[4 * k + 2] - 1, y1 - 1});
        }
        // Each pixel reads only the tables, and a comb's is its nearest tine's alone: the tines on several cores, a round
        // stump past STUMP_SHARE_PX in bands of rows, the same page however they run.
        if (tines != null) {
            Cores.Piece tine = (k, worker) -> { if (table[2 * k + 1] != 0 && !idle[k]) softRows(k, box[4 * k + 1], box[4 * k + 3] - 1); };
            if (Cores.shared) Cores.each(spots, tine);
            else for (int k = 0; k < spots; k++) tine.run(k, 0);
        } else if (table[1] != 0 && !idle[0]) {
            int y0 = box[1], y1 = box[3];
            if (Math.PI * spotR * spotR < STUMP_SHARE_PX || !Cores.shared) softRows(0, y0, y1 - 1);
            else {
                int pieces = Math.min(y1 - y0, 4 * Cores.WORKERS), per = (y1 - y0 + pieces - 1) / pieces, last = y1 - 1;
                Cores.each((y1 - y0 + per - 1) / per, (piece, worker) -> softRows(0, y0 + piece * per, Math.min(last, y0 + (piece + 1) * per - 1)));
            }
        }
    }
    /**
     * A round stump covering this many pixels or more shares its rows out over {@link Cores}: each pixel takes two means of
     * the table, several times a trade's pixel ({@link #SHARE_PX}), so a smaller dab is worth sharing.
     */
    static final double STUMP_SHARE_PX = 1000;
    /** Spot {@code k}'s table's left and top page column and row. */
    private int left(int k) { return Math.max(0, box[4 * k] - blur - Math.abs(pullX)); }
    private int top(int k) { return Math.max(0, box[4 * k + 1] - blur - Math.abs(pullY)); }

    /** Spot {@code k}'s page rows {@code ya..yb}: each pixel within its radius, and nearer it than any other spot, moved toward its means. */
    private void softRows(int k, int ya, int yb) {
        double R2 = spotR * spotR, wgt = sWeight, cx = spotX[k], cy = spotY[k], flat = flatter;
        boolean comb = c.tines != null;
        // The round stump mixes in 20% of the page around the pixel itself; a tine drags only what it meets behind it.
        double own = comb ? 0 : .2;
        int T = CarryPlane.SIDE, missed = 0, W = pageW, H = pageH, x0 = box[4 * k], x1 = box[4 * k + 2];
        // The spot's table, read once ({@link #mean}).
        int sl = left(k), st = top(k), sw = table[2 * k + 1], base = table[2 * k], sb = Math.min(pageH, box[4 * k + 3] + blur + Math.abs(pullY));
        double[] m = new double[2];
        for (int py = ya; py <= yb; py++) {
            double dy = py + .5 - cy;
            // The tile under the pixel, looked up once a tile's run (lazily: only a pixel within the spot fills one).
            int tile = -1;
            float[] A = null, P = null;
            byte[] F = null;
            for (int px = x0; px < x1; px++) {
                double dx = px + .5 - cx, d2 = dx * dx + dy * dy, q = d2 / R2;
                if (q >= 1 || comb && nearer(k, px + .5, py + .5, d2)) continue;
                if (px / T != tile) {
                    tile = px / T;
                    A = plane.coverageTile(tile, py / T);
                    P = A == null ? null : plane.darknessTile(tile, py / T);
                    F = A == null ? null : plane.freshTile(tile, py / T);
                }
                if (A == null) { missed++; continue; }
                mean(sl, st, sw, base, sb, px, py, m);
                double ma = m[0], mp = m[1];
                if (pullX != 0 || pullY != 0) {
                    // Mostly from behind the moving stump, carrying tones forward; the source stays on the page.
                    mean(sl, st, sw, base, sb, Math.max(0, Math.min(W - 1, px - pullX)), Math.max(0, Math.min(H - 1, py - pullY)), m);
                    ma = own * ma + (1 - own) * m[0]; mp = own * mp + (1 - own) * m[1];
                }
                int i = (py % T) * T + px % T;
                // A fully flattened falloff is (1 − q)⁰, 1, without the pow.
                double f = wgt * (1 - q) * (comb ? 1 : flat == 0 ? 1 - q : flat == 1 ? 1 : Math.pow(1 - q, 1 - flat));
                float na = (float) (A[i] + (ma - A[i]) * f), np = (float) (P[i] + (mp - P[i]) * f);
                if (na != A[i] || np != P[i]) { A[i] = na; P[i] = np; F[i] = 1; }
            }
        }
        if (missed > 0) plane.missed(missed);
    }
    /** Whether another spot than {@code k} is nearer page point {@code x, y} than {@code d2} (squared): the pixel is that tine's. */
    private boolean nearer(int k, double x, double y, double d2) {
        for (int j = 0; j < spots; j++) {
            if (j == k) continue;
            double dx = x - spotX[j], dy = y - spotY[j], e = dx * dx + dy * dy;
            if (e < d2 || e == d2 && j < k) return true;
        }
        return false;
    }
    /**
     * A spot's table's mean coverage and darkness over the blur box about page pixel {@code px, py}, clipped to the
     * table, into {@code out}: the table's left and top page column and row, row length, start and bottom row (exclusive).
     */
    private void mean(int sl, int st, int sw, int base, int sb, int px, int py, double[] out) {
        int sr = sl + sw - 1;
        int x0 = Math.max(sl, px - blur) - sl, y0 = Math.max(st, py - blur) - st;
        int x1 = Math.min(sr, px + blur + 1) - sl, y1 = Math.min(sb, py + blur + 1) - st;
        double n = (x1 - x0) * (double) (y1 - y0);
        int a = base + y1 * sw + x1, b = base + y0 * sw + x1, c0 = base + y1 * sw + x0, d = base + y0 * sw + x0;
        out[0] = (sumA[a] - sumA[b] - sumA[c0] + sumA[d]) / n; out[1] = (sumP[a] - sumP[b] - sumP[c0] + sumP[d]) / n;
    }
}
