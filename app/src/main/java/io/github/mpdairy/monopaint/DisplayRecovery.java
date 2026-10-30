package io.github.mpdairy.monopaint;

/**
 * How the canvas's fast display ({@link DrawingPad}'s gray session) rides out a busy or failing panel. A busy present is
 * tried again; the session is given up only once the panel has refused it for {@link #BUSY_MS} and {@link #BUSY_TRIES}
 * tries, however many callers tried meanwhile (the retry, the eraser's ring at every frame, the drying wash). A session
 * given up, or one whose present failed, is connected again after {@link #RECONNECT_MS}, up to {@link #RECONNECTS}
 * times until a present goes through again: before, it stayed on Android drawing (slow on e-ink) until a dialog or
 * the app's focus came back.
 */
final class DisplayRecovery {
    /** A busy panel is given up after this many refused presents, and no sooner than {@link #BUSY_MS} after the first. */
    static final int BUSY_TRIES = 120;
    static final long BUSY_MS = 2000;
    /** After a failure, the fast display connects again this long after, this many times in a row at most. */
    static final long RECONNECT_MS = 1000;
    static final int RECONNECTS = 3;
    private int tries;
    private long busySince;
    private int failures;

    /** A present refused as busy at {@code now} (ms); true once the session should be given up. */
    boolean busy(long now) {
        if (tries++ == 0) busySince = now;
        return tries >= BUSY_TRIES && now - busySince >= BUSY_MS;
    }
    /** A present went through: the panel is well again. */
    void shown() { tries = 0; failures = 0; }
    /** Another session, or the busy wait given up: refused presents count from nothing again. */
    void restart() { tries = 0; }
    /** The session failed: how long until it connects again (ms), or -1 to stay on Android drawing. */
    long failed() {
        tries = 0;
        return ++failures <= RECONNECTS ? RECONNECT_MS : -1;
    }
    /** Failures in a row since a present last went through. */
    int failures() { return failures; }
}
