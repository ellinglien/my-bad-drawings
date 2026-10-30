package io.github.mpdairy.monopaint;

/**
 * Which way is down on the page, and how strongly, for wet paint that moves with the tablet's tilt (Gravity): x right,
 * y down, its length {@link #m} 0 (level) to 1 (upright). A nearly flat tablet reads as level: below {@link #FLAT} of
 * gravity in the page's plane (about 4.6°, MonoPaint's {@code CanvasGravity} dead zone) nothing moves, so paint never
 * creeps on a desk; past it the strength rises from 0, so there is no jump at the edge of the dead zone. Pure, checked
 * on the desktop; {@code CanvasGravity} makes one from the accelerometer.
 */
final class PageTilt {
    /** Gravity's share in the page's plane below which the page counts as level (MonoPaint's CanvasGravity.FLAT). */
    static final double FLAT = .08;
    static final PageTilt LEVEL = new PageTilt(0, 0);

    /** Where the tilt comes from: the sensor on the device, a fixed one in the checks. */
    interface Source { PageTilt now(); }

    /** Downhill, scaled by the strength {@link #m}. */
    final double x, y;
    /** The strength: 0 level, 1 upright. */
    final double m;

    private PageTilt(double x, double y) { this.x = x; this.y = y; this.m = Math.hypot(x, y); }

    /** A tilt already past the dead zone: downhill {@code x, y}, its length the strength (at most 1). For the checks and the prototype's values. */
    static PageTilt of(double x, double y) {
        double m = Math.hypot(x, y);
        if (!(m > 0)) return LEVEL;
        double k = m > 1 ? 1 / m : 1;
        return new PageTilt(x * k, y * k);
    }

    /**
     * From gravity's pull in the page's plane as a share of g ({@code gx} right, {@code gy} down, already turned to the
     * page): level inside {@link #FLAT}, then the strength rising to 1 upright, as MonoPaint's CanvasGravity.apply.
     */
    static PageTilt pull(double gx, double gy) {
        double length = Math.hypot(gx, gy);
        if (!(length > FLAT)) return LEVEL;
        double strength = Math.min(1, (length - FLAT) / (1 - FLAT)) / length;
        return new PageTilt(gx * strength, gy * strength);
    }

    /**
     * The accelerometer's reading ({@code ax, ay} in m/s², the device's axes: x right, y up in its natural orientation)
     * turned to a page shown with the display's rotation {@code quarters} (Display.getRotation, 0 to 3): gravity's pull
     * in the page's plane, in g. Android stays in portrait; the drawing screen's page turns with the app, so there
     * {@code quarters} is the app's own turn ({@code CanvasGravity}); in Brush Lab it is 0. The same turn as {@link
     * PageView#turn}, which turns the pen's tilt.
     */
    static double[] fromDevice(double ax, double ay, int quarters) {
        // Gravity pulls opposite to what the accelerometer reads; screen y runs down. Turned a quarter (the device
        // turned a quarter anticlockwise), the screen's x is the device's −y and its up the device's x.
        double x = -ax / 9.80665, y = ay / 9.80665;
        switch (((quarters % 4) + 4) % 4) {
            case 1: return new double[]{y, -x};
            case 2: return new double[]{-x, -y};
            case 3: return new double[]{-y, x};
            default: return new double[]{x, y};
        }
    }

    boolean level() { return m == 0; }
    /** Unit downhill (0, 0 when level). */
    double ux() { return m > 0 ? x / m : 0; }
    double uy() { return m > 0 ? y / m : 0; }

    @Override public String toString() { return level() ? "level" : String.format(java.util.Locale.ROOT, "(%.2f, %.2f)", x, y); }
}
