package io.github.mpdairy.monopaint;

/** Layer thumbnails show their own pair only, wash then ink, dimmed when hidden, and are cheap enough to make after strokes. */
public final class LayerThumbnailChecks {
    public static void main(String[] args) {
        ToneDocument doc = InkWashLayers.blank(400, 500);
        InkWashLayers.add(doc, true);
        // Pair 0: a black ink square over the left half; pair 1: a dark wash over the right half.
        fill(doc, InkWashLayers.inkOf(doc, 0), 0, 0, 200, 500, 0);
        fill(doc, InkWashLayers.washOf(doc, 1), 200, 0, 400, 500, 120);
        LayerThumbnail thumbnail = new LayerThumbnail();
        int[] zero = new int[40 * 50], one = new int[40 * 50], hidden = new int[40 * 50];
        thumbnail.render(doc, 0, 40, 50, false, zero);
        thumbnail.render(doc, 1, 40, 50, false, one);
        thumbnail.render(doc, 1, 40, 50, true, hidden);
        check(black(zero, 0, 20) == 20 * 50 && black(zero, 20, 40) == 0, "Pair 0 shows its ink only, where it is");
        int dark = black(one, 20, 40);
        check(black(one, 0, 20) == 0 && dark > 200 && dark < 20 * 50, "Pair 1 shows its wash as dots, only its own");
        check(black(hidden, 20, 40) < dark / 2 && black(hidden, 20, 40) > 0, "A hidden pair is dimmed, still visible");
        // A sampled picture takes the average of what it samples: half-covered blocks come out grey.
        ToneDocument stripes = InkWashLayers.blank(400, 500);
        for (int x = 0; x < 400; x += 10) fill(stripes, InkWashLayers.INK, x, 0, x + 5, 500, 0);
        int[] grey = new int[40 * 50];
        thumbnail.render(stripes, 0, 40, 50, false, grey);
        int dots = black(grey, 0, 40);
        check(dots > 40 * 50 / 5 && dots < 40 * 50 * 4 / 5, "Fine lines average to grey, not lost or solid: " + dots);
        timing(args.length > 0);
        System.out.println("PASS: thumbnails show their own pair, wash then ink as dots, dimmed when hidden, averaged by sampling");
    }
    /**
     * Four pairs at 1920 by 2400 (the Manta page below the bar), each picture 64 by 80 pixels (40 dp tall at
     * 2x): run under -Xint to bound the device's worst case, about 30 ms for all four.
     */
    private static void timing(boolean print) {
        ToneDocument doc = InkWashLayers.blank(1920, 2400);
        for (int pair = 1; pair < 4; pair++) InkWashLayers.add(doc, true);
        for (int layer = 0; layer < 8; layer++) fill(doc, layer, layer * 100, layer * 200, layer * 100 + 800, layer * 200 + 900, layer % 2 == 0 ? 200 : 0);
        LayerThumbnail thumbnail = new LayerThumbnail();
        int[] out = new int[64 * 80];
        long best = Long.MAX_VALUE;
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int pair = 0; pair < 4; pair++) thumbnail.render(doc, pair, 64, 80, pair == 2, out);
            best = Math.min(best, System.nanoTime() - start);
        }
        if (print) System.out.printf("Four thumbnails, 1920x2400 page, 64x80 each: %.1f ms%n", best / 1e6);
    }
    private static void fill(ToneDocument doc, int layer, int x0, int y0, int x1, int y1, int tone) {
        int active = doc.activeLayer();
        doc.selectLayer(layer); doc.begin();
        int w = Math.min(x1, doc.width) - x0, h = Math.min(y1, doc.height) - y0;
        int[] tones = new int[w * h]; java.util.Arrays.fill(tones, tone);
        doc.setTones(x0, y0, w, h, tones); doc.finish(); doc.selectLayer(active);
    }
    private static int black(int[] pixels, int from, int to) {
        int count = 0;
        for (int i = 0; i < pixels.length; i++) if (i % 40 >= from && i % 40 < to && pixels[i] == 0xff000000) count++;
        return count;
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
