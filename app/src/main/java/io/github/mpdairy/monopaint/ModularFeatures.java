package io.github.mpdairy.monopaint;

/**
 * What the engine draws. A deferred choice decodes and is kept, but rolls, dice, swaps and variations never pick it
 * (spec, decision 4). Carry (Smudge: Lift, Drag, Smear) and Bristles draw (Stage 3); plumes and graphite's Fill plane
 * wait. Pure, checked on the desktop.
 */
final class ModularFeatures {
    static final boolean BRISTLES = true, CARRY = true, PLUMES = false;
    /** Float (suminagashi): offered in Brush Lab, rolled rarely and only from the seed ({@link ModularStyle#rollFloat}), never a variation's swap. */
    static final boolean FLOAT = true;

    /** Whether rolls, dice, swaps and variations may pick {@code c}; never Hand Hatch, cut (2026-10-24: its codes read as Hatch, {@link ModularCode#asHatch}). */
    static boolean offered(PartChoice c) {
        return c != PartChoice.HAND_HATCH && !(c == PartChoice.BRISTLES && !BRISTLES) && !(c.part == BrushPart.CARRY && c != PartChoice.OFF && !CARRY) && !(c == PartChoice.FLOAT && !FLOAT);
    }
    /** Whether a variation may swap to {@code c}: offered, and not one only a roll from the seed picks. */
    static boolean varies(PartChoice c) { return offered(c) && !seedOnly(c); }
    /**
     * The rare choices only a brush's roll picks, from its seed after every other part ({@link ModularStyle#rollFloat},
     * {@link ModularStyle#rollPattern}, {@link ModularStyle#rollBlend}, {@link ModularStyle#rollHalo}, {@link ModularStyle#rollTone}, {@link ModularStyle#rollClearWater}, {@link ModularStyle#rollFilbert}, {@link ModularStyle#rollFade}): Float, Stipple, Hatch, Resist, Halo, Tone, Smudge's Clear Water, the Filbert, Fading and Lemon Juice; and the Stamp, Crayon, the Spiro, Stay in Lines, Filled Shape, Hatching, Wet and the photo papers (Cotton to Fine Speck, {@link #photoPaper}), which no roll picks. No style weighs
     * them, so no part's roll or die picks them, and a variation never swaps to one, so a brush never turns odd by surprise.
     */
    static boolean seedOnly(PartChoice c) { return c == PartChoice.FLOAT || c == PartChoice.STIPPLE || c == PartChoice.HATCH || c == PartChoice.RESIST || c == PartChoice.HALO || c == PartChoice.TONE || c == PartChoice.CLEAR_WATER || c == PartChoice.FILBERT || c == PartChoice.FADE || c == PartChoice.LEMON || c == PartChoice.STAMP || c == PartChoice.CRAYON || c == PartChoice.SPIRO || c == PartChoice.STAY_IN || c == PartChoice.SHAPE || c == PartChoice.HAND_HATCH || c == PartChoice.WET_PAINT || photoPaper(c); }
    /** The owner's eight photo papers (Paper choices 6 to 13): added after the rolls, so no roll or variation of an older brush changes. */
    static boolean photoPaper(PartChoice c) { return c.part == BrushPart.PAPER && c.ordinal() >= PartChoice.COTTON.ordinal(); }
    /**
     * Whether rolls draw {@code c} after every part rather than with its part's other choices: a choice that joined
     * after Stage 1 (Bristles; Smudge's Lift, Drag and Smear), so a style that offers it rolls every other part, and
     * every other choice, as it did before ({@link ModularStyle#roll}); its odds are its weight over the part's, as
     * the prototype's.
     */
    static boolean late(PartChoice c) { return c == PartChoice.BRISTLES || c.part == BrushPart.CARRY && c != PartChoice.OFF && !seedOnly(c); }
    /** Whether the lab shows {@code d}: Life's Takes ink and Plume wait for plumes to draw. */
    static boolean shown(PartDial d) { return PLUMES || d != PartDial.TAKES && d != PartDial.PLUME; }

    private ModularFeatures() { }
}
