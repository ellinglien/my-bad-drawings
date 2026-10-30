package io.github.mpdairy.monopaint;

public final class LinkedEditChecks {
    public static void main(String[] args) {
        linkedStepsTravelTogether(); unlinkedStepsStayApart(); nothingToLink(); continuationKeepsLink();
        System.out.println("PASS: linked edits undo and redo as one step, unlinked edits stay separate, empty gestures never link, continuations keep links");
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    private static void mark(ToneDocument doc, int layer, int x, int y) {
        doc.selectLayer(layer); doc.begin(); doc.setTone(x, y, 0); doc.finish();
    }
    private static void linkedStepsTravelTogether() {
        ToneDocument doc = new ToneDocument(8, 8); doc.addLayer();
        mark(doc, 1, 1, 1);
        doc.selectLayer(0); doc.begin(); doc.setTone(2, 2, 0);
        check(doc.finishWithPrevious(), "A changed linked gesture reports a change");
        check(doc.undo(), "Undo succeeds");
        check(doc.compositeTone(1, 1) == 255 && doc.compositeTone(2, 2) == 255, "One undo reverts both linked edits");
        check(doc.canUndo(), "Earlier history (adding the layer) remains");
        check(doc.redo(), "Redo succeeds");
        check(doc.compositeTone(1, 1) == 0 && doc.compositeTone(2, 2) == 0, "One redo restores both linked edits");
        check(!doc.canRedo(), "Both edits were redone");
    }
    private static void continuationKeepsLink() {
        ToneDocument doc = new ToneDocument(8, 8); doc.addLayer();
        mark(doc, 1, 1, 1);
        doc.selectLayer(0); doc.begin(); doc.setTone(2, 2, 0); doc.finishWithPrevious();
        doc.begin(); doc.setTone(3, 3, 0); doc.finishContinuation();
        doc.undo();
        check(doc.compositeTone(1, 1) == 255 && doc.compositeTone(2, 2) == 255 && doc.compositeTone(3, 3) == 255,
                "A continuation merged onto a linked edit stays linked: one undo reverts all three");
        doc.redo();
        check(doc.compositeTone(1, 1) == 0 && doc.compositeTone(2, 2) == 0 && doc.compositeTone(3, 3) == 0, "One redo restores all three");
    }
    private static void unlinkedStepsStayApart() {
        ToneDocument doc = new ToneDocument(8, 8); doc.addLayer();
        mark(doc, 1, 1, 1); mark(doc, 0, 2, 2);
        doc.undo();
        check(doc.compositeTone(2, 2) == 255 && doc.compositeTone(1, 1) == 0, "A plain finish stays its own undo step");
    }
    private static void nothingToLink() {
        ToneDocument doc = new ToneDocument(8, 8); doc.addLayer();
        mark(doc, 1, 1, 1);
        doc.selectLayer(0); doc.begin();
        check(!doc.finishWithPrevious(), "An unchanged gesture reports no change");
        mark(doc, 0, 3, 3); doc.undo();
        check(doc.compositeTone(3, 3) == 255 && doc.compositeTone(1, 1) == 0, "An empty linked gesture links nothing");
    }
}
