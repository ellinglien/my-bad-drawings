package io.github.mpdairy.monopaint;

import java.util.Locale;

/**
 * A quick stroke's true stroke shown behind the pen while drawing (the audit's finding 17, {@link QuickStroke#trail}):
 * where its tones are final (Instant everywhere, Fast-wet outside its window; Drying's as laid, settled at lift) they
 * are written to the page behind the pen, and at lift only what is left. It must end byte for byte as without it
 * (every layer's tones, coverage, shares and print tones, and its Undo step), show the true stroke far behind the pen
 * before lift, keep the preview near the pen (a smudge with no paint of its own: the smudge itself, under the pen too),
 * and leave a stroke with the trail off as it was. {@code java TrailChecks v} prints numbers.
 */
public final class TrailChecks {
    static final int W = QuickStrokeChecks.W, H = QuickStrokeChecks.H;
    static boolean verbose;

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        verbose = args.length > 0;
        long composeMs = QuickStroke.composeMs;
        boolean trail = QuickStroke.trail;
        // Composing whenever the render has caught up, so it writes behind the pen many times a stroke.
        QuickStroke.composeMs = 0;
        try { same(); behind(); others(); } finally { QuickStroke.composeMs = composeMs; QuickStroke.trail = trail; }
        System.out.println("PASS: a quick stroke's true stroke is written behind the pen while drawing (Instant, Fast-wet outside its window) and ends byte for byte as without it, every layer's tones, coverage, shares and print tones and its one Undo step, for every style Instant, Fast-wet and Drying, washes, hakes, opaque paint, pens, sticks, a halftone, Erasable paint, a Slow ink wash and a halo's; far behind the pen the page shows the true stroke before lift, near it the preview; smudges trail too, byte for byte, those with no paint of their own showing the smudge under the pen; the flag off writes nothing behind the pen");
    }
    static void note(String s) { if (verbose) System.out.println(s); }

    /** Every layer's pixels, coverage, shares and print tones (null where a layer has none), its kind planes' after it (null where none). */
    static byte[][] all(ToneDocument d) {
        ToneDocument.Snapshot s = d.layerSnapshot();
        int each = 1 + ToneDocument.PLANES;
        byte[][] out = new byte[4 * each * s.layers.size()][];
        for (int i = 0; i < s.layers.size(); i++) for (int p = -1; p < ToneDocument.PLANES; p++) {
            ToneDocument.Layer l = p < 0 ? s.layers.get(i) : s.layers.get(i).plane(p);
            int at = 4 * (each * i + p + 1);
            if (l == null) continue;
            out[at] = l.tones.clone(); out[at + 1] = l.alpha.clone();
            out[at + 2] = l.share == null ? null : l.share.clone(); out[at + 3] = l.print == null ? null : l.print.clone();
        }
        return out;
    }
    static boolean same(byte[][] a, byte[][] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (!java.util.Arrays.equals(a[i], b[i])) return false;
        return true;
    }
    /** The quick stroke of {@code b} along the checks' S, the render given time to catch up between batches; with the trail or without. */
    static QuickStroke draw(ToneDocument d, ModularBrush b, double size, boolean trail, Runnable beforeLift) {
        QuickStroke.trail = trail;
        QuickStroke s = QuickStroke.quickly(d, b, size, 1, new WatercolorSession());
        QuickStrokeChecks.draw(s, QuickStrokeChecks.path(), false, () -> {
            for (int k = 0; k < 50 && s.rendered() < s.samples(); k++) {
                try { Thread.sleep(1); } catch (InterruptedException e) { throw new RuntimeException(e); }
            }
            try { Thread.sleep(1); } catch (InterruptedException e) { throw new RuntimeException(e); }
        });
        if (beforeLift != null) beforeLift.run();
        s.finish();
        return s;
    }

    /** 1. With the trail and without, the same samples end byte for byte alike, and one Undo takes either back whole. */
    static void same() {
        java.util.List<Object[]> cases = new java.util.ArrayList<>();
        for (String style : QuickStrokeChecks.styles()) for (PartChoice life : new PartChoice[]{PartChoice.NONE, PartChoice.FAST_WET})
            for (double size : new double[]{72, 200}) cases.add(new Object[]{style + " " + life, QuickStrokeChecks.brush(style, life), size});
        // Drying (speed plan F9): behind the pen as laid, its lift settling it over what was written.
        for (String style : QuickStrokeChecks.styles()) cases.add(new Object[]{style + " DRYING", QuickStrokeChecks.brush(style, PartChoice.DRYING), 120.0});
        for (Object[] p : new Object[][]{{"Slow ink wash", 72.0}, {"Slow ink wash", 200.0}, {"Mold ink", 120.0}}) cases.add(new Object[]{p[0], GravityChecks.preset((String)p[0]), p[1]});
        for (Object[] p : new Object[][]{{"Soft wash", 120.0}, {"Wet hake", 72.0}, {"Grey wash", 90.0}, {"Wide gouache", 72.0}, {"Steady line", 60.0}, {"Messy stick", 120.0}})
            cases.add(new Object[]{p[0], GravityChecks.preset((String)p[0]), p[1]});
        cases.add(new Object[]{"Halftone charcoal", ModularCode.decode("nimble-okra.M51i5eei05u00006yat_30vox00we01_0_1tc_1jz_8jnip00vbew00000000nz0.2lchwi"), 120.0});
        cases.add(new Object[]{"Halftone ink", ModularCode.decode("plain-ink.M00i000000000at_000zz00000000000000001_0_000_1jz_0000000000000000000nz0.1y9"), 60.0});
        int trailed = 0;
        for (Object[] c : cases) {
            ModularBrush b = (ModularBrush)c[1];
            double size = (Double)c[2];
            ToneDocument off = page(), on = page();
            draw(off, b, size, false, null);
            QuickStroke s = draw(on, b, size, true, null);
            if (s.trailTiles > 0) trailed++;
            note(String.format(Locale.ROOT, "%-24s %4.0f px: %d tiles written behind the pen in %d writes", c[0], size, s.trailTiles, s.trailWrites));
            check(same(all(off), all(on)), c[0] + " at " + size + " px with the trail ends byte for byte as without it");
            byte[][] drawn = all(on);
            check(on.undo() && off.undo() && same(all(on), all(off)) && ModularStrokeChecks.same(ModularStrokeChecks.layers(on), ModularStrokeChecks.layers(page()))
                    && !on.canUndo() && on.redo() && same(all(on), drawn), c[0] + ": one Undo takes it back as without it, Redo lays it again");
        }
        check(trailed >= cases.size() * 3 / 4, "most of them wrote behind the pen while it was down: " + trailed + " of " + cases.size());
    }
    /** A blank page with one pair, on a shaded paper's white: the checks' page. */
    static ToneDocument page() { return InkWashLayers.blank(W, H); }

    /**
     * 2. Before lift: far behind the pen (the S's first third) the page shows the true stroke, what it ends with; near
     * the pen (the S's last stretch) the preview, not yet the true stroke.
     */
    static void behind() {
        for (Object[] p : new Object[][]{{"Soft wash", 120.0}, {"Wet hake", 72.0}}) {
            ModularBrush b = GravityChecks.preset((String)p[0]);
            double size = (Double)p[1];
            ToneDocument d = page();
            int layer = ModularSizes.inks(b) ? InkWashLayers.INK : InkWashLayers.WASH;
            byte[][] before = new byte[1][];
            // The layer's look, its kind planes too.
            draw(d, b, size, true, () -> before[0] = d.layerSnapshot().layers.get(layer).flattened().tones.clone());
            byte[] was = before[0], now = d.layerSnapshot().layers.get(layer).flattened().tones;
            float[][] path = QuickStrokeChecks.path();
            int x0 = (int)path[0][0] - 80, xFar = (int)path[path.length / 3][0], xNear = (int)path[path.length - 30][0];
            long far = 0, farSame = 0, near = 0, nearSame = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int i = y * W + x;
                if (now[i] == (byte)255 && was[i] == (byte)255) continue;
                if (x >= x0 && x < xFar) { far++; if (now[i] == was[i]) farSame++; }
                else if (x >= xNear) { near++; if (now[i] == was[i]) nearSame++; }
            }
            note(String.format(Locale.ROOT, "%s %.0f px before lift: far behind the pen %d of %d pixels as they end, near it %d of %d", p[0], size, farSame, far, nearSame, near));
            check(far > 1000 && farSame == far, p[0] + ": far behind the pen the page shows the true stroke before lift: " + farSame + " of " + far);
            // A wash's flat preview is its final tone at most pixels (speed plan F5): near the pen not yet all of them.
            check(near > 1000 && nearSame < near, p[0] + ": near the pen, the preview: " + nearSame + " of " + near + " as they end");
        }
    }

    /**
     * 3. A smudge trails too (the owner, 2026-10-23: smudges "take a second to show the effect"): over marks it ends byte
     * for byte as without the trail; one with no paint of its own shows the true smudge under the pen before lift, where
     * without the trail it showed its preview's dither (but Clear Water's, which runs out again at lift); with paint (Oil
     * paint), behind the pen. A stroke with the trail
     * off writes nothing behind the pen.
     */
    static void others() {
        Object[][] smudges = {{"Blend stump", 60.0, true}, {"Stump", 60.0, true}, {"Dirty brush", 120.0, true},
                // Clear Water: what it lifted runs out at lift, so the page near the pen changes again then.
                {"Water brush", 120.0, false}, {"Damp tissue", 120.0, false}, {"Wash comb", 200.0, true}, {"Oil paint", 120.0, false}};
        for (Object[] c : smudges) {
            ModularBrush b = GravityChecks.preset((String)c[0]);
            double size = (Double)c[1];
            ToneDocument off = marked(), on = marked();
            byte[][] found = all(on), before[] = new byte[1][][];
            draw(off, b, size, false, null);
            QuickStroke s = draw(on, b, size, true, () -> before[0] = all(on));
            check(same(all(off), all(on)), c[0] + " at " + size + " px with the trail ends byte for byte as without it");
            check(s.trailTiles > 0, c[0] + ": written behind the pen while it was down");
            if (!(Boolean)c[2]) continue;
            // Near the pen (the S's last stretch), before lift: the smudge as it ends, not a preview.
            int layer = ModularSizes.inks(b) ? InkWashLayers.INK : InkWashLayers.WASH;
            int base = 4 * (1 + ToneDocument.PLANES) * layer;
            byte[] was = found[base], then = before[0][base], now = all(on)[base];
            float[][] path = QuickStrokeChecks.path();
            int xNear = (int)path[path.length - 60][0];
            long moved = 0, shown = 0;
            for (int y = 0; y < H; y++) for (int x = xNear; x < W; x++) {
                int i = y * W + x;
                if (now[i] == was[i]) continue;
                moved++;
                if (then[i] == now[i]) shown++;
            }
            note(String.format(Locale.ROOT, "%s %.0f px: near the pen before lift %d of %d smudged pixels as they end; %d tiles written in %d writes", c[0], size, shown, moved, s.trailTiles, s.trailWrites));
            check(moved > 200 && shown * 2 > moved, c[0] + ": near the pen before lift the page shows the smudge: " + shown + " of " + moved);
        }
        ToneDocument off = page(), on = page();
        draw(off, GravityChecks.preset("Soft wash"), 120, false, null);
        QuickStroke s = draw(on, GravityChecks.preset("Soft wash"), 120, false, null);
        check(s.trailTiles == 0 && same(all(off), all(on)), "trail off: nothing written behind the pen, the page as without it");
    }
    /** The checks' page with marks on both layers of its pair, for a smudge to move: ink lines, wash bands. */
    static ToneDocument marked() {
        ToneDocument d = page();
        int[] row = new int[W];
        for (int layer : new int[]{InkWashLayers.INK, InkWashLayers.WASH}) {
            d.selectLayer(layer);
            d.begin(1);
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) row[x] = layer == InkWashLayers.INK ? (Math.floorMod(x + y / 3, 24) < 3 ? 40 : -1) : (x / 60 % 3 == 1 ? 150 : -1);
                d.setTones(0, y, W, 1, row);
            }
            d.finish();
        }
        return d;
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
