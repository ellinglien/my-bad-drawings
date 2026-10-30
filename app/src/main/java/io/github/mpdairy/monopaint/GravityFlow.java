package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.lerp;
import static io.github.mpdairy.monopaint.StrokeMath.smooth;
import static io.github.mpdairy.monopaint.StrokeMath.smoothstep;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Gravity: a Slow wash that is still wet moves with the page's tilt (the Gravity Lab's gravity.js Flow, ported).
 * One flow a {@link DryingWater} patch, built at its first tilted tick and stepped every tick until the wash is dry:
 * water above what the paper holds runs downhill and carries its pigment (Runs), pigment drains to the low edge and
 * beads there (Pool), the granulating share sinks into the valleys (Sink), rocking the page blends it (Blend), and drips
 * break from the low edge where the bead of paint gathers, fairly evenly spaced, broad and much alike, each a darker run
 * led by a teardrop bead that slows and stops where its paint runs out (Drips). The outline stays where the stroke
 * laid it while the colour inside drains; nothing moves once dry.
 * <p>
 * The flow keeps the layer multiplicative: it holds the wash's share of each point ({@code cur}) and applies a change as
 * tone × (1 − new) / (1 − old), on a float copy of the layer's tones ({@link TonePage}) written back once a tick, so
 * lines drawn over or under the wash stay as they are. Water and pigment move between cells of {@link #cs} pixels: the
 * prototype's 2, or 4 for a stroke on the lattice ({@link ModularStroke#latticeFrom}, as its own lattice halves the resolution), a
 * quarter of the cells; each pixel keeps its own share of its cell, so the wash keeps its edge, and paint and drips
 * move as far on the page a tick either way. Deterministic: seeded by the stroke, and the work shared over {@link Cores}
 * gathers each cell's result in a fixed order. Pure.
 */
final class GravityFlow {
    /** Water a cell of paper holds on average; only the excess runs. */
    static final double FLOW_HOLD = .35;
    /** Pigment's weight in a cell's load (water + LOAD_K × mobile pigment): dark, wet spots move first. */
    static final double LOAD_K = 1.2;
    /** Water a drip leaves in the paper, per pixel of run and pixel of width. */
    static final double DRIP_LOSS = .15;
    /** The dry front's hold, times each cell's pin. */
    static final double PIN_K = 3;
    /** Edge flow: the share drawn from 1 to 4 cells inside the wet edge. */
    private static final double[] EDGE_TAPER = {0, .45, .35, .25, .15};
    /**
     * The most a flow's arrays may take, bytes: past it the cells grow coarser ({@link #cells}). The app's heap is 192 MB.
     */
    static final long MOST_BYTES = 48L << 20;
    /** What a flow over {@code fw × fh} pixels in cells of {@code cs} takes, bytes: 13 a pixel and 122 a cell. */
    static long bytes(long fw, long fh, int cs) { return fw * fh * 13 + ((fw + cs - 1) / cs) * ((fh + cs - 1) / cs) * 122; }
    /**
     * The most cells a tick works through before they grow coarser: on the Manta a tick's passes over the cells cost
     * about 0.4 ms for every 1,000 (four cores), so a tick's own work stays near 20 ms (a 300 px stroke of a 60 px wash
     * keeps the prototype's 2-pixel cells).
     */
    static final int MOST_CELLS = 48_000;
    /**
     * The cells for a wash over {@code w × h} pixels with {@code g} on the stroke's lattice {@code shift}: 2 pixels (the
     * prototype's), 4 on a lattice, coarser while there would be more than {@link #MOST_CELLS} or the arrays would
     * pass {@link #MOST_BYTES}, up to 8; 0 when even those would (the wash doesn't move).
     */
    static int cells(int w, int h, ResolvedBrush.Grav g, int shift) {
        long fw = w + 2L * margin(g), fh = h + 2L * margin(g);
        int cs = 2 << shift;
        while (cs < 8 && ((fw + cs - 1) / cs) * ((fh + cs - 1) / cs) > MOST_CELLS) cs *= 2;
        for (; cs <= 8; cs *= 2) if (bytes(fw, fh, cs) <= MOST_BYTES) return cs;
        return 0;
    }
    /** The room a wash with {@code g} has to run past its patch on every side, pixels. */
    static int margin(ResolvedBrush.Grav g) { return 2 * (int)Math.round(8 + 60 * g.runs + 30 * g.blend + 10 * g.pool + 12 * g.drips); }

    // ---------------------------------------------------------------------------------------------------------------
    // The layer's tones as floats, read on first touch, written back once a tick.

    /**
     * The flow's layer's tones (0–255) as floats, in 64-pixel tiles read from the document on first touch: every change
     * the flow and its patch make multiplies them, and {@link #flush} writes what rounds differently back to the
     * document. The flow owns the area while it is wet (a new stroke lays the wash first), so the copy stays true.
     */
    static final class TonePage {
        private static final int SHIFT = 6, SIDE = 1 << SHIFT, MASK = SIDE - 1;
        private final ToneDocument doc;
        final int w, h;
        private final int cols;
        /** Each tile's tones as floats, and as last read or written (what the document holds); made on first touch. */
        private final float[][] tiles;
        private final byte[][] held;
        /**
         * Tiles changed since the last flush, and in each of their rows the first and last pixel changed (first past last
         * when none): a pixel row is only ever changed by one band at a time, so this is safe on several cores.
         */
        private final boolean[] changed;
        private final byte[][] spans;
        private boolean any;

        TonePage(ToneDocument doc) {
            this.doc = doc; w = doc.width; h = doc.height; cols = (w + MASK) >> SHIFT;
            int n = cols * ((h + MASK) >> SHIFT);
            tiles = new float[n][]; held = new byte[n][]; changed = new boolean[n]; spans = new byte[n][];
        }
        private float[] tile(int x, int y) {
            int k = (y >> SHIFT) * cols + (x >> SHIFT);
            float[] t = tiles[k];
            if (t == null) {
                t = new float[SIDE * SIDE];
                byte[] b = new byte[SIDE * SIDE];
                int left = (x >> SHIFT) << SHIFT, top = (y >> SHIFT) << SHIFT;
                for (int v = 0; v < SIDE && top + v < h; v++) for (int u = 0; u < SIDE && left + u < w; u++) { int tone = doc.tone(left + u, top + v); t[v << SHIFT | u] = tone; b[v << SHIFT | u] = (byte)tone; }
                held[k] = b;
                byte[] sp = new byte[2 * SIDE];
                for (int v = 0; v < SIDE; v++) sp[2 * v] = SIDE - 1;
                spans[k] = sp;
                tiles[k] = t;
            }
            return t;
        }
        /** The tone at page pixel {@code x, y}. */
        float tone(int x, int y) { return tile(x, y)[(y & MASK) << SHIFT | (x & MASK)]; }
        /** Its darkness, 0 (paper) to 1. */
        double dark(int x, int y) { return 1 - tone(x, y) / 255.0; }
        /** Multiplies the tone at {@code x, y} by {@code f}; safe from bands on several cores once their tiles are read ({@link #prepare}). */
        void mul(int x, int y, double f) {
            float[] t = tile(x, y);
            int i = (y & MASK) << SHIFT | (x & MASK), k = (y >> SHIFT) * cols + (x >> SHIFT);
            t[i] = (float)(t[i] * f);
            touch(k, x, y);
        }
        /** Notes pixel {@code x, y} of tile {@code k} changed. */
        private void touch(int k, int x, int y) {
            byte[] sp = spans[k];
            int r = (y & MASK) << 1, u = x & MASK;
            if (u < sp[r]) sp[r] = (byte)u;
            if (u > sp[r + 1]) sp[r + 1] = (byte)u;
            if (!changed[k]) { changed[k] = true; any = true; }
        }
        /**
         * Darkens {@code x, y} to {@code a} where it is lighter (the darker of the two shows): returns the darkness laid
         * (0 where it was already as dark), which {@link #mul}(1 / (1 − it)) takes back.
         */
        double union(int x, int y, double a) {
            float[] t = tile(x, y);
            int i = (y & MASK) << SHIFT | (x & MASK), k = (y >> SHIFT) * cols + (x >> SHIFT);
            double e = 1 - t[i] / 255.0;
            if (a <= e + .002) return 0;
            double am = (a - e) / (1 - e);
            t[i] = (float)(t[i] * (1 - am));
            touch(k, x, y);
            return am;
        }
        /** Reads the tiles over a box now, so bands on several cores can then change them. */
        void prepare(int x0, int y0, int x1, int y1) {
            for (int ty = Math.max(0, y0) >> SHIFT; ty <= Math.min(h - 1, y1) >> SHIFT; ty++)
                for (int tx = Math.max(0, x0) >> SHIFT; tx <= Math.min(w - 1, x1) >> SHIFT; tx++) tile(tx << SHIFT, ty << SHIFT);
        }
        /**
         * Writes what changed into {@code out} (the document's tones to be, {@link DryingWater#apply} writes them), a tile's
         * row at a time, only where a tone rounds differently from what the document holds; returns whether it wrote any.
         */
        boolean flush(StrokeTones out) {
            if (!any) return false;
            any = false;
            boolean wrote = false;
            int[] grays = new int[SIDE];
            for (int k = 0; k < changed.length; k++) {
                if (!changed[k]) continue;
                changed[k] = false;
                float[] t = tiles[k];
                byte[] b = held[k];
                byte[] sp = spans[k];
                int left = (k % cols) << SHIFT, top = (k / cols) << SHIFT;
                for (int v = 0; v < SIDE && top + v < h; v++) {
                    int lo = sp[2 * v], hi = Math.min(sp[2 * v + 1], w - 1 - left);
                    if (lo > sp[2 * v + 1]) continue;
                    sp[2 * v] = SIDE - 1; sp[2 * v + 1] = 0;
                    // Only the span that changed, and of it from the first tone that rounds differently to the last.
                    int first = -1, last = -1;
                    for (int u = lo, i = (v << SHIFT) + lo; u <= hi; u++, i++) {
                        int tone = Math.round(t[i]);
                        tone = tone < 0 ? 0 : tone > 255 ? 255 : tone;
                        grays[u] = -1;
                        if (tone != (b[i] & 255)) { grays[u] = tone; b[i] = (byte)tone; if (first < 0) first = u; last = u; }
                    }
                    if (first < 0) continue;
                    if (first > 0) System.arraycopy(grays, first, grays, 0, last - first + 1);
                    out.setTones(left + first, top + v, last - first + 1, 1, grays);
                    wrote = true;
                }
            }
            return wrote;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The flow's state. Points: the flow's grid, s page pixels apart; cells: 2 × 2 points.

    final ResolvedBrush.Grav g;
    private final TonePage W;
    private final StrokePaper paper;
    /** A cell's side, pixels: the prototype's 2, or 4 on a big stroke's lattice. */
    final int cs;
    /**
     * The prototype's cell (2 pixels) in cells: what it measures in cells (how far paint moves a tick, a bead's height,
     * the scale of lobes and blooms) is scaled by it, so a wash in bigger cells moves as far on the page a tick.
     */
    private final double k;
    /** The page, pixels. */
    private final int pw, ph;
    /** How many ticks the wash is wet. */
    private final int N;
    private final int seed;
    private final WildBrush.Random r;
    /** Last tick's tilt (Blend reads the change). */
    private double gx, gy;
    /** The flow's box in pixels (its first pixel, its size) and its cell grid. */
    final int fx0, fy0, fw, fh, cw, ch;
    /** A pixel's paint already in the paper, the wash's share now shown, its share of its cell's pigment. */
    private final float[] fixed, cur, wgt;
    /** A pixel's tooth (.55–1.45, a byte), and how much of it arriving pigment follows ({@link #toothA}). */
    private final byte[] tooth;
    private final double tk;
    /** Each cell: mobile pigment, water, sinking grain, paper height, settled pigment and grain, a tick's moves, the blurred excess. */
    private final float[] A, Wt, Gs, H, S, Sg, dA, dW, dG, Eb, tmp;
    /** What crosses each cell's lower pair this tick: water, pigment, grain (made on first use; Runs and Pool). */
    private float[] eDw, eDa, eDg;
    /**
     * What each cell showed at its last render (softened pigment, wet mask, grain; NaN before the first), and whether its
     * pixels' shares changed since: a render passes over cells where none of this changed round them, which looks the
     * same (a change under {@link #STILL} moves no pixel by a visible step) and spares the still parts of a big wash.
     */
    private final float[] Plast, Qlast, Glast;
    private final byte[] touched, moved;
    private static final float STILL = 1e-4f;
    /** Each cell's paper: the water it holds, how easily water passes, its drying rate, where pigment gathers, the front's hold, how fast pigment settles. */
    private final float[] hold, cond, evap, pot, pin, dep, nEdge, Hs, W0, P2, P3;
    /** The wash's thickness (about the brush's width), its mean excess water at lift, pooling's caps, how liquid moved paint's edges are. */
    final double thick, e0, capA, capG, liq;
    /**
     * How near black the wash is at lift, 0 to 1: 0 for every wash before washes reached black (its darkest pixel
     * {@link ModularSizes#KNEE} at most), 1 at {@link #DEEP_FROM} and darker. The darkest moved paint, a drip and its bead
     * go ({@link #deep}) rise with it toward {@link #DEEPEST}, so a black wash's pooled edge and drips stay darker than it.
     */
    private final double deep;
    /** {@link #deep}'s ends: the wash's darkest at lift where it is all there, and the darkest it lets paint go. */
    static final double DEEP_FROM = .95, DEEPEST = .99;
    /** The cells still worth working over, {x0, y0, x1, y1}; null once nothing is left. */
    private int[] box;
    private final List<Drip> drips = new ArrayList<>();
    /** Where each drip left the wash (page pixels): the next keeps its distance along the edge. */
    private final List<double[]> dripStarts = new ArrayList<>();
    private final List<double[]> blooms = new ArrayList<>();
    private final int maxBlooms;
    /** Each drip's trail store, by 64-pixel tile (page pixels): the darkness laid, then the nearest normalised distance. */
    private final HashMap<Integer, float[]> tiles = new HashMap<>();
    /** Pigment the patch's settling added, and the drips carried off (points of darkness): the ledger's terms. */
    double added, dripPig;
    private double washTone = -1;
    /** A render is due (it runs every other tick). */
    private boolean pend;
    /** The valleys' weight on grain at each pixel, made on first use (Sink). */
    private float[] vf;
    /** Every drip there has been, and how many merged (for the checks). */
    final List<Drip> log = new ArrayList<>();
    int merges;
    /** A pixel's weights towards its cell's neighbours, by its place in the cell (the render's bilinear read). */
    private final double[] near;
    /**
     * Cells of 4 pixels: the softened pigment and each cell's own at the start, so a render shows only what moved since
     * (a pixel's wash as the stroke laid it, plus the change), rather than redrawing the stroke's soft edge in 4-pixel
     * steps; null in the prototype's 2-pixel cells.
     */
    private float[] P0, A0;

    /** A drip: where its head is (pixels), how it moves, its water and pigment, its shape along the run, its bead. */
    static final class Drip {
        double x, y, x0, y0, dx, dy, v, vol, vol0, pig, w0, w0t, w, tone, t, len, max, fed, pl, lk, wa, wl, bias, wob = 1, mc = .82;
        /** Half the width of the stretch of edge that feeds it (pixels); how heavy it is (its paint: about 0.1 to 3, 1 a typical one). */
        double half, load = 1;
        /** Where its trail was last laid to, and the radius, tone and middle's share there; what is noted to lay next (x, y, radius, run, tone, middle's share). */
        double lx, ly, lr, lt, lm;
        double[] queue = new double[36];
        int queueN;
        /** Its width without the flare where it leaves the wash (the bead's scale); the darkness of the edge it left. */
        double wb, edge;
        /** Which way it left the wash; whether its mouth is laid. */
        double ux0, uy0;
        boolean mouthLaid, laid;
        int seed, ws, merged;
        boolean done;
        Drip into;
        /** The cell where it left the edge, while the stretch of bead there still feeds it; null after. */
        int[] src;
        /** The path every 6 pixels: x, y, width. */
        float[] path = new float[48];
        int pathN;
        /** The bead laid reversibly at the head: page pixels (y << 16 | x) and the darkness laid at each. */
        int[] beadI = new int[64];
        float[] beadA = new float[64];
        int beadN;
        /** Where its bead was laid, its radius, and whether hard (it is laid again only when one of these changes). */
        double bx, by, br;
        boolean bh;
        /** The bead's stamp (see putBead): its pixels' offsets from the head's pixel (v << 16 | u, each + 32768), their darkness, how many; what it was worked out for. */
        int[] stampAt;
        float[] stamp;
        int stampN;
        double kr, kc, kL, kt, ka;
        boolean kh;

        void addPath(double px, double py, double pwid) {
            if (pathN + 3 > path.length) path = Arrays.copyOf(path, path.length * 2);
            path[pathN++] = (float)px; path[pathN++] = (float)py; path[pathN++] = (float)pwid;
        }
        void addBead(int i, float a) {
            if (beadN == beadI.length) { beadI = Arrays.copyOf(beadI, beadN * 2); beadA = Arrays.copyOf(beadA, beadN * 2); }
            beadI[beadN] = i; beadA[beadN++] = a;
        }
    }

    /**
     * A flow over the patch {@code X0, Y0, rw × rh} (page pixels) of a wash on {@code page}: {@code D} its darkness at
     * lift and {@code shown} its share shown now, row by row; {@code N} ticks wet; the tilt it starts on.
     * {@code cs}: its cells' side, pixels (2, the prototype's; 4 on a big stroke's lattice; 8 for a wash over much of the
     * page, {@link #cells}).
     */
    GravityFlow(TonePage page, StrokePaper paper, int X0, int Y0, int rw, int rh, float[] D, float[] shown, ResolvedBrush.Grav g, int N, int seed, PageTilt G, int cs) {
        this.W = page; this.paper = paper; this.g = g; this.N = N; this.seed = seed; this.cs = cs; k = 2.0 / cs;
        r = new WildBrush.Random(seed ^ 0x6a71);
        gx = G.x; gy = G.y;
        pw = page.w; ph = page.h;
        double[] w8 = new double[cs];
        for (int u = 0; u < cs; u++) w8[u] = (u + .5) / cs - .5;
        near = w8;
        float[] Dp = D, Sp = shown;
        int X0p = X0, Y0p = Y0, rwp = rw, rhp = rh;
        double darkest = 0;
        for (float v : D) if (v > darkest) darkest = v;
        deep = smoothstep(ModularSizes.KNEE, DEEP_FROM, darkest);
        // Room to run past the patch, on every side (the tilt can change while it is wet); cells start on multiples of cs.
        int m = margin(g);
        int x0 = Math.max(0, X0p - m) / cs * cs, y0 = Math.max(0, Y0p - m) / cs * cs, x1 = Math.min(pw, X0p + rwp + m), y1 = Math.min(ph, Y0p + rhp + m);
        fx0 = x0; fy0 = y0; fw = x1 - x0; fh = y1 - y0; cw = (fw + cs - 1) / cs; ch = (fh + cs - 1) / cs;
        int np = fw * fh, nc = cw * ch;
        double cell = 1.0 / (cs * cs);
        fixed = new float[np]; cur = new float[np]; wgt = new float[np]; tooth = new byte[np];
        A = new float[nc]; Wt = new float[nc]; Gs = new float[nc]; H = new float[nc]; S = new float[nc]; Sg = new float[nc];
        dA = new float[nc]; dW = new float[nc]; dG = new float[nc]; Eb = new float[nc]; tmp = new float[nc];
        // What moves: a share of the wash's pigment (more with more of the dials); the rest is already in the paper.
        double fm = clamp(.55 + .4 * Math.max(Math.max(g.runs, g.pool), Math.max(g.blend, Math.max(.6 * g.sink, .6 * g.drips))), 0, .88), gsh = .6 * g.sink;
        int bx0 = cw, by0 = ch, bx1 = -1, by1 = -1;
        long area = 0, per = 0;
        for (int y = 0; y < rhp; y++) for (int x = 0; x < rwp; x++) {
            int k = y * rwp + x, lx = x + X0p - fx0, ly = y + Y0p - fy0, j = ly * fw + lx, c = (ly / cs) * cw + lx / cs;
            double sh = Sp[k];
            if (Dp[k] > .004) {
                Wt[c] += (float)cell;
                int cx = lx / cs, cy = ly / cs;
                if (cx < bx0) bx0 = cx;
                if (cx > bx1) bx1 = cx;
                if (cy < by0) by0 = cy;
                if (cy > by1) by1 = cy;
                area++;
                if (x == 0 || y == 0 || x == rwp - 1 || y == rhp - 1 || Dp[k - 1] <= .004 || Dp[k + 1] <= .004 || Dp[k - rwp] <= .004 || Dp[k + rwp] <= .004) per++;
            }
            if (sh <= 0) continue;
            double mob = sh * fm;
            cur[j] = (float)sh; fixed[j] = (float)(sh - mob);
            A[c] += (float)(mob * (1 - gsh) * cell); Gs[c] += (float)(mob * gsh * cell); wgt[j] = (float)mob;
        }
        // The wash's thickness (2 × area / perimeter ≈ the brush's width): sets the scale of lobes, beads and drips.
        thick = clamp(2.2 * area / Math.max(1, per), 4, 400);
        tk = clamp(1.5 * Math.max(Math.max(g.runs, g.pool), Math.max(.7 * g.drips, g.sink)));
        liq = clamp(1.5 * Math.max(Math.max(g.runs, g.pool), .7 * g.drips));
        // Per pixel, in bands of cell rows on several cores: its share of its cell (so the wash keeps its crisp edge
        // until paint arrives from elsewhere), its tooth, and the paper's height (.65 fine + .35 mid, half of it blurred
        // a pixel each way) into its cell's mean and, with Sink, the valleys' weight on grain.
        if (g.sink > 0) vf = new float[np];
        Cores.inBands(0, ch - 1, 8, (long)np, (top, bottom, worker) -> pixels(top, bottom, cell));
        // The paper and the wash are uneven: seeded noise (lobes ~6% of the wash's thickness, and finer) sets, per cell, how
        // much water it holds, how easily water passes (valleys channel it, peaks hold it back), how fast it dries, where
        // pigment gathers, how readily the front breaks, and where drips prefer to start.
        double sc = clamp(thick * .06, 2, 14) * k;
        float[] n1 = cellNoise(cw, ch, sc, r), n2 = cellNoise(cw, ch, sc * .4, r), n3 = cellNoise(cw, ch, sc * 1.7, r), n4 = cellNoise(cw, ch, sc * .9, r);
        hold = new float[nc]; cond = new float[nc]; evap = new float[nc]; pot = new float[nc]; pin = new float[nc]; dep = new float[nc]; nEdge = n4;
        // The paper's height at the scale water feels it (not single grains).
        Hs = StrokeMath.blur(H, cw, ch, 2);
        for (int c = 0; c < nc; c++) {
            double a = .6 * n1[c] + .4 * n2[c], hh = Hs[c];
            hold[c] = (float)(FLOW_HOLD * (.6 + .8 * a));
            cond[c] = (float)clamp((.4 + 1.2 * n3[c]) * (1 + 3 * (.5 - hh)), .15, 2.2);
            evap[c] = (float)(.0018 + .0075 * n3[c] * n3[c]);
            pot[c] = (float)(.65 * n4[c] + .35 * clamp(1.5 - 2 * hh));
            pin[c] = (float)(.25 + 1.4 * (1 - n1[c]) * (1 - n1[c]));
            dep[c] = (float)(.3 + 1.5 * n3[c] * (1 - .5 * n2[c]));
            if (Wt[c] > 0) Wt[c] *= (float)(.75 + .5 * n3[c]);
        }
        // The wash's own excess water at lift (mean over its wet cells): a bead has to grow well past this before it drips.
        double e = 0; int ne = 0;
        for (int c = 0; c < nc; c++) if (Wt[c] > .06) { e += Math.max(0, Wt[c] - hold[c]); ne++; }
        e0 = ne > 0 ? e / ne : 0;
        // The water each cell had at lift: what arrives above it is the bead.
        W0 = Wt.clone();
        // How full a cell may get: pooling and sinking stop where the edge is about twice the wash, never a black rim.
        double sa = 0, sg = 0; int na = 0;
        for (int c = 0; c < nc; c++) if (A[c] + Gs[c] > 0) { sa += A[c]; sg += Gs[c]; na++; }
        capA = Math.max(.02, 2.4 * sa / Math.max(1, na)); capG = Math.max(.01, 2.6 * sg / Math.max(1, na));
        box = bx1 < 0 ? null : new int[]{Math.max(0, bx0 - 1), Math.max(0, by0 - 1), Math.min(cw - 1, bx1 + 1), Math.min(ch - 1, by1 + 1)};
        maxBlooms = (int)Math.round(1 + 3 * Math.max(g.runs, g.pool));
        P2 = new float[nc]; P3 = new float[nc];
        Plast = new float[nc]; Qlast = new float[nc]; Glast = new float[nc]; touched = new byte[nc]; moved = new byte[nc];

        // Running, pooling paint lands in the paper's tooth (a point's share of arriving pigment); paint only blended by
        // rocking stays smooth.

        // What each cell shows now, so a render passes over a cell until something round it moves: where nothing
        // moves the wash stays exactly as the stroke laid it.
        soften(new int[]{0, 0, cw - 1, ch - 1});
        System.arraycopy(tmp, 0, Plast, 0, nc); System.arraycopy(P3, 0, Qlast, 0, nc);
        for (int c = 0; c < nc; c++) Glast[c] = Gs[c] + Sg[c];
        if (cs > 2) { A0 = A.clone(); P0 = tmp.clone(); }
    }
    /** The constructor's per-pixel work over cell rows {@code cy0..cy1}; {@code cell}: a pixel's share of a cell. */
    private void pixels(int cy0, int cy1, double cell) {
        int y0 = cy0 * cs, y1 = Math.min(fh - 1, cy1 * cs + cs - 1), rw = fw + 2;
        // The raw heights from a row above to a row below, a pixel either side (clamped to the box, as the blur).
        float[] raw = new float[(y1 - y0 + 3) * rw];
        for (int y = y0 - 1; y <= y1 + 1; y++) {
            int ly = StrokeMath.index(y, fh), at = (y - y0 + 1) * rw;
            for (int x = -1; x <= fw; x++) raw[at + x + 1] = (float)rawHeight(StrokeMath.index(x, fw) + fx0, ly + fy0);
        }
        for (int ly = y0; ly <= y1; ly++) for (int lx = 0; lx < fw; lx++) {
            int j = ly * fw + lx, c = (ly / cs) * cw + lx / cs, at = (ly - y0 + 1) * rw + lx + 1;
            double tot = A[c] + Gs[c];
            wgt[j] = tot > 0 ? (float)(wgt[j] / tot) : 0;
            tooth[j] = (byte)Math.round((toothAt(lx + fx0, ly + fy0) - .55) / .9 * 255);
            // StrokeMath.blur's box (radius 1), clamped at the box's edge.
            double box = 0;
            for (int v = -1; v <= 1; v++) for (int u = -1; u <= 1; u++) box += raw[at + v * rw + u];
            double hgt = .5 * raw[at] + .5 * box / 9;
            H[c] += (float)(hgt * cell);
            if (vf != null) vf[j] = (float)clamp(Math.exp(2.2 * (.5 - hgt)), .35, 2.2);
        }
    }

    /** The prototype's paper height before its blur: .65 fine + .35 mid at page pixel {@code x, y}. */
    private double rawHeight(int x, int y) {
        x = StrokeMath.index(x, W.w); y = StrokeMath.index(y, W.h);
        return .65 * paper.fine(x, y) + .35 * paper.mid(x, y);
    }
    /** How much of arriving pigment pixel {@code j} takes: its tooth, as far as the moving paint follows it ({@link #tk}). */
    private double toothA(int j) { return 1 + tk * ((tooth[j] & 255) * (.9 / 255) + .55 - 1); }
    /** {@link #toothAt}, from the flow's own copy inside its box. */
    private double tooth(int px, int py) {
        int lx = px - fx0, ly = py - fy0;
        return lx >= 0 && ly >= 0 && lx < fw && ly < fh ? (tooth[ly * fw + lx] & 255) * (.9 / 255) + .55 : toothAt(px, py);
    }
    /** How much of arriving pigment a pixel takes: the paper's tooth (fine and mid grain), mean about 1. */
    private double toothAt(int px, int py) {
        px = StrokeMath.index(px, W.w); py = StrokeMath.index(py, W.h);
        return clamp(1 + .7 * (paper.fine(px, py) - .5) + .45 * (paper.mid(px, py) - .5), .55, 1.45);
    }

    /** Smooth value noise over the flow's cells, 0..1, features about {@code s} cells across. Seeded, so a redraw matches. */
    static float[] cellNoise(int cw, int ch, double s, WildBrush.Random r) {
        s = Math.max(1, s);
        int gx = (int)Math.ceil(cw / s) + 2, gy = (int)Math.ceil(ch / s) + 2;
        float[] L = new float[gx * gy], out = new float[cw * ch];
        for (int i = 0; i < L.length; i++) L[i] = (float)r.next();
        for (int y = 0; y < ch; y++) {
            double fy = y / s; int iy = (int)fy; double ty = smooth(fy - iy);
            for (int x = 0; x < cw; x++) {
                double fx = x / s; int ix = (int)fx; double tx = smooth(fx - ix); int k = iy * gx + ix;
                out[y * cw + x] = (float)lerp(lerp(L[k], L[k + 1], tx), lerp(L[k + gx], L[k + gx + 1], tx), ty);
            }
        }
        return out;
    }
    /** Smooth 1D value noise (smoothstep between seeded values): long, gentle curves for a drip's wander. */
    static double smoothNoise(int seed, double x) {
        int i = (int)Math.floor(x);
        return lerp(StrokeMath.hash01(seed, i), StrokeMath.hash01(seed, i + 1), smooth(x - i));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Ticks

    /** A settle deposit (the patch's) lands in the paper at page pixel {@code px, py}: the fixed share, shown at once. */
    void addFixed(int px, int py, double a) {
        int lx = px - fx0, ly = py - fy0;
        if (lx < 0 || ly < 0 || lx >= fw || ly >= fh) { W.mul(px, py, 1 - a); return; }
        // It shows at the next render (this tick or the next), which takes every pixel's paper share as it is: the
        // pixel's share now, so the many tiny deposits of a settling wash cost no page writes of their own.
        fixed[ly * fw + lx] += (float)a; added += a;
        touched[(ly / cs) * cw + lx / cs] = 1;
        pend = true;
    }

    /** Whether it has finished (the patch is dry or nothing is left): its paint is then where the page shows it. */
    boolean done() { return box == null; }

    /**
     * One tick at the patch's age {@code age} on the page tilted {@code G}. Returns true when the wash is dry (the flow
     * is then dropped; the page already holds the result).
     */
    boolean step(int age, PageTilt G) {
        double wetF = clamp(1 - (double)age / N), mob = Math.pow(wetF, 1.3);
        boolean done = age >= N - 1 || box == null;
        if (!done && mob > .01) {
            grow(2);
            long t0 = Prof.now();
            if (G.m > 0) {
                // Water runs with Runs, and (pigment staying mostly behind) to feed the beads Drips need.
                double kw = Math.max(g.runs, .55 * g.drips);
                if ((kw > 0 || g.pool > 0) && age % 2 == 0) excessBlur();
                if (kw > 0) sweep(RUN, G.x * kw * .7 * mob * k, G.y * kw * .7 * mob * k, G);
                // Pool every other tick at twice the rate: pigment is heavier than the water and drifts downhill through it.
                double kp = Math.max(g.pool, Math.max(.6 * g.runs, .45 * g.drips));
                if (kp > 0 && age % 2 == 0) sweep(POOL, G.x * kp * .6 * mob * k, G.y * kp * .6 * mob * k, G);
                t0 = lap(Prof.G_RUN, t0);
                if (kw > 0 || g.pool > 0) pairs(G, kw * mob, 1, .1 * Math.min(1, 3 * mob), g.pool * .12 * mob);
                t0 = lap(Prof.G_PAIRS, t0);
                if (g.pool > 0 || g.runs > 0) edgeFlow(G, (g.pool * .25 + g.runs * .12) * Math.min(1, 2 * mob));
                t0 = lap(Prof.G_EDGE, t0);
                if (g.sink > 0 && age % 2 == 1) sweep(SINK, G.x * g.sink * .6 * mob * k, G.y * g.sink * .6 * mob * k, G);
                if (g.runs > 0 || g.pool > 0) bloomCheck(mob, age);
                t0 = lap(Prof.G_RUN, t0);
            }
            double dg = Math.hypot(G.x - gx, G.y - gy);
            if (g.blend > 0 && dg > .004) blend(G.x - gx, G.y - gy, clamp(g.blend * dg * 6 * mob, 0, 2));
            t0 = lap(Prof.G_PAIRS, t0);
            if (g.drips > 0) { spawnDrips(G, mob, age); stepDrips(G, mob, age % 2 == 1); }
            t0 = lap(Prof.G_DRIPS, t0);
            settle(mob);
            if (age % 6 == 5) tighten();
            t0 = lap(Prof.G_SETTLE, t0);
            // The wash renders every other tick (5 Hz is plenty on e-ink; drips draw every tick), and on its last wet tick.
            if (age % 2 == 0 || age >= N - 2 || mob <= .02) render(); else pend = true;
            Prof.add(Prof.G_RENDER, t0);
        } else if (pend) render();
        gx = G.x; gy = G.y;
        if (done) stopDrips();
        return done;
    }
    /** Dry now (the wet indicator's tap): a render still due shows, and nothing runs any further. */
    void finish() {
        if (pend) render();
        pend = false;
        stopDrips();
        box = null;
    }

    private static long lap(int k, long since) { Prof.add(k, since); return Prof.now(); }
    /** How hard the dry front holds (surface tension): water piles up at the low edge as a bead before it breaks out. */
    private double pinG(PageTilt G) { return PIN_K * (1.3 - .4 * G.m) * (1.6 - 1.1 * g.runs); }
    private void grow(int n) { int[] b = box; b[0] = Math.max(0, b[0] - n); b[1] = Math.max(0, b[1] - n); b[2] = Math.min(cw - 1, b[2] + n); b[3] = Math.min(ch - 1, b[3] + n); }
    private void tighten() {
        int[] b = box;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1;
        for (int cy = b[1]; cy <= b[3]; cy++) for (int cx = b[0], c = cy * cw + b[0]; cx <= b[2]; cx++, c++)
            if (Wt[c] > .01 || A[c] > 1e-5 || Gs[c] > 1e-5) { if (cx < x0) x0 = cx; if (cx > x1) x1 = cx; if (cy < y0) y0 = cy; if (cy > y1) y1 = cy; }
        box = x1 < 0 ? null : new int[]{x0, y0, x1, y1};
    }
    /** Moves the deltas in dW, dA, dG into the cells; a cell that gains pigment from elsewhere spreads it evenly over its points. */
    private void apply() {
        int[] b = box;
        Cores.inBands(b[1], b[3], 8, 4L * (b[2] - b[0] + 1) * (b[3] - b[1] + 1), (top, bottom, worker) -> applyRows(top, bottom));
    }
    private void applyRows(int cy0, int cy1) {
        int[] b = box;
        for (int cy = cy0; cy <= cy1; cy++) for (int cx = b[0], c = cy * cw + b[0]; cx <= b[2]; cx++, c++) {
            float da = dA[c], dw = dW[c], dgg = dG[c];
            if (da == 0 && dw == 0 && dgg == 0) continue;
            dA[c] = 0; dW[c] = 0; dG[c] = 0;
            applyCell(c, cx, cy, da, dw, dgg);
        }
    }
    /** Moves {@code da, dw, dgg} into cell {@code c} (at {@code cx, cy}); its points take a share of pigment that arrived. */
    private void applyCell(int c, int cx, int cy, float da, float dw, float dgg) {
        if (dw != 0) Wt[c] = Math.max(0, Wt[c] + dw);
        if (da == 0 && dgg == 0) return;
        double before = A[c] + Gs[c] + S[c];
        A[c] = Math.max(0, A[c] + da); Gs[c] = Math.max(0, Gs[c] + dgg);
        double gain = (da > 0 ? da : 0) + (dgg > 0 ? dgg : 0), after = A[c] + Gs[c] + S[c];
        if (gain > 0 && after > 0) {
            double rr = before <= 1e-7 ? 1 : Math.min(1, gain / after);
            int x = cx * cs, y = cy * cs;
            for (int v = 0; v < cs; v++) for (int u = 0; u < cs; u++) {
                if (x + u >= fw || y + v >= fh) continue;
                int j = (y + v) * fw + x + u;
                wgt[j] += (float)((toothA(j) - wgt[j]) * rr);
            }
            touched[c] = 1;
        }
    }

    private static final int RUN = 0, POOL = 1, SINK = 2;
    /**
     * Upwind transfer from each cell to its downhill neighbour along x, then along y: Runs, Pool or Sink. What a cell
     * sends is worked out from the cells as they were (it lands at {@link #apply}), so the rows can be shared out: each
     * cell's sending is gathered into its own slot first, then every cell takes what it sent and what it was sent.
     */
    private void sweep(int kind, double ux, double uy, PageTilt G) {
        double[][] axes = {{ux, 1, 0}, {uy, 0, 1}};
        for (double[] axis : axes) {
            double u = axis[0];
            if (Math.abs(u) < 1e-4) continue;
            grow(1);
            int dx = (int)axis[1], dy = (int)axis[2], sg = u > 0 ? 1 : -1, off = dx != 0 ? sg : sg * cw;
            double au = Math.min(.45, Math.abs(u)), capR = capA * 1.7, pinG = pinG(G);
            int[] b = box;
            // Each cell's sending: water, pigment, grain (outW, outA, outG are tmp, P2, P3; filled 0 first).
            Cores.inBands(b[1], b[3], 8, 4L * (b[2] - b[0] + 1) * (b[3] - b[1] + 1), (top, bottom, worker) -> {
                for (int cy = top; cy <= bottom; cy++) {
                    int ny = cy + dy * sg;
                    for (int cx = b[0], c = cy * cw + b[0]; cx <= b[2]; cx++, c++) {
                        tmp[c] = 0; P2[c] = 0; P3[c] = 0;
                        int nx = cx + dx * sg;
                        if (ny < 0 || ny >= ch || nx < 0 || nx >= cw) continue;
                        int n = c + off;
                        if (kind == RUN) run(c, n, au, capR, pinG);
                        else if (kind == POOL) pool(c, n, au);
                        else sink(c, n, au);
                    }
                }
            });
            // Then each cell takes what it was sent less what it sent, in the prototype's order (−own, +from upwind),
            // and applies it at once: a cell's sending was worked out above, so the rows don't wait for each other.
            Cores.inBands(b[1], b[3], 8, 4L * (b[2] - b[0] + 1) * (b[3] - b[1] + 1), (top, bottom, worker) -> {
                for (int cy = top; cy <= bottom; cy++) {
                    int py = cy - dy * sg;
                    for (int cx = b[0], c = cy * cw + b[0]; cx <= b[2]; cx++, c++) {
                        int px = cx - dx * sg, p = c - off, nx = cx + dx * sg, ny = cy + dy * sg;
                        boolean from = py >= b[1] && py <= b[3] && px >= b[0] && px <= b[2], to = ny >= b[1] && ny <= b[3] && nx >= b[0] && nx <= b[2];
                        // What would cross the box's edge stays (the cells there are dry), so nothing is lost.
                        float ow = to ? tmp[c] : 0, oa = to ? P2[c] : 0, og = to ? P3[c] : 0;
                        if (ow == 0 && oa == 0 && og == 0 && !(from && (tmp[p] != 0 || P2[p] != 0 || P3[p] != 0))) continue;
                        // An upwind sender before this cell in the prototype's order (sending downward or right) adds after its own sending.
                        float w = 0, a = 0, gr = 0;
                        if (from && off > 0) { w += tmp[p]; a += P2[p]; gr += P3[p]; }
                        w -= ow; a -= oa; gr -= og;
                        if (from && off < 0) { w += tmp[p]; a += P2[p]; gr += P3[p]; }
                        applyCell(c, cx, cy, a, w, gr);
                    }
                }
            });
        }
    }
    /**
     * Runs from cell {@code c} to {@code n}: what drives a cell is its load (water + LOAD_K × pigment) above what its
     * paper holds. Thin water barely moves, thick water runs; valleys pass it faster. At the dry front surface tension
     * holds a bead until the load beats the cell's pin, so the front breaks out in lobes and fingers.
     */
    private void run(int c, int n, double u, double capR, double pinG) {
        double w = Wt[c], hc = hold[c];
        if (w <= hc * .5) return;
        double ex = w + LOAD_K * (A[c] + .5 * Gs[c]) - hc;
        if (ex <= 0) return;
        // The pin is judged on the load across a few cells, so the front breaks in lobes, not single-cell hairs.
        if (Wt[n] < .06 && Eb[c] + .3 * LOAD_K * A[c] < pin[c] * pinG) return;
        double v = Math.min(.45, u * cond[c] * smooth(ex / .5)), F = Math.min(w - hc * .5, ex) * v;
        if (F <= 0) return;
        // A front running onto fresh paper takes more of its pigment along (the edge flow that darkens a drying edge).
        double f = Math.min(.85, F / w * (Wt[n] < hc ? 5 : 1));
        tmp[c] = (float)F;
        double room = Math.max(.15, 1 - A[n] / capR);
        if (A[c] > 0) P2[c] = (float)(A[c] * f * room);
        if (Gs[c] > 0) P3[c] = (float)(Gs[c] * f * .5 * room);
    }
    /** Pool: pigment slides downhill inside the wet area; how much a cell takes varies (pot), so the low edge beads. */
    private void pool(int c, int n, double u) {
        if (A[c] <= 0 || Wt[c] < .05 || Wt[n] < hold[n] * .4) return;
        double cn = capA * (.3 + 1.6 * pot[n] * pot[n]);
        P2[c] = (float)(A[c] * u * Math.min(1.5, cond[c]) * Math.max(0, 1 - A[n] / cn));
    }
    /** Sink: the granulating share drifts downhill, held back in valleys. */
    private void sink(int c, int n, double u) {
        if (Gs[c] <= 0 || Wt[n] < .2) return;
        P3[c] = (float)(Gs[c] * u * smoothstep(.25, .65, H[c]) * Math.max(0, 1 - Gs[n] / capG));
    }

    /**
     * One pass over neighbouring cells (right and down), three things at once: water evens out (its own pressure), more
     * across the slope than along it, and leans into the paper's valleys; pigment in wet paint mixes a little; with Pool
     * it drifts towards pot's high points. Onto dry paper only where the blurred excess beats the pin.
     */
    private void pairs(PageTilt G, double k, double carry, double km, double kg) {
        int[] b = box;
        double m = G.m > 0 ? G.m : 1, wx = .45 + .55 * (G.y / m) * (G.y / m), wy = .45 + .55 * (G.x / m) * (G.x / m);
        double kk = .3 * Math.min(1, k * 2.5), cap = capA, pinG = pinG(G);
        if (eDw == null) { eDw = new float[cw * ch]; eDa = new float[cw * ch]; eDg = new float[cw * ch]; }
        long area = 4L * (b[2] - b[0] + 1) * (b[3] - b[1] + 1);
        // Each cell's two pairs (right, down) worked out from the cells as they were: what moves across each, from the
        // cell to its neighbour (water, pigment, grain), into tmp, P2, P3 (right) and eDw, eDa, eDg (down).
        Cores.inBands(b[1], b[3], 8, area, (top, bottom, worker) -> {
            for (int cy = top; cy <= bottom; cy++) for (int cx = b[0], c = cy * cw + b[0]; cx <= b[2]; cx++, c++) {
                tmp[c] = P2[c] = P3[c] = eDw[c] = eDa[c] = eDg[c] = 0;
                double wc = Wt[c];
                if (wc < .06 && cx + 1 < cw && Wt[c + 1] < .06 && (cy + 1 >= ch || Wt[c + cw] < .06)) continue;
                for (int s2 = 0; s2 < 2; s2++) {
                    int n = s2 == 1 ? (cy + 1 < ch ? c + cw : -1) : (cx + 1 < cw ? c + 1 : -1);
                    if (n < 0) continue;
                    double ec = wc - hold[c], en = Wt[n] - hold[n], ea = 0, ew = 0, eg = 0;
                    if (wc >= hold[c] * .7 && Wt[n] >= hold[n] * .7) {
                        double fa = (A[c] - A[n]) * km, fg = (Gs[c] - Gs[n]) * km * .5;
                        if (kg > 0) {
                            double d = pot[n] - pot[c]; int dst = d > 0 ? n : c;
                            double room = 1 - A[dst] / (cap * (.35 + 1.1 * pot[dst]));
                            if (room > 0) fa += (d > 0 ? A[c] : -A[n]) * Math.min(.2, Math.abs(d) * 4 * kg) * room;
                        }
                        ea = fa; eg = fg;
                    }
                    if (!(k <= 0 || (ec <= 0 && en <= 0))) {
                        double F = ((ec > 0 ? ec : 0) - (en > 0 ? en : 0) + 1.5 * (Hs[n] - Hs[c])) * kk * (s2 == 1 ? wy : wx);
                        int src = F > 0 ? c : n, dst = F > 0 ? n : c;
                        double sign = F > 0 ? 1 : -1;
                        F = Math.abs(F);
                        double es = Wt[src] - hold[src];
                        if (es > 0 && !(Wt[dst] < .06 && Eb[src] < pin[src] * pinG)) {
                            F = Math.min(F, es * .25);
                            // Pigment lags water pushed uphill.
                            boolean up = (s2 == 1 ? G.y : G.x) * (dst > src ? 1 : -1) < 0;
                            double f = F / Wt[src] * (up ? .35 : 1);
                            ew += sign * F;
                            if (A[src] > 0) ea += sign * A[src] * f * carry;
                            if (Gs[src] > 0) eg += sign * Gs[src] * f * .5 * carry;
                        }
                    }
                    if (s2 == 0) { tmp[c] = (float)ew; P2[c] = (float)ea; P3[c] = (float)eg; }
                    else { eDw[c] = (float)ew; eDa[c] = (float)ea; eDg[c] = (float)eg; }
                }
            }
        });
        takePairs(b, area);
    }
    /**
     * Each cell in box {@code b} takes what crossed its four pairs this pass (right: tmp, P2, P3; down: eDw, eDa, eDg,
     * each from the cell to its neighbour) and applies it at once, on several cores.
     */
    private void takePairs(int[] b, long area) {
        Cores.inBands(b[1], b[3], 8, area, (top, bottom, worker) -> {
            for (int cy = top; cy <= bottom; cy++) for (int cx = b[0], c = cy * cw + b[0]; cx <= b[2]; cx++, c++) {
                float w = 0, a = 0, gr = 0;
                if (cy > b[1]) { w += eDw[c - cw]; a += eDa[c - cw]; gr += eDg[c - cw]; }
                if (cx > b[0]) { w += tmp[c - 1]; a += P2[c - 1]; gr += P3[c - 1]; }
                // A pair across the box's edge moves nothing (both cells are dry there), so nothing is lost.
                if (cx < b[2]) { w -= tmp[c]; a -= P2[c]; gr -= P3[c]; }
                if (cy < b[3]) { w -= eDw[c]; a -= eDa[c]; gr -= eDg[c]; }
                if (w != 0 || a != 0 || gr != 0) applyCell(c, cx, cy, a, w, gr);
            }
        });
    }
    /** The excess water averaged over a 5-wide then a 3-wide box of cells: the front is judged on it, so it breaks in round lobes. */
    private void excessBlur() {
        int[] b = box;
        int x0 = Math.max(0, b[0] - 4), x1 = Math.min(cw - 1, b[2] + 4), y0 = Math.max(0, b[1] - 4), y1 = Math.min(ch - 1, b[3] + 4);
        for (int y = y0; y <= y1; y++) for (int c = y * cw + x0, e = y * cw + x1; c <= e; c++) { double v = Wt[c] - hold[c]; Eb[c] = v > 0 ? (float)v : 0; }
        for (int rr = 2; rr >= 1; rr--) {
            int R = rr;
            double k = 1.0 / (2 * R + 1);
            Cores.inBands(y0, y1, 8, 2L * (x1 - x0 + 1) * (y1 - y0 + 1), (top, bottom, worker) -> {
                for (int y = top; y <= bottom; y++) {
                    int row = y * cw;
                    double sum = 0;
                    for (int u = x0; u <= Math.min(x1, x0 + R); u++) sum += Eb[row + u];
                    for (int x = x0; x <= x1; x++) {
                        tmp[row + x] = (float)(sum * k);
                        if (x + R + 1 <= x1) sum += Eb[row + x + R + 1];
                        if (x - R >= x0) sum -= Eb[row + x - R];
                    }
                }
            });
            Cores.inBands(x0, x1, 8, 2L * (x1 - x0 + 1) * (y1 - y0 + 1), (left, right, worker) -> {
                for (int x = left; x <= right; x++) {
                    double sum = 0;
                    for (int v = y0; v <= Math.min(y1, y0 + R); v++) sum += tmp[v * cw + x];
                    for (int y = y0; y <= y1; y++) {
                        Eb[y * cw + x] = (float)(sum * k);
                        if (y + R + 1 <= y1) sum += tmp[(y + R + 1) * cw + x];
                        if (y - R >= y0) sum -= tmp[(y - R) * cw + x];
                    }
                }
            });
        }
    }
    /**
     * Edge flow: water evaporating at the wet edge draws pigment out to it (the darker line a wash dries with), most at
     * the low edge, from up to four cells inside, laid over a band two cells deep, beaded along the edge by pot.
     */
    private void edgeFlow(PageTilt G, double k) {
        int[] b = box;
        double m = G.m > 0 ? G.m : 1, ux = G.x / m, uy = G.y / m, cap = capA * 1.15;
        for (int cy = Math.max(1, b[1]); cy <= Math.min(ch - 2, b[3]); cy++) for (int cx = Math.max(1, b[0]), c = cy * cw + cx; cx <= Math.min(cw - 2, b[2]); cx++, c++) {
            if (Wt[c] < .06) continue;
            double wsum = 0; int ix = 0, iy = 0;
            if (Wt[c + 1] < .06) { wsum += Math.max(.2, ux); ix -= 1; }
            if (Wt[c - 1] < .06) { wsum += Math.max(.2, -ux); ix += 1; }
            if (Wt[c + cw] < .06) { wsum += Math.max(.2, uy); iy -= 1; }
            if (Wt[c - cw] < .06) { wsum += Math.max(.2, -uy); iy += 1; }
            if (wsum == 0 || (ix == 0 && iy == 0)) continue;
            double room = 1 - A[c] / (cap * (.45 + 1.4 * pot[c] * pot[c]));
            if (room <= 0) continue;
            int st = Math.abs(ix) >= Math.abs(iy) ? Integer.signum(ix) : Integer.signum(iy) * cw;
            double a = Math.min(.25, k * wsum * room), got = 0;
            int depth = 0;
            for (int q = 1, n = c + st; q <= 4; q++, n += st) {
                int ny = Math.floorDiv(n, cw), nx = n - ny * cw;
                if (ny < 0 || ny >= ch || nx < 0 || nx >= cw || Wt[n] < .06) break;
                double t = A[n] * a * EDGE_TAPER[q];
                dA[n] -= t; got += t; depth = q;
            }
            if (depth >= 2) { dA[c] += got * .55; dA[c + st] += got * .3; dA[c + 2 * st] += got * .15; } else dA[c] += got;
        }
        apply();
    }
    /** Drying, unevenly: each cell loses water at its own rate, and its mobile pigment settles into the paper at its own rate. */
    private void settle(double mob) {
        int[] b = box;
        double kd = .004 + .02 * (1 - mob);
        Cores.inBands(b[1], b[3], 8, 4L * (b[2] - b[0] + 1) * (b[3] - b[1] + 1), (top, bottom, worker) -> {
            for (int cy = top; cy <= bottom; cy++) for (int c = cy * cw + b[0], e = cy * cw + b[2]; c <= e; c++) {
                double w = Wt[c];
                if (w > 0) Wt[c] = (float)(w - w * evap[c]);
                // Water that ran onto fresh paper keeps its pigment moving to the lobe's or finger's end.
                double lo = w < .04 ? 12 : liq > 0 && W0[c] < .06 ? .35 : w < hold[c] ? 2.5 : 1;
                if (A[c] > 0) { double d = A[c] * Math.min(1, kd * dep[c] * lo); A[c] -= (float)d; S[c] += (float)d; }
                if (Gs[c] > 0) { double d = Gs[c] * Math.min(1, kd * (.4 + 2.4 * clamp(1 - 2 * H[c] + .5)) * lo); Gs[c] -= (float)d; Sg[c] += (float)d; }
            }
        });
    }
    /** Rocking: diffusion along the change of tilt ({@code dx, dy}), stronger the bigger the change; water too, so edges soften. */
    private void blend(double dx, double dy, double sk) {
        int[] b = box;
        double m = dx * dx + dy * dy; if (m == 0) m = 1;
        double wx = .2 + .8 * dx * dx / m, wy = .2 + .8 * dy * dy / m;
        int n = (int)Math.min(6, Math.ceil(sk / .2));
        double k = sk / n;
        if (eDw == null) { eDw = new float[cw * ch]; eDa = new float[cw * ch]; eDg = new float[cw * ch]; }
        for (int it = 0; it < n; it++) {
            grow(1);
            long area = 4L * (b[2] - b[0] + 1) * (b[3] - b[1] + 1);
            // Each cell's two pairs (right into tmp, P2, P3; down into eDw, eDa, eDg), from the cells as they were.
            Cores.inBands(b[1], b[3], 8, area, (top, bottom, worker) -> {
                for (int cy = top; cy <= bottom; cy++) for (int cx = b[0], c = cy * cw + b[0]; cx <= b[2]; cx++, c++) {
                    tmp[c] = P2[c] = P3[c] = eDw[c] = eDa[c] = eDg[c] = 0;
                    for (int s2 = 0; s2 < 2; s2++) {
                        int nb = s2 == 0 ? (cx + 1 < cw ? c + 1 : -1) : (cy + 1 < ch ? c + cw : -1);
                        if (nb < 0 || (Wt[c] < .15 && Wt[nb] < .15)) continue;
                        double kk = k * (s2 == 0 ? wx : wy) * .5;
                        float fa = (float)((A[c] - A[nb]) * kk), fw2 = (float)((Wt[c] - Wt[nb]) * kk), fg = (float)((Gs[c] - Gs[nb]) * kk * .5);
                        if (s2 == 0) { tmp[c] = fw2; P2[c] = fa; P3[c] = fg; } else { eDw[c] = fw2; eDa[c] = fa; eDg[c] = fg; }
                    }
                }
            });
            takePairs(b, area);
        }
        // Rocking also washes the loose pigment out of the brush's own texture within each wet cell: smoother, softer paint.
        double kt = Math.min(.1, sk * .0015);
        for (int cy = b[1]; cy <= b[3]; cy++) for (int cx = b[0], c = cy * cw + b[0]; cx <= b[2]; cx++, c++) {
            if (Wt[c] < .15 || A[c] <= 0) continue;
            touched[c] = 1;
            for (int v = 0; v < cs; v++) for (int u = 0; u < cs; u++) {
                int x = cx * cs + u, y = cy * cs + v;
                if (x >= fw || y >= fh) continue;
                int j = y * fw + x;
                if (wgt[j] > 0) wgt[j] += (float)((toothA(j) - wgt[j]) * kt);
            }
        }
    }

    // ---------- blooms (backruns) ----------

    /** Where thick moving water meets a cell that is already drying: a pale centre with a darker, crinkled rim that dries hard. */
    private void bloomCheck(double mob, int age) {
        if (age % 3 != 0 || mob < .15 || mob > .85 || blooms.size() >= maxBlooms) return;
        if (r.next() > Math.max(g.runs, g.pool) * .45) return;
        int[] b = box;
        int best = -1; double bs = 0;
        for (int cy = Math.max(1, b[1]); cy <= Math.min(ch - 2, b[3]); cy++) for (int cx = Math.max(1, b[0]), c = cy * cw + cx; cx <= Math.min(cw - 2, b[2]); cx++, c++) {
            double w = Wt[c];
            if (w < .1 || w > hold[c] * .8 || A[c] + S[c] <= 0) continue;
            double push = Math.max(Math.max(Wt[c - 1] - hold[c - 1], Wt[c + 1] - hold[c + 1]), Math.max(Math.max(Wt[c - cw] - hold[c - cw], Wt[c + cw] - hold[c + cw]), 0));
            if (push < .3) continue;
            boolean near = false;
            for (double[] bl : blooms) if (Math.abs(bl[0] - cx) + Math.abs(bl[1] - cy) < bl[2] * 2.5) { near = true; break; }
            if (near) continue;
            double sc = push * (.3 + nEdge[c]);
            if (sc > bs) { bs = sc; best = c; }
        }
        if (best >= 0) bloom(best % cw, best / cw);
    }
    private void bloom(int bx, int by) {
        double R = clamp(thick * .06 * (.6 + .8 * r.next()), 2.5, 16) * k;
        int lobes = 5 + (int)(r.next() * 5);
        double p1 = r.next() * 6.283, p2 = r.next() * 6.283, a1 = .12 + .1 * r.next();
        int RR = (int)Math.ceil(R * 1.4) + 1;
        List<Integer> rim = new ArrayList<>();
        double pool = 0;
        blooms.add(new double[]{bx, by, R});
        grow(RR);
        for (int v = -RR; v <= RR; v++) for (int u = -RR; u <= RR; u++) {
            int x = bx + u, y = by + v;
            if (x < 0 || y < 0 || x >= cw || y >= ch) continue;
            int c = y * cw + x;
            double d = Math.hypot(u, v), th = Math.atan2(v, u), rho = R * (1 + a1 * Math.sin(lobes * th + p1) + .07 * Math.sin((2 * lobes + 3) * th + p2));
            if (A[c] + S[c] <= 0 && Wt[c] < .06) continue;
            if (d < rho - 1) { double t = A[c] * .7 * (1 - .4 * d / rho); A[c] -= (float)t; pool += t; Wt[c] += .15f; }
            else if (d < rho + .7) rim.add(c);
        }
        if (rim.isEmpty() || pool <= 0) return;
        double each = pool / rim.size();
        for (int c : rim) dA[c] += (float)each;
        apply();
        for (int c : rim) { double a = Math.min(A[c], each * 1.2); A[c] -= (float)a; S[c] += (float)a; }
    }

    // ---------- drips ----------

    /**
     * Drips' scale, set when the first could start: a drip's width (pixels, from the wash's thickness and its load), the
     * spacing of drips along the low edge (the bead's natural wavelength) and where along the edge that spacing starts.
     */
    private double dripW, lam, phase;
    /** The spacing of drips along the low edge: drips' widths, and at least this many pixels (a thin line drips sparsely). */
    static final double DRIP_SPACING = 4, DRIP_SPACING_PX = 90;
    /** How far a stretch's middle may sit off the regular spacing (of the spacing, each way); a pair's gap (drips' widths). */
    static final double DRIP_JITTER = .17, TWIN_GAP = 1.2;
    /** A stretch of the edge drips once, not at all, or twice side by side ({@link #stretchKind}). */
    private static final int ONE = 0, NONE = 1, TWIN = 2;
    /** Seeded by stretch: about one in eight drips not at all (never two in a row), one in seven twice side by side. */
    private int stretchKind(int bin) {
        double u = StrokeMath.hash01(seed ^ 0x1b873593, bin);
        return u < .12 ? (StrokeMath.hash01(seed ^ 0x1b873593, bin - 1) < .12 ? ONE : NONE) : u > .86 ? TWIN : ONE;
    }
    /** A stretch's (or a pair's one side's) heaviness, 0 to 1, seeded: how much paint its bead gathers. */
    private double heavy(int bin, int slot) { return StrokeMath.hash01(seed ^ 0x27d4eb2f, bin * 2 + slot); }

    /**
     * Drips form along the low edge, where the bead of pooled paint is heaviest, as a wet edge on a tilted page breaks:
     * the bead's instability has a natural wavelength ({@link #lam}, about {@link #DRIP_SPACING} drips' widths), so the
     * edge breaks at fairly regular places, each a stretch of the edge whose bead has gathered enough water (a threshold
     * lower with more tilt and more Drips). One drip a stretch; it takes the stretch's water above what the paper holds,
     * and its pigment, most from the middle of the stretch.
     */
    private void spawnDrips(PageTilt G, double mob, int age) {
        if (G.m < .12 || mob < .2 || age % 2 != 0) return;
        if (dripW == 0) {
            double wet = clamp(e0 / .6, .3, 1.2);
            dripW = clamp(thick * (.17 + .09 * wet) * (.85 + .3 * g.drips), 3, .45 * thick);
            lam = Math.max(dripW * DRIP_SPACING, DRIP_SPACING_PX);
            phase = StrokeMath.hash01(seed, 0x51ed) * lam;
        }
        int[] b = box;
        double ux = G.x / G.m, uy = G.y / G.m, px = -uy, py = ux;
        // Every stretch along the wash already has its drip: nothing more to look for.
        double across = Math.abs(px) * (b[2] - b[0] + 1) * cs + Math.abs(py) * (b[3] - b[1] + 1) * cs;
        if (dripStarts.size() > across / lam + 1) return;
        int ox = (int)Math.round(ux * 1.45), oy = (int)Math.round(uy * 1.45);
        int rc = Math.max(1, (int)Math.round(dripW * .3 / cs));
        // Water arrives at the edge a pixel's width a tick: in a lattice's wider cells the bead stands lower by k.
        double thr = Math.max(.25, e0 * 5.5 * (1.45 - .45 * G.m) * (1.3 - .45 * g.drips)) * k * k;
        int best = -1, bBin = 0, bSlot = 0, bKind = 0; double bs = 0;
        for (int cy = b[1]; cy <= b[3]; cy++) for (int cx = b[0], c = cy * cw + b[0]; cx <= b[2]; cx++, c++) {
            if (Wt[c] <= hold[c] + .05) continue;
            int nx = cx + ox, ny = cy + oy;
            if (nx < 0 || ny < 0 || nx >= cw || ny >= ch || Wt[ny * cw + nx] > .06) continue;
            // Only near the middle of its stretch of the edge (each stretch's middle off the regular spacing; a stretch
            // now and then has none, or two side by side that soon run together).
            double ex = fx0 + cx * cs + cs * .5, ey = fy0 + cy * cs + cs * .5, q = (ex * px + ey * py - phase) / lam;
            int bin = (int)Math.floor(q), kind = stretchKind(bin), slot = 0;
            if (kind == NONE) continue;
            double mid = .5 + 2 * DRIP_JITTER * (StrokeMath.hash01(seed ^ 0x3c6ef372, bin) - .5);
            if (kind == TWIN) { slot = q - bin < mid ? 0 : 1; mid += (slot == 0 ? -.5 : .5) * TWIN_GAP * dripW / lam; }
            if (Math.abs(q - bin - mid) > (kind == TWIN ? .06 : .13)) continue;
            boolean taken = false;
            for (double[] st : dripStarts)
                if (st[2] == bin ? st[3] == slot : Math.hypot(st[0] - ex, st[1] - ey) < .5 * lam) { taken = true; break; }
            if (taken) continue;
            double v = 0;
            for (int y = Math.max(0, cy - rc); y <= Math.min(ch - 1, cy + rc); y++) for (int x = Math.max(0, cx - rc), j = y * cw + x; x <= Math.min(cw - 1, cx + rc); x++, j++) {
                double e = Wt[j] - W0[j];
                if (e > 0) v += e;
            }
            // A heavier stretch breaks sooner.
            double sc = v / (2 * rc + 1) / (1.2 - .4 * heavy(bin, slot));
            if (sc > bs) { bs = sc; best = c; bBin = bin; bSlot = slot; bKind = kind; }
        }
        if (best < 0 || bs < thr) return;
        int bx = best % cw, by = best / cw;
        // It drains its stretch of the edge: across it, and back into the wash about a drip's width; most from the middle
        // (a pair splits the stretch).
        double half = bKind == TWIN ? .6 * TWIN_GAP * dripW : .5 * lam;
        double[] got = drain(bx, by, ux, uy, .85, half);
        double vol = got[0], pig = got[1];
        if (vol * cs * cs < 6) { Wt[best] += (float)vol; A[best] += (float)pig; return; }
        // In pixels of water and pixels of darkness.
        vol *= cs * cs; pig *= cs * cs; dripPig += pig;
        // Its tone: pigment collects in a drip, so darker than the pooled edge it leaves (never lighter than the wash).
        if (washTone < 0) {
            double sum = 0; int n = 0;
            for (int ly = 0; ly < fh; ly += 3) for (int lx = 0; lx < fw; lx += 3)
                if (cur[ly * fw + lx] > .05) { sum += W.dark(lx + fx0, ly + fy0); n++; }
            washTone = n > 0 ? sum / n : .2;
        }
        double dk = 0; int dn = 0, R2 = (int)Math.round(dripW * .5) + 2;
        for (int v = -R2; v <= R2; v += 2) for (int u = -R2; u <= R2; u += 2) {
            int lx = bx * cs + u, ly = by * cs + v;
            if (lx < 0 || ly < 0 || lx >= fw || ly >= fh) continue;
            if (cur[ly * fw + lx] > .05) { dk += W.dark(lx + fx0, ly + fy0); dn++; }
        }
        // How much paint the stretch holds: its edge's darkness against the wash's, with the stretch's own heaviness (the
        // bead never gathers evenly). A heavier drip runs longer, a little broader and darker; a light one barely starts.
        double edge = Math.max(dn > 0 ? dk / dn : 0, washTone), pr = clamp(edge / Math.max(.02, washTone), .8, 1.3);
        double hv = heavy(bBin, bSlot), load = pr * pr * (.15 + 1.55 * Math.pow(hv, 1.2)), ld = clamp((load - .15) / 1.55);
        double tone = clamp(edge * 1.45 * (.86 + .22 * ld) * (.97 + .06 * r.next()) + .05, .12, deep(.8));
        double w0 = dripW * (.88 + .22 * ld) * (.92 + .16 * r.next());
        dripStarts.add(new double[]{fx0 + bx * cs + cs * .5, fy0 + by * cs + cs * .5, bBin, bSlot});
        Drip d = new Drip();
        d.x = d.x0 = d.lx = fx0 + bx * cs + cs * .5; d.y = d.y0 = d.ly = fy0 + by * cs + cs * .5; d.dx = d.ux0 = ux; d.dy = d.uy0 = uy;
        d.vol = d.vol0 = vol; d.pig = pig; d.w0 = d.w0t = w0; d.tone = tone; d.edge = Math.min(edge, tone); d.max = Double.POSITIVE_INFINITY;
        // The bead behind keeps feeding it from its stretch while the head is near the wash.
        d.src = new int[]{bx, by}; d.half = half;
        // Its shape along the run: a gentle seeded swell and pinch.
        d.seed = seed ^ (dripStarts.size() * 0x9e3779b1);
        d.lk = 1 / load; d.load = load; d.wa = .045 + .03 * r.next(); d.wl = w0 * (3 + 2 * r.next()); d.ws = d.seed ^ 0x5bd1e995;
        // Most run straight down; now and then one leans a little; a pair leans together and joins.
        double ln = StrokeMath.hash01(seed ^ 0x85ebca6b, bBin * 2 + bSlot);
        d.bias = bKind == TWIN ? (bSlot == 0 ? 1 : -1) * (.07 + .03 * ln) : (r.next() - .5) * .012 + (ln < .3 ? (ln < .15 ? -1 : 1) * (.035 + .2 * Math.abs(ln - .15)) : 0);
        dripWidth(d);
        d.lr = d.w * .5; d.lt = d.edge;
        drips.add(d); log.add(d);
    }
    /**
     * Takes {@code share} of the water above (0.9 of) what the paper holds from the stretch of the low edge a drip at cell
     * {@code bx, by} drains (across it half the spacing each way, back into the wash about a drip's width; most from its
     * middle), with the pigment that water holds: {water, pigment} in cells.
     */
    private double[] drain(int bx, int by, double ux, double uy, double share, double halfPx) {
        double px = -uy, py = ux, half = halfPx / cs, deep = Math.max(2, 1.2 * dripW / cs), vol = 0, pig = 0;
        // The stretch's box: across it half the spacing each way, from 1.5 cells past the edge to deep inside.
        double sx = Math.abs(px) * half + Math.abs(ux) * deep, sy = Math.abs(py) * half + Math.abs(uy) * deep;
        int x0 = Math.max(0, (int)Math.floor(bx - sx) - 2), x1 = Math.min(cw - 1, (int)Math.ceil(bx + sx) + 2), y0 = Math.max(0, (int)Math.floor(by - sy) - 2), y1 = Math.min(ch - 1, (int)Math.ceil(by + sy) + 2);
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
            double ax = Math.abs((x - bx) * px + (y - by) * py), al = (bx - x) * ux + (by - y) * uy;
            if (ax > half || al < -1.5 || al > deep) continue;
            int c = y * cw + x;
            double ex = (Wt[c] - hold[c] * .9) * share * (1 - .65 * smoothstep(.35 * half, half, ax)) * (1 - .7 * smoothstep(.3 * deep, deep, al));
            if (ex <= 0) continue;
            double f = ex / Wt[c];
            vol += ex; Wt[c] -= (float)ex;
            // Some of the pigment stays in the paper: a drained stretch lightens a little, not to a notch.
            double a = A[c] * f * .6; A[c] -= (float)a; pig += a;
        }
        return new double[]{vol, pig};
    }
    /** Moves the drips a tick; {@code show}: their trails and beads may be drawn this tick (every other one, between the wash's renders). */
    private void stepDrips(PageTilt G, double mob, boolean show) {
        for (Drip d : drips) {
            if (d.done) continue;
            // While it is close to the wash, the bead behind keeps feeding it water and the pigment in that water.
            if (d.src != null && d.len < thick * 1.2 && G.m >= .1) {
                double[] got = drain(d.src[0], d.src[1], G.x / G.m, G.y / G.m, .06, d.half);
                double area = cs * cs, f = got[0], p = got[1];
                d.vol += Math.min(f * area, Math.max(0, d.vol0 * (1 + g.drips) - d.fed)); d.fed += f * area; d.pig += p * area; dripPig += p * area;
            } else if (d.len >= thick * 1.2) d.src = null;
            // A level page stops the drip where it is.
            if (G.m < .1) { d.v = 0; continue; }
            double ux = G.x / G.m, uy = G.y / G.m, px = -uy, py = ux, share = Math.max(0, d.vol) / d.vol0;
            // Its speed follows its paint and the tilt; it slows as it runs out or the paint dries, and stops with a bead.
            double drive = G.m * .7 * (.3 + .7 * Math.sqrt(Math.min(1.5, share))) * Math.pow(d.w0 / 25, .3) * (.3 + .7 * mob) * smoothstep(.04, .22, share) * (.7 + .3 * Math.min(1.5, d.load));
            d.v = Math.min(7, d.v + drive - .16 * d.v);
            if (d.v < .03 && share < .3) { d.done = true; continue; }
            // Steps of at most 1 pixel for its heading; the trail is noted every half its width (3 pixels at least) and at the tick's end.
            int n = Math.max(1, (int)Math.ceil(d.v));
            double st = d.v / n, loss = DRIP_LOSS * (2.4 - 1.5 * G.m) * (1.5 - .8 * g.drips);
            // Straight down the page: only a tiny wander from the paper's grain, all but none on a steep page.
            double wander = .004 + .06 * (1 - G.m) * (1 - G.m), pull = .03 + .15 * (1 - G.m), lam = 40 + 5 * d.w0;
            for (int k = 0; k < n && !d.done; k++) {
                double hw = Math.max(1.5, d.w * .5), hl = hAt(d.x + px * hw, d.y + py * hw), hr = hAt(d.x - px * hw, d.y - py * hw);
                double lat = (hr - hl) * pull + (smoothNoise(d.seed, d.len / lam) - .5) * 2 * wander + d.bias * (1.2 - .5 * G.m);
                double tx = ux + px * lat, ty = uy + py * lat, tm = Math.sqrt(tx * tx + ty * ty);
                if (tm == 0) tm = 1;
                tx /= tm; ty /= tm;
                d.dx += (tx - d.dx) * .035; d.dy += (ty - d.dy) * .035;
                double dm = Math.sqrt(d.dx * d.dx + d.dy * d.dy);
                if (dm == 0) dm = 1;
                d.dx /= dm; d.dy /= dm;
                d.x += d.dx * st; d.y += d.dy * st; d.len += st;
                if (d.len - d.pl >= 6) { d.addPath(d.x, d.y, d.w); d.pl = d.len; }
                if (d.x < 1 || d.y < 1 || d.x >= pw - 1 || d.y >= ph - 1) { d.done = true; break; }
                d.vol -= st * loss * d.lk * d.w;
                // A merge widens it gradually.
                if (d.w0t > d.w0) d.w0 += Math.min(d.w0t - d.w0, (d.w0t - d.w0) * .05 * st + .02 * st);
                // Its width is worked out where its trail is noted (every few pixels), not every pixel.
                double mx = d.x - (d.queueN > 0 ? d.queue[d.queueN - 6] : d.lx), my = d.y - (d.queueN > 0 ? d.queue[d.queueN - 5] : d.ly), step = Math.max(3, .5 * d.w);
                if (mx * mx + my * my >= step * step || k == n - 1) { dripWidth(d); layTrail(d); }
                if (d.vol < Math.max(3, d.vol0 * .03) || d.len > d.max) d.done = true;
            }
        }
        // Drips that meet merge: heads that touch (the bigger takes the other's water), and a head that runs into another
        // live drip's wet trail joins it.
        List<Drip> live = new ArrayList<>();
        for (Drip d : drips) if (!d.done) live.add(d);
        for (int i = 0; i < live.size(); i++) for (int j = 0; j < live.size(); j++) {
            Drip a = live.get(i), b = live.get(j);
            if (i == j || a.done || b.done) continue;
            Drip keep = null, other = null;
            double jx = 0, jy = 0;
            if (j > i && Math.hypot(a.x - b.x, a.y - b.y) <= (a.w + b.w) * .45 + 1) { keep = a.vol >= b.vol ? a : b; other = keep == a ? b : a; jx = keep.x; jy = keep.y; }
            else {
                float[] P = b.path;
                double rr = a.w * .45 + 1;
                for (int k = 0; k + 2 < b.pathN - 3; k += 3) if (Math.hypot(a.x - P[k], a.y - P[k + 1]) <= rr + P[k + 2] * .45) { keep = b; other = a; break; }
                // It joins the other's middle a little way ahead, so it bends in gently (or where they touch, if none is ahead).
                if (keep != null) {
                    double near = Double.MAX_VALUE;
                    for (int k = 0; k + 2 < b.pathN; k += 3) {
                        double ox = P[k] - a.x, oy = P[k + 1] - a.y, al = ox * a.dx + oy * a.dy, e = Math.abs(al - .8 * a.w) + (al < 0 ? 1e3 : 0) + Math.abs(ox * a.dy - oy * a.dx) * .01;
                        if (e < near) { near = e; jx = P[k]; jy = P[k + 1]; }
                    }
                }
            }
            if (keep == null) continue;
            // It runs on into the other: its trail reaches into the other's middle (where they touch, if that is ahead), and
            // its bead is lifted.
            double ox = jx - other.x, oy = jy - other.y, om = Math.hypot(ox, oy);
            if (om > 1e-6 && ox * other.dx + oy * other.dy > 0) { other.dx = ox / om; other.dy = oy / om; other.x = jx; other.y = jy; }
            unBead(other); other.done = true; other.into = keep; layTrail(other); flushTrail(other);
            keep.bias = (keep.bias * keep.vol + other.bias * other.vol) / Math.max(1e-9, keep.vol + other.vol) * .3;
            keep.vol += other.vol; keep.pig += other.pig; keep.vol0 += other.vol0 * .5;
            keep.w0t = Math.max(keep.w0t, Math.min(keep.w0 * 1.3, Math.hypot(keep.w0, other.w0)));
            keep.merged++;
            merges++;
        }
        // Each bead is laid again once its head has moved half its radius (the trail, laid half a radius behind the head,
        // never shows past it), or it has grown, or dried hard; one that has hardly moved stays as it is.
        for (Drip d : drips) {
            if (d.into != null) continue;
            if (d.x < 1 || d.y < 1 || d.x >= pw - 1 || d.y >= ph - 1) { flushTrail(d); continue; }
            double mx = d.x - d.bx, my = d.y - d.by, half = .5 * d.br;
            if (d.beadN > 0 && d.bh == d.done && (!show || mx * mx + my * my < half * half && Math.abs(beadR(d) - d.br) < .5)) continue;
            if (d.done) layTrail(d);
            unBead(d); flushTrail(d); putBead(d);
        }
        drips.removeIf(d -> d.done);
    }
    /** The wash is dry or dried now: every drip stops where it is, its bead drying with a hard edge. */
    private void stopDrips() {
        for (Drip d : drips) {
            if (d.done) continue;
            d.done = true;
            if (d.into == null && d.beadN > 0) { layTrail(d); unBead(d); flushTrail(d); putBead(d); }
        }
        drips.clear();
    }
    /**
     * Notes the drip's trail to just behind its head (to its head once it has merged), to be laid with its bead
     * ({@link #flushTrail}): the trail and the bead over it change together.
     */
    private void layTrail(Drip d) {
        // The trail's tone: the edge's where it leaves the wash, deepening to the drip's own over its first widths, a touch
        // lighter as it thins (and where it pinches). It is laid a little behind the head, which the bead covers.
        double lag = d.into != null ? 0 : Math.min(.5 * beadR(d), Math.max(0, d.len - .3 * d.w0)), x = d.x - d.dx * lag, y = d.y - d.dy * lag, run = d.len - lag;
        double[] q = d.queue;
        int n = d.queueN;
        double qx = n > 0 ? q[n - 6] : d.lx, qy = n > 0 ? q[n - 5] : d.ly;
        if ((x - qx) * d.dx + (y - qy) * d.dy <= 0) return;
        if (n + 6 > q.length) q = d.queue = Arrays.copyOf(q, q.length * 2);
        q[n] = x; q[n + 1] = y; q[n + 2] = .5 * d.wb; q[n + 3] = run;
        q[n + 4] = lerp(d.edge, d.tone, smoothstep(0, 1.5 * d.w0, run)) * (.88 + .14 * Math.min(1, Math.max(0, d.vol) / d.vol0)) * (1 + .3 * (d.wob - 1));
        q[n + 5] = d.mc;
        d.queueN = n + 6;
    }
    /** Lays the trail noted since it was last laid, piece by piece; its end cap once the drip has stopped. */
    private void flushTrail(Drip d) {
        double[] q = d.queue;
        for (int i = 0; i < d.queueN; i += 6) {
            double x = q[i], y = q[i + 1], rr = q[i + 2], run = q[i + 3], t = q[i + 4], mc = q[i + 5];
            if (!d.mouthLaid && (run >= 1.3 * d.w0 || d.done)) { d.mouthLaid = true; mouth(d); }
            seg(d.lx, d.ly, x, y, d.lr, rr, d.lt != 0 ? d.lt : t, t, d.lm != 0 ? d.lm : mc, mc, !d.laid, d.done && i + 6 >= d.queueN);
            d.laid = true;
            d.lx = x; d.ly = y; d.lr = rr; d.lt = t; d.lm = mc;
        }
        d.queueN = 0;
    }
    /**
     * The drip's width here: w0, narrowing a little as it loses water and runs, with a gentle swell and pinch along the
     * run (two octaves of seeded value noise); thin stretches are a little lighter with stronger rims (mc: the middle's
     * share).
     */
    private static void dripWidth(Drip d) {
        double sx = d.len / d.wl, n = .62 * (smoothNoise(d.ws, sx) - .5) + .38 * (smoothNoise(d.ws ^ 0x2c1b3c6d, sx * 2.3 + 7.1) - .5);
        double wob = 1 + 2 * d.wa * n, share = Math.max(0, d.vol) / d.vol0;
        double base = clamp(Math.pow(share, .2), .8, 1.05) * Math.max(.8, 1 - .003 * d.len / d.w0);
        d.wob = wob; d.wb = Math.max(1.4, d.w0 * base * wob); d.w = d.wb;
        d.mc = clamp(.8 - .3 * Math.max(0, 1 - wob) - .06 * (1 - Math.min(1, share)), .6, .82);
    }
    /**
     * A drip's darkness {@code a} at page pixel {@code px, py} as laid over the wash there: the darker of the two shows
     * (the drip is the wash's own water leaving it), so a drip grows out of the wash's edge without a seam.
     */
    private double over(int px, int py, double a) {
        int lx = px - fx0, ly = py - fy0;
        if (lx < 0 || ly < 0 || lx >= fw || ly >= fh) return a;
        int j = ly * fw + lx;
        // Inside the wash itself the wash is the drip's water: only on paper it left (and its soft edge) does it show.
        a *= 1 - smoothstep(.004, .04, fixed[j]);
        double o = cur[j];
        return o <= 0 ? a : a <= o ? 0 : (a - o) / (1 - o);
    }
    /** The flare where a drip leaves the wash, {@code run} pixels along it: a concave fillet, widest at the edge. */
    private static double root(Drip d, double run) { double u = clamp(run / (1.3 * d.w0)); return 1 + .9 * (1 - u) * (1 - u); }
    /** The bead's radius: about 1.3× the drip's width across, rounder and fuller as it slows. */
    private static double beadR(Drip d) { return .5 * d.w0 * (1.3 + .25 * (1 - Math.min(1, d.v / 3))) * (.84 + .16 * Math.min(1, d.load)) + .4; }
    /** The paper's height at pixel {@code x, y} (truncated, clamped to the page; the prototype's before its blur). */
    private double hAt(double x, double y) { return rawHeight((int)clamp((int)x, 0, pw - 1), (int)clamp((int)y, 0, ph - 1)); }
    /** A drip's trail store tile for page pixel {@code px, py}: [darkness laid, nearest normalised distance], made as it is reached. */
    private float[] tile(int px, int py) {
        int k = (py >> 6) * 4096 + (px >> 6);
        if (k == lastKey) return lastTile;
        float[] t = tiles.get(k);
        if (t == null) { t = new float[8192]; Arrays.fill(t, 4096, 8192, 9); tiles.put(k, t); }
        lastKey = k; lastTile = t;
        return t;
    }
    /** The trail tile {@link #tile} looked up last (a drip's pixels are mostly in one). */
    private int lastKey = -1;
    private float[] lastTile;
    /**
     * One piece of trail: a capsule whose radius and tone run smoothly from ({@code r0, t0}) to ({@code r1, t1}), drawn
     * at every page pixel with a soft edge. Pigment collects at the drip's edges (darker rims, a lighter middle), and the
     * paper's tooth takes it unevenly, which dithers the 16-grey display. Where trails overlap, each pixel takes the trail
     * it is nearest the middle of, so consecutive pieces join seamlessly and merging drips form a union.
     */
    private void seg(double x0, double y0, double x1, double y1, double r0, double r1, double t0, double t1, double m0, double m1, boolean cap, boolean end) {
        int w = W.w, h = W.h;
        double R = Math.max(r0, r1) + 1.5, sx = x1 - x0, sy = y1 - y0, L2 = sx * sx + sy * sy;
        if (L2 == 0) L2 = 1e-9;
        // A piece that continues the last needs no start cap (the last one's end laid the same pixels), and one the next
        // will continue no end cap past three pixels (enough for the drip's slow bends): mostly the band between.
        double L = Math.sqrt(L2), nx = Math.abs(sy) / L * R, ny = Math.abs(sx) / L * R, over = 3 / L, ex1 = x1 + sx * over, ey1 = y1 + sy * over;
        double bx0, bx1, by0, by1;
        if (cap) { bx0 = Math.min(x0, x1) - R; bx1 = Math.max(x0, x1) + R; by0 = Math.min(y0, y1) - R; by1 = Math.max(y0, y1) + R; }
        else if (end) { bx0 = Math.min(Math.min(x0, x1) - nx, x1 - R); bx1 = Math.max(Math.max(x0, x1) + nx, x1 + R); by0 = Math.min(Math.min(y0, y1) - ny, y1 - R); by1 = Math.max(Math.max(y0, y1) + ny, y1 + R); }
        else { bx0 = Math.min(x0, ex1) - nx; bx1 = Math.max(x0, ex1) + nx; by0 = Math.min(y0, ey1) - ny; by1 = Math.max(y0, ey1) + ny; }
        int X0 = Math.max(0, (int)Math.floor(bx0)), X1 = Math.min(w - 1, (int)Math.ceil(bx1));
        int Y0 = Math.max(0, (int)Math.floor(by0)), Y1 = Math.min(h - 1, (int)Math.ceil(by1));
        for (int py = Y0; py <= Y1; py++) for (int px = X0; px <= X1; px++) {
            double qx = px + .5 - x0, qy = py + .5 - y0, along = qx * sx + qy * sy;
            if (along <= 0 && !cap || along > L2 * (1 + over) && !end) continue;
            double t = clamp(along / L2), ex = qx - sx * t, ey = qy - sy * t, rr = r0 + (r1 - r0) * t, d2 = ex * ex + ey * ey;
            if (d2 > (rr + .6) * (rr + .6)) continue;
            double d = Math.sqrt(d2);
            trailPixel(px, py, d / rr, t0 + (t1 - t0) * t, m0 + (m1 - m0) * t, clamp((rr + .6 - d) / 1.2));
        }
    }
    /**
     * A pixel of trail at normalised distance {@code qn} from its middle, its tone, the middle's share {@code mc} and its
     * soft edge: pigment collects at the edges, the paper's tooth takes it unevenly. A pixel keeps the trail it is nearest
     * the middle of.
     */
    private void trailPixel(int px, int py, double qn, double tone, double mc, double soft) {
        float[] T = tile(px, py);
        int k = ((py & 63) << 6) | (px & 63);
        if (qn >= T[4096 + k]) return;
        double q = Math.min(1, qn);
        double a = over(px, py, Math.min(deep(.9), shade(tone, (mc + (1.32 - mc) * q * q) * soft * (1 + .55 * (tooth(px, py) - 1)))));
        if (Math.abs(a - T[k]) > 1e-4) W.mul(px, py, (1 - a) / (1 - T[k]));
        T[k] = (float)a; T[4096 + k] = (float)qn;
    }
    /**
     * The drip's mouth, laid once it has left the wash: a concave fillet from the wash's edge (about 1.9× the drip's
     * width there) narrowing into the drip over about its width, in the edge's tone deepening to the drip's, without the
     * darker rims, so the wash runs into the drip with no seam.
     */
    private void mouth(Drip d) {
        double L = 1.3 * d.w0, hw = .5 * d.w0, ux = d.ux0, uy = d.uy0, R = hw * 1.9 + 1.5;
        int X0 = Math.max(0, (int)Math.floor(d.x0 - R - Math.abs(ux) * L)), X1 = Math.min(W.w - 1, (int)Math.ceil(d.x0 + R + Math.abs(ux) * L));
        int Y0 = Math.max(0, (int)Math.floor(d.y0 - R - Math.abs(uy) * L)), Y1 = Math.min(W.h - 1, (int)Math.ceil(d.y0 + R + Math.abs(uy) * L));
        for (int py = Y0; py <= Y1; py++) for (int px = X0; px <= X1; px++) {
            double qx = px + .5 - d.x0, qy = py + .5 - d.y0, sAlong = qx * ux + qy * uy, across = Math.abs(qy * ux - qx * uy);
            if (sAlong < -1.5 * hw || sAlong > L) continue;
            // Above the edge it rounds off inside the wash, so where the edge slopes no corner shows past it.
            double u = clamp(sAlong / L), up = sAlong < 0 ? sAlong / (1.5 * hw) : 0, hm = hw * root(d, Math.max(0, sAlong)) * Math.sqrt(1 - up * up), dist = across;
            if (hm <= .5) continue;
            if (dist > hm + .6) continue;
            trailPixel(px, py, dist / hm, lerp(d.edge, d.tone, smoothstep(0, 1.5, u)) * .95, lerp(1.25, d.mc, u), clamp((hm + .6 - dist) / 1.2));
        }
    }
    /**
     * The bead: the drip's head, leading it, round and full (about 1.3× its width, rounder and fuller as it slows),
     * darker than the trail, darkest towards its front where the pigment it carries collects, with a teardrop tail
     * tapering back into the trail (longer while it runs). Laid reversibly while it moves; a drip that has stopped dries
     * with a dark, hard edge round its bead.
     */
    private void putBead(Drip d) {
        int w = W.w, h = W.h;
        boolean hard = d.done;
        // Its shape in small steps, centred on the pixel under the head: a moving bead is the same stamp moved, worked out
        // again only when its size, tail, tone or heading has changed by a step.
        double slow = Math.round(4 * (1 - Math.min(1, d.v / 3))) / 4.0, r = Math.round(2 * beadR(d)) / 2.0;
        double c = Math.round(100 * shade(clamp(d.tone * (1.3 + .25 * Math.min(1, d.len / (3 * d.w0))) + .04, 0, deep(.85)), .4 + .6 * smoothstep(0, d.w0, d.len))) / 100.0;
        double Lt = Math.round(2 * Math.min(r * (1 + .5 * (1 - slow)), Math.max(0, d.len - .5 * d.w0))) / 2.0, rt = Math.max(.7, Math.min(r, Math.round(d.wb) * .5));
        double ang = Math.round(Math.atan2(d.dy, d.dx) * 50) / 50.0;
        if (d.stamp == null || r != d.kr || c != d.kc || Lt != d.kL || rt != d.kt || ang != d.ka || hard != d.kh) {
            d.kr = r; d.kc = c; d.kL = Lt; d.kt = rt; d.ka = ang; d.kh = hard;
            stamp(d, r, c, Lt, rt, -Math.cos(ang), -Math.sin(ang), hard);
        }
        int ix = (int)Math.floor(d.x), iy = (int)Math.floor(d.y);
        int[] at = d.stampAt;
        float[] S = d.stamp;
        for (int k = 0, n = d.stampN; k < n; k++) {
            int px = ix + (at[k] & 0xffff) - 32768, py = iy + (at[k] >>> 16) - 32768;
            if (px < 0 || py < 0 || px >= w || py >= h) continue;
            // The darker of the bead and what is there shows: the trail's rims and the wash don't print through it.
            double am = W.union(px, py, Math.min(deep(.9), shade(S[k], 1 + .4 * (tooth(px, py) - 1))));
            if (am > 0) d.addBead(py << 16 | px, (float)am);
        }
        d.bx = d.x; d.by = d.y; d.br = beadR(d); d.bh = hard;
    }
    /**
     * Works out a drip's bead stamp: its darkness before the paper's tooth at each pixel offset from the pixel under its
     * head, for a bead of radius {@code r} and darkness {@code c}, its tail {@code Lt} long back along ({@code bx, by})
     * narrowing to {@code rt}; {@code hard}: dried, with a dark edge.
     */
    private void stamp(Drip d, double r, double c, double Lt, double rt, double bx, double by, boolean hard) {
        int R = (int)Math.ceil(r + 1);
        int x0 = (int)Math.floor(Math.min(0, bx * Lt)) - R, x1 = (int)Math.ceil(Math.max(0, bx * Lt)) + R;
        int y0 = (int)Math.floor(Math.min(0, by * Lt)) - R, y1 = (int)Math.ceil(Math.max(0, by * Lt)) + R;
        int most = (x1 - x0 + 1) * (y1 - y0 + 1), n = 0;
        if (d.stamp == null || d.stamp.length < most) { d.stamp = new float[most]; d.stampAt = new int[most]; }
        float[] S = d.stamp;
        int[] at = d.stampAt;
        double iL = Lt > 0 ? 1 / Lt : 0, ir = 1 / r;
        for (int v = y0; v <= y1; v++) for (int u = x0; u <= x1; u++) {
            double along = u * bx + v * by, t = along <= 0 ? 0 : along >= Lt ? 1 : along * iL, ax = u - bx * Lt * t, ay = v - by * Lt * t;
            double om = 1 - t, om15 = om * Math.sqrt(om), rr = rt + (r - rt) * om15, d2 = ax * ax + ay * ay;
            if (d2 > (rr + .6) * (rr + .6)) continue;
            double dd = Math.sqrt(d2), q = Math.min(1, dd / rr), front = 1 + .25 * smoothstep(-.2, .9, -along * ir);
            double prof = hard ? clamp((rr + .35 - dd) / .7) * (.6 + .2 * q * q + .75 * smoothstep(.5, .95, q) * om15)
                    : clamp((rr + .6 - dd) / 1.2) * (.78 + .3 * q * q);
            double a = shade(c, prof * front * (1 - .7 * t * t));
            if (a <= .002) continue;
            S[n] = (float)a; at[n++] = (v + 32768) << 16 | (u + 32768);
        }
        d.stampN = n;
    }
    private void unBead(Drip d) {
        for (int k = 0; k < d.beadN; k++) { int i = d.beadI[k]; W.mul(i & 0xffff, i >>> 16, 1 / (1 - d.beadA[k])); }
        d.beadN = 0;
    }

    // ---------- render ----------

    /** The wash's share of each pixel in the box, re-applied where it changed: page *= (1 − new) / (1 − old). */
    private void render() {
        pend = false;
        // Nothing left to move: what the patch laid since still shows, wherever it is.
        int[] b = box != null ? box : new int[]{0, 0, cw - 1, ch - 1};
        soften(b);
        int bx0 = Math.max(0, b[0] - 1), bx1 = Math.min(cw - 1, b[2] + 1), by0 = Math.max(0, b[1] - 1), by1 = Math.min(ch - 1, b[3] + 1);
        // Which cells changed since they last rendered (one more round them, for the bilinear read).
        int mx0 = Math.max(0, bx0 - 1), mx1 = Math.min(cw - 1, bx1 + 1), my0 = Math.max(0, by0 - 1), my1 = Math.min(ch - 1, by1 + 1);
        Cores.inBands(my0, my1, 8, 4L * (mx1 - mx0 + 1) * (my1 - my0 + 1), (top, bottom, worker) -> {
            for (int cy = top; cy <= bottom; cy++) for (int c = cy * cw + mx0, e = cy * cw + mx1; c <= e; c++)
                moved[c] = (byte)(touched[c] != 0 || !(Math.abs(tmp[c] - Plast[c]) <= STILL) || Math.abs(P3[c] - Qlast[c]) > STILL || Math.abs(Gs[c] + Sg[c] - Glast[c]) > STILL ? 1 : 0);
        });
        // The page's tiles under the cells to render, read now: the bands below change them on several cores.
        for (int cy = by0; cy <= by1; cy++) for (int cx = bx0; cx <= bx1; cx++) {
            int c = cy * cw + cx;
            if (moved[c] == 0) continue;
            W.prepare(fx0 + Math.max(0, cx - 1) * cs, fy0 + Math.max(0, cy - 1) * cs, fx0 + (cx + 2) * cs - 1, fy0 + (cy + 2) * cs - 1);
        }
        Cores.inBands(by0, by1, 8, (long)cs * cs * (bx1 - bx0 + 1) * (by1 - by0 + 1), (top, bottom, worker) -> renderRows(top, bottom, bx0, bx1));
    }
    /**
     * The cells' pigment over box {@code b} (and two cells round it) into {@link #tmp}, softened by a [1 2 1] kernel each
     * way over wet cells only (normalised), so moved paint has smooth gradients and round, liquid edges; the blurred wet
     * mask into {@link #P3}, which gives paper the wash never covered a smooth outline.
     */
    private void soften(int[] b) {
        float[] P = tmp, Q = P2, Qm = P3;
        int ex0 = Math.max(0, b[0] - 2), ex1 = Math.min(cw - 1, b[2] + 2), ey0 = Math.max(0, b[1] - 2), ey1 = Math.min(ch - 1, b[3] + 2);
        long work = 4L * (ex1 - ex0 + 1) * (ey1 - ey0 + 1);
        Cores.inBands(ey0, ey1, 8, work, (top, bottom, worker) -> {
            for (int cy = top; cy <= bottom; cy++) {
                int row = cy * cw;
                for (int c = row + ex0, e = row + ex1; c <= e; c++) { P[c] = A[c] + S[c]; Qm[c] = P[c] > 0 || Wt[c] >= .03 ? 1 : 0; }
                float pp = P[row + ex0], pm = Qm[row + ex0];
                for (int cx = ex0; cx <= ex1; cx++) {
                    int c = row + cx;
                    float p0 = P[c], m0 = Qm[c], pn = cx < ex1 ? P[c + 1] : p0, mn = cx < ex1 ? Qm[c + 1] : m0;
                    Q[c] = .5f * p0 + .25f * (pp + pn); Qm[c] = .5f * m0 + .25f * (pm + mn); pp = p0; pm = m0;
                }
            }
        });
        Cores.inBands(ex0, ex1, 8, work, (left, right, worker) -> {
            for (int cx = left; cx <= right; cx++) {
                float pq = Q[ey0 * cw + cx], pm = Qm[ey0 * cw + cx];
                for (int cy = ey0; cy <= ey1; cy++) {
                    int c = cy * cw + cx;
                    float q = Q[c], m = Qm[c], nq = cy < ey1 ? Q[c + cw] : q, nm = cy < ey1 ? Qm[c + cw] : m;
                    double num = .5 * q + .25 * (pq + nq), den = .5 * m + .25 * (pm + nm);
                    pq = q; pm = m;
                    P[c] = (float)(den <= 1e-6 ? 0 : liq >= 1 || Wt[c] >= .03 || A[c] + S[c] > 0 ? num / den : num + (num / den - num) * liq);
                    Qm[c] = (float)den;
                }
            }
        });
    }
    /** Render's cell rows {@code cy0..cy1}: each pixel's new share, applied. */
    private void renderRows(int cy0, int cy1, int bx0, int bx1) {
        float[] P = tmp, Qm = P3;
        for (int cy = cy0; cy <= cy1; cy++) for (int cx = bx0, c = cy * cw + bx0; cx <= bx1; cx++, c++) {
            // Nothing round it changed: its pixels would come out as they are.
            boolean any = false;
            for (int v = Math.max(0, cy - 1); v <= Math.min(ch - 1, cy + 1) && !any; v++) for (int u = Math.max(0, cx - 1); u <= Math.min(cw - 1, cx + 1); u++) if (moved[v * cw + u] != 0) { any = true; break; }
            if (!any) continue;
            Plast[c] = P[c]; Qlast[c] = Qm[c]; Glast[c] = Gs[c] + Sg[c]; touched[c] = 0;
            double a = P[c], gs = Gs[c] + Sg[c];
            // Paper the wash never covered takes the softened pigment where water has run onto it (or next to it), shaped
            // by the paper's tooth: the soft, rounded edge of a lobe or finger. The wash's own edge elsewhere stays crisp.
            boolean fresh = (W0[c] < .06 && Wt[c] >= .03) || (cx > 0 && Wt[c - 1] >= .06 && W0[c - 1] < .06) || (cx + 1 < cw && Wt[c + 1] >= .06 && W0[c + 1] < .06)
                    || (cy > 0 && Wt[c - cw] >= .06 && W0[c - cw] < .06) || (cy + 1 < ch && Wt[c + cw] >= .06 && W0[c + cw] < .06);
            double l = cx > 0 ? P[c - 1] : a, rt = cx + 1 < cw ? P[c + 1] : a, t = cy > 0 ? P[c - cw] : a, d = cy + 1 < ch ? P[c + cw] : a;
            double tl = cx > 0 && cy > 0 ? P[c - cw - 1] : a, tr = cx + 1 < cw && cy > 0 ? P[c - cw + 1] : a, dl = cx > 0 && cy + 1 < ch ? P[c + cw - 1] : a, dr = cx + 1 < cw && cy + 1 < ch ? P[c + cw + 1] : a;
            double m = Qm[c], ml = cx > 0 ? Qm[c - 1] : m, mr = cx + 1 < cw ? Qm[c + 1] : m, mt = cy > 0 ? Qm[c - cw] : m, md = cy + 1 < ch ? Qm[c + cw] : m;
            double mtl = cx > 0 && cy > 0 ? Qm[c - cw - 1] : m, mtr = cx + 1 < cw && cy > 0 ? Qm[c - cw + 1] : m, mdl = cx > 0 && cy + 1 < ch ? Qm[c + cw - 1] : m, mdr = cx + 1 < cw && cy + 1 < ch ? Qm[c + cw + 1] : m;
            boolean none = a <= 0 && l <= 0 && rt <= 0 && t <= 0 && d <= 0 && tl <= 0 && tr <= 0 && dl <= 0 && dr <= 0 && gs <= 0;
            // Cells of 4: the softened pigment at the start, and the cell's own then (see P0).
            float[] Z = P0;
            double a0 = 0, l0 = 0, r0 = 0, t0 = 0, d0 = 0, tl0 = 0, tr0 = 0, dl0 = 0, dr0 = 0, own0 = 0;
            if (Z != null) {
                a0 = Z[c]; l0 = cx > 0 ? Z[c - 1] : a0; r0 = cx + 1 < cw ? Z[c + 1] : a0; t0 = cy > 0 ? Z[c - cw] : a0; d0 = cy + 1 < ch ? Z[c + cw] : a0;
                tl0 = cx > 0 && cy > 0 ? Z[c - cw - 1] : a0; tr0 = cx + 1 < cw && cy > 0 ? Z[c - cw + 1] : a0; dl0 = cx > 0 && cy + 1 < ch ? Z[c + cw - 1] : a0; dr0 = cx + 1 < cw && cy + 1 < ch ? Z[c + cw + 1] : a0;
                own0 = A0[c];
                none &= a0 <= 0 && l0 <= 0 && r0 <= 0 && t0 <= 0 && d0 <= 0 && tl0 <= 0 && tr0 <= 0 && dl0 <= 0 && dr0 <= 0;
            }
            for (int v = 0; v < cs; v++) for (int u = 0; u < cs; u++) {
                int lx = cx * cs + u, ly = cy * cs + v;
                if (lx >= fw || ly >= fh) continue;
                int j = ly * fw + lx;
                if (none && fixed[j] == cur[j]) continue;
                // The pixel's place in its cell: how far towards the neighbour on its side, each way (bilinear).
                double fu = near[u], fv = near[v], au = Math.abs(fu), av = Math.abs(fv), wa = (1 - au) * (1 - av), wx = au * (1 - av), wy = (1 - au) * av, wd = au * av;
                double sx = fu > 0 ? rt : l, sy = fv > 0 ? d : t, sd = fu > 0 ? (fv > 0 ? dr : tr) : (fv > 0 ? dl : tl);
                double wj = wgt[j];
                if (wj == 0 && fresh) {
                    double mb = wa * m + wx * (fu > 0 ? mr : ml) + wy * (fv > 0 ? md : mt) + wd * (fu > 0 ? (fv > 0 ? mdr : mtr) : (fv > 0 ? mdl : mtl));
                    wj = toothA(j) * (1 + (smoothstep(.2, .45, mb) - 1) * liq);
                } else if (wj > 1.25) wj = 1.25;
                if (wj == 0 && fixed[j] == cur[j] && gs <= 0) continue;
                double ab = wa * a + wx * sx + wy * sy + wd * sd;
                if (Z != null) ab += own0 - (wa * a0 + wx * (fu > 0 ? r0 : l0) + wy * (fv > 0 ? d0 : t0) + wd * (fu > 0 ? (fv > 0 ? dr0 : tr0) : (fv > 0 ? dl0 : tl0)));
                double tt = fixed[j] + (ab > 0 ? ab : 0) * wj;
                if (gs > 0) tt += gs * wj * vf[j];
                double most = deep(.9);
                if (tt > most) tt = most;
                double o = cur[j];
                if (Math.abs(tt - o) < .0015) continue;
                W.mul(lx + fx0, ly + fy0, (1 - tt) / (1 - o)); cur[j] = (float)tt;
            }
        }
    }

    /** Cap {@code c} on a darkness, raised toward {@link #DEEPEST} as the wash is near black ({@link #deep}): {@code c} itself for every wash before. */
    private double deep(double c) { return deep == 0 ? c : c + (DEEPEST - c) * deep; }
    /**
     * Darkness {@code tone} shaded by {@code f} (a drip's middle, soft edge, the paper's tooth): {@code tone × f}, as for
     * every wash before; as the wash is near black ({@link #deep}), shaded as a body's strength is
     * ({@link ModularSizes#laid}), so a share of a near-black drip stays near black, as a share of a black wash does.
     */
    private double shade(double tone, double f) {
        double plain = tone * f;
        return deep == 0 ? plain : plain + (ModularSizes.laid(ModularSizes.strength(tone) * f) - plain) * deep;
    }

    // ---------- for the checks ----------

    /** The pigment ledger: {total, wash, mean y} of the wash's pigment (pixels of darkness) plus what drips took, less what the patch's settling added. */
    double[] ledger() {
        double t = 0, ty = 0;
        for (int ly = 0; ly < fh; ly++) { double rr = 0; for (int lx = 0, j = ly * fw; lx < fw; lx++, j++) rr += fixed[j]; t += rr; ty += rr * (ly + fy0); }
        for (int cy = 0; cy < ch; cy++) { double rr = 0; for (int c = cy * cw, e = c + cw; c < e; c++) rr += A[c] + S[c] + Gs[c] + Sg[c]; t += cs * cs * rr; ty += cs * cs * rr * (cy * cs + cs * .5 + fy0); }
        return new double[]{t + dripPig - added, t, t != 0 ? ty / t : 0};
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Quick: gravity inside the wet window under the pen.

    /** Quick's window under a tilt: Runs, Pool and Sink as they act on the body the window settles. */
    static final class Window {
        final double runs, pool, sink, ux, uy, m, far, near;
        Window(double runs, double pool, double sink, double ux, double uy, double m, double far, double near) {
            this.runs = runs; this.pool = pool; this.sink = sink; this.ux = ux; this.uy = uy; this.m = m; this.far = far; this.near = near;
        }
        /**
         * The darkness factor at window point {@code x, y} ({@code j} in {@code P}, the raw body over its {@code rw ×
         * rh} rectangle), wet for the share {@code age} of the window, its paper's {@code fine} and {@code mid}:
         * pigment leaves the uphill side, a hard darker rim where the paper downhill is dry, beaded by the paper, and
         * grain shows more towards the low side (the Gravity Lab's gravWindow).
         */
        double factor(float[] P, int rw, int rh, int x, int y, int j, double age, double fine, double mid) {
            double p = P[j];
            if (p <= 0 || age <= 0) return 1;
            double k = 1, a = age * m;
            if (runs > 0) { double up = at(P, rw, rh, x, y, -ux * far, -uy * far), dn = at(P, rw, rh, x, y, ux * far, uy * far); k *= clamp(1 + runs * a * 1.3 * (up - dn) / (up + dn + .05), .35, 2); }
            if (pool > 0) { double dn = at(P, rw, rh, x, y, ux * near, uy * near); if (dn < p * .5) k *= 1 + pool * a * 1.6 * clamp(.2 + 1.6 * smoothstep(.3, .7, mid), 0, 1.8); }
            if (sink > 0) {
                double up = at(P, rw, rh, x, y, -ux * far, -uy * far), dn = at(P, rw, rh, x, y, ux * far, uy * far), low = clamp((up - dn) / (up + dn + .05) + .3);
                k *= clamp(1 + sink * a * low * 3 * (1.5 * (fine - .5) + .6 * (mid - .5)), .2, 2);
            }
            return k;
        }
        private static double at(float[] P, int rw, int rh, int x, int y, double dx, double dy) {
            int xx = (int)clamp(Math.round(x + dx), 0, rw - 1), yy = (int)clamp(Math.round(y + dy), 0, rh - 1);
            return P[yy * rw + xx];
        }
    }
    /**
     * Quick's window under {@code G} (gravWindowOf): null on a level page or without Runs, Pool or Sink. On a stroke's
     * lattice ({@code shift} 1) its reach in lattice points is half its reach in pixels.
     */
    static Window window(ResolvedBrush.Grav grav, PageTilt G, double bodySize, int shift) {
        if (grav == null || G.level() || !(grav.runs > 0 || grav.pool > 0 || grav.sink > 0)) return null;
        double k = 1.0 / (1 << shift);
        return new Window(grav.runs, grav.pool, grav.sink, G.ux(), G.uy(), G.m, Math.max(3, bodySize * .22) * k, 2.5 * k);
    }
}
