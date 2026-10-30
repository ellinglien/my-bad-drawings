package io.github.mpdairy.monopaint;

import java.util.EnumSet;
import java.util.Random;

public final class ModularStyleChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        sizes(); bands(); withinStyles(); rarer(); charcoal(); hake(); anythingGoes(); nothingDeferred(); locks(); oneAtATime();
        System.out.println("PASS: the 14 styles roll six-part brushes inside their weights and ranges (Charcoal's spread in its bands, Hake's head mostly following), Anything goes reaches every offered choice with tip roughness capped, nothing deferred is rolled, locks keep parts and dials, a part's or a dial's own dice changes only it");
    }
    static ModularStyle s(String name) { return ModularStyle.named(name); }
    static void sizes() {
        check(s("Fineliner").size(PartChoice.INK) == 4 && s("Ink wash").size(PartChoice.INK) == 8 && s("Ink wash").size(PartChoice.WASH) == 90
                && s("Ink wash").size(PartChoice.COVER) == 8 && s("Hake").size(PartChoice.WASH) == 180 && s("Anything goes").size(PartChoice.WASH) == 16, "Sizes as styleSize");
    }
    static void bands() {
        check(java.util.Arrays.equals(s("Fineliner").band(PartChoice.ROUND, PartDial.TAPER_IN), new double[]{0, .15}), "A sometimes-on dial's band runs from 0");
        check(java.util.Arrays.equals(s("Brush pen").band(PartChoice.ROUND, PartDial.TAPER_IN), new double[]{.3, .8}), "An always-on dial's band is its range");
        check(java.util.Arrays.equals(s("Charcoal").band(PartChoice.CHARCOAL, PartDial.SPREAD), new double[]{0, .9}), "Bands span every band");
        check(java.util.Arrays.equals(s("Marker").band(PartChoice.CHISEL, PartDial.ASPECT), new double[]{.35, .55}) && s("Fineliner").band(PartChoice.ROUND, PartDial.SOFT) == null
                && s("Hake").band(PartChoice.HAKE, PartDial.HEAD_TURN) == null && s("Anything goes").band(PartChoice.ROUND, PartDial.WEIGHT) == null, "No band without a spec, for choice dials, or in Anything goes");
    }
    static void withinStyles() {
        for (ModularStyle style : ModularStyle.all()) {
            if (style.wild) continue;
            Random random = new Random(1000 + style.index);
            for (int n = 0; n < 300; n++) {
                ModularBrush b = style.roll(random);
                check(b.style == style && !b.name.isEmpty() && b.seed >= 0 && b.seed <= 0xffffffffL, style.name + " rolls a named brush in itself");
                for (BrushPart part : BrushPart.values()) {
                    ModularBrush.Part p = b.part(part);
                    // Float: rolled rarely from the seed, after every part (FloatChecks counts it), Spread .3–.8, Drift .5.
                    if (p.choice == PartChoice.FLOAT) {
                        check(style.floatOdds() > 0 && p.get(PartDial.F_SPREAD) >= .3 && p.get(PartDial.F_SPREAD) <= .8 && p.get(PartDial.DRIFT) == .5, style.name + " rolls Float only with odds, Spread .3–.8, Drift .5");
                        continue;
                    }
                    // Stipple and Hatch: rolled rarely from the seed, after every part (StippleChecks, HatchChecks count them).
                    if (p.choice == PartChoice.STIPPLE || p.choice == PartChoice.HATCH) {
                        check((p.choice == PartChoice.STIPPLE ? style.stippleOdds() : style.hatchOdds()) > 0, style.name + " rolls " + p.choice + " only with odds");
                        continue;
                    }
                    // Resist: rolled rarely from the seed, after every part (ResistChecks counts it), masking fluid or wax.
                    if (p.choice == PartChoice.RESIST) {
                        check(style.resistOdds() > 0 && (p.get(PartDial.ERASABLE) == 1 || p.get(PartDial.ERASABLE) == 0), style.name + " rolls Resist only with odds, fluid or wax");
                        continue;
                    }
                    // Halo: rolled rarely from the seed, after every part (HaloChecks counts it), Reach and Growth .3–.8.
                    if (p.choice == PartChoice.HALO) {
                        check(style.haloOdds() > 0 && p.get(PartDial.H_REACH) >= .3 && p.get(PartDial.H_REACH) <= .8 && p.get(PartDial.GROWTH) >= .3 && p.get(PartDial.GROWTH) <= .8, style.name + " rolls Halo only with odds, Reach and Growth .3–.8");
                        continue;
                    }
                    // Screentone: rolled rarely from the seed, after every part (ScreenChecks counts it).
                    if (p.choice == PartChoice.TONE) { check(style.toneOdds() > 0, style.name + " rolls Screentone only with odds"); continue; }
                    // Clear Water: rolled rarely from the seed, after every part (WetSmudgeChecks counts it), Pick up .35–.75, Lay down .4–.8.
                    if (p.choice == PartChoice.CLEAR_WATER) {
                        check(style.waterOdds() > 0 && p.get(PartDial.PICK) >= .35 && p.get(PartDial.PICK) <= .75 && p.get(PartDial.LAY) >= .4 && p.get(PartDial.LAY) <= .8, style.name + " rolls Clear Water only with odds, Pick up .35–.75, Lay down .4–.8");
                        continue;
                    }
                    // The Filbert: rolled rarely from the seed, after every part (OilChecks counts it), .35–.55 wide.
                    if (p.choice == PartChoice.FILBERT) {
                        check(style.filbertOdds() > 0 && p.get(PartDial.ASPECT) >= .35 - 1e-9 && p.get(PartDial.ASPECT) <= .55 + 1e-9, style.name + " rolls a Filbert only with odds, .35–.55 wide");
                        continue;
                    }
                    // Fading and Lemon Juice: rolled rarely from the seed, after every part (FadingChecks counts them), 15–60 s and 2–6 s.
                    if (p.choice == PartChoice.FADE || p.choice == PartChoice.LEMON) {
                        boolean fade = p.choice == PartChoice.FADE;
                        double v = p.get(fade ? PartDial.FADE_IN : PartDial.DRY_IN);
                        check((fade ? style.fadeOdds() : style.lemonOdds()) > 0 && (fade ? v >= 15 && v <= 60 : v >= 180 && v <= 420), style.name + " rolls " + p.choice + " only with odds, 15–60 s or 3–7 min");
                        continue;
                    }
                    if (part != BrushPart.HAND) check(style.weight(p.choice) > 0 && ModularFeatures.offered(p.choice), style.name + " rolls only its own offered " + part + " choices: " + p.choice);
                    for (PartDial d : p.choice.dials) {
                        double v = p.get(d), r[] = p.choice.range(d);
                        check(v >= r[0] - 1e-12 && v <= r[1] + 1e-12, style.name + " " + d + " inside its range: " + v);
                        ModularStyle.Spec spec = style.spec(d);
                        if (d.choice() || part == BrushPart.HAND) continue;
                        // The core line, thinned from the seed after every part (ModularStyle.rollCore): Pen line 0.
                        if (d == PartDial.LINE && v == 0) continue;
                        if (d == PartDial.ERASABLE) { check(p.choice == PartChoice.RESIST || v == 1, style.name + " starts Erasable at 1: " + v); continue; }
                        // A Screen's dials: rolled from the seed with it, after every part (ScreenChecks); without one, their defaults.
                        if (d.underScreen() && (int) p.get(PartDial.SCREEN) > 0) { check(style.screenOdds() > 0, style.name + " rolls a Screen only with odds"); continue; }
                        // Flick and Fling: rolled rarely from the seed, after every part (FlickFlingChecks counts them).
                        // Paint Load: rolled rarely from the seed, after every part (OilChecks counts it), .35–.75.
                        if (d == PartDial.PLOAD && v > 0) { check(style.paintLoadOdds() > 0 && v >= .35 - 1e-9 && v <= .75 + 1e-9, style.name + " rolls Paint Load only with odds, .35–.75"); continue; }
                        if (d == PartDial.FLICK && v > 0) { check(style.flickOdds() > 0 && v >= .35 && v <= .85, style.name + " rolls Flick only with odds, .35–.85"); continue; }
                        if (d == PartDial.FLING && v > 0) { check(style.flingOdds() > 0 && v >= .3 && v <= .8, style.name + " rolls Fling only with odds, .3–.8"); continue; }
                        if (spec == null) check(v == Math.max(r[0], Math.min(r[1], p.choice.defaultOf(d))), style.name + " leaves " + d + " at its default (inside its range)");
                        else if (spec.kind == ModularStyle.Spec.Kind.ALWAYS) check(v >= Math.max(spec.lo, r[0]) - 1e-9 && v <= Math.min(spec.hi, r[1]) + 1e-9, style.name + " " + d + " inside its spec: " + v);
                        else if (spec.kind == ModularStyle.Spec.Kind.MAYBE) check(v == 0 || v >= Math.max(spec.lo, r[0]) - 1e-9 && v <= Math.min(spec.hi, r[1]) + 1e-9, style.name + " " + d + " off or inside its spec: " + v);
                    }
                }
                ModularBrush.Part hand = b.part(BrushPart.HAND);
                check(hand.choice == ModularBrush.matchHand(hand) && hand.get(PartDial.GAIN) == 1, style.name + "'s hand is named for its cells, at full response");
            }
        }
    }
    /** The core line in a mark keeps one roll in four (Ink wash two), and a Rake one in three, against the same rolls without the step. */
    static void rarer() {
        ModularStyle wild = ModularStyle.named("Anything goes");
        int n = 6000, rakeOn = 0, rakeOff = 0, coreOn = 0, coreOff = 0;
        for (int i = 0; i < n; i++) {
            ModularBrush on = wild.roll(new java.util.Random(31000L + i)), off;
            ModularStyle.coreRolls = false;
            try { off = wild.roll(new java.util.Random(31000L + i)); } finally { ModularStyle.coreRolls = true; }
            if (on.choice(BrushPart.TIP) == PartChoice.MULTI_LINE) rakeOn++;
            if (off.choice(BrushPart.TIP) == PartChoice.MULTI_LINE) rakeOff++;
            if (core(on)) coreOn++;
            if (core(off)) coreOff++;
        }
        check(rakeOff > 300 && Math.abs(rakeOn - rakeOff / 3.0) <= 4 * Math.sqrt(rakeOff * 2 / 9.0) + 1, "a rolled Rake stays one in three: " + rakeOn + " of " + rakeOff);
        check(coreOff > 600 && Math.abs(coreOn - coreOff / 4.0) <= 4 * Math.sqrt(coreOff * 3 / 16.0) + 1, "a mark keeps its core line one roll in four: " + coreOn + " of " + coreOff);
    }
    /** A mark with a pen line through its heart: a wash with Pen line on, or ink at least .35 wet with it. */
    private static boolean core(ModularBrush b) {
        ModularBrush.Part m = b.part(BrushPart.MEDIUM);
        return m.get(PartDial.LINE) > 0 && (m.choice == PartChoice.WASH || m.choice == PartChoice.INK && m.get(PartDial.WET) >= .35);
    }
    static void charcoal() {
        Random random = new Random(21);
        int mid = 0, crisp = 0, messy = 0, n = 3000;
        for (int i = 0; i < n; i++) {
            double v = s("Charcoal").roll(random).part(BrushPart.MEDIUM).get(PartDial.SPREAD);
            if (v >= .15 && v <= .45) mid++; else if (v <= .1) crisp++; else if (v >= .6 && v <= .9) messy++;
        }
        check(mid > .64 * n && mid < .76 * n && crisp > .11 * n && crisp < .19 * n && messy > .11 * n && messy < .19 * n, "Charcoal's spread rolls 70% .15–.45, 15% crisp, 15% messy: " + mid + " " + crisp + " " + messy);
    }
    static void hake() {
        Random random = new Random(22);
        int fixed = 0, wash = 0, n = 2000;
        for (int i = 0; i < n; i++) {
            ModularBrush b = s("Hake").roll(random);
            check(b.choice(BrushPart.TIP) == PartChoice.HAKE, "Hake rolls a hake");
            if (b.part(BrushPart.TIP).get(PartDial.HEAD_TURN) == 1) fixed++;
            if (b.choice(BrushPart.MEDIUM) == PartChoice.WASH) wash++;
        }
        check(fixed > .18 * n && fixed < .32 * n && wash > .62 * n && wash < .78 * n, "Hake's head is fixed one roll in four, its medium wash seven in ten: " + fixed + " " + wash);
    }
    static void anythingGoes() {
        Random random = new Random(7);
        EnumSet<PartChoice> seen = EnumSet.noneOf(PartChoice.class);
        for (int i = 0; i < 4000; i++) {
            ModularBrush b = s("Anything goes").roll(random);
            for (BrushPart part : BrushPart.values()) seen.add(b.choice(part));
            ModularBrush.Part tip = b.part(BrushPart.TIP);
            check(tip.get(PartDial.DRY) + tip.get(PartDial.WOBBLE) <= 1.1 + 1e-9, "Anything goes caps a tip's roughness");
        }
        // Resist rolls only with Watercolor (the lab's odds); the Stamp never (it needs a cut), nor Crayon, the Spiro, Stay in Lines or Wet (the presets'), nor the photo papers.
        for (PartChoice c : PartChoice.values()) if (c.part != BrushPart.HAND && ModularFeatures.offered(c) && c != PartChoice.RESIST && c != PartChoice.STAMP && c != PartChoice.CRAYON && c != PartChoice.SPIRO && c != PartChoice.STAY_IN && c != PartChoice.SHAPE && c != PartChoice.HAND_HATCH && c != PartChoice.WET_PAINT && !ModularFeatures.photoPaper(c)) check(seen.contains(c), "Anything goes reaches " + c);
        check(!seen.contains(PartChoice.RESIST) && !seen.contains(PartChoice.STAMP) && !seen.contains(PartChoice.CRAYON) && !seen.contains(PartChoice.SPIRO) && !seen.contains(PartChoice.STAY_IN) && !seen.contains(PartChoice.SHAPE) && !seen.contains(PartChoice.HAND_HATCH) && !seen.contains(PartChoice.WET_PAINT), "but never Resist, the Stamp, Crayon, the Spiro, Stay in Lines, Filled Shape, Hand Hatch or Wet");
        check(seen.contains(PartChoice.CUSTOM) && seen.contains(PartChoice.STEADY), "and Custom and Steady hands");
    }
    static void nothingDeferred() {
        for (ModularStyle style : ModularStyle.all()) {
            Random random = new Random(300 + style.index);
            for (int i = 0; i < 500; i++) {
                ModularBrush b = style.roll(random);
                check(ModularFeatures.offered(b.choice(BrushPart.TIP)) && ModularFeatures.offered(b.choice(BrushPart.CARRY)) && b.part(BrushPart.LIFE).get(PartDial.TAKES) == 0, style.name + " rolls nothing the engine can't draw");
            }
        }
    }
    static void locks() {
        Random random = new Random(3);
        ModularStyle pencil = s("Pencil");
        ModularBrush old = pencil.roll(random);
        ModularLocks paper = new ModularLocks(); paper.parts.add(BrushPart.PAPER);
        for (int i = 0; i < 20; i++) check(pencil.roll(random, old, paper).part(BrushPart.PAPER).sameAs(old.part(BrushPart.PAPER)), "A locked part is kept whole");
        ModularLocks grade = new ModularLocks(); grade.dials.add(PartDial.GRADE);
        for (int i = 0; i < 20; i++) check(pencil.roll(random, old, grade).part(BrushPart.MEDIUM).get(PartDial.GRADE) == old.part(BrushPart.MEDIUM).get(PartDial.GRADE), "A locked dial is kept when its choice rolls again");
    }
    static void oneAtATime() {
        Random random = new Random(4);
        ModularStyle dip = s("Dip pen");
        ModularBrush b = dip.roll(random);
        ModularBrush c = dip.rollOnePart(b, BrushPart.TIP, new ModularLocks(), random);
        for (BrushPart part : BrushPart.values()) if (part != BrushPart.TIP) check(c.part(part).sameAs(b.part(part)), "A part's dice changes only that part");
        check(c.seed == b.seed && c.name.equals(b.name) && c != b, "and keeps the name and seed, on a copy");
        ModularBrush d = dip.rollOneDial(b, PartDial.ANGLE, random);
        double angle = d.part(BrushPart.TIP).get(PartDial.ANGLE);
        check(angle >= 30 && angle <= 60, "A dial's dice stays in the style's band: " + angle);
        for (BrushPart part : BrushPart.values()) for (PartDial dial : b.part(part).choice.dials)
            if (dial != PartDial.ANGLE) check(d.part(part).get(dial) == b.part(part).get(dial), "and changes only that dial: " + dial);
        ModularBrush e = dip.rollOneDial(b, PartDial.TS, random);
        double ts = e.part(BrushPart.HAND).get(PartDial.TS);
        check((ts == 0 || ts >= .25 && ts <= 1) && e.choice(BrushPart.HAND) == ModularBrush.matchHand(e.part(BrushPart.HAND)), "A hand cell's dice is off or .25–1, and the hand is renamed");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
