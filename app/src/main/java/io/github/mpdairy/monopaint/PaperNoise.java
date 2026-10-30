package io.github.mpdairy.monopaint;

/**
 * The paper's own noise: tileable value noise, fixed from a seed and laid on the page by position, so it never
 * changes on its own. {@link #FINE} and {@link #MID} are the plain paper's grain ({@link PaperGrain#PLAIN}); {@link
 * #low} (soft blooms) and {@link #blob} are what a six-part stroke reads as the prototype's {@code surf.low} and
 * {@code surf.blob} ({@link StrokePaper}). Each 0 to 255. The old watercolor's paper (WatercolorSettle.Paper),
 * kept value for value when that engine went. Pure, checked on the desktop.
 */
final class PaperNoise {
    /** The texture sides, powers of two, so a page position masks into them. */
    static final int SIDE = 512, GRAIN_SIDE = 256;
    /** Soft blooms, on lattices of 64 and 32 pixels. */
    static final byte[] BLOOMS = noise(SIDE, 64, 32, 0x5eed_b100);
    /** The plain paper's fine grain (lattices of 4 and 2 pixels) and its mid noise (8 and 4), and a 16-pixel blob. */
    static final byte[] FINE = noise(GRAIN_SIDE, 4, 2, 0x5eed_9a17);
    static final byte[] MID = noise(GRAIN_SIDE, 8, 4, 0x5eed_0d1d), BLOB = noise(SIDE, 16, 8, 0x5eed_b10b);

    private PaperNoise() {}

    /** Where page row {@code y} starts in {@link #BLOOMS} and {@link #BLOB}. */
    static int row(int y) { return (y & SIDE - 1) * SIDE; }
    /** The noises at page pixel {@code x, y}: blob, about 16 px; low, the blooms. */
    static int blob(int x, int y) { return BLOB[row(y) + (x & SIDE - 1)] & 255; }
    static int low(int x, int y) { return BLOOMS[row(y) + (x & SIDE - 1)] & 255; }

    /**
     * Smooth value noise {@code side} pixels square that tiles, lattice values hashed from {@code seed}: one
     * octave on a {@code coarse} lattice and a half-strength one on a {@code fine} lattice, stretched to 0–255.
     */
    private static byte[] noise(int side, int coarse, int fine, int seed) {
        float[] sum = new float[side * side];
        octave(sum, side, coarse, 1, seed);
        octave(sum, side, fine, .5f, seed * 31 + 7);
        float low = sum[0], high = sum[0];
        for (float v : sum) if (v < low) low = v; else if (v > high) high = v;
        byte[] out = new byte[side * side];
        float scale = 255 / (high - low);
        for (int i = 0; i < out.length; i++) out[i] = (byte)(int)((sum[i] - low) * scale + .5f);
        return out;
    }
    /** Adds {@code weight} times smooth noise on a lattice {@code cell} pixels apart, wrapping so it tiles. */
    private static void octave(float[] sum, int side, int cell, float weight, int seed) {
        int cells = side / cell;
        float[] lattice = new float[cells * cells], blend = new float[cell];
        for (int y = 0; y < cells; y++) for (int x = 0; x < cells; x++) lattice[y * cells + x] = lattice(x, y, seed);
        for (int i = 0; i < cell; i++) blend[i] = smooth((i + .5f) / cell);
        // A row's lattice values, blended down from the lattice rows above and below it, then across.
        float[] row = new float[cells + 1];
        for (int y = 0; y < side; y++) {
            int y0 = y / cell * cells, y1 = (y / cell + 1) % cells * cells;
            float ty = blend[y % cell];
            for (int x = 0; x < cells; x++) row[x] = weight * mix(lattice[y0 + x], lattice[y1 + x], ty);
            row[cells] = row[0];
            for (int x = 0, at = y * side; x < side; x++, at++) sum[at] += mix(row[x / cell], row[x / cell + 1], blend[x % cell]);
        }
    }
    /** A lattice point's value, 0 to 1. */
    private static float lattice(int x, int y, int seed) {
        int hash = x * 0x1f123bb5 + y * 0x05491333 + seed;
        hash ^= hash >>> 16; hash *= 0x45d9f3b; hash ^= hash >>> 16; hash *= 0x45d9f3b; hash ^= hash >>> 16;
        return (hash & 65535) / 65535f;
    }
    private static float smooth(float t) { return t * t * (3 - 2 * t); }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
