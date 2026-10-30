package io.github.mpdairy.monopaint;

/**
 * MonoPaint's grey strip as numbers, shared by its color bar ({@link ShadePicker}) and Inkwash's grey bar ({@link
 * GreyBar}): black at the start to white at the end in the panel's own dots, a solid black and a solid white end zone,
 * and between them the greys evenly spaced in dot density ({@link DotPattern#pickerTone}), so each of the panel's
 * {@link #LEVELS} densities gets an equal share of the strip. Positions are pixels along the strip from its start.
 * Pure, checked on the desktop.
 */
final class ShadeStrip {
    /** How many greys the strip holds: the panel's dot densities, 0 to 64 white dots of 64. */
    static final int LEVELS = 65;
    /** dp: the strip's inset in its view; the gutter under it for the marker; the marker's side, and its gap above the view's foot; the end zones' most. */
    static final int INSET = 5, GUTTER = 10, MARKER = 6, MARKER_GAP = 1, END = 12;

    /** dp: that strip's width; how far a mark reaches past each side of it; the dot right of it at the grey in use (the owner, 2026-10-21). */
    static final int SIDE_WIDTH = 8, SIDE_REACH = 3, SIDE_DOT = 6;
    /**
     * The side strip's width in {@code room} (px or dp, all alike): a swatch's square's width, so the strip stands over the
     * right column of swatches, its left edge on theirs (the owner, 2026-10-22: "align the swatches so they look good with
     * the content above (aligned, following same grid)"), the dot {@code dot} wide right of it, {@code clear} either side.
     */
    static int sideStrip(int room, int dot, int clear) { return room - dot - 2 * clear; }

    private ShadeStrip() { }

    /** The end zones' width on a strip {@code length} long: {@code most} at most, and a sixteenth of it. */
    static int endpoint(int length, int most) { return Math.min(most, length / 16); }
    /** The grey 0–255 at {@code x} on a strip {@code length} long whose end zones are {@code endpoint} wide. */
    static int toneAt(float x, int length, int endpoint) {
        int position = Math.round((x - endpoint) * 255f / Math.max(1, length - 2 * endpoint - 1));
        return DotPattern.pickerTone(position);
    }
    /** Where the marker sits for {@code gray}: the middle of an end zone for black and white, else its density's place. */
    static float markerAt(int gray, int length, int endpoint) {
        int density = DotPattern.whiteCount(gray);
        return density == 0 ? endpoint / 2f : density == 64 ? length - endpoint / 2f
                : endpoint + DotPattern.pickerPosition(gray) * (length - 2 * endpoint - 1) / 255f;
    }
    /** The strip's pixels, {@code length} × {@code height}, row by row, each column its grey in the panel's dots. */
    static int[] pixels(int length, int height, int endpoint) {
        int[] pixels = new int[length * height];
        for (int x = 0; x < length; x++) {
            int tone = toneAt(x, length, endpoint);
            for (int y = 0; y < height; y++) pixels[y * length + x] = DotPattern.pixel(tone, x, y);
        }
        return pixels;
    }
}
