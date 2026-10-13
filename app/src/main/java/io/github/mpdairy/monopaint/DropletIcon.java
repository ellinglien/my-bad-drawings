package io.github.mpdairy.monopaint;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * The Watercolor mode droplet: a tall, skinny drop outline, solid while the mode is on and
 * dashed while it is off. {@link WetDroplet} ripples the same outline while paint moves.
 */
final class DropletIcon extends Drawable {
    /** The outline's width, in the icon's 24 units. */
    static final float STROKE = 1.5f;
    private static final Path OUTLINE = outline();
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);

    DropletIcon(boolean dashed) { style(ink, dashed); }

    /** The droplet's outline in its 24-unit space, tip at the top. */
    static Path outline() {
        Path drop = new Path();
        drop.moveTo(12, 2); drop.cubicTo(11, 6, 6, 10, 6, 15.5f);
        drop.arcTo(new RectF(6, 9.5f, 18, 21.5f), 180, -180);
        drop.cubicTo(18, 10, 13, 6, 12, 2); drop.close();
        return drop;
    }
    /** Sets {@code paint} to draw the outline, in the icon's 24 units: solid, or dashed. */
    static void style(Paint paint, boolean dashed) {
        paint.setColor(Color.BLACK); paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(STROKE); paint.setStrokeJoin(Paint.Join.ROUND);
        // Square ends, so the gaps stay as wide as they are drawn: plainly dashed on e-ink.
        paint.setStrokeCap(dashed ? Paint.Cap.BUTT : Paint.Cap.ROUND);
        paint.setPathEffect(dashed ? new DashPathEffect(new float[]{2.6f, 2.6f}, 0) : null);
    }
    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float scale = bounds.width() / 24f;
        int saved = canvas.save();
        canvas.translate(bounds.left, bounds.top); canvas.scale(scale, scale);
        canvas.drawPath(OUTLINE, ink);
        canvas.restoreToCount(saved);
    }
    @Override public void setAlpha(int alpha) { ink.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { ink.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
