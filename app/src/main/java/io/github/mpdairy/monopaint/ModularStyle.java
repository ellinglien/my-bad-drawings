package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.PartChoice.*;
import static io.github.mpdairy.monopaint.PartDial.*;
import static io.github.mpdairy.monopaint.StrokeMath.lerp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The 15 styles of six-part brushes (parts.js 276–415, then Print, the app's own), in share-code order: never reorder
 * (a new style goes on the end, after Anything goes; {@link #shown} puts Anything goes last on screen). A style is a size,
 * weights for each part's choices and specs for its dials; it rolls a brush, a part or a dial. A dial a style
 * leaves out rolls at its choice's default. Anything goes rolls any offered choice of every part by its own rules.
 * Rolls never pick what the engine can't draw yet ({@link ModularFeatures}); a choice that joined later (Bristles) is
 * drawn after every part, so the rest of a roll is what it was. Pure, checked on the desktop.
 */
final class ModularStyle {
    static final String ANYTHING_GOES = "Anything goes";

    /** A style's range for a dial: always in [lo, hi]; on that often, else 0; weighted bands; or one of some options. */
    static final class Spec {
        enum Kind { ALWAYS, MAYBE, BANDS, OPTIONS }

        final Kind kind;
        /** MAYBE: the share of rolls that are on; 1 for the others. */
        final double p;
        /** ALWAYS and MAYBE: the range rolled in. BANDS and OPTIONS: the span of every band or option. */
        final double lo, hi;
        /** BANDS: {share, lo, hi} each; null for the others. */
        final double[][] bands;
        /** OPTIONS: the values picked from evenly (repeat one to weight it); null for the others. */
        final int[] options;

        private Spec(Kind kind, double p, double lo, double hi, double[][] bands, int[] options) {
            this.kind = kind; this.p = p; this.lo = lo; this.hi = hi; this.bands = bands; this.options = options;
        }
        static Spec always(double lo, double hi) { return new Spec(Kind.ALWAYS, 1, lo, hi, null, null); }
        static Spec maybe(double p, double lo, double hi) { return new Spec(Kind.MAYBE, p, lo, hi, null, null); }
        static Spec bands(double[]... bands) {
            double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            for (double[] b : bands) { lo = Math.min(lo, b[1]); hi = Math.max(hi, b[2]); }
            return new Spec(Kind.BANDS, 1, lo, hi, bands, null);
        }
        static Spec options(int... options) {
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int o : options) { lo = Math.min(lo, o); hi = Math.max(hi, o); }
            return new Spec(Kind.OPTIONS, 1, lo, hi, null, options);
        }

        /** parts.js rollSpec (327–332). Not clamped to the dial's range. */
        double roll(Random random) {
            switch (kind) {
                case ALWAYS: return lerp(lo, hi, random.nextDouble());
                case BANDS: {
                    double r = random.nextDouble(), a = 0;
                    for (double[] b : bands) { a += b[0]; if (r <= a) return lerp(b[1], b[2], random.nextDouble()); }
                    return lerp(bands[0][1], bands[0][2], random.nextDouble());
                }
                case OPTIONS: return options[(int) (random.nextDouble() * options.length)];
                default: return random.nextDouble() < p ? lerp(lo, hi, random.nextDouble()) : 0;
            }
        }
    }

    /** Its name: a key (share codes, presets and saved brushes find a style by it), never respelled. */
    final String name;
    /** Its name as the app shows it, in Canadian spelling (docs/TEXT-STYLE.md): "Watercolour" for Watercolor. */
    final String label;
    /** Its place in the list, as share codes carry it. */
    final int index;
    /** Anything goes: any choice of every part. */
    final boolean wild;
    /** The size it draws at best; Ink wash's for ink, and for any medium {@link #mediumSizes} doesn't name. */
    private final double size;
    private final Map<PartChoice, Double> mediumSizes = new EnumMap<>(PartChoice.class);
    /** Per part, its choices' weights in the prototype's order (pickWeighted walks them in it). The hand has none. */
    private final Map<BrushPart, LinkedHashMap<PartChoice, Double>> weights = new EnumMap<>(BrushPart.class);
    private final Map<PartDial, Spec> specs = new EnumMap<>(PartDial.class);

    private ModularStyle(String name, int index, double size) {
        this.name = name; this.index = index; this.size = size; this.wild = name.equals(ANYTHING_GOES);
        this.label = name.replace("Watercolor", "Watercolour");
    }

    // ---- The table (parts.js 281–321, 343–344), built once ----

    /** Anything goes' rough dials, capped together at 1.1 (parts.js ROUGH). */
    private static final Set<PartDial> ROUGH = EnumSet.of(DRY, WOBBLE, TOOTH);
    /** Dials Anything goes rolls over their whole range, never off (parts.js 371's list). */
    private static final Set<PartDial> WHOLE_RANGE = EnumSet.of(WET, SOFT, ANGLE, ASPECT, GAP, REACH, SPLAY, COHESION, SPREAD, HOLD,
            GRADE, PICK, LAY, LOAD, PartDial.DRYING, SETTLE, PLUME, DRYNESS, INK_LOAD);
    /** Anything goes' hand, cell by cell in HAND_CELLS order (parts.js 378–380). */
    private static final Spec[] WILD_HAND = {Spec.maybe(.75, .3, 1), Spec.maybe(.35, .2, .8), Spec.maybe(.3, .3, 1),
            Spec.maybe(.3, .3, 1), Spec.maybe(.2, .2, .7), Spec.maybe(.25, .3, 1),
            Spec.maybe(.35, .2, .8), Spec.maybe(.2, .2, .7), Spec.maybe(.15, .2, .8)};

    private static final List<ModularStyle> ALL, SHOWN;
    static {
        List<ModularStyle> all = new ArrayList<>();
        add(all, "Fineliner", 4).parts(ROUND, 1, INK, 1, OFF, 1, PLAIN, 3, COLD_PRESS, 1, FAST_WET, 1)
                .maybe(TAPER_IN, .4, 0, .15).maybe(TAPER_OUT, .4, 0, .2).maybe(WOBBLE, .2, 0, .1).always(WEIGHT, .9, 1.1)
                .always(WET, 0, 0).always(LINE, 1, 1).maybe(POOL, .4, 0, .3).maybe(TOOTH, .3, 0, .15).always(PS, 0, .25).maybe(SS, .4, 0, .2);
        add(all, "Brush pen", 8).parts(ROUND, 2, CHISEL, 1, INK, 1, OFF, 1, PLAIN, 2, COLD_PRESS, 1, FELT, 1, FAST_WET, 1)
                .always(TAPER_IN, .3, .8).always(TAPER_OUT, .4, 1).maybe(DRY, .3, 0, .3).maybe(WOBBLE, .3, 0, .15).always(ASPECT, .3, .55)
                .maybe(WET, .2, .1, .35).always(LINE, .75, 1).maybe(POOL, .4, 0, .4).maybe(SPLATTER, .15, 0, .3)
                .maybe(TOOTH, .3, 0, .2).always(PS, .7, 1).maybe(PD, .5, .2, .5).always(SS, 0, .7).maybe(TS, .4, .3, .8);
        add(all, "Dip pen", 6).parts(FLAT_NIB, 2, CHISEL, 1, SPLIT_NIB, 2, INK, 1, OFF, 1, PLAIN, 1, LAID, 1, COLD_PRESS, 1, FAST_WET, 1)
                .always(ANGLE, 30, 60).always(GAP, .35, .8).maybe(TAPER_IN, .5, 0, .3).maybe(TAPER_OUT, .5, 0, .4).maybe(DRY, .25, 0, .25)
                .maybe(WET, .15, .1, .3).maybe(POOL, .6, .1, .6).maybe(SPLATTER, .3, 0, .35).maybe(TOOTH, .3, 0, .2)
                .always(PS, .4, .85).always(PW, .5, 1).maybe(SS, .5, 0, .3).maybe(TW, .5, .4, .9);
        // A Marker of wash is a highlighter, broad (Highlighter, ToolPresets): no roll picks one.
        add(all, "Marker", 12).sizeFor(WASH, 40).parts(CHISEL, 2, ROUND, 1, INK, 1, OFF, 1, PLAIN, 1, FELT, 1, FAST_WET, 1)
                .always(ASPECT, .35, .55).maybe(WET, .3, .05, .25).always(POOL, .3, .8).maybe(BLEED, .3, 0, .3).maybe(TOOTH, .3, 0, .2)
                .always(PS, 0, .2).always(SS, 0, .3).maybe(TW, .3, .3, .7);
        add(all, "Pencil", 4).parts(ROUND, 1, GRAPHITE, 1, OFF, 6, SMUDGE, 1, COLD_PRESS, 1, FELT, 1, LAID, 1, FAST_WET, 1)
                .always(GRADE, .2, .8).maybe(SPREAD, .3, 0, .15).maybe(TAPER_IN, .4, 0, .2).maybe(TAPER_OUT, .4, 0, .25).maybe(WOBBLE, .3, 0, .12)
                .always(TOOTH, .5, .9).always(PICK, .3, .6).always(PS, .2, .5).always(PD, .4, .8).always(TS, .3, .7).always(TD, .3, .6);
        add(all, "Charcoal", 30).parts(CHISEL, 2, ROUND, 1, CHARCOAL, 1, OFF, 3, SMUDGE, 1, COLD_PRESS, 2, LAID, 1, PLAIN, 1, FAST_WET, 1)
                .bands(SPREAD, new double[]{.7, .15, .45}, new double[]{.15, 0, .1}, new double[]{.15, .6, .9}).always(HOLD, .1, .8)
                .maybe(TAPER_IN, .4, 0, .3).maybe(TAPER_OUT, .4, 0, .3).maybe(DRY, .5, .1, .4).maybe(WOBBLE, .6, 0, .4).always(ASPECT, .3, .55)
                .always(TOOTH, .6, 1).always(PS, .5, .9).always(PD, .3, .7).always(TS, .6, 1).always(TD, .4, .7).maybe(DUST, .25, .3, .8);
        add(all, "Watercolor", 120).parts(ROUND, 1, WASH, 1, OFF, 1, PLAIN, 1, COLD_PRESS, 2, FAST_WET, 4, PartChoice.DRYING, 1)
                .always(SOFT, .2, .6).always(WET, .7, 1).maybe(POOL, .6, .2, .7).maybe(BLEED, .3, 0, .3).maybe(BLOOM, .6, .2, .6)
                .maybe(BACKRUN, .3, 0, .5).maybe(TOOTH, .5, 0, .5).always(PartDial.DRYING, .3, .7).always(PS, .3, .8)
                // Gravity, now and then and gently: a wash that runs and pools a little when the page tilts. Never drips.
                .maybe(RUNS, .2, .1, .35).maybe(G_POOL, .2, .15, .5);
        add(all, "Ink wash", 8).sizeFor(WASH, 90).parts(ROUND, 1, INK, 3, WASH, 2, OFF, 1, PLAIN, 1, FELT, 1, FAST_WET, 3, PartChoice.DRYING, 1)
                .always(SOFT, .5, 1).maybe(TAPER_IN, .5, .1, .5).maybe(TAPER_OUT, .5, .2, .7).always(WET, .45, .9).always(LINE, .5, 1)
                .always(BLEED, .3, .8).maybe(BLOOM, .3, 0, .3).maybe(POOL, .3, 0, .3).always(PartDial.DRYING, .5, .9).always(PS, .4, .9);
        add(all, "Gouache", 60).parts(ROUND, 2, FLAT_NIB, 1, HAKE, 1, PartChoice.COVER, 1, OFF, 1, LIFT, 1, PLAIN, 2, FELT, 1, COLD_PRESS, 1, FAST_WET, 1)
                .always(PartDial.COVER, 1, 1).always(IMPASTO, 0, 0).always(SOFT, 0, .2).maybe(DRY, .3, 0, .3).always(WEIGHT, .8, 1.1)
                .maybe(TOOTH, .3, 0, .2).always(PS, 0, .3);
        add(all, "Oils", 60).parts(ROUND, 2, FLAT_NIB, 1, HAKE, 1, PartChoice.COVER, 1, DRAG, 7, OFF, 3, CANVAS, 1, FAST_WET, 1)
                .always(PartDial.COVER, 1, 1).always(IMPASTO, .4, .9).always(DRY, .5, .95).always(SOFT, 0, .15).maybe(TOOTH, .4, 0, .3)
                .always(PICK, .4, .8).always(PS, .3, .7).maybe(TS, .4, .4, .9);
        add(all, "Pastels", 24).parts(CHISEL, 1, ROUND, 1, CHARCOAL, 1, OFF, 2, SMUDGE, 1, COLD_PRESS, 1, FELT, 1, CRUMPLED, 1, LAID, 1, FAST_WET, 1)
                .always(SPREAD, .1, .35).always(HOLD, .1, .5).always(DRY, .1, .3).always(ASPECT, .35, .55).always(TOOTH, .6, 1)
                .always(PS, .2, .6).always(TS, .5, 1).always(TD, .3, .6).maybe(DUST, .25, .3, .8);
        add(all, "Dry brush", 60).parts(ROUND, 2, BRISTLES, 2, FLAT_NIB, 1, WASH, 7, PartChoice.COVER, 3, OFF, 1, COLD_PRESS, 1, LAID, 1, CRUMPLED, 1, FAST_WET, 1)
                .always(WET, 0, .4).always(PartDial.COVER, .5, .9).always(DRY, .5, 1).always(SOFT, 0, .3).always(DRYNESS, .4, .8)
                .always(SPLAY, .4, .9).always(HAIRS, 16, 32).always(TOOTH, .3, .8).always(PS, .3, .7);
        add(all, "Hake", 180).parts(HAKE, 1, WASH, 7, PartChoice.COVER, 3, OFF, 1, PLAIN, 1, COLD_PRESS, 1, FAST_WET, 1)
                .options(HEAD_TURN, 0, 0, 0, 1).always(ANGLE, 70, 80).always(DRY, .2, .6).always(SOFT, .1, .4).always(WET, .3, 1)
                .always(PartDial.COVER, .5, .9).always(PS, .1, .4);
        add(all, ANYTHING_GOES, 16).parts(ROUND, 3, CHISEL, 2, FLAT_NIB, 2, HAKE, 1, SPLIT_NIB, 1.5, MULTI_LINE, 1.5, BRISTLES, 1,
                INK, 3, WASH, 3, PartChoice.COVER, 2, CHARCOAL, 1.5, GRAPHITE, 1, OFF, 6, LIFT, 1, DRAG, 1, SMUDGE, 1,
                PLAIN, 3, COLD_PRESS, 2, LAID, 1, FELT, 1, CRUMPLED, 1, CANVAS, 1, FAST_WET, 7, PartChoice.DRYING, 2, NONE, 1);
        // Print (not the prototype's; after Anything goes, as codes carry the index): every roll prints through a Print
        // Shading ({@link #rollScreen}), or now and then is Screentone ({@link #rollTone}). Pens a little broader than a
        // Fineliner's, so the pattern shows; charcoal, pencil and wash broader still. Pressure darkens: it grows the dots.
        add(all, "Print", 12).sizeFor(CHARCOAL, 30).sizeFor(GRAPHITE, 14).sizeFor(WASH, 40)
                .parts(ROUND, 2, CHISEL, 1, INK, 3, WASH, 1, CHARCOAL, 1, GRAPHITE, 1, OFF, 1, PLAIN, 2, COLD_PRESS, 1, FAST_WET, 1)
                .always(SOFT, .3, .7).always(ASPECT, .35, .55).maybe(TAPER_IN, .3, 0, .2).maybe(TAPER_OUT, .3, 0, .3)
                .always(GRADE, .4, .8).always(SPREAD, .1, .4).always(HOLD, .3, .7).always(WET, 0, 0).always(LINE, 1, 1)
                .maybe(TOOTH, .3, 0, .3).always(PS, .1, .4).always(PD, .4, .8);
        ALL = Collections.unmodifiableList(all);
        List<ModularStyle> shown = new ArrayList<>();
        for (ModularStyle s : all) if (!s.wild) shown.add(s);
        for (ModularStyle s : all) if (s.wild) shown.add(s);
        SHOWN = Collections.unmodifiableList(shown);
    }
    private static ModularStyle add(List<ModularStyle> all, String name, double size) {
        ModularStyle s = new ModularStyle(name, all.size(), size);
        all.add(s);
        return s;
    }
    private ModularStyle sizeFor(PartChoice medium, double size) { mediumSizes.put(medium, size); return this; }
    /** Pairs of a choice and its weight. */
    private ModularStyle parts(Object... pairs) {
        for (int i = 0; i < pairs.length; i += 2) {
            PartChoice c = (PartChoice) pairs[i];
            LinkedHashMap<PartChoice, Double> w = weights.get(c.part);
            if (w == null) weights.put(c.part, w = new LinkedHashMap<>());
            w.put(c, ((Number) pairs[i + 1]).doubleValue());
        }
        return this;
    }
    private ModularStyle always(PartDial d, double lo, double hi) { specs.put(d, Spec.always(lo, hi)); return this; }
    private ModularStyle maybe(PartDial d, double p, double lo, double hi) { specs.put(d, Spec.maybe(p, lo, hi)); return this; }
    private ModularStyle bands(PartDial d, double[]... bands) { specs.put(d, Spec.bands(bands)); return this; }
    private ModularStyle options(PartDial d, int... options) { specs.put(d, Spec.options(options)); return this; }

    static List<ModularStyle> all() { return ALL; }
    /** Every style in the order the app lists them (the Tools page's chips, Brush Lab's styles): Anything goes last. */
    static List<ModularStyle> shown() { return SHOWN; }
    /** Null outside 0–14. */
    static ModularStyle at(int index) { return index >= 0 && index < ALL.size() ? ALL.get(index) : null; }
    /** "Wild" (old brushes' name) and "Wildcard" are Anything goes; null for unknown names. */
    static ModularStyle named(String name) {
        if (BrushStyle.WILD.equals(name) || "Wildcard".equals(name)) name = ANYTHING_GOES;
        for (ModularStyle s : ALL) if (s.name.equals(name)) return s;
        return null;
    }

    // ---- What the table says ----

    /** parts.js styleSize: the size it draws at best with {@code medium} (any medium for most styles). */
    double size(PartChoice medium) { Double s = mediumSizes.get(medium); return s != null ? s : size; }
    /** Its weight for {@code c}; 0 if it doesn't list it (a hand: always 0). */
    double weight(PartChoice c) {
        Map<PartChoice, Double> w = weights.get(c.part);
        Double v = w == null ? null : w.get(c);
        return v != null ? v : 0;
    }
    /** Its spec for {@code d}; null if it leaves the dial out (Anything goes leaves every dial out). */
    Spec spec(PartDial d) { return specs.get(d); }
    /**
     * parts.js styleBand: the range it keeps {@code d} in on choice {@code c}, for the lab's hatched band and for
     * variations; null without a spec, for choice dials and for options.
     */
    double[] band(PartChoice c, PartDial d) {
        Spec spec = spec(d);
        if (spec == null || d.choice()) return null;
        double[] r = c.range(d);
        switch (spec.kind) {
            case ALWAYS: return new double[]{StrokeMath.clamp(spec.lo, r[0], r[1]), StrokeMath.clamp(spec.hi, r[0], r[1])};
            case BANDS: return new double[]{spec.lo, spec.hi};
            case OPTIONS: return null;
            default: return new double[]{spec.p < 1 ? 0 : spec.lo, spec.hi};
        }
    }

    // ---- Rolls ----

    /** A new brush in this style. */
    ModularBrush roll(Random random) { return roll(random, null, null); }
    /**
     * parts.js rollStyle: a new name and seed, then each part rolled, but a locked part copied whole from
     * {@code old}, and a locked dial kept when its part rolls {@code old}'s choice again; last, the name says what
     * stands out ({@link BrushNames#described}). {@code old} and {@code locks} may be null.
     */
    ModularBrush roll(Random random, ModularBrush old, ModularLocks locks) {
        if (locks == null) locks = new ModularLocks();
        String name = BrushNames.fresh(random);
        long seed = random.nextInt() & 0xffffffffL;
        ModularBrush b = new ModularBrush(name, seed, this);
        for (BrushPart part : BrushPart.values()) {
            if (old != null && locks.parts.contains(part)) { b.set(part, old.part(part).copy()); continue; }
            b.set(part, rollPart(part, old == null ? null : old.part(part), locks.dials, random));
            if (old != null) b.set(part, keepLocked(b.part(part), old.part(part), locks.dials));
        }
        for (BrushPart part : BrushPart.values()) if (old == null || !locks.parts.contains(part)) rollGravity(b.part(part), locks.dials, random);
        for (BrushPart part : BrushPart.values()) if (old == null || !locks.parts.contains(part)) rollLate(b, part, old == null ? null : old.part(part), locks.dials, random);
        if (old == null || !locks.parts.contains(BrushPart.LIFE)) rollFloat(b);
        if (old == null || !locks.parts.contains(BrushPart.TIP)) rollBristles(b, old == null ? null : old.part(BrushPart.TIP), locks.dials, new Random(b.seed ^ 0xb415e5L));
        if (old == null || !locks.parts.contains(BrushPart.TIP)) rollPattern(b);
        keepErasable(b, old);
        if (old == null || !locks.parts.contains(BrushPart.MEDIUM) && !locks.dials.contains(M_BLEND)) rollBlend(b);
        if (old == null || !locks.parts.contains(BrushPart.LIFE)) rollHalo(b);
        if (old == null || !locks.parts.contains(BrushPart.MEDIUM) && !locks.dials.contains(LINE)) rollCore(b);
        if (old == null || !locks.parts.contains(BrushPart.TIP)) rollRake(b);
        if (old == null || !locks.parts.contains(BrushPart.MEDIUM)) rollTone(b);
        if (old == null || !locks.parts.contains(BrushPart.MEDIUM) && !locks.dials.contains(SCREEN)) rollScreen(b);
        if (old == null || !locks.parts.contains(BrushPart.CARRY)) rollClearWater(b);
        if (old == null || !locks.parts.contains(BrushPart.TIP) && !locks.dials.contains(FLICK)) rollFlick(b);
        if (old == null || !locks.parts.contains(BrushPart.MEDIUM) && !locks.dials.contains(FLING)) rollFling(b);
        if (old == null || !locks.parts.contains(BrushPart.TIP)) rollFilbert(b);
        if (old == null || !locks.parts.contains(BrushPart.CARRY) && !locks.dials.contains(PLOAD)) rollPaintLoad(b);
        if (old == null || !locks.parts.contains(BrushPart.LIFE)) rollFade(b);
        b.normalize();
        // Last, from the finished brush and its seed: what stands out takes the adjective's place ("drip-plum").
        b.name = BrushNames.described(b, name);
        return b;
    }

    /** {@code medium}'s Erasable where a brush is made in this style ({@link PartChoice#erasable}): 1, every style alike (Oils' paint was off until 2026-10-22). */
    double erasable(PartChoice medium) { return medium.erasable(); }

    /**
     * The owner (2026-10-22): "maybe the randomizer leaves that var alone unless the user moves it". A roll (Randomize, a
     * style picked, the dice, a part's die) never changes Erasable: {@code b}'s medium takes {@code old}'s value, or 1
     * where there is no old brush or either medium is Resist (whose dial is wax or fluid, its own roll's). Draws nothing.
     */
    static void keepErasable(ModularBrush b, ModularBrush old) {
        ModularBrush.Part m = b.part(BrushPart.MEDIUM), o = old == null ? null : old.part(BrushPart.MEDIUM);
        if (!m.has(ERASABLE) || m.choice == RESIST) return;
        m.set(ERASABLE, o != null && o.has(ERASABLE) && o.choice != RESIST ? o.get(ERASABLE) : 1);
    }

    /** Whether rolls may pick Stipple and Hatch; the checks turn it off to see every other roll is as before. */
    static boolean patternRolls = true;
    /** One roll in how many is Stipple, and Hatch (newmods.js NM_ODDS); 0 for never. */
    int stippleOdds() { return wild ? 30 : name.equals("Fineliner") ? 40 : name.equals("Watercolor") ? 45 : 0; }
    int hatchOdds() { return wild ? 30 : name.equals("Fineliner") ? 35 : name.equals("Dip pen") ? 30 : name.equals("Pencil") ? 35 : 0; }
    /** newmods.js NM_SALT's: each new choice's own hash of the seed. */
    private static final int STIPPLE_SALT = 0x5717e, HATCH_SALT = 0x4a7c4;
    /**
     * Stipple and Hatch are rare (newmods.js rollNew): decided after every other part, from the brush's seed with each
     * one's salt rather than another random draw, so every other roll comes out exactly as before, and a brush either
     * keeps its tip or changes it to one of them, its weight kept. Stipple first, then Hatch, at the lab's odds; their
     * dials from the seed too: a Stipple's dots .35–.65 (a wash's .35–.55, sponged: Jitter .7–1), Scatter .2–.8; a
     * Hatch's Spacing .2–.6, Line weight .15–.45, angle 30–60°, fixed three times in four.
     */
    void rollPattern(ModularBrush b) {
        if (!patternRolls) return;
        int stipple = stippleOdds(), hatch = hatchOdds();
        if (stipple > 0 && hash(b, STIPPLE_SALT, 0) < 1.0 / stipple) {
            ModularBrush.Part p = ModularBrush.switchTo(b.part(BrushPart.TIP), STIPPLE);
            boolean sponge = b.choice(BrushPart.MEDIUM) == WASH;
            double u1 = hash(b, STIPPLE_SALT, 1), u3 = hash(b, STIPPLE_SALT, 3);
            p.set(DOT, sponge ? lerp(.35, .55, u1) : lerp(.35, .65, u1));
            p.set(SCATTER, lerp(.2, .8, hash(b, STIPPLE_SALT, 2)));
            p.set(JITTER, sponge ? lerp(.7, 1, u3) : u3);
            b.set(BrushPart.TIP, p);
        } else if (hatch > 0 && hash(b, HATCH_SALT, 0) < 1.0 / hatch) {
            ModularBrush.Part p = ModularBrush.switchTo(b.part(BrushPart.TIP), HATCH);
            p.set(PITCH, lerp(.2, .6, hash(b, HATCH_SALT, 1)));
            p.set(LINE_W, lerp(.15, .45, hash(b, HATCH_SALT, 2)));
            p.set(HANGLE, lerp(30, 60, hash(b, HATCH_SALT, 3)));
            p.set(HEAD_TURN, hash(b, HATCH_SALT, 4) < .75 ? 1 : 0);
            b.set(BrushPart.TIP, p);
        }
    }
    /** Whether rolls may pick a Blend; the checks turn it off to see every other roll is as before. */
    static boolean blendRolls = true;
    /** newmods.js NM_SALT's for Blend's White (its key: lighten), Behind and Invert, and Resist's (which Blend gives way to). */
    private static final int WHITE_SALT = 0x119a7, BEHIND_SALT = 0xbe41d, INVERT_SALT = 0x14e47, RESIST_SALT = 0x7e515;
    /** One roll in how many is White, Behind, Invert and Resist (newmods.js NM_ODDS); 0 for never. */
    int whiteOdds() { return wild ? 40 : name.equals("Pastels") ? 30 : name.equals("Charcoal") || name.equals("Gouache") ? 40 : 0; }
    int behindOdds() { return name.equals("Watercolor") || name.equals("Ink wash") ? 30 : name.equals("Gouache") ? 35 : 0; }
    int invertOdds() { return wild ? 80 : 0; }
    int resistOdds() { return name.equals("Watercolor") ? 60 : 0; }
    /** Whether rolls may pick Resist (with {@link #blendRolls}: off, neither); the checks turn it off to see every other roll is as before. */
    static boolean resistRolls = true;
    /**
     * Resist and Blend are rare (newmods.js rollNew): decided after every other part, from the brush's seed with each
     * one's salt rather than another random draw, so every other roll comes out exactly as before, and a brush either
     * keeps its medium as it is, becomes Resist (masking fluid seven times in ten, else wax) or gains one Blend: White,
     * then Behind, then Invert, at the lab's odds.
     */
    void rollBlend(ModularBrush b) {
        if (!blendRolls) return;
        int resist = resistOdds(), white = whiteOdds(), behind = behindOdds(), invert = invertOdds();
        if (resist > 0 && hash(b, RESIST_SALT, 0) < 1.0 / resist) {
            // Off: the medium stays as rolled, without a Blend (as before Resist drew).
            if (!resistRolls) return;
            ModularBrush.Part p = ModularBrush.switchTo(b.part(BrushPart.MEDIUM), RESIST);
            p.set(ERASABLE, hash(b, RESIST_SALT, 1) < .7 ? 1 : 0);
            b.set(BrushPart.MEDIUM, p);
            return;
        }
        int option = white > 0 && hash(b, WHITE_SALT, 0) < 1.0 / white ? 1 : behind > 0 && hash(b, BEHIND_SALT, 0) < 1.0 / behind ? 2
                : invert > 0 && hash(b, INVERT_SALT, 0) < 1.0 / invert ? 3 : 0;
        if (option > 0) b.part(BrushPart.MEDIUM).set(M_BLEND, option);
    }
    /** newmods.js u(key, i): the brush's seed hashed with a choice's salt, 0 to 1. */
    private static double hash(ModularBrush b, int salt, int i) { return StrokeMath.hash01((int)b.seed ^ salt, i); }

    /** Whether rolls may pick Halo; the checks turn it off to see every other roll is as before. */
    static boolean haloRolls = true;
    /** One roll in how many is Halo (newmods.js NM_ODDS); 0 for never. */
    int haloOdds() { return wild || name.equals("Ink wash") ? 30 : name.equals("Fineliner") ? 50 : 0; }
    /** newmods.js NM_SALT's for Halo. */
    private static final int HALO_SALT = 0x4a10;
    /**
     * Halo is rare (newmods.js rollNew): decided after every other part, from the brush's seed with its salt rather than
     * another random draw, so every other roll comes out exactly as before, and a brush either keeps its drying as rolled
     * or becomes Halo (its drying time kept), never from Float: Anything goes and Ink wash 1 in 30, Fineliner 1 in 50.
     * Reach and Growth .3–.8 from the seed too.
     */
    void rollHalo(ModularBrush b) {
        int n = haloRolls ? haloOdds() : 0;
        if (n == 0 || b.choice(BrushPart.LIFE) == FLOAT || !(hash(b, HALO_SALT, 0) < 1.0 / n)) return;
        ModularBrush.Part p = ModularBrush.switchTo(b.part(BrushPart.LIFE), HALO);
        p.set(H_REACH, lerp(.3, .8, hash(b, HALO_SALT, 1)));
        p.set(GROWTH, lerp(.3, .8, hash(b, HALO_SALT, 2)));
        b.set(BrushPart.LIFE, p);
    }

    /** Whether rolls may pick Smudge's Clear Water; the checks turn it off to see every other roll is as before. */
    static boolean waterRolls = true;
    /** One roll in how many is Clear Water (newmods.js NM_ODDS): Anything goes 1 in 40, Watercolor and Ink wash 1 in 50; 0 for never. */
    int waterOdds() { return wild ? 40 : name.equals("Watercolor") || name.equals("Ink wash") ? 50 : 0; }
    /** newmods.js NM_SALT's for Clear Water. */
    private static final int WATER_SALT = 0xc1ea4;
    /**
     * Clear Water is rare (the wet smudge spec, newmods.js rollNew): decided after every other part, from the brush's
     * seed with its salt rather than another random draw, so every other roll comes out exactly as before, and a brush
     * either keeps its smudge as rolled or lays clear water instead (its paint gone, its Drying kept), never on Float.
     * Pick Up Paint .35–.75 and Lay Down Paint .4–.8 from the seed too.
     */
    void rollClearWater(ModularBrush b) {
        int n = waterRolls ? waterOdds() : 0;
        if (n == 0 || b.choice(BrushPart.LIFE) == FLOAT || !(hash(b, WATER_SALT, 0) < 1.0 / n)) return;
        ModularBrush.Part p = new ModularBrush.Part(CLEAR_WATER);
        p.set(PICK, lerp(.35, .75, hash(b, WATER_SALT, 1)));
        p.set(LAY, lerp(.4, .8, hash(b, WATER_SALT, 2)));
        b.set(BrushPart.CARRY, p);
    }

    /** Whether rolls may give Flick, and Fling; the checks turn them off to see every other roll is as before. */
    static boolean flickRolls = true, flingRolls = true;
    /** One roll in how many flicks (the mischief modules spec): Brush pen, Ink wash and Anything goes 1 in 12, Watercolor 1 in 20; 0 for never. */
    int flickOdds() { return wild || name.equals("Brush pen") || name.equals("Ink wash") ? 12 : name.equals("Watercolor") ? 20 : 0; }
    /** One roll in how many flings: Dip pen, Brush pen and Anything goes 1 in 15; 0 for never. */
    int flingOdds() { return wild || name.equals("Dip pen") || name.equals("Brush pen") ? 15 : 0; }
    /** newmods.js NM_SALT's for Flick and Fling. */
    private static final int FLICK_SALT = 0xf11c4, FLING_SALT = 0xf1196;
    /**
     * Flick is rare (the mischief modules spec, Stage 2): decided after every other part, from the brush's seed with its
     * salt rather than another random draw, so every other roll comes out exactly as before, and a brush either keeps
     * its tip as rolled or carries on past a moving lift: Flick .35 to .85 from the seed too.
     */
    void rollFlick(ModularBrush b) {
        int n = flickRolls ? flickOdds() : 0;
        ModularBrush.Part t = b.part(BrushPart.TIP);
        if (n == 0 || !t.has(FLICK) || !(hash(b, FLICK_SALT, 0) < 1.0 / n)) return;
        t.set(FLICK, lerp(.35, .85, hash(b, FLICK_SALT, 1)));
    }
    /** Fling, as Flick: a medium with Blend (not Resist or Screentone) throws drops on a hard stop, Fling .3 to .8 from the seed. */
    void rollFling(ModularBrush b) {
        int n = flingRolls ? flingOdds() : 0;
        ModularBrush.Part m = b.part(BrushPart.MEDIUM);
        if (n == 0 || !m.has(FLING) || !(hash(b, FLING_SALT, 0) < 1.0 / n)) return;
        m.set(FLING, lerp(.3, .8, hash(b, FLING_SALT, 1)));
    }

    /** Whether rolls may give a Filbert and Paint Load (the oil paint spec); the checks turn it off to see every other roll is as before. */
    static boolean oilRolls = true;
    /** One roll in how many has a Filbert (Oils one in three, Anything goes, which reaches every choice, one in 30), and Paint Load (Oils one in three); 0 for never. */
    int filbertOdds() { return name.equals("Oils") ? 3 : wild ? 30 : 0; }
    int paintLoadOdds() { return name.equals("Oils") ? 3 : 0; }
    /** Salts for the Filbert and Paint Load. */
    private static final int FILBERT_SALT = 0xf11be, PLOAD_SALT = 0x910ad;
    /**
     * The Filbert (the oil paint spec): decided after every other part, from the brush's seed with its salt rather than
     * another random draw, so every other roll comes out exactly as before; an Oils (or Anything goes) round or flat nib
     * becomes a Filbert one roll in {@link #filbertOdds}, keeping what a tip swap keeps (its size, tapers, dry brush, soft edge), its
     * width .35 to .55 from the seed too. A hake or bristles stays as rolled.
     */
    void rollFilbert(ModularBrush b) {
        int n = oilRolls ? filbertOdds() : 0;
        PartChoice tip = b.choice(BrushPart.TIP);
        if (n == 0 || tip != ROUND && tip != FLAT_NIB || !(hash(b, FILBERT_SALT, 0) < 1.0 / n)) return;
        ModularBrush.Part p = ModularBrush.switchTo(b.part(BrushPart.TIP), FILBERT);
        p.set(ASPECT, lerp(.35, .55, hash(b, FILBERT_SALT, 1)));
        b.set(BrushPart.TIP, p);
    }
    /**
     * Paint Load (the oil paint spec), as the Filbert: an Oils Drag or Smear runs out of paint one roll in {@link
     * #paintLoadOdds}, Paint Load .35 to .75 from the seed, its Starting Load the default.
     */
    void rollPaintLoad(ModularBrush b) {
        int n = oilRolls ? paintLoadOdds() : 0;
        ModularBrush.Part c = b.part(BrushPart.CARRY);
        if (n == 0 || !c.has(PLOAD) || !(hash(b, PLOAD_SALT, 0) < 1.0 / n)) return;
        c.set(PLOAD, lerp(.35, .75, hash(b, PLOAD_SALT, 1)));
    }

    /** Whether rolls may pick Fading, and Lemon Juice; the checks turn them off to see every other roll is as before. */
    static boolean fadeRolls = true, lemonRolls = true;
    /** One roll in how many fades (the mischief modules spec, Stage 3): Anything goes 1 in 40; 0 for never. */
    int fadeOdds() { return wild ? 40 : 0; }
    /** One roll in how many is lemon juice: Anything goes 1 in 40, Ink wash 1 in 60; 0 for never. */
    int lemonOdds() { return wild ? 40 : name.equals("Ink wash") ? 60 : 0; }
    /** Salts for Fading and Lemon Juice. */
    private static final int FADE_SALT = 0xfade5, LEMON_SALT = 0x1e307;
    /**
     * Fading and Lemon Juice are rare (the mischief modules spec, Stage 3): decided after every other part, from the
     * brush's seed with each one's salt rather than another random draw, so every other roll comes out exactly as before,
     * and a brush either keeps its drying as rolled or fades or darkens in instead, never from Float or Mold: Fading
     * first, Fades In 15 to 60 s from the seed too; else Lemon Juice, Dries In 3 to 7 min.
     */
    void rollFade(ModularBrush b) {
        PartChoice life = b.choice(BrushPart.LIFE);
        if (life == FLOAT || life == HALO) return;
        int fade = fadeRolls ? fadeOdds() : 0, lemon = lemonRolls ? lemonOdds() : 0;
        if (fade > 0 && hash(b, FADE_SALT, 0) < 1.0 / fade) {
            ModularBrush.Part p = new ModularBrush.Part(FADE);
            p.set(FADE_IN, Math.round(lerp(15, 60, hash(b, FADE_SALT, 1))));
            b.set(BrushPart.LIFE, p);
        } else if (lemon > 0 && hash(b, LEMON_SALT, 0) < 1.0 / lemon) {
            ModularBrush.Part p = new ModularBrush.Part(LEMON);
            p.set(DRY_IN, 30 * Math.round(lerp(6, 14, hash(b, LEMON_SALT, 1))));
            b.set(BrushPart.LIFE, p);
        }
    }

    /** Whether rolls thin out the core line and the rake; the checks turn it off to see every other roll is as before. */
    static boolean coreRolls = true;
    /** One mark in how many keeps its core line: Ink wash, whose look it is, 1 in 2; every other style 1 in 4. */
    int coreKeepOdds() { return name.equals("Ink wash") ? 2 : 4; }
    /** Salts for the core line and the rake. */
    private static final int CORE_SALT = 0x3c0e, RAKE_SALT = 0x4a4e;
    /**
     * The owner's "internal line, weighted less often": a mark with a pen line through its heart (a wash with Pen line
     * on, or ink at least .35 wet) keeps it one roll in {@link #coreKeepOdds}, decided from the seed with its salt like
     * Halo, so every other roll comes out as before. Without it a wash loses its line, and wet ink goes .9–1 wet (no more than
     * its style allows) with Pen line 0, so its line melts into the mark.
     */
    void rollCore(ModularBrush b) {
        if (!coreRolls || hash(b, CORE_SALT, 0) < 1.0 / coreKeepOdds()) return;
        ModularBrush.Part m = b.part(BrushPart.MEDIUM);
        if (m.choice == WASH && m.get(LINE) > 0) m.set(LINE, 0);
        else if (m.choice == INK && m.get(WET) >= .35 && m.get(LINE) > 0) {
            m.set(LINE, 0);
            // Up to the style's own top (Ink wash: .9).
            double hi = spec(WET) != null ? spec(WET).hi : 1;
            m.set(WET, Math.max(m.get(WET), lerp(Math.min(.9, hi), hi, hash(b, CORE_SALT, 1))));
        }
    }
    /** One rolled Rake in how many stays a Rake; the rest become a Round tip. */
    static final int RAKE_KEEP_ODDS = 3;
    /** The owner's "multiline nibs less often": a rolled Rake stays one in {@link #RAKE_KEEP_ODDS}, from the seed with its salt. */
    void rollRake(ModularBrush b) {
        if (!coreRolls || b.choice(BrushPart.TIP) != MULTI_LINE || hash(b, RAKE_SALT, 0) < 1.0 / RAKE_KEEP_ODDS) return;
        b.set(BrushPart.TIP, ModularBrush.switchTo(b.part(BrushPart.TIP), ROUND));
    }

    /** Whether rolls may pick a Screen, and Tone; the checks turn them off to see every other roll is as before. */
    static boolean screenRolls = true, toneRolls = true;
    /** newmods.js NM_SALT's for Screen and Tone. */
    private static final int SCREEN_SALT = 0x5c4ee, TONE_SALT = 0x70e5, NEW_SCREEN_SALT = 0x5c4e6, NEW_SCREEN_ODDS = 6;
    /** One roll in how many has a Screen (newmods.js NM_ODDS; never Watercolor; Print every one), and is Tone (Anything goes, and Print one in five); 0 for never. */
    int screenOdds() { return name.equals("Print") ? 1 : wild || name.equals("Marker") || name.equals("Ink wash") || name.equals("Charcoal") ? 25 : name.equals("Fineliner") || name.equals("Pencil") ? 40 : 0; }
    int toneOdds() { return wild ? 40 : name.equals("Print") ? 5 : 0; }
    /**
     * Screen is rare (the mischief modules spec): decided after every other part, from the brush's seed with its salt
     * rather than another random draw, so every other roll comes out exactly as before, and a brush either keeps its
     * medium as it is or gains a Screen: halftone, line screen, dither or grain alike, a cell of 4 to 10 px, its angle
     * 45° one roll in two (else 0 to 90°), and a newsprint dial now and then (Misregister one in four, Ink gain and
     * Uneven one in three). A medium without one (Resist, Tone) stays as rolled.
     */
    void rollScreen(ModularBrush b) {
        int n = screenRolls ? screenOdds() : 0;
        ModularBrush.Part m = b.part(BrushPart.MEDIUM);
        if (n == 0 || !m.has(SCREEN) || !(hash(b, SCREEN_SALT, 0) < 1.0 / n)) return;
        // One roll in NEW_SCREEN_ODDS takes one of the six appended patterns instead (the rate of a Screen stays as it was).
        // Print picks any of the ten alike.
        if (name.equals("Print")) m.set(SCREEN, 1 + Math.min(9, (int) (hash(b, NEW_SCREEN_SALT, 1) * 10)));
        else m.set(SCREEN, hash(b, NEW_SCREEN_SALT, 0) < 1.0 / NEW_SCREEN_ODDS ? 5 + Math.min(5, (int) (hash(b, NEW_SCREEN_SALT, 1) * 6)) : 1 + Math.min(3, (int) (hash(b, SCREEN_SALT, 1) * 4)));
        m.set(CELL, Math.round(lerp(4, 10, hash(b, SCREEN_SALT, 2))));
        m.set(S_ANGLE, hash(b, SCREEN_SALT, 3) < .5 ? 45 : Math.round(lerp(0, 90, hash(b, SCREEN_SALT, 4))));
        m.set(MIS, hash(b, SCREEN_SALT, 5) < .25 ? Math.round(lerp(1, 5, hash(b, SCREEN_SALT, 6))) : 0);
        m.set(S_GAIN, hash(b, SCREEN_SALT, 7) < 1 / 3.0 ? lerp(.2, .6, hash(b, SCREEN_SALT, 8)) : 0);
        m.set(UNEVEN, hash(b, SCREEN_SALT, 9) < 1 / 3.0 ? lerp(.2, .6, hash(b, SCREEN_SALT, 10)) : 0);
    }
    /**
     * Tone is rarer still (the mischief modules spec), from the seed with its salt like Screen: Anything goes one
     * roll in 40 becomes it, any pattern alike, its Tone .25 to .65, a cell of 4 to 10 px, 45° one in two.
     */
    void rollTone(ModularBrush b) {
        int n = toneRolls ? toneOdds() : 0;
        if (n == 0 || b.choice(BrushPart.MEDIUM) == RESIST || !(hash(b, TONE_SALT, 0) < 1.0 / n)) return;
        ModularBrush.Part p = ModularBrush.switchTo(b.part(BrushPart.MEDIUM), TONE);
        p.set(PATTERN, Math.min(3, (int) (hash(b, TONE_SALT, 1) * 4)));
        p.set(TTONE, lerp(.25, .65, hash(b, TONE_SALT, 2)));
        p.set(CELL, Math.round(lerp(4, 10, hash(b, TONE_SALT, 3))));
        p.set(S_ANGLE, hash(b, TONE_SALT, 4) < .5 ? 45 : Math.round(lerp(0, 90, hash(b, TONE_SALT, 5))));
        b.set(BrushPart.MEDIUM, p);
    }

    /** Whether rolls may pick Float; the checks turn it off to see every other roll is as before. */
    static boolean floatRolls = true;
    /** One roll in how many is Float ({@link #rollFloat}); 0 for never. */
    int floatOdds() { return !ModularFeatures.FLOAT || !floatRolls ? 0 : wild ? 30 : name.equals("Ink wash") ? 25 : name.equals("Watercolor") ? 40 : 0; }
    /**
     * Float is rare (parts.js rollFloat): decided after every other part, from the brush's seed rather than another
     * random draw, so every other roll (and the lab's seeded starting library) comes out exactly as before: Anything goes
     * 1 in 30, Ink wash 1 in 25, Watercolor 1 in 40, no other style. Spread .3–.8 from the seed too, Drift .5; its
     * floating time is the Slow drying time it had, if any.
     */
    void rollFloat(ModularBrush b) {
        int n = floatOdds();
        if (n == 0) return;
        long h = (((int) b.seed ^ 0x5f10a7) * (int) 2654435761L) & 0xffffffffL;
        if (h % n != 0) return;
        ModularBrush.Part p = ModularBrush.switchTo(b.part(BrushPart.LIFE), FLOAT);
        p.set(F_SPREAD, .3 + .5 * ((h >>> 8) % 1000) / 1000.0);
        p.set(DRIFT, .5);
        b.set(BrushPart.LIFE, p);
    }

    /** parts.js rollOnePart: {@code part} rolled again (its dice), keeping locked dials, on a copy keeping the name and seed. */
    ModularBrush rollOnePart(ModularBrush b, BrushPart part, ModularLocks locks, Random random) {
        if (locks == null) locks = new ModularLocks();
        ModularBrush c = b.copy();
        ModularBrush.Part old = b.part(part);
        c.set(part, keepLocked(rollPart(part, old, locks.dials, random), old, locks.dials));
        rollGravity(c.part(part), locks.dials, random);
        rollLate(c, part, old, locks.dials, random);
        if (part == BrushPart.TIP) rollBristles(c, old, locks.dials, random);
        if (part == BrushPart.MEDIUM) keepErasable(c, b);
        return c.normalize();
    }

    /**
     * parts.js rollOneDial: {@code d} rolled again (its dice), on a copy. A hand cell is off three times in ten,
     * else .25–1, and the hand is renamed for its cells; another dial rolls in this style's band, or over its
     * whole range where the style has none.
     */
    ModularBrush rollOneDial(ModularBrush b, PartDial d, Random random) {
        ModularBrush c = b.copy();
        if (!c.part(d.part).has(d)) return c;
        // Erasable has no die (only Resist's, which is wax or fluid): it moves only by hand.
        if (d == ERASABLE && c.part(d.part).choice != RESIST) return c;
        ModularBrush.Part p = c.part(d.part);
        if (Arrays.asList(GRID_CELLS).contains(d)) {
            p.set(d, random.nextDouble() < .3 ? 0 : lerp(.25, 1, random.nextDouble()));
            c.set(BrushPart.HAND, ModularBrush.namedHand(p));
        } else {
            double v = rollDial(p.choice, d, random);
            double[] band = band(p.choice, d), r = p.choice.range(d);
            p.set(d, band != null || d.choice() ? v : lerp(r[0], r[1], random.nextDouble()));
        }
        return c.normalize();
    }

    /**
     * parts.js rollPart: a weighted choice and each of its dials rolled, a locked dial kept from {@code old} when
     * it holds the same choice; Anything goes caps its rough dials together at 1.1. The hand rolls its own way.
     */
    ModularBrush.Part rollPart(BrushPart part, ModularBrush.Part old, Set<PartDial> locked, Random random) {
        if (part == BrushPart.HAND) return rollHand(random);
        return rollChoice(pickWeighted(part, random), old, locked, random);
    }
    /** rollPart's dials for choice {@code c}: each rolled, a locked dial kept from {@code old} when it holds {@code c}. */
    private ModularBrush.Part rollChoice(PartChoice c, ModularBrush.Part old, Set<PartDial> locked, Random random) {
        ModularBrush.Part p = new ModularBrush.Part(c);
        for (PartDial d : c.dials) {
            // A gravity dial a style rolls is rolled after every part (rollGravity); here it stays off.
            if (d.gravity() && spec(d) != null) continue;
            // Erasable draws nothing from random and is never rolled: 1 here, the old brush's after every part (keepErasable).
            if (d == ERASABLE) { p.set(d, locked != null && locked.contains(d) && old != null && old.choice == c ? old.get(d) : erasable(c)); continue; }
            // Blend rolls from the seed after every part (rollBlend): here the medium's own way, drawing nothing from random.
            if (d == M_BLEND) { p.set(d, locked != null && locked.contains(d) && old != null && old.choice == c ? old.get(d) : 0); continue; }
            // Screen and its dials, and Tone's, roll from the seed after every part (rollScreen, rollTone): here their
            // defaults, drawing nothing from random.
            if (d == SCREEN || d.underScreen() || d == PATTERN || d == TTONE) { p.set(d, locked != null && locked.contains(d) && old != null && old.choice == c ? old.get(d) : c.defaultOf(d)); continue; }
            // Smear's Soften is never rolled: 0, today's smear, drawing nothing from random.
            if (d == SOFTEN) { p.set(d, locked != null && locked.contains(d) && old != null && old.choice == c ? old.get(d) : 0); continue; }
            // Keeps Paint is never rolled either: off, a smudge that starts each stroke clean, drawing nothing from random.
            if (d == KEEPS) { p.set(d, locked != null && locked.contains(d) && old != null && old.choice == c ? old.get(d) : 0); continue; }
            // Paint Load and its Starting Load neither: off and the default, drawing nothing from random (Oils' from the seed, rollPaintLoad).
            if (d == PLOAD || d == PMIN) { p.set(d, locked != null && locked.contains(d) && old != null && old.choice == c ? old.get(d) : c.defaultOf(d)); continue; }
            // Flick and Fling roll from the seed after every part (rollFlick, rollFling): here off, drawing nothing from random.
            if (d == FLICK || d == FLING) { p.set(d, locked != null && locked.contains(d) && old != null && old.choice == c ? old.get(d) : 0); continue; }
            p.set(d, locked != null && locked.contains(d) && old != null && old.choice == c ? old.get(d) : rollDial(c, d, random));
        }
        if (wild) {
            double rough = 0;
            for (PartDial d : c.dials) if (ROUGH.contains(d)) rough += p.get(d);
            if (rough > 1.1) for (PartDial d : c.dials) if (ROUGH.contains(d)) p.set(d, p.get(d) * (1.1 / rough));
        }
        return p.normalize();
    }

    /**
     * parts.js rollDial: a value for {@code d} on choice {@code c}. A spec rolls, clamped to the choice's range;
     * without one a style keeps the default, and Anything goes rolls by its own rules. Takes ink rolls 0 until plumes draw.
     */
    double rollDial(PartChoice c, PartDial d, Random random) {
        if (d == TAKES && !ModularFeatures.PLUMES) return 0;
        Spec spec = spec(d);
        double[] r = c.range(d);
        double lo = r[0], hi = r[1];
        if (d.choice()) {
            if (spec != null && spec.kind == Spec.Kind.OPTIONS) return spec.roll(random);
            if (wild) return (int) (random.nextDouble() * d.choices.length);
            if (spec == null) return c.defaultOf(d);
        }
        if (spec != null) return StrokeMath.clamp(spec.roll(random), lo, hi);
        if (!wild) return c.defaultOf(d);
        if (d == WINDOW) return lerp(3, 9, random.nextDouble());
        // Gravity rolls off: a random brush never runs or drips (the Gravity Lab's rule).
        if (d.gravity()) return 0;
        if (d == WEIGHT) return lerp(.8, 1.2, random.nextDouble());
        if (d == LINE) return c == INK ? lerp(.6, 1, random.nextDouble()) : random.nextDouble() < .7 ? 0 : lerp(.3, .8, random.nextDouble());
        if (d == PartDial.COVER) return lerp(.6, 1, random.nextDouble());
        if (d == OFFSET || d == CHAOS) return random.nextDouble() < .25 ? lerp(0, .7, random.nextDouble()) : 0;
        if (d.min == 0 && !WHOLE_RANGE.contains(d))
            return random.nextDouble() < .5 ? lerp(lo, Math.min(hi, lo + (hi - lo) * .8), random.nextDouble()) : 0;
        return lerp(lo, hi, random.nextDouble());
    }

    /**
     * The choices rolls draw after every part ({@link ModularFeatures#late}): {@code part} of {@code b} becomes one, its
     * dials rolled, on its weight's share of the part's offered weights (the prototype's odds), keeping locked dials
     * from {@code old}. Only a style that weighs one draws at all, so every other style rolls exactly as before.
     */
    private void rollLate(ModularBrush b, BrushPart part, ModularBrush.Part old, Set<PartDial> locked, Random random) {
        Map<PartChoice, Double> w = weights.get(part);
        if (w == null) return;
        double total = 0;
        for (Map.Entry<PartChoice, Double> e : w.entrySet()) if (ModularFeatures.offered(e.getKey())) total += e.getValue();
        for (Map.Entry<PartChoice, Double> e : w.entrySet()) {
            PartChoice c = e.getKey();
            if (!ModularFeatures.late(c) || c == BRISTLES || !ModularFeatures.offered(c) || !(e.getValue() > 0)) continue;
            if (random.nextDouble() * total < e.getValue()) {
                ModularBrush.Part p = rollChoice(c, old, locked, random);
                b.set(part, old == null ? p : keepLocked(p, old, locked));
                return;
            }
            total -= e.getValue();
        }
    }

    /** Whether rolls may pick Bristles; the checks turn it off to see every other roll is as before. */
    static boolean bristleRolls = true;
    /**
     * Bristles (late, {@link ModularFeatures#late}), decided last of all, after Smudge and Float: the tip of {@code b}
     * becomes Bristles on its weight's share of the tip's offered weights (the prototype's odds), its dials rolled,
     * keeping locked dials from {@code old}. Only a style that weighs it draws at all (Dry brush, Anything goes). A
     * brush's roll draws it from its own seed, as Float, so every other roll, and every roll after it from the same
     * Random, is what it was before Bristles joined; the tip's die draws it from the die's Random.
     */
    private void rollBristles(ModularBrush b, ModularBrush.Part old, Set<PartDial> locked, Random random) {
        double w = weight(BRISTLES);
        if (!bristleRolls || !ModularFeatures.offered(BRISTLES) || !(w > 0)) return;
        double total = 0;
        for (PartChoice c : BrushPart.TIP.choices()) if (ModularFeatures.offered(c)) total += weight(c);
        if (random.nextDouble() * total >= w) return;
        ModularBrush.Part p = rollChoice(BRISTLES, old, locked, random);
        b.set(BrushPart.TIP, old == null ? p : keepLocked(p, old, locked));
    }

    /**
     * The gravity dials this style rolls (Watercolor's Runs and Pool, Charcoal's and Pastels' Dust), on {@code p} unless
     * locked: drawn after every part, so the style's other rolls are what they were before gravity.
     */
    private void rollGravity(ModularBrush.Part p, Set<PartDial> locked, Random random) {
        for (PartDial d : p.choice.dials) {
            Spec spec = d.gravity() ? spec(d) : null;
            if (spec == null || locked.contains(d)) continue;
            double[] r = p.choice.range(d);
            p.set(d, StrokeMath.clamp(spec.roll(random), r[0], r[1]));
        }
    }

    /** parts.js rollHand: the cells from the style's specs (else off), or Anything goes' odds for the nine and a coin for Fast is; named for its cells. */
    private ModularBrush.Part rollHand(Random random) {
        ModularBrush.Part p = new ModularBrush.Part(CUSTOM);
        for (int i = 0; i < HAND_CELLS.length; i++) {
            Spec spec = wild ? WILD_HAND[i] : spec(HAND_CELLS[i]);
            p.set(HAND_CELLS[i], spec != null ? spec.roll(random) : 0);
        }
        if (wild) p.set(DIR, random.nextDouble() < .5 ? 1 : 0);
        // Page tilt only where a style asks for it (none does yet): no draw otherwise, so rolls are as they were.
        else for (PartDial cell : PAGE_CELLS) { Spec spec = spec(cell); if (spec != null) p.set(cell, spec.roll(random)); }
        return ModularBrush.namedHand(p).normalize();
    }

    /**
     * parts.js pickWeighted, over the choices rolls may pick with their part ({@link #picks}): walks the weights in
     * order. With none left, the part's first such choice.
     */
    private PartChoice pickWeighted(BrushPart part, Random random) {
        Map<PartChoice, Double> w = weights.get(part);
        double total = 0;
        if (w != null) for (Map.Entry<PartChoice, Double> e : w.entrySet()) if (picks(e.getKey())) total += e.getValue();
        if (total <= 0) {
            for (PartChoice c : part.choices()) if (picks(c)) return c;
            throw new IllegalStateException("No " + part + " choice is offered");
        }
        double r = random.nextDouble() * total;
        PartChoice last = null;
        for (Map.Entry<PartChoice, Double> e : w.entrySet()) {
            if (!picks(e.getKey())) continue;
            last = e.getKey();
            r -= e.getValue();
            if (r <= 0) return last;
        }
        return last;
    }

    /** Whether a part's roll picks {@code c} with its other choices: offered, and not drawn after every part ({@link #rollLate}). */
    private static boolean picks(PartChoice c) { return ModularFeatures.offered(c) && !ModularFeatures.late(c); }

    /**
     * A rolled part keeps its locked dials' values from {@code old} when it rolled {@code old}'s choice again. Every
     * hand has the same dials, so a hand keeps its locked cells whatever it rolled, and is named for its cells
     * (Brush Lab's hand grid locks a row of cells). Returns the part.
     */
    private static ModularBrush.Part keepLocked(ModularBrush.Part rolled, ModularBrush.Part old, Set<PartDial> locked) {
        boolean hand = rolled.choice.part == BrushPart.HAND;
        if (old.choice != rolled.choice && !hand) return rolled;
        for (PartDial d : rolled.choice.dials) if (locked.contains(d)) rolled.set(d, old.get(d));
        return hand ? ModularBrush.namedHand(rolled) : rolled;
    }
}
