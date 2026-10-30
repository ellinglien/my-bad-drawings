package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * The side column's layer changes that take a page away (docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md,
 * "Stage 3c: layers are stacked pages"): Delete and Merge Down, each one Undo step ({@link Undo}). A layer is a page
 * stacked on the one below ({@link PageStacks}); deleting it takes the page out, merging it down flattens it into the
 * layer under it ({@link InkWashLayers#merge(ToneDocument, ToneDocument.Snapshot)}) and takes it out. Undo puts the page
 * back as it was, in its place, drawn on, while nothing else changed since (no stroke left, turn or other book edit). Pure,
 * checked on the desktop ({@code PageStacksChecks}).
 */
final class LayerEdits {
    private LayerEdits() {}

    /** A layer change taken back in one step, while the page it left drawn on is as it left it. */
    static final class Undo {
        /** The page to put back. */
        private final DrawingBook.Taken taken;
        private final boolean merged;
        private final ToneDocument after;
        /** The page's last Undo step then ({@link ToneDocument#top}), and the book's edits. */
        private final long top, changes;
        private Undo(DrawingBook book, DrawingBook.Taken taken, boolean merged) {
            this.taken = taken; this.merged = merged;
            after = book.current(); top = after.top(); changes = book.changes();
        }
        /** Whether it can still be taken back: the same page drawn on, its last Undo step the same (a stroke since, undone, is), and no other book edit since. */
        boolean ready(DrawingBook book) { return book != null && book.current() == after && after.top() == top && book.changes() == changes; }
        /** Takes it back: the merge undone on the layer below, the page back in its place, drawn on. */
        void apply(DrawingBook book) throws IOException {
            if (merged) after.undo();
            book.restore(taken);
            book.select(taken.at);
        }
    }

    /** Delete: layer {@code page} out of its stack (never a page alone: that is the notebook's Delete Page); null when it can't. */
    static Undo delete(DrawingBook book, int page) throws IOException {
        if (DrawingBook.isCover(page) || page < 0 || page >= book.count() || !book.stacks().stacked(page)) return null;
        DrawingBook.Taken taken = book.take(page);
        if (page == book.index()) book.deletePage(); else book.deletePage(page);
        return new Undo(book, taken, false);
    }

    /**
     * Merge Down: layer {@code page} flattened into the layer under it, which is drawn on; null when it has none, or when
     * either is locked (a PDF's page, or a timed page run out under Lock Pages: neither changes).
     */
    static Undo mergeDown(DrawingBook book, int page) throws IOException {
        if (!book.linked(page)) return null;
        ToneDocument decoded = book.cachedPage(page);
        ToneDocument.Snapshot above = decoded != null ? decoded.layerSnapshot() : DocumentCodec.read(new ByteArrayInputStream(book.packed(page).bytes())).layerSnapshot();
        if (above.clock.locks(book.options().lockPages)) { above.release(); return null; }
        DrawingBook.Taken taken = book.take(page);
        int was = book.index();
        book.select(page - 1);
        if (book.current().locked()) { above.release(); book.select(was); return null; }
        InkWashLayers.merge(book.current(), above);
        book.deletePage(page);
        return new Undo(book, taken, true);
    }
}
