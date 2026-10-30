package io.github.mpdairy.monopaint;

import android.graphics.Bitmap;
import android.graphics.Rect;
import java.util.ArrayList;

/**
 * Something drawn over the presented raster for a moment, never into the page: the gradient
 * guide, the eraser's outline. It covers a few small tiles, so only they reach the panel; the
 * pixels under them are saved before it is drawn and put back to remove it. Tiles may overlap,
 * so they are put back in reverse.
 */
final class RasterOverlay {
    interface Queue { void area(Rect area); }
    private final ArrayList<Rect> tiles = new ArrayList<>();
    private int[] saved = new int[0];

    boolean isEmpty() { return tiles.isEmpty(); }
    /** Adds {@code tile}, clipped to {@code target}; nothing if it lies outside. Add every tile, then {@link #save}, then draw. */
    void add(Rect tile, Bitmap target) { if (tile.intersect(0, 0, target.getWidth(), target.getHeight())) tiles.add(tile); }
    /** Saves the pixels under every tile, before anything is drawn over them. */
    void save(Bitmap target) {
        int size = 0;
        for (Rect tile : tiles) size += tile.width() * tile.height();
        if (saved.length < size) saved = new int[size];
        int offset = 0;
        for (Rect tile : tiles) {
            target.getPixels(saved, offset, tile.width(), tile.left, tile.top, tile.width(), tile.height());
            offset += tile.width() * tile.height();
        }
    }
    /** Queues every tile for presentation. */
    void queue(Queue queue) { for (Rect tile : tiles) queue.area(tile); }
    /** Puts back the pixels under every tile, queueing each, and forgets them. */
    void restore(Bitmap target, Queue queue) {
        int offset = 0;
        for (Rect tile : tiles) offset += tile.width() * tile.height();
        for (int i = tiles.size() - 1; i >= 0; i--) {
            Rect tile = tiles.get(i);
            offset -= tile.width() * tile.height();
            target.setPixels(saved, offset, tile.width(), tile.left, tile.top, tile.width(), tile.height());
            queue.area(tile);
        }
        tiles.clear();
    }
    /** Forgets the tiles without putting anything back: the raster under them was redrawn whole. */
    void forget() { tiles.clear(); }
}
