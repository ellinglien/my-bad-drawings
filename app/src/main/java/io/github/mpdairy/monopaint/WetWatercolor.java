package io.github.mpdairy.monopaint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/** Transient water and pigment, separate from the dry drawing beneath it.
 * All calls run on the document owner thread; only the pixel work of a batch is shared
 * with spare cores, inside the call. Only painted tiles allocate storage.
 * Drying drops this state; the composited tones remain ordinary saved pixels. */
final class WetWatercolor {
    static final int FRAME_MS = 150;
    /**
     * Once no flowing water is {@link #LOOSE}, a small wash's frames take at least this long.
     * The fresh water's frames, which make most of the watercolor's look, run as fast as they
     * can; a small wash could otherwise run through the rest in a moment too, settling
     * before the tablet can be tilted. At this pace the slower tail runs for about two seconds.
     * Bigger washes take less of a wait, and from {@link #PACED_TILES} tiles, none.
     */
    private static final int FLOW_FRAME_MS = 50, PACED_TILES = 64;
    /** Spare cores that share each batch's pixel work with the calling thread, or none on one core. */
    private static final int HELPERS = Math.max(0, Math.min(3, Runtime.getRuntime().availableProcessors() - 1));
    private static final java.util.concurrent.ExecutorService WORKERS = HELPERS == 0 ? null
            : java.util.concurrent.Executors.newFixedThreadPool(HELPERS, work -> {
                Thread thread = new Thread(work, "Wet paint"); thread.setDaemon(true); return thread;
            });
    private static final int SIDE = 16, PIXELS = SIDE * SIDE, FRAMES = 20;
    private static final int TILES_PER_FRAME = 96;
    /** Brush pen water stays wet longer and evens out quickly with the wet paint around it. */
    private static final int WATER_FRAMES = 40;
    /**
     * Flowing water dries by this factor each frame. Once {@link #STILL} it rests and stops
     * holding its tile open: the ink it would still move, by {@link #DAMP} (mobility under
     * 3%), changes a stroke's look by only a shade or two, and stopping first saves a
     * quarter of the frames. Below DAMP water also stops joining tile-scale spreading.
     */
    private static final float DRYING = .985f, STILL = .4f, DAMP = .3f;
    /**
     * From this many tiles running at once, a wash of flowing water is huge, and each of its
     * frames stands for {@link #HUGE_STEP} frames of drying and of ink running between tiles.
     * It settles in that many times fewer frames, with less time for ink to bleed pixel by
     * pixel: coarser, but a page's worth of paint runs and settles without a long wait.
     */
    private static final int HUGE_TILES = 2048, HUGE_STEP = 2;
    /**
     * Water this fresh, for its first 23 frames, still runs at full speed, and a watercolor
     * brush finds it wet enough to bleed into. Water runs on more slowly after that, until a
     * wash goes back over it: then it rests, and the wash glazes over its paint instead of
     * lifting it.
     */
    private static final float LOOSE = .7f;
    /**
     * Share of the soaking still to come each dab of a watercolor brush soaks into the stuck
     * paint under it. A single pass is several overlapping dabs, so this stays small: it
     * takes a good deal of scrubbing before the brush can lift much.
     */
    private static final float SCRUB = .027f;
    /**
     * Whether scrubbing soaks stuck paint loose at all. Off for now: the brush lays a flat
     * glaze while drawn, and dried paint stays put under it. The wet brush pen still lifts it.
     */
    static final boolean SCRUB_LIFTS = false;
    /**
     * Share of the paint under a watercolor brush that a fully wet canvas loosens into the
     * wash, to bleed and run with it; the rest stays stuck and shows through. Lifting it all
     * let the brush soak up whatever it crossed and gravity pool it at the stroke's low end.
     */
    private static final float CANVAS_LIFT = .3f;
    /** Share of an ink-holding brush's usual trade with the paint under it that opaque water mixes into the brush instead. */
    private static final float MIX = .25f;
    /** Dry paint that flowing water lifts holds this many times its own shade in pigment, and dissolves this fast when fresh. */
    private static final float DEPTH = 2, DISSOLVE = .8f;
    /** Share of the ink a brush soaks from lifted paint that comes from what shows, while its reserve lasts. */
    private static final float SHOWS = .4f;
    /** How readily water of this freshness lets ink through: drying water quickly becomes sluggish. */
    private static float mobility(float fresh) { return fresh * fresh * fresh; }
    /** Share of the ink difference that tiles joined by a full edge of fresh flowing water trade each step; at most 1/4 stays stable. */
    private static final float SPREAD = .25f;
    /** Tile-scale steps per frame, so ink runs quickly through fresh water without evening it all out. */
    private static final int SPREAD_STEPS = 8;
    /** Tiles measured at once while planning the spread, between checks of the deadline. */
    private static final int MEASURED = 256;
    private final ArrayList<Tile> spreading = new ArrayList<>();
    private float[] spreadInk = new float[16], spreadChange = new float[16], links = new float[16], downhill = new float[16];
    private int[] spreadPixels = new int[16];
    /** Share of a straight-downhill tile pair's trade that falls instead of evening out, on an upright tablet. */
    private static final float POOL = .7f;
    private int[] linked = new int[32];
    /** Working space for tiles computed on this thread; and the current frame. */
    private final Scratch scratch = new Scratch();
    private int frame;
    // Spread planning shares the animation deadline, including the first frame.
    // Snapshot queue membership because pen input may wake more tiles between slices.
    private Tile[] spreadCandidates;
    private int spreadPhase, spreadCursor, spreadPairs, spreadStep;
    /** How much of its load the brush pen leaves in fresh water at full pull, and picks up from each dab. */
    private static final float CARRY = .5f, PICKUP = .3f;
    private final ToneDocument document;
    private final int columns;
    private final Tile[] tiles;
    private final ArrayDeque<Tile> active = new ArrayDeque<>();
    private final ArrayList<Tile> batch = new ArrayList<>();
    private boolean canContinue;
    private final boolean wholeCanvas;
    private float wetness = 1;
    /** Downhill direction in page axes, length 0 (flat) to 1 (upright). */
    private float gravityX, gravityY;
    private int strokePixels, frameRemaining, advancedTiles;
    /** Pigment the brush pen carries along the current stroke. */
    private float load;
    private boolean loaded, hasFlowing, flowingFrame;
    /** Whether flowing water was still {@link #LOOSE} in the last whole frame, or has been laid since; and so far in this frame. */
    private boolean looseWater, looseSoFar;
    /** The most tiles running at once since the water was last all still: how big this wash is. */
    private int runTiles;
    /** Frames of drying this frame stands for, see {@link #HUGE_TILES}; and how much flowing water dries in it. */
    private int step = 1;
    private float drying = DRYING;
    /** Whether this stroke's watercolor brush has stopped older water from running; see {@link #restOlderWater}. */
    private boolean restedOlder;
    /**
     * Brush dabs shown at once as a plain glaze, waiting their turn in the full model, in order.
     * {@link #STROKE} marks where a later stroke began. Laying a big watercolor dab in full
     * costs several times the plain glaze, so the pen never waits for it.
     */
    private final ArrayDeque<Dab> queued = new ArrayDeque<>();
    private static final Dab STROKE = new Dab(null, null, 0, 0, 0, 0, 0, false, 0, null);
    private int[] replayMask = new int[0];

    WetWatercolor(ToneDocument document) {
        this(document, false, 100);
    }
    WetWatercolor(ToneDocument document, int amount) {
        this(document, true, amount);
    }
    private WetWatercolor(ToneDocument document, boolean wholeCanvas, int amount) {
        this.wholeCanvas = wholeCanvas;
        setWetness(amount);
        this.document = document;
        columns = (document.width + SIDE - 1) / SIDE;
        tiles = new Tile[columns * ((document.height + SIDE - 1) / SIDE)];
    }
    /** True when existing paint everywhere joins in; false when only painted pixels are wet. */
    boolean coversCanvas() { return wholeCanvas; }
    void setWetness(int amount) { wetness = Math.max(0, Math.min(100, amount)) / 100f; }
    void setGravity(float x, float y) { gravityX = x; gravityY = y; }
    /** Wet reach beyond a stamp: paint can run further downhill before it dries. */
    private int margin(float downhill) { return 8 + Math.round(24 * Math.max(0, downhill)); }
    void beginStroke() {
        canContinue = false;
        for (Tile tile : tiles) if (tile != null && tile.previewed != null) tile.previewed.clear();
        // Dabs still waiting from the last stroke keep their own stroke's state.
        if (queued.isEmpty()) startStroke(); else queued.addLast(STROKE);
    }
    private void startStroke() {
        strokePixels = 0;
        loaded = restedOlder = false;
        for (Tile tile : tiles) if (tile != null) tile.touched.clear();
    }
    void paintMask(int[] mask, int stride, int x, int y, int w, int h, int gray) {
        paintMask(mask, stride, x, y, w, h, gray, false);
    }
    void paintMask(int[] mask, int stride, int x, int y, int w, int h, int gray, boolean transparent) {
        paintMask(mask, stride, x, y, w, h, gray, transparent, 1);
    }
    /** {@code strength}, from 0 to 1, moves each pixel only that far toward the paint, as a brush running out of it does. */
    void paintMask(int[] mask, int stride, int x, int y, int w, int h, int gray, boolean transparent, float strength) {
        lay(true, Long.MAX_VALUE);
        // Rehydrate the local neighborhood lazily: existing drawing is pigment too.
        // The whole canvas is eligible, without scanning/allocating it on a toggle.
        int marginLeft = margin(-gravityX), marginTop = margin(-gravityY);
        int marginRight = margin(gravityX), marginBottom = margin(gravityY);
        if (wholeCanvas) hydrate(x - marginLeft, y - marginTop, x + w + marginRight, y + h + marginBottom);
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
            int px = x + col, py = y + row;
            if ((mask[row * stride + col] >>> 24) == 0 || px < 0 || py < 0
                    || px >= document.width || py >= document.height) continue;
            Tile tile = tileAt(px, py);
            int index = (py % SIDE) * SIDE + px % SIDE;
            // Interpolated stamps must not repeatedly replace pigment or the moving trail.
            if (tile.touched.get(index)) continue;
            tile.touched.set(index);
            strokePixels++;
            float pigment = (255 - gray) * strength;
            if (wholeCanvas) {
                float old = tile.pigment[index];
                if (transparent) pigment = old + (255 - old) * (255 - gray) * strength / 510f;
                else pigment = old + (255 - gray - old) * strength;
                // Opaque paint starts crisp, then fades into the pigment underneath.
                // Clear water introduces no white pigment; it only wakes diffusion.
                tile.target[index] = old * (.6f * wetness) + pigment * (1 - .6f * wetness);
                tile.pigment[index] = pigment;
                clearWater(tile, index);
                if(transparent) document.glazeTone(px,py,Math.round(255 - (255 - gray) * strength));
                else document.paintTone(px,py,Math.round(255 - pigment));
                composite(tile, index, px, py);
                wake(tile);
                continue;
            }
            if (!tile.wet.get(index)) {
                tile.base[index] = (byte)dryTone(tile, index, px, py);
                tile.target[index] = pigment;
                markWet(tile, index);
            } else tile.target[index] = tile.pigment[index] * .45f + pigment * .55f;
            tile.pigment[index] = pigment;
            clearWater(tile, index);
            composite(tile, index, px, py);
            wake(tile);
        }
        wakeAround(x - marginLeft, y - marginTop, x + w + marginRight, y + h + marginBottom);
    }
    /**
     * Brush pen clear water. It wets only the pixels it touches, so on a dry canvas its
     * edges stay sharp, and lifts the paint already there. Inside the water pigment flows
     * freely, so nearby paint seeps into it, thinning as it spreads. The pen also drags
     * a little of the pigment it passes over. {@code pull}, from 0 to 1, scales both.
     * <p>
     * {@code flowing} water instead behaves like ink in water: it does not drag, and
     * neighboring water pixels trade pigment at the rate of the less fresh one, so ink
     * evens out quickly through fresh water and seeps slowly into water that has begun
     * to dry. There {@code pull} is the water's freshness, which fades each frame. Dry
     * paint it lifts is a hidden reserve that keeps dissolving into the water while it is
     * fresh, but never makes that water darker than the paint was.
     */
    void waterMask(int[] mask, int stride, int x, int y, int w, int h, float pull, boolean flowing) {
        waterMask(mask, stride, x, y, w, h, pull, flowing, 0);
    }
    /**
     * {@code carry}, from 0 to 1, slows how fast the dragging pen trades its load for the
     * paint under it, so it pushes what it picked up, white too, further along the stroke.
     * The slowing is squared so the middle of the range already carries a long way;
     * at 1 the pen keeps its first load for the whole stroke.
     */
    void waterMask(int[] mask, int stride, int x, int y, int w, int h, float pull, boolean flowing, float carry) {
        waterMask(mask, stride, x, y, w, h, pull, flowing, carry, null);
    }
    /**
     * @param ink the ink a flowing brush holds between strokes, traded with every wet pixel
     *            it covers; or null. Its water is at least its {@link BrushInk#tint}.
     */
    void waterMask(int[] mask, int stride, int x, int y, int w, int h, float pull, boolean flowing, float carry, BrushInk ink) {
        waterMask(mask, stride, x, y, w, h, pull, flowing, carry, ink, -1, 0);
    }
    /**
     * @param cover for opaque paint, the shade the water carries, which covers {@code coverage}
     *              of the paint each pixel holds as the stroke first reaches it, lifted paint's
     *              hidden reserve too, so white pushes dark back; or -1 for clear water
     * @param coverage from 0 to 1
     */
    void waterMask(int[] mask, int stride, int x, int y, int w, int h, float pull, boolean flowing, float carry, BrushInk ink, int cover, float coverage) {
        lay(true, Long.MAX_VALUE);
        layWater(mask, stride, x, y, w, h, pull, flowing, carry, ink, null, cover, coverage);
    }
    /**
     * {@link #waterMask} for a watercolor brush, shown at once and laid in full later, when
     * there is time: see {@link #advance}. Until then each pixel the dab newly covers shows
     * the brush's tint glazed over it, which is what a clean brush leaves on dry paper.
     */
    void queueWater(int[] mask, int stride, int x, int y, int w, int h, float pull, boolean flowing, float carry, BrushInk ink) {
        queueWater(mask, stride, x, y, w, h, pull, flowing, carry, ink, null);
    }
    /**
     * @param hits for a run of stamps queued as one, how many of them covered each pixel,
     *             in the mask's layout; the brush rubs each pixel once per stamp. Null for one stamp.
     */
    void queueWater(int[] mask, int stride, int x, int y, int w, int h, float pull, boolean flowing, float carry, BrushInk ink, byte[] hits) {
        byte[] alpha = new byte[w * h], counts = hits == null ? null : new byte[w * h];
        float tint = ink == null ? 0 : ink.tint();
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
            int coverage = mask[row * stride + col] >>> 24, px = x + col, py = y + row;
            alpha[row * w + col] = (byte)coverage;
            if (counts != null) counts[row * w + col] = hits[row * stride + col];
            if (coverage == 0 || px < 0 || py < 0 || px >= document.width || py >= document.height) continue;
            Tile tile = tileAt(px, py);
            int index = (py % SIDE) * SIDE + px % SIDE;
            if (tile.previewed == null) { tile.previewed = new BitSet(PIXELS); tile.held = new BitSet(PIXELS); tile.under = new byte[PIXELS]; }
            if (tile.previewed.get(index)) continue;
            tile.previewed.set(index);
            int tone = document.tone(px, py);
            // The model reads the paint as it was, not the preview over it.
            if (!tile.held.get(index)) { tile.held.set(index); tile.under[index] = (byte)tone; }
            document.setTone(px, py, Math.round(tone * (1 - tint * coverage / 255f)));
        }
        queued.addLast(new Dab(alpha, counts, x, y, w, h, pull, flowing, carry, ink));
    }
    private void layNext() {
        Dab dab = queued.removeFirst();
        if (dab == STROKE) { startStroke(); return; }
        int size = dab.w * dab.h;
        if (replayMask.length < size) replayMask = new int[size];
        for (int i = 0; i < size; i++) replayMask[i] = (dab.alpha[i] & 255) << 24;
        layWater(replayMask, dab.w, dab.x, dab.y, dab.w, dab.h, dab.pull, dab.flowing, dab.carry, dab.ink, dab.hits, -1, 0);
    }
    /**
     * Lays all queued dabs in full outside a gesture, as one continuation of the last
     * stroke, e.g. before the paint dries. False if there were none, or nothing changed.
     */
    boolean layQueued(boolean strokeActive) { return lay(strokeActive, Long.MAX_VALUE); }
    /** Lays queued dabs for about {@code budgetNanos}, at least one; false if none, or nothing changed. */
    private boolean lay(boolean strokeActive, long budgetNanos) {
        if (queued.isEmpty()) return false;
        long start = System.nanoTime();
        if (!strokeActive) document.begin();
        do layNext(); while (!queued.isEmpty() && System.nanoTime() - start < budgetNanos);
        return !strokeActive && finishSlice();
    }
    /** Ends a slice of work outside a gesture; the first that changes anything after a stroke starts its own Undo step. */
    private boolean finishSlice() {
        boolean changed = canContinue ? document.finishContinuation() : document.finish();
        canContinue |= changed;
        return changed;
    }
    /**
     * @param hits stamps per pixel for a run queued as one, in the mask's layout; or null for one stamp
     * @param cover the opaque shade the water carries, or -1; and how much it covers, see {@link #waterMask(int[], int, int, int, int, int, float, boolean, float, BrushInk, int, float)}
     */
    private void layWater(int[] mask, int stride, int x, int y, int w, int h, float pull, boolean flowing, float carry, BrushInk ink, byte[] hits, int cover, float covers) {
        boolean sticky = ink != null && ink.dryPaintSticks;
        if (sticky && !wholeCanvas && !restedOlder) { restOlderWater(); restedOlder = true; }
        int marginLeft = margin(-gravityX), marginTop = margin(-gravityY);
        int marginRight = margin(gravityX), marginBottom = margin(gravityY);
        if (wholeCanvas) hydrate(x - marginLeft, y - marginTop, x + w + marginRight, y + h + marginBottom);
        // Only the dragging pen needs a first pass to measure its pigment load.
        // Flowing water lifts each new pixel in the application pass below.
        float under = 0; int count = 0;
        if (!flowing) {
            for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
                int px = x + col, py = y + row;
                if ((mask[row * stride + col] >>> 24) == 0 || px < 0 || py < 0
                        || px >= document.width || py >= document.height) continue;
                Tile tile = tileAt(px, py);
                int index = (py % SIDE) * SIDE + px % SIDE;
                if (!tile.wet.get(index)) lift(tile, index, px, py);
                under += tile.pigment[index]; count++;
            }
            if (count == 0) return;
            under /= count;
            if (!loaded) { load = under; loaded = true; }
        }
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
            int px = x + col, py = y + row;
            if ((mask[row * stride + col] >>> 24) == 0 || px < 0 || py < 0
                    || px >= document.width || py >= document.height) continue;
            Tile tile = tileAt(px, py);
            int index = (py % SIDE) * SIDE + px % SIDE;
            float coverage = (mask[row * stride + col] >>> 24) / 255f;
            int rubs = hits == null ? 1 : hits[row * stride + col] & 255;
            // Each dab adds water only where the pen is newly arriving, but rubbing keeps trading ink.
            if (tile.touched.get(index)) {
                if (cover < 0) for (int i = 0; i < rubs; i++) rub(tile, index, px, py, ink, pull, coverage);
                continue;
            }
            // A watercolor brush leaves dry paint stuck, a wet canvas only loosening some of it;
            // anything else lifts it all.
            if (sticky) {
                settleDried(tile, index, px, py);
                if (wholeCanvas) loosen(tile, index, wetness * CANVAS_LIFT);
                scrub(tile, index, coverage);
            }
            else if (!tile.wet.get(index)) lift(tile, index, px, py);
            else loosen(tile, index, 1);
            tile.touched.set(index);
            strokePixels++;
            boolean newWater = !tile.water.get(index);
            if (!flowing) tile.pigment[index] += (load - tile.pigment[index]) * CARRY * pull;
            // Water ignores its target; flowing water keeps the lifted paint's shade there.
            if (newWater) tile.target[index] = tile.pigment[index];
            if (tile.pull == null) { tile.pull = new float[PIXELS]; tile.mobility = new float[PIXELS]; }
            // More water over a wet spot never makes it pull less. Full pull evens each
            // water pixel with its neighbors every frame, the most that stays stable.
            tile.pull[index] = newWater ? pull : Math.max(tile.pull[index], pull);
            tile.mobility[index] = mobility(tile.pull[index]);
            if (flowing && newWater && tile.pigment[index] > 0) {
                if (tile.reserve == null) tile.reserve = new float[PIXELS];
                tile.reserve[index] = tile.pigment[index] * (DEPTH - 1);
            }
            tile.water.set(index);
            if (flowing) { tile.flowing.set(index); hasFlowing = looseWater = true; } else tile.flowing.clear(index);
            if (flowing && ink == null) { if (tile.plain == null) tile.plain = new BitSet(PIXELS); tile.plain.set(index); }
            else if (tile.plain != null) tile.plain.clear(index);
            if (cover >= 0) {
                // Opaque water covers what it lands on, lifted paint's reserve too, and a brush
                // holding ink takes on a little of the paint it covers instead of soaking it up.
                float covered = covers * coverage, beneath = tile.pigment[index];
                tile.pigment[index] += (255 - cover - beneath) * covered;
                tile.target[index] = tile.pigment[index];
                if (tile.reserve != null) tile.reserve[index] *= 1 - covered;
                if (tile.stuck != null) tile.stuck[index] *= 1 - covered;
                if (ink != null) ink.trade(beneath, pull * coverage * MIX);
                composite(tile, index, px, py);
                wake(tile, WATER_FRAMES);
                continue;
            }
            // A tinted brush's water is never lighter than its tint; darker paint it lifts stays.
            // Over stuck paint the tint fills only the light left, glazing like transparent paint.
            if (ink != null && flowing) {
                float tint = 255 * ink.tint() * (1 - (tile.stuck == null ? 0 : tile.stuck[index]) / 255f);
                if (tile.pigment[index] < tint) tile.pigment[index] += (tint - tile.pigment[index]) * coverage;
            }
            if (ink != null && flowing) soak(tile, index, px, py, ink, pull * coverage);
            else composite(tile, index, px, py);
            for (int i = 1; i < rubs; i++) rub(tile, index, px, py, ink, pull, coverage);
            wake(tile, WATER_FRAMES);
        }
        if (!flowing) load += (under - load) * PICKUP * (1 - carry) * (1 - carry);
        wakeAround(x - marginLeft, y - marginTop, x + w + marginRight, y + h + marginBottom);
    }
    /** A dab over water the stroke already laid trades ink with it again. */
    private void rub(Tile tile, int index, int px, int py, BrushInk ink, float pull, float coverage) {
        if (ink == null || !tile.flowing.get(index)) return;
        if (ink.dryPaintSticks) scrub(tile, index, coverage);
        soak(tile, index, px, py, ink, pull * coverage);
    }
    /**
     * Trades ink between the brush and a pixel of flowing water. Ink the brush soaks up
     * leaves the pixel's dissolving reserve and lifted shade in step, so the spot stays
     * lighter instead of refilling from paint the brush already took.
     */
    private void soak(Tile tile, int index, int px, int py, BrushInk ink, float share) {
        // Soaked stuck paint is within the brush's reach too, but never flows by itself.
        float pigment = tile.pigment[index], reachable = reachable(tile, index);
        float moved = ink.trade(pigment + reachable, share);
        // Even with nothing to trade, the pixel shows the water just laid on it.
        if (moved == 0) { composite(tile, index, px, py); return; }
        if (moved > 0) {
            // Lifted paint holds more ink than shows: the brush drinks mostly from that reserve,
            // so touching a dark line fills it with gray while the line only lightens a little.
            // The rest is loose ink, and last the soaked paint beneath.
            float hidden = tile.reserve == null ? 0 : Math.min(moved * (1 - SHOWS), tile.reserve[index]);
            if (hidden > 0) tile.reserve[index] -= hidden;
            float rest = moved - hidden, loose = Math.min(rest, pigment);
            rest -= loose;
            if (loose > 0) {
                float kept = 1 - loose / pigment;
                if (tile.reserve != null) tile.reserve[index] *= kept;
                tile.target[index] *= kept;
            }
            tile.pigment[index] = pigment - loose;
            if (rest > 0 && tile.stuck != null) tile.stuck[index] = Math.max(0, tile.stuck[index] - rest);
        } else tile.pigment[index] = Math.min(255, pigment - moved);
        composite(tile, index, px, py);
    }
    /** How much of a pixel's stuck paint the brush can take: the square of how soaked it is. */
    private static float reachable(Tile tile, int index) {
        if (tile.stuck == null || tile.stuck[index] <= 0) return 0;
        float soaked = tile.soaked[index];
        return tile.stuck[index] * soaked * soaked;
    }
    /**
     * One dab of a watercolor brush soaks a little into the stuck paint under it, which then
     * stays soaked while its water is wet. Soaked paint does not run into the wash; only the
     * brush, coming back over it, can take some: see {@link #reachable}.
     */
    private static void scrub(Tile tile, int index, float coverage) {
        if (!SCRUB_LIFTS || tile.stuck == null || tile.stuck[index] <= 0) return;
        tile.soaked[index] += (1 - tile.soaked[index]) * SCRUB * coverage;
    }
    private Tile tileAt(int x, int y) {
        int key = (y / SIDE) * columns + x / SIDE;
        Tile tile = tiles[key];
        if (tile == null) tiles[key] = tile = new Tile(x / SIDE * SIDE, y / SIDE * SIDE);
        return tile;
    }
    /** Adjacent old washes participate even when the new stamp only touches their edge. */
    private void wakeAround(int left, int top, int right, int bottom) {
        left = Math.max(0, left) / SIDE; top = Math.max(0, top) / SIDE;
        right = Math.min(document.width - 1, right) / SIDE;
        bottom = Math.min(document.height - 1, bottom) / SIDE;
        for (int ty = top; ty <= bottom; ty++) for (int tx = left; tx <= right; tx++) {
            Tile tile = tiles[ty * columns + tx];
            if (tile != null) wake(tile);
        }
    }
    private void hydrate(int left, int top, int right, int bottom) {
        left = Math.max(0, left); top = Math.max(0, top);
        right = Math.min(document.width, right); bottom = Math.min(document.height, bottom);
        if (left >= right || top >= bottom) return;
        // Successive stamps overlap almost entirely. Skip tiles that are already
        // fully wet instead of revisiting each of their pixels on every stamp.
        for (int ty = top / SIDE; ty <= (bottom - 1) / SIDE; ty++)
            for (int tx = left / SIDE; tx <= (right - 1) / SIDE; tx++) {
                Tile tile = tiles[ty * columns + tx];
                if (tile == null) tiles[ty * columns + tx] = tile = new Tile(tx * SIDE, ty * SIDE);
                else if (tile.wetPixels == PIXELS) continue;
                int x0 = Math.max(left, tile.x), x1 = Math.min(right, tile.x + SIDE);
                int y0 = Math.max(top, tile.y), y1 = Math.min(bottom, tile.y + SIDE);
                for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                    int i = (y - tile.y) * SIDE + x - tile.x;
                    if (!tile.wet.get(i)) lift(tile, i, x, y);
                }
            }
    }
    /** Wets a dry pixel, taking its drawn tone up as pigment over white. */
    /**
     * Where a watercolor brush's water lands on a dry canvas: water still wet from an
     * earlier stroke keeps its loose ink, but dry paint, and water that has dried, stays
     * stuck to the paper beneath fresh water.
     */
    private void settleDried(Tile tile, int index, int x, int y) {
        boolean wet = tile.wet.get(index);
        if (wet && tile.flowing.get(index) && tile.pull != null && tile.pull[index] > LOOSE) return;
        int tone = dryTone(tile, index, x, y);
        if (!wet) markWet(tile, index);
        tile.base[index] = (byte)255;
        tile.pigment[index] = tile.target[index] = 0;
        if (tile.reserve != null) tile.reserve[index] = 0;
        if (tile.stuck == null) { tile.stuck = new float[PIXELS]; tile.soaked = new float[PIXELS]; }
        tile.stuck[index] = 255 - tone; tile.soaked[index] = 0;
        tile.water.clear(index); tile.flowing.clear(index);
    }
    /**
     * A watercolor brush's stroke stops older water that is no longer {@link #LOOSE} from
     * running: it may still be moving, but it reads as dried, so the new wash glazes over
     * it instead of the old paint running into the fresh water, even in gaps the brush missed.
     */
    private void restOlderWater() {
        for (Tile tile : active) {
            if (tile.pull == null) continue;
            for (int i = tile.flowing.nextSetBit(0); i >= 0; i = tile.flowing.nextSetBit(i + 1))
                if (tile.pull[i] <= LOOSE && tile.pull[i] > STILL) { tile.pull[i] = STILL; tile.mobility[i] = mobility(STILL); }
        }
    }
    /** Frees {@code share} of a pixel's stuck paint into its water as loose ink; its shade stays the same. */
    private static void loosen(Tile tile, int index, float share) {
        if (tile.stuck == null || tile.stuck[index] <= 0 || share <= 0) return;
        float freed = tile.stuck[index] * Math.min(1, share);
        tile.stuck[index] -= freed; tile.pigment[index] += freed;
    }
    private void lift(Tile tile, int index, int x, int y) {
        markWet(tile, index); tile.base[index] = (byte)255;
        tile.pigment[index] = tile.target[index] = 255 - dryTone(tile, index, x, y);
    }
    /** The drawn tone under a pixel, as the model last left it or found it, beneath any preview of queued dabs. */
    private int dryTone(Tile tile, int index, int x, int y) {
        return tile.held != null && tile.held.get(index) ? tile.under[index] & 255 : document.tone(x, y);
    }
    private void wake(Tile tile) { wake(tile, FRAMES); }
    private void wake(Tile tile, int frames) {
        if (tile.frames == 0) active.addLast(tile);
        tile.frames = Math.max(tile.frames, frames);
    }
    private static void markWet(Tile tile, int index) {
        tile.wet.set(index); tile.wetPixels++;
        int row = index / SIDE, col = index % SIDE;
        tile.wetRows[row] |= 1 << col;
        tile.wetColumns[col] |= 1 << row;
    }
    boolean finishStroke() {
        boolean changed = document.finish();
        canContinue |= changed;
        return changed;
    }
    boolean isAnimating() { return !active.isEmpty() || !queued.isEmpty(); }
    int activeTiles() { return active.size(); }
    int activePixels() { return active.size() * PIXELS; }
    int strokePixels() { return strokePixels; }
    /** About how many tile frames the paint has left to move, counting until its flowing water rests, plus queued dabs. */
    int remainingWork() {
        int work = queued.size();
        for (Tile tile : active) work += (Math.max(tile.frames, tile.dampFrames) + step - 1) / step;
        return work;
    }
    int advancedTiles() { return advancedTiles; }
    /** A frame, or queued dabs, are partly done: the next slice should follow at once. */
    boolean framePending() { return frameRemaining > 0 || !queued.isEmpty(); }
    /** Dabs shown but not yet laid in full. */
    int queuedDabs() { return queued.size(); }
    /**
     * Whether watercolor or wet canvas paint is moving, or waiting to be laid: more than the
     * dry brush pen's clear water seeping for a moment after it lifts.
     */
    boolean running() { return isAnimating() && (wholeCanvas || !queued.isEmpty() || flowing()); }
    /** Whether flowing water is moving. Its frames run faster than still paint's. */
    boolean flowing() { return flowingFrame && !active.isEmpty(); }
    /** How long flowing water's frames should take at least, or 0 to run them as fast as they can; see {@link #FLOW_FRAME_MS}. */
    int flowFrameMillis() {
        if (!flowing() || looseWater) return 0;
        return Math.round(FLOW_FRAME_MS * Math.max(0, 1 - runTiles / (float)PACED_TILES));
    }
    /** Counts frames begun, so the caller can pace them. */
    int frame() { return frame; }
    /** Unpaced advancement for model checks. The UI supplies a small work budget. */
    boolean advance(boolean strokeActive) {
        return advance(strokeActive, TILES_PER_FRAME, Long.MAX_VALUE, Integer.MAX_VALUE);
    }
    /** One spatially bounded slice; never leave a partial edit between callbacks.
     * The deadline is checked between tiles, so a single tile may exceed it. */
    boolean advance(boolean strokeActive, int maxTiles, long budgetNanos, int maxSpan) {
        advancedTiles = 0;
        if (budgetNanos <= 0) return false;
        // Paint is laid before it flows: queued dabs first.
        if (!queued.isEmpty()) return lay(strokeActive, budgetNanos);
        if (active.isEmpty() || maxTiles <= 0) return false;
        long start = System.nanoTime();
        if (frameRemaining == 0) {
            frameRemaining = active.size(); frame++;
            runTiles = Math.max(runTiles, active.size());
            flowingFrame = false;
            for (Tile tile : active) if (!tile.flowing.isEmpty()) { flowingFrame = true; break; }
            step = flowingFrame && runTiles >= HUGE_TILES ? HUGE_STEP : 1;
            drying = (float)Math.pow(DRYING, step);
            spreadCandidates = hasFlowing ? active.toArray(new Tile[0]) : null;
            spreading.clear();
            spreadPhase = spreadCursor = spreadPairs = spreadStep = 0;
        }
        if (!planSpread(start, budgetNanos)) return false;
        if (!strokeActive) document.begin();
        batch.clear();
        int count = Math.min(maxTiles, frameRemaining);
        int left = 0, top = 0, right = 0, bottom = 0;
        // Each pixel draws more from uphill neighbors than downhill ones, so pigment
        // drifts with gravity. The weights average 1, keeping total flow unchanged.
        float fromLeft = 1 + gravityX, fromRight = 1 - gravityX;
        float fromAbove = 1 + gravityY, fromBelow = 1 - gravityY;
        for (int t = 0; t < count; t++) {
            Tile tile = active.peekFirst();
            int l = t == 0 ? tile.x : Math.min(left, tile.x);
            int u = t == 0 ? tile.y : Math.min(top, tile.y);
            int r = t == 0 ? tile.x + SIDE : Math.max(right, tile.x + SIDE);
            int b = t == 0 ? tile.y + SIDE : Math.max(bottom, tile.y + SIDE);
            // A distant tile waits for the next slice instead of expanding the
            // display's dirty rectangle across otherwise unchanged artwork.
            if (t > 0 && (r - l > maxSpan || b - u > maxSpan || System.nanoTime() - start >= budgetNanos)) break;
            left = l; top = u; right = r; bottom = b;
            batch.add(active.removeFirst());
        }
        // Every tile reads its neighbors' current pigment before any tile takes its next.
        shareBatch((tile, own) -> computeTile(tile, own, fromLeft, fromRight, fromAbove, fromBelow));
        shareBatch((tile, own) -> settle(tile));
        // Only writing the tones that changed stays on this thread.
        for (Tile tile : batch) {
            document.setTones(tile.x, tile.y, SIDE, SIDE, tile.shade);
            // The model owns these pixels' tones again; any preview over them is gone.
            if (tile.held != null) tile.held.andNot(tile.wet);
            looseSoFar |= tile.loose;
            if ((tile.frames -= step) > 0) active.addLast(tile);
        }
        advancedTiles = batch.size();
        frameRemaining -= advancedTiles;
        if (frameRemaining == 0) { looseWater = looseSoFar; looseSoFar = false; }
        if (active.isEmpty()) runTiles = 0;
        return !strokeActive && finishSlice();
    }
    /**
     * Does {@code work} to every tile in {@link #batch}. Work on a tile writes only that tile,
     * and reads other tiles only where no work in the same call writes, so the spare cores
     * share the batch and the result does not depend on how it is shared.
     */
    private void shareBatch(java.util.function.BiConsumer<Tile, Scratch> work) { share(batch, work); }
    /** Does {@code work} to each of {@code tiles}, shared with the spare cores in the same way as {@link #shareBatch}. */
    private void share(List<Tile> tiles, java.util.function.BiConsumer<Tile, Scratch> work) {
        int count = tiles.size();
        if (count < 2 || WORKERS == null) {
            for (int t = 0; t < count; t++) work.accept(tiles.get(t), scratch);
            return;
        }
        java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
        Runnable share = () -> {
            Scratch own = new Scratch();
            for (int t; (t = next.getAndIncrement()) < count; ) work.accept(tiles.get(t), own);
        };
        int helpers = Math.min(HELPERS, count - 1);
        java.util.concurrent.Future<?>[] helping = new java.util.concurrent.Future<?>[helpers];
        for (int h = 0; h < helpers; h++) helping[h] = WORKERS.submit(share);
        share.run();
        try { for (java.util.concurrent.Future<?> help : helping) help.get(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
        catch (java.util.concurrent.ExecutionException failed) { throw new IllegalStateException(failed.getCause()); }
    }
    /**
     * Takes a tile's next pigment and works out the tones it shows that differ from the
     * document's, into {@link Tile#shade}. Flowing water then dissolves its reserve and keeps
     * its tile moving until it has nearly dried.
     */
    private void settle(Tile tile) {
        Arrays.fill(tile.shade, -1);
        for (int i = tile.wet.nextSetBit(0); i >= 0; i = tile.wet.nextSetBit(i + 1)) {
            tile.pigment[i] = tile.next[i];
            int tone = shade(tile, i);
            if (tone != document.tone(tile.x + i % SIDE, tile.y + i / SIDE)) tile.shade[i] = tone;
        }
        float wettest = 0;
        boolean loose = false;
        for (int i = tile.flowing.nextSetBit(0); i >= 0; i = tile.flowing.nextSetBit(i + 1)) {
            if (tile.reserve != null && tile.reserve[i] > 0) {
                float gap = Math.max(0, tile.target[i] - tile.pigment[i]);
                float dissolved = Math.min(tile.reserve[i], step == 1 ? gap * tile.pull[i] * DISSOLVE
                        : gap * (1 - (float)Math.pow(1 - tile.pull[i] * DISSOLVE, step)));
                tile.reserve[i] -= dissolved; tile.pigment[i] += dissolved;
            }
            wettest = Math.max(wettest, tile.pull[i] *= drying);
            loose |= tile.pull[i] > LOOSE;
            tile.mobility[i] = mobility(tile.pull[i]);
        }
        tile.loose = loose;
        boolean damp = wettest > STILL;
        tile.dampFrames = damp ? (int)Math.ceil(Math.log(STILL / wettest) / Math.log(DRYING)) : 0;
        if (damp) tile.frames = Math.max(tile.frames, step + 1);
    }
    private void computeTile(Tile tile, Scratch scratch, float fromLeft, float fromRight, float fromAbove, float fromBelow) {
        // This frame's tile-scale ink change: fresher water takes more of a gain, and a
        // loss comes from where the ink is, so no pixel goes below clear water.
        float[] shares = scratch.spread;
        boolean spreads = shapeSpread(tile, scratch);
        if (surrounded(tile)) { computeSurrounded(tile, scratch, spreads, fromLeft, fromRight, fromAbove, fromBelow); return; }
        for (int i = tile.wet.nextSetBit(0); i >= 0; i = tile.wet.nextSetBit(i + 1)) {
            float value = tile.pigment[i];
            if (tile.flowing.get(i)) {
                if (tile.pull[i] <= STILL) { tile.next[i] = value; continue; }
                float fresh = tile.mobility[i];
                tile.next[i] = flowed(tile, i, value, fromLeft * exchange(tile, i, -1, 0, value, fresh) + fromRight * exchange(tile, i, 1, 0, value, fresh)
                        + fromAbove * exchange(tile, i, 0, -1, value, fresh) + fromBelow * exchange(tile, i, 0, 1, value, fresh), spreads, shares);
                continue;
            }
            tile.next[i] = evened(tile, i, value, fromLeft * neighbor(tile, i, -1, 0, value) + fromRight * neighbor(tile, i, 1, 0, value)
                    + fromAbove * neighbor(tile, i, 0, -1, value) + fromBelow * neighbor(tile, i, 0, 1, value));
        }
    }
    /** A flowing pixel's next pigment, from what it gains along its rays, weighted for gravity, and its share of the tile-scale spread, see {@link #shapeSpread}. */
    private static float flowed(Tile tile, int i, float value, float gained, boolean spreads, float[] spread) {
        return value + gained / 12 + (spreads ? spread[i] : 0);
    }
    /** Any other wet pixel's next pigment, from the sum of its samples along its rays, weighted for gravity. */
    private float evened(Tile tile, int i, float value, float neighbors) {
        // Water has no settled pigment to return to: it only evens out.
        if (tile.water.get(i)) return value + (neighbors / 12 - value) * tile.pull[i];
        float flow = wholeCanvas ? wetness : 1;
        return value * (1 - .55f * flow) + neighbors * (flow / 30f) + tile.target[i] * (.15f * flow);
    }
    /** Whether a tile and the four beside it are wet all over, so no ray is cut off and every sample is wet. */
    private boolean surrounded(Tile tile) {
        if (tile.wetPixels != PIXELS) return false;
        for (int side = 0; side < 4; side++) {
            Tile other = beside(tile, 1, side == 0 ? -1 : side == 1 ? 1 : 0, side == 2 ? -1 : side == 3 ? 1 : 0);
            if (other == null || other.wetPixels != PIXELS) return false;
        }
        return true;
    }
    /**
     * {@link #computeTile} for a {@link #surrounded} tile, as most of a big wash is: the same
     * sums, read straight from a copy of the tile and the reach of its rays into its neighbors.
     */
    private void computeSurrounded(Tile tile, Scratch scratch, boolean spreads, float fromLeft, float fromRight, float fromAbove, float fromBelow) {
        float[] shares = scratch.spread, ink = scratch.ink, free = scratch.free;
        gather(tile, 0, 0, 0, 0, SIDE, SIDE, ink, free);
        gather(beside(tile, 1, -1, 0), SIDE - REACH, 0, -REACH, 0, REACH, SIDE, ink, free);
        gather(beside(tile, 1, 1, 0), 0, 0, SIDE, 0, REACH, SIDE, ink, free);
        gather(beside(tile, 1, 0, -1), 0, SIDE - REACH, 0, -REACH, SIDE, REACH, ink, free);
        gather(beside(tile, 1, 0, 1), 0, 0, 0, SIDE, SIDE, REACH, ink, free);
        for (int i = 0; i < PIXELS; i++) {
            int p = (i / SIDE + REACH) * SPAN + i % SIDE + REACH;
            float value = ink[p];
            if (tile.flowing.get(i)) {
                if (tile.pull[i] <= STILL) { tile.next[i] = value; continue; }
                float fresh = tile.mobility[i];
                tile.next[i] = flowed(tile, i, value, fromLeft * ray(ink, free, p, -1, value, fresh) + fromRight * ray(ink, free, p, 1, value, fresh)
                        + fromAbove * ray(ink, free, p, -SPAN, value, fresh) + fromBelow * ray(ink, free, p, SPAN, value, fresh), spreads, shares);
                continue;
            }
            tile.next[i] = evened(tile, i, value, fromLeft * (ink[p - 1] + ink[p - 4] + ink[p - 8]) + fromRight * (ink[p + 1] + ink[p + 4] + ink[p + 8])
                    + fromAbove * (ink[p - SPAN] + ink[p - 4 * SPAN] + ink[p - 8 * SPAN])
                    + fromBelow * (ink[p + SPAN] + ink[p + 4 * SPAN] + ink[p + 8 * SPAN]));
        }
    }
    /** Copies a block of a tile's pigment and mobility, 0 where it holds no water, into the scratch layout at {@code toX, toY} from the gathered tile's corner. */
    private static void gather(Tile from, int x, int y, int toX, int toY, int w, int h, float[] ink, float[] free) {
        for (int row = 0; row < h; row++) {
            int source = (y + row) * SIDE + x, target = (toY + row + REACH) * SPAN + toX + REACH;
            System.arraycopy(from.pigment, source, ink, target, w);
            if (from.mobility == null) Arrays.fill(free, target, target + w, 0);
            else System.arraycopy(from.mobility, source, free, target, w);
        }
    }
    /** {@link #exchange} along a ray that no dry pixel cuts off, in the scratch layout; a sample that is not water has no mobility, so it trades nothing. */
    private static float ray(float[] ink, float[] free, int p, int step, float value, float fresh) {
        float gained = 0;
        gained += Math.min(fresh, free[p + step]) * (ink[p + step] - value);
        gained += Math.min(fresh, free[p + 4 * step]) * (ink[p + 4 * step] - value);
        gained += Math.min(fresh, free[p + 8 * step]) * (ink[p + 8 * step] - value);
        return gained;
    }
    /**
     * Pixel exchange alone spreads ink only a few pixels a frame. Once per frame, tiles
     * joined by flowing water also trade their average ink over several quick steps, so
     * ink runs through the whole wet area while the water is fresh. Each pair moves the
     * same amount either way, conserving ink. The result waits in {@link Tile#spread}.
     */
    private boolean planSpread(long start, long budgetNanos) {
        if (spreadCandidates == null) return true;
        do {
            int count = spreading.size();
            if (spreadPhase == 0) {
                if (spreadCursor < spreadCandidates.length) {
                    int end = Math.min(spreadCandidates.length, spreadCursor + MEASURED);
                    List<Tile> measuring = Arrays.asList(spreadCandidates).subList(spreadCursor, end);
                    share(measuring, (tile, own) -> { if (!tile.flowing.isEmpty()) measure(tile); });
                    for (Tile tile : measuring)
                        if (!tile.flowing.isEmpty() && tile.meanFresh > DAMP) { tile.spreadSlot = spreading.size(); spreading.add(tile); }
                    spreadCursor = end;
                } else {
                    if (spreadInk.length < count) {
                        spreadInk = new float[count * 2]; spreadChange = new float[count * 2]; spreadPixels = new int[count * 2];
                    }
                    spreadPhase = 1; spreadCursor = 0;
                }
            } else if (spreadPhase == 1) {
                if (spreadCursor < count) for (int end = Math.min(count, spreadCursor + 64); spreadCursor < end; ) {
                    int a = spreadCursor++;
                    Tile tile = spreading.get(a);
                    spreadInk[a] = tile.meanInk; spreadPixels[a] = tile.flowingPixels;
                    // Right and lower neighbors only, so each pair is linked once.
                    for (int side = 0; side < 2; side++) {
                        int x = tile.x + (side == 0 ? SIDE : 0), y = tile.y + (side == 1 ? SIDE : 0);
                        if (x >= document.width || y >= document.height) continue;
                        Tile other = tiles[y / SIDE * columns + x / SIDE];
                        if (other == null || other.spreadSlot < 0) continue;
                        int joined = Integer.bitCount(side == 0 ? tile.flowRight & other.flowLeft : tile.flowBottom & other.flowTop);
                        if (joined == 0) continue;
                        if (links.length <= spreadPairs) {
                            links = Arrays.copyOf(links, spreadPairs * 2);
                            downhill = Arrays.copyOf(downhill, spreadPairs * 2);
                            linked = Arrays.copyOf(linked, spreadPairs * 4);
                        }
                        links[spreadPairs] = SPREAD * joined / SIDE * mobility(Math.min(tile.meanFresh, other.meanFresh))
                                * Math.min(tile.flowingPixels, other.flowingPixels);
                        // How steeply the pair runs downhill, from this tile to the other.
                        downhill[spreadPairs] = side == 0 ? gravityX : gravityY;
                        linked[spreadPairs * 2] = a; linked[spreadPairs * 2 + 1] = other.spreadSlot; spreadPairs++;
                    }
                } else { spreadPhase = spreadPairs == 0 ? 5 : 2; spreadCursor = 0; }
            } else if (spreadPhase == 2) {
                int end = Math.min(count, spreadCursor + 64);
                Arrays.fill(spreadChange, spreadCursor, end, 0);
                spreadCursor = end;
                if (end == count) { spreadPhase = 3; spreadCursor = 0; }
            } else if (spreadPhase == 3) {
                int end = Math.min(spreadPairs, spreadCursor + 64);
                for (; spreadCursor < end; spreadCursor++) {
                    int a = linked[spreadCursor * 2], b = linked[spreadCursor * 2 + 1];
                    // Downhill, part of the trade becomes a fall: ink runs from the upper tile to
                    // the lower one whatever the lower holds, so it pools at the wet area's low edge.
                    // Each tile still sends no more than evening out could, which stays stable.
                    float link = links[spreadCursor], slope = downhill[spreadCursor], fall = POOL * Math.abs(slope);
                    float moved = link * (1 - fall) * (spreadInk[b] - spreadInk[a])
                            + link * fall * (slope > 0 ? -spreadInk[a] : spreadInk[b]);
                    spreadChange[a] += moved; spreadChange[b] -= moved;
                }
                if (end == spreadPairs) { spreadPhase = 4; spreadCursor = 0; }
            } else if (spreadPhase == 4) {
                int end = Math.min(count, spreadCursor + 64);
                for (; spreadCursor < end; spreadCursor++)
                    spreadInk[spreadCursor] += spreadChange[spreadCursor] / spreadPixels[spreadCursor];
                if (end == count) { spreadPhase = ++spreadStep == SPREAD_STEPS * step ? 5 : 2; spreadCursor = 0; }
            } else {
                if (spreadCursor < count) for (int end = Math.min(count, spreadCursor + 64); spreadCursor < end; ) {
                    Tile tile = spreading.get(spreadCursor);
                    // A gain goes mostly to fresher water, a loss comes from where the ink is.
                    float change = spreadInk[spreadCursor++] - tile.meanInk;
                    tile.spread = change / Math.max(change > 0 ? DAMP : 1, change > 0 ? tile.meanFresh : tile.meanInk);
                    tile.spreadInk = change;
                    tile.spreadFrame = frame; tile.spreadSlot = -1;
                } else { spreadCandidates = null; return true; }
            }
        } while (System.nanoTime() - start < budgetNanos);
        return false;
    }
    /**
     * Loads this frame's planned spread of a tile and its eight neighbors, so {@link #spreadAt}
     * can blend them: applied to whole tiles, neighboring tiles' different changes leave
     * square steps where ink runs quickly. A neighbor without a plan takes the tile's own.
     * False when nothing nearby spreads.
     */
    private boolean gatherSpread(Tile tile, float[] spreadNear) { return gatherSpread(tile, spreadNear, PLAN); }
    /** What {@link #gatherSpread} gathers from each planned tile: its {@link Tile#spread}, {@link Tile#spreadInk}, mean freshness or mean ink. */
    private static final int PLAN = 0, PLAN_INK = 1, FRESH = 2, INK = 3;
    private static float planned(Tile tile, int what) {
        return what == PLAN ? tile.spread : what == PLAN_INK ? tile.spreadInk : what == FRESH ? tile.meanFresh : tile.meanInk;
    }
    private boolean gatherSpread(Tile tile, float[] spreadNear, int what) {
        float own = tile.spreadFrame != frame ? 0 : planned(tile, what);
        boolean any = own != 0;
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            int x = tile.x + dx * SIDE, y = tile.y + dy * SIDE;
            Tile other = x >= 0 && y >= 0 && x < document.width && y < document.height ? tiles[y / SIDE * columns + x / SIDE] : null;
            // Plain water's plan fades out toward flowing water too damp to spread, instead of
            // stopping at the tile edge.
            float value = other != null && other.spreadFrame == frame ? planned(other, what)
                    : what == PLAN_INK && other != null && !other.flowing.isEmpty() ? 0 : own;
            spreadNear[(dy + 1) * 3 + dx + 1] = value;
            any |= value != 0;
        }
        return any;
    }
    /**
     * Shares out the tile-scale spread among a tile's flowing pixels, into {@link Scratch#spread}:
     * a gain mostly to fresher water, a loss from where the ink is, each pixel's share blended
     * from its tile's plan and its neighbors' (see {@link #gatherSpread}) so ink fades across
     * tile edges instead of stepping. False when nothing nearby spreads.
     * <p>
     * Blending each plan's share per unit of freshness (a gain) or of ink (a loss) mixes the
     * two where a gaining tile meets a losing one, and so makes ink, darkening what lifted
     * paint runs into. Watercolor and the ink-holding wet brush pen were tuned with that extra
     * ink, so only {@link Tile#plain} water blends the plans as ink instead, and its shares
     * then add up to its tile's own plan: it thins lifted paint into the water like the first
     * wet brush pen, without that pen's square steps.
     */
    private boolean shapeSpread(Tile tile, Scratch scratch) {
        float[] near = scratch.near, spread = scratch.spread;
        if (!gatherSpread(tile, near)) return false;
        for (int i = tile.flowing.nextSetBit(0); i >= 0; i = tile.flowing.nextSetBit(i + 1)) {
            float blended = spreadAt(i, near);
            spread[i] = blended * (blended > 0 ? tile.pull[i] : tile.pigment[i]);
        }
        if (tile.plain == null || tile.plain.isEmpty()) return true;
        // Plain water in a tile too damp to spread takes no share, as in the first wet brush pen.
        if (tile.spreadFrame != frame) {
            for (int i = tile.plain.nextSetBit(0); i >= 0; i = tile.plain.nextSetBit(i + 1)) spread[i] = 0;
            return true;
        }
        // The plans as ink, and the means they are shared out by, all blended between tile
        // centers: anything taken per tile would step at tile edges.
        float[] ink = scratch.nearInk, fresh = scratch.nearFresh, inky = scratch.nearInky;
        gatherSpread(tile, ink, PLAN_INK); gatherSpread(tile, fresh, FRESH); gatherSpread(tile, inky, INK);
        // Blending still leaves soft lumps a tile apart where neighboring plans differ, as along
        // a diagonal stroke. Each frame moves the blend's centers, so they never build up in one place.
        float shiftX = (frame * .618034f) % 1 - .5f, shiftY = (frame * .754878f) % 1 - .5f;
        for (int i = tile.plain.nextSetBit(0); i >= 0; i = tile.plain.nextSetBit(i + 1)) {
            float change = spreadAt(i, ink, shiftX, shiftY);
            spread[i] = tile.pull[i] <= STILL ? 0 : change > 0 ? change * tile.pull[i] / Math.max(DAMP, spreadAt(i, fresh, shiftX, shiftY))
                    : Math.max(-tile.pigment[i], change * tile.pigment[i] / Math.max(1, spreadAt(i, inky, shiftX, shiftY)));
        }
        return true;
    }
    /** The planned spread at a pixel of the gathered tile, blended between tile centers. */
    private static float spreadAt(int index, float[] spreadNear) { return spreadAt(index, spreadNear, 0, 0); }
    /** {@link #spreadAt(int, float[])} with the tile centers moved by up to half a tile, {@code shiftX, shiftY} from -.5 to .5. */
    private static float spreadAt(int index, float[] spreadNear, float shiftX, float shiftY) {
        float u = (index % SIDE + .5f) / SIDE - .5f + shiftX, v = (index / SIDE + .5f) / SIDE - .5f + shiftY;
        int sx = u < 0 ? 0 : 2, sy = v < 0 ? 0 : 6;
        float a = Math.abs(u), b = Math.abs(v);
        return (1 - a) * (1 - b) * spreadNear[4] + a * (1 - b) * spreadNear[3 + sx]
                + (1 - a) * b * spreadNear[sy + 1] + a * b * spreadNear[sy + sx];
    }
    /** Updates the mean ink, mean freshness and count of a tile's flowing pixels, and which of them are along its edges. */
    private static void measure(Tile tile) {
        float ink = 0, fresh = 0; int count = 0, left = 0, right = 0, top = 0, bottom = 0;
        for (int i = tile.flowing.nextSetBit(0); i >= 0; i = tile.flowing.nextSetBit(i + 1)) {
            ink += tile.pigment[i]; fresh += tile.pull[i]; count++;
            int row = i / SIDE, col = i % SIDE;
            if (col == 0) left |= 1 << row; else if (col == SIDE - 1) right |= 1 << row;
            if (row == 0) top |= 1 << col; else if (row == SIDE - 1) bottom |= 1 << col;
        }
        tile.meanInk = ink / count; tile.meanFresh = fresh / count; tile.flowingPixels = count;
        tile.flowLeft = left; tile.flowRight = right; tile.flowTop = top; tile.flowBottom = bottom;
    }
    // Each pixel samples its wet neighbors 1, 4 and 8 px away in each direction.
    // A ray ends at the first dry pixel, so water never jumps a gap.

    /** Pixels from {@code index} to its tile's edge in direction dx,dy, counting itself. */
    private static int edge(int index, int dx, int dy) {
        return dx < 0 ? index % SIDE + 1 : dx > 0 ? SIDE - index % SIDE : dy < 0 ? index / SIDE + 1 : SIDE - index / SIDE;
    }
    /** The tile a ray enters within its 8 px reach, or null. */
    private Tile beside(Tile tile, int edge, int dx, int dy) {
        if (edge > 8) return null;
        int x = tile.x + dx * SIDE, y = tile.y + dy * SIDE;
        return x >= 0 && y >= 0 && x < document.width && y < document.height ? tiles[y / SIDE * columns + x / SIDE] : null;
    }
    /** Sum of the three wet samples' pigment along a ray; a sample cut off by dry paper reads {@code fallback}. */
    private float neighbor(Tile tile, int index, int dx, int dy, float fallback) {
        int edge = edge(index, dx, dy), step = dx + dy * SIDE, wrap = dx * SIDE + dy * PIXELS;
        Tile next = beside(tile, edge, dx, dy);
        int reach = reach(tile, next, index, dx, dy, edge);
        float close = reach < 1 ? fallback : edge > 1 ? tile.pigment[index + step] : next.pigment[index + step - wrap];
        float near = reach < 4 ? fallback : edge > 4 ? tile.pigment[index + 4 * step] : next.pigment[index + 4 * step - wrap];
        float far = reach < 8 ? fallback : edge > 8 ? tile.pigment[index + 8 * step] : next.pigment[index + 8 * step - wrap];
        return close + near + far;
    }
    /** Pigment flowing water gains along a ray, trading with each wet sample at the mobility of the less fresh of the two. */
    private float exchange(Tile tile, int index, int dx, int dy, float value, float fresh) {
        int edge = edge(index, dx, dy), step = dx + dy * SIDE, wrap = dx * SIDE + dy * PIXELS;
        Tile next = beside(tile, edge, dx, dy);
        int reach = reach(tile, next, index, dx, dy, edge);
        float gained = 0;
        if (reach >= 1) gained += edge > 1 ? trade(tile, index + step, value, fresh) : trade(next, index + step - wrap, value, fresh);
        if (reach >= 4) gained += edge > 4 ? trade(tile, index + 4 * step, value, fresh) : trade(next, index + 4 * step - wrap, value, fresh);
        if (reach >= 8) gained += edge > 8 ? trade(tile, index + 8 * step, value, fresh) : trade(next, index + 8 * step - wrap, value, fresh);
        return gained;
    }
    /** Only water trades with flowing water; other wet paint holds its pigment. The trade is symmetric, so ink is conserved. */
    private static float trade(Tile source, int index, float value, float fresh) {
        // Pixels that are not water have no mobility.
        return source.mobility == null ? 0 : Math.min(fresh, source.mobility[index]) * (source.pigment[index] - value);
    }
    /** Paint over water makes it ordinary wet paint again. */
    private static void clearWater(Tile tile, int index) {
        tile.water.clear(index); tile.flowing.clear(index);
        if (tile.plain != null) tile.plain.clear(index);
        if (tile.mobility != null) tile.mobility[index] = 0;
    }
    /** How many pixels in a row are wet beyond {@code index} along the ray, up to the 8 it samples. */
    private int reach(Tile tile, Tile next, int index, int dx, int dy, int edge) {
        // Fully hydrated tiles have no dry gaps to inspect.
        if (tile.wetPixels == PIXELS && (edge > 8 || next != null && next.wetPixels == PIXELS)) return 8;
        int position = dx != 0 ? index % SIDE : index / SIDE;
        int line = dx != 0 ? index / SIDE : index % SIDE;
        int bits = dx != 0 ? tile.wetRows[line] : tile.wetColumns[line];
        int nextBits = next == null ? 0 : dx != 0 ? next.wetRows[line] : next.wetColumns[line];
        if (dx + dy < 0) {
            bits = Integer.reverse(bits) >>> (32 - SIDE);
            nextBits = Integer.reverse(nextBits) >>> (32 - SIDE);
            position = SIDE - 1 - position;
        }
        int connected = bits >>> (position + 1);
        if (edge <= 8) connected |= nextBits << (edge - 1);
        // The first dry bit cuts the ray off, including across tile boundaries.
        // Water still cannot jump a gap; no per-pixel coordinate walk is needed.
        return Integer.numberOfTrailingZeros(~connected);
    }
    private void composite(Tile tile, int index, int x, int y) {
        document.setTone(x, y, shade(tile, index));
        // The model owns this pixel's tone again; any preview over it is gone.
        if (tile.held != null) tile.held.clear(index);
    }
    /** The tone a pixel's paint shows. */
    private static int shade(Tile tile, int index) {
        // Legacy washes retain a dry base. Canvas mode imports old paint as pigment
        // over white, so it participates in the same diffusion as each new stroke.
        // Stuck paint and the loose ink over it add up, so freeing one into the other keeps the shade.
        float ink = tile.pigment[index] + (tile.stuck == null ? 0 : tile.stuck[index]);
        int tone = Math.round((tile.base[index] & 255) * (1 - Math.min(255, ink) / 255f));
        return Math.max(0, Math.min(255, tone));
    }
    /** How far a pixel's rays sample, and the row length of the {@link Scratch} layout, which reaches that far beyond the tile each way. */
    private static final int REACH = 8, SPAN = SIDE + 2 * REACH;
    /** Per-thread working space for {@link #computeTile}. */
    private static final class Scratch {
        /** Each flowing pixel's share of its tile's spread this frame. */
        final float[] spread = new float[PIXELS];
        /** Neighboring tiles' planned spread as ink, mean freshness and mean ink, for {@link #shapeSpread}. */
        final float[] nearInk = new float[9], nearFresh = new float[9], nearInky = new float[9];
        final float[] near = new float[9], ink = new float[SPAN * SPAN], free = new float[SPAN * SPAN];
    }
    private static final class Tile {
        final int x, y;
        final byte[] base = new byte[PIXELS];
        final float[] pigment = new float[PIXELS], target = new float[PIXELS], next = new float[PIXELS];
        /** After a frame, each pixel's new tone, or -1 where it did not change, worked out alongside its next pigment. */
        final int[] shade = new int[PIXELS];
        final BitSet wet = new BitSet(PIXELS), touched = new BitSet(PIXELS), water = new BitSet(PIXELS), flowing = new BitSet(PIXELS);
        final int[] wetRows = new int[SIDE], wetColumns = new int[SIDE];
        /** Brush pen pull of each water pixel, or freshness of flowing water; allocated once a pen reaches this tile. */
        float[] pull;
        /** Cached cubic freshness, shared by the twelve exchanges per flowing pixel. */
        float[] mobility;
        /** Flowing water laid without ink, by the wet brush pen holding none; see {@link #shapeSpread}. Allocated when there is some. */
        BitSet plain;
        /** Lifted paint not yet dissolved into flowing water, allocated when there is some. */
        float[] reserve;
        /** Dry paint, out of 255, held stuck beneath a watercolor brush's water until it soaks loose, and how soaked it is, 0 to 1; allocated when there is some. */
        float[] stuck, soaked;
        /** Pixels queued dabs have glazed this stroke; and those whose tone beneath a preview is held in {@link #under}. */
        BitSet previewed, held;
        byte[] under;
        int frames, wetPixels, flowingPixels;
        /** Whether any of the tile's flowing water was still {@link #LOOSE} after its last frame. */
        boolean loose;
        /** Frames until this tile's freshest flowing water rests, as of its last frame. */
        int dampFrames;
        float meanInk, meanFresh;
        /** Flowing pixels along each edge as of the last {@link #measure}, a bit for each row or column. */
        int flowLeft, flowRight, flowTop, flowBottom;
        /** Planned change to this tile's mean flowing ink, per unit of freshness (a gain) or of ink (a loss), during {@link #spreadFrame}. */
        float spread;
        /** The same planned change, as ink per flowing pixel. */
        float spreadInk;
        int spreadSlot = -1, spreadFrame = -1;

        Tile(int x, int y) { this.x = x; this.y = y; }
    }
    /** A queued {@link #waterMask} call; its mask holds only coverage. */
    private static final class Dab {
        final byte[] alpha, hits;
        final int x, y, w, h;
        final float pull, carry;
        final boolean flowing;
        final BrushInk ink;
        Dab(byte[] alpha, byte[] hits, int x, int y, int w, int h, float pull, boolean flowing, float carry, BrushInk ink) {
            this.alpha = alpha; this.hits = hits; this.x = x; this.y = y; this.w = w; this.h = h;
            this.pull = pull; this.flowing = flowing; this.carry = carry; this.ink = ink;
        }
    }
}
