package io.github.mpdairy.monopaint;

/**
 * Wall-clock limits in the desktop checks. A busy Mac must not fail the regular pass, so by default a limit that is
 * missed only prints "TIME over limit: ..." with the measured time; it fails when {@code -Dstrict.timing=true} or the
 * env var {@code STRICT_TIMING=1} is set (scripts/bench_desktop.sh sets it). Correctness stays on each file's check().
 */
final class Timing {
    static final boolean STRICT = Boolean.getBoolean("strict.timing") || "1".equals(System.getenv("STRICT_TIMING"));

    /** {@code ok}: the time is within its limit; {@code what} says the limit and the measured time. */
    static void limit(boolean ok, String what) {
        if (ok) return;
        if (STRICT) throw new AssertionError(what);
        System.out.println("TIME over limit: " + what.replace('\n', ' '));
    }

    private Timing() {}
}
