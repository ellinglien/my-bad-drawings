package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.List;

/**
 * What the canvas shows of a page's mirrors ({@link Mirrors}), over its own render of the page and never in it: the
 * guides, dashed in grey (unless Show Guides is off: {@link Mirrors#guides}); Real Mirror's reflection (each pixel of a
 * covered cell showing the kept pixel it reflects, read from the page as it is now, so drawing on the kept side shows in
 * the reflection at once); Copy Strokes' live copies while the pen is down; and a placing stroke in light grey, or Cut Stamp's lasso in
 * marching ants ({@link Place#lasso}); a Spiro's pinned ring, oval or bar dashed in light grey while it is set up and drawn, following the setup's drag ({@link #ring}). PNG and PDF copies never show the guides; they show
 * the reflection, as the page shows it, through their own composites ({@link ToneDocument#exportPixels}).
 *
 * <p>Live copies: while a stroke is down only it is drawn ({@link MirrorStroke}); each time the page under it changes,
 * every map's image of the change is kept here as tones (what the stroke shows there, {@link Marks}, darkening what
 * lies under it, or for the eraser what it leaves), in tiles a page's size; the copies drawn at lift take its place.
 * A copy looks only where the change's {@link #BLOCK}-pixel blocks that hold a mark land, and asks to render again only
 * the box it lit, so a pen's thin line costs its own pixels, not its box's. Each render reads only the rows it draws.
 * Pure: page pixels in, ARGB pixels out ({@link DotPattern}'s dots, or plain greys for the zoomed raster), checked on
 * the desktop ({@code MirrorChecks}).
 */
final class MirrorShow {
    /** What a stroke shows on the page: the tone of each pixel of a block it has changed, −1 where it hasn't ({@link ToneDocument#gestureTones}, the eraser's {@code erasedTones}). */
    interface Marks { void tones(int x, int y, int w, int h, int[] out); }
    private static final int TILE = 64, NONE = -1;
    /**
     * The most page pixels a frame's live copies may look at (where the change's marked blocks land): past it the later
     * maps (the group's farther ones, found last) wait for the lift. A pen's frame (its line across a box up to about
     * 100 px) shows every copy, the cap's 23 too, as does a kaleidoscope's six of a big wash; of the cap's 23, a 200 px
     * box full of marks about seven.
     */
    static final long LIVE_PX = 300_000;
    private int width, height, columns;
    /** The mirrors as shown: their guides when on and shown, and Real Mirror's view ({@link Mirrors#view}), or null. */
    private Mirrors shown = Mirrors.NONE;
    private Mirrors.View view;
    /** Live copies: the maps (the page itself left out) and their inverses; null while no stroke shows copies. */
    private double[][] maps, inverses;
    /** Whether the overlay darkens what lies under it (a brush, a placing stroke) or replaces it (the eraser). */
    private boolean darkens;
    /** The overlay's tones by tile (null: nothing there), and the box it has covered, {x0, y0, x1, y1} exclusive; null for none. */
    private short[][] overlay;
    private int[] covered;
    /** Areas to render again ({x0, y0, x1, y1}, exclusive), taken by the next {@link #take}. */
    private final List<int[]> pending = new ArrayList<>();
    private int[] marks = new int[0];
    /** The live copies' blocks, pixels: which of a change's blocks hold a mark ({@link #take}), so a copy looks only where they land. */
    private static final int BLOCK = 16;
    private boolean[] held = new boolean[0];
    private float lastX = Float.NaN, lastY;
    /** How far a lasso has gone, page px: where along it its dashes are. */
    private double placed;
    /** A lasso's marching ants: dashes and gaps this long, page px (the prototype's 4 px), in ink, half a placing line wide. */
    static final double DASH = 4 * Mirrors.PX;
    static final int ANTS_TONE = 0;
    /**
     * A Spiro's track ({@link #ring}): its pin (the ring's centre, an Oval's or Bar's first corner), the brush's own radius
     * and its mode ({@link SpiroStamper#box}); its centre and halves now ({@code ringR} the ring's radius or the longer
     * half, 0: none), and as last sent to be drawn.
     */
    private float ringX, ringY, ringOwn, ringR;
    private int ringMode;
    private double[] ringNow = {0, 0, 0, 0}, ringShown = ringNow;

    /**
     * The pen's stroke while Place Mirror waits: shown in light grey, its points for {@link Mirrors#fit}; or, a {@link
     * #lasso}, Cut Stamp's (mischief-lab-2.html's cut2): shown in marching ants, its points the lasso the nib is cut
     * with ({@link StampNib#cut}). It draws nothing on the page.
     */
    static final class Place implements DrawingStroke {
        private final MirrorShow show;
        private float[] xs = new float[256], ys = new float[256];
        private int count;
        /** Cut Stamp's lasso, in marching ants. */
        final boolean lasso;
        Place(MirrorShow show) { this(show, false); }
        Place(MirrorShow show, boolean lasso) { this.show = show; this.lasso = lasso; }
        @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY) {
            if (!Float.isFinite(x) || !Float.isFinite(y)) return;
            if (count == xs.length) { xs = java.util.Arrays.copyOf(xs, 2 * count); ys = java.util.Arrays.copyOf(ys, 2 * count); }
            xs[count] = x; ys[count] = y; count++;
            show.place(x, y, lasso);
        }
        @Override public boolean finish() { return false; }
        int count() { return count; }
        float[] xs() { return xs; }
        float[] ys() { return ys; }
    }

    /** A {@code width × height} page: everything shown before is forgotten. */
    void page(int width, int height) {
        if (width == this.width && height == this.height) return;
        this.width = width; this.height = height; columns = (width + TILE - 1) / TILE;
        overlay = null; covered = null; maps = inverses = null; pending.clear();
    }
    /** The page's mirrors now: their guides, and the reflection if they show one. */
    void show(Mirrors mirrors) {
        if (mirrors == shown) return;
        shown = mirrors;
        view = mirrors.view();
    }
    /** Real Mirror's view as shown, or null. */
    Mirrors.View view() { return view; }

    /** A stroke's live copies, one for each map of {@code group} but the first; {@code darkens}: a brush's, else the eraser's. */
    void beginCopies(Mirrors.Group group, boolean darkens) {
        // A Spiro's ring set up stays for the stroke that draws it.
        float r = ringR;
        double[] now = ringNow;
        end();
        ringR = r; ringNow = now;
        maps = java.util.Arrays.copyOfRange(group.maps, 1, group.maps.length);
        inverses = new double[maps.length][];
        for (int k = 0; k < maps.length; k++) inverses[k] = Mirrors.Group.inverse(maps[k]);
        this.darkens = darkens;
    }
    /** A placing stroke's line from where it last was to {@code x, y}, in light grey; a {@code lasso}'s in marching ants ({@link #DASH}). */
    void place(float x, float y, boolean lasso) {
        darkens = true;
        float fx = Float.isNaN(lastX) ? x : lastX, fy = Float.isNaN(lastX) ? y : lastY;
        lastX = x; lastY = y;
        double r = lasso ? Mirrors.PX / 2 : Mirrors.PX, length = Math.hypot(x - fx, y - fy);
        int steps = Math.max(1, (int) Math.ceil(length));
        for (int s = 0; s <= steps; s++) {
            double cx = fx + (x - fx) * s / (double) steps, cy = fy + (y - fy) * s / (double) steps;
            if (lasso && Math.floorMod((long) Math.floor((placed + length * s / steps) / DASH), 2) == 1) continue;
            for (int py = (int) Math.floor(cy - r); py <= (int) Math.ceil(cy + r); py++)
                for (int px = (int) Math.floor(cx - r); px <= (int) Math.ceil(cx + r); px++)
                    if (Math.hypot(px + .5 - cx, py + .5 - cy) <= r) set(px, py, lasso ? ANTS_TONE : Mirrors.PLACING_TONE);
        }
        placed += length;
        int pad = (int) Math.ceil(r) + 1;
        pending.add(new int[]{(int) Math.min(fx, x) - pad, (int) Math.min(fy, y) - pad, (int) Math.max(fx, x) + pad + 1, (int) Math.max(fy, y) + pad + 1});
    }
    /**
     * A Spiro's pinned ring (Ring; {@link DrawingStroke#ringGuide}), its centre {@code cx, cy}, radius {@code radius},
     * page px: shown from the touch-down in a lasso's dashes, a placing line's light grey half as wide, darkening what
     * is there, until {@link #end}; over the page, never in it. The setup's drag ({@link #ringTo}) sizes it as the
     * stamper's rim does ({@link SpiroStamper#rim}). An Oval's or Bar's track ({@code mode}) is pinned by its corner,
     * {@code radius} its longer half until the drag sets the other ({@link SpiroStamper#box}).
     */
    void ring(float cx, float cy, float radius, int mode) {
        ringX = cx; ringY = cy; ringR = ringOwn = radius; ringMode = mode;
        ringShown = ringNow = SpiroStamper.box(mode, cx, cy, cx, cy, radius);
        arcs(ringNow);
    }
    /** The pen at {@code x, y}: whether the track, its rim or corner there (within the pin the brush's own), changed half a pixel or more (its arcs, old and new, drawn again at the next {@link #take}). */
    boolean ringTo(float x, float y) {
        if (ringR <= 0 || !Float.isFinite(x) || !Float.isFinite(y)) return false;
        double[] to = SpiroStamper.box(ringMode, ringX, ringY, x, y, ringOwn);
        boolean moved = false;
        for (int i = 0; i < 4; i++) moved |= Math.abs(to[i] - ringNow[i]) >= .5;
        if (!moved) return false;
        ringNow = to; ringR = (float) Math.max(to[2], to[3]);
        return true;
    }
    /** The ring's arcs as last shown and as now, drawn again once a render ({@link #take}), however many samples moved it between. */
    private void ringArcs() {
        if (ringShown != ringNow) arcs(ringShown);
        arcs(ringNow);
        ringShown = ringNow;
    }
    /** The track {@code b} ({cx, cy, ha, hb}) to draw again: boxes round its arcs, not its whole box, so a pull costs its line. */
    private void arcs(double[] b) {
        boolean bar = ringMode == SpiroStamper.BAR;
        double ha = b[2], hb = b[3], c = SpiroStamper.corner(ha, hb), length = bar ? SpiroStamper.barLength(ha, hb) : 2 * Math.PI * Math.max(ha, hb);
        int n = Math.max(8, (int) Math.ceil(length / 64));
        // How far an arc bows out from its chord: the ellipse's at most its longer half's, a Bar's its corners'.
        double pad = (bar ? c * (1 - Math.cos(Math.min(Math.PI / 2, length / n / c) / 2)) : Math.max(ha, hb) * (1 - Math.cos(Math.PI / n))) + Mirrors.PX / 2 + 2;
        double[] p = new double[3];
        for (int i = 0; i < n; i++) {
            double x0, y0, x1, y1;
            if (bar) {
                SpiroStamper.outline(true, ha, hb, i / (double) n, p); x0 = b[0] + p[0]; y0 = b[1] + p[1];
                SpiroStamper.outline(true, ha, hb, (i + 1) / (double) n, p); x1 = b[0] + p[0]; y1 = b[1] + p[1];
            } else {
                double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
                x0 = b[0] + ha * Math.cos(a0); y0 = b[1] + hb * Math.sin(a0); x1 = b[0] + ha * Math.cos(a1); y1 = b[1] + hb * Math.sin(a1);
            }
            pending.add(new int[]{(int) Math.floor(Math.min(x0, x1) - pad), (int) Math.floor(Math.min(y0, y1) - pad), (int) Math.ceil(Math.max(x0, x1) + pad) + 1, (int) Math.ceil(Math.max(y0, y1) + pad) + 1});
        }
    }
    /** Whether the track's dashes cover the pixel at {@code x, y}. */
    private boolean ringAt(int x, int y) {
        double[] b = ringNow;
        double dx = x + .5 - b[0], dy = y + .5 - b[1];
        boolean on;
        if (ringMode < SpiroStamper.OVAL) { double d2 = dx * dx + dy * dy, lo = Math.max(0, ringR - Mirrors.PX / 2), hi = ringR + Mirrors.PX / 2; on = d2 <= hi * hi && d2 >= lo * lo; }
        else on = Math.abs(SpiroStamper.edge(ringMode, b[2], b[3], dx, dy)) <= Mirrors.PX / 2;
        return on && Math.floorMod((long) Math.floor((b[2] + b[3]) / 2 * Math.atan2(dy, dx) / DASH), 2) == 0;
    }
    /** Whether the track reaches row {@code y}. */
    private boolean ringRow(int y) { return ringR > 0 && Math.abs(y + .5 - ringNow[1]) <= ringNow[3] + Mirrors.PX; }
    /** The stroke's live copies or the placing line go: what they covered is rendered again. Whether a Spiro's ring went too. */
    boolean end() {
        if (covered != null) pending.add(covered);
        boolean ring = ringR > 0;
        if (ring) { ringArcs(); ringR = 0; }
        overlay = null; covered = null; maps = inverses = null; lastX = Float.NaN; placed = 0;
        return ring;
    }
    /** Inkwash: the drawing page's ghost plane ({@link GhostPlane}), what it shows lies on; null: none. */
    volatile GhostPlane.Planes ghost;
    /** Whether live copies, a placing line or a Spiro's ring are shown. */
    boolean showing() { return overlay != null || maps != null || ringR > 0; }

    /**
     * The page changed in {@code bounds} ({x0, y0, x1, y1}, exclusive; null for nothing): the live copies of what
     * {@code marks} shows there are kept. Returns every area to render again besides {@code bounds}: the copies'
     * images of it, the reflection's (Real Mirror's covered cells show it), and anything waiting.
     */
    List<int[]> take(int[] bounds, Marks marks) {
        if (ringR > 0 && ringShown != ringNow) ringArcs();
        List<int[]> out = new ArrayList<>(pending);
        pending.clear();
        if (bounds == null || width == 0) return out;
        int x0 = Math.max(0, bounds[0]), y0 = Math.max(0, bounds[1]), x1 = Math.min(width, bounds[2]), y1 = Math.min(height, bounds[3]);
        if (x0 >= x1 || y0 >= y1) return out;
        if (maps != null && marks != null) {
            int w = x1 - x0, h = y1 - y0;
            if (this.marks.length < w * h) this.marks = new int[w * h];
            int[] src = this.marks;
            marks.tones(x0, y0, w, h, src);
            // Which BLOCK-pixel blocks of the change hold a mark, and their box: a pen's thin line leaves most of its box
            // empty, and a copy looks only where its blocks land.
            int bw = (w + BLOCK - 1) / BLOCK, bh = (h + BLOCK - 1) / BLOCK;
            if (held.length < bw * bh) held = new boolean[bw * bh];
            java.util.Arrays.fill(held, 0, bw * bh, false);
            int m0 = w, n0 = h, m1 = -1, n1 = -1;
            for (int y = 0; y < h; y++)
                for (int x = 0, i = y * w; x < w; x++, i++)
                    if (src[i] >= 0) {
                        held[(y / BLOCK) * bw + x / BLOCK] = true;
                        if (x < m0) m0 = x; if (x > m1) m1 = x; if (y < n0) n0 = y; if (y > n1) n1 = y;
                    }
            if (m1 >= 0) {
                long budget = LIVE_PX;
                for (int k = 0; k < maps.length; k++) {
                    int[] dest = image(maps[k], x0 + m0, y0 + n0, x0 + m1 + 1, y0 + n1 + 1);
                    if (dest == null) continue;
                    double[] t = inverses[k];
                    // Its blocks, BLOCK pixels on the page, whose source (a pixel more each way) holds a mark.
                    int[] lit = null;
                    long looked = 0;
                    for (int by = dest[1] / BLOCK; by <= (dest[3] - 1) / BLOCK; by++)
                        for (int bx = dest[0] / BLOCK; bx <= (dest[2] - 1) / BLOCK; bx++) {
                            int qx0 = Math.max(dest[0], bx * BLOCK), qy0 = Math.max(dest[1], by * BLOCK), qx1 = Math.min(dest[2], (bx + 1) * BLOCK), qy1 = Math.min(dest[3], (by + 1) * BLOCK);
                            if (!holds(t, qx0, qy0, qx1, qy1, x0, y0, bw, bh)) continue;
                            looked += (long) (qx1 - qx0) * (qy1 - qy0);
                            for (int qy = qy0; qy < qy1; qy++) {
                                double cy = qy + .5;
                                for (int qx = qx0; qx < qx1; qx++) {
                                    double cx = qx + .5;
                                    int px = (int) Math.floor(t[0] * cx + t[1] * cy + t[4]), py = (int) Math.floor(t[2] * cx + t[3] * cy + t[5]);
                                    if (px < x0 || py < y0 || px >= x1 || py >= y1) continue;
                                    int tone = src[(py - y0) * w + px - x0];
                                    if (tone < 0) continue;
                                    set(qx, qy, tone);
                                    if (lit == null) lit = new int[]{qx, qy, qx + 1, qy + 1};
                                    else { lit[0] = Math.min(lit[0], qx); lit[1] = Math.min(lit[1], qy); lit[2] = Math.max(lit[2], qx + 1); lit[3] = Math.max(lit[3], qy + 1); }
                                }
                            }
                        }
                    if (lit != null) out.add(lit);
                    budget -= looked;
                    if (budget < 0) break;
                }
            }
        }
        if (view != null) for (int k = 1; k < view.maps.length; k++) { int[] dest = image(view.maps[k], x0, y0, x1, y1); if (dest != null) out.add(dest); }
        return out;
    }
    /**
     * Whether the page's box {@code qx0, qy0} to {@code qx1, qy1} takes any mark under {@code inverse}: its source, a
     * pixel more each way, meets a block of {@link #held} (the change's, from {@code x0, y0}, {@code bw × bh} blocks).
     */
    private boolean holds(double[] inverse, int qx0, int qy0, int qx1, int qy1, int x0, int y0, int bw, int bh) {
        double[] s = Mirrors.Group.box(inverse, qx0, qy0, qx1, qy1);
        if (s[2] + 1 < x0 || s[3] + 1 < y0) return false;
        int a0 = Math.max(0, ((int) Math.floor(s[0]) - 1 - x0) / BLOCK), a1 = Math.min(bw - 1, ((int) Math.ceil(s[2]) + 1 - x0) / BLOCK);
        int c0 = Math.max(0, ((int) Math.floor(s[1]) - 1 - y0) / BLOCK), c1 = Math.min(bh - 1, ((int) Math.ceil(s[3]) + 1 - y0) / BLOCK);
        for (int by = c0; by <= c1; by++) for (int bx = a0; bx <= a1; bx++) if (held[by * bw + bx]) return true;
        return false;
    }
    /** The box {@code t} carries {@code x0, y0, x1, y1} to, a pixel more each way, on the page; null off it. */
    private int[] image(double[] t, int x0, int y0, int x1, int y1) {
        double[] b = Mirrors.Group.box(t, x0, y0, x1, y1);
        int[] box = {Math.max(0, (int) Math.floor(b[0]) - 1), Math.max(0, (int) Math.floor(b[1]) - 1), Math.min(width, (int) Math.ceil(b[2]) + 1), Math.min(height, (int) Math.ceil(b[3]) + 1)};
        return box[0] < box[2] && box[1] < box[3] ? box : null;
    }
    /** The overlay at {@code x, y} takes {@code tone} (darkening: the darker of it and what is there). */
    private void set(int x, int y, int tone) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        if (overlay == null) overlay = new short[columns * ((height + TILE - 1) / TILE)][];
        int key = (y / TILE) * columns + x / TILE;
        short[] tile = overlay[key];
        if (tile == null) { tile = new short[TILE * TILE]; java.util.Arrays.fill(tile, (short) NONE); overlay[key] = tile; }
        int i = (y % TILE) * TILE + x % TILE;
        tile[i] = (short) (tile[i] >= 0 && darkens ? Math.min(tile[i], tone) : tone);
        if (covered == null) covered = new int[]{x, y, x + 1, y + 1};
        else { covered[0] = Math.min(covered[0], x); covered[1] = Math.min(covered[1], y); covered[2] = Math.max(covered[2], x + 1); covered[3] = Math.max(covered[3], y + 1); }
    }
    private int overlayAt(int x, int y) {
        short[] tile = overlay[(y / TILE) * columns + x / TILE];
        return tile == null ? NONE : tile[(y % TILE) * TILE + x % TILE];
    }

    /** Whether anything here draws in the box {@code x0, y0} to {@code x1, y1} (exclusive). */
    boolean touches(int x0, int y0, int x1, int y1) {
        if (view != null) return true;
        if (overlay != null && covered != null && covered[0] < x1 && covered[2] > x0 && covered[1] < y1 && covered[3] > y0) return true;
        if (ringNear(x0, y0, x1, y1)) return true;
        return shown.guidesShown() && shown.guidesNear(x0, y0, x1, y1);
    }
    /**
     * Draws over {@code pixels} (ARGB, the page's {@code w × h} block at {@code x0, y0}, as the canvas rendered it):
     * the reflection, then the live copies or the placing line, then the guides; {@code page} reads the page's tones
     * (the reflection's sources, what the copies darken), {@code paper} what shows beyond the page's edge, {@code dots}
     * the panel's dots (else plain greys). Returns whether it changed any pixel.
     */
    boolean draw(int[] pixels, int x0, int y0, int w, int h, boolean dots, Mirrors.Tones page, int paper) {
        boolean guides = shown.guidesShown() && shown.guidesNear(x0, y0, x0 + w, y0 + h), live = overlay != null && covered != null
                && covered[0] < x0 + w && covered[2] > x0 && covered[1] < y0 + h && covered[3] > y0;
        if (view == null && !guides && !live && !ringNear(x0, y0, x0 + w, y0 + h)) return false;
        boolean[] changed = new boolean[Cores.WORKERS + 1];
        // A big block (a whole page, a reflection's cells) on several cores, each band its own rows: the page is only read.
        Cores.inBands(y0, y0 + h - 1, TILE, (long) w * h, (top, bottom, worker) -> {
            if (drawRows(pixels, x0, y0, w, top, bottom, dots, page, paper, guides, live)) changed[worker] = true;
        });
        for (boolean c : changed) if (c) return true;
        return false;
    }
    /** Whether the ring's box meets {@code x0, y0} to {@code x1, y1} (exclusive). */
    private boolean ringNear(int x0, int y0, int x1, int y1) {
        double[] b = ringNow;
        double rx = b[2] + Mirrors.PX, ry = b[3] + Mirrors.PX;
        return ringR > 0 && b[0] - rx < x1 && b[0] + rx > x0 && b[1] - ry < y1 && b[1] + ry > y0;
    }
    /** {@link #draw}'s rows {@code top} to {@code bottom} (inclusive). */
    private boolean drawRows(int[] pixels, int x0, int y0, int w, int top, int bottom, boolean dots, Mirrors.Tones page, int paper, boolean guides, boolean live) {
        int[] source = view == null ? null : new int[w], guideFrom = new int[Mirrors.MOST], guideTo = new int[Mirrors.MOST];
        boolean changed = false;
        for (int y = top; y <= bottom; y++) {
            int row = y - y0;
            if (source != null) view.row(y, x0, x0 + w, width, height, source);
            // The guides' spans on this row, each mirror's (a pixel's centre within HALF_WIDTH of it): only those pixels look for a dash.
            int spans = 0;
            if (guides) for (Mirrors.Line m : shown.lines) { int[] span = Mirrors.guideSpan(m, y, x0, x0 + w); if (span != null) { guideFrom[spans] = span[0]; guideTo[spans++] = span[1]; } }
            boolean ring = ringRow(y);
            if (source == null && !live && spans == 0 && !ring) continue;
            for (int col = 0; col < w; col++) {
                int x = x0 + col, tone = -1;
                if (source != null) { int s = source[col]; if (s != y * width + x) tone = s < 0 ? paper : page.tone(s % width, s / width); }
                if (live) {
                    int o = overlayAt(x, y);
                    if (o >= 0) tone = darkens ? Math.min(tone >= 0 ? tone : page.tone(x, y), o) : o;
                }
                boolean near = false;
                for (int i = 0; i < spans && !near; i++) near = x >= guideFrom[i] && x < guideTo[i];
                if (near && shown.guideAt(x + .5, y + .5)) tone = Math.min(tone >= 0 ? tone : page.tone(x, y), Mirrors.GUIDE_TONE);
                if (ring && ringAt(x, y)) tone = Math.min(tone >= 0 ? tone : page.tone(x, y), Mirrors.PLACING_TONE);
                if (tone < 0) continue;
                // Inkwash: on tracing paper's ghost, as the page under it shows (GhostPlane).
                if (ghost != null) tone = GhostPlane.over(tone, ghost, y * width + x);
                pixels[row * w + col] = dots ? DotPattern.pixel(tone, x, y) : 0xff000000 | tone * 0x010101;
                changed = true;
            }
        }
        return changed;
    }
}
