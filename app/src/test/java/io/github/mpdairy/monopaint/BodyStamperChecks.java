package io.github.mpdairy.monopaint;

public final class BodyStamperChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        diameter(); disc(); hakeWidth(); hakeTurns(); hakeStart(); settleIdentity(); bands(); hollow(); impasto(); cover(); blend();
        System.out.println("PASS: paint dabs are as wide as their diameter; the hake sweeps a ribbon its head's width, fixed or following, and pivots round a U-turn without gaps; a hake's start lies along the stroke after a wobbly, hooked landing, laid as it comes or at lift, and a Wet hake's too; an empty look settles to itself, banded or not, on one core or several; a hollow edge darkens the rim, impasto lights the top edge, cover keeps paint as laid; a line loosens into the wash it crosses");
    }
    static final StrokePaper PAPER = new StrokePaper(BrushTexture.PLAIN);
    static StrokePoints line(float y, float x0, float x1, float p) {
        StrokePoints pts = new StrokePoints();
        for (float x = x0; x <= x1; x += 2) pts.add(x, y, p, (long)((x - x0) * 2.5), Float.NaN, Float.NaN);
        return pts;
    }
    static int rows(FloatTiles t, int x, float min) { int n = 0; for (int y = 0; y < t.height; y++) if (t.get(x, y) >= min) n++; return n; }
    static ResolvedBrush wash() { return ResolvedBrush.of(ModularCodeChecks.fixture("wet-wash"), 60, 1); }
    static void diameter() {
        ResolvedBrush.Body b = wash().body;
        check(Math.abs(BodyStamper.diameter(b, 100, 1, 0) - 100) < 1e-9, "A full press reaches the size");
        ResolvedBrush.Body hake = ResolvedBrush.of(ModularCodeChecks.fixture("wide-hake"), 100, 1).body;
        check(Math.abs(BodyStamper.diameter(hake, 100, 0, 0) - 80) < 1e-9, "A flat head is 80% of the size at the lightest touch");
    }
    static void disc() {
        FloatTiles t = new FloatTiles(800, 400, 0);
        BodyStamper.stamp(t, null, PAPER, wash().body, line(200, 40, 700, 1), 60, 1, new LineStamper.Meta(), true);
        check(Math.abs(rows(t, 300, .001f) - 60) <= 3, "A wash at full press is 60 px wide, its soft edge included: " + rows(t, 300, .001f));
    }
    static int hakeRows(boolean fixed, double angle) {
        ResolvedBrush.Body b = ResolvedBrush.of(ModularCodeChecks.fixture("wide-hake"), 120, 1).body;
        b.headFixed = fixed; b.angle = angle;
        FloatTiles t = new FloatTiles(900, 500, 0);
        HakeRibbon.stamp(t, null, PAPER, b, line(250, 40, 800, .5f), 120, new LineStamper.Meta(), .2, true);
        return rows(t, 400, .2f);
    }
    static void hakeWidth() {
        int follow = hakeRows(false, 70), fixed = hakeRows(true, 20);
        check(follow > 95 && follow < 115, "A following head lies across the stroke: its width, about 106 px at half press: " + follow);
        check(fixed > 28 && fixed < 50 && follow > 2 * fixed, "A head fixed at 20° sweeps only about 2W·sin 20° plus its thickness: " + fixed);
    }
    static void hakeTurns() {
        ResolvedBrush.Body b = ResolvedBrush.of(ModularCodeChecks.fixture("wide-hake"), 120, 1).body;
        b.headFixed = false;
        StrokePoints u = new StrokePoints(); long t = 0;
        for (int x = 100; x <= 400; x += 2) u.add(x, 150, .5f, t += 5, Float.NaN, Float.NaN);
        for (int i = 1; i <= 90; i++) { double a = -Math.PI / 2 + Math.PI * i / 90; u.add((float)(400 + 40 * Math.cos(a)), (float)(190 + 40 * Math.sin(a)), .5f, t += 5, Float.NaN, Float.NaN); }
        for (int x = 398; x >= 100; x -= 2) u.add(x, 230, .5f, t += 5, Float.NaN, Float.NaN);
        FloatTiles tiles = new FloatTiles(700, 400, 0);
        HakeRibbon.stamp(tiles, null, PAPER, b, u, 120, new LineStamper.Meta(), .2, true);
        int covered = 0, n = 0;
        for (int i = 0; i < u.count; i += 3, n++) if (tiles.get((int)u.x[i], (int)u.y[i]) > .3f) covered++;
        check(covered >= .95 * n, "The hake pivots round a tight U-turn without leaving its path bare: " + covered + " of " + n);
    }
    /**
     * A pen landing with a hook and a wobble, then on to the right: a following hake's start lies square across the stroke
     * (the owner, 2026-10-22: "struggles to find its direction and often leaves a mark that doesn't match at the beginning").
     * {x, y} at 480 Hz: 8 px down-left, a turn, then a slow wobbly 30 px and steady on.
     */
    static float[][] landing(float x0, float y0, float length) {
        java.util.List<float[]> o = new java.util.ArrayList<>();
        java.util.Random rnd = new java.util.Random(5);
        float x = x0, y = y0;
        for (int i = 0; i < 8; i++) o.add(new float[]{x -= .5f, y += .9f});
        for (int i = 0; i < 30; i++) o.add(new float[]{x += .9f, y + (float)((rnd.nextDouble() - .5) * 3)});
        for (float s = 0; s < length; s += 2.5f) o.add(new float[]{x + s, y});
        return o.toArray(new float[0][]);
    }
    /** How many px the mark's left edge wanders over the rows {@code y0..y1} of {@code tone} (cover above {@code over}): 0 for a square start. */
    static int startSpread(java.util.function.IntBinaryOperator tone, int w, int y0, int y1, int over) {
        int lo = Integer.MAX_VALUE, hi = -1;
        for (int y = y0; y <= y1; y++) for (int x = 0; x < w; x++) if (tone.applyAsInt(x, y) > over) { lo = Math.min(lo, x); hi = Math.max(hi, x); break; }
        return hi < 0 ? Integer.MAX_VALUE : hi - lo;
    }
    static void hakeStart() {
        ResolvedBrush.Body b = ResolvedBrush.of(ModularCodeChecks.fixture("wide-hake"), 120, 1).body;
        b.headFixed = false;
        float[][] path = landing(100, 160, 400);
        int half = (int)(BodyStamper.diameter(b, 120, .6, 0) / 2);
        for (boolean asItComes : new boolean[]{true, false}) {
            StrokePoints u = new StrokePoints(); LineStamper.Meta m = new LineStamper.Meta();
            FloatTiles tiles = new FloatTiles(700, 400, 0);
            int n = asItComes ? path.length : 20;   // laid as it comes, or lifted after 20 samples (before the direction settles)
            for (int i = 0; i < n; i++) {
                u.add(path[i][0], path[i][1], .6f, i * 2, Float.NaN, Float.NaN);
                if (asItComes && u.count >= 2) HakeRibbon.stamp(tiles, null, PAPER, b, u, 120, m, .2, false);
            }
            if (!asItComes) HakeRibbon.stamp(tiles, null, PAPER, b, u, 120, m, .2, true);
            float yc = path[path.length - 1][1];
            int spread = startSpread((x, y) -> tiles.get(x, y) > .2f ? 255 : 0, 700, (int)yc - half * 3 / 4, (int)yc + half * 3 / 4, 0);
            if (asItComes) check(spread <= 10, "A hake's start lies square across the stroke after a hooked, wobbly landing (left edge within 10 px over its width): " + spread);
            else check(spread != Integer.MAX_VALUE, "A hake lifted before its direction settled still lays its start");
        }
        // A Wet hake: its footprint laid once, final, waits for the direction too.
        ModularBrush wet = null;
        for (ToolPresets.Preset p : ToolPresets.withRetired()) if (p.name.equals("Gouache hake")) wet = p.brush().parts;
        ToneDocument d = InkWashLayers.blank(700, 400);
        DrawingStroke s = QuickStroke.start(d, wet, 60, 1, new WatercolorSession());
        long t = 0;
        for (float[] q : path) { s.sample(q[0], q[1], ToolSettings.pressure(.8f), 0, 0, t += 2); s.present(); }
        s.finish();
        float yc = path[path.length - 1][1];
        int spread = startSpread((x, y) -> 255 - d.compositeTone(x, y), 700, (int)yc - 18, (int)yc + 18, 128);
        check(spread <= 6, "A Wet hake's start lies square across the stroke after a hooked, wobbly landing: " + spread);
    }
    static FloatTiles blob(int w, int h) {
        FloatTiles t = new FloatTiles(w, h, 0);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) { double d = Math.hypot(x - w / 2.0, y - h / 2.0); if (d < 40) t.set(x, y, 1); else if ((x * 7 + y * 13) % 11 == 0 && x > 20 && y > 20 && x < w - 20 && y < h - 20) t.set(x, y, .5f); }
        return t;
    }
    static void settleIdentity() {
        FloatTiles src = blob(200, 200), dst = new FloatTiles(200, 200, 0);
        BodySettle.settle(src, dst, null, PAPER, new ResolvedBrush.Look(), new int[]{20, 20, 179, 179}, 60, null, 0, new StrokeMath.Scratch());
        for (int y = 0; y < 200; y++) for (int x = 0; x < 200; x++) check(dst.get(x, y) == src.get(x, y), "An empty look settles paint to itself");
    }
    static void bands() {
        FloatTiles src = blob(300, 700);
        ResolvedBrush.Look look = new ResolvedBrush.Look(); look.feather = .5; look.hollow = .5; look.bloom = .3; look.backrun = .3; look.gran = .2; look.impasto = .5;
        FloatTiles a = new FloatTiles(300, 700, 0), b = new FloatTiles(300, 700, 0), ra = new FloatTiles(300, 700, 0), rb = new FloatTiles(300, 700, 0);
        int[] rect = {30, 30, 269, 669};
        BodySettle.settle(src, a, ra, PAPER, look, rect, 300, null, 0, null, 64, new StrokeMath.Scratch());
        BodySettle.settle(src, b, rb, PAPER, look, rect, 300, null, 0, null, 10_000, new StrokeMath.Scratch());
        for (int y = 0; y < 700; y++) for (int x = 0; x < 300; x++) check(a.get(x, y) == b.get(x, y) && ra.get(x, y) == rb.get(x, y), "Settling in bands gives the one-piece result at " + x + "," + y);
        FloatTiles c = new FloatTiles(300, 700, 0), rc = new FloatTiles(300, 700, 0);
        Cores.shared = false;
        try { BodySettle.settle(src, c, rc, PAPER, look, rect, 300, null, 0, null, 64, new StrokeMath.Scratch()); } finally { Cores.shared = true; }
        for (int y = 0; y < 700; y++) for (int x = 0; x < 300; x++) check(a.get(x, y) == c.get(x, y) && ra.get(x, y) == rc.get(x, y), "and its bands shared out to other cores give what one core does at " + x + "," + y);
    }
    static void hollow() {
        FloatTiles src = new FloatTiles(200, 200, 0), dst = new FloatTiles(200, 200, 0);
        for (int y = 0; y < 200; y++) for (int x = 0; x < 200; x++) if (Math.hypot(x - 100, y - 100) < 40) src.set(x, y, 1);
        ResolvedBrush.Look look = new ResolvedBrush.Look(); look.hollow = 1;
        BodySettle.settle(src, dst, null, PAPER, look, new int[]{55, 55, 145, 145}, 60, null, 0, new StrokeMath.Scratch());
        check(dst.get(100, 100) < dst.get(137, 100), "A hollow edge pools pigment at the rim: " + dst.get(100, 100) + " < " + dst.get(137, 100));
    }
    static void impasto() {
        FloatTiles src = new FloatTiles(200, 200, 0), dst = new FloatTiles(200, 200, 0), relief = new FloatTiles(200, 200, 0);
        for (int y = 80; y < 120; y++) for (int x = 40; x < 160; x++) src.set(x, y, 1);
        ResolvedBrush.Look look = new ResolvedBrush.Look(); look.impasto = 1; look.cover = 1;
        BodySettle.settle(src, dst, relief, PAPER, look, new int[]{40, 80, 159, 119}, 60, null, 0, new StrokeMath.Scratch());
        check(relief.get(100, 80) > 0 && relief.get(100, 119) < 0, "Impasto is lit from the upper left: the top edge lighter, the bottom darker");
    }
    static void cover() {
        FloatTiles src = blob(200, 200), dst = new FloatTiles(200, 200, 0);
        ResolvedBrush.Look look = new ResolvedBrush.Look(); look.cover = 1; look.feather = 1; look.hollow = 1; look.bloom = 1; look.backrun = 1;
        BodySettle.settle(src, dst, null, PAPER, look, new int[]{20, 20, 179, 179}, 60, null, 0, new StrokeMath.Scratch());
        for (int y = 0; y < 200; y++) for (int x = 0; x < 200; x++) check(dst.get(x, y) == src.get(x, y), "Opaque paint keeps none of the wet effects");
    }
    static void blend() {
        ResolvedBrush r = ResolvedBrush.of(ModularCodeChecks.fixture("rake"), 16, 1);
        FloatTiles l = new FloatTiles(400, 300, 0), w = new FloatTiles(400, 300, 0), d = new FloatTiles(400, 300, 0), ls = new FloatTiles(400, 300, 0), bs = new FloatTiles(400, 300, 0);
        for (int y = 100; y < 200; y++) for (int x = 100; x < 300; x++) w.set(x, y, .8f);
        for (int y = 148; y < 152; y++) for (int x = 60; x < 340; x++) l.set(x, y, 1);
        double before = 0, after = 0;
        for (int y = 140; y < 160; y++) for (int x = 120; x < 280; x++) before += l.get(x, y);
        BodySettle.blend(l, w, d, ls, bs, null, PAPER, r, new int[]{60, 100, 339, 199}, new StrokeMath.Scratch());
        for (int y = 140; y < 160; y++) for (int x = 120; x < 280; x++) after += l.get(x, y);
        check(d.get(200, 150) > 0 && d.get(200, 120) > 0, "The blend settles the body into D");
        check(ls.get(200, 150) == 0 && bs.get(200, 150) == 0, "and clears what it saved");
        check(after != before, "A line crossing wet wash loosens: " + before + " → " + after);
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
