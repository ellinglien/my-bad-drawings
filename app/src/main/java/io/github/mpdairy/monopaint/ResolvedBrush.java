package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.PartDial.*;
import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.lerp;
import static io.github.mpdairy.monopaint.StrokeMath.mix;
import static io.github.mpdairy.monopaint.StrokeMath.smooth;

/**
 * Parts → what the stampers draw: the prototype's resolve and partsA (parts.js 170–274), with the fallbacks for what
 * isn't drawn yet (spec, "Resolve"): a Drying wash takes no ink; with {@link ModularFeatures#BRISTLES} off, Bristles
 * draw as a round tip whose dryness is dry brush. The paper is 255, so gray is
 * 255 × (1 − darkness). The medium sets the blend of a line and a body of paint; the tip picks the stampers; the hand
 * feeds every stamper. Doubles throughout. Pure, checked on the desktop.
 */
final class ResolvedBrush {
    enum Rule { INK, CHARCOAL, GRAPHITE, CRAYON }
    enum Life { NONE, WET, DRY, FLOAT }
    enum Lines { CENTRE, SPREAD, OUTLINE, SQUIGGLE, SPLIT }
    enum LineTip { NIB, HAKE, BRISTLES, STIPPLE, HATCH, STAMP, SPIRO, HAND_HATCH }
    enum BodyTip { BODY, PLACED, BRISTLES, STIPPLE, HATCH, STAMP, SPIRO, HAND_HATCH }
    /**
     * Medium's Blend ({@link PartDial#M_BLEND}): how the mark meets what is there. OWN, the medium's way (ink, washes and
     * sticks darken; opaque paint covers); WHITE lays white by the mark's darkness; BEHIND goes only where the page is
     * still blank; INVERT turns what is under it toward its opposite ({@link ModularCompose}).
     */
    enum BlendMode { OWN, WHITE, BEHIND, INVERT;
        /** Writes coverage over what lies under it, as opaque paint does: White and Invert, whatever the medium. */
        boolean lays() { return this == WHITE || this == INVERT; }
    }

    /** partsA's blend of a line and a body. */
    static final class Blend { double w, inkShare, lineWet, bodyAmt, lineSize, bodySize, diffuse; int diffR; boolean stampLine, placed; }

    /**
     * The wide Tilt range, in degrees from upright, as the old Fude pen drew (InkWashSizes before the one engine): the
     * tip alone at {@link #STEEP} or steeper, its {@link #FINE} share of the width; the natural hold, all of it; laid
     * at {@link #LOW} or lower, {@link #BROAD} times it (forty times the fine width) and the nib {@link #FLATTEST}.
     */
    static final double STEEP = 30, NATURAL = 45, LOW = 60, FINE = .12, BROAD = 40 * FINE, FLATTEST = .2;
    /**
     * Tilt Shading at its fullest ({@link Pen#shade}): the line this many times as wide again, this much lighter (the
     * side spreads the hand's force), its edge soft over this share of its radius (a stick's fuzzy edge this much
     * wider again); the paper's grain skipped up to this height, eased over this much either side ({@link
     * LineStamper#sideTooth}); its streaks at most this much lighter ({@link LineStamper#sideStreak}); a dab laid every
     * this share of its width. Together about two thirds of the upright mark's darkness at the same pressure.
     */
    static final double SIDE_WIDTH = 5, SIDE_LIGHT = .05, SIDE_SOFT = .8, SIDE_FEATHER = .5, SIDE_SKIP = .35, SIDE_BAND = .05, SIDE_STREAK = .1, SIDE_STEP = .2;

    /** The line's inputs (partsA's pen plus the hand). Mutable: bodyPen is a changed copy. */
    static final class Pen {
        long seed; double aspect, angle, taperIn, taperOut, pressure, speed, dry, tooth, wobble, pool, splatter;
        Lines lines; int count; double spread, breathe, wetGap, offset, chaos, skipStart, skipEnd;
        boolean fast, thicker; double tiltSize, tiltAngle, tDark, pDark, sDark, pWidth, sAngle, fuzz;
        /** The hand's Tilt range is wide ({@link #tiltWidth}). */
        boolean tiltWide;
        /** The hand's page tilt (Gravity): drawn downhill to size, dark and the nib's width ({@link StrokeMath#gSize}). */
        double gSize, gDark, gWidth;
        /** A Filbert: the nib's broad side lies across the pen's lean (its Nib Angle without a reading). */
        boolean filbert;
        /** A Crayon (not its Scratch): the nib's edge ragged, fixed to the page, and its line holds how hard it presses ({@link LineStamper}). */
        boolean crayon;
        /** The hand's Tilt Shading: how far a pen laid low lays the side of its nib ({@link #shade}). */
        double side;

        Pen copy() {
            Pen p = new Pen();
            p.seed = seed; p.aspect = aspect; p.angle = angle; p.taperIn = taperIn; p.taperOut = taperOut; p.pressure = pressure;
            p.speed = speed; p.dry = dry; p.tooth = tooth; p.wobble = wobble; p.pool = pool; p.splatter = splatter;
            p.lines = lines; p.count = count; p.spread = spread; p.breathe = breathe; p.wetGap = wetGap; p.offset = offset;
            p.chaos = chaos; p.skipStart = skipStart; p.skipEnd = skipEnd; p.fast = fast; p.thicker = thicker;
            p.tiltSize = tiltSize; p.tiltAngle = tiltAngle; p.tDark = tDark; p.pDark = pDark; p.sDark = sDark;
            p.pWidth = pWidth; p.sAngle = sAngle; p.fuzz = fuzz; p.tiltWide = tiltWide;
            p.gSize = gSize; p.gDark = gDark; p.gWidth = gWidth; p.filbert = filbert; p.crayon = crayon; p.side = side;
            return p;
        }

        /**
         * Tilt → size's factor on a dab's width, the pen leaning {@code lean} ({@link BrushDirection#tiltLean} of
         * {@code tiltX, tiltY}): 1 + 1.2 × the cell × lean, to 2.2 times; with a wide Tilt range, the old Fude pen's
         * (from {@link #FINE} of the width held upright, 30 degrees or steeper, through all of it at the natural 45 to
         * {@link #BROAD} times laid at 60 or lower) as far as the cell goes, and the natural hold without a reading.
         */
        double tiltWidth(double tiltX, double tiltY, double lean) {
            if (!tiltWide) return 1 + 1.2 * tiltSize * lean;
            double degrees = reading(tiltX, tiltY) ? 5 + 60 * lean : NATURAL;
            double share = degrees <= NATURAL ? FINE + (1 - FINE) * clamp((degrees - STEEP) / (NATURAL - STEEP))
                    : 1 + (BROAD - 1) * clamp((degrees - NATURAL) / (LOW - NATURAL));
            return 1 + tiltSize * (share - 1);
        }
        /**
         * Tilt → angle's flattening of the nib, as a share of its short side: 1 − 0.45 × the cell × lean; with a wide
         * Tilt range, round to the natural 45 degrees, then flattening (eased in) to {@link #FLATTEST} at 60 as far as
         * the cell goes, as the old Fude pen's flat face did; round without a reading.
         */
        double tiltFlat(double tiltX, double tiltY, double lean) {
            if (!tiltWide) return 1 - 0.45 * tiltAngle * lean;
            double past = reading(tiltX, tiltY) ? clamp((5 + 60 * lean - NATURAL) / (LOW - NATURAL)) : 0;
            return 1 - (1 - FLATTEST) * tiltAngle * past * past;
        }
        /** Whether {@code tiltX, tiltY} is a tilt reading, as {@link BrushDirection#tiltLean} takes one. */
        private static boolean reading(double tiltX, double tiltY) {
            return Double.isFinite(tiltX) && Double.isFinite(tiltY) && Math.abs(tiltX) <= 90 && Math.abs(tiltY) <= 90;
        }

        /** Whether tilt changes the nib: its size, angle or darkness; a Filbert's angle always. */
        boolean tilts() { return tiltSize + tiltAngle + tDark + side > 0 || filbert; }
        /**
         * Tilt Shading, the pen leaning {@code lean} ({@link BrushDirection#tiltLean}: 0 at 5 degrees, 1 at 65):
         * none through 35 degrees, so the natural hold (the Manta reads 30 to 45) stays a crisp line, easing in to all
         * of {@link #side} at 65 and lower, as a pencil's lead lies on its side. {@link LineStamper#nib} lays it:
         * {@link #SIDE_WIDTH} times as wide again, a little lighter and more diffused: {@link #SIDE_LIGHT} of its
         * darkness lifted, an edge soft over {@link #SIDE_SOFT} of the nib's radius, the paper's valleys skipped and
         * faint streaks along it.
         */
        double shade(double lean) { return side > 0 && lean > .5 ? side * StrokeMath.smoothstep(.5, 1, lean) : 0; }
        /** A dab's width before tapers, at line size {@code size}, pressure {@code pr} and speed {@code v} (px/ms): the line's and the placed lines'. */
        double width(double size, double pr, double v) {
            return size * lerp(1 - 0.75 * pressure, 1 + 0.6 * pressure, pr) * clamp(1 + speed * 0.55 * (clamp(v / 2.5) - 0.3), 0.35, 1.6);
        }
        /** How much ink a dry line still carries {@code d} px along, at speed {@code v} and pressure {@code pr}: under it, bristles skip. */
        double load(double d, double v, double pr) { return 1 - dry * (0.35 + 0.8 * clamp(d / 600) + 0.7 * clamp(v / 2.5) - 0.6 * pr); }

        /** The seed's random draws the stampers read, drawn at a stroke's first stamp rather than each stamp (the seed is set before). */
        private double[] bristles;
        private Placement.Draws placed;
        double[] bristles() { if (bristles == null) bristles = LineStamper.bristles(seed); return bristles; }
        Placement.Draws placed() { if (placed == null) placed = new Placement.Draws(seed); return placed; }
    }

    /**
     * The body's inputs (partsA's body plus the hand, cover and head); also the hake as a line ({@link #lineHake}).
     * {@code wet} is the prototype's {@code mode === "wet"}, {@code flatHead} its {@code head === "flat"},
     * {@code headFixed} its {@code headTurn === "fixed"}.
     */
    static final class Body {
        long seed; boolean wet; double pressure, soft, streaks, aspect, angle, taperIn, taperOut, amount, skipStart, skipEnd;
        boolean fast; double tiltSize, tDark, pDark, sDark, pWidth, sAngle, cover, impasto, gran;
        boolean flatHead, headFixed; double tilt, speed;
        /** The hand's page tilt (Gravity): drawn downhill to size and dark. */
        double gSize, gDark;
        /**
         * A Filbert: a rounded oval, its broad side across the pen's lean (its Nib Angle without a reading); with Paint
         * Load ({@code crescent}) each dab a crescent hollowed toward the way it moves ({@link BodyStamper}).
         */
        boolean filbert, crescent;
        /** Screened ({@link PrintScreen}): its edge as crisp as opaque cover's, so the screen meets a clean footprint. */
        boolean crisp;
        /** Filled only for lineHake here; the hake body's softK is computed by the body stamper (engine.js:267: lerp(lerp(.08, .012, cover), .6, soft)). */
        double softK;

        Body copy() {
            Body b = new Body();
            b.seed = seed; b.wet = wet; b.pressure = pressure; b.soft = soft; b.streaks = streaks; b.aspect = aspect; b.angle = angle;
            b.taperIn = taperIn; b.taperOut = taperOut; b.amount = amount; b.skipStart = skipStart; b.skipEnd = skipEnd;
            b.fast = fast; b.tiltSize = tiltSize; b.tDark = tDark; b.pDark = pDark; b.sDark = sDark; b.pWidth = pWidth;
            b.sAngle = sAngle; b.cover = cover; b.impasto = impasto; b.gran = gran; b.flatHead = flatHead; b.headFixed = headFixed;
            b.tilt = tilt; b.speed = speed; b.softK = softK; b.gSize = gSize; b.gDark = gDark; b.filbert = filbert; b.crescent = crescent; b.crisp = crisp;
            return b;
        }

        /** The streak lanes ({@link BodyStamper#hairs}), drawn at a stroke's first stamp rather than each stamp (the seed is set before). */
        private double[] hairs;
        double[] hairs() { if (hairs == null) hairs = BodyStamper.hairs(seed); return hairs; }
    }

    /**
     * Smudge (Carry: Lift, Drag, Smear; the Smudge spec, {@link Carry}): a dab-sized reservoir of radius {@code r} in
     * cells {@code stride} px apart, {@code 2n + 1} a side, its dabs {@code spacing} px apart, {@code hits} of them a
     * radius of travel; Pick up and Lay down. {@code moves}: whether it passes over the page at all (opaque Lift
     * doesn't: what it covers tints its paint, {@link #tint}). Smear's {@code soften} (0 to 1): how much of it is
     * MonoPaint's blending stump ({@link Carry}), which reads the page {@link #softenReach} px past the dab; with a
     * Rake or Split nib the stump works only along {@code tines} (offsets across the stroke, px; null: the round dab),
     * each {@code tineW} px either side.
     */
    static final class Carry {
        final PartChoice kind; final double pick, lay, r, stride, spacing, hits, soften, tineW, keeps; final int n; final boolean moves;
        final double[] tines;
        Carry(PartChoice kind, double pick, double lay, double r, boolean moves, double soften, double[] tines, double tineW, double keeps) {
            this.kind = kind; this.pick = pick; this.lay = lay; this.r = r; this.moves = moves; this.soften = soften;
            this.tines = tines; this.tineW = tineW; this.keeps = keeps;
            stride = r > 30 ? r / 30 : 1; n = (int) Math.ceil(r / stride) + 1;
            // Dabs 1 + .08 r apart (r / 4 under 15 px), so a smear streaks smoothly, without combing.
            spacing = Math.max(1.5, Math.min(r * .25, 1 + r * .08)); hits = r / spacing;
        }
        /**
         * A round stump under {@link #SMALL_STUMP} px in radius is smaller than MonoPaint's ever was (19 px across at its
         * lightest press). MonoPaint's reach there, a 5 × 5 blur and a pull of a pixel or two, mixes less than the
         * pencil and charcoal lines and grain it is drawn over (a few px whatever the stump's size), and its {@code
         * (1 − q)²} falloff leaves only the middle of the dab working: a 4 or 16 px stump changed a third of its band
         * over pencil, a 44 px one 61%, and the Manta's 8 × 8 dots showed next to nothing (the owner, 2026-10-19: "no
         * effect"). So a small stump reaches at least {@link #FINE_REACH} px for both, and its falloff flattens toward
         * {@code (1 − q)} ({@link #flatter}); from {@link #SMALL_STUMP} up, MonoPaint's own.
         */
        static final double SMALL_STUMP = 10;
        /** A small stump's least blur reach and pull, px. */
        static final int FINE_REACH = 3;
        /** A round stump smaller than MonoPaint's ({@link #SMALL_STUMP}); a comb never is. */
        boolean small() { return tines == null && r < SMALL_STUMP; }
        /** How far a small stump's falloff has flattened from {@code (1 − q)²} toward {@code (1 − q)}: 0 from {@link #SMALL_STUMP} px up, 1 at 6 px and under. */
        double flatter() { return small() ? StrokeMath.clamp((SMALL_STUMP - r) / 4) : 0; }
        /** The stump's blur reach, px (MonoPaint's: a fifth of the radius, 2 to 8; a comb's, its tines' half-width; a small stump's at least {@link #FINE_REACH}). */
        int blur() { return tines != null ? Math.max(1, (int) Math.round(tineW)) : Math.max(small() ? FINE_REACH : 2, Math.min(8, (int) Math.round(r * .2))); }
        /** How far behind the stump softens toward, px: a quarter of the radius (MonoPaint's), a small stump's at least {@link #FINE_REACH}. */
        double pullReach() { return small() ? Math.max(FINE_REACH, r * .25) : r * .25; }
        /** {@link #pullReach}, whole pixels. */
        int pull() { return (int) Math.round(pullReach()); }
        /** A comb tine's reach either side of its point, px (1.6 of its half-width). */
        double tineReach() { return 1.6 * tineW; }
        /** The stump's footprint radius, px: the dab's, or out to a comb's outer tines. */
        double stumpR() {
            if (tines == null) return r;
            double far = 0;
            for (double t : tines) far = Math.max(far, Math.abs(t));
            return far + tineReach();
        }
        /** Clear Water: lifts as Lift does and lets what it lifted run out through its wet ({@link ClearWater}). */
        boolean water() { return kind == PartChoice.CLEAR_WATER; }
        /** Lift and Clear Water start clean and lift at once (a damp brush); Drag and Smear first read the page. */
        boolean lifts() { return kind == PartChoice.LIFT || kind == PartChoice.CLEAR_WATER; }
        /** Drag's and Smear's Keeps Paint: what it picked up is kept for the next stroke ({@link HeldPaint}). */
        boolean keepsPaint() { return keeps > 0 && soften < 1; }
        /** How far past the dab's radius the stump reads the page, px; 0 without Soften. */
        double softenReach() { return soften > 0 ? Math.max(0, stumpR() - r) + blur() + pull() + 1 : 0; }
    }

    /**
     * Float (the Float spec, parts.js floatParts): what the stroke does on the water. Spread sizes a tap's drop, Drift its
     * drift on a tilted page, {@code floatMs} how long after the last touch it prints. {@code load} is Smudge's Own
     * paint (0: clear water, which lays nothing); {@code pick} how far a Drag or Smear pen drags what floats (0 for Off and
     * Lift). {@code filmW} is the half-width of the film it lays; the pen's tines sit at {@code tines} across its motion,
     * each {@code sig} wide. Greys: the line's and the body's films, and a tap's drop.
     */
    static final class Float {
        final double spread, drift, load, pick, filmW, sig, lineDark, bodyDark, dropDark;
        final long floatMs;
        final double[] tines;
        Float(double spread, double drift, long floatMs, double load, double pick, double filmW, double[] tines, double sig, double lineDark, double bodyDark, double dropDark) {
            this.spread = spread; this.drift = drift; this.floatMs = floatMs; this.load = load; this.pick = pick; this.filmW = filmW;
            this.tines = tines; this.sig = sig; this.lineDark = lineDark; this.bodyDark = bodyDark; this.dropDark = dropDark;
        }
        /** Clear water: no paint of its own (Smudge's Own paint 0). */
        boolean clear() { return load < .02; }
    }

    /**
     * A bristle tip's swarm (parts.js lineSwarm, bodySwarm; {@link BristleSwarm}): its hairs and how they follow each
     * other (cohesion) and open (splay), how dry its paint is and how far its load carries, the hand's pressure to size,
     * tilt to size and darkness cells, and the share of paint it lays (a body's amount; 1 for a line). {@code gSize}, {@code gDark}: the
     * hand's page tilt (Gravity), as every stamper reads it.
     */
    static final class Swarm {
        long seed; double hairs, cohesion, splay, dryness, load, pressure, tiltSize, tDark, pDark, sDark, amount = 1, gSize, gDark;
        Swarm copy() {
            Swarm s = new Swarm();
            s.seed = seed; s.hairs = hairs; s.cohesion = cohesion; s.splay = splay; s.dryness = dryness; s.load = load;
            s.pressure = pressure; s.tiltSize = tiltSize; s.tDark = tDark; s.pDark = pDark; s.sDark = sDark; s.amount = amount; s.gSize = gSize; s.gDark = gDark;
            return s;
        }
    }

    /**
     * A Stipple, Hatch or Stamp tip (the new modules spec; newmods.js resolve's pat): laid where a round tip of its
     * weight would reach, by {@link StippleStamper}, {@link HatchStamper} or {@link StampStamper} in place of the nib and
     * the body. Stipple: Dot size, Scatter, Jitter and Build-up (0 off, 1 slow, 2 fast); Hatch: Spacing, Line weight,
     * Angle (degrees), Lines fixed on the page or following the stroke, Layers; Stamp: its nib ({@link StampNib}, round
     * without one), printed {@code size} px along its longer side every Spacing of it, turning with the line or not,
     * Ink Fade's share of its ink used a print; Spiro: its curve ({@link SpiroStamper.Settings}), {@code size} px across
     * along the line. The hand's Pressure size narrows the band (a stamp's print) when light, Pressure and Speed dark
     * lighten the marks (a Spiro's size answers its drives instead).
     */
    static final class Pattern {
        final PartChoice kind; final long seed;
        final double dot, scatter, jitter, pitch, lineW, angle, ps, pDark, sDark;
        final boolean fixed; final int layers, hold;
        /** Stamp: its print's size (px), spacing (a share of it), Ink Fade and whether it turns; its nib. 0, false and null for the others. */
        final double size, spacing, fade; final boolean turns; final StampNib nib;
        /** Spiro: its curve and drives; null for the others. Its {@link #size} is its curve's width along the line. */
        final SpiroStamper.Settings spiro;
        /** Hatching: its strokes' Line Length, Wobble and Taper Out, 0 to 1. */
        final double hLen, hWob, hEnds;
        Pattern(ModularBrush b, double ownSize) {
            ModularBrush.Part t = b.part(BrushPart.TIP);
            kind = t.choice; seed = b.seed;
            dot = t.get(DOT, DOT.def); scatter = t.get(SCATTER, SCATTER.def); jitter = t.get(JITTER, JITTER.def); hold = (int) t.get(BUILD_UP);
            pitch = t.get(PITCH, PITCH.def); lineW = t.get(LINE_W, LINE_W.def); angle = t.get(HANGLE, HANGLE.def);
            fixed = (int) t.get(HEAD_TURN, 1) == 1; layers = (int) Math.round(t.get(LAYERS, 3));
            ps = b.handCell(PS); pDark = b.handCell(PD); sDark = b.handCell(SD);
            boolean stamp = kind == PartChoice.STAMP;
            spiro = kind == PartChoice.SPIRO ? new SpiroStamper.Settings(b) : null;
            size = stamp || spiro != null ? ownSize : 0; spacing = t.get(STAMP_SPACING, 0); fade = t.get(INK_FADE, 0);
            turns = stamp && (int) t.get(HEAD_TURN) == 0;
            nib = stamp ? StampNib.orRound(b.nib) : null;
            hLen = t.get(HLEN, HLEN.def); hWob = t.get(HWOB, HWOB.def); hEnds = t.get(HENDS, HENDS.def);
        }
        boolean stipple() { return kind == PartChoice.STIPPLE; }
        boolean stamp() { return kind == PartChoice.STAMP; }
        boolean spiro() { return kind == PartChoice.SPIRO; }
        boolean handHatch() { return kind == PartChoice.HAND_HATCH; }
        /** The band's half-width at pressure {@code pr}: {@code r} times Pressure size's narrowing, exactly {@code r} with it off. */
        double band(double r, double pr) { return r * (ps != 0 ? lerp(1, .35 + .65 * pr, ps) : 1); }
        /** How dark a mark is at pressure {@code pr} and speed {@code v}: the hand's darkness cells (no tilt). */
        double dark(double pr, double v) { return StrokeMath.handDark(pDark, sDark, 0, pr, v, 0); }
    }

    /** The settle's inputs. */
    static final class Look { double feather, hollow, bloom, backrun, gran, cover, impasto; }

    /**
     * Gravity (the Gravity Lab's gravity.js): how wet paint moves when the page tilts. Slow takes all five, after lift
     * ({@link GravityFlow}); Quick only Runs, Pool and Sink, inside its window under the pen.
     */
    static final class Grav {
        final double runs, drips, pool, sink, blend;
        Grav(double runs, double drips, double pool, double sink, double blend) { this.runs = runs; this.drips = drips; this.pool = pool; this.sink = sink; this.blend = blend; }
    }

    final Blend parts; final Pen pen;
    /** The body of paint; null when the medium lays none (partsA's bodyAmt is 0). */
    final Body body;
    /** The body the placed lines read their geometry from: partsA's body with the hand, even when there's no body. */
    final Body placeBody;
    final Look look;
    final Rule rule; final BrushTexture paper; final Life life;
    final double inkSize, washSize, bodySize, inkK, washK, cover, gray, shade, hold, spread, grade, windowLen, drying, settle, lineTooth, placeSize;
    /**
     * The shade Drying's light preview grey ({@link ModularCompose#previewWash}) shows the body at: the swatch's, a
     * wash's as dark as its glaze is to the old 34% one ({@link ModularSizes#WASH_BLACK} / {@link ModularSizes#GLAZE} of
     * it), so a wash that lays as an old one did previews as it did, and a black one darker.
     */
    final double previewShade;
    /** Drying: the share of the body's darkness held back at lift to settle after it (stroke.js 672); none for opaque paint. */
    final double heldBack;
    /**
     * A Drying body that stays wet after lift ({@link DryingWater}): transparent, with Settle above 0 (it keeps
     * settling) or a gravity dial up (it moves when the page tilts).
     */
    final boolean settlesAfterLift;
    /** How wet paint moves with the page's tilt; null when it doesn't: Instant, opaque Cover, or every gravity dial 0. */
    final Grav grav;
    /** Charcoal's dust falling downhill at lift on a tilted page, 0–1. */
    final double dust;
    /** How much of its paint the eraser takes, 0 (permanent) to 1 (Erasable; every brush before it: 1). */
    final double erase;
    /** The kind of mark it lays ({@link ToneDocument#KIND_PEN} and the like), for an eraser that takes one kind; 0 when the brush lays no paint of its own (a finger's smudge). Opaque paint (Cover) adds {@link ToneDocument#KIND_OPAQUE}: its own plane. */
    final int kind;
    /** Inkwash: the kind its Medium is, paint of its own or not: the plane a smudge with none of its own works on ({@code ModularStroke.kinds}). */
    final int medium;
    /** How the mark meets what is there (Blend); OWN on water, where every medium floats as a darkening film. */
    final BlendMode blendMode;
    /** How long a Slow mark stays wet, ms: the Gravity Lab's longer range for one that moves ({@link PartDial#wetMs(double, boolean)}). */
    final long wetMs;
    final boolean line, lineInk, linePlaced, lineStateful, bodyStateful;
    final int lineReach;
    final LineTip lineTip; final BodyTip bodyTip;
    /** The hake as a line, or null. */
    final Body lineHake;
    /** A bristle tip's swarm as the line and as the body; null otherwise. */
    final Swarm lineSwarm, bodySwarm;
    /** A Stipple, Hatch or Stamp tip's pattern, line and body; null otherwise. */
    final Pattern pattern;
    /** The placed body (a split nib's or multi-line's paint): its line size, placement size, pen and geometry; 0 / null otherwise. */
    final double bodyLineSize, bodyPlaceSize; final Pen bodyPen; final Body bodyGeom;
    /** Smudge, or null for Off; null on water too, where Drag and Smear comb what floats ({@link #flt}). */
    final Carry carry;
    /**
     * Stay in Lines ({@link PartChoice#STAY_IN}): its Flood, 0 to 1, for a stroke that keeps to the shape the ink lines
     * shut it in and floods on through it ({@link StayIn}); −1 for every other brush, and on water. No smudge then.
     */
    final double stayIn;
    /** Whether its stroke keeps inside the ink lines ({@link #stayIn}). */
    boolean staysIn() { return stayIn >= 0; }
    /** Float: what the stroke does on the water; null for the other Drying choices. */
    final Float flt;
    /** Smudge's Own paint: the share of its own paint the brush lays, 1 for Off. */
    final double load;
    /** Opaque Lift: how far its paint's grey moves toward darker paint it covers (twice Pick up, at most 1); 0 otherwise. */
    final double tint;
    /**
     * Smudge's Paint Load (the oil paint spec, {@link LoadedPaint}): a Drag or Smear whose own paint runs out along the
     * stroke; null for every other brush (and on water, where Drag and Smear comb what floats, or with no paint of its own).
     */
    final LoadedPaint loaded;

    /**
     * Resist (the new modules spec): masking fluid or wax. Laid as a crisp ink line ({@link #asInk}), not onto the
     * stroke's layer but onto the page's masking fluid ({@link ToneDocument#beginFluid}), which shields every layer from
     * whatever comes later; wax (the part the eraser can't lift, {@link #erase} below 1) also lays a faint sheen of its
     * own on the pair's ink layer and catches only the paper's high points ({@link ModularCompose}).
     */
    final boolean resist;
    /**
     * Crayon (mischief-lab-2.html's fourth pad): its Wax, 0 to 1 (0 for its Scratch and every other brush). Its line
     * builds up where crayon already lies ({@link ModularCompose}), and its wax goes on the page's masking fluid at the
     * lift, so washes slide off it ({@link ModularStroke}).
     */
    final double wax;
    /** Crayon's Scratch: a thin blade laid as Blend's White, scraping back to white. */
    final boolean scratch;
    /** A Marker's flat ink: the Marker style's wash that dries at once (Colouring pen, Fill in, Filled shape), for {@link #lightOver}. */
    final boolean markerInk;
    /** Whether its stroke lays wax on the page's masking fluid at the lift: a Crayon with Wax, not its Scratch. */
    boolean waxes() { return wax > 0; }
    /** A stick: its line reads what lies under it, and a quick stroke previews a share of it ({@link ModularCompose#STICK_SHARE}). */
    boolean stick() { return pen.fuzz > 0 || rule == Rule.CHARCOAL || rule == Rule.CRAYON; }
    /**
     * Light over dark (the owner, 2026-10-22: "light colors on opaque materials.. crayon, oils.. they should go on light",
     * "pencil and charcoal in lighter colors should do the same", "also the print ones"): a dry stick's or pencil's line
     * (graphite, charcoal, pastels, crayon) and an opaque Print Shading mark are pigment that sits on top, so where the page
     * under it is darker than its grey it lays that grey over it ({@link ModularCompose}); black, any other grey over lighter
     * marks, and transparent ink and washes darken only, as ever. Not with a Blend, Resist or a smudge of its own. Any other
     * print replaces the prints under it instead ({@link #replaces}, 2026-10-29): it leaves pen and washes as they are.
     * White ink (the owner, 2026-10-24: "if they select a light shade, it's treated like a light color ink"): a pen's or
     * marker's ink lighter than mid grey is opaque light ink, a gel or paint pen's, by {@link #lightOver}.
     */
    boolean laysLight() { return lightOver() > 0; }
    /**
     * How opaque its light grey is over darker marks ({@link #laysLight}): 1 for a stick's or pencil's line and an opaque print;
     * for a pen's line or a Marker's flat ink (ink that dries at once: no wash, float, fade or mould), none at mid grey
     * or darker, so those darken exactly as ever, easing up to all of it by a grey of 150. 0 for everything else.
     */
    double lightOver() {
        if (!(gray > 0) || blendMode != BlendMode.OWN || resist || carry != null) return 0;
        if (screen != null) return !replaces() && (line || body != null) ? 1 : 0;
        if (rule != Rule.INK) return line ? 1 : 0;
        boolean pen = lineInk && line && parts.w < .5, marker = markerInk && body != null && !line;
        if (!(pen || marker) || !(life == Life.NONE || life == Life.WET && pen) || fade != null || halo != null || flt != null || !(gray > LIGHT_INK)) return 0;
        double t = Math.min(1, (gray - LIGHT_INK) / (OPAQUE_INK - LIGHT_INK));
        return t * t * (3 - 2 * t);
    }
    /** Mid grey: ink lighter than this lays light over darker marks ({@link #lightOver}), wholly opaque from {@code OPAQUE_INK}. */
    static final double LIGHT_INK = 128, OPAQUE_INK = 150;

    /**
     * Halo (the new modules spec, {@link HaloGrowth}): after Slow's lift, how far its veins reach from the stroke's edge
     * (page px), how many scouts grow them and how fast veins darken (Growth, 0–1), how many ticks it grows, how dark a
     * vein gets at most, and whether it goes only where the page was blank (Blend's Behind). Null for every other brush,
     * and for a Halo brush that lays white or inverts (veins only darken) or resists.
     */
    static final class Halo {
        /** The web lab's Reach is 12 to 80 px, times up to 3 for a broad tip; the app keeps it within three tip widths (the owner's limit). */
        static final double MOST_WIDTHS = 3;
        /** The longest it grows: the lab's longest drying time, 20 s (the owner's limit). */
        static final int MOST_TICKS = 200;
        final int reach, ticks;
        final double growth, dark;
        final boolean behind;
        Halo(int reach, double growth, int ticks, double dark, boolean behind) { this.reach = reach; this.growth = growth; this.ticks = ticks; this.dark = dark; this.behind = behind; }
    }
    /** Halo's growth after lift; null for every other brush. */
    final Halo halo;
    /**
     * Fading or Lemon Juice (the mischief modules spec, Stage 3; {@link FadingInk}): laid as Quick lays it, then from the
     * lift the mark changes by the clock, fading away or darkening in over its time. Null for every other brush.
     */
    final FadingInk.Spec fade;
    /**
     * Wet (the wet paint spec; {@link PartChoice#WET_PAINT}): opaque paint, clear water, or ink in water (Ink or Wash, the
     * wet brush spec) laid into MonoPaint's wet canvas ({@link WetPaintStroke}), which stays wet across strokes until it
     * is dried. Its Stays Wet and Runs Downhill. Null for every other brush, and for Wet on any other medium (laid as Instant, as the
     * engine lays Wet everywhere it has no wet canvas: samples, Copy Strokes' copies, the web lab).
     */
    static final class Wet {
        /** Stays Wet and Runs Downhill, 0 to 1. */
        final double stays, runs;
        /**
         * Whether paint dried before it (the wet paint taken up from the page) wets again under it: Gouache's does, an
         * Oils brush's doesn't (the owner, 2026-10-22: "Gouache re-wets, dried Oils do not").
         */
        final boolean rewets;
        /**
         * Ink or Wash without Clear Water (the wet brush spec, 2026-10-22): MonoPaint 0.98.3's wet brush pen, its ink in
         * water ({@link BrushInk}, untinted, held between strokes): it trades ink with the wet paint it touches (a clean
         * brush soaks it up, an inky one lets it out), lifts dry paint at once, and the ink runs through its water.
         */
        final boolean inks;
        /**
         * Wet watercolour (the wet paint spec, section 9): a Watercolor style brush's Wash without Clear Water, laid as a
         * transparent wash that merges wet into wet and dries back with a dark edge, blooms, bleed and granulation, from its
         * Wash's and Paper's own dials ({@link #wash(ModularBrush, double)}); null for every other Wet brush.
         */
        final WetWatercolor.Wash wash;
        /** The wash's water as laid, 0 to 1 ({@link WetWatercolor#washMask}): from the Wash's Wet. */
        final float washPull;
        /** A wet wash with Pen Line past 0: it lays a crisp pen line beneath its water too ({@link WetPaintStroke}). */
        final boolean line;
        Wet(double stays, double runs, boolean rewets) { this(stays, runs, rewets, false); }
        Wet(double stays, double runs, boolean rewets, boolean inks) { this(stays, runs, rewets, inks, null, 0, false); }
        Wet(double stays, double runs, boolean rewets, boolean inks, WetWatercolor.Wash wash, float washPull, boolean line) {
            this.stays = stays; this.runs = runs; this.rewets = rewets; this.inks = inks; this.wash = wash; this.washPull = washPull; this.line = line;
        }
        /**
         * {@code b}'s wash (a Watercolor Wash, Wet), its Stays Wet {@code stays}: Dark Edge → the rim, Bloom and Backruns →
         * blooms (Backruns their water's unevenness too), Bleed → feathering, the paper's Tooth → granulation on its grain,
         * Stays Wet → how slowly its water dries back (a frame keeps .9775 of its freshness at 0 to .9925 at 1; .985,
         * MonoPaint's, at .5).
         */
        static WetWatercolor.Wash wash(ModularBrush b, double stays) {
            ModularBrush.Part m = b.part(BrushPart.MEDIUM), p = b.part(BrushPart.PAPER);
            StrokePaper paper = new StrokePaper(p.choice.texture());
            // Ink wash (the ink wash note): ink on absorbent paper, a softer rim and smaller blooms than pigment gathering at
            // a watercolour's edge, and its water feathering further into the paper by its Bleed.
            boolean ink = inkWash(b);
            double rim = ink ? INK_WASH_RIM : 1;
            return new WetWatercolor.Wash((float) (rim * m.get(POOL)), (float) (rim * Math.min(1, m.get(BLOOM) + .5 * m.get(BACKRUN))), (float) m.get(BACKRUN),
                    (float) m.get(BLEED), (float) p.get(TOOTH), (float) (1 - .015 * (1.5 - stays)), ink ? INK_WASH_FEATHER : .85f,
                    (x, y) -> .55f * paper.fine(x, y) + .45f * paper.blob(x, y),
                    (x, y) -> paper.low(x, y),
                    (x, y) -> 1 - (.6f * paper.fine(x, y) + .4f * paper.mid(x, y)));
        }
        /**
         * {@code b}'s wash's water as laid ({@link #washPull}): a Watercolor Wash's .45 + .4 × its Wet; an Ink wash one's
         * fresher, .6 + .25 × its Wet (the owner, 2026-10-22: "less dark where they overlap, especially when wet"), so its
         * strokes stay loose long enough to mix and its water feathers into the paper by its Bleed, as ink on rice paper.
         */
        static float pull(ModularBrush b) {
            double w = b.part(BrushPart.MEDIUM).get(WET);
            return (float) (inkWash(b) ? .6 + .25 * w : .45 + .4 * w);
        }
        static boolean inkWash(ModularBrush b) { return "Ink wash".equals(b.style.name); }
        /** An Ink wash's Dark Edge and Bloom, a share of a watercolour's. */
        static final double INK_WASH_RIM = .4;
        /** How far an Ink wash's water feathers into the paper ({@link WetWatercolor.Wash#feather}): further than a watercolour's .85. */
        static final float INK_WASH_FEATHER = .95f;
        /**
         * MonoPaint's wetness, 15 to 85 (its Wetness slider's, {@link WetWatercolor#setWetness}): how much of the paint
         * under a new stroke mixes into it (60% of it at 100: 9% to 51% here, 30% at the middle) and how freely wet paint
         * evens out. A little at Stays Wet 0, so Wet paint always mixes some; never so much that a dark stroke on white
         * paper settles to half its grey.
         */
        int wetness() { return (int) Math.round(15 + 70 * stays); }
        /** How many frames a touched patch moves ({@link WetWatercolor#setFrames}), 8 to 40 (MonoPaint's 20 a little past the middle): 1.2 to 6 s at 150 ms a frame. */
        int frames() { return (int) Math.round(8 + 32 * stays); }
    }
    /**
     * A wet ink wash's body under the pen (the coordinator's Manta bench, 2026-10-22: Line and wash at 60 px lagged 1 to
     * 2 s): its body, up to 8 times its line wide, is laid at most {@link #WET_HALO} px past the line each side and at most
     * {@link #WET_BODY} px across (unless the line is wider), as every pixel the wet canvas lays under the pen costs the
     * same; a wash that size keeps up as Grey wash does.
     */
    static double wetBody(double body, double line) { return Math.min(body, Math.max(line, Math.min(WET_BODY, line + 2 * WET_HALO))); }
    static final double WET_HALO = 40, WET_BODY = 180;
    /**
     * Whether {@code b} is a wet wash ({@link Wet#wash}): Wet without Clear Water, a Watercolor style Wash (the wet paint
     * spec, section 9) or an Ink wash style Wash, or Ink with a body (Wet past .08), the ink wash note of 2026-10-22: its
     * strokes merge wet into wet with the watercolour washes and dry back as they do. Laid on the pair's wash half
     * ({@link ModularStroke#layerOf}), a pen line and all.
     */
    static boolean washes(ModularBrush b) {
        if (b.choice(BrushPart.LIFE) != PartChoice.WET_PAINT || b.choice(BrushPart.CARRY) == PartChoice.CLEAR_WATER) return false;
        PartChoice m = b.choice(BrushPart.MEDIUM);
        if ("Watercolor".equals(b.style.name)) return m == PartChoice.WASH;
        return "Ink wash".equals(b.style.name) && (m == PartChoice.WASH || m == PartChoice.INK && b.part(BrushPart.MEDIUM).get(WET) > .08);
    }
    final Wet wet;
    /**
     * Flick (the Tip's) and Fling (the Medium's), 0 to 1 (the mischief modules spec, Stage 2; {@link FlickFling}): how far
     * a moving lift carries the stroke on, and how many drops a hard stop throws. 0 for every brush before them, and
     * Fling for Resist and Screentone, laid as plain ink.
     */
    final double flick, fling;
    /**
     * Screen or Tone (the mischief modules spec): the print screen its mark reaches the page through, pure black and
     * white ({@link ModularCompose}); null for every other brush, and on water, where every medium floats as a darkening
     * film. A screened mark doesn't settle, run, drip, shed dust or grow mold: nothing lays a grey after it.
     */
    final PrintScreen screen;
    /**
     * Whether its stroke builds on the layer's print tone ({@link ToneDocument.Layer#print}, {@link PrintScreen#printed}):
     * a Screen laid the medium's own way (or Behind). White and Invert work from the mark over white, and Tone's dots
     * from its fill, as before.
     */
    /**
     * A screened mark's darkness over white, 0 to 1, where its line lays coverage {@code c} and its body {@code d}:
     * the line's at its ink, the body's {@link #SCREENED_BODY} of the swatch's grey at its fullest (its
     * {@link Blend#bodyAmt}), the more of the two. Both are even across the tip (crisp, the soft edge and grain off for a screen), so
     * the screen prints one tone over the whole footprint, set by the grey, pressure and the hand.
     */
    /** A screened body's darkest, a share of the swatch's: firm pressure at black still shows its pattern, not a solid blot. */
    static final double SCREENED_BODY = .8;
    double screenTone(double c, double d) {
        double body = d > 0 ? screenBody(d) * shade * load : 0;
        return clamp(Math.max(c * inkK, body));
    }
    /** How much of a screened mark lies here, whatever its grey: {@link #screenTone} over a light one ({@link #laysLight}). */
    double screenReach(double c, double d) { return clamp(Math.max(c * parts.inkShare * load, d > 0 ? screenBody(d) * load : 0)); }
    private double screenBody(double d) { return Math.min(1, parts.bodyAmt > 0 ? d / parts.bodyAmt : d) * SCREENED_BODY; }
    boolean prints() { return screen != null && !screen.tone && !blendMode.lays(); }
    /**
     * A print over prints (the owner, 2026-10-29: "go over that area again and make it the same pattern, but lighter"):
     * a screened mark (Print Shading, Screentone) of its own Blend, not opaque, replaces what its plane, Paint, holds
     * under its footprint, clear between its dots, so a lighter one over a darker one leaves the lighter. Pen, on a plane
     * of its own over it, stays; so do washes, under it. No light over dark ({@link #laysLight}): the clear shows them.
     */
    boolean replaces() { return screen != null && blendMode == BlendMode.OWN && !(cover > 0) && carry == null; }

    /** {@code darkness} is the swatch's, 0 (paper) to 1 (black): the prototype's shade. */
    static ResolvedBrush of(ModularBrush b, double size, double darkness) {
        PartChoice m = b.choice(BrushPart.MEDIUM);
        boolean resist = m == PartChoice.RESIST, tone = m == PartChoice.TONE;
        ModularBrush.Part halo = b.choice(BrushPart.LIFE) == PartChoice.HALO ? b.part(BrushPart.LIFE) : null;
        // Tone is laid as Resist is, a crisp ink line (on paper, never on water), through its own screen.
        PrintScreen screen = tone || b.choice(BrushPart.LIFE) != PartChoice.FLOAT ? PrintScreen.of(b.part(BrushPart.MEDIUM)) : null;
        ModularBrush a = resist || tone ? asInk(b) : m == PartChoice.CRAYON ? dryLine(b.copy()) : b;
        PartChoice life = b.choice(BrushPart.LIFE);
        // Wet: the engine lays it as Instant; the wet canvas does the rest (Opaque, Clear Water, Ink or Wash has one).
        if (life == PartChoice.WET_PAINT) {
            ModularBrush.Part L = b.part(BrushPart.LIFE);
            boolean water = b.choice(BrushPart.CARRY) == PartChoice.CLEAR_WATER;
            // A Watercolor Wash, or an Ink wash style's Wash or Ink with a body, is a wet wash (section 9); any other Ink or
            // Wash, ink in water (the Wet brush).
            boolean washes = washes(b);
            boolean inks = !water && !washes && (m == PartChoice.INK || m == PartChoice.WASH);
            double stays = L.get(STAYS_WET);
            ModularBrush.Part M = b.part(BrushPart.MEDIUM);
            Wet wet = washes ? new Wet(stays, L.get(RUNS), false, false, Wet.wash(b, stays), Wet.pull(b), M.get(LINE) > 0)
                    : m == PartChoice.COVER || water || inks ? new Wet(stays, L.get(RUNS), !"Oils".equals(b.style.name), inks) : null;
            return new ResolvedBrush(asInstant(a), size, darkness, resist, null, screen, null, wet);
        }
        FadingInk.Spec fade = life == PartChoice.FADE || life == PartChoice.LEMON
                ? new FadingInk.Spec(life == PartChoice.LEMON, (int) Math.round(1000 * b.part(BrushPart.LIFE).get(life == PartChoice.LEMON ? DRY_IN : FADE_IN))) : null;
        return new ResolvedBrush(halo != null ? asSlow(a) : fade != null ? asQuick(a) : a, size, darkness, resist, halo, screen, fade, null);
    }
    /** A Wet brush as the engine lays it ({@link Wet}): Instant, dry the moment the nib passes. */
    static ModularBrush asInstant(ModularBrush b) {
        ModularBrush a = b.copy();
        a.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.NONE));
        return a;
    }
    /** newmods.js resolve: a Fading or Lemon Juice brush as the engine lays it, Quick at its usual wet edge; the clock does the rest. */
    static ModularBrush asQuick(ModularBrush b) {
        ModularBrush a = b.copy();
        a.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.FAST_WET));
        return a;
    }
    /** newmods.js resolve: a Halo brush as the engine lays it, Slow's lift at its drying time with no settling of its own. */
    static ModularBrush asSlow(ModularBrush b) {
        ModularBrush a = b.copy();
        ModularBrush.Part slow = new ModularBrush.Part(PartChoice.DRYING);
        slow.set(PartDial.DRYING, b.part(BrushPart.LIFE).get(PartDial.DRYING, PartChoice.HALO.defaultOf(PartDial.DRYING)));
        slow.set(SETTLE, 0);
        a.set(BrushPart.LIFE, slow);
        return a;
    }
    /**
     * newmods.js resolve: a Resist brush as the engine lays it, a crisp line of ink (no wash, pooling, splatter or
     * broken line) at its Erasable, without a smudge (it smears nothing), on paper never on water (Float lays as Quick).
     * Tone's fill too, always erasable (it has no Erasable of its own).
     */
    static ModularBrush asInk(ModularBrush b) {
        ModularBrush a = b.copy();
        ModularBrush.Part ink = new ModularBrush.Part(PartChoice.INK);
        ink.set(ERASABLE, b.part(BrushPart.MEDIUM).get(ERASABLE, 1));
        a.set(BrushPart.MEDIUM, ink);
        return dryLine(a);
    }
    /** {@code a} without a smudge (it smears nothing), on paper never on water (Float lays as Quick): Resist's line, and Crayon's wax. */
    private static ModularBrush dryLine(ModularBrush a) {
        a.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.OFF));
        if (a.choice(BrushPart.LIFE) == PartChoice.FLOAT) a.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.FAST_WET));
        return a;
    }

    private ResolvedBrush(ModularBrush B, double size, double shade, boolean resist, ModularBrush.Part halo, PrintScreen screen, FadingInk.Spec fade, Wet wet) {
        this.resist = resist; this.screen = screen; this.fade = fade; this.wet = wet;
        ModularBrush.Part T = B.part(BrushPart.TIP), M = B.part(BrushPart.MEDIUM), C = B.part(BrushPart.CARRY), P = B.part(BrushPart.PAPER), L = B.part(BrushPart.LIFE);
        // Smudge's Own paint: what share of its own paint a smudging brush lays (Off: all of it).
        double load = ModularBrush.ownPaint(C);
        PartChoice tk = T.choice, mk = M.choice;
        boolean lineMedium = mk == PartChoice.INK || mk == PartChoice.CHARCOAL || mk == PartChoice.GRAPHITE || mk == PartChoice.CRAYON;
        boolean scratch = mk == PartChoice.CRAYON && (int) M.get(SCRATCH) == 1;
        this.scratch = scratch;
        this.wax = mk == PartChoice.CRAYON && !scratch ? M.get(WAX) : 0;
        double w = 0, lineKept = 1, wetMul = 1, cover = 0, impasto = 0, lineDark = 1;
        boolean wetMode = true;
        Rule rule = Rule.INK;
        if (mk == PartChoice.INK) { w = M.get(WET); lineKept = M.get(LINE); }
        else if (mk == PartChoice.WASH) {
            w = 1; lineKept = M.get(LINE); wetMode = M.get(WET) >= .5;
            wetMul = wetMode ? lerp(.4, 1, (M.get(WET) - .5) * 2) : 1;
        }
        else if (mk == PartChoice.COVER) { w = 1; lineKept = 0; wetMode = false; cover = M.get(PartDial.COVER); impasto = M.get(IMPASTO); }
        else rule = mk == PartChoice.CHARCOAL ? Rule.CHARCOAL : mk == PartChoice.CRAYON ? Rule.CRAYON : Rule.GRAPHITE;
        if (mk == PartChoice.CHARCOAL) lineDark = lerp(.72, 1, M.get(HOLD));
        if (mk == PartChoice.GRAPHITE) lineDark = 1 - lerp(185, 40, M.get(GRADE)) / 255;
        double weight = T.get(WEIGHT, 1);
        double inkSize, washSize;
        if (lineMedium) { inkSize = size * weight; washSize = Math.min(360, inkSize * 8); }
        else { washSize = size * weight; inkSize = Math.max(1.5, washSize / 10); }
        // Charcoal is the costliest stamp (its fuzz never fills): capped at 240 px.
        if (mk == PartChoice.CHARCOAL) inkSize = Math.min(inkSize, 240);
        // Crayon's Scratch is a blade: mischief-lab-2.html's 0.7–2 px against its crayon's 3.2–6.6.
        if (scratch) inkSize = Math.max(1.5, inkSize * .3);

        // The prototype's bb: the brush as pen-paint-blend's partsA reads it. A bristle tip lays no dry brush of its own (its
        // hairs run dry); without the swarm it draws as a round tip whose dryness is its dry brush.
        boolean swarm = tk == PartChoice.BRISTLES && ModularFeatures.BRISTLES;
        boolean roundNib = !(tk == PartChoice.CHISEL || tk == PartChoice.SPLIT_NIB || tk == PartChoice.FLAT_NIB || tk == PartChoice.FILBERT);
        double speedAmt = B.handCell(SS);
        boolean thicker = (int) B.part(BrushPart.HAND).get(DIR) == 1;
        double angle = T.get(ANGLE, 45);
        double bristle = tk == PartChoice.BRISTLES ? (swarm ? 0 : T.get(DRYNESS)) : T.get(DRY);
        double bbSpeed = speedAmt == 0 ? 0 : (thicker ? 1 : -1) * speedAmt;
        Lines lines = Lines.CENTRE;
        double spreadBb = .6, breathe = 0;
        int count = (int) Math.round(T.get(COUNT, 3));
        if (mk == PartChoice.INK) lines = new Lines[]{Lines.CENTRE, Lines.OUTLINE, Lines.SQUIGGLE}[Math.max(0, Math.min(2, (int) M.get(LINES)))];
        if (tk == PartChoice.SPLIT_NIB) { lines = Lines.SPLIT; spreadBb = T.get(GAP); breathe = Math.max(.25, B.handCell(PW)); }
        if (tk == PartChoice.MULTI_LINE) { lines = Lines.SPREAD; spreadBb = T.get(REACH); breathe = B.handCell(PW); }

        // partsA (parts.js 249–274): two parts, a line and a wash, fed the same samples.
        double s = smooth(w), mid = 4 * w * (1 - w);
        Pen pen = new Pen();
        pen.seed = B.seed; pen.aspect = roundNib ? 1 : T.get(ASPECT); pen.angle = angle; pen.taperIn = T.get(TAPER_IN); pen.taperOut = T.get(TAPER_OUT);
        pen.pressure = B.handCell(PS); pen.speed = bbSpeed; pen.dry = bristle * (1 - w); pen.tooth = P.get(TOOTH) * (1 - w);
        pen.wobble = T.get(WOBBLE); pen.pool = M.get(POOL) * (1 - w); pen.splatter = M.get(SPLATTER);
        pen.lines = lines; pen.count = count; pen.spread = spreadBb; pen.breathe = breathe; pen.wetGap = clamp(w * 1.8);
        pen.offset = M.get(OFFSET); pen.chaos = M.get(CHAOS);
        Blend Pa = new Blend();
        Pa.w = w;
        Pa.inkShare = mix(1, lineKept, s);
        Pa.lineWet = mid * 1.2 + w * lineKept;
        Pa.bodyAmt = smooth((w - .08) / .75);
        Body body = new Body();
        body.seed = B.seed; body.wet = wetMode; body.pressure = pen.pressure; body.soft = mix(1, T.get(SOFT, .3), s); body.streaks = bristle;
        body.aspect = mix(pen.aspect, 1, s); body.angle = angle; body.taperIn = pen.taperIn * (1 - w); body.taperOut = pen.taperOut * (1 - w);
        body.amount = Pa.bodyAmt;
        Pa.bodySize = mix(inkSize * 2.2, washSize, Math.pow(w, 1.4));
        if (pen.offset > 0) {
            WildBrush.Random r = new WildBrush.Random(B.seed ^ 0x1a6);
            double a = pen.offset * (14 + Pa.bodySize * .55) * (.4 + .6 * r.next());
            double bLen = pen.offset * (14 + Pa.bodySize * .55) * (.4 + .6 * r.next());
            if (r.next() < .5) pen.skipStart = a; else body.skipStart = a;
            if (r.next() < .5) pen.skipEnd = bLen; else body.skipEnd = bLen;
        }
        double grain = P.get(TOOTH);
        Look look = new Look();
        look.feather = clamp(M.get(BLEED) * wetMul * s + .3 * mid);
        look.hollow = wetMode ? M.get(POOL) * s : 0;
        look.bloom = wetMode ? M.get(BLOOM) * wetMul * s : 0;
        look.backrun = wetMode ? M.get(BACKRUN) * wetMul * s : 0;
        look.gran = grain * s;
        Pa.lineSize = inkSize;
        Pa.diffuse = mid;
        Pa.diffR = (int) Math.round(1 + mid * (1.5 + inkSize * .5));
        Pa.stampLine = Pa.inkShare > 0 || Pa.lineWet > 0;
        Pa.placed = pen.lines != Lines.CENTRE || pen.offset > 0 || pen.chaos > 0;

        // The hand feeds every stamper.
        Life life = L.choice == PartChoice.NONE ? Life.NONE : L.choice == PartChoice.FAST_WET ? Life.WET : L.choice == PartChoice.FLOAT ? Life.FLOAT : Life.DRY;
        boolean fast = life == Life.NONE;
        pen.fast = fast; pen.tiltSize = B.handCell(TS); pen.tiltAngle = B.handCell(TW); pen.tDark = B.handCell(TD);
        pen.pDark = B.handCell(PD); pen.sDark = B.handCell(SD); pen.pWidth = B.handCell(PW); pen.sAngle = B.handCell(SW);
        pen.fuzz = rule == Rule.INK ? 0 : M.get(SPREAD);
        pen.tiltWide = (int) B.part(BrushPart.HAND).get(TILT_RANGE) == 1;
        pen.gSize = B.handCell(GS); pen.gDark = B.handCell(GD); pen.gWidth = B.handCell(GW);
        pen.thicker = thicker;
        pen.filbert = tk == PartChoice.FILBERT;
        pen.crayon = mk == PartChoice.CRAYON && !scratch;
        pen.side = B.part(BrushPart.HAND).get(SIDE_SHADE, 0);
        if (tk == PartChoice.SPLIT_NIB || tk == PartChoice.MULTI_LINE) pen.pWidth = 0;   // pressure → width is their breathing, inside the placement
        body.fast = fast; body.tiltSize = pen.tiltSize; body.tDark = pen.tDark; body.pDark = pen.pDark; body.sDark = pen.sDark;
        body.pWidth = B.handCell(PW); body.sAngle = pen.sAngle; body.gSize = pen.gSize; body.gDark = pen.gDark;
        body.cover = cover; body.impasto = impasto; body.gran = look.gran; body.flatHead = tk == PartChoice.HAKE;
        body.headFixed = (int) T.get(HEAD_TURN) == 1;
        body.tilt = Math.max(pen.tiltSize, pen.tiltAngle);
        // Paint's own speed and nib: a body follows the hand's speed too (0 for every old paint), and a shaped tip's ellipse.
        body.speed = lineMedium ? 0 : bbSpeed;
        if (!lineMedium && (tk == PartChoice.CHISEL || tk == PartChoice.FLAT_NIB)) { body.aspect = clamp(T.get(ASPECT) * 1.6, .25, 1); body.angle = angle; }
        // A Filbert's paint is its own oval, at its width, following tilt.
        if (tk == PartChoice.FILBERT) { body.filbert = true; if (!lineMedium) { body.aspect = T.get(ASPECT); body.angle = angle; } }
        look.cover = cover; look.impasto = impasto;
        if (screen != null) {
            // Screened (Print Shading, Screentone): the screen inks the mark's darkness, so a grey that falls off at its
            // edge or breaks up in its grain left only the core inked, a squiggle down a stroke meant to fill. Its
            // footprint is crisp and even instead: no soft edge, dry brush, streaks, tooth, stick fuzz, wet edges or
            // line bled into the wash, so a pixel under the tip has the stroke's own tone (screenTone: its grey and
            // pressure) and the screen sets its pattern there.
            body.soft = 0; body.crisp = true; body.streaks = 0; body.gran = 0; Pa.diffuse = 0; Pa.lineWet = 0;
            pen.dry = 0; pen.tooth = 0; pen.fuzz = 0; pen.side = 0;
            look.feather = 0; look.hollow = 0; look.bloom = 0; look.backrun = 0; look.gran = 0;
        }

        this.parts = Pa; this.pen = pen; this.look = look; this.rule = rule; this.paper = P.choice.texture(); this.life = life;
        this.inkSize = inkSize; this.washSize = washSize;
        this.bodySize = lineMedium ? (wet != null && wet.wash != null ? wetBody(Pa.bodySize, inkSize) : Pa.bodySize) : washSize;
        this.markerInk = mk == PartChoice.WASH && L.choice == PartChoice.NONE && B.style != null && "Marker".equals(B.style.name);
        this.lineTooth = pen.tooth; this.cover = cover * load; this.gray = 255 * (1 - shade); this.shade = shade;
        this.hold = M.get(HOLD); this.spread = M.get(SPREAD); this.grade = M.get(GRADE); this.lineInk = mk == PartChoice.INK;
        this.windowLen = L.get(WINDOW, 6) * 12 + 0.5 * (lineMedium && w < .5 ? inkSize : washSize);
        this.drying = L.get(PartDial.DRYING, .35);
        // Blend: White and Invert carry no pigment, so they don't settle, run, drip or shed dust; on water it does nothing.
        BlendMode mode = scratch ? BlendMode.WHITE : life == Life.FLOAT ? BlendMode.OWN : BlendMode.values()[(int) M.get(M_BLEND, 0)];
        this.blendMode = mode;
        this.settle = life == Life.DRY && !mode.lays() && screen == null ? L.get(SETTLE, 0) : 0;
        // A smudge with no paint of its own (Own paint 0: a finger, a stump) lays no line and no body: only its smudge
        // draws (the web lab lays them unseen).
        boolean ownPaint = load > 0;
        this.body = Pa.bodyAmt > 0 && ownPaint ? body : null;
        this.heldBack = cover > 0 ? 0 : .45 * settle;
        // Gravity: wet paint only (Quick's window, Slow after lift), never opaque Cover (the Gravity Lab's resolve).
        Grav grav = null;
        if ((life == Life.WET || life == Life.DRY) && !(cover > 0) && !mode.lays() && screen == null) {
            boolean dry = life == Life.DRY;
            double runs = L.get(RUNS), pool = L.get(G_POOL), sink = L.get(SINK), drips = dry ? L.get(DRIPS) : 0, blend = dry ? L.get(BLEND) : 0;
            if (runs > 0 || pool > 0 || sink > 0 || drips > 0 || blend > 0) grav = new Grav(runs, drips, pool, sink, blend);
        }
        this.grav = grav;
        this.dust = mk == PartChoice.CHARCOAL && !mode.lays() && screen == null ? M.get(DUST) : 0;
        this.erase = M.get(ERASABLE, 1);
        // Opaque paint is Paint to the eraser, on a plane of its own over pen ({@link ToneDocument#KIND_OPAQUE}). A texture
        // (Stipple, Hatch, Hand Hatch, Print Shading, Screentone) is Paint whatever its medium, so Pen erases only pen lines
        // (the owner, 2026-10-29: "a way to erase one or the other"); on the Paint plane under pen, it looks as it did.
        boolean texture = screen != null || tk == PartChoice.STIPPLE || tk == PartChoice.HATCH || tk == PartChoice.HAND_HATCH;
        this.medium = mk == PartChoice.COVER ? ToneDocument.KIND_PAINT | ToneDocument.KIND_OPAQUE : mk == PartChoice.WASH || texture ? ToneDocument.KIND_PAINT
                : mk == PartChoice.CHARCOAL || mk == PartChoice.GRAPHITE || mk == PartChoice.CRAYON ? ToneDocument.KIND_PENCIL : ToneDocument.KIND_PEN;
        this.kind = ownPaint ? medium : 0;
        this.wetMs = PartDial.wetMs(drying, life == Life.DRY && grav != null);
        // Clear Water's wet settles after lift as a wash's does, what it lifted running out through it (ClearWater).
        boolean water = C.choice == PartChoice.CLEAR_WATER && life != Life.FLOAT;
        this.settlesAfterLift = life == Life.DRY && (this.body != null || water) && (heldBack > 0 || grav != null);
        this.line = Pa.stampLine && ownPaint;

        // Which stamper draws each part. Without the swarm a bristle tip is a nib's line and a plain body.
        // A Stipple, Hatch or Stamp tip resolves as a round tip of its weight (it has no taper, dry brush, wobble or nib
        // of its own, so the round's are off and its soft edge the default) and lays its dots, lines or prints there instead.
        // A Stamp prints its nib as big as its tip: the line's size for ink and the sticks, the wash's for paint; a Spiro
        // draws its curve as wide.
        boolean pattern = tk == PartChoice.STIPPLE || tk == PartChoice.HATCH || tk == PartChoice.STAMP || tk == PartChoice.SPIRO || tk == PartChoice.HAND_HATCH;
        this.pattern = pattern ? new Pattern(B, lineMedium ? inkSize : washSize) : null;
        this.lineTip = tk == PartChoice.HAKE ? LineTip.HAKE : swarm ? LineTip.BRISTLES : tk == PartChoice.STIPPLE ? LineTip.STIPPLE : tk == PartChoice.HATCH ? LineTip.HATCH
                : tk == PartChoice.STAMP ? LineTip.STAMP : tk == PartChoice.SPIRO ? LineTip.SPIRO : tk == PartChoice.HAND_HATCH ? LineTip.HAND_HATCH : LineTip.NIB;
        this.linePlaced = lineTip == LineTip.NIB && Pa.placed;
        this.lineStateful = lineTip != LineTip.NIB;
        this.placeBody = body;
        // A multi-line tip spreads its lines over a band wider than one line: the line plus 1.4 lines' room per extra line.
        boolean multi = tk == PartChoice.MULTI_LINE;
        this.placeSize = multi ? Math.max(Pa.bodySize, inkSize * (2 + 1.4 * count)) : Pa.bodySize;
        this.lineReach = linePlaced ? (int) Math.ceil(Math.max(Pa.bodySize, multi ? inkSize * (2 + 1.4 * count) : 0) * .9 + inkSize * 3
                + (pen.offset > 0 ? Pa.bodySize * 1.4 + inkSize * 2.5 : 0) + (pen.chaos > 0 ? Pa.bodySize * .6 + inkSize : 0)) : 0;
        if (lineTip == LineTip.HAKE) {
            Body h = new Body();
            h.seed = B.seed; h.wet = false; h.pressure = B.handCell(PS); h.soft = T.get(SOFT, .2); h.streaks = T.get(DRY); h.gran = 0;
            h.angle = angle; h.headFixed = body.headFixed; h.tilt = body.tilt; h.fast = false; h.flatHead = true; h.cover = 0;
            h.pDark = B.handCell(PD); h.pWidth = B.handCell(PW); h.softK = lerp(.012, .6, T.get(SOFT, .2)); h.gSize = B.handCell(GS);
            h.amount = 1;   // the prototype's lineHake has no amount: the hake reads it as 1 (amount ?? 1)
            this.lineHake = h;
        } else this.lineHake = null;
        if (swarm) {
            Swarm sw = new Swarm();
            sw.seed = B.seed; sw.hairs = T.get(HAIRS); sw.cohesion = T.get(COHESION); sw.splay = T.get(SPLAY); sw.dryness = T.get(DRYNESS);
            sw.load = T.get(INK_LOAD); sw.pressure = pen.pressure; sw.tiltSize = pen.tiltSize; sw.tDark = pen.tDark; sw.pDark = pen.pDark; sw.sDark = pen.sDark;
            sw.gSize = pen.gSize; sw.gDark = pen.gDark;
            this.lineSwarm = lineTip == LineTip.BRISTLES ? sw : null;
            Swarm bs = sw.copy();
            bs.amount = Pa.bodyAmt;
            this.bodySwarm = bs;
        } else { this.lineSwarm = null; this.bodySwarm = null; }
        this.bodyTip = swarm ? BodyTip.BRISTLES : tk == PartChoice.STIPPLE ? BodyTip.STIPPLE : tk == PartChoice.HATCH ? BodyTip.HATCH : tk == PartChoice.STAMP ? BodyTip.STAMP
                : tk == PartChoice.SPIRO ? BodyTip.SPIRO : tk == PartChoice.HAND_HATCH ? BodyTip.HAND_HATCH : (tk == PartChoice.SPLIT_NIB || multi) && !lineMedium ? BodyTip.PLACED : BodyTip.BODY;
        this.bodyStateful = tk == PartChoice.HAKE || swarm || pattern;
        if (bodyTip == BodyTip.PLACED) {
            boolean split = tk == PartChoice.SPLIT_NIB;
            this.bodyLineSize = washSize * (split ? .55 : .22);
            Pen bp = pen.copy();
            bp.lines = split ? Lines.SPLIT : Lines.SPREAD; bp.wetGap = 0; bp.dry = T.get(DRY); bp.aspect = split ? T.get(ASPECT) : 1;
            bp.skipStart = 0; bp.skipEnd = 0; bp.pool = 0; bp.splatter = 0;
            this.bodyPen = bp;
            Body g = body.copy();
            g.aspect = 1;
            this.bodyGeom = g;
            this.bodyPlaceSize = multi ? washSize * 1.8 : washSize;
        } else { this.bodyLineSize = 0; this.bodyPen = null; this.bodyGeom = null; this.bodyPlaceSize = 0; }

        // How dark: the swatch's grey, the share of the line that stays crisp, charcoal's and graphite's darkest, and
        // Smudge's Own paint, opaque cover's too (Smudge spec F1). A body glazes at a third of the swatch, as the app's
        // paint shades; a wash to near black (ModularSizes.washStrength: its full body lays .97 of the swatch's grey).
        this.load = load;
        this.inkK = shade * Pa.inkShare * lineDark * load;
        boolean washes = mk == PartChoice.WASH;
        this.washK = (washes ? ModularSizes.washStrength(shade) : ModularSizes.GLAZE * shade) * load;
        this.previewShade = washes ? ModularSizes.WASH_BLACK * shade / ModularSizes.GLAZE : shade;
        // Stay in Lines moves no paint along: it keeps the stroke inside the lines and floods on through them (StayIn).
        // Filled Shape neither: its stroke is the outline, filled at the lift (FilledShape).
        this.stayIn = C.choice == PartChoice.STAY_IN && life != Life.FLOAT && (this.line || this.body != null) ? C.get(FLOOD) : -1;
        if (C.choice != PartChoice.OFF && C.choice != PartChoice.STAY_IN && C.choice != PartChoice.SHAPE && life != Life.FLOAT) {
            // As wide as the mark, to 180 px, half the largest brush (F4).
            double r = Math.max(3, Math.min(180, (lineMedium && w < .4 ? inkSize : washSize) / 2));
            double soften = C.get(SOFTEN, 0);
            // A stump that is a Rake or a Split nib combs: it softens along its tines (the Stump spec).
            double[] tines = soften > 0 ? tinesOf(T, count, placeSize / 2) : null;
            this.carry = new Carry(C.choice, C.get(PICK, .4), C.get(LAY, .5), r, !(C.choice == PartChoice.LIFT && this.cover > 0), soften,
                    tines == null ? null : java.util.Arrays.copyOf(tines, tines.length - 1), tines == null ? 0 : tines[tines.length - 1], C.get(KEEPS, 0));
        } else this.carry = null;
        this.tint = carry != null && carry.kind == PartChoice.LIFT && this.cover > 0 ? Math.min(1, 2 * carry.pick) : 0;
        // Paint Load: a Drag's or Smear's own paint runs out (only one that lays some); a Filbert's dabs are crescents then.
        boolean runsOut = carry != null && (C.choice == PartChoice.DRAG || C.choice == PartChoice.SMUDGE) && (this.line || this.body != null);
        this.loaded = runsOut ? LoadedPaint.of(C, this.body != null ? this.bodySize : inkSize) : null;
        if (loaded != null && body.filbert) body.crescent = true;
        this.flt = life == Life.FLOAT ? floatParts(T, C, L, Pa, load, count) : null;
        this.halo = halo == null || resist || mode.lays() || screen != null ? null : haloParts(halo);
        this.flick = T.get(FLICK, 0); this.fling = M.get(FLING, 0);
    }

    /**
     * newmods.js resolve's R.halo: its Reach 12 to 80 px, grown with the square root of the tip past 6 px (up to three
     * times), at most {@link Halo#MOST_WIDTHS} tip widths; its drying time's ticks, at most {@link Halo#MOST_TICKS}; a
     * line's darkness or what a body half again as strong lays ({@link ModularSizes#laid}).
     */
    private Halo haloParts(ModularBrush.Part L) {
        double width = line ? inkSize : bodySize, big = clamp(Math.sqrt(width / 6), 1, 3);
        long reach = Math.min(Math.round((12 + 68 * L.get(H_REACH)) * big), Math.max(1, Math.round(Halo.MOST_WIDTHS * width)));
        long ticks = Math.min(Halo.MOST_TICKS, Math.max(3, Math.round(PartDial.wetMs(L.get(PartDial.DRYING)) * DryingWater.TPS / 1000.0)));
        return new Halo((int) reach, L.get(GROWTH), (int) ticks, line ? clamp(inkK) : ModularSizes.laid(washK * 1.5), blendMode == BlendMode.BEHIND);
    }

    /**
     * A Rake's or a Split nib's tines (parts.js floatParts): each one's offset across the stroke, a Rake's spread over
     * {@code hw} either side, then their half-width; null for a tip that drags with one point.
     */
    private double[] tinesOf(ModularBrush.Part T, int count, double hw) {
        if (T.choice == PartChoice.MULTI_LINE) {
            double half = hw * lerp(.3, .95, T.get(REACH)), gap = 2 * half / Math.max(1, count - 1);
            double[] t = new double[count + 1];
            for (int j = 0; j < count; j++) t[j] = -half + gap * j;
            t[count] = Math.max(2.5, Math.min(gap / 3, inkSize * .4));
            return t;
        }
        if (T.choice == PartChoice.SPLIT_NIB) {
            double g = Math.max(2, inkSize * .6 * (T.get(GAP) + .3));
            return new double[]{-g, g, Math.max(2.5, inkSize * .4)};
        }
        return null;
    }

    /**
     * parts.js floatParts: the film's half-width from the line and body the brush would lay (clear water's too, which
     * lays neither, so its drops are the size of its paint's), the tines (one stylus as wide as the tip, a hake's broad;
     * one per line of a split nib or multi-line) and the greys.
     */
    private Float floatParts(ModularBrush.Part T, ModularBrush.Part C, ModularBrush.Part L, Blend Pa, double load, int count) {
        PartChoice tk = T.choice;
        boolean drags = C.choice == PartChoice.DRAG || C.choice == PartChoice.SMUDGE || C.choice == PartChoice.CLEAR_WATER, stamps = Pa.bodyAmt > 0, multi = tk == PartChoice.MULTI_LINE;
        double lineW = inkSize / 2 * (multi ? count : tk == PartChoice.SPLIT_NIB ? 2 : 1);
        double filmW = Math.max(Math.max(stamps ? bodySize / 2 : 0, Pa.stampLine ? lineW : 0), 1);
        double hw = stamps ? bodySize / 2 : multi ? placeSize / 2 : inkSize / 2;
        double[] tines = {0};
        double sig = Math.max(3, hw * (tk == PartChoice.HAKE ? .7 : .8));
        double[] split = tinesOf(T, count, hw);
        if (split != null) { tines = java.util.Arrays.copyOf(split, split.length - 1); sig = split[split.length - 1]; }
        return new Float(L.get(F_SPREAD, .5), L.get(DRIFT, .5), PartDial.wetMs(L.get(PartDial.DRYING, .6)), load, drags ? C.get(PICK, .4) : 0,
                filmW, tines, sig, clamp(inkK), ModularSizes.laid(washK * 1.2), Pa.stampLine ? clamp(inkK) : ModularSizes.laid(washK * 1.2));
    }
}
