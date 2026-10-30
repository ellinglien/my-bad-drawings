package io.github.mpdairy.monopaint;

/** Supernote's measured signed X/Y tilt degrees, projected onto the page. */
final class BrushDirection {
    private static final double MIN_LEAN = Math.tan(Math.toRadians(5));
    static float resolve(float tiltX, float tiltY, float offset, float fallback) {
        if (!Float.isFinite(tiltX) || !Float.isFinite(tiltY)
                || Math.abs(tiltX) > 90 || Math.abs(tiltY) > 90) return fallback;
        double x=Math.tan(Math.toRadians(Math.max(-89.9,Math.min(89.9,tiltX))));
        double y=Math.tan(Math.toRadians(Math.max(-89.9,Math.min(89.9,tiltY))));
        // A nearly upright pen has no dependable azimuth; keep its last heading.
        if (Math.hypot(x,y) < MIN_LEAN) return fallback;
        // A flat brush's broad edge lies across its shaft's lean direction.
        return normalize((float)Math.toDegrees(Math.atan2(y,x))+90+offset);
    }
    static float againstLean(float tiltX, float tiltY, float dx, float dy) {
        if (!Float.isFinite(tiltX) || !Float.isFinite(tiltY) || !Float.isFinite(dx) || !Float.isFinite(dy)
                || Math.abs(tiltX)>90 || Math.abs(tiltY)>90) return 0;
        double x=Math.tan(Math.toRadians(Math.max(-89.9,Math.min(89.9,tiltX))));
        double y=Math.tan(Math.toRadians(Math.max(-89.9,Math.min(89.9,tiltY))));
        double lean=Math.hypot(x,y), distance=Math.hypot(dx,dy);
        if (lean<MIN_LEAN || distance<.001) return 0;
        // Keep signed lean here: a head has 180-degree symmetry, a push does not.
        return (float)Math.max(0,Math.min(1,-(x*dx+y*dy)/(lean*distance)));
    }
    /**
     * Which end of a head lying along {@code angle} degrees the pen's shaft leans toward:
     * +1 along the angle, -1 against it, or {@code fallback} without a dependable lean.
     */
    static float shaftSide(float tiltX, float tiltY, float angle, float fallback) {
        if (!Float.isFinite(tiltX) || !Float.isFinite(tiltY) || Math.abs(tiltX) > 90 || Math.abs(tiltY) > 90) return fallback;
        double x=Math.tan(Math.toRadians(Math.max(-89.9,Math.min(89.9,tiltX))));
        double y=Math.tan(Math.toRadians(Math.max(-89.9,Math.min(89.9,tiltY))));
        if (Math.hypot(x,y) < MIN_LEAN) return fallback;
        return x*Math.cos(Math.toRadians(angle)) + y*Math.sin(Math.toRadians(angle)) >= 0 ? 1 : -1;
    }
    /**
     * The lean's direction on the page in radians, as the modular engine's tiltOf().dir: atan2(tan tiltY, tan tiltX);
     * NaN without a dependable lean (under 5 degrees, or no reading). Doubles, so a tilt interpolated between samples
     * reads as the prototype reads it.
     */
    static double azimuth(double tiltX, double tiltY) {
        if (!Double.isFinite(tiltX) || !Double.isFinite(tiltY) || Math.abs(tiltX) > 90 || Math.abs(tiltY) > 90) return Double.NaN;
        double x = slope(tiltX), y = slope(tiltY);
        return Math.hypot(x, y) < MIN_LEAN ? Double.NaN : Math.atan2(y, x);
    }
    /**
     * As {@link #lean}, in the modular engine's arithmetic (tiltOf().lean, doubles), so a tilt interpolated between
     * samples gives the prototype's lean to the bit: 0 near upright to 1 at 65 degrees, 0 without a reading.
     */
    static double tiltLean(double tiltX, double tiltY) {
        if (!Double.isFinite(tiltX) || !Double.isFinite(tiltY) || Math.abs(tiltX) > 90 || Math.abs(tiltY) > 90) return 0;
        double inclination = Math.atan(Math.hypot(slope(tiltX), slope(tiltY))) / (Math.PI / 180);
        return Math.max(0, Math.min(1, (inclination - 5) / 60));
    }
    /** The tangent of a tilt reading, in the prototype's arithmetic (degrees times PI/180), so it matches to the bit. */
    private static double slope(double degrees) { return Math.tan(Math.max(-89.9, Math.min(89.9, degrees)) * (Math.PI / 180)); }
    /** Degrees the pen leans from upright; 0 without a reading. */
    static float inclination(float tiltX, float tiltY) { return inclination(tiltX, tiltY, 0); }
    /** Degrees the pen leans from upright; {@code fallback} without a reading. */
    static float inclination(float tiltX, float tiltY, float fallback) {
        if (!Float.isFinite(tiltX) || !Float.isFinite(tiltY) || Math.abs(tiltX) > 90 || Math.abs(tiltY) > 90) return fallback;
        double x = Math.tan(Math.toRadians(Math.min(89.9, Math.abs(tiltX))));
        double y = Math.tan(Math.toRadians(Math.min(89.9, Math.abs(tiltY))));
        return (float)Math.toDegrees(Math.atan(Math.hypot(x,y)));
    }
    /** How far a tool broadens with tilt: 0 near upright to 1 at 65 degrees and beyond. */
    static float lean(float tiltX, float tiltY) {
        return Math.max(0, Math.min(1, (inclination(tiltX, tiltY) - 5) / 60));
    }
    static float normalize(float angle) { return (angle % 180 + 180) % 180; }
    static float delta(float from, float to) {
        // Both heads are symmetric: 179 -> 1 is a two-degree turn, not 178.
        return normalize(to-from+90)-90;
    }
    private BrushDirection() {}
}
