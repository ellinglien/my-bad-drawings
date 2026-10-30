package io.github.mpdairy.monopaint;

import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import java.util.function.Supplier;

/**
 * A grey strip's pen and marker on the panel, shared by MonoPaint's color bar ({@link ShadePicker}), Inkwash's grey
 * bar ({@link GreyBar}) and the side bar's standing grey strip ({@link GreyStrip}): the pen's pointer while it is down on the strip ({@link #touch}), and the marker's fast e-ink
 * feedback. Each move of the marker goes to the panel through {@link SelectionFeedback} for the marker's gutter alone
 * (the owner's {@code area}); on a fast binary plane its Android frame waits until the pen lifts, and 250 ms after it
 * lifts one clean grey pass clears the fast ink.
 */
final class StripMarker {
    private final View owner;
    private final SelectionFeedback feedback;
    private final Supplier<Rect> area;
    private int pointer = -1;
    private boolean deferCommit;
    private final Runnable clean = this::clean;
    /** After {@link #touch}: where it chose, in the owner's x and y; whether the pen left the strip, and whether it lifted (else it was cancelled). */
    float x, y;
    boolean ended, lifted;

    StripMarker(View owner, SelectionFeedback feedback, Supplier<Rect> area) { this.owner = owner; this.feedback = feedback; this.area = area; }

    /** Whether the marker's gutter is all ink and paper: it goes through the panel's binary update ({@link SelectionFeedback#updateBinary}). */
    boolean binary;
    /** The marker moves through the fast display: {@code change} shows on the panel over the marker's gutter. */
    void update(Runnable change) { if (binary) feedback.updateBinary(owner, area.get(), change); else feedback.update(owner, area.get(), change); }
    /** A marker gesture begins (the pen down, a key, a slide): its Android frames wait while the plane is fast and binary. */
    void begin() {
        owner.removeCallbacks(clean);
        deferCommit = DirectEink.fastBinaryControls();
    }
    /** Commits what the marker showed to Android's surface too, unless a gesture defers it. */
    void finishUpdate() { if (!deferCommit) feedback.finishUpdate(owner); }
    /**
     * The gesture ended: one clean pass soon, on a fast binary plane, and after a {@link #binary} marker's moves (Inkwash:
     * the Manta's stroke mode 5 leaves a faint trail where the size slider's tab passed, the owner, 2026-10-24:
     * "artifacting of side size indicator"; one grey pass of the tab's strip alone, no flash).
     */
    void scheduleCleanup() {
        owner.removeCallbacks(clean);
        if (DirectEink.fastBinaryControls() || binary) owner.postDelayed(clean, 250);
    }
    /** The strip left its window or lost focus: no clean pass; what it showed is committed now. */
    void stop() {
        owner.removeCallbacks(clean);
        deferCommit = false;
        finishUpdate();
    }
    /** Whether the pen is down on the strip. */
    boolean held() { return pointer != -1; }
    private void clean() {
        if (held()) return;
        deferCommit = false;
        finishUpdate();
        // The binary stroke mode's trail: the paper driven again, gently (on the fast plane the clean pass does it).
        if (binary && !DirectEink.fastBinaryControls()) feedback.redrive(owner, area.get()); else feedback.clean(owner, area.get());
    }

    /**
     * Follows the pen on the strip: down begins a gesture, holding the pen's pointer; it chooses where the pen is down,
     * moves and lifts (true, at {@link #x}); a lift or a cancel ends it ({@link #ended}, {@link #lifted}), the clean pass
     * scheduled.
     */
    boolean touch(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            begin();
            pointer = event.getPointerId(0);
            if (owner.getParent() != null) owner.getParent().requestDisallowInterceptTouchEvent(true);
        }
        lifted = (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
                && event.getPointerId(event.getActionIndex()) == pointer;
        ended = lifted || action == MotionEvent.ACTION_CANCEL;
        int index = event.findPointerIndex(pointer);
        boolean chooses = index >= 0 && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || lifted);
        if (chooses) { x = event.getX(index); y = event.getY(index); }
        if (ended) {
            pointer = -1;
            if (owner.getParent() != null) owner.getParent().requestDisallowInterceptTouchEvent(false);
            scheduleCleanup();
        }
        return chooses;
    }
}
