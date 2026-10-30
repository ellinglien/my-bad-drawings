package io.github.mpdairy.monopaint;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

/**
 * A blow dryer standing in for a button's icon for a moment after a tap, blowing air, with
 * no box around it: the Watercolor mode droplet shows it when a tap dries the moving paint.
 * MonoPaint's own (upstream v0.98.3, BlowDryer.java and res/drawable/ic_hair_dryer.xml, MIT, by mpdairy), its frames,
 * timing, shake, air and icon as they are there; only its drawing is shared with Inkwash's droplet ({@link WetIndicator}).
 */
final class BlowDryer implements ToolButton.Overlay {
    /** Frames of air, each this long (Inkwash's wet indicator runs the same frames, {@link #draw(Canvas, Drawable, float, float, int, float)}). */
    static final int FRAMES = 6, FRAME_MS = 140;
    /** Half the dryer's size at most, in dp; a narrow button takes a smaller one. */
    private static final int HALF = 12;
    /** Where the dryer sits each frame as it shakes, in dp up or down. */
    private static final int[] SHAKE = {0, -1, 0, 1};

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
        boolean done = frame + 1 >= FRAMES;
        // Step inside the change, so the control's pixels before it still show the last frame.
        host.selectionFeedback().update(button, () -> { frame++; button.overlay = done ? null : this; });
        if (!done) button.postDelayed(this::step, FRAME_MS);
    }
    /**
     * The dryer, stepped back to leave room for its air, shaking a little as it runs, and puffs
     * leaving its nozzle, further out each frame. Its 24-unit icon and 11 units of air fit the button.
     */
    @Override public void draw(Canvas canvas, ToolButton b) {
        // Cover the button's own icon; the selection dot draws over this.
        canvas.drawColor(Color.WHITE);
        // In the turned landscape column a step back would move it along the column instead.
        int turn = b.headerIcon ? host.toolbarTurn() : 0;
        canvas.save(); canvas.rotate(-turn, b.getWidth() / 2f, b.getHeight() / 2f);
        draw(canvas, dryer, b.getWidth(), b.getHeight(), frame, button.getResources().getDisplayMetrics().density);
        canvas.restore();
    }
    /**
     * Frame {@code frame} of the dryer and its air in a {@code width × height} px space, {@code density} px a dp: the
     * dryer at the left, centred up and down, its air blowing right.
     */
    static void draw(Canvas canvas, Drawable dryer, float width, float height, int frame, float density) {
        float half = Math.min(HALF * density, (width - 2 * density) * 12 / 35f);
        float x = density + half, y = height / 2f;
        // It rocks between three spots, a dp apart, as a running dryer buzzes.
        float shake = density * SHAKE[frame % SHAKE.length];
        float unit = 2 * half / 24, left = x - half, top = y - half + shake;
        dryer.setBounds(Math.round(left), Math.round(top), Math.round(x + half), Math.round(top + 2 * half));
        dryer.draw(canvas);
        // Pure black lines keep the button on the panel's fast black and white updates.
        Paint paint = new Paint();
        paint.setColor(Color.BLACK); paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * density); paint.setStrokeCap(Paint.Cap.ROUND);
        // Its nozzle faces right, and its air lines sit at these heights of the 24-unit icon.
        float limit = width - density;
        for (float row : new float[]{4.5f, 9, 13.5f}) for (int wave = 0; wave < 2; wave++) {
            float start = left + (24 + (frame * 2 + wave * 4) % 8) * unit;
            float end = Math.min(limit, start + 3 * unit);
            if (end > start) canvas.drawLine(start, top + row * unit, end, top + row * unit, paint);
        }
    }
}
