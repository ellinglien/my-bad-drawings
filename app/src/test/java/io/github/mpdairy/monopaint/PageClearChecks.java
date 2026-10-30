package io.github.mpdairy.monopaint;

import java.io.IOException;
import java.util.Arrays;

/**
 * Clear page ({@link PageClear}, {@link ToneDocument#clearPage}): offered only on a page with something on it; it takes
 * off every layer's marks, hidden ones too, the masking fluid and the magnets, keeping the layers and the shade, as one
 * Undo step; Undo gives the page back byte for byte, Redo clears it again; on a full Manta page with Erasable's shares
 * the step stays undoable after more strokes.
 */
public final class PageClearChecks {
    static final int W = PageShadeChecks.W, H = PageShadeChecks.H;

    public static void main(String[] args) throws IOException {
        Assets.install(Assets.folder(Assets.FOLDER));
        offered(); clears(); undo(); budget();
        System.out.println("PASS: Clear page: offered only on a page with paint, fluid or a magnet (not on a blank or only shaded one); "
                + "it takes off every layer's marks, a hidden layer's too, the masking fluid and the magnets as one Undo step, keeping the layers, their names, visibility and opacity, and the shade; "
                + "Undo gives the page back byte for byte (its file, every layer's pixels and shares, the fluid, the magnets), Redo clears it again; "
                + "a blank page isn't cleared and gains no step; on a full Manta page with shares the clear stays undoable after more strokes");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /** A page with everything on it: wash and ink by brush, a permanent mark (shares), a hidden layer pair, masking fluid, magnets and a shade. */
    static ToneDocument full() {
        ToneDocument d = InkWashLayers.blank(W, H);
        check(d.shadePaper(170), "shades");
        PageShadeChecks.live(d, PageShadeChecks.instant(PageShadeChecks.own(PageShadeChecks.brush(PageShadeChecks.WASH))), 40, .6, PageShadeChecks.across(20, 220, 60));
        PageShadeChecks.live(d, PageShadeChecks.instant(PageShadeChecks.brush(PageShadeChecks.INK)), 6, 1, PageShadeChecks.across(30, 210, 50));
        d.begin(.3); for (int x = 10; x < 90; x++) d.setTone(x, 140, 20); d.finish();
        check(InkWashLayers.add(d, true), "adds a pair");
        d.begin(0); for (int x = 100; x < 160; x++) d.setTone(x, 100, 60); d.finish();
        check(InkWashLayers.show(d, 1, false), "hides it");
        InkWashLayers.select(d, 0);
        d.setLayerOpacity(2, 64);
        d.beginFluid(1); for (int x = 40; x < 120; x++) d.setTone(x, 30, 0); d.finish();
        d.setMagnets(Magnets.NONE.with(new Magnets.Magnet("moon", 70, 50, -12, 0)).with(new Magnets.Magnet("tide", 150, 120, 8, 1)));
        return d;
    }
    /** Everything the page holds, as bytes: its file, then every layer's and the fluid's own arrays. */
    static byte[] all(ToneDocument d) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(DrawingBook.encode(d));
        ToneDocument.Snapshot s = d.layerSnapshot();
        java.util.List<ToneDocument.Layer> layers = new java.util.ArrayList<>(s.layers);
        if (s.mask != null) layers.add(s.mask);
        for (ToneDocument.Layer l : layers) {
            out.write(l.name.getBytes("UTF-8")); out.write(l.visible ? 1 : 0); out.write(l.opacity);
            out.write(l.tones); out.write(l.alpha); out.write(l.share == null ? new byte[]{7} : l.share);
        }
        out.write(d.magnets().encode()); out.write(d.paperTone()); out.write(d.activeLayer());
        s.release();
        return out.toByteArray();
    }
    static boolean blankLayers(ToneDocument d) {
        ToneDocument.Snapshot s = d.layerSnapshot();
        boolean blank = !s.layers.isEmpty();
        for (ToneDocument.Layer l : s.layers) for (byte a : l.alpha) blank &= a == 0;
        s.release();
        return blank;
    }

    static void offered() {
        check(!PageClear.offered(InkWashLayers.blank(W, H)) && !PageClear.offered(null), "A blank page isn't offered Clear page");
        check(!PageClear.offered(PageShadeChecks.shaded(90)), "nor a page with only its shade");
        ToneDocument worded = InkWashLayers.blank(W, H);
        worded.setMagnets(Magnets.NONE.with(new Magnets.Magnet("moon", 70, 50, 0, 0)));
        ToneDocument fluid = InkWashLayers.blank(W, H);
        fluid.beginFluid(1); fluid.setTone(5, 5, 0); fluid.finish();
        check(PageClear.offered(worded) && PageClear.offered(fluid) && PageClear.offered(full()), "A page with a magnet, fluid or paint is");
        // The words: a button of two words, a note of about three, no hints (docs/TEXT-STYLE.md).
        check(PageClear.CLEAR.equals("Clear page") && PageClear.CLEARED.equals("Page cleared") && !PageClear.CLEARED.contains("ndo"), "short words");
    }
    static void clears() {
        ToneDocument d = full();
        int steps = d.undoSteps(), layers = d.layerCount(), active = d.activeLayer();
        check(d.clearPage(), "Clear page clears");
        check(d.undoSteps() == steps + 1, "as one Undo step");
        check(d.unmarked() && blankLayers(d) && !d.hasMask() && !d.hasMaskLayer() && d.magnets().isEmpty(), "every layer, the hidden one too, the fluid and the magnets");
        check(d.layerCount() == layers && d.activeLayer() == active && !d.layerVisible(2) && !d.layerVisible(3) && d.layerOpacity(2) == 64
                && d.layerName(0).equals("Wash") && d.layerName(1).equals("Ink"), "keeping the layers, their names, visibility and opacity");
        check(d.paperTone() == 170 && d.compositeTone(120, 60) == 170, "and the page's shade");
        check(!d.clearPage() && d.undoSteps() == steps + 1, "A cleared page clears no more, and gains no step");
        ToneDocument blank = InkWashLayers.blank(W, H);
        check(!blank.clearPage() && !blank.canUndo(), "A blank page isn't cleared");
    }
    static void undo() throws IOException {
        ToneDocument d = full();
        byte[] before = all(d);
        check(d.clearPage(), "clears");
        byte[] cleared = all(d);
        check(d.undo() && Arrays.equals(all(d), before), "Undo gives the page back byte for byte: layers, shares, fluid, magnets, shade");
        check(d.magnets().size() == 2 && d.hasMask() && d.paperTone() == 170, "the magnets and fluid too");
        check(d.redo() && Arrays.equals(all(d), cleared) && d.magnets().isEmpty(), "Redo clears it again");
        check(d.undo() && Arrays.equals(all(d), before), "and Undo again gives it back");
        // Undo past the clear goes on to the steps before it, as ever: the magnets laid last.
        check(d.undo() && d.magnets().isEmpty() && d.hasMask(), "Undo before the clear takes back the magnets laid before it");
        // Drawn on after a clear: Undo takes the stroke back, then the clear.
        ToneDocument e = full();
        byte[] was = all(e);
        check(e.clearPage(), "clears");
        PageShadeChecks.live(e, PageShadeChecks.instant(PageShadeChecks.brush(PageShadeChecks.INK)), 6, 1, PageShadeChecks.across(30, 60, 90));
        check(!e.unmarked() && e.undo() && e.unmarked() && e.undo() && Arrays.equals(all(e), was), "Undo takes back a stroke on the cleared page, then the clear");
    }
    /** A full Manta page, both layers holding Erasable's shares: the clear holds some 30 MB by reference and stays undoable after more strokes. */
    static void budget() {
        int w = 1920, h = 2560;
        ToneDocument d = InkWashLayers.blank(w, h);
        for (int layer = 0; layer < 2; layer++) {
            d.selectLayer(layer);
            d.begin(.4); for (int x = 0; x < w; x += 3) d.setTone(x, 100 + layer, 40); d.finish();
        }
        d.selectLayer(InkWashLayers.INK);
        ToneDocument.Snapshot s = d.layerSnapshot();
        check(s.layers.get(0).share != null && s.layers.get(1).share != null, "both layers hold shares");
        s.release();
        check(d.clearPage(), "clears the full page");
        for (int stroke = 0; stroke < 8; stroke++) { d.begin(); for (int x = 0; x < 600; x++) d.setTone(x, 400 + stroke * 50, 0); d.finish(); }
        for (int stroke = 0; stroke < 8; stroke++) check(d.undo(), "undoes stroke " + stroke);
        check(d.unmarked() && d.undo() && !d.unmarked() && d.layerTone(0, 101) < 255, "The clear is still undoable after eight strokes on it");
    }

    private PageClearChecks() {}
}
