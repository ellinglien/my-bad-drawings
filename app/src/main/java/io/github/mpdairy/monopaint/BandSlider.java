package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Brush Lab's slider, as the mockup's: a 2 dp ink track, a square 12 × 16 dp ink thumb (grey-400 when it can't
 * move), and behind it the style's range as a band hatched in grey-600. Dragging past the band is allowed. 200
 * steps, as the mockup's.
 */
@SuppressLint("ViewConstructor")
final class BandSlider extends View {
    interface Listener { void started(); void moved(double value); void stopped(); default void reset() {} }
    /** Two taps this close (ms) on the same {@link #id} are a double tap: slow enough for e-ink's redraw between them. */
    static final long DOUBLE_TAP_MS = 500;
    /** The latest tap's setting, time and place: static, since the lab redraws its rows (new sliders) between the two taps. */
    private static Object lastId; private static long lastAt; private static float lastX;
    static final int STEPS = 200;
    final double min, max;
    double value;
    /** The style's range, hatched behind the track; null for none (Wild). */
    double[] band;
    Listener listener;
    /** What this slider sets; a double tap on one with an id resets it ({@link Listener#reset}). Null for none. */
    Object id;
    private final Paint paint = new Paint();
    private boolean dragging, doubled, tap;
    private float downX;

    BandSlider(Context context, double min, double max, double value, double[] band) {
        super(context);
        this.min = min; this.max = max; this.value = value; this.band = band;
        setMinimumHeight(dp(20));
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthSpec), resolveSize(dp(20), heightSpec));
    }
    @Override protected void onDraw(Canvas canvas) {
        float cy = getHeight() / 2f;
        if (band != null) {
            // A zero-width band (a setting the style leaves off) still shows as a short mark.
            float a = x(band[0]), width = Math.max(.025f * (right() - left()), x(band[1]) - a);
            float l = a - dp(6), r = a + width + dp(6), t = cy - dp(8), b = cy + dp(8), h = b - t;
            canvas.save();
            canvas.clipRect(l, t, r, b);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(Theme.BASE)); paint.setColor(Theme.GREY_600);
            for (float x = l - h; x < r; x += dp(5)) canvas.drawLine(x, b, x + h, t, paint);
            canvas.restore();
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Theme.INK);
        canvas.drawRect(left(), cy - dp(Theme.BASE) / 2f, right(), cy + dp(Theme.BASE) / 2f, paint);
        paint.setColor(isEnabled() ? Theme.INK : Theme.GREY_400);
        float thumb = x(Math.max(min, Math.min(max, value)));
        canvas.drawRect(thumb - dp(6), cy - dp(8), thumb + dp(6), cy + dp(8), paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                downX = event.getX(); tap = true;
                if (id != null && id == lastId && event.getEventTime() - lastAt <= DOUBLE_TAP_MS && Math.abs(downX - lastX) <= 3 * ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                    lastId = null; doubled = true;
                    if (listener != null) listener.reset();
                    return true;
                }
                dragging = true;
                if (listener != null) listener.started();
                move(event.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) { if (Math.abs(event.getX() - downX) > ViewConfiguration.get(getContext()).getScaledTouchSlop()) tap = false; move(event.getX()); }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (doubled) { doubled = false; return true; }
                if (!dragging) return true;
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    move(event.getX());
                    if (tap) { lastId = id; lastAt = event.getEventTime(); lastX = downX; }
                }
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                if (listener != null) listener.stopped();
                return true;
            default:
                return true;
        }
    }
    private void move(float x) {
        double t = Math.max(0, Math.min(1, (x - left()) / (right() - left())));
        double v = min + Math.round(t * STEPS) * (max - min) / STEPS;
        if (v == value) return;
        value = v;
        invalidate();
        if (listener != null) listener.moved(v);
    }
    private float left() { return dp(6); }
    private float right() { return getWidth() - dp(6); }
    private float x(double v) { return left() + (float)((v - min) / (max - min)) * (right() - left()); }
    private int dp(float value) { return Ui.dp(getContext(), value); }
}
