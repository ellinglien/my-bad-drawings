package io.github.mpdairy.monopaint;

/**
 * The canvas's fast display through a busy or failing panel ({@link DisplayRecovery}, used by {@code DrawingPad.flush}):
 * the owner's "sluggish after hovering the eraser's ring; FAST DISPLAY stopped; opening the project jogs it back". A busy
 * panel was given up after 120 refused presents from every caller together, so the ring's present at each frame beside
 * the 8 ms retry gave up a busy spell a third sooner; and once given up, the page stayed on Android drawing until a
 * dialog closed, the notice waiting for the next autosave message.
 */
public final class DisplayRecoveryChecks {
    public static void main(String[] args) {
        busyWait(); reconnects();
        System.out.println("PASS: display recovery: a busy panel is waited out for 2 s however many callers try (the eraser's ring at every frame "
                + "beside the retry gave up a 0.7 s spell before), a present that goes through starts the wait over, another session too; "
                + "a failed fast display connects again after 1 s, three times in a row at most, and a present that goes through gives it its tries back");
    }

    /**
     * When a busy spell of {@code spellMs} is given up, ms after it began, or -1 if it is waited out: the retry every
     * 8 ms, and with {@code ring} the eraser's ring every 16 ms, each a refused present while the spell lasts.
     */
    static long givenUp(DisplayRecovery r, long spellMs, boolean ring) {
        for (long t = 0; t < spellMs; t++) {
            boolean retry = t % 8 == 0, frame = ring && t % 16 == 5;
            if (retry && r.busy(t)) return t;
            if (frame && r.busy(t)) return t;
        }
        r.shown();
        return -1;
    }
    /** The rule before: 120 refused presents in all. */
    static long givenUpBefore(long spellMs, boolean ring) {
        int tries = 0;
        for (long t = 0; t < spellMs; t++) {
            if (t % 8 == 0 && ++tries >= 120) return t;
            if (ring && t % 16 == 5 && ++tries >= 120) return t;
        }
        return -1;
    }

    static void busyWait() {
        // Before: the retry alone waited about a second; with the ring hovering, a 0.7 s spell was given up.
        check(givenUpBefore(900, false) == -1, "before: the retry alone waits out 0.9 s");
        long before = givenUpBefore(900, true);
        check(before > 0 && before < 700, "before: with the ring, a busy spell was given up at " + before + " ms");
        // Now: the same spells, and one of 1.9 s, are waited out with the ring trying at every frame.
        DisplayRecovery r = new DisplayRecovery();
        check(givenUp(r, 900, true) == -1, "a 0.9 s busy spell is waited out with the ring trying");
        check(givenUp(r, 1900, true) == -1, "a 1.9 s busy spell too");
        // A panel busy for good is given up, after 2 s.
        long t = givenUp(r, 10_000, true);
        check(t >= DisplayRecovery.BUSY_MS && t < DisplayRecovery.BUSY_MS + 20, "a panel busy for good is given up after 2 s: " + t);
        // A present that goes through starts the wait over: spells of 1.5 s with presents between never give up.
        r = new DisplayRecovery();
        for (int k = 0; k < 20; k++) check(givenUp(r, 1500, true) == -1, "spell " + k + " waited out");
        // Many refused presents in a moment (several callers at once) don't give up before the time.
        r = new DisplayRecovery();
        boolean gave = false;
        for (int i = 0; i < 1000; i++) gave |= r.busy(i / 10);
        check(!gave, "a thousand refused presents in 0.1 s are no reason to give up");
        // Another session counts from nothing: a wait almost given up, then disconnected, starts again.
        r = new DisplayRecovery();
        for (int i = 0; i < 500; i++) r.busy(i * 4);
        r.restart();
        check(!r.busy(2100) && !r.busy(2200), "after restart the wait starts over");
    }

    static void reconnects() {
        DisplayRecovery r = new DisplayRecovery();
        for (int k = 1; k <= DisplayRecovery.RECONNECTS; k++) {
            check(r.failed() == DisplayRecovery.RECONNECT_MS, "failure " + k + " connects again after 1 s");
            check(r.failures() == k, "failures counted");
        }
        check(r.failed() < 0, "after three failures in a row it stays on Android drawing (a menu retries)");
        // A present that goes through gives the tries back.
        r.shown();
        check(r.failures() == 0 && r.failed() == DisplayRecovery.RECONNECT_MS, "a present gives the reconnects back");
        // A failure starts the busy wait over too.
        r = new DisplayRecovery();
        for (int i = 0; i < 200; i++) r.busy(i * 20);
        r.failed();
        check(!r.busy(5000), "a failure starts the busy wait over");
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
