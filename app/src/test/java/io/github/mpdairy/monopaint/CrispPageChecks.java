package io.github.mpdairy.monopaint;

/**
 * The page shown big inside a notebook ({@link PagePicture#shown}): made from the page itself at its box's exact size,
 * never scaled after (a page at its own size comes back pixel for pixel), the template's guides in it; timed.
 */
public final class CrispPageChecks {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) {
        int W = 1920, H = 2400, w = 1523, h = 1904;
        ToneDocument page = new ToneDocument(W, H);
        page.begin();
        for (int y = 0; y < H; y++) page.setTone(W / 3, y, 0);
        page.finish();
        ToneDocument.Snapshot plain = page.layerSnapshot();
        PagePicture shown = PagePicture.shown(plain, null, w, h);
        check(shown.width == w && shown.height == h, "Made at the box's exact size: " + shown.width + "x" + shown.height);
        check(PagePicture.shown(plain, null, W * 2, H * 2).width == W, "Never larger than the page");
        // At the page's own size nothing is scaled: each pixel is the page's own grey.
        PagePicture same = PagePicture.shown(plain, null, W, H);
        check(same.tone(W / 3, 7) == 0 && same.tone(W / 3 + 1, 7) == 255 && same.tone(W / 3 - 1, 7) == 255, "At its own size the line stays one pixel, black");
        check(darkPixels(shown) > 0 && darkPixels(shown) <= 2 * h, "Scaled once, the one-pixel line stays a line at most two pixels wide: " + darkPixels(shown));
        // The guides are in it.
        ToneDocument diary = new ToneDocument(W, H);
        diary.placeGuides(NotebookTemplate.DIARY.shared(W, H));
        check(darkPixels(PagePicture.shown(diary.layerSnapshot(), null, w, h)) > w, "The template's guides are in it");
        check(darkPixels(PagePicture.shown(new ToneDocument(W, H).layerSnapshot(), null, w, h)) == 0, "A blank page with no template is white");
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) { long t = System.nanoTime(); PagePicture.shown(diary.layerSnapshot(), null, w, h); best = Math.min(best, System.nanoTime() - t); }
        System.out.println("PASS: the page shown big made at its box's exact size from the page itself, unscaled at its own size, its guides in it (" + W + "x" + H + " to " + w + "x" + h + " in " + best / 1_000_000 + " ms)");
    }
    static int darkPixels(PagePicture p) {
        int n = 0;
        for (int y = 0; y < p.height; y++) for (int x = 0; x < p.width; x++) if (p.tone(x, y) < 250) n++;
        return n;
    }
}
