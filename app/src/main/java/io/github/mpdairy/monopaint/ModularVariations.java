package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.lerp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Six "your brush, but…" variations (parts.js 417–461). Each nudges two or three unlocked dials; at most one of
 * the six also swaps one unlocked part's choice (never the hand's, never to a choice the engine can't draw yet) and says
 * so ("Split nib", "Instant drying"). Each has a new seed and a kin name. Pure, checked on the desktop.
 */
final class ModularVariations {
    static final int COUNT = 6;

    static final class Made {
        final ModularBrush brush;
        /** The swap's label first if there is one, then each nudge ("more nib width"). */
        final List<String> caption;
        /** The swapped part's new choice in words ("Split nib", "Instant drying"), or null. */
        final String swap;
        /**
         * What changed, most telling first (the swap, then the nudges by how far they moved, as a share of the dial's
         * range), each in its wordings, longest first: "Split nib"; "more nib width" or "+nib width".
         * A card shows what fits it ({@link LabText#changes}).
         */
        final List<List<String>> changes;

        Made(ModularBrush brush, List<String> caption, String swap, List<List<String>> changes) {
            this.brush = brush; this.caption = Collections.unmodifiableList(caption); this.swap = swap;
            this.changes = Collections.unmodifiableList(changes);
        }
    }

    private ModularVariations() { }

    /**
     * parts.js makeVars: six variations of {@code b}, which is left as it is. A nudge moves an unlocked slider
     * (not the response, Erasable, Soften, a Screen's dials or Tone's Tone, as the web lab leaves them) 35–80% of the style's band, widened to take in its value (else the choice's range),
     * towards the far end when it sits near one; a dial that can be off snaps off below .04; a nudged hand is
     * renamed for its cells.
     */
    static List<Made> make(ModularBrush b, ModularLocks locks, Random random) {
        List<Made> out = new ArrayList<>();
        int swapAt = random.nextDouble() < .85 ? (int) (random.nextDouble() * COUNT) : -1;
        List<PartDial> pool = new ArrayList<>();
        for (BrushPart part : BrushPart.values()) {
            if (locks.parts.contains(part)) continue;
            // A gravity dial that is off stays off: a variation never sets a still wash running; nor Flick or Fling a still pen.
            for (PartDial d : b.choice(part).dials)
                if (!locks.dials.contains(d) && !d.choice() && d != PartDial.GAIN && d != PartDial.ERASABLE && d != PartDial.SOFTEN && d != PartDial.KEEPS && d != PartDial.PLOAD && d != PartDial.PMIN && !d.underScreen() && d != PartDial.TTONE && !((d.gravity() || d.mischief()) && b.part(part).get(d) == 0)) pool.add(d);
        }
        for (int i = 0; i < COUNT; i++) {
            ModularBrush c = b.copy(random.nextInt() & 0xffffffffL);
            c.name = BrushNames.kin(b.name, c.seed, random);
            List<String> caption = new ArrayList<>();
            List<List<String>> changes = new ArrayList<>();
            List<double[]> moved = new ArrayList<>();
            String swap = null;
            if (i == swapAt) {
                List<BrushPart> parts = swappable(b, locks);
                double pick = random.nextDouble();
                if (!parts.isEmpty()) {
                    BrushPart part = parts.get((int) (pick * parts.size()));
                    List<PartChoice> others = others(b.choice(part));
                    PartChoice to = others.get((int) (random.nextDouble() * others.size()));
                    to = late(b.choice(part), to, c.seed);
                    c.set(part, ModularBrush.switchTo(b.part(part), to));
                    swap = swapWords(to);
                    caption.add(swap);
                    changes.add(Collections.singletonList(swap));
                }
            }
            List<PartDial> left = new ArrayList<>(pool);
            int n = 2 + (random.nextDouble() < .4 ? 1 : 0);
            for (int j = 0; j < n && !left.isEmpty(); j++) {
                PartDial d = left.remove((int) (random.nextDouble() * left.size()));
                ModularBrush.Part p = c.part(d.part);
                if (!p.has(d)) continue;
                double[] range = p.choice.range(d), band = b.style.band(p.choice, d);
                if (band == null) band = range;
                double v = p.get(d), lo = Math.min(band[0], v), hi = Math.max(band[1], v), span = hi - lo;
                if (span == 0) span = (range[1] - range[0]) * .3;
                double rel = (v - lo) / (span == 0 ? 1 : span);
                int sign = random.nextDouble() < .5 ? -1 : 1;
                if (rel < .08) sign = 1; else if (rel > .92) sign = -1;
                double nv = StrokeMath.clamp(v + sign * lerp(.35, .8, random.nextDouble()) * span, range[0], range[1]);
                if (d.min == 0 && nv < .04) nv = 0;
                p.set(d, nv);
                if (d.part == BrushPart.HAND) c.set(BrushPart.HAND, ModularBrush.namedHand(p));
                String label = c.choice(d.part).labelOf(d).toLowerCase(Locale.ROOT).replaceFirst(" \\(.*\\)", "");
                caption.add((nv > v ? "more " : "less ") + label);
                moved.add(new double[]{Math.abs(nv - v) / Math.max(1e-9, range[1] - range[0]), moved.size()});
                changes.add(Arrays.asList(caption.get(caption.size() - 1), (nv > v ? "+" : "-") + label));
            }
            // The nudges that moved furthest first, after the swap; a tie keeps their order.
            int first = swap != null ? 1 : 0;
            List<List<String>> nudges = new ArrayList<>(changes.subList(first, changes.size()));
            moved.sort((x, y) -> x[0] != y[0] ? Double.compare(y[0], x[0]) : Double.compare(x[1], y[1]));
            for (int k = 0; k < moved.size(); k++) changes.set(first + k, nudges.get((int) moved.get(k)[1]));
            out.add(new Made(c.normalize(), caption, swap, changes));
        }
        return out;
    }

    /**
     * The parts a variation may swap: unlocked, not the hand, and with another choice to swap to (Carry has none
     * until carrying draws; the prototype has no part without one).
     */
    private static List<BrushPart> swappable(ModularBrush b, ModularLocks locks) {
        List<BrushPart> parts = new ArrayList<>();
        for (BrushPart p : BrushPart.values())
            if (!locks.parts.contains(p) && p != BrushPart.HAND && !others(b.choice(p)).isEmpty()) parts.add(p);
        return parts;
    }
    /**
     * {@code current}'s part's other choices a variation may swap to: offered, never a Custom hand. Those drawn after
     * ({@link ModularFeatures#late}) only when there is nothing else.
     */
    private static List<PartChoice> others(PartChoice current) {
        List<PartChoice> others = new ArrayList<>(), late = new ArrayList<>();
        for (PartChoice c : current.part.choices())
            if (c != current && c != PartChoice.CUSTOM && ModularFeatures.varies(c)) (ModularFeatures.late(c) ? late : others).add(c);
        return others.isEmpty() ? late : others;
    }
    /**
     * A swap from {@code current} to {@code to}, or to a choice drawn after ({@link ModularFeatures#late}) on its even
     * share of the other choices, as the prototype picks: drawn from the variation's own seed, so the variations'
     * other draws are what they were before it joined.
     */
    private static PartChoice late(PartChoice current, PartChoice to, long seed) {
        List<PartChoice> all = new ArrayList<>(), late = new ArrayList<>();
        for (PartChoice c : current.part.choices())
            if (c != current && c != PartChoice.CUSTOM && ModularFeatures.varies(c)) { all.add(c); if (ModularFeatures.late(c)) late.add(c); }
        if (late.isEmpty() || late.contains(to)) return to;
        int pick = (int) (new Random(seed ^ 0x5eed1a7eL).nextDouble() * all.size());
        return pick < late.size() ? late.get(pick) : to;
    }
    /** A swap in words: the new choice, a drying named as one ("Instant drying"; Float as it is). */
    static String swapWords(PartChoice to) {
        String label = ModularWords.sentence(to.label);
        return to.part == BrushPart.LIFE && to != PartChoice.FLOAT ? label + " drying" : label;
    }
    /**
     * A variation's caption: a swap on its own line ("Split nib"), then the nudges' first line; with no swap, the
     * nudges on two lines.
     */
    static List<String> lines(Made m) {
        if (m.swap == null) return BrushCaptions.twoLines(m.caption);
        List<String> lines = new ArrayList<>();
        lines.add(m.swap);
        List<String> nudges = BrushCaptions.twoLines(m.caption.subList(1, m.caption.size()));
        if (!nudges.isEmpty()) lines.add(nudges.get(0));
        return lines;
    }
}
