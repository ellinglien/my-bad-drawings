package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Screen and Tone (the mischief modules spec, Stage 1; docs/archive/brush-lab/prototypes/mischief-lab.html's
 * {@code buildScreen} and {@code bit1}): a threshold at every page pixel, fixed to the page, that turns a mark's darkness
 * into ink or none. A screened brush lays its mark crisp and even ({@link ResolvedBrush#screenTone}), and
 * {@link ModularCompose} writes each pixel of its mark pure black (White's white, Invert's opposite) where its tone
 * passes the threshold there, else
 * leaves the page as it found it (opaque paint lays white there). Two strokes of one screen land on the same dots, and
 * a stroke drawn again lays the same pixels. Tone lays the pattern at its Tone over the brush's fill, in black
 * dots ({@link #DOT_TONE} at {@link #DOT_COVERAGE}, so the eraser knows to scrape them, {@link EraseBothStroke}).
 * Misregister is the prototype's faint second pass, laid by the compose as the mark's line and body again at an offset
 * ({@link #misX}, {@link #misY}); Ink gain and Uneven are its second pass, worked out at each pixel from its neighbours'
 * thresholds. Pure, checked on the desktop ({@code ScreenChecks}).
 */
final class PrintScreen {
    /**
     * The Screen dial's name wherever the app shows it (its key stays {@code screen}, and its dials' {@code cell},
     * {@code sangle}, {@code mis}, {@code gain}, {@code uneven}): the owner's pick, 2026-10-19. A rename is this line
     * (and the web twins' {@code SCREEN_LABEL}, newmods.js).
     */
    static final String LABEL = "Print Shading";
    /**
     * Its short form, for its row in Brush Lab's open Medium card only ({@link LabLayout#rowLabel}): "PRINT SHADING" in
     * the row's caps takes two lines there, and Ink's seventeen dial rows have room for one. Its menu's panel says {@link #LABEL}.
     */
    static final String ROW_LABEL = "Shading";
    /**
     * Screen's ten (halftone dots, an engraver's lines, Bayer dither, grain; then rosette, Ben-Day, crosshatch, wavy lines,
     * mezzotint, diamond dots; then stipple, woodcut, scribble and hatching, whose look changes as they darken) in
     * {@link PartDial#SCREEN}'s order, and Tone's eleven patterns ({@link PartDial#PATTERN}'s order)
     * after them: new ones are appended, so every old code reads as before.
     */
    enum Kind {
        HALFTONE, LINES, DITHER, GRAIN, ROSETTE, BENDAY, CROSSHATCH, WAVY, MEZZOTINT, DIAMOND, STIPPLE, WOODCUT, SCRIBBLE, HATCHING,
        DOTS, TONE_LINES, SAND, GRADIENT, TONE_CROSS, TONE_WAVY, TONE_MEZZO, TONE_STIPPLE, TONE_WOODCUT, TONE_SCRIBBLE, TONE_HATCHING
    }
    /** A Tone dot on its layer: tone 1 (black on the panel's 16 greys) at coverage 254, which no other brush lays. */
    static final int DOT_TONE = 1, DOT_COVERAGE = 254;
    /** How strong the misregistered second pass is (the prototype's faint .45). */
    static final double GHOST = .45;

    final Kind kind;
    /** The cell, page px, the angle, degrees; Ink gain and Uneven, 0 to 1; Tone's Tone. */
    final double cell, angle, gain, uneven, level;
    /** Misregister's offset, page px (the prototype's .8 and .6 of it); 0 for none. */
    final int misX, misY;
    /** Tone: the pattern at {@link #level} over the fill, in dots. */
    final boolean tone;
    private final double ca, sa, inv;
    /** Rosette's four screens' angles (15, 75, 0 and 45 degrees at the default 45), as cosine and sine. */
    private final double[] rc = new double[4], rs = new double[4];
    /** Grain's grain, page px. */
    private final int grain;

    private PrintScreen(Kind kind, double cell, double angle, double mis, double gain, double uneven, double level) {
        this.kind = kind; this.cell = cell; this.angle = angle; this.gain = gain; this.uneven = uneven; this.level = level;
        tone = kind.ordinal() >= Kind.DOTS.ordinal();
        misX = (int) Math.round(mis * .8); misY = (int) Math.round(mis * .6);
        double a = Math.toRadians(angle);
        ca = Math.cos(a); sa = Math.sin(a); inv = 1 / Math.max(1, cell);
        grain = Math.max(1, (int) Math.round(cell / 6));
        if (kind == Kind.ROSETTE) for (int i = 0; i < 4; i++) { double r = Math.toRadians(angle + ROSETTE[i]); rc[i] = Math.cos(r); rs[i] = Math.sin(r); }
        evens = family(kind) >= 0;
        kept = keeps(kind) ? new AtomicReferenceArray<>(TILES * TILES) : null;
    }

    /** The screen {@code medium} lays through: its Screen, or Tone's pattern; null for none (every other brush). */
    static PrintScreen of(ModularBrush.Part medium) {
        double cell = medium.get(PartDial.CELL, PartDial.CELL.def), angle = medium.get(PartDial.S_ANGLE, PartDial.S_ANGLE.def);
        if (medium.choice == PartChoice.TONE)
            return new PrintScreen(Kind.values()[Kind.DOTS.ordinal() + (int) medium.get(PartDial.PATTERN)], cell, angle, 0, 0, 0, medium.get(PartDial.TTONE));
        int screen = (int) medium.get(PartDial.SCREEN, 0);
        if (screen <= 0) return null;
        return new PrintScreen(Kind.values()[screen - 1], cell, angle, medium.get(PartDial.MIS), medium.get(PartDial.S_GAIN), medium.get(PartDial.UNEVEN), 1);
    }

    /**
     * The print tone (0 to 255; {@link ToneDocument.Layer#print}) a screened mark of darkness {@code a} over white leaves
     * over print tone {@code v}: the darker of the two, so strokes over strokes at one tone print the same dots and an
     * area scribbled over fills evenly (the owner, 2026-10-21), and a darker stroke darkens it; opaque paint, covering
     * it by {@code cover} (0 to 1), moves it toward its own.
     */
    static double printed(double v, double a, double cover) {
        double t = Math.min(v, 255 * (1 - a));
        return cover > 0 ? t + (255 * (1 - a) - t) * cover : t;
    }

    /** Whether its second pass is laid at an offset (Misregister). */
    boolean misregisters() { return misX != 0 || misY != 0; }

    /** Light over dark ({@link ResolvedBrush#laysLight}): whether a light print inks white here, {@code share} of the page the pixels whose thresholds are highest (the complement of its dots). */
    boolean white(double share, int x, int y) { return share > 0 && threshold(x, y) >= 1 - share; }
    /**
     * Whether a mark of darkness {@code a} over white (0 to 1) lays ink at page pixel {@code x, y} of a page
     * {@code height} tall (Tone's gradient runs down it): mischief-lab.html's {@code bit1} and its render's
     * second pass. Ink gain swells a dot by a ragged pixel where a neighbour's threshold would ink; Uneven thins the ink
     * in soft blots and drops specks where it ran thin.
     */
    boolean ink(double a, int x, int y, int height) {
        if (!(a > 0)) return false;
        if (tone) return a * (kind == Kind.GRADIENT ? clamp(level * 2 * (y + .5) / height) : level) > threshold(x, y);
        double blot = uneven > 0 ? PaperNoise.low(x, y) / 255.0 : 0, t = a * (1 - uneven * .55 * blot);
        boolean b = t > threshold(x, y);
        if (!b && gain > 0) {
            int n = (t > threshold(x - 1, y) ? 1 : 0) + (t > threshold(x + 1, y) ? 1 : 0) + (t > threshold(x, y - 1) ? 1 : 0) + (t > threshold(x, y + 1) ? 1 : 0);
            if (n > 0 && hash(x, y, 17) < gain * (.22 + .18 * n)) b = true;
        }
        if (b && uneven > 0 && hash(x, y, 23) < uneven * (.04 + .14 * blot)) b = false;
        return b;
    }

    /**
     * The thresholds as worked out ({@link #worked}), in {@link #TILE}-px tiles of the page, NaN until asked for: the
     * four evened kinds' as floats (40 to 150 ns a pixel here, 20 times that on the Manta), Ben-Day's and Rosette's as
     * the doubles worked out, so no dot moves (10 to 30 ns, and Ink gain asks a pixel's four neighbours too); null for
     * the rest, as quick to work out as to look up (kept, they were slower: 2026-10-22). A stroke composes and previews
     * the same pixels at every present: worked out once a stroke instead. Any core may fill a pixel (the same value); a
     * tile is published whole, its NaNs set before it is seen.
     */
    private final AtomicReferenceArray<Object> kept;
    private final boolean evens;
    private static final int TILE = 32, TILES = 128;
    /** Whether {@code kind}'s thresholds are {@link #kept}. */
    private static boolean keeps(Kind kind) {
        switch (kind) {
            case ROSETTE: case BENDAY: return true;
            default: return family(kind) >= 0;
        }
    }

    /** The threshold at page pixel {@code x, y}, 0 to 1: ink where the darkness is above it (buildScreen). */
    double threshold(int x, int y) {
        return kept != null && x >= 0 && y >= 0 && x < TILE * TILES && y < TILE * TILES ? keptAt(x, y) : worked(x, y);
    }
    /** {@link #threshold} from {@link #kept}, worked out there first if it isn't yet. */
    private double keptAt(int x, int y) {
        int i = (y / TILE) * TILES + x / TILE, at = (y % TILE) * TILE + x % TILE;
        Object t = kept.get(i);
        if (t == null) {
            Object fresh;
            if (evens) { float[] f = new float[TILE * TILE]; java.util.Arrays.fill(f, Float.NaN); fresh = f; }
            else { double[] d = new double[TILE * TILE]; java.util.Arrays.fill(d, Double.NaN); fresh = d; }
            t = kept.compareAndSet(i, null, fresh) ? fresh : kept.get(i);
        }
        if (evens) {
            float[] f = (float[]) t;
            float v = f[at];
            if (v != v) f[at] = v = (float) worked(x, y);
            return v;
        }
        double[] d = (double[]) t;
        double v = d[at];
        if (v != v) d[at] = v = worked(x, y);
        return v;
    }

    /** The threshold at page pixel {@code x, y} worked out afresh (the four evened kinds' to a float's precision). */
    double worked(int x, int y) {
        switch (kind) {
            case MEZZOTINT: case TONE_MEZZO: return mezzotint(x, y);
            case STIPPLE: case TONE_STIPPLE: case WOODCUT: case TONE_WOODCUT: case SCRIBBLE: case TONE_SCRIBBLE: case HATCHING: case TONE_HATCHING:
                return (float) evened(kind, (x * ca + y * sa) * inv, (-x * sa + y * ca) * inv, cell);
            case DITHER: return (BAYER[(y & 7) << 3 | (x & 7)] + .5) / 64;
            case GRAIN: return hash(Math.floorDiv(x, grain), Math.floorDiv(y, grain), 3);
            case SAND: return hash(x, y, 5);
            default: break;
        }
        double u = (x * ca + y * sa) * inv, v = (-x * sa + y * ca) * inv;
        switch (kind) {
            // An engraver's lines, with a slight swell along them.
            case LINES: { double vv = v + .22 * sin1(u / 9); return Math.abs(vv - Math.floor(vv) - .5) * 2; }
            // Banknote waves: the same lines, swinging most of a line's pitch.
            case WAVY: case TONE_WAVY: return tri(v + .7 * sin1(u / 8));
            // Etching: one line set up to half grey, the crossing set from there to black.
            case CROSSHATCH: case TONE_CROSS: { double t1 = tri(v + .22 * sin1(u / 9)), t2 = tri(u + .22 * sin1(v / 9)); return Math.min(t1 < .5 ? t1 : 2, (1 + t2) / 2); }
            // Square dots in the screen's frame, which the default 45 degrees stands on a corner.
            case DIAMOND: { double d = 2 * Math.max(Math.abs(u - Math.floor(u) - .5), Math.abs(v - Math.floor(v) - .5)); return d * d; }
            // Four halftones at the process-colour angles, all black: a pixel inks where any of them would.
            case ROSETTE: {
                double m = 1;
                for (int i = 0; i < 4; i++) m = Math.min(m, .5 - (cos1((x * rc[i] + y * rs[i]) * inv) + cos1((-x * rs[i] + y * rc[i]) * inv)) / 4);
                double q = (1 - m) * (1 - m);
                return 1 - q * q;
            }
            // Comic Ben-Day: a round dot at every site of an offset lattice, all one size, growing with the tone.
            case BENDAY: {
                double d = benDayDistance(u / BENDAY_PITCH, v / BENDAY_PITCH);
                double[] share = BenDay.SHARE;
                return share[(int) Math.min(share.length - 1, Math.max(0, d * (share.length - 1) / BENDAY_REACH))];
            }
            case TONE_LINES: return Math.abs(v - Math.floor(v) - .5) * 2;
            // Round dots: the tone sets their size.
            default: return .5 - (cos1(u) + cos1(v)) / 4;
        }
    }

    /** Rosette's screen angles, off the Angle dial: 15, 75, 0 and 45 degrees at its default 45. */
    private static final double[] ROSETTE = {-30, 30, -45, 0};
    /**
     * Ben-Day: its sites on an offset (hexagonal) lattice {@code BENDAY_PITCH} cells apart, in the angle's frame (the
     * default 45 degrees sets the rows on the diagonal), its dots growing smoothly with the tone: a pixel inks once the
     * tone passes the share of the page its site's dot covers when it reaches it, solid just short of full darkness.
     * It stepped in a printer's six tints (10, 20, 30, 50, 70%, solid) until the owner, 2026-10-22: "pressure changes
     * with the print brushes: the ben day one, it doesn't seem to blend between, it seems to jump between shades".
     */
    private static final double BENDAY_PITCH = 1.5, BENDAY_SOLID = .997;
    /** How far from its site a pixel can be, lattice units (the hexagon's corner), and each distance's threshold: the lattice's own share within it. */
    private static final double BENDAY_REACH = 1 / Math.sqrt(3) + 1e-9;
    /** Made on Ben-Day's first use, not with the class (startup: every brush's resolve meets this class). */
    private static final class BenDay {
        static final double[] SHARE = new double[1024];
        static {
            // The share of the page within each distance of its site (so a dot reaching it covers that share).
            int[] within = new int[SHARE.length];
            int n = 256;
            double hh = Math.sqrt(3) / 2;
            for (int a = 0; a < n; a++) for (int b = 0; b < n; b++) {
                double d = benDayDistance((a + .5) / n, (b + .5) / n * 2 * hh);
                within[(int) Math.min(within.length - 1, d * (within.length - 1) / BENDAY_REACH)]++;
            }
            double sum = 0;
            for (int i = 0; i < SHARE.length; i++) {
                sum += within[i];
                SHARE[i] = BENDAY_SOLID * sum / (n * n);
            }
        }
    }
    /** The distance from {@code x, y} (lattice units) to its nearest site: rows {@code √3/2} apart, every other one shifted half a site. */
    private static double benDayDistance(double x, double y) {
        double h = Math.sqrt(3) / 2, best = 9;
        int j0 = (int) Math.floor(y / h);
        for (int j = j0; j <= j0 + 1; j++) {
            double shift = (j & 1) * .5, sx = Math.rint(x - shift) + shift, dx = x - sx, dy = y - j * h;
            best = Math.min(best, dx * dx + dy * dy);
        }
        return Math.sqrt(best);
    }
    /** A triangle wave: 0 on a line's centre, 1 midway between lines. */
    private static double tri(double t) { return Math.abs(t - Math.floor(t) - .5) * 2; }
    /** Mezzotint: dashes lying along the angle in rows a third of a cell wide, each row its own dash length and shift, each dash its own rank. */
    private double mezzotint(int x, int y) {
        double w = Math.max(1, cell / 3), px = x * ca + y * sa, py = -x * sa + y * ca;
        int j = (int) Math.floor(py / w);
        double len = cell * (.6 + 1.2 * hash(j, 0, 51));
        return hash((int) Math.floor((px + hash(j, 0, 52) * len) / len), j, 53);
    }

    // ---- The four whose look changes as they darken: a field ranked, then evened so the share inked is the darkness ----

    /** Stipple, woodcut, scribble or hatching at {@code u, v} (cells, in the angle's frame), evened ({@link #EVEN}). */
    private static double evened(Kind kind, double u, double v, double cell) {
        int k = family(kind);
        double r = raw(k, u, v, cell);
        double[] e = even(k);
        double f = r * (EVEN_BINS - 1) / RAW_MOST;
        int i = (int) Math.max(0, Math.min(EVEN_BINS - 2, Math.floor(f)));
        return e[i] + (e[i + 1] - e[i]) * Math.max(0, Math.min(1, f - i));
    }
    /** Which of the four: 0 stipple, 1 woodcut, 2 scribble, 3 hatching (a Print Shading and its Screentone twin alike); -1 for every other kind. */
    private static int family(Kind kind) {
        switch (kind) {
            case STIPPLE: case TONE_STIPPLE: return 0;
            case WOODCUT: case TONE_WOODCUT: return 1;
            case SCRIBBLE: case TONE_SCRIBBLE: return 2;
            case HATCHING: case TONE_HATCHING: return 3;
            default: return -1;
        }
    }
    /** The raw fields run 0 to about this; the even tables span it. */
    private static final double RAW_MOST = 2.5;
    private static final int EVEN_BINS = 1024;
    /** Each field's share of a page at or under each raw value, to .999 (so full darkness inks every pixel): its rank, evened. */
    private static final AtomicReferenceArray<double[]> EVEN = new AtomicReferenceArray<>(4);

    /**
     * The raw field at {@code u, v} (cells): lower inks first. Stipple: dots on a jittered lattice appear one by one in
     * their own order, small, then (all there) grow and merge. Woodcut: lines that waver, broken at first, thicken until
     * only broken white lines are left. Scribble: three looping scribbles, each laid over the last as it darkens.
     * Hatching: hatch strokes, short and a little askew, one direction, then a second and a third, then each again
     * between its rows.
     */
    static double raw(int family, double u, double v, double cell) {
        switch (family) {
            case 0: {
                // Dots a cell apart; a dot's own size, at least a pixel and a half across.
                double r0 = Math.max(.75, cell * .2) / cell, best = 9;
                int i0 = (int) Math.floor(u - .5), j0 = (int) Math.floor(v - .5);
                for (int i = i0; i <= i0 + 1; i++) for (int j = j0; j <= j0 + 1; j++) {
                    int h = mix(i, j, 61);
                    double sx = i + .5 + (unit(h) - .5) * .6, sy = j + .5 + (unit(h * 0x9e3779b1) - .5) * .6;
                    double d = Math.sqrt((u - sx) * (u - sx) + (v - sy) * (v - sy));
                    best = Math.min(best, .9 * unit(h * 0x85ebca6b) + 1.6 * Math.max(0, d - r0));
                }
                return best;
            }
            case 1: {
                // A line a cell; its wander and breaks its own.
                int j = (int) Math.floor(v);
                double wav = 0;
                for (int pass = 0; pass < 2; pass++) {
                    int h = mix(j, 0, 71);
                    wav = .2 * sin1(u * .05 + unit(h)) + .07 * sin1(u * .17 + unit(h * 0x85ebca6b));
                    int jj = (int) Math.floor(v + wav);
                    if (jj == j) break;
                    j = jj;
                }
                int h = mix(j, 0, 73);
                double b = .5 + .3 * sin1(u * .06 + unit(h)) + .2 * sin1(u * .19 + unit(h * 0x9e3779b1));
                return tri(v + wav) + .55 * (b - .5) + .3;
            }
            case 2: {
                // Three scribbles: each the loops where a smooth field crosses its levels, as far off them as the field's
                // slope says (so a loop is one width all round, not a blot where the field lies flat).
                double best = 9;
                for (int l = 0; l < 3; l++) {
                    double uu = l == 1 ? v : u, vv = l == 1 ? -u : v, ph = l * .37, a = .07 + .015 * l, tau = 2 * Math.PI;
                    // F and its slope, the chain rule through each wave (cos1 is the slope of sin1, over 2π).
                    double p1 = a * 1.3 * vv + ph, A = a * uu + .45 * sin1(p1), p2 = a * .9 * uu + 2 * ph, B = a * 1.1 * vv + .45 * sin1(p2), C = a * .7 * (uu + vv) + ph * 3;
                    double cA = tau * cos1(A), cB = tau * cos1(B), cC = .6 * tau * cos1(C) * a * .7;
                    double F = sin1(A) + sin1(B) + .6 * sin1(C);
                    double fu = cA * a + cB * .45 * tau * cos1(p2) * a * .9 + cC, fv = cA * .45 * tau * cos1(p1) * a * 1.3 + cB * a * 1.1 + cC;
                    double k = F * SCRIBBLE_K, off = Math.abs(k - Math.rint(k)) / Math.max(.12, SCRIBBLE_K * Math.sqrt(fu * fu + fv * fv));
                    best = Math.min(best, SCRIBBLE_AT[l] + 1.8 * off);
                }
                return best;
            }
            default: {
                double best = 9;
                // The sets in order of where they start: one that starts past the best so far can't be nearer.
                for (int s = 0; s < 6 && HATCH_AT[s] < best; s++) {
                    int L = s % 3, odd = s / 3;
                    double c = TURN_COS[L], sn = TURN_SIN[L];
                    double hu = u * c + v * sn, hv = -u * sn + v * c;
                    // Rows two cells apart, the odd ones between them.
                    double t = (hv - odd) / 2;
                    int k = (int) Math.floor(t + .5);
                    int row = mix(k, s, 83);
                    double seg = 6, shift = unit(row) * seg;
                    int m = (int) Math.floor((hu + shift) / seg);
                    int h = mix(row, m, 89);
                    double mid = (m + .5) * seg - shift + (unit(h) - .5) * 1.2, x = hu - mid, half = seg * (.36 + .1 * unit(h * 0x27d4eb2d));
                    double centre = 2 * k + odd + (unit(h * 0x9e3779b1) - .5) * .5 + (unit(h * 0x85ebca6b) - .5) * .16 * x;
                    // Thinner toward its ends, as a flicked stroke.
                    double e = Math.abs(x) / half, d = Math.abs(hv - centre) * (1 + 2 * Math.max(0, e - .55)) + 2 * Math.max(0, Math.abs(x) - half);
                    best = Math.min(best, HATCH_AT[s] + 1.4 * d);
                }
                return best;
            }
        }
    }
    /** How many of a scribble's field's levels a unit holds. */
    private static final double SCRIBBLE_K = .55;
    /** Hatching's three directions off the angle (Hatch's turns), as cosine and sine. */
    private static final double[] TURN_COS = new double[3], TURN_SIN = new double[3];
    static { for (int L = 0; L < 3; L++) { TURN_COS[L] = Math.cos(Math.toRadians(HatchStamper.TURN[L])); TURN_SIN[L] = Math.sin(Math.toRadians(HatchStamper.TURN[L])); } }
    /** Where each scribble starts, and each hatching set (each direction, then each between its rows). */
    private static final double[] SCRIBBLE_AT = {0, .45, .8}, HATCH_AT = {0, .3, .55, .8, .95, 1.1};
    private static int mix(int a, int b, int c) {
        int h = a * 0x27d4eb2d ^ b * 0x165667b1 ^ c * 0x9e3779b1;
        h = (h ^ (h >>> 15)) * 0x85ebca6b; h = (h ^ (h >>> 13)) * 0xc2b2ae35;
        return h ^ (h >>> 16);
    }
    private static double unit(int h) { int g = (h ^ (h >>> 16)) * 0x45d9f3b; g ^= g >>> 16; return (g & 0xffffffffL) / 4294967296.0; }
    /**
     * Field {@code k}'s even table, from a page of its default size (Pattern Size 6) at 45 degrees: made on its first use,
     * not with the class (over 20 s for the four on the Manta, and every brush's resolve meets this class at startup).
     */
    private static double[] even(int k) {
        double[] made = EVEN.get(k);
        if (made != null) return made;
        synchronized (EVEN) {
            if ((made = EVEN.get(k)) != null) return made;
            int n = 384;
            double cell = PartDial.CELL.def, inv = 1 / cell, ca = Math.cos(Math.PI / 4), sa = ca;
            int[] count = new int[EVEN_BINS];
            for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
                double r = raw(k, (x * ca + y * sa) * inv, (-x * sa + y * ca) * inv, cell);
                count[(int) Math.max(0, Math.min(EVEN_BINS - 1, Math.round(r * (EVEN_BINS - 1) / RAW_MOST)))]++;
            }
            double[] e = new double[EVEN_BINS];
            double sum = 0;
            for (int i = 0; i < EVEN_BINS; i++) { sum += count[i]; e[i] = .998 * (sum - count[i] / 2.0) / (n * n) + 1e-6 * i; }
            EVEN.set(k, e);
            return e;
        }
    }

    /** {@code cos(2πt)} from a table: the screen needs its shape, not its last decimal, and two a pixel must be cheap. */
    private static double cos1(double t) { return COS[(int) ((t - Math.floor(t)) * TABLE) & (TABLE - 1)]; }
    private static double sin1(double t) { return cos1(t - .25); }
    private static final int TABLE = 4096;
    private static final double[] COS = new double[TABLE];
    /** The 8 × 8 Bayer matrix, row by row (mischief-lab.html's bayer(8)). */
    private static final int[] BAYER = new int[64];
    static {
        for (int i = 0; i < TABLE; i++) COS[i] = Math.cos(2 * Math.PI * i / TABLE);
        int[][] m = {{0}};
        while (m.length < 8) {
            int k = m.length;
            int[][] q = {{0, 2}, {3, 1}}, out = new int[2 * k][2 * k];
            for (int y = 0; y < 2 * k; y++) for (int x = 0; x < 2 * k; x++) out[y][x] = 4 * m[y % k][x % k] + q[y / k][x / k];
            m = out;
        }
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) BAYER[y * 8 + x] = m[y][x];
    }
    /** mischief-lab.html's hash: page pixel {@code x, y} and salt {@code s}, 0 to 1. */
    static double hash(int x, int y, int s) {
        int h = x * 0x27d4eb2d ^ y * 0x165667b1 ^ s * 0x9e3779b1;
        h = (h ^ (h >>> 15)) * 0x85ebca6b; h = (h ^ (h >>> 13)) * 0xc2b2ae35;
        return ((h ^ (h >>> 16)) & 0xffffffffL) / 4294967296.0;
    }
}
