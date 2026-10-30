package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The film a Float stroke lays (float.js traceFilm): its line or body, as the stampers drew it, traced at half coverage
 * into closed outlines by marching squares, so it floats as a ring of its own. The field is padded by a cell of nothing,
 * so every outline closes; each edge point joins exactly two segments, so loops are walked by adjacency; a saddle is
 * split by the mean of its four corners. Pure.
 */
final class FloatTrace {
    private FloatTrace() { }

    /**
     * The outlines where {@code buffer} crosses {@code th} over lattice points {@code lx0..lx1, ly0..ly1} (the buffer's
     * own lattice): each loop as {x[], y[]} in page px, a lattice point at the middle of its page pixel. Also the mean of
     * {@code min(cap, v)} over the points at or above {@code th} into {@code mean[0]} and their count into {@code mean[1]}.
     */
    static List<float[][]> trace(FloatTiles buffer, int lx0, int ly0, int lx1, int ly1, double th, double cap, double[] mean) {
        int rw = lx1 - lx0 + 1, rh = ly1 - ly0 + 1;
        List<float[][]> loops = new ArrayList<>();
        mean[0] = 0; mean[1] = 0;
        if (rw <= 0 || rh <= 0) return loops;
        float[] f = new float[rw * rh];
        // Each row's first and last point at or above th (lo > hi: none): the cells to visit, the stroke's footprint.
        int[] lo = new int[rh + 2], hi = new int[rh + 2];
        java.util.Arrays.fill(lo, Integer.MAX_VALUE); java.util.Arrays.fill(hi, -1);
        double sum = 0; int count = 0;
        for (int y = 0; y < rh; y++) {
            buffer.row(lx0, ly0 + y, rw, f, y * rw);
            for (int x = 0, i = y * rw; x < rw; x++, i++) if (f[i] >= th) { sum += Math.min(cap, f[i]); count++; if (x < lo[y + 1]) lo[y + 1] = x; hi[y + 1] = x; }
        }
        mean[1] = count;
        if (count == 0) return loops;
        mean[0] = sum / count;
        int W = rw + 2, H = rh + 2, step = buffer.step();
        Map<Integer, int[]> nbr = new HashMap<>();
        for (int y = 0; y < H - 1; y++) {
            // Padded row y holds field row y − 1: a cell touches padded rows y and y + 1.
            int from = Math.min(lo[y], lo[y + 1]), to = Math.max(hi[y], hi[y + 1]);
            if (to < 0) continue;
            for (int x = Math.max(0, from); x <= Math.min(W - 2, to + 1); x++) {
            double a = v(f, rw, rh, x, y), b = v(f, rw, rh, x + 1, y), c = v(f, rw, rh, x + 1, y + 1), d = v(f, rw, rh, x, y + 1);
            int idx = (a >= th ? 8 : 0) | (b >= th ? 4 : 0) | (c >= th ? 2 : 0) | (d >= th ? 1 : 0);
            if (idx == 0 || idx == 15) continue;
            int T = (y * W + x) * 2, B = ((y + 1) * W + x) * 2, L = (y * W + x) * 2 + 1, R = (y * W + x + 1) * 2 + 1;
            switch (idx) {
                case 1: case 14: link(nbr, L, B); break;
                case 2: case 13: link(nbr, B, R); break;
                case 3: case 12: link(nbr, L, R); break;
                case 4: case 11: link(nbr, T, R); break;
                case 6: case 9: link(nbr, T, B); break;
                case 7: case 8: link(nbr, L, T); break;
                default: {
                    boolean mid = (a + b + c + d) / 4 >= th;
                    if ((idx == 5) == mid) { link(nbr, L, T); link(nbr, B, R); } else { link(nbr, L, B); link(nbr, T, R); }
                }
            }
            }
        }
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        // Ids in a fixed order, so the same field always traces the same loops.
        List<Integer> ids = new ArrayList<>(nbr.keySet());
        java.util.Collections.sort(ids);
        for (int id0 : ids) {
            if (seen.contains(id0)) continue;
            FloatList xs = new FloatList(), ys = new FloatList();
            int prev = -1, cur = id0;
            while (!seen.contains(cur)) {
                seen.add(cur);
                int cell = cur >> 1, cx = cell % W, cy = cell / W;
                double px, py;
                if ((cur & 1) != 0) { double a = v(f, rw, rh, cx, cy), b = v(f, rw, rh, cx, cy + 1); px = cx; py = cy + (th - a) / (b - a); }
                else { double a = v(f, rw, rh, cx, cy), b = v(f, rw, rh, cx + 1, cy); px = cx + (th - a) / (b - a); py = cy; }
                xs.add((float) ((lx0 + px - 1) * step + .5)); ys.add((float) ((ly0 + py - 1) * step + .5));
                int[] nb = nbr.get(cur);
                int nxt = nb[0] != prev ? nb[0] : nb[1];
                prev = cur; cur = nxt;
                if (cur < 0) break;
            }
            if (xs.n >= 3) loops.add(new float[][]{xs.trim(), ys.trim()});
        }
        return loops;
    }
    /** The padded field's value at {@code x, y}: 0 on the padding. */
    private static double v(float[] f, int rw, int rh, int x, int y) { return x <= 0 || y <= 0 || x > rw || y > rh ? 0 : f[(y - 1) * rw + x - 1]; }
    private static void link(Map<Integer, int[]> nbr, int a, int b) { add(nbr, a, b); add(nbr, b, a); }
    private static void add(Map<Integer, int[]> nbr, int a, int b) {
        int[] e = nbr.get(a);
        if (e == null) nbr.put(a, new int[]{b, -1}); else e[1] = b;
    }
    /** A growing list of floats. */
    private static final class FloatList {
        float[] v = new float[64]; int n;
        void add(float f) { if (n == v.length) v = java.util.Arrays.copyOf(v, n * 2); v[n++] = f; }
        float[] trim() { return java.util.Arrays.copyOf(v, n); }
    }
}
