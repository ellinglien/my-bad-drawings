package io.github.mpdairy.monopaint;

import java.util.Arrays;

/**
 * A page-sized float buffer in {@link #SIDE}-square tiles, each made on its first write, so a stroke's buffers cost
 * only the tiles it touches (the prototype's page-sized Float32Arrays, without 20 MB each). A tile never written
 * reads as {@link #fill}.
 * <p>
 * It holds every page pixel, or with {@link #shift} 1 only the pixels whose coordinates are both even: the
 * <em>lattice</em> a big stroke is worked out on, a quarter of the pixels; {@link #expand} fills a whole-page buffer
 * from it for showing. {@link #get}, {@link #set} and {@link #raise} take page pixels (a pixel off the lattice
 * reads and writes the lattice point at or before it); {@link #tile}, {@link #row} and {@link #setRow} take lattice
 * places, the lattice point {@code (x >> shift, y >> shift)}, a tile holding {@link #SIDE} of them each way, indexed
 * at {@code ((ly & 63) << 6) | (lx & 63)}. Callers looping over pixels visit the lattice ({@link #up},
 * {@link #step}) and fetch a tile once per run inside it. With shift 0 lattice and page are the same. Pure.
 */
final class FloatTiles {
    static final int SHIFT = 6, SIDE = 1 << SHIFT, MASK = SIDE - 1;
    final int width, height;
    final float fill;
    /** 0: every page pixel; 1: the even pixels only. */
    final int shift;
    /** Lattice points a row and a column hold. */
    final int latticeWidth, latticeHeight;
    private final int columns, rows;
    private final float[][] tiles;

    FloatTiles(int width, int height, float fill) { this(width, height, fill, 0); }
    FloatTiles(int width, int height, float fill, int shift) {
        this.width = width; this.height = height; this.fill = fill; this.shift = shift;
        latticeWidth = (width + (1 << shift) - 1) >> shift; latticeHeight = (height + (1 << shift) - 1) >> shift;
        columns = (latticeWidth + MASK) >> SHIFT; rows = (latticeHeight + MASK) >> SHIFT;
        tiles = new float[columns * rows][];
    }

    /** Page pixels from one lattice point to the next. */
    int step() { return 1 << shift; }
    /** The first lattice coordinate at or after page coordinate {@code v} (0 or more). */
    int up(int v) { return (v + (1 << shift) - 1) >> shift << shift; }
    /** A distance of {@code r} page pixels (a blur's radius) in lattice points, at least 1 on a coarser lattice. */
    int radius(int r) { return shift == 0 ? r : Math.max(1, (r + (1 << shift) - 1) >> shift); }
    /** The tile column (or row) holding page coordinate {@code v}. */
    int tileOf(int v) { return v >> (SHIFT + shift); }
    /** The last page coordinate in tile column (or row) {@code t}. */
    int tileEnd(int t) { return ((t + 1) << (SHIFT + shift)) - 1; }
    /** Where in its tile's rows page row {@code y} lies, to or with {@link #at}. */
    int rowAt(int y) { return ((y >> shift) & MASK) << SHIFT; }
    /** Where in its tile's row page column {@code x} lies. */
    int at(int x) { return (x >> shift) & MASK; }

    /** The value at page pixel {@code x, y}: {@link #fill} where nothing was written. */
    float get(int x, int y) {
        x >>= shift; y >>= shift;
        float[] t = tiles[(y >> SHIFT) * columns + (x >> SHIFT)];
        return t == null ? fill : t[((y & MASK) << SHIFT) | (x & MASK)];
    }
    /** Writes {@code v}; writing {@link #fill} where no tile was made leaves it unmade. */
    void set(int x, int y, float v) {
        x >>= shift; y >>= shift;
        float[] t = tile(x >> SHIFT, y >> SHIFT, v != fill);
        if (t != null) t[((y & MASK) << SHIFT) | (x & MASK)] = v;
    }
    /** Keeps the stronger of what is there and {@code v}; true if {@code v} was stronger. */
    boolean raise(int x, int y, float v) {
        x >>= shift; y >>= shift;
        int tx = x >> SHIFT, ty = y >> SHIFT, i = ((y & MASK) << SHIFT) | (x & MASK);
        float[] t = tiles[ty * columns + tx];
        if (v <= (t == null ? fill : t[i])) return false;
        if (t == null) t = tile(tx, ty, true);
        t[i] = v;
        return true;
    }

    /** The tile at tile column {@code tx}, row {@code ty} ({@link #SIDE} square, row by row): made filled when {@code create}, else null if absent. */
    float[] tile(int tx, int ty, boolean create) {
        int k = ty * columns + tx;
        float[] t = tiles[k];
        if (t == null && create) {
            t = new float[SIDE * SIDE];
            if (fill != 0) Arrays.fill(t, fill);
            tiles[k] = t;
        }
        return t;
    }

    /**
     * Lattice row {@code y}'s {@code n} values from lattice column {@code x} into {@code out} from {@code at},
     * {@link #fill} where nothing was written: what {@link #get} gives point by point, a tile's run at a time. The run
     * must lie on the lattice.
     */
    void row(int x, int y, int n, float[] out, int at) {
        int base = (y >> SHIFT) * columns, line = (y & MASK) << SHIFT;
        for (int px = x, end = x + n; px < end; ) {
            int tx = px >> SHIFT, stop = Math.min(end, (tx + 1) << SHIFT);
            float[] t = tiles[base + tx];
            if (t == null) Arrays.fill(out, at + px - x, at + stop - x, fill);
            else System.arraycopy(t, line | (px & MASK), out, at + px - x, stop - px);
            px = stop;
        }
    }
    /** Writes {@code n} values of {@code in} from {@code at} into lattice row {@code y} from lattice column {@code x}, as {@link #set} would one by one. The run must lie on the lattice. */
    void setRow(int x, int y, int n, float[] in, int at) {
        int base = (y >> SHIFT) * columns, line = (y & MASK) << SHIFT;
        for (int px = x, end = x + n; px < end; ) {
            int tx = px >> SHIFT, stop = Math.min(end, (tx + 1) << SHIFT);
            float[] t = tiles[base + tx];
            if (t == null) {
                // Writing only the fill leaves the tile unmade.
                for (int i = at + px - x, last = at + stop - x; i < last; i++) if (in[i] != fill) { t = tile(tx, y >> SHIFT, true); break; }
            }
            if (t != null) System.arraycopy(in, at + px - x, t, line | (px & MASK), stop - px);
            px = stop;
        }
    }

    /** Back to {@link #fill} inside {@code rect} (page pixels, inclusive, clipped to the page); tiles wholly inside are dropped. */
    void clear(int[] rect) {
        int[] q = lattice(rect);
        if (q == null) return;
        int x0 = q[0], y0 = q[1], x1 = q[2], y1 = q[3], width = latticeWidth, height = latticeHeight;
        for (int ty = y0 >> SHIFT; ty <= y1 >> SHIFT; ty++) {
            int top = ty << SHIFT, ya = Math.max(y0, top), yb = Math.min(y1, Math.min(height, top + SIDE) - 1);
            for (int tx = x0 >> SHIFT; tx <= x1 >> SHIFT; tx++) {
                int k = ty * columns + tx;
                float[] t = tiles[k];
                if (t == null) continue;
                int left = tx << SHIFT, xa = Math.max(x0, left), xb = Math.min(x1, Math.min(width, left + SIDE) - 1);
                // Wholly inside: the tile's every lattice point is cleared (a tile past the lattice's edge counts only its lattice part).
                if (xa == left && ya == top && xb == Math.min(width, left + SIDE) - 1 && yb == Math.min(height, top + SIDE) - 1) { tiles[k] = null; continue; }
                for (int y = ya; y <= yb; y++) {
                    int row = (y & MASK) << SHIFT;
                    Arrays.fill(t, row | (xa & MASK), (row | (xb & MASK)) + 1, fill);
                }
            }
        }
    }
    /** The lattice points inside page rectangle {@code rect} (inclusive, clipped to the page), as a lattice rectangle, or null for none. */
    private int[] lattice(int[] rect) {
        if (StrokeMath.empty(rect)) return null;
        int x0 = up(Math.max(0, rect[0])) >> shift, y0 = up(Math.max(0, rect[1])) >> shift;
        int x1 = Math.min(width - 1, rect[2]) >> shift, y1 = Math.min(height - 1, rect[3]) >> shift;
        return x1 < x0 || y1 < y0 ? null : new int[]{x0, y0, x1, y1};
    }
    void clearAll() { Arrays.fill(tiles, null); }

    /** Takes {@code from}'s values inside {@code rect} (page pixels, inclusive, clipped to the page); {@code from} is the same size, lattice and fill. */
    void copy(FloatTiles from, int[] rect) {
        int[] q = lattice(rect);
        if (q == null) return;
        int x0 = q[0], y0 = q[1], x1 = q[2], y1 = q[3];
        for (int ty = y0 >> SHIFT; ty <= y1 >> SHIFT; ty++) {
            int ya = Math.max(y0, ty << SHIFT), yb = Math.min(y1, (ty << SHIFT) | MASK);
            for (int tx = x0 >> SHIFT; tx <= x1 >> SHIFT; tx++) {
                float[] src = from.tiles[ty * columns + tx], dst = tiles[ty * columns + tx];
                if (src == null && dst == null) continue;
                if (dst == null) dst = tile(tx, ty, true);
                int xa = Math.max(x0, tx << SHIFT) & MASK, xb = Math.min(x1, (tx << SHIFT) | MASK) & MASK;
                for (int y = ya; y <= yb; y++) {
                    int row = (y & MASK) << SHIFT;
                    if (src == null) Arrays.fill(dst, row | xa, (row | xb) + 1, fill);
                    else System.arraycopy(src, row | xa, dst, row | xa, xb - xa + 1);
                }
            }
        }
    }

    /**
     * A page pixel's value from the lattice points around it, as {@link #expand} stretches: {@code p} at its lattice
     * column and row, {@code q} the next column, {@code s} the next row, {@code t} both; {@code oddX}, {@code oddY}
     * whether the pixel lies between columns, rows.
     */
    static float between(float p, float q, float s, float t, boolean oddX, boolean oddY) {
        if (!oddX) return oddY ? (p + s) * .5f : p;
        return oddY ? (p + q + s + t) * .25f : (p + q) * .5f;
    }

    /**
     * Fills {@code page} (every pixel, the same size and fill) inside {@code rect} (page pixels, inclusive, clipped)
     * from this lattice: a lattice point's own value, between them the mean of the two or four around, as a bilinear
     * stretch would. Runs that read only tiles never made are left as they are: {@code page} must hold the fill there
     * (it does if this lattice's tiles were never dropped since). {@code row} holds at least twice {@code rect}'s
     * width plus eight. Shift 1 only.
     */
    void expand(FloatTiles page, int[] rect, float[] row) {
        int x0 = Math.max(0, rect[0]), y0 = Math.max(0, rect[1]), x1 = Math.min(width - 1, rect[2]), y1 = Math.min(height - 1, rect[3]);
        if (x1 < x0 || y1 < y0) return;
        // Lattice columns l0..l1 hold every point a pixel of the run reads.
        int l0 = x0 >> 1, l1 = Math.min(latticeWidth - 1, (x1 >> 1) + 1), n = l1 - l0 + 1;
        int a = 0, b = n, out = 2 * n;
        int have = -1, t0 = l0 >> SHIFT, t1 = l1 >> SHIFT;
        boolean[] made = new boolean[t1 - t0 + 1];
        for (int y = y0; y <= y1; y++) {
            int ly = y >> 1, lb = Math.min(latticeHeight - 1, ly + 1);
            if (have != ly) {
                row(l0, ly, n, row, a); row(l0, lb, n, row, b); have = ly;
                for (int t = t0; t <= t1; t++) made[t - t0] = tiles[(ly >> SHIFT) * columns + t] != null || tiles[(lb >> SHIFT) * columns + t] != null;
            }
            boolean midRow = (y & 1) != 0;
            // A tile column's run of pixels at a time: those reading only unmade tiles are skipped.
            for (int xs = x0; xs <= x1; ) {
                int t = (xs >> 1) >> SHIFT, xe = Math.min(x1, ((t + 1) << (SHIFT + 1)) - 1);
                if (!made[t - t0] && (t + 1 > t1 || !made[t + 1 - t0])) { xs = xe + 1; continue; }
                for (int x = xs; x <= xe; x++) {
                    int i = (x >> 1) - l0, j = Math.min(n - 1, i + 1);
                    row[out + x - x0] = between(row[a + i], row[a + j], row[b + i], row[b + j], (x & 1) != 0, midRow);
                }
                page.setRow(xs, y, xe - xs + 1, row, out + xs - x0);
                xs = xe + 1;
            }
        }
    }
}
