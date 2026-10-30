package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/**
 * Erasable (spec docs/archive/superpowers/specs/2026-10-16-erasable-design.md): the Medium dial a chosen kind and Smudge respect
 * (the Eraser on Everything takes all since 2026-10-22). Pencil, ink over it, Dry Media: the ink alone, pixel for pixel; a wash erases in part; Smudge moves only what erases;
 * wet paint after lift keeps its brush's value; quick and live end alike; Undo and Redo bring the shares back; codes and
 * rolls are as before with the dial at 1. The file format's checks are {@code ErasableFileChecks} (test_document.sh).
 */
public final class ErasableChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        codes(); rolls(); words(); workflow(); orders(); partly(); oldPages(); smudge(); wetPaint(); quick(); cores(); undo(); slots();
        System.out.println("PASS: Erasable: a Sketch pencil drawing inked with Steady line and erased with Dry Media is the ink drawn alone, Everything takes the ink too, every byte of the ink layer, in either order; "
                + "a Soft wash keeps 60% of its darkness and gouache 70% of its coverage; pages and brushes from before erase to paper and grow no shares; "
                + "Smear, Drag and Lift move only what erases (permanent ink stays as it was, pencil moves, a Lift takes at most 40% of a wash); "
                + "a Slow wash's settle, gravity's drips and a Float print keep their brush's value; quick ends as live, shares and all, and several cores as one; "
                + "Undo and Redo bring strokes and erasing back byte for byte, shares too, a tile from before its layer had a share at 255; "
                + "old codes byte for byte, the dial written only when not 1 (after charcoal's dust), every Basic tool and new brush at 1, 2,000 rolls hash as before, "
                + "no roll, die or variation changes it (Resist's wax or fluid apart); the words say it only off 1; stored brushes go to 1 once, wax kept");
    }

    static final int W = 480, H = 360;

    // ---- helpers ----

    /**
     * {@code b} at its real medium's Erasable, set by hand as the owner may: dried ink permanent, a wash .4, gouache .3,
     * Oils' paint off, else 1 (what a roll and every Basic tool gave until 2026-10-22; now they all start at 1). Returns it.
     */
    static ModularBrush real(ModularBrush b) {
        ModularBrush.Part m = b.part(BrushPart.MEDIUM);
        if (m.has(PartDial.ERASABLE) && m.choice != PartChoice.RESIST) m.set(PartDial.ERASABLE, realValue(b.style, m.choice));
        return b;
    }
    static double realValue(ModularStyle style, PartChoice medium) {
        return medium == PartChoice.INK ? 0 : medium == PartChoice.WASH ? .4 : medium == PartChoice.COVER ? style.name.equals("Oils") ? 0 : .3 : 1;
    }
    /** {@code b} with Erasable 1, as every brush was before the dial; returns it. */
    static ModularBrush allErasable(ModularBrush b) {
        ModularBrush.Part m = b.part(BrushPart.MEDIUM);
        if (m.has(PartDial.ERASABLE)) m.set(PartDial.ERASABLE, 1);
        return b;
    }
    /** {@code name}'s Basic tool at its real medium's Erasable ({@link #real}): the rule's checks draw permanent ink and part-erasable paint. */
    static ModularBrush preset(String name) { return real(GravityChecks.preset(name)); }
    static ModularBrush erasable(ModularBrush b, double e) { b.part(BrushPart.MEDIUM).set(PartDial.ERASABLE, e); return b; }
    static void stroke(ToneDocument d, ModularBrush b, double size, double[][] path) { CarrySmudgeChecks.live(d, b, size, 1, null, path); }
    static double[][] across(double x0, double x1, double y) { return CarrySmudgeChecks.across(x0, x1, y); }
    /** A pencil's hatching: six diagonal lines and a scribble. */
    static void sketch(ToneDocument d, ModularBrush pencil) {
        for (int k = 0; k < 6; k++) {
            double[][] p = new double[61][];
            for (int i = 0; i <= 60; i++) { double u = i / 60.0; p[i] = new double[]{60 + 60 * k + 80 * u, 60 + 240 * u, .6 + .3 * Math.sin(Math.PI * u), i * 6}; }
            stroke(d, pencil, 6, p);
        }
        stroke(d, pencil, 6, CarrySmudgeChecks.wave());
    }
    /** An ink line: a wave across the sketch. */
    static void ink(ToneDocument d, ModularBrush ink) {
        double[][] p = new double[81][];
        for (int i = 0; i <= 80; i++) { double u = i / 80.0; p[i] = new double[]{30 + 420 * u, 300 - 240 * u + 30 * Math.sin(5 * u), .8, i * 6}; }
        stroke(d, ink, 4, p);
    }
    /**
     * The Erasable rule over every pixel of every layer ({@link ToneDocument#eraseKinds} 0), one Undo step: what a pixel
     * keeps where a chosen kind meets another kind's permanent paint, and MonoPaint's erasers. The Eraser on Everything
     * no longer keeps anything (the owner, 2026-10-22: "Pen isn't erased by eraser (everything)"): {@link #erase}.
     */
    static void eraseAll(ToneDocument d) {
        byte[] all = new byte[d.width * d.height]; Arrays.fill(all, (byte) 1);
        int was = d.activeLayer(); boolean any = false;
        for (int i = 0; i < d.layerCount(); i++) for (int p = -1; p < ToneDocument.PLANES; p++) { d.selectLayer(i); if (!d.beginPlane(p, false)) continue; d.eraseTones(0, 0, d.width, d.height, all); any = any ? d.finishWithPrevious() || true : d.finish(); }
        d.selectLayer(was);
    }
    /** The eraser taking {@code kinds} ({@link EraseKind}; 0: Everything) over every pixel of the page: a finger 160 px wide, row after row. */
    static void erase(ToneDocument d, int kinds) {
        EraseBothStroke s = new EraseBothStroke(d, 160f, kinds);
        for (int y = -40; y <= d.height + 40; y += 50) for (int x = -60; x <= d.width + 60; x += 20) s.sample(x, y, 1f, Float.NaN, Float.NaN);
        s.finish();
    }
    /** Layer {@code i}'s tones and coverages as it looks (its kind planes laid on its base), then its base's and each plane's shares (255 everywhere without one). */
    static byte[][] layer(ToneDocument d, int i) {
        ToneDocument.Layer l = d.layerSnapshot().layers.get(i), look = l.flattened();
        byte[][] out = new byte[3 + ToneDocument.PLANES][];
        out[0] = look.tones.clone(); out[1] = look.alpha.clone();
        for (int p = -1; p < ToneDocument.PLANES; p++) {
            ToneDocument.Layer part = p < 0 ? l : l.plane(p);
            out[3 + p] = part != null && part.share != null ? part.share.clone() : ToneDocument.paper(d.width, d.height);
        }
        return out;
    }
    /** Layer {@code i}'s base and kind planes. */
    static java.util.List<ToneDocument.Layer> parts(ToneDocument d, int i) {
        ToneDocument.Layer l = d.layerSnapshot().layers.get(i);
        java.util.List<ToneDocument.Layer> out = new java.util.ArrayList<>();
        out.add(l);
        for (int p = 0; p < ToneDocument.PLANES; p++) if (l.plane(p) != null) out.add(l.plane(p));
        return out;
    }
    static boolean same(byte[][] a, byte[][] b) { for (int i = 0; i < a.length; i++) if (!Arrays.equals(a[i], b[i])) return false; return true; }
    /** How many pixels differ between two layers' tones and coverages. */
    static int differ(byte[][] a, byte[][] b) { int n = 0; for (int i = 0; i < a[0].length; i++) if (a[0][i] != b[0][i] || a[1][i] != b[1][i]) n++; return n; }
    /** The page's darkness over white: the sum of 255 − its tones. */
    static long dark(ToneDocument d) { long s = 0; for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) s += 255 - d.compositeTone(x, y); return s; }
    /** Layer {@code i}'s total coverage. */
    static long coverage(ToneDocument d, int i) { long s = 0; for (ToneDocument.Layer l : parts(d, i)) for (byte a : l.alpha) s += a & 255; return s; }
    /** Layer {@code i}'s kept coverage at each pixel ({@link ToneDocument#kept}): its base's and planes' as sheets over each other. */
    static int[] kept(ToneDocument d, int i) {
        int[] k = new int[d.width * d.height];
        for (ToneDocument.Layer l : parts(d, i)) if (l.share != null) for (int p = 0; p < k.length; p++) {
            int q = ToneDocument.kept(l.alpha[p] & 255, l.share[p] & 255);
            k[p] = q + (k[p] * (255 - q) + 127) / 255;
        }
        return k;
    }
    static boolean hasShare(ToneDocument d, int i) { for (ToneDocument.Layer l : parts(d, i)) if (l.share != null) return true; return false; }

    // ---- codes and rolls ----

    static void codes() {
        // Every code from before reads Erasable 1 and writes back byte for byte.
        java.util.List<String> olds = new java.util.ArrayList<>(Arrays.asList(ModularCodeChecks.FIXTURES));
        for (String[] b : LegacyBrushes.BUILTINS) olds.add(b[2]);
        for (Object[] g : CarrySmudgeChecks.GOLDENS) olds.add((String) g[1]);
        olds.add("f.M70i000000000at_000zz0000000000000_3wejs00_000_3lli0i0_0000000000000000000nz0.7");
        for (String c : olds) {
            ModularBrush b = ModularCode.decode(c);
            check(b != null && ModularCode.encode(b).equals(c) && b.part(BrushPart.MEDIUM).get(PartDial.ERASABLE) == 1, "An old code reads Erasable 1 and writes back byte for byte: " + c);
        }
        // The spec's examples: Steady line permanent (by hand), a charcoal at .9 after its dust, a wash at .4; the Basic tool erases clean, its code the prototype's.
        String steady = ModularCode.encode(preset("Steady line")), messy = ModularCode.encode(erasable(preset("Messy stick"), .9)), wash = ModularCode.encode(preset("Soft wash"));
        check(steady.equals("steady-line.M00il00000000at_000zz000000000000000_0_00r_1jz_84w0000000000000000nz0.51j8ud"), "Steady line permanent: " + steady);
        for (ToolPresets.Preset p : ToolPresets.all()) {
            ModularBrush.Part m = ModularCode.decode(p.code).part(BrushPart.MEDIUM);
            check(!m.has(PartDial.ERASABLE) || m.choice == PartChoice.RESIST || m.get(PartDial.ERASABLE) == 1, "Every Basic tool erases clean (Resist's is wax or fluid): " + p.name);
        }
        check(ModularCode.encode(GravityChecks.preset("Steady line")).equals("steady-line.M00il00000000at_000zz0000000000000_0_00r_1jz_84w0000000000000000nz0.51j8ud"), "Steady line's code is the prototype's again");
        check(messy.split("_")[1].equals("3nbcw00we") && wash.split("_")[1].equals("1wva200000000ee"), "Charcoal at .9 writes Dust then Erasable, a wash .4: " + messy + " " + wash);
        // Written only when not 1; read back within half a step.
        for (PartChoice m : BrushPart.MEDIUM.choices()) {
            // Screentone has no Erasable of its own: it always erases (ScreenChecks).
            if (!m.has(PartDial.ERASABLE)) continue;
            ModularBrush b = preset("Steady line");
            b.set(BrushPart.MEDIUM, ModularBrush.switchTo(b.part(BrushPart.MEDIUM), m));
            erasable(b, 1);
            String at1 = ModularCode.encode(b);
            erasable(b, .37);
            ModularBrush back = ModularCode.decode(ModularCode.encode(b));
            String seg1 = at1.split("_")[1], seg = ModularCode.encode(b).split("_")[1];
            check(seg.startsWith(seg1) && seg.length() == seg1.length() + (m == PartChoice.CHARCOAL ? 4 : 2) && Math.abs(back.part(BrushPart.MEDIUM).get(PartDial.ERASABLE) - .37) < .5 / ModularCode.STEPS
                    && ModularCode.encode(back).equals(ModularCode.encode(b)), m + ": Erasable is written only when not 1, two digits last (charcoal's after its dust), read back within half a step");
        }
    }

    /** The CRC of 2,000 rolls' codes (style i mod 14, {@code new Random(31337 + i)}) from before Erasable (inkwash-v2-lab 8d52808). */
    static final String ROLLS_BEFORE = "b96c79b2";
    static void rolls() {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        // The styles from before Print (which rolls a Screen every time).
        java.util.List<ModularStyle> all = ModularStyle.all().subList(0, 14);
        boolean fresh = true;
        for (int i = 0; i < 2000; i++) {
            ModularStyle s = all.get(i % all.size());
            // Stipple, Hatch, Blend, Halo, Screen, Screentone, Clear Water, Flick, Fling, the Filbert, Paint Load, Fading and Lemon Juice, rolled later from the seed, are off here (StippleChecks, HatchChecks, BlendChecks, ScreenChecks
            // and HaloChecks show they change nothing else), and names keep their adjective (BrushNamesChecks: only the first word changes).
            ModularStyle.patternRolls = false; ModularStyle.blendRolls = false; ModularStyle.haloRolls = false; ModularStyle.coreRolls = false; ModularStyle.screenRolls = false; ModularStyle.toneRolls = false; ModularStyle.waterRolls = false; ModularStyle.flickRolls = false; ModularStyle.flingRolls = false; ModularStyle.oilRolls = false; ModularStyle.fadeRolls = false; ModularStyle.lemonRolls = false; BrushNames.traitNames = false;
            ModularBrush b;
            try {
                b = s.roll(new Random(31337 + i));
            } finally { ModularStyle.patternRolls = true; ModularStyle.blendRolls = true; ModularStyle.haloRolls = true; ModularStyle.coreRolls = true; ModularStyle.screenRolls = true; ModularStyle.toneRolls = true; ModularStyle.waterRolls = true; ModularStyle.flickRolls = true; ModularStyle.flingRolls = true; ModularStyle.oilRolls = true; ModularStyle.fadeRolls = true; ModularStyle.lemonRolls = true; BrushNames.traitNames = true; }
            fresh &= b.part(BrushPart.MEDIUM).get(PartDial.ERASABLE, 1) == 1;
            crc.update(ModularCode.encode(b).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        check(fresh, "A brush rolled new starts at Erasable 1");
        check(Long.toHexString(crc.getValue()).equals(ROLLS_BEFORE), "2,000 rolls are as before Erasable, at 1 (" + Long.toHexString(crc.getValue()) + ")");
        // No roll changes it (the owner, 2026-10-22: "the randomizer leaves that var alone unless the user moves it"):
        // Randomize, a style picked, the slot's dice, the Medium's die, the dial (no die), variations; with every roll on.
        boolean kept = true;
        int rolled = 0;
        for (ModularStyle s : ModularStyle.all()) for (int i = 0; i < 30; i++) {
            ModularBrush mine = s.roll(new Random(900 + i));
            if (mine.choice(BrushPart.MEDIUM) == PartChoice.RESIST || !mine.part(BrushPart.MEDIUM).has(PartDial.ERASABLE)) continue;
            erasable(mine, .25);
            java.util.List<ModularBrush> after = new java.util.ArrayList<>();
            after.add(s.roll(new Random(i), mine, new ModularLocks()));
            after.add(ModularStyle.named("Pencil").roll(new Random(i), mine, new ModularLocks()));
            after.add(ToolSlots.rolled(Brush.of(mine), new Random(i)).parts);
            after.add(s.rollOnePart(mine, BrushPart.MEDIUM, new ModularLocks(), new Random(i)));
            after.add(s.rollOneDial(mine, PartDial.ERASABLE, new Random(i)));
            for (ModularVariations.Made v : ModularVariations.make(mine, new ModularLocks(), new Random(i))) after.add(v.brush);
            for (ModularBrush a : after) {
                ModularBrush.Part m = a.part(BrushPart.MEDIUM);
                if (!m.has(PartDial.ERASABLE) || m.choice == PartChoice.RESIST) continue;
                rolled++;
                kept &= m.get(PartDial.ERASABLE) == .25;
            }
        }
        check(kept && rolled > 2000, "No roll changes Erasable: Randomize, a style, the dice, the Medium's die, the dial, variations (" + rolled + ")");
        // Resist's Erasable is wax or fluid: its own roll, and its die, as before.
        ModularBrush fluid = GravityChecks.preset("Masking fluid");
        boolean flips = false;
        for (int i = 0; i < 20; i++) flips |= fluid.style.rollOneDial(fluid, PartDial.ERASABLE, new Random(i)).part(BrushPart.MEDIUM).get(PartDial.ERASABLE) != fluid.part(BrushPart.MEDIUM).get(PartDial.ERASABLE);
        check(flips, "Resist's die still picks wax or fluid");
        for (ModularStyle s : ModularStyle.all()) for (PartChoice m : BrushPart.MEDIUM.choices())
            check(s.erasable(m) == 1 && m.erasable() == 1, "Every medium starts at 1, Oils' paint too: " + s.name + " " + m);
        // A medium chosen keeps the value; to or from Resist, 1.
        ModularBrush pencil = erasable(GravityChecks.preset("HB pencil"), .25);
        check(ModularBrush.switchTo(pencil.part(BrushPart.MEDIUM), PartChoice.INK).get(PartDial.ERASABLE) == .25
                && ModularBrush.switchTo(pencil.part(BrushPart.MEDIUM), PartChoice.WASH).get(PartDial.ERASABLE) == .25, "A medium chosen keeps its Erasable");
        ModularBrush.Part wax = ModularBrush.switchTo(pencil.part(BrushPart.MEDIUM), PartChoice.RESIST);
        wax.set(PartDial.ERASABLE, 0);
        check(ModularBrush.switchTo(pencil.part(BrushPart.MEDIUM), PartChoice.RESIST).get(PartDial.ERASABLE) == 1
                && ModularBrush.switchTo(wax, PartChoice.INK).get(PartDial.ERASABLE) == 1, "To or from Resist, 1 (its dial is wax or fluid)");
    }

    static void words() {
        ModularBrush fine = GravityChecks.preset("Steady line"), pencil = GravityChecks.preset("HB pencil"), wash = GravityChecks.preset("Soft wash");
        check(ModularWords.erasable(fine).isEmpty() && ModularWords.erasable(pencil).isEmpty() && ModularWords.erasable(wash).isEmpty(), "Nothing said at 1, every medium's own");
        erasable(fine, 0); erasable(pencil, 0); erasable(wash, .7);
        check(ModularWords.erasable(fine).equals("Permanent") && ModularWords.erasable(pencil).equals("Permanent") && ModularWords.erasable(wash).equals("Part erasable"), "Permanent, Part erasable");
        check(ModularWords.caption(fine).contains("permanent") && ModularWords.caption(pencil).contains("permanent") && ModularWords.caption(wash).contains("part erasable"), "The caption's tag: " + ModularWords.caption(pencil));
        check(ModularWords.plainWords(fine).contains(" Permanent.") && ModularWords.plainWords(wash).contains(" Part erasable."), "\"This brush\": " + ModularWords.plainWords(fine) + " / " + ModularWords.plainWords(wash));
        check(PartDial.ERASABLE.label.equals("Erasable") && PartDial.ERASABLE.shown(0).equals("off") && PartDial.ERASABLE.shown(.4).equals("0.40") && PartDial.ERASABLE.shown(1).equals("1.00"), "The dial reads off, 0.40, 1.00");
        ToolPresets.Preset sketch = null;
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals("Sketch pencil")) sketch = p;
        ModularBrush s = sketch.brush().parts;
        check(sketch.style.name.equals("Pencil") && sketch.line.equals("erases clean") && s.choice(BrushPart.MEDIUM) == PartChoice.GRAPHITE && PartDial.grade(s.part(BrushPart.MEDIUM).get(PartDial.GRADE)).equals("H")
                && s.choice(BrushPart.PAPER) == PartChoice.PLAIN && s.choice(BrushPart.HAND) == PartChoice.PRESS_DARKER && s.part(BrushPart.MEDIUM).get(PartDial.ERASABLE) == 1,
                "Sketch pencil: Graphite H, smooth paper, Press dark, erases clean");
    }

    // ---- the eraser ----

    /**
     * The workflow: a Sketch pencil drawing, Steady line over it, every pixel erased with Dry Media (the owner, 2026-10-22:
     * the Eraser on Everything takes the ink too): the ink drawn alone, every byte of the ink layer.
     */
    static void workflow() {
        ModularBrush pencil = preset("Sketch pencil"), ink = preset("Steady line");
        ToneDocument d = InkWashLayers.blank(W, H), alone = InkWashLayers.blank(W, H);
        sketch(d, pencil);
        check(!hasShare(d, InkWashLayers.INK), "Pencil that erases clean gives the layer no share");
        long sketched = dark(d);
        ink(d, ink); ink(alone, ink);
        check(differ(layer(d, InkWashLayers.INK), layer(alone, InkWashLayers.INK)) > 3000, "The sketch is on the page under the ink");
        erase(d, ToneDocument.KIND_PENCIL);
        byte[][] a = layer(d, InkWashLayers.INK), b = layer(alone, InkWashLayers.INK);
        note(String.format(Locale.ROOT, "workflow: pencil darkness %d, %d px of ink layer differ after erasing", sketched, differ(a, b)));
        check(same(a, b), "Pencil, ink over it, erased: the ink layer is the ink drawn alone, every byte (" + differ(a, b) + " px differ)");
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) check(d.compositeTone(x, y) == alone.compositeTone(x, y), "and the page shows it");
        // A second pass takes nothing more.
        byte[][] once = layer(d, InkWashLayers.INK);
        erase(d, ToneDocument.KIND_PENCIL);
        check(same(once, layer(d, InkWashLayers.INK)), "What is left is permanent: a second pass takes nothing");
        // Everything takes all of it, the permanent ink too.
        erase(d, 0);
        check(dark(d) == 0 && coverage(d, InkWashLayers.INK) == 0, "The Eraser on Everything takes permanent ink to paper");
    }
    /** Both orders on the ink layer: erasable over permanent and permanent over erasable. */
    static void orders() {
        ModularBrush pencil = preset("Sketch pencil"), soft = preset("5B pencil"), ink = preset("Steady line");
        ToneDocument over = InkWashLayers.blank(W, H), alone = InkWashLayers.blank(W, H);
        ink(over, ink); ink(alone, ink);
        sketch(over, soft);
        check(differ(layer(over, InkWashLayers.INK), layer(alone, InkWashLayers.INK)) > 3000, "Pencil over ink shows");
        eraseAll(over);
        check(same(layer(over, InkWashLayers.INK), layer(alone, InkWashLayers.INK)), "Erasable over permanent, erased: the permanent mark again, every byte");
        // Part erasable: a charcoal at .9 over a permanent line and under it.
        ToneDocument mixed = InkWashLayers.blank(W, H);
        ModularBrush coal = erasable(preset("Soft charcoal"), .9);
        stroke(mixed, coal, 30, across(40, 440, 180));
        ink(mixed, ink);
        long before = dark(mixed);
        eraseAll(mixed);
        long after = dark(mixed), inkOnly = dark(alone);
        note(String.format(Locale.ROOT, "charcoal .9 under ink: darkness %d → %d (ink alone %d)", before, after, inkOnly));
        check(after > inkOnly && after < inkOnly + (before - inkOnly) * .2, "Charcoal at .9 leaves a faint ghost beside the ink");
        check(pencil.part(BrushPart.MEDIUM).get(PartDial.ERASABLE) == 1 && preset("Soft charcoal").part(BrushPart.MEDIUM).get(PartDial.ERASABLE) == 1, "the Sketch pencil and Soft charcoal erase clean");
    }
    /** A wash at .4 keeps 60% of its darkness; gouache at .3 70% of its coverage. */
    static void partly() {
        ModularBrush wash = preset("Soft wash");
        ToneDocument d = InkWashLayers.blank(W, H);
        stroke(d, wash, 90, across(40, 440, 180));
        long d0 = dark(d);
        eraseAll(d);
        double keeps = dark(d) / (double) d0;
        note(String.format(Locale.ROOT, "Soft wash keeps %.1f%%", 100 * keeps));
        check(Math.abs(keeps - .6) < .02, "A wash at .4 keeps 60% of its darkness: " + keeps);
        ModularBrush gouache = preset("Opaque round");
        ToneDocument g = InkWashLayers.blank(W, H);
        stroke(g, gouache, 60, across(40, 440, 180));
        long c0 = coverage(g, InkWashLayers.WASH);
        eraseAll(g);
        double cov = coverage(g, InkWashLayers.WASH) / (double) c0;
        note(String.format(Locale.ROOT, "gouache keeps %.1f%% of its coverage", 100 * cov));
        check(Math.abs(cov - .7) < .02, "Gouache at .3 keeps 70% of its coverage: " + cov);
        ModularBrush oil = preset("Impasto");
        ToneDocument o = InkWashLayers.blank(W, H);
        stroke(o, oil, 60, across(40, 440, 180));
        byte[][] laid = layer(o, InkWashLayers.WASH);
        eraseAll(o);
        check(same(laid, layer(o, InkWashLayers.WASH)), "Dried oils don't erase");
    }
    /** Pages and brushes from before: no shares, the eraser leaves paper, as the eraser golden says (EraseBothChecks). */
    static void oldPages() {
        ToneDocument d = InkWashLayers.blank(W, H);
        for (String c : new String[]{LegacyBrushes.BUILTINS[0][2], LegacyBrushes.BUILTINS[3][2], ModularCodeChecks.FIXTURES[0]}) {
            ModularBrush b = ModularCode.decode(c);
            stroke(d, b, ModularSizes.inks(b) ? 8 : 60, across(40, 440, 120 + 40 * d.undoSteps()));
        }
        check(!hasShare(d, 0) && !hasShare(d, 1) && dark(d) > 0, "Old brushes grow no shares");
        eraseAll(d);
        check(dark(d) == 0 && coverage(d, 0) == 0 && coverage(d, 1) == 0, "and erase to paper, as before");
    }

    // ---- Smudge ----

    static ModularBrush finger(PartChoice carry, double load) {
        ModularBrush b = ModularCode.decode(CarrySmudgeChecks.GOLDENS[1][1].toString());
        b.set(BrushPart.CARRY, ModularBrush.switchTo(b.part(BrushPart.CARRY), carry));
        if (b.part(BrushPart.CARRY).has(PartDial.LOAD)) b.part(BrushPart.CARRY).set(PartDial.LOAD, load);
        b.part(BrushPart.CARRY).set(PartDial.PICK, .9);
        return b;
    }
    static void smudge() {
        ModularBrush ink = preset("Steady line"), pencil = preset("5B pencil");
        for (PartChoice carry : new PartChoice[]{PartChoice.SMUDGE, PartChoice.DRAG, PartChoice.LIFT}) {
            ModularBrush f = finger(carry, 0);
            // Over permanent ink, nothing moves.
            ToneDocument inked = InkWashLayers.blank(W, H);
            for (int x = 100; x < 400; x += 60) stroke(inked, ink, 6, new double[][]{{x, 40, .8, 0}, {x + 5, 200, .8, 100}, {x, 320, .8, 200}});
            byte[][] was = layer(inked, InkWashLayers.INK);
            CarrySmudgeChecks.live(inked, f, 30, carry == PartChoice.LIFT ? 0 : 1, null, across(60, 440, 180));
            check(same(was, layer(inked, InkWashLayers.INK)), carry + " over permanent ink changes nothing");
            // Over pencil, it moves; over both, the ink's kept part stays exactly where it was.
            ToneDocument both = InkWashLayers.blank(W, H);
            sketch(both, pencil);
            for (int x = 100; x < 400; x += 60) stroke(both, ink, 6, new double[][]{{x, 40, .8, 0}, {x + 5, 200, .8, 100}, {x, 320, .8, 200}});
            byte[][] before = layer(both, InkWashLayers.INK);
            int[] keptBefore = kept(both, InkWashLayers.INK);
            CarrySmudgeChecks.live(both, f, 30, carry == PartChoice.LIFT ? 0 : 1, null, across(60, 440, 180));
            int moved = differ(before, layer(both, InkWashLayers.INK));
            note(carry + " over pencil and ink: " + moved + " px moved");
            check(moved > 500, carry + " moves pencil: " + moved + " px");
            check(Arrays.equals(keptBefore, kept(both, InkWashLayers.INK)), carry + " leaves the ink's permanent part where it was");
        }
        // A Lift takes back at most the erasable 40% of a wash, however often it passes.
        ModularBrush wash = preset("Soft wash"), lift = ModularCode.decode(CarrySmudgeChecks.GOLDENS[3][1].toString());
        ToneDocument d = InkWashLayers.blank(W, H);
        stroke(d, wash, 90, across(40, 440, 180));
        long d0 = dark(d);
        for (int k = 0; k < 6; k++) CarrySmudgeChecks.live(d, lift, 60, 0, null, across(40, 440, 170 + 4 * k));
        double keeps = dark(d) / (double) d0;
        note(String.format(Locale.ROOT, "a wash lifted six times keeps %.1f%%", 100 * keeps));
        check(keeps < .97 && keeps > .58, "Lift takes back only the erasable part of a wash: " + keeps);
    }

    // ---- wet paint after lift ----

    static void wetPaint() {
        // A Slow wash at .4, settled over its ticks: erased, it keeps 60%.
        ModularBrush slow = preset("Slow bloom");
        GravityChecks.Drawn s = GravityChecks.draw(slow, t -> PageTilt.LEVEL, GravityChecks.washPts(90, 430, 140, 25), 110, false);
        check(s.ticks > 3, "The Slow wash settled over ticks: " + s.ticks);
        long d0 = dark(s.page);
        eraseAll(s.page);
        double keeps = dark(s.page) / (double) d0;
        note(String.format(Locale.ROOT, "Slow wash settled, erased, keeps %.1f%%", 100 * keeps));
        // It settles in steps, each keeping 60% of its own darkening (as the web lab's Water): a little more than 60% in all.
        check(keeps > .59 && keeps < .7, "A Slow wash at .4 erased after its settle keeps 60 to 70%: " + keeps);
        // Gravity's drips take their brush's value.
        GravityChecks.Drawn g = GravityChecks.draw(preset("Drips"), t -> PageTilt.of(0, 1), GravityChecks.washPts(90, 430, 140, 25), 110, false);
        long g0 = dark(g.page);
        eraseAll(g.page);
        double gk = dark(g.page) / (double) g0;
        note(String.format(Locale.ROOT, "drips erased keep %.1f%%", 100 * gk));
        check(gk > .59 && gk < .7, "Gravity's runs and drips keep their brush's 60 to 70%: " + gk);
        // A Float print: Sumi (permanent) stays whole; at 1 it erases to paper.
        for (double e : new double[]{0, 1}) {
            GravityChecks.Drawn f = GravityChecks.draw(erasable(preset("Sumi"), e), t -> PageTilt.LEVEL, GravityChecks.washPts(90, 430, 140, 25), 30, false);
            check(!f.session.settling() && dark(f.page) > 0, "Sumi printed");
            byte[][] printed = layer(f.page, InkWashLayers.INK);
            eraseAll(f.page);
            check(e == 0 ? same(printed, layer(f.page, InkWashLayers.INK)) : dark(f.page) == 0, "A Float print takes its brush's value: " + e);
        }
    }
    /** A quick stroke ends with the shares a live one does (Instant and Slow). */
    static void quick() {
        String[][] cases = {{"Fineliner", "200"}, {"Watercolor", "200"}, {"Gouache", "200"}, {"Charcoal", "60"}};
        for (String[] c : cases) for (PartChoice life : new PartChoice[]{PartChoice.NONE, PartChoice.DRYING}) {
            // At its medium's real value: a roll may erase clean now (the weighted rolls), and this is about the shares.
            ModularBrush b = real(QuickStrokeChecks.brush(c[0], life));
            // Charcoal erases clean now (2026-10-20): at its old .9, for a share.
            if (c[0].equals("Charcoal")) erasable(b, .9);
            double size = Double.parseDouble(c[1]);
            ToneDocument q = quickPage(b, size), l = livePage(b, size);
            int layer = ModularSizes.inks(b) ? InkWashLayers.INK : InkWashLayers.WASH;
            check(b.part(BrushPart.MEDIUM).get(PartDial.ERASABLE) < 1 && hasShare(q, layer), c[0] + " " + life + ": a share");
            check(same(layer(q, layer), layer(l, layer)), c[0] + " " + life + " " + size + " px: quick ends as live, shares and all");
        }
    }
    static ToneDocument quickPage(ModularBrush b, double size) {
        ToneDocument d = InkWashLayers.blank(QuickStrokeChecks.W, QuickStrokeChecks.H);
        QuickStroke s = QuickStroke.quickly(d, b, size, 1, null);
        QuickStrokeChecks.draw(s, QuickStrokeChecks.path(), false, null);
        s.finish();
        return d;
    }
    static ToneDocument livePage(ModularBrush b, double size) {
        ToneDocument d = InkWashLayers.blank(QuickStrokeChecks.W, QuickStrokeChecks.H);
        ModularStroke s = ModularStroke.start(d, b, size, 1, null);
        QuickStrokeChecks.draw(s, QuickStrokeChecks.path(), false, null);
        s.finish();
        return d;
    }

    /** Several cores draw what one does, shares and all: big strokes over permanent marks, in bands. */
    static void cores() {
        String[][] cases = {{"Soft wash", "240"}, {"Stump", "120"}, {"Steady line", "120"}, {"Opaque round", "240"}};
        for (String[] c : cases) {
            ModularBrush b = preset(c[0]);
            int layer = ModularSizes.inks(b) ? InkWashLayers.INK : InkWashLayers.WASH;
            byte[][][] out = new byte[2][][];
            for (int i = 0; i < 2; i++) {
                boolean was = Cores.shared;
                Cores.shared = i == 0;
                try {
                    ToneDocument d = InkWashLayers.blank(W, H);
                    d.selectLayer(layer); d.begin(0);
                    int[] row = new int[W];
                    for (int y = 0; y < H; y++) { for (int x = 0; x < W; x++) row[x] = Math.floorMod(x + y / 3, 12) < 3 ? 70 : -1; d.setTones(0, y, W, 1, row); }
                    d.finish();
                    stroke(d, b, Double.parseDouble(c[1]), CarrySmudgeChecks.wave());
                    out[i] = layer(d, layer);
                } finally { Cores.shared = was; }
            }
            check(same(out[0], out[1]), c[0] + " at " + c[1] + " px over permanent marks: several cores draw what one does, shares and all");
        }
    }

    // ---- Undo ----

    static void undo() {
        ModularBrush pencil = preset("5B pencil"), ink = preset("Steady line");
        ToneDocument d = InkWashLayers.blank(W, H);
        byte[][] blank = layer(d, InkWashLayers.INK);
        // A tile captured before its layer had a share: pencil first (no share), then ink over it (the share made).
        sketch(d, pencil);
        byte[][] sketched = layer(d, InkWashLayers.INK);
        int steps = d.undoSteps();
        ink(d, ink);
        byte[][] inked = layer(d, InkWashLayers.INK);
        check(hasShare(d, InkWashLayers.INK) && d.undoSteps() == steps + 1, "Ink makes the share, one step");
        eraseAll(d);
        byte[][] erased = layer(d, InkWashLayers.INK);
        check(d.undo() && same(layer(d, InkWashLayers.INK), inked), "Undo after erasing: the ink and the pencil back, shares too");
        check(d.undo() && same(layer(d, InkWashLayers.INK), sketched), "Undo the ink: the pencil as it was, shares 255");
        check(d.redo() && same(layer(d, InkWashLayers.INK), inked) && d.redo() && same(layer(d, InkWashLayers.INK), erased), "Redo the ink, then the erasing, byte for byte");
        while (d.canUndo()) d.undo();
        check(same(layer(d, InkWashLayers.INK), blank), "Undone to blank paper, shares 255");
        while (d.canRedo()) d.redo();
        check(same(layer(d, InkWashLayers.INK), erased), "and redone to the end");
        // The eraser's preview is what the page shows once it lifts, kept paint and all.
        ToneDocument p = InkWashLayers.blank(W, H);
        stroke(p, preset("Soft wash"), 90, across(40, 440, 180));
        ink(p, ink);
        EraseBothStroke e = new EraseBothStroke(p, 120f);
        for (int x = 0; x <= W; x += 20) e.sample(x, 180, 1f, Float.NaN, Float.NaN);
        int[] shown = new int[W * H];
        e.erasedTones(0, 0, W, H, shown);
        e.finish();
        int checked = 0;
        for (int i = 0; i < shown.length; i++) if (shown[i] >= 0) { checked++; check(shown[i] == p.compositeTone(i % W, i / W), "The eraser's preview is the page as erased"); }
        check(checked > 10000, "previewed " + checked + " px");
    }

    /** Once, at the update: a slot holding a Basic tool exactly as it came takes the tool's new code; others stay. */
    /**
     * The migration (2026-10-22): every stored brush (slots, the app's toolbar, smudge and stamp tools, recent, library)
     * below Erasable 1 goes to 1, once; Resist's wax stays; codes not stored read as written.
     */
    static void slots() {
        java.util.Map<String, String> prefs = new java.util.HashMap<>();
        BrushLibrary.Store store = new BrushLibrary.Store() {
            public String get(String key) { return prefs.get(key); }
            public void put(String key, String value) { if (value == null) prefs.remove(key); else prefs.put(key, value); }
        };
        ModularBrush permanent = preset("Steady line"), wash = preset("Soft wash"), wax = GravityChecks.preset("Wax resist"), pencil = preset("HB pencil");
        permanent.name = "Mine";
        check(permanent.part(BrushPart.MEDIUM).get(PartDial.ERASABLE) == 0 && wax.part(BrushPart.MEDIUM).get(PartDial.ERASABLE) == 0, "the fixture: a permanent ink and wax");
        String[] lines = {Brush.of(permanent).write(), Brush.of(wash).write(), Brush.of(wax).write(), "@INK", Brush.of(pencil).write()};
        prefs.put("tool_slots", String.join("\n", lines));
        prefs.put(ToolSlots.RECENT, Brush.of(permanent).write() + "\n" + Brush.of(wax).write());
        prefs.put(ToolSlots.STAMP_BRUSH, Brush.of(wash).write());
        prefs.put(ToolSlots.APPWIDE, Brush.of(permanent).write() + "\t8.0\t0.5");
        prefs.put(BrushLibrary.LIBRARY, "7\t7\t" + Brush.of(permanent).write() + "\n8\t8\t" + Brush.of(wax).write());
        prefs.put("tool_slots_erasable", "1");
        ToolSlots slots = new ToolSlots(store, new Random(1));
        java.util.function.ToDoubleFunction<Brush> e = b -> b.parts.part(BrushPart.MEDIUM).get(PartDial.ERASABLE);
        check(e.applyAsDouble(slots.brush(0)) == 1 && slots.brush(0).name().equals("Mine") && e.applyAsDouble(slots.brush(1)) == 1, "Slots below 1 go to 1, names kept");
        check(slots.brush(2).parts.choice(BrushPart.MEDIUM) == PartChoice.RESIST && e.applyAsDouble(slots.brush(2)) == 0, "Wax resist stays wax");
        check(slots.brush(3).parts.key().equals(LegacyBrushes.builtin("INK").key()) && e.applyAsDouble(slots.brush(3)) == 1, "A slot from before the six-part engine converts, at 1");
        check(slots.brush(4).sameAs(Brush.of(pencil)), "A pencil, already 1, stays");
        java.util.List<Brush> recent = slots.recent();
        check(e.applyAsDouble(recent.get(0)) == 1 && e.applyAsDouble(recent.get(1)) == 0, "Recent: the ink at 1, the wax as it was");
        check(e.applyAsDouble(slots.brush(ToolSlots.STAMP)) == 1, "The stamp tool's brush at 1");
        check(prefs.get(ToolSlots.APPWIDE).equals(Brush.of(erasable(permanent.copy(), 1)).write() + "\t8.0\t0.5"), "The app's own toolbar, waiting, at 1, its size and grey kept");
        boolean libInk = false, libWax = false;
        for (BrushLibrary.Entry en : slots.library.entries()) {
            if (en.brush.name().equals("Mine")) libInk = e.applyAsDouble(en.brush) == 1 && en.id == 7;
            if (en.brush.parts.choice(BrushPart.MEDIUM) == PartChoice.RESIST) libWax = e.applyAsDouble(en.brush) == 0;
        }
        check(libInk && libWax, "The library: the ink at 1 in its place, the wax as it was");
        // Once: a permanent brush made after it (the hand on the dial) stays permanent at the next launch.
        slots.use(0, Brush.of(permanent));
        check(e.applyAsDouble(new ToolSlots(store, new Random(2)).brush(0)) == 0, "Only once: the owner's permanent brush after it stays");
        // A code pasted later reads as written.
        check(ModularCode.decode(ModularCode.encode(permanent)).part(BrushPart.MEDIUM).get(PartDial.ERASABLE) == 0, "A share code reads as written");
        // A first launch writes the flag and has nothing to change.
        prefs.clear();
        new ToolSlots(store, new Random(3));
        check("1".equals(prefs.get(ToolSlots.ERASABLE)), "A first launch is past the migration");
    }

    static boolean verbose = Boolean.getBoolean("verbose");
    static void note(String s) { if (verbose) System.out.println(s); }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
