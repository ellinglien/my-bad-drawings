package io.github.mpdairy.monopaint;

public final class FingerToolChecks {
    public static void main(String[] args) {
        choices(); refused(); cancels(); starts(); palms();
        System.out.println("PASS: one finger does Nothing, Eraser (what it always did, the choice at first), Smudge or a slot's brush, kept under its key; it never draws while the pen is down or near, for a palm-sized contact or before it moves; a second finger, the pen landing or a contact swelling to a palm's takes it back, a pressing fingertip's stroke kept");
    }
    static void choices() {
        FingerTool.Choice[] all = FingerTool.Choice.values();
        check(all.length == 3 + ToolSlots.COUNT, "Nothing, Eraser, Smudge and the ten slots");
        check(FingerTool.Choice.NOTHING.tool() == -1 && FingerTool.Choice.ERASER.tool() == ToolSlots.ERASER && FingerTool.Choice.SMUDGE.tool() == ToolSlots.SMUDGE,
                "Nothing draws nothing; Eraser and Smudge draw with the bar's eraser and smudge tool");
        for (int slot = 0; slot < ToolSlots.COUNT; slot++) {
            FingerTool.Choice c = all[3 + slot];
            // No numbers (the owner, 2026-10-19): a slot's choice is called by the brush in it.
            int s = slot;
            check(c.tool() == slot && c.slot() && c.label(i -> i == s ? "Brush pen" : "?").equals("Brush pen") && c.key.equals("slot" + (slot + 1)),
                    "Slot " + (slot + 1) + " draws with that slot's brush, and is called by it");
            check(!c.label(i -> "Fineliner").matches(".*[0-9].*") && c.row(i -> "Fineliner").equals("Finger Action: Fineliner"), "No slot number in its name or its menu row");
        }
        check(FingerTool.Choice.saved(null) == FingerTool.Choice.ERASER && FingerTool.Choice.saved("") == FingerTool.Choice.ERASER && FingerTool.Choice.saved("thumb") == FingerTool.Choice.ERASER,
                "At first (or a key that doesn't read) a finger erases, as it always did");
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (FingerTool.Choice c : all) {
            check(FingerTool.Choice.saved(c.key) == c && keys.add(c.key), c + " is kept under a key of its own");
            if (c.slot()) continue;
            String label = c.label(i -> null);
            check(label.split(" ").length <= 2 && label.matches("[A-Z][a-z]+"), label + ": a button's two words at most, sentence case, no symbols (docs/TEXT-STYLE.md)");
            check(c.row(i -> null).equals("Finger Action: " + label) && c.row(i -> null).split(" ").length <= 4, c.row(i -> null) + ": the menu's row, four words at most");
        }
    }
    static void refused() {
        float tip = 60;
        for (FingerTool.Choice c : FingerTool.Choice.values()) {
            boolean draws = c != FingerTool.Choice.NOTHING;
            check(FingerTool.refused(c, true, false, false, tip) != draws, c + ": a fingertip " + (draws ? "draws" : "is refused"));
            check(FingerTool.refused(c, false, false, false, tip), c + ": refused with the pen among the touches");
            check(FingerTool.refused(c, true, true, false, tip), c + ": refused while the pen is down or was near a moment ago");
            check(FingerTool.refused(c, true, false, true, tip), c + ": refused while the app is busy");
        }
    }
    static void cancels() {
        check(!FingerTool.cancels(false, true, 60, false) && !FingerTool.cancels(false, true, 60, true), "A fingertip alone carries on");
        check(FingerTool.cancels(true, true, 60, false) && FingerTool.cancels(true, true, 60, true), "A second finger (a tap's) takes it back");
        check(FingerTool.cancels(false, false, 60, true), "The pen landing takes it back (a palm that was there first)");
        check(FingerTool.cancels(false, true, 100, false) && !FingerTool.cancels(false, true, 100, true), "Before drawing, a 100 dp contact is a palm; a pressing fingertip that swells to it mid-stroke is kept");
        check(FingerTool.cancels(false, true, 120, true), "A contact as broad as a hand takes back what it drew");
        check(!FingerTool.keepsOnCancel(FingerTool.KEEP_MS - 1) && FingerTool.keepsOnCancel(FingerTool.KEEP_MS) && FingerTool.keepsOnCancel(2300), "The pen coming near cancels the touch: a stroke of " + FingerTool.KEEP_MS + " ms or more is kept, a palm's brush taken back");
    }
    static void starts() {
        check(!FingerTool.starts(0) && !FingerTool.starts(FingerTool.SLOP_DP) && FingerTool.starts(FingerTool.SLOP_DP + .5f), "A finger draws only once it moves past " + FingerTool.SLOP_DP + " dp");
    }
    static void palms() {
        // The owner's logcat: fingertips land at 51 to 77 dp and swell to 85 as they rub; resting hands land at 94 to 128.
        for (FingerTool.Choice c : FingerTool.Choice.values()) {
            for (float tip = 51; tip <= 77; tip++) check(c == FingerTool.Choice.NOTHING || !FingerTool.refused(c, true, false, false, tip), c + ": a " + tip + " dp fingertip draws");
            for (float palm = 94; palm <= 128; palm++) check(FingerTool.refused(c, true, false, false, palm), c + ": a " + palm + " dp palm never draws");
            for (float swell = 51; swell <= 85; swell++) check(!FingerTool.cancels(false, true, swell, true), c + ": a fingertip swelling to " + swell + " dp keeps drawing");
        }
        check(ToolSettings.pressure(FingerTool.PRESSURE_TRAVEL) > ToolSettings.pressure(0) && ToolSettings.pressure(FingerTool.PRESSURE_TRAVEL) < ToolSettings.pressure(1),
                "A finger's brush draws at a medium press");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
