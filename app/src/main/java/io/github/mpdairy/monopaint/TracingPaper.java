package io.github.mpdairy.monopaint;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Tracing paper (docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md, "Data model and file format"): what a
 * notebook shows through the page drawn on. Its {@link #paper} (Plain, Tracing, Glass), the page pinned as the ghost
 * ({@link #ghost}, −1: none), the step a page never set shows at ({@link #strength}: Half or Faint) and each page's step (the page's in the low byte, 0 for never set, then 2 bits a layer pair:
 * 0 Solid, 1 Half, 2 Faint; a Hidden layer is the page's own visible flag), and whether it is stacked on the page below
 * ({@link #LINKED}: a layer of the same stack, {@link PageStacks}; the spec's "Stage 3c"). A page's step is its step as a
 * layer: within its stack a layer never set shows Solid; another stack shows through at the Paper's strength times it.
 * Steps are a view only: never in a page's bytes, its copies, Fill, the eyedropper or a brush (a Hidden layer is left out
 * of its stack's copy). Immutable, as {@code DrawingBook} hands it to a file ({@code BookOptions}: {@code PAPM}, {@code GHST},
 * {@code STEP}, {@code PSTR}, {@code STAK}, each only when it isn't the default) and the recovery. Pure, checked on the
 * desktop ({@code TracingChecks}, {@code PageStacksChecks}).
 */
final class TracingPaper {
    static final int PLAIN = 0, TRACING = 1, GLASS = 2;
    /** Glass shows at most this many pages: each costs a page decode on every turn (100 pages took seconds). */
    static final int GLASS_PAGES = 4;
    /** The setting's words, and the Papers' (docs/TEXT-STYLE.md; the owner, 2026-10-21: layers are pages). */
    static final String TRANSPARENCY = "See-Through Pages";
    static final String[] PAPERS = {"Off", "Page Before", "Nearby Pages"};
    /** A page step: never set (the Paper's default), Solid, Half, Faint, Hidden. */
    static final int UNSET = 0, SOLID = 1, HALF = 2, FAINT = 3, HIDDEN = 4;
    static final String[] STEPS = {"", "Solid", "Half", "Faint", "Hidden"};
    /** Each step's strength, %, the opacity a ghost is laid at (the owner, 2026-10-20: Half 44, Faint 18). */
    static final int[] STRENGTH = {0, 100, 44, 18, 0};
    /** A page's bits: its step (the low byte), its old in-page layers' steps (the next), and stacked on the page below. */
    static final int STEP_BITS = 255, LAYER_BITS = 255 << 8, LINKED = 1 << 16;

    static final TracingPaper NONE = new TracingPaper(PLAIN, -1, new int[0]);

    /** The Paper, the pinned ghost, and the notebook's default strength: the step of a page never set ({@link #HALF} or {@link #FAINT}). */
    final int paper, ghost, strength;
    private final int[] steps;

    TracingPaper(int paper, int ghost, int[] steps) { this(paper, ghost, steps, HALF); }
    TracingPaper(int paper, int ghost, int[] steps, int strength) {
        this.paper = paper; this.ghost = ghost < 0 || ghost >= steps.length ? -1 : ghost; this.steps = steps.clone();
        this.strength = strength == FAINT ? FAINT : HALF;
        boolean[] links = new boolean[steps.length];
        for (int i = 0; i < links.length; i++) links[i] = (steps[i] & LINKED) != 0;
        stacks = new PageStacks(links);
    }
    /** The stacks the links make. */
    private final PageStacks stacks;
    PageStacks stacks() { return stacks; }
    /** Whether page {@code i} is stacked on the page below. */
    boolean linked(int i) { return (steps[i] & LINKED) != 0; }
    /** Page {@code i}'s step as a layer of its stack: its own, Solid when never set. */
    int layerShown(int i) { int s = step(i); return s == UNSET ? SOLID : s; }
    /** All defaults for {@code count} pages: Plain, no ghost, no step set. */
    static TracingPaper plain(int count) { return new TracingPaper(PLAIN, -1, new int[count]); }
    /** All defaults but the stacks: Plain, no ghost, no step set, each page still stacked as it is. */
    TracingPaper plainView() {
        int[] s = new int[steps.length];
        for (int i = 0; i < s.length; i++) s[i] = steps[i] & LINKED;
        return new TracingPaper(PLAIN, -1, s, HALF);
    }

    int count() { return steps.length; }
    /** Page {@code i}'s step and layer bits, as the file keeps them. */
    int stepBits(int i) { return steps[i]; }
    /** Page {@code i}'s page step ({@link #UNSET} to {@link #HIDDEN}). */
    int step(int i) { return steps[i] & 255; }
    /** Page {@code i}'s layer steps, 2 bits a pair. */
    int layerBits(int i) { return i < 0 || i >= steps.length ? 0 : (steps[i] & LAYER_BITS) >>> 8; }
    /** Pair {@code pair}'s step on page {@code i}: {@link #SOLID}, {@link #HALF} or {@link #FAINT} (Hidden is the layer's own flag). */
    int layerStep(int i, int pair) { return SOLID + (layerBits(i) >> 2 * pair & 3); }
    /** Pair {@code pair}'s strength, %, in layer bits {@code bits}. */
    static int layerStrength(int bits, int pair) { return STRENGTH[SOLID + (bits >> 2 * pair & 3)]; }
    /** Whether the file keeps nothing for it. */
    boolean isDefault() { return paper == PLAIN && ghost < 0 && strength == HALF && !stepped() && !stacked(); }
    /** Whether any page or old in-page layer has a step set. */
    boolean stepped() { for (int s : steps) if ((s & (STEP_BITS | LAYER_BITS)) != 0) return true; return false; }
    /** Whether any page is stacked on another. */
    boolean stacked() { for (int s : steps) if ((s & LINKED) != 0) return true; return false; }

    /**
     * The most pages a turn decodes for what shows through (the owner, 2026-10-21): the stack's own layers first, then
     * the neighbouring stacks, nearest first, as long as each whole stack fits.
     */
    static final int MOST_DECODES = 6;
    /**
     * The pages that show through page {@code index} (a cover: none), bottom first, each {page, its strength as a layer
     * %, its stack's strength %, its stack} (the stack −1 for the stack drawn on: each layer on its own): the other layers
     * of its stack, each at its step (Solid if never set; Hidden left out), and the other stacks the Paper shows, each
     * flattened (its layers at their steps) then shown at the Paper's strength. Tracing: the stack before, or the pinned
     * ghost's stack in its place, at the notebook's strength, and the stack after at Faint. Glass: the nearest
     * {@link #GLASS_PAGES} other stacks with a layer shown, at the notebook's strength. Plain: no other stack. At most
     * {@link #MOST_DECODES} pages, the stack drawn on's first. The layer drawn on itself never ghosts: it shows Solid.
     */
    int[][] ghostLayers(int index) {
        if (index < 0 || index >= steps.length) return new int[0][];
        java.util.List<int[]> out = new java.util.ArrayList<>();
        int here = stacks.of(index), count = stacks.count();
        stack(out, here, SOLID, index, -1);
        if (paper == GLASS) {
            // The nearest GLASS_PAGES stacks with a layer shown (the stack before first on a tie), while they fit.
            int n = 0;
            for (int d = 1; n < GLASS_PAGES && (here - d >= 0 || here + d < count); d++)
                for (int s : new int[]{here - d, here + d})
                    if (s >= 0 && s < count && n < GLASS_PAGES && shows(s) && fits(out, s)) { stack(out, s, strength, -1, s); n++; }
        } else if (paper == TRACING) {
            int before = ghost >= 0 && stacks.of(ghost) != here ? stacks.of(ghost) : here - 1, after = here + 1;
            if (before >= 0 && fits(out, before)) stack(out, before, strength, -1, before);
            if (after < count && after != before && fits(out, after)) stack(out, after, FAINT, -1, after);
        }
        out.sort((a, b) -> a[0] - b[0]);
        return out.toArray(new int[0][]);
    }
    /** Whether stack {@code s} fits the pages to decode. */
    private boolean fits(java.util.List<int[]> out, int s) { return out.size() + stacks.size(s) <= MOST_DECODES; }
    /** Stack {@code s}'s layers but {@code except}, each at its own step, the stack at {@code at}'s strength, Hidden ones left out; {@code group} as {@link #ghosts} gives it. */
    private void stack(java.util.List<int[]> out, int s, int at, int except, int group) {
        for (int p = stacks.first(s); p <= stacks.last(s); p++) {
            int layer = STRENGTH[layerShown(p)];
            if (p != except && layer > 0 && STRENGTH[at] > 0) out.add(new int[]{p, layer, STRENGTH[at], group});
        }
    }
    /** {@link #ghostLayers}, each {page, strength %}: its own strength times its stack's (what a page alone shows at). */
    int[][] ghosts(int index) { return strengths(ghostLayers(index)); }
    private static int[][] strengths(int[][] ghosts) {
        int[][] out = new int[ghosts.length][];
        for (int i = 0; i < ghosts.length; i++) out[i] = new int[]{ghosts[i][0], ghosts[i][1] * ghosts[i][2] / 100};
        return out;
    }
    /** Whether stack {@code s} has a layer that isn't Hidden. */
    private boolean shows(int s) { for (int p = stacks.first(s); p <= stacks.last(s); p++) if (step(p) != HIDDEN) return true; return false; }

    TracingPaper withPaper(int paper) { return new TracingPaper(paper < PLAIN || paper > GLASS ? PLAIN : paper, ghost, steps, strength); }
    TracingPaper withGhost(int ghost) { return new TracingPaper(paper, ghost, steps, strength); }
    /** The default strength ({@link #HALF} or {@link #FAINT}). */
    TracingPaper withStrength(int strength) { return new TracingPaper(paper, ghost, steps, strength); }
    TracingPaper withStep(int page, int step) {
        int[] s = steps.clone();
        s[page] = s[page] & ~255 | Math.max(UNSET, Math.min(HIDDEN, step));
        return new TracingPaper(paper, ghost, s, strength);
    }
    /** Pair {@code pair} of page {@code page} at {@code step} ({@link #SOLID}, {@link #HALF}, {@link #FAINT}). */
    TracingPaper withLayerStep(int page, int pair, int step) {
        int[] s = steps.clone();
        int bits = Math.max(0, Math.min(2, step - SOLID));
        s[page] = s[page] & ~(3 << 8 + 2 * pair) | bits << 8 + 2 * pair;
        return new TracingPaper(paper, ghost, s, strength);
    }
    /** Page {@code page}'s pairs rearranged: new pair k takes old pair {@code from[k]}'s step (−1, a new pair: Solid). */
    TracingPaper withLayers(int page, int[] from) {
        int old = layerBits(page), bits = 0;
        for (int k = 0; k < from.length && k < 4; k++) if (from[k] >= 0) bits |= (old >> 2 * from[k] & 3) << 2 * k;
        int[] s = steps.clone();
        s[page] = s[page] & ~LAYER_BITS | bits << 8;
        return new TracingPaper(paper, ghost, s, strength);
    }

    // As the file keeps them (BookOptions): a record each, only when it isn't the default.

    byte[] paperBytes() { return new byte[]{(byte) paper}; }
    byte[] strengthBytes() { return new byte[]{(byte) strength}; }
    byte[] ghostBytes() { return ByteBuffer.allocate(4).putInt(ghost).array(); }
    /** {@code tracing} with the stacks {@code STAK}'s bytes say ({@link #read}; null: none), for a recovery that keeps them apart. */
    static TracingPaper stacked(TracingPaper tracing, byte[] stacks) {
        TracingPaper links = read(null, null, null, null, stacks, tracing.count());
        int[] s = tracing.steps.clone();
        for (int i = 0; i < s.length; i++) s[i] = s[i] & ~LINKED | links.steps[i] & LINKED;
        return new TracingPaper(tracing.paper, tracing.ghost, s, tracing.strength);
    }
    /** The page count, then each page's stacked-on-below mark, a byte each (1: stacked). */
    byte[] stackBytes() {
        ByteBuffer b = ByteBuffer.allocate(4 + steps.length).putInt(steps.length);
        for (int s : steps) b.put((byte) ((s & LINKED) != 0 ? 1 : 0));
        return b.array();
    }
    /** The page count, then each page's step and its layers' steps, a byte each. */
    byte[] stepBytes() {
        ByteBuffer b = ByteBuffer.allocate(4 + 2 * steps.length).putInt(steps.length);
        for (int s : steps) b.put((byte) s).put((byte) (s >> 8));
        return b.array();
    }
    /**
     * The records' bytes read for a notebook of {@code count} pages (null: no record): a Paper outside Tracing and Glass,
     * a ghost outside the pages, or steps not for {@code count} pages or with a byte out of range are each left out.
     */
    static TracingPaper read(byte[] paper, byte[] ghost, byte[] steps, int count) { return read(paper, ghost, steps, null, count); }
    /** As {@link #read(byte[], byte[], byte[], int)}, with {@code PSTR}'s bytes (null or anything but Faint: Half). */
    static TracingPaper read(byte[] paper, byte[] ghost, byte[] steps, byte[] strength, int count) { return read(paper, ghost, steps, strength, null, count); }
    /**
     * As above, with {@code STAK}'s bytes (null: no page stacked): a record for another page count, a byte other than 0 or
     * 1, the first page stacked or a stack of more than {@link PageStacks#MOST} is left out, every page its own.
     */
    static TracingPaper read(byte[] paper, byte[] ghost, byte[] steps, byte[] strength, byte[] stacks, int count) {
        int p = paper != null && paper.length == 1 && (paper[0] == TRACING || paper[0] == GLASS) ? paper[0] : PLAIN;
        int g = ghost != null && ghost.length == 4 ? ByteBuffer.wrap(ghost).getInt() : -1;
        int[] s = new int[count];
        if (steps != null && steps.length == 4 + 2 * count && ByteBuffer.wrap(steps).getInt() == count) {
            int[] read = new int[count];
            boolean ok = true;
            for (int i = 0; i < count && ok; i++) {
                int page = steps[4 + 2 * i] & 255, layers = steps[5 + 2 * i] & 255;
                for (int k = 0; k < 4; k++) if ((layers >> 2 * k & 3) == 3) ok = false;
                if (page > HIDDEN) ok = false;
                read[i] = page | layers << 8;
            }
            if (ok) s = read;
        }
        if (stacks != null && stacks.length == 4 + count && ByteBuffer.wrap(stacks).getInt() == count) {
            boolean[] links = new boolean[count];
            boolean ok = true;
            for (int i = 0; i < count; i++) { int b = stacks[4 + i]; if (b != 0 && b != 1) ok = false; links[i] = b == 1; }
            if (ok && PageStacks.valid(links)) for (int i = 0; i < count; i++) if (links[i]) s[i] |= LINKED;
        }
        return new TracingPaper(p, g, s, strength != null && strength.length == 1 && strength[0] == FAINT ? FAINT : HALF);
    }

    @Override public boolean equals(Object o) {
        return o instanceof TracingPaper && ((TracingPaper) o).paper == paper && ((TracingPaper) o).ghost == ghost && ((TracingPaper) o).strength == strength && Arrays.equals(((TracingPaper) o).steps, steps);
    }
    @Override public int hashCode() { return paper * 31 + ghost * 7 + strength + Arrays.hashCode(steps); }
}
