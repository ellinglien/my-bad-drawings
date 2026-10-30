package io.github.mpdairy.monopaint;

/** Paging for the lab's library (e-ink: pages, not scrolling). Pure, checked on the desktop. */
final class LabPages {
    private LabPages() { }
    /** How many pages {@code count} cards take at {@code perPage} a page; at least one. */
    static int pages(int count, int perPage) { int per = Math.max(1, perPage); return Math.max(1, (count + per - 1) / per); }
    /** The page holding card {@code index}. */
    static int of(int index, int perPage) { return Math.max(0, index) / Math.max(1, perPage); }
    /** {@code page} kept inside the pages there are. */
    static int clamp(int page, int count, int perPage) { return Math.max(0, Math.min(page, pages(count, perPage) - 1)); }
}
