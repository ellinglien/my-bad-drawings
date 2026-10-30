package io.github.mpdairy.monopaint;

public final class LineStamperChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        width(); tapers(); tiltWidens(); tiltRange(); tiltShades(); sideTone(); tiltTurns(); pressureDarkens(); grid(); windows(); charcoal(); splatter(); split(); multi(); outline();
        System.out.println("PASS: the line is as wide as its size, tapers in, widens and turns with tilt when the hand says so (a wide Tilt range from the old Fude's fine point upright to nearly five times laid low), shades with its side laid low (Tilt Shading: as before through 35 degrees, at 70 wider, a little lighter at the same pressure, softer-edged and grainier, for a pencil and a charcoal stick as drawn; the Pencil, Charcoal and Pastels sticks have it), lightens with light pressure, lays no gaps at 360 px, stays inside its window; charcoal is deterministic and fuzzy; flicks splatter; a split nib parts, a 3-line rake lays three lines and an outline two");
    }
    static final StrokePaper PAPER = new StrokePaper(BrushTexture.PLAIN);
    /** A plain round pen: a fresh Fineliner-like brush, every rough dial and hand cell off. */
    static ResolvedBrush.Pen pen(double size) {
        ModularBrush b = new ModularBrush("p", 7, ModularStyle.named("Fineliner"));
        b.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.DRYING));
        return ResolvedBrush.of(b, size, 1).pen;
    }
    /** A horizontal line at y from x0 to x1, 2 px and 5 ms a sample, at pressure travel p, tilted (tx, ty). */
    static StrokePoints line(float y, float x0, float x1, float p, float tx, float ty) {
        StrokePoints pts = new StrokePoints();
        for (float x = x0; x <= x1; x += 2) pts.add(x, y, p, (long)((x - x0) * 2.5), tx, ty);
        return pts;
    }
    static FloatTiles stamp(ResolvedBrush.Pen b, StrokePoints pts, double size) {
        FloatTiles t = new FloatTiles(1400, 900, 0);
        LineStamper.stamp(t, PAPER, b, pts, size, true, 1, null, new LineStamper.Meta(), null);
        return t;
    }
    static int rows(FloatTiles t, int x, float min) { int n = 0; for (int y = 0; y < 900; y++) if (t.get(x, y) >= min) n++; return n; }
    static void width() {
        FloatTiles t = stamp(pen(16), line(100, 40, 360, .5f, Float.NaN, Float.NaN), 16);
        check(Math.abs(rows(t, 200, .5f) - 16) <= 1 && t.get(200, 100) == 1 && t.get(200, 120) == 0, "A 16 px line is 16 px wide: " + rows(t, 200, .5f));
    }
    static void tapers() {
        ResolvedBrush.Pen b = pen(16); b.taperIn = 1;
        FloatTiles t = stamp(b, line(100, 40, 360, .5f, Float.NaN, Float.NaN), 16);
        check(rows(t, 44, .5f) < rows(t, 300, .5f) - 6, "Taper in starts it narrow");
    }
    /** Tilt Shading (the owner, 2026-10-22: "when the pencil is tilted it shades like an angled pencil"). */
    static void tiltShades() {
        ResolvedBrush.Pen plain = pen(8), b = pen(8); b.side = 1;
        for (float deg : new float[]{0, 20, 35}) {
            FloatTiles a = stamp(plain, line(100, 40, 360, .6f, 0, deg), 8), s = stamp(b, line(100, 40, 360, .6f, 0, deg), 8);
            boolean same = true;
            for (int y = 80; y < 120; y++) for (int x = 30; x < 370; x++) if (a.get(x, y) != s.get(x, y)) same = false;
            check(same, "Tilt Shading: at " + deg + " degrees a line as without it");
        }
        FloatTiles up = stamp(b, line(200, 40, 360, .6f, 0, 0), 8), low = stamp(b, line(200, 40, 360, .6f, 0, 70), 8), mid = stamp(b, line(200, 40, 360, .6f, 0, 50), 8);
        float peak = 0; for (int y = 150; y < 250; y++) peak = Math.max(peak, low.get(200, y));
        int edge = 0; for (int y = 150; y < 250; y++) { float v = low.get(200, y); if (v > .05f && v < peak * .8f) edge++; }
        check(rows(low, 200, .05f) >= 5 * rows(up, 200, .05f) && rows(mid, 200, .05f) > rows(up, 200, .05f) + 8 && rows(mid, 200, .05f) < rows(low, 200, .05f),
                "Tilt Shading widens with the lean: " + rows(up, 200, .05f) + " upright, " + rows(mid, 200, .05f) + " at 50, " + rows(low, 200, .05f) + " at 70");
        check(peak < 1 && up.get(200, 200) == 1, "laid low it is lighter: " + peak);
        check(edge >= 6, "and its edge soft: " + edge + " rows between");
        // The Pencil presets (not Loose hatch, a hatch; Another pencil too since the owner retuned it, 2026-10-28) and the Charcoal and
        // Pastels sticks (not the stumps or crayons) have it, and nothing else does.
        java.util.List<String> not = java.util.Arrays.asList("Loose hatch", "Blend stump", "Stump", "Wax crayon", "Crayon scratch");
        for (ToolPresets.Preset p : ToolPresets.all()) {
            double side = p.brush().parts.part(BrushPart.HAND).get(PartDial.SIDE_SHADE, 0);
            boolean has = (p.style.name.equals("Pencil") || p.style.name.equals("Charcoal") || p.style.name.equals("Pastels")) && !not.contains(p.name);
            check(side == (has ? 1 : 0), p.name + ": Tilt Shading " + side);
        }
    }
    /**
     * Tilt Shading as drawn (the owner, 2026-10-23: "on an angled pencil the shade is usually a little lighter than a
     * straight mark... and it's a bit more diffused"): the 2B pencil and Soft charcoal presets on their own paper at the
     * same pressure, upright and at 70 degrees. Laid low the band's middle is lighter, but not washed out (55 to 80% of
     * the upright mark's darkness), and more of the band is its soft edge.
     */
    static void sideTone() {
        for (String name : new String[]{"2B pencil", "Soft charcoal"}) {
            ModularBrush b = null;
            for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(name)) b = p.brush().parts;
            double size = ModularSizes.size(b, InkWashSizes.Preset.MEDIUM);
            double[] up = band(b, size, 0), low = band(b, size, 70);
            double share = low[0] / up[0];
            check(share > .55 && share < .8, name + " laid low is lighter at the same pressure, not washed out: " + Math.round(100 * share) + "% of upright");
            check(low[1] > up[1], name + " laid low has the softer edge: " + Math.round(100 * low[1]) + "% of its band against " + Math.round(100 * up[1]) + "%");
        }
    }
    /** {@code b} drawn across a page at travel .6 leaning {@code deg}: its band's middle darkness (0 to 255) and the share of its rows between 10 and 90% of that. */
    static double[] band(ModularBrush b, double size, float deg) {
        int W = 300, H = 260;
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession session = new WatercolorSession();
        DrawingStroke s = QuickStroke.start(d, b.copy(), size, 1, session);
        for (int i = 0; i <= 120; i++) { double u = i / 120.0; s.sample((float)(20 + (W - 40) * u), H / 2f, ToolSettings.pressure(.6f), 0f, deg, 4L * i); s.present(); }
        s.finish();
        // Each row's darkness, the middle third of the band's length.
        double[] row = new double[H];
        for (int y = 0; y < H; y++) { double sum = 0; for (int x = W / 3; x < 2 * W / 3; x++) sum += 255 - d.compositeTone(x, y); row[y] = sum / (W / 3); }
        int top = -1, bottom = -1;
        for (int y = 0; y < H; y++) if (row[y] > 255 * .05) { if (top < 0) top = y; bottom = y; }
        int w = bottom - top + 1;
        double mid = 0; int n = 0;
        for (int y = top + w / 4; y <= bottom - w / 4; y++) { mid += row[y]; n++; }
        mid /= n;
        int edge = 0; for (int y = top; y <= bottom; y++) if (row[y] > .1 * mid && row[y] < .9 * mid) edge++;
        return new double[]{mid, (double)edge / w};
    }
    static void tiltWidens() {
        ResolvedBrush.Pen b = pen(16); b.tiltSize = 1;
        int upright = rows(stamp(b, line(100, 40, 360, .5f, 0, 0), 16), 200, .5f), leaning = rows(stamp(b, line(100, 40, 360, .5f, 40, 0), 16), 200, .5f);
        check(upright <= 17 && leaning >= 25, "Tilt → size widens a leaning line (about 1 + 1.2 × lean): " + upright + " → " + leaning);
        check(rows(stamp(pen(16), line(100, 40, 360, .5f, 40, 0), 16), 200, .5f) <= 17, "and only when the hand says so");
    }
    /** A wide Tilt range: the old Fude pen's width, a fine point at 30 degrees or steeper, the size at 45, 4.8 times at 60; the natural hold without a reading. */
    static void tiltRange() {
        ResolvedBrush.Pen b = pen(16); b.tiltSize = 1; b.tiltWide = true;
        int upright = rows(stamp(b, line(100, 40, 360, .5f, 20, 0), 16), 200, .5f), natural = rows(stamp(b, line(100, 40, 360, .5f, 45, 0), 16), 200, .5f);
        int low = rows(stamp(b, line(100, 40, 360, .5f, 60, 0), 16), 200, .5f), none = rows(stamp(b, line(100, 40, 360, .5f, Float.NaN, Float.NaN), 16), 200, .5f);
        check(upright <= 4 && natural >= 14 && natural <= 18 && low >= 70 && none == natural, "A wide range runs fine to broad: " + upright + ", " + natural + ", " + low + "; no reading " + none);
        for (double[] at : new double[][]{{0, .12}, {30, .12}, {45, 1}, {60, 4.8}, {80, 4.8}}) {
            double lean = BrushDirection.tiltLean(at[0], 0);
            check(Math.abs(b.tiltWidth(at[0], 0, lean) - at[1]) < 1e-9, "its width at " + at[0] + " degrees: " + b.tiltWidth(at[0], 0, lean));
        }
        b.tiltSize = .5; b.tiltAngle = 1;
        double lean60 = BrushDirection.tiltLean(60, 0);
        check(Math.abs(b.tiltWidth(60, 0, lean60) - 2.9) < 1e-9 && Math.abs(b.tiltFlat(60, 0, lean60) - .2) < 1e-9 && b.tiltFlat(45, 0, BrushDirection.tiltLean(45, 0)) == 1
                && b.tiltFlat(Double.NaN, 0, 0) == 1 && b.tiltWidth(Double.NaN, 0, 0) == 1, "as far as the cells go; the nib flattens past 45 degrees");
        b.tiltWide = false;
        for (double lean : new double[]{0, .3, .917, 1})
            check(b.tiltWidth(10, 0, lean) == 1 + 1.2 * b.tiltSize * lean && b.tiltFlat(10, 0, lean) == 1 - 0.45 * b.tiltAngle * lean, "A normal range is the prototype's, to the bit");
    }
    static void tiltTurns() {
        ModularBrush m = new ModularBrush("c", 7, ModularStyle.named("Marker"));
        m.set(BrushPart.TIP, new ModularBrush.Part(PartChoice.CHISEL)); m.part(BrushPart.TIP).set(PartDial.ASPECT, .3); m.part(BrushPart.TIP).set(PartDial.ANGLE, 20);
        ResolvedBrush.Pen b = ResolvedBrush.of(m, 16, 1).pen;
        int still = rows(stamp(b, line(100, 40, 360, .5f, 40, 0), 16), 200, .5f);
        b.tiltAngle = 1;
        int turned = rows(stamp(b, line(100, 40, 360, .5f, 40, 0), 16), 200, .5f);
        check(turned > still + 3, "Tilt → angle turns a chisel's broad side across a horizontal stroke: " + still + " → " + turned);
    }
    static void pressureDarkens() {
        ResolvedBrush.Pen b = pen(16); b.pDark = 1;
        FloatTiles light = stamp(b, line(100, 40, 360, .1f, Float.NaN, Float.NaN), 16);
        check(light.get(200, 100) <= .29f && light.get(200, 100) > .25f, "Pressure → dark: a light touch lays 1 − .8(1 − p): " + light.get(200, 100));
    }
    static void grid() {
        FloatTiles t = stamp(pen(360), line(450, 40, 1360, .5f, Float.NaN, Float.NaN), 360);
        for (int x = 200; x <= 1200; x++) check(t.get(x, 450) == 1, "A 360 px line's grid dabs leave no gap at x " + x);
    }
    static void windows() {
        FloatTiles t = new FloatTiles(1400, 900, 0);
        LineStamper.Window w = new LineStamper.Window(); w.dMin = 100; w.dMax = 200;
        LineStamper.stamp(t, PAPER, pen(8), line(100, 40, 360, .5f, Float.NaN, Float.NaN), 8, false, 1, null, new LineStamper.Meta(), w);
        check(t.get(100, 100) == 0 && t.get(190, 100) == 1 && t.get(300, 100) == 0, "A window lays only its own stretch");
    }
    static void charcoal() {
        ResolvedBrush.Pen b = ResolvedBrush.of(ModularCodeChecks.fixture("soft-coal"), 30, 1).pen;
        FloatTiles one = stamp(b, line(100, 40, 360, .6f, Float.NaN, Float.NaN), 30), two = stamp(b, line(100, 40, 360, .6f, Float.NaN, Float.NaN), 30);
        int partial = 0;
        for (int y = 60; y < 140; y++) for (int x = 100; x < 300; x++) { check(one.get(x, y) == two.get(x, y), "Charcoal is the same every time"); float c = one.get(x, y); if (c > .05f && c < .95f) partial++; }
        check(partial > 400, "and fuzzy at its edges: " + partial);
    }
    static void splatter() {
        ResolvedBrush.Pen b = pen(8); b.splatter = 1;
        StrokePoints fast = new StrokePoints();
        for (int i = 0; i <= 60; i++) fast.add(40 + 6 * i, 100, .5f, i, Float.NaN, Float.NaN);
        FloatTiles t = stamp(b, fast, 8);
        int outside = 0;
        for (int y = 0; y < 900; y++) for (int x = 0; x < 1400; x++) if (t.get(x, y) > 0 && (Math.abs(y - 100) > 8 || x > 410)) outside++;
        check(outside > 0, "Fast flicks throw drops off the line");
        b.splatter = 0;
        FloatTiles none = stamp(b, fast, 8); int outsideNone = 0;
        for (int y = 0; y < 900; y++) for (int x = 0; x < 1400; x++) if (none.get(x, y) > 0 && (Math.abs(y - 100) > 8 || x > 410)) outsideNone++;
        check(outsideNone == 0, "and none without splatter");
    }
    static ResolvedBrush placed(ResolvedBrush.Lines lines, int count) {
        ModularBrush m = ModularCodeChecks.fixture("brave-onion");
        ResolvedBrush r = ResolvedBrush.of(m, 16, 1);
        r.pen.lines = lines; r.pen.count = count; r.pen.chaos = 0; r.pen.offset = 0; r.pen.wobble = 0; r.pen.spread = .8; r.pen.breathe = .4; r.pen.dry = 0;
        return r;
    }
    static FloatTiles place(ResolvedBrush r) {
        FloatTiles t = new FloatTiles(1400, 900, 0);
        // A body 120 px wide, so a 3-line rake's lines (about 13 px each) have room to stand apart.
        Placement.stamp(t, null, null, PAPER, r.pen, r.placeBody, line(200, 40, 600, .9f, Float.NaN, Float.NaN), r.inkSize, 120, true, 1, null);
        return t;
    }
    static int runs(FloatTiles t, int x) { int n = 0; boolean in = false; for (int y = 0; y < 900; y++) { boolean on = t.get(x, y) > .5f; if (on && !in) n++; in = on; } return n; }
    static void split() {
        FloatTiles t = place(placed(ResolvedBrush.Lines.SPLIT, 3));   // the gap: |across| under .72·g − .14 of the half-width is bare
        check(t.get(300, 200) < .5f && runs(t, 300) == 2, "A pressed split nib parts into two rails: " + runs(t, 300));
    }
    static void multi() { check(runs(place(placed(ResolvedBrush.Lines.SPREAD, 3)), 300) == 3, "A 3-line rake lays three lines"); }
    static void outline() { check(runs(place(placed(ResolvedBrush.Lines.OUTLINE, 3)), 300) == 2, "An outline lays two"); }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
