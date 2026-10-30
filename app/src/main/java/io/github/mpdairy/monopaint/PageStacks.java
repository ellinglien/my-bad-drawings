package io.github.mpdairy.monopaint;

/**
 * Layers as a stack of clear pages (docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md, "Stage 3c: layers are
 * stacked pages"): a page marked "stacked on the page below" ({@link TracingPaper#LINKED}) is a layer of the same stack
 * as the page before it, so a stack is a run of pages, its first page the bottom layer, at most {@link #MOST} layers.
 * Everywhere but the side column a stack is one page: the strip, the numbers, the notebook screen, page turns and the
 * copies. Immutable, made from the links; pure, checked on the desktop ({@code PageStacksChecks}).
 */
final class PageStacks {
    /** The most layers a stack holds: the old in-page layers' four. */
    static final int MOST = InkWashLayers.MAX_PAIRS;

    /** Each stack's first page, then the page count; each page's stack. */
    private final int[] first, of;

    /** The stacks of {@code linked.length} pages, page {@code i} stacked on page {@code i − 1} when {@code linked[i]} (page 0 never is). */
    PageStacks(boolean[] linked) {
        int n = linked.length, stacks = 0;
        of = new int[n];
        int[] starts = new int[n + 1];
        for (int i = 0; i < n; i++) {
            if (i == 0 || !linked[i]) starts[stacks++] = i;
            of[i] = stacks - 1;
        }
        starts[stacks] = n;
        first = java.util.Arrays.copyOf(starts, stacks + 1);
    }

    /** How many stacks: the page count a notebook says. */
    int count() { return first.length - 1; }
    /** The pages. */
    int pages() { return of.length; }
    /** Page {@code page}'s stack; a cover's place stays itself ({@link DrawingBook#FRONT}, {@link DrawingBook#BACK}). */
    int of(int page) { return DrawingBook.isCover(page) ? page : of[page]; }
    /** Stack {@code stack}'s bottom page. */
    int first(int stack) { return first[stack]; }
    /** Stack {@code stack}'s top page. */
    int last(int stack) { return first[stack + 1] - 1; }
    /** How many layers stack {@code stack} holds. */
    int size(int stack) { return first[stack + 1] - first[stack]; }
    /** The bottom page of page {@code page}'s stack. */
    int bottom(int page) { return first(of[page]); }
    /** Whether page {@code page} has a page stacked on it, or is stacked on one. */
    boolean stacked(int page) { return size(of[page]) > 1; }
    /** What comes after stack place {@code place}: the first after the front cover, the back cover after the last; {@link DrawingBook#NONE} after the back. */
    int after(int place) { if (place == DrawingBook.FRONT) return 0; if (place == DrawingBook.BACK) return DrawingBook.NONE; return place + 1 < count() ? place + 1 : DrawingBook.BACK; }
    /** What comes before stack place {@code place}: the front cover before the first, the last before the back cover; {@link DrawingBook#NONE} before the front. */
    int before(int place) { if (place == DrawingBook.BACK) return count() - 1; if (place == DrawingBook.FRONT) return DrawingBook.NONE; return place > 0 ? place - 1 : DrawingBook.FRONT; }
    /** The page a stack place stands for: a stack's top layer (where a turn lands when no layer of it was drawn on this session, {@code DrawingBook#pageOf}), a cover itself. */
    int page(int place) { return DrawingBook.isCover(place) ? place : last(place); }
    /**
     * A stack's layers as one page, bottom first ({@code layers}; {@code hidden}: each one's Hidden step, left out): the
     * bottom page (its paper, guides and magnets) with every shown layer's own layers over its own, as a copy and the
     * screens show the stack. The bottom's snapshot, its layers added to; read it, not the others, after.
     */
    static ToneDocument.Snapshot flatten(java.util.List<ToneDocument.Snapshot> layers, boolean[] hidden) {
        ToneDocument.Snapshot bottom = layers.get(0);
        if (hidden != null && hidden[0]) for (ToneDocument.Layer l : bottom.layers) l.visible = false;
        for (int k = 1; k < layers.size(); k++) if (hidden == null || !hidden[k]) bottom.layers.addAll(layers.get(k).layers);
        return bottom;
    }
    /** {@code links} (each page's stacked-on-below mark) as a run whose stacks hold no more than {@link #MOST}, page 0 never linked. */
    static boolean valid(boolean[] links) {
        int run = 1;
        for (int i = 0; i < links.length; i++) {
            if (i == 0 && links[i]) return false;
            run = i > 0 && links[i] ? run + 1 : 1;
            if (run > MOST) return false;
        }
        return true;
    }
}
