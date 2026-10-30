package io.github.mpdairy.monopaint;

/**
 * What a touch on the page bar's previews is (docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md, "Interaction
 * details"), from its time and how far it moved: a tap goes to the page; a quick drag ({@link #MOVE_DP} before
 * {@link #HOLD_MS}) riffles; a hold, then a drag, lifts the page to move it; a hold released where it began opens the
 * page's options. The same for pen and finger; the pen's side button held as it lands opens the options at once. A
 * second pointer calls it off. Hovering never reaches it. Pure, checked on the desktop ({@code StripGestureChecks}).
 */
final class StripGesture {
    /** The hold, ms, and the move that ends a tap, dp: the prototype's starting values, to tune on the device. */
    static final long HOLD_MS = 400;
    static final float MOVE_DP = 8;
    /**
     * The screen's top band, dp, where a finger pulled down opens the Supernote's tray (Android's own start band on the
     * Manta is 69 px, 37 dp, portrait: logcat's SystemGestures, 2026-10-24): the page bar's whole height.
     */
    static final int SYSTEM_EDGE_DP = 40;
    /**
     * A finger that landed in {@link #SYSTEM_EDGE_DP} has moved {@code dx}, {@code dy} (px): 0 while under {@code slop}
     * ({@link #MOVE_DP}, so it is decided before a riffle starts), 1 a pull down (the system's tray, never the bar's),
     * -1 anything else (the bar's own).
     */
    static int edgePull(float dx, float dy, float slop) { return Math.hypot(dx, dy) < slop ? 0 : dy > Math.abs(dx) ? 1 : -1; }
    /** Undecided yet, then what it became. */
    enum Kind { PRESSED, HELD, RIFFLE, LIFT, TAP, OPTIONS, CANCELLED }

    private final float move;
    private Kind kind = Kind.CANCELLED;
    private long down;
    private float x0, y0;

    /** {@code movePx}: {@link #MOVE_DP} in the screen's pixels. */
    StripGesture(float movePx) { move = movePx; }

    Kind kind() { return kind; }
    /** Whether it has become a riffle or a lifted page, which follow the touch until it lifts. */
    boolean dragging() { return kind == Kind.RIFFLE || kind == Kind.LIFT; }

    /** A pointer lands at {@code time}; {@code sideButton}: the pen's button is held. */
    Kind down(long time, float x, float y, boolean sideButton) {
        down = time; x0 = x; y0 = y;
        return kind = sideButton ? Kind.OPTIONS : Kind.PRESSED;
    }
    /** Time passes with the pointer still: a press held long enough is held. */
    Kind tick(long time) {
        if (kind == Kind.PRESSED && time - down >= HOLD_MS) kind = Kind.HELD;
        return kind;
    }
    /** The pointer moves: a move before the hold riffles, one after it lifts the page. */
    Kind move(long time, float x, float y) {
        tick(time);
        if ((kind == Kind.PRESSED || kind == Kind.HELD) && Math.hypot(x - x0, y - y0) >= move)
            kind = kind == Kind.PRESSED ? Kind.RIFFLE : Kind.LIFT;
        return kind;
    }
    /** The pointer lifts: a press is a tap, a hold opens the options; a riffle or a lift ends where it is. */
    Kind up(long time, float x, float y) {
        move(time, x, y);
        if (kind == Kind.PRESSED) kind = Kind.TAP;
        else if (kind == Kind.HELD) kind = Kind.OPTIONS;
        return kind;
    }
    /** A second pointer, or the system took the touch: nothing happens. */
    Kind cancel() { return kind = Kind.CANCELLED; }

    /**
     * The strip's tile of place {@code i} in a notebook of {@code count} pages: the front cover first, the pages, the + tile
     * (tile {@code count + 1}, where the blank page at the end used to be), the back cover last.
     */
    static int tileOf(int i, int count) { return i == DrawingBook.FRONT ? 0 : i == DrawingBook.BACK ? count + 2 : i + 1; }
    /** The place at tile {@code tile}; {@link DrawingBook#NONE} for the + tile. */
    static int placeAt(int tile, int count) { return tile == 0 ? DrawingBook.FRONT : tile == count + 2 ? DrawingBook.BACK : tile == count + 1 ? DrawingBook.NONE : tile - 1; }
    /**
     * Whether a window of {@code shown} slots from tile {@code start} shows tile {@code tile} of a notebook of {@code count}
     * pages: inside it and not under the + tile, pinned in its last slot when its own tile is out of the window.
     */
    static boolean shows(int start, int tile, int count, int shown) {
        int k = tile - start, plus = count + 1 - start;
        boolean pinned = plus < 0 || plus >= shown;
        return start >= 0 && start <= Math.max(0, count + 3 - shown) && k >= 0 && k < shown && !(pinned && k == shown - 1);
    }
    /** Where a lifted page lands over place {@code over}: that page's place; over a cover, the nearest end (covers stay). */
    static int landing(int over, int count) { return over == DrawingBook.FRONT ? 0 : over == DrawingBook.BACK ? count - 1 : over; }
}
