package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

/**
 * The side column's size slider (the owner, 2026-10-24: "change the four size options to a quick size slider. I find
 * myself wanting to fine tune the size often"), the picker box's upper-right triangle, over the grey strip's ({@link
 * SwatchBar}, the owner's drawing, 2026-10-27; the right cell beside the strip that day, the left from the owner's
 * sketch, 2026-10-24, "DO IT THIS WAY", until then), the view the whole box, standing up: broad at the top to fine at the foot (the owner,
 * 2026-10-24: "flip the triangle for the size so it goes down") on {@link ToolSwatches#position}'s log scale, so fine
 * sizes get as much room as broad ones. Its track the owner's triangle, a 1.5 dp ink outline as Iconoir's icons since
 * 2026-10-29 (filled ink until then: "b 164 looks good"), the square's width at the top to a point at the foot, its
 * outline closing to a thin sliver there that reaches the box's corner ("they need to be thin to demonstrate the finer
 * point sizes"), its straight side on the right by the tab (mirrored from the drawing; the owner, 2026-10-24: "size picker is still
 * blinking. make it an outline shape instead?", then "triangle shape should be right triangle"; a grey-600 right
 * triangle, then an outline pointing down from the middle, then a ruled outline until the owner drew it, 2026-10-26),
 * which never draws again; the size in use an ink tab right of it
 * ({@link DrawLayout#sliderMarkRoom}), on its cell's outer side. The
 * whole cell takes the pen: down or a drag moves the tab through the fast display ({@link StripMarker}), the tab's strip
 * alone, as the grey strip's tab moves (its binary update, mode 5 and a redrive after, blinked: 2026-10-27); the lift
 * sets the size, so a tap jumps there, and the readout over it says it ({@link SwatchReadout}).
 * Brush Lab's slider is {@link BandSlider}. Accessibility: said "Size: 1.4 mm"; a screen reader's scroll steps it a
 * twentieth.
 */
@SuppressLint("ViewConstructor")
final class SizeSlider extends View {
    interface Listener {
        /** The pen landed: false when nothing may change now (the tab stays). */
        boolean ready();
        /** The pen lifted at {@code t} (0 finest to 1 broadest), or a step: it is set. */
        void set(double t);
    }
    /** No anti-aliasing: the tab's strip and the outline stay ink and paper. */
    private final Paint paint = new Paint();
    /** The owner's triangle at the track's size, ink and clear ({@link Theme#shape}); null until drawn at a size. */
    private Bitmap wedge;
    private final Listener listener;
    private final StripMarker pen;
    /** The size in use, as a position (NaN: none); the one the tab marks, the pen's while it is down. */
    private double inUse = Double.NaN, marked = Double.NaN;
    /** Whether this gesture began while the app could take it. */
    private boolean taking;

    SizeSlider(Context context, SelectionFeedback feedback, Listener listener) {
        super(context);
        this.listener = listener;
        // The grey strip's marker path (mode 7 moves, no clean after): the binary one (mode 5, then a two-pass redrive
        // 250 ms after the lift) blinked and left the tab half drawn (the owner, 2026-10-27: "weird ghosting glitch").
        pen = new StripMarker(this, feedback, this::markArea);
        setFocusable(true);
    }

    /** Marks position {@code t} (NaN: none). Asks Android for no frame: the caller puts the change on the panel ({@link #markArea}). */
    void show(double t) { inUse = marked = t; }
    /**
     * The picker box's left, px: a square and half the gap left of the axis, the middle of the view, which is two cells
     * wide (the box's lower-left triangle the grey strip's, {@link GreyStrip#boxed}).
     */
    private int left() { return getWidth() / 2 - gap() - side(); }
    private int gap() { return Ui.dp(getContext(), DrawLayout.FACE_GAP / 2f); }
    /** A face's square, px: the strip's ({@link GreyStrip#square}) in a cell half the view, leaving the tab's room. */
    private int side() { return Math.max(1, Math.min(Math.min(getWidth() / 2 - Ui.dp(getContext(), DrawLayout.sliderMarkRoom()), Ui.dp(getContext(), DrawLayout.FACE_SQUARE)), getHeight() / DrawLayout.FACE_ROWS - Ui.dp(getContext(), DrawLayout.FACE_GAP))); }
    /** The box's right, px: a square and half the gap right of the axis. */
    private int right() { return getWidth() / 2 + gap() + side(); }
    /** What a drag puts on the panel: the tab's strip, right of the box (left when {@link #mirrored}), the cell's height. */
    Rect markArea() { return mirrored ? new Rect(0, 0, getWidth() - right(), getHeight()) : new Rect(right(), 0, getWidth(), getHeight()); }
    /** Drawn mirrored, the tab left of the box and the grey strip's triangle lower right: the column on the right (left-handed). */
    boolean mirrored;
    /** Whether a pen landing at ({@code x}, {@code y}) is the grey strip's, the box's other triangle, or nearer it ({@link DrawLayout#boxShade}). */
    boolean shade(float x, float y) {
        return DrawLayout.boxShade(mirrored ? getWidth() - x : x, y, left(), dp(DrawLayout.sliderY(1, tall())), right(), dp(DrawLayout.sliderY(0, tall())));
    }
    private float tall() { return getHeight() / getResources().getDisplayMetrics().density; }
    private double at(float y) { return DrawLayout.sliderAt(y / getResources().getDisplayMetrics().density, tall()); }
    private void follow(double t) { if (Double.compare(t, marked) != 0) pen.update(() -> marked = t); }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (Double.isNaN(inUse)) return true;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) taking = listener.ready();
        if (!taking) return true;
        if (pen.touch(event)) follow(at(pen.y));
        if (pen.ended) {
            taking = false;
            if (pen.lifted) listener.set(at(pen.y));
            // As the strip: a lift that changed nothing (the eraser's least size) puts the tab back where the size is.
            follow(inUse);
        }
        return true;
    }
    @Override public boolean performAccessibilityAction(int action, android.os.Bundle arguments) {
        boolean up = action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
                down = action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
        if ((up || down) && !Double.isNaN(inUse) && listener.ready()) { listener.set(StrokeMath.clamp(inUse + (up ? .05 : -.05))); return true; }
        return super.performAccessibilityAction(action, arguments);
    }
    @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (Double.isNaN(inUse)) return;
        info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }
    @Override protected void onDetachedFromWindow() { pen.stop(); super.onDetachedFromWindow(); }
    @Override public void onWindowFocusChanged(boolean focused) { if (!focused) pen.stop(); super.onWindowFocusChanged(focused); }

    @Override protected void onDraw(Canvas canvas) {
        // No paper: the grey strip's triangle is under it, the column's paper under both.
        if (Double.isNaN(marked)) return;
        if (mirrored) canvas.scale(-1, 1, getWidth() / 2f, 0);
        float top = dp(DrawLayout.sliderY(1, tall())), foot = dp(DrawLayout.sliderY(0, tall()));
        int right = right(), left = left();
        // The track: the picker box's upper-right triangle, outlined (2026-10-29; the owner's drawing filled from
        // 2026-10-27), broad at the top to a point at the foot, so which end is which needs no words; its straight side down the box's right, by the tab,
        // clear of the tab's strip, so the tab is never on it.
        int w = right - left, h = Math.round(foot - top);
        if (wedge == null || wedge.getWidth() != w || wedge.getHeight() != h) {
            if (wedge != null) wedge.recycle();
            wedge = Theme.shape(getContext(), R.drawable.shape_box_size, w, h);
        }
        canvas.drawBitmap(wedge, left, Math.round(top), null);
        // The size in use: the grey strip's tab, right of the box.
        paint.setColor(Theme.INK);
        float y = dp(DrawLayout.sliderY(marked, tall())), half = dp(DrawLayout.STRIP_MARK) / 2f, x = right + dp(DrawLayout.FACE_MARK_GAP);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(x, Math.max(0, y - half), x + dp(DrawLayout.FACE_MARK), Math.min(getHeight(), y + half), paint);
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
