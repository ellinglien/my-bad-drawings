package io.github.mpdairy.monopaint;

import java.util.Arrays;

/**
 * A stroke's samples, as the prototype's addPt over addPoint (engine.js 51–66, port.js 277–286): position,
 * pressure, distance along the stroke, smoothed speed (px/ms), tilt, the running peak pressure and the smoothed
 * travel direction. A sample under 0.6 px from the last is skipped. Speed is the hand's on the glass ({@link #zoom}).
 * Pure, checked on the desktop.
 */
final class StrokePoints {
    int count;
    float[] x = new float[64], y = new float[64], p = new float[64], d = new float[64], v = new float[64],
            tx = new float[64], ty = new float[64], pk = new float[64], ux = new float[64], uy = new float[64], ga = new float[64];
    long[] t = new long[64];
    // ux, uy: NaN until a second sample sets the first segment's direction on both its ends.
    // ga: the travel direction · the page's tilt (Gravity Lab's addPt), NaN where the page was level.
    /** The page's downhill vector the next samples are drawn on ({@link PageTilt}); level (0, 0) unless set. */
    double gx, gy;
    /** Screen px a page px shows as ({@link DrawingStroke#zoom}): speed is the hand's on the glass, page px/ms at 100%. */
    float zoom = 1;
    /**
     * Flick ({@link FlickFling#carry}): the samples past this distance are the stroke carrying on past its lift, over
     * {@link #flickLen} px; the stampers taper and dry them ({@link #flickWidth}, {@link #flickDry}). Infinite for none.
     */
    float flickFrom = Float.POSITIVE_INFINITY, flickLen;
    /**
     * Paint Load ({@link LoadedPaint.Run}): how much of its own paint the brush still lays at each sample, 1 loaded to 0
     * empty; null for a brush that never runs out, which every stamper reads as 1 ({@link #own(int, int, double)}).
     * Gradient ({@link GreyGradient#apply}) writes its grey's share here too, times Paint Load's when both are on.
     */
    float[] own;

    /**
     * Adds a sample at time {@code time} (ms, or {@link PenSpeed#UNTIMED}); false when it is skipped. Speed is
     * smoothed a quarter at a time; a segment without times at both ends adds 0 to it, as the old Wild ink's did.
     */
    boolean add(float x, float y, float p, long time, float tiltX, float tiltY) {
        if (count == this.x.length) grow();
        int k = count;
        if (k == 0) {
            this.x[0] = x; this.y[0] = y; this.p[0] = p; t[0] = time; d[0] = 0; v[0] = 0;
            tx[0] = tiltX; ty[0] = tiltY; pk[0] = Math.max(0, p); ux[0] = Float.NaN; uy[0] = Float.NaN; ga[0] = Float.NaN;
            count = 1;
            return true;
        }
        int o = k - 1;
        double seg = Math.hypot(x - this.x[o], y - this.y[o]);
        if (seg < 0.6) return false;
        boolean timed = time != PenSpeed.UNTIMED && t[o] != PenSpeed.UNTIMED;
        double instant = timed ? seg * zoom / Math.max(1, time - t[o]) : 0;
        this.x[k] = x; this.y[k] = y; this.p[k] = p; t[k] = time;
        d[k] = (float)(d[o] + seg);
        v[k] = (float)(v[o] * 0.75 + instant * 0.25);
        tx[k] = tiltX; ty[k] = tiltY;
        pk[k] = Math.max(pk[o], p);
        double dx = (x - this.x[o]) / seg, dy = (y - this.y[o]) / seg;
        if (Float.isNaN(ux[o])) { ux[o] = (float)dx; uy[o] = (float)dy; ux[k] = (float)dx; uy[k] = (float)dy; }
        else {
            double s = 1 - Math.exp(-seg / 12);
            double sx = ux[o] + (dx - ux[o]) * s, sy = uy[o] + (dy - uy[o]) * s, m = Math.hypot(sx, sy);
            if (m == 0) m = 1;
            ux[k] = (float)(sx / m); uy[k] = (float)(sy / m);
        }
        ga[k] = Float.NaN;
        if (gx != 0 || gy != 0) { ga[k] = (float)(ux[k] * gx + uy[k] * gy); if (Float.isNaN(ga[o])) ga[o] = ga[k]; }
        count = k + 1;
        return true;
    }

    /**
     * How far the stroke runs downhill ({@code > 0}) or uphill on the tilted page between samples {@code j} and
     * {@code k} at {@code t} (gaAt): 0 where neither was drawn on a tilted page.
     */
    double ga(int j, int k, double t) {
        float a = ga[j], b = ga[k];
        if (a != a && b != b) return 0;
        return StrokeMath.lerp(a != a ? 0 : a, b != b ? 0 : b, t);
    }

    /** {@link #own} made, as long as the samples' arrays. */
    void owns() { if (own == null) own = new float[x.length]; }
    /** The paint left {@code t} of the way from sample {@code j} to {@code k} ({@link #own}): 1 for a brush that never runs out. */
    double own(int j, int k, double t) { return own == null ? 1 : own[j] + (own[k] - (double) own[j]) * t; }

    /** Whether the stroke carries on past its lift ({@link #flickFrom}). */
    boolean flicks() { return flickFrom != Float.POSITIVE_INFINITY; }
    /** How far along the flick distance {@code at} is, 0 (before it) to 1 (its end). */
    private double flickU(double at) { return at <= flickFrom ? 0 : Math.min(1, (at - flickFrom) / flickLen); }
    /** A flick's taper at distance {@code at}: 1 up to the lift, narrowing to nothing at its end (mischief-lab.html's {@code (1 − u)^.75}). */
    double flickWidth(double at) { double u = flickU(at); return u <= 0 ? 1 : Math.pow(1 - u, .75); }
    /** A flick's dryness at distance {@code at}: none up to its first 30%, then rising to .8 of the bristles left dry at its end (the prototype's). */
    double flickDry(double at) { return StrokeMath.smoothstep(.3, 1, flickU(at)) * .8; }

    /** The stroke's length so far: 0 when empty. */
    float total() { return count == 0 ? 0 : d[count - 1]; }
    /** The first sample from 1 at or past distance {@code at}, at most the last (stroke.js firstAt). */
    int firstAt(float at) {
        int k = 1;
        while (k < count - 1 && d[k] < at) k++;
        return k;
    }

    /** stroke.js hashPts: FNV-1a over the samples' positions to a quarter pixel, so the same stroke hashes the same. */
    int hash() {
        int h = 0x811c9dc5;
        for (int k = 0; k < count; k++) { h = (h ^ Math.round(x[k] * 4)) * 16777619; h = (h ^ Math.round(y[k] * 4)) * 16777619; }
        return h;
    }

    private void grow() {
        int more = 2 * x.length;
        x = Arrays.copyOf(x, more); y = Arrays.copyOf(y, more); p = Arrays.copyOf(p, more); d = Arrays.copyOf(d, more);
        v = Arrays.copyOf(v, more); tx = Arrays.copyOf(tx, more); ty = Arrays.copyOf(ty, more); pk = Arrays.copyOf(pk, more);
        ux = Arrays.copyOf(ux, more); uy = Arrays.copyOf(uy, more); ga = Arrays.copyOf(ga, more); t = Arrays.copyOf(t, more);
        if (own != null) own = Arrays.copyOf(own, more);
    }
}
