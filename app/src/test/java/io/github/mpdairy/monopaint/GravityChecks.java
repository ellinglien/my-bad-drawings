package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Gravity (the Gravity Lab's gravity.cjs, ported): wet paint runs downhill when the page tilts, its pigment conserved;
 * the low edge pools and beads; drips break from it, broad, unevenly spaced about the edge's wavelength, varied in length with their paint, darker than the wash, led by a
 * bead, straight down the page; grain sinks; rocking blends; Instant, Cover and dry paint never move, nor anything on a level page; the
 * hand's page tilt and charcoal's dust; old brushes draw as before at any tilt.
 */
public final class GravityChecks {
    static final int W = 520, H = 460;

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        // Every pixel, as the prototype: a big stroke's lattice is checked on its own (lattice()).
        double lattice = ModularStroke.latticePx;
        ModularStroke.latticePx = 1000;
        if (args.length > 0) { dump(args); return; }
        runs(); drains(); pool(); poolEdge(); drips(); sink(); blend(); instant(); fastWet(); dry(); finishNow(); level(); hand(); dust(); oldBrushes(); tilt(); every();
        atBlack();
        ModularStroke.latticePx = lattice;
        coarse();
        System.out.println("PASS: gravity: a tilted page runs a wet wash downhill with its pigment conserved, pools and beads its low edge, drips (from the low edge, broad, unevenly spaced, varied lengths with their paint, darker than the wash, a bead that leads and dries hard, straight, stopped by a level page, merging), sinks grain and blends when rocked; a big stroke's wash in cells of 4 pixels moves alike; Instant, Cover, dry paint and a level page (or one inside the dead zone) never move; the hand's page tilt and charcoal's dust; old brushes draw as before at any tilt");
    }

    /**
     * Since washes reach black: a wash at black runs downhill with its pigment conserved, drains, pools, sinks and blends
     * as at the black it was tuned at; its pooled low edge is darker than the wash inside, and its drips, a few from a
     * moderate tilt, darker than the wash they leave (the flow's darkest paint rises with the wash: GravityFlow.deep).
     * Near black there is little room to be darker, so these are darker, not the tuned shares darker.
     */
    static void atBlack() {
        black = true;
        try {
            runs(); drains(); pool(); sink(); blend();
            ModularBrush b = preset("Pooling wash");
            Drawn lv = draw(b, 0, 0), tl = draw(b, 0, .8);
            double[] P = profile(tl, lv);
            double inside = 0, edge = 0;
            for (int i = 0; i < 12; i++) inside += P[i] / 12;
            for (int i = 36; i < 46; i++) edge += P[i] / 10;
            check(edge > inside + 8, f("Pooling wash at black: the pooled low edge is darker than the wash inside (%.0f vs %.0f)", edge, inside));
            ModularBrush d = preset("Drips");
            Drawn f = draw(d, 0, 0), t = draw(d, 0, .6);
            int[][] C = cols(f);
            List<GravityFlow.Drip> L = t.flow.log;
            check(L.size() >= 2 && L.size() <= 6, f("Drips at black: a moderate tilt (0.6) gives %d drips (want 2–6)", L.size()));
            double wash = 0; int nw = 0; for (int x = 150; x < 370; x++) if (C[x] != null) for (int y = C[x][0] + 5; y < C[x][1] - 5; y++) { wash += f.dark(x, y); nw++; }
            double trail = 0; int nt = 0;
            for (GravityFlow.Drip q : L) for (double[] p : along(q, 1.5 * q.w0, q.len - 2 * q.w0)) { trail += t.at(p[0], p[1]); nt++; }
            check(nt > 0 && trail / nt > wash / nw, f("Drips at black: darker than the wash (middle %.0f vs wash %.0f)", trail / Math.max(1, nt), wash / nw));
            System.out.println(f("  gravity at black: the pooled edge %.0f over the wash's %.0f inside; %d drips, %.0f over the wash's %.0f (darkness of 255)", edge, inside, L.size(), trail / nt, wash / nw));
        } finally { black = false; }
    }

    // ---- brushes ----

    /** The five gravity presets (the Gravity Lab's styles at the middle of their ranges), as ToolPresets carries them. */
    static ModularBrush preset(String name) { return ModularCode.decode(FloatChecks.find(name).code); }
    /** {@code name}'s preset with its gravity dials set to {@code runs, drips, pool, sink, blend}. */
    static ModularBrush with(String name, double runs, double drips, double pool, double sink, double blend) {
        ModularBrush b = preset(name);
        ModularBrush.Part life = b.part(BrushPart.LIFE);
        life.set(PartDial.RUNS, runs); life.set(PartDial.DRIPS, drips); life.set(PartDial.G_POOL, pool); life.set(PartDial.SINK, sink); life.set(PartDial.BLEND, blend);
        return b;
    }

    // ---- drawing ----

    /** What a draw left: the page, the wash's flow (the last one made) and its first ledger, the ticks it took. */
    static final class Drawn {
        ToneDocument page; GravityFlow flow; double[] led0; int ticks; WatercolorSession session;
        int dark(int x, int y) { return x < 0 || y < 0 || x >= page.width || y >= page.height ? 0 : 255 - page.compositeTone(x, y); }
        double at(double x, double y) {
            int ix = (int)Math.floor(x - .5), iy = (int)Math.floor(y - .5);
            double fx = x - .5 - ix, fy = y - .5 - iy;
            return (dark(ix, iy) * (1 - fx) + dark(ix + 1, iy) * fx) * (1 - fy) + (dark(ix, iy + 1) * (1 - fx) + dark(ix + 1, iy + 1) * fx) * fy;
        }
    }
    interface Tilts { PageTilt at(int tick); }
    /**
     * Whether draw() lays a wash at black; else (every check but {@link #atBlack}) at the black it was tuned at, the Gravity
     * Lab's, a wash's 34 % glaze before washes reached black ({@link ModularSizes#sinceBlack}).
     */
    static boolean black;
    /** The session draw() is drawing (a Tilts can look at its flow). */
    static WatercolorSession lastSession;
    /** A paper with no texture at all (the prototype's flat), to judge the model's own smoothness. */
    static final StrokePaper FLAT;
    static { byte[] grey = new byte[PaperGrain.SIDE * PaperGrain.SIDE]; java.util.Arrays.fill(grey, (byte)128); FLAT = new StrokePaper(PaperGrain.scan(grey)); }
    static float[][] washPts(double x0, double x1, double y, double amp) {
        float[][] p = new float[81][];
        for (int i = 0; i <= 80; i++) { double u = i / 80.0; p[i] = new float[]{(float)(x0 + (x1 - x0) * u), (float)(y + amp * Math.sin(u * Math.PI * 1.5)), .7f, (i + 1) * 8}; }
        return p;
    }
    static Drawn draw(ModularBrush b, double tx, double ty) { return draw(b, tick -> PageTilt.of(tx, ty), washPts(90, 430, 140, 25), 110, false); }
    /** {@code b} at {@code size} along {@code pts} on a blank page with the tilt each tick; dried; {@code plain}: on {@link #FLAT}. */
    static Drawn draw(ModularBrush b, Tilts tilt, float[][] pts, double size, boolean plain) {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession session = new WatercolorSession();
        lastSession = session;
        int[] tick = {0};
        session.tilt = () -> tilt.at(tick[0]);
        d.selectLayer(ModularSizes.inks(b) ? InkWashLayers.inkOf(d, InkWashLayers.current(d)) : InkWashLayers.washOf(d, InkWashLayers.current(d)));
        ModularStroke s = new ModularStroke(d, b, size, black ? 1 : ModularSizes.sinceBlack(b, 1), session, plain ? FLAT : null);
        for (float[] q : pts) s.sample(q[0], q[1], ToolSettings.pressure(q[2]), Float.NaN, Float.NaN, 10_000 + (long)q[3]);
        s.finish();
        Drawn out = new Drawn();
        out.page = d; out.session = session;
        while (session.settling() && tick[0] < 5000) {
            session.tickDrying(d);
            tick[0]++;
            GravityFlow f = flowOf(session);
            if (f != null && out.flow != f) { out.flow = f; out.led0 = f.ledger(); }
        }
        out.ticks = tick[0];
        return out;
    }
    /** The session's wash's flow, or null (for the checks: read through the session's settling wash). */
    static GravityFlow flowOf(WatercolorSession session) { DryingWater w = session.drying(); return w == null ? null : w.flow(); }

    static double centroidY(Drawn r) { double a = 0, sy = 0; for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { int d = r.dark(x, y); if (d > 0) { a += d; sy += d * y; } } return sy / a; }
    static double centroidX(Drawn r) { double a = 0, sx = 0; for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { int d = r.dark(x, y); if (d > 0) { a += d; sx += d * x; } } return sx / a; }
    static int differ(ToneDocument a, ToneDocument b) { int n = 0; for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (a.compositeTone(x, y) != b.compositeTone(x, y)) n++; return n; }
    /** The wash's footprint per column: {top, bottom} rows darker than 20, or null. */
    static int[][] cols(Drawn r) {
        int[][] c = new int[W][];
        for (int x = 0; x < W; x++) { int top = -1, bot = -1; for (int y = 0; y < H; y++) if (r.dark(x, y) > 20) { if (top < 0) top = y; bot = y; } if (bot >= 0) c[x] = new int[]{top, bot}; }
        return c;
    }
    static double mean(List<Double> a) { double s = 0; for (double v : a) s += v; return a.isEmpty() ? 0 : s / a.size(); }
    static double cv(List<Double> a) { double m = mean(a), s = 0; for (double v : a) s += (v - m) * (v - m); return Math.sqrt(s / Math.max(1, a.size())) / m; }

    // ---- checks ----

    /** Runs: the wash's pigment ends lower on a page tilted towards you, and further right tilted right. */
    static void runs() {
        ModularBrush b = with("Pooling wash", .8, 0, 0, 0, 0);
        double flat = centroidY(draw(b, 0, 0)), tilted = centroidY(draw(b, 0, 1));
        check(tilted > flat + 3, f("Runs: the pigment's centre moves downhill (level %.1f → tilted %.1f px)", flat, tilted));
        double fx = centroidX(draw(b, 0, 0)), sx = centroidX(draw(b, 1, 0));
        check(sx > fx + 2, f("Runs: tilted right, it runs right (%.1f → %.1f px)", fx, sx));
    }
    /** The pigment flows down with the water, conserved; the outline stays while the colour inside drains. */
    static void drains() {
        for (String name : new String[]{"Pooling wash", "Drips"}) {
            ModularBrush b = preset(name);
            Drawn lv = draw(b, 0, 0), r = draw(b, 0, 1);
            double[] L0 = r.led0, L1 = r.flow.ledger();
            double drift = Math.abs(L1[0] - L0[0]) / L0[0];
            check(drift < 1e-3 && L1[2] > L0[2] + 6, f("%s at tilt (0, 1): the pigment's centre moves %.1f px downhill, its total %.0f → %.0f (%.4f%% drift)", name, L1[2] - L0[2], L0[0], L1[0], drift * 100));
            int[][] C = cols(lv);
            List<Double> up = new ArrayList<>(), upL = new ArrayList<>(), lo = new ArrayList<>(), loL = new ArrayList<>();
            for (int x = 150; x < 370; x++) {
                int[] c = C[x];
                if (c == null) continue;
                int h = c[1] - c[0];
                for (int y = c[0] + 3; y < c[0] + h * .3; y++) { up.add((double)r.dark(x, y)); upL.add((double)lv.dark(x, y)); }
                for (int y = (int)Math.round(c[1] - h * .25); y <= c[1]; y++) { lo.add((double)r.dark(x, y)); loL.add((double)lv.dark(x, y)); }
            }
            check(mean(up) < mean(upL) * .85 && mean(lo) > mean(loL), f("%s: the uphill part lightens (%.0f → %.0f) and the low part darkens (%.0f → %.0f)", name, mean(upL), mean(up), mean(loL), mean(lo)));
        }
    }
    /** Pool: a darker low edge than the wash's middle. */
    static void pool() {
        ModularBrush b = with("Pooling wash", 0, 0, 1, 0, 0);
        double fl = band(draw(b, 0, 0)), t = band(draw(b, 0, .8));
        check(t > fl * 1.15, f("Pool: the low edge is darker than the middle (ratio level %.2f → tilted %.2f)", fl, t));
    }
    private static double band(Drawn r) {
        double lo = 0, mid = 0; int nl = 0, nm = 0;
        for (int x = 150; x < 370; x++) {
            int top = -1, bot = -1;
            for (int y = 0; y < H; y++) if (r.dark(x, y) > 20) { if (top < 0) top = y; bot = y; }
            if (bot < 0) continue;
            for (int y = bot - 6; y <= bot; y++) { lo += r.dark(x, y); nl++; }
            int m = (top + bot) >> 1;
            for (int y = m - 3; y <= m + 3; y++) { mid += r.dark(x, y); nm++; }
        }
        return (lo / nl) / (mid / nm);
    }
    /** The Pooling wash's low edge: broken and beaded along it, not a translation of the wash, darker than inside and fading into it smoothly. */
    static void poolEdge() {
        ModularBrush b = preset("Pooling wash");
        Drawn lv = draw(b, 0, 0), tl = draw(b, 0, .8);
        double cvL = lowEdgeCv(lv), cvT = lowEdgeCv(tl);
        check(cvT > .2 && cvT > cvL * 2, f("Pool: the low edge's darkness varies along it (CV level %.2f → tilted %.2f)", cvL, cvT));
        List<Double> shift = new ArrayList<>();
        for (int x = 130; x < 390; x++) { int a = -1, c = -1; for (int y = 0; y < H; y++) { if (lv.dark(x, y) > 20) a = y; if (tl.dark(x, y) > 20) c = y; } shift.add((double)(c - a)); }
        double m = mean(shift), sd = 0; for (double v : shift) sd += (v - m) * (v - m); sd = Math.sqrt(sd / shift.size());
        check(sd > 3, f("Pool: not a translation: the low edge moves %.1f ± %.1f px along its length", m, sd));
        Drawn tF = draw(preset("Pooling wash"), t -> PageTilt.of(0, .8), washPts(90, 430, 140, 25), 110, true), lvF = draw(preset("Pooling wash"), t -> PageTilt.LEVEL, washPts(90, 430, 140, 25), 110, true);
        double[] P = profile(tl, lv), F = profile(tF, lvF);
        double inside = 0, edge = 0;
        for (int i = 0; i < 12; i++) inside += P[i] / 12;
        for (int i = 36; i < 46; i++) edge += P[i] / 10;
        check(edge > inside * 1.15, f("Pooling wash: the pooled low edge is darker than the wash inside (%.0f vs %.0f)", edge, inside));
        double d2 = 0; int fall = 0;
        for (int i = 1; i + 1 < 40; i++) d2 = Math.max(d2, Math.abs(F[i + 1] - 2 * F[i] + F[i - 1]));
        for (int i = 0; i + 1 < 40; i++) if (F[i + 1] < F[i] - .5) fall++;
        check(d2 < 6 && fall <= 3 && F[39] > F[0] + 8, f("Pooling wash: on plain paper the band fades smoothly into the edge (max 2nd difference %.2f, %d falls over 40 px, %.0f → %.0f)", d2, fall, F[0], F[39]));
    }
    private static double lowEdgeCv(Drawn r) {
        List<Double> v = new ArrayList<>();
        for (int x = 130; x < 390; x++) { int bot = -1; for (int y = 0; y < H; y++) if (r.dark(x, y) > 20) bot = y; if (bot < 0) continue; double s = 0; for (int y = bot - 5; y <= bot; y++) s += r.dark(x, y); v.add(s / 6); }
        return cv(v);
    }
    /** The darkness from 40 px inside the level wash's low edge to 6 px past it, averaged along it. */
    private static double[] profile(Drawn p, Drawn ref) {
        int[][] C = cols(ref);
        double[] out = new double[47];
        for (int o = -40; o <= 6; o++) { double s = 0; int n = 0; for (int x = 150; x < 370; x++) if (C[x] != null) { s += p.dark(x, C[x][1] + o); n++; } out[o + 40] = s / n; }
        return out;
    }

    /**
     * Drips: a few broad ones from the low edge where the bead gathers, about its natural wavelength apart but unevenly
     * (now and then none, or two side by side that join), widths a little varied, lengths varied with their paint, none level; darker than the wash, rims darker than the middle, a darker bead leading and drying hard where it stops;
     * smooth along their length, straight down the page, longer the steeper it is, stopped by a level page; they merge;
     * the same seed draws the same.
     */
    static void drips() {
        ModularBrush b = preset("Drips"), max = with("Drips", .35, 1, .3, 0, 0);
        Drawn f = draw(b, 0, 0), t = draw(b, 0, .6);
        int[][] C = cols(f);
        double washH; { double s = 0; int n = 0; for (int x = 150; x < 370; x += 10) if (C[x] != null) { s += C[x][1] - C[x][0]; n++; } washH = s / n; }
        List<GravityFlow.Drip> L = t.flow.log;
        check(L.size() >= 2 && L.size() <= 6, f("Drips: a moderate tilt (0.6) gives %d drips (want 2–6: few, and broad)", L.size()));
        // Broad: each several times wider than a hairline, a fifth or more of the wash's height, measured on the page too.
        List<Double> w0 = new ArrayList<>(), seen = new ArrayList<>();
        for (GravityFlow.Drip d : L) {
            w0.add(d.w0);
            if (d.pathN < 12) continue;
            int k = (d.pathN / 6) * 3; double x = d.path[k], y = d.path[k + 1], c = t.at(x + .5, y + .5), th = Math.max(6, c / 3), a = 0, bb = 0;
            for (double u = 0; u < 2 * d.w0 && t.at(x + .5 - u, y + .5) > th; u += .25) a = u;
            for (double u = 0; u < 2 * d.w0 && t.at(x + .5 + u, y + .5) > th; u += .25) bb = u;
            seen.add(a + bb);
        }
        double wmin = Double.MAX_VALUE; for (double v : w0) wmin = Math.min(wmin, v);
        check(wmin >= .2 * washH && !seen.isEmpty() && mean(seen) >= .17 * washH, f("Drips: broad: %.1f px at the least, %.1f px across on the page, of the wash's %.0f px", wmin, mean(seen), washH));
        // From the low edge: each starts at the wash's bottom edge where it leaves.
        double off = 0; for (GravityFlow.Drip d : L) { int[] c = C[(int)d.x0]; off = Math.max(off, c == null ? 99 : Math.abs(d.y0 - c[1])); }
        check(off <= 8, f("Drips: each starts at the low edge (at most %.1f px from it)", off));
        // Natural, not regular: over several strokes, widths a little varied; spaced at about the edge's natural
        // wavelength but unevenly (now and then a stretch with none, or two side by side that join); lengths varied, the
        // heavier drips running further; mostly straight down, now and then a slight lean.
        { List<Double> ws = new ArrayList<>(), gaps = new ArrayList<>(), lens = new ArrayList<>(), loads = new ArrayList<>();
            double lean = 0, least = Double.MAX_VALUE; int joins = 0;
            for (int k = 0; k < 6; k++) for (double ty : new double[]{.6, .8}) {
                Drawn s = draw(b, q -> PageTilt.of(0, ty), washPts(90 + 3 * k, 430 + 3 * k, 140, 25), 110, false);
                joins += s.flow.merges;
                List<Double> xs = new ArrayList<>();
                for (GravityFlow.Drip d : s.flow.log) {
                    xs.add(d.x0);
                    if (ty != .6) continue;
                    ws.add(d.w0);
                    if (d.into == null && d.merged == 0) { lens.add(d.len); loads.add(d.load); }
                    if (d.pathN >= 3) lean = Math.max(lean, Math.abs(d.path[d.pathN - 3] - d.x0) / Math.max(1, d.path[d.pathN - 2] - d.y0));
                }
                java.util.Collections.sort(xs);
                for (int i = 1; i < xs.size(); i++) { double g = xs.get(i) - xs.get(i - 1); least = Math.min(least, g); if (ty == .6) gaps.add(g); }
            }
            double wm = mean(ws), wlo = Double.MAX_VALUE, whi = 0; for (double v : ws) { wlo = Math.min(wlo, v / wm); whi = Math.max(whi, v / wm); }
            check(cv(ws) >= .04 && cv(ws) <= .2 && wlo >= .7 && whi <= 1.35, f("Drips: widths a little varied (CV %.2f, %.2f–%.2f× the mean)", cv(ws), wlo, whi));
            List<Double> sg = new ArrayList<>(gaps); java.util.Collections.sort(sg);
            double med = sg.get(sg.size() / 2) / wm;
            check(least >= wm && cv(gaps) >= .2 && med >= 3 && med <= 6, f("Drips: spaced unevenly about the edge's wavelength: median gap %.1f widths, CV %.2f, never closer than %.1f widths", med, cv(gaps), least / wm));
            check(joins >= 1, f("Drips: two side by side join now and then (%d over 12 strokes)", joins));
            double ml = mean(lens), ma = mean(loads), cov = 0, vl = 0, va = 0;
            for (int i = 0; i < lens.size(); i++) { double x = loads.get(i) - ma, y = lens.get(i) - ml; cov += x * y; va += x * x; vl += y * y; }
            double corr = cov / Math.sqrt(Math.max(1e-12, va * vl)), shortest = Double.MAX_VALUE; for (double v : lens) shortest = Math.min(shortest, v);
            check(cv(lens) >= .2 && corr >= .5 && shortest <= .5 * ml, f("Drips: lengths varied (CV %.2f, shortest %.0f of a mean %.0f px), the heavier running further (correlation %.2f)", cv(lens), shortest, ml, corr));
            check(lean >= .02 && lean <= .1, f("Drips: now and then a slight lean (the most %.3f across a pixel down)", lean)); }
        // Darker than the wash they leave.
        { double wash = 0; int nw = 0; for (int x = 150; x < 370; x++) if (C[x] != null) for (int y = C[x][0] + 5; y < C[x][1] - 5; y++) { wash += f.dark(x, y); nw++; }
            double trail = 0; int nt = 0;
            for (GravityFlow.Drip d : L) { List<double[]> S = along(d, 1.5 * d.w0, d.len - 2 * d.w0); for (double[] q : S) { trail += t.at(q[0], q[1]); nt++; } }
            check(nt > 0 && trail / nt > 1.15 * wash / nw, f("Drips: darker than the wash (middle %.0f vs wash %.0f)", trail / Math.max(1, nt), wash / nw)); }
        // A drip that stops dries with a hard edge round its bead (its front edge darker than its middle).
        { List<Double> edge = new ArrayList<>(), mid = new ArrayList<>();
            for (GravityFlow.Drip d : L) { double r = d.br; if (d.into != null || d.beadN == 0 || d.y + r >= H - 1 || d.x - r < 1 || d.x + r >= W - 1) continue; mid.add(t.at(d.x, d.y)); edge.add(t.at(d.x + d.dx * .8 * r, d.y + d.dy * .8 * r)); }
            check(!edge.isEmpty() && mean(edge) > mean(mid) * 1.1, f("Drips: %d stop on the page with a bead that dries with a hard edge (edge %.0f vs middle %.0f)", edge.size(), mean(edge), mean(mid))); }
        // No banding: on plain paper the tone along each drip's middle changes smoothly.
        { Drawn fl = draw(preset("Drips"), q -> PageTilt.of(0, .6), washPts(90, 430, 140, 25), 110, true); double d1 = 0, d2 = 0; int nd = 0;
            for (GravityFlow.Drip d : fl.flow.log) {
                if (d.into != null || d.len < 5 * d.w0) continue;
                nd++;
                List<double[]> S = along(d, 1.5 * d.w0, d.len - 2.5 * d.w0);
                double[] pr = new double[S.size()];
                for (int i = 0; i < pr.length; i++) { double[] q = S.get(i); double s = 0; int n = 0; for (double u = -q[4] / 4; u <= q[4] / 4; u += .5) { s += fl.at(q[0] - q[3] * u, q[1] + q[2] * u); n++; } pr[i] = s / n; }
                for (int i = 1; i + 1 < pr.length; i++) { d1 = Math.max(d1, Math.abs(pr[i + 1] - pr[i])); d2 = Math.max(d2, Math.abs(pr[i + 1] - 2 * pr[i] + pr[i - 1])); }
            }
            check(nd > 0 && d1 < 1.5 && d2 < 1.5, f("Drips: smooth tone along the middle on plain paper, no bands (%d drips, max step %.2f, max 2nd difference %.2f of 255)", nd, d1, d2));
            // The width swells and pinches only a little along a drip, measured on the page, with no step jumps.
            List<Double> cvs = new ArrayList<>(); double step = 0;
            for (GravityFlow.Drip d : fl.flow.log) {
                if (d.into != null || d.len < 6 * d.w0) continue;
                List<double[]> S = along(d, 2 * d.w0, d.len - 2 * d.w0);
                List<Double> wd = new ArrayList<>();
                for (double[] q : S) { double c = fl.at(q[0], q[1]), th = Math.max(4, c / 3), a = 0, bb = 0;
                    for (double u = 0; u < 2 * d.w0 && fl.at(q[0] - q[3] * u, q[1] + q[2] * u) > th; u += .25) a = u;
                    for (double u = 0; u < 2 * d.w0 && fl.at(q[0] + q[3] * u, q[1] - q[2] * u) > th; u += .25) bb = u;
                    wd.add(a + bb); }
                cvs.add(cv(wd));
                for (int i = 3; i < wd.size(); i++) step = Math.max(step, Math.abs(wd.get(i) - wd.get(i - 3)) / d.w0);
            }
            double most = 0; for (double v : cvs) most = Math.max(most, v);
            check(!cvs.isEmpty() && most <= .15 && step <= .2, f("Drips: an even width along each drip (CV at most %.2f), no step jumps (largest change over 3 px %.2f× the width)", most, step)); }
        check(below(f) == 0 && (f.flow == null || f.flow.log.isEmpty()), "Drips: none on a level page");
        Drawn m = draw(max, 0, 1);
        double longest = 0; for (GravityFlow.Drip d : m.flow.log) longest = Math.max(longest, d.len / d.w0);
        check(longest >= 8 && below(m) > 3000, f("Drips at Drips 1 and tilt (0, 1): the longest runs %.1f× its width (%d drips, %d px below the wash)", longest, m.flow.log.size(), below(m)));
        // Steeper runs further: the longest run by the same time (before the page's foot stops them) at tilt (0, 1) beats
        // tilt (0, .5).
        { double[] most = new double[2];
            for (int i = 0; i < 2; i++) { int at = i; double ty = i == 0 ? .5 : 1;
                draw(b, q -> { if (q == 90) { GravityFlow fl = flowOf(lastSession); if (fl != null) for (GravityFlow.Drip d : fl.log) most[at] = Math.max(most[at], d.len); } return PageTilt.of(0, ty); }, washPts(90, 430, 140, 25), 110, false); }
            check(most[1] > 1.3 * most[0], f("Drips: steeper runs further (the longest %.0f px at tilt 0.5, %.0f at 1, by the same time)", most[0], most[1])); }
        // Rims darker than the middle; a darker bead at the end.
        { double rim = 0, mid = 0; int n = 0;
            for (GravityFlow.Drip d : m.flow.log) { if (d.pathN < 30 || d.into != null) continue; int k = (d.pathN / 6) * 3; double x = d.path[k], y = d.path[k + 1], w = d.path[k + 2]; if (w < 5) continue;
                mid += m.at(x + .5, y + .5); rim += (m.at(x - w * .38 + .5, y + .5) + m.at(x + w * .38 + .5, y + .5)) / 2; n++; }
            check(n > 0 && rim > mid * 1.08, f("Drips: pigment collects at the edges (rim %.1f vs middle %.1f)", rim / Math.max(1, n), mid / Math.max(1, n))); }
        { double bead = 0, trail = 0; int n = 0;
            for (GravityFlow.Drip d : L) { if (d.beadN == 0 || d.pathN < 6 || d.y + d.br >= H - 1) continue; int k = (d.pathN / 6) * 3; bead += t.dark((int)(d.x + d.dx * .4 * d.br), (int)(d.y + d.dy * .4 * d.br)); trail += t.dark((int)d.path[k], (int)d.path[k + 1]); n++; }
            check(n > 0 && bead > trail * 1.15, f("Drips: a darker bead at the end (%.1f vs trail %.1f)", bead / Math.max(1, n), trail / Math.max(1, n))); }
        // Straight down the page: all but perfectly on a steep page, nearly so on a gentle one.
        for (double tilt : new double[]{1, .5}) {
            Drawn st = tilt == 1 ? m : draw(max, 0, tilt);
            double dev = 0; int n = 0;
            for (GravityFlow.Drip d : st.flow.log) {
                if (d.into != null || d.len <= 4 * d.w0) continue;
                n++;
                // Off the straight line from where it starts to where it ends (a lean is straight).
                double ex = d.path[d.pathN - 3] - d.x0, ey = d.path[d.pathN - 2] - d.y0, el = Math.hypot(ex, ey), mx = 0;
                for (int k = 0; k < d.pathN; k += 3) mx = Math.max(mx, Math.abs((d.path[k] - d.x0) * ey - (d.path[k + 1] - d.y0) * ex) / el);
                dev = Math.max(dev, mx / d.w0);
            }
            check(n > 0 && dev < (tilt == 1 ? .15 : .4), f("Drips at tilt (0, %.1f) run straight: off a straight line at most %.2f× the width (%d long drips)", tilt, dev, n));
        }
        // A level page stops them where they are.
        { double[] at = new double[1]; List<double[]> lens = new ArrayList<>();
            Drawn lv = draw(max, q -> { if (q == 70) { GravityFlow fl = flowOf(lastSession); if (fl != null) for (GravityFlow.Drip d : fl.log) lens.add(new double[]{d.len}); } return q < 70 ? PageTilt.of(0, 1) : PageTilt.LEVEL; }, washPts(90, 430, 140, 25), 110, false);
            boolean same = !lens.isEmpty() && lens.size() == lv.flow.log.size();
            for (int i = 0; same && i < lens.size(); i++) same = Math.abs(lv.flow.log.get(i).len - lens.get(i)[0]) < 1e-9;
            check(same, f("Drips: a level page stops them where they are (%d drips)", lens.size())); }
        Drawn turn = draw(max, q -> q < 25 ? PageTilt.of(0, 1) : PageTilt.of(1, .15), washPts(90, 430, 140, 25), 110, false);
        check(turn.flow.merges >= 1, f("Drips: drips the tilt swings together merge (%d merges)", turn.flow.merges));
        check(differ(draw(b, 0, .6).page, t.page) == 0, "Drips: the same seed draws exactly the same");
    }
    private static int below(Drawn r) { int c = 0; for (int x = 0; x < W; x++) for (int y = 260; y < H; y++) if (r.dark(x, y) > 25) c++; return c; }
    /** Points along a drip's path every page pixel between run lengths s0 and s1: x, y, dirx, diry, width. */
    private static List<double[]> along(GravityFlow.Drip d, double s0, double s1) {
        float[] P = new float[d.pathN + 3];
        P[0] = (float)d.x0; P[1] = (float)d.y0; P[2] = (float)d.w0;
        System.arraycopy(d.path, 0, P, 3, d.pathN);
        List<double[]> out = new ArrayList<>();
        double acc = 0;
        for (int k = 0; k + 5 < P.length; k += 3) {
            double ax = P[k], ay = P[k + 1], bx = P[k + 3], by = P[k + 4], len = Math.hypot(bx - ax, by - ay);
            if (len == 0) len = 1e-9;
            for (double s = Math.ceil(acc) - acc; s < len; s++) { double g = acc + s; if (g >= s0 && g <= s1) out.add(new double[]{ax + .5 + (bx - ax) * s / len, ay + .5 + (by - ay) * s / len, (bx - ax) / len, (by - ay) / len, P[k + 2] + (P[k + 5] - P[k + 2]) * s / len}); }
            acc += len;
        }
        return out;
    }

    /** Sink: the grain gathers downhill (more texture in the low half of the wash than the high). */
    static void sink() {
        ModularBrush b = with("Sinking grain", 0, 0, 0, .95, 0);
        float[][] flat = washPts(90, 430, 150, 0);
        double fl = texture(draw(b, t -> PageTilt.LEVEL, flat, 110, false)), t = texture(draw(b, q -> PageTilt.of(0, .8), flat, 110, false));
        check(t > fl * 1.1, f("Sink: grain texture low/high ratio level %.2f → tilted %.2f", fl, t));
    }
    private static double texture(Drawn r) {
        double up = 0, lo = 0;
        for (int y = 100; y < 200; y++) for (int x = 120; x < 400; x++) { double g = Math.abs(r.dark(x, y) - r.dark(x + 1, y)) + Math.abs(r.dark(x, y) - r.dark(x, y + 1)); if (y < 140) up += g; else if (y >= 160) lo += g; }
        return lo / Math.max(1, up);
    }
    /** Blend: rocking the page while wet softens the wash's edges. */
    static void blend() {
        ModularBrush b = with("Tilt blend", 0, 0, 0, 0, .95);
        double still = sharpness(draw(b, 0, .3)), rock = sharpness(draw(b, t -> PageTilt.of(Math.sin(t / 5.0) * .8, .3), washPts(90, 430, 140, 25), 110, false));
        check(rock < still * .85, f("Blend: rocking softens its edges (steepest step across them %.1f → %.1f)", still, rock));
    }
    /** The steepest step across the wash's top and bottom edges, averaged along it. */
    private static double sharpness(Drawn r) {
        double s = 0;
        for (int x = 150; x < 370; x++) {
            double top = 0, bottom = 0;
            for (int y = 0; y + 1 < H; y++) { double g = Math.abs(r.dark(x, y + 1) - r.dark(x, y)); if (y < 140) top = Math.max(top, g); else bottom = Math.max(bottom, g); }
            s += top + bottom;
        }
        return s / 440;
    }

    /** Instant never moves: the same at any tilt, nothing wet. Cover never runs. */
    static void instant() {
        ModularBrush b = preset("Drips");
        b.set(BrushPart.LIFE, ModularBrush.switchTo(b.part(BrushPart.LIFE), PartChoice.NONE));
        Drawn a = draw(b, 0, 0), c = draw(b, .6, .8);
        check(differ(a.page, c.page) == 0 && !a.session.settling() && !c.session.settling(), "Instant: identical at any tilt, nothing stays wet");
        ModularBrush cover = preset("Drips");
        cover.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.COVER));
        Drawn e = draw(cover, 0, 0), g = draw(cover, 0, 1);
        check(differ(e.page, g.page) == 0 && g.flow == null, "Cover never runs: identical at any tilt");
    }
    /** Quick moves only in its window: tilt changes the mark, nothing wet after lift; without its dials, tilt changes nothing. */
    static void fastWet() {
        ModularBrush b = with("Pooling wash", 1, 0, 1, 0, 0);
        b.set(BrushPart.LIFE, ModularBrush.switchTo(b.part(BrushPart.LIFE), PartChoice.FAST_WET));
        Drawn a = draw(b, 0, 0), c = draw(b, 0, 1);
        int n = differ(a.page, c.page);
        check(n > 100 && !c.session.settling(), "Quick: runs and pool act in the window (" + n + " px differ), nothing wet after lift");
        ModularBrush d = with("Pooling wash", 0, 0, 0, 0, 0);
        d.set(BrushPart.LIFE, ModularBrush.switchTo(d.part(BrushPart.LIFE), PartChoice.FAST_WET));
        check(differ(draw(d, 0, 0).page, draw(d, .7, .7).page) == 0, "Quick without gravity dials: identical at any tilt");
    }
    /** Dry paint never moves: once dry, more tilted ticks change nothing; it dries on time. */
    static void dry() {
        ModularBrush b = preset("Drips");
        Drawn r = draw(b, 0, 1);
        long ticks = Math.round(b.dryingMs() * DryingWater.TPS / 1000.0);
        int[] snap = tones(r.page);
        r.session.tilt = () -> PageTilt.of(1, 0);
        for (int i = 0; i < 150; i++) r.session.tickDrying(r.page);
        check(java.util.Arrays.equals(snap, tones(r.page)) && !r.session.settling(), "Dry paint never moves: 150 more ticks at another tilt change nothing");
        check(r.ticks <= ticks + 2, f("It dries on time: %d ticks for %.1f s", r.ticks, b.dryingMs() / 1000.0));
    }
    static int[] tones(ToneDocument d) { int[] t = new int[d.width * d.height]; for (int i = 0; i < t.length; i++) t[i] = d.compositeTone(i % d.width, i / d.width); return t; }
    /** The wet indicator's tap: dry at once, nothing moves after; one Undo takes back the stroke and everything it did. */
    static void finishNow() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession session = new WatercolorSession();
        session.tilt = () -> PageTilt.of(0, 1);
        ModularStroke s = ModularStroke.start(d, preset("Drips"), 110, ModularSizes.sinceBlack(preset("Drips"), 1), session);
        for (float[] q : washPts(90, 430, 140, 25)) s.sample(q[0], q[1], ToolSettings.pressure(q[2]), Float.NaN, Float.NaN, 10_000 + (long)q[3]);
        s.finish();
        for (int i = 0; i < 20; i++) session.tickDrying(d);
        session.settleNow(d);
        int[] snap = tones(d);
        for (int i = 0; i < 40; i++) session.tickDrying(d);
        check(!session.settling() && java.util.Arrays.equals(snap, tones(d)), "Finish now: dry at once, nothing moves afterwards");
        d.undo();
        boolean blank = true;
        for (int y = 0; y < H; y += 3) for (int x = 0; x < W; x += 3) blank &= d.compositeTone(x, y) == 255;
        check(blank, "One Undo takes back the stroke, its settling and everything gravity did");
    }
    /** A level page moves nothing: brushes with gravity dials draw exactly what they draw without them. */
    static void level() {
        for (String name : new String[]{"Drips", "Pooling wash", "Tilt blend", "Sinking grain"}) {
            // At a drying time short enough that gravity's longer range keeps its ticks (2.5 s), so the two dry alike.
            ModularBrush b = preset(name), plain = with(name, 0, 0, 0, 0, 0);
            b.part(BrushPart.LIFE).set(PartDial.DRYING, .3); plain.part(BrushPart.LIFE).set(PartDial.DRYING, .3);
            check(Math.round(b.dryingMs() / 100.0) == Math.round(plain.dryingMs() / 100.0), "The same ticks with and without gravity at 2.5 s");
            Drawn with = draw(b, 0, 0), without = draw(plain, 0, 0);
            check(differ(with.page, without.page) == 0 && with.flow == null, name + ": on a level page it draws exactly what it draws without gravity");
            PageTilt desk = PageTilt.pull(PageTilt.FLAT * .7, PageTilt.FLAT * .7);
            check(differ(draw(b, t -> desk, washPts(90, 430, 140, 25), 110, false).page, without.page) == 0, name + ": a tilt inside the dead zone (a desk) is level");
        }
    }
    /** The hand's page tilt: drawn downhill is wider and darker than drawn uphill. */
    static void hand() {
        ModularBrush b = ModularCode.decode(ToolPresets.all().get(0).code);
        b.part(BrushPart.HAND).set(PartDial.GS, 1); b.part(BrushPart.HAND).set(PartDial.GD, 1);
        b.set(BrushPart.HAND, ModularBrush.namedHand(b.part(BrushPart.HAND)));
        b.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.NONE));
        float[][] down = new float[61][], up = new float[61][];
        for (int i = 0; i <= 60; i++) {
            down[i] = new float[]{(float)(260 + 30 * Math.sin(i / 20.0)), (float)(60 + 340 * i / 60.0), .6f, i * 8};
            up[i] = new float[]{(float)(260 + 30 * Math.sin(i / 20.0)), (float)(400 - 340 * i / 60.0), .6f, i * 8};
        }
        double dn = ink(draw(b, t -> PageTilt.of(0, 1), down, 10, false)), upInk = ink(draw(b, t -> PageTilt.of(0, 1), up, 10, false));
        double lv = ink(draw(b, t -> PageTilt.LEVEL, down, 10, false)), lvUp = ink(draw(b, t -> PageTilt.LEVEL, up, 10, false));
        check(dn > upInk * 1.3 && Math.abs(lv - lvUp) < lv * .02, f("Page tilt → size and dark: downhill %.0f vs uphill %.0f ink (level %.0f / %.0f)", dn, upInk, lv, lvUp));
    }
    static double ink(Drawn r) { double c = 0; for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) c += r.dark(x, y); return c; }
    /** Charcoal's dust falls downhill at lift, only when tilted. */
    static void dust() {
        ModularBrush b = preset("Charcoal dust");
        float[][] pts = washPts(90, 430, 140, 25);
        int t = specks(draw(b, q -> PageTilt.of(0, 1), pts, 30, false)), fl = specks(draw(b, q -> PageTilt.LEVEL, pts, 30, false));
        check(t > fl + 20, "Dust: specks below the stroke when tilted (" + t + " vs level " + fl + ")");
    }
    private static int specks(Drawn r) { int c = 0; for (int y = 200; y < H; y++) for (int x = 0; x < W; x++) if (r.dark(x, y) > 4) c++; return c; }
    /**
     * Old brushes: every preset and built-in without gravity dials draws the same at any tilt (their codes are
     * unchanged; LegacyConversionChecks and the bench's page hashes pin their pages).
     */
    static void oldBrushes() {
        int drawn = 0, diff = 0;
        for (ToolPresets.Preset p : ToolPresets.all()) {
            ModularBrush b = ModularCode.decode(p.code);
            check(ModularCode.encode(b).equals(p.code) || HandHatchChecks.handHatch(p.code), "An old code writes back as it was (Hand Hatch's as Hatch): " + p.code);
            // Float's paint drifts on a tilted page while it floats, as it should.
            if (usesGravity(b) || b.choice(BrushPart.LIFE) == PartChoice.FLOAT) continue;
            if (drawn++ % 3 != 0) continue;
            double size = ModularSizes.inks(b) ? 8 : 50;
            diff += differ(draw(b, t -> PageTilt.LEVEL, washPts(40, 260, 100, 30), size, false).page, draw(b, t -> PageTilt.of(.3, .9), washPts(40, 260, 100, 30), size, false).page);
        }
        check(diff == 0, f("%d presets without gravity draw the same on a tilted page: %d px differ", drawn, diff));
    }
    static boolean usesGravity(ModularBrush b) {
        for (BrushPart part : BrushPart.values()) for (PartDial d : b.part(part).choice.dials) if (d.gravity() && b.part(part).get(d) > 0) return true;
        return false;
    }
    /** The sensor's mapping: the dead zone, the strength past it, the display's rotation. */
    static void tilt() {
        check(PageTilt.pull(.05, .05).level() && !PageTilt.pull(0, .2).level(), "Inside about 4.6° the page is level; past it, it tilts");
        check(Math.abs(PageTilt.pull(0, 1).m - 1) < 1e-9 && Math.abs(PageTilt.pull(0, PageTilt.FLAT + 1e-6).m) < 1e-5, "The strength rises from 0 at the dead zone's edge to 1 upright");
        double[] up = PageTilt.fromDevice(0, 9.80665, 0);
        check(Math.abs(up[0]) < 1e-9 && Math.abs(up[1] - 1) < 1e-9, "Held upright, the page's bottom is downhill");
        double[] right = PageTilt.fromDevice(-9.80665, 0, 0);
        check(right[0] > .99 && Math.abs(right[1]) < 1e-9, "Its right edge lowered, paint runs right");
        double[] turned = PageTilt.fromDevice(9.80665, 0, 1);
        check(Math.abs(turned[0]) < 1e-9 && turned[1] > .99, "Turned a quarter (the display's rotation 1), the device's left edge down is the page's bottom");
    }
    /**
     * A big stroke (past the lattice's 100 px) moves in cells of 4 pixels, a quarter of the work: as far a tick, its
     * pigment conserved, about as many drips; still nothing on a level page.
     */
    static void coarse() {
        // The same wash on every pixel and on the lattice (a paint's past 50 px, ModularStroke.latticeFrom).
        double lattice = ModularStroke.latticePx;
        ModularStroke.latticePx = 1000;
        Drawn fine;
        try { fine = draw(preset("Drips"), 0, .8); } finally { ModularStroke.latticePx = lattice; }
        Drawn big = draw(preset("Drips"), 0, .8);
        check(fine.flow.cs == 2 && big.flow.cs == 4, "A wash on the lattice moves in cells of 4 pixels, on every pixel 2");
        double[] L0 = big.led0, L1 = big.flow.ledger();
        check(Math.abs(L1[0] - L0[0]) / L0[0] < 1e-3 && L1[2] > L0[2] + 3, f("In cells of 4 the pigment is conserved and moves downhill (%.1f px)", L1[2] - L0[2]));
        int nf = fine.flow.log.size(), nb = big.flow.log.size();
        check(nb >= 2 && Math.abs(nb - nf) <= 2, f("and drips about as often (%d against %d)", nb, nf));
        ModularBrush b = preset("Drips"), plain = with("Drips", 0, 0, 0, 0, 0);
        b.part(BrushPart.LIFE).set(PartDial.DRYING, .3); plain.part(BrushPart.LIFE).set(PartDial.DRYING, .3);
        check(differ(draw(b, 0, 0).page, draw(plain, 0, 0).page) == 0, "and on a level page draws exactly what it draws without gravity");
    }

    /** Every tip and medium on Quick and Slow, every gravity dial full, rocked and tilted: draws, no stray tones, dries. */
    static void every() {
        List<String> bad = new ArrayList<>(); int k = 0;
        for (PartChoice tip : BrushPart.TIP.choices()) for (PartChoice med : BrushPart.MEDIUM.choices()) for (PartChoice life : new PartChoice[]{PartChoice.FAST_WET, PartChoice.DRYING}) {
            ModularBrush b = preset("Drips");
            b.set(BrushPart.TIP, ModularBrush.switchTo(b.part(BrushPart.TIP), tip));
            b.set(BrushPart.MEDIUM, ModularBrush.switchTo(b.part(BrushPart.MEDIUM), med));
            b.set(BrushPart.LIFE, ModularBrush.switchTo(b.part(BrushPart.LIFE), life));
            for (PartDial g : PartDial.GRAVITY) if (b.part(BrushPart.LIFE).has(g)) b.part(BrushPart.LIFE).set(g, 1);
            if (med == PartChoice.CHARCOAL) b.part(BrushPart.MEDIUM).set(PartDial.DUST, 1);
            for (PartDial c : PartDial.PAGE_CELLS) b.part(BrushPart.HAND).set(c, 1);
            if (life == PartChoice.DRYING) b.part(BrushPart.LIFE).set(PartDial.DRYING, .3);
            boolean rock = k++ % 3 == 0, ink = ModularSizes.inks(b);
            Drawn r = draw(b, t -> PageTilt.of(rock ? Math.sin(t / 5.0) * .5 : .5, .8), washPts(90, 430, 140, 25), ink ? 12 : 60, false);
            // A resist marks the page's masking fluid, not its tones (newmods.js sweep: counted by its plane).
            int marked = 0; for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (r.dark(x, y) > 17 || r.page.maskShield(x, y) >= 128) marked++;
            if (marked < 100 || r.session.settling()) bad.add(tip.key + "/" + med.key + "/" + life.key + " marked " + marked);
        }
        check(bad.isEmpty(), "Every tip × medium × Quick/Slow with every gravity dial full draws and dries" + (bad.isEmpty() ? "" : ": " + bad));
    }

    // ---- images, for looking (not a check): java GravityChecks <out.pgm> <preset> <tx> <ty> [plain] ----
    static void dump(String[] a) {
        Drawn r = draw(preset(a[1]), t -> PageTilt.of(Double.parseDouble(a[2]), Double.parseDouble(a[3])), washPts(90, 430, 140, 25), 110, a.length > 4);
        try (java.io.OutputStream out = new java.io.FileOutputStream(a[0])) {
            out.write(("P5\n" + W + " " + H + "\n255\n").getBytes("US-ASCII"));
            byte[] px = new byte[W * H];
            for (int i = 0; i < px.length; i++) px[i] = (byte)r.page.compositeTone(i % W, i / W);
            out.write(px);
        } catch (java.io.IOException e) { throw new RuntimeException(e); }
        System.out.println(a[1] + " ticks " + r.ticks + (r.flow != null ? " drips " + r.flow.log.size() + " merges " + r.flow.merges : " no flow"));
    }

    static String f(String format, Object... args) { return String.format(Locale.ROOT, format, args); }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); if (VERBOSE) System.out.println("  ok  " + what); }
    /** Each check's line as it passes (java -Dverbose=true ...). */
    static final boolean VERBOSE = Boolean.getBoolean("verbose");
}
