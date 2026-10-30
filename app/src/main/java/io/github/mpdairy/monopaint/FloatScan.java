package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.List;

/**
 * How much floating paint covers each pixel (float.js Bath.render): a scanline sweep over the rings' edges that finds,
 * along each row, the topmost ring (the newest whose outline the row is inside, even-odd per ring) and adds its grey
 * times the exact share of the pixel it covers. {@code samples} rows of it a pixel row: 4 for the full show and the
 * print, 1 while the pen is down or the page drifts. So an edge is at most one in-between grey, and nothing is drawn
 * twice. Bands of rows share out over {@link Cores}; each pixel is the same however they are cut. Pure.
 */
final class FloatScan {
    private FloatScan() { }

    /**
     * The film over page pixels {@code x0..x1, y0..y1} (inclusive) into {@code out}, row by row ({@code (x1 − x0 + 1)}
     * a row): each pixel's darkness 0–1 under the floating paint. Returns whether any is above 0.
     */
    static boolean film(List<FloatBath.Ring> rings, int x0, int y0, int x1, int y1, int samples, float[] out) {
        int rw = x1 - x0 + 1, rh = y1 - y0 + 1;
        Arrays.fill(out, 0, rw * rh, 0);
        if (rw <= 0 || rh <= 0) return false;
        // The edges of every ring reaching the rectangle, bucketed by the row they start in.
        int nr = rings.size(), ne = 0;
        boolean[] used = new boolean[nr];
        for (int r = 0; r < nr; r++) {
            FloatBath.Ring g = rings.get(r);
            if (g.box[3] < y0 || g.box[1] > y1 + 1 || g.box[2] < x0 || g.box[0] > x1 + 1) continue;
            used[r] = true;
            for (int c = 0; c < g.n.length; c++) ne += g.n[c];
        }
        if (ne == 0) return false;
        Edges e = new Edges(ne);
        float[] dark = new float[nr];
        for (int r = 0; r < nr; r++) {
            if (!used[r]) continue;
            FloatBath.Ring g = rings.get(r);
            dark[r] = g.dark;
            for (int c = 0; c < Math.min(g.n.length, Math.min(g.x.length, g.y.length)); c++) {
                float[] X = g.x[c], Y = g.y[c];
                // The bath can grow a ring while a print reads it (a crash on the Manta, 2026-10-22): read only what both arrays hold.
                for (int i = 0, n = Math.min(g.n[c], Math.min(X.length, Y.length)), j = n - 1; i < n; j = i++) {
                    double xa = X[j], ya = Y[j], xb = X[i], yb = Y[i];
                    if (ya == yb) continue;
                    if (ya > yb) { double t = xa; xa = xb; xb = t; t = ya; ya = yb; yb = t; }
                    if (yb <= y0 || ya >= y1 + 1) continue;
                    e.add(xa, ya, yb, (xb - xa) / (yb - ya), r);
                }
            }
        }
        if (e.n == 0) return false;
        boolean[] any = new boolean[1];
        // Bands of 32 rows; each band its own active list and scratch.
        long area = (long) rw * rh * samples;
        Cores.inBands(y0, y1, 32, area, (top, bottom, worker) -> {
            if (band(e, dark, x0, rw, top, bottom, y0, samples, out)) synchronized (any) { any[0] = true; }
        });
        return any[0];
    }

    /** The edges: x at the top end, the y span, the slope, the ring. */
    private static final class Edges {
        final double[] x, y0, y1, slope; final int[] ring; int n;
        Edges(int cap) { x = new double[cap]; y0 = new double[cap]; y1 = new double[cap]; slope = new double[cap]; ring = new int[cap]; }
        void add(double xa, double ya, double yb, double s, int r) { if (n == x.length) return; x[n] = xa; y0[n] = ya; y1[n] = yb; slope[n] = s; ring[n] = r; n++; }
    }

    /** Rows {@code top..bottom} of the film. */
    private static boolean band(Edges e, float[] dark, int x0, int rw, int top, int bottom, int oy, int samples, float[] out) {
        int rows = bottom - top + 1, nr = dark.length;
        // The edges reaching the band, by the band row they start in (or the first).
        int[] start = new int[rows], next = new int[e.n];
        Arrays.fill(start, -1);
        for (int k = 0; k < e.n; k++) {
            if (e.y1[k] <= top || e.y0[k] >= bottom + 1) continue;
            int row = Math.max(0, Math.min(rows - 1, (int) Math.floor(e.y0[k]) - top));
            next[k] = start[row]; start[row] = k;
        }
        int[] active = new int[64];
        int na = 0;
        long[] keys = new long[64];
        byte[] par = new byte[nr];
        double[] acc = new double[rw + 1];
        double left = x0 - 2, right = x0 + rw + 2;
        boolean any = false;
        for (int y = 0; y < rows; y++) {
            for (int k = start[y]; k >= 0; k = next[k]) { if (na == active.length) active = Arrays.copyOf(active, na * 2); active[na++] = k; }
            Arrays.fill(acc, 0);
            boolean row = false;
            for (int sub = 0; sub < samples; sub++) {
                double ys = top + y + (sub + .5) / samples;
                int nk = 0;
                for (int a = 0; a < na; a++) {
                    int k = active[a];
                    if (e.y0[k] > ys || e.y1[k] <= ys) continue;
                    double x = e.x[k] + (ys - e.y0[k]) * e.slope[k];
                    if (nk == keys.length) keys = Arrays.copyOf(keys, nk * 2);
                    keys[nk++] = (Math.round((Math.min(right, Math.max(left, x)) - x0 + 4) * 256) << 20) | e.ring[k];
                }
                if (nk == 0) continue;
                Arrays.sort(keys, 0, nk);
                int topR = -1;
                double prevX = 0, v = 0;
                for (int q = 0; q < nk; q++) {
                    long key = keys[q];
                    int ri = (int) (key & 0xfffff);
                    double x = (key >>> 20) / 256.0 - 4;
                    if (topR >= 0 && (v = dark[topR]) > 0) {
                        double a = Math.max(0, prevX), b = Math.min(rw, x);
                        if (b > a) {
                            v /= samples;
                            int ia = (int) a, ib = (int) b;
                            if (ia == ib) acc[ia] += (b - a) * v;
                            else { acc[ia] += (ia + 1 - a) * v; for (int p = ia + 1; p < ib; p++) acc[p] += v; if (ib < rw) acc[ib] += (b - ib) * v; }
                            row = true;
                        }
                    }
                    par[ri] ^= 1;
                    if (par[ri] != 0) { if (ri > topR) topR = ri; }
                    else if (ri == topR) { topR = -1; for (int r2 = ri - 1; r2 >= 0; r2--) if (par[r2] != 0) { topR = r2; break; } }
                    prevX = x;
                }
                for (int q = 0; q < nk; q++) par[(int) (keys[q] & 0xfffff)] = 0;
            }
            if (row) {
                any = true;
                for (int x = 0, o = (top + y - oy) * rw; x < rw; x++, o++) out[o] = (float) Math.min(1, acc[x]);
            }
            // Edges ending in this row leave.
            double end = top + y + 1;
            int m = 0;
            for (int a = 0; a < na; a++) if (e.y1[active[a]] > end) active[m++] = active[a];
            na = m;
        }
        return any;
    }
}
