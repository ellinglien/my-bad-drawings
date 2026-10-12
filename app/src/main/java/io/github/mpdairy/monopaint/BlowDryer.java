package io.github.mpdairy.monopaint;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/** The air the color bar's blow dryer blows after a tap, inside a box around it. */
final class BlowDryer {
    private BlowDryer() { }
    /** Frames of air, each this long. */
    private static final int FRAMES = 6, FRAME_MS = 140;

    /** Shows frame {@code frame} of the dryer's air, then the next; past the last, clears it. */
    static void blow(ControlHost host, ToolButton button, int frame) {
        if (!button.isAttachedToWindow()) return;
        boolean done = frame >= FRAMES;
        // The dryer steps back while it blows, leaving room for the air in front of it. In the
        // turned landscape column the icon's offset would move it along the column instead.
        int turn = host.toolbarTurn(), back = turn == 0 ? -6 : 0;
        host.selectionFeedback().update(button, () -> {
            button.overlay = done ? null : (canvas, b) -> drawAir(canvas, b, turn, frame);
            button.iconOffset = done ? 0 : back;
        });
        if (!done) button.postDelayed(() -> blow(host, button, frame + 1), FRAME_MS);
    }
    /** A box around the dryer and puffs of air leaving its nozzle, further out each frame. */
    private static void drawAir(Canvas canvas, ToolButton button, int turn, int frame) {
        button.press.drawBox(canvas);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK); paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(button, 1.5f)); paint.setStrokeCap(Paint.Cap.ROUND);
        // Draw beside the upright icon; its nozzle faces right, and its air lines sit at these heights of the 24-unit icon.
        float x = button.getWidth() / 2f + dp(button, button.iconOffset), y = button.getHeight() / 2f;
        canvas.save(); canvas.rotate(-turn, x, y);
        float half = dp(button, button.iconHalf), unit = 2 * half / 24, left = x - half, top = y - half;
        // The turned icon stays centered in its square button, so the box's edge is as far away either way.
        float limit = button.getWidth() - dp(button, PressOutline.BOX_INSET_DP + 3);
        for (float row : new float[]{4.5f, 9, 13.5f}) for (int wave = 0; wave < 2; wave++) {
            float start = left + (24 + (frame * 2 + wave * 4) % 8) * unit;
            float end = Math.min(limit, start + 3 * unit);
            if (end > start) canvas.drawLine(start, top + row * unit, end, top + row * unit, paint);
        }
        canvas.restore();
    }
    private static int dp(ToolButton view, float value) { return Ui.dp(view.getContext(), value); }
}
