package io.github.mpdairy.monopaint;

/**
 * Fill's region: the area a tap is in, shut in by the page's crisp ink lines, the owner's "Stay in the lines"
 * (docs/archive/brush-lab/prototypes/mischief-lab-2.html, section 1, room1 and walls1, and its NOTES).
 *
 * <p>Only the walls' layer makes walls (the selected pair's ink layer: a wash or any soft mark never holds a fill),
 * and only its line pixels, darker than {@link #LINE}. They are thickened by {@link #THICKEN} only where the ink near
 * a cell (within {@link #BOX}) is two pieces, or one piece in two parts there that meet only a long way round along
 * the ink (over {@link #GEO_K} times their distance apart there, and over {@link #GEO_MIN}), and only where one of them
 * ends there: a gap between line ends, which holds, never a wedge's sides, which meet near by at its point or run on
 * side by side, so a wedge or a corner fills to its tip, wobbling or not. The region is the open cells four ways from
 * the tap (the nearest open cell within {@link #START}), plus:
 * <ul>
 * <li>its margin ({@link #MARGIN}): the thickened wall on this side of the lines, reached four ways, each step no
 * farther from a line, so never through a gap;</li>
 * <li>the line's own pixels next to it ({@link #UNDER}) and one layer behind them ({@link #BEHIND}), so the fill runs
 * under the line's edge and no paper rim shows;</li>
 * <li>pockets ({@link #POCKET}): paper shut in where lines touch or cross, a few cells or a thin sliver, within
 * {@link #ANCHOR} of the region through the line.</li>
 * </ul>
 * Open paper that reaches the page's edge is no region: {@link #find} gives null, as for a tap with no open cell near.
 *
 * <p>The prototype's sizes are CSS pixels (one canvas pixel each); the page has {@link #SCALE} page px a CSS pixel,
 * the Manta's (and the Nomad's) 300 ppi over the web's 160 dp, as the labs' pictures are drawn (Brush Studio's NOTES).
 * So the walls, the flood, the margin and the pockets are worked out in cells of {@link #CELL} × {@link #CELL} page px
 * (a cell as dark as its darkest pixel), about the prototype's own pixel (1.07 of them), its sizes in cells; then the
 * region is drawn at page resolution ({@link Refine}): its cells' pixels, the light edge pixels of the line cells beside
 * them, and the line pixels under the fill's edge. The work is lazy: the ink is read, and its distances and walls worked
 * out, only in the tiles and at the cells the flood reaches, never over the whole page. Pure, checked on the desktop
 * ({@code FillRegionChecks}).
 */
final class FillRegion {
    /** The layer the walls are read from: how dark it is, 0 where clear to 255 for black, over a block, row by row; false (and {@code out} untouched) for a block with none. */
    interface Ink { boolean darkness(int x, int y, int w, int h, int[] out); }

    /** Page pixels a prototype (CSS) pixel: 300 ppi over 160 dp. */
    static final double SCALE = 1.875;
    /** A cell's side, page px: the walls and the flood are worked out in cells, about the prototype's pixel. */
    static final int CELL = 2;
    /** A line pixel is darker than this (the prototype's ink over .4). */
    static final int LINE = 102;
    /**
     * How far a wall is thickened where the gap is real, cells: the prototype's two 3 × 3 max passes (2 px), which over
     * its antialiased line hold open gaps of 4 px and, by how the pixels fall, often 5.
     */
    static final int THICKEN = cells(2);
    /** The box looked at round a cell for the ink near it, cells each way (the prototype's 6 px). */
    static final int BOX = cells(6);
    /** A gap: two parts of the ink meeting only over this many times their distance apart, along the ink, and over {@link #GEO_MIN} cells (the prototype's 20 px). */
    static final double GEO_K = 14;
    static final int GEO_MIN = cells(20);
    /** The tap's nearest open cell within this, cells (the prototype's 8 px). */
    static final int START = cells(8);
    /** The margin starts beside a line nearer than this (squared, cells; the prototype's 5 px). */
    static final int MARGIN_REACH2 = (int)Math.ceil(sq(5 * SCALE / CELL)) - 1;
    /** Pockets: up to this many cells (the prototype's 24 px), or up to {@link #POCKET_MOST} (its 1000) none over {@link #POCKET_THIN2} from a line (squared; its 3.2 px). */
    static final int POCKET_FEW = (int)Math.round(24 * sq(SCALE / CELL)), POCKET_MOST = (int)Math.round(1000 * sq(SCALE / CELL)), POCKET_THIN2 = (int)Math.floor(sq(3.2 * SCALE / CELL));
    /** A pocket fills when it is within this many line cells of the region (the prototype's 12 px). */
    static final int ANCHOR = cells(12);
    /** Pockets too small for the cells, page px: the prototype's 12 px through the line, 24 px of paper, or 1000 in line cells. */
    static final int ANCHOR_PX = (int)Math.round(12 * SCALE), POCKET_FEW_PX = (int)Math.round(24 * SCALE * SCALE), POCKET_MOST_PX = (int)Math.round(1000 * SCALE * SCALE);
    /** How far, page px, the fill runs from its cells into the light edge pixels of the line cells beside them. */
    static final int EDGE_REACH = 24;

    /** What a pixel of a {@link Room} is: outside, open, the margin, a line pixel under the fill, the layer behind it, a pocket. */
    static final byte OUT = 0, OPEN = 1, MARGIN = 2, UNDER = 3, BEHIND = 4, POCKET = 5;

    /** A region: its box (inclusive) and each pixel's kind over it, row by row. */
    static final class Room {
        final int x0, y0, x1, y1, width, area;
        final byte[] at;
        Room(int x0, int y0, int x1, int y1, byte[] at, int area) { this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1; this.at = at; this.area = area; width = x1 - x0 + 1; }
        /** The pixel's kind ({@link #OPEN} …), {@link #OUT} off the box. */
        byte kind(int x, int y) { return x < x0 || y < y0 || x > x1 || y > y1 ? OUT : at[(y - y0) * width + x - x0]; }
        boolean inside(int x, int y) { return kind(x, y) != OUT; }
        int height() { return y1 - y0 + 1; }
    }

    /**
     * The region round page pixel {@code x, y} of a {@code width × height} page whose walls are {@code ink}; null when
     * there is none: no open cell near the tap, or open paper reaching the page's edge.
     */
    static Room find(int width, int height, Ink ink, int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return null;
        Finder f = Finder.of((width + CELL - 1) / CELL, (height + CELL - 1) / CELL);
        Room cells;
        try { cells = f.find(new Cells(ink, width, height), x / CELL, y / CELL); }
        finally { f.release(); }
        return cells == null ? null : Refine.timed(cells, width, height, ink);
    }

    /**
     * As {@link #find}, the page's edges a wall too, as Fill takes them (the owner, 2026-10-22: "i just used the paint
     * bucket on a blank page and it said no closed area.. it should fill the page ideally"): open paper reaching the edge
     * is the region up to it (a blank page's is the whole page); null only when no open cell is near the tap.
     */
    static Room filled(int width, int height, Ink ink, int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return null;
        // No line pixel on the walls' layer (a blank page): the whole page, open, as the search finds it, without it.
        if (blankAtOnce && blank(width, height, ink, y)) {
            byte[] at = new byte[width * height];
            java.util.Arrays.fill(at, OPEN);
            return new Room(0, 0, width - 1, height - 1, at, width * height);
        }
        Finder f = Finder.of((width + CELL - 1) / CELL, (height + CELL - 1) / CELL);
        Room cells;
        try { f.edgeWalls = true; cells = f.find(new Cells(ink, width, height), x / CELL, y / CELL); }
        finally { f.edgeWalls = false; f.release(); }
        return cells == null ? null : Refine.timed(cells, width, height, ink);
    }

    /** Whether {@link #filled} takes a page with no line pixel whole at once; off in the checks that show the search finds the same. */
    static boolean blankAtOnce = true;
    /**
     * Whether {@code ink} has no line pixel (none darker than {@link #LINE}) on a {@code width × height} page, read a
     * band of rows at a time until one is met, from the band holding row {@code y} (a tap's: the lines round it cross
     * its row, so a fill inside them reads one band).
     */
    static boolean blank(int width, int height, Ink ink, int y) {
        int rows = Math.max(1, Math.min(height, Refine.READ_PX / width)), bands = (height - 1) / rows + 1, first = Math.max(0, Math.min(height - 1, y)) / rows;
        int[] dark = new int[width * rows];
        for (int k = 0; k < bands; k++) {
            int top = (first + k) % bands * rows, h = Math.min(rows, height - top);
            if (ink.darkness(0, top, width, h, dark)) for (int i = 0; i < width * h; i++) if (dark[i] > LINE) return false;
        }
        return true;
    }

    /**
     * As {@link #find}, the area's open cells bounded to those within {@code most} page px of the tap each way: an area
     * reaching past that is no region (null), as open paper is, and the work stays within the box (Stay in Lines'
     * stroke, {@link StayIn}, finds its area at pen-down).
     */
    static Room find(int width, int height, Ink ink, int x, int y, int most) {
        if (x < 0 || y < 0 || x >= width || y >= height) return null;
        int cw = (width + CELL - 1) / CELL, ch = (height + CELL - 1) / CELL, m = Math.max(1, most / CELL), cx = x / CELL, cy = y / CELL;
        Finder f = Finder.of(cw, ch);
        Room cells;
        try { cells = f.find(new Cells(ink, width, height), cx, cy, Math.max(0, cx - m), Math.max(0, cy - m), Math.min(cw - 1, cx + m), Math.min(ch - 1, cy + m)); }
        finally { f.release(); }
        return cells == null ? null : Refine.timed(cells, width, height, ink);
    }

    /** As {@link #find}, with arrays of its own (not those kept from the last find): for the checks, which show the two the same. */
    static Room fresh(int width, int height, Ink ink, int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return null;
        Room cells = new Finder((width + CELL - 1) / CELL, (height + CELL - 1) / CELL).find(new Cells(ink, width, height), x / CELL, y / CELL);
        return cells == null ? null : Refine.timed(cells, width, height, ink);
    }

    /** Prototype px in cells. */
    private static int cells(double prototype) { return (int)Math.round(prototype * SCALE / CELL); }
    private static double sq(double v) { return v * v; }

    /** The page's ink in cells: each cell as dark as its darkest pixel, so no line, however thin, is lost. */
    private static final class Cells implements Ink {
        private final Ink page;
        private final int w, h;
        private int[] read = new int[0];
        Cells(Ink page, int w, int h) { this.page = page; this.w = w; this.h = h; }
        @Override public boolean darkness(int x, int y, int cw, int ch, int[] out) {
            int px = x * CELL, py = y * CELL, pw = Math.min(w - px, cw * CELL), ph = Math.min(h - py, ch * CELL);
            if (pw <= 0 || ph <= 0) return false;
            if (read.length < pw * ph) read = new int[pw * ph];
            if (!page.darkness(px, py, pw, ph, read)) return false;
            java.util.Arrays.fill(out, 0, cw * ch, 0);
            for (int yy = 0; yy < ph; yy++) for (int xx = 0, o = yy * pw; xx < pw; xx++, o++) {
                int c = (yy / CELL) * cw + xx / CELL, v = read[o];
                if (v > out[c]) out[c] = v;
            }
            return true;
        }
    }

    /**
     * The region in cells drawn at page resolution: the pixels of its open, margin and pocket cells; then, four ways
     * from them up to {@link #EDGE_REACH} px, the light pixels of line cells (the line's antialiased edge on this side,
     * never past its line pixels); then the line pixels beside them ({@link #UNDER}) and one layer behind ({@link #BEHIND}).
     */
    private static final class Refine {
        /** A box pixel's bits besides its kind: a line pixel; in a line cell; queued for the line under the fill; a pocket's search's marks. */
        private static final int LINE_PX = 0x10, LINE_CELL = 0x20, EDGE = 0x40, KIND = 0x0f;
        /** The ink read at a time, pixels. */
        private static final int READ_PX = 1 << 18;
        /** About how many bands of rows the passes over the box are cut in, for the cores to share. */
        private static final int BANDS = 4 * Cores.WORKERS;
        private final int bw, bh, x0, y0;
        private final byte[] at;
        private int[] q = new int[1024], edge = new int[256];
        private int edges, area, ax0, ay0, ax1 = -1, ay1 = -1;

        private Refine(int bw, int bh, int x0, int y0) {
            this.bw = bw; this.bh = bh; this.x0 = x0; this.y0 = y0;
            at = new byte[bw * bh];
            ax0 = bw; ay0 = bh;
        }
        /** {@link #room}, its time in {@link Prof#F_REFINE}. */
        static Room timed(Room cells, int w, int h, Ink ink) { long t0 = Prof.now(); Room r = room(cells, w, h, ink); Prof.add(Prof.F_REFINE, t0); return r; }
        static Room room(Room cells, int w, int h, Ink ink) {
            int m = EDGE_REACH + 2;
            int x0 = Math.max(0, cells.x0 * CELL - m), y0 = Math.max(0, cells.y0 * CELL - m);
            int x1 = Math.min(w - 1, (cells.x1 + 1) * CELL - 1 + m), y1 = Math.min(h - 1, (cells.y1 + 1) * CELL - 1 + m);
            Refine r = new Refine(x1 - x0 + 1, y1 - y0 + 1, x0, y0);
            return r.run(cells, ink);
        }
        private int kind(int i) { return at[i] & KIND; }
        private boolean line(int i) { return (at[i] & LINE_PX) != 0; }
        private void put(int i, int kind) {
            if (kind(i) == OUT) {
                area++;
                int x = i % bw, y = i / bw;
                if (x < ax0) ax0 = x; if (x > ax1) ax1 = x; if (y < ay0) ay0 = y; if (y > ay1) ay1 = y;
            }
            at[i] = (byte)((at[i] & ~KIND) | kind);
        }
        private void queue(int i) { if (q.length == tail) q = java.util.Arrays.copyOf(q, tail * 2); q[tail++] = i; }
        private int tail;
        /** Line pixel {@code i} beside the fill, kept once for the line under it and the pockets' search. */
        private void edge(int i) {
            if ((at[i] & EDGE) != 0) return;
            at[i] |= EDGE;
            if (edges == edge.length) edge = java.util.Arrays.copyOf(edge, edges * 2);
            edge[edges++] = i;
        }
        /** The 4-way neighbour {@code k} of box pixel {@code i} (at {@code x, y}), or −1 off the box. */
        private int beside(int i, int x, int y, int k) {
            switch (k) {
                case 0: return x > 0 ? i - 1 : -1;
                case 1: return x < bw - 1 ? i + 1 : -1;
                case 2: return y > 0 ? i - bw : -1;
                default: return y < bh - 1 ? i + bw : -1;
            }
        }

        private Room run(Room cells, Ink ink) {
            // The line pixels, read a band of rows at a time: the box's ink at once took an int a pixel (18 MB over a page).
            int rows = Math.max(1, Math.min(bh, READ_PX / bw));
            int[] dark = new int[bw * rows];
            // The rows with a line pixel, so the line cells' pass skips the rest.
            boolean[] lined = new boolean[bh];
            for (int top = 0; top < bh; top += rows) {
                int h = Math.min(rows, bh - top);
                if (ink.darkness(x0, y0 + top, bw, h, dark)) for (int i = 0, o = top * bw; i < bw * h; i++, o++) if (dark[i] > LINE) { at[o] = LINE_PX; lined[o / bw] = true; }
            }
            // Bands of rows on several cores (Cores): each band's pixels written only by it (the box starts on a cell's
            // edge, so its rows are whole cells), its counts and finds kept apart, then put together in order, so the
            // box is the one a row-by-row pass makes.
            int side = Math.max(16, (bh + BANDS - 1) / BANDS / CELL * CELL), bands = (bh - 1) / side + 1, ox = x0 % CELL, oy = y0 % CELL;
            // Line cells: a cell with a line pixel, marked from each line pixel.
            Cores.inBands(0, bh - 1, side, (long)bw * bh, (top, bottom, worker) -> {
                for (int y = top; y <= bottom; y++) {
                    if (!lined[y]) continue;
                    int ya = Math.max(0, y - (y + oy) % CELL), yb = Math.min(bh, y - (y + oy) % CELL + CELL);
                    for (int x = 0, i = y * bw; x < bw; x++, i++) {
                        if ((at[i] & LINE_PX) == 0) continue;
                        int xa = Math.max(0, x - (x + ox) % CELL), xb = Math.min(bw, x - (x + ox) % CELL + CELL);
                        for (int yy = ya; yy < yb; yy++) for (int xx = xa; xx < xb; xx++) at[yy * bw + xx] |= LINE_CELL;
                    }
                }
            });
            // The pixels of the region's cells (a light cell under the line's edge, wedged between line cells, as open),
            // each still outside: counted and boxed a row at a time.
            int cx0 = Math.max(0, cells.x0 * CELL - x0), cx1 = Math.min(bw - 1, (cells.x1 + 1) * CELL - 1 - x0);
            int[] got = new int[bands * 5];
            Cores.inBands(0, bh - 1, side, (long)bw * bh, (top, bottom, worker) -> {
                int n = 0, fx = bw, lx = -1, fy = -1, ly = -1;
                for (int y = top; y <= bottom; y++) {
                    int cyy = (y0 + y) / CELL;
                    if (cyy < cells.y0 || cyy > cells.y1) continue;
                    int row = (cyy - cells.y0) * cells.width - cells.x0, first = -1, last = -1, m = 0;
                    for (int x = cx0, i = y * bw + cx0; x <= cx1; x++, i++) {
                        int a = at[i];
                        if ((a & LINE_PX) != 0) continue;
                        byte k = cells.at[row + (x0 + x) / CELL];
                        if (k == UNDER || k == BEHIND) { if ((a & LINE_CELL) != 0) continue; k = OPEN; }
                        else if (k != OPEN && k != MARGIN && k != POCKET) continue;
                        at[i] = (byte)(a | k);
                        if (first < 0) first = x;
                        last = x; m++;
                    }
                    if (m == 0) continue;
                    n += m; fx = Math.min(fx, first); lx = Math.max(lx, last); if (fy < 0) fy = y; ly = y;
                }
                int b = top / side * 5;
                got[b] = n; got[b + 1] = fx; got[b + 2] = lx; got[b + 3] = fy; got[b + 4] = ly;
            });
            for (int b = 0; b < bands * 5; b += 5) {
                if (got[b] == 0) continue;
                area += got[b]; ax0 = Math.min(ax0, got[b + 1]); ax1 = Math.max(ax1, got[b + 2]); ay0 = Math.min(ay0, got[b + 3]); ay1 = Math.max(ay1, got[b + 4]);
            }
            // From its edge, four ways up to EDGE_REACH px, the light pixels of the line cells (the line's antialiased edge
            // on this side, never past its line pixels); the line pixels met kept for the line under the fill. Each band
            // finds its edge's pixels (a line pixel j kept as j, a pixel to go from i as ~i), kept in band order after.
            int[][] found = new int[bands][];
            int[] founds = new int[bands];
            Cores.inBands(0, bh - 1, side, (long)bw * bh, (top, bottom, worker) -> {
                int[] f = new int[64];
                int n = 0;
                for (int y = top, i = top * bw; y <= bottom; y++) for (int x = 0; x < bw; x++, i++) {
                    int k = kind(i);
                    if (k == OUT || k == UNDER) continue;
                    // Most of the fill is fill all round.
                    if (x > 0 && x < bw - 1 && y > 0 && y < bh - 1 && kind(i - 1) != OUT && kind(i + 1) != OUT && kind(i - bw) != OUT && kind(i + bw) != OUT) continue;
                    for (int d = 0; d < 4; d++) {
                        int j = beside(i, x, y, d);
                        if (j < 0 || kind(j) != OUT) continue;
                        if (line(j)) { if (k != POCKET) f = Finder.push(f, n++, j); }
                        else if ((at[j] & LINE_CELL) != 0) { f = Finder.push(f, n++, ~i); break; }
                    }
                }
                found[top / side] = f; founds[top / side] = n;
            });
            for (int b = 0; b < bands; b++) for (int k = 0; k < founds[b]; k++) { int v = found[b][k]; if (v >= 0) edge(v); else queue(~v); }
            int head = 0;
            for (int depth = 1; depth <= EDGE_REACH && head < tail; depth++) {
                int end = tail;
                for (; head < end; head++) {
                    int i = q[head], k = kind(i), x = i % bw, y = i / bw;
                    for (int d = 0; d < 4; d++) {
                        int j = beside(i, x, y, d);
                        if (j < 0 || kind(j) != OUT) continue;
                        if (line(j)) { if (k != POCKET) edge(j); continue; }
                        if ((at[j] & LINE_CELL) == 0) continue;
                        put(j, k == POCKET ? POCKET : OPEN);
                        queue(j);
                    }
                }
            }
            pockets();
            // Under the line: its pixels beside the fill (but a pocket's), then a layer behind them.
            int under = edges;
            for (int e = 0; e < under; e++) {
                int i = edge[e];
                boolean by = false;
                int x = i % bw, y = i / bw;
                for (int d = 0; d < 4 && !by; d++) { int j = beside(i, x, y, d); by = j >= 0 && (kind(j) == OPEN || kind(j) == MARGIN); }
                if (by) put(i, UNDER);
            }
            for (int e = 0; e < under; e++) {
                int i = edge[e];
                if (kind(i) != UNDER) continue;
                int x = i % bw, y = i / bw;
                for (int d = 0; d < 4; d++) { int j = beside(i, x, y, d); if (j >= 0 && line(j) && kind(j) == OUT) put(j, BEHIND); }
            }
            // The box tight round it: the box itself when it is (a whole page's), its kinds kept in place.
            int rw = ax1 - ax0 + 1, rh = ay1 - ay0 + 1;
            byte[] tight = rw == bw && rh == bh ? at : new byte[rw * rh];
            for (int y = 0; y < rh; y++) for (int x = 0, i = (y + ay0) * bw + ax0, o = y * rw; x < rw; x++, i++, o++) tight[o] = (byte)kind(i);
            return new Room(x0 + ax0, y0 + ay0, x0 + ax1, y0 + ay1, tight, area);
        }

        /**
         * Pockets too small for the cells: paper shut in by the lines (a sliver between two merged lines) within {@link
         * #ANCHOR_PX} line pixels of the fill, of up to {@link #POCKET_FEW_PX} pixels, or up to {@link #POCKET_MOST_PX}
         * all in line cells (so none far from a line).
         */
        private void pockets() {
            int n = at.length;
            // Seen: a line pixel reached, or a light pixel searched; none: searched, in no pocket (bits, not a byte a pixel).
            java.util.BitSet seen = new java.util.BitSet(n), none = new java.util.BitSet(n);
            int[] seeds = new int[256];
            int nSeeds = 0;
            // The line pixels within ANCHOR_PX of the fill through the line, a layer at a time from those beside it
            // (seen); the light pixels beside them are where a pocket may start.
            tail = 0;
            for (int e = 0; e < edges; e++) { seen.set(edge[e]); queue(edge[e]); }
            int head = 0;
            for (int level = 1; head < tail; level++) {
                int end = tail;
                for (; head < end; head++) {
                    int i = q[head], x = i % bw, y = i / bw;
                    for (int d = 0; d < 4; d++) {
                        int j = beside(i, x, y, d);
                        if (j < 0 || kind(j) != OUT || seen.get(j)) continue;
                        if (!line(j)) { if (nSeeds == seeds.length) seeds = java.util.Arrays.copyOf(seeds, nSeeds * 2); seeds[nSeeds++] = j; continue; }
                        if (level >= ANCHOR_PX) continue;
                        seen.set(j); queue(j);
                    }
                }
            }
            // Each seed's paper, four ways: a pocket, or marked none so the rest of it stops at once.
            for (int s = 0; s < nSeeds; s++) {
                int s0 = seeds[s];
                if (seen.get(s0) || kind(s0) != OUT) continue;
                tail = 0;
                seen.set(s0); queue(s0);
                boolean big = false, lined = true;
                for (head = 0; head < tail && !big; head++) {
                    int i = q[head], x = i % bw, y = i / bw;
                    if (x == 0 || y == 0 || x == bw - 1 || y == bh - 1) { big = true; break; }
                    if ((at[i] & LINE_CELL) == 0) lined = false;
                    for (int d = 0; d < 4; d++) {
                        int j = beside(i, x, y, d);
                        if (line(j) || kind(j) != OUT) continue;
                        if (none.get(j)) { big = true; break; }
                        if (seen.get(j)) continue;
                        seen.set(j); queue(j);
                    }
                    if (tail > POCKET_MOST_PX || !lined && tail > POCKET_FEW_PX) big = true;
                }
                if (big) { for (int k = 0; k < tail; k++) none.set(q[k]); continue; }
                for (int k = 0; k < tail; k++) put(q[k], POCKET);
            }
        }
    }

    /**
     * One find's work over the page's cells (its "pixels" below are cells, {@link #CELL} × {@link #CELL} page px), its
     * arrays kept for the next find on a page of the same size (cleared where used). The prototype's room1 and walls1.
     */
    private static final class Finder {
        private static final int TS = 64, TSHIFT = 6;
        /** A pixel's bits: a line pixel; within {@link #THICKEN} of one; within {@link #NEAR} of one; walls worked out; a thickened wall; reached through the line from the region. */
        private static final byte CORE = 1, BAND = 2, NEAR6 = 4, EVAL = 8, THICK = 16, REACH = 32;
        /** Chebyshev reach that may hold a pixel within a pocket's thinness ({@link #POCKET_THIN2}). */
        private static final int NEAR = (int)Math.ceil(Math.sqrt(POCKET_THIN2));
        /** The pocket marks in {@link #room}'s high bits: seen, and seen in a component that is no pocket. */
        private static final int SEEN = 0x10, BIG = 0x20, KIND = 0x0f;
        private static final int BW = 2 * BOX + 1;
        private static final float[] BOX_D = new float[BW * BW];
        static { for (int dy = -BOX; dy <= BOX; dy++) for (int dx = -BOX; dx <= BOX; dx++) BOX_D[(dy + BOX) * BW + dx + BOX] = (float)Math.hypot(dx, dy); }

        private static java.lang.ref.SoftReference<Finder> kept;
        static synchronized Finder of(int w, int h) {
            Finder f = kept == null ? null : kept.get();
            kept = null;
            return f != null && f.w == w && f.h == h ? f : new Finder(w, h);
        }
        void release() { clear(); synchronized (Finder.class) { kept = new java.lang.ref.SoftReference<>(this); } }

        final int w, h, cols, rows;
        final byte[] bits, room, d2;
        /** A tile's state: its line pixels read (1), its bands worked out (2); and how many line pixels it has. */
        final byte[] tile;
        final int[] lines;
        /** Each line cell's piece (8-way connected line cells), numbered when first needed (0: not yet). */
        final int[] piece;
        int pieces;
        Ink ink;
        final int[] read = new int[TS * TS];
        /** Growable queues of pixels: the region's passes', a piece's numbering's and the geodesic search's. */
        int[] q = new int[4096], pq = new int[4096], gq = new int[4096];
        final int[] loc = new int[BW * BW], wq = new int[BW * BW], repI = new int[BW * BW];
        final float[] repD = new float[BW * BW];
        final int[] want = new int[BW * BW];

        int nearAt;

        Finder(int w, int h) {
            this.w = w; this.h = h;
            cols = (w + TS - 1) >> TSHIFT; rows = (h + TS - 1) >> TSHIFT;
            bits = new byte[w * h]; room = new byte[w * h]; d2 = new byte[w * h];
            tile = new byte[cols * rows]; lines = new int[cols * rows];
            piece = new int[w * h];
        }
        /** Every tile used cleared, for the next find. */
        void clear() {
            for (int t = 0; t < tile.length; t++) {
                if (tile[t] == 0) continue;
                int x0 = (t % cols) << TSHIFT, y0 = (t / cols) << TSHIFT, x1 = Math.min(w, x0 + TS), y1 = Math.min(h, y0 + TS);
                for (int y = y0; y < y1; y++) {
                    int a = y * w + x0, b = y * w + x1;
                    java.util.Arrays.fill(bits, a, b, (byte)0); java.util.Arrays.fill(room, a, b, (byte)0); java.util.Arrays.fill(d2, a, b, (byte)0);
                    java.util.Arrays.fill(piece, a, b, 0);
                }
                tile[t] = 0; lines[t] = 0;
            }
            pieces = 0; ink = null;
            for (Field f : fields) if (f != null) f.used = false;
            nextField = 0;
        }

        // ---- the ink, read lazily ----

        private int tileOf(int x, int y) { return (y >> TSHIFT) * cols + (x >> TSHIFT); }
        /** Tile {@code t}'s line pixels, read once. */
        private void lines(int t) {
            if ((tile[t] & 1) != 0) return;
            tile[t] |= 1;
            long t0 = Prof.now();
            int x0 = (t % cols) << TSHIFT, y0 = (t / cols) << TSHIFT, tw = Math.min(TS, w - x0), th = Math.min(TS, h - y0);
            if (!ink.darkness(x0, y0, tw, th, read)) { Prof.add(Prof.F_INK, t0); return; }
            int n = 0;
            for (int y = 0; y < th; y++) for (int x = 0, o = y * tw, i = (y0 + y) * w + x0; x < tw; x++, o++, i++)
                if (read[o] > LINE) { bits[i] |= CORE; n++; }
            lines[t] = n;
            Prof.add(Prof.F_INK, t0);
        }
        /** The line pixels of every tile meeting {@code x0..x1, y0..y1}, read. */
        private void lines(int x0, int y0, int x1, int y1) {
            for (int ty = Math.max(0, y0) >> TSHIFT; ty <= Math.min(h - 1, y1) >> TSHIFT; ty++)
                for (int tx = Math.max(0, x0) >> TSHIFT; tx <= Math.min(w - 1, x1) >> TSHIFT; tx++) lines(ty * cols + tx);
        }
        /** Tile {@code t}'s {@link #BAND} and {@link #NEAR6}, from the line pixels round it, once. */
        private void bands(int t) {
            if ((tile[t] & 2) != 0) return;

            int tx = t % cols, ty = t / cols, x0 = tx << TSHIFT, y0 = ty << TSHIFT, x1 = Math.min(w, x0 + TS) - 1, y1 = Math.min(h, y0 + TS) - 1;
            lines(x0 - NEAR, y0 - NEAR, x1 + NEAR, y1 + NEAR);
            tile[t] |= 2;
            long t0 = Prof.now();
            try { bandsNow(t, tx, ty, x0, y0, x1, y1); } finally { Prof.add(Prof.F_BANDS, t0); }
        }
        private void bandsNow(int t, int tx, int ty, int x0, int y0, int x1, int y1) {
            int any = 0;
            for (int ny = Math.max(0, ty - 1); ny <= Math.min(rows - 1, ty + 1); ny++)
                for (int nx = Math.max(0, tx - 1); nx <= Math.min(cols - 1, tx + 1); nx++) any += lines[ny * cols + nx];
            if (any == 0) return;
            // Each row's distance to the nearest line pixel along it, out to NEAR (NEAR + 1 beyond), over the tile's
            // columns and NEAR rows above and below; then down each column, the nearest within NEAR rows.
            int ya = Math.max(0, y0 - NEAR), yb = Math.min(h - 1, y1 + NEAR), tw = x1 - x0 + 1;
            byte[] along = new byte[(yb - ya + 1) * tw];
            for (int y = ya; y <= yb; y++) {
                int row = y * w, o = (y - ya) * tw, last = -1000;
                for (int x = Math.max(0, x0 - NEAR); x <= x1; x++) {
                    if ((bits[row + x] & CORE) != 0) last = x;
                    if (x >= x0) along[o + x - x0] = (byte)Math.min(NEAR + 1, x - last);
                }
                int next = 1000;
                for (int x = Math.min(w - 1, x1 + NEAR); x >= x0; x--) {
                    if ((bits[row + x] & CORE) != 0) next = x;
                    if (x <= x1) { int k = o + x - x0; along[k] = (byte)Math.min(along[k], Math.min(NEAR + 1, next - x)); }
                }
            }
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
                int band = 0, near = 0;
                for (int dy = -NEAR; dy <= NEAR && band == 0; dy++) {
                    int yy = y + dy;
                    if (yy < ya || yy > yb) continue;
                    int a = along[(yy - ya) * tw + x - x0], ady = dy < 0 ? -dy : dy, cheb = Math.max(a, ady);
                    if (cheb <= NEAR) near = NEAR6;
                    if (cheb <= THICKEN) band = BAND;
                }
                if ((band | near) != 0) bits[y * w + x] |= (byte)(band | near);
            }
        }
        private boolean core(int i) { return (bits[i] & CORE) != 0; }
        /** Pixel {@code i}'s bits, its tile's bands worked out first. */
        private int at(int i) { int y = i / w; bands(tileOf(i - y * w, y)); return bits[i]; }

        /** Squared distance from pixel {@code i} to the nearest line pixel, out to {@link #BOX} (more beyond: 255); its line pixel in {@link #nearAt} (−1 beyond). */
        private int d2(int i) {
            int x = i % w, y = i / w;
            lines(x - BOX, y - BOX, x + BOX, y + BOX);
            if (core(i)) { nearAt = i; return 0; }
            int best = Integer.MAX_VALUE, bestAt = -1;
            for (int r = 1; r <= BOX && r * r <= best; r++) {
                for (int dy = -r; dy <= r; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= h) continue;
                    int step = dy == -r || dy == r ? 1 : 2 * r;
                    for (int dx = -r; dx <= r; dx += step) {
                        int xx = x + dx;
                        if (xx < 0 || xx >= w) continue;
                        int j = yy * w + xx;
                        if (!core(j)) continue;
                        int d = dx * dx + dy * dy;
                        if (d < best || d == best && j < bestAt) { best = d; bestAt = j; }
                    }
                }
            }
            nearAt = bestAt;
            int v = bestAt < 0 || best > BOX * BOX * 2 ? 255 : best;
            d2[i] = (byte)(v == 255 ? 255 : v + 1);
            return v;
        }
        /** {@link #d2(int)}, kept. */
        private int dist2(int i) { int k = d2[i] & 255; return k == 0 ? d2(i) : k == 255 ? 255 : k - 1; }

        /** The piece (8-way connected line cells) line cell {@code i} is in, numbered once for the whole piece. */
        private int piece(int i) {
            if (piece[i] != 0) return piece[i];
            int id = ++pieces, head = 0, tail = 0;
            piece[i] = id; pq = push(pq, tail++, i);
            while (head < tail) {
                int k = pq[head++], kx = k % w, ky = k / w;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int xx = kx + dx, yy = ky + dy;
                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                    int j = yy * w + xx;
                    lines(tileOf(xx, yy));
                    if (core(j) && piece[j] == 0) { piece[j] = id; pq = push(pq, tail++, j); }
                }
            }
            return id;
        }
        private static int[] push(int[] q, int at, int v) {
            if (at == q.length) q = java.util.Arrays.copyOf(q, q.length * 2);
            q[at] = v;
            return q;
        }

        /**
         * Whether band pixel {@code p} (not a line pixel, within {@link #THICKEN} of one) is a thickened wall
         * (walls1): the ink within {@link #BOX} of it two pieces, or one piece whose parts there are far apart along it.
         */
        private boolean thick(int p) {
            if ((bits[p] & EVAL) != 0) return (bits[p] & THICK) != 0;
            long t0 = Prof.now(), ink0 = Prof.t[Prof.F_INK];
            boolean thick = thickNow(p);
            if (Prof.on) Prof.t[Prof.F_GAP] += System.nanoTime() - t0 - (Prof.t[Prof.F_INK] - ink0);
            bits[p] |= (byte)(EVAL | (thick ? THICK : 0));
            return thick;
        }
        private boolean thickNow(int p) {
            int px = p % w, py = p / w;
            d2(p);
            int c = nearAt;
            if (c < 0) return false;
            int nx = c % w - px, ny = c / w - py;
            double nl = Math.hypot(nx, ny);
            int x0 = Math.max(0, px - BOX), x1 = Math.min(w - 1, px + BOX), y0 = Math.max(0, py - BOX), y1 = Math.min(h - 1, py + BOX);
            lines(x0, y0, x1, y1);
            // Two pieces near: a gap. One piece with ink on the far side of p from its nearest line pixel: look closer.
            int first = 0;
            boolean two = false, far = false;
            for (int y = y0; y <= y1 && !two; y++) for (int x = x0; x <= x1; x++) {
                int j = y * w + x;
                if (!core(j)) continue;
                int k = piece(j);
                if (k != first) { if (first == 0) first = k; else { two = true; break; } }
                int dx = x - px, dy = y - py;
                if (!far && dx * nx + dy * ny < -.2 * nl * BOX_D[(dy + BOX) * BW + dx + BOX]) far = true;
            }
            if (!two && !far) return false;
            // The piece's parts within the box, each with its pixel nearest p.
            int bw = x1 - x0 + 1, bh = y1 - y0 + 1, parts = 0;
            java.util.Arrays.fill(loc, 0, bw * bh, 0);
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
                int c0 = (y - y0) * bw + (x - x0);
                if (loc[c0] != 0 || !core(y * w + x)) continue;
                int id = ++parts, head = 0, tail = 0, best = -1;
                float bd = Float.POSITIVE_INFINITY;
                loc[c0] = id; wq[tail++] = c0;
                while (head < tail) {
                    int cc = wq[head++], cx = cc % bw, cy = cc / bw, gx = x0 + cx, gy = y0 + cy;
                    float d = BOX_D[(gy - py + BOX) * BW + gx - px + BOX];
                    if (d < bd) { bd = d; best = gy * w + gx; }
                    for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                        int ax = gx + dx, ay = gy + dy;
                        if (ax < x0 || ay < y0 || ax > x1 || ay > y1) continue;
                        int c2 = (ay - y0) * bw + (ax - x0);
                        if (loc[c2] == 0 && core(ay * w + ax)) { loc[c2] = id; wq[tail++] = c2; }
                    }
                }
                repI[id - 1] = best; repD[id - 1] = bd;
            }
            if (parts < 2) return false;
            // Two pieces: a gap where a line ends here (two strokes running on side by side are a channel).
            if (two) { for (int k = 0; k < parts; k++) if (ends(k, x0, y0, bw, bh, px, py)) return true; return false; }
            // From the part nearest p, along the ink: does it reach each other part soon enough? Only worth asking where a
            // line ends here (see below).
            int src = 0;
            for (int k = 1; k < parts; k++) if (repD[k] < repD[src]) src = k;
            boolean anyEnd = false;
            for (int k = 0; k < parts && !anyEnd; k++) anyEnd = ends(k, x0, y0, bw, bh, px, py);
            if (!anyEnd) return false;
            int lim = 0, left = parts - 1;
            for (int k = 0; k < parts; k++) { want[k] = k == src ? -1 : Math.max(GEO_MIN, (int)Math.ceil(GEO_K * (repD[src] + repD[k]))); lim = Math.max(lim, want[k]); }
            // How far along the ink each part is from the nearest's pixel: a search kept for the pixels round it (Field).
            Field f = field(repI[src]);
            for (int k = 0; k < parts; k++) if (want[k] >= 0) { int lv = f.level(repI[k] % w, repI[k] / w); if (lv >= 0 && lv <= lim) { want[k] = -1; left--; } }
            if (left == 0) return false;
            // Far apart along the ink, but a gap only where a line ends here: the nearest part, or one not reached, enters
            // the box and stops (or lies inside it). Two sides that both run on through the box are a channel, a
            // wobbling wedge's pinch, never a gap (beyond the prototype, whose thin wedges could shut off their far end).
            if (ends(src, x0, y0, bw, bh, px, py)) return true;
            for (int k = 0; k < parts; k++) if (want[k] >= 0 && ends(k, x0, y0, bw, bh, px, py)) return true;
            return false;
        }
        /**
         * Whether the box's part {@code k} (0 based, in {@link #loc}) ends in the box round {@code px, py}: it meets the
         * box's edge in one run at most (or lies inside it), and no two of the edge cells it meets are on opposite sides
         * of the pixel (at more than a right angle from it). A part that meets the edge twice, or across the pixel, runs
         * on through the box.
         */
        private boolean ends(int k, int x0, int y0, int bw, int bh, int px, int py) {
            int id = k + 1, n = 2 * (bw + bh) - 4, runs = 0, m = 0;
            boolean was = loc[border(n - 1, bw, bh)] == id;
            for (int e = 0; e < n; e++) {
                int c = border(e, bw, bh);
                boolean on = loc[c] == id;
                if (on && !was) runs++;
                was = on;
                if (on) { edgeX[m] = x0 + c % bw - px; edgeY[m] = y0 + c / bw - py; m++; }
            }
            if (runs > 1) return false;
            for (int i = 0; i < m; i++) for (int j = i + 1; j < m; j++) if (edgeX[i] * edgeX[j] + edgeY[i] * edgeY[j] < 0) return false;
            return true;
        }
        private final int[] edgeX = new int[4 * BW], edgeY = new int[4 * BW];
        /** The box's edge cell {@code e}, clockwise from its top left, as an index into {@link #loc}. */
        private static int border(int e, int bw, int bh) {
            if (e < bw) return e;
            e -= bw;
            if (e < bh - 1) return (e + 1) * bw + bw - 1;
            e -= bh - 1;
            if (e < bw - 1) return (bh - 1) * bw + bw - 2 - e;
            e -= bw - 1;
            return (bh - 2 - e) * bw;
        }
        /**
         * A breadth-first search along the ink (8 ways, a level a step) from one line cell, to {@link #LIM} levels: each
         * cell's level in a window round its start. The cells round a gap all ask from much the same cell, so a search
         * is kept and asked again from any start it reached within {@link #NEAR_START} levels (its levels then off by
         * that at most), up to {@link #FIELDS} at once.
         */
        private static final class Field {
            /** The window's top left cell and its side; each cell's level, −1 where not reached. */
            int x0, y0;
            final short[] level = new short[SIDE * SIDE];
            /** The window's cells it reached (to clear for the next search), or null for none yet. */
            int[] reached;
            boolean used;
            Field() { java.util.Arrays.fill(level, (short)-1); }
            int level(int x, int y) {
                int lx = x - x0, ly = y - y0;
                return lx < 0 || ly < 0 || lx >= SIDE || ly >= SIDE ? -1 : level[ly * SIDE + lx];
            }
        }
        /**
         * The deepest a search goes, levels: what the farthest parts of a box ask ({@link #GEO_K} times the box's
         * diagonal and a band's reach), so a search kept answers any later ask.
         */
        private static final int LIM = (int)Math.ceil(GEO_K * (BOX * Math.sqrt(2) + THICKEN * Math.sqrt(2) + 1)), SIDE = 2 * LIM + 1;
        private static final int FIELDS = 12, NEAR_START = 10;
        private final Field[] fields = new Field[FIELDS];
        private int nextField;
        /** The search from line cell {@code start}: one kept that reached it soon, or a new one. */
        private Field field(int start) {
            int sx = start % w, sy = start / w;
            for (Field f : fields) {
                if (f == null || !f.used) continue;
                int lv = f.level(sx, sy);
                if (lv >= 0 && lv <= NEAR_START) return f;
            }
            Field f = fields[nextField];
            if (f == null) f = fields[nextField] = new Field();
            nextField = (nextField + 1) % FIELDS;
            if (f.reached != null) for (int c : f.reached) f.level[c] = -1;
            f.used = true; f.x0 = sx - LIM; f.y0 = sy - LIM;
            short[] lv = f.level;
            // A window's cells at most: no growing in the loop.
            if (gq.length < SIDE * SIDE) gq = new int[SIDE * SIDE];
            int[] gq = this.gq;
            int head = 0, tail = 0, c0 = LIM * SIDE + LIM;
            gq[tail++] = c0; lv[c0] = 0;
            while (head < tail) {
                int c = gq[head++], l = lv[c];
                if (l >= LIM) continue;
                int lx = c % SIDE, ly = c / SIDE, x = f.x0 + lx, y = f.y0 + ly;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= h) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx;
                        if (xx < 0 || xx >= w || (dx | dy) == 0) continue;
                        int c2 = c + dy * SIDE + dx;
                        if (lv[c2] >= 0) continue;
                        int t = (yy >> TSHIFT) * cols + (xx >> TSHIFT);
                        if ((tile[t] & 1) == 0) lines(t);
                        if ((bits[yy * w + xx] & CORE) == 0) continue;
                        lv[c2] = (short)(l + 1);
                        gq[tail++] = c2;
                    }
                }
            }
            f.reached = java.util.Arrays.copyOf(gq, tail);
            return f;
        }
        /** Whether pixel {@code i} is open: no line pixel, no thickened wall. */
        private boolean open(int i) {
            int s = at(i);
            if ((s & CORE) != 0) return false;
            return (s & BAND) == 0 || !thick(i);
        }
        /** Whether pixel {@code i} is a wall that isn't a line pixel: a thickened wall. */
        private boolean thickWall(int i) {
            int s = at(i);
            return (s & CORE) == 0 && (s & BAND) != 0 && thick(i);
        }

        // ---- the region ----

        int bx0, by0, bx1, by1, area;
        private void grow(int x, int y) { if (x < bx0) bx0 = x; if (x > bx1) bx1 = x; if (y < by0) by0 = y; if (y > by1) by1 = y; area++; }
        private void put(int i, byte kind) { room[i] = (byte)((room[i] & ~KIND) | kind); grow(i % w, i / w); }
        private int kind(int i) { return room[i] & KIND; }

        /** The cells the region's open cells may take, inclusive: reaching their edge is reaching the page's. */
        private int lx0, ly0, lx1, ly1;
        /** Whether reaching the page's edge is a wall ({@link #filled}) rather than no region. */
        boolean edgeWalls;
        Room find(Ink ink, int tx, int ty) { return find(ink, tx, ty, 0, 0, w - 1, h - 1); }
        Room find(Ink ink, int tx, int ty, int x0, int y0, int x1, int y1) {
            this.ink = ink;
            lx0 = x0; ly0 = y0; lx1 = x1; ly1 = y1;
            if (tx < 0 || ty < 0 || tx >= w || ty >= h) return null;
            // The nearest open pixel, within START.
            int s = -1;
            for (int r = 0; r <= START && s < 0; r++) for (int dy = -r; dy <= r && s < 0; dy++) for (int dx = -r; dx <= r; dx++) {
                if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                int x = tx + dx, y = ty + dy;
                if (x >= 0 && y >= 0 && x < w && y < h && open(y * w + x)) { s = y * w + x; break; }
            }
            if (s < 0) return null;
            bx0 = w; by0 = h; bx1 = -1; by1 = -1; area = 0;
            int[] seeds = new int[256];
            long t0 = Prof.now();
            int nSeeds = flood(s, seeds);
            Prof.add(Prof.F_FLOOD, t0);
            if (nSeeds < 0) return null;
            seeds = this.seeds;
            t0 = Prof.now(); margin(seeds, nSeeds); Prof.add(Prof.F_MARGIN, t0);
            t0 = Prof.now(); under(); Prof.add(Prof.F_UNDER, t0);
            t0 = Prof.now(); pockets(); Prof.add(Prof.F_POCKETS, t0);
            int rw = bx1 - bx0 + 1, rh = by1 - by0 + 1;
            byte[] at = new byte[rw * rh];
            for (int y = by0; y <= by1; y++) for (int x = bx0, o = (y - by0) * rw, i = y * w + bx0; x <= bx1; x++, o++, i++) at[o] = (byte)kind(i);
            return new Room(bx0, by0, bx1, by1, at, area);
        }
        private int[] seeds;

        /**
         * The open pixels four ways from {@code s} (so a diagonal line still holds), a span at a time; returns how many
         * margin seeds it found in {@link #seeds} (region pixels beside a thickened wall), or −1 when it reaches the
         * page's edge.
         */
        private int flood(int s, int[] seedsIn) {
            int[] stack = new int[1024];
            int n = 0, top = 0;
            seeds = seedsIn;
            stack[top++] = s;
            while (top > 0) {
                int i = stack[--top];
                if (kind(i) != OUT) continue;
                int y = i / w, row = y * w, x = i - row, l = x, r = x;
                while (l > lx0 && kind(row + l - 1) == OUT && open(row + l - 1)) l--;
                while (r < lx1 && kind(row + r + 1) == OUT && open(row + r + 1)) r++;
                if (!edgeWalls && (l == lx0 || r == lx1 || y == ly0 || y == ly1)) return -1;
                for (int k = l; k <= r; k++) { room[row + k] = (byte)((room[row + k] & ~KIND) | OPEN); }
                area += r - l + 1;
                if (l < bx0) bx0 = l; if (r > bx1) bx1 = r; if (y < by0) by0 = y; if (y > by1) by1 = y;
                // At the page's edge (Fill's edges are walls) there is no pixel past it.
                if (l > 0 && thickWall(row + l - 1)) seeds = push(seeds, n++, row + l);
                if (r < w - 1 && thickWall(row + r + 1)) seeds = push(seeds, n++, row + r);
                for (int ny = y - 1; ny <= y + 1; ny += 2) {
                    if (ny < ly0 || ny > ly1) continue;
                    int nrow = ny * w;
                    for (int k = l; k <= r; k++) {
                        int j = nrow + k;
                        if (kind(j) != OUT) continue;
                        if (!open(j)) { if (thickWall(j)) seeds = push(seeds, n++, row + k); continue; }
                        if (top == stack.length) stack = java.util.Arrays.copyOf(stack, top * 2);
                        stack[top++] = j;
                        // The rest of this run is reached from j's span.
                        while (k + 1 <= r && kind(nrow + k + 1) == OUT && open(nrow + k + 1)) k++;
                    }
                }
            }
            return n;
        }

        /**
         * The margin: from the region toward the lines, four ways, each step no farther from a line, through the
         * thickened wall only (past it is another region). Every pixel this side of a line has such a path, at any
         * angle and into corners; a gap can't be crossed, since past it the pixels are farther from the line ends again.
         */
        private void margin(int[] from, int n) {
            int head = 0, tail = 0;
            for (int k = 0; k < n; k++) {
                int i = from[k];
                if (dist2(i) > MARGIN_REACH2) continue;
                q = push(q, tail++, i);
            }
            while (head < tail) {
                int i = q[head++], x = i % w, di = dist2(i);
                for (int k = 0; k < 4; k++) {
                    int j = k == 0 ? (x > 0 ? i - 1 : -1) : k == 1 ? (x < w - 1 ? i + 1 : -1) : k == 2 ? (i >= w ? i - w : -1) : (i < w * h - w ? i + w : -1);
                    if (j < 0 || kind(j) != OUT || !thickWall(j) || dist2(j) > di) continue;
                    put(j, MARGIN);
                    q = push(q, tail++, j);
                }
            }
        }

        /** Whether a 4-way neighbour of {@code i} is of kind {@code a} or {@code b}. */
        private boolean beside(int i, int a, int b) {
            int x = i % w, k;
            return x > 0 && ((k = kind(i - 1)) == a || k == b) || x < w - 1 && ((k = kind(i + 1)) == a || k == b)
                    || i >= w && ((k = kind(i - w)) == a || k == b) || i < w * h - w && ((k = kind(i + w)) == a || k == b);
        }
        /** Whether a 4-way neighbour of {@code i} is a line pixel. */
        private boolean besideLine(int i) {
            int x = i % w;
            return x > 0 && core(i - 1) || x < w - 1 && core(i + 1) || i >= w && core(i - w) || i < w * h - w && core(i + w);
        }

        /**
         * Under the line: its pixels next to the region or margin (and any light pixel wedged between line pixels that
         * the margin missed), then one layer behind them.
         */
        private void under() {
            int x0 = Math.max(0, bx0 - 1), x1 = Math.min(w - 1, bx1 + 1), y0 = Math.max(0, by0 - 1), y1 = Math.min(h - 1, by1 + 1);
            int[] add = new int[256];
            for (int layer = UNDER; layer <= BEHIND; layer++) {
                int n = 0;
                for (int y = y0; y <= y1; y++) for (int x = x0, i = y * w + x0; x <= x1; x++, i++) {
                    if (kind(i) != OUT) continue;
                    boolean line = core(i);
                    if (!line && (layer == BEHIND || !besideLine(i))) continue;
                    if (layer == UNDER ? beside(i, OPEN, MARGIN) : beside(i, UNDER, UNDER)) add = push(add, n++, i);
                }
                for (int k = 0; k < n; k++) put(add[k], (byte)layer);
                x0 = Math.max(0, x0 - 1); x1 = Math.min(w - 1, x1 + 1); y0 = Math.max(0, y0 - 1); y1 = Math.min(h - 1, y1 + 1);
            }
        }

        /**
         * Pockets: paper or light ink shut in by the lines where they touch or cross (a wedge's point, a wobbly line
         * running into the other side, the far side of a wedge's narrow neck): a few pixels ({@link #POCKET_FEW}), or a
         * sliver none of whose pixels is farther than {@link #POCKET_THIN2} from a line ({@link #POCKET_MOST}), within
         * {@link #ANCHOR} line pixels of the region; so no paper shows in the line.
         */
        private void pockets() {
            // The line cells within ANCHOR of the region through the line, a layer at a time from those beside it; the
            // open cells beside them, or beside the region itself, are where a pocket may start. Then again from the
            // pockets found, {@link #CHAIN} times, so a sliver shut in further along two merged lines fills too.
            int x0 = Math.max(0, bx0 - 1), x1 = Math.min(w - 1, bx1 + 1), y0 = Math.max(0, by0 - 1), y1 = Math.min(h - 1, by1 + 1);
            int tail = 0, nStarts = 0;
            int[] starts = new int[256];
            for (int y = y0; y <= y1; y++) for (int x = x0, i = y * w + x0; x <= x1; x++, i++) {
                if (kind(i) != OUT || !beside4(i)) continue;
                if (core(i)) { bits[i] |= REACH; q = push(q, tail++, i); }
                else starts = push(starts, nStarts++, i);
            }
            for (int round = 0; ; round++) {
                nStarts = reach(tail, starts, nStarts);
                starts = this.starts;
                made = 0;
                for (int k = 0; k < nStarts; k++) pocket(starts[k]);
                if (made == 0 || round == CHAIN) break;
                // The next round from the line cells beside the pockets just made.
                tail = 0; nStarts = 0;
                for (int k = 0; k < made; k++) {
                    int i = madeAt[k], x = i % w;
                    for (int d = 0; d < 4; d++) {
                        int j = d == 0 ? (x > 0 ? i - 1 : -1) : d == 1 ? (x < w - 1 ? i + 1 : -1) : d == 2 ? (i >= w ? i - w : -1) : (i < w * h - w ? i + w : -1);
                        if (j < 0 || kind(j) != OUT || !core(j) || (bits[j] & REACH) != 0) continue;
                        bits[j] |= REACH; q = push(q, tail++, j);
                    }
                }
            }
        }
        /** How many rounds of pockets beyond the first, each from the last's. */
        private static final int CHAIN = 2;
        private int[] starts, madeAt = new int[256];
        private int made;
        /**
         * From the {@code tail} line cells queued (first layer), the line cells within ANCHOR through the line; the open
         * cells beside them added to {@code starts} ({@link #starts} after). Each line cell's REACH mark is cleared after.
         */
        private int reach(int tail, int[] starts, int nStarts) {
            int head = 0;
            for (int level = 1; level < ANCHOR && head < tail; level++) {
                int end = tail;
                while (head < end) {
                    int i = q[head++], x = i % w;
                    for (int k = 0; k < 4; k++) {
                        int j = k == 0 ? (x > 0 ? i - 1 : -1) : k == 1 ? (x < w - 1 ? i + 1 : -1) : k == 2 ? (i >= w ? i - w : -1) : (i < w * h - w ? i + w : -1);
                        if (j < 0 || kind(j) != OUT) continue;
                        lines(tileOf(j % w, j / w));
                        if (!core(j)) { if ((room[j] & SEEN) == 0) starts = push(starts, nStarts++, j); continue; }
                        if ((bits[j] & REACH) != 0) continue;
                        bits[j] |= REACH; q = push(q, tail++, j);
                    }
                }
            }
            // The last layer's open neighbours.
            while (head < tail) {
                int i = q[head++], x = i % w;
                for (int k = 0; k < 4; k++) {
                    int j = k == 0 ? (x > 0 ? i - 1 : -1) : k == 1 ? (x < w - 1 ? i + 1 : -1) : k == 2 ? (i >= w ? i - w : -1) : (i < w * h - w ? i + w : -1);
                    if (j < 0) continue;
                    lines(tileOf(j % w, j / w));
                    if (kind(j) == OUT && !core(j)) starts = push(starts, nStarts++, j);
                }
            }
            for (int k = 0; k < tail; k++) bits[q[k]] &= ~REACH;
            this.starts = starts;
            return nStarts;
        }
        /** Whether a 4-way neighbour of {@code i} is in the region (open, margin or under the line). */
        private boolean beside4(int i) {
            int x = i % w, k;
            return x > 0 && (k = kind(i - 1)) != OUT && k != POCKET || x < w - 1 && (k = kind(i + 1)) != OUT && k != POCKET
                    || i >= w && (k = kind(i - w)) != OUT && k != POCKET || i < w * h - w && (k = kind(i + w)) != OUT && k != POCKET;
        }
        /** The open pixels four ways from {@code s0} (not the region, no line): a pocket, or marked as none. */
        private void pocket(int s0) {
            lines(tileOf(s0 % w, s0 / w));
            if (kind(s0) != OUT || core(s0) || (room[s0] & (SEEN | BIG)) != 0) return;
            int head = 0, tail = 0;
            room[s0] |= SEEN; q = push(q, tail++, s0);
            boolean none = false, thin = true;
            while (head < tail && !none) {
                int i = q[head++], x = i % w, y = i / w;
                if (thin) {
                    int s = at(i);
                    if ((s & NEAR6) == 0 || dist2(i) > POCKET_THIN2) thin = false;
                }
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) { none = true; break; }
                for (int k = 0; k < 4; k++) {
                    int j = k == 0 ? i - 1 : k == 1 ? i + 1 : k == 2 ? i - w : i + w;
                    lines(tileOf(j % w, j / w));
                    if (kind(j) != OUT || core(j)) continue;
                    int m = room[j] & (SEEN | BIG);
                    if ((m & BIG) != 0) { none = true; break; }
                    if (m != 0) continue;
                    room[j] |= SEEN; q = push(q, tail++, j);
                }
                if (tail > POCKET_MOST || !thin && tail > POCKET_FEW) none = true;
            }
            if (none || !(tail <= POCKET_FEW || thin)) { for (int k = 0; k < tail; k++) room[q[k]] |= BIG; return; }
            for (int k = 0; k < tail; k++) { put(q[k], POCKET); madeAt = push(madeAt, made++, q[k]); }
        }
    }

    private FillRegion() {}
}
