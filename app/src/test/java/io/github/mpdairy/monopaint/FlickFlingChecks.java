package io.github.mpdairy.monopaint;

import java.util.Random;

/**
 * Flick and Fling (the mischief modules spec, Stage 2; {@link FlickFling}): a quick lift carries the stroke on and a slow
 * one doesn't; a dead stop flings drops ahead and a smooth stroke doesn't, while the pen is still down or at a lift right
 * after the stop; the same samples land the same drops; the continuation and the drops are the stroke's one Undo step;
 * quick ends as live; zoomed, the hand's speed on the glass decides; codes round-trip with every older code byte for
 * byte; rolls are rare, from the seed, every other roll as before; words.
 */
public final class FlickFlingChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        codes(); rolls(); words(); off();
        flicks(); slow(); flings(); smooth(); liftFling(); seeded(); undo(); quick(); zoomed(); screened();
        System.out.println("PASS: Flick (every tip's last dial) and Fling (every Blend medium's last) are written only when on, every older code byte for byte, and round-trip; they roll rarely from the seed "
                + "(Flick 1 in 12 for Brush pen, Ink wash and Anything goes, 1 in 20 for Watercolor; Fling 1 in 15 for Dip pen, Brush pen and Anything goes), every other roll as before; "
                + "\"This brush\" and the modules say so; a brush with both off watches nothing; a quick lift carries the stroke on, tapering, in Instant, Quick and Slow alike, and a slow lift doesn't; "
                + "a hard stop held with the pen down throws drops ahead before the lift, nearest first, and pools at the stop, and so does a lift just after one; a smooth stroke throws none; "
                + "the same samples land the same drops; one Undo takes the stroke, its flick and its drops; quick ends as live, a flicked smudge too; zoomed in, the hand's speed on the glass decides; a printed flick stays black and white");
    }

    static final int W = 900, H = 600;
    static ModularBrush swell() { return FloatChecks.preset("Swell brush"); }
    /** {@code b} with Flick and Fling at {@code flick} and {@code fling}, and {@code life} (null: as it is). */
    static ModularBrush with(ModularBrush b, double flick, double fling, PartChoice life) {
        ModularBrush c = b.copy();
        c.part(BrushPart.TIP).set(PartDial.FLICK, flick);
        c.part(BrushPart.MEDIUM).set(PartDial.FLING, fling);
        if (life != null) c.set(BrushPart.LIFE, new ModularBrush.Part(life));
        return c.normalize();
    }
    /** A straight stroke from {@code x0} to {@code x1} along {@code y} at {@code speed} page px/ms, a sample every 2 ms; lifted at its last. */
    static double[][] line(double x0, double x1, double y, double speed) {
        int n = (int) Math.ceil((x1 - x0) / (speed * 2));
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) o[i] = new double[]{x0 + (x1 - x0) * i / n, y, .7, 1000 + 2 * i};
        return o;
    }
    /** mischief-lab.html's dash: speeding up from {@code x0} to {@code x1} (about 5 px/ms at the end), then held {@code hold} ms with a hair's jitter, a sample every 4 ms. */
    static double[][] dash(double x0, double x1, double y, int hold) {
        java.util.List<double[]> o = new java.util.ArrayList<>();
        for (int i = 0; i <= 26; i++) { double u = Math.pow(i / 26.0, 1.7); o.add(new double[]{x0 + (x1 - x0) * u, y, .45 + .4 * i / 26, 1000 + 4 * i}); }
        for (int k = 1; k * 4 <= hold; k++) o.add(new double[]{x1 + .3 * Math.sin(k), y + .3 * Math.cos(k * 1.3), .85, 1000 + 104 + 4 * k});
        return o.toArray(new double[0][]);
    }
    static ToneDocument draw(ModularBrush b, double size, double[][] path, Boolean quick) {
        ToneDocument d = InkWashLayers.blank(W, H);
        DrawingStroke s = quick == null ? QuickStroke.start(d, b, size, 1, null) : quick ? QuickStroke.quickly(d, b, size, 1, null) : ModularStroke.start(d, b, size, 1, null);
        CarrySmudgeChecks.feed(s, path);
        return d;
    }
    /** How many pixels in the box are darker than the paper. */
    static int ink(ToneDocument d, int x0, int x1, int y0, int y1) {
        int n = 0;
        for (int y = Math.max(0, y0); y <= Math.min(H - 1, y1); y++) for (int x = Math.max(0, x0); x <= Math.min(W - 1, x1); x++) if (d.compositeTone(x, y) < 250) n++;
        return n;
    }
    /** The furthest column right of {@code from} with ink, or {@code from} for none. */
    static int reach(ToneDocument d, int from) {
        int most = from;
        for (int y = 0; y < H; y++) for (int x = from; x < W; x++) if (d.compositeTone(x, y) < 250) most = Math.max(most, x);
        return most;
    }

    // ---- codes, rolls, words ----

    static void codes() {
        for (PartChoice c : BrushPart.TIP.choices()) check(c.dials.get(c.dials.size() - 1) == PartDial.FLICK, c + ": Flick last");
        for (PartChoice c : BrushPart.MEDIUM.choices())
            check(c.has(PartDial.M_BLEND) == c.has(PartDial.FLING) && (!c.has(PartDial.FLING) || c.dials.get(c.dials.size() - 1) == PartDial.FLING), c + ": Fling last of every medium with Blend");
        check(ModularCode.ADDED.contains(PartDial.FLICK) && ModularCode.ADDED.contains(PartDial.FLING) && PartDial.FLICK.key.equals("flick") && PartDial.FLING.key.equals("fling")
                && PartDial.FLICK.label.equals("Flick") && PartDial.FLING.label.equals("Fling") && PartDial.FLICK.def == 0 && PartDial.FLING.def == 0, "keys, labels, off by default, written only when on");
        for (ToolPresets.Preset p : ToolPresets.all()) {
            check((ModularCode.encode(ModularCode.decode(p.code)).equals(p.code) || HandHatchChecks.handHatch(p.code)), p.name + "'s code reads and writes byte for byte");
            ModularBrush b = ModularCode.decode(p.code);
            check(b.part(BrushPart.TIP).get(PartDial.FLICK, 0) == 0 && b.part(BrushPart.MEDIUM).get(PartDial.FLING, 0) == 0, p.name + " neither flicks nor flings");
        }
        ModularBrush b = swell();
        String before = ModularCode.encode(b);
        ModularBrush both = with(b, .6, .4, null);
        String code = ModularCode.encode(both);
        ModularBrush back = ModularCode.decode(code);
        check(Math.abs(back.part(BrushPart.TIP).get(PartDial.FLICK) - .6) < 1e-3 && Math.abs(back.part(BrushPart.MEDIUM).get(PartDial.FLING) - .4) < 1e-3 && ModularCode.encode(back).equals(code), "both round-trip: " + code);
        check(!code.equals(before) && ModularCode.encode(with(b, 0, 0, null)).equals(before), "off, the code is as before");
        // Flick adds two digits to the tip; Fling writes the medium's dials up to it.
        check(seg(code, 0).equals(seg(before, 0) + seg(code, 0).substring(seg(before, 0).length())) && seg(code, 0).length() == seg(before, 0).length() + 2, "Flick on the tip's end: " + seg(code, 0));
        check(seg(code, 1).startsWith(seg(before, 1)), "Fling on the medium's end: " + seg(code, 1));
    }
    private static String seg(String code, int part) { return code.substring(code.indexOf(".M") + 3, code.lastIndexOf('.')).split("_")[part]; }

    static void rolls() {
        int flicked = 0, flung = 0, flickN = 0, flingN = 0;
        for (ModularStyle s : ModularStyle.all()) for (int i = 0; i < 600; i++) {
            ModularBrush b = s.roll(new Random(77000L * (s.index + 1) + i)), without;
            ModularStyle.flickRolls = false; ModularStyle.flingRolls = false;
            try { without = s.roll(new Random(77000L * (s.index + 1) + i)); } finally { ModularStyle.flickRolls = true; ModularStyle.flingRolls = true; }
            double flick = b.part(BrushPart.TIP).get(PartDial.FLICK, 0), fling = b.part(BrushPart.MEDIUM).get(PartDial.FLING, 0);
            check(without.part(BrushPart.TIP).get(PartDial.FLICK, 0) == 0 && without.part(BrushPart.MEDIUM).get(PartDial.FLING, 0) == 0, "frozen, nothing flicks or flings");
            if (flick > 0) { flicked++; check(s.flickOdds() > 0 && flick >= .35 && flick <= .85, s.name + " rolls Flick only with odds: " + flick); }
            if (fling > 0) { flung++; check(s.flingOdds() > 0 && fling >= .3 && fling <= .8, s.name + " rolls Fling only with odds: " + fling); }
            if (s.flickOdds() > 0) flickN++;
            if (s.flingOdds() > 0) flingN++;
            // Every other dial of every part as rolled without them.
            ModularBrush c = b.copy();
            if (c.part(BrushPart.TIP).has(PartDial.FLICK)) c.part(BrushPart.TIP).set(PartDial.FLICK, 0);
            if (c.part(BrushPart.MEDIUM).has(PartDial.FLING)) c.part(BrushPart.MEDIUM).set(PartDial.FLING, 0);
            for (BrushPart part : BrushPart.values()) check(c.part(part).sameAs(without.part(part)), s.name + " " + part + " rolls as before");
            check(b.name.equals(without.name) || flick > 0 || fling > 0, s.name + "'s name as before");
        }
        // Brush pen, Ink wash and Anything goes 1 in 12, Watercolor 1 in 20: 2,400 rolls, about 180; Fling 1,800 at 1 in 15, about 120.
        double flickWant = 600 * 3 / 12.0 + 600 / 20.0, flingWant = flingN / 15.0;
        check(Math.abs(flicked - flickWant) < 4 * Math.sqrt(flickWant) && flickN == 2400, "Flick's odds: " + flicked + " of " + flickN + " (about " + flickWant + ")");
        check(Math.abs(flung - flingWant) < 4 * Math.sqrt(flingWant) && flingN == 1800, "Fling's odds: " + flung + " of " + flingN + " (about " + flingWant + ")");
        // A variation never turns either on.
        ModularBrush plain = swell();
        for (int seed = 0; seed < 20; seed++) for (ModularVariations.Made m : ModularVariations.make(plain, new ModularLocks(), new Random(seed))) {
            ModularBrush v = m.brush;
            check(v.part(BrushPart.TIP).get(PartDial.FLICK, 0) == 0 && v.part(BrushPart.MEDIUM).get(PartDial.FLING, 0) == 0, "a variation leaves Flick and Fling off");
        }
    }

    static void words() {
        ModularBrush b = swell();
        check(ModularWords.mischiefWords(b) == null && !ModularWords.plainWords(b).contains("Flick"), "a brush that does neither says nothing");
        check(ModularWords.plainWords(with(b, .6, 0, null)).contains("Flicks on a quick lift.") && ModularWords.plainWords(with(b, 0, .6, null)).contains("Flings drops on a hard stop.")
                && ModularWords.plainWords(with(b, .6, .6, null)).contains("Flicks on a quick lift, flings drops on a hard stop."), "This brush says it plainly: " + ModularWords.plainWords(with(b, .6, .6, null)));
        check(ModularWords.sentences(ModularWords.plainWords(with(b, .6, .6, null))).get(1).startsWith("Flicks"), "right after what it is");
        check(ModularWords.choiceWords(with(b, .6, 0, null), BrushPart.TIP).equals("Round") && ModularWords.inside(BrushPart.TIP).endsWith("flick") && ModularWords.inside(BrushPart.MEDIUM).endsWith("fling"),
                "the Tip module has no room for Flick (its words are the tip's); the Tip's and Medium's say they hold them: " + ModularWords.inside(BrushPart.TIP));
        check(ModularWords.choiceWords(with(b, 0, .6, null), BrushPart.MEDIUM).equals("Ink, fling"), "the Medium module: " + ModularWords.choiceWords(with(b, 0, .6, null), BrushPart.MEDIUM));
        check(PartDial.FLICK.shown(0).equals("off") && PartDial.FLING.shown(.5).equals("0.50"), "the dials read as the others");
    }
    /** Both off: no watcher, and the stroke is the one it was. */
    static void off() {
        for (ToolPresets.Preset p : ToolPresets.all()) check(FlickFling.of(ResolvedBrush.of(p.brush().parts, 12, 1)) == null, p.name + " watches nothing");
        ModularBrush b = swell();
        check(!ModularStroke.start(InkWashLayers.blank(64, 64), b, 12, 1, null).buildsWhileHeld() && ModularStroke.start(InkWashLayers.blank(64, 64), with(b, .5, 0, null), 12, 1, null).buildsWhileHeld(),
                "only a brush that flicks or flings hears a resting pen");
    }

    // ---- Flick ----

    static void flicks() {
        for (PartChoice life : new PartChoice[]{PartChoice.NONE, PartChoice.FAST_WET, PartChoice.DRYING}) {
            ModularBrush on = with(swell(), .7, 0, life), off = with(swell(), 0, 0, life);
            double[][] quick = line(100, 400, 300, 3);
            ToneDocument a = draw(on, 12, quick, false), b = draw(off, 12, quick, false);
            int beyond = ink(a, 412, W - 1, 200, 400), none = ink(b, 412, W - 1, 200, 400);
            // Flick × 3 px/ms × 85 on the glass: about 180 px past the lift, tapering.
            int far = reach(a, 400);
            check(reach(b, 400) < 430 && beyond > 300 + none && far > 520 && far < 620, life + ": a quick lift carries on past it (" + beyond + " px of ink, to x " + far + "); without Flick, " + none + " (its own end)");
            int near = ink(a, 410, 440, 280, 320), end = ink(a, far - 30, far, 270, 330);
            check(near > end, life + ": tapering (" + near + " px near the lift, " + end + " at its end)");
        }
        // A Watercolor wash flicks its body.
        ModularBrush wash = with(FloatChecks.preset("Soft wash"), .6, 0, PartChoice.NONE);
        ToneDocument w = draw(wash, 60, line(100, 400, 300, 3), false);
        check(reach(w, 440) > 520, "a wash's body carries on too: to " + reach(w, 440));
    }
    static void slow() {
        // Slow (.25 px/ms): nothing, byte for byte.
        ModularBrush on = with(swell(), 1, 0, PartChoice.NONE), off = with(swell(), 0, 0, PartChoice.NONE);
        double[][] slow = new double[121][];
        for (int i = 0; i <= 120; i++) slow[i] = new double[]{200 + i * 1.0, 300, .7, 1000 + 4 * i};
        check(QuickStrokeChecks.same(draw(on, 12, slow, false), draw(off, 12, slow, false)), "a slow lift doesn't flick");
        // A quick stroke held still before the lift doesn't either.
        double[][] quick = line(100, 400, 300, 3), held = java.util.Arrays.copyOf(quick, quick.length + 20);
        for (int k = 1; k <= 20; k++) held[quick.length + k - 1] = new double[]{400, 300, .7, quick[quick.length - 1][3] + 5 * k};
        check(QuickStrokeChecks.same(draw(on, 12, held, false), draw(off, 12, held, false)), "stopped before the lift, no flick");
    }

    // ---- Fling ----

    static void flings() {
        ModularBrush on = with(swell(), 0, .8, PartChoice.NONE), off = with(swell(), 0, 0, PartChoice.NONE);
        // Held 300 ms: every drop lands while the pen is still down.
        double[][] stop = dash(100, 420, 300, 300);
        ToneDocument d = InkWashLayers.blank(W, H);
        ModularStroke s = ModularStroke.start(d, on, 12, 1, null);
        boolean before = false;
        for (double[] p : stop) {
            s.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), 0, 0, (long) p[3]);
            s.present();
            if (p[3] < 1000 + 104 + 44 && ink(d, 432, W - 1, 0, H - 1) > 0) before = true;
        }
        int down = ink(d, 432, W - 1, 0, H - 1);
        s.finish();
        ToneDocument plain = draw(off, 12, stop, false);
        check(!before && down > 0 && ink(plain, 432, W - 1, 0, H - 1) == 0, "a dead stop flings drops ahead while the pen is down, after 45 ms still (" + down + " px); without Fling none");
        check(ink(d, 432, W - 1, 0, H - 1) == down, "all of them before the lift, nothing more at it");
        // A little ink pools at the stop.
        check(ink(d, 405, 430, 280, 320) > ink(plain, 405, 430, 280, 320), "ink pools at the stop");
        // Nearest first: as the clock runs, the drops land further out.
        FlickFling ff = FlickFling.of(ResolvedBrush.of(on, 12, 1));
        StrokePoints pts = new StrokePoints();
        for (double[] p : stop) { pts.add((float) p[0], (float) p[1], (float) p[2], (long) p[3], 0, 0); ff.sampled(pts, (float) p[0], (float) p[1], (long) p[3]); }
        java.util.List<FlickFling.Drop> all = ff.land(Long.MAX_VALUE);
        check(all.size() > 4, "drops: " + all.size());
        for (int i = 2; i < all.size(); i++) {
            FlickFling.Drop a = all.get(i - 1), b = all.get(i);
            check(a.at <= b.at && Math.hypot(a.x - 420, a.y - 300) <= Math.hypot(b.x - 420, b.y - 300) + 30, "nearest lands first");
        }
        for (FlickFling.Drop drop : all.subList(1, all.size())) check(drop.x > 425 && Math.abs(Math.atan2(drop.y - 300, drop.x - 420)) < .6, "ahead of the stop, within the throw");
    }
    static void smooth() {
        ModularBrush on = with(swell(), 0, 1, PartChoice.NONE), off = with(swell(), 0, 0, PartChoice.NONE);
        // Lifted at speed: a smooth stroke, no fling (and no Flick to carry on).
        check(QuickStrokeChecks.same(draw(on, 12, line(100, 400, 300, 3), false), draw(off, 12, line(100, 400, 300, 3), false)), "a smooth quick stroke throws nothing");
        // Slowing gently to a stop: no hard stop.
        double[][] ease = new double[150][];
        for (int i = 0; i < 150; i++) { double u = i / 149.0; ease[i] = new double[]{100 + 200 * (1 - (1 - u) * (1 - u)), 300, .7, 1000 + 4 * i}; }
        double[][] held = java.util.Arrays.copyOf(ease, ease.length + 25);
        for (int k = 1; k <= 25; k++) held[ease.length + k - 1] = new double[]{300, 300, .7, ease[ease.length - 1][3] + 4 * k};
        check(QuickStrokeChecks.same(draw(on, 12, held, false), draw(off, 12, held, false)), "a stroke slowing to a stop throws nothing");
    }
    static void liftFling() {
        // Stopped dead and lifted within 45 ms: flung at the lift.
        ModularBrush on = with(swell(), .7, .8, PartChoice.NONE), off = with(swell(), 0, 0, PartChoice.NONE);
        double[][] stop = dash(100, 420, 300, 24);
        ToneDocument d = draw(on, 12, stop, false), plain = draw(off, 12, stop, false);
        check(ink(d, 432, W - 1, 0, H - 1) > 0 && ink(plain, 432, W - 1, 0, H - 1) == 0, "a lift right after a dead stop flings, and doesn't flick");
    }
    static void seeded() {
        ModularBrush on = with(swell(), .7, .8, PartChoice.NONE);
        double[][] stop = dash(100, 420, 300, 120);
        check(QuickStrokeChecks.same(draw(on, 12, stop, false), draw(on, 12, stop, false)) && QuickStrokeChecks.same(draw(on, 12, line(100, 400, 300, 3), false), draw(on, 12, line(100, 400, 300, 3), false)),
                "the same samples land the same flick and drops");
        // Another brush seed, other drops.
        ModularBrush other = on.copy(on.seed ^ 0x55);
        check(!QuickStrokeChecks.same(draw(on, 12, stop, false), draw(other, 12, stop, false)), "seeded by the brush and stroke");
    }
    static void undo() {
        for (PartChoice life : new PartChoice[]{PartChoice.NONE, PartChoice.DRYING}) {
            ModularBrush on = with(swell(), .7, .8, life);
            for (double[][] path : new double[][][]{line(100, 400, 300, 3), dash(100, 420, 300, 120)}) {
                ToneDocument d = draw(on, 12, path, false);
                int was = ink(d, 0, W - 1, 0, H - 1);
                check(d.undo() && ink(d, 0, W - 1, 0, H - 1) == 0 && !d.undo(), life + ": one Undo takes the stroke, its flick and drops");
                check(d.redo() && ink(d, 0, W - 1, 0, H - 1) == was, life + ": Redo brings them back");
            }
        }
    }
    static void quick() {
        for (PartChoice life : new PartChoice[]{PartChoice.NONE, PartChoice.DRYING}) {
            ModularBrush on = with(swell(), .7, .8, life);
            for (double[][] path : new double[][][]{line(100, 400, 300, 3), dash(100, 420, 300, 120)})
                check(QuickStrokeChecks.same(draw(on, 12, path, true), draw(on, 12, path, false)), life + ": quick ends as live");
        }
        // A smudge flicked on past its samples: the quick render's page is read as far as a flick reaches.
        ModularBrush stump = with(FloatChecks.preset("Blend stump"), .8, 0, null);
        ToneDocument[] two = new ToneDocument[2];
        for (int k = 0; k < 2; k++) {
            two[k] = InkWashLayers.blank(W, H);
            CarrySmudgeChecks.lay(two[k], ModularSizes.inks(stump) ? InkWashLayers.INK : InkWashLayers.WASH, (x, y) -> x >= 380 && x < 520 && y >= 250 && y < 350 ? 60 : 255);
            CarrySmudgeChecks.feed(k == 0 ? QuickStroke.quickly(two[k], stump, 40, 1, null) : ModularStroke.start(two[k], stump, 40, 1, null), line(100, 360, 300, 3));
        }
        check(QuickStrokeChecks.same(two[0], two[1]) && CarrySmudgeChecks.mean(two[1], 390, 510, 280, 320) > 61, "a smudge's flick smears past the lift, quick as live");
    }
    static void zoomed() {
        // At 200% the same hand covers half the page at the same speed on the glass: half as far on the page.
        ModularBrush on = with(swell(), .7, 0, PartChoice.NONE);
        ToneDocument fitted = InkWashLayers.blank(W, H), zoomed = InkWashLayers.blank(W, H);
        DrawingStroke a = ModularStroke.start(fitted, on, 12, 1, null), b = ModularStroke.start(zoomed, on, 12, 1, null);
        b.zoom(2);
        CarrySmudgeChecks.feed(a, line(100, 400, 300, 3));
        CarrySmudgeChecks.feed(b, line(100, 250, 300, 1.5));
        int far = reach(fitted, 400) - 400, half = reach(zoomed, 250) - 250;
        check(Math.abs(half - far / 2.0) < 12, "zoomed to 200%, half as far on the page: " + half + " px for " + far);
        // At 400% a page crawl (.3 px/ms, under the lift's speed read on the page) is 1.2 px/ms on the glass: it flicks.
        ToneDocument slow = InkWashLayers.blank(W, H);
        DrawingStroke c = ModularStroke.start(slow, on, 12, 1, null);
        c.zoom(4);
        CarrySmudgeChecks.feed(c, line(100, 250, 300, .3));
        check(reach(slow, 252) > 262, "zoomed to 400%, a page crawl that is quick on the glass flicks");
    }
    static void screened() {
        ModularBrush on = with(swell(), .8, .8, PartChoice.NONE);
        on.part(BrushPart.MEDIUM).set(PartDial.SCREEN, 1);
        ToneDocument d = draw(on, 12, line(100, 400, 300, 3), false);
        boolean bw = true;
        for (int y = 200; y < 400; y++) for (int x = 405; x < W; x++) { int t = d.compositeTone(x, y); if (t != 0 && t != 255) bw = false; }
        check(bw && ink(d, 405, W - 1, 200, 400) > 0, "a halftone flick prints black and white");
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
