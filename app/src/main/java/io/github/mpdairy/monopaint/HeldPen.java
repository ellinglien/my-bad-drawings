package io.github.mpdairy.monopaint;

/**
 * A pen resting on the page may send no samples at all (the browser sends none; a panel may filter a still pen's
 * reports): for a stroke that builds while held ({@link DrawingStroke#buildsWhileHeld}, Stipple's Build-up), the pad
 * hands it the last sample again, at the time, once the pen has been quiet a tick, so dots keep landing. A resent
 * sample is an ordinary one at the same place (too close to keep as a point), so a quick stroke's render and a live
 * stroke get the same samples and end alike. Pure; {@link DrawingPad} and {@link BrushTestPad} tick it.
 */
final class HeldPen {
    /** How long the pen may be quiet before its sample is sent again, ms: a few of Build-up's 20 ms ticks. */
    static final long TICK_MS = 30;
    private float x, y, pressure, tiltX, tiltY;
    private long time = PenSpeed.UNTIMED;

    /** The pen's latest sample. */
    void sampled(float x, float y, float pressure, float tiltX, float tiltY, long time) {
        this.x = x; this.y = y; this.pressure = pressure; this.tiltX = tiltX; this.tiltY = tiltY; this.time = time;
    }
    /** The pen lifted: nothing to send again. */
    void clear() { time = PenSpeed.UNTIMED; }
    /** Whether {@code stroke} should hear from a quiet pen at {@code now} (the samples' clock, ms): it builds while held and the pen has been quiet a tick. */
    boolean due(DrawingStroke stroke, long now) { return stroke != null && stroke.buildsWhileHeld() && time != PenSpeed.UNTIMED && now - time >= TICK_MS; }
    /** The last sample again, at {@code now}, to {@code stroke}. */
    void resend(DrawingStroke stroke, long now) { time = now; stroke.sample(x, y, pressure, tiltX, tiltY, now); }
}
