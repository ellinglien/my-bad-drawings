package io.github.mpdairy.monopaint;

import java.util.Arrays;

public final class DryingWaterChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        settles(); onlyDarkens(); ends(); settleNow(); settleMidway(); dropped(); oneUndo(); opaque(); huge(); sample();
        System.out.println("PASS: a Drying wash with Settle keeps settling after lift, only darkening, for its drying time; a new stroke lays what's left where it is; anything else changing the page stops it; the stroke and its settling are one undo step; opaque paint doesn't settle; laid midway it keeps its pigment; a wash too big to hold is laid settled at lift; a sample ends settled");
    }
    static ModularBrush brush(double settle, double drying) {
        ModularBrush b = ModularCodeChecks.fixture("wet-wash");
        b.part(BrushPart.LIFE).set(PartDial.SETTLE, settle); b.part(BrushPart.LIFE).set(PartDial.DRYING, drying);
        return b;
    }
    static ToneDocument drawn(WatercolorSession session, ModularBrush b) {
        ToneDocument d = InkWashLayers.blank(480, 360);
        d.selectLayer(InkWashLayers.WASH);
        ModularStroke s = new ModularStroke(d, b, 80, 1, session);
        for (int i = 0; i <= 120; i++) { double t = i / 120.0; s.sample((float)(40 + 400 * t), (float)(180 + 60 * Math.sin(2 * Math.PI * t)), ToolSettings.pressure(.8f), 0, 0, i * 5L); }
        s.finish();
        return d;
    }
    static int[] tones(ToneDocument d) { int[] t = new int[d.width * d.height]; for (int i = 0; i < t.length; i++) t[i] = d.compositeTone(i % d.width, i / d.width); return t; }
    static void settles() {
        WatercolorSession session = new WatercolorSession();
        ToneDocument d = drawn(session, brush(.8, .2));
        check(session.settling(), "A Drying wash with Settle keeps settling after lift");
        int[] before = tones(d);
        int ticks = 0; while (session.tickDrying(d) && ticks < 1000) ticks++;
        int changed = 0; int[] after = tones(d); for (int i = 0; i < after.length; i++) if (after[i] != before[i]) changed++;
        check(changed > 100, "Settling changes the wash: " + changed + " pixels over " + ticks + " ticks");
    }
    static void onlyDarkens() {
        WatercolorSession session = new WatercolorSession();
        ToneDocument d = drawn(session, brush(.8, .2));
        int[] prev = tones(d);
        while (session.tickDrying(d)) { int[] now = tones(d); for (int i = 0; i < now.length; i++) check(now[i] <= prev[i], "Settling only darkens"); prev = now; }
    }
    static void ends() {
        WatercolorSession session = new WatercolorSession();
        ToneDocument d = drawn(session, brush(.8, 0));
        int ticks = 0; while (session.tickDrying(d)) ticks++;
        check(ticks <= PartDial.wetMs(0) / 100 + 1 && !session.settling(), "It ends with its drying time: " + ticks + " ticks");
    }
    static void settleNow() {
        WatercolorSession session = new WatercolorSession();
        ToneDocument d = drawn(session, brush(.8, 1));
        int[] before = tones(d);
        session.settleNow(d);
        int darker = 0; int[] after = tones(d);
        for (int i = 0; i < after.length; i++) { check(after[i] <= before[i], "Laying it now only darkens"); if (after[i] < before[i]) darker++; }
        check(darker > 100 && !session.settling(), "A new stroke lays what's still suspended where it is");
    }
    static void dropped() {
        WatercolorSession session = new WatercolorSession();
        ToneDocument d = drawn(session, brush(.8, 1));
        d.begin(); d.setTone(5, 5, 0); d.finish();
        int[] before = tones(d);
        check(!session.tickDrying(d) && !session.settling(), "Anything else changing the page stops it");
        int[] after = tones(d); for (int i = 0; i < after.length; i++) check(after[i] == before[i], "without writing");
    }
    static void oneUndo() {
        WatercolorSession session = new WatercolorSession();
        ToneDocument d = drawn(session, brush(.8, .2));
        while (session.tickDrying(d)) { }
        d.undo();
        for (int y = 0; y < d.height; y += 5) for (int x = 0; x < d.width; x += 5) check(d.compositeTone(x, y) == 255, "One Undo takes back the stroke and its settling");
    }
    static void opaque() {
        WatercolorSession session = new WatercolorSession();
        ModularBrush b = brush(.8, .2); b.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.COVER));
        drawn(session, b);
        check(!session.settling(), "Opaque paint doesn't settle");
        WatercolorSession none = new WatercolorSession();
        drawn(none, brush(0, .5));
        check(!none.settling(), "nor a wash with Settle 0");
    }
    /** 255 − tone summed over the page: how much darkness it holds. */
    static long ink(ToneDocument d) { long sum = 0; for (int t : tones(d)) sum += 255 - t; return sum; }
    static void settleMidway() {
        WatercolorSession ticked = new WatercolorSession(), laid = new WatercolorSession();
        ToneDocument full = drawn(ticked, brush(.8, .2)), now = drawn(laid, brush(.8, .2));
        long lift = ink(full);
        while (ticked.tickDrying(full)) { }
        for (int i = 0; i < 4; i++) laid.tickDrying(now);
        int[] before = tones(now);
        laid.settleNow(now);
        int[] after = tones(now);
        for (int i = 0; i < after.length; i++) check(after[i] <= before[i], "Laying it midway only darkens");
        double share = (double)(ink(now) - lift) / (ink(full) - lift);
        check(!laid.settling() && share > .98 && share < 1.02, "Laid midway (a pause, another page, a layer change), the wash keeps the pigment it would have laid, only without the rest of its drift: " + share);
    }
    /** A {@code w × h} page with a wash of darkness {@code dark} on it, as a Drying stroke leaves it (its held-back share not yet laid). */
    static ToneDocument washed(int w, int h, float[] dark) {
        ToneDocument d = InkWashLayers.blank(w, h);
        d.selectLayer(InkWashLayers.WASH);
        d.begin();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (dark[y * w + x] > 0) d.setTone(x, y, (int)Math.round(255 * (1 - dark[y * w + x] * .64)));
        d.finish();
        return d;
    }
    /** A soft disc of darkness across a {@code w × h} box. */
    static float[] disc(int w, int h) {
        float[] dark = new float[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double r = Math.hypot(x - w / 2.0, y - h / 2.0) / (Math.min(w, h) * .45);
            if (r < 1) dark[y * w + x] = (float)(.7 * (1 - r * r));
        }
        return dark;
    }
    static void huge() {
        StrokePaper paper = new StrokePaper(ResolvedBrush.of(brush(.8, .2), 80, 1).paper);
        int w = 300, h = 240;
        // A wash over the cap (1000 cells here): laid settled at lift, to the pixels of one laid at once before its first tick.
        ToneDocument capped = washed(w, h, disc(w, h)), held = washed(w, h, disc(w, h)), ticked = washed(w, h, disc(w, h));
        long lift = ink(capped);
        check(DryingWater.lay(capped, paper, 0, 0, w, h, disc(w, h), .2, .8, 7, 9, 1000) == null && ink(capped) > lift, "A wash over the cap is laid settled at lift, not held");
        DryingWater water = DryingWater.lay(held, paper, 0, 0, w, h, disc(w, h), .2, .8, 7, 9);
        water.finishNow(held);
        check(Arrays.equals(tones(capped), tones(held)), "to the pixels of one laid at once before its first tick");
        DryingWater animated = DryingWater.lay(ticked, paper, 0, 0, w, h, disc(w, h), .2, .8, 7, 9);
        while (animated.step(ticked)) { }
        double share = (double)(ink(capped) - lift) / (ink(ticked) - lift);
        check(share > .98 && share < 1.02, "and keeps the pigment a ticked settle lays, without its drift: " + share);
        // The cap itself: a page-wide wash of more cells than MOST_CELLS is laid at lift.
        int W = 1040, H = 1040;
        float[] even = new float[W * H]; Arrays.fill(even, .5f);
        check((W / 2) * (H / 2) > DryingWater.MOST_CELLS, "the big wash is over the cap");
        ToneDocument big = washed(W, H, even);
        long bigLift = ink(big);
        check(DryingWater.lay(big, paper, 0, 0, W, H, even, .2, .8, 7, 9) == null && ink(big) > bigLift, "A wash of more than " + DryingWater.MOST_CELLS + " cells is laid settled at lift");
    }
    static void sample() {
        WatercolorSession session = new WatercolorSession();
        ToneDocument held = drawn(session, brush(.8, .2)), sample = drawn(null, brush(.8, .2));
        session.settleNow(held);
        check(Arrays.equals(tones(held), tones(sample)), "A sample (no session) ends as its stroke laid settled at once, not lighter by what was held back");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
