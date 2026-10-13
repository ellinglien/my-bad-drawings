package io.github.mpdairy.monopaint;

/**
 * The ink a wet brush holds in its water between strokes. Its water never runs out.
 * Wherever it touches flowing water it trades ink with each pixel toward the same shade:
 * a clean brush soaks dark ink up and leaves the paint lighter, an inky one lets it out
 * over lighter paint, and once it is about as inky as the paint under it, it only smears.
 * <p>
 * A brush can be {@link #tint tinted} with a shade, the lightest it ever lays: it never
 * runs out of that ink, and only what it picks up on top of it trades away. The wet brush
 * pen's ink is untinted, so it can soak up and give out everything.
 */
final class BrushInk {
    /** How much ink a full brush holds, in pixels of full black. */
    static final float CAPACITY = 3000;
    /** Share of the difference between a pixel and the brush that one dab trades at full pressure. */
    private static final float RATE = .2f;
    /** Ink held, in pixels of full black, from the tint's share of {@link #CAPACITY} up to all of it. */
    private float ink;
    /** The tint, as ink in the water, 0 (clear) to 1 (black). */
    private float floor;
    /**
     * On a dry canvas, the dry paint this brush's water lands on stays stuck to the paper and
     * only slowly soaks loose, as in a watercolor wash; the wet brush pen lifts it at once.
     */
    final boolean dryPaintSticks;

    /** The wet brush pen's ink, which lifts dry paint at once. */
    BrushInk() { this(false); }
    BrushInk(boolean dryPaintSticks) { this.dryPaintSticks = dryPaintSticks; }

    /** How inky the brush is, 0 (clear water) to 1 (black). */
    float fullness() { return ink / CAPACITY; }
    /** The lightest shade the brush lays, as ink in the water from 0 to 1. */
    float tint() { return floor; }
    /** Restores a saved brush. */
    void restore(float fullness, float tint) {
        floor = finite(tint); ink = Math.max(floor, finite(fullness)) * CAPACITY;
    }
    private static float finite(float value) { return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0; }
    /** Fills the brush with ink of {@code gray}, as if dipped in it, or with only its tint if that is darker. */
    void load(int gray) { ink = Math.max(floor, (255 - Math.max(0, Math.min(255, gray))) / 255f) * CAPACITY; }
    /** Leaves only the tint's ink in the brush. */
    void clean() { ink = floor * CAPACITY; }
    /** Sets the brush's ink to {@code gray}, which then is the lightest it lays. */
    void tint(int gray) { floor = (255 - Math.max(0, Math.min(255, gray))) / 255f; clean(); }
    /**
     * Ink, out of 255, that moves from a pixel holding {@code pigment} into the brush; negative
     * moves out of the brush. The brush gains or loses it, never beyond full or below its tint.
     * @param share of a full exchange, from pressure and how much of the pixel the dab covers
     */
    float trade(float pigment, float share) {
        float moved = (pigment - 255 * fullness()) * RATE * share;
        moved = Math.max(-(ink - floor * CAPACITY) * 255, Math.min((CAPACITY - ink) * 255, moved));
        ink += moved / 255;
        return moved;
    }
}
