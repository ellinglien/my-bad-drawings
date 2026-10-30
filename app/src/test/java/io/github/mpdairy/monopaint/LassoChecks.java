package io.github.mpdairy.monopaint;

import java.util.Arrays;

/** The Lasso on Inkwash's pages ({@link LassoPlanes}): both of a pair's layers and the masking fluid, every plane, carried; Undo, Delete, Copy, another page, Cut and Paste, the loop that deletes. */
public final class LassoChecks {
    public static void main(String[] args) throws Exception {
        carried(); turned(); deleted(); copied(); anotherPage(); shown(); filed(); pasted(); fastDelete();
        System.out.println("Lasso checks passed");
    }
    private static float[] box(float l, float t, float r, float b) { return new float[]{l, t, r, t, r, b, l, b}; }
    /** A pair: a Paint wash, permanent Pen ink with print tone, a Pencil mark (the ink's kinds plane), wax over a corner. */
    private static ToneDocument page() {
        ToneDocument doc = InkWashLayers.blank(100, 80);
        doc.selectLayer(InkWashLayers.WASH);
        doc.begin(1, ToneDocument.KIND_PAINT);
        for (int y = 20; y < 40; y++) for (int x = 20; x < 40; x++) doc.paintTone(x, y, 120, 128);
        doc.finish();
        doc.selectLayer(InkWashLayers.INK);
        doc.begin(.4, ToneDocument.KIND_PEN);
        for (int y = 25; y < 35; y++) for (int x = 25; x < 35; x++) doc.paintTone(x, y, 0);
        doc.printing();
        doc.setPrints(25, 30, 4, new int[]{90, 80, 70, 60});
        doc.finish();
        doc.begin(1, ToneDocument.KIND_PENCIL); doc.paintTone(36, 36, 60); doc.finish();
        doc.beginFluid(.5); doc.paintTone(22, 22, 0); doc.finish();
        return doc;
    }
    /** Layer {@code index}'s (-1: the fluid) planes over w × h at x, y, one after another: its base's, then each kind plane's (blank where none). */
    private static byte[] planes(ToneDocument doc, int index, int x, int y, int w, int h) {
        int n = w * h, kinds = index < 0 ? 0 : ToneDocument.PLANES;
        byte[] t = new byte[n], a = new byte[n], s = new byte[n], p = new byte[n], k = new byte[n], all = new byte[5 * n * (1 + kinds)];
        for (int plane = -1; plane < kinds; plane++) {
            int at = 5 * n * (plane + 1);
            doc.copyPlanes(index, plane, x, y, w, h, t, a, s, p, k);
            System.arraycopy(t, 0, all, at, n); System.arraycopy(a, 0, all, at + n, n); System.arraycopy(s, 0, all, at + 2 * n, n);
            System.arraycopy(p, 0, all, at + 3 * n, n); System.arraycopy(k, 0, all, at + 4 * n, n);
        }
        return all;
    }
    private static byte[][] whole(ToneDocument doc) {
        return new byte[][]{planes(doc, 0, 0, 0, doc.width, doc.height), planes(doc, 1, 0, 0, doc.width, doc.height), planes(doc, -1, 0, 0, doc.width, doc.height)};
    }
    /** Layer {@code layer}'s coverage at x, y as it looks: its base and kind planes. */
    private static int alphaAt(ToneDocument doc, int layer, int x, int y) { int[] t = new int[1], c = new int[1]; doc.layerPixels(layer, x, y, 1, 1, t, c); return c[0]; }
    private static boolean bare(ToneDocument doc, int x, int y, int w, int h) {
        for (int index = -1; index <= 1; index++) if (!Arrays.equals(planes(doc, index, x, y, w, h), planes(InkWashLayers.blank(doc.width, doc.height), index, x, y, w, h))) return false;
        return true;
    }
    private static FloatingSelection moved(ToneDocument doc, float dx, float dy) {
        FloatingSelection piece = LassoPlanes.lift(doc, box(10, 10, 50, 50), 4);
        piece.grab(FloatingSelection.MOVE); piece.drag(FloatingSelection.MOVE, 0, 0, dx, dy); piece.release();
        return piece;
    }

    private static void carried() {
        ToneDocument doc = page();
        byte[][] before = whole(doc);
        byte[] wash = planes(doc, 0, 20, 20, 20, 20), ink = planes(doc, 1, 20, 20, 20, 20), fluid = planes(doc, -1, 20, 20, 20, 20);
        FloatingSelection piece = LassoPlanes.lift(doc, box(10, 10, 50, 50), 4);
        check(piece != null && piece.width == 20 && piece.height == 20 && piece.cutX == 20, "The frame hugs the wash, the ink and the fluid");
        check(Arrays.deepEquals(before, whole(doc)), "Lifting leaves the page untouched");
        piece.grab(FloatingSelection.MOVE); piece.drag(FloatingSelection.MOVE, 0, 0, 50, 30); piece.release();
        check(piece.place(doc), "The moved piece is laid");
        check(Arrays.equals(planes(doc, 0, 70, 50, 20, 20), wash) && Arrays.equals(planes(doc, 1, 70, 50, 20, 20), ink)
                && Arrays.equals(planes(doc, -1, 70, 50, 20, 20), fluid), "Wash, ink and fluid land whole: tones, coverage, Erasable's share, print tone, kinds");
        check(bare(doc, 20, 20, 20, 20), "Where it was is bare, every plane");
        check(doc.activeLayer() == InkWashLayers.INK, "The ink stays selected");
        byte[][] after = whole(doc);
        check(doc.undo() && Arrays.deepEquals(before, whole(doc)), "One Undo puts it back");
        check(doc.redo() && Arrays.deepEquals(after, whole(doc)), "Redo moves it again");
        check(!LassoPlanes.lift(doc, box(60, 40, 99, 79), 4).place(doc), "A piece not moved changes nothing");
        check(LassoPlanes.lift(doc, box(1, 1, 15, 15), 4) == null, "A loop round nothing lifts nothing");
        // Only what lies inside the loop: a triangle over the ink's top left.
        ToneDocument tri = page();
        FloatingSelection half = LassoPlanes.lift(tri, new float[]{20, 20, 40, 20, 20, 40}, 3);
        half.grab(FloatingSelection.MOVE); half.drag(FloatingSelection.MOVE, 0, 0, 50, 0); half.place(tri);
        check(alphaAt(tri, 1, 26, 26) == 0 && alphaAt(tri, 1, 33, 33) == 255 && alphaAt(tri, 1, 76, 26) == 255, "Only pixels inside the loop lift");
    }
    private static void turned() {
        ToneDocument doc = page();
        byte[] ink = planes(doc, 1, 20, 20, 20, 20);
        FloatingSelection piece = LassoPlanes.lift(doc, box(10, 10, 50, 50), 4);
        float cx = piece.centerX, cy = piece.centerY;
        piece.grab(FloatingSelection.ROTATE); piece.drag(FloatingSelection.ROTATE, cx, cy - 10, cx + 10, cy - .5f); piece.release();
        check(piece.angle == Math.PI / 2, "A turn settles on a quarter");
        piece.place(doc);
        byte[] turned = planes(doc, 1, 20, 20, 20, 20);
        // A quarter turn moves each pixel, every plane with it: the same planes' values, in other places.
        int n = 400;
        for (int plane = 0; plane < 5; plane++) {
            byte[] was = Arrays.copyOfRange(ink, plane * n, plane * n + n), now = Arrays.copyOfRange(turned, plane * n, plane * n + n);
            Arrays.sort(was); Arrays.sort(now);
            check(Arrays.equals(was, now), "A quarter turn keeps every pixel of every plane");
        }
        check(alphaAt(doc, 1, 34, 25) == 255 && alphaAt(doc, 1, 25, 34) == 255, "The ink square turns in place");
    }
    private static void deleted() {
        ToneDocument doc = page();
        byte[][] before = whole(doc);
        FloatingSelection piece = moved(doc, 30, 0);
        check(piece.planes.place(doc, piece, false, true), "Delete takes what was lifted");
        check(bare(doc, 0, 0, 100, 80), "Nothing left of it anywhere");
        check(doc.undo() && Arrays.deepEquals(before, whole(doc)), "One Undo puts it back");
    }
    private static void copied() {
        ToneDocument doc = page();
        byte[] ink = planes(doc, 1, 20, 20, 20, 20);
        FloatingSelection piece = moved(doc, 50, 30), twin = piece.copy();
        check(!twin.lifted && twin.samePose(piece) && twin.planes == piece.planes, "A copy is lifted from nowhere, where the piece lies");
        check(piece.planes.place(doc, piece, true, false), "Laid without cutting, it adds one");
        check(Arrays.equals(planes(doc, 1, 20, 20, 20, 20), ink) && Arrays.equals(planes(doc, 1, 70, 50, 20, 20), ink), "The page keeps the original and gains the copy");
        twin.grab(FloatingSelection.MOVE); twin.drag(FloatingSelection.MOVE, 0, 0, -60, 0); twin.release();
        check(twin.place(doc) && Arrays.equals(planes(doc, 1, 10, 50, 20, 20), ink) && Arrays.equals(planes(doc, 1, 20, 20, 20, 20), ink),
                "The floating copy goes down elsewhere, cutting nothing");
    }
    private static void anotherPage() {
        ToneDocument doc = page();
        byte[][] source = whole(doc);
        byte[] wash = planes(doc, 0, 20, 20, 20, 20), ink = planes(doc, 1, 20, 20, 20, 20);
        FloatingSelection carried = LassoPlanes.lift(doc, box(10, 10, 50, 50), 4).copy();
        ToneDocument next = InkWashLayers.blank(100, 80);
        check(carried.place(next) && Arrays.equals(planes(next, 0, 20, 20, 20, 20), wash) && Arrays.equals(planes(next, 1, 20, 20, 20, 20), ink),
                "A piece carried to another page goes down there whole");
        check(Arrays.deepEquals(source, whole(doc)), "The page it came from is as it was");
        check(next.undo() && bare(next, 0, 0, 100, 80), "Its page's Undo takes it off");
        // A one-layer page (MonoPaint's): the wash and the ink on the one layer, the ink over the wash.
        ToneDocument one = new ToneDocument(100, 80);
        carried.place(one);
        check(one.layerCount() == 1 && one.compositeTone(30, 30) == doc.compositeTone(30, 30) && one.compositeTone(21, 30) == doc.compositeTone(21, 30),
                "On a one-layer page it looks the same");
    }
    private static void shown() {
        ToneDocument doc = page();
        FloatingSelection piece = moved(doc, 50, 30);
        int[] out = new int[100 * 80];
        piece.planes.shown(doc, piece, 0, 0, 100, 80, out);
        ToneDocument placed = page();
        moved(placed, 50, 30).place(placed);
        boolean same = true;
        for (int y = 0; y < 80; y++) for (int x = 0; x < 100; x++) {
            int o = out[y * 100 + x];
            if (o >= 0 && o != placed.compositeTone(x, y)) same = false;
        }
        check(same && out[30 * 100 + 30] == 255 && out[60 * 100 + 80] >= 0 && out[5 * 100 + 5] == -1,
                "Floating, the screen shows the page as it will be: the hole bare, the piece where it lies, the rest left as it is");
    }
    private static void filed() throws Exception {
        ToneDocument doc = page();
        moved(doc, 50, 30).place(doc);
        java.io.ByteArrayOutputStream file = new java.io.ByteArrayOutputStream();
        DocumentCodec.write(file, doc.layerSnapshot());
        ToneDocument read = DocumentCodec.read(new java.io.ByteArrayInputStream(file.toByteArray()));
        check(Arrays.deepEquals(whole(doc), whole(read)), "A page with a moved piece saves and reads back the same, every plane");
    }
    /**
     * Cut and Paste, and Duplicate (DrawingPad.cutSelection, pasteSelection, duplicateSelection): what is kept is a copy lifted from
     * nowhere; Cut takes the piece off as Delete does, one Undo step; each Paste floats a fresh copy where it was kept,
     * on any page of its size, and goes down cutting nothing.
     */
    private static void pasted() {
        ToneDocument doc = page();
        byte[][] before = whole(doc);
        byte[] ink = planes(doc, 1, 20, 20, 20, 20), wash = planes(doc, 0, 20, 20, 20, 20);
        FloatingSelection piece = LassoPlanes.lift(doc, box(10, 10, 50, 50), 4), kept = piece.copy();
        check(Arrays.deepEquals(before, whole(doc)), "Copy leaves the page as it was");
        check(piece.planes.place(doc, piece, false, true) && bare(doc, 0, 0, 100, 80), "Cut takes it off the page");
        check(doc.undo() && Arrays.deepEquals(before, whole(doc)) && doc.redo(), "One Undo puts a cut back");
        FloatingSelection first = kept.copy();
        first.grab(FloatingSelection.MOVE); first.drag(FloatingSelection.MOVE, 0, 0, 50, 30); first.release();
        check(first.place(doc) && Arrays.equals(planes(doc, 1, 70, 50, 20, 20), ink), "Pasted and moved, it goes down where it was dragged");
        FloatingSelection second = kept.copy();
        check(!second.lifted && second.samePose(kept) && !second.samePose(first), "Each Paste floats a fresh copy where it was kept");
        check(second.place(doc) && Arrays.equals(planes(doc, 1, 20, 20, 20, 20), ink) && Arrays.equals(planes(doc, 0, 20, 20, 20, 20), wash),
                "Pasted and put down unmoved, it lands where it was cut, every plane");
        check(doc.undo() && Arrays.equals(planes(doc, 1, 70, 50, 20, 20), ink) && alphaAt(doc, 1, 30, 30) == 0, "A paste's Undo takes that paste alone");
        ToneDocument next = InkWashLayers.blank(100, 80);
        check(kept.fits(next) && kept.copy().place(next) && Arrays.equals(planes(next, 1, 20, 20, 20, 20), ink), "Pasted on another page of its size");
        check(!kept.fits(InkWashLayers.blank(80, 100)), "A page of another size takes none");
    }
    /** The Lasso's Delete way (DrawingPad.liftLasso with lassoDeletes): the loop's piece, unmoved, deleted at once, permanent ink too, one Undo step. */
    private static void fastDelete() {
        ToneDocument doc = page();
        byte[][] before = whole(doc);
        FloatingSelection rung = LassoPlanes.lift(doc, new float[]{10, 10, 31, 10, 31, 31, 10, 31}, 4);
        check(rung.unmoved() && rung.planes.place(doc, rung, false, true), "The loop's piece goes as the loop closes");
        check(alphaAt(doc, 1, 27, 27) == 0 && alphaAt(doc, 0, 21, 21) == 0, "The permanent Pen ink and the wash inside it go, as the Eraser's Everything takes them");
        check(alphaAt(doc, 1, 33, 33) == 255 && alphaAt(doc, 0, 38, 38) != 0, "What lies outside the loop stays");
        check(doc.undo() && Arrays.deepEquals(before, whole(doc)), "One Undo puts it back");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
