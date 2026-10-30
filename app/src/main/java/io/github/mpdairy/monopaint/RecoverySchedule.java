package io.github.mpdairy.monopaint;

/**
 * When the working drawing's recovery save runs while drawing (docs/archive/notes/2026-10-19-audit.md, finding 6). It used to run
 * after every stroke, packing the whole page each time (seconds of a core on the Manta, while the next stroke drew). Now
 * it waits until the page has been still for {@link #QUIET_MS} after its last change, starting again at each change; while
 * changes keep coming, it runs once the oldest unsaved one is {@link #MOST_MS} old. Never while a stroke is down or
 * finishing; while paint is still wet only once {@link #MOST_MS} is up. A crash loses at most the changes of the last
 * {@link #QUIET_MS} after a pause, and of the last {@link #MOST_MS} while drawing without one (plus the save's own
 * time). Moments that must save at once (a pause, a page turned, a notebook left or opened) don't ask this. Pure Java:
 * the host keeps the clock and the timer.
 */
final class RecoverySchedule {
    /** How long the page is still after a change before it is saved. */
    static final long QUIET_MS = 2500;
    /** The most an unsaved change waits while changes keep coming, a stroke down or paint wet aside. */
    static final long MOST_MS = 5000;
    /** How often a save that is due looks again while a stroke is down. */
    static final long RETRY_MS = 250;

    /** When the oldest unsaved change and the newest were made; {@code first} is -1 when nothing waits. */
    private long first = -1, last;

    /** The page changed at {@code now}: how long until the save is due. */
    long changed(long now) {
        if (first < 0) first = now;
        last = now;
        return until(now);
    }
    /**
     * The timer fired at {@code now}: 0 to save now, else how long to wait before asking again (-1: nothing waits).
     * {@code stroking}: a stroke is down or still finishing after lift, or something holds the page; {@code wet}: paint
     * is still settling, drying or floating.
     */
    long ready(long now, boolean stroking, boolean wet) {
        if (first < 0) return -1;
        if (stroking) return RETRY_MS;
        long wait = until(now);
        if (wait > 0) return wait;
        if (wet && now < first + MOST_MS) return first + MOST_MS - now;
        return 0;
    }
    /** A save of the page as it is now was queued (by this schedule or at once). */
    void saved() { first = -1; }
    /** Whether a change waits to be saved. */
    boolean waiting() { return first >= 0; }

    private long until(long now) { return Math.max(0, Math.min(last + QUIET_MS, first + MOST_MS) - now); }
}
