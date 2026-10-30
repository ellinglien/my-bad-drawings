package io.github.mpdairy.monopaint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Inkwash: where the eraser has rubbed a notebook cover's design off (docs/archive/superpowers/specs/
 * 2026-10-19-notebook-options-design.md, "Covers"), a bit a pixel, row by row. Where set, the cover shows its paper made
 * with the design rubbed off ({@code NotebookCovers.paper(…, true)}): white where the pattern was, the tape, the label and
 * the cover picture as they are, so those can't be rubbed. Kept with the cover's page (its {@code WORN} section,
 * {@link DocumentCodec}) and in its picture ({@link PagePicture}); it stays when the design changes, and applies to the
 * new one. Immutable: a stroke makes a new one, so Undo steps and snapshots share them. Pure: checked on the desktop
 * ({@code CoverWearChecks}).
 */
final class CoverWear {
    final int width, height;
    private final byte[] bits;
    private CoverWear(int width, int height, byte[] bits) { this.width = width; this.height = height; this.bits = bits; }

    /** Whether the design is rubbed off at pixel ({@code x}, {@code y}). */
    boolean at(int x, int y) { int i = y * width + x; return (bits[i >> 3] >> (i & 7) & 1) != 0; }
    /** The bytes it holds (for the Undo budget). */
    int bytes() { return bits.length; }

    /** Where a stroke erased, as {@code EraseBothStroke#erased} gives it: non-zero in {@code out} for each erased pixel of the block, row by row. */
    interface Erased { void erased(int left, int top, int w, int h, byte[] out); }
    /** {@code was} (null: none) with the pixels {@code erased} marks in {@code bounds} ({left, top, right, bottom}; null: none) rubbed too; {@code was} itself when none is new. */
    static CoverWear with(CoverWear was, int width, int height, int[] bounds, Erased erased) {
        if (bounds == null) return was;
        int w = bounds[2] - bounds[0];
        byte[] row = new byte[w], bits = null;
        for (int y = bounds[1]; y < bounds[3]; y++) {
            erased.erased(bounds[0], y, w, 1, row);
            for (int x = 0; x < w; x++) {
                if (row[x] == 0) continue;
                int i = y * width + bounds[0] + x;
                if (bits == null) { if (was != null && was.at(bounds[0] + x, y)) continue; bits = was == null ? new byte[(width * height + 7) / 8] : was.bits.clone(); }
                bits[i >> 3] |= 1 << (i & 7);
            }
        }
        return bits == null ? was : new CoverWear(width, height, bits);
    }
    /** This at {@code w × h}: each pixel the nearest one's. */
    CoverWear scaled(int w, int h) {
        if (w == width && h == height) return this;
        byte[] out = new byte[(w * h + 7) / 8];
        for (int y = 0; y < h; y++) {
            int sy = (int) ((2L * y + 1) * height / (2L * h));
            for (int x = 0; x < w; x++)
                if (at((int) ((2L * x + 1) * width / (2L * w)), sy)) { int i = y * w + x; out[i >> 3] |= 1 << (i & 7); }
        }
        return new CoverWear(w, h, out);
    }
    /** {@code paper} with {@code bare}'s pixels wherever {@code wear} (the same size) is rubbed: a new array. */
    static byte[] apply(byte[] paper, byte[] bare, CoverWear wear) {
        byte[] out = paper.clone();
        for (int i = 0; i < out.length; i++) if ((wear.bits[i >> 3] >> (i & 7) & 1) != 0) out[i] = bare[i];
        return out;
    }

    /** The bits, deflated. */
    byte[] encode() {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(bits); deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            while (!deflater.finished()) out.write(chunk, 0, deflater.deflate(chunk));
            return out.toByteArray();
        } finally { deflater.end(); }
    }
    /** What {@link #encode} made for a {@code width × height} cover, from {@code offset} of {@code bytes}; refuses anything else. */
    static CoverWear decode(byte[] bytes, int offset, int width, int height) throws IOException {
        byte[] bits = new byte[(int) (((long) width * height + 7) / 8)];
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(bytes, offset, bytes.length - offset);
            int n = 0;
            while (n < bits.length && !inflater.finished()) {
                int got = inflater.inflate(bits, n, bits.length - n);
                if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                n += got;
            }
            if (n != bits.length || !inflater.finished() || inflater.getRemaining() != 0) throw new IOException("Invalid cover wear");
        } catch (DataFormatException e) { throw new IOException("Invalid cover wear", e); }
        finally { inflater.end(); }
        return new CoverWear(width, height, bits);
    }
}
