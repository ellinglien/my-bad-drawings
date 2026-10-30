package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Stipple ({@link StippleStamper}, the new modules spec): the web lab's dots on the lab's strokes, the same however the
 * samples come, more with pressure and with each pass, Build-up's dots while the pen rests, quick strokes ending as live
 * ones, rare rolls that change nothing else, its codes and its words. {@code HatchChecks} uses its harness.
 */
public final class StippleChecks {
    static final int W = 480, H = 240;

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        lab(); deterministic(); pressure(); passes(); buildUp(); quick(); rolls(); codes(); words();
        System.out.println("PASS: stipple lays the web lab's dots (coverage, area and centre on the lab's strokes: a pen line, a sponge, an even stipple, Build-up held a second, the owner's Zealous shallot's sponged wash); the same samples lay the same dots batched 1, 3 or 1000 at a time; pressure packs them closer and each pass adds more; Build-up keeps laying dots while the pen rests, faster on fast, none off or untimed; quick strokes end as the same samples drawn live, Build-up and all; Anything goes, Fineliner and Watercolor roll Stipple at the lab's odds from the seed with every other roll as before, no other style, never a variation; its codes are the lab's and Build-up is written only when on; \"This brush\" and captions say what it is");
    }

    // ---- the lab's strokes (golden.cjs: every value a float32, x and y on quarter pixels, travel on 1/256) ----

    interface Ramp { double at(double u); }
    static double q4(double v) { return Math.round(v * 4) / 4.0; }
    static double q256(double v) { return Math.round(v * 256) / 256.0; }
    /** {x, y, travel, time} samples along a line, every 6 ms from 1000. */
    static List<double[]> line(double x0, double y0, double x1, double y1, int n, Ramp p) {
        List<double[]> o = new ArrayList<>();
        for (int k = 0; k <= n; k++) o.add(new double[]{q4(x0 + (x1 - x0) * k / n), q4(y0 + (y1 - y0) * k / n), q256(p.at((double)k / n)), 1000 + 6 * k});
        return o;
    }
    /** As {@link #line}, along an arc. */
    static List<double[]> arc(double cx, double cy, double r, double a0, double a1, int n, Ramp p) {
        List<double[]> o = new ArrayList<>();
        for (int k = 0; k <= n; k++) { double a = a0 + (a1 - a0) * k / n; o.add(new double[]{q4(cx + r * Math.cos(a)), q4(cy + r * Math.sin(a)), q256(p.at((double)k / n)), 1000 + 6 * k}); }
        return o;
    }
    /** golden.cjs mk: a Fineliner ink, Quick, seed 17, with {@code tip}'s dials as given. */
    static ModularBrush mk(PartChoice tip, PartChoice hand, Object... dials) {
        ModularBrush b = new ModularBrush("g", 17, ModularStyle.named("Fineliner"));
        ModularBrush.Part t = new ModularBrush.Part(tip);
        for (int i = 0; i < dials.length; i += 2) t.set((PartDial)dials[i], ((Number)dials[i + 1]).doubleValue());
        b.set(BrushPart.TIP, t); b.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.INK)); b.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.OFF));
        b.set(BrushPart.PAPER, new ModularBrush.Part(PartChoice.PLAIN)); b.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.FAST_WET)); b.set(BrushPart.HAND, new ModularBrush.Part(hand));
        return b.normalize();
    }
    /** {@code s} through the pattern's stamper alone, {@code per} samples a time, into a fresh buffer: the line's at the ink size, or the body's. */
    static FloatTiles stamp(ModularBrush b, double size, List<double[]> s, int per, boolean body) {
        ResolvedBrush r = ResolvedBrush.of(b, size, 1);
        FloatTiles t = new FloatTiles(W, H, 0);
        StrokePoints pts = new StrokePoints();
        LineStamper.Meta meta = new LineStamper.Meta();
        StippleStamper.Holds holds = r.pattern.hold > 0 ? new StippleStamper.Holds() : null;
        double at = body ? r.bodySize : r.inkSize;
        int done = 1;
        for (int i = 0; i < s.size(); i += per) {
            for (int j = i; j < Math.min(s.size(), i + per); j++) {
                double[] q = s.get(j);
                pts.add((float)q[0], (float)q[1], (float)q[2], (long)q[3], Float.NaN, Float.NaN);
                if (holds != null) holds.feed((float)q[0], (float)q[1], (float)q[2], (long)q[3], pts.total());
            }
            if (r.pattern.stipple()) StippleStamper.stamp(t, null, r.pattern, pts, at, done, meta, holds);
            else HatchStamper.stamp(t, null, r.pattern, pts, at, done, meta);
            done = pts.count;
        }
        return t;
    }
    /**
     * The lab's numbers for {@code t} (golden.cjs, the lab's engine in Node on the same strokes): coverage's sum within
     * .05%, its area over .05 within .2%, its centroid within .01 px.
     */
    static void like(FloatTiles t, String what, double sum, int area, double cx, double cy) {
        double s = 0, mx = 0, my = 0;
        int a = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { double v = t.get(x, y); s += v; mx += v * x; my += v * y; if (v > .05) a++; }
        check(Math.abs(s - sum) <= .0005 * sum && Math.abs(a - area) <= Math.max(2, .002 * area), what + ": the lab's ink and area: " + s + " vs " + sum + ", " + a + " vs " + area);
        check(Math.abs(mx / s - cx) <= .01 && Math.abs(my / s - cy) <= .01, what + ": the lab's centre: " + mx / s + ", " + my / s);
    }
    static ModularBrush zealous() { return ModularCode.decode(ZEALOUS); }
    /** The owner's favourite from Brush Studio: a sponged Watercolor stipple. */
    static final String ZEALOUS = "zealous-shallot.M67gzlduui0_1zfmq009t0500ee_0_000_1jz_8rx0000000000000000nz0.vvrysb";

    static void lab() {
        like(stamp(mk(PartChoice.STIPPLE, PartChoice.STEADY, PartDial.DOT, .3, PartDial.SCATTER, .4, PartDial.JITTER, .5), 16, line(20, 120, 460, 120, 160, u -> .2 + .75 * u), 3, false),
                "A pen stipple", 4410.204, 7014, 268.6715, 119.3662);
        like(stamp(mk(PartChoice.STIPPLE, PartChoice.STEADY, PartDial.DOT, .45, PartDial.SCATTER, .4, PartDial.JITTER, .9), 26, line(20, 100, 460, 150, 160, u -> .3 + .6 * Math.sin(Math.PI * u)), 3, false),
                "A sponge", 5925.576, 8249, 235.0925, 123.4325);
        like(stamp(mk(PartChoice.STIPPLE, PartChoice.PRESS_WIDER, PartDial.DOT, .5, PartDial.SCATTER, .35, PartDial.JITTER, 0), 20, line(20, 120, 460, 120, 120, u -> u), 3, false),
                "An even stipple, Pressure size narrowing it", 5879.55, 8094, 306.5325, 119.0643);
        like(stamp(mk(PartChoice.STIPPLE, PartChoice.STEADY, PartDial.DOT, .4, PartDial.SCATTER, .3, PartDial.JITTER, .35, PartDial.BUILD_UP, 2), 20, held(), 3, false),
                "Build-up fast, held a second", 4268.797, 6154, 250.0594, 119.3281);
        like(stamp(zealous(), 60, line(40, 120, 440, 120, 200, u -> .3 + .6 * Math.sin(Math.PI * u)), 3, true), "Zealous shallot's sponged wash", 14213.895, 16285, 236.3605, 119.2166);
    }
    /** golden.cjs's held stroke: a second at 120, 120 (a sample every 10 ms), then a line to the right. */
    static List<double[]> held() {
        List<double[]> s = new ArrayList<>();
        for (int k = 0; k <= 100; k++) s.add(new double[]{120, 120, 179 / 256.0, 1000 + 10 * k});
        for (double[] q : line(120, 120, 400, 120, 100, u -> .7)) s.add(new double[]{q[0], q[1], q[2], q[3] + 1010});
        return s;
    }

    static boolean same(FloatTiles a, FloatTiles b) {
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (Float.floatToIntBits(a.get(x, y)) != Float.floatToIntBits(b.get(x, y))) return false;
        return true;
    }
    static void deterministic() {
        ModularBrush pen = BristleSwarmChecks.preset("Stipple pen"), held = pen.copy();
        held.part(BrushPart.TIP).set(PartDial.BUILD_UP, 1);
        for (ModularBrush b : new ModularBrush[]{pen, held, zealous()}) {
            List<double[]> s = b == held ? held() : line(20, 100, 460, 150, 160, u -> .3 + .6 * u);
            FloatTiles a = stamp(b, 24, s, 3, b == zealous()), c = stamp(b, 24, s, 3, b == zealous());
            check(same(a, c) && same(a, stamp(b, 24, s, 1, b == zealous())) && same(a, stamp(b, 24, s, 1000, b == zealous())), b.name + ": the same samples lay the same dots, however they're batched");
            ModularBrush other = b.copy(b.seed ^ 0x5151);
            check(!same(a, stamp(other, 24, s, 3, b == zealous())), b.name + ": another seed, other dots");
        }
    }

    // ---- on the page ----

    /** {@code b} drawn along {@code s} ({x, y, travel, time}) on a fresh pair, {@code per} samples between presents. */
    static ToneDocument page(ModularBrush b, double size, List<double[]> s, int per) {
        ToneDocument d = InkWashLayers.blank(W, H);
        ModularStroke st = ModularStroke.start(d, b, size, 1, null);
        feed(st, s, per);
        st.finish();
        return d;
    }
    static void feed(DrawingStroke st, List<double[]> s, int per) {
        for (int i = 0; i < s.size(); i++) {
            double[] q = s.get(i);
            st.sample((float)q[0], (float)q[1], ToolSettings.pressure((float)q[2]), Float.NaN, Float.NaN, (long)q[3]);
            if (i % per == per - 1) st.present();
        }
    }
    /** Pixels darker than {@code below} in rows {@code y0..y1}. */
    static int dark(ToneDocument d, int below, int y0, int y1) {
        int n = 0;
        for (int y = y0; y <= y1; y++) for (int x = 0; x < W; x++) if (d.compositeTone(x, y) < below) n++;
        return n;
    }
    static void pressure() {
        ModularBrush st = mk(PartChoice.STIPPLE, PartChoice.STEADY, PartDial.DOT, .3, PartDial.SCATTER, .4, PartDial.JITTER, .5);
        int lo = dark(page(st, 16, line(20, 120, 460, 120, 160, u -> .2), 4), 128, 0, H - 1), hi = dark(page(st, 16, line(20, 120, 460, 120, 160, u -> .95), 4), 128, 0, H - 1);
        check(hi > 2.5 * lo && lo > 40, "Pressure packs the dots closer: " + lo + " dark px light, " + hi + " pressed (the lab: ×2.9)");
    }
    static void passes() {
        ModularBrush pen = BristleSwarmChecks.preset("Stipple pen");
        ToneDocument one = page(pen, 20, line(20, 120, 460, 120, 160, u -> .5), 4);
        ToneDocument two = InkWashLayers.blank(W, H);
        for (int k = 0; k < 2; k++) {
            List<double[]> s = line(20, 120 + k, 460, 120 + k, 160, u -> .5);
            ModularStroke stroke = ModularStroke.start(two, pen, 20, 1, null);
            feed(stroke, s, 4); stroke.finish();
        }
        int a = dark(one, 128, 0, H - 1), b = dark(two, 128, 0, H - 1);
        check(b > 1.3 * a, "Each pass adds dots: " + a + " dark px, " + b + " after a second pass");
    }

    /** {@code b} held at the page's middle for {@code ms} (a sample every 5 ms, or untimed), lifted. */
    static ToneDocument hold(ModularBrush b, int ms, boolean timed) {
        List<double[]> s = new ArrayList<>();
        for (int t = 0; t <= ms; t += 5) s.add(new double[]{240, 120, .6, timed ? 1000 + t : PenSpeed.UNTIMED});
        return page(b, 24, s, 4);
    }
    static void buildUp() {
        ModularBrush off = BristleSwarmChecks.preset("Stipple pen"), slow = off.copy(), fast = off.copy();
        slow.part(BrushPart.TIP).set(PartDial.BUILD_UP, 1); fast.part(BrushPart.TIP).set(PartDial.BUILD_UP, 2);
        int[] times = {0, 250, 500, 1000, 2000};
        StringBuilder seen = new StringBuilder();
        int last = -1;
        for (int ms : times) {
            int o = dark(hold(off, ms, true), 128, 0, H - 1), sl = dark(hold(slow, ms, true), 128, 0, H - 1), f = dark(hold(fast, ms, true), 128, 0, H - 1);
            seen.append(ms).append(" ms: ").append(o).append(" / ").append(sl).append(" / ").append(f).append("; ");
            check(o == dark(hold(off, 0, true), 128, 0, H - 1), "Build-up off: holding still adds nothing (" + seen + ")");
            check(ms == 0 ? sl == o && f == o : sl > o && f > sl, "Build-up: held " + ms + " ms, slow adds dots, fast more (" + seen + ")");
            check(f > last, "Build-up fast keeps building the longer it's held (" + seen + ")");
            last = f;
        }
        check(dark(hold(fast, 1000, false), 128, 0, H - 1) == dark(hold(off, 1000, false), 128, 0, H - 1), "Untimed samples build nothing");
        // A pen that sends nothing while it rests: the pad's HeldPen sends its last sample again each quiet tick.
        ToneDocument d = InkWashLayers.blank(W, H);
        ModularStroke st = ModularStroke.start(d, fast, 24, 1, null);
        HeldPen held = new HeldPen();
        float press = ToolSettings.pressure(.6f);
        held.sampled(240, 120, press, Float.NaN, Float.NaN, 1000); st.sample(240, 120, press, Float.NaN, Float.NaN, 1000);
        int resent = 0;
        for (long now = 1000; now <= 2000; now += 10) if (held.due(st, now)) { held.resend(st, now); st.present(); resent++; }
        st.finish();
        check(!held.due(ModularStroke.start(InkWashLayers.blank(W, H), off, 24, 1, null), 5000) && resent >= 30 && dark(d, 128, 0, H - 1) > 1.5 * dark(hold(fast, 0, true), 128, 0, H - 1),
                "A quiet pen's last sample, sent again by the pad, builds as a resting pen's samples do (" + resent + " resent): " + dark(d, 128, 0, H - 1) + " dark px");
    }

    // ---- quick strokes ----

    static void quick() {
        ModularBrush built = BristleSwarmChecks.preset("Stipple pen");
        built.part(BrushPart.TIP).set(PartDial.BUILD_UP, 2);
        for (ModularBrush b0 : new ModularBrush[]{BristleSwarmChecks.preset("Stipple pen"), BristleSwarmChecks.preset("Stipple sponge"), zealous(), built})
            for (PartChoice life : new PartChoice[]{PartChoice.NONE, PartChoice.FAST_WET, PartChoice.DRYING}) {
                ModularBrush b = b0.copy();
                ModularBrush.Part p = new ModularBrush.Part(life);
                if (life == PartChoice.DRYING) p.set(PartDial.SETTLE, .5);
                b.set(BrushPart.LIFE, p);
                for (double size : new double[]{24, 140}) {
                    String what = b.name + " " + life + " " + size + " px";
                    ToneDocument q = QuickStrokeChecks.quick(b, size, null), live = QuickStrokeChecks.live(b, size, false, null);
                    if (life != PartChoice.FAST_WET) check(QuickStrokeChecks.same(q, live), what + ": quick ends with every layer as live");
                    else check(QuickStrokeChecks.sameShown(q, live) && QuickStrokeChecks.same(q, QuickStrokeChecks.live(b, size, true, null)), what + ": quick shows as live, every layer as live composed once");
                }
            }
        // Build-up's dots come from the samples' times: a stroke that rests half a second, quick, ends as live.
        List<double[]> s = held();
        ToneDocument live = page(built, 30, s, 3), q = InkWashLayers.blank(W, H);
        QuickStroke qs = QuickStroke.quickly(q, built, 30, 1, null);
        feed(qs, s, 3); qs.finish();
        check(QuickStrokeChecks.same(q, live), "A held Build-up stroke, quick, ends as live");
        // A stipple's dots are laid once: a pen stipple draws live at every size (the Manta keeps up to its 360 px); a sponge's wet body goes quick as Fast-wet's.
        ResolvedBrush big = ResolvedBrush.of(BristleSwarmChecks.preset("Stipple pen"), 360, 1), sponge = ResolvedBrush.of(BristleSwarmChecks.preset("Stipple sponge"), 240, 1);
        check(!QuickStroke.quick(big) && QuickStroke.panelReach(big) == 0 && QuickStroke.keepsUpTo(big) == QuickStroke.STIPPLE && ModularStroke.reach(big) == StippleStamper.reach(big.pattern, big.inkSize) && ModularStroke.reach(big) < QuickStroke.STIPPLE,
                "A pen stipple draws live at any size, its scatter counted: " + ModularStroke.reach(big) + " px");
        check(QuickStroke.quick(sponge) && QuickStroke.keepsUpTo(sponge) == QuickStroke.WET_BODY, "A Quick sponge's wet body goes quick past Fast-wet's reach: " + ModularStroke.reach(sponge));
    }

    // ---- rolls ----

    /**
     * Every style's rolls with Stipple and Hatch off and on, the same Random: each brush the same, or its tip changed
     * to {@code choice} (or the other new tip) and nothing else; the styles' odds the lab's. Returns how often each style
     * rolled {@code choice}.
     */
    static int[] rolls(PartChoice choice) {
        int[] hits = new int[ModularStyle.all().size()];
        for (ModularStyle style : ModularStyle.all()) for (int i = 0; i < 3000; i++) {
            ModularStyle.patternRolls = false;
            ModularBrush without;
            try { without = style.roll(new Random(4000L * (style.index + 1) + i)); } finally { ModularStyle.patternRolls = true; }
            ModularBrush with = style.roll(new Random(4000L * (style.index + 1) + i));
            PartChoice tip = with.choice(BrushPart.TIP);
            boolean changed = tip != without.choice(BrushPart.TIP);
            if (changed) check(tip == PartChoice.STIPPLE || tip == PartChoice.HATCH, style.name + " roll " + i + ": only to Stipple or Hatch");
            for (BrushPart p : BrushPart.values()) if (p != BrushPart.TIP || !changed) check(with.part(p).sameAs(without.part(p)), style.name + " roll " + i + ": " + p + " as before");
            // The name's first word may say the new tip (BrushNames.described: "dot-fig", "hatch-leek"); its produce is the same.
            check(with.seed == without.seed && with.name.equals(BrushNames.name(with)), style.name + " roll " + i + ": its seed as before, its name said by what it is");
            if (changed) check(with.part(BrushPart.TIP).get(PartDial.WEIGHT) == without.part(BrushPart.TIP).get(PartDial.WEIGHT, 1) || !without.part(BrushPart.TIP).has(PartDial.WEIGHT), style.name + " roll " + i + ": the tip's weight kept");
            if (tip == choice) hits[style.index]++;
        }
        return hits;
    }
    /** {@code hits} of 3000 within four standard deviations of one in {@code odds} (of {@code share} of the rolls); 0 where odds is 0. */
    static void odds(int[] hits, ModularStyle style, int odds, double share, String what) {
        int n = hits[style.index];
        if (odds == 0) { check(n == 0, style.name + " never rolls " + what); return; }
        double p = share / odds, mean = 3000 * p, sd = Math.sqrt(3000 * p * (1 - p));
        check(Math.abs(n - mean) <= 4 * sd + 1, style.name + " rolls " + what + " one in " + odds + ": " + n + " of 3000");
    }
    static void rolls() {
        int[] hits = rolls(PartChoice.STIPPLE);
        for (ModularStyle s : ModularStyle.all()) odds(hits, s, s.stippleOdds(), 1, "Stipple");
        check(ModularStyle.named(ModularStyle.ANYTHING_GOES).stippleOdds() == 30 && ModularStyle.named("Fineliner").stippleOdds() == 40 && ModularStyle.named("Watercolor").stippleOdds() == 45, "The lab's odds");
        ModularStyle water = ModularStyle.named("Watercolor");
        for (int i = 0; i < 3000; i++) {
            ModularBrush b = water.roll(new Random(4000L * (water.index + 1) + i));
            if (b.choice(BrushPart.TIP) != PartChoice.STIPPLE) continue;
            ModularBrush.Part t = b.part(BrushPart.TIP);
            boolean sponge = b.choice(BrushPart.MEDIUM) == PartChoice.WASH;
            check(t.get(PartDial.DOT) >= .35 && t.get(PartDial.DOT) <= (sponge ? .55 : .65) && t.get(PartDial.SCATTER) >= .2 && t.get(PartDial.SCATTER) <= .8
                    && (!sponge || t.get(PartDial.JITTER) >= .7) && t.get(PartDial.BUILD_UP) == 0, "A rolled stipple's dials: " + ModularCode.encode(b));
        }
        for (ModularStyle s : ModularStyle.all()) {
            ModularBrush b = s.roll(new Random(77 + s.index));
            for (ModularVariations.Made m : ModularVariations.make(b, new ModularLocks(), new Random(5)))
                check(m.brush.choice(BrushPart.TIP) == b.choice(BrushPart.TIP) || !ModularFeatures.seedOnly(m.brush.choice(BrushPart.TIP)), s.name + ": a variation never swaps to Stipple or Hatch");
        }
    }

    // ---- codes and words ----

    static void codes() {
        String[] lab = {"stipple-pen.M07i0clcli0_000zz000000000000000_0_000_1jz_0000000000000000000nz0.h6q",
                "stipple-sponge.M67g7eewei0_1cl0000770000ee_0_000_1jz_0000000000000000000nz0.h6p", ZEALOUS};
        for (String c : lab) { ModularBrush b = ModularCode.decode(c); check(b != null && b.choice(BrushPart.TIP) == PartChoice.STIPPLE && ModularCode.encode(b).equals(c), "The lab's stipple code reads and writes back: " + c); }
        ModularBrush b = mk(PartChoice.STIPPLE, PartChoice.STEADY);
        String plain = ModularCode.encode(b);
        check(plain.contains(".M07cleei0i0_"), "A fresh stipple's tip is the lab's 7cleei0i0: " + plain);
        b.part(BrushPart.TIP).set(PartDial.BUILD_UP, 2);
        String built = ModularCode.encode(b);
        check(built.contains(".M07cleei0i02_") && ModularCode.encode(ModularCode.decode(built)).equals(built) && (int)ModularCode.decode(built).part(BrushPart.TIP).get(PartDial.BUILD_UP) == 2,
                "Build-up is written only when on, one digit last, and reads back: " + built);
    }
    static void words() {
        check(ModularWords.plainWords(BristleSwarmChecks.preset("Stipple pen")).equals("Ink stipple."), "Stipple pen: " + ModularWords.plainWords(BristleSwarmChecks.preset("Stipple pen")));
        check(ModularWords.plainWords(BristleSwarmChecks.preset("Stipple sponge")).equals("Sponged wash."), "Stipple sponge: " + ModularWords.plainWords(BristleSwarmChecks.preset("Stipple sponge")));
        check(ModularWords.plainWords(zealous()).startsWith("Sponged wash. "), "Zealous shallot: " + ModularWords.plainWords(zealous()));
        ModularBrush built = BristleSwarmChecks.preset("Stipple pen");
        built.part(BrushPart.TIP).set(PartDial.BUILD_UP, 1);
        check(ModularWords.plainWords(built).equals("Ink stipple. Builds up where it lingers."), "With Build-up: " + ModularWords.plainWords(built));
        check(ModularWords.caption(BristleSwarmChecks.preset("Stipple pen")).equals(java.util.Arrays.asList("Fineliner", "stipple")), "Caption: " + ModularWords.caption(BristleSwarmChecks.preset("Stipple pen")));
        check(ModularWords.choiceWords(built, BrushPart.TIP).equals("Stipple") && PartDial.BUILD_UP.shown(1).equals("Slow") && PartDial.BUILD_UP.label.equals("Build-Up"), "The card says Stipple; Build-Up Off, Slow, Fast");
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
