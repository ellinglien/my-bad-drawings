package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class ModularVariationsChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        lines(); words(); choices(); drying(); tiltRange(); variations(); locks();
        System.out.println("PASS: six variations nudge two or three dials and at most one swaps a part to an offered choice, labelled (\"Split nib\"), leaving locks alone; the words, captions and \"This brush\" are the house style's; Slow gives a drying time only when its wash settles after lift; a wide Tilt range broadens");
    }
    /** A fixture as a brush made new: at Erasable 1, so its words say nothing of it. */
    static ModularBrush f(String name) { return ErasableChecks.allErasable(ModularCodeChecks.fixture(name)); }
    static void words() {
        String[][] expected = {
                {"brave-onion", "Line and wash, split nib, on ribbed paper. Opens into two lines. Tilt turns nib, pressure widens."},
                {"wet-wash", "Wet wash on cold-press paper. Dark edges, blooms. Wet for 5.6 s. Pressure widens."},
                {"thick-oil", "Thick paint, flat nib, on canvas. Dry-brush texture, raised ridges. Tilt widens and lightens."},
                {"soft-coal", "Vine charcoal, chisel tip, on cold-press paper. Picks up the texture, soft speckled edge. Dries at once. Tilt and pressure widen."},
                {"wide-hake", "Flat wash, hake. Dry-brush streaks. Dries at once. Pressure widens."},
                {"rake", "Ink wash, 4-line rake. Line shifted. Dries at once. Pressure widens and spreads lines."},
                {"hb-pencil", "B graphite on ribbed paper. Picks up the texture. Dries at once. Pressure darkens and widens."}};
        for (String[] e : expected) check(ModularWords.plainWords(f(e[0])).equals(e[1]), "\"This brush\" in the house style (docs/TEXT-STYLE.md): " + ModularWords.plainWords(f(e[0])));
    }
    static void choices() {
        ModularBrush onion = f("brave-onion");
        check(ModularWords.choiceWords(onion, BrushPart.MEDIUM).equals("Wet ink") && ModularWords.choiceWords(onion, BrushPart.LIFE).equals("Quick")
                && ModularWords.choiceWords(onion, BrushPart.TIP).equals("Split Nib") && ModularWords.choiceWords(onion, BrushPart.HAND).equals("tilt turns nib, pressure widens"), "Choice words");
        check(ModularWords.choiceWords(f("wet-wash"), BrushPart.LIFE).equals("Slow 5.6 s") && ModularWords.choiceWords(f("hb-pencil"), BrushPart.MEDIUM).equals("Graphite B")
                && ModularWords.choiceWords(f("rake"), BrushPart.TIP).equals("4 lines") && ModularWords.choiceWords(f("thick-oil"), BrushPart.MEDIUM).equals("Thick paint")
                && ModularWords.choiceWords(f("soft-coal"), BrushPart.MEDIUM).equals("Charcoal"), "and more");
        check(ModularWords.caption(onion).equals(Arrays.asList("Dip pen", "split nib", "wet ink", "ribbed")) && ModularWords.caption(f("soft-coal")).equals(Arrays.asList("Charcoal", "instant"))
                && ModularWords.caption(f("hb-pencil")).equals(Arrays.asList("Pencil", "B", "ribbed", "instant")) && ModularWords.caption(f("wet-wash")).equals(Arrays.asList("Watercolour", "slow")), "Caption items: " + ModularWords.caption(onion));
    }
    /** Slow keeps changing after lift only as the stroke does: a transparent wash with Settle above 0. */
    static void drying() {
        ModularBrush wash = f("wet-wash");
        check(ModularWords.settles(wash) && ModularWords.plainWords(wash).contains(" Wet for 5.6 s.")
                && ModularWords.swapNote(wash, BrushPart.LIFE).equals("wet 5.6 s") && ModularWords.caption(wash).contains("slow"), "A settling wash keeps changing after lift");
        ModularBrush still = wash.copy();
        still.part(BrushPart.LIFE).set(PartDial.SETTLE, 0);
        ModularBrush opaque = wash.copy();
        opaque.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.COVER));
        ModularBrush inkWash = LegacyBrushes.builtin("WASH"), watercolor = LegacyBrushes.builtin("WATERCOLOR");
        for (ModularBrush b : new ModularBrush[]{still, opaque, inkWash, watercolor}) {
            check(b.choice(BrushPart.LIFE) == PartChoice.DRYING && !ModularWords.settles(b), "Slow without settling after lift: " + ModularCode.encode(b));
            check(!ModularWords.plainWords(b).contains("Wet for"), "says no drying time: " + ModularWords.plainWords(b));
            check(ModularWords.choiceWords(b, BrushPart.LIFE).equals("Slow") && ModularWords.swapNote(b, BrushPart.LIFE).isEmpty()
                    && !ModularWords.caption(b).contains("slow"), "with no drying time: " + ModularWords.caption(b));
        }
        // A converted Wild pen: Slow, a line and no body; at lift only its tail re-tapers.
        ModularBrush pen = LegacyBrushes.of(BrushCode.decode("bobson-dugnutt.I610zkyum2fbzsge00ej5k00e7.1"));
        check(!ModularWords.settles(pen) && ModularWords.plainWords(pen).equals("Ink line, chisel tip. Tapers to a point, spatters on flicks. Pressure widens.") && ModularWords.swapNote(pen, BrushPart.LIFE).isEmpty()
                && !ModularWords.caption(pen).contains("slow") && ModularWords.choiceWords(pen, BrushPart.LIFE).equals("Slow"), "A Slow line alone says nothing of drying: " + ModularWords.plainWords(pen));
    }
    /** A wide Tilt range (the Brush pen's) says Tilt → size reaches from a fine point to broad. */
    static void tiltRange() {
        ModularBrush pen = LegacyBrushes.builtin("FUDE");
        check((int) pen.part(BrushPart.HAND).get(PartDial.TILT_RANGE) == 1 && ModularWords.handWords(pen, 2).equals("tilt broadens and turns nib"), "The Brush pen's hand: " + ModularWords.handWords(pen, 2));
        pen.part(BrushPart.HAND).set(PartDial.TILT_RANGE, 0);
        check(ModularWords.handWords(pen, 2).equals("tilt widens and turns nib"), "a normal range widens");
    }
    static void variations() {
        Random random = new Random(11);
        ModularBrush b = f("brave-onion");
        int swaps = 0;
        for (int round = 0; round < 300; round++) {
            List<ModularVariations.Made> made = ModularVariations.make(b, new ModularLocks(), random);
            check(made.size() == 6, "Six variations");
            int swapped = 0;
            for (ModularVariations.Made m : made) {
                // A nudged hand cell renames the hand for its cells (Turn becomes Custom): not a swap.
                check(m.brush.choice(BrushPart.HAND) == ModularBrush.matchHand(m.brush.part(BrushPart.HAND)), "The hand is named for its cells");
                int differ = 0; BrushPart which = null;
                for (BrushPart part : BrushPart.values()) if (part != BrushPart.HAND && m.brush.choice(part) != b.choice(part)) { differ++; which = part; }
                check(differ <= 1, "A variation swaps at most one part");
                if (differ == 1) {
                    swapped++;
                    check(which != BrushPart.HAND && ModularFeatures.offered(m.brush.choice(which)) && ModularVariations.swapWords(m.brush.choice(which)).equals(m.swap) && m.caption.get(0).equals(m.swap), "A swap is labelled, never the hand, never deferred: " + m.swap);
                } else check(m.swap == null, "No label without a swap");
                int nudges = m.caption.size() - (m.swap == null ? 0 : 1);
                check(nudges <= 3, "Two or three nudges");
                for (String item : m.caption) if (!item.equals(m.swap)) check(item.startsWith("more ") || item.startsWith("less "), "A nudge says which way: " + item);
                // What changed, for a card: the swap first, each change in its wordings, longest first, the caption's own
                // words the longest; a nudge's short form "+nib width" or "-nib width".
                java.util.Set<String> longest = new java.util.HashSet<>();
                for (List<String> wordings : m.changes) {
                    longest.add(wordings.get(0));
                    for (int k = 1; k < wordings.size(); k++) check(wordings.get(k).length() < wordings.get(k - 1).length(), "Wordings longest first: " + wordings);
                    if (!wordings.get(0).equals(m.swap)) check(wordings.size() == 2 && wordings.get(1).equals((wordings.get(0).startsWith("more ") ? "+" : "-") + wordings.get(0).substring(5)), "A nudge's short form: " + wordings);
                    else check(wordings.size() == 1, "A swap has one wording, its choice: " + wordings);
                }
                check(longest.equals(new java.util.HashSet<>(m.caption)) && m.changes.size() == m.caption.size() && (m.swap == null || m.changes.get(0).get(0).equals(m.swap)), "The changes are the caption's, the swap first: " + m.changes);
                check(m.brush.name.endsWith("-onion") && m.brush.style == b.style && m.brush != b, "A kin name, the same style, a new brush");
            }
            check(swapped <= 1, "At most one of the six swaps a part");
            swaps += swapped;
        }
        check(swaps > 230 && swaps < 280, "About 85% of rounds swap: " + swaps);
        check(b.key().equals(f("brave-onion").key()), "The brush varied is unchanged");
    }
    static void locks() {
        Random random = new Random(12);
        ModularBrush b = f("brave-onion");
        ModularLocks locks = new ModularLocks(); locks.parts.add(BrushPart.TIP); locks.dials.add(PartDial.WET);
        for (int round = 0; round < 100; round++) for (ModularVariations.Made m : ModularVariations.make(b, locks, random)) {
            check(m.brush.part(BrushPart.TIP).sameAs(b.part(BrushPart.TIP)), "A locked part never varies");
            if (m.brush.choice(BrushPart.MEDIUM) == b.choice(BrushPart.MEDIUM)) check(m.brush.part(BrushPart.MEDIUM).get(PartDial.WET) == b.part(BrushPart.MEDIUM).get(PartDial.WET), "nor a locked dial");
        }
    }
    /** Captions: two lines of at most 17 characters (BrushCaptions.twoLines, the mockup's twoLines). */
    static void lines() {
        check(BrushCaptions.twoLines(java.util.Arrays.asList("more splatter", "less taper in", "less pressure")).equals(java.util.Arrays.asList("more splatter", "less taper in")), "Two lines; what doesn't fit is left out");
        check(BrushCaptions.twoLines(java.util.Arrays.asList("Wild", "chisel nib", "toothy")).equals(java.util.Arrays.asList("Wild, chisel nib", "toothy")), "Items share a line while it fits");
        check(BrushCaptions.twoLines(java.util.Arrays.asList("a very long item indeed", "short")).equals(java.util.Arrays.asList("short")), "An item too long for a line is left out");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
