package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.Arrays;

/** Each layer action on ink-over-wash pairs is one undoable step that never touches a pixel. */
public final class LayerPairChecks {
    public static void main(String[] args) {
        ToneDocument doc = InkWashLayers.blank(64, 48);
        paint(doc, 0, 1);
        State one = new State(doc);

        // Add above: a blank pair over the selected one, selected.
        check(InkWashLayers.add(doc, true), "Add above");
        check(InkWashLayers.pairs(doc) == 2 && InkWashLayers.current(doc) == 1 && doc.activeLayer() == 3, "The new pair is above, selected on its ink");
        check(blank(doc, 1) && same(one, 0, doc, 0), "The new pair is blank; the old one keeps its pixels");
        steps(doc, one, "Add above");
        paint(doc, 1, 2);
        // Add below the selected pair (1): the new pair becomes 1, the painted one moves up to 2.
        State two = new State(doc);
        check(InkWashLayers.add(doc, false), "Add below");
        check(InkWashLayers.pairs(doc) == 3 && InkWashLayers.current(doc) == 1 && blank(doc, 1), "The new pair is below the selected one, selected");
        check(same(two, 0, doc, 0) && same(two, 1, doc, 2), "The others keep their pixels, in order");
        steps(doc, two, "Add below");
        paint(doc, 1, 3);
        check(InkWashLayers.add(doc, true) && InkWashLayers.pairs(doc) == 4, "A fourth pair");
        check(!InkWashLayers.add(doc, true) && !InkWashLayers.add(doc, false) && InkWashLayers.pairs(doc) == 4, "No more than four pairs");
        paint(doc, 2, 4);
        // Pairs, bottom to top: 1, 3, 4, 2 (by their paint).
        State four = new State(doc);
        check(top(doc, 0) == 1 && top(doc, 1) == 3 && top(doc, 2) == 4 && top(doc, 3) == 2, "Stacking order");

        // Move up and down by one, keeping the moved pair selected.
        check(InkWashLayers.move(doc, 1, true), "Move up");
        check(top(doc, 1) == 4 && top(doc, 2) == 3 && InkWashLayers.current(doc) == 2 && same(four, 1, doc, 2) && same(four, 2, doc, 1), "Swapped, pixels whole, still selected");
        steps(doc, four, "Move up"); doc.undo();
        check(InkWashLayers.move(doc, 1, false), "Move down");
        check(top(doc, 0) == 3 && top(doc, 1) == 1 && InkWashLayers.current(doc) == 0, "Moved below");
        steps(doc, four, "Move down"); doc.undo();
        check(!InkWashLayers.move(doc, 3, true) && !InkWashLayers.move(doc, 0, false) && new State(doc).equals(four), "Nothing moves past the top or bottom");

        // Hide and show.
        int topBefore = doc.compositeTone(20, 20);
        check(InkWashLayers.show(doc, 3, false) && !InkWashLayers.visible(doc, 3), "Hide");
        check(doc.compositeTone(20, 20) != topBefore && same(four, 3, doc, 3), "A hidden pair leaves the page but keeps its pixels");
        steps(doc, four, "Hide"); doc.undo();
        check(!InkWashLayers.show(doc, 3, true), "Showing a shown pair changes nothing");
        InkWashLayers.show(doc, 2, false);
        InkWashLayers.select(doc, 2);
        check(InkWashLayers.showSelected(doc) && InkWashLayers.visible(doc, 2) && !InkWashLayers.showSelected(doc), "Drawing on a hidden pair shows it");
        doc.undo(); doc.undo();
        doc.selectLayer(four.active);
        check(new State(doc).equals(four), "Undone");

        // Remove, keeping at least one.
        State before = new State(doc);
        check(InkWashLayers.remove(doc, 1), "Remove");
        check(InkWashLayers.pairs(doc) == 3 && InkWashLayers.current(doc) == 1 && top(doc, 1) == 4 && same(before, 2, doc, 1), "The pair above takes its place, selected");
        steps(doc, before, "Remove"); doc.undo();
        check(InkWashLayers.remove(doc, 3) && InkWashLayers.current(doc) == 2, "Removing the top selects the new top");
        while (InkWashLayers.pairs(doc) > 1) check(InkWashLayers.remove(doc, 0), "Remove down to one");
        check(!InkWashLayers.remove(doc, 0) && InkWashLayers.pairs(doc) == 1, "The last pair stays");

        // A one-layer drawing from MonoPaint: the first structural change pairs its layer with a blank ink.
        ToneDocument single = new ToneDocument(64, 48);
        single.begin(); single.setTone(20, 20, 9); single.finish();
        State alone = new State(single);
        check(InkWashLayers.add(single, true), "Add to a one-layer drawing");
        check(single.layerCount() == 4 && InkWashLayers.current(single) == 1 && single.compositeTone(20, 20) == 9 && Arrays.equals(alone.pixels.get(0), new State(single).pixels.get(0)), "Its layer is pair 0's wash, under a blank ink");
        steps(single, alone, "Add to one layer");
        saved();
        System.out.println("PASS: layer pairs: saved and loaded with their order, visibility and the selected pair; add above or below, move up or down, hide and show, remove down to one; each one undo step restoring exactly, redone exactly, pixels untouched; at most four; drawing on a hidden pair shows it; a one-layer drawing pairs up");
    }

    /** A drawing saves its pairs, which are hidden, and the selected pair. */
    private static void saved() {
        ToneDocument doc = InkWashLayers.blank(64, 48);
        for (int i = 0; i < 3; i++) InkWashLayers.add(doc, true);
        for (int pair = 0; pair < 4; pair++) paint(doc, pair, pair + 1);
        InkWashLayers.show(doc, 1, false);
        InkWashLayers.select(doc, 2);
        State before = new State(doc);
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            DocumentCodec.write(out, doc.layerSnapshot());
            ToneDocument loaded = DocumentCodec.read(new java.io.ByteArrayInputStream(out.toByteArray()));
            check(new State(loaded).equals(before) && InkWashLayers.current(loaded) == 2 && !InkWashLayers.visible(loaded, 1), "Saved and loaded exactly, the selected pair too");
        } catch (java.io.IOException error) { throw new AssertionError(error); }
    }
    /** Undo restores {@code before} exactly; redo gives back what the step made. */
    private static void steps(ToneDocument doc, State before, String what) {
        State after = new State(doc);
        check(doc.undo() && new State(doc).equals(before), what + ": one undo restores exactly");
        check(doc.redo() && new State(doc).equals(after), what + ": redo reapplies");
        check(doc.undo() && new State(doc).equals(before), what + ": and undo again");
        check(doc.redo(), what + ": redo");
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    /** Paints pair {@code pair}'s wash and ink at a spot of their own, so pairs are told apart by {@link #top}. */
    private static void paint(ToneDocument doc, int pair, int mark) {
        int active = doc.activeLayer();
        doc.selectLayer(InkWashLayers.washOf(doc, pair)); doc.begin(); doc.setTone(mark, 1, 200); doc.setTone(20, 20, 240 - 40 * mark); doc.finish();
        doc.selectLayer(InkWashLayers.inkOf(doc, pair)); doc.begin(); doc.setTone(mark, 2, 0); doc.finish();
        doc.selectLayer(active);
    }
    /** Which mark pair {@code pair} carries: the column of its ink dot. */
    private static int top(ToneDocument doc, int pair) {
        ToneDocument.Snapshot s = doc.layerSnapshot();
        byte[] alpha = s.layers.get(InkWashLayers.inkOf(doc, pair)).alpha;
        for (int x = 0; x < 10; x++) if (alpha[2 * doc.width + x] != 0) return x;
        return -1;
    }
    private static boolean blank(ToneDocument doc, int pair) {
        ToneDocument.Snapshot s = doc.layerSnapshot();
        for (int layer : new int[]{InkWashLayers.washOf(doc, pair), InkWashLayers.inkOf(doc, pair)})
            for (byte a : s.layers.get(layer).alpha) if (a != 0) return false;
        return true;
    }
    /** Whether pair {@code p} of {@code state} holds exactly the pixels of pair {@code q} of {@code doc}. */
    private static boolean same(State state, int p, ToneDocument doc, int q) {
        State now = new State(doc);
        return Arrays.equals(state.pixels.get(2 * p), now.pixels.get(2 * q)) && Arrays.equals(state.pixels.get(2 * p + 1), now.pixels.get(2 * q + 1));
    }
    /** Every layer's name, visibility and pixels, and the selection. */
    private static final class State {
        final ArrayList<String> names = new ArrayList<>();
        final ArrayList<Boolean> visible = new ArrayList<>();
        final ArrayList<byte[]> pixels = new ArrayList<>();
        final int active;
        State(ToneDocument doc) {
            ToneDocument.Snapshot s = doc.layerSnapshot();
            for (ToneDocument.Layer layer : s.layers) {
                names.add(layer.name); visible.add(layer.visible);
                byte[] both = Arrays.copyOf(layer.tones, layer.tones.length * 2);
                System.arraycopy(layer.alpha, 0, both, layer.tones.length, layer.alpha.length);
                pixels.add(both);
            }
            active = s.active;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof State)) return false;
            State o = (State)other;
            if (active != o.active || !names.equals(o.names) || !visible.equals(o.visible) || pixels.size() != o.pixels.size()) return false;
            for (int i = 0; i < pixels.size(); i++) if (!Arrays.equals(pixels.get(i), o.pixels.get(i))) return false;
            return true;
        }
        @Override public int hashCode() { return active; }
    }
}
