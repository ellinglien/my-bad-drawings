package io.github.mpdairy.monopaint;

/**
 * A stroke on a page with mirrors ({@link Mirrors}): the stroke as drawn, and then the same stroke again for each map
 * of the group the mirrors make: its change reflected, or a real stroke of the same brush ({@link Copies}: {@link
 * ModularStroke}, the eraser's, the smudge tool's) fed the samples as that map moves them (its pen's tilt mirrored
 * too), so its textures, wet paint and drying are its own; the brush's noise is fixed to the page. Undone and redone
 * together, as one step ({@link ToneDocument#linkSince}).
 *
 * <p>While the pen is down only the stroke itself is drawn, on the pen's thread as ever; the screen shows the copies
 * from it ({@code MirrorShow}, the canvas's). At lift the stroke finishes (its own work after lift first, {@link
 * LiftingStroke}), then the copies are laid one after another, each on the page as the last left it, so where they
 * overlap they lay over each other as two strokes do: the host looks in between ({@link QuickLift}), one copy a look.
 * A copy wholly off the page is never drawn. A brush whose marks only darken by a factor of their own and are final at
 * lift ({@code reflects}: {@link ModularStroke#laysAgain}, most pens, pencils and washes) has its copies laid as the
 * stroke's change reflected ({@link ToneDocument#layAgain}): each pixel of a copy darkens what it finds as the stroke
 * darkened what it found, a few milliseconds a copy where drawing it again took as long as the stroke. Its paper grain
 * and noise are the stroke's, reflected, not the page's there. Any other (opaque paint, a smudge, the eraser, a wash
 * that settles after lift) has each copy drawn again: a stroke of the same tool fed the samples as the map moves them.
 *
 * <p>Real Mirror ({@link Mirrors.Style#REAL}) has no copies: the reflection is the canvas's view, and the stroke draws
 * on any side. Each sample folds into the kept cell ({@link Mirrors.View#fold}: the inverse of the map whose cell holds
 * it, its tilt turned with it), so the stroke lays the kept side's marks and the view shows them on every side. Where
 * the pen crosses from one cell to another the stroke gets a sample at the crossing ({@link Mirrors.View#leaves}), its
 * pressure, tilt and time between the two, so the mark meets the mirror and goes on as its reflection, no gap, no
 * corner cut. Across a mirror the two cells' folds meet; where they don't (mirrors past the cap, whose cells don't
 * tile), the rest is a stroke of its own from the crossing on ({@link #stretches}, drawn at lift as a copy is), one
 * Undo step with the rest.
 *
 * <p>A fill with Copy Strokes ({@code FillTool.Areas}) is the tap's fill and then a fill for each image's area, each
 * started by its {@link Copies} at lift, one a look, as a copy drawn again is, until it starts none. Pure, checked on
 * the desktop ({@code MirrorChecks}, {@code FillChecks}).
 */
final class MirrorStroke implements LiftingStroke {
    /** Starts one copy's stroke: the same tool at the same size and grey, on the same layer (a fill's: the next area's fill, null when none is left). */
    interface Copies { DrawingStroke start(); }

    private final ToneDocument document;
    /** The stroke as drawn. */
    final DrawingStroke primary;
    /** The maps of its copies (the page itself left out); none for Real Mirror. */
    private final double[][] maps;
    private final Copies copies;
    /** How far the brush's marks may reach past its samples, page pixels: a copy is drawn only if this box round them lands on the page. */
    private final double reach;
    /** Real Mirror's view, which folds every sample into its kept cell; null for Copy Strokes. */
    private final Mirrors.View fold;
    /** The samples, for the copies. */
    private final PenBatch samples = new PenBatch();
    /** Real Mirror: the stretches after a fold that jumps, each its own stroke at lift (rare: mirrors past the cap). */
    private final java.util.List<PenBatch> stretches = new java.util.ArrayList<>();
    /** The last Undo step before the stroke: its own and its copies' after it link into one. */
    private final Object mark;
    /** Whether the copies may be laid as the stroke's change reflected ({@link ModularStroke#laysAgain}); and that change, once the stroke is done (null: each copy drawn again). */
    private final boolean reflects;
    private ToneDocument.Darkening change;
    /** A fill's ({@link #MirrorStroke(ToneDocument, DrawingStroke, Copies)}): its copies the further fills; and whether the last is laid. */
    private final boolean fills;
    private boolean filled;
    /** {@link #areas}: null until reflected copies begin. */
    private java.util.List<int[]> areas;
    private float zoom = 1;
    private boolean lifted, primaryLifting, primaryDone, done, changed;
    /** The next map (Real Mirror: stretch) to draw, and how many were drawn (the culled ones not). */
    private int next;
    int drawn;
    /** Real Mirror: the pen's last sample, as drawn, and its cell (−1 before the first); the last one the stroke got. */
    private float lastX, lastY, lastPressure, lastTiltX, lastTiltY, sentX = Float.NaN, sentY = Float.NaN;
    private long lastTime;
    private int lastCell = -1;
    /** Folds of a crossing further apart than this, page px, jump: the stroke goes on as another. */
    static final double JUMP = .5;

    /**
     * Copy Strokes: {@code primary} and its copies, one for each map of {@code group} but the first (the page itself);
     * {@code reflects}: laid as its change reflected, where its step allows ({@link ToneDocument#darkeningSince}), else
     * each started by {@code copies} and drawn again.
     */
    MirrorStroke(ToneDocument document, DrawingStroke primary, Mirrors.Group group, Copies copies, double reach, boolean reflects) {
        this.document = document; this.primary = primary; this.copies = copies; this.reach = reach; this.reflects = reflects; fold = null; fills = false;
        maps = java.util.Arrays.copyOfRange(group.maps, 1, group.maps.length);
        mark = document.lastStep();
    }
    /** A fill with Copy Strokes: {@code primary} the tap's fill, then at lift each fill {@code more} starts, until it starts none. */
    MirrorStroke(ToneDocument document, DrawingStroke primary, Copies more) {
        this.document = document; this.primary = primary; copies = more; reach = 0; reflects = false; fold = null; fills = true;
        maps = new double[0][];
        mark = document.lastStep();
    }
    /** Real Mirror: {@code primary} fed the samples folded into {@code view}'s kept cell; {@code copies} starts a stretch after a jump. */
    MirrorStroke(ToneDocument document, DrawingStroke primary, Mirrors.View view, Copies copies) {
        this.document = document; this.primary = primary; this.copies = copies; reach = 0; reflects = false; fold = view; fills = false;
        maps = new double[0][];
        mark = document.lastStep();
    }

    /** Whether the pen is still on it (no copy drawn yet): only then does the canvas show live copies of what the page's gesture changes. */
    boolean live() { return !lifted && !done; }
    @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY) { sample(x, y, pressure, tiltX, tiltY, PenSpeed.UNTIMED); }
    @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY, long time) {
        if (fold == null) {
            if (maps.length > 0) samples.add(x, y, pressure, tiltX, tiltY, time);
            primary.sample(x, y, pressure, tiltX, tiltY, time);
            return;
        }
        if (!Float.isFinite(x) || !Float.isFinite(y)) { send(x, y, pressure, tiltX, tiltY, time); return; }
        int k = fold.cell(x, y);
        if (lastCell >= 0 && k != lastCell) cross(x, y, pressure, tiltX, tiltY, time);
        lay(k, x, y, pressure, tiltX, tiltY, time, false);
        lastCell = k; lastX = x; lastY = y; lastPressure = pressure; lastTiltX = tiltX; lastTiltY = tiltY; lastTime = time;
    }
    /** Real Mirror: the pen went from the last sample's cell to {@code x, y}'s: a sample at each crossing on the way, folded by the cell it leaves; past a jump, the next stretch begins there. */
    private void cross(float x, float y, float pressure, float tiltX, float tiltY, long time) {
        int cell = lastCell;
        double from = 0;
        for (int guard = 0; guard < fold.ox.length; guard++) {
            double[] out = fold.leaves(cell, lastX, lastY, x, y, from);
            // A crossing at the sample itself is the sample.
            if (out == null || out[0] >= 1 - 1e-9) break;
            double t = out[0];
            int into = (int) out[1];
            float cx = lerp(lastX, x, t), cy = lerp(lastY, y, t), cp = lerp(lastPressure, pressure, t);
            float tx = Float.isFinite(lastTiltX) && Float.isFinite(tiltX) ? lerp(lastTiltX, tiltX, t) : tiltX;
            float ty = Float.isFinite(lastTiltY) && Float.isFinite(tiltY) ? lerp(lastTiltY, tiltY, t) : tiltY;
            long ct = time == PenSpeed.UNTIMED || lastTime == PenSpeed.UNTIMED ? time : lastTime + Math.round((time - lastTime) * t);
            lay(cell, cx, cy, cp, tx, ty, ct, true);
            double[] a = fold.fold(cell, cx, cy), b = fold.fold(into, cx, cy);
            if (Math.hypot(a[0] - b[0], a[1] - b[1]) > JUMP) {
                stretches.add(new PenBatch());
                lay(into, cx, cy, cp, tx, ty, ct, true);
            }
            cell = into; from = t;
        }
    }
    private static float lerp(float a, float b, double t) { return (float) (a + (b - a) * t); }
    /** Real Mirror: {@code x, y} in cell {@code k}, folded into the kept cell, to the stroke; a crossing's ({@code crossing}) not again where the last one went. */
    private void lay(int k, float x, float y, float pressure, float tiltX, float tiltY, long time, boolean crossing) {
        double[] p = fold.fold(k, x, y);
        float fx = (float) p[0], fy = (float) p[1];
        if (crossing && Math.abs(fx - sentX) < 1e-3 && Math.abs(fy - sentY) < 1e-3) return;
        float[] tilt = Mirrors.Group.tilt(fold.inverse[k], tiltX, tiltY);
        send(fx, fy, pressure, tilt[0], tilt[1], time);
    }
    /** Real Mirror: a sample for the stroke being drawn, the stroke itself or the last stretch. */
    private void send(float x, float y, float pressure, float tiltX, float tiltY, long time) {
        if (Float.isFinite(x) && Float.isFinite(y)) { sentX = x; sentY = y; }
        if (stretches.isEmpty()) primary.sample(x, y, pressure, tiltX, tiltY, time);
        else stretches.get(stretches.size() - 1).add(x, y, pressure, tiltX, tiltY, time);
    }
    @Override public void present() { primary.present(); }
    @Override public void zoom(float scale) { zoom = scale; primary.zoom(scale); }
    @Override public boolean holdsWetPaint() { return primary.holdsWetPaint(); }
    @Override public boolean buildsWhileHeld() { return primary.buildsWhileHeld(); }
    @Override public float ringGuide(float pressure, float tiltX, float tiltY) { return primary.ringGuide(pressure, tiltX, tiltY); }
    @Override public int ringMode() { return primary.ringMode(); }
    @Override public boolean screened() { return primary instanceof LiftingStroke && ((LiftingStroke) primary).screened(); }

    @Override public boolean developsAfterLift() {
        return !done && ((fills ? !filled : next < (fold == null ? maps.length : stretches.size())) || primary instanceof LiftingStroke && ((LiftingStroke) primary).developsAfterLift());
    }
    @Override public boolean lift() {
        if (done) return false;
        if (!lifted) {
            lifted = true;
            primaryLifting = primary instanceof LiftingStroke && ((LiftingStroke) primary).developsAfterLift() && ((LiftingStroke) primary).lift();
        }
        return true;
    }
    @Override public boolean develop(long nanos) {
        if (!lifted || done) return false;
        long end = System.nanoTime() + Math.min(nanos, Long.MAX_VALUE / 4);
        if (!primaryDone) {
            if (primaryLifting && ((LiftingStroke) primary).develop(nanos)) return true;
            finishPrimary();
        }
        // One copy a look at least; more while the time lasts.
        do { if (!copyNext()) { complete(); return false; } } while (System.nanoTime() < end);
        return true;
    }
    @Override public boolean finish() {
        if (done) return changed;
        if (!primaryDone) finishPrimary();
        while (copyNext()) { }
        complete();
        return changed;
    }
    private void finishPrimary() {
        primaryDone = true; changed |= primary.finish();
        if (reflects && maps.length > 0) change = document.darkeningSince(mark);
        if (change == null) return;
        // What is still to render (the stroke's last part), then each copy's box.
        areas = new java.util.ArrayList<>();
        int[] left = document.dirty();
        if (left != null) areas.add(left);
    }
    /**
     * Once done, where the reflected copies went, to render each apart rather than one box round them all (a
     * kaleidoscope's round the page's middle): what was still to render when they began, then each copy's box, page px
     * {left, top, right, bottom} exclusive. Every pixel changed since the last render lies in one. Null when the copies
     * were drawn again (their wet paint may spread past any box worked out here), or none were laid.
     */
    java.util.List<int[]> areas() { return done ? areas : null; }
    private void complete() { done = true; document.linkSince(mark); }

    /** Draws the next copy (none if it would land off the page), or Real Mirror's next stretch; false when none is left. */
    private boolean copyNext() {
        if (fills) {
            DrawingStroke s = filled ? null : copies.start();
            if (s == null) { filled = true; return false; }
            lay(s);
            return true;
        }
        if (fold != null) {
            if (next >= stretches.size()) return false;
            draw(Mirrors.Group.IDENTITY, stretches.get(next++));
            return true;
        }
        if (next >= maps.length) return false;
        double[] t = maps[next++];
        if (change != null) {
            if (document.layAgain(change, t)) {
                changed = true; drawn++;
                double[] b = Mirrors.Group.box(t, change.box[0], change.box[1], change.box[2], change.box[3]);
                areas.add(new int[]{Math.max(0, (int) Math.floor(b[0]) - 2), Math.max(0, (int) Math.floor(b[1]) - 2), Math.min(document.width, (int) Math.ceil(b[2]) + 2), Math.min(document.height, (int) Math.ceil(b[3]) + 2)});
            }
            return true;
        }
        if (samples.isEmpty() || !lands(t)) return true;
        draw(t, samples);
        return true;
    }
    /** A stroke of the same tool, fed {@code batch} as {@code t} moves it. */
    private void draw(double[] t, PenBatch batch) {
        DrawingStroke s = copies.start();
        s.zoom(zoom);
        for (int i = 0; i < batch.size(); i++) {
            float x = batch.x(i), y = batch.y(i);
            float[] tilt = Mirrors.Group.tilt(t, batch.tiltX(i), batch.tiltY(i));
            s.sample((float) (t[0] * x + t[1] * y + t[4]), (float) (t[2] * x + t[3] * y + t[5]), batch.pressure(i), tilt[0], tilt[1], batch.time(i));
        }
        lay(s);
    }
    /** A copy's stroke, its samples in, shown and finished. */
    private void lay(DrawingStroke s) {
        s.present();
        changed |= s.finish();
        drawn++;
    }
    /** Whether the samples' box, grown by {@link #reach}, lands on the page under {@code t}. */
    private boolean lands(double[] t) {
        double x0 = Double.POSITIVE_INFINITY, y0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, y1 = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < samples.size(); i++) {
            float x = samples.x(i), y = samples.y(i);
            if (!Float.isFinite(x) || !Float.isFinite(y)) continue;
            x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
        }
        if (x0 > x1) return false;
        double[] b = Mirrors.Group.box(t, x0 - reach, y0 - reach, x1 + reach, y1 + reach);
        return b[2] >= 0 && b[3] >= 0 && b[0] < document.width && b[1] < document.height;
    }
}
