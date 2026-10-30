package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * A page's small picture, for the shelf of notebooks and flipping through one (docs/archive/superpowers/specs/
 * 2026-10-18-notebooks-design.md): the page's visible layers on its paper, each picture pixel the average of the block
 * of page pixels it stands for, at the panel's 16 greys ({@link GrayPalette#VALUES}). Never the masking fluid. Saved
 * inside the notebook's file ({@link BookPictures}) so showing a page never decodes it. Pure, checked and timed on the
 * desktop.
 */
final class PagePicture {
    /** The picture's longer side, px: a 1920 × 2400 page's picture is 480 × 600, a pixel for each 4 × 4 of the page. */
    static final int LONG_SIDE = 600;
    /** The encoding's first byte. */
    private static final int FORMAT = 1;
    /** Each tone's nearest of the panel's greys, as an index into {@link GrayPalette#VALUES}. */
    private static final byte[] LEVEL = new byte[256];
    static {
        for (int tone = 0; tone < 256; tone++) {
            int best = 0;
            for (int i = 1; i < GrayPalette.VALUES.length; i++)
                if (Math.abs(GrayPalette.VALUES[i] - tone) < Math.abs(GrayPalette.VALUES[best] - tone)) best = i;
            LEVEL[tone] = (byte) best;
        }
    }

    /** A picture of a cover's marks alone ({@link #overlay}): the encoding's first byte. */
    private static final int MARKS = 2;

    final int width, height;
    /** One grey a pixel, row by row, an index into {@link GrayPalette#VALUES}. */
    private final byte[] levels;
    /**
     * Inkwash: a notebook cover's picture is of its marks alone, to lay on its paper when shown (the paper is made from
     * the notebook's name and its cover picture, {@code NotebookCovers}, and changes with them): each pixel's coverage,
     * 0 to 15 (all), its grey being the marks' where they cover it. Null on any other page's picture.
     */
    private final byte[] cover;
    /**
     * Inkwash: on a cover's picture, where its design is rubbed off ({@link CoverWear}) at the picture's size, or null.
     * Kept after the encoding's deflated greys, which readers before it stop at.
     */
    private final CoverWear wear;

    private PagePicture(int width, int height, byte[] levels) { this(width, height, levels, null, null); }
    private PagePicture(int width, int height, byte[] levels, byte[] cover, CoverWear wear) { this.width = width; this.height = height; this.levels = levels; this.cover = cover; this.wear = wear; }
    /** Where a cover's design is rubbed off, at the picture's size; null for none. */
    CoverWear wear() { return wear; }
    /** Whether this is a cover's picture of its marks alone, to lay on its paper ({@link #toneOn}). */
    boolean marksAlone() { return cover != null; }

    /**
     * Inkwash: a stack's layers' pictures as one (a layer is a page stacked on the one below, {@link PageStacks}), bottom
     * first: each pixel the darker their greys multiplied, as the layers' marks lie over each other on the paper. A
     * picture of another size is left out. The strip's and the notebook screen's picture of a stack.
     */
    static PagePicture stacked(java.util.List<PagePicture> layers) {
        PagePicture bottom = layers.get(0);
        byte[] levels = new byte[bottom.levels.length];
        for (int i = 0; i < levels.length; i++) {
            int x = i % bottom.width, y = i / bottom.width, tone = 255;
            for (PagePicture p : layers) if (p.width == bottom.width && p.height == bottom.height) tone = tone * p.tone(x, y) / 255;
            levels[i] = LEVEL[tone];
        }
        return new PagePicture(bottom.width, bottom.height, levels);
    }
    /** The picture's size for a {@code pageWidth × pageHeight} page: {@link #LONG_SIDE} on its longer side, never larger than the page. */
    static int[] size(int pageWidth, int pageHeight) {
        double scale = Math.min(1, LONG_SIDE / (double) Math.max(pageWidth, pageHeight));
        return new int[]{Math.max(1, (int) Math.round(pageWidth * scale)), Math.max(1, (int) Math.round(pageHeight * scale))};
    }

    /** The picture of {@code page}: its visible layers, bottom to top, on its paper, each picture pixel its block's average. */
    static PagePicture of(ToneDocument.Snapshot page) { return of(page, null); }
    /** As {@link #of(ToneDocument.Snapshot)}, its fridge magnets drawn by {@code tiles} over it (null: none), each tile averaged into the blocks it covers. */
    static PagePicture of(ToneDocument.Snapshot page, Magnets.Tiles tiles) {
        Magnets magnets = tiles == null || page.magnets == null ? Magnets.NONE : page.magnets;
        if (page.paperPixels != null) return overlay(page.width, page.height, new Composite(page), magnets, tiles, page.wear);
        return of(page.width, page.height, new Composite(page), magnets, tiles);
    }
    /**
     * Inkwash: {@code page} box-averaged straight to {@code w × h} (no larger than the page), its template guides under its
     * marks ({@link Composite#printed}): the page shown big inside a notebook, drawn 1:1 at its box's own pixels.
     */
    static PagePicture shown(ToneDocument.Snapshot page, Magnets.Tiles tiles, int w, int h) {
        Magnets magnets = tiles == null || page.magnets == null ? Magnets.NONE : page.magnets;
        return of(page.width, page.height, Composite.printed(page), magnets, tiles, Math.min(w, page.width), Math.min(h, page.height));
    }
    /** A cover's picture of {@code page}'s marks alone ({@link #marksAlone}), whether or not its paper is laid on it: for a cover read from its file. */
    static PagePicture marks(ToneDocument.Snapshot page, Magnets.Tiles tiles) {
        return overlay(page.width, page.height, new Composite(page), tiles == null || page.magnets == null ? Magnets.NONE : page.magnets, tiles, page.wear);
    }
    /**
     * A cover's picture ({@link #cover}): its marks alone, each picture pixel its block's average coverage and inked tone,
     * magnets taken as covering what they lie on (averaged into their blocks as {@link #of} does); {@code wear} (null:
     * none) at its size.
     */
    private static PagePicture overlay(int pageWidth, int pageHeight, Composite page, Magnets magnets, Magnets.Tiles tiles, CoverWear wear) {
        int[] size = size(pageWidth, pageHeight);
        int w = size[0], h = size[1];
        int[] column = new int[pageWidth], inked = new int[pageWidth], covered = new int[pageWidth];
        for (int x = 0; x < pageWidth; x++) column[x] = Math.min(w - 1, (int) ((long) x * w / pageWidth));
        long[] inks = new long[w], covers = new long[w];
        int[] counts = new int[w];
        double[] ink = new double[w * h], coverage = new double[w * h];
        int[] cells = new int[w * h];
        for (int oy = 0; oy < h; oy++) {
            int y0 = (int) ((long) oy * pageHeight / h), y1 = (int) ((long) (oy + 1) * pageHeight / h);
            java.util.Arrays.fill(inks, 0); java.util.Arrays.fill(covers, 0); java.util.Arrays.fill(counts, 0);
            for (int y = y0; y < y1; y++) {
                page.marks(y, inked, covered);
                for (int x = 0; x < pageWidth; x++) { int c = column[x]; inks[c] += inked[x]; covers[c] += covered[x]; counts[c]++; }
            }
            for (int ox = 0, o = oy * w; ox < w; ox++, o++) {
                cells[o] = counts[ox];
                if (counts[ox] > 0) { ink[o] = inks[ox] / (double) counts[ox]; coverage[o] = covers[ox] / (double) counts[ox]; }
            }
        }
        for (Magnets.Magnet m : magnets.list()) {
            Magnets.Tile t = tiles.tile(m);
            int[] b = Magnets.bounds(m);
            java.util.HashMap<Integer, double[]> touched = new java.util.HashMap<>();
            for (int ty = 0; ty < t.height; ty++) {
                int y = b[1] + ty;
                if (y < 0 || y >= pageHeight) continue;
                int oy = Math.min(h - 1, (int) ((long) y * h / pageHeight));
                for (int tx = 0; tx < t.width; tx++) {
                    int x = b[0] + tx;
                    if (x < 0 || x >= pageWidth) continue;
                    int argb = t.argb[ty * t.width + tx], alpha = argb >>> 24;
                    if (alpha == 0) continue;
                    int o = oy * w + column[x];
                    double a = alpha / 255.0;
                    double[] cell = touched.computeIfAbsent(o, k -> new double[3]);
                    cell[0] += (argb >> 16 & 255) * a + ink[o] * (1 - a); cell[1] += 255 * a + coverage[o] * (1 - a); cell[2]++;
                }
            }
            for (java.util.Map.Entry<Integer, double[]> cell : touched.entrySet()) {
                int o = cell.getKey(); double[] c = cell.getValue();
                double rest = Math.max(0, cells[o] - c[2]), n = Math.max(1, c[2] + rest);
                ink[o] = (c[0] + rest * ink[o]) / n; coverage[o] = (c[1] + rest * coverage[o]) / n;
            }
        }
        byte[] levels = new byte[w * h], cover = new byte[w * h];
        for (int i = 0; i < levels.length; i++) {
            int c = (int) Math.round(coverage[i] * 15 / 255);
            cover[i] = (byte) Math.max(0, Math.min(15, c));
            levels[i] = LEVEL[coverage[i] <= 0 ? 255 : (int) Math.max(0, Math.min(255, Math.round(ink[i] * 255 / coverage[i])))];
        }
        return new PagePicture(w, h, levels, cover, wear == null ? null : wear.scaled(w, h));
    }
    static PagePicture of(int pageWidth, int pageHeight, List<ToneDocument.Layer> layers) { return of(pageWidth, pageHeight, new Composite(pageWidth, layers, 255), Magnets.NONE, null); }
    private static PagePicture of(int pageWidth, int pageHeight, Composite page, Magnets magnets, Magnets.Tiles tiles) {
        int[] size = size(pageWidth, pageHeight);
        return of(pageWidth, pageHeight, page, magnets, tiles, size[0], size[1]);
    }
    private static PagePicture of(int pageWidth, int pageHeight, Composite page, Magnets magnets, Magnets.Tiles tiles, int w, int h) {
        int[] column = new int[pageWidth], row = new int[pageWidth];
        for (int x = 0; x < pageWidth; x++) column[x] = Math.min(w - 1, (int) ((long) x * w / pageWidth));
        int[] sums = new int[w], counts = new int[w];
        int[] tones = new int[w * h], cells = new int[w * h];
        for (int oy = 0; oy < h; oy++) {
            int y0 = (int) ((long) oy * pageHeight / h), y1 = (int) ((long) (oy + 1) * pageHeight / h);
            java.util.Arrays.fill(sums, 0); java.util.Arrays.fill(counts, 0);
            for (int y = y0; y < y1; y++) {
                page.row(y, row);
                for (int x = 0; x < pageWidth; x++) { int c = column[x]; sums[c] += row[x]; counts[c]++; }
            }
            for (int ox = 0, o = oy * w; ox < w; ox++, o++) { cells[o] = counts[ox]; tones[o] = counts[ox] == 0 ? 255 : (sums[ox] + counts[ox] / 2) / counts[ox]; }
        }
        // Magnets: each tile's pixels over the block they fall in, the block's other pixels as they were.
        for (Magnets.Magnet m : magnets.list()) {
            Magnets.Tile t = tiles.tile(m);
            int[] b = Magnets.bounds(m);
            java.util.HashMap<Integer, long[]> touched = new java.util.HashMap<>();
            for (int ty = 0; ty < t.height; ty++) {
                int y = b[1] + ty;
                if (y < 0 || y >= pageHeight) continue;
                int oy = Math.min(h - 1, (int) ((long) y * h / pageHeight));
                for (int tx = 0; tx < t.width; tx++) {
                    int x = b[0] + tx;
                    if (x < 0 || x >= pageWidth) continue;
                    int argb = t.argb[ty * t.width + tx], alpha = argb >>> 24;
                    if (alpha == 0) continue;
                    int o = oy * w + column[x];
                    long[] cell = touched.computeIfAbsent(o, k -> new long[2]);
                    cell[0] += ((argb >> 16 & 255) * alpha + (long) tones[o] * (255 - alpha) + 127) / 255; cell[1]++;
                }
            }
            for (java.util.Map.Entry<Integer, long[]> cell : touched.entrySet()) {
                int o = cell.getKey(); long[] c = cell.getValue();
                long rest = Math.max(0, cells[o] - c[1]);
                tones[o] = (int) ((c[0] + rest * tones[o] + (c[1] + rest) / 2) / Math.max(1, c[1] + rest));
            }
        }
        byte[] levels = new byte[w * h];
        for (int i = 0; i < levels.length; i++) levels[i] = LEVEL[Math.max(0, Math.min(255, tones[i]))];
        return new PagePicture(w, h, levels);
    }

    /** A page's visible layers composed on its paper, a row at a time: {@link ToneDocument#compositeTone}'s arithmetic, quicker. */
    static final class Composite {
        private final int width, visible, paper;
        private final byte[][] tones, alpha;
        private final int[] opacity;
        /** A cover's paper pixels ({@link ToneDocument#paperPixels}), or null: {@link #paper} everywhere. */
        private final byte[] ground;
        /** Inkwash: Real Mirror's reflection ({@link Mirrors#view}), shown as the page shows it, or null; the page's height, and a row's sources. */
        private final Mirrors.View view;
        private final int height;
        private final int[] source;
        /** {@code page}'s, on its paper, as it shows (Real Mirror's reflection too). */
        Composite(ToneDocument.Snapshot page) { this(page.width, page.height, page.layers, page.paper, page.paperPixels, page.mirrors == null ? null : page.mirrors.view()); }
        /** As above with the page's template guides under its marks ({@link ToneDocument.Snapshot#shownPaper}): what the copies print. */
        static Composite printed(ToneDocument.Snapshot page) { return printed(page, 0, Integer.MAX_VALUE); }
        /**
         * As {@link #printed(ToneDocument.Snapshot)}, of the page's composite places {@code from} up to {@code to} alone
         * ({@link ToneDocument#stacked}; none: its paper and guides): a layer of a page's Layers copy ({@link PageExport}).
         */
        static Composite printed(ToneDocument.Snapshot page, int from, int to) {
            return new Composite(page.width, page.height, page.layers, page.paper, page.shownPaper(), page.mirrors == null ? null : page.mirrors.view(), from, to);
        }
        /** {@code layers}, a page {@code width} wide, on {@code paper} (255: white). */
        Composite(int width, List<ToneDocument.Layer> layers, int paper) { this(width, 0, layers, paper, null, null); }
        private Composite(int width, int height, List<ToneDocument.Layer> layers, int paper, byte[] ground, Mirrors.View view) { this(width, height, layers, paper, ground, view, 0, Integer.MAX_VALUE); }
        private Composite(int width, int height, List<ToneDocument.Layer> layers, int paper, byte[] ground, Mirrors.View view, int from, int to) {
            this.width = width; this.height = height; this.paper = paper; this.ground = ground; this.view = view;
            source = view == null ? null : new int[width];
            int places = ToneDocument.stackSize(layers.size());
            tones = new byte[places][]; alpha = new byte[places][]; opacity = new int[places];
            int n = 0;
            // Inkwash: each layer's base and kind planes, in the page's order.
            for (int k = Math.max(0, from); k < Math.min(to, places); k++) {
                ToneDocument.Layer entry = ToneDocument.stacked(layers, k);
                if (entry == null) continue;
                ToneDocument.Layer layer = layers.get(ToneDocument.stackOwner(k));
                if (layer.visible) { tones[n] = entry.tones; alpha[n] = entry.alpha; opacity[n++] = ToneDocument.stackOpacity(layer, entry); }
            }
            visible = n;
        }
        /** Row {@code y}'s tones (0 black, 255 white) into {@code out}. */
        void row(int y, int[] out) {
            if (view != null) view.row(y, 0, width, width, height, source);
            for (int x = 0, at = y * width; x < width; x++, at++) {
                int i = view == null ? at : source[x];
                if (i < 0) { out[x] = paper; continue; }
                int result = ground == null ? paper : ground[i] & 255;
                for (int n = 0; n < visible; n++) {
                    int a = alpha[n][i] & 255;
                    if (a == 0) continue;
                    if (opacity[n] != 100) a = (a * opacity[n] + 50) / 100;
                    result = ((tones[n][i] & 255) * a + result * (255 - a) + 127) / 255;
                }
                out[x] = result;
            }
        }
        /**
         * Row {@code y}'s marks alone, over nothing: each pixel's coverage ({@code cover}, 0 to 255) and its tone times its
         * coverage ({@code inked}, 0 to 255), so that on any paper p it shows {@code inked + p × (255 − cover) / 255}.
         */
        void marks(int y, int[] inked, int[] cover) {
            if (view != null) view.row(y, 0, width, width, height, source);
            for (int x = 0, at = y * width; x < width; x++, at++) {
                int i = view == null ? at : source[x];
                int c = 0, k = 0;
                if (i < 0) { inked[x] = 0; cover[x] = 0; continue; }
                for (int n = 0; n < visible; n++) {
                    int a = alpha[n][i] & 255;
                    if (a == 0) continue;
                    if (opacity[n] != 100) a = (a * opacity[n] + 50) / 100;
                    k = ((tones[n][i] & 255) * a + k * (255 - a) + 127) / 255;
                    c = a + (c * (255 - a) + 127) / 255;
                }
                inked[x] = k; cover[x] = c;
            }
        }
    }

    /** The grey at picture pixel ({@code x}, {@code y}), a page tone (0 black, 255 paper); a cover's marks on white. */
    int tone(int x, int y) { return toneOn(x, y, 255); }
    /** The grey at picture pixel ({@code x}, {@code y}) on paper of tone {@code paper}: a cover's marks over it; any other picture as it is. */
    int toneOn(int x, int y, int paper) {
        int i = y * width + x, tone = GrayPalette.VALUES[levels[i]];
        if (cover == null) return tone;
        int c = cover[i];
        return (tone * c + paper * (15 - c) + 7) / 15;
    }
    /** Whether every pixel is white paper (a blank page's picture; marks too faint to show leave it white too); a cover's: no marks. */
    boolean white() {
        if (cover != null) { if (wear != null) return false; for (byte c : cover) if (c != 0) return false; return true; }
        for (byte level : levels) if (level != LEVEL[255]) return false;
        return true;
    }

    /**
     * The picture box-averaged to {@code w × h} (no larger than it), in the page's own dots ({@link DotPattern#pixel}), ARGB
     * row by row: a page's preview in the drawing screen's page bar ({@code PageTurner}).
     */
    int[] dots(int w, int h) {
        int[] out = new int[w * h];
        for (int oy = 0; oy < h; oy++) {
            int y0 = oy * height / h, y1 = Math.max(y0 + 1, (oy + 1) * height / h);
            for (int ox = 0; ox < w; ox++) {
                int x0 = ox * width / w, x1 = Math.max(x0 + 1, (ox + 1) * width / w), sum = 0;
                for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) sum += tone(x, y);
                int n = (y1 - y0) * (x1 - x0);
                out[oy * w + ox] = DotPattern.pixel((sum + n / 2) / n, ox, oy);
            }
        }
        return out;
    }

    /** Each pixel's darkness (255 less its grey as shown, {@link DotPattern#exportGray}), row by row: an alpha mask to draw ink with. */
    byte[] darkness() {
        byte[] dark = new byte[16];
        for (int i = 0; i < 16; i++) dark[i] = (byte) (255 - DotPattern.exportGray(GrayPalette.VALUES[i]));
        byte[] out = new byte[levels.length];
        if (cover != null) {
            for (int i = 0; i < levels.length; i++) out[i] = (byte) (255 - DotPattern.exportGray(tone(i % width, i / width)));
            return out;
        }
        for (int i = 0; i < levels.length; i++) out[i] = dark[levels[i]];
        return out;
    }

    /** The bytes kept in the file: the format, the size, then the greys two to a byte, deflated. */
    byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(cover == null ? FORMAT : MARKS); out.writeShort(width); out.writeShort(height);
            Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
            try {
                DeflaterOutputStream packed = new DeflaterOutputStream(bytes, deflater, 8192);
                byte[] row = new byte[(width + 1) / 2];
                // A cover's: its grey and its coverage, a byte a pixel.
                if (cover != null) {
                    byte[] both = new byte[width];
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) both[x] = (byte) (cover[y * width + x] << 4 | levels[y * width + x]);
                        packed.write(both);
                    }
                    packed.finish();
                    if (wear != null) bytes.write(wear.encode());
                    return bytes.toByteArray();
                }
                for (int y = 0; y < height; y++) {
                    java.util.Arrays.fill(row, (byte) 0);
                    for (int x = 0; x < width; x++) row[x >> 1] |= (x & 1) == 0 ? levels[y * width + x] << 4 : levels[y * width + x];
                    packed.write(row);
                }
                packed.finish();
            } finally { deflater.end(); }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    /** A picture {@link #encode} made; refuses anything else, never reading past its size. */
    static PagePicture decode(byte[] encoded) throws IOException {
        ByteArrayInputStream source = new ByteArrayInputStream(encoded);
        DataInputStream in = new DataInputStream(source);
        int format = in.readUnsignedByte();
        if (format != FORMAT && format != MARKS) throw new IOException("Unknown page picture");
        int w = in.readUnsignedShort(), h = in.readUnsignedShort();
        if (w < 1 || h < 1 || w > LONG_SIDE || h > LONG_SIDE) throw new IOException("Invalid page picture size");
        byte[] levels = new byte[w * h], row = new byte[(w + 1) / 2];
        Inflater inflater = new Inflater();
        try {
            DataInputStream packed = new DataInputStream(new InflaterInputStream(in, inflater));
            if (format == MARKS) {
                byte[] cover = new byte[w * h];
                packed.readFully(cover);
                if (packed.read() != -1) throw new IOException("Extra page picture data");
                for (int i = 0; i < cover.length; i++) { levels[i] = (byte) (cover[i] & 15); cover[i] = (byte) ((cover[i] >> 4) & 15); }
                // What follows the greys' deflated stream: the design rubbed off, if any.
                int after = encoded.length - source.available() - inflater.getRemaining();
                return new PagePicture(w, h, levels, cover, after < encoded.length ? CoverWear.decode(encoded, after, w, h) : null);
            }
            for (int y = 0; y < h; y++) {
                packed.readFully(row);
                for (int x = 0; x < w; x++) levels[y * w + x] = (byte) ((x & 1) == 0 ? (row[x >> 1] >> 4) & 15 : row[x >> 1] & 15);
            }
            if (packed.read() != -1) throw new IOException("Extra page picture data");
        } catch (java.io.EOFException short_) { throw new IOException("Short page picture", short_); }
        finally { inflater.end(); }
        return new PagePicture(w, h, levels);
    }

    /**
     * The picture shown {@code w × h} px, as ARGB greys for the screen ({@link DotPattern#exportGray}, as a PNG copy
     * shows the page): each shown pixel its footprint's average when smaller, blended between the nearest four when larger.
     */
    int[] pixels(int w, int h) {
        int[] out = new int[w * h];
        boolean smaller = w <= width && h <= height;
        for (int y = 0, o = 0; y < h; y++)
            for (int x = 0; x < w; x++, o++) {
                int tone;
                if (smaller) {
                    int x0 = (int) ((long) x * width / w), x1 = Math.max(x0 + 1, (int) ((long) (x + 1) * width / w));
                    int y0 = (int) ((long) y * height / h), y1 = Math.max(y0 + 1, (int) ((long) (y + 1) * height / h));
                    int sum = 0, count = 0;
                    for (int sy = y0; sy < y1; sy++) for (int sx = x0; sx < x1; sx++) { sum += tone(sx, sy); count++; }
                    tone = (sum + count / 2) / count;
                } else {
                    float fx = Math.max(0, (x + .5f) * width / w - .5f), fy = Math.max(0, (y + .5f) * height / h - .5f);
                    int ax = Math.min(width - 1, (int) fx), ay = Math.min(height - 1, (int) fy);
                    int bx = Math.min(width - 1, ax + 1), by = Math.min(height - 1, ay + 1);
                    float tx = fx - ax, ty = fy - ay;
                    float top = tone(ax, ay) * (1 - tx) + tone(bx, ay) * tx, bottom = tone(ax, by) * (1 - tx) + tone(bx, by) * tx;
                    tone = Math.round(top * (1 - ty) + bottom * ty);
                }
                int g = DotPattern.exportGray(tone);
                out[o] = 0xff000000 | g << 16 | g << 8 | g;
            }
        return out;
    }
}
