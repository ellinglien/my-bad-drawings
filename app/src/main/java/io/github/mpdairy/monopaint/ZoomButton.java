package io.github.mpdairy.monopaint;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

/**
 * The page bar's Zoom button ({@link PageTurner}, the owner, 2026-10-21: out of the side bar), MonoPaint's ({@code Toolbar}'s zoom button, {@link ZoomButtonTouch}) in Inkwash's look:
 * Phosphor's padlock, closed while zoom and pan are locked (the default) and open while two fingers may pinch and pan
 * ({@link PinchPan}), beside the zoom's percentage. A tap locks or unlocks, and the choice is kept ({@code
 * PaintActivity.toggleNavigationLock}); locking or unlocking keeps the view as it is; a second quick tap fits the whole
 * page, keeping the lock as it was ({@link ZoomButtonTouch}). Both reach the panel through the fast feedback path ({@link SelectionFeedback}), the
 * percentage too while fingers pinch. {@link DrawLayout#zoomButton} wide. Its own class, so the bar's other changes stay apart from it.
 */
final class ZoomButton {
    final ToolButton button;
    private final PaintActivity app;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable locked, open;
    /** What the button shows: its percentage and its padlock. */
    private int percent = 100;
    private boolean shownLocked = true;

    ZoomButton(PaintActivity app) {
        this.app = app;
        button = app.iconButton("Zoom", R.drawable.ic_ph_lock, app::toggleNavigationLock);
        // The padlock and the percentage are the overlay's: no centred icon.
        button.iconOnly(null, null);
        button.setBackground(null);  // plain, as the bar's other buttons (the owner, 2026-10-21)
        button.overlay = (canvas, b) -> draw(canvas);
        button.setOnTouchListener(new ZoomButtonTouch(app, button));
        locked = app.getDrawable(R.drawable.ic_ph_lock); open = app.getDrawable(R.drawable.ic_ph_lock_open);
        text.setColor(Theme.INK); text.setTypeface(Theme.face(700));
        text.setTextSize(app.getResources().getDisplayMetrics().density * LabText.em(TypeStyle.CAPTION.sp));
        shownLocked = app.navigationLocked;
        describe();
    }

    /** The zoom changed ({@code DrawingPad.updateViewport}): the percentage follows, through the fast path while fingers pinch. */
    void refresh() {
        int now = app.pad == null ? 100 : app.pad.viewport.percent();
        if (now == percent) return;
        Runnable change = () -> { percent = now; describe(); };
        if (app.pad != null && app.pad.navigating && button.getWidth() > 0) app.selectionFeedback.update(button, change);
        else { change.run(); button.invalidate(); }
    }
    /** {@code change} locks or unlocks: the padlock follows, through the fast path. */
    void lockChanged(Runnable change) {
        app.selectionFeedback.update(button, () -> { change.run(); shownLocked = app.navigationLocked; describe(); });
    }
    private void describe() { button.setContentDescription(PinchPan.describe(percent, shownLocked)); }
    /** The padlock beside the percentage, the two centred together in the button. */
    private void draw(Canvas canvas) {
        int lock = app.dp(DrawLayout.ZOOM_LOCK), gap = app.dp(DrawLayout.ZOOM_GAP);
        String words = PinchPan.caption(percent);
        float left = (button.getWidth() - (lock + gap + text.measureText(words))) / 2f, middle = button.getHeight() / 2f;
        Drawable padlock = shownLocked ? locked : open;
        padlock.setBounds(Math.round(left), Math.round(middle - lock / 2f), Math.round(left + lock), Math.round(middle + lock / 2f));
        padlock.draw(canvas);
        canvas.drawText(words, left + lock + gap, middle - (text.ascent() + text.descent()) / 2, text);
    }
}
