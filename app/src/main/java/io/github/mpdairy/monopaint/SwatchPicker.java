package io.github.mpdairy.monopaint;

import java.util.Random;

/**
 * Grey Shift (mischief-lab-2.html's Shifting swatches, section 5; the owner's "unpredictable swatch selection on
 * pen touch ... shifting randomly, or from a set, or following a pattern"): a brush whose Control has it on draws each
 * touch in a grey picked at the touch-down from a set, by its rule: Random (repeats allowed), From a Set (a shuffled
 * deck, each grey once before any again, never the same grey twice across a reshuffle) or Pattern, In Order or
 * Ping-Pong (1 2 3 4 3 2 …). The greys on offer ("completely open, or one of the toolbar ones") are Toolbar, the bar's
 * grey swatches as they are now ({@link ToolSwatches#grey}, the default), or Any, the panel's 16 (paper white to black).
 * Each keeps its own set in the hand's dials ({@link PartDial#SHIFTS}), as the greys left out, so a brush carries it.
 * Where a holder is in its turn ({@link State}) is kept per holder in {@link HeldPaint}, while the app runs. Pure,
 * checked on the desktop ({@code SwatchChecks}).
 */
final class SwatchPicker {
    /** Grey Shift's options: Off, Random, From a Set, Pattern. */
    static final int OFF = 0, RANDOM = 1, DECK = 2, PATTERN = 3;
    /** Pattern's Order: In Order, Ping-Pong. */
    static final int IN_ORDER = 0, PING_PONG = 1;
    /** Greys: the toolbar's grey swatches, or Any of the panel's. */
    static final int TOOLBAR = 0, ANY = 1;
    /** Any's greys: the panel's 16 levels, paper white (0) to black (15), a darkness of {@code k / 15} each. */
    static final int LEVELS = 16;

    private SwatchPicker() { }

    /**
     * A holder's place in its turn: the last grey picked (an index into the palette, -1 for none yet), Pattern's step,
     * and what is left of From a Set's deck, dealt from its end; all begun again when the rule, order, palette or set
     * changes. Its own random numbers.
     */
    static final class State {
        final Random random;
        int last = -1, step = -1, left;
        int[] deck = new int[LEVELS];
        /** The rule, order, palette and set it was in, packed; -1 for none yet. */
        long shape = -1;
        State(Random random) { this.random = random; }
    }

    /** {@code b}'s Grey Shift: {@link #OFF}, {@link #RANDOM}, {@link #DECK} or {@link #PATTERN}. */
    static int rule(ModularBrush b) { return (int) b.part(BrushPart.HAND).get(PartDial.SHIFT, OFF); }
    /** Whether {@code b} picks its grey at each touch. */
    static boolean shifts(ModularBrush b) { return rule(b) != OFF; }
    /** {@code b}'s greys: {@link #TOOLBAR} or {@link #ANY}. */
    static int palette(ModularBrush b) { return (int) b.part(BrushPart.HAND).get(PartDial.SHIFT_GREYS, TOOLBAR); }
    /** How many greys {@code palette} offers: the toolbar's swatches, or the panel's 16. */
    static int size(int palette) { return palette == ANY ? LEVELS : ToolSwatches.COUNT; }
    /** The greys of {@code hand}'s {@code palette} left out of its set, a bit each. */
    static int out(ModularBrush.Part hand, int palette) {
        if (palette != ANY) return (int) Math.round(hand.get(PartDial.SHIFT_BAR, 0)) & 0xf;
        return ((int) Math.round(hand.get(PartDial.SHIFT_ANY, 1)) & 0xff) | ((int) Math.round(hand.get(PartDial.SHIFT_ANY2, 0)) & 0xff) << 8;
    }
    /** Whether grey {@code k} of {@code b}'s palette is in its set; with every grey left out (a hand set by hand), all are. */
    static boolean in(ModularBrush b, int k) {
        int palette = palette(b), out = out(b.part(BrushPart.HAND), palette), all = (1 << size(palette)) - 1;
        return (out & all) == all || (out >> k & 1) == 0;
    }
    /** The greys of {@code b}'s set, in palette order. */
    static int[] set(ModularBrush b) {
        int n = size(palette(b)), count = 0;
        int[] on = new int[n];
        for (int k = 0; k < n; k++) if (in(b, k)) on[count++] = k;
        return java.util.Arrays.copyOf(on, count);
    }
    /**
     * {@code hand} with grey {@code k} of {@code palette} taken into its set, or out of it; false (and {@code hand} as
     * it was) when that would leave the set empty.
     */
    static boolean toggle(ModularBrush.Part hand, int palette, int k) {
        int n = size(palette), all = (1 << n) - 1, out = out(hand, palette);
        if ((out & all) == all) out = 0;
        int next = out ^ 1 << k;
        if ((next & all) == all) return false;
        if (palette != ANY) hand.set(PartDial.SHIFT_BAR, next);
        else { hand.set(PartDial.SHIFT_ANY, next & 0xff); hand.set(PartDial.SHIFT_ANY2, next >> 8 & 0xff); }
        return true;
    }

    /**
     * The grey {@code b} picks at this touch, an index into its palette, {@code state}'s turn moving on; -1 for a brush
     * that doesn't shift (mischief-lab-2.html pick5).
     */
    static int pick(ModularBrush b, State state) {
        int rule = rule(b);
        if (rule == OFF) return -1;
        int palette = palette(b), order = (int) b.part(BrushPart.HAND).get(PartDial.SHIFT_ORDER, IN_ORDER);
        int[] on = set(b);
        int n = on.length;
        long shape = rule | order << 2 | palette << 3 | (long) out(b.part(BrushPart.HAND), palette) << 4;
        if (shape != state.shape) { state.shape = shape; state.step = -1; state.left = 0; }
        int k;
        if (rule == RANDOM) k = on[state.random.nextInt(n)];
        else if (rule == DECK) {
            if (state.left == 0) {
                int[] deck = state.deck;
                System.arraycopy(on, 0, deck, 0, n);
                for (int i = n - 1; i > 0; i--) { int j = state.random.nextInt(i + 1), t = deck[i]; deck[i] = deck[j]; deck[j] = t; }
                // Never the same grey twice across a reshuffle: the deck is dealt from its end.
                if (n > 1 && deck[n - 1] == state.last) { int t = deck[0]; deck[0] = deck[n - 1]; deck[n - 1] = t; }
                state.left = n;
            }
            k = state.deck[--state.left];
        } else {
            int period = order == PING_PONG ? Math.max(1, 2 * n - 2) : n;
            state.step = (state.step + 1) % period;
            k = on[state.step < n ? state.step : period - state.step];
        }
        state.last = k;
        return k;
    }
    /**
     * The darkness grey {@code k} of {@code b}'s palette stands for: the toolbar's swatch as it is ({@code swatches};
     * null, its first greys, {@link ToolSwatches#GREYS}), or Any's level, {@code k / 15}.
     */
    static double darkness(ModularBrush b, ToolSwatches swatches, int k) {
        if (palette(b) == ANY) return k / (double) (LEVELS - 1);
        return swatches != null ? swatches.grey(k) : ToolSwatches.GREYS[k];
    }
    /**
     * {@code look} at the grey {@code brush} picks at this touch, {@code held}'s turn for {@code holder} moving on;
     * {@code look} itself for the eraser (null) or a brush that doesn't shift.
     */
    static ToolSwatches.Look look(Brush brush, ToolSwatches.Look look, ToolSwatches swatches, HeldPaint held, String holder) {
        int k = brush == null ? -1 : pick(brush.parts, held.shifting(holder));
        return k < 0 ? look : at(look, darkness(brush.parts, swatches, k));
    }
    /** {@code look} at {@code darkness}. */
    static ToolSwatches.Look at(ToolSwatches.Look look, double darkness) { return new ToolSwatches.Look(look.preset, look.px, darkness); }

    /** "This brush"'s sentence for Grey Shift, by its rule; null when it is off. */
    static String words(ModularBrush b) {
        switch (rule(b)) {
            case RANDOM: return "Greys shift each stroke at random.";
            case DECK: return "Greys shift each stroke, shuffled.";
            case PATTERN: return (int) b.part(BrushPart.HAND).get(PartDial.SHIFT_ORDER, IN_ORDER) == PING_PONG
                    ? "Greys shift each stroke, there and back." : "Greys shift each stroke, in order.";
            default: return null;
        }
    }
}
