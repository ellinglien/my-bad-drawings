package io.github.mpdairy.monopaint;

import java.nio.charset.StandardCharsets;

/**
 * The covers' noise ({@link CoverDesign}), ported exactly from the approved prototype
 * (docs/archive/brush-lab/prototypes/cover-designs.html, "seeds" and "noise"): a name's seed (FNV-1a, 32 bits, over its UTF-8),
 * SplitMix32 numbers from it, a lattice point's integer hash, gradient noise with 16 directions whose components are
 * exact square roots, octaves of it turned by an exact 3-4-5 rotation, and cells (the distances to the nearest three
 * jittered points). Only +, −, ×, ÷, √, floor, {@code int} multiplies and tables made once, so every Java gives the same
 * numbers, and the prototype's page the same ones too. Pure, checked on the desktop ({@code CoverDesignChecks}).
 */
final class CoverNoise {
    /** FNV-1a, 32 bits, over {@code text}'s UTF-8 bytes. */
    static int seed(String text) {
        int h = 0x811c9dc5;
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) { h ^= b & 255; h *= 0x01000193; }
        return h;
    }

    /** SplitMix32: numbers from 0 (inclusive) to 1, the same from the same seed everywhere. */
    static final class Random {
        private int s;
        Random(int seed) { s = seed; }
        double next() {
            s += 0x9e3779b9;
            int z = s;
            z = (z ^ (z >>> 16)) * 0x85ebca6b; z = (z ^ (z >>> 13)) * 0xc2b2ae35; z ^= z >>> 16;
            return (z & 0xffffffffL) / 4294967296.0;
        }
        /** A seed of 32 bits for a noise: the prototype's {@code (r() * 4294967296) | 0}. */
        int bits() { return (int) (long) (next() * 4294967296.0); }
    }

    /** A lattice point's hash, 32 bits (read unsigned where it matters, with {@code >>>} and masks). */
    static int hash(int ix, int iy, int s) {
        int h = ix * 0x27d4eb2d ^ iy * 0x165667b1 ^ s;
        h = (h ^ (h >>> 15)) * 0x85ebca6b; h = (h ^ (h >>> 13)) * 0xc2b2ae35;
        return h ^ (h >>> 16);
    }

    /** The gradient noise's 16 directions: (1, 0), (1, 1)/√2, (2, 1)/√5 and their turns. */
    private static final double[] GX = new double[16], GY = new double[16];
    static {
        double r2 = Math.sqrt(.5), a = 2 / Math.sqrt(5), b = 1 / Math.sqrt(5);
        double[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {r2, r2}, {-r2, r2}, {r2, -r2}, {-r2, -r2},
                {a, b}, {-a, b}, {a, -b}, {-a, -b}, {b, a}, {-b, a}, {b, -a}, {-b, -a}};
        for (int i = 0; i < 16; i++) { GX[i] = d[i][0]; GY[i] = d[i][1]; }
    }
    /** Gradient noise, about −.7 to .7, a feature about half a unit across (quintic fade). */
    static double perlin(double x, double y, int s) {
        double fx0 = Math.floor(x), fy0 = Math.floor(y);
        int ix = (int) fx0, iy = (int) fy0;
        double fx = x - fx0, fy = y - fy0;
        double ux = fx * fx * fx * (fx * (fx * 6 - 15) + 10), uy = fy * fy * fy * (fy * (fy * 6 - 15) + 10);
        int g = hash(ix, iy, s) & 15; double a = GX[g] * fx + GY[g] * fy;
        g = hash(ix + 1, iy, s) & 15; double b = GX[g] * (fx - 1) + GY[g] * fy;
        g = hash(ix, iy + 1, s) & 15; double c = GX[g] * fx + GY[g] * (fy - 1);
        g = hash(ix + 1, iy + 1, s) & 15; double d = GX[g] * (fx - 1) + GY[g] * (fy - 1);
        double top = a + (b - a) * ux;
        return top + (c + (d - c) * ux - top) * uy;
    }
    /** {@code octaves} of {@link #perlin}, each twice as fine and {@code gain} as strong, each turned by an exact rotation so the grid hides. */
    static double fbm(double x, double y, int octaves, double gain, int s) {
        double sum = 0, amp = 1, norm = 0;
        for (int o = 0; o < octaves; o++) {
            sum += amp * perlin(x, y, s + o * 1013); norm += amp; amp *= gain;
            double nx = .8 * x - .6 * y, ny = .6 * x + .8 * y; x = nx * 2 + 17.3; y = ny * 2 + 5.1;
        }
        return sum / norm;
    }
    /**
     * Cells: into {@code out} the distances to the nearest three feature points (one a cell, jittered .1 to .9), nearest
     * first. {@code reach} 1 looks over 3 × 3 cells (enough for the nearest two), 2 over 5 × 5 (the third too, no seams).
     */
    static void worley(double x, double y, int s, int reach, double[] out) {
        double fx0 = Math.floor(x), fy0 = Math.floor(y);
        int ix = (int) fx0, iy = (int) fy0;
        double f1 = 9, f2 = 9, f3 = 9;
        for (int j = -reach; j <= reach; j++)
            for (int i = -reach; i <= reach; i++) {
                int h = hash(ix + i, iy + j, s);
                double px = ix + i + .1 + .8 * (h & 0xffff) / 65536 - x, py = iy + j + .1 + .8 * (h >>> 16) / 65536 - y;
                double d = px * px + py * py;
                if (d < f1) { f3 = f2; f2 = f1; f1 = d; } else if (d < f2) { f3 = f2; f2 = d; } else if (d < f3) f3 = d;
            }
        out[0] = Math.sqrt(f1); out[1] = Math.sqrt(f2); out[2] = Math.sqrt(f3);
    }
    /** Smoothstep from 0 to 1. */
    static double smooth01(double t) { t = t < 0 ? 0 : t > 1 ? 1 : t; return t * t * (3 - 2 * t); }

    /** Directions round the circle, made once (fdlibm's, as the prototype's page has them): Terrazzo's chips. */
    static final int DIRS = 256;
    static final double[] DCOS = new double[DIRS], DSIN = new double[DIRS];
    static {
        for (int i = 0; i < DIRS; i++) { DCOS[i] = StrictMath.cos(2 * Math.PI * i / DIRS); DSIN[i] = StrictMath.sin(2 * Math.PI * i / DIRS); }
    }

    private CoverNoise() {}
}
