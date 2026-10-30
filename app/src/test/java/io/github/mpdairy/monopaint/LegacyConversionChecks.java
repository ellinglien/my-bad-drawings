package io.github.mpdairy.monopaint;

import java.util.zip.CRC32;

/**
 * Brushes from before the six-part engine convert the same way every time (LegacyBrushes; plan
 * docs/archive/superpowers/plans/2026-10-15-one-engine.md): the five share codes BrushCodeChecks pins and the four built-ins
 * become fixed M codes, and those draw fixed pages. Replaces LegacyGoldenChecks, which pinned the old engines' pixels
 * until they were removed: the conversion's fidelity to them was measured on the desktop before (the I codes within 0.1
 * of a tone on average, the P codes and built-ins as the plan says). The M codes are the contract; the checksums are the
 * six-part engine's, recorded when the old engines went, and change only when that engine changes on purpose (record
 * anew with any argument, and say why in the commit). Re-recorded once, for the Manta speed work: the paints' bodies
 * lay a dab a spacing apart at every size, and the two P codes at Medium (120 px reach) are worked out on the lattice
 * (ModularStroke.latticePx, 100 px); the pens drew the same. Re-recorded again when the sample loop was turned to stand
 * upright (x and y swapped), and again when its tail after the crossing was halved and it ends firm: only each
 * brush's loop sample changed, its pages drew the same. Re-recorded again when paints started the lattice at 50 px
 * (2026-10-19, the audit's finding 16): the built-in Wash's and Watercolor's two Medium pages (reach 50 to 100 px) are
 * on it now; every other page and sample drew the same. Re-recorded again when every sample was pressed no firmer than
 * a usual .6 (WildSample.TYPICAL_PRESS, 2026-10-22): only each brush's loop sample changed, its pages drew the same.
 */
public final class LegacyConversionChecks {
    static final String[][] CODES = {
            {"dapper-fig.I610gavij3f7q16o0000006m00.e1", "dapper-fig.Md1gavii0j3f70000at_000zz6m00000000000_0_000_200000js_8q10000000000mn0000nz0.e1"},
            {"bobson-dugnutt.I610zkyum2fbzsge00ej5k00e7.1", "bobson-dugnutt.Md1zkyui0m2fb005kat_000zz00e7000000000_0_0ej_200000js_8zs0000000000370000nz0.1"},
            {"x.I613zkyum2fbzsge00ej5k00e7.1", "x.Md1zkyui0m2fb005kat_000zz00e7000000000_0_3ej_200000js_8zs0000000000370000nz0.1"},
            {"mellow-plum.P600xjf400c4jzxh00aqhu.hd", "mellow-plum.Md0i000000000f4_1zzjzc4xhaq00_0_000_28e000js_8xj00003m0000000000nz0.hd"},
            {"bouncy-moose.P610hh46000000000000wp.25", "bouncy-moose.Md0i00000000046_1000000000000_0_000_2fd000js_8hh00003m0000000000nz0.25"}};
    // One page re-pinned 2026-10-29 (75350864): a line past 16 px laid on a closer grid (LineStamper, EdgeRippleChecks).
    static final String EXPECTED = "28ce86fa bbe782e4 113989ec bf1e81e9 f9996c07 ca977a34 1f6245ea 230eb1a3 4ad541f8 87306636 b7a5ed87 3b953b7d 70dac630 40af05d 364ef8de cd3eca39 42bfe783 f0a5e74c 2bab9ffc a8f0042b ed3774be 6dce5018 2220b680 99529471 9ef2ecea cbdfe6b6 b7954af8 9eff9cf9 452060ee 5e919e97 4e785449 ce7bb434 a3d3b325 805196ec 1510b504 91b65910 8cbdecbc a601c5f 591481db 59a0c977 acfeeaab";

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        codes();
        StringBuilder sums = new StringBuilder();
        for (String[] c : CODES) sums.append(pages(LegacyBrushes.read("brush\t" + c[0]))).append(' ');
        for (String[] b : LegacyBrushes.BUILTINS) sums.append(pages(LegacyBrushes.read("@" + b[0]))).append(' ');
        String got = sums.toString().trim();
        if (args.length > 0) { System.out.println(got); return; }
        check(got.equals(EXPECTED), "Converted brushes draw the pages they drew: " + got + " vs " + EXPECTED);
        System.out.println("PASS: the five old share codes and the four built-ins convert to fixed six-part brushes (names, sizes and greys kept) and draw fixed pages and samples");
    }

    static void codes() {
        for (String[] c : CODES) {
            ModularBrush m = LegacyBrushes.of(BrushCode.decode(c[0]));
            check(ModularCode.encode(m).equals(c[1]), c[0] + " converts to " + c[1] + ", not " + ModularCode.encode(m));
            LegacyBrushes.Old old = LegacyBrushes.read("Old name\t" + c[0]);
            check(old != null && old.brush.name.equals("Old name") && old.builtin == null, "a stored " + c[0] + " keeps its name");
            check(ModularCode.encode(old.brush).equals(c[1].replaceFirst("^[a-z-]+", "old-name")), "and converts as its code does");
            check(old.pen == c[0].contains(".I"), c[0] + " was a " + (old.pen ? "pen" : "paint"));
            check(Brush.of(m).inks() == old.pen, "a converted pen still draws on the ink layer, a paint on the wash layer");
        }
        String[] names = {"Fineliner", "Brush pen", "Ink wash", "Watercolour"}, styles = {"Fineliner", "Brush pen", "Ink wash", "Watercolor"};
        for (int i = 0; i < LegacyBrushes.BUILTINS.length; i++) {
            String[] b = LegacyBrushes.BUILTINS[i];
            LegacyBrushes.Old old = LegacyBrushes.read("@" + b[0]);
            check(old != null && old.builtin.equals(b[0]) && old.brush.name.equals(names[i]) && old.brush.style.name.equals(styles[i]), "@" + b[0] + " reads as " + names[i]);
            check(ModularCode.encode(old.brush).equals(b[2]), "@" + b[0] + " converts to its fixed code");
            check(old.pen == (i < 2) && Brush.of(old.brush).inks() == (i < 2), "Ink and Fude draw on the ink layer, Wash and Watercolor on the wash layer");
        }
        check(LegacyBrushes.read("@ERASER") == null && LegacyBrushes.read("@") == null, "an unknown built-in reads as nothing");
        check(LegacyBrushes.read("x\tnot-a-code") == null && LegacyBrushes.read("no tab") == null && LegacyBrushes.read(null) == null, "a line that isn't an old brush reads as nothing");
        check(LegacyBrushes.read("x\tbrave-onion.M24p6okati000ee00_0atzz9000000020000_0_277_1e0_6ee00ee0000we000000nz0.1u5hvr4") == null, "an M line isn't an old brush");
        // Sizes and greys: what each drew.
        LegacyBrushes.Old ink = LegacyBrushes.read("@INK"), wash = LegacyBrushes.read("@WASH"), water = LegacyBrushes.read("@WATERCOLOR");
        check(ink.px(InkWashSizes.Preset.SMALL) == 3 && ink.px(InkWashSizes.Preset.LARGE) == 16 && wash.px(InkWashSizes.Preset.MEDIUM) == 120 && water.px(InkWashSizes.Preset.LARGE) == 220, "old sizes in px");
        check(Math.abs(ink.darkness(InkWashShades.Shade.LIGHT) - (1 - 120 / 255.0)) < 1e-9 && water.darkness(InkWashShades.Shade.LIGHT) == InkWashShades.WATERCOLOR_PALE, "old greys as darkness");
        check(Math.abs(wash.darkness(InkWashShades.Shade.DARK) - 1.1 * (50 / 255.0) / .34) < 1e-9, "the old Wash's flat grey through the glaze");
        check(Math.abs(LegacyBrushes.drying(1) - Math.sqrt(4250 / 19250.0)) < 1e-12
                && PartDial.wetMs(LegacyBrushes.drying(.6)) == Math.round(750 + 4250 * .36), "an old paint's drying time keeps its seconds");
    }

    /**
     * One brush's pages: S, M and L (pens) or S and M (paints) in Dark, M in Light, and its loop sample. Each at the
     * darkness its slot draws at: the old one's, kept as it was then, a paint's (a wash) through {@link
     * ModularSizes#sinceBlack} since washes reach black ({@link ToolSwatches#look}), so it draws the pages it drew.
     */
    static String pages(LegacyBrushes.Old old) {
        StringBuilder out = new StringBuilder();
        for (InkWashSizes.Preset p : InkWashSizes.Preset.values()) {
            // No old brush had the fourth size (2026-10-19), so no old page drew at it.
            if (!old.pen && p == InkWashSizes.Preset.LARGE || p == InkWashSizes.Preset.LARGER) continue;
            out.append(draw(old.brush, old.px(p), ModularSizes.sinceBlack(old.brush, old.darkness(InkWashShades.Shade.DARK)))).append(' ');
        }
        out.append(draw(old.brush, old.px(InkWashSizes.Preset.MEDIUM), ModularSizes.sinceBlack(old.brush, old.darkness(InkWashShades.Shade.LIGHT)))).append(' ');
        int[] pixels = WildSample.modular(old.brush, ModularSizes.sampleDarkness(old.brush), WildSample.Shape.LOOP, 120, 96, false);
        CRC32 crc = new CRC32();
        for (int p : pixels) { crc.update(p); crc.update(p >>> 8); crc.update(p >>> 16); }
        return out.append(Long.toHexString(crc.getValue())).toString();
    }
    /** LegacyGoldenChecks' wave: pressure rising and falling, the pen leaning and turning, 5 ms a sample. */
    static String draw(ModularBrush brush, double px, double darkness) {
        ToneDocument doc = InkWashLayers.blank(480, 360);
        DrawingStroke stroke = ModularStroke.start(doc, brush, px, darkness, new WatercolorSession());
        for (int i = 0; i <= 120; i++) {
            double t = i / 120.0;
            stroke.sample((float)(40 + 400 * t), (float)(180 + 100 * Math.sin(2 * Math.PI * 1.25 * t)),
                    ToolSettings.pressure((float)(.15 + .75 * Math.pow(Math.sin(Math.PI * t), .7))), 10 * (i % 7) - 30, 15, i * 5L);
        }
        stroke.finish();
        return crc(doc);
    }
    /** A page's checksum: every layer's tones and alpha. */
    static String crc(ToneDocument doc) {
        CRC32 crc = new CRC32();
        for (ToneDocument.Layer each : doc.layerSnapshot().layers) { ToneDocument.Layer layer = each.flattened(); crc.update(layer.tones); crc.update(layer.alpha); }
        return Long.toHexString(crc.getValue());
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
