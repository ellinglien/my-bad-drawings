package io.github.mpdairy.monopaint;

import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Random;

/**
 * Smear's Soften, MonoPaint's blending stump in Inkwash (docs/archive/superpowers/specs/2026-10-18-stump-design.md): codes,
 * the web lab's golden pages, MonoPaint's own stump ({@link ToolStroke}, {@code SOFTEN}) drawn the same, what it does to
 * marks, the comb's streaks, Float's Comb, quick as live and cores. {@code java StumpChecks v} prints the numbers.
 */
public final class StumpChecks {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        CarrySmudgeChecks.verbose = verbose = args.length > 0;
        codes(); goldens(); monoPaint(); marks(); comb(); floatComb(); quickIsLive(); cores(); washes(); erasable();
        System.out.println("PASS: Smear's Soften: written only when on and never rolled, every code as before; Blend stump and Wash comb draw the web lab's golden pages; at Soften 1 Inkwash draws MonoPaint's blending stump; it softens hatching without lightening it, pulls a line the way it rubs, a short way; Wash comb combs a wash's edge into streaks and lays nothing on paper; Comb combs what floats and lays nothing; quick as live; several cores as one; on a pair's ink it blends the pair's wash too, adding no paint, both where both have marks, one Undo, quick as live, never a hidden wash or a paint smudge's other layer; permanent ink stays byte for byte, a dried wash blends by how liftable it is, masking fluid blocks it");
    }
    static boolean verbose;
    static void note(String s) { if (verbose) System.out.println(s); }
    static final int W = CarrySmudgeChecks.W, H = CarrySmudgeChecks.H;

    static ModularBrush preset(String name) { return FloatChecks.preset(name); }
    static ModularBrush soften(ModularBrush b, double soften, double pick) {
        ModularBrush c = b.copy();
        c.part(BrushPart.CARRY).set(PartDial.SOFTEN, soften); c.part(BrushPart.CARRY).set(PartDial.PICK, pick);
        return c.normalize();
    }

    /** 1. Soften round-trips, is written only when on, never rolls; the Tools page's three read as set. */
    static void codes() {
        ModularBrush stump = preset("Stump");
        String plain = ModularCode.encode(stump);
        check(plain.equals(ToolPresets.ROWS[findRow("Stump")][2]), "the Stump's code is as before: " + plain);
        for (double v : new double[]{.01, .3, .5, 1}) {
            String code = ModularCode.encode(soften(stump, v, .7));
            ModularBrush back = ModularCode.decode(code);
            check(code.length() == plain.length() + 2 && ModularCode.encode(back).equals(code) && Math.abs(back.part(BrushPart.CARRY).get(PartDial.SOFTEN) - v) < .001,
                    "Soften " + v + " is two digits more and round-trips: " + code);
        }
        check(ModularCode.encode(soften(stump, 0, .7)).equals(plain), "Soften 0 writes the code as before");
        ModularBrush blend = preset("Blend stump"), wash = preset("Wash comb"), comb = preset("Comb");
        check(blend.part(BrushPart.CARRY).get(PartDial.SOFTEN) == 1 && Math.abs(blend.part(BrushPart.CARRY).get(PartDial.PICK) - .8) < .001 && blend.part(BrushPart.CARRY).get(PartDial.LOAD) == 0
                && blend.choice(BrushPart.MEDIUM) == PartChoice.CHARCOAL, "Blend stump: the Stump at Soften 1, Pick up .8");
        check(wash.choice(BrushPart.TIP) == PartChoice.MULTI_LINE && wash.choice(BrushPart.MEDIUM) == PartChoice.WASH && wash.part(BrushPart.CARRY).get(PartDial.SOFTEN) == 1
                && wash.part(BrushPart.CARRY).get(PartDial.LOAD) == 0 && ModularWords.stump(wash) && ModularWords.combs(wash), "Wash comb: a Rake, Wash, Smear, Own paint 0, Soften 1");
        check(comb.choice(BrushPart.TIP) == PartChoice.MULTI_LINE && comb.choice(BrushPart.LIFE) == PartChoice.FLOAT && ModularWords.clearWater(comb)
                && comb.part(BrushPart.CARRY).sameAs(FloatChecks.clear().part(BrushPart.CARRY)), "Comb: Clear water with a Rake");
        check(ModularWords.plainWords(blend).contains("Softens.") && ModularWords.caption(blend).contains("stump") && ModularWords.plainWords(wash).contains("Combs.")
                && ModularWords.caption(wash).contains("comb") && ModularWords.plainWords(stump).contains("Smears."), "Words: Softens., Combs., Smears.");
        // Rolls never set it: every style's rolls, Smudge's included, keep Soften 0.
        for (ModularStyle s : ModularStyle.all()) for (int i = 0; i < 200; i++) {
            ModularBrush b = s.roll(new Random(31L * (s.index + 1) + i));
            check(b.part(BrushPart.CARRY).get(PartDial.SOFTEN, 0) == 0, s.name + " rolls Soften 0");
        }
    }
    static int findRow(String name) { for (int i = 0; i < ToolPresets.ROWS.length; i++) if (ToolPresets.ROWS[i][1].equals(name)) return i; return -1; }

    /** 2. Blend stump and Wash comb draw the web lab's golden pages (scripts/carry_goldens.cjs), as CarrySmudgeChecks' six. */
    static void goldens() throws Exception {
        for (String[] g : new String[][]{{"blend-stump", "Blend stump", "28"}, {"wash-comb", "Wash comb", "60"}}) {
            ModularBrush b = preset(g[1]);
            ToneDocument d = CarrySmudgeChecks.page(CarrySmudgeChecks.ownLayer(b), CarrySmudgeChecks::mark);
            CarrySmudgeChecks.live(d, b, Double.parseDouble(g[2]), 1, null, CarrySmudgeChecks.wave());
            BufferedImage lab = CarrySmudgeChecks.lab(g[0]);
            int off = 0, far = 0, changed = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int a = CarrySmudgeChecks.q(d.compositeTone(x, y)), l = CarrySmudgeChecks.q(lab.getRaster().getSample(x, y, 0)), m = CarrySmudgeChecks.q(CarrySmudgeChecks.mark(x, y));
                if (l != m) changed++;
                if (Math.abs(a - l) == 1) off++; else if (a != l) far++;
            }
            note(String.format(Locale.ROOT, "golden %-12s changed %6d, one grey off %5d (%.3f%%), further %d", g[0], changed, off, 100.0 * off / (W * H), far));
            check(changed > 300, g[0] + ": the lab's smudge changes the page: " + changed);
            check(far == 0 && off <= W * H / 1000, g[0] + " draws the lab's page at 16 greys: " + off + " pixels one grey off, " + far + " further");
        }
    }

    /**
     * 3. At Soften 1 and Pick up .35, firm pressure, Inkwash's Smear is MonoPaint's blending stump at its default strength
     * (35): the same stroke over the same marks, on the ink layer, ends a tone or so from it on average, under 4% of its pixels a grey and more apart (MonoPaint rounds each dab to a tone and dabs further apart).
     */
    static void monoPaint() {
        ModularBrush f = soften(preset("Stump"), 1, .35);
        for (int size : new int[]{20, 44, 90}) {
            ToneDocument ink = CarrySmudgeChecks.page(InkWashLayers.INK, CarrySmudgeChecks::mark), mono = CarrySmudgeChecks.page(InkWashLayers.INK, CarrySmudgeChecks::mark);
            double[][] path = CarrySmudgeChecks.wave();
            for (double[] p : path) p[2] = 1;
            CarrySmudgeChecks.live(ink, f, size, 1, null, path);
            mono.selectLayer(InkWashLayers.INK);
            CarrySmudgeChecks.feed(new ToolStroke(mono, ToolSettings.defaults(ToolSettings.Tool.SOFTEN).size(size).minimum(size), 0), path);
            double sum = 0; int changed = 0, far = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int a = ink.compositeTone(x, y), b = mono.compositeTone(x, y), m = CarrySmudgeChecks.mark(x, y);
                if (b == m && a == m) continue;
                changed++; sum += Math.abs(a - b);
                if (CarrySmudgeChecks.q(a) != CarrySmudgeChecks.q(b) && Math.abs(a - b) > 8) far++;
            }
            note(String.format(Locale.ROOT, "MonoPaint's stump %d px: %d pixels changed, %.2f tones apart on average, %d a grey and more than 8 tones apart", size, changed, sum / changed, far));
            check(changed > 1000 && sum / changed < 2 && far <= changed / 25, "Soften 1 is MonoPaint's blending stump at " + size + " px: " + sum / changed + " tones apart, " + far + " of " + changed + " a grey off");
        }
    }

    /**
     * 4. Blend stump over hatching softens it without lightening it; a black line it crosses it pulls on the way it rubs,
     * a short way: more of it lies just after the line than the Stump (today's Smear) leaves there, and none two widths on.
     */
    static void marks() {
        ModularBrush blend = preset("Blend stump"), stump = preset("Stump");
        ToneDocument hatched = CarrySmudgeChecks.page(InkWashLayers.INK, (x, y) -> Math.floorMod(x, 9) < 3 ? 90 : 255);
        double edge0 = CarrySmudgeChecks.edges(hatched), mean0 = CarrySmudgeChecks.mean(hatched, 100, 380, 172, 188);
        CarrySmudgeChecks.live(hatched, blend, 28, 1, null, CarrySmudgeChecks.across(40, 440, 180));
        double edge1 = CarrySmudgeChecks.edges(hatched), mean1 = CarrySmudgeChecks.mean(hatched, 100, 380, 172, 188);
        note(String.format(Locale.ROOT, "Blend stump over hatching: edges %.1f → %.1f, mean %.1f → %.1f", edge0, edge1, mean0, mean1));
        check(edge1 < .75 * edge0 && Math.abs(mean1 - mean0) < 2, "Blend stump softens hatching without lightening it: edges " + edge0 + " → " + edge1 + ", mean " + mean0 + " → " + mean1);
        // A black line crossed left to right: more lies after it than before it, and less than the Stump's two widths on.
        double[] far = new double[2], after = new double[2], before = new double[2];
        ModularBrush[] brushes = {blend, stump};
        for (int k = 0; k < 2; k++) {
            ToneDocument d = CarrySmudgeChecks.page(InkWashLayers.INK, (x, y) -> x >= 200 && x < 206 ? 0 : 255);
            CarrySmudgeChecks.live(d, brushes[k], 28, 1, null, CarrySmudgeChecks.across(40, 440, 180));
            far[k] = 255 - CarrySmudgeChecks.mean(d, 258, 264, 176, 184);
            after[k] = 255 - CarrySmudgeChecks.mean(d, 207, 214, 176, 184); before[k] = 255 - CarrySmudgeChecks.mean(d, 192, 199, 176, 184);
        }
        note(String.format(Locale.ROOT, "a black line: Blend stump %.0f darker just after it, %.0f just before, %.0f two widths on (the Stump %.0f, %.0f, %.0f)", after[0], before[0], far[0], after[1], before[1], far[1]));
        check(after[0] > before[0] + 17, "Blend stump pulls a line the way it rubs: " + after[0] + " after, " + before[0] + " before");
        check(after[0] > after[1] + 17 && far[0] < 2 && far[1] > far[0] + 4, "a short way, keeping it near: just after " + after[0] + " (the Stump " + after[1] + "), two widths on " + far[0] + " (the Stump " + far[1] + ")");
    }

    /** 5. Wash comb drawn across a wash's edge combs it into streaks, one per tine; on blank paper it lays nothing. */
    static void comb() {
        ModularBrush comb = preset("Wash comb");
        ToneDocument d = CarrySmudgeChecks.page(InkWashLayers.WASH, (x, y) -> x < 200 ? 150 : 255);
        CarrySmudgeChecks.live(d, comb, 60, 1, null, CarrySmudgeChecks.across(120, 440, 180));
        // Down a column past the edge: the streaks are dark runs between pale ones.
        int x = 215, runs = 0; boolean dark = false;
        for (int y = 130; y <= 230; y++) {
            boolean now = d.compositeTone(x, y) < 240;
            if (now && !dark) runs++;
            dark = now;
        }
        note("Wash comb: " + runs + " streaks 15 px past the edge");
        check(runs >= 4, "Wash comb combs a wash's edge into streaks: " + runs);
        ToneDocument blank = InkWashLayers.blank(W, H);
        byte[][] before = CarrySmudgeChecks.all(blank);
        CarrySmudgeChecks.live(blank, comb, 60, 1, null, CarrySmudgeChecks.wave());
        check(ModularStrokeChecks.same(before, CarrySmudgeChecks.all(blank)), "Wash comb on blank paper lays nothing");
    }

    /** 6. Comb (Clear water with a Rake) combs floating rings along the stroke, keeping their area, adding nothing. */
    static void floatComb() {
        ToneDocument d = InkWashLayers.blank(FloatChecks.W, FloatChecks.H);
        WatercolorSession s = new WatercolorSession();
        FloatChecks.rings(d, s, 200, 150, 4);
        FloatBath b = FloatChecks.bath(s);
        float[] box = b.box();
        int rings = b.rings.size();
        double area = 0; for (FloatBath.Ring g : b.rings) area += g.area();
        FloatChecks.stroke(d, s, preset("Comb"), 14, FloatChecks.line(20, 150, 400, 150));
        double after = 0; for (FloatBath.Ring g : b.rings) after += g.area();
        note("Comb: right edge " + box[2] + " → " + b.box()[2] + ", area " + String.format(Locale.ROOT, "%.2f%%", 100 * (after / area - 1)));
        check(b.box()[2] > box[2] + 10 && b.rings.size() == rings && Math.abs(after / area - 1) < .03, "Comb combs the rings along, adding nothing, keeping their area");
        ToneDocument blank = InkWashLayers.blank(FloatChecks.W, FloatChecks.H);
        byte[][] before = CarrySmudgeChecks.all(blank);
        WatercolorSession t = new WatercolorSession();
        FloatChecks.stroke(blank, t, preset("Comb"), 14, FloatChecks.line(30, 40, 380, 260));
        t.settleNow(blank);
        check(ModularStrokeChecks.same(before, CarrySmudgeChecks.all(blank)), "Comb on blank paper lays nothing");
    }

    /** 7. Blend stump and Wash comb drawn quick (rendered at lift) end as drawn live, reading only the page as found. */
    static void quickIsLive() {
        for (Object[] c : new Object[][]{{"Blend stump", 120.0}, {"Wash comb", 120.0}, {"Blend stump", 20.0}}) {
            ModularBrush b = preset((String)c[0]);
            ToneDocument live = CarrySmudgeChecks.page(CarrySmudgeChecks.ownLayer(b), CarrySmudgeChecks::mark), quick = CarrySmudgeChecks.page(CarrySmudgeChecks.ownLayer(b), CarrySmudgeChecks::mark);
            CarrySmudgeChecks.live(live, b, (Double)c[1], 1, null, CarrySmudgeChecks.wave());
            DrawingStroke q = QuickStroke.quickly(quick, b, (Double)c[1], 1, null);
            CarrySmudgeChecks.feed(q, CarrySmudgeChecks.wave());
            check(ModularStrokeChecks.same(CarrySmudgeChecks.all(live), CarrySmudgeChecks.all(quick)), c[0] + " at " + c[1] + " px: quick ends as live");
        }
    }

    /** 8. A big Blend stump's dabs over several cores draw what one does. */
    static void cores() {
        ModularBrush b = preset("Blend stump");
        byte[][][] pages = new byte[2][][];
        boolean was = Cores.shared;
        try {
            for (int k = 0; k < 2; k++) {
                Cores.shared = k == 0;
                ToneDocument d = CarrySmudgeChecks.page(InkWashLayers.INK, CarrySmudgeChecks::mark);
                CarrySmudgeChecks.live(d, b, 200, 1, null, CarrySmudgeChecks.wave());
                pages[k] = CarrySmudgeChecks.all(d);
            }
        } finally { Cores.shared = was; }
        check(ModularStrokeChecks.same(pages[0], pages[1]), "a 200 px Blend stump on several cores draws what one core does");
    }

    /** Hatching across the band the strokes rub: 3 px in 9 at {@code tone}, {@code shift} px along. */
    static java.util.function.IntBinaryOperator hatch(int tone, int shift) { return (x, y) -> Math.floorMod(x + shift, 9) < 3 ? tone : 255; }
    /** {@code tone(x, y)} (255: nothing) laid on {@code layer} as erasable as {@code erasable}. */
    static void lay(ToneDocument d, int layer, double erasable, java.util.function.IntBinaryOperator tone) {
        d.selectLayer(layer);
        d.begin(erasable);
        int[] row = new int[d.width];
        for (int y = 0; y < d.height; y++) {
            for (int x = 0; x < d.width; x++) { int t = tone.applyAsInt(x, y); row[x] = t >= 255 ? -1 : t; }
            d.setTones(0, y, d.width, 1, row);
        }
        d.finish();
    }
    /** Layer {@code layer}'s darkness over white along the band the strokes rub: the mean step between neighbours, and the mean. */
    static double[] band(ToneDocument d, int layer) {
        int[] dark = new int[W * H];
        d.layerDarkness(layer, 0, 0, W, H, dark);
        double steps = 0, sum = 0; int n = 0;
        for (int y = 172; y <= 188; y++) for (int x = 101; x <= 380; x++) { steps += Math.abs(dark[y * W + x] - dark[y * W + x - 1]); sum += dark[y * W + x]; n++; }
        return new double[]{steps / n, sum / n};
    }
    static boolean sameLayer(byte[][] a, byte[][] b, int layer) { return java.util.Arrays.equals(a[2 * layer], b[2 * layer]) && java.util.Arrays.equals(a[2 * layer + 1], b[2 * layer + 1]); }

    /**
     * 9. On a pair's ink layer the Blend stump blends the pair's wash too ("blend washes", the owner, 2026-10-19): hatching
     * on the wash softens as on the ink, keeping its darkness, the ink layer left as it was; with hatching on both, both
     * soften; one Undo takes back both, Redo lays both; drawn quick it ends as drawn live; on a hidden wash, or for a
     * paint's smudge (Wash comb, on the wash), the other layer is as it was.
     */
    static void washes() {
        ModularBrush blend = preset("Blend stump");
        ToneDocument d = CarrySmudgeChecks.page(InkWashLayers.WASH, hatch(90, 0));
        byte[][] before = CarrySmudgeChecks.all(d);
        double[] wash0 = band(d, InkWashLayers.WASH);
        CarrySmudgeChecks.live(d, blend, 28, 1, null, CarrySmudgeChecks.across(40, 440, 180));
        double[] wash1 = band(d, InkWashLayers.WASH);
        byte[][] after = CarrySmudgeChecks.all(d);
        note(String.format(Locale.ROOT, "Blend stump over a wash's hatching: steps %.1f → %.1f, darkness %.1f → %.1f", wash0[0], wash1[0], wash0[1], wash1[1]));
        check(wash1[0] < .75 * wash0[0] && Math.abs(wash1[1] - wash0[1]) < 2, "Blend stump softens a wash's hatching without darkening or lightening it: steps " + wash0[0] + " → " + wash1[0] + ", darkness " + wash0[1] + " → " + wash1[1]);
        check(sameLayer(before, after, InkWashLayers.INK), "and lays nothing on the empty ink layer");
        check(d.undo() && ModularStrokeChecks.same(before, CarrySmudgeChecks.all(d)), "one Undo takes it back");
        check(d.redo() && ModularStrokeChecks.same(after, CarrySmudgeChecks.all(d)) && !d.canRedo(), "and Redo lays it again");
        // Hatching on both halves: both soften.
        ToneDocument both = CarrySmudgeChecks.page(InkWashLayers.WASH, hatch(110, 0));
        CarrySmudgeChecks.lay(both, InkWashLayers.INK, hatch(70, 4));
        double[] ink0 = band(both, InkWashLayers.INK), under0 = band(both, InkWashLayers.WASH);
        CarrySmudgeChecks.live(both, blend, 28, 1, null, CarrySmudgeChecks.across(40, 440, 180));
        double[] ink1 = band(both, InkWashLayers.INK), under1 = band(both, InkWashLayers.WASH);
        note(String.format(Locale.ROOT, "over both: the ink's steps %.1f → %.1f, the wash's %.1f → %.1f", ink0[0], ink1[0], under0[0], under1[0]));
        check(ink1[0] < .75 * ink0[0] && under1[0] < .75 * under0[0], "with marks on both layers, both soften: ink " + ink0[0] + " → " + ink1[0] + ", wash " + under0[0] + " → " + under1[0]);
        // Quick (rendered at lift) and small: as drawn live, both layers.
        for (double size : new double[]{120, 20}) {
            ToneDocument live = CarrySmudgeChecks.page(InkWashLayers.WASH, CarrySmudgeChecks::mark), quick = CarrySmudgeChecks.page(InkWashLayers.WASH, CarrySmudgeChecks::mark);
            byte[][] found = CarrySmudgeChecks.all(live);
            CarrySmudgeChecks.live(live, blend, size, 1, null, CarrySmudgeChecks.wave());
            DrawingStroke q = QuickStroke.quickly(quick, blend, size, 1, null);
            CarrySmudgeChecks.feed(q, CarrySmudgeChecks.wave());
            check(ModularStrokeChecks.same(CarrySmudgeChecks.all(live), CarrySmudgeChecks.all(quick)) && !sameLayer(found, CarrySmudgeChecks.all(live), InkWashLayers.WASH),
                    "a " + size + " px Blend stump over a wash changes it, and drawn quick ends as drawn live");
        }
        // A hidden wash isn't blended; nor is the ink over a paint's smudge.
        ToneDocument hidden = CarrySmudgeChecks.page(InkWashLayers.WASH, hatch(90, 0));
        hidden.setLayerVisible(InkWashLayers.WASH, false);
        byte[][] h0 = CarrySmudgeChecks.all(hidden);
        CarrySmudgeChecks.live(hidden, blend, 28, 1, null, CarrySmudgeChecks.across(40, 440, 180));
        check(ModularStrokeChecks.same(h0, CarrySmudgeChecks.all(hidden)), "a hidden wash is left as it was");
        ToneDocument inked = CarrySmudgeChecks.page(InkWashLayers.INK, hatch(90, 0));
        byte[][] i0 = CarrySmudgeChecks.all(inked);
        CarrySmudgeChecks.live(inked, preset("Wash comb"), 60, 1, null, CarrySmudgeChecks.across(40, 440, 180));
        check(ModularStrokeChecks.same(i0, CarrySmudgeChecks.all(inked)), "Wash comb, a paint's smudge, leaves the ink above it as it was");
    }

    /**
     * 10. Erasable over both halves: a permanent ink line (Erasable 0) stays byte for byte while the wash under it blends; a
     * dried wash blends by how liftable it is (Erasable .4 less than 1, 0 not at all); where masking fluid lies over the
     * wash, it stays as it was.
     */
    static void erasable() {
        ModularBrush blend = preset("Blend stump");
        ToneDocument d = InkWashLayers.blank(W, H);
        lay(d, InkWashLayers.WASH, 1, hatch(90, 0));
        lay(d, InkWashLayers.INK, 0, (x, y) -> x >= 200 && x < 206 ? 0 : 255);
        byte[][] before = CarrySmudgeChecks.all(d);
        double[] wash0 = band(d, InkWashLayers.WASH);
        CarrySmudgeChecks.live(d, blend, 28, 1, null, CarrySmudgeChecks.across(40, 440, 180));
        byte[][] after = CarrySmudgeChecks.all(d);
        double[] wash1 = band(d, InkWashLayers.WASH);
        check(sameLayer(before, after, InkWashLayers.INK), "permanent ink is never smeared: its layer byte for byte");
        check(wash1[0] < .75 * wash0[0], "while the wash under it blends: " + wash0[0] + " → " + wash1[0]);
        // How far a wash blends by how liftable it is.
        double[] drop = new double[3], erasables = {1, .4, 0};
        for (int k = 0; k < 3; k++) {
            ToneDocument w = InkWashLayers.blank(W, H);
            lay(w, InkWashLayers.WASH, erasables[k], hatch(90, 0));
            byte[][] w0 = CarrySmudgeChecks.all(w);
            double e0 = band(w, InkWashLayers.WASH)[0];
            CarrySmudgeChecks.live(w, blend, 28, 1, null, CarrySmudgeChecks.across(40, 440, 180));
            drop[k] = 1 - band(w, InkWashLayers.WASH)[0] / e0;
            if (k == 2) check(ModularStrokeChecks.same(w0, CarrySmudgeChecks.all(w)), "a permanent wash (Erasable 0) stays byte for byte");
        }
        note(String.format(Locale.ROOT, "a wash's steps soften by %.0f%% at Erasable 1, %.0f%% at .4, %.0f%% at 0", 100 * drop[0], 100 * drop[1], 100 * drop[2]));
        check(drop[0] > drop[1] + .1 && drop[1] > .05 && drop[2] == 0, "a dried wash blends by how liftable it is: " + drop[0] + ", " + drop[1] + ", " + drop[2]);
        // Masking fluid over the right of the page: the wash under it stays as it was.
        ToneDocument m = InkWashLayers.blank(W, H);
        lay(m, InkWashLayers.WASH, 1, hatch(90, 0));
        m.beginFluid(1);
        int[] row = new int[W];
        for (int y = 0; y < H; y++) { for (int x = 0; x < W; x++) row[x] = x >= 240 ? 0 : -1; m.setTones(0, y, W, 1, row); }
        m.finish();
        int[] wash = new int[W * H], was = new int[W * H];
        m.layerDarkness(InkWashLayers.WASH, 0, 0, W, H, was);
        CarrySmudgeChecks.live(m, blend, 28, 1, null, CarrySmudgeChecks.across(40, 440, 180));
        m.layerDarkness(InkWashLayers.WASH, 0, 0, W, H, wash);
        boolean kept = true, moved = false;
        for (int y = 160; y <= 200; y++) for (int x = 60; x < W; x++) {
            if (x >= 250) kept &= wash[y * W + x] == was[y * W + x];
            else if (x < 220) moved |= wash[y * W + x] != was[y * W + x];
        }
        check(kept && moved, "masking fluid blocks it: the wash under the fluid as it was, beside it blended");
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}

