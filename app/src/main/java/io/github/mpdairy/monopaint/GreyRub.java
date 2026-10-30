package io.github.mpdairy.monopaint;

import android.view.MotionEvent;
import android.view.View;
import java.util.function.DoubleConsumer;
import java.util.function.Predicate;

/**
 * A rub on a grey (the oil paint spec), shared by a grey face ({@link SwatchFace}) and the side bar's grey strip
 * ({@link GreyStrip}): the pen moving over it past {@link LoadedPaint#RUB_DP} dp is a rub, not a tap; at its lift the
 * rub's travel goes to its handler instead of the tap's, and the tap and long press it would have been are called off.
 */
final class GreyRub {
    private final View view;
    /** The view's own touch handling (its tap and long press). */
    private final Predicate<MotionEvent> own;
    /** The rub's handler, or null; the gesture's travel so far, its last place, and whether it is a rub yet. */
    DoubleConsumer handler;
    private float travel, lastX, lastY;
    private boolean rubbing;

    GreyRub(View view, Predicate<MotionEvent> own) { this.view = view; this.own = own; }

    boolean touch(MotionEvent event) {
        if (handler == null) return own.test(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                travel = 0; rubbing = false; lastX = event.getX(); lastY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getHistorySize(); i++) moved(event.getHistoricalX(i), event.getHistoricalY(i));
                moved(event.getX(), event.getY());
                if (!rubbing && LoadedPaint.rub(travel, density())) {
                    // A rub now: the tap and the long press it might have been are called off.
                    rubbing = true;
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    own.test(cancel);
                    cancel.recycle();
                }
                if (rubbing) return true;
                break;
            case MotionEvent.ACTION_UP:
                if (rubbing) { rubbing = false; handler.accept(travel); return true; }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (rubbing) { rubbing = false; return true; }
                break;
            default:
                break;
        }
        return own.test(event);
    }
    /** Counts the step from the last counted place only past the tremor, so a held pen's jitter never adds up to a rub (it called the long press off). */
    private void moved(float x, float y) {
        float step = (float) Math.hypot(x - lastX, y - lastY);
        if (LoadedPaint.travels(step, density())) { travel += step; lastX = x; lastY = y; }
    }
    private float density() { return view.getResources().getDisplayMetrics().density; }
}
