package io.github.mpdairy.monopaint;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * The black box a control shows while pressed. It is submitted straight to the e-ink
 * panel on pen-down and released shortly after the click, so presses feel immediate.
 */
final class PressOutline {
    /** The box sits this far inside the control, centered on its stroke. */
    static final int BOX_INSET_DP = 3;
    private static final int RELEASE_MS = 200;
    private final View owner;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable release = () -> set(false);
    /** Controls without a feedback session draw no press outline. */
    SelectionFeedback feedback;
    boolean pressed;
    /**
     * Inkwash: the press shown as the box inverted (ink, the icon paper, {@link ToolButton}), in the panel's binary update
     * (mode 5, about 145 ms) on the pen's down, and taken off by the click's own frame ({@link #clicked}), no update of its
     * own: a button whose answer is slow to show (a menu, a page turn), as MonoPaint's press outline and KOReader's
     * inverted button.
     */
    boolean inverted;
    /**
     * Inkwash: the press shown as the selection it asks for, a bar row's side bar ({@link ToolButton#bar}), in the
     * binary update on the down: a pick's first frame, as MonoPaint's press outline is its. It stays through the click,
     * whose mark takes it over ({@link ToolButton#mark}), so the pick's own update has only the old bar to send.
     */
    boolean bar;

    PressOutline(View owner, SelectionFeedback feedback) {
        this.owner = owner; this.feedback = feedback;
        paint.setColor(Color.BLACK); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Ui.dp(owner.getContext(), 3));
    }

    void set(boolean value) {
        owner.removeCallbacks(release);
        if (feedback == null || pressed == value) return;
        Runnable change = () -> pressed = value;
        if (bar) feedback.updateBinary(owner, ((ToolButton) owner).markerArea(), change);
        else if (inverted && value) feedback.updateBinary(owner, new android.graphics.Rect(0, 0, owner.getWidth(), owner.getHeight()), change);
        else feedback.update(owner, change);
    }
    /**
     * Inkwash: an {@link #inverted} press let go as its click runs: drawn unpressed at Android's next frame, the one the
     * click's answer asks for, and the panel drives the box back to paper with it (it drives what differs from what it
     * holds).
     */
    void clicked() {
        if (!inverted || !pressed) return;
        // The Nomad's fast plane keeps its pixels through Android's frames: let go there in an update, as upstream's.
        if (DirectEink.fastBinaryControls()) { set(false); return; }
        owner.removeCallbacks(release); pressed = false; owner.invalidate();
    }
    void releaseLater() {
        owner.removeCallbacks(release);
        if (feedback != null && owner.isAttachedToWindow()) owner.postDelayed(release, RELEASE_MS);
    }
    /** Call before the control's own touch handling. {@code accepting} is false for disabled or blocked controls. */
    void onTouch(MotionEvent event, boolean accepting) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) { if (accepting) set(true); }
        else if (action == MotionEvent.ACTION_CANCEL || (action == MotionEvent.ACTION_MOVE && (event.getX() < 0
                || event.getY() < 0 || event.getX() >= owner.getWidth() || event.getY() >= owner.getHeight()))) set(false);
    }
    void draw(Canvas canvas) { if (pressed) drawBox(canvas); }
    void drawBox(Canvas canvas) { box(canvas, paint); }
    /** The box filled black: a selection shown inverted. */
    void fillBox(Canvas canvas) {
        if (fill == null) { fill = new Paint(Paint.ANTI_ALIAS_FLAG); fill.setColor(Color.BLACK); }
        box(canvas, fill);
    }
    private Paint fill;
    private void box(Canvas canvas, Paint paint) {
        int inset = Ui.dp(owner.getContext(), BOX_INSET_DP), radius = Ui.dp(owner.getContext(), 4);
        canvas.drawRoundRect(inset, inset, owner.getWidth()-inset, owner.getHeight()-inset, radius, radius, paint);
    }
    void detached() { owner.removeCallbacks(release); pressed = false; }
}
