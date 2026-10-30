package io.github.mpdairy.monopaint;

/**
 * A notebook's cover design (docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md, "Covers"; the approved
 * prototype docs/archive/brush-lab/prototypes/cover-designs.html and its NOTES): nine printed patterns, each made from the
 * notebook's name with the design's own seed (FNV-1a of {@code key + ":" + name}, then SplitMix32), so the same name
 * always gives the same cover. Classic Black Marble is the default for a notebook without one (older ones); a new
 * notebook is given one of the nine by chance (the owner, 2026-10-20: "i liked all of the diff ones").
 *
 * <p>Every design but Swirl is a <em>field</em> over the cover in cover widths (u 0 to 1 across, v 0 to the cover's
 * height down): above 0 paper, below 0 ink, cut at 0 by {@link CoverPattern} with the edge spread over a pixel by its
 * slope. Swirl is a tone (1 paper, 0 ink), today's marbling ({@link CoverMarble}). Ported from the prototype exactly:
 * the same seeds, hashes and order of operations, so the covers are the prototype's. Pure, checked and timed on the
 * desktop ({@code CoverDesignChecks}).
 */
enum CoverDesign {
    CLASSIC("classic", "Classic Black Marble"),
    SPECKLE("speckle", "Fine Speckle"),
    BOLD("bold", "Bold Marble"),
    REVERSE("reverse", "Reverse Marble"),
    SWIRL("swirl", "Swirl"),
    CRACKLE("crackle", "Crackle"),
    TERRAZZO("terrazzo", "Terrazzo"),
    WOOD("wood", "Woodgrain"),
    ENGRAVED("engraved", "Engraved");

    /** Every notebook's design unless another is chosen, an old one's too. */
    static final CoverDesign DEFAULT = CLASSIC;
    /** The design's key, kept in the notebook's file (never changes) and its shown name (docs/TEXT-STYLE.md: Title Case). */
    final String key, label;
    CoverDesign(String key, String label) { this.key = key; this.label = label; }

    /** The design kept as {@code key}: the default for none, or one this version doesn't know (a later version's). */
    static CoverDesign of(String key) {
        if (key != null) for (CoverDesign d : values()) if (d.key.equals(key)) return d;
        return DEFAULT;
    }

    /** A design's pattern for one name: {@link #at} over the cover, scratch a worker's own (any 3 doubles). */
    interface Field {
        /** At ({@code u}, {@code v}), cover widths: a field (paper above 0) or, for {@link #tone}, a tone (1 paper, 0 ink). */
        double at(double u, double v, double[] scratch);
    }
    /** Whether {@link #field}'s values are tones (Swirl's) rather than a field cut at 0. */
    boolean tone() { return this == SWIRL; }

    /**
     * This design's pattern for the notebook named {@code name} on a cover {@code aspect} tall to 1 wide; the back's for
     * {@code back}. A field's back is the same field further along the printed sheet ({@link CoverPattern#BACK_SHIFT});
     * Swirl's back is a bath of its own.
     */
    Field field(String name, double aspect, boolean back) {
        CoverNoise.Random r = new CoverNoise.Random(CoverNoise.seed(key + ":" + name));
        switch (this) {
            case SWIRL: {
                CoverMarble.Recipe k = CoverMarble.recipe(back ? new CoverNoise.Random(CoverNoise.seed(key + ":" + name + ":back")) : r, aspect);
                return (u, v, s) -> CoverMarble.tone(k, u, v);
            }
            case CRACKLE: { Crackle p = new Crackle(r); return p::at; }
            case TERRAZZO: { int[] s = {r.bits(), r.bits()}; return (u, v, scratch) -> terrazzo(u, v, s); }
            case WOOD: { Wood p = new Wood(r); return (u, v, s) -> p.at(u, v); }
            case ENGRAVED: { Engraved p = new Engraved(r); return (u, v, s) -> p.at(u, v); }
            default: { Marble p = new Marble(r, this); return p::at; }
        }
    }

    /**
     * This design's pattern over a printed sheet {@code widths} covers wide and {@code widths × aspect} tall for the name
     * {@code name}: its texture's ({@link CoverTexture}). Every field is the same at any size but Swirl's bath and
     * Woodgrain's knots, which are spread over the sheet, as many a cover's area as on a cover; one wide, the cover's own.
     */
    Field sheet(String name, double aspect, int widths) {
        CoverNoise.Random r = new CoverNoise.Random(CoverNoise.seed(key + ":" + name));
        switch (this) {
            case SWIRL: { CoverMarble.Recipe k = CoverMarble.recipe(r, aspect, widths); return (u, v, s) -> CoverMarble.tone(k, u, v); }
            case WOOD: { Wood p = new Wood(r, widths, (int) Math.ceil(widths * aspect / Wood.CELL)); return (u, v, s) -> p.at(u, v); }
            default: return field(name, aspect, false);
        }
    }

    // ---------- the marbles: Classic, Fine Speckle, Bold and Reverse share one field ----------

    /**
     * Paper where either of two things says so, on a page warped twice (a broad drift, then a wobble at the cells' own
     * size): the cells (foam walls where the second-nearest cell point is nearly as near as the nearest, swelling into
     * knots where the third is too, their widths wandering so threads break and blobs swell) and the flecks (octaves of
     * noise cut at a level that itself wanders, porous where a fine grain is high). {@code scale} is cells a cover width.
     */
    private static final class Marble {
        final int[] s = new int[7];
        final double ox, oy;
        final double scale, warp, wobble, ragged, wall, wallVary, knot, knotVary, split, dense, blob, gain, level, pores, specks;
        final int octaves;
        final boolean invert;
        Marble(CoverNoise.Random r, CoverDesign d) {
            for (int i = 0; i < 7; i++) s[i] = r.bits();
            ox = r.next() * 100; oy = r.next() * 100;
            //            scale warp wobble ragged wall  wallVary knot knotVary split dense blob octaves gain level pores specks
            double[] p = d == SPECKLE ? new double[]{75, .02, .5, .06, .01, .07, .22, .2, 0, .07, .45, 3, .6, .22, .3, .1}
                    : d == BOLD ? new double[]{15, .04, .55, .07, .035, .09, .24, .18, .04, .06, .35, 6, .6, .15, .55, .07}
                    : d == REVERSE ? new double[]{30, .03, .5, .06, -.01, .09, .22, .2, 0, .06, .35, 5, .6, .2, .5, .05}
                    : new double[]{36, .03, .5, .06, .04, .09, .24, .18, .03, .06, .35, 5, .6, .15, .5, .07};
            scale = p[0]; warp = p[1]; wobble = p[2]; ragged = p[3]; wall = p[4]; wallVary = p[5]; knot = p[6]; knotVary = p[7];
            split = p[8]; dense = p[9]; blob = p[10]; octaves = (int) p[11]; gain = p[12]; level = p[13]; pores = p[14]; specks = p[15];
            invert = d == REVERSE;
        }
        double at(double u, double v, double[] c) {
            double C = scale;
            // The drift: broad, moving things up to warp of a width.
            double x = u + ox, y = v + oy;
            x += CoverNoise.fbm(x * 4, y * 4, 2, .5, s[0]) * warp; y += CoverNoise.fbm(x * 4 + 31.7, y * 4 + 11.3, 2, .5, s[0]) * warp;
            // The wobble: at the cells' own size, so no cell is a clean polygon.
            double wob = wobble / C;
            double qx = x + CoverNoise.fbm(x * C * .8, y * C * .8, 2, .5, s[1]) * wob, qy = y + CoverNoise.fbm(x * C * .8 + 9.1, y * C * .8 + 3.7, 2, .5, s[1]) * wob;
            double wander = CoverNoise.perlin(x * C * .15, y * C * .15, s[2]);   // slow: where it's dense or sparse
            double width = CoverNoise.perlin(x * C * .6, y * C * .6, s[3]);      // per few cells: thick, thin or broken walls
            double grain = CoverNoise.perlin(qx * C * 5, qy * C * 5, s[6]);      // fine: ragged edges, specks and pores
            double dense = this.dense * wander;
            // The foam: walls where the second-nearest cell point is nearly as near as the nearest, knots where the third is too.
            CoverNoise.worley(qx * C, qy * C, s[4], knot > 0 ? 2 : 1, c);
            double paper = (wall + wallVary * width + dense) - (c[1] - c[0]) + ragged * grain;
            if (knot > 0) paper = Math.max(paper, ((knot + knotVary * width + dense) - (c[2] - c[0]) + ragged * grain) * .7);
            // Finer cells splitting the bigger ones, where the slow noise lets them.
            if (split > 0) {
                CoverNoise.worley(qx * C * 2.2 + 5.5, qy * C * 2.2 + 1.7, s[4] + 1, 1, c);
                paper = Math.max(paper, ((split * (wander + .3) * 2) - (c[1] - c[0]) + ragged * grain) * .45);
            }
            // The flecks: octaves of noise cut at a level that wanders, porous where the fine grain is high.
            double f = C * blob;
            double n = CoverNoise.fbm(qx * f, qy * f, octaves, gain, s[5]);
            paper = Math.max(paper, (n - (level - 0 - 1.2 * dense) - pores * Math.max(0, grain - .25)) * 1.4);
            // Specks: a few small flecks inside the black.
            if (specks > 0) paper = Math.max(paper, (CoverNoise.perlin(qx * C * 3.1, qy * C * 3.1, s[6] + 3) - (.56 - specks - .3 * dense)) * .35);
            return invert ? -paper : paper;
        }
    }

    // ---------- Crackle: a crazed glaze ----------

    /**
     * Paper glaze with ink cracks: the walls between big cells (7 a width), wide and wandering, then finer cells' walls
     * (19 a width) inside them, thin, only where a slow noise lets them through, as crazing spreads in patches.
     */
    private static final class Crackle {
        final int[] s = new int[5];
        final double ox, oy;
        Crackle(CoverNoise.Random r) { for (int i = 0; i < 5; i++) s[i] = r.bits(); ox = r.next() * 100; oy = r.next() * 100; }
        double at(double u, double v, double[] c) {
            double x = u + ox, y = v + oy;
            x += CoverNoise.fbm(x * 3, y * 3, 2, .5, s[0]) * .02; y += CoverNoise.fbm(x * 3 + 7.7, y * 3 + 1.3, 2, .5, s[0]) * .02;
            // A wobble at the big cells' size bends their walls; a fine jitter makes them crack, not draw.
            double bx = x + CoverNoise.fbm(x * 12, y * 12, 2, .5, s[1] + 4) * .018, by = y + CoverNoise.fbm(x * 12 + 4.4, y * 12 + 2.2, 2, .5, s[1] + 4) * .018;
            double jx = CoverNoise.perlin(x * 110, y * 110, s[1]) * .001, jy = CoverNoise.perlin(x * 110 + 3.3, y * 110 + 8.8, s[1]) * .001;
            CoverNoise.worley((bx + jx) * 7, (by + jy) * 7, s[2], 1, c);
            double w1 = .014 + .01 * CoverNoise.perlin(x * 14, y * 14, s[3]);
            double paper = (c[1] - c[0]) - w1;
            CoverNoise.worley((bx + jx) * 19, (by + jy) * 19, s[4], 1, c);
            double patch = CoverNoise.perlin(x * 3.5, y * 3.5, s[3] + 77);
            double w2 = .03 * CoverNoise.smooth01((patch + .1) * 4) - .006;
            return Math.min(paper * 2.7, (c[1] - c[0]) - w2);
        }
    }

    // ---------- Terrazzo: scattered chips ----------

    /**
     * Paper chips on ink, one or none a cell of a jittered grid of cells {@code cell} wide, {@code fill} of the cells
     * having one: each the intersection of 4 to 7 half-planes round its centre, so always convex and angular. The field
     * is the nearest chip's signed distance (the largest half-plane distance), kept if more than {@code best}.
     */
    private static double chips(double x, double y, double cell, int s, double fill, double best) {
        double gx = x / cell, gy = y / cell;
        int ix = (int) Math.floor(gx), iy = (int) Math.floor(gy);
        for (int j = -1; j <= 1; j++)
            for (int i = -1; i <= 1; i++) {
                int h = CoverNoise.hash(ix + i, iy + j, s);
                if ((h & 255) / 256.0 >= fill) continue;
                int h2 = CoverNoise.hash(ix + i, iy + j, s + 1);
                double cx = (ix + i + .2 + .6 * ((h >>> 8) & 255) / 256) * cell, cy = (iy + j + .2 + .6 * ((h >>> 16) & 255) / 256) * cell;
                double size = cell * (.16 + .3 * ((h >>> 24) / 256.0) * ((h >>> 24) / 256.0) + .02);
                double dx = x - cx, dy = y - cy;
                if (dx * dx + dy * dy > 4 * size * size) continue;
                int sides = 4 + (h2 & 3), turn = (h2 >>> 2) & 255;
                double d = -9;
                for (int k = 0; k < sides; k++) {
                    int a = (int) (turn + (k * CoverNoise.DIRS / sides) + (((h2 >>> (10 + 2 * k)) & 3) - 1.5) * 9) & (CoverNoise.DIRS - 1);
                    double reach = size * (.7 + .3 * (((h2 >>> (k * 3)) & 7) / 7.0)) * (k % 2 != 0 ? 1 : .8);
                    d = Math.max(d, dx * CoverNoise.DCOS[a] + dy * CoverNoise.DSIN[a] - reach);
                }
                best = Math.max(best, -d);
            }
        return best;
    }
    /** Three sizes of chip on jittered grids (cells .052, .021 and .009 of a width; 85 %, 70 % and 35 % of cells have one). */
    private static double terrazzo(double u, double v, int[] s) {
        double paper = -9;
        paper = chips(u, v, .052, s[0], .85, paper);
        paper = chips(u + .3, v + .7, .021, s[1], .7, paper);
        paper = chips(u + .1, v + .2, .009, s[1] + 9, .35, paper);
        return paper;
    }

    // ---------- Woodgrain: a plank's lines, cut like a woodcut ----------

    /** How much a knot is squeezed down the plank. */
    private static final double KNOT_STRETCH = .3;
    /**
     * Paper lines on ink, running down the cover, 30 to 38 a width: streamlines of a slow flow down the plank, parted round
     * a knot or two as water round a stone (a cylinder's stream function, x·R²/r²), each knot an eye of rings in an ink
     * ring. Each line's width rises and falls along it, as a gouge's does.
     */
    private static final class Wood {
        final double[] kx, ky, kr;
        final int[] s = new int[3];
        final double ox, lines;
        /** The length of a plank's part with its own knot or two, cover widths (a cover's, as the prototype's). */
        static final double CELL = 1.25;
        Wood(CoverNoise.Random r) { this(r, 1, 1); }
        /** A plank {@code across} covers wide and {@code down} parts long, a knot or two in each cover-sized part. */
        Wood(CoverNoise.Random r, int across, int down) {
            double[] x = new double[2 * across * down], y = new double[x.length], radius = new double[x.length];
            int n = 0;
            for (int cy = 0; cy < down; cy++)
                for (int cx = 0; cx < across; cx++)
                    for (int i = 0, here = 1 + (int) Math.floor(r.next() * 2); i < here; i++, n++) {
                        x[n] = cx + .25 + .65 * r.next(); y[n] = cy * CELL + .15 + .95 * r.next(); radius[n] = .025 + .02 * r.next();
                    }
            kx = java.util.Arrays.copyOf(x, n); ky = java.util.Arrays.copyOf(y, n); kr = java.util.Arrays.copyOf(radius, n);
            for (int i = 0; i < 3; i++) s[i] = r.bits();
            ox = r.next() * 50; lines = 30 + 8 * r.next();
        }
        double at(double u, double v) {
            double t = u + .05 * CoverNoise.fbm(u * 1.6 + ox, v * .45, 3, .5, s[0]) + .006 * CoverNoise.perlin(u * 8, v * 2, s[1]);
            double eye = 9, ring = 0;
            for (int i = 0; i < kx.length; i++) {
                double dx = u - kx[i], dy = (v - ky[i]) * KNOT_STRETCH, r2 = dx * dx + dy * dy, R2 = kr[i] * kr[i], r = Math.sqrt(r2);
                if (r < kr[i] * .9) { eye = r / kr[i]; ring = r; }
                else t -= dx * R2 / r2;
            }
            double width = .3 + .2 * CoverNoise.perlin(u * 5, v * 7, s[2]);
            if (eye < 9) {
                // Inside an eye: rings, and an ink ring round it where the streamlines meet it.
                double rr = ring * lines * 1.4, f = rr - Math.floor(rr);
                return Math.min(.5 * width - Math.abs(f - .5), (.86 - eye) * 3);
            }
            double l = t * lines, f = l - Math.floor(l);
            return .5 * width - Math.abs(f - .5);
        }
    }

    // ---------- Engraved: a banknote's lines ----------

    /** Ink lines across the cover, 52 a width, waving gently, their weight following a slow noise from hairline to solid, as an engraver shades. */
    private static final class Engraved {
        final int[] s = new int[2];
        final double ox, oy;
        Engraved(CoverNoise.Random r) { s[0] = r.bits(); s[1] = r.bits(); ox = r.next() * 50; oy = r.next() * 50; }
        double at(double u, double v) {
            double wave = .012 * CoverNoise.perlin(u * 3 + ox, v * 3 + oy, s[0]) + .004 * CoverNoise.perlin(u * 8, v * 8, s[0] + 3);
            double lines = (v + wave) * 52, f = lines - Math.floor(lines);
            double shade = .5 + 1.1 * CoverNoise.fbm(u * 2.2 + ox, v * 2.2 + oy, 3, .5, s[1]);
            double weight = .08 + .42 * CoverNoise.smooth01(shade);
            return Math.abs(f - .5) - (.5 - weight);
        }
    }
}
