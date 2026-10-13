package io.github.mpdairy.monopaint;

import java.util.BitSet;

/**
 * A shape or flat fill in watercolor mode: its whole region laid as one watercolor brush
 * dab of fresh water tinted with the shade, which then lifts, blends and runs like a
 * brush's. Unlike a brush, it starts clean each time and keeps nothing it picks up.
 */
final class WatercolorWash {
    /** Rows queued per dab, so a page-sized wash is laid in slices between frames. */
    private static final int BAND = 64;

    private WatercolorWash() { }

    /** Queues a shape's {@code spans}, two intervals per row as {@link ShapeGeometry} rasters them. */
    static void spans(WetWatercolor wet, int[] spans, int width, int height, int gray) {
        BrushInk ink = ink(gray);
        int[] band = new int[width * BAND];
        for (int top = 0; top < height; top += BAND) {
            int rows = Math.min(BAND, height - top), left = width, right = 0;
            java.util.Arrays.fill(band, 0);
            for (int row = 0; row < rows; row++) for (int part = 0; part < 4; part += 2) {
                int at = (top + row) * 4 + part, from = Math.max(0, spans[at]), to = Math.min(width, spans[at + 1]);
                if (from >= to) continue;
                java.util.Arrays.fill(band, row * width + from, row * width + to, 0xFF000000);
                left = Math.min(left, from); right = Math.max(right, to);
            }
            if (left < right) queue(wet, band, width, left, top, right - left, rows, ink);
        }
    }
    /** Queues the pixels set in {@code region}, indexed row by row across {@code width}. */
    static void pixels(WetWatercolor wet, BitSet region, int width, int height, int gray) {
        BrushInk ink = ink(gray);
        int[] band = new int[width * BAND];
        for (int top = 0; top < height; top += BAND) {
            int rows = Math.min(BAND, height - top), left = width, right = 0;
            java.util.Arrays.fill(band, 0);
            for (int pixel = region.nextSetBit(top * width); pixel >= 0 && pixel < (top + rows) * width;
                 pixel = region.nextSetBit(pixel + 1)) {
                int x = pixel % width;
                band[(pixel / width - top) * width + x] = 0xFF000000;
                left = Math.min(left, x); right = Math.max(right, x + 1);
            }
            if (left < right) queue(wet, band, width, left, top, right - left, rows, ink);
        }
    }
    /** A clean brush tinted with the shade; the whole wash shares it, as one brush would. */
    private static BrushInk ink(int gray) { BrushInk ink = new BrushInk(true); ink.tint(gray); return ink; }
    private static void queue(WetWatercolor wet, int[] band, int width, int left, int top, int w, int h, BrushInk ink) {
        // Fully fresh water, as a watercolor brush lays it.
        wet.queueWater(java.util.Arrays.copyOfRange(band, left, band.length), width, left, top, w, h, 1, true, 0, ink);
    }
}
