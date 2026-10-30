package io.github.mpdairy.monopaint;

/**
 * The eraser's four sizes, which broaden as the pen presses harder, and the finger eraser's;
 * the old pens' and paints' sizes, which converted slots keep. Brushes have their own px ({@link ToolSwatches}).
 * Sizes are page pixels on the Manta's 300 dpi panel.
 */
final class InkWashSizes {
    /**
     * The four sizes in the order the bar shows them, each also a size swatch's place ({@link ToolSwatches}). Saved by
     * name, so a name never changes; the order may. {@link #LARGER} came in 2026-10-19 (the owner's fourth size) between
     * M and the old L, which keeps its name and what it draws, shown as XL now.
     */
    enum Preset {
        SMALL("S"), MEDIUM("M"), LARGER("L"), LARGE("XL");
        final String label;
        Preset(String label) { this.label = label; }
        /** The preset saved as {@code name}: the retired extra small is small, and none or an unknown name medium. */
        static Preset saved(String name) {
            if ("EXTRA_SMALL".equals(name)) return SMALL;
            for (Preset preset : values()) if (preset.name().equals(name)) return preset;
            return MEDIUM;
        }
    }
    /**
     * The old Ink and Fude pens' widths at each preset, which a slot converted from them keeps ({@link LegacyBrushes});
     * {@link Preset#LARGER}'s, which no old pen had, between M's and XL's on a log scale.
     */
    private static final int[] INK = {3, 8, 11, 16};
    /** Every eraser stroke starts this fine. */
    static final int TIP = 2;
    /**
     * The broadest eraser diameter per preset (and the old wash's and watercolor's): the preset is the upper limit.
     * {@link Preset#LARGER}'s 160 is between M's 120 and XL's 220 on a log scale (162), as the brushes' fourth size is.
     */
    private static final int[] MAX = {24, 120, 160, 220};

    /** The old pens' width at the preset. */
    static int inkSize(Preset preset) { return INK[preset.ordinal()]; }

    /** The broadest eraser diameter of the preset (and the old wash's and watercolor's). */
    static int maxSize(Preset preset) { return MAX[preset.ordinal()]; }

    /**
     * The eraser's diameter, from the tip to the preset's limit, by pressure alone: a light touch is the tip, a usual
     * press under half, a hard press the limit. The response is curved, so light pressure stays fine for longer.
     * Tilt no longer broadens it (2026-10-29): the owner's natural hold leans about 33°, which had it at its limit from
     * a usual press, so pressing harder seemed to do nothing ("pressure for the eraser doesn't seem to affect the size").
     */
    static float diameter(Preset preset, float pressure) { return diameter(MAX[preset.ordinal()], pressure); }
    /** As above, up to {@code most} px (the current size, {@link ToolSwatches#useSize}). */
    static float diameter(float most, float pressure) {
        return TIP + (most - TIP) * (float)Math.pow(ToolSettings.travel(pressure), 2);
    }

    /** What the eraser's size dots show: the preset's upper limit. */
    static float typical(Preset preset) { return maxSize(preset); }

    /** The finger eraser's narrowest and broadest diameters, and its share of the contact's width. */
    static final float FINGER_NARROWEST = 12, FINGER_BROADEST = 90, FINGER_SHARE = .9f;

    /**
     * The finger eraser's diameter for a contact {@code contact} page pixels across (the touch's
     * major axis): a capacitive stylus tip erases finely, a fingertip broadly. Without a reading,
     * the middle preset at medium pressure.
     */
    static float fingerEraser(float contact) {
        if (!(contact > 0)) return diameter(Preset.MEDIUM, .25f);
        return Math.max(FINGER_NARROWEST, Math.min(FINGER_BROADEST, contact * FINGER_SHARE));
    }

    private InkWashSizes() {}
}
