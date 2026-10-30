package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.Random;

/**
 * Bristles ({@link BristleSwarm}): the web lab's swarm, deterministic, splaying with pressure, carrying its ink a long
 * way and then running dry into streaks; quick strokes end as live ones; rolls take it up without changing the rest.
 * (MonoPaint's {@code BristleChecks} is its bristle texture's.)
 */
public final class BristleSwarmChecks {
    static final int W = 900, H = 300;

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        lab(); deterministic(); splay(); inkLasts(); quick(); rolls(); codes(); words();
        System.out.println("PASS: bristles draw as the web lab's swarm (a brush of 10 px radius or less to within float rounding: area, ink, centre, spread and the hairs' rows across the head); the same samples and seed draw the same hairs however they're batched and on one core or several, another seed other hairs; pressing splays the head; a loaded brush carries its ink past 1000 px and then runs dry into streaks, gradually, further than the lab's, and a barely dry one never runs out; a quick stroke ends as the same samples drawn live; Dry brush and Anything goes roll Bristles at the lab's odds, from the brush's seed, with every other part and every later roll from the same Random as before, other styles never; Ink load is written only off its default; \"This brush\" says what bristles do");
    }

    // ---- strokes (the lab harness's strokes.cjs, every value a float) ----

    /** A gentle wave left to right, pressure rising then falling: {x, y, travel, t}. */
    static float[][] wave() {
        int n = (int)Math.floor((W - 160) / 2.5);
        float[][] s = new float[n + 1][];
        for (int i = 0; i <= n; i++) {
            double u = (double)i / n;
            s[i] = new float[]{(float)(80 + i * 2.5), (float)(H / 2.0 + Math.round(H * .18 * Math.sin(u * Math.PI * 2) * 4) / 4.0),
                    (float)(Math.round((.25 + .7 * Math.sin(u * Math.PI)) * 256) / 256.0), i * 4};
        }
        return s;
    }
    /** A straight line at y = H/2, travel .75. */
    static float[][] line() {
        int n = (W - 160) / 2;
        float[][] s = new float[n + 1][];
        for (int i = 0; i <= n; i++) s[i] = new float[]{80 + 2 * i, H / 2f, .75f, i * 3};
        return s;
    }
    /** The lab's default swarm with seed {@code seed}; Ink load carrying as far as the lab's. */
    static ResolvedBrush.Swarm labSwarm(long seed) {
        ResolvedBrush.Swarm b = new ResolvedBrush.Swarm();
        b.seed = seed; b.hairs = 26; b.cohesion = .55; b.splay = .45; b.dryness = .15;
        b.load = .25;   // carry(.25) = 1
        return b;
    }
    /** {@code s} fed to the swarm three samples at a time (the lab's harness), into a fresh buffer. */
    static FloatTiles swarm(ResolvedBrush.Swarm b, float[][] s, double size, int per) {
        FloatTiles t = new FloatTiles(W, H, 0);
        StrokePoints pts = new StrokePoints();
        LineStamper.Meta meta = new LineStamper.Meta();
        StrokePaper paper = new StrokePaper(BrushTexture.PLAIN);
        for (int i = 0; i < s.length; i += per) {
            for (int j = i; j < Math.min(s.length, i + per); j++) pts.add(s[j][0], s[j][1], s[j][2], (long)s[j][3], Float.NaN, Float.NaN);
            if (pts.count >= 2) BristleSwarm.stamp(t, null, paper, b, pts, size / 2, meta);
        }
        return t;
    }

    // ---- the web lab ----

    /**
     * The lab's numbers (its engine.js run in Node on the same strokes, docs/brush-lab/prototypes/modular-lab-src):
     * the coverage's sum, its area over .05, centroid and spread across; for the line, each row's sum.
     */
    static void lab() {
        Object[][] cases = {{"wave", 12.0, 12345L, 7306.03, 10533, 150.5931, 38.491}, {"wave", 20.0, 777L, 12120.816, 15595, 149.3065, 39.2005},
                {"line", 16.0, 4242L, 9687.104, 12743, 149.185, 4.5697}};
        for (Object[] c : cases) {
            FloatTiles t = swarm(labSwarm((long)c[2]), c[0].equals("wave") ? wave() : line(), (double)c[1], 3);
            double sum = 0, my = 0, sy = 0;
            int area = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { double v = t.get(x, y); sum += v; my += v * y; if (v > .05) area++; }
            my /= sum;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) sy += t.get(x, y) * (y - my) * (y - my);
            sy = Math.sqrt(sy / sum);
            String what = c[0] + " at " + c[1] + " px";
            check(Math.abs(sum - (double)c[3]) <= .0005 * (double)c[3] && Math.abs(area - (int)c[4]) <= .002 * (int)c[4], what + ": the lab's ink and area: " + sum + ", " + area);
            check(Math.abs(my - (double)c[5]) <= .01 && Math.abs(sy - (double)c[6]) <= .01, what + ": the lab's centre and spread across: " + my + ", " + sy);
            if (c[0].equals("line")) {
                int[] rows = {38, 288, 420, 510, 602, 626, 631, 613, 621, 651, 668, 686, 684, 662, 592, 511, 374, 288, 205, 16};
                for (int y = 0; y < H; y++) {
                    double r = 0;
                    for (int x = 0; x < W; x++) r += t.get(x, y);
                    double want = y >= 140 && y <= 159 ? rows[y - 140] : 0;
                    check(Math.abs(r - want) <= 1, what + ": the hairs' row " + y + " as the lab's: " + r + " vs " + want);
                }
            }
        }
    }

    // ---- determinism ----

    static ModularBrush preset(String name) { return ModularCode.decode(FloatChecks.find(name).code); }
    static boolean same(FloatTiles a, FloatTiles b) {
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (Float.floatToIntBits(a.get(x, y)) != Float.floatToIntBits(b.get(x, y))) return false;
        return true;
    }
    static void deterministic() {
        for (double size : new double[]{16, 60, 120}) {
            ResolvedBrush r = ResolvedBrush.of(preset("Bristle brush"), size, 1);
            FloatTiles a = swarm(r.lineSwarm, wave(), size, 3), b = swarm(r.lineSwarm, wave(), size, 3);
            check(same(a, b), size + " px: the same samples and seed lay the same hairs");
            check(same(a, swarm(r.lineSwarm, wave(), size, 1)) && same(a, swarm(r.lineSwarm, wave(), size, 1000)), size + " px: however the samples are batched");
            Cores.shared = false;
            try { check(same(a, swarm(r.lineSwarm, wave(), size, 3)), size + " px: on one core as on several"); } finally { Cores.shared = true; }
            ResolvedBrush.Swarm other = r.lineSwarm.copy();
            other.seed ^= 0x5151;
            check(!same(a, swarm(other, wave(), size, 3)), size + " px: another seed, other hairs");
        }
    }

    // ---- the look ----

    /** {@code b} drawn along a straight line at travel {@code travel} from x = 40 to {@code length} + 40, page {@code w} wide. */
    static ToneDocument straight(ModularBrush b, double size, float travel, int length, int w) {
        ToneDocument d = InkWashLayers.blank(w, H);
        ModularStroke s = ModularStroke.start(d, b, size, 1, null);
        for (int i = 0; i * 2 <= length; i++) {
            s.sample(40 + 2 * i, H / 2f, ToolSettings.pressure(travel), 0, 0, 10_000 + 4L * i);
            if (i % 8 == 7) s.present();
        }
        s.finish();
        return d;
    }
    /** How many rows across the stroke are marked (darker than 200) in columns {@code x0..x1}. */
    static int across(ToneDocument d, int x0, int x1) {
        int n = 0;
        for (int y = 0; y < H; y++) for (int x = x0; x <= x1; x++) if (d.compositeTone(x, y) < 200) { n++; break; }
        return n;
    }
    static void splay() {
        ModularBrush b = preset("Bristle brush");
        int light = across(straight(b, 60, .1f, 700, 800), 380, 420), heavy = across(straight(b, 60, 1, 700, 800), 380, 420);
        check(heavy >= 1.5 * light, "Pressing splays the head (Pressure → size): " + light + " rows light, " + heavy + " pressed");
        ModularBrush steady = b.copy(); steady.set(BrushPart.HAND, new ModularBrush.Part(PartChoice.STEADY));
        int sLight = across(straight(steady, 60, .1f, 700, 800), 380, 420), sHeavy = across(straight(steady, 60, 1, 700, 800), 380, 420);
        check(sHeavy >= 1.15 * sLight && sHeavy < heavy, "and the lab's own splay opens it a little with a steady hand: " + sLight + ", " + sHeavy);
    }

    /** The share of the band across a straight stroke (rows 110–189) in columns {@code x0..x1} that is inked (darker than 128). */
    static double inked(ToneDocument d, int x0, int x1) {
        int n = 0, all = 0;
        for (int y = 110; y < 190; y++) for (int x = x0; x <= x1; x++, all++) if (d.compositeTone(x, y) < 128) n++;
        return (double)n / all;
    }
    static void inkLasts() {
        ModularBrush b = preset("Bristle brush");
        double dry = b.part(BrushPart.TIP).get(PartDial.DRYNESS);
        check(dry >= .7, "Bristle brush is a dry one: " + dry);
        ToneDocument d = straight(b, 60, .6f, 2000, 2100);
        double start = inked(d, 100, 400), middle = inked(d, 800, 1100), late = inked(d, 1500, 1800), end = inked(d, 1800, 2030);
        check(middle >= .9 * start, "A loaded brush carries its ink past 1000 px: " + start + " → " + middle);
        check(late < .9 * start && end < late && end > .1 * start, "then runs dry into streaks, gradually: " + late + ", " + end);
        // The lab's load (Ink load .25: carry 1) runs dry sooner.
        ModularBrush lab = b.copy(); lab.part(BrushPart.TIP).set(PartDial.INK_LOAD, .25);
        ToneDocument l = straight(lab, 60, .6f, 2000, 2100);
        check(inked(l, 800, 1100) < middle && inked(l, 1500, 1800) < late, "further than the lab's: " + inked(l, 800, 1100) + ", " + inked(l, 1500, 1800));
        ModularBrush wet = b.copy(); wet.part(BrushPart.TIP).set(PartDial.DRYNESS, .15);
        ToneDocument w = straight(wet, 60, .6f, 2000, 2100);
        check(inked(w, 1800, 2030) >= .95 * inked(w, 100, 400), "A barely dry brush never runs out: " + inked(w, 100, 400) + " → " + inked(w, 1800, 2030));
        check(BristleSwarm.streaksAt(dry, .5) < 2000 && BristleSwarm.streaksAt(.15, .5) > 50_000 && BristleSwarm.streaksAt(0, .5) == Double.POSITIVE_INFINITY
                && BristleSwarm.streaksAt(dry, 1) > BristleSwarm.streaksAt(dry, .5), "How soon streaks start: sooner the drier, later the more it holds");
    }

    // ---- quick strokes ----

    static void quick() {
        for (String name : new String[]{"Bristle brush", "Hog bristle"}) for (PartChoice life : new PartChoice[]{PartChoice.NONE, PartChoice.FAST_WET, PartChoice.DRYING}) {
            ModularBrush b = preset(name);
            ModularBrush.Part p = new ModularBrush.Part(life);
            if (life == PartChoice.DRYING) p.set(PartDial.SETTLE, .5);
            b.set(BrushPart.LIFE, p);
            for (double size : new double[]{40, 160}) {
                ToneDocument q = QuickStrokeChecks.quick(b, size, null), live = QuickStrokeChecks.live(b, size, false, null);
                String what = name + " " + life + " " + size + " px";
                if (life != PartChoice.FAST_WET) check(QuickStrokeChecks.same(q, live), what + ": quick ends with every layer as live");
                else check(QuickStrokeChecks.sameShown(q, live) && QuickStrokeChecks.same(q, QuickStrokeChecks.live(b, size, true, null)), what + ": quick shows as live, every layer as live composed once");
            }
        }
        ResolvedBrush small = ResolvedBrush.of(preset("Bristle brush"), 12, 1), wide = ResolvedBrush.of(preset("Bristle brush"), 24, 1);
        check(!QuickStroke.quick(small) && QuickStroke.quick(wide) && QuickStroke.panelReach(wide) == ModularStroke.reach(wide) && QuickStroke.keepsUpTo(wide) == QuickStroke.BRISTLES,
                "Bristles go quick past the panel's reach, their hairs splayed: " + ModularStroke.reach(small) + " / " + ModularStroke.reach(wide) + " px reach");
    }

    // ---- rolls ----

    /** Dry brush's rolls before Bristles joined (seeds 0–11), from the last build without them (names from no assets). */
    static final String[] BEFORE = {
            "nimble-yam.Mb0i00000nk0019_2lm00_0_4b7_1jz_8fb0000000000000000nz0.h32u0q",
            "nimble-plum.Mb0i00000in0074_2k700_0_2r6_1jz_8ny0000000000000000nz0.t4mf73",
            "nimble-fig.Mb0i00000xd000d_2oi00_0_4f9_1jz_8ce0000000000000000nz0.1s13pn4",
            "nimble-leek.Mb25aeei00000sg0067_2n200_0_2d8_1jz_8l10000000000000000nz0.51j8ud",
            "nimble-beet.Mb25aeei00000lx0033_1caat00770000_0_1l5_1jz_8l40000000000000000nz0.1t996q7",
            "nimble-pear.Mb0i00000yz008x_1auat00770000_0_4j4_1jz_8fd0000000000000000nz0.69opxg",
            "nimble-fig.Mb0i00000vq0026_10rat00770000_0_1p6_1jz_8i70000000000000000nz0.15660dh",
            "nimble-kale.Mb0i00000qt0080_1dpat00770000_0_4n5_1jz_8ch0000000000000000nz0.1h7plju",
            "nimble-beet.Mb0i00000k9004w_16iat00770000_0_2d2_1jz_8cj0000000000000000nz0.16ebhgk",
            "nimble-fig.Mb25aeei00000xc00aq_153at00770000_0_1b1_1jz_8l70000000000000000nz0.1ifv2mx",
            "nimble-lime.Mb0i00000u3003z_19eat00770000_0_2h3_1jz_8o10000000000000000nz0.ib8b3u",
            "nimble-kale.Mb0i00000p6009u_17yat00770000_0_4f3_1jz_8ia0000000000000000nz0.ucrwa7",
    };
    static void rolls() {
        // Stipple and Hatch, decided after Bristles from the seed, are off here (StippleChecks and HatchChecks show they change nothing else).
        ModularStyle.patternRolls = false;
        try { bristleRolls(); } finally { ModularStyle.patternRolls = true; }
    }
    static void bristleRolls() {
        ModularStyle dry = ModularStyle.named("Dry brush");
        check(BEFORE.length == 12, "twelve rolls from before");
        for (int s = 0; s < BEFORE.length; s++) {
            String now = ModularCode.encode(ErasableChecks.allErasable(dry.roll(new Random(s))));
            String[] a = BEFORE[s].split("_"), b = now.split("_");
            boolean bristles = ModularCode.decode(now).choice(BrushPart.TIP) == PartChoice.BRISTLES;
            String tipBefore = a[0].substring(a[0].indexOf(".M") + 3), tipNow = b[0].substring(b[0].indexOf(".M") + 3);
            // The name is left out: it is read from the assets' lists, which the build that recorded these didn't have.
            check(a[0].substring(a[0].indexOf(".M"), a[0].indexOf(".M") + 3).equals(b[0].substring(b[0].indexOf(".M"), b[0].indexOf(".M") + 3))
                    && Arrays.equals(Arrays.copyOfRange(a, 1, 6), Arrays.copyOfRange(b, 1, 6)) && (bristles || tipBefore.equals(tipNow)),
                    "Dry brush seed " + s + ": every other part and the seed as before; the tip unless it is Bristles: " + now);
        }
        // From its own seed: every roll after it from the same Random is as without Bristles, Smudge's and Float's too.
        for (String name : new String[]{"Dry brush", ModularStyle.ANYTHING_GOES}) {
            ModularStyle st = ModularStyle.named(name);
            Random on = new Random(11), off = new Random(11);
            int flips = 0;
            for (int i = 0; i < 300; i++) {
                ModularBrush with = st.roll(on);
                ModularStyle.bristleRolls = false;
                ModularBrush without;
                try { without = st.roll(off); } finally { ModularStyle.bristleRolls = true; }
                boolean bristles = with.choice(BrushPart.TIP) == PartChoice.BRISTLES && without.choice(BrushPart.TIP) != PartChoice.BRISTLES;
                if (bristles) flips++;
                for (BrushPart p : BrushPart.values()) check(p == BrushPart.TIP && bristles || with.part(p).sameAs(without.part(p)), name + " roll " + i + ": " + p + " as without Bristles");
                // The name's first word may say what stands out (BrushNames.described: "bristle-kale"); its produce is the same.
                check(with.seed == without.seed && with.name.equals(BrushNames.name(with)), name + " roll " + i + ": its seed as without Bristles, its name said by what it is");
            }
            check(flips > 0, name + " rolls some bristle brushes: " + flips);
        }
        // The lab's odds: Dry brush's Bristles 2 of 5, Anything goes' 1 of 12; never in a style that doesn't weigh it.
        Random random = new Random(7);
        int n = 3000, dryB = 0, wildB = 0;
        for (int i = 0; i < n; i++) {
            if (dry.roll(random).choice(BrushPart.TIP) == PartChoice.BRISTLES) dryB++;
            if (ModularStyle.named(ModularStyle.ANYTHING_GOES).roll(random).choice(BrushPart.TIP) == PartChoice.BRISTLES) wildB++;
        }
        check(Math.abs(dryB / (double)n - .4) < .03 && Math.abs(wildB / (double)n - 1 / 12.0) < .015, "Bristles at the lab's odds: " + dryB + " and " + wildB + " of " + n);
        for (ModularStyle st : ModularStyle.all()) {
            if (st.weight(PartChoice.BRISTLES) > 0) continue;
            for (int i = 0; i < 50; i++) check(st.roll(random).choice(BrushPart.TIP) != PartChoice.BRISTLES, st.name + " never rolls Bristles");
        }
        // A rolled bristle brush takes its style's dials; the tip's die can land on it; a variation can swap to it.
        ModularBrush b = null;
        for (int s = 0; b == null; s++) { ModularBrush r = dry.roll(new Random(s)); if (r.choice(BrushPart.TIP) == PartChoice.BRISTLES) b = r; }
        ModularBrush.Part tip = b.part(BrushPart.TIP);
        check(tip.get(PartDial.DRYNESS) >= .4 && tip.get(PartDial.DRYNESS) <= .8 && tip.get(PartDial.SPLAY) >= .4 && tip.get(PartDial.HAIRS) >= 16, "Dry brush's bristle dials in its bands");
        int dice = 0, swaps = 0;
        ModularBrush round = dry.roll(new Random(4));
        for (int i = 0; i < 200; i++) if (dry.rollOnePart(round, BrushPart.TIP, null, random).choice(BrushPart.TIP) == PartChoice.BRISTLES) dice++;
        for (int i = 0; i < 300; i++) for (ModularVariations.Made m : ModularVariations.make(round, new ModularLocks(), random)) if ("Bristle".equals(m.swap)) swaps++;
        check(dice > 50 && dice < 110 && swaps > 0, "The tip's die lands on Bristles (" + dice + " of 200) and a variation swaps to it (" + swaps + ")");
    }

    // ---- codes and words ----

    static void codes() {
        ModularBrush b = preset("Bristle brush");
        String code = ModularCode.encode(b);
        check(b.part(BrushPart.TIP).get(PartDial.INK_LOAD) == PartDial.INK_LOAD.def && code.split("_")[0].length() == "bristle-brush.Mb6".length() + 10,
                "Ink load at its default is left out: the prototype's code, byte for byte: " + code);
        b.part(BrushPart.TIP).set(PartDial.INK_LOAD, .8);
        ModularBrush back = ModularCode.decode(ModularCode.encode(b));
        check(Math.abs(back.part(BrushPart.TIP).get(PartDial.INK_LOAD) - .8) < 1e-3 && ModularCode.encode(back).equals(ModularCode.encode(b)), "off it, it is written and read back");
        check(ModularCode.decode("test-tip.Md6rog7js5ei0_000zz0000000000000_0_000_0_0000000000000000000nz0.9ix").part(BrushPart.TIP).get(PartDial.INK_LOAD) == PartDial.INK_LOAD.def,
                "a lab code reads Ink load's default");
    }
    static void words() {
        String words = ModularWords.plainWords(preset("Bristle brush"));
        check(words.startsWith("Ink line, bristle brush") && words.contains("splits into strands") && words.contains("Runs dry"), "This brush: " + words);
        ModularBrush wet = preset("Bristle brush"); wet.part(BrushPart.TIP).set(PartDial.DRYNESS, .15);
        check(!ModularWords.plainWords(wet).toLowerCase(java.util.Locale.ROOT).contains("runs dry"), "a brush that never runs dry doesn't say it does");
        check(PartDial.INK_LOAD.shown(.5).equals("0.50") && PartChoice.BRISTLES.labelOf(PartDial.INK_LOAD).equals("Paint Load"), "Ink load's dial");
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
