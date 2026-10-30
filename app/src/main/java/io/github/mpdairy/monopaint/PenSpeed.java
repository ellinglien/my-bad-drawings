package io.github.mpdairy.monopaint;

/**
 * How fast the pen or finger moves, and what that does to the eraser, the practical exception to
 * physical media (see PHILOSOPHY.md): small and accurate when slow, big when fast. (Brushes read speed
 * through their own Control part; {@link #UNTIMED} marks a sample without a time for them too.)
 *
 * <p>Each stroke keeps its own, fed every sample's page position and time. Speed is smoothed
 * over about {@link #SMOOTHING_MS}, eased into a fastness from 0 (slow) to 1 (fast) between
 * {@link #SLOW} and {@link #FAST}, and the fastness may change no quicker than a full swing in
 * {@link #FULL_SWING_MS}, so widths change gradually and corners never pinch or bulge. Each
 * {@link Effect} turns the fastness into a factor. Samples without a time ({@link #UNTIMED})
 * leave every factor at 1: the strokes behave exactly as they did before speed.
 *
 * <p>Calibrated for the Manta: page pixels are screen pixels on a 1920-wide, 300 dpi page, and the
 * pen reports about 480 samples a second. A careful, slow move runs under 0.3 px/ms (1 px/ms is
 * about 8.5 cm/s); a quick flick 3 px/ms or more. Zoomed in, a page pixel shows wider ({@link #zoom}),
 * and speeds are the hand's on the glass, screen px/ms, as the calibration's.
 */
final class PenSpeed {
    /** The time of a sample that has none, e.g. the desktop checks': no speed effect. */
    static final long UNTIMED = Long.MIN_VALUE;
    /** The time constant, in ms, of the exponential smoothing of the speed: about a tenth of a second. */
    static final float SMOOTHING_MS = 100;
    /** Speeds in page px/ms: up to SLOW a careful, accurate move with no speed effect; from FAST a quick flick with all of it. */
    static final float SLOW = .3f, FAST = 3f;
    /**
     * The fastest the fastness may go from 0 to 1 or back, in ms: the rate limit on every factor.
     * A sudden stop or flick moves a factor by at most its range times {@code ms / FULL_SWING_MS}.
     * 150 ms keeps a 220 px eraser from jumping while still growing within a short flick.
     */
    static final float FULL_SWING_MS = 150;

    /** What speed does to a tool: a factor from {@link #slow} (fastness 0) to {@link #fast} (fastness 1). */
    enum Effect {
        /** The pen eraser's diameter, as a share of its pressure size: 30% when slow, all of it when fast. */
        PEN_ERASER(.3f, 1),
        /**
         * The finger eraser's diameter, as a share of the way from {@link InkWashSizes#FINGER_NARROWEST}
         * (slow, a fine point) to its contact size (fast): see {@link #fingerDiameter}.
         */
        FINGER_ERASER(0, 1);
        final float slow, fast;
        Effect(float slow, float fast) { this.slow = slow; this.fast = fast; }
        /** The factor at {@code fastness}, 0 to 1. */
        float at(float fastness) { return slow + (fast - slow) * fastness; }
    }

    private float lastX, lastY, pending, speed, fastness;
    private long lastTime;
    private boolean timed;
    /** Screen px a page px shows as ({@link DrawingStroke#zoom}); 1 at 100%. */
    float zoom = 1;

    /** The pen at {@code x, y} (page pixels) at {@code time} ms, or {@link #UNTIMED}, which is ignored. */
    void sample(float x, float y, long time) {
        if (time == UNTIMED || !Float.isFinite(x) || !Float.isFinite(y)) return;
        if (!timed) { timed = true; lastX = x; lastY = y; lastTime = time; return; }
        // Millisecond times repeat at 480 samples a second: the distance waits until the clock moves.
        pending += (float)Math.hypot(x - lastX, y - lastY) * zoom;
        lastX = x; lastY = y;
        long elapsed = time - lastTime;
        if (elapsed <= 0) return;
        lastTime = time;
        float instant = pending / elapsed;
        pending = 0;
        speed += (instant - speed) * (1 - (float)Math.exp(-elapsed / SMOOTHING_MS));
        float step = elapsed / FULL_SWING_MS;
        fastness += Math.max(-step, Math.min(step, fastnessAt(speed) - fastness));
    }
    /** The smoothed speed in page px/ms; 0 before two timed samples. */
    float speed() { return speed; }
    /** How fast the pen moves, 0 (slow) to 1 (fast), smoothed and rate-limited. */
    float fastness() { return fastness; }
    /** {@code effect}'s factor now; 1, no effect, until a timed sample. */
    float factor(Effect effect) { return timed ? effect.at(fastness) : 1; }

    /** Fastness for a smoothed {@code speed}: 0 up to {@link #SLOW}, 1 from {@link #FAST}, eased in and out between. */
    static float fastnessAt(float speed) {
        if (!(speed > SLOW)) return 0;
        float t = Math.min(1, (speed - SLOW) / (FAST - SLOW));
        return t * t * (3 - 2 * t);
    }
    /**
     * The finger eraser's diameter for a contact erasing {@code broadest} px when fast, at {@code factor}
     * ({@link Effect#FINGER_ERASER}): slow narrows it to {@link InkWashSizes#FINGER_NARROWEST}.
     */
    static float fingerDiameter(float broadest, float factor) {
        float narrowest = Math.min(broadest, InkWashSizes.FINGER_NARROWEST);
        return narrowest + (broadest - narrowest) * factor;
    }
}
