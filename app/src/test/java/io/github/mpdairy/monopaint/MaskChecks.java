package io.github.mpdairy.monopaint;

import java.io.*;
import java.util.Arrays;

/** Masking fluid: it shields every layer from paint, erasing, shapes, fills and seeping wet paint until rubbed or peeled off. */
final class MaskChecks {
    public static void main(String[] args) throws Exception {
        shields(); partial(); rubbing(); history(); persistence(); wetSeeping(); shapesAndFills(); pageEdits();
        System.out.println("PASS: masking fluid shields opaque, transparent and wet paint, erasing, shapes and fills; partial fluid; rubbing off; peel and paint history; saves; clear and expand");
    }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
    private static void fluid(ToneDocument doc, int x, int y, int w, int h) {
        doc.paintMaskingFluid(true); doc.begin();
        for (int row = y; row < y+h; row++) for (int col = x; col < x+w; col++) doc.paintTone(col, row, 0);
        doc.finish(); doc.paintMaskingFluid(false);
    }
    private static void fill(ToneDocument doc, int gray) {
        doc.begin();
        for (int y = 0; y < doc.height; y++) for (int x = 0; x < doc.width; x++) doc.paintTone(x, y, gray);
        doc.finish();
    }
    private static void shields() {
        ToneDocument doc = new ToneDocument(32, 32);
        fill(doc, 200);
        fluid(doc, 8, 8, 8, 8);
        check(doc.hasMask() && doc.compositeTone(10, 10) == 200, "Masking fluid is not part of the artwork");
        fill(doc, 40);
        check(doc.compositeTone(10, 10) == 200 && doc.compositeTone(2, 2) == 40, "Opaque paint goes around the fluid");
        int[] stamp = new int[32*32]; Arrays.fill(stamp, 0xff000000);
        doc.begin(); doc.transparentMask(stamp, 32, 0, 0, 32, 32, 0); doc.finish();
        check(doc.compositeTone(10, 10) == 200 && doc.compositeTone(2, 2) < 40, "Transparent paint goes around the fluid");
        doc.begin(); doc.eraseMask(stamp, 32, 0, 0, 32, 32); doc.finish();
        check(doc.compositeTone(10, 10) == 200 && doc.compositeTone(2, 2) == 255, "Erasing goes around the fluid");
        doc.addLayer(); fill(doc, 0);
        check(doc.compositeTone(10, 10) == 200, "The fluid shields every layer");
        check(doc.maskHatch(9, 15) >= 0 && doc.maskHatch(12, 15) >= 0 && doc.maskHatch(13, 15) < 0, "The fluid shows as hatching lines four pixels across");
        check(doc.maskHatch(2, 2) < 0, "Unmasked paper shows no hatching");
    }
    private static void partial() {
        ToneDocument doc = new ToneDocument(4, 4);
        doc.paintMaskingFluid(true); doc.begin(); doc.paintTone(1, 1, 0, 128); doc.finish(); doc.paintMaskingFluid(false);
        fill(doc, 0);
        int tone = doc.compositeTone(1, 1);
        check(tone > 100 && tone < 155, "Half-strength fluid lets about half the paint through (" + tone + ")");
        doc.begin(); doc.paintTone(1, 1, 0); doc.paintTone(1, 1, 0); doc.finish();
        check(doc.compositeTone(1, 1) <= tone, "Paint keeps working into thin fluid with each gesture");
    }
    private static void rubbing() {
        ToneDocument doc = new ToneDocument(16, 16);
        fluid(doc, 0, 0, 16, 16);
        doc.paintMaskingFluid(true); doc.begin(); doc.eraseTone(3, 3, 1); doc.finish(); doc.paintMaskingFluid(false);
        fill(doc, 30);
        check(doc.compositeTone(3, 3) == 30 && doc.compositeTone(4, 4) == 255, "Erasing masking fluid rubs off only where it touches");
    }
    private static void history() {
        ToneDocument doc = new ToneDocument(16, 16);
        fluid(doc, 0, 0, 4, 4);
        check(doc.peelMask() && !doc.hasMask(), "Peeling removes all the fluid");
        check(!doc.peelMask(), "Peeling a bare page adds no step");
        fluid(doc, 8, 8, 4, 4);
        fill(doc, 50);
        byte[] painted = doc.snapshot();
        check(doc.compositeTone(9, 9) == 255 && doc.compositeTone(1, 1) == 50, "Fluid laid after a peel shields alone");
        check(doc.undo() && doc.undo() && !doc.hasMask(), "Undo removes fluid laid after a peel");
        check(doc.undo() && doc.hasMask(), "Undo puts peeled fluid back");
        check(doc.undo() && !doc.hasMask() && !doc.canUndo(), "Laying fluid is one step");
        check(doc.redo() && doc.redo() && !doc.hasMask() && doc.redo() && doc.hasMask() && doc.redo(), "Redo replays the fluid and peel");
        check(Arrays.equals(doc.snapshot(), painted), "Redo restores paint around the later fluid");
        doc.paintMaskingFluid(true); doc.begin(); doc.cancel(); doc.paintMaskingFluid(false);
        doc.begin(); doc.paintTone(9, 9, 0); doc.finish();
        check(doc.compositeTone(9, 9) == 255, "A cancelled fluid gesture returns gestures to the layer");
    }
    private static void persistence() throws Exception {
        ToneDocument bare = new ToneDocument(8, 8);
        check(new DataInputStream(new ByteArrayInputStream(DrawingBook.encode(bare))).readInt() == 0x54534d33, "Pages without fluid keep the earlier format");
        ToneDocument doc = new ToneDocument(8, 8);
        fluid(doc, 2, 2, 2, 2);
        byte[] saved = DrawingBook.encode(doc);
        check(new DataInputStream(new ByteArrayInputStream(saved)).readInt() == 0x54534d34, "Pages with fluid save it");
        ToneDocument restored = DocumentCodec.read(new ByteArrayInputStream(saved));
        fill(restored, 0);
        check(restored.compositeTone(2, 2) == 255 && restored.compositeTone(0, 0) == 0, "Saved fluid still shields");
        DocumentCodec.validate(new ByteArrayInputStream(saved), 8, 8);
        DrawingBook book = new DrawingBook(doc); book.addPage(); book.select(0);
        check(book.current().hasMask(), "Fluid survives page eviction");
        check(book.snapshot().page(0).hasMask(), "Book snapshots keep the current page's fluid");
    }
    private static void wetSeeping() {
        ToneDocument doc = new ToneDocument(96, 64);
        fluid(doc, 40, 0, 16, 64);
        WetWatercolor wet = new WetWatercolor(doc);
        int[] stamp = new int[40*64]; Arrays.fill(stamp, 0xff000000);
        doc.begin(); wet.beginStroke(); wet.paintMask(stamp, 40, 0, 0, 40, 64, 0); wet.finishStroke();
        int frames = 0;
        while (wet.isAnimating()) { wet.advance(false); check(++frames < 1000, "Animation terminates"); }
        check(doc.compositeTone(39, 32) < 128, "Wet paint reaches the fluid's edge");
        for (int x = 40; x < 56; x++) check(doc.compositeTone(x, 32) == 255, "Wet paint does not seep under the fluid");
    }
    private static void shapesAndFills() {
        ToneDocument doc = new ToneDocument(16, 16);
        fluid(doc, 4, 0, 4, 16);
        doc.begin(); doc.paintSpan(0, 16, 3, 0); doc.finish();
        check(doc.compositeTone(5, 3) == 255 && doc.compositeTone(0, 3) == 0 && doc.compositeTone(10, 3) == 0, "Shapes go around the fluid");
        FloodFill fill = new FloodFill(doc, 0, 8, 100); while (!fill.advance(1 << 20)) { } fill.finish();
        check(doc.compositeTone(5, 8) == 255 && doc.compositeTone(10, 8) == 100, "Fills go around the fluid");
        ToneDocument region = new ToneDocument(16, 16);
        region.begin(); for (int y = 0; y < 16; y++) region.paintTone(8, y, 0); region.finish();
        region.paintMaskingFluid(true);
        FloodFill masking = new FloodFill(region, 2, 2, 0); while (!masking.advance(1 << 20)) { } masking.finish();
        region.paintMaskingFluid(false);
        fill(region, 120);
        check(region.compositeTone(2, 2) == 255 && region.compositeTone(12, 2) == 120, "A fill lays fluid over the painted region it starts in");
    }
    private static void pageEdits() {
        ToneDocument doc = new ToneDocument(8, 8);
        fluid(doc, 0, 0, 2, 2);
        doc.expand(1, 1, 0, 0);
        fill(doc, 0);
        check(doc.compositeTone(1, 1) == 255 && doc.compositeTone(0, 0) == 0, "Expanding moves the fluid with the painting");
        doc.setLayerVisible(0, false);
        fluid(doc, 5, 5, 1, 1);
        doc.setLayerVisible(0, true);
        check(doc.clear() && doc.hasMask(), "Clearing a layer leaves the fluid");
        check(doc.clearAllLayers() && !doc.hasMask(), "Clearing the page peels the fluid too");
        check(doc.undo() && doc.hasMask(), "Undoing the clear puts the fluid back");
    }
}
