package io.github.mpdairy.monopaint;

/**
 * Clear page (the owner, 2026-10-19: "long press on eraser: option to clear page?"): a long press on the eraser's tile
 * shows a Clear page button beside it for {@link #OFFER_MS}, and only
 * on a page with something on it. A tap takes off every mark, on every layer, the masking fluid and the magnets, as one
 * Undo step ({@link ToneDocument#clearPage}), with no question asked: Undo gives it all back. The page stays, with its
 * layers, its shade and its place in the notebook. Pure Java, checked on the desktop (PageClearChecks).
 */
final class PageClear {
    /** How long the button stays, ms (as Fill page's); the pen going down takes it away sooner. */
    static final long OFFER_MS = PageShade.OFFER_MS;
    /** The button's words, and the note once it cleared (docs/TEXT-STYLE.md). */
    static final String CLEAR = "Clear page", CLEARED = "Page cleared";

    /** Whether a long press on the eraser offers to clear {@code page}: there is something on it (paint, fluid or a magnet; the shade is none). */
    static boolean offered(ToneDocument page) { return page != null && !page.unmarked(); }

    private PageClear() {}
}
