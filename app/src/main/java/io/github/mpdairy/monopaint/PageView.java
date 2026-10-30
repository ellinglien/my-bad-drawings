package io.github.mpdairy.monopaint;

/**
 * Where the page lies on the canvas: a quarter turn (MonoPaint's, when its page stayed the tablet's way up while the
 * app turned; Inkwash's page turns with the app, so {@code DrawingPad} passes 0), then the zoom's scale and offset ({@link CanvasViewport}), as the nine values of an affine map from page
 * pixels to view pixels ({@code android.graphics.Matrix#setValues}'s order). {@code DrawingPad} lays the page, the pen
 * and everything drawn over it (the eraser's ring, a magnet's outline) through it at any zoom. Pure, checked on the
 * desktop ({@code PinchPanChecks}).
 */
final class PageView {
    private PageView() { }

    /**
     * The map for a {@code width × height} page (page px) turned {@code quarter} quarter turns as {@code
     * android.view.Surface}'s rotations count them (1: the app turned a quarter, the page a quarter back), scaled by
     * {@code scale} and moved by {@code x, y} view px: {a, b, c, d, e, f, 0, 0, 1}, a view point being
     * (a·px + b·py + c, d·px + e·py + f).
     */
    static float[] map(int quarter, int width, int height, float scale, float x, float y) {
        float s = scale;
        switch (quarter & 3) {
            case 1: return new float[]{0, s, x, -s, 0, s * width + y, 0, 0, 1};
            case 2: return new float[]{-s, 0, s * width + x, 0, -s, s * height + y, 0, 0, 1};
            case 3: return new float[]{0, -s, s * height + x, s, 0, y, 0, 0, 1};
            default: return new float[]{s, 0, x, 0, s, y, 0, 0, 1};
        }
    }
    /**
     * A direction in the panel's own axes ({@code x, y}: Supernote's pen tilt, the accelerometer's pull) in the axes of
     * the app turned {@code quarter} (as {@link #map} counts it), where the page lies upright: the map's turn alone.
     */
    static float[] turn(int quarter, float x, float y) { return apply(map(quarter, 0, 0, 1, 0, 0), new float[]{x, y}); }
    /** {@code p} (x, y pairs) through {@code m}, in place. */
    static float[] apply(float[] m, float[] p) {
        for (int i = 0; i + 1 < p.length; i += 2) {
            float px = p[i], py = p[i + 1];
            p[i] = m[0] * px + m[1] * py + m[2];
            p[i + 1] = m[3] * px + m[4] * py + m[5];
        }
        return p;
    }
    /** The map back, view to page. */
    static float[] invert(float[] m) {
        float det = m[0] * m[4] - m[1] * m[3];
        float a = m[4] / det, b = -m[1] / det, d = -m[3] / det, e = m[0] / det;
        return new float[]{a, b, -(a * m[2] + b * m[5]), d, e, -(d * m[2] + e * m[5]), 0, 0, 1};
    }
}
