package io.github.mpdairy.monopaint;

import android.view.View;

/**
 * Ten ticks a second while a Drying wash settles, between strokes (spec "Drying after lift"): each tick steps the
 * session's settling wash on the host's page and shows it, paced by the session's {@link SettlePacer} (backing off
 * after lagging input or a slow tick, showing at most every 120 ms; see {@link WatercolorSession#tick}); when the
 * wash is done the host is told, to save and refresh as after a stroke.
 *
 * <p>Beside it, the page's marks fading or darkening by the clock (Fading, Lemon Juice: {@link FadingInk}) step when
 * they are due, each shown on its own (a partial refresh of what it changed), never while anything on the page is
 * still wet (a wash reads the page as it left it); when one is done the host is told, as for a wash. With nothing
 * settling or fading nothing is posted.
 */
final class DryingTicker {
    static final long TICK_MS = SettlePacer.TICK_MS;
    /** How soon a due fading mark looks again while the host is busy or the page wet. */
    static final long FADE_WAIT_MS = 500;

    interface Host {
        /** The page the wash should still be on. */
        ToneDocument page();
        /** A stroke is down or the page can't be drawn now: try again next tick. */
        boolean busy();
        /** Renders and presents what the tick changed. */
        void show();
        /** The wash has settled, been laid at once or stopped; or a fading mark is done. */
        void settled();
    }

    private final View view;
    private final WatercolorSession session;
    private final Host host;
    private final Runnable tick = this::tick;
    /** Whether the ticks since {@link #start} step a settling wash. */
    private boolean washing;

    DryingTicker(View view, WatercolorSession session, Host host) { this.view = view; this.session = session; this.host = host; }

    /** After a stroke, a page shown or a pause: ticks while the session's wash settles, or when the page's next fading mark is due. */
    void start() {
        view.removeCallbacks(tick);
        washing = session.settling();
        long wait = washing ? TICK_MS : fadeWait();
        if (wait >= 0) view.postDelayed(tick, wait);
    }
    void stop() { view.removeCallbacks(tick); }
    /**
     * Lays a wash still settling where it is at once, shows it and reports it settled, and ticks on only for fading marks
     * (by the clock, they go on): before the page is put away or changed by anything but a stroke.
     */
    void settleNow() {
        stop();
        // Wet paint at rest is wet too: the tap dries it.
        if (session.wet()) {
            session.settleNow(host.page());
            host.show(); host.settled();
        }
        start();
    }

    /**
     * The wet indicator's tap: what is wet runs on to the end it would reach left alone, as fast as its ticks go, and is
     * shown and reported settled once there ({@link WatercolorSession#hurry}); the ticks start at once.
     */
    void hurry() {
        stop();
        if (session.wet()) {
            if (session.hurry(host.page())) { washing = true; view.post(tick); return; }
            // Done at once (a bath printed, wet paint at rest dried).
            host.show(); host.settled();
        }
        start();
    }

    /**
     * One tick. A wash laid at once or dropped since the last one (a shade or layer chosen, undo) is shown and reported
     * as one that ended, as soon as the host is free. Then the fading marks due, by the clock.
     */
    private void tick() {
        if (host.busy()) { view.postDelayed(tick, washing ? TICK_MS : FADE_WAIT_MS); return; }
        long next = -1;
        if (washing) {
            next = session.tick(host.page(), host::show);
            if (next < 0) { washing = false; host.settled(); }
        }
        long fade = fade();
        if (fade >= 0) next = next < 0 ? fade : Math.min(next, fade);
        if (next >= 0) view.postDelayed(tick, next);
    }
    /** Steps the page's fading marks due now, unless the page is wet; how long until the next is due, or -1 for none. */
    private long fade() {
        ToneDocument page = host.page();
        if (page == null || !page.fading()) return -1;
        if (session.moving()) return FADE_WAIT_MS;
        long now = FadingInk.now();
        if (page.nextFade() <= now && (page.fade(now, host::show) & ToneDocument.FADE_ENDED) != 0) host.settled();
        return fadeWait();
    }
    /** How long until the page's next fading mark is due, ms; -1 for none. */
    private long fadeWait() {
        ToneDocument page = host.page();
        return page == null || !page.fading() ? -1 : Math.max(0, Math.min(FADE_WAIT_MS * 20, page.nextFade() - FadingInk.now()));
    }
}
