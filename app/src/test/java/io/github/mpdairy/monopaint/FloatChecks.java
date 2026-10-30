package io.github.mpdairy.monopaint;

import java.util.Random;

/** Float (suminagashi): the Float spec's checks (docs/archive/superpowers/specs/2026-10-16-float-design.md, "Checks"). */
public final class FloatChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        drops(); combing(); crisp(); clearWaterLaysNothing(); printIsWhatFloated(); undo(); drift(); printsOnTime(); deterministic();
        quickIsLive(); ownLayer(); whatPrintsFirst(); slowWashStays(); showsWhereChanged(); codes(); rolls(); cores(); worker(); full(); timing();
        System.out.println("PASS: Float: drops push rings outward keeping their area, clear water combs them and lays nothing, rings print crisp at 16 greys, the print is what floated, Undo takes back one stroke while it floats and the whole print after, a level page doesn't drift and a tilted one drifts downhill, it prints on time, the log and the cores give the same bath, a stroke joining the bath finds the worker idle and the print is the bath as last left, a full bath prints at lift, quick ends as live, it keeps to its layer, a stroke that doesn't float prints it first, codes round-trip and Float rolls rarely from the seed changing no other roll");
    }

    static final int W = 420, H = 300;
    /** The Tools page's pair: Sumi, and Clear water to alternate with it. */
    static ModularBrush sumi() { return preset("Sumi"); }
    static ModularBrush clear() { return preset("Clear water"); }
    static ModularBrush preset(String name) { return find(name).brush().parts; }
    /**
     * The preset named {@code name}, retired ones too (the old Gouache and Oils): the checks that drew them still do. A
     * Watercolor wash retired when its wet one replaced it (the wet paint spec, section 9) is the old one: the checks of
     * the Slow and Quick washes (settling, gravity, erasing, smudging) still draw them; the wet ones are WetPaintChecks'.
     */
    static ToolPresets.Preset find(String name) {
        for (ToolPresets.Preset p : ToolPresets.retired()) if (p.name.equals(name) && p.style.name.equals("Watercolor")) return p;
        for (ToolPresets.Preset p : ToolPresets.withRetired()) if (p.name.equals(name)) return p;
        throw new AssertionError("no " + name);
    }
    /** {@code b} with another tip (a hake, a rake). */
    static ModularBrush tip(ModularBrush b, PartChoice tip) {
        ModularBrush c = b.copy();
        c.set(BrushPart.TIP, new ModularBrush.Part(tip));
        return c.normalize();
    }
    private static long clock;
    /** A stroke as the drawing screen starts one ({@link FloatPictures#stroke}): live or quick as {@code quick} says (null: the rule). */
    static void stroke(ToneDocument d, WatercolorSession s, ModularBrush b, double size, double[][] path, Boolean quick) {
        // As the drawing screen starts a stroke (InkWash.stroke): what floats or settles first, then the drying dropped.
        s.settleBefore(d, b);
        s.dry("stroke");
        DrawingStroke st = quick == null ? QuickStroke.start(d, b, size, 1, s) : quick ? QuickStroke.quickly(d, b, size, 1, s) : ModularStroke.start(d, b, size, 1, s);
        for (double[] p : path) { st.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), 0, 0, clock + (long) p[3]); st.present(); }
        st.finish();
        clock += 10_000;
    }
    static void stroke(ToneDocument d, WatercolorSession s, ModularBrush b, double size, double[][] path) { stroke(d, s, b, size, path, null); }
    static double[][] tap(double x, double y, int ms) { return tap(x, y, .7, ms); }
    /** A tap at {@code x, y} at pressure travel {@code p}, the pen held {@code ms} (a sample every 8 ms, jittering under a pixel). */
    static double[][] tap(double x, double y, double p, int ms) {
        int n = Math.max(1, ms / 8);
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) o[i] = new double[]{x + .2 * Math.sin(i), y + .2 * Math.cos(i * 1.3), p, i * 8};
        return o;
    }
    static double[][] line(double x0, double y0, double x1, double y1) { return line(x0, y0, x1, y1, .7); }
    /** A straight line at pressure travel {@code p}, a sample every 2.5 px, 4 ms apart. */
    static double[][] line(double x0, double y0, double x1, double y1, double p) {
        int n = (int) Math.max(2, Math.ceil(Math.hypot(x1 - x0, y1 - y0) / 2.5));
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) { double u = (double) i / n; o[i] = new double[]{x0 + (x1 - x0) * u, y0 + (y1 - y0) * u, p, i * 4}; }
        return o;
    }
    /** Sumi and Clear water in turn at {@code x, y}, {@code n} times each. */
    static void rings(ToneDocument d, WatercolorSession s, double x, double y, int n) {
        for (int i = 0; i < n; i++) { stroke(d, s, sumi(), 8, tap(x, y, 250 + 40 * i)); stroke(d, s, clear(), 8, tap(x, y, 180)); }
    }
    static FloatBath bath(WatercolorSession s) { return s.floating().bath; }

    /** 1. A drop pushes everything floating outward and keeps every ring's area. */
    static void drops() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, sumi(), 8, tap(150, 150, 200));
        FloatBath b = bath(s);
        FloatBath.Ring first = b.rings.get(0);
        double a0 = first.area(), x0 = first.box[0];
        // Drops beside it (a drop inside a ring makes its hole: the paint's area is kept, the outline's grows by the hole).
        stroke(d, s, clear(), 8, tap(220, 150, 400));
        stroke(d, s, sumi(), 8, tap(226, 155, 300));
        check(b.rings.get(0) == first && Math.abs(first.area() / a0 - 1) < .005, "a drop keeps every ring's area (" + String.format("%.3f%%", 100 * (first.area() / a0 - 1)) + ")");
        check(first.box[0] < x0 - 3, "and pushes it outward (" + x0 + " → " + first.box[0] + ")");
    }
    /** 2. Clear water dragged through rings combs them: points move along the stroke, area nearly kept. */
    static void combing() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        rings(d, s, 200, 150, 4);
        FloatBath b = bath(s);
        double[] areas = new double[b.rings.size()];
        for (int i = 0; i < areas.length; i++) areas[i] = b.rings.get(i).area();
        float[] box = b.box();
        stroke(d, s, clear(), 14, line(20, 150, 400, 150));
        float[] after = b.box();
        double worst = 0;
        for (int i = 0; i < areas.length; i++) worst = Math.max(worst, Math.abs(b.rings.get(i).area() / areas[i] - 1));
        check(after[2] > box[2] + 10, "a clear-water drag pulls the rings along it (right edge " + box[2] + " → " + after[2] + ")");
        check(worst < .03, "and keeps their area within 3% (" + String.format("%.2f%%", 100 * worst) + ")");
        ModularBrush rake = tip(clear(), PartChoice.MULTI_LINE);
        int strokes = b.strokes();
        stroke(d, s, rake, 14, line(200, 20, 200, 280));
        check(b.strokes() == strokes + 1 && b.rings.size() == areas.length, "a rake combs the same bath, adding nothing");
    }
    /** 3. Sumi and Clear water in turn print as rings: many edges along a ray, each crisp at 16 greys. */
    static void crisp() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        rings(d, s, 150, 150, 6);
        s.settleNow(d);
        int changes = 0, grey = 0, last = -1;
        for (int x = 150; x < W; x++) {
            int v = Math.round(d.compositeTone(x, 150) / 17f) * 17, k = v < 120 ? 0 : v > 200 ? 1 : -1;
            if (k < 0) grey++; else { if (last >= 0 && k != last) changes++; last = k; }
        }
        check(changes >= 10 && grey <= 2.5 * changes, "rings print crisp: " + changes + " edges along a ray, " + String.format("%.1f", grey / (double) Math.max(1, changes)) + " in-between pixels an edge");
    }
    /** Clear water on blank paper lays nothing: a drag and a tap. */
    static void clearWaterLaysNothing() {
        ToneDocument d = InkWashLayers.blank(W, H);
        byte[][] before = CarrySmudgeChecks.all(d);
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, clear(), 14, line(30, 40, 380, 260));
        stroke(d, s, clear(), 14, tap(100, 200, 300));
        s.settleNow(d);
        check(ModularStrokeChecks.same(before, CarrySmudgeChecks.all(d)), "clear water on blank paper lays nothing");
    }
    /** 4. The print is what floated: the same greys, the paper's tooth taking a little less; nothing else changes. */
    static void printIsWhatFloated() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        rings(d, s, 150, 150, 4);
        stroke(d, s, sumi(), 8, line(40, 60, 380, 80));
        int[] floating = tones(d);
        s.settleNow(d);
        check(s.floating() == null, "settling prints the bath");
        int[] printed = tones(d);
        int worse = 0, marked = 0;
        for (int i = 0; i < printed.length; i++) {
            int dark0 = 255 - floating[i], dark1 = 255 - printed[i];
            if (dark0 > 0) marked++;
            if (dark1 > dark0 || dark1 < dark0 * .9 - 2) worse++;
        }
        check(marked > 1000 && worse == 0, "the print is what floated, at most a tenth lighter in the paper's valleys (" + worse + " of " + marked + " off)");
    }
    /** 5. Undo while it floats takes back one stroke; after the print, one Undo takes back the whole print. */
    static void undo() {
        ToneDocument d = InkWashLayers.blank(W, H);
        byte[][] blank = CarrySmudgeChecks.all(d);
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, sumi(), 8, tap(150, 150, 300));
        stroke(d, s, clear(), 8, tap(151, 150, 200));
        int hash2 = bath(s).hash();
        byte[][] page2 = CarrySmudgeChecks.all(d);
        stroke(d, s, sumi(), 8, line(40, 150, 380, 160));
        check(bath(s).strokes() == 3, "three strokes float on one bath");
        check(s.undo(d) && s.floating() != null && bath(s).strokes() == 2 && bath(s).hash() == hash2, "Undo while it floats takes back the last stroke, still floating");
        check(ModularStrokeChecks.same(page2, CarrySmudgeChecks.all(d)), "and the page shows the bath as it was");
        stroke(d, s, clear(), 14, line(150, 40, 150, 280));
        s.settleNow(d);
        check(s.floating() == null && !ModularStrokeChecks.same(blank, CarrySmudgeChecks.all(d)), "printed");
        check(s.undo(d) && ModularStrokeChecks.same(blank, CarrySmudgeChecks.all(d)), "one Undo after the print takes back the whole sheet, every layer");
        check(d.redo() && !ModularStrokeChecks.same(blank, CarrySmudgeChecks.all(d)), "and Redo prints it again");
        // With one stroke, Undo is the page's own.
        ToneDocument e = InkWashLayers.blank(W, H);
        WatercolorSession t = new WatercolorSession();
        stroke(e, t, sumi(), 8, tap(150, 150, 300));
        check(t.undo(e) && t.floating() == null && ModularStrokeChecks.same(blank, CarrySmudgeChecks.all(e)), "Undo of a bath's only stroke leaves the page as it was, nothing floating");
    }
    /** 6. A level page, and a tilt inside the dead zone, don't drift; tilted, it drifts downhill, area kept. */
    static void drift() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        rings(d, s, 150, 120, 3);
        FloatBath b = bath(s);
        int h0 = b.hash();
        byte[][] page = CarrySmudgeChecks.all(d);
        for (int i = 0; i < 5; i++) s.tickDrying(d);
        s.tilt = () -> PageTilt.pull(.05, .05);
        for (int i = 0; i < 5; i++) s.tickDrying(d);
        check(b.hash() == h0 && ModularStrokeChecks.same(page, CarrySmudgeChecks.all(d)), "a level page, and one inside the dead zone, doesn't drift");
        check(!s.wantsTilt() || s.floating().drifts(), "the sensor listens while it can drift");
        FloatBath.Ring g = b.rings.get(0);
        double y0 = (g.box[1] + g.box[3]) / 2, a0 = g.area();
        s.tilt = () -> PageTilt.of(0, .9);
        for (int i = 0; i < 20; i++) s.tickDrying(d);
        double moved = (g.box[1] + g.box[3]) / 2 - y0;
        check(moved > 20 && Math.abs(g.area() / a0 - 1) < .01, "tilted, it drifts downhill (" + String.format("%.1f", moved) + " px in 2 s at Drift .5), area kept");
    }
    /** 7. It prints by itself once its floating time has passed since the last touch. */
    static void printsOnTime() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, sumi(), 8, tap(150, 150, 300));
        long ms = PartDial.wetMs(.6);
        int ticks = 0;
        while (s.tickDrying(d)) ticks++;
        check(s.floating() == null && Math.abs((ticks + 1) * 1000L / DryingWater.TPS - ms) <= 100, "it prints when its floating time is up (" + (ticks + 1) + " ticks for " + ms + " ms)");
    }
    /** 8. The log is the bath, and the same strokes float the same. */
    static void deterministic() {
        int[] hash = new int[2];
        FloatBath last = null;
        for (int k = 0; k < 2; k++) {
            ToneDocument d = InkWashLayers.blank(W, H);
            WatercolorSession s = new WatercolorSession();
            rings(d, s, 150, 150, 3);
            stroke(d, s, tip(clear(), PartChoice.MULTI_LINE), 12, line(30, 150, 390, 150));
            hash[k] = bath(s).hash();
            last = bath(s);
        }
        check(hash[0] == hash[1], "the same strokes float the same");
        check(last.replay().hash() == hash[1], "replaying the log gives the same bath");
    }
    /** Quick and live strokes end the same: the same samples give the same bath and the same page. */
    static void quickIsLive() {
        byte[][][] pages = new byte[2][][];
        int[] hash = new int[2];
        for (int k = 0; k < 2; k++) {
            boolean quick = k == 1;
            ToneDocument d = InkWashLayers.blank(W, H);
            WatercolorSession s = new WatercolorSession();
            clock = 0;
            stroke(d, s, sumi(), 8, tap(150, 150, 300), quick);
            stroke(d, s, clear(), 8, tap(150, 150, 200), quick);
            stroke(d, s, sumi(), 10, line(30, 100, 390, 180), quick);
            stroke(d, s, tip(clear(), PartChoice.HAKE), 40, line(30, 150, 390, 150), quick);
            hash[k] = bath(s).hash();
            s.settleNow(d);
            pages[k] = CarrySmudgeChecks.all(d);
        }
        check(hash[0] == hash[1], "quick and live strokes float the same bath");
        check(ModularStrokeChecks.same(pages[0], pages[1]), "and print the same page");
    }
    /** A bath on the ink layer leaves the wash layer and other pairs as they were. */
    static void ownLayer() {
        ToneDocument d = InkWashLayers.blank(W, H);
        CarrySmudgeChecks.lay(d, InkWashLayers.WASH, (x, y) -> x >= 100 && x < 220 ? 150 : 255);
        byte[][] before = CarrySmudgeChecks.all(d);
        WatercolorSession s = new WatercolorSession();
        rings(d, s, 160, 150, 3);
        s.settleNow(d);
        byte[][] after = CarrySmudgeChecks.all(d);
        boolean washSame = java.util.Arrays.equals(before[2 * InkWashLayers.WASH], after[2 * InkWashLayers.WASH]) && java.util.Arrays.equals(before[2 * InkWashLayers.WASH + 1], after[2 * InkWashLayers.WASH + 1]);
        check(washSame && !ModularStrokeChecks.same(before, after), "a bath on the ink layer leaves the wash layer as it was");
    }
    /** What prints first: a stroke that doesn't float, a layer change; not another Float stroke on the layer. */
    static void whatPrintsFirst() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, sumi(), 8, tap(150, 150, 300));
        FloatWater w = s.floating();
        stroke(d, s, clear(), 8, tap(150, 150, 300));
        stroke(d, s, sumi(), 4, tap(200, 150, 300));
        check(s.floating() == w && w.bath.strokes() == 3, "Sumi and Clear water, at any size, float on the same bath");
        ModularBrush pen = new ModularBrush("pen", 7, ModularStyle.named("Fineliner"));
        pen.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.INK));
        stroke(d, s, pen.normalize(), 4, line(20, 20, 300, 30));
        check(s.floating() == null, "a stroke that doesn't float prints it first");
        stroke(d, s, sumi(), 8, tap(150, 150, 300));
        ModularBrush washFloat = sumi();
        washFloat.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.WASH));
        s.settleBefore(d, washFloat);
        check(s.floating() == null, "a Float stroke on the other layer of the pair prints it first");
        stroke(d, s, sumi(), 8, tap(150, 150, 300));
        // A pause or another page settles the session first (DrawingPad.suspend, through DryingTicker.settleNow).
        s.settleNow(d);
        check(s.floating() == null, "a layer change prints it");
    }
    /** Clear water doesn't move a Slow wash still wet: the wash is laid first, and dry paint never moves under the water. */
    static void slowWashStays() {
        ModularBrush wash = new ModularBrush("wash", 7, ModularStyle.named("Watercolor"));
        wash.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.WASH));
        wash.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.DRYING));
        wash.part(BrushPart.LIFE).set(PartDial.SETTLE, .6);
        wash.normalize();
        byte[][][] pages = new byte[2][][];
        for (int k = 0; k < 2; k++) {
            ToneDocument d = InkWashLayers.blank(W, H);
            WatercolorSession s = new WatercolorSession();
            stroke(d, s, wash, 80, line(60, 150, 360, 150));
            check(s.settling(), "the wash is still wet");
            if (k == 0) s.settleNow(d);
            else stroke(d, s, tip(clear(), PartChoice.HAKE), 60, line(200, 30, 200, 270));
            s.settleNow(d);
            pages[k] = CarrySmudgeChecks.all(d);
        }
        check(ModularStrokeChecks.same(pages[0], pages[1]), "Clear water over a Slow wash still wet lays the wash and moves nothing");
    }
    /** A stroke's show reaches only where the bath changed; the rest stays as shown, a whole show changes no pixel by more than a grey. */
    static void showsWhereChanged() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        rings(d, s, 110, 150, 4);
        rings(d, s, 310, 150, 4);
        stroke(d, s, clear(), 14, line(250, 40, 370, 260));
        int[] partial = tones(d);
        FloatWater w = s.floating();
        d.begin(1, w.kinds());
        w.write(w.reach(), w.fullFilm(w.reach()), false);
        w.commit();
        int[] whole = tones(d), worst = {0};
        int off = 0;
        for (int i = 0; i < whole.length; i++) { int e = Math.abs(whole[i] - partial[i]); if (e > 17) off++; worst[0] = Math.max(worst[0], e); }
        check(off == 0, "a stroke's show is the whole show to within a grey (" + off + " pixels past, worst " + worst[0] + ")");
    }
    /** Budgets at about twice this Mac's time: a medium bath's drag moves and its whole show. */
    static void timing() {
        ToneDocument d = InkWashLayers.blank(1200, 1200);
        WatercolorSession s = new WatercolorSession();
        for (int k = 0; k < 3; k++) for (int i = 0; i < 10; i++) {
            stroke(d, s, sumi(), 8, tap(300 + 300 * k, 600, 250));
            stroke(d, s, clear(), 8, tap(300 + 300 * k, 600, 180));
        }
        FloatWater w = s.floating();
        double best = Double.MAX_VALUE, show = Double.MAX_VALUE;
        int points = w.bath.points;
        // Combing stretches the rings: the bath prints once it holds too many points (FloatWater.MOST_POINTS).
        for (int r = 0; r < 4 && s.floating() == w; r++) {
            long t1 = System.nanoTime();
            w.fullFilm(w.reach());
            show = Math.min(show, (System.nanoTime() - t1) / 1e6);
            points = w.bath.points;
            long t0 = System.nanoTime();
            stroke(d, s, tip(clear(), PartChoice.MULTI_LINE), 12, line(100, 560, 1100, 640));
            best = Math.min(best, (System.nanoTime() - t0) / 1e6 / 100);
        }
        Timing.limit(best < 1.2, String.format(java.util.Locale.ROOT, "a rake through a %d-point bath: %.2f ms a 10 px of pen, finished (budget 1.2)", points, best));
        Timing.limit(show < 25, String.format(java.util.Locale.ROOT, "its whole show: %.1f ms (budget 25)", show));
    }
    /**
     * With a worker (the drawing screen's), as the owner's Sumi strokes on the Manta (2026-10-22: a print worked out there
     * read a ring as a stroke grew it, and crashed the app): a stroke joining the bath finds no work there reading it,
     * at its start nor while it draws, and the print is the bath as the last stroke left it, as with no worker.
     */
    static void worker() {
        ToneDocument d = InkWashLayers.blank(W, H), plain = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession(), p = new WatercolorSession();
        s.worker = new SettleWorker();
        Random rnd = new Random(5);
        for (int i = 0; i < 16; i++) {
            ModularBrush b = i % 4 == 3 ? tip(clear(), PartChoice.MULTI_LINE) : i % 2 == 0 ? sumi() : clear();
            double x = 80 + rnd.nextInt(W - 160), y = 60 + rnd.nextInt(H - 120);
            double[][] path = i % 4 == 3 ? line(20, y, W - 20, y + 30) : tap(x, y, 200 + rnd.nextInt(200));
            s.settleBefore(d, b);
            s.dry("stroke");
            check(!s.working(), "stroke " + i + " joining the bath finds no work there reading it");
            DrawingStroke st = ModularStroke.start(d, b, 8, 1, s);
            for (double[] q : path) { st.sample((float) q[0], (float) q[1], ToolSettings.pressure((float) q[2]), 0, 0, clock + (long) q[3]); st.present(); check(!s.working(), "nor while it draws"); }
            st.finish();
            clock += 10_000;
            stroke(plain, p, b, 8, path, false);
        }
        s.settleNow(d); p.settleNow(plain);
        check(java.util.Arrays.equals(tones(d), tones(plain)), "the print is the bath as the last stroke left it, as with no worker");
    }
    /**
     * A bath past {@link FloatWater#MOST_POINTS} points or {@link FloatWater#MOST_PX} pixels of box prints at the lift of
     * the stroke that filled it, and the next drop floats on a new one: a stroke's work never grows past them.
     */
    static void full() {
        ToneDocument d = InkWashLayers.blank(1200, 1200);
        WatercolorSession s = new WatercolorSession();
        FloatWater first = null;
        int most = 0, strokes = 0;
        while (strokes < 400) {
            stroke(d, s, strokes % 2 == 0 ? sumi() : clear(), 10, tap(600 + strokes % 7, 600, 220));
            strokes++;
            if (first == null) first = s.floating();
            if (s.floating() != first) break;
            int[] b = first.box();
            check(first.bath.points <= FloatWater.MOST_POINTS && (long) (b[2] - b[0] + 1) * (b[3] - b[1] + 1) <= FloatWater.MOST_PX, "a floating bath is within its caps");
            most = Math.max(most, first.bath.points);
        }
        check(s.floating() == null && first.bath.rings.isEmpty(), "the stroke that filled it printed it (" + strokes + " strokes, " + most + " points floated at most)");
        stroke(d, s, sumi(), 10, tap(600, 600, 220));
        check(s.floating() != null && s.floating() != first && s.floating().bath.rings.size() == 1, "the next drop floats on a new bath");
    }
    /** Codes: a Float code round-trips byte for byte; the lab's reads as Float with its dials. */
    static void codes() {
        String lab = "f.M70i000000000at_000zz0000000000000_3wejs00_000_3lli0i0_0000000000000000000nz0.7";
        ModularBrush b = ModularCode.decode(lab);
        check(b != null && b.choice(BrushPart.LIFE) == PartChoice.FLOAT && ModularCode.encode(b).equals(lab), "the lab's Float code reads and writes back byte for byte");
        check(Math.abs(b.part(BrushPart.LIFE).get(PartDial.DRYING) - .6) < .001 && Math.abs(b.part(BrushPart.LIFE).get(PartDial.F_SPREAD) - .5) < .001 && Math.abs(b.part(BrushPart.LIFE).get(PartDial.DRIFT) - .5) < .001, "with its floating time, spread and drift");
        check(ModularWords.clearWater(b) && ModularWords.plainWords(b).startsWith("Clear water. Taps drop clear rings"), "and says it is clear water");
        check(ModularWords.choiceWords(sumi(), BrushPart.LIFE).equals("Float 7.7 s"), "Float's card says its floating time: " + ModularWords.choiceWords(sumi(), BrushPart.LIFE));
    }
    /** Rolls: every other part of every style rolls as before for the same Random; Float at its odds. */
    static void rolls() {
        int floats = 0, n = 0, other = 0;
        for (ModularStyle style : ModularStyle.all()) for (int i = 0; i < 400; i++) {
            ModularStyle.floatRolls = false;
            ModularBrush without = style.roll(new Random(9000L * (style.index + 1) + i));
            ModularStyle.floatRolls = true;
            ModularBrush with = style.roll(new Random(9000L * (style.index + 1) + i));
            n++;
            if (with.choice(BrushPart.LIFE) == PartChoice.FLOAT) {
                floats++;
                for (BrushPart p : BrushPart.values()) if (p != BrushPart.LIFE && !with.part(p).sameAs(without.part(p))) other++;
                check(style.floatOdds() > 0, style.name + " rolls Float only if it has odds for it");
            } else if (!ModularCode.encode(with).equals(ModularCode.encode(without))) other++;
        }
        check(other == 0, "Float rolls change no other roll (" + other + ")");
        int wild = 0, wn = 6000;
        ModularStyle anything = ModularStyle.named(ModularStyle.ANYTHING_GOES);
        Random random = new Random(5);
        for (int i = 0; i < wn; i++) if (anything.roll(random).choice(BrushPart.LIFE) == PartChoice.FLOAT) wild++;
        double p = 1 / 30.0;
        check(Math.abs(wild / (double) wn - p) <= 4 * Math.sqrt(p * (1 - p) / wn), "Anything goes rolls Float about 1 in 30 (" + wild + " of " + wn + ")");
        check(floats > 0, "some styles' rolls float (" + floats + " of " + n + ")");
    }
    /** The bath's moves and its show are the same on one core as on several. */
    static void cores() {
        int[] hash = new int[2];
        float[][] film = new float[2][];
        boolean was = Cores.shared;
        int cap = FloatBath.SHARE_POINTS;
        try {
            for (int k = 0; k < 2; k++) {
                Cores.shared = k == 1;
                ToneDocument d = InkWashLayers.blank(W, H);
                WatercolorSession s = new WatercolorSession();
                rings(d, s, 150, 150, 6);
                stroke(d, s, tip(clear(), PartChoice.MULTI_LINE), 12, line(30, 150, 390, 150));
                hash[k] = bath(s).hash();
                film[k] = s.floating().film(new int[]{0, 0, W - 1, H - 1}, FloatWater.FULL);
            }
        } finally { Cores.shared = was; }
        check(hash[0] == hash[1] && java.util.Arrays.equals(film[0], film[1]), "several cores float and show what one does");
    }

    static int[] tones(ToneDocument d) {
        int[] t = new int[d.width * d.height];
        for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) t[y * d.width + x] = d.compositeTone(x, y);
        return t;
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); if (System.getProperty("float.say") != null) System.out.println("  ok " + what); }
}
