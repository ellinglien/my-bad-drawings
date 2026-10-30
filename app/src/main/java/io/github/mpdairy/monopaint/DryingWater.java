package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.index;
import static io.github.mpdairy.monopaint.StrokeMath.lerp;
import static io.github.mpdairy.monopaint.StrokeMath.smooth;

import java.util.Arrays;

/**
 * A Drying wash's suspended pigment settling into the paper's valleys after lift (stroke.js 76–238, patches only: no
 * parcels, sources, stains, plumes or trails). One patch over the stroke's box on the layer it drew, its own clock
 * starting at the lift; it is built at its first step, so the lift doesn't wait for it, and a wash too big to hold
 * ({@link #MOST_CELLS}) is laid settled at the lift instead. The box is split into 2 × 2
 * cells and only the cells the wash reached are kept, so a big wash holds what it covers rather than its box. Each step on the patch's even ages and its last lets the pigment drift
 * between neighbouring cells (toward the valleys, toward the wash's middle, along a slow curl) and lays a share of it:
 * a pixel is written as its tone at lift × (1 − shown) / (1 − shown at lift), so steps never drift, and never lighter
 * than it is. A step is its own gesture finished as a continuation, one undo step with the stroke.
 * <p>
 * Each step (and the finish) is worked out off the page first ({@link #work}, {@link #finishing}: reading the page,
 * keeping what it lays in a {@link StrokeTones}) and then written ({@link #apply}), so the work can run on a thread of
 * its own while the page doesn't change ({@link SettleWorker}); {@link #step} and {@link #finishNow} do both at once.
 * Laying it now changes nothing of the patch's own, so it can be worked out ahead, between steps.
 * <p>
 * Gravity: a wash whose brush moves with the page's tilt ({@link ResolvedBrush.Grav}) holds a {@link GravityFlow} too,
 * built at the first tick the page is tilted (a level page never builds one, so it settles exactly as without gravity)
 * and stepped every tick; from then on the patch's settling lands through the flow, multiplying the page's tones as the
 * flow does. Pure.
 */
final class DryingWater {
    /** stroke.js TPS: ticks a second. */
    static final int TPS = 10;
    private final ToneDocument document;
    private final int layer;
    private long revision;
    /** The patch's box on the page, its cell grid's width, how many ticks it lasts and the age of the next one. */
    private final int x0, y0, w, h, cw, ticks;
    private int age;
    private final double settle, lam;
    /**
     * What the lift left, until the first step builds the patch from it (off the lift, which the pen waits for): the
     * paper, the held-back darkness over the box and the seeds. Dropped once built.
     */
    private StrokePaper paper;
    private float[] darkness;
    /** Where the settling may land, 0 to 1 a pixel over the box (Behind: how blank the page was); null everywhere. Dropped with {@link #darkness}. */
    private float[] lands;
    /** A fill's room, where the settling lands when {@link #lands} is null (null: everywhere). Dropped with {@link #darkness}. */
    private FillRegion.Room room;
    private final long brushSeed;
    private final int strokeSeed;
    /** How many cells the wash reached. */
    private int count;
    /** The kept cells in page order: each one's index on the grid, the first kept cell of each grid row (and one past the last), the kept cell below it or −1. */
    private int[] pos, rowStart, down;
    /** Each kept cell's pigment still suspended (and the next step's), how much it holds, its paper height, how far inside the wash it is, its curl. */
    private float[] S, Sn, smax, height, inside, fx, fy, blurred;
    /**
     * Each kept cell's darkest pixel wet at lift: settling takes a pixel to {@link #SETTLED} at most, or to that if
     * darker (a wash near black, since washes reach black, settles back to its own darkness).
     */
    private float[] darkest;
    /** The darkest settling takes a pixel (stroke.js stepPatch), past which only a cell darker wet goes. */
    static final double SETTLED = .95;
    /** Each kept cell's four pixels (4k + 2v + u): how much shows, the tone it would have with none showing, and its share of what the cell lays; base < 0 off the box. */
    private float[] shown, base, vfn;
    /** A step's blurred rows of S (five, by row mod 5), the rows they hold and how far across; one row scattered and once blurred; the tones a run writes. Made on first use. */
    private float[][] ring;
    private int[] ringRow, ringSpan;
    private float[] row, once, zeros;
    /** Each worker's row of tones a run writes ({@link Cores#WORKERS}), made on first use. */
    private final int[][] grays = new int[Cores.WORKERS][];
    /** The cells' noise, the paper's heights and the valleys' table while the lift values are read. */
    private WildBrush.Random noise;
    private Heights heights;
    private float[] valleys;
    /** Gravity's dials, or null: the wash never moves. */
    private final ResolvedBrush.Grav grav;
    /** The stroke's lattice (1: a big stroke's even pixels), the flow's grid. */
    private final int shift;
    /** With gravity: each kept cell's pixels' darkness at lift (as {@link #shown}), and the paper, for the flow. */
    private float[] liftD;
    private StrokePaper flowPaper;
    /** The flow moving the wash with the tilt, once the page has tilted; and the layer's tones it multiplies (kept once made, as the patch's settling lands through them). */
    private GravityFlow flow;
    private GravityFlow.TonePage tones;
    /** Too big to move with the tilt ({@link GravityFlow#MOST_BYTES}): it settles as if level. Read on the page's thread while a worker steps it. */
    private volatile boolean still;
    /** How much of what it lays the eraser takes: the stroke's Erasable ({@link ToneDocument#begin(double)}). */
    private final double erasable;
    /** The kind of mark it lays ({@link ToneDocument#begin(double, int)}). */
    private final int kind;
    /** Its work is no longer wanted ({@link #stop}): the work in progress ends at its next row. */
    private volatile boolean stopped;

    /**
     * The most cells a wash keeps to settle over ticks: 2^18, about a million pixels (a 1024 × 1024 wash, a fifth of
     * the Manta's 1920 × 2560 page). A patch holds 92 bytes a kept cell (eleven arrays of a value a cell, three of a value
     * a pixel), 24 MB at the cap, plus 12 bytes a box cell for the grids building it (at most 15 MB for the whole page),
     * beside the document in a heap without largeHeap; and it works through every cell each step (on the session's
     * {@link SettleWorker} in the app): at the cap a step took about 11 ms on the desktop (the build about 100 ms), which
     * leaves the device's slower CPU room inside the 200 ms between steps. A bigger wash is laid settled at lift instead ({@link #finishNow}), a block
     * of cell rows at a time ({@link #FINISH_ROWS}, shared out over the cores), so it never holds more than a block (at
     * most 64 rows of the page's 960 cells, 5.6 MB).
     */
    static final int MOST_CELLS = 1 << 18;

    /**
     * The wash laid on {@code document}'s active layer, which the stroke has just finished: {@code darkness} is the
     * held-back body, laid(d · washK) ({@link ModularSizes#laid}), over {@code x0, y0, w, h} row by row. null when settle is 0 or nothing was
     * reached: nothing to settle; null too when it reached more than {@link #MOST_CELLS} cells, laid settled at once
     * here. Keeps {@code darkness} only until its first step (or {@link #finishNow}) has read it.
     */
    static DryingWater lay(ToneDocument document, StrokePaper paper, int x0, int y0, int w, int h, float[] darkness, double drying, double settle, long brushSeed, int strokeSeed) {
        return lay(document, paper, x0, y0, w, h, darkness, drying, settle, brushSeed, strokeSeed, MOST_CELLS);
    }
    /** As {@link #lay(ToneDocument, StrokePaper, int, int, int, int, float[], double, double, long, int)}, keeping at most {@code mostCells} to settle over ticks. */
    static DryingWater lay(ToneDocument document, StrokePaper paper, int x0, int y0, int w, int h, float[] darkness, double drying, double settle, long brushSeed, int strokeSeed, int mostCells) {
        return lay(document, paper, x0, y0, w, h, darkness, PartDial.wetMs(drying), settle, null, 0, brushSeed, strokeSeed, 1, 0, null, null, mostCells);
    }
    /**
     * As {@link #lay(ToneDocument, StrokePaper, int, int, int, int, float[], double, double, long, int)} for a wash wet
     * {@code wetMs} that may move with the page's tilt ({@code grav}, or null; then it needs Settle above 0 to be held),
     * on the stroke's lattice {@code shift}, its paint as erasable as the stroke's ({@code erasable}, {@link ToneDocument#begin(double)}).
     */
    static DryingWater lay(ToneDocument document, StrokePaper paper, int x0, int y0, int w, int h, float[] darkness, long wetMs, double settle,
                           ResolvedBrush.Grav grav, int shift, long brushSeed, int strokeSeed, double erasable, int kind) {
        return lay(document, paper, x0, y0, w, h, darkness, wetMs, settle, grav, shift, brushSeed, strokeSeed, erasable, kind, null, null, MOST_CELLS);
    }
    /**
     * As above, the settling landing at each pixel of the box only by {@code lands} (0 to 1, row by row; null: all): a
     * wash Behind marks settles only where the page was blank (Blend), though its pigment drifts as any wash's. With
     * {@code room} (a fill's, or null) and no {@code lands}, it lands only in the room, read there rather than copied.
     */
    static DryingWater lay(ToneDocument document, StrokePaper paper, int x0, int y0, int w, int h, float[] darkness, long wetMs, double settle,
                           ResolvedBrush.Grav grav, int shift, long brushSeed, int strokeSeed, double erasable, int kind, float[] lands, FillRegion.Room room) {
        return lay(document, paper, x0, y0, w, h, darkness, wetMs, settle, grav, shift, brushSeed, strokeSeed, erasable, kind, lands, room, MOST_CELLS);
    }
    private static DryingWater lay(ToneDocument document, StrokePaper paper, int x0, int y0, int w, int h, float[] darkness, long wetMs, double settle,
                                   ResolvedBrush.Grav grav, int shift, long brushSeed, int strokeSeed, double erasable, int kind, float[] lands, FillRegion.Room room, int mostCells) {
        if (!(settle > 0) && grav == null || darkness == null || w <= 0 || h <= 0) return null;
        int cw = (w + 1) >> 1, ch = (h + 1) >> 1, kept = 0;
        // A box with room for more cells than that has them counted; any other needs only one.
        boolean big = (long)cw * ch > mostCells;
        reach: for (int cy = 0; cy < ch; cy++) for (int cx = 0; cx < cw; cx++)
            if (reached(darkness, w, h, cx, cy) && (++kept > mostCells || !big)) break reach;
        if (kept == 0) return null;
        DryingWater water = new DryingWater(document, paper, x0, y0, w, h, darkness, wetMs, settle, grav, shift, brushSeed, strokeSeed, erasable, kind);
        water.lands = lands; water.room = lands == null ? room : null;
        if (kept <= mostCells) return water;
        water.finishNow(document);
        return null;
    }
    /** stroke.js 186: whether the wash reached cell {@code cx, cy}, its pixels' mean darkness over .003. */
    private static boolean reached(float[] d, int w, int h, int cx, int cy) {
        double sum = 0; int n = 0;
        for (int v = 0; v < 2; v++) for (int u = 0; u < 2; u++) {
            int x = 2 * cx + u, y = 2 * cy + v;
            if (x < w && y < h) { sum += d[y * w + x]; n++; }
        }
        return sum / n > .003;
    }

    private DryingWater(ToneDocument document, StrokePaper paper, int x0, int y0, int w, int h, float[] darkness, long wetMs, double settle,
                        ResolvedBrush.Grav grav, int shift, long brushSeed, int strokeSeed, double erasable, int kind) {
        this.erasable = erasable; this.kind = kind;
        this.document = document; layer = document.activeLayer(); revision = document.revision();
        this.paper = paper; this.darkness = darkness; this.brushSeed = brushSeed; this.strokeSeed = strokeSeed;
        this.x0 = x0; this.y0 = y0; this.w = w; this.h = h; this.settle = settle; this.grav = grav; this.shift = shift;
        cw = (w + 1) >> 1;
        ticks = (int)Math.max(3, Math.round(wetMs * TPS / 1000.0));
        lam = 2.1 / ticks;
    }

    /**
     * addWet's first half (stroke.js 176–192) from what the lift left, on the page as the stroke left it: each kept
     * cell's pigment, what it holds and its pixels, all that laying it now reads ({@link #finishing}).
     */
    private void liftAll() {
        long t0 = System.nanoTime();
        int ch = (h + 1) >> 1;
        int[] kept = kept(0, ch);
        int n = 0;
        for (int k : kept) n += k;
        hold(n, ch);
        lift(0, ch, kept);
        liftNanos = System.nanoTime() - t0;
    }
    /** Its box on the page, {left, top, right, bottom} inclusive, and the layer it settles on. */
    int[] box() { return new int[]{x0, y0, x0 + w - 1, y0 + h - 1}; }
    int layer() { return layer; }
    /** How long {@link #liftAll} took, ns (for the bench). */
    long liftNanos;
    /** How many cells the wash reached on each grid row {@code cy0} to {@code cy1}, rows shared out over the cores. */
    private int[] kept(int cy0, int cy1) {
        int[] kept = new int[cy1 - cy0];
        float[] D = darkness;
        Cores.Piece count = (piece, worker) -> {
            for (int cy = cy0 + piece * LIFT_ROWS, end = Math.min(cy1, cy + LIFT_ROWS); cy < end; cy++) {
                int n = 0;
                for (int cx = 0; cx < cw; cx++) if (reached(D, w, h, cx, cy)) n++;
                kept[cy - cy0] = n;
            }
        };
        int pieces = (cy1 - cy0 + LIFT_ROWS - 1) / LIFT_ROWS;
        if (pieces > 1 && (long)cw * (cy1 - cy0) >= Cores.SHARE_PX) Cores.each(pieces, count);
        else for (int k = 0; k < pieces; k++) count.run(k, 0);
        return kept;
    }
    /** Lifts it now if nothing has ({@link #liftAll}): from then on laying it now ({@link #finishing}) leaves it as it is. */
    void lift() { if (pos == null) liftAll(); }
    /**
     * Gets it ready to step (off the lift, which the pen waits for): lifted, then the rest of addWet (stroke.js 193–198),
     * each cell's place in the wash and its curl. {@link #work} does it at the first step if nothing has.
     */
    void build() {
        if (darkness == null) return;
        if (pos == null) liftAll();
        float[] D = darkness;
        int ch = (h + 1) >> 1;
        down = new int[count]; inside = new float[count]; fx = new float[count]; fy = new float[count]; blurred = new float[count];
        int k;
        for (k = 0; k < count; k++) {
            int cy = pos[k] / cw;
            down[k] = cy + 2 > ch ? -1 : Math.max(-1, Arrays.binarySearch(pos, rowStart[cy + 1], rowStart[cy + 2], pos[k] + cw));
        }
        // How far inside the wash each cell is (stroke.js 230, Wb: the kept cells blurred), and the depth the curl reads
        // (stroke.js 167–175: the reached pixels blurred, here on the cells' grid), on grids dropped once built.
        live();
        float[] grid = new float[cw * ch], tmp = new float[cw * ch], out = new float[cw * ch];
        for (k = 0; k < count; k++) grid[pos[k]] = 1;
        StrokeMath.blur(StrokeMath.blur(grid, cw, ch, 3, tmp, out), cw, ch, 2, tmp, out);
        for (k = 0; k < count; k++) inside[k] = out[pos[k]];
        for (int cy = 0; cy < ch; cy++) for (int cx = 0; cx < cw; cx++) {
            int n = 0, wet = 0;
            for (int v = 0; v < 2; v++) for (int u = 0; u < 2; u++) {
                int x = 2 * cx + u, y = 2 * cy + v;
                if (x < w && y < h) { n++; if (D[y * w + x] > .004) wet++; }
            }
            grid[cy * cw + cx] = (float)wet / n;
        }
        float[] depth = StrokeMath.blur(StrokeMath.blur(grid, cw, ch, 2, tmp, out), cw, ch, 1, tmp, out);
        WildBrush.Random fr = new WildBrush.Random((int)brushSeed ^ 0xf10e);
        float[] psiA = lattice(fr, 64);
        // psiB: drawn for psiC's draws to follow; at the patch's tick 0 its phase weighs it 0.
        lattice(fr, 64);
        float[] psiC = lattice(fr, 32);
        for (k = 0; k < count; k++) {
            if ((k & 4095) == 0) live();
            int X = x0 + 2 * (pos[k] % cw) + 1, Y = y0 + 2 * (pos[k] / cw) + 1;
            fx[k] = (float)((psi(psiA, psiC, D, depth, X, Y + 2) - psi(psiA, psiC, D, depth, X, Y - 2)) * 30);
            fy[k] = (float)(-(psi(psiA, psiC, D, depth, X + 2, Y) - psi(psiA, psiC, D, depth, X - 2, Y)) * 30);
        }
        if (grav != null) flowPaper = paper;
        paper = null; darkness = null; lands = null; room = null;
    }

    /** Room for {@code n} kept cells on a grid {@code ch} cells high. */
    private void hold(int n, int ch) {
        pos = new int[n]; rowStart = new int[ch + 1];
        S = new float[n]; Sn = new float[n]; smax = new float[n]; height = new float[n]; darkest = new float[n];
        shown = new float[4 * n]; base = new float[4 * n]; vfn = new float[4 * n];
        liftD = grav != null ? new float[4 * n] : null;
    }
    /** Cell rows a piece of {@link #lift} takes when it shares them out over the cores. */
    private static final int LIFT_ROWS = 16;
    /**
     * addWet (stroke.js 176–192) over grid rows {@code cy0} to {@code cy1}, which follow the rows already read, {@code kept}
     * the cells kept on each: each kept cell's pigment, what it holds, its paper height and its pixels, as kept cells 0 on.
     * Every cell draws its noise, kept or not, in order: a cell's draw is known from its place, so the rows are read on
     * their own and a big patch's are shared out over the cores ({@link Cores}).
     */
    private void lift(int cy0, int cy1, int[] kept) {
        if (noise == null) { noise = new WildBrush.Random(strokeSeed ^ 0x5e771e); heights = new Heights(paper, document.width, document.height, x0, y0, w); valleys = valleys(2.4 * settle); }
        int[] at = new int[cy1 - cy0 + 1];
        for (int cy = cy0, k = 0; cy <= cy1; cy++) { at[cy - cy0] = rowStart[cy] = k; if (cy < cy1) k += kept[cy - cy0]; }
        count = at[cy1 - cy0];
        WildBrush.Random rn = noise;
        int pieces = (cy1 - cy0 + LIFT_ROWS - 1) / LIFT_ROWS;
        Heights[] own = new Heights[Cores.WORKERS];
        Cores.Piece read = (piece, worker) -> {
            if (own[worker] == null) own[worker] = worker == 0 ? heights : new Heights(paper, document.width, document.height, x0, y0, w);
            int r0 = cy0 + piece * LIFT_ROWS, r1 = Math.min(cy1, r0 + LIFT_ROWS);
            liftCells(r0, r1, at[r0 - cy0], (long)(r0 - cy0) * cw, rn, own[worker]);
        };
        if (pieces > 1 && 4L * count >= Cores.SHARE_PX) Cores.each(pieces, read);
        else for (int k = 0; k < pieces; k++) read.run(k, 0);
        noise.skip((long)(cy1 - cy0) * cw);
    }
    /**
     * addWet (stroke.js 181–192) for grid rows {@code cy0} to {@code cy1}, their kept cells from {@code k} on: {@code drawn}
     * cells draw their noise from {@code rn} before the first, and the paper is read through {@code heights}.
     */
    private void liftCells(int cy0, int cy1, int k, long drawn, WildBrush.Random rn, Heights heights) {
        float[] D = darkness, hh4 = new float[4];
        double f = .45 * settle;
        for (int cy = cy0; cy < cy1; cy++) {
            live();
            for (int cx = 0; cx < cw; cx++, drawn++) {
                if (!reached(D, w, h, cx, cy)) continue;
                double sum = 0, hs = 0, vs = 0; int n = 0;
                float most = 0;
                heights.cell(cx, cy, hh4);
                for (int v = 0; v < 2; v++) for (int u = 0; u < 2; u++) {
                    int x = 2 * cx + u, y = 2 * cy + v, j = 4 * k + 2 * v + u;
                    if (x >= w || y >= h) { base[j] = -1; continue; }
                    float hh = hh4[2 * v + u], d = D[y * w + x];
                    sum += d; n++; hs += hh; most = Math.max(most, d);
                    vfn[j] = (float)valley(valleys, hh); vs += vfn[j];
                    shown[j] = d > 0 ? (float)(d * (1 - f)) : 0;
                    if (liftD != null) liftD[j] = d;
                    base[j] = (float)(document.tone(x0 + x, y0 + y) / (1 - (double)shown[j]));
                }
                for (int j = 4 * k; j < 4 * k + 4; j++) vfn[j] *= n / vs;
                if (lands != null || room != null) for (int v = 0; v < 2; v++) for (int u = 0; u < 2; u++) { int x = 2 * cx + u, y = 2 * cy + v; if (x < w && y < h) vfn[4 * k + 2 * v + u] *= lands != null ? lands[y * w + x] : room.inside(x0 + x, y0 + y) ? 1f : 0f; }
                pos[k] = cy * cw + cx; height[k] = (float)(hs / n); darkest[k] = most;
                S[k] = (float)(f * sum / n * (1 + .8 * settle * (rn.ahead(drawn + 1) - .5)));
                smax[k] = (float)(f * sum / n * 2.4);
                k++;
            }
        }
    }

    /** How finely {@link #valleys} tabulates exp: well under a float's step of vfn between entries. */
    private static final int VALLEY_STEPS = 1024;
    /** stroke.js 185's exp(G (.5 − height)) for heights 0 to 1, for {@link #valley}: a wash reads it at every pixel it reached. */
    private static float[] valleys(double G) {
        float[] t = new float[VALLEY_STEPS + 2];
        for (int i = 0; i <= VALLEY_STEPS; i++) t[i] = (float)Math.exp(G * (.5 - (double)i / VALLEY_STEPS));
        t[VALLEY_STEPS + 1] = t[VALLEY_STEPS];
        return t;
    }
    private static double valley(float[] t, float height) {
        double f = clamp(height) * VALLEY_STEPS;
        int i = (int)f;
        return t[i] + (t[i + 1] - t[i]) * (f - i);
    }
    /** Water's psi (stroke.js 145–147) at page pixel {@code u, v} at tick 0, its depth read at patch coordinates, clamped. */
    private double psi(float[] a, float[] c, float[] D, float[] depth, int u, int v) {
        int x = index(u - x0, w), y = index(v - y0, h);
        double dep = D[y * w + x] > .004 ? clamp((depth[(y >> 1) * cw + (x >> 1)] - .5) * 2.4) : 0;
        return (tile(a, 6, u, v) + .6 * tile(c, 5, u, v) - .8) * dep;
    }
    /** waterTile's lattice (stroke.js 87): one draw a cell of a 256-pixel tile. */
    private static float[] lattice(WildBrush.Random r, int cell) {
        int g = 256 / cell;
        float[] lat = new float[g * g];
        for (int i = 0; i < lat.length; i++) lat[i] = (float)r.next();
        return lat;
    }
    /**
     * waterTile's value at whole pixel {@code x, y} (stroke.js 88–96), which is sampleTile's there (97–101), worked out
     * from the lattice of cells 2^{@code shift} pixels wide.
     */
    private static float tile(float[] lat, int shift, int x, int y) {
        int cell = 1 << shift, g = 256 >> shift, m = g - 1, ix = x & 255, iy = y & 255, cx = ix >> shift, cy = iy >> shift;
        double sx = smooth((double)(ix & cell - 1) / cell), sy = smooth((double)(iy & cell - 1) / cell);
        int r0 = (cy & m) * g, r1 = ((cy + 1) & m) * g, c0 = cx & m, c1 = (cx + 1) & m;
        return (float)lerp(lerp(lat[r0 + c0], lat[r0 + c1], sx), lerp(lat[r1 + c0], lat[r1 + c1], sx), sy);
    }

    /** Still the page it was laid on: the same document, revision and active layer, shown. */
    boolean valid(ToneDocument document) {
        return document == this.document && document.revision() == revision && document.activeLayer() == layer && document.layerVisible(layer);
    }
    private static long lap(int k, long since) { Prof.add(k, since); return Prof.now(); }
    /** The flow moving it with the page's tilt, or null (for the checks). */
    GravityFlow flow() { return flow; }
    /** Whether it moves with the page's tilt while wet (the sensor need run): not once too big to ({@link #still}). */
    boolean moves() { return grav != null && !still; }
    /** One tick (100 ms) on a level page; false when it has finished. */
    boolean step(ToneDocument document) { return step(document, PageTilt.LEVEL); }
    /** One tick (100 ms) on the page tilted {@code tilt}; false when it has finished. One gesture, a continuation of the stroke's. */
    boolean step(ToneDocument document, PageTilt tilt) {
        Laid laid = work(tilt);
        apply(document, laid);
        return laid.more;
    }

    /**
     * What a step or laying it now lays on the page, worked out off it ({@link #work}, {@link #finishing}), for
     * {@link #apply} to write.
     */
    static final class Laid {
        /** The tones to write, in a gesture continuing the stroke's; null: nothing changes. */
        final StrokeTones tones;
        /** Whether it settles on after this: false after its last step, or laid now. */
        final boolean more;
        /** Whether the patch changed, so what laying it now lays did too. */
        final boolean changed;
        Laid(StrokeTones tones, boolean more, boolean changed) { this.tones = tones; this.more = more; this.changed = changed; }
    }
    /**
     * One tick's work on the page tilted {@code tilt} (100 ms of the patch's age), on any thread while nothing else
     * changes the page: the page is read, nothing written ({@link #apply} writes what it lays). Builds the patch first if
     * nothing has ({@link #build}).
     */
    Laid work(PageTilt tilt) {
        int a = age++;
        boolean last = a >= ticks - 1;
        if (darkness != null) build();
        // Gravity: the flow is made the first time the page is tilted while the wash is wet.
        long t0 = Prof.now();
        if (flow == null && grav != null && tones == null && !still && !last && !tilt.level() && count > 0) makeFlow(document, tilt);
        t0 = lap(Prof.G_MAKE, t0);
        StrokeTones out = new StrokeTones(document);
        boolean wrote = false, changed = false;
        // stroke.js 384: a patch steps on its even ages and on its last.
        if (settle > 0 && ((a & 1) == 0 || last)) { wrote = settle(out, a, last); changed = true; }
        t0 = lap(Prof.G_PATCH, t0);
        if (flow != null) { changed = true; if (flow.step(a, tilt)) flow = null; }
        t0 = Prof.now();
        if (tones != null) wrote |= tones.flush(out);
        Prof.add(Prof.G_WRITE, t0);
        return new Laid(wrote ? out : null, !last, changed || tones != null);
    }
    /**
     * Writes what {@link #work} or {@link #finishing} laid to {@code document} (the page's thread): one gesture, finished
     * as the stroke's continuation, one undo step with it.
     */
    void apply(ToneDocument document, Laid laid) {
        if (laid.tones == null) return;
        long t0 = System.nanoTime();
        document.begin(erasable, kind);
        laid.tones.applyTo(document, null);
        long t1 = System.nanoTime();
        document.finishContinuation();
        revision = document.revision();
        applyNanos = t1 - t0; finishNanos = System.nanoTime() - t1;
    }
    /** The last {@link #apply}'s writes and its continuation's finish, ns (for the bench). */
    long applyNanos, finishNanos;
    /** The flow over the patch as it shows now: the wash's darkness at lift and its share shown, pixel by pixel. */
    private void makeFlow(ToneDocument document, PageTilt tilt) {
        // The flow's cells: the stroke's lattice's, coarser for a wash over much of the page; too big, it doesn't move.
        int cells = GravityFlow.cells(w, h, grav, shift);
        if (cells == 0) { still = true; liftD = null; return; }
        float[] D = new float[w * h], now = new float[w * h];
        for (int k = 0; k < count; k++) for (int v = 0; v < 2; v++) for (int u = 0; u < 2; u++) {
            int j = 4 * k + 2 * v + u;
            if (base[j] < 0) continue;
            int i = (2 * (pos[k] / cw) + v) * w + 2 * (pos[k] % cw) + u;
            D[i] = liftD[j]; now[i] = shown[j];
        }
        tones = new GravityFlow.TonePage(document);
        flow = new GravityFlow(tones, flowPaper, x0, y0, w, h, D, now, grav, ticks, strokeSeed, tilt, cells);
        liftD = null;
    }

    /**
     * Lays everything still suspended where it is, at once: the last step, without drift. Before the first step it
     * reads the lift a row of cells at a time and lays each row as it goes, holding nothing patch-sized, to the same
     * pixels the whole patch would.
     */
    void finishNow(ToneDocument document) { apply(document, finishing()); }
    /**
     * What laying it now lays ({@link #finishNow}), worked out off the page as {@link #work} is. Once it is lifted
     * ({@link #lift}) and while gravity hasn't moved it ({@link #finishesAhead}) this changes nothing, so it can be
     * worked out ahead of a step and the patch step on; otherwise it is the end of the patch.
     */
    Laid finishing() { return finishing(null); }
    /**
     * {@link #finishing} on the page as it will be once {@code after} (a step's {@link Laid#tones}, not yet written; null:
     * none) is written: so laying it after a step can be worked out with the step, before either is written. (Under
     * masking fluid a pixel written shows lighter than asked; laying it now lays a pixel only where it falls below what
     * the step asked there, so it lays the same.)
     */
    Laid finishing(StrokeTones after) {
        StrokeTones out = new StrokeTones(document);
        boolean wrote = false;
        if (pos != null) {
            boolean ahead = tones == null;
            if (settle > 0) {
                // The last step's settle (stroke.js 233–237), without drift: in Sn, which steps only use as scratch.
                System.arraycopy(S, 0, Sn, 0, count);
                wrote = lay(out, 1, 0, rowStart.length - 1, !ahead, after);
            }
            if (flow != null) { flow.finish(); flow = null; }
            if (tones != null) wrote |= tones.flush(out);
            return new Laid(wrote ? out : null, false, true);
        }
        int ch = (h + 1) >> 1;
        hold(cw * Math.min(FINISH_ROWS, ch), ch);
        // Rows are read and laid on their own: a block at a time lays as a row at a time would, its pieces on the cores.
        for (int cy = 0; cy < ch; cy += FINISH_ROWS) {
            int cy1 = Math.min(ch, cy + FINISH_ROWS);
            lift(cy, cy1, kept(cy, cy1));
            System.arraycopy(S, 0, Sn, 0, count);
            wrote |= lay(out, 1, cy, cy1, true, null);
        }
        // Nothing is left suspended.
        count = 0; Arrays.fill(rowStart, 0);
        paper = null; darkness = null; lands = null; room = null; noise = null; heights = null; valleys = null;
        return new Laid(wrote ? out : null, false, true);
    }
    /**
     * Whether laying it now may be worked out ahead of a step ({@link #finishing} leaves it as it is once lifted): no flow
     * has moved it.
     */
    boolean finishesAhead() { return tones == null; }
    /** Its work in progress, on whatever thread, ends at its next row ({@link Stopped}): it is no longer wanted. */
    void stop() { stopped = true; }
    /** Thrown by work that was {@link #stop}ped: the patch is then left half-stepped, to be dropped. */
    static final class Stopped extends RuntimeException {
        Stopped() { super("drying wash stopped", null, false, false); }
    }
    private void live() { if (stopped) throw new Stopped(); }

    /** stepPatch (stroke.js 201–238): the pigment drifts (unless last), then a share of it is laid into {@code out}; whether anything was. */
    private boolean settle(StrokeTones out, int age, boolean last) {
        if (darkness != null) build();
        System.arraycopy(S, 0, Sn, 0, count);
        if (!last) flow(age);
        boolean wrote = lay(out, last ? 1 : 1 - Math.exp(-2 * lam * (.4 + 1.2 * age / ticks)), 0, rowStart.length - 1, true, null);
        float[] t = S; S = Sn; Sn = t;
        return wrote;
    }
    /** Cell rows a piece of {@link #lay} takes when it shares them out over the cores. */
    private static final int LAY_ROWS = 16;
    /** Cell rows an unlifted wash's {@link #finishing} lifts and lays at once: a piece of {@link #lay} for each worker. */
    private static final int FINISH_ROWS = Cores.WORKERS * LAY_ROWS;
    /**
     * stroke.js 220–237: a share {@code q} of each kept cell's pigment on grid rows {@code cy0} to {@code cy1} (in
     * {@link #Sn}) laid into {@code out}, a run of cells at a time; with {@code keep}, what shows is kept as the patch's
     * (else laid only into {@code out}: what laying it now would lay); on the page as it will be once {@code after} is
     * written (null: as it is). Whether anything was laid. Rows are laid on their own, so a big patch without a flow
     * shares them out over the cores ({@link Cores}).
     */
    private boolean lay(StrokeTones out, double q, int cy0, int cy1, boolean keep, StrokeTones after) {
        int pieces = (cy1 - cy0 + LAY_ROWS - 1) / LAY_ROWS;
        boolean[] any = new boolean[Cores.WORKERS];
        if (tones != null || pieces < 2 || (long)4 * (rowStart[cy1] - rowStart[cy0]) < Cores.SHARE_PX) any[0] = layRows(out, q, cy0, cy1, keep, after, 0);
        else Cores.each(pieces, (k, worker) -> { if (layRows(out, q, cy0 + k * LAY_ROWS, Math.min(cy1, cy0 + (k + 1) * LAY_ROWS), keep, after, worker)) any[worker] = true; });
        for (boolean a : any) if (a) return true;
        return false;
    }
    /** {@link #lay} over grid rows {@code cy0} to {@code cy1}, as {@code worker}. */
    private boolean layRows(StrokeTones out, double q, int cy0, int cy1, boolean keep, StrokeTones after, int worker) {
        int[] grays = this.grays[worker];
        if (grays == null) grays = this.grays[worker] = new int[4 * cw];
        boolean wrote = false;
        for (int cy = cy0; cy < cy1; cy++) {
            live();
            int py = 2 * cy, rh = Math.min(2, h - py);
            for (int k0 = rowStart[cy], end = rowStart[cy + 1]; k0 < end; ) {
                // A run of kept cells side by side is written at once.
                int k1 = k0 + 1;
                while (k1 < end && pos[k1] == pos[k1 - 1] + 1) k1++;
                int px = 2 * (pos[k0] % cw), rw = Math.min(2 * (pos[k1 - 1] % cw) + 2, w) - px;
                Arrays.fill(grays, 0, rw * rh, -1);
                boolean any = false;
                for (int k = k0; k < k1; k++) {
                    if (Sn[k] < 0) Sn[k] = 0;
                    double dq = Sn[k] * q;
                    if (dq <= 0) continue;
                    Sn[k] -= dq;
                    double cap = Math.max(SETTLED, darkest[k]);
                    for (int v = 0; v < 2; v++) for (int u = 0; u < 2; u++) {
                        int j = 4 * k + 2 * v + u;
                        if (base[j] < 0) continue;
                        double old = shown[j], nd = Math.min(cap, old + dq * vfn[j]);
                        if (nd <= old) continue;
                        float now = (float)nd;
                        if (keep) shown[j] = now;
                        // Gravity has moved the wash: what settles lands through the flow (the Gravity Lab's addFixed), or,
                        // once the flow is done, multiplies the tones it left.
                        if (tones != null) {
                            int qx = x0 + 2 * (pos[k] % cw) + u, qy = y0 + py + v;
                            if (flow != null) flow.addFixed(qx, qy, nd - old); else tones.mul(qx, qy, (1 - nd) / (1 - old));
                            continue;
                        }
                        long tone = Math.round(base[j] * (1 - (double)now));
                        // Only where the tone falls, and never lighter than the page is.
                        if (tone >= Math.round(base[j] * (1 - old))) continue;
                        int x = 2 * (pos[k] % cw) + u, y = py + v, shows = after == null ? -1 : after.tone(x0 + x, y0 + y);
                        if (tone < (shows >= 0 ? shows : document.tone(x0 + x, y0 + y))) { grays[v * rw + x - px] = (int)tone; any = true; }
                    }
                }
                if (any) { out.setTones(x0 + px, y0 + py, rw, rh, grays); wrote = true; }
                k0 = k1;
            }
        }
        return wrote;
    }

    /** stroke.js 205–219: each kept cell trades pigment with its kept neighbours to the right and below. */
    private void flow(int age) {
        double wet = 1 - (double)age / ticks, v = 2 * Math.pow(Math.max(0, wet), 1.4), st = settle;
        double kf = .5 * st * v, kv = 1.5 * st * v, ke = 2.2 * st * v * (1 - wet), kd = .1 * st * v, dif = .06 + .04 * v;
        blurS();
        float[] B = blurred;
        for (int c = 0; c < count; c++) {
            if ((c & 4095) == 0) live();
            if (pos[c] % cw + 1 < cw && c + 1 < count && pos[c + 1] == pos[c] + 1) {
                int n = c + 1;
                double u = kf * (B[n] - B[c]) / (B[n] + B[c] + 1e-4) + kv * (height[c] - height[n]) + ke * (inside[c] - inside[n]) + kd * (fx[c] + fx[n]) * .5;
                trade(c, n, u < -.3 ? -.3 : u > .3 ? .3 : u, dif);
            }
            int n = down[c];
            if (n >= 0) {
                double u = kf * (B[n] - B[c]) / (B[n] + B[c] + 1e-4) + kv * (height[c] - height[n]) + ke * (inside[c] - inside[n]) + kd * (fy[c] + fy[n]) * .5;
                trade(c, n, u < -.3 ? -.3 : u > .3 ? .3 : u, dif);
            }
        }
    }
    /** stroke.js flow: pigment moves from cell {@code c} to {@code n} at {@code u} (back for u < 0), only as far as there is room, and evens out. */
    private void trade(int c, int n, double u, double dif) {
        double flux = u > 0 ? S[c] * u * Math.max(0, 1 - S[n] / smax[n]) : S[n] * u * Math.max(0, 1 - S[c] / smax[c]);
        double d = (S[c] - S[n]) * dif;
        Sn[c] -= flux + d; Sn[n] += flux + d;
    }
    /**
     * stroke.js 206's B, blur(blur(S, 1), 1) over the cell grid, at the kept cells into {@link #blurred}: each row
     * blurred twice across, then each kept cell's column down; only rows near kept cells, and only as far across as
     * their kept cells reach, are worked out.
     */
    private void blurS() {
        int ch = rowStart.length - 1;
        if (ring == null) { ring = new float[5][cw]; ringRow = new int[5]; ringSpan = new int[10]; row = new float[cw]; once = new float[cw]; zeros = new float[cw]; }
        Arrays.fill(ringRow, -1);
        float[][] rows = new float[9][];
        for (int cy = 0; cy < ch; cy++) {
            int k0 = rowStart[cy], end = rowStart[cy + 1];
            if (k0 == end) continue;
            live();
            // Two clamped 3-row blurs: row cy's value is the mean of nine rows, each clamped twice.
            for (int a = -1; a <= 1; a++) {
                int r1 = index(cy + a, ch);
                for (int b = -1; b <= 1; b++) rows[3 * a + b + 4] = across(index(r1 + b, ch));
            }
            for (int k = k0; k < end; k++) {
                int x = pos[k] % cw;
                double sum = 0;
                for (float[] r : rows) sum += r[x];
                blurred[k] = (float)(sum / 9);
            }
        }
    }
    /** Grid row {@code cy} of S blurred twice across (clamped), from the ring if it is there; 0 beyond two cells of its kept cells. */
    private float[] across(int cy) {
        int slot = cy % 5;
        if (ringRow[slot] == cy) return ring[slot];
        int k0 = rowStart[cy], end = rowStart[cy + 1];
        if (k0 == end) return zeros;
        float[] out = ring[slot];
        if (ringRow[slot] >= 0) Arrays.fill(out, ringSpan[2 * slot], ringSpan[2 * slot + 1] + 1, 0);
        int lo = pos[k0] % cw, hi = pos[end - 1] % cw;
        for (int k = k0; k < end; k++) row[pos[k] % cw] = S[k];
        box3(row, once, lo - 1, hi + 1);
        box3(once, out, lo - 2, hi + 2);
        for (int k = k0; k < end; k++) row[pos[k] % cw] = 0;
        Arrays.fill(once, Math.max(0, lo - 1), Math.min(cw - 1, hi + 1) + 1, 0);
        ringRow[slot] = cy; ringSpan[2 * slot] = Math.max(0, lo - 2); ringSpan[2 * slot + 1] = Math.min(cw - 1, hi + 2);
        return out;
    }
    /** A clamped 3-wide box blur of {@code a} into {@code out} over columns {@code from} to {@code to}, kept on the grid. */
    private void box3(float[] a, float[] out, int from, int to) {
        int last = cw - 1;
        for (int x = Math.max(0, from), end = Math.min(last, to); x <= end; x++) out[x] = (float)((a[Math.max(0, x - 1)] + a[x] + (double)a[Math.min(last, x + 1)]) / 3);
    }

    /**
     * Water's height (stroke.js 137–140: .65 fine + .35 mid, half of it blurred by 1 px) at the patch's pixels, the
     * paper read only where asked, four rows at a time.
     */
    private static final class Heights {
        private final StrokePaper paper;
        private final int pageW, pageH, x0, y0;
        private final float[][] raw;
        private final int[] rowOf = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};

        Heights(StrokePaper paper, int pageW, int pageH, int x0, int y0, int w) {
            this.paper = paper; this.pageW = pageW; this.pageH = pageH; this.x0 = x0; this.y0 = y0;
            raw = new float[4][w + 3]; // w + 3: an odd width's last cell reads one pixel past w + 1
        }
        private final float[] window = new float[16], across = new float[8];
        /** Cell {@code cx, cy}'s four pixels' heights into {@code out} (2v + u), from the 4 × 4 pixels around them. */
        void cell(int cx, int cy, float[] out) {
            int x = 2 * cx - 1, y = 2 * cy - 1;
            for (int i = 0; i < 4; i++) {
                float[] r = row(y + i);
                for (int j = 0; j < 4; j++) {
                    float v = r[x + j + 1];
                    // Not yet read from the paper.
                    if (v != v) {
                        int px = index(x0 + x + j, pageW), py = index(y0 + y + i, pageH);
                        v = r[x + j + 1] = (float)(.65 * paper.fine(px, py) + .35 * paper.mid(px, py));
                    }
                    window[4 * i + j] = v;
                }
            }
            for (int i = 0; i < 4; i++) for (int u = 1; u <= 2; u++)
                across[2 * i + u - 1] = (float)((window[4 * i + u - 1] + window[4 * i + u] + (double)window[4 * i + u + 1]) / 3);
            for (int v = 0; v < 2; v++) for (int u = 0; u < 2; u++) {
                double b = (double)across[2 * v + u] + across[2 * (v + 1) + u] + across[2 * (v + 2) + u];
                out[2 * v + u] = (float)(.5 * window[4 * (v + 1) + u + 1] + .5 * (float)(b / 3));
            }
        }
        /** The paper's raw heights along patch row {@code y}, from one pixel before it to one after; NaN where not yet read. */
        private float[] row(int y) {
            int slot = (y + 1) & 3;
            float[] r = raw[slot];
            if (rowOf[slot] != y) { Arrays.fill(r, Float.NaN); rowOf[slot] = y; }
            return r;
        }
    }
}
