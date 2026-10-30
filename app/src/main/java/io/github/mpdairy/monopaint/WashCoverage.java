package io.github.mpdairy.monopaint;

/**
 * The pixels one stroke covers, for each the strongest level (1 to 255) that reached it: the eraser's
 * ({@link EraseBothStroke}), which erases each pixel once and the wash beneath at lift. Sparse 64-pixel tiles,
 * held in flat arrays by tile key, so a stroke allocates pixels only where it went and lookups cost no boxing or
 * tree walk. (The old watercolor's wet joining and diffusing lived here too, until every brush became six-part.)
 */
final class WashCoverage {
    interface Area { void at(int left, int top, int right, int bottom); }
    /** Tile size, the same as {@link ToneDocument}'s, so a tile here is a tile of the page. */
    static final int TILE = 64;
    final int width, height;
    private final byte[][] levels;
    private int left, top, right, bottom;

    WashCoverage(int width, int height) {
        this.width = width; this.height = height; left = width; top = height;
        int tiles = ((width + TILE - 1) / TILE) * ((height + TILE - 1) / TILE);
        levels = new byte[tiles][];
    }
    private int key(int x, int y) { return (y / TILE) * ((width + TILE - 1) / TILE) + x / TILE; }
    private static int offset(int x, int y) { return (y % TILE) * TILE + x % TILE; }
    /** Whether the pixel lies on the page. */
    boolean inside(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height; }

    /** Covers a pixel at {@code level} (1–255), keeping the stronger level; true if it was not covered before. */
    boolean cover(int x, int y, int level) {
        if (!inside(x, y) || level < 1 || level > 255) return false;
        int key = key(x, y);
        byte[] tile = levels[key];
        if (tile == null) levels[key] = tile = new byte[TILE * TILE];
        int i = offset(x, y), old = tile[i] & 255;
        if (level > old) tile[i] = (byte)level;
        if (old != 0) return false;
        left = Math.min(left, x); top = Math.min(top, y); right = Math.max(right, x + 1); bottom = Math.max(bottom, y + 1);
        return true;
    }
    /**
     * {@link #cover} for each pixel of row {@code y} from {@code xStart} to {@code xEnd} (exclusive), the
     * i-th at {@code wanted[i]}, a tile at a time: {@code fresh[i]} becomes 1 where the pixel was not
     * covered before and 0 elsewhere. Returns how many were not.
     */
    int cover(int y, int xStart, int xEnd, int[] wanted, byte[] fresh) {
        if (xEnd <= xStart) return 0;
        java.util.Arrays.fill(fresh, 0, xEnd - xStart, (byte)0);
        if (y < 0 || y >= height) return 0;
        int x0 = Math.max(0, xStart), x1 = Math.min(width, xEnd), columns = (width + TILE - 1) / TILE;
        int tileRow = (y / TILE) * columns, offset = (y % TILE) * TILE, count = 0, first = 0, last = 0;
        for (int x = x0; x < x1; ) {
            int end = Math.min(x1, (x / TILE + 1) * TILE), key = tileRow + x / TILE;
            byte[] tile = levels[key];
            for (int t = offset + x % TILE, i = x - xStart; x < end; x++, t++, i++) {
                int level = wanted[i];
                if (level < 1 || level > 255) continue;
                if (tile == null) levels[key] = tile = new byte[TILE * TILE];
                int old = tile[t] & 255;
                if (level > old) tile[t] = (byte)level;
                if (old != 0) continue;
                fresh[i] = 1;
                if (count++ == 0) first = x;
                last = x;
            }
        }
        if (count > 0) { left = Math.min(left, first); top = Math.min(top, y); right = Math.max(right, last + 1); bottom = Math.max(bottom, y + 1); }
        return count;
    }
    /** The pixel's level, or 0 where the stroke never went. */
    int level(int x, int y) {
        if (!inside(x, y)) return 0;
        byte[] tile = levels[key(x, y)];
        return tile == null ? 0 : tile[offset(x, y)] & 255;
    }
    /**
     * Copies the levels of the {@code w} by {@code h} pixels at {@code left, top} into {@code out}, row by
     * row: a tile row at a time, 0 where the stroke never went or off the page. For whole areas, where
     * {@link #level} for each pixel would look its tile up again.
     */
    void levels(int left, int top, int w, int h, byte[] out) {
        java.util.Arrays.fill(out, 0, w * h, (byte)0);
        int x0 = Math.max(0, left), x1 = Math.min(width, left + w), y0 = Math.max(0, top), y1 = Math.min(height, top + h);
        int columns = (width + TILE - 1) / TILE;
        for (int y = y0; y < y1; y++) {
            int tileRow = (y / TILE) * columns, offset = (y % TILE) * TILE;
            for (int x = x0; x < x1; ) {
                int end = Math.min(x1, (x / TILE + 1) * TILE);
                byte[] tile = levels[tileRow + x / TILE];
                if (tile != null) System.arraycopy(tile, offset + x % TILE, out, (y - top) * w + x - left, end - x);
                x = end;
            }
        }
    }
    /** {left, top, right, bottom} of the covered pixels, right and bottom exclusive; null when empty. */
    int[] bounds() { return right > left ? new int[]{left, top, right, bottom} : null; }
    /** Visits each tile with any coverage that meets {@code left..right, top..bottom}, clipped to it and the page. */
    void forEachTile(int left, int top, int right, int bottom, Area area) {
        int columns = (width + TILE - 1) / TILE;
        int x0 = Math.max(0, left), y0 = Math.max(0, top), x1 = Math.min(width, right), y1 = Math.min(height, bottom);
        if (x1 <= x0 || y1 <= y0) return;
        for (int ty = y0 / TILE; ty * TILE < y1; ty++)
            for (int tx = x0 / TILE; tx * TILE < x1; tx++)
                if (levels[ty * columns + tx] != null)
                    area.at(Math.max(x0, tx * TILE), Math.max(y0, ty * TILE), Math.min(x1, (tx + 1) * TILE), Math.min(y1, (ty + 1) * TILE));
    }
}
