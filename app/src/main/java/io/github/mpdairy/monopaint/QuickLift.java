package io.github.mpdairy.monopaint;

import android.os.SystemClock;
import android.util.Log;
import android.view.View;

/**
 * A stroke whose work after lift runs off the UI thread ({@link LiftingStroke}: a quick stroke's render, a Drying
 * stroke's settle): its thread works while this looks in every few ms, the UI thread free meanwhile (taps land); then
 * {@code done} finishes it as any other (written, shown, saved, the drying ticker started).
 * The host keeps it as its stroke until then, so anything that needs the page first (a new stroke, undo, another page,
 * a pause, the wet indicator's tap) finishes it at once, as it would a stroke still down. Shared by the drawing screen
 * and Brush Lab's test page.
 */
final class QuickLift {
    /** How often it looks in on the render thread after lift, ms. */
    static final long POLL_MS = 5;
    private final View view;
    private final Runnable done;
    private final Runnable step = this::step;
    private LiftingStroke stroke;
    private long liftedAt;

    QuickLift(View view, Runnable done) { this.view = view; this.done = done; }

    /** The pen lifted from {@code s}: true if its work after lift runs off this thread ({@link LiftingStroke}), now until done or {@link #stop}. */
    boolean start(DrawingStroke s) {
        if (!(s instanceof LiftingStroke) || !((LiftingStroke)s).lift()) return false;
        stroke = (LiftingStroke)s; liftedAt = SystemClock.uptimeMillis();
        view.post(step);
        return true;
    }
    /** A stroke is working after lift. */
    boolean running() { return stroke != null; }
    /** No more looking in: the host finishes the stroke now (its finish waits for the render and composes at once). */
    void stop() {
        view.removeCallbacks(step);
        if (stroke != null) report("cut short");
        stroke = null;
    }
    private void step() {
        if (stroke == null) return;
        if (stroke.develop(0)) { view.postDelayed(step, POLL_MS); return; }
        report("done");
        stroke = null;
        done.run();
    }
    private void report(String how) {
        if (!(stroke instanceof QuickStroke)) { Log.i(ProbeActivity.TAG, "stroke timing lift lift_to_final_ms=" + (SystemClock.uptimeMillis() - liftedAt) + " " + how); return; }
        QuickStroke stroke = (QuickStroke)this.stroke;
        Log.i(ProbeActivity.TAG, String.format(java.util.Locale.ROOT, "stroke timing quick samples=%d by_lift=%d render_ms=%.1f after_lift_ms=%.1f finish_ms=%.1f lift_to_final_ms=%d trail_tiles=%d trail_writes=%d %s",
                stroke.samples(), stroke.takenBeforeLift, stroke.renderNanos / 1e6, stroke.afterLiftNanos / 1e6, stroke.finishNanos / 1e6, SystemClock.uptimeMillis() - liftedAt, stroke.trailTiles, stroke.trailWrites, how));
    }
}
