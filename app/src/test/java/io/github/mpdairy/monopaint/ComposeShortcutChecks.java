package io.github.mpdairy.monopaint;

/**
 * ModularCompose leaves a pixel whose line and body are as last composed; composing every pixel instead must draw the
 * same page, for the brushes whose windows and relief take marks back or change them while the pen is down.
 */
public final class ComposeShortcutChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        ModularBrush ink = ModularStrokeChecks.life(ModularCodeChecks.fixture("brave-onion"), PartChoice.NONE);
        ResolvedBrush r = ResolvedBrush.of(ink, 16, 1);
        check(r.line && r.body != null, "brave-onion under None lays a line and a body");
        Object[][] cases = {
                {"Fast-wet wash", ModularStrokeChecks.life(ModularCodeChecks.fixture("wet-wash"), PartChoice.FAST_WET), 60.0},
                {"None, line and body", ink, 16.0},
                {"charcoal", ModularStrokeChecks.life(ModularCodeChecks.fixture("soft-coal"), PartChoice.NONE), 24.0},
                {"Fast-wet impasto", ModularStrokeChecks.life(ModularCodeChecks.fixture("thick-oil"), PartChoice.FAST_WET), 60.0},
                {"Drying wash", ModularStrokeChecks.life(ModularCodeChecks.fixture("wet-wash"), PartChoice.DRYING), 60.0},
                {"Fast-wet Smear", ModularCode.decode(CarrySmudgeChecks.SMEAR), 24.0},
                {"Drag, Fast-wet", ModularStrokeChecks.life(ModularCode.decode(CarrySmudgeChecks.GOLDENS[5][1].toString()), PartChoice.FAST_WET), 60.0},
                {"Drag, Drying", ModularStrokeChecks.life(ModularCode.decode(CarrySmudgeChecks.GOLDENS[5][1].toString()), PartChoice.DRYING), 60.0},
                {"Stump", ModularCode.decode(CarrySmudgeChecks.GOLDENS[2][1].toString()), 28.0}};
        for (Object[] c : cases) {
            ModularBrush b = (ModularBrush)c[1];
            double size = (Double)c[2];
            String skipping = draw(b, size);
            ModularCompose.skipUnchanged = false;
            String every;
            try { every = draw(b, size); } finally { ModularCompose.skipUnchanged = true; }
            check(skipping.equals(every), c[0] + ": composing every pixel draws the same page (" + skipping + " vs " + every + ")");
        }
        System.out.println("PASS: composing only what changed draws the same page as composing every pixel (Fast-wet wash and impasto, ink with a body, charcoal, a Drying wash, smudges: what a smudge changed is composed)");
    }
    /** A wave and a crossing stroke over paint already on both layers; the page's CRC. */
    static String draw(ModularBrush b, double size) {
        PartChoice m = b.choice(BrushPart.MEDIUM);
        ToneDocument d = InkWashLayers.blank(480, 360);
        d.selectLayer(InkWashLayers.WASH); d.begin(); for (int y = 120; y < 240; y++) for (int x = 150; x < 330; x++) d.setTone(x, y, 120); d.finish();
        d.selectLayer(InkWashLayers.INK); d.begin(); for (int y = 0; y < 360; y++) for (int x = 230; x < 240; x++) d.setTone(x, y, 30); d.finish();
        d.selectLayer(m == PartChoice.WASH || m == PartChoice.COVER ? InkWashLayers.WASH : InkWashLayers.INK);
        ModularStrokeChecks.wave(d, b, size, .8).finish();
        ModularStroke s = new ModularStroke(d, b, size * .7, .5, new WatercolorSession());
        for (int i = 0; i <= 60; i++) s.sample(60 + 6 * i, 40 + 4.5f * i, ToolSettings.pressure(.4f + .008f * i), 0, 0, i * 7L);
        s.finish();
        return LegacyConversionChecks.crc(d);
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
