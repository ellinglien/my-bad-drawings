package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;

/**
 * Flick and Fling (the mischief modules spec, Stage 2; mischief-lab.html's section 2, {@code flick}, {@code fling},
 * {@code land2}, {@code watch2}): ink that keeps going after the pen.
 * <ul>
 * <li><b>Flick</b> (a Tip dial): a stroke lifted while still moving carries on along its last heading and turn, eased
 * out, for about Flick × the speed, narrowing and running dry. It is the stroke's own samples synthesized past the lift
 * ({@link #carry}), drawn by the stroke's own stampers ({@link StrokePoints#flickWidth}, {@link StrokePoints#flickDry}),
 * so it is the brush's mark, in the stroke's Undo step and in each reflection of a mirrored stroke.</li>
 * <li><b>Fling</b> (a Medium dial): a hard stop (fast in the last 140 ms, then still for 45 ms with the pen down, or a
 * lift right after it) pools a little ink at the stop and throws drops ahead, nearest landing first ({@link #drops});
 * the stroke lays each, an ellipse along its flight, in its own buffers as the samples' clock reaches it, so they are its
 * medium and its Undo step.</li>
 * </ul>
 * Both read only the samples and the brush's seed, so a redraw of the same samples (a quick stroke's render, a mirror's
 * replay) lands the same. A stroke whose brush has both off makes none of this ({@link #of}).
 *
 * <p>Scale: mischief-lab.html's pad is CSS pixels, a CSS pixel 1/96 in; the Manta's page pixel is 1/300 in. Its lengths
 * and speeds are taken {@link #PROTO} times as the hand's on the glass ({@link StrokePoints#zoom}): zoomed in, the same
 * hand throws as far on the glass and a shorter way on the page. Times are the prototype's. Pure, checked on the desktop
 * ({@code FlickFlingChecks}).
 */
final class FlickFling {
    /** Page px (at 100%) a prototype px is: 300 ppi over CSS's 96. */
    static final double PROTO = 3;
    /** Flick: still moving at the lift (moved within LIFT_MS of it, faster than FLICK_SPEED over the last FLICK_MS: glass px/ms). */
    static final long LIFT_MS = 40, FLICK_MS = 30;
    static final double FLICK_SPEED = .35 * PROTO;
    /** Flick's length on the glass: Flick × the speed × REACH, at most MOST; under LEAST nothing. Its turn at most TURN rad a glass px. */
    static final double REACH = 85, MOST = 260 * PROTO, LEAST = 3 * PROTO, TURN = .012 / PROTO;
    /** Fling: faster than FLING_SPEED (glass px/ms) in the PEAK_MS before a stop, then still STILL_MS; aimed by the last AIM_MS. */
    static final long PEAK_MS = 140, STILL_MS = 45, AIM_MS = 40;
    static final double FLING_SPEED = 1 * PROTO;
    /** Still: the pen within this many glass px of where it stopped (a stylus keeps reporting, a hair apart, when held). */
    static final double STILL_PX = 4;
    /** The speed the drops follow at most, prototype px/ms. */
    static final double FASTEST = 5;
    /** How far back the heading at a sample is read, glass px ({@link Motion#end}). */
    static final double AIM_PX = 3 * PROTO;
    /** The samples' spacing past the lift, page px. */
    static final double STEP = 1.5;
    /** Fling's seed salt. */
    private static final int SALT = 0xf1196;

    private final ResolvedBrush r;
    /** Where the pen came to rest (page px), when it got there (ms) and its last sample then; the latest sample's time. */
    private float ax, ay;
    private long anchorT = PenSpeed.UNTIMED, lastT = PenSpeed.UNTIMED, checkedAt = PenSpeed.UNTIMED;
    private int anchorK;
    /** A fling has been thrown (one a stroke); the pen has lifted. */
    private boolean flung, lifted;
    /** The drops still in the air, in landing order, and the next to land. */
    private Drop[] drops = new Drop[0];
    private int landed;

    /** A drop: where it lands (page px), its half-axes along and across its flight and its heading, when it lands (ms), and a speck beyond. */
    static final class Drop {
        final double x, y, along, across, angle; final long at; final boolean speck;
        Drop(double x, double y, double along, double across, double angle, long at, boolean speck) {
            this.x = x; this.y = y; this.along = along; this.across = across; this.angle = angle; this.at = at; this.speck = speck;
        }
    }

    private FlickFling(ResolvedBrush r) { this.r = r; }
    /** The stroke's watcher, or null when its brush neither flicks nor flings: nothing runs then. */
    static FlickFling of(ResolvedBrush r) { return r.flick > 0 || r.fling > 0 ? new FlickFling(r) : null; }

    /**
     * Every sample, kept as a point or not (a resting pen's, {@link HeldPen}'s), after {@code pts} took it: where the pen
     * rests and since when, and a hard stop held {@link #STILL_MS} with the pen down throws the fling.
     */
    void sampled(StrokePoints pts, float x, float y, long time) {
        if (time == PenSpeed.UNTIMED || lifted || pts.count == 0) return;
        lastT = time;
        if (anchorT == PenSpeed.UNTIMED || Math.hypot(x - ax, y - ay) * pts.zoom > STILL_PX) { ax = x; ay = y; anchorT = time; anchorK = pts.count - 1; return; }
        if (flung || r.fling <= 0 || time - anchorT < STILL_MS || checkedAt == anchorT) return;
        // Each stop is weighed once.
        checkedAt = anchorT;
        double peak = peak(pts, anchorK, PEAK_MS);
        if (peak > FLING_SPEED) fling(pts, peak, time);
    }

    /**
     * The pen lifted, at its latest sample: still moving, a Flick's samples past the lift ({@link #carry}); just stopped
     * after speed, the fling. Once; nothing after a fling.
     */
    Carry lift(StrokePoints pts) {
        if (lifted) return null;
        lifted = true;
        if (flung || lastT == PenSpeed.UNTIMED || pts.count < 3) return null;
        int last = pts.count - 1;
        Motion m = motion(pts, last, FLICK_MS);
        if (lastT - anchorT < LIFT_MS && m.speed > FLICK_SPEED) return r.flick > 0 ? carry(pts, m, r.flick) : null;
        if (r.fling > 0) {
            double peak = peak(pts, anchorK, PEAK_MS);
            if (peak > FLING_SPEED) fling(pts, peak, lastT);
        }
        return null;
    }

    /** The flick's samples past the lift: page px, the pressure's travel and the samples' clock; tilt held as at the lift. */
    static final class Carry {
        final int n; final float[] x, y, p; final long[] t; final float tx, ty;
        Carry(int n, float tx, float ty) { this.n = n; x = new float[n]; y = new float[n]; p = new float[n]; t = new long[n]; this.tx = tx; this.ty = ty; }
    }
    /**
     * mischief-lab.html's flick: on from the last sample along its heading there ({@link Motion#end}), turning as it turned, for Flick × the speed
     * × {@link #REACH} on the glass (at most {@link #MOST}); eased out over 150 to 300 ms of the samples' clock, the
     * pressure falling to nothing. Marks {@code pts} so the stampers narrow and dry what follows. Null when too short.
     */
    static Carry carry(StrokePoints pts, Motion m, double flick) {
        double glass = Math.min(MOST, flick * m.speed * REACH);
        if (glass < LEAST) return null;
        int last = pts.count - 1;
        double len = glass / pts.zoom, ms = 150 + 150 * Math.min(1, glass / PROTO / 220);
        int n = (int) Math.ceil(len / STEP);
        Carry c = new Carry(n, pts.tx[last], pts.ty[last]);
        double th = m.end, x = pts.x[last], y = pts.y[last], p0 = pts.p[last], ds = len / n;
        long t0 = pts.t[last];
        for (int i = 0; i < n; i++) {
            th += m.turn * ds * .85; x += Math.cos(th) * ds; y += Math.sin(th) * ds;
            double u = (i + 1.0) / n;
            // Eased out (1 − (1 − e)^2.2 of the way at e of the time): the time each place is reached.
            double e = 1 - Math.pow(1 - u, 1 / 2.2);
            c.x[i] = (float) x; c.y[i] = (float) y; c.p[i] = (float) (p0 * (1 - u)); c.t[i] = t0 + Math.round(e * ms);
        }
        pts.flickFrom = pts.total(); pts.flickLen = (float) len;
        return c;
    }

    /** A velocity over a window of samples (mischief-lab.html's velocity2): page px/ms, its speed on the glass, heading and turn (rad a page px). */
    static final class Motion { double vx, vy, speed, dir, turn, end; }
    /**
     * velocity2: from the first sample within {@code win} ms before sample {@code k} (the one before {@code k} at least)
     * to {@code k}; its turn, the heading's change from the window before, over the window's length, at most
     * {@link #TURN} a glass px.
     */
    static Motion motion(StrokePoints pts, int k, long win) {
        Motion m = new Motion();
        long lt = pts.t[k];
        int j = k;
        while (j > 0 && pts.t[j - 1] >= lt - win) j--;
        if (j == k && j > 0) j--;
        double dt = Math.max(1, lt - pts.t[j]);
        m.vx = (pts.x[k] - pts.x[j]) / dt; m.vy = (pts.y[k] - pts.y[j]) / dt;
        m.speed = Math.hypot(m.vx, m.vy) * pts.zoom; m.dir = Math.atan2(m.vy, m.vx);
        // The heading at the sample itself: over its last few glass px (a window's chord lags a curve by half its turn).
        int e = k;
        while (e > 0 && (pts.d[k] - pts.d[e]) * pts.zoom < AIM_PX) e--;
        m.end = e < k ? Math.atan2(pts.y[k] - pts.y[e], pts.x[k] - pts.x[e]) : m.dir;
        int q = j;
        while (q > 0 && pts.t[q - 1] >= pts.t[j] - win) q--;
        double len = Math.hypot(pts.x[k] - pts.x[j], pts.y[k] - pts.y[j]);
        if (q < j && len * pts.zoom > 2 * PROTO) {
            double da = m.dir - Math.atan2(pts.y[j] - pts.y[q], pts.x[j] - pts.x[q]);
            while (da > Math.PI) da -= 2 * Math.PI;
            while (da < -Math.PI) da += 2 * Math.PI;
            double most = TURN * pts.zoom;
            m.turn = clamp(da / len, -most, most);
        }
        return m;
    }
    /** peak2: the fastest the pen went (glass px/ms, over about 20 ms) in the {@code win} ms up to sample {@code k}. */
    static double peak(StrokePoints pts, int k, long win) {
        double most = 0;
        long lt = pts.t[k];
        for (int i = k, j = k; i > 0 && pts.t[i] >= lt - win; i--) {
            if (j > i) j = i;
            while (j > 0 && pts.t[i] - pts.t[j] < 20) j--;
            long dt = pts.t[i] - pts.t[j];
            if (dt > 0) most = Math.max(most, (pts.d[i] - pts.d[j]) * pts.zoom / dt);
        }
        return most;
    }

    /**
     * mischief-lab.html's fling at the stop, {@code peak} its speed before it (glass px/ms), at {@code now}: a pool a
     * little past the stop, and Fling × (1.5 + 2.2 × the speed) drops within about ±13° of its heading, further and
     * bigger the faster, seeded by the samples up to the stop.
     */
    private void fling(StrokePoints pts, double peak, long now) {
        flung = true;
        int k = anchorK;
        double a = r.fling, v = Math.min(FASTEST, peak / PROTO), dir = motion(pts, k, AIM_MS).dir, sx = pts.x[k], sy = pts.y[k];
        // The brush's half-width at the stop; the drops scale with it (the prototype's brush pen was 3.2 px), more slowly past twice that.
        double r0 = r.line ? Math.max(.6, r.pen.width(r.inkSize, pts.p[k], 0) / 2) : r.bodySize / 2;
        double grow = r0 <= 6.4 ? r0 / 3.2 : 2 * Math.sqrt(r0 / 6.4), far = PROTO / pts.zoom;
        double pool = r.line ? 1.3 * r0 : 1.05 * r0, ahead = r.line ? .5 * r0 : .3 * r0;
        int n = (int) Math.round(a * (1.5 + v * 2.2));
        Drop[] out = new Drop[n + 1];
        // Ink piles up a little where the nib stops dead.
        out[0] = new Drop(sx + Math.cos(dir) * ahead, sy + Math.sin(dir) * ahead, pool, pool, dir, now, false);
        WildBrush.Random rnd = new WildBrush.Random((int) r.pen.seed ^ SALT ^ (k + 1) * LineStamper.GOLDEN ^ (int) Math.round(pts.d[k] * 16));
        for (int i = 1; i <= n; i++) {
            double ang = dir + (rnd.next() - .5) * .45 + (rnd.next() - .5) * (rnd.next() - .5) * .6;
            double dist = (10 + v * 34) * (.35 + rnd.next() * .95) * (.6 + a * .8), rad = clamp((.8 + v * .8) * (.4 + rnd.next() * .9), .7, 6) * grow;
            double at = dist * far + ahead;
            out[i] = new Drop(sx + Math.cos(ang) * at, sy + Math.sin(ang) * at, rad * 1.5, rad, ang, now + 30 + Math.round(dist / v * 1.6), rnd.next() < .4);
        }
        // Nearest first.
        java.util.Arrays.sort(out, 1, out.length, (p, q) -> Long.compare(p.at, q.at));
        drops = out; landed = 0;
    }

    /** Whether a drop lands by {@code now} (the samples' clock; {@link Long#MAX_VALUE} at the lift, every one). */
    boolean landing(long now) { return landed < drops.length && drops[landed].at <= now; }
    /** The drops that land by {@code now}, each once, nearest first. */
    java.util.List<Drop> land(long now) {
        java.util.List<Drop> out = new java.util.ArrayList<>();
        while (landing(now)) out.add(drops[landed++]);
        return out;
    }
    /** Every drop landed so far, for a lift that lays the stroke's buffers again ({@link ModularStroke}). */
    java.util.List<Drop> landed() { return java.util.Arrays.asList(drops).subList(0, landed); }

    /**
     * Lays drop {@code d} into {@code into} at {@code k} (a line's coverage 1, a body's its amount), keeping the strongest
     * per pixel, as the stampers do: an ellipse along its flight (mischief-lab.html's ellipse2) and its speck beyond;
     * {@code wetD} (a Fast-wet body's distances, or null) takes {@code d} where it lays. Returns the box it touched.
     */
    static int[] lay(FloatTiles into, FloatTiles wetD, Drop d, double k, float dist) {
        int[] box = ellipse(into, wetD, d.x, d.y, d.along, d.across, d.angle, k, dist);
        if (d.speck) {
            double r = Math.max(.5, d.across * .35), x = d.x + Math.cos(d.angle) * d.across * 3, y = d.y + Math.sin(d.angle) * d.across * 3;
            int[] s = ellipse(into, wetD, x, y, r, r, 0, k, dist);
            if (!StrokeMath.empty(s)) box = StrokeMath.empty(box) ? s : StrokeMath.union(box, s);
        }
        return box;
    }
    private static int[] ellipse(FloatTiles into, FloatTiles wetD, double cx, double cy, double ra, double rb, double ang, double k, float dist) {
        int R = (int) Math.ceil(Math.max(ra, rb) + 1);
        int x0 = Math.max(0, (int) Math.floor(cx - R)), x1 = Math.min(into.width - 1, (int) Math.ceil(cx + R));
        int y0 = Math.max(0, (int) Math.floor(cy - R)), y1 = Math.min(into.height - 1, (int) Math.ceil(cy + R));
        int[] box = StrokeMath.emptyRect(into.width, into.height);
        if (x1 < x0 || y1 < y0) return box;
        double ca = Math.cos(ang), sa = Math.sin(ang);
        for (int py = into.up(y0), step = into.step(); py <= y1; py += step) for (int px = into.up(x0); px <= x1; px += step) {
            double dx = px + .5 - cx, dy = py + .5 - cy, al = (dx * ca + dy * sa) / ra, ac = (-dx * sa + dy * ca) / rb;
            double c = clamp((1 - Math.sqrt(al * al + ac * ac)) * rb + .5) * k;
            if (c > 0 && into.raise(px, py, (float) c) && wetD != null) wetD.set(px, py, dist);
        }
        StrokeMath.grow(box, x0, y0, x1, y1);
        return box;
    }
}
