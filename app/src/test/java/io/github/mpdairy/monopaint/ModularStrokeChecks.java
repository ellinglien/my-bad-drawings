package io.github.mpdairy.monopaint;

import java.util.Arrays;

public final class ModularStrokeChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        draws(); oneUndo(); noneIsFinal(); dryingSettlesAtLift(); neverLighter(); coverCovers(); coverHidesBelow(); deterministic(); masked(); fallbacks(); coverTones();
        System.out.println("PASS: a six-part stroke draws on its layer as one undo step; None is final as laid, Drying previews then settles at lift; ink never lightens what's beneath, cover lays light over dark and hides the layers below, by the coverage it ends at; masking fluid shields it; Bristles draw as hairs, Smudge draws");
    }
    static ToneDocument page(int layer) { ToneDocument d = InkWashLayers.blank(480, 360); d.selectLayer(layer); return d; }
    static ModularBrush fixture(String name) { return ModularCodeChecks.fixture(name); }
    static ModularBrush life(ModularBrush b, PartChoice life) { b.set(BrushPart.LIFE, new ModularBrush.Part(life)); return b; }
    static ModularStroke wave(ToneDocument d, ModularBrush b, double size, double darkness) {
        ModularStroke s = new ModularStroke(d, b, size, darkness, new WatercolorSession());
        for (int i = 0; i <= 120; i++) {
            double t = i / 120.0;
            s.sample((float)(40 + 400 * t), (float)(180 + 60 * Math.sin(2 * Math.PI * t)), ToolSettings.pressure((float)(.2 + .7 * Math.sin(Math.PI * t))), 0, 0, i * 5L);
        }
        // The page as the screen shows it with the pen still down.
        s.present();
        return s;
    }
    static byte[][] layers(ToneDocument d) { ToneDocument.Snapshot s = d.layerSnapshot(); byte[][] out = new byte[4][]; for (int i = 0; i < 2; i++) { ToneDocument.Layer l = s.layers.get(i).flattened(); out[2 * i] = l.tones.clone(); out[2 * i + 1] = l.alpha.clone(); } return out; }
    static boolean same(byte[][] a, byte[][] b) { for (int i = 0; i < a.length; i++) if (!Arrays.equals(a[i], b[i])) return false; return true; }
    static void draws() {
        ToneDocument d = page(InkWashLayers.INK);
        wave(d, life(fixture("brave-onion"), PartChoice.NONE), 16, 1).finish();
        int dark = 0; for (int y = 0; y < 360; y++) for (int x = 0; x < 480; x++) if (d.compositeTone(x, y) < 128) dark++;
        check(dark > 2000 && d.compositeTone(240, 20) == 255, "A six-part line draws along its path, paper elsewhere: " + dark);
        check(!d.layerDarkness(InkWashLayers.WASH, 0, 0, 480, 360, new int[480 * 360]), "on its own layer only");
    }
    static void oneUndo() {
        ToneDocument d = page(InkWashLayers.WASH);
        wave(d, fixture("wet-wash"), 60, 1).finish();
        check(d.compositeTone(240, 180) < 255, "A wash draws");
        d.undo();
        for (int y = 0; y < 360; y += 7) for (int x = 0; x < 480; x += 7) check(d.compositeTone(x, y) == 255, "and one Undo takes it all back");
    }
    static void noneIsFinal() {
        for (String name : new String[]{"soft-coal", "wide-hake", "rake", "hb-pencil"}) {
            ToneDocument d = page(name.equals("wide-hake") ? InkWashLayers.WASH : InkWashLayers.INK);
            ModularStroke s = wave(d, life(fixture(name), PartChoice.NONE), 24, 1);
            byte[][] before = layers(d);
            s.finish();
            check(same(before, layers(d)), "Life None: nothing changes at lift (" + name + ")");
        }
    }
    static void dryingSettlesAtLift() {
        ToneDocument d = page(InkWashLayers.WASH);
        // At the old black (a grey kept from before washes reached black: ModularSizes.sinceBlack), as it always did.
        ModularStroke s = wave(d, fixture("wet-wash"), 60, ModularSizes.sinceBlack(fixture("wet-wash"), 1));
        int preview = d.compositeTone(240, 180);
        check(preview == 200, "Drying previews as the old watercolor did (255 - (255 - min(200, .82 x 255)) x 1): " + preview);
        s.finish();
        check(d.compositeTone(240, 180) != preview, "and settles at lift");
        // At black, darker by the same steps over white: 255 - 55 x .95 / .34.
        ToneDocument black = page(InkWashLayers.WASH);
        ModularStroke b = wave(black, fixture("wet-wash"), 60, 1);
        int dark = black.compositeTone(240, 180);
        check(dark == Math.round(255 - 55 * ModularSizes.WASH_BLACK / ModularSizes.GLAZE) && dark < preview, "A black wash previews darker: " + dark);
        b.finish();
        check(black.compositeTone(240, 180) < d.compositeTone(240, 180), "and lays darker");
        ModularBrush tapered = life(fixture("brave-onion"), PartChoice.DRYING);
        tapered.part(BrushPart.TIP).set(PartDial.TAPER_OUT, 1);
        ToneDocument e = page(InkWashLayers.INK);
        ModularStroke t = wave(e, tapered, 16, 1);
        byte[][] before = layers(e);
        t.finish();
        check(!same(before, layers(e)), "A Drying line's taper out is laid at lift");
    }
    static void neverLighter() {
        ToneDocument d = page(InkWashLayers.INK);
        d.begin(); for (int y = 150; y < 210; y++) for (int x = 100; x < 400; x++) d.setTone(x, y, 100); d.finish();
        int[] before = new int[480 * 360]; for (int i = 0; i < before.length; i++) before[i] = d.compositeTone(i % 480, i / 480);
        // At mid grey: a lighter ink is white ink, which covers (ResolvedBrush.lightOver, LightOverDarkChecks).
        wave(d, life(fixture("brave-onion"), PartChoice.NONE), 16, 1 - InkWashShades.INK_GRAY / 255.0).finish();
        for (int i = 0; i < before.length; i++) check(d.compositeTone(i % 480, i / 480) <= before[i], "Ink at mid grey or darker never lightens what it crosses");
    }
    static ModularBrush gouache() {
        ModularBrush b = new ModularBrush("g", 3, ModularStyle.named("Gouache"));
        b.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.COVER));
        return life(b, PartChoice.NONE);
    }
    static void coverCovers() {
        ToneDocument d = page(InkWashLayers.WASH);
        d.begin(); for (int y = 100; y < 260; y++) for (int x = 100; x < 400; x++) d.setTone(x, y, 40); d.finish();
        wave(d, gouache(), 60, .31).finish();
        int tone = d.compositeTone(240, 180), gray = (int)Math.round(255 * .69);
        check(Math.abs(tone - gray) <= 3 && d.opacity(240, 180) == 255, "Light gouache covers dark on its layer: " + tone);
    }
    static void coverHidesBelow() {
        ToneDocument d = InkWashLayers.blank(480, 360);
        d.selectLayer(InkWashLayers.WASH); d.begin(); for (int y = 100; y < 260; y++) for (int x = 100; x < 400; x++) d.setTone(x, y, 40); d.finish();
        d.selectLayer(InkWashLayers.INK);
        wave(d, gouache(), 60, .31).finish();
        check(Math.abs(d.compositeTone(240, 180) - Math.round(255 * .69)) <= 3, "and hides the layers below");
    }
    static void deterministic() {
        String[] sums = new String[2];
        for (int i = 0; i < 2; i++) { ToneDocument d = page(InkWashLayers.INK); wave(d, fixture("rake"), 16, 1).finish(); sums[i] = LegacyConversionChecks.crc(d); }
        check(sums[0].equals(sums[1]), "The same brush and samples draw the same pixels");
    }
    static void masked() {
        ToneDocument d = page(InkWashLayers.INK);
        d.paintMaskingFluid(true); d.begin(); for (int y = 0; y < 360; y++) for (int x = 200; x < 280; x++) d.paintTone(x, y, 0, 255); d.finish(); d.paintMaskingFluid(false);
        wave(d, life(fixture("brave-onion"), PartChoice.NONE), 16, 1).finish();
        d.peelMask();
        for (int y = 0; y < 360; y++) for (int x = 200; x < 280; x++) check(d.compositeTone(x, y) == 255, "Masking fluid shields a six-part stroke");
    }
    static void fallbacks() {
        ModularBrush bristles = life(ModularCode.decode("test-tip.Md6rog7js5ei0_000zz0000000000000_0_000_0_0000000000000000000nz0.9ix"), PartChoice.NONE);
        ToneDocument d = page(InkWashLayers.INK); wave(d, bristles, 16, 1).finish();
        int marked = 0; for (int y = 0; y < 360; y++) for (int x = 0; x < 480; x++) if (d.compositeTone(x, y) < 200) marked++;
        check(marked > 1000, "Bristles draw (BristleSwarmChecks checks how): " + marked);
        ModularBrush drag = life(ModularCode.decode("test-carry.Md0i000000000at_000zz0000000000000_2llp6ul_000_0_0000000000000000000nz0.9ix"), PartChoice.NONE), off = drag.copy();
        off.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.OFF));
        ToneDocument a = page(InkWashLayers.INK), b = page(InkWashLayers.INK);
        wave(a, drag, 16, 1).finish(); wave(b, off, 16, 1).finish();
        check(!LegacyConversionChecks.crc(a).equals(LegacyConversionChecks.crc(b)), "Drag draws (CarrySmudgeChecks checks how)");
    }
    static void coverTones() {
        ToneDocument d = page(InkWashLayers.WASH);
        d.begin(); d.coverTones(10, 10, 2, 1, new int[]{200, -1}, new int[]{255, 255}); d.finish();
        check(d.compositeTone(10, 10) == 200 && d.opacity(10, 10) == 255 && d.compositeTone(11, 10) == 255 && d.opacity(11, 10) == 0, "coverTones lays opaque tone; a negative gray leaves its pixel");
        d.begin(); d.coverTones(10, 10, 1, 1, new int[]{220}, new int[]{40}); d.finish();
        check(d.compositeTone(10, 10) == 220 && d.opacity(10, 10) == 255, "and never lowers the coverage already there");
        // Within one gesture a cover written high, then lower (a wet window settling), ends at the lower coverage.
        ToneDocument once = InkWashLayers.blank(480, 360), twice = InkWashLayers.blank(480, 360);
        for (ToneDocument e : new ToneDocument[]{once, twice}) { e.selectLayer(InkWashLayers.WASH); e.begin(); e.setTone(20, 10, 40); e.finish(); e.selectLayer(InkWashLayers.INK); e.begin(); }
        twice.coverTones(20, 10, 1, 1, new int[]{200}, new int[]{255});
        once.coverTones(20, 10, 1, 1, new int[]{200}, new int[]{100}); twice.coverTones(20, 10, 1, 1, new int[]{200}, new int[]{100});
        once.finish(); twice.finish();
        check(twice.opacity(20, 10) == 100 && twice.compositeTone(20, 10) == once.compositeTone(20, 10) && twice.compositeTone(20, 10) < 200,
                "A cover lowered within its gesture ends at the lower coverage, the layer below showing again: " + twice.opacity(20, 10));
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
