package io.github.mpdairy.monopaint;

import android.graphics.Bitmap;
import android.graphics.Rect;

/**
 * Shows a {@link FloatingSelection} over the page, display only, like {@link ShapePreview}:
 * each frame redraws where the piece was and where it is now. The page stays untouched.
 */
final class SelectionPreview {
    static { System.loadLibrary("monopaint_display"); }
    private static native void nativeApply(Bitmap display, byte[][] tones, byte[][] alpha, int[] visible, int target,
            boolean newLayer, byte[] pieceTones, byte[] pieceAlpha, int pieceWidth, int pieceHeight,
            boolean lifted, int cutX, int cutY, float[] inverse, int[] dots, boolean logical,
            int left, int top, int right, int bottom);
    final FloatingSelection piece;
    private final byte[][] tones, alpha;
    private final int[] visible;
    private final int target;
    /** Page pixels the piece covered in the last frame. */
    private final Rect shown = new Rect();

    SelectionPreview(ToneDocument document, FloatingSelection piece) {
        this.piece = piece;
        ToneDocument.Snapshot source = document.layerSnapshot(); target = source.active;
        tones = new byte[source.layers.size()][]; alpha = new byte[tones.length][]; visible = new int[tones.length];
        for (int i = 0; i < tones.length; i++) {
            ToneDocument.Layer layer = source.layers.get(i);
            tones[i] = layer.tones; alpha[i] = layer.alpha; visible[i] = layer.visible ? layer.opacity : 0;
        }
    }
    private static Rect rect(int[] bounds) { return bounds == null ? new Rect() : new Rect(bounds[0], bounds[1], bounds[2], bounds[3]); }
    /** Everything the piece affects on screen: where it lies now and the hole it was lifted from. */
    Rect area() {
        Rect area = rect(piece.bounds()); area.union(rect(piece.cutBounds())); area.union(shown);
        return area;
    }
    /** Draws the piece as it lies now; returns the page pixels that changed. */
    Rect show(Bitmap display, boolean logical) {
        Rect now = rect(piece.bounds()), dirty = new Rect(now);
        dirty.union(shown);
        // The first frame, or one after the page was redrawn, also opens the hole.
        if (shown.isEmpty()) dirty.union(rect(piece.cutBounds()));
        return draw(dirty, now, display, logical);
    }
    /** Draws the whole piece and its hole again, after the page beneath was redrawn. */
    Rect showAll(Bitmap display, boolean logical) { return draw(area(), rect(piece.bounds()), display, logical); }
    private Rect draw(Rect dirty, Rect now, Bitmap display, boolean logical) {
        if (!dirty.intersect(0, 0, display.getWidth(), display.getHeight())) return new Rect();
        nativeApply(display, tones, alpha, visible, target, piece.newLayer, piece.tones, piece.alpha, piece.width, piece.height,
                piece.lifted, piece.cutX, piece.cutY, piece.inverse(), ViewportBitmap.DOTS, logical,
                dirty.left, dirty.top, dirty.right, dirty.bottom);
        shown.set(now);
        return dirty;
    }
}
