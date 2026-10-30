package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.Random;

/**
 * Screen and Tone (the mischief modules spec, Stage 1): a Medium dial (none, halftone, engraved, dither, grain, with
 * Cell, Angle, Misregister, Ink gain and Uneven) and a Medium choice (a screentone: dots, lines, sand or a gradient at a
 * Tone over the brush's fill). Codes, old ones byte for byte; pure black and white; overlapping strokes landing on the
 * same dots and a redraw matching; each screen's coverage following the tone; Misregister, Ink gain and Uneven; Tone's
 * dots over pen marks and its scrape; a shaded page; quick strokes and several cores; rolls from the seed; words and
 * Brush Lab.
 */
public final class ScreenChecks {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        codes(); pure(); aligned(); tracks(); newPrints(); builds(); printFile(); newsprint(); tone(); scrape(); shaded(); quick(); cores(); kept(); rolls(); words(); lab();
        System.out.println("PASS: Screen is the last of every medium's dials after Blend (screen cell sangle mis gain uneven) and Tone is Medium choice 6 (pattern ttone cell sangle), each written only when not its default: every preset's and old code byte for byte, none of them screened; every screen, in ink and wash, Instant, Quick and Slow, lays only pure black on white; a redraw matches, and each screen inks about as much as the tone asks (the six appended after Grain, as well: rosette, Ben-Day, crosshatch, wavy lines, mezzotint and diamond dots grow only darker with the grey, nest, turn with Angle, differ from each other, match their Screentone twins, and are the page's); a grey swatch and a light pen give fewer dots, the stroke's tone even under its tip, and a second stroke over the first prints its own tone, lighter too, the pen line under it untouched, the print tone kept per layer: the eraser takes it, Undo and Redo bring it back, a file keeps it, and a page without a screened mark writes as before; Misregister lays its faint pass at its offset, Ink gain swells dots, Uneven drops specks; Tone lays black dots at its level over its fill and leaves the pen line and the wash layer as they were, its patterns differ and the gradient darkens down the page; the eraser scrapes it in streaks, more each pass, until gone, and erases the line under it in one; a chosen Paint takes it in one pass, a chosen Pen only the line under it; a print over prints replaces them where it lies; on a shaded page each lays black and leaves the paper's shade; quick strokes end as drawn live and several cores as one; every screen's kept thresholds are the ones worked out, bit for bit; rolls give a Screen 1 in 25 (Anything goes, Marker, Ink wash, Charcoal) or 40 (Fineliner, Pencil), never Watercolor, and Tone 1 in 40 to Anything goes only, every other roll as before; the words and Brush Lab say them");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static final int W = 480, H = 360;
    /** Print Shading's choices after None: the first four, then the six appended (rosette, Ben-Day, crosshatch, wavy lines, mezzotint, diamond dots). */
    static final int SCREENS = 14;
    /** Screentone's patterns: the first four, crosshatch, wavy lines and mezzotint, then stipple, woodcut, scribble and hatching. */
    static final int TONES = 11;
    static final String INK = ResistChecks.INK;
    static ModularBrush brush(String code) { return BlendChecks.brush(code); }
    static ModularBrush ink() { return brush(INK); }
    static ModularBrush wash() { return ErasableChecks.preset("Soft wash"); }
    /** A pen line that erases clean. */
    static ModularBrush pen() { return BlendChecks.erasable(ink(), 1); }
    /** {@code b} through Screen {@code screen} (1 halftone, 2 engraving, 3 dither, 4 grain). */
    static ModularBrush screened(ModularBrush b, int screen) { ModularBrush c = b.copy(); c.part(BrushPart.MEDIUM).set(PartDial.SCREEN, screen); return c.normalize(); }
    static ModularBrush set(ModularBrush b, PartDial d, double v) { ModularBrush c = b.copy(); c.part(d.part).set(d, v); return c.normalize(); }
    /** {@code b} as Tone with pattern {@code pattern} at {@code level}. */
    static ModularBrush tone(ModularBrush b, int pattern, double level) {
        ModularBrush c = b.copy();
        ModularBrush.Part p = ModularBrush.switchTo(c.part(BrushPart.MEDIUM), PartChoice.TONE);
        p.set(PartDial.PATTERN, pattern); p.set(PartDial.TTONE, level);
        c.set(BrushPart.MEDIUM, p);
        return c.normalize();
    }
    static ToneDocument blank() { return InkWashLayers.blank(W, H); }
    static void live(ToneDocument d, ModularBrush b, double size, double darkness, double[][] path) { BlendChecks.live(d, b, size, darkness, null, path); }
    static double[][] wave() { return BlendChecks.wave(); }
    static double[][] across(double x0, double x1, double y) { return BlendChecks.across(x0, x1, y); }
    static int[] shown(ToneDocument d) { return BlendChecks.shown(d); }
    static int black(int[] t) { int n = 0; for (int v : t) if (v == 0) n++; return n; }
    /** The medium segment of a code. */
    static String seg(String code) { return code.split("_")[1]; }

    // ---- 1. codes ----

    static void codes() {
        // Every Basic tool, every converted old brush, the golden pages' codes: byte for byte, and none screened.
        java.util.List<String> olds = new java.util.ArrayList<>();
        // Print's and the screened ones are new: they are screened by design.
        for (ToolPresets.Preset p : ToolPresets.all()) if (!p.style.name.equals("Print")) olds.add(p.code);
        for (String[] c : LegacyConversionChecks.CODES) olds.add(c[1]);
        for (Object[] g : CarrySmudgeChecks.GOLDENS) olds.add((String) g[1]);
        for (Object[] g : BlendChecks.GOLDENS) olds.add((String) g[1]);
        for (String c : olds) {
            ModularBrush b = BrushImport.decode(c);
            if (b == null) continue;
            String code = ModularCode.encode(b);
            if (c.contains(".M")) check(code.equals(c) || HandHatchChecks.handHatch(c), "an old code writes back byte for byte (Hand Hatch's as Hatch): " + c + " " + code);
            check(ResolvedBrush.of(b, 30, 1).screen == null && (int) b.part(BrushPart.MEDIUM).get(PartDial.SCREEN, 0) == 0, "no old brush is screened: " + c);
        }
        // Screen: written only when not none, after Blend's digit; its dials after it, each only when not its default.
        ModularBrush ink = ink();
        String plain = ModularCode.encode(ink);
        check(ModularCode.encode(screened(ink, 0)).equals(plain), "Screen none leaves the code as it was");
        for (int s = 1; s <= SCREENS; s++) {
            String code = ModularCode.encode(screened(ink, s));
            ModularBrush back = ModularCode.decode(code);
            check(seg(code).startsWith(seg(plain)) && seg(code).endsWith("0" + Integer.toString(s, 36)) && (int) back.part(BrushPart.MEDIUM).get(PartDial.SCREEN) == s && ModularCode.encode(back).equals(code),
                    "Screen " + s + " is one digit after Blend's, and round-trips: " + seg(code));
        }
        ModularBrush all = screened(ink, 1);
        all = set(set(set(set(set(all, PartDial.CELL, 11), PartDial.S_ANGLE, 15), PartDial.MIS, 5), PartDial.S_GAIN, .4), PartDial.UNEVEN, .3);
        ModularBrush back = ModularCode.decode(ModularCode.encode(all));
        ModularBrush.Part m = back.part(BrushPart.MEDIUM);
        check(m.get(PartDial.CELL) == 11 && m.get(PartDial.MIS) == 5 && Math.abs(m.get(PartDial.S_ANGLE) - 15) < .1 && Math.abs(m.get(PartDial.S_GAIN) - .4) < .01 && Math.abs(m.get(PartDial.UNEVEN) - .3) < .01
                && ModularCode.encode(back).equals(ModularCode.encode(all)), "Cell, Angle, Misregister, Ink gain and Uneven round-trip: " + seg(ModularCode.encode(all)));
        check(ModularCode.encode(set(screened(ink, 2), PartDial.CELL, 6)).equals(ModularCode.encode(screened(ink, 2))), "a dial at its default isn't written");
        for (PartChoice c : new PartChoice[]{PartChoice.INK, PartChoice.WASH, PartChoice.COVER, PartChoice.CHARCOAL, PartChoice.GRAPHITE}) {
            java.util.List<PartDial> dials = c.dials;
            // Then Fling, last of all (FlickFlingChecks).
            check(dials.subList(dials.size() - 7, dials.size() - 1).equals(Arrays.asList(PartDial.SCREEN_DIALS)) && dials.get(dials.size() - 8) == PartDial.M_BLEND && dials.get(dials.size() - 1) == PartDial.FLING, c + ": Screen's dials after Blend, then Fling");
        }
        check(!PartChoice.RESIST.has(PartDial.SCREEN) && PartDial.SCREEN.key.equals("screen") && PartDial.CELL.key.equals("cell") && PartDial.S_ANGLE.key.equals("sangle")
                && PartDial.MIS.key.equals("mis") && PartDial.S_GAIN.key.equals("gain") && PartDial.UNEVEN.key.equals("uneven"), "the spec's keys; Resist has no Screen");
        // Tone: Medium choice 6, its dials pattern, ttone, cell, sangle; a fresh one is "_6_".
        ModularBrush t = tone(ink, 0, PartDial.TTONE.def);
        check(PartChoice.TONE.index() == 6 && PartChoice.TONE.key.equals("tone") && seg(ModularCode.encode(t)).equals("6") && PartDial.PATTERN.key.equals("pattern") && PartDial.TTONE.key.equals("ttone"),
                "Tone is Medium choice 6, written _6_ at its defaults: " + ModularCode.encode(t));
        ModularBrush t2 = set(set(tone(ink, 3, .7), PartDial.CELL, 9), PartDial.S_ANGLE, 30);
        ModularBrush tb = ModularCode.decode(ModularCode.encode(t2));
        check(tb.choice(BrushPart.MEDIUM) == PartChoice.TONE && (int) tb.part(BrushPart.MEDIUM).get(PartDial.PATTERN) == 3 && Math.abs(tb.part(BrushPart.MEDIUM).get(PartDial.TTONE) - .7) < .01
                && tb.part(BrushPart.MEDIUM).get(PartDial.CELL) == 9 && ModularCode.encode(tb).equals(ModularCode.encode(t2)), "Tone's dials round-trip: " + seg(ModularCode.encode(t2)));
        ResolvedBrush r = ResolvedBrush.of(BlendChecks.life(t2, PartChoice.FLOAT), 30, 1);
        check(r.screen != null && r.screen.tone && r.erase == 1 && r.line && r.carry == null && r.life == ResolvedBrush.Life.WET && ModularSizes.inks(t2),
                "Tone resolves as a crisp ink line on the ink layer, always erasable, never on water");
        check(ResolvedBrush.of(BlendChecks.life(screened(ink, 1), PartChoice.FLOAT), 30, 1).screen == null, "on water a Screen does nothing");
        ResolvedBrush slow = ResolvedBrush.of(set(BlendChecks.life(screened(wash(), 1), PartChoice.DRYING), PartDial.SETTLE, .8), 60, 1);
        check(slow.settle == 0 && slow.grav == null && !slow.settlesAfterLift, "a screened mark lays no grey after it: no settling or gravity");
    }

    // ---- 2. pure black and white ----

    static void pure() {
        for (ModularBrush base : new ModularBrush[]{ink(), wash(), ErasableChecks.preset("Messy stick")})
            for (int s = 1; s <= SCREENS; s++)
                for (PartChoice life : new PartChoice[]{PartChoice.NONE, PartChoice.FAST_WET, PartChoice.DRYING}) {
                    ModularBrush b = BlendChecks.life(screened(base, s), life);
                    ToneDocument d = blank();
                    live(d, b, ModularSizes.inks(b) ? 24 : 90, 1, wave());
                    int[] t = shown(d);
                    int bl = 0, other = 0;
                    for (int v : t) { if (v == 0) bl++; else if (v != 255) other++; }
                    check(other == 0 && bl > 300, base.name + " screen " + s + " " + life + ": only black (" + bl + ") and white, " + other + " others");
                }
    }

    // ---- 3. fixed to the page ----

    static void aligned() {
        for (int s = 1; s <= SCREENS; s++) {
            ModularBrush b = screened(wash(), s);
            ToneDocument a = blank(), c = blank(), both = blank(), again = blank();
            live(a, b, 120, 1, across(40, 440, 160));
            live(c, b, 120, 1, across(60, 420, 210));
            live(both, b, 120, 1, across(40, 440, 160));
            live(both, b, 120, 1, across(60, 420, 210));
            live(again, b, 120, 1, across(40, 440, 160));
            int[] ta = shown(a), tc = shown(c), tb = shown(both), pa = printed(a, b), pc = printed(c, b);
            // A print over prints replaces them where it lies (ResolvedBrush.replaces, the owner, 2026-10-29): the second
            // stroke's dots there, the first's elsewhere; along the second's soft edge (three pixels each side) a blend of the two.
            int overlap = 0, wrong = 0;
            for (int i = 0; i < tb.length; i++) {
                if (ta[i] == 0 && tc[i] == 0) overlap++;
                if (!near(pc, i, 3) && tb[i] != (pc[i] < 255 ? tc[i] : ta[i])) wrong++;
            }
            check(wrong == 0 && overlap > 200 && pa.length == pc.length, "screen " + s + ": a stroke over another lays its own dots where it lies, the first's elsewhere (" + overlap + " px both, " + wrong + " wrong)");
            check(LegacyConversionChecks.crc(a).equals(LegacyConversionChecks.crc(again)), "screen " + s + ": the stroke drawn again lays the same pixels");
        }
        // Two screens on one layer: a halftone, then a dither over it is the dither alone where it lies (its plane cleared
        // between its dots), the halftone's dots gone.
        { ModularBrush half = screened(wash(), 1), dither = screened(wash(), 3);
          ToneDocument one = blank(), two = blank();
          live(one, dither, 120, 1, across(40, 440, 180)); live(two, half, 120, 1, across(40, 440, 180)); live(two, dither, 120, 1, across(40, 440, 180));
          int[] t1 = shown(one), t2 = shown(two), p1 = printed(one, dither);
          int differ = 0, inside = 0;
          for (int i = 0; i < t1.length; i++) if (p1[i] < 255 && !near(p1, i, 3)) { inside++; if (t1[i] != t2[i]) differ++; }
          check(differ == 0 && inside > 10000, "a dither over a halftone is the dither alone where it lies: " + differ + " of " + inside + " px differ"); }
        // The threshold is the page's: one pixel's is the same whatever the stroke.
        PrintScreen one = PrintScreen.of(screened(ink(), 1).part(BrushPart.MEDIUM)), two = PrintScreen.of(screened(wash(), 1).part(BrushPart.MEDIUM));
        boolean same = true;
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) same &= one.threshold(x, y) == two.threshold(x, y);
        check(same, "a halftone's thresholds are the page's, whatever the brush");
    }

    // ---- 4. coverage follows the tone ----

    static void tracks() {
        String[] names = {"", "halftone", "engraving", "dither", "grain", "rosette", "ben-day", "crosshatch", "wavy lines", "mezzotint", "diamond dots", "stipple", "woodcut", "scribble", "hatching"};
        for (int s = 1; s <= SCREENS; s++) {
            PrintScreen p = PrintScreen.of(screened(ink(), s).part(BrushPart.MEDIUM));
            double last = -1;
            StringBuilder said = new StringBuilder();
            for (double a : new double[]{.1, .25, .5, .75, .9}) {
                int n = 0, all = 0;
                for (int y = 0; y < 240; y++) for (int x = 0; x < 240; x++) { all++; if (p.ink(a, x, y, H)) n++; }
                double f = n / (double) all;
                said.append(String.format(java.util.Locale.ROOT, " %.2f→%.2f", a, f));
                // Ben-Day too: its dots grow smoothly with the tone (no longer in six tints; the owner, 2026-10-22).
                check(Math.abs(f - a) <= (s == 1 || s == 5 ? .12 : .05) && f > last, names[s] + " inks about as much as the tone asks:" + said);
                last = f;
            }
        }
        // Through the engine: a lighter swatch lays fewer dots.
        int dark = black(shown(drawn(screened(wash(), 1), 1))), light = black(shown(drawn(screened(wash(), 1), .5)));
        check(light < dark * .8 && light > dark * .2, "a halftone wash in a lighter grey lays fewer dots: " + light + " vs " + dark);
    }
    static ToneDocument drawn(ModularBrush b, double darkness) { ToneDocument d = blank(); live(d, b, 120, darkness, wave()); return d; }
    /** The print tone of {@code b}'s layer on {@code d} at every pixel (255 where none). */
    /** Whether pixel {@code i} of a print tone {@code p} lies within {@code r} px of the edge of where it is printed (below 255). */
    static boolean near(int[] p, int i, int r) {
        int x = i % W, y = i / W;
        boolean in = p[i] < 255;
        for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
            int nx = x + dx, ny = y + dy;
            if (nx >= 0 && ny >= 0 && nx < W && ny < H && (p[ny * W + nx] < 255) != in) return true;
        }
        return false;
    }
    static int[] printed(ToneDocument d, ModularBrush b) {
        int layer = ModularStroke.layerOf(d, b);
        int[] out = new int[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) out[y * W + x] = d.printTone(layer, x, y);
        return out;
    }
    /** A straight stroke at {@code y} at pressure {@code pr} (travel), a sample every 2.5 px. */
    static double[][] pressed(double y, double pr) { double[][] p = across(40, 440, y); for (double[] s : p) s[2] = pr; return p; }
    /**
     * {@code b} drawn {@code strokes} times along {@code path} at {@code darkness}, screened and not: {black share of the
     * screened page, the unscreened page's mean darkness}, over the pixels the unscreened marks reach.
     */
    static double[] shares(ModularBrush b, double size, double darkness, double[][] path, int strokes) {
        ToneDocument plain = blank(), scr = blank();
        for (int k = 0; k < strokes; k++) { live(plain, screened(b, 0), size, darkness, path); live(scr, b, size, darkness, path); }
        int foot = 0, bl = 0; double dark = 0;
        for (int y = 0; y < H; y++) for (int x = 80; x < 400; x++) {
            int t = plain.compositeTone(x, y);
            if (t >= 250) continue;
            foot++; dark += (255 - t) / 255.0; if (scr.compositeTone(x, y) == 0) bl++;
        }
        return new double[]{bl / (double) foot, dark / foot};
    }
    static String pct(double[] s) { return String.format(java.util.Locale.ROOT, "%.0f%% dots for %.0f%% grey", 100 * s[0], 100 * s[1]); }

    // ---- 4a. the six appended prints ----

    /** Each appended print: coverage tracks the grey and only grows with it, its ink nests, it is the page's, it differs from the others, and Screentone's twins match. */
    static void newPrints() {
        for (int s = 5; s <= SCREENS; s++) {
            PrintScreen p = PrintScreen.of(screened(ink(), s).part(BrushPart.MEDIUM)), q = PrintScreen.of(screened(wash(), s).part(BrushPart.MEDIUM));
            boolean[] was = new boolean[240 * 240];
            int steps = 0;
            int last = -1;
            for (double a = .05; a < 1.001; a += .05) {
                int n = 0;
                for (int y = 0; y < 240; y++) for (int x = 0; x < 240; x++) {
                    boolean on = p.ink(a, x, y, H);
                    if (was[y * 240 + x] && !on) throw new AssertionError("print " + s + ": ink at a lighter grey is gone at " + a);
                    was[y * 240 + x] = on;
                    if (on) n++;
                    if (a == .5 && p.threshold(x, y) != q.threshold(x, y)) throw new AssertionError("print " + s + ": thresholds are the page's, not the brush's");
                }
                // Ben-Day as every other: no tint held between steps, more grey always more ink.
                check(n > last, "print " + s + ": more grey, more ink (" + n + " after " + last + " at " + a + ")");
                if (last >= 0 && n > last) steps++;
                last = n;
            }
            check(last == 240 * 240, "print " + s + ": black at full grey (" + last + ")");
            check(s != 6 || steps == 19, "Ben-Day: a step for every twentieth of the grey, not six tints (" + steps + " steps)");
            // Angle turns it (except where there is nothing to turn: all do).
            PrintScreen turned = PrintScreen.of(set(screened(ink(), s), PartDial.S_ANGLE, 20).part(BrushPart.MEDIUM));
            int diff = 0;
            for (int y = 0; y < 60; y++) for (int x = 0; x < 60; x++) if (p.ink(.5, x, y, H) != turned.ink(.5, x, y, H)) diff++;
            check(diff > 200, "print " + s + ": Angle turns it (" + diff + " pixels differ)");
            for (int o = 1; o < s; o++) {
                PrintScreen other = PrintScreen.of(screened(ink(), o).part(BrushPart.MEDIUM));
                int d = 0;
                for (int y = 0; y < 60; y++) for (int x = 0; x < 60; x++) if (p.ink(.75, x, y, H) != other.ink(.75, x, y, H)) d++;
                check(d > 300, "print " + s + " differs from print " + o + " (" + d + ")");
            }
        }
        // Screentone's twins lay the same pattern as the Print Shading of the same name.
        int[][] twins = {{4, 7}, {5, 8}, {6, 9}, {7, 11}, {8, 12}, {9, 13}, {10, 14}};
        for (int[] t : twins) {
            PrintScreen tone = PrintScreen.of(tone(ink(), t[0], 1).part(BrushPart.MEDIUM)), scr = PrintScreen.of(screened(ink(), t[1]).part(BrushPart.MEDIUM));
            boolean same = true;
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) same &= tone.threshold(x, y) == scr.threshold(x, y);
            check(same, "Screentone pattern " + t[0] + " is Print Shading " + t[1]);
        }
        // Stable: mezzotint's dashes sit at the same page pixels whatever else changes.
        PrintScreen a = PrintScreen.of(screened(ink(), 9).part(BrushPart.MEDIUM)), b = PrintScreen.of(set(screened(wash(), 9), PartDial.MIS, 3).part(BrushPart.MEDIUM));
        for (int y = 0; y < 40; y++) for (int x = 0; x < 40; x++) check(a.threshold(x, y) == b.threshold(x, y), "mezzotint is fixed to the page");
    }

    // ---- 4b. the tone it prints: the grey, the pressure, a second stroke ----

    static void builds() {
        // The dots follow the stroke's own tone, even across its tip (ResolvedBrush.screenTone): the swatch's grey and
        // the pen's pressure through Control's Pressure dark. A second stroke over the first at the same tone keeps
        // the same dots (the print tone is the darker of the two, not built up), so strokes over strokes fill evenly.
        ModularBrush ink = screened(ink(), 1), wash = screened(wash(), 1), pressing = ink.copy();
        pressing.part(BrushPart.HAND).set(PartDial.PD, 1); pressing.normalize();
        for (Object[] c : new Object[][]{{"ink", ink, 24.0}, {"wash", wash, 90.0}}) {
            ModularBrush b = (ModularBrush) c[1]; double size = (Double) c[2];
            double[] black = shares(b, size, 1, across(40, 440, 180), 1), grey = shares(b, size, .53, across(40, 440, 180), 1), light = shares(b, size, .31, across(40, 440, 180), 1);
            double[] twice = shares(b, size, .53, across(40, 440, 180), 2);
            String said = c[0] + ": black " + pct(black) + ", grey " + pct(grey) + ", light grey " + pct(light) + ", grey twice " + pct(twice);
            check(black[0] > grey[0] + .1 && grey[0] > light[0] + .1 && Math.abs(twice[0] - grey[0]) < .02, "a lighter grey lays fewer dots, a second stroke the same: " + said);
            if (b == ink) for (double[] t : new double[][]{{1, black[0]}, {.53, grey[0]}, {.31, light[0]}}) check(Math.abs(t[0] - t[1]) < .12, "the dots follow the stroke's tone, " + said);
        }
        double[] soft = shares(pressing, 24, 1, pressed(180, .2), 1), hard = shares(pressing, 24, 1, pressed(180, .9), 1);
        check(hard[0] > soft[0] + .4, "with Pressure dark, pressing harder lays more dots: light " + pct(soft) + ", hard " + pct(hard));
        // The print tone is the stroke's own tone under its tip, the darker of two strokes: here the grey's, 1 − .53.
        ToneDocument d = blank();
        for (int k = 0; k < 2; k++) live(d, ink, 24, .53, across(40, 440, 180));
        int[] p = printed(d, ink); int off = 0, n = 0;
        for (int y = 0; y < H; y++) for (int x = 80; x < 400; x++) if (p[y * W + x] < 250) { n++; if (Math.abs(p[y * W + x] - 255 * .47) > 3) off++; }
        check(n > 1000 && off < n / 20, "the print tone is the stroke's tone (" + off + " of " + n + " px off by more than 3)");
        // A stroke over another prints its own tone (the owner, 2026-10-29: "go over that area again and make it the same
        // pattern, but lighter"): a darker one darkens it, a lighter one lightens it; a pen line under them stays as it was.
        ToneDocument up = blank(), down = blank(), lightOnly = blank(), darkOnly = blank();
        live(up, ink, 24, .31, across(40, 440, 180)); live(up, ink, 24, .8, across(40, 440, 180));
        live(down, pen(), 4, 1, across(20, 460, 180));
        ToneDocument.Layer penLine = KindLayersChecks.copy(KindLayersChecks.layer(down, InkWashLayers.INK).plane(ToneDocument.PLANE_PEN));
        live(down, ink, 24, .8, across(40, 440, 180)); live(down, ink, 24, .31, across(40, 440, 180));
        live(lightOnly, ink, 24, .31, across(40, 440, 180)); live(darkOnly, ink, 24, .8, across(40, 440, 180));
        int[] pu = printed(up, ink), pd = printed(down, ink), pl = printed(lightOnly, ink), pk = printed(darkOnly, ink), sd = shown(down), sk = shown(darkOnly);
        int wrongUp = 0, wrongDown = 0, darkBlack = 0, downBlack = 0;
        for (int y = 170; y <= 190; y++) for (int x = 80; x < 400; x++) {
            int i = y * W + x;
            if (pu[i] != pk[i]) wrongUp++;
            if (pd[i] != pl[i]) wrongDown++;
            if (sk[i] == 0) darkBlack++;
            if (sd[i] == 0 && Math.abs(y - 180) > 3) downBlack++;
        }
        check(wrongUp == 0 && wrongDown == 0 && downBlack < darkBlack / 2, "a lighter stroke over a darker prints the lighter, a darker over a lighter the darker: "
                + wrongUp + " and " + wrongDown + " px off, " + downBlack + " black px against " + darkBlack);
        check(KindLayersChecks.samePlane(penLine, KindLayersChecks.layer(down, InkWashLayers.INK).plane(ToneDocument.PLANE_PEN))
                && KindLayersChecks.layer(down, InkWashLayers.INK).plane(ToneDocument.PLANE_WASH).print != null, "the prints on the Paint plane, the pen line under them byte for byte");
        // An unscreened brush neither reads nor makes a print tone.
        ToneDocument none = blank();
        live(none, ink(), 24, .53, across(40, 440, 180)); live(none, wash(), 90, .53, across(40, 440, 180));
        for (ToneDocument.Layer l : none.layerSnapshot().layers) check(l.print == null, "an unscreened page has no print tone");

        // The eraser takes it with the dots; Undo and Redo bring both back; a stroke after the eraser prints afresh.
        ModularBrush clean = screened(pen(), 1);
        ToneDocument e = blank();
        live(e, clean, 24, .53, across(40, 440, 180));
        String first = LegacyConversionChecks.crc(e); int[] firstPrint = printed(e, clean);
        // A darker second stroke (one at the same grey prints nothing new).
        live(e, clean, 24, .8, across(40, 440, 180));
        String two = LegacyConversionChecks.crc(e); int[] twoPrint = printed(e, clean);
        check(e.undo() && LegacyConversionChecks.crc(e).equals(first) && java.util.Arrays.equals(printed(e, clean), firstPrint), "Undo takes the second stroke back, its print tone too");
        check(e.redo() && LegacyConversionChecks.crc(e).equals(two) && java.util.Arrays.equals(printed(e, clean), twoPrint), "Redo puts it back, its print tone too");
        wipe(e, 180);
        int[] gone = printed(e, clean); int left = 0;
        for (int y = 160; y <= 200; y++) for (int x = 120; x <= 360; x++) if (gone[y * W + x] != 255 || e.compositeTone(x, y) != 255) left++;
        check(left == 0, "the eraser takes the dots and the print tone under them: " + left + " px left");
        check(e.undo() && LegacyConversionChecks.crc(e).equals(two) && java.util.Arrays.equals(printed(e, clean), twoPrint), "Undo brings the erased dots and print tone back");
        check(e.redo() && java.util.Arrays.equals(printed(e, clean), gone), "Redo erases them again");
        live(e, clean, 24, .53, across(40, 440, 180));
        int[] again = printed(e, clean), fresh = shown(e), once = shown(drawnOnce(clean));
        int differ = 0;
        for (int y = 165; y <= 195; y++) for (int x = 130; x <= 350; x++) { int i = y * W + x; if (again[i] != firstPrint[i] || fresh[i] != once[i]) differ++; }
        check(differ == 0, "after the eraser, a stroke prints as the first did: " + differ + " px differ");
        // Masking fluid keeps the print tone out where it shields.
        ToneDocument masked = blank();
        masked.paintMaskingFluid(true); masked.begin(); for (int y = 150; y < 210; y++) for (int x = 200; x < 280; x++) masked.paintTone(x, y, 0); masked.finish(); masked.paintMaskingFluid(false);
        live(masked, ink, 24, .53, across(40, 440, 180));
        check(masked.printTone(ModularStroke.layerOf(masked, ink), 240, 180) == 255 && masked.printTone(ModularStroke.layerOf(masked, ink), 150, 180) < 200, "masking fluid keeps the print tone out");
    }
    static ToneDocument drawnOnce(ModularBrush b) { ToneDocument d = blank(); live(d, b, 24, .53, across(40, 440, 180)); return d; }

    /** A file keeps the print tone, so a stroke after reopening builds on it; a page without one writes as before. */
    static void printFile() throws java.io.IOException {
        ModularBrush ink = screened(ink(), 1);
        ToneDocument d = blank();
        live(d, ink, 24, .53, across(40, 440, 180));
        live(d, screened(wash(), 3), 90, .53, across(40, 440, 200));
        ToneDocument back = reread(d);
        for (int i = 0; i < d.layerCount(); i++) for (int p = -1; p < ToneDocument.PLANES; p++) {
            ToneDocument.Layer x = d.layerSnapshot().layers.get(i), y = back.layerSnapshot().layers.get(i);
            if (p >= 0) { x = x.plane(p); y = y.plane(p); }
            byte[] a = x == null ? null : x.print, b = y == null ? null : y.print;
            check(a == null ? b == null : java.util.Arrays.equals(a, b), "layer " + i + "'s print tone round-trips (plane " + p + ")");
        }
        check(LegacyConversionChecks.crc(d).equals(LegacyConversionChecks.crc(back)), "and its marks");
        live(d, ink, 24, .53, across(40, 440, 180)); live(back, ink, 24, .53, across(40, 440, 180));
        check(LegacyConversionChecks.crc(d).equals(LegacyConversionChecks.crc(back)), "a stroke after reopening builds on it as it would have before");
        // A page without a screened mark: the same bytes whether or not a layer has a print tone of nothing (a screened
        // stroke undone), so every page without one writes as before (the goldens' CRCs check the rest).
        ToneDocument plain = blank(), undone = blank();
        for (ToneDocument p : new ToneDocument[]{plain, undone}) { live(p, ink(), 24, .53, across(40, 440, 180)); live(p, wash(), 90, .53, across(40, 440, 220)); }
        live(undone, ink, 24, .53, across(40, 440, 120));
        check(undone.undo(), "undone");
        undone.selectLayer(plain.activeLayer());
        check(java.util.Arrays.equals(bytes(plain), bytes(undone)), "a page whose screened stroke was undone writes as one never screened, byte for byte");
    }
    static byte[] bytes(ToneDocument d) throws java.io.IOException { java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(); DocumentCodec.write(out, d.layerSnapshot()); return out.toByteArray(); }
    static ToneDocument reread(ToneDocument d) throws java.io.IOException { return DocumentCodec.read(new java.io.ByteArrayInputStream(bytes(d))); }

    // ---- 5. newsprint: Misregister, Ink gain, Uneven ----

    static void newsprint() {
        ModularBrush b = screened(ink(), 1);
        int[] plain = shown(line(b)), mis = shown(line(set(b, PartDial.MIS, 8))), gain = shown(line(set(b, PartDial.S_GAIN, 1))), uneven = shown(line(set(b, PartDial.UNEVEN, 1)));
        // The faint pass at 6, 5 px: its dots only add, and reach past the line by the offset, no farther.
        int added = 0, lost = 0, far = 0;
        int[] box = bounds(plain);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int i = y * W + x;
            if (plain[i] == 0 && mis[i] != 0) lost++;
            if (mis[i] == 0 && plain[i] != 0) { added++; if (x < box[0] || y < box[1] || x > box[2] + 7 || y > box[3] + 6) far++; }
        }
        boolean beyond = false;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (mis[y * W + x] == 0 && (y > box[3] || x > box[2])) beyond = true;
        check(lost == 0 && added > 100 && far == 0 && beyond, "Misregister lays a faint second pass at its offset: " + added + " px added, " + far + " too far, none lost");
        int g = 0, gl = 0, u = 0, ug = 0;
        for (int i = 0; i < plain.length; i++) { if (gain[i] == 0 && plain[i] != 0) g++; if (plain[i] == 0 && gain[i] != 0) gl++; if (plain[i] == 0 && uneven[i] != 0) u++; if (uneven[i] == 0 && plain[i] != 0) ug++; }
        check(g > 100 && gl == 0, "Ink gain swells the dots by a ragged pixel: " + g + " px more");
        check(u > 50 && ug == 0, "Uneven drops specks where the ink ran thin: " + u + " px fewer");
    }
    static ToneDocument line(ModularBrush b) { ToneDocument d = blank(); live(d, b, 30, .6, across(40, 440, 180)); return d; }
    /** The black pixels' bounds {x0, y0, x1, y1}. */
    static int[] bounds(int[] t) {
        int[] b = {W, H, -1, -1};
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (t[y * W + x] == 0) { b[0] = Math.min(b[0], x); b[1] = Math.min(b[1], y); b[2] = Math.max(b[2], x); b[3] = Math.max(b[3], y); }
        return b;
    }

    // ---- 6. Tone ----

    /** A page with a grey wash band on its wash layer and a pen line on its ink layer, then a Tone scribbled over both. */
    static ToneDocument toned(ModularBrush t) {
        ToneDocument d = BlendChecks.page(InkWashLayers.WASH, (x, y) -> y >= 120 && y < 240 ? 160 : 255);
        live(d, pen(), 4, 1, across(30, 450, 180));
        fill(d, t);
        return d;
    }
    static void fill(ToneDocument d, ModularBrush t) { for (int y = 90; y <= 270; y += 24) live(d, t, 40, 1, across(40, 440, y)); }
    static void tone() {
        ToneDocument before = BlendChecks.page(InkWashLayers.WASH, (x, y) -> y >= 120 && y < 240 ? 160 : 255);
        live(before, pen(), 4, 1, across(30, 450, 180));
        int[] wash0 = BlendChecks.layerTones(before, InkWashLayers.WASH), ink0 = BlendChecks.layerTones(before, InkWashLayers.INK);
        int[][] dots = new int[TONES][];
        for (int p = 0; p < TONES; p++) {
            ToneDocument d = toned(tone(ink(), p, .45));
            int[] ink1 = BlendChecks.layerTones(d, InkWashLayers.INK);
            check(Arrays.equals(BlendChecks.layerTones(d, InkWashLayers.WASH), wash0), "pattern " + p + ": the wash layer is as it was");
            int n = 0, inside = 0, kept = 0, lines = 0, wrong = 0;
            dots[p] = new int[W * H];
            ToneDocument.Layer inkLook = d.layerSnapshot().layers.get(InkWashLayers.INK).flattened();
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int i = y * W + x;
                boolean dot = inkLook.alpha[i] == (byte) PrintScreen.DOT_COVERAGE;
                if (ink0[i] < 128) { lines++; if (ink1[i] <= ink0[i]) kept++; }
                else if (dot) { dots[p][i] = 1; if (y >= 100 && y <= 260 && x >= 60 && x <= 420) n++; }
                else if (ink1[i] != ink0[i]) wrong++;
                if (y >= 100 && y <= 260 && x >= 60 && x <= 420) inside++;
                if (dot && d.compositeTone(x, y) > 1) wrong++;
            }
            check(kept == lines && wrong == 0, "pattern " + p + ": the pen line stays as it was under the tone, every other pixel a black dot or as it was (" + wrong + " wrong)");
            double f = n / (double) inside;
            if (p != 3) check(f > .3 && f < .6, "pattern " + p + ": dots on about its Tone of the fill, " + String.format(java.util.Locale.ROOT, "%.2f", f));
            else {
                int top = 0, bottom = 0;
                for (int x = 60; x <= 420; x++) for (int y = 100; y < 140; y++) { top += dots[p][y * W + x]; bottom += dots[p][(y + 120) * W + x]; }
                check(bottom > top * 1.5, "the gradient darkens down the page: " + top + " dots high, " + bottom + " low");
            }
        }
        for (int a = 0; a < TONES; a++) for (int b = a + 1; b < TONES; b++) check(!Arrays.equals(dots[a], dots[b]), "patterns " + a + " and " + b + " lay different dots");
        // A darker Tone lays more.
        int light = count(toned(tone(ink(), 0, .25))), darker = count(toned(tone(ink(), 0, .7)));
        check(darker > light * 1.8, "a darker Tone lays more dots: " + light + " → " + darker);
    }
    static int count(ToneDocument d) { int n = 0; for (byte a : d.layerSnapshot().layers.get(InkWashLayers.INK).flattened().alpha) if (a == (byte) PrintScreen.DOT_COVERAGE) n++; return n; }

    /** The eraser scrapes Tone in streaks along its stroke: some each pass, more on some streaks, until gone; the pen line under it in one. */
    static void scrape() {
        ToneDocument d = toned(tone(ink(), 0, .6));
        int[] rows0 = rows(d);
        int before = path(rows0), outside = away(d);
        erase(d, 180);
        int[] rows1 = rows(d);
        int after = path(rows1);
        check(after < before * .75 && after > before * .1 && away(d) == outside, "one pass scrapes some of the tone where it went, none elsewhere: " + before + " → " + after + " dots");
        // Streaks: along the stroke (rows here) some keep much more than others.
        double lo = 1, hi = 0;
        for (int y = 172; y <= 188; y++) if (rows0[y] > 20) { double k = rows1[y] / (double) rows0[y]; lo = Math.min(lo, k); hi = Math.max(hi, k); }
        check(hi - lo > .35, String.format(java.util.Locale.ROOT, "in streaks along the stroke: rows keep %.2f to %.2f", lo, hi));
        check(line(d) == 0, "the pen line under it goes in one pass");
        erase(d, 180);
        check(path(rows(d)) < after * .8, "a second pass scrapes more: " + after + " → " + path(rows(d)));
        for (int i = 0; i < 24; i++) erase(d, 180);
        int left = 0;
        byte[] look = d.layerSnapshot().layers.get(InkWashLayers.INK).flattened().alpha;
        for (int y = 174; y <= 186; y++) for (int x = 120; x <= 360; x++) if (look[y * W + x] == (byte) PrintScreen.DOT_COVERAGE) left++;
        check(left < 20, "pass after pass it scrapes it all: " + left + " dots left");
        // Tone is Paint (2026-10-29): a chosen Pen takes the pen line under it and leaves the dots; a chosen Paint takes the
        // dots whole in one pass (the owner, 2026-10-20, of Pen then) and leaves the line; neither anything away from its path.
        ToneDocument p = toned(tone(ink(), 0, .6));
        int far = away(p);
        ToneDocument.Layer dots = KindLayersChecks.copy(KindLayersChecks.layer(p, InkWashLayers.INK).plane(ToneDocument.PLANE_WASH));
        erase(p, 180, ToneDocument.KIND_PEN);
        check(line(p) == 0 && KindLayersChecks.samePlane(dots, KindLayersChecks.layer(p, InkWashLayers.INK).plane(ToneDocument.PLANE_WASH)), "Pen takes the line under the tone, the tone stays byte for byte");
        ToneDocument q = toned(tone(ink(), 0, .6));
        erase(q, 180, ToneDocument.KIND_PAINT);
        int[] r = rows(q); int under = 0;
        for (int y = 175; y <= 185; y++) under += r[y];
        check(under == 0 && away(q) == far && line(q) > 200, "Paint takes the tone under it in one pass, the line stays: " + under + " dots left, " + line(q) + " px of line");
    }
    /** Ink-layer pen-line pixels left on row 180 (the line's), where the eraser went. */
    static int line(ToneDocument d) { int n = 0; byte[] a = d.layerSnapshot().layers.get(InkWashLayers.INK).flattened().alpha; for (int x = 100; x < 380; x++) if (a[180 * W + x] == (byte) 255) n++; return n; }
    /** The dots well away from the eraser's path. */
    static int away(ToneDocument d) { int n = 0; byte[] a = d.layerSnapshot().layers.get(InkWashLayers.INK).flattened().alpha; for (int y = 0; y < H; y++) if (y < 130 || y > 230) for (int x = 0; x < W; x++) if (a[y * W + x] == (byte) PrintScreen.DOT_COVERAGE) n++; return n; }
    /** The dots on the eraser's path's rows (of {@link #rows}). */
    static int path(int[] rows) { int n = 0; for (int y = 172; y <= 188; y++) n += rows[y]; return n; }
    static int[] rows(ToneDocument d) {
        int[] r = new int[H];
        byte[] a = d.layerSnapshot().layers.get(InkWashLayers.INK).flattened().alpha;
        for (int y = 0; y < H; y++) for (int x = 120; x <= 360; x++) if (a[y * W + x] == (byte) PrintScreen.DOT_COVERAGE) r[y]++;
        return r;
    }
    /** A finger eraser 60 px wide straight across at {@code y}. */
    static void erase(ToneDocument d, int y) { erase(d, y, 0); }
    /** As above, the eraser limited to {@code kinds} ({@link EraseKind}). */
    static void erase(ToneDocument d, int y, int kinds) {
        EraseBothStroke e = new EraseBothStroke(d, 60f, kinds);
        for (int i = 0; i <= 60; i++) e.sample(60 + 6 * i, y, 1, 0, 0, i * 5L);
        e.finish();
    }
    /** A finger eraser back and forth over the rows {@code y − 14}, {@code y} and {@code y + 14}: a 24 px line there goes. */
    static void wipe(ToneDocument d, int y) {
        EraseBothStroke e = new EraseBothStroke(d, 60f);
        for (int i = 0; i <= 180; i++) { int k = i % 60, row = i / 60; e.sample(row == 1 ? 420 - 6 * k : 60 + 6 * k, y - 14 + 14 * row, 1, 0, 0, i * 3L); }
        e.finish();
    }

    // ---- 7. a shaded page ----

    static void shaded() {
        for (int s = 1; s <= SCREENS; s++) {
            ToneDocument d = blank();
            check(d.shadePaper(120), "the page shades");
            live(d, screened(wash(), s), 90, 1, wave());
            int bl = 0, other = 0;
            for (int v : shown(d)) { if (v == 0) bl++; else if (v != 120) other++; }
            check(bl > 300 && other == 0, "screen " + s + " on a shaded page: black dots (" + bl + ") and the paper's shade, " + other + " others");
        }
        ToneDocument d = blank();
        d.shadePaper(97);
        fill(d, tone(ink(), 0, .5));
        int bl = 0, other = 0;
        for (int v : shown(d)) { if (v <= 1) bl++; else if (v != 97) other++; }
        check(bl > 2000 && other == 0, "Tone on a shaded page: black dots (" + bl + ") on the shade, " + other + " others");
    }

    // ---- 8. quick strokes, several cores ----

    static void quick() {
        for (ModularBrush b : new ModularBrush[]{screened(wash(), 1), screened(ink(), 3), tone(ink(), 0, .5)})
            for (PartChoice life : new PartChoice[]{PartChoice.NONE, PartChoice.DRYING}) for (double size : new double[]{40, 160}) {
                ModularBrush c = BlendChecks.life(b, life);
                ToneDocument q = blank(), l = blank();
                // Twice: the second builds on the first's print tone.
                for (int k = 0; k < 2; k++) { BlendChecks.quick(q, c, size, .6, null, wave()); live(l, c, size, .6, wave()); }
                check(BlendChecks.same(BlendChecks.all(q), BlendChecks.all(l)) && java.util.Arrays.equals(printed(q, c), printed(l, c)), b.part(BrushPart.MEDIUM).choice + " " + life + " " + size + ": quick ends as drawn live, its print tone too");
            }
        // The quick preview under the pen is black dots or nothing, as the screen's (the panel's pattern for the screen's grey: QuickStroke.Sweep#flatScreen).
        ToneDocument d = blank();
        QuickStroke s = QuickStroke.quickly(d, screened(wash(), 1), 160, 1, null);
        double[][] p = across(20, 460, 180);
        for (int i = 0; i < p.length / 2; i++) s.sample((float) p[i][0], (float) p[i][1], ToolSettings.pressure((float) p[i][2]), 0, 0, (long) p[i][3]);
        s.present();
        int bl = 0, other = 0;
        for (int v : shown(d)) { if (v == 0) bl++; else if (v != 255) other++; }
        check(bl > 500 && other == 0, "the quick preview shows black dots only: " + bl + " black, " + other + " others");
        check(s.screened(), "a quick stroke knows it is screened (the bench's fast panel path, StrokePanel)");
        s.finish();
        check(ModularStroke.start(blank(), tone(ink(), 0, .5), 20, 1, null).screened() && !ModularStroke.start(blank(), ink(), 20, 1, null).screened(), "so does a live one; an unscreened one doesn't");
    }
    static void cores() {
        for (ModularBrush b : new ModularBrush[]{set(screened(wash(), 1), PartDial.MIS, 6), screened(wash(), 4), tone(ink(), 1, .5)}) {
            String[] crc = new String[2];
            for (int i = 0; i < 2; i++) {
                boolean was = Cores.shared;
                Cores.shared = i == 0;
                try { ToneDocument d = blank(); live(d, b, 300, 1, wave()); crc[i] = LegacyConversionChecks.crc(d); }
                finally { Cores.shared = was; }
            }
            check(crc[0].equals(crc[1]), b.part(BrushPart.MEDIUM).choice + " at 300 px lays the same on several cores as on one");
        }
    }

    /**
     * Every screen and pattern, off its default cell and angle: each pixel's threshold as kept (asked twice: worked out
     * into its tile, then read back), off the page and past the kept tiles too, is the one worked out afresh, bit for bit.
     */
    static void kept() {
        for (int s = 1; s <= SCREENS + TONES; s++) {
            ModularBrush b = s <= SCREENS ? screened(ink(), s) : tone(ink(), s - SCREENS - 1, .5);
            PrintScreen p = PrintScreen.of(set(set(b, PartDial.CELL, 9), PartDial.S_ANGLE, 20).part(BrushPart.MEDIUM));
            int wrong = 0;
            for (int pass = 0; pass < 2; pass++)
                for (int y : new int[]{-3, -1, 0, 1, 31, 32, 33, 700, 4095, 4096, 4100})
                    for (int x = -40; x < 4200; x += x < 100 || x > 4000 ? 1 : 37)
                        if (Double.doubleToRawLongBits(p.threshold(x, y)) != Double.doubleToRawLongBits(p.worked(x, y))) wrong++;
            check(wrong == 0, p.kind + ": its kept thresholds are the ones worked out (" + wrong + " differ)");
        }
    }

    // ---- 9. rolls ----

    static void rolls() {
        int n = 1000;
        for (ModularStyle s : ModularStyle.all()) {
            int screens = 0, tones = 0, other = 0, appended = 0;
            for (int i = 0; i < n; i++) {
                ModularBrush with = s.roll(new Random(8100L * (s.index + 1) + i)), noScreen, noTone;
                ModularStyle.screenRolls = false;
                try { noScreen = s.roll(new Random(8100L * (s.index + 1) + i)); } finally { ModularStyle.screenRolls = true; }
                ModularStyle.toneRolls = false;
                try { noTone = s.roll(new Random(8100L * (s.index + 1) + i)); } finally { ModularStyle.toneRolls = true; }
                for (BrushPart p : BrushPart.values()) if (p != BrushPart.MEDIUM && !(with.part(p).sameAs(noScreen.part(p)) && with.part(p).sameAs(noTone.part(p)))) other++;
                check(with.seed == noScreen.seed && with.style == noScreen.style, "the same seed");
                ModularBrush.Part m = with.part(BrushPart.MEDIUM);
                if (m.choice == PartChoice.TONE) {
                    tones++;
                    check(noTone.choice(BrushPart.MEDIUM) != PartChoice.TONE && (int) m.get(PartDial.PATTERN) >= 0 && m.get(PartDial.TTONE) >= .25 && m.get(PartDial.TTONE) <= .65, "a roll that becomes Tone, from the seed");
                    continue;
                }
                if (!m.sameAs(noTone.part(BrushPart.MEDIUM))) other++;
                int sc = (int) m.get(PartDial.SCREEN, 0);
                if (sc > 0) {
                    screens++;
                    if (sc > 4) appended++;
                    ModularBrush.Part plain = m.copy();
                    for (PartDial d : PartDial.SCREEN_DIALS) plain.set(d, plain.choice.defaultOf(d));
                    check(plain.sameAs(noScreen.part(BrushPart.MEDIUM)) && m.get(PartDial.CELL) >= 4 && m.get(PartDial.CELL) <= 10, "a screened roll is the same roll with its Screen");
                } else if (!m.sameAs(noScreen.part(BrushPart.MEDIUM))) other++;
            }
            check(other == 0, s.name + ": every other roll is as before (" + other + ")");
            int so = s.screenOdds(), to = s.toneOdds();
            // Print: every roll screened, or one in five Screentone.
            int[] want = s.name.equals("Print") ? new int[]{1, 5} : new int[]{s.wild || s.name.equals("Marker") || s.name.equals("Ink wash") || s.name.equals("Charcoal") ? 25 : s.name.equals("Fineliner") || s.name.equals("Pencil") ? 40 : 0, s.wild ? 40 : 0};
            if (s.name.equals("Marker")) check(appended > 0 && appended < screens / 2, "Marker rolls an appended print now and then: " + appended + " of " + screens);
            check(so == want[0] && to == want[1], s.name + "'s odds");
            check(so == 0 ? screens == 0 : screens > n / so / 3 && screens < n / so * 3, s.name + " rolls a Screen about 1 in " + so + ": " + screens + " of " + n);
            check(to == 0 ? tones == 0 : tones > n / to / 3 && tones < n / to * 3, s.name + " rolls Tone about 1 in " + to + ": " + tones + " of " + n);
        }
        check(ModularStyle.named("Watercolor").screenOdds() == 0, "never Watercolor");
        ModularBrush plain = ModularStyle.named("Anything goes").roll(new Random(11));
        for (int i = 0; i < 60; i++) for (ModularVariations.Made m : ModularVariations.make(plain, new ModularLocks(), new Random(i)))
            check(m.brush.choice(BrushPart.MEDIUM) != PartChoice.TONE && (int) m.brush.part(BrushPart.MEDIUM).get(PartDial.SCREEN, 0) == (int) plain.part(BrushPart.MEDIUM).get(PartDial.SCREEN, 0), "a variation never swaps to Tone or sets a Screen");
    }

    // ---- 10. words; 11. Brush Lab ----

    /** The web lab's words (newmods.js choiceWords, plainWords, captionItems) for screened and toned brushes, some as Brush Studio rolls them (scratch words.cjs). */
    static final String[][] LAB_WORDS = {
            {"plain-ink.M00i000000000at_000zz00000000000000001_0_000_1jz_0000000000000000000nz0.1y9", "Halftone ink", "Halftone ink line. Permanent.", "Fineliner, halftone, permanent"},
            {"plain-ink.M00i000000000at_000zz000000000000000038bi0qzi0_0_000_1jz_0000000000000000000nz0.1y9", "Dithered ink", "Dithered ink line. Doubled, dots swell. Permanent.", "Fineliner, dithered, permanent"},
            {"plain-ink.M00i000000000at_000zz00000000000000012_0_000_1jz_0000000000000000000nz0.1y9", "White ink", "Engraved white ink line. Permanent.", "Fineliner, white, engraved, permanent"},
            {"plain-ink.M00i000000000at_63_0_000_1jz_0000000000000000000nz0.1y9", "Screentone", "Gradient screentone. Scrapes off in streaks.", "Fineliner, gradient screentone"},
            {"nimble-squash.M50i00000ajdwat_3cpfg00we04gm2000hh_0_1nt_1jz_8ipm900wkge00000000nz0.8in3ab", "Grained", "Grained vine charcoal on cold-press paper. Dots swell. Soft speckled edge, picks up the texture. Part erasable. Tilt widens, pressure grows dots.", "Charcoal, grained, round, part erasable"},
            {"mellow-yam.M31vjeei000000000at_000zzrz00000000000zz032s_0_300_1jz_81g00000000006b0000nz0.1mgep1", "Dithered ink", "Dithered ink line, chisel tip, on felt paper. Pools when slow. Speed thins.", "Marker, dithered, felt"},
            {"vivid-plum.M31tyeei000000000at_02dzzhm00000000000zz022si04i00al_0_300_1jz_86b00000000009j0000nz0.11we46l", "Engraved ink", "Engraved line and wash, chisel tip, on felt paper. Patchy ink, doubled. Pools when slow. Speed thins, pressure widens.", "Marker, engraved, wet ink, felt"},
            {"laid-radish.M51iveei07q00003tat_36sok00we02jd_0_2zw_1jz_8idnq00osjr00000000nz0.hdufsf", "Engraved", "Engraved compressed charcoal, chisel tip, on ribbed paper. Picks up the texture, soft speckled edge. Part erasable. Tilt widens, pressure grows dots.", "Charcoal, engraved, part erasable, ribbed"},
            {"smear-radish.Md28uksc18vql00m2zk_609m2shl_3qisc1p_1dk_1u9_8axl200000000000000nz0.xpeeit", "Dot screentone", "Dot screentone, flat nib, on cold-press paper. Scrapes off in streaks. Slight wobble, tapers to a point. Pressure grows dots and widens.", "Anything goes, dot screentone, flat nib, cold press"},
            {"nimble-okra.M51i5eei05u00006yat_30vox00we01_0_1tc_1jz_8jnip00vbew00000000nz0.2lchwi", "Halftone", "Halftone compressed charcoal, chisel tip, on cold-press paper. Picks up the texture. Part erasable. Tilt and pressure widen.", "Charcoal, halftone, crisp, part erasable"},
            {"mellow-okra.M00ic00630000at_000zz000000000000000035jus_0_100_1jz_82n00000000004r0000nz0.1jl5dns", "Dithered ink", "Dithered ink line on cold-press paper. Permanent.", "Fineliner, dithered, permanent, cold press"},
            {"smear-squash.M51pieei03d21a5c3at_3atr900we03duj70098_3p6jsat_1w5_1jz_8tvgm00t6n400000000nz0.1frcii2", "Dithered", "Dithered compressed charcoal, chisel tip, on cold-press paper. Dots swell. Picks up the texture, soft speckled edge. Smears. Part erasable. Pressure and tilt widen.", "Charcoal, dithered, smear, part erasable"},
            {"split-corn.Md4c3yuzsk7d50000_61gs8btl_0_58d_2fit02ms_0000000000000000000nz0.fyz0vd", "Line screentone", "Line screentone, split nib, on canvas. Scrapes off in streaks. Opens into two lines.", "Anything goes, line screentone, split nib, canvas"},
    };
    static void words() {
        for (String[] w : LAB_WORDS) {
            ModularBrush b = brush(w[0]);
            check(ModularWords.choiceWords(b, BrushPart.MEDIUM).equals(w[1]), "the Medium card says \"" + w[1] + "\": " + ModularWords.choiceWords(b, BrushPart.MEDIUM));
            check(ModularWords.plainWords(b).equals(w[2]), "This brush says \"" + w[2] + "\": " + ModularWords.plainWords(b));
            check(String.join(", ", ModularWords.caption(b)).equals(w[3]), "the caption says \"" + w[3] + "\": " + ModularWords.caption(b));
        }
        ModularBrush ink = screened(ink(), 1);
        check(ModularWords.choiceWords(ink, BrushPart.MEDIUM).equals("Halftone ink"), "the Medium card: " + ModularWords.choiceWords(ink, BrushPart.MEDIUM));
        check(ModularWords.plainWords(ink).startsWith("Halftone ink line."), "This brush: " + ModularWords.plainWords(ink));
        check(ModularWords.caption(ink).contains("halftone"), "the caption: " + ModularWords.caption(ink));
        check(ModularWords.choiceWords(screened(wash(), 3), BrushPart.MEDIUM).equals("Dithered wash") && ModularWords.choiceWords(screened(ink(), 2), BrushPart.MEDIUM).equals("Engraved ink")
                && ModularWords.choiceWords(screened(wash(), 4), BrushPart.MEDIUM).equals("Grained wash"), "each screen names its print");
        String[][] more = {{"5", "Rosette ink"}, {"6", "Ben-Day ink"}, {"7", "Crosshatch ink"}, {"8", "Wavy-line ink"}, {"9", "Mezzotint ink"}, {"10", "Diamond-dot"}, {"11", "Stippled ink"}, {"12", "Woodcut ink"}, {"13", "Scribbled ink"}, {"14", "Hatched ink"}};
        for (String[] w : more) check(ModularWords.choiceWords(screened(ink(), Integer.parseInt(w[0])), BrushPart.MEDIUM).equals(w[1]), "print " + w[0] + " names its print: " + ModularWords.choiceWords(screened(ink(), Integer.parseInt(w[0])), BrushPart.MEDIUM));
        check(PartDial.SCREEN.choices.length == 15 && String.join(",", java.util.Arrays.copyOfRange(PartDial.SCREEN.choices, 11, 15)).equals("Stipple,Woodcut,Scribble,Hatching")
                && String.join(",", java.util.Arrays.copyOfRange(PartDial.PATTERN.choices, 7, 11)).equals("Stipple,Woodcut,Scribble,Hatching") && PartDial.SCREEN.choices[5].equals("Rosette") && PartDial.SCREEN.choices[6].equals("Ben-Day") && PartDial.SCREEN.choices[7].equals("Crosshatch")
                && PartDial.SCREEN.choices[8].equals("Wavy Lines") && PartDial.SCREEN.choices[9].equals("Mezzotint") && PartDial.SCREEN.choices[10].equals("Diamond Dots") && PartDial.SCREEN.choices[4].equals("Grain"),
                "Print Shading's six new choices, then four more, are appended (Screentone's four too), the old four unmoved");
        ModularBrush stick = screened(ErasableChecks.preset("Messy stick"), 2);
        check(ModularWords.choiceWords(stick, BrushPart.MEDIUM).equals("Engraved"), "too long beside the medium, the print alone: " + ModularWords.choiceWords(stick, BrushPart.MEDIUM));
        check(ModularWords.plainWords(set(ink, PartDial.MIS, 8)).startsWith("Halftone ink line. Doubled."), "Double Print says so: " + ModularWords.plainWords(set(ink, PartDial.MIS, 8)));
        ModularBrush t = tone(ModularStyle.named("Anything goes").roll(new Random(3)), 0, .5);
        t.set(BrushPart.TIP, new ModularBrush.Part(PartChoice.ROUND)); t.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.FAST_WET)); t.set(BrushPart.PAPER, new ModularBrush.Part(PartChoice.PLAIN));
        t.set(BrushPart.HAND, new ModularBrush.Part(PartChoice.STEADY)); t.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.OFF)); t.normalize();
        check(ModularWords.choiceWords(t, BrushPart.MEDIUM).equals("Dot screentone") && PartChoice.TONE.label.equals("Screentone"), "Tone's card: " + ModularWords.choiceWords(t, BrushPart.MEDIUM));
        check(ModularWords.plainWords(t).startsWith("Dot screentone. Scrapes off in streaks."), "This brush: " + ModularWords.plainWords(t));
        check(String.join(", ", ModularWords.caption(t)).startsWith("Anything goes, dot screentone"), "the caption: " + ModularWords.caption(t));
        check(ModularWords.choiceWords(tone(t, 3, .5), BrushPart.MEDIUM).equals("Screentone"), "a gradient");
        check(PartDial.SCREEN.shown(1, ink.part(BrushPart.MEDIUM)).equals("Halftone") && PartDial.CELL.shown(6).equals("6 px") && PartDial.S_ANGLE.shown(45).equals("45°") && PartDial.MIS.shown(0).equals("off"), "the dials read Halftone, 6 px, 45°, off");
        // The owner's names: the dial is Print shading (one constant), its second print Engraving; Tone is Screentone, its level Darkness.
        check(PartDial.SCREEN.label.equals(PrintScreen.LABEL) && PrintScreen.LABEL.equals("Print Shading") && PartDial.SCREEN.shown(2, ink.part(BrushPart.MEDIUM)).equals("Engraving")
                && PartChoice.TONE.label.equals("Screentone") && PartDial.TTONE.label.equals("Darkness"), "Print Shading, Engraving, Screentone, Darkness");
        // Control: a screened brush's darkness cells size its dots, and say so.
        ModularBrush pressing = ink.copy(), tilting = ink.copy();
        pressing.part(BrushPart.HAND).set(PartDial.PD, 1); pressing.normalize();
        tilting.part(BrushPart.HAND).set(PartDial.TD, 1); tilting.part(BrushPart.HAND).set(PartDial.SD, 1); tilting.normalize();
        check(ModularWords.handWords(pressing, 2).equals("pressure grows dots") && ModularWords.handWords(tilting, 2).equals("tilt and speed shrink dots")
                && ModularWords.darkHead(pressing).equals("Dots") && ModularWords.darkHead(ink()).equals("Dark") && ModularWords.handWords(screened(pressing, 0), 2).equals("pressure darkens")
                && ModularWords.darkHead(tone(ink(), 0, .5)).equals("Dots") && ModularWords.darkHead(BlendChecks.life(ink, PartChoice.FLOAT)).equals("Dark"),
                "Control says a screened brush's darkness grows or shrinks its dots, and its grid's column is Dots: " + ModularWords.handWords(pressing, 2) + ", " + ModularWords.handWords(tilting, 2));
    }
    static void lab() {
        java.util.Map<String, String> store = new java.util.HashMap<>();
        BrushLibrary.Store st = new BrushLibrary.Store() { public String get(String k) { return store.get(k); } public void put(String k, String v) { if (v == null) store.remove(k); else store.put(k, v); } };
        BrushLab lab = new BrushLab(new ToolSlots(st, new Random(1)), 2, new Random(7));
        lab.put(Brush.of(ink()));
        check(lab.dials(BrushPart.MEDIUM).contains(PartDial.SCREEN) && !lab.dials(BrushPart.MEDIUM).contains(PartDial.CELL), "with Screen none, its own dials don't show");
        lab.choose(PartDial.SCREEN, 1);
        check(lab.dials(BrushPart.MEDIUM).containsAll(Arrays.asList(PartDial.SCREEN_DIALS)) && lab.active(PartDial.CELL) && lab.active(PartDial.S_ANGLE), "a halftone shows Cell, Angle and the newsprint dials");
        lab.choose(PartDial.SCREEN, 3);
        check(!lab.active(PartDial.CELL) && !lab.active(PartDial.S_ANGLE) && lab.active(PartDial.MIS), "a dither greys Cell and Angle");
        check(lab.chips(BrushPart.MEDIUM).contains(PartChoice.TONE), "Brush Lab offers Tone among the Medium chips");
        lab.choose(BrushPart.MEDIUM, PartChoice.TONE);
        check(lab.dials(BrushPart.MEDIUM).equals(Arrays.asList(PartDial.PATTERN, PartDial.TTONE, PartDial.CELL, PartDial.S_ANGLE)), "Tone's dials: Pattern, Tone, Cell, Angle");
        LabLayout manta = new LabLayout(LabLayoutChecks.MANTA[0], LabLayoutChecks.MANTA[1]), nomad = new LabLayout(LabLayoutChecks.NOMAD[0], LabLayoutChecks.NOMAD[1]);
        check(manta.menu(PartDial.SCREEN.choices) && nomad.menu(PartDial.SCREEN.choices), "Screen's five options are a menu button");
        // The Nomad's open card grew with the toolbar band gone (2026-10-29): Screen now fits beside Ink's dials too.
        check(manta.shows(PartChoice.INK, PartDial.MIS) && !nomad.shows(PartChoice.INK, PartDial.MIS) && nomad.shows(PartChoice.INK, PartDial.SCREEN) && nomad.shows(PartChoice.WASH, PartDial.SCREEN) && nomad.shows(PartChoice.TONE, PartDial.CELL),
                "the Manta shows every Screen dial; the Nomad Screen alone (Ink's too), and Tone's own");
        // Its samples: a screened brush's loop is pure black and white too.
        int[] loop = WildSample.modular(screened(wash(), 1), 1, WildSample.Shape.LOOP, 120, 80, false);
        int grey = 0, bl = 0;
        for (int px : loop) { int v = px & 255; if (v == 0) bl++; else if (v != 255) grey++; }
        check(bl > 50 && grey == 0, "a halftone's loop is its dots: " + bl + " black, " + grey + " grey");
    }
}
