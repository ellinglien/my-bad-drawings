package io.github.mpdairy.monopaint;

/**
 * A timed page's clock (docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md, "Rules"), immutable and kept in the
 * page's own record ({@link DocumentCodec}'s {@code TIME} section, only for a page whose timer has started): the drawing
 * time spent on it so far, whether its first stroke has started the timer, and whether it has run out.
 * A page that has never been timed has {@link #NONE}, which the file keeps nothing for. A page run out stays so whatever
 * the notebook's Timed Pages is later set to, and is locked only while the notebook's Lock Pages is on ({@link #locks}).
 * A PDF's page is locked as a clock run out with nothing spent ({@code lock(0)}), always. Pure, checked on the desktop
 * ({@code NotebookRulesChecks}).
 */
final class PageClock {
    static final PageClock NONE = new PageClock(0, false, false);
    /** The section's length: the time spent (ms, an int), then a flags byte. A later version may add more after it. */
    static final int BYTES = 5;
    private static final int STARTED = 1, LOCKED = 2;
    /** The most a page's clock keeps, ms: an hour, the longest Timed Pages gives a page. */
    static final long MOST_MS = 3600_000L;

    /** The drawing time spent on the page, ms. */
    final int spent;
    /** Whether the page's first stroke has started its timer. */
    final boolean started;
    /** Whether the time ran out (a timed page's, with time spent), or the page is locked (a PDF's, with none). */
    final boolean locked;

    private PageClock(int spent, boolean started, boolean locked) { this.spent = spent; this.started = started; this.locked = locked; }

    /** The timer started with nothing spent yet. */
    PageClock start() { return started ? this : new PageClock(spent, true, locked); }
    /** The same clock with {@code ms} spent in all (never less than before, at most {@link #MOST_MS}). */
    PageClock spentTo(long ms) { return new PageClock((int) Math.max(spent, Math.min(ms, MOST_MS)), true, locked); }
    /** The time ran out: locked, with all of {@code limit} ms spent. */
    PageClock lock(long limit) { return new PageClock((int) Math.max(spent, Math.min(limit, MOST_MS)), true, true); }

    /**
     * Whether the page takes no marks, no Undo and no Redo: a PDF's page always, a page whose time ran out only when
     * {@code timerLocks} (the notebook's Lock Pages, off by default: the owner, 2026-10-27, "it's just a guide"). A timed
     * page always spent its time (30 s or more) before it ran out, so the two never mix, in old files too.
     */
    boolean locks(boolean timerLocks) { return locked && (spent == 0 || timerLocks); }
    /** Whether a file keeps anything for this clock. */
    boolean kept() { return started || locked; }
    /** The time left of a page given {@code limit} ms, at least 0. */
    long remaining(long limit) { return Math.max(0, limit - spent); }

    /** The section's bytes. */
    byte[] encode() {
        int s = spent;
        return new byte[]{(byte) (s >>> 24), (byte) (s >>> 16), (byte) (s >>> 8), (byte) s, (byte) ((started ? STARTED : 0) | (locked ? LOCKED : 0))};
    }
    /** The clock {@code bytes} keep ({@code length} of them); null when they don't read. */
    static PageClock decode(byte[] bytes, int length) {
        if (length < BYTES || bytes.length < BYTES) return null;
        int s = (bytes[0] & 255) << 24 | (bytes[1] & 255) << 16 | (bytes[2] & 255) << 8 | (bytes[3] & 255);
        int flags = bytes[4] & 255;
        if (s < 0 || s > MOST_MS) return null;
        PageClock clock = new PageClock(s, (flags & (STARTED | LOCKED)) != 0, (flags & LOCKED) != 0);
        return clock.kept() ? clock : NONE;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PageClock)) return false;
        PageClock o = (PageClock) other;
        return spent == o.spent && started == o.started && locked == o.locked;
    }
    @Override public int hashCode() { return spent * 31 + (started ? 1 : 0) + (locked ? 2 : 0); }
    @Override public String toString() { return "PageClock(" + spent + " ms" + (started ? ", started" : "") + (locked ? ", locked" : "") + ")"; }
}
