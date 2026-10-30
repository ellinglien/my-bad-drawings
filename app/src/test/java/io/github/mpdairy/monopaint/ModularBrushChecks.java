package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class ModularBrushChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        value(); storage(); labels(); sizes(); swatches(); strokes(); samples(); slots();
        System.out.println("PASS: a six-part brush is a Brush: stored as its M code, read back, keyed by its settings, drawn on the ink layer for ink, charcoal and graphite and the wash layer for wash and cover, sized from its style, sampled to fit its tile; old codes read as six parts; Wild shows as Anything goes");
    }
    static Brush f(String name) { return Brush.of(ModularCodeChecks.fixture(name)); }
    static void value() {
        check(f("brave-onion").inks() && f("soft-coal").inks() && f("hb-pencil").inks() && !f("wet-wash").inks() && !f("thick-oil").inks(), "Ink, charcoal and graphite are pens; wash and cover paints");
        check(f("brave-onion").name().equals("brave-onion"), "Its name is the six-part brush's");
    }
    static void storage() {
        Brush b = f("rake");
        check(b.write().equals("rake\t" + ModularCode.encode(b.parts)), "Stored as name and M code");
        Brush back = Brush.read(b.write());
        check(back != null && back.parts != null && back.sameAs(b), "and read back as the same brush");
        Brush renamed = Brush.of(b.parts.copy()); renamed.parts.name = "Other Name";
        check(!renamed.sameAs(b) && Brush.read(renamed.write()).name().equals("Other Name"), "A name is part of identity and survives storage");
        Brush old = Brush.read("x\tdapper-fig.I610gavij3f7q16o0000006m00.e1");
        check(old != null && old.name().equals("x") && old.parts.style.wild, "Old codes read as six parts (LegacyBrushes)");
    }
    static void labels() {
        check(ModularStyle.named("Wild") == ModularStyle.named(ModularStyle.ANYTHING_GOES) && f("rake").parts.style.wild && f("brave-onion").parts.style.name.equals("Dip pen"), "Wild is called Anything goes");
        check(BrushCaptions.describe(f("brave-onion")).equals(BrushCaptions.twoLines(ModularWords.caption(ModularCodeChecks.fixture("brave-onion")))), "Six-part captions");
    }
    static void sizes() {
        ModularBrush onion = ModularCodeChecks.fixture("brave-onion");
        check(ModularSizes.size(onion, InkWashSizes.Preset.MEDIUM) == 6 && Math.abs(ModularSizes.size(onion, InkWashSizes.Preset.SMALL) - 2.4) < 1e-9 && ModularSizes.size(onion, InkWashSizes.Preset.LARGE) == 12, "Dip pen: 2.4, 6 and 12 px");
        check(ModularSizes.size(ModularCodeChecks.fixture("rake"), InkWashSizes.Preset.MEDIUM) == 8 && Math.abs(ModularSizes.size(ModularCodeChecks.fixture("hb-pencil"), InkWashSizes.Preset.SMALL) - 1.6) < 1e-9, "Anything goes ink is 8 px at M; Pencil's S is 1.6 px");
        ModularBrush wash = ModularCodeChecks.fixture("rake").copy(); wash.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.WASH));
        check(ModularSizes.size(wash, InkWashSizes.Preset.MEDIUM) == 120 && ModularSizes.size(ModularCodeChecks.fixture("wide-hake"), InkWashSizes.Preset.LARGE) == 360, "Anything goes paint is 120 px at M; Hake's L is 360");
        check(ModularSizes.darkness(InkWashShades.Shade.DARK) == 1 && Math.abs(ModularSizes.darkness(InkWashShades.Shade.LIGHT) - (1 - 120 / 255.0)) < 1e-12 && Math.abs(ModularSizes.darkness(InkWashShades.Shade.LIGHTER) - (1 - 175 / 255.0)) < 1e-12, "Shades are the ink greys' darkness");
        check(Math.abs(ModularSizes.sampleSize(onion, 96) - 96 / (13 + 14 * onion.part(BrushPart.MEDIUM).get(PartDial.WET))) < 1e-4 && Math.abs(ModularSizes.sampleSize(ModularCodeChecks.fixture("wide-hake"), 96) - 96 * .24) < 1e-4, "Samples fit their tile");
    }
    static void swatches() {
        check(ModularSizes.swatch(ModularCodeChecks.fixture("brave-onion"), InkWashShades.Shade.LIGHT) == InkWashShades.INK_GRAY && ModularSizes.swatch(ModularCodeChecks.fixture("wet-wash"), InkWashShades.Shade.DARK) == Math.round(255 * (1 - ModularSizes.WASH_BLACK))
                && ModularSizes.swatch(ModularCodeChecks.fixture("wet-wash"), ModularSizes.sinceBlack(ModularCodeChecks.fixture("wet-wash"), 1)) == Math.round(255 * (1 - .34))
                && ModularSizes.swatch(ModularCodeChecks.fixture("thick-oil"), InkWashShades.Shade.DARK) == 0, "Swatches: ink's grey, a wash's glaze, cover's own grey");
    }
    static void strokes() {
        for (String name : new String[]{"brave-onion", "wet-wash"}) {
            ToneDocument d = InkWashLayers.blank(480, 360);
            DrawingStroke s = ModularStroke.start(d, ModularCodeChecks.fixture(name), InkWashSizes.Preset.MEDIUM, InkWashShades.Shade.DARK, new WatercolorSession());
            check(d.activeLayer() == (name.equals("wet-wash") ? InkWashLayers.WASH : InkWashLayers.INK), "start picks the layer by medium: " + name);
            for (int i = 0; i <= 60; i++) s.sample(60 + 6 * i, 180, ToolSettings.pressure(.6f), 0, 0, i * 6L);
            check(s.finish(), "and draws: " + name);
        }
    }
    static void samples() {
        for (String code : ModularCodeChecks.FIXTURES) {
            ModularBrush b = ModularCode.decode(code);
            int[] px = WildSample.modular(b, 1, WildSample.Shape.LOOP, 120, 96, true);
            int dark = 0; for (int p : px) if ((p & 0xff) < 200) dark++;
            check(px.length == 120 * 96 && dark > 30, "A six-part sample shows marks: " + code);
            check(WildSample.modular(b, 1, WildSample.Shape.LOOP, 120, 96, true) == px, "and is kept");
        }
    }
    static void slots() {
        Map<String, String> store = new HashMap<>();
        BrushLibrary.Store s = new BrushLibrary.Store() { public String get(String k) { return store.get(k); } public void put(String k, String v) { if (v == null) store.remove(k); else store.put(k, v); } };
        ToolSlots slots = new ToolSlots(s, new Random(1));
        Brush b = f("thick-oil");
        check(slots.use(3, b) && slots.brush(3).sameAs(b) && new ToolSlots(s, new Random(2)).brush(3).sameAs(b), "A six-part brush lives in a slot across launches");
        check(slots.library.save(b) == BrushLibrary.Saved.ADDED && slots.library.contains(b), "and in the library");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
