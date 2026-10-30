package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Grey Shift (mischief-lab-2.html's Shifting swatches; {@link SwatchPicker}): Off picks nothing; Random picks only
 * from the set, every grey in it in time; From a Set deals each grey once before any again, never one twice running
 * across a reshuffle; Pattern goes in order or there and back over the set; Toolbar's greys are the bar's swatches as
 * they are, Any's the panel's 16, each palette with its own set (Any's without paper white at first), never emptied;
 * each holder keeps its own turn; picked greys draw; codes, every other code byte for byte; kept by a Control preset;
 * never rolled; its words; Brush Lab's rows and set.
 */
public final class SwatchChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        off(); random(); deck(); pattern(); palettes(); holders(); draws(); codes(); words(); lab();
        System.out.println("PASS: Grey Shift off picks nothing and keeps the look; Random picks only from the set and every grey in it; From a Set deals each once before any again, "
                + "never one twice running across a reshuffle; Pattern goes in order or there and back over the set, starting again when it changes; Toolbar is the bar's greys as set, "
                + "Any the panel's 16, each with its own set (Any's without paper white), never emptied; each holder keeps its own turn; picked greys draw; codes round-trip, "
                + "every other code byte for byte; a Control preset keeps it; never rolled; its words; Brush Lab's rows and set");
    }

    /** An ink brush, Instant, steady, its hand's {@code dials} (pairs of a dial and its value). */
    static ModularBrush brush(Object... dials) {
        ModularBrush b = StippleChecks.mk(PartChoice.ROUND, PartChoice.STEADY);
        b.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.NONE));
        ModularBrush.Part hand = b.part(BrushPart.HAND);
        for (int i = 0; i < dials.length; i += 2) hand.set((PartDial) dials[i], ((Number) dials[i + 1]).doubleValue());
        return b.normalize();
    }
    /** {@code n} picks of {@code b} from a fresh turn. */
    static int[] picks(ModularBrush b, int n, long seed) {
        SwatchPicker.State s = new SwatchPicker.State(new Random(seed));
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = SwatchPicker.pick(b, s);
        return out;
    }

    static void off() {
        ModularBrush b = brush();
        check(!SwatchPicker.shifts(b) && SwatchPicker.pick(b, new SwatchPicker.State(new Random(1))) == -1, "off by default: no pick");
        ToolSwatches.Look look = new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, 12, .4);
        check(SwatchPicker.look(Brush.of(b), look, null, new HeldPaint(), HeldPaint.ANY) == look && SwatchPicker.look(null, look, null, new HeldPaint(), HeldPaint.ANY) == look,
                "off, and the eraser, draw at the look as it is");
        check(SwatchPicker.words(b) == null, "off says nothing");
    }

    static void random() {
        int[] p = picks(brush(PartDial.SHIFT, SwatchPicker.RANDOM), 400, 2);
        Set<Integer> seen = new HashSet<>();
        for (int k : p) seen.add(k);
        check(seen.equals(new HashSet<>(Arrays.asList(0, 1, 2, 3))), "Random over the toolbar picks each of its four greys: " + seen);
        boolean repeats = false;
        for (int i = 1; i < p.length; i++) repeats |= p[i] == p[i - 1];
        check(repeats, "Random allows a grey twice running");
        p = picks(brush(PartDial.SHIFT, SwatchPicker.RANDOM, PartDial.SHIFT_BAR, 0b0101), 200, 3);
        for (int k : p) check(k == 1 || k == 3, "Random picks only from the set (greys 1 and 3): " + k);
    }

    static void deck() {
        for (Object[] set : new Object[][]{{PartDial.SHIFT_GREYS, SwatchPicker.TOOLBAR}, {PartDial.SHIFT_GREYS, SwatchPicker.ANY}, {PartDial.SHIFT_BAR, 0b1001}}) {
            ModularBrush b = brush(PartDial.SHIFT, SwatchPicker.DECK, set[0], set[1]);
            int n = SwatchPicker.set(b).length;
            int[] p = picks(b, n * 50, 4);
            for (int deal = 0; deal < 50; deal++) {
                Set<Integer> dealt = new HashSet<>();
                for (int i = 0; i < n; i++) dealt.add(p[deal * n + i]);
                check(dealt.size() == n, "From a Set deals each of its " + n + " greys once before any again");
                for (int i = 0; i < n; i++) check(SwatchPicker.in(b, p[deal * n + i]), "only greys in the set");
            }
            for (int i = 1; i < p.length; i++) check(p[i] != p[i - 1], "never the same grey twice running, across a reshuffle too: at " + i);
        }
        int[] one = picks(brush(PartDial.SHIFT, SwatchPicker.DECK, PartDial.SHIFT_BAR, 0b1110), 5, 5);
        check(Arrays.equals(one, new int[]{0, 0, 0, 0, 0}), "a set of one deals it every time");
    }

    static void pattern() {
        check(Arrays.equals(picks(brush(PartDial.SHIFT, SwatchPicker.PATTERN), 9, 6), new int[]{0, 1, 2, 3, 0, 1, 2, 3, 0}), "In Order: 0 1 2 3 0 …");
        check(Arrays.equals(picks(brush(PartDial.SHIFT, SwatchPicker.PATTERN, PartDial.SHIFT_ORDER, SwatchPicker.PING_PONG), 9, 6), new int[]{0, 1, 2, 3, 2, 1, 0, 1, 2}),
                "Ping-Pong: 0 1 2 3 2 1 0 1 2 …");
        check(Arrays.equals(picks(brush(PartDial.SHIFT, SwatchPicker.PATTERN, PartDial.SHIFT_BAR, 0b0010), 6, 6), new int[]{0, 2, 3, 0, 2, 3}), "over the set only");
        check(Arrays.equals(picks(brush(PartDial.SHIFT, SwatchPicker.PATTERN, PartDial.SHIFT_ORDER, SwatchPicker.PING_PONG, PartDial.SHIFT_BAR, 0b1110), 3, 6), new int[]{0, 0, 0}),
                "Ping-Pong over one grey stays on it");
        // A change of rule, order or set starts the turn again.
        ModularBrush b = brush(PartDial.SHIFT, SwatchPicker.PATTERN);
        SwatchPicker.State s = new SwatchPicker.State(new Random(7));
        SwatchPicker.pick(b, s); SwatchPicker.pick(b, s);
        b.part(BrushPart.HAND).set(PartDial.SHIFT_ORDER, SwatchPicker.PING_PONG);
        check(SwatchPicker.pick(b, s) == 0, "a new order starts from the first grey");
    }

    static void palettes() {
        ModularBrush any = brush(PartDial.SHIFT, SwatchPicker.RANDOM, PartDial.SHIFT_GREYS, SwatchPicker.ANY);
        int[] set = SwatchPicker.set(any);
        check(set.length == 15 && set[0] == 1 && set[14] == 15, "Any offers the panel's 16, paper white left out at first: " + Arrays.toString(set));
        check(SwatchPicker.darkness(any, null, 0) == 0 && SwatchPicker.darkness(any, null, 15) == 1 && Math.abs(SwatchPicker.darkness(any, null, 5) - 1 / 3.0) < 1e-12,
                "Any's greys are the panel's levels, paper to black");
        ModularBrush bar = brush(PartDial.SHIFT, SwatchPicker.RANDOM);
        check(SwatchPicker.set(bar).length == ToolSwatches.COUNT, "Toolbar offers the bar's greys, all in the set at first");
        for (int k = 0; k < ToolSwatches.COUNT; k++) check(SwatchPicker.darkness(bar, null, k) == ToolSwatches.GREYS[k], "without the bar, its first greys");
        ToolSwatchesChecks.prefs = new HashMap<>();
        // An older build's swatches (no longer set in the app since the one grey, 2026-10-24): Dark, then .6 third, then .3.
        ToolSwatchesChecks.prefs.put(ToolSwatches.GREY_SWATCHES, "1,0.6,0.3");
        ToolSwatches swatches = new ToolSwatches(ToolSwatchesChecks.store());
        check(SwatchPicker.darkness(bar, swatches, 2) == .6 && SwatchPicker.darkness(bar, swatches, 0) == swatches.grey(0), "Toolbar's greys are the bar's as they are set");
        // Each palette keeps its own set; the last grey in it stays.
        ModularBrush.Part hand = any.part(BrushPart.HAND);
        check(SwatchPicker.toggle(hand, SwatchPicker.ANY, 12) && !SwatchPicker.in(any, 12) && (int) hand.get(PartDial.SHIFT_ANY2) == 1 << 4
                && SwatchPicker.toggle(hand, SwatchPicker.ANY, 0) && SwatchPicker.in(any, 0) && (int) hand.get(PartDial.SHIFT_ANY) == 0, "Any's greys toggle in its two dials");
        check((int) hand.get(PartDial.SHIFT_BAR) == 0 && SwatchPicker.set(bar).length == 4, "Toolbar's set is its own");
        ModularBrush.Part barHand = bar.part(BrushPart.HAND);
        for (int k = 0; k < 3; k++) check(SwatchPicker.toggle(barHand, SwatchPicker.TOOLBAR, k), "a grey left out");
        check(!SwatchPicker.toggle(barHand, SwatchPicker.TOOLBAR, 3) && Arrays.equals(SwatchPicker.set(bar), new int[]{3}), "the last grey stays in");
        barHand.set(PartDial.SHIFT_BAR, 15);
        check(SwatchPicker.set(bar).length == 4, "a hand set with every grey left out has them all");
    }

    static void holders() {
        HeldPaint held = new HeldPaint();
        ModularBrush b = brush(PartDial.SHIFT, SwatchPicker.PATTERN);
        Brush brush = Brush.of(b);
        ToolSwatches.Look look = new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, 12, .4);
        List<Double> a = new ArrayList<>(), c = new ArrayList<>();
        for (int i = 0; i < 3; i++) a.add(SwatchPicker.look(brush, look, null, held, "SLOT_1").darkness);
        c.add(SwatchPicker.look(brush, look, null, held, "SLOT_2").darkness);
        a.add(SwatchPicker.look(brush, look, null, held, "SLOT_1").darkness);
        check(a.equals(Arrays.asList(ToolSwatches.GREYS[0], ToolSwatches.GREYS[1], ToolSwatches.GREYS[2], ToolSwatches.GREYS[3])) && c.get(0) == ToolSwatches.GREYS[0],
                "each holder keeps its own turn: " + a + " " + c);
        check(held.shifting("SLOT_1").last == 3 && SwatchPicker.look(brush, look, null, held, "SLOT_1").px == look.px, "the holder's last grey; the size as it was");
    }

    /** Two touches of a Pattern brush lay its two greys. */
    static void draws() {
        ModularBrush b = brush(PartDial.SHIFT, SwatchPicker.PATTERN, PartDial.SHIFT_BAR, 0b0110);
        HeldPaint held = new HeldPaint();
        int[] tones = new int[2];
        for (int i = 0; i < 2; i++) {
            ToneDocument d = InkWashLayers.blank(200, 100);
            double dark = SwatchPicker.look(Brush.of(b), new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, 10, .5), null, held, HeldPaint.ANY).darkness;
            DrawingStroke s = QuickStroke.start(d, b, 10, dark, null);
            for (int x = 20; x <= 180; x += 4) s.sample(x, 50, .6f, Float.NaN, Float.NaN);
            s.finish();
            tones[i] = d.compositeTone(100, 50);
        }
        check(tones[0] < 20 && tones[1] > 150 && tones[1] < 250, "black, then the light grey: " + Arrays.toString(tones));
    }

    static void codes() {
        ModularBrush b = brush(PartDial.SHIFT, SwatchPicker.PATTERN, PartDial.SHIFT_ORDER, SwatchPicker.PING_PONG, PartDial.SHIFT_GREYS, SwatchPicker.ANY, PartDial.SHIFT_ANY, 0x81, PartDial.SHIFT_ANY2, 0xc0);
        b.name = "shifty";
        String code = ModularCode.encode(b);
        ModularBrush back = ModularCode.decode(code);
        check(back != null && ModularCode.encode(back).equals(code) && Arrays.equals(SwatchPicker.set(back), SwatchPicker.set(b)) && SwatchPicker.rule(back) == SwatchPicker.PATTERN
                && SwatchPicker.palette(back) == SwatchPicker.ANY, "a code with Grey Shift reads back, its set too: " + code);
        ModularBrush bar = brush(PartDial.SHIFT, SwatchPicker.RANDOM, PartDial.SHIFT_BAR, 0b1010);
        check(Arrays.equals(SwatchPicker.set(ModularCode.decode(ModularCode.encode(bar))), new int[]{0, 2}), "the toolbar's set reads back");
        String plain = ModularCode.encode(brush()), hand = plain.split("\\.")[1].split("_")[5];
        check(hand.length() == 1 + 9 * 2 + 2 + 1, "off, a hand writes the nine, gain and dir as before: " + hand);
        String random = ModularCode.encode(brush(PartDial.SHIFT, SwatchPicker.RANDOM)).split("\\.")[1].split("_")[5];
        check(random.startsWith(hand) && random.length() == hand.length() + 7 + 3 + 1 && random.endsWith("1"), "on, the hand adds the dials before it (Tilt Range, page tilt, a Spiro's drives), then its rule: " + random);
        for (ToolPresets.Preset p : ToolPresets.all()) check((ModularCode.encode(ModularCode.decode(p.code)).equals(p.code) || HandHatchChecks.handHatch(p.code)), p.name + "'s code byte for byte");
        ModularBrush.Part kept = ModularBrush.switchTo(b.part(BrushPart.HAND), PartChoice.PRESS_DARKER);
        check((int) kept.get(PartDial.SHIFT) == SwatchPicker.PATTERN && (int) kept.get(PartDial.SHIFT_ANY2) == 0xc0, "a Control preset keeps Grey Shift and its set");
        Random random2 = new Random(19);
        for (ModularStyle s : ModularStyle.all()) for (int i = 0; i < 200; i++)
            check(!SwatchPicker.shifts(s.roll(random2)), s.name + " never rolls Grey Shift");
    }

    static void words() {
        check(ModularWords.plainWords(brush(PartDial.SHIFT, SwatchPicker.RANDOM)).contains("Greys shift each stroke at random."), ModularWords.plainWords(brush(PartDial.SHIFT, SwatchPicker.RANDOM)));
        check(ModularWords.plainWords(brush(PartDial.SHIFT, SwatchPicker.DECK)).contains("Greys shift each stroke, shuffled."), "From a Set's words");
        check(ModularWords.plainWords(brush(PartDial.SHIFT, SwatchPicker.PATTERN, PartDial.SHIFT_ORDER, SwatchPicker.PING_PONG)).contains("Greys shift each stroke, there and back."), "Ping-Pong's words");
        check(!ModularWords.plainWords(brush()).contains("Greys"), "off says nothing");
        for (PartDial d : PartDial.SHIFTS) if (d.choice()) for (String option : d.choices) check(!option.contains(";") && Character.isUpperCase(option.charAt(0)), d.label + "'s options are Title Case");
    }

    /** The Control card's first rows for {@code b} (Response, Fast and, for a nib's line, Tilt Shading), then {@code more}. */
    static java.util.List<PartDial> handHead(ModularBrush b, PartDial... more) {
        java.util.List<PartDial> rows = new java.util.ArrayList<>(Arrays.asList(PartDial.GAIN, PartDial.DIR));
        if (BrushLab.tiltShades(b)) rows.add(PartDial.SIDE_SHADE);
        rows.addAll(Arrays.asList(more));
        return rows;
    }
    static void lab() {
        BrushLab lab = new BrushLab(BrushLabChecks.slots(), 2, new Random(8));
        ModularBrush.Part hand = lab.parts().part(BrushPart.HAND);
        hand.set(PartDial.SHIFT, SwatchPicker.OFF);
        check(BrushLab.handRows(lab.parts(), false).equals(handHead(lab.parts(), PartDial.SHIFT)), "off, the Control card adds Grey Shift's row");
        hand.set(PartDial.SHIFT, SwatchPicker.PATTERN);
        check(BrushLab.handRows(lab.parts(), false).equals(handHead(lab.parts(), PartDial.SHIFT, PartDial.SHIFT_ORDER, PartDial.SHIFT_GREYS, null)), "a Pattern: its Order, Greys and set");
        check(lab.toggleGrey(1) && !SwatchPicker.in(lab.parts(), 1) && lab.status.equals("3 of 4 greys"), "a tap leaves a grey out: " + lab.status);
        check(lab.toggleGrey(1) && SwatchPicker.in(lab.parts(), 1), "and takes it in again");
        lab.undo();
        check(!SwatchPicker.in(lab.parts(), 1), "Undo takes it back");
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
