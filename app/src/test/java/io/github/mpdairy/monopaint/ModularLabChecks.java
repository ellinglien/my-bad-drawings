package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class ModularLabChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        fromOld(); parts(); locks(); codes(); undo(); column(); swaps(); sentences(); advanced(); backRoom(); redraws();
        System.out.println("PASS: the lab rolls six-part brushes from any brush, swaps choices, slides, rolls and locks dials and parts, copies and loads M codes and loads old I/P codes as six parts; Undo takes each change back; the column's chips, sliders and dice work for every style, part and dial; swaps change one part; the open card greys dials that change nothing, cycles hand cells, locks and rolls hand rows and flips Fast; a slide draws again only its own part, a new brush draws all six; every choice rolls may pick is offered (Hatch, Stipple, Filled Shape and the Play menu's toys too); the lab shows Tip, Medium, Paper, Drying, Control, Smudge");
    }
    static BrushLab lab(int slot, long seed) {
        Map<String, String> store = new HashMap<>();
        BrushLibrary.Store s = new BrushLibrary.Store() { public String get(String k) { return store.get(k); } public void put(String k, String v) { if (v == null) store.remove(k); else store.put(k, v); } };
        return new BrushLab(ToolSlotsChecks.old(s, new Random(1)), slot, new Random(seed));
    }
    static void fromOld() {
        BrushLab lab = lab(2, 1);
        check(lab.load("dapper-fig.I610gavij3f7q16o0000006m00.e1") && lab.parts().style.wild, "An old Wild brush opens as six parts, Anything goes");
        lab.randomize();
        check(lab.parts().style.wild && lab.canUndo(), "and randomizes as Anything goes");
        BrushLab fude = lab(1, 2);
        fude.randomize();
        check(fude.parts().style == ModularStyle.named("Print"), "A converted built-in randomizes in its style");
        for (WildBrush.Kind kind : WildBrush.Kind.values()) for (BrushStyle style : BrushStyle.of(kind)) check(ModularStyle.named(style.name) != null, "Every old style has a six-part style to convert to: " + style.name);
    }
    /** What a change draws again (e-ink): a slide only its part, a new brush every part; the lab's order of the parts. */
    static void redraws() {
        BrushLab lab = lab(2, 11);
        lab.pickStyle(ModularStyle.named("Watercolor"));
        check(BrushLab.redraw(null, lab.parts()).all, "Nothing shown yet: every part");
        ModularBrush before = lab.parts().copy();
        PartDial macro = lab.parts().part(BrushPart.TIP).choice.macro;
        double[] range = lab.parts().part(BrushPart.TIP).choice.range(macro);
        lab.startSlide(); lab.slide(macro, range[0]); lab.slide(macro, range[1]); lab.endSlide(macro);
        BrushLab.Redraw slid = BrushLab.redraw(before, lab.parts());
        check(!slid.all && slid.parts.equals(java.util.EnumSet.of(BrushPart.TIP)), "A Tip slide draws only Tip again: " + slid.parts);

        ModularBrush slidBrush = lab.parts().copy();
        lab.undo();
        BrushLab.Redraw undone = BrushLab.redraw(slidBrush, lab.parts());
        check(!undone.all && undone.parts.equals(java.util.EnumSet.of(BrushPart.TIP)), "Undo of it draws only Tip again: " + undone.parts);
        check(BrushLab.redraw(lab.parts().copy(), lab.parts()).nothing(), "No change, nothing");
        ModularBrush unrenamed = lab.parts().copy();
        lab.rename("plain-pear");
        check(BrushLab.redraw(unrenamed, lab.parts()).nothing(), "A rename draws no module again");
        ModularBrush old = lab.parts().copy();
        lab.randomize();
        check(BrushLab.redraw(old, lab.parts()).all, "Randomize: every part");
        check(BrushLab.redraw(lab.parts(), null).nothing(), "An old brush has no modules");
        check(BrushPart.SHOWN.equals(Arrays.asList(BrushPart.TIP, BrushPart.MEDIUM, BrushPart.PAPER, BrushPart.LIFE, BrushPart.HAND, BrushPart.CARRY)), "The lab shows Tip, Medium, Paper, Drying, Control, Smudge");
        check(PartChoice.SMUDGE.label.equals("Smear") && PartChoice.SMUDGE.key.equals("smudge"), "Smudge's choice reads Smear; its code is unchanged");
    }
    static void parts() {
        BrushLab lab = lab(2, 3);
        lab.pickStyle(ModularStyle.named("Dip pen"));
        lab.choose(BrushPart.TIP, PartChoice.HAKE);
        check(lab.parts().choice(BrushPart.TIP) == PartChoice.HAKE && lab.status.equals("Tip: Hake"), "A choice chip swaps the part: " + lab.status);
        lab.startSlide(); lab.slide(PartDial.ANGLE, 50); lab.endSlide(PartDial.ANGLE);
        check(lab.parts().part(BrushPart.TIP).get(PartDial.ANGLE) == 50 && lab.status.equals("Nib Angle 50°"), "A slider sets its dial: " + lab.status);
        lab.startSlide(); lab.slide(PartDial.TS, .7); lab.endSlide(PartDial.TS);
        check(lab.parts().part(BrushPart.HAND).get(PartDial.TS) == .7 && lab.parts().choice(BrushPart.HAND) == ModularBrush.matchHand(lab.parts().part(BrushPart.HAND)), "A hand cell slides; the hand is named for its cells");
        lab.roll(PartDial.ANGLE);
        check(lab.status.startsWith("Nib Angle "), "A dial's own dice: " + lab.status);
        PartChoice paper = lab.parts().choice(BrushPart.PAPER);
        lab.rollPart(BrushPart.MEDIUM);
        check(lab.parts().choice(BrushPart.PAPER) == paper, "A part's dice leaves the others");
    }
    static void locks() {
        BrushLab lab = lab(2, 4);
        lab.pickStyle(ModularStyle.named("Watercolor"));
        lab.toggleLock(BrushPart.PAPER);
        ModularBrush.Part paper = lab.parts().part(BrushPart.PAPER).copy();
        for (int i = 0; i < 10; i++) { lab.randomize(); check(lab.parts().part(BrushPart.PAPER).sameAs(paper), "A locked part survives Randomize"); }
    }
    static void codes() {
        BrushLab lab = lab(2, 6);
        lab.pickStyle(ModularStyle.named("Hake"));
        String code = lab.code();
        check(code.contains(".Mc"), "A six-part brush's code is an M code: " + code);
        BrushLab other = lab(2, 7);
        check(other.load(code) && other.parts() != null && ModularCode.encode(other.parts()).equals(code), "and loads");
        check(other.load("dapper-fig.I610gavij3f7q16o0000006m00.e1") && other.code().startsWith("dapper-fig.Md"), "An I code loads as its six parts");
        check(!other.load("brave-onion.Mz0_0_0_0_0_0.1") && other.status.equals("Not a brush code. Paste all of it."), "A bad code says so");
        check(other.load(ModularCodeChecks.BRAVE_ONION) && other.brush().name().equals("brave-onion"), "The web lab's codes load");
        check(other.rename("Brave Onion II") && other.parts().name.equals("Brave Onion II"), "Rename");
    }
    static void undo() {
        BrushLab lab = lab(2, 8);
        lab.pickStyle(ModularStyle.named("Marker"));
        String before = lab.code();
        lab.choose(BrushPart.TIP, PartChoice.SPLIT_NIB);
        lab.undo();
        check(lab.code().equals(before), "Undo takes a swap back");
    }
    /** What the lab's column shows for a six-part brush, and every chip, slider and die in it, in every style. */
    static void column() {
        for (ModularStyle style : ModularStyle.all()) {
            for (long seed = 0; seed < 8; seed++) {
                BrushLab lab = lab(2, 100 + seed);
                lab.pickStyle(style);
                shows(lab);
            }
            BrushLab lab = lab(2, 9);
            lab.pickStyle(style);
            for (BrushPart part : BrushPart.values()) {
                for (PartChoice choice : lab.chips(part)) {
                    lab.choose(part, choice);
                    for (PartDial dial : lab.dials(part)) {
                        if (dial.choice()) for (int option = dial.choices.length - 1; option >= 0; option--) lab.choose(dial, option);
                        else {
                            double[] r = lab.parts().choice(part).range(dial);
                            lab.startSlide(); lab.slide(dial, r[0]); lab.slide(dial, r[1]); lab.endSlide(dial);
                            check(lab.parts().part(part).get(dial) == r[1], "A slider reaches its range's end: " + dial);
                        }
                        lab.roll(dial);
                        shows(lab);
                    }
                    lab.rollPart(part);
                    shows(lab);
                }
            }
        }
        BrushLab lab = lab(2, 10);
        lab.pickStyle(ModularStyle.named("Dip pen"));
        lab.choose(BrushPart.TIP, PartChoice.BRISTLES);
        lab.choose(BrushPart.CARRY, PartChoice.DRAG);
        BrushLab decoded = lab(2, 11);
        check(decoded.load(lab.code()) && decoded.chips(BrushPart.TIP).contains(PartChoice.BRISTLES) && decoded.chips(BrushPart.CARRY).contains(PartChoice.DRAG)
                && decoded.dials(BrushPart.CARRY).equals(Arrays.asList(PartDial.PICK, PartDial.LAY, PartDial.LOAD, PartDial.KEEPS, PartDial.PLOAD, PartDial.PMIN)), "A decoded Bristles or Drag shows as its part's chip, with its dials");
        shows(decoded);
        decoded.choose(BrushPart.TIP, PartChoice.ROUND);
        decoded.choose(BrushPart.CARRY, PartChoice.OFF);
        check(decoded.chips(BrushPart.CARRY).contains(PartChoice.DRAG) && decoded.chips(BrushPart.TIP).contains(PartChoice.BRISTLES),
                "Bristles and Drag, which draw, stay offered once swapped away");
        decoded.choose(BrushPart.LIFE, PartChoice.DRYING);
        check(decoded.dials(BrushPart.LIFE).equals(Arrays.asList(PartDial.DRYING, PartDial.SETTLE, PartDial.RUNS, PartDial.DRIPS, PartDial.G_POOL, PartDial.SINK, PartDial.BLEND)),
                "Life hides Takes ink and Plume; gravity's five follow Settle");
        decoded.choose(BrushPart.TIP, PartChoice.HAKE);
        decoded.choose(PartDial.HEAD_TURN, 1);
        check(decoded.parts().part(BrushPart.TIP).get(PartDial.HEAD_TURN) == 1 && decoded.status.equals("Head Follows Page"), "A choice dial's chip: " + decoded.status);
        decoded.choose(BrushPart.HAND, PartChoice.STEADY);
        decoded.startSlide(); decoded.slide(PartDial.SW, .37); decoded.endSlide(PartDial.SW);
        check(decoded.parts().choice(BrushPart.HAND) == PartChoice.CUSTOM && decoded.chips(BrushPart.HAND).contains(PartChoice.CUSTOM), "A Custom hand shows Custom");
        decoded.choose(BrushPart.HAND, PartChoice.BRUSH_HAND);
        check(!decoded.chips(BrushPart.HAND).contains(PartChoice.CUSTOM) && decoded.chips(BrushPart.HAND).size() == 8, "A preset hand shows the eight presets");
    }
    /** The swap strip: for every part of three rolled brushes, one tile is the brush, and each other changes only that part. */
    static void swaps() {
        for (long seed = 0; seed < 3; seed++) {
            BrushLab lab = lab(2, 300 + seed);
            lab.randomize();
            ModularBrush b = lab.parts();
            for (BrushPart part : BrushPart.values()) {
                List<BrushLab.Swap> swaps = lab.swaps(part);
                int now = 0;
                for (BrushLab.Swap swap : swaps) {
                    ModularBrush t = swap.brush.parts;
                    if (swap.now) { now++; check(swap.brush.sameAs(lab.brush()) && swap.choice == b.choice(part), "The current tile is the brush: " + part); continue; }
                    check(t.choice(part) == swap.choice && swap.choice != b.choice(part) && ModularFeatures.offered(swap.choice) && swap.choice != PartChoice.CUSTOM, "A tile switches its part to an offered choice: " + swap.choice);
                    for (BrushPart other : BrushPart.values()) check(other == part || t.part(other).sameAs(b.part(other)), "A tile changes only its part: " + swap.choice + " " + other);
                    if (part == BrushPart.HAND) check(t.part(part).get(PartDial.GAIN) == b.part(part).get(PartDial.GAIN) && t.part(part).get(PartDial.DIR) == b.part(part).get(PartDial.DIR), "A hand tile keeps Response and Fast is");
                    check(t.name.equals(b.name) && t.seed == b.seed, "A tile is the brush, named as it is");
                }
                check(now == 1, "One tile is the part's choice now: " + part);
                check(swaps.size() == lab.chips(part).size(), "A tile per chip: " + part);
            }
            BrushLab.Swap other = null;
            for (BrushLab.Swap swap : lab.swaps(BrushPart.TIP)) if (!swap.now) other = swap;
            lab.choose(BrushPart.TIP, other.choice);
            check(lab.brush().sameAs(other.brush), "Choosing a tile's choice gives the tile's brush");
        }
    }
    /** "This brush" keeps whole sentences, never fewer than two. */
    static void sentences() {
        check(ModularWords.sentences("A. B b. C.").equals(Arrays.asList("A.", "B b.", "C.")), "Three sentences, each with its full stop");
        check(ModularWords.sentences("Drying · 5.6 s. Then.").size() == 2, "A decimal point doesn't end a sentence");
        check(ModularWords.firstSentences("A. B. C.", 1).equals("A. B.") && ModularWords.firstSentences("A. B. C.", 3).equals("A. B. C.")
                && ModularWords.firstSentences("A. B. C.", 9).equals("A. B. C."), "First sentences: at least two");
        BrushLab lab = lab(2, 12);
        lab.pickStyle(ModularStyle.named("Watercolor"));
        lab.choose(BrushPart.LIFE, PartChoice.NONE);
        check(ModularWords.swapNote(lab.parts(), BrushPart.LIFE).equals("dries at once") && ModularWords.swapNote(lab.parts(), BrushPart.TIP).isEmpty(), "Swap notes");
    }
    /**
     * What was Brush Lab's back room (Advanced's only until 2026-10-29, when Basic went): Hatch, Stipple, Filled Shape,
     * Stamp, Spiro, Float, Mold, Fading and Lemon Juice are offered whenever rolls may pick them.
     */
    static void backRoom() {
        BrushLab lab = lab(0, 3);
        for (PartChoice c : new PartChoice[]{PartChoice.STIPPLE, PartChoice.HATCH, PartChoice.SHAPE, PartChoice.STAMP, PartChoice.SPIRO, PartChoice.FLOAT, PartChoice.HALO, PartChoice.FADE, PartChoice.LEMON})
            if (c != lab.parts().choice(c.part)) check(lab.chips(c.part).contains(c) == ModularFeatures.offered(c), c + " offered as rolls are");
        check(lab.load("hatch-pen.M08at7x901zzi0_000zz000000000000000_0_000_1jz_0000000000000000000nz0.x3") && lab.chips(BrushPart.TIP).contains(PartChoice.HATCH), "A Hatch brush shows Hatch");
    }
    /** Advanced's open card: greyed dials, the hand grid's cells, rows and locks, and the Fast switch. */
    static void advanced() {
        BrushLab lab = lab(2, 13);
        lab.pickStyle(ModularStyle.named("Dip pen"));
        lab.choose(BrushPart.MEDIUM, PartChoice.INK);
        lab.startSlide(); lab.slide(PartDial.WET, 0); lab.endSlide(PartDial.WET);
        check(!lab.active(PartDial.LINE) && !lab.active(PartDial.BLEED) && !lab.active(PartDial.BLOOM) && lab.active(PartDial.WET) && lab.active(PartDial.POOL), "Ink at Wet 0 greys Line, Bleed and Bloom");
        lab.choose(BrushPart.MEDIUM, PartChoice.WASH);
        lab.startSlide(); lab.slide(PartDial.WET, .3); lab.endSlide(PartDial.WET);
        check(!lab.active(PartDial.BLOOM) && lab.active(PartDial.LINE), "Wash at Wet .3 greys Bloom");
        lab.startSlide(); lab.slide(PartDial.WET, .8); lab.endSlide(PartDial.WET);
        check(lab.active(PartDial.BLOOM), "and not at .8");
        for (ModularStyle style : ModularStyle.all()) {
            BrushLab each = lab(2, 400);
            each.pickStyle(style);
            for (BrushPart part : BrushPart.values()) for (PartDial dial : each.dials(part)) each.active(dial);
        }
        // The hand grid: a cell cycles off, some, full, off; the hand is named for its cells.
        lab.choose(BrushPart.HAND, PartChoice.STEADY);
        double[] seen = new double[3];
        for (int i = 0; i < 3; i++) {
            lab.cycleCell(PartDial.PS);
            ModularBrush.Part hand = lab.parts().part(BrushPart.HAND);
            seen[i] = hand.get(PartDial.PS);
            check(hand.choice == ModularBrush.matchHand(hand), "A cycled hand is Custom or the preset it matches: " + hand.choice);
        }
        check(seen[0] == .5 && seen[1] == 1 && seen[2] == 0 && lab.parts().choice(BrushPart.HAND) == PartChoice.STEADY
                && lab.status.equals("Pressure Size: off"), "A cell cycles some, full, off: " + Arrays.toString(seen) + " " + lab.status);
        lab.undo();
        check(lab.parts().part(BrushPart.HAND).get(PartDial.PS) == 1, "Undo takes a cell's tap back");
        // Tilt size goes on past full to max, the wide Tilt range; the tap after leaves the range normal.
        String[] levels = new String[4];
        for (int i = 0; i < 4; i++) {
            lab.cycleCell(PartDial.TS);
            levels[i] = BrushLab.level(lab.parts().part(BrushPart.HAND), PartDial.TS) + (int) lab.parts().part(BrushPart.HAND).get(PartDial.TILT_RANGE);
        }
        check(String.join(" ", levels).equals("some0 full0 max1 off0") && lab.status.equals("Tilt Size: off"), "Tilt size cycles some, full, max, off: " + String.join(" ", levels));
        lab.undo();
        check(BrushLab.level(lab.parts().part(BrushPart.HAND), PartDial.TS).equals("max"), "and Undo takes max back");
        lab.cycleCell(PartDial.TS);
        // A locked row survives its die, the other rows' and Randomize.
        lab.cycleCell
(PartDial.PD);
        lab.toggleRowLock(0);
        check(lab.rowLocked(0) && !lab.rowLocked(1), "A row's lock locks its three cells");
        double[] row = cells(lab, 0);
        lab.rollRow(0);
        for (int i = 0; i < 10; i++) {
            lab.rollRow(1); lab.rollRow(2); lab.randomize();
            check(Arrays.equals(cells(lab, 0), row), "A locked row survives rolls and Randomize");
            ModularBrush.Part hand = lab.parts().part(BrushPart.HAND);
            check(hand.choice == ModularBrush.matchHand(hand), "and the hand is named for its cells");
        }
        lab.toggleLock(PartDial.TS);
        double ts = lab.parts().part(BrushPart.HAND).get(PartDial.TS);
        for (int i = 0; i < 5; i++) { lab.rollRow(1); check(lab.parts().part(BrushPart.HAND).get(PartDial.TS) == ts, "A row's die keeps its locked cell"); }
        lab.toggleRowLock(0);
        check(!lab.rowLocked(0), "and unlocks");
        // The Fast switch.
        double dir = lab.parts().part(BrushPart.HAND).get(PartDial.DIR);
        lab.flipFast();
        check(lab.parts().part(BrushPart.HAND).get(PartDial.DIR) == 1 - dir && lab.status.equals("Fast strokes " + (dir == 0 ? "thicker" : "thinner")), "Fast flips: " + lab.status);
        lab.flipFast();
        check(lab.parts().part(BrushPart.HAND).get(PartDial.DIR) == dir, "and back");
    }
    private static double[] cells(BrushLab lab, int row) {
        double[] v = new double[3];
        for (int i = 0; i < 3; i++) v[i] = lab.parts().part(BrushPart.HAND).get(BrushLab.handRow(row)[i]);
        return v;
    }
    /** What the column reads for the lab's brush: its words, each part's chips with the current one on, each dial's range, band and value. */
    private static void shows(BrushLab lab) {
        ModularBrush b = lab.parts();
        check(!ModularWords.plainWords(b).isEmpty(), "This brush has words");
        for (BrushPart part : BrushPart.values()) {
            List<PartChoice> chips = lab.chips(part);
            check(chips.contains(b.choice(part)), "A part's chips include its choice: " + b.choice(part));
            check(part != BrushPart.HAND || b.choice(part) == PartChoice.CUSTOM || !chips.contains(PartChoice.CUSTOM), "Custom shows only when it's the hand");
            for (PartDial dial : lab.dials(part)) {
                check(b.part(part).has(dial) && ModularFeatures.shown(dial), "A shown dial is its choice's: " + dial);
                double[] r = b.choice(part).range(dial);
                check(r[0] < r[1] && !dial.shown(b.part(part).get(dial)).isEmpty(), "A dial has a range and a value: " + dial);
                double[] band = b.style.band(b.choice(part), dial);
                check(band == null || band[0] <= band[1], "A band runs low to high: " + dial);
            }
        }
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
