package io.github.mpdairy.monopaint;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.os.SystemClock;

/**
 * While watercolor or wet canvas paint is still moving, the droplet's outline ripples:
 * quickly while lots of paint is moving, slower and slower as the last of it settles. Then
 * the droplet comes to rest.
 */
final class WetDroplet {
    /** How long each frame shows, with lots of paint moving and with almost none; each costs a panel update. */
    private static final int FASTEST_MS = 90, SLOWEST_MS = 380;
    /** Tile frames of work left from which the outline ripples fastest. */
    private static final int BUSY_WORK = 1500;
    /** Frames for the ripples to travel one whole wave around the outline. */
    private static final int PHASES = 4;
    /** Ripples around the outline, and how far they push it in and out, in the icon's 24 units. */
    private static final int WAVES = 7;
    private static final float AMPLITUDE = .8f;
    /** Points the rippling outline is drawn through. */
    private static final int SAMPLES = 112;
    private static final float[] outline = outline();
    /** How long after the droplet comes to rest a tap still counts as drying, for the panel's lag. */
    private static final int SETTLED_GRACE_MS = 1000;

    private final PaintActivity app;
    private final ToolButton button;
    private final Runnable step = this::step;
    private final Path wave = new Path();
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    /** The frame showing, or -1 while the droplet rests. */
    private int frame = -1;
    private boolean running;
    /** When the droplet last came to rest by itself, as the paint settled. */
    private long settledAt = Long.MIN_VALUE / 2;
    /** The paint was dried outright; a ripple still showing only finishes its frame. */
    private boolean dried;

    WetDroplet(PaintActivity app, ToolButton button) {
        this.app = app; this.button = button;
        density = button.getResources().getDisplayMetrics().density;
    }
    /** Starts the outline rippling, if it isn't already, as watercolor or wet canvas paint begins to move. */
    void start() {
        dried = false;
        if (!running) { running = true; button.postDelayed(step, FASTEST_MS); }
    }
    /** Whether wet paint is moving, or settled so recently that the panel may still show the outline rippling. */
    boolean moving() {
        return !dried && (running || SystemClock.uptimeMillis() - settledAt < SETTLED_GRACE_MS);
    }
    /** All wet paint was dried at once, so nothing is moving even while a ripple finishes its frame. */
    void dried() { dried = true; settledAt = Long.MIN_VALUE / 2; }
    /**
     * Brings the droplet to rest right away, once the paint is dried, leaving its button's
     * overlay to the caller. The next tap toggles wet canvas again.
     */
    void rest() {
        button.removeCallbacks(step);
        dried(); running = false; frame = -1;
    }
    private void settle() { running = false; if (!dried) settledAt = SystemClock.uptimeMillis(); }
    private void step() {
        if (!button.isAttachedToWindow()) { running = false; return; }
        // The droplet holds still while the pen draws, leaving the panel and the time to the
        // pen; counting the work left visits every moving tile, so it waits too.
        if (app.pad.stroking()) { button.postDelayed(step, FASTEST_MS); return; }
        int work = app.pad.wetWork();
        // Once the paint settles the droplet comes to rest.
        int shown = work == 0 ? -1 : (frame + 1) % PHASES;
        if (shown == -1 && frame == -1) { settle(); return; }
        app.selectionFeedback().update(button, () -> {
            frame = shown;
            button.overlay = shown >= 0 ? this::draw : null;
        });
        if (shown == -1) { settle(); return; }
        float busy = Math.min(1, work / (float)BUSY_WORK);
        button.postDelayed(step, Math.round(SLOWEST_MS + (FASTEST_MS - SLOWEST_MS) * busy));
    }
    /** One frame: the droplet's outline, solid or dashed like the icon, rippling a little further around it each frame. */
    private void draw(Canvas canvas, ToolButton b) {
        int width = b.getWidth(), height = b.getHeight();
        // Cover the resting droplet; the selection dot and press box draw over this.
        canvas.drawColor(Color.WHITE);
        canvas.save();
        // Upright where the icon sits, however the strip is turned, as the icon is.
        canvas.translate(width / 2f + b.iconOffset * density, height / 2f);
        if (b.headerIcon) canvas.rotate(-app.toolbarTurn());
        float scale = 2 * b.iconHalf * density / 24;
        canvas.scale(scale, scale); canvas.translate(-12, -12);
        double phase = 2 * Math.PI * frame / PHASES;
        wave.rewind();
        for (int i = 0; i < SAMPLES; i++) {
            float push = AMPLITUDE * (float)Math.sin(2 * Math.PI * WAVES * i / SAMPLES - phase);
            float x = outline[i * 4] + outline[i * 4 + 2] * push, y = outline[i * 4 + 1] + outline[i * 4 + 3] * push;
            if (i == 0) wave.moveTo(x, y); else wave.lineTo(x, y);
        }
        wave.close();
        DropletIcon.style(ink, !b.marked);
        canvas.drawPath(wave, ink);
        canvas.restore();
    }
    /** Points around the droplet icon's outline, in its 24-unit space, each with its unit normal: x, y, nx, ny. */
    private static float[] outline() {
        PathMeasure measure = new PathMeasure(DropletIcon.outline(), true);
        float[] points = new float[SAMPLES * 4], position = new float[2], tangent = new float[2];
        for (int i = 0; i < SAMPLES; i++) {
            measure.getPosTan(measure.getLength() * i / SAMPLES, position, tangent);
            points[i * 4] = position[0]; points[i * 4 + 1] = position[1];
            points[i * 4 + 2] = -tangent[1]; points[i * 4 + 3] = tangent[0];
        }
        return points;
    }
}
