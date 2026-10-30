package io.github.mpdairy.monopaint;

/**
 * A small picture of one layer pair, its wash then its ink on white paper, as the bar shows it.
 * Cheap by sampling: each picture pixel averages {@link #SAMPLES} by {@link #SAMPLES} page pixels
 * spread evenly over the block it stands for, instead of reading every page pixel. Greys come out
 * as the page's own dot screen, so the picture looks like the page does on the panel. Pure, so it is
 * timed and checked on the desktop.
 */
final class LayerThumbnail {
    /** Samples per picture pixel, each way. */
    static final int SAMPLES = 3;
    /** A hidden pair's picture keeps this share of its darkness. */
    static final float DIMMED = .35f;
    /** Where the samples fall, as page pixel indices, for the last page and picture size. */
    private int[] at = new int[0];
    private int[] tones = new int[0];
    private int pageWidth, pageHeight, width, height;

    /**
     * Pair {@code pair} of {@code document}, shown or hidden, into {@code out} ({@code width} by
     * {@code height} ARGB pixels, row by row), lighter when {@code dimmed}. The page fills the picture;
     * keep the page's aspect for an undistorted one.
     */
    void render(ToneDocument document, int pair, int width, int height, boolean dimmed, int[] out) {
        if (document.width != pageWidth || document.height != pageHeight || width != this.width || height != this.height)
            place(document.width, document.height, width, height);
        int count = width * height * SAMPLES * SAMPLES;
        document.layerTones(InkWashLayers.washOf(document, pair), InkWashLayers.inkOf(document, pair), at, count, tones);
        int per = SAMPLES * SAMPLES;
        for (int y = 0, k = 0, o = 0; y < height; y++)
            for (int x = 0; x < width; x++, o++) {
                int sum = 0;
                for (int end = k + per; k < end; k++) sum += tones[k];
                int tone = (sum + per / 2) / per;
                if (dimmed) tone = 255 - Math.round((255 - tone) * DIMMED);
                out[o] = DotPattern.pixel(tone, x, y);
            }
    }
    /**
     * The whole page as it shows (its visible layers on its paper, {@link ToneDocument#compositeTone}) into {@code out}, as
     * {@link #render} does a pair: the drawing page's preview in the page bar ({@code PageTurner}), after a stroke settles.
     */
    void renderPage(ToneDocument document, int width, int height, int[] out) {
        if (document.width != pageWidth || document.height != pageHeight || width != this.width || height != this.height)
            place(document.width, document.height, width, height);
        int per = SAMPLES * SAMPLES;
        for (int y = 0, k = 0, o = 0; y < height; y++)
            for (int x = 0; x < width; x++, o++) {
                int sum = 0;
                for (int end = k + per; k < end; k++) sum += document.compositeTone(at[k] % pageWidth, at[k] / pageWidth);
                out[o] = DotPattern.pixel((sum + per / 2) / per, x, y);
            }
    }
    /** Works out the sample points: each picture pixel's, together, evenly inside its block of the page. */
    private void place(int pageWidth, int pageHeight, int width, int height) {
        this.pageWidth = pageWidth; this.pageHeight = pageHeight; this.width = width; this.height = height;
        int count = width * height * SAMPLES * SAMPLES;
        at = new int[count]; tones = new int[count];
        int[] columns = new int[width * SAMPLES], rows = new int[height * SAMPLES];
        for (int i = 0; i < columns.length; i++) columns[i] = Math.min(pageWidth - 1, (int)((i + .5f) * pageWidth / columns.length));
        for (int i = 0; i < rows.length; i++) rows[i] = Math.min(pageHeight - 1, (int)((i + .5f) * pageHeight / rows.length));
        for (int y = 0, k = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                for (int sy = 0; sy < SAMPLES; sy++)
                    for (int sx = 0; sx < SAMPLES; sx++) at[k++] = rows[y * SAMPLES + sy] * pageWidth + columns[x * SAMPLES + sx];
    }
}
