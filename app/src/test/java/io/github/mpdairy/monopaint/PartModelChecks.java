package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PartModelChecks {
    public static void main(String[] args) {
        tables(); fresh(); normalize(); swaps(); hand(); shown(); styles();
        System.out.println("PASS: six parts with the prototype's choices and dials in code order; fresh parts take their choice's defaults, normalizing clamps to the choice's range, swaps keep what means the same, hands are named for the preset they match, deferred choices aren't offered");
    }
    static String keys(BrushPart part) { List<String> k = new ArrayList<>(); for (PartChoice c : part.choices()) k.add(c.key); return String.join(",", k); }
    static String dials(PartChoice c) { List<String> k = new ArrayList<>(); for (PartDial d : c.dials) k.add(d.key); return String.join(" ", k); }
    static void tables() {
        check(Arrays.toString(BrushPart.values()).equals("[TIP, MEDIUM, CARRY, PAPER, LIFE, HAND]"), "Six parts in code order");
        check(keys(BrushPart.TIP).equals("round,chisel,flat,hake,split,multi,bristles,stipple,hatch,filbert,stamp,spiro,handhatch"), "Tip choices, Stipple, Hatch, the Filbert, the Stamp, the Spiro and Hand Hatch appended: " + keys(BrushPart.TIP));
        check(dials(PartChoice.SPIRO).equals("weight mode curve loops teeth wheel hole petals ratio phase fades flick") && PartChoice.SPIRO.label.equals("Spiro")
                && String.join(",", PartDial.SPIRO_MODE.choices).equals("Line,Ring,Oval,Bar") && PartDial.WHEEL.def == 4 && PartDial.HOLE.def == .8 && PartDial.PETALS.def == 5,
                "The Spiro's dials (mischief-lab-2.html): Size, Mode, Curve, Loops per cm, the toy's Ring Teeth, Wheel and Hole, Petals, Ratio, Phase, Fade Turns, then Flick");
        check(keys(BrushPart.MEDIUM).equals("ink,wash,cover,charcoal,graphite,resist,tone,crayon"), "Medium choices, Resist, Screentone and Crayon appended: " + keys(BrushPart.MEDIUM));
        check(keys(BrushPart.CARRY).equals("off,lift,drag,smudge,water,stayin,shape"), "Carry choices, Clear Water, Stay in Lines and Filled Shape appended: " + keys(BrushPart.CARRY));
        check(keys(BrushPart.PAPER).equals("plain,cold press,laid,felt,crumpled,canvas,watercolour,rough,unryu,library,kraft,old buff,blue fibre,fine speck"), "Paper choices");
        check(keys(BrushPart.LIFE).equals("none,wet,dry,float,halo,fade,lemon,wetpaint"), "Life choices, Halo, Fading, Lemon Juice and Wet appended: " + keys(BrushPart.LIFE));
        check(keys(BrushPart.HAND).equals("steady,wider,darker,both,brush,side,nib,quick,custom"), "Hand choices");
        check(dials(PartChoice.ROUND).equals("weight taperIn taperOut dry wobble soft flick"), "Round's dials");
        check(dials(PartChoice.CHISEL).equals("aspect angle weight taperIn taperOut dry wobble soft flick") && dials(PartChoice.FLAT_NIB).equals(dials(PartChoice.CHISEL)), "Chisel's and flat nib's dials");
        check(dials(PartChoice.HAKE).equals("weight headTurn angle dry soft flick"), "Hake's dials");
        check(dials(PartChoice.SPLIT_NIB).equals("gap aspect angle weight taperIn taperOut dry flick"), "Split nib's dials");
        check(dials(PartChoice.MULTI_LINE).equals("count reach weight taperIn taperOut dry wobble flick"), "Multi-line's dials");
        check(dials(PartChoice.BRISTLES).equals("hairs splay cohesion dryness weight load flick"), "Bristles' dials: the prototype's, then Ink load, then Flick");
        check(dials(PartChoice.FILBERT).equals(dials(PartChoice.CHISEL)) && Arrays.equals(PartChoice.FILBERT.range(PartDial.ASPECT), new double[]{.3, .55}), "The Filbert's dials are a chisel's (the oil paint spec)");
        check(dials(PartChoice.STIPPLE).equals("dot scatter jitter weight hold flick") && dials(PartChoice.HATCH).equals("pitch lineW hangle headTurn layers weight flick"), "Stipple's and Hatch's dials (newmods.js), Stipple's Build-up, then Flick last");
        check(dials(PartChoice.STAMP).equals("weight spacing headTurn inkFade flick") && PartChoice.STAMP.labelOf(PartDial.HEAD_TURN).equals("Turns")
                && String.join(",", PartChoice.STAMP.optionsOf(PartDial.HEAD_TURN)).equals("On,Off") && PartChoice.STAMP.defaultOf(PartDial.HEAD_TURN) == 0
                && PartChoice.STAMP.defaultOf(PartDial.STAMP_SPACING) == .12 && PartChoice.STAMP.defaultOf(PartDial.INK_FADE) == .22, "The Stamp's dials (mischief-lab-2.html): Size, Spacing, Turns (On), Ink Fade, then Flick");
        check(PartChoice.HATCH.defaultOf(PartDial.HEAD_TURN) == 1 && PartChoice.HATCH.labelOf(PartDial.HEAD_TURN).equals("Lines Follow") && ModularCode.ADDED.contains(PartDial.BUILD_UP), "Hatch's lines fixed by default, named Lines Follow; Build-up written only when on");
        String screen = " screen cell sangle mis gain uneven fling";
        check(dials(PartChoice.INK).equals("wet line pool splatter bleed bloom lines offset chaos erase blend" + screen), "Ink's dials, Erasable, Blend then Screen's last");
        check(dials(PartChoice.WASH).equals("wet pool bleed bloom backrun line erase blend" + screen), "Wash's dials, Erasable, Blend then Screen's last");
        check(dials(PartChoice.COVER).equals("cover impasto erase blend" + screen) && dials(PartChoice.CHARCOAL).equals("spread hold dust erase blend" + screen) && dials(PartChoice.GRAPHITE).equals("grade spread erase blend" + screen), "Cover's, charcoal's, graphite's dials, Erasable, Blend then Screen's last");
        check(dials(PartChoice.TONE).equals("pattern ttone cell sangle"), "Screentone's dials");
        check(String.join(",", PartChoice.INK.optionsOf(PartDial.M_BLEND)).equals("Darken,White,Behind,Invert") && String.join(",", PartChoice.COVER.optionsOf(PartDial.M_BLEND)).equals("Cover,White,Behind,Invert")
                && ModularCode.ADDED.contains(PartDial.M_BLEND) && PartDial.M_BLEND.label.equals("Blend Mode"), "Blend Mode: the medium's own way first (Opaque's Cover), then White, Behind, Invert; written only when not 0");
        check(dials(PartChoice.OFF).isEmpty() && dials(PartChoice.LIFT).equals("pick lay") && dials(PartChoice.SMUDGE).equals("pick lay load soften keep pload pmin")
                && dials(PartChoice.DRAG).equals("pick lay load keep pload pmin") && dials(PartChoice.CLEAR_WATER).equals("pick lay")
                && ModularCode.ADDED.contains(PartDial.SOFTEN) && PartDial.SOFTEN.def == 0 && ModularCode.ADDED.contains(PartDial.KEEPS) && PartDial.KEEPS.def == 0
                && ModularCode.ADDED.contains(PartDial.PLOAD) && PartDial.PLOAD.def == 0 && ModularCode.ADDED.contains(PartDial.PMIN),
                "Carry's dials, Smear's Soften then Keeps Paint, Paint Load and Starting Load last (Drag's too), written only when on; Clear Water's Pick up and Lay down");
        check(dials(PartChoice.NONE).isEmpty() && dials(PartChoice.FAST_WET).equals("window runs pool sink") && dials(PartChoice.DRYING).equals("drying settle takes plume runs drips pool sink blend")
                && dials(PartChoice.FLOAT).equals("drying spread drift") && dials(PartChoice.HALO).equals("drying reach growth") && dials(PartChoice.FADE).equals("fadeIn") && dials(PartChoice.LEMON).equals("dryIn") && dials(PartChoice.WET_PAINT).equals("stays runs") && ModularCode.ADDED.contains(PartDial.STAYS_WET) && PartChoice.WET_PAINT.index() == 7 && PartChoice.HALO.defaultOf(PartDial.DRYING) == .55,
                "Life's dials, gravity's last; Float's floating time, spread and drift; Halo's drying time (6.6 s), Reach and Growth");
        for (PartChoice c : BrushPart.HAND.choices()) check(dials(c).equals("ps pd pw ts td tw ss sd sw gain dir trange gs gd gw pdrive sdrive tdrive shift sorder sgreys sbar sany sany2 grad gto glen tshade") && ModularCode.ADDED.contains(PartDial.SIDE_SHADE) && PartDial.SIDE_SHADE.def == 0,
                "Every hand has the nine cells, gain, dir, Tilt range, page tilt's three, a Spiro's drives, Grey Shift's six, Gradient's three and Tilt Shading (written only when on)");
        for (BrushPart part : BrushPart.values()) for (int i = 0; i < part.choices().size(); i++)
            check(part.choices().get(i).index() == i && PartChoice.of(part, i) == part.choices().get(i) && part.choices().get(i).part == part, "Choice indexes: " + part + " " + i);
        check(PartChoice.of(BrushPart.TIP, 13) == null && PartChoice.of(BrushPart.TIP, -1) == null, "No choice past the list");
        check(PartChoice.PLAIN.texture() == BrushTexture.PLAIN && PartChoice.COLD_PRESS.texture() == BrushTexture.COLD_PRESS && PartChoice.LAID.texture() == BrushTexture.LAID
                && PartChoice.FELT.texture() == BrushTexture.FELT && PartChoice.CRUMPLED.texture() == BrushTexture.CRUMPLED && PartChoice.CANVAS.texture() == BrushTexture.CANVAS, "A paper is its texture");
        check(BrushPart.PAPER.choices().size() == BrushTexture.values().length && PartChoice.COTTON.texture() == BrushTexture.WATERCOLOUR && PartChoice.BUMPY.texture() == BrushTexture.ROUGH
                && PartChoice.LIBRARY.texture() == BrushTexture.LIBRARY && PartChoice.FINE_SPECK.texture() == BrushTexture.FINE_SPECK && PartChoice.COTTON.index() == 6 && PartChoice.FINE_SPECK.index() == 13,
                "The eight photo papers are Paper choices 6 to 13, each its texture");
        for (PartChoice c : BrushPart.PAPER.choices())
            check(c.defaultOf(PartDial.TOOTH) == (c == PartChoice.PLAIN ? 0 : .5) && ModularFeatures.offered(c) && ModularFeatures.seedOnly(c) == (c.index() >= 6) && c.label.length() <= 10,
                    c + ": Texture .5, offered in Brush Lab, never rolled or swapped by a variation if a photo paper, its name 10 characters at most");
        check(Arrays.equals(PartChoice.CHISEL.range(PartDial.ASPECT), new double[]{.3, .55}) && Arrays.equals(PartChoice.FLAT_NIB.range(PartDial.ASPECT), new double[]{.14, .28})
                && Arrays.equals(PartChoice.ROUND.range(PartDial.WEIGHT), new double[]{.5, 1.5}), "Ranges: a choice's own, else the dial's");
    }
    static void fresh() {
        ModularBrush.Part hake = new ModularBrush.Part(PartChoice.HAKE);
        check(hake.get(PartDial.ANGLE) == 75 && hake.get(PartDial.DRY) == .35 && hake.get(PartDial.SOFT) == .2 && hake.get(PartDial.WEIGHT) == 1 && hake.get(PartDial.HEAD_TURN) == 0, "A fresh hake");
        check(!hake.has(PartDial.TAPER_IN) && hake.get(PartDial.TAPER_IN) == 0 && hake.get(PartDial.TAPER_IN, -1) == -1, "An absent dial reads 0, or the fallback asked for");
        check(new ModularBrush.Part(PartChoice.CHISEL).get(PartDial.ASPECT) == .42 && new ModularBrush.Part(PartChoice.FLAT_NIB).get(PartDial.ASPECT) == .2
                && new ModularBrush.Part(PartChoice.WASH).get(PartDial.WET) == .85 && new ModularBrush.Part(PartChoice.WASH).get(PartDial.LINE) == 0
                && new ModularBrush.Part(PartChoice.PLAIN).get(PartDial.TOOTH) == 0 && new ModularBrush.Part(PartChoice.LAID).get(PartDial.TOOTH) == .5
                && new ModularBrush.Part(PartChoice.GRAPHITE).get(PartDial.SPREAD) == 0 && new ModularBrush.Part(PartChoice.DRAG).get(PartDial.LOAD) == .85, "Choice defaults");
        ModularBrush.Part nib = new ModularBrush.Part(PartChoice.TILT_NIB);
        check(nib.get(PartDial.PS) == .4 && nib.get(PartDial.TW) == .9 && nib.get(PartDial.PW) == .4 && nib.get(PartDial.TS) == 0 && nib.get(PartDial.GAIN) == 1 && nib.get(PartDial.DIR) == 0, "A fresh hand preset sets its cells");
        ModularBrush b = new ModularBrush("x", 5, ModularStyle.named("Fineliner"));
        for (BrushPart part : BrushPart.values()) check(b.choice(part) == part.choices().get(0), "A new brush holds each part's first choice");
        check(b.seed == 5 && b.name.equals("x") && b.style.name.equals("Fineliner"), "and its name, seed and style");
    }
    static void normalize() {
        ModularBrush.Part chisel = new ModularBrush.Part(PartChoice.CHISEL);
        chisel.set(PartDial.ASPECT, .2); chisel.set(PartDial.ANGLE, 99); chisel.set(PartDial.TAPER_IN, -1); chisel.set(PartDial.SOFT, Double.NaN);
        chisel.normalize();
        check(chisel.get(PartDial.ASPECT) == .3 && chisel.get(PartDial.ANGLE) == 70 && chisel.get(PartDial.TAPER_IN) == 0 && chisel.get(PartDial.SOFT) == .3, "Normalizing clamps to the choice's range and fills what isn't a number");
        ModularBrush.Part multi = new ModularBrush.Part(PartChoice.MULTI_LINE); multi.set(PartDial.COUNT, 3.6); multi.normalize();
        check(multi.get(PartDial.COUNT) == 4, "An integer dial rounds");
        ModularBrush.Part hand = new ModularBrush.Part(PartChoice.CUSTOM); hand.set(PartDial.DIR, 1.7); hand.normalize();
        check(hand.get(PartDial.DIR) == 1, "A choice dial rounds into its options");
    }
    static void swaps() {
        ModularBrush.Part chisel = new ModularBrush.Part(PartChoice.CHISEL);
        chisel.set(PartDial.TAPER_IN, .5); chisel.set(PartDial.ANGLE, 30); chisel.set(PartDial.ASPECT, .5);
        ModularBrush.Part split = ModularBrush.switchTo(chisel, PartChoice.SPLIT_NIB);
        check(split.choice == PartChoice.SPLIT_NIB && split.get(PartDial.TAPER_IN) == .5 && split.get(PartDial.ANGLE) == 30 && split.get(PartDial.ASPECT) == .5 && split.get(PartDial.GAP) == .55, "A swap keeps the tip's shared dials and an aspect inside the new range");
        check(ModularBrush.switchTo(chisel, PartChoice.FLAT_NIB).get(PartDial.ASPECT) == .2, "An aspect outside the new range starts at its default");
        check(ModularBrush.switchTo(new ModularBrush.Part(PartChoice.PLAIN), PartChoice.COLD_PRESS).get(PartDial.TOOTH) == .5, "Plain paper's tooth isn't kept");
        ModularBrush.Part cold = new ModularBrush.Part(PartChoice.COLD_PRESS); cold.set(PartDial.TOOTH, .8);
        check(ModularBrush.switchTo(cold, PartChoice.LAID).get(PartDial.TOOTH) == .8, "Another paper's tooth is");
        ModularBrush.Part ink = new ModularBrush.Part(PartChoice.INK); ink.set(PartDial.POOL, .4); ink.set(PartDial.WET, .6);
        ModularBrush.Part wash = ModularBrush.switchTo(ink, PartChoice.WASH);
        check(wash.get(PartDial.POOL) == .4 && wash.get(PartDial.WET) == .85, "A medium keeps pooling, not wet");
        ModularBrush.Part brush = new ModularBrush.Part(PartChoice.BRUSH_HAND); brush.set(PartDial.GAIN, 1.3);
        ModularBrush.Part custom = ModularBrush.switchTo(brush, PartChoice.CUSTOM);
        check(custom.get(PartDial.PS) == .9 && custom.get(PartDial.SS) == .4 && custom.get(PartDial.GAIN) == 1.3, "Custom keeps every cell");
        ModularBrush.Part wider = ModularBrush.switchTo(brush, PartChoice.PRESS_WIDER);
        check(wider.get(PartDial.PS) == .8 && wider.get(PartDial.PD) == 0 && wider.get(PartDial.GAIN) == 1.3, "A preset takes its own cells, keeping the response");
        ModularBrush.Part dry = new ModularBrush.Part(PartChoice.DRYING); dry.set(PartDial.DRYING, .9);
        check(ModularBrush.switchTo(dry, PartChoice.FAST_WET).get(PartDial.WINDOW) == 6, "Life keeps drying time");
        dry.set(PartDial.RUNS, .4); dry.set(PartDial.DRIPS, .5);
        ModularBrush.Part quick = ModularBrush.switchTo(dry, PartChoice.FAST_WET);
        check(quick.get(PartDial.RUNS) == .4 && !quick.has(PartDial.DRIPS), "and Runs, Pool and Sink, which Quick has too");
    }
    static void hand() {
        ModularBrush b = new ModularBrush("x", 1, ModularStyle.named("Anything goes"));
        b.set(BrushPart.HAND, new ModularBrush.Part(PartChoice.CUSTOM));
        b.part(BrushPart.HAND).set(PartDial.PS, .8); b.part(BrushPart.HAND).set(PartDial.GAIN, 1.5); b.part(BrushPart.HAND).set(PartDial.TD, .2);
        check(b.handCell(PartDial.PS) == 1 && Math.abs(b.handCell(PartDial.TD) - .3) < 1e-12, "A cell in use is its amount times the response, at most 1");
        check(ModularBrush.matchHand(new ModularBrush.Part(PartChoice.TILT_NIB)) == PartChoice.TILT_NIB, "A preset matches itself");
        ModularBrush.Part c = new ModularBrush.Part(PartChoice.CUSTOM); c.set(PartDial.PS, .4); c.set(PartDial.TW, .9); c.set(PartDial.PW, .4);
        check(ModularBrush.matchHand(c) == PartChoice.TILT_NIB, "Cells that match a preset are named for it");
        c.set(PartDial.TW, .8);
        check(ModularBrush.matchHand(c) == PartChoice.CUSTOM, "otherwise Custom");
        check(ModularBrush.matchHand(new ModularBrush.Part(PartChoice.CUSTOM)) == PartChoice.STEADY, "Every cell off is Steady");
    }
    static void shown() {
        check(PartDial.GRADE.shown(.4).equals("HB") && PartDial.GRADE.shown(0).equals("6H") && PartDial.GRADE.shown(1).equals("8B"), "Grades");
        check(PartDial.WINDOW.shown(6).equals("6.0 mm") && PartDial.DRYING.shown(.35).equals("3.1 s") && PartDial.WEIGHT.shown(1).equals("×1.00")
                && PartDial.ANGLE.shown(40.4).equals("40°") && PartDial.TAPER_IN.shown(0).equals("off") && PartDial.LINES.shown(2).equals("Squiggle")
                && PartDial.COUNT.shown(3).equals("3") && PartDial.GAIN.shown(1).equals("×1.00") && PartDial.SOFT.shown(.3).equals("0.30")
                && PartDial.DIR.shown(1).equals("Thicker"), "Dials show as the prototype's fmtDial");
        check(PartDial.wetMs(.35) == 3108 && PartDial.wetMs(0) == 750 && PartDial.wetMs(1) == 20000, "Drying time spans 0.75 to 20 s");
        check(PartDial.wetMs(1, true) == 60000 && PartDial.wetMs(.35, false) == 3108 && Math.abs(PartDial.wetMs(.35, true) - 3108) < 100 && PartDial.wetMs(.82, true) > 25000,
                "With gravity, to a minute (the same to within 0.1 s at the 3 s default)");
        ModularBrush.Part slow = new ModularBrush.Part(PartChoice.DRYING); slow.set(PartDial.DRYING, 1);
        check(PartDial.DRYING.shown(1, slow).equals("20.0 s") && !ModularBrush.runs(slow), "A Slow brush without gravity shows 20 s at the top");
        slow.set(PartDial.SINK, .2);
        check(PartDial.DRYING.shown(1, slow).equals("60.0 s") && ModularBrush.runs(slow), "one with gravity a minute");
        check(PartChoice.WASH.labelOf(PartDial.POOL).equals("Dark Edge") && PartChoice.INK.labelOf(PartDial.POOL).equals("Pooling") && PartChoice.HAKE.labelOf(PartDial.DRY).equals("Dry Brush"), "Labels a choice renames");
        // One term per concept (docs/TEXT-STYLE.md): every display label is free of arrows and code keys stay as they were.
        for (PartChoice c : PartChoice.values()) for (PartDial d : c.dials) check(!c.labelOf(d).contains("→") && !c.labelOf(d).contains("↔") && !c.labelOf(d).contains("("), "No symbols in " + c + "'s " + d);
        for (PartChoice c : PartChoice.values()) check(!c.label.contains("→"), "No arrow in " + c.label);
        check(PartChoice.MULTI_LINE.label.equals("Rake") && PartChoice.MULTI_LINE.key.equals("multi") && PartChoice.PLAIN.label.equals("Smooth") && PartChoice.PLAIN.key.equals("plain")
                && PartChoice.COVER.label.equals("Opaque") && PartChoice.COVER.key.equals("cover"), "Display labels change; keys don't");
        check(ModularStyle.named("Watercolor").label.equals("Watercolour") && ModularStyle.named("Pencil").label.equals("Pencil"), "Style labels in Canadian spelling, names unchanged");
    }
    static void styles() {
        String[] names = {"Fineliner", "Brush pen", "Dip pen", "Marker", "Pencil", "Charcoal", "Watercolor", "Ink wash", "Gouache", "Oils", "Pastels", "Dry brush", "Hake", "Anything goes", "Print"};
        check(ModularStyle.all().size() == names.length, "Fifteen styles");
        check(ModularStyle.shown().size() == names.length && ModularStyle.shown().get(13) == ModularStyle.named("Print") && ModularStyle.shown().get(14).wild, "Shown with Anything goes last");
        for (int i = 0; i < names.length; i++) check(ModularStyle.at(i).name.equals(names[i]) && ModularStyle.at(i).index == i && ModularStyle.named(names[i]) == ModularStyle.at(i), "Style " + i);
        check(ModularStyle.named("Wild") == ModularStyle.at(13) && ModularStyle.named("Wildcard") == ModularStyle.at(13) && ModularStyle.at(13).wild && !ModularStyle.at(0).wild && ModularStyle.named("Nope") == null, "Wild is Anything goes");
        check(ModularFeatures.offered(PartChoice.BRISTLES) && ModularFeatures.offered(PartChoice.LIFT) && ModularFeatures.offered(PartChoice.DRAG) && ModularFeatures.offered(PartChoice.SMUDGE)
                && ModularFeatures.offered(PartChoice.HAKE) && ModularFeatures.offered(PartChoice.OFF) && ModularFeatures.offered(PartChoice.DRYING), "Smudge's choices and Bristles are offered");
        for (PartChoice c : PartChoice.values()) check(ModularFeatures.late(c) == (c == PartChoice.BRISTLES || c == PartChoice.LIFT || c == PartChoice.DRAG || c == PartChoice.SMUDGE), "Bristles and Smudge's choices are rolled after every part: " + c);
        for (PartChoice c : PartChoice.values()) check(ModularFeatures.seedOnly(c) == (c == PartChoice.FLOAT || c == PartChoice.STIPPLE || c == PartChoice.HATCH || c == PartChoice.RESIST || c == PartChoice.HALO || c == PartChoice.TONE || c == PartChoice.CLEAR_WATER || c == PartChoice.FILBERT || c == PartChoice.FADE || c == PartChoice.LEMON || c == PartChoice.STAMP || c == PartChoice.CRAYON || c == PartChoice.SPIRO || c == PartChoice.STAY_IN || c == PartChoice.SHAPE || c == PartChoice.HAND_HATCH || c == PartChoice.WET_PAINT || c.part == BrushPart.PAPER && c.index() >= 6) && ModularFeatures.varies(c) == (ModularFeatures.offered(c) && !ModularFeatures.seedOnly(c)),
                "Float, Stipple, Hatch, Resist, Mold, Screentone, Clear Water and the Filbert only from a roll's seed, the Stamp, Crayon, the Spiro, Stay in Lines and Hand Hatch from none, never a variation's swap: " + c);
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
