package io.github.mpdairy.monopaint;

/**
 * The Manta's left hardware edge strip held with two fingers (as Supernote's own gesture): the pen is a Trash for as
 * long as the hold lasts. Pure state: the key events in, what to do out.
 */
final class EdgeHold {
    // Measured on the device (2026-10-27, logcat from WindowManager and InputDispatcher): one finger on the strip is
    // keyCode 301 (a slide too, with no position), a second finger adds 300; both come up as the fingers lift.
    // They have no KeyEvent constants.
    static final int KEY_ONE = 301, KEY_TWO = 300;

    enum Act { NONE, ENTER, LEAVE }

    private boolean one, two, holding, waiting;

    /** A strip key event; {@code loopDrawing}: the pen is mid-loop, which finishes and acts before the Trash goes. */
    Act key(int code, boolean down, boolean repeat, boolean loopDrawing) {
        if (repeat) return Act.NONE;
        if (code == KEY_ONE) one = down; else if (code == KEY_TWO) two = down; else return Act.NONE;
        if (down) {
            if (!one || !two || holding) return Act.NONE;
            holding = true;
            // A hold again while a loop was still finishing: the same hold goes on.
            if (waiting) { waiting = false; return Act.NONE; }
            return Act.ENTER;
        }
        if (!holding) return Act.NONE;
        holding = false;
        if (loopDrawing) { waiting = true; return Act.NONE; }
        return Act.LEAVE;
    }

    /** The loop the pen was drawing at the lift has acted. */
    Act loopEnded() {
        if (!waiting) return Act.NONE;
        waiting = false;
        return Act.LEAVE;
    }
}
