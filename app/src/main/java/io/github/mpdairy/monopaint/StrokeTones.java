package io.github.mpdairy.monopaint;

/**
 * A stroke's tones composed off the page ({@link ModularCompose.Page}): what composing it onto the page would write,
 * pixel by pixel, kept in tiles beside it and written in one pass ({@link #applyTo}). A quick stroke's true render
 * composes into it on its own thread while the pen is down ({@link QuickStroke}), so its lift only writes what is
 * composed already.
 *
 * <p>A pixel holds the tone last composed for it (with opaque paint's coverage), or nothing where the stroke now lays
 * nothing: composing it again replaces it, so it holds what composing the whole stroke once would write, and
 * {@link #applyTo} writes each pixel once, with the calls that compose would make (a row with paint through
 * {@link ToneDocument#coverTones}, any other through {@link ToneDocument#setTones}). The page ends as composing the
 * whole stroke once would leave it, a layer's coverage too. The tones the gesture found are read from the page a tile
 * at a time ({@link ToneDocument#baseTones}, safe while its owner keeps them shared) and kept; nothing here is written
 * to the page before {@link #applyTo}, so the page counts as showing what the gesture found.
 *
 * <p>A quick stroke whose tones are final behind its pen ({@link #trails}) writes them there while the pen is down, a tile
 * at a time ({@link #writeTiles}): each tile it changed since last written is written again, first restored to what the
 * gesture found, so the page ends as one write over what the gesture found would leave it.
 *
 * <p>A settling wash's tick and a Float bath's show keep what they write in one too, worked out off the page's thread
 * ({@link SettleWorker}): a bath's through {@link #smearTones} ({@link #smeared}), every pixel with its coverage floor.
 * Workers may keep pixels of the same tile at once (each its own pixels). Pure.
 */
final class StrokeTones implements ModularCompose.Page {
    private static final int TILE = ToneDocument.TILE, SHIFT = 6, MASK = TILE - 1;
    /** A pixel with nothing to write; a tone that laid paint ({@link #coverTones} with a coverage of 0 or more). */
    private static final short NONE = -1, PAINT = 0x100;
    private final ToneDocument document;
    private final int width, height, columns, rows;
    /** Each tile's tones over white as the gesture found them, made on first read; their coverages, made on the first read that wants them. */
    private final byte[][] found, foundCover;
    /** Each tile's pixels to write: {@link #NONE}, or the tone (with {@link #PAINT}); made on first write. */
    private final short[][] tones;
    /** Each tile's coverages of paint (unsigned) where {@link #PAINT} is set, made on the first paint. */
    private final byte[][] coverages;
    /**
     * Erasable: each tile's pixels' own darkness ({@link ModularCompose.Page#setTones}'s {@code owns}, 0 to 255; −1 for
     * none: the write's change), made on the first write that gives one.
     */
    private final short[][] owns;
    /** Whether it keeps a smear's writes ({@link #smearTones}) instead of a compose's. */
    private final boolean smear;
    /** Whether what it composes over is the masking fluid ({@link #onMask}) rather than the gesture's layer. */
    private final boolean mask;
    /**
     * Screen: each tile's print tones as found ({@link ToneDocument#basePrints}), made on first read, and to write
     * ({@link #NONE} or the tone), made on first write ({@link ModularCompose.Page#setPrints}).
     */
    private final byte[][] foundPrint;
    private final short[][] prints;
    /**
     * Trail ({@link #trails}): each tile whose kept tones (or print tones) changed since it was last written to the page
     * ({@link #writeTiles}); null otherwise. Set by the compose's workers, read by the writer under the caller's lock.
     */
    private boolean[] unwritten;

    StrokeTones(ToneDocument document) { this(document, false, false); }
    /** Tones kept as {@link ToneDocument#smearTones} writes them, each with its whole coverage floor (a Float bath's show, a smudge's render), written so. */
    static StrokeTones smeared(ToneDocument document) { return new StrokeTones(document, true, false); }
    /**
     * Tones composed over the masking fluid as it is now ({@link ToneDocument#maskTones}), while a gesture on a layer is
     * in progress: a crayon's wax, composed under the pen and written onto the fluid at its lift ({@link ModularStroke}).
     */
    static StrokeTones onMask(ToneDocument document) { return new StrokeTones(document, false, true); }
    private StrokeTones(ToneDocument document, boolean smear, boolean mask) {
        this.document = document; this.smear = smear; this.mask = mask;
        width = document.width; height = document.height;
        columns = (width + MASK) >> SHIFT; rows = (height + MASK) >> SHIFT;
        found = new byte[columns * rows][]; foundCover = new byte[columns * rows][]; tones = new short[columns * rows][]; coverages = new byte[columns * rows][]; owns = new short[columns * rows][];
        foundPrint = new byte[columns * rows][]; prints = new short[columns * rows][];
    }

    /** From now on keeps which tiles changed since last written ({@link #writeTiles}): a quick stroke's tones written behind its pen. */
    void trails() { unwritten = new boolean[columns * rows]; }
    private void changed(int k) { if (unwritten != null) unwritten[k] = true; }

    @Override public int width() { return width; }
    @Override public int height() { return height; }

    /** Tile {@code k}'s tones as found, read from the page the first time. A band's tiles are its own, so workers never share one. */
    private byte[] found(int k) {
        byte[] t = found[k];
        if (t == null) { t = new byte[TILE * TILE]; if (mask) document.maskTones(k, t, null); else document.baseTones(k, t); found[k] = t; }
        return t;
    }
    /** Tile {@code k}'s coverages as found (and its tones, {@link #found}), read from the page the first time. */
    private byte[] foundCover(int k) {
        byte[] c = foundCover[k];
        if (c == null) { byte[] t = new byte[TILE * TILE]; c = new byte[TILE * TILE]; if (mask) document.maskTones(k, t, c); else document.baseTones(k, t, c); found[k] = t; foundCover[k] = c; }
        return c;
    }
    /** Tile {@code k}'s tones to write, made on first write (under a lock: workers may share a tile, each keeping its own pixels). */
    private short[] tones(int k) {
        short[] t = tones[k];
        if (t != null) return t;
        synchronized (tones) {
            t = tones[k];
            if (t == null) { t = new short[TILE * TILE]; java.util.Arrays.fill(t, NONE); tones[k] = t; }
            return t;
        }
    }
    private short[] owns(int k) {
        short[] o = owns[k];
        if (o != null) return o;
        synchronized (tones) {
            o = owns[k];
            if (o == null) { o = new short[TILE * TILE]; java.util.Arrays.fill(o, NONE); owns[k] = o; }
            return o;
        }
    }
    private short[] prints(int k) {
        short[] p = prints[k];
        if (p != null) return p;
        synchronized (tones) {
            p = prints[k];
            if (p == null) { p = new short[TILE * TILE]; java.util.Arrays.fill(p, NONE); prints[k] = p; }
            return p;
        }
    }
    private byte[] coverages(int k) {
        byte[] c = coverages[k];
        if (c != null) return c;
        synchronized (tones) {
            c = coverages[k];
            if (c == null) coverages[k] = c = new byte[TILE * TILE];
            return c;
        }
    }

    @Override public void strokeBaseTones(int x, int y, int w, int h, int[] out) {
        int x0 = Math.max(0, x), x1 = Math.min(width, x + w), y0 = Math.max(0, y), y1 = Math.min(height, y + h);
        for (int py = y0; py < y1; py++)
            for (int px = x0; px < x1; ) {
                int end = Math.min(x1, ((px >> SHIFT) + 1) << SHIFT);
                byte[] t = found((py >> SHIFT) * columns + (px >> SHIFT));
                for (int i = ((py & MASK) << SHIFT) | (px & MASK), o = (py - y) * w + px - x; px < end; px++, i++, o++) out[o] = t[i] & 255;
            }
    }
    /** The tones and coverages as found (a White or Invert stroke's compose, a smudge's). */
    @Override public void strokeBasePixels(int x, int y, int w, int h, int[] tones, int[] coverages) {
        int x0 = Math.max(0, x), x1 = Math.min(width, x + w), y0 = Math.max(0, y), y1 = Math.min(height, y + h);
        for (int py = y0; py < y1; py++)
            for (int px = x0; px < x1; ) {
                int end = Math.min(x1, ((px >> SHIFT) + 1) << SHIFT), k = (py >> SHIFT) * columns + (px >> SHIFT);
                byte[] c = foundCover(k), t = found[k];
                for (int i = ((py & MASK) << SHIFT) | (px & MASK), o = (py - y) * w + px - x; px < end; px++, i++, o++) { tones[o] = t[i] & 255; coverages[o] = c[i] & 255; }
            }
    }
    @Override public void strokeBasePrints(int x, int y, int w, int[] out) {
        if (y < 0 || y >= height) return;
        for (int px = Math.max(0, x), x1 = Math.min(width, x + w); px < x1; ) {
            int end = Math.min(x1, ((px >> SHIFT) + 1) << SHIFT), k = (y >> SHIFT) * columns + (px >> SHIFT);
            byte[] p = foundPrint[k];
            if (p == null) { p = new byte[TILE * TILE]; document.basePrints(k, p); foundPrint[k] = p; }
            for (int i = ((y & MASK) << SHIFT) | (px & MASK), o = px - x; px < end; px++, i++, o++) out[o] = p[i] & 255;
        }
    }
    @Override public void setPrints(int x, int y, int w, int[] values) {
        if (y < 0 || y >= height) return;
        for (int col = Math.max(0, -x); col < w && x + col < width; col++) {
            int p = values[col];
            if (p < 0) continue;
            if (p > 255) throw new IllegalArgumentException("Invalid tone");
            int px = x + col, k = (y >> SHIFT) * columns + (px >> SHIFT);
            prints(k)[((y & MASK) << SHIFT) | (px & MASK)] = (short) p;
            changed(k);
        }
    }
    /** A Float bath's show or a smudge's compose ({@link #smeared}). */
    @Override public void smearTones(int x, int y, int w, int h, int[] grays, int[] coverages, int[] owns) {
        if (!smear) throw new UnsupportedOperationException("A smear's tones are kept by a smeared StrokeTones");
        keep(x, y, w, h, grays, coverages, owns);
    }
    void smearTones(int x, int y, int w, int h, int[] grays, int[] coverages) { smearTones(x, y, w, h, grays, coverages, null); }
    @Override public boolean touched(int x, int y) { return tones[(y >> SHIFT) * columns + (x >> SHIFT)] != null; }
    /**
     * The tone kept for page pixel {@code x, y}, or −1 where none is. Kept through {@link #setTones}, it is what the page
     * shows there once written ({@link ToneDocument#setTones} gives a pixel exactly the tone asked for, without masking
     * fluid), so work that follows can read the page as it will be.
     */
    int tone(int x, int y) {
        short[] t = tones[(y >> SHIFT) * columns + (x >> SHIFT)];
        if (t == null) return -1;
        short v = t[((y & MASK) << SHIFT) | (x & MASK)];
        return v == NONE ? -1 : v & 255;
    }
    /** The page shows what the gesture found until {@link #applyTo}: a pixel differs only where a tone waits to be written. */
    @Override public boolean differs(int x, int y, int found) {
        short[] t = tones[(y >> SHIFT) * columns + (x >> SHIFT)];
        return t != null && t[((y & MASK) << SHIFT) | (x & MASK)] != NONE;
    }
    /** Back to what the gesture found: nothing to write. */
    @Override public void restoreSpan(int x, int end, int y) {
        x = Math.max(0, x); end = Math.min(width, end);
        if (y < 0 || y >= height) return;
        int r = (y & MASK) << SHIFT;
        for (; x < end; ) {
            int stop = Math.min(end, ((x >> SHIFT) + 1) << SHIFT);
            int k = (y >> SHIFT) * columns + (x >> SHIFT);
            short[] t = tones[k], p = prints[k];
            if (t != null) java.util.Arrays.fill(t, r + (x & MASK), r + ((stop - 1) & MASK) + 1, NONE);
            if (p != null) java.util.Arrays.fill(p, r + (x & MASK), r + ((stop - 1) & MASK) + 1, NONE);
            if (t != null || p != null) changed(k);
            x = stop;
        }
    }
    @Override public void setTones(int x, int y, int w, int h, int[] grays, int[] owns) { composing(); keep(x, y, w, h, grays, null, owns); }
    @Override public void coverTones(int x, int y, int w, int h, int[] grays, int[] coverages, int[] owns) { composing(); keep(x, y, w, h, grays, coverages, owns); }
    void setTones(int x, int y, int w, int h, int[] grays) { setTones(x, y, w, h, grays, null); }
    void coverTones(int x, int y, int w, int h, int[] grays, int[] coverages) { coverTones(x, y, w, h, grays, coverages, null); }
    private void composing() { if (smear) throw new UnsupportedOperationException("A smear's tones keep their coverage floors"); }
    /** Keeps each pixel's tone (a negative one leaves it), with its coverage where it laid paint (0 or more) and its own darkness (Erasable). */
    private void keep(int x, int y, int w, int h, int[] grays, int[] cover, int[] own) {
        for (int row = 0; row < h; row++) {
            int py = y + row;
            if (py < 0 || py >= height) continue;
            for (int col = Math.max(0, -x); col < w && x + col < width; ) {
                int px = x + col, stop = Math.min(Math.min(w, width - x), col + (((px >> SHIFT) + 1) << SHIFT) - px), k = (py >> SHIFT) * columns + (px >> SHIFT);
                short[] t = null, o = own == null ? owns[k] : null;
                byte[] c = null;
                for (int g = row * w + col, i = ((py & MASK) << SHIFT) | (px & MASK); col < stop; col++, g++, i++) {
                    int gray = grays[g];
                    if (gray < 0) continue;
                    if (gray > 255) throw new IllegalArgumentException("Invalid tone");
                    if (t == null) { t = tones(k); changed(k); }
                    if (own != null) { if (o == null) o = owns(k); o[i] = (short) Math.max(-1, Math.min(255, own[g])); }
                    else if (o != null) o[i] = NONE;
                    int paint = cover == null ? -1 : cover[g];
                    if (paint < 0) { t[i] = (short)gray; continue; }
                    if (paint > 255) throw new IllegalArgumentException("Invalid coverage");
                    if (c == null) c = coverages(k);
                    t[i] = (short)(gray | PAINT); c[i] = (byte)paint;
                }
            }
        }
    }
    /** Nothing is shared with the page until {@link #applyTo}; a band's tiles are its own. */
    @Override public void openBands() { }
    @Override public void closeBands() { }

    /**
     * Writes every pixel kept here to the page, in the gesture in progress there on the layer these tones were composed
     * for: each row as composing the stroke there would write it, rows with paint through {@link ToneDocument#coverTones}.
     * Composed over what the gesture has written already (a Drying lift over its preview), pass {@code restore}, the
     * rectangle composed ({left, top, right, bottom}, inclusive): there a pixel left without a tone that shows anything
     * but what the gesture found goes back to it, as the compose on the page would have taken it back; null when the
     * gesture has written nothing yet. In bands of tile rows on several cores ({@link Cores},
     * {@link ToneDocument#openBands}). Then drops what it kept.
     */
    void applyTo(ToneDocument page, int[] restore) {
        int r0 = restore == null ? rows : Math.max(0, restore[1]) >> SHIFT, r1 = restore == null ? -1 : Math.min(height - 1, restore[3]) >> SHIFT;
        int[] tops = new int[rows];
        int bands = 0;
        for (int ty = 0; ty < rows; ty++) {
            boolean any = ty >= r0 && ty <= r1;
            for (int tx = 0; tx < columns && !any; tx++) any = tones[ty * columns + tx] != null || prints[ty * columns + tx] != null;
            if (any) tops[bands++] = ty;
        }
        if (bands == 0) { release(); return; }
        int[][] grays = new int[Cores.WORKERS][], cover = new int[Cores.WORKERS][], under = new int[Cores.WORKERS][], own = new int[Cores.WORKERS][];
        Cores.Piece write = (b, worker) -> {
            if (grays[worker] == null) { grays[worker] = new int[width]; cover[worker] = new int[width]; under[worker] = new int[TILE]; own[worker] = new int[width]; }
            int ty = tops[b];
            for (int y = ty << SHIFT, end = Math.min(height, (ty + 1) << SHIFT); y < end; y++) {
                if (restore != null && y >= restore[1] && y <= restore[3]) restore(page, y, Math.max(0, restore[0]), Math.min(width - 1, restore[2]), under[worker]);
                row(page, y, 0, columns - 1, grays[worker], cover[worker], own[worker]);
                printRow(page, y, 0, columns - 1, grays[worker]);
            }
        };
        if (bands == 1) write.run(0, 0);
        else {
            page.openBands();
            try { Cores.each(bands, write); } finally { page.closeBands(); }
        }
        release();
    }
    /**
     * Page row {@code y}, columns {@code x0..x1}: each pixel without a tone here that shows anything but what the gesture
     * found goes back to it. Only where the gesture has written: elsewhere the page shows what it found.
     */
    private void restore(ToneDocument page, int y, int x0, int x1, int[] under) {
        int ty = y >> SHIFT, r = (y & MASK) << SHIFT;
        for (int xs = x0; xs <= x1; ) {
            int tx = xs >> SHIFT, xe = Math.min(x1, (tx << SHIFT) | MASK);
            if (!page.touched(xs, y)) { xs = xe + 1; continue; }
            short[] t = tones[ty * columns + tx];
            page.strokeBaseTones(xs, y, xe - xs + 1, 1, under);
            int run = -1;
            for (int x = xs; x <= xe; x++) {
                boolean back = (t == null || t[r | (x & MASK)] == NONE) && page.tone(x, y) != under[x - xs];
                if (back) { if (run < 0) run = x; }
                else if (run >= 0) { page.restoreSpan(run, x, y); run = -1; }
            }
            if (run >= 0) page.restoreSpan(run, xe + 1, y);
            xs = xe + 1;
        }
    }
    /** Writes page row {@code y}'s pixels kept here in tile columns {@code tx0..tx1}, with {@code grays}, {@code cover} and {@code own} as a row's scratch. */
    private void row(ToneDocument page, int y, int tx0, int tx1, int[] grays, int[] cover, int[] own) {
        int lo = -1, hi = -1;
        boolean paint = false, owned = false;
        int ty = y >> SHIFT, r = (y & MASK) << SHIFT;
        for (int tx = tx0; tx <= tx1; tx++) {
            short[] t = tones[ty * columns + tx];
            if (t == null) continue;
            byte[] c = coverages[ty * columns + tx];
            short[] o = owns[ty * columns + tx];
            for (int x = tx << SHIFT, end = Math.min(width, (tx + 1) << SHIFT), i = r; x < end; x++, i++) {
                short v = t[i];
                if (v == NONE) continue;
                if (lo < 0) lo = x;
                else for (int k = hi + 1; k < x; k++) { grays[k] = -1; cover[k] = -1; own[k] = -1; }
                hi = x;
                grays[x] = v & 255;
                if ((v & PAINT) != 0) { cover[x] = c[i] & 255; paint = true; }
                else cover[x] = -1;
                own[x] = o == null ? -1 : o[i];
                owned |= o != null;
            }
        }
        if (lo < 0) return;
        int n = hi - lo + 1;
        if (lo > 0) { System.arraycopy(grays, lo, grays, 0, n); System.arraycopy(cover, lo, cover, 0, n); System.arraycopy(own, lo, own, 0, n); }
        int[] owns = owned ? own : null;
        if (smear) page.smearTones(lo, y, n, 1, grays, cover, owns);
        else if (paint) page.coverTones(lo, y, n, 1, grays, cover, owns);
        else page.setTones(lo, y, n, 1, grays, owns);
    }
    /** Writes page row {@code y}'s print tones kept here (Screen) in tile columns {@code tx0..tx1}, with {@code row} as a row's scratch. */
    private void printRow(ToneDocument page, int y, int tx0, int tx1, int[] row) {
        int ty = y >> SHIFT, r = (y & MASK) << SHIFT;
        for (int tx = tx0; tx <= tx1; tx++) {
            short[] p = prints[ty * columns + tx];
            if (p == null) continue;
            int x0 = tx << SHIFT, n = Math.min(width, x0 + TILE) - x0;
            for (int i = 0; i < n; i++) row[i] = p[r + i];
            page.setPrints(x0, y, n, row);
        }
    }
    /**
     * Trail ({@link #trails}): writes to the page, in the gesture in progress, every tile that changed here since it was
     * last written or that {@code previewed} marks (a quick stroke's preview wrote it since; both cleared as written),
     * but those touching {@code away} ({left, top, right, bottom}, inclusive, page px; null: none): each row of such a
     * tile first restored to what the gesture found wherever the gesture wrote it, then written as {@link #applyTo}
     * writes it, so the tile ends as one write over what the gesture found would leave it however often it is written.
     * Keeps what it kept: a tile may change and be written again. At most {@code most} tiles, the first in raster order
     * (the rest stay marked for the next call); in bands of tile rows on several cores ({@link Cores}). Returns how many
     * tiles it wrote.
     */
    int writeTiles(ToneDocument page, boolean[] previewed, int[] away, int most) {
        boolean[] pick = new boolean[columns * rows];
        int[] tops = new int[rows];
        int bands = 0, picked = 0;
        for (int ty = 0; ty < rows; ty++) {
            boolean any = false;
            for (int tx = 0; tx < columns && picked < most; tx++) {
                int k = ty * columns + tx;
                if (!(unwritten != null && unwritten[k] || previewed != null && previewed[k])) continue;
                if (away != null && (tx << SHIFT) <= away[2] && ((tx + 1) << SHIFT) > away[0] && (ty << SHIFT) <= away[3] && ((ty + 1) << SHIFT) > away[1]) continue;
                pick[k] = true; any = true; picked++;
                if (unwritten != null) unwritten[k] = false;
                if (previewed != null) previewed[k] = false;
            }
            if (any) tops[bands++] = ty;
        }
        if (bands == 0) return 0;
        int[][] grays = new int[Cores.WORKERS][], cover = new int[Cores.WORKERS][], own = new int[Cores.WORKERS][];
        Cores.Piece write = (b, worker) -> {
            if (grays[worker] == null) { grays[worker] = new int[width]; cover[worker] = new int[width]; own[worker] = new int[width]; }
            int ty = tops[b];
            for (int tx = 0; tx < columns; ) {
                if (!pick[ty * columns + tx]) { tx++; continue; }
                int tx0 = tx;
                while (tx < columns && pick[ty * columns + tx]) tx++;
                int tx1 = tx - 1;
                for (int y = ty << SHIFT, end = Math.min(height, (ty + 1) << SHIFT); y < end; y++) {
                    for (int c = tx0; c <= tx1; c++) if (page.touched(c << SHIFT, y)) page.restoreSpan(c << SHIFT, Math.min(width, (c + 1) << SHIFT), y);
                    row(page, y, tx0, tx1, grays[worker], cover[worker], own[worker]);
                    printRow(page, y, tx0, tx1, grays[worker]);
                }
            }
        };
        if (bands == 1) write.run(0, 0);
        else {
            page.openBands();
            try { Cores.each(bands, write); } finally { page.closeBands(); }
        }
        return picked;
    }
    /** Drops everything kept. */
    void release() {
        java.util.Arrays.fill(found, null); java.util.Arrays.fill(foundCover, null); java.util.Arrays.fill(tones, null); java.util.Arrays.fill(coverages, null); java.util.Arrays.fill(owns, null);
        java.util.Arrays.fill(foundPrint, null); java.util.Arrays.fill(prints, null);
    }
}
