package io.github.mpdairy.monopaint;

import java.util.Arrays;

/**
 * Wet paint on one layer (the wet paint spec, docs/archive/superpowers/specs/2026-10-22-wet-paint-design.md; the owner's
 * Option A, 2026-10-22): MonoPaint's wet canvas ({@link WetWatercolor} in whole-canvas mode, upstream 8743286, MIT, ©
 * mpdairy; in this fork unchanged but for the hooks it marks "Inkwash"), the session's fourth wet thing beside a Slow
 * wash, a Float bath and Halos ({@link WatercolorSession#wetCanvas}). Wet strokes ({@link WetPaintStroke}) lay their
 * paint into it, final under the pen; between strokes it moves a slice at a time ({@link #step}: a frame every
 * {@link WetWatercolor#FRAME_MS}, each slice's work sized by MonoPaint's {@link WetWorkBudget}), each slice part of the
 * last Wet stroke's Undo step, and it stays wet until it is dried (the owner, 2026-10-22: the droplet's tap, or the page
 * put away; {@link WatercolorSession#settleNow}). Its pixels are the page's as it goes, so drying it lays nothing and an
 * app killed with paint wet keeps it as it was, dry.
 *
 * <p>Anything else that changes the page (another stroke, an Undo, a layer's fade) is taken up the next time it is used
 * ({@link #catchUp}): the wet pixels it changed take what shows as their paint, and the paint rests where it is, still
 * wet, until a Wet stroke wakes it (so the flow never writes over another stroke, and an Undo's Redo stays). A Pastels
 * or Dry brush stroke over it joins it instead ({@link #join}): its marks over the wet paint sink into it and move, and
 * a dry brush drags the wet paint along its path ({@link #step}, a slice at a time, one Undo step with its stroke).
 *
 * <p>All on the page's thread; a slice's pixel work is shared with spare cores inside {@link WetWatercolor}. Pure, checked
 * on the desktop ({@code WetPaintChecks}).
 */
final class WetCanvas {
    final ToneDocument document;
    /** The layer the paint is wet on (a pair's wash half). */
    final int layer;
    final WetWatercolor wet;
    /** MonoPaint's pacing of its slices, kept from canvas to canvas: what a tile costs here is learnt once, not again for every new wet paint. */
    private static final WetWorkBudget budget = new WetWorkBudget();
    /** The page's revision as this canvas last left it: a different one means something else changed the page. */
    private long revision;
    /** Runs Downhill of the last Wet stroke, 0 to 1: how much of the page's tilt reaches the paint. */
    private double runs;
    /** When the frame running now began, ms ({@link System#nanoTime}): flowing water's frames are paced from it. */
    private long frameStarted;
    /** A Pastels or Dry brush stroke over the paint ended: its marks are taken up and moved at the next step ({@link #join}). */
    private boolean joined;
    /** A dry brush's drag still to lay through the paint, or null. */
    private Drag drag;
    /**
     * The widest a slice's tiles spread, page px. MonoPaint keeps an idle slice within 192 px so its render stays small;
     * here no pen waits and the session shows at most every {@link SettlePacer#SHOW_MS}, so a slice takes whatever is due
     * within its time (a long stroke's movement in tens of slices, not thousands, each with its scheduling).
     */
    static int SPAN = 4096;

    WetCanvas(ToneDocument document, int layer) {
        this.document = document; this.layer = layer;
        wet = new WetWatercolor(document, 50);
        revision = document.revision();
    }

    /** Still on {@code document}, its layer there and shown. */
    boolean valid(ToneDocument document) { return document == this.document && layer < document.layerCount() && document.layerVisible(layer); }
    /** Whether the paint is moving, or a joined stroke still waits to be taken up. */
    boolean moving() { return wet.isAnimating() || joined || drag != null; }
    /** Whether the page's tilt moves it now: it moves and runs downhill. */
    boolean runs() { return runs > 0 && wet.isAnimating(); }

    /** A Wet stroke of {@code r} is laid: its wetness, how long what it touches moves, how it runs and what it lays (Erasable, kind). */
    void brush(ResolvedBrush r) {
        wet.setWetness(r.wet.wetness());
        wet.setFrames(r.wet.frames());
        wet.gesture(r.erase, kinds(r));
        // It mixes only with paint (dark on white stays dark); dried paint wets again under Gouache, not under Oils.
        wet.paintOnly(true, !r.wet.rewets);
        // A watercolour wash's water dries back its way (the wet paint spec, section 9), and the washes laid before
        // another Wet stroke keep drying back so until the paint is dried.
        if (r.wet.wash != null) wet.wash(r.wet.wash);
        runs = r.wet.runs;
    }
    /**
     * Takes up what else changed the page since this canvas last left it ({@link WetWatercolor#rewet}): with {@code wake}
     * (a stroke that joins) its tiles move; else the paint rests where it is, still wet. The page may not be mid-gesture.
     */
    void catchUp(boolean wake) {
        if (joined) { wake = true; joined = false; }
        if (document.revision() == revision) return;
        boolean moves = wake;
        onLayer(() -> wet.rewet(moves));
        if (!wake) { wet.rest(); drag = null; }
        seen();
    }
    /** This canvas left the page as it is now (its own stroke or slice just finished). */
    void seen() { revision = document.revision(); }
    /**
     * A Pastels or Dry brush stroke ({@code r}) over the paint ended: at the next {@link #step} its marks over wet paint
     * become wet paint and move; with {@code drags} (a dry brush) the paint is then dragged along its samples {@code pts}.
     */
    void join(ResolvedBrush r, StrokePoints pts, boolean drags) {
        joined = true;
        drag = drags && pts.count > 0 ? new Drag(r, pts) : null;
    }
    /** Lets the paint rest where it is (Undo: it moves again only when a Wet stroke wakes it). */
    void rest() { wet.rest(); drag = null; joined = false; }

    /**
     * One slice, on the page's thread with no stroke down: what else changed the page taken up, a dry brush's drag laid
     * on, else the paint moved by about {@link WetWorkBudget}'s idle budget, gravity at {@code tilt} times Runs Downhill.
     * Returns how long until the next slice, ms, or −1 once the paint rests.
     */
    long step(PageTilt tilt) {
        catchUp(false);
        if (drag != null) { if (drag.slice()) drag = null; seen(); return 1; }
        if (!wet.isAnimating()) return -1;
        wet.setGravity((float) (runs * tilt.x), (float) (runs * tilt.y));
        long t0 = System.nanoTime(), now = t0 / 1_000_000;
        long nanos = budget.nanos(false, 0, wet.activePixels(), now);
        int frame = wet.frame();
        onLayer(() -> wet.advance(false, budget.tiles(nanos, false), nanos / 2, SPAN));
        long t1 = System.nanoTime();
        if (wet.frame() != frame) frameStarted = t0 / 1_000_000;
        budget.completed(t1 - t0, wet.advancedTiles(), false, t1 / 1_000_000);
        seen();
        if (!wet.isAnimating()) return -1;
        // As MonoPaint's DrawingPad.scheduleWet: a frame part done goes on at once; flowing water runs ahead, but a small
        // wash's slowing tail; still paint waits a frame.
        if (wet.framePending()) return 1;
        if (wet.flowing()) return Math.max(1, wet.flowFrameMillis() - (t1 / 1_000_000 - frameStarted));
        return WetWatercolor.FRAME_MS;
    }
    /** Unpaced, for the desktop and the checks: what {@link #step} does until the frame running now is done; false once the paint rests. */
    boolean frame(PageTilt tilt) {
        catchUp(false);
        if (drag != null) { while (!drag.slice()) { } drag = null; seen(); return true; }
        if (!wet.isAnimating()) return false;
        wet.setGravity((float) (runs * tilt.x), (float) (runs * tilt.y));
        onLayer(() -> { do wet.advance(false); while (wet.framePending()); });
        seen();
        return wet.isAnimating();
    }

    /**
     * Inkwash: the kinds its gestures lay, its one plane: the first stroke's on it ({@link ModularStroke#kinds}), so wet
     * paint of another kind laid into it after mixes there, as wet paint does, rather than lying apart.
     */
    private int kinds = -1;
    /** Its plane's kinds ({@link #kinds(ResolvedBrush)}); -1 before its first stroke. */
    int kinds() { return kinds; }
    int kinds(ResolvedBrush r) {
        if (kinds < 0) kinds = ModularStroke.kinds(document, r);
        return kinds;
    }
    /**
     * Runs {@code work} with this canvas's layer active (the page's thread, no gesture open), the active layer put back after.
     * Inkwash: and its paint's plane read ({@link ToneDocument#selectKind}), what was read before put back after.
     */
    private void onLayer(Runnable work) {
        int back = document.activeLayer(), was = document.selectedKinds();
        if (back != layer) document.selectLayer(layer);
        document.selectKind(wet.kinds());
        try { work.run(); } finally { if (back != layer) document.selectLayer(back); document.selectKind(was); }
    }

    /**
     * A dry brush's drag through the wet paint (the proposal's "thirsty brush"): its footprint along its samples, as
     * MonoPaint's dry brush pen lays its water ({@link WetWatercolor#waterMask}, not flowing): it picks up the wet paint
     * it crosses and carries it on, evening it out, lifting nothing that is dry. Laid a few ms at a time, its gestures
     * one Undo step with the stroke that made it.
     */
    private final class Drag {
        /** How hard the drag pulls the paint, and how far it carries what it picked up (MonoPaint's dry pen, middling). */
        static final float PULL = .5f, CARRY = .5f;
        /** Work per slice, ns. */
        static final long SLICE_NS = 4_000_000;
        final StrokePoints pts;
        final Footprint sweep;
        final double erase;
        final int kind;
        int at;
        Drag(ResolvedBrush r, StrokePoints pts) {
            this.pts = pts; erase = r.erase; kind = kinds(r);
            sweep = new Footprint(r, pts, document.width, document.height, r.body == null, new StrokePaper(r.paper));
        }
        /** Lays the drag on for about {@link #SLICE_NS}; true once it is all laid. */
        boolean slice() {
            long t0 = System.nanoTime();
            boolean[] done = {false};
            onLayer(() -> {
                document.begin(erase, kind);
                if (at == 0) wet.beginStroke();
                for (; at < pts.count && System.nanoTime() - t0 < SLICE_NS; at++) {
                    sweep.to(at);
                    sweep.take((mask, x, y, w, h) -> wet.waterMask(mask, w, x, y, w, h, PULL, false, CARRY));
                }
                wet.finishStroke(true);
                done[0] = at >= pts.count;
            });
            return done[0];
        }
    }

    /**
     * A mark's footprint swept along a stroke's samples ({@link FootprintSweep}), its new pixels gathered into one mask
     * over their box for the wet canvas ({@link #take}): what a Wet stroke lays, and what a dry brush drags.
     */
    static final class Footprint extends FootprintSweep {
        /** The new spans, row, first and last pixel each, and their box. */
        private int[] spans = new int[96];
        private int n, x0, y0, x1, y1;
        private int[] mask = new int[0];
        interface Lay { void lay(int[] mask, int x, int y, int w, int h); }

        Footprint(ResolvedBrush r, StrokePoints pts, int width, int height, boolean isLine, StrokePaper paper) {
            super(r, pts, width, height, isLine, paper, false);
            reset();
        }
        @Override void write(int y) {
            span(y, lo[y], hi[y]);
            if (lo2[y] <= hi2[y]) span(y, lo2[y], hi2[y]);
            lo[y] = lo2[y] = Integer.MAX_VALUE; hi[y] = hi2[y] = -1;
        }
        private void span(int y, int a, int b) {
            if (n + 3 > spans.length) spans = Arrays.copyOf(spans, spans.length * 2);
            spans[n++] = y; spans[n++] = a; spans[n++] = b;
            x0 = Math.min(x0, a); x1 = Math.max(x1, b); y0 = Math.min(y0, y); y1 = Math.max(y1, y);
        }
        private void reset() { n = 0; x0 = y0 = Integer.MAX_VALUE; x1 = y1 = -1; }
        /**
         * The pixels swept since the last take, as one mask (alpha 255 where new) handed to {@code lay}; false, and nothing
         * handed, when none. While {@code lay} runs, {@link #rows} holds each mask row's first and last new column (−1, −2:
         * none): a broad round's new pixels are a thin crescent in a box about half the round, so a wet wash reads only
         * those (the coordinator's Manta bench, 2026-10-22); and only the spans written are cleared after, not the box.
         */
        boolean take(Lay lay) {
            flush();
            if (n == 0) return false;
            int w = x1 - x0 + 1, h = y1 - y0 + 1;
            if (mask.length < w * h) mask = new int[w * h];
            if (rows.length < 2 * h) rows = new int[4 * h];
            for (int r = 0; r < h; r++) { rows[2 * r] = Integer.MAX_VALUE; rows[2 * r + 1] = -2; }
            for (int i = 0; i < n; i += 3) {
                int r = spans[i] - y0, row = r * w - x0;
                Arrays.fill(mask, row + spans[i + 1], row + spans[i + 2] + 1, 0xff000000);
                rows[2 * r] = Math.min(rows[2 * r], spans[i + 1] - x0); rows[2 * r + 1] = Math.max(rows[2 * r + 1], spans[i + 2] - x0);
            }
            for (int r = 0; r < h; r++) if (rows[2 * r + 1] < 0) rows[2 * r] = -1;
            int x = x0, y = y0, written = n;
            reset();
            lay.lay(mask, x, y, w, h);
            // The mask is all clear again for the next take: only what was written is cleared.
            for (int i = 0; i < written; i += 3) { int row = (spans[i] - y) * w - x; Arrays.fill(mask, row + spans[i + 1], row + spans[i + 2] + 1, 0); }
            return true;
        }
        /** During {@link #take}'s {@code lay}: each mask row's first and last new column, pairs (−1, −2 for none). */
        int[] rows = new int[0];
    }
}
