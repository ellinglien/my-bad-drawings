package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

/**
 * The grey bar (the owner, 2026-10-19: "a gradient displayed like in monopaint"): MonoPaint's color bar ({@link
 * ShadePicker}) for a grey swatch, its strip and pen shared ({@link ShadeStrip}, {@link StripMarker}). Black at the
 * left to paper white at the right in the panel's dots, a solid black and a solid white end, so both are always a tap
 * away; a fine ink frame; a black marker under the grey in use. A tap or a drag picks one of the {@link
 * ShadeStrip#LEVELS} greys: while the pen moves only the marker is drawn again, through the fast display, and {@link
 * Listener#moved} hears each new grey; where it lifts goes to {@link Listener#lifted}. Greys are darkness, 0 paper to 1
 * black ({@link ToolSwatches#darkness}), as the grey swatches keep them.
 */
@SuppressLint("ViewConstructor")
final class GreyBar extends View {
    interface Listener {
        /** The pen moved onto a new grey. */
        void moved(double darkness);
        /** The pen lifted on a grey (not on a cancel). */
        void lifted(double darkness);
    }
    private final Listener listener;
    private final StripMarker pen;
    private final Paint ink = new Paint();
    private Bitmap strip;
    private int endpoint;
    private double darkness;

    GreyBar(Context context, SelectionFeedback feedback, Listener listener) {
        super(context);
        this.listener = listener;
        pen = new StripMarker(this, feedback, this::markerArea);
        setClickable(true);
        setContentDescription("Grey, black to white");
    }
    /** Shows {@code darkness}'s marker, an ordinary redraw (a grey chosen elsewhere). */
    void show(double darkness) { if (this.darkness != darkness) { this.darkness = darkness; invalidate(); } }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        if (strip != null) strip.recycle();
        int width = w - 2 * dp(ShadeStrip.INSET), height = h - dp(ShadeStrip.INSET + ShadeStrip.GUTTER);
        if (width <= 0 || height <= 0) { strip = null; return; }
        endpoint = ShadeStrip.endpoint(width, dp(ShadeStrip.END));
        strip = Bitmap.createBitmap(ShadeStrip.pixels(width, height, endpoint), width, height, Bitmap.Config.ARGB_8888);
    }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Theme.PAPER);
        if (strip == null) return;
        int inset = dp(ShadeStrip.INSET), line = Math.max(1, dp(Theme.FINE));
        canvas.drawBitmap(strip, inset, inset, null);
        ink.setColor(Theme.INK); ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(line);
        canvas.drawRect(inset - line / 2f, inset - line / 2f, inset + strip.getWidth() + line / 2f, inset + strip.getHeight() + line / 2f, ink);
        float x = inset + ShadeStrip.markerAt(ToolSwatches.tone(darkness), strip.getWidth(), endpoint);
        int bottom = getHeight() - dp(ShadeStrip.MARKER_GAP), half = dp(ShadeStrip.MARKER) / 2;
        ink.setStyle(Paint.Style.FILL);
        canvas.drawRect(x - half, bottom - dp(ShadeStrip.MARKER), x + half, bottom, ink);
    }
    /** The marker's gutter under the strip: all a move draws again. */
    private Rect markerArea() {
        int height = Math.min(getHeight(), dp(ShadeStrip.MARKER + ShadeStrip.MARKER_GAP + 1));
        return new Rect(0, getHeight() - height, getWidth(), getHeight());
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (strip == null) return true;
        if (pen.touch(event)) {
            double d = ToolSwatches.darkness(ShadeStrip.toneAt(pen.x - dp(ShadeStrip.INSET), strip.getWidth(), endpoint));
            if (d != darkness) {
                pen.update(() -> darkness = d);
                listener.moved(d);
            }
        }
        if (pen.ended && pen.lifted) { listener.lifted(darkness); performClick(); }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override protected void onDetachedFromWindow() {
        pen.stop();
        super.onDetachedFromWindow();
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        if (!focused) pen.stop();
        super.onWindowFocusChanged(focused);
    }
    private int dp(float value) { return Ui.dp(getContext(), value); }
}
