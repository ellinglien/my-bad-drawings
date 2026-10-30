package io.github.mpdairy.monopaint;

import java.util.Random;

/**
 * The wet smudge spec's two smudge brushes (docs/archive/superpowers/specs/2026-10-19-wet-smudge-design.md): Smudge's Keeps Paint
 * (Dirty brush: what it picks up it keeps for the next stroke, until Clean Brush) and Smudge's Clear Water (Water brush:
 * lifts, re-wets and lets what it lifted run out, as a wash's water: it settles, runs downhill, nudges what floats; it
 * never moves permanent paint and stops at masking fluid); their codes, rolls and words; and their desktop timings
 * beside Smear's.
 */
public class WetSmudgeChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        codes(); rolls(); words();
        dirty(); clean();
        lightens(); spreads(); permanent(); masking(); settles(); downhill(); stillWet(); onWater(); quick(); blank();
        timings();
        System.out.println("PASS: Keeps Paint is written only when on and never rolled, and Clear Water is Smudge's fifth choice (_4), every older code byte for byte; Clear Water rolls rarely from the seed, every other roll as before; "
                + "a Dirty brush keeps what it picked up for its holder's next stroke, at any size, until Clean Brush; the Water brush lightens a dried wash, spreads what it lifted through its wet, leaves permanent paint and what masking fluid shields as they were, "
                + "stays wet and settles, runs downhill on a tilted page, lifts more from a wash still wet, nudges what floats rather than printing it, ends quick as live and lays nothing on blank paper; " + timing);
    }

    static final int W = 480, H = 360;
    static ModularBrush preset(String name) { return FloatChecks.preset(name); }
    static ModularBrush dirtyBrush() { return preset("Dirty brush"); }
    static ModularBrush waterBrush() { return preset("Water brush"); }
    /** A stroke as the drawing screen starts one (InkWash.stroke): {@code holder}'s, on the water while paint floats, what is wet laid first; quick by the rule. */
    static void stroke(ToneDocument d, WatercolorSession s, String holder, ModularBrush b, double size, double darkness, double[][] path) {
        s.holding(holder);
        ModularBrush parts = s.onWater(d, b);
        s.settleBefore(d, parts);
        s.dry("stroke");
        CarrySmudgeChecks.feed(QuickStroke.start(d, parts, size, darkness, s), path);
    }
    static double[][] line(double x0, double y0, double x1, double y1) { return FloatChecks.line(x0, y0, x1, y1, .8); }
    static double mean(ToneDocument d, int x0, int x1, int y0, int y1) { return CarrySmudgeChecks.mean(d, x0, x1, y0, y1); }
    /** A dried wash over most of the page: Soft wash rows, Slow, settled and dry. */
    static ToneDocument driedWash(WatercolorSession s) {
        ToneDocument d = InkWashLayers.blank(W, H);
        ModularBrush wash = preset("Soft wash");
        for (int y = 70; y <= 290; y += 40) stroke(d, s, HeldPaint.ANY, wash, 110, 1, line(60, y, 420, y));
        s.settleNow(d); s.dry("dried");
        return d;
    }
    static void tick(WatercolorSession s, ToneDocument d) { while (s.tickDrying(d)) { } }

    // ---- codes, rolls, words ----

    static void codes() {
        // Every Basic tool's code reads and writes byte for byte; Smear's and Drag's without Keeps Paint end as before.
        for (ToolPresets.Preset p : ToolPresets.all()) check((ModularCode.encode(ModularCode.decode(p.code)).equals(p.code) || HandHatchChecks.handHatch(p.code)), p.name + "'s code reads and writes byte for byte");
        String smear = "stump.M50i00000000yat_3eqlj00we_3p6ll00_1nl_1jz_8pthh00x9is00000000nz0.51j8ud";
        ModularBrush b = ModularCode.decode(smear);
        check(b.part(BrushPart.CARRY).get(PartDial.KEEPS) == 0 && ModularCode.encode(b).equals(smear), "a Smear code from before reads Keeps Paint off and writes as it was");
        b.part(BrushPart.CARRY).set(PartDial.KEEPS, .9);
        String kept = ModularCode.encode(b);
        check(kept.contains("_3p6ll0000we_") && Math.abs(ModularCode.decode(kept).part(BrushPart.CARRY).get(PartDial.KEEPS) - .9) < 1e-3, "Keeps Paint on is written after Soften: " + kept);
        ModularBrush drag = ModularCode.decode(CarrySmudgeChecks.GOLDENS[5][1].toString());
        drag.part(BrushPart.CARRY).set(PartDial.KEEPS, 1);
        check(ModularCode.decode(ModularCode.encode(drag)).part(BrushPart.CARRY).get(PartDial.KEEPS) == 1, "Drag's Keeps Paint round-trips");
        ModularBrush water = waterBrush();
        check(water.choice(BrushPart.CARRY) == PartChoice.CLEAR_WATER && ModularCode.encode(water).contains("_4") && ModularCode.decode(ModularCode.encode(water)).part(BrushPart.CARRY).sameAs(water.part(BrushPart.CARRY)), "Clear Water is Smudge's choice 4 and round-trips");
        check(PartChoice.of(BrushPart.CARRY, 7) == null && ModularCode.decode(ModularCode.encode(water).replace("_4i0ll_", "_7i0ll_")) == null, "no Smudge choice past Filled Shape");
    }
    static void rolls() {
        int water = 0, n = 0;
        for (ModularStyle s : ModularStyle.all()) for (int i = 0; i < 400; i++) {
            ModularBrush b = s.roll(new Random(9900L * (s.index + 1) + i));
            ModularStyle.waterRolls = false;
            ModularBrush without;
            try { without = s.roll(new Random(9900L * (s.index + 1) + i)); } finally { ModularStyle.waterRolls = true; }
            check(b.part(BrushPart.CARRY).get(PartDial.KEEPS, 0) == 0, "Keeps Paint is never rolled");
            boolean became = b.choice(BrushPart.CARRY) == PartChoice.CLEAR_WATER;
            if (became) { water++; check(s.waterOdds() > 0 && b.choice(BrushPart.LIFE) != PartChoice.FLOAT, s.name + " rolls Clear Water only with odds, never on Float"); }
            if (s.waterOdds() > 0) n++;
            // Every other part as rolled without it, and the smudge too when it isn't Clear Water.
            for (BrushPart part : BrushPart.values()) if (part != BrushPart.CARRY || !became) check(b.part(part).sameAs(without.part(part)), s.name + " " + part + " rolls as before");
        }
        // Anything goes 1 in 40, Watercolor and Ink wash 1 in 50: about 25 in 1200.
        check(water >= 8 && water <= 50, "Clear Water rolls rarely: " + water + " in " + n);
    }
    static void words() {
        check(ModularWords.plainWords(dirtyBrush()).contains("Smears. Keeps paint for the next stroke.") && ModularWords.caption(dirtyBrush()).contains("keeps paint"), "Dirty brush says it keeps paint: " + ModularWords.plainWords(dirtyBrush()));
        check(ModularWords.plainWords(waterBrush()).startsWith("Clear water. Lifts paint, lets it run.") && ModularWords.plainWords(waterBrush()).contains("runs and pools when tilted")
                && ModularWords.caption(waterBrush()).contains("clear water"), "Water brush says it is clear water: " + ModularWords.plainWords(waterBrush()));
        check(ModularWords.choiceWords(waterBrush(), BrushPart.CARRY).equals("Water") && PartChoice.CLEAR_WATER.label.equals("Clear Water") && PartDial.KEEPS.label.equals("Keeps Paint"), "Title Case labels, one word on the module");
        check(ToolSlots.smudges(Brush.of(dirtyBrush())) && ToolSlots.smudges(Brush.of(waterBrush())), "both are smudge brushes, in the smudge tool's chooser");
        check(ToolSlots.smudgeKind(Brush.of(dirtyBrush())) == ToolSlots.SmudgeKind.DIRTY && ToolSlots.smudgeKind(Brush.of(waterBrush())) == ToolSlots.SmudgeKind.WATER
                && ToolSlots.smudgeKind(Brush.of(preset("Blend stump"))) == ToolSlots.SmudgeKind.BLUR, "each has its own icon on the bar");
        ModularBrush onFloat = ClearWater.afloat(waterBrush(), true);
        check(ModularWords.clearWater(onFloat) && onFloat.choice(BrushPart.MEDIUM) == PartChoice.INK && ResolvedBrush.of(onFloat, 40, 1).flt.clear(), "on the water it is Float's clear water");
    }

    // ---- Dirty brush ----

    /** A dark wash band on the left; a Dirty brush stroke through it, then one on blank paper. */
    static ToneDocument band() {
        ToneDocument d = InkWashLayers.blank(W, H);
        CarrySmudgeChecks.lay(d, InkWashLayers.WASH, (x, y) -> x >= 80 && x < 160 && y >= 40 && y < 180 ? 60 : 255);
        return d;
    }
    static void dirty() {
        ToneDocument d = band(), clean = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, "SMUDGE", dirtyBrush(), 60, .5, line(40, 110, 200, 110));
        check(s.held.dirty("SMUDGE") && !s.held.dirty("SMUDGE/finger"), "the brush holds what it picked up, its holder's only");
        // The bar shows it in its paint's grey (the smudge tool's head, a slot's dab): one of the six, darker than paper, and
        // a darker band's paint never shows lighter. A slot's Dirty brush holds its own the same way.
        float picked = s.held.of("SMUDGE").darkness();
        ToneDocument black = InkWashLayers.blank(W, H);
        CarrySmudgeChecks.lay(black, InkWashLayers.WASH, (x, y) -> x >= 80 && x < 160 && y >= 40 && y < 180 ? 0 : 255);
        WatercolorSession k = new WatercolorSession();
        stroke(black, k, "slot0", dirtyBrush(), 60, .5, line(40, 110, 200, 110));
        int grey = DrawLayout.heldGrey(picked), darker = DrawLayout.heldGrey(k.held.of("slot0").darkness());
        check(k.held.dirty("slot0") && picked > .1 && grey < 255 && darker <= grey && java.util.Arrays.stream(NotebookOptions.PAPERS).anyMatch(g -> g == grey),
                "the held paint's grey on the bar: " + grey + " from a 60 band (" + String.format("%.2f", picked) + " dark), " + darker + " from a black one");
        stroke(d, s, "SMUDGE", dirtyBrush(), 60, .5, line(240, 280, 440, 280));
        stroke(clean, new WatercolorSession(), "SMUDGE", dirtyBrush(), 60, .5, line(240, 280, 440, 280));
        double carried = mean(d, 245, 290, 265, 295), none = mean(clean, 245, 290, 265, 295);
        check(none == 255 && carried < 240, "the next stroke lays it on blank paper (" + String.format("%.1f", carried) + ", a clean brush " + none + ")");
        // Off, a smear starts every stroke clean, as before.
        ModularBrush off = dirtyBrush(); off.part(BrushPart.CARRY).set(PartDial.KEEPS, 0);
        ToneDocument e = band();
        WatercolorSession t = new WatercolorSession();
        stroke(e, t, "SMUDGE", off, 60, .5, line(40, 110, 200, 110));
        stroke(e, t, "SMUDGE", off, 60, .5, line(240, 280, 440, 280));
        check(t.held.of("SMUDGE") == null && mean(e, 240, 440, 250, 310) == 255, "Keeps Paint off keeps nothing");
        // Another size: the held paint is scaled to the reservoir.
        ToneDocument f = band();
        WatercolorSession u = new WatercolorSession();
        stroke(f, u, "SMUDGE", dirtyBrush(), 60, .5, line(40, 110, 200, 110));
        stroke(f, u, "SMUDGE", dirtyBrush(), 120, .5, line(240, 280, 440, 280));
        double bigger = mean(f, 245, 300, 250, 310);
        check(bigger < 245, "at a bigger size it still lays it (" + String.format("%.1f", bigger) + ")");
    }
    static void clean() {
        ToneDocument d = band(), after = InkWashLayers.blank(W, H), clean = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, "SMUDGE", dirtyBrush(), 60, .5, line(40, 110, 200, 110));
        check(s.held.clean("SMUDGE") && !s.held.dirty("SMUDGE") && !s.held.clean("SMUDGE"), "Clean Brush empties it (once)");
        stroke(after, s, "SMUDGE", dirtyBrush(), 60, .5, line(240, 280, 440, 280));
        stroke(clean, new WatercolorSession(), "SMUDGE", dirtyBrush(), 60, .5, line(240, 280, 440, 280));
        check(QuickStrokeChecks.same(after, clean), "and the next stroke is a clean brush's");
        // Laid out on blank paper, a dirty brush cleans itself.
        WatercolorSession u = new WatercolorSession();
        ToneDocument g = band();
        stroke(g, u, "SMUDGE", dirtyBrush(), 60, .5, line(40, 110, 200, 110));
        for (int i = 0; i < 8; i++) stroke(g, u, "SMUDGE", dirtyBrush(), 60, .5, line(30, 230 + 12 * i, 450, 230 + 12 * i));
        check(!u.held.dirty("SMUDGE"), "worked out on clean paper it comes clean");
    }

    // ---- Water brush ----

    static void lightens() {
        WatercolorSession s = new WatercolorSession();
        ToneDocument d = driedWash(s);
        double before = mean(d, 220, 260, 90, 270);
        stroke(d, s, "SMUDGE", waterBrush(), 60, .5, line(240, 30, 240, 330));
        tick(s, d);
        double after = mean(d, 220, 260, 90, 270);
        check(after > before + 4, "the Water brush lightens a dried wash where it goes (" + String.format("%.1f → %.1f", before, after) + ")");
    }
    static void spreads() {
        // A dried band of wash; clear water along its edge: what it lifts runs out through the wet, onto paper beside it.
        ToneDocument d = InkWashLayers.blank(W, H);
        CarrySmudgeChecks.lay(d, InkWashLayers.WASH, (x, y) -> x < 240 ? 130 : 255);
        WatercolorSession s = new WatercolorSession();
        byte[][] before = CarrySmudgeChecks.all(d);
        stroke(d, s, "SMUDGE", waterBrush(), 60, .5, line(240, 30, 240, 330));
        tick(s, d);
        double out = mean(d, 246, 270, 80, 280), in = mean(d, 205, 234, 80, 280);
        check(out < 250 && in > 132, "it lifts the edge and runs it out onto the paper beside (" + String.format("inside %.1f, beside %.1f", in, out) + ")");
        check(!ModularStrokeChecks.same(before, CarrySmudgeChecks.all(d)) && mean(d, 330, 470, 0, 359) == 255, "and no further than its wet");
        // Softly: the edge's steepest step is far gentler.
        int steep = 0;
        for (int x = 200; x < 290; x++) steep = Math.max(steep, Math.abs(d.compositeTone(x + 1, 180) - d.compositeTone(x, 180)));
        check(steep < 40, "the edge it ran through is soft (steepest step " + steep + " of 125)");
    }
    static void permanent() {
        // Permanent paint on its own layer (ink lines) and on the wash layer itself (a permanent wash) stays put, byte for byte.
        ToneDocument d = InkWashLayers.blank(W, H);
        d.selectLayer(InkWashLayers.WASH); d.begin(0);
        int[] row = new int[W];
        for (int y = 0; y < H; y++) { for (int x = 0; x < W; x++) row[x] = x < 240 ? 120 : -1; d.setTones(0, y, W, 1, row); }
        d.finish();
        CarrySmudgeChecks.lay(d, InkWashLayers.INK, (x, y) -> Math.floorMod(x, 40) < 4 ? 0 : 255);
        byte[][] before = CarrySmudgeChecks.all(d);
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, "SMUDGE", waterBrush(), 80, .5, line(60, 60, 420, 300));
        tick(s, d);
        check(ModularStrokeChecks.same(before, CarrySmudgeChecks.all(d)), "the Water brush doesn't move permanent paint: ink lines and a permanent wash stay byte for byte");
        // A wash that is part permanent (Erasable .4) loses only its loose part, however often the water passes: its
        // permanent coverage stays, and it keeps at least the 60% the eraser can't take.
        WatercolorSession t = new WatercolorSession();
        ToneDocument e = driedWash(t);
        int[] keptBefore = ErasableChecks.kept(e, InkWashLayers.WASH);
        long d0 = ErasableChecks.dark(e);
        for (int k = 0; k < 6; k++) { stroke(e, t, "SMUDGE", waterBrush(), 60, .5, line(200 + 16 * k, 30, 200 + 16 * k, 330)); tick(t, e); }
        int[] keptAfter = ErasableChecks.kept(e, InkWashLayers.WASH);
        int lost = 0;
        for (int i = 0; i < keptBefore.length; i++) if (keptAfter[i] < keptBefore[i]) lost++;
        double keeps = ErasableChecks.dark(e) / (double) d0;
        check(lost == 0 && keeps > .58, "it lifts only what erases: no permanent coverage lost (" + lost + " px), a wash kept at " + String.format("%.0f%%", 100 * keeps));
    }
    static void erase(ToneDocument d) {
        EraseBothStroke e = new EraseBothStroke(d, 220f);
        for (int y = 40; y <= 320; y += 4) e.sample(240, y, 1, 0, 0, y);
        e.finish();
    }
    static void masking() {
        WatercolorSession s = new WatercolorSession();
        ToneDocument d = driedWash(s);
        // Masking fluid across the middle, then the Water brush through it.
        stroke(d, s, HeldPaint.ANY, preset("Masking fluid"), 40, 1, line(60, 180, 420, 180));
        int[] shielded = new int[W * H];
        int n = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (d.maskShield(x, y) >= 250) { shielded[n++] = y * W + x; }
        check(n > 2000, "masking fluid laid (" + n + " px)");
        int[] tones = new int[n];
        for (int i = 0; i < n; i++) tones[i] = d.compositeTone(shielded[i] % W, shielded[i] / W);
        stroke(d, s, "SMUDGE", waterBrush(), 80, .5, line(240, 30, 240, 330));
        tick(s, d);
        int moved = 0;
        for (int i = 0; i < n; i++) if (d.compositeTone(shielded[i] % W, shielded[i] / W) != tones[i]) moved++;
        check(moved == 0, "masking fluid keeps the water off: " + moved + " of " + n + " shielded pixels changed");
        check(mean(d, 225, 255, 90, 150) > mean(d, 140, 170, 90, 150) + 3, "while it lifts the wash beside the fluid");
    }
    static void settles() {
        WatercolorSession s = new WatercolorSession();
        ToneDocument d = driedWash(s);
        stroke(d, s, "SMUDGE", waterBrush(), 60, .5, line(240, 30, 240, 330));
        check(s.wet() && s.drying() != null, "a Slow Water brush stays wet after lift, as a wash does");
        byte[][] lifted = CarrySmudgeChecks.all(d);
        int ticks = 0;
        while (s.tickDrying(d)) ticks++;
        check(ticks > 10 && !ModularStrokeChecks.same(lifted, CarrySmudgeChecks.all(d)), "and settles as it dries (" + ticks + " ticks)");
        // Quick: done at lift.
        ModularBrush quick = waterBrush(); quick.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.FAST_WET));
        WatercolorSession q = new WatercolorSession();
        ToneDocument e = driedWash(q);
        stroke(e, q, "SMUDGE", quick, 60, .5, line(240, 30, 240, 330));
        check(!q.wet(), "a Quick one is done at lift");
    }
    /** The darkness-weighted mean row of the box {@code x0..x1} by rows {@code y0..y1}. */
    static double centre(ToneDocument d, int x0, int x1, int y0, int y1) {
        double s = 0, w = 0;
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) { double k = 255 - d.compositeTone(x, y); s += k * y; w += k; }
        return s / w;
    }
    static void downhill() {
        WatercolorSession level = new WatercolorSession(), tilted = new WatercolorSession();
        ToneDocument a = driedWash(level), b = driedWash(tilted);
        tilted.tilt = () -> PageTilt.of(0, 1);
        stroke(a, level, "SMUDGE", waterBrush(), 60, .5, line(240, 60, 240, 300));
        stroke(b, tilted, "SMUDGE", waterBrush(), 60, .5, line(240, 60, 240, 300));
        check(tilted.drying() != null && tilted.drying().moves(), "on a tilted page its wet moves with the tilt");
        tick(level, a); tick(tilted, b);
        double ya = centre(a, 215, 265, 20, 359), yb = centre(b, 215, 265, 20, 359);
        int uphill = 0;
        for (int y = 0; y < 20; y++) for (int x = 0; x < W; x++) if (a.compositeTone(x, y) != b.compositeTone(x, y)) uphill++;
        check(yb > ya + .5 && uphill == 0, "on a tilted page it runs downhill (the paint's centre " + String.format("%.1f → %.1f", ya, yb) + "), nothing above it moves");
    }
    static void stillWet() {
        // The same Slow wash, still wet when the water comes, and dried first: the water lifts more from the wet one.
        // At Erasable .4, set by hand (a wash that dries in part permanent; every brush starts at 1 since 2026-10-22).
        ModularBrush wash = ErasableChecks.erasable(preset("Slow bloom"), .4);
        WatercolorSession sw = new WatercolorSession(), sd = new WatercolorSession();
        ToneDocument wet = InkWashLayers.blank(W, H), dry = InkWashLayers.blank(W, H);
        stroke(wet, sw, HeldPaint.ANY, wash, 200, 1, line(80, 180, 400, 180));
        stroke(dry, sd, HeldPaint.ANY, wash, 200, 1, line(80, 180, 400, 180));
        check(sw.wet(), "(the wash is still wet)");
        sd.settleNow(dry); sd.dry("dried"); dry.selectLayer(InkWashLayers.INK); dry.selectLayer(InkWashLayers.WASH);
        check(sw.stillWet(wet) == null, "(nothing was laid yet)");
        stroke(wet, sw, "SMUDGE", waterBrush(), 60, .5, line(240, 60, 240, 300));
        stroke(dry, sd, "SMUDGE", waterBrush(), 60, .5, line(240, 60, 240, 300));
        tick(sw, wet); tick(sd, dry);
        double lw = mean(wet, 225, 255, 150, 210), ld = mean(dry, 225, 255, 150, 210);
        check(lw > ld + 2, "it lifts more from a wash still wet (" + String.format("%.1f against %.1f dried", lw, ld) + ")");
    }
    static void onWater() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        FloatChecks.rings(d, s, 200, 180, 3);
        FloatWater w = s.floating();
        int strokes = w.bath.strokes();
        float[] box = w.bath.rings.get(0).box.clone();
        stroke(d, s, "SMUDGE", waterBrush(), 40, .5, line(120, 180, 300, 180));
        check(s.floating() == w && w.bath.strokes() == strokes + 1, "while paint floats, the Water brush works on the water: it joins the bath rather than printing it");
        check(!java.util.Arrays.equals(box, w.bath.rings.get(0).box), "and nudges what floats along");
    }
    static void quick() {
        WatercolorSession sa = new WatercolorSession(), sb = new WatercolorSession();
        ToneDocument a = driedWash(sa), b = driedWash(sb);
        sa.holding("SMUDGE"); sa.settleBefore(a, waterBrush());
        CarrySmudgeChecks.feed(QuickStroke.quickly(a, waterBrush(), 60, .5, sa), CarrySmudgeChecks.wave());
        sb.holding("SMUDGE"); sb.settleBefore(b, waterBrush());
        CarrySmudgeChecks.feed(ModularStroke.start(b, waterBrush(), 60, .5, sb), CarrySmudgeChecks.wave());
        sa.settleNow(a); sb.settleNow(b);
        check(QuickStrokeChecks.same(a, b), "the Water brush quick ends as live");
        // And the Dirty brush keeps the same paint either way.
        WatercolorSession qa = new WatercolorSession(), qb = new WatercolorSession();
        ToneDocument c = band(), e = band();
        qa.holding("SMUDGE"); qb.holding("SMUDGE");
        CarrySmudgeChecks.feed(QuickStroke.quickly(c, dirtyBrush(), 60, .5, qa), line(40, 110, 200, 110));
        CarrySmudgeChecks.feed(ModularStroke.start(e, dirtyBrush(), 60, .5, qb), line(40, 110, 200, 110));
        HeldPaint.Reservoir ra = qa.held.of("SMUDGE"), rb = qb.held.of("SMUDGE");
        check(QuickStrokeChecks.same(c, e) && ra != null && java.util.Arrays.equals(ra.p, rb.p), "the Dirty brush quick ends as live, holding the same paint");
    }
    static void blank() {
        ToneDocument d = InkWashLayers.blank(W, H);
        byte[][] before = CarrySmudgeChecks.all(d);
        WatercolorSession s = new WatercolorSession();
        stroke(d, s, "SMUDGE", waterBrush(), 60, .5, CarrySmudgeChecks.wave());
        tick(s, d);
        check(ModularStrokeChecks.same(before, CarrySmudgeChecks.all(d)), "on blank paper the Water brush lays nothing");
        stroke(d, s, "SMUDGE", dirtyBrush(), 60, .5, CarrySmudgeChecks.wave());
        check(ModularStrokeChecks.same(before, CarrySmudgeChecks.all(d)) && !s.held.dirty("SMUDGE"), "nor a clean Dirty brush");
    }

    // ---- timings ----

    static String timing = "";
    /** Smear (the Stump), the Dirty brush and the Water brush over marks at 60 and 120 px, live: ms a sample and the lift, best of three. */
    static void timings() {
        StringBuilder out = new StringBuilder("desktop, ms a sample / lift:");
        ModularBrush stump = preset("Stump");
        // The same smear on the wash layer, as the two wet smudges are (Stump is a charcoal, on the ink layer).
        ModularBrush smear = dirtyBrush(); smear.part(BrushPart.CARRY).set(PartDial.KEEPS, 0);
        double[] worst = new double[2];
        for (int size : new int[]{60, 120}) {
            double[][] t = new double[3][];
            ModularBrush[] brushes = {smear, dirtyBrush(), waterBrush()};
            for (int k = 0; k < 3; k++) t[k] = time(brushes[k], size);
            out.append(String.format(java.util.Locale.ROOT, " %d px Smear %.3f / %.1f, Dirty brush %.3f / %.1f, Water brush %.3f / %.1f;", size, t[0][0], t[0][1], t[1][0], t[1][1], t[2][0], t[2][1]));
            worst[0] = Math.max(worst[0], t[2][0] / t[0][0]);
            Timing.limit(t[2][0] <= 2 * t[0][0] + .02 && t[1][0] <= 2 * t[0][0] + .02, "the wet smudges draw about as fast as Smear at " + size + " px");
            Timing.limit(t[2][1] < 60, "the Water brush's lift within 60 ms at " + size + " px: " + t[2][1]);
        }
        if (stump == null) throw new AssertionError();
        timing = out.toString();
    }
    static double[] time(ModularBrush b, int size) {
        double best = Double.MAX_VALUE, lift = Double.MAX_VALUE;
        double[][] path = CarrySmudgeChecks.wave();
        for (int round = 0; round < 4; round++) {
            WatercolorSession s = new WatercolorSession();
            ToneDocument d = InkWashLayers.blank(W, H);
            CarrySmudgeChecks.lay(d, InkWashLayers.WASH, CarrySmudgeChecks::mark);
            s.holding("SMUDGE");
            ModularStroke st = ModularStroke.start(d, b, size, .5, s);
            long t0 = System.nanoTime();
            for (int i = 0; i < path.length; i++) {
                double[] p = path[i];
                st.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), 0, 0, (long) p[3]);
                if (i % 3 == 2) st.present();
            }
            st.present();
            long t1 = System.nanoTime();
            st.finish();
            long t2 = System.nanoTime();
            if (round == 0) continue;
            best = Math.min(best, (t1 - t0) / 1e6 / path.length);
            lift = Math.min(lift, (t2 - t1) / 1e6);
        }
        return new double[]{best, lift};
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
