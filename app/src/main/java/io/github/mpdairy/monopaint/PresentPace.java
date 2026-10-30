package io.github.mpdairy.monopaint;

/**
 * How often a pad presents to the panel: while a stroke is drawn, presents are spaced by about half again as long as
 * one takes lately (a running mean), 8 to 100 ms, so that a slow panel driver (Manta firmware 3.30.45 queues gray
 * updates) takes fewer, larger updates instead of holding the pen's input up at every one; 8 ms otherwise. The drawing
 * screen's ({@link DrawingPad}) and Brush Lab's test page's ({@link BrushTestPad}). Pure.
 */
final class PresentPace {
    /** How long a present takes lately, ms. */
    private float ms;
    /** When the last present was made, the uptime clock's ms. */
    long last;

    /** The least time between presents, ms, while a stroke is drawn ({@code stroking}) or not. */
    long gap(boolean stroking) { return !stroking ? 8 : Math.max(8, Math.min(100, Math.round(1.5f * ms))); }
    /** How long to wait before presenting at {@code now} (uptime ms); 0: present now. */
    long wait(boolean stroking, long now) { long elapsed = now - last, gap = gap(stroking); return elapsed < gap ? gap - elapsed : 0; }
    /** A present took {@code nanos} and ended at {@code now} (uptime ms). */
    void presented(long nanos, long now) { ms += (nanos / 1e6f - ms) * .3f; last = now; }
}
