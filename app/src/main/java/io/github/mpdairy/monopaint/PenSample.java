package io.github.mpdairy.monopaint;

import android.view.MotionEvent;

/** One pen sample read from a MotionEvent as the strokes take it: position, pressure, Supernote's signed tilt degrees and the input's own clock. */
final class PenSample {
    float x, y, pressure, tiltX, tiltY;
    long time;

    /**
     * Reads pointer {@code index}'s sample {@code history} (-1 for the current one) of {@code event}. Tilt comes from
     * {@code physical}, the untransformed event, when given (Android would rotate its axes with the app); the
     * position stays the event's. {@code landscapeTilt}: see {@link Device#landscapeTilt}.
     */
    void read(MotionEvent event, MotionEvent physical, int index, int history, boolean landscapeTilt) {
        if (physical == null) physical = event;
        int source = physical.findPointerIndex(event.getPointerId(index));
        if (source < 0 || history >= physical.getHistorySize()) { physical = event; source = index; }
        boolean current = history < 0;
        x = current ? event.getX(index) : event.getHistoricalX(index, history);
        y = current ? event.getY(index) : event.getHistoricalY(index, history);
        pressure = current ? event.getPressure(index) : event.getHistoricalPressure(index, history);
        // The input's own clock, so strokes follow the pen's speed (see PenSpeed).
        time = current ? event.getEventTime() : event.getHistoricalEventTime(history);
        // Supernote encodes signed X degrees in ORIENTATION, Y in TILT; treating ORIENTATION as Android azimuth corrupts tilt.
        tiltX = current ? physical.getOrientation(source) : physical.getHistoricalOrientation(source, history);
        tiltY = current ? physical.getAxisValue(MotionEvent.AXIS_TILT, source) : physical.getHistoricalAxisValue(MotionEvent.AXIS_TILT, source, history);
        if (landscapeTilt) { float panelX = tiltX; tiltX = -tiltY; tiltY = panelX; }
    }
}
