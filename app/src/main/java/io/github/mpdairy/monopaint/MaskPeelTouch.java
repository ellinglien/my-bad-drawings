package io.github.mpdairy.monopaint;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Holding the masking fluid button pops out a Peel button below it. Sliding the pen onto
 * it and lifting peels all the masking fluid off; lifting elsewhere leaves it open to tap.
 */
final class MaskPeelTouch implements View.OnTouchListener {
    private final PaintActivity app;
    private final ToolButton button;
    private final Runnable offer = this::offer;
    /** The hold opened the Peel button, so this gesture no longer toggles masking fluid. */
    private boolean held;
    private PageActionButton peel;

    MaskPeelTouch(PaintActivity app, ToolButton button) { this.app = app; this.button = button; }

    @Override public boolean onTouch(View view, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            held = false; peel = null; button.removeCallbacks(offer);
            if (!app.busy()) button.postDelayed(offer, ViewConfiguration.getLongPressTimeout());
            return false;
        }
        if (!held) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) button.removeCallbacks(offer);
            return false;
        }
        boolean over = peel != null && peel.isEnabled() && peel.isAttachedToWindow() && !Ui.outside(peel, event);
        if (action == MotionEvent.ACTION_MOVE && peel != null && peel.press.pressed != over) peel.press.set(over);
        if (action == MotionEvent.ACTION_UP && over) { peel.press.set(false); peel.performClick(); }
        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) { if (peel != null) peel.press.set(false); }
        return true;
    }
    private void offer() {
        if (app.busy() || !button.isPressed()) return;
        held = true;
        // End the button's own press, so lifting the pen does not also toggle masking fluid.
        MotionEvent cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        button.onTouchEvent(cancel); cancel.recycle();
        View shown = app.showPeel();
        peel = shown instanceof PageActionButton ? (PageActionButton)shown : null;
    }
}
