package io.github.mpdairy.monopaint;

/**
 * What a notebook's rules allow (docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md, "Rules"; its "Lock" was cut, 2026-10-24),
 * as questions the drawing screen asks before it acts, so each rule is decided in one place:
 * <ul>
 * <li><b>No Erasing</b> refuses the eraser (the bar's, the pen's eraser end and its side button, the finger's), Clear Page
 * and Clear Layer, Peel Off Masking Fluid, and any smudge that takes paint away: a brush whose Smudge is Lift or Clear Water
 * ({@link #erases}). It is "no eraser, no undo" (the owner, 2026-10-21): nothing drawn is taken back, so Undo and Redo are off
 * ({@link #undoOff}; the history is kept but ignored while it is on), and a page or layer with marks is not deleted
 * ({@link #keeps}). A smudge that moves paint (Drag, Smear) and a white paint are drawing, not erasing; Merge Down and Merge
 * Old Layers keep everything that shows (hidden layers show), so they stay. Adding, moving and setting pages stays too.</li>
 * <li><b>Brushes</b> keeps the bar to its first slots ({@link NotebookOptions#slotsShown}); the others and the smudge tool, another brush, are refused.</li>
 * <li><b>Timed Pages</b> ({@link TimedPages}) says Time's up at zero, never on a cover; the page locks then only under
 * <b>Lock Pages</b> ({@link NotebookOptions#lockPages}), else stays editable ({@link #timedSeconds}): {@link #blinks} says when its number blinks.</li>
 * </ul>
 * Pure, checked on the desktop ({@code NotebookRulesChecks}).
 */
final class NotebookRules {
    /** The notes the screen gives for a refused stroke or tap (docs/TEXT-STYLE.md: about three words). */
    static final String ERASING_OFF = "Erasing is off", UNDO_OFF = "Undo is off", PAGE_LOCKED = "Page locked";
    /** Time's up's offer on a timed page ({@link InkWashBar#timeUp}). */
    static final String NEXT_PAGE = "Next Page";
    /** The sheet's rows' labels and the choices that are words (Title Case). */
    static final String NO_ERASING = "No Erasing", TIMED_PAGES = "Timed Pages", LOCK_PAGES = "Lock Pages";
    /** What No Erasing means, said as it is set on (a card line, docs/TEXT-STYLE.md). */
    static final String NO_ERASING_SAID = "No eraser, no undo";
    static final String OFF = "Off", ON = "On";

    /** The last stretch of a page's time in which its number blinks, ms; for a page of a minute or less the last 10 s. */
    static final long BLINK_MS = 30_000, BLINK_SHORT_MS = 10_000;
    /** The number shows, then goes, each this long, ms: a slow blink, one update a second. */
    static final long BLINK_STEP_MS = 1000;

    private NotebookRules() {}

    // A page's own rules (the owner, 2026-10-23, Page Settings: "constraints for this page")

    /**
     * No Erasing and Timed Pages each the notebook's ({@link #NOTEBOOKS}), or Off or On for one page alone: 2 bits a rule in
     * a byte, kept beside the page's steps ({@code DrawingBook#pageRules}, {@code BookOptions}' {@code PRUL}). 0: both the
     * notebook's, as every page of an older notebook.
     */
    static final int NOTEBOOKS = 0, OWN_OFF = 1, OWN_ON = 2;
    static final int ERASING_BITS = 0, TIMED_BITS = 2;
    /** The time a page timed on its own has when it has no time of its own and the notebook's Timed Pages is off. */
    static final int PAGE_SECONDS = 300;
    /** Rule {@code at}'s ({@link #ERASING_BITS}, {@link #TIMED_BITS}) choice in a page's {@code bits}. */
    static int own(int bits, int at) { return bits >> at & 3; }
    /** {@code bits} with rule {@code at} set to {@code choice}. */
    static int withOwn(int bits, int at, int choice) { return bits & ~(3 << at) | (choice & 3) << at; }
    /** Whether {@code bits} are a page's rules as this version writes them (a file's other bits are left out). */
    static boolean validOwn(int bits) { return (bits & ~15) == 0 && own(bits, ERASING_BITS) != 3 && own(bits, TIMED_BITS) != 3; }
    /** The rules in force on a page whose own are {@code bits}, in a notebook with {@code options}: the notebook's, each overridden where the page has its own. */
    static NotebookOptions forPage(NotebookOptions options, int bits) { return forPage(options, bits, 0); }
    /** {@link #forPage}, the page's own time {@code seconds} (0: none) its time while its Timed Pages is On. */
    static NotebookOptions forPage(NotebookOptions options, int bits, int seconds) {
        if (options == null) return null;
        int erasing = own(bits, ERASING_BITS), timed = own(bits, TIMED_BITS);
        if (erasing != NOTEBOOKS) options = options.withNoErasing(erasing == OWN_ON);
        if (timed == OWN_OFF) options = options.withSeconds(0);
        else if (timed == OWN_ON && seconds > 0) options = options.withSeconds(seconds);
        else if (timed == OWN_ON && options.seconds == 0) options = options.withSeconds(PAGE_SECONDS);
        return options;
    }

    /** How Timed Pages' choices read on the library card: Off, "90 s", "5 min". */
    static String timeLabel(int seconds) { return seconds == 0 ? OFF : seconds % 60 == 0 ? seconds / 60 + " min" : seconds + " s"; }

    /** The countdown in the side bar's top, m:ss: whole seconds in the last minute, else in tens (so it changes every 10 s). */
    static String countdown(long remaining) {
        long step = countdownStep(remaining), s = (remaining + step - 1) / step * step / 1000;
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }
    /** How long until {@link #countdown} says something else, ms. */
    static long nextCountdown(long remaining) {
        if (remaining <= 0) return Long.MAX_VALUE;
        long step = countdownStep(remaining);
        return remaining - (remaining - 1) / step * step;
    }
    private static long countdownStep(long remaining) { return remaining > 60_000 ? 10_000 : 1000; }

    /** Whether {@code brush} takes paint away as it goes: its Smudge is Lift or Clear Water. */
    static boolean erases(Brush brush) {
        if (brush == null) return true;
        PartChoice carry = brush.parts.choice(BrushPart.CARRY);
        return carry == PartChoice.LIFT || carry == PartChoice.CLEAR_WATER;
    }

    /**
     * Whether a stroke of tool {@code tool} (a slot, {@link ToolSlots#ERASER} or {@link ToolSlots#SMUDGE}) holding {@code brush}
     * (null for the eraser) is refused in a notebook with {@code options}.
     */
    static boolean refuses(NotebookOptions options, int tool, Brush brush) {
        if (options == null) return false;
        if (tool == ToolSlots.ERASER) return options.noErasing;
        if (options.brushes > 0 && (tool == ToolSlots.SMUDGE || tool == ToolSlots.STAMP || tool < ToolSlots.COUNT && tool >= options.brushes)) return true;
        return options.noErasing && brush != null && erases(brush);
    }
    /** Whether the eraser's tile shows disabled. */
    static boolean eraserOff(NotebookOptions options) { return options != null && options.noErasing; }
    /** Whether the smudge tool's (or the stamp tool's) tile shows disabled: Brushes keeps to its slots, and No Erasing refuses a brush that lifts. */
    static boolean smudgeOff(NotebookOptions options, Brush smudge) { return options != null && (options.brushes > 0 || options.noErasing && erases(smudge)); }

    /**
     * The Tools page's note while the notebook's toolbar can't change ({@link NotebookOptions#toolbarFrozen}), else null
     * (the owner, 2026-10-22: "selecting an oil brush in the picker screen doesn't stick ... it should prevent you from
     * picking a new brush and explain"). Its cards are greyed and {@link #toolsSettings} is one tap away.
     */
    static String toolsFixed(NotebookOptions options) {
        return options == null || !options.toolbarFrozen() ? null : TOOLS_EACH_PAGE;
    }
    static final String TOOLS_EACH_PAGE = "Notebook picks tools each page";
    /** The Tools page's button to the setting that frees the toolbar. */
    static final String NOTEBOOK_SETTINGS = "Notebook Settings";
    /** The library card's screen that frees a fixed toolbar: Tools; null when it isn't fixed. */
    static CardSheet.Screen toolsSettings(NotebookOptions options) { return options == null || !options.toolbarFrozen() ? null : CardSheet.Screen.TOOLS; }

    /** Whether Undo and Redo are off (shown disabled, and refused with {@link #UNDO_OFF}): No Erasing is on. */
    static boolean undoOff(NotebookOptions options) { return options != null && options.noErasing; }
    /** Whether a page or layer is kept from Clear and Delete: No Erasing is on and it is {@code marked} (a blank one may go). */
    static boolean keeps(NotebookOptions options, boolean marked) { return options != null && options.noErasing && marked; }
    /** The time Timed Pages gives page {@code page}, s (0: untimed): none for a cover, nor for a page timed by its sections. */
    static int timedSeconds(NotebookOptions options, int page, boolean sectioned) {
        return options == null || DrawingBook.isCover(page) || sectioned ? 0 : options.seconds;
    }

    /** How long before zero a page of {@code seconds} starts blinking, ms. */
    static long blinkWindow(int seconds) { return seconds <= 60 ? BLINK_SHORT_MS : BLINK_MS; }
    /** Whether a page of {@code seconds} with {@code remaining} ms left is in its blink window. */
    static boolean blinks(long remaining, int seconds) { return seconds > 0 && remaining > 0 && remaining <= blinkWindow(seconds); }
    /** Whether the number is hidden now: in the blink window, every other second, starting shown. */
    static boolean numberHidden(long remaining, int seconds) {
        if (!blinks(remaining, seconds)) return false;
        long into = blinkWindow(seconds) - remaining;
        return (into / BLINK_STEP_MS) % 2 == 1;
    }
    /** The next time the blink changes, ms from now, for a page with {@code remaining} left (so the screen is updated only then). */
    static long nextBlink(long remaining, int seconds) {
        long window = blinkWindow(seconds);
        if (remaining > window) return remaining - window;
        long into = window - remaining;
        return BLINK_STEP_MS - into % BLINK_STEP_MS;
    }
}
