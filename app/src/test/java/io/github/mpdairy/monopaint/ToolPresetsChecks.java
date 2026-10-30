package io.github.mpdairy.monopaint;

import java.util.HashSet;
import java.util.Set;

public final class ToolPresetsChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        table(); codes(); words(); draws(); tissue();
        System.out.println("PASS: 126 presets, five for each of the 13 rolled styles, Print's twelve (five Screentone, seven through Print Shading), the everyday pens (Ballpoint, Gel pen, Fountain pen, Highlighter), the owner's seven Brush Studio pens (two Fineliner, one Brush pen, three Marker, one Pencil), the eight showing the engine's choices no other preset did and the Stamp, the four gravity ones (three Watercolor, one Charcoal; Drips retired) that move with the page's tilt, Charcoal's Stump Ink wash's Sumi and Clear water (Float), Watercolor's Dirty brush and Water brush (the wet smudges), Gouache's and Oils' six Wet ones (Gouache water, Loaded oils) and Brush pen's Wet brush (ink in water), the old Gouache and Oils and the old Wet brush retired but known, a bristle brush (Dry brush), Pencil's Sketch pencil (erases clean) and the web lab's Stipple pen, Hatch pen, Hand hatch, Neat hatch, Loose hatch (Pencil), White gel pen (Fineliner), Stipple sponge, Wash behind, Masking fluid and Wax resist (Watercolor) and Mold ink (Ink wash); each erasing clean (Erasable 1; Resist's wax or fluid its own); every share code decodes to its style and round-trips, names are unique, names (but the owner's) and descriptions fit one line of the Nomad's card, and every preset draws; Damp tissue lifts the wash and darkens nothing, paper or paint");
    }

    /** Watercolor's washes made Wet (the wet paint spec, section 9), under their old names. */
    static final java.util.List<String> WET_WASHES = java.util.Arrays.asList("Soft wash", "Wet blooms", "Backrun", "Granulating", "Slow bloom", "Pooling wash", "Tilt blend", "Sinking grain");
    /** Ink wash's washes made Wet (the ink wash note, 2026-10-22), under their old names. */
    static final java.util.List<String> WET_INK_WASHES = java.util.Arrays.asList("Line and wash", "Wet ink", "Grey wash", "Flat wash", "Slow ink wash");
    /** A wet wash's style and name: Watercolor's or Ink wash's. */
    static boolean wetWash(ToolPresets.Preset p) { return p.style.name.equals("Watercolor") && WET_WASHES.contains(p.name) || p.style.name.equals("Ink wash") && WET_INK_WASHES.contains(p.name); }
    /** The owner's pens (2026-10-27): Brush Studio saves kept as made, under the owner's names. */
    static final java.util.List<String> OWNERS = java.util.Arrays.asList("Cheap ballpoint", "Smooth fountain", "Ink wash brush", "Permanent marker", "Acrylic paint marker", "Ms. Sketch", "Another pencil");
    static void table() {
        check(ToolPresets.styles().size() == 14 && !ToolPresets.styles().contains(ModularStyle.named(ModularStyle.ANYTHING_GOES)) && ToolPresets.styles().get(13) == ModularStyle.named("Print"), "The chips' 14 styles, Print last, not Anything goes");
        check(ToolPresets.all().size() == 126 && ToolPresets.of(null).size() == 126, "126 presets; All shows them all");
        java.util.List<String> everyday = java.util.Arrays.asList("Ballpoint", "Gel pen", "Fountain pen", "Highlighter");
        for (ModularStyle s : ToolPresets.styles()) {
            if (s.name.equals("Print")) { prints(); continue; }
            int pens = 0, owned = 0;
            int engine = 0;
            int gravity = 0, stump = 0, floats = 0, bristles = 0, sketch = 0, patterns = 0, blends = 0, resists = 0, molds = 0, wets = 0, oils = 0;
            for (ToolPresets.Preset p : ToolPresets.of(s)) {
                ModularBrush b = ModularCode.decode(p.code);
                // A resist's Erasable says which it is: masking fluid (1) or wax (0).
                if (b.choice(BrushPart.MEDIUM) == PartChoice.RESIST) { resists++; continue; }
                check(b.part(BrushPart.MEDIUM).get(PartDial.ERASABLE, 1) == 1, p.name + " erases clean (Erasable 1, 2026-10-22)");
                // The engine's choices no preset showed (Crayon, Spiro, Stay in Lines, Filled Shape, Lift, Fading, Lemon Juice, Springy): counted on their own.
                if (engine(b) || p.name.equals("Damp tissue")) { engine++; continue; }
                // The everyday pens, built by hand: counted on their own.
                if (everyday.contains(p.name)) { pens++; continue; }
                if (OWNERS.contains(p.name)) { owned++; continue; }
                // The wet smudges (Dirty brush, Water brush): counted on their own.
                if (b.part(BrushPart.CARRY).get(PartDial.KEEPS, 0) > 0 || b.choice(BrushPart.CARRY) == PartChoice.CLEAR_WATER) { wets++; continue; }
                // Oil paint (the oil paint spec): Paint Load, counted on its own.
                if (b.part(BrushPart.CARRY).get(PartDial.PLOAD, 0) > 0) { oils++; continue; }
                // The Gravity Lab's (a Wet brush's Runs Downhill is the wet paint's own; but the three wet watercolours that
                // replaced its Pooling wash, Tilt blend and Sinking grain are still its).
                boolean wetLife = b.choice(BrushPart.LIFE) == PartChoice.WET_PAINT;
                if (GravityChecks.usesGravity(b) && !wetLife || wetLife && s.name.equals("Watercolor") && b.part(BrushPart.LIFE).get(PartDial.RUNS) > 0) gravity++;
                if (p.name.equals("Sketch pencil")) sketch++;
                // The smudges that lay nothing on the page (Stump, Blend stump, Wash comb; Float's are Float's).
                if (b.choice(BrushPart.LIFE) != PartChoice.FLOAT && b.choice(BrushPart.CARRY) != PartChoice.OFF && b.part(BrushPart.CARRY).get(PartDial.LOAD, 1) == 0) stump++;
                if (b.choice(BrushPart.LIFE) == PartChoice.FLOAT) floats++;
                if (b.choice(BrushPart.LIFE) == PartChoice.HALO) molds++;
                if (b.choice(BrushPart.TIP) == PartChoice.BRISTLES) bristles++;
                if (b.choice(BrushPart.TIP) == PartChoice.STIPPLE || b.choice(BrushPart.TIP) == PartChoice.HATCH || b.choice(BrushPart.TIP) == PartChoice.HAND_HATCH) patterns++;
                if (b.part(BrushPart.MEDIUM).get(PartDial.M_BLEND) != 0) blends++;
            }
            check(pens == (s.name.equals("Fineliner") ? 3 : s.name.equals("Marker") ? 1 : 0), "Ballpoint, Gel pen and Fountain pen under Fineliner, Highlighter under Marker");
            check(owned == (s.name.equals("Fineliner") ? 2 : s.name.equals("Marker") ? 3 : s.name.equals("Brush pen") || s.name.equals("Pencil") ? 1 : 0), "Cheap ballpoint and Smooth fountain under Fineliner, Ink wash brush under Brush pen, Permanent marker, Acrylic paint marker and Mr. Sketch under Marker, Another pencil under Pencil");
            check(ToolPresets.of(s).size() - engine - pens - owned - gravity - stump - floats - bristles - sketch - patterns - blends - resists - molds - wets - oils == 5, "Five presets for " + s.name + " besides gravity's, the smudges, Float's, bristles', Sketch pencil, the stipples and hatching, Blend's, Resist's, Mold's, the wet smudges and Oil paint");
            check(engine == (s.name.equals("Marker") || s.name.equals("Fineliner") ? 3 : s.name.equals("Pastels") ? 2 : s.name.equals("Brush pen") || s.name.equals("Dip pen") || s.name.equals("Watercolor") ? 1 : 0),
                    "Spirograph, Vanishing ink and Stamp under Fineliner, Springy brush under Brush pen, Lemon juice under Dip pen, Colouring pen, Fill in and Filled shape under Marker, Damp tissue under Watercolor, Wax crayon and Crayon scratch under Pastels");
            check(oils == (s.name.equals("Oils") ? 1 : 0), "Loaded oils under Oils");
            check(wets == (s.name.equals("Watercolor") ? 2 : s.name.equals("Gouache") ? 1 : 0), "Dirty brush and Water brush under Watercolor, Gouache water under Gouache");
            check(molds == (s.name.equals("Ink wash") ? 1 : 0), "Mold ink under Ink wash");
            check(resists == (s.name.equals("Watercolor") ? 2 : 0), "Masking fluid and Wax resist under Watercolor");
            check(blends == (s.name.equals("Fineliner") || s.name.equals("Watercolor") ? 1 : 0), "White gel pen under Fineliner, Wash behind under Watercolor");
            check(patterns == (s.name.equals("Fineliner") ? 4 : s.name.equals("Watercolor") || s.name.equals("Pencil") ? 1 : 0), "Stipple pen, Hatch pen, Hand hatch and Neat hatch under Fineliner, Loose hatch under Pencil, Stipple sponge under Watercolor");
            check(sketch == (s.name.equals("Pencil") ? 1 : 0), "One Sketch pencil, under Pencil");
            check(floats == (s.name.equals("Ink wash") ? 3 : 0), "Float's three (Sumi, Clear water, Comb), under Ink wash");
            check(stump == (s.name.equals("Charcoal") ? 2 : s.name.equals("Watercolor") ? 1 : 0), "Stump and Blend stump under Charcoal, Wash comb under Watercolor");
            check(bristles == (s.name.equals("Dry brush") ? 1 : 0), "Bristles' preset: one Dry brush (Hog bristle retired with the old Oils)");
            // Gouache and Oils are Wet (the wet paint spec; the owner, 2026-10-22), and Watercolor's washes (section 9), every other style's none.
            for (ToolPresets.Preset p : ToolPresets.of(s))
                check((p.brush().parts.choice(BrushPart.LIFE) == PartChoice.WET_PAINT) == (s.name.equals("Gouache") || s.name.equals("Oils") || p.name.equals("Wet brush")
                        || wetWash(p)), p.name + ": Gouache, Oils, the Wet brush and Watercolor's and Ink wash's washes are Wet, no other");
            check(gravity == (s.name.equals("Watercolor") ? 3 : s.name.equals("Charcoal") ? 1 : 0), "Gravity's presets: three Watercolor, one Charcoal (Drips retired)");
        }
        Set<String> names = new HashSet<>();
        for (ToolPresets.Preset p : ToolPresets.all()) check(names.add(p.name.toLowerCase(java.util.Locale.ROOT)), "Preset names are unique: " + p.name);
        // The old Gouache and Oils (retired when Wet replaced them): known, not offered, none of their names reused.
        check(ToolPresets.retired().size() == 27, "27 retired presets, the old Ink wash washes' five, the old Watercolor washes' eight, Drips, the old Wet brush, the old Gouache's five and Oils' seven");
        for (ToolPresets.Preset p : ToolPresets.retired()) {
            // The old Wet brush's name goes on: the wet canvas one replaced it under the same name (the wet brush spec), and
            // so do the Watercolor washes' (section 9).
            boolean wetBrush = p.name.equals("Wet brush"), drips = p.name.equals("Drips") && p.style.name.equals("Watercolor"), wash = wetWash(p);
            check(names.contains(p.name.toLowerCase(java.util.Locale.ROOT)) == (wetBrush || wash) && ToolPresets.find(p.brush()) == null, p.name + " is retired: not offered, its name not reused (but the Wet brush's and the washes')");
            check(p.brush().parts.choice(BrushPart.LIFE) != PartChoice.WET_PAINT && (p.style.name.equals("Gouache") || p.style.name.equals("Oils") || wetBrush && p.style.name.equals("Brush pen") || wash || drips),
                    p.name + " is an old Gouache or Oils brush, the old Wet brush, an old Watercolor or Ink wash wash or Drips");
        }
        // The wet watercolours (section 9): the old wash's tip, medium, paper and Control, Wet; a transparent wash on the wet canvas.
        for (ToolPresets.Preset p : ToolPresets.all()) if (wetWash(p)) {
            ModularBrush b = p.brush().parts, old = null;
            for (ToolPresets.Preset q : ToolPresets.retired()) if (q.name.equals(p.name) && q.style == p.style) old = q.brush().parts;
            check(old != null, p.name + " replaced an old one");
            for (BrushPart part : BrushPart.values()) if (part != BrushPart.LIFE)
                check(ModularCode.encode(b).split("_")[part.ordinal()].replaceAll("^.*\\.M[67]", "").equals(ModularCode.encode(old).split("_")[part.ordinal()].replaceAll("^.*\\.M[67]", "")),
                        p.name + ": its " + part + " is the old one's");
            ResolvedBrush r = ResolvedBrush.of(b, 60, .7);
            boolean ink = p.style.name.equals("Ink wash");
            check((b.choice(BrushPart.MEDIUM) == PartChoice.WASH || ink && b.choice(BrushPart.MEDIUM) == PartChoice.INK) && r.wet != null && r.wet.wash != null && !r.wet.inks && !r.wet.rewets
                    && (!ink || r.wet.runs == 0), p.name + ": a wet wash, dried paint glazed over (Ink wash's still on a tilt)");
            // Ink wash: on the wash half, so its washes merge with the others there; Line and wash and Wet ink keep their pen line.
            check(ModularStroke.layerOf(InkWashLayers.blank(10, 10), b) == InkWashLayers.WASH, p.name + ": wet on the wash half");
            check(r.wet.line == (p.name.equals("Line and wash") || p.name.equals("Wet ink")), p.name + ": a pen line only where it is the brush's");
        }
        // The Wet brush: Wet ink in water on a Wash (the wash half), its old one's tip and Control.
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals("Wet brush")) {
            ModularBrush b = p.brush().parts;
            ResolvedBrush r = ResolvedBrush.of(b, 60, 1);
            check(b.choice(BrushPart.MEDIUM) == PartChoice.WASH && r.wet != null && r.wet.inks && r.wet.rewets && r.wet.runs > 0, "Wet brush: Wet ink in water on a Wash, lifting dry paint, running downhill");
        }
    }
    /** Print's twelve: Screentone's five patterns (Dots to Crosshatch), then seven through a Print Shading, each a different one. */
    static void prints() {
        java.util.List<ToolPresets.Preset> print = ToolPresets.of(ModularStyle.named("Print"));
        check(print.size() == 12, "Twelve Print presets");
        Set<Integer> patterns = new HashSet<>(), screens = new HashSet<>();
        for (ToolPresets.Preset p : print) {
            ModularBrush.Part m = p.brush().parts.part(BrushPart.MEDIUM);
            if (m.has(PartDial.ERASABLE)) check(m.get(PartDial.ERASABLE) == 1, p.name + " erases clean");
            if (m.choice == PartChoice.TONE) patterns.add((int) m.get(PartDial.PATTERN));
            else screens.add((int) m.get(PartDial.SCREEN, 0));
        }
        check(patterns.size() == 5 && !patterns.contains(5) && !patterns.contains(6), "Five Screentone patterns: " + patterns);
        check(screens.size() == 7 && !screens.contains(0) && screens.containsAll(java.util.Arrays.asList(1, 2, 3, 5, 6, 8, 9)), "Seven Print Shadings, all different: " + screens);
        // The highlighter goes behind the lines (Blend Mode's Behind) in a light wash.
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals("Highlighter")) {
            ModularBrush b = p.brush().parts;
            check(b.choice(BrushPart.MEDIUM) == PartChoice.WASH && b.part(BrushPart.MEDIUM).get(PartDial.M_BLEND) == 2 && b.choice(BrushPart.TIP) == PartChoice.CHISEL
                    && ModularSizes.size(b, InkWashSizes.Preset.MEDIUM) >= 40, "Highlighter: a broad chisel of wash, behind");
        }
        // Every preset's code is PresetPictures' (the rows are what it prints).
        for (PresetPictures.Made m : PresetPictures.made()) {
            ToolPresets.Preset p = null;
            for (ToolPresets.Preset q : ToolPresets.all()) if (q.name.equals(m.name)) p = q;
            check(p != null && p.code.equals(m.code()) && p.line.equals(m.line) && p.style.name.equals(m.style), m.name + " is PresetPictures' row");
        }
    }
    /** A preset that shows a choice of the engine's no other preset does: Crayon, Spiro, Stay in Lines, Filled Shape, Lift, Fading, Lemon Juice or Control's Springy. */
    static boolean engine(ModularBrush b) {
        return b.choice(BrushPart.MEDIUM) == PartChoice.CRAYON || b.choice(BrushPart.TIP) == PartChoice.SPIRO || b.choice(BrushPart.CARRY) == PartChoice.STAY_IN || b.choice(BrushPart.CARRY) == PartChoice.SHAPE
                || b.choice(BrushPart.CARRY) == PartChoice.LIFT || b.choice(BrushPart.LIFE) == PartChoice.FADE || b.choice(BrushPart.LIFE) == PartChoice.LEMON
                || b.choice(BrushPart.HAND) == PartChoice.BRUSH_HAND || b.choice(BrushPart.TIP) == PartChoice.STAMP;
    }
    /**
     * Damp tissue lifts and lays nothing (the owner, 2026-10-22: "damp tissue lifts paint but if it touches any
     * light/paper area it leaves a dark mark"): Clear Water, Lay Down 0. Across a page half washed (tone 120, its wash
     * layer), live and quick, at two sizes: no pixel of the paper darker, none of the wash darker, the wash lifted.
     */
    static void tissue() {
        ToolPresets.Preset p = null;
        for (ToolPresets.Preset q : ToolPresets.all()) if (q.name.equals("Damp tissue")) p = q;
        ModularBrush b = p.brush().parts;
        check(b.choice(BrushPart.CARRY) == PartChoice.CLEAR_WATER && b.part(BrushPart.CARRY).get(PartDial.LAY) == 0 && ModularBrush.ownPaint(b.part(BrushPart.CARRY)) == 0, "Damp tissue: Clear Water, no paint of its own, Lay Down 0");
        int W = 600, H = 200;
        for (double size : new double[]{30, 80}) for (Boolean quick : new Boolean[]{false, true}) {
            ToneDocument d = InkWashLayers.blank(W, H);
            d.selectLayer(InkWashLayers.WASH);
            d.begin(1);
            int[] row = new int[W];
            for (int y = 0; y < H; y++) { for (int x = 0; x < W; x++) row[x] = x < W / 2 ? 120 : -1; d.setTones(0, y, W, 1, row); }
            d.finish(); d.clearDirty();
            int[] before = new int[W * H];
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) before[y * W + x] = d.compositeTone(x, y);
            WatercolorSession session = new WatercolorSession();
            QuickStroke.benchAlways = quick;
            try {
                DrawingStroke s = QuickStroke.start(d, b.copy(), size, 1, session);
                // From the paper into the wash and out onto the paper again, carrying what it lifted.
                for (int i = 0; i < 400; i++) { float u = i / 399f; s.sample(40 + 520 * (u < .5f ? 2 * u : 2 - 2 * u) , 100 + (float) (40 * Math.sin(i / 40.0)) + (u < .5f ? -20 : 20), ToolSettings.pressure(.6f), 0, 30, 100_000 + i * 2L); if (i % 8 == 7) s.present(); }
                s.present(); s.finish();
                for (int t = 0; t < 5000 && session.settling(); t++) session.tickDrying(d);
            } finally { QuickStroke.benchAlways = null; }
            int darker = 0, lifted = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int a = before[y * W + x], c = d.compositeTone(x, y);
                if (c < a) darker++;
                if (x < W / 2 && c > a + 40) lifted++;
            }
            check(darker == 0, "Damp tissue at " + size + (quick ? " quick" : " live") + " darkens nothing, paper or wash (" + darker + " pixels darker)");
            check(lifted > size * 100, "Damp tissue at " + size + (quick ? " quick" : " live") + " lifts the wash (" + lifted + " pixels lighter)");
        }
    }
    static void codes() {
        java.util.List<String[]> rows = new java.util.ArrayList<>(java.util.Arrays.asList(ToolPresets.ROWS));
        rows.addAll(java.util.Arrays.asList(ToolPresets.RETIRED));
        for (String[] row : rows) {
            ModularBrush b = ModularCode.decode(row[2]);
            check(b != null, row[1] + "'s code decodes");
            check(b.style == ModularStyle.named(row[0]), row[1] + " is a " + row[0] + " brush");
            // But Hand Hatch's (cut, 2026-10-24), which reads as Hatch: HandHatchChecks.cut.
            check(ModularCode.encode(b).equals(row[2]) || HandHatchChecks.handHatch(row[2]), row[1] + "'s code round-trips");
            check(Long.parseLong(row[4]) > 0, row[1] + " keeps the seed it was rolled with");
            // A Wet preset is built by hand (PresetPictures): its seed is its code's.
            if (b.choice(BrushPart.LIFE) == PartChoice.WET_PAINT) check(Long.toString(Long.parseLong(row[4]), 36).equals(row[2].substring(row[2].lastIndexOf('.') + 1)), row[1] + "'s seed is its code's");
        }
        for (ToolPresets.Preset p : ToolPresets.all()) {
            Brush brush = p.brush();
            check(brush.name().equals(p.name) && brush.parts.style == p.style, p.name + " as a brush keeps its name and style");
            check(brush != p.brush() && brush.sameAs(p.brush()), p.name + " is a fresh copy each time, the same brush");
            check(Brush.read(brush.write()).sameAs(brush), p.name + " is stored and read back as itself");
        }
    }
    static void words() {
        // The narrowest card that shows a line: the finger's chooser's (InkWashBar), CARD_LEAST wide.
        int room = (int) (ToolsLayout.CARD_LEAST - 2 * LabText.CAPTION_INSET);
        for (ToolPresets.Preset p : ToolPresets.all()) {
            // The owner's names stay the owner's: a card cuts a long one with "…" (CardArt), the brush line and This Brush say it whole.
            check(LabText.width(p.line, LabText.CAPTION_SP, 0) <= room && (LabText.width(p.name, LabText.CAPTION_SP, 0) <= room || OWNERS.contains(p.name)), p.name + " / " + p.line + " fit one line of the Nomad's card (" + room + " dp)");
            check(!p.line.isEmpty() && p.line.equals(p.line.toLowerCase(java.util.Locale.ROOT)), p.name + " has a short lower-case description");
        }
    }
    static void draws() {
        ModularBrush stump = null;
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals("Stump")) stump = p.brush().parts;
        ModularBrush.Part carry = stump.part(BrushPart.CARRY);
        check(carry.choice == PartChoice.SMUDGE && carry.get(PartDial.LOAD) == 0 && Math.abs(carry.get(PartDial.PICK) - .7) < .001 && carry.get(PartDial.LAY) == .6
                && stump.choice(BrushPart.MEDIUM) == PartChoice.CHARCOAL && WildSample.smearsMostly(stump), "Stump: Charcoal, Smear, Own paint 0, Pick up .7, Lay down .6; its loop shows over a grey band");
        ModularBrush sumi = null, clear = null;
        for (ToolPresets.Preset p : ToolPresets.all()) { if (p.name.equals("Sumi")) sumi = p.brush().parts; if (p.name.equals("Clear water")) clear = p.brush().parts; }
        check(sumi.choice(BrushPart.LIFE) == PartChoice.FLOAT && sumi.choice(BrushPart.CARRY) == PartChoice.OFF && sumi.choice(BrushPart.MEDIUM) == PartChoice.INK
                && sumi.choice(BrushPart.HAND) == PartChoice.PRESS_DARKER && !ModularWords.clearWater(sumi), "Sumi: Round, Ink, Smudge Off, Float, Press → darker");
        check(clear.choice(BrushPart.CARRY) == PartChoice.SMUDGE && clear.part(BrushPart.CARRY).get(PartDial.LOAD) == 0 && Math.abs(clear.part(BrushPart.CARRY).get(PartDial.PICK) - .9) < .001
                && ModularWords.clearWater(clear) && clear.part(BrushPart.LIFE).sameAs(sumi.part(BrushPart.LIFE)), "Clear water: Sumi with Smear, Own paint 0, Pick up .9");
        for (ToolPresets.Preset p : ToolPresets.all()) {
            int marked = 0;
            for (int px : WildSample.modular(p.brush().parts, 1, WildSample.Shape.LOOP, 120, 80, false)) if (px != 0xffffffff) marked++;
            check(marked > 40, p.name + " draws its loop (" + marked + " pixels)");
        }
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
