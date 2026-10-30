package io.github.mpdairy.monopaint;

/**
 * Two fingers on the page while Zoom is unlocked (MonoPaint's pinch and pan, its TODO.md "Zoom and pan lock"), told apart
 * from a two- or three-finger tap (Undo, Redo: {@link MultiFingerTap}). The fingers landing are a tap until one of them
 * moves past a tap's slop from where it landed ({@link #SLOP_DP}, the slop the tap itself allows); from then on they
 * navigate and no tap fires: the page follows them from where they landed, a pinch zooming, a drag panning. A third
 * finger landing before they move makes a three-finger tap of it, never a pinch. Locked (the default) they never
 * navigate, and a tap still undoes. One finger is the finger tool's ({@link FingerTool}); a second takes back what it
 * drew before this sees them. The pen, down or near, stops it ({@code DrawingPad}). And the Zoom button's words. Pure,
 * checked on the desktop ({@code PinchPanChecks}).
 */
final class PinchPan {
    /** How far a finger moves from where it landed before two fingers navigate, dp: a two- or three-finger tap's slop. */
    static final float SLOP_DP = 18;
    /** Fingers closer than this, dp, don't navigate (MonoPaint's): two landing so close are one broad touch. */
    static final float LEAST_SPAN_DP = 24;

    /** The two fingers, -1 for none; whether they navigate yet. */
    private int a = -1, b = -1;
    private boolean moving;
    /** Where they landed, view px. */
    private float downAx, downAy, downBx, downBy;
    /** Their midpoint and span at the last step, view px. */
    private float x, y, span;

    /**
     * Fingers {@code a} and {@code b} landed at {@code ax, ay} and {@code bx, by}, view px: true if they may navigate,
     * once they move ({@link #starts}). Never while {@code locked}, nor {@code leastSpan} apart or closer.
     */
    boolean land(boolean locked, int a, float ax, float ay, int b, float bx, float by, float leastSpan) {
        end();
        if (locked || Math.hypot(bx - ax, by - ay) < leastSpan) return false;
        this.a = a; this.b = b;
        downAx = ax; downAy = ay; downBx = bx; downBy = by;
        return true;
    }
    /** Two fingers are down, landed by {@link #land}: waiting to move, or navigating. */
    boolean tracking() { return a >= 0; }
    /** They navigate (past the slop). */
    boolean moving() { return moving; }
    int first() { return a; }
    int second() { return b; }
    /**
     * The fingers now at {@code ax, ay} and {@code bx, by}: true when this move starts navigation, one of them past
     * {@code slop} from where it landed. The next {@link #step} then goes from where they landed, so the page point
     * under them stays under them.
     */
    boolean starts(float ax, float ay, float bx, float by, float slop) {
        if (a < 0 || moving) return false;
        if (Math.hypot(ax - downAx, ay - downAy) <= slop && Math.hypot(bx - downBx, by - downBy) <= slop) return false;
        moving = true;
        x = (downAx + downBx) / 2; y = (downAy + downBy) / 2;
        span = (float) Math.hypot(downBx - downAx, downBy - downAy);
        return true;
    }
    /**
     * While navigating, the fingers now at {@code ax, ay} and {@code bx, by}: the view's change since the last step as
     * {@link CanvasViewport#gesture}'s {factor, oldX, oldY, newX, newY}; null while they are closer than {@code leastSpan}
     * (a pinch past them is skipped, as MonoPaint's is) or not navigating.
     */
    float[] step(float ax, float ay, float bx, float by, float leastSpan) {
        if (!moving) return null;
        float nx = (ax + bx) / 2, ny = (ay + by) / 2, ns = (float) Math.hypot(bx - ax, by - ay);
        if (ns < leastSpan) return null;
        float[] gesture = {ns / span, x, y, nx, ny};
        x = nx; y = ny; span = ns;
        return gesture;
    }
    /** The fingers lifted, or something else took the page: nothing tracked. */
    void end() { a = b = -1; moving = false; }

    // The Zoom button's words (docs/TEXT-STYLE.md).

    /** The button's percentage: "200%". */
    static String caption(int percent) { return percent + "%"; }
    /** Its screen-reader words: "Zoom 200%, locked". */
    static String describe(int percent, boolean locked) { return "Zoom " + caption(percent) + ", " + (locked ? "locked" : "unlocked"); }
}
