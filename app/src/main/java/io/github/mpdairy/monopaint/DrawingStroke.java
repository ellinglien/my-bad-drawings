package io.github.mpdairy.monopaint;

interface DrawingStroke {
    void sample(float x, float y, float pressure, float tiltX, float tiltY);
    /**
     * {@link #sample(float, float, float, float, float)} at {@code time}, the input's clock in ms, or
     * {@link PenSpeed#UNTIMED}; strokes that follow the pen's speed (see {@link PenSpeed}) override it.
     */
    default void sample(float x, float y, float pressure, float tiltX, float tiltY, long time) { sample(x, y, pressure, tiltX, tiltY); }
    /**
     * Brings the page up to date with the samples so far. Callers that feed an input event's samples (its history
     * included) call it once before showing the page; {@link #finish} presents whatever is left. A stroke that writes
     * as it samples needs nothing here.
     */
    default void present() {}
    /**
     * The canvas's zoom, set before the first sample: a page pixel shows {@code scale} screen pixels wide. A stroke that
     * follows the pen's speed reads it as the hand moves on the glass ({@link PenSpeed}'s calibration), so zoomed in a
     * hand's usual speed is not taken for a crawl. Positions and sizes stay page pixels. Default: no speed to read.
     */
    default void zoom(float scale) {}
    boolean finish();
    /** Wet paint holds still while this stroke is drawn, and moves once it is finished. */
    default boolean holdsWetPaint() { return false; }
    /** It keeps laying paint while the pen rests (Stipple's Build-up): the pad sends a quiet pen's last sample again ({@link HeldPen}). */
    default boolean buildsWhileHeld() { return false; }
    /**
     * Inkwash: a Spiro in Ring ({@link SpiroStamper#guide}): its pinned ring's radius, page px, for a touch-down of
     * {@code pressure} and tilt as {@link #sample} takes them; the pad shows the ring over the page until the lift
     * ({@link MirrorShow#ring}). 0 for none.
     */
    default float ringGuide(float pressure, float tiltX, float tiltY) { return 0; }
    /** Inkwash: the shape that ring takes, a Spiro's Mode ({@link SpiroStamper#box}): 1 the ring, else an Oval's or Bar's track. */
    default int ringMode() { return 1; }
}
