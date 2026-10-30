package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

/**
 * The droplet, always there (the owner, 2026-10-22: "is the droplet invisible?"), in Phosphor's drops (the owner,
 * 2026-10-22: "for drop icon use phosphor drop"), keeping MonoPaint's meaning: the drop's outline dashed while the page
 * is dry (Phosphor's solid outline read as on, the owner, 2026-10-23: "the wet icon seems like it's always on now", as
 * Zen Mode's off is its dashed circle; the owner's own dashed drop from 2026-10-27, Iconoir's droplet dashed since 2026-10-29,
 * res/drawable/ic_drop_dashed);
 * half full while paint on it still moves ({@link WatercolorSession#moving}: a Slow wash settling, halos growing, wet
 * paint running, a quick stroke's render after lift once it has lasted {@link #RENDERING_MS}), where MonoPaint's droplet
 * ripples (still on e-ink); full while paint lies wet at rest ({@link WatercolorSession#wet}, a wet canvas held), as
 * MonoPaint's is solid. In the drawing screen's side column beside Play, under the Lasso (2026-10-26; on the brush line from 2026-10-24), and beside Clear over Brush Lab's test page.
 * A tap dries what is wet the way it would dry left alone, only sooner ({@link DryingTicker#hurry}: a wash runs on to
 * its end, spreading, settling and forming its edges, with no wait between its ticks, then shows once; one undo step
 * with its stroke; the owner, 2026-10-29: "looks like the hair dryer just stops the image as it is instead of accellerate
 * the drying processing"), and MonoPaint's blow dryer blows in its place meanwhile, as on MonoPaint's droplet (the owner, 2026-10-22:
 * "the hair dryer animation isn't the same as monopaint, can you pull that pls"): {@link BlowDryer}'s six frames and
 * MonoPaint's own dryer icon, in a box MonoPaint's droplet button's width ({@link #DRYER_DP}), no box drawn round it,
 * each frame put on the panel over the indicator's square alone, round again while the drying runs ({@link Host#hurrying}). It keeps its place and size either way, so nothing
 * around it moves; a change is drawn over its own square only ({@link Host#update}), and never while the pen is down: it
 * looks again once the pen lifts, and every {@link #WET_MS} while wet, for paint coming to rest.
 */
@SuppressLint("ViewConstructor")
final class WetIndicator extends View {
    interface Host {
        /** Whether anything is still wet. */
        boolean wet();
        /** Whether paint still moves ({@link WatercolorSession#moving}). */
        boolean moving();
        /** A stroke is down: the indicator waits for it to end. */
        boolean stroking();
        /** All that is wet is a quick stroke rendering after lift ({@link WatercolorSession#onlyRendering}): shown only once it lasts {@link #RENDERING_MS}. */
        boolean onlyRendering();
        /** Dries what is still wet as it would dry left alone, only sooner ({@link DryingTicker#hurry}), and shows it once done. */
        void dry();
        /** Whether a tap's drying still runs ({@link WatercolorSession#hurrying}). */
        boolean hurrying();
        /** Applies {@code change} to {@code view} and puts the result on the panel, over the view's square only. */
        void update(View view, Runnable change);
    }
    /** How often it looks again while a stroke is down, ms. */
    private static final long STROKING_MS = 100;
    /**
     * A quick stroke's render after lift alone shows the droplet only once it has lasted this long, ms. Most end well
     * before. On the Manta the panel runs one update at a time where they don't overlap: a droplet drawn at the lift
     * (while the stroke's fast updates still run) held the stroke's own last update back until the droplet's was done,
     * about 0.3 s, and hiding it again cost as much (engine spec, "Quick then render", the wait after lift).
     */
    static final long RENDERING_MS = 1000;
    /** The drop's box, dp: about MonoPaint's droplet's height (29 dp), Phosphor's drop filling most of its box. */
    // The other small icons' size, Menu's (the owner, 2026-10-29: "the droplet looks a bit big in the toolbar... try to keep them all proportional"; 30 until then).
    static final float ICON_DP = DrawLayout.ICON_FULL;
    /** How often it looks again while wet, for paint coming to rest, ms. */
    static final long WET_MS = 1000;
    /** What it shows: dry, paint moving, wet paint at rest. */
    enum State { DRY, MOVING, WET }
    /** The blow dryer's room, dp: MonoPaint's droplet button's width, which its dryer and air fit ({@link BlowDryer#draw(Canvas, Drawable, float, float, int, float)}). */
    static final float DRYER_DP = 32;
    private final Host host;
    private final Drawable[] drops = new Drawable[State.values().length];
    private final Runnable sync = this::sync;
    /** What it shows. */
    private State shown = State.DRY;
    /** When a quick stroke's render alone began to keep the page wet (uptime ms), or −1. */
    private long renderingSince = -1;
    /** The blow dryer's frame while it blows ({@link BlowDryer#FRAMES}), or −1. */
    private int blowing = -1;
    private Drawable dryer;
    private final Runnable blow = this::blow;

    WetIndicator(Context context, Host host) {
        super(context);
        this.host = host;
        setOnClickListener(v -> {
            if (host.stroking()) return;
            // Dry already, the dryer still blows, as MonoPaint's does on a tap.
            if (shown != State.DRY) host.dry();
            // MonoPaint's blow dryer, its first frame at once.
            removeCallbacks(blow);
            host.update(this, () -> blowing = 0);
            postDelayed(blow, BlowDryer.FRAME_MS);
        });
        describe();
    }
    /** Whether anything is wet may have changed ({@link WatercolorSession#wetChanged}): it looks once the event is handled. */
    void changed() { removeCallbacks(sync); post(sync); }
    private void sync() {
        removeCallbacks(sync);
        if (host.stroking()) { postDelayed(sync, STROKING_MS); return; }
        boolean wet = host.wet();
        if (wet && shown == State.DRY && host.onlyRendering()) {
            long now = SystemClock.uptimeMillis();
            if (renderingSince < 0) renderingSince = now;
            if (now - renderingSince < RENDERING_MS) { postDelayed(sync, renderingSince + RENDERING_MS - now); return; }
        } else if (!host.onlyRendering()) renderingSince = -1;
        State state = !wet ? State.DRY : host.moving() ? State.MOVING : State.WET;
        // Wet: it looks again a while later, for paint coming to rest (no event says so).
        if (wet) postDelayed(sync, WET_MS);
        if (state == shown) return;
        if (!isAttachedToWindow()) { shown = state; describe(); return; }
        host.update(this, () -> { shown = state; describe(); });
    }
    /** The dryer's next frame; past the last, round again while the drying runs, else the droplet or the paper again. */
    private void blow() {
        if (blowing < 0) return;
        int next = blowing + 1 < BlowDryer.FRAMES ? blowing + 1 : host.hurrying() ? 0 : -1;
        if (!isAttachedToWindow()) { blowing = -1; return; }
        host.update(this, () -> blowing = next);
        if (next >= 0) postDelayed(blow, BlowDryer.FRAME_MS);
    }
    private void describe() {
        setContentDescription(shown == State.DRY ? "Dry." : shown == State.MOVING ? "Paint moving. Tap to dry." : "Wet. Tap to dry.");
    }
    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); removeCallbacks(sync); removeCallbacks(blow); blowing = -1; }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Theme.PAPER);
        if (blowing >= 0) {
            // The dryer and its air, as MonoPaint draws them on its droplet's button, that button's width, centred.
            if (dryer == null) dryer = getContext().getDrawable(R.drawable.ic_hair_dryer);
            float density = getResources().getDisplayMetrics().density, box = Math.min(getWidth(), DRYER_DP * density);
            int saved = canvas.save();
            canvas.translate((getWidth() - box) / 2f, 0);
            BlowDryer.draw(canvas, dryer, box, getHeight(), blowing, density);
            canvas.restoreToCount(saved);
            return;
        }
        // The drop for what it shows, centred, a short row taking it smaller.
        float side = Math.min(Ui.dp(getContext(), ICON_DP), Math.min(getWidth(), getHeight()));
        int left = Math.round((getWidth() - side) / 2f), top = Math.round((getHeight() - side) / 2f);
        Drawable drop = drops[shown.ordinal()];
        if (drop == null) drop = drops[shown.ordinal()] = getContext().getDrawable(shown == State.DRY ? R.drawable.ic_drop_dashed
                : shown == State.MOVING ? R.drawable.ic_ph_drop_half_bottom : R.drawable.ic_ph_drop_fill);
        drop.setBounds(left, top, left + Math.round(side), top + Math.round(side));
        drop.draw(canvas);
    }
}

