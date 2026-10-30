package io.github.mpdairy.monopaint;

/**
 * The page's shade (the owner, 2026-10-19): on a page with no marks, a grey picked on the side bar offers, for
 * {@link #OFFER_MS} beside its swatch, to fill the page with that grey ("Fill page"); the lightest grey offers plain
 * white paper back ("White page"). Filling is one Undo step ({@link ToneDocument#shadePaper}), and the shade is the
 * paper's, not a mark: the page stays unmarked, so another grey can shade it again until something is drawn. Pure Java,
 * checked on the desktop (PageShadeChecks). The strip no longer offers it (the owner, 2026-10-25: it popped up on every
 * grey change); a page's shade is Page Settings' Page Colour.
 */
final class PageShade {
    /** How long an offer stays, ms; the pen going down takes it away sooner. */
    static final long OFFER_MS = 4000;
    /** The button's words, and the note once it filled (docs/TEXT-STYLE.md). */
    static final String FILL = "Fill page", WHITE = "White page", SHADED = "Page shaded", WHITENED = "Page white";

    /** The paper tone grey swatch {@code i} of {@code greys} (as the faces show them) shades a page: white for the lightest. */
    static int tone(int[] greys, int i) {
        for (int grey : greys) if (grey > greys[i]) return greys[i];
        return 255;
    }
    /**
     * What picking grey swatch {@code i} of {@code greys} offers {@code page} at {@code now}, ms; null when the page has
     * marks or is that shade already, and on a notebook cover, whose marble is its paper ({@link ToneDocument#paperPixels}).
     */
    static Offer offer(ToneDocument page, int[] greys, int i, long now) {
        int tone = tone(greys, i);
        return page.paperPixels() != null || page.paperTone() == tone || !page.unmarked() ? null : new Offer(tone, now + OFFER_MS);
    }

    /** One offer: its shade, until when it stays, and whether the pen has taken it away. */
    static final class Offer {
        final int tone;
        private final long until;
        private boolean gone;
        private Offer(int tone, long until) { this.tone = tone; this.until = until; }
        /** The button's words. */
        String label() { return tone == 255 ? WHITE : FILL; }
        /** The note once it filled. */
        String note() { return tone == 255 ? WHITENED : SHADED; }
        /** Whether it is still offered at {@code now}, ms. */
        boolean shown(long now) { return !gone && now < until; }
        /** The pen went down: the offer goes. */
        void penDown() { gone = true; }
        /** Its button tapped at {@code now}: fills {@code page} as one Undo step and goes; false, changing nothing, once gone or when the page has marks since. */
        boolean take(ToneDocument page, long now) {
            boolean live = shown(now);
            gone = true;
            return live && page.unmarked() && page.shadePaper(tone);
        }
    }

    private PageShade() {}
}
