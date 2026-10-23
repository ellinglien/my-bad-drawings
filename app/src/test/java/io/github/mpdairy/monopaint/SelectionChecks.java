package io.github.mpdairy.monopaint;

import java.util.Arrays;

public final class SelectionChecks {
    public static void main(String[] args) {
        liftAndMove(); unmovedPlacesNothing(); loopShape(); quarterTurn(); scaleHoldsCorner(); handles();
        importAsLayer(); importOntoLayer(); emptyLoop(); importedGrays();
        System.out.println("Selection checks passed");
    }
    private static float[] box(float l, float t, float r, float b) { return new float[]{l, t, r, t, r, b, l, b}; }
    private static ToneDocument square() {
        ToneDocument doc = new ToneDocument(120, 100);
        doc.begin();
        for (int y = 20; y < 40; y++) for (int x = 30; x < 50; x++) doc.paintTone(x, y, 40 + x - 30);
        doc.finish();
        return doc;
    }
    private static void liftAndMove() {
        ToneDocument doc = square(); doc.addLayer(); doc.selectLayer(0);
        byte[] before = doc.snapshot();
        FloatingSelection piece = FloatingSelection.lift(doc, box(10, 10, 70, 60), 4);
        check(piece != null && piece.width == 20 && piece.height == 20, "Handles hug the lifted artwork");
        check(Arrays.equals(before, doc.snapshot()), "Lifting leaves the page untouched");
        piece.grab(FloatingSelection.MOVE);
        piece.drag(FloatingSelection.MOVE, 40, 30, 50.4f, 35.6f);
        check(piece.place(doc), "Moved piece is placed");
        check(doc.opacity(30, 20) == 0 && doc.opacity(39, 25) == 0, "Lifted area is cleared");
        check(doc.layerTone(40, 26) == 40 && doc.layerTone(59, 45) == 59 && doc.opacity(40, 26) == 255, "Moves by whole pixels exactly");
        check(doc.layerCount() == 2 && doc.activeLayer() == 0, "Stays on its layer");
        byte[] after = doc.snapshot();
        check(doc.undo() && Arrays.equals(before, doc.snapshot()), "One undo puts the piece back");
        check(doc.redo() && Arrays.equals(after, doc.snapshot()), "Redo moves it again");
    }
    private static void unmovedPlacesNothing() {
        ToneDocument doc = square();
        FloatingSelection piece = FloatingSelection.lift(doc, box(0, 0, 120, 100), 4);
        check(!piece.place(doc), "Unmoved piece changes nothing");
        int steps = 0; while (doc.undo()) steps++;
        check(steps == 1, "An unmoved piece adds no undo step");
    }
    private static void loopShape() {
        ToneDocument doc = square();
        // A triangle over the square's top-left half.
        FloatingSelection piece = FloatingSelection.lift(doc, new float[]{30, 20, 50, 20, 30, 40}, 3);
        piece.grab(FloatingSelection.MOVE); piece.drag(FloatingSelection.MOVE, 0, 0, 60, 0);
        piece.place(doc);
        check(doc.opacity(31, 21) == 0 && doc.opacity(48, 38) == 255, "Only pixels inside the loop lift");
        check(doc.opacity(91, 21) == 255 && doc.opacity(108, 38) == 0, "The loop's shape moves");
    }
    private static void quarterTurn() {
        ToneDocument doc = new ToneDocument(60, 60);
        doc.begin(); doc.paintTone(20, 20, 10); doc.paintTone(21, 20, 20); doc.paintTone(22, 20, 30);
        doc.paintTone(20, 21, 40); doc.paintTone(21, 21, 50); doc.paintTone(22, 21, 60); doc.finish();
        FloatingSelection piece = FloatingSelection.lift(doc, box(0, 0, 60, 60), 4);
        float[] knob = piece.knob(0, -1, 10, new float[]{0, 0, 60, 60});
        piece.grab(FloatingSelection.ROTATE);
        // A turn just short of a quarter settles on it.
        float cx = piece.centerX, cy = piece.centerY;
        piece.drag(FloatingSelection.ROTATE, cx, cy - 10, cx + 10, cy - 0.5f);
        piece.release();
        check(piece.angle == Math.PI / 2, "Turns snap to a quarter turn");
        float[] after = piece.knob(0, -1, 10, new float[]{0, 0, 60, 60});
        check(after[0] > piece.centerX + 5 && after[5] == 0, "The knob turns with the piece, staying where the pen left it");
        piece.place(doc);
        int[] expected = {40, 10, 50, 20, 60, 30};
        int found = 0;
        for (int y = 0; y < 60; y++) for (int x = 0; x < 60; x++) if (doc.opacity(x, y) != 0) {
            check(doc.opacity(x, y) == 255, "Quarter turns keep full coverage");
            check(doc.layerTone(x, y) == expected[found++], "Quarter turns keep every pixel");
        }
        check(found == 6, "Quarter turn moves no pixel twice");
    }
    private static void scaleHoldsCorner() {
        ToneDocument doc = square();
        FloatingSelection piece = FloatingSelection.lift(doc, box(0, 0, 120, 100), 4);
        float[] c = piece.corners();
        piece.grab(FloatingSelection.SCALE + 2);
        piece.drag(FloatingSelection.SCALE + 2, c[4], c[5], c[4] + 20, c[5] + 20);
        float[] after = piece.corners();
        check(Math.abs(piece.scale - 2) < 1e-4 && Math.abs(after[0] - c[0]) < 1e-3 && Math.abs(after[1] - c[1]) < 1e-3,
                "Scaling holds the opposite corner still");
        piece.place(doc);
        check(doc.opacity(30, 20) == 255 && doc.opacity(68, 58) == 255 && doc.opacity(71, 61) == 0, "Scaled piece covers twice the size");
    }
    private static void handles() {
        ToneDocument doc = square();
        FloatingSelection piece = FloatingSelection.lift(doc, box(0, 0, 120, 100), 4);
        piece.grab(FloatingSelection.SCALE + 2);
        piece.drag(FloatingSelection.SCALE + 2, 50, 40, 80, 70);
        piece.release();
        float[] knob = piece.knob(0, -1, 8, new float[]{0, 0, 120, 100});
        check(Math.abs(knob[0] - 55) < 1e-3 && Math.abs(knob[1] - 12) < 1e-3, "Knob stands above the top edge");
        check(piece.hit(55, 13, knob, 4) == FloatingSelection.ROTATE, "Knob turns");
        check(piece.hit(79, 69, knob, 4) == FloatingSelection.SCALE + 2, "Corner scales");
        check(piece.hit(55, 50, knob, 4) == FloatingSelection.MOVE, "Inside moves");
        check(piece.hit(5, 5, knob, 4) == FloatingSelection.NONE, "Outside finishes");
        FloatingSelection top = FloatingSelection.lift(square(), box(30, 20, 50, 23), 4);
        float[] inside = top.knob(0, -1, 30, new float[]{0, 0, 120, 100});
        check(inside[1] > 20 && inside[1] < 23 && inside[0] == 40 && inside[5] == 1, "Knob moves inside a piece whose top edge is at the top of the screen");
        check(top.knob(0, -1, 30, new float[]{0, -100, 120, 100})[5] == 1, "Once inside, the knob stays inside");
        // A turn that carries the knob off the screen leaves it under the pen until the pen lifts.
        FloatingSelection middle = FloatingSelection.lift(square(), box(0, 0, 120, 100), 4);
        float[] screen = {25, 0, 120, 100};
        check(middle.knob(0, -1, 8, screen)[5] == 0, "Knob starts outside where there is room");
        middle.grab(FloatingSelection.ROTATE);
        middle.drag(FloatingSelection.ROTATE, 40, 12, 32, 30);
        float[] turned = middle.knob(0, -1, 8, screen);
        check(turned[5] == 0 && turned[0] < 25, "Knob stays outside during a turn");
        middle.release();
        check(middle.knob(0, -1, 8, screen)[5] == 1, "Knob moves inside once the turn ends");
    }
    private static void importAsLayer() {
        ToneDocument doc = square();
        byte[] before = doc.snapshot();
        byte[] tones = new byte[4 * 3], alpha = new byte[12];
        Arrays.fill(tones, (byte)0); Arrays.fill(alpha, (byte)255); alpha[0] = 0; tones[0] = (byte)255;
        FloatingSelection picture = FloatingSelection.imported(doc, 4, 3, tones, alpha, 0, true);
        check(picture.bounds()[0] == 58 && picture.bounds()[1] == 49 && picture.bounds()[2] == 62, "Pictures arrive centered on whole pixels");
        check(picture.place(doc) && doc.layerCount() == 2 && doc.activeLayer() == 1, "Picture is placed on a new layer");
        check(doc.layerTone(59, 49) == 0 && doc.opacity(58, 49) == 0, "Transparent picture pixels stay clear");
        check(doc.undo() && doc.layerCount() == 1 && Arrays.equals(before, doc.snapshot()), "One undo removes the new layer");
        byte[] wide = new byte[6];
        FloatingSelection turned = FloatingSelection.imported(doc, 3, 2, wide, new byte[6], Math.PI / 2, true);
        int[] b = turned.bounds();
        check(b[2] - b[0] == 2 && b[3] - b[1] == 3, "A turned picture stands upright on the turned screen");
    }
    private static void importOntoLayer() {
        ToneDocument doc = square();
        byte[] tones = new byte[4], alpha = new byte[4];
        Arrays.fill(alpha, (byte)128);
        FloatingSelection picture = FloatingSelection.imported(doc, 2, 2, tones, alpha, 0, false);
        check(picture.place(doc) && doc.layerCount() == 1, "Picture is placed onto the current layer");
        check(doc.opacity(60, 50) == 128 && doc.layerTone(60, 50) == 127, "Translucent picture pixels lie over bare paper");
        check(doc.layerTone(40, 30) == 50, "Artwork outside the picture stays");
    }
    private static void emptyLoop() {
        ToneDocument doc = square();
        check(FloatingSelection.lift(doc, box(80, 60, 110, 90), 4) == null, "A loop around nothing lifts nothing");
        check(FloatingSelection.lift(doc, new float[]{1, 1, 5, 5}, 2) == null, "A line is not a loop");
    }
    private static void importedGrays() {
        for (int tone = 0; tone < 256; tone++) {
            int exported = DotPattern.exportGray(tone);
            check(DotPattern.exportGray(DotPattern.importGray(exported)) == exported, "An exported gray imports as the tone it came from");
        }
        check(DotPattern.importGray(0) == 0 && DotPattern.importGray(255) == 255, "Black and white import unchanged");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
