package io.github.mpdairy.monopaint;

/**
 * The Swirl cover design's marble ({@link CoverDesign#SWIRL}): the suminagashi the first covers had, as Jaffer and Lu
 * model it (the Float work's drops, docs/archive/brush-lab/prototypes/marble-and-bristle.NOTES.md), cleaned up as the approved
 * prototype has it (docs/archive/brush-lab/prototypes/cover-designs.html, "Swirl"): ink and clear drops land in turn at sites
 * spread over the bath, five columns of them, each pushing what is there out by an area-keeping map, so each site grows
 * rings in rings and the sites crowd into stones; then two comb passes of nine tines drag it and two vortices swirl it,
 * both area-keeping too. Each map has an exact inverse, so a point is found by walking back through the swirls and tines
 * and then the drops, newest first, to the drop it lies in (the white bath where none). Its recipe comes from the
 * design's seed ({@link CoverNoise.Random}); the back's from a seed of its own. Only +, ×, / and √ on doubles per point and
 * tables made once (StrictMath), so every Java gives the same tones. Pure, checked on the desktop ({@code CoverDesignChecks}).
 */
final class CoverMarble {
    /** The most drops, the comb passes (each a few tines) and the swirls a marble a cover wide takes (a wider bath, more). */
    private static final int DROPS = 140, COMBS = 2, TINES = 9, SWIRLS = 2;

    /** One marble's recipe, in units of the cover's width (the bath is {@code widths} wide and {@code widths × aspect} tall). */
    static final class Recipe {
        final double[] dx, dy, r2;
        final boolean[] ink;
        int drops;
        // A tine: a point on its line, its direction (unit), how far it drags (alpha) and how sharply it falls off (lambda).
        final int tines;
        final double[] tx, ty, tmx, tmy, ta, tl;
        // A swirl: its centre, its turn at the centre (radians) and its reach (sigma²).
        final int swirls;
        final double[] sx, sy, turn, reach;
        Recipe(int drops, int tines, int swirls) {
            dx = new double[drops]; dy = new double[drops]; r2 = new double[drops]; ink = new boolean[drops];
            this.tines = tines;
            tx = new double[tines]; ty = new double[tines]; tmx = new double[tines]; tmy = new double[tines]; ta = new double[tines]; tl = new double[tines];
            this.swirls = swirls;
            sx = new double[swirls]; sy = new double[swirls]; turn = new double[swirls]; reach = new double[swirls];
        }
    }

    /** A recipe from {@code r}, for a cover {@code aspect} tall to 1 wide (the prototype's {@code swirlParams}). */
    static Recipe recipe(CoverNoise.Random r, double aspect) { return recipe(r, aspect, 1); }
    /**
     * A recipe from {@code r} for a bath {@code widths} covers wide and {@code widths × aspect} tall (a cover's texture,
     * {@link CoverTexture}): as many sites, drops, tines and swirls a cover's area as a cover's own; one wide, the cover's.
     */
    static Recipe recipe(CoverNoise.Random r, double aspect, int widths) {
        int area = widths * widths;
        Recipe k = new Recipe(DROPS * area, COMBS * TINES * widths, SWIRLS * area);
        double tall = aspect * widths;
        // Drops at sites spread over the cover and a little past its edges, ink and clear in turn at each, every site a
        // drop a round, so each grows rings in rings and the sites crowd each other into stones.
        int columns = 5 * widths, rows = (int) Math.max(1, Math.round(5 * tall));
        int sites = columns * rows;
        double[] cx = new double[sites], cy = new double[sites], radius = new double[sites];
        int[] drops = new int[sites];
        boolean[] inkFirst = new boolean[sites];
        for (int s = 0; s < sites; s++) {
            cx[s] = -.05 + 1.1 * widths * (s % columns + .15 + .7 * r.next()) / columns;
            cy[s] = -.05 * aspect + 1.1 * tall * (s / columns + .15 + .7 * r.next()) / rows;
            radius[s] = .045 + .03 * r.next();
            drops[s] = 3 + (int) Math.floor(4 * r.next());
            inkFirst[s] = r.next() < .5;
        }
        int i = 0, most = k.dx.length;
        for (int round = 0; i < most && round < 8; round++)
            for (int n = 0; n < sites && i < most; n++) {
                int s = (n * 7 + round * 3) % sites;
                if (round >= drops[s]) continue;
                k.dx[i] = cx[s]; k.dy[i] = cy[s]; k.r2[i] = radius[s] * radius[s];
                k.ink[i] = inkFirst[s] == (round % 2 == 0);
                i++;
            }
        k.drops = i;
        // Comb passes: parallel tines across the cover, each pass a new direction, dragging one way then the other.
        int tines = TINES * widths;
        for (int c = 0, t = 0; c < COMBS; c++) {
            double angle = Math.PI * r.next(), mx = StrictMath.cos(angle), my = StrictMath.sin(angle), nx = -my, ny = mx;
            double span = tall + widths, spacing = span / (tines + 1), offset = r.next() * spacing, sign = r.next() < .5 ? 1 : -1;
            for (int j = 0; j < tines; j++, t++) {
                double along = -span / 2 + offset + j * spacing;
                k.tx[t] = widths / 2.0 + nx * along; k.ty[t] = tall / 2 + ny * along;
                k.tmx[t] = mx; k.tmy[t] = my;
                k.ta[t] = sign * (.035 + .05 * r.next()); k.tl[t] = .012 + .025 * r.next();
                sign = -sign;
            }
        }
        // Swirls: a turn or two at the centre, fading out over a reach about a third of the cover.
        for (int w = 0; w < k.swirls; w++) {
            k.sx[w] = .15 * widths + .7 * widths * r.next(); k.sy[w] = .15 * tall + .7 * tall * r.next();
            k.turn[w] = (r.next() < .5 ? -1 : 1) * (.8 + 1.2 * r.next());
            double sigma = .18 + .14 * r.next();
            k.reach[w] = sigma * sigma;
        }
        return k;
    }

    /** The bath at cover point ({@code u}, {@code v}) (cover widths): 1 clear, 0 ink; walked back through the swirls, the tines, then the drops. */
    static double tone(Recipe k, double u, double v) {
        for (int i = k.swirls - 1; i >= 0; i--) {
            double x = u - k.sx[i], y = v - k.sy[i], q = (x * x + y * y) / (2 * k.reach[i]);
            if (q >= FALLOFF_END) continue;
            // Turned back: a rotation by an angle that depends only on the distance keeps area, and turns back exactly.
            double angle = -k.turn[i] * falloff(q), c = cos(angle), s = sin(angle);
            u = k.sx[i] + x * c - y * s; v = k.sy[i] + x * s + y * c;
        }
        for (int t = k.tines - 1; t >= 0; t--) {
            // A tine drags along its line, by alpha·lambda / (d + lambda) at distance d: the distance doesn't change, so the drag is undone exactly.
            double d = Math.abs((u - k.tx[t]) * -k.tmy[t] + (v - k.ty[t]) * k.tmx[t]);
            double z = k.ta[t] * k.tl[t] / (d + k.tl[t]);
            u -= z * k.tmx[t]; v -= z * k.tmy[t];
        }
        for (int i = k.drops - 1; i >= 0; i--) {
            double x = u - k.dx[i], y = v - k.dy[i], d2 = x * x + y * y;
            if (d2 <= k.r2[i]) return k.ink[i] ? 0 : 1;
            // A drop pushed every point out from its centre, keeping area: P' = C + (P - C)·√(1 + r²/|P - C|²). Undone: √(1 - r²/|P' - C|²).
            double s = Math.sqrt(1 - k.r2[i] / d2);
            u = k.dx[i] + x * s; v = k.dy[i] + y * s;
        }
        return 1;
    }

    // Tables made once (StrictMath), so the marble is the same in every Java

    /** The swirl's falloff, e^-q for q from 0 to {@link #FALLOFF_END}, by linear steps. */
    private static final double FALLOFF_END = 12;
    private static final double[] FALLOFF = new double[4097];
    private static final double[] SINE = new double[4097];
    static {
        for (int i = 0; i < FALLOFF.length; i++) FALLOFF[i] = StrictMath.exp(-FALLOFF_END * i / (FALLOFF.length - 1));
        for (int i = 0; i < SINE.length; i++) SINE[i] = StrictMath.sin(2 * Math.PI * i / (SINE.length - 1));
    }
    private static double falloff(double q) { return table(FALLOFF, q / FALLOFF_END); }
    private static double sin(double angle) { double turns = angle / (2 * Math.PI); return table(SINE, turns - Math.floor(turns)); }
    private static double cos(double angle) { return sin(angle + Math.PI / 2); }
    /** {@code table} at {@code f} of its way along (0 to 1), between its two nearest entries. */
    private static double table(double[] table, double f) {
        double at = f * (table.length - 1);
        int i = Math.min(table.length - 2, Math.max(0, (int) at));
        double t = at - i;
        return table[i] + (table[i + 1] - table[i]) * t;
    }

    private CoverMarble() {}
}
