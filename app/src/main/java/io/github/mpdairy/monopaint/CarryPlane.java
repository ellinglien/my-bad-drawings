package io.github.mpdairy.monopaint;

/**
 * The page a smudge moves (Smudge spec, "How A works in the app"): per page pixel of the stroke's own layer, its
 * coverage {@code a} and darkness {@code p = a × (1 − raw / 255)} (premultiplied: the tone over white is
 * {@code 255 × (1 − p)}), 0 to 1, in {@link ToneDocument#TILE}-square tiles, each filled the first time the smudge
 * reaches it from the layer as the gesture found it ({@link ToneDocument#strokeBasePixels}: the page at pen-down, even
 * after the stroke has written there). The smudge picks up from and lays into this plane, so a later dab drags what an
 * earlier dab smeared, never what the stroke itself painted; the stroke composes its own paint over it
 * ({@link ModularCompose}). Freed at lift with the stroke. Pure, checked on the desktop.
 *
 * <p>A quick stroke's render ({@link QuickStroke}) may not read the page, which its preview holds: there the pen's
 * thread fills the tiles a batch can reach before handing it over ({@link #fill}), and the render thread only reads
 * and writes tiles already filled ({@code onDemand} false; a tile it finds unfilled is passed over and counted in
 * {@link #missed}, which the checks keep at 0).
 *
 * <p>A stump's other half ({@code ModularStroke.alsoBlends}): a plane of a layer no gesture is changing, the selected
 * pair's wash under a Blend stump on its ink, filled from that layer as it is, which is as the stroke found it.
 *
 * <p>Erasable: only what the eraser could take moves. On a layer that keeps paint ({@link ToneDocument#strokeBaseKept}),
 * a pixel's kept part (its kept coverage, at the pixel's tone) stays where it is, held beside the plane; the plane holds
 * the rest, the loose part, which the smudge trades; what lies under the stroke is the two together. Under masking fluid
 * or wax (Resist) the share it shields stays put the same way: the smudge picks up only what isn't shielded.
 */
final class CarryPlane {
    static final int SIDE = ToneDocument.TILE;
    private final ToneDocument document;
    private final int columns, rows;
    /** Each tile's coverage and darkness, row by row, or null where not yet filled: its loose part, all of it on a layer that keeps nothing. */
    private final float[][] a, p;
    /** Each filled tile's kept part (coverage and darkness) that doesn't move, or null where the tile keeps none. */
    private final float[][] keptA, keptP;
    /** Each tile's pixels a dab changed since they were last composed (1), beside {@link #a}. */
    private final byte[][] fresh;
    private final boolean onDemand;
    /** The layer it moves: −1, the gesture's, as the gesture found it; else that layer as it is ({@link ToneDocument#layerPixels}). */
    private final int layer;
    /** Inkwash: that layer's plane it moves, by kind ({@link ToneDocument#planeOf}; 0: its base). */
    private final int kinds;
    /**
     * Each filled tile with no loose paint, none it could move ({@link #blank}): what a stump's spot reads there is
     * nothing to soften. Unset for good once a dab may have written it ({@link #unblank}).
     */
    private final boolean[] blank;
    /**
     * Clear Water ({@link #water}): each filled tile's loose darkness as found, and how wet each pixel is (0 to 1, the
     * dabs' edge), null until it is first wet; what {@link ClearWater} lets run out at lift reads both.
     */
    private float[][] foundP, wet;
    /** Clear Water: a wash still wet here ({left, top, right, bottom}) isn't set yet, so all of it is loose; null for none. */
    private int[] unset;
    /** A tile's worth of tones and coverages, read for a fill. */
    private final int[] tones = new int[SIDE * SIDE], coverages = new int[SIDE * SIDE], kept = new int[SIDE * SIDE];
    /** Pixels a dab passed over because their tile wasn't filled (a quick render reaching past what the pen's thread filled). */
    int missed;
    /** Counts {@code n} more {@link #missed} (dabs' bands report from several threads). */
    synchronized void missed(int n) { missed += n; }

    /** The stroke's own layer of {@code document}; {@code onDemand}: a tile is filled from the page when first reached. */
    CarryPlane(ToneDocument document, boolean onDemand) { this(document, onDemand, -1, 0); }
    /** As {@link #CarryPlane(ToneDocument, boolean)}, of {@code layer} as it is (−1: the gesture's layer as found), its plane for {@code kinds}. */
    CarryPlane(ToneDocument document, boolean onDemand, int layer, int kinds) {
        this.document = document; this.onDemand = onDemand; this.layer = layer; this.kinds = kinds;
        columns = (document.width + SIDE - 1) / SIDE; rows = (document.height + SIDE - 1) / SIDE;
        a = new float[columns * rows][]; p = new float[columns * rows][]; fresh = new byte[columns * rows][];
        keptA = new float[columns * rows][]; keptP = new float[columns * rows][];
        blank = new boolean[columns * rows];
    }

    /**
     * Keeps what Clear Water needs at lift: the darkness found, and where it wet the page; within {@code stillWet} (the
     * box of a wash still wet, or null) the paint isn't set yet, so the eraser's rule doesn't hold it there (masking
     * fluid's still does). Before any tile is filled.
     */
    void water(int[] stillWet) { foundP = new float[a.length][]; wet = new float[a.length][]; unset = stillWet; }
    /** Tile {@code tx, ty}'s loose darkness as found (Clear Water's), beside {@link #darknessTile}; null if unfilled. */
    float[] foundTile(int tx, int ty) { return foundP == null ? null : foundP[ty * columns + tx]; }
    /** Tile {@code tx, ty}'s wetness (Clear Water's), made on first use for a filled tile; null without {@link #water}. */
    float[] wetTile(int tx, int ty) {
        if (wet == null) return null;
        int k = ty * columns + tx;
        float[] w = wet[k];
        if (w == null && a[k] != null) synchronized (this) { w = wet[k]; if (w == null) wet[k] = w = new float[SIDE * SIDE]; }
        return w;
    }
    /** Tile {@code tx, ty}'s wetness if it was ever wet, else null (it never was, or isn't filled). */
    float[] wetIfAny(int tx, int ty) { return wet == null ? null : wet[ty * columns + tx]; }
    /** The plane's tiles across and down. */
    int columns() { return columns; }
    int rows() { return rows; }
    /**
     * Clear Water's lift at page pixel {@code x, y} (of a filled tile; {@link ClearWater#lift}): darkened by {@code glaze},
     * 0 to 1, a glaze over what lies there, kept and loose together, which its loose part takes; then {@code wet} of its
     * loose paint (0 to 1) is wet again, and {@code held} of that (0 to 1) is lifted off for now, to settle back after
     * lift. Returns the wet paint's darkness over the rest (a wash's at lift, {@link DryingWater}'s), so that the pixel
     * shows the rest darkened by {@code (1 − held)} of it. Marked fresh.
     */
    double rewet(int x, int y, double glaze, double wet, double held) {
        int k = (y / SIDE) * columns + x / SIDE, i = (y % SIDE) * SIDE + x % SIDE;
        float[] ka = keptA[k], kp = keptP[k];
        double kept = kp == null ? 0 : kp[i], all = ka == null ? a[k][i] : ka[i] + a[k][i];
        double loose = p[k][i];
        if (glaze > 0) {
            loose += glaze * Math.max(0, 1 - (kept + loose));
            a[k][i] += (float) (glaze * Math.max(0, 1 - all));
        }
        double dark = Math.min(1, kept + loose), rest = 1 - (dark - wet * loose), d = rest > 0 ? StrokeMath.clamp(1 - (1 - dark) / rest) : 0;
        if (held > 0 && d > 0) loose = Math.max(0, 1 - rest * (1 - d * (1 - held)) - kept);
        p[k][i] = (float) loose;
        fresh[k][i] = 1;
        blank[k] = false;
        return d;
    }
    /**
     * Fills every tile the page rectangle {@code x0..x1, y0..y1} (inclusive, clipped) touches from the page as the
     * gesture found it, if not yet filled. Only on the thread that may read the page.
     */
    void fill(int x0, int y0, int x1, int y1) {
        x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(document.width - 1, x1); y1 = Math.min(document.height - 1, y1);
        if (x1 < x0 || y1 < y0) return;
        for (int ty = y0 / SIDE; ty <= y1 / SIDE; ty++) for (int tx = x0 / SIDE; tx <= x1 / SIDE; tx++) fillTile(tx, ty);
    }
    /** {@link #fill}, if this plane fills its tiles itself ({@code onDemand}): a dab's tiles before its bands run on several threads. */
    void reach(int x0, int y0, int x1, int y1) { if (onDemand) fill(x0, y0, x1, y1); }
    private void fillTile(int tx, int ty) {
        int k = ty * columns + tx;
        if (a[k] != null) return;
        int left = tx * SIDE, top = ty * SIDE, w = Math.min(SIDE, document.width - left), h = Math.min(SIDE, document.height - top);
        boolean keeps;
        if (layer < 0) { document.strokeBasePixels(left, top, w, h, tones, coverages); keeps = document.strokeBaseKept(left, top, w, h, kept); }
        else { document.layerPixels(layer, kinds, left, top, w, h, tones, coverages); keeps = document.layerKept(layer, kinds, left, top, w, h, kept); }
        boolean masked = document.hasMaskLayer(), loose = false;
        float[] ta = new float[SIDE * SIDE], tp = new float[SIDE * SIDE], ka = null, kp = null;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int o = y * w + x, i = y * SIDE + x, c = coverages[o], keep = keeps && !unset(left + x, top + y) ? Math.min(c, kept[o]) : 0;
            // What masking fluid shields doesn't move either.
            if (masked && c > 0) keep = Math.max(keep, (c * document.maskShield(left + x, top + y) + 127) / 255);
            ta[i] = c / 255f; tp[i] = (255 - tones[o]) / 255f;
            if (keep == 0) continue;
            if (ka == null) { ka = new float[SIDE * SIDE]; kp = new float[SIDE * SIDE]; }
            // The kept part at the pixel's tone; the rest is loose. All kept: nothing moves.
            if (keep == c) { ka[i] = ta[i]; kp[i] = tp[i]; ta[i] = 0; tp[i] = 0; }
            else { ka[i] = keep / 255f; kp[i] = tp[i] * keep / c; ta[i] -= ka[i]; tp[i] -= kp[i]; }
        }
        for (int i = 0; i < ta.length && !loose; i++) loose = ta[i] != 0 || tp[i] != 0;
        blank[k] = !loose;
        keptA[k] = ka; keptP[k] = kp;
        if (foundP != null) foundP[k] = tp.clone();
        fresh[k] = new byte[SIDE * SIDE]; p[k] = tp; a[k] = ta;
    }

    /**
     * Whether every tile the page rectangle {@code x0..x1, y0..y1} (inclusive, clipped) touches is filled and holds no
     * loose paint: a stump reading only there moves nothing.
     */
    boolean blank(int x0, int y0, int x1, int y1) {
        x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(document.width - 1, x1); y1 = Math.min(document.height - 1, y1);
        for (int ty = y0 / SIDE; ty <= y1 / SIDE; ty++) for (int tx = x0 / SIDE; tx <= x1 / SIDE; tx++) { int k = ty * columns + tx; if (a[k] == null || !blank[k]) return false; }
        return true;
    }
    /** The tiles the page rectangle {@code x0..x1, y0..y1} touches may hold loose paint from now on: a dab writes there. */
    void unblank(int x0, int y0, int x1, int y1) {
        x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(document.width - 1, x1); y1 = Math.min(document.height - 1, y1);
        for (int ty = y0 / SIDE; ty <= y1 / SIDE; ty++) for (int tx = x0 / SIDE; tx <= x1 / SIDE; tx++) blank[ty * columns + tx] = false;
    }
    /** The box ({left, top, right, bottom}, inclusive) of the tiles a dab changed since they were last composed ({@link #takeFresh}), or null for none. */
    int[] freshBounds() {
        int[] box = null;
        for (int k = 0; k < fresh.length; k++) {
            byte[] f = fresh[k];
            if (f == null) continue;
            boolean any = false;
            for (int i = 0; i < f.length && !any; i++) any = f[i] != 0;
            if (!any) continue;
            int x0 = k % columns * SIDE, y0 = k / columns * SIDE, x1 = Math.min(document.width, x0 + SIDE) - 1, y1 = Math.min(document.height, y0 + SIDE) - 1;
            if (box == null) box = new int[]{x0, y0, x1, y1};
            else { box[0] = Math.min(box[0], x0); box[1] = Math.min(box[1], y0); box[2] = Math.max(box[2], x1); box[3] = Math.max(box[3], y1); }
        }
        return box;
    }

    /** Whether page pixel {@code x, y} lies in a wash still wet ({@link #water}). */
    private boolean unset(int x, int y) { return unset != null && x >= unset[0] && x <= unset[2] && y >= unset[1] && y <= unset[3]; }
    /** Tile {@code tx, ty}'s coverages, filled first when {@code onDemand}; null if unfilled. */
    float[] coverageTile(int tx, int ty) {
        int k = ty * columns + tx;
        if (a[k] == null && onDemand) fillTile(tx, ty);
        return a[k];
    }
    /** Tile {@code tx, ty}'s darkness, beside {@link #coverageTile}; null if unfilled. */
    float[] darknessTile(int tx, int ty) { return p[ty * columns + tx]; }
    /** Tile {@code tx, ty}'s fresh marks ({@link #fresh}), beside {@link #coverageTile}; null if unfilled. */
    byte[] freshTile(int tx, int ty) { return fresh[ty * columns + tx]; }
    /**
     * Whether a dab changed page pixel {@code x, y} (of a filled tile) since it was last composed, and from now on not:
     * a compose that skips pixels whose line and body are as last composed composes these. Compose bands own whole tile
     * rows, so bands never share a mark.
     */
    boolean takeFresh(int x, int y) {
        byte[] f = fresh[(y / SIDE) * columns + x / SIDE];
        int i = (y % SIDE) * SIDE + x % SIDE;
        if (f[i] == 0) return false;
        f[i] = 0;
        return true;
    }
    /** Whether the tile holding page pixel {@code x, y} is filled: there the plane, not the page as found, is what lies under the stroke. */
    boolean has(int x, int y) { return a[(y / SIDE) * columns + x / SIDE] != null; }
    /** The tone over white at page pixel {@code x, y} of a filled tile, its kept and loose parts together, 0–255, rounded. */
    int tone(int x, int y) {
        int k = (y / SIDE) * columns + x / SIDE, i = (y % SIDE) * SIDE + x % SIDE;
        float[] kp = keptP[k];
        return (int) Math.round(255 * (1 - (kp == null ? (double) p[k][i] : Math.min(1, (double) kp[i] + p[k][i]))));
    }
    /** The coverage at page pixel {@code x, y} of a filled tile, its kept and loose parts together, 0–255, rounded. */
    int coverage(int x, int y) {
        int k = (y / SIDE) * columns + x / SIDE, i = (y % SIDE) * SIDE + x % SIDE;
        float[] ka = keptA[k];
        return (int) Math.round(255 * (ka == null ? (double) a[k][i] : Math.min(1, (double) ka[i] + a[k][i])));
    }
}
