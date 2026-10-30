package io.github.mpdairy.monopaint;

public final class InkWashSizesChecks {
    public static void main(String[] args) {
        presets(); brush();
        System.out.println("PASS: four presets (the old pens' 3/8/16, limits 24/120/220, and the new L between M and the old L, now XL: 11 and 160), saved XS falls back to S; the eraser's tip-to-limit range from pressure and tilt, finger eraser from contact size");
    }
    private static void presets() {
        InkWashSizes.Preset[] all = InkWashSizes.Preset.values();
        check(all.length == 4 && all[0] == InkWashSizes.Preset.SMALL && all[1] == InkWashSizes.Preset.MEDIUM && all[2] == InkWashSizes.Preset.LARGER
                && all[3] == InkWashSizes.Preset.LARGE, "Four presets: S, M, the new L, and the old L (XL now)");
        check(InkWashSizes.Preset.LARGER.label.equals("L") && InkWashSizes.Preset.LARGE.label.equals("XL"), "shown S, M, L, XL");
        int[] ink = {3, 8, 11, 16}, limit = {24, 120, 160, 220};
        for (InkWashSizes.Preset preset : all) {
            check(InkWashSizes.inkSize(preset) == ink[preset.ordinal()], "The old pens' presets are 3 / 8 / 16 px (11 between)");
            check(InkWashSizes.maxSize(preset) == limit[preset.ordinal()], "The eraser's limits (and the old paints') are 24 / 120 / 220 px (160 between)");
        }
        check(InkWashSizes.Preset.saved("EXTRA_SMALL") == InkWashSizes.Preset.SMALL, "A saved XS, retired, falls back to S");
        check(InkWashSizes.Preset.saved("LARGE") == InkWashSizes.Preset.LARGE && InkWashSizes.Preset.saved("SMALL") == InkWashSizes.Preset.SMALL, "Saved presets load as they were");
        check(InkWashSizes.Preset.saved(null) == InkWashSizes.Preset.MEDIUM && InkWashSizes.Preset.saved("HUGE") == InkWashSizes.Preset.MEDIUM, "Nothing or nonsense saved is M");
    }
    private static void brush() {
        float previousReach = 0;
        for (InkWashSizes.Preset preset : InkWashSizes.Preset.values()) {
            float tip = InkWashSizes.diameter(preset, 0f), max = InkWashSizes.maxSize(preset);
            check(tip <= 4 && InkWashSizes.diameter(preset, .05f) <= 4, "A light touch is the fine tip at every preset");
            check(Math.abs(InkWashSizes.diameter(preset, .78f) - max) < .01f && InkWashSizes.diameter(preset, 1f) == InkWashSizes.diameter(preset, .78f), "A hard press reaches the preset maximum and nothing exceeds it");
            check(max > previousReach, "The maximum grows from S to L"); previousReach = max;
            float last = 0;
            for (float pressure = 0; pressure <= .77f; pressure += .01f) {
                float d = InkWashSizes.diameter(preset, pressure);
                check(pressure < .45f ? d >= last : d > last, "Diameter never shrinks as pressure grows, and still grows past .45"); last = d;
            }
            // The owner's hand on firmware 3.31.45 (2026-10-29): light .1, usual .3, firm .6 to .75; a natural ~33° hold no longer counts.
            float light = InkWashSizes.diameter(preset, .1f), usual = InkWashSizes.diameter(preset, .3f), firm = InkWashSizes.diameter(preset, .6f), hard = InkWashSizes.diameter(preset, .75f);
            check(light <= tip + max * .03f, "A light touch stays near the tip: " + light);
            check(usual > max / 3 && usual < max / 2, "A usual press is a third to a half of the preset: " + usual + " of " + max);
            check(firm > max * .7f && hard > max * .85f, "A firm press is most of it, a hard one nearly all: " + firm + ", " + hard);
            check(InkWashSizes.diameter(preset, Float.NaN) == tip, "Missing readings give the tip");
            check(InkWashSizes.typical(preset) == max, "The size dot shows the preset's upper limit");
        }
        check(ToolSettings.travel(.05f) == 0 && ToolSettings.travel(.78f) == 1 && ToolSettings.travel(.9f) == 1 && Math.abs(ToolSettings.travel(.25f) - .5f) < 1e-4, "Measured pen range maps to 0..1");
        // The knee (firmware 3.31.45, 2026-10-29): up to .30 exactly MonoPaint's .05–.45 line; past it still growing to .78.
        for (int i = 0; i <= 300; i++) { float p = i / 1000f; check(ToolSettings.travel(p) == Math.max(0, Math.min(1, (p - .05f) / .40f)), "A usual press keeps its old travel at " + p); }
        check(Math.abs(ToolSettings.travel(.30f) - .625f) < 1e-5 && Math.abs(ToolSettings.travel(.3001f) - .625f) < 1e-3, "The knee is continuous at .30");
        check(ToolSettings.travel(.45f) < ToolSettings.travel(.6f) && ToolSettings.travel(.6f) < ToolSettings.travel(.78f), "Pressing past .45 keeps growing travel");
        for (int i = 0; i <= 100; i++) {
            float t = i / 100f, p = i / 100f;
            check(Math.abs(ToolSettings.travel(ToolSettings.pressure(t)) - t) < 1e-5, "pressure() inverts travel() at travel " + t);
            if (p >= .05f && p <= .78f) check(Math.abs(ToolSettings.pressure(ToolSettings.travel(p)) - p) < 1e-5, "travel() inverts pressure() at " + p);
        }
        check(ToolSettings.pressure(1) == .78f && Math.abs(ToolSettings.pressure((float) WildSample.TYPICAL_PRESS) - .29f) < 1e-5, "A full press is .78; the usual hand still presses .29");
        check(InkWashSizes.fingerEraser(10) == InkWashSizes.FINGER_NARROWEST, "A stylus tip erases at the narrowest finger size");
        check(Math.abs(InkWashSizes.fingerEraser(60) - 54) < .01f, "A fingertip erases about as wide as it touches");
        check(InkWashSizes.fingerEraser(112) == InkWashSizes.FINGER_BROADEST && InkWashSizes.fingerEraser(400) == InkWashSizes.FINGER_BROADEST, "A broad contact is capped");
        float unread = InkWashSizes.fingerEraser(Float.NaN);
        check(unread > InkWashSizes.FINGER_NARROWEST && unread < InkWashSizes.FINGER_BROADEST && InkWashSizes.fingerEraser(0) == unread, "Without a contact reading a finger erases at a fingertip's size");
    }
    private static boolean near(float a, float b) { return Math.abs(a - b) < .01f; }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
