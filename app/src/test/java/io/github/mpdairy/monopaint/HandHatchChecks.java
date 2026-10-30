package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand Hatch ({@link HandHatchStamper}) and the four tonal prints (Stipple, Woodcut, Scribble, Hatching;
 * {@link PrintScreen}): the same samples lay the same strokes however they are batched, a held pen's too; pressure,
 * going over and holding each add sets, to a cap; strokes run past the band, never off the page; Line Length, Wobble
 * and Taper Out change the strokes; old codes and old prints exactly as before; its words; and its cost beside Hatch's.
 */
public final class HandHatchChecks {
    static final int W = 480, H = 320;

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        deterministic(); builds(); spray(); reaches(); bounds(); dials(); old(); prints(); words();
        String bench = bench();
        System.out.println("PASS: Hand Hatch, a spray of strokes, lays the same strokes from the same samples batched 1, 3 or 1000 at a time and live (a held pen's too); pressure, going over a place, holding the pen there and dragging slowly each lay more, up to the darkest; a held dab grows; its strokes run past the band and never off the page; Line Length, Wobble and Taper Out change them; old codes, old prints and the Hatch pen as before; the four tonal prints darken step by step, change their look as they do and match their Screentone twins; its words. " + bench);
    }

    static ModularBrush hatching() { return StippleChecks.mk(PartChoice.HAND_HATCH, PartChoice.STEADY); }
    static ModularBrush with(ModularBrush b, PartDial d, double v) { ModularBrush c = b.copy(); c.part(d.part).set(d, v); return c.normalize(); }

    /** {x, y, travel, time}: a zigzag over a box, {@code passes} times, in one stroke, 6 ms a sample. */
    static List<double[]> zigzag(double x0, double y0, double w, double h, double p, int passes) {
        List<double[]> o = new ArrayList<>();
        long t = 1000;
        for (int k = 0; k < passes; k++) {
            boolean right = true;
            for (double y = y0 + 4 * k; y <= y0 + h; y += 18) { for (double x = 0; x <= w; x += 3) { o.add(new double[]{right ? x0 + x : x0 + w - x, y, p, t}); t += 6; } right = !right; }
        }
        return o;
    }
    /** A light stroke that stops for {@code ms} in the middle. */
    static List<double[]> held(int ms, double p) {
        List<double[]> o = new ArrayList<>();
        long t = 1000;
        for (double x = 100; x <= 240; x += 3) { o.add(new double[]{x, 160, p, t}); t += 6; }
        for (int k = 0; k < ms / 8; k++) { o.add(new double[]{240, 160, p, t}); t += 8; }
        for (double x = 240; x <= 380; x += 3) { o.add(new double[]{x, 160, p, t}); t += 6; }
        return o;
    }

    /** {@code s} through the stamper alone, {@code per} samples a time, Build-up's ticks fed as the stroke feeds them. */
    static FloatTiles stamp(ModularBrush b, double size, List<double[]> s, int per) {
        ResolvedBrush r = ResolvedBrush.of(b, size, 1);
        FloatTiles t = new FloatTiles(W, H, 0);
        StrokePoints pts = new StrokePoints();
        LineStamper.Meta meta = new LineStamper.Meta();
        StippleStamper.Holds holds = new StippleStamper.Holds();
        int done = 1;
        for (int i = 0; i < s.size(); i += per) {
            for (int j = i; j < Math.min(s.size(), i + per); j++) {
                double[] q = s.get(j);
                pts.add((float) q[0], (float) q[1], (float) q[2], (long) q[3], Float.NaN, Float.NaN);
                holds.feed((float) q[0], (float) q[1], (float) q[2], (long) q[3], pts.total());
            }
            HandHatchStamper.stamp(t, null, r.pattern, pts, r.inkSize, done, meta, holds);
            done = pts.count;
        }
        return t;
    }
    static ToneDocument page(ModularBrush b, double size, List<double[]> s, int per) {
        ToneDocument d = InkWashLayers.blank(W, H);
        ModularStroke st = ModularStroke.start(d, b, size, 1, null);
        StippleChecks.feed(st, s, per);
        st.present(); st.finish();
        return d;
    }
    static int dark(ToneDocument d) { int n = 0; for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (d.compositeTone(x, y) < 128) n++; return n; }
    static boolean same(ToneDocument a, ToneDocument b) {
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (a.compositeTone(x, y) != b.compositeTone(x, y)) return false;
        return true;
    }

    static void deterministic() {
        ModularBrush follows = with(hatching(), PartDial.HEAD_TURN, 0);
        for (ModularBrush b : new ModularBrush[]{hatching(), follows})
            for (List<double[]> s : List.of(zigzag(60, 60, 200, 160, .5, 2), held(1500, .4))) {
                FloatTiles a = stamp(b, 60, s, 3);
                check(StippleChecks.same(a, stamp(b, 60, s, 1)) && StippleChecks.same(a, stamp(b, 60, s, 1000)), "The same samples (and times) lay the same strokes, however they're batched");
                ToneDocument live = page(b, 60, s, 1);
                check(same(live, page(b, 60, s, 4)) && same(live, page(b, 60, s, 1000)) && dark(live) > 200, "Live, the same strokes however the samples are presented");
            }
        // A second stroke elsewhere lays strokes of its own (seeded by where it starts), not a copy shifted.
        FloatTiles one = stamp(hatching(), 60, zigzag(60, 60, 200, 160, .3, 1), 3), two = stamp(hatching(), 60, zigzag(61, 63, 200, 160, .3, 1), 3);
        check(!StippleChecks.same(one, two), "Each stroke its own strokes");
    }
    static void builds() {
        int[] p = new int[4];
        double[] ps = {.2, .5, .75, .95};
        for (int i = 0; i < 4; i++) p[i] = dark(page(hatching(), 60, zigzag(100, 80, 240, 140, ps[i], 1), 4));
        check(p[0] < p[1] && p[1] < p[2] && p[2] < p[3] && p[3] > 2.2 * p[0], "Pressure adds sets: " + java.util.Arrays.toString(p));
        int[] over = new int[4];
        for (int k = 0; k < 4; k++) over[k] = dark(page(hatching(), 60, zigzag(100, 80, 240, 140, .3, k + 1), 4));
        check(over[0] < over[1] && over[1] < over[2] && over[2] <= over[3] && over[2] > 1.6 * over[0], "Going over again adds sets: " + java.util.Arrays.toString(over));
        int[] hold = new int[4];
        int[] ms = {0, 600, 1500, 4000};
        for (int k = 0; k < 4; k++) hold[k] = dark(page(hatching(), 60, held(ms[k], .3), 4));
        check(hold[0] < hold[1] && hold[1] < hold[2] && hold[2] <= hold[3] && hold[3] > 1.5 * hold[0], "Holding the pen adds sets: " + java.util.Arrays.toString(hold));
        // Capped: at the darkest, more holding adds nothing where the spray is thickest (round the pen, 240, 160).
        ResolvedBrush.Pattern P = ResolvedBrush.of(hatching(), 60, 1).pattern;
        int at8 = dark(page(hatching(), 60, held(8000, .3), 4)), at16 = dark(page(hatching(), 60, held(16000, .3), 4)), at32 = dark(page(hatching(), 60, held(32000, .3), 4));
        check(at16 - at8 > at32 - at16 && at32 - at16 < .04 * at16 && HandHatchStamper.full(P, 60) >= 1, "Holding stops at the darkest: " + at8 + ", " + at16 + ", " + at32);
        check(page(hatching(), 60, held(1000, .3), 4) != null && new ModularStrokeProbe().builds(), "A Hand Hatch stroke builds while held");
        // A pen set down and held still, never moving: a patch that grows.
        int[] dab = new int[3];
        int[] dms = {200, 800, 2400};
        for (int k = 0; k < 3; k++) { List<double[]> o = new ArrayList<>(); for (int t = 0; t <= dms[k]; t += 8) o.add(new double[]{240, 160, .45, 1000 + t}); dab[k] = dark(page(hatching(), 60, o, 4)); }
        check(dab[0] < dab[1] && dab[1] < dab[2] && dab[2] > 0, "A held dab grows: " + java.util.Arrays.toString(dab));
    }
    static int darkNear(ToneDocument d, int cx, int cy, int r) { int n = 0; for (int y = cy - r; y <= cy + r; y++) for (int x = cx - r; x <= cx + r; x++) if (d.compositeTone(x, y) < 128) n++; return n; }
    /** A spray: a slow drag lays more than a quick one at the same pressure; light, one direction; dark, crossed. */
    static void spray() {
        List<double[]> slow = new ArrayList<>(), quick = new ArrayList<>();
        for (int i = 0; i <= 300; i++) slow.add(new double[]{80 + i, 160, .4, 1000 + 8 * i});
        for (int i = 0; i <= 30; i++) quick.add(new double[]{80 + 10 * i, 160, .4, 1000 + 8 * i});
        int s = dark(page(hatching(), 60, slow, 4)), q = dark(page(hatching(), 60, quick, 4));
        check(s > 1.3 * q && q > 0, "A slow drag lays more than a quick one: " + s + " against " + q);
    }
    /** Whether a Hand Hatch stroke asks the pad for held ticks ({@link DrawingStroke#buildsWhileHeld}). */
    static final class ModularStrokeProbe { boolean builds() { return ModularStroke.start(InkWashLayers.blank(W, H), hatching(), 60, 1, null).buildsWhileHeld(); } }
    static void reaches() {
        // A straight firm stroke along y = 160: strokes run past the band (30 px each side), and within reach.
        ToneDocument d = page(hatching(), 60, StippleChecks.line(60, 160, 420, 160, 120, u -> .8), 4);
        int past = 0, beyond = 0;
        double reach = HandHatchStamper.reach(ResolvedBrush.of(hatching(), 60, 1).pattern, 60);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (d.compositeTone(x, y) < 200) {
            double off = Math.abs(y + .5 - 160);
            if (off > 31) past++;
            if (off > reach || x < 60 - reach || x > 420 + reach) beyond++;
        }
        check(past > 30 && beyond == 0, "Strokes run past the band (" + past + " px) and never past their reach (" + beyond + ")");
    }
    static void bounds() {
        // Strokes at the page's edges and corners, and samples off it: nothing thrown, nothing outside.
        for (List<double[]> s : List.of(zigzag(-30, -30, 90, 90, .95, 3), zigzag(W - 50, H - 50, 90, 90, .95, 3), held(500, .9)))
            for (ModularBrush b : new ModularBrush[]{hatching(), with(hatching(), PartDial.HEAD_TURN, 0)}) {
                FloatTiles t = stamp(b, 120, s, 5);
                check(t.width == W && t.height == H, "the page's size");
                page(b, 120, s, 3);
            }
        ResolvedBrush r = ResolvedBrush.of(hatching(), 60, 1);
        check(ModularStroke.reach(r) == HandHatchStamper.reach(r.pattern, r.inkSize) && QuickStroke.keepsUpTo(r) == QuickStroke.STIPPLE, "its reach, and kept up with as a stipple is");
        check(Math.abs(ModularSizes.size(hatching(), InkWashSizes.Preset.MEDIUM) - ModularSizes.HATCHING) < 1e-9, "its Medium size, every style");
    }
    static void dials() {
        ModularBrush b = hatching();
        List<double[]> s = zigzag(80, 80, 240, 140, .5, 1);
        FloatTiles base = stamp(b, 60, s, 3);
        for (PartDial d : new PartDial[]{PartDial.HLEN, PartDial.HWOB, PartDial.HENDS, PartDial.LINE_W, PartDial.PITCH, PartDial.HANGLE})
            check(!StippleChecks.same(base, stamp(with(b, d, d == PartDial.HANGLE ? 100 : d.def < .5 ? .9 : .1), 60, s, 3)), d.label + " changes the strokes");
        check(PartChoice.HAND_HATCH.labelOf(PartDial.HEAD_TURN).equals("Lines Follow") && (int) b.part(BrushPart.TIP).get(PartDial.HEAD_TURN) == 1
                && String.join(",", labels(PartChoice.HAND_HATCH)).equals("Spacing,Line Weight,Line Angle,Lines Follow,Line Length,Wobble,Taper Out,Size,Flick"), "Its dials: " + labels(PartChoice.HAND_HATCH));
    }
    /**
     * Hand Hatch is cut (the refocus, 2026-10-24): a code that holds it (a slot's, the library's, a share code, the Hand
     * hatch and Neat hatch Basic tools) still reads, as Hatch, the nearest tip, keeping the dials the two share; Brush Lab
     * never offers it, in Basic or Advanced, and no roll picks it.
     */
    static void cut() {
        ModularBrush hh = with(with(with(hatching(), PartDial.PITCH, .8), PartDial.LINE_W, .3), PartDial.HLEN, .9);
        String code = ModularCode.encode(hh);
        check(code.contains(".M0c"), "A Hand Hatch code as older versions wrote it: " + code);
        ModularBrush read = ModularCode.decode(code);
        check(read != null && read.choice(BrushPart.TIP) == PartChoice.HATCH, "It reads as Hatch: " + code);
        for (PartDial d : PartChoice.HATCH.dials)
            check(read.part(BrushPart.TIP).get(d) == (PartChoice.HAND_HATCH.has(d) ? ModularCode.decode(ModularCode.encode(hh.copy())).part(BrushPart.TIP).get(d) : PartChoice.HATCH.defaultOf(d)),
                    d.label + " kept where both tips have it, else Hatch's default");
        check(Math.abs(read.part(BrushPart.TIP).get(PartDial.PITCH) - .8) < .01 && Math.abs(read.part(BrushPart.TIP).get(PartDial.LINE_W) - .3) < .01, "Its spacing and line weight kept");
        check(read.choice(BrushPart.MEDIUM) == hh.choice(BrushPart.MEDIUM) && read.seed == hh.seed && read.name.equals(hh.name), "The other parts, the seed and the name as they were");
        check(ModularCode.decode(ModularCode.encode(read)).choice(BrushPart.TIP) == PartChoice.HATCH, "Saved again, it stays Hatch");
        for (String name : new String[]{"Hand hatch", "Neat hatch"}) {
            ToolPresets.Preset p = ToolKits.preset(name);
            check(p != null && p.brush().parts.choice(BrushPart.TIP) == PartChoice.HATCH, name + ", a Basic tool, draws with Hatch");
        }
        check(!ModularFeatures.offered(PartChoice.HAND_HATCH), "Never offered: no chip, roll, die, swap or variation picks it");
        check(!ModularLabChecks.lab(0, 1).chips(BrushPart.TIP).contains(PartChoice.HAND_HATCH), "No Hand Hatch chip in Brush Lab");
    }
    /** Whether {@code code} holds Hand Hatch (Tip choice 12): it reads as Hatch, so it no longer writes back byte for byte. */
    static boolean handHatch(String code) { int m = code.indexOf(".M"); return m >= 0 && code.length() > m + 3 && code.charAt(m + 3) == 'c'; }
    static List<String> labels(PartChoice c) { List<String> o = new ArrayList<>(); for (PartDial d : c.dials) o.add(c.labelOf(d)); return o; }
    static void old() {
        check(PartChoice.of(BrushPart.TIP, 12) == PartChoice.HAND_HATCH && PartChoice.HAND_HATCH.key.equals("handhatch"), "Tip choice 12, so no older code holds it");
        String hatchPen = "hatch-pen.M08at7x901zzi0_000zz000000000000000_0_000_1jz_0000000000000000000nz0.x3";
        check(ModularCode.encode(ModularCode.decode(hatchPen)).equals(hatchPen), "The Hatch pen's code as before");
        cut();
        // Every older print and screentone but Ben-Day, at two sizes and angles: thresholds and ink as at 4a8cf86, to the bit.
        check(oldPrints() == 0x590a4c49L, "The older prints as before: " + Long.toHexString(oldPrints()));
        for (int s = 1; s <= 10; s++) check(ModularCode.decode(ModularCode.encode(ScreenChecks.screened(ScreenChecks.ink(), s))).part(BrushPart.MEDIUM).get(PartDial.SCREEN) == s, "print " + s + " reads back");
    }
    static long oldPrints() {
        java.util.zip.CRC32 c = new java.util.zip.CRC32();
        for (double[] ca : new double[][]{{6, 45}, {11, 20}})
            for (int k = 1; k <= 17; k++) {
                if (k == 6) continue;   // Ben-Day was redrawn as the comic dots (the owner, 2026-10-21)
                ModularBrush.Part m;
                if (k <= 10) { m = new ModularBrush.Part(PartChoice.INK); m.set(PartDial.SCREEN, k); }
                else { m = new ModularBrush.Part(PartChoice.TONE); m.set(PartDial.PATTERN, k - 11); m.set(PartDial.TTONE, .45); }
                m.set(PartDial.CELL, ca[0]); m.set(PartDial.S_ANGLE, ca[1]);
                PrintScreen p = PrintScreen.of(m);
                for (int y = 0; y < 96; y++) for (int x = 0; x < 96; x++) {
                    long b = Double.doubleToLongBits(p.threshold(x, y));
                    for (int s = 0; s < 64; s += 8) c.update((int) (b >>> s));
                    c.update(p.ink(.4, x, y, 96) ? 1 : 0);
                }
            }
        return c.getValue();
    }
    static void prints() {
        for (int s = 11; s <= 14; s++) {
            PrintScreen p = PrintScreen.of(ScreenChecks.screened(ScreenChecks.ink(), s).part(BrushPart.MEDIUM));
            // Eight clear steps: each a tenth darker than the last at least.
            double last = -1;
            for (int k = 1; k <= 8; k++) {
                double a = k / 9.0, f = share(p, a);
                check(f > last + .07, "print " + s + ": step " + k + " darker (" + f + " after " + last + ")");
                last = f;
            }
        }
        // Their looks change as they darken. Stipple: more separate dots, then merged.
        PrintScreen st = PrintScreen.of(ScreenChecks.screened(ScreenChecks.ink(), 11).part(BrushPart.MEDIUM));
        int fewest = spots(st, .04), lightSpots = spots(st, .15), darkSpots = spots(st, .7);
        check(fewest < .7 * lightSpots && darkSpots < .6 * lightSpots, "Stipple: more dots as it darkens, then they join (" + fewest + " spots at .04, " + lightSpots + " at .15, " + darkSpots + " at .7)");
        // Hatching: one direction light (its ink lies along the angle), crossed when darker.
        PrintScreen ha = PrintScreen.of(ScreenChecks.screened(ScreenChecks.ink(), 14).part(BrushPart.MEDIUM));
        double l = along(ha, .12), d = along(ha, .5);
        check(l > .8 && d < l - .15, "Hatching: one direction light (" + l + " of runs along the angle), crossed darker (" + d + ")");
    }
    static double share(PrintScreen p, double a) { int n = 0; for (int y = 0; y < 200; y++) for (int x = 0; x < 200; x++) if (p.ink(a, x, y, 200)) n++; return n / 40000.0; }
    /** Its separate spots of ink at {@code a} over 160 px square (4-connected). */
    static int spots(PrintScreen p, double a) {
        int n = 160; boolean[] on = new boolean[n * n], seen = new boolean[n * n];
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) on[y * n + x] = p.ink(a, x, y, n);
        int count = 0;
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        for (int i = 0; i < n * n; i++) if (on[i] && !seen[i]) {
            count++; seen[i] = true; q.add(i);
            while (!q.isEmpty()) { int j = q.poll(), x = j % n, y = j / n;
                for (int[] dd : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { int u = x + dd[0], v = y + dd[1], k = v * n + u; if (u >= 0 && v >= 0 && u < n && v < n && on[k] && !seen[k]) { seen[k] = true; q.add(k); } } }
        }
        return count;
    }
    /** The share of inked neighbour pairs at 45° (the default angle) among those at 45° and -45°. */
    static double along(PrintScreen p, double a) {
        int with = 0, across = 0;
        for (int y = 1; y < 199; y++) for (int x = 1; x < 199; x++) if (p.ink(a, x, y, 200)) {
            if (p.ink(a, x + 1, y + 1, 200)) with++;
            if (p.ink(a, x + 1, y - 1, 200)) across++;
        }
        return with / Math.max(1.0, with + across);
    }
    static void words() {
        ModularBrush b = hatching();
        check(ModularWords.plainWords(b).startsWith("Ink hatched by hand. " + ModularWords.HATCHES), "This brush: " + ModularWords.plainWords(b));
        check(ModularWords.caption(b).subList(0, 2).equals(java.util.Arrays.asList("Fineliner", "hand hatch")), "Caption: " + ModularWords.caption(b));
        check(ModularWords.choiceWords(b, BrushPart.TIP).equals("Hand Hatch"), "The Tip card: " + ModularWords.choiceWords(b, BrushPart.TIP));
        for (ModularStyle s : ModularStyle.all()) for (int i = 0; i < 400; i++) check(s.roll(new java.util.Random(i)).choice(BrushPart.TIP) != PartChoice.HAND_HATCH, "never rolled");
    }

    /** Desktop ms a sample, live (stamper and compose), Hand Hatch beside the Hatch pen, and ns a pixel for the tonal prints beside Halftone. */
    static String bench() {
        StringBuilder said = new StringBuilder("Timings (this Mac), ms a sample live:");
        List<double[]> path = zigzag(40, 40, 400, 240, .7, 1);
        double[] hh = new double[2], ht = new double[2];
        double[] sizes = {60, 120};
        for (int i = 0; i < 2; i++) {
            hh[i] = perSample(hatching(), sizes[i], path);
            ht[i] = perSample(BristleSwarmChecks.preset("Hatch pen"), sizes[i], path);
            said.append(String.format(java.util.Locale.ROOT, " %d px Hand Hatch %.4f, Hatch pen %.4f;", (int) sizes[i], hh[i], ht[i]));
            Timing.limit(hh[i] <= Math.max(.05, 1.5 * ht[i]), "Hand Hatch costs about what Hatch does: " + said);
        }
        // The spray at its busiest: a firm scribble over and over a box (most cells filling at once), 120 px.
        double busy = perSample(hatching(), 120, zigzag(40, 40, 400, 240, .95, 3));
        said.append(String.format(java.util.Locale.ROOT, " 120 px firm scribble, three times over: Hand Hatch %.4f;", busy));
        Timing.limit(busy <= Math.max(.05, 2 * ht[1]), "Hand Hatch's busiest costs about what Hatch does: " + said);
        // A screened ink stroke, live, 40 px: what a print costs a sample.
        double plain = perSample(ScreenChecks.screened(ScreenChecks.ink(), 1), 40, path);
        said.append(String.format(java.util.Locale.ROOT, " 40 px printed ink: Halftone %.4f", plain));
        String[] printNames = {"Stipple", "Woodcut", "Scribble", "Hatching"};
        for (int s = 11; s <= 14; s++) {
            double ms = perSample(ScreenChecks.screened(ScreenChecks.ink(), s), 40, path);
            said.append(String.format(java.util.Locale.ROOT, ", %s %.4f", printNames[s - 11], ms));
            Timing.limit(ms <= Math.max(.05, 2.5 * plain), printNames[s - 11] + " costs a sample about what Halftone does: " + said);
        }
        // A wet ink body composes the same pixels at every present: each print costs about what Halftone does there too
        // (its thresholds are worked out once a stroke), not three times as much.
        ModularBrush wet = GravityChecks.preset("Engraving pen");
        double[] halftone = wetPrint(wet, 1);
        said.append(String.format(java.util.Locale.ROOT, "; 60 px wet ink, live: Halftone %.4f", halftone[0]));
        for (int s = 11; s <= 14; s++) {
            double[] t = wetPrint(wet, s);
            said.append(String.format(java.util.Locale.ROOT, ", %s %.4f", printNames[s - 11], t[0]));
            Timing.limit(t[0] <= Math.max(.1, 1.8 * halftone[0]), printNames[s - 11] + " over a wet body costs a sample about what Halftone does: " + said);
        }
        said.append(";");
        said.append(" ns a pixel:");
        double half = nsPerPixel(1);
        said.append(String.format(java.util.Locale.ROOT, " Halftone %.1f", half));
        String[] names = {"Stipple", "Woodcut", "Scribble", "Hatching"};
        for (int s = 11; s <= 14; s++) {
            double ns = nsPerPixel(s);
            said.append(String.format(java.util.Locale.ROOT, ", %s %.1f", names[s - 11], ns));
            Timing.limit(ns < 400, names[s - 11] + " is cheap enough a pixel: " + ns);
        }
        return said.toString();
    }
    /** {@code b} through Print Shading {@code screen}, Fast-wet, on ModularBenchChecks' wave at 60 px: the best of three. */
    static double[] wetPrint(ModularBrush b, int screen) {
        ModularBrush c = ScreenChecks.screened(b, screen);
        c.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.FAST_WET));
        ModularBenchChecks.run(c, 60);
        return ModularBenchChecks.best(ModularBenchChecks.best(ModularBenchChecks.run(c, 60), ModularBenchChecks.run(c, 60)), ModularBenchChecks.run(c, 60));
    }
    static double perSample(ModularBrush b, double size, List<double[]> path) {
        double best = Double.MAX_VALUE;
        for (int run = 0; run < 5; run++) {
            ToneDocument d = InkWashLayers.blank(W, H);
            long t0 = System.nanoTime();
            DrawingStroke s = ModularStroke.start(d, b, size, 1, null);
            for (int i = 0; i < path.size(); i++) { double[] q = path.get(i); s.sample((float) q[0], (float) q[1], ToolSettings.pressure((float) q[2]), Float.NaN, Float.NaN, (long) q[3]); if (i % 4 == 3) s.present(); }
            s.present(); s.finish();
            best = Math.min(best, (System.nanoTime() - t0) / 1e6 / path.size());
        }
        return best;
    }
    static double nsPerPixel(int screen) {
        PrintScreen p = PrintScreen.of(ScreenChecks.screened(ScreenChecks.ink(), screen).part(BrushPart.MEDIUM));
        double best = Double.MAX_VALUE; int n = 0;
        for (int run = 0; run < 5; run++) {
            long t0 = System.nanoTime();
            for (int y = 0; y < 300; y++) for (int x = 0; x < 300; x++) if (p.ink(.5, x, y, 300)) n++;
            best = Math.min(best, (System.nanoTime() - t0) / 90000.0);
        }
        return n < 0 ? 0 : best;
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
