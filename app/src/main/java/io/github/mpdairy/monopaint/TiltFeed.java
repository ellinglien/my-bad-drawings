package io.github.mpdairy.monopaint;

import android.view.View;

/**
 * The page's tilt for a session's wet paint (Gravity), from the accelerometer through MonoPaint's {@link CanvasGravity}
 * (low-pass filtered, turned to the page, a dead zone of about 4.6° so a tablet on a desk is level). It listens only
 * while the session wants the tilt ({@link WatercolorSession#wantsTilt}: a stroke that reads it is down, or a wash that
 * moves with it is still wet) and {@code view}'s window has focus (not while paused, behind a dialog or another
 * screen), and a few seconds after, so the next stroke starts with a reading; otherwise the sensor is off. The
 * drawing screen's bar and Brush Lab's test area each make one for their session.
 */
final class TiltFeed {
    /** How long it keeps listening after the tilt stops mattering, ms: the next stroke usually comes sooner. */
    static final long LINGER_MS = 4000;
    private final CanvasGravity sensor;
    private final WatercolorSession session;
    private final View view;
    private final Runnable off = this::off;

    TiltFeed(View view, WatercolorSession session) {
        this.view = view; this.session = session;
        sensor = new CanvasGravity(view.getContext(), () -> true);
        session.tilt = sensor::read;
        session.tiltChanged = this::update;
        view.getViewTreeObserver().addOnWindowFocusChangeListener(focus -> update());
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { update(); }
            @Override public void onViewDetachedFromWindow(View v) { view.removeCallbacks(off); sensor.listen(false); }
        });
    }
    /** Listens if the tilt matters now; once it stops mattering, after {@link #LINGER_MS}; never without the window's focus. */
    private void update() {
        boolean focus = view.isAttachedToWindow() && view.hasWindowFocus();
        view.removeCallbacks(off);
        if (!focus) { sensor.listen(false); return; }
        if (session.wantsTilt()) sensor.listen(true);
        else view.postDelayed(off, LINGER_MS);
    }
    private void off() { if (!session.wantsTilt()) sensor.listen(false); }
}
