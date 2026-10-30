package io.github.mpdairy.monopaint;

import java.util.function.Consumer;

/**
 * A six-part brush's Drying wash still settling after lift ({@link DryingWater}), one at a time: a new stroke, a
 * layer change, a pause or another page lays what is still suspended where it is ({@link #settleNow}, which must come
 * before {@link #dry}), and anything else changing the page (undo) stops it where it is. (It held the old
 * watercolor's wet washes too, until every brush became six-part.) Its {@link #pace} paces the settling, after lift
 * and a Fast-wet stroke's under the pen.
 * <p>
 * Beside it, a Float bath still floating ({@link FloatWater}, the Float spec), on the layer of the stroke that started
 * it: a Float stroke on that layer joins it, and anything else that would change the page prints it first
 * ({@link #settleNow}): a stroke that doesn't float or floats on another layer, a layer change, a pause, another page.
 * A slot, shade or size change doesn't. While it floats, Undo takes back its strokes one by
 * one ({@link #undo}). One of the two at most is wet at a time: each lays or prints the other before it begins.
 * <p>
 * Beside them, Halo strokes' veins still growing ({@link HaloGrowth}, the new modules spec), on the layer they were
 * drawn on: a Halo stroke there joins them (its mark is food for theirs, and theirs for its), and anything else sets
 * them where they are ({@link #settleNow}): any other stroke, the eraser, Undo, a layer change, a pause, another page.
 * A slot, shade or size change doesn't. They grow a tick at a time together, each built and
 * stepped on the {@link #worker} (a step reads nothing on the page, so it may run while a Halo stroke is drawn) and
 * written here, one undo step with the last stroke. Only one of the three kinds is wet at a time.
 * <p>
 * With a {@link #worker} (the drawing screen, Brush Lab), their work runs there ({@link #tick}): this thread starts a
 * tick and writes what it laid when it is done. What laying the wash or printing the bath now would write is worked out
 * there too, ahead, for the page as it shows: after the lift (or a Float stroke, or an Undo of one), and with each tick
 * for the page as the tick leaves it. A new stroke or a layer change ({@link #settleNow})
 * writes that and lets the tick running there go, so it never waits for a tick. It waits only for the work ahead
 * itself just after the lift, and for a tick of a wash gravity has moved, whose laying can't be worked out ahead
 * without copying its flow (then the tick is written and the wash laid here). Without a worker (the desktop, samples)
 * everything runs here, as {@link #tickDrying}.
 * <p>
 * Beside them, wet paint ({@link WetCanvas}, the wet paint spec): MonoPaint's wet canvas on the layer Wet strokes
 * ({@link WetPaintStroke}) lay into, one at a time. Unlike the others it stays wet across every other stroke (the owner,
 * 2026-10-22: "dries only on the droplet tap or leaving the page"): only the wet indicator's tap ({@link #hurry}) and
 * {@link #settleNow} (the page put away, a layer or pair change) dry it, so {@link #settleBefore} leaves it, and Undo
 * only lets it rest. It moves (on this thread, MonoPaint's slices) only while none of the others is wet, after them.
 * <p>
 * The wet indicator's tap ({@link #hurry}) doesn't lay them where they are: each runs on to the end it would reach left
 * alone, its ticks back to back with no pace, then the page is shown once.
 */
final class WatercolorSession {
    /** The Drying wash still settling, or null. */
    private DryingWater drying;
    /** The Float bath still floating, or null. */
    private FloatWater floating;
    /** Halos still growing, in the order their strokes were laid, all on one layer; empty for none. */
    private final java.util.List<HaloGrowth> halos = new java.util.ArrayList<>();
    /**
     * Halo strokes' lifts whose halos are still to be built, in order ({@link #holdHalo}): the first one's is built on the
     * worker, then the next; the halos grow no tick meanwhile.
     */
    private final java.util.List<HaloGrowth.Lift> lifts = new java.util.ArrayList<>();
    /** The page's revision as the halos last left it (after a Halo stroke joined them, or a tick was written): anything else that changes it sets them. */
    private long haloRevision;
    /** Where it is reported: Logcat on the device, nowhere on the desktop. */
    Consumer<String> log = message -> { };
    /** When settling runs and shows ({@link SettlePacer}); null: unpaced (the desktop, samples). */
    SettlePacer pace;
    /** Told whenever whether anything is still wet ({@link #wet}) may have changed: the wet indicator. */
    Runnable wetChanged = () -> { };
    /** The page's tilt (Gravity): the accelerometer on the device ({@code TiltFeed}); level unless set. */
    PageTilt.Source tilt = () -> PageTilt.LEVEL;
    /** Told whenever {@link #wantsTilt} may have changed: the sensor listens only while it does. */
    Runnable tiltChanged = () -> { };
    /** A stroke that reads the page's tilt is down ({@link #strokeReadsTilt}). */
    private boolean tiltStroke;
    /** A quick stroke's final render is still running after lift ({@link #developing}). */
    private boolean developing;
    /** Where the wet paint's work runs ({@link SettleWorker}); null: on this thread, as it ticks. */
    SettleWorker worker;
    /** What the worker is doing: laying the wash now or printing the bath now worked out ahead ({@link #ahead}), or a tick. */
    private enum Work { LAY, PRINT, TICK, GROW, HALO }
    /** The job running there (or done, not yet taken in), what it is and what it works on (the drying wash or the bath); null when none. */
    private SettleWorker.Job<?> job;
    private Work working;
    private Object jobOn;
    /** What laying the wash now ({@link DryingWater.Laid}) or printing the bath now ({@link FloatWater.Shown}) writes, for the page as it shows; null until worked out. */
    private Object ahead;
    /** The wet indicator's tap ({@link #hurry}): what is wet runs on unpaced, shown once at its end; since when (ns). */
    private boolean hurrying;
    private long hurried;
    /**
     * The longest a tap's run may take, ms; what is still wet then is laid where it is ({@link #settleNow}).
     * shortcut: a Slow wash ticks its whole drying time one tick after another (`ModularPerfBench dryer` on a Mac: a
     * page-wide Watercolor at 240 px 0.16 s for its 2.7 s left, Drips' gravity 1.2 to 2.9 s for its 25 s; the Manta some
     * 10 to 20 times slower), so a page-wide gravity wash on the Manta reaches this and the rest is laid where it is; if
     * that matters, step a wash in bigger ticks while hurrying.
     */
    static long HURRY_MS = 4000;
    /** Whether each tick's work and write are reported ({@link #log}): for the bench. */
    static boolean timing;
    /** The last tick's writes and the finish of its continuation, ns (for {@link #timing}). */
    private long applied, finished;

    /**
     * Keeps Paint (the wet smudge spec): the paint each holder's brush keeps between strokes ({@link HeldPaint}), and
     * whose the next stroke is ({@link #holding}: the drawing screen's tool, the finger's; {@link HeldPaint#ANY} elsewhere).
     */
    final HeldPaint held = new HeldPaint();
    /** The toolbar's swatches, whose greys a Gradient's Cycle on Toolbar steps through ({@link GreyGradient}); null: their first. */
    ToolSwatches swatches;
    private String holder = HeldPaint.ANY;
    /** The next stroke is {@code holder}'s ({@link HeldPaint}). */
    void holding(String holder) { this.holder = holder; }
    String holder() { return holder; }
    /**
     * Clear Water (the wet smudge spec): the box ({left, top, right, bottom}) and layer of the Slow wash last laid
     * while still wet before a stroke ({@link #settleNow}), and the page's revision once laid.
     */
    private int[] wetBox;
    private int wetLayer = -1;
    private long wetRevision;
    /** The box of a wash that was still wet just before this stroke on {@code document}'s active layer (nothing else changed the page since), or null. */
    int[] stillWet(ToneDocument document) {
        return wetBox != null && document != null && document.revision() == wetRevision && document.activeLayer() == wetLayer ? wetBox.clone() : null;
    }
    /** The settling wash laid where it is on {@code document}, valid there: remembered as still wet a moment ago ({@link #stillWet}). */
    private void layWet(ToneDocument document) {
        int[] box = drying.box();
        int layer = drying.layer();
        layNow(document);
        wetBox = box; wetLayer = layer; wetRevision = document.revision();
    }
    /**
     * The brush a stroke of {@code b} draws with on {@code document}: itself, but Clear Water while a bath floats on
     * the selected pair works on the water ({@link ClearWater#afloat}), nudging and combing what floats rather than
     * printing it; a Float stroke then joins the bath ({@link #settleBefore}).
     */
    ModularBrush onWater(ToneDocument document, ModularBrush b) {
        if (b == null || floating == null || document == null || b.choice(BrushPart.CARRY) != PartChoice.CLEAR_WATER
                || b.choice(BrushPart.LIFE) == PartChoice.FLOAT || !floating.valid(document)) return b;
        int pair = InkWashLayers.current(document);
        boolean ink = floating.layer == InkWashLayers.inkOf(document, pair);
        if (!ink && floating.layer != InkWashLayers.washOf(document, pair)) return b;
        return ClearWater.afloat(b, ink);
    }

    /** The wet paint ({@link WetCanvas}), or null when there is none. */
    private WetCanvas canvas;
    /**
     * The most wet paint's tiles may take: a quarter of the heap (128 MB on the Manta's large heap). Past it, before a
     * stroke's next mask, the tiles painted longest ago dry first ({@link WetWatercolor#STROKE_BYTES}).
     */
    static long WET_BYTES = Runtime.getRuntime().maxMemory() / 4;
    /** The wet paint, or null (for the checks). */
    WetCanvas canvas() { return canvas; }
    /**
     * The wet paint a Wet stroke on {@code document}'s layer {@code layer} lays into: the one there, what else changed
     * the page taken up first ({@link WetCanvas#catchUp}), or a new one (any other dried first: it was on another page or
     * layer). Before the stroke's gesture begins.
     */
    WetCanvas wetCanvas(ToneDocument document, int layer) {
        if (canvas != null && canvas.valid(document) && canvas.layer == layer) { canvas.catchUp(false); return canvas; }
        dryCanvas("another layer");
        canvas = new WetCanvas(document, layer);
        return canvas;
    }
    /** A Wet stroke has been laid: the paint moves, and the indicator shows it wet. */
    void wetStroked() { wetChanged.run(); tiltChanged.run(); }
    /** Dries the wet paint: it is the page's as it is; nothing is laid. */
    private void dryCanvas(String reason) {
        if (canvas == null) return;
        log.accept("wet paint dried: " + reason);
        boolean moved = wantsTilt();
        canvas = null;
        wetChanged.run();
        if (moved != wantsTilt()) tiltChanged.run();
    }
    /**
     * Pastels and Dry brush over wet paint (the owner, 2026-10-22: they "stay dry media but react to wet paint"): a stroke
     * of {@code brush}, in one of those styles, on the pair whose wash half holds the wet paint, is drawn on that layer,
     * and its marks join the paint when it ends ({@link #lifted}). Returns the layer it draws on then, or −1 (no wet paint
     * there, or another brush): the brush's own half, as ever.
     */
    int joinsWet(ToneDocument document, ModularBrush brush) {
        joining = null;
        if (canvas == null || !canvas.valid(document) || brush == null || !joins(brush)) return -1;
        if (canvas.layer != InkWashLayers.washOf(document, InkWashLayers.current(document))) return -1;
        joining = brush;
        return canvas.layer;
    }
    /** Whether a brush's strokes join wet paint ({@link #joinsWet}): a Pastels or Dry brush one that lays its own mark there. */
    static boolean joins(ModularBrush brush) {
        String style = brush.style.name;
        PartChoice life = brush.choice(BrushPart.LIFE), m = brush.choice(BrushPart.MEDIUM);
        return ("Pastels".equals(style) || "Dry brush".equals(style)) && life != PartChoice.FLOAT && life != PartChoice.WET_PAINT && m != PartChoice.RESIST && m != PartChoice.TONE;
    }
    /** The brush of the stroke joining the wet paint now ({@link #joinsWet}), or null. */
    private ModularBrush joining;
    /** Inkwash: the kinds a stroke joining the wet paint lays ({@link #joinsWet}): the wet paint's plane ({@link WetCanvas#kinds}); -1 when none joins. */
    int joiningKinds() { return joining == null || canvas == null ? -1 : canvas.kinds(); }
    /**
     * A stroke of {@code r} (its samples {@code pts}) ended on {@code document}: one joining the wet paint has its marks
     * taken up there, and a dry brush's drags the paint along its path ({@link WetCanvas#join}). Any other, nothing.
     */
    void lifted(ToneDocument document, ResolvedBrush r, StrokePoints pts) {
        ModularBrush brush = joining;
        joining = null;
        if (brush == null || canvas == null || !canvas.valid(document)) return;
        canvas.join(r, pts, "Dry brush".equals(brush.style.name));
        wetChanged.run();
    }

    /** Drops the settling wash, unlaid; call {@link #settleNow} first to keep it. (Halos are set by {@link #settleBefore} and {@link #settleNow}.) */
    void dry(String reason) {
        // Nothing settling, nothing to drop: and no print of the bath started ahead here, which a Float stroke about to
        // join it would change under it (a crash on the Manta, 2026-10-22, and a print missing the strokes after it).
        if (drying == null) return;
        log.accept("drying wash dropped: " + reason);
        hold(null);
    }
    /** After a Drying stroke's lift: its wash keeps settling ({@code water}, or null for none). */
    void holdDrying(DryingWater water) { hold(water); }
    boolean settling() { return drying != null || floating != null || haloing() || canvas != null && canvas.moving(); }
    /** Whether something on the page moves now or a quick stroke still renders: {@link #wet} less wet paint at rest. Fading marks wait for it. */
    boolean moving() { return settling() || developing; }
    /** Whether halos are growing (or one is still being built). */
    boolean haloing() { return !halos.isEmpty() || !lifts.isEmpty(); }
    /** The halos growing (for the checks). */
    java.util.List<HaloGrowth> halos() { return java.util.Collections.unmodifiableList(halos); }
    /** The wash still settling, or null (for the checks). */
    DryingWater drying() { return drying; }
    /**
     * Whether anything on the page is still wet: a Drying wash settling after lift, or a quick stroke still rendering
     * its final mark after lift ({@link QuickStroke}). (A Fast-wet stroke's window develops only while the pen is down
     * and is final at lift, so nothing of it is wet after.)
     */
    boolean wet() { return settling() || developing || canvas != null; }
    /** Whether all that is wet is a quick stroke still rendering after lift (no wash settling): the wet indicator shows it only if it lasts. */
    boolean onlyRendering() { return developing && drying == null && floating == null && !haloing() && canvas == null; }
    /**
     * A quick stroke's final render began after lift ({@code on}) or ended ({@link QuickStroke}): the page shows the
     * wet indicator meanwhile. Its host finishes it before anything else changes the page, as a stroke still down.
     */
    void developing(boolean on) {
        if (on == developing) return;
        developing = on;
        wetChanged.run();
    }
    /**
     * Whether the page's tilt matters now (so the sensor need run): a stroke that reads it is down, or a wash that moves
     * with it is still wet. A level, dry or still page needs no sensor.
     */
    boolean wantsTilt() { return tiltStroke || drying != null && drying.moves() || floating != null && floating.drifts() || canvas != null && canvas.runs(); }
    /** A stroke that reads the page's tilt (Gravity: a wet brush with gravity dials, the hand's page tilt, dust) began ({@code down}) or ended. */
    void strokeReadsTilt(boolean down) {
        if (down == tiltStroke) return;
        tiltStroke = down;
        tiltChanged.run();
    }
    /**
     * One tick of the settling wash and of the floating bath (it drifts on a tilted page, and prints once its floating
     * time has passed); false when neither is left. A page changed by anything else drops them where they are.
     */
    boolean tickDrying(ToneDocument document) {
        idle(document);
        ahead = null;
        if (drying != null) {
            if (document == null || !drying.valid(document)) { log.accept("drying wash stops: page changed"); hold(null); }
            else if (!drying.step(document, drying.moves() ? tilt.now() : PageTilt.LEVEL)) hold(null);
        }
        if (floating != null) {
            if (document == null || !floating.valid(document)) { log.accept("floating paint stops: page changed"); floating(null); }
            else if (!floating.tick(floating.drifts() ? tilt.now() : PageTilt.LEVEL)) floating(null);
        }
        if (!halos.isEmpty()) {
            if (document == null || !halosValid(document)) setHalos("page changed");
            else { HaloGrowth.apply(document, halos, stepHalos(halos)); haloRevision = document.revision(); grown(); }
        }
        // Wet paint moves a whole frame, once nothing else is wet.
        if (canvasTurn()) canvasFrame(document);
        return settling();
    }
    /** Whether the wet paint's turn has come: it moves, and nothing else is wet (or working there). */
    private boolean canvasTurn() { return canvas != null && canvas.moving() && drying == null && floating == null && !haloing() && job == null; }
    /** An unpaced frame of the wet paint ({@link #tickDrying}): it dries if its page is gone. */
    private void canvasFrame(ToneDocument document) {
        if (!canvas.valid(document)) { dryCanvas("page changed"); return; }
        boolean moved = canvas.runs();
        canvas.frame(canvas.runs() ? tilt.now() : PageTilt.LEVEL);
        if (moved != canvas.runs()) tiltChanged.run();
    }
    /**
     * The wet indicator's tap (the owner, 2026-10-29: "looks like the hair dryer just stops the image as it is instead of
     * accellerate the drying processing"): everything wet runs on to the end it would reach left alone (a Slow wash to
     * its last tick, halos through all theirs, a bath to its print, wet paint until it rests, then dried), its ticks back
     * to back with no pace and the page's tilt as it is ({@link #tilt}, as the ticks would read it), the page shown once
     * at the end ({@link #tick}), one undo step with its stroke as ever. A bath that can't drift prints now, as it would
     * once its time is up. Anything else changing the page meanwhile (a stroke, Undo, the page put away) lays what is
     * left where it is, as before; so does {@link #HURRY_MS}. Whether anything still runs: tick it on.
     */
    boolean hurry(ToneDocument document) {
        if (!hurrying) hurried = System.nanoTime();
        hurrying = true;
        if (floating != null && document != null && floating.valid(document) && (!floating.drifts() || tilt.now().level())) { printNow(document); floating(null); }
        if (settling()) return true;
        hurrying = false;
        dryCanvas("dried");
        return false;
    }
    /** Whether a tap's run is going ({@link #hurry}): the blow dryer blows on. */
    boolean hurrying() { return hurrying; }
    /**
     * Before any stroke, and before the page is put away or changed: what is still settling is laid where it is, and
     * what floats prints.
     */
    void settleNow(ToneDocument document) {
        hurrying = false;
        settleOthers(document);
        dryCanvas("dried");
    }
    /** {@link #settleNow} but for wet paint, which stays wet: before a stroke. */
    private void settleOthers(ToneDocument document) {
        setHalos("set");
        if (drying != null) {
            if (document != null && drying.valid(document)) layWet(document);
            hold(null);
        }
        if (floating != null) {
            if (document != null && floating.valid(document)) printNow(document);
            floating(null);
        }
    }
    /**
     * The drying wash, valid on {@code document}, laid where it is: as worked out ahead for the page as it shows (a tick
     * running there is let go), else once the work ahead is done, else here (gravity has moved it: once a tick running
     * there is written).
     */
    private void layNow(ToneDocument document) {
        if (worker == null) { drying.finishNow(document); return; }
        long t0 = System.nanoTime();
        boolean ready = ahead != null;
        if (ahead == null) idle(document);
        if (drying == null) return;
        long t1 = System.nanoTime();
        boolean worked = ahead instanceof DryingWater.Laid;
        if (worked) { DryingWater.Laid laid = (DryingWater.Laid)ahead; letGo(drying); drying.apply(document, laid); }
        else drying.finishNow(document);
        if (timing) log.accept(String.format(java.util.Locale.ROOT, "settle laid now %s wait_ms=%.1f write_ms=%.1f", ready ? "ready" : worked ? "waited" : "here", (t1 - t0) / 1e6, (System.nanoTime() - t1) / 1e6));
    }
    /** The floating bath, valid on {@code document}, printed: as worked out ahead (a tick running there is let go), else once the work there is done. */
    private void printNow(ToneDocument document) {
        if (worker == null) { floating.print(); return; }
        long t0 = System.nanoTime();
        boolean ready = ahead != null;
        if (ahead == null) idle(document);
        if (floating == null) return;
        long t1 = System.nanoTime();
        boolean worked = ahead instanceof FloatWater.Shown;
        // The bath printed as worked out ahead, left as it is: work let go may still read it.
        if (worked) { FloatWater.Shown print = (FloatWater.Shown)ahead; letGo(floating); floating.show(print); }
        else floating.print();
        if (timing) log.accept(String.format(java.util.Locale.ROOT, "settle printed now %s wait_ms=%.1f write_ms=%.1f", ready ? "ready" : worked ? "waited" : "here", (t1 - t0) / 1e6, (System.nanoTime() - t1) / 1e6));
    }
    /**
     * Before a stroke of {@code brush} (null: the eraser): {@link #settleNow}, except that a Float stroke on the layer
     * the bath floats on joins it (a Slow wash still settling is laid first all the same), and wet paint stays wet (the
     * owner, 2026-10-22).
     */
    void settleBefore(ToneDocument document, ModularBrush brush) {
        hurrying = false;
        // Wet paint keeps every tile it ever wet until it dries: past a quarter of the heap the stroke's first mask dries
        // the tiles painted longest ago (WetWatercolor.roomFor), not all of it, so the stroke just before stays wet to
        // blend with (the owner, 2026-10-23; docs/archive/stability-2026-10-22.md).
        if (haloing()) {
            // A Halo stroke on the halos' layer joins them. A halo still to be built reads the page under it first (here, or
            // waiting for the worker's read): the rest of its building reads nothing there.
            if (brush == null || !joinsHalos(document, brush)) { settleOthers(document); return; }
            if (document.revision() != haloRevision || !halos.isEmpty() && !halosValid(document)) { setHalos("page changed"); return; }
            long t0 = System.nanoTime();
            boolean waited = false;
            for (HaloGrowth.Lift l : lifts) if (!l.wasRead()) { waited = true; l.read(); }
            if (timing && waited) log.accept(String.format(java.util.Locale.ROOT, "halo read before a stroke wait_ms=%.1f", (System.nanoTime() - t0) / 1e6));
            return;
        }
        // A resist on Float lays as Quick (it never floats): the bath prints first.
        boolean joins = floating != null && brush != null && brush.choice(BrushPart.LIFE) == PartChoice.FLOAT && brush.choice(BrushPart.MEDIUM) != PartChoice.RESIST && document != null
                && floating.valid(document) && floating.layer == ModularStroke.layerOf(document, brush);
        if (!joins) { settleOthers(document); return; }
        if (drying != null) {
            if (drying.valid(document)) layWet(document);
            hold(null);
        }
        quiet(document);
    }
    /**
     * Nothing works on the bath there any more (a Float stroke joins it, Undo takes one back): a print worked out ahead
     * is let go, a tick written once it is done.
     */
    private void quiet(ToneDocument document) {
        if (working == Work.PRINT) letGo(jobOn);
        idle(document);
        ahead = null;
    }

    /**
     * The bath a Float stroke on {@code document}'s active layer floats on: the one floating there, or a new one
     * ({@link #settleBefore} printed any other first).
     */
    FloatWater floatWater(ToneDocument document, StrokePaper paper) {
        if (floating != null && floating.valid(document)) return floating;
        if (floating != null) { log.accept("floating paint dropped: page changed"); letGo(floating); ahead = null; }
        FloatWater water = new FloatWater(document, paper);
        floating = water;
        return water;
    }
    /** A Float stroke on {@code water} ended: it floats on for {@code f}'s floating time, drifting at its Drift (none left after a stroke with no samples). */
    void floatLifted(FloatWater water, ResolvedBrush.Float f) {
        if (water != floating) return;
        if (water.bath.strokes() == 0) { floating(null); return; }
        water.touched(f.floatMs, f.drift);
        // A print worked out before this stroke is stale: let go, worked out again for the bath as it is now.
        quiet(water.document);
        // A bath grown too big to show quickly prints now (FloatWater.MOST_PX).
        if (water.tooBig()) { log.accept("floating paint prints: the bath is full"); printNow(water.document); floating(null); return; }
        start();
        // The droplet shows, and the sensor listens while it can drift.
        wetChanged.run();
        tiltChanged.run();
    }
    /** The bath floating, or null (for the checks). */
    FloatWater floating() { return floating; }
    /**
     * Undo, from the drawing screen: while a bath floats on {@code document}, its last stroke taken back (the bath floats
     * on, or with its first stroke it is gone); else the page's Undo. Whether anything was taken back.
     */
    boolean undo(ToneDocument document) {
        hurrying = false;
        // Wet paint stops where it is, still wet; it takes up what the Undo changed when next used (WetCanvas#catchUp).
        if (canvas != null) canvas.rest();
        if (floating != null && floating.valid(document)) {
            quiet(document);
            if (floating == null) return document.undo();
            if (!floating.undo()) floating(null); else { wetChanged.run(); start(); }
            return true;
        }
        // Halos growing are set where they are; the undo takes the last stroke back with the veins grown since.
        setHalos("undo");
        // A Slow wash still settling goes back with its stroke: its work there is no longer wanted (the next tick drops it).
        boolean undone = document.undo();
        if (undone) letGo(drying);
        return undone;
    }
    private void floating(FloatWater water) {
        boolean was = floating != null, moved = wantsTilt();
        if (water != floating) { letGo(floating); ahead = null; }
        floating = water;
        if (was || water != null) wetChanged.run();
        if (moved != wantsTilt()) tiltChanged.run();
    }
    private void hold(DryingWater water) {
        boolean was = drying != null, moved = wantsTilt();
        if (water != drying) { letGo(drying); ahead = null; }
        drying = water;
        start();
        // A lift always tells: a stroke down may have hidden a change the indicator waits to show.
        if (was || water != null) wetChanged.run();
        if (moved != wantsTilt()) tiltChanged.run();
    }

    /**
     * After a Halo stroke's lift ({@code lift}, on its page): its halo is built (on the {@link #worker}, reading the page
     * as the stroke left it; without one, here) and grows beside the halos already growing, which its mark feeds. A
     * tick of theirs running there is written first.
     */
    void holdHalo(HaloGrowth.Lift lift) {
        ToneDocument document = lift.document;
        if (worker == null) {
            if (!halos.isEmpty() && !joined(document)) setHalos("page changed");
            HaloGrowth g = HaloGrowth.grow(lift, halos);
            if (g != null) halos.add(g);
            haloRevision = document.revision();
            wetChanged.run();
            return;
        }
        // A tick running there is written first (the halos then take the stroke as their page's change); a halo being
        // built goes on, this one after it.
        if (working == Work.HALO) idle(document);
        if (!halos.isEmpty() && !joined(document)) setHalos("page changed");
        lifts.add(lift);
        haloRevision = document.revision();
        if (job == null) {
            java.util.List<HaloGrowth> feed = new java.util.ArrayList<>(halos);
            run(Work.GROW, lift, () -> HaloGrowth.grow(lift, feed));
        }
        wetChanged.run();
    }
    /** The halos growing take the Halo stroke just drawn on {@code document} as their own page's change: whether they still grow there. */
    private boolean joined(ToneDocument document) {
        HaloGrowth first = halos.get(0);
        if (document.activeLayer() != first.layer) return false;
        for (HaloGrowth g : halos) g.joined(document);
        return halosValid(document);
    }
    /** Whether a stroke of {@code brush} joins the halos growing: a Halo brush (not a resist) on their layer. */
    private boolean joinsHalos(ToneDocument document, ModularBrush brush) {
        if (document == null || brush.choice(BrushPart.LIFE) != PartChoice.HALO || brush.choice(BrushPart.MEDIUM) == PartChoice.RESIST) return false;
        int layer = halos.isEmpty() ? lifts.get(0).layer : halos.get(0).layer;
        return ModularStroke.layerOf(document, brush) == layer;
    }
    private boolean halosValid(ToneDocument document) {
        for (HaloGrowth g : halos) if (!g.valid(document)) return false;
        return true;
    }
    /** One tick of every halo in {@code these}, together, worked out (on any thread): what each grew, in order. */
    private static HaloGrowth.Grown[] stepHalos(java.util.List<HaloGrowth> these) {
        HaloGrowth.Grown[] grown = new HaloGrowth.Grown[these.size()];
        for (int i = 0; i < grown.length; i++) grown[i] = these.get(i).step();
        return grown;
    }
    /** Drops the halos that have grown all their ticks. */
    private void grown() {
        if (halos.removeIf(HaloGrowth::done) && !haloing()) wetChanged.run();
    }
    /** Sets every halo growing where it is (newmods.js haloSet): one being built, or a tick running there, is let go. */
    private void setHalos(String reason) {
        if (!haloing()) return;
        if (working == Work.GROW || working == Work.HALO) {
            if (working == Work.HALO) for (HaloGrowth g : halos) g.stop();
            job.cancel(false);
            job = null; working = null; jobOn = null;
        }
        for (HaloGrowth g : halos) g.set();
        halos.clear();
        lifts.clear();
        log.accept("halos set: " + reason);
        wetChanged.run();
    }

    /** A pen event on the page handled ({@link SettlePacer#input}). */
    void input(long elapsedNanos, long eventAgeMs) { if (pace != null) pace.input(elapsedNanos, eventAgeMs); }
    /**
     * One paced tick of the settling wash ({@link DryingTicker}, every {@link SettlePacer#TICK_MS} while no stroke is
     * down): backing off, it only says how long to wait; otherwise one {@link #tickDrying} step, then {@code show}
     * (render and present the page's changed area) if the pace allows, and always at the end. Returns the ms until the
     * next tick, or −1 once the wash has settled, been laid or stopped. The ticks step by the patch's age, so however they
     * are paced the page ends the same.
     * <p>
     * With a {@link #worker} the step runs there: this starts it and returns {@link SettleWorker#POLL_MS}, and is called
     * again until it is done; then writes what it laid, shows it as above and paces on what the tick cost there and
     * here, its next tick {@link SettlePacer#TICK_MS} after, less what the work ahead done with it there took.
     */
    long tick(ToneDocument document, Runnable show) {
        long next = canvasTurn() ? tickCanvas(document, show) : worker != null ? tickThere(document, show) : tickHere(document, show);
        // The others done, wet paint still moving moves next.
        if (next < 0 && canvas != null && canvas.moving()) next = SettlePacer.TICK_MS;
        if (!hurrying) return next;
        // A tap's run: the next tick at once (a job there looked at again soon).
        if (next >= 0 && System.nanoTime() - hurried < HURRY_MS * 1_000_000) return job != null ? 1 : 0;
        // Done, or out of time: anything left laid where it is, the wet paint dried, and the page shown.
        settleNow(document);
        if (document != null && document.dirty() != null) { show.run(); if (pace != null) pace.shown(); }
        return -1;
    }
    /** How long the pace says to wait before working, ms: none unpaced, or for a tap's run. */
    private long waitMs() { return pace == null || hurrying ? 0 : pace.waitMs(); }
    /**
     * One slice of the wet paint ({@link WetCanvas#step}), on this thread (its pixel work shared with spare cores there),
     * backing off as the pace says; shown as the pace allows, and always at its end. Returns the ms until the next.
     */
    private long tickCanvas(ToneDocument document, Runnable show) {
        long wait = waitMs();
        if (wait > 0) return wait;
        if (!canvas.valid(document)) { dryCanvas("page changed"); return -1; }
        long t0 = System.nanoTime();
        boolean moved = canvas.runs();
        long next = canvas.step(canvas.runs() ? tilt.now() : PageTilt.LEVEL);
        if (moved != canvas.runs()) tiltChanged.run();
        show(document, show, next >= 0);
        if (pace != null) pace.worked(System.nanoTime() - t0, SettlePacer.TICK_BUDGET_MS);
        if (timing) log.accept(String.format(java.util.Locale.ROOT, "wet paint slice ms=%.1f tiles=%d", (System.nanoTime() - t0) / 1e6, canvas.wet.activeTiles()));
        return next;
    }
    private long tickHere(ToneDocument document, Runnable show) {
        long wait = waitMs();
        if (wait > 0) return wait;
        long t0 = pace == null ? 0 : pace.now();
        boolean more = tickDrying(document);
        show(document, show, more);
        if (pace != null) pace.worked(pace.now() - t0, SettlePacer.TICK_BUDGET_MS);
        return more ? SettlePacer.TICK_MS : -1;
    }
    /** Shows what a tick changed if the pace allows ({@link SettlePacer#mayShow}), and always after the last. */
    private void show(ToneDocument document, Runnable show, boolean more) {
        // A tap's run shows only its end (tick).
        if (hurrying) return;
        if (!more || pace == null || document.dirty() != null && pace.mayShow()) {
            show.run();
            if (pace != null) pace.shown();
        }
    }

    /**
     * A tick's work done there: what the wash laid, or the bath showed (one of them); what laying or printing it now writes
     * once that is written, worked out with it (null: as before the tick, or not ahead); how long the tick's own work took.
     */
    private static final class Ticked {
        final DryingWater.Laid laid;
        final FloatWater.Shown shown;
        final Object ahead;
        final long nanos;
        Ticked(DryingWater.Laid laid, FloatWater.Shown shown, Object ahead, long nanos) { this.laid = laid; this.shown = shown; this.ahead = ahead; this.nanos = nanos; }
    }
    private long tickThere(ToneDocument document, Runnable show) {
        if (job != null) {
            if (!job.isDone()) return SettleWorker.POLL_MS;
            if (working == Work.TICK) {
                long t0 = System.nanoTime(), there = ((Ticked)job.result()).nanos, aheadThere = job.nanos - there;
                written(document);
                boolean more = settling();
                long t1 = System.nanoTime();
                show(document, show, more);
                long here = System.nanoTime() - t0;
                // Paced as a tick on this thread was: its work there and its write here; the work ahead done with it there
                // fills the wait that follows.
                if (pace != null) pace.worked(there + here, SettlePacer.TICK_BUDGET_MS, aheadThere);
                if (timing) log.accept(String.format(java.util.Locale.ROOT, "settle tick work_ms=%.1f ahead_ms=%.1f write_ms=%.1f (apply %.1f undo %.1f) show_ms=%.1f", there / 1e6, aheadThere / 1e6, (t1 - t0) / 1e6, applied / 1e6, finished / 1e6, (System.nanoTime() - t1) / 1e6));
                applied = finished = 0;
                start();
                // The work ahead done there with the tick filled some of the wait for the next.
                return more ? Math.max(0, SettlePacer.TICK_MS - aheadThere / 1_000_000) : -1;
            }
            if (working == Work.HALO) {
                long t0 = System.nanoTime(), there = job.nanos;
                written(document);
                boolean more = settling();
                show(document, show, more);
                if (pace != null) pace.worked(there + System.nanoTime() - t0, SettlePacer.TICK_BUDGET_MS);
                if (timing) log.accept(String.format(java.util.Locale.ROOT, "halo tick work_ms=%.1f write_ms=%.1f", there / 1e6, (System.nanoTime() - t0) / 1e6));
                return more ? SettlePacer.TICK_MS : -1;
            }
            // The work ahead is done (or a halo built): the tick that is due starts now.
            idle(document);
        }
        if (!stillValid(document)) return -1;
        if (!halos.isEmpty() && lifts.isEmpty()) {
            long wait = waitMs();
            if (wait > 0) return wait;
            java.util.List<HaloGrowth> these = new java.util.ArrayList<>(halos);
            run(Work.HALO, these, () -> stepHalos(these));
            return SettleWorker.POLL_MS;
        }
        if (!lifts.isEmpty()) {
            HaloGrowth.Lift lift = lifts.get(0);
            java.util.List<HaloGrowth> feed = new java.util.ArrayList<>(halos);
            run(Work.GROW, lift, () -> HaloGrowth.grow(lift, feed));
            return SettleWorker.POLL_MS;
        }
        // Only wet paint left (it moves here, next tick: WetCanvas#step).
        if (drying == null && floating == null) return canvas != null && canvas.moving() ? 1 : -1;
        long wait = waitMs();
        if (wait > 0) return wait;
        DryingWater w = drying;
        FloatWater f = floating;
        PageTilt t = w != null ? (w.moves() ? tilt.now() : PageTilt.LEVEL) : f.drifts() ? tilt.now() : PageTilt.LEVEL;
        // A tap's run never lays or prints where it is, so nothing is worked out ahead.
        boolean workAhead = !hurrying;
        // Laying the wash or printing the bath after the tick is worked out with it, for the page as the tick leaves it.
        run(Work.TICK, w != null ? w : f, () -> {
            long t0 = System.nanoTime();
            if (w != null) {
                DryingWater.Laid laid = w.work(t);
                long took = System.nanoTime() - t0;
                return new Ticked(laid, null, workAhead && laid.more && laid.changed && w.finishesAhead() ? w.finishing(laid.tones) : null, took);
            }
            FloatWater.Shown shown = f.work(t);
            long took = System.nanoTime() - t0;
            return new Ticked(null, shown, workAhead && !shown.print && shown.moved ? f.printing(shown) : null, took);
        });
        return SettleWorker.POLL_MS;
    }
    /** Whether a job runs there, or is done and not yet taken in; and whether it is a tick (for the checks). */
    boolean working() { return job != null; }
    boolean ticking() { return working == Work.TICK; }
    /** Drops what a page changed by anything else (undo) has left invalid; whether anything is still settling. */
    private boolean stillValid(ToneDocument document) {
        if (!halos.isEmpty() && (document == null || !halosValid(document))) setHalos("page changed");
        if (drying != null && (document == null || !drying.valid(document))) { log.accept("drying wash stops: page changed"); hold(null); }
        if (floating != null && (document == null || !floating.valid(document))) { log.accept("floating paint stops: page changed"); floating(null); }
        return settling();
    }

    /**
     * Starts the work ahead that is due there, if nothing runs there and a tick hasn't brought it: laying the wash now
     * (lifting it first, after the lift), or printing the bath now (after a Float stroke or an Undo of one).
     */
    private void start() {
        if (worker == null || job != null || ahead != null || hurrying) return;
        DryingWater w = drying;
        FloatWater f = floating;
        if (w != null && w.finishesAhead()) run(Work.LAY, w, () -> { w.lift(); return w.finishing(); });
        else if (w == null && f != null) run(Work.PRINT, f, f::printing);
    }
    private void run(Work what, Object on, java.util.concurrent.Callable<?> work) { working = what; jobOn = on; job = worker.run(work); }
    /**
     * Waits for the job running there, if any, and takes it in: a tick's work written on {@code document} (not shown),
     * work ahead kept.
     */
    private void idle(ToneDocument document) {
        if (job == null) return;
        if (working == Work.TICK || working == Work.HALO) { written(document); return; }
        if (working == Work.GROW) {
            HaloGrowth g = (HaloGrowth)job.result();
            if (timing) log.accept(String.format(java.util.Locale.ROOT, "halo built work_ms=%.1f read_ms=%.1f band=%d px reach=%d px", job.nanos / 1e6, g == null ? 0 : g.readNanos / 1e6, g == null ? 0 : g.band, g == null ? 0 : g.reach));
            HaloGrowth.Lift lift = (HaloGrowth.Lift)jobOn;
            lifts.remove(lift);
            job = null; working = null; jobOn = null;
            // Only Halo strokes joining have changed the page since its stroke: it grows on as they do.
            if (g != null && lift.document.revision() == haloRevision) { g.joined(lift.document); halos.add(g); }
            else if (g != null) { setHalos("page changed"); return; }
            wetChanged.run();
            return;
        }
        Object done = job.result();
        if (timing) log.accept(String.format(java.util.Locale.ROOT, "settle ahead %s work_ms=%.1f%s", working == Work.LAY ? "lay" : "print", job.nanos / 1e6,
                jobOn instanceof DryingWater && ((DryingWater)jobOn).liftNanos > 0 ? String.format(java.util.Locale.ROOT, " (lift %.1f)", ((DryingWater)jobOn).liftNanos / 1e6) : ""));
        if (jobOn instanceof DryingWater) ((DryingWater)jobOn).liftNanos = 0;
        job = null; working = null; jobOn = null;
        ahead = done;
    }
    /** Writes the tick done there on {@code document}, if what it worked on is still settling there. */
    private void written(ToneDocument document) {
        if (working == Work.HALO) {
            HaloGrowth.Grown[] grown = (HaloGrowth.Grown[])job.result();
            Object on = jobOn;
            job = null; working = null; jobOn = null;
            if (!halos.equals(on)) return;
            if (document == null || !halosValid(document)) { setHalos("page changed"); return; }
            HaloGrowth.apply(document, halos, grown);
            haloRevision = document.revision();
            grown();
            return;
        }
        Ticked t = (Ticked)job.result();
        Object on = jobOn;
        job = null; working = null; jobOn = null;
        if (t.laid != null && on == drying) {
            if (document == null || !drying.valid(document)) { log.accept("drying wash stops: page changed"); hold(null); return; }
            drying.apply(document, t.laid);
            applied = drying.applyNanos; finished = drying.finishNanos;
            if (t.ahead != null) ahead = t.ahead; else if (t.laid.changed) ahead = null;
            if (!t.laid.more) hold(null);
        }
        if (t.shown != null && on == floating) {
            if (document == null || !floating.valid(document)) { log.accept("floating paint stops: page changed"); floating(null); return; }
            floating.show(t.shown);
            if (t.ahead != null) ahead = t.ahead; else if (t.shown.moved) ahead = null;
            if (t.shown.print) floating(null);
        }
    }
    /**
     * The job running there on {@code on} (the drying wash or the bath, being laid, printed or dropped) is no longer
     * wanted: let go unread (a drying wash's work stops at its next row, work not yet begun never runs); its result is
     * never taken in.
     */
    private void letGo(Object on) {
        if (job == null || on == null || jobOn != on) return;
        if (on instanceof DryingWater) ((DryingWater)on).stop();
        if (on instanceof FloatWater) ((FloatWater)on).stop();
        if (on instanceof java.util.List) for (Object g : (java.util.List<?>)on) ((HaloGrowth)g).stop();
        // Not yet begun there, it never runs.
        job.cancel(false);
        job = null; working = null; jobOn = null;
    }
}
