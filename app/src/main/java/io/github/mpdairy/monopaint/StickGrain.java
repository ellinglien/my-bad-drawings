package io.github.mpdairy.monopaint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Charcoal's own grain (engine.js 82–106): three tileable {@link #SIDE}-pixel value noises, fine (cell 1.6 px),
 * mid (7) and low (36), drawn in that order from the brush's seed. The {@link #KEPT} seeds' grains last used are kept
 * (3 MB each).
 * Pure, checked on the desktop.
 */
final class StickGrain {
    static final int SIDE = 512, KEPT = 2;
    private static final Map<Long, StickGrain> GRAINS = new LinkedHashMap<Long, StickGrain>(4, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, StickGrain> eldest) { return size() > KEPT; }
    };

    private final float[] fine, mid, low;

    private StickGrain(long seed) {
        WildBrush.Random r = new WildBrush.Random(((int)seed ^ 0xc4a2c0a1) & 0xffffffffL);
        fine = tileNoise(SIDE, 1.6, r); mid = tileNoise(SIDE, 7, r); low = tileNoise(SIDE, 36, r);
    }

    /** The grain of {@code seed}, made the first time and kept while it is among the last {@link #KEPT} used. */
    static synchronized StickGrain of(long seed) {
        StickGrain g = GRAINS.get(seed);
        if (g == null) { g = new StickGrain(seed); GRAINS.put(seed, g); }
        return g;
    }

    float fine(int x, int y) { return fine[at(x, y)]; }
    float mid(int x, int y) { return mid[at(x, y)]; }
    float low(int x, int y) { return low[at(x, y)]; }
    private static int at(int x, int y) { return ((y & SIDE - 1) << 9) | (x & SIDE - 1); }

    /** An {@code n}-square value noise on a lattice of about {@code cell} px that wraps at its edges. */
    static float[] tileNoise(int n, double cell, WildBrush.Random r) {
        int g = Math.max(2, (int)Math.round(n / cell));
        float[] lattice = new float[g * g], out = new float[n * n];
        for (int i = 0; i < lattice.length; i++) lattice[i] = (float)r.next();
        // Each column's lattice cells and easing, the same on every row, worked out once; and each lattice row eased
        // across, once (the row below one is the next one's row above).
        int[] x0s = new int[n], x1s = new int[n];
        double[] sxs = new double[n], above = new double[n], below = new double[n];
        for (int x = 0; x < n; x++) {
            double fx = (double)x / n * g; int ix = (int)fx; double tx = fx - ix;
            sxs[x] = tx * tx * (3 - 2 * tx); x0s[x] = ix % g; x1s[x] = (ix + 1) % g;
        }
        int eased = -1;
        for (int y = 0; y < n; y++) {
            double fy = (double)y / n * g; int iy = (int)fy; double ty = fy - iy, sy = ty * ty * (3 - 2 * ty);
            if (iy != eased) {
                if (iy == eased + 1 && eased >= 0) { double[] t = above; above = below; below = t; }
                else ease(lattice, iy % g * g, x0s, x1s, sxs, above);
                ease(lattice, (iy + 1) % g * g, x0s, x1s, sxs, below);
                eased = iy;
            }
            for (int x = 0, o = y * n; x < n; x++, o++) out[o] = (float)StrokeMath.lerp(above[x], below[x], sy);
        }
        return out;
    }
    /** Lattice row {@code at} (its first cell's index) eased across to every column, into {@code into}. */
    private static void ease(float[] lattice, int at, int[] x0s, int[] x1s, double[] sxs, double[] into) {
        for (int x = 0; x < into.length; x++) into[x] = StrokeMath.lerp(lattice[at + x0s[x]], lattice[at + x1s[x]], sxs[x]);
    }
}
