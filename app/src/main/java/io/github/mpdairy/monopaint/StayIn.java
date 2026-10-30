package io.github.mpdairy.monopaint;

/**
 * Stay in Lines' stroke (mischief-lab-2.html, section 1, "Stay in the lines", and its NOTES; {@link PartChoice#STAY_IN}):
 * the shape the stroke starts in, found once at pen-down from the selected pair's ink layer by Fill's region
 * ({@link FillRegion}: only crisp ink makes walls, a gap of about 4 px holds, a wedge fills to its point), and the
 * wash's flood through it.
 *
 * <p>Everything the stroke lays is kept to the shape ({@link #keep}). The flood is the prototype's bucket queue at page
 * resolution: each dab's rim wets its pixels at distance 0, and the wash runs on from there in chamfer steps (3 straight,
 * 4 diagonally, diagonally only where both sides are open), about the prototype's 3 px a frame by pen time, out to
 * {@link #reach} past the brush (Flood 0 the brush alone, 1 the whole shape). Past its reach a wash within a few px of a
 * line still runs on straight to it, so it never stops a sliver short; it runs under the line's edge one layer in, and
 * into the pockets the lines shut in once it reaches near them. Paler toward the far end, ×1.3 where it pools against a
 * line, ×1.22 in a rim where it stops short, mottled by the page's soft noise; a share of the dab's own darkness.
 *
 * <p>Bounded: the shape is looked for within {@link #MOST} page px of pen-down (one reaching past is no shape: the
 * stroke is a plain wash, as on open paper), so the work at pen-down and the memory (about 4 bytes a pixel of the
 * shape's box) are too; each sample settles at most {@link #BUDGET} pixels, the rest caught up as it goes and at the
 * lift ({@link #finish}). Pure, checked on the desktop ({@code StayInChecks}).
 */
final class StayIn {
    /** Page px a prototype px ({@link FillRegion#SCALE}). */
    static final double SCALE = FillRegion.SCALE;
    /** How far from pen-down the shape may reach each way, page px: about 5.4 cm on the Manta. */
    static final int MOST = 640;
    /** The flood's speed, distance units (thirds of a page px) a ms: the prototype's 3 px a 60 Hz frame. */
    static final double RATE = 9 * SCALE / (1000 / 60.0);
    /** Without pen times, distance units a sample. */
    static final int UNTIMED_STEP = (int) Math.round(9 * SCALE);
    /** The most pixels one sample settles; the rest wait for the next sample or the lift. */
    static final int BUDGET = 2000;
    /** Unreached; and a reach with no end (Flood 1, "Fills"). */
    static final int FAR = Short.MAX_VALUE, ENDLESS = Integer.MAX_VALUE / 4;
    /** The meeting the line rule's reach past the flood's, and the rim and the far end's paling, in distance units. */
    private static final int MEET = (int) Math.round(12 * SCALE), RIM = (int) Math.round(7 * SCALE), RIM_FROM = (int) Math.round(12 * SCALE);
    private static final double FADE_ALL = 1800 * SCALE;
    /** How near a line counts for the meeting the line rule, distance units (the prototype's 6 px). */
    private static final int EDGE_CAP = (int) Math.round(3 * 6 * SCALE);
    /** A pocket fills when the wash reaches a pixel this near it (the prototype's 12 px), page px. */
    private static final int ANCHOR = FillRegion.ANCHOR_PX;
    /** The keep's blocks: none of it in the shape, all of it, or some. */
    private static final int BLOCK_SHIFT = 4, NONE = 0, ALL = 2, SOME = 1;

    /** Flood ({@link PartDial#FLOOD}) in mm on the Manta's page: 220 × flood² prototype px. */
    static double mm(double flood) { return 220 * flood * flood * SCALE * 25.4 / 300; }
    /** How far the wash runs past the brush, distance units: 0 the brush alone; {@link #ENDLESS} past .995 ("Fills"). */
    static int reach(double flood) { return flood > .995 ? ENDLESS : (int) Math.round(3 * 220 * flood * flood * SCALE); }

    final FillRegion.Room room;
    private final int x0, y0, bw, bh, reach;
    private final byte[] kind;
    /** Each pixel's flood distance ({@link #FAR}: unreached), how wet it went (0–255), and its chamfer distance to the line. */
    private final short[] dist;
    private final byte[] val, edge;
    private final byte[] blocks;
    private final int bcols;
    /** Pockets: each anchor pixel's pockets, the pockets' pixels. */
    private final java.util.Map<Integer, int[]> anchors = new java.util.HashMap<>();
    private final java.util.BitSet anchored = new java.util.BitSet();
    private int[] pocketPx = new int[0];
    /** The bucket queue: a ring of eight buckets by key, each pixel and distance packed. */
    private final long[][] buckets = new long[8][];
    private final int[] sizes = new int[8];
    private int queued, key;
    private long t0 = PenSpeed.UNTIMED;
    private float tint;
    /** The last sample walked, and where the next rim falls along the stroke (the prototype's walk). */
    private int walked = -1;
    private double next;
    /** What the flood wet since the last call. */
    private int cx0, cy0, cx1, cy1;
    /** How many pixels it settled (for the checks). */
    long settled;

    /** The shape round page pixel {@code x, y} of a page whose ink is {@code ink}, within {@link #MOST}; null for none. */
    static FillRegion.Room shape(int width, int height, FillRegion.Ink ink, int x, int y) { return FillRegion.find(width, height, ink, x, y, MOST); }
    /**
     * The flood at {@code flood} of {@code document}'s stroke starting at {@code x, y}, in the shape layer {@code ink}'s
     * lines shut it in (the selected pair's ink layer, {@link FillTool#walls}); null for none. Reads only that layer.
     */
    static StayIn find(ToneDocument document, int ink, int x, int y, double flood) {
        FillRegion.Room room = shape(document.width, document.height, (bx, by, bw, bh, out) -> document.layerDarkness(ink, bx, by, bw, bh, out), x, y);
        return room == null ? null : new StayIn(room, flood);
    }

    /** A stroke's flood in {@code room} at {@code flood} (0–1). */
    StayIn(FillRegion.Room room, double flood) {
        this.room = room;
        x0 = room.x0; y0 = room.y0; bw = room.width; bh = room.height();
        reach = reach(flood);
        kind = room.at;
        int n = bw * bh;
        dist = new short[n]; java.util.Arrays.fill(dist, (short) FAR);
        val = new byte[n];
        edge = edges();
        bcols = ((bw - 1) >> BLOCK_SHIFT) + 1;
        blocks = blocks();
        pockets();
        resetChanged();
    }

    // ---- the shape's pixels ----

    /** Each pixel's chamfer distance (3, 4) to the nearest pixel not in the shape's open part or margin (the line, or what is past it), capped. */
    private byte[] edges() {
        int[] e = new int[bw * bh];
        for (int i = 0; i < e.length; i++) { int k = kind[i]; e[i] = k == FillRegion.OPEN || k == FillRegion.MARGIN || k == FillRegion.POCKET ? EDGE_CAP : 0; }
        for (int y = 0; y < bh; y++) for (int x = 0, i = y * bw; x < bw; x++, i++) {
            int v = e[i];
            if (v == 0) continue;
            if (x > 0) v = Math.min(v, e[i - 1] + 3);
            if (y > 0) { v = Math.min(v, e[i - bw] + 3); if (x > 0) v = Math.min(v, e[i - bw - 1] + 4); if (x < bw - 1) v = Math.min(v, e[i - bw + 1] + 4); }
            e[i] = v;
        }
        for (int y = bh - 1; y >= 0; y--) for (int x = bw - 1, i = y * bw + x; x >= 0; x--, i--) {
            int v = e[i];
            if (v == 0) continue;
            if (x < bw - 1) v = Math.min(v, e[i + 1] + 3);
            if (y < bh - 1) { v = Math.min(v, e[i + bw] + 3); if (x < bw - 1) v = Math.min(v, e[i + bw + 1] + 4); if (x > 0) v = Math.min(v, e[i + bw - 1] + 4); }
            e[i] = v;
        }
        byte[] out = new byte[e.length];
        for (int i = 0; i < e.length; i++) out[i] = (byte) Math.min(255, e[i]);
        return out;
    }
    /** The keep's blocks of 16 px: whether none, some or all of each is in the shape. */
    private byte[] blocks() {
        int brows = ((bh - 1) >> BLOCK_SHIFT) + 1;
        int[] in = new int[bcols * brows];
        for (int y = 0; y < bh; y++) for (int x = 0, i = y * bw; x < bw; x++, i++) if (kind[i] != FillRegion.OUT) in[(y >> BLOCK_SHIFT) * bcols + (x >> BLOCK_SHIFT)]++;
        byte[] b = new byte[in.length];
        for (int k = 0; k < in.length; k++) {
            int bx = (k % bcols) << BLOCK_SHIFT, by = (k / bcols) << BLOCK_SHIFT;
            int area = (Math.min(bw, bx + (1 << BLOCK_SHIFT)) - bx) * (Math.min(bh, by + (1 << BLOCK_SHIFT)) - by);
            b[k] = (byte) (in[k] == 0 ? NONE : in[k] == area ? ALL : SOME);
        }
        return b;
    }
    /**
     * The pockets (paper the lines shut in where they touch or cross): each 4-way piece of pocket pixels, and the pixel of
     * the shape nearest it within {@link #ANCHOR} (through the line), which wets it when the wash reaches there.
     */
    private void pockets() {
        int n = bw * bh, pieces = 0, total = 0;
        int[] mark = null, q = new int[256], ring = new int[256];
        for (int s = 0; s < n; s++) {
            if (kind[s] != FillRegion.POCKET || mark != null && mark[s] != 0) continue;
            if (mark == null) mark = new int[n];
            int id = ++pieces, head = 0, tail = 0;
            q[tail++] = s; mark[s] = id;
            while (head < tail) {
                int i = q[head++], x = i % bw;
                for (int k = 0; k < 4; k++) {
                    int j = k == 0 ? (x > 0 ? i - 1 : -1) : k == 1 ? (x < bw - 1 ? i + 1 : -1) : k == 2 ? i - bw : i + bw;
                    if (j < 0 || j >= n || kind[j] != FillRegion.POCKET || mark[j] != 0) continue;
                    mark[j] = id;
                    if (tail == q.length) q = java.util.Arrays.copyOf(q, tail * 2);
                    q[tail++] = j;
                }
            }
            int piece = tail;
            // Outward from the piece, through anything, to the nearest pixel of the shape that the wash can reach.
            int anchor = -1, rh = 0, rt = 0;
            for (int k = 0; k < piece; k++) { if (rt == ring.length) ring = java.util.Arrays.copyOf(ring, rt * 2); ring[rt++] = q[k]; }
            for (int level = 0; level < ANCHOR && anchor < 0 && rh < rt; level++) {
                int end = rt;
                while (rh < end && anchor < 0) {
                    int i = ring[rh++], x = i % bw;
                    for (int k = 0; k < 4; k++) {
                        int j = k == 0 ? (x > 0 ? i - 1 : -1) : k == 1 ? (x < bw - 1 ? i + 1 : -1) : k == 2 ? i - bw : i + bw;
                        if (j < 0 || j >= n || mark[j] == id || mark[j] == -id) continue;
                        int kj = kind[j];
                        if (kj >= FillRegion.OPEN && kj <= FillRegion.BEHIND) { anchor = j; break; }
                        mark[j] = kj == FillRegion.POCKET ? mark[j] : -id;
                        if (kj == FillRegion.POCKET) continue;
                        if (rt == ring.length) ring = java.util.Arrays.copyOf(ring, rt * 2);
                        ring[rt++] = j;
                    }
                }
            }
            // The marks the search left outside the piece, cleared.
            for (int k = piece; k < rt; k++) if (mark[ring[k]] == -id) mark[ring[k]] = 0;
            rh = rt = 0;
            if (anchor < 0) continue;
            if (pocketPx.length < total + piece) pocketPx = java.util.Arrays.copyOf(pocketPx, Math.max(2 * pocketPx.length, total + piece));
            System.arraycopy(q, 0, pocketPx, total, piece);
            int[] list = anchors.get(anchor);
            list = list == null ? new int[]{total, piece} : concat(list, total, piece);
            anchors.put(anchor, list);
            anchored.set(anchor);
            total += piece;
        }
    }
    private static int[] concat(int[] a, int at, int n) { int[] b = java.util.Arrays.copyOf(a, a.length + 2); b[a.length] = at; b[a.length + 1] = n; return b; }

    // ---- the flood ----

    /**
     * The samples not yet walked wet the shape under the brush's rim, {@code radius} page px, every .3 of it, at the
     * darkness {@code tint} its body lays (the latest's); the flood runs on to pen time {@code now} (the newest sample's,
     * kept or not: a resting pen's flood runs on), painting {@code into} (a stroke's body buffer, on its lattice).
     * Returns what it painted, or null.
     */
    int[] sample(StrokePoints pts, long now, double radius, float tint, FloatTiles into) {
        this.tint = tint;
        double r = Math.max(1, radius), gap = Math.max(1, r * .3);
        for (int k = walked + 1; k < pts.count; k++, walked++) {
            if (k == 0) { rim(pts.x[0], pts.y[0], r); next = gap; continue; }
            double d0 = pts.d[k - 1], d1 = pts.d[k], seg = d1 - d0;
            for (; next <= d1; next += gap) {
                double t = seg > 0 ? (next - d0) / seg : 1;
                rim(pts.x[k - 1] + (pts.x[k] - pts.x[k - 1]) * t, pts.y[k - 1] + (pts.y[k] - pts.y[k - 1]) * t, r);
            }
        }
        return step(now, into);
    }
    /** Wets the shape's pixels on the circle of radius {@code r} round {@code x, y} (and its centre), at distance 0. */
    private void rim(double x, double y, double r) {
        push((int) Math.floor(x), (int) Math.floor(y), 0);
        int n = Math.max(8, (int) Math.ceil(2 * Math.PI * r * 1.5));
        for (int k = 0; k < n; k++) {
            double a = 2 * Math.PI * k / n;
            push((int) Math.floor(x + r * Math.cos(a)), (int) Math.floor(y + r * Math.sin(a)), 0);
        }
    }
    private void push(int px, int py, int d) {
        int x = px - x0, y = py - y0;
        if (x < 0 || y < 0 || x >= bw || y >= bh) return;
        int i = y * bw + x, k = kind[i];
        if (k == FillRegion.OUT || k == FillRegion.POCKET) return;
        put(i, d, key);
    }
    /** wetPush: pixel {@code i} wet at distance {@code d} when the flood's key reaches {@code at}. */
    private void put(int i, int d, int at) {
        if (d >= dist[i]) return;
        dist[i] = (short) Math.min(FAR - 1, d);
        int b = at & 7;
        long[] q = buckets[b];
        if (q == null) q = buckets[b] = new long[256];
        else if (sizes[b] == q.length) q = buckets[b] = java.util.Arrays.copyOf(q, q.length * 2);
        q[sizes[b]++] = ((long) dist[i] << 32) | i;
        queued++;
    }
    /** The flood on to pen time {@code now} (at most {@link #BUDGET} pixels), painting {@code into}; what it painted, or null. */
    int[] step(long now, FloatTiles into) {
        if (t0 == PenSpeed.UNTIMED && now != PenSpeed.UNTIMED) t0 = now;
        long target = now == PenSpeed.UNTIMED || t0 == PenSpeed.UNTIMED ? key + UNTIMED_STEP : (long) ((now - t0) * RATE);
        run(target, BUDGET, into);
        return changed();
    }
    /** The lift: the flood run to its end, painting {@code into}; what it painted, or null. */
    int[] finish(FloatTiles into) {
        run(Long.MAX_VALUE, Long.MAX_VALUE, into);
        return changed();
    }
    /** Whether the flood has more to do. */
    boolean flooding() { return queued > 0; }
    private void run(long target, long most, FloatTiles into) {
        long work = 0;
        while (queued > 0 && key < target && work < most) {
            int b = key & 7, n = sizes[b];
            if (n == 0) { key++; continue; }
            // A bucket may grow while it settles (a pocket's pixels at key + 3 never land in it): take its pixels first.
            long[] q = buckets[b];
            sizes[b] = 0;
            queued -= n;
            for (int k = 0; k < n; k++) { settle((int) q[k], (int) (q[k] >>> 32), into); work++; }
            key++;
        }
        if (queued == 0 && target != Long.MAX_VALUE && key < target) key = (int) Math.min(Integer.MAX_VALUE / 2, target);
        settled += work;
    }
    /** settle1: pixel {@code i} goes wet at distance {@code d}, then the flood runs on from it. */
    private void settle(int i, int d, FloatTiles into) {
        if (dist[i] != d) return;
        int x = i % bw, y = i / bw, me = kind[i];
        paint(i, x, y, d, me, into);
        int[] pocket = anchored.get(i) ? anchors.get(i) : null;
        if (pocket != null) for (int k = 0; k < pocket.length; k += 2) for (int p = pocket[k], end = p + pocket[k + 1]; p < end; p++) put(pocketPx[p], d + 3, key + 3);
        if (me >= FillRegion.BEHIND) return;
        if (me == FillRegion.UNDER) {
            if (x > 0) go(i, i - 1, d, 3); if (x < bw - 1) go(i, i + 1, d, 3); if (y > 0) go(i, i - bw, d, 3); if (y < bh - 1) go(i, i + bw, d, 3);
            return;
        }
        if (x > 0) go(i, i - 1, d, 3); if (x < bw - 1) go(i, i + 1, d, 3); if (y > 0) go(i, i - bw, d, 3); if (y < bh - 1) go(i, i + bw, d, 3);
        if (d >= reach) return;
        // Diagonally only where both sides are open, so the wash never slips through a corner of a line.
        boolean l = x > 0 && open(i - 1), r = x < bw - 1 && open(i + 1), u = y > 0 && open(i - bw), dn = y < bh - 1 && open(i + bw);
        if (l && u && open(i - bw - 1)) go(i, i - bw - 1, d, 4); if (r && u && open(i - bw + 1)) go(i, i - bw + 1, d, 4);
        if (l && dn && open(i + bw - 1)) go(i, i + bw - 1, d, 4); if (r && dn && open(i + bw + 1)) go(i, i + bw + 1, d, 4);
    }
    private boolean open(int j) { int k = kind[j]; return k == FillRegion.OPEN || k == FillRegion.MARGIN; }
    /**
     * The flood from {@code i} to {@code j}: within its reach; past it onto the line, or strictly nearer a line (so it
     * meets the line, creeping a few px along it at most), or along the margin no farther from it a little way on.
     */
    private void go(int i, int j, int d, int step) {
        int to = kind[j], me = kind[i];
        if (to == FillRegion.OUT || to == FillRegion.POCKET || (me == FillRegion.UNDER ? to != FillRegion.BEHIND : to == FillRegion.BEHIND)) return;
        int dd = d + step, ej = edge[j] & 255, ei = edge[i] & 255;
        if (dd <= reach || to >= FillRegion.UNDER || ej < ei || to == FillRegion.MARGIN && ej <= ei && dd <= reach + MEET) put(j, dd, key + step);
    }
    /** wet1: how wet pixel {@code i} goes at distance {@code d}, painted on {@code into} where stronger. */
    private void paint(int i, int x, int y, int d, int me, FloatTiles into) {
        int px = x + x0, py = y + y0;
        double u = reach >= ENDLESS ? Math.min(1, d / FADE_ALL) : reach > 0 ? Math.min(1, d / (double) reach) : 0;
        double v = tint * (1 - .4 * u * u) * (.88 + .24 * PaperNoise.blob(px >> 1, py >> 1) / 255.0);
        if (me >= FillRegion.MARGIN) v *= 1.3;
        else if (reach < ENDLESS && reach > RIM_FROM && reach - d < RIM) v *= 1.22;
        int q = (int) Math.round(Math.min(1, v) * 255);
        if (q <= (val[i] & 255)) return;
        val[i] = (byte) q;
        int step = into.step();
        if (((px | py) & (step - 1)) != 0) return;
        if (into.raise(px, py, q / 255f)) {
            if (px < cx0) cx0 = px; if (px > cx1) cx1 = px; if (py < cy0) cy0 = py; if (py > cy1) cy1 = py;
        }
    }
    private int[] changed() {
        if (cx1 < cx0) return null;
        int[] out = {cx0, cy0, cx1, cy1};
        resetChanged();
        return out;
    }
    private void resetChanged() { cx0 = cy0 = Integer.MAX_VALUE; cx1 = cy1 = Integer.MIN_VALUE; }

    // ---- the stroke's buffers ----

    /** How wet the flood left page pixel {@code x, y}, 0–1. */
    float wet(int x, int y) { return room.inside(x, y) ? (val[(y - y0) * bw + x - x0] & 255) / 255f : 0; }
    /** The flood's distance at page pixel {@code x, y} (distance units, thirds of a page px); {@link #FAR} unreached. */
    int distance(int x, int y) { return room.inside(x, y) ? dist[(y - y0) * bw + x - x0] : FAR; }

    /** {@code t} kept to the shape over {@code at} (page px): {@link FloatTiles#fill} outside it. */
    void keep(FloatTiles t, int[] at) {
        if (t == null || StrokeMath.empty(at)) return;
        int step = t.step(), xa = t.up(Math.max(0, at[0])), xb = Math.min(t.width - 1, at[2]);
        if (xb < xa) return;
        int n = (xb - xa) / step + 1;
        float[] row = new float[n];
        for (int y = t.up(Math.max(0, at[1])); y <= Math.min(t.height - 1, at[3]); y += step) {
            boolean read = false, changed = false;
            for (int k = 0, x = xa; k < n; k++, x += step) {
                int s = block(x, y);
                if (s == ALL) { k += skipAll(x, y, step); x = xa + k * step; continue; }
                if (!read) { t.row(xa >> t.shift, y >> t.shift, n, row, 0); read = true; }
                if (row[k] != t.fill && (s == NONE || !room.inside(x, y))) { row[k] = t.fill; changed = true; }
            }
            if (changed) t.setRow(xa >> t.shift, y >> t.shift, n, row, 0);
        }
    }
    private int block(int px, int py) {
        int x = px - x0, y = py - y0;
        if (x < 0 || y < 0 || x >= bw || y >= bh) return NONE;
        return blocks[(y >> BLOCK_SHIFT) * bcols + (x >> BLOCK_SHIFT)];
    }
    /** Lattice points to skip after {@code x} to the end of its all-in block. */
    private int skipAll(int px, int py, int step) {
        int end = x0 + (((px - x0) >> BLOCK_SHIFT) + 1 << BLOCK_SHIFT);
        return Math.max(0, (end - px - 1) / step);
    }
    /** The flood laid again on {@code into} over {@code at} (page px; the whole flood for null), where stronger: a body laid afresh. */
    void relay(FloatTiles into, int[] at) {
        int xa = Math.max(x0, at == null ? x0 : at[0]), xb = Math.min(x0 + bw - 1, at == null ? x0 + bw - 1 : at[2]);
        int ya = Math.max(y0, at == null ? y0 : at[1]), yb = Math.min(y0 + bh - 1, at == null ? y0 + bh - 1 : at[3]);
        int step = into.step();
        for (int y = into.up(ya); y <= yb; y += step) for (int x = into.up(xa), i = (y - y0) * bw + x - x0; x <= xb; x += step, i += step) {
            int q = val[i] & 255;
            if (q > 0) into.raise(x, y, q / 255f);
        }
    }
    /** The flood's box, page px, inclusive: the shape's. */
    int[] box() { return new int[]{x0, y0, x0 + bw - 1, y0 + bh - 1}; }
}
