package io.github.mpdairy.monopaint;

public final class InkWashLayersChecks {
    public static void main(String[] args) {
        ToneDocument doc = InkWashLayers.blank(16, 12);
        check(doc.layerCount() == 2 && doc.layerName(InkWashLayers.WASH).equals("Wash")
                && doc.layerName(InkWashLayers.INK).equals("Ink"), "Wash below, ink above");
        check(doc.activeLayer() == InkWashLayers.INK, "A new page starts on the ink layer");
        check(!doc.canUndo(), "The layers are not an undoable edit");
        for (int y = 0; y < 12; y++) for (int x = 0; x < 16; x++) check(doc.compositeTone(x, y) == 255, "Blank paper");
        // Ink is never covered by wash, whichever is drawn first.
        paint(doc, InkWashLayers.WASH, 3, 3, InkWashShades.WASH_DARK); paint(doc, InkWashLayers.INK, 3, 3, 0);
        paint(doc, InkWashLayers.INK, 5, 5, 0); paint(doc, InkWashLayers.WASH, 5, 5, InkWashShades.WASH_DARK);
        paint(doc, InkWashLayers.WASH, 7, 7, InkWashShades.WASH_DARK);
        check(doc.compositeTone(3, 3) == 0 && doc.compositeTone(5, 5) == 0, "Ink stays black over wash");
        check(Math.abs(doc.compositeTone(7, 7) - InkWashShades.WASH_DARK) <= 1, "Wash shows where there is no ink");
        pairs();
        merges();
        resized(doc);
        System.out.println("PASS: a new page is one pair, ink over wash in either order, blank paper; pair k is wash 2k and ink 2k+1, "
                + "the selected pair holds the active layer, one-layer and odd-count drawings fall back, grooves and the eraser use the pair's own layers; "
                + "a resized page keeps what it holds, top-left anchored; "
                + "Merge Old Layers makes every pair one as the page shows it, hidden pairs kept, one undo step that restores them exactly");
    }
    /** Brush Lab's test page keeps what is drawn when its size changes: cut off or extended blank, top-left anchored. */
    private static void resized(ToneDocument doc) {
        ToneDocument smaller = InkWashLayers.resized(doc, 6, 6), larger = InkWashLayers.resized(doc, 20, 15);
        check(smaller.width == 6 && smaller.height == 6 && smaller.layerCount() == 2 && smaller.activeLayer() == doc.activeLayer(), "Resized: its size, layers and selection");
        check(smaller.compositeTone(3, 3) == 0 && smaller.compositeTone(5, 5) == 0, "Smaller: what was drawn stays where it was");
        check(larger.compositeTone(3, 3) == 0 && Math.abs(larger.compositeTone(7, 7) - InkWashShades.WASH_DARK) <= 1, "Larger: what was drawn stays too");
        check(larger.compositeTone(19, 14) == 255 && larger.compositeTone(16, 0) == 255 && !larger.canUndo(), "Larger: blank beyond, not an undoable edit");
    }
    private static void pairs() {
        ToneDocument doc = InkWashLayers.blank(16, 12);
        check(InkWashLayers.pairs(doc) == 1 && InkWashLayers.current(doc) == 0, "A new page is one pair, selected");
        check(InkWashLayers.MAX_PAIRS == 4, "Up to four pairs");
        java.util.ArrayList<ToneDocument.Layer> layers = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) layers.add(InkWashLayers.empty(i % 2 == 0 ? "Wash" : "Ink", 64, 64));
        ToneDocument three = new ToneDocument(64, 64, layers, 3);
        check(InkWashLayers.pairs(three) == 3, "Six layers are three pairs");
        for (int layer = 0; layer < 6; layer++) check(InkWashLayers.pair(layer) == layer / 2, "Layer " + layer + "'s pair");
        check(InkWashLayers.current(three) == 1, "The active layer's pair is selected");
        for (int pair = 0; pair < 3; pair++)
            check(InkWashLayers.washOf(three, pair) == 2 * pair && InkWashLayers.inkOf(three, pair) == 2 * pair + 1, "Pair " + pair + " is wash 2k, ink 2k+1");
        check(InkWashLayers.inkOver(three, 2) == 3 && InkWashLayers.inkOver(three, 4) == 5, "Each wash grooves under its own pair's ink");
        three.selectLayer(2);
        check(InkWashLayers.current(three) == 1, "Its wash selected, the pair stays the same");

        ToneDocument single = new ToneDocument(4, 4);
        check(InkWashLayers.pairs(single) == 1 && InkWashLayers.inkOf(single, 0) == 0 && InkWashLayers.washOf(single, 0) == 0
                && InkWashLayers.inkOver(single, 0) == -1, "A one-layer drawing from elsewhere takes everything on its only layer");
        java.util.ArrayList<ToneDocument.Layer> odd = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) odd.add(InkWashLayers.empty("Layer", 4, 4));
        ToneDocument triple = new ToneDocument(4, 4, odd, 2);
        check(InkWashLayers.pairs(triple) == 2 && InkWashLayers.current(triple) == 1 && InkWashLayers.washOf(triple, 1) == 2
                && InkWashLayers.inkOf(triple, 1) == 2 && InkWashLayers.inkOver(triple, 2) == -1, "An odd top layer is a pair of its own");

        // The eraser clears only the selected pair, both halves, as one undo step.
        for (int layer = 0; layer < 6; layer++) paint(three, layer, 20, 20, layer % 2 == 0 ? InkWashShades.WASH_DARK : 0);
        three.selectLayer(3);
        EraseBothStroke eraser = new EraseBothStroke(three, InkWashSizes.Preset.SMALL);
        eraser.sample(20, 20, .25f, 0, 0);
        check(eraser.finish(), "Erasing changes the page");
        check(three.activeLayer() == 3, "The eraser leaves the selection as it was");
        for (int layer = 0; layer < 6; layer++) check((alpha(three, layer, 20, 20) == 0) == (layer == 2 || layer == 3), "Only pair 1 is erased: layer " + layer);
        check(three.undo(), "Undo");
        for (int layer = 0; layer < 6; layer++) check(alpha(three, layer, 20, 20) != 0, "One undo restores both halves: layer " + layer);
    }
    /** Merge Old Layers (the Layers panel, layers being pages): every pair made one as the page shows it, hidden ones too, one undo step. */
    private static void merges() {
        java.util.ArrayList<ToneDocument.Layer> layers = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) layers.add(InkWashLayers.empty(i % 2 == 0 ? "Wash" : "Ink", 8, 8));
        ToneDocument doc = new ToneDocument(8, 8, layers, 1);
        // Overlapping greys on every layer at x = y, a wash at 50 % opacity, and pair 2 hidden with a mark of its own at (7, 0).
        for (int layer = 0; layer < 6; layer++) for (int k = 0; k <= layer; k++) paint(doc, layer, k, k, 40 * layer);
        paint(doc, 4, 7, 0, 0);
        doc.setLayerOpacity(2, 50);
        check(InkWashLayers.show(doc, 2, false), "Pair 2 hidden");
        doc.selectLayer(3);
        int[][] before = shown(doc);
        int[] tones = new int[64];
        check(InkWashLayers.show(doc, 2, true), "(shown, to read the page with it)");
        for (int i = 0; i < 64; i++) tones[i] = doc.compositeTone(i % 8, i / 8);
        check(doc.undo(), "(hidden again)");
        check(!InkWashLayers.merge(InkWashLayers.blank(8, 8)), "One pair: nothing to merge");
        check(InkWashLayers.merge(doc), "Merge");
        check(InkWashLayers.pairs(doc) == 1 && doc.activeLayer() == InkWashLayers.INK && InkWashLayers.visible(doc, 0), "One pair, on its ink, shown");
        for (int i = 0; i < 64; i++) check(Math.abs(doc.compositeTone(i % 8, i / 8) - tones[i]) <= 1, "Each pixel as the page showed it, the hidden pair's marks kept: " + i);
        check(doc.compositeTone(7, 0) == 0, "The hidden pair's own mark shows: not lost");
        check(doc.undo() && java.util.Arrays.deepEquals(shown(doc), before) && !InkWashLayers.visible(doc, 2), "One undo brings the pairs back as they were, pair 2 hidden");
    }
    /** Every layer's alpha, in order, and the selected layer. */
    private static int[][] shown(ToneDocument doc) {
        int[][] out = new int[doc.layerCount() + 1][64];
        int active = doc.activeLayer();
        for (int layer = 0; layer < doc.layerCount(); layer++) for (int i = 0; i < 64; i++) out[layer][i] = alpha(doc, layer, i % 8, i / 8);
        doc.selectLayer(active);
        out[doc.layerCount()][0] = active;
        return out;
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    private static int alpha(ToneDocument doc, int layer, int x, int y) {
        int active = doc.activeLayer(); doc.selectLayer(layer);
        int a = doc.opacity(x, y); doc.selectLayer(active); return a;
    }
    private static void paint(ToneDocument doc, int layer, int x, int y, int tone) {
        doc.selectLayer(layer); doc.begin(); doc.setTone(x, y, tone); doc.finish();
    }
}
