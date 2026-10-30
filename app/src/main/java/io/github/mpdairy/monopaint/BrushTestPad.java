package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

/**
 * Brush Lab's test area: a scratch page of its own, one pair (ink over wash), page pixels at screen pixels, drawn
 * on by the pen with the same strokes the drawing screen uses (see {@link InkWash#stroke(ToneDocument, Brush,
 * ToolSwatches.Look, WatercolorSession)}). Shown in the
 * page's dots, presented straight to the e-ink panel (mode 7) with the firmware's own pen ink blocked, and
 * refreshed whole once the pen has been away a while, as {@link DrawingPad} does. Nothing lies over the pad (Clear
 * and the sizes sit in a head above it, the brush's loop beside its name): what the pad presents is only the page, so
 * no refresh can paint over a control. A new size keeps what is drawn, top-left anchored. Cut Stamp ({@link #cutNext}):
 * the pen's next stroke is a lasso, shown in marching ants over the page ({@link MirrorShow}) through the panel's fast
 * update, whose ink becomes a Stamp's nib.
 */
@SuppressLint("ViewConstructor")
final class BrushTestPad extends View {
    /** Starts a stroke on the test page: the lab's brush, or the eraser for the pen's eraser end or side button. */
    interface Strokes { DrawingStroke start(ToneDocument page, boolean eraserEnd); }
    private final boolean landscapeTilt;
    private final Strokes strokes;
    private final WatercolorSession session;
    private final NativePen input;
    private final PenSample pen = new PenSample();
    private ToneDocument page;
    private Bitmap display;
    private DirectEink direct;
    /** A pen stroke's session on the panel's fast update ({@link StrokePanel}), and what it showed; as the drawing screen's. */
    private DirectEink fast;
    private final Rect fastShown = new Rect();
    /** A fast session that has stopped showing, until its end reaches the panel through the gray one ({@link DirectEink#endFast}). */
    private DirectEink ending;
    /** A Spiro's ring, shown through a fast session of its own at the touch-down (as the drawing screen's {@code ringDirect}), and what it showed. */
    private DirectEink ringFast;
    private final Rect ringShown = new Rect();
    private DrawingStroke stroke;
    private int pointer = -1, retries;
    /** Something over the pad (the Load sheet, the rename keyboard) that direct presenting would draw over: Android draws the pad meanwhile. */
    private boolean covered;
    /** Display pixels changed since they were last presented. */
    private final Rect pending = new Rect();
    /** The pad changed on the panel since its last full refresh. */
    private boolean unrefreshed;
    private final Runnable retry = this::flush;
    /**
     * The pen's samples gathered and drawn together once the input queue is empty, and the stroke's presents spaced by
     * their cost, as the drawing screen's ({@link DrawingPad}'s {@code drawBatch}, {@link PresentPace}): drawing,
     * rendering and presenting after each of the Manta's 480 events a second held a big brush's input up (the owner,
     * 2026-10-25: "sluggish in brush studio but not in main").
     */
    private final PenBatch batch = new PenBatch();
    private final Runnable drawBatch = this::drawBatch;
    private final PresentPace pace = new PresentPace();
    private final Runnable refresh = this::refresh;
    /** A six-part Drying wash settling after lift, in the lab's session. */
    private final DryingTicker drying;
    /** A stroke's work after lift (a quick stroke's render, a Drying stroke's settle), as the drawing screen's; the stroke stays {@link #stroke} until it is done. */
    private final QuickLift quickLift = new QuickLift(this, this::finishStroke);
    /** Cut Stamp: what takes the next pen stroke's cut (a nib, or null for nothing to cut); null while not cutting. */
    private java.util.function.Consumer<StampNib> cut;
    /** The lasso's marching ants, and a Spiro's ring, over the page and never in it. */
    private final MirrorShow ants = new MirrorShow();
    private int[] antPixels = new int[0];

    BrushTestPad(Context context, boolean landscapeTilt, WatercolorSession session, Strokes strokes) {
        super(context);
        this.landscapeTilt = landscapeTilt; this.strokes = strokes; this.session = session;
        drying = new DryingTicker(this, session, new DryingTicker.Host() {
            @Override public ToneDocument page() { return page; }
            @Override public boolean busy() { return stroke != null; }
            @Override public void show() { renderDirty(); }
            @Override public void settled() { removeCallbacks(refresh); postDelayed(refresh, DrawingPad.PEN_AWAY_MS); }
        });
        input = new NativePen(this, (bitmap, region) -> bitmap.recycle());
        setContentDescription("Test area: draw with the pen to try the brush");
    }

    /** The test page, or null before the pad is laid out. */
    ToneDocument page() { return page; }
    /** A stroke is down (not one working after lift). */
    boolean stroking() { return stroke != null && !quickLift.running(); }
    /** Finishes a stroke still working after lift, then dries what is wet as it would dry, only sooner, and shows it: the wet indicator's tap. */
    void dry() {
        if (quickLift.running()) finishStroke();
        if (stroke == null && page != null) drying.hurry();
    }
    /** Cut Stamp: the pen's next stroke on the test page is a lasso, the ink inside it handed to {@code cut} as a nib (null: nothing to cut); null stops waiting. */
    void cutNext(java.util.function.Consumer<StampNib> cut) { this.cut = cut; }
    /** Whether the pen's next stroke is Cut Stamp's lasso. */
    boolean cutting() { return cut != null; }
    /**
     * Clears the test page: its wet paint dried first (the droplet goes) and the test brush dipped again, so a Wet brush
     * starts full on the fresh page.
     */
    void clear() {
        finishStroke();
        if (page == null) return;
        session.settleNow(page);
        session.held.dip(session.holder());
        page = InkWashLayers.blank(page.width, page.height);
        renderAll();
        refreshSoon();
    }
    /**
     * Something lies over the pad, or no longer does: meanwhile the pad is drawn by Android, under it, rather than
     * presented straight to the panel over it.
     */
    void setCovered(boolean covered) {
        if (covered == this.covered) return;
        this.covered = covered;
        if (covered) { disconnect(); return; }
        renderAll();
        post(this::connect);
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        disconnect();
        if (display != null) { display.recycle(); display = null; }
        if (w <= 0 || h <= 0) return;
        // What is drawn stays, top-left anchored: a wash still settling is laid first.
        if (page != null) session.settleNow(page);
        page = page == null ? InkWashLayers.blank(w, h) : InkWashLayers.resized(page, w, h);
        display = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        display.setHasAlpha(false);
        renderAll();
        post(this::connect);
    }
    /** Moved (the lab laid out again): present at the new place. */
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!changed) return;
        if (direct != null) disconnect();
        post(this::connect);
    }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.WHITE);
        if (display != null) canvas.drawBitmap(display, 0, 0, null);
    }
    /** Leaving the test area (its screen put away) dries what is wet on it, as leaving the page does. */
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE || page == null) return;
        finishStroke();
        if (session.wet()) { session.settleNow(page); renderAll(); }
    }
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) post(this::connect); else disconnect();
    }

    // Pen input

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (page == null || !hasWindowFocus()) return true;
        int action = event.getActionMasked(), index = event.getActionIndex();
        if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) && pointer == -1 && isPen(event, index)) {
            float x = event.getX(index), y = event.getY(index);
            if (x < 0 || y < 0 || x >= page.width || y >= page.height) return true;
            // A stroke still working after lift finishes first.
            finishStroke();
            removeCallbacks(refresh);
            pointer = event.getPointerId(index);
            getParent().requestDisallowInterceptTouchEvent(true);
            boolean eraserEnd = event.getToolType(index) == MotionEvent.TOOL_TYPE_ERASER || (event.getButtonState() & MotionEvent.BUTTON_STYLUS_PRIMARY) != 0;
            if (cut != null && !eraserEnd) ants.page(page.width, page.height);
            stroke = cut != null && !eraserEnd ? new MirrorShow.Place(ants, true) : strokes.start(page, eraserEnd);
            if (StrokePanel.wants(stroke)) beginFast();
            sample(event, index, -1);
            // A Spiro in Ring: its ring at the brush's size, pinned where the pen lands, over the page until the lift (the
            // drawing screen's setup, DrawingPad.RingSetup, is the page's alone).
            float ring = stroke.ringGuide(pen.pressure, pen.tiltX, pen.tiltY);
            if (ring > 0) { ants.page(page.width, page.height); ants.ring(pen.x, pen.y, ring, stroke.ringMode()); showRing(); }
            drawBatch();
            if (stroke.buildsWhileHeld()) { removeCallbacks(heldTick); postDelayed(heldTick, HeldPen.TICK_MS); }
            return true;
        }
        if (stroke == null) return true;
        if (action == MotionEvent.ACTION_CANCEL) { finishStroke(); return true; }
        int p = event.findPointerIndex(pointer);
        if (p < 0) return true;
        boolean lifted = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(index) == pointer;
        if (action == MotionEvent.ACTION_MOVE || lifted) {
            // The first samples since the last draw post it: it runs once every event queued behind this one is in.
            if (batch.isEmpty()) post(drawBatch);
            for (int h = 0; h < event.getHistorySize(); h++) sample(event, p, h);
            sample(event, p, -1);
            if (lifted) { drawBatch(); if (!liftQuick()) finishStroke(); }
        }
        return true;
    }
    /** Draws the gathered samples: the stroke takes them all, then the page is rendered and presented once. */
    private void drawBatch() {
        removeCallbacks(drawBatch);
        if (stroke == null || batch.isEmpty()) return;
        long start = System.nanoTime(), age = Math.max(0, SystemClock.uptimeMillis() - batch.oldest());
        batch.feed(stroke);
        stroke.present();
        renderDirty();
        session.input(System.nanoTime() - start, age);
    }
    /** Every hover restarts the wait: the pad refreshes once the pen has been away a while. */
    @Override public boolean onHoverEvent(MotionEvent event) {
        if (isPen(event, 0) && stroke == null) { removeCallbacks(refresh); postDelayed(refresh, DrawingPad.PEN_AWAY_MS); }
        return true;
    }
    private static boolean isPen(MotionEvent event, int index) {
        int type = event.getToolType(index);
        return type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER;
    }
    private void sample(MotionEvent event, int index, int history) {
        pen.read(event, null, index, history, landscapeTilt);
        held.sampled(pen.x, pen.y, pen.pressure, pen.tiltX, pen.tiltY, pen.time);
        batch.add(pen.x, pen.y, pen.pressure, pen.tiltX, pen.tiltY, pen.time);
    }
    /** A resting pen's last sample, sent again to a stroke that builds while held ({@link HeldPen}). */
    private final HeldPen held = new HeldPen();
    private final Runnable heldTick = this::heldTick;
    private void heldTick() {
        if (stroke == null || pointer == -1 || !stroke.buildsWhileHeld()) { held.clear(); return; }
        long now = SystemClock.uptimeMillis();
        if (held.due(stroke, now)) { drawBatch(); held.resend(stroke, now); stroke.present(); renderDirty(); }
        postDelayed(heldTick, HeldPen.TICK_MS);
    }
    /** The pen lifted from a stroke whose work after lift runs off this thread ({@link LiftingStroke}): it runs ({@link QuickLift}), then {@link #finishStroke}. False for any other stroke. */
    private boolean liftQuick() {
        if (!quickLift.start(stroke)) return false;
        pointer = -1;
        getParent().requestDisallowInterceptTouchEvent(false);
        return true;
    }
    /** Ends a stroke in progress, laying what it lays on lift. */
    void finishStroke() {
        if (stroke == null) { batch.clear(); return; }
        drawBatch();
        quickLift.stop();
        DrawingStroke ended = stroke;
        stroke.finish();
        stroke = null; pointer = -1;
        // Cut Stamp's lasso's ants, or a Spiro's ring, go; the ink inside a lasso is the nib.
        ants.end();
        if (ended instanceof MirrorShow.Place) {
            java.util.function.Consumer<StampNib> took = cut;
            cut = null;
            MirrorShow.Place lasso = (MirrorShow.Place) ended;
            if (took != null) took.accept(StampNib.cut(page::compositeTone, page.paperTone(), lasso.xs(), lasso.ys(), lasso.count(), page.width, page.height));
        }
        getParent().requestDisallowInterceptTouchEvent(false);
        // A Spiro's ring's fast session ends with the stroke's, or as one.
        if (ringFast != null) {
            if (fast == null) fast = ringFast; else ringFast.close();
            fastShown.union(ringShown); ringFast = null; ringShown.setEmpty();
        }
        // The fast session ends: what it showed and the stroke's last change go to the panel through the gray session (closeFast, flush).
        closeFast();
        renderDirty();
        flush();
        removeCallbacks(refresh); postDelayed(refresh, DrawingPad.PEN_AWAY_MS);
        drying.start();
    }

    // Rendering and presenting

    private void renderAll() { if (display != null) render(new Rect(0, 0, display.getWidth(), display.getHeight())); }
    private void renderDirty() {
        // The lasso's ants where they changed (drawn, or gone).
        ants.page(page.width, page.height);
        for (int[] a : ants.take(null, null)) render(new Rect(a[0] - 2, a[1] - 2, a[2] + 2, a[3] + 2));
        int[] b = page.dirty();
        if (b == null) return;
        page.clearDirty();
        render(new Rect(b[0] - 2, b[1] - 2, b[2] + 2, b[3] + 2));
    }
    /** Renders {@code area} of the page in its dots and presents it. */
    private void render(Rect area) {
        if (display == null || !area.intersect(0, 0, display.getWidth(), display.getHeight())) return;
        ViewportBitmap.compose(page, display, true, area);
        // Masking fluid shows hatched, as on the drawing screen.
        DrawingPad.drawMask(page, display, area);
        antPixels = DrawingPad.drawShow(ants, page, display, area, true, antPixels);
        pending.union(area);
        flush();
    }
    private void flush() {
        removeCallbacks(retry);
        if (pending.isEmpty()) return;
        unrefreshed = true;
        if (direct == null) { invalidate(); pending.setEmpty(); return; }
        // A stroke's presents spaced by their cost, as the drawing screen's.
        long wait = stroking() ? pace.wait(true, SystemClock.uptimeMillis()) : 0;
        if (wait > 0) { postDelayed(retry, wait); return; }
        long presentStart = System.nanoTime();
        int result;
        if (fast != null) {
            try { result = fast.present(display, pending); if (result >= 0) fastShown.union(pending); }
            catch (RuntimeException error) {
                // Only the preview loses its speed: the gray session shows the rest.
                Log.w(ProbeActivity.TAG, "Brush Lab's fast stroke display failed", error);
                closeFast(); flush(); return;
            }
        } else if (ending != null) {
            // The fast session's end: every pixel it changed is driven again, where the final keeps what it showed too.
            result = direct.endFast(ending, display, pending);
            if (result >= 0 || retries + 1 >= 120) { ending.close(); ending = null; }
        } else result = direct.present(display, pending);
        pace.presented(System.nanoTime() - presentStart, SystemClock.uptimeMillis());
        if (result < 0) {
            // A busy display: try again shortly.
            if (++retries < 120) { postDelayed(retry, 8); return; }
        }
        retries = 0;
        pending.setEmpty();
    }
    /** A pen stroke goes to the panel through its fastest black-and-white update while the pen is down ({@link StrokePanel}). */
    private void beginFast() {
        if (direct == null || fast != null) return;
        flush();
        fast = StrokePanel.open(this, display, new Matrix());
    }
    /**
     * A Spiro's ring at once through the fast update, as the lasso is, in a session of its own the stroke's gray
     * updates leave be; without one it goes with them.
     */
    private void showRing() {
        if (direct == null || fast != null) return;
        flush();
        ringFast = StrokePanel.open(this, display, new Matrix());
        presentRing();
    }
    /** What changed, through the ring's fast session, while it has one and the stroke none ({@link #ringFast}). */
    private void presentRing() {
        if (ringFast == null || fast != null) return;
        fast = ringFast;
        renderDirty();
        // A failed present closed it: the gray session shows the ring.
        if (fast == null) { ringFast = null; return; }
        ringShown.union(fastShown); fastShown.setEmpty(); fast = null;
    }
    /** Stops the fast preview; what it showed goes with the next present through the gray session, which ends it ({@link DirectEink#endFast}). */
    private void closeFast() {
        if (fast == null) return;
        if (fastShown.isEmpty()) { fast.close(); fast = null; return; }
        if (ending != null) ending.close();
        ending = fast; fast = null;
        pending.union(fastShown); fastShown.setEmpty();
    }

    /** Starts direct presentation and blocks the firmware's own pen ink, once the pad has its window and size. */
    void connect() {
        // A Drying wash still settling ticks again; disconnect stopped it.
        drying.start();
        if (direct != null || covered || display == null || !hasWindowFocus() || isLayoutRequested() || DirectEink.androidDrawing()) return;
        try {
            if (!input.prepareDocumentCanvas()) throw new IllegalStateException(input.status);
            direct = DirectEink.forView(this, display, new Matrix(), 0, 7);
            refreshSoon();
            if (opened) {
                opened = false;
                Ui.cleanWindow(getRootView(), "screen opened", () -> { if (direct != null) input.partialGrey(); });
            }
        } catch (RuntimeException | LinkageError error) {
            Log.w(ProbeActivity.TAG, "Brush Lab's test area uses Android drawing", error);
        }
    }
    /**
     * The screen opened or came back over another (the drawing screen's grey page and column): the next connect gives the
     * whole window one clean update ({@link Ui#cleanWindow}), so nothing of it ghosts through.
     */
    private boolean opened;
    void opened() { opened = true; }
    /** Ends a stroke and gives the panel and the pen back, e.g. while a dialog or another screen is up. */
    void disconnect() {
        finishStroke();
        drying.stop();
        removeCallbacks(retry);
        closeFast();
        if (ringFast != null) { ringFast.close(); ringFast = null; ringShown.setEmpty(); }
        if (ending != null) { ending.close(); ending = null; }
        if (direct != null) { direct.close(); direct = null; }
        input.disable();
        // Whatever lies over the pad meanwhile (another screen, a popup) may stay in the panel's shared plane, which a
        // session only overwrites where its own pixels change: the next connect shows the whole pad again, forced.
        unrefreshed = true; removeCallbacks(refresh);
    }
    void close() {
        disconnect();
        removeCallbacks(refresh);
        input.close();
        if (display != null) { display.recycle(); display = null; }
    }
    private void refreshSoon() { removeCallbacks(refresh); postDelayed(refresh, DrawingPad.CLOSED_MS); }
    /** One full, clean refresh of the pad, if it changed since the last; never under the pen. */
    private void refresh() {
        if (!unrefreshed || stroke != null || display == null) return;
        // Fast display: no pass at the pause, as the drawing screen's (the forced grey pass drives nothing on the Manta).
        if (direct == null) invalidate();
        unrefreshed = false;
    }
}
