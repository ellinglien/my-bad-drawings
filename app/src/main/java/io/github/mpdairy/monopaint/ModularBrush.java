package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A six-part brush: a name, a 32-bit seed (read unsigned), a style, and per part one choice and its dials
 * (parts.js 134–168, 386–390). Treat a Brush's as a value; change a copy. Pure, checked on the desktop.
 */
final class ModularBrush {
    /** One part: a choice and its dials' values, in the choice's dial order. */
    static final class Part {
        final PartChoice choice;
        private final double[] values;

        /**
         * Every dial at the choice's default; a hand preset sets its cells (parts.js freshPart 135–140). Not clamped,
         * as freshPart: a fresh hake's angle is 75, past the dial's 70, until normalized or written.
         */
        Part(PartChoice choice) {
            this.choice = choice;
            List<PartDial> dials = choice.dials;
            values = new double[dials.size()];
            for (int i = 0; i < values.length; i++) values[i] = choice.defaultOf(dials.get(i));
            if (choice.preset()) for (PartDial cell : PartDial.HAND_CELLS) values[dials.indexOf(cell)] = choice.cell(cell);
        }
        private Part(PartChoice choice, double[] values) { this.choice = choice; this.values = values; }

        boolean has(PartDial d) { return choice.has(d); }
        /** The value; 0 for a dial the choice doesn't have (parts.js val). */
        double get(PartDial d) { return get(d, 0); }
        /** The value; {@code absent} for a dial the choice doesn't have (the prototype's {@code ?? fallback}). */
        double get(PartDial d, double absent) { int i = choice.dials.indexOf(d); return i < 0 ? absent : values[i]; }
        /** Sets a dial the choice has. */
        void set(PartDial d, double v) {
            int i = choice.dials.indexOf(d);
            if (i < 0) throw new IllegalArgumentException(choice + " has no " + d);
            values[i] = v;
        }
        /**
         * parts.js normPart (150–163): a value that isn't a finite number takes the default; a choice dial rounds
         * into its options; others clamp to the choice's range; integer dials round. Returns this.
         */
        Part normalize() {
            for (int i = 0; i < values.length; i++) {
                PartDial d = choice.dials.get(i);
                double[] r = choice.range(d);
                double v = values[i];
                if (Double.isNaN(v) || Double.isInfinite(v)) v = choice.defaultOf(d);
                v = d.choice() ? StrokeMath.clamp(Math.round(v), 0, d.choices.length - 1) : StrokeMath.clamp(v, r[0], r[1]);
                if (d.integer) v = Math.round(v);
                values[i] = v;
            }
            return this;
        }
        Part copy() { return new Part(choice, values.clone()); }
        /** The same choice and every value equal. */
        boolean sameAs(Part other) { return other != null && choice == other.choice && Arrays.equals(values, other.values); }
    }

    String name;
    final long seed;
    /**
     * A Stamp tip's nib ({@link StampNib#id}, kept in the nib store), or 0 for none: a round stamp. The brush's, so a
     * tip swapped away and back keeps it; written in its code only with a Stamp tip ({@link ModularCode}).
     */
    int nib;
    /** Never null: its code begins with it (see {@link ModularCode#encode}). */
    final ModularStyle style;
    private final Part[] parts = new Part[BrushPart.values().length];

    /** Every part its first choice, fresh. */
    ModularBrush(String name, long seed, ModularStyle style) {
        this.name = name; this.seed = seed & 0xffffffffL; this.style = Objects.requireNonNull(style, "a six-part brush's style");
        for (BrushPart p : BrushPart.values()) parts[p.ordinal()] = new Part(p.choices().get(0));
    }

    Part part(BrushPart p) { return parts[p.ordinal()]; }
    PartChoice choice(BrushPart p) { return parts[p.ordinal()].choice; }
    /** Puts {@code part} in its own part's place. */
    void set(BrushPart p, Part part) {
        if (part.choice.part != p) throw new IllegalArgumentException(part.choice + " isn't a " + p + " choice");
        parts[p.ordinal()] = part;
    }

    /**
     * Whether {@code life} moves with the page's tilt while wet: Slow with a gravity dial up. Such a brush dries over the
     * Gravity Lab's longer range ({@link PartDial#wetMs(double, boolean)}).
     */
    static boolean runs(Part life) {
        if (life.choice != PartChoice.DRYING) return false;
        for (PartDial d : PartDial.GRAVITY) if (life.get(d) > 0) return true;
        return false;
    }
    /** How long its Slow paint stays wet, ms ({@link PartDial#wetMs(double, boolean)}). */
    long dryingMs() { Part life = part(BrushPart.LIFE); return PartDial.wetMs(life.get(PartDial.DRYING, .35), runs(life)); }

    /** parts.js handCell: the hand's cell in use, its amount times the response, at most 1. */
    double handCell(PartDial cell) { Part hand = part(BrushPart.HAND); return Math.min(1, or0(hand.get(cell)) * hand.get(PartDial.GAIN, 1)); }

    /** Whether its tip is a Stamp, which prints its {@link #nib}. */
    boolean stamps() { return choice(BrushPart.TIP) == PartChoice.STAMP; }
    /** A copy whose Stamp prints {@code nib} ({@link StampNib#keep}). */
    ModularBrush withNib(int nib) { ModularBrush b = copy(); b.nib = nib; return b; }

    /** What makes two brushes draw alike: its parts, seed and a Stamp's nib, not its name or style (parts.js brushKey). */
    String key() { return ModularCode.settings(this); }

    /**
     * Smudge's Own paint ({@code carry}, the brush's Smudge part): the share of its own paint the brush lays. 1 for Off,
     * 0 for Clear Water, which lays only water; else the dial (parts.js ownPaint).
     */
    static double ownPaint(Part carry) {
        return carry.choice == PartChoice.OFF ? 1 : carry.choice == PartChoice.CLEAR_WATER ? 0 : carry.get(PartDial.LOAD, 1);
    }
    /** parts.js normBrush: every part normalized. Returns this. */
    ModularBrush normalize() { for (Part p : parts) p.normalize(); return this; }
    ModularBrush copy() { return copy(seed); }
    /** A copy with another seed. */
    ModularBrush copy(long seed) {
        ModularBrush b = new ModularBrush(name, seed, style);
        for (int i = 0; i < parts.length; i++) b.parts[i] = parts[i].copy();
        b.nib = nib;
        return b;
    }

    /**
     * parts.js switchChoice (143–149): {@code to}'s fresh part, keeping {@code old}'s values for the dials that mean
     * the same in both ({@code to.part.keeps()}; a paper's tooth not when the old paper was plain); a chisel's,
     * flat nib's or split nib's aspect when it is inside the new range; every value for a Custom hand.
     */
    static Part switchTo(Part old, PartChoice to) {
        Part out = new Part(to);
        if (old == null) return out;
        if (old.choice.part != to.part) throw new IllegalArgumentException(old.choice + " and " + to + " are different parts");
        BrushPart part = to.part;
        for (PartDial d : part.keeps())
            if (out.has(d) && old.has(d) && !(part == BrushPart.PAPER && old.choice == PartChoice.PLAIN)) out.set(d, old.get(d));
        if (to == PartChoice.CHISEL || to == PartChoice.FLAT_NIB || to == PartChoice.SPLIT_NIB) {
            double[] r = to.range(PartDial.ASPECT);
            if (old.has(PartDial.ASPECT) && old.get(PartDial.ASPECT) >= r[0] && old.get(PartDial.ASPECT) <= r[1]) out.set(PartDial.ASPECT, old.get(PartDial.ASPECT));
        }
        if (to == PartChoice.CUSTOM) for (PartDial d : old.choice.dials) if (out.has(d)) out.set(d, old.get(d));
        // A medium chosen keeps the Erasable it had (parts.js switchChoice), 1 from or to Resist (whose dial is wax or fluid).
        if (out.has(PartDial.ERASABLE)) out.set(PartDial.ERASABLE, old.has(PartDial.ERASABLE) && old.choice != PartChoice.RESIST && to != PartChoice.RESIST ? old.get(PartDial.ERASABLE) : to.erasable());
        return out;
    }

    /** parts.js matchHand: the first preset whose cells (the twelve, page tilt's off in every preset) are all within .005 of {@code hand}'s, else Custom. */
    static PartChoice matchHand(Part hand) {
        for (PartChoice c : BrushPart.HAND.choices()) {
            if (!c.preset()) continue;
            boolean all = true;
            for (PartDial cell : PartDial.GRID_CELLS) if (!(Math.abs(or0(hand.get(cell)) - c.cell(cell)) < .005)) { all = false; break; }
            if (all) return c;
        }
        return PartChoice.CUSTOM;
    }
    /**
     * The prototype's {@code P.k = matchHand(P)}: {@code hand}'s own values under the name of the preset it matches
     * (or Custom), on a copy. Every hand choice has the same dials, so the values carry over as they are.
     */
    static Part namedHand(Part hand) {
        if (hand.choice.part != BrushPart.HAND) throw new IllegalArgumentException(hand.choice + " isn't a hand");
        return new Part(matchHand(hand), hand.values.clone());
    }

    /** The prototype's {@code v || 0}. */
    private static double or0(double v) { return Double.isNaN(v) ? 0 : v; }
}
