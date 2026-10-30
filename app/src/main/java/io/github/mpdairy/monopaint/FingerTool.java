package io.github.mpdairy.monopaint;

import java.util.function.IntFunction;

/**
 * What one finger does on the page, chosen from the main menu's Finger row and kept: nothing, the eraser
 * (what a finger always did, sized by the fingertip), the smudge tool, or a slot's brush at the slot's size and grey. Two and
 * three fingers keep their taps (Undo, Redo). And when a finger may draw at all: never while the pen is down or near
 * (a palm resting with the pen), never a palm-sized contact, and only once it moves, so a two- or three-finger tap's
 * first finger lays nothing; a second finger, the pen arriving or a contact swelling to a palm's takes back what it
 * drew. Pure, checked on the desktop ({@code FingerToolChecks}).
 */
final class FingerTool {
    /** Where the choice is kept (the app's preferences). */
    static final String KEY = "inkwash_finger";
    /**
     * Palm thresholds, dp, from the owner's logcat: fingertips land at 51 to 77 dp and swell to 85 dp as they rub; resting
     * hands land at 94 to 128 dp. Once drawing only a contact as broad as a whole hand counts as a palm, or a pressing
     * fingertip's stroke would come undone mid-way.
     */
    static final float PALM_DP = 90, PALM_DRAWING_DP = 115;
    /** How far a finger moves before it draws, dp: a tap's first finger stays within it. */
    static final float SLOP_DP = 8;
    /** The pressure a finger draws a brush at (a fingertip has none): a medium press's ({@link ToolSettings#pressure}). */
    static final float PRESSURE_TRAVEL = .5f;

    /**
     * The choices, in the menu's order. Keys are kept: never change them. A slot has no label of its own: the slots
     * have no numbers (the owner, 2026-10-19), so it is shown as its brush, by name ({@link #label(IntFunction)}) and
     * by its tray stroke beside it.
     */
    enum Choice {
        NOTHING("nothing", "Nothing"), ERASER("eraser", "Eraser"), SMUDGE("smudge", "Smudge"),
        SLOT_1("slot1", null), SLOT_2("slot2", null), SLOT_3("slot3", null),
        SLOT_4("slot4", null), SLOT_5("slot5", null), SLOT_6("slot6", null), SLOT_7("slot7", null), SLOT_8("slot8", null),
        SLOT_9("slot9", null), SLOT_10("slot10", null);
        final String key;
        /** Nothing's, Eraser's and Smudge's label; null for a slot. */
        private final String label;
        Choice(String key, String label) { this.key = key; this.label = label; }
        /** Whether it draws with a slot's brush. */
        boolean slot() { return label == null; }
        /** What it is called: its label, or a slot's brush's name, {@code brushName} of the slot. */
        String label(IntFunction<String> brushName) { return slot() ? brushName.apply(tool()) : label; }
        /** What Finger Action says, and its menu row: "Finger Action: Eraser", "Finger Action: Brush pen" ("Finger" until the owner, 2026-10-29). */
        String row(IntFunction<String> brushName) { return "Finger Action: " + label(brushName); }
        /** The bar's tool the finger draws with ({@link ToolSlots#ERASER}, {@link ToolSlots#SMUDGE} or a slot); -1 for nothing. */
        int tool() {
            switch (this) {
                case NOTHING: return -1;
                case ERASER: return ToolSlots.ERASER;
                case SMUDGE: return ToolSlots.SMUDGE;
                default: return ordinal() - SLOT_1.ordinal();
            }
        }
        /** The choice kept as {@code key}; the eraser (what a finger always did) for none or one that doesn't read. */
        static Choice saved(String key) {
            for (Choice c : values()) if (c.key.equals(key)) return c;
            return ERASER;
        }
    }

    private FingerTool() { }

    /**
     * Whether a finger landing is refused for the whole touch: the choice is Nothing, a pen is among the touches, the pen
     * is down or was near a moment ago or something else holds the page ({@code blocked}), the app is busy, or a contact
     * is {@code widestDp} across, a palm's.
     */
    static boolean refused(Choice choice, boolean fingersOnly, boolean blocked, boolean busy, float widestDp) {
        return choice == Choice.NOTHING || !fingersOnly || blocked || busy || widestDp > PALM_DP;
    }
    /**
     * Whether this event takes back the finger's touch (what it drew, if it is {@code drawing}): another finger landing
     * (a tap's), the pen landing among the touches (a palm that was there first), or a contact grown to a palm's.
     */
    static boolean cancels(boolean anotherFinger, boolean fingersOnly, float widestDp, boolean drawing) {
        return anotherFinger || !fingersOnly || widestDp > (drawing ? PALM_DRAWING_DP : PALM_DP);
    }
    /**
     * Whether a system cancel keeps what the finger drew: the Supernote cancels every touch when the pen comes near
     * (the owner, 2026-10-23: "had some stuff reappear on screen after erasing with my finger"), so a stroke that has
     * gone on {@link #KEEP_MS} or more is kept, and only a shorter one, a brush of a palm, is taken back.
     */
    static boolean keepsOnCancel(long heldMs) { return heldMs >= KEEP_MS; }
    static final long KEEP_MS = 400;
    /** Whether a finger {@code movedDp} from where it landed starts drawing. */
    static boolean starts(float movedDp) { return movedDp > SLOP_DP; }
}
