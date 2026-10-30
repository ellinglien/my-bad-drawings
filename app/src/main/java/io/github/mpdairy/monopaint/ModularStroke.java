package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.empty;
import static io.github.mpdairy.monopaint.StrokeMath.grow;
import static io.github.mpdairy.monopaint.StrokeMath.union;

/**
 * A six-part brush's stroke from pen-down to lift (stroke.js Stroke), on the document's active layer. Life decides
 * when the mark is final: None is final as laid; Fast-wet keeps the last few millimetres behind the nib developing
 * while the pen is down (the line's tail re-tapers, the body re-settles) and changes nothing at lift; Drying shows its
 * body as a preview while the pen is down and at lift lays the line again with its taper out, lays the body again and
 * settles it (with the line where they meet), then composes the whole stroke. One undo step. Pure, checked on the
 * desktop.
 */
final class ModularStroke implements LiftingStroke {
    /** Fast-wet: the body re-settles at least once an e-ink frame of pen time (stroke.js WET_FRAME_MS), the line's tail at most every 50 ms. */
    static final long WET_FRAME_MS = 330, RETAPER_MS = 50, BIG_RETAPER_MS = 150;
    /** Fast-wet: the least pen time between two body settles that the distance rule asks for. */
    static final long SETTLE_MIN_MS = 150;
    /**
     * A paint's stroke (wash or cover: washes, hakes, opaque paint) whose marks reach wider than this ({@link #reach},
     * page pixels) is worked out on every other pixel each way, a quarter of them, and shown stretched back
     * ({@link FloatTiles#expand}): the same formulas at the same page places, at half the resolution. Below it every
     * pixel is worked out. On the Manta, 100 px is where wide washes stopped keeping up with the pen (the bench's
     * threshold sweep, 110 to 200); from 50 px they render about twice as fast, their edges a pixel softer, as past 100
     * already (the audit's finding 16, the owner's call, 2026-10-19). The bench varies it.
     */
    static double latticePx = 50;
    /**
     * Ink, charcoal and graphite (the ink layer's media) start the lattice no sooner than this: their lines and grain are
     * worked out on every pixel under 100 px, as before ({@link #latticeFrom}).
     */
    static final double INK_LATTICE_PX = 100;
    /** Where {@code brush}'s strokes start the lattice ({@link #latticePx}): a pen's no sooner than {@link #INK_LATTICE_PX}. */
    static double latticeFrom(ModularBrush brush) { return ModularSizes.inks(brush) ? Math.max(latticePx, INK_LATTICE_PX) : latticePx; }
    private final ToneDocument document;
    private final ResolvedBrush r;
    private final StrokePaper paper;
    private final ModularCompose compose;
    /**
     * A live crayon's wax ({@link ResolvedBrush#waxes}): composed onto the masking fluid as it is, off the page, each time
     * the stroke composes, so its lift only writes it ({@link #layWax}) instead of composing the whole stroke again (the
     * owner, 2026-10-29: a long delay before the line showed). Null for any other stroke, a render, Drying or a fill.
     */
    private StrokeTones waxTones;
    private ModularCompose waxCompose;
    /**
     * A quick stroke's render ({@link #render}): its tones, composed off the page as it goes (on its own thread) and
     * written to the page at {@link #finish}; null for a stroke drawn live, which composes onto the page.
     */
    private StrokeTones tones;
    /**
     * Trail ({@link #trail}): the tiles ({@link ToneDocument#TILE}) a quick stroke's preview wrote since its true tones
     * were last written there; null for a stroke whose tones wait for {@link #finish} to be written.
     */
    private boolean[] previewed;
    /** The last samples are in ({@link #lastSamples}); the lift's work is done ({@link #liftWork}). */
    private boolean sampled, developed;
    /** A live stroke's lift running off the pen's thread ({@link #lift}), or null. */
    private java.util.concurrent.FutureTask<Void> lifting;
    /** A Drying lift's rectangle and the line, body and relief it composes ({@link #liftBuffers}); null before. */
    private int[] liftOut;
    private FloatTiles liftLine, liftBody, liftRelief;
    /** A render's Drying lift composed off the page ({@link #develop}), to write over its tones; null for none. */
    private StrokeTones liftTones;
    /** Receives a Drying wash's settling after lift, or null for none. */
    private final WatercolorSession session;
    /** When a Fast-wet re-settle shows ({@link WatercolorSession#pace}); null: at once (no session, or a quick stroke's render). */
    private final SettlePacer pace;
    private final StrokePoints pts = new StrokePoints();
    /** The line's coverage, the body as laid and the body settled. */
    private final FloatTiles L, W, D;
    /** The lattice the stroke's buffers hold: 0, every pixel; 1, a big stroke's even pixels. */
    private final int shift;
    /** With a lattice, the line, body and relief stretched to every pixel for a Drying lift's compose; made at the lift. */
    private FloatTiles shownL, shownBody, shownRelief;
    /** Each worker's ({@link Cores}) row of scratch for {@link FloatTiles#expand}. */
    private final float[][] stretchRows = new float[Cores.WORKERS][];
    /** Impasto's relief and the blend's saved line and body, made the first time a Drying lift or a Fast-wet settle needs them. */
    private FloatTiles relief, lineSave, bodySave;
    /** Fast-wet: the line and body as laid, without the window's work, and each body pixel's distance of its latest dab (−1 for none); made on first use. */
    private FloatTiles rawL, rawW, wetD;
    private final LineStamper.Meta mL = new LineStamper.Meta(), mW = new LineStamper.Meta();
    /** Stipple's Build-up: its ticks of pen time while the pen rests or lingers; null without it. */
    private final StippleStamper.Holds holds;
    /** Stamp: the ink its holder's last stroke left ({@link HeldPaint#stampInk}), and whose it is (null: a sample's, fresh, kept by none). */
    private final StampStamper.Ink stampInk;
    private final String inkedBy;
    /** Spiro: the pen has lifted ({@link #lastSamples}), its stamper's lift's work to do. */
    private boolean spiroLift;
    /** The lift of a hake whose start still waits for its direction ({@link HakeRibbon#settle}): laid now ({@link #lastSamples}). */
    private boolean hakeLift;
    /** The arrays Fast-wet's settles and Drying's lift reuse; made on first use, dropped at finish. */
    private StrokeMath.Scratch scratch;
    /** {@link #settleRows}: made on first use, dropped at finish. */
    private float[][] settleRows;
    private final boolean drying, wet;
    /** Drying: the share of the body's darkness held back at lift to settle after it (stroke.js 672; none for opaque paint). */
    private final double heldBack;
    /** A Drying wash that keeps settling after lift, in {@link #session}; without one (samples) it is laid settled at finish. */
    private final boolean settles;
    /** Fast-wet: the line's dabs before this distance are final (stroke.js frozenD). */
    private double frozenD;
    /** Fast-wet: the pen time the line last re-tapered and the body last settled, the stroke's length at that settle, and whether each has happened yet. */
    private long lineT = PenSpeed.UNTIMED, settleT = PenSpeed.UNTIMED;
    private double settleD;
    private boolean retapered, settled;
    /**
     * Speed plan F7: a render's Fast-wet line puts each re-taper off until it is next composed ({@link #retaperNow}): a
     * line alone (a body would take it in as laid), without splatter, a stick's dust (its specks reach past the box a
     * re-taper lays again), a flick, a smudge, Paint Load or the hand's page tilt (none of which read the line or change
     * a sample after it); the re-taper's sample ({@code retaperAt}, −1 for none).
     */
    private final boolean defersRetaper;
    private int retaperAt = -1;
    /** Off: every render re-tapers at its sample, as before (for the checks). */
    static volatile boolean deferRetapers = true;
    /** Everything the stroke has touched on the page. */
    private final int[] rect;
    /** What the samples since the last {@link #present} changed, still to compose; null for nothing. */
    private int[] pending;
    /**
     * Fast-wet: what re-settles changed and haven't shown yet, null for nothing: composed once the session's pace lets
     * settling show ({@link SettlePacer#mayShow}), and at lift at the latest. The pen's own dabs never wait.
     */
    private int[] pendingSettle;
    /** The first sample not yet stamped. */
    private int done = 1;
    /**
     * The darkness a Drying body keeps wet at lift, {@code laid(d · washK)} ({@link ModularSizes#laid}) before the held-back share was taken,
     * over {@link #pendingRect} ({left, top, right, bottom}, inclusive) row by row; null when nothing settles. For the
     * settling after lift: {@link #finish} hands it to {@link DryingWater#lay} and drops it; the water keeps it only
     * until its first step has kept the cells the wash reached, so this box-sized array outlives the lift by a tick.
     */
    float[] pendingWet;
    int[] pendingRect;
    /** Behind: how blank the page was at each pixel of {@link #pendingRect}, where its settling may land ({@link DryingWater}); null for the others. */
    float[] pendingLands;
    /** How often the Fast-wet line window re-tapered its tail (for the checks). */
    int retapers;
    /**
     * Gravity: the page's tilt ({@link WatercolorSession#tilt}; level without a session), and whether this stroke reads
     * it: Quick's window with gravity dials, a Slow wash that moves with it after lift, the hand's page tilt, dust.
     */
    private final PageTilt.Source tilt;
    /** Inkwash: the kinds its gestures begin with, and its settling and Halo after it ({@link #kinds}). */
    private final int kind;
    private final boolean readsTilt, handTilt;
    /** Smudge: the page it moves and its reservoir; null for Off (and opaque Lift, which only tints its paint). */
    private CarryPlane plane;
    private Carry carry;
    /**
     * A stump's other half ({@link #alsoBlends}): the selected pair's wash layer, which it blends too, its plane and
     * reservoir there and what its dabs reached; −1 and null for any other stroke. Its dabs are the stroke's, sample by
     * sample; what they moved there is laid at {@link #finish}, a gesture of its own linked to the stroke's (a gesture
     * binds one layer), as the eraser lays its wash.
     */
    private final int washLayer;
    /** Inkwash: the kinds of the wash layer's paint it blends: its wash plane's when it has one ({@link ToneDocument#PLANE_WASH}), else its base (0). */
    private final int washKind;
    private CarryPlane washPlane;
    private Carry washCarry;
    private int[] washRect;
    /** Keeps Paint: whose brush's paint the smudge starts with and keeps at finish ({@link WatercolorSession#holder}); null for none. */
    private final String heldBy;
    /** Clear Water: the box of a wash still wet when the stroke began ({@link WatercolorSession#stillWet}), or null. */
    private final int[] stillWet;
    /**
     * Paint Load ({@link LoadedPaint}): the stroke's paint, spent sample by sample, and whose brush it is (it starts with
     * what that holder's brush holds, and leaves it what is left, {@link HeldPaint#load}); null for a brush that never runs out.
     */
    private final LoadedPaint.Run paint;
    private final String loadedBy;
    /**
     * Gradient ({@link GreyGradient}): the grey changing along the stroke from its own, the stroke laid at the darkest it
     * reaches and each sample's share of that written as it comes; null for a brush whose grey stays.
     */
    private final GreyGradient gradient;
    /** Blend: the page under a Behind or Invert stroke as its gesture found it ({@link #under}); null for the others. */
    private ModularCompose.Under under;
    /**
     * Float: the bath it floats on (the session's, or for a sample one of its own, printed at lift) and its pen on the
     * water; null for the other Drying choices. A Float stroke stamps its line and body as Instant does but composes
     * nothing: they float as films at lift ({@link FloatPen}).
     */
    private final FloatWater water;
    private final FloatPen floatPen;
    /** The samples' box since the last show under the pen ({@link #floatFrame}), or null; whether the lift's films are laid. */
    private int[] nearPen;
    private boolean floated;
    /** A quick stroke's render ({@link #render}), not drawn live. */
    private final boolean renders;
    /**
     * Fill ({@link #fill}): the region this stroke lays its brush over, instead of along samples; null for a stroke. Its
     * marks stay inside it: the settled body, its relief and the line are kept to it, and so is the settling after lift.
     */
    private FillRegion.Room room;
    /** A fill's stroke ({@link #fill}): its line and body laid once over its room, never along samples. */
    private boolean filled;
    /**
     * Stay in Lines ({@link ResolvedBrush#staysIn}): the shape the stroke started in and its flood ({@link StayIn}),
     * found at its first sample; its {@link #room} too, so its marks are kept to it as a fill's are. Null without one
     * (another brush, open paper, a render), and once looked for, {@link #stayLooked}.
     */
    private StayIn stay;
    private boolean stayLooked;
    /** Flick and Fling: the pen's motion watched for a moving lift or a hard stop ({@link FlickFling}); null when the brush has neither. */
    private final FlickFling flickFling;

    /**
     * {@code brush} at {@code size} page pixels and {@code darkness} 0–1 on the document's active layer;
     * {@code session} receives a Drying wash's settling, or null to have it laid settled at finish (samples).
     */
    ModularStroke(ToneDocument document, ModularBrush brush, double size, double darkness, WatercolorSession session) { this(document, brush, size, darkness, session, null); }
    /** As {@link #ModularStroke(ToneDocument, ModularBrush, double, double, WatercolorSession)} on {@code paper} rather than the brush's (null: the brush's). */
    ModularStroke(ToneDocument document, ModularBrush brush, double size, double darkness, WatercolorSession session, StrokePaper paper) {
        this(document, brush, size, darkness, session, paper, null);
        begin();
    }
    /**
     * A quick stroke's true render ({@link QuickStroke}): as {@link #ModularStroke(ToneDocument, ModularBrush, double,
     * double, WatercolorSession)}, reading the page's tilt from {@code tilt} (the tilt each sample was drawn at), its
     * gesture not yet begun ({@link #begin}), so it may take the samples on another thread while the quick preview
     * still holds the page, and unpaced. It composes off the page ({@link StrokeTones}, the page's base read shared:
     * {@link ToneDocument#shareBase}), on that thread, as often as {@link #present} is called there; {@link #develop}
     * does the rest there; {@link #finish} writes it to the page.
     */
    static ModularStroke render(ToneDocument document, ModularBrush brush, double size, double darkness, WatercolorSession session, PageTilt.Source tilt) {
        return new ModularStroke(document, brush, size, darkness, session, null, tilt);
    }
    /** The stroke, its gesture begun by the caller; {@code tilt} null: live (the session's tilt, paced), else a {@link #render}. */
    private ModularStroke(ToneDocument document, ModularBrush brush, double size, double darkness, WatercolorSession session, StrokePaper paper, PageTilt.Source tilt) {
        this.document = document; this.session = session;
        renders = tilt != null;
        pace = session == null || tilt != null ? null : session.pace;
        gradient = GreyGradient.of(brush, darkness, session == null ? null : session.swatches);
        r = ResolvedBrush.of(brush, size, gradient != null ? gradient.ref : darkness);
        // Pastels or a dry brush joining wet paint lays on its plane, to sink into it (WatercolorSession#joinsWet).
        int joins = session == null ? -1 : session.joiningKinds();
        kind = joins >= 0 ? joins : kinds(document, r);
        this.paper = paper != null ? paper : new StrokePaper(r.paper);
        // A smudge's render composes its plane off the page as any render does, its writes kept as smears (each pixel with
        // its coverage floor); what composes after them at finish (a Drying lift, Clear Water's run-out) composes on the page.
        // A resist's composes on the page at finish: wax lays its sheen and then the resist, on two layers. And a crayon's, its wax.
        // A print over prints (ResolvedBrush.replaces) clears its plane between its dots: kept as smears are.
        boolean smears = r.carry != null && r.carry.moves || r.replaces();
        tones = tilt != null && r.flt == null && !r.resist && !r.waxes() ? smears ? StrokeTones.smeared(document) : new StrokeTones(document) : null;
        compose = new ModularCompose(tones != null ? tones : ModularCompose.of(document), r, this.paper);
        // Wax: the stroke lays its sheen on the ink layer, then the resist on the masking fluid ({@link #finish}).
        if (r.resist && !fluid(r)) compose.sheen(true);
        int w = document.width, h = document.height;
        // Stipple's dots and Hatch's lines are a pixel or two across: worked out on every pixel at any size.
        shift = reach(r) > latticeFrom(brush) && r.pattern == null ? 1 : 0;
        holds = r.pattern != null && (r.pattern.stipple() && r.pattern.hold > 0 || r.pattern.handHatch()) ? new StippleStamper.Holds() : null;
        // A stamp carries on with the ink its holder's last stroke left, unless the pen has rested since.
        inkedBy = r.pattern != null && r.pattern.stamp() && session != null ? session.holder() : null;
        stampInk = inkedBy != null ? session.held.stampInk(inkedBy) : StampStamper.Ink.FRESH;
        L = buffer(0); W = buffer(0); D = buffer(0);
        rect = StrokeMath.emptyRect(w, h);
        drying = r.life == ResolvedBrush.Life.DRY; wet = r.life == ResolvedBrush.Life.WET;
        heldBack = r.heldBack;
        settles = r.settlesAfterLift;
        this.tilt = tilt != null ? tilt : session != null ? session.tilt : () -> PageTilt.LEVEL;
        handTilt = handTilt(r);
        readsTilt = readsTilt(r);
        if (readsTilt && session != null) session.strokeReadsTilt(true);
        // A render may not read the page (the quick preview holds it): its plane is filled for it ({@link #reachCarry}).
        if (r.flt != null) {
            water = session != null ? session.floatWater(document, this.paper) : new FloatWater(document, this.paper);
            water.floats(r.erase, kind);
            water.bath.begin();
            floatPen = new FloatPen(r, water.bath, r.pen.seed);
        } else { water = null; floatPen = null; }
        under = under(document, r);
        compose.under(under);
        if (r.waxes() && !renders && !drying && !r.prints()) {
            waxTones = StrokeTones.onMask(document);
            waxCompose = new ModularCompose(waxTones, r, this.paper);
            waxCompose.under(under);
            waxCompose.waxOnMask();
        }
        flickFling = FlickFling.of(r);
        if (r.carry != null && r.carry.moves) {
            plane = new CarryPlane(document, tilt == null);
            // A dirty brush starts with the paint its holder's last stroke kept; Clear Water reads where a wash was still wet.
            heldBy = session != null && r.carry.keepsPaint() ? session.holder() : null;
            stillWet = session != null && r.carry.water() ? session.stillWet(document) : null;
            carry = new Carry(r.carry, plane, heldBy == null ? null : session.held.of(heldBy), stillWet);
            compose.carry(plane);
        } else { carry = null; heldBy = null; stillWet = null; }
        washLayer = carry != null ? alsoBlends(document, r) : -1;
        washKind = washLayer >= 0 && document.hasPlane(washLayer, ToneDocument.PLANE_WASH) ? ToneDocument.KIND_PAINT : 0;
        if (washLayer >= 0) {
            // A clean reservoir of its own: what it softens there is the wash's (a dirty brush's paint is its own layer's).
            washPlane = new CarryPlane(document, tilt == null, washLayer, washKind);
            washCarry = new Carry(r.carry, washPlane);
            washRect = StrokeMath.emptyRect(w, h);
        }
        defersRetaper = deferRetapers && renders && retapers(r) && r.body == null && !(r.pen.splatter > 0) && !(r.pen.fuzz > 0) && flickFling == null && carry == null && r.loaded == null && !handTilt && r.flt == null;
        loadedBy = r.loaded != null && session != null ? session.holder() : null;
        paint = r.loaded != null ? new LoadedPaint.Run(r, r.loaded.startShare(loadedBy == null ? 0 : session.held.load(loadedBy))) : null;
    }
    /** Paint Load: the stroke's paint ({@link LoadedPaint.Run}), or null for a brush that never runs out. */
    LoadedPaint.Run paint() { return paint; }
    /**
     * A quick stroke's render: fills the smudge's plane from the page as the gesture found it where samples within
     * {@code x0..x1, y0..y1} (page px) can reach, before they are handed to the render thread. On the page's thread.
     */
    void reachCarry(double x0, double y0, double x1, double y1) {
        if (plane == null) return;
        double pad = r.carry.r + r.carry.stride + 2 + r.carry.softenReach();
        plane.fill((int)Math.floor(x0 - pad), (int)Math.floor(y0 - pad), (int)Math.ceil(x1 + pad), (int)Math.ceil(y1 + pad));
        if (washPlane != null) washPlane.fill((int)Math.floor(x0 - pad), (int)Math.floor(y0 - pad), (int)Math.ceil(x1 + pad), (int)Math.ceil(y1 + pad));
    }
    /**
     * A quick stroke's render at lift, the pen last at {@code x, y}: a smudging brush's Flick carries it past every sample
     * ({@link FlickFling#carry}), so its plane is filled as far as a flick can reach on the page. Nothing without both.
     */
    void reachFlick(double x, double y) {
        if (plane == null || !(r.flick > 0)) return;
        double far = FlickFling.MOST / pts.zoom;
        reachCarry(x - far, y - far, x + far, y + far);
    }
    /** Pixels the smudge passed over because its plane wasn't filled there (a render reaching past {@link #reachCarry}): 0. */
    int carryMissed() { return (plane == null ? 0 : plane.missed) + (washPlane == null ? 0 : washPlane.missed); }
    /**
     * Begins the stroke's gesture on the page, as erasable as its brush ({@link ResolvedBrush#erase}). Before it, samples lay only into the stroke's own buffers ({@link
     * #sample} touches no page): nothing may be presented or finished.
     */
    void begin() {
        // Inkwash: a stroke on water lays on its bath's plane, as the bath's shows and print do.
        if (fluid(r)) document.beginFluid(1); else document.begin(r.erase, water != null ? water.kinds() : kind);
        // Crayon: wax takes wax, so layers build up and Scratch scrapes them; masking fluid still keeps it out.
        if (r.rule == ResolvedBrush.Rule.CRAYON) document.takesOnWax();
        // Screen: it builds on its layer's print tone, which the layer gets now if it has none.
        if (r.prints()) document.printing();
    }
    /**
     * Whether {@code r}'s stroke is laid on the page's masking fluid as it goes: a resist that lifts in full (masking
     * fluid). One that keeps some (wax) lays its sheen on the ink layer first, then the resist at {@link #finish}.
     */
    static boolean fluid(ResolvedBrush r) { return r.resist && !(r.erase < 1); }
    /**
     * {@code brush} at {@code preset} ({@link ModularSizes#size}) in {@code shade} on the selected pair: ink, charcoal
     * and graphite on its ink layer, wash and cover on its wash layer. {@link InkWash#stroke} starts six-part strokes here.
     */
    static ModularStroke start(ToneDocument document, ModularBrush brush, InkWashSizes.Preset preset, InkWashShades.Shade shade, WatercolorSession session) {
        return start(document, brush, ModularSizes.size(brush, preset), ModularSizes.darkness(shade), session);
    }
    /** As {@link #start(ToneDocument, ModularBrush, InkWashSizes.Preset, InkWashShades.Shade, WatercolorSession)}, at {@code size} page pixels and {@code darkness} 0–1: for samples, which size to their tile. */
    static ModularStroke start(ToneDocument document, ModularBrush brush, double size, double darkness, WatercolorSession session) {
        selectLayer(document, brush);
        prepare(document, ResolvedBrush.of(brush, size, darkness));
        return new ModularStroke(document, brush, size, darkness, session);
    }
    /**
     * Inkwash: before a stroke of {@code r} on the selected layer: what it reads before its gesture begins is the plane it
     * will draw on ({@link #kinds}, {@link ToneDocument#selectKind}). Not on water (Float) or masking fluid.
     */
    static void prepare(ToneDocument document, ResolvedBrush r) { prepare(document, r, null); }
    /** As above; a stroke joining {@code session}'s wet paint ({@link WatercolorSession#joinsWet}) reads that paint's plane. */
    static void prepare(ToneDocument document, ResolvedBrush r, WatercolorSession session) {
        if (r.flt != null || fluid(r)) return;
        int joins = session == null ? -1 : session.joiningKinds();
        document.selectKind(joins >= 0 ? joins : kinds(document, r));
    }
    /**
     * Inkwash: the kinds a stroke of {@code r} on the selected layer begins its gestures with, the plane it works on
     * ({@link ToneDocument#planeOf}): its own paint's; a smudge with none of its own (a finger's Lift, Drag, Smear, the
     * smudge tool's stumps and water) its Medium's, so a stump blends dry media and leaves pen lines crisp and water lifts
     * washes, where the layer has that plane; on a layer without it (paint from before planes) its base, as ever.
     */
    static int kinds(ToneDocument document, ResolvedBrush r) {
        if (r.kind != 0 || r.carry == null || !r.carry.moves) return r.kind;
        int p = ToneDocument.planeOf(r.medium);
        return p >= 0 && document.hasPlane(document.activeLayer(), p) ? r.medium : 0;
    }
    /** Inkwash: the kinds its gestures begin with ({@link #kinds}). */
    int kind() { return kind; }
    /** Selects {@code brush}'s half of the selected pair: the ink layer for ink, charcoal and graphite, the wash layer for wash and cover. */
    static void selectLayer(ToneDocument document, ModularBrush brush) { document.selectLayer(layerOf(document, brush)); }
    /**
     * The layer {@code brush} draws on: the selected pair's ink layer for ink, charcoal and graphite, its wash layer for
     * wash and cover, and for a wet wash ({@link ResolvedBrush#washes}), an Ink wash Ink too, so its washes merge with the
     * others there.
     */
    static int layerOf(ToneDocument document, ModularBrush brush) {
        int pair = InkWashLayers.current(document);
        return ModularSizes.inks(brush) && !ResolvedBrush.washes(brush) ? InkWashLayers.inkOf(document, pair) : InkWashLayers.washOf(document, pair);
    }
    /**
     * The top layer of the page a Blend reads under the stroke ({@link ModularCompose.Under}): Behind, the selected
     * pair's top, so a wash goes behind the pair's own ink and everything below; White and Invert their own layer, the
     * page as it shows there.
     */
    static int underTop(ToneDocument document, ResolvedBrush r) {
        return r.blendMode == ResolvedBrush.BlendMode.BEHIND ? InkWashLayers.inkOf(document, InkWashLayers.current(document)) : document.activeLayer();
    }
    /** The page under a Behind or Invert stroke, or one that lays light ({@link ResolvedBrush#laysLight}), as its gesture finds it ({@link #underTop}); null for the others, which don't read it. */
    static ModularCompose.Under under(ToneDocument document, ResolvedBrush r) {
        ResolvedBrush.BlendMode m = r.blendMode;
        return m == ResolvedBrush.BlendMode.BEHIND || m == ResolvedBrush.BlendMode.INVERT || r.laysLight() ? reading(document, r) : null;
    }
    /**
     * The page under a Blend stroke ({@link #underTop}): Behind's over white, how blank it is being the marks' doing;
     * White's and Invert's on the page's paper (PageShade), as it shows.
     */
    static ModularCompose.Under reading(ToneDocument document, ResolvedBrush r) {
        return new ModularCompose.Under(document, underTop(document, r), r.blendMode == ResolvedBrush.BlendMode.BEHIND ? 255 : ToneDocument.PAGE_PAPER);
    }
    /** Whether a Fast-wet line re-tapers its tail under the pen ({@link #wetLine}): one with a taper out that isn't a hake's. */
    static boolean retapers(ResolvedBrush r) { return r.life == ResolvedBrush.Life.WET && r.line && !r.lineStateful && r.pen.taperOut > 0; }
    /** Whether the hand reads the page's tilt (Gravity: drawn downhill to size, dark or the nib's width), sample by sample. */
    static boolean handTilt(ResolvedBrush r) { return r.pen.gSize != 0 || r.pen.gDark != 0 || r.pen.gWidth != 0; }
    /** Whether a stroke of {@code r} reads the page's tilt: the hand's page tilt, a wash that moves with it (Quick's window, a Slow wash after lift), dust. */
    static boolean readsTilt(ResolvedBrush r) { return handTilt(r) || r.grav != null && r.body != null || r.dust > 0; }
    /**
     * Whether what a stroke of {@code r} changed may be laid again elsewhere as that change ({@link
     * ToneDocument#layAgain}: Copy Strokes' copies, the change reflected) rather than drawn again: its compose only
     * darkens by a factor of its own ({@link ModularCompose#darkensBy}), it is final at lift (nothing settles, grows or
     * floats after) and nothing in it reads which way is down. The copy's paper grain and noise are the stroke's,
     * reflected, not the page's there.
     */
    /**
     * The layer a stroke of {@code r} on {@code document}'s active layer blends besides its own ("blend washes", the
     * owner, 2026-10-19): a stump (a Smear with Soften) on the selected pair's ink layer softens the pair's wash too, the
     * same way, wherever the wash has marks; what it can't lift there (permanent, under masking fluid) stays, as on its
     * own layer ({@link CarryPlane}). −1 for any other stroke, a pair with no wash layer of its own, or a hidden one.
     * A paint's smudge still moves only its own layer: pen lines stay crisp (the Smudge spec, decision A).
     */
    static int alsoBlends(ToneDocument document, ResolvedBrush r) {
        if (r.carry == null || !r.carry.moves || !(r.carry.soften > 0) || r.carry.water() || r.flt != null || r.resist || r.screen != null
                || r.blendMode != ResolvedBrush.BlendMode.OWN) return -1;
        int pair = InkWashLayers.current(document), ink = InkWashLayers.inkOf(document, pair), wash = InkWashLayers.washOf(document, pair);
        return document.activeLayer() == ink && wash != ink && document.layerVisible(wash) ? wash : -1;
    }
    static boolean laysAgain(ResolvedBrush r) {
        return ModularCompose.darkensBy(r) && !r.settlesAfterLift && r.halo == null && r.flt == null && r.fade == null && !readsTilt(r);
    }

    /**
     * How wide the stroke's marks reach at most, page pixels: the line at full pressure with a stick's soft edge, the
     * body, a placed line's spread, bristles splayed, a stipple's scatter; what decides its {@link #shift}.
     */
    static double reach(ResolvedBrush r) { return Math.max(lineReach(r), bodyReach(r)); }
    /** How far {@code r}'s body reaches ({@link #reach}): its size, a bristle body's hairs splayed, a stipple's dots scattered; 0 without one. */
    static double bodyReach(ResolvedBrush r) {
        if (r.body == null) return 0;
        switch (r.bodyTip) {
            case BRISTLES: return BristleSwarm.reach(r.bodySwarm, r.bodySize / 2);
            case STIPPLE: return StippleStamper.reach(r.pattern, r.bodySize);
            case HATCH: return HatchStamper.reach(r.bodySize);
            case STAMP: return StampStamper.reach(r.pattern, r.pattern.size);
            case SPIRO: return SpiroStamper.reach(r.pattern, r.pattern.size);
            case HAND_HATCH: return HandHatchStamper.reach(r.pattern, r.bodySize);
            default: return r.bodySize;
        }
    }
    /** How far {@code r}'s line reaches ({@link #reach}), 0 without one. */
    static double lineReach(ResolvedBrush r) {
        double line = r.line ? r.inkSize * (1 + .6 * r.pen.pressure) * (1 + 1.06 * r.pen.fuzz) : 0;
        if (r.linePlaced) line = Math.max(line, r.placeSize);
        if (r.line && r.lineTip == ResolvedBrush.LineTip.BRISTLES) line = BristleSwarm.reach(r.lineSwarm, r.inkSize / 2);
        if (r.line && r.lineTip == ResolvedBrush.LineTip.STIPPLE) line = StippleStamper.reach(r.pattern, r.inkSize);
        if (r.line && r.lineTip == ResolvedBrush.LineTip.HATCH) line = HatchStamper.reach(r.inkSize);
        if (r.line && r.lineTip == ResolvedBrush.LineTip.STAMP) line = StampStamper.reach(r.pattern, r.pattern.size);
        if (r.line && r.lineTip == ResolvedBrush.LineTip.SPIRO) line = SpiroStamper.reach(r.pattern, r.pattern.size);
        if (r.line && r.lineTip == ResolvedBrush.LineTip.HAND_HATCH) line = HandHatchStamper.reach(r.pattern, r.inkSize);
        return line;
    }
    /** Whether the stroke is worked out on the lattice ({@link #latticeFrom}). */
    boolean onLattice() { return shift > 0; }
    /** A page-sized buffer on the stroke's lattice, filled with {@code fill}. */
    private FloatTiles buffer(float fill) { return new FloatTiles(document.width, document.height, fill, shift); }

    @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY) { sample(x, y, pressure, tiltX, tiltY, PenSpeed.UNTIMED); }
    @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY, long time) {
        // As the other pens: a sample without a position or pressure reading draws nothing.
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(pressure)) return;
        // The hand's page tilt reads which way is down as each sample comes (the Gravity Lab's addPt).
        if (handTilt) { PageTilt g = tilt.now(); pts.gx = g.x; pts.gy = g.y; }
        add(x, y, ToolSettings.travel(pressure), time, tiltX, tiltY);
    }

    /** stroke.js add for one sample, {@code p} the pressure's travel: stamps what is new, to compose at {@link #present}. */
    private void add(float x, float y, float p, long time, float tiltX, float tiltY) {
        boolean kept = pts.add(x, y, p, time, tiltX, tiltY);
        // Stay in Lines: the shape it stays in, found once where the stroke starts.
        if (!stayLooked && r.staysIn() && !renders && room == null && pts.count > 0) stayIn();
        stayReady(false);
        // Paint Load: the paint the brush still has at the new sample, before any stamper reads it.
        if (paint != null && kept) paint.spend(pts);
        // Gradient: the grey there, as a share of the darkest the stroke reaches (over Paint Load's).
        if (gradient != null && kept) gradient.apply(pts, paint != null);
        // Fling: a hard stop held with the pen down throws its drops; they land as the samples' clock reaches them.
        if (flickFling != null) { flickFling.sampled(pts, x, y, time); if (flickFling.landing(time)) land(time); }
        // Stipple's Build-up (and Hatching's): every sample, kept or not, tells the time; a resting pen's ticks lay dots (strokes) without a new point.
        boolean held = holds != null && pts.count > 0 && holds.feed(x, y, p, time, pts.total());
        // Float: a pen held still drops, and keeps spreading, sample by sample (too close to keep or not).
        if (floatPen != null && pts.count > 0) {
            floatPen.held(pts, time);
            nearPen = nearPen == null ? new int[]{(int) x, (int) y, (int) x, (int) y} : union(nearPen, new int[]{(int) x, (int) y, (int) x, (int) y});
        }
        if ((kept && pts.count >= 2) || held) stampNew();
        // Stay in Lines: the flood runs on by pen time, a resting pen's too.
        if (stay != null && r.body != null) flood(stay.sample(pts, time, stayRadius(), stayTint(), flooded()));
    }
    /**
     * Stay in Lines: its shape round the first sample and its flood ({@link StayIn#find}); none on open paper. On the
     * drawing screen and Brush Lab's page (a session) it is found off the pen's thread, so a big shape never stalls the
     * pen ({@link #stayFound} takes it in when ready); a brush on the ink layer itself, and a sample, find it at once.
     */
    private void stayIn() {
        stayLooked = true;
        int x = (int) pts.x[0], y = (int) pts.y[0], ink = FillTool.walls(document);
        double flood = r.stayIn;
        java.util.concurrent.Callable<StayIn> find = () -> StayIn.find(document, ink, x, y, flood);
        if (session == null || document.activeLayer() == ink) {
            try { stayFound(find.call()); } catch (Exception e) { throw Cores.rethrow(e); }
            return;
        }
        stayFinding = new java.util.concurrent.FutureTask<>(find);
        STAY_FINDER.execute(stayFinding);
    }
    /** Stay in Lines' shape found off the pen's thread, or null; waited for at the lift. */
    private java.util.concurrent.FutureTask<StayIn> stayFinding;
    /** One thread finds every stroke's shape, in turn (Mirrors' copies too), so one finder's page-sized arrays serve them all. */
    private static final java.util.concurrent.Executor STAY_FINDER = java.util.concurrent.Executors.newSingleThreadExecutor(job -> {
        Thread t = new Thread(job, "Inkwash stay in lines");
        t.setDaemon(true);
        return t;
    });
    /** Takes in the shape found off the pen's thread once it is ready ({@code wait}: at the lift, waiting for it). */
    private void stayReady(boolean wait) {
        if (stayFinding == null || !wait && !stayFinding.isDone()) return;
        StayIn found;
        try { found = stayFinding.get(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); found = null; }
        catch (java.util.concurrent.ExecutionException e) { throw Cores.rethrow(e.getCause()); }
        stayFinding = null;
        stayFound(found);
    }
    /**
     * Stay in Lines' shape and flood, or null for none: what the stroke laid before it was found is kept to the shape
     * and composed again (the page outside back as the gesture found it), and floods from every sample so far.
     */
    private void stayFound(StayIn found) {
        if (found == null) return;
        stay = found;
        room = found.room;
        if (empty(rect)) return;
        stay.keep(L, rect); stay.keep(wet ? D : W, rect); stay.keep(relief, rect);
        if (pending == null) pending = rect.clone(); else grow(pending, rect);
    }
    /** The body buffer the flood paints, as composed: Fast-wet's shown body, else the body as laid. */
    private FloatTiles flooded() { return wet ? D : W; }
    /** The brush's rim the flood starts from, page px: most of its body's half-width. */
    private double stayRadius() { return r.body == null ? 0 : .45 * r.bodySize; }
    /** How dark the flood lays: the body's darkness at the newest sample, as a firm still press lays it at {@link #fill}. */
    private float stayTint() {
        if (r.body == null || pts.count == 0) return 0;
        ResolvedBrush.Body b = r.body;
        int k = pts.count - 1;
        double pr = pts.p[k];
        return (float)((b.wet ? StrokeMath.lerp(.7, 1, pr) : 1) * b.amount * StrokeMath.handDark(b.pDark, b.sDark, b.tDark, pr, pts.v[k], 0));
    }
    /** What the flood painted ({@code at}, or null), to compose at {@link #present}. */
    private void flood(int[] at) {
        if (at == null) return;
        if (pending == null) pending = at.clone(); else grow(pending, at);
        grow(rect, at);
    }
    /** Stamps what is new since {@link #done} (at the lift, a Spiro's lift too: {@link #spiroLift}), to compose at {@link #present}. */
    private void stampNew() {
        int[] changed = StrokeMath.emptyRect(document.width, document.height), line = null;
        long t0 = Prof.now();
        if (wet) {
            if (r.line) { line = wetLine(); changed = union(changed, line); }
            t0 = lap(Prof.LINE, t0);
            if (r.body != null) { int[] body = wetBody(line); if (body != null) changed = union(changed, body); }
        } else {
            if (r.line) { line = stampL(L, false, done, null, null); changed = union(changed, line); }
            t0 = lap(Prof.LINE, t0);
            if (r.body != null) changed = union(changed, stampW(W, null, false, done));
            t0 = lap(Prof.BODY, t0);
            if (r.life == ResolvedBrush.Life.NONE && r.line && r.body != null) mergeLineWet(line, W, null, 0);
            lap(Prof.MERGE, t0);
        }
        // Smudge: after the marks, before the compose (stroke.js add).
        if (carry != null) {
            long c0 = Prof.now();
            changed = union(changed, carry.pass(pts, done, document.width, document.height));
            if (washCarry != null) washRect = union(washRect, washCarry.pass(pts, done, document.width, document.height));
            Prof.add(Prof.CARRY, c0);
        }
        // Float: the pen pushes aside and combs what floats; its marks wait in its buffers for the lift.
        if (floatPen != null) { floatPen.moved(pts, done); done = pts.count; if (!empty(changed)) grow(rect, changed); return; }
        done = pts.count;
        if (empty(changed)) return;
        if (pending == null) pending = changed.clone(); else grow(pending, changed);
        grow(rect, changed);
    }

    /**
     * Composes what the samples since the last call changed, once: a pixel's tone depends only on what the gesture
     * found and its line, body and relief as they are now, so composing an input event's samples together draws what
     * composing after each would, without composing the rectangles they share again and again.
     */
    @Override public void present() { present(false); }
    @Override public void zoom(float scale) { pts.zoom = scale; }
    /** {@link #present()}; {@code all}: what re-settles changed too, however the pace stands (at lift). */
    private void present(boolean all) {
        // Float: the bath shown quick under the pen, the stroke's own paint over it (live; a render's shows through QuickStroke).
        if (floatPen != null) { if (!renders) { Frame f = floatFrame(); if (f != null) water.write(f.at, f.film, false); } return; }
        retaperNow();
        int[] changed = pending;
        pending = null;
        if (pendingSettle != null && (all || pace.mayShow())) {
            Prof.count(Prof.SHOWN_PX, Prof.area(pendingSettle));
            changed = changed == null ? pendingSettle : union(changed, pendingSettle);
            pendingSettle = null;
            pace.shown();
        }
        if (changed == null) return;
        // Stay in Lines: nothing outside its shape; Fast-wet's settle under the pen may have taken the flood back there.
        if (stay != null) {
            stay.keep(L, changed); stay.keep(wet ? D : W, changed); stay.keep(relief, changed);
            if (wet && r.body != null) stay.relay(D, changed);
        }
        Prof.count(Prof.COMPOSE_PX, Prof.area(changed));
        long t0 = Prof.now();
        compose(L, wet ? D : W, wet ? relief : null, changed, drying ? ModularCompose.Phase.PREVIEW : ModularCompose.Phase.FINAL);
        Prof.add(Prof.COMPOSE, t0);
    }

    /** {@link ModularCompose#compose}; a lattice's buffers composed as stretched, over {@code at} grown by the pixel a lattice point's stretch reaches. */
    private void compose(FloatTiles line, FloatTiles body, FloatTiles relief, int[] at, ModularCompose.Phase phase) {
        compose(compose, line, body, relief, at, phase);
        if (waxCompose != null) compose(waxCompose, line, body, relief, at, phase);
    }
    private void compose(ModularCompose c, FloatTiles line, FloatTiles body, FloatTiles relief, int[] at, ModularCompose.Phase phase) {
        if (shift > 0) c.composeLattice(line, body, relief, grownByStretch(at), phase);
        else c.compose(line, body, relief, at, phase);
    }
    /** {@code at} grown by the pixel a lattice point's stretch reaches, on the page. */
    private int[] grownByStretch(int[] at) {
        return new int[]{Math.max(0, at[0] - 1), Math.max(0, at[1] - 1), Math.min(document.width - 1, at[2] + 1), Math.min(document.height - 1, at[3] + 1)};
    }
    /**
     * With a lattice: the line, body and relief (or null) stretched into {@link #shownL}, {@link #shownBody} and
     * {@link #shownRelief} over {@code at} grown by the pixel a lattice point's stretch reaches; returns that rectangle.
     */
    private int[] stretch(FloatTiles line, FloatTiles body, FloatTiles relief, int[] at) {
        at = grownByStretch(at);
        shownL = stretch(line, shownL, at);
        shownBody = stretch(body, shownBody, at);
        if (relief != null) shownRelief = stretch(relief, shownRelief, at);
        return at;
    }
    /**
     * {@code lattice} stretched over {@code at} into {@code page} (made if null, every pixel); returns {@code page}. A
     * big rectangle is stretched in bands of tile rows on several cores: each band writes only its own tiles.
     */
    private FloatTiles stretch(FloatTiles lattice, FloatTiles page, int[] at) {
        FloatTiles into = page != null ? page : new FloatTiles(document.width, document.height, lattice.fill);
        int y0 = Math.max(0, at[1]), y1 = Math.min(document.height - 1, at[3]);
        Cores.inBands(y0, y1, FloatTiles.SIDE, (long)(at[2] - at[0] + 1) * (y1 - y0 + 1), (top, bottom, worker) -> {
            float[] row = stretchRows[worker];
            if (row == null) stretchRows[worker] = row = new float[2 * document.width + 8];
            lattice.expand(into, new int[]{at[0], top, at[2], bottom}, row);
        });
        return into;
    }

    /** The hand a fill is laid with ({@link #fill}): a firm, even press (pressure's travel), the pen still (px/ms). */
    static final double FILL_PRESSURE = .8, FILL_SPEED = 0;
    /**
     * Fill: lays the brush over {@code room} at once, as a firm, still press lays it in the middle of a stroke, everywhere
     * in the room (its line's coverage and its body's paint, the hand's darkness included); a Fast-wet body settles once,
     * as a Drying lift settles it. Then the stroke composes and finishes as any stroke of its brush does (a Drying one
     * settling at lift and after), its marks kept to the room. The tip's own marks (its edge, taper, dry streaks, a
     * stipple's dots) are a stroke's and aren't laid; the medium, paper, Print Shading, grey and drying are. Before any
     * sample, once; no samples after.
     */
    void fill(FillRegion.Room room) { fill(room, false); }
    /**
     * Filled Shape's fill ({@link FilledShape}): {@link #fill}, its line as dark as the outline's where it lays most (a
     * stick's or a crayon's press, {@link LineStamper#press}), and never darker than the darker of the outline and the
     * fill ({@link ModularCompose#keepDarker}), so outline and inside read as one shape at its grey.
     */
    void fillShape(FillRegion.Room room) { compose.keepDarker(); fill(room, true); }
    private void fill(FillRegion.Room room, boolean shape) {
        if (pts.count > 0 || this.room != null) throw new IllegalStateException("A fill is laid once, before any sample");
        // A fill composes as it lays and lets its buffers go: a crayon's wax composes at the lift, as laid.
        waxTones = null; waxCompose = null;
        this.room = room;
        filled = true;
        int[] box = {room.x0, room.y0, room.x1, room.y1};
        double pr = FILL_PRESSURE, v = FILL_SPEED;
        float line = r.line ? (float)(StrokeMath.handDark(r.pen.pDark, r.pen.sDark, r.pen.tDark, pr, v, 0) * (shape ? LineStamper.press(r.pen, pr) : 1)) : 0, body = 0;
        if (r.body != null) {
            ResolvedBrush.Body b = r.body;
            body = (float)((b.wet ? StrokeMath.lerp(.7, 1, pr) : 1) * b.amount * StrokeMath.handDark(b.pDark, b.sDark, b.tDark, pr, v, 0));
        }
        if (inStrips()) { strips(box, line, body); return; }
        long t0 = Prof.now();
        lay(L, line, room.y0, room.y1);
        lay(W, body, room.y0, room.y1);
        int[] out = box.clone();
        if (r.life == ResolvedBrush.Life.NONE && r.line && r.body != null) mergeLineWet(box, W, null, 0);
        Prof.add(Prof.F_LAY, t0);
        if (wet && r.body != null) {
            t0 = Prof.now();
            grow(out, settleBody(out));
            keepToRoom(out);
            Prof.add(Prof.F_SETTLE, t0);
        }
        grow(rect, out);
        pending = out.clone();
    }
    /**
     * A fill's strip ({@link #strips}): the lattice rows of a band of its settle ({@link BodySettle}), as many bands at a
     * time as there are cores. A band of 64 rows took the least heap on the desktop, no slower than 256 (2026-10-23);
     * the checks set others.
     */
    static int fillBand = 64;
    /**
     * Whether a fill is laid in strips ({@link #strips}): one composed once, as laid. Not Drying (it settles and composes
     * again at lift), wax (composed again on the masking fluid at finish) or Double Print (its second pass reads rows above).
     */
    private boolean inStrips() {
        return !drying && floatPen == null && plane == null && tones == null && !(r.resist && !fluid(r) || r.waxes()) && (r.screen == null || !r.screen.misregisters());
    }
    /**
     * A fill laid, settled (Fast-wet) and composed a strip of rows at a time down the page, each strip's buffers let go
     * once composed (the owner chose "fill in strips", docs/archive/stability-2026-10-22.md §3.3: a page fill held its line,
     * body, settled body and the blend's copies whole, over 100 MB at once on the Manta). Each step runs as far down as
     * what it reads is final: the bands settle once the body is laid (the line loosened in it, its wet laid) through the
     * rows they read, and a row is composed once its line and settled body are. So the page is the one the whole fill at
     * once wrote, byte for byte ({@code FillChecks}).
     */
    private void strips(int[] box, float line, float body) {
        int sh = shift, h = document.height, strip = fillBand * Cores.WORKERS;
        boolean settles = wet && r.body != null, merges = r.life == ResolvedBrush.Life.NONE && r.line && r.body != null;
        BodySettle.Blend blend = null;
        BodySettle.Bands bands = null;
        int[] out = box.clone();
        long t0 = Prof.now();
        if (settles) {
            if (r.look.impasto > 0) relief = buffer(0);
            if (r.line) {
                lineSave = buffer(0); bodySave = buffer(0);
                blend = new BodySettle.Blend(L, W, D, lineSave, bodySave, relief, paper, r, box, fillBand, scratch());
                bands = blend.bands;
                grow(out, blend.region());
            } else {
                bands = BodySettle.bands(W, D, relief, paper, r.look, box, r.bodySize, null, 0, null, fillBand, scratch());
                if (bands != null) grow(out, bands.region);
            }
        }
        Prof.add(Prof.F_SETTLE, t0);
        // What a stroke's compose covers: on a lattice, the pixel its points' stretch reaches.
        int[] all = sh > 0 ? grownByStretch(out) : out;
        FloatTiles shown = wet ? D : W, rel = wet ? relief : null;
        int ahead = blend != null ? blend.reach() << sh : 0, loosens = blend != null ? blend.loosens() << sh : 0;
        // Page rows before which each step is done.
        int laid = 0, mixed = 0, wetted = 0, kept = 0, composed = all[1], freed = 0;
        for (int k = 0, end = 0; end < h; ) {
            // This strip's rows are final before end; they read the laid body before need.
            int need;
            if (bands != null) {
                k = Math.min(bands.count, k + Cores.WORKERS);
                end = k == bands.count ? h : bands.top(k) << sh;
                need = k == bands.count ? h : bands.reads(k) << sh;
            } else end = need = Math.min(h, ((end >> sh) / strip * strip + strip) << sh);
            int to = need < h ? Math.min(h, need + ahead) : h;
            t0 = Prof.now();
            if (to > laid) {
                lay(L, line, laid, to - 1); lay(W, body, laid, to - 1);
                if (merges) mergeLineWet(new int[]{box[0], Math.max(box[1], laid), box[2], Math.min(box[3], to - 1)}, W, null, 0);
                if (blend != null) blend.save(laid, to - 1);
                laid = to;
            }
            Prof.add(Prof.F_LAY, t0); t0 = Prof.now();
            if (blend != null) {
                int m = need < h ? Math.min(h, need + loosens) : h;
                blend.mix(mixed, m - 1); mixed = m;
                blend.wet(wetted, need - 1); wetted = need;
            }
            if (bands != null) bands.settle(k);
            if (settles) { keepToRoom(new int[]{out[0], Math.max(out[1], kept), out[2], Math.min(out[3], end - 1)}); kept = end; }
            Prof.add(Prof.F_SETTLE, t0); t0 = Prof.now();
            // A row reads its lattice row and, between two, the next.
            int last = end < h ? Math.min(all[3], end - 1 - sh) : all[3];
            if (last >= composed) {
                int[] at = {all[0], composed, all[2], last};
                if (sh > 0) compose.composeLattice(L, shown, rel, at, ModularCompose.Phase.FINAL);
                else compose.compose(L, shown, rel, at, ModularCompose.Phase.FINAL);
                composed = last + 1;
            }
            Prof.add(Prof.F_COMPOSE, t0);
            // Above what any step still reads, the buffers let go, a tile row at a time.
            int low = composed >> sh;
            if (bands != null) low = Math.min(low, bands.from(k) - 4);
            // The loosening reads the saved line and body that far above the rows it has still to finish.
            if (blend != null) low = Math.min(low, (mixed - 2 * loosens) >> sh);
            low = Math.max(0, low) >> FloatTiles.SHIFT << FloatTiles.SHIFT << sh;
            if (low > freed) {
                int[] behind = {0, freed, document.width - 1, low - 1};
                for (FloatTiles t : new FloatTiles[]{L, W, D, relief, lineSave, bodySave}) if (t != null) t.clear(behind);
                compose.forget(behind);
                freed = low;
            }
        }
        if (blend != null) blend.done();
        grow(rect, out);
    }
    /** {@code v} at every lattice point of {@code into} in the {@link #room}, at page rows {@code y0..y1}. */
    private void lay(FloatTiles into, float v, int y0, int y1) {
        if (!(v > 0)) return;
        int step = into.step(), x0 = into.up(room.x0), n = room.x1 < x0 ? 0 : (room.x1 - x0) / step + 1;
        float[] row = new float[Math.max(1, n)];
        for (int y = into.up(Math.max(room.y0, y0)); y <= Math.min(room.y1, y1); y += step) {
            boolean any = false;
            // The room's own row: every lattice point here is in its box.
            for (int k = 0, o = (y - room.y0) * room.width + x0 - room.x0; k < n; k++, o += step) { boolean in = room.at[o] != FillRegion.OUT; row[k] = in ? v : 0; any |= in; }
            if (any) into.setRow(x0 >> into.shift, y >> into.shift, n, row, 0);
        }
    }
    /** A fill's line, settled body and relief kept to its {@link #room} over {@code at} (page px): nothing outside it. */
    private void keepToRoom(int[] at) {
        keepToRoom(L, at); keepToRoom(D, at); keepToRoom(relief, at);
    }
    private void keepToRoom(FloatTiles t, int[] at) {
        if (t == null || empty(at)) return;
        int step = t.step(), x0 = t.up(Math.max(0, at[0])), x1 = Math.min(document.width - 1, at[2]), n = x1 < x0 ? 0 : (x1 - x0) / step + 1;
        if (n == 0) return;
        int y0 = Math.max(0, at[1]), y1 = Math.min(document.height - 1, at[3]);
        // Each row alone: in bands of tile rows on several cores.
        Cores.inBands(y0, y1, FloatTiles.SIDE << t.shift, (long)n * (y1 - y0 + 1) << 2 * t.shift, (top, bottom, worker) -> {
            float[] row = new float[n];
            for (int y = t.up(top); y <= bottom; y += step) {
                t.row(x0 >> t.shift, y >> t.shift, n, row, 0);
                boolean changed = false;
                for (int k = 0, x = x0; k < n; k++, x += step) if (row[k] != t.fill && !room.inside(x, y)) { row[k] = t.fill; changed = true; }
                if (changed) t.setRow(x0 >> t.shift, y >> t.shift, n, row, 0);
            }
        });
    }
    private void releaseLift() { lineSave = bodySave = rawL = rawW = wetD = shownL = shownBody = shownRelief = liftLine = liftBody = liftRelief = null; }
    /** Whether this stroke is a fill's ({@link #fill}). */
    boolean fills() { return filled; }
    /** Whether this stroke stays in a shape of the ink lines ({@link #stay}), its shape found. */
    boolean staysIn() { return room != null && !filled; }

    /** Ends section {@code k} begun at {@code since} and starts the next. */
    private static long lap(int k, long since) { Prof.add(k, since); return Prof.now(); }

    /** stampL: the line, by its tip. last = the stroke is lifting; clip and win null for none (the hake, bristles, stipple, hatching, stamp and spiro take neither). */
    private int[] stampL(FloatTiles into, boolean last, int from, int[] clip, LineStamper.Window win) {
        if (r.lineTip == ResolvedBrush.LineTip.SPIRO) return SpiroStamper.stamp(into, null, r.pattern, pts, mL, spiroLift);
        if (r.lineTip == ResolvedBrush.LineTip.STAMP) return StampStamper.stamp(into, null, r.pattern, pts, r.pattern.size, from, mL, stampInk);
        if (r.lineTip == ResolvedBrush.LineTip.STIPPLE) return StippleStamper.stamp(into, null, r.pattern, pts, r.inkSize, from, mL, holds);
        if (r.lineTip == ResolvedBrush.LineTip.HATCH) return HatchStamper.stamp(into, null, r.pattern, pts, r.inkSize, from, mL);
        if (r.lineTip == ResolvedBrush.LineTip.HAND_HATCH) return HandHatchStamper.stamp(into, null, r.pattern, pts, r.inkSize, from, mL, holds);
        if (r.lineTip == ResolvedBrush.LineTip.HAKE) return HakeRibbon.stamp(into, null, paper, r.lineHake, pts, r.inkSize, mL, r.lineHake.softK, last || hakeLift);
        if (r.lineTip == ResolvedBrush.LineTip.BRISTLES) return BristleSwarm.stamp(into, null, paper, r.lineSwarm, pts, r.inkSize / 2, mL);
        if (r.linePlaced) {
            // A split's wet gap goes to the body as laid; Fast-wet's body is rawW, which the prototype's gap never reached (it wrote W, unread in Fast-wet).
            int[] out = Placement.stamp(into, null, r.body != null && !wet ? W : null, paper, r.pen, r.placeBody, pts, r.inkSize, r.placeSize, last, from, win);
            mL.widest = Math.max(mL.widest, r.lineReach);
            return out;
        }
        return LineStamper.stamp(into, paper, r.pen, pts, r.inkSize, last, from, clip, mL, win);
    }
    /** stampW: the body, by its tip; wetD: the Fast-wet distance map, or null. */
    private int[] stampW(FloatTiles into, FloatTiles wetD, boolean last, int from) { return stampW(into, wetD, last, from, null); }
    /** As stampW, a round body laying only inside {@code clip} (null for the page; a placed body takes none). */
    private int[] stampW(FloatTiles into, FloatTiles wetD, boolean last, int from, int[] clip) {
        if (r.bodyTip == ResolvedBrush.BodyTip.PLACED) return Placement.stamp(into, wetD, null, paper, r.bodyPen, r.bodyGeom, pts, r.bodyLineSize, r.bodyPlaceSize, last, from, null);
        if (r.bodyTip == ResolvedBrush.BodyTip.BRISTLES) return BristleSwarm.stamp(into, wetD, paper, r.bodySwarm, pts, r.bodySize / 2, mW);
        if (r.bodyTip == ResolvedBrush.BodyTip.STIPPLE) return StippleStamper.stamp(into, wetD, r.pattern, pts, r.bodySize, from, mW, holds);
        if (r.bodyTip == ResolvedBrush.BodyTip.HATCH) return HatchStamper.stamp(into, wetD, r.pattern, pts, r.bodySize, from, mW);
        if (r.bodyTip == ResolvedBrush.BodyTip.HAND_HATCH) return HandHatchStamper.stamp(into, wetD, r.pattern, pts, r.bodySize, from, mW, holds);
        if (r.bodyTip == ResolvedBrush.BodyTip.STAMP) return StampStamper.stamp(into, wetD, r.pattern, pts, r.pattern.size, from, mW, stampInk);
        if (r.bodyTip == ResolvedBrush.BodyTip.SPIRO) return SpiroStamper.stamp(into, wetD, r.pattern, pts, mW, spiroLift);
        return BodyStamper.stamp(into, wetD, paper, r.body, pts, r.bodySize, from, mW, last || hakeLift && r.body.flatHead, clip);
    }

    /** mergeLineWet (stroke.js 496–505): the line's loose share joins the body {@code into} as it is laid; wetD, if any, takes distance {@code d} where it does. */
    private void mergeLineWet(int[] at, FloatTiles into, FloatTiles wetD, double d) {
        double k = r.parts.lineWet * StrokeMath.LINE_WET_K;
        if (!(k > 0) || empty(at)) return;
        double along = .3 * r.parts.bodyAmt;
        int x0 = Math.max(0, at[0]), x1 = Math.min(document.width - 1, at[2]), step = L.step();
        for (int y = L.up(Math.max(0, at[1])); y <= Math.min(document.height - 1, at[3]); y += step) {
            int ty = L.tileOf(y), row = L.rowAt(y);
            for (int x = L.up(x0); x <= x1; ) {
                int end = Math.min(x1, L.tileEnd(L.tileOf(x)));
                float[] line = L.tile(L.tileOf(x), ty, false);
                // Where the line laid nothing there is nothing to merge.
                if (line == null) { x = end + 1; continue; }
                for (; x <= end; x += step) {
                    float l = line[row | L.at(x)];
                    if (l <= 0) continue;
                    if (into.raise(x, y, (float)(Math.max(into.get(x, y), l * k) + along * l * .25)) && wetD != null) wetD.set(x, y, (float)d);
                }
            }
        }
    }

    /**
     * Fling: the drops that land by {@code now} laid in the stroke's own buffers, so they compose as its medium (a line's
     * coverage, else its body: Fast-wet's as laid and as shown, in its window), at the next {@link #present}.
     */
    private void land(long now) {
        java.util.List<FlickFling.Drop> drops = flickFling.land(now);
        if (drops.isEmpty()) return;
        int[] changed = StrokeMath.emptyRect(document.width, document.height);
        for (FlickFling.Drop d : drops) {
            int[] box = layDrop(d, true);
            if (!empty(box)) changed = empty(changed) ? box : union(changed, box);
        }
        if (empty(changed)) return;
        if (pending == null) pending = changed.clone(); else grow(pending, changed);
        grow(rect, changed);
    }
    /** Lays drop {@code d} where the stroke's marks are: its line, else its body; {@code live}: as the pen draws (Fast-wet's raw buffers too), not a lift laying them again. */
    private int[] layDrop(FlickFling.Drop d, boolean live) {
        if (r.line) {
            int[] box = FlickFling.lay(L, null, d, 1, 0);
            // A Fast-wet tail laid again copies its raw line back: the drop goes there too.
            if (live && rawL != null) FlickFling.lay(rawL, null, d, 1, 0);
            return box;
        }
        if (r.body == null) return StrokeMath.emptyRect(document.width, document.height);
        if (!wet) return FlickFling.lay(W, null, d, r.body.amount, 0);
        // Fast-wet: the body as laid (in the window, to settle with it) and as shown.
        if (rawW == null) { rawW = buffer(0); wetD = buffer(-1); }
        FlickFling.lay(rawW, wetD, d, r.body.amount, pts.total());
        return FlickFling.lay(D, null, d, r.body.amount, 0);
    }

    /**
     * Fast-wet's line window (stroke.js 506–533): dabs further back than the taper are final; the tail is re-tapered at
     * most every 50 ms of pen time, 150 ms for a line over 64 px or on the lattice (without times, every sample), and in between new
     * dabs are laid as they come. No
     * taper out (or the hake's ribbon): each dab is laid once.
     */
    private int[] wetLine() {
        double total = pts.total(), prev = frozenD;
        if (!retapers(r)) {
            int[] out = stampL(L, false, done, null, null);
            if (r.pen.splatter > 0) LineStamper.splatterLive(L, r.pen, pts, r.inkSize, done, out);
            return out;
        }
        double outLen = StrokeMath.taperLens(r.pen.taperIn, r.pen.taperOut, r.inkSize)[1];
        double dW = Math.max(0, total - Math.min(r.windowLen, outLen + 4));
        long now = pts.t[pts.count - 1];
        // Without times the tail re-tapers every sample.
        // A wide line's tail, many times the pixels, re-tapers at most every BIG_RETAPER_MS.
        if (retapered && now != PenSpeed.UNTIMED && lineT != PenSpeed.UNTIMED && now - lineT < (shift > 0 || r.inkSize > 64 ? BIG_RETAPER_MS : RETAPER_MS)) {
            LineStamper.Window win = new LineStamper.Window(); win.dMin = frozenD;
            return stampL(L, false, done, null, win);
        }
        lineT = now; retapered = true; retapers++;
        // A render's re-taper waits for its next compose (retaperNow), its dabs laid meanwhile as between re-tapers; not
        // the first two samples' (the second sets the first's direction).
        if (defersRetaper && pts.count > 2) {
            retaperAt = pts.count - 1;
            LineStamper.Window win = new LineStamper.Window(); win.dMin = frozenD;
            return stampL(L, false, done, null, win);
        }
        return retaper(prev, dW, outLen);
    }
    /** {@link #wetLine}'s re-taper at the newest sample: the window's frozen dabs from {@code prev} to {@code dW} laid raw, the tail laid again tapered. Returns what it changed. */
    private int[] retaper(double prev, double dW, double outLen) {
        long t0 = Prof.now();
        if (rawL == null) rawL = buffer(0);
        if (dW > prev) {
            LineStamper.Window win = new LineStamper.Window(); win.dMin = prev; win.dMax = dW;
            stampL(rawL, false, pts.firstAt((float)prev), null, win);
            frozenD = dW;
        }
        if (r.pen.splatter > 0) LineStamper.splatterLive(rawL, r.pen, pts, r.inkSize, done, StrokeMath.emptyRect(document.width, document.height));
        int reach = Math.max(Math.max(mL.widest, (int)Math.ceil(r.inkSize * 0.9) + 2), r.lineReach) + 2;
        int[] C = boxOf(Math.max(0, pts.firstAt((float)prev) - 1), reach + (r.pen.splatter > 0 ? 50 : 0));
        L.copy(rawL, C);
        int k = pts.count - 1;
        double pk = pts.pk[k] != 0 ? pts.pk[k] : pts.p[k], v = pts.v[k];
        double fall = pk > 0 ? clamp((0.75 * pk - pts.p[k]) / (0.45 * pk)) : 0, still = 1 - clamp(v / 0.3), flick = clamp((v - 1.2) / 2);
        double amt = r.pen.taperOut * Math.max(fall, Math.max(still, flick)), len = Math.min(Math.max(1, outLen), r.windowLen * 0.85);
        LineStamper.Window win = new LineStamper.Window(); win.dMin = dW;
        if (amt > 0) { win.tail = true; win.tailLen = len; win.tailAmt = amt; }
        stampL(L, false, pts.firstAt((float)dW), C, win);
        Prof.add(Prof.RETAPER, t0);
        return C;
    }
    /**
     * Speed plan F7: a render's re-taper put off ({@link #defersRetaper}) done now, before a compose, as it would have
     * been at its sample: the samples since hidden from it, then their dabs laid again as between re-tapers. A
     * Fast-wet line is the re-taper the pen's time last called for and the dabs since (the one before lays nothing
     * that it doesn't lay again: its frozen dabs are frozen again, its tail is copied back and laid again), so the
     * line, and every compose of it, is as if each had been done at its sample; one a compose is spared.
     */
    private void retaperNow() {
        if (retaperAt < 0) return;
        int at = retaperAt, count = pts.count;
        retaperAt = -1;
        double prev = frozenD, total = pts.d[at];
        double outLen = StrokeMath.taperLens(r.pen.taperIn, r.pen.taperOut, r.inkSize)[1];
        double dW = Math.max(0, total - Math.min(r.windowLen, outLen + 4));
        int[] C;
        pts.count = at + 1;
        try { C = retaper(prev, dW, outLen); } finally { pts.count = count; }
        if (at + 1 < count) {
            LineStamper.Window win = new LineStamper.Window(); win.dMin = frozenD;
            C = union(C, stampL(L, false, at + 1, null, win));
        }
        if (empty(C)) return;
        if (pending == null) pending = C.clone(); else grow(pending, C);
        grow(rect, C);
    }

    /** The samples from {@code k0} on, padded by {@code pad}, on the page (stroke.js boxOf). */
    private int[] boxOf(int k0, double pad) {
        double l = Double.POSITIVE_INFINITY, t = Double.POSITIVE_INFINITY, rt = Double.NEGATIVE_INFINITY, b = Double.NEGATIVE_INFINITY;
        for (int k = k0; k < pts.count; k++) {
            double x = pts.x[k], y = pts.y[k];
            if (x < l) l = x;
            if (x > rt) rt = x;
            if (y < t) t = y;
            if (y > b) b = y;
        }
        return new int[]{Math.max(0, (int)Math.floor(l - pad)), Math.max(0, (int)Math.floor(t - pad)),
                Math.min(document.width - 1, (int)Math.ceil(rt + pad)), Math.min(document.height - 1, (int)Math.ceil(b + pad))};
    }

    /**
     * Fast-wet's body window (stroke.js 540–594): the body is laid raw; the last windowLen px are settled once 330
     * ms of pen time has passed, or when the stroke has grown by 0.3 of the window but no sooner than 150 ms after the
     * last settle, or by 0.7 (without times, by distance only), and in
     * between the raw body shows inside the window. Returns the rectangle to compose, or null.
     */
    private int[] wetBody(int[] lineRect) {
        int w = document.width, h = document.height;
        double total = pts.total(), len = r.windowLen, dW = Math.max(0, total - len);
        if (rawW == null) { rawW = buffer(0); wetD = buffer(-1); }
        FloatTiles raw = rawW;
        long t0 = Prof.now();
        int[] got = stampW(raw, wetD, false, done);
        t0 = lap(Prof.BODY, t0);
        if (r.line && lineRect != null) { mergeLineWet(lineRect, raw, wetD, total); got = union(got, lineRect); }
        t0 = lap(Prof.MERGE, t0);
        long s0 = pace == null ? 0 : pace.now();
        int[] out = settleWet(raw, got, total, len, dW);
        Prof.add(out == got ? Prof.WET_RAISE : Prof.WET_SETTLE, t0);
        if (out == got || out == null || pace == null) return out;
        // Paced: the settle's box shows when the pace allows (present); the stroke's own dabs now.
        pace.worked(pace.now() - s0, SettlePacer.RESETTLE_BUDGET_MS);
        if (pendingSettle == null) pendingSettle = out.clone(); else grow(pendingSettle, out);
        grow(rect, out);
        return empty(got) ? null : got;
    }

    /** wetBody's raise or settle of the window: returns {@code got} when it only raised, the settled box otherwise, or null. */
    private int[] settleWet(FloatTiles raw, int[] got, double total, double len, double dW) {
        int w = document.width, h = document.height;
        long now = pts.t[pts.count - 1];
        double grown = total - settleD;
        // Settles: the first time; once WET_FRAME_MS of pen time has passed; when the stroke has grown by 0.3 of the
        // window, but not within SETTLE_MIN_MS of the last (a quick pen grows that much every few samples, and each
        // settle redoes the whole window); and always by 0.7, so nothing leaves the window unsettled. Without times,
        // by distance alone.
        boolean timed = now != PenSpeed.UNTIMED && settleT != PenSpeed.UNTIMED;
        boolean due = !settled || (timed ? now - settleT >= WET_FRAME_MS || grown >= 0.3 * len && now - settleT >= SETTLE_MIN_MS || grown >= 0.7 * len
                : grown >= 0.3 * len);
        if (!due) {
            if (empty(got)) return null;
            int x0 = Math.max(0, got[0]), x1 = Math.min(w - 1, got[2]), y0 = Math.max(0, got[1]), y1 = Math.min(h - 1, got[3]);
            // In bands of tile rows, each raising only its own tiles of D: a big one on several cores.
            Cores.inBands(y0, y1, FloatTiles.SIDE << shift, (long)(x1 - x0 + 1) * (y1 - y0 + 1), (top, bottom, worker) -> raise(raw, x0, x1, top, bottom));
            return got;
        }
        // A settle further than 0.3 of the window past the last reads ahead by the difference: the paint that leaves the
        // window before the next settle has then developed as far as the distance rule alone would have let it.
        double ahead = settled ? Math.max(0, grown - 0.3 * len) : 0;
        settleT = now; settleD = total; settled = true;
        Prof.count(Prof.SETTLES, 1);
        ResolvedBrush.Look look = r.look;
        double cw = 1 - clamp(look.cover);
        double hol = look.hollow * cw, blo = look.bloom * cw, gr = look.gran, fe = look.feather * cw, br = look.backrun * cw, imp = look.impasto;
        int fr = (int)Math.round(2 + 12 * fe);
        // A bristle body's hairs splay past the body's size, a stipple's dots scatter past it: the box takes in their reach.
        boolean wider = r.bodyTip == ResolvedBrush.BodyTip.BRISTLES || r.bodyTip == ResolvedBrush.BodyTip.STIPPLE;
        double half = wider ? Math.max(r.bodySize * 0.55, bodyReach(r) / 2) : r.bodySize * 0.55;
        int[] box = boxOf(Math.max(0, pts.firstAt((float)dW) - 1), half + fr + 12);
        if (!empty(got)) grow(box, got);
        // The box's lattice points: x0, y0 the first, rw × rh of them; px, py below are page pixels.
        int x0 = raw.up(box[0]) >> shift, y0 = raw.up(box[1]) >> shift, rw = (box[2] >> shift) - x0 + 1, rh = (box[3] >> shift) - y0 + 1;
        if (rw <= 0 || rh <= 0) return null;
        // The stroke's scratch, slot by slot: the body as laid, its part inside the window (blurred in place into the
        // bleed), the blurs' rows, the hollow's blur and impasto's.
        int n = rw * rh;
        Prof.count(Prof.SETTLE_PX, n);
        StrokeMath.Scratch sc = scratch();
        float[] P = sc.get(0, n), Wt = sc.get(1, n), tmp = sc.get(2, n), Bh = null, Bf = null, Bp = null;
        // The box in bands of lattice tile rows: each writes only its own tiles of D, wetD and relief, so a big box's
        // bands run on several cores, each worker reading, changing and writing back a row at a time in its own rows.
        // Its area on the page: four pixels a lattice point.
        long area = (long)n << (2 * shift);
        float[][] rows = settleRows(rw);
        Cores.inBands(y0, y0 + rh - 1, FloatTiles.SIDE, area, (top, bottom, worker) -> {
            float[] when = rows[3 * worker];
            for (int y = top - y0; y <= bottom - y0; y++) {
                raw.row(x0, y + y0, rw, P, y * rw);
                wetD.row(x0, y + y0, rw, when, 0);
                for (int x = 0, j = y * rw; x < rw; x++, j++) Wt[j] = when[x] >= dW ? P[j] : 0;
            }
        });
        if (hol > 0) { Bh = sc.get(3, n); StrokeMath.blur(StrokeMath.blur(P, rw, rh, raw.radius(3 + (int)Math.round(4 * hol)), tmp, Bh), rw, rh, raw.radius(2), tmp, Bh); }
        if (fe > 0) { Bf = Wt; StrokeMath.blur(StrokeMath.blur(Wt, rw, rh, raw.radius(fr), tmp, Bf), rw, rh, raw.radius(Math.max(1, fr >> 1)), tmp, Bf); }
        if (imp > 0) Bp = StrokeMath.blur(P, rw, rh, raw.radius(Math.max(2, (int)Math.round(3 * StrokeMath.paintBig(r.bodySize)))), tmp, sc.get(4, n));
        if (imp > 0 && relief == null) relief = buffer(0);
        FloatTiles relief = this.relief;
        float[] Bhf = Bh, Bff = Bf, Bpf = Bp;
        // Gravity in the window: runs, pools and sinks downhill while the page tilts (null when level, or no dials).
        GravityFlow.Window gw = r.grav == null ? null : GravityFlow.window(r.grav, tilt.now(), r.bodySize, shift);
        Cores.inBands(y0, y0 + rh - 1, FloatTiles.SIDE, area, (top, bottom, worker) -> settleBand(raw, relief, P, Bhf, Bff, Bpf,
                rows[3 * worker], rows[3 * worker + 1], rows[3 * worker + 2], x0, y0, rw, rh, top - y0, bottom + 1 - y0, total, ahead, len, dW, hol, blo, gr, fe, br, imp, gw));
        return box;
    }

    /** Each worker's ({@link Cores}) three rows for {@link #settleWet}: distances, the settled body and relief, at least {@code rw} long. */
    private float[][] settleRows(int rw) {
        if (settleRows == null || settleRows[0].length < rw) {
            settleRows = new float[3 * Cores.WORKERS][];
            for (int i = 0; i < settleRows.length; i++) settleRows[i] = new float[Math.max(rw, 256)];
        }
        return settleRows;
    }

    /** wetBody's raise of page rows {@code ya..yb}, columns {@code x0..x1}: body laid since the last settle shows as laid where it is stronger than shown. */
    private void raise(FloatTiles raw, int x0, int x1, int ya, int yb) {
        int step = raw.step();
        for (int y = raw.up(ya); y <= yb; y += step) {
            int ty = raw.tileOf(y), row = raw.rowAt(y);
            for (int x = raw.up(x0); x <= x1; ) {
                int tx = raw.tileOf(x), end = Math.min(x1, raw.tileEnd(tx));
                float[] laid = raw.tile(tx, ty, false), when = wetD.tile(tx, ty, false), shown = D.tile(tx, ty, false);
                // Where no body was laid it can't rise.
                if (laid == null || when == null) { x = end + 1; continue; }
                for (; x <= end; x += step) {
                    int i = row | raw.at(x);
                    float p = laid[i];
                    // Only paint laid since the last settle rises (the prototype raised every pixel of the rectangle
                    // still in the window, so settled paint behind the nib went back to raw and, once the window had
                    // passed, stayed raw: flat half-moons in a quick, wide stroke).
                    if (p > 0 && when[i] > settleD && p > (shown == null ? D.fill : shown[i])) {
                        if (shown == null) shown = D.tile(tx, ty, true);
                        shown[i] = p;
                    }
                }
            }
        }
    }

    /** {@link #settleWet}'s rows {@code ya..yb − 1} of its box (from lattice row y0), with one worker's rows {@code when}, {@code shown} and {@code lit}. */
    private void settleBand(FloatTiles raw, FloatTiles relief, float[] P, float[] Bh, float[] Bf, float[] Bp, float[] when, float[] shown, float[] lit,
                            int x0, int y0, int rw, int rh, int ya, int yb, double total, double ahead, double len, double dW,
                            double hol, double blo, double gr, double fe, double br, double imp, GravityFlow.Window gw) {
        for (int y = ya; y < yb; y++) {
            int ly = y + y0, py = ly << shift;
            boolean bled = false;
            wetD.row(x0, ly, rw, when, 0); D.row(x0, ly, rw, shown, 0);
            if (Bp != null) relief.row(x0, ly, rw, lit, 0);
            for (int x = 0; x < rw; x++) {
                int j = y * rw + x, px = (x + x0) << shift;
                double p = P[j], wd = when[x];
                boolean bleedOnly = false;
                if (wd < dW) {
                    if (!(p <= 0 && wd < 0 && Bf != null && Bf[j] > 0.02)) continue;
                    bleedOnly = true; wd = total; when[x] = (float)total; bled = true;
                }
                double age = clamp((total + ahead - wd) / (0.7 * len)), d = p;
                if (Bh != null && p > 0) d = d * (1 - 0.6 * hol * age) + Math.max(0, p - Bh[j]) * hol * 5 * age;
                // The paper's noises, each read once a pixel (NaN until read).
                float low = Float.NaN, mid = Float.NaN;
                if (Bf != null && (bleedOnly || Bf[j] > p)) {
                    low = paper.low(px, py); mid = paper.mid(px, py);
                    double reach = clamp((Bf[j] - (0.42 - 0.3 * fe) - 0.45 * (low - 0.5) - 0.25 * (mid - 0.5)) * 6), f = reach * Bf[j] * 1.4 * fe * age;
                    if (f > d) d = f;
                }
                if (d <= 0) { shown[x] = 0; continue; }
                if ((blo > 0 || br > 0) && low != low) low = paper.low(px, py);
                if ((blo > 0 || gr > 0) && mid != mid) mid = paper.mid(px, py);
                if (blo > 0) d *= clamp(1 + blo * 2.6 * age * (low - 0.5) + blo * 0.5 * age * (mid - 0.5), 0.25, 2);
                if (br > 0) {
                    double th = 0.7 - 0.05 * br, nn = 0.55 * paper.blob(px, py) + 0.45 * low, inside = clamp((nn - th) * 40), e = (nn - th) / 0.02;
                    // Past twelve widths the ring adds nothing a double can hold.
                    double ring = e * e > 144 ? 0 : Math.exp(-(e * e));
                    d = d * (1 - 0.4 * br * age * inside) + d * br * 0.9 * age * ring;
                }
                if (gr > 0) d *= 1 + gr * age * (1.5 * (paper.fine(px, py) - 0.5) + 0.6 * (mid - 0.5));
                if (gw != null) {
                    if (mid != mid) mid = paper.mid(px, py);
                    d *= gw.factor(P, rw, rh, x, y, j, age, gw.sink > 0 ? paper.fine(px, py) : .5f, mid);
                }
                shown[x] = (float)clamp(d, 0, 1.6);
                if (Bp != null && x > 0 && y > 0 && x < rw - 1 && y < rh - 1) {
                    double h0 = Math.min(1, P[j]), ridge = Math.min(1, P[j + rw + 1]) - Math.min(1, P[j - rw - 1]);
                    // On the lattice the neighbours are twice as far apart: the same slope rises twice as much.
                    if (shift > 0) ridge /= 1 << shift;
                    lit[x] = (float)(imp * (100 * ridge - 90 * Math.max(0, h0 - Bp[j])));
                }
            }
            D.setRow(x0, ly, rw, shown, 0);
            if (bled) wetD.setRow(x0, ly, rw, when, 0);
            if (Bp != null) relief.setRow(x0, ly, rw, lit, 0);
        }
    }

    /**
     * A render's ({@link #render}) work up to writing the page, on its thread once it has drawn the last sample: a tap's
     * dot, the last compose and, for Drying, the lift's line, body, settle and compose. Touches only the stroke's buffers
     * and tones; {@link #finish} writes them to the page.
     */
    /** Whether a render composes off the page as it goes ({@link #tones}): all but Float's, a resist's and a crayon's. */
    boolean composesOffPage() { return tones != null; }
    /**
     * Trail (the audit's finding 17): a render whose tones are final behind the pen, Instant everywhere and Fast-wet
     * outside its window ({@link #frontier}), writes them there while the pen is down ({@link #writeBehind}) and at
     * {@link #finish} only the tiles that changed since or that the preview marks in {@code previewed}; its gesture is
     * the preview's, begun as its own ({@link #begin}). Drying too (speed plan F9): behind the pen its line and body as
     * laid (the body's light grey while wet), which its lift then lays again, settled, over all of them as it would over
     * them written at lift; a halo's growth reads the page as the gesture found it either way. A smudge too, its plane
     * composed as it goes. Not Float, a fill or a resist (which compose on the page). Whether this stroke trails, from
     * now on. Before any sample.
     */
    boolean trail(boolean[] previewed) {
        if (!renders || tones == null || r.life == ResolvedBrush.Life.FLOAT || floatPen != null || room != null) return false;
        tones.trails();
        this.previewed = previewed;
        return true;
    }
    /**
     * Trail, on the render's thread after a {@link #present}: the stroke's length from which its marks may still change
     * (beside the samples to come): Instant and Drying (whose body holds still while the pen is down), the newest
     * sample's; Fast-wet, where its window starts (a re-tapering line's frozen dabs, if they end sooner).
     */
    double frontier() {
        double total = pts.total();
        if (!wet) return total;
        double f = Math.max(0, total - r.windowLen);
        return retapers(r) ? Math.min(f, frozenD) : f;
    }
    /** Trail: how far past its samples a stroke's marks, a Fast-wet window's settle and a lattice's stretch may change the page, page px. */
    double trailReach() { return Math.max(reach(r), sideReach(r)) + 32 + (r.pen.splatter > 0 ? 50 : 0); }
    /**
     * How far {@code r}'s line reaches laid on its side (Tilt Shading), as wide as it goes; its {@link #lineReach}
     * without. Only how far its marks may change the page: the lattice and quick strokes go by the upright line, so an
     * upright mark is drawn as before the brush had Tilt Shading.
     */
    static double sideReach(ResolvedBrush r) { return r.line && r.pen.side > 0 ? lineReach(r) * (1 + ResolvedBrush.SIDE_WIDTH * r.pen.side) : lineReach(r); }
    /**
     * Trail, on the page's thread while nothing composes ({@link QuickStroke}): the tones composed so far written to the
     * page wherever they changed since last written or the preview wrote, but in the tiles touching {@code away} (page
     * px, inclusive; null: none), where the marks may still change; at most {@code most} tiles (the rest the next
     * time). Returns how many tiles it wrote.
     */
    int writeBehind(int[] away, int most) { return tones.writeTiles(document, previewed, away, most); }
    void develop() {
        if (floatPen != null) { floatLift(); return; }
        lastSamples();
        // A smudge's Drying lift composes on the page at finish, over its plane, as a live one's does.
        liftWork(plane == null);
    }
    /** The samples' last part: a tap's dot, and what is still to compose. Once. */
    private void lastSamples() {
        if (sampled) return;
        sampled = true;
        // Flick: a moving lift carries the stroke on, its own samples past the lift; a lift just after a hard stop flings.
        if (flickFling != null) {
            FlickFling.Carry c = flickFling.lift(pts);
            if (c != null) for (int i = 0; i < c.n; i++) add(c.x[i], c.y[i], c.p[i], c.t[i], c.tx, c.ty);
            land(Long.MAX_VALUE);
        }
        // A tap is a dot: a second point just beside the first.
        if (pts.count == 1) add(pts.x[0] + .8f, pts.y[0], pts.p[0], pts.t[0] == PenSpeed.UNTIMED ? PenSpeed.UNTIMED : pts.t[0] + 16, pts.tx[0], pts.ty[0]);
        // A Spiro's ring catches up with the pen, or, never left, draws its whole figure.
        if (r.pattern != null && r.pattern.spiro() && floatPen == null && pts.count >= 2) { spiroLift = true; stampNew(); }
        // A hake lifted before its direction settled lays its start now, along the way it went.
        if (floatPen == null && pts.count >= 2 && (waits(r.lineTip == ResolvedBrush.LineTip.HAKE, mL) || waits(r.body != null && r.body.flatHead, mW))) { hakeLift = true; stampNew(); }
        // Stay in Lines: its shape (waited for), and the flood run to its end.
        stayReady(true);
        if (stay != null && r.body != null) { flood(stay.sample(pts, PenSpeed.UNTIMED, stayRadius(), stayTint(), flooded())); flood(stay.finish(flooded())); }
        present(true);
    }
    /** Whether a hake ({@code hake}) with state in {@code m} still waits to lay its start. */
    private static boolean waits(boolean hake, LineStamper.Meta m) { return hake && m.hake != null && Double.isNaN(m.hake.th); }
    /**
     * Drying's lift in the stroke's own buffers ({@link #liftBuffers}) and, {@code offPage}, composed off the page too
     * ({@link #liftTones}), as composing on the page over what the stroke wrote there would: every pixel of the
     * rectangle, those left with nothing taken back when written ({@link StrokeTones#applyTo}). Once.
     */
    private void liftWork(boolean offPage) {
        if (developed) return;
        developed = true;
        long t0 = Prof.now();
        if (drying && !empty(rect)) {
            liftOut = liftBuffers();
            if (offPage) {
                liftTones = r.replaces() ? StrokeTones.smeared(document) : new StrokeTones(document);
                ModularCompose off = new ModularCompose(liftTones, r, paper);
                off.under(under);
                if (r.resist && !fluid(r)) off.sheen(true);
                off.release();
                liftCompose(off);
            }
        }
        Prof.add(Prof.LIFT, t0);
    }

    /**
     * A live stroke lifts off the pen's thread ({@link LiftingStroke}) when its lift has work to do there: a Drying
     * stroke on the drawing screen or Brush Lab's page (one with a session to settle in), its line laid again, body
     * settled and composed; not a smudge, which composes on the page.
     */
    @Override public boolean developsAfterLift() { return tones == null && plane == null && session != null && drying && !empty(rect) && lifting == null; }
    @Override public boolean screened() { return r.screen != null; }
    /**
     * The pen lifted from a live Drying stroke: the last samples composed on the page here, then the lift's line, body,
     * settle and compose on a thread of its own, off the page (reading the page as the stroke found it, shared:
     * {@link ToneDocument#shareBase}); {@link #finish} writes it. The page reads as wet meanwhile
     * ({@link WatercolorSession#developing}). False with nothing to do there.
     */
    @Override public boolean lift() {
        if (lifting != null) return !lifting.isDone();
        if (!developsAfterLift()) return false;
        lastSamples();
        document.shareBase(true);
        lifting = new java.util.concurrent.FutureTask<>(() -> liftWork(true), null);
        Thread t = new Thread(lifting, "Inkwash lift");
        t.setDaemon(true);
        t.start();
        session.developing(true);
        return true;
    }
    /** After {@link #lift}: waits up to about {@code nanos} for the lift's work; whether it is still running. */
    @Override public boolean develop(long nanos) {
        if (lifting == null) return false;
        try { lifting.get(Math.max(0, nanos), java.util.concurrent.TimeUnit.NANOSECONDS); }
        catch (java.util.concurrent.TimeoutException e) { return true; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return true; }
        catch (java.util.concurrent.ExecutionException e) { throw Cores.rethrow(e.getCause()); }
        return false;
    }

    @Override public boolean finish() {
        if (floatPen != null) return finishFloat();
        // Fading and Lemon Juice: the Undo steps from here on are the mark that changes by the clock from now; without a
        // session to tick it (a sample) it stays as laid.
        boolean fades = r.fade != null && session != null;
        Object fadeFrom = fades ? document.lastStep() : null;
        if (lifting != null) { while (develop(Long.MAX_VALUE)) { } session.developing(false); }
        else if (tones != null) develop();
        else { lastSamples(); liftWork(false); }
        long t0 = Prof.now();
        // Nothing reads the page's base off this thread any more (a render's, a lift's).
        document.shareBase(false);
        // A render: its tones, composed off the page, written as composing the stroke once here would write them; one that
        // trails, only where they changed since written behind the pen, or the preview wrote.
        if (tones != null && previewed != null) { tones.writeTiles(document, previewed, null, Integer.MAX_VALUE); tones.release(); }
        else if (tones != null) tones.applyTo(document, null);
        // A smudge's render: what composes from here on (its Drying lift, Clear Water's run-out) composes on the page.
        if (tones != null && plane != null) compose.onto(ModularCompose.of(document));
        // A Drying lift composed off the page goes over them, as a live one's over its preview; else it composes here.
        if (liftTones != null) liftTones.applyTo(document, liftOut);
        else if (liftOut != null) liftCompose(compose);
        tones = liftTones = null;
        lifting = null;
        // Clear Water: what it lifted runs out through its wet, a Slow one's share kept wet to settle after lift.
        if (plane != null && r.carry.water() && !empty(rect)) runOut();
        // Charcoal's dust falls downhill of the stroke at lift, while the line's coverage is still here.
        // (Not a fill's: it would fall outside the lines.)
        if (r.dust > 0 && !empty(rect) && room == null) dust(tilt.now());
        Prof.add(Prof.LIFT, t0);
        // Halo: what its growth reads after the gesture (its layer's tiles as the stroke found them), and where it laid.
        java.util.Map<Integer, byte[]> found = r.halo != null && !empty(rect) ? document.foundTiles() : null;
        int[] laid = liftOut != null ? liftOut.clone() : rect.clone();
        int layer = document.activeLayer();
        // The lift's page-sized buffers go before Undo's step is copied (a page fill's peak), but for wax, which lays them again after.
        boolean waxing = (r.resist && !fluid(r) || r.waxes()) && !empty(rect);
        if (!waxing) releaseLift();
        boolean changed = document.finish();
        // Wax: its sheen is laid; now the resist onto the page's masking fluid (which would have shielded the sheen), one
        // Undo step with it.
        // So does a crayon's wax, so washes slide off it.
        if (waxing) changed = layWax(changed);
        // A stump's other half: what it softened on the pair's wash, one Undo step with the rest.
        if (washCarry != null) changed = blendWash(changed);
        L.clearAll(); W.clearAll(); D.clearAll();
        stay = null;
        // Hatching's nearest-dab distances go with the stroke's buffers.
        mL.hatch = null; mW.hatch = null;
        releaseLift(); relief = null;
        liftOut = null;
        // Keeps Paint: the brush keeps what it ends with for its holder's next stroke.
        if (heldBy != null && carry != null) session.held.keep(heldBy, carry.kept());
        // Paint Load: what is left starts the holder's next stroke, if more than the Starting Load it would have anyway.
        if (loadedBy != null) session.held.keepLoad(loadedBy, paint.left() > r.loaded.start ? paint.left() : 0);
        // Stamp: the ink left, and when it last printed, for the holder's next stroke.
        StampStamper.State printed = mL.stamp != null ? mL.stamp : mW.stamp;
        if (inkedBy != null && printed != null) session.held.keepStampInk(inkedBy, printed.left());
        mL.stamp = null; mW.stamp = null;
        plane = null; carry = null; compose.carry(null);
        washPlane = null; washCarry = null;
        under = null; compose.under(null);
        java.util.Arrays.fill(stretchRows, null);
        scratch = null; settleRows = null;
        compose.release();
        if (waxCompose != null) waxCompose.release();
        waxTones = null; waxCompose = null;
        // The wash keeps settling after lift; the water reads the page as the stroke left it. Without a session to keep
        // it (a sample) it is laid settled now, so the sample ends as the stroke does rather than lighter by what was held back.
        if (changed) {
            DryingWater water = pendingWet == null ? null : DryingWater.lay(document, paper, pendingRect[0], pendingRect[1],
                    pendingRect[2] - pendingRect[0] + 1, pendingRect[3] - pendingRect[1] + 1, pendingWet, r.wetMs, r.settle, room == null ? r.grav : null, shift, r.pen.seed, (int)(r.pen.seed ^ pts.hash()), r.erase, kind, pendingLands, room);
            if (session != null) session.holdDrying(water); else if (water != null) water.finishNow(document);
            // Halo: its veins grow from the stroke's edge while it is wet; without a session (a sample) to the end at once.
            if (found != null) {
                HaloGrowth.Lift lift = new HaloGrowth.Lift(document, layer, laid, found, r.halo, (int)(r.pen.seed ^ pts.hash() ^ 0x51a3e01d), r.erase, kind);
                if (session != null) session.holdHalo(lift);
                else { HaloGrowth g = HaloGrowth.grow(lift, null); if (g != null) g.run(document); }
            }
        }
        pendingWet = null; pendingRect = null; pendingLands = null;
        if (readsTilt && session != null) session.strokeReadsTilt(false);
        if (fades && changed) document.fadeSince(fadeFrom, r.fade, FadingInk.now());
        // Pastels and Dry brush over wet paint: their marks join it (WatercolorSession#lifted); a render's quick stroke tells it.
        if (session != null && !renders) session.lifted(document, r, pts);
        return changed;
    }
    /**
     * Wax ({@link ResolvedBrush#resist}, some of it kept; or a crayon's, {@link ResolvedBrush#waxes}): the stroke as laid
     * composed again onto the page's masking fluid, in a gesture of its own after its sheen's or the crayon's ({@code
     * sheen}: whether that changed the page), linked to it.
     * Returns whether either changed the page.
     */
    private boolean layWax(boolean sheen) {
        // A crayon's wax all stays: the eraser lifts none of it (nor is it hatched as fluid that rubs off).
        document.beginFluid(r.resist ? r.erase : 0);
        if (waxTones != null) waxTones.applyTo(document, null);
        else {
            if (r.resist) compose.sheen(false); else compose.waxOnMask();
            if (liftOut != null) liftCompose(compose);
            else compose(L, wet ? D : W, wet ? relief : null, rect, ModularCompose.Phase.FINAL);
        }
        return sheen ? document.finishWithPrevious() || true : document.finish();
    }
    /**
     * A stump's other half ({@link #alsoBlends}): what its dabs moved on the pair's wash, composed there in a gesture of
     * its own after the stroke's ({@code ink}: whether that changed the page), linked to it, as erasable as the stroke
     * (a smudge lays nothing of its own, so what each pixel keeps is what it kept). Only where it moved anything.
     * Returns whether either changed the page.
     */
    private boolean blendWash(boolean ink) {
        int[] fresh = washPlane.freshBounds();
        if (fresh == null || empty(washRect)) return ink;
        int[] at = {Math.max(fresh[0], washRect[0]), Math.max(fresh[1], washRect[1]), Math.min(fresh[2], washRect[2]), Math.min(fresh[3], washRect[3])};
        if (empty(at)) return ink;
        int back = document.activeLayer();
        document.selectLayer(washLayer);
        try {
            document.begin(r.erase, washKind);
            ModularCompose wash = new ModularCompose(ModularCompose.of(document), r, paper);
            wash.carry(washPlane);
            FloatTiles none = new FloatTiles(document.width, document.height, 0);
            wash.compose(none, none, null, at, ModularCompose.Phase.FINAL);
            wash.release();
            boolean blended = ink ? document.finishWithPrevious() : document.finish();
            return ink || blended;
        } finally { document.selectLayer(back); }
    }
    /**
     * Clear Water's lift ({@link ClearWater#lift}): what it lifted laid again where it ran out, composed in the stroke's
     * gesture; with a Slow drying its held-back share, and on a tilted page all of it, is what stays wet
     * ({@link #pendingWet}), as a wash's body is.
     */
    private void runOut() {
        ClearWater.RunOut out = ClearWater.lift(document, plane, r.carry, rect, stillWet, settles ? heldBack : 0, settles);
        if (out == null) return;
        compose(L, wet ? D : W, wet ? relief : null, out.at, ModularCompose.Phase.FINAL);
        grow(rect, out.at);
        if (out.wet != null) { pendingWet = out.wet; pendingRect = out.at; }
    }
    /** A Drying wash holds still while it is drawn and settles once it is finished. */
    @Override public boolean holdsWetPaint() { return settles; }
    /**
     * Stipple's Build-up keeps laying dots while the pen rests; Flick and Fling hear a resting pen's time, to know a stop
     * from a moving lift and land a fling's drops while the pen is held ({@link FlickFling}).
     */
    @Override public boolean buildsWhileHeld() { return holds != null || flickFling != null; }
    /** A Spiro in Ring: its pinned ring ({@link SpiroStamper#guide}), the pressure's travel as {@link #sample} takes it. */
    @Override public float ringGuide(float pressure, float tiltX, float tiltY) {
        return r.pattern == null ? 0 : (float) SpiroStamper.guide(r.pattern, ToolSettings.travel(pressure), tiltX, tiltY);
    }
    @Override public int ringMode() { return SpiroStamper.ring(r.pattern) ? r.pattern.spiro.mode : 1; }

    // ---- Float ----

    /** A show of the bath under the pen: its film (darkness 0–1) over {@code at}, page px, row by row. */
    static final class Frame {
        final int[] at; final float[] film;
        Frame(int[] at, float[] film) { this.at = at; this.film = film; }
    }
    /** Whether the stroke floats (Float). */
    boolean floats() { return floatPen != null; }
    /**
     * Float, while the pen is down: the bath near the samples since the last frame, quick (a sample a row), with the
     * stroke's own paint over it as it will float, one grey per film (float.js floatShow); null with nothing new. On the
     * thread that draws the samples (a render's own).
     */
    Frame floatFrame() {
        if (nearPen == null || floated) return null;
        double pad = floatPen.reach() + 2;
        int[] at = {Math.max(0, (int) Math.floor(nearPen[0] - pad)), Math.max(0, (int) Math.floor(nearPen[1] - pad)),
                Math.min(document.width - 1, (int) Math.ceil(nearPen[2] + pad)), Math.min(document.height - 1, (int) Math.ceil(nearPen[3] + pad))};
        nearPen = null;
        if (at[2] < at[0] || at[3] < at[1]) return null;
        float[] film = water.film(at, FloatWater.QUICK);
        ResolvedBrush.Float f = r.flt;
        if (!f.clear() && (r.line || r.body != null)) {
            int rw = at[2] - at[0] + 1;
            for (int y = at[1]; y <= at[3]; y++) for (int x = at[0], i = (y - at[1]) * rw; x <= at[2]; x++, i++) {
                double k = 1;
                if (r.body != null && W.get(x, y) >= .5) k *= 1 - f.bodyDark;
                if (r.line && L.get(x, y) >= .5) k *= 1 - f.lineDark;
                if (k < 1) film[i] = (float) (1 - (1 - film[i]) * k);
            }
        }
        return new Frame(at, film);
    }
    /** A render's {@link #floatFrame}, written in the open gesture (the quick preview's), on the page's thread. */
    void showFrame(Frame f) { water.write(f.at, f.film, false); }
    /** The lift on the water: a tap drops; a drag's line and body float as films; the whole bath's show worked out. Once. */
    private void floatLift() {
        if (floated) return;
        lastSamples();
        floated = true;
        floatPen.lift(pts, r.line ? L : null, r.body != null ? W : null, rect);
        water.prepare();
    }
    /**
     * Float's finish: the bath shown whole in the stroke's gesture, which continues the bath's undo step; a sample's
     * bath (no session) prints at once. The session keeps it floating.
     */
    private boolean finishFloat() {
        floatLift();
        document.shareBase(false);
        L.clearAll(); W.clearAll(); D.clearAll();
        compose.release();
        boolean changed;
        if (pts.count == 0) {
            // No sample: nothing happened on the water.
            water.bath.undoLast();
            document.cancel();
            changed = false;
        } else {
            water.full(session == null);
            changed = water.commit();
        }
        if (session != null) session.floatLifted(water, r.flt);
        return changed;
    }

    /**
     * Drying's lift (stroke.js 664–681), in the stroke's own buffers: the line's taper out, the body laid again and
     * settled, what stays wet held back. Returns the rectangle {@link #liftCompose} composes.
     */
    private int[] liftBuffers() {
        int[] out = rect.clone();
        long t0 = Prof.now();
        // Lifting changes only the end: the taper out, the end skipped, the last point laid. The line and the body
        // are laid again over the box those dabs reach (the samples from there on, padded by the widest dab and, for
        // a stick, its dust); everywhere else they are as laid. A placed line or body is laid again whole.
        double total = pts.total();
        // A fill has no samples to lay again: its line and body are as laid.
        if (r.line && !r.lineStateful && !filled) {
            int[] at = rect;
            if (!r.linePlaced) {
                double outLen = r.pen.fast ? 0 : StrokeMath.taperLens(r.pen.taperIn, r.pen.taperOut, r.inkSize)[1];
                at = boxOf(Math.max(0, pts.firstAt((float)(total - outLen - 1)) - 1), (r.pen.fuzz > 0 ? 2.4 * mL.widest + 4 : mL.widest) + 2);
            }
            L.clear(at); grow(out, stampL(L, true, 1, r.linePlaced ? null : at, null));
        }
        if (r.body != null && !r.bodyStateful && !filled) {
            int[] at = rect;
            if (r.bodyTip != ResolvedBrush.BodyTip.PLACED) {
                double outLen = r.body.taperOut * 90 * Math.max(1, r.bodySize / 24);
                at = boxOf(Math.max(0, pts.firstAt((float)(total - Math.max(outLen, r.body.skipEnd) - 1)) - 1), mW.widest + 2);
            }
            W.clear(at);
            // Laid afresh: a big body's spacing starts again (the prototype kept the last dab's, so it laid nothing).
            mW.lastD = Double.NEGATIVE_INFINITY;
            grow(out, stampW(W, null, true, 1, r.bodyTip == ResolvedBrush.BodyTip.PLACED ? null : at));
        }
        // Fling: the line and body laid again over the stroke's end, the drops that landed there laid again too.
        if (flickFling != null) for (FlickFling.Drop d : flickFling.landed()) { int[] box = layDrop(d, false); if (!empty(box)) grow(out, box); }
        // Stay in Lines: the flood laid again with the body laid afresh, to settle with it.
        if (stay != null && r.body != null) stay.relay(W, out);
        t0 = lap(Prof.LIFT_STAMP, t0);
        if (r.body != null) grow(out, settleBody(out));
        if (room != null) keepToRoom(out);
        t0 = lap(Prof.LIFT_SETTLE, t0);
        FloatTiles line = L, body = D, rel = relief;
        if (shift > 0) {
            out = stretch(L, D, relief, out); line = shownL; body = shownBody; rel = relief == null ? null : shownRelief;
            // Stretched back, a fill's edge kept to the room at every pixel.
            if (room != null) { keepToRoom(line, out); keepToRoom(body, out); keepToRoom(rel, out); }
        }
        if (r.body != null) holdBack(body, out);
        liftLine = line; liftBody = body; liftRelief = rel;
        Prof.add(Prof.LIFT_COMPOSE, t0);
        return out;
    }
    /**
     * The body settled ({@link BodySettle}) into D (and impasto's relief) over {@code out}: with a line, blended with it
     * (the line loosening in the wash); returns the region it covers. A Drying lift's, and a Fast-wet fill's.
     */
    private int[] settleBody(int[] out) {
        if (r.look.impasto > 0) relief = buffer(0);
        if (r.line) {
            lineSave = buffer(0); bodySave = buffer(0);
            return BodySettle.blend(L, W, D, lineSave, bodySave, relief, paper, r, out, scratch());
        }
        return BodySettle.settle(W, D, relief, paper, r.look, out, r.bodySize, null, 0, scratch());
    }
    /** Drying's lift on the page, by {@code c}: the whole stroke composed as it stays. */
    private void liftCompose(ModularCompose c) {
        long t0 = Prof.now();
        c.compose(liftLine, liftBody, liftRelief, liftOut, ModularCompose.Phase.FINAL);
        Prof.add(Prof.LIFT_COMPOSE, t0);
    }

    private StrokeMath.Scratch scratch() { if (scratch == null) scratch = new StrokeMath.Scratch(); return scratch; }

    /**
     * Charcoal's dust (the Gravity Lab's dustFall): specks from where the stick laid most fall downhill of the stroke,
     * further the more the page tilts, and catch only on the paper's high points. Seeded by the stroke, so a redraw
     * matches; nothing on a level page. Darkens the stroke's own layer, in its gesture.
     */
    private void dust(PageTilt g) {
        if (g.level()) return;
        int w = document.width, h = document.height;
        WildBrush.Random rnd = new WildBrush.Random((int)(r.pen.seed ^ pts.hash() ^ 0xd057));
        double ux = g.ux(), uy = g.uy();
        int x0 = Math.max(0, rect[0]), y0 = Math.max(0, rect[1]), x1 = Math.min(w - 1, rect[2]), y1 = Math.min(h - 1, rect[3]);
        long area = (long)(x1 - x0 + 1) * (y1 - y0 + 1);
        int n = (int)Math.min(6000, Math.round(area * .02 * r.dust));
        double reach = g.m * (8 + 70 * r.dust) * Math.sqrt(Math.max(1, r.inkSize / 12)), k = r.inkK;
        // What the specks keep of each pixel's tone, multiplied as they land; laid once, row by row.
        FloatTiles keep = new FloatTiles(w, h, 1);
        int[] out = StrokeMath.emptyRect(w, h);
        for (int m = 0; m < n; m++) {
            double sx = x0 + rnd.next() * (x1 - x0 + 1), sy = y0 + rnd.next() * (y1 - y0 + 1);
            double c = L.get(Math.min(x1, (int)sx), Math.min(y1, (int)sy));
            if (c < .3 || rnd.next() > c) continue;
            double fall = reach * Math.pow(rnd.next(), 1.8), side = (rnd.next() - .5) * (2 + .25 * fall);
            double x = sx + ux * fall - uy * side, y = sy + uy * fall + ux * side, rad = .45 + rnd.next() * rnd.next() * 1.1;
            double dark = k * (.18 + .4 * rnd.next()) * r.dust * (1 - .5 * fall / (reach + 1));
            int X0 = Math.max(0, (int)Math.floor(x - rad - 1)), X1 = Math.min(w - 1, (int)Math.ceil(x + rad + 1));
            int Y0 = Math.max(0, (int)Math.floor(y - rad - 1)), Y1 = Math.min(h - 1, (int)Math.ceil(y + rad + 1));
            if (X1 < X0 || Y1 < Y0) continue;
            for (int py = Y0; py <= Y1; py++) for (int px = X0; px <= X1; px++) {
                double hi = paper.fine(px, py) * .65 + paper.mid(px, py) * .35;
                double a = clamp(rad - Math.hypot(px + .5 - x, py + .5 - y) + .5) * dark * StrokeMath.smoothstep(.38, .62, hi);
                // Behind: its dust only where the page was blank too.
                if (under != null && r.blendMode == ResolvedBrush.BlendMode.BEHIND) a *= ModularCompose.blank(under.tone(px, py));
                if (a > .003) keep.set(px, py, (float)(keep.get(px, py) * (1 - Math.min(.9, a))));
            }
            grow(out, new int[]{X0, Y0, X1, Y1});
        }
        if (empty(out)) return;
        int rw = out[2] - out[0] + 1;
        float[] row = new float[rw];
        int[] grays = new int[rw];
        for (int y = out[1]; y <= out[3]; y++) {
            keep.row(out[0], y, rw, row, 0);
            boolean any = false;
            for (int x = 0; x < rw; x++) {
                grays[x] = -1;
                if (row[x] >= 1) continue;
                int was = document.tone(out[0] + x, y), now = (int)Math.round(was * (double)row[x]);
                if (now < was) { grays[x] = now; any = true; }
            }
            if (any) document.setTones(out[0], y, rw, 1, grays);
        }
        grow(rect, out);
    }

    /**
     * The density a body of density {@code d} lays at lift, its held-back share of darkness taken ({@link
     * ResolvedBrush#heldBack}): {@code d} less that share; past the {@link ModularSizes#KNEE} (a dark wash), the density
     * that lays that share less darkness, so what settles later ({@link DryingWater}, which reads the darkness laid as
     * the wet darkness less that share) puts back what was held.
     */
    private double held(double d) {
        double x = d * r.washK;
        if (x <= ModularSizes.KNEE) return d * (1 - heldBack);
        return ModularSizes.strength((1 - heldBack) * ModularSizes.laid(x)) / r.washK;
    }
    /** What stays wet: the body's darkness; a share of it settles later (only for transparent paint), kept to settle (or move with the page's tilt) when it {@link #settles}. */
    private void holdBack(FloatTiles D, int[] out) {
        int x0 = Math.max(0, out[0]), y0 = Math.max(0, out[1]), x1 = Math.min(document.width - 1, out[2]), y1 = Math.min(document.height - 1, out[3]);
        // Nothing held back and nothing to move with the page's tilt: nothing stays wet.
        if (x1 < x0 || y1 < y0 || !(heldBack > 0) && !settles) return;
        int rw = x1 - x0 + 1;
        float[] wet = settles ? new float[rw * (y1 - y0 + 1)] : null, row = new float[rw];
        boolean behind = r.blendMode == ResolvedBrush.BlendMode.BEHIND && under != null;
        // A fill's settling lands only in its room (and doesn't run downhill: finish), so it stays in the lines.
        // Behind's, by how blank; a fill's is its room, read there ({@link DryingWater}), never a page-sized copy.
        float[] lands = behind && wet != null ? new float[wet.length] : null;
        // D holds every pixel here (a lattice's is stretched first): a row at a time.
        for (int y = y0; y <= y1; y++) {
            D.row(x0, y, rw, row, 0);
            boolean any = false;
            for (int x = 0; x < rw; x++) {
                float d = row[x];
                // Behind settles, runs and drips only where it landed: what stays wet, and where its settling may land,
                // by how blank the page was there.
                double f = behind ? ModularCompose.blank(under.tone(x0 + x, y)) : 1;
                if (room != null && !room.inside(x0 + x, y)) f = 0;
                if (lands != null) lands[(y - y0) * rw + x] = (float) f;
                if (d <= 0) continue;
                any = true;
                if (wet != null) wet[(y - y0) * rw + x] = (float)(ModularSizes.laid(d * r.washK) * f);
                row[x] = (float)held(d);
            }
            if (any) D.setRow(x0, y, rw, row, 0);
        }
        if (wet != null) { pendingWet = wet; pendingRect = new int[]{x0, y0, x1, y1}; pendingLands = lands; }
    }
}
