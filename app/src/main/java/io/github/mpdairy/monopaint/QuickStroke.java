package io.github.mpdairy.monopaint;

/**
 * A big brush's stroke drawn quick, then rendered (the owner: "go with quick then render"). A brush whose live stroke
 * would fall behind the pen on the Manta ({@link #quick}, decided at pen-down from the brush and its size, never
 * mid-stroke) shows a plain preview while the pen is down: the brush's footprint, as wide as its nib or body is at
 * each sample (the stampers' own geometry: {@link LineStamper#nib}, {@link BodyStamper#shape}, the hake's head), swept
 * from sample to sample and filled flat ({@link ModularCompose#flatTone}): the line's ink solid, opaque paint its own
 * grey, a wash and a stick's line their flat tones (a smudge's a light dither). No texture, no wet paint: only the pixels the
 * footprint newly reaches are written, a few times a stroke's width.
 *
 * <p>The true stroke is drawn from the samples by a {@link ModularStroke} with the page's tilt each was drawn at, on a
 * thread of its own as they come ({@link #render}): with quiet helpers ({@link Cores#quiet}), at the lowest priority while
 * the pen is down, into the stroke's own buffers only, so the pen never waits for it; every {@link #composeMs}, while
 * it keeps ahead of the pen, it composes what changed into tones of its own ({@link StrokeTones}, reading the page as the stroke found it, shared:
 * {@link ToneDocument#shareBase}). At lift ({@link #lift}) the preview is taken back (its gesture cancelled: the page as
 * the stroke found it) and the true stroke's gesture begins; the thread, at the usual priority now, draws what is left
 * and finishes everything but the page ({@link ModularStroke#develop}: the last compose, a Drying lift's settle); then
 * the pen's thread writes its tones to the page ({@link #develop}), while the host shows the wet indicator if it lasts
 * ({@link WatercolorSession#developing}). {@link #finish} waits and does it at once.
 *
 * <p>Trail (the audit's finding 17, {@link #trail}): where the true stroke's tones are final while the pen is down
 * (Instant everywhere, Fast-wet outside its window; Drying's as laid, its lift settling them after: {@link ModularStroke#trail}), the pen's thread writes them to the page
 * behind the pen, a tile at a time, once each time the render has composed more ({@link #writeBehind}), so each write
 * joins the next refresh of the page with the preview's; the preview stays only near the pen. The preview's gesture is
 * the true stroke's from pen-down, so at lift nothing is taken back: only the tiles that changed since they were
 * written, and those the preview wrote since, are written, each restored to what the gesture found and written again.
 * The page ends byte for byte as without it.
 *
 * <p>One undo step; a Drying wash then settles, and gravity moves it, exactly as after any stroke. The page ends as the
 * same samples drawn live end: every layer the same for Instant and Drying strokes; for Fast-wet the same tones, a
 * layer's coverage as when its whole stroke is composed once (live, it depends on how the input was batched).
 * Pure, checked on the desktop ({@code QuickStrokeChecks}).
 */
final class QuickStroke implements LiftingStroke {
    // Which strokes go quick ({@link #quick}): two reasons, the panel's and the engine's. The panel's: a stroke whose
    // marks change pixels again and again under the pen reaching past PANEL. The engine's: how far a stroke's marks may
    // reach ({@link ModularStroke#reach}, page px) and still keep up with the pen drawn live on the Manta (the device bench, ModularPerfBench: a 480 Hz stroke, the pen's wait until
    // its samples are rendered under 60 ms at the 95th percentile, measured with the screen asleep, which runs the
    // engine about 1.8 times slower than awake), by what drives the cost. Past it a stroke goes quick. Tune here;
    // scripts/bench_device.sh -e sizes edge -e quick rule times each brush at the biggest size still drawn live.
    /** A wash, ink or opaque paint (impasto too) with no stick and nothing Fast-wet re-lays under the pen: keeps up at any size. */
    static final double ANY = Double.POSITIVE_INFINITY;
    /** An ink line whose Fast-wet tail re-tapers under the pen ({@link ModularStroke#retapers}). */
    static final double RETAPER_INK = 320;
    /** A graphite line (the paper's tooth at every pixel). */
    static final double GRAPHITE = 260;
    /** A charcoal or pastel stick's line, or graphite with a soft edge (the stick's grain and edge at every pixel). */
    static final double STICK = 200;
    /** Placed lines (a split nib's, multi-line), each laid as a line of its own. */
    static final double PLACED = 220;
    /** A Fast-wet line at least a quarter as broad as the wet body it bleeds into (the line merged into the body as laid). */
    static final double LINE_INTO_WET_BODY = 260;
    /** A stick's or graphite's line whose Fast-wet tail re-tapers: the stick laid again behind the nib. */
    static final double RETAPER_STICK = 130;
    /** Impasto, Fast-wet: each re-settle raises its relief again, and the relief composes every pixel. */
    static final double WET_IMPASTO = 280;
    /** A Fast-wet body: its window re-settles under the pen. */
    static final double WET_BODY = 420;
    /**
     * Bristles, line or body (any Drying too: the hairs are laid as the pen moves): every hair's dab, step by step. The
     * Manta (2026-10-16, `-e quick off`, Fast-wet): the Bristle brush preset's ink at 60, 120 and 240 px (reach 134, 268,
     * 536) waits 4, 16 and 23 ms at the 95th percentile; Hog bristle's impasto body (reach 125, 250, 500) 13, 55 and 134
     * ms, which Fast-wet impasto's own row already sends quick past 280.
     */
    static final double BRISTLES = 540;
    /**
     * Stipple and Hatch (the Manta, 2026-10-16, `-e quick off`, Fast-wet, awake, so about 1.8 times these asleep): the
     * Stipple pen preset at 120, 240 and 360 px (reach 214, 418, 621) waits 3, 8 and 10 ms at the 95th percentile, so it
     * keeps up at every size; the Hatch pen at 60, 80, 100, 120, 160 and 240 px (reach its size and 3) 4, 12, 17, 29, 54
     * and 406 ms: it keeps up to about 125. Stipple sponge's wet body (reach 490 at 240 px: 27 ms) is Fast-wet's own row's.
     */
    static final double STIPPLE = 640, HATCH = 125;
    /**
     * Stamp ({@link StampStamper}): each print turns its nib over every pixel it covers, as Hatch's lines do over their
     * band; not yet measured on the Manta, so taken as Hatch's.
     */
    static final double STAMP = HATCH;
    /**
     * Spiro ({@link SpiroStamper}): a fine line laid once a pixel, a few dozen pixels a sample whatever its size: keeps
     * up at any size, and must, as its quick preview's footprint would follow the pen, not the ring's rosette.
     */
    static final double SPIRO = ANY;
    /**
     * The panel's reach, page px: 3 mm on the Manta's 300 ppi panel (where the owner found brushes slow to show). A live
     * stroke keeps changing the pixels under its nib while the nib passes (its body darkening dab by dab, a Fast-wet
     * window settling behind it), and the panel shows a pixel only after its last change; a quick stroke's preview writes
     * each pixel once, as the nib's edge reaches it. Engine spec, "Quick then render", panel findings.
     */
    static final double PANEL = 36;

    /**
     * How far {@code r}'s marks reach where a live stroke changes pixels again and again, page px: its line (not a
     * stipple's, whose dots are laid once), and its body
     * but for a transparent Drying body, whose live preview is a flat light grey written once (why Watercolor in Slow
     * already showed promptly), unless it smudges; and a smudge's dab, which changes the page under the pen again and again,
     * but for a smudge with no paint of its own: a preview can't show a smudge, so drawn quick its smear would only show
     * behind the pen (the owner, 2026-10-23: smudges "take a second to show the effect"); live, it shows under the pen.
     */
    static double panelReach(ResolvedBrush r) {
        // A stipple's dots are each laid once, where they land: its line changes no pixel again under the pen; nor does a
        // Spiro's fine line, which is laid away from the pen besides (its ring's rosette round the pin), nor a comb's
        // placed lines that lay nothing (Wash comb's: its tines smudge).
        double line = !r.line || r.lineTip == ResolvedBrush.LineTip.STIPPLE || r.lineTip == ResolvedBrush.LineTip.SPIRO || r.lineTip == ResolvedBrush.LineTip.HAND_HATCH ? 0 : ModularStroke.lineReach(r);
        boolean smudges = r.carry != null && r.carry.moves, flatPreview = r.life == ResolvedBrush.Life.DRY && r.cover <= 0 && !smudges;
        double body = flatPreview || r.bodyTip == ResolvedBrush.BodyTip.SPIRO ? 0 : ModularStroke.bodyReach(r);
        return Math.max(Math.max(line, body), smudges && (r.line || r.body != null) ? 2 * r.carry.r : 0);
    }

    /**
     * How far {@code r}'s marks may reach and still keep up with the pen live on the Manta, page px: the least of what
     * its line and body ask. A Drying body shows a plain preview live, so only its line counts.
     */
    static double keepsUpTo(ResolvedBrush r) {
        double most = ANY;
        if (r.line && r.lineTip == ResolvedBrush.LineTip.BRISTLES || r.body != null && r.bodyTip == ResolvedBrush.BodyTip.BRISTLES) most = BRISTLES;
        if (r.pattern != null) most = r.pattern.stipple() || r.pattern.handHatch() ? STIPPLE : r.pattern.stamp() ? STAMP : r.pattern.spiro() ? SPIRO : HATCH;
        if (r.line && r.lineTip == ResolvedBrush.LineTip.NIB) {
            boolean stick = r.stick(), retapers = ModularStroke.retapers(r);
            if (stick || r.rule == ResolvedBrush.Rule.GRAPHITE) most = retapers ? RETAPER_STICK : stick ? STICK : GRAPHITE;
            else if (retapers) most = RETAPER_INK;
            if (r.linePlaced) most = Math.min(most, PLACED);
            if (r.life == ResolvedBrush.Life.WET && r.body != null && r.parts.lineWet > 0 && 4 * r.inkSize >= r.bodySize) most = Math.min(most, LINE_INTO_WET_BODY);
        }
        if (r.body != null && r.life != ResolvedBrush.Life.DRY) {
            boolean wet = r.life == ResolvedBrush.Life.WET;
            if (wet) most = Math.min(most, r.look.impasto > 0 ? WET_IMPASTO : WET_BODY);
        }
        return most;
    }
    /**
     * A smudge's dab (its diameter, page px) that keeps up live on its own: its trade with the page and the compose of
     * all it moved. The device bench over marks (2026-10-23, `-e marks true -e quick off`, the Tools page's presets),
     * pen to screen p95: Dirty brush 24, 32 and 44 ms at 120, 168 and 240 px, Water brush 27, 34 and 72, Wash comb
     * (a comb) 9, 46 and 148; Blend stump (Soften 1, and the pair's wash too) 7 ms at 42 px, 81 at 60; Stump (Soften 0)
     * 6 at 60. So 150 px keeps up with room (the 2026-10-16 bench's 90 was before the dabs shared the cores); Soften's
     * stump costs about three times the trade ({@link #cost}), a comb's tines no more than it. Its share adds to the
     * marks' ({@link #cost}).
     */
    static final double CARRY = 150;
    /** {@code r}'s live cost: its reach over what keeps up ({@link #keepsUpTo}), and a smudge's dab over {@link #CARRY}; past 1 the pen would wait. */
    static double cost(ResolvedBrush r) { return ModularStroke.reach(r) / keepsUpTo(r) + (r.carry != null && r.carry.moves ? 2 * r.carry.r / CARRY * (r.carry.tines == null ? 1 + 2 * r.carry.soften : 1) : 0); }
    /**
     * Whether a stroke of {@code r} is drawn quick, then rendered at lift: its marks change pixels under the pen past
     * {@link #PANEL}, or the engine wouldn't keep up live. Never Stay in Lines: its shape is read from the page at
     * pen-down and its flood shows as it runs, which a render can't do.
     */
    static boolean quick(ResolvedBrush r) { return r.flt != null || !r.staysIn() && (panelReach(r) > PANEL || cost(r) > 1); }

    /**
     * A six-part stroke on the selected pair ({@link ModularStroke#selectLayer}): quick ({@link #quick}) or live. Every
     * stroke the drawing screen and Brush Lab's test page draw starts here ({@link InkWash#stroke}).
     */
    static DrawingStroke start(ToneDocument document, ModularBrush brush, double size, double darkness, WatercolorSession session) {
        ResolvedBrush r = ResolvedBrush.of(brush, size, darkness);
        // Wet (the wet paint spec): laid into the pair's wet paint, final under the pen; without a session (a sample), as Instant.
        if (r.wet != null && session != null) return new WetPaintStroke(document, brush, r, session);
        // Pastels and Dry brush over wet paint draw on its layer and join it at the end (WatercolorSession#joinsWet).
        int joins = session == null ? -1 : session.joinsWet(document, brush);
        if (joins >= 0) document.selectLayer(joins); else ModularStroke.selectLayer(document, brush);
        ModularStroke.prepare(document, r, session);
        Boolean always = benchAlways;
        // Draw once (the experiment): Pastels and Dry brush as Instant, quick, with no preview, each pixel written once.
        if (drawOnce && drawsOnce(brush)) {
            if (brush.choice(BrushPart.LIFE) == PartChoice.FAST_WET) { brush = ResolvedBrush.asInstant(brush); r = ResolvedBrush.of(brush, size, darkness); }
            always = true;
        }
        return (always != null ? always : quick(r)) ? new QuickStroke(document, brush, size, darkness, session, r) : new ModularStroke(document, brush, size, darkness, session);
    }
    /**
     * Draw once (the owner's experiment, 2026-10-22: "one write per pixel", the wet paint spec): Pastels and Dry brush
     * strokes are drawn quick with no preview, Quick drying as Instant, so the panel shows each pixel once, final, as the
     * true stroke is written behind the pen ({@link #trail}), not a flat preview that the texture replaces (two updates of
     * each pixel). Off by default; switched by the drawing screen from its launch ({@code drawOnce}, {@link PaintActivity}).
     */
    static volatile boolean drawOnce;
    /** Whether {@link #drawOnce} draws {@code brush} so: a Pastels or Dry brush one. */
    static boolean drawsOnce(ModularBrush brush) { return "Pastels".equals(brush.style.name) || "Dry brush".equals(brush.style.name); }
    /** The live-stroke bench's override of {@link #quick}: true, every stroke quick; false, every one live; null, the rule. */
    static volatile Boolean benchAlways;
    /** As {@link #start}, quick whatever the rule says: for the checks and the bench. */
    static QuickStroke quickly(ToneDocument document, ModularBrush brush, double size, double darkness, WatercolorSession session) {
        ModularStroke.selectLayer(document, brush);
        ResolvedBrush r = ResolvedBrush.of(brush, size, darkness);
        ModularStroke.prepare(document, r);
        return new QuickStroke(document, brush, size, darkness, session, r);
    }

    private final ToneDocument document;
    private final WatercolorSession session;
    private final ResolvedBrush r;
    private final PageTilt.Source tilt;
    private final boolean handTilt;
    /** The preview's samples: where the footprint goes. */
    private final StrokePoints pts = new StrokePoints();
    /**
     * Screen: how much of a full body a transparent body's preview dots stand for ({@link ResolvedBrush#screenTone} of
     * it): a wet body's at middling pressure, as the final lays it under the tip (crisp and even when screened).
     */
    static final double SCREENED_BODY = .85;
    /** The least pen time between two writes of the preview, ms. */
    static final long WRITE_MS = 8;
    /** The least distance between two shapes the preview sweeps between, px: a 24th of the stroke's reach, at least 2. */
    private final double step;
    /** The last sample swept (−1 for none). */
    private int swept = -1;
    /**
     * The preview of the line and of the body; null where the brush draws none. Placed lines (a split nib's, multi-line):
     * a sweep for each line, and for each side of a split's gap ({@link #placedParts}); else one.
     */
    private final Sweep[] lines;
    private final Sweep body;
    /**
     * Paint Load ({@link LoadedPaint}): the preview's samples spend the true stroke's paint as it does; where less than
     * {@link #SHOWN} is left the brush only smudges, and the preview shows it as a smudge's ({@link #emptied}, made then).
     * Null for a brush that never runs out.
     */
    private final LoadedPaint.Run paint;
    private Sweep emptied;
    /** Draw once ({@link #drawOnce}): no preview. */
    private final boolean once;
    /**
     * A smudge with no paint of its own that trails (the owner, 2026-10-23: smudges "take a second to show the effect"):
     * no preview; the render composes whenever it has caught up, and the true smudge is written under the pen too, not
     * only behind it, so the smear shows as it is made rather than at lift.
     */
    private final boolean underPen;
    /** Paint Load: the share of its own paint under which the preview shows the brush as smudging only. */
    static final double SHOWN = .5;
    /**
     * Gradient ({@link GreyGradient}): the preview's grey along the stroke, a panel grey at a time ({@link #shade}): the
     * stroke's own at first, then each of the 16 it passes, resolved once each ({@link #levels}). Null for a brush whose
     * grey stays.
     */
    private final GreyGradient gradient;
    private final ModularBrush brush;
    private final double size;
    private ResolvedBrush[] levels;
    /** The panel grey the preview's sweeps show now: −1 for the stroke's own. */
    private int level = -1;
    /** Blend: the page under the stroke as found ({@link ModularStroke#underTop}), for the preview; null for the medium's own way. */
    private final ModularCompose.Under under;
    /** White and Invert: the preview's dots cover what is under them ({@link ToneDocument#coverTones}, all of it). */
    private final int[] opaque;

    /** The true stroke ({@link ModularStroke#render}): its samples go to it on its own thread, its gesture begins at lift. */
    private final ModularStroke full;
    /**
     * Samples (and, for a brush that reads it, the page's tilt each was drawn at; else null) not yet handed to the
     * render thread; under their lock with {@link #lifted}, {@link #rendered} and {@link #failed}.
     */
    private final PenBatch incoming = new PenBatch();
    private final java.util.ArrayList<PageTilt> incomingTilts;
    private boolean lifted, rendered;
    /** The last sample with a position, for {@link ModularStroke#reachCarry} (NaN before one). */
    private float lastX = Float.NaN, lastY = Float.NaN;
    private Throwable failed;
    /** The render thread's own: the samples handed over, their tilts, how many it has drawn, the tilt it reads now. */
    private final PenBatch taken = new PenBatch();
    private final java.util.ArrayList<PageTilt> takenTilts;
    private volatile int fed;
    private PageTilt replayTilt = PageTilt.LEVEL;
    /** The page's tilt at lift (charcoal's dust reads it at the finish). */
    private PageTilt liftTilt = PageTilt.LEVEL;
    private boolean done, changed;
    /** How many samples the stroke has had, and how many the true stroke had drawn by lift. */
    private int received;
    int takenBeforeLift;
    /**
     * Time spent rendering the true stroke on the render thread, the part of it after lift (what was left to draw and
     * {@link ModularStroke#develop}), and writing it to the page after lift, ns (for the stroke's timing).
     */
    volatile long renderNanos, afterLiftNanos;
    long finishNanos;
    /**
     * How often the render thread composes what it has drawn while the pen is down, ms of its own time: what it composes
     * again (a Fast-wet window's) costs it once an interval, not once a sample. The checks set 0 (every batch).
     */
    static volatile long composeMs = 100;
    /** Float: the least time between two of the bath's frames under the pen, ms of the render thread's time (e-ink shows a few a second). */
    static final long FRAME_MS = 60;
    /**
     * Trail ({@link ModularStroke#trail}): on by default; off, a quick stroke shows its preview until lift, as before. A
     * pixel behind the pen changes twice (the preview's grey, then the true tone), which the panel shows as a second
     * update there; this turns it off should that flicker on the device.
     */
    static volatile boolean trail = true;
    /** Whether this stroke trails; the tiles its preview wrote since the true stroke was written there (null if not). */
    private final boolean trails;
    private final boolean[] previewed;
    /** Held by the render thread while it composes and by the pen's thread while it writes behind the pen. */
    private final java.util.concurrent.locks.ReentrantLock composing = new java.util.concurrent.locks.ReentrantLock();
    /** How many composes the render has made, and how many the pen's thread had seen when it last wrote behind the pen. */
    private volatile int composes;
    private int written;
    /**
     * The most tiles one write behind the pen takes ({@link #writeBehind}): a 64 px tile is about 0.1 ms of the pen's
     * thread on the desktop, a few on the Manta, over the cores; more wait for the next present.
     */
    static final int TRAIL_TILES = 8;
    /** The most tiles this stroke's write takes: {@link #TRAIL_TILES}; under the pen ({@link #underPen}), twice the tiles its dab spans, so it keeps up with the dab. */
    private final int trailMost;
    /** Tiles were left for the next present by the last write behind the pen. */
    private boolean behind;
    /** The stroke's length from which the true stroke may still change, as of the render's last compose ({@link ModularStroke#frontier}). */
    private volatile double frontier;
    /** How many tiles were written behind the pen, and how often, while it was down (for the checks and the timing). */
    int trailTiles, trailWrites;
    /** Float: frames made and not yet shown, under {@link #incoming}'s lock. */
    private final java.util.ArrayList<ModularStroke.Frame> frames = new java.util.ArrayList<>();

    private QuickStroke(ToneDocument document, ModularBrush brush, double size, double darkness, WatercolorSession session, ResolvedBrush r) {
        this.document = document; this.session = session; this.r = r; this.brush = brush; this.size = size;
        gradient = GreyGradient.of(brush, darkness, session == null ? null : session.swatches);
        tilt = session != null ? session.tilt : () -> PageTilt.LEVEL;
        handTilt = ModularStroke.handTilt(r);
        boolean readsTilt = ModularStroke.readsTilt(r);
        incomingTilts = readsTilt ? new java.util.ArrayList<>() : null;
        takenTilts = readsTilt ? new java.util.ArrayList<>() : null;
        step = Math.max(2, ModularStroke.reach(r) / 24);
        StrokePaper paper = new StrokePaper(r.paper);
        under = r.blendMode == ResolvedBrush.BlendMode.OWN ? null : ModularStroke.reading(document, r);
        opaque = r.blendMode.lays() ? new int[document.width] : null;
        if (opaque != null) java.util.Arrays.fill(opaque, 255);
        full = ModularStroke.render(document, brush, size, darkness, session, () -> replayTilt);
        paint = full.paint() == null ? null : new LoadedPaint.Run(r, full.paint().share);
        boolean[] marks = trail ? new boolean[((document.width + ToneDocument.TILE - 1) / ToneDocument.TILE) * ((document.height + ToneDocument.TILE - 1) / ToneDocument.TILE)] : null;
        trails = marks != null && full.trail(marks);
        previewed = trails ? marks : null;
        boolean smudgeOnly = r.body == null && !r.line && r.carry != null && r.carry.moves;
        underPen = trails && smudgeOnly;
        int across = underPen ? (int) Math.ceil((2 * r.carry.r + 2) / ToneDocument.TILE) + 1 : 0;
        trailMost = Math.max(TRAIL_TILES, 2 * across * across);
        // A smudge with no paint of its own shows its smudge's footprint (as the light wash, ModularCompose.flatTone); one
        // that trails shows the true smudge instead, under the pen too ({@link #underPen}).
        // Draw once: no preview; the true stroke is written behind the pen, each pixel once.
        once = drawOnce && drawsOnce(brush);
        if (once || underPen || !(r.line || smudgeOnly)) lines = null;
        else {
            int parts = placedParts(r);
            lines = new Sweep[Math.max(1, parts)];
            for (int i = 0; i < lines.length; i++) lines[i] = new Sweep(true, paper, false, parts > 0 ? i : -1);
        }
        body = !once && r.body != null ? new Sweep(false, paper) : null;
        // Trail: the preview draws in the true stroke's gesture, which goes on at lift.
        // Masking fluid previews on the page's mask, so it shows hatched under the pen; wax its sheen.
        if (trails) full.begin();
        else if (ModularStroke.fluid(r)) document.beginFluid(1); else document.begin(1, full.kind());
        // The render thread reads the page as the stroke found it while the preview draws on it.
        document.shareBase(true);
        // Its own thread and helpers, at the lowest priority while the pen is down ({@link Cores#quiet}): the pen's
        // thread never waits for it.
        Thread thread = new Thread(this::render, "Inkwash quick render");
        thread.setDaemon(true);
        thread.start();
    }

    @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY) { sample(x, y, pressure, tiltX, tiltY, PenSpeed.UNTIMED); }
    @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY, long time) {
        long t0 = Prof.now();
        PageTilt g = incomingTilts != null ? tilt.now() : null;
        synchronized (incoming) {
            if (lifted) return;
            // A smudge reads the page as the pen found it: its plane is filled here, on the page's thread, where the sample can reach, before the render has it.
            if (Float.isFinite(x) && Float.isFinite(y)) {
                float fx = Float.isNaN(lastX) ? x : lastX, fy = Float.isNaN(lastY) ? y : lastY;
                full.reachCarry(Math.min(x, fx), Math.min(y, fy), Math.max(x, fx), Math.max(y, fy));
                lastX = x; lastY = y;
            }
            incoming.add(x, y, pressure, tiltX, tiltY, time);
            if (g != null) incomingTilts.add(g);
            received++;
            incoming.notify();
        }
        // As the live stroke: a sample without a position or pressure reading draws nothing.
        if (Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(pressure)) {
            if (handTilt) { pts.gx = g.x; pts.gy = g.y; }
            if (pts.add(x, y, ToolSettings.travel(pressure), time, tiltX, tiltY) && paint != null) paint.spend(pts);
        }
        Prof.add(Prof.QUICK, t0);
    }
    /**
     * Shows the samples since the last write, once {@link #WRITE_MS} of pen time has passed since it (every call for
     * untimed samples): the footprint swept through them, every {@link #step} px and to the newest, written body first,
     * then the line over it. In between it waits, so each page row is written a few times a stroke's width, not every
     * sample; the newest few ms show with the next samples, or not at all at lift.
     */
    /** The preview's samples and the true stroke's read the pen's speed alike. */
    @Override public void zoom(float scale) { pts.zoom = scale; full.zoom(scale); }
    @Override public void present() {
        // Float: the bath's frames the render thread made, shown in the preview's gesture.
        if (full.floats() && !isLifted()) {
            java.util.List<ModularStroke.Frame> show;
            synchronized (incoming) { show = frames.isEmpty() ? null : new java.util.ArrayList<>(frames); frames.clear(); }
            if (show != null) for (ModularStroke.Frame f : show) full.showFrame(f);
        }
        if (trails && !done && !isLifted()) writeBehind();
        int newest = pts.count - 1;
        if (done || isLifted() || newest <= swept) return;
        long now = pts.t[newest], last = swept < 0 ? PenSpeed.UNTIMED : pts.t[swept];
        if (swept >= 0 && now != PenSpeed.UNTIMED && last != PenSpeed.UNTIMED && now - last < WRITE_MS) return;
        long t0 = Prof.now();
        for (int k = swept + 1; k <= newest; k++) {
            // A hake's preview waits for the stroke's direction, as the true stroke does (FootprintSweep#holds).
            if (body != null && body.holds() || lines != null && lines[0].holds()) break;
            if (k < newest && swept >= 0 && pts.d[k] - pts.d[swept] < step) continue;
            // Paint Load: once the paint is mostly spent, the brush only smudges.
            if (paint != null && pts.own[k] < SHOWN && !once) {
                if (emptied == null) emptied = new Sweep(r.body == null, new StrokePaper(r.paper), true);
                emptied.to(k);
                swept = k;
                continue;
            }
            if (gradient != null) shade(k);
            if (body != null) body.to(k);
            if (lines != null) for (Sweep l : lines) l.to(k);
            swept = k;
        }
        if (body != null) body.flush();
        if (lines != null) for (Sweep l : lines) l.flush();
        if (emptied != null) emptied.flush();
        Prof.add(Prof.QUICK, t0);
    }
    private boolean isLifted() { synchronized (incoming) { return lifted; } }
    /**
     * Gradient: the sweeps' grey at sample {@code k}, the panel grey nearest the gradient's there (the stroke's own while
     * that is its nearest): what is swept so far is written first in the grey it was swept in.
     */
    private void shade(int k) {
        int start = (int) Math.round(r.shade * (GreyGradient.LEVELS - 1)), at = (int) Math.round(gradient.darkness(pts.d[k]) * (GreyGradient.LEVELS - 1));
        int next = at == start ? -1 : at;
        if (next == level) return;
        level = next;
        if (levels == null) levels = new ResolvedBrush[GreyGradient.LEVELS];
        ResolvedBrush rr = next < 0 ? r : levels[next] != null ? levels[next] : (levels[next] = ResolvedBrush.of(brush, size, next / (double) (GreyGradient.LEVELS - 1)));
        if (body != null) { body.flush(); body.shade(rr); }
        if (lines != null) for (Sweep l : lines) { l.flush(); l.shade(rr); }
    }
    /**
     * Trail: once the render has composed more since the last time (or tiles were left the last time), and isn't
     * composing now, the true stroke written behind the pen ({@link ModularStroke#writeBehind}): up to {@link #TRAIL_TILES}
     * tiles, any but those near the samples from its {@link #frontier} on, the pen's newest included. On the pen's
     * thread, with its present: joining the page's next refresh.
     */
    private void writeBehind() {
        if (composes == written && !behind || !composing.tryLock()) return;
        try {
            written = composes;
            int newest = pts.count - 1;
            if (newest < 0) return;
            double pad = full.trailReach();
            float l = Float.POSITIVE_INFINITY, t = Float.POSITIVE_INFINITY, rt = Float.NEGATIVE_INFINITY, b = Float.NEGATIVE_INFINITY;
            for (int k = Math.max(0, pts.firstAt((float) frontier) - 1); k <= newest; k++) {
                l = Math.min(l, pts.x[k]); rt = Math.max(rt, pts.x[k]); t = Math.min(t, pts.y[k]); b = Math.max(b, pts.y[k]);
            }
            int[] away = underPen ? null : new int[]{(int) Math.floor(l - pad), (int) Math.floor(t - pad), (int) Math.ceil(rt + pad), (int) Math.ceil(b + pad)};
            long t0 = Prof.now();
            int n = full.writeBehind(away, trailMost);
            Prof.add(Prof.QUICK, t0);
            behind = n == trailMost;
            if (n > 0) { trailTiles += n; trailWrites++; }
        } finally { composing.unlock(); }
    }
    /** No samples wait for the render thread. */
    private boolean caughtUp() { synchronized (incoming) { return incoming.isEmpty(); } }

    /**
     * The render thread: draws each sample into the true stroke as it comes (its own buffers and tones only; the page is
     * the preview's until lift), with the tilt it was drawn at, composing every {@link #composeMs}; after lift, at the
     * usual priority, the rest and {@link ModularStroke#develop}. Writing the page is the pen's thread's, at
     * {@link #develop}.
     */
    private void render() {
        Thread me = Thread.currentThread();
        me.setPriority(Thread.MIN_PRIORITY);
        Cores.quiet(true);
        try {
            boolean last = false, waited = false;
            long composed = System.nanoTime(), lifting = 0, framed = 0;
            while (!last) {
                synchronized (incoming) {
                    while (incoming.isEmpty() && !lifted) { waited = true; incoming.wait(); }
                    last = lifted;
                    incoming.moveTo(taken);
                    if (incomingTilts != null) { takenTilts.addAll(incomingTilts); incomingTilts.clear(); }
                }
                long t0 = System.nanoTime();
                if (last) {
                    // Lifted: the pen's thread waits for the rest, at the usual priority, with the usual helpers.
                    me.setPriority(Thread.NORM_PRIORITY);
                    Cores.quiet(false);
                    lifting = t0;
                }
                for (int i = fed; i < taken.size(); i++) {
                    if (takenTilts != null) replayTilt = takenTilts.get(i);
                    taken.feed(full, i);
                    fed = i + 1;
                }
                // What it has drawn, composed off the page every composeMs, so the lift is left only the newest; only
                // while it keeps ahead of the pen (it has waited for samples since, and none wait now): drawing them
                // comes first. A smudge shown under the pen composes whenever none wait, so the newest shows too.
                if (!last && (waited || underPen) && full.composesOffPage() && System.nanoTime() - composed >= (underPen ? 0 : composeMs) * 1_000_000 && caughtUp()) {
                    // Trail: the pen's thread writes what it composed behind the pen; never while it composes.
                    if (trails) composing.lock();
                    try {
                        full.present();
                        if (trails) { frontier = full.frontier(); composes++; }
                    } finally { if (trails) composing.unlock(); }
                    composed = System.nanoTime(); waited = false;
                }
                // Float: the bath near the pen, quick, for the pen's thread to show, every FRAME_MS once caught up.
                if (!last && full.floats() && System.nanoTime() - framed >= FRAME_MS * 1_000_000 && caughtUp()) {
                    ModularStroke.Frame f = full.floatFrame();
                    if (f != null) synchronized (incoming) { frames.add(f); }
                    framed = System.nanoTime();
                }
                renderNanos += System.nanoTime() - t0;
            }
            long t0 = System.nanoTime();
            // As at lift: what follows (a tap's dot) reads the page's tilt then.
            replayTilt = liftTilt;
            if (full.composesOffPage() || full.floats()) full.develop();
            renderNanos += System.nanoTime() - t0;
            afterLiftNanos = System.nanoTime() - lifting;
        } catch (Throwable e) {
            synchronized (incoming) { failed = e; }
        } finally {
            Cores.quiet(false);
            synchronized (incoming) { rendered = true; incoming.notifyAll(); }
        }
    }

    /**
     * The pen lifted: the preview is taken back and the true stroke's gesture begins; its render thread takes the last
     * samples ({@link #develop} finishes it). Returns whether it is still to do (false once done).
     */
    @Override public boolean developsAfterLift() { return !done; }
    @Override public boolean screened() { return r.screen != null; }
    @Override public boolean lift() {
        PageTilt g = incomingTilts != null ? tilt.now() : PageTilt.LEVEL;
        synchronized (incoming) {
            if (lifted) return !done;
            // A Flick carries a smudge on past every sample: its plane filled as far as one can reach, here on the page's thread.
            if (!Float.isNaN(lastX)) full.reachFlick(lastX, lastY);
            liftTilt = g; lifted = true; takenBeforeLift = fed;
            incoming.notify();
        }
        // Trail: the preview's gesture is the true stroke's; what it wrote near the pen is written over at the finish.
        if (!trails) { document.cancel(); full.begin(); }
        if (session != null) session.developing(true);
        return true;
    }
    /**
     * After {@link #lift}: waits up to about {@code nanos} for the render thread to draw the last samples and develop
     * them, then finishes the true stroke on this thread (its tones written to the page). Returns whether more is left.
     * Before lift, nothing.
     */
    @Override public boolean develop(long nanos) {
        if (done) return false;
        synchronized (incoming) {
            if (!lifted) return false;
            long end = System.nanoTime() + Math.min(nanos, Long.MAX_VALUE / 4);
            try {
                for (long wait; !rendered && (wait = end - System.nanoTime()) > 0; ) incoming.wait(wait / 1_000_000, (int)(wait % 1_000_000));
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); return true; }
            if (!rendered) return true;
            if (failed != null) throw Cores.rethrow(failed);
        }
        long t0 = System.nanoTime();
        replayTilt = liftTilt;
        changed = full.finish();
        done = true;
        // Pastels and Dry brush over wet paint: their marks join it (WatercolorSession#lifted).
        if (session != null) session.lifted(document, r, pts);
        finishNanos = System.nanoTime() - t0;
        if (Prof.on) Prof.t[Prof.QUICK_FINAL] += finishNanos;
        if (session != null) session.developing(false);
        return false;
    }
    @Override public boolean finish() {
        lift();
        while (develop(Long.MAX_VALUE)) { }
        return changed;
    }
    /** A Drying wash holds still while it is drawn and settles once it is finished. */
    @Override public boolean holdsWetPaint() { return r.settlesAfterLift; }
    /** Its render builds while the pen rests (Stipple's Build-up): it hears a quiet pen too. */
    @Override public boolean buildsWhileHeld() { return full.buildsWhileHeld(); }
    @Override public float ringGuide(float pressure, float tiltX, float tiltY) { return full.ringGuide(pressure, tiltX, tiltY); }
    @Override public int ringMode() { return full.ringMode(); }
    /** How many samples the stroke has had; how many the true stroke has drawn so far. */
    int samples() { synchronized (incoming) { return received; } }
    int rendered() { return fed; }

    /**
     * The preview of one of the brush's marks, the line's or the body's: its shape at each sample as a convex polygon
     * (an ellipse; the hake's thin head), the hull of the last two swept and filled flat. Only what the last shape didn't
     * cover is new (a sliver ahead, and behind where the mark grows); each page row's new pixels gather into at most two
     * spans, written at {@link #flush}.
     */
    private final class Sweep extends FootprintSweep {
        final int[] base, tones;
        /**
         * How it is filled: {@link #SOLID}, the flat tone at every pixel, which the panel's own dots show as they show the
         * final's even tones (speed plan F5: a wash's and a stick's preview so match 56–90% of the final's dots, against
         * 27–43% as the half and quarter dithers they had); or, for what only hints at where it goes (a smudge, a brush
         * run dry, Float, a pattern), an even dither of dots on every fourth pixel ({@link #SPARSE}), each dark enough that
         * the dots average the flat tone; or a crayon's line ({@link #TOOTH}), its whole ink on the grain's peaks the
         * pressure reaches, so it looks waxy and lies where the final's dots will; or a charcoal or pastel stick's line
         * ({@link #GRAIN}), the final's own contact rule at the core of the stick, by its grain and the pen's pressure now.
         */
        final int fill;
        /** Each tone the gesture found → this mark's dot over it ({@link ModularCompose#flatTone}, through the dither); Gradient's grey changes it ({@link #shade}). */
        final int[] dot = new int[256];
        /** Grain: the line's darkness ({@link ResolvedBrush#inkK}) at the brush's grey, Gradient's too ({@link #shade}). */
        double inkK;
        /** Grain: the stick's grain, the final's ({@link ModularCompose}'s, by the pen's seed). */
        final StickGrain grain;
        /** Grain: the final's contact rule, and how far the stick's low and mid grain move its coverage (LineStamper's dab). */
        final ModularCompose.Contact contact;
        final double grainLow, grainMid;
        /** Grain on the lattice: a big stick's ({@code inkSize} past {@link ModularStroke#INK_LATTICE_PX}); each lattice column's last coverage, height and lattice row, and the ink it laid over the tone it last met. */
        final boolean latticed;
        final float[] cAt, hgtAt, inkAt;
        final int[] cRow, uAt;
        /** How many pixels a dot stands for: 1 or 4 ({@link #fill}). */
        private final int share;
        /**
         * Screen: the flat mark's darkness over white, through the screen's threshold at each pixel (no dither of its
         * own). A transparent body's is its glaze where it lies {@link #SCREENED_BODY} on (its soft edge and streaks
         * averaged), not Drying's light preview grey, so fewer of its dots change at lift.
         */
        final double screened;
        /**
         * An ink screen (Ben-Day, Rosette, Halftone and the rest but the tones): its flat mark shown as black dots in the
         * panel's own pattern for the grey the screen's dots average ({@link #screened}, {@link DotPattern}), not the
         * screen's dots, which it would work out pixel by pixel on the pen's thread: a Ben-Day brush past 60 px fell
         * 0.1–1.3 s behind the pen on the Manta (2026-10-24). Black or nothing, as the screen's. Its own dots come with the
         * true stroke behind the pen and at lift. {@code flatGray} the grey whose pattern it is.
         */
        final boolean flatScreen;
        final int flatGray;
        /** Paint Load: a brush that has run out, shown as a smudge is, the light wash's dither over what it passes ({@link #paint}). */
        final boolean emptied;

        /**
         * Placed lines ({@link #placedParts}): where they lie ({@link Placement.Geometry}, as the true stroke lays them), and
         * which of them this sweep shows: line {@code part / 2}, a split's side {@code part % 2}; else line {@code part}.
         * Null and −1 for any other mark.
         */
        private final Placement.Geometry placed;
        private final int part;
        private final double[] cutX = new double[MOST], cutY = new double[MOST];

        Sweep(boolean isLine, StrokePaper paper) { this(isLine, paper, false, -1); }
        Sweep(boolean isLine, StrokePaper paper, boolean emptied) { this(isLine, paper, emptied, -1); }
        Sweep(boolean isLine, StrokePaper paper, boolean emptied, int part) {
            super(QuickStroke.this.r, QuickStroke.this.pts, document.width, document.height, isLine, paper, fillOf(isLine, emptied) == SPARSE);
            this.emptied = emptied;
            this.part = part;
            placed = part < 0 ? null : new Placement.Geometry(paper, r.pen, r.placeBody, pts, r.inkSize, r.placeSize, false, null, document.width, document.height);
            boolean stick = r.stick(), smears = r.carry != null && r.carry.moves && r.load < .5;
            screened = r.screen == null ? 0 : !isLine && r.cover <= 0 && !smears ? r.screenTone(0, SCREENED_BODY * r.parts.bodyAmt)
                    : 1 - ModularCompose.ownFlat(r, 255, isLine, !isLine || r.body != null) / 255.0;
            fill = fillOf(isLine, emptied);
            flatScreen = r.screen != null && !r.screen.tone && fill == SOLID && opaque == null;
            flatGray = (int) Math.round(255 * (1 - screened));
            share = fill == SPARSE ? 4 : 1;
            grain = fill == GRAIN ? StickGrain.of(r.pen.seed) : null;
            contact = fill == GRAIN ? new ModularCompose.Contact(r) : null;
            grainLow = 1.2 * r.pen.fuzz; grainMid = 0.5 * r.pen.fuzz;
            latticed = fill == GRAIN && r.inkSize >= ModularStroke.INK_LATTICE_PX;
            cAt = latticed ? new float[document.width / 2 + 1] : null;
            hgtAt = latticed ? new float[document.width / 2 + 1] : null;
            cRow = latticed ? new int[document.width / 2 + 1] : null;
            inkAt = latticed ? new float[document.width / 2 + 1] : null;
            uAt = latticed ? new int[document.width / 2 + 1] : null;
            if (latticed) java.util.Arrays.fill(cRow, -1);
            shade(r);
            base = new int[document.width]; tones = new int[document.width];
        }

        /**
         * Placed lines: sample {@code k}'s line (as {@link Placement#stamp} lays it at the sample: its nib's ellipse, turned
         * as the pen's angle, where it is placed, half a pixel wider for its soft edge's lighter dots), one side of a split's gap (the ellipse cut along the stroke where the
         * gap's ink falls under half); none where Chaos skips it, before a second sample, or where the gap takes it all.
         */
        @Override int shapeAt(int k, double[] xs, double[] ys) {
            if (placed == null) return super.shapeAt(k, xs, ys);
            if (k < 1) return 0;
            placed.steps(k);
            boolean split = placed.kind == ResolvedBrush.Lines.SPLIT;
            if (!placed.at(k, 1) || !placed.line(split ? part / 2 : part)) return 0;
            ellipse(placed.cx, placed.cy, placed.a + .5, placed.bb + .5, placed.cosA, placed.sinA, xs, ys);
            if (!split) return SIDES;
            // The gap: across the stroke, within g · 0.72 of the line's long half-axis, softened over 0.14 of it.
            double gap = (placed.g * .72 - .07) * placed.a;
            if (gap <= 0) return part % 2 == 0 ? SIDES : 0;
            double nx = -placed.diry, ny = placed.dirx, side = part % 2 == 0 ? 1 : -1;
            return cut(xs, ys, SIDES, side * nx, side * ny, side * (nx * placed.cx + ny * placed.cy) + gap, cutX, cutY);
        }

        /** Its dots as {@code at} lays them: the brush at another grey (Gradient), or its own. */
        void shade(ResolvedBrush at) {
            inkK = at.inkK;
            // White and Invert: each tone under the mark (not the layer's) → the dot over it, which may be lighter.
            for (int u = 0; u < 256; u++) {
                int flat = flatScreen ? 0 : emptied ? (int)Math.round(ModularCompose.previewWash(u, at.previewShade)) : ModularCompose.flatTone(at, u, isLine, !isLine || at.body != null);
                // Tooth: the line's whole ink, on the grain's peaks only, so its dots average the flat tone.
                dot[u] = (int)Math.max(0, Math.min(255, Math.round(u - (u - flat) * (fill == TOOTH ? 1 / ModularCompose.STICK_SHARE : share))));
            }
        }

        /**
         * Writes row {@code y}'s spans in the flat tone over what the gesture found there, where it darkens what the layer
         * shows (opaque paint: wherever it differs), and empties them: one write for the row.
         */
        void write(int y) {
            int a = lo[y], b = hi[y];
            boolean two = lo2[y] <= hi2[y];
            if (two) { a = Math.min(a, lo2[y]); b = Math.max(b, hi2[y]); }
            int n = b - a + 1;
            java.util.Arrays.fill(tones, 0, n, -1);
            boolean any = tones(y, a, lo[y], hi[y]);
            if (two) any |= tones(y, a, lo2[y], hi2[y]);
            lo[y] = lo2[y] = Integer.MAX_VALUE; hi[y] = hi2[y] = -1;
            if (!any) return;
            if (opaque != null) document.coverTones(a, y, n, 1, tones, opaque);
            else document.setTones(a, y, n, 1, tones);
            // Trail: these tiles show the preview until the true stroke is written there again.
            if (previewed != null) {
                int row = (y / ToneDocument.TILE) * ((document.width + ToneDocument.TILE - 1) / ToneDocument.TILE);
                for (int tx = Math.max(0, a) / ToneDocument.TILE; tx <= Math.min(document.width - 1, b) / ToneDocument.TILE; tx++) previewed[row + tx] = true;
            }
        }
        /**
         * Grain: the final's contact rule over tone {@code u} at the stick's core, its coverage {@code press} before the
         * grain; the tone it lays. A big stick's ({@link #latticed}) is read once for each 2 × 2 pixels, as a big stroke's
         * lattice reads its buffers ({@link ModularStroke#INK_LATTICE_PX}), so it costs about what a flat tone did.
         */
        private int grainTone(int x, int y, int u, double press) {
            if (!latticed) return tone(u, contact.preview(grainC(x, y, press), 1 - u * (1 / 255.0), grainHeight(x, y)));
            int lx = x >> 1, ly = y >> 1;
            if (cRow[lx] != ly) { cRow[lx] = ly; cAt[lx] = (float)grainC(lx << 1, ly << 1, press); hgtAt[lx] = (float)grainHeight(lx << 1, ly << 1); uAt[lx] = -1; }
            // Over the same tone as the point's last pixel (blank paper, mostly): its ink again.
            if (uAt[lx] != u) { uAt[lx] = u; inkAt[lx] = (float)contact.preview(cAt[lx], 1 - u * (1 / 255.0), hgtAt[lx]); }
            return tone(u, inkAt[lx]);
        }
        /** Grain: the tone {@code ink} lays over {@code u}. */
        private int tone(int u, double ink) {
            return (int)(u * (1.0 - ink * inkK) + .5);
        }
        /** Grain: the stick's coverage at {@code x, y}, {@code press} through its low and mid grain (LineStamper's dab at its core). */
        private double grainC(int x, int y, double press) {
            return press * StrokeMath.clamp(1 + grainLow * (grain.low(x, y) - 0.5) + grainMid * (grain.mid(x, y) - 0.5), 0.2, 1.2);
        }
        /** Grain: the height the contact rule reads at {@code x, y}, the stick's grain and the paper's. */
        private double grainHeight(int x, int y) {
            return contact.height(grain.fine(x, y) * .65 + grain.mid(x, y) * .35, paper.fine(x, y) * .65 + paper.mid(x, y) * .35);
        }
        /** Row {@code y}'s pixels {@code from..to} into {@link #tones} (from {@code a}): its dots; whether any changes. */
        boolean tones(int y, int a, int from, int to) {
            int n = to - from + 1;
            document.strokeBaseTones(from, y, n, 1, base);
            boolean covers = !isLine && !emptied && !flatScreen && r.cover > 0, any = false;
            // The first dot: every pixel; or the even columns of even rows (SPARSE).
            int first = fill != SPARSE ? 0 : from & 1, stride = fill != SPARSE ? 1 : 2;
            ResolvedBrush.BlendMode mode = r.blendMode;
            // Tooth: the peaks the wax reaches at the pen's pressure now (Crayon.wax: tooth past 1.08 − press).
            double peaks = fill == TOOTH ? CRAYON_PEAKS - pts.p[Math.max(0, swept)] : 0;
            // Grain: the stick's core as LineStamper lays it at the pen's pressure now (its press, the hand's darkness).
            double press = fill == GRAIN ? StrokeMath.lerp(1, 0.3 + 0.7 * nib.pr * nib.pr, r.pen.fuzz) * nib.dk : 0;
            PrintScreen screen = flatScreen ? null : r.screen;
            boolean prints = r.prints();
            for (int i = first; i < n; i += stride) {
                if (fill == GRAIN) {
                    // Only where its ink darkens the tone found, and what the layer shows now.
                    int x = from + i, want = grainTone(x, y, base[i], press);
                    if (want >= base[i] || mode == ResolvedBrush.BlendMode.BEHIND && under != null && ModularCompose.blank(under.tone(x, y)) < .5) continue;
                    if (want < document.tone(x, y)) { tones[from - a + i] = want; any = true; }
                    continue;
                }
                int x = from + i, now = document.tone(x, y);
                if (flatScreen && DotPattern.pixel(flatGray, x, y) != 0xff000000) continue;
                // Screen over a pixel its dot can't darken (inked already): no need to work out the screen there (speed plan §5.1).
                if (screen != null && opaque == null && now <= (screen.tone ? Math.min(base[i], PrintScreen.DOT_TONE) : 0)) continue;
                // Screen: its dots where the flat mark inks, as the compose lays them, built on the print tone there
                // (the preview writes none: it is as the stroke found it); nothing elsewhere.
                if (screen != null && !screen.ink(prints ? 1 - Math.round(PrintScreen.printed(document.printTone(x, y), screened, covers ? r.cover : 0)) / 255.0 : screened, x, y, document.height)) continue;
                // White and Invert: over the page under it, shown whole (opaque); Behind: only where the page is blank.
                if (opaque != null) {
                    int u = under != null ? under.tone(x, y) : base[i], want = screen == null ? dot[u] : mode == ResolvedBrush.BlendMode.WHITE ? 255 : 255 - u;
                    if (want != now || document.opacity(x, y) < 255) { tones[from - a + i] = want; any = true; }
                    continue;
                }
                if (fill == TOOTH && Crayon.tooth(x, y) < peaks) continue;
                if (mode == ResolvedBrush.BlendMode.BEHIND && under != null && ModularCompose.blank(under.tone(x, y)) < .5) continue;
                int want = screen == null ? dot[base[i]] : screen.tone ? Math.min(base[i], PrintScreen.DOT_TONE) : 0;
                if (covers && screen == null ? want != now : want < now) { tones[from - a + i] = want; any = true; }
            }
            return any;
        }
    }

    /**
     * The convex polygon {@code xs, ys} ({@code n} corners) cut to where {@code nx · x + ny · y >= c}, in place; returns
     * its corners (at most n + 1, 0 for none); {@code ox, oy} its scratch.
     */
    static int cut(double[] xs, double[] ys, int n, double nx, double ny, double c, double[] ox, double[] oy) {
        int m = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double si = nx * xs[i] + ny * ys[i] - c, sj = nx * xs[j] + ny * ys[j] - c;
            if (si >= 0) { ox[m] = xs[i]; oy[m] = ys[i]; m++; }
            if (si >= 0 != sj >= 0) {
                double t = si / (si - sj);
                ox[m] = xs[i] + (xs[j] - xs[i]) * t; oy[m] = ys[i] + (ys[j] - ys[i]) * t; m++;
            }
        }
        if (m < 3) return 0;
        System.arraycopy(ox, 0, xs, 0, m); System.arraycopy(oy, 0, ys, 0, m);
        return m;
    }
    /**
     * How many sweeps a brush's placed lines take in the preview ({@link Sweep#placed}): one a line, two a split's (a side
     * of its gap each); 0 for a line that isn't placed (one sweep: its nib's, a pattern's, a smudge's) or Float's.
     */
    static int placedParts(ResolvedBrush r) {
        if (!r.line || !r.linePlaced || r.pattern != null || r.flt != null) return 0;
        ResolvedBrush.Lines kind = r.pen.lines;
        return kind == ResolvedBrush.Lines.SPLIT ? 2 : kind == ResolvedBrush.Lines.SPREAD ? r.pen.count : kind == ResolvedBrush.Lines.OUTLINE ? 2 : 1;
    }

    /** How a mark's preview is filled ({@link Sweep#fill}). */
    private static final int SOLID = 0, SPARSE = 2, TOOTH = 3, GRAIN = 4;
    /**
     * A crayon's line ({@link #TOOTH}): its whole ink where {@link Crayon#tooth} is at least this less the pen's travel
     * now, nothing between ({@link Crayon#wax} takes the tooth past 1.08 − its press; 1.1 matched the final's inked
     * pixels within 13% from travel .6 to .9 at 60 px, sharing 85–94% of them), so the preview has the wax's broken,
     * grainy look, lightens with a light hand and lies on the dots the final lays; not the flat grey a stick shows,
     * which read as one dark band at any pressure (the owner, 2026-10-22: "the preview for crayon is pretty poor").
     * Preview only: the final is the stroke's own.
     */
    static final double CRAYON_PEAKS = 1.1;
    /** How the line's ({@code isLine}) or the body's preview is filled ({@link Sweep#fill}), Paint Load's {@code emptied} one too. */
    private int fillOf(boolean isLine, boolean emptied) {
        boolean smudge = isLine && !r.line;
        if (r.screen == null && (emptied || r.flt != null || r.pattern != null || smudge)) return SPARSE;
        if (!isLine || r.screen != null || r.blendMode.lays()) return SOLID;
        return r.rule == ResolvedBrush.Rule.CRAYON && !r.scratch ? TOOTH : r.rule == ResolvedBrush.Rule.CHARCOAL && r.lineTip == ResolvedBrush.LineTip.NIB && !r.linePlaced ? GRAIN : SOLID;
    }
}
