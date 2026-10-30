package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.blur;
import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.lerp;
import static io.github.mpdairy.monopaint.StrokeMath.smooth;

/**
 * The settle and the line-and-wash blend (engine.js 723–775, stroke.js 6–74): paint bleeds, pools at the rim, blooms,
 * backruns and granulates, cover fades the wet effects, impasto lights its ridges from the upper left; where a line
 * meets wet wash at lift, the line loosens into it. The settle works in bands of rows, each read with enough rows
 * above and below for every blur it runs, so a band's rows equal the one-piece result and a stroke never holds more
 * than a band's worth of scratch arrays, reused from band to band and, through the stroke's {@link StrokeMath.Scratch},
 * from settle to settle. Pure, checked on the desktop.
 */
final class BodySettle {
    /** Rows a band writes at most. */
    static final int BAND = 256;

    /** The scratch slots a band uses: the rows read, a blur's result, the blurs' rows, the settled rows, impasto's heights, the blend's half-size line, a row of relief and the pull's rows of line and body. */
    private static final int P_SLOT = 0, BLUR = 1, TMP = 2, OUT = 3, HEIGHTS = 4, DOWN = 5, LIT = 6, PULL_ROWS = 7;

    /**
     * A step run on the copied rows before the settle: {@code p} is {@code w × h}, its top left at page {@code x0, y0};
     * it may use {@code scratch}'s {@link #BLUR}, {@link #TMP} and {@link #DOWN} slots.
     */
    interface Prep { void apply(float[] p, int x0, int y0, int w, int h, StrokeMath.Scratch scratch); }

    /**
     * settleBody: reads src over rect (padded), writes dst (0–1.6) and, with impasto, relief (none when null), its
     * arrays from {@code scratch}. Returns the region covered.
     */
    static int[] settle(FloatTiles src, FloatTiles dst, FloatTiles relief, StrokePaper paper, ResolvedBrush.Look look, int[] rect, double size, Prep prep, int extraPad, StrokeMath.Scratch scratch) {
        return settle(src, dst, relief, paper, look, rect, size, prep, extraPad, null, BAND, scratch);
    }

    /**
     * The same, in bands of {@code band} rows (package-private, for the check). src and dst must differ. A prep reads
     * no farther than {@code extraPad + 5} rows from a row it changes (the blend's diffuse and line-pull blurs do so),
     * and lays paint only within that of src's or {@code also}'s (null for none). Only the parts of a band within
     * every blur's reach of src's or also's tiles are settled (a long diagonal stroke's rectangle is mostly bare);
     * dst is cleared over the rest, which settles to nothing.
     */
    static int[] settle(FloatTiles src, FloatTiles dst, FloatTiles relief, StrokePaper paper, ResolvedBrush.Look look, int[] rect, double size, Prep prep, int extraPad, FloatTiles also, int band, StrokeMath.Scratch scratch) {
        Bands work = bands(src, dst, relief, paper, look, rect, size, prep, extraPad, also, band, scratch);
        if (work == null) return rect;
        work.settle(work.count);
        return work.region;
    }
    /** {@link #settle}'s bands, none settled yet ({@link Bands#settle}); null for a region with no rows. */
    static Bands bands(FloatTiles src, FloatTiles dst, FloatTiles relief, StrokePaper paper, ResolvedBrush.Look look, int[] rect, double size, Prep prep, int extraPad, FloatTiles also, int band, StrokeMath.Scratch scratch) {
        int w = src.width, h = src.height;
        double K = StrokeMath.paintBig(size), KN = K * K;
        double wet = 1 - clamp(look.cover), feather = look.feather * wet, hollow = look.hollow * wet, bloom = look.bloom * wet, backrun = look.backrun * wet;
        int fr = (int)Math.round((2 + feather * 12) * K), pad = fr + 8 + extraPad;
        int X0 = Math.max(0, rect[0] - pad), Y0 = Math.max(0, rect[1] - pad), X1 = Math.min(w - 1, rect[2] + pad), Y1 = Math.min(h - 1, rect[3] + pad);
        // The region's lattice points (every pixel, or a big stroke's even ones): x0, y0 the first, rw × rh of them.
        int sh = src.shift, x0 = src.up(X0) >> sh, y0 = src.up(Y0) >> sh, rw = (X1 >> sh) - x0 + 1, rh = (Y1 >> sh) - y0 + 1;
        if (rw <= 0 || rh <= 0) return null;
        int rimR = (int)Math.round((3 + hollow * 5) * K), rimR2 = (int)Math.round(2 * K);
        boolean impasto = look.impasto > 0 && relief != null;
        int pileR = Math.max(2, (int)Math.round(3 * K));
        // The radii on the lattice.
        fr = src.radius(fr); rimR = src.radius(rimR); rimR2 = src.radius(rimR2); pileR = src.radius(pileR);
        if (extraPad > 0) extraPad = src.radius(extraPad);
        // Rows a band reads past its own: every blur's reach on both sides of the step it feeds, and the prep's.
        int over = 2 * ((feather > 0 ? fr : 0) + (hollow > 0 ? rimR + rimR2 : 0) + (impasto ? pileR : 0)) + extraPad + 8;
        Settle s = new Settle(paper, look, K, KN, feather, hollow, bloom, backrun, fr, rimR, rimR2, pileR, w, h, sh, scratch);
        // Bands end on lattice rows that are multiples of band, so no two share a tile row of dst or relief: they are
        // independent (a band's rows equal the one-piece result) and may run at once, on other cores.
        int bands = 0;
        int[] tops = new int[rh / Math.max(1, band) + 3];
        for (int top = 0; top < rh; top = band <= 0 ? rh : ((y0 + top) / band + 1) * band - y0) tops[bands++] = top;
        tops[bands] = rh;
        return new Bands(s, src, dst, relief, impasto, prep, also, x0, y0, rw, rh, over, tops, bands, band % FloatTiles.SIDE == 0, new int[]{X0, Y0, X1, Y1});
    }

    /**
     * One settle's bands, shared out over the cores ({@link Cores}), each worker with its own scratch; settled all at
     * once, or a few at a time down the region (a fill's strips, {@link ModularStroke#fill}), src laid through the rows
     * they read ({@link #reads}) before.
     */
    static final class Bands {
        final Settle s; final FloatTiles src, dst, relief, also; final boolean impasto; final Prep prep;
        final int x0, y0, rw, rh, over, tx0, ty0, tw, th; final int[] tops;
        /** How many bands, and how many are settled. */
        final int count;
        private int settled;
        /** The page pixels the settle covers (the rectangle padded by the blurs' reach), inclusive. */
        final int[] region;
        /** Whether bands end on tile rows, so they may run at once. */
        final boolean apart;
        /**
         * Which of the region's tiles hold paint, src's or also's: the bands settle only within reach of them. Looked at
         * a tile row at a time as the bands come to read it ({@link #known} so far).
         */
        private final boolean[] laid;
        private int known;
        private final Settle[] own = new Settle[Cores.WORKERS];
        private final boolean[][] near = new boolean[Cores.WORKERS][];

        Bands(Settle s, FloatTiles src, FloatTiles dst, FloatTiles relief, boolean impasto, Prep prep, FloatTiles also, int x0, int y0, int rw, int rh, int over,
              int[] tops, int count, boolean apart, int[] region) {
            this.apart = apart; this.region = region;
            this.s = s; this.src = src; this.dst = dst; this.relief = relief; this.impasto = impasto; this.prep = prep; this.also = also; this.x0 = x0; this.y0 = y0;
            this.rw = rw; this.rh = rh; this.over = over; this.tops = tops; this.count = count;
            tx0 = x0 >> FloatTiles.SHIFT; ty0 = y0 >> FloatTiles.SHIFT;
            tw = ((x0 + rw - 1) >> FloatTiles.SHIFT) - tx0 + 1; th = ((y0 + rh - 1) >> FloatTiles.SHIFT) - ty0 + 1;
            laid = new boolean[tw * th];
        }

        /** Band {@code k}'s first lattice row ({@code k} = {@link #count}: past the last). */
        int top(int k) { return y0 + tops[k]; }
        /** The lattice row before which the bands before {@code k} read src (and the prep its rows): src laid through it, they may settle. */
        int reads(int k) { return y0 + Math.min(rh, tops[k] + over); }
        /** The first lattice row band {@code k} reads ({@code k} = {@link #count}: past the last). */
        int from(int k) { return k == count ? y0 + rh : y0 + (Math.max(0, tops[k] - over) & ~1); }

        /** Settles the bands before {@code k} not settled yet, sharing them out when they may run at once. */
        void settle(int k) {
            int k0 = settled;
            if (k <= k0) return;
            settled = k;
            // The tiles with paint in the rows these bands read.
            for (int last = ((reads(k) - 1) >> FloatTiles.SHIFT) - ty0; known <= last; known++)
                for (int tx = 0; tx < tw; tx++) laid[known * tw + tx] = src.tile(tx + tx0, known + ty0, false) != null || also != null && also.tile(tx + tx0, known + ty0, false) != null;
            Cores.Piece piece = (i, worker) -> {
                if (own[worker] == null) { own[worker] = worker == 0 ? s : s.with(new StrokeMath.Scratch()); near[worker] = new boolean[tw]; }
                band(own[worker], tops[k0 + i], tops[k0 + i + 1], near[worker]);
            };
            if (apart) Cores.each(k - k0, piece);
            else for (int i = 0; i < k - k0; i++) piece.run(i, 0);
        }

        /** Settles rows top..bottom−1 of the region. */
        private void band(Settle s, int top, int bottom, boolean[] near) {
            // Reading from an even row keeps the blend's half-size grid where the one-piece settle has it.
            int from = Math.max(0, top - over) & ~1, to = Math.min(rh, bottom + over);
            // The tile columns with paint within the rows this band reads, and the column runs within reach of them.
            java.util.Arrays.fill(near, false);
            for (int ty = (y0 + from) >> FloatTiles.SHIFT; ty <= (y0 + to - 1) >> FloatTiles.SHIFT; ty++)
                for (int tx = 0; tx < tw; tx++) if (laid[(ty - ty0) * tw + tx]) near[tx] = true;
            // Runs of the band's columns within reach of a tile with paint, merged where they meet; dst cleared between.
            int a = -1, b = -1, done = 0;
            for (int tx = 0; tx < tw; tx++) {
                if (!near[tx]) continue;
                // From an even column, as the rows: the blend's half-size grid stays where the whole region has it.
                int ra = Math.max(0, ((tx + tx0) << FloatTiles.SHIFT) - over - x0) & ~1, rb = Math.min(rw, ((tx + tx0 + 1) << FloatTiles.SHIFT) + over - x0);
                if (a >= 0 && ra <= b) { b = Math.max(b, rb); continue; }
                if (a >= 0) done = BodySettle.run(s, src, dst, relief, impasto, prep, x0, y0, a, b, done, top, bottom, from, to, rh);
                a = ra; b = rb;
            }
            if (a >= 0) done = BodySettle.run(s, src, dst, relief, impasto, prep, x0, y0, a, b, done, top, bottom, from, to, rh);
            if (done < rw) clear(dst, relief, x0 + done, x0 + rw - 1, y0 + top, y0 + bottom - 1, s.sh);
        }
    }

    /** Settles the band's columns a..b−1 (of the region from lattice column x0), clearing those from done to a; returns b. */
    private static int run(Settle s, FloatTiles src, FloatTiles dst, FloatTiles relief, boolean impasto, Prep prep, int x0, int y0, int a, int b, int done,
                           int top, int bottom, int from, int to, int rh) {
        if (a > done) clear(dst, relief, x0 + done, x0 + a - 1, y0 + top, y0 + bottom - 1, s.sh);
        s.band(src, dst, relief, impasto, prep, x0 + a, y0 + from, b - a, to - from, top - from, bottom - from, from, rh);
        return b;
    }
    /** Clears dst (and relief, if any) over lattice columns x0..x1, rows y0..y1: what a settle lays where there is no paint within reach. */
    private static void clear(FloatTiles dst, FloatTiles relief, int x0, int x1, int y0, int y1, int sh) {
        int[] page = {x0 << sh, y0 << sh, x1 << sh, y1 << sh};
        dst.clear(page);
        if (relief != null) relief.clear(page);
    }

    /** One settle's inputs, run band by band. */
    private static final class Settle {
        final StrokePaper paper; final ResolvedBrush.Look look;
        final double K, KN, feather, hollow, bloom, backrun;
        /** fr .. pileR: the radii in lattice points; sh: the lattice's shift. */
        final int fr, rimR, rimR2, pileR, w, h, sh;
        final StrokeMath.Scratch scratch;

        Settle(StrokePaper paper, ResolvedBrush.Look look, double K, double KN, double feather, double hollow, double bloom, double backrun,
               int fr, int rimR, int rimR2, int pileR, int w, int h, int sh, StrokeMath.Scratch scratch) {
            this.paper = paper; this.look = look; this.K = K; this.KN = KN; this.feather = feather; this.hollow = hollow;
            this.bloom = bloom; this.backrun = backrun; this.fr = fr; this.rimR = rimR; this.rimR2 = rimR2; this.pileR = pileR; this.w = w; this.h = h;
            this.sh = sh; this.scratch = scratch;
        }
        /** The same settle with its own scratch, for another thread. */
        Settle with(StrokeMath.Scratch scratch) { return new Settle(paper, look, K, KN, feather, hollow, bloom, backrun, fr, rimR, rimR2, pileR, w, h, sh, scratch); }

        /**
         * Settles lattice rows {@code y0 .. y0 + rh − 1} from lattice column {@code x0} and writes the band's own rows
         * {@code own0 .. own1 − 1} (of those). {@code at} is the band's first row within the whole padded region of
         * {@code all} rows. Page pixels (X, Y) read the paper.
         */
        void band(FloatTiles src, FloatTiles dst, FloatTiles relief, boolean impasto, Prep prep, int x0, int y0, int rw, int rh, int own0, int own1, int at, int all) {
            int cells = rw * rh;
            float[] P = scratch.get(P_SLOT, cells), tmp = scratch.get(TMP, cells);
            for (int y = 0; y < rh; y++) src.row(x0, y + y0, rw, P, y * rw);
            if (prep != null) prep.apply(P, x0, y0, rw, rh, scratch);
            if (feather > 0) {
                float[] B = scratch.get(BLUR, cells);
                blur(blur(P, rw, rh, fr, tmp, B), rw, rh, Math.max(1, fr >> 1), tmp, B);
                for (int y = 0; y < rh; y++) for (int x = 0; x < rw; x++) {
                    int j = y * rw + x, X = (x + x0) << sh, Y = (y + y0) << sh;
                    double reach = clamp((B[j] - (0.42 - 0.3 * feather) - 0.45 * (paper.low(X, Y) - 0.5) - 0.25 * (paper.mid(X, Y) - 0.5)) * 6);
                    double f = reach * B[j] * 1.4 * feather;
                    if (f > P[j]) P[j] = (float)clamp(f);
                }
            }
            float[] rim = null;
            if (hollow > 0) {
                // The rim takes the place of the blur it is read from.
                rim = scratch.get(BLUR, cells);
                blur(blur(P, rw, rh, rimR, tmp, rim), rw, rh, rimR2, tmp, rim);
                for (int j = 0; j < cells; j++) rim[j] = (float)Math.max(0, (double)P[j] - rim[j]);
            }
            // Every cell is written, so the reused rows need no clearing first.
            float[] out = scratch.get(OUT, cells);
            for (int y = 0; y < rh; y++) for (int x = 0; x < rw; x++) {
                int j = y * rw + x, X = (x + x0) << sh, Y = (y + y0) << sh;
                double d = P[j];
                if (d <= 0) { out[j] = 0; continue; }
                // Blooms and backruns read the low noises scaled up with the brush.
                int lx = K > 1 ? Math.min(w - 1, (int)(X / KN)) : X, ly = K > 1 ? Math.min(h - 1, (int)(Y / KN)) : Y;
                if (rim != null) d = d * (1 - 0.6 * hollow) + rim[j] * hollow * 5;
                // The paper's noises, each read once a pixel.
                float low = bloom > 0 || backrun > 0 ? paper.low(lx, ly) : 0, mid = bloom > 0 || look.gran > 0 ? paper.mid(X, Y) : 0;
                if (bloom > 0) d *= clamp(1 + bloom * 2.6 * (low - 0.5) + bloom * 0.5 * (mid - 0.5), 0.25, 2);
                if (backrun > 0) {
                    double th = 0.7 - 0.05 * backrun, n = 0.55 * paper.blob(lx, ly) + 0.45 * low, e = (n - th) / 0.02;
                    // Past twelve widths the ring adds nothing a double can hold.
                    double inside = clamp((n - th) * 40), ring = e * e > 144 ? 0 : Math.exp(-(e * e));
                    d = d * (1 - 0.4 * backrun * inside) + d * backrun * 0.9 * ring;
                }
                if (look.gran > 0) d *= 1 + look.gran * 1.5 * (paper.fine(X, Y) - 0.5) + look.gran * 0.6 * (mid - 0.5);
                out[j] = (float)clamp(d, 0, 1.6);
            }
            for (int y = own0; y < own1; y++) dst.setRow(x0, y + y0, rw, out, y * rw);
            if (impasto) {
                float[] Dd = scratch.get(HEIGHTS, cells);
                for (int j = 0; j < cells; j++) Dd[j] = Math.min(1, out[j]);
                float[] pile = blur(Dd, rw, rh, pileR, tmp, scratch.get(BLUR, cells));
                // Light fixed at the upper left: a slope rising toward the lower right faces it and reads lighter.
                float[] lit = scratch.get(LIT, rw);
                for (int y = Math.max(own0, 1 - at); y < Math.min(own1, all - 1 - at); y++) {
                    relief.row(x0, y + y0, rw, lit, 0);
                    for (int x = 1; x < rw - 1; x++) {
                        int j = y * rw + x;
                        if (Dd[j] <= 0) continue;
                        double ridge = (double)Dd[j + rw + 1] - Dd[j - rw - 1], edge = Math.max(0, (double)Dd[j] - pile[j]);
                        // On a coarser lattice the neighbours are farther apart: the same slope rises more between them.
                        if (sh > 0) ridge /= 1 << sh;
                        lit[x] = (float)(look.impasto * (100 * ridge - 90 * edge));
                    }
                    relief.setRow(x0, y + y0, rw, lit, 0);
                }
            }
        }
    }

    /**
     * settleBlend: the line loosens in wet wash, the wash pulls feathers out of it, the rim pools where they cross.
     * Writes L in place and the body into D; uses lineSave and bodySave and leaves them clear. Returns the region covered.
     */
    static int[] blend(FloatTiles l, FloatTiles w, FloatTiles d, FloatTiles lineSave, FloatTiles bodySave, FloatTiles relief, StrokePaper paper, ResolvedBrush r, int[] rect, StrokeMath.Scratch scratch) {
        Blend b = new Blend(l, w, d, lineSave, bodySave, relief, paper, r, rect, BAND, scratch);
        int last = l.height - 1;
        b.save(0, last); b.mix(0, last); b.wet(0, last);
        if (b.bands != null) b.bands.settle(b.bands.count);
        b.done();
        return b.region();
    }

    /**
     * {@link #blend}'s steps, each over page rows given: the line and body saved, the line loosened into the body, the
     * line's wet laid into the body, then the settle's bands ({@link #bands}). All at once, or down the region a strip at
     * a time (a fill's, {@link ModularStroke#fill}): a row's line and body are final once the loosening has run
     * {@link #loosens} rows past it, and the loosening reads the saved rows that far past its own.
     */
    static final class Blend {
        private final FloatTiles l, w, lineSave, bodySave;
        private final StrokePaper paper;
        private final ResolvedBrush.Blend P;
        private final int[] rect;
        private final boolean mixing;
        /** Where the line and body are saved before the line loosens (null: they aren't), and how far a line pixel's halo reaches, lattice points. */
        private final int[] saved;
        private final int reachF;
        /** The settle's bands, none settled yet; null with no body to settle. */
        final Bands bands;

        Blend(FloatTiles l, FloatTiles w, FloatTiles d, FloatTiles lineSave, FloatTiles bodySave, FloatTiles relief, StrokePaper paper, ResolvedBrush r, int[] rect, int band, StrokeMath.Scratch scratch) {
            this.l = l; this.w = w; this.lineSave = lineSave; this.bodySave = bodySave; this.paper = paper; this.rect = rect;
            P = r.parts;
            int pw = l.width, ph = l.height;
            mixing = P.stampLine && P.bodyAmt > 0;
            // On the stroke's lattice (every pixel, or a big stroke's even ones); dx, dy count lattice points.
            int g = 6;
            saved = mixing ? new int[]{Math.max(0, rect[0] - g), Math.max(0, rect[1] - g), Math.min(pw - 1, rect[2] + g), Math.min(ph - 1, rect[3] + g)} : null;
            reachF = l.radius(1 + (int)Math.round(P.w));
            if (!(P.bodyAmt > 0 || P.lineWet > 0)) { bands = null; return; }
            final int rT = (int)Math.round(3 + 9 * P.w), diffR = w.radius(P.diffR), diffR2 = w.radius(Math.max(1, P.diffR >> 1));
            Prep diffuse = P.diffuse > 0 ? (Q, x0, y0, rw, rh, s) -> {
                int n = rw * rh;
                float[] B = s.get(BLUR, n), tmp = s.get(TMP, n);
                blur(blur(Q, rw, rh, diffR, tmp, B), rw, rh, diffR2, tmp, B);
                double m = P.diffuse;
                for (int j = 0; j < n; j++) Q[j] = (float)(Q[j] * (1 - .45 * m) + B[j] * m * 1.1);
            } : null;
            Prep prep = mixing ? new Pull(diffuse, lineSave, bodySave, paper, saved, rT, P.w) : diffuse;
            bands = BodySettle.bands(w, d, relief, paper, r.look, rect, r.bodySize, prep, prep != null ? Math.max(P.diffR * 2, mixing ? rT * 2 : 0) : 0, mixing ? lineSave : null, band, scratch);
        }
        /** Lattice rows past a row whose line and body are to be final that the loosening reads the saved line and body (the rim's three page px too). */
        int reach() { return mixing ? 2 * reachF + bodySave.radius(3) : 0; }
        /** Lattice rows the loosening runs past a row before its line is final. */
        int loosens() { return mixing ? reachF : 0; }

        /** Rows {@code y0..y1} of the line and body saved as laid, before the line loosens there. */
        void save(int y0, int y1) {
            if (!mixing) return;
            int[] at = {saved[0], Math.max(saved[1], y0), saved[2], Math.min(saved[3], y1)};
            lineSave.copy(l, at); bodySave.copy(w, at);
        }
        /** Page rows before which the line's loosening is final ({@link #mix}). */
        private int loosened = Integer.MIN_VALUE;
        /** Each worker's halos and loosened lines for the rows a band of {@link #mix} reads (NaN: no loosening there). */
        private final double[][] halos = new double[Cores.WORKERS][], lines = new double[Cores.WORKERS][];

        /**
         * The line loosening in the wash, its rows through {@code y1} saved: where the line meets the body it loosens
         * (lighter, its paint into the body), and a pixel of it lays a halo on the line about it, where the line was
         * light. As MonoPaint lays them, pixel by pixel down the page, a halo laid before a light line pixel loosens is
         * taken back by it and one laid after stays, and halos keep the strongest. So each pixel is worked out alone from
         * the pixels about it, the rows {@link #loosens} of a pixel short of {@code y1} at a time (all once {@code y1} is
         * the last), in bands of tile rows on several cores, each band writing only its own rows: the same floats.
         */
        void mix(int y0, int y1) {
            if (!mixing) return;
            int sh = l.shift, reach = reachF << sh, from = Math.max(loosened, saved[1]), to = y1 >= saved[3] ? saved[3] : y1 - reach;
            if (to < from) return;
            loosened = to + 1;
            Cores.inBands(from, to, FloatTiles.SIDE << sh, (long)(saved[2] - saved[0] + 1) * (to - from + 1), this::loosen);
        }
        /** {@link #mix}'s page rows {@code top..bottom}, by {@code worker}. */
        private void loosen(int top, int bottom, int worker) {
            int sh = l.shift, step = l.step(), reach = reachF << sh, X0 = l.up(saved[0]), X1 = saved[2];
            int n = (X1 - X0 >> sh) + 1, r0 = l.up(Math.max(saved[1], top - reach)), r1 = Math.min(saved[3], bottom + reach), rows = (r1 - r0 >> sh) + 1;
            if (n <= 0 || rows <= 0) return;
            double[] H = halos[worker], N = lines[worker];
            if (H == null || H.length < n * rows) { halos[worker] = H = new double[n * rows]; lines[worker] = N = new double[n * rows]; }
            double k = smooth(P.w * 1.5);
            // Each pixel the band reads: its halo and its line loosened, if it loosens.
            for (int y = r0, j = 0; y <= r1; y += step, j += n) for (int x = X0; x <= X1; ) {
                int tx = l.tileOf(x), ty = l.tileOf(y), xe = Math.min(X1, l.tileEnd(tx)), o = j + (x - X0 >> sh), oe = j + (xe - X0 >> sh);
                if (lineSave.tile(tx, ty, false) == null || bodySave.tile(tx, ty, false) == null) { java.util.Arrays.fill(N, o, oe + 1, Double.NaN); x = xe + 1; continue; }
                for (; x <= xe; x += step, o++) {
                    N[o] = Double.NaN;
                    float ls = lineSave.get(x, y), bs = bodySave.get(x, y);
                    if (ls <= 0 || bs <= 0) continue;
                    double soft = clamp(bs * 1.5) * k * .9 * clamp((paper.low(x, y) - .38) * 3.2 + .15 * (paper.mid(x, y) - .5));
                    if (soft <= 0) continue;
                    N[o] = ls * (1 - .72 * soft);
                    H[o] = ls * soft * .5;
                }
            }
            for (int y = l.up(top), j = (y - r0 >> sh) * n; y <= bottom; y += step, j += n) for (int x = X0, o = j; x <= X1; x += step, o++) {
                float ls = lineSave.get(x, y);
                double nl = N[o];
                boolean loosens = nl == nl;
                if (loosens) w.set(x, y, (float)(w.get(x, y) + (ls - nl) * 1.2));
                if (ls >= .5) { if (loosens) l.set(x, y, (float)nl); continue; }
                // The strongest halo laid on it before it loosens, and after.
                double before = Double.NEGATIVE_INFINITY, after = before;
                for (int dy = -reachF; dy <= reachF; dy++) {
                    int yy = y + (dy << sh);
                    if (yy < r0 || yy > r1) continue;
                    for (int dx = -reachF; dx <= reachF; dx++) {
                        int xx = x + (dx << sh), s = o + dy * n + dx;
                        if (xx < X0 || xx > X1 || dx == 0 && dy == 0 || N[s] != N[s]) continue;
                        double v = H[s] / (1 + (Math.abs(dx) + Math.abs(dy) << sh));
                        if (dy > 0 || dy == 0 && dx > 0) { if (v > after) after = v; }
                        else if (v > before) before = v;
                    }
                }
                float was = l.get(x, y), now = was;
                if (loosens) {
                    float found = before > was ? (float)before : was;
                    now = (float)Math.max(nl, Math.min(found, ls));
                    if (after > now) now = (float)after;
                } else {
                    double most = Math.max(before, after);
                    if (most > was) now = (float)most;
                }
                if (now != was) l.set(x, y, now);
            }
        }
        /** The line's wet laid into the body at rows {@code y0..y1}, their line final. */
        void wet(int y0, int y1) {
            if (!(P.lineWet > 0)) return;
            int pw = l.width, ph = l.height, step = l.step();
            double k = P.lineWet * StrokeMath.LINE_WET_K, along = .3 * P.bodyAmt;
            int from = Math.max(Math.max(0, rect[1]), y0), to = Math.min(Math.min(ph - 1, rect[3]), y1), x0 = Math.max(0, rect[0]), x1 = Math.min(pw - 1, rect[2]);
            // Each pixel alone: in bands of tile rows on several cores.
            Cores.inBands(from, to, FloatTiles.SIDE << l.shift, (long)(x1 - x0 + 1) * (to - from + 1), (top, bottom, worker) -> {
                for (int y = l.up(top); y <= bottom; y += step) for (int x = l.up(x0); x <= x1; x += step) {
                    if (l.tile(l.tileOf(x), l.tileOf(y), false) == null) { x = Math.min(pw - 1, l.tileEnd(l.tileOf(x))) - step + 1; continue; }
                    float lv = l.get(x, y);
                    double v = lv * k;
                    if (v > 0) w.set(x, y, (float)(Math.max(w.get(x, y), v) + along * lv));
                }
            });
        }
        /** The saved line and body let go. */
        void done() { if (saved != null) { lineSave.clear(saved); bodySave.clear(saved); } }
        /** The region the blend covers: its rectangle, and the settle's. */
        int[] region() { return bands == null ? rect : StrokeMath.union(rect, bands.region); }
    }

    /**
     * The blend's prep: the wash pulls feathers out of the line (a half-size blur of it) and pools where they cross.
     * On a big stroke's lattice, already half size, the line is blurred on the lattice itself.
     */
    private static final class Pull implements Prep {
        final Prep diffuse; final FloatTiles ls, bs; final StrokePaper paper; final int[] saved; final int rT; final double tk;

        Pull(Prep diffuse, FloatTiles ls, FloatTiles bs, StrokePaper paper, int[] saved, int rT, double w) {
            this.diffuse = diffuse; this.ls = ls; this.bs = bs; this.paper = paper; this.saved = saved; this.rT = rT; tk = smooth(w * 1.4);
        }

        /** {@code Q} is {@code rw × rh} lattice points from lattice column X0, row Y0. */
        @Override public void apply(float[] Q, int X0, int Y0, int rw, int rh, StrokeMath.Scratch s) {
            if (diffuse != null) diffuse.apply(Q, X0, Y0, rw, rh, s);
            int w = ls.width, H = ls.height, sh = ls.shift;
            // The half-size grid: f lattice points to a cell each way.
            int f = sh == 0 ? 2 : 1, hw = (rw + f - 1) / f, hh = (rh + f - 1) / f;
            float share = 1f / (f * f);
            float[] down = s.zeroed(DOWN, hw * hh);
            int sx0 = ls.up(saved[0]) >> sh, sy0 = ls.up(saved[1]) >> sh, sx1 = saved[2] >> sh, sy1 = saved[3] >> sh;
            // The line a row at a time.
            int xa = Math.max(X0, sx0), xb = Math.min(X0 + rw, sx1 + 1);
            float[] run = s.get(PULL_ROWS, Math.max(xb - xa, 3 * (rw + 2 * bs.radius(3))));
            if (xb > xa) for (int y = Math.max(Y0, sy0); y < Math.min(Y0 + rh, sy1 + 1); y++) {
                int row = ((y - Y0) / f) * hw;
                ls.row(xa, y, xb - xa, run, 0);
                for (int x = xa; x < xb; x++) { float l = run[x - xa]; if (l > 0) down[row + (x - X0) / f] += l * share; }
            }
            float[] BL = blur(down, hw, hh, Math.max(1, rT >> 1), s.get(TMP, hw * hh), down);
            // The rim compares the body three page pixels away (two lattice points on a big stroke's).
            int d3 = bs.radius(3), n3 = d3 << sh, cw = rw + 2 * d3;
            // The body's row and the rows three page pixels above and below it, read once a row as a pixel needs them:
            // the row from d3 before X0 at 0, the one above from X0 at cw + d3, the one below at 2 cw + d3.
            float[] near = run;
            for (int y = 0; y < rh; y++) {
                int Y = (y + Y0) << sh, hy0, hy1;
                boolean read = false;
                double ty;
                if (f == 2) {
                    double fy = Math.max(0, y / 2.0 - .25);
                    hy0 = Math.min(hh - 1, (int)fy); hy1 = Math.min(hh - 1, hy0 + 1); ty = fy - hy0;
                } else { hy0 = hy1 = y; ty = 0; }
                for (int x = 0; x < rw; x++) {
                    double bl;
                    if (f == 2) {
                        if (BL[hy0 * hw + (x >> 1)] < .006 && BL[hy1 * hw + (x >> 1)] < .006) continue;
                        double fx = Math.max(0, x / 2.0 - .25);
                        int hx0 = Math.min(hw - 1, (int)fx), hx1 = Math.min(hw - 1, hx0 + 1);
                        double tx = fx - hx0;
                        bl = lerp(lerp(BL[hy0 * hw + hx0], BL[hy0 * hw + hx1], tx), lerp(BL[hy1 * hw + hx0], BL[hy1 * hw + hx1], tx), ty);
                    } else bl = BL[y * hw + x];
                    if (bl < .012) continue;
                    int j = y * rw + x, X = (x + X0) << sh;
                    if (!read) {
                        int ly = y + Y0, c0 = Math.max(0, X0 - d3), c1 = Math.min(bs.latticeWidth - 1, X0 + rw - 1 + d3);
                        bs.row(c0, ly, c1 - c0 + 1, near, c0 - (X0 - d3));
                        if (ly - d3 >= 0) bs.row(X0, ly - d3, rw, near, cw + d3);
                        if (ly + d3 < bs.latticeHeight) bs.row(X0, ly + d3, rw, near, 2 * cw + d3);
                        read = true;
                    }
                    double br = near[d3 + x], inWash = clamp(br * 2.5);
                    double reach = clamp((bl * 5 - (.45 - .25 * tk) - .45 * (paper.low(X, Y) - .5) - 1.1 * (paper.mid(X, Y) - .5)) * 4);
                    double add = reach * clamp(bl * 3) * .9 * tk * (.35 + .65 * inWash);
                    if (br > 0 && X >= n3 && Y >= n3 && X < w - n3 && Y < H - n3) {
                        double rim = Math.max(0, br - ((double)near[x] + near[x + 2 * d3] + near[cw + d3 + x] + near[2 * cw + d3 + x]) * .25);
                        add += rim * clamp(bl * 7) * 3.2 * tk;
                    }
                    Q[j] += add;
                }
            }
        }
    }

    private BodySettle() {}
}
