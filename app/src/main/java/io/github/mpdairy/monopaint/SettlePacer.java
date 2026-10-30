package io.github.mpdairy.monopaint;

import java.util.function.LongSupplier;

/**
 * When settling work may run and show, so the pen always wins (MonoPaint's {@code WetWorkBudget} and
 * {@code WET_SHOW_MS}, simplified): a Drying wash's ticks after lift ({@link WatercolorSession#tick}) and a Fast-wet
 * re-settle's box under the pen ({@link ModularStroke#present}). Input that lags (an event handled late, or slowly) or
 * settling work over its budget backs settling off for {@link #BACK_OFF_MS}; and what settling changed is shown at
 * most once every {@link #SHOW_MS}, its areas meanwhile gathered into one. Pacing moves only when work happens and is
 * shown, never what it lays: a tick steps by its patch's age, not the clock, and a box shown later is composed from the
 * same buffers. Pure; a host makes one per session (null: no pacing, as on the desktop and for samples).
 */
final class SettlePacer {
    /** How long settling backs off after lagging input or slow work, and the least time between two of its shows, ms. */
    static final long BACK_OFF_MS = 100, SHOW_MS = 120;
    /** Input lags when an event is handled this long after it happened, or takes this long to handle, ms. */
    static final long LATE_EVENT_MS = 24, SLOW_INPUT_MS = 8;
    /** A Fast-wet re-settle (half a 60 Hz frame) or a Drying tick with its show (under half the tick) over this is slow, ms. */
    static final long RESETTLE_BUDGET_MS = 8, TICK_BUDGET_MS = 40;
    /** A Drying wash's ticks: {@link DryingWater#TPS} a second. */
    static final long TICK_MS = 1000 / DryingWater.TPS;
    private static final long MS = 1_000_000;

    private final LongSupplier nanos;
    /** Settling may run again from this time, ns. */
    private long quietUntil;
    private boolean quiet;
    /** When settling last showed, ns. */
    private long shownAt;
    private boolean shownYet;
    /** How many times settling has shown, and backed off (for the checks). */
    int shows, backOffs;

    SettlePacer() { this(System::nanoTime); }
    /** With {@code nanos} as the clock, ns (the checks give one of their own). */
    SettlePacer(LongSupplier nanos) { this.nanos = nanos; }

    long now() { return nanos.getAsLong(); }

    /** A pen event handled: {@code elapsedNanos} to handle it, {@code eventAgeMs} after it happened. */
    void input(long elapsedNanos, long eventAgeMs) {
        if (elapsedNanos >= SLOW_INPUT_MS * MS || eventAgeMs >= LATE_EVENT_MS) backOff();
    }
    /** Settling work done: {@code elapsedNanos} against a budget of {@code budgetMs}. */
    void worked(long elapsedNanos, long budgetMs) { worked(elapsedNanos, budgetMs, 0); }
    /**
     * As {@link #worked(long, long)}, its back-off counted from {@code agoNanos} before now: work that ran meanwhile
     * elsewhere ({@link SettleWorker}, the work ahead done with a tick) filled that much of it.
     */
    void worked(long elapsedNanos, long budgetMs, long agoNanos) {
        if (elapsedNanos > budgetMs * MS) backOff(Math.max(0, agoNanos));
    }
    private void backOff() { backOff(0); }
    private void backOff(long agoNanos) { quiet = true; quietUntil = now() - agoNanos + BACK_OFF_MS * MS; backOffs++; }

    /** How long settling should still wait, ms; 0 to run now. */
    long waitMs() {
        if (!quiet) return 0;
        long left = quietUntil - now();
        if (left <= 0) { quiet = false; return 0; }
        return (left + MS - 1) / MS;
    }
    /** Whether what settling changed may be shown now: not backing off, and {@link #SHOW_MS} since it last showed. */
    boolean mayShow() { return waitMs() == 0 && (!shownYet || now() - shownAt >= SHOW_MS * MS); }
    /** What settling changed was shown. */
    void shown() { shownAt = now(); shownYet = true; shows++; }
}
