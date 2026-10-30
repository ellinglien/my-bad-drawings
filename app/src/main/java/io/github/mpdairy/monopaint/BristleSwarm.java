package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.lerp;
import static io.github.mpdairy.monopaint.StrokeMath.smooth;

/**
 * Bristles (engine.js 604–716, marble-and-bristle's swarm): hairs that read each other's fading trails, so they follow
 * one another, split into strands and rejoin. Each hair keeps its place across the head and its lag behind the pen;
 * per step (one per unit of pen travel) it senses a shared trail field ahead-left and ahead-right and moves toward
 * moderate trail and away from crowding (Cohesion), inside a soft wall that opens with pressure and speed (Splay), with
 * a seeded wander and a weak spring to its slot. A lone hair is grey, a tuft black. Each hair carries a load that runs
 * down with travel (faster with Dryness; further with Ink load), and a dry hair inks only where the paper's tooth is
 * under what it still holds, so the stroke breaks into dry-brush gaps as it runs out.
 *
 * <p>A big brush is the small swarm scaled up: the hairs move in units of a 10 px brush, {@code k} page pixels each.
 * Stateful (the swarm in {@link LineStamper.Meta#swarm}): fed the samples not yet walked; what the pen lays is final,
 * nothing changes at lift. A feed's hairs are worked out step by step, then laid in bands of tile rows, in order, a big
 * feed's on several cores ({@link Cores}), each keeping the strongest coverage per pixel. Powers and trigonometry are
 * StrictMath's, so the hairs walk the same paths on every device. Pure, checked on the desktop.
 *
 * <p>Where it differs from the prototype: each swarm keeps its own trail field in tiles of cells (the prototype's
 * line and body swarms shared one page-sized array); the load carries {@link #carry} times as far; the page's tilt
 * (the hand's page-tilt cells) sizes and darkens the hairs as it does every stamper's marks.
 */
final class BristleSwarm {
    /** A trail field cell, in the swarm's units. */
    static final int CELL = 2;
    /** How a trail fades with the steps since it was laid: .93 a step, as floats (the prototype's SW_PT). */
    private static final float[] FADE = new float[400];
    static { for (int k = 0; k < FADE.length; k++) FADE[k] = (float)StrictMath.pow(.93, k); }
    /** The most hairs a brush has. */
    static final int MOST_HAIRS = 32;
    /**
     * A dab's numbers in {@link Dabs}: x, y, radius, darkness, what it still holds, distance along the stroke, the pen's
     * place and the head's normal, and where the hair was the step before (x, y again for a round dab).
     */
    private static final int DAB = 12;
    /** The hair lanes' thresholds ({@link State#lanes}): how many across the head before they repeat. */
    private static final int LANES = 256;

    /** One stroke's swarm: its hairs, its trail field and how far it has walked. */
    static final class State {
        final double R, k, cs, sn, sd, gain, sat, drain;
        final int n, gw, gh;
        /** Per hair: its slot and place across the head, its sideways speed, its lag, load, thirst, radius, noise seed, where it is and how many share its line. */
        final double[] u0, u, w, a, load, thirst, rb, x, y, fx, fy, F;
        final int[] ns, company;
        /** The trail field: each cell's trail and the step it was last laid ({@link #TILE} × {@link #TILE} cells a tile). */
        private final float[][] field;
        private final int[][] last;
        private final int tilesAcross;
        /** A field cell's width in page px. */
        private final double cellSize;
        /**
         * The head's lanes, fixed across it ({@link #laneWidth} page px each): each lane's threshold, 0–1. A dry hair
         * inks a lane only while it holds more than the lane asks ({@link #laneInk}), so as the load runs out the mark
         * breaks into streaks along the stroke before it skips the paper's grain; a big head's tufts always leave a
         * few lanes bare ({@link #bare}), its own hairs' tracks.
         */
        final double[] lanes = new double[LANES];
        final double laneWidth, bare;
        final int laneSeed;
        private static final int TILE = 64;
        int step, done = 1;
        /** A feed's dabs and each worker's lanes, kept the stroke through so a feed allocates nothing once they've grown. */
        final Dabs dabs = new Dabs();
        final Lanes[] workers = new Lanes[Cores.WORKERS];
        /** A big brush's next step, px along the stroke (0 before the first). */
        double next;
        double dirx, diry;

        /**
         * The swarm of {@code b}, radius {@code radius} page px, begun at {@code x0, y0} on a page {@code width ×
         * height}, laying into a buffer with {@code step} page px between its points (a big stroke's lattice: 2).
         */
        State(ResolvedBrush.Swarm b, double radius, double x0, double y0, int width, int height, int step) {
            double Rp = Math.max(2, radius);
            k = Math.max(1, Rp / 10); R = Rp / k;
            n = (int)Math.max(6, Math.min(MOST_HAIRS, Math.round(b.hairs)));
            int seed = (((int)b.seed ^ (int)Math.round(x0 * 4)) * 16777619 ^ (int)Math.round(y0 * 4)) * 16777619;
            WildBrush.Random r = new WildBrush.Random(seed);
            u0 = new double[n]; u = new double[n]; w = new double[n]; a = new double[n]; load = new double[n]; thirst = new double[n];
            rb = new double[n]; x = new double[n]; y = new double[n]; fx = new double[n]; fy = new double[n]; F = new double[n]; ns = new int[n]; company = new int[n];
            for (int i = 0; i < n; i++) {
                u0[i] = ((i + .5) / n * 2 - 1) + (r.next() - .5) * (2.0 / n);
                u[i] = u0[i] * R * .35;
                a[i] = (.25 - StrictMath.pow(r.next(), 1.3)) * R;
                load[i] = 1;
                thirst[i] = .6 + .8 * r.next();
                rb[i] = Math.max(.55, R * .1) * (.75 + .5 * r.next());
                ns[i] = (int)(long)(r.next() * 4294967296.0);
                x[i] = x0; y[i] = y0;
            }
            cellSize = CELL * k;
            gw = (int)Math.ceil(width / cellSize); gh = (int)Math.ceil(height / cellSize);
            tilesAcross = (gw + TILE - 1) / TILE;
            field = new float[tilesAcross * ((gh + TILE - 1) / TILE)][];
            last = new int[field.length][];
            double th = lerp(.55, 1.15, b.splay);
            cs = StrictMath.cos(th); sn = StrictMath.sin(th); sd = R * .5 + 2.5;
            gain = lerp(-.9, 1.0, b.cohesion) * .6 * Math.sqrt(R / 8); sat = lerp(1.2, 4.2, b.cohesion);
            drain = drain(b.dryness, b.load);
            laneSeed = (int)b.seed ^ 0xb415;
            WildBrush.Random lr = new WildBrush.Random(laneSeed);
            for (int i = 0; i < LANES; i++) lanes[i] = lr.next();
            // A lane is at least a point and a quarter of the buffer wide, so on a big stroke's lattice no lane falls between points.
            laneWidth = Math.max(1.25 * step, .45 * k);
            bare = BARE * smooth((k - 1.5) / 3);
        }

        /** The trail at page point {@code px, py} now (swField). */
        double trail(double px, double py) {
            int gx = (int)(px / cellSize), gy = (int)(py / cellSize);
            if (gx < 0 || gy < 0 || gx >= gw || gy >= gh) return 0;
            int t = (gy / TILE) * tilesAcross + gx / TILE, c = (gy % TILE) * TILE + gx % TILE;
            float[] f = field[t];
            if (f == null) return 0;
            int age = step - last[t][c];
            return age >= FADE.length ? 0 : f[c] * (double)FADE[age];
        }
        /** Lays trail {@code amt} at page point {@code px, py} and .4 of it in the eight cells round (swDeposit). */
        void deposit(double px, double py, double amt) {
            int gx0 = (int)(px / cellSize), gy0 = (int)(py / cellSize);
            for (int gy = gy0 - 1; gy <= gy0 + 1; gy++) for (int gx = gx0 - 1; gx <= gx0 + 1; gx++) {
                if (gx < 0 || gy < 0 || gx >= gw || gy >= gh) continue;
                int t = (gy / TILE) * tilesAcross + gx / TILE, c = (gy % TILE) * TILE + gx % TILE;
                if (field[t] == null) { field[t] = new float[TILE * TILE]; last[t] = new int[TILE * TILE]; }
                float[] f = field[t];
                int age = step - last[t][c];
                double kk = gx == gx0 && gy == gy0 ? amt : amt * .4;
                f[c] = (float)((age >= FADE.length ? 0 : f[c] * (double)FADE[age]) + kk);
                last[t][c] = step;
            }
        }
    }

    /** The share of a big head's lanes that its tufts leave bare at full load. */
    static final double BARE = .08;
    /**
     * How far a lane's threshold drifts along the stroke, and over how many of the swarm's units: a bare lane opens and
     * closes again, as hairs part and rejoin, rather than running the stroke's length.
     */
    static final double LANE_DRIFT = .3, LANE_RUN = 30;

    /**
     * Whether a lane with threshold {@code q} takes ink from a hair holding {@code held} (the load with pressure and
     * speed): every lane while it holds .85 or more, none under .35, and never the head's {@code bare} share.
     */
    static boolean laneInk(double q, double held, double bare) { return q < Math.min(1 - bare, (held - .35) / .5); }

    /**
     * How many times further than the prototype a load of {@code load} (Ink load, 0–1) carries: 3 at the middle (the
     * owner: "it seems to run out of ink quickly"), from three quarters at 0 to 12 at 1.
     */
    static double carry(double load) { return 2 * StrictMath.pow(4, 2 * load - 1); }

    /**
     * About how far along a stroke a bristle brush of {@code dryness} and Ink load {@code load} lays before its hairs
     * break into streaks, page px: where a hair of middling thirst, at a steady pace, holds too little for every lane;
     * infinite for a dryness of 0, which never runs dry.
     */
    static double streaksAt(double dryness, double load) {
        double drain = drain(dryness, load);
        return drain > 0 ? .15 / drain : Double.POSITIVE_INFINITY;
    }
    /** How much of its load a hair of middling thirst gives up a page px at a slow pace: the prototype's, over {@link #carry}. */
    static double drain(double dryness, double load) { return .0009 * dryness * dryness * dryness / carry(load); }

    /**
     * The half-width the hairs keep to (the soft wall, page px, the hairs' own radius not counted) at {@code d} px
     * along the stroke, pressure travel {@code p} and speed {@code v} (px/ms), for a swarm of radius {@code radius}.
     */
    static double wall(ResolvedBrush.Swarm b, double radius, double p, double v, double d) {
        double Rp = Math.max(2, radius), k = Math.max(1, Rp / 10), R = Rp / k;
        double sv = clamp(v / 2.2), pp = clamp((p - .3) / .7), ramp = smooth(d / k / (R * 1.5));
        return R * (.42 + .38 * ramp + b.splay * (.55 * pp + .5 * sv)) * press(b, pp) * k;
    }
    /**
     * The hand's Pressure → size on the head (not the prototype's, whose swarm ignored it): a light touch gathers the
     * hairs to the tip, pressing spreads them; exactly 1 with the cell off. {@code pp}: pressure past the first touch, 0–1.
     */
    static double press(ResolvedBrush.Swarm b, double pp) { return b.pressure == 0 ? 1 : lerp(1 - .6 * b.pressure, 1 + .5 * b.pressure, pp); }
    /** The widest a hair's dab is at pressure travel {@code p}, page px: its radius pressed, with the most tilt. */
    static double hairRadius(ResolvedBrush.Swarm b, double radius, double p) {
        double Rp = Math.max(2, radius), k = Math.max(1, Rp / 10), R = Rp / k;
        return Math.max(.55, R * .1) * 1.25 * k * (.7 + .7 * clamp((p - .3) / .7)) * (1 + 1.2 * b.tiltSize) * StrokeMath.gSize(b.gSize, 1);
    }
    /**
     * How wide the swarm reaches at most, page px: its wall at full pressure and speed, a hair's radius past it each
     * side, and the overshoot the wall's springs allow.
     */
    static double reach(ResolvedBrush.Swarm b, double radius) {
        return 2 * (1.15 * wall(b, radius, 1, 2.2, Double.POSITIVE_INFINITY) + hairRadius(b, radius, 1));
    }
    /**
     * How wide the swarm lands at pressure travel {@code p} and speed {@code v}, page px: its wall where the stroke begins,
     * not yet opened (it starts small), and a hair's dab each side; the hover ring's ({@link HoverNib}).
     */
    static double landing(ResolvedBrush.Swarm b, double radius, double p, double v) { return 2 * (wall(b, radius, p, v, 0) + hairRadius(b, radius, p)); }

    /**
     * swarmStart and swarmFeed: walks the samples {@code pts} hasn't fed yet, laying each hair at each step into
     * {@code into} (wetD, if any, takes each laid pixel's distance along the stroke); {@code radius} the swarm's, page px.
     * The swarm is {@code meta}'s, begun at the stroke's first sample. Returns the touched rectangle.
     */
    static int[] stamp(FloatTiles into, FloatTiles wetD, StrokePaper paper, ResolvedBrush.Swarm b, StrokePoints pts, double radius, LineStamper.Meta meta) {
        int[] rect = StrokeMath.emptyRect(into.width, into.height);
        if (pts.count < 1) return rect;
        if (meta.swarm == null) meta.swarm = new State(b, radius, pts.x[0], pts.y[0], into.width, into.height, into.step());
        State S = meta.swarm;
        Dabs dabs = S.dabs;
        dabs.clear();
        boolean tilts = b.tiltSize != 0 || b.tDark != 0;
        for (int k = Math.max(1, S.done); k < pts.count; k++) {
            int j = k - 1;
            double seg = (double)pts.d[k] - pts.d[j];
            if (seg <= 0) continue;
            double ux = ((double)pts.x[k] - pts.x[j]) / seg, uy = ((double)pts.y[k] - pts.y[j]) / seg;
            if (S.dirx == 0 && S.diry == 0) { S.dirx = ux; S.diry = uy; }
            double lean = tilts ? BrushDirection.tiltLean(pts.tx[k], pts.ty[k]) : 0;
            // A brush of 10 px radius or less steps as the prototype: the segment in steps of at most a pixel. A bigger
            // one steps every k px of travel, wherever the samples fall (the prototype stepped at least once a sample,
            // so a big brush's hairs swirled and ran dry faster the more often the pen reported).
            int steps;
            double t0, dt;
            if (S.k == 1) { steps = (int)Math.max(1, Math.ceil(seg)); t0 = dt = 0; }
            else {
                if (S.next == 0) S.next = pts.d[j] + S.k;
                steps = (int)Math.max(0, Math.floor((pts.d[k] - S.next) / S.k) + 1);
                t0 = (S.next - pts.d[j]) / seg; dt = S.k / seg;
                S.next += steps * S.k;
            }
            for (int s = 0; s < steps; s++) {
                double t = S.k == 1 ? (s + 1.0) / steps : t0 + s * dt;
                // The heading eases toward the pen's way, so a hairpin turn swings the head instead of snapping it.
                double hx = S.dirx + (ux - S.dirx) * .2, hy = S.diry + (uy - S.diry) * .2, m = Math.hypot(hx, hy);
                if (m == 0) m = 1;
                S.dirx = hx / m; S.diry = hy / m;
                double ga = b.gSize != 0 || b.gDark != 0 ? pts.ga(j, k, t) : 0;
                step(S, b, dabs, lerp(pts.x[j], pts.x[k], t), lerp(pts.y[j], pts.y[k], t), lerp(pts.v[j], pts.v[k], t),
                        lerp(pts.p[j], pts.p[k], t), lerp(pts.d[j], pts.d[k], t), lean, ga, pts.own(j, k, t), into.width, into.height, rect);
            }
        }
        S.done = Math.max(S.done, pts.count);
        lay(into, wetD, paper, S, dabs);
        return rect;
    }

    /** swStep: one step of every hair with the pen at {@code px, py}, {@code own} of its own paint still laid (Paint Load); their dabs go to {@code dabs}. */
    private static void step(State S, ResolvedBrush.Swarm b, Dabs dabs, double px, double py, double v, double p, double d, double lean, double ga,
                             double own, int width, int height, int[] rect) {
        double R = S.R, k = S.k, spr = b.splay, dry = b.dryness;
        int N = S.n;
        double dx = S.dirx, dy = S.diry, nx = -dy, ny = dx, cs = S.cs, sn = S.sn, sd = S.sd * k, gain = S.gain, sat = S.sat;
        double sv = clamp(v / 2.2), pp = clamp((p - .3) / .7), ramp = smooth(d / k / (R * 1.5));
        double Rs = R * (.42 + .38 * ramp + spr * (.55 * pp + .5 * sv)) * press(b, pp);
        double wander = .025 * R * spr * (.4 + sv), wl = R * 2.5, root = Math.sqrt(sat);
        // Sense, every hair first, so the order they are kept in doesn't matter.
        for (int i = 0; i < N; i++) {
            double x = px + (nx * S.u[i] + dx * S.a[i]) * k, y = py + (ny * S.u[i] + dy * S.a[i]) * k;
            double sL = S.trail(x + (dx * cs - nx * sn) * sd, y + (dy * cs - ny * sn) * sd);
            double sR = S.trail(x + (dx * cs + nx * sn) * sd, y + (dy * cs + ny * sn) * sd);
            double sC = S.trail(x + dx * sd, y + dy * sd);
            double F = gain * (pull(sR, sat) - pull(sL, sat)) / root;
            if (sC > sat * 1.8) F += (sR >= sL ? -1 : 1) * Math.abs(gain) * .4;
            F += (noise(d / k / wl, S.ns[i]) - .5) * 2 * wander;
            F -= .009 * (S.u[i] - S.u0[i] * Rs);
            double over = Math.abs(S.u[i]) - Rs;
            if (over > 0) F -= .3 * over * Math.signum(S.u[i]);
            S.F[i] = F;
        }
        // A big brush's hair is laid from where it was the step before to where it is now, so its track has no beads
        // where steps lie further apart than its width (not on the first step: every hair begins at the first sample).
        boolean swept = k > 1 && S.step > 0;
        for (int i = 0; i < N; i++) {
            S.fx[i] = S.x[i]; S.fy[i] = S.y[i];
            S.w[i] = S.w[i] * .8 + S.F[i]; S.u[i] += S.w[i];
            S.x[i] = px + (nx * S.u[i] + dx * S.a[i]) * k; S.y[i] = py + (ny * S.u[i] + dy * S.a[i]) * k;
        }
        // Company: how many hairs share this one's line across the head.
        for (int i = 0; i < N; i++) {
            int c = 0;
            for (int j = 0; j < N; j++) if (Math.abs(S.u[j] - S.u[i]) < (S.rb[i] + S.rb[j]) * .8) c++;
            S.company[i] = c - 1;
        }
        double dk = StrokeMath.handDark(b.pDark, b.sDark, b.tDark, p, v, lean) * (ga != 0 ? StrokeMath.gDark(b.gDark, ga) : 1);
        if (own != 1) dk *= own;
        double grow = (.7 + .7 * pp) * (1 + 1.2 * b.tiltSize * lean) * (ga != 0 ? StrokeMath.gSize(b.gSize, ga) : 1);
        for (int i = 0; i < N; i++) {
            // Ink: runs out with travel and speed; pressing pushes a little more out. Dryness 0 never runs out and
            // skips the tooth test.
            S.load[i] = Math.max(0, S.load[i] - S.drain * k * S.thirst[i] * (1 + .6 * sv));
            double held = S.load[i] + dry * .3 * (pp - .4) - dry * dry * .2 * sv;
            double thr = dry <= 0 ? 9 : held + .1;
            if (thr <= -.05) continue;
            double dark = lerp(.42, 1, clamp(S.company[i] / 3.0)) * lerp(.72, 1, clamp(S.load[i] * 1.4)) * dk * b.amount;
            double rb = S.rb[i] * k * grow, x = S.x[i], y = S.y[i], fx = swept ? S.fx[i] : x, fy = swept ? S.fy[i] : y;
            int reach = (int)Math.ceil(rb + 1);
            int x0 = Math.max(0, (int)Math.floor(Math.min(x, fx) - reach)), x1 = Math.min(width - 1, (int)Math.ceil(Math.max(x, fx) + reach));
            int y0 = Math.max(0, (int)Math.floor(Math.min(y, fy) - reach)), y1 = Math.min(height - 1, (int)Math.ceil(Math.max(y, fy) + reach));
            dabs.add(x, y, rb, dark, thr, d, px, py, nx, ny, fx, fy);
            StrokeMath.grow(rect, x0, y0, x1, y1);
        }
        for (int i = 0; i < N; i++) S.deposit(S.x[i], S.y[i], 1);
        S.step++;
    }

    /** The sensors' response: toward moderate trail, away from crowding. */
    private static double pull(double s, double sat) { double q = s / sat; return s / (1 + q * q); }
    /** swHash: a hash of {@code i} and {@code s}, 0 to 1 in 65536ths. */
    private static double hash(int i, int s) {
        int h = i * 0x1f123bb5 ^ s;
        h ^= h >>> 16; h *= 0x45d9f3b; h ^= h >>> 16;
        return (h & 65535) / 65536.0;
    }
    /** swNoise: a smooth 1-D noise along {@code x}. */
    private static double noise(double x, int s) {
        double f = Math.floor(x);
        int i = (int)f;
        return lerp(hash(i, s), hash(i + 1, s), smooth(x - f));
    }

    /** One worker's lane thresholds for the dab it lays (a lane's drift along the stroke is the same over a dab). */
    private static final class Lanes {
        final double[] q = new double[LANES];
        final int[] at = new int[LANES];
        int dab;
        /** Lane {@code lane}'s threshold {@code along} the stroke, worked out once a dab. */
        double threshold(State S, int lane, double along) {
            if (at[lane] != dab) { at[lane] = dab; q[lane] = S.lanes[lane] + LANE_DRIFT * (noise(along, lane * 0x9e3779b1 ^ S.laneSeed) - .5); }
            return q[lane];
        }
    }

    /** A feed's hair dabs, in order (its array kept from feed to feed, grown as a feed needs). */
    private static final class Dabs {
        double[] v = new double[DAB * 64];
        int n;
        int top = Integer.MAX_VALUE, bottom = Integer.MIN_VALUE;
        long area;
        void clear() { n = 0; top = Integer.MAX_VALUE; bottom = Integer.MIN_VALUE; area = 0; }
        void add(double x, double y, double rb, double dark, double thr, double d, double cx, double cy, double nx, double ny, double fx, double fy) {
            if ((n + 1) * DAB > v.length) v = java.util.Arrays.copyOf(v, v.length * 2);
            int o = n * DAB;
            v[o] = x; v[o + 1] = y; v[o + 2] = rb; v[o + 3] = dark; v[o + 4] = thr; v[o + 5] = d;
            v[o + 6] = cx; v[o + 7] = cy; v[o + 8] = nx; v[o + 9] = ny; v[o + 10] = fx; v[o + 11] = fy;
            n++;
            double r = Math.ceil(rb + 1);
            top = Math.min(top, (int)Math.floor(Math.min(y, fy) - r)); bottom = Math.max(bottom, (int)Math.ceil(Math.max(y, fy) + r));
            area += (long)((2 * r + 1 + Math.abs(x - fx)) * (2 * r + 1 + Math.abs(y - fy)));
        }
    }

    /** Lays {@code dabs} into {@code into} in bands of its tile rows, each band its dabs in order (swHair). */
    private static void lay(FloatTiles into, FloatTiles wetD, StrokePaper paper, State S, Dabs dabs) {
        if (dabs.n == 0) return;
        int top = Math.max(0, dabs.top), bottom = Math.min(into.height - 1, dabs.bottom);
        if (bottom < top) return;
        Lanes[] lanes = S.workers;
        Cores.inBands(top, bottom, FloatTiles.SIDE << into.shift, dabs.area, (ya, yb, worker) -> {
            Lanes ln = lanes[worker];
            if (ln == null) lanes[worker] = ln = new Lanes();
            for (int o = 0; o < dabs.n * DAB; o += DAB) {
                double y = dabs.v[o + 1], rb = dabs.v[o + 2], fy = dabs.v[o + 11];
                int reach = (int)Math.ceil(rb + 1);
                int y0 = Math.max(Math.max(0, (int)Math.floor(Math.min(y, fy) - reach)), ya), y1 = Math.min(Math.min(into.height - 1, (int)Math.ceil(Math.max(y, fy) + reach)), yb);
                if (y1 >= y0) hair(into, wetD, paper, S, ln, dabs.v, o, reach, y0, y1);
            }
        });
    }

    /**
     * swHair: one hair's round dab ({@code v} from {@code o}), or a big brush's swept from where the hair was, rows
     * {@code y0..y1}; a dry one inks only the head's lanes it still holds enough for and skips the paper's high points.
     */
    private static void hair(FloatTiles into, FloatTiles wetD, StrokePaper paper, State S, Lanes ln, double[] v, int o, int reach, int y0, int y1) {
        double x = v[o], y = v[o + 1], rb = v[o + 2], dark = v[o + 3], thr = v[o + 4], d = v[o + 5], cx = v[o + 6], cy = v[o + 7], nx = v[o + 8], ny = v[o + 9];
        double fx = v[o + 10], fy = v[o + 11], sx = x - fx, sy = y - fy, ss = sx * sx + sy * sy;
        int step = into.step(), w = into.width;
        // Lanes matter only once some are bare: a dry hair past its first streaks, or a big head's tufts.
        double held = thr < 1.2 ? thr - .1 : 9;
        boolean laned = S.bare > 0 || held < .85;
        double lw = S.laneWidth, along = d / (LANE_RUN * S.k);
        ln.dab++;
        int x0 = Math.max(0, (int)Math.floor(Math.min(x, fx) - reach)), x1 = Math.min(w - 1, (int)Math.ceil(Math.max(x, fx) + reach));
        float when = (float)d;
        for (int py = into.up(y0); py <= y1; py += step) {
            double ey = py + .5 - y;
            int row = into.rowAt(py), ty = into.tileOf(py);
            for (int px = into.up(x0); px <= x1; ) {
                int tx = into.tileOf(px), end = Math.min(x1, into.tileEnd(tx));
                float[] tile = into.tile(tx, ty, false), wet = wetD != null ? wetD.tile(tx, ty, false) : null;
                for (; px <= end; px += step) {
                    double ex = px + .5 - x, ez = ey;
                    if (ss > 0) {
                        // The nearest point of the sweep: from (fx, fy) to (x, y).
                        double t = clamp(((px + .5 - fx) * sx + (py + .5 - fy) * sy) / ss);
                        ex = px + .5 - (fx + t * sx); ez = py + .5 - (fy + t * sy);
                    }
                    double cov = rb + .5 - Math.sqrt(ex * ex + ez * ez);
                    if (cov <= 0) continue;
                    if (cov > 1) cov = 1;
                    if (laned) {
                        int lane = (int)Math.floor(((px + .5 - cx) * nx + (py + .5 - cy) * ny) / lw) & (LANES - 1);
                        if (!laneInk(ln.threshold(S, lane, along), held, S.bare)) continue;
                    }
                    if (thr < 1.2) {
                        double s = (thr - (paper.fine(px, py) * .62 + paper.mid(px, py) * .38)) * 8 + .5;
                        if (s <= 0) continue;
                        if (s < 1) cov *= s * s * (3 - 2 * s);
                    }
                    int i = row | into.at(px);
                    double c = cov * dark;
                    if (c > (tile == null ? into.fill : tile[i])) {
                        if (tile == null) tile = into.tile(tx, ty, true);
                        tile[i] = (float)c;
                    }
                    if (wetD != null) {
                        if (wet == null) wet = wetD.tile(tx, ty, true);
                        wet[i] = when;
                    }
                }
            }
        }
    }

    private BristleSwarm() {}
}
