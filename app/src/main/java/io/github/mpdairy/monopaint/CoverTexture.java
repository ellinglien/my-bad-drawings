package io.github.mpdairy.monopaint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * A cover design's printed sheet made once, ahead (scripts/cover_textures.sh: the design's own generator,
 * {@link CoverDesign#sheet}), and bundled with the app as a black-and-white PNG (assets/covers/{key}.png): a notebook's
 * cover is a window of it, so a cover takes a few milliseconds to make, not seconds. The window, from the design's seed
 * and the name ({@code key + ":" + name}, as the generators' seeds), is turned one of 8 ways (flipped either way,
 * turned a quarter) where the design has no direction (the marbles, Crackle, Terrazzo, Swirl) and one of 4 (flipped)
 * where it has (Woodgrain's grain runs down, Engraved's lines across), and placed anywhere on the sheet: the five
 * sheets that tile (the marbles and Terrazzo, made to wrap round with no edge) anywhere at all, the others anywhere it
 * fits. The back is the same sheet further along ({@link CoverPattern#BACK_SHIFT}), as a real book's cover paper wraps
 * round. The tape and the label are drawn on it as before ({@link CoverPattern#tape}, {@link NotebookCovers}).
 *
 * <p>The sheet has {@link #PER_WIDTH} texels a cover's width, the Manta's page's width, so its cover is the sheet's
 * texels one for one, black and white; another size is the sheet between its nearest four texels; a small one (a
 * shelf tile, the chooser's covers) the sheet averaged down by 2, 4, 8 or 16 first (made once a sheet), then between
 * the nearest four of those, smooth greys. Kept in memory, a few sheets, while room allows. Pure, checked and timed on
 * the desktop ({@code CoverDesignChecks}).
 */
final class CoverTexture {
    /** Texels a cover's width: the Manta's page's (1785 × 2560 beside its bar), so its covers are the sheet's one for one. */
    static final int PER_WIDTH = 1785;
    /** The name the sheets are made for (their generators' seed with the design's key). */
    static final String NAME = "Inkwash cover sheet";
    /** The averaged-down sheets: by 2, 4, 8 and 16. */
    private static final int LEVELS = 4;

    /** What a design's sheet is: its size, texels; whether it wraps round; whether a window may be turned a quarter; how many covers wide its generator's sheet is. */
    static final class Kind {
        final int width, height, widths;
        final boolean tiles, turns;
        Kind(int width, int height, int widths, boolean tiles, boolean turns) { this.width = width; this.height = height; this.widths = widths; this.tiles = tiles; this.turns = turns; }
        /** The sheet's height over its width, its generator's {@code aspect}. */
        double aspect() { return height / (double) width; }
    }
    /**
     * Each design's sheet: the marbles and Terrazzo 2560 square, wrapping round (a Manta cover's height, so its covers
     * never repeat); Swirl's bath and Crackle 3584 square, two covers wide, not wrapping (a crack cut by a seam would
     * stop dead, and Swirl's bath is its own); Woodgrain and Engraved 3584 × 5120, cheap to keep (long lines compress
     * well), their grain one way.
     */
    static Kind kind(CoverDesign design) {
        switch (design) {
            case SWIRL: case CRACKLE: return new Kind(3584, 3584, 2, false, true);
            case WOOD: case ENGRAVED: return new Kind(3584, 5120, 2, false, false);
            default: return new Kind(2560, 2560, 1, true, true);
        }
    }
    /** A design's PNG among the app's assets. */
    static String asset(CoverDesign design) { return "covers/" + design.key + ".png"; }

    final CoverDesign design;
    final Kind kind;
    final int width, height, rowBytes;
    /** The sheet, a bit a texel (1 paper, 0 ink), each row {@link #rowBytes} long, the first texel the highest bit. */
    final byte[] bits;
    /** The sheet averaged down by 2, 4, 8 and 16 (made when first needed), a tone a texel. */
    private final byte[][] levels = new byte[LEVELS + 1][];

    CoverTexture(CoverDesign design, int width, int height, byte[] bits) {
        this.design = design; this.kind = kind(design); this.width = width; this.height = height; this.rowBytes = (width + 7) / 8; this.bits = bits;
        if (bits.length != rowBytes * height) throw new IllegalArgumentException("A sheet's bits");
    }
    /** Whether the texel at ({@code x}, {@code y}) is paper. */
    boolean paper(int x, int y) { return (bits[y * rowBytes + (x >> 3)] >> (7 - (x & 7)) & 1) != 0; }

    // ---------- the PNG: grey, a bit a pixel, rows unfiltered, as scripts/cover_textures.sh writes them ----------

    private static final byte[] SIGNATURE = {(byte) 137, 'P', 'N', 'G', 13, 10, 26, 10};
    /** A sheet from its PNG (only the kind the script writes: 1-bit grey, not interlaced, every row unfiltered). */
    static CoverTexture decode(CoverDesign design, byte[] png) throws IOException {
        for (int i = 0; i < SIGNATURE.length; i++) if (i >= png.length || png[i] != SIGNATURE[i]) throw new IOException("Not a PNG");
        int at = SIGNATURE.length, width = 0, height = 0;
        ByteArrayOutputStream data = new ByteArrayOutputStream(png.length);
        while (at + 8 <= png.length) {
            int length = int32(png, at);
            String type = new String(png, at + 4, 4, "ISO-8859-1");
            int body = at + 8;
            if (length < 0 || body + length + 4 > png.length) throw new IOException("A broken PNG");
            if (type.equals("IHDR")) {
                width = int32(png, body); height = int32(png, body + 4);
                if (png[body + 8] != 1 || png[body + 9] != 0 || png[body + 12] != 0) throw new IOException("Not a 1-bit grey PNG");
            } else if (type.equals("IDAT")) data.write(png, body, length);
            else if (type.equals("IEND")) break;
            at = body + length + 4;
        }
        int rowBytes = (width + 7) / 8;
        if (width <= 0 || height <= 0 || (long) rowBytes * height > 64 << 20) throw new IOException("A PNG's size");
        byte[] raw = new byte[(rowBytes + 1) * height];
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data.toByteArray());
            int n = 0;
            while (n < raw.length && !inflater.finished()) {
                int got = inflater.inflate(raw, n, raw.length - n);
                if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                n += got;
            }
            if (n != raw.length) throw new IOException("A PNG's pixels are cut short");
        } catch (DataFormatException e) { throw new IOException("A PNG's pixels", e); }
        finally { inflater.end(); }
        byte[] bits = new byte[rowBytes * height];
        for (int y = 0; y < height; y++) {
            if (raw[y * (rowBytes + 1)] != 0) throw new IOException("A filtered PNG row");
            System.arraycopy(raw, y * (rowBytes + 1) + 1, bits, y * rowBytes, rowBytes);
        }
        return new CoverTexture(design, width, height, bits);
    }
    private static int int32(byte[] b, int at) { return (b[at] & 255) << 24 | (b[at + 1] & 255) << 16 | (b[at + 2] & 255) << 8 | b[at + 3] & 255; }

    // ---------- kept ----------

    /** Sheets read lately, by design, kept while their bytes are under {@link #ROOM}; designs whose sheet didn't read. */
    private static final Map<CoverDesign, CoverTexture> KEPT = new LinkedHashMap<>(16, .75f, true);
    private static final Map<CoverDesign, Boolean> MISSING = new EnumMap<>(CoverDesign.class);
    private static final long ROOM = 16L << 20;
    /** {@code design}'s sheet, read from the app's assets the first time; null when it can't be (then the generator makes the cover). */
    static CoverTexture of(CoverDesign design) {
        synchronized (KEPT) {
            CoverTexture kept = KEPT.get(design);
            if (kept != null || MISSING.containsKey(design)) return kept;
            CoverTexture read;
            try { read = decode(design, Assets.bytes(asset(design))); }
            catch (IOException | RuntimeException none) { MISSING.put(design, true); return null; }
            KEPT.put(design, read);
            long bytes = 0;
            for (CoverTexture t : KEPT.values()) bytes += t.bytes();
            for (Iterator<CoverTexture> oldest = KEPT.values().iterator(); bytes > ROOM && KEPT.size() > 1 && oldest.hasNext(); ) {
                CoverTexture t = oldest.next();
                if (t == read) continue;
                bytes -= t.bytes(); oldest.remove();
            }
            return read;
        }
    }
    /** Lets go of the sheets read: on the way back to drawing, and the checks', to read them again (those that didn't read, to try again). */
    static void forget() { synchronized (KEPT) { KEPT.clear(); MISSING.clear(); } }
    private long bytes() {
        long n = bits.length;
        synchronized (levels) { for (byte[] l : levels) if (l != null) n += l.length; }
        return n;
    }

    /** The sheet averaged down by 2 to the {@code level} (1 to {@link #LEVELS}): each texel its square's share of paper, 0 to 255. */
    byte[] level(int level) {
        synchronized (levels) {
            if (levels[level] != null) return levels[level];
            int f = 1 << level, lw = width / f, lh = height / f, n = f * f;
            byte[] out = new byte[lw * lh];
            Cores.each(lh, (y, worker) -> {
                int[] sums = new int[lw];
                for (int row = y * f; row < y * f + f; row++) {
                    int base = row * rowBytes;
                    for (int x = 0; x < lw; x++) {
                        int from = x * f, count = 0;
                        if (f >= 8) for (int b = from >> 3; b < (from + f) >> 3; b++) count += Integer.bitCount(bits[base + b] & 255);
                        else count = Integer.bitCount((bits[base + (from >> 3)] & 255) >> (8 - f - (from & 7)) & ((1 << f) - 1));
                        sums[x] += count;
                    }
                }
                for (int x = 0; x < lw; x++) out[y * lw + x] = (byte) ((sums[x] * 255 + n / 2) / n);
            });
            return levels[level] = out;
        }
    }

    // ---------- a cover's window ----------

    /** A cover's window: turned one of 8 ways ({@code turn}: 1 flipped across, 2 flipped down, 4 turned a quarter), from texel ({@code x}, {@code y}), {@code per} texels a cover's width. */
    static final class Window {
        final int turn, x, y;
        final double per;
        Window(int turn, int x, int y, double per) { this.turn = turn; this.x = x; this.y = y; this.per = per; }
    }
    /** The window for the notebook named {@code name}, on a cover {@code w × h} px, the front's or the {@code back}'s. */
    Window window(String name, int w, int h, boolean back) {
        CoverNoise.Random r = new CoverNoise.Random(CoverNoise.seed(design.key + ":" + name));
        int turn = r.bits() & (kind.turns ? 7 : 3);
        boolean across = (turn & 4) != 0;
        double per = PER_WIDTH, aspect = h / (double) w;
        double rw = across ? per * aspect : per, rh = across ? per : per * aspect;
        // A sheet that doesn't wrap round holds the whole window, at fewer texels a width if it must.
        if (!kind.tiles && (rw > width || rh > height)) { double s = Math.min(width / rw, height / rh); per *= s; rw *= s; rh *= s; }
        int spanX = kind.tiles ? width : (int) Math.floor(width - rw) + 1, spanY = kind.tiles ? height : (int) Math.floor(height - rh) + 1;
        int x = (int) Math.floor(r.next() * spanX), y = (int) Math.floor(r.next() * spanY);
        // The back: the same sheet further along the cover's width.
        if (back) {
            int on = (int) Math.round(CoverPattern.BACK_SHIFT * per);
            if (across) y = (y + on) % spanY; else x = (x + on) % spanX;
        }
        return new Window(turn, x, y, per);
    }

    /** The window's tones on a cover {@code w × h} px, each through {@code levels} (paper 255, ink 0, smooth greys between where it is made smaller or larger, before). */
    byte[] tones(Window window, int w, int h, int[] levels) {
        double k = window.per / w;
        int level = k < 1.5 ? 0 : Math.max(1, Math.min(LEVELS, (int) Math.round(Math.log(k) / Math.log(2))));
        int f = 1 << level, lw = level == 0 ? width : width / f, lh = level == 0 ? height : height / f;
        byte[] source = level == 0 ? null : level(level);
        boolean across = (window.turn & 4) != 0, flipX = (window.turn & 1) != 0, flipY = (window.turn & 2) != 0;
        double ww = window.per, wh = window.per * h / w;
        // Along each of the cover's axes, the texel (of the level) and the next and the share of the next: across the
        // cover, the sheet's columns (its rows when turned), and down it the other.
        Axis along = new Axis(w, k, flipX ? ww : 0, flipX ? -1 : 1, across ? window.y : window.x, f, across ? lh : lw, kind.tiles);
        Axis down = new Axis(h, k, flipY ? wh : 0, flipY ? -1 : 1, across ? window.x : window.y, f, across ? lw : lh, kind.tiles);
        Axis columns = across ? down : along, rows = across ? along : down;
        byte[] out = new byte[w * h];
        boolean exact = along.exact && down.exact;
        int band = 64;
        Cores.each((h + band - 1) / band, (piece, worker) -> {
            int y0 = piece * band, y1 = Math.min(h, y0 + band);
            for (int bx = 0; bx < w; bx += band) {
                int bx1 = Math.min(w, bx + band);
                for (int y = y0; y < y1; y++) {
                    int o = y * w;
                    for (int x = bx; x < bx1; x++) {
                        int c = across ? y : x, r = across ? x : y;
                        int tone;
                        if (level == 0) {
                            int r0 = rows.at[r] * rowBytes, c0 = columns.at[c];
                            int a = bits[r0 + (c0 >> 3)] >> (7 - (c0 & 7)) & 1;
                            if (exact) tone = a * 255;
                            else {
                                int r1 = rows.next[r] * rowBytes, c1 = columns.next[c];
                                int b = bits[r0 + (c1 >> 3)] >> (7 - (c1 & 7)) & 1, cc = bits[r1 + (c0 >> 3)] >> (7 - (c0 & 7)) & 1, d = bits[r1 + (c1 >> 3)] >> (7 - (c1 & 7)) & 1;
                                tone = blend(a * 255, b * 255, cc * 255, d * 255, columns.share[c], rows.share[r]);
                            }
                        } else {
                            int r0 = rows.at[r] * lw, r1 = rows.next[r] * lw, c0 = columns.at[c], c1 = columns.next[c];
                            tone = blend(source[r0 + c0] & 255, source[r0 + c1] & 255, source[r1 + c0] & 255, source[r1 + c1] & 255, columns.share[c], rows.share[r]);
                        }
                        out[o + x] = (byte) levels[tone];
                    }
                }
            }
        });
        return out;
    }
    /** Between four tones by shares of 256: {@code a} to {@code b} across, then to the row below's. */
    private static int blend(int a, int b, int c, int d, int across, int down) {
        int top = a * (256 - across) + b * across, bottom = c * (256 - across) + d * across;
        return (top * (256 - down) + bottom * down + 32768) >> 16;
    }
    /**
     * One of the cover's axes on the sheet (at its {@code f}-times-smaller level, {@code size} texels): for each of its
     * {@code n} pixels, the texel before its centre, the one after (round the sheet if it {@code wraps}, else the
     * last), and the share of the one after, of 256. Its pixel {@code i}'s centre is {@code from + step × (i + .5) × k}
     * texels into the window, which starts {@code start} texels in.
     */
    private static final class Axis {
        final int[] at, next, share;
        boolean exact = true;
        Axis(int n, double k, double from, int step, int start, int f, int size, boolean wraps) {
            at = new int[n]; next = new int[n]; share = new int[n];
            for (int i = 0; i < n; i++) {
                double t = (start + from + step * (i + .5) * k) / f - .5, floor = Math.floor(t);
                int a = (int) floor, s = (int) Math.round((t - floor) * 256);
                if (s >= 256) { a++; s = 0; }
                int b = a + 1;
                if (wraps) { a = Math.floorMod(a, size); b = Math.floorMod(b, size); }
                else { a = Math.max(0, Math.min(size - 1, a)); b = Math.max(0, Math.min(size - 1, b)); }
                at[i] = a; next[i] = b; share[i] = s;
                if (s != 0) exact = false;
            }
        }
    }

}
