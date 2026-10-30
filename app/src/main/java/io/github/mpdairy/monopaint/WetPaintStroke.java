package io.github.mpdairy.monopaint;

/**
 * A Wet brush's stroke (the wet paint spec, docs/archive/superpowers/specs/2026-10-22-wet-paint-design.md; the owner's Option A,
 * 2026-10-22): MonoPaint's brush on a wet canvas in the six-part engine. Its footprint along the pen (the stampers' own
 * geometry, {@link FootprintSweep}) is laid into the pair's {@link WetCanvas} each time the pen's samples are presented:
 * every pixel the footprint newly reaches takes the paint once, final under the pen (no preview, no render after:
 * MonoPaint's "first write is final", the main reason it feels quick on e-ink), mixing into the wet paint under it by the
 * canvas's wetness ({@link WetWatercolor#paintMask}), which then evens out with its wet neighbours and runs downhill
 * while the canvas moves after the lift.
 *
 * <ul>
 * <li>Opaque: the swatch's grey at the medium's Opacity ({@link ResolvedBrush#gray}, {@link ResolvedBrush#cover}).
 * Each stroke starts dipped in it again (MonoPaint's {@code brushInk.load}).</li>
 * <li>With Drag or Smear the brush picks up what it crosses and gets dirtier along the stroke (MonoPaint's
 * {@link PaintLoad}, its pick-up): with Smudge's Paint Load its own paint runs out as {@link LoadedPaint} spends it, so it
 * lays more of what it picked up and at last only smears (MonoPaint's oil paint mode); without, it keeps a share of its
 * own by its Pick Up Paint. What Paint Load leaves starts the holder's next stroke, as every Paint Load brush's does.</li>
 * <li>Ink or Wash (the wet brush spec, 2026-10-22): MonoPaint 0.98.3's wet brush pen, ink in flowing water ({@link
 * BrushInk}, untinted) that the brush holds between strokes: where it touches wet paint it trades ink toward the same
 * shade (a clean brush soaks ink up, an inky one lets it out), it lifts dry paint at once, and its ink runs through the
 * water and pools downhill. A tap on a grey dips it ({@link HeldPaint#dip}).</li>
 * <li>With Clear Water it lays flowing clear water ({@link WetWatercolor#waterMask}) from a brush rinsed clean at each
 * stroke's start ({@link BrushInk}): it lifts wet or dry paint, soaking some up and letting the rest run through the water
 * (MonoPaint's wet brush pen, its Pick Up Paint the pen's strength).</li>
 * </ul>
 * Not yet: the paper's tooth and Dry Brush gaps (the paint is laid solid, as MonoPaint's), Impasto, Gradient along the
 * stroke, Flick. One Undo step, with the canvas's movement after it. Pure, checked on the desktop ({@code WetPaintChecks}).
 */
final class WetPaintStroke implements DrawingStroke {
    private final ToneDocument document;
    private final WatercolorSession session;
    private final ResolvedBrush r;
    private final WetCanvas canvas;
    private final StrokePoints pts = new StrokePoints();
    private final WetCanvas.Footprint sweep;
    /**
     * A wet wash's pen line (Pen Line past 0, the ink wash note of 2026-10-22: Line and wash, Wet ink): the line's own
     * footprint, laid as dry ink beneath the wash's water ({@link WetWatercolor#lineMask}), so it stays crisp while the
     * wash merges and dries back around it; null for every other Wet brush.
     */
    private final WetCanvas.Footprint lineSweep;
    /** A pen line laid with the wash in its pass instead ({@link WetWatercolor#washMask}), its dry ink out of 255; 0 for none. */
    private final float lineInWash;
    /** The least distance between two shapes swept, px: a 24th of the stroke's reach, at least 2 (as the quick preview's). */
    private final double step;
    /** The last sample swept (−1 for none). */
    private int swept = -1;
    /** Paint Load ({@link LoadedPaint.Run}) and its holder; null for a brush that never runs out. */
    private final LoadedPaint.Run paint;
    private final String loadedBy;
    /** Paint Load: what the brush picks up and lays as it runs out (MonoPaint's {@link PaintLoad}); null without it. */
    private final PaintLoad load;
    /**
     * Drag or Smear without Paint Load: the share of its own paint the brush keeps (1 − 0.3 × Pick Up Paint), the rest
     * the paint it picked up ({@link #dirt}): it gets dirty, never empty, and blank paper doesn't lighten it.
     */
    private final float own;
    /** The paint picked up, a tone (−1 before any), as {@link PaintLoad}'s pick-up trades it over 40 px of travel, from paint only. */
    private float dirt = -1;
    private final boolean dirties;
    private final boolean water;
    /** Clear Water: the brush's water, clean at each stroke's start (MonoPaint's wet brush pen rinsed): it soaks up what it lifts. */
    private final BrushInk rinsed;
    /**
     * Ink or Wash (the wet brush spec, {@link ResolvedBrush.Wet#inks}): the ink the brush holds in its water (MonoPaint's
     * wet brush pen's {@link BrushInk}, untinted), as its holder's last stroke left it, or dipped in its grey after a tap on
     * a grey ({@link HeldPaint#dip}); null for every other Wet brush. Its holder keeps what is left.
     */
    private final BrushInk held;
    private final String inkedBy;
    private final int gray;
    private boolean finished, changed;

    /**
     * Starts a stroke of {@code brush} (resolved as {@code r}, a Wet one) on the selected pair's half for it, its canvas
     * there from {@code session} (what else changed the page since taken up first).
     */
    WetPaintStroke(ToneDocument document, ModularBrush brush, ResolvedBrush r, WatercolorSession session) {
        this.document = document; this.session = session; this.r = r;
        canvas = session.wetCanvas(document, ModularStroke.layerOf(document, brush));
        ModularStroke.selectLayer(document, brush);
        canvas.brush(r);
        water = r.carry != null && r.carry.water();
        rinsed = water ? new BrushInk() : null;
        gray = (int) Math.round(r.gray);
        inkedBy = r.wet.inks ? session.holder() : null;
        held = r.wet.inks ? new BrushInk(false, inkCapacity(ModularStroke.reach(r))) : null;
        if (held != null) {
            float fullness = session.held.wetInk(inkedBy);
            if (Float.isNaN(fullness)) held.load(gray); else held.restore(fullness, 0);
        }
        step = Math.max(2, ModularStroke.reach(r) / 24);
        sweep = new WetCanvas.Footprint(r, pts, document.width, document.height, r.body == null, new StrokePaper(r.paper));
        // A pen line as wide as its wash (a broad one, its wash held to it: ResolvedBrush.wetBody) is laid with the wash, in
        // its one pass; a narrower one has its own footprint.
        boolean whole = lines(r) && r.bodySize <= r.inkSize;
        lineSweep = lines(r) && !whole ? new WetCanvas.Footprint(r, pts, document.width, document.height, true, new StrokePaper(r.paper)) : null;
        lineInWash = whole ? (float) ((255 - gray) * StrokeMath.clamp(r.parts.inkShare)) : 0;
        loadedBy = r.loaded != null ? session.holder() : null;
        paint = r.loaded != null ? new LoadedPaint.Run(r, r.loaded.startShare(session.held.load(loadedBy))) : null;
        boolean drags = !water && held == null && r.carry != null && r.carry.moves;
        load = drags && paint != null ? new PaintLoad(document, gray, Float.MAX_VALUE) : null;
        dirties = drags && paint == null;
        own = dirties ? (float) (1 - .3 * r.carry.pick) : 1;
        // Inkwash: on the wet paint's plane (WetCanvas#kinds).
        document.begin(r.erase, canvas.kinds(r));
        canvas.wet.beginStroke();
    }

    @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY) { sample(x, y, pressure, tiltX, tiltY, PenSpeed.UNTIMED); }
    @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY, long time) {
        if (finished || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(pressure)) return;
        if (pts.add(x, y, ToolSettings.travel(pressure), time, tiltX, tiltY) && paint != null) paint.spend(pts);
    }
    @Override public void zoom(float scale) { pts.zoom = scale; }
    /** Lays the footprint swept through the samples since the last present, every {@link #step} px and to the newest. */
    @Override public void present() {
        int newest = pts.count - 1;
        if (finished || newest <= swept) return;
        long t0 = Prof.now();
        for (int k = swept + 1; k <= newest; k++) {
            // A hake waits for the stroke's direction before it lays its start (FootprintSweep#holds).
            if (sweep.holds()) break;
            if (k < newest && swept >= 0 && pts.d[k] - pts.d[swept] < step) continue;
            float travelled = swept < 0 ? 0 : pts.d[k] - pts.d[swept];
            sweep.to(k);
            int at = k;
            // A wet wash's pen line first, beneath where the wash then lays (one write a pixel where it reaches both at once).
            if (lineSweep != null) {
                lineSweep.to(k);
                lineSweep.take((mask, x, y, w, h) -> canvas.wet.lineMask(mask, w, x, y, w, h, gray, (float) r.parts.inkShare, lineSweep.rows));
            }
            sweep.take((mask, x, y, w, h) -> lay(mask, x, y, w, h, at, travelled));
            swept = k;
        }
        Prof.add(Prof.QUICK, t0);
    }
    /** Lays {@code mask}'s pixels (new to the footprint) at sample {@code k}, {@code travelled} px past the last. */
    private void lay(int[] mask, int x, int y, int w, int h, int k, float travelled) {
        WetWatercolor wet = canvas.wet;
        if (water) { wet.waterMask(mask, w, x, y, w, h, (float) (.5 + .5 * StrokeMath.clamp(r.carry.pick)), true, 0, rinsed); return; }
        // Wet watercolour (section 9): a transparent wash, fresh water that glazes its grey over what is there.
        if (r.wet.wash != null) { wet.washMask(mask, w, x, y, w, h, gray, washStrength(r, pts.p[k]), r.wet.washPull, sweep.rows, lineInWash); return; }
        // Ink in water (MonoPaint's wet brush pen at its strength, 60): each pixel the footprint reaches trades as much
        // ink as the pen's overlapping dabs would, laid once.
        if (held != null) { wet.waterMask(mask, w, x, y, w, h, INK_PULL, held, inkTrade(pts.p[k])); return; }
        if (dirties) { wet.paintMask(mask, w, x, y, w, h, dirty(mask, x, y, w, h, travelled), false, (float) r.cover); return; }
        if (load == null) { wet.paintMask(mask, w, x, y, w, h, gray, false, (float) r.cover); return; }
        // Paint Load: what it covers (as the stroke found it) mixes into what it carries; its own paint runs out, so it
        // lays more of the mix, less of it, and at last only smears (MonoPaint's oil paint mode).
        load.travel(travelled);
        load.pickUp(mask, w, x, y, w, h);
        load.left(pts.own[k]);
        wet.paintMask(mask, w, x, y, w, h, load.tone(), false, (float) (r.cover * load.deposit()));
    }
    /**
     * Drag or Smear without Paint Load: the paint under {@code mask} (as the stroke found it; blank paper isn't paint) mixed
     * into what the brush picked up, over 40 px of travel as {@link PaintLoad}'s pick-up; returns the tone it lays, its own
     * grey with {@link #own} of it kept.
     */
    private int dirty(int[] mask, int x, int y, int w, int h, float travelled) {
        float under = 0; int count = 0;
        // Every other pixel each way: the mean of what it covers, at a quarter of the reads (a broad brush's every sample).
        for (int row = 0; row < h; row += 2) for (int col = 0; col < w; col += 2) {
            int px = x + col, py = y + row;
            if (mask[row * w + col] == 0 || px < 0 || py < 0 || px >= document.width || py >= document.height) continue;
            int tone = document.strokeBaseTone(px, py);
            if (tone < PAPER) { under += tone; count++; }
        }
        if (count > 0) {
            under /= count;
            dirt = dirt < 0 ? under : dirt + (under - dirt) * (1 - (float) Math.exp(-travelled / PICKUP_PX));
        }
        return dirt < 0 ? gray : Math.round(dirt + (gray - dirt) * own);
    }
    /**
     * How much of its grey a wet watercolour wash lays over fresh paper at pen travel {@code travel} (0 to 1): {@link
     * #WASH_BODY} from a usual pressure up, about half of it at the lightest (a loaded brush pressed lays more pigment),
     * lighter still at a light touch as far as the hand's Press Dark asks; so a wash at black lays near black.
     */
    static float washStrength(ResolvedBrush r, float travel) {
        float t = Math.max(0, Math.min(1, travel));
        return (float) (WASH_BODY * body(r) * Math.min(1, .55 + .65 * t) * (1 - .7 * StrokeMath.clamp(r.pen.pDark) * (1 - t)));
    }
    /**
     * The share of its grey a wet wash's body lays at full strength: a Wash's all of it; an Ink's (an Ink wash ink wash)
     * the glaze an ink's body lays ({@link ModularSizes#GLAZE}, a third), as much as it has a body ({@link
     * ResolvedBrush.Blend#bodyAmt}): the pale wash about its line that the old one laid.
     */
    static double body(ResolvedBrush r) { return (r.lineInk ? ModularSizes.GLAZE : 1) * r.parts.bodyAmt; }
    /** Whether a stroke of {@code r} lays a pen line with its wet wash ({@link #lineSweep}): its Pen Line past 0. */
    static boolean lines(ResolvedBrush r) { return r.wet.wash != null && r.wet.line && r.line; }
    /** A wash's body: about the share of the swatch's grey a Slow wash's body lays (Soft wash's, by eye and mean tone). */
    static final float WASH_BODY = 1f;
    /** Lighter than this is blank paper, not paint, to a dirty brush; and the travel over which what it picks up trades for what it covers (MonoPaint's PaintLoad). */
    private static final int PAPER = 250;
    private static final float PICKUP_PX = 40;
    /** The wet brush pen's water (MonoPaint's strength 60: fresh enough to run, not so fresh it evens out every dark). */
    static final float INK_PULL = .6f;
    /**
     * How much of the brush's shade a pixel of fresh paper takes, at the pen's lightest and at its full pressure (the
     * owner, 2026-10-22: "we can have a wider range, darker"): from a pale wash to near black for a full black dip.
     * MonoPaint's pen lays about .4 of it (its dabs, spaced .4 of a radius, rub a pixel about 4 times, each trading
     * {@link BrushInk}'s .2 of the difference at the pull); here the pressure sets it, the trade as MonoPaint's.
     */
    static final float INK_LIGHT = .2f, INK_FULL = .92f;
    /**
     * How many dabs' worth of ink one pixel trades at pen travel {@code travel} (0 to 1, {@link ToolSettings#travel}),
     * laid once: as much as lays {@link #INK_LIGHT} to {@link #INK_FULL} of the brush's shade on fresh paper, the
     * curve squared so a light hand stays light.
     */
    static float inkTrade(float travel) {
        float t = Math.max(0, Math.min(1, travel));
        return (INK_LIGHT + (INK_FULL - INK_LIGHT) * t * t) / (.2f * INK_PULL);
    }
    /** MonoPaint's wet brush pen's own size (its default, 48 px): up to it a brush holds {@link BrushInk#CAPACITY}. */
    static final double INK_PX = 48;
    /**
     * A full brush holds this many times MonoPaint's pen's ink: it lays up to {@link #INK_FULL} of its shade where the
     * pen lays about .4, so with this much more a dark stroke still runs dry over about as long as the pen's paler one.
     */
    static final float INK_LOAD = 4f;
    /**
     * How much ink a wet brush {@code reach} px across holds: {@link #INK_LOAD} times MonoPaint's {@link
     * BrushInk#CAPACITY} up to its pen's size, by its area beyond, so a broad brush runs dry over as many of its widths
     * as the pen does over its own.
     */
    static float inkCapacity(double reach) { double k = Math.max(1, reach / INK_PX); return (float) (INK_LOAD * BrushInk.CAPACITY * k * k); }
    @Override public boolean finish() {
        if (finished) return changed;
        sweep.lifted = true;
        if (lineSweep != null) lineSweep.lifted = true;
        present();
        finished = true;
        changed = canvas.wet.finishStroke();
        canvas.seen();
        // Paint Load: what is left starts the holder's next stroke, if more than the Starting Load it would have anyway.
        if (loadedBy != null) session.held.keepLoad(loadedBy, paint.left() > r.loaded.start ? paint.left() : 0);
        // Ink in water: the brush keeps the ink it soaked up or has left for its holder's next stroke.
        if (inkedBy != null) session.held.keepWetInk(inkedBy, held.fullness());
        session.wetStroked();
        return changed;
    }
    /** The canvas holds still while the pen is down and moves once it lifts. */
    @Override public boolean holdsWetPaint() { return true; }
}
