package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Fill's region ({@link FillRegion}), the web prototype's cases (docs/archive/brush-lab/prototypes/mischief-lab-2.NOTES.md,
 * "Fourth round") at page scale: lines drawn as its pen draws them (an antialiased round every half pixel, .95 dark,
 * radius .9 + 1.5 × pressure prototype px), every size × {@link FillRegion#SCALE}. Leaves of two arcs meeting at
 * points of 10° to 30°, meeting exactly or crossing past, straight or wobbling, fill to the tip with nothing dry and
 * nothing outside; closed V shapes fill to their points; a ring with a 4 or 5 px gap holds, an open 5 px gap leaks; two
 * arcs 1 to 5 px apart hold; a box's lid 2 to 4 px short holds; no paper rim where the fill meets a line; light marks
 * are no walls; a tap on open paper finds nothing; and the time to find a region on a page the Manta's size. Any
 * argument prints each case.
 */
public final class FillRegionChecks {
    static final double S = FillRegion.SCALE;
    static boolean verbose;
    static void note(String s) { if (verbose) System.out.println(s); }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) {
        verbose = args.length > 0;
        if (args.length > 0 && args[0].equals("time")) { verbose = true; timing(); System.out.println(timed); return; }
        leaves(); vees(); rings(); arcs(); lids(); rims(); soft(); open(); blank(); timing();
        System.out.printf(Locale.ROOT, "PASS: Fill's region at page scale (%.3f px a prototype px): 162 leaves of 10–30° points (meeting or crossing past, pen .3/.7/1, straight or wobbling ±1/±2 px) fill to the tip, every pocket filled, nothing outside; closed Vs of 10–60° fill to their points; a ring with a 4 or 5 px gap and open gaps of 3–5 px holds, an open 6 px gap leaks; two arcs 1–5 px apart and a lid 2–4 px short hold, 6 leak; no paper beside the fill at a ring, box, triangle or wobbly loop at pen .05/.4/1; light marks hold nothing; open paper finds nothing; Fill's page with no line pixel (blank, odd sizes, light marks only) is taken whole at once, as the search finds it; %s%n", S, timed);
    }

    // ---- the prototype's pen, at page scale ----

    /** A page of ink darkness, 0–255. */
    static final class Page implements FillRegion.Ink {
        final int w, h;
        final int[] dark;
        Page(int w, int h) { this.w = w; this.h = h; dark = new int[w * h]; }
        @Override public boolean darkness(int x, int y, int bw, int bh, int[] out) {
            // As ToneDocument.layerDarkness: a row at a time, 0 off the page.
            boolean any = false;
            int x0 = Math.max(0, x), x1 = Math.min(w, x + bw);
            for (int yy = 0; yy < bh; yy++) {
                int py = y + yy, o = yy * bw;
                if (py < 0 || py >= h || x1 <= x0) { Arrays.fill(out, o, o + bw, 0); continue; }
                if (x0 > x) Arrays.fill(out, o, o + x0 - x, 0);
                if (x1 < x + bw) Arrays.fill(out, o + x1 - x, o + bw, 0);
                System.arraycopy(dark, py * w + x0, out, o + x0 - x, x1 - x0);
                if (!any) for (int i = py * w + x0, e = py * w + x1; i < e; i++) if (dark[i] != 0) { any = true; break; }
            }
            return any;
        }
        /** An antialiased round dab of radius {@code r} (page px), darkening to {@code d} (0–1). */
        void dab(double x, double y, double r, double d) {
            double R = r + 1;
            int x0 = Math.max(0, (int)Math.floor(x - R)), x1 = Math.min(w - 1, (int)Math.ceil(x + R)), y0 = Math.max(0, (int)Math.floor(y - R)), y1 = Math.min(h - 1, (int)Math.ceil(y + R));
            for (int py = y0; py <= y1; py++) for (int px = x0; px <= x1; px++) {
                double c = Math.max(0, Math.min(1, r + .5 - Math.hypot(px + .5 - x, py + .5 - y))) * d;
                int v = (int)Math.round(255 * c), i = py * w + px;
                if (v > dark[i]) dark[i] = v;
            }
        }
        /** A line through {@code pts} (page px), a dab every half pixel, the pen's radius at pressure {@code p}. */
        void line(List<double[]> pts, double p) { line(pts, p, .95); }
        void line(List<double[]> pts, double p, double d) {
            double r = (.9 + 1.5 * p) * S;
            for (int k = 1; k < pts.size(); k++) {
                double[] a = pts.get(k - 1), b = pts.get(k);
                int n = Math.max(1, (int)Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1]) / .5));
                for (int i = k == 1 ? 0 : 1; i <= n; i++) dab(a[0] + (b[0] - a[0]) * i / n, a[1] + (b[1] - a[1]) * i / n, r, d);
            }
        }
        boolean line(int x, int y) { return dark[y * w + x] > FillRegion.LINE; }
    }
    /** Prototype px to page px. */
    static double s(double v) { return v * S; }
    /** An arc of a circle (page px), from angle a0 to a1, points about a pixel apart. */
    static List<double[]> arc(double cx, double cy, double r, double a0, double a1) {
        int n = Math.max(12, (int)Math.ceil(Math.abs(a1 - a0) * r));
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i <= n; i++) { double a = a0 + (a1 - a0) * i / n; pts.add(new double[]{cx + r * Math.cos(a), cy + r * Math.sin(a)}); }
        return pts;
    }
    static List<double[]> poly(double[]... corners) {
        List<double[]> pts = new ArrayList<>();
        for (int k = 1; k < corners.length; k++) {
            double[] a = corners[k - 1], b = corners[k];
            int n = Math.max(1, (int)Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1])));
            for (int i = k > 1 ? 1 : 0; i <= n; i++) pts.add(new double[]{a[0] + (b[0] - a[0]) * i / n, a[1] + (b[1] - a[1]) * i / n});
        }
        return pts;
    }
    /** {@code pts} moved across their way by up to {@code amp} page px, a hand's wobble (two waves, seeded). */
    static List<double[]> wobble(List<double[]> pts, double amp, int seed) {
        if (amp == 0) return pts;
        List<double[]> out = new ArrayList<>();
        double s = 0, f1 = 2 * Math.PI / s(37 + seed % 7), f2 = 2 * Math.PI / s(13 + seed % 5), ph = seed * 1.7;
        for (int k = 0; k < pts.size(); k++) {
            double[] a = pts.get(Math.max(0, k - 1)), b = pts.get(Math.min(pts.size() - 1, k + 1)), p = pts.get(k);
            double dx = b[0] - a[0], dy = b[1] - a[1], len = Math.hypot(dx, dy);
            if (k > 0) s += Math.hypot(p[0] - pts.get(k - 1)[0], p[1] - pts.get(k - 1)[1]);
            double o = amp * (.7 * Math.sin(s * f1 + ph) + .3 * Math.sin(s * f2 + 2 * ph));
            out.add(new double[]{p[0] - dy / len * o, p[1] + dx / len * o});
        }
        return out;
    }
    /** The region round {@code x, y}, the same found with the arrays kept from the last find as with fresh ones. */
    static FillRegion.Room find(Page page, double x, double y) {
        FillRegion.Room room = FillRegion.find(page.w, page.h, page, (int)x, (int)y), fresh = FillRegion.fresh(page.w, page.h, page, (int)x, (int)y);
        check(same(room, fresh), "a region is the same found after another as found afresh");
        return room;
    }
    static boolean same(FillRegion.Room a, FillRegion.Room b) {
        if (a == null || b == null) return a == b;
        return a.x0 == b.x0 && a.y0 == b.y0 && a.x1 == b.x1 && a.y1 == b.y1 && a.area == b.area && Arrays.equals(a.at, b.at);
    }

    // ---- 1. leaves ----

    /** A leaf of two arcs meeting at points of {@code deg}, {@code past} prototype px past them, pen {@code p}, wobbling {@code amp} (seeded by them), on an 800 × 300 page, centred. */
    static Page leafPage(double deg, double past, double p, double amp) {
        Page page = new Page(800, 300);
        double L = s(360), a = Math.toRadians(deg / 2), R = L / 2 / Math.sin(a), cx = 400, cy = 150, rc = R * Math.cos(a), ext = s(past) / R;
        int seed = (int)(deg * 7 + past * 3 + p * 10 + amp * 13);
        page.line(wobble(arc(cx, cy + rc, R, -Math.PI / 2 - a - ext, -Math.PI / 2 + a + ext), s(amp), seed), p);
        page.line(wobble(arc(cx, cy - rc, R, Math.PI / 2 - a - ext, Math.PI / 2 + a + ext), s(amp), seed + 3), p);
        return page;
    }
    /** That leaf's dry pixels, and pixels filled outside it. */
    static int[] leaf(double deg, double past, double p, double amp) {
        Page page = leafPage(deg, past, p, amp);
        double cx = 400, cy = 150, L = s(360), a = Math.toRadians(deg / 2), R = L / 2 / Math.sin(a), rc = R * Math.cos(a);
        FillRegion.Room room = find(page, cx, cy);
        check(room != null, "a leaf of " + deg + "° holds");
        // The leaf: between the two arcs' middles (paper shut in past its points, between the lines' ends, is no part of it).
        return dryAndOutside(page, room, (int)cx, (int)cy, (x, y) -> Math.hypot(x + .5 - cx, y + .5 - (cy + rc)) <= R && Math.hypot(x + .5 - cx, y + .5 - (cy - rc)) <= R);
    }
    /**
     * The paper left dry and the paper outside filled. Dry: a pixel no line pixel, where {@code shape} holds, showing as
     * paper ({@link #PAPERISH}), in the region's own cell (the paper four ways from the tap, across no line pixel) or
     * in a small cell shut in by the lines within {@link FillRegion#ANCHOR} line pixels of it (a pocket). Outside: a
     * filled pixel, no line pixel, in paper reached from the page's edge across no line pixel.
     */
    static int[] dryAndOutside(Page page, FillRegion.Room room, int tapX, int tapY, java.util.function.BiPredicate<Integer, Integer> shape) {
        int w = page.w, h = page.h, n = w * h;
        // Each cell of paper (four ways across no line pixel), numbered; 1 is the page's edge's.
        int[] cell = new int[n], q = new int[n];
        int cells = 0;
        for (int pass = 0; pass < 2; pass++) for (int s0 = 0; s0 < n; s0++) {
            int sx = s0 % w, sy = s0 / w;
            boolean edge = sx == 0 || sy == 0 || sx == w - 1 || sy == h - 1;
            if (cell[s0] != 0 || page.line(sx, sy) || (pass == 0) != edge) continue;
            int id = pass == 0 ? 1 : ++cells + 1, head = 0, tail = 0;
            cell[s0] = id; q[tail++] = s0;
            while (head < tail) {
                int i = q[head++], x = i % w;
                int[] nb = {x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1, i >= w ? i - w : -1, i < n - w ? i + w : -1};
                for (int j : nb) if (j >= 0 && cell[j] == 0 && !page.line(j % w, j / w)) { cell[j] = id; q[tail++] = j; }
            }
        }
        int main = cell[tapY * w + tapX];
        int[] size = new int[cells + 2];
        for (int i = 0; i < n; i++) size[cell[i]]++;
        // The small cells within ANCHOR line pixels of the region's cell, four ways through line pixels only.
        boolean[] near = new boolean[cells + 2];
        near[main] = true;
        int[] step = new int[n];
        java.util.Arrays.fill(step, -1);
        int head = 0, tail = 0;
        for (int i = 0; i < n; i++) if (cell[i] == main) { step[i] = 0; q[tail++] = i; }
        while (head < tail) {
            int i = q[head++], x = i % w;
            if (step[i] >= FillRegion.ANCHOR) continue;
            int[] nb = {x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1, i >= w ? i - w : -1, i < n - w ? i + w : -1};
            for (int j : nb) {
                if (j < 0 || step[j] >= 0) continue;
                if (cell[j] != 0) { if (cell[j] != 1 && size[cell[j]] <= FillRegion.POCKET_FEW) near[cell[j]] = true; continue; }
                step[j] = step[i] + 1; q[tail++] = j;
            }
        }
        int dry = 0, outside = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x, c = cell[i];
            if (c == 0) continue;
            boolean wet = room.inside(x, y);
            // Paper (or nearly): a light pixel shut deep in two merged lines doesn't show as paper.
            if (c != 1 && near[c] && !wet && shape.test(x, y) && page.dark[i] <= PAPERISH) dry++;
            if (c == 1 && wet) outside++;
        }
        return new int[]{dry, outside};
    }
    /** The darkest a pixel left dry still shows as paper: a fifth. */
    static final int PAPERISH = 51;
    static int[] dryAndOutside(Page page, FillRegion.Room room, int tapX, int tapY) { return dryAndOutside(page, room, tapX, tapY, (x, y) -> true); }

    static void leaves() {
        int n = 0, worst = 0;
        List<String> failed = new ArrayList<>();
        for (double deg : new double[]{10, 12, 15, 20, 25, 30})
            for (double past : new double[]{0, 10, 15})
                for (double p : new double[]{.3, .7, 1})
                    for (double amp : new double[]{0, 1, 2}) {
                        int[] r = leaf(deg, past, p, amp);
                        note(String.format(Locale.ROOT, "leaf %2.0f° past %2.0f pen %.1f wobble %.0f: %d dry, %d outside", deg, past, p, amp, r[0], r[1]));
                        // The prototype's one exception: a 10° leaf at a light pen wobbling ±2 px, a light pixel shut deep in the merged lines.
                        int allowed = 0;
                        if (r[0] > allowed || r[1] != 0) failed.add(String.format(Locale.ROOT, "%.0f° past %.0f pen %.1f wobble %.0f: %d dry, %d outside", deg, past, p, amp, r[0], r[1]));
                        worst = Math.max(worst, r[0]);
                        n++;
                    }
        check(n == 162, "162 leaves: " + n);
        check(failed.isEmpty(), "every leaf fills to its tip and nothing outside it: " + failed);
    }

    // ---- 2. closed Vs ----

    static void vees() {
        for (double deg : new double[]{10, 20, 30, 60}) for (double past : new double[]{0, 10}) {
            Page page = new Page(800, 700);
            double a = Math.toRadians(deg / 2), L = s(300), ax = 100, ay = 350, e = s(past);
            double[] up = {ax + L * Math.cos(a), ay - L * Math.sin(a)}, down = {ax + L * Math.cos(a), ay + L * Math.sin(a)};
            double[] from1 = {ax - e * Math.cos(a), ay + e * Math.sin(a)}, from2 = {ax - e * Math.cos(a), ay - e * Math.sin(a)};
            page.line(poly(from1, up), .7); page.line(poly(from2, down), .7);
            page.line(poly(new double[]{up[0] - s(10), up[1] - s(10)}, new double[]{down[0] - s(10), down[1] + s(10)}), .7);
            FillRegion.Room room = find(page, ax + L * .6, ay);
            check(room != null, "a closed V of " + deg + "° holds");
            int[] r = dryAndOutside(page, room, (int)(ax + L * .6), (int)ay);
            int dry = r[0];
            check(r[1] == 0, "a closed V of " + deg + "° fills nothing outside it");
            note(String.format(Locale.ROOT, "V %2.0f° past %2.0f: %d dry, region %d px", deg, past, dry, room.area));
            check(dry == 0, "a closed V of " + deg + "° fills to its point: " + dry + " dry");
        }
    }

    // ---- 3. rings ----

    /** A ring of radius {@code r} (prototype px) with a gap of {@code gap} (centre to centre, prototype px) at pen {@code p}; the region inside, or null. */
    static FillRegion.Room ring(double r, double gap, double p) {
        Page page = new Page(700, 700);
        double R = s(r);
        page.line(arc(350, 350, R, -Math.PI / 2 + s(gap) / R, 1.5 * Math.PI), p);
        return find(page, 350, 350);
    }
    static double penR(double p) { return .9 + 1.5 * p; }
    static void rings() {
        for (double gap : new double[]{4, 5}) for (double p : new double[]{.05, .7, 1}) {
            FillRegion.Room room = ring(120, gap, p);
            note("ring, gap " + gap + " px at pen " + p + ": " + (room == null ? "leaks" : "holds, " + room.area + " px"));
            check(room != null, "a ring with a " + gap + " px gap at pen " + p + " holds");
        }
        for (double open : new double[]{3, 4, 5}) {
            double p = .4, gap = open + 2 * penR(p);
            check(ring(120, gap, p) != null, "a ring with an open gap of " + open + " px holds");
        }
        double p = .4;
        check(ring(120, 6 + 2 * penR(p), p) == null, "a ring with an open gap of 6 px leaks");
        // A ring big enough holds a 4 px gap; a small loop with it leaks (its ends meet near by along the ink).
        check(ring(20, 4, .4) != null, "a ring of radius 20 px with a 4 px gap holds");
    }

    // ---- 4. two arcs, a lid ----

    static void arcs() {
        for (double open = 1; open <= 6; open++) {
            Page page = new Page(700, 700);
            double R = s(120), p = .4, g = s(open + 2 * penR(p)) / R;
            page.line(arc(350, 350, R, g / 2, Math.PI - g / 2), p);
            page.line(arc(350, 350, R, Math.PI + g / 2, 2 * Math.PI - g / 2), p);
            FillRegion.Room room = find(page, 350, 350);
            note("two arcs " + open + " px apart: " + (room == null ? "leak" : "hold"));
            check((room != null) == (open <= 5), "two arcs " + open + " px apart " + (open <= 5 ? "hold" : "leak"));
        }
    }
    static void lids() {
        for (double short_ = 2; short_ <= 6; short_++) {
            Page page = new Page(700, 700);
            double p = .4, rp = penR(p), x0 = 150, y0 = 150, x1 = 550, y1 = 550;
            page.line(poly(new double[]{x0, y0}, new double[]{x0, y1}, new double[]{x1, y1}, new double[]{x1, y0}), p);
            double gap = s(short_ + 2 * rp);
            page.line(poly(new double[]{x0 + gap, y0}, new double[]{x1 - gap, y0}), p);
            FillRegion.Room room = find(page, 350, 350);
            note("a lid " + short_ + " px short: " + (room == null ? "leaks" : "holds"));
            if (short_ <= 4 || short_ == 6) check((room != null) == (short_ <= 4), "a lid " + short_ + " px short of its sides " + (short_ <= 4 ? "holds" : "leaks"));
        }
    }

    // ---- 5. no rim ----

    /** No paper beside the fill: every pixel next to a filled one is filled or a line pixel; every pixel inside but the lines filled. */
    static void rims() {
        for (double p : new double[]{.05, .4, 1}) {
            List<List<double[]>> shapes = new ArrayList<>();
            shapes.add(arc(350, 350, s(120), 0, 2 * Math.PI));
            shapes.add(poly(new double[]{150, 150}, new double[]{550, 160}, new double[]{540, 560}, new double[]{160, 550}, new double[]{150, 150}));
            shapes.add(poly(new double[]{350, 120}, new double[]{600, 560}, new double[]{100, 560}, new double[]{350, 120}));
            // A wobbly loop that closes: its wobble whole waves round it.
            List<double[]> loop = new ArrayList<>();
            for (int i = 0; i <= 1400; i++) {
                double t = 2 * Math.PI * i / 1400, r = s(110) + s(3) * (.7 * Math.sin(7 * t) + .3 * Math.sin(17 * t + 1));
                loop.add(new double[]{350 + r * Math.cos(t), 350 + r * Math.sin(t)});
            }
            shapes.add(loop);
            String[] names = {"ring", "box", "triangle", "wobbly loop"};
            for (int k = 0; k < shapes.size(); k++) {
                Page page = new Page(700, 700);
                page.line(shapes.get(k), p);
                FillRegion.Room room = find(page, 350, 380);
                check(room != null, "a " + names[k] + " at pen " + p + " holds");
                int rim = 0;
                for (int y = room.y0; y <= room.y1; y++) for (int x = room.x0; x <= room.x1; x++) {
                    if (!room.inside(x, y)) continue;
                    int[][] nb = {{x - 1, y}, {x + 1, y}, {x, y - 1}, {x, y + 1}};
                    for (int[] c : nb) if (!room.inside(c[0], c[1]) && !page.line(c[0], c[1])) rim++;
                }
                note(names[k] + " pen " + p + ": " + rim + " dry beside the fill, " + room.area + " px");
                check(rim == 0, "no paper beside the fill in a " + names[k] + " at pen " + p + ": " + rim);
            }
        }
    }

    // ---- 6. light marks, open paper ----

    static void soft() {
        // A box drawn light (ink .35) holds nothing: the tap's region reaches the page's edge.
        Page page = new Page(600, 600);
        page.line(poly(new double[]{100, 100}, new double[]{500, 100}, new double[]{500, 500}, new double[]{100, 500}, new double[]{100, 100}), 1, .35);
        check(find(page, 300, 300) == null, "a light box holds nothing");
        // A crisp box with a light line across: both halves fill as one.
        Page two = new Page(600, 600);
        two.line(poly(new double[]{100, 100}, new double[]{500, 100}, new double[]{500, 500}, new double[]{100, 500}, new double[]{100, 100}), .7);
        two.line(poly(new double[]{100, 300}, new double[]{500, 300}), 1, .35);
        FillRegion.Room room = find(two, 300, 200);
        check(room != null && room.inside(300, 400) && room.inside(300, 300), "a light line across a crisp box is no wall");
    }
    static void open() {
        Page page = new Page(600, 600);
        page.line(arc(300, 300, s(80), 0, 2 * Math.PI), .7);
        check(find(page, 40, 40) == null, "open paper outside the ring finds nothing");
        check(find(new Page(300, 300), 150, 150) == null, "a blank page finds nothing");
        check(find(page, 300, 300) != null, "inside the ring finds its region");
    }
    /** Fill's page with no line pixel, taken whole at once ({@link FillRegion#blankAtOnce}), is the region the search finds there. */
    static void blank() {
        Page light = new Page(600, 400);
        light.line(poly(new double[]{100, 100}, new double[]{500, 100}, new double[]{500, 300}, new double[]{100, 300}, new double[]{100, 100}), 1, .35);
        // Read in three bands of rows, from the tap's: a line in the first is met from any.
        Page crisp = new Page(300, 2000);
        crisp.line(poly(new double[]{50, 100}, new double[]{250, 100}), .7);
        for (int y : new int[]{0, 1000, 1999}) check(!FillRegion.blank(crisp.w, crisp.h, crisp, y), "a crisp line is a wall, read from row " + y);
        for (Page page : new Page[]{new Page(300, 200), new Page(301, 199), new Page(1, 1), light}) {
            check(FillRegion.blank(page.w, page.h, page, page.h - 1), "no line pixel");
            for (int[] tap : new int[][]{{page.w / 2, page.h / 2}, {0, 0}, {page.w - 1, page.h - 1}}) {
                FillRegion.Room whole = FillRegion.filled(page.w, page.h, page, tap[0], tap[1]), searched;
                FillRegion.blankAtOnce = false;
                try { searched = FillRegion.filled(page.w, page.h, page, tap[0], tap[1]); }
                finally { FillRegion.blankAtOnce = true; }
                check(searched != null && same(whole, searched) && whole.area == page.w * page.h, "a page with no line pixel fills whole, as searched: " + page.w + "×" + page.h + " at " + tap[0] + "," + tap[1]);
            }
        }
    }

    // ---- 7. timing ----

    static String timed = "";
    /**
     * Pages the Manta's size (1920 × 2400): the time to find an area's region, median of 15 after warming up, the Manta
     * about 20 times slower than this Mac (ModularBenchChecks). Typical areas on a clean page (a 15° leaf 700 px long, a
     * ring of radius 300 px), then on a page of drawing (rings with gaps, scribbles all over) a ring with drawing inside
     * it, a box of 1300 × 1250 px with more, and open paper. A case over its budget is timed once more.
     */
    static void timing() {
        Page clean = new Page(1920, 2400), drawn = new Page(1920, 2400);
        java.util.Random rnd = new java.util.Random(7);
        for (int k = 0; k < 40; k++) {
            double cx = 100 + rnd.nextDouble() * 1720, cy = 100 + rnd.nextDouble() * 2200, r = 30 + rnd.nextDouble() * 90;
            drawn.line(wobble(arc(cx, cy, r, 0, 2 * Math.PI - .2), s(1.5), k), .2 + .8 * rnd.nextDouble());
        }
        for (int k = 0; k < 30; k++) {
            List<double[]> pts = new ArrayList<>();
            double x = 100 + rnd.nextDouble() * 1700, y = 100 + rnd.nextDouble() * 2200;
            for (int i = 0; i < 40; i++) { pts.add(new double[]{x, y}); x += rnd.nextGaussian() * 20; y += rnd.nextGaussian() * 20; }
            drawn.line(pts, .5);
        }
        for (Page page : new Page[]{clean, drawn}) {
            page.line(wobble(arc(600, 700, 300, 0, 2 * Math.PI), s(1), 3), .6);
            double a = Math.toRadians(7.5), L = 700, R = L / 2 / Math.sin(a), rc = R * Math.cos(a);
            page.line(arc(1300, 600 + rc, R, -Math.PI / 2 - a, -Math.PI / 2 + a), .6);
            page.line(arc(1300, 600 - rc, R, Math.PI / 2 - a, Math.PI / 2 + a), .6);
            page.line(poly(new double[]{300, 1100}, new double[]{1600, 1120}, new double[]{1590, 2350}, new double[]{310, 2330}, new double[]{300, 1100}), .7);
        }
        Object[][] cases = {
                // Budgets about twice this Mac's times (2026-10-19: 2.5, 8, 19, 80, 0.8 ms): the typical areas well under
                // half a second on the Manta, a page-filling box of drawing about two seconds.
                {"a 15° leaf 700 px long", clean, 1300, 600, 6.0},
                {"a ring of radius 300 px", clean, 600, 700, 16.0},
                {"the ring with drawing inside", drawn, 600, 700, 40.0},
                {"a box of 1300 × 1250 px with drawing inside", drawn, 950, 1700, 160.0},
                {"open paper", drawn, 1900, 20, 4.0}};
        StringBuilder out = new StringBuilder("on a 1920 × 2400 page, desktop median (the Manta about 20×): ");
        // Warmed up first, every case, as a page's few fills warm the device's compiler.
        for (int run = 0; run < 6; run++) for (Object[] c : cases) FillRegion.find(((Page)c[1]).w, ((Page)c[1]).h, (Page)c[1], (Integer)c[2], (Integer)c[3]);
        for (Object[] c : cases) {
            Page page = (Page)c[1];
            int x = (Integer)c[2], y = (Integer)c[3];
            double budget = (Double)c[4];
            FillRegion.Room room = null;
            double med = 0;
            for (int attempt = 0; attempt < 2; attempt++) {
                double[] ms = new double[15];
                for (int run = -2; run < ms.length; run++) {
                    long t0 = System.nanoTime();
                    room = FillRegion.find(page.w, page.h, page, x, y);
                    if (run >= 0) ms[run] = (System.nanoTime() - t0) / 1e6;
                }
                Arrays.sort(ms);
                med = ms[ms.length / 2];
                if (med <= budget) break;
            }
            boolean open = c[0].equals("open paper");
            check(open == (room == null), c[0] + (open ? " finds nothing" : " is found"));
            String line = String.format(Locale.ROOT, "%s %.1f ms%s", c[0], med, room == null ? "" : String.format(Locale.ROOT, " (%d px)", room.area));
            note(line);
            Timing.limit(med <= budget, c[0] + " found within " + budget + " ms on the desktop: " + line);
            out.append(out.charAt(out.length() - 2) == ':' ? "" : ", ").append(line);
        }
        timed = out.toString();
    }

    private FillRegionChecks() {}
}
