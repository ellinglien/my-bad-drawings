package io.github.mpdairy.monopaint;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Lines and dots drawn straight into the presented raster over the artwork, such as a
 * gradient's direction, the lasso's loop or a selection's frame, so they reach the panel
 * like any canvas change. Without antialiasing their pixels stay black/white for the fast
 * preview path. The pixels beneath are kept to erase them again.
 */
final class CanvasGuide {
    /** Black line width, the white edging's width around it, and the longest stretch one saved tile spans. */
    private final float thin, wide, longest;
    private final Paint paint = new Paint();
    /** Presented pixels drawn over, in drawing order, and what was under each. */
    private final ArrayList<Rect> tiles = new ArrayList<>();
    private int[] background = new int[0];
    private int saved;

    CanvasGuide(float thin, float wide, float longest) { this.thin = thin; this.wide = wide; this.longest = longest; }
    boolean isEmpty() { return tiles.isEmpty(); }

    /**
     * Draws lines through {@code count} points ({@code x, y} pairs, presented pixels), back to
     * the first when {@code closed}, and dots of {@code radius} at {@code dots}, over whatever
     * is already drawn. {@code changed} receives each area drawn over.
     */
    void add(Bitmap target, float[] points, int count, boolean closed, float[] dots, float radius, Consumer<Rect> changed) {
        int first = tiles.size();
        int segments = closed ? count : count - 1;
        RectF bounds = new RectF();
        for (int s = 0; s < segments; s++) {
            int a = 2 * s, b = 2 * ((s + 1) % count);
            float x0 = points[a], y0 = points[a+1], x1 = points[b], y1 = points[b+1];
            // Short pieces keep a long diagonal's damage to a band along the line.
            int pieces = Math.max(1, (int)Math.ceil(Math.hypot(x1 - x0, y1 - y0) / longest));
            for (int i = 0; i < pieces; i++) {
                float u = (float)i / pieces, v = (float)(i + 1) / pieces;
                bounds.set(Math.min(x0 + (x1 - x0) * u, x0 + (x1 - x0) * v), Math.min(y0 + (y1 - y0) * u, y0 + (y1 - y0) * v),
                        Math.max(x0 + (x1 - x0) * u, x0 + (x1 - x0) * v), Math.max(y0 + (y1 - y0) * u, y0 + (y1 - y0) * v));
                bounds.inset(-wide - 1, -wide - 1);
                keep(target, bounds);
            }
        }
        if (dots != null) for (int i = 0; i + 1 < dots.length; i += 2) {
            bounds.set(dots[i], dots[i+1], dots[i], dots[i+1]); bounds.inset(-radius - wide - 1, -radius - wide - 1);
            keep(target, bounds);
        }
        Canvas canvas = new Canvas(target);
        paint.setStyle(Paint.Style.STROKE);
        for (int pass = 0; pass < 2; pass++) {
            paint.setColor(pass == 0 ? Color.WHITE : Color.BLACK); paint.setStrokeWidth(pass == 0 ? wide : thin);
            for (int s = 0; s < segments; s++) {
                int a = 2 * s, b = 2 * ((s + 1) % count);
                canvas.drawLine(points[a], points[a+1], points[b], points[b+1], paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
        // Black dots in a wide white ring with a thin black rim, plain on dark artwork and light.
        if (dots != null) for (int i = 0; i + 1 < dots.length; i += 2) {
            float ring = radius + wide - thin;
            paint.setColor(Color.BLACK); canvas.drawCircle(dots[i], dots[i+1], ring + thin / 2, paint);
            paint.setColor(Color.WHITE); canvas.drawCircle(dots[i], dots[i+1], ring, paint);
            paint.setColor(Color.BLACK); canvas.drawCircle(dots[i], dots[i+1], radius, paint);
        }
        for (int i = first; i < tiles.size(); i++) changed.accept(tiles.get(i));
    }
    /** Saves the presented pixels under {@code bounds} before they are drawn over. */
    private void keep(Bitmap target, RectF bounds) {
        Rect tile = new Rect(); bounds.roundOut(tile);
        if (!tile.intersect(0, 0, target.getWidth(), target.getHeight())) return;
        int size = tile.width() * tile.height();
        if (background.length < saved + size) background = java.util.Arrays.copyOf(background, Math.max(saved + size, 2 * background.length));
        target.getPixels(background, saved, tile.width(), tile.left, tile.top, tile.width(), tile.height());
        saved += size; tiles.add(tile);
    }
    /** Restores the pixels under everything drawn, latest first so overlaps unwind correctly. */
    void erase(Bitmap target, Consumer<Rect> changed) {
        for (int i = tiles.size() - 1; i >= 0; i--) {
            Rect tile = tiles.get(i);
            saved -= tile.width() * tile.height();
            target.setPixels(background, saved, tile.width(), tile.left, tile.top, tile.width(), tile.height());
            changed.accept(tile);
        }
        forget();
    }
    /** Forgets what was drawn, once the raster beneath has been redrawn without it. */
    void forget() { tiles.clear(); saved = 0; }
}
