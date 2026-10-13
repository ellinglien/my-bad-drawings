package io.github.mpdairy.monopaint;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

/**
 * A blow dryer standing in for a button's icon for a moment after a tap, blowing air inside
 * a box around it: the Watercolor mode droplet shows it when a tap dries the moving paint.
 */
final class BlowDryer implements ToolButton.Overlay {
    /** Frames of air, each this long. */
    private static final int FRAMES = 6, FRAME_MS = 140;
    /** Half the dryer's size at most, in dp; a narrow button takes a smaller one. */
    private static final int HALF = 12;

    private final ControlHost host;
    private final ToolButton button;
    private final Drawable dryer;
    private int frame;

    private BlowDryer(ControlHost host, ToolButton button) {
        this.host = host; this.button = button;
        dryer = button.getContext().getDrawable(R.drawable.ic_hair_dryer);
    }
    /** Shows the dryer blowing over {@code button}'s icon, then gives the icon back. */
    static void blow(ControlHost host, ToolButton button) {
        BlowDryer blower = new BlowDryer(host, button);
        host.selectionFeedback().update(button, () -> button.overlay = blower);
        button.postDelayed(blower::step, FRAME_MS);
    }
    /** Shows the next frame of air; past the last, clears it. */
    private void step() {
        // Something else took the button over meanwhile, like the droplet as paint moves again.
        if (!button.isAttachedToWindow() || button.overlay != this) return;
        boolean done = ++frame >= FRAMES;
        host.selectionFeedback().update(button, () -> button.overlay = done ? null : this);
        if (!done) button.postDelayed(this::step, FRAME_MS);
    }
    /** The dryer, stepped back to leave room for the air, and puffs leaving its nozzle, further out each frame. */
    @Override public void draw(Canvas canvas, ToolButton b) {
        // Cover the button's own icon; the selection dot draws over this.
        canvas.drawColor(Color.WHITE);
        b.press.drawBox(canvas);
        // In the turned landscape column a step back would move it along the column instead.
        int turn = b.headerIcon ? host.toolbarTurn() : 0;
        float half = Math.min(dp(HALF), (b.getWidth() - dp(10)) / 2f);
        float x = Math.max(half + dp(1), b.getWidth() / 2f - (turn == 0 ? dp(6) : 0)), y = b.getHeight() / 2f;
        canvas.save(); canvas.rotate(-turn, b.getWidth() / 2f, y);
        float unit = 2 * half / 24, left = x - half, top = y - half;
        dryer.setBounds(Math.round(left), Math.round(top), Math.round(x + half), Math.round(y + half));
        dryer.draw(canvas);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK); paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f)); paint.setStrokeCap(Paint.Cap.ROUND);
        // Its nozzle faces right, and its air lines sit at these heights of the 24-unit icon.
        float limit = b.getWidth() - dp(PressOutline.BOX_INSET_DP + 3);
        for (float row : new float[]{4.5f, 9, 13.5f}) for (int wave = 0; wave < 2; wave++) {
            float start = left + (24 + (frame * 2 + wave * 4) % 8) * unit;
            float end = Math.min(limit, start + 3 * unit);
            if (end > start) canvas.drawLine(start, top + row * unit, end, top + row * unit, paint);
        }
        canvas.restore();
    }
    private float dp(float value) { return value * button.getResources().getDisplayMetrics().density; }
}
