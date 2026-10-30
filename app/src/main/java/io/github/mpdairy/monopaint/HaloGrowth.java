package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.lerp;
import static io.github.mpdairy.monopaint.StrokeMath.smooth;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A Halo stroke's veins after lift (the new modules spec; the web lab's newmods.js Halo, Living Ink's Mould): scouts
 * leave the stroke's edge into a band of Reach around it, sense left, ahead and right, steer up their trail and toward
 * other marks, and lay trail; the trail diffuses and fades over the band only. A scout that reaches another mark carries
 * back to its own stroke and lays a bridge trail too; where traffic runs well above the band's mean a vein darkens, to
 * .7 of the stroke's darkness where the bridge trail runs and .2 elsewhere, so the veins that last are those between
 * marks. Vigour falls as it dries, so growth slows to nothing; it only darkens, and then never moves again.
 * <p>
 * The lab's arithmetic exactly ({@link #lab}; the app's own moves its scouts {@link #MOVES} times a tick and lets a black mark feed it, {@link #OWN_BLACK}) (its float32 arrays as floats, worked in doubles; its sines and cosines StrictMath's,
 * fdlibm as V8's; its hypot V8's), so the same page and samples grow the same veins. Its storage is the app's: only the
 * 32 px tiles within Reach of the stroke are kept, about 21 bytes a pixel of them, and the band is at most
 * {@link #MOST_BAND} pixels (a Reach that would pass it is trimmed). Built off the pen's thread ({@link #grow}, from
 * what the stroke left: {@link Lift}), stepped there ({@link #step}: it reads nothing on the page), and written on the
 * page's thread ({@link #apply}): each tick one gesture, a continuation of the stroke's, one undo step with it. Pure.
 */
final class HaloGrowth {
    /** newmods.js HALO_AGENTS and HALO_TUNE: the most scouts; sensors 8 px ahead at ±43°, a little wander, food worth five trail, veins .7, the rest .2, a vein at three times the band's mean. */
    static final int AGENTS = 2400;
    private static final double SD = 8, SA = 43 * LineStamper.DEG, WANDER = .18, FOOD = 5, VEIN = .7, FAINT = .2, THR = 3;
    /**
     * The most pixels a band holds: 2^19, half a million (a 200 px wash's stroke 1,100 px long at its widest Reach). A
     * Reach that would pass it is trimmed until it fits. About 11 MB of tiles at the cap, and on four cores about 4 ms a
     * tick on the desktop.
     */
    static final int MOST_BAND = 1 << 19;
    /** The tiles' side: 32 px (shift 5), page-aligned. */
    private static final int SHIFT = 5, SIDE = 1 << SHIFT, MASK = SIDE - 1;
    private static final float FAR = 1e9f;
    /** A pixel's flags: within Reach (the band), another mark there (newmods.js env over .5), the stroke's own mark (own .5 and over), its own mark food for another halo (own over .5). */
    private static final byte BAND = 1, FOOD_HERE = 2, SEED = 4, FEEDS = 8;
    /** While a page is read: black before the stroke and after it, which may be under the stroke or another mark ({@link Read}). */
    private static final byte BLACK = 16;
    /**
     * How near a pixel the stroke darkened a pixel black before and after must be to count as the stroke's own, px. Further
     * off it is another mark, food: newmods.js counts every such pixel in the box as the stroke's own, so Mold never bridged
     * to a black mark (the owner, 2026-10-22: "mold is very faint").
     */
    static final int OWN_BLACK = 2;
    /**
     * The scouts' moves a tick: newmods.js's two, or four in the app, so veins cross to a mark and darken in half the time
     * (the owner, 2026-10-22: "mold is very faint / light and slow"). Ten ticks a second, shown as before.
     */
    static final int MOVES = 4;
    /** The web lab's arithmetic exactly: two moves a tick, and black before and after always the stroke's own (the checks' port of the lab's pages). */
    static boolean lab;

    /**
     * What a Halo stroke leaves at its lift ({@link #grow}): its rectangle, its layer's tiles as it found them, and once
     * {@link #read} the page under its box: the stroke's own mark and the marks it found, page tile by page tile.
     */
    static final class Lift {
        final ToneDocument document;
        final int layer;
        /** The stroke's rectangle {left, top, right, bottom}, inclusive. */
        final int[] rect;
        /** Its layer's tiles as the stroke found them ({@link ToneDocument#foundTiles}). */
        final Map<Integer, byte[]> found;
        final ResolvedBrush.Halo halo;
        final int seed;
        final double erasable;
        final int kind;
        private Read read;
        Lift(ToneDocument document, int layer, int[] rect, Map<Integer, byte[]> found, ResolvedBrush.Halo halo, int seed, double erasable, int kind) {
            this.kind = kind; this.document = document; this.layer = layer; this.rect = rect; this.found = found; this.halo = halo; this.seed = seed; this.erasable = erasable;
        }
        /**
         * Reads the page under its box, once (the only part of building a halo that reads the page): on the worker after
         * the lift, or on the page's thread first if it changes the page sooner (a Halo stroke joining). Whoever comes
         * second waits for the first.
         */
        synchronized Read read() {
            if (read == null) read = new Read(page(this), rect, halo);
            return read;
        }
        /** Whether it has been read. */
        synchronized boolean wasRead() { return read != null; }
    }
    /** Read's flags at page pixel {@code px, py} (0 off the box or a tile with none). */
    private static int flags(Read r, int px, int py) {
        int T64 = ToneDocument.TILE, kx = px / T64 - r.kx0, ky = py / T64 - r.ky0;
        if (px < r.x0 || py < r.y0 || px >= r.x0 + r.w || py >= r.y0 + r.h) return 0;
        byte[] c = r.codes[ky * r.kcols + kx];
        return c == null ? 0 : c[(py % T64) * T64 + px % T64];
    }
    /** The page a halo is built from: each of {@link ToneDocument#TILE}'s tiles' composite before the stroke and after it. */
    interface Page {
        int width();
        int height();
        /** Whether the stroke changed tile {@code key}. */
        boolean changed(int key);
        /** Tile {@code key}'s composite after the stroke into {@code after}, and before it into {@code before} (null: not wanted), {@link ToneDocument#TILE} to a row. */
        void composites(int key, byte[] before, byte[] after);
    }
    /** A document after a stroke ({@link Lift}). */
    private static Page page(Lift lift) {
        ToneDocument d = lift.document;
        return new Page() {
            @Override public int width() { return d.width; }
            @Override public int height() { return d.height; }
            @Override public boolean changed(int key) { return lift.found.containsKey(key); }
            @Override public void composites(int key, byte[] before, byte[] after) {
                d.foundComposite(key, lift.layer, lift.kind, null, after);
                if (before != null) d.foundComposite(key, lift.layer, lift.kind, lift.found.get(key), before);
            }
        };
    }
    /**
     * The page under a halo's box as it reads ({@link Lift#read}): the box, and for each page tile of it with anything
     * there, each pixel's flags: the stroke's own mark (its darkness over what it found, over its own darkness, .5 and
     * over: newmods.js's own, which a pixel black before and after holds in full too) and other marks as it found them
     * (newmods.js env, 1 − b / paper, over .5); for Behind, the tones it found. Page tile rows are read on the cores.
     */
    static final class Read {
        final int pageW, pageH, x0, y0, w, h, kx0, ky0, kcols, krows;
        final byte[][] codes, unders;
        long nanos;
        /**
         * Black before and after is the stroke's own only within {@link #OWN_BLACK} of a pixel it darkened (under it, where
         * it crossed a black mark); else another black mark, food and never a seed.
         */
        private void ownBlack() {
            int T64 = ToneDocument.TILE;
            java.util.List<int[]> own = new java.util.ArrayList<>();
            for (int k = 0; k < codes.length; k++) {
                byte[] c = codes[k];
                if (c == null) continue;
                int px0 = (kx0 + k % kcols) * T64, py0 = (ky0 + k / kcols) * T64;
                for (int o = 0; o < c.length; o++) {
                    if ((c[o] & BLACK) == 0) continue;
                    int px = px0 + o % T64, py = py0 + o / T64;
                    boolean near = false;
                    for (int dy = -OWN_BLACK; dy <= OWN_BLACK && !near; dy++) for (int dx = -OWN_BLACK; dx <= OWN_BLACK && !near; dx++) {
                        int f = flags(this, px + dx, py + dy);
                        near = (f & SEED) != 0 && (f & BLACK) == 0;
                    }
                    if (near) own.add(new int[]{k, o});
                }
            }
            for (byte[] c : codes) if (c != null) for (int o = 0; o < c.length; o++) if ((c[o] & BLACK) != 0) c[o] = (byte) (c[o] & ~(BLACK | SEED | FEEDS));
            for (int[] p : own) codes[p[0]][p[1]] |= SEED | FEEDS;
        }
        Read(Page page, int[] rect, ResolvedBrush.Halo H) {
            long t0 = System.nanoTime();
            pageW = page.width(); pageH = page.height();
            int R = H.reach, T64 = ToneDocument.TILE, cols = (pageW + T64 - 1) / T64;
            x0 = Math.max(0, rect[0] - R - 3); y0 = Math.max(0, rect[1] - R - 3);
            int x1 = Math.min(pageW - 1, rect[2] + R + 3), y1 = Math.min(pageH - 1, rect[3] + R + 3);
            w = x1 - x0 + 1; h = y1 - y0 + 1;
            kx0 = x0 / T64; ky0 = y0 / T64; kcols = x1 / T64 - kx0 + 1; krows = y1 / T64 - ky0 + 1;
            codes = new byte[kcols * krows][]; unders = H.behind ? new byte[kcols * krows][] : null;
            double floor = Math.max(.05, H.dark);
            byte[][] befores = new byte[Cores.WORKERS][], afters = new byte[Cores.WORKERS][];
            Cores.Piece row = (ky, worker) -> {
                if (befores[worker] == null) { befores[worker] = new byte[T64 * T64]; afters[worker] = new byte[T64 * T64]; }
                byte[] before = befores[worker], after = afters[worker];
                for (int kx = 0; kx < kcols; kx++) {
                    int key = (ky0 + ky) * cols + kx0 + kx, px0 = (kx0 + kx) * T64, py0 = (ky0 + ky) * T64;
                    boolean changed = page.changed(key);
                    page.composites(key, changed ? before : null, after);
                    byte[] found = changed ? before : after, c = null;
                    for (int py = Math.max(y0, py0), pe = Math.min(y1, py0 + T64 - 1); py <= pe; py++)
                        for (int px = Math.max(x0, px0), xe = Math.min(x1, px0 + T64 - 1), o = (py - py0) * T64 + px - px0; px <= xe; px++, o++) {
                            int b = found[o] & 255, a = after[o] & 255;
                            float v = (float) clamp((1 - a / (double) Math.max(1, b)) / floor);
                            int f = (v >= .5f ? SEED | (v > .5f ? FEEDS : 0) | (a == 0 && b == 0 && !lab ? BLACK : 0) : 0) | (b <= 127 ? FOOD_HERE : 0);
                            if (f == 0) continue;
                            if (c == null) codes[ky * kcols + kx] = c = new byte[T64 * T64];
                            c[o] = (byte) f;
                        }
                    if (unders != null) unders[ky * kcols + kx] = found.clone();
                }
            };
            if ((long) w * h >= Cores.SHARE_PX && krows > 1) Cores.each(krows, row);
            else for (int ky = 0; ky < krows; ky++) row.run(ky, 0);
            ownBlack();
            nanos = System.nanoTime() - t0;
        }
    }

    /** A kept tile: the chamfer distance to the stroke, trail, bridge trail, accumulated traffic and vein level (newmods.js's arrays), flags. */
    private static final class Tile {
        final float[] dist = new float[SIDE * SIDE], T = new float[SIDE * SIDE], F = new float[SIDE * SIDE], acc = new float[SIDE * SIDE], lev = new float[SIDE * SIDE];
        final byte[] flag = new byte[SIDE * SIDE];
        /** The tone the page should show here, as the lab's float page ({@link #apply}, the page's thread); made on first write. */
        float[] want;
        /** Behind: the page under the vein as the lab's base, multiplied by each vein written; null for the others. */
        float[] under;
        Tile() { Arrays.fill(dist, FAR); }
    }

    private final ToneDocument document;
    final int layer;
    private long revision;
    /** The box on the page (the stroke's rectangle and Reach and 3 px around it), as newmods.js's. */
    final int x0, y0, w, h;
    /** Reach, page px (trimmed to keep the band within {@link #MOST_BAND}); the ticks it grows, the next one's number. */
    int reach;
    final int ticks;
    private int tick;
    private final double dark, rate, erasable;
    private final int kind;
    /** The kept tiles over the box's tile grid (page tiles {@link #tx0}, {@link #ty0} on), null where nothing is within Reach. */
    private final int tx0, ty0, tcols, trows;
    private final Tile[] tiles;
    /** How many pixels the band holds. */
    int band;
    private final WildBrush.Random r;
    /** The scouts: where, heading, ticks still to wait, carrying back, have found another mark. */
    private double[] ax, ay, ah;
    private int[] wait;
    private boolean[] back, fed;
    private int agents;
    /** Each worker's ({@link Cores}) rows of trail and bridge trail above, at and below the row a tick's diffusion works on. */
    private final float[][][] rows = new float[Cores.WORKERS][][];
    /** The band's old trail and bridge trail on each tile row's first and last box rows, kept before a tick's diffusion overwrites them. */
    private float[][] firstT, firstF, lastT, lastF;
    /** Its work is no longer wanted ({@link #stop}). */
    private volatile boolean stopped;

    private HaloGrowth(ToneDocument document, int layer, Read page, ResolvedBrush.Halo H, int seed, double erasable, int kind) {
        this.kind = kind; this.document = document; this.layer = layer;
        revision = document.revision();
        reach = H.reach; ticks = H.ticks; dark = H.dark; this.erasable = erasable;
        x0 = page.x0; y0 = page.y0; w = page.w; h = page.h;
        tx0 = x0 >> SHIFT; ty0 = y0 >> SHIFT; tcols = ((x0 + w - 1) >> SHIFT) - tx0 + 1; trows = ((y0 + h - 1) >> SHIFT) - ty0 + 1;
        tiles = new Tile[tcols * trows];
        rate = .04 * (.6 + .8 * H.growth);
        r = new WildBrush.Random(seed);
    }

    /**
     * newmods.js haloLift and Halo's constructor: the halo of the stroke {@code lift} describes, from the page under its
     * box as the stroke left it ({@link Lift#read}, read now if it hasn't been), feeding each of {@code growing} (halos
     * still growing on the page: the new stroke's mark is food for them too); null when it grows nothing (no edge).
     */
    static HaloGrowth grow(Lift lift, List<HaloGrowth> growing) {
        long t0 = System.nanoTime();
        Read page = lift.read();
        HaloGrowth g = new HaloGrowth(lift.document, lift.layer, page, lift.halo, lift.seed, lift.erasable, lift.kind);
        if (!g.build(page, lift.halo)) return null;
        if (growing != null) for (HaloGrowth o : growing) o.feedFrom(g);
        g.buildNanos = System.nanoTime() - t0;
        g.readNanos = page.nanos;
        return g;
    }
    /** How long {@link #grow} took, ns, and its {@link Lift#read} (for the bench and the session's timing log). */
    long buildNanos, readNanos;
    /** {@link #grow} from {@code page} (the checks: a page as the web lab saw it), writing on {@code document}'s layer {@code layer}. */
    static HaloGrowth grow(Page page, ToneDocument document, int layer, int[] rect, ResolvedBrush.Halo halo, int seed) {
        Read read = new Read(page, rect, halo);
        HaloGrowth g = new HaloGrowth(document, layer, read, halo, seed, 1, 0);
        return g.build(read, halo) ? g : null;
    }

    private Tile tile(int px, int py) {
        int tx = (px >> SHIFT) - tx0, ty = (py >> SHIFT) - ty0;
        return tx < 0 || ty < 0 || tx >= tcols || ty >= trows ? null : tiles[ty * tcols + tx];
    }
    private static int at(int px, int py) { return (py & MASK) << SHIFT | (px & MASK); }
    /** The distance at box pixel {@code bx, by}; far outside the box and the kept tiles. */
    private float dist(int bx, int by) {
        if (bx < 0 || by < 0 || bx >= w || by >= h) return FAR;
        Tile t = tile(x0 + bx, y0 + by);
        return t == null ? FAR : t.dist[at(x0 + bx, y0 + by)];
    }
    private boolean seed(int bx, int by) {
        Tile t = tile(x0 + bx, y0 + by);
        return t != null && (t.flag[at(x0 + bx, y0 + by)] & SEED) != 0;
    }
    /** Box row {@code by}'s kept tiles' columns: the box columns each covers, first and last, two to a tile (−1 for none). */
    private int[] spans(int by) {
        int ty = ((y0 + by) >> SHIFT) - ty0;
        int[] s = new int[2 * tcols];
        for (int tx = 0; tx < tcols; tx++) {
            if (tiles[ty * tcols + tx] == null) { s[2 * tx] = -1; continue; }
            s[2 * tx] = Math.max(0, ((tx0 + tx) << SHIFT) - x0); s[2 * tx + 1] = Math.min(w - 1, ((tx0 + tx) << SHIFT) + MASK - x0);
        }
        return s;
    }

    /**
     * The chamfer distance to its seeds, the band within Reach, its edge and its scouts (newmods.js Halo's
     * constructor), from the page as read. Whether it grows anything. Reads nothing on the page.
     */
    private boolean build(Read page, ResolvedBrush.Halo H) {
        int T64 = ToneDocument.TILE;
        // Which 32 px tiles hold seeds; every one within Reach of a seed's tile is kept (a pixel within Reach of a seed is
        // within Reach of its tile both ways).
        boolean[] seeded = new boolean[tiles.length];
        for (int k = 0; k < page.codes.length; k++) {
            byte[] c = page.codes[k];
            if (c == null) continue;
            int px0 = (page.kx0 + k % page.kcols) * T64, py0 = (page.ky0 + k / page.kcols) * T64;
            for (int o = 0; o < c.length; o++) if ((c[o] & SEED) != 0) seeded[(((py0 + o / T64) >> SHIFT) - ty0) * tcols + ((px0 + o % T64) >> SHIFT) - tx0] = true;
        }
        int R = reach, D = (R + MASK) >> SHIFT, keptCount = 0;
        boolean[] kept = new boolean[tiles.length];
        for (int ty = 0; ty < trows; ty++) for (int tx = 0; tx < tcols; tx++) {
            if (!seeded[ty * tcols + tx]) continue;
            for (int y = Math.max(0, ty - D); y <= Math.min(trows - 1, ty + D); y++) for (int x = Math.max(0, tx - D); x <= Math.min(tcols - 1, tx + D); x++)
                if (!kept[y * tcols + x]) { kept[y * tcols + x] = true; keptCount++; }
        }
        if (keptCount == 0) return false;
        // The kept tiles take the page's flags, a row of a tile at a time; a seed is 0 from the stroke.
        for (int k = 0; k < tiles.length; k++) {
            if (!kept[k]) continue;
            Tile t = tiles[k] = new Tile();
            int px0 = (tx0 + k % tcols) << SHIFT, py0 = (ty0 + k / tcols) << SHIFT;
            for (int v = 0; v < SIDE; v++) {
                int py = py0 + v;
                if (py < y0 || py >= y0 + h) continue;
                byte[] c = page.codes[(py / T64 - page.ky0) * page.kcols + px0 / T64 - page.kx0], u = page.unders == null ? null : page.unders[(py / T64 - page.ky0) * page.kcols + px0 / T64 - page.kx0];
                if (u != null && t.under == null) t.under = new float[SIDE * SIDE];
                for (int x = Math.max(px0, x0), xe = Math.min(px0 + MASK, x0 + w - 1); x <= xe; x++) {
                    int i = v << SHIFT | (x - px0), o = (py % T64) * T64 + x % T64;
                    if (u != null) t.under[i] = u[o] & 255;
                    if (c == null) continue;
                    byte f = c[o];
                    t.flag[i] = f;
                    if ((f & SEED) != 0) t.dist[i] = 0;
                }
            }
        }
        chamfer();
        // The box's edge is never in the band (newmods.js).
        for (int bx = 0; bx < w; bx++) { setFar(bx, 0); setFar(bx, h - 1); }
        for (int by = 0; by < h; by++) { setFar(0, by); setFar(w - 1, by); }
        // The band: within Reach. Trimmed until it fits.
        band = markBand(R);
        if (band > MOST_BAND) {
            while (band > MOST_BAND && reach > 2) { reach = Math.max(2, (int) (reach * Math.sqrt((double) MOST_BAND / band))); band = markBand(reach); }
            // Tiles the trimmed band doesn't reach are let go.
            for (int k = 0; k < tiles.length; k++) {
                Tile t = tiles[k];
                if (t == null) continue;
                boolean any = false;
                for (int i = 0; i < SIDE * SIDE && !any; i++) any = (t.flag[i] & (BAND | SEED)) != 0;
                if (!any) tiles[k] = null;
            }
        }
        // Its edge: seeds beside a pixel that isn't one, in the box's row order.
        int[] edges = new int[1024];
        int edgeCount = 0;
        for (int by = 1; by < h - 1; by++) for (int tx = 0; tx < tcols; tx++) {
            Tile t = tiles[(((y0 + by) >> SHIFT) - ty0) * tcols + tx];
            if (t == null) continue;
            int bxs = Math.max(1, ((tx0 + tx) << SHIFT) - x0), bxe = Math.min(w - 2, ((tx0 + tx) << SHIFT) + MASK - x0);
            for (int bx = bxs; bx <= bxe; bx++) {
                if ((t.flag[at(x0 + bx, y0 + by)] & SEED) == 0) continue;
                if (seed(bx - 1, by) && seed(bx + 1, by) && seed(bx, by - 1) && seed(bx, by + 1)) continue;
                if (edgeCount == edges.length) edges = Arrays.copyOf(edges, 2 * edgeCount);
                edges[edgeCount++] = by * w + bx;
            }
        }
        // The scouts, from the edge outward into the band (one draw short of the band is passed over).
        int K = (int) Math.min(AGENTS, Math.round(band * (.01 + .05 * H.growth)));
        ax = new double[K]; ay = new double[K]; ah = new double[K]; wait = new int[K]; back = new boolean[K]; fed = new boolean[K];
        R = reach;
        for (int k = 0; k < K && edgeCount > 0; k++) {
            int i = edges[(int) (r.next() * edgeCount)];
            double x = i % w + .5, y = i / w + .5;
            double[] n = outward(x, y);
            double out = 1.5 + r.next() * r.next() * R * .8;
            if (!inBand(x + n[0] * out, y + n[1] * out)) continue;
            ax[agents] = x + n[0] * out; ay[agents] = y + n[1] * out;
            ah[agents] = StrictMath.atan2(n[1], n[0]) + (r.next() - .5) * 2.4;
            wait[agents] = (int) Math.floor(r.next() * ticks * .15);
            agents++;
        }
        return agents > 0;
    }
    private void setFar(int bx, int by) { Tile t = tile(x0 + bx, y0 + by); if (t != null) t.dist[at(x0 + bx, y0 + by)] = FAR; }
    /** Marks the band within {@code R}: how many pixels it holds. */
    private int markBand(int R) {
        int n = 0;
        for (Tile t : tiles) {
            if (t == null) continue;
            for (int i = 0; i < SIDE * SIDE; i++) {
                if (t.dist[i] <= R) { t.flag[i] |= BAND; n++; } else t.flag[i] &= ~BAND;
            }
        }
        return n;
    }
    /**
     * newmods.js nmChamfer over the box (1 a step across, 1.414 diagonally, forward then back), a box row at a time in
     * two rows of the box's width, on the kept tiles: a pixel outside them is further than Reach from every seed, so
     * nothing within Reach comes through it.
     */
    private void chamfer() {
        float[] near = new float[w], cur = new float[w];
        Arrays.fill(near, FAR);
        for (int by = 0; by < h; by++) {
            int[] s = spans(by);
            loadDist(by, s, cur);
            for (int k = 0; k < tcols; k++) {
                if (s[2 * k] < 0) continue;
                for (int bx = s[2 * k], e = s[2 * k + 1]; bx <= e; bx++) {
                    double v = cur[bx];
                    if (bx > 0) v = Math.min(v, cur[bx - 1] + 1.0);
                    if (by > 0) {
                        v = Math.min(v, near[bx] + 1.0);
                        if (bx > 0) v = Math.min(v, near[bx - 1] + 1.414);
                        if (bx < w - 1) v = Math.min(v, near[bx + 1] + 1.414);
                    }
                    cur[bx] = (float) v;
                }
            }
            storeDist(by, s, cur);
            float[] t = near; near = cur; cur = t;
        }
        Arrays.fill(near, FAR);
        for (int by = h - 1; by >= 0; by--) {
            int[] s = spans(by);
            loadDist(by, s, cur);
            for (int k = tcols - 1; k >= 0; k--) {
                if (s[2 * k] < 0) continue;
                for (int bx = s[2 * k + 1], e = s[2 * k]; bx >= e; bx--) {
                    double v = cur[bx];
                    if (bx < w - 1) v = Math.min(v, cur[bx + 1] + 1.0);
                    if (by < h - 1) {
                        v = Math.min(v, near[bx] + 1.0);
                        if (bx < w - 1) v = Math.min(v, near[bx + 1] + 1.414);
                        if (bx > 0) v = Math.min(v, near[bx - 1] + 1.414);
                    }
                    cur[bx] = (float) v;
                }
            }
            storeDist(by, s, cur);
            float[] t = near; near = cur; cur = t;
        }
    }
    /** Box row {@code by}'s distances into {@code row} (far off the kept tiles {@code spans} lists), and back. */
    private void loadDist(int by, int[] spans, float[] row) {
        Arrays.fill(row, FAR);
        int py = y0 + by, ty = (py >> SHIFT) - ty0;
        for (int k = 0; k < tcols; k++) if (spans[2 * k] >= 0) System.arraycopy(tiles[ty * tcols + k].dist, at(x0 + spans[2 * k], py), row, spans[2 * k], spans[2 * k + 1] - spans[2 * k] + 1);
    }
    private void storeDist(int by, int[] spans, float[] row) {
        int py = y0 + by, ty = (py >> SHIFT) - ty0;
        for (int k = 0; k < tcols; k++) if (spans[2 * k] >= 0) System.arraycopy(row, spans[2 * k], tiles[ty * tcols + k].dist, at(x0 + spans[2 * k], py), spans[2 * k + 1] - spans[2 * k] + 1);
    }
    /** newmods.js inBand: box point {@code x, y} within Reach. */
    private boolean inBand(double x, double y) {
        if (!(x >= 0 && y >= 0 && x < w && y < h)) return false;
        Tile t = tile(x0 + (int) x, y0 + (int) y);
        return t != null && (t.flag[at(x0 + (int) x, y0 + (int) y)] & BAND) != 0;
    }
    /** newmods.js outward: the distance's slope at box point {@code x, y}, away from the stroke; a random way where it has none. */
    private double[] outward(double x, double y) {
        int xi = (int) clamp((int) x, 1, w - 2), yi = (int) clamp((int) y, 1, h - 2);
        double gx = (double) dist(xi + 1, yi) - dist(xi - 1, yi), gy = (double) dist(xi, yi + 1) - dist(xi, yi - 1), m = hypot(gx, gy);
        if (m < 1e-6 || m > 1e6) { double a = r.next() * 6.2832; return new double[]{StrictMath.cos(a), StrictMath.sin(a)}; }
        return new double[]{gx / m, gy / m};
    }
    /** V8's Math.hypot of two (the largest's square root of the sum of squares over it, Kahan-summed), so outward's slope is the lab's to the bit. */
    static double hypot(double a, double b) {
        a = Math.abs(a); b = Math.abs(b);
        double max = Math.max(a, b);
        if (max == 0) return 0;
        double sum = 0, comp = 0;
        for (double v : new double[]{a, b}) {
            double n = v / max, summand = n * n - comp, pre = sum + summand;
            comp = (pre - sum) - summand;
            sum = pre;
        }
        return Math.sqrt(sum) * max;
    }
    /**
     * newmods.js sense: trail at box point {@code x, y} and food there: for a scout seeking, another mark; for one
     * carrying back, its own stroke; −1 outside the band.
     */
    private double sense(double x, double y, boolean home) {
        if (!(x >= 0 && y >= 0 && x < w && y < h)) return -1;
        int px = x0 + (int) x, py = y0 + (int) y;
        Tile t = tile(px, py);
        if (t == null) return -1;
        int i = at(px, py), f = t.flag[i];
        if ((f & BAND) == 0) return -1;
        boolean food = home ? t.dist[i] <= 2 : (f & FOOD_HERE) != 0 && t.dist[i] > 2;
        return t.T[i] + (food ? FOOD : 0);
    }
    /** newmods.js feed: another Halo stroke ({@code o}) lifted while this one grows: its mark is food here too. */
    void feedFrom(HaloGrowth o) {
        for (int k = 0; k < o.tiles.length; k++) {
            Tile ot = o.tiles[k];
            if (ot == null) continue;
            int px0 = (o.tx0 + k % o.tcols) << SHIFT, py0 = (o.ty0 + k / o.tcols) << SHIFT;
            for (int i = 0; i < SIDE * SIDE; i++) {
                if ((ot.flag[i] & FEEDS) == 0) continue;
                int px = px0 + (i & MASK), py = py0 + (i >> SHIFT);
                if (px < x0 || py < y0 || px >= x0 + w || py >= y0 + h || px < o.x0 || py < o.y0 || px >= o.x0 + o.w || py >= o.y0 + o.h) continue;
                Tile t = tile(px, py);
                if (t != null) t.flag[at(px, py)] |= FOOD_HERE;
            }
        }
    }

    /** What a tick grew, for {@link #apply}: each pixel whose vein darkened (page index) and the factor its tone takes; the rectangle. */
    static final class Grown {
        final int[] at;
        final double[] f;
        final int count;
        final int[] rect;
        Grown(int[] at, double[] f, int count, int[] rect) { this.at = at; this.f = f; this.count = count; this.rect = rect; }
    }
    /** Whether it grows no more: all its ticks grown, or set. */
    boolean done() { return set || tick >= ticks; }
    /** How many ticks it has grown. */
    int age() { return tick; }
    /** Sets it where it is: it grows no more (newmods.js haloSet). */
    void set() { set = true; }
    private volatile boolean set;

    /**
     * newmods.js Halo.step: one tick (ten a second), the scouts' {@link #MOVES} moves (the lab's two), then the trail's diffusion and the veins over
     * the band, on any thread (it reads nothing on the page). Null when nothing darkened, or it has grown all its ticks.
     * The band's rows are shared out over the cores a tile row at a time; the result is the same on any number of them.
     */
    Grown step() {
        if (done()) return null;
        double v = StrictMath.pow(1 - (double) tick / ticks, 1.4), sa = SA * .5;
        for (int sub = 0, subs = lab ? 2 : MOVES; sub < subs; sub++) for (int a = 0; a < agents; a++) {
            if (wait[a] > 0) { if (sub == 0) wait[a]--; continue; }
            boolean bk = back[a];
            double x = ax[a], y = ay[a], hd = ah[a];
            double F = sense(x + StrictMath.cos(hd) * SD, y + StrictMath.sin(hd) * SD, bk);
            double Lv = sense(x + StrictMath.cos(hd - SA) * SD, y + StrictMath.sin(hd - SA) * SD, bk);
            double Rv = sense(x + StrictMath.cos(hd + SA) * SD, y + StrictMath.sin(hd + SA) * SD, bk);
            if (F >= Lv && F >= Rv) { } else if (Lv > Rv) hd -= sa; else if (Rv > Lv) hd += sa; else hd += r.next() < .5 ? -sa : sa;
            hd += (r.next() - .5) * WANDER;
            double sp = .3 + .8 * v, nx = x + StrictMath.cos(hd) * sp, ny = y + StrictMath.sin(hd) * sp;
            if (!inBand(nx, ny)) { ah[a] = hd + Math.PI * (.6 + .8 * r.next()); continue; }
            ax[a] = nx; ay[a] = ny;
            int px = x0 + (int) nx, py = y0 + (int) ny, i = at(px, py);
            Tile t = tile(px, py);
            t.T[i] += 1;
            // A scout that has found another mark lays the bridge trail too: only bridges darken in full.
            if (fed[a]) t.F[i] += 1;
            if (!back[a] && (t.flag[i] & FOOD_HERE) != 0 && t.dist[i] > 2) { back[a] = fed[a] = true; hd += Math.PI; }
            else if (back[a] && t.dist[i] <= 2.5) { back[a] = false; hd += Math.PI; }
            ah[a] = hd;
        }
        if (stopped) return null;
        // The trail diffuses and fades over the band, a tile row at a time.
        if (firstT == null) { firstT = new float[trows][]; firstF = new float[trows][]; lastT = new float[trows][]; lastF = new float[trows][]; }
        for (int tr = 0; tr < trows; tr++) {
            int first = Math.max(0, ((ty0 + tr) << SHIFT) - y0), last = Math.min(h - 1, ((ty0 + tr) << SHIFT) + MASK - y0);
            if (firstT[tr] == null) { firstT[tr] = new float[w]; firstF[tr] = new float[w]; lastT[tr] = new float[w]; lastF[tr] = new float[w]; }
            load(first, firstT[tr], firstF[tr]);
            load(last, lastT[tr], lastF[tr]);
        }
        double[] sums = new double[trows];
        int[][] hot = new int[trows][];
        int[] hotCount = new int[trows];
        Cores.Piece rowWork = (tr, worker) -> diffuse(tr, worker, sums, hot, hotCount);
        if ((long) band >= Cores.SHARE_PX && trows > 1) Cores.each(trows, rowWork);
        else for (int tr = 0; tr < trows; tr++) rowWork.run(tr, 0);
        if (stopped) return null;
        double sum = 0;
        for (double s : sums) sum += s;
        double thr = .6 + THR * sum / Math.max(1, band), rt = rate * v;
        // Where traffic runs well above the band's mean, the vein darkens: in the band's order.
        int n = 0;
        for (int c : hotCount) n += c;
        int[] at = new int[n];
        double[] f = new double[n];
        int count = 0, pageW = document.width;
        int l = Integer.MAX_VALUE, tp = Integer.MAX_VALUE, rr = -1, bt = -1;
        for (int tr = 0; tr < trows; tr++) {
            int[] list = hot[tr];
            for (int k = 0; k < hotCount[tr]; k++) {
                Tile t = tiles[list[k] >>> 10];
                int i = list[k] & 1023;
                double tv = t.T[i];
                if (!(tv > thr)) continue;
                double di = t.dist[i];
                t.acc[i] = (float) Math.min(1, t.acc[i] + (tv - thr) / thr * rt * smooth((di - 2) / 7));
                double a = smooth(t.acc[i]) * lerp(FAINT, VEIN, clamp(2 * t.F[i] / tv));
                float old = t.lev[i];
                if (!(a > old + .004)) continue;
                float lv = (float) a;
                t.lev[i] = lv;
                int ti = list[k] >>> 10, px = ((tx0 + ti % tcols) << SHIFT) + (i & MASK), py = ((ty0 + ti / tcols) << SHIFT) + (i >> SHIFT);
                // newmods.js write: the vein's new darkness multiplied in (so it commutes with every glaze), Behind's only where the page was blank.
                double fk = (1 - lv * dark) / (1 - old * dark);
                if (t.under != null) { fk = 1 - (1 - fk) * ModularCompose.blank(t.under[i]); t.under[i] = (float) (t.under[i] * fk); }
                at[count] = py * pageW + px; f[count++] = fk;
                if (px < l) l = px; if (px > rr) rr = px; if (py < tp) tp = py; if (py > bt) bt = py;
            }
        }
        tick++;
        return count == 0 ? null : new Grown(at, f, count, new int[]{l, tp, rr, bt});
    }
    /** Box row {@code by}'s trail and bridge trail into {@code T} and {@code F} (the box's width; 0 off the kept tiles). */
    private void load(int by, float[] T, float[] F) {
        Arrays.fill(T, 0); Arrays.fill(F, 0);
        int py = y0 + by, ty = (py >> SHIFT) - ty0;
        for (int tx = 0; tx < tcols; tx++) {
            Tile t = tiles[ty * tcols + tx];
            if (t == null) continue;
            int bxs = Math.max(0, ((tx0 + tx) << SHIFT) - x0), bxe = Math.min(w - 1, ((tx0 + tx) << SHIFT) + MASK - x0), i = at(x0 + bxs, py);
            System.arraycopy(t.T, i, T, bxs, bxe - bxs + 1);
            System.arraycopy(t.F, i, F, bxs, bxe - bxs + 1);
        }
    }
    /**
     * Tile row {@code tr}'s share of a tick's diffusion (newmods.js: three parts the pixel's own and one each of its four
     * neighbours', .88 of it kept), in place, its rows read from three rolling rows (its first row's above, and its last
     * row's below, as kept before the tick); its band's trail summed, and the pixels it leaves over .6 beyond 2 px out
     * (where a vein may darken) listed, a tile and a pixel each.
     */
    private void diffuse(int tr, int worker, double[] sums, int[][] hot, int[] hotCount) {
        int first = Math.max(0, ((ty0 + tr) << SHIFT) - y0), last = Math.min(h - 1, ((ty0 + tr) << SHIFT) + MASK - y0);
        float[][] rw = rows[worker];
        if (rw == null || rw[0].length < w) rw = rows[worker] = new float[6][w];
        float[] pT = rw[0], cT = rw[1], nT = rw[2], pF = rw[3], cF = rw[4], nF = rw[5];
        if (first > 0) { System.arraycopy(lastT[tr - 1], 0, pT, 0, w); System.arraycopy(lastF[tr - 1], 0, pF, 0, w); } else { Arrays.fill(pT, 0); Arrays.fill(pF, 0); }
        System.arraycopy(firstT[tr], 0, cT, 0, w); System.arraycopy(firstF[tr], 0, cF, 0, w);
        int[] list = new int[256];
        int count = 0;
        double sum = 0;
        for (int by = first; by <= last; by++) {
            if (by == last) {
                if (by + 1 < h && tr + 1 < trows) { System.arraycopy(firstT[tr + 1], 0, nT, 0, w); System.arraycopy(firstF[tr + 1], 0, nF, 0, w); }
                else { Arrays.fill(nT, 0); Arrays.fill(nF, 0); }
            } else load(by + 1, nT, nF);
            int py = y0 + by, ty = (py >> SHIFT) - ty0;
            for (int tx = 0; tx < tcols; tx++) {
                int ti = ty * tcols + tx;
                Tile t = tiles[ti];
                if (t == null) continue;
                int bxs = Math.max(0, ((tx0 + tx) << SHIFT) - x0), bxe = Math.min(w - 1, ((tx0 + tx) << SHIFT) + MASK - x0);
                for (int bx = bxs, i = at(x0 + bxs, py); bx <= bxe; bx++, i++) {
                    if ((t.flag[i] & BAND) == 0) continue;
                    double tv = .88 * ((double) cT[bx] * 3 + cT[bx - 1] + cT[bx + 1] + pT[bx] + nT[bx]) / 7;
                    sum += tv;
                    float tf = (float) tv;
                    t.T[i] = tf;
                    t.F[i] = (float) (.88 * ((double) cF[bx] * 3 + cF[bx - 1] + cF[bx + 1] + pF[bx] + nF[bx]) / 7);
                    if (tf > .6 && t.dist[i] > 2) {
                        if (count == list.length) list = Arrays.copyOf(list, 2 * count);
                        list[count++] = ti << 10 | i;
                    }
                }
            }
            float[] s = pT; pT = cT; cT = nT; nT = s;
            s = pF; pF = cF; cF = nF; nF = s;
            if (stopped) break;
        }
        sums[tr] = sum; hot[tr] = list; hotCount[tr] = count;
    }

    /** Still the page it grows on: the same document, unchanged since it last wrote (or a Halo stroke joined it), the same active layer, shown. */
    boolean valid(ToneDocument document) {
        return document == this.document && document.revision() == revision && document.activeLayer() == layer && document.layerVisible(layer);
    }
    /** The page has changed only as it should (a Halo stroke joined it): it stays valid. */
    void joined(ToneDocument document) { if (document == this.document) revision = document.revision(); }
    /** Its work in progress, on whatever thread, ends soon: it is no longer wanted. */
    void stop() { stopped = true; }

    /**
     * Writes what {@code tick} grew of {@code halos} on {@code document} (the page's thread): each pixel's tone, as the
     * lab's float page would hold it, times its factor, in one gesture as erasable as the stroke, finished as the
     * stroke's continuation, one undo step with it. The masking fluid shields it as any write. Each halo's ticks list
     * the halo they belong to, in order; null entries are skipped.
     */
    static void apply(ToneDocument document, List<HaloGrowth> halos, Grown[] grown) {
        boolean any = false;
        for (Grown g : grown) any |= g != null;
        if (!any) return;
        double erasable = 1;
        int kind = 0;
        for (HaloGrowth g : halos) { erasable = Math.min(erasable, g.erasable); kind |= g.kind; }
        document.begin(erasable, kind);
        int W = document.width;
        int[] row = new int[W];
        Arrays.fill(row, -1);
        for (int k = 0; k < grown.length; k++) {
            Grown g = grown[k];
            if (g == null) continue;
            HaloGrowth halo = halos.get(k);
            int y = -1, l = W, rr = -1;
            for (int j = 0; j <= g.count; j++) {
                int py = j < g.count ? g.at[j] / W : -2;
                if (py != y && y >= 0 && rr >= l) {
                    document.setTones(l, y, rr - l + 1, 1, Arrays.copyOfRange(row, l, rr + 1));
                    Arrays.fill(row, l, rr + 1, -1);
                    l = W; rr = -1;
                }
                if (j == g.count) break;
                y = py;
                int px = g.at[j] - py * W, cur = document.tone(px, py), i = at(px, py);
                // As the lab's page: the tone it should hold, unless something other than a halo has changed it since.
                float was = halo.want(px, py)[i];
                double base = was >= 0 && Math.round(was) == cur ? was : cur;
                float now = (float) (base * g.f[j]);
                // Every halo here holds it: the next to write here goes on from it.
                for (HaloGrowth o : halos) { float[] wt = o == halo ? halo.want(px, py) : o.wantIfAny(px, py); if (wt != null) wt[i] = now; }
                int gray = (int) Math.max(0, Math.min(255, Math.round(now)));
                if (gray == cur) continue;
                row[px] = gray;
                if (px < l) l = px;
                if (px > rr) rr = px;
            }
        }
        document.finishContinuation();
        long revision = document.revision();
        for (HaloGrowth g : halos) g.revision = revision;
    }

    /** The tones its tile at page pixel {@code px, py} should hold ({@link Tile#want}), made on first use; it must keep that tile. */
    private float[] want(int px, int py) {
        Tile t = tile(px, py);
        if (t.want == null) { t.want = new float[SIDE * SIDE]; Arrays.fill(t.want, -1); }
        return t.want;
    }
    /** {@link #want} where it keeps a tile at {@code px, py} (made on first use), else null. */
    private float[] wantIfAny(int px, int py) { return tile(px, py) == null ? null : want(px, py); }
    /** Grows it to the end at once on {@code document}, each tick written as {@link #apply} writes it (a sample's, a picture's). */
    void run(ToneDocument document) {
        List<HaloGrowth> one = java.util.Collections.singletonList(this);
        while (!done()) apply(document, one, new Grown[]{step()});
    }
    /** The veins' level at page pixel {@code x, y}, 0 to 1 (for the checks). */
    float level(int px, int py) { Tile t = tile(px, py); return t == null ? 0 : t.lev[at(px, py)]; }
    /** How many scouts it grows with (for the checks). */
    int scouts() { return agents; }
    /** About how many bytes it holds. */
    long bytes() { long n = 0; for (Tile t : tiles) if (t != null) n += SIDE * SIDE * (21 + (t.want != null ? 4 : 0) + (t.under != null ? 4 : 0)); return n + agents * 30L; }
}
