package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class LabSlotsChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        opens(); select(); put(); undoTwoSlots(); own(); library(); surprise(); results(); locks(); pages();
        System.out.println("PASS: the lab holds the ten slots and works on the picked one; cards and Surprise go into it and Undo takes any slot's change back, reselecting it; the library keeps one order with slot badges; locks keep what they lock; pages");
    }
    static void opens() {
        ToolSlots slots = BrushLabChecks.slots();
        BrushLab lab = new BrushLab(slots, 2, new Random(1));
        for (int i = 0; i < ToolSlots.COUNT; i++) check(lab.slot(i).sameAs(slots.brush(i)), "The lab holds slot " + i + "'s brush");
        check(lab.selected() == 2 && lab.brush().sameAs(slots.brush(2)) && !lab.canUndo(), "It works on the slot it opened on, nothing to undo");
    }
    static void select() {
        ToolSlots slots = BrushLabChecks.slots();
        BrushLab lab = new BrushLab(slots, 2, new Random(2));
        lab.select(4);
        check(lab.selected() == 4 && lab.brush().sameAs(slots.brush(4)) && !lab.canUndo(), "Selecting a slot works on it; not an Undo step");
        check(lab.status.equals("Slot 5"), "and says so: " + lab.status);
    }
    static void put() {
        ToolSlots slots = BrushLabChecks.slots();
        BrushLab lab = new BrushLab(slots, 2, new Random(3));
        lab.select(4);
        Brush fude = lab.library().get(1).brush;
        lab.put(fude);
        check(lab.slot(4).name().equals("Halftone brush") && lab.canUndo() && lab.status.equals("Halftone brush in slot 5"), "A card goes in the selected slot: " + lab.status);
        for (int i = 0; i < ToolSlots.COUNT; i++) if (i != 4) check(lab.slot(i).sameAs(slots.brush(i)), "The other slots stay");
        lab.put(fude);
        check(lab.status.equals("Already in slot 5"), "The same brush again says so: " + lab.status);
        lab.select(1);
        lab.undo();
        check(lab.selected() == 4 && lab.slot(4).sameAs(slots.brush(4)) && !lab.canUndo(), "Undo takes the slot's change back and reselects it; the second put was no step");
        check(lab.status.equals("Undone"), lab.status);
    }
    static void undoTwoSlots() {
        ToolSlots slots = BrushLabChecks.slots();
        BrushLab lab = new BrushLab(slots, 0, new Random(9));
        List<BrushLab.Card> cards = lab.library();
        lab.select(4);
        lab.put(cards.get(1).brush);
        lab.select(1);
        lab.put(cards.get(2).brush);
        check(lab.slot(4).sameAs(cards.get(1).brush) && lab.slot(1).sameAs(cards.get(2).brush), "Puts in two slots");
        lab.undo();
        check(lab.selected() == 1 && lab.slot(1).sameAs(slots.brush(1)) && lab.slot(4).sameAs(cards.get(1).brush) && lab.canUndo(),
                "The first Undo takes back slot 2's put, reselecting it, and leaves slot 5");
        lab.select(0);
        lab.undo();
        check(lab.selected() == 4 && lab.slot(4).sameAs(slots.brush(4)) && lab.slot(1).sameAs(slots.brush(1)) && !lab.canUndo(),
                "The second takes back slot 5's, reselecting it");
        for (int i = 0; i < ToolSlots.COUNT; i++) check(lab.slot(i).sameAs(slots.brush(i)), "Every slot is as it was");
    }
    /** A change works on the selected slot's own copy: the first brushes are ordinary brushes, changed in place. */
    static void own() {
        ToolSlots slots = BrushLabChecks.slots();
        BrushLab lab = new BrushLab(slots, 3, new Random(4));
        lab.select(0);
        lab.roll(PartDial.WEIGHT);
        lab.rename("mine");
        Brush[] results = lab.results();
        check(results[0].name().equals("mine") && !results[0].sameAs(slots.brush(0)) && results[0].parts.style.name.equals("Fineliner"), "Changing slot 0's Fineliner changes it there");
        for (int i = 1; i < ToolSlots.COUNT; i++) check(results[i].sameAs(slots.brush(i)), "and only there");
        check(slots.brush(0).name().equals("Fineliner"), "The slot itself takes it only on Back");
    }
    static void library() {
        ToolSlots slots = BrushLabChecks.slots();
        BrushLab lab = new BrushLab(slots, 2, new Random(5));
        List<BrushLab.Card> cards = lab.library();
        for (int i = 0; i < ToolSlots.COUNT; i++) check(cards.get(i).brush.sameAs(slots.brush(i)), "A first launch's library is the ten first brushes, in slot order");
        check(cards.get(0).slots.equals(Arrays.asList(0)) && cards.get(1).slots.equals(Arrays.asList(1)), "A card shows the slots holding it: " + cards.get(0).slots);
        lab.select(0); lab.randomize(); lab.save();
        Brush a = lab.results()[0];
        lab.select(1); lab.randomize(); lab.save();
        Brush b = lab.results()[1];
        int ia = lab.indexOf(a), ib = lab.indexOf(b);
        check(ib == 0 && ia == 1, "Saved brushes come first, newest first: " + ia + ", " + ib);
        List<String> before = names(lab.library());
        lab.select(2);
        lab.put(a);
        check(names(lab.library()).equals(before), "The order never depends on the slots");
        check(lab.library().get(ia).slots.equals(Arrays.asList(0, 2)), "A brush in two slots shows both");
        check(lab.indexOf(Brush.of(ModularStyle.named("Marker").roll(new Random(99)))) == -1, "A brush not in the library has no card");
    }
    static void surprise() {
        ToolSlots slots = BrushLabChecks.slots();
        BrushLab lab = new BrushLab(slots, 2, new Random(6));
        java.util.Set<String> picked = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            Brush held = lab.brush();
            int index = lab.surprise();
            check(index >= 0, "Surprise always finds one");
            Brush card = lab.library().get(index).brush;
            check(!card.sameAs(held) && lab.brush().sameAs(card) && lab.status.equals(card.name() + " in slot 3"), "never the brush the slot held: " + lab.status);
            picked.add(card.name());
            lab.undo();
            check(lab.brush().sameAs(held) && !lab.canUndo(), "Undo takes it back");
        }
        check(picked.size() == ToolSlots.COUNT - 1 && !picked.contains(lab.brush().name()), "Every other first brush comes up: " + picked);
    }
    static void results() {
        ToolSlots slots = BrushLabChecks.slots();
        BrushLab lab = new BrushLab(slots, 5, new Random(7));
        Brush[] results = lab.results();
        check(results.length == ToolSlots.COUNT, "Ten brushes back");
        for (int i = 0; i < ToolSlots.COUNT; i++) check(results[i].sameAs(slots.brush(i)), "Unchanged slots come back as they were");
    }
    static void locks() {
        BrushLab lab = new BrushLab(BrushLabChecks.slots(), 2, new Random(8));
        lab.pickStyle(ModularStyle.named(ModularStyle.ANYTHING_GOES));
        lab.toggleLock(BrushPart.TIP);
        ModularBrush.Part tip = lab.parts().part(BrushPart.TIP).copy();
        for (int i = 0; i < 20; i++) { lab.randomize(); check(lab.parts().part(BrushPart.TIP).sameAs(tip), "A locked tip stays"); }
        lab.toggleLock(BrushPart.TIP);
        boolean changed = false;
        for (int i = 0; i < 20 && !changed; i++) { lab.randomize(); changed = lab.parts().choice(BrushPart.TIP) != tip.choice; }
        check(changed, "Unlocked, it rolls");
    }
    static void pages() {
        check(LabPages.pages(0, 10) == 1 && LabPages.pages(10, 10) == 1 && LabPages.pages(11, 10) == 2, "Pages");
        check(LabPages.of(10, 10) == 1 && LabPages.clamp(5, 11, 10) == 1 && LabPages.clamp(-1, 11, 10) == 0, "A card's page, kept inside");
    }
    private static List<String> names(List<BrushLab.Card> cards) {
        List<String> names = new ArrayList<>();
        for (BrushLab.Card c : cards) names.add(c.brush.write());
        return names;
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
