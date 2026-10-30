package io.github.mpdairy.monopaint;

import java.util.Random;

/**
 * The oil paint spec (docs/archive/superpowers/specs/2026-10-19-oil-paint-design.md): Smudge's Paint Load and Starting Load
 * (MonoPaint's oil paint mode, {@link LoadedPaint}), the Filbert tip and its crescent, the Oil paint Basic tool, Oils'
 * rolls, the rub that loads a brush, and their codes and words.
 */
public class OilChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        codes(); rolls(); words();
        arithmetic(); runsOut(); wider(); emptyPaper(); starts(); rubbed(); otherStampers();
        crescent(); quick(); off();
        timings();
        System.out.println("PASS: Paint Load and Starting Load are Drag's and Smear's last dials, written only when on, and the Filbert is Tip choice 9, every older code byte for byte; Oils rolls them rarely from the seed, every other roll as before; "
                + "Oil paint lays its grey solidly, runs out along the stroke (sooner pressed wider), trades it for what it drags and then only smudges, leaving nothing on paper; every stroke starts at its Starting Load, a rub loads more (pure arithmetic), what is left carries over; "
                + "a Filbert's first touch is a crescent hollowed the way it moves, its oval without Paint Load; quick ends as live; a brush without Paint Load spends nothing; " + timing);
    }

    static final int W = 640, H = 420;
    /** The dark wash band's grey. */
    static final int BAND = 50;

    static ModularBrush oil() { return FloatChecks.preset("Oil paint"); }
    /** A page with a dark wash band down its middle (x 240 to 400). */
    static ToneDocument darkBand(int w, int h) {
        ToneDocument d = InkWashLayers.blank(w, h);
        // Opaque paint, on its plane, where the oils drag it (kind layers: a brush with paint of its own moves its own kind).
        CarrySmudgeChecks.lay(d, InkWashLayers.WASH, ToneDocument.KIND_PAINT | ToneDocument.KIND_OPAQUE, (x, y) -> x >= 240 && x < 400 ? BAND : 255);
        return d;
    }
    /** A stroke as the drawing screen starts one ({@link InkWash#stroke}): {@code holder}'s, quick by the rule. */
    static void stroke(ToneDocument d, WatercolorSession s, String holder, ModularBrush b, double size, double darkness, double[][] path) {
        s.holding(holder);
        s.settleBefore(d, b);
        s.dry("stroke");
        feed(QuickStroke.start(d, b, size, darkness, s), path);
    }
    /** {@code path}'s samples ({x, y, pressure travel, ms, tilt x, tilt y}) into {@code s}, then its lift. */
    static void feed(DrawingStroke s, double[][] path) {
        for (int i = 0; i < path.length; i++) {
            double[] p = path[i];
            s.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), p.length > 4 ? (float) p[4] : 0, p.length > 5 ? (float) p[5] : 0, (long) p[3]);
            if (i % 3 == 2) s.present();
        }
        s.present();
        s.finish();
    }
    static double[][] line(double x0, double y0, double x1, double y1, double p) { return FloatChecks.line(x0, y0, x1, y1, p); }
    /** {@link #line} with the pen leaning {@code tiltX, tiltY} degrees. */
    static double[][] line(double x0, double y0, double x1, double y1, double p, double tiltX, double tiltY) {
        double[][] o = line(x0, y0, x1, y1, p);
        for (int i = 0; i < o.length; i++) o[i] = new double[]{o[i][0], o[i][1], o[i][2], o[i][3], tiltX, tiltY};
        return o;
    }
    /** A tap at {@code x, y}, the pen leaning {@code tiltX, tiltY}. */
    static double[][] tap(double x, double y, double tiltX, double tiltY) { return new double[][]{{x, y, .8, 0, tiltX, tiltY}}; }
    static double mean(ToneDocument d, int x0, int x1, int y0, int y1) { return CarrySmudgeChecks.mean(d, x0, x1, y0, y1); }
    /** How far along a horizontal stroke at row {@code y} (from {@code x0}) the page is still darker than {@code under} by 20: where its paint ends. */
    static int reach(ToneDocument d, int x0, int x1, int y, int under) {
        int last = x0;
        for (int x = x0; x <= x1; x++) if (d.compositeTone(x, y) < under - 20) last = x;
        return last - x0;
    }

    // ---- codes, rolls, words ----

    static void codes() {
        for (ToolPresets.Preset p : ToolPresets.all()) check(ModularCode.encode(ModularCode.decode(p.code)).equals(p.code) || HandHatchChecks.handHatch(p.code), p.name + "'s code reads and writes byte for byte (Hand Hatch's reads as Hatch)");
        // A Drag and a Smear from before: Paint Load reads off, Starting Load its default, written as they were.
        for (String old : new String[]{CarrySmudgeChecks.GOLDENS[5][1].toString(), "stump.M50i00000000yat_3eqlj00we_3p6ll00_1nl_1jz_8pthh00x9is00000000nz0.51j8ud", "dirty-brush.M60i000000000at_1ulat00770000ee_3qzi00000we_000_1jz_0000000000000000000nz0.20j"}) {
            ModularBrush b = ModularCode.decode(old);
            ModularBrush.Part c = b.part(BrushPart.CARRY);
            check(c.get(PartDial.PLOAD) == 0 && c.get(PartDial.PMIN) == PartDial.PMIN.def && ModularCode.encode(b).equals(old) && ResolvedBrush.of(b, 60, 1).loaded == null, "an older smudge code reads Paint Load off and writes as it was: " + old);
        }
        ModularBrush oil = oil();
        String code = ModularCode.encode(oil);
        ModularBrush.Part c = oil.part(BrushPart.CARRY);
        check(oil.choice(BrushPart.TIP) == PartChoice.FILBERT && PartChoice.FILBERT.index() == 9 && code.contains(".M99"), "the Filbert is Tip choice 9: " + code);
        check(PartChoice.of(BrushPart.TIP, 10) == PartChoice.STAMP && PartChoice.of(BrushPart.TIP, 11) == PartChoice.SPIRO && PartChoice.of(BrushPart.TIP, 12) == PartChoice.HAND_HATCH && PartChoice.of(BrushPart.TIP, 13) == null && ModularCode.decode(code.replace(".M99", ".M9d")) == null, "the Stamp, the Spiro and Hand Hatch after the Filbert, no Tip choice past them");
        check(oil.choice(BrushPart.CARRY) == PartChoice.DRAG && Math.abs(c.get(PartDial.PLOAD) - .55) < 1e-3 && c.get(PartDial.PMIN) == PartDial.PMIN.def, "Oil paint is a Drag with Paint Load .55 at the default Starting Load");
        // Paint Load on is written after Keeps Paint; a Starting Load set too after it; both round-trip.
        c.set(PartDial.PMIN, .1);
        ModularBrush back = ModularCode.decode(ModularCode.encode(oil));
        check(back.part(BrushPart.CARRY).sameAs(oil.part(BrushPart.CARRY)) || Math.abs(back.part(BrushPart.CARRY).get(PartDial.PMIN) - .1) < 1e-3, "Starting Load round-trips");
        check(ModularCode.encode(back).equals(ModularCode.encode(oil)), "Paint Load and Starting Load write back byte for byte");
        // Off with only its Starting Load moved: still read as off.
        ModularBrush smear = FloatChecks.preset("Stump");
        smear.part(BrushPart.CARRY).set(PartDial.PMIN, .5);
        check(ResolvedBrush.of(ModularCode.decode(ModularCode.encode(smear)), 60, 1).loaded == null, "Starting Load alone turns nothing on");
        check(BrushPart.CARRY.keeps().contains(PartDial.PLOAD) && ModularBrush.switchTo(oil().part(BrushPart.CARRY), PartChoice.SMUDGE).get(PartDial.PLOAD) == oil().part(BrushPart.CARRY).get(PartDial.PLOAD), "Drag swapped for Smear keeps its Paint Load");
        check(ModularBrush.switchTo(oil().part(BrushPart.TIP), PartChoice.ROUND).choice == PartChoice.ROUND && ModularBrush.switchTo(new ModularBrush.Part(PartChoice.ROUND), PartChoice.FILBERT).get(PartDial.ASPECT) == .5, "a fresh Filbert is .5 wide");
    }
    static void rolls() {
        int filberts = 0, loads = 0, oils = 0, oilDrags = 0, oilTips = 0;
        for (ModularStyle s : ModularStyle.all()) for (int i = 0; i < 300; i++) {
            ModularBrush b = s.roll(new Random(4400L * (s.index + 1) + i)), without;
            ModularStyle.oilRolls = false;
            try { without = s.roll(new Random(4400L * (s.index + 1) + i)); } finally { ModularStyle.oilRolls = true; }
            boolean filbert = b.choice(BrushPart.TIP) == PartChoice.FILBERT, load = b.part(BrushPart.CARRY).get(PartDial.PLOAD, 0) > 0;
            if (filbert) { filberts++; check(s.filbertOdds() > 0, s.name + " rolls a Filbert only with odds"); }
            if (load) { loads++; check(s.paintLoadOdds() > 0 && b.part(BrushPart.CARRY).get(PartDial.PLOAD) >= .35 - 1e-9 && b.part(BrushPart.CARRY).get(PartDial.PLOAD) <= .75 + 1e-9, s.name + " rolls Paint Load only with odds, .35 to .75"); }
            if (s.name.equals("Oils")) {
                oils++;
                PartChoice t = without.choice(BrushPart.TIP);
                if (t == PartChoice.ROUND || t == PartChoice.FLAT_NIB) oilTips++;
                if (without.part(BrushPart.CARRY).has(PartDial.PLOAD)) oilDrags++;
            }
            // Every other part as rolled without them; a tip or smudge too when it didn't change.
            for (BrushPart part : BrushPart.values()) {
                if (part == BrushPart.TIP && filbert) continue;
                if (part == BrushPart.CARRY && load) { check(b.part(part).choice == without.part(part).choice, "Paint Load keeps the smudge rolled"); continue; }
                check(b.part(part).sameAs(without.part(part)), s.name + " " + part + " rolls as before");
            }
            check(b.seed == without.seed, "the seed as before");
        }
        // Oils one in three of its rounds and flat nibs, one in three of its Drags.
        check(oils == 300 && Math.abs(filberts - oilTips / 3.0) <= 4 * Math.sqrt(oilTips * 2 / 9.0) + 1, "Oils rolls a Filbert about one in three: " + filberts + " of " + oilTips);
        check(Math.abs(loads - oilDrags / 3.0) <= 4 * Math.sqrt(oilDrags * 2 / 9.0) + 1, "Oils rolls Paint Load about one in three: " + loads + " of " + oilDrags);
        // Anything goes, which reaches every choice, rolls a Filbert one round or flat nib in 30, and never Paint Load.
        int wildTips = 0, wildFilberts = 0;
        for (int i = 0; i < 3000; i++) {
            ModularBrush b = ModularStyle.named("Anything goes").roll(new Random(77000L + i)), without;
            ModularStyle.oilRolls = false;
            try { without = ModularStyle.named("Anything goes").roll(new Random(77000L + i)); } finally { ModularStyle.oilRolls = true; }
            if (without.choice(BrushPart.TIP) == PartChoice.ROUND || without.choice(BrushPart.TIP) == PartChoice.FLAT_NIB) wildTips++;
            if (b.choice(BrushPart.TIP) == PartChoice.FILBERT) wildFilberts++;
            check(b.part(BrushPart.CARRY).get(PartDial.PLOAD, 0) == 0, "Anything goes never rolls Paint Load");
        }
        check(wildFilberts > 0 && Math.abs(wildFilberts - wildTips / 30.0) <= 4 * Math.sqrt(wildTips / 30.0) + 1, "Anything goes rolls a Filbert about one in 30: " + wildFilberts + " of " + wildTips);
        for (int i = 0; i < 400; i++) {
            ModularBrush v = ModularStyle.named("Oils").rollOnePart(oil(), BrushPart.CARRY, null, new Random(i));
            check(v.part(BrushPart.CARRY).get(PartDial.PLOAD, 0) == 0 || v.part(BrushPart.CARRY).choice == PartChoice.DRAG || v.part(BrushPart.CARRY).choice == PartChoice.SMUDGE, "a smudge's die leaves Paint Load off");
        }
        check(!ModularFeatures.varies(PartChoice.FILBERT) && ModularFeatures.offered(PartChoice.FILBERT), "a variation never swaps to the Filbert; Brush Lab offers it");
        // A variation never moves Paint Load or Starting Load.
        for (ModularVariations.Made m : ModularVariations.make(oil(), new ModularLocks(), new Random(5))) {
            ModularBrush.Part c = m.brush.part(BrushPart.CARRY);
            if (c.choice == PartChoice.DRAG) check(c.get(PartDial.PLOAD) == oil().part(BrushPart.CARRY).get(PartDial.PLOAD) && c.get(PartDial.PMIN) == PartDial.PMIN.def, "a variation leaves Paint Load as it is");
        }
    }
    static void words() {
        String words = ModularWords.plainWords(oil());
        check(words.startsWith("Oil paint, filbert, on canvas.") && words.contains("Runs out of paint, then only smudges. Rub a grey to load."), "Oil paint says it runs out and how to load it: " + words);
        check(String.join(", ", ModularWords.caption(oil())).equals("Oils, filbert, runs out"), "its caption: " + ModularWords.caption(oil()));
        check(PartChoice.FILBERT.label.equals("Filbert") && PartDial.PLOAD.label.equals("Paint Load") && PartDial.PMIN.label.equals("Starting Load") && PartDial.PLOAD.key.equals("pload") && PartDial.PMIN.key.equals("pmin"), "Title Case labels, their keys");
        check(PartDial.PLOAD.shown(0).equals("off") && ModularWords.inside(BrushPart.CARRY).contains("load"), "Paint Load off says so; the Smudge module holds load");
        ModularBrush noLoad = oil();
        noLoad.part(BrushPart.CARRY).set(PartDial.PLOAD, 0);
        check(ModularWords.plainWords(noLoad).contains("Drags wet paint.") && !ModularWords.plainWords(noLoad).contains("Runs out"), "without Paint Load it drags as before");
        check(java.util.Arrays.asList("oily", "loaded").contains(BrushNames.plainWords(oil()).get(1)), "a brush that runs out is named oily or loaded: " + BrushNames.plainWords(oil()));
        ModularBrush round = noLoad.copy();
        round.set(BrushPart.TIP, ModularBrush.switchTo(round.part(BrushPart.TIP), PartChoice.FILBERT));
        check(BrushNames.plainWords(round).get(2).equals("filbert"), "a Filbert is named for it: " + BrushNames.plainWords(round));
    }

    // ---- the paint ----

    static void arithmetic() {
        LoadedPaint p = ResolvedBrush.of(oil(), 60, 1).loaded;
        check(p != null && Math.abs(p.full - LoadedPaint.FULL_PX * .55 * .55) < 2 && p.start == PartDial.PMIN.def, "a full load at 60 px lays " + (p == null ? 0 : Math.round(p.full)) + " px");
        check(p.amount(.25, 0) == 1 && p.amount(.25, p.full * .25) == 0 && Math.abs(p.amount(.25, p.full * .125) - .5) < 1e-9 && p.amount(0, 0) == 0, "its paint holds, then falls smoothly to none where its share is spent; an empty brush none");
        check(p.left(.25, p.full * .1) > .149 && p.left(.25, p.full * .1) < .151 && p.left(.25, p.full) == 0, "the share left");
        check(p.startShare(0) == .25 && p.startShare(.6) == .6 && p.startShare(3) == 1, "a stroke starts with what is held, at least the Starting Load, at most full");
        // A rub: from at least the Starting Load, twice as far as the pen rubbed, at most full.
        check(Math.abs(p.rubbed(0, 100) - (.25 + 200 / p.full)) < 1e-9 && Math.abs(p.rubbed(.5, 100) - (.5 + 200 / p.full)) < 1e-9 && p.rubbed(.9, 1e6) == 1 && p.rubbed(0, -5) == .25, "a rub adds about twice as far as the pen rubbed, up to full");
        check(!LoadedPaint.rub(5, 1) && LoadedPaint.rub(7, 1) && !LoadedPaint.rub(11, 2) && LoadedPaint.rub(13, 2), "a rub is the pen moving past 6 dp; less is a tap");
        // A broader brush holds more, by the root of its width.
        LoadedPaint big = ResolvedBrush.of(oil(), 240, 1).loaded, small = ResolvedBrush.of(oil(), 15, 1).loaded;
        check(Math.abs(big.full / p.full - 2) < 1e-3 && Math.abs(small.full / p.full - .5) < 1e-3, "a brush four times as wide holds twice the paint, a quarter as wide half");
    }
    /** A loaded stroke over clean paper: solid at its start, nothing at its end. */
    static void runsOut() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        s.held.keepLoad("A", .6);
        stroke(d, s, "A", oil(), 40, 1, line(20, 100, 620, 100, .8));
        int end = reach(d, 20, 620, 100, 255);
        double length = .6 * ResolvedBrush.of(oil(), 40, 1).loaded.full;
        check(mean(d, 30, 60, 95, 105) < 60, "a loaded brush lays its grey solidly at first: " + mean(d, 30, 60, 95, 105));
        check(end > .4 * length && end < 1.05 * length + 40, "and runs out over its load's length: " + end + " of " + Math.round(length));
        check(mean(d, 600, 620, 90, 110) > 250, "past it, on paper, nothing");
        check(s.held.load("A") == 0, "spent, the brush holds no more than its Starting Load");
        // The paint fades: darker near the start than near where it runs out.
        double a = mean(d, 20 + end / 4, 30 + end / 4, 95, 105), b = mean(d, 20 + end * 3 / 4, 30 + end * 3 / 4, 95, 105);
        check(a < b, "it lays less as it runs out: " + a + " then " + b);
    }
    /** Pressed harder, wider, it runs out sooner. */
    static void wider() {
        int[] ends = new int[2];
        for (int k = 0; k < 2; k++) {
            ToneDocument d = InkWashLayers.blank(W, H);
            WatercolorSession s = new WatercolorSession();
            s.held.keepLoad("A", .5);
            stroke(d, s, "A", oil(), 40, 1, line(20, 100, 620, 100, k == 0 ? .35 : 1));
            ends[k] = reach(d, 20, 620, 100, 255);
        }
        check(ends[1] < ends[0] * .9, "a wider stroke spends its paint faster: " + ends[0] + " px light, " + ends[1] + " px pressed");
    }
    /** Empty, over paper, it lays nothing; over a dark band it only drags it on. */
    static void emptyPaper() {
        ModularBrush empty = oil();
        empty.part(BrushPart.CARRY).set(PartDial.PMIN, 0);
        ToneDocument d = InkWashLayers.blank(W, H);
        byte[][] before = CarrySmudgeChecks.all(d);
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, "A", empty, 60, 1, line(20, 100, 620, 100, .8));
        check(ModularStrokeChecks.same(before, CarrySmudgeChecks.all(d)), "an empty brush lays nothing on clean paper");
        ToneDocument e = darkBand(W, H);
        stroke(e, s, "A", empty, 60, 1, line(100, 200, 600, 200, .8));
        check(mean(e, 420, 470, 190, 210) < 250 && mean(e, 150, 200, 190, 210) > 252, "it drags the band's paint on past it, and lays none before it: " + mean(e, 420, 470, 190, 210));
    }
    /** Every stroke starts with its Starting Load; what a rub loads more lasts until spent, then the Starting Load again. */
    static void starts() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        int[] ends = new int[4];
        // Two strokes at the Starting Load lay as far as each other.
        stroke(d, s, "A", oil(), 40, 1, line(20, 50, 620, 50, .8)); ends[0] = reach(d, 20, 620, 50, 255);
        stroke(d, s, "A", oil(), 40, 1, line(20, 120, 620, 120, .8)); ends[1] = reach(d, 20, 620, 120, 255);
        check(ends[0] > 20 && Math.abs(ends[0] - ends[1]) <= 2, "every stroke starts with its Starting Load: " + ends[0] + ", " + ends[1] + " px");
        double start = ResolvedBrush.of(oil(), 40, 1).loaded.start * ResolvedBrush.of(oil(), 40, 1).loaded.full;
        check(ends[0] > .4 * start && ends[0] < start + 40, "as far as a quarter load lays: " + ends[0] + " of " + Math.round(start));
        // A rub loads it: half its load is left after a short stroke, and starts the next.
        s.held.keepLoad("A", ResolvedBrush.of(oil(), 40, 1).loaded.rubbed(0, 400));
        double rubbed = s.held.load("A");
        stroke(d, s, "A", oil(), 40, 1, line(20, 190, 120, 190, .8));
        double left = s.held.load("A");
        check(left > .25 && left < rubbed, "a short stroke leaves some of a rub's load: " + rubbed + " then " + left);
        stroke(d, s, "A", oil(), 40, 1, line(20, 260, 620, 260, .8)); ends[2] = reach(d, 20, 620, 260, 255);
        check(ends[2] > ends[0] + 30 && s.held.load("A") == 0, "the next lays further, and spends it: " + ends[2] + " px");
        stroke(d, s, "A", oil(), 40, 1, line(20, 330, 620, 330, .8)); ends[3] = reach(d, 20, 620, 330, 255);
        check(Math.abs(ends[3] - ends[0]) <= 2, "then the Starting Load again: " + ends[3] + " px");
        // Each holder's paint is its own.
        s.held.keepLoad("B", 1);
        check(s.held.load("A") == 0 && s.held.load("B") == 1, "each holder's load is its own");
    }
    /** The rub the drawing screen's grey faces take ({@link LoadedPaint#rub}): only a brush with Paint Load loads. */
    static void rubbed() {
        WatercolorSession s = new WatercolorSession();
        Brush oil = Brush.of(oil()), ink = Brush.of(FloatChecks.preset("Steady line"));
        ToolSwatches.Look look = new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, 40, 1);
        HeldPaint h = s.held;
        check(LoadedPaint.rub(h, "A", oil, look, 60) && h.load("A") > .25, "a rub loads a brush with Paint Load");
        double once = h.load("A");
        check(LoadedPaint.rub(h, "A", oil, look, 60) && h.load("A") > once, "more the farther the pen rubs");
        for (int i = 0; i < 40; i++) LoadedPaint.rub(h, "A", oil, look, 60);
        check(h.load("A") == 1, "up to full");
        check(!LoadedPaint.rub(h, "B", ink, look, 300) && h.load("B") == 0 && !LoadedPaint.rub(h, "C", null, look, 300), "a brush without it, or the eraser, doesn't load");
        check(Math.abs(LoadedPaint.shown(h, "A", oil, look) - 1) < 1e-9 && Math.abs(LoadedPaint.shown(h, "Z", oil, look) - .25) < 1e-9 && Double.isNaN(LoadedPaint.shown(h, "B", ink, look)), "the bar shows the load the next stroke starts with");
    }
    /** Paint Load on an ink line, a hake and bristles: each runs out too. */
    static void otherStampers() {
        for (PartChoice tip : new PartChoice[]{PartChoice.ROUND, PartChoice.HAKE, PartChoice.BRISTLES, PartChoice.FILBERT}) {
            for (PartChoice medium : new PartChoice[]{PartChoice.INK, PartChoice.COVER}) {
                ModularBrush b = oil();
                b.set(BrushPart.TIP, ModularBrush.switchTo(b.part(BrushPart.TIP), tip));
                if (medium == PartChoice.INK) b.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.INK));
                b.part(BrushPart.CARRY).set(PartDial.PMIN, .1);
                ToneDocument d = InkWashLayers.blank(W, H);
                WatercolorSession s = new WatercolorSession();
                stroke(d, s, "A", b, 30, 1, line(20, 100, 620, 100, .8));
                check(mean(d, 30, 50, 90, 110) < 235 && mean(d, 560, 620, 80, 120) > 254, tip + " " + medium + " lays at first and runs out: " + mean(d, 30, 50, 90, 110) + ", " + mean(d, 560, 620, 80, 120));
            }
        }
    }

    // ---- the Filbert ----

    /** A tap, and a short stroke: a crescent hollowed the way it moves with Paint Load, the whole oval without. */
    static void crescent() {
        ModularBrush on = oil(), off = oil();
        on.part(BrushPart.CARRY).set(PartDial.PMIN, 1);
        off.part(BrushPart.CARRY).set(PartDial.PLOAD, 0);
        ResolvedBrush r = ResolvedBrush.of(on, 80, 1);
        check(r.body.filbert && r.body.crescent && !ResolvedBrush.of(off, 80, 1).body.crescent && ResolvedBrush.of(off, 80, 1).body.filbert, "a Filbert's dabs are crescents only with Paint Load");
        double[] covered = new double[2];
        ToneDocument[] taps = new ToneDocument[2];
        for (int k = 0; k < 2; k++) {
            ToneDocument d = InkWashLayers.blank(240, 240);
            stroke(d, new WatercolorSession(), "A", k == 0 ? on : off, 80, 1, tap(120, 120, 40, 0));
            int n = 0;
            for (int y = 0; y < 240; y++) for (int x = 0; x < 240; x++) if (d.compositeTone(x, y) < 128) n++;
            covered[k] = n;
            taps[k] = d;
        }
        // Leaning right, the oval stands upright (its broad side across the lean); a tap moves right, so its hollow is on the right.
        check(covered[0] > 0 && covered[0] < .6 * covered[1], "a first touch with Paint Load is a sliver: " + covered[0] + " px of the oval's " + covered[1]);
        check(mean(taps[1], 116, 124, 100, 140) < 80 && mean(taps[0], 116, 124, 100, 140) > 200 && mean(taps[0], 107, 110, 105, 135) < 120, "the oval whole without it; with it, hollow at its middle and solid behind: " + mean(taps[1], 116, 124, 100, 140) + ", " + mean(taps[0], 116, 124, 100, 140) + ", " + mean(taps[0], 107, 110, 105, 135));
        check(mean(taps[1], 115, 125, 92, 98) < 128 && mean(taps[1], 140, 160, 110, 130) > 250, "upright: tall, not wide, leaning right");
        // A stroke right: its start rounded as the oval, its end hollowed.
        ToneDocument d = InkWashLayers.blank(320, 240);
        stroke(d, new WatercolorSession(), "A", on, 80, 1, line(80, 120, 220, 120, .8, 40, 0));
        int lastAtMiddle = 0, lastAtTip = 0;
        for (int x = 0; x < 320; x++) { if (d.compositeTone(x, 120) < 128) lastAtMiddle = x; if (d.compositeTone(x, 95) < 128) lastAtTip = x; }
        check(lastAtTip > lastAtMiddle, "its end hollowed the way it moved: the paint reaches " + lastAtTip + " near its tip, " + lastAtMiddle + " at its middle");
        // Without a tilt reading the oval lies at its Nib Angle.
        ResolvedBrush.Body b = ResolvedBrush.of(off, 80, 1).body;
        BodyStamper.Shape sh = new BodyStamper.Shape();
        BodyStamper.shape(sh, b, 80, 1, 0, 0, 0, Double.NaN);
        check(Math.abs(sh.th - 40 * LineStamper.DEG) < 1e-9 && Math.abs(sh.B / sh.A - .5) < 1e-3, "without a reading its oval lies at its Nib Angle");
        BodyStamper.shape(sh, b, 80, 1, 0, 0, .5, 0);
        check(Math.abs(sh.th - Math.PI / 2) < 1e-9, "a lean to the right stands it upright");
    }
    static void quick() {
        WatercolorSession sa = new WatercolorSession(), sb = new WatercolorSession();
        ToneDocument a = darkBand(W, H), b = darkBand(W, H);
        sa.holding("A"); sb.holding("A");
        sa.held.keepLoad("A", .6); sb.held.keepLoad("A", .6);
        check(QuickStroke.quick(ResolvedBrush.of(oil(), 60, .3)), "Oil paint goes quick");
        CarrySmudgeChecks.feed(QuickStroke.quickly(a, oil(), 60, .3, sa), line(30, 200, 610, 200, .8));
        CarrySmudgeChecks.feed(ModularStroke.start(b, oil(), 60, .3, sb), line(30, 200, 610, 200, .8));
        check(QuickStrokeChecks.same(a, b) && sa.held.load("A") == sb.held.load("A"), "quick ends as live, leaving the brush the same paint");
    }
    /** A brush without Paint Load: no paint to spend at any sample (the stampers read 1 without a lookup). */
    static void off() {
        for (ToolPresets.Preset p : ToolPresets.all()) {
            // Loaded oils (the wet paint spec) is Oil paint's Wet successor: it runs out too.
            if (p.name.equals("Oil paint") || p.name.equals("Loaded oils")) continue;
            ResolvedBrush r = ResolvedBrush.of(p.brush().parts, 60, 1);
            check(r.loaded == null && (r.body == null || !r.body.crescent), p.name + " never runs out");
        }
        ToneDocument d = InkWashLayers.blank(W, H);
        ModularStroke st = ModularStroke.start(d, FloatChecks.preset("Steady line"), 8, 1, new WatercolorSession());
        check(st.paint() == null, "a stroke without Paint Load has no paint to spend");
        st.finish();
    }

    static String timing = "";
    /** Desktop ms a sample, Oil paint against the same brush without Paint Load (the Filbert's oval). */
    static void timings() {
        ModularBrush off = oil();
        off.part(BrushPart.CARRY).set(PartDial.PLOAD, 0);
        StringBuilder out = new StringBuilder("desktop, ms a sample:");
        for (int size : new int[]{60, 120}) {
            double on = WetSmudgeChecks.time(oil(), size)[0], without = WetSmudgeChecks.time(off, size)[0];
            out.append(String.format(java.util.Locale.ROOT, " %d px %.3f, without Paint Load %.3f;", size, on, without));
            Timing.limit(on <= 2.5 * without + .03, "Oil paint draws about as fast as without Paint Load at " + size + " px");
        }
        timing = out.toString();
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
