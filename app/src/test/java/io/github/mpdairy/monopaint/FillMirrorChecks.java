package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.Locale;

/**
 * Fill with mirrors ({@link FillTool.Areas}): with Copy Strokes a tap fills its area and each image's, each as a fill of
 * its own on the page as the last left it (byte for byte the fills tapped one after another without mirrors), one Undo
 * step, laid at lift; an area holding several images fills once (found once); an image on a line or off the page is
 * passed by; a tap on open paper still fills its images' areas. A kaleidoscope's six, and a ring round its centre found
 * once, timed. With Real Mirror the tap folds into the kept side and the walls are the page as shown: a bracket open
 * at the mirror holds the fill, kept to the kept side.
 */
public final class FillMirrorChecks {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    static final int W = FillChecks.W, H = FillChecks.H;
    /** One mirror down the page's middle. */
    static final Mirrors.Line MIDDLE = new Mirrors.Line(W / 2.0, H / 2.0, Math.PI / 2);

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        copies(); shared(); passedBy(); kaleidoscope(); real();
        String timed = timing();
        System.out.println("PASS: Fill with mirrors: Copy Strokes fills the tap's area and each image's, each a fill of its own as if tapped in turn, one Undo step, laid at lift; an area holding several images fills once; an image on a line or off the page is passed by; open paper at the tap still fills the images'; a kaleidoscope fills six; Real Mirror fills the kept side, a shape the mirror closes holding it; " + timed);
    }

    /** Ink {@code path} with the pen the Fill checks draw with, on the selected pair's ink layer. */
    static void ink(ToneDocument d, double[][] path) { BlendChecks.live(d, BlendChecks.brush(FillChecks.PEN), 7, 1, null, path); }
    static ToneDocument blank() { ToneDocument d = InkWashLayers.blank(W, H); InkWashLayers.select(d, 0); return d; }
    /** A page with a ring round each point. */
    static ToneDocument rings(double r, double[]... at) {
        ToneDocument d = blank();
        for (double[] p : at) ink(d, FillChecks.arc(p[0], p[1], r, 0, 2 * Math.PI + .05));
        return d;
    }
    /** An open line through {@code corners}, samples about 2 px apart. */
    static double[][] path(double[]... corners) {
        java.util.ArrayList<double[]> o = new java.util.ArrayList<>();
        for (int k = 1; k < corners.length; k++) {
            double[] a = corners[k - 1], b = corners[k];
            int n = (int)Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1]) / 2);
            for (int i = k == 1 ? 0 : 1; i <= n; i++) o.add(new double[]{a[0] + (b[0] - a[0]) * i / n, a[1] + (b[1] - a[1]) * i / n, .8, o.size() * 4});
        }
        return o.toArray(new double[0][]);
    }
    /** A fill as the drawing screen lays it ({@code InkWash.fill}): the tap's area found, laid, then lifted and finished; the areas found, in order. */
    static java.util.List<FillRegion.Room> fill(ToneDocument d, ModularBrush brush, int x, int y) {
        java.util.List<FillRegion.Room> rooms = new java.util.ArrayList<>();
        FillTool.Areas areas = new FillTool.Areas(d, x, y);
        FillRegion.Room first = areas.next();
        if (first == null) return rooms;
        DrawingStroke s = FillTool.lay(d, areas, first, r -> { rooms.add(r); return FillTool.start(d, brush, 30, .5, null, r); });
        s.present();
        if (s instanceof LiftingStroke && ((LiftingStroke) s).developsAfterLift()) {
            LiftingStroke l = (LiftingStroke) s;
            check(rooms.size() == 1, "only the tap's area is laid before lift");
            check(l.lift(), "it lifts");
            while (l.develop(1_000_000)) { }
        }
        s.finish();
        return rooms;
    }
    /** {@code rooms} filled one after another on {@code d}, no mirrors. */
    static void plain(ToneDocument d, ModularBrush brush, FillRegion.Room... rooms) {
        for (FillRegion.Room r : rooms) { ModularStroke s = FillTool.start(d, brush, 30, .5, null, r); s.present(); s.finish(); }
    }
    static int changedOutside(int[] before, int[] after, FillRegion.Room... rooms) {
        int n = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            if (after[y * W + x] == before[y * W + x]) continue;
            boolean in = false;
            for (FillRegion.Room r : rooms) in |= r.inside(x, y);
            if (!in) n++;
        }
        return n;
    }

    // ---- Copy Strokes ----

    static void copies() {
        for (ModularBrush brush : new ModularBrush[]{FillChecks.watercolour(), FillChecks.halftone()}) {
            ToneDocument d = rings(110, new double[]{250, 320}, new double[]{650, 320});
            ToneDocument same = rings(110, new double[]{250, 320}, new double[]{650, 320});
            d.setMirrors(Mirrors.NONE.add(MIDDLE));
            int wash = InkWashLayers.washOf(d, 0);
            int[] before = FillChecks.shown(d);
            java.util.List<FillRegion.Room> rooms = fill(d, brush, 230, 300);
            check(rooms.size() == 2 && rooms.get(1).inside(650, 320) && !rooms.get(1).inside(250, 320), "Copy Strokes: the tap's ring and its image's fill: " + rooms.size());
            int[] after = FillChecks.shown(d);
            check(after[320 * W + 250] < 255 && after[320 * W + 650] < 255, "both rings take the fill");
            check(changedOutside(before, after, rooms.get(0), rooms.get(1)) == 0, "nothing changes outside the two areas");
            // Each a fill of its own, as if tapped in turn without mirrors.
            plain(same, brush, rooms.get(0), rooms.get(1));
            check(ErasableChecks.same(ErasableChecks.layer(d, wash), ErasableChecks.layer(same, wash)), "the image's fill is the one tapped there, byte for byte");
            check(d.undo() && Arrays.equals(FillChecks.shown(d), before) && d.mirrors().copies(), "one Undo takes both fills back, the mirrors kept");
            check(d.redo() && Arrays.equals(FillChecks.shown(d), after), "Redo lays both again");
        }
    }

    /** A box across the mirror holds the tap and its image: one area, found once, filled once (as without mirrors). */
    static void shared() {
        ToneDocument d = blank(), same = blank();
        for (ToneDocument p : new ToneDocument[]{d, same}) ink(p, FillChecks.box(250, 180, 650, 460));
        d.setMirrors(Mirrors.NONE.add(MIDDLE));
        int wash = InkWashLayers.washOf(d, 0);
        java.util.List<FillRegion.Room> rooms = fill(d, FillChecks.watercolour(), 330, 300);
        check(rooms.size() == 1, "an area holding the tap and its image is found once: " + rooms.size());
        plain(same, FillChecks.watercolour(), FillTool.region(same, 330, 300));
        check(ErasableChecks.same(ErasableChecks.layer(d, wash), ErasableChecks.layer(same, wash)), "and filled once, as without mirrors");
    }

    /** An image on a line or off the page fills nothing; a tap on open paper fills it and its image's area. */
    static void passedBy() {
        // The image of the ring's middle lands on a line drawn down the other ring.
        ToneDocument d = rings(110, new double[]{250, 320}, new double[]{650, 320});
        ink(d, path(new double[]{650, 100}, new double[]{650, 560}));
        d.setMirrors(Mirrors.NONE.add(MIDDLE));
        check(FillChecks.lineAt(ErasableChecks.layer(d, FillTool.walls(d)), 649, 320), "the setup: a line at the image");
        FillTool.Areas areas = new FillTool.Areas(d, 250, 320);
        check(areas.next() != null && areas.next() == null, "an image on a line is passed by");
        // A mirror near the right edge: the image lands off the page.
        ToneDocument e = rings(110, new double[]{250, 320});
        e.setMirrors(Mirrors.NONE.add(new Mirrors.Line(800, 320, Math.PI / 2)));
        areas = new FillTool.Areas(e, 250, 320);
        check(areas.next() != null && areas.next() == null, "an image off the page is passed by");
        // Open paper at the tap: the image's ring still fills.
        ToneDocument f = rings(110, new double[]{650, 320});
        f.setMirrors(Mirrors.NONE.add(MIDDLE));
        java.util.List<FillRegion.Room> rooms = fill(f, FillChecks.watercolour(), 250, 320);
        // Open paper fills to the page's edges (2026-10-22), the image's ring too.
        check(rooms.size() == 2 && rooms.get(0).inside(250, 320) && !rooms.get(0).inside(650, 320) && rooms.get(1).inside(650, 320) && FillChecks.shown(f)[320 * W + 650] < 255,
                "a tap on open paper fills it and its image's area");
        ToneDocument g = blank();
        g.setMirrors(Mirrors.NONE.add(MIDDLE));
        java.util.List<FillRegion.Room> whole = fill(g, FillChecks.watercolour(), 250, 320);
        check(whole.size() == 1 && whole.get(0).inside(250, 320) && whole.get(0).inside(W - 250, 320), "and a blank page fills once, whole, its image inside it");
    }

    /** A kaleidoscope's six rings, one tap; a ring round its centre holds all six images: found once. */
    static void kaleidoscope() {
        Mirrors k = Mirrors.NONE.kaleidoscope(W, H);
        double[][] at = k.group().points(W / 2.0 + 190, H / 2.0);
        ToneDocument d = rings(45, at);
        d.setMirrors(k);
        int[] before = FillChecks.shown(d);
        java.util.List<FillRegion.Room> rooms = fill(d, FillChecks.halftone(), (int)at[0][0], (int)at[0][1]);
        check(rooms.size() == 6, "a kaleidoscope fills six rings: " + rooms.size());
        check(d.undo() && Arrays.equals(FillChecks.shown(d), before) && d.mirrors().copies(), "one Undo takes all six back, the mirrors kept");
        ToneDocument c = rings(200, new double[]{W / 2.0, H / 2.0});
        c.setMirrors(k);
        check(fill(c, FillChecks.halftone(), W / 2 + 60, H / 2 + 30).size() == 1, "a ring round the centre holds all six images: one area");
    }

    // ---- Real Mirror ----

    /**
     * One mirror down the middle, the left kept. A bracket on the left whose ends run under the mirror: on the page it is
     * open (no area), as shown it is shut by its reflection. A tap on either side fills inside it, on the left only.
     */
    static void real() {
        for (int tapX : new int[]{340, W - 340 + 10}) {
            ToneDocument d = blank();
            ink(d, path(new double[]{470, 200}, new double[]{300, 200}, new double[]{300, 440}, new double[]{470, 440}));
            FillRegion.Room openRoom = FillTool.region(d, 340, 320);
            check(openRoom != null && openRoom.x1 == W - 1, "the setup: the bracket is open on the page (its fill runs to the page's edge)");
            d.setMirrors(Mirrors.NONE.add(MIDDLE).styled(Mirrors.Style.REAL).keeping(100, 320));
            check(d.mirrors().reflects(), "the setup: Real Mirror reflects");
            int wash = InkWashLayers.washOf(d, 0);
            byte[][] washBefore = ErasableChecks.layer(d, wash);
            java.util.List<FillRegion.Room> rooms = fill(d, FillChecks.watercolour(), tapX, 320);
            check(rooms.size() == 1, "Real Mirror: one area, on the kept side (tap at " + tapX + ")");
            FillRegion.Room r = rooms.get(0);
            check(r.inside(340, 320) && r.inside(445, 320) && r.x1 <= W / 2 && !r.inside(250, 320), "inside the bracket, up to the mirror and not past it: " + r.x0 + ".." + r.x1);
            byte[][] washAfter = ErasableChecks.layer(d, wash);
            int right = 0;
            for (int y = 0; y < H; y++) for (int x = W / 2 + 1; x < W; x++) if (washAfter[0][y * W + x] != washBefore[0][y * W + x] || washAfter[1][y * W + x] != washBefore[1][y * W + x]) right++;
            check(right == 0, "nothing laid on the covered side: " + right);
        }
    }

    // ---- timing ----

    /** A kaleidoscope's six rings of radius 120 px on a Manta-sized page, found and laid at lift; budget about twice this Mac's. */
    static String timing() {
        int w = 1404, h = 1872;
        Mirrors k = Mirrors.NONE.kaleidoscope(w, h);
        double[][] at = k.group().points(w / 2.0 + 400, h / 2.0);
        ModularBrush brush = FillChecks.halftone();
        double med = 0, single = 0;
        for (int attempt = 0; attempt < 2; attempt++) {
            double[] ms = new double[7], one = new double[7];
            for (int run = -2; run < ms.length; run++) {
                ToneDocument d = InkWashLayers.blank(w, h);
                InkWashLayers.select(d, 0);
                for (double[] p : at) ink(d, FillChecks.arc(p[0], p[1], 120, 0, 2 * Math.PI + .05));
                long t0 = System.nanoTime();
                FillRegion.Room r = FillTool.region(d, (int)at[0][0], (int)at[0][1]);
                plain(d, brush, r);
                long t1 = System.nanoTime();
                d.undo();
                d.setMirrors(k);
                long t2 = System.nanoTime();
                int n = fill(d, brush, (int)at[0][0], (int)at[0][1]).size();
                long t3 = System.nanoTime();
                check(n == 6, "the timed kaleidoscope fills six: " + n);
                if (run >= 0) { one[run] = (t1 - t0) / 1e6; ms[run] = (t3 - t2) / 1e6; }
            }
            Arrays.sort(ms); Arrays.sort(one);
            med = ms[ms.length / 2]; single = one[one.length / 2];
            if (med <= 6.5 * single + 20) break;
        }
        String line = String.format(Locale.ROOT, "a kaleidoscope's six rings %.0f ms, one ring %.0f ms (desktop, the Manta about 20×)", med, single);
        Timing.limit(med <= 6.5 * single + 20, "six fills cost about six: " + line);
        return line;
    }

    private FillMirrorChecks() {}
}
