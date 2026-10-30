package io.github.mpdairy.monopaint;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Every dial of a modular brush (parts.js 19–75): its part, label (display words, docs/TEXT-STYLE.md; the key is the
 * code's), range, default, options and how it's shown.
 * A choice dial holds an option index. Which dials a choice has, and in what order, is the choice's
 * ({@link PartChoice}): codes are written in the choice's order, not this one. Pure, checked on the desktop.
 */
enum PartDial {
    // Tip
    WEIGHT(BrushPart.TIP, "weight", "Size", .5, 1.5, 1),
    ASPECT(BrushPart.TIP, "aspect", "Nib Width", .14, .55, .4),
    ANGLE(BrushPart.TIP, "angle", "Nib Angle", 20, 70, 40),
    TAPER_IN(BrushPart.TIP, "taperIn", "Taper In", 0, 1, 0),
    TAPER_OUT(BrushPart.TIP, "taperOut", "Taper Out", 0, 1, 0),
    DRY(BrushPart.TIP, "dry", "Dry Brush", 0, 1, 0),
    WOBBLE(BrushPart.TIP, "wobble", "Wobble", 0, 1, 0),
    SOFT(BrushPart.TIP, "soft", "Soft Edge", 0, 1, .3),
    HEAD_TURN(BrushPart.TIP, "headTurn", "Head Follows", 0, "Stroke", "Page"),
    GAP(BrushPart.TIP, "gap", "Tine Gap", 0, 1, .55),
    COUNT(BrushPart.TIP, "count", "Lines", 2, 5, 3, true),
    REACH(BrushPart.TIP, "reach", "Spacing", 0, 1, .6),
    HAIRS(BrushPart.TIP, "hairs", "Hairs", 6, 32, 26, true),
    COHESION(BrushPart.TIP, "cohesion", "Clump", 0, 1, .55),
    SPLAY(BrushPart.TIP, "splay", "Splay", 0, 1, .45),
    DRYNESS(BrushPart.TIP, "dryness", "Dry Brush", 0, 1, .15),
    /**
     * Not the prototype's: how far a bristle brush's load carries before its hairs run dry ({@link BristleSwarm#carry}):
     * the middle, three times as far as the prototype's; 0 a quarter of that, 1 four times. Last of Bristles' dials,
     * written only when off its default ({@link ModularCode#ADDED}).
     */
    INK_LOAD(BrushPart.TIP, "load", "Paint Load", 0, 1, .5),
    /** Stipple ({@link StippleStamper}): how big its dots are, how far they scatter past the tip, even to sponged. */
    DOT(BrushPart.TIP, "dot", "Dot Size", 0, 1, .35),
    SCATTER(BrushPart.TIP, "scatter", "Scatter", 0, 1, .4),
    JITTER(BrushPart.TIP, "jitter", "Uneven Dots", 0, 1, .5),
    /**
     * Not the prototype's first draft (the owner's ask, as other apps' sprays do): dots keep landing in the tip while the
     * pen rests or lingers, slowly or fast, so a held spot builds up. Last of Stipple's dials, written only when on
     * ({@link ModularCode#ADDED}).
     */
    BUILD_UP(BrushPart.TIP, "hold", "Build-Up", 0, "Off", "Slow", "Fast"),
    /** Hatch ({@link HatchStamper}): the lines' spacing and weight, their angle on the page (or to the stroke), how many directions pressure crosses. */
    PITCH(BrushPart.TIP, "pitch", "Spacing", 0, 1, .4),
    LINE_W(BrushPart.TIP, "lineW", "Line Weight", 0, 1, .3),
    HANGLE(BrushPart.TIP, "hangle", "Line Angle", 0, 180, 45),
    LAYERS(BrushPart.TIP, "layers", "Layers", 1, 3, 3, true),
    /**
     * Flick (the mischief modules spec, Stage 2; {@link FlickFling}): how far a stroke lifted while still moving carries
     * on past the lift, tapering dry; off, a stroke that ends where the pen lifts. Last of every Tip choice's dials,
     * written only when on ({@link ModularCode#ADDED}); rolled rarely from the seed ({@link ModularStyle#rollFlick}).
     */
    FLICK(BrushPart.TIP, "flick", "Flick", 0, 1, 0),
    /**
     * Stamp (mischief-lab-2.html's; {@link StampStamper}): how far apart its prints land, a share of its size: small, a
     * continuous nib trail; large, separate prints (a tap prints once).
     */
    STAMP_SPACING(BrushPart.TIP, "spacing", "Spacing", .05, 1.5, .12),
    /**
     * Stamp's Ink Fade (the owner, 2026-10-19: "be nice to have it not fade sometimes"): the share of its ink each print
     * uses, None (every print as dark as the first) to Strong; a rest of about a second inks it again.
     */
    INK_FADE(BrushPart.TIP, "inkFade", "Ink Fade", 0, .3, .22),
    /**
     * Spiro (mischief-lab-2.html's Spirograph; {@link SpiroStamper}): Line, the curve rides the stroke; Ring, the toy,
     * its ring pinned where the stroke starts, circling the pen turning its wheel; Oval and Bar, the toy's oval and bar
     * tracks, set up corner to corner (the owner, 2026-10-26).
     */
    SPIRO_MODE(BrushPart.TIP, "mode", "Mode", 0, "Line", "Ring", "Oval", "Bar"),
    /** Spiro's curve: the toy's (a wheel inside the ring), the wheel outside, a rose, a Lissajous figure, two pendulums, a coil. */
    CURVE(BrushPart.TIP, "curve", "Curve", 0, "Spirograph", "Epi", "Rose", "Lissajous", "Harmonograph", "Coil"),
    /** Spiro: how many turns of the curve a centimetre of the line takes (Line), and a ring's coil. */
    LOOPS(BrushPart.TIP, "loops", "Loops per cm", .3, 4, 2),
    /** Spiro, the toy's wheel and ring: the ring's teeth, the wheel (an index into {@link SpiroStamper#WHEELS}) and its pen hole, a share of its radius. */
    TEETH(BrushPart.TIP, "teeth", "Ring Teeth", 0, "96", "105"),
    WHEEL(BrushPart.TIP, "wheel", "Wheel", 0, 17, 4, true),
    HOLE(BrushPart.TIP, "hole", "Hole", .2, .9, .8),
    /** Spiro's Rose: how many petals. */
    PETALS(BrushPart.TIP, "petals", "Petals", 3, 12, 5, true),
    /** Spiro's Lissajous and Harmonograph: the two swings' ratio and the first's head start (0 to 90°). */
    RATIO(BrushPart.TIP, "ratio", "Ratio", 1, "1:2", "2:3", "3:4", "3:5"),
    PHASE(BrushPart.TIP, "phase", "Phase", 0, 1, .5),
    /** Spiro's Harmonograph: the turns its swing takes to die away to 5%. */
    FADES(BrushPart.TIP, "fades", "Fade Turns", 3, 40, 12, true),
    /**
     * Hatching's stroke ({@link HandHatchStamper}): how long each hatch stroke is (about half the tip to one and a half
     * times it), how loosely the hand lays them (neat, nearly ruled, to loose: each turned, bowed, strayed off its row and
     * varied in length and weight) and how its end flicks away (blunt to a thin flick).
     */
    HLEN(BrushPart.TIP, "hlen", "Line Length", 0, 1, .5),
    HWOB(BrushPart.TIP, "hwob", "Wobble", 0, 1, .5),
    HENDS(BrushPart.TIP, "hends", "Taper Out", 0, 1, .7),
    // Medium
    WET(BrushPart.MEDIUM, "wet", "Wet", 0, 1, 0),
    LINE(BrushPart.MEDIUM, "line", "Pen Line", 0, 1, 1),
    POOL(BrushPart.MEDIUM, "pool", "Pooling", 0, 1, 0),
    SPLATTER(BrushPart.MEDIUM, "splatter", "Spatter", 0, 1, 0),
    BLEED(BrushPart.MEDIUM, "bleed", "Bleed", 0, 1, 0),
    BLOOM(BrushPart.MEDIUM, "bloom", "Bloom", 0, 1, 0),
    BACKRUN(BrushPart.MEDIUM, "backrun", "Backruns", 0, 1, 0),
    LINES(BrushPart.MEDIUM, "lines", "Line Style", 0, "Centre", "Outline", "Squiggle"),
    OFFSET(BrushPart.MEDIUM, "offset", "Line Shift", 0, 1, 0),
    CHAOS(BrushPart.MEDIUM, "chaos", "Breaks", 0, 1, 0),
    COVER(BrushPart.MEDIUM, "cover", "Opacity", 0, 1, 1),
    IMPASTO(BrushPart.MEDIUM, "impasto", "Thick Paint", 0, 1, 0),
    SPREAD(BrushPart.MEDIUM, "spread", "Messy Edge", 0, 1, .3),
    HOLD(BrushPart.MEDIUM, "hold", "Density", 0, 1, .4),
    GRADE(BrushPart.MEDIUM, "grade", "Grade", 0, 1, .4),
    /** Gravity: charcoal's dust falling downhill at lift on a tilted page (the Gravity Lab's; Charcoal's last dial). */
    DUST(BrushPart.MEDIUM, "dust", "Dust When Tilted", 0, 1, 0),
    /**
     * How much of a mark the eraser takes (the Erasable spec): off, permanent, to 1, erased clean. Last of every Medium
     * choice's dials, written only when off its default ({@link ModularCode#ADDED}), so a code that doesn't say reads 1,
     * as every brush erased before. A brush made new starts at 1 ({@link PartChoice#erasable}); no roll changes it
     * ({@link ModularStyle#keepErasable}): only the hand on the dial (Resist's, wax or fluid, apart).
     */
    ERASABLE(BrushPart.MEDIUM, "erase", "Erasable", 0, 1, 1),
    /**
     * Blend (the new modules spec): how a mark meets what is already there. 0 the medium's own way (Darken; Opaque's
     * Cover, {@link PartChoice#optionsOf}), 1 White (key {@code lighten}: lays white, the shade setting how strongly), 2
     * Behind (only where the page is still blank), 3 Invert. After Erasable on every Medium choice, written only when
     * not 0 ({@link ModularCode#ADDED}). Not gravity's {@link #BLEND} (Drying's rocking).
     */
    M_BLEND(BrushPart.MEDIUM, "blend", "Blend Mode", 0, "Darken", "White", "Behind", "Invert"),
    /**
     * Screen (the mischief modules spec, Stage 1): a print screen the mark's greys reach the page through, pure black and
     * white by a threshold fixed to the page ({@link PrintScreen}): none, halftone, engraving (a line screen), dither,
     * grain, then (appended, so old codes read as before) rosette, Ben-Day, crosshatch, wavy lines, mezzotint and diamond dots, then stipple, woodcut, scribble and hatching, whose look changes as they darken; shown as {@link PrintScreen#LABEL} (the owner's "Print shading"). After
     * Blend on every medium that has it, with its own dials after it, each written only when not its default
     * ({@link ModularCode#ADDED}).
     */
    SCREEN(BrushPart.MEDIUM, "screen", PrintScreen.LABEL, 0, "None", "Halftone", "Engraving", "Dither", "Grain", "Rosette", "Ben-Day", "Crosshatch", "Wavy Lines", "Mezzotint", "Diamond Dots", "Stipple", "Woodcut", "Scribble", "Hatching"),
    /** Screen's and Tone's cell, page px (the prototype's 3 to 16; a pixel there is about one of the Manta's, 6 px about .5 mm), and their angle. */
    CELL(BrushPart.MEDIUM, "cell", "Pattern Size", 3, 16, 6, true),
    S_ANGLE(BrushPart.MEDIUM, "sangle", "Angle", 0, 90, 45),
    /** Screen's newsprint dials: a faint second pass offset this many page px (Misregister, shown as Double Print), dots swelling a ragged pixel (Ink gain, Dot Swell), specks dropped where the ink ran thin (Uneven, Patchy Ink). */
    MIS(BrushPart.MEDIUM, "mis", "Double Print", 0, 8, 0, true),
    S_GAIN(BrushPart.MEDIUM, "gain", "Dot Swell", 0, 1, 0),
    UNEVEN(BrushPart.MEDIUM, "uneven", "Patchy Ink", 0, 1, 0),
    /** Tone (screentone): the pattern laid over its fill (dots, lines, sand, a gradient down the page) and how dark it is. */
    PATTERN(BrushPart.MEDIUM, "pattern", "Pattern", 0, "Dots", "Lines", "Sand", "Gradient", "Crosshatch", "Wavy Lines", "Mezzotint", "Stipple", "Woodcut", "Scribble", "Hatching"),
    TTONE(BrushPart.MEDIUM, "ttone", "Darkness", .05, 1, .45),
    /**
     * Fling (the mischief modules spec, Stage 2; {@link FlickFling}): how many drops a hard stop throws ahead, Spatter's
     * kin; their size and reach follow the speed. Last of every medium with Blend, after Screen's dials, written only
     * when on ({@link ModularCode#ADDED}); rolled rarely from the seed ({@link ModularStyle#rollFling}).
     */
    FLING(BrushPart.MEDIUM, "fling", "Fling", 0, 1, 0),
    /**
     * Crayon (mischief-lab-2.html's fourth pad): how much wax it lays, chalky to waxy. More wax builds up more where
     * crayon already lies, and sheds washes more (its share of the page's masking fluid, {@link ModularStroke}).
     */
    WAX(BrushPart.MEDIUM, "wax", "Wax", 0, 1, .6),
    /** Crayon's Scratch: On, a thin blade that scrapes back to white (scratch-through on a black page). */
    SCRATCH(BrushPart.MEDIUM, "scratch", "Scratch", 0, "Off", "On"),
    // Carry
    PICK(BrushPart.CARRY, "pick", "Pick Up Paint", 0, 1, .4),
    LAY(BrushPart.CARRY, "lay", "Lay Down Paint", 0, 1, .5),
    LOAD(BrushPart.CARRY, "load", "Own Paint", 0, 1, 1),
    /**
     * Smear's Soften (the Stump spec, docs/archive/superpowers/specs/2026-10-18-stump-design.md): how much the smear works as
     * MonoPaint's blending stump, softening what's under it in place and pulling it on from a quarter width behind,
     * rather than carrying it along. 0, today's smear; 1, all stump. Last in Smear's list, written only when not 0
     * ({@link ModularCode#ADDED}); never rolled.
     */
    SOFTEN(BrushPart.CARRY, "soften", "Soften", 0, 1, 0),
    /**
     * Drag's and Smear's Keeps Paint (the wet smudge spec, docs/archive/superpowers/specs/2026-10-19-wet-smudge-design.md): how
     * much of what the brush picked up it keeps for the next stroke, a dirty brush ({@link HeldPaint}). Off, today's
     * smudge, which starts every stroke clean; 1, the next stroke starts with all of it. Last in their lists, written
     * only when on ({@link ModularCode#ADDED}); never rolled.
     */
    KEEPS(BrushPart.CARRY, "keep", "Keeps Paint", 0, 1, 0),
    /**
     * Drag's and Smear's Paint Load (the oil paint spec, docs/archive/superpowers/specs/2026-10-19-oil-paint-design.md; MonoPaint's
     * "Oil paint mode when opaque"): how much paint a full brush holds ({@link LoadedPaint}). Off, today's brush, which
     * never runs out; on, every stroke starts with its {@link #PMIN} share of a full load (more after a rub on a grey),
     * lays its own paint solidly, then runs out along the stroke, sooner the wider it is, trading its own paint for what
     * it picks up until it only smudges. After Keeps Paint, written only when on ({@link ModularCode#ADDED}); a part's
     * roll leaves it off (Oils rolls it from the seed, {@link ModularStyle#rollPaintLoad}).
     */
    PLOAD(BrushPart.CARRY, "pload", "Paint Load", 0, 1, 0),
    /** Paint Load's Starting Load: the share of a full load every stroke starts with (the owner: "start with a small load"). Last, written only when off its default. */
    PMIN(BrushPart.CARRY, "pmin", "Starting Load", 0, 1, .25),
    /**
     * Stay in Lines' Flood (mischief-lab-2.html's Spread; "spread" is an engine word on screen, docs/TEXT-STYLE.md): how
     * far the wash runs on past the brush inside the ink lines, {@link StayIn#reach}: 220 × flood² of the prototype's px
     * (about 35 mm at 1); 0, the brush alone; 1, the whole shape ("Fills").
     */
    FLOOD(BrushPart.CARRY, "flood", "Flood", 0, 1, .35),
    // Paper
    TOOTH(BrushPart.PAPER, "tooth", "Texture", 0, 1, .5),
    // Life
    WINDOW(BrushPart.LIFE, "window", "Wet Edge", 1, 10, 6),
    DRYING(BrushPart.LIFE, "drying", "Drying Time", 0, 1, .35),
    SETTLE(BrushPart.LIFE, "settle", "Flow While Wet", 0, 1, .35),
    TAKES(BrushPart.LIFE, "takes", "Takes Ink", 0, "Off", "Drift", "Vein"),
    PLUME(BrushPart.LIFE, "plume", "Plume", 0, 1, .55),
    /**
     * Gravity (the Gravity Lab's), while the paint is wet and the page tilts ({@link GravityFlow}): water above what the
     * paper holds runs downhill with its pigment; drips break from the low edge; pigment pools at the low edge; the
     * granulating share sinks; rocking blends. Last in Slow's and Quick's lists, so old codes read them 0. G_POOL's key
     * is the prototype's {@code life.pool}; Medium's Pooling is {@link #POOL}.
     */
    RUNS(BrushPart.LIFE, "runs", "Runs Downhill", 0, 1, 0),
    DRIPS(BrushPart.LIFE, "drips", "Drips", 0, 1, 0),
    G_POOL(BrushPart.LIFE, "pool", "Pools Downhill", 0, 1, 0),
    SINK(BrushPart.LIFE, "sink", "Grainy", 0, 1, 0),
    BLEND(BrushPart.LIFE, "blend", "Rock to Blend", 0, 1, 0),
    /** Float: how big a tap's drop is (small rings to wide rings), and how fast floating paint drifts on a tilted page. */
    F_SPREAD(BrushPart.LIFE, "spread", "Ring Size", 0, 1, .5),
    DRIFT(BrushPart.LIFE, "drift", "Drift Downhill", 0, 1, .5),
    /**
     * Halo (the new modules spec, {@link HaloGrowth}): how far its veins reach past the stroke (12 to 80 px, wider with
     * a broad tip, at most three times its width), and how many scouts grow them and how fast veins darken.
     */
    H_REACH(BrushPart.LIFE, "reach", "Mold Length", 0, 1, .5),
    GROWTH(BrushPart.LIFE, "growth", "Mold Growth", 0, 1, .5),
    /**
     * Fading's and Lemon Juice's time (the mischief modules spec, Stage 3; {@link FadingInk}), seconds: how long a
     * fading mark takes to go (10 s to 2 min) and lemon juice to darken in (10 s to 10 min; minutes since the owner, 2026-10-30:
     * "make it appear minutes late instead of right away. maybe 5 minutes?").
     */
    FADE_IN(BrushPart.LIFE, "fadeIn", "Fades In", 10, 120, 20),
    DRY_IN(BrushPart.LIFE, "dryIn", "Dries In", 10, 600, 300),
    /**
     * Wet's one dial (the wet paint spec; the owner's "one Stays Wet dial", 2026-10-22): how wet MonoPaint's wet canvas
     * keeps the paint ({@link ResolvedBrush.Wet}): how much of what lies under new paint mixes into it and how freely it
     * evens out with its wet neighbours, and how long a touched patch keeps moving (about 1 to 6 s). Written only when
     * off its default ({@link ModularCode#ADDED}).
     */
    STAYS_WET(BrushPart.LIFE, "stays", "Stays Wet", 0, 1, .5),
    // Hand
    PS(BrushPart.HAND, "ps", "Pressure Size", 0, 1, 0),
    PD(BrushPart.HAND, "pd", "Pressure Dark", 0, 1, 0),
    PW(BrushPart.HAND, "pw", "Pressure Nib", 0, 1, 0),
    TS(BrushPart.HAND, "ts", "Tilt Size", 0, 1, 0),
    TD(BrushPart.HAND, "td", "Tilt Dark", 0, 1, 0),
    TW(BrushPart.HAND, "tw", "Tilt Nib", 0, 1, 0),
    SS(BrushPart.HAND, "ss", "Speed Size", 0, 1, 0),
    SD(BrushPart.HAND, "sd", "Speed Dark", 0, 1, 0),
    SW(BrushPart.HAND, "sw", "Speed Nib", 0, 1, 0),
    DIR(BrushPart.HAND, "dir", "Fast Strokes", 0, "Thinner", "Thicker"),
    GAIN(BrushPart.HAND, "gain", "Response", 0, 1.5, 1),
    /**
     * Not the prototype's: how far Tilt → size and Tilt → angle reach. Normal, the prototype's; wide, the old Fude
     * pen's, a fine point held upright to nearly five times the size laid low, the nib flattening (the lab's "full+"
     * on Tilt → size). Last of the hand's dials, so codes without it read as normal ({@link ModularCode}).
     */
    TILT_RANGE(BrushPart.HAND, "trange", "Tilt Range", 0, "Normal", "Wide"),
    /**
     * Gravity in the hand (the Gravity Lab's fourth row): drawn downhill on a tilted page wider, uphill lighter, a
     * shaped nib opening downhill. After Tilt range, so codes without them read 0.
     */
    GS(BrushPart.HAND, "gs", "Page Tilt Size", 0, 1, 0),
    GD(BrushPart.HAND, "gd", "Page Tilt Dark", 0, 1, 0),
    GW(BrushPart.HAND, "gw", "Page Tilt Nib", 0, 1, 0),
    /**
     * A Spiro's Control (mischief-lab-2.html's Control block): what of its shape pressure, speed and tilt each drive,
     * scaling it .4 to 1.3 times (1 at about two thirds); Nothing leaves it as set ({@link SpiroStamper}). Last of the
     * hand's dials, written only when off their defaults ({@link ModularCode#ADDED}); the lab shows them for a Spiro.
     */
    P_DRIVE(BrushPart.HAND, "pdrive", "Pressure Sets", 0, "Size", "Loops", "Wheel", "Hole", "Petals", "Nothing"),
    S_DRIVE(BrushPart.HAND, "sdrive", "Speed Sets", 5, "Size", "Loops", "Wheel", "Hole", "Petals", "Nothing"),
    T_DRIVE(BrushPart.HAND, "tdrive", "Tilt Sets", 3, "Size", "Loops", "Wheel", "Hole", "Petals", "Nothing"),
    /**
     * Grey Shift (mischief-lab-2.html's Shifting swatches, the owner's "unpredictable swatch selection on pen
     * touch"): each touch picks the brush's grey from a set ({@link SwatchPicker}), at random, From a Set (a shuffled
     * deck, each once before any again) or in a Pattern; Off draws at the slot's grey. Its Order (Pattern's: in order, or
     * there and back) and Greys: the toolbar's grey swatches, or Any of the panel's 16. Each palette keeps its own set,
     * as the greys left out, a bit each ({@link #SHIFT_BAR}; Any's in two, its first eight and its last, paper white left
     * out at first). After a Spiro's drives, last of the hand's, written only when on ({@link ModularCode#ADDED}).
     */
    SHIFT(BrushPart.HAND, "shift", "Grey Shift", 0, "Off", "Random", "From a Set", "Pattern"),
    SHIFT_ORDER(BrushPart.HAND, "sorder", "Order", 0, "In Order", "Ping-Pong"),
    SHIFT_GREYS(BrushPart.HAND, "sgreys", "Greys", 0, "Toolbar", "Any"),
    SHIFT_BAR(BrushPart.HAND, "sbar", "Toolbar Greys", 0, 15, 0, true),
    SHIFT_ANY(BrushPart.HAND, "sany", "Any Greys", 0, 255, 1, true),
    SHIFT_ANY2(BrushPart.HAND, "sany2", "Any Greys", 0, 255, 0, true),
    /**
     * Gradient (the owner's "fading color (or gradient shift)"): the grey changes along each stroke, by the distance
     * drawn ({@link GreyGradient}): Fade lightens it to paper, To Grey takes it to its End Grey (one of the panel's 16,
     * paper white 0 to black 15), Cycle steps through Grey Shift's set (its Greys, Toolbar or Any), blending from one to
     * the next, a rainbow pencil's way; Off, as ever. Length: how far the shift takes, whole cm (or each step's, Cycle's).
     * Each stroke starts again at its own grey. After Grey Shift's, last of the hand's, written only when on
     * ({@link ModularCode#ADDED}).
     */
    GRADIENT(BrushPart.HAND, "grad", "Gradient", 0, "Off", "Fade", "To Grey", "Cycle"),
    GRAD_TO(BrushPart.HAND, "gto", "End Grey", 0, 15, 5, true),
    GRAD_LEN(BrushPart.HAND, "glen", "Length", 1, 36, 10, true),
    /**
     * Tilt Shading (the owner, 2026-10-22, of the pencils: "when the pencil is tilted it shades like an angled pencil"):
     * how far a pen laid low lays the side of its nib, a wider, lighter, softer-edged line (graphite's catches only the
     * paper's tooth), held upright a crisp line as ever ({@link ResolvedBrush.Pen#shade}). After Gradient's, last of the
     * hand's, written only when on ({@link ModularCode#ADDED}).
     */
    SIDE_SHADE(BrushPart.HAND, "tshade", "Tilt Shading", 0, 1, 0);

    /** The hand's nine cells (parts.js HAND_CELLS): pressure, tilt and speed, each to size, dark and width/angle. */
    static final PartDial[] HAND_CELLS = {PS, PD, PW, TS, TD, TW, SS, SD, SW};
    /** The hand's page-tilt cells: drawn downhill to size, dark and width. */
    static final PartDial[] PAGE_CELLS = {GS, GD, GW};
    /** Every cell of the lab's hand grid, row by row: the nine, then page tilt's three (the prototype's HAND_CELLS). */
    static final PartDial[] GRID_CELLS = {PS, PD, PW, TS, TD, TW, SS, SD, SW, GS, GD, GW};
    /** A Spiro's Control: what pressure, speed and tilt drive, in that order. */
    static final PartDial[] DRIVES = {P_DRIVE, S_DRIVE, T_DRIVE};
    /** Grey Shift and its own dials, last of the hand's, in their order. */
    static final PartDial[] SHIFTS = {SHIFT, SHIFT_ORDER, SHIFT_GREYS, SHIFT_BAR, SHIFT_ANY, SHIFT_ANY2};
    /** Gradient and its own dials, after Grey Shift's, in their order. */
    static final PartDial[] GRADIENTS = {GRADIENT, GRAD_TO, GRAD_LEN};
    /** The gravity dials of Slow (and Quick's Runs, Pool and Sink), in their order. */
    static final PartDial[] GRAVITY = {RUNS, DRIPS, G_POOL, SINK, BLEND};
    /** Screen and its own dials, last of every medium that has Blend, in their order. */
    static final PartDial[] SCREEN_DIALS = {SCREEN, CELL, S_ANGLE, MIS, S_GAIN, UNEVEN};
    /** parts.js GRADES. */
    private static final String[] GRADES = {"6H", "5H", "4H", "3H", "2H", "H", "F", "HB", "B", "2B", "3B", "4B", "5B", "6B", "7B", "8B"};

    final BrushPart part;
    /** The prototype's key. */
    final String key, label;
    /** A slider's range and default; a choice dial's range is its option indexes. A choice may narrow the range or move the default. */
    final double min, max, def;
    /** A choice dial's options; null for a slider. */
    final String[] choices;
    /** Rounded to a whole number when normalized. */
    final boolean integer;

    PartDial(BrushPart part, String key, String label, double min, double max, double def) { this(part, key, label, min, max, def, false); }
    PartDial(BrushPart part, String key, String label, double min, double max, double def, boolean integer) {
        this.part = part; this.key = key; this.label = label; this.min = min; this.max = max; this.def = def; this.choices = null; this.integer = integer;
    }
    PartDial(BrushPart part, String key, String label, int def, String... choices) {
        this.part = part; this.key = key; this.label = label; this.min = 0; this.max = choices.length - 1; this.def = def; this.choices = choices; this.integer = false;
    }

    boolean choice() { return choices != null; }
    /** Screen's own dials (Cell, Angle, Misregister, Ink gain, Uneven; shown as Pattern Size, Angle, Double Print, Dot Swell, Patchy Ink): they do something only with a screen. */
    boolean underScreen() { return this == CELL || this == S_ANGLE || this == MIS || this == S_GAIN || this == UNEVEN; }
    /** Flick or Fling ({@link FlickFling}): off, a variation leaves it off, as it does gravity's. */
    boolean mischief() { return this == FLICK || this == FLING; }
    /** One of gravity's dials: Slow's and Quick's five, Charcoal's Dust, the hand's page tilt. Rolls leave them off. */
    boolean gravity() {
        switch (this) { case RUNS: case DRIPS: case G_POOL: case SINK: case BLEND: case DUST: case GS: case GD: case GW: return true; default: return false; }
    }

    /** parts.js fmtDial (127–133) with each dial's fmt. */
    String shown(double v) { return shown(v, choices); }
    /** {@link #shown(double)} with a choice dial's options as {@code options} names them (Opaque's Blend: cover first). */
    private String shown(double v, String[] options) {
        if (options != null) return options[Math.max(0, Math.min(options.length - 1, (int) v))];
        switch (this) {
            case WEIGHT: case GAIN: return "×" + fixed(v, 2);
            case ANGLE: case HANGLE: case S_ANGLE: return Math.round(v) + "°";
            case CELL: return Math.round(v) + " px";
            case MIS: return v < .5 ? "off" : Math.round(v) + " px";
            case COUNT: case HAIRS: case LAYERS: return String.valueOf(Math.round(v));
            case INK_LOAD: return fixed(v, 2);
            case STAMP_SPACING: return Math.round(v * 100) + "%";
            case INK_FADE: return v < .005 ? "None" : v > .295 ? "Strong" : Math.round(v * 100) + "%";
            case WHEEL: return String.valueOf(SpiroStamper.WHEELS[(int) Math.max(0, Math.min(SpiroStamper.WHEELS.length - 1, Math.round(v)))]);
            case HOLE: return Math.round(v * 100) + "%";
            case PETALS: case FADES: return String.valueOf(Math.round(v));
            case LOOPS: return fixed(v, 1);
            case PHASE: return Math.round(v * 90) + "°";
            // Whole millimetres from 10: "10 mm" keeps a value to six characters, the lab's 12 sp value column (LabLayout.VALUE_WIDEST).
            case WINDOW: return fixed(v, v >= 9.95 ? 0 : 1) + " mm";
            case DRYING: return fixed(wetMs(v) / 1000.0, 1) + " s";
            case FADE_IN: return Math.round(v) + " s";
            case DRY_IN: { long s = Math.round(v); return s < 60 ? s + " s" : s / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", s % 60); }
            case FLOOD: return v < .005 ? "Brush" : v > .995 ? "Fills" : Math.max(1, Math.round(StayIn.mm(v))) + " mm";
            case GRADE: return grade(v);
            // The End Grey as the panel's grey it is; whole cm (the Manta's and the Nomad's alike).
            case GRAD_TO: return GreyGradient.greyShown((int) Math.round(v));
            case GRAD_LEN: return Math.round(v) + " cm";
            default: return min == 0 && v < .005 ? "off" : fixed(v, 2);
        }
    }

    /** {@link #shown(double)} on {@code part}: a Slow drying time as long as that brush's ({@link ModularBrush#runs}). */
    String shown(double v, ModularBrush.Part part) {
        return this == DRYING && ModularBrush.runs(part) ? fixed(wetMs(v, true) / 1000.0, 1) + " s" : shown(v, choices == null ? null : part.choice.optionsOf(this));
    }

    /** parts.js 15–16 gradeOf: 0 is 6H, .4 is HB, 1 is 8B. */
    static String grade(double g) {
        long i = Math.round(g < .4 ? g / .4 * 7 : 7 + (g - .4) / .6 * 8);
        return GRADES[(int) Math.max(0, Math.min(GRADES.length - 1, i))];
    }

    /** stroke.js 82 wetMsOf: how long a Drying mark stays wet, 0.75 to 20 s. */
    static long wetMs(double d) { return Math.round(750 + (20000 - 750) * d * d); }
    /**
     * As {@link #wetMs(double)}, and {@code runs} (a Slow brush that moves with the page's tilt): the Gravity Lab's
     * 0.75 to 60 s, the same to within 0.1 s up to the 3 s default, so a wash stays wet long enough to tilt, rock and
     * watch it run. Only gravity brushes take it, so no older brush dries differently.
     */
    static long wetMs(double d, boolean runs) { return runs ? Math.round(750 + (20000 - 750) * d * d + 40000 * Math.pow(d, 6)) : wetMs(d); }

    /** JavaScript's toFixed: the exact value rounded half up, so labels read as the prototype's. */
    static String fixed(double v, int places) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return String.valueOf(v);
        return new BigDecimal(v).setScale(places, RoundingMode.HALF_UP).toPlainString();
    }
}
