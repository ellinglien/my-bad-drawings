package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * Gradient ({@link GreyGradient}): Off changes nothing; Fade reaches paper at its Length and stays there; To Grey ends at
 * its End Grey, lighter or darker; Cycle visits Grey Shift's set in order, round and round, from the stroke's grey on
 * (Toolbar's greys as the bar has them, or Any's); every stroke starts again at its own grey (Grey Shift's pick too);
 * Ink, Wash, Charcoal, Graphite and Crayon take it, Opaque, Resist, Screentone and Float don't; with Paint Load the two
 * shares multiply; a Fill doesn't shift; the quick preview shows it, and lays what the live stroke does; codes, every
 * other code byte for byte; kept by a Control preset; never rolled, never a variation's; its words; Brush Lab's rows;
 * under 0.1 ms an input sample.
 */
public final class GradientChecks {
    static final int W = 700, H = 100, Y = 50, X0 = 20, X1 = 640;
    /** A Length of 2 cm, page px. */
    static final double L = 2 * GreyGradient.PX_CM;

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        off(); fade(); toGrey(); cycle(); fresh(); media(); load(); fill(); quick(); codes(); words(); lab(); speed();
        System.out.println("PASS: Gradient off changes nothing; Fade reaches paper at its Length; To Grey ends at its End Grey, lighter or darker; Cycle visits the set in order, "
                + "round and round, the toolbar's greys as set; each stroke starts again at its grey, Grey Shift's pick too; ink, wash, charcoal, graphite and crayon take it, "
                + "opaque, resist, screentone and float don't; with Paint Load the shares multiply; a fill doesn't shift; the quick preview shows it and lays what the live stroke does; "
                + "codes round-trip, every other code byte for byte; a Control preset keeps it; never rolled or a variation's; its words; Brush Lab's rows; under 0.1 ms a sample");
    }

    /** A {@code medium} brush, round, Instant, steady, its hand's {@code dials} (pairs of a dial and its value), a Length of 2 cm unless set. */
    static ModularBrush brush(PartChoice medium, Object... dials) {
        ModularBrush b = SwatchChecks.brush(PartDial.GRAD_LEN, 2);
        b.set(BrushPart.MEDIUM, new ModularBrush.Part(medium));
        ModularBrush.Part hand = b.part(BrushPart.HAND);
        for (int i = 0; i < dials.length; i += 2) hand.set((PartDial) dials[i], ((Number) dials[i + 1]).doubleValue());
        return b.normalize();
    }
    static ModularBrush ink(Object... dials) { return brush(PartChoice.INK, dials); }

    /** A straight stroke of {@code b} from {@link #X0} to {@link #X1} along {@link #Y}, at {@code size} and {@code darkness}, quick or live, on a fresh page. */
    static ToneDocument draw(ModularBrush b, double size, double darkness, boolean quick, WatercolorSession session) {
        ToneDocument d = InkWashLayers.blank(W, H);
        draw(d, b, size, darkness, quick, session);
        return d;
    }
    static void draw(ToneDocument d, ModularBrush b, double size, double darkness, boolean quick, WatercolorSession session) {
        DrawingStroke s;
        if (quick) s = QuickStroke.quickly(d, b, size, darkness, session);
        else { ModularStroke.selectLayer(d, b); s = new ModularStroke(d, b, size, darkness, session); }
        for (int x = X0; x <= X1; x += 3) { s.sample(x, Y, .6f, Float.NaN, Float.NaN); s.present(); }
        s.finish();
    }
    /** The mean tone round distance {@code at} along the stroke: a 9 by 5 box on its middle. */
    static double tone(ToneDocument d, double at) {
        int x = (int) Math.round(X0 + at);
        double sum = 0;
        for (int dx = -4; dx <= 4; dx++) for (int dy = -2; dy <= 2; dy++) sum += d.compositeTone(x + dx, Y + dy);
        return sum / 45;
    }

    static void off() {
        ModularBrush b = ink();
        check(GreyGradient.rule(b) == GreyGradient.OFF && GreyGradient.of(b, .7, null) == null && GreyGradient.words(b) == null, "off by default: no gradient, no words");
        ToneDocument plain = draw(b, 10, .7, false, null), set = draw(ink(PartDial.GRAD_TO, 12, PartDial.GRAD_LEN, 5), 10, .7, false, null);
        for (int x = 0; x < W; x += 7) check(plain.compositeTone(x, Y) == set.compositeTone(x, Y), "off, its other dials change nothing");
    }

    static void fade() {
        ModularBrush b = ink(PartDial.GRADIENT, GreyGradient.FADE);
        GreyGradient g = GreyGradient.of(b, .8, null);
        check(g.darkness(0) == .8 && Math.abs(g.darkness(L / 2) - .4) < 1e-9 && g.darkness(L) == 0 && g.darkness(3 * L) == 0 && g.ref == .8 && Math.abs(g.length - L) < 1e-9,
                "Fade: from the stroke's grey to paper over its Length, then paper");
        ToneDocument d = draw(b, 10, 1, false, null), plain = draw(ink(), 10, 1, false, null);
        check(Math.abs(tone(d, 6) - tone(plain, 6)) < 12, "it starts at the stroke's grey: " + tone(d, 6) + " against " + tone(plain, 6));
        double last = -1;
        for (double at = 6; at < L; at += 12) { double t = tone(d, at); check(t >= last - 1, "it lightens along the stroke: " + t + " at " + at); last = t; }
        check(Math.abs(tone(d, L / 2) - 255 * (1 - .5 * (255 - tone(plain, L / 2)) / 255)) < 14, "half way it is half as dark: " + tone(d, L / 2));
        for (double at = L + 12; at < X1 - X0 - 20; at += 20) check(tone(d, at) == 255, "past its Length, paper: " + tone(d, at) + " at " + at);
    }

    static void toGrey() {
        for (PartChoice m : new PartChoice[]{PartChoice.INK, PartChoice.WASH}) {
            // Lighter: black to a third.
            ToneDocument d = draw(brush(m, PartDial.GRADIENT, GreyGradient.TO_GREY, PartDial.GRAD_TO, 5), 14, 1, false, null);
            ToneDocument third = draw(brush(m), 14, 1 / 3.0, false, null), black = draw(brush(m), 14, 1, false, null);
            check(Math.abs(tone(d, 6) - tone(black, 6)) < 12, m + ": To Grey starts at the stroke's grey");
            for (double at = L + 20; at < X1 - X0 - 30; at += 40)
                check(Math.abs(tone(d, at) - tone(third, at)) < 4, m + ": To Grey ends at its End Grey: " + tone(d, at) + " against " + tone(third, at) + " at " + at);
            // Darker: a light grey to black, laid at black, a share of it at first.
            ToneDocument up = draw(brush(m, PartDial.GRADIENT, GreyGradient.TO_GREY, PartDial.GRAD_TO, 15), 14, .2, false, null), light = draw(brush(m), 14, .2, false, null);
            check(Math.abs(tone(up, 4) - tone(light, 4)) < 12, m + ": darker, it starts at the stroke's grey: " + tone(up, 4) + " against " + tone(light, 4));
            check(Math.abs(tone(up, L + 60) - tone(black, L + 60)) < 4 && tone(up, L / 2) < tone(light, L / 2) - 20, m + ": and ends black: " + tone(up, L + 60) + " against " + tone(black, L + 60));
        }
        check(GreyGradient.of(ink(PartDial.GRADIENT, GreyGradient.TO_GREY, PartDial.GRAD_TO, 15), .2, null).ref == 1, "a darker End Grey: laid at it");
    }

    static void cycle() {
        // Toolbar: from the stroke's grey (in the set), the set's next, round and round.
        ModularBrush b = ink(PartDial.GRADIENT, GreyGradient.CYCLE);
        GreyGradient g = GreyGradient.of(b, ToolSwatches.GREYS[1], null);
        double[] want = {ToolSwatches.GREYS[1], ToolSwatches.GREYS[2], ToolSwatches.GREYS[3], ToolSwatches.GREYS[0], ToolSwatches.GREYS[1], ToolSwatches.GREYS[2]};
        for (int m = 0; m < want.length; m++) check(Math.abs(g.darkness(m * L) - want[m]) < 1e-9, "Cycle visits the toolbar's greys in order, round and round: stop " + m + " " + g.darkness(m * L));
        double mid = g.darkness(L / 2);
        check(mid < want[0] && mid > want[1], "and blends from one to the next");
        // Its set only, and the toolbar's greys as set.
        ModularBrush part = ink(PartDial.GRADIENT, GreyGradient.CYCLE, PartDial.SHIFT_BAR, 0b0101);
        GreyGradient p = GreyGradient.of(part, ToolSwatches.GREYS[1], null);
        check(Math.abs(p.darkness(0) - ToolSwatches.GREYS[1]) < 1e-9 && p.darkness(L) == ToolSwatches.GREYS[3] && p.darkness(2 * L) == ToolSwatches.GREYS[1] && p.darkness(3 * L) == ToolSwatches.GREYS[3],
                "a grey not in the set starts it from the nearest, then only the set's");
        ToolSwatchesChecks.prefs = new HashMap<>();
        // An older build's swatches (no longer set in the app since the one grey, 2026-10-24): Dark, then .6 third, then .3.
        ToolSwatchesChecks.prefs.put(ToolSwatches.GREY_SWATCHES, "1,0.6,0.3");
        ToolSwatches swatches = new ToolSwatches(ToolSwatchesChecks.store());
        check(GreyGradient.of(b, 1, swatches).darkness(2 * L) == .6, "the toolbar's greys as the bar has them");
        // Any: the panel's levels in order.
        ModularBrush any = ink(PartDial.GRADIENT, GreyGradient.CYCLE, PartDial.SHIFT_GREYS, SwatchPicker.ANY, PartDial.SHIFT_ANY, 0xff, PartDial.SHIFT_ANY2, 0xff);
        ModularBrush.Part hand = any.part(BrushPart.HAND);
        hand.set(PartDial.SHIFT_ANY, 0xff & ~(1 << 3)); hand.set(PartDial.SHIFT_ANY2, 0xff & ~(1 << (9 - 8)) & ~(1 << (12 - 8)));
        check(Arrays.equals(SwatchPicker.set(any), new int[]{3, 9, 12}), "a set of three of Any's: " + Arrays.toString(SwatchPicker.set(any)));
        GreyGradient a = GreyGradient.of(any, .5, null);
        double[] stops = {.5, .6, .8, .2, .6, .8, .2};
        for (int m = 0; m < stops.length; m++) check(Math.abs(a.darkness(m * L) - stops[m]) < 1e-9, "Any's set, nearest first: stop " + m + " " + a.darkness(m * L));
        check(a.ref == .8, "laid at the set's darkest");
        // Drawn: at each stop the grey a plain stroke of it lays.
        ToneDocument d = draw(b, 10, 1, false, null);
        double[] greys = {1, ToolSwatches.GREYS[1], ToolSwatches.GREYS[2]};
        for (int m = 0; m < greys.length; m++) {
            ToneDocument plain = draw(ink(), 10, greys[m], false, null);
            check(Math.abs(tone(d, Math.max(5, m * L)) - tone(plain, Math.max(5, m * L))) < 5, "drawn, stop " + m + " lays its grey: " + tone(d, Math.max(5, m * L)) + " against " + tone(plain, Math.max(5, m * L)));
        }
    }

    /** Every stroke starts again at its own grey, Grey Shift's pick when it is on. */
    static void fresh() {
        WatercolorSession session = new WatercolorSession();
        ModularBrush b = ink(PartDial.GRADIENT, GreyGradient.FADE);
        ToneDocument d = InkWashLayers.blank(W, 2 * H);
        for (int i = 0; i < 2; i++) {
            DrawingStroke s = QuickStroke.quickly(d, b, 10, 1, session);
            for (int x = X0; x <= X1; x += 3) { s.sample(x, Y + i * H, .6f, Float.NaN, Float.NaN); s.present(); }
            s.finish();
        }
        int a = d.compositeTone(X0 + 8, Y), c = d.compositeTone(X0 + 8, Y + H);
        check(a < 40 && a == c && d.compositeTone(X1 - 20, Y + H) == 255, "the second stroke starts black again, as the first: " + a + ", " + c);
        // Grey Shift's pick is its start.
        ModularBrush shifting = ink(PartDial.GRADIENT, GreyGradient.FADE, PartDial.SHIFT, SwatchPicker.PATTERN);
        HeldPaint held = new HeldPaint();
        for (int i = 0; i < 3; i++) {
            double start = SwatchPicker.look(Brush.of(shifting), new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, 10, .5), null, held, HeldPaint.ANY).darkness;
            check(start == ToolSwatches.GREYS[i] && GreyGradient.of(shifting, start, null).darkness(0) == start, "Grey Shift's pick starts stroke " + i);
            ToneDocument t = draw(shifting, 10, start, false, null), plain = draw(ink(), 10, start, false, null);
            check(Math.abs(tone(t, 5) - tone(plain, 5)) < 12, "and is what it lays first: " + tone(t, 5) + " against " + tone(plain, 5));
        }
    }

    static void media() {
        for (PartChoice m : new PartChoice[]{PartChoice.INK, PartChoice.WASH, PartChoice.CHARCOAL, PartChoice.GRAPHITE, PartChoice.CRAYON}) {
            ModularBrush b = brush(m, PartDial.GRADIENT, GreyGradient.FADE);
            check(GreyGradient.takes(b) && GreyGradient.on(b), m + " takes a Gradient");
            for (boolean slow : new boolean[]{false, true}) {
                ModularBrush c = slow ? CarrySmudgeChecks.with(b, PartChoice.FAST_WET) : b;
                ToneDocument d = draw(c, 16, 1, false, null);
                double first = tone(d, 8), half = tone(d, L * .6), past = tone(d, L + 40);
                check(first < 200 && half > first + 10 && past >= 250, m + (slow ? " (Quick)" : "") + " fades along the stroke to paper: " + first + ", " + half + ", " + past);
            }
        }
        for (PartChoice m : new PartChoice[]{PartChoice.COVER, PartChoice.RESIST, PartChoice.TONE}) {
            ModularBrush b = brush(m, PartDial.GRADIENT, GreyGradient.FADE);
            check(!GreyGradient.takes(b) && GreyGradient.of(b, 1, null) == null && GreyGradient.words(b) == null && !BrushLab.handRows(b).contains(PartDial.GRADIENT),
                    m + " takes none: no gradient, no words, no rows");
        }
        ModularBrush floats = CarrySmudgeChecks.with(ink(PartDial.GRADIENT, GreyGradient.FADE), PartChoice.FLOAT);
        check(!GreyGradient.takes(floats) && GreyGradient.of(floats, 1, null) == null, "nor Float");
    }

    /** With Paint Load: the shares multiply, each its own; a brush with both draws. */
    static void load() {
        ModularBrush b = ink(PartDial.GRADIENT, GreyGradient.FADE);
        GreyGradient g = GreyGradient.of(b, 1, null);
        StrokePoints pts = new StrokePoints();
        for (int x = 0; x < 300; x += 5) pts.add(x, 0, .5f, PenSpeed.UNTIMED, Float.NaN, Float.NaN);
        pts.owns();
        Arrays.fill(pts.own, .5f);
        g.apply(pts, true);
        boolean ok = true;
        for (int k = 0; k < pts.count; k++) ok &= Math.abs(pts.own[k] - .5 * g.shareAt(pts.d[k])) < 1e-6;
        check(ok && pts.own[0] == .5f, "over Paint Load: its share times the gradient's");
        StrokePoints alone = new StrokePoints();
        for (int x = 0; x < 300; x += 5) alone.add(x, 0, .5f, PenSpeed.UNTIMED, Float.NaN, Float.NaN);
        GreyGradient.of(b, 1, null).apply(alone, false);
        check(alone.own[0] == 1 && Math.abs(alone.own[alone.count - 1] - g.shareAt(alone.d[alone.count - 1])) < 1e-6, "alone: the gradient's share");
        // Oil paint is Opaque (no Gradient); a wash Smear with Paint Load takes both.
        check(!GreyGradient.takes(FloatChecks.preset("Oil paint")), "Oil paint (Opaque) takes no Gradient");
        ModularBrush loaded = brush(PartChoice.WASH), faded = brush(PartChoice.WASH, PartDial.GRADIENT, GreyGradient.FADE, PartDial.GRAD_LEN, 4);
        for (ModularBrush w : new ModularBrush[]{loaded, faded}) {
            ModularBrush.Part smear = new ModularBrush.Part(PartChoice.SMUDGE);
            smear.set(PartDial.PLOAD, .6); smear.set(PartDial.PMIN, 1);
            w.set(BrushPart.CARRY, smear);
            w.normalize();
        }
        check(ResolvedBrush.of(faded, 30, 1).loaded != null && GreyGradient.on(faded), "a wash Smear with Paint Load and a Fade");
        ToneDocument d = draw(faded, 30, 1, false, new WatercolorSession()), plain = draw(loaded, 30, 1, false, new WatercolorSession());
        double first = tone(d, 6), firstPlain = tone(plain, 6), mid = tone(d, 2 * L), midPlain = tone(plain, 2 * L);
        check(Math.abs(first - firstPlain) < 8 && midPlain < 250 && mid > midPlain + 10, "with Paint Load it fades too: " + first + "/" + firstPlain + ", " + mid + "/" + midPlain);
    }

    static void fill() {
        ModularBrush b = ink(PartDial.GRADIENT, GreyGradient.TO_GREY, PartDial.GRAD_TO, 15);
        check(GreyGradient.on(b) && !GreyGradient.on(FillTool.brush(b)) && GreyGradient.on(b), "a fill lays its grey at once: no Gradient (the slot's brush keeps it)");
    }

    /** The quick preview shows the gradient under the pen; the quick stroke lays what the live one does. */
    static void quick() {
        for (Object[] c : new Object[][]{{GreyGradient.FADE, 5}, {GreyGradient.TO_GREY, 15}, {GreyGradient.CYCLE, 5}}) {
            ModularBrush b = ink(PartDial.GRADIENT, c[0], PartDial.GRAD_TO, c[1]);
            double start = (int) c[0] == GreyGradient.TO_GREY ? .2 : 1;
            ToneDocument q = draw(b, 12, start, true, null), live = draw(b, 12, start, false, null);
            int diff = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (q.compositeTone(x, y) != live.compositeTone(x, y)) diff++;
            check(diff == 0, PartDial.GRADIENT.choices[(int) c[0]] + ": the quick stroke lays what the live one does (" + diff + " pixels differ)");
        }
        ToneDocument d = InkWashLayers.blank(W, H);
        QuickStroke s = QuickStroke.quickly(d, ink(PartDial.GRADIENT, GreyGradient.FADE), 12, 1, null);
        for (int x = X0; x <= X1; x += 3) { s.sample(x, Y, .6f, Float.NaN, Float.NaN); s.present(); }
        int near = d.compositeTone(X0 + 6, Y), half = d.compositeTone((int) (X0 + L / 2), Y), past = d.compositeTone((int) (X0 + L + 30), Y);
        s.finish();
        check(near < 40 && half > near + 40 && past == 255, "the preview fades under the pen: " + near + ", " + half + ", " + past);
    }

    static void codes() {
        ModularBrush b = ink(PartDial.GRADIENT, GreyGradient.TO_GREY, PartDial.GRAD_TO, 9, PartDial.GRAD_LEN, 7);
        b.name = "graded";
        String code = ModularCode.encode(b);
        ModularBrush back = ModularCode.decode(code);
        check(back != null && ModularCode.encode(back).equals(code) && GreyGradient.rule(back) == GreyGradient.TO_GREY && GreyGradient.end(back) == 9 && GreyGradient.cm(back) == 7,
                "a code with a Gradient reads back, its End Grey and Length too: " + code);
        ModularBrush fade = ink(PartDial.GRADIENT, GreyGradient.FADE);
        fade.part(BrushPart.HAND).set(PartDial.GRAD_LEN, PartDial.GRAD_LEN.def);
        ModularBrush fadeBack = ModularCode.decode(ModularCode.encode(fade));
        check(GreyGradient.rule(fadeBack) == GreyGradient.FADE && GreyGradient.cm(fadeBack) == 10 && ModularCode.encode(fadeBack).equals(ModularCode.encode(fade)), "a Fade at its defaults reads back");
        ModularBrush plain = SwatchChecks.brush();
        String hand = ModularCode.encode(plain).split("\\.")[1].split("_")[5], shift = ModularCode.encode(SwatchChecks.brush(PartDial.SHIFT, SwatchPicker.RANDOM)).split("\\.")[1].split("_")[5];
        check(hand.length() == 1 + 9 * 2 + 2 + 1, "off, a hand writes as before: " + hand);
        check(ModularCode.encode(SwatchChecks.brush(PartDial.SHIFT, SwatchPicker.RANDOM, PartDial.GRAD_TO, 5)).split("\\.")[1].split("_")[5].equals(shift), "off, Grey Shift's code as before");
        String on = ModularCode.encode(SwatchChecks.brush(PartDial.GRADIENT, GreyGradient.CYCLE)).split("\\.")[1].split("_")[5];
        check(on.startsWith(hand) && on.length() == hand.length() + 7 + 3 + 3 + 3 * 2 + 1 && on.endsWith("3"), "on, the hand adds the dials before it (Grey Shift's too), then its rule: " + on);
        for (ToolPresets.Preset p : ToolPresets.all()) check((ModularCode.encode(ModularCode.decode(p.code)).equals(p.code) || HandHatchChecks.handHatch(p.code)) && !GreyGradient.on(ModularCode.decode(p.code)), p.name + "'s code byte for byte, no Gradient");
        ModularBrush.Part kept = ModularBrush.switchTo(b.part(BrushPart.HAND), PartChoice.PRESS_DARKER);
        check(GreyGradient.rule(new ModularBrush("k", 1, b.style)) == GreyGradient.OFF && (int) kept.get(PartDial.GRADIENT) == GreyGradient.TO_GREY && (int) kept.get(PartDial.GRAD_TO) == 9
                && (int) kept.get(PartDial.GRAD_LEN) == 7, "a Control preset keeps Gradient and its dials");
        Random random = new Random(23);
        for (ModularStyle s : ModularStyle.all()) for (int i = 0; i < 200; i++) {
            ModularBrush rolled = s.roll(random);
            check(GreyGradient.rule(rolled) == GreyGradient.OFF, s.name + " never rolls Gradient");
            if (i % 40 == 0) for (ModularVariations.Made m : ModularVariations.make(rolled, new ModularLocks(), random))
                check(GreyGradient.rule(m.brush) == GreyGradient.OFF, s.name + ": no variation turns Gradient on");
        }
    }

    static void words() {
        check("Fades to paper over 2 cm.".equals(GreyGradient.words(ink(PartDial.GRADIENT, GreyGradient.FADE))), GreyGradient.words(ink(PartDial.GRADIENT, GreyGradient.FADE)));
        check("Shifts to 33% grey over 2 cm.".equals(GreyGradient.words(ink(PartDial.GRADIENT, GreyGradient.TO_GREY))), GreyGradient.words(ink(PartDial.GRADIENT, GreyGradient.TO_GREY)));
        check("Shifts to black over 2 cm.".equals(GreyGradient.words(ink(PartDial.GRADIENT, GreyGradient.TO_GREY, PartDial.GRAD_TO, 15))), "To Grey's words, to black");
        check("Fades to paper over 2 cm.".equals(GreyGradient.words(ink(PartDial.GRADIENT, GreyGradient.TO_GREY, PartDial.GRAD_TO, 0))), "To Grey's words, to paper white");
        check("Cycles through its greys every 2 cm.".equals(GreyGradient.words(ink(PartDial.GRADIENT, GreyGradient.CYCLE))), "Cycle's words");
        String said = ModularWords.plainWords(ink(PartDial.GRADIENT, GreyGradient.FADE, PartDial.SHIFT, SwatchPicker.RANDOM));
        check(said.contains("Greys shift each stroke at random. Fades to paper over 2 cm."), "after Grey Shift's: " + said);
        check(!ModularWords.plainWords(ink()).contains("over"), "off says nothing");
        check(PartDial.GRAD_TO.shown(0).equals("White") && PartDial.GRAD_TO.shown(15).equals("Black") && PartDial.GRAD_TO.shown(7).equals("47%") && PartDial.GRAD_LEN.shown(10).equals("10 cm"),
                "End Grey and Length as they show");
        for (PartDial d : PartDial.GRADIENTS) {
            check(Character.isUpperCase(d.label.charAt(0)) && d.label.length() <= 12, d.label + ": Title Case, short");
            if (d.choice()) for (String option : d.choices) check(Character.isUpperCase(option.charAt(0)) && !option.contains(";"), d.label + "'s options are Title Case");
        }
    }

    static void lab() {
        List<PartDial> base = SwatchChecks.handHead(ink(), PartDial.SHIFT);
        check(BrushLab.handRows(ink()).equals(concat(base, PartDial.GRADIENT)), "off, the Control card adds Gradient's row: " + BrushLab.handRows(ink()));
        check(BrushLab.handRows(ink(PartDial.GRADIENT, GreyGradient.FADE)).equals(concat(base, PartDial.GRADIENT, PartDial.GRAD_LEN)), "Fade: its Length");
        check(BrushLab.handRows(ink(PartDial.GRADIENT, GreyGradient.TO_GREY)).equals(concat(base, PartDial.GRADIENT, PartDial.GRAD_TO, PartDial.GRAD_LEN)), "To Grey: its End Grey and Length");
        check(BrushLab.handRows(ink(PartDial.GRADIENT, GreyGradient.CYCLE)).equals(concat(base, PartDial.GRADIENT, PartDial.GRAD_LEN, PartDial.SHIFT_GREYS, null)), "Cycle: its Length, Greys and set");
        check(BrushLab.handRows(ink(PartDial.GRADIENT, GreyGradient.CYCLE, PartDial.SHIFT, SwatchPicker.RANDOM)).equals(
                SwatchChecks.handHead(ink(), PartDial.SHIFT, PartDial.SHIFT_GREYS, null, PartDial.GRADIENT, PartDial.GRAD_LEN)), "Cycle with Grey Shift on: the Greys and set once, Grey Shift's");
        check(!BrushLab.handRows(ink(PartDial.GRADIENT, GreyGradient.CYCLE), false).contains(PartDial.GRADIENT), "and without Gradient's rows, where there's no room");
    }

    /** At most 0.1 ms an input sample on this computer: the live stroke's more for a gradient, and a quick stroke's pen thread with one. */
    static void speed() {
        ModularBrush off = ink(), on = ink(PartDial.GRADIENT, GreyGradient.CYCLE, PartDial.SHIFT_GREYS, SwatchPicker.ANY, PartDial.GRAD_LEN, 1);
        double best = Double.MAX_VALUE, quick = Double.MAX_VALUE;
        int n = 0;
        for (int round = 0; round < 6; round++) {
            long a = 0, b = 0, q = 0;
            n = 0;
            for (ModularBrush brush : new ModularBrush[]{off, on}) {
                ToneDocument d = InkWashLayers.blank(1400, 300);
                ModularStroke.selectLayer(d, brush);
                ModularStroke s = new ModularStroke(d, brush, 10, 1, null);
                long t0 = System.nanoTime();
                int k = 0;
                for (int x = 20; x < 1380; x += 2, k++) { s.sample(x, 150 + (float) Math.sin(x / 40.0) * 60, .6f, Float.NaN, Float.NaN); s.present(); }
                long t = System.nanoTime() - t0;
                s.finish();
                if (brush == off) a = t; else { b = t; n = k; }
            }
            ToneDocument d = InkWashLayers.blank(1400, 300);
            QuickStroke s = QuickStroke.quickly(d, on, 10, 1, null);
            long t0 = System.nanoTime();
            for (int x = 20; x < 1380; x += 2) { s.sample(x, 150 + (float) Math.sin(x / 40.0) * 60, .6f, Float.NaN, Float.NaN); s.present(); }
            q = System.nanoTime() - t0;
            s.finish();
            best = Math.min(best, (b - a) / 1e6 / n);
            quick = Math.min(quick, q / 1e6 / n);
        }
        Timing.limit(best <= .1, "a gradient adds " + best + " ms a sample to a live stroke");
        Timing.limit(quick <= .1, "a quick stroke's pen thread with a Cycle over Any: " + quick + " ms a sample");
        System.out.println("Gradient: +" + String.format("%.4f", best) + " ms a sample live, " + String.format("%.4f", quick) + " ms a sample on a quick stroke's pen thread");
    }

    @SafeVarargs private static <T> List<T> concat(List<T> a, T... more) { List<T> out = new java.util.ArrayList<>(a); out.addAll(Arrays.asList(more)); return out; }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
