package io.github.mpdairy.monopaint;

public final class EraseBothChecks {
    public static void main(String[] args) {
        ToneDocument doc = InkWashLayers.blank(64, 64);
        paint(doc, InkWashLayers.WASH, 20, 20, InkWashShades.WASH_DARK); paint(doc, InkWashLayers.INK, 20, 20, 0);
        paint(doc, InkWashLayers.WASH, 50, 50, InkWashShades.WASH_DARK);
        doc.selectLayer(InkWashLayers.INK);
        check(erase(doc, 20), "Erasing ink and wash changes the page");
        check(doc.compositeTone(20, 20) == 255, "Ink and the wash beneath are both gone");
        check(Math.abs(doc.compositeTone(50, 50) - InkWashShades.WASH_DARK) <= 1, "Wash elsewhere stays");
        check(doc.activeLayer() == InkWashLayers.INK, "The eraser leaves the layer selection as it was");
        check(doc.undo() && doc.compositeTone(20, 20) == 0, "One undo restores the ink");
        check(doc.redo() && doc.compositeTone(20, 20) == 255, "One redo erases both again");

        ToneDocument washOnly = InkWashLayers.blank(64, 64);
        paint(washOnly, InkWashLayers.WASH, 20, 20, InkWashShades.WASH_DARK); paint(washOnly, InkWashLayers.INK, 5, 5, 0);
        check(erase(washOnly, 20), "Erasing only wash still changes the page");
        check(washOnly.compositeTone(20, 20) == 255, "The wash is gone");
        check(washOnly.undo() && Math.abs(washOnly.compositeTone(20, 20) - InkWashShades.WASH_DARK) <= 1, "Undo restores it");
        check(washOnly.compositeTone(5, 5) == 0, "Undo does not reach the earlier, unrelated ink mark");

        ToneDocument blank = InkWashLayers.blank(64, 64);
        check(!erase(blank, 20) && !blank.canUndo(), "Erasing blank paper changes nothing");

        ToneDocument cancelled = InkWashLayers.blank(64, 64);
        paint(cancelled, InkWashLayers.INK, 20, 20, 0);
        EraseBothStroke stroke = new EraseBothStroke(cancelled, InkWashSizes.Preset.SMALL);
        stroke.sample(20, 20, .25f, 0, 0); stroke.cancel();
        check(cancelled.compositeTone(20, 20) == 0, "Cancelling restores what it erased");
        ToneDocument washActive = InkWashLayers.blank(64, 64);
        paint(washActive, InkWashLayers.WASH, 20, 20, InkWashShades.WASH_DARK); paint(washActive, InkWashLayers.INK, 20, 20, 0);
        washActive.selectLayer(InkWashLayers.WASH);
        check(erase(washActive, 20) && washActive.activeLayer() == InkWashLayers.WASH, "The wash layer is active again after finish");
        EraseBothStroke again = new EraseBothStroke(washActive, InkWashSizes.Preset.SMALL);
        again.sample(50, 50, .25f, 0, 0); again.cancel();
        check(washActive.activeLayer() == InkWashLayers.WASH, "The wash layer is active again after cancel");

        ToneDocument single = new ToneDocument(64, 64);
        single.begin(); single.setTone(20, 20, 0); single.finish();
        check(erase(single, 20) && single.compositeTone(20, 20) == 255, "A one-layer document is erased too");
        ToneDocument inked = InkWashLayers.blank(64, 64);
        inked.selectLayer(InkWashLayers.INK); inked.begin();
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) inked.setTone(x, y, 0);
        inked.finish();
        EraseBothStroke finger = new EraseBothStroke(inked, 20f);
        finger.sample(32, 32, 1f, 60f, 0f); finger.finish();
        int erased = 0;
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) if (inked.compositeTone(x, y) == 255) erased++;
        check(Math.abs(erased - Math.PI * 100) < 20, "A finger erases a round spot of its own size, whatever the pressure and tilt");
        preview();
        golden(args.length > 0);
        System.out.println("PASS: eraser clears ink and wash in one undo step, leaves other marks, cancels cleanly, a finger erases at its own size, paper where it went previews the result; scripted eraser strokes over six-part ink and wash give the pages they always gave");
    }
    /**
     * While erasing, the screen shows paper wherever the eraser went, though the wash goes only at
     * lift: that preview must be exactly what the page shows once the eraser lifts.
     */
    private static void preview() {
        ToneDocument doc = InkWashLayers.blank(200, 150);
        doc.selectLayer(InkWashLayers.WASH); doc.begin();
        for (int y = 20; y < 130; y++) for (int x = 10; x < 190; x++) doc.setTone(x, y, 150 + (x + y) % 60);
        doc.finish();
        doc.selectLayer(InkWashLayers.INK); doc.begin();
        for (int y = 60; y < 70; y++) for (int x = 0; x < 200; x++) doc.setTone(x, y, 0);
        doc.finish();
        EraseBothStroke stroke = new EraseBothStroke(doc, 30f);
        check(stroke.previews(), "An ink-and-wash page previews erasing");
        check(stroke.takeErased() == null, "Nothing erased yet");
        stroke.sample(40, 65, 0, Float.NaN, Float.NaN);
        int[] first = stroke.takeErased();
        check(first != null && first[0] >= 24 && first[2] <= 56 && first[1] >= 49 && first[3] <= 81, "The first dab's area is reported");
        check(stroke.takeErased() == null, "Taking the newly erased area empties it");
        stroke.sample(150, 100, 0, Float.NaN, Float.NaN);
        int[] next = stroke.takeErased(), all = stroke.erasedBounds();
        check(next != null && next[2] > 150 && next[0] > 30, "The next samples report what they newly erased");
        check(all[0] == first[0] && all[3] == next[3], "erasedBounds covers everything erased so far");
        byte[] erased = new byte[200 * 150];
        stroke.erased(0, 0, 200, 150, erased);
        int[] predicted = new int[200 * 150];
        for (int y = 0; y < 150; y++) for (int x = 0; x < 200; x++)
            predicted[y * 200 + x] = erased[y * 200 + x] != 0 ? 255 : doc.compositeTone(x, y);
        check(stroke.finish(), "The erase changes the page");
        for (int y = 0; y < 150; y++) for (int x = 0; x < 200; x++)
            check(doc.compositeTone(x, y) == predicted[y * 200 + x], "The page after the lift is what the preview showed, at " + x + "," + y);
        // Over other pairs, the preview shows what they hold where the selected pair's paint went.
        ToneDocument pairs = InkWashLayers.blank(200, 150);
        InkWashLayers.add(pairs, true); InkWashLayers.add(pairs, true);
        for (int layer = 0; layer < 6; layer++) {
            pairs.selectLayer(layer); pairs.begin();
            for (int y = 10 + 20 * layer; y < 40 + 20 * layer; y++) for (int x = 0; x < 200; x++) pairs.setTone(x, y, layer % 2 == 0 ? 120 + (x % 50) : 0);
            pairs.finish();
        }
        InkWashLayers.select(pairs, 1);
        EraseBothStroke middle = new EraseBothStroke(pairs, 40f);
        check(middle.previews(), "Every pair previews erasing");
        for (int y = 0; y < 150; y += 6) middle.sample(100, y, 0, Float.NaN, Float.NaN);
        int[] shown = new int[200 * 150];
        middle.erasedTones(0, 0, 200, 150, shown);
        check(middle.finish(), "Erasing the middle pair changes the page");
        int previewed = 0;
        for (int y = 0; y < 150; y++) for (int x = 0; x < 200; x++) {
            int tone = shown[y * 200 + x];
            if (tone < 0) continue;
            previewed++;
            check(pairs.compositeTone(x, y) == tone, "The page after the lift is what the preview showed over the other pairs, at " + x + "," + y);
        }
        check(previewed > 1000 && pairs.compositeTone(100, 95) < 255 && pairs.compositeTone(100, 45) < 255, "It previewed, and the pairs above and below stay");
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    /**
     * The eraser must not change a pixel (WashGoldenChecks' eraser part, kept when the old watercolor went): the
     * Fineliner and the Ink wash laid on a page, the pen eraser through both, a finger eraser, then Undo. Checksums
     * recorded before the old engines were removed, and again when the Ink wash's six-part body changed (its dabs a
     * spacing apart, the Manta speed work: the eraser over the page the old body drew still gives the old sums);
     * {@code print} prints them instead (to record new ones).
     */
    static void golden(boolean print) {
        Assets.install(Assets.folder(Assets.FOLDER));
        ToneDocument doc = InkWashLayers.blank(640, 520);
        StringBuilder sums = new StringBuilder();
        ModularBrush wash = LegacyBrushes.builtin("WASH"), ink = LegacyBrushes.builtin("INK");
        // The Ink wash at .6 as kept before washes reached black (ModularSizes.sinceBlack): the page it always drew, with
        // the lattice from 100 px as then (its marks reach 50 to 100 px: since paints start it at 50 they'd draw it a
        // pixel softer), so the sums still pin the eraser alone.
        double lattice = ModularStroke.latticePx;
        ModularStroke.latticePx = 100;
        DrawingStroke s;
        try {
            s = ModularStroke.start(doc, wash, 120, ModularSizes.sinceBlack(wash, .6), null);
            for (int i = 0; i <= 200; i++) s.sample(40 + 2.8f * i, 200 + 120 * (float)Math.sin(i / 30.0), ToolSettings.pressure(.3f + .5f * (float)Math.sin(i / 64.0)), 20, 10, i * 6L);
            s.finish();
        } finally { ModularStroke.latticePx = lattice; }
        s = ModularStroke.start(doc, ink, 8, 1, null);
        for (int i = 0; i <= 200; i++) s.sample(60 + 2.6f * i, 420 - 1.5f * i, ToolSettings.pressure(.6f), 0, 0, i * 5L);
        s.finish();
        sums.append(LegacyConversionChecks.crc(doc));
        EraseBothStroke eraser = new EraseBothStroke(doc, InkWashSizes.Preset.MEDIUM);
        // Raw .3 to .8 under the old .05–.45 line, kept as that travel since the knee (2026-10-29).
        for (int i = 0; i <= 100; i++) eraser.sample(600 - 5 * i, 60 + 4 * i, ToolSettings.pressure(Math.min(1, (.25f + .005f * i) / .40f)), 0, 0, i * 4L);
        eraser.finish();
        sums.append(' ').append(LegacyConversionChecks.crc(doc));
        EraseBothStroke finger = new EraseBothStroke(doc, 40f);
        for (int i = 0; i <= 80; i++) finger.sample(100 + 5 * i, 300, .5f, 0, 0, i * 8L);
        finger.finish();
        sums.append(' ').append(LegacyConversionChecks.crc(doc));
        doc.undo(); doc.undo();
        sums.append(' ').append(LegacyConversionChecks.crc(doc));
        if (print) { System.out.println(sums); return; }
        check(sums.toString().equals(ERASED), "The eraser gives exactly the pages it gave: " + sums + " vs " + ERASED);
    }
    private static final String ERASED = "19d50d8 4121e586 74fe9dbd 19d50d8";
    private static void paint(ToneDocument doc, int layer, int x, int y, int tone) {
        doc.selectLayer(layer); doc.begin(); doc.setTone(x, y, tone); doc.finish();
    }
    private static boolean erase(ToneDocument doc, int y) {
        EraseBothStroke stroke = new EraseBothStroke(doc, InkWashSizes.Preset.SMALL);
        for (int x = 14; x <= 26; x += 3) stroke.sample(x, y, .25f, 0, 0);
        return stroke.finish();
    }
}
