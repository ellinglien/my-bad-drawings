package io.github.mpdairy.monopaint;

/**
 * Filled Shape ({@link PartChoice#SHAPE}; the owner, 2026-10-23: "draw a filled shape", as a brush, not a tool): the
 * stroke is drawn as its brush draws it, the outline, and at the lift it closes, a last sample at its start, and the
 * area it encloses fills with the same brush at the same size and grey, laid as the retired bucket laid an area
 * ({@link FillTool#start}: the medium, paper, Print Shading and drying as a firm, still press everywhere in it, in strips,
 * on the layer the brush's strokes go to, as dark as the outline and never darker than the darker of the two where
 * they meet, {@link ModularStroke#fillShape}, so outline and inside are one tone at any grey). Inside is non-zero winding: a loop gone round twice, a scribbled knot or a star
 * fills whole, and both lobes of a figure eight fill. A dot or a short line ({@link #tiny}) stays a stroke, unclosed.
 * Mirrors: Copy Strokes fills every image of the shape, Real Mirror the kept side of what the page shows (each cell's
 * share of the shape folded into the kept cell), so the shape seen fills either way. Outline and fill are one Undo step.
 * Pure, checked on the desktop ({@code FilledShapeChecks}).
 */
final class FilledShape implements LiftingStroke {
    /** The smallest area that fills, page px: about a millimetre square on the Manta. A shape smaller than its brush is square stays a stroke too. */
    static final int MIN_AREA = 144;
    /**
     * A fill this many page px or more shows at once on the drawing screen, flat in its grey, while it is laid ({@link
     * #preview}): about a twentieth of the Manta's page. The retired bucket's: a Bleed marker fill of 3 million px took 6 s.
     */
    static final int PREVIEW_PX = 250_000;
    /**
     * The heap a fill takes at its peak, bytes a page px of its area, over the page itself (the bucket's, measured
     * 2026-10-22 before strips; strips take less). A fill the heap can't take even with older Undo steps let go
     * ({@link #TIGHT_HISTORY}) is left out: the outline stays.
     */
    static final int PEAK_BYTES_PX = 18, TIGHT_HISTORY = 8 * 1024 * 1024;

    private final ToneDocument document;
    /** The outline's stroke as drawn: the brush's own, or its mirrors' ({@link MirrorStroke}). */
    final DrawingStroke outline;
    private final ModularBrush brush;
    private final double size, darkness;
    private final WatercolorSession session;
    /** The last Undo step before the outline: its steps and the fill's link into one. */
    private final Object mark;
    /** The page's mirrors as the outline began: Copy Strokes' maps (the page itself first), or Real Mirror's view; null for none. */
    private final double[][] maps;
    private final Mirrors.View view;
    /** The pen's samples as drawn (page px, before any mirror moves them). */
    private float[] xs = new float[256], ys = new float[256];
    private int n;
    private float lastPressure, lastTiltX = Float.NaN, lastTiltY = Float.NaN;
    private long firstTime = PenSpeed.UNTIMED, lastTime = PenSpeed.UNTIMED;
    private double travel;
    /** The area to fill, found at the lift; null for none (a tiny outline, or no room in the heap). */
    private FillRegion.Room room;
    /** The drawing screen's flat preview of a big fill ({@link #PREVIEW_PX}), shown before it is laid: its area and grey. */
    java.util.function.BiConsumer<FillRegion.Room, Double> preview;
    private boolean closed, outlineLifting, outlineDone, previewed, done, changed;

    private FilledShape(ToneDocument document, DrawingStroke outline, ModularBrush brush, double size, double darkness, WatercolorSession session) {
        this.document = document; this.outline = outline; this.brush = brush; this.size = size; this.darkness = darkness; this.session = session;
        mark = document.lastStep();
        Mirrors mirrors = document.mirrors();
        view = mirrors.reflects() ? mirrors.view() : null;
        maps = view != null ? view.inverse : mirrors.copies() ? mirrors.group().maps : new double[][]{Mirrors.Group.IDENTITY};
    }
    /** {@code outline}, {@code brush}'s stroke at {@code size} and {@code darkness}: a filled shape's when the brush is one ({@link #fills}), else itself. */
    static DrawingStroke of(ToneDocument document, ModularBrush brush, DrawingStroke outline, double size, double darkness, WatercolorSession session) {
        return fills(brush) ? new FilledShape(document, outline, brush, size, darkness, session) : outline;
    }
    /** Whether {@code b} fills the shapes it draws: Filled Shape, but on water (a float is a pen moving on water). */
    static boolean fills(ModularBrush b) { return b != null && b.choice(BrushPart.CARRY) == PartChoice.SHAPE && b.choice(BrushPart.LIFE) != PartChoice.FLOAT; }

    @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY) { sample(x, y, pressure, tiltX, tiltY, PenSpeed.UNTIMED); }
    @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY, long time) {
        if (Float.isFinite(x) && Float.isFinite(y) && !closed) {
            if (n == xs.length) { xs = java.util.Arrays.copyOf(xs, 2 * n); ys = java.util.Arrays.copyOf(ys, 2 * n); }
            if (n > 0) travel += Math.hypot(x - xs[n - 1], y - ys[n - 1]); else firstTime = time;
            xs[n] = x; ys[n] = y; n++;
            lastPressure = pressure; lastTiltX = tiltX; lastTiltY = tiltY; lastTime = time;
        }
        outline.sample(x, y, pressure, tiltX, tiltY, time);
    }
    @Override public void present() { outline.present(); }
    @Override public void zoom(float scale) { outline.zoom(scale); }
    @Override public boolean holdsWetPaint() { return outline.holdsWetPaint(); }
    @Override public boolean buildsWhileHeld() { return outline.buildsWhileHeld(); }
    @Override public float ringGuide(float pressure, float tiltX, float tiltY) { return outline.ringGuide(pressure, tiltX, tiltY); }
    @Override public int ringMode() { return outline.ringMode(); }
    @Override public boolean screened() { return outline instanceof LiftingStroke && ((LiftingStroke) outline).screened(); }

    @Override public boolean developsAfterLift() { return !done; }
    @Override public boolean lift() {
        if (done) return false;
        if (!closed) {
            close();
            outlineLifting = outline instanceof LiftingStroke && ((LiftingStroke) outline).developsAfterLift() && ((LiftingStroke) outline).lift();
        }
        return true;
    }
    @Override public boolean develop(long nanos) {
        if (!closed || done) return false;
        if (!outlineDone) {
            if (outlineLifting && ((LiftingStroke) outline).develop(nanos)) return true;
            finishOutline();
        }
        // A big fill shows flat at once, and the screen gets a look before it is laid.
        if (!previewed) {
            previewed = true;
            if (room != null && preview != null && room.area >= PREVIEW_PX) { preview.accept(room, darkness); return true; }
        }
        fill();
        return false;
    }
    @Override public boolean finish() {
        if (done) return changed;
        close();
        if (!outlineDone) finishOutline();
        fill();
        return changed;
    }

    /** At the lift, once: the area the outline encloses, and unless it is tiny, the outline closed by a sample at its start. */
    private void close() {
        if (closed) return;
        closed = true;
        room = n < 3 ? null : room(xs, ys, n, maps, document.width, document.height);
        if (room != null && view != null) room = FillTool.keptSide(room, view);
        if (room == null || tiny(room.area / (double) maps.length, size)) { room = null; return; }
        // The closing line drawn at the hand's own pace.
        double gap = Math.hypot(xs[0] - xs[n - 1], ys[0] - ys[n - 1]);
        long time = lastTime == PenSpeed.UNTIMED || firstTime == PenSpeed.UNTIMED || travel <= 0 ? PenSpeed.UNTIMED
                : lastTime + Math.max(1, Math.round(gap * (lastTime - firstTime) / travel));
        outline.sample(xs[0], ys[0], lastPressure, lastTiltX, lastTiltY, time);
        outline.present();
    }
    /** Whether an outline enclosing {@code area} page px with a brush {@code size} px wide stays a stroke: a dot or a short line. */
    static boolean tiny(double area, double size) { return area < Math.max(MIN_AREA, size * size); }
    private void finishOutline() { outlineDone = true; changed |= outline.finish(); }
    /** The fill, laid and finished, if the heap can take it; then the outline's steps and its linked as one. */
    private void fill() {
        if (done) return;
        done = true;
        if (room != null && !fits(room.area)) { System.gc(); if (!fits(room.area)) { document.trimHistory(TIGHT_HISTORY); System.gc(); } }
        if (room != null && fits(room.area)) {
            ModularStroke s = FillTool.start(document, brush, size, darkness, session, room, true);
            s.present();
            changed |= s.finish();
        }
        document.linkSince(mark);
    }
    private static boolean fits(long area) { Runtime r = Runtime.getRuntime(); return area * PEAK_BYTES_PX <= r.maxMemory() - (r.totalMemory() - r.freeMemory()); }

    /**
     * The area the closed polygon {@code xs, ys} ({@code n} points, page px) encloses by non-zero winding, under each of
     * {@code maps} ({@link Mirrors.Group}'s affine maps), together, on a {@code width × height} page; null for none. A
     * pixel is in when its centre is. A byte a pixel of the box round it, and the crossings of its rows.
     */
    static FillRegion.Room room(float[] xs, float[] ys, int n, double[][] maps, int width, int height) {
        double[][] px = new double[maps.length][n], py = new double[maps.length][n];
        double x0 = Double.POSITIVE_INFINITY, y0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, y1 = Double.NEGATIVE_INFINITY;
        for (int m = 0; m < maps.length; m++) for (int i = 0; i < n; i++) {
            double[] p = Mirrors.Group.apply(maps[m], xs[i], ys[i]);
            px[m][i] = p[0]; py[m][i] = p[1];
            x0 = Math.min(x0, p[0]); y0 = Math.min(y0, p[1]); x1 = Math.max(x1, p[0]); y1 = Math.max(y1, p[1]);
        }
        int bx0 = Math.max(0, (int) Math.floor(x0)), by0 = Math.max(0, (int) Math.floor(y0)), bx1 = Math.min(width - 1, (int) Math.ceil(x1)), by1 = Math.min(height - 1, (int) Math.ceil(y1));
        if (bx0 > bx1 || by0 > by1) return null;
        int bw = bx1 - bx0 + 1, bh = by1 - by0 + 1;
        byte[] at = new byte[bw * bh];
        for (int m = 0; m < maps.length; m++) scan(px[m], py[m], n, bx0, by0, bw, bh, at);
        int area = 0;
        for (byte b : at) if (b != FillRegion.OUT) area++;
        return area == 0 ? null : new FillRegion.Room(bx0, by0, bx1, by1, at, area);
    }
    /** One polygon's inside (non-zero winding) marked {@link FillRegion#OPEN} in {@code at}, the box {@code bw × bh} at {@code bx0, by0}. */
    private static void scan(double[] xs, double[] ys, int n, int bx0, int by0, int bw, int bh, byte[] at) {
        // Each edge's crossings of the rows' centres, by row: counted, then laid out, then sorted along each row.
        int[] start = new int[bh + 1];
        for (int pass = 0; pass < 2; pass++) {
            long[] keys = pass == 0 ? null : new long[start[bh]];
            int[] fill = pass == 0 ? null : start.clone();
            for (int i = 0; i < n; i++) {
                double ax = xs[i], ay = ys[i], bx = xs[(i + 1) % n], by = ys[(i + 1) % n];
                if (ay == by) continue;
                int r0 = Math.max(0, (int) Math.ceil(Math.min(ay, by) - .5) - by0), r1 = Math.min(bh, (int) Math.ceil(Math.max(ay, by) - .5) - by0);
                for (int r = r0; r < r1; r++) {
                    if (pass == 0) { start[r + 1]++; continue; }
                    double yc = by0 + r + .5, x = ax + (yc - ay) * (bx - ax) / (by - ay);
                    // Along the row in 1/256 px, kept a pixel past the box (the spans are cut to it), the winding's sign in bit 0.
                    double cx = Math.max(-1, Math.min(bw + 1, x - bx0)) + 2;
                    keys[fill[r]++] = (Math.round(cx * 256) << 1) | (by > ay ? 1 : 0);
                }
            }
            if (pass == 0) { for (int r = 0; r < bh; r++) start[r + 1] += start[r]; continue; }
            for (int r = 0; r < bh; r++) {
                java.util.Arrays.sort(keys, start[r], start[r + 1]);
                int wind = 0;
                double from = 0;
                for (int k = start[r]; k < start[r + 1]; k++) {
                    double x = (keys[k] >> 1) / 256.0 - 2;
                    if (wind != 0) {
                        int a = Math.max(0, (int) Math.ceil(from - .5)), b = Math.min(bw, (int) Math.ceil(x - .5));
                        for (int c = a, o = r * bw + a; c < b; c++, o++) at[o] = FillRegion.OPEN;
                    }
                    wind += (keys[k] & 1) != 0 ? 1 : -1;
                    from = x;
                }
            }
        }
    }
}
