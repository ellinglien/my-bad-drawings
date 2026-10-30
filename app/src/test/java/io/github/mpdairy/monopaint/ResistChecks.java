package io.github.mpdairy.monopaint;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Resist (the new modules spec, a Medium choice): masking fluid and wax on the page's masking fluid. Codes; golden pages
 * against the web lab (scripts/resist_goldens.cjs); a wash and every other mark over it leaving the masked pixels as
 * they were; the eraser lifting the fluid first, then erasing as usual, wax and all; wax catching the paper's tooth
 * only, its sheen; the hatch only where it lifts; files; Undo; Clear page; quick strokes ending as live; Blend, Smudge,
 * Slow, gravity and Float over it; rolls from the seed; words and Brush Lab. {@code java ResistChecks pairs <folder>}
 * writes each golden beside the app's drawing.
 */
public final class ResistChecks {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (args.length > 1 && args[0].equals("pairs")) { pairs(new File(args[1])); return; }
        verbose = args.length > 0;
        codes(); goldens(); takes(); others(); eraser(); afterLift(); partly(); wax(); hatch(); files(); undo(); clear(); quick(); cores(); rolls(); words(); lab();
        System.out.println("PASS: Resist is Medium choice 5 with Erasable only (masking fluid _5_, wax _500_; old codes byte for byte), resolved as a crisp ink line on the page's masking fluid; masking fluid and wax draw the web lab's golden pages (hatched as the screen shows it, and under ink, at 16 greys); a wash, Quick, Slow with its settling, and running and dripping on a tilted page, leaves every fully masked pixel as it was, every layer byte for byte, and takes beside it; so do White, Behind and Invert, a smear, a blending stump and a comb (which pick up nothing from under it), Float's print and an eraser stroke over wax; the eraser lifts the fluid first and leaves the paint under it, the next pass erases as usual, and wax shields nothing from it: it takes wax and the paint under it (the owner, 2026-10-25); wax catches only the paper's high points, keeps a one-grey sheen on the ink layer that the eraser takes with it, and is never hatched; the hatch shows only what lifts; files keep the fluid as TSM4 and wax's shares in TSM5's MSHR; Undo takes a resist, wax with its sheen, and a lift back as one step; Clear page peels it all, Brush Lab's test page keeps it when laid out again; quick strokes end as drawn live and preview hatched; several cores lay it as one; Watercolor rolls it rarely from the seed, every other roll as before; the words and Brush Lab say it");
    }
    static boolean verbose;
    static void note(String s) { if (verbose) System.out.println(s); }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static final int W = BlendChecks.W, H = BlendChecks.H;
    /** The Light grey's darkness ({@link ModularSizes#darkness}). */
    static final double LIGHT = 1 - 120 / 255.0;
    /** The web lab's presets (newmods.js newModPresets), the Tools page's. */
    static final String FLUID = "masking-fluid.M60i000000000at_5_0_000_1jz_0000000000000000000nz0.px",
            WAX = "wax-resist.M60i00000cl00at_500_0_1i0_1jz_0000000000000000000nz0.py",
            WAX_SMOOTH = "wax-smooth.M60i000000000at_500_0_000_1jz_0000000000000000000nz0.py",
            INK = "plain-ink.M00i000000000at_000zz000000000000000_0_000_1jz_0000000000000000000nz0.1y9";
    /** The goldens (scripts/resist_goldens.cjs lists the same): name, resist code, its size and darkness, marks, the ink across's size (0: none), shown hatched. */
    static final Object[][] GOLDENS = {
            {"fluid-hatch", FLUID, 30.0, 1.0, "lines", 0.0, true},
            {"fluid-ink", FLUID, 30.0, 1.0, "blank", 90.0, false},
            {"fluid-light", FLUID, 40.0, LIGHT, "blank", 90.0, false},
            {"wax-hatch", WAX_SMOOTH, 30.0, 1.0, "lines", 0.0, true},
            {"wax-ink", WAX_SMOOTH, 40.0, 1.0, "blank", 90.0, false},
    };

    static ModularBrush brush(String code) { return BlendChecks.brush(code); }
    static int mark(String kind, int x, int y) { return kind.equals("blank") ? 255 : CarrySmudgeChecks.mark(x, y); }
    static int q(int tone) { return BlendChecks.q(tone); }
    /** What the screen shows at {@code x, y}: the hatch where the fluid lifts, else the page's tone. */
    static int shown(ToneDocument d, int x, int y) { int h = d.maskHatch(x, y); return h >= 0 ? h : d.compositeTone(x, y); }

    // ---- 1. codes ----

    static void codes() {
        for (String c : new String[]{FLUID, WAX}) {
            ModularBrush b = brush(c);
            check(b.choice(BrushPart.MEDIUM) == PartChoice.RESIST && ModularCode.encode(b).equals(c), "The lab's code reads as Resist and writes back: " + c);
        }
        check(brush(FLUID).part(BrushPart.MEDIUM).get(PartDial.ERASABLE) == 1 && brush(WAX).part(BrushPart.MEDIUM).get(PartDial.ERASABLE) == 0
                && FLUID.contains("_5_") && WAX.contains("_500_"), "Resist is Medium choice 5, its one dial Erasable written only when off (masking fluid _5_, wax _500_)");
        check(PartChoice.RESIST.dials.size() == 1 && PartChoice.RESIST.has(PartDial.ERASABLE) && !PartChoice.RESIST.has(PartDial.M_BLEND) && PartChoice.RESIST.erasable() == 1
                && PartChoice.RESIST.macro == PartDial.ERASABLE && PartChoice.RESIST.words[0].equals("wax") && PartChoice.RESIST.words[1].equals("fluid"), "Its one dial, Erasable, wax to fluid; no Blend");
        // A medium chosen starts as masking fluid; resolved, a crisp ink line at its Erasable, no smudge, never on water.
        ModularBrush ink = brush(INK);
        check(ModularBrush.switchTo(ink.part(BrushPart.MEDIUM), PartChoice.RESIST).get(PartDial.ERASABLE) == 1, "Resist chosen starts as masking fluid");
        ModularBrush onWater = BlendChecks.life(brush(FLUID), PartChoice.FLOAT);
        onWater.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.SMUDGE));
        ResolvedBrush r = ResolvedBrush.of(onWater, 30, 1);
        check(r.resist && r.life == ResolvedBrush.Life.WET && r.carry == null && r.flt == null && r.line && r.body == null && r.rule == ResolvedBrush.Rule.INK
                && r.blendMode == ResolvedBrush.BlendMode.OWN && r.erase == 1, "Resolved: a line of ink at its Erasable, without a smudge, on paper (Float lays as Quick)");
        check(ResolvedBrush.of(brush(WAX), 30, 1).erase == 0 && !ResolvedBrush.of(ink, 30, 1).resist, "wax's Erasable off; any other medium no resist");
    }

    // ---- 2. golden pages against the web lab ----

    /** The app's drawing of golden {@code g}: the marks on the wash layer, the resist along the wave, then the ink across. */
    static ToneDocument golden(Object[] g) {
        String kind = (String)g[4];
        ToneDocument d = BlendChecks.page(InkWashLayers.WASH, (x, y) -> mark(kind, x, y));
        BlendChecks.live(d, brush((String)g[1]), (Double)g[2], (Double)g[3], null, BlendChecks.wave());
        if ((Double)g[5] > 0) BlendChecks.live(d, brush(INK), (Double)g[5], 1, null, BlendChecks.across(40, 440, 180));
        return d;
    }
    static int appTone(ToneDocument d, Object[] g, int x, int y) { return (Boolean)g[6] ? shown(d, x, y) : d.compositeTone(x, y); }
    static BufferedImage lab(String name) throws Exception { return javax.imageio.ImageIO.read(new File("app/src/test/resources/resist/" + name + ".png")); }
    static void goldens() throws Exception {
        for (Object[] g : GOLDENS) {
            ToneDocument d = golden(g);
            BufferedImage lab = lab((String)g[0]);
            int off = 0, far = 0, changed = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int a = q(appTone(d, g, x, y)), b = q(lab.getRaster().getSample(x, y, 0)), m = q(mark((String)g[4], x, y));
                if (b != m) changed++;
                if (Math.abs(a - b) == 1) off++; else if (a != b) far++;
            }
            note(String.format(Locale.ROOT, "golden %-12s changed %6d, one grey off %5d (%.3f%%), further %d", g[0], changed, off, 100.0 * off / (W * H), far));
            check(changed > 500, g[0] + ": the lab's strokes change the page: " + changed);
            if (System.getProperty("allGoldens") == null) check(far <= 20 && off <= W * H / 1000, g[0] + " draws the lab's page at 16 greys: " + off + " pixels one grey off, " + far + " further");
        }
    }
    // ---- pages ----

    static final double[][] BAND = BlendChecks.across(60, 420, 180);
    /** A blank one-pair page with {@code code} (a resist) laid along {@link #BAND} at 60 px, Dark. */
    static ToneDocument resisted(String code) { ToneDocument d = InkWashLayers.blank(W, H); BlendChecks.live(d, brush(code), 60, 1, null, BAND); return d; }
    /** The pixels masking fluid or wax shields in full. */
    static boolean[] full(ToneDocument d) { boolean[] f = new boolean[W * H]; for (int i = 0; i < f.length; i++) f[i] = d.maskShield(i % W, i / W) == 255; return f; }
    static int count(boolean[] b) { int n = 0; for (boolean v : b) if (v) n++; return n; }
    /** The masking fluid's tones, coverages and shares (paper for none). */
    static byte[][] mask(ToneDocument d) {
        ToneDocument.Layer m = d.layerSnapshot().mask;
        if (m == null) return new byte[][]{ToneDocument.paper(W, H), new byte[W * H], ToneDocument.paper(W, H)};
        return new byte[][]{m.tones.clone(), m.alpha.clone(), m.share != null ? m.share.clone() : ToneDocument.paper(W, H)};
    }
    /** Every layer's tones, coverages and shares, then the masking fluid's. */
    static byte[][] state(ToneDocument d) {
        byte[][] layers = BlendChecks.all(d), m = mask(d), out = java.util.Arrays.copyOf(layers, layers.length + 3);
        System.arraycopy(m, 0, out, layers.length, 3);
        return out;
    }
    /** How many pixels of {@code at} differ in any of {@code a}'s and {@code b}'s planes (null: every pixel). */
    static int changed(byte[][] a, byte[][] b, boolean[] at) {
        int n = 0;
        for (int i = 0; i < W * H; i++) {
            if (at != null && !at[i]) continue;
            for (int k = 0; k < a.length; k++) if (a[k][i] != b[k][i]) { n++; break; }
        }
        return n;
    }
    /** How many pixels outside {@code at} the page shows darker than they were. */
    static int darkened(int[] before, ToneDocument d, boolean[] at) { int n = 0; for (int i = 0; i < W * H; i++) if (!at[i] && d.compositeTone(i % W, i / W) < before[i] - 8) n++; return n; }
    /** {@code b} along {@code path} at {@code size}, Dark, with a session (on a tilted page: {@code tilt}), dried. */
    static void dried(ToneDocument d, ModularBrush b, double size, double[][] path, PageTilt tilt) {
        WatercolorSession s = new WatercolorSession();
        if (tilt != null) s.tilt = () -> tilt;
        BlendChecks.live(d, b, size, 1, s, path);
        for (int i = 0; s.settling() && i < 5000; i++) s.tickDrying(d);
        s.settleNow(d);
    }
    static ModularBrush preset(String name) { return GravityChecks.preset(name); }
    /** A stroke across the band, from above it to below it. */
    static final double[][] DIAGONAL;
    static {
        DIAGONAL = new double[161][];
        for (int i = 0; i <= 160; i++) DIAGONAL[i] = new double[]{40 + 400 * i / 160.0, 90 + 180 * i / 160.0, .8, i * 4};
    }
    /** A wash over the band, broad and wet. */
    static final double[][] WASH_PATH = BlendChecks.across(40, 440, 180);

    // ---- 3. nothing takes on it ----

    /** A wash over masking fluid and over wax: Quick, Slow settling, running and dripping on a tilted page. */
    static void takes() {
        ModularBrush soft = preset("Soft wash"), slow = BlendChecks.life(preset("Soft wash"), PartChoice.DRYING), drips = preset("Drips");
        slow.part(BrushPart.LIFE).set(PartDial.SETTLE, .6);
        Object[][] washes = {{"Quick wash", soft, null}, {"Slow wash", slow, null}, {"dripping wash", drips, PageTilt.of(.3, .9)}};
        for (String code : new String[]{FLUID, WAX_SMOOTH}) for (Object[] w : washes) {
            ToneDocument d = resisted(code);
            boolean[] at = full(d);
            check(count(at) > 10000, code + ": the band is masked in full: " + count(at));
            byte[][] before = state(d);
            int[] shown = BlendChecks.shown(d);
            dried(d, (ModularBrush)w[1], 120, WASH_PATH, (PageTilt)w[2]);
            int under = changed(before, state(d), at), beside = darkened(shown, d, at);
            note(String.format(Locale.ROOT, "%-14s %-14s under %d, beside %d", code.substring(0, code.indexOf('.')), w[0], under, beside));
            check(under == 0, w[0] + " leaves every fully masked pixel as it was, every layer: " + under + " changed (" + code + ")");
            check(beside > 10000, w[0] + " takes beside it: " + beside);
        }
    }
    /** Every other mark over masking fluid: White, Behind, Invert, a smear, Float's print. */
    static void others() {
        ModularBrush white = BlendChecks.brush(BlendChecks.WHITE_GEL), behind = BlendChecks.brush(BlendChecks.WASH_BEHIND), invert = BlendChecks.brush(BlendChecks.INVERT_INK);
        Object[][] marks = {{"White ink", white, 30.0}, {"Wash behind", behind, 120.0}, {"Invert ink", invert, 60.0}, {"Sumi on water", preset("Sumi"), 60.0}};
        for (Object[] m : marks) {
            ToneDocument d = resisted(FLUID);
            // A dark ground under it all, on the wash layer, for White and Invert to show on.
            BlendChecks.lay(d, InkWashLayers.WASH, (x, y) -> x >= 20 && x < 460 ? 60 : 255, 1);
            boolean[] at = full(d);
            byte[][] before = state(d);
            dried(d, (ModularBrush)m[1], (Double)m[2], DIAGONAL, null);
            int under = changed(before, state(d), at), beside = changed(before, state(d), null) - under;
            note(String.format(Locale.ROOT, "%-14s under %d, beside %d", m[0], under, beside));
            check(under == 0 && beside > 2000, m[0] + " over masking fluid leaves every masked pixel as it was (" + under + " changed) and takes beside it (" + beside + ")");
        }
        // A resist on Float never floats: the bath floating there prints before it is laid.
        { ToneDocument d = InkWashLayers.blank(W, H); WatercolorSession s = new WatercolorSession();
          BlendChecks.live(d, preset("Sumi"), 60, 1, s, DIAGONAL);
          boolean floated = s.settling();
          s.settleBefore(d, BlendChecks.life(brush(FLUID), PartChoice.FLOAT));
          check(floated && !s.settling(), "Floating paint prints before a resist on Float is laid"); }
        // A smear, a blending stump and a comb (Smear's Soften) across a dark band under masking fluid move none of it and
        // draw nothing out from under it.
        ModularBrush smear = BlendChecks.brush(BlendChecks.CHALK);
        smear.set(BrushPart.MEDIUM, ModularBrush.switchTo(smear.part(BrushPart.MEDIUM), PartChoice.WASH));
        smear.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.SMUDGE));
        smear.part(BrushPart.CARRY).set(PartDial.LOAD, 0);
        Object[][] smudges = {{"A smear", smear}, {"Blend stump", preset("Blend stump")}, {"Wash comb", preset("Wash comb")}};
        for (Object[] m : smudges) for (boolean masked : new boolean[]{false, true}) {
            ToneDocument d = InkWashLayers.blank(W, H);
            // On the smudge's own layer, the one it moves.
            BlendChecks.lay(d, CarrySmudgeChecks.ownLayer((ModularBrush)m[1]), (x, y) -> x >= 200 && x < 260 ? 40 : 255, 1);
            if (masked) { d.beginFluid(1); int[] row = new int[W]; java.util.Arrays.fill(row, -1); for (int x = 190; x < 270; x++) row[x] = 0; for (int y = 0; y < H; y++) d.setTones(0, y, W, 1, row, null); d.finish(); }
            byte[][] before = state(d);
            int[] shown = BlendChecks.shown(d);
            BlendChecks.live(d, (ModularBrush)m[1], 60, 1, null, BlendChecks.across(150, 420, 180));
            long right = 0;
            for (int y = 0; y < H; y++) for (int x = 275; x < W; x++) right += shown[y * W + x] - d.compositeTone(x, y);
            int under = changed(before, state(d), full(d));
            note(m[0] + (masked ? " over fluid" : " bare") + ": darkness carried right " + right + ", masked changed " + under);
            check(masked ? right == 0 && under == 0 : right > 2000, masked ? m[0] + " over masking fluid picks up nothing from under it and changes none of it: " + right + ", " + under : m[0] + " bare drags the band: " + right);
        }
    }

    // ---- 4. the eraser lifts it first ----

    static void eraser() {
        // Erasable ink under masking fluid (laid after it): one pass lifts the fluid and leaves the ink, the next erases it.
        ModularBrush pencil = BlendChecks.erasable(brush(INK), 1);
        for (String code : new String[]{FLUID, WAX_SMOOTH}) {
            ToneDocument d = InkWashLayers.blank(W, H);
            BlendChecks.live(d, pencil, 40, 1, null, BlendChecks.across(40, 440, 180));
            BlendChecks.live(d, brush(code), 60, 1, null, BAND);
            boolean[] at = full(d);
            boolean wax = code.equals(WAX_SMOOTH);
            int[] inked = BlendChecks.shown(d);
            byte[][] before = state(d);
            boolean[] went = erase(d, 200, 280);
            int liftable = 0, kept = 0, inkStays = 0, hatched = 0;
            for (int y = 150; y < 210; y++) for (int x = 180; x < 300; x++) {
                if (!at[y * W + x] || !went[y * W + x]) continue;
                liftable += d.maskLiftable(x, y); kept += d.maskShield(x, y) == 255 ? 1 : 0;
                if (d.compositeTone(x, y) == inked[y * W + x]) inkStays++;
                if (d.maskHatch(x, y) >= 0) hatched++;
            }
            note(code.substring(0, code.indexOf('.')) + ": after one pass liftable " + liftable + ", still masked " + kept + ", ink kept " + inkStays + ", hatched " + hatched);
            if (!wax) check(liftable == 0 && kept == 0 && hatched == 0 && inkStays > 2000, "One eraser pass lifts the masking fluid where it went (no hatch left) and leaves the ink under it: " + inkStays);
            // Wax shields no paint from the eraser: it goes with the ink under it in one pass (the owner, 2026-10-25).
            else {
                int waxLeft = 0, inkLeft = 0, over = 0;
                for (int y = 160; y < 200; y++) for (int x = 180; x < 300; x++) if (at[y * W + x] && went[y * W + x]) { over++; if (d.maskShield(x, y) > 0) waxLeft++; if (d.compositeTone(x, y) < 255) inkLeft++; }
                check(over > 1000 && waxLeft == 0 && inkLeft == 0, "One eraser pass takes wax and the ink under it: " + waxLeft + " waxed, " + inkLeft + " inked of " + over);
            }
            boolean[] again = erase(d, 200, 280);
            int gone = 0, both = 0;
            for (int y = 160; y < 200; y++) for (int x = 180; x < 300; x++) if (at[y * W + x] && went[y * W + x] && again[y * W + x]) { both++; if (d.compositeTone(x, y) > 250) gone++; }
            check(both > 1000 && gone == both, "the next pass leaves paper: " + gone + " of " + both);
            // Outside the fluid the first pass erased the ink as any eraser does.
            check(d.compositeTone(230, 140) > 250, "beside it the eraser erases as usual");
        }
    }
    /** A finger eraser across x0..x1 over the band, waving up and down; returns the pixels it went over. */
    static boolean[] erase(ToneDocument d, int x0, int x1) {
        EraseBothStroke e = new EraseBothStroke(d, 70f);
        for (int i = 0; i <= 40; i++) e.sample(x0 + (x1 - x0) * i / 40f, 180 + (float)(Math.sin(i / 3.0) * 20), 1, 0, 0, i * 5L);
        e.finish();
        byte[] over = new byte[W * H];
        e.erased(0, 0, W, H, over);
        boolean[] out = new boolean[W * H];
        for (int i = 0; i < out.length; i++) out[i] = over[i] != 0;
        return out;
    }

    /** Lifted, the paper takes paint again where the fluid was. */
    static void afterLift() {
        ToneDocument d = resisted(FLUID);
        boolean[] at = full(d), went = erase(d, 60, 420);
        int[] before = BlendChecks.shown(d);
        dried(d, preset("Soft wash"), 120, WASH_PATH, null);
        int n = 0, takes = 0;
        for (int i = 0; i < W * H; i++) if (at[i] && went[i]) { n++; if (d.compositeTone(i % W, i / W) < before[i] - 8) takes++; }
        check(n > 5000 && takes == n, "a wash after the lift takes where the fluid was: " + takes + " of " + n);
    }
    /** A resist part erasable (Erasable .5): hatched for the half that lifts; the eraser lifts that half, the rest stays and keeps half a wash out. */
    static void partly() {
        ModularBrush half = BlendChecks.erasable(brush(FLUID), .5);
        ToneDocument d = InkWashLayers.blank(W, H);
        BlendChecks.live(d, half, 60, 1, null, BAND);
        boolean[] at = full(d);
        int hatched = 0, lift = 0, n = 0;
        for (int i = 0; i < W * H; i++) if (at[i]) { n++; lift = Math.max(lift, d.maskLiftable(i % W, i / W)); }
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (d.maskHatch(x, y) >= 0) hatched++;
        check(n > 10000 && Math.abs(lift - 128) <= 2 && hatched > 2000, "Part erasable: half of it lifts (" + lift + " of 255), hatched (" + hatched + " px)");
        boolean[] went = erase(d, 60, 420);
        int kept = 255, hatchedAfter = 0;
        for (int i = 0; i < W * H; i++) if (at[i] && went[i]) { kept = Math.min(kept, d.maskShield(i % W, i / W)); if (d.maskHatch(i % W, i / W) >= 0) hatchedAfter++; }
        check(Math.abs(kept - 127) <= 2 && hatchedAfter == 0, "lifted, the half that stays still shields (" + kept + "), unhatched");
        int[] before = BlendChecks.shown(d);
        BlendChecks.live(d, brush(INK), 90, 1, null, BlendChecks.across(40, 440, 180));
        int x = 240, y = 180;
        check(at[y * W + x] && went[y * W + x] && Math.abs(d.compositeTone(x, y) - 255 * (1 - ModularCompose.WAX_SHEEN / 2) / 2) <= 3 && d.compositeTone(240, 140) < 5, "and keeps out half of the ink over it (over its half sheen): " + d.compositeTone(x, y));
    }

    // ---- 5. wax: the paper's tooth, its sheen ----

    static void wax() {
        // On cold press: the share of the band wax shields against masking fluid's, and where: only the paper's high points.
        ModularBrush wax = brush(WAX), fluid = brush(WAX);
        fluid.part(BrushPart.MEDIUM).set(PartDial.ERASABLE, 1);
        ToneDocument dw = InkWashLayers.blank(W, H), df = InkWashLayers.blank(W, H);
        BlendChecks.live(dw, wax, 60, 1, null, BAND); BlendChecks.live(df, fluid, 60, 1, null, BAND);
        StrokePaper paper = new StrokePaper(BrushTexture.values()[PartChoice.COLD_PRESS.index()]);
        int fl = 0, wx = 0, low = 0, lowShielded = 0, highShielded = 0;
        for (int y = 165; y < 195; y++) for (int x = 80; x < 400; x++) {
            if (df.maskShield(x, y) >= 128) fl++;
            if (dw.maskShield(x, y) >= 128) wx++;
            double h = paper.fine(x, y) * .65 + paper.mid(x, y) * .35;
            if (h < .4) { low++; if (dw.maskShield(x, y) > 0) lowShielded++; }
            if (dw.maskShield(x, y) >= 128 && h < .5 - 1e-6) highShielded++;
        }
        note("cold press: fluid shields " + fl + ", wax " + wx + "; low pixels " + low + ", wax there " + lowShielded + ", wax below the middle " + highShielded);
        check(fl > 30 * 320 * .9 && wx < fl * .8 && wx > fl * .2, "On cold press masking fluid fills the grain, wax catches part: " + wx + " of " + fl);
        check(low > 500 && lowShielded == 0 && highShielded == 0, "and wax only the paper's high points: none in its " + low + " low pixels");
        // Its sheen: a grey step at most on the ink layer, which the eraser leaves; masking fluid lays none.
        ToneDocument d = resisted(WAX_SMOOTH), f = resisted(FLUID);
        int ink = InkWashLayers.inkOf(d, InkWashLayers.current(d)), darkest = 255, fluidDark = 255;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { darkest = Math.min(darkest, d.compositeTone(x, y)); fluidDark = Math.min(fluidDark, f.compositeTone(x, y)); }
        check(darkest >= 255 - 18 && darkest < 255 && fluidDark == 255, "Wax lays a faint sheen, one grey step (" + darkest + "); masking fluid nothing (" + fluidDark + ")");
        check(BlendChecks.all(f)[3 * ink + 1][180 * W + 200] == 0 && BlendChecks.all(d)[3 * ink + 1][180 * W + 200] != 0, "the sheen is on the pair's ink layer");
        int[] sheen = BlendChecks.shown(d);
        boolean[] went = erase(d, 60, 420);
        int kept = 0, waxLeft = 0, over = 0;
        for (int i = 0; i < W * H; i++) if (went[i] && sheen[i] < 255) { over++; if (d.compositeTone(i % W, i / W) < 255) kept++; if (d.maskShield(i % W, i / W) > 0) waxLeft++; }
        check(over > 10000 && kept == 0 && waxLeft == 0, "and the eraser takes it, with the wax (the owner, 2026-10-25): " + kept + " sheen, " + waxLeft + " waxed px left of " + over);
    }

    // ---- 6. the hatch: only what lifts ----

    static void hatch() {
        ToneDocument f = resisted(FLUID), w = resisted(WAX_SMOOTH);
        int hf = 0, hw = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { if (f.maskHatch(x, y) >= 0) hf++; if (w.maskHatch(x, y) >= 0) hw++; }
        check(hf > 3000 && hw == 0, "Masking fluid shows hatched (" + hf + " px), wax never (" + hw + ")");
        // Masking fluid over wax on cold press: hatched where it fills the grain the wax left; erased, the hatch goes and
        // the wax under it stays for the next pass, which takes it. (Over wax that shields in full, fluid adds nothing: it stays wax, unhatched.)
        ToneDocument c = resisted(WAX);
        boolean[] waxedBefore = full(c);
        BlendChecks.live(c, brush(FLUID), 30, 1, null, BlendChecks.across(100, 380, 180));
        int over = 0; for (int x = 120; x < 360; x++) for (int y = 168; y < 192; y++) if (c.maskHatch(x, y) >= 0) over++;
        check(over > 300, "fluid over wax on cold press is hatched in the grain: " + over);
        boolean[] went = erase(c, 60, 420);
        int left = 0, waxed = 0, waxed0 = 0, lifts = 0;
        for (int y = 160; y < 200; y++) for (int x = 120; x < 360; x++) {
            if (!went[y * W + x]) continue;
            if (c.maskHatch(x, y) >= 0) left++;
            if (waxedBefore[y * W + x]) { waxed0++; if (c.maskShield(x, y) == 255) waxed++; }
            lifts = Math.max(lifts, c.maskLiftable(x, y));
        }
        // What lifts by a step or less (the lab's .004) is left as it is.
        check(left == 0 && lifts <= 1 && waxed0 > 500, "lifted, the hatch goes: " + left + " hatched, " + lifts + " most to lift");
        boolean[] again = erase(c, 60, 420);
        int still = 0;
        for (int y = 160; y < 200; y++) for (int x = 120; x < 360; x++) if (went[y * W + x] && again[y * W + x] && c.maskShield(x, y) > 0) still++;
        check(still == 0, "and the next pass takes the wax: " + still + " px still waxed");
        // The screen's render draws it; exports and the drawing's tones never hold it.
        int[] px = new int[W * H];
        f.render(px, 0, 0, W, H);
        int black = 0; for (int y = 160; y < 200; y++) for (int x = 100; x < 380; x++) if (px[y * W + x] == 0xff000000) black++;
        int[] out = f.exportPixels(false);
        int marked = 0; for (int p : out) if ((p & 255) != 255) marked++;
        check(black > 1000 && marked == 0 && f.compositeTone(200, 180) == 255, "the screen shows the hatch (" + black + " px), the drawing and its export never (" + marked + ")");
    }

    // ---- 7. files ----

    static void files() throws Exception {
        ToneDocument fluid = resisted(FLUID), wax = resisted(WAX_SMOOTH);
        byte[] f = write(fluid), w = write(wax);
        check(magic(f).equals("TSM4") && magic(w).equals("TSM5"), "Masking fluid saves as TSM4, as before; wax, which keeps a share, as TSM5 (" + magic(f) + ", " + magic(w) + ")");
        for (Object[] c : new Object[][]{{fluid, f}, {wax, w}}) {
            ToneDocument back = DocumentCodec.read(new java.io.ByteArrayInputStream((byte[])c[1]));
            check(changed(state((ToneDocument)c[0]), state(back), null) == 0, "A saved resist reads back byte for byte, its shares too");
        }
        // Read back, the eraser lifts the fluid first, then takes the wax.
        ToneDocument both = resisted(WAX_SMOOTH);
        BlendChecks.live(both, brush(FLUID), 30, 1, null, BlendChecks.across(100, 380, 180));
        ToneDocument back = DocumentCodec.read(new java.io.ByteArrayInputStream(write(both)));
        int waxed = 0, lifts = 0;
        for (int y = 160; y < 200; y++) for (int x = 120; x < 360; x++) if (back.maskShield(x, y) == 255) waxed++;
        boolean[] went = erase(back, 60, 420);
        for (int y = 160; y < 200; y++) for (int x = 120; x < 360; x++) if (went[y * W + x]) lifts += back.maskLiftable(x, y);
        boolean[] again = erase(back, 60, 420);
        int left = 0;
        for (int y = 160; y < 200; y++) for (int x = 120; x < 360; x++) if (went[y * W + x] && again[y * W + x] && back.maskShield(x, y) > 0) left++;
        check(waxed > 5000 && lifts == 0 && left == 0, "and read back (" + waxed + " px waxed), the eraser lifts the fluid (" + lifts + " left to lift) and the wax by the next pass (" + left + " left)");
        // A page with sections for its layers and for wax.
        ToneDocument mixed = InkWashLayers.blank(W, H);
        BlendChecks.live(mixed, brush(INK), 20, 1, null, BlendChecks.across(40, 440, 60));
        BlendChecks.live(mixed, brush(WAX_SMOOTH), 60, 1, null, BAND);
        ToneDocument mb = DocumentCodec.read(new java.io.ByteArrayInputStream(write(mixed)));
        check(changed(state(mixed), state(mb), null) == 0, "a page with permanent ink and wax round-trips both");
    }
    static byte[] write(ToneDocument d) throws Exception { java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream(); DocumentCodec.write(o, d.layerSnapshot()); return o.toByteArray(); }
    static String magic(byte[] b) { return new String(b, 0, 4, java.nio.charset.StandardCharsets.US_ASCII); }

    // ---- 8. Undo; 9. Clear page ----

    static void undo() {
        for (String code : new String[]{FLUID, WAX_SMOOTH}) {
            // Erasable ink crossing the band, so the eraser changes the page beside it too.
            ToneDocument d = InkWashLayers.blank(W, H);
            BlendChecks.live(d, BlendChecks.erasable(brush(INK), 1), 20, 1, null, DIAGONAL);
            byte[][] before = state(d);
            BlendChecks.live(d, brush(code), 60, 1, null, BAND);
            byte[][] laid = state(d);
            check(d.undo() && changed(before, state(d), null) == 0, code + ": one Undo takes the resist back" + (code.equals(WAX_SMOOTH) ? ", wax with its sheen" : ""));
            check(d.redo() && changed(laid, state(d), null) == 0, "and Redo lays it again");
            erase(d, 60, 420);
            check(changed(laid, state(d), null) > 50, "the eraser changes the page");
            boolean undone = d.undo();
            int back = changed(laid, state(d), null);
            check(undone && back == 0, code + ": Undo takes an eraser stroke back, the fluid it lifted with the paint: " + back + " pixels differ");
        }
    }
    static void clear() {
        ToneDocument d = resisted(FLUID);
        BlendChecks.live(d, brush(WAX_SMOOTH), 60, 1, null, BlendChecks.across(60, 420, 80));
        byte[][] laid = state(d);
        check(d.clearAllLayers() && !d.hasMaskLayer() && !d.hasMask(), "Clear page peels every resist, fluid and wax");
        check(d.undo() && changed(laid, state(d), null) == 0, "and Undo puts them back");
        check(changed(laid, state(InkWashLayers.resized(d, W, H)), null) == 0, "Brush Lab's test page keeps them when it is laid out again");
    }

    // ---- 10. quick then render; 11. cores ----

    static void quick() {
        for (String code : new String[]{FLUID, WAX, WAX_SMOOTH}) for (double size : new double[]{60, 160}) {
            ToneDocument live = InkWashLayers.blank(W, H), quick = InkWashLayers.blank(W, H);
            BlendChecks.live(live, brush(code), size, 1, null, BlendChecks.wave());
            QuickStroke q = QuickStroke.quickly(quick, brush(code), size, 1, null);
            double[][] path = BlendChecks.wave();
            int hatched = 0;
            for (int i = 0; i < path.length; i++) {
                q.sample((float)path[i][0], (float)path[i][1], ToolSettings.pressure((float)path[i][2]), 0, 0, (long)path[i][3]);
                if (i % 3 == 2) q.present();
            }
            q.present();
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (quick.maskHatch(x, y) >= 0) hatched++;
            q.finish();
            int differ = changed(state(live), state(quick), null);
            check(differ == 0, code + " at " + size + " px drawn quick ends as drawn live: " + differ + " pixels differ");
            check(code.equals(FLUID) ? hatched > 500 : hatched == 0, code + " at " + size + " px previews hatched under the pen as masking fluid (" + hatched + " px), wax not");
        }
        // A live Slow resist's lift on a thread of its own ends as one lifted on the pen's.
        for (String code : new String[]{FLUID, WAX}) {
            byte[][][] got = new byte[2][][];
            for (int k = 0; k < 2; k++) {
                ToneDocument d = InkWashLayers.blank(W, H);
                ModularStroke s = ModularStroke.start(d, BlendChecks.life(brush(code), PartChoice.DRYING), 60, 1, new WatercolorSession());
                double[][] path = BlendChecks.wave();
                for (double[] p : path) s.sample((float)p[0], (float)p[1], ToolSettings.pressure((float)p[2]), 0, 0, (long)p[3]);
                s.present();
                if (k == 1) check(s.lift(), "a Slow resist lifts off the pen's thread");
                s.finish();
                got[k] = state(d);
            }
            check(changed(got[0], got[1], null) == 0 && count(full(InkWashLayers.blank(W, H))) == 0, code + " in Slow lifted on its own thread lays as on the pen's");
        }
    }
    static void cores() {
        for (String code : new String[]{FLUID, WAX}) {
            String[] crc = new String[2];
            for (int i = 0; i < 2; i++) {
                boolean was = Cores.shared;
                Cores.shared = i == 0;
                try { ToneDocument d = InkWashLayers.blank(W, H); BlendChecks.live(d, brush(code), 300, 1, null, BlendChecks.wave()); java.util.zip.CRC32 c = new java.util.zip.CRC32(); for (byte[] p : state(d)) c.update(p); crc[i] = Long.toHexString(c.getValue()); }
                finally { Cores.shared = was; }
            }
            check(crc[0].equals(crc[1]), code + " at 300 px lays the same on several cores as on one");
        }
    }

    // ---- 12. rolls; 13. words; 14. Brush Lab ----

    static void rolls() {
        int resists = 0, fluid = 0, other = 0, n = 0;
        for (ModularStyle s : ModularStyle.all()) for (int i = 0; i < 900; i++) {
            ModularBrush with = s.roll(new java.util.Random(9000L * (s.index + 1) + i)), without;
            ModularStyle.resistRolls = false;
            try { without = s.roll(new java.util.Random(9000L * (s.index + 1) + i)); } finally { ModularStyle.resistRolls = true; }
            n++;
            if (with.choice(BrushPart.MEDIUM) != PartChoice.RESIST) { if (!ModularCode.encode(with).equals(ModularCode.encode(without))) other++; continue; }
            check(s.name.equals("Watercolor"), "Only Watercolor rolls Resist: " + s.name);
            resists++;
            if (with.part(BrushPart.MEDIUM).get(PartDial.ERASABLE) == 1) fluid++;
            // Everything but the medium as the roll without it.
            for (BrushPart p : BrushPart.values()) if (p != BrushPart.MEDIUM) check(with.part(p).sameAs(without.part(p)), "a roll that becomes Resist keeps every other part: " + p);
        }
        note("rolls: " + resists + " Resist of 900 Watercolor, " + fluid + " masking fluid");
        check(other == 0, "every other roll is as before: " + other + " of " + n);
        check(resists >= 900 / 60 / 3 && resists <= 900 / 60 * 3 && fluid >= resists / 2 && fluid < resists, "Watercolor rolls Resist about one in 60 (" + resists + " of 900), masking fluid about seven in ten (" + fluid + ")");
        ModularBrush plain = ModularStyle.named("Watercolor").roll(new java.util.Random(11));
        for (int i = 0; i < 50; i++) for (ModularVariations.Made m : ModularVariations.make(plain, new ModularLocks(), new java.util.Random(i)))
            check(m.brush.choice(BrushPart.MEDIUM) != PartChoice.RESIST, "a variation never swaps to Resist");
    }
    /** The web lab's words (newmods.js choiceWords, plainWords, captionItems) for its presets and a stippled wax. */
    static final String[][] LAB_WORDS = {
            {FLUID, "Masking fluid", "Masking fluid. Nothing takes on it until rubbed off.", "Watercolour, resist"},
            {WAX, "Wax resist", "Wax resist on cold-press paper. Nothing takes on it. Dry-brush texture.", "Watercolour, wax"},
            {"stipple-wax.M77g7eewei0_500_0_200_0_8la0000000000000000nz0.t4mf73", "Wax resist", "Wax resist on ribbed paper. Nothing takes on it. Dries at once. Pressure widens.", "Ink wash, wax, stipple, ribbed, instant"},
    };
    static void words() {
        for (String[] w : LAB_WORDS) {
            ModularBrush b = brush(w[0]);
            check(ModularWords.choiceWords(b, BrushPart.MEDIUM).equals(w[1]), "Medium's card says \"" + w[1] + "\": " + ModularWords.choiceWords(b, BrushPart.MEDIUM));
            check(ModularWords.plainWords(b).equals(w[2]), "This brush says \"" + w[2] + "\": " + ModularWords.plainWords(b));
            check(String.join(", ", ModularWords.caption(b)).equals(w[3]), "the caption says \"" + w[3] + "\": " + ModularWords.caption(b));
        }
        check(ModularWords.swapNote(brush(WAX), BrushPart.MEDIUM).equals("Wax resist") && PartChoice.RESIST.label.equals("Resist"), "the swap tile's note, the chip's name");
    }
    static void lab() {
        java.util.Map<String, String> store = new java.util.HashMap<>();
        BrushLibrary.Store st = new BrushLibrary.Store() { public String get(String k) { return store.get(k); } public void put(String k, String v) { if (v == null) store.remove(k); else store.put(k, v); } };
        BrushLab lab = new BrushLab(new ToolSlots(st, new java.util.Random(1)), 2, new java.util.Random(7));
        lab.put(Brush.of(brush(INK)));
        check(lab.chips(BrushPart.MEDIUM).contains(PartChoice.RESIST), "Brush Lab offers Resist among the Medium chips");
        lab.choose(BrushPart.MEDIUM, PartChoice.RESIST);
        check(lab.dials(BrushPart.MEDIUM).equals(java.util.Collections.singletonList(PartDial.ERASABLE)), "its one dial, Erasable");
        // Its samples show by what refuses to take: a wash over it, the fluid hatched; wax speckled white in it.
        for (String code : new String[]{FLUID, WAX}) {
            int[] card = WildSample.part(brush(code), BrushPart.MEDIUM, 200, 120), loop = WildSample.modular(brush(code), 1, WildSample.Shape.LOOP, 120, 80, false);
            int white = 0, grey = 0;
            for (int p : card) { if ((p & 255) == 255) white++; else grey++; }
            check(white > 1000 && grey > 10000, code + "'s Medium card wave shows white in the wash over it: " + white + " white, " + grey + " not");
            int lw = 0; for (int p : loop) if ((p & 255) == 255) lw++;
            check(lw > 200, code + "'s loop too: " + lw);
        }
    }

    /** Each golden beside the app's drawing (lab left, app right, the difference below), 16 greys, into {@code out}. */
    static void pairs(File out) throws Exception {
        out.mkdirs();
        for (Object[] g : GOLDENS) {
            ToneDocument d = golden(g);
            BufferedImage lab = lab((String)g[0]), img = new BufferedImage(2 * W + 8, 2 * H + 8, BufferedImage.TYPE_BYTE_GRAY);
            for (int y = 0; y < 2 * H + 8; y++) for (int x = 0; x < 2 * W + 8; x++) img.getRaster().setSample(x, y, 0, 128);
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int a = q(lab.getRaster().getSample(x, y, 0)), b = q(appTone(d, g, x, y));
                img.getRaster().setSample(x, y, 0, a * 17); img.getRaster().setSample(W + 8 + x, y, 0, b * 17);
                img.getRaster().setSample(x, H + 8 + y, 0, a == b ? 255 : Math.abs(a - b) == 1 ? 160 : 0);
            }
            javax.imageio.ImageIO.write(img, "png", new File(out, "golden-" + g[0] + ".png"));
        }
        System.out.println("wrote " + out);
    }
}
