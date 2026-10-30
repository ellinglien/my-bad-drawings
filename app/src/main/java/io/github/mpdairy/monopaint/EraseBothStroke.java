package io.github.mpdairy.monopaint;

/**
 * The eraser, the pen's eraser end and a finger: whatever is under it goes, ink and wash
 * together, permanent paint too (the owner, 2026-10-22: "Pen isn't erased by eraser (everything)"; Erasable,
 * {@link ToneDocument.Layer#share}, no longer holds back the eraser on Everything, nor on a chosen kind). A gesture binds one layer, so ink
 * is erased as the eraser moves and the wash beneath when it lifts, finished as one undo step.
 * Meanwhile the screen can show the page as it will be wherever it went ({@link #erasedTones}),
 * which is what the page shows once it lifts. Masking fluid comes off first (the new modules spec, Resist): where the
 * eraser passes over fluid it can lift, that pass takes the fluid at lift and leaves the paint under it; the next pass
 * erases as usual. Wax (the fluid's {@link ToneDocument.Layer#share}: a crayon's, a wax resist's) shields no paint from
 * the eraser, and goes with it on Everything (the owner, 2026-10-25: "Base erase should erase everything").
 * Tone's dots ({@link PrintScreen#DOT_COVERAGE}) scrape off in streaks along the stroke (the mischief modules spec,
 * mischief-lab.html's scrape): each pass takes some, more on some streaks than others. On a notebook cover, erasing
 * everything also rubs the cover's design off where it went, down to white paper; its tape, label and picture stay
 * ({@link ToneDocument#rubDesign}, {@link CoverWear}). An eraser limited to kinds leaves the design: it is no kind of mark.
 */
final class EraseBothStroke implements DrawingStroke {
    private final ToneDocument document;
    /** The pen's broadest, px, its size following pressure and speed; NaN for a finger, sized by its contact and speed. */
    private final float most;
    private final float diameter;
    private final WashCoverage coverage;
    /** The pixels it went over where masking fluid could lift, lifted at lift instead of erased; null on a page without any. */
    private final WashCoverage lifts;
    /** The kinds of mark it takes ({@link ToneDocument#eraseKinds}); 0: Everything. */
    private final int kinds;
    /**
     * What it takes of the paint, whole ({@link ToneDocument#eraseKinds}): {@link #kinds}, and every kind for Everything, so
     * permanent ink goes too (the owner, 2026-10-22). Masking fluid's wax is no kind: lifting fluid takes with 0, the Erasable rule.
     */
    private final int takes;
    private final RoundFootprint footprint = new RoundFootprint();
    /** Slow is small and accurate, fast is big. */
    private final PenSpeed speed = new PenSpeed();
    /** Where the last sample erased, page pixels, and how wide; NaN before the first. */
    private float lastX = Float.NaN, lastY = Float.NaN, lastSize = Float.NaN;
    /** The layer selected before, and the selected pair's halves: ink erased as it moves, wash at lift. */
    private final int restore, inkLayer, washLayer;
    /**
     * Inkwash: what it erases as it moves, where Tone's dots are: the ink layer's Paint plane ({@link ToneDocument#PLANE_WASH})
     * when it takes paint and the layer has one (textures are Paint since 2026-10-29), else its Pen plane ({@link
     * ToneDocument#PLANE_PEN}, older dots) when it takes pen and the layer has one, else (-1) its base. Every other plane
     * it takes, and the base, go at lift.
     */
    private final int live;
    private final boolean previews;
    /** Level 1 for a whole row, and which pixels of a span are new to the stroke. */
    private final int[] ones;
    private final byte[] fresh;
    private final RoundFootprint.Span erase = this::erase;
    /** Tone's scrape: the stroke's heading, and a salt of its own (the page's revision and where it began), so each pass scrapes afresh. */
    private double headX = 1, headY = 0;
    private int scrape;
    /** Bounds of the pixels erased since {@link #takeErased}, right and bottom exclusive; empty when left >= right. */
    private int newLeft = Integer.MAX_VALUE, newTop = Integer.MAX_VALUE, newRight, newBottom;

    /** The Eraser tool or the pen's eraser end, sized by {@code preset} and pressure. */
    EraseBothStroke(ToneDocument document, InkWashSizes.Preset preset) { this(document, InkWashSizes.maxSize(preset), 0, 0); }
    /** As above, taking only marks of {@code kinds} ({@link ToneDocument#eraseKinds}; 0: everything), whole, permanent paint too. */
    EraseBothStroke(ToneDocument document, InkWashSizes.Preset preset, int kinds) { this(document, InkWashSizes.maxSize(preset), 0, kinds); }
    /** The Eraser tool or the pen's eraser end at the current size ({@link ToolSwatches#useSize}): {@code most} px at its broadest. */
    static EraseBothStroke pen(ToneDocument document, float most, int kinds) { return new EraseBothStroke(document, most, 0, kinds); }
    /** A finger: {@code diameter} page pixels when fast, narrowing when slow (see {@link PenSpeed#fingerDiameter}), whatever its pressure. */
    EraseBothStroke(ToneDocument document, float diameter) { this(document, Float.NaN, diameter, 0); }
    EraseBothStroke(ToneDocument document, float diameter, int kinds) { this(document, Float.NaN, diameter, kinds); }
    private EraseBothStroke(ToneDocument document, float most, float diameter, int kinds) {
        this.document = document; this.most = most; this.diameter = diameter; this.kinds = kinds;
        takes = kinds == 0 ? ToneDocument.KIND_ALL : kinds;
        restore = document.activeLayer();
        coverage = new WashCoverage(document.width, document.height);
        boolean masked = document.hasMask();
        // Masking fluid is no kind of mark: an eraser limited to one leaves it (it still shields what is under it).
        lifts = masked && kinds == 0 ? new WashCoverage(document.width, document.height) : null;
        ones = new int[document.width]; java.util.Arrays.fill(ones, 1); fresh = new byte[document.width];
        int pair = InkWashLayers.current(document);
        inkLayer = InkWashLayers.inkOf(document, pair); washLayer = InkWashLayers.washOf(document, pair);
        // The other pairs show through where the selected one is erased, unless masking fluid shields paint (a page
        // whose fluid has all been lifted previews again).
        previews = !masked;
        document.selectLayer(inkLayer);
        live = (takes & ToneDocument.KIND_PAINT) != 0 && document.hasPlane(inkLayer, ToneDocument.PLANE_WASH) ? ToneDocument.PLANE_WASH
                : (takes & ToneDocument.KIND_PEN) != 0 && document.hasPlane(inkLayer, ToneDocument.PLANE_PEN) ? ToneDocument.PLANE_PEN : -1;
        if (live < 0) document.begin(); else document.beginPlane(live, false);
    }

    @Override public void zoom(float scale) { speed.zoom = scale; }
    @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY) { sample(x, y, pressure, tiltX, tiltY, PenSpeed.UNTIMED); }
    @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY, long time) {
        speed.sample(x, y, time);
        float size = Float.isNaN(most) ? PenSpeed.fingerDiameter(diameter, speed.factor(PenSpeed.Effect.FINGER_ERASER))
                : InkWashSizes.diameter(most, pressure) * speed.factor(PenSpeed.Effect.PEN_ERASER);
        if (Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(size)) {
            if (Float.isNaN(lastX)) scrape = (int) document.revision() * 0x9e3779b1 ^ Float.floatToIntBits(x) * 31 ^ Float.floatToIntBits(y);
            else { double dx = x - lastX, dy = y - lastY, d = Math.hypot(dx, dy); if (d > .5) { headX = dx / d; headY = dy / d; } }
            lastX = x; lastY = y; lastSize = size;
        }
        footprint.to(x, y, size, document.width, document.height, erase);
    }
    /** Where the eraser last was and how wide, {x, y, diameter} in page pixels, speed included; null before it has been anywhere. */
    float[] at() { return Float.isNaN(lastX) ? null : new float[]{lastX, lastY, lastSize}; }
    /** Erases the ink on the pixels {@code x0} to {@code x1} (exclusive) of row {@code y} the stroke has not been over yet. */
    private void erase(int y, int x0, int x1) {
        if (coverage.cover(y, x0, x1, ones, fresh) == 0) return;
        // Fluid it can lift: this pass takes the fluid (at lift) and leaves what is under it.
        boolean any = lifts == null;
        if (lifts != null) for (int i = 0; i < x1 - x0; i++) {
            if (fresh[i] == 0) continue;
            if (document.maskLiftable(x0 + i, y) > 1) { fresh[i] = 0; lifts.cover(x0 + i, y, 1); }
            else any = true;
        }
        if (!any) return;
        // Tone's dots: a dot stays where its streak scraped lightly this pass.
        any = false;
        for (int i = 0; i < x1 - x0; i++) { if (fresh[i] == 0) continue; if (scrapes(x0 + i, y)) any = true; else fresh[i] = 0; }
        if (!any) return;
        take(x0, y, x1 - x0, 1, fresh);
        int a = 0, b = x1 - x0 - 1;
        while (fresh[a] == 0) a++;
        while (fresh[b] == 0) b--;
        newLeft = Math.min(newLeft, x0 + a); newRight = Math.max(newRight, x0 + b + 1);
        newTop = Math.min(newTop, y); newBottom = Math.max(newBottom, y + 1);
    }

    /**
     * Whether this pass takes page pixel {@code x, y}: anything but a Tone dot (a chosen kind takes those too); a dot by its streak, across the
     * stroke 1.4 px wide (mischief-lab.html's dab1, its passes of the blade taken at once): a light streak takes about
     * one in five, a heavy one nine in ten.
     */
    private boolean scrapes(int x, int y) {
        // A chosen kind takes Tone's dots whole (the owner, 2026-10-20, of Pen; Paint since textures are); Everything scrapes them a little each pass.
        if (!dot(x, y) || kinds != 0) return true;
        double n = PrintScreen.hash((int) Math.floor((-headY * x + headX * y) / 1.4), 0, 31), take = 1 - Math.pow(1 - (.08 + .5 * n * n), SCRAPES);
        return PrintScreen.hash(x, y, scrape) < take;
    }
    /** Whether page pixel {@code x, y} of the ink layer (the gesture's) holds a Tone dot. */
    private boolean dot(int x, int y) { return document.opacity(x, y) == PrintScreen.DOT_COVERAGE && document.tone(x, y) == PrintScreen.DOT_TONE; }
    /** How many of the prototype's blade passes one eraser pass takes, at a point. */
    private static final int SCRAPES = 3;

    /** Whether the screen may show what lies under the selected pair wherever the eraser went, before the wash goes at lift. */
    boolean previews() { return previews; }
    /**
     * For the {@code w} by {@code h} block at {@code left, top}, row by row into {@code out}: the tone the page
     * will show once the eraser lifts wherever it went (the page with the selected pair at what it keeps there,
     * {@link ToneDocument#compositeToneKept}), -1 elsewhere and on a Tone dot it left.
     */
    void erasedTones(int left, int top, int w, int h, int[] out) {
        if (fresh.length < w) throw new IllegalArgumentException("Wider than the page");
        int from = Math.min(washLayer, inkLayer), to = Math.max(washLayer, inkLayer);
        for (int y = 0; y < h; y++) {
            coverage.levels(left, top + y, w, 1, fresh);
            // A Tone dot the scrape left shows as it is.
            for (int x = 0; x < w; x++) out[y * w + x] = fresh[x] == 0 || kinds == 0 && dot(left + x, top + y) ? -1 : document.compositeToneKept(left + x, top + y, from, to, takes);
        }
    }
    /** {left, top, right, bottom} of the pixels erased since the last call, right and bottom exclusive; null for none. */
    int[] takeErased() {
        if (newLeft >= newRight) return null;
        int[] area = {newLeft, newTop, newRight, newBottom};
        newLeft = newTop = Integer.MAX_VALUE; newRight = newBottom = 0;
        return area;
    }
    /** {left, top, right, bottom} of every pixel erased so far; null for none. */
    int[] erasedBounds() { return coverage.bounds(); }
    /** Marks the erased pixels of the {@code w} by {@code h} block at {@code left, top} non-zero in {@code out}, row by row. */
    void erased(int left, int top, int w, int h, byte[] out) { coverage.levels(left, top, w, h, out); }

    @Override public boolean finish() {
        boolean changed;
        try {
            boolean ink = document.finish(), fluid = false, wash = false;
            // A tile at a time: the same as eraseTone pixel by pixel, checked once a block.
            byte[] erased = new byte[WashCoverage.TILE * WashCoverage.TILE], kept = lifts == null ? null : new byte[erased.length];
            int[] lifted = lifts == null ? null : lifts.bounds();
            int[] went = coverage.bounds();
            if (lifts != null && went != null) {
                // The fluid it lifted, but what the eraser can't take (wax); wherever else it went, wax too (a crayon's,
                // a wax resist's), as it took the paint under it: everything (the owner, 2026-10-25). One step with the ink.
                document.beginFluid(1);
                coverage.forEachTile(went[0], went[1], went[2], went[3], (left, top, right, bottom) -> {
                    int n = (right - left) * (bottom - top);
                    coverage.levels(left, top, right - left, bottom - top, erased);
                    lifts.levels(left, top, right - left, bottom - top, kept);
                    for (int i = 0; i < n; i++) if (kept[i] != 0) erased[i] = 0;
                    take(left, top, right - left, bottom - top, erased);
                    take(left, top, right - left, bottom - top, kept, 0);
                });
                fluid = ink ? document.finishWithPrevious() : document.finish();
            }
            // Inkwash: the pair's other planes it takes, the ink layer's then the wash layer's, base first, each a gesture
            // of its own (a gesture binds one plane), one Undo step with the rest. A plane of another kind is left whole.
            for (int layer : washLayer != inkLayer ? new int[]{inkLayer, washLayer} : new int[]{inkLayer}) {
                document.selectLayer(layer);
                for (int p = -1; p < ToneDocument.PLANES; p++) {
                    if (layer == inkLayer && p == live || p >= 0 && (ToneDocument.PLANE_KINDS[p] & takes) == 0) continue;
                    if (p < 0) document.begin(); else if (!document.beginPlane(p, false)) continue;
                    int[] b = coverage.bounds();
                    if (b != null) coverage.forEachTile(b[0], b[1], b[2], b[3], (left, top, right, bottom) -> {
                        coverage.levels(left, top, right - left, bottom - top, erased);
                        // Under the fluid it lifted, the paint stays.
                        if (lifted != null) {
                            lifts.levels(left, top, right - left, bottom - top, kept);
                            for (int i = 0; i < (right - left) * (bottom - top); i++) if (kept[i] != 0) erased[i] = 0;
                        }
                        take(left, top, right - left, bottom - top, erased);
                    });
                    wash |= ink || fluid || wash ? document.finishWithPrevious() : document.finish();
                }
            }
            changed = ink || fluid || wash;
        } finally { document.selectLayer(restore); }
        // After the layer is restored: Undo puts back the layer selected with the design.
        if (kinds == 0 && document.rubs())
            changed |= document.rubDesign(CoverWear.with(document.wear(), document.width, document.height, coverage.bounds(), this::erased), coverage.bounds(), changed);
        return changed;
    }

    /** {@link ToneDocument#eraseTones} of the kinds this eraser takes, whole. */
    private void take(int x, int y, int w, int h, byte[] erased) { take(x, y, w, h, erased, takes); }
    /** As above, of {@code kinds} ({@link ToneDocument#eraseKinds}; 0: the Erasable rule, for the fluid layer's wax). */
    private void take(int x, int y, int w, int h, byte[] erased, int kinds) {
        document.eraseKinds(kinds);
        try { document.eraseTones(x, y, w, h, erased); } finally { document.eraseKinds(0); }
    }

    /** Drops the stroke, restoring what it erased: a finger that turned out to be a tap or a palm. */
    void cancel() { document.cancel(); document.selectLayer(restore); }
}
