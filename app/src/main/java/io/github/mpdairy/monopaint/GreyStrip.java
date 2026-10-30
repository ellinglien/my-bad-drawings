package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import java.util.function.DoubleConsumer;

/**
 * The side bar's grey strip ({@link SwatchBar}, the owner, 2026-10-21; the picker box's lower-left triangle since
 * 2026-10-27, {@link #boxed}, the history below the standing strip's, Opacity's still): {@link ShadeStrip}'s strip standing up, white
 * at the top to black at the foot in the panel's dots (the owner, 2026-10-24: "same with the shade picker", black at
 * the top until then), in the left cell beside the size slider (the right one from 2026-10-24 to 27), as long as the four
 * sizes' squares were beside it, exactly a square wide, against the axis, with an ink tab left of the strip, on its
 * cell's outer side (right of it until 2026-10-27), at the height of
 * the grey the tool draws at (the owner, 2026-10-21; its mark across the strip gone, 2026-10-22; its frame gone too,
 * 2026-10-23), its outline the owner's drawn bar (2026-10-26: "you'll apply a gradient to it after"). A tap or a drag picks the strip's grey where the pen lifts, as MonoPaint's color bar does, setting no
 * swatch (the owner, 2026-10-22: "the color picker is too hard to use.. let's keep the gradient picker"). While the pen
 * is down the square follows it through the fast display ({@link StripMarker}, the owner, 2026-10-23: "selecting a shade
 * in the gradient picker is sluggish"); the whole view takes the pen, the room either side of the strip too. A long
 * press does only what its owner gives it (on the drawing screen, Opacity since 2026-10-27; nothing from 2026-10-24); a
 * rub picks too, and then
 * loads the brush ({@link GreyRub}, the oil paint spec).
 */
@SuppressLint("ViewConstructor")
final class GreyStrip extends View {
    interface Listener {
        /** A tap: the grey {@code darkness} (0–1) there picked. */
        void tapped(double darkness);
    }
    private final Paint ink = new Paint();
    private Bitmap strip;
    private int endpoint;
    /** The grey in use, darkness 0–1, or NaN for none (the eraser, the smudge tool); the grey the square marks, the pen's while it is down. */
    private double inUse = Double.NaN, marked = Double.NaN;
    /** Whether this gesture became a long press: its lift picks nothing. */
    private boolean held;
    private final Listener listener;
    private final StripMarker pen;
    private final GreyRub rub = new GreyRub(this, super::onTouchEvent);

    /** {@code hold}: a long press's, or null for none. */
    GreyStrip(Context context, SelectionFeedback feedback, Listener listener, Runnable hold, DoubleConsumer rub) {
        super(context);
        this.listener = listener;
        pen = new StripMarker(this, feedback, this::squareArea);
        this.rub.handler = rub;
        if (hold != null) setOnLongClickListener(v -> { held = true; follow(inUse); hold.run(); return true; });
    }

    /**
     * Marks the grey in use, {@code darkness} 0–1 (NaN: none). Asks Android for no frame: the caller puts the change on
     * the panel ({@link SelectionFeedback}, over {@link #squareArea}), as MonoPaint's color bar does.
     */
    void show(double darkness) { inUse = marked = darkness; }
    /**
     * Drawn as paper and taking no touch (the eraser, the smudge tool), set as {@link #show} is, asking Android for no
     * frame: {@code setVisibility} invalidated the column, and the eraser's pick drew it all again through Android, a
     * second grey pass (2026-10-25, "Device timing").
     */
    boolean hidden;

    /** A row of faces, px: a {@link DrawLayout#FACE_ROWS}th of the view, as the slider's ({@link DrawLayout#faceSide}). */
    private int face() { return getHeight() / DrawLayout.FACE_ROWS; }
    /**
     * In the picker box ({@link SwatchBar}; the owner's drawing, 2026-10-27): the box's lower-left triangle, the box two
     * cells wide across the axis, the size slider over its other triangle; else the standing strip (Opacity's).
     */
    boolean boxed;
    /** The cell it would fill: the view, or the box's half. */
    private int cell() { return boxed ? getWidth() / 2 : getWidth(); }
    /** A size's square beside it, px, as {@link DrawLayout#faceSide} sizes it in a cell this wide. */
    private int square() { return Math.max(1, Math.min(Math.min(cell() - Ui.dp(getContext(), DrawLayout.FACE_GAP / 2f) - dp(DrawLayout.faceMarkRoom()), dp(DrawLayout.FACE_SQUARE)), face() - dp(DrawLayout.FACE_GAP))); }
    /** The strip's top: the first size's square's. */
    private int inset() { return dp(DrawLayout.PAIR_PAD); }
    /** The strip's length: from the first size's square's top to the last's foot; in the box, the slider's track's, the box less the gap. */
    private int length() { return boxed ? getHeight() - dp(DrawLayout.FACE_GAP) : (DrawLayout.FACE_ROWS - 1) * face() + square(); }
    /** The strip's left: its right half the gap from the axis, which is the cell's right (the box's left: the slider's square the other side). */
    private int left() { return cell() - gap() - square(); }
    private int gap() { return Ui.dp(getContext(), DrawLayout.FACE_GAP / 2f); }
    /** The strip's grey at the view's {@code y}, darkness 0–1: past its ends, the end's. White at the top, black at the foot (the owner, 2026-10-24). */
    private double at(float y) { return DrawLayout.shadeAt(y, inset(), strip.getWidth(), endpoint); }
    /** The square moves to {@code darkness}'s height, its column alone put on the panel through the fast display. */
    private void follow(double darkness) { if (Double.compare(darkness, marked) != 0) pen.update(() -> marked = darkness); }
    /** The tab's column, left of the strip (right when {@link #mirrored}): all the pen's moves draw again. */
    Rect squareArea() {
        int room = strip == null ? getWidth() : left();
        return mirrored ? new Rect(getWidth() - room, 0, getWidth(), getHeight()) : new Rect(0, 0, room, getHeight());
    }
    /** Drawn mirrored, the tab right of the strip: the Opacity strip beside a column on the right (left-handed). */
    boolean mirrored;

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (hidden) return false;
        if (strip == null || Double.isNaN(inUse)) { rub.touch(event); return true; }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) held = false;
        if (pen.touch(event) && !held) follow(at(pen.y));
        if (pen.ended) {
            // Picked where the pen lifts, before a rub's load; a cancel, a long press or a busy app leaves the grey in use.
            if (pen.lifted && !held) listener.tapped(at(pen.y));
            follow(inUse);
        }
        // The strip keeps the whole gesture: with no long press its own touch handling would refuse the down, and the
        // moves and the lift (the pick) would never come (2026-10-24, found by PickerBench).
        rub.touch(event);
        return true;
    }
    @Override protected void onDetachedFromWindow() {
        pen.stop();
        super.onDetachedFromWindow();
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        if (!focused) pen.stop();
        super.onWindowFocusChanged(focused);
    }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        if (strip != null) strip.recycle();
        int length = length(), width = stripWidth();
        if (length <= 0 || width <= 0) { strip = null; return; }
        endpoint = ShadeStrip.endpoint(length, dp(ShadeStrip.END));
        // Laid out along x as the grey bar's, then stood up when drawn; paper outside the owner's drawn bar, which stands
        // up, so its column x is the strip's row and its row y the strip's length - 1 - x.
        int[] pixels = ShadeStrip.pixels(length, width, endpoint);
        Bitmap shape = Theme.shape(getContext(), boxed ? R.drawable.shape_box_shade : R.drawable.shape_grey_strip, width, length);
        for (int x = 0; x < length; x++)
            for (int y = 0; y < width; y++) {
                // No ink outline over the greys, so each shows as it is (the owner, 2026-10-29: "remove the stroke outline
                // from the shade side so it's easier to see the right shade"); the size side keeps its outline.
                if (shape.getPixel(y, length - 1 - x) == 0) pixels[y * length + x] = Theme.PAPER;
            }
        shape.recycle();
        strip = Bitmap.createBitmap(pixels, length, width, Bitmap.Config.ARGB_8888);
    }
    @Override protected void onDraw(Canvas canvas) {
        // In the box, the slider is drawn over it and the column's paper under both.
        if (!boxed) canvas.drawColor(Theme.PAPER);
        if (strip == null || hidden) return;
        if (mirrored) canvas.scale(-1, 1, getWidth() / 2f, 0);
        int top = inset(), width = strip.getHeight(), left = left();
        canvas.save();
        // Laid out black to white along x: turned so white is at the top and black at the foot.
        canvas.translate(left, top + strip.getWidth());
        canvas.rotate(-90);
        canvas.drawBitmap(strip, 0, 0, null);
        canvas.restore();
        // No frame round the strip (the owner, 2026-10-23: "remove black line around gradient picker"): its solid black
        // end and the square beside it are enough.
        ink.setColor(Theme.INK); ink.setStyle(Paint.Style.FILL);
        if (Double.isNaN(marked)) return;
        // The grey in use: a tab in the room left of the strip, at its height, and no mark across the strip (the owner,
        // 2026-10-22: "no need to have a line in the gradient picker, just the dot on the side is fine").
        float y = top + strip.getWidth() - ShadeStrip.markerAt(ToolSwatches.tone(marked), strip.getWidth(), endpoint), half = dp(DrawLayout.STRIP_MARK) / 2f;
        float x = left - dp(DrawLayout.FACE_MARK_GAP) - dp(DrawLayout.FACE_MARK);
        canvas.drawRect(x, Math.max(0, y - half), x + dp(DrawLayout.FACE_MARK), Math.min(getHeight(), y + half), ink);
    }
    /** A square's width, its mark in the room left of it, on the cell's outer side; in the box, the box's width, both squares and the gap. */
    private int stripWidth() { return boxed ? 2 * (square() + gap()) : square(); }
    private int dp(float value) { return Ui.dp(getContext(), value); }
}
