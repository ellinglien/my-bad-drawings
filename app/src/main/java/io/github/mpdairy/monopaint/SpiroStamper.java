package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.lerp;

/**
 * Spiro (mischief-lab-2.html's Spirograph, section 3; the owner: "more like the real toy"): a curve drawn as a fine
 * antialiased line, in place of the nib and the body, from two turning phases: th, the wheel's trip round the ring, and
 * ps, its spin, turning q times as fast. ps is summed step by step, never worked out as q × th, so a setting the pen
 * changes mid-stroke bends the curve instead of making it jump.
 *
 * <p>Curves, each within its size: the toy's hypotrochoid (a wheel of the toy's teeth rolling inside a 96- or 105-tooth
 * ring, the pen in a hole a share of its radius from its centre: smooth loops, no cusps), the epitrochoid (the wheel
 * outside), a rose, a Lissajous figure, a harmonograph (the Lissajous a hair out of tune, dying away over Fade Turns)
 * and a coil (a helix round the line seen 50° from end-on, its far side lighter).
 *
 * <p>Line: the curve rides the stroke, th turning Loops per cm with its length. Ring (the toy): the stroke's first point
 * pins the ring. Its second, if it has no pressure and lies {@link #PIN} or more out ({@link #rim}), is the ring's rim:
 * the drawing screen's setup (the owner, 2026-10-26: pen down pins the centre, the drag out sizes the ring, the lift
 * sets it; DrawingPad's {@code RingSetup}) begins the stroke that draws with the centre and the rim, and nothing is
 * inked for them. The ring is then that radius at what Control drives when the pen starts round, else the brush's own
 * size. The pen's angle round the pin (nothing within {@link #PIN} of it) is th, eased over
 * {@link #EASE_MS} and only forward: a reversal under {@link #REV} of the pen's arc is a tremble that holds the wheel
 * still, past it the wheel turns back. At the lift the eased angle catches up; a stroke that never left the pin (a tap)
 * draws the whole figure, until it closes, in the ring. Oval and Bar (the owner, 2026-10-26: "on oval and rectangle"):
 * the toy's oval and bar tracks, set up corner to corner as a shape is ({@link #box}); the wheel rolls round inside the
 * track ({@link Track}) as far as the pen's angle round its centre goes along it, and the other curves are stretched to
 * its box. Control drives the shape ({@link PartDial#DRIVES}):
 * each of pressure, speed and tilt scales one of Size, Loops, Wheel, Hole and Petals by .4 to 1.3, eased over {@link
 * #EASE_HAND_MS}, a setting changed since the last point inked blended in along the turn, so it bends the line. A tilt the pen doesn't give leaves its
 * setting as set. The marks depend only on the samples and their times, so however they're batched the same line lands.
 * Pure, checked on the desktop ({@code SpiroChecks}).
 */
final class SpiroStamper {
    private SpiroStamper() { }

    /** Page px a prototype (CSS) px is. */
    static final double PROTO = FlickFling.PROTO;
    /** Page px a centimetre, on the 300 ppi panel. */
    static final double PX_CM = 300 / 2.54;
    /** The toy's wheels, teeth: {@link PartDial#WHEEL}'s values. */
    static final int[] WHEELS = {24, 30, 32, 36, 40, 42, 45, 48, 50, 52, 56, 60, 63, 64, 72, 75, 80, 84};
    /** The toy's rings, teeth: {@link PartDial#TEETH}'s options. */
    static final int[] TEETH = {96, 105};
    /** {@link PartDial#RATIO}'s options. */
    static final int[][] RATIOS = {{1, 2}, {2, 3}, {3, 4}, {3, 5}};
    /** {@link PartDial#CURVE}'s options, in order. */
    static final int SPIROGRAPH = 0, EPI = 1, ROSE = 2, LISSAJOUS = 3, HARMONOGRAPH = 4, COIL = 5;
    /** {@link PartDial#SPIRO_MODE}'s tracks (after Line and Ring). */
    static final int OVAL = 2, BAR = 3;
    /** A track set up within the pin (a tap, a finger's stroke) is this tall for its width. */
    static final double ASPECT = .6;
    /** What a reading drives ({@link PartDial#DRIVES}' options, in order). */
    static final int SIZE = 0, LOOPS = 1, WHEEL = 2, HOLE = 3, PETALS = 4, NOTHING = 5;
    /** A ring's coil is this share of it; a coil is seen this far from end-on (its sine). */
    static final double COIL_RHO = .2, COIL_SIDE = Math.sin(50 * Math.PI / 180);
    /** A ring's radius over the line's curve's half width (the prototype's 80 px and 22 px). */
    static final double RING = 80 / 22.0;
    /** Nothing nearer the pin turns the wheel; a turn back under this of the pen's arc is a tremble. Page px. */
    static final double PIN = 6 * PROTO, REV = 6 * PROTO;
    /** The pen's angle eases over this, ms; what it drives over this. */
    static final double EASE_MS = 25, EASE_HAND_MS = 80;
    /** A sample without times counts as this long after the last, ms. */
    static final double UNTIMED_MS = 8;
    /** Speed reads 0 to 1 up to this, glass px/ms (the prototype's 1.2 CSS px/ms). */
    static final double SPEED_FULL = 1.2 * PROTO;
    /** The line's tone, and a coil's far side's. */
    static final double NEAR = .92, FAR = .47;
    /** The most the pen's hole moves between two points inked, page px. */
    static final double STEP = 1.5;
    /** The line's half width at the Medium size, page px (the prototype's .8 CSS px). */
    static final double LINE_R = .8 * PROTO;
    private static final double TAU = 2 * Math.PI;

    /** A Spiro tip's settings and what its Control drives, read once from the brush. */
    static final class Settings {
        final boolean ring;
        /** {@link PartDial#SPIRO_MODE}: 0 Line, 1 Ring, {@link #OVAL}, {@link #BAR}. */
        final int mode;
        final int curve, teeth, wheel, petals, ratio;
        final double perCm, hole, phase, fades;
        /** What pressure, speed and tilt drive: {@link #SIZE} to {@link #NOTHING}. */
        final int pressure, speed, tilt;
        Settings(ModularBrush b) {
            ModularBrush.Part t = b.part(BrushPart.TIP), hand = b.part(BrushPart.HAND);
            mode = (int) t.get(PartDial.SPIRO_MODE, 0); ring = mode != 0;
            curve = (int) t.get(PartDial.CURVE, 0); teeth = TEETH[(int) t.get(PartDial.TEETH, 0)];
            wheel = (int) Math.max(0, Math.min(WHEELS.length - 1, Math.round(t.get(PartDial.WHEEL, PartDial.WHEEL.def))));
            petals = (int) Math.round(t.get(PartDial.PETALS, PartDial.PETALS.def)); ratio = (int) t.get(PartDial.RATIO, PartDial.RATIO.def);
            perCm = t.get(PartDial.LOOPS, PartDial.LOOPS.def); hole = t.get(PartDial.HOLE, PartDial.HOLE.def);
            phase = t.get(PartDial.PHASE, PartDial.PHASE.def); fades = t.get(PartDial.FADES, PartDial.FADES.def);
            pressure = (int) hand.get(PartDial.P_DRIVE, PartDial.P_DRIVE.def);
            speed = (int) hand.get(PartDial.S_DRIVE, PartDial.S_DRIVE.def);
            tilt = (int) hand.get(PartDial.T_DRIVE, PartDial.T_DRIVE.def);
        }
        /** Whether the curve is the toy's wheel in or outside the ring. */
        boolean wheels() { return curve == SPIROGRAPH || curve == EPI; }
    }

    /** The curve's shape now: q, and what the curve needs of it. */
    static final class Gear {
        int c;
        double q = 1, a, d, ph, lam, th0;
        /** An Oval's or Bar's track (its wheel's {@code a} and {@code d} then its radius and hole, of the track's longer half); null: the ring. */
        Track tr;
    }

    /**
     * An Oval's or Bar's track (the toy's oval and bar rings), its longer half 1: an ellipse, or a rectangle whose corners
     * are rounded to a quarter of its shorter side ({@link #corner}). Its wheels are sized to its tightest bend as the
     * ring's to its radius, so they always roll round it, the corners too. Sampled along its line from its right end:
     * the pen's angle round its centre finds the point there, how far along the line it is and which way it faces.
     */
    static final class Track {
        static final int N = 2048;
        final double ha, hb, length, bend;
        /**
         * Its teeth: the ring's pitch round it, rounded up to a whole 24 as a kit's are, so a wheel's whole teeth close
         * the figure within about as many laps as in the 96-tooth ring.
         */
        final int teeth;
        final double[] x = new double[N + 1], y = new double[N + 1], s = new double[N + 1], phi = new double[N + 1], n = new double[N + 1], p = new double[4];
        Track(boolean bar, double ha, double hb, int ringTeeth) {
            this.ha = ha; this.hb = hb;
            double[] o = new double[3];
            for (int i = 0; i <= N; i++) {
                outline(bar, ha, hb, i / (double) N, o);
                x[i] = o[0]; y[i] = o[1];
                n[i] = i == 0 ? o[2] : unwrap(o[2], n[i - 1]);
                phi[i] = i == 0 ? 0 : unwrap(Math.atan2(o[1], o[0]), phi[i - 1]);
                s[i] = i == 0 ? 0 : s[i - 1] + Math.hypot(x[i] - x[i - 1], y[i] - y[i - 1]);
            }
            phi[N] = n[N] = TAU;
            length = s[N];
            bend = bar ? corner(ha, hb) : Math.min(ha, hb) * Math.min(ha, hb) / Math.max(ha, hb);
            teeth = 24 * (int) Math.ceil(length * ringTeeth / (TAU * bend * 24) - 1e-9);
        }
        /** The line at the pen's angle {@code th} round the centre, any turn: x, y, how far along it (its laps counted) and its outward normal's angle (turns counted), into {@link #p}. */
        double[] at(double th) {
            double lap = Math.floor(th / TAU), f = th - lap * TAU;
            int lo = 0, hi = N;
            while (hi - lo > 1) { int m = (lo + hi) >>> 1; if (phi[m] <= f) lo = m; else hi = m; }
            double u = clamp((f - phi[lo]) / (phi[hi] - phi[lo]));
            p[0] = lerp(x[lo], x[hi], u); p[1] = lerp(y[lo], y[hi], u);
            p[2] = lap * length + lerp(s[lo], s[hi], u); p[3] = lap * TAU + lerp(n[lo], n[hi], u);
            return p;
        }
    }
    private static double unwrap(double a, double near) { return a + TAU * Math.round((near - a) / TAU); }
    /** A Bar's corners' radius: a quarter of its shorter side. */
    static double corner(double ha, double hb) { return Math.min(ha, hb) / 2; }
    static double barLength(double ha, double hb) { double c = corner(ha, hb); return 4 * (ha + hb - 2 * c) + TAU * c; }
    /**
     * The point {@code u} of the way round a track of halves {@code ha, hb} from its right end, and its outward normal's
     * angle, into {@code out}: an ellipse (u its angle's share), or a Bar (u its length's share).
     */
    static void outline(boolean bar, double ha, double hb, double u, double[] out) {
        if (!bar) { double t = TAU * u; out[0] = ha * Math.cos(t); out[1] = hb * Math.sin(t); out[2] = Math.atan2(ha * Math.sin(t), hb * Math.cos(t)); return; }
        double c = corner(ha, hb), sx = ha - c, sy = hb - c, s = u * barLength(ha, hb) + sy;
        // Side q (0 right, 1 bottom, 2 left, 3 top, then the right again), from its start, then the corner after it.
        for (int k = 0; ; k++) {
            int q = k & 3;
            double nx = q == 0 ? 1 : q == 2 ? -1 : 0, ny = q == 1 ? 1 : q == 3 ? -1 : 0, half = q % 2 == 0 ? sy : sx, far = q % 2 == 0 ? ha : hb;
            if (s <= 2 * half || k == 4) { double o = Math.min(s, 2 * half) - half; out[0] = nx * far - ny * o; out[1] = ny * far + nx * o; out[2] = k * Math.PI / 2; return; }
            s -= 2 * half;
            if (s <= c * Math.PI / 2) {
                double a = k * Math.PI / 2 + s / c, along = q % 2 == 0 ? sx : sy;
                out[0] = nx * along - ny * half + c * Math.cos(a); out[1] = ny * along + nx * half + c * Math.sin(a); out[2] = a; return;
            }
            s -= c * Math.PI / 2;
        }
    }
    /**
     * A track's centre and halves, {cx, cy, ha, hb}, page px, set up from the pin {@code px, py} to the pen at {@code x,
     * y}: a ring round the pin out to the pen, an Oval or Bar corner to corner (each half at least {@link #PIN}); within
     * the pin the brush's own radius {@code own} round it, an Oval or Bar {@link #ASPECT} as tall. The stamper and the guide both.
     */
    static double[] box(int mode, double px, double py, double x, double y, double own) {
        double rim = rim(px, py, x, y);
        if (mode < OVAL) { double r = rim > 0 ? rim : own; return new double[]{px, py, r, r}; }
        if (rim <= 0) return new double[]{px, py, own, own * ASPECT};
        return new double[]{(px + x) / 2, (py + y) / 2, Math.max(PIN, Math.abs(x - px) / 2), Math.max(PIN, Math.abs(y - py) / 2)};
    }
    /** How far {@code dx, dy} from a track's centre lies outside its line (inside: below 0), page px: the ring's or an Oval's ellipse (near enough off it), a Bar's rounded rectangle. */
    static double edge(int mode, double ha, double hb, double dx, double dy) {
        if (mode == BAR) {
            double c = corner(ha, hb), qx = Math.abs(dx) - (ha - c), qy = Math.abs(dy) - (hb - c);
            return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - c;
        }
        double k0 = Math.hypot(dx / ha, dy / hb), k1 = Math.hypot(dx / (ha * ha), dy / (hb * hb));
        return k1 > 0 ? k0 * (k0 - 1) / k1 : -Math.min(ha, hb);
    }

    /** One stroke's curve, line or body. */
    static final class State {
        /** The next sample to read; whether the lift's work is done. */
        int k;
        boolean lifted;
        /** What the pen drives, eased: Size, Loops, Wheel, Hole, Petals. */
        final double[] f = {1, 1, 1, 1, 1};
        long t;
        Gear g, was;
        double A, wasA, th, ps, th0;
        /** The pen's hole: where it last inked, and whether it has inked yet. */
        double hx, hy, htone;
        boolean fresh;
        // Ring: the pin, the farthest the pen has been from it, the pen's angle round it (raw, eased, last read) and
        // which way the wheel turns (0 until it first does); the ring's radius before Control's Size, and its rim's (0: none;
        // an Oval's or Bar's longer half), and their track (null: the ring).
        double cx, cy, far, raw, sm, lastA, base, rim;
        Track tr;
        boolean on;
        int dir;
        // Line: the normal at the last sample.
        double nx, ny;
        boolean started;
    }

    /** The ring's radius set by a rim at {@code x, y} round the pin {@code cx, cy}, page px: 0 within {@link #PIN} (the brush's own size). The stamper and the guide both. */
    static double rim(double cx, double cy, double x, double y) {
        double d = Math.hypot(x - cx, y - cy);
        return d >= PIN ? d : 0;
    }

    /** The curve's half width (Line) or its ring's radius (Ring) at size {@code size}, before Control's Size. */
    static double radius(ResolvedBrush.Pattern P, double size) { return size / 2 * (P.spiro.ring ? RING : 1); }
    /** The line's half width at size {@code size}: finer for a smaller brush, at least a pixel. */
    static double lineR(double size) { return Math.max(1, LINE_R * Math.sqrt(size / ModularSizes.SPIRO)); }
    /** How far a Spiro's marks reach across, page px: its curve, or its ring, at Control's largest Size, and the line. */
    static double reach(ResolvedBrush.Pattern P, double size) { return 2 * 1.3 * radius(P, size) + 2 * lineR(size) + 2; }

    /**
     * A Ring's guide (the prototype's light grey ring while the pen is down): the pinned ring's radius, page px, at a
     * touch-down of pressure {@code pressure} (travel, 0 to 1) and tilt {@code tiltX, tiltY} (no speed yet), as the
     * stamper starts it: the toy's ring (the epitrochoid's, inside its wheel), else how far the figure reaches. 0 along
     * the line.
     */
    static double guide(ResolvedBrush.Pattern P, float pressure, float tiltX, float tiltY) {
        if (!ring(P)) return 0;
        StrokePoints down = new StrokePoints();
        down.add(0, 0, pressure, PenSpeed.UNTIMED, tiltX, tiltY);
        State M = new State();
        drive(M, P.spiro, down, 0, Double.NaN);
        return radius(P, P.size) * M.f[SIZE] * ringShare(P.spiro, M.f);
    }
    /** Whether {@code P} is a Spiro in Ring, Oval or Bar. */
    static boolean ring(ResolvedBrush.Pattern P) { return P != null && P.spiro != null && P.spiro.ring; }
    /** The toy's ring as a share of the figure's radius: 1, but the epitrochoid's, inside its wheel. */
    static double ringShare(Settings S, double[] f) {
        // The epitrochoid's ring: R of R + 2r, its gear's a = (R + r) / (R + 2r) twice less one.
        return S.curve == EPI && S.mode < OVAL ? 2 * gear(S, f, true, 1).a - 1 : 1;
    }

    /** Whether driving {@code what} ({@link #SIZE} to {@link #NOTHING}) changes {@code tip}'s curve: Size always, the rest where its curve has them. */
    static boolean acts(ModularBrush.Part tip, int what) {
        switch (what) {
            case SIZE: return true;
            case LOOPS: return shows(tip, PartDial.LOOPS);
            // Wheel drives the toy's wheel, or the swings' ratio.
            case WHEEL: return shows(tip, PartDial.WHEEL) || shows(tip, PartDial.RATIO);
            case HOLE: return shows(tip, PartDial.HOLE);
            case PETALS: return shows(tip, PartDial.PETALS);
            default: return false;
        }
    }
    /** Whether a Spiro tip shows {@code d}: its curve's own dials, and Loops per cm along the line or for a coil. */
    static boolean shows(ModularBrush.Part tip, PartDial d) {
        if (tip.choice != PartChoice.SPIRO) return true;
        int curve = (int) tip.get(PartDial.CURVE);
        boolean wheels = curve == SPIROGRAPH || curve == EPI, swings = curve == LISSAJOUS || curve == HARMONOGRAPH;
        switch (d) {
            case LOOPS: return (int) tip.get(PartDial.SPIRO_MODE) == 0 || curve == COIL;
            case TEETH: case WHEEL: case HOLE: return wheels;
            case PETALS: return curve == ROSE;
            case RATIO: case PHASE: return swings;
            case FADES: return curve == HARMONOGRAPH;
            default: return true;
        }
    }

    /**
     * The curve from the samples not yet read on into {@code into}; {@code wetD}, if any, takes each laid pixel's
     * distance along the stroke. {@code lifting}: the pen has lifted, the lift's work (once). Returns the touched
     * rectangle.
     */
    static int[] stamp(FloatTiles into, FloatTiles wetD, ResolvedBrush.Pattern P, StrokePoints pts, LineStamper.Meta meta, boolean lifting) {
        int[] rect = StrokeMath.emptyRect(into.width, into.height);
        if (pts.count < 1) return rect;
        Settings S = P.spiro;
        double r = lineR(P.size), base = radius(P, P.size);
        State M = meta.spiro;
        if (M == null) {
            M = meta.spiro = new State();
            drive(M, S, pts, 0, Double.NaN);
            M.t = pts.t[0];
            M.cx = pts.x[0]; M.cy = pts.y[0];
            M.base = base;
            if (S.mode >= OVAL) M.tr = new Track(S.mode == BAR, 1, ASPECT, S.teeth);
            ringGear(M, S);
            M.k = 1;
        }
        for (; M.k < pts.count; M.k++) {
            if (S.ring) ring(into, wetD, P, S, pts, M, M.k, r, rect);
            else line(into, wetD, P, S, pts, M, M.k, base, r, rect);
        }
        if (lifting && !M.lifted) {
            M.lifted = true;
            if (S.ring) lift(into, wetD, P, S, pts, M, r, rect);
        }
        return rect;
    }

    /** The readings at sample {@code k} eased into what the pen drives, {@code dt} ms since the last (NaN: at once). */
    private static void drive(State M, Settings S, StrokePoints pts, int k, double dt) {
        double[] want = {1, 1, 1, 1, 1};
        scale(want, S.pressure, pts.p[k]);
        scale(want, S.speed, clamp(pts.v[k] / SPEED_FULL));
        float tx = pts.tx[k], ty = pts.ty[k];
        // A tilt the pen doesn't give leaves its setting as set.
        if (Float.isFinite(tx) && Float.isFinite(ty) && Math.abs(tx) <= 90 && Math.abs(ty) <= 90) scale(want, S.tilt, BrushDirection.tiltLean(tx, ty));
        double e = Double.isNaN(dt) ? 1 : 1 - Math.exp(-dt / EASE_HAND_MS);
        for (int i = 0; i < want.length; i++) M.f[i] += (want[i] - M.f[i]) * e;
    }
    private static void scale(double[] f, int what, double reading) { if (what >= 0 && what < NOTHING) f[what] *= .4 + .9 * reading; }
    /** Milliseconds from the last sample read to sample {@code k}, and that sample's time kept. */
    private static double since(State M, StrokePoints pts, int k) {
        long t = pts.t[k];
        if (t == PenSpeed.UNTIMED || M.t == PenSpeed.UNTIMED) { M.t = t; return UNTIMED_MS; }
        double dt = Math.max(1, t - M.t);
        M.t = t;
        return dt;
    }

    /** The curve's shape with what the pen drives now; {@code A} the ring's radius (a ring's coil winds by it). */
    static Gear gear(Settings S, double[] f, boolean ring, double A) {
        Gear g = new Gear();
        g.c = S.curve;
        switch (S.curve) {
            case SPIROGRAPH: case EPI: {
                // A wheel r rolls inside the ring R (outside: the epitrochoid), the pen d from its centre: d < r, smooth loops.
                double R = S.teeth, r = wheelTeeth(S, f), d = holeShare(S, f) * r;
                if (S.curve == SPIROGRAPH) { g.q = (R - r) / r; g.a = (R - r) / R; g.d = d / R; }
                else { double E = R + 2 * r; g.q = (R + r) / r; g.a = (R + r) / E; g.d = d / E; }
                break;
            }
            case ROSE: {
                // The rhodonea r = cos(k th): k odd has k petals, k even 2k, k = n/2 (n odd) 2n.
                int P = S.petals, n = P / 2;
                g.q = (P % 2 == 1 ? P : n % 2 == 1 ? n / 2.0 : n) * f[PETALS];
                break;
            }
            case LISSAJOUS: case HARMONOGRAPH: {
                int[] ab = RATIOS[S.ratio];
                g.q = ab[0] / (double) ab[1] * f[WHEEL] + (S.curve == HARMONOGRAPH ? .008 : 0);
                g.ph = S.phase * Math.PI / 2;
                g.lam = Math.log(20) / (S.fades * TAU);
                break;
            }
            default:
                // A ring's coil winds a whole number of turns, so the ring closes.
                g.q = ring ? Math.max(3, Math.round(S.perCm * f[LOOPS] * TAU * (1 - COIL_RHO) * A / PX_CM)) : 1;
        }
        return g;
    }
    /** The wheel's teeth with what the pen drives now, and its hole's share of its radius. */
    private static double wheelTeeth(Settings S, double[] f) { return clamp(WHEELS[S.wheel] * f[WHEEL], 12, S.curve == SPIROGRAPH ? S.teeth - 8 : 120); }
    private static double holeShare(Settings S, double[] f) { return clamp(S.hole * f[HOLE], .05, .95); }
    /**
     * The curve on the track {@code tr}: the toy's wheel rolls round inside it (the epitrochoid's outside), its teeth's
     * share of the track's; the other curves are stretched to its box.
     */
    private static void onTrack(Gear g, Settings S, double[] f, Track tr) {
        g.tr = tr;
        if (!S.wheels()) return;
        double w = wheelTeeth(S, f);
        g.a = tr.length * w / (TAU * tr.teeth); g.d = holeShare(S, f) * g.a;
        g.q = tr.teeth / w + (S.curve == EPI ? 1 : -1);
    }
    /** Whether {@code g} rolls a wheel round a track. */
    private static boolean rolls(Gear g) { return g.tr != null && (g.c == SPIROGRAPH || g.c == EPI); }
    /**
     * How far the wheel's spin turns as the pen goes on {@code step} from {@code th0} round the pin: q times as far; on a
     * track as far as it rolls along it, less its line's own turning (the epitrochoid's, more).
     */
    private static double spin(Gear g, double th0, double step) {
        if (!rolls(g)) return g.q * step;
        double[] p = g.tr.at(th0);
        double s0 = p[2], n0 = p[3];
        p = g.tr.at(th0 + step);
        return (p[2] - s0) / g.a + (g.c == EPI ? 1 : -1) * (p[3] - n0);
    }
    /** The figure's whole turn, for a tap: until it closes (the harmonograph: until it has all but died away, 3%). */
    static double span(Settings S, Gear g) {
        switch (S.curve) {
            case SPIROGRAPH: case EPI: { int r = WHEELS[S.wheel]; return TAU * r / gcd(g != null && g.tr != null ? g.tr.teeth : S.teeth, r); }
            case ROSE: { int P = S.petals; return P % 2 == 1 ? Math.PI : (P / 2) % 2 == 1 ? 4 * Math.PI : TAU; }
            case LISSAJOUS: return TAU * RATIOS[S.ratio][1];
            case HARMONOGRAPH: return Math.log(1 / .03) / g.lam;
            default: return TAU;
        }
    }
    private static int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }

    /**
     * The pen's hole at phases {@code th, ps} of a curve {@code A} across centred at {@code cx, cy} into {@code out}
     * (x, y, tone); {@code nx, ny} the line's normal, which a coil wraps round. In a ring the coil's line is the circle under it.
     * On a track the wheel's centre keeps its radius {@code a} in from the line (the epitrochoid's out), the pen's hole
     * {@code d} from it; the other curves stretched to the track's box.
     */
    static void at(Gear g, double th, double ps, double A, double cx, double cy, double nx, double ny, boolean ring, double[] out) {
        Track tr = ring ? g.tr : null;
        if (tr == null) { curve(g, th, ps, A, cx, cy, nx, ny, ring, out); return; }
        if (rolls(g)) {
            double[] p = tr.at(th);
            double side = g.c == EPI ? 1 : -1, wx = p[0] + side * g.a * Math.cos(p[3]), wy = p[1] + side * g.a * Math.sin(p[3]);
            out[0] = cx + A * (wx - side * g.d * Math.cos(ps)); out[1] = cy + A * (wy - g.d * Math.sin(ps)); out[2] = NEAR;
            return;
        }
        curve(g, th, ps, 1, 0, 0, nx, ny, true, out);
        out[0] = cx + A * tr.ha * out[0]; out[1] = cy + A * tr.hb * out[1];
    }
    private static void curve(Gear g, double th, double ps, double A, double cx, double cy, double nx, double ny, boolean ring, double[] out) {
        double tone = NEAR, x, y;
        switch (g.c) {
            case SPIROGRAPH: x = cx + A * (g.a * Math.cos(th) + g.d * Math.cos(ps)); y = cy + A * (g.a * Math.sin(th) - g.d * Math.sin(ps)); break;
            case EPI: x = cx + A * (g.a * Math.cos(th) - g.d * Math.cos(ps)); y = cy + A * (g.a * Math.sin(th) - g.d * Math.sin(ps)); break;
            case ROSE: { double r = Math.cos(ps); x = cx + A * r * Math.cos(th); y = cy + A * r * Math.sin(th); break; }
            case LISSAJOUS: x = cx + A * Math.sin(ps + g.ph); y = cy + A * Math.sin(th); break;
            case HARMONOGRAPH: { double e = A * Math.exp(-g.lam * Math.max(0, th - g.th0)); x = cx + e * Math.sin(ps + g.ph); y = cy + e * Math.sin(th); break; }
            default: {
                double rho = A, tx = ny, ty = -nx;
                if (ring) { rho = A * COIL_RHO; nx = Math.cos(th); ny = Math.sin(th); tx = -ny; ty = nx; cx += (A - rho) * nx; cy += (A - rho) * ny; }
                double c = Math.cos(ps), s = Math.sin(ps);
                x = cx + rho * (nx * c + tx * s * COIL_SIDE); y = cy + rho * (ny * c + ty * s * COIL_SIDE);
                if (s < 0) tone = FAR;
            }
        }
        out[0] = x; out[1] = y; out[2] = tone;
    }

    // ---------- Ring ----------
    /** The ring's radius and shape with what the pen drives now. */
    private static void ringGear(State M, Settings S) {
        M.A = M.base * M.f[SIZE];
        M.g = gear(S, M.f, true, M.A);
        M.g.th0 = M.th0;
        if (M.tr != null) onTrack(M.g, S, M.f, M.tr);
    }
    /** The ring as its rim set it, at what the pen drives now; else the brush's own. */
    private static void ringSet(State M, Settings S) {
        if (M.rim > 0) M.base = M.rim / (M.f[SIZE] * ringShare(S, M.f));
        ringGear(M, S);
    }
    /** The wheel set at angle {@code a}: its trip starts there, nothing inked yet. */
    private static void ringStart(State M, double a) {
        M.on = true; M.th = a; M.th0 = a; M.g.th0 = a; M.ps = M.g.q * a; M.lastA = a;
        place(M);
    }
    /** Where the pen's hole is now, nothing inked yet: the wheel's first turn inks it. */
    private static void place(State M) {
        double[] p = new double[3];
        at(M.g, M.th, M.ps, M.A, M.cx, M.cy, 0, 0, true, p);
        M.hx = p[0]; M.hy = p[1]; M.htone = p[2]; M.was = M.g; M.wasA = M.A; M.fresh = true;
    }
    /** Ring: sample {@code k}. */
    private static void ring(FloatTiles into, FloatTiles wetD, ResolvedBrush.Pattern P, Settings S, StrokePoints pts, State M, int k, double r, int[] rect) {
        // The setup's rim: no pressure, out from the pin (an Oval's or Bar's far corner, the pin its first). Nothing inked.
        if (k == 1 && pts.p[1] == 0 && (M.rim = rim(M.cx, M.cy, pts.x[1], pts.y[1])) > 0) {
            if (M.tr != null) {
                double[] b = box(S.mode, M.cx, M.cy, pts.x[1], pts.y[1], 0);
                M.cx = b[0]; M.cy = b[1]; M.rim = Math.max(b[2], b[3]);
                M.tr = new Track(S.mode == BAR, b[2] / M.rim, b[3] / M.rim, S.teeth);
            }
            return;
        }
        double dx = pts.x[k] - M.cx, dy = pts.y[k] - M.cy, dist = Math.hypot(dx, dy);
        M.far = Math.max(M.far, dist);
        if (dist < PIN) return;
        double ang = Math.atan2(dy, dx), dt = since(M, pts, k);
        drive(M, S, pts, k, dt);
        // The wheel starts where the pen first is round the pin.
        if (!M.on) { ringSet(M, S); ringStart(M, ang); M.raw = M.sm = ang; return; }
        ringGear(M, S);
        // The pen's angle unwrapped (raw), then eased (sm).
        double da = ang - M.lastA;
        while (da > Math.PI) da -= TAU;
        while (da < -Math.PI) da += TAU;
        M.lastA = ang; M.raw += da;
        M.sm += (M.raw - M.sm) * (1 - Math.exp(-dt / EASE_MS));
        // Only forward, until the pen has clearly gone the other way.
        double lead = M.sm - M.th;
        if (M.dir == 0) { place(M); if (Math.abs(lead) * dist < 1) return; M.dir = lead > 0 ? 1 : -1; }
        if (lead * M.dir < 0) { if (-lead * M.dir * dist < REV) return; M.dir = -M.dir; }
        turn(into, wetD, P, M, M.sm, pts, k, r, rect);
    }
    /** The lift: the eased angle catches up; a stroke that never left the pin draws the whole figure from the top. */
    private static void lift(FloatTiles into, FloatTiles wetD, ResolvedBrush.Pattern P, Settings S, StrokePoints pts, State M, double r, int[] rect) {
        int k = pts.count - 1;
        if (M.on && M.dir != 0 && (M.raw - M.th) * M.dir > 0) turn(into, wetD, P, M, M.raw, pts, k, r, rect);
        if (M.far < PIN) {
            ringSet(M, S);
            ringStart(M, -Math.PI / 2);
            turn(into, wetD, P, M, M.th + span(S, M.g), pts, k, r, rect);
        }
    }
    /**
     * Turns the wheel on to {@code th1} in steps of at most {@link #STEP} of the pen's hole, inking each as a line. A
     * setting the pen changed since the last point inked is blended in along the way, so it bends the line.
     */
    private static void turn(FloatTiles into, FloatTiles wetD, ResolvedBrush.Pattern P, State M, double th1, StrokePoints pts, int k, double r, int[] rect) {
        Gear g = M.g, was = M.was;
        double A = Math.max(M.wasA, M.A);
        double reach = Math.abs(th1 - M.th) * (A * (1 + Math.abs(g.q)) + 1);
        if (rolls(g)) {
            // On a track: as far as the line goes and turns, the wheel's centre and its hole with it.
            double[] p = g.tr.at(M.th);
            double s0 = p[2], n0 = p[3];
            p = g.tr.at(th1);
            double a = Math.max(g.a, was.a), d = Math.max(g.d, was.d);
            reach = A * (Math.abs(p[2] - s0) * (1 + d / a) + (a + d) * Math.abs(p[3] - n0)) + Math.abs(th1 - M.th);
        }
        int n = (int) Math.ceil(reach / STEP);
        if (n <= 0) return;
        double dk = P.dark(pts.p[k], pts.v[k]) * pts.own(k, k, 0), d = pts.d[k];
        if (M.fresh) { segment(into, wetD, M.hx, M.hy, M.hx, M.hy, r, M.htone * dk, d, rect); M.fresh = false; }
        double step = (th1 - M.th) / n;
        double[] p0 = new double[3], p1 = new double[3];
        for (int i = 1; i <= n; i++) {
            M.ps += spin(g, M.th, step); M.th += step;
            double u = i / (double) n;
            at(was, M.th, M.ps, M.wasA, M.cx, M.cy, 0, 0, true, p0);
            at(g, M.th, M.ps, M.A, M.cx, M.cy, 0, 0, true, p1);
            double x = lerp(p0[0], p1[0], u), y = lerp(p0[1], p1[1], u);
            segment(into, wetD, M.hx, M.hy, x, y, r, p1[2] * dk, d, rect);
            M.hx = x; M.hy = y; M.htone = p1[2];
        }
        M.was = g; M.wasA = M.A;
    }

    // ---------- Line ----------
    /** Line: the segment from sample {@code k − 1} to {@code k}, th turning Loops per cm with its length. */
    private static void line(FloatTiles into, FloatTiles wetD, ResolvedBrush.Pattern P, Settings S, StrokePoints pts, State M, int k, double base, double r, int[] rect) {
        int j = k - 1;
        double seg = (double) pts.d[k] - pts.d[j];
        double dt = since(M, pts, k);
        drive(M, S, pts, k, dt);
        Gear g = gear(S, M.f, false, 0);
        g.th0 = 0;   // the harmonograph dies away from each stroke's start
        double A1 = base * M.f[SIZE], rate = TAU * S.perCm * M.f[LOOPS] / PX_CM;
        double ux = pts.ux[k], uy = pts.uy[k];
        if (Float.isNaN(pts.ux[k])) { ux = seg > 0 ? (pts.x[k] - pts.x[j]) / seg : 1; uy = seg > 0 ? (pts.y[k] - pts.y[j]) / seg : 0; }
        double nx1 = -uy, ny1 = ux;
        double[] p0 = new double[3], p1 = new double[3];
        if (!M.started) {
            // The first point: the curve set at the stroke's start, inked by its first step.
            M.started = true; M.nx = nx1; M.ny = ny1; M.was = g; M.wasA = A1;
            at(g, M.th, M.ps, A1 * pts.flickWidth(pts.d[j]), pts.x[j], pts.y[j], nx1, ny1, false, p0);
            M.hx = p0[0]; M.hy = p0[1]; M.htone = p0[2]; M.fresh = true;
        }
        double dth = rate * seg, A = Math.max(A1, M.wasA);
        int n = (int) Math.ceil((dth * A * (1 + Math.abs(g.q)) + seg) / STEP);
        double dk0 = P.dark(pts.p[j], pts.v[j]), dk1 = P.dark(pts.p[k], pts.v[k]);
        if (M.fresh) { segment(into, wetD, M.hx, M.hy, M.hx, M.hy, r, M.htone * dk0 * pts.own(j, j, 0), pts.d[j], rect); M.fresh = false; }
        for (int i = 1; i <= n; i++) {
            double u = i / (double) n;
            M.th += dth / n; M.ps += g.q * dth / n;
            double cx = lerp(pts.x[j], pts.x[k], u), cy = lerp(pts.y[j], pts.y[k], u), d = lerp(pts.d[j], pts.d[k], u);
            double nx = lerp(M.nx, nx1, u), ny = lerp(M.ny, ny1, u), nl = Math.hypot(nx, ny);
            if (nl > 0) { nx /= nl; ny /= nl; } else { nx = nx1; ny = ny1; }
            double fw = pts.flickWidth(d);
            at(M.was, M.th, M.ps, M.wasA * fw, cx, cy, nx, ny, false, p0);
            at(g, M.th, M.ps, A1 * fw, cx, cy, nx, ny, false, p1);
            double x = lerp(p0[0], p1[0], u), y = lerp(p0[1], p1[1], u);
            segment(into, wetD, M.hx, M.hy, x, y, r, p1[2] * lerp(dk0, dk1, u) * pts.own(j, k, u), d, rect);
            M.hx = x; M.hy = y; M.htone = p1[2];
        }
        M.nx = nx1; M.ny = ny1; M.was = g; M.wasA = A1;
    }

    /**
     * An antialiased line from {@code x0, y0} to {@code x1, y1}, {@code r} px each side, round ends, of darkness
     * {@code tone}, raised into {@code into} (each pixel keeps its strongest), {@code wetD} taking distance {@code d}.
     */
    static void segment(FloatTiles into, FloatTiles wetD, double x0, double y0, double x1, double y1, double r, double tone, double d, int[] rect) {
        if (!(tone > 0)) return;
        int w = into.width, h = into.height;
        double R = r + 1;
        int bx0 = Math.max(0, (int) Math.floor(Math.min(x0, x1) - R)), bx1 = Math.min(w - 1, (int) Math.ceil(Math.max(x0, x1) + R));
        int by0 = Math.max(0, (int) Math.floor(Math.min(y0, y1) - R)), by1 = Math.min(h - 1, (int) Math.ceil(Math.max(y0, y1) + R));
        if (bx1 < bx0 || by1 < by0) return;
        double ex = x1 - x0, ey = y1 - y0, len2 = ex * ex + ey * ey;
        for (int py = by0; py <= by1; py++) {
            double qy = py + .5 - y0;
            for (int px = bx0; px <= bx1; px++) {
                double qx = px + .5 - x0;
                double t = len2 > 0 ? clamp((qx * ex + qy * ey) / len2) : 0, ddx = qx - t * ex, ddy = qy - t * ey;
                double c = clamp(r + .5 - Math.sqrt(ddx * ddx + ddy * ddy));
                if (c > 0 && into.raise(px, py, (float) (c * tone)) && wetD != null) wetD.set(px, py, (float) d);
            }
        }
        StrokeMath.grow(rect, bx0, by0, bx1, by1);
    }
}
