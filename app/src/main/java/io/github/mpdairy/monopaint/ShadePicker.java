package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * The gray strip in the color bar, drawn with the panel's real dot patterns. Tap or drag
 * to choose a shade; a held gesture previews a pending gradient's second color.
 */
@SuppressLint("ViewConstructor")
final class ShadePicker extends View {
    private final PaintActivity app;
    private Bitmap strip;
    /** Width of the solid black and white end zones. */
    private int endpointWidth;
    private final Paint marker = new Paint();
    /** The pen on the strip and the marker's fast feedback, shared with Inkwash's grey bar. */
    private final StripMarker pen;
    /** Steps of a marker slide, each this long: the same however far, so a long slide moves faster. */
    private static final int SLIDE_STEPS = 4, SLIDE_STEP_MS = 45;
    /** Where the sliding marker shows, in the strip's coordinates, while the shade is still {@link #slideShade}; NaN when not sliding. */
    private float slideX = Float.NaN, slideFrom, slideTo;
    private int slideShade, slideStep;
    private final Runnable slide = this::slide;

    ShadePicker(PaintActivity app) {
        super(app); this.app = app; setFocusable(true); setClickable(true);
        pen = new StripMarker(this, app.selectionFeedback, this::markerArea);
        setContentDescription("Gray gradient. Tap or drag from black on the left to white on the right.");
    }
    private int dp(float value) { return app.dp(value); }

    /** Save settings immediately, but don't queue an Android marker frame for every tap. */
    void finishUpdate() { pen.finishUpdate(); }
    @Override protected void onDetachedFromWindow() {
        removeCallbacks(slide); slideX = Float.NaN;
        pen.stop();
        super.onDetachedFromWindow();
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        if (!focused) pen.stop();
        super.onWindowFocusChanged(focused);
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        if (strip != null) strip.recycle();
        int width = w - 2 * dp(ShadeStrip.INSET), height = h - dp(ShadeStrip.INSET + ShadeStrip.GUTTER);
        if (width <= 0 || height <= 0) { strip = null; return; }
        endpointWidth = ShadeStrip.endpoint(width, dp(ShadeStrip.END));
        strip = Bitmap.createBitmap(ShadeStrip.pixels(width, height, endpointWidth), width, height, Bitmap.Config.ARGB_8888);
    }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.WHITE);
        if (strip == null) return;
        int saved = canvas.save();
        // Black stays at the bottom; the black marker faces the canvas
        // whether the landscape palette is on the left or the right.
        if (app.landscape) canvas.scale(-1, app.colorMarkersOnTop() ? -1 : 1, getWidth()/2f, getHeight()/2f);
        int inset = dp(ShadeStrip.INSET);
        canvas.drawBitmap(strip, inset, inset, null);
        marker.setColor(Color.BLACK); marker.setStyle(Paint.Style.STROKE); marker.setStrokeWidth(1);
        canvas.drawRect(inset, inset, inset + strip.getWidth(), inset + strip.getHeight(), marker);
        if (app.shadeMarkerVisible()) drawMarker(canvas, inset);
        canvas.restoreToCount(saved);
    }
    private void drawMarker(Canvas canvas, int inset) {
        boolean sliding = !Float.isNaN(slideX) && app.paint.gray == slideShade;
        float x = inset + (sliding ? slideX : markerX(app.paint.gray));
        marker.setStyle(Paint.Style.FILL);
        int bottom = getHeight() - dp(ShadeStrip.MARKER_GAP), half = dp(ShadeStrip.MARKER) / 2;
        canvas.drawRect(x - half, bottom - dp(ShadeStrip.MARKER), x + half, bottom, marker);
    }
    /** Where the marker sits for {@code gray}, in the strip's coordinates. */
    private float markerX(int gray) { return ShadeStrip.markerAt(gray, strip.getWidth(), endpointWidth); }
    /**
     * Slides the marker from where it shows {@code fromGray} to the selected shade, quickly,
     * easing in to land. A new shade chosen meanwhile shows at once instead.
     */
    void slideMarker(int fromGray) {
        removeCallbacks(slide);
        if (strip == null) return;
        pen.begin();
        slideShade = app.paint.gray; slideStep = 0;
        slideFrom = slideX = markerX(fromGray); slideTo = markerX(slideShade);
        slide();
    }
    private void slide() {
        boolean done = ++slideStep >= SLIDE_STEPS || app.paint.gray != slideShade || Math.abs(slideTo - slideFrom) < dp(4);
        float t = slideStep / (float)SLIDE_STEPS, eased = 1 - (1 - t) * (1 - t);
        app.updateColorBar(() -> slideX = done ? Float.NaN : slideFrom + (slideTo - slideFrom) * eased);
        if (done) { finishUpdate(); pen.scheduleCleanup(); } else postDelayed(slide, SLIDE_STEP_MS);
    }
    /** Keep moving ink off the dotted ramp and capture only its small white gutter. */
    Rect markerArea() {
        int height = Math.min(getHeight(), dp(8));
        return app.colorMarkersOnTop()
                ? new Rect(0, 0, getWidth(), height)
                : new Rect(0, getHeight() - height, getWidth(), getHeight());
    }
    private void selectX(float x) {
        if (strip == null || !Float.isFinite(x)) return;
        if (app.landscape) x = getWidth()-x;
        app.selectShade(ShadeStrip.toneAt(x - dp(ShadeStrip.INSET), strip.getWidth(), endpointWidth));
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (app.busy()) return true;
        boolean chooses = pen.touch(event);
        app.paintRub.track(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            app.hideGradientHint();
            if (!app.pad.hasGradient()) app.pad.finishStroke();
        }
        if (chooses) selectX(pen.x);
        if (pen.ended) {
            app.endShadeGesture(pen.lifted);
            app.saveToolState();
            if (pen.lifted) performClick();
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            pen.begin();
            int direction = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
            app.selectShade(DotPattern.pickerTone(DotPattern.pickerPosition(app.paint.gray) + direction * 4));
            app.saveToolState(); return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            app.endShadeGesture(true); pen.scheduleCleanup(); return true;
        }
        return super.onKeyUp(keyCode, event);
    }
}
