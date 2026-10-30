package io.github.mpdairy.monopaint;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Halo (the new modules spec, a Drying choice): veins that reach from a wet stroke to nearby marks and bridge them,
 * then set. Codes; the web lab's golden pages (scripts/halo_goldens.cjs): the lab's halos grown from the lab's pages
 * tone for tone, and the app's own strokes' halos against them.
 */
public final class HaloChecks {
    public static void main(String[] args) throws Exception {
        verbose = args.length > 0;
        codes();
        // The lab's pages are grown by the lab's arithmetic: two moves a tick, every black pixel its own.
        HaloGrowth.lab = true;
        try { port(); goldens(); } finally { HaloGrowth.lab = false; }
        black(); sets(); joins(); undo(); eraser(); fluid(); worker(); cores(); quick(); files(); limits(); timing(); rolls(); words(); presets();
        System.out.println("PASS: Halo, shown as Mold, is Drying choice 4 (_4, Drying time 6.6 s, Reach, Growth; old codes byte for byte), laid as Slow lays it with Reach within three tip widths and at most 20 s; it grows the web lab's veins from the lab's pages tone for tone, and from the app's own strokes the lab's golden pages; any other stroke, the eraser, Undo, a layer change and the wet indicator set it where it is; a Halo stroke on its layer joins it and feeds it; Undo takes a stroke back with its veins as one step; Erasable and masking fluid hold for the veins; the session's worker, any number of cores and a quick stroke grow the same veins; files keep them; its building and ticks fit their budgets; rolls reach it rarely from the seed, every other roll as before, never a variation; its words are the lab's; Mold ink is the lab's Halo ink under its display name, its Growth full and Drying 5.5 s, and its samples grow at once; the app's scouts move four times a tick and a black mark nearby feeds them, so Mold bridges to it darker and sooner");
    }
    static boolean verbose;
    static void note(String s) { if (verbose) System.out.println(s); }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static final int W = 480, H = 360;
    static final File GOLD = new File("app/src/test/resources/halo");
    static int[] png(String name) throws Exception {
        BufferedImage img = javax.imageio.ImageIO.read(new File(GOLD, name + ".png"));
        int[] t = new int[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) t[y * W + x] = img.getRaster().getSample(x, y, 0);
        return t;
    }
    /** halo.json, read plainly: each case's strokes' numbers by name. */
    static final class Stroke { String code; double size, shade, dark, growth; int[] rect; int seed, reach, ticks, band, scouts; }
    static java.util.Map<String, List<Stroke>> info() throws Exception {
        String json = new String(java.nio.file.Files.readAllBytes(new File(GOLD, "halo.json").toPath()), "UTF-8");
        java.util.Map<String, List<Stroke>> out = new java.util.LinkedHashMap<>();
        java.util.regex.Matcher cases = java.util.regex.Pattern.compile("\"(\\w+)\": \\[(.*?)\\n \\]", java.util.regex.Pattern.DOTALL).matcher(json);
        while (cases.find()) {
            List<Stroke> list = new ArrayList<>();
            for (String obj : cases.group(2).split("\\n  \\},?")) {
                if (!obj.contains("\"code\"")) continue;
                Stroke s = new Stroke();
                s.code = str(obj, "code"); s.size = num(obj, "size"); s.shade = num(obj, "shade"); s.dark = num(obj, "dark"); s.growth = num(obj, "growth");
                s.seed = (int) (long) num(obj, "seed"); s.reach = (int) num(obj, "reach"); s.ticks = (int) num(obj, "ticks"); s.band = (int) num(obj, "band"); s.scouts = (int) num(obj, "scouts");
                s.rect = ints(obj, "rect");
                list.add(s);
            }
            out.put(cases.group(1), list);
        }
        return out;
    }
    private static String str(String o, String k) { java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + k + "\": \"([^\"]*)\"").matcher(o); m.find(); return m.group(1); }
    private static double num(String o, String k) { java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + k + "\": ([-0-9.e]+)").matcher(o); m.find(); return Double.parseDouble(m.group(1)); }
    private static int[] ints(String o, String k) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + k + "\": \\[([^\\]]*)\\]").matcher(o); m.find();
        String[] p = m.group(1).trim().split("\\s*,\\s*");
        int[] r = new int[p.length]; for (int i = 0; i < p.length; i++) r[i] = Integer.parseInt(p[i].trim()); return r;
    }

    /** A page of whole tones (the lab's, rounded) before and after a stroke, as {@link HaloGrowth.Page}. */
    static HaloGrowth.Page page(int[] before, int[] after) {
        int cols = (W + ToneDocument.TILE - 1) / ToneDocument.TILE, T = ToneDocument.TILE;
        return new HaloGrowth.Page() {
            @Override public int width() { return W; }
            @Override public int height() { return H; }
            @Override public boolean changed(int key) {
                int kx = key % cols * T, ky = key / cols * T;
                for (int y = ky; y < Math.min(H, ky + T); y++) for (int x = kx; x < Math.min(W, kx + T); x++) if (before[y * W + x] != after[y * W + x]) return true;
                return false;
            }
            @Override public void composites(int key, byte[] b, byte[] a) {
                int kx = key % cols * T, ky = key / cols * T;
                for (int y = ky; y < Math.min(H, ky + T); y++) for (int x = kx; x < Math.min(W, kx + T); x++) {
                    a[(y - ky) * T + x - kx] = (byte) after[y * W + x];
                    if (b != null) b[(y - ky) * T + x - kx] = (byte) before[y * W + x];
                }
            }
        };
    }
    /** One opaque layer holding {@code tones}. */
    static ToneDocument opaque(int[] tones) { byte[] b = new byte[W * H]; for (int i = 0; i < b.length; i++) b[i] = (byte) tones[i]; return new ToneDocument(W, H, b); }
    static int[] tones(ToneDocument d) { int[] t = new int[W * H]; for (int i = 0; i < t.length; i++) t[i] = d.compositeTone(i % W, i / W); return t; }
    /** Writes {@code tones} where they differ from the page, one gesture (a stroke the lab laid). */
    static void paint(ToneDocument d, int[] tones) {
        d.begin();
        int[] row = new int[W];
        for (int y = 0; y < H; y++) { for (int x = 0; x < W; x++) row[x] = d.tone(x, y) == tones[y * W + x] ? -1 : tones[y * W + x]; d.setTones(0, y, W, 1, row); }
        d.finish();
    }
    static ResolvedBrush.Halo halo(Stroke s) { return new ResolvedBrush.Halo(s.reach, s.growth, s.ticks, s.dark, false); }

    // ---- 1. the port: the lab's halo from the lab's pages ----

    /**
     * Each golden case's strokes as the lab laid them (its pages before and after each, rounded), their halos grown here
     * to the end, every halo a tick at a time together as the lab's test area grows them: the lab's page tone for tone
     * (a pixel a tone off where the lab's float page rounds the other way after another stroke wrote over a vein).
     */
    static void port() throws Exception {
        for (java.util.Map.Entry<String, List<Stroke>> c : info().entrySet()) {
            String name = c.getKey();
            List<Stroke> strokes = c.getValue();
            ToneDocument d = opaque(png(name + "-0-after"));
            List<HaloGrowth> growing = new ArrayList<>();
            for (int k = 0; k < strokes.size(); k++) {
                Stroke s = strokes.get(k);
                int[] before = k == 0 ? png(name + "-0-before") : tones(d), after = png(name + "-" + k + "-after");
                if (k > 0) {
                    int[] lab = png(name + "-" + k + "-before");
                    int off = differ(before, lab, 1), far = differ(before, lab, 2);
                    note(String.format(Locale.ROOT, "port %-7s before stroke %d: %d px a tone off the lab's, %d further", name, k, off, far));
                    check(far == 0 && off < 40, name + ": the page before stroke " + k + " is the lab's: " + off + " a tone off, " + far + " further");
                    paint(d, after);
                }
                HaloGrowth g = HaloGrowth.grow(page(before, after), d, 0, s.rect, halo(s), s.seed);
                check(g != null, name + " grows a halo");
                check(g.band == s.band && g.scouts() == s.scouts, name + " stroke " + k + ": the lab's band and scouts (" + g.band + " px, " + g.scouts() + " vs " + s.band + ", " + s.scouts + ")");
                for (HaloGrowth o : growing) o.feedFrom(g);
                growing.add(g);
                // The halos grow together until the next stroke (the lab's ticks between them).
                int grow = k + 1 < strokes.size() ? ticksBefore(name, k) : 0;
                for (int t = 0; t < grow; t++) tick(d, growing);
            }
            while (!growing.isEmpty()) tick(d, growing);
            int[] lab = png(name + "-lab"), app = tones(d);
            int off = differ(app, lab, 1), far = differ(app, lab, 2), veins = differ(lab, png(name + "-" + (strokes.size() - 1) + "-after"), 1);
            note(String.format(Locale.ROOT, "port %-7s %6d px of veins; %d a tone off the lab's, %d further", name, veins, off, far));
            check(veins > 200, name + ": the lab's halo grows veins: " + veins);
            check(far == 0 && off <= 40, name + ": the lab's halo, tone for tone: " + off + " px a tone off, " + far + " further");
        }
    }
    // ---- 2. golden pages: the app's own strokes ----

    /** The marks' tone at {@code x, y} (halo_goldens.cjs mark): black bars; lines, a grey band and hatching; paper. */
    static int mark(String kind, int x, int y) {
        if (kind.equals("bars")) return (y >= 148 && y < 152) || (y >= 236 && y < 240 && x > 60 && x < 420) ? 0 : 255;
        if (kind.equals("page")) {
            if ((x >= 100 && x < 104) || (x >= 340 && x < 344)) return 0;
            if (x >= 200 && x < 280 && y >= 40 && y < 320) return 150;
            if (x >= 380 && x < 460 && ((x + y / 2) % 10 + 10) % 10 < 2) return 90;
        }
        return 255;
    }
    static final String[] KINDS = {"bridge", "bars", "thin", "bars", "wave", "page", "wash", "bars", "pair", "blank"};
    static String kind(String name) { for (int i = 0; i < KINDS.length; i += 2) if (KINDS[i].equals(name)) return KINDS[i + 1]; throw new IllegalArgumentException(name); }
    /** Case {@code name}'s stroke {@code k}'s samples (halo_goldens.cjs): x, y, pressure, ms. */
    static double[][] path(String name, int k) {
        if (name.equals("wave")) return CarrySmudgeChecks.wave();
        double y = name.equals("bridge") ? 120 : name.equals("thin") ? 138 : name.equals("wash") ? 100 : k == 0 ? 150 : 190;
        double[][] o = new double[121][];
        for (int i = 0; i <= 120; i++) o[i] = new double[]{30 + 420 * i / 120.0, y, .8, i * 5};
        return o;
    }
    /** The app's drawing of case {@code name}: its marks on the wash layer, each stroke live with a session, its halos grown to the end. */
    static ToneDocument drawn(String name, List<Stroke> strokes) {
        ToneDocument d = BlendChecks.page(InkWashLayers.WASH, (x, y) -> mark(kind(name), x, y));
        WatercolorSession s = new WatercolorSession();
        for (int k = 0; k < strokes.size(); k++) {
            Stroke st = strokes.get(k);
            s.settleBefore(d, BlendChecks.brush(st.code));
            CarrySmudgeChecks.live(d, BlendChecks.brush(st.code), st.size, st.shade, s, path(name, k));
            for (int t = 0; t < ticksBefore(name, k) && s.settling(); t++) s.tickDrying(d);
        }
        for (int i = 0; s.settling() && i < 1000; i++) s.tickDrying(d);
        return d;
    }
    /**
     * Each golden case drawn by the app (its own strokes on its own layers, the halos grown by a session): the lab's page
     * at 16 greys, near enough (a stroke drawn by the app is the lab's to a tone or so, and its halo grows from it).
     */
    static void goldens() throws Exception {
        for (java.util.Map.Entry<String, List<Stroke>> c : info().entrySet()) {
            String name = c.getKey();
            ToneDocument d = drawn(name, c.getValue());
            int[] lab = png(name + "-lab"), app = tones(d), after = png(name + "-" + (c.getValue().size() - 1) + "-after");
            int off = 0, far = 0, veinsLab = 0, veinsApp = 0;
            for (int i = 0; i < lab.length; i++) {
                int a = BlendChecks.q(app[i]), b = BlendChecks.q(lab[i]);
                if (Math.abs(a - b) == 1) off++; else if (a != b) far++;
                if (lab[i] < after[i] - 8) veinsLab++;
                if (app[i] < after[i] - 8) veinsApp++;
            }
            note(String.format(Locale.ROOT, "golden %-7s veins lab %5d app %5d; at 16 greys %d px one grey off, %d further", name, veinsLab, veinsApp, off, far));
            // The app draws these strokes as the lab does to the tone; the wave over the grey band and the wash it draws a tone or so off.
            goldensCheck(name, off, far, veinsLab, veinsApp, !name.equals("wave") && !name.equals("wash"));
        }
    }
    static void goldensCheck(String name, int off, int far, int veinsLab, int veinsApp, boolean same) {
        if (same) check(off == 0 && far == 0, name + ": the app's own strokes grow the lab's page: " + off + " px a grey off, " + far + " further");
        else check(veinsApp > veinsLab / 3 && veinsApp < veinsLab * 3, name + ": the app's own stroke (a tone or so from the lab's) grows about as many veins: " + veinsApp + " vs the lab's " + veinsLab);
    }

    // ---- 3. what sets it, what joins it ----

    static final String INK = "halo-steady.M70i077at0000at_000zz000000000000000_0_000_4jsjsll_0000000000000000000nz0.wx",
            PRESET = "halo-ink.M70i077at0000at_000zz0000000000000_0_000_4jsjsll_25ess00000000000000nz0.wx",
            WASH = "halo-wash.M60i000000000at_1ulat00770000ee_0_000_4jsi0i0_0000000000000000000nz0.11kx",
            PLAIN_INK = "plain-ink.M00i000000000at_000zz000000000000000_0_000_1jz_0000000000000000000nz0.1y9";
    /** Mold ink (ToolPresets): the lab's Halo ink with Growth at full and Drying 5.5 s (the owner, 2026-10-22: "mold is very faint / light and slow"). */
    static final String MOLD = "mold-ink.M70i077at0000at_000zz0000000000000_0_000_4hsjszz_25ess00000000000000nz0.wx";
    static ModularBrush brush(String code) { return BlendChecks.brush(code); }
    /**
     * A black mark beside a Halo stroke (the bars' lower bar, black before and after): the lab counted it the stroke's
     * own, so its veins only ever reached .2 of the stroke's darkness. The app feeds on it and bridges: darker veins,
     * and sooner (four moves a tick).
     */
    static void black() {
        int[] lab = blackVeins(true), app = blackVeins(false);
        note("black bar: darkest vein " + lab[0] + " (lab) " + app[0] + " (app); 3 greys darker after 2 s: " + lab[1] + " " + app[1] + " px");
        check(lab[0] >= 190 && app[0] <= 110, "veins bridge to a black mark: darkest " + app[0] + ", the lab's " + lab[0]);
        check(app[1] > 100 && app[1] > 4 * lab[1], "and show sooner: " + app[1] + " px 3 greys darker at 2 s, the lab's " + lab[1]);
    }
    /** {@link #black}'s darkest tone off the marks at the end, and how many pixels 3 greys darker than paper after 20 ticks. */
    static int[] blackVeins(boolean lab) {
        HaloGrowth.lab = lab;
        try {
            ToneDocument d = bars();
            int[] marks = tones(d);
            WatercolorSession s = new WatercolorSession();
            draw(d, s, INK, 10, line(214));
            int[] laid = tones(d);
            grow(d, s, 20);
            int[] early = tones(d);
            grow(d, s, 1000);
            int[] end = tones(d);
            int darkest = 255, soon = 0;
            for (int i = 0; i < end.length; i++) {
                if (laid[i] < 250 || marks[i] < 250) continue;
                darkest = Math.min(darkest, end[i]);
                if (early[i] <= 255 - 51) soon++;
            }
            return new int[]{darkest, soon};
        } finally { HaloGrowth.lab = false; }
    }
    /** A bars page (the goldens') on one pair. */
    static ToneDocument bars() { return BlendChecks.page(InkWashLayers.WASH, (x, y) -> mark("bars", x, y)); }
    static double[][] line(double y) { double[][] o = new double[121][]; for (int i = 0; i <= 120; i++) o[i] = new double[]{30 + 420 * i / 120.0, y, .8, i * 5}; return o; }
    /** {@code code} along {@code path} at {@code size}, Dark, with {@code s}, as the drawing screen starts it (the session settled first). */
    static void draw(ToneDocument d, WatercolorSession s, String code, double size, double[][] path) {
        s.settleBefore(d, code == null ? null : brush(code));
        if (code == null) { CarrySmudgeChecks.feed(new EraseBothStroke(d, (float) size), path); return; }
        CarrySmudgeChecks.live(d, brush(code), size, 1, s, path);
    }
    static void grow(ToneDocument d, WatercolorSession s, int ticks) { for (int t = 0; t < ticks && s.settling(); t++) s.tickDrying(d); }
    static void codes() {
        ModularBrush b = brush(PRESET);
        check(b.choice(BrushPart.LIFE) == PartChoice.HALO && ModularCode.encode(b).equals(PRESET) && PRESET.contains("_4jsjsll_"), "The lab's Halo ink reads as Halo and writes back: Drying choice 4, then its three dials");
        check(PartChoice.HALO.key.equals("halo") && PartChoice.HALO.label.equals("Mold") && PartChoice.HALO.index() == 4 && PartChoice.HALO.macro == PartDial.H_REACH
                && PartChoice.HALO.words[0].equals("short") && PartChoice.HALO.words[1].equals("long"), "Its key is the codes' halo, shown as Mold; its slider Mold Length, short to long");
        ModularBrush slow = BlendChecks.life(brush(PLAIN_INK), PartChoice.DRYING);
        slow.part(BrushPart.LIFE).set(PartDial.DRYING, .7);
        check(ModularBrush.switchTo(slow.part(BrushPart.LIFE), PartChoice.HALO).get(PartDial.DRYING) == .7 && new ModularBrush.Part(PartChoice.HALO).get(PartDial.DRYING) == .55
                && PartDial.DRYING.shown(.55).equals("6.6 s"), "Halo keeps a Slow drying time across a swap; on its own 6.6 s");
        // Resolved: Slow's lift with no settling, nothing that runs, its growth beside it.
        ResolvedBrush r = ResolvedBrush.of(b, 12, 1);
        check(r.life == ResolvedBrush.Life.DRY && r.settle == 0 && r.grav == null && !r.settlesAfterLift && r.halo != null && r.halo.ticks == 66 && r.halo.dark == 1,
                "Resolved: laid as Slow lays it, nothing settling or running after, its veins 66 ticks at the line's darkness");
        check(ResolvedBrush.of(BlendChecks.blend(b, 1), 12, 1).halo == null && ResolvedBrush.of(BlendChecks.blend(b, 3), 12, 1).halo == null && ResolvedBrush.of(BlendChecks.blend(b, 2), 12, 1).halo.behind,
                "White and Invert grow no veins (veins only darken); Behind's only where the page was blank");
        // A Halo stroke lays as the same brush in Slow with Settle 0 lays it, layer for layer.
        ToneDocument a = bars(), c = bars();
        CarrySmudgeChecks.live(a, ResolvedBrush.asSlow(b), 12, 1, null, line(120));
        WatercolorSession s = new WatercolorSession();
        CarrySmudgeChecks.live(c, b, 12, 1, s, line(120));
        check(BlendChecks.same(BlendChecks.all(a), BlendChecks.all(c)) && s.halos().size() == 1, "A Halo stroke lays as Slow lays it, then its halo grows");
    }
    /** Any stroke that isn't Halo, the eraser, a layer change and the page put away set the halos where they are; a slot change doesn't (no stroke). */
    static void sets() {
        for (String what : new String[]{"ink", "eraser", "layer", "away"}) {
            ToneDocument d = bars();
            WatercolorSession s = new WatercolorSession();
            draw(d, s, INK, 12, line(120));
            grow(d, s, 5);
            HaloGrowth h = s.halos().get(0);
            byte[][] at5 = BlendChecks.all(d);
            if (what.equals("ink")) draw(d, s, PLAIN_INK, 6, line(300));
            else if (what.equals("eraser")) draw(d, s, null, 20, line(330));
            else if (what.equals("layer")) { s.settleNow(d); s.dry("layer changed"); }
            else s.settleNow(d);
            check(!s.haloing() && h.age() == 5 && h.done(), what + " sets the halo where it is, after its 5 ticks: " + h.age());
            if (!what.equals("ink") && !what.equals("eraser")) check(BlendChecks.same(at5, BlendChecks.all(d)), what + " leaves the page as the halo left it");
            grow(d, s, 70);
            check(!s.settling(), "nothing grows after: " + what);
        }
    }
    /** A Halo stroke on the halos' layer joins them (the first grows on); one on the other layer of the pair sets them. */
    static void joins() {
        ToneDocument d = bars();
        WatercolorSession s = new WatercolorSession();
        draw(d, s, INK, 12, line(120));
        grow(d, s, 10);
        HaloGrowth first = s.halos().get(0);
        draw(d, s, INK, 12, line(200));
        check(s.halos().size() == 2 && s.halos().get(0) == first && first.age() == 10, "A second Halo ink stroke joins the first, which grows on");
        grow(d, s, 100);
        check(!s.settling() && first.done(), "both grow to the end");
        ToneDocument e = bars();
        WatercolorSession t = new WatercolorSession();
        draw(e, t, INK, 12, line(120));
        grow(e, t, 3);
        first = t.halos().get(0);
        draw(e, t, WASH, 40, line(220));
        check(t.halos().size() == 1 && t.halos().get(0) != first && first.done() && first.age() == 3, "A Halo wash, on the pair's wash layer, sets the ink's halos and grows its own");
    }
    /** Undo takes back the last stroke with the veins grown since, one step, and sets the halos. */
    static void undo() {
        ToneDocument d = bars();
        byte[][] blank = BlendChecks.all(d);
        WatercolorSession s = new WatercolorSession();
        draw(d, s, INK, 12, line(120));
        byte[][] laid = BlendChecks.all(d);
        grow(d, s, 30);
        check(!BlendChecks.same(laid, BlendChecks.all(d)), "the veins have grown");
        check(s.undo(d) && !s.haloing() && BlendChecks.same(blank, BlendChecks.all(d)), "Undo takes the stroke back with its veins, one step, and sets the halo");
        check(d.redo() && !BlendChecks.same(laid, BlendChecks.all(d)), "Redo brings both back");
    }
    /** The Erasable rule takes veins as it takes their stroke: all of an erasable one, none of a permanent one. */
    static void eraser() {
        for (double e : new double[]{1, 0}) {
            ToneDocument d = InkWashLayers.blank(W, H);
            BlendChecks.lay(d, InkWashLayers.WASH, (x, y) -> mark("bars", x, y), 0);
            WatercolorSession s = new WatercolorSession();
            ModularBrush b = BlendChecks.erasable(brush(INK), e);
            s.settleBefore(d, b);
            CarrySmudgeChecks.live(d, b, 12, 1, s, line(120));
            grow(d, s, 100);
            int[] grown = tones(d);
            s.settleBefore(d, null);
            // The Erasable rule (a chosen kind where another kind's permanent paint meets it): the Eraser on Everything takes all (2026-10-22).
            ErasableChecks.eraseAll(d);
            int kept = 0, veins = 0;
            for (int y = 60; y < 145; y++) for (int x = 0; x < W; x++) { int i = y * W + x; if (grown[i] < 247 && (y < 110 || y > 130)) { veins++; if (d.compositeTone(x, y) < 247) kept++; } }
            note(String.format(Locale.ROOT, "eraser: Erasable %.0f, %d of %d vein px kept", e, kept, veins));
            check(veins > 100 && (e == 1 ? kept < veins / 20 : kept > veins * 9 / 10), "Erasable " + e + ": the eraser " + (e == 1 ? "takes the veins" : "leaves the veins") + " (" + kept + " of " + veins + " kept)");
        }
    }
    /** Masking fluid shields the veins as any write: a fully masked pixel stays as it was. */
    static void fluid() {
        ToneDocument d = bars();
        WatercolorSession s = new WatercolorSession();
        CarrySmudgeChecks.live(d, brush(ResistChecks.FLUID), 40, 1, null, BlendChecks.across(30, 450, 95));
        boolean[] masked = ResistChecks.full(d);
        int[] before = tones(d);
        draw(d, s, INK, 12, line(120));
        int[] laid = tones(d);
        grow(d, s, 100);
        int moved = 0, open = 0;
        for (int i = 0; i < W * H; i++) { if (masked[i] && d.compositeTone(i % W, i / W) != before[i]) moved++; if (!masked[i] && d.compositeTone(i % W, i / W) < laid[i] - 8) open++; }
        check(ResistChecks.count(masked) > 2000 && moved == 0 && open > 100, "masking fluid shields the veins: " + moved + " fully masked px changed, " + open + " veins beside it");
    }
    /** The drawing screen's way: built and stepped on the session's worker, written here; the same veins, a joining stroke never waits for a build. */
    static void worker() throws Exception {
        ToneDocument here = bars(), there = bars();
        WatercolorSession a = new WatercolorSession(), b = new WatercolorSession();
        b.worker = new SettleWorker();
        draw(here, a, INK, 12, line(120));
        grow(here, a, 1000);
        draw(there, b, INK, 12, line(120));
        int n = 0;
        for (long wait; (wait = b.tick(there, () -> { })) >= 0 && n < 100000; n++) Thread.sleep(Math.min(wait, 1));
        check(BlendChecks.same(BlendChecks.all(here), BlendChecks.all(there)), "the worker grows the same veins as growing them here");
        // A second Halo stroke straight after the lift: it joins while the first is still being built, and both grow.
        ToneDocument d = bars();
        WatercolorSession s = new WatercolorSession();
        s.worker = new SettleWorker();
        draw(d, s, INK, 12, line(120));
        draw(d, s, INK, 12, line(200));
        draw(d, s, INK, 12, line(280));
        int built = 0;
        for (long wait; (wait = s.tick(d, () -> { })) >= 0 && n < 200000; n++) { built = Math.max(built, s.halos().size()); Thread.sleep(Math.min(wait, 1)); }
        check(built == 3 && !s.settling(), "three Halo strokes in a row, each joining before the last was built: " + built + " grow, to the end");
        // A different brush while a halo is still being built sets it at once.
        draw(d, s, INK, 12, line(60));
        draw(d, s, PLAIN_INK, 6, line(330));
        check(!s.haloing(), "another brush sets a halo still being built");
    }
    /** Shared out over the cores or not, the same veins. */
    static void cores() {
        ToneDocument one = BlendChecks.page(InkWashLayers.WASH, (x, y) -> mark("page", x, y)), four = BlendChecks.page(InkWashLayers.WASH, (x, y) -> mark("page", x, y));
        WatercolorSession a = new WatercolorSession(), b = new WatercolorSession();
        Cores.shared = false;
        try { draw(one, a, WASH, 40, CarrySmudgeChecks.wave()); grow(one, a, 1000); } finally { Cores.shared = true; }
        draw(four, b, WASH, 40, CarrySmudgeChecks.wave()); grow(four, b, 1000);
        check(BlendChecks.same(BlendChecks.all(one), BlendChecks.all(four)), "one core and four grow the same veins");
    }
    /** A quick stroke (quick then rendered) ends as the stroke drawn live, then grows the same veins. */
    static void quick() {
        ToneDocument live = bars(), quick = bars();
        WatercolorSession a = new WatercolorSession(), b = new WatercolorSession();
        draw(live, a, WASH, 40, line(100));
        b.settleBefore(quick, brush(WASH));
        BlendChecks.quick(quick, brush(WASH), 40, 1, b, line(100));
        check(BlendChecks.same(BlendChecks.all(live), BlendChecks.all(quick)) && b.halos().size() == 1, "a quick stroke ends as the live one, its halo built");
        grow(live, a, 1000); grow(quick, b, 1000);
        check(BlendChecks.same(BlendChecks.all(live), BlendChecks.all(quick)), "and grows the same veins");
    }
    /** Veins are the layer's paint: a file keeps them. */
    static void files() throws Exception {
        ToneDocument d = bars();
        WatercolorSession s = new WatercolorSession();
        draw(d, s, INK, 12, line(120));
        grow(d, s, 1000);
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        DocumentCodec.write(o, d.layerSnapshot());
        ToneDocument back = DocumentCodec.read(new java.io.ByteArrayInputStream(o.toByteArray()));
        check(BlendChecks.same(BlendChecks.all(d), BlendChecks.all(back)), "a saved drawing keeps the veins");
    }
    /** The owner's limits: Reach within three tip widths (the lab's where it is less), at most 20 s. */
    static void limits() {
        ModularBrush b = brush(INK);
        b.part(BrushPart.LIFE).set(PartDial.H_REACH, 1);
        b.part(BrushPart.LIFE).set(PartDial.DRYING, 1);
        for (double size : new double[]{2, 6, 12, 40, 80, 200}) {
            ResolvedBrush r = ResolvedBrush.of(b, size, 1);
            double width = r.line ? r.inkSize : r.bodySize;
            long lab = Math.round(80 * StrokeMath.clamp(Math.sqrt(width / 6), 1, 3));
            check(r.halo.reach == Math.min(lab, Math.round(3 * width)) && r.halo.ticks == 200, size + " px: Reach " + r.halo.reach + " (the lab's " + lab + ", three widths " + Math.round(3 * width) + "), 200 ticks (20 s)");
        }
    }
    /** Building and a tick fit their budgets on the desktop (the Manta about 20 times slower): a page-long Halo ink wave and a broad wash. */
    static void timing() {
        for (Object[] c : new Object[][]{{PRESET, 20.0}, {WASH, 120.0}}) {
            ToneDocument d = InkWashLayers.blank(1920, 2560);
            WatercolorSession s = new WatercolorSession();
            double[][] wave = new double[351][];
            for (int i = 0; i <= 350; i++) { double t = i / 350.0; wave[i] = new double[]{260 + 1400 * t, 1280 + 250 * Math.sin(2 * Math.PI * t), .2 + .7 * Math.sin(Math.PI * t), i * 4}; }
            draw(d, s, (String) c[0], (Double) c[1], wave);
            HaloGrowth g = s.halos().get(0);
            long t0 = System.nanoTime(); int n = 0;
            while (s.settling()) { s.tickDrying(d); n++; }
            double tick = (System.nanoTime() - t0) / 1e6 / Math.max(1, n);
            note(String.format(Locale.ROOT, "timing %s %.0f px: build %.1f ms (read %.1f), band %d px, %d ticks %.2f ms each, %.1f MB", ((String) c[0]).substring(0, 9), (Double) c[1], g.buildNanos / 1e6, g.readNanos / 1e6, g.band, n, tick, g.bytes() / 1e6));
            check(g.band <= HaloGrowth.MOST_BAND && g.bytes() < 40_000_000L, "band and memory: " + g.band + " px, " + g.bytes() / 1e6 + " MB");
            Timing.limit(g.buildNanos < 400_000_000L && tick < 20, "builds in " + g.buildNanos / 1e6 + " ms, ticks " + tick + " ms, " + g.bytes() / 1e6 + " MB");
        }
    }
    /** Rolls: Anything goes and Ink wash about one in 30, Fineliner one in 50; every other part as before; never from Float; never a variation. */
    static void rolls() {
        int halos = 0, other = 0, n = 0;
        for (ModularStyle s : ModularStyle.all()) for (int i = 0; i < 600; i++) {
            ModularBrush with = s.roll(new Random(9100L * (s.index + 1) + i)), without;
            ModularStyle.haloRolls = false;
            try { without = s.roll(new Random(9100L * (s.index + 1) + i)); } finally { ModularStyle.haloRolls = true; }
            n++;
            if (with.choice(BrushPart.LIFE) != PartChoice.HALO) { if (!ModularCode.encode(with).equals(ModularCode.encode(without))) other++; continue; }
            check(s.haloOdds() > 0 && without.choice(BrushPart.LIFE) != PartChoice.FLOAT, s.name + " rolls Halo only with odds, never from Float");
            halos++;
            for (BrushPart p : BrushPart.values()) if (p != BrushPart.LIFE) check(with.part(p).sameAs(without.part(p)), "a roll that becomes Halo keeps every other part: " + p);
        }
        note("rolls: " + halos + " Halo of " + n);
        check(other == 0 && halos >= 600 * (2.0 / 30 + 1.0 / 50) / 3 && halos <= 600 * (2.0 / 30 + 1.0 / 50) * 3, "Halo rolls rarely (" + halos + " of " + n + "), every other roll as before (" + other + " differ)");
        ModularBrush plain = ModularStyle.named("Ink wash").roll(new Random(12));
        for (int i = 0; i < 50; i++) for (ModularVariations.Made m : ModularVariations.make(plain, new ModularLocks(), new Random(i)))
            check(m.brush.choice(BrushPart.LIFE) != PartChoice.HALO, "a variation never swaps to Halo");
    }
    /**
     * The lab's words (halo_goldens.cjs words.tsv), for rolled brushes of every style made Halo: the Drying card and the
     * swap tile note as the lab's; the caption's "mold" where the lab puts it, the rest as the brush in Quick says it;
     * "This brush" the lab's where the app says the brush in Quick as the lab does (the app words a bristle tip and an
     * Anything goes round tip its own way), else the brush in Quick's with Mold's sentence before Control's.
     */
    static void words() throws Exception {
        int n = 0, same = 0;
        for (String row : java.nio.file.Files.readAllLines(new File(GOLD, "words.tsv").toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
            if (row.isEmpty()) continue;
            String[] w = row.split("\t");
            ModularBrush b = brush(w[0]), quick = BlendChecks.life(b, PartChoice.FAST_WET);
            check(ModularCode.encode(b).equals(w[0]), "reads and writes back: " + w[0]);
            check(ModularWords.choiceWords(b, BrushPart.LIFE).equals(w[1]), "Drying's card says \"" + w[1] + "\": " + ModularWords.choiceWords(b, BrushPart.LIFE));
            check(ModularWords.swapNote(b, BrushPart.LIFE).equals(w[4]), "swap note " + w[4] + ": " + ModularWords.swapNote(b, BrushPart.LIFE));
            List<String> cap = new ArrayList<>(ModularWords.caption(b)), lab = java.util.Arrays.asList(w[2].split("\\|"));
            check(cap.indexOf("mold") == lab.indexOf("mold") && cap.remove("mold") && cap.equals(ModularWords.caption(quick)), w[0] + ": caption " + w[2] + ", the app's " + ModularWords.caption(b));
            String app = ModularWords.plainWords(b), mold = "Veins bridge nearby marks for " + w[1].substring(5, w[1].length() - 2) + " s.";
            if (ModularWords.plainWords(quick).equals(w[6])) { check(app.equals(w[3]), w[0] + ": This brush \"" + w[3] + "\", not \"" + app + "\""); same++; }
            else check(app.contains(mold) && (app.endsWith(mold) || !ModularWords.handWords(b, 2).equals("steady")), w[0] + ": This brush says \"" + mold + "\" before Control's words: " + app);
            n++;
        }
        note("words: " + n + " brushes, " + same + " said exactly as the lab says them");
        check(n > 100 && same > n / 2, "the lab's words for " + n + " brushes (" + same + " exactly)");
    }

    /** Mold ink (the Tools page's) is the lab's Halo ink but for its name; a sample's halo grows to the end at once. */
    static void presets() {
        ToolPresets.Preset p = null;
        for (ToolPresets.Preset q : ToolPresets.all()) if (q.name.equals("Mold ink")) p = q;
        check(p != null && p.style.name.equals("Ink wash") && ModularCode.settings(brush(p.code)).equals(ModularCode.settings(brush(MOLD))), "Mold ink: the lab's Halo ink, its Growth full and Drying 5.5 s");
        ModularBrush lab = brush(PRESET), mold = brush(MOLD);
        check(mold.part(BrushPart.LIFE).get(PartDial.GROWTH, 0) == 1 && PartDial.DRYING.shown(mold.part(BrushPart.LIFE).get(PartDial.DRYING, 0)).equals("5.5 s")
                && PartDial.DRYING.shown(lab.part(BrushPart.LIFE).get(PartDial.DRYING, 0)).equals("6.6 s"), "Growth full, Drying 5.5 s (the lab's 6.6 s)");
        long t0 = System.nanoTime();
        int[] tile = WildSample.modular(brush(p.code), 1, WildSample.Shape.LOOP, 240, 120, false);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        int dark = 0;
        for (int v : tile) if ((v & 255) < 200) dark++;
        note("sample: Mold ink's loop, 240 x 120, " + ms + " ms");
        check(dark > 100, "Mold ink's sample draws");
        Timing.limit(ms < 2000, "Mold ink's sample draws, its halo grown at once (" + ms + " ms)");
    }

    /** The ticks case {@code name}'s halos grow before its stroke {@code k + 1} (halo_goldens.cjs). */
    static int ticksBefore(String name, int k) { return name.equals("pair") ? 20 : 0; }
    /** One tick of every halo in {@code growing} together, written; the grown ones dropped. */
    static void tick(ToneDocument d, List<HaloGrowth> growing) {
        HaloGrowth.Grown[] g = new HaloGrowth.Grown[growing.size()];
        for (int i = 0; i < g.length; i++) g[i] = growing.get(i).step();
        HaloGrowth.apply(d, growing, g);
        growing.removeIf(HaloGrowth::done);
    }
    /** How many pixels of {@code a} and {@code b} differ by {@code by} tones or more. */
    static int differ(int[] a, int[] b, int by) { int n = 0; for (int i = 0; i < a.length; i++) if (Math.abs(a[i] - b[i]) >= by) n++; return n; }
}
