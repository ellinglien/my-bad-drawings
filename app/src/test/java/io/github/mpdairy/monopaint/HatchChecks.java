package io.github.mpdairy.monopaint;

/**
 * Hatch ({@link HatchStamper}, the new modules spec): the web lab's lines on the lab's strokes, the same however the
 * samples come, crossed a second and a third way by pressure into the spec's grey bands, crisp, fixed strokes lining
 * up, quick strokes ending as live ones, rare rolls that change nothing else, its codes and its words. The harness is
 * {@code StippleChecks}'.
 */
public final class HatchChecks {
    static final int W = StippleChecks.W, H = StippleChecks.H;

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        lab(); deterministic(); bands(); crisp(); quick(); rolls(); codes(); words();
        System.out.println("PASS: hatching lays the web lab's lines (coverage, area and centre on the lab's strokes, fixed on the page and following an arc); the same samples lay the same lines batched 1, 3 or 1000 at a time; pressure crosses them a second and a third way, a band about 21, 37 and 50% dark; only their edges are in-between greys; overlapping fixed strokes line up; quick strokes show as the same samples drawn live, every layer as live composed once; Anything goes, Dip pen, Fineliner and Pencil roll Hatch at the lab's odds from the seed with every other roll as before, no other style; its codes are the lab's; \"This brush\" and captions say what it is");
    }

    static ModularBrush fixed() { return StippleChecks.mk(PartChoice.HATCH, PartChoice.STEADY, PartDial.PITCH, .35, PartDial.LINE_W, .25, PartDial.HANGLE, 45, PartDial.HEAD_TURN, 1); }
    static void lab() {
        StippleChecks.like(StippleChecks.stamp(fixed(), 40, StippleChecks.line(20, 120, 460, 120, 160, u -> .2 + .78 * u), 3, false), "Hatching fixed at 45°", 6571.629, 9157, 280.3279, 119.4839);
        ModularBrush follows = StippleChecks.mk(PartChoice.HATCH, PartChoice.STEADY, PartDial.PITCH, .3, PartDial.LINE_W, .3, PartDial.HANGLE, 90, PartDial.HEAD_TURN, 0, PartDial.LAYERS, 2);
        StippleChecks.like(StippleChecks.stamp(follows, 40, StippleChecks.arc(240, 240, 180, Math.PI * 1.05, Math.PI * 1.95, 200, u -> .3 + .6 * u), 3, false),
                "Hatching across an arc, following it, two layers", 7848.34, 11081, 257.6611, 114.1857);
    }
    static void deterministic() {
        ModularBrush follows = fixed();
        follows.part(BrushPart.TIP).set(PartDial.HEAD_TURN, 0);
        for (ModularBrush b : new ModularBrush[]{fixed(), follows, BristleSwarmChecks.preset("Hatch pen")}) {
            java.util.List<double[]> s = StippleChecks.line(20, 100, 460, 160, 160, u -> .2 + .75 * u);
            FloatTiles a = StippleChecks.stamp(b, 40, s, 3, false);
            check(StippleChecks.same(a, StippleChecks.stamp(b, 40, s, 1, false)) && StippleChecks.same(a, StippleChecks.stamp(b, 40, s, 1000, false)), "The same samples lay the same lines, however they're batched");
        }
    }

    /** The mean darkness, 0–1, of a straight hatch's band core (rows within {@code half} of the middle, columns 120–360). */
    static double tone(ToneDocument d, int half) {
        double sum = 0; int n = 0;
        for (int y = H / 2 - half; y <= H / 2 + half; y++) for (int x = 120; x <= 360; x++) { sum += 1 - d.compositeTone(x, y) / 255.0; n++; }
        return sum / n;
    }
    static ToneDocument straight(ModularBrush b, double size, double travel) { return StippleChecks.page(b, size, StippleChecks.line(20, H / 2.0, 460, H / 2.0, 160, u -> travel), 4); }
    static void bands() {
        ModularBrush plain = StippleChecks.mk(PartChoice.HATCH, PartChoice.STEADY);
        double one = tone(straight(plain, 60, .3), 20), two = tone(straight(plain, 60, .6), 20), three = tone(straight(plain, 60, .9), 20);
        check(Math.abs(one - .21) < .04 && Math.abs(two - .37) < .04 && Math.abs(three - .50) < .04,
                "Pressure crosses the lines: a band " + Math.round(one * 100) + ", " + Math.round(two * 100) + " and " + Math.round(three * 100) + "% dark with one, two and three sets (the spec: 21, 37, 50)");
        ModularBrush twoLayers = plain.copy(); twoLayers.part(BrushPart.TIP).set(PartDial.LAYERS, 2);
        check(Math.abs(tone(straight(twoLayers, 60, .9), 20) - two) < .01, "Layers 2 stops at two directions");
        int light = StippleChecks.dark(straight(fixed(), 60, .3), 128, 0, H - 1), pressed = StippleChecks.dark(straight(fixed(), 60, .95), 128, 0, H - 1);
        check(pressed > 1.6 * light, "Pressed, more dark pixels: " + light + " → " + pressed + " (the lab: ×2.4)");
    }
    static void crisp() {
        ToneDocument d = straight(fixed(), 60, .95);
        int dark = 0, grey = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { int t = d.compositeTone(x, y); if (t < 128) dark++; if (t > 34 && t < 221) grey++; }
        check(grey < .9 * dark, "Crisp lines: " + grey + " in-between greys to " + dark + " dark pixels");
        // Two fixed strokes overlapping line up: the second adds little in the rows both cover.
        ToneDocument a = InkWashLayers.blank(W, H), b = InkWashLayers.blank(W, H);
        for (int k = 0; k < 2; k++) {
            ToneDocument into = k == 0 ? a : b;
            for (int y : k == 0 ? new int[]{110} : new int[]{110, 125}) {
                ModularStroke st = ModularStroke.start(into, fixed(), 60, 1, null);
                StippleChecks.feed(st, StippleChecks.line(20, y, 460, y, 160, u -> .3), 4);
                st.finish();
            }
        }
        int one = StippleChecks.dark(a, 128, 101, 134), two = StippleChecks.dark(b, 128, 101, 134);
        check(two < 1.35 * one, "Overlapping fixed strokes line up: " + one + " → " + two + " dark pixels where both lay");
    }
    static void quick() {
        for (ModularBrush b0 : new ModularBrush[]{BristleSwarmChecks.preset("Hatch pen"), fixed()})
            for (PartChoice life : new PartChoice[]{PartChoice.NONE, PartChoice.FAST_WET, PartChoice.DRYING}) {
                ModularBrush b = b0.copy();
                ModularBrush.Part p = new ModularBrush.Part(life);
                if (life == PartChoice.DRYING) p.set(PartDial.SETTLE, .5);
                b.set(BrushPart.LIFE, p);
                for (double size : new double[]{30, 160}) {
                    String what = b.name + " " + life + " " + size + " px";
                    // A pixel takes its nearest dab's lines, so its coverage can fall as the pen passes; a layer keeps the
                    // most a gesture gave it, so (as Fast-wet's window) live strokes show the same tones, their layers'
                    // coverage as when the whole stroke is composed once.
                    ToneDocument q = QuickStrokeChecks.quick(b, size, null), live = QuickStrokeChecks.live(b, size, false, null);
                    check(QuickStrokeChecks.sameShown(q, live) && QuickStrokeChecks.same(q, QuickStrokeChecks.live(b, size, true, null)), what + ": quick shows as live, every layer as live composed once");
                }
            }
        ResolvedBrush small = ResolvedBrush.of(BristleSwarmChecks.preset("Hatch pen"), 20, 1), wide = ResolvedBrush.of(BristleSwarmChecks.preset("Hatch pen"), 60, 1), big = ResolvedBrush.of(BristleSwarmChecks.preset("Hatch pen"), 160, 1);
        check(!QuickStroke.quick(small) && QuickStroke.quick(wide) && QuickStroke.keepsUpTo(wide) == QuickStroke.HATCH && QuickStroke.cost(big) > 1 && QuickStroke.cost(wide) < 1,
                "Hatching goes quick past the panel's reach, and past what the Manta keeps up with live: " + ModularStroke.reach(small) + " / " + ModularStroke.reach(wide) + " / " + ModularStroke.reach(big) + " px");
    }
    static void rolls() {
        int[] hits = StippleChecks.rolls(PartChoice.HATCH);
        // Hatch is decided after Stipple: its odds are of the rolls Stipple left.
        for (ModularStyle s : ModularStyle.all()) StippleChecks.odds(hits, s, s.hatchOdds(), s.stippleOdds() == 0 ? 1 : 1 - 1.0 / s.stippleOdds(), "Hatch");
        check(ModularStyle.named("Dip pen").hatchOdds() == 30 && ModularStyle.named("Pencil").hatchOdds() == 35 && ModularStyle.named("Fineliner").hatchOdds() == 35, "The lab's odds");
        ModularStyle pencil = ModularStyle.named("Pencil");
        for (int i = 0; i < 3000; i++) {
            ModularBrush b = pencil.roll(new java.util.Random(4000L * (pencil.index + 1) + i));
            if (b.choice(BrushPart.TIP) != PartChoice.HATCH) continue;
            ModularBrush.Part t = b.part(BrushPart.TIP);
            check(t.get(PartDial.PITCH) >= .2 && t.get(PartDial.PITCH) <= .6 && t.get(PartDial.LINE_W) >= .15 && t.get(PartDial.LINE_W) <= .45
                    && t.get(PartDial.HANGLE) >= 30 && t.get(PartDial.HANGLE) <= 60, "A rolled hatch's dials: " + ModularCode.encode(b));
        }
    }
    static void codes() {
        String lab = "hatch-pen.M08at7x901zzi0_000zz000000000000000_0_000_1jz_0000000000000000000nz0.x3";
        ModularBrush b = ModularCode.decode(lab);
        check(b != null && b.choice(BrushPart.TIP) == PartChoice.HATCH && ModularCode.encode(b).equals(lab) && (int)b.part(BrushPart.TIP).get(PartDial.HEAD_TURN) == 1
                && Math.abs(b.part(BrushPart.TIP).get(PartDial.HANGLE) - 45) < .1, "The lab's Hatch pen reads and writes back, fixed at 45°");
        check(ModularCode.encode(StippleChecks.mk(PartChoice.HATCH, PartChoice.STEADY)).contains(".M08eeat901zzi0_"), "A fresh hatch's tip is the lab's 8eeat901zzi0");
    }
    static void words() {
        ModularBrush pen = BristleSwarmChecks.preset("Hatch pen"), pencil = pen.copy();
        pencil.set(BrushPart.MEDIUM, ModularBrush.switchTo(pen.part(BrushPart.MEDIUM), PartChoice.GRAPHITE));
        check(ModularWords.plainWords(pen).equals("Ink hatching.") && ModularWords.plainWords(pencil).startsWith("Pencil hatching."), "Hatch pen: " + ModularWords.plainWords(pen) + " / " + ModularWords.plainWords(pencil));
        check(ModularWords.caption(pen).equals(java.util.Arrays.asList("Fineliner", "hatch")), "Caption: " + ModularWords.caption(pen));
        check(ModularWords.choiceWords(pen, BrushPart.TIP).equals("Hatch") && PartChoice.HATCH.labelOf(PartDial.HEAD_TURN).equals("Lines Follow") && PartDial.HEAD_TURN.shown(1).equals("Page")
                && PartDial.HANGLE.shown(45).equals("45°") && PartDial.LAYERS.shown(3).equals("3"), "The card says Hatch; Lines Follow Page, Line Angle 45°, Layers 3");
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
