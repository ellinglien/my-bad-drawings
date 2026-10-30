package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.PartDial.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The six parts of a modular brush, in share-code order (parts.js 6–14). Never reorder. The labels are what the lab
 * shows (Life, Hand and Carry read Drying, Control and Smudge); the constant names are the codes' and stay. Pure,
 * checked on the desktop.
 */
enum BrushPart {
    TIP("Tip"), MEDIUM("Medium"), CARRY("Smudge"), PAPER("Paper"), LIFE("Drying"), HAND("Control");

    /**
     * The order the lab shows the parts in, reading left to right, top to bottom, two to a row: Tip, Medium, Paper,
     * Drying, Control, Smudge. Only what the lab shows; the codes keep {@link #values()}'s order.
     */
    static final List<BrushPart> SHOWN = Collections.unmodifiableList(Arrays.asList(TIP, MEDIUM, PAPER, LIFE, HAND, CARRY));

    final String label;

    BrushPart(String label) { this.label = label; }

    /** This part's choices, in code order. */
    List<PartChoice> choices() { return Tables.CHOICES.get(this); }

    /** The dials a swap keeps (parts.js 142 KEEPS). */
    Set<PartDial> keeps() { return Tables.KEEPS.get(this); }

    /** Built on first use, so the enums that refer to each other are all made first. */
    private static final class Tables {
        static final Map<BrushPart, List<PartChoice>> CHOICES = new EnumMap<>(BrushPart.class);
        static final Map<BrushPart, Set<PartDial>> KEEPS = new EnumMap<>(BrushPart.class);
        static {
            for (BrushPart p : BrushPart.values()) {
                List<PartChoice> list = new ArrayList<>();
                for (PartChoice c : PartChoice.values()) if (c.part == p) list.add(c);
                CHOICES.put(p, Collections.unmodifiableList(list));
            }
            keep(TIP, EnumSet.of(WEIGHT, TAPER_IN, TAPER_OUT, DRY, WOBBLE, SOFT, ANGLE, FLICK));
            keep(MEDIUM, EnumSet.of(POOL, BLEED, BLOOM, SPLATTER, FLING));
            keep(CARRY, EnumSet.of(PLOAD, PMIN));
            keep(PAPER, EnumSet.of(TOOTH));
            keep(LIFE, EnumSet.of(PartDial.DRYING, RUNS, G_POOL, SINK));
            keep(HAND, EnumSet.of(GAIN, DIR, P_DRIVE, S_DRIVE, T_DRIVE, SHIFT, SHIFT_ORDER, SHIFT_GREYS, SHIFT_BAR, SHIFT_ANY, SHIFT_ANY2, GRADIENT, GRAD_TO, GRAD_LEN, SIDE_SHADE));
        }
        private static void keep(BrushPart p, Set<PartDial> dials) { KEEPS.put(p, Collections.unmodifiableSet(dials)); }
    }
}
