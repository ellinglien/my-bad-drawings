package io.github.mpdairy.monopaint;

public final class FastWetChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        nothingAtLift(); finalBehind(); tailTapersWhileDrawing(); hollowBehind(); untimed(); paced();
        System.out.println("PASS: Fast-wet changes nothing at lift; what lies behind its window is final; the tail tapers and the wash develops while the pen is down; strokes without times still taper; the tail re-tapers at most every 50 ms");
    }
    static ModularBrush wet(String name) { return ModularStrokeChecks.life(ModularCodeChecks.fixture(name), PartChoice.FAST_WET); }
    static ToneDocument page(ModularBrush b) {
        PartChoice m = b.choice(BrushPart.MEDIUM);
        return ModularStrokeChecks.page(m == PartChoice.WASH || m == PartChoice.COVER ? InkWashLayers.WASH : InkWashLayers.INK);
    }
    static void nothingAtLift() {
        for (String name : new String[]{"brave-onion", "wet-wash", "thick-oil", "rake", "wide-hake"}) {
            ModularBrush b = wet(name);
            ToneDocument d = page(b);
            ModularStroke s = ModularStrokeChecks.wave(d, b, name.equals("brave-onion") || name.equals("rake") ? 16 : 60, 1);
            byte[][] before = ModularStrokeChecks.layers(d);
            s.finish();
            check(ModularStrokeChecks.same(before, ModularStrokeChecks.layers(d)), "Fast-wet: nothing changes at lift (" + name + ")");
        }
    }
    static void finalBehind() {
        ModularBrush b = wet("wet-wash");
        ToneDocument d = InkWashLayers.blank(1400, 400); d.selectLayer(InkWashLayers.WASH);
        ModularStroke s = new ModularStroke(d, b, 60, 1, new WatercolorSession());
        int[] early = null;
        for (int i = 0; i <= 600; i++) {
            s.sample(40 + 2 * i, 200, ToolSettings.pressure(.7f), 0, 0, i * 5L);
            s.present();
            if (i == 300) { early = new int[120 * 200]; for (int y = 100; y < 300; y++) for (int x = 40; x < 160; x++) early[(y - 100) * 120 + x - 40] = d.compositeTone(x, y); }
        }
        s.finish();
        for (int y = 100; y < 300; y++) for (int x = 40; x < 160; x++) check(d.compositeTone(x, y) == early[(y - 100) * 120 + x - 40], "What lies far behind the window never changes again");
    }
    static void tailTapersWhileDrawing() {
        ModularBrush b = wet("brave-onion");
        b.set(BrushPart.TIP, new ModularBrush.Part(PartChoice.ROUND)); b.part(BrushPart.TIP).set(PartDial.TAPER_OUT, 1);
        b.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.INK));
        ToneDocument d = ModularStrokeChecks.page(InkWashLayers.INK);
        ModularStroke s = new ModularStroke(d, b, 16, 1, new WatercolorSession());
        for (int i = 0; i <= 150; i++) { float p = i < 120 ? .7f : .7f * (150 - i) / 30f; s.sample(40 + 2.5f * i, 180, ToolSettings.pressure(p), 0, 0, i * 8L); }
        s.present();
        // At x = 400, inside the tail the last re-taper (at x = 410) laid; the dabs after it, under 50 ms of pen time, keep their width.
        int middle = rows(d, 150), end = rows(d, 400);
        check(end < middle - 4, "The tail tapers while the pen is still down: " + middle + " → " + end);
        s.finish();
        check(rows(d, 400) == end, "and stays so at lift");
    }
    static int rows(ToneDocument d, int x) { int n = 0; for (int y = 0; y < d.height; y++) if (d.compositeTone(x, y) < 128) n++; return n; }
    static void hollowBehind() {
        ModularBrush b = wet("wet-wash");
        b.part(BrushPart.MEDIUM).set(PartDial.POOL, 1);
        ToneDocument d = InkWashLayers.blank(1000, 400); d.selectLayer(InkWashLayers.WASH);
        ModularStroke s = new ModularStroke(d, b, 80, 1, new WatercolorSession());
        for (int i = 0; i <= 400; i++) s.sample(40 + 2 * i, 200, ToolSettings.pressure(1), 0, 0, i * 10L);
        s.finish();
        int y0 = 200, rim = 255, middle = d.compositeTone(200, y0);
        for (int y = 160; y < 200; y++) rim = Math.min(rim, d.compositeTone(200, y));
        check(rim < middle, "Behind the nib the wash develops its hollow edge while drawing: rim " + rim + " vs middle " + middle);
    }
    static void untimed() {
        ModularBrush b = wet("brave-onion");
        b.set(BrushPart.TIP, new ModularBrush.Part(PartChoice.ROUND)); b.part(BrushPart.TIP).set(PartDial.TAPER_OUT, 1);
        b.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.INK));
        ToneDocument d = ModularStrokeChecks.page(InkWashLayers.INK);
        ModularStroke s = new ModularStroke(d, b, 16, 1, new WatercolorSession());
        for (int i = 0; i <= 150; i++) { float p = i < 120 ? .7f : .7f * (150 - i) / 30f; s.sample(40 + 2.5f * i, 180, ToolSettings.pressure(p), 0, 0); }
        s.finish();
        check(rows(d, 410) < rows(d, 150) - 4, "A stroke without times still tapers");
    }
    static void paced() {
        ModularBrush b = wet("brave-onion");
        b.set(BrushPart.TIP, new ModularBrush.Part(PartChoice.ROUND)); b.part(BrushPart.TIP).set(PartDial.TAPER_OUT, 1);
        ToneDocument d = ModularStrokeChecks.page(InkWashLayers.INK);
        ModularStroke s = new ModularStroke(d, b, 16, 1, new WatercolorSession());
        for (int i = 0; i <= 300; i++) s.sample(40 + i, 180, ToolSettings.pressure(.6f), 0, 0, i);
        check(s.retapers >= 2 && s.retapers <= 300 / 50 + 1, "The tail re-tapers at most every 50 ms of pen time: " + s.retapers);
        s.finish();
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
