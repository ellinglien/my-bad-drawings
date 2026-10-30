package io.github.mpdairy.monopaint;

/**
 * Where a six-part stroke's time goes, section by section, for the timing bench (androidTest ModularPerfBench, run on
 * the device and the desktop), and a fill's (StabilityBench's fill: finding its area, then laying it). Off unless the bench turns it on: then each section's nanoseconds and a few counts add
 * up here (counts from any thread). Off, a section costs a field read.
 */
final class Prof {
    static final int LINE = 0, BODY = 1, MERGE = 2, WET_RAISE = 3, WET_SETTLE = 4, COMPOSE = 5, LIFT = 6, LIFT_STAMP = 7,
            LIFT_SETTLE = 8, LIFT_COMPOSE = 9, SETTLES = 10, SETTLE_PX = 11, COMPOSE_PX = 12, TONED_PX = 13, SHOWN_PX = 14, RETAPER = 15,
            G_RUN = 16, G_PAIRS = 17, G_EDGE = 18, G_SETTLE = 19, G_DRIPS = 20, G_RENDER = 21, G_WRITE = 22, G_PATCH = 23, G_MAKE = 24,
            QUICK = 25, QUICK_FINAL = 26, CARRY = 27,
            F_INK = 28, F_BANDS = 29, F_GAP = 30, F_FLOOD = 31, F_MARGIN = 32, F_UNDER = 33, F_POCKETS = 34, F_REFINE = 35,
            F_LAY = 36, F_SETTLE = 37, F_COMPOSE = 38, N = 39;
    static final String[] NAMES = {"line", "body", "merge", "wetRaise", "wetSettle", "compose", "lift", "lift.stamp",
            "lift.settle", "lift.compose", "#settles", "settlePx", "composePx", "tonedPx", "settleShownPx", "line.retaper",
            "g.run", "g.pairs", "g.edge", "g.settle", "g.drips", "g.render", "g.write", "g.patch", "g.make",
            "quick.preview", "quick.final", "carry",
            "fill.ink", "fill.bands", "fill.gap", "fill.flood", "fill.margin", "fill.under", "fill.pockets", "fill.refine",
            "fill.lay", "fill.settle", "fill.compose"};
    /** Whether sections are timed. */
    static boolean on;
    static final long[] t = new long[N];

    static void reset() { java.util.Arrays.fill(t, 0); }
    /** The start of a section (0 when off). */
    static long now() { return on ? System.nanoTime() : 0; }
    /** Ends section {@code k}, started at {@code since}. */
    static void add(int k, long since) { if (on) t[k] += System.nanoTime() - since; }
    /** Adds {@code n} to count {@code k}. */
    static void count(int k, long n) { if (on) synchronized (t) { t[k] += n; } }
    /** A rectangle's pixels, for the counts. */
    static long area(int[] r) { return StrokeMath.empty(r) ? 0 : (long)(r[2] - r[0] + 1) * (r[3] - r[1] + 1); }

    private Prof() {}
}
